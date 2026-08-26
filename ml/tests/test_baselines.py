import numpy as np

from telemetry_ml.detectors.baselines import (
    RollingZScoreDetector,
    RuleBasedDrainRateDetector,
    ThresholdRuleDetector,
)
from telemetry_ml.detectors.ensemble import EnsembleDetector


def test_threshold_rule_flags_below_floor():
    detector = ThresholdRuleDetector(level_floor_pct=20.0)
    window = np.array([[50.0], [30.0], [15.0]])  # last row is level=15
    assert detector.score(window) >= detector.threshold


def test_threshold_rule_does_not_flag_above_floor():
    detector = ThresholdRuleDetector(level_floor_pct=20.0)
    window = np.array([[50.0], [40.0], [35.0]])
    assert detector.score(window) < detector.threshold


def test_drain_rate_rule_requires_pump_off_and_sustained_drain():
    detector = RuleBasedDrainRateDetector(max_plausible_drain_pct_per_min=0.05, sustained_for_samples=3)
    # columns: level_pct, d_level_per_min, pump_on — steady drain, pump off
    leaking = np.array([[80, -0.1, 0], [79, -0.1, 0], [78, -0.1, 0]])
    assert detector.score(leaking) >= detector.threshold

    pump_running = np.array([[80, -0.5, 1], [75, -0.5, 1], [70, -0.5, 1]])
    assert detector.score(pump_running) < detector.threshold

    within_bounds = np.array([[80, -0.01, 0], [80, -0.01, 0], [80, -0.01, 0]])
    assert detector.score(within_bounds) < detector.threshold


def test_rolling_zscore_flags_outlier():
    detector = RollingZScoreDetector(drain_column_index=0, z_threshold=3.0)
    normal = np.random.default_rng(0).normal(0, 0.01, size=20)
    window = np.concatenate([normal, [5.0]]).reshape(-1, 1)  # a clear outlier last
    assert detector.score(window) > detector.threshold


def test_ensemble_majority_vote():
    class AlwaysFlag:
        detector_id = "always"
        threshold = 0.5

        def score(self, window):
            return 1.0

    class NeverFlag:
        detector_id = "never"
        threshold = 0.5

        def score(self, window):
            return 0.0

    ensemble = EnsembleDetector(detector_id="ensemble", members=[AlwaysFlag(), AlwaysFlag(), NeverFlag()])
    window = np.zeros((5, 1))
    assert ensemble.score(window) == 2 / 3
