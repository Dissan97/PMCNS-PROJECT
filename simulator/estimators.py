import math

# ───────────────────────────────────────────────────────────────
class WelfordEstimator:
    def __init__(self):
        self.n = 0
        self.mean = 0.0
        self.M2 = 0.0
        self.min = float("inf")
        self.max = float("-inf")

    def add(self, x):
        self.n += 1
        delta = x - self.mean
        self.mean += delta / self.n
        self.M2 += delta * (x - self.mean)
        self.min = min(self.min, x)
        self.max = max(self.max, x)

    def get_mean(self):
        return self.mean if self.n else 0.0

    def get_stddev(self):
        return math.sqrt(self.M2 / (self.n - 1)) if self.n > 1 else 0.0


# ───────────────────────────────────────────────────────────────
#  RESPONSE-TIME ESTIMATOR (sistema intero)
# ───────────────────────────────────────────────────────────────
class ResponseTimeEstimator:                        #  ### MOD
    def __init__(self, sched):
        self.w = WelfordEstimator()
        sched.subscribe("DEPARTURE", self._on_dep)

    def _on_dep(self, e, s):
        job = s.job_table[e.job_id]
        self.w.add(s.current_time - job.arrival_time)


# ───────────────────────────────────────────────────────────────
class PopulationEstimator:
    def __init__(self, sched):
        self.pop = 0
        self.w = WelfordEstimator()
        sched.subscribe("ARRIVAL", self._inc)
        sched.subscribe("DEPARTURE", self._dec)

    def _inc(self, e, s):
        self.pop += 1
        self.w.add(self.pop)

    def _dec(self, e, s):
        self.pop -= 1
        self.w.add(self.pop)


# ───────────────────────────────────────────────────────────────
class CompletionsEstimator:
    def __init__(self, sched, routing_matrix):
        self.count = 0
        self.routing_matrix = routing_matrix
        sched.subscribe("DEPARTURE", self._on_dep)
    """
    differenza da prima è che conta solo quando esce dal sistema e non quando esce da un nodo.
    """
    def _on_dep(self, e, s):
        if self.routing_matrix.get((e.server, e.job_class)) == "EXIT":
            self.count += 1


# ───────────────────────────────────────────────────────────────
class ObservationTimeEstimator:
    def __init__(self, sched):
        self.start = sched.current_time
        self.end = self.start
        sched.subscribe("ARRIVAL", self._upd)
        sched.subscribe("DEPARTURE", self._upd)

    def _upd(self, e, s):
        self.end = s.current_time

    def elapsed(self):
        return self.end - self.start


# ───────────────────────────────────────────────────────────────
class BusytimeEstimator:
    def __init__(self, sched):
        self.pop = 0
        self.busy = False
        self.last = sched.current_time
        self.total = 0.0
        sched.subscribe("ARRIVAL", self._on_arr)
        sched.subscribe("DEPARTURE", self._on_dep)

    def _on_arr(self, e, s):
        if self.pop == 0:
            self.busy = True
            self.last = s.current_time
        self.pop += 1

    def _on_dep(self, e, s):
        self.pop -= 1
        if self.pop == 0 and self.busy:
            self.total += s.current_time - self.last
            self.busy = False

    def get_busy_time(self):
        return self.total

    def finalize(self, current_time):
        if self.pop > 0 and self.busy:
            self.total += current_time - self.last
            self.busy = False


# ───────────────────────────────────────────────────────────────
#  VERSIONI PER SINGOLO NODO
# ───────────────────────────────────────────────────────────────
class _NodeFilterMixin:
    def __init__(self, sched, node, *args, **kwargs):
        self.node = node
        super().__init__(sched, *args, **kwargs)


# ---- Response-time per nodo ------------------------------------
class ResponseTimeEstimatorNode(_NodeFilterMixin, ResponseTimeEstimator):
    def __init__(self, sched, node):
        super().__init__(sched, node)

    """
    adesso si usa arrival_time e current.time del job per calcolare il tempo di risposta.
    DIFFERENZA DA PRIMA:
            at = self.arr.pop(e.job_id, None) dizionario per job_id con i tempi di arrivo
        if at is not None:
            self.w.add(e.time - at) e.time era il tempo dell evento non della partenza effettiva
    """
    def _on_dep(self, e, s):
        if e.server == self.node:
            job = s.job_table[e.job_id]
            self.w.add(s.current_time - job.arrival_time)


# ---- Population per nodo --------------------------------------
class PopulationEstimatorNode(_NodeFilterMixin, PopulationEstimator):
    def _inc(self, e, s):
        if e.server == self.node:
            super()._inc(e, s)

    def _dec(self, e, s):
        if e.server == self.node:
            super()._dec(e, s)


# ---- Completions per nodo -------------------------------------
class CompletionsEstimatorNode(_NodeFilterMixin, CompletionsEstimator):
    def __init__(self, sched, node, routing_matrix):
        super().__init__(sched, node, routing_matrix=routing_matrix)

    def _on_dep(self, e, s):
        if e.server == self.node:
            super()._on_dep(e, s)


# ---- Busy-time per nodo ---------------------------------------
class BusytimeEstimatorNode(_NodeFilterMixin, BusytimeEstimator):
    def _on_arr(self, e, s):
        if e.server == self.node:
            super()._on_arr(e, s)

    def _on_dep(self, e, s):
        if e.server == self.node:
            super()._on_dep(e, s)


# ───────────────────────────────────────────────────────────────
def make_node_estimators(sched, nodes, EstCls, **extra):
    return {n: EstCls(sched, n, **extra) for n in nodes}
