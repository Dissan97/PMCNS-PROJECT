import json

from lemer.rngs import MultiStreamRNG
from lemer.rvms import idfExponential
from simulator.arrivals import ArrivalsGenerator
from simulator.estimators import (
    ResponseTimeEstimator, PopulationEstimator,
    CompletionsEstimator, ObservationTimeEstimator,
    BusytimeEstimator
)
from simulator.event import Event
from simulator.job import Job
from simulator.network import Network
from simulator.scheduler import NextEventScheduler


class Simulation:
    def __init__(self, cfg_path, seed=12345):
        """
        Simulator for the web app workflow
        :param cfg_path: the file configuration path must be json
        :param seed: random seed for Lemer-PRNG
        """

        cfg = json.load(open(cfg_path))
        self.lambda_rate = cfg.get("arrival_rate", 0.5)  # γ
        self.service_rates = {tuple(eval(k)): v for k, v in cfg["service_rates"].items()}
        print("Service rates: ", self.service_rates)
        self.routing_matrix = {tuple(eval(k)): (tuple(eval(v)) if v != "EXIT" else "EXIT")
                               for k, v in cfg["routing_matrix"].items()}
        print("Routing matrix: ", self.routing_matrix)
        self.max_events = cfg.get("max_events", None)

        self.scheduler = NextEventScheduler()
        self.network = Network(self.scheduler,
                               self.routing_matrix,
                               self.service_rates)

        # Poisson arrivals a A
        self.lemer_rng = MultiStreamRNG(seed_val=seed)
        ArrivalsGenerator(self.scheduler, rate=self.lambda_rate, lemer_rng=self.lemer_rng)

        # Global handler
        def _on_arrival(event: Event, scheduler: NextEventScheduler):
            node = self.network.get_node(event.server)
            if event.job_id is None:
                mu = node.service_rates[event.job_class]
                svc = idfExponential(1.0 / mu, self.lemer_rng.random())
                job = Job(event.job_class, event.time, svc)
            else:
                job = scheduler.job_table[event.job_id]
            scheduler.job_table[job.id] = job

            node.arrival(job, scheduler)

        def _on_departure(event: Event, scheduler: NextEventScheduler):
            node = self.network.get_node(event.server)
            job = scheduler.job_table[event.job_id]
            node.departure(job, scheduler)
            nxt = self.routing_matrix[(event.server, event.job_class)]
            if nxt == "EXIT":
                print(f"Job {job.id} with class {job.job_class} completed at time {scheduler.current_time}")
                return
            n_node, n_class = nxt
            job.job_class = n_class
            mu = self.network.get_node(n_node).service_rates[n_class]
            job.remaining_service = idfExponential(1.0 / mu, self.lemer_rng.random())
            next_e = Event(time=scheduler.current_time,
                           event_type="ARRIVAL",
                           server=n_node,
                           job_id=job.id,
                           job_class=n_class)
            # print(f"prev_e : {e} next:e: {next_e}")
            scheduler.schedule(next_e)

        self.scheduler.subscribe("ARRIVAL", _on_arrival)
        self.scheduler.subscribe("DEPARTURE", _on_departure)

        # stimatori
        self.rt = ResponseTimeEstimator(self.scheduler)
        self.pop = PopulationEstimator(self.scheduler)
        self.comp = CompletionsEstimator(self.scheduler)
        self.ot = ObservationTimeEstimator(self.scheduler)
        self.busy = BusytimeEstimator(self.scheduler)

    def run(self):
        cnt = 0
        while self.scheduler.has_next():
            self.scheduler.next()
            cnt += 1
            if self.max_events and cnt >= self.max_events:
                break
        # raccolta metriche
        return {
            "mean_response_time": self.rt.w.get_mean(),
            "std_response_time": self.rt.w.get_stddev(),
            "mean_population": self.pop.w.get_mean(),
            "std_population": self.pop.w.get_stddev(),
            "throughput": self.comp.count / self.ot.elapsed(),
            "utilization": self.busy.get_busy_time() / self.ot.elapsed(),
            "simulation_time": self.ot.elapsed(),
            "events_processed": cnt
        }
