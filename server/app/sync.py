"""Sync engine: Hybrid Logical Clock + per-field last-write-wins merge.

A faithful mirror of the Kotlin engine in app/src/main/java/com/food/opencook/sync/.
Both implementations must satisfy the shared vectors in server/tests/fixtures/sync-vectors.json,
so timestamps are byte-identical and merges converge identically across devices
and the server.
"""

from __future__ import annotations

from dataclasses import dataclass

MILLIS_WIDTH = 15
COUNTER_WIDTH = 4
MAX_COUNTER = 0xFFFF


class ClockDriftError(RuntimeError):
    """Logical counter overflowed — clock drift too large."""


@dataclass(frozen=True)
class Hlc:
    millis: int
    counter: int
    node: str

    def pack(self) -> str:
        # Fixed-width so lexicographic order == (millis, counter, node) order,
        # and identical to the Kotlin formatting.
        return f"{self.millis:0{MILLIS_WIDTH}d}-{self.counter:0{COUNTER_WIDTH}X}-{self.node}"

    def __lt__(self, other: "Hlc") -> bool:
        return self.pack() < other.pack()

    @staticmethod
    def parse(packed: str) -> "Hlc":
        first = packed.index("-")
        second = packed.index("-", first + 1)
        return Hlc(
            millis=int(packed[:first]),
            counter=int(packed[first + 1 : second], 16),
            node=packed[second + 1 :],
        )


class HlcClock:
    """A node's mutable clock. Not thread-safe; callers serialise access."""

    def __init__(self, node: str, initial: Hlc | None = None) -> None:
        self.node = node
        self.last = initial or Hlc(0, 0, node)

    def send(self, now: int) -> Hlc:
        millis = max(self.last.millis, now)
        counter = self.last.counter + 1 if millis == self.last.millis else 0
        self._guard(counter)
        self.last = Hlc(millis, counter, self.node)
        return self.last

    def recv(self, remote: Hlc, now: int) -> Hlc:
        millis = max(self.last.millis, remote.millis, now)
        if millis == self.last.millis and millis == remote.millis:
            counter = max(self.last.counter, remote.counter) + 1
        elif millis == self.last.millis:
            counter = self.last.counter + 1
        elif millis == remote.millis:
            counter = remote.counter + 1
        else:
            counter = 0
        self._guard(counter)
        self.last = Hlc(millis, counter, self.node)
        return self.last

    @staticmethod
    def _guard(counter: int) -> None:
        if counter > MAX_COUNTER:
            raise ClockDriftError(f"HLC counter overflow ({counter})")


# A materialised store is a dict keyed by (dataset, row_id, column) -> (value, clock).
Store = dict[tuple[str, str, str], tuple[str, str]]


def apply_message(store: Store, message: dict) -> bool:
    """Per-column LWW: apply only if strictly newer than the field's clock.

    ``message`` has keys timestamp, dataset, rowId, column, value. Returns True
    if it won (changed state). Applying any message set in any order converges.
    """
    key = (message["dataset"], message["rowId"], message["column"])
    current = store.get(key)
    if current is not None and message["timestamp"] <= current[1]:
        return False
    store[key] = (message["value"], message["timestamp"])
    return True


def apply_all(store: Store, messages: list[dict]) -> None:
    for message in messages:
        apply_message(store, message)


# --- Merkle trie over message timestamps (mirror of app/.../sync/Merkle.kt) ---

MINUTE_MS = 60_000
KEY_LEN = 18


def fnv1a32(s: str) -> int:
    h = 0x811C9DC5
    for ch in s:
        h ^= ord(ch)
        h = (h * 0x01000193) & 0xFFFFFFFF
    return h


class Merkle:
    def __init__(self) -> None:
        self.hash = 0
        self.children: dict[str, "Merkle"] = {}


def merkle_build(packed_timestamps) -> Merkle:
    root = Merkle()
    for ts in packed_timestamps:
        merkle_insert(root, ts)
    return root


def merkle_insert(root: Merkle, packed: str) -> None:
    h = fnv1a32(packed)
    key = _key_of(Hlc.parse(packed).millis)
    root.hash ^= h
    node = root
    for c in key:
        node = node.children.setdefault(c, Merkle())
        node.hash ^= h


def merkle_diff(a: Merkle, b: Merkle) -> int | None:
    """Earliest divergent time (epoch millis, minute-floor) or None if equal."""
    if a.hash == b.hash:
        return None
    na, nb = a, b
    prefix = ""
    while True:
        keys = sorted(set(na.children) | set(nb.children))
        diff_key = next(
            (
                k
                for k in keys
                if (na.children[k].hash if k in na.children else 0)
                != (nb.children[k].hash if k in nb.children else 0)
            ),
            None,
        )
        if diff_key is None:
            return _prefix_to_millis(prefix)
        prefix += diff_key
        na = na.children.get(diff_key) or Merkle()
        nb = nb.children.get(diff_key) or Merkle()


def merkle_to_dict(node: Merkle) -> dict:
    return {"hash": node.hash, "children": {k: merkle_to_dict(v) for k, v in node.children.items()}}


def merkle_from_dict(data: dict) -> Merkle:
    node = Merkle()
    node.hash = int(data.get("hash", 0))
    for k, v in (data.get("children") or {}).items():
        node.children[k] = merkle_from_dict(v)
    return node


def _key_of(millis: int) -> str:
    return _to_base3(millis // MINUTE_MS).rjust(KEY_LEN, "0")


def _to_base3(n: int) -> str:
    if n == 0:
        return "0"
    digits = []
    while n:
        digits.append(str(n % 3))
        n //= 3
    return "".join(reversed(digits))


def _prefix_to_millis(prefix: str) -> int:
    if not prefix:
        return 0
    return int(prefix.ljust(KEY_LEN, "0"), 3) * MINUTE_MS
