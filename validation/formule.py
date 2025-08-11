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
    Metriche teoriche per la web app A-B-A-P-A con disciplina Processor-Sharing.
    Le tre visite su A sono lo *stesso server fisico* → si sommano i tempi di servizio (domanda di servizio).

    Assunzioni:
      - Modello aperto (arrivi esterni) con tasso gamma [job/s]
      - PS (M/G/1-PS) su ciascun nodo fisico
      - Nessuna perdita/abbandono all'interno di questo scope (quindi X = gamma se stabile)

    Output:
      - stable: True/False
      - throughput: X (se stabile), altrimenti None
      - mean_response_time: W_sys
      - mean_population: N_sys = X * W_sys
      - utilizations: ρ_k per ciascun nodo fisico
      - system_busy_prob: stima P{almeno un nodo busy} (approssimazione d’indipendenza)
      - X_max: bound teorico di throughput = 1 / max(D_k)
      - bottleneck: nodo con domanda di servizio massima
    """
    # ---- 1) Tempi medi per *visita* (secondi) ------------------------------------
    S = _parse_service_times(cfg)

    # ---- 2) Aggregazione per nodo fisico -----------------------------------------
    # Domande di servizio per job:
    D_A = S[("A", 1)] + S[("A", 2)] + S[("A", 3)]
    D_B = S[("B", 1)]
    D_P = S[("P", 2)]

    # ---- 3) Throughput esterno e utilizzazioni -----------------------------------
    X = float(gamma)
    rho_A = X * D_A
    rho_B = X * D_B
    rho_P = X * D_P

    # Bound di capacità e bottleneck (utile anche se instabile)
    X_max = 1.0 / max(D_A, D_B, D_P)
    bottleneck = max((("A", D_A), ("B", D_B), ("P", D_P)), key=lambda t: t[1])[0]

    # ---- 4) Stabilità -------------------------------------------------------------
    if any(r >= 1.0 for r in (rho_A, rho_B, rho_P)):
        # Rete instabile: ritorniamo info diagnostiche
        return {
            "stable": False,
            "throughput": None,
            "mean_response_time": math.inf,
            "std_response_time": None,
            "mean_population": None,
            "std_population": None,
            "utilizations": {"A": rho_A, "B": rho_B, "P": rho_P},
            # per coerenza con compare(): niente 'utilization' scalare se instabile
            "util_A": rho_A,
            "util_B": rho_B,
            "util_P": rho_P,
            "system_busy_prob": None,
            "X_max": X_max,
            "bottleneck": bottleneck,
        }

    # ---- 5) Tempi medi per nodo (M/G/1-PS): W_k = D_k / (1 - ρ_k) ----------------
    W_A = D_A / (1.0 - rho_A)
    W_B = D_B / (1.0 - rho_B)
    W_P = D_P / (1.0 - rho_P)

    # ---- 6) Metriche globali ------------------------------------------------------
    W_sys = W_A + W_B + W_P
    N_sys = X * W_sys

    # Stima opzionale P{sistema non vuoto} assumendo indipendenza tra nodi (approssimata)
    U_sys = 1.0 - (1.0 - rho_A) * (1.0 - rho_B) * (1.0 - rho_P)

    return {
        "stable": True,
        "throughput": X,
        "mean_response_time": W_sys,
        "std_response_time": None,
        "mean_population": N_sys,
        "std_population": None,

        # --- Utilizzazioni ---
        "utilization": U_sys,                   # scalar per compare()
        "util_A": rho_A,                        # per-nodo (se vuoi confrontarle)
        "util_B": rho_B,
        "util_P": rho_P,
        "utilizations": {"A": rho_A, "B": rho_B, "P": rho_P},  # anche come dict

        "system_busy_prob": U_sys,              # alias, se già usato altrove
        "X_max": X_max,
        "bottleneck": bottleneck,
    }

