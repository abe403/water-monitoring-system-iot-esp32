FROM python:3.11-slim
WORKDIR /app
COPY tools/chaos/pyproject.toml ./
COPY tools/chaos/src ./src
RUN pip install --no-cache-dir .
ENTRYPOINT ["wtm-conservation"]
