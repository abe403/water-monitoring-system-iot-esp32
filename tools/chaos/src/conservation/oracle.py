from __future__ import annotations

from collections import Counter
from dataclasses import asdict, dataclass
from typing import Iterable


@dataclass(frozen=True, order=True)
class RecordKey:
    device_id: str
    boot_id: int
    seq: int


@dataclass(frozen=True)
class ConservationReport:
    expected_count: int
    observed_count: int
    unique_observed_count: int
    missing: tuple[RecordKey, ...]
    unexpected: tuple[RecordKey, ...]
    duplicates: tuple[RecordKey, ...]

    @property
    def conserved(self) -> bool:
        """Conservation permits retry duplicates but never missing/unexpected keys."""
        return not self.missing and not self.unexpected

    def to_dict(self) -> dict:
        return {
            "conserved": self.conserved,
            "expected_count": self.expected_count,
            "observed_count": self.observed_count,
            "unique_observed_count": self.unique_observed_count,
            "missing": [asdict(key) for key in self.missing],
            "unexpected": [asdict(key) for key in self.unexpected],
            "duplicates": [asdict(key) for key in self.duplicates],
        }


def compare(expected: Iterable[RecordKey], observed: Iterable[RecordKey]) -> ConservationReport:
    expected_keys = set(expected)
    observed_list = list(observed)
    counts = Counter(observed_list)
    observed_keys = set(counts)

    return ConservationReport(
        expected_count=len(expected_keys),
        observed_count=len(observed_list),
        unique_observed_count=len(observed_keys),
        missing=tuple(sorted(expected_keys - observed_keys)),
        unexpected=tuple(sorted(observed_keys - expected_keys)),
        duplicates=tuple(sorted(key for key, count in counts.items() if count > 1)),
    )
