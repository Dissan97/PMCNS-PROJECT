import json, numpy as np
from lemer.rvms import idfExponential
from simulator.scheduler import NextEventScheduler
from simulator.network   import Network
from simulator.arrivals  import ArrivalsGenerator
from simulator.job       import Job
from simulator.event     import Event
from simulator.estimators import (
    ResponseTimeEstimator, PopulationEstimator,
    CompletionsEstimator, ObservationTimeEstimator,
    BusytimeEstimator
)
from lemer.rngs import MultiStreamRNG

class Simulation:
    def __init__(self, cfg_path, seed=12345):
        cfg = json.load(open(cfg_path))
        self.lambda_rate = cfg.get("arrival_rate", 0.5)               # γ
        self.service_rates  = { tuple(eval(k)): v for k,v in cfg["service_rates"].items() }
        print("Service rates: ", self.service_rates)
        self.routing_matrix = { tuple(eval(k)): (tuple(eval(v)) if v!="EXIT" else "EXIT") 
                               for k,v in cfg["routing_matrix"].items() }
        print("Routing matrix: ", self.routing_matrix)
        self.max_events     = cfg.get("max_events", None)

        self.scheduler = NextEventScheduler()
        self.scheduler.job_table = {}
        self.network   = Network(self.scheduler,
                                 self.routing_matrix,
                                 self.service_rates)
        # Poisson arrivals a A
        lemer_rng = MultiStreamRNG(seed_val=seed)
        ArrivalsGenerator(self.scheduler, rate=self.lambda_rate, lemer_rng = lemer_rng)

        # HANDLER GLOBALI (Fig.6)
        def _on_arr(e, s):
            node = self.network.get_node(e.server)
            if e.job_id is None:
                mu  = node.service_rates[e.job_class]
                svc = idfExponential(1.0/mu, lemer_rng.random())
                job = Job(e.job_class, e.time, svc)
            else:
                job = s.job_table[e.job_id]
            s.job_table[job.id] = job
            node.arrival(job, s)

        def _on_dep(e, s):
            node = self.network.get_node(e.server)
            job  = s.job_table[e.job_id]
            node.departure(job, s)
            nxt  = self.routing_matrix[(e.server, e.job_class)]
            if nxt == "EXIT":
                print(f"Job {job.id} with class {job.job_class} completed at time {s.current_time}")
                return
            n_node, n_class = nxt
            job.job_class        = n_class
            mu                   = self.network.get_node(n_node).service_rates[n_class]
            job.remaining_service = idfExponential(1.0/mu, lemer_rng.random())
            next_e = Event(time=s.current_time,
                             event_type="ARRIVAL",
                             server=n_node,
                             job_id=job.id,
                             job_class=n_class)
            #print(f"prev_e : {e} next:e: {next_e}")
            s.schedule(next_e)

        self.scheduler.subscribe("ARRIVAL",   _on_arr)
        self.scheduler.subscribe("DEPARTURE", _on_dep)

        # stimatori
        self.rt   = ResponseTimeEstimator(self.scheduler)
        self.pop  = PopulationEstimator(self.scheduler)
        self.comp = CompletionsEstimator(self.scheduler)
        self.ot   = ObservationTimeEstimator(self.scheduler)
        self.busy = BusytimeEstimator(self.scheduler)

    def run(self):
        cnt=0
        while self.scheduler.has_next():
            self.scheduler.next()
            cnt+=1
            if self.max_events and cnt>=self.max_events:
                break
        # raccolta metriche
        return {
            "mean_response_time": self.rt.w.get_mean(),
            "std_response_time":  self.rt.w.get_stddev(),
            "mean_population":    self.pop.w.get_mean(),
            "std_population":     self.pop.w.get_stddev(),
            "throughput":         self.comp.count / self.ot.elapsed(),
            "utilization":        self.busy.get_busy_time() / self.ot.elapsed(),
            "simulation_time":    self.ot.elapsed(),
            "events_processed":   cnt
        }
