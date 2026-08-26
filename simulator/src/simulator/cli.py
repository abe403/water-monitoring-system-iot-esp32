"""wtm-simulate — publish or print simulated device telemetry.

Examples:

    # Print 100 normal readings as NDJSON, no broker needed
    wtm-simulate --device water-tank-01 --count 100 --output print

    # Inject a 3-hour leak (72 readings @ 150s) starting at reading 500
    wtm-simulate --device water-tank-01 --count 1000 --fault leak \
        --fault-start 500 --fault-length 72 --output print

    # Publish to a real broker at 20x the nominal 150s cadence, for a quick
    # load smoke test (the full conservation test in tools/chaos/ goes much
    # further — many devices, injected broker/consumer failures)
    wtm-simulate --device water-tank-01 --count 5000 --speedup 20 \
        --output mqtt --broker localhost --port 1883
"""

from __future__ import annotations

import argparse
import json
import sys
import time
from random import Random

from simulator.generator import DeviceState, FaultMode, generate_stream


def _build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Simulate water-tank device telemetry")
    parser.add_argument("--device", required=True, help="device id, e.g. water-tank-01")
    parser.add_argument("--boot-id", type=int, default=1)
    parser.add_argument("--count", type=int, default=100)
    parser.add_argument("--cadence-s", type=int, default=150, help="seconds between readings (real device: ~150)")
    parser.add_argument("--speedup", type=float, default=1.0, help="publish faster than real-time by this factor")
    parser.add_argument("--fault", choices=[f.name.lower() for f in FaultMode], default="none")
    parser.add_argument("--fault-start", type=int, default=None, help="reading index the fault begins at")
    parser.add_argument("--fault-length", type=int, default=0, help="number of readings the fault lasts")
    parser.add_argument("--seed", type=int, default=0)
    parser.add_argument("--output", choices=["print", "mqtt"], default="print")
    parser.add_argument("--broker", default="localhost")
    parser.add_argument("--port", type=int, default=1883)
    parser.add_argument("--topic-prefix", default="wtm/v1")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _build_arg_parser().parse_args(argv)
    fault = FaultMode[args.fault.upper()]

    state = DeviceState(device_id=args.device, boot_id=args.boot_id)
    epoch_start = int(time.time())
    stream = generate_stream(
        state,
        args.count,
        epoch_start_s=epoch_start,
        cadence_s=args.cadence_s,
        fault=fault,
        fault_start_index=args.fault_start,
        fault_length=args.fault_length,
        rng=Random(args.seed),
    )

    if args.output == "print":
        return _run_print(stream, args.device)
    return _run_mqtt(stream, args)


def _run_print(stream, device_id: str) -> int:
    for frame, should_publish in stream:
        record = {"device_id": device_id, "published": should_publish, **frame.__dict__}
        print(json.dumps(record))
    return 0


def _run_mqtt(stream, args) -> int:
    try:
        import paho.mqtt.client as mqtt
    except ImportError:
        print("paho-mqtt is required for --output mqtt (it is a declared dependency; run `uv sync`)", file=sys.stderr)
        return 1

    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=f"sim-{args.device}")
    client.connect(args.broker, args.port)
    client.loop_start()

    topic = f"{args.topic_prefix}/{args.device}/tel"
    delay_s = args.cadence_s / max(args.speedup, 1e-9)
    published = skipped = 0

    try:
        for frame, should_publish in stream:
            if should_publish:
                client.publish(topic, frame.encode(), qos=1)
                published += 1
            else:
                skipped += 1  # models an on-device outbox retaining data during a comms gap
            time.sleep(delay_s)
    finally:
        client.loop_stop()
        client.disconnect()

    print(f"done: published={published} withheld_by_comms_gap={skipped}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
