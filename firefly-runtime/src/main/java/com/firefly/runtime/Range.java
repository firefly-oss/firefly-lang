package com.firefly.runtime;

/**
 * Simple integer range with inclusive/exclusive upper bound.
 */
public final class Range {
    private final int start;
    private final int end;
    private final boolean inclusive;

    public Range(int start, int end, boolean inclusive) {
        this.start = start;
        this.end = end;
        this.inclusive = inclusive;
    }

    public int getStart() { return start; }
    public int getEnd() { return end; }
    public boolean isInclusive() { return inclusive; }

    /**
     * Returns true if the given value lies within the range.
     */
    public boolean contains(int value) {
        if (inclusive) {
            return value >= start && value <= end;
        } else {
            return value >= start && value < end;
        }
    }

    @Override
    public String toString() {
        return start + (inclusive ? "..=" : "..") + end;
    }
}
