package com.g45.webappsim.estimators;

import com.g45.webappsim.simulator.Network;
import com.g45.webappsim.simulator.NextEventScheduler;
import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.asciitable.CWC_LongestLine;
import de.vandermeer.skb.interfaces.transformers.textformat.TextAlignment;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public interface Estimator {
    /**
     * Allow the calculation of the stats depending on implementation
     * @param scheduler the NextEventScheduler
     * @param network the server network
     * {@link NextEventScheduler}
     * {@link Network}
     */
    void calculateStats(NextEventScheduler scheduler, Network network);

}
