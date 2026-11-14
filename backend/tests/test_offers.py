from datetime import date

import pytest

from app.offers import CAMPAIGNS, COUPONS, ISSUER


@pytest.fixture
def offers_client(client):
    client.app.state.offers.today = lambda: date(2026, 11, 14)
    return client


def _ids(body: dict) -> set[str]:
    return {row["coupon_id"] for row in body["coupons"]}


def test_every_offer_is_from_s_pankki():
    assert ISSUER == "S-Pankki"
    campaign_ids = {campaign.campaign_id for campaign in CAMPAIGNS}
    for coupon in COUPONS:
        assert coupon.campaign_id is None or coupon.campaign_id in campaign_ids


def test_campaigns_list_only_the_running_ones(offers_client):
    body = offers_client.get("/campaigns").json()
    ids = [row["campaign_id"] for row in body["campaigns"]]
    assert ids == ["CMP-WINTER-2026", "CMP-SAVER-AUTUMN-2026", "CMP-TRAVEL-2026"]
    assert body["count"] == 3
    assert body["as_of"] == "2026-11-14"
    assert all(row["issuer"] == "S-Pankki" for row in body["campaigns"])
    winter = body["campaigns"][0]
    assert winter["days_left"] == 47
    assert winter["coupon_ids"] == ["CPN-RESTO-BONUS", "CPN-GROCERY-5", "CPN-TRANSPORT-3"]


def test_coupons_follow_what_the_customer_data_says(offers_client):
    first = offers_client.get("/customers/CUST00001/coupons").json()
    assert first["customer_id"] == "CUST00001"
    # Age 63 and already saving in funds.
    assert _ids(first) == {
        "CPN-RESTO-BONUS",
        "CPN-GROCERY-5",
        "CPN-FUND-TOPUP",
        "CPN-SAVINGS-RATE",
        "CPN-MONEY-REVIEW",
    }

    second = offers_client.get("/customers/CUST00002/coupons").json()
    # No investment deposits, so the starter fund coupon instead of the top-up.
    assert "CPN-FUND-NOFEE" in _ids(second)
    assert "CPN-FUND-TOPUP" not in _ids(second)

    borrower = offers_client.get("/customers/CUST04457/coupons").json()
    assert "CPN-HOME-EXTRA" in _ids(borrower)
    home = next(row for row in borrower["coupons"] if row["coupon_id"] == "CPN-HOME-EXTRA")
    assert home["reason"] == "Offered because you have a home loan application."


def test_coupons_outside_their_dates_stay_out(offers_client):
    for customer_id in ("CUST00001", "CUST00002", "CUST04457"):
        ids = _ids(offers_client.get(f"/customers/{customer_id}/coupons").json())
        assert "CPN-SUMMER-ICE" not in ids  # ended
        assert "CPN-SPRING-FUND" not in ids  # not started
        assert "CPN-TRANSPORT-3" not in ids  # starts 1 Dec


def test_coupon_money_is_cents_with_display_strings(offers_client):
    body = offers_client.get("/customers/CUST00001/coupons").json()
    grocery = next(row for row in body["coupons"] if row["coupon_id"] == "CPN-GROCERY-5")
    assert grocery["value_cents"] == 500
    assert grocery["value"] == "€5.00"
    assert grocery["min_spend_cents"] == 20_000
    assert grocery["min_spend"] == "€200.00"
    assert grocery["activated"] is False
    assert grocery["activate"] == "/customers/CUST00001/coupons/CPN-GROCERY-5/activate"


def test_activating_a_coupon_is_kept_per_customer(offers_client):
    response = offers_client.post("/customers/CUST00001/coupons/CPN-SAVINGS-RATE/activate")
    assert response.status_code == 200
    first = response.json()
    assert first["activated"] is True
    assert first["activated_at"]

    again = offers_client.post("/customers/CUST00001/coupons/CPN-SAVINGS-RATE/activate").json()
    assert again["activated_at"] == first["activated_at"]

    listed = offers_client.get("/customers/CUST00001/coupons").json()
    assert listed["activated_count"] == 1
    other = offers_client.get("/customers/CUST00002/coupons").json()
    assert other["activated_count"] == 0


def test_activation_errors(offers_client):
    post = offers_client.post
    assert post("/customers/CUST00001/coupons/CPN-NOPE/activate").status_code == 404
    # Under 30 only, and CUST00001 is 63.
    assert post("/customers/CUST00001/coupons/CPN-YOUNG-CARD/activate").status_code == 404
    assert post("/customers/CUST00001/coupons/CPN-SUMMER-ICE/activate").status_code == 409
    assert post("/customers/CUST99999/coupons/CPN-SAVINGS-RATE/activate").status_code == 404
    assert offers_client.get("/customers/CUST99999/coupons").status_code == 404
