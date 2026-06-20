import json
from pathlib import Path

import pytest

from app.sync import (
    ClockDriftError,
    Hlc,
    HlcClock,
    apply_all,
    apply_message,
    merkle_build,
    merkle_diff,
)

VECTORS = json.loads(
    (Path(__file__).resolve().parent / "fixtures" / "sync-vectors.json").read_text()
)


@pytest.mark.parametrize("case", VECTORS["pack"])
def test_pack_vectors(case):
    assert Hlc(case["millis"], case["counter"], case["node"]).pack() == case["packed"]


@pytest.mark.parametrize("case", VECTORS["order"])
def test_order_vectors(case):
    a, b = case["a"], case["b"]
    cmp = (a > b) - (a < b)
    assert cmp == case["cmp"]


@pytest.mark.parametrize("case", VECTORS["merge"], ids=lambda c: c["name"])
def test_merge_vectors(case):
    store: dict = {}
    apply_all(store, case["messages"])
    materialised = {f"{d}|{r}|{c}": v for (d, r, c), (v, _) in store.items()}
    for key, expected in case["expected"].items():
        assert materialised[key] == expected


def test_merge_is_order_independent():
    messages = VECTORS["merge"][0]["messages"]
    forward: dict = {}
    apply_all(forward, messages)
    backward: dict = {}
    apply_all(backward, list(reversed(messages)))
    assert {k: v[0] for k, v in forward.items()} == {k: v[0] for k, v in backward.items()}


@pytest.mark.parametrize("case", VECTORS["merkle"]["build"])
def test_merkle_build_vectors(case):
    assert merkle_build(case["timestamps"]).hash == case["rootHash"]


@pytest.mark.parametrize("case", VECTORS["merkle"]["diff"], ids=lambda c: str(c["expectedMillis"]))
def test_merkle_diff_vectors(case):
    a = merkle_build(case["a"])
    b = merkle_build(case["b"])
    assert merkle_diff(a, b) == case["expectedMillis"]


def test_parse_round_trips():
    hlc = Hlc(1_700_000_000_000, 255, "abc-def")
    assert Hlc.parse(hlc.pack()) == hlc


def test_send_and_recv_monotonic():
    clock = HlcClock("A")
    t1 = clock.send(1000)
    t2 = clock.send(1000)
    assert t2.counter == t1.counter + 1
    advanced = clock.recv(Hlc(9000, 3, "B"), now=1000)
    assert advanced.millis == 9000 and advanced.counter == 4


def test_counter_overflow_raises():
    clock = HlcClock("A")
    with pytest.raises(ClockDriftError):
        for _ in range(0xFFFF + 2):
            clock.send(1000)


def test_stale_rejected():
    store: dict = {}
    clock = HlcClock("A")
    newer = {"timestamp": clock.send(1001).pack(), "dataset": "recipes", "rowId": "r1", "column": "name", "value": '"new"'}
    older = {"timestamp": Hlc(1000, 0, "A").pack(), "dataset": "recipes", "rowId": "r1", "column": "name", "value": '"old"'}
    assert apply_message(store, newer) is True
    assert apply_message(store, older) is False
    assert store[("recipes", "r1", "name")][0] == '"new"'
