from fastapi.testclient import TestClient

from app.main import app


def test_health() -> None:
    with TestClient(app) as client:
        response = client.get("/health")
        assert response.status_code == 200
        assert response.json() == {"status": "ok"}


def test_get_unknown_job_returns_404() -> None:
    with TestClient(app) as client:
        response = client.get("/jobs/does-not-exist")
        assert response.status_code == 404
