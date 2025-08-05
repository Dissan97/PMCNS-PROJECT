from lemer.rngs import MultiStreamRNG
from lemer.rvms import *
import numpy as np

class Sum:
    
    def __init__(self):
        self.delay : np.ndarray = np.array([], dtype=np.float32)
        self.wait: np.ndarray = np.array([], dtype=np.float32)
        self.service = 0.0
        self.interarrival = 0.0
        
        

class SSQ2:
    
    def __init__(self, seed=1234, checkpoints=20):

      self.n = 1000
      self.sum = Sum()
      self.departure = 0.0
      self.index = 0
      self.stream = MultiStreamRNG(seed_val=seed)
      self.n_checkpoints = checkpoints 
      self.avg_waits = []
      
    
    def get_arrival(self) -> float:
        """
        inter-arrival time = 2 sec;
        lambda = 1/(time) = 0.5;

        """
        return idfExponential(m=2.0, u=self.stream.random(stream=0))
    
    # Still not supporting streams
    def get_service(self) -> float:
        """
        E(s) = 1.5 sec;
        mu=1/(E(s)) = 0.6666667;

        """
        return idfUniform(a=1.0, b=2.0, u=self.stream.random(stream=0))

    
    def run(self, start=0, last=1000):
        START = start
        arrival = 0.0
        delay = 0.0
        self.index = 0
        self.departure = 0.0
        arrival = START
        self.sum.delay = np.empty(0, dtype=np.float32)
        self.sum.wait = np.empty(0, dtype=np.float32)
        self.sum.service = 0.0
        

        while self.index < last:
            self.index += 1
            arrival += self.get_arrival()
            if arrival < self.departure:
                delay = self.departure - arrival
            else:
                delay = 0.0
            service = self.get_service()
            wait = delay + service
            self.departure = arrival + wait
            self.sum.delay = np.append(self.sum.delay, delay)
            self.sum.wait = np.append(self.sum.wait, wait) 
            self.sum.service += service

            if self.index % self.n_checkpoints == 0:
                mean_wait = self.sum.wait.mean()
                self.avg_waits.append(mean_wait)

        self.sum.interarrival = arrival - START

            
    def print_stats(self):
        print(f"for {self.index} jobs:")
        print(f"Average interarrival= {(self.sum.interarrival / self.index):.4f}")
        print(f"Average wait= {self.sum.wait.mean():.4f}")
        print(f"Average delay= {self.sum.delay.mean():.4f}")
        print(f"Average service time= {(self.sum.service / self.index):.4f}")
        print(f"Average # in the node= {(self.sum.wait.sum() / self.departure):.4f}")
        print(f"Average # in the queue= {(self.sum.delay.sum() / self.departure):.4f}")
        print(f"utilization= {(self.sum.service / self.departure):.4f}")