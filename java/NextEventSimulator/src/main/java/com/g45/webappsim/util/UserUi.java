package com.g45.webappsim.util;

import com.g45.webappsim.simulator.SimulationConfig;
import com.g45.webappsim.simulator.TargetClass;

import java.util.*;

/**
 * Provides all interactive features to review and modify the SimulationConfig.
 * Uses the custom PersonalPrintStream for any user-facing text (no System.out).
 */
public class UserUi {

    private final PersonalPrintStream out;
    private final Scanner in;

    /**
     * Creates a UI bound to the provided Scanner (input) and PersonalPrintStream (output).
     * @param inputScanner scanner reading from user input (e.g., System.in)
     */
    public UserUi(Scanner inputScanner) {
        this.out = Printer.out();
        this.in = inputScanner;
    }

    /**
     * Runs a confirmation-based wizard that:
     * 1) Shows a summary of the current configuration.
     * 2) Ask the user whether they want to modify it.
     * 3) If yes, opens a loop menu to edit key fields.
     * 4) Ends with an explicit "Start simulation" confirmation.
     * @param cfg the configuration to review and update
     * @return true if the user chose to start the simulation; false if aborted
     */
    public boolean runInteractiveConfig(SimulationConfig cfg) {
        printSummary(cfg);

        if (!askYesNo("Do you want to change the configuration? [y/N]")) {
            out.info("Starting simulation with current configuration.");
            return true;
        }

        boolean done = false;
        while (!done) {
            out.header("Configuration Menu");
            out.info("1) Set Arrival Rate (λ)");
            out.info("2) Set Max Events");
            out.info("3) Set Initial Arrivals");
            out.info("4) Edit Service Rate for a Node/Class");
            out.info("5) Edit Seeds");
            out.info("6) Edit warmup completion");
            out.info("7) Show Summary");
            out.info("8) Start Simulation");
            out.info("9) Abort");

            int choice = askInt("Enter choice [1-9]: ", 1, 8);
            switch (choice) {
                case 1 -> setArrivalRate(cfg);
                case 2 -> setMaxEvents(cfg);
                case 3 -> setInitialArrivals(cfg);
                case 4 -> editServiceRate(cfg);
                case 5 -> editSeeds(cfg);
                case 6 -> editWarmUp(cfg);
                case 7 -> printSummary(cfg);
                case 8 -> {
                    if (askYesNo("Confirm start simulation now? [y/N]")) {
                        done = true;
                    }
                }
                case 9 -> {
                    out.warn("Aborted by user.");
                    return false;
                }
                default -> out.warn("Invalid choice.");
            }
        }
        return true;
    }



    /** Prints a concise configuration summary. */
    private void printSummary(SimulationConfig cfg) {
        out.header("Current Configuration");
        out.info("Arrival rate λ: " + cfg.getArrivalRate());
        out.info("Max events: " + cfg.getMaxEvents());
        out.info("Initial arrivals: " + cfg.getInitialArrival());
        out.info("Seeds: " + cfg.getSeeds());
        out.info("Warmup Completions " + cfg.getWarmupCompletions());
        Map<String, Map<String, Double>> sr = cfg.getServiceRates();
        if (sr != null && !sr.isEmpty()) {
            out.info("Service rates (node → class → mean S):");
            for (var nodeEntry : sr.entrySet()) {
                out.info("  " + nodeEntry.getKey() + " : " + nodeEntry.getValue());
            }
        } else {
            out.warn("No service rates configured.");
        }
        Map<String, Map<String, TargetClass>> rt = cfg.getRoutingMatrix();
        if (rt != null && !rt.isEmpty()) {
            for (var nodeEntry : rt.entrySet()) {
                out.info("  " + nodeEntry.getKey() + " : " + nodeEntry.getValue());
            }
        } else {
            out.warn("No routing matrix configured.");
        }
    }

    /** Prompts and sets the arrival rate (λ). */
    private void setArrivalRate(SimulationConfig cfg) {
        double v = askDouble("Enter arrival rate λ (> 0): ", d -> d > 0.0);
        cfg.setArrivalRate(v);
        out.info("Arrival rate set to " + v);
    }

    /** Prompts and sets the max events (integer > 0). */
    private void setMaxEvents(SimulationConfig cfg) {
        int v = askInt("Enter max events (> 0): ", 1, Integer.MAX_VALUE);
        cfg.setMaxEvents(v);
        out.info("Max events set to " + v);
    }

    /** Prompts and sets the initial arrivals (integer ≥ 0). */
    private void setInitialArrivals(SimulationConfig cfg) {
        int v = askInt("Enter initial arrivals (≥ 0): ", 0, Integer.MAX_VALUE);
        cfg.setInitialArrival(v);
        out.info("Initial arrivals set to " + v);
    }

    /**
     * Allows editing a single service rate for (node, class).
     * Creates the nested maps as needed.
     */
    private void editServiceRate(SimulationConfig cfg) {
        String node = askNonEmpty("Enter node name (e.g., 'A' or 'FrontEnd'): ");
        String clazz = askNonEmpty("Enter class id (e.g., 'default'): ");
        double meanService = askDouble("Enter mean service time E[S] (> 0): ", d -> d > 0.0);

        Map<String, Map<String, Double>> sr = cfg.getServiceRates();
        if (sr == null) {
            sr = new HashMap<>();
            cfg.setServiceRates(sr);
        }
        sr.computeIfAbsent(node, k -> new HashMap<>()).put(clazz, meanService);
        out.info("Service rate set for (" + node + ", " + clazz + ") = " + meanService);
    }
    private void editWarmUp(SimulationConfig cfg) {
        out.info("Current Warmup Completions " + cfg.getWarmupCompletions());
        int warmup = askAnyInt("insert warmup setup");
        if (warmup < 0) {
            out.warn("Invalid warmup setup. it must be >= 0");
            return;
        }
        cfg.setWarmupCompletions(warmup);

    }
    /**
     * Opens a mini-menu to replace or append seeds.
     * Validates integer parsing and shows the resulting list.
     */
    private void editSeeds(SimulationConfig cfg) {
        List<Integer> seeds = new ArrayList<>(cfg.getSeeds() != null ? cfg.getSeeds() : List.of());
        boolean back = false;
        while (!back) {
            out.header("Seeds Editor");
            out.info("Current seeds: " + seeds);
            out.info("1) Replace all");
            out.info("2) Add one");
            out.info("3) Remove one");
            out.info("4) Done");
            int ch = askInt("Enter choice [1-4]: ", 1, 4);
            switch (ch) {
                case 1 -> {
                    String line = askNonEmpty("Enter comma-separated integers: ");
                    List<Integer> newSeeds = parseSeeds(line);
                    seeds.clear();
                    seeds.addAll(newSeeds);
                    out.info("Seeds replaced.");
                }
                case 2 -> {
                    int s = askAnyInt("Enter integer seed: ");
                    seeds.add(s);
                    out.info("Seed added.");
                }
                case 3 -> {
                    int s = askAnyInt("Enter integer seed to remove: ");
                    if (seeds.remove((Integer) s)) {
                        out.info("Seed removed.");
                    } else {
                        out.warn("Seed not found.");
                    }
                }
                case 4 -> back = true;
                default -> out.warn("Invalid choice.");
            }
        }
        cfg.setSeeds(seeds);
        out.info("Final seeds: " + seeds);
    }

    /** Parses a comma-separated list of integers into a List<Integer>. */
    private List<Integer> parseSeeds(String text) {
        String[] toks = text.split(",");
        List<Integer> res = new ArrayList<>();
        for (String t : toks) {
            t = t.trim();
            if (!t.isEmpty()) {
                res.add(Integer.parseInt(t));
            }
        }
        return res;
    }

    /** Asks a yes/no question; defaults to false if empty or unrecognized. */
    private boolean askYesNo(String prompt) {
        out.prompt(prompt);
        String s = in.nextLine().trim().toLowerCase(Locale.ROOT);
        return s.equals("y") || s.equals("yes");
    }

    /** Asks for a non-empty string. */
    private String askNonEmpty(String prompt) {
        while (true) {
            out.prompt(prompt);
            String s = in.nextLine().trim();
            if (!s.isEmpty()) return s;
            out.warn("Value cannot be empty.");
        }
    }

    /** Asks for any integer (no bounds). */
    private int askAnyInt(String prompt) {
        while (true) {
            out.prompt(prompt);
            String s = in.nextLine().trim();
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                out.warn("Please enter a valid integer.");
            }
        }
    }

    /** Asks for an integer within [min, max]. */
    private int askInt(String prompt, int min, int max) {
        while (true) {
            int v = askAnyInt(prompt);
            if (v >= min && v <= max) return v;
            out.warn("Value must be in [" + min + ", " + max + "].");
        }
    }

    /** Functional interface for double validation. */
    private interface DoubleValidator { boolean ok(double d); }

    /** Asks for a double that satisfies the validator. */
    private double askDouble(String prompt, DoubleValidator validator) {
        while (true) {
            out.prompt(prompt);
            String s = in.nextLine().trim();
            try {
                double v = Double.parseDouble(s);
                if (validator.ok(v)) return v;
                out.warn("Value not accepted by constraints.");
            } catch (NumberFormatException e) {
                out.warn("Please enter a valid number.");
            }
        }
    }
}
