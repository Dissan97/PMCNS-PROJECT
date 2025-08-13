# --------------------------------------------------------------
# validation/validate.py
#
# Confronta repliche di simulazione (CSV) con formule analitiche.
#
# Nella tabella principale:
#  - std_response_time       = AGGIORNATA con covarianze empiriche stimate dai per-job
#  - std_population          = analitica tempo-pesata (NON ricavata da γ·σ_T)
#
# In fondo alla stessa tabella aggiunge due righe "what-if (indipendenza)":
#  - std_response_time_indep = analitico con cov=0 (formula pura)
#  - std_population_indep    = X_analytic * std_response_time_indep
#    (mostrando a fianco i valori di simulazione; diff vuoto)
#
# USO:
#   python validation\validate.py <gamma> <config.json> <csv1> [csv2 ...]
#
# ESEMPI:
#   python validation\validate.py 1.2 config_obj1.json       .output_simulation\results_obj1_run*.csv
#   python validation\validate.py 1.2 config_obj2_2fa.json   .output_simulation\results_obj2_2fa_run*.csv
#   python validation\validate.py 1.2 config_obj3_heavy.json .output_simulation\results_obj3_heavy_run*.csv
# --------------------------------------------------------------

import sys
import glob
import math
from pathlib import Path
from tabulate import tabulate

from formule import load_cfg, analytic_metrics, _parse_service_times
from loader   import load_sim_csv
from compare  import compare


def apply_empirical_cov_to_std_rt(analytic: dict) -> None:
    """
    Aggiorna SOLO analytic["std_response_time"] usando Var/Cov empiriche stimate
    dai per-job (.output_simulation/per_job_times*.csv). NON tocca std_population.
    """
    try:
        import pandas as pd
        import numpy as np
        import os
    except Exception:
        return

    per_job_files = glob.glob(os.path.join(".output_simulation", "per_job_times*.csv"))
    if not per_job_files:
        return

    try:
        df_list = [pd.read_csv(p) for p in per_job_files]
        if not df_list:
            return
        df = pd.concat(df_list, ignore_index=True)
    except Exception:
        return

    req = {"T_A", "T_B", "T_P"}
    if not req.issubset(df.columns) or len(df) <= 1:
        return

    T_A = df["T_A"].to_numpy()
    T_B = df["T_B"].to_numpy()
    T_P = df["T_P"].to_numpy()

    varA  = float(np.var(T_A, ddof=1))
    varB  = float(np.var(T_B, ddof=1))
    varP  = float(np.var(T_P, ddof=1))
    covAB = float(np.cov(T_A, T_B, ddof=1)[0, 1])
    covAP = float(np.cov(T_A, T_P, ddof=1)[0, 1])
    covBP = float(np.cov(T_B, T_P, ddof=1)[0, 1])

    var_R = varA + varB + varP + 2.0 * (covAB + covAP + covBP)
    if var_R < 0.0:
        var_R = 0.0
    analytic["std_response_time"] = math.sqrt(var_R)
    # NB: NON cambiamo analytic["std_population"] (tempo-pesata)


def compute_std_independence(cfg: dict, gamma: float):
    """
    Calcola gli std nello scenario "indipendenza (cov=0)":
      - std_RT_indep = sqrt(W_A^2 + W_B^2 + W_P^2)
      - std_N_indep  = X * std_RT_indep
    Ritorna (std_RT_indep, std_N_indep); (None, None) se instabile o dati mancanti.
    """
    try:
        S = _parse_service_times(cfg)
        D_A = S[("A", 1)] + S[("A", 2)] + S[("A", 3)]
        D_B = S[("B", 1)]
        D_P = S[("P", 2)]
        X = float(gamma)
        rho_A = X * D_A
        rho_B = X * D_B
        rho_P = X * D_P
        if any(r >= 1.0 for r in (rho_A, rho_B, rho_P)):
            return None, None
        W_A = D_A / (1.0 - rho_A)
        W_B = D_B / (1.0 - rho_B)
        W_P = D_P / (1.0 - rho_P)
        var_R_indep = W_A**2 + W_B**2 + W_P**2
        std_R_indep = math.sqrt(var_R_indep)
        std_N_indep = X * std_R_indep
        return std_R_indep, std_N_indep
    except Exception:
        return None, None


def main() -> None:
    # ---------- parsing argv --------------------
    if len(sys.argv) < 4:
        print(
            "Uso:\n"
            "  python validation\\validate.py <gamma> <config.json> <csv1> [csv2 ...]\n"
            "Esempio:\n"
            "  python validation\\validate.py 1.2 config_obj1.json "
            ".output_simulation\\results_obj1_run*.csv"
        )
        sys.exit(1)

    # gamma
    try:
        gamma = float(sys.argv[1])
    except ValueError:
        print("Errore: <gamma> deve essere un numero (float).")
        sys.exit(1)

    # cfg
    cfg_path = Path(sys.argv[2]).resolve()
    if not cfg_path.is_file():
        print(f"Errore: file di configurazione non trovato → {cfg_path}")
        sys.exit(1)

    # espansione wildcard CSV
    csv_patterns = sys.argv[3:]
    csv_files: list[str] = []
    for pat in csv_patterns:
        csv_files.extend(glob.glob(pat))
    if not csv_files:
        print("Errore: nessun CSV trovato con i pattern indicati.")
        sys.exit(1)

    # ---------- parte analitica (base) --------------------------
    cfg      = load_cfg(cfg_path)
    analytic = analytic_metrics(cfg, gamma)

    # Aggiorna SOLO std_response_time analitico con covarianze empiriche
    apply_empirical_cov_to_std_rt(analytic)

    # ---------- carica repliche simulazione --------------------
    replicas = []
    for csv_path in csv_files:
        overall, _ = load_sim_csv(Path(csv_path))
        replicas.append(overall)

    # ---------- confronto standard -----------------------------
    cmp = compare(analytic, replicas)

    # Costruisci tabella standard (formattiamo CI_95 e diff_pct come stringhe "pulite")
    rows = []
    for k, v in cmp.items():
        rows.append(dict(
            metric   = k,
            analytic = v["analytic"],
            sim_mean = v["sim_mean"],
            CI_95    = f"±{v['ci']:.3f}",
            diff_pct = f"{v['diff_pct']:.2f}%"
        ))

    # ---------- righe extra "indep" (cov=0) --------------------
    std_rt_indep, std_pop_indep = compute_std_independence(cfg, gamma)

    # recupero valori di simulazione da affiancare
    sim_rt_mean = cmp.get("std_response_time", {}).get("sim_mean")
    sim_rt_ci   = cmp.get("std_response_time", {}).get("ci")
    sim_np_mean = cmp.get("std_population", {}).get("sim_mean")
    sim_np_ci   = cmp.get("std_population", {}).get("ci")

    if std_rt_indep is not None:
        rows.append(dict(
            metric   = "std_response_time_indep",
            analytic = std_rt_indep,
            sim_mean = sim_rt_mean if sim_rt_mean is not None else "-",
            CI_95    = f"±{sim_rt_ci:.3f}" if sim_rt_ci is not None else "-",
            diff_pct = "-"   # non confrontiamo: scenari diversi
        ))
    if std_pop_indep is not None:
        rows.append(dict(
            metric   = "std_population_indep",
            analytic = std_pop_indep,
            sim_mean = sim_np_mean if sim_np_mean is not None else "-",
            CI_95    = f"±{sim_np_ci:.3f}" if sim_np_ci is not None else "-",
            diff_pct = "-"   # non confrontiamo: std_N analitico (indep) vs std_N tempo-pesata di sim
        ))

    # ---------- stampa tabella finale ---------------------------
    print(tabulate(rows, headers="keys", floatfmt=".5f", tablefmt="rounded_outline"))


if __name__ == "__main__":
    main()
