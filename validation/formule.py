# validation/formulas.py
# --------------------------------------------------------------
#  Rete A-B-A-P-A (Processor-Sharing) – metriche teoriche
# --------------------------------------------------------------

import json
import math
from pathlib import Path
from typing import Dict, Any


# ───────────────────────────────────────────────────────────────
def load_cfg(path: Path) -> Dict[str, Any]:
    """Carica il file JSON di configurazione."""
    with open(path, encoding="utf-8") as f:
        return json.load(f)


# ───────────────────────────────────────────────────────────────
def mm1_ps_mean_response(lam: float, msr: float) -> float:
    """E[T] per un M/M/1-PS.  Ritorna inf se ρ≥1 (instabile)."""
    rho = lam * msr
    return math.inf if rho >= 1 else (msr / ((1 - rho)))


# ───────────────────────────────────────────────────────────────
def analytic_metrics(cfg: dict, gamma: float) -> dict:
    """
    Metriche teoriche globali per il carico esterno γ.
    Visite: A1-B-A2-P-A3 con rate diversi.
    """
    # ----- estrai i service rate μ -------------------------------
    rates = {tuple(eval(k)): v for k, v in cfg["service_rates"].items()}

    msr_A1 = rates[("A", 1)]
    msr_A2 = rates[("A", 2)]
    msr_A3 = rates[("A", 3)]
    msr_B  = rates[("B", 1)]
    msr_P  = rates[("P", 2)]

    lam = gamma                     # ogni visita riceve lo stesso flusso

    # ----- tempi di risposta di visita ---------------------------
    W_A1 = mm1_ps_mean_response(lam, msr_A1)
    W_B  = mm1_ps_mean_response(lam, msr_B)
    W_A2 = mm1_ps_mean_response(lam, msr_A2)
    W_P  = mm1_ps_mean_response(lam, msr_P)
    W_A3 = mm1_ps_mean_response(lam, msr_A3)

    # ----- metriche globali --------------------------------------
    W_sys = W_A1 + W_B + W_A2 + W_P + W_A3
    N_sys = gamma * W_sys

    # utilizzo di ciascun nodo
    rho_A1 = lam * msr_A1
    rho_A2 = lam * msr_A2
    rho_A3 = lam * msr_A3
    rho_B  = lam * msr_B
    rho_P  = lam * msr_P

    # Probabilità che il sistema NON sia vuoto
    util_components = [rho_A1, rho_A2, rho_A3, rho_B, rho_P]
    if all(rho < 1 for rho in util_components):
        prod_idle = math.prod(1 - rho for rho in util_components)
        U_sys = 1.0 - prod_idle          # P{almeno un nodo busy}
    else:
        U_sys = None                     # rete instabile → ignora nel confronto

    return {
        "mean_response_time": W_sys,
        "std_response_time":  None,
        "mean_population":    N_sys,
        "std_population":     None,
        "throughput":         gamma,     # un job completato per arrivo
        "utilization":        U_sys,     # None se instabile
    }
