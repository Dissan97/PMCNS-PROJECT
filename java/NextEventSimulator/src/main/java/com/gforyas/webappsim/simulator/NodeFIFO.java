package com.gforyas.webappsim.simulator;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;

public class NodeFIFO extends Node {

    // Coda FIFO di ID di job
    private final Queue<Integer> queue = new ArrayDeque<>();

    // Stato server e job corrente
    private boolean busy = false;
    private Integer currentJobId = null;

    public NodeFIFO(String name, Map<Integer, Double> serviceMeans) {
        super(name, serviceMeans);
    }

    @Override
    public void arrival(Job job, NextEventScheduler scheduler) {
        if (!busy) {
            startService(scheduler, job);
        } else {
            queue.add(job.getId());
        }
    }

    @Override
    public void departure(Job job, NextEventScheduler scheduler) {
        // Completa il job in servizio (se combacia)
        if (currentJobId != null && currentJobId == job.getId()) {
            busy = false;
            currentJobId = null;
        }

        // Se c'è coda, fai partire il prossimo
        if (!queue.isEmpty()) {
            int nextJobId = queue.remove();
            Job next = scheduler.getJobTable().get(nextJobId);
            if (next != null) {
                startService(scheduler, next);
            }
        }
    }

    private void startService(NextEventScheduler scheduler, Job job) {
        busy = true;
        currentJobId = job.getId();

        // In questo modello il remainingService è già impostato da Simulation
        double svc = Math.max(0.0, job.getRemainingService());
        double abs = scheduler.getCurrentTime() + svc;

        // Schedula DEPARTURE a tempo assoluto
        Event completion = new Event(
                abs,
                Event.Type.DEPARTURE,
                getName(),                 // usa getter, non il campo 'name'
                job.getId(),
                job.getJobClass()
        );
        scheduler.scheduleAt(completion, abs);
    }
}
