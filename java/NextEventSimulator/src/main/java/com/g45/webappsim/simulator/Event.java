package com.g45.webappsim.simulator;

import java.util.concurrent.atomic.AtomicLong;

public final class Event implements Comparable<Event> {


    public enum Type { ARRIVAL, DEPARTURE }

    private static final AtomicLong SEQ = new AtomicLong(0);

    private final long id;                 // increasing to break ties
    private double time;
    private final Type type;
    private final String server;           // node name
    private final int jobId;               // -1 means external arrival
    private final int jobClass;            // 1,2,3,...
    private volatile boolean cancelled;    // soft-cancel flag

    public Event(double time, Type type, String server, int jobId, int jobClass) {
        this.id = SEQ.getAndIncrement();
        this.time = time;
        this.type = type;
        this.server = server;
        this.jobId = jobId;
        this.jobClass = jobClass;
        this.cancelled = false;
    }

    public long getId() { return id; }
    public double getTime() { return time; }
    public void setTime(double t) { this.time = t; }
    public Type getType() { return type; }
    public String getServer() { return server; }
    public int getJobId() { return jobId; }
    public int getJobClass() { return jobClass; }
    public boolean isCancelled() { return cancelled; }
    public void cancel() { this.cancelled = true; }

    @Override public int compareTo(Event o) {
        int cmp = Double.compare(this.time, o.time);
        if (cmp != 0) return cmp;
        return Long.compare(this.id, o.id);
    }

    @Override public String toString() {
        return String.format("Event(t=%.6f, type=%s, server=%s, job=%d, class=%d)",
                time, type, server, jobId, jobClass);
    }
    public static void reset() {
        SEQ.set(0);
    }
}