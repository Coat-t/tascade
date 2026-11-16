package com.coatt.tascade.replay.play;

import java.util.Arrays;

final class TimedList<T> {
    private int[] times = new int[16];
    private Object[] values = new Object[16];
    private int size;

    void add(int time, T value) {
        if (size == times.length) {
            times = Arrays.copyOf(times, size * 2);
            values = Arrays.copyOf(values, size * 2);
        }
        times[size] = time;
        values[size] = value;
        size++;
    }

    int size() {
        return size;
    }

    int time(int index) {
        return times[index];
    }

    @SuppressWarnings("unchecked")
    T get(int index) {
        return (T) values[index];
    }

    int indexAt(int time) {
        return lastAtOrBefore(times, size, time);
    }

    T at(int time) {
        int index = indexAt(time);
        return index >= 0 ? get(index) : null;
    }

    T last() {
        return size > 0 ? get(size - 1) : null;
    }

    static int lastAtOrBefore(int[] sorted, int size, int key) {
        int low = 0;
        int high = size - 1;
        int found = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (sorted[mid] <= key) {
                found = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return found;
    }
}
