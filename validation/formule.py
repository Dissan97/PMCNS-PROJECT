# --------------------------------------------------------------
#  Rete A-B-A-P-A (Processor-Sharing) – metriche teoriche
#  Versione allineata: std_population come time-average (coerente con sim)
#  - mean_population = somma E[N_k] (equivale anche a X * somma W_k)
#  - std_population  = sqrt( Var(N_A)+Var(N_B)+Var(N_P) + 2*Cov(...) )
#    con Var(N_k) = rho_k / (1 - rho_k)^2  (M/M/1, vale anche per PS con exp)
#    e Cov opzionali da cfg (default = 0).
# --------------------------------------------------------------

import json
import math
import ast
from pathlib import Path
from typing import Dict, Any


# ───────────────────────────────────────────────────────────────
def load_cfg(path: Path) -> Dict[str, Any]:
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def mm1_ps_mean_response(lam: float, mst: float) -> float:
    rho = lam * mst
    return math.inf if rho >= 1.0 else (mst / (1.0 - rho))


def _parse_service_times(cfg: dict) -> Dict[tuple, float]:
    """
    Converte la mappa dei tempi medi di servizio per *visita* in:
      { ('A',1): E[S_A1], ('A',2): E[S_A2], ('A',3): E[S_A3], ('B',1): E[S_B], ('P',2): E[S_P] }
    I valori SONO TEMPI MEDI (secondi).
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

    Scelte:
      - 'mean_response_time' = W_A + W_B + W_P (con W_k = D_k/(1 - rho_k))
      - 'std_response_time'  = di base indipendenza (poi puoi aggiornarla in validate.py
                               con cov empiriche dai per-job)
      - 'mean_population'    = somma E[N_k] = sum( rho_k/(1 - rho_k) )
      - 'std_population'     = time-average: sqrt( sum Var(N_k) + 2*sum Cov(N_i,N_j) )
                               con Var(N_k) = rho_k / (1 - rho_k)^2
                               Cov opzionali dal cfg (pop_covariance_sec2 / pop_corr)

    Covarianze disponibili via cfg:
      - rt_covariance_sec2 / rt_corr     → per tempi di risposta (se vuoi usarle altrove)
      - pop_covariance_sec2 / pop_corr   → per popolazioni N_i (qui!)
    """
    # ---- 1) Tempi medi per visita (secondi) -------------------------------------
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

    # ---- 5) Per-nodo W_k (PS) ---------------------------------------------------
    W_A = D_A / (1.0 - rho_A)
    W_B = D_B / (1.0 - rho_B)
    W_P = D_P / (1.0 - rho_P)

    # ---- 6) Response-time totals -------------------------------------------------
    W_sys = W_A + W_B + W_P

    # ---- 7) Population means (time-average) -------------------------------------
    EN_A = rho_A / (1.0 - rho_A)
    EN_B = rho_B / (1.0 - rho_B)
    EN_P = rho_P / (1.0 - rho_P)
    N_sys_mean = EN_A + EN_B + EN_P  # = X * W_sys (coerenza con Little)

    # ---- 8) Population std (time-average, stile simulazione) --------------------
    # Var per-nodo: Var(N_k) = rho_k / (1 - rho_k)^2
    Var_A = rho_A / ((1.0 - rho_A) ** 2)
    Var_B = rho_B / ((1.0 - rho_B) ** 2)
    Var_P = rho_P / ((1.0 - rho_P) ** 2)

    std_N_A = math.sqrt(Var_A)
    std_N_B = math.sqrt(Var_B)
    std_N_P = math.sqrt(Var_P)

    # Covarianze opzionali tra popolazioni (default 0).
    # Formati nel cfg:
    #   pop_covariance_sec2: { "A,B": <jobs^2>, "A,P": <jobs^2>, "B,P": <jobs^2> }
    #   pop_corr:            { "A,B": <rho>,     "A,P": <rho>,     "B,P": <rho>     }
    # Se entrambi presenti, pop_covariance_sec2 ha precedenza per la coppia.
    stds_pop = {"A": std_N_A, "B": std_N_B, "P": std_N_P}
    cov_pop = _read_covariances_generic(
        cfg,
        key_cov="pop_covariance_sec2",
        key_corr="pop_corr",
        stds=stds_pop
    )
    cov_AB = cov_pop.get(("A", "B"), 0.0)
    cov_AP = cov_pop.get(("A", "P"), 0.0)
    cov_BP = cov_pop.get(("B", "P"), 0.0)

    Var_N_sys = Var_A + Var_B + Var_P + 2.0 * (cov_AB + cov_AP + cov_BP)
    if Var_N_sys < 0.0:
        Var_N_sys = 0.0
    std_N_sys = math.sqrt(Var_N_sys)

    # ---- 9) std_response_time (base: indipendenza) ------------------------------
    # (Puoi poi aggiornarlo in validate.py con cov empiriche dai per-job)
    std_W_A = W_A
    std_W_B = W_B
    std_W_P = W_P
    std_W_sys_indep = math.sqrt(std_W_A**2 + std_W_B**2 + std_W_P**2)

    return {
        "stable": True,
        "throughput": X,
        "mean_response_time": W_sys,
        "std_response_time": std_W_sys_indep,   # base (indep). Puoi sostituirlo in validate.py con cov empiriche
        "mean_population": N_sys_mean,          # time-average, somma EN_k
        "std_population": std_N_sys,            # time-average, come in simulazione
        "utilization": U_sys,
        "util_A": rho_A, "util_B": rho_B, "util_P": rho_P,
        "utilizations": {"A": rho_A, "B": rho_B, "P": rho_P},
        "system_busy_prob": U_sys,
        "X_max": X_max, "bottleneck": bottleneck,
    }


# ───────────────────────────────────────────────────────────────
# Helpers per leggere mappe coppie → cov/corr
# ───────────────────────────────────────────────────────────────
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


def _read_covariances_generic(cfg: dict, key_cov: str, key_corr: str, stds: Dict[str, float]) -> Dict[tuple, float]:
    """
    Restituisce Cov per le coppie tra 'A','B','P' usando:
      - key_cov  (covarianze espresse direttamente, unità coerenti con la metrica),
      - key_corr (correlazioni ρ_ij → Cov = ρ_ij * std_i * std_j).
    Precedenza a key_cov per le coppie specificate. Se nulla, 0.0.
    """
    cov_map  = _normalize_pair_map(cfg.get(key_cov, {}))
    corr_map = _normalize_pair_map(cfg.get(key_corr, {}))
    pairs = {("A", "B"), ("A", "P"), ("B", "P")}
    out: Dict[tuple, float] = {}
    for i, j in pairs:
        key = tuple(sorted((i, j)))
        if key in cov_map:
            out[key] = float(cov_map[key])
        elif key in corr_map:
            rho = float(corr_map[key])
            out[key] = rho * stds[i] * stds[j]
        else:
            out[key] = 0.0
    return out
