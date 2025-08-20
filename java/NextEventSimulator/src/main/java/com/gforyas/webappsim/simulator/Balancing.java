package com.gforyas.webappsim.simulator;

/** Load balancing policy selector (config-level). */
public enum Balancing {
    RR,     // Round-robin
    RND,    // Random
    LEAST;  // Least-busy

    /** Parse tolerant names (RR/ROUND_ROBIN, RND/RANDOM, LEAST/LEAST_BUSY). */
    public static Balancing parse(String s) {
        if (s == null) return RR;
        String u = s.trim().toUpperCase();
        return switch (u) {
            case "RND", "RANDOM" -> RND;
            case "LEAST", "LEAST_BUSY" -> LEAST;
            default -> RR;
        };
    }
}
