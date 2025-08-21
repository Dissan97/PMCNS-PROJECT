package com.gforyas.webappsim.util;

import com.gforyas.webappsim.logging.SysLogger;
import com.gforyas.webappsim.simulator.Simulation;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.gforyas.webappsim.App.getCfgPath;

public class SinkToCsv {

    private static final List<String> HEADERS = Arrays.stream(CsvHeader.values())
            .map(s -> s.name().toLowerCase(Locale.ROOT))
            .toList();

    public static final Path OUT_DIR = Path.of(".output_simulation");
    private final EnumMap<CsvHeader, String> records = new EnumMap<>(CsvHeader.class);
    private final List<String> lines = new ArrayList<>();
    private final Path outputPath;

    public SinkToCsv(int seed) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        // Get only the base name of the config file (remove directories)
        Path cfgPath = Path.of(getCfgPath());
        String cfgBaseName = cfgPath.getFileName().toString().replace(".json", "");

        int dot = cfgBaseName.lastIndexOf('.');
        String cfgStem = (dot > 0 ? cfgBaseName.substring(0, dot) : cfgBaseName);

        // Build file name under OUT_DIR only
        String fileName = String.format("results_%s_run%03d_seed%s_%s.csv",
                cfgStem, Simulation.SIMULATION_COUNTER.get(), seed, ts);

        this.outputPath = OUT_DIR.resolve(fileName);

        try {
            Files.createDirectories(OUT_DIR);
        } catch (IOException e) {
            String severe = "issue in creating dir " + e.getMessage();
            SysLogger.getInstance().getLogger().severe(severe);
        }
    }

    public void appendRecord(CsvHeader header, String value) {
        records.put(header, value);
    }

    public void lineRecord() {
        StringBuilder stringBuilder = new StringBuilder();

        for (var header : CsvHeader.values()) {
            stringBuilder.append(records.get(header)).append(',');
        }
        stringBuilder.replace(stringBuilder.length() - 1, stringBuilder.length(), "\n");

        lines.add(stringBuilder.toString());
        records.clear();
    }

    public void sink() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath.toFile()))) {

            StringBuilder stringBuilder = new StringBuilder();
            HEADERS.forEach(s -> stringBuilder.append(s).append(','));
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

    public enum CsvHeader {
        SCOPE,
        ARRIVAL_RATE,
        MEAN_RESPONSE_TIME,
        STD_RESPONSE_TIME,
        MEAN_POPULATION,
        STD_POPULATION,
        THROUGHPUT,
        UTILIZATION,
        STD_RESPONSE_TIME_COV,
        STD_POPULATION_COV;

        public String getName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

}
