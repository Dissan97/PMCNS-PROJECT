from simulator.node import Node
from simulator.scheduler import NextEventScheduler

class Network:
    def __init__(self, scheduler: NextEventScheduler, routing_matrix, service_rates):
        """
            Definition of the network
            :param scheduler: the scheduler to be used
            :param routing_matrix: the routing matrix for class-switch
            :param service_rates: the service rates for each node
        """
        self.scheduler = scheduler
        self.routing_matrix = routing_matrix
        self.nodes = {}
        names = {n for (n, c) in service_rates}
        for n in names:
            rates = {c: mu for (nn, c), mu in service_rates.items() if nn == n}
            self.nodes[n] = Node(n, rates)

    def get_node(self, name) -> Node:
        return self.nodes[name]
