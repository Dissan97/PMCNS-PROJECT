from lemer.rngs import MultiStreamRNG
from lemer.rvms import idfExponential
from simulator.event import Event
from simulator.scheduler import NextEventScheduler


class ArrivalsGenerator:
    def __init__(self, scheduler: NextEventScheduler, rate, target_node="A", job_class=1,
                 lemer_rng=None):
        """
            Arrivals Generator
            :param scheduler: scheduler object next event scheduler
            :param rate: arrival rate for the jobs
            :param target_node: node to start arriving from
            :param job_class: job class must be class 1
            :param lemer_rng: lemer pseudo random number generator
        """
        if lemer_rng is None:
            raise ValueError("A Lemer RNG instance must be provided.")
        self.lemer_rng: MultiStreamRNG = lemer_rng
        self.scheduler = scheduler
        self.rate = rate
        self.node = target_node
        self.job_class = job_class
        self.active = True
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

        if self.active and ev.job_id is None and ev.server == self.node:
            ia = idfExponential(1.0 / self.rate, self.lemer_rng.random())
            # print(f"Scheduling next arrival at {sched.current_time + ia:.4f} for node {self.node} with class {self.job_class}")
            nxt = Event(time=sched.current_time + ia,
                        event_type="ARRIVAL",
                        server=self.node,
                        job_id=None,
                        job_class=self.job_class)
            sched.schedule(nxt)

