from lemer.rngs import MultiStreamRNG
from lemer.rvms import idfExponential
from simulator.arrivals import ArrivalsGenerator
from simulator.estimators import (
    ResponseTimeEstimator, PopulationEstimator,
    CompletionsEstimator, ObservationTimeEstimator,
    BusyTimeEstimator
)
from simulator.event import Event
from simulator.job import Job
from simulator.network import Network
from simulator.scheduler import NextEventScheduler


class Simulation:
    def __init__(self, cfg: dict, seed=12345):
        """
        Simulator for the web app workflow
        :param cfg: the file configuration json
        :param seed: random seed for Lemer-PRNG
        """

        self.lambda_rate = cfg.get("arrival_rate", 0.5)  # γ
        self.service_rates = {tuple(eval(k)): v for k, v in cfg["service_rates"].items()}
        # print("Service rates: ", self.service_rates)
        self.routing_matrix = {tuple(eval(k)): (tuple(eval(v)) if v != "EXIT" else "EXIT")
                               for k, v in cfg["routing_matrix"].items()}
        # print("Routing matrix: ", self.routing_matrix)
        self.max_events = cfg.get("max_events", None)

        self.scheduler: NextEventScheduler = NextEventScheduler()
        self.network = Network(self.scheduler,
                               self.routing_matrix,
                               self.service_rates)

        # Poisson arrivals a A
        self.lemer_rng = MultiStreamRNG(seed_val=seed)
        self.arrival_generator = ArrivalsGenerator(self.scheduler, rate=self.lambda_rate, lemer_rng=self.lemer_rng)

        # Global handler
        """
        i due idf presenti in on arrival e on_departure non sono ridondanti
        perché in on arrival viene calcolato il tempo di servizio medio per il job che arriva ovvero anche per i job
        che arrivano dall'esterno mentre in on_departure viene calcolato il tempo di servizio medio per il job che
        sta partendo ovvero per i job che sono già nel sistema
        """

        def _on_arrival(event: Event, scheduler: NextEventScheduler):
            node = self.network.get_node(event.server)
            if event.job_id is None:
                """
                DIFFERENCE:
                mu = node.service_rates[event.job_class]
                svc = idfExponential(1.0/mu, self.lemer_rng.random())
                veniva usato come mu ma in realta si tratta di E(s) quindi si tratta gia di media del tempo di servizio
                """
                mean_service_time = node.service_rates[event.job_class]
                svc = idfExponential(mean_service_time, self.lemer_rng.random())
                job = Job(event.job_class, event.time, svc)
                self.total_external_arrivals += 1
            else:
                job = scheduler.job_table[event.job_id]
            scheduler.job_table[job.id] = job

            node.arrival(job, scheduler)

        def _on_departure(event: Event, scheduler: NextEventScheduler):
            node = self.network.get_node(event.server)
            job = scheduler.job_table[event.job_id]
            # se il nodo non ha il job in coda allora non deve essere fatto il departure
            if job not in node.jobs:
                return
            node.departure(job, scheduler)
            nxt = self.routing_matrix[(event.server, event.job_class)]
            if nxt == "EXIT":
                # print(f"Job {job.id} with class {job.job_class} completed at time {scheduler.current_time}")
                self.total_completed_jobs += 1
                return
            n_node, n_class = nxt
            job.job_class = n_class
            # stesso cambio fatto in _on_arrival
            mean_service_time = self.network.get_node(n_node).service_rates[n_class]
            job.remaining_service = idfExponential(mean_service_time, self.lemer_rng.random())
            next_e = Event(time=scheduler.current_time,
                           event_type="ARRIVAL",
                           server=n_node,
                           job_id=job.id,
                           job_class=n_class)
            # print(f"prev_e : {e} next:e: {next_e}")
            scheduler.schedule(next_e)

        self.scheduler.subscribe("ARRIVAL", _on_arrival)
        self.scheduler.subscribe("DEPARTURE", _on_departure)
        # job counters
        self.total_external_arrivals = 0
        self.total_completed_jobs = 0
        # stimatori
        self.rt = ResponseTimeEstimator(self.scheduler)
        self.pop = PopulationEstimator(self.scheduler)
        self.comp = CompletionsEstimator(self.scheduler, self.routing_matrix)
        self.ot = ObservationTimeEstimator(self.scheduler)
        self.busy = BusyTimeEstimator(self.scheduler)
        # ── stimatori PER NODO ─────────────────────────────
        nodes = list(self.network.nodes.keys())
        from simulator.estimators import (
            ResponseTimeEstimatorNode, PopulationEstimatorNode,
            CompletionsEstimatorNode, BusyTimeEstimatorNode,
            make_node_estimators)
        self.rt_n = make_node_estimators(self.scheduler, nodes, ResponseTimeEstimatorNode)
        self.pop_n = make_node_estimators(self.scheduler, nodes, PopulationEstimatorNode)
        self.comp_n = make_node_estimators(
            self.scheduler,  # sched
            nodes,  # iterable di nodi
            CompletionsEstimatorNode,  # classe da istanziare
            routing_matrix=self.routing_matrix  # <── parametro obbligatorio
        )

        self.busy_n = make_node_estimators(self.scheduler, nodes, BusyTimeEstimatorNode)


    def run(self):
        cnt = 0
        while self.scheduler.has_next():
            self.scheduler.next()
            cnt += 1
            if self.max_events and cnt >= self.max_events:
                self.arrival_generator.active = False

        # raccolta metriche
        # chiudo eventuale periodo busy aperto
        self.busy.finalize(self.scheduler.current_time)
        for b in self.busy_n.values():
            b.finalize(self.scheduler.current_time)

        return self._collect()

    def _collect(self):
        overall = {
            "mean_response_time": self.calculate_overall_rt(),
            "std_response_time": self.rt.w.get_stddev(),
            "mean_population": self.pop.w.get_mean(),
            "std_population": self.pop.w.get_stddev(),
            "throughput": self.comp.count / self.ot.elapsed(),
            "utilization": self.busy.get_busy_time() / self.ot.elapsed(),
            "total_external_arrivals": self.total_external_arrivals,
            "total_completed_jobs": self.total_completed_jobs
        }

        per_node = {}
        for n in self.network.nodes:
            rt = self.rt_n[n].w
            pop = self.pop_n[n].w
            comp = self.comp_n[n]
            busy = self.busy_n[n]
            per_node[n] = {
                "mean_response_time": rt.get_mean(),
                "std_response_time": rt.get_stddev(),
                "mean_population": pop.get_mean(),
                "std_population": pop.get_stddev(),
                "throughput": comp.count / self.ot.elapsed(),
                "utilization": busy.get_busy_time() / self.ot.elapsed(),
            }
        return overall, per_node

    def calculate_overall_rt(self):
        return self.rt_n['A'].w.mean * 3 + self.rt_n['B'].w.mean + self.rt_n['P'].w.mean
