import json
from io import BytesIO
from urllib.error import URLError

from app import statfi


def test_statistics_finland_query_returns_json_stat2(client, monkeypatch):
    expected = {"version": "2.0", "class": "dataset", "value": [1, 2, 3]}
    requested = {}

    def fake_urlopen(request, timeout):
        requested["url"] = request.full_url
        requested["method"] = request.get_method()
        requested["body"] = json.loads(request.data)
        requested["timeout"] = timeout
        return BytesIO(json.dumps(expected).encode())

    monkeypatch.setattr(statfi, "urlopen", fake_urlopen)

    response = client.get("/statistics/education-assets")

    assert response.status_code == 200
    assert response.json() == expected
    assert requested == {
        "url": statfi.STATFI_URL,
        "method": "POST",
        "body": statfi.EDUCATION_ASSETS_QUERY,
        "timeout": 15,
    }


def test_statistics_finland_unavailable_returns_bad_gateway(client, monkeypatch):
    def fail_urlopen(request, timeout):
        raise URLError("offline")

    monkeypatch.setattr(statfi, "urlopen", fail_urlopen)

    response = client.get("/statistics/education-assets")

    assert response.status_code == 502
    assert response.json()["detail"] == "Could not reach Statistics Finland API"
