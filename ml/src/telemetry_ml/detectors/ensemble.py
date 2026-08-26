"""Composite: an ensemble of detectors that is itself an AnomalyDetector.

The inference consumer (telemetry_ml.serving) does not need to know whether
it holds one detector or several — that is the entire point of making this
class satisfy the same Protocol as its children.
"""

from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np

from telemetry_ml.detectors.base import AnomalyDetector


@dataclass
class EnsembleDetector:
    detector_id: str
    members: list[AnomalyDetector] = field(default_factory=list)

    @property
    def threshold(self) -> float:
        return 0.5  # members are normalized to a 0/1 vote before combining

    def score(self, window: np.ndarray) -> float:
        if not self.members:
            raise ValueError("EnsembleDetector has no members")
        votes = [1.0 if m.score(window) >= m.threshold else 0.0 for m in self.members]
        return float(np.mean(votes))
