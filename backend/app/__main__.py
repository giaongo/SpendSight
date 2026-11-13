from pathlib import Path

import uvicorn

CERT = Path(__file__).resolve().parents[1] / ".cache" / "certs" / "localhost.crt"
KEY = Path(__file__).resolve().parents[1] / ".cache" / "certs" / "localhost.key"


def main() -> None:
    if not CERT.is_file() or not KEY.is_file():
        raise SystemExit(f"Missing TLS certificate at {CERT}")
    uvicorn.run(
        "app.main:app",
        host="127.0.0.1",
        port=8000,
        ssl_certfile=str(CERT),
        ssl_keyfile=str(KEY),
    )


if __name__ == "__main__":
    main()
