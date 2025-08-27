package com.gforyas.webappsim.util;

import com.gforyas.webappsim.logging.SysLogger;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class SinkObj5ToCsv extends SinkToCsv {

    private static final List<String> HEADERS = Arrays.stream(CsvHeader.values())
            .map(s -> s.name().toLowerCase(Locale.ROOT))
            .toList();

    public SinkObj5ToCsv(int seed) {
        super(seed);
    }

    public void addProb(double p){
        mapLines.put(p,lines.stream().map(s->s.substring(0, s.length() - 1)+"," +p+"\n").collect(Collectors.toList()));
        lines.clear();
    }

    Map<Double, List<String>> mapLines = new HashMap<>();

    @Override
    public void sink() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath.toFile()))) {

            StringBuilder stringBuilder = new StringBuilder();
            HEADERS.forEach(s -> stringBuilder.append(s).append(','));
            stringBuilder.replace(stringBuilder.length() - 1, stringBuilder.length(), "\n");
            for (var key : mapLines.keySet().stream().sorted(Double::compare).toList()) {
                for(var line : mapLines.get(key)) {
                    stringBuilder.append(line);
                }
            }
            writer.write(stringBuilder.toString());
            writer.flush();

        } catch (IOException e) {
            String severe = " Io problem " + e.getMessage();
            SysLogger.getInstance().getLogger().severe(severe);
        }
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
        PATH_ABAB_FORCED,
        PROBABILITY
    }
}
