# validation/formulas.py
# --------------------------------------------------------------
#  Rete A-B-A-P-A (Processor-Sharing) – metriche teoriche
#  Versione finale (aggregazione per nodo fisico, conforme ai paper)
# --------------------------------------------------------------

import json
import math
import ast
from pathlib import Path
from typing import Dict, Any


# ───────────────────────────────────────────────────────────────
def load_cfg(path: Path) -> Dict[str, Any]:
    """Carica il file JSON di configurazione."""
    with open(path, encoding="utf-8") as f:
        return json.load(f)


# ───────────────────────────────────────────────────────────────
def mm1_ps_mean_response(lam: float, mst: float) -> float:
    """
    Tempo medio di risposta E[T] per un M/M/1-PS dato:
      - lam: tasso di arrivo λ [job/s]
      - mst: tempo medio di servizio E[S] [s]
    Ritorna math.inf se ρ ≥ 1 (instabile).
    NOTA: In questa versione "finale" non viene usata direttamente,
          perché lavoriamo su nodi fisici con domanda di servizio aggregata.
    """
    rho = lam * mst
    return math.inf if rho >= 1.0 else (mst / (1.0 - rho))


# ───────────────────────────────────────────────────────────────
def _parse_service_times(cfg: dict) -> Dict[tuple, float]:
    """
    Converte la mappa dei tempi medi di servizio per *visita* in:
      { ('A',1): E[S_A1], ('A',2): E[S_A2], ('A',3): E[S_A3], ('B',1): E[S_B], ('P',2): E[S_P] }
    Il JSON può usare la chiave 'service_means_sec' (consigliata) oppure 'service_rates' (storica).
    I valori SONO TEMPI MEDI in secondi (E[S]), non tassi.
    """
    raw = cfg.get("service_means_sec", cfg.get("service_rates"))
    if raw is None:
        raise KeyError("Manca 'service_means_sec' (o 'service_rates') nel cfg.")

    # Le chiavi arrivano come stringhe tipo "['A', 1]": usiamo ast.literal_eval per sicurezza
    out: Dict[tuple, float] = {}
    for k_str, v in raw.items():
        key = ast.literal_eval(k_str)         # es. "['A', 1]" -> ['A', 1]
        if isinstance(key, list):
            key = tuple(key)                  # -> ('A', 1)
        out[tuple(key)] = float(v)
    return out


# ───────────────────────────────────────────────────────────────
def analytic_metrics(cfg: dict, gamma: float) -> dict:
    """
    Theoretical metrics for the A-B-A-P-A web app under Processor-Sharing (PS).
    Physical-node aggregation: the three visits to A are served by the same physical node.

    Assumptions for std:
      - M/M/1-PS at each physical node (exponential service).
      - Poisson external arrivals.
      - Nodes are treated as independent for variance aggregation.
      => For each node k: E[W_k] = D_k / (1 - rho_k) and std[W_k] = E[W_k].
         System std is sqrt(sum_k Var[W_k]).

    Returns:
      - stable: True/False
      - throughput: X if stable, else X_max (capacity bound)
      - mean_response_time, std_response_time
      - mean_population (= X * E[T]), std_population (= X * std_T)
      - utilization (scalar system-busy prob) and per-node utilizations
      - system_busy_prob (alias of utilization)
      - X_max and bottleneck
    """
    # ---- 1) Service times per visit (seconds) ------------------------------------
    S = _parse_service_times(cfg)

    # ---- 2) Aggregate service demand per physical node ---------------------------
    D_A = S[("A", 1)] + S[("A", 2)] + S[("A", 3)]
    D_B = S[("B", 1)]
    D_P = S[("P", 2)]

    # ---- 3) External throughput and utilizations --------------------------------
    X = float(gamma)
    rho_A = X * D_A
    rho_B = X * D_B
    rho_P = X * D_P
    U_sys = 1.0 - (1.0 - rho_A) * (1.0 - rho_B) * (1.0 - rho_P)

    # Capacity bound and bottleneck (useful also if unstable)
    X_max = 1.0 / max(D_A, D_B, D_P)
    bottleneck = max((("A", D_A), ("B", D_B), ("P", D_P)), key=lambda t: t[1])[0]

    # ---- 4) Stability check ------------------------------------------------------
    if any(r >= 1.0 for r in (rho_A, rho_B, rho_P)):
        # Unstable network: provide diagnostics and infinite second-order metrics
        return {
            "stable": False,
            "throughput": X_max,
            "mean_response_time": math.inf,
            "std_response_time": math.inf,       # replaced None
            "mean_population": math.inf,
            "std_population": math.inf,          # replaced None
            "utilization": 1.0,                  # kept for compare()
            "utilizations": {"A": rho_A, "B": rho_B, "P": rho_P},
            "util_A": rho_A,
            "util_B": rho_B,
            "util_P": rho_P,
            "system_busy_prob": U_sys,
            "X_max": X_max,
            "bottleneck": bottleneck,
        }

    # ---- 5) Per-node mean response (M/M/1-PS): W_k = D_k / (1 - rho_k) ----------
    W_A = D_A / (1.0 - rho_A)
    W_B = D_B / (1.0 - rho_B)
    W_P = D_P / (1.0 - rho_P)

    # ---- 6) System means ---------------------------------------------------------
    W_sys = W_A + W_B + W_P
    N_sys = X * W_sys

    # ---- 6bis) Standard deviations (M/M/1-PS with exponential service) ----------
    # For each node: std[W_k] = W_k; assume independence across nodes.
    std_W_A = W_A
    std_W_B = W_B
    std_W_P = W_P

    # System std: sqrt of sum of per-node variances
    std_W_sys = math.sqrt(std_W_A**2 + std_W_B**2 + std_W_P**2)
    std_N_sys = X * std_W_sys  # From Little's Law and treating X as constant

    return {
        "stable": True,
        "throughput": X,
        "mean_response_time": W_sys,
        "std_response_time": std_W_sys,          # now provided
        "mean_population": N_sys,
        "std_population": std_N_sys,             # now provided

        # --- Utilizations ---
        "utilization": U_sys,
        "util_A": rho_A,
        "util_B": rho_B,
        "util_P": rho_P,
        "utilizations": {"A": rho_A, "B": rho_B, "P": rho_P},

        "system_busy_prob": U_sys,
        "X_max": X_max,
        "bottleneck": bottleneck,
    }


