package com.gforyas.webappsim.util;

import java.util.*;
import java.util.stream.Collectors;

public class SinkObj5ToCsvCox extends SinkObj5ToCsv {

    private static final List<String> EXTRA_HEADERS = List.of("COX_MU", "COX_P");
    private final Map<String, List<String>> byParamKey = new HashMap<>();

    public SinkObj5ToCsvCox(int seed) {
        super(seed);
    }
    private static String toSemicolonString(double[] arr) {
        return "[" + Arrays.stream(arr)
                .mapToObj(Double::toString)
                .collect(Collectors.joining("; ")) + "; ]";
    }

    public void addCoxParams(double[] mu, double[] p) {
        String muStr = toSemicolonString(mu);
        String pStr  = toSemicolonString(p);
        String key = "mu=" + muStr + ";p=" + pStr;
        byParamKey.put(key, lines.stream()
                .map(s -> s.substring(0, s.length() - 1) + "," +
                        "-,"+
                        muStr + "," +
                        pStr + "\n")
                .toList());
        lines.clear();
    }

    @Override
    public void sink() {
        try (var writer = new java.io.BufferedWriter(new java.io.FileWriter(outputPath.toFile()))) {
            StringBuilder sb = new StringBuilder();

            // Base headers from parent + extras
            var base = Arrays.stream(CsvHeader.values())
                    .map(h -> h.name().toLowerCase(Locale.ROOT))
                    .toList();
            base.forEach(h -> sb.append(h).append(','));
            EXTRA_HEADERS.forEach(h -> sb.append(h).append(','));
            sb.replace(sb.length() - 1, sb.length(), "\n");

            for (var entry : byParamKey.entrySet()) {
                for (var line : byParamKey.get(entry.getKey())) sb.append(line);
            }
            writer.write(sb.toString());
            writer.flush();
        } catch (java.io.IOException e) {
            com.gforyas.webappsim.logging.SysLogger.getInstance().getLogger()
                    .severe("Io problem " + e.getMessage());
        }
    }
}
