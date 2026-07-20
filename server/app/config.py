"""Application configuration, sourced from environment variables (12-factor).

Keeping all config in env vars makes the later move to Docker trivial.
"""

import socket
from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="OPENCOOK_", env_file=".env", extra="ignore")

    # Where uploaded images, crops and the SQLite database live.
    data_dir: Path = Path("./data")

    # HTTP server bind.
    host: str = "0.0.0.0"
    port: int = 8000

    # Friendly name advertised over mDNS so the app shows it in the server picker.
    server_name: str = socket.gethostname()

    # Ollama vision model used for recipe extraction. qwen2.5vl:7b was validated
    # against real cookbook photos and far outperforms llama3.2-vision:11b on
    # German OCR, multi-recipe pages, rotation and reading printed nutrition.
    ollama_base_url: str = "http://localhost:11434"
    ollama_model: str = "qwen2.5vl:7b"

    # How often the in-process worker polls the jobs table (seconds).
    worker_poll_interval: float = 2.0

    # Where backup archives are written, and how many to keep (rotation). Point
    # backup_dir at an external/network drive (or copy archives off-box) so a backup
    # survives an OS reinstall. Default: under data_dir (so it is never self-included
    # in an archive, which only contains opencook.db + images/).
    backup_dir: Path | None = None
    backup_keep: int = 14

    # Admin password bootstrap. Applied at startup only when no password is set yet, so
    # a later change through the web console survives a restart. This (or the "set
    # password" form the console shows on first visit) is how a fresh server gets one —
    # the app deliberately has no admin surface: everything it could do, the console at
    # /admin does, and more.
    admin_password: str | None = None

    @property
    def db_path(self) -> Path:
        return self.data_dir / "opencook.db"

    @property
    def images_dir(self) -> Path:
        return self.data_dir / "images"

    @property
    def backups_dir(self) -> Path:
        return self.backup_dir or (self.data_dir / "backups")


@lru_cache
def get_settings() -> Settings:
    settings = Settings()
    settings.data_dir.mkdir(parents=True, exist_ok=True)
    settings.images_dir.mkdir(parents=True, exist_ok=True)
    settings.backups_dir.mkdir(parents=True, exist_ok=True)
    return settings
