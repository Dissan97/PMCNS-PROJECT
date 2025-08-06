from simulator.node import Node

class Network:
    def __init__(self, scheduler, routing_matrix, service_rates):
        self.scheduler = scheduler
        self.routing_matrix = routing_matrix
        self.nodes = {}
        # Fig.3 + Table 1: crea un Node per ogni nome in service_rates
        names = {n for (n, c) in service_rates}
        for n in names:
            rates = {c: mu for (nn, c), mu in service_rates.items() if nn == n}
            self.nodes[n] = Node(n, rates)

    def get_node(self, name) -> Node:
        return self.nodes[name]
