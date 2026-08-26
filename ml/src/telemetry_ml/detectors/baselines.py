"""Baseline detectors the LSTM must beat before it ships.

See docs/ARCHITECTURE.md: "baselines must be implemented and beaten *before*
the LSTM exists — otherwise its number means nothing". These are real,
runnable detectors, not stubs — ``RuleBasedDrainRateDetector`` in particular
is the repo's original ``level < 20`` check, generalized into something that
can be evaluated on the same harness as everything else. If it wins, that is
a legitimate finding to report, not a bug to hide.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np


@dataclass
class ThresholdRuleDetector:
    """The original project's entire "leak detection" logic: level below a floor.

    Kept intentionally simple as the floor every other detector must clear.
    """

    detector_id: str = "threshold-rule-v1"
    level_floor_pct: float = 20.0
    level_column_index: int = 0

    @property
    def threshold(self) -> float:
        return 0.5  # score is a binary 0/1 flag; 0.5 splits it

    def score(self, window: np.ndarray) -> float:
        current_level = window[-1, self.level_column_index]
        return 1.0 if current_level < self.level_floor_pct else 0.0


@dataclass
class RuleBasedDrainRateDetector:
    """Flags a sustained, physically-implausible drain rate with the pump off.

    ``window`` columns are assumed ordered as produced by
    ``telemetry_ml.features.windowing.build_feature_frame`` restricted to
    ``["level_pct", "d_level_per_min", "pump_on"]`` for this detector's use.
    """

    detector_id: str = "drain-rate-rule-v1"
    max_plausible_drain_pct_per_min: float = 0.05
    sustained_for_samples: int = 3

    @property
    def threshold(self) -> float:
        return 0.5

    def score(self, window: np.ndarray) -> float:
        level_idx, drain_idx, pump_idx = 0, 1, 2
        tail = window[-self.sustained_for_samples :]
        pump_off = np.all(tail[:, pump_idx] == 0)
        sustained_drain = np.all(-tail[:, drain_idx] > self.max_plausible_drain_pct_per_min)
        return 1.0 if (pump_off and sustained_drain) else 0.0


@dataclass
class RollingZScoreDetector:
    """A rolling z-score of the drain rate against its own recent history.

    The "plain statistics" baseline the plan calls for: no model, no
    training run, just ``(x - rolling_mean) / rolling_std`` computed once
    per window and compared to a threshold picked on the validation split.
    """

    detector_id: str = "rolling-zscore-v1"
    drain_column_index: int = 1
    z_threshold: float = 3.0

    @property
    def threshold(self) -> float:
        return self.z_threshold

    def score(self, window: np.ndarray) -> float:
        series = window[:, self.drain_column_index]
        mean, std = series[:-1].mean(), series[:-1].std()
        if std == 0:
            return 0.0
        return float(abs((series[-1] - mean) / std))
