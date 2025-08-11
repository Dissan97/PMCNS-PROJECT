package com.g45.webappsim.simulator;

import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class Node implements Comparable<Node> {
    private final String name;
    private final Map<Integer, Double> serviceMeans; // class -> mean service time E[S]
    private final List<Job> jobs = new ArrayList<>();
    private double lastUpdate = 0.0;
    private static final double EPS = 1e-12;
    private Event nextDeparture; // last scheduled DEPARTURE for this node

    public Node(String name, Map<Integer, Double> serviceMeans) {
        this.name = name;
        this.serviceMeans = new HashMap<>(serviceMeans);
    }

    public String getName() {
        return name;
    }

    public Map<Integer, Double> getServiceMeans() {
        return serviceMeans;
    }

    private void updateRemaining(double now) {
        if (jobs.isEmpty()) { lastUpdate = now; return; }
        double elapsed = Math.max(0.0, now - lastUpdate);
        double share = elapsed / jobs.size();
        for (Job j : jobs) {
            j.setRemainingService(Math.max(0.0, j.getRemainingService() - share));
        }
        lastUpdate = now;
    }

    public void arrival(Job job, NextEventScheduler scheduler) {
        updateRemaining(scheduler.getCurrentTime());
        jobs.add(job);
        scheduleNextDeparture(scheduler);
    }

    public void departure(Job job, NextEventScheduler scheduler) {
        if (!jobs.contains(job)) return; // orphan event already cancelled
        updateRemaining(scheduler.getCurrentTime());
        jobs.removeIf(j -> j.getId() == job.getId());
        scheduleNextDeparture(scheduler);
    }

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

    @Override
    public int compareTo(@NotNull Node o) {
        return Comparator.comparing(Node::getName).compare(this, o);
    }
}
