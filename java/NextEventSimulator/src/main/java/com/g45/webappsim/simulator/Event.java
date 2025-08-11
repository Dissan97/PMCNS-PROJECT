package com.g45.webappsim.simulator;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a scheduled event in the Next-Event-Simulation.
 * <p>
 * Each event has a timestamp, type, target server, job identifiers,
 * and a unique sequence number to break ties between events with
 * the same timestamp.
 * </p>
 */
public final class Event implements Comparable<Event> {

    /**
     * Enumeration of possible event types in the simulation.
     */
    public enum Type { ARRIVAL, DEPARTURE }

    /**
     * Global atomic counter to assign unique, monotonically increasing IDs to events.
     * Used to ensure deterministic ordering for events with the same time.
     */
    private static final AtomicLong SEQ = new AtomicLong(0);

    /**
     * Unique sequence ID for this event.
     */
    private final long id;

    /**
     * Simulation time at which this event occurs.
     */
    private double time;

    /**
     * Type of the event (arrival or departure).
     */
    private final Type type;

    /**
     * Name of the server (node) this event is associated with.
     */
    private final String server;

    /**
     * Identifier of the job this event refers to.
     * <p>
     * A value of {@code -1} indicates an external arrival (i.e., a job entering the system).
     * </p>
     */
    private final int jobId;

    /**
     * Class/type of the job (e.g., 1, 2, 3...).
     */
    private final int jobClass;

    /**
     * Flag indicating whether this event has been cancelled.
     * Cancelled events are skipped during simulation processing.
     */
    private volatile boolean cancelled;

    /**
     * Creates a new simulation event.
     *
     * @param time     The simulation time at which the event occurs.
     * @param type     The event type (arrival or departure).
     * @param server   The server (node) name the event is associated with.
     * @param jobId    The ID of the job associated with the event (-1 for external arrivals).
     * @param jobClass The class of the job.
     */
    public Event(double time, Type type, String server, int jobId, int jobClass) {
        this.id = SEQ.getAndIncrement();
        this.time = time;
        this.type = type;
        this.server = server;
        this.jobId = jobId;
        this.jobClass = jobClass;
        this.cancelled = false;
    }

    /**
     * Returns the unique ID of the event.
     *
     * @return event ID
     */
    public long getId() { return id; }

    /**
     * Returns the time at which the event occurs.
     *
     * @return event time
     */
    public double getTime() { return time; }

    /**
     * Updates the event's scheduled time.
     *
     * @param t new event time
     */
    public void setTime(double t) { this.time = t; }

    /**
     * Returns the type of the event.
     *
     * @return event type
     */
    public Type getType() { return type; }

    /**
     * Returns the name of the server the event is associated with.
     *
     * @return server name
     */
    public String getServer() { return server; }

    /**
     * Returns the job ID associated with this event.
     *
     * @return job ID (-1 for external arrivals)
     */
    public int getJobId() { return jobId; }

    /**
     * Returns the job class associated with this event.
     *
     * @return job class
     */
    public int getJobClass() { return jobClass; }

    /**
     * Checks whether this event has been cancelled.
     *
     * @return {@code true} if cancelled, {@code false} otherwise
     */
    public boolean isCancelled() { return cancelled; }

    /**
     * Marks this event as cancelled.
     * Cancelled events are ignored by the scheduler.
     */
    public void cancel() { this.cancelled = true; }

    /**
     * Compares two events first by time, then by sequence ID if times are equal.
     *
     * @param o the other event
     * @return a negative value if this event is earlier, positive if later, or 0 if equal
     */
    @Override
    public int compareTo(Event o) {
        int cmp = Double.compare(this.time, o.time);
        if (cmp != 0) return cmp;
        return Long.compare(this.id, o.id);
    }

    /**
     * Returns a string representation of the event.
     *
     * @return formatted event string
     */
    @Override
    public String toString() {
        return String.format("Event(t=%.6f, type=%s, server=%s, job=%d, class=%d)",
                time, type, server, jobId, jobClass);
    }

    /**
     * Resets the global event sequence counter to 0.
     * <p>
     * This is useful when restarting a simulation from scratch
     * to ensure deterministic ordering.
     * </p>
     */
    public static void reset() {
        SEQ.set(0);
    }
}
