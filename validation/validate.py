# validation/validate.py
# --------------------------------------------------------------
# Confronta repliche di simulazione (CSV) con formule analitiche.
# Supporta covarianze/ correlazioni opzionali dal cfg (vedi formulas.py).
# --------------------------------------------------------------

import sys
import glob
from pathlib import Path
from tabulate import tabulate

from formule import load_cfg, analytic_metrics   # <— aggiornata l'import
from loader   import load_sim_csv
from compare  import compare


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

    # ---------- parte analitica --------------------------------
    cfg      = load_cfg(cfg_path)
    analytic = analytic_metrics(cfg, gamma)

    # ---------- carica repliche simulazione --------------------
    replicas = []
    for csv_path in csv_files:
        overall, _ = load_sim_csv(Path(csv_path))
        replicas.append(overall)

    # ---------- confronto e tabella finale ---------------------
    cmp = compare(analytic, replicas)
    rows = [
        dict(
            metric   = k,
            analytic = v["analytic"],
            sim_mean = v["sim_mean"],
            CI_95    = f"±{v['ci']:.3f}",
            diff_pct = f"{v['diff_pct']:.2f}%"
        )
        for k, v in cmp.items()
    ]
    print(tabulate(rows, headers="keys", floatfmt=".5f", tablefmt="rounded_outline"))


if __name__ == "__main__":
    main()
