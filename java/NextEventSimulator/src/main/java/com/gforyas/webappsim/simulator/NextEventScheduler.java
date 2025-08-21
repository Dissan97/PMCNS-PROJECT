package com.gforyas.webappsim.simulator;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Next-Event-Simulation scheduler that processes events in chronological order.
 * <p>
 * Events are stored in a priority queue ordered by their scheduled execution time,
 * and optionally by insertion order in case of ties.
 * </p>
 * <p>
 * Subscribers can register callbacks for specific {@link Event.Type}s, which will
 * be invoked when an event of that type is processed.
 * </p>
 */
public final class NextEventScheduler {

    /**
     * Priority queue storing all future events, ordered by time.
     */
    private final PriorityQueue<Event> pq = new PriorityQueue<>();

    /**
     * Map from event type to list of subscriber callbacks that handle the event.
     */
    private final Map<Event.Type, List<BiConsumer<Event, NextEventScheduler>>> subscribers =
            new EnumMap<>(Event.Type.class);

    /**
     * Map from event type to list of interceptor callbacks that can process an event before subscribers.
     * (Currently unused in main loop unless integrated manually.)
     */
    private final Map<Event.Type, List<BiConsumer<Event, NextEventScheduler>>> interceptors =
            new EnumMap<>(Event.Type.class);

    /**
     * Small epsilon to handle floating-point precision issues when comparing times.
     */
    private static final double EPS = 1e-12;

    /**
     * Shared job table mapping job IDs to {@link Job} instances.
     * This is accessible by all nodes, similar to the Python implementation.
     */
    final Map<Integer, Job> jobTable = new HashMap<>();

    /**
     * Current simulation time.
     */
    private double currentTime = 0.0;

    /**
     * Schedules an event to occur immediately (delay = 0).
     *
     * @param e the event to schedule
     */
    public void schedule(Event e) {
        this.schedule(e, 0.0);
    }

    /**
     * Schedules an event to occur at an absolute simulation time.
     *
     * @param e       the event to schedule
     * @param absTime absolute time for the event
     */
    public void scheduleAt(Event e, double absTime) {
        if (absTime < currentTime - EPS) {
            absTime = currentTime;
        }
        e.setTime(absTime);
        pq.add(e);
    }

    /**
     * Schedules an event to occur after a given delay from the current time.
     *
     * @param e     the event to schedule
     * @param delay the delay in simulation time units
     */
    public void scheduleAfter(Event e, double delay) {
        if (delay < 0) delay = 0;
        e.setTime(currentTime + delay);
        pq.add(e);
    }

    /**
     * Checks if there are any pending events in the queue.
     *
     * @return {@code true} if events are scheduled, {@code false} otherwise
     */
    public boolean hasNext() {
        return !pq.isEmpty();
    }

    /**
     * Processes the next event in chronological order.
     * <p>
     * - Updates the simulation time to the event's time.
     * - Calls all subscribed handlers for the event type.
     * - Skips cancelled events.
     * </p>
     */
    public void next() {
        while (!pq.isEmpty()) {
            Event e = pq.poll();
            if (e.isCancelled()) continue;
            currentTime = Math.max(currentTime, e.getTime());

            // Notify subscribers
            List<BiConsumer<Event, NextEventScheduler>> subs = subscribers.get(e.getType());
            if (subs != null) {
                for (BiConsumer<Event, NextEventScheduler> consumer : subs) {
                    consumer.accept(e, this);
                }
            }
            return;
        }
    }

    /**
     * Schedules an event after the specified delay from the current time.
     *
     * @param e     the event to schedule
     * @param delay delay in simulation time units
     */
    public void schedule(Event e, double delay) {
        e.setTime(getCurrentTime() + delay);
        pq.add(e);
    }

    /**
     * Cancels a scheduled event, preventing it from being executed.
     *
     * @param e the event to cancel
     */
    public void cancel(Event e) {
        if (e != null) e.cancel();
    }

    /**
     * Subscribes a handler to a specific event type.
     *
     * @param t       the event type
     * @param handler the callback to execute when the event occurs
     */
    public void subscribe(Event.Type t, BiConsumer<Event, NextEventScheduler> handler) {
        subscribers.computeIfAbsent(t, k -> new ArrayList<>()).add(handler);
    }

    /**
     * Registers an interceptor for a specific event type.
     * Interceptors can process or modify events before normal subscribers.
     *
     * @param t       the event type
     * @param handler the interceptor callback
     */
    public void intercept(Event.Type t, BiConsumer<Event, NextEventScheduler> handler) {
        interceptors.computeIfAbsent(t, k -> new ArrayList<>()).add(handler);
    }

    /**
     * Returns the current simulation time.
     *
     * @return the current simulation time
     */
    public double getCurrentTime() {
        return currentTime;
    }

    /**
     * Returns the shared job table.
     *
     * @return the job table
     */
    public Map<Integer, Job> getJobTable() {
        return jobTable;
    }
}
