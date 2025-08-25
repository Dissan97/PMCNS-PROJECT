# --------------------------------------------------------------
# validation/validate.py
#
# Confronta repliche di simulazione (CSV) con le formule analitiche.
# Si adatta automaticamente al caso deterministico o probabilistico:
# la logica di riconoscimento e l'eventuale uso di p = P(ABAPA)
# sono incapsulati in formule.analytic_metrics().
#
# Nota: aggiorniamo SOLO 'std_response_time' analitico usando
# covarianze empiriche dai per-job (se disponibili).
# --------------------------------------------------------------

import sys
import glob
from pathlib import Path
from tabulate import tabulate

from formule import load_cfg, analytic_metrics
from loader  import load_sim_csv
from compare import compare


# ───────────────────────────────────────────────────────────────
# Covarianze empiriche → SOLO std_response_time
# ───────────────────────────────────────────────────────────────

def apply_empirical_cov_to_std_rt(analytic: dict) -> None:
    """
    Aggiorna SOLO analytic['std_response_time'] usando var/cov empiriche per-job (se presenti).
    Cerca file CSV in .output_simulation/per_job_times*.csv con colonne: T_A, T_B, T_P.
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
    analytic["std_response_time"] = var_R ** 0.5


# ───────────────────────────────────────────────────────────────
# Main
# ───────────────────────────────────────────────────────────────

def main() -> None:
    # ---------- parsing argv --------------------
    if len(sys.argv) < 4:
        print(
            "Uso:\n"
            "  python validation\\validate.py <gamma> <config.json> <csv1> [csv2 ...]\n"
            "Esempio:\n"
            "  python validation\\validate.py 1.2 config.json "
            ".output_simulation\\results_run*.csv"
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

    # ---------- parte analitica -------------------------------
    cfg = load_cfg(cfg_path)

    # analytic_metrics() si occupa di:
    #  - riconoscere deterministico vs probabilistico
    #  - estrarre/derivare p = P(ABAPA) quando necessario
    #  - calcolare tutte le metriche teoriche
    analytic = analytic_metrics(cfg, gamma)

    # Aggiorna SOLO std_response_time con covarianze empiriche (se disponibili)
    apply_empirical_cov_to_std_rt(analytic)

    # ---------- carica repliche simulazione --------------------
    replicas = []
    for csv_path in csv_files:
        overall, _ = load_sim_csv(Path(csv_path))
        replicas.append(overall)

    # ---------- confronto standard -----------------------------
    cmp = compare(analytic, replicas)

    # tabella finale
    rows = []
    for k, v in cmp.items():
        rows.append(dict(
            metric   = k,
            analytic = v["analytic"],
            sim_mean = v["sim_mean"],
            CI_95    = f"±{v['ci']:.3f}",
            diff_pct = f"{v['diff_pct']:.2f}%"
        ))

    print(tabulate(rows, headers="keys", floatfmt=".5f", tablefmt="rounded_outline"))


if __name__ == "__main__":
    main()
