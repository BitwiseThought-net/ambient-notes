FROM python:3.12-slim AS base

# ffmpeg is required by pydub for non-WAV audio decoding; build-essential is
# needed to build the optional dejavu/pydub native deps.
RUN apt-get update && apt-get install -y --no-install-recommends \
        ffmpeg \
        build-essential \
        libsndfile1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /srv/app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

# Optional: local audio fingerprinting. Left as a separate, best-effort layer
# so the image still builds (with dejavu disabled) if this ever breaks on a
# given platform -- see docs/PROVIDERS.md for enabling it manually instead.
RUN pip install --no-cache-dir PyDejavu==0.1.3 mysqlclient==2.2.4 || \
    echo "WARNING: dejavu install failed; local fingerprinting provider will report unavailable."

COPY app ./app

ENV PYTHONUNBUFFERED=1
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://localhost:8080/api/v1/health', timeout=3).status==200 else 1)"

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080"]
