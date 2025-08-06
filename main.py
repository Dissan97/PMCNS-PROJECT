from simulator.simulation import Simulation
import sys

"""
TODO:
check why some classes are in the wrong node
check why some jobs are not completed
"""


if __name__ == "__main__":
    cfg = sys.argv[1] if len(sys.argv)>1 else "config_obj1.json"
    sim = Simulation(cfg)
    res = sim.run()
    print("=== Simulation Results ===")
    for k,v in res.items():
        print(f"{k:20s}: {v:.6f}")
