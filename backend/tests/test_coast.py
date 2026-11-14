import json

from app.verda import chat_url


def test_chat_url_accepts_the_containers_base():
    assert chat_url("https://containers.verda.com/demo") == (
        "https://containers.verda.com/demo/v1/chat/completions"
    )
    assert chat_url("https://containers.verda.com/demo/v1") == (
        "https://containers.verda.com/demo/v1/chat/completions"
    )


def test_coast_ask_sends_only_that_customers_facts(client, monkeypatch):
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
        "/customers/CUST00002/coast/ask",
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
    assert "153916" in sent
    assert "472358" not in sent


def test_coast_ask_needs_the_deployment_settings(client, monkeypatch):
    monkeypatch.setenv("VERDA_API_KEY", "")
    monkeypatch.setenv("VERDA_BASE_URL", "")
    response = client.post("/customers/CUST00002/coast/ask", json={"text": "What is my balance?"})
    assert response.status_code == 503
    assert "VERDA_API_KEY" in response.json()["detail"]
    assert client.post("/customers/NOPE/coast/ask", json={"text": "Hello"}).status_code == 404
