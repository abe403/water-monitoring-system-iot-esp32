from random import Random

from simulator.generator import DeviceState, FaultMode, generate_stream
from simulator.wire import NO_TEMPERATURE_READING


def test_seq_is_contiguous_regardless_of_publish_status():
    state = DeviceState(device_id="d1", boot_id=1)
    frames = list(
        generate_stream(
            state, 50, epoch_start_s=0, fault=FaultMode.COMMS_GAP, fault_start_index=10, fault_length=10, rng=Random(1)
        )
    )
    seqs = [f.seq for f, _ in frames]
    assert seqs == list(range(50))  # the on-device outbox never skips a seq


def test_comms_gap_withholds_publication_only_during_the_window():
    state = DeviceState(device_id="d1", boot_id=1)
    frames = list(
        generate_stream(
            state, 30, epoch_start_s=0, fault=FaultMode.COMMS_GAP, fault_start_index=10, fault_length=5, rng=Random(1)
        )
    )
    published_flags = [should_publish for _, should_publish in frames]
    assert published_flags[:10] == [True] * 10
    assert published_flags[10:15] == [False] * 5
    assert published_flags[15:] == [True] * 15


def test_leak_fault_produces_sustained_decline():
    state = DeviceState(device_id="d1", boot_id=1, level_pct=80.0)
    frames = list(
        generate_stream(
            state, 40, epoch_start_s=0, fault=FaultMode.LEAK, fault_start_index=5, fault_length=20, rng=Random(1)
        )
    )
    level_before = frames[4][0].distance_mm
    level_during_end = frames[24][0].distance_mm
    # distance_mm increases as the tank empties (sensor farther from water)
    assert level_during_end > level_before


def test_sensor_fault_reports_no_temperature_and_increments_checksum_errors():
    state = DeviceState(device_id="d1", boot_id=1)
    frames = list(
        generate_stream(
            state, 20, epoch_start_s=0, fault=FaultMode.SENSOR_FAULT, fault_start_index=5, fault_length=5, rng=Random(1)
        )
    )
    in_fault = [f for i, (f, _) in enumerate(frames) if 5 <= i < 10]
    assert all(f.temp_c_x10 == NO_TEMPERATURE_READING for f in in_fault)
    assert frames[9][0].checksum_errors_total == 5
