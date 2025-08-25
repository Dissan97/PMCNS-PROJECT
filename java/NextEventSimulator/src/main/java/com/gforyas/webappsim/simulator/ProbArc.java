package com.gforyas.webappsim.simulator;

/**
 * Rappresenta un arco probabilistico della matrice di routing.
 * - target: nodo di destinazione oppure "EXIT"
 * - nextClass: classe del job in arrivo al nodo di destinazione (null se target == EXIT)
 * - p: probabilità associata all'arco (0 < p <= 1)
 */
public final class ProbArc {
    private final String target;
    private final Integer nextClass; // null ammesso solo per EXIT
    private final double p;

    public ProbArc(String target, Integer nextClass, double p) {
        this.target = target;
        this.nextClass = nextClass;
        this.p = p;
    }

    public String getTarget() {
        return target;
    }

    public Integer getNextClass() {
        return nextClass;
    }

    public double getP() {
        return p;
    }

    @Override
    public String toString() {
        return "ProbArc{" +
                "target='" + target + '\'' +
                ", nextClass=" + nextClass +
                ", p=" + p +
                '}';
    }
}
