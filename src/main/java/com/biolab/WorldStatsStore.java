package com.biolab;

import java.util.*;

/**
 * Thread-safe, unbounded world-stats history storage.
 */
public class WorldStatsStore {
    private static final int INITIAL_CAPACITY = 1_024;

    private long[] timestamps;
    private long[] ticks;
    private double[][] metricValues;
    private int size;

    public WorldStatsStore() {
        this(INITIAL_CAPACITY);
    }

    public WorldStatsStore(int initialCapacity) {
        int cap = Math.max(16, initialCapacity);
        this.timestamps = new long[cap];
        this.ticks = new long[cap];
        this.metricValues = new double[WorldMetricId.values().length][cap];
    }

    public synchronized void append(WorldStatsSample sample) {
        double[] values = new double[WorldMetricId.values().length];
        for (WorldMetricId id : WorldMetricId.values()) {
            values[id.ordinal()] = sample.metricValues().getOrDefault(id, 0.0);
        }
        append(sample.timestampMillis(), sample.tick(), values);
    }

    public synchronized void append(long timestampMillis, long tick, double[] valuesByOrdinal) {
        if (valuesByOrdinal == null || valuesByOrdinal.length != WorldMetricId.values().length) {
            throw new IllegalArgumentException("valuesByOrdinal must match metric count");
        }
        ensureCapacity(size + 1);
        timestamps[size] = timestampMillis;
        ticks[size] = tick;
        for (int m = 0; m < metricValues.length; m++) {
            metricValues[m][size] = valuesByOrdinal[m];
        }
        size++;
    }

    public synchronized void replaceAll(List<WorldStatsSample> samples) {
        clear();
        if (samples == null || samples.isEmpty()) {
            return;
        }
        ensureCapacity(samples.size());
        for (WorldStatsSample sample : samples) {
            append(sample);
        }
    }

    public synchronized List<WorldStatsSample> snapshotAll() {
        LinkedHashSet<WorldMetricId> all = new LinkedHashSet<>();
        Collections.addAll(all, WorldMetricId.values());
        return queryRangeByTick(all, firstTick(), lastTick(), Integer.MAX_VALUE);
    }

    public synchronized void clear() {
        size = 0;
    }

    public synchronized int size() {
        return size;
    }

    public synchronized long firstTick() {
        return size == 0 ? 0L : ticks[0];
    }

    public synchronized long lastTick() {
        return size == 0 ? 0L : ticks[size - 1];
    }

    public synchronized List<WorldStatsSample> queryRangeByTick(
            Set<WorldMetricId> requestedMetrics,
            long fromTick,
            long toTick,
            int maxPoints
    ) {
        if (size == 0) {
            return List.of();
        }
        Set<WorldMetricId> metrics = sanitizeMetrics(requestedMetrics);
        if (metrics.isEmpty()) {
            return List.of();
        }
        if (maxPoints <= 0) {
            maxPoints = 1;
        }

        List<Integer> matching = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            long tick = ticks[index];
            if (tick >= fromTick && tick <= toTick) {
                matching.add(index);
            }
        }
        if (matching.isEmpty()) {
            return List.of();
        }

        WorldMetricId primary = metrics.iterator().next();
        List<Integer> sampled = downsample(matching, maxPoints, primary);

        List<WorldStatsSample> out = new ArrayList<>(sampled.size());
        for (int index : sampled) {
            EnumMap<WorldMetricId, Double> values = new EnumMap<>(WorldMetricId.class);
            for (WorldMetricId id : metrics) {
                values.put(id, metricValues[id.ordinal()][index]);
            }
            out.add(new WorldStatsSample(timestamps[index], ticks[index], values));
        }
        return out;
    }

    private void ensureCapacity(int required) {
        if (required <= timestamps.length) {
            return;
        }
        int newCapacity = timestamps.length;
        while (newCapacity < required) {
            newCapacity = newCapacity * 2;
        }

        long[] newTimestamps = new long[newCapacity];
        long[] newTicks = new long[newCapacity];
        System.arraycopy(timestamps, 0, newTimestamps, 0, size);
        System.arraycopy(ticks, 0, newTicks, 0, size);
        timestamps = newTimestamps;
        ticks = newTicks;

        double[][] newMetricValues = new double[metricValues.length][newCapacity];
        for (int m = 0; m < metricValues.length; m++) {
            System.arraycopy(metricValues[m], 0, newMetricValues[m], 0, size);
        }
        metricValues = newMetricValues;
    }

    private Set<WorldMetricId> sanitizeMetrics(Set<WorldMetricId> requestedMetrics) {
        if (requestedMetrics == null || requestedMetrics.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<WorldMetricId> ordered = new LinkedHashSet<>();
        for (WorldMetricId id : requestedMetrics) {
            if (id != null) {
                ordered.add(id);
            }
        }
        return ordered;
    }

    private List<Integer> downsample(List<Integer> source, int maxPoints, WorldMetricId primaryMetric) {
        if (source.size() <= maxPoints) {
            return source;
        }
        if (maxPoints <= 2) {
            return List.of(source.get(0), source.get(source.size() - 1));
        }

        List<Integer> reduced = new ArrayList<>(maxPoints);
        reduced.add(source.get(0));

        int buckets = maxPoints - 2;
        double bucketSize = (double) (source.size() - 2) / buckets;
        int primaryIndex = primaryMetric.ordinal();

        for (int bucket = 0; bucket < buckets; bucket++) {
            int start = 1 + (int) Math.floor(bucket * bucketSize);
            int end = 1 + (int) Math.floor((bucket + 1) * bucketSize);
            end = Math.max(start + 1, Math.min(source.size() - 1, end));

            int bestIndex = source.get(start);
            double best = metricValues[primaryIndex][bestIndex];
            boolean chooseMax = (bucket % 2 == 0);

            for (int i = start + 1; i < end; i++) {
                int idx = source.get(i);
                double candidate = metricValues[primaryIndex][idx];
                if ((chooseMax && candidate > best) || (!chooseMax && candidate < best)) {
                    best = candidate;
                    bestIndex = idx;
                }
            }

            if (reduced.get(reduced.size() - 1) != bestIndex) {
                reduced.add(bestIndex);
            }
        }

        int last = source.get(source.size() - 1);
        if (reduced.get(reduced.size() - 1) != last) {
            reduced.add(last);
        }
        if (reduced.size() > maxPoints) {
            return reduced.subList(0, maxPoints);
        }
        return reduced;
    }
}

