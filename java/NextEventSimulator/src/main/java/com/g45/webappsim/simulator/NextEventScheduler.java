package com.g45.webappsim.simulator;

import java.util.*;

import java.util.function.BiConsumer;

public final class NextEventScheduler {
    private final PriorityQueue<Event> pq = new PriorityQueue<>();
    private final Map<Event.Type, List<BiConsumer<Event, NextEventScheduler>>> subscribers = new EnumMap<>(Event.Type.class);
    private final Map<Event.Type, List<BiConsumer<Event, NextEventScheduler>>> interceptors = new EnumMap<>(Event.Type.class);
    private static final double EPS = 1e-12;
    // Shared job table like the Python version
    final Map<Integer, Job> jobTable = new HashMap<>();

    private double currentTime = 0.0;

    public void schedule(Event e) {
        this.schedule(e, 0.0);
    }

    public void scheduleAt(Event e, double absTime) {
        if (absTime < currentTime - EPS) {
            absTime = currentTime;
        }
        e.setTime(absTime);
        pq.add(e);
    }

    public void scheduleAfter(Event e, double delay) {
        if (delay < 0) delay = 0;
        e.setTime(currentTime + delay);
        pq.add(e);
    }

    public boolean hasNext() {
        return !pq.isEmpty();
    }

    public void next() {
        while (!pq.isEmpty()) {
            Event e = pq.poll();
            if (e.isCancelled()) continue;
            currentTime = Math.max(currentTime, e.getTime());
            for (BiConsumer<Event, NextEventScheduler> consumer: subscribers.get(e.getType())) {
                consumer.accept(e, this);
            }
            return;

        }

    }
    public void schedule(Event e, double delay) {
        e.setTime(getCurrentTime() + delay);
        pq.add(e);
    }

    public void cancel(Event e) {
        if (e != null) e.cancel();
    }



    public void subscribe(Event.Type t, BiConsumer<Event, NextEventScheduler> handler) {
        subscribers.computeIfAbsent(t, k -> new ArrayList<>()).add(handler);
    }

    public void intercept(Event.Type t, BiConsumer<Event, NextEventScheduler> handler) {
        interceptors.computeIfAbsent(t, k -> new ArrayList<>()).add(handler);
    }

    public double getCurrentTime() {
        return currentTime;
    }

    public Map<Integer, Job> getJobTable() {
        return jobTable;
    }
}
