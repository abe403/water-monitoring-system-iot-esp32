"""The telemetry_outbox v1 wire format, encoder side.

This MUST stay byte-for-byte identical to two other places: the firmware
struct in firmware/components/telemetry_outbox/telemetry_outbox.h
(``TelemetryRecord``) and the decoder in
platform/ingest-gateway/.../TelemetryOutboxV1Decoder.java. There is
deliberately no shared schema file generating all three yet (that is what
contracts/ is for once Avro/Schema Registry lands, plan M3) — until then,
this module's struct format string is the thing to check first if a decode
error shows up, and :func:`decode` exists specifically so this file can
prove its own encoder and the Java decoder agree, via
tests/test_wire_format.py's cross-check against a Java-produced fixture.
"""

from __future__ import annotations

import struct
from dataclasses import dataclass

# < = little-endian, no padding. Order and types must match TelemetryRecord
# in telemetry_outbox.h exactly: boot_id, seq, epoch_s (u32 each), then
# distance_mm, temp_c_x10, rssi_dbm (i16 each), then checksum_errors_total (u16).
_STRUCT_FORMAT = "<IIIhhhH"
RECORD_SIZE_BYTES = struct.calcsize(_STRUCT_FORMAT)
assert RECORD_SIZE_BYTES == 20, "wire format drifted from telemetry_outbox.h — update both together"

NO_TEMPERATURE_READING = -32768  # matches INT16_MIN sentinel in firmware


@dataclass(frozen=True)
class TelemetryFrame:
    boot_id: int
    seq: int
    epoch_s: int
    distance_mm: int
    temp_c_x10: int  # NO_TEMPERATURE_READING if absent
    rssi_dbm: int
    checksum_errors_total: int

    def encode(self) -> bytes:
        return struct.pack(
            _STRUCT_FORMAT,
            self.boot_id,
            self.seq,
            self.epoch_s,
            self.distance_mm,
            self.temp_c_x10,
            self.rssi_dbm,
            self.checksum_errors_total,
        )

    @staticmethod
    def decode(payload: bytes) -> "TelemetryFrame":
        if len(payload) != RECORD_SIZE_BYTES:
            raise ValueError(f"expected {RECORD_SIZE_BYTES}-byte frame, got {len(payload)}")
        boot_id, seq, epoch_s, distance_mm, temp_c_x10, rssi_dbm, checksum_errors = struct.unpack(
            _STRUCT_FORMAT, payload
        )
        return TelemetryFrame(boot_id, seq, epoch_s, distance_mm, temp_c_x10, rssi_dbm, checksum_errors)
