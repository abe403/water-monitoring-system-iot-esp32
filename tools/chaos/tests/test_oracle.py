from conservation.oracle import RecordKey, compare


def key(seq: int) -> RecordKey:
    return RecordKey("tank-01", 7, seq)


def test_exact_conservation_passes():
    report = compare([key(0), key(1), key(2)], [key(0), key(1), key(2)])
    assert report.conserved
    assert report.duplicates == ()


def test_retry_duplicates_are_reported_but_do_not_hide_conservation():
    report = compare([key(0), key(1)], [key(0), key(0), key(1)])
    assert report.conserved
    assert report.duplicates == (key(0),)
    assert report.observed_count == 3
    assert report.unique_observed_count == 2


def test_missing_and_unexpected_records_fail():
    report = compare([key(0), key(1)], [key(0), key(9)])
    assert not report.conserved
    assert report.missing == (key(1),)
    assert report.unexpected == (key(9),)
