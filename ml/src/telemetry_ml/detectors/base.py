"""The Strategy interface every anomaly detector implements.

A ``Protocol`` rather than an ABC: detectors are pure functions of a window
to a score plus a threshold, nothing more, and structural typing is enough —
there is no shared base-class behavior worth forcing an inheritance
relationship for. Every implementation must be a pure function of its input:
no Kafka client, no database handle, no wall-clock read. That is what makes
:mod:`telemetry_ml.pipelines.base`'s evaluation harness able to test any
detector — baseline or LSTM — identically, on a numpy array alone.
"""

from __future__ import annotations

from typing import Protocol

import numpy as np


class AnomalyDetector(Protocol):
    detector_id: str

    def score(self, window: np.ndarray) -> float:
        """A higher score means more anomalous. No fixed scale is assumed across detectors."""
        ...

    @property
    def threshold(self) -> float:
        """The score above which :meth:`score`'s output should be treated as an anomaly flag."""
        ...
