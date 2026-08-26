"""Evaluation metrics for anomaly detection.

Deliberately does not lead with accuracy — see docs/ARCHITECTURE.md: at the
expected ~1% anomaly prevalence, a detector that always predicts "normal"
scores over 99% accuracy. :func:`evaluate_events` reports PR-AUC and
event-level precision/recall/false-alarm-rate, which are the metrics that
actually distinguish a working detector from a lazy one. Accuracy is
still returned (some stakeholders will ask for it) but never used as the
sole reported number.

Also deliberately does NOT implement point-adjustment (retroactively marking
an entire true anomaly segment as detected the moment any one point in it is
flagged). That protocol is well known to inflate F1 toward 1.0 for
near-random detectors and using it here would undermine the honesty this
whole evaluation harness exists for.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import pandas as pd
from sklearn.metrics import average_precision_score


@dataclass(frozen=True)
class EventMetrics:
    n_events: int
    pr_auc: float
    accuracy: float
    event_precision: float
    event_recall: float
    false_alarms_per_device_day: float


def evaluate_events(
    timestamps: pd.DatetimeIndex,
    scores: np.ndarray,
    threshold: float,
    ground_truth_events: list[tuple[pd.Timestamp, pd.Timestamp]],
    *,
    detection_tolerance: pd.Timedelta = pd.Timedelta(minutes=15),
    device_days_observed: float = 1.0,
) -> EventMetrics:
    """Score a detector's output against physically-labeled fault episodes.

    ``ground_truth_events`` are ``(start, end)`` pairs — e.g. the logged
    start/stop of an induced leak (plan M7's labeling CLI). An event counts
    as detected if any score exceeds ``threshold`` within
    ``[start - detection_tolerance, end + detection_tolerance]``. A flagged
    point outside every event's tolerance window is a false alarm.
    """
    is_anomaly_truth = np.zeros(len(timestamps), dtype=bool)
    for start, end in ground_truth_events:
        is_anomaly_truth |= (timestamps >= start) & (timestamps <= end)

    flagged = scores >= threshold

    pr_auc = average_precision_score(is_anomaly_truth, scores) if is_anomaly_truth.any() else float("nan")
    accuracy = float(np.mean(flagged == is_anomaly_truth))

    detected_events = 0
    for start, end in ground_truth_events:
        window = (timestamps >= start - detection_tolerance) & (timestamps <= end + detection_tolerance)
        if np.any(flagged & window):
            detected_events += 1
    event_recall = detected_events / len(ground_truth_events) if ground_truth_events else float("nan")

    in_any_tolerance_window = np.zeros(len(timestamps), dtype=bool)
    for start, end in ground_truth_events:
        in_any_tolerance_window |= (timestamps >= start - detection_tolerance) & (timestamps <= end + detection_tolerance)
    false_alarm_points = int(np.sum(flagged & ~in_any_tolerance_window))
    true_alarm_points = int(np.sum(flagged & in_any_tolerance_window))
    event_precision = (
        true_alarm_points / (true_alarm_points + false_alarm_points)
        if (true_alarm_points + false_alarm_points) > 0
        else float("nan")
    )

    false_alarms_per_device_day = false_alarm_points / device_days_observed if device_days_observed > 0 else float("nan")

    return EventMetrics(
        n_events=len(ground_truth_events),
        pr_auc=pr_auc,
        accuracy=accuracy,
        event_precision=event_precision,
        event_recall=event_recall,
        false_alarms_per_device_day=false_alarms_per_device_day,
    )
