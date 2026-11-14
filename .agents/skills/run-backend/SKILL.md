---
name: run-backend
description: Start the SpendSight Python API on https://127.0.0.1:8000. Use when the user runs /run-backend or asks to run, start, or open the SpendSight backend.
allowed-tools: Bash
---

Start the SpendSight API and leave it running.

1. Work in `/Users/sond/Desktop/Work/Hackathon/backend`. If the current directory is somewhere else, `cd` there first.
2. If `https://127.0.0.1:8000/health` already returns `{"ok":true,"service":"spendsight"}`, the service is up. Report that URL and leave the running process alone. Check with `curl -fsS -k --max-time 5 https://127.0.0.1:8000/health`.
3. The process needs `.venv/bin/python` and the certificate pair `.cache/certs/localhost.crt` and `.cache/certs/localhost.key`. If `.venv/bin/python` is missing, stop and say the virtualenv is missing. If the certificate files are missing, create them and leave an existing pair as it is:

```bash
mkdir -p .cache/certs
openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout .cache/certs/localhost.key \
  -out .cache/certs/localhost.crt \
  -days 825 \
  -subj "/CN=localhost" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"
```

The phone trusts a certificate whose CN or SAN is `localhost` or `127.0.0.1`.
4. Start the server in the background. It keeps running after the command returns. It listens on this computer only.

```bash
.venv/bin/python -m app
```

That runs uvicorn with HTTPS on `127.0.0.1:8000`, using the certificate pair above. The first start loads the Junction CSVs and can take several minutes before it listens. A process that is still loading is not a failure.
5. Request `https://127.0.0.1:8000/health` with `curl -k` until it returns ok, or until the process exits. On failure, show the error and stop. When it is up, report `https://127.0.0.1:8000` and `https://127.0.0.1:8000/docs`. The browser warns once because the certificate is self-signed.
