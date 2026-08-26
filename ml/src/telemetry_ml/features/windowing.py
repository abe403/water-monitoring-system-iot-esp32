"""Model feature engineering.

Pure functions over pandas DataFrames, not a class hierarchy — a
``FeatureExtractor`` object here would carry no state worth having, and a
class hierarchy would only make it easier for training and inference to
silently drift apart. This module is imported by BOTH the training pipeline
(``telemetry_ml.pipelines``) and the real-time inference consumer
(``telemetry_ml.serving``); that is the entire mechanism that prevents
training/serving skew, so importing this module and only this module is a
hard requirement wherever a feature is needed. See
docs/ARCHITECTURE.md, "the training/serving skew boundary".

Input DataFrames are expected to carry the *domain facts* computed by
``platform/stream-processor`` (calibrated level, quality flags, contiguity) —
this module derives model-only signals from them. It never touches raw
device bytes and never re-derives anything stream-processor already computed.
"""

from __future__ import annotations

import numpy as np
import pandas as pd

REQUIRED_COLUMNS = ("device_id", "observed_at", "level_pct", "temp_c", "rssi_dbm", "pump_on")


def build_feature_frame(readings: pd.DataFrame, roll_windows: tuple[str, ...] = ("1h", "6h")) -> pd.DataFrame:
    """Derive model features from enriched readings for a single device.

    ``readings`` must be sorted by ``observed_at`` ascending and contain
    :data:`REQUIRED_COLUMNS`. Returns a new frame indexed the same way, with
    the original columns plus derived ones — nothing is dropped, so this can
    be chained.
    """
    missing = set(REQUIRED_COLUMNS) - set(readings.columns)
    if missing:
        raise ValueError(f"readings frame is missing required columns: {sorted(missing)}")
    if readings["device_id"].nunique() > 1:
        raise ValueError("build_feature_frame operates on one device at a time; group by device_id first")

    df = readings.copy()
    df["observed_at"] = pd.to_datetime(df["observed_at"], utc=True)
    df = df.sort_values("observed_at").set_index("observed_at")

    df["interarrival_s"] = df.index.to_series().diff().dt.total_seconds()

    # d(level)/dt in percent-per-minute — the leak-rate signal the forecast
    # model's residual is interpreted against. NaN on the first row and
    # across any interarrival gap of zero (duplicate timestamp) by
    # construction; callers must handle NaN, not have it silently become 0.
    dt_minutes = df["interarrival_s"] / 60.0
    df["d_level_per_min"] = df["level_pct"].diff() / dt_minutes.replace(0.0, np.nan)

    for window in roll_windows:
        df[f"level_roll_mean_{window}"] = df["level_pct"].rolling(window).mean()
        df[f"level_roll_std_{window}"] = df["level_pct"].rolling(window).std()

    seconds_of_day = (df.index.hour * 3600 + df.index.minute * 60 + df.index.second).to_numpy()
    fraction_of_day = seconds_of_day / 86400.0
    df["time_of_day_sin"] = np.sin(2 * np.pi * fraction_of_day)
    df["time_of_day_cos"] = np.cos(2 * np.pi * fraction_of_day)
    df["day_of_week"] = df.index.dayofweek

    return df.reset_index()
