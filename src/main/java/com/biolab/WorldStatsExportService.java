package com.biolab;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Export helpers for world stats viewers.
 */
public final class WorldStatsExportService {
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    private WorldStatsExportService() {
    }

    public static String buildCsv(List<WorldStatsSample> samples, List<WorldMetricDefinition> metrics) {
        StringBuilder sb = new StringBuilder();
        sb.append("tick");
        for (WorldMetricDefinition def : metrics) {
            sb.append(',').append(def.label()).append(" [").append(def.unit()).append(']');
        }
        sb.append('\n');

        for (WorldStatsSample sample : samples) {
            sb.append(sample.tick());
            for (WorldMetricDefinition def : metrics) {
                double value = sample.metricValues().getOrDefault(def.id(), 0.0);
                sb.append(',').append(String.format(Locale.ROOT, "%.6f", value));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    public static String buildJson(List<WorldStatsSample> samples, List<WorldMetricDefinition> metrics) {
        Set<WorldMetricId> allowed = new LinkedHashSet<>();
        for (WorldMetricDefinition metric : metrics) {
            allowed.add(metric.id());
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"samples\": [\n");
        for (int i = 0; i < samples.size(); i++) {
            WorldStatsSample sample = samples.get(i);
            sb.append("    { \"tick\": ").append(sample.tick()).append(", \"metrics\": {");
            int c = 0;
            for (WorldMetricId id : allowed) {
                if (c++ > 0) sb.append(',');
                sb.append('"').append(id.name()).append('"').append(':')
                        .append(String.format(Locale.ROOT, "%.6f", sample.metricValues().getOrDefault(id, 0.0)));
            }
            sb.append("} }");
            if (i < samples.size() - 1) sb.append(',');
            sb.append('\n');
        }
        sb.append("  ]\n}");
        return sb.toString();
    }

    public static void exportCsv(Path file, List<WorldStatsSample> samples, List<WorldMetricDefinition> metrics) throws IOException {
        Files.writeString(file, buildCsv(samples, metrics), StandardCharsets.UTF_8);
    }

    public static void exportJson(Path file, List<WorldStatsSample> samples, List<WorldMetricDefinition> metrics) throws IOException {
        Files.writeString(file, buildJson(samples, metrics), StandardCharsets.UTF_8);
    }

    public static void exportPng(Path file, Component component) throws IOException {
        int w = Math.max(1, component.getWidth());
        int h = Math.max(1, component.getHeight());
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            component.paint(g);
        } finally {
            g.dispose();
        }
        ImageIO.write(image, "png", file.toFile());
    }

    public static String defaultFileName(String prefix, String extension) {
        return prefix + "-" + FILE_TS.format(LocalDateTime.now()) + "." + extension;
    }
}

