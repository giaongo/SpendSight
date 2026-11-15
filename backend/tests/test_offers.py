from datetime import date

import pytest

from app.offers import CAMPAIGNS, COUPONS, ISSUER, SAMPLE_NOTE

DEMO_IDS = ["CPN-ROSSO", "CPN-SMARKET-FRUIT", "CPN-ALEPA-SALAD"]


@pytest.fixture
def offers_client(client):
    client.app.state.offers.today = lambda: date(2026, 9, 30)
    return client


def _ids(body: dict) -> list[str]:
    return [row["coupon_id"] for row in body["coupons"]]


def test_every_offer_is_an_invented_s_group_offer():
    assert ISSUER == "S Group"
    assert SAMPLE_NOTE == "Demo offers for Junction: S Group brands, invented discounts."
    campaign_ids = {campaign.campaign_id for campaign in CAMPAIGNS}
    for coupon in COUPONS:
        assert coupon.campaign_id is None or coupon.campaign_id in campaign_ids


def test_the_offers_default_to_the_demo_day(client):
    assert client.get("/campaigns").json()["as_of"] == "2026-09-30"


def test_campaigns_list_only_the_running_ones(offers_client):
    body = offers_client.get("/campaigns").json()
    assert [row["title"] for row in body["campaigns"]] == [
        "Prisma autumn home week",
        "Sokos Hotels autumn weekends",
    ]
    assert [row["brand"] for row in body["campaigns"]] == ["Prisma", "Sokos Hotels"]
    assert body["campaigns"][0]["ends_on"] == "2026-10-11"
    assert body["campaigns"][0]["days_left"] == 11


def test_the_demo_coupons_in_order(offers_client):
    body = offers_client.get("/customers/CUST00001/coupons").json()
    assert _ids(body) == DEMO_IDS
    assert body["count"] == 3
    assert body["activated_count"] == 0
    rosso, smarket, alepa = body["coupons"]
    assert rosso["title"] == "Rosso coupon"
    assert rosso["benefit"] == "−15 %"
    assert rosso["line"] == "−15 % on your next 4 visits · renews monthly"
    assert rosso["tag"] == "Fits your everyday spending"
    assert rosso["uses_per_month"] == 4
    assert rosso["used_this_month"] == 0
    assert rosso["renews_on"] == "2026-10-01"
    assert rosso["activate"] == "/customers/CUST00001/coupons/CPN-ROSSO/activate"
    assert rosso["deactivate"] == "/customers/CUST00001/coupons/CPN-ROSSO/deactivate"
    assert (smarket["title"], smarket["benefit"], smarket["line"]) == (
        "S-market: fruit and vegetables",
        "−10 %",
        "Until 11 Oct",
    )
    assert (alepa["title"], alepa["benefit"], alepa["line"]) == (
        "Alepa: lunch salads",
        "−15 %",
        "Until 15 Oct",
    )
    assert smarket["tag"] is None and smarket["uses_per_month"] is None


def test_coupons_outside_their_dates_stay_out(offers_client):
    assert "CPN-SUMMER-ICE" not in _ids(offers_client.get("/customers/CUST00001/coupons").json())
    offers_client.app.state.offers.today = lambda: date(2026, 10, 12)
    assert _ids(offers_client.get("/customers/CUST00001/coupons").json()) == ["CPN-ROSSO", "CPN-ALEPA-SALAD"]


def test_activating_and_turning_off_a_coupon(offers_client):
    post = offers_client.post
    first = post("/customers/CUST00001/coupons/CPN-ROSSO/activate").json()
    assert first["activated"] is True
    assert first["activated_at"]
    again = post("/customers/CUST00001/coupons/CPN-ROSSO/activate").json()
    assert again["activated_at"] == first["activated_at"]
    assert offers_client.get("/customers/CUST00001/coupons").json()["activated_count"] == 1
    assert offers_client.get("/customers/CUST00002/coupons").json()["activated_count"] == 0

    off = post("/customers/CUST00001/coupons/CPN-ROSSO/deactivate").json()
    assert off["activated"] is False
    assert offers_client.get("/customers/CUST00001/coupons").json()["activated_count"] == 0


def test_demo_reset_turns_every_coupon_off(offers_client):
    offers_client.post("/customers/CUST00001/coupons/CPN-ROSSO/activate")
    offers_client.post("/customers/CUST00001/coupons/CPN-ALEPA-SALAD/activate")
    assert offers_client.post("/demo/reset").json() == {"ok": True}
    assert offers_client.get("/customers/CUST00001/coupons").json()["activated_count"] == 0


def test_activation_errors(offers_client):
    post = offers_client.post
    assert post("/customers/CUST00001/coupons/CPN-NOPE/activate").status_code == 404
    assert post("/customers/CUST00001/coupons/CPN-NOPE/deactivate").status_code == 404
    assert post("/customers/CUST00001/coupons/CPN-SUMMER-ICE/activate").status_code == 409
    assert post("/customers/CUST99999/coupons/CPN-ROSSO/activate").status_code == 404
    assert offers_client.get("/customers/CUST99999/coupons").status_code == 404
