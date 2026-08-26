import numpy as np
import pandas as pd
import pytest

from telemetry_ml.features.windowing import REQUIRED_COLUMNS, build_feature_frame


def _make_readings(n=50, freq="150s", level_start=80.0, drain_per_step=0.0):
    times = pd.date_range("2026-06-01T00:00:00Z", periods=n, freq=freq)
    levels = level_start - np.arange(n) * drain_per_step
    return pd.DataFrame(
        {
            "device_id": ["water-tank-01"] * n,
            "observed_at": times,
            "level_pct": levels,
            "temp_c": np.full(n, 22.0),
            "rssi_dbm": np.full(n, -60.0),
            "pump_on": np.zeros(n, dtype=int),
        }
    )


def test_missing_required_column_raises():
    df = _make_readings().drop(columns=["temp_c"])
    with pytest.raises(ValueError, match="missing required columns"):
        build_feature_frame(df)


def test_multiple_devices_rejected():
    df = _make_readings()
    df.loc[0, "device_id"] = "some-other-device"
    with pytest.raises(ValueError, match="one device at a time"):
        build_feature_frame(df)


def test_derived_columns_are_present():
    out = build_feature_frame(_make_readings())
    for col in ("interarrival_s", "d_level_per_min", "time_of_day_sin", "time_of_day_cos", "day_of_week"):
        assert col in out.columns
    for col in REQUIRED_COLUMNS:
        assert col in out.columns


def test_drain_rate_reflects_a_steady_leak():
    # 0.1 pct per 150s step == 0.04 pct/min drain
    out = build_feature_frame(_make_readings(n=30, drain_per_step=0.1))
    steady_state = out["d_level_per_min"].iloc[5:]  # skip warm-up rows
    assert steady_state.mean() == pytest.approx(-0.04, abs=1e-6)


def test_first_row_has_nan_derivatives():
    out = build_feature_frame(_make_readings())
    assert np.isnan(out["d_level_per_min"].iloc[0])
