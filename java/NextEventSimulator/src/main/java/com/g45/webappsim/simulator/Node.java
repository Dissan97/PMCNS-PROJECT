package com.g45.webappsim.simulator;

import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Represents a single server node in the network simulation.
 * <p>
 * A node contains a set of jobs being processed and schedules departure events
 * based on their remaining service time, using a processor-sharing (PS) discipline.
 * </p>
 */
public final class Node implements Comparable<Node> {

    /**
     * Name of the node (e.g., "A", "B", "P").
     */
    private final String name;

    /**
     * Mean service times per job class.
     * Maps class ID to expected service time E[S].
     */
    private final Map<Integer, Double> serviceMeans;

    /**
     * Jobs currently in service at this node.
     */
    private final List<Job> jobs = new ArrayList<>();

    /**
     * Last time the remaining service times were updated.
     */
    private double lastUpdate = 0.0;

    /**
     * Small epsilon to avoid scheduling zero-length events due to floating-point errors.
     */
    private static final double EPS = 1e-12;

    /**
     * The next departure event scheduled for this node.
     * Used to cancel and reschedule when job list changes.
     */
    private Event nextDeparture;

    /**
     * Creates a node with a given name and mean service times per class.
     *
     * @param name          the node name
     * @param serviceMeans  mapping from job class to mean service time
     */
    public Node(String name, Map<Integer, Double> serviceMeans) {
        this.name = name;
        this.serviceMeans = new HashMap<>(serviceMeans);
    }

    /**
     * Gets the node name.
     *
     * @return the node name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the map of mean service times per job class.
     *
     * @return mapping from class ID to mean service time
     */
    public Map<Integer, Double> getServiceMeans() {
        return serviceMeans;
    }

    /**
     * Updates the remaining service time for all jobs
     * based on elapsed simulation time and processor-sharing allocation.
     *
     * @param now the current simulation time
     */
    private void updateRemaining(double now) {
        if (jobs.isEmpty()) {
            lastUpdate = now;
            return;
        }
        double elapsed = Math.max(0.0, now - lastUpdate);
        double share = elapsed / jobs.size();
        for (Job j : jobs) {
            j.setRemainingService(Math.max(0.0, j.getRemainingService() - share));
        }
        lastUpdate = now;
    }

    /**
     * Handles an arrival event for a new job at this node.
     * Updates remaining times, adds the job, and schedules next departure.
     *
     * @param job       the arriving job
     * @param scheduler the event scheduler
     */
    public void arrival(Job job, NextEventScheduler scheduler) {
        updateRemaining(scheduler.getCurrentTime());
        jobs.add(job);
        scheduleNextDeparture(scheduler);
    }

    /**
     * Handles a departure event for a job at this node.
     * Updates remaining times, removes the job, and reschedules next departure.
     *
     * @param job       the departing job
     * @param scheduler the event scheduler
     */
    public void departure(Job job, NextEventScheduler scheduler) {
        if (!jobs.contains(job)) return; // orphan event already cancelled
        updateRemaining(scheduler.getCurrentTime());
        jobs.removeIf(j -> j.getId() == job.getId());
        scheduleNextDeparture(scheduler);
    }

    /**
     * Cancels any existing departure event and schedules the next one
     * for the job with the smallest remaining service time.
     *
     * @param scheduler the event scheduler
     */
    private void scheduleNextDeparture(NextEventScheduler scheduler) {
        if (nextDeparture != null) {
            scheduler.cancel(nextDeparture);
            nextDeparture = null;
        }
        if (jobs.isEmpty()) return;

        Job minJob = Collections.min(jobs, Comparator.comparingDouble(Job::getRemainingService));
        double ttf = Math.max(EPS, minJob.getRemainingService() * jobs.size());
        Event e = new Event(0.0, Event.Type.DEPARTURE, name, minJob.getId(), minJob.getJobClass());
        nextDeparture = e;
        scheduler.scheduleAt(e, scheduler.getCurrentTime() + ttf);
    }

    /**
     * Compares nodes by name.
     *
     * @param o the other node
     * @return comparison result
     */
    @Override
    public int compareTo(@NotNull Node o) {
        return Comparator.comparing(Node::getName).compare(this, o);
    }
}
