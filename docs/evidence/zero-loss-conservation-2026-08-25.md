# RF=3 conservation run — 2026-08-25

This record summarizes the successful local acceptance run produced by
`tools/chaos/run-kind.sh` with Podman 6.0.2, kind 0.32.0, and kubectl 1.36.4.
The complete raw output remains in the local/CI conservation artifact bundle.

## Fault conditions

- Kafka brokers: 3
- Topic replication factor: 3
- `min.insync.replicas`: 2
- Workload: 8 devices × 500 records = 4,000 records
- Fault: delete `kafka-1` while the conservation job is `Running`
- Original `kafka-1` UID: `72ff3748-f2c5-4c16-9057-0c314aaaf9e8`
- Replacement `kafka-1` UID: `44ca4821-0ebe-406d-a7fc-b6b47f977a7f`
- Replacement observed NotReady before becoming Ready: yes

## Conservation result

```json
{
  "expected_count": 4000,
  "observed_count": 4000,
  "unique_observed_count": 4000,
  "missing": [],
  "unexpected": [],
  "duplicates": [],
  "publish_errors": [],
  "malformed": 0,
  "malformed_records": 0,
  "application_ack": {
    "complete": true,
    "incomplete_devices": []
  },
  "pass": true
}
```

## Recovery result

- `kafka-0`, `kafka-1`, and `kafka-2`: `Running`, Ready
- All tested topic partitions: full ISR
- Under-replicated partitions: none
- Unavailable partitions: none
- kind cluster cleanup: successful

This demonstrates conservation for the recorded workload and single-broker
replacement scenario. It is not a universal proof for every load shape,
outage duration, simultaneous failure, or infrastructure configuration.
