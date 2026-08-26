import pandas as pd
import pytest

from telemetry_ml.pipelines.splitting import split_temporal


def _frame(n=100, freq="1h"):
    times = pd.date_range("2026-01-01T00:00:00Z", periods=n, freq=freq)
    return pd.DataFrame({"observed_at": times, "value": range(n)})


def test_rejects_train_end_after_val_end():
    df = _frame()
    with pytest.raises(ValueError, match="must be before"):
        split_temporal(df, train_end=df["observed_at"].iloc[50], val_end=df["observed_at"].iloc[10])


def test_splits_are_disjoint_and_ordered():
    df = _frame()
    train_end = df["observed_at"].iloc[40]
    val_end = df["observed_at"].iloc[70]
    result = split_temporal(df, train_end=train_end, val_end=val_end, embargo=pd.Timedelta(0))

    assert result.train["observed_at"].max() < train_end
    assert result.val["observed_at"].min() >= train_end
    assert result.val["observed_at"].max() < val_end
    assert result.test["observed_at"].min() >= val_end
    assert len(result.train) + len(result.val) + len(result.test) == len(df)


def test_embargo_removes_rows_near_each_boundary():
    df = _frame(n=100, freq="1h")
    train_end = df["observed_at"].iloc[40]
    val_end = df["observed_at"].iloc[70]
    embargo = pd.Timedelta(hours=3)

    with_embargo = split_temporal(df, train_end, val_end, embargo=embargo)
    without_embargo = split_temporal(df, train_end, val_end, embargo=pd.Timedelta(0))

    assert len(with_embargo.train) < len(without_embargo.train)
    assert len(with_embargo.val) < len(without_embargo.val)
    # every train row respects the embargo gap before train_end
    assert with_embargo.train["observed_at"].max() <= train_end - embargo
