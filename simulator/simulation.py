from __future__ import annotations

import ast
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
    """
    Simulatore ad eventi discreti per il workflow A-B-A-P-A (Processor-Sharing su ciascun nodo).

    Scelte:
      - I valori in cfg["service_rates"] sono TEMPI MEDI di servizio (secondi) per (nodo, classe).
      - Dopo un DEPARTURE, l’ARRIVAL al nodo successivo è schedulato con un piccolo epsilon
        per evitare sovrapposizioni allo stesso timestamp (bias sulla popolazione).
      - Coerenza totale con Little: popolazioni derivate sempre come N = X * W.
        Per i nodi si moltiplica per il numero medio di visite (A=3, B=1, P=1).
    """

    # piccolo epsilon per ordinare eventi con lo stesso timestamp (evita overlap tra nodi)
    _EPS = 1e-12

    def __init__(self, cfg: dict, seed: int = 12345):
        """
        :param cfg: dizionario di configurazione caricato dal JSON
        :param seed: seed per il PRNG Lehmer (MultiStreamRNG)
        """

        # Tasso di arrivo esterno γ
        self.lambda_rate = cfg.get("arrival_rate", 0.5)

        # Tempi medi di servizio (secondi) per (nodo, classe); parsing chiavi in sicurezza
        raw_sr = cfg["service_rates"]
        self.service_rates = {tuple(ast.literal_eval(k)): float(v) for k, v in raw_sr.items()}

        # Matrice di instradamento: { (nodo, classe) -> (nodo_successivo, classe_successiva) | "EXIT" }
        raw_rm = cfg["routing_matrix"]
        routing: dict[tuple, tuple | str] = {}
        for k, v in raw_rm.items():
            key = tuple(ast.literal_eval(k))
            if v == "EXIT":
                routing[key] = "EXIT"
            else:
                routing[key] = tuple(ast.literal_eval(v))
        self.routing_matrix = routing

        # (Opzionale) limite di eventi processati prima di spegnere gli arrivi; il sistema poi drena
        self.max_events = cfg.get("max_events", None)

        # Oggetti principali
        self.scheduler: NextEventScheduler = NextEventScheduler()
        self.network = Network(self.scheduler, self.routing_matrix, self.service_rates)

        # Arrivi di Poisson al nodo A (sorgente esterna)
        self.lemer_rng = MultiStreamRNG(seed_val=seed)
        self.arrival_generator = ArrivalsGenerator(
            self.scheduler, rate=self.lambda_rate, lemer_rng=self.lemer_rng
        )

        # ------------------ Gestori globali degli eventi ------------------

        def _on_arrival(event: Event, scheduler: NextEventScheduler):
            """
            ARRIVAL su un nodo. Se event.job_id è None, è un arrivo esterno:
            crea un nuovo Job con tempo di servizio ~ Exp(mean) (mean in secondi dal cfg).
            """
            node = self.network.get_node(event.server)

            if event.job_id is None:
                mean_service_time = node.service_rates[event.job_class]
                svc = idfExponential(mean_service_time, self.lemer_rng.random())
                job = Job(event.job_class, event.time, svc)
                self.total_external_arrivals += 1
            else:
                # Hop interno: recupera il job esistente
                job = scheduler.job_table[event.job_id]

            scheduler.job_table[job.id] = job
            node.arrival(job, scheduler)

        def _on_departure(event: Event, scheduler: NextEventScheduler):
            """
            DEPARTURE da un nodo. Se il flusso continua, schedula un ARRIVAL
            sul nodo successivo a time = current_time + EPS per evitare overlap.
            """
            node = self.network.get_node(event.server)
            job = scheduler.job_table[event.job_id]

            # Se il job non è presente nel nodo, esci (difensivo)
            if job not in node.jobs:
                return

            node.departure(job, scheduler)

            nxt = self.routing_matrix[(event.server, event.job_class)]
            if nxt == "EXIT":
                self.total_completed_jobs += 1
                return

            # Prosegui verso nodo/classe successivi
            n_node, n_class = nxt
            job.job_class = n_class

            # Campiona un nuovo tempo di servizio per la prossima visita (usa la media configurata)
            mean_service_time = self.network.get_node(n_node).service_rates[n_class]
            job.remaining_service = idfExponential(mean_service_time, self.lemer_rng.random())

            # IMPORTANTE: aggiungi epsilon per non contare il job su due nodi nello stesso intervallo
            next_e = Event(
                time=scheduler.current_time + self._EPS,
                event_type="ARRIVAL",
                server=n_node,
                job_id=job.id,
                job_class=n_class,
            )
            scheduler.schedule(next_e)

        # Subscribe dei gestori
        self.scheduler.subscribe("ARRIVAL", _on_arrival)
        self.scheduler.subscribe("DEPARTURE", _on_departure)

        # Contatori
        self.total_external_arrivals = 0
        self.total_completed_jobs = 0

        # Stimatori (di sistema)
        self.rt = ResponseTimeEstimator(self.scheduler)        # tempi di risposta (event-based, per visita/nodo)
        self.pop = PopulationEstimator(self.scheduler,self.routing_matrix)         # opzionale/diagnostica, non usato per N
        self.comp = CompletionsEstimator(self.scheduler, self.routing_matrix)
        self.ot = ObservationTimeEstimator(self.scheduler)
        self.busy = BusyTimeEstimator(self.scheduler,self.routing_matrix)          # tempo in cui il sistema non è vuoto

        # Stimatori per nodo
        nodes = list(self.network.nodes.keys())
        from simulator.estimators import (
            ResponseTimeEstimatorNode, PopulationEstimatorNode,
            CompletionsEstimatorNode, BusyTimeEstimatorNode,
            make_node_estimators
        )
        self.rt_n = make_node_estimators(self.scheduler, nodes, ResponseTimeEstimatorNode)
        self.pop_n = make_node_estimators(self.scheduler, nodes, PopulationEstimatorNode)  # solo diagnostica
        self.comp_n = make_node_estimators(
            self.scheduler, nodes, CompletionsEstimatorNode, routing_matrix=self.routing_matrix
        )
        self.busy_n = make_node_estimators(self.scheduler, nodes, BusyTimeEstimatorNode)

    # -------------------------------------------------------------------------

    def run(self):
        """
        Esegue finché lo scheduler non è vuoto. Se max_events è impostato, dopo quel numero
        di eventi spegne *gli arrivi esterni* ma lascia drenare la rete prima di raccogliere le metriche.
        """
        cnt = 0
        while self.scheduler.has_next():
            self.scheduler.next()
            cnt += 1
            if self.max_events and cnt >= self.max_events:
                # Ferma gli arrivi esterni; la rete continuerà a drenare
                self.arrival_generator.active = False

        # Chiude eventuali periodi busy aperti PRIMA della raccolta finale
        self.busy.finalize(self.scheduler.current_time)
        for b in self.busy_n.values():
            b.finalize(self.scheduler.current_time)

        return self._collect()

    # -------------------------------------------------------------------------

    def _collect(self):
        """
        Raccoglie le metriche di sistema e per nodo.
        Coerenza totale: N = X * W. Per i nodi si applica il numero di visite.
        """
        elapsed = self.ot.elapsed()
        X = self.comp.count / elapsed if elapsed > 0.0 else 0.0

        # Tempi medi di risposta per nodo (misurati dagli estimator per-nodo)
        W_A = self.rt_n['A'].w.get_mean()
        W_B = self.rt_n['B'].w.get_mean()
        W_P = self.rt_n['P'].w.get_mean()

        # Tempo medio di risposta di sistema via somma visite: A tre volte
        W_sys = (3.0 * W_A) + W_B + W_P

        # Popolazioni via Little (derivate): N = X * W
        N_sys = X * W_sys
        N_A   = X * (3.0 * W_A)  # 3 visite su A
        N_B   = X * W_B
        N_P   = X * W_P

        overall = {
            "mean_response_time": W_sys,
            "std_response_time": self.rt.w.get_stddev(),  # dev.std dei tempi a livello sistema (se aggregata)
            "mean_population": N_sys,
            "std_population": 0.0,                        # N derivata: std non stimata
            "throughput": X,
            "utilization": self.busy.get_busy_time() / elapsed if elapsed > 0.0 else 0.0,
            "total_external_arrivals": self.total_external_arrivals,
            "total_completed_jobs": self.total_completed_jobs,
        }

        # Per-nodo: throughput per nodo = X * visite_nodo; popolazione = X_nodo * W_nodo
        per_node = {
            'A': {
                "mean_response_time": W_A,
                "std_response_time": self.rt_n['A'].w.get_stddev(),
                "mean_population": N_A,
                "std_population": 0.0,
                "throughput": 3.0 * X,  # tre visite ad A
                "utilization": self.busy_n['A'].get_busy_time() / elapsed if elapsed > 0.0 else 0.0,
            },
            'B': {
                "mean_response_time": W_B,
                "std_response_time": self.rt_n['B'].w.get_stddev(),
                "mean_population": N_B,
                "std_population": 0.0,
                "throughput": X,
                "utilization": self.busy_n['B'].get_busy_time() / elapsed if elapsed > 0.0 else 0.0,
            },
            'P': {
                "mean_response_time": W_P,
                "std_response_time": self.rt_n['P'].w.get_stddev(),
                "mean_population": N_P,
                "std_population": 0.0,
                "throughput": X,
                "utilization": self.busy_n['P'].get_busy_time() / elapsed if elapsed > 0.0 else 0.0,
            }
        }

        return overall, per_node

    # -------------------------------------------------------------------------

    def calculate_overall_rt(self) -> float:
        """
        Helper: somma dei tempi medi per nodo/visita (A tre volte).
        Utile per confronto o diagnostica.
        """
        return (
            self.rt_n['A'].w.mean * 3
            + self.rt_n['B'].w.mean
            + self.rt_n['P'].w.mean
        )
