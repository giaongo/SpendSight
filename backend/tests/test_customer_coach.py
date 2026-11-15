import json

from app.verda import answer_language, chat_url, spending_relevance


def test_chat_url_accepts_the_containers_base():
    assert chat_url("https://containers.verda.com/demo") == (
        "https://containers.verda.com/demo/v1/chat/completions"
    )
    assert chat_url("https://containers.verda.com/demo/v1") == (
        "https://containers.verda.com/demo/v1/chat/completions"
    )


def test_the_reply_language_follows_the_question():
    assert answer_language("What is my balance?") == "English"
    assert answer_language("Tell me a joke") == "English"
    # English only for now: whatever the language of the question, the answer is English.
    assert answer_language("Mikä on saldoni?") == "English"
    assert answer_language("Wie hoch ist mein Kontostand?") == "English"
    assert spending_relevance("Mikä on saldoni?") == "spending"
    assert spending_relevance("Mikä on Suomen pääkaupunki?") == "other"
    assert spending_relevance("Wie hoch ist mein Kontostand?") == "spending"
    assert spending_relevance("Hur mycket har jag på kontot?") == "spending"


def test_spending_questions_keep_the_customers_figures():
    assert spending_relevance("How much left my account in September?") == "spending"
    assert spending_relevance("What is my balance?") == "spending"
    assert spending_relevance("What is the capital of Finland?") == "other"
    assert spending_relevance("Tell me a joke") == "other"
    assert spending_relevance("What is a mortgage?") == "other"


def test_customer_coach_ask_sends_only_that_customers_facts(client, monkeypatch):
    monkeypatch.setenv("VERDA_API_KEY", "test-key")
    monkeypatch.setenv("VERDA_BASE_URL", "https://containers.verda.com/demo")
    monkeypatch.setenv("VERDA_MODEL", "Qwen/Qwen2.5-1.5B-Instruct")
    captured = {}

    class FakeResponse:
        def __enter__(self):
            return self

        def __exit__(self, *args):
            return False

        def read(self):
            return json.dumps(
                {
                    "model": "Qwen/Qwen2.5-1.5B-Instruct",
                    "choices": [{"message": {"content": "September spending was €684.50."}}],
                }
            ).encode()

    def fake_urlopen(request, timeout=0):
        captured["url"] = request.full_url
        captured["auth"] = request.get_header("Authorization")
        captured["body"] = json.loads(request.data.decode())
        captured["timeout"] = timeout
        return FakeResponse()

    monkeypatch.setattr("app.verda.urlopen", fake_urlopen)
    response = client.post(
        "/customers/CUST00002/coach/ask",
        json={"text": "How much left my account in September?"},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["customer_id"] == "CUST00002"
    assert body["answer"] == "September spending was €684.50."
    assert body["source"] == "verda"
    assert captured["url"] == "https://containers.verda.com/demo/v1/chat/completions"
    assert captured["auth"] == "Bearer test-key"
    sent = json.dumps(captured["body"])
    assert "CUST00002" in sent
    assert "How much left my account in September?" in sent
    # Amounts go to the model as the service's euro strings, never as cents to divide.
    facts = captured["body"]["messages"][1]["content"]
    assert "€1,539.16" in facts and "153916" not in facts
    assert "€4,723.58" not in facts and "_cents" not in facts
    assert body["uses_customer_data"] is True
    assert "Answer in English" in captured["body"]["messages"][0]["content"]


def test_other_questions_are_sent_without_customer_facts(client, monkeypatch):
    monkeypatch.setenv("VERDA_API_KEY", "test-key")
    monkeypatch.setenv("VERDA_BASE_URL", "https://containers.verda.com/demo")
    captured = {}

    class FakeResponse:
        def __enter__(self):
            return self

        def __exit__(self, *args):
            return False

        def read(self):
            return json.dumps(
                {
                    "model": "Qwen/Qwen2.5-1.5B-Instruct",
                    "choices": [{"message": {"content": "Helsinki."}}],
                }
            ).encode()

    def fake_urlopen(request, timeout=0):
        captured["body"] = json.loads(request.data.decode())
        return FakeResponse()

    monkeypatch.setattr("app.verda.urlopen", fake_urlopen)
    response = client.post(
        "/customers/CUST00002/coach/ask",
        json={"text": "What is the capital of Finland?"},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["answer"] == "Helsinki."
    assert body["uses_customer_data"] is False
    sent = json.dumps(captured["body"])
    assert "What is the capital of Finland?" in sent
    assert "153916" not in sent
    assert "facts" not in sent
    assert "Answer in English" in captured["body"]["messages"][0]["content"]


def test_a_finnish_question_still_gets_an_english_answer(client, monkeypatch):
    monkeypatch.setenv("VERDA_API_KEY", "test-key")
    monkeypatch.setenv("VERDA_BASE_URL", "https://containers.verda.com/demo")
    captured = {}

    class FakeResponse:
        def __enter__(self):
            return self

        def __exit__(self, *args):
            return False

        def read(self):
            return json.dumps(
                {
                    "model": "Qwen/Qwen2.5-1.5B-Instruct",
                    "choices": [{"message": {"content": "Saldosi on €1 539,16."}}],
                }
            ).encode()

    def fake_urlopen(request, timeout=0):
        captured["body"] = json.loads(request.data.decode())
        return FakeResponse()

    monkeypatch.setattr("app.verda.urlopen", fake_urlopen)
    response = client.post(
        "/customers/CUST00002/coach/ask",
        json={"text": "Mikä on saldoni?"},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["uses_customer_data"] is True
    assert not body["disclaimer"].startswith("Tämä vastaus")
    assert "Answer in English" in captured["body"]["messages"][0]["content"]
    assert '"language": "English"' in captured["body"]["messages"][1]["content"]


def test_a_german_question_still_gets_an_english_answer(client, monkeypatch):
    monkeypatch.setenv("VERDA_API_KEY", "test-key")
    monkeypatch.setenv("VERDA_BASE_URL", "https://containers.verda.com/demo")
    systems = []

    class FakeResponse:
        def __init__(self, content: str):
            self._content = content

        def __enter__(self):
            return self

        def __exit__(self, *args):
            return False

        def read(self):
            return json.dumps(
                {
                    "model": "Qwen/Qwen2.5-1.5B-Instruct",
                    "choices": [{"message": {"content": self._content}}],
                }
            ).encode()

    def fake_urlopen(request, timeout=0):
        body = json.loads(request.data.decode())
        system = body["messages"][0]["content"]
        systems.append(system)
        if "Reply with one word" in system:
            return FakeResponse("spending")
        return FakeResponse("Dein Kontostand beträgt €4.723,58.")

    monkeypatch.setattr("app.verda.urlopen", fake_urlopen)
    response = client.post(
        "/customers/CUST00001/coach/ask",
        json={"text": "Wie hoch ist mein Kontostand?"},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["answer"] == "Dein Kontostand beträgt €4.723,58."
    assert body["uses_customer_data"] is True
    assert not body["disclaimer"].startswith("Diese Antwort")
    assert any("Answer in English" in system for system in systems)
    assert not any("Answer in German" in system for system in systems)


def test_customer_coach_ask_needs_the_deployment_settings(client, monkeypatch):
    monkeypatch.setenv("VERDA_API_KEY", "")
    monkeypatch.setenv("VERDA_BASE_URL", "")
    response = client.post("/customers/CUST00002/coach/ask", json={"text": "What is my balance?"})
    assert response.status_code == 503
    assert "VERDA_API_KEY" in response.json()["detail"]
    assert client.post("/customers/NOPE/coach/ask", json={"text": "Hello"}).status_code == 404
