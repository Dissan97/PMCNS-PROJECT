from lemer.rngs import MultiStreamRNG
from lemer.rvms import *
import numpy as np

class Sum:

    def __init__(self):
        self.setup = 0.0
        self.holding = np.array([], dtype = np.float32)
        self.shortage = np.array([], dtype = np.float32)
        self.order = np.array([], dtype = np.float32)
        self.demand = np.array([], dtype = np.float32)


class SIS2:

    MINIMUM = 20
    MAXIMUM = 80

    def __init__(self, seed = 12345):
        self.inventory = SIS2.MAXIMUM
        self.stream = MultiStreamRNG(seed_val = seed)
        self.index = 0
        self.sum = Sum()
        self.avg_level = []
        self.inventory_history = []
        
    
    def get_demand(self) -> float:
        return idfEquilikely(a = 10, b = 50, u = self.stream.random())
    
    def run(self, start=0, last=1000, n_checkpoints=20):
        self.last = last
        self.inventory = SIS2.MAXIMUM
        self.index = 0
        while self.index < last:
            self.index += 1
            self.inventory_history.append(self.inventory)
            if self.inventory < SIS2.MINIMUM:
                order = SIS2.MAXIMUM - self.inventory
                self.sum.setup += 1
                self.sum.order = np.append(self.sum.order, order)
            else:
                order = 0
            self.inventory += order
            demand = self.get_demand()
            self.sum.demand = np.append(self.sum.demand, demand)
            
            if self.inventory > demand:
                shortage = 0.0
                holding = (self.inventory - (0.5 * demand))
                self.sum.holding = np.append(self.sum.holding, holding)
            else:
                shortage = ((demand - self.inventory) ** 2 / (2 * demand))
                holding = ((self.inventory) ** 2 / (2 * demand))
                self.sum.holding = np.append(self.sum.holding, holding)
                self.sum.shortage = np.append(self.sum.shortage, shortage)
            self.inventory -= demand
                    
                    # Restock finale se l'inventario è ancora sotto S_MAX

            if self.index % n_checkpoints == 0:
                mean_level = holding - shortage
                self.avg_level.append(mean_level)

        if self.inventory < SIS2.MAXIMUM:
            order = SIS2.MAXIMUM - self.inventory
            self.sum.setup += 1
            self.sum.order = np.append(self.sum.order, order)
            self.inventory += order

    

    def print_stats(self):
        n = self.last
        total_order = np.sum(self.sum.order)
        print("=== SIS2 Statistics ===")
        print(f"Setup frequency: {self.sum.setup / n:.2f}")
        print(f"Average order per period: {total_order / n:.2f}")
        print(f"Average order when ordering: {np.mean(self.sum.order):.2f}")
        print(f"Average demand per period: {np.mean(self.sum.demand):.2f}")
        print(f"Average holding level: {np.sum(self.sum.holding) / n:.2f}")
        print(f"Average shortage level: {np.sum(self.sum.shortage) / n:.2f}")
        print(f"Final inventory level: {self.inventory:.2f}")
        print(f"Average inventory level: {np.mean(self.avg_level):.2f}")
        print(f"Total inventory level: {np.sum(self.avg_level):.2f}")