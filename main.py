# main.py
from simulator.simulation import Simulation
from tabulate import tabulate
from pathlib import Path
from datetime import datetime
import pandas as pd
import sys

"""
TODO:
rivedere di gruppo le formule per la validazione
controllare che le metriche siano corrette
rivedere il codice per le metriche per i nodi, in particolare il ResponseTime torkin le fa diverse
"""


# ───────────────────────────────────────────────────────────────────
CFG_PATH = Path(sys.argv[1] if len(sys.argv) > 1 else "config_obj1.json")

SEEDS    = [111, 222, 333, 444, 555]       # lista di seed per le repliche
OUT_DIR  = Path(".output_simulation")
# Controlla se la cartella esiste, altrimenti la crea
if not OUT_DIR.exists():
    OUT_DIR.mkdir()
# ───────────────────────────────────────────────────────────────────

def run_one(seed: int, run_idx: int):
    sim = Simulation(CFG_PATH, seed=seed)
    overall, per_node = sim.run()

    # --- stampa in console --------------------------------------------------
    print(f"\n► Run {run_idx}  (seed={seed})")

    print("\nOVERALL")
    print(tabulate([overall], headers="keys", floatfmt=".3f",
                   tablefmt="rounded_outline"))

    print("\nPER NODE")
    node_rows = [dict(Node=n, **per_node[n]) for n in sorted(per_node)]
    print(tabulate(node_rows, headers="keys", floatfmt=".3f",
                   tablefmt="fancy_grid"))

    # --- salva CSV ----------------------------------------------------------
    ts   = datetime.now().strftime("%Y%m%d_%H%M%S")
    name = f"results_{CFG_PATH.stem}_run{run_idx:03}_seed{seed}_{ts}.csv"

    rows = [dict(scope="OVERALL", **overall)]
    rows += [dict(scope=f"NODE_{n}", **per_node[n]) for n in sorted(per_node)]
    pd.DataFrame(rows).to_csv(OUT_DIR / name, index=False)

    print(f"✓ CSV salvato → {name}")

# ───────────────────────────────────────────────────────────────────
for idx, s in enumerate(SEEDS, start=1):
    run_one(s, idx)
