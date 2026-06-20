from fastapi.testclient import TestClient
from PIL import Image

from app.config import get_settings
from app.main import app


def test_get_image_returns_stored_file() -> None:
    settings = get_settings()
    name = "test_serve.jpg"
    Image.new("RGB", (20, 20), (120, 200, 80)).save(settings.images_dir / name)
    try:
        with TestClient(app) as client:
            response = client.get(f"/images/{name}")
            assert response.status_code == 200
            assert response.headers["content-type"] == "image/jpeg"
            assert response.content[:2] == b"\xff\xd8"  # JPEG magic
    finally:
        (settings.images_dir / name).unlink(missing_ok=True)


def test_get_unknown_image_returns_404() -> None:
    with TestClient(app) as client:
        assert client.get("/images/nope.jpg").status_code == 404


def test_get_image_rejects_path_traversal() -> None:
    with TestClient(app) as client:
        # %2e%2e%2f == "../" — must not escape the images directory.
        response = client.get("/images/..%2f..%2fconfig.py")
        assert response.status_code in (400, 404)
