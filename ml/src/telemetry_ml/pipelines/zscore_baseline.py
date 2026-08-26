"""The simplest possible concrete TrainingPipeline: a rolling z-score.

Exists for two reasons: it is the baseline the LSTM must beat (see
docs/ARCHITECTURE.md), and — practically — it is what proves the
``TrainingPipeline`` skeleton actually works end to end without requiring
torch or any real hardware data. ``tests/test_pipeline_end_to_end.py`` runs
this pipeline on synthetic data.
"""

from __future__ import annotations

from collections import deque
from dataclasses import dataclass, field

import numpy as np
import pandas as pd

from telemetry_ml.features.windowing import build_feature_frame
from telemetry_ml.pipelines.base import TrainingPipeline


@dataclass
class _RollingZScoreModel:
    window: deque = field(default_factory=lambda: deque(maxlen=20))


class ZScoreBaselinePipeline(TrainingPipeline):
    """Scores each point by how many standard deviations its drain rate is
    from the trailing window's mean. No training in the ML sense — "fit"
    only seeds the rolling window from the tail of the training split.
    """

    def __init__(self, config, raw_frame: pd.DataFrame, drain_column: str = "d_level_per_min", window_size: int = 20):
        super().__init__(config)
        self._raw_frame = raw_frame
        self._drain_column = drain_column
        self._window_size = window_size

    @property
    def pipeline_id(self) -> str:
        return "zscore-baseline-v1"

    def load(self) -> pd.DataFrame:
        return self._raw_frame

    def engineer(self, raw: pd.DataFrame) -> pd.DataFrame:
        features = build_feature_frame(raw)
        # Rolling/derivative columns are NaN for the warm-up rows at the start
        # of the series; StandardScaler cannot fit on NaNs, and there is no
        # principled value to impute here, so those rows are dropped rather
        # than silently zero-filled.
        return features.dropna(subset=list(self.config.feature_columns))

    def build_model(self) -> _RollingZScoreModel:
        return _RollingZScoreModel(window=deque(maxlen=self._window_size))

    def fit(self, model: _RollingZScoreModel, train: pd.DataFrame, val: pd.DataFrame) -> _RollingZScoreModel:
        tail = train[self._drain_column].dropna().to_numpy()[-self._window_size :]
        for value in tail:
            model.window.append(value)
        return model

    def score_window(self, model: _RollingZScoreModel, feature_row: np.ndarray) -> float:
        idx = self.config.feature_columns.index(self._drain_column)
        value = feature_row[idx]

        if len(model.window) < 2 or np.isnan(value):
            score = 0.0
        else:
            arr = np.array(model.window)
            mean, std = arr.mean(), arr.std()
            score = 0.0 if std == 0 else float(abs((value - mean) / std))

        if not np.isnan(value):
            model.window.append(value)
        return score
