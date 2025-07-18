package it.torvergata.ahmed.des;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class DesMultiServerConfig extends DesSingleServerConfig {
    private int serverCount;

    public DesMultiServerConfig() {
        super();
        this.serverCount = 2; // default 2 servers
    }

}
