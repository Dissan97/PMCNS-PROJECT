package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;
import com.g45.webappsim.simulator.TargetClass;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Colleziona T_A, T_B, T_P per job a EXIT.
 * - Accumula tutte le righe in RAM (StringBuilder) senza I/O durante la simulazione.
 * - Mantiene anche i campioni in memoria per il calcolo di var/cov.
 * - Alla fine, chiamare flushToDisk() per scrivere in un colpo solo.
 */
public class ResponseTimePerJobCollector {

    private final Map<String, Map<String, TargetClass>> routing;
    private final ResponseTimeEstimatorNode estA, estB, estP;

    private final Path outPath;

    // Buffer in RAM per le righe CSV (senza header)
    private final StringBuilder sb = new StringBuilder(1 << 20); // ~1 MB iniziali

    // Campioni in RAM per var/cov (allineati per indice)
    private final List<Double> tA = new ArrayList<>();
    private final List<Double> tB = new ArrayList<>();
    private final List<Double> tP = new ArrayList<>();

    public ResponseTimePerJobCollector(NextEventScheduler sched,
                                       Map<String, Map<String, TargetClass>> routing,
                                       ResponseTimeEstimatorNode estA,
                                       ResponseTimeEstimatorNode estB,
                                       ResponseTimeEstimatorNode estP,
                                       Path outCsv) {
        this.routing = routing;
        this.estA = estA; this.estB = estB; this.estP = estP;
        this.outPath = outCsv;

        // niente I/O qui: header scritto in flushToDisk() se necessario
        sched.subscribe(Event.Type.DEPARTURE, this::onDeparture);
    }

    private void onDeparture(Event e, NextEventScheduler s) {
        if (e.getJobId() < 0) return;
        if (!routesToExit(e.getServer(), e.getJobClass())) return;

        int id = e.getJobId();
        double TA = estA.takeAndClear(id);
        double TB = estB.takeAndClear(id);
        double TP = estP.takeAndClear(id);
        double TOT = TA + TB + TP;

        // Append SOLO in memoria
        sb.append(id).append(',')
          .append(TA).append(',')
          .append(TB).append(',')
          .append(TP).append(',')
          .append(TOT).append(',')
          .append(e.getTime()).append('\n');

        // Mantieni i campioni per covarianze
        tA.add(TA); tB.add(TB); tP.add(TP);
    }

    private boolean routesToExit(String server, int jobClass) {
        Map<String, TargetClass> m = routing.get(server);
        if (m == null) return false;
        TargetClass tc = m.get(Integer.toString(jobClass));
        return tc != null && "EXIT".equalsIgnoreCase(tc.eventClass());
    }

    // ---- API per EstimatorFacade ----

    public int size() { return tA.size(); }
    public double[] getTA() { return toArray(tA); }
    public double[] getTB() { return toArray(tB); }
    public double[] getTP() { return toArray(tP); }

    /**
     * Scrive su disco in un colpo:
     * - crea la cartella se serve
     * - scrive l'header SE il file non esiste
     * - appende il buffer di righe accumulate
     */
    public void flushToDisk() {
        try {
            Files.createDirectories(outPath.getParent());
            boolean exists = Files.exists(outPath);
            try (BufferedWriter w = Files.newBufferedWriter(
                    outPath,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND)) {
                if (!exists) {
                    w.write("job_id,T_A,T_B,T_P,T_total,t_complete\n");
                }
                w.write(sb.toString());
            }
            // opzionale: libera RAM se vuoi continuare
            sb.setLength(0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static double[] toArray(List<Double> src) {
        double[] a = new double[src.size()];
        for (int i = 0; i < src.size(); i++) a[i] = src.get(i);
        return a;

    }

    public void startCollecting() {
        sb.setLength(0);
        tA.clear();
        tB.clear();
        tP.clear();
        // non serve altro: le somme per-job vive nei Node estimator (già azzerate sopra)
    }
}
