from simulator.wire import RECORD_SIZE_BYTES, TelemetryFrame


def test_record_size_matches_firmware_struct():
    # Must equal sizeof(TelemetryRecord) asserted by static_assert in
    # firmware/components/telemetry_outbox/telemetry_outbox.h.
    assert RECORD_SIZE_BYTES == 20


def test_round_trip_encode_decode():
    original = TelemetryFrame(
        boot_id=123456,
        seq=42,
        epoch_s=1_800_000_000,
        distance_mm=450,
        temp_c_x10=221,
        rssi_dbm=-63,
        checksum_errors_total=3,
    )
    decoded = TelemetryFrame.decode(original.encode())
    assert decoded == original


def test_wrong_length_payload_rejected():
    import pytest

    with pytest.raises(ValueError, match="expected 20-byte frame"):
        TelemetryFrame.decode(b"\x00" * 19)


def test_negative_values_round_trip():
    # distance_mm/rssi_dbm/temp_c_x10 are signed; verify negative RSSI and
    # the "no temperature reading" sentinel survive encode/decode.
    from simulator.wire import NO_TEMPERATURE_READING

    frame = TelemetryFrame(1, 0, 0, distance_mm=200, temp_c_x10=NO_TEMPERATURE_READING, rssi_dbm=-90, checksum_errors_total=0)
    decoded = TelemetryFrame.decode(frame.encode())
    assert decoded.rssi_dbm == -90
    assert decoded.temp_c_x10 == NO_TEMPERATURE_READING
