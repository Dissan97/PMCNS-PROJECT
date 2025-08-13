# validation/formule.py
# --------------------------------------------------------------
#  Rete A-B-A-P-A (Processor-Sharing) – metriche teoriche
#  Versione con covarianze opzionali (da cfg) o indipendenza di default
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
    Tempo medio di risposta E[T] per M/M/1-PS:
      - lam: tasso di arrivo λ [job/s]
      - mst: tempo medio di servizio E[S] [s]
    Ritorna math.inf se ρ ≥ 1 (instabile).
    (In questa implementazione non usata direttamente: lavoriamo su nodi fisici con domanda aggregata.)
    """
    rho = lam * mst
    return math.inf if rho >= 1.0 else (mst / (1.0 - rho))


# ───────────────────────────────────────────────────────────────
def _parse_service_times(cfg: dict) -> Dict[tuple, float]:
    """
    Converte la mappa dei tempi medi di servizio per *visita* in:
      { ('A',1): E[S_A1], ('A',2): E[S_A2], ('A',3): E[S_A3], ('B',1): E[S_B], ('P',2): E[S_P] }
    Il JSON può usare 'service_means_sec' (consigliato) o 'service_rates' (storico).
    I valori sono TEMPI MEDI (secondi).
    """
    raw = cfg.get("service_means_sec", cfg.get("service_rates"))
    if raw is None:
        raise KeyError("Manca 'service_means_sec' (o 'service_rates') nel cfg.")

    out: Dict[tuple, float] = {}
    for k_str, v in raw.items():
        key = ast.literal_eval(k_str)  # es. "['A', 1]" -> ['A', 1]
        if isinstance(key, list):
            key = tuple(key)
        out[tuple(key)] = float(v)
    return out


# ───────────────────────────────────────────────────────────────
def analytic_metrics(cfg: dict, gamma: float) -> dict:
    """
    Metriche teoriche per la web app A-B-A-P-A con PS (aggregazione per nodo fisico).
    Assunzioni di base:
      - Ogni nodo fisico k ha domanda aggregata D_k (somma visite).
      - ρ_k = X * D_k, con X = gamma (throughput esterno impostato).
      - E[W_k] = D_k / (1 - ρ_k). Per exp/PS, std[W_k] = E[W_k].

    Covarianze:
      - Opzionali da cfg:
          rt_covariance_sec2: { "A,B": <sec^2>, "A,P": <sec^2>, "B,P": <sec^2> }
          rt_corr:            { "A,B": <rho>,   "A,P": <rho>,   "B,P": <rho>   }
        Se entrambi presenti, rt_covariance_sec2 ha precedenza per le coppie specificate.
      - Se assenti → indipendenza (cov=0).

    Ritorna dizionario con: stable, throughput, mean/std_response_time,
    mean/std_population, utilizzo di sistema e per nodo, X_max, bottleneck.
    """
    # ---- 1) Tempi medi per visita ------------------------------------------------
    S = _parse_service_times(cfg)

    # ---- 2) Domande aggregate per nodo fisico -----------------------------------
    D_A = S[("A", 1)] + S[("A", 2)] + S[("A", 3)]
    D_B = S[("B", 1)]
    D_P = S[("P", 2)]

    # ---- 3) Throughput esterno e utilizzi ---------------------------------------
    X = float(gamma)
    rho_A = X * D_A
    rho_B = X * D_B
    rho_P = X * D_P

    # Probabilità che almeno un nodo sia occupato (approssimazione moltiplicativa)
    U_sys = 1.0 - (1.0 - rho_A) * (1.0 - rho_B) * (1.0 - rho_P)

    # Limite di capacità e collo di bottiglia
    X_max = 1.0 / max(D_A, D_B, D_P)
    bottleneck = max((("A", D_A), ("B", D_B), ("P", D_P)), key=lambda t: t[1])[0]

    # ---- 4) Stabilità ------------------------------------------------------------
    if any(r >= 1.0 for r in (rho_A, rho_B, rho_P)):
        return {
            "stable": False,
            "throughput": X_max,
            "mean_response_time": math.inf,
            "std_response_time": math.inf,
            "mean_population": math.inf,
            "std_population": math.inf,
            "utilization": 1.0,
            "utilizations": {"A": rho_A, "B": rho_B, "P": rho_P},
            "util_A": rho_A, "util_B": rho_B, "util_P": rho_P,
            "system_busy_prob": U_sys,
            "X_max": X_max, "bottleneck": bottleneck,
        }

    # ---- 5) Medie per nodo (PS) --------------------------------------------------
    W_A = D_A / (1.0 - rho_A)
    W_B = D_B / (1.0 - rho_B)
    W_P = D_P / (1.0 - rho_P)

    # ---- 6) Medie di sistema -----------------------------------------------------
    W_sys = W_A + W_B + W_P
    N_sys = X * W_sys

    # ---- 6bis) Varianze e covarianze --------------------------------------------
    # Per PS+exp: std(W_k) = W_k, quindi var(W_k) = W_k^2
    std_W_A = W_A
    std_W_B = W_B
    std_W_P = W_P

    var_W_A = std_W_A ** 2
    var_W_B = std_W_B ** 2
    var_W_P = std_W_P ** 2

    # Covarianze opzionali dal cfg (altrimenti 0)
    cov_pairs = _read_covariances(
        cfg,
        needed_pairs={("A", "B"): None, ("A", "P"): None, ("B", "P"): None},
        stds={"A": std_W_A, "B": std_W_B, "P": std_W_P},
    )
    cov_AB = cov_pairs[("A", "B")]
    cov_AP = cov_pairs[("A", "P")]
    cov_BP = cov_pairs[("B", "P")]

    # Varianza del totale R = W_A + W_B + W_P
    var_R = var_W_A + var_W_B + var_W_P + 2.0 * (cov_AB + cov_AP + cov_BP)
    var_R = max(var_R, 0.0)  # clamp numerico
    std_W_sys = math.sqrt(var_R)

    # Da Little (con X costante): std_N = X * std_R
    std_N_sys = X * std_W_sys

    return {
        "stable": True,
        "throughput": X,
        "mean_response_time": W_sys,
        "std_response_time": std_W_sys,
        "mean_population": N_sys,
        "std_population": std_N_sys,
        "utilization": U_sys,
        "util_A": rho_A, "util_B": rho_B, "util_P": rho_P,
        "utilizations": {"A": rho_A, "B": rho_B, "P": rho_P},
        "system_busy_prob": U_sys,
        "X_max": X_max, "bottleneck": bottleneck,
    }


# ───────────────────────────────────────────────────────────────
# Helper per leggere covarianze/correlazioni dal cfg
# ───────────────────────────────────────────────────────────────
def _read_covariances(cfg: dict,
                      needed_pairs: Dict[tuple, Any],
                      stds: Dict[str, float]) -> Dict[tuple, float]:
    """
    Legge covarianze (sec^2) e/o correlazioni (rho) dal cfg e restituisce Cov[W_i,W_j] per le coppie richieste.
    Formati accettati:
      rt_covariance_sec2: { "A,B": 0.12, "A,P": 0.00, "B,P": 0.03 }
      rt_corr:            { "A,B": 0.15, "A,P": 0.10, "B,P": 0.05 }
    Se entrambi presenti, rt_covariance_sec2 ha precedenza per le coppie specificate.
    """
    cov_map = _normalize_pair_map(cfg.get("rt_covariance_sec2", {}))
    corr_map = _normalize_pair_map(cfg.get("rt_corr", {}))

    out: Dict[tuple, float] = {}
    for (i, j) in needed_pairs.keys():
        key = tuple(sorted((i, j)))
        if key in cov_map:
            out[key] = float(cov_map[key])
        elif key in corr_map:
            rho = float(corr_map[key])
            out[key] = rho * stds[i] * stds[j]
        else:
            out[key] = 0.0
    return out


def _normalize_pair_map(raw: dict) -> Dict[tuple, float]:
    """
    Normalizza chiavi di coppia in tuple ordinate ('A','B').
    Accetta chiavi tipo "A,B", "B,A", "('A','B')", ecc.
    """
    out: Dict[tuple, float] = {}
    for k, v in raw.items():
        s = str(k).strip().replace("(", "").replace(")", "").replace("[", "").replace("]", "").replace("'", "")
        parts = [p.strip() for p in s.split(",") if p.strip()]
        if len(parts) != 2:
            continue
        i, j = sorted(parts[:2])
        out[(i, j)] = float(v)
    return out
