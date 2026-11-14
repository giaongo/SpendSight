"""Test setup.

By default the tests read the small fixture in ``tests/fixtures/junction`` (three
whole customers), with the index built in a throwaway folder, so they run in CI
without the gitignored dataset (OPS-08). Per-customer figures match the full
dataset because every row of those customers is kept.

``SPENDSIGHT_TEST_FULL_DATA=1 pytest`` runs against the real Junction folder and
also runs the ``full_data`` tests, which check dataset-wide totals.
"""

import os
import tempfile
from pathlib import Path

import pytest

FULL_DATA = os.environ.get("SPENDSIGHT_TEST_FULL_DATA") == "1"

# app.main builds the app at import time, so the data folders are set before that import.
if not FULL_DATA:
    os.environ["JUNCTION_DIR"] = str(Path(__file__).resolve().parent / "fixtures" / "junction")
    os.environ["SPENDSIGHT_CACHE_DIR"] = tempfile.mkdtemp(prefix="spendsight-test-cache-")

from fastapi.testclient import TestClient  # noqa: E402

from app.main import create_app  # noqa: E402


def pytest_collection_modifyitems(config, items):
    if FULL_DATA:
        return
    skip = pytest.mark.skip(reason="needs the full Junction dataset; set SPENDSIGHT_TEST_FULL_DATA=1")
    for item in items:
        if "full_data" in item.keywords:
            item.add_marker(skip)


@pytest.fixture
def client():
    application = create_app()
    with TestClient(application) as test_client:
        yield test_client
