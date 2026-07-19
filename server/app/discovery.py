"""Advertise the server on the LAN via mDNS (zeroconf) so the app discovers it
without a typed address. Best-effort: if it fails (no multicast, etc.) the app
falls back to a manually entered address.
"""

import logging
import socket

from zeroconf import ServiceInfo
from zeroconf.asyncio import AsyncZeroconf

from app.config import get_settings

logger = logging.getLogger(__name__)

SERVICE_TYPE = "_opencook._tcp.local."


def _local_ip() -> str:
    """Primary outbound LAN IP. The UDP 'connect' sends no packets — it just makes
    the OS pick the interface/route, so this works offline too."""
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("8.8.8.8", 80))
        return sock.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        sock.close()


class MdnsAdvertiser:
    """Registers/unregisters the _opencook._tcp service for the app's discovery."""

    def __init__(self) -> None:
        self._azc: AsyncZeroconf | None = None

    async def start(self) -> None:
        settings = get_settings()
        name = settings.server_name
        ip = _local_ip()
        info = ServiceInfo(
            SERVICE_TYPE,
            f"{name}.{SERVICE_TYPE}",
            addresses=[socket.inet_aton(ip)],
            port=settings.port,
            # "role" lets the app tell the real server apart from peer phones, which
            # advertise the same _opencook._tcp service with role=peer for P2P sync.
            properties={"name": name, "role": "server"},
            server=f"{socket.gethostname()}.local.",
        )
        try:
            self._azc = AsyncZeroconf()
            await self._azc.async_register_service(info)
            logger.info("mDNS: advertising %r at %s:%d", name, ip, settings.port)
        except Exception:  # noqa: BLE001 - discovery is best-effort, never fatal
            logger.warning("mDNS advertisement failed; app must use manual address.", exc_info=True)
            self._azc = None

    async def stop(self) -> None:
        if self._azc is not None:
            await self._azc.async_unregister_all_services()
            await self._azc.async_close()
            self._azc = None
