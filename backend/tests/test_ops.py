"""OPS-02: container settings and the health route."""

from pathlib import Path

from app import junction_db


def test_healthz_reports_ready_index(client):
    response = client.get("/healthz")
    assert response.status_code == 200
    body = response.json()
    assert body["ok"] is True
    assert body["index"]["status"] == "ready"
    assert body["index"]["rows"] > 0


def test_cache_dir_defaults_to_backend_cache(monkeypatch):
    monkeypatch.delenv("SPENDSIGHT_CACHE_DIR", raising=False)
    assert junction_db.cache_dir() == Path(junction_db.__file__).resolve().parents[1] / ".cache"


def test_cache_dir_override(monkeypatch, tmp_path):
    monkeypatch.setenv("SPENDSIGHT_CACHE_DIR", str(tmp_path))
    assert junction_db.cache_dir() == tmp_path
    assert junction_db.transaction_db_path() == tmp_path / "junction.sqlite"
