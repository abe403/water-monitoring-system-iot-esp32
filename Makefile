.PHONY: build test up up-infra down verify clean firmware-validate test-chaos conservation conservation-local forward-start forward-stop forward-restart forward-status forward-test build-console test-console

CONTAINER_ENGINE ?= podman
ifeq ($(CONTAINER_ENGINE),podman)
COMPOSE ?= python -m podman_compose
else
COMPOSE ?= $(CONTAINER_ENGINE) compose
endif
PWSH ?= powershell.exe
FORWARD_SCRIPT ?= tools/podman-port-forward.ps1

# ─── Build ────────────────────────────────────────────────────────────────────

build: build-java build-python build-console

build-java:
	cd platform && ./gradlew build

build-python:
	cd ml && pip install -e ".[dev]" -q
	cd simulator && pip install -e ".[dev]" -q
	cd tools/chaos && pip install -e ".[dev]" -q

build-console:
	cd console && npm ci && npm run build

# ─── Test ─────────────────────────────────────────────────────────────────────

test: test-java test-python test-console

test-java:
	cd platform && ./gradlew test

test-python:
	cd ml && python -m pytest tests/ -q
	cd simulator && python -m pytest tests/ -q
	cd tools/chaos && python -m pytest tests/ -q

test-console:
	cd console && npm run lint && npm run test

test-chaos:
	cd tools/chaos && python -m pytest tests/ -q

# ─── Infrastructure ──────────────────────────────────────────────────────────

up: build-java
	cd infra && $(COMPOSE) --profile apps up -d --build

up-infra:
	cd infra && $(COMPOSE) up -d

down:
	cd infra && $(COMPOSE) --profile apps down

# ─── Verification ────────────────────────────────────────────────────────────

verify: test firmware-validate
	@echo "All checks passed."

firmware-validate:
	cd firmware && esphome config water-level.yaml > /dev/null
	cd firmware && esphome config pump-controller.yaml > /dev/null

# ─── Simulator ───────────────────────────────────────────────────────────────

simulate:
	cd simulator && python -m simulator.cli --device water-tank-01 --count 30 --output print

simulate-mqtt:
	cd simulator && python -m simulator.cli --device water-tank-01 --count 60 --output mqtt --broker localhost --speedup 20

# Requires a Docker-compatible engine, kind and kubectl. Deletes one Kafka broker during load.
conservation:
	bash tools/chaos/run-kind.sh

# Reusable smoke test for an already-populated local Compose stack.
conservation-local:
	$(CONTAINER_ENGINE) build -f tools/chaos/Dockerfile -t localhost/wtm/conservation:latest .
	$(CONTAINER_ENGINE) run --rm --network watermonitor-dev_default localhost/wtm/conservation:latest --mqtt-host=mosquitto --kafka-bootstrap=kafka:19092 --start-at=latest --devices=2 --records-per-device=20 --rate=50

# Windows Podman machine fallback for published ports when VirtioProxy is
# unhealthy. Uses OpenSSH local forwards through the machine's dynamically
# discovered SSH endpoint and container IPs. TimescaleDB is exposed at 15432
# because a host PostgreSQL commonly owns 5432.
forward-start:
	$(PWSH) -NoProfile -ExecutionPolicy Bypass -File $(FORWARD_SCRIPT) Start

forward-stop:
	$(PWSH) -NoProfile -ExecutionPolicy Bypass -File $(FORWARD_SCRIPT) Stop

forward-restart:
	$(PWSH) -NoProfile -ExecutionPolicy Bypass -File $(FORWARD_SCRIPT) Restart

forward-status:
	$(PWSH) -NoProfile -ExecutionPolicy Bypass -File $(FORWARD_SCRIPT) Status

forward-test:
	$(PWSH) -NoProfile -ExecutionPolicy Bypass -File $(FORWARD_SCRIPT) Test

# ─── Clean ───────────────────────────────────────────────────────────────────

clean:
	cd platform && ./gradlew clean
	find . -type d -name __pycache__ -exec rm -rf {} + 2>/dev/null || true
	find . -type d -name "*.egg-info" -exec rm -rf {} + 2>/dev/null || true
