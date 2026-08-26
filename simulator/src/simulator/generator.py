"""Synthetic multivariate telemetry generation, with injectable faults.

Two uses, per docs/ARCHITECTURE.md: (1) load — sustaining many simulated
devices at a high message rate to exercise the ingestion pipeline's
durability chain (plan M4's conservation test), and (2) bronze-tier training
data — pretraining material for telemetry_ml, never the reported evaluation
metric (see docs/ARCHITECTURE.md, "risks and honesty traps", item 1).
"""

from __future__ import annotations

import random
from collections.abc import Iterator
from dataclasses import dataclass
from enum import Enum, auto

from simulator.wire import NO_TEMPERATURE_READING, TelemetryFrame


class FaultMode(Enum):
    NONE = auto()
    LEAK = auto()  # steady, sustained drain
    SENSOR_FAULT = auto()  # checksum errors, no valid distance change
    COMMS_GAP = auto()  # seq still advances (on-device outbox), nothing is published
    THERMAL = auto()  # temperature spikes


@dataclass
class DeviceState:
    device_id: str
    boot_id: int
    seq: int = 0
    level_pct: float = 75.0
    temp_c: float = 22.0
    rssi_dbm: int = -55


def _level_to_distance_mm(level_pct: float, full_mm: int = 200, empty_mm: int = 720) -> int:
    """Inverse of the LinearTwoPointCalibration used server-side (platform/domain)."""
    fraction = max(0.0, min(1.0, level_pct / 100.0))
    return round(empty_mm - fraction * (empty_mm - full_mm))


def generate_stream(
    state: DeviceState,
    count: int,
    *,
    epoch_start_s: int,
    cadence_s: int = 150,
    fault: FaultMode = FaultMode.NONE,
    fault_start_index: int | None = None,
    fault_length: int = 0,
    rng: random.Random | None = None,
) -> Iterator[tuple[TelemetryFrame, bool]]:
    """Yield ``(frame, should_publish)`` pairs.

    ``should_publish=False`` during a COMMS_GAP fault models the on-device
    outbox continuing to buffer and increment ``seq`` while nothing actually
    reaches the broker — exactly the scenario
    firmware/components/telemetry_outbox exists to survive. The gateway
    should see a contiguous sequence resume once the gap ends, with no
    record of the skipped seq numbers ever arriving out of order.
    """
    rng = rng or random.Random(0)
    checksum_errors_total = 0

    for i in range(count):
        in_fault_window = fault_start_index is not None and fault_start_index <= i < fault_start_index + fault_length

        if in_fault_window and fault is FaultMode.LEAK:
            state.level_pct = max(0.0, state.level_pct - 0.8)
        elif in_fault_window and fault is FaultMode.THERMAL:
            state.temp_c = 22.0 + 15.0 * ((i - fault_start_index) / max(1, fault_length))
        else:
            state.level_pct = max(0.0, min(100.0, state.level_pct + rng.gauss(0, 0.05)))
            state.temp_c += rng.gauss(0, 0.02)

        state.rssi_dbm = max(-95, min(-40, state.rssi_dbm + rng.randint(-1, 1)))

        distance_mm = _level_to_distance_mm(state.level_pct)
        temp_c_x10 = NO_TEMPERATURE_READING if (in_fault_window and fault is FaultMode.SENSOR_FAULT) else round(
            state.temp_c * 10
        )
        if in_fault_window and fault is FaultMode.SENSOR_FAULT:
            checksum_errors_total += 1

        frame = TelemetryFrame(
            boot_id=state.boot_id,
            seq=state.seq,
            epoch_s=epoch_start_s + i * cadence_s,
            distance_mm=distance_mm,
            temp_c_x10=temp_c_x10,
            rssi_dbm=state.rssi_dbm,
            checksum_errors_total=checksum_errors_total & 0xFFFF,
        )
        state.seq += 1

        should_publish = not (in_fault_window and fault is FaultMode.COMMS_GAP)
        yield frame, should_publish
