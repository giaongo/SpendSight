"""Try coach questions from the terminal and see what the model gets and gives back.

Run from backend/:

    .venv/bin/python scripts/ask.py "Where did my money go?"
    .venv/bin/python scripts/ask.py "What does a typical household spend?" --prompt
    .venv/bin/python scripts/ask.py --dry-run "How much did I spend on restaurants?"
    .venv/bin/python scripts/ask.py                 # type questions one by one

--dry-run never calls Verda: it shows the category, the card, and the prompt
the model would get. Without it, the call uses backend/.env like the service.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import textwrap
from pathlib import Path

BACKEND = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BACKEND))

FIXTURE = BACKEND / "tests" / "fixtures" / "junction"


def _rule(title: str) -> None:
    print(f"\n── {title} " + "─" * max(0, 60 - len(title)))


def _show_prompt(messages: list[dict], full: bool) -> None:
    system, user = messages[0]["content"], messages[1]["content"]
    _rule("system prompt")
    print(system)
    _rule("user message")
    try:
        payload = json.loads(user)
    except json.JSONDecodeError:
        print(user)
        return
    if not full and isinstance(payload.get("facts"), dict):
        facts = payload["facts"]
        payload["facts"] = {
            key: (f"<{len(value)} items, --full to show>" if isinstance(value, list) and len(value) > 6 else value)
            for key, value in facts.items()
        }
    print(json.dumps(payload, indent=2, ensure_ascii=False))


def _show_card(card: dict | None) -> None:
    _rule("card")
    if card is None:
        print("(none)")
        return
    print(f"{card['kind']} · {card['title']} · {card['context']}")
    for name in ("headline", "reference"):
        figure = card.get(name)
        if figure:
            print(f"  {name:10} {figure['label']}: {figure['amount']}")
    if card.get("verdict"):
        print(f"  verdict    {card['verdict']} — {card.get('verdict_detail') or ''}")
    for item in card["items"]:
        mark = "▶" if item["highlight"] else " "
        bar = "█" * ((item["bar_pct"] or 0) // 10)
        detail = f"  ({item['detail']})" if item.get("detail") else ""
        print(f"  {mark} {item['label'][:34]:34} {item['amount']:>12} {bar}{detail}")
    if card.get("options"):
        options = card["options"]
        print(f"  options    {options['label']}: {[c['key'] for c in options['choices']]}, selected {options['selected']}")
    print("  how        " + textwrap.shorten(card["how"], 110))


def ask(question: str, args, book, statistics) -> None:
    from app import verda

    calls: list[tuple[list[dict], str]] = []
    real_complete = verda._complete

    def traced(key, base, model, messages, *, temperature, max_tokens):
        is_classifier = "Reply with one word" in messages[0]["content"]
        if args.dry_run:
            answer = args.category if is_classifier else "(dry run: no model call)"
            used = "dry-run"
        else:
            answer, used = real_complete(key, base, model, messages, temperature=temperature, max_tokens=max_tokens)
        calls.append((messages, answer))
        return answer, used

    verda._complete = traced
    try:
        print(f"\n═══ {question}  [{book.customer_id}]")
        print("patterns say:", verda.question_category(question) or "unsure, the model sorts it")
        result = verda.ask_customer(book, question, statistics)
    except (verda.VerdaConfigError, verda.VerdaError) as exc:
        print(f"error: {exc}")
        return
    finally:
        verda._complete = real_complete

    for messages, answer in calls:
        if "Reply with one word" in messages[0]["content"]:
            print(f"model sorted it as: {answer!r}")
        elif args.prompt or args.dry_run:
            _show_prompt(messages, args.full)
            if not args.dry_run:
                _rule("raw model output (before the layout guard)")
                print(answer)

    if args.json:
        _rule("response JSON")
        print(json.dumps(result, indent=2, ensure_ascii=False))
        return
    _rule(f"answer · {result['category']} · {result['model'] or 'no model'}")
    print("LEAD:   " + result["lead"])
    print("DETAIL: " + (result["detail"] or "(none)"))
    _show_card(result["card"])
    _rule("footer")
    print(result["caption"])
    print("Ask next: " + " | ".join(f"{c['text']} ({c['category']})" for c in result["follow_ups"]))
    print(result["disclaimer"])


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("question", nargs="*", help="the question; leave out to type several")
    parser.add_argument("-c", "--customer", default="CUST00002", help="Junction customer id (default CUST00002)")
    parser.add_argument("--dry-run", action="store_true", help="show the prompt, do not call Verda")
    parser.add_argument(
        "--category",
        default="general",
        choices=["personal", "peer", "general"],
        help="in --dry-run, what the model classifier answers when the patterns are unsure",
    )
    parser.add_argument("--prompt", action="store_true", help="also print the prompt sent to the model")
    parser.add_argument("--full", action="store_true", help="print every fact in the prompt, not a summary")
    parser.add_argument("--json", action="store_true", help="print the whole response JSON")
    parser.add_argument("--fixture", action="store_true", help="use the three test customers (fast start)")
    args = parser.parse_args()

    if args.fixture:
        os.environ["JUNCTION_DIR"] = str(FIXTURE)
    if args.dry_run:
        os.environ.setdefault("VERDA_API_KEY", "dry-run")
        os.environ.setdefault("VERDA_BASE_URL", "https://dry-run.invalid")

    from app.junction_db import JunctionLibrary
    from app.statfi import StatisticsCache

    print("loading Junction data…", file=sys.stderr)
    library = JunctionLibrary()
    try:
        book = library.book(args.customer)
    except (KeyError, LookupError):
        sys.exit(f"No customer with transactions: {args.customer}")
    statistics = StatisticsCache()

    if args.question:
        ask(" ".join(args.question), args, book, statistics)
        return
    print("Type a question, or an empty line to stop.")
    while True:
        try:
            question = input("\n? ").strip()
        except (EOFError, KeyboardInterrupt):
            break
        if not question:
            break
        ask(question, args, book, statistics)


if __name__ == "__main__":
    main()
