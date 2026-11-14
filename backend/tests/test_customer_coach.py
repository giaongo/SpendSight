import json

from app.verda import chat_url, spending_relevance


def test_chat_url_accepts_the_containers_base():
    assert chat_url("https://containers.verda.com/demo") == (
        "https://containers.verda.com/demo/v1/chat/completions"
    )
    assert chat_url("https://containers.verda.com/demo/v1") == (
        "https://containers.verda.com/demo/v1/chat/completions"
    )


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


def test_customer_coach_ask_needs_the_deployment_settings(client, monkeypatch):
    monkeypatch.setenv("VERDA_API_KEY", "")
    monkeypatch.setenv("VERDA_BASE_URL", "")
    response = client.post("/customers/CUST00002/coach/ask", json={"text": "What is my balance?"})
    assert response.status_code == 503
    assert "VERDA_API_KEY" in response.json()["detail"]
    assert client.post("/customers/NOPE/coach/ask", json={"text": "Hello"}).status_code == 404
