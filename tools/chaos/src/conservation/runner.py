from __future__ import annotations

import argparse
import json
import struct
import threading
import time
import uuid
from random import Random

import paho.mqtt.client as mqtt
from kafka import KafkaConsumer

from conservation.oracle import RecordKey, compare

# Keep this runner independent of an installed simulator package so its image
# contains the exact protocol tripwire used by the chaos test.
_FRAME = struct.Struct("<IIIHhhH")


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Verify MQTT-to-Kafka record conservation")
    parser.add_argument("--mqtt-host", default="mosquitto")
    parser.add_argument("--mqtt-port", type=int, default=1883)
    parser.add_argument("--kafka-bootstrap", default="kafka-0.kafka:9092,kafka-1.kafka:9092,kafka-2.kafka:9092")
    parser.add_argument("--topic", default="telemetry.raw.v1")
    parser.add_argument(
        "--start-at",
        choices=("earliest", "latest"),
        default="earliest",
        help="use latest when verifying an already-populated development cluster",
    )
    parser.add_argument("--devices", type=int, default=8)
    parser.add_argument("--records-per-device", type=int, default=500)
    parser.add_argument("--rate", type=float, default=200.0, help="aggregate MQTT publishes per second")
    parser.add_argument("--settle-timeout", type=float, default=90.0)
    parser.add_argument("--ack-subscribe-timeout", type=float, default=15.0)
    return parser


def _frame(boot_id: int, seq: int, epoch_s: int, seed: int) -> bytes:
    rng = Random(seed)
    return _FRAME.pack(boot_id, seq, epoch_s, 300 + rng.randrange(0, 200), 220, -60, 0)


class AckTracker:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self.highest: dict[str, int] = {}

    def callback(self, _client, _userdata, message) -> None:
        parts = message.topic.split("/")
        if len(parts) != 4 or len(message.payload) != 4:
            return
        seq = struct.unpack("<I", message.payload)[0]
        with self._lock:
            self.highest[parts[2]] = max(seq, self.highest.get(parts[2], -1))


def _publish_device(args, device_index: int, expected: list[RecordKey], errors: list[str]) -> None:
    device_id = f"chaos-{device_index:03d}"
    boot_id = 10_000 + device_index
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=f"conservation-{device_id}")
    try:
        client.connect(args.mqtt_host, args.mqtt_port)
        client.loop_start()
        delay = args.devices / args.rate if args.rate > 0 else 0
        epoch_s = int(time.time())
        for seq in range(args.records_per_device):
            expected.append(RecordKey(device_id, boot_id, seq))
            info = client.publish(
                f"wtm/v1/{device_id}/tel",
                _frame(boot_id, seq, epoch_s + seq, device_index * 1_000_000 + seq),
                qos=1,
            )
            info.wait_for_publish(timeout=30)
            if not info.is_published():
                raise TimeoutError(f"MQTT PUBACK timeout at {device_id}/{seq}")
            if delay:
                time.sleep(delay)
    except Exception as exc:  # surfaced in the final machine-readable report
        errors.append(f"{device_id}: {exc!r}")
    finally:
        client.loop_stop()
        client.disconnect()


def run(args) -> tuple[int, dict]:
    expected: list[RecordKey] = []
    observed: list[RecordKey] = []
    publish_errors: list[str] = []
    malformed = 0

    consumer = KafkaConsumer(
        args.topic,
        bootstrap_servers=args.kafka_bootstrap.split(","),
        group_id=f"conservation-{uuid.uuid4()}",
        auto_offset_reset=args.start_at,
        enable_auto_commit=False,
        consumer_timeout_ms=1000,
    )

    acks = AckTracker()
    ack_client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=f"conservation-acks-{uuid.uuid4()}")
    ack_subscription_ready = threading.Event()

    def on_subscribe(_client, _userdata, _mid, reason_codes, _properties) -> None:
        if reason_codes and all(not reason_code.is_failure for reason_code in reason_codes):
            ack_subscription_ready.set()

    ack_client.on_message = acks.callback
    ack_client.on_subscribe = on_subscribe
    ack_client.connect(args.mqtt_host, args.mqtt_port)
    ack_client.loop_start()
    result, _mid = ack_client.subscribe("wtm/v1/+/ack", qos=1)
    if result != mqtt.MQTT_ERR_SUCCESS or not ack_subscription_ready.wait(args.ack_subscribe_timeout):
        ack_client.loop_stop()
        ack_client.disconnect()
        consumer.close()
        raise TimeoutError("MQTT application-ack subscription was not confirmed")

    # With auto.offset.reset=latest, force assignment and resolve each
    # partition's current end position before any publisher starts. Without
    # this warm-up, a fast workload can arrive before the first poll and be
    # mistaken for pre-existing traffic.
    if args.start_at == "latest":
        consumer.poll(timeout_ms=1000)

    threads = [
        threading.Thread(target=_publish_device, args=(args, index, expected, publish_errors), daemon=True)
        for index in range(args.devices)
    ]
    for thread in threads:
        thread.start()

    deadline = time.monotonic() + args.settle_timeout
    try:
        while time.monotonic() < deadline:
            records = consumer.poll(timeout_ms=500, max_records=1000)
            for batch in records.values():
                for record in batch:
                    try:
                        value = json.loads(record.value.decode("utf-8"))
                        observed.append(RecordKey(value["deviceId"], int(value["bootId"]), int(value["seq"])))
                    except (UnicodeDecodeError, json.JSONDecodeError, KeyError, TypeError, ValueError):
                        malformed += 1

            publishers_done = all(not thread.is_alive() for thread in threads)
            # The Kafka record can become visible to this consumer just before
            # the gateway's application ACK callback reaches the tracker. Do
            # not stop at the first complete Kafka read: the test's contract is
            # MQTT -> gateway -> durably replicated Kafka -> application ACK.
            # Waiting for both signals prevents a fast consumer from reporting
            # a false ACK gap during the broker fault window.
            ack_complete = all(
                acks.highest.get(f"chaos-{index:03d}", -1) >= args.records_per_device - 1
                for index in range(args.devices)
            )
            if (
                publishers_done
                and len(set(observed)) >= args.devices * args.records_per_device
                and ack_complete
            ):
                break
    finally:
        for thread in threads:
            thread.join(timeout=5)
        ack_client.loop_stop()
        ack_client.disconnect()
        consumer.close()

    report = compare(expected, observed)
    target_seq = args.records_per_device - 1
    incomplete_acks = sorted(
        f"chaos-{index:03d}" for index in range(args.devices)
        if acks.highest.get(f"chaos-{index:03d}", -1) < target_seq
    )
    payload = report.to_dict() | {
        "publish_errors": publish_errors,
        "malformed": malformed,
        "malformed_records": malformed,
        "application_ack": {
            "complete": not incomplete_acks,
            "incomplete_devices": incomplete_acks,
        },
        "application_ack_incomplete_devices": incomplete_acks,
        "pass": report.conserved and not publish_errors and not malformed and not incomplete_acks,
    }
    return (0 if payload["pass"] else 1), payload


def main(argv: list[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        code, report = run(args)
    except Exception as exc:
        incomplete_acks = [f"chaos-{index:03d}" for index in range(args.devices)]
        report = {
            "conserved": False,
            "expected_count": args.devices * args.records_per_device,
            "observed_count": 0,
            "unique_observed_count": 0,
            "missing": [],
            "unexpected": [],
            "duplicates": [],
            "publish_errors": [],
            "malformed": 0,
            "malformed_records": 0,
            "application_ack": {
                "complete": False,
                "incomplete_devices": incomplete_acks,
            },
            "application_ack_incomplete_devices": incomplete_acks,
            "fatal_error": repr(exc),
            "pass": False,
        }
        code = 2
    print(json.dumps(report, indent=2, sort_keys=True))
    return code


if __name__ == "__main__":
    raise SystemExit(main())
