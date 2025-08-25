package com.gforyas.webappsim.simulator.router;

import com.gforyas.webappsim.lemer.Rngs;
import com.gforyas.webappsim.logging.SysLogger;
import com.gforyas.webappsim.simulator.ProbArc;
import com.gforyas.webappsim.simulator.SimulationConfig;
import com.gforyas.webappsim.simulator.TargetClass;

import java.util.*;

/**
 * Router probabilistico: per ogni (server, class) ha una lista di archi {target, nextClass, p}.
 * In costruzione pre-calcola i CDF per selezione O(1) tramite un'unica estrazione U(0,1).
 *
 * Nota: usa uno stream dedicato del Rngs (routingStreamId) che DEVE essere impostato
 * prima dell'uso tramite {@link #setRoutingStreamId(int)} per non “inquinare” gli stream
 * dei tempi di servizio.
 */
public class ProbabilisticRouter implements Router {

    /** Tabella probabilistica: server -> class -> lista di archi con p */
    private final Map<String, Map<Integer, List<ProbArc>>> table;

    /** CDF pre-calcolati allineati agli archi in table */
    private final Map<String, Map<Integer, double[]>> cdf;

    /** Stream id dedicato al routing (deve essere impostato dalla Simulation) */
    private Integer routingStreamId = null;

    /** Tolleranza numerica per Σp */
    private static final double TOL = 1e-9;

    public ProbabilisticRouter(Map<String, Map<Integer, List<ProbArc>>> probRoutingTable) {
        this.table = (probRoutingTable == null) ? Map.of() : probRoutingTable;
        this.cdf = precomputeCdf(this.table);
        this.routingStreamId = Rngs.getStreamId();
    }

    /**
     * Imposta lo stream id da usare per le estrazioni U(0,1) del routing.
     * @param streamId id di stream di {@link Rngs#random(int)}
     */
    public void setRoutingStreamId(int streamId) {
        this.routingStreamId = streamId;
    }

    @Override
    public TargetClass next(String currentServer, int currentClass, Rngs rng) {
        Map<Integer, List<ProbArc>> byClass = table.get(currentServer);
        if (byClass == null) return null;
        List<ProbArc> arcs = byClass.get(currentClass);
        if (arcs == null || arcs.isEmpty()) return null;

        if (routingStreamId == null) {
            String severe = "ProbabilisticRouter: routingStreamId non impostato (devi chiamare setRoutingStreamId in Simulation).";
            SysLogger.getInstance().getLogger().severe(severe);
            throw new IllegalStateException(severe);
        }

        double u = rng.random(this.routingStreamId);
        double[] cum = cdf.get(currentServer).get(currentClass);

        // selezione indice tramite CDF
        int idx = selectByCdf(u, cum);

        ProbArc arc = arcs.get(idx);
        String target = arc.getTarget();

        if (SimulationConfig.EXIT.equalsIgnoreCase(target)) {
            // EXIT: restituisco un TargetClass con eventClass="EXIT" (server non usato)
            return new TargetClass(currentServer, SimulationConfig.EXIT);
        } else {
            // Hop verso un nodo: nextClass deve essere non-null
            Integer nextCls = arc.getNextClass();
            if (nextCls == null) {
                // Non dovrebbe accadere: parser già valida. Log di sicurezza.
                SysLogger.getInstance().getLogger()
                        .severe("ProbabilisticRouter: nextClass null per target=" + target +
                                " su (" + currentServer + ", class=" + currentClass + ")");
                return null;
            }
            return new TargetClass(target, Integer.toString(nextCls));
        }
    }

    @Override
    public boolean isProbabilistic() {
        return true;
    }

    // =========================
    // Helpers
    // =========================

    /** Pre-calcola i CDF (cumulativi) per ogni (server,class). Forza l'ultimo a 1.0. */
    private static Map<String, Map<Integer, double[]>> precomputeCdf(Map<String, Map<Integer, List<ProbArc>>> tbl) {
        Map<String, Map<Integer, double[]>> out = new HashMap<>();

        for (Map.Entry<String, Map<Integer, List<ProbArc>>> eServer : tbl.entrySet()) {
            String server = eServer.getKey();
            Map<Integer, List<ProbArc>> byClass = eServer.getValue();

            Map<Integer, double[]> perClassCdf = new HashMap<>();
            for (Map.Entry<Integer, List<ProbArc>> eCls : byClass.entrySet()) {
                int cls = eCls.getKey();
                List<ProbArc> arcs = eCls.getValue();

                double[] cum = new double[arcs.size()];
                double sum = 0.0;
                for (int i = 0; i < arcs.size(); i++) {
                    double p = arcs.get(i).getP();
                    sum += p;
                    cum[i] = sum;
                }
                // Validazione lieve: il parser già garantisce Σp≈1, qui allineiamo l'ultimo a 1.0
                if (Math.abs(sum - 1.0) > TOL) {
                    SysLogger.getInstance().getLogger()
                            .warning(String.format(Locale.ROOT,
                                    "ProbabilisticRouter: Σp=%.12f per (%s, class=%d). " +
                                            "Il parser dovrebbe già garantire Σp≈1.", sum, server, cls));
                }
                cum[cum.length - 1] = 1.0; // forza 1.0 per robustezza numerica

                perClassCdf.put(cls, cum);
            }
            out.put(server, perClassCdf);
        }
        return out;
    }

    /** Selezione lineare su CDF (liste piccole: costo trascurabile). */
    private static int selectByCdf(double u, double[] cum) {
        for (int i = 0; i < cum.length; i++) {
            if (u <= cum[i]) return i;
        }
        // Fallback numerico
        return cum.length - 1;
    }
}
