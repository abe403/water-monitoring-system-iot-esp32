#!/usr/bin/env bash
set -Eeuo pipefail

# Three-broker conservation test.  This script intentionally runs the same
# RF=3/minISR=2 acceptance check with either Docker or Podman.  It is also the
# canonical entry point from Windows Git Bash; do not replace it with a
# provider-specific compose command.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CLUSTER="${KIND_CLUSTER_NAME:-wtm-conservation}"
NAMESPACE="wtm-conservation"
RUN_DIR="${CONSERVATION_ARTIFACT_DIR:-$ROOT/.artifacts/conservation-$CLUSTER}"
if [[ -z "${CONSERVATION_ARTIFACT_DIR:-}" ]]; then
  RUN_DIR="$ROOT/.artifacts/conservation-$CLUSTER-$(date -u +%Y%m%dT%H%M%SZ)"
fi
ENGINE="${CONTAINER_ENGINE:-podman}"
KIND_PROVIDER="${KIND_PROVIDER:-$ENGINE}"
KIND_VERSION="${KIND_VERSION:-v0.32.0}"
KUBECTL_VERSION="${KUBECTL_VERSION:-v1.36.4}"
FAULT_DELAY_SECONDS="${FAULT_DELAY_SECONDS:-4}"
JOB_TIMEOUT_SECONDS="${JOB_TIMEOUT_SECONDS:-480}"

mkdir -p "$RUN_DIR"

log() {
  printf '[conservation] %s\n' "$*" >&2
}

die() {
  log "ERROR: $*"
  exit 1
}

on_error() {
  local line=$1
  log "failed at run-kind.sh:$line; collecting diagnostics in $RUN_DIR"
  collect_diagnostics || true
}

collect_diagnostics() {
  {
    echo "=== cluster ==="
    if [[ "$KIND_PROVIDER" == podman ]]; then
      podman ps -a --filter "name=${CLUSTER}-control-plane" --format '{{.Names}} {{.Status}}' 2>&1 || true
    else
      kind get clusters 2>&1 || true
    fi
    echo "=== nodes ==="
    kubectl get nodes -o wide 2>&1 || true
    echo "=== workloads ==="
    kubectl -n "$NAMESPACE" get all -o wide 2>&1 || true
    echo "=== statefulset ==="
    kubectl -n "$NAMESPACE" describe statefulset/kafka 2>&1 || true
    echo "=== kafka events ==="
    kubectl -n "$NAMESPACE" get events --sort-by=.lastTimestamp 2>&1 || true
    echo "=== kafka pod descriptions ==="
    for pod in kafka-0 kafka-1 kafka-2; do
      kubectl -n "$NAMESPACE" describe pod "$pod" 2>&1 || true
    done
    echo "=== kafka logs ==="
    for pod in kafka-0 kafka-1 kafka-2; do
      echo "--- $pod ---"
      kubectl -n "$NAMESPACE" logs "$pod" --all-containers --tail=300 2>&1 || true
    done
    echo "=== gateway logs ==="
    kubectl -n "$NAMESPACE" logs deployment/ingest-gateway --all-containers --tail=300 2>&1 || true
    echo "=== conservation job ==="
    kubectl -n "$NAMESPACE" describe job/conservation 2>&1 || true
    kubectl -n "$NAMESPACE" logs job/conservation --all-containers 2>&1 || true
  } >"$RUN_DIR/diagnostics.txt"
}

cleanup() {
  local status=$?
  if [[ "$status" -ne 0 ]]; then
    collect_diagnostics || true
  fi
  if [[ "${KEEP_CLUSTER:-0}" != "1" ]]; then
    log "deleting kind cluster $CLUSTER"
    kind delete cluster --name "$CLUSTER" >"$RUN_DIR/cluster-delete.log" 2>&1 || true
  else
    log "KEEP_CLUSTER=1; retaining kind cluster $CLUSTER"
  fi
  exit "$status"
}

trap 'on_error $LINENO' ERR
trap cleanup EXIT

if [[ "${MSYSTEM:-}" == MINGW* || "${OSTYPE:-}" == msys* || "${OSTYPE:-}" == cygwin* ]]; then
  log "Windows Git Bash detected"
  log "explicit invocation: CONTAINER_ENGINE=podman bash tools/chaos/run-kind.sh"
fi

case "$ENGINE" in
  podman|docker) ;;
  *) die "CONTAINER_ENGINE must be podman (default) or docker, got '$ENGINE'" ;;
esac
case "$KIND_PROVIDER" in
  podman|docker) ;;
  *) die "KIND_PROVIDER must be podman or docker, got '$KIND_PROVIDER'" ;;
esac

command -v "$ENGINE" >/dev/null 2>&1 || die "$ENGINE is not installed or not on PATH"
"$ENGINE" info >/dev/null 2>&1 || die "$ENGINE is not reachable; start the Podman machine or Docker daemon first"

# Install exact, reproducible client versions into the repo-local tool cache
# when they are not already present.  This keeps Git Bash, CI, and WSL runs on
# the same Kubernetes client versions without requiring administrator access.
export PATH="$ROOT/.tools/bin:$PATH"
"$ROOT/tools/chaos/install-kind-tools.sh"
# MSYS2 otherwise rewrites container-internal absolute paths (for example
# /opt/kafka/bin/kafka-topics.sh) into Windows host paths before kubectl or
# Podman receives them.  Set this only after the installer has used host paths
# with Windows curl.
if [[ "${MSYSTEM:-}" == MINGW* || "${OSTYPE:-}" == msys* || "${OSTYPE:-}" == cygwin* ]]; then
  export MSYS_NO_PATHCONV=1
fi
command -v kind >/dev/null 2>&1 || die "kind $KIND_VERSION is unavailable"
command -v kubectl >/dev/null 2>&1 || die "kubectl $KUBECTL_VERSION is unavailable"
kind version | tee "$RUN_DIR/kind-version.txt"
kubectl version --client=true --output=yaml | tee "$RUN_DIR/kubectl-version.txt"
grep -q "kind $KIND_VERSION" "$RUN_DIR/kind-version.txt" || die "expected kind $KIND_VERSION"
grep -q "gitVersion: $KUBECTL_VERSION" "$RUN_DIR/kubectl-version.txt" || die "expected kubectl $KUBECTL_VERSION"

export KIND_EXPERIMENTAL_PROVIDER="$KIND_PROVIDER"
cd "$ROOT"

if [[ "$KIND_PROVIDER" == podman ]]; then
  if podman ps -a --format '{{.Names}}' | grep -Fxq "${CLUSTER}-control-plane"; then
    die "kind cluster $CLUSTER already exists; delete it explicitly before rerunning"
  fi
else
  if kind get clusters 2>/dev/null | grep -Fxq "$CLUSTER"; then
    die "kind cluster $CLUSTER already exists; delete it explicitly before rerunning"
  fi
fi

log "creating kind cluster $CLUSTER with provider $KIND_PROVIDER"
kind create cluster --name "$CLUSTER" --config infra/kind/cluster.yaml >"$RUN_DIR/kind-create.log" 2>&1

log "building gateway and conservation images with $ENGINE"
./platform/gradlew -p platform :ingest-gateway:bootJar >"$RUN_DIR/gradle-gateway.log" 2>&1
"$ENGINE" build -f Dockerfile --build-arg SERVICE=ingest-gateway -t localhost/wtm/ingest-gateway:conservation . >"$RUN_DIR/build-gateway.log" 2>&1
"$ENGINE" build -f tools/chaos/Dockerfile -t localhost/wtm/conservation:latest . >"$RUN_DIR/build-conservation.log" 2>&1
"$ENGINE" pull apache/kafka:3.9.0 >"$RUN_DIR/pull-kafka.log" 2>&1
"$ENGINE" pull eclipse-mosquitto:2.0 >"$RUN_DIR/pull-mosquitto.log" 2>&1

log "loading images into kind"
if [[ "$KIND_PROVIDER" == podman ]]; then
  # Podman 6 changed `.Labels` from a map to a slice in the Go API exposed by
  # `podman ps`.  kind v0.32.0's Podman provider still uses the old template
  # while implementing `kind load docker-image`, so loading through kind is
  # impossible even though cluster creation/deletion work.  Import directly
  # into the node's containerd namespace instead; the cluster still runs with
  # the Podman provider and Kubernetes receives the same local images.
  node="${CLUSTER}-control-plane"
  podman inspect "$node" >"$RUN_DIR/node-inspect.json"
  import_image() {
    local source=$1 target=$2
    log "importing $source as $target into $node"
    podman save "$source" | podman exec -i "$node" ctr -n k8s.io images import - >>"$RUN_DIR/kind-load.log" 2>&1
    podman exec "$node" ctr -n k8s.io images tag "$source" "$target" >>"$RUN_DIR/kind-load.log" 2>&1
    # containerd/CRI may canonicalize a reference while checking the image;
    # retain the Docker Hub alias as an additional local tag without changing
    # the manifest's explicit localhost reference.
    podman exec "$node" ctr -n k8s.io images tag "$target" "docker.io/${target#localhost/}" >>"$RUN_DIR/kind-load.log" 2>&1 || true
  }
  : >"$RUN_DIR/kind-load.log"
  import_image docker.io/apache/kafka:3.9.0 apache/kafka:3.9.0
  import_image docker.io/library/eclipse-mosquitto:2.0 eclipse-mosquitto:2.0
  import_image localhost/wtm/ingest-gateway:conservation localhost/wtm/ingest-gateway:conservation
  import_image localhost/wtm/conservation:latest localhost/wtm/conservation:latest
else
  kind load docker-image --name "$CLUSTER" \
    apache/kafka:3.9.0 eclipse-mosquitto:2.0 \
    localhost/wtm/ingest-gateway:conservation localhost/wtm/conservation:latest >"$RUN_DIR/kind-load.log" 2>&1
fi

log "deploying Kafka, Mosquitto, and gateway"
kubectl apply -f infra/kind/stack.yaml >"$RUN_DIR/kubectl-apply.log"
kubectl -n "$NAMESPACE" rollout status statefulset/kafka --timeout=300s | tee "$RUN_DIR/kafka-rollout.txt"
kubectl -n "$NAMESPACE" rollout status deployment/mosquitto --timeout=180s | tee "$RUN_DIR/mosquitto-rollout.txt"
kubectl -n "$NAMESPACE" wait --for=condition=complete job/kafka-topics --timeout=180s | tee "$RUN_DIR/topics-job.txt"
kubectl -n "$NAMESPACE" rollout status deployment/ingest-gateway --timeout=240s | tee "$RUN_DIR/gateway-rollout.txt"

pod_ready() {
  local pod=$1
  local phase ready
  phase="$(kubectl -n "$NAMESPACE" get pod "$pod" -o jsonpath='{.status.phase}')"
  ready="$(kubectl -n "$NAMESPACE" get pod "$pod" -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}')"
  [[ "$phase" == Running && "$ready" == True ]]
}

assert_three_ready() {
  local ready_count=0 pod phase ready report_file
  report_file="$RUN_DIR/kafka-ready-$(date -u +%Y%m%dT%H%M%SZ).txt"
  : >"$report_file"
  for pod in kafka-0 kafka-1 kafka-2; do
    phase="$(kubectl -n "$NAMESPACE" get pod "$pod" -o jsonpath='{.status.phase}')"
    ready="$(kubectl -n "$NAMESPACE" get pod "$pod" -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}')"
    printf '%s phase=%s ready=%s\n' "$pod" "$phase" "$ready" >>"$report_file"
    if [[ "$phase" == Running && "$ready" == True ]]; then
      ((ready_count += 1))
    fi
  done
  cat "$report_file"
  if [[ "$ready_count" -ne 3 ]]; then
    log "expected 3 Kafka Ready brokers, got $ready_count"
    return 1
  fi
}

describe_topic() {
  local topic=$1 out
  out="$(kubectl -n "$NAMESPACE" exec kafka-0 -- /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server kafka-0.kafka:9092 --describe --topic "$topic")"
  printf '%s\n' "$out" | tee "$RUN_DIR/topic-$topic.txt"
  if ! grep -Eq 'ReplicationFactor:[[:space:]]*3' <<<"$out"; then
    log "$topic is not replication factor 3"
    return 1
  fi
  if ! grep -q 'min.insync.replicas=2' <<<"$out"; then
    log "$topic is not min.insync.replicas=2"
    return 1
  fi
  local partition_line replicas isr isr_count partition_count=0
  while IFS= read -r partition_line; do
    if [[ -z "$partition_line" ]]; then
      continue
    fi
    replicas="$(awk '{for (i=1;i<=NF;i++) if ($i == "Replicas:") print $(i+1)}' <<<"$partition_line")"
    isr="$(awk '{for (i=1;i<=NF;i++) if ($i == "Isr:") print $(i+1)}' <<<"$partition_line")"
    if [[ "$replicas" != *,* ]]; then
      log "$topic partition has fewer than 3 replicas: $partition_line"
      return 1
    fi
    isr_count="$(tr ',' '\n' <<<"$isr" | sed '/^$/d' | wc -l | tr -d ' ')"
    if [[ "$isr_count" -ne 3 ]]; then
      log "$topic partition is not fully in-sync: $partition_line"
      return 1
    fi
    partition_count=$((partition_count + 1))
  done < <(grep -E '[[:space:]]Partition:[[:space:]]' <<<"$out")
  if [[ "$partition_count" -eq 0 ]]; then
    log "$topic describe output contained no partition rows"
    return 1
  fi
}

assert_topics_healthy() {
  local topic
  for topic in telemetry.raw.v1 telemetry.enriched.v1 telemetry.dlq.v1 ingest.gap.v1; do
    describe_topic "$topic" || return 1
  done
}

assert_no_under_replicated() {
  local out unavailable
  out="$(kubectl -n "$NAMESPACE" exec kafka-0 -- /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server kafka-0.kafka:9092 --describe --under-replicated-partitions 2>&1 || true)"
  unavailable="$(kubectl -n "$NAMESPACE" exec kafka-0 -- /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server kafka-0.kafka:9092 --describe --unavailable-partitions 2>&1 || true)"
  printf '%s\n' "$out" >"$RUN_DIR/under-replicated-partitions.txt"
  printf '%s\n' "$unavailable" >"$RUN_DIR/unavailable-partitions.txt"
  if grep -q 'Partition:' "$RUN_DIR/under-replicated-partitions.txt"; then
    log "under-replicated partitions remain"
    return 1
  fi
  if grep -q 'Partition:' "$RUN_DIR/unavailable-partitions.txt"; then
    log "unavailable partitions remain"
    return 1
  fi
}

log "pre-fault evidence: exactly three Ready brokers and RF3/minISR2/full ISR"
assert_three_ready || die "pre-fault Kafka Ready assertion failed"
assert_topics_healthy || die "pre-fault topic replication/ISR assertion failed"

old_uid="$(kubectl -n "$NAMESPACE" get pod kafka-1 -o jsonpath='{.metadata.uid}')"
[[ -n "$old_uid" ]] || die "could not capture old kafka-1 UID"
printf 'old_kafka_1_uid=%s\n' "$old_uid" | tee "$RUN_DIR/fault-evidence.txt"

log "starting 8-device x 500-record conservation job"
kubectl apply -f infra/kind/conservation-job.yaml >"$RUN_DIR/conservation-apply.log"
runner_pod=""
for _ in $(seq 1 120); do
  runner_pod="$(kubectl -n "$NAMESPACE" get pods -l job-name=conservation \
    -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)"
  if [[ -n "$runner_pod" ]]; then break; fi
  sleep 1
done
[[ -n "$runner_pod" ]] || die "conservation runner pod was not created"
kubectl -n "$NAMESPACE" wait --for=jsonpath='{.status.phase}'=Running "pod/$runner_pod" --timeout=180s | tee "$RUN_DIR/runner-running.txt"
printf 'runner_pod=%s\n' "$runner_pod" | tee -a "$RUN_DIR/fault-evidence.txt"

# The runner's configured 200 aggregate publishes/sec makes this delay safely
# inside the ~20 second active publish window.  Refuse to fault a job that has
# already exited; that would not test broker loss during sustained load.
sleep "$FAULT_DELAY_SECONDS"
runner_phase="$(kubectl -n "$NAMESPACE" get pod "$runner_pod" -o jsonpath='{.status.phase}')"
runner_running="$(kubectl -n "$NAMESPACE" get pod "$runner_pod" -o jsonpath='{.status.containerStatuses[0].state.running.startedAt}' 2>/dev/null || true)"
printf 'runner_phase_before_fault=%s\nrunner_started_at=%s\n' "$runner_phase" "$runner_running" | tee -a "$RUN_DIR/fault-evidence.txt"
[[ "$runner_phase" == Running && -n "$runner_running" ]] || die "runner is not active during planned broker fault"

log "deleting kafka-1 during active load"
kubectl -n "$NAMESPACE" delete pod kafka-1 --wait=false | tee -a "$RUN_DIR/fault-evidence.txt"

replacement_uid=""
replacement_not_ready_seen=0
for _ in $(seq 1 240); do
  uid="$(kubectl -n "$NAMESPACE" get pod kafka-1 -o jsonpath='{.metadata.uid}' 2>/dev/null || true)"
  phase="$(kubectl -n "$NAMESPACE" get pod kafka-1 -o jsonpath='{.status.phase}' 2>/dev/null || true)"
  ready="$(kubectl -n "$NAMESPACE" get pod kafka-1 -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || true)"
  printf '%s uid=%s phase=%s ready=%s\n' "$(date -u +%FT%TZ)" "$uid" "$phase" "$ready" >>"$RUN_DIR/kafka-1-replacement-watch.txt"
  if [[ -n "$uid" && "$uid" != "$old_uid" ]]; then
    replacement_uid="$uid"
    if [[ "$phase" != Running || "$ready" != True ]]; then
      replacement_not_ready_seen=1
    fi
    [[ "$ready" == True ]] && break
  fi
  sleep 0.25
done
printf 'replacement_kafka_1_uid=%s\nreplacement_not_ready_seen=%s\n' "$replacement_uid" "$replacement_not_ready_seen" | tee -a "$RUN_DIR/fault-evidence.txt"
[[ -n "$replacement_uid" && "$replacement_uid" != "$old_uid" ]] || die "kafka-1 was not replaced with a new UID"
[[ "$replacement_not_ready_seen" -eq 1 ]] || log "replacement reached Ready before polling observed a transient NotReady state; watch evidence retained"

log "waiting for the conservation job to complete"

capture_conservation_report() {
  local report_file=$1
  local stderr_file=$2
  local logs_status=0

  # kubectl can close a completed log stream with a non-zero status after it
  # has already emitted the complete JSON.  Do not let pipefail discard a
  # valid report; the strict validator below is the authority on acceptance.
  kubectl -n "$NAMESPACE" logs job/conservation --all-containers \
    >"$report_file" 2>"$stderr_file" || logs_status=$?
  printf 'kubectl_logs_exit=%s\n' "$logs_status" >>"$stderr_file"
  return 0
}

if ! kubectl -n "$NAMESPACE" wait --for=condition=complete job/conservation --timeout="${JOB_TIMEOUT_SECONDS}s" | tee "$RUN_DIR/conservation-wait.txt"; then
  capture_conservation_report \
    "$RUN_DIR/conservation-report.json" \
    "$RUN_DIR/conservation-logs.stderr"
  die "conservation job failed or timed out"
fi
capture_conservation_report \
  "$RUN_DIR/conservation-report.json" \
  "$RUN_DIR/conservation-logs.stderr"

report_path="$RUN_DIR/conservation-report.json"
if [[ "${MSYSTEM:-}" == MINGW* || "${OSTYPE:-}" == msys* || "${OSTYPE:-}" == cygwin* ]]; then
  report_path="$(cygpath -w "$report_path")"
fi
if ! python - "$report_path" >"$RUN_DIR/acceptance.txt" 2>&1 <<'PY'
import json
import pathlib
import sys

text = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
start = text.find("{")
if start < 0:
    raise SystemExit("conservation output did not contain JSON")
report, _ = json.JSONDecoder().raw_decode(text[start:])
required = {
    "expected_count": 4000,
    "observed_count": 4000,
    "unique_observed_count": 4000,
    "missing": [],
    "unexpected": [],
    "duplicates": [],
    "publish_errors": [],
    "malformed": 0,
    "malformed_records": 0,
    "pass": True,
}
for key, expected in required.items():
    if report.get(key) != expected:
        raise SystemExit(f"acceptance failure: {key}={report.get(key)!r}, expected {expected!r}")
ack = report.get("application_ack", {})
if ack.get("complete") is not True or ack.get("incomplete_devices") != []:
    raise SystemExit(f"acceptance failure: incomplete application ACKs: {ack!r}")
PY
then
  cat "$RUN_DIR/acceptance.txt"
  die "strict conservation acceptance failed"
fi
cat "$RUN_DIR/acceptance.txt"

log "post-recovery evidence: three Ready brokers, full ISR, zero under-replicated"
kubectl -n "$NAMESPACE" rollout status statefulset/kafka --timeout=300s | tee "$RUN_DIR/kafka-recovery-rollout.txt"

# StatefulSet readiness only proves the replacement process is accepting TCP;
# Kafka needs a further bounded interval to elect leaders and replicate every
# partition back into ISR.  Poll all three acceptance checks and retain every
# attempt rather than treating this normal convergence window as a failure.
recovery_deadline=$((SECONDS + 300))
recovery_attempt=0
while :; do
  recovery_attempt=$((recovery_attempt + 1))
  recovery_dir="$RUN_DIR/recovery-attempt-$recovery_attempt"
  mkdir -p "$recovery_dir"
  previous_run_dir="$RUN_DIR"
  RUN_DIR="$recovery_dir"
  recovery_ok=1
  assert_three_ready || recovery_ok=0
  assert_topics_healthy || recovery_ok=0
  assert_no_under_replicated || recovery_ok=0
  RUN_DIR="$previous_run_dir"
  printf 'attempt=%s ok=%s\n' "$recovery_attempt" "$recovery_ok" >>"$RUN_DIR/recovery-attempts.txt"
  if [[ "$recovery_ok" -eq 1 ]]; then
    cp "$recovery_dir"/kafka-ready-*.txt "$RUN_DIR/post-recovery-kafka-ready.txt" 2>/dev/null || true
    cp "$recovery_dir/under-replicated-partitions.txt" "$RUN_DIR/post-recovery-under-replicated.txt"
    cp "$recovery_dir/unavailable-partitions.txt" "$RUN_DIR/post-recovery-unavailable.txt"
    break
  fi
  if (( SECONDS >= recovery_deadline )); then
    die "Kafka did not regain full ISR within 300 seconds; see $RUN_DIR/recovery-attempts.txt"
  fi
  sleep 5
done

log "RF=3 conservation validation passed; artifacts are in $RUN_DIR"
