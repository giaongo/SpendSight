"""Cents, formatted the way the phone shows them."""

import math

MINUS = "−"


def js_round(value: float) -> int:
    """Match JavaScript Math.round for the design's half-up positive results."""
    if value >= 0:
        return int(math.floor(value + 0.5))
    return int(math.ceil(value - 0.5))


def eur(cents: int) -> str:
    sign = MINUS if cents < 0 else ""
    value = abs(int(cents))
    whole = f"{value // 100:,}"
    frac = f"{value % 100:02d}"
    return f"{sign}€{whole}.{frac}"


def parse_eur_cents(raw: str) -> int:
    """Parse a Junction amount such as '-27.32' or '3080.7' into cents."""
    text = raw.strip()
    if not text:
        raise ValueError("empty amount")
    negative = text[0] == "-"
    body = text[1:] if negative else text
    if "." in body:
        whole, frac = body.split(".", 1)
        frac = (frac + "00")[:2]
    else:
        whole, frac = body, "00"
    if not whole:
        whole = "0"
    cents = int(whole) * 100 + int(frac)
    return -cents if negative else cents
