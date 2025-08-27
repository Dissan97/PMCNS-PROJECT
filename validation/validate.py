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

#   python validation\validate.py 1.2 config_obj1.json       .output_simulation\results_obj1_run*.csv
#   python validation\validate.py 1.2 config_obj1.json       .output_simulation\results_obj1_run*.csv
#   python validation\validate.py 1.2 config_obj1.json       .output_simulation\results_obj1_run*.csv


#linux
#   python validation/validate.py 1.2 config_obj1.json       .output_simulation/results_obj1_run*.csv
#   python validation/validate.py 1.2 config_obj1.json       .output_simulation/results_obj1_run*.csv
#   python validation/validate.py 1.2 config_obj1.json       .output_simulation/results_obj1_run*.csv

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

    cfg = load_cfg(Path("config_obj1.json"))
    csv_path = sweep_analytic_metrics_over_lambda_to_csv(cfg)
    print("CSV scritto in:", csv_path)
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


def sweep_analytic_metrics_over_lambda_to_csv(
    cfg,
    output_csv_path: str = ".output_simulation/analytic_sweep_lambda.csv",
    include_empirical_cov: bool = True
) -> str:
    """
    Esegue lo sweep di λ in [0.50, 1.20] con passo 0.05, calcola le metriche
    analitiche tramite analytic_metrics(cfg, λ) e salva i risultati in CSV.

    - NON modifica la logica del file: è una utility invocabile a parte.
    - Se include_empirical_cov=True, aggiorna SOLO 'std_response_time'
      con le covarianze empiriche per-job (se disponibili) usando
      apply_empirical_cov_to_std_rt(...).
    - Ritorna il percorso assoluto del CSV scritto.

    Parametri:
        cfg: configurazione già caricata con load_cfg(...)
        output_csv_path: percorso del CSV da creare
        include_empirical_cov: se True applica la correzione empirica su std_response_time
    """
    from decimal import Decimal, getcontext
    from pathlib import Path
    import csv
    import os

    # Evita errori di accumulo floating sul range
    getcontext().prec = 28
    start = Decimal("0.50")
    stop  = Decimal("1.20")
    step  = Decimal("0.05")

    # Calcolo righe
    rows = []
    header_order = None  # fisseremo l'ordine delle colonne in modo stabile

    x = start
    while x <= stop + Decimal("1e-12"):
        lam = float(x)
        metrics = analytic_metrics(cfg, lam)

        if include_empirical_cov:
            apply_empirical_cov_to_std_rt(metrics)

        row = {"lambda": round(lam, 2)}
        row.update(metrics)
        rows.append(row)

        if header_order is None:
            # Prima colonna 'lambda', poi le metriche in ordine alfabetico stabile
            metric_keys = [k for k in metrics.keys() if k != "lambda"]
            header_order = ["lambda"] + sorted(metric_keys)

        x += step

    # Garantiamo che il CSV contenga l'unione di tutte le chiavi eventuali
    all_keys = set()
    for r in rows:
        all_keys.update(r.keys())
    if header_order is None:
        header_order = ["lambda"]
    # Aggiungi eventuali chiavi mancanti mantenendo ordine stabile
    for k in sorted(all_keys):
        if k not in header_order:
            header_order.append(k)

    # Scrittura CSV
    out_path = Path(output_csv_path)
    os.makedirs(out_path.parent, exist_ok=True)

    with open(out_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=header_order)
        writer.writeheader()
        for r in rows:
            writer.writerow({k: r.get(k, "") for k in header_order})

    return str(out_path.resolve())



if __name__ == "__main__":
    main()
