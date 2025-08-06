import numpy as np
from simulator.event import Event
from lemer.rvms import *

class ArrivalsGenerator:
    def __init__(self, scheduler, rate, target_node="A", job_class=1, 
                 lemer_rng=None):
        if lemer_rng is None:
            raise ValueError("A Lemer RNG instance must be provided.")
        self.lemer_rng = lemer_rng
        self.scheduler = scheduler
        self.rate      = rate
        self.node      = target_node
        self.job_class = job_class
        # subscriber per auto‐reschedulazione
        scheduler.subscribe("ARRIVAL", self._on_arrival)
        # primo arrivo a t=0
        first = Event(time=0.0,
                      event_type="ARRIVAL",
                      server=self.node,
                      job_id=None,
                      job_class=self.job_class)
        scheduler.schedule(first)

    def _on_arrival(self, ev, sched):
        # solo se esterno e al nodo A
        if ev.job_id is None and ev.server == self.node:
            ia = idfExponential(1.0/self.rate, self.lemer_rng.random())
            #print(f"Scheduling next arrival at {sched.current_time + ia:.4f} for node {self.node} with class {self.job_class}")
            nxt = Event(time=sched.current_time + ia,
                        event_type="ARRIVAL",
                        server=self.node,
                        job_id=None,
                        job_class=self.job_class)
            sched.schedule(nxt)
