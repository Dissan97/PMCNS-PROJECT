package com.gforyas.webappsim.util;

import com.gforyas.webappsim.App;
import com.gforyas.webappsim.estimators.StatsType;
import com.gforyas.webappsim.logging.SysLogger;
import com.gforyas.webappsim.simulator.Simulation;
import com.gforyas.webappsim.simulator.SimulationConfig;

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
    protected List<String> lines = new ArrayList<>();
    protected Path outputPath;

    public SinkToCsv(long seed) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        // Prende solo il nome base del file di config (senza directory)
        Path cfgPath = Path.of(getCfgPath());
        String cfgBaseName = cfgPath.getFileName().toString().replace(".json", "");

        int dot = cfgBaseName.lastIndexOf('.');
        String cfgStem = (dot > 0 ? cfgBaseName.substring(0, dot) : cfgBaseName);

        // Costruisce il nome del file sotto OUT_DIR
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

    public SinkToCsv(int seed, double prob) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        // Prende solo il nome base del file di config (senza directory)
        Path cfgPath = Path.of(getCfgPath());
        String cfgBaseName = cfgPath.getFileName().toString().replace(".json", "");

        int dot = cfgBaseName.lastIndexOf('.');
        String cfgStem = (dot > 0 ? cfgBaseName.substring(0, dot) : cfgBaseName);

        // Costruisce il nome del file sotto OUT_DIR
        String fileName = String.format("results_%s_run%03d_seed%s_prob%s_%s.csv",
                cfgStem, Simulation.SIMULATION_COUNTER.get(), seed, prob, ts);

        this.outputPath = OUT_DIR.resolve(fileName);

        try {
            Files.createDirectories(OUT_DIR);
        } catch (IOException e) {
            String severe = "issue in creating dir " + e.getMessage();
            SysLogger.getInstance().getLogger().severe(severe);
        }
    }

    public SinkToCsv(int seed, double service, int nothing) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        // Prende solo il nome base del file di config (senza directory)
        Path cfgPath = Path.of(getCfgPath());
        String cfgBaseName = cfgPath.getFileName().toString().replace(".json", "");

        int dot = cfgBaseName.lastIndexOf('.');
        String cfgStem = (dot > 0 ? cfgBaseName.substring(0, dot) : cfgBaseName);

        // Costruisce il nome del file sotto OUT_DIR
        String fileName = String.format("results_%s_run%03d_seed%s_service%s_%s.csv",
                cfgStem, Simulation.SIMULATION_COUNTER.get(), seed, service, ts);

        this.outputPath = OUT_DIR.resolve(fileName);

        try {
            Files.createDirectories(OUT_DIR);
        } catch (IOException e) {
            String severe = "issue in creating dir " + e.getMessage();
            SysLogger.getInstance().getLogger().severe(severe);
        }
    }

    public SinkToCsv() {
    }

    public void appendRecord(CsvHeader header, String value) {
        records.put(header, value);
    }

    public void lineRecord() {
        StringBuilder stringBuilder = new StringBuilder();

        // Se un campo non è stato scritto, usa "-" per evitare "null"
        for (var header : CsvHeader.values()) {
            String v = records.getOrDefault(header, "-");
            stringBuilder.append(v).append(',');
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
    public static SinkToCsv build (SimulationConfig config) {
        if (Objects.requireNonNull(config.getStatsType()) == StatsType.BATCH) {
            return new SinkBatchToCsv(config.getFilename().replace(".json", ".csv"));
        }
        return new SinkToCsv(config.getRngs().getSeed());
    }
    public enum CsvHeader {
        // Campi esistenti
        SCOPE,
        ARRIVAL_RATE,
        MEAN_RESPONSE_TIME,
        STD_RESPONSE_TIME,
        MEAN_POPULATION,
        STD_POPULATION,
        THROUGHPUT,
        UTILIZATION,
        STD_RESPONSE_TIME_COV,
        STD_POPULATION_COV,
        // --- NEW: campi per routing probabilistico / modalità routing ---
        ROUTING_MODE,        // "deterministic" | "probabilistic"
        PATH_AB,             // conteggio job che escono subito dopo B
        PATH_ABAPA,          // conteggio job che compiono ABAPA
        PATH_ABAB_FORCED;    // conteggio job chiusi per max_hops (loop forzato)

        public String getName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

}
