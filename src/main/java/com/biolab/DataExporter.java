package com.biolab;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Appends a single-line CSV snapshot of the simulation state to
 * {@code biolab_stats.csv} in the working directory once per second.
 *
 * <h3>CSV Schema</h3>
 * <pre>
 * Timestamp,Population,Temp,Tox,AvgHealth,AvgEnergy,AvgHeatRes,AvgToxRes,AvgSpeed,AvgDiet
 * </pre>
 *
 * <ul>
 *   <li>The header row is written the first time the file is created.</li>
 *   <li>All floating-point values are rounded to 4 decimal places.</li>
 *   <li>If the microbe list is empty the average columns are recorded as {@code 0}.</li>
 *   <li>This class is stateless; the caller is responsible for the 1-second interval.</li>
 * </ul>
 */
public final class DataExporter {

    private static final Logger LOGGER = Logger.getLogger(DataExporter.class.getName());

    /**
     * Path of the output file – resolved once at class-load time.
     */
    private static final Path OUTPUT_PATH =
            Paths.get(System.getProperty("user.dir"), "biolab_stats.csv");

    private static final String CSV_HEADER =
            "Timestamp,Population,Temp,Tox,AvgHealth,AvgEnergy,AvgHeatRes,AvgToxRes,AvgSpeed,AvgDiet";

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Utility class – no instances.
     */
    private DataExporter() {
    }

    /**
     * Calculates population averages and appends one CSV row to the log file.
     * Creates the file with a header row if it does not yet exist.
     *
     * <p>This method performs blocking I/O and must <em>not</em> be called on the EDT.</p>
     *
     * @param microbes snapshot of the current living microbe population
     * @param env      current environment (provides temperature and toxicity)
     */
    public static void logSimulationData(List<Microbe> microbes, Environment env) {
        int population = microbes.size();

        double avgHealth = 0;
        double avgEnergy = 0;
        double avgHeatRes = 0;
        double avgToxRes = 0;
        double avgSpeed = 0;
        double avgDiet = 0;

        if (population > 0) {
            for (Microbe m : microbes) {
                avgHealth += m.getHealth();
                avgEnergy += m.getEnergy();
                avgHeatRes += m.getHeatResistance();
                avgToxRes += m.getToxinResistance();
                avgSpeed += m.getSpeed();
                avgDiet += m.getDiet();
            }
            avgHealth /= population;
            avgEnergy /= population;
            avgHeatRes /= population;
            avgToxRes /= population;
            avgSpeed /= population;
            avgDiet /= population;
        }

        String timestamp = LocalDateTime.now().format(TS_FORMAT);
        double temp = env.getTemperature();
        double tox = env.getToxicity();

        String row = String.format("%s,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f%n",
                timestamp, population, temp, tox,
                avgHealth, avgEnergy, avgHeatRes, avgToxRes, avgSpeed, avgDiet);

        try {
            boolean fileExists = Files.exists(OUTPUT_PATH);

            if (!fileExists) {
                // Write header + first data row atomically (CREATE)
                String content = CSV_HEADER + System.lineSeparator() + row;
                Files.writeString(OUTPUT_PATH, content, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            } else {
                // Append subsequent rows
                Files.writeString(OUTPUT_PATH, row, StandardCharsets.UTF_8,
                        StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "DataExporter: failed to write to " + OUTPUT_PATH, e);
        }
    }
}


