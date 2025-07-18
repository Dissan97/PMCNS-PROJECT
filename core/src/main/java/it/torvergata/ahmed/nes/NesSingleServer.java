package it.torvergata.ahmed.nes;

import it.torvergata.ahmed.simulator.Simulator;

public class NesSingleServer extends Simulator {
    public NesSingleServer(NesSingleServerConfig config) {
        super(null);
    }
    @Override
    public String getMetricsToJson() {
        return "";
    }

    @Override
    protected void getService() {

    }

    @Override
    protected void getArrival() {

    }

    @Override
    public void run() {

    }
}
