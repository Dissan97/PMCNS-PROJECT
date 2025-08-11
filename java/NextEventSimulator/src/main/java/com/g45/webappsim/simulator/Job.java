package com.g45.webappsim.simulator;

import java.util.concurrent.atomic.AtomicInteger;

public final class Job {
    private static final AtomicInteger SEQ = new AtomicInteger(0);

    private final int id;
    private int jobClass;               // mutable for class switch
    private final double arrivalTime;
    private double remainingService;

    public Job(int jobClass, double arrivalTime, double serviceTime) {
        this.id = SEQ.getAndIncrement();
        this.jobClass = jobClass;
        this.arrivalTime = arrivalTime;
        this.remainingService = serviceTime;
    }



    public int getId() {
        return id;
    }

    public int getJobClass() {
        return jobClass;
    }

    public void setJobClass(int c) {
        this.jobClass = c;
    }

    public double getArrivalTime() {
        return arrivalTime;
    }

    public double getRemainingService() {
        return remainingService;
    }

    public void setRemainingService(double v) {
        this.remainingService = v;
    }

    @Override
    public String toString() {
        return String.format("Job(id=%d, class=%d, arr=%.6f, rem=%.6f)", id, jobClass, arrivalTime, remainingService);
    }
    public static void reset() {
        SEQ.set(0);
    }
}
