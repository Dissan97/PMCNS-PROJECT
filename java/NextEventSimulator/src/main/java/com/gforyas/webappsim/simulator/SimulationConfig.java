package com.gforyas.webappsim.simulator;

import com.gforyas.webappsim.estimators.StatsType;
import com.gforyas.webappsim.lemer.Rngs;
import com.gforyas.webappsim.util.SinkConvergenceToCsv;
import com.gforyas.webappsim.util.SinkToCsv;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Holds configuration parameters for a single simulation run.
 * <p>
 * Questa classe incapsula i parametri di input necessari per inizializzare
 * la simulazione, inclusi tassi di arrivo, tassi di servizio, regole di routing
 * e numero massimo di eventi da processare.
 * </p>
 */
public class SimulationConfig {

    private int warmupCompletions = 10_000; // default sensato
    private SinkToCsv sink;
    private SinkConvergenceToCsv sinkConv;
    private int initialSeed = 123456789;
    private StatsType statsType = StatsType.NORMAL;
    ;

    public int getWarmupCompletions() {
        return warmupCompletions;
    }

    public void setWarmupCompletions(int warmupCompletions) {
        this.warmupCompletions = warmupCompletions;
    }

    private static final AtomicInteger ARRIVAL_RATE_INDEX = new AtomicInteger(0);

    /** Tasso di arrivo esterno λ (jobs per unit time). */
    private List<Double> arrivalRate = List.of(1.2);

    /**
     * Tassi di servizio per nodo e classe.
     * Formato: nodeName → (classId → mean service time E[S]).
     */
    private Map<String, Map<String, Double>> serviceRates;

    /**
     * Routing deterministico (legacy).
     * Formato: nodeName → (classId → {@link TargetClass}).
     */
    private Map<String, Map<String, TargetClass>> routingMatrix;

    /** Numero massimo di eventi prima di fermare gli arrivi. */
    private int maxEvents;

    /** Costante per indicare l'uscita dal sistema. */
    public static final String EXIT = "EXIT";

    /** RNG per la simulazione. */
    private Rngs rngs = new Rngs();

    /** Numero di arrival event iniziali nello scheduler. */
    private int initialArrival = 0;

    /** Semi della simulazione. */
    private int seeds = 1;

    // --- Esistente per modalità LOAD_BALANCE ---
    private Map<String, Map<String, java.util.List<TargetClass>>> routingMatrixLB;
    private SimulationType simulationType = SimulationType.NORMAL;
    private Balancing balancing = Balancing.RR;   // usato solo se simulationType == LOAD_BALANCE

    private int batchSize = -1;
    private int batchCount = -1;

    // =========================
    // Nuovi campi per routing probabilistico
    // =========================

    /** Modalità di routing: "probabilistic" oppure assente ⇒ deterministico. */
    private String routingMode; // opzionale, può non esserci nel JSON

    /**
     * Tabella di routing probabilistico:
     * node → (classId → lista di ProbArc).
     * Nota: classId è Integer per semplicità di confronto aritmetico.
     */
    private Map<String, Map<Integer, List<ProbArc>>> probRoutingTable;

    /** Parametro di safety per evitare loop infiniti (opzionale). */
    private Integer safetyMaxHops;

    // =========================

    /** @return tasso di arrivo esterno λ (iterando la lista se multipla). */
    public double getArrivalRate() {
        int index = ARRIVAL_RATE_INDEX.getAndIncrement();
        if (index >= arrivalRate.size()) {
            ARRIVAL_RATE_INDEX.set(0);
            index = arrivalRate.size() - 1;
        }
        return arrivalRate.get(index);
    }

    public int getNumArrivals(){
        return arrivalRate.size();
    }

    public String getArrivalRates() {
        return this.arrivalRate.toString();
    }

    public Map<String, Map<String, Double>> getServiceRates() {
        return serviceRates;
    }

    public Map<String, Map<String, TargetClass>> getRoutingMatrix() {
        return routingMatrix;
    }

    public int getMaxEvents() {
        return maxEvents;
    }

    public void setArrivalRate(List<Double> arrivalRate) {
        this.arrivalRate = arrivalRate;
    }

    public void setServiceRates(Map<String, Map<String, Double>> serviceRates) {
        this.serviceRates = serviceRates;
    }

    public void setRoutingMatrix(Map<String, Map<String, TargetClass>> routingMatrix) {
        this.routingMatrix = routingMatrix;
    }

    public void setMaxEvents(int maxEvents) {
        this.maxEvents = maxEvents;
    }

    public Rngs getRngs() {
        return rngs;
    }

    public void setRngs(Rngs rngs) {
        this.rngs = rngs;
    }

    public void setInitialArrival(int initialArrival) {
        this.initialArrival = initialArrival;
    }

    public int getInitialArrival() {
        return this.initialArrival;
    }

    public int getSeeds() {
        return seeds;
    }

    public void setSeeds(int seeds) {
        this.seeds = seeds;
    }

    public Map<String, Map<String, java.util.List<TargetClass>>> getRoutingMatrixLB() {
        return routingMatrixLB;
    }

    public void setRoutingMatrixLB(Map<String, Map<String, java.util.List<TargetClass>>> routingMatrixLB) {
        this.routingMatrixLB = routingMatrixLB;
    }

    public SimulationType getSimulationType() { return simulationType; }
    public void setSimulationType(SimulationType simulationType) { this.simulationType = simulationType; }
    public Balancing getBalancing() { return balancing; }
    public void setBalancing(Balancing balancing) { this.balancing = balancing; }

    // =========================
    // Getter/Setter nuovi campi
    // =========================

    /** Ritorna la stringa della modalità (può essere null). */
    public String getRoutingMode() { return routingMode; }
    public void setRoutingMode(String routingMode) { this.routingMode = routingMode; }

    public Map<String, Map<Integer, List<ProbArc>>> getProbRoutingTable() { return probRoutingTable; }
    public void setProbRoutingTable(Map<String, Map<Integer, List<ProbArc>>> probRoutingTable) {
        this.probRoutingTable = probRoutingTable;
    }

    public Integer getSafetyMaxHops() { return safetyMaxHops; }
    public void setSafetyMaxHops(Integer safetyMaxHops) { this.safetyMaxHops = safetyMaxHops; }

    /** Comodità: true se il config è probabilistico (per modalità o per presenza tabella). */
    public boolean isProbabilistic() {
        return (routingMode != null && "probabilistic".equalsIgnoreCase(routingMode))
                || (probRoutingTable != null && !probRoutingTable.isEmpty());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{\n\t");
        if (simulationType.equals(SimulationType.LOAD_BALANCE)) {
            routingMatrixLB.keySet().stream().sorted().forEach(
                    key -> sb.append(key).append("=").append(routingMatrixLB.get(key)).append("\n\t"));
        } else if (routingMatrix != null) {
            routingMatrix.keySet().stream().sorted().forEach(
                    key -> sb.append(key).append("=").append(routingMatrix.get(key)).append("\n\t"));
        } else {
            sb.append("routingMatrix=").append("null").append("\n\t");
        }
        sb.append('}');

        if (batchSize > 0 && batchCount > 0) {
            sb.append("\n\t").append("Batch Length: ").append(batchSize);
            sb.append("\n\t").append("Max Batches: ").append(batchCount);
        }

        // Append info di routing probabilistico se presenti
        if (isProbabilistic()) {
            sb.append("\n\t").append("routingMode=").append(routingMode != null ? routingMode : "probabilistic(auto)");
            sb.append("\n\t").append("probRouting=").append(probRoutingTable != null ? "present" : "null");
            if (safetyMaxHops != null) {
                sb.append("\n\t").append("safetyMaxHops=").append(safetyMaxHops);
            }
        }

        return "SimulationConfig={" +
                "\narrivalRate=" + arrivalRate +
                "\n, serviceRates=" + serviceRates +
                "\n, routingMatrix=" + sb +
                "\n, maxEvents=" + maxEvents +
                "\n, initialArrival=" + initialArrival +
                "\n, seeds=" + seeds +
                "\n, warmupCompletions=" + warmupCompletions +
                "\n, simulationType=" + simulationType +
                (simulationType.equals(SimulationType.LOAD_BALANCE) ? "\n, balancing=" + balancing : "") +
                "\n}";
    }

    public int getBatchSize() {
        return this.batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getBatchCount() {
        return this.batchCount;
    }

    public void setBatchCount(int batchCount) {
        this.batchCount = batchCount;
    }

    public void setSink(SinkToCsv sink) {
        this.sink = sink;
    }

    public SinkToCsv getSink() {
        return this.sink;
    }

    public SinkConvergenceToCsv getSinkConv(){
        return this.sinkConv;
    }

    public void setSinkConv(SinkConvergenceToCsv sinkConv) {
        this.sinkConv = sinkConv;
    }

    public void setInitialSeed(int initialSeed) {
        this.initialSeed = initialSeed;
    }

    public int getInitialSeed() {
        return initialSeed;
    }

    public StatsType getStatsType() {
        return this.statsType;
    }

    public void setStatsType(StatsType statsType) {
        this.statsType = statsType;
    }
}