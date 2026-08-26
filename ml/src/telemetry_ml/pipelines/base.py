"""The Template Method that protects the held-out metric from itself.

``TrainingPipeline.run`` is deliberately final (by convention here, since
Python has no ``final`` keyword for methods — see the note on
:meth:`TrainingPipeline.run`). Every invariant that keeps a reported metric
honest lives in this fixed sequence, not in any subclass:

* the split is always :func:`telemetry_ml.pipelines.splitting.split_temporal`
  — never a random split;
* the scaler is fit on ``train`` only;
* the detection threshold is calibrated on ``val`` only;
* ``test`` is read exactly once, inside :func:`evaluate_events`.

A subclass cannot accidentally shuffle the split or fit a scaler on test
data, because it never receives the whole frame — only the piece the
skeleton hands it at each step. Because every concrete pipeline
(``ZScoreBaselinePipeline``, a future ``ForecastResidualLstmPipeline``, etc.)
shares this skeleton, they are all evaluated under identical rules, which is
the only way comparing them to each other means anything.
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Any

import numpy as np
import pandas as pd
from sklearn.preprocessing import StandardScaler

from telemetry_ml.pipelines.evaluation import EventMetrics, evaluate_events
from telemetry_ml.pipelines.splitting import TemporalSplit, split_temporal


@dataclass(frozen=True)
class PipelineConfig:
    device_id: str
    train_end: pd.Timestamp
    val_end: pd.Timestamp
    embargo: pd.Timedelta
    feature_columns: tuple[str, ...]
    ground_truth_events: tuple[tuple[pd.Timestamp, pd.Timestamp], ...] = ()
    target_recall: float = 0.90


@dataclass(frozen=True)
class TrainingResult:
    pipeline_id: str
    metrics: EventMetrics
    threshold: float
    model: Any


class TrainingPipeline(ABC):
    def __init__(self, config: PipelineConfig):
        self.config = config

    def run(self) -> TrainingResult:
        """The fixed skeleton. Do not override — override the steps below instead."""
        raw = self.load()
        features = self.engineer(raw)
        split = split_temporal(
            features, self.config.train_end, self.config.val_end, embargo=self.config.embargo
        )
        self._check_split_is_nonempty(split)

        scaler = StandardScaler().fit(split.train[list(self.config.feature_columns)])
        scaled_train = self._scaled(scaler, split.train)
        scaled_val = self._scaled(scaler, split.val)
        scaled_test = self._scaled(scaler, split.test)

        model = self.build_model()
        model = self.fit(model, scaled_train, scaled_val)

        # threshold and final scoring both use the SAME fitted scaler as
        # training — never refit on val or test, and never scored unscaled
        # while trained scaled. Getting this pairing wrong silently shifts
        # every downstream score and makes the calibrated threshold meaningless.
        threshold = self._calibrate_threshold(model, scaled_val)
        metrics = evaluate_events(
            timestamps=pd.DatetimeIndex(split.test["observed_at"]),
            scores=np.array([self.score_window(model, row) for row in scaled_test[list(self.config.feature_columns)].to_numpy()]),
            threshold=threshold,
            ground_truth_events=list(self.config.ground_truth_events),
        )
        return TrainingResult(pipeline_id=self.pipeline_id, metrics=metrics, threshold=threshold, model=model)

    # ---- steps a subclass implements -------------------------------------------------

    @property
    @abstractmethod
    def pipeline_id(self) -> str: ...

    @abstractmethod
    def load(self) -> pd.DataFrame: ...

    @abstractmethod
    def engineer(self, raw: pd.DataFrame) -> pd.DataFrame: ...

    @abstractmethod
    def build_model(self) -> Any: ...

    @abstractmethod
    def fit(self, model: Any, train: pd.DataFrame, val: pd.DataFrame) -> Any: ...

    @abstractmethod
    def score_window(self, model: Any, feature_row: np.ndarray) -> float: ...

    # ---- shared, non-overridable helpers ----------------------------------------------

    def _scaled(self, scaler: StandardScaler, df: pd.DataFrame) -> pd.DataFrame:
        out = df.copy()
        cols = list(self.config.feature_columns)
        out[cols] = scaler.transform(df[cols])
        return out

    def _calibrate_threshold(self, model: Any, val: pd.DataFrame) -> float:
        scores = np.array([self.score_window(model, row) for row in val[list(self.config.feature_columns)].to_numpy()])
        if len(scores) == 0:
            raise ValueError("validation split is empty; cannot calibrate a threshold")
        # A simple, explicit choice: the (1 - target_recall) percentile of
        # validation scores. Real deployments may want a smarter search, but
        # every candidate must still only ever look at `val`.
        percentile = (1.0 - self.config.target_recall) * 100
        return float(np.percentile(scores, 100 - percentile))

    @staticmethod
    def _check_split_is_nonempty(split: TemporalSplit) -> None:
        for name, part in (("train", split.train), ("val", split.val), ("test", split.test)):
            if len(part) == 0:
                raise ValueError(f"temporal split produced an empty '{name}' segment — check train_end/val_end/embargo")
