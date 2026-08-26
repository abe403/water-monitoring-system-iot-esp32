package com.watermonitor.domain.device;

/**
 * Identifies one continuous span of a device's RTC memory. A device's
 * {@code seq} counter is only monotonic within a single {@code bootId} — a
 * power loss (not a deep-sleep wake, which preserves RTC memory) starts a
 * new boot with a new, randomly chosen id and {@code seq} restarting at 0.
 * Contiguity checks (see {@code platform/stream-processor}) key on the pair
 * {@code (bootId, seq)}, never {@code seq} alone.
 */
public record BootId(long value) {
}
