package com.biolab;

import java.util.*;

/**
 * Thread-safe ring-buffer store for world metric samples.
 *
 * <p>Write pattern is single-writer (simulation thread), reads may happen on EDT.
 * Methods are synchronized to guarantee consistent snapshots for the UI.</p>
 */
public class WorldStatsStore {
    private static final int DEFAULT_CAPACITY = 108_000;

    private final int capacity;
    private final long[] timestamps;
    private final long[] ticks;
    private final double[][] metricValues;

    private int size;
    private int writeIndex;

    public WorldStatsStore() {
        this(DEFAULT_CAPACITY);
    }

    public WorldStatsStore(int capacity) {
        if (capacity <= 8) {
            throw new IllegalArgumentException("capacity must be > 8");
        }
        this.capacity = capacity;
        this.timestamps = new long[capacity];
        this.ticks = new long[capacity];
        this.metricValues = new double[WorldMetricId.values().length][capacity];
    }

    public synchronized void append(WorldStatsSample sample) {
        double[] values = new double[WorldMetricId.values().length];
        for (WorldMetricId id : WorldMetricId.values()) {
            values[id.ordinal()] = sample.metricValues().getOrDefault(id, 0.0);
        }
        appendInternal(sample.timestampMillis(), sample.tick(), values);
    }

    public synchronized void append(long timestampMillis, long tick, double[] valuesByOrdinal) {
        if (valuesByOrdinal == null || valuesByOrdinal.length != WorldMetricId.values().length) {
            throw new IllegalArgumentException("valuesByOrdinal must match metric count");
        }
        appendInternal(timestampMillis, tick, valuesByOrdinal);
    }

    private void appendInternal(long timestampMillis, long tick, double[] valuesByOrdinal) {
        timestamps[writeIndex] = timestampMillis;
        ticks[writeIndex] = tick;
        for (int m = 0; m < metricValues.length; m++) {
            metricValues[m][writeIndex] = valuesByOrdinal[m];
        }

        writeIndex = (writeIndex + 1) % capacity;
        if (size < capacity) {
            size++;
        }
    }

    public synchronized long firstTimestampMillis() {
        if (size == 0) {
            return 0;
        }
        return timestamps[toPhysicalIndex(0)];
    }

    public synchronized int size() {
        return size;
    }

    public synchronized List<WorldStatsSample> queryRange(
            Set<WorldMetricId> requestedMetrics,
            long fromMillis,
            long toMillis,
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
        int logicalEnd = size - 1;
        for (int logical = 0; logical <= logicalEnd; logical++) {
            int physical = toPhysicalIndex(logical);
            long ts = timestamps[physical];
            if (ts >= fromMillis && ts <= toMillis) {
                matching.add(physical);
            }
        }
        if (matching.isEmpty()) {
            return List.of();
        }

        WorldMetricId primary = metrics.iterator().next();
        List<Integer> sampled = downsample(matching, maxPoints, primary);

        List<WorldStatsSample> out = new ArrayList<>(sampled.size());
        for (int physicalIndex : sampled) {
            EnumMap<WorldMetricId, Double> values = new EnumMap<>(WorldMetricId.class);
            for (WorldMetricId id : metrics) {
                values.put(id, metricValues[id.ordinal()][physicalIndex]);
            }
            out.add(new WorldStatsSample(timestamps[physicalIndex], ticks[physicalIndex], values));
        }
        return out;
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
            int startLogical = 1 + (int) Math.floor(bucket * bucketSize);
            int endLogical = 1 + (int) Math.floor((bucket + 1) * bucketSize);
            endLogical = Math.max(startLogical + 1, Math.min(source.size() - 1, endLogical));

            int bestPhysical = source.get(startLogical);
            double best = metricValues[primaryIndex][bestPhysical];
            boolean chooseMax = (bucket % 2 == 0);

            for (int i = startLogical + 1; i < endLogical; i++) {
                int physical = source.get(i);
                double candidate = metricValues[primaryIndex][physical];
                if ((chooseMax && candidate > best) || (!chooseMax && candidate < best)) {
                    best = candidate;
                    bestPhysical = physical;
                }
            }

            if (reduced.get(reduced.size() - 1) != bestPhysical) {
                reduced.add(bestPhysical);
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

    private int toPhysicalIndex(int logicalIndex) {
        int oldest = (writeIndex - size + capacity) % capacity;
        return (oldest + logicalIndex) % capacity;
    }
}

