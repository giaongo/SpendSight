"""The Statistics Finland query behind the coach's peer answers."""

import json
from io import BytesIO
from urllib.error import URLError

import pytest

from app import statfi


def test_statistics_finland_query_returns_json_stat2(monkeypatch):
    expected = {"version": "2.0", "class": "dataset", "value": [1, 2, 3]}
    requested = {}

    def fake_urlopen(request, timeout):
        requested["url"] = request.full_url
        requested["method"] = request.get_method()
        requested["body"] = json.loads(request.data)
        requested["timeout"] = timeout
        return BytesIO(json.dumps(expected).encode())

    monkeypatch.setattr(statfi, "urlopen", fake_urlopen)

    assert statfi.fetch_education_assets() == expected
    assert requested == {
        "url": statfi.STATFI_URL,
        "method": "POST",
        "body": statfi.EDUCATION_ASSETS_QUERY,
        "timeout": 15,
    }


def test_statistics_finland_unavailable_raises(monkeypatch):
    def fail_urlopen(request, timeout):
        raise URLError("offline")

    monkeypatch.setattr(statfi, "urlopen", fail_urlopen)

    with pytest.raises(statfi.StatisticsApiError, match="Could not reach Statistics Finland API"):
        statfi.fetch_education_assets()
