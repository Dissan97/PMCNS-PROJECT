from simulator.simulation import Simulation
import sys
from tabulate import tabulate


"""
TODO:
check why some classes are in the wrong node DONE
check why some jobs are not completed DONE
check utilization of the nodes why is it 0? should be 0.96 circa DONE
valutare i vari criteri di arresto della simulazione basato su tempo/numero di eventi(attuale)
"""


if __name__ == "__main__":
    cfg = sys.argv[1] if len(sys.argv) > 1 else "config_obj1.json"
    sim = Simulation(cfg)
    overall, per_node = sim.run()

# --- stampa risultati globali ---
print("\n=== OVERALL ===")
print(
    tabulate(
        [overall],                # lista di dict
        headers="keys",
        floatfmt=".3f",
        tablefmt="rounded_outline"  # puoi provare anche "github", "fancy_grid", …
    )
)

# --- stampa risultati per nodo ---
print("\n=== PER NODE ===")
node_rows = [dict(Node=n, **per_node[n]) for n in sorted(per_node)]
print(
    tabulate(
        node_rows,
        headers="keys",
        floatfmt=".3f",
        tablefmt="fancy_grid"
    )
)

