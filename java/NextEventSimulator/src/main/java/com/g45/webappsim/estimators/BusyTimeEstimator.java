package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Event;
import com.g45.webappsim.simulator.NextEventScheduler;

public class BusyTimeEstimator {

    protected int pop = 0;
    protected boolean busy = false;
    protected double last;
    protected double total = 0.0;

    public BusyTimeEstimator(NextEventScheduler sched) {
        this.last = sched.getCurrentTime();
        sched.subscribe(Event.Type.ARRIVAL, this::onArrival);
        sched.subscribe(Event.Type.DEPARTURE, this::onDeparture);
    }

    protected void onArrival(Event e, NextEventScheduler s) {
        if (pop == 0) {
            busy = true;
            last = s.getCurrentTime();
        }
        pop += 1;
    }

    protected void onDeparture(Event e, NextEventScheduler s) {
        pop -= 1;
        if (pop == 0 && busy) {
            total += s.getCurrentTime() - last;
            busy = false;
        }
    }

    public double getBusyTime() {
        return total;
    }

    public void finalizeBusy(double currentTime) {
        if (pop > 0 && busy) {
            total += currentTime - last;
            busy = false;
        }
    }
}
