# Single-image deployment for self-hosters. Build context = server/
FROM python:3.12-slim

WORKDIR /srv

# Build needs the app package present (hatchling packages = ["app"]), so copy it
# before installing.
COPY pyproject.toml ./
COPY app ./app
RUN pip install --no-cache-dir .

# Run as a non-root user; pre-create /data so a fresh named volume inherits its
# ownership on first mount (a bind mount must be chown'ed to uid 10001 on the host).
RUN useradd -u 10001 -m app && mkdir -p /data && chown app:app /data
USER app

ENV OPENCOOK_DATA_DIR=/data
VOLUME ["/data"]
EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
