import math


class WelfordEstimator:
    def __init__(self):
        self.n, self.mean, self.M2 = 0, 0.0, 0.0
        self.min, self.max = float('inf'), float('-inf')

    def add(self, x):
        self.n += 1
        delta = x - self.mean
        self.mean += delta / self.n
        self.M2 += delta * (x - self.mean)
        self.min = min(self.min, x)
        self.max = max(self.max, x)

    def get_mean(self):   return self.mean if self.n else 0.0

    def get_stddev(self): return math.sqrt(self.M2 / (self.n - 1)) if self.n > 1 else 0.0


class ResponseTimeEstimator:
    def __init__(self, sched):
        self.arr = {}
        self.w = WelfordEstimator()
        sched.subscribe("ARRIVAL", self._on_arr)
        sched.subscribe("DEPARTURE", self._on_dep)

    def _on_arr(self, e, s):
        if e.job_id is not None:
            self.arr[e.job_id] = e.time

    def _on_dep(self, e, s):
        at = self.arr.pop(e.job_id, None)
        if at is not None:
            self.w.add(e.time - at)


class PopulationEstimator:
    """
    Media della popolazione *tempo-pesata* a livello di SISTEMA.
    Regole:
      - integra l'area PRIMA di cambiare N
      - N++ solo su ARRIVAL esterno (e.job_id is None)
      - N-- solo su DEPARTURE che instrada a EXIT
    """
    def __init__(self, sched, routing_matrix):
        self.pop = 0
        self.start_time = sched.current_time
        self.last_time = self.start_time
        self.area = 0.0
        self.min = 0
        self.max = 0
        self.routing_matrix = routing_matrix

        sched.subscribe("ARRIVAL",   self._tick_then_maybe_inc)
        sched.subscribe("DEPARTURE", self._tick_then_maybe_dec)

    def _tick(self, s):
        """Aggiorna l'area fino all'istante corrente, senza cambiare pop."""
        now = s.current_time
        dt = now - self.last_time
        if dt > 0:
            self.area += self.pop * dt
            self.last_time = now

    def _tick_then_maybe_inc(self, e, s):
        self._tick(s)
        # ARRIVO ESTERNO: nel tuo simulatore ha job_id == None
        if e.job_id is None:
            self.pop += 1
            if self.pop > self.max: self.max = self.pop

    def _tick_then_maybe_dec(self, e, s):
        self._tick(s)
        # DEPARTURE → decrementa solo se la prossima tappa è EXIT
        if self.routing_matrix.get((e.server, e.job_class)) == "EXIT":
            self.pop -= 1
            if self.pop < self.min: self.min = self.pop

    def get_mean(self):
        """Media temporale N = area / (last_time - start_time)."""
        elapsed = self.last_time - self.start_time
        return (self.area / elapsed) if elapsed > 0.0 else 0.0


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


class ObservationTimeEstimator:
    def __init__(self, sched):
        self.start = sched.current_time
        self.end = self.start
        sched.subscribe("ARRIVAL", self._upd)
        sched.subscribe("DEPARTURE", self._upd)

    def _upd(self, e, s):
        self.end = s.current_time

    def elapsed(self): return self.end - self.start

class BusyTimeEstimator:
    def __init__(self, sched, routing_matrix):
        self.pop = 0                 # popolazione di SISTEMA
        self.busy = False
        self.last = sched.current_time
        self.total = 0.0
        self.routing_matrix = routing_matrix
        sched.subscribe("ARRIVAL", self._on_arr)
        sched.subscribe("DEPARTURE", self._on_dep)

    def _on_arr(self, e, s):
        # incremento solo su ARRIVAL esterno
        if e.job_id is None:
            if self.pop == 0:
                self.busy = True
                self.last = s.current_time
            self.pop += 1

    def _on_dep(self, e, s):
        # decremento solo su EXIT
        if self.routing_matrix.get((e.server, e.job_class)) == "EXIT":
            self.pop -= 1
            if self.pop == 0 and self.busy:
                self.total += s.current_time - self.last
                self.busy = False



# ────────────────────────────────────────────────────────────────
# Versioni filtrate per un singolo nodo
class _NodeFilterMixin:
    def __init__(self, sched, node, *args, **kwargs):
        self.node = node
        super().__init__(sched, *args, **kwargs)


class ResponseTimeEstimatorNode(_NodeFilterMixin, ResponseTimeEstimator):
    def _on_arr(self, e, s):
        if e.server == self.node and e.job_id is not None:
            self.arr[e.job_id] = e.time

    def _on_dep(self, e, s):
        if e.server == self.node:
            super()._on_dep(e, s)


class PopulationEstimatorNode(_NodeFilterMixin, PopulationEstimator):
    """
    Media temporale della popolazione *del singolo nodo*.
    Nota: l'area va aggiornata ad ogni evento di sistema (tick),
    ma incrementiamo/decrementiamo SOLO se l'evento riguarda quel nodo.
    """
    def __init__(self, sched, node, *args, **kwargs):
        super().__init__(sched, *args, **kwargs)
        self.node = node
        # sovrascrivo le subscribe per avere il tick sempre e il +/- filtrato
        # rimuovere le vecchie subscribe non è necessario nel tuo scheduler,
        # quindi aggiungiamo handler dedicati:
        sched.subscribe("ARRIVAL", self._node_tick_then_inc)
        sched.subscribe("DEPARTURE", self._node_tick_then_dec)

    def _node_tick_then_inc(self, e, s):
        # aggiorna area sempre…
        self._tick(s)
        # …ma incrementa solo se l’ARRIVAL è su questo nodo
        if e.server == self.node:
            self.pop += 1
            if self.pop > self.max: self.max = self.pop

    def _node_tick_then_dec(self, e, s):
        # aggiorna area sempre…
        self._tick(s)
        # …ma decrementa solo se il DEPARTURE è su questo nodo
        if e.server == self.node:
            self.pop -= 1
            if self.pop < self.min: self.min = self.pop



class CompletionsEstimatorNode(_NodeFilterMixin, CompletionsEstimator):
    def _on_dep(self, e, s):
        if e.server == self.node: super()._on_dep(e, s)


class BusyTimeEstimatorNode(_NodeFilterMixin, BusyTimeEstimator):
    def _on_arr(self, e, s):
        if e.server == self.node: super()._on_arr(e, s)

    def _on_dep(self, e, s):
        if e.server == self.node: super()._on_dep(e, s)


# helper per creare gli stimatori per tutti i nodi
def make_node_estimators(sched, nodes, EstCls, **extra) :
    return {n: EstCls(sched, n, **extra) for n in nodes}
