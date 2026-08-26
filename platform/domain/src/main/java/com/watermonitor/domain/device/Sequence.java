package com.watermonitor.domain.device;

/**
 * A device-assigned, monotonically increasing counter within one
 * {@link BootId}. Never negative.
 */
public record Sequence(long value) implements Comparable<Sequence> {

    public Sequence {
        if (value < 0) {
            throw new IllegalArgumentException("sequence cannot be negative: " + value);
        }
    }

    public Sequence next() {
        return new Sequence(value + 1);
    }

    public boolean immediatelyFollows(Sequence previous) {
        return this.value == previous.value + 1;
    }

    @Override
    public int compareTo(Sequence other) {
        return Long.compare(this.value, other.value);
    }
}
