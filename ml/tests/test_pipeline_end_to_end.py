"""Proves the TrainingPipeline skeleton works end to end on synthetic data.

Not a claim about real-world accuracy — see docs/ARCHITECTURE.md, "risks and
honesty traps", item 1: a number measured on synthetic data must never be
reported as if it were field performance. This test only asserts the
plumbing (split -> scale -> fit -> threshold -> evaluate -> result) runs
without error and that an obvious, synthetic leak is not invisible to the
simplest baseline.
"""

import numpy as np
import pandas as pd

from telemetry_ml.pipelines.base import PipelineConfig
from telemetry_ml.pipelines.zscore_baseline import ZScoreBaselinePipeline

FEATURE_COLUMNS = (
    "level_pct",
    "d_level_per_min",
    "level_roll_mean_1h",
    "level_roll_std_1h",
    "level_roll_mean_6h",
    "level_roll_std_6h",
    "time_of_day_sin",
    "time_of_day_cos",
)


def _synthetic_readings_with_one_leak():
    rng = np.random.default_rng(42)
    n = 12 * 24 * 10  # 10 days at 10-minute cadence
    times = pd.date_range("2026-06-01T00:00:00Z", periods=n, freq="10min")

    level = 70.0 + np.cumsum(rng.normal(0, 0.02, size=n))
    level = np.clip(level, 40.0, 95.0)

    # Inject an obvious leak on day 7: steady drain for 3 hours (18 samples).
    leak_start_idx = 12 * 24 * 7
    leak_len = 18
    drain = np.linspace(0, -8.0, leak_len)
    level[leak_start_idx : leak_start_idx + leak_len] += drain

    df = pd.DataFrame(
        {
            "device_id": ["water-tank-01"] * n,
            "observed_at": times,
            "level_pct": level,
            "temp_c": 20.0 + rng.normal(0, 0.1, size=n),
            "rssi_dbm": -60.0 + rng.normal(0, 1.0, size=n),
            "pump_on": np.zeros(n, dtype=int),
        }
    )
    leak_start = times[leak_start_idx]
    leak_end = times[leak_start_idx + leak_len - 1]
    return df, (leak_start, leak_end)


def test_zscore_baseline_pipeline_runs_and_flags_the_injected_leak():
    readings, (leak_start, leak_end) = _synthetic_readings_with_one_leak()

    config = PipelineConfig(
        device_id="water-tank-01",
        train_end=pd.Timestamp("2026-06-06T00:00:00Z"),
        val_end=pd.Timestamp("2026-06-06T12:00:00Z"),
        embargo=pd.Timedelta(hours=1),
        feature_columns=FEATURE_COLUMNS,
        ground_truth_events=((leak_start, leak_end),),
        target_recall=0.90,
    )
    pipeline = ZScoreBaselinePipeline(config, raw_frame=readings)

    result = pipeline.run()

    assert result.pipeline_id == "zscore-baseline-v1"
    assert result.metrics.n_events == 1
    # The single most important assertion: an obvious synthetic leak is not
    # invisible to the simplest possible baseline. This is a sanity check on
    # the pipeline plumbing, not an accuracy claim.
    assert result.metrics.event_recall == 1.0
