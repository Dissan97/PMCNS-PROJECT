# --------------------------------------------------------------
#  Rete A-B-A-P-A (Processor-Sharing) – metriche teoriche
#  Auto-adattivo: supporta cfg deterministico e probabilistico.
#
#  Novità principali:
#   - _parse_service_times accetta sia forma piatta ("['A', 1]": 0.2)
#     sia forma annidata ("A": {"1": 0.2, ...}).
#   - Riconoscimento della modalità probabilistica da:
#       * routing_mode == "probabilistic", oppure
#       * presenza di prob_routing / routing_matrix probabilistica.
#   - Se probabilistico, calcolo di p = P(ABAPA) da:
#       * cfg["p_abapa"], altrimenti
#       * cfg["routing_path_probs"]["ABAPA"], altrimenti
#       * tabella probabilistica (prob_routing o routing_matrix).
#   - Domande per nodo con p:
#       D_A = S_A1 + p*(S_A2 + S_A3)
#       D_B = S_B
#       D_P = p*S_P
# --------------------------------------------------------------

import json
import math
import ast
from pathlib import Path
from typing import Dict, Any


# ───────────────────────────────────────────────────────────────
# IO config
# ───────────────────────────────────────────────────────────────

def load_cfg(path: Path) -> Dict[str, Any]:
    with open(path, encoding="utf-8") as f:
        return json.load(f)


# ───────────────────────────────────────────────────────────────
# Utilità per M/M/1-PS (restano invariate)
# ───────────────────────────────────────────────────────────────

def mm1_ps_mean_response(lam: float, mst: float) -> float:
    rho = lam * mst
    return math.inf if rho >= 1.0 else (mst / (1.0 - rho))


# ───────────────────────────────────────────────────────────────
# Parsing tempi di servizio – ora tollerante (piatto o annidato)
# ───────────────────────────────────────────────────────────────

def _parse_service_times(cfg: dict) -> Dict[tuple, float]:
    """
    Converte la mappa dei tempi medi di servizio per *visita* in:
      { ('A',1): E[S_A1], ('A',2): E[S_A2], ('A',3): E[S_A3], ('B',1): E[S_B], ('P',2): E[S_P] }
    I valori SONO TEMPI MEDI (secondi).

    Accetta due forme di input nel cfg:
      1) Forma piatta (storica):
         "service_means_sec" OPPURE "service_rates" come dict
         con chiavi-stringa tipo "['A', 1]": 0.2
      2) Forma annidata (nuova):
         "service_means_sec" OPPURE "service_rates" come dict annidato:
         { "A": {"1": 0.2, "2": 0.4, "3": 0.1}, "B": {"1": 0.8}, "P": {"2": 0.4} }
    """
    raw = cfg.get("service_means_sec", cfg.get("service_rates"))
    if raw is None:
        raise KeyError("Manca 'service_means_sec' (o 'service_rates') nel cfg.")

    out: Dict[tuple, float] = {}

    # Caso 1: forma piatta (chiavi come stringa di lista/tupla)
    try:
        # Heuristica: se la prima chiave è stringa e inizia con '[' o '(' → forma piatta
        sample_key = next(iter(raw.keys())) if raw else None
        if isinstance(sample_key, str) and sample_key.strip()[:1] in ("[", "("):
            for k_str, v in raw.items():
                key = ast.literal_eval(k_str)  # es. "['A', 1]" -> ['A', 1]
                if isinstance(key, list):
                    key = tuple(key)
                out[tuple(key)] = float(v)
            return out
    except Exception:
        # Se fallisce l'heuristica, proviamo l'altra forma
        pass

    # Caso 2: forma annidata (server -> classe -> valore)
    if all(isinstance(v, dict) for v in raw.values()):
        for server, clsmap in raw.items():
            for cls_id, val in clsmap.items():
                out[(str(server), int(cls_id))] = float(val)
        return out

    # Se arriviamo qui, il formato non è riconosciuto
    raise KeyError("Formato 'service_means_sec/service_rates' non riconosciuto (piatto o annidato).")


# ───────────────────────────────────────────────────────────────
# Riconoscimento modalità & p = P(ABAPA)
# ───────────────────────────────────────────────────────────────

def _is_prob_routing_matrix(mat) -> bool:
    """
    Rileva una routing_matrix probabilistica: dizionario
    { 'A': { '1': [ {target|server|serverTarget, class|eventClass, p|prob}, ... ], ... }, ... }
    """
    if not isinstance(mat, dict):
        return False
    for v in mat.values():
        if isinstance(v, dict):
            for lst in v.values():
                if isinstance(lst, list) and any(
                    isinstance(x, dict) and (
                        ('p' in x) or ('prob' in x) or ('target' in x) or ('server' in x) or ('serverTarget' in x)
                    ) for x in lst
                ):
                    return True
    return False


def _is_probabilistic_cfg(cfg: dict) -> bool:
    """
    Riconosce se il cfg indica routing probabilistico.
    Condizioni:
      - routing_mode / routingMode == "probabilistic"
      - presenza di prob_routing / probRouting
      - routing_matrix con struttura probabilistica
    """
    mode = str(cfg.get("routing_mode", cfg.get("routingMode", "deterministic"))).lower()
    if mode == "probabilistic":
        return True
    if ("prob_routing" in cfg) or ("probRouting" in cfg):
        return True
    rm = cfg.get("routing_matrix") or cfg.get("routingMatrix")
    return _is_prob_routing_matrix(rm)


def _get_prob_table(cfg: dict):
    """
    Restituisce (tabella, nome_sorgente) con la tabella delle transizioni probabilistiche.
    Origini possibili:
      - cfg['prob_routing'] / cfg['probRouting']
      - cfg['routing_matrix'] (se in forma probabilistica)
    """
    if "prob_routing" in cfg:
        return cfg["prob_routing"], "prob_routing"
    if "probRouting" in cfg:
        return cfg["probRouting"], "probRouting"
    rm = cfg.get("routing_matrix") or cfg.get("routingMatrix")
    if _is_prob_routing_matrix(rm):
        return rm, "routing_matrix"
    return None, None


def _to_float(x, default=None):
    try:
        return float(x)
    except Exception:
        return default


def _match_event_class(val, wanted):
    """Confronta classi accettando '2' / 2 come equivalenti. Se wanted è None → accetta tutto non-EXIT."""
    if wanted is None:
        if isinstance(val, str):
            return val.strip().upper() != "EXIT"
        return True
    try:
        return int(val) == int(wanted)
    except Exception:
        return False


def _sum_trans_prob(trans_list, target_server=None, event_class=None):
    """
    Somma le probabilità in una lista di transizioni, filtrando per serverTarget e/o eventClass.
    Accetta campi ('prob' o 'p') per la probabilità, ('target', 'server', 'serverTarget') per il target,
    ('class', 'eventClass') per la classe.
    """
    if not isinstance(trans_list, list):
        return 0.0
    tot = 0.0
    for tr in trans_list:
        if not isinstance(tr, dict):
            continue
        tgt = str(tr.get("serverTarget", tr.get("server", tr.get("target", "")))).strip().upper()
        evc = tr.get("eventClass", tr.get("class", None))
        if target_server is not None and tgt != str(target_server).strip().upper():
            continue
        if event_class is not None and not _match_event_class(evc, event_class):
            continue
        p = tr.get("prob", tr.get("p", None))
        p = _to_float(p, 0.0)
        tot += (p if p is not None else 0.0)
    return tot


def _extract_p_abapa(cfg: dict) -> float:
    """
    Estrae p = P(ABAPA) dal cfg:
      1) Se presente p_abapa → usa quello.
      2) Se presente routing_path_probs con chiave 'ABAPA' → usa quello.
      3) Altrimenti, se c'è una tabella probabilistica (prob_routing o routing_matrix),
         ricava p da transizioni:
             q = P(B,1 -> A,2)
             r = P(A,2 -> P, *)
         p = (q*r)/(1 - q + q*r)
    Se non ricavabile → solleva KeyError con spiegazione.
    """
    # 1) chiave diretta
    if "p_abapa" in cfg:
        p = _to_float(cfg["p_abapa"])
        if p is None:
            raise KeyError("p_abapa presente ma non numerico.")
        if not (0.0 <= p <= 1.0):
            raise ValueError(f"p_abapa={p} fuori da [0,1].")
        return p

    # 2) routing_path_probs
    rpp = cfg.get("routing_path_probs") or cfg.get("routingPathProbs")
    if isinstance(rpp, dict) and ("ABAPA" in rpp):
        p = _to_float(rpp["ABAPA"])
        if p is None:
            raise KeyError("routing_path_probs['ABAPA'] presente ma non numerico.")
        if not (0.0 <= p <= 1.0):
            raise ValueError(f"routing_path_probs['ABAPA']={p} fuori da [0,1].")
        return p

    # 3) tabella probabilistica
    pr, keyname = _get_prob_table(cfg)
    if pr is None or not isinstance(pr, dict):
        raise KeyError(
            "Config probabilistico senza 'p_abapa' e senza tabella probabilistica ('prob_routing' o 'routing_matrix')."
        )

    # estrai liste di transizioni attese
    # B, class 1 → cerca verso A, class 2
    B_map = pr.get("B") or pr.get("b")
    if not isinstance(B_map, dict):
        raise KeyError(f"'{keyname}': sezione 'B' mancante o non valida.")
    trans_B1 = B_map.get("1") or B_map.get(1) or []

    q = _sum_trans_prob(trans_B1, target_server="A", event_class=2)

    # A, class 2 → cerca verso P (classe per P di solito 2, ma filtriamo solo per target server)
    A_map = pr.get("A") or pr.get("a")
    if not isinstance(A_map, dict):
        raise KeyError(f"'{keyname}': sezione 'A' mancante o non valida.")
    trans_A2 = A_map.get("2") or A_map.get(2) or []

    r = _sum_trans_prob(trans_A2, target_server="P", event_class=None)

    if q is None or r is None:
        raise KeyError("Impossibile ricavare q o r dalla tabella di routing probabilistico.")
    if not (0.0 <= q <= 1.0 and 0.0 <= r <= 1.0):
        raise ValueError(f"q={q}, r={r}: probabilità fuori da [0,1].")

    # formula chiusa per P(visita P prima dell'EXIT), con loop A2↔B:
    # p = (q*r) / (1 - q + q*r)
    denom = (1.0 - q + q * r)
    if denom <= 0.0:
        # casi limite (q≈1 e r≈0) → probabilità nulla di raggiungere P
        return 0.0
    p = (q * r) / denom
    return max(0.0, min(1.0, p))


# ───────────────────────────────────────────────────────────────
# Analitica – ora usa p quando serve
# ───────────────────────────────────────────────────────────────

def analytic_metrics(cfg: dict, gamma: float) -> dict:
    """
    Metriche teoriche per la web app A-B-A-P-A con PS (aggregazione per nodo fisico).

    Scelte:
      - 'mean_response_time' = W_A + W_B + W_P (con W_k = D_k/(1 - rho_k))
      - 'std_response_time'  = base: indipendenza (puoi aggiornarla altrove con cov empiriche)
      - 'mean_population'    = somma E[N_k] = sum( rho_k/(1 - rho_k) )
      - 'std_population'     = time-average: sqrt( sum Var(N_k) + 2*sum Cov(N_i,N_j) )
                               con Var(N_k) = rho_k / (1 - rho_k)^2
                               Cov opzionali dal cfg (pop_covariance_sec2 / pop_corr)

    Adattamento probabilistico:
      - Se il cfg è probabilistico, p = P(ABAPA) viene estratto/derivato e applicato a D_A e D_P.
      - Se l'estrazione fallisce, si usa p=1.0 come fallback conservativo.
    """
    # ---- 1) Tempi medi per visita (secondi) -------------------------------------
    S = _parse_service_times(cfg)

    # ---- 2) p e domande aggregate per nodo fisico --------------------------------
    # ---- 2) p e domande aggregate per nodo fisico --------------------------------
    # default deterministico (nessun loop)
    p = 1.0
    E_B = 1.0
    E_A2 = 1.0

    if _is_probabilistic_cfg(cfg):
        try:
            q, r, p, E_B, E_A2 = _extract_loop_params(cfg)
        except Exception:
            # fallback: se so solo p uso il vecchio schema (compat)
            try:
                p = _extract_p_abapa(cfg)
            except Exception:
                p = 1.0
            E_B = 1.0
            E_A2 = p  # se non conosco q, r, assumo una sola visita ad A2 quando passo da P

    # Domande per nodo (generale, copre anche il caso senza loop: r=1 => E_B=1, E_A2=q=p)
    D_A = S[("A", 1)] + E_A2 * S[("A", 2)] + p * S[("A", 3)]
    D_B = E_B * S[("B", 1)]
    D_P = p * S[("P", 2)]


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
# Helpers per leggere mappe coppie → cov/corr (invariati)
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



def _extract_loop_params(cfg: dict):
    """
    Restituisce (q, r, p, E_B, E_A2) dove:
      q = P(B,1 -> A,2)
      r = P(A,2 -> P, *)
      p = prob. di visitare P prima dell'EXIT con loop B<->A2
      E_B  = visite attese a B
      E_A2 = visite attese ad A2
    Richiede una tabella probabilistica (prob_routing o routing_matrix).
    """
    pr, keyname = _get_prob_table(cfg)
    if pr is None or not isinstance(pr, dict):
        raise KeyError("Config probabilistico senza tabella ('prob_routing' o 'routing_matrix').")

    B_map = pr.get("B") or pr.get("b") or {}
    trans_B1 = B_map.get("1") or B_map.get(1) or []
    q = _sum_trans_prob(trans_B1, target_server="A", event_class=2)

    A_map = pr.get("A") or pr.get("a") or {}
    trans_A2 = A_map.get("2") or A_map.get(2) or []
    r = _sum_trans_prob(trans_A2, target_server="P", event_class=None)

    if not (0.0 <= q <= 1.0 and 0.0 <= r <= 1.0):
        raise ValueError(f"q={q}, r={r}: probabilità fuori da [0,1].")

    Z = 1.0 - q * (1.0 - r)
    if Z <= 0.0:
        # loop assorbente: niente EXIT e p=0 di raggiungere P
        return q, r, 0.0, float("inf"), float("inf")

    p = (q * r) / Z
    E_B  = 1.0 / Z
    E_A2 = q   / Z
    return q, r, p, E_B, E_A2
