package it.torvergata.ahmed;

import it.torvergata.ahmed.rand.MultiRandomStream;
import it.torvergata.ahmed.rand.dist.PoissonDistribution;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class Trial {
    public static void main(String[] args) {
        try(BufferedWriter writer = new BufferedWriter(
                new FileWriter("foo.csv")
        )) {
            StringBuilder builder = new StringBuilder();
            MultiRandomStream rs = new MultiRandomStream(1234);
            for (int i = 0; i < 9; i++) {
                rs.addDistribution(i, new PoissonDistribution(40));
                builder.append("gauss").append(i);
                if (i < 8){
                    builder.append(',');
                }
            }

            builder.append('\n');
            int j =0;
            for (int i = 0; i < 1000; i++) {
                for (j = 0; j < 8; j++) {
                    builder.append(rs.nextDist(j)).append(',');
                }
                builder.append(rs.nextDist(j)).append('\n');
            }
            writer.write(builder.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
