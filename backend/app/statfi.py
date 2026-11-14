"""Queries for Statistics Finland's public PxWeb API."""

from __future__ import annotations

import json
import time
from urllib.error import HTTPError
from urllib.request import Request, urlopen

STATFI_URL = "https://pxdata.stat.fi/PxWeb/api/v1/en/StatFin/vtutk/15jx.px"

EDUCATION_ASSETS_QUERY = {
    "query": [
        {
            "code": "koulutusaste_17_20180101",
            "selection": {
                "filter": "item",
                "values": ["SSS", "3_4", "5_6", "7", "8", "9_X"],
            },
        },
        {
            "code": "sukupuoli_9_20180101",
            "selection": {
                "filter": "item",
                "values": ["SSS", "1", "2"],
            },
        },
    ],
    "response": {"format": "json-stat2"},
}


class StatisticsApiError(Exception):
    """The Statistics Finland API could not return a usable response."""


def fetch_education_assets() -> dict:
    request = Request(
        STATFI_URL,
        data=json.dumps(EDUCATION_ASSETS_QUERY).encode("utf-8"),
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    try:
        with urlopen(request, timeout=15) as response:
            return json.loads(response.read())
    except HTTPError as exc:
        raise StatisticsApiError(f"Statistics Finland API returned HTTP {exc.code}") from exc
    except OSError as exc:
        raise StatisticsApiError("Could not reach Statistics Finland API") from exc
    except json.JSONDecodeError as exc:
        raise StatisticsApiError("Statistics Finland API returned invalid JSON") from exc


class StatisticsCache:
    """Keeps one Statistics Finland response and retries after a failure or the ttl."""

    def __init__(self, fetch=None, ttl_seconds: float = 6 * 60 * 60, clock=None) -> None:
        self._fetch = fetch or fetch_education_assets
        self._ttl = ttl_seconds
        self._clock = clock or time.monotonic
        self._data = None
        self._at: float | None = None

    def education_assets(self) -> dict:
        now = self._clock()
        if self._data is not None and self._at is not None and now - self._at < self._ttl:
            return self._data
        data = self._fetch()
        self._data = data
        self._at = now
        return data
