package com.gforyas.webappsim.simulator;

public class BootstrapEvent extends Event{
    /**
     * Creates a new simulation event.
     *
     * @param time     The simulation time at which the event occurs.
     * @param type     The event type (arrival or departure).
     * @param server   The server (node) name the event is associated with.
     * @param jobId    The ID of the job associated with the event (-1 for external arrivals).
     * @param jobClass The class of the job.
     */
    public BootstrapEvent(double time, Type type, String server, int jobId, int jobClass) {
        super(time, type, server, jobId, jobClass);
    }
}
