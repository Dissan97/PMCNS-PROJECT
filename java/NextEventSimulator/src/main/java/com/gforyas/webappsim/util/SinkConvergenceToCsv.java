package com.gforyas.webappsim.util;

import com.gforyas.webappsim.logging.SysLogger;
import com.gforyas.webappsim.simulator.Simulation;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Locale;

import static com.gforyas.webappsim.App.getCfgPath;

/**
 * Sink for convergence results.
 * Adds num_departures and distinguishes between OVERALL and NODE_xxx scope.
 */
public class SinkConvergenceToCsv extends SinkToCsv {

    private final EnumMap<CsvHeaderConv, String> convRecords = new EnumMap<>(CsvHeaderConv.class);

    public SinkConvergenceToCsv(int seed) {
        super(seed);
        Path cfgPath = Path.of(getCfgPath());
        String cfgBaseName = cfgPath.getFileName().toString().replace(".json", "");

        int dot = cfgBaseName.lastIndexOf('.');
        String cfgStem = (dot > 0 ? cfgBaseName.substring(0, dot) : cfgBaseName);
        // Cambiamo il nome file per distinguerlo
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = String.format("conv_%s_run%03d_seed%s_%s.csv",
                cfgStem, Simulation.SIMULATION_COUNTER.get(), seed, ts);

        this.outputPath = OUT_DIR.resolve(fileName);
        try {
            java.nio.file.Files.createDirectories(OUT_DIR);
        } catch (IOException e) {
            String severe = "issue in creating convergence dir " + e.getMessage();
            SysLogger.getInstance().getLogger().severe(severe);
        }
    }

    public SinkConvergenceToCsv(int seed, double prob) {
        super(seed);
        Path cfgPath = Path.of(getCfgPath());
        String cfgBaseName = cfgPath.getFileName().toString().replace(".json", "");

        int dot = cfgBaseName.lastIndexOf('.');
        String cfgStem = (dot > 0 ? cfgBaseName.substring(0, dot) : cfgBaseName);
        // Cambiamo il nome file per distinguerlo
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = String.format("conv_%s_run%03d_seed%s_prob%s_%s.csv",
                cfgStem, Simulation.SIMULATION_COUNTER.get(), seed, prob, ts);

        this.outputPath = OUT_DIR.resolve(fileName);
        try {
            java.nio.file.Files.createDirectories(OUT_DIR);
        } catch (IOException e) {
            String severe = "issue in creating convergence dir " + e.getMessage();
            SysLogger.getInstance().getLogger().severe(severe);
        }
    }

    /** Append a record for convergence with new header set. */
    public void appendConvRecord(CsvHeaderConv header, String value) {
        convRecords.put(header, value);
    }

    /** Flushes one line into the CSV lines buffer. */
    public void lineConvRecord() {
        StringBuilder stringBuilder = new StringBuilder();
        for (var header : CsvHeaderConv.values()) {
            stringBuilder.append(convRecords.get(header)).append(',');
        }
        stringBuilder.replace(stringBuilder.length() - 1, stringBuilder.length(), "\n");
        lines.add(stringBuilder.toString());
        convRecords.clear();
    }

    /** Override sink() to print the proper headers for convergence. */
    @Override
    public void sink() {
        try (var writer = new java.io.BufferedWriter(new java.io.FileWriter(outputPath.toFile()))) {
            StringBuilder stringBuilder = new StringBuilder();
            for (var h : CsvHeaderConv.values()) {
                stringBuilder.append(h.getName()).append(',');
            }
            stringBuilder.replace(stringBuilder.length() - 1, stringBuilder.length(), "\n");
            for (var line : lines) {
                stringBuilder.append(line);
            }
            writer.write(stringBuilder.toString());
            writer.flush();
        } catch (IOException e) {
            String severe = " Io problem " + e.getMessage();
            SysLogger.getInstance().getLogger().severe(severe);
        }
    }

    /** Headers for convergence CSV, includes num_departures. */
    public enum CsvHeaderConv {
        SCOPE,
        METRIC,
        VALUE,
        NUM_DEPARTURES;

        public String getName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}