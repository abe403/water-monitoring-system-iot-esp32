"""Temporal train/validation/test splitting.

This is the single most important piece of code in the whole ml/ package: a
random split across a sliding window leaks future samples into training and
is the standard way an anomaly-detection accuracy number becomes fiction. See
docs/ARCHITECTURE.md, "risks and honesty traps", item 3.

``TrainingPipeline.run`` (see telemetry_ml.pipelines.base) calls
:func:`split_temporal` exactly once, as a fixed, non-overridable step — no
subclass ever gets the chance to shuffle a split or reuse a scaler fit across
boundaries.
"""

from __future__ import annotations

from dataclasses import dataclass

import pandas as pd


@dataclass(frozen=True)
class TemporalSplit:
    train: pd.DataFrame
    val: pd.DataFrame
    test: pd.DataFrame


def split_temporal(
    frame: pd.DataFrame,
    train_end: pd.Timestamp,
    val_end: pd.Timestamp,
    *,
    time_column: str = "observed_at",
    embargo: pd.Timedelta = pd.Timedelta(hours=2),
) -> TemporalSplit:
    """Split strictly by time, with an embargo gap between segments.

    The embargo drops rows within ``embargo`` of each boundary. Without it,
    a rolling-window feature computed near a split boundary (e.g. a 1h
    rolling mean) mixes samples from both sides of the "wall" between train
    and test, which leaks information across the split even though no row is
    literally duplicated. The embargo must be at least as large as the
    largest feature window in use (see ``features.windowing``'s
    ``roll_windows``) — this function does not know your window sizes and
    cannot enforce that for you; the caller must pass an embargo that covers
    them.
    """
    if train_end >= val_end:
        raise ValueError(f"train_end ({train_end}) must be before val_end ({val_end})")

    t = pd.to_datetime(frame[time_column], utc=True)

    train = frame[t < (train_end - embargo)]
    val = frame[(t >= train_end) & (t < (val_end - embargo))]
    test = frame[t >= val_end]

    return TemporalSplit(train=train, val=val, test=test)
