# RF=3 conservation test

`run-kind.sh` creates a fresh three-broker Kafka KRaft cluster, proves every
test topic is `replication.factor=3` with `min.insync.replicas=2` and all
partitions fully in-sync, then publishes exactly 8 × 500 records. It deletes
the `kafka-1` pod while the job is running and requires the replacement broker
to recover before accepting the final report.

The acceptance report must be exactly:

- expected, observed, and unique: 4000
- missing, unexpected, duplicates, publish errors, malformed records: empty/0
- application ACKs complete for all eight devices
- `pass: true`

After the report, the script proves three Ready brokers, full ISR, and zero
under-replicated or unavailable partitions. A failure leaves diagnostics under
`.artifacts/` before deleting the cluster. Set `KEEP_CLUSTER=1` to retain it.

## Windows Git Bash + Podman

Start the Podman machine, open Git Bash at the repository root, and run:

```bash
CONTAINER_ENGINE=podman bash tools/chaos/run-kind.sh
```

The script installs exact repo-local clients (`kind v0.32.0` and
`kubectl v1.36.4`) under `.tools/bin`, so no administrator privileges are
needed. To use Docker instead:

```bash
CONTAINER_ENGINE=docker KIND_PROVIDER=docker bash tools/chaos/run-kind.sh
```

CI uses the Docker form. The test never lowers RF or `min.insync.replicas`.
