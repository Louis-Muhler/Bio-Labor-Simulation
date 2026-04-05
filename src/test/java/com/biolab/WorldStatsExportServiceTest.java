package com.biolab;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldStatsExportServiceTest {

    @Test
    void csvExportShouldContainSelectedMetricsOnly() throws Exception {
        EnumMap<WorldMetricId, Double> values = new EnumMap<>(WorldMetricId.class);
        values.put(WorldMetricId.POPULATION_ALIVE, 12.0);
        values.put(WorldMetricId.TEMPERATURE, 44.0);
        List<WorldStatsSample> samples = List.of(new WorldStatsSample(1_000L, 30L, values));
        List<WorldMetricDefinition> defs = List.of(
                WorldMetricRegistry.definition(WorldMetricId.POPULATION_ALIVE),
                WorldMetricRegistry.definition(WorldMetricId.TEMPERATURE)
        );

        Path out = Files.createTempFile("world-stats-", ".csv");
        WorldStatsExportService.exportCsv(out, samples, defs);
        String text = Files.readString(out);

        assertTrue(text.contains("tick"));
        assertTrue(text.contains("Population Alive"));
        assertTrue(text.contains("Temperature"));
        assertTrue(text.contains("30"));
    }

    @Test
    void pngExportShouldCreateNonEmptyFile() throws Exception {
        JPanel panel = new JPanel();
        panel.setSize(320, 180);

        Path out = Files.createTempFile("world-stats-", ".png");
        WorldStatsExportService.exportPng(out, panel);

        assertTrue(Files.exists(out));
        assertTrue(Files.size(out) > 0);
    }
}

