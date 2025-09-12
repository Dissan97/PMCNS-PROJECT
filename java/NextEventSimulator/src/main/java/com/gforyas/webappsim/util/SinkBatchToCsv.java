package com.gforyas.webappsim.util;

import com.gforyas.webappsim.estimators.BatchMeans;
import com.gforyas.webappsim.logging.SysLogger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Scrive un CSV con una riga per ogni batch (formato "wide").
 * Colonne: batch_id, t_start, t_end, duration, mean_response_time, mean_population, throughput, utilization
 */
public class SinkBatchToCsv extends SinkToCsv {


    String filename;

    public SinkBatchToCsv(String filename) {
        this.filename = filename;
    }

    private static final String OVERALL = "OVERALL";
    private StringBuilder inMem;

    public void sink(List<BatchMeans.BatchStats> batches, Map<String, List<BatchMeans.BatchStats>> batchPerNode,
                     double arrivalRate) {


        this.inMem = new StringBuilder();
        Arrays.asList(CSVHeader.values()).forEach(s -> this.inMem.append(s).append(','));
        this.inMem.replace(this.inMem.length() - 1, this.inMem.length(), "\n");
        for (var i = 0; i < batches.size(); i++) {
            appendBatchRecord(OVERALL, batches, arrivalRate, this.inMem, i);
        }

        batchPerNode.keySet().forEach(node -> {
            for (var i = 0; i < batchPerNode.get(node).size(); i++) {
                appendBatchRecord(node, batchPerNode.get(node), arrivalRate, this.inMem, i);
            }
        });



    }

    @Override
    public void sink() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(OUT_DIR.toFile().getPath()
                + File.separator + filename, StandardCharsets.UTF_8))) {

            writer.write(inMem.toString());
        } catch (IOException e) {
            String severe = "error " + e.getMessage();
            SysLogger.getInstance().getLogger().severe(severe);
        }
    }

    private void appendBatchRecord(String scope, List<BatchMeans.BatchStats> batches, double arrivalRate,
                                   StringBuilder stringBuilder, int i) {
        var batch = batches.get(i);
        stringBuilder.append(scope).append(',')
                .append(arrivalRate).append(',')
                .append(i).append(',')
                .append(batch.completions()).append(',')
                .append(batch.meanResponseTime()).append(',')
                .append(batch.stdResponseTime()).append(',')
                .append(batch.meanPopulation()).append(',')
                .append(batch.stdPopulation()).append(',')
                .append(batch.throughput()).append(',')
                .append(batch.utilization()).append('\n');
    }


    public enum CSVHeader {
        SCOPE,
        ARRIVAL_RATE,
        BATCH_NUM,
        COMPLETION,
        MEAN_RESPONSE_TIME,
        STD_RESPONSE_TIME,
        MEAN_POPULATION,
        STD_POPULATION,
        THROUGHPUT,
        UTILIZATION
    }
}
