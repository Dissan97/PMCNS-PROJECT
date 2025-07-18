package it.torvergata.ahmed.rand;

import it.torvergata.ahmed.rand.dist.Distribution;
import it.torvergata.ahmed.rand.dist.HyperExponential;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MultiRandomStreamTest {

    @Test
    void testNoCollisionsAcrossAllStreams() {
        long seed = 1234;
        int streamCount = 256;
        int iterations = 100_000; // Safe distance: much less than guaranteed ~8 million per stream

        MultiRandomStream rng = new MultiRandomStream(seed, streamCount);
        Set<Long> globalSet = new HashSet<>(streamCount * iterations);

        for (int stream = 0; stream < streamCount; stream++) {
            for (int j = 0; j < iterations; j++) {
                long value = rng.next(stream);

                // If the number already exists in another stream's output, it's a collision
                boolean added = globalSet.add(value);
                int finalStream = stream;
                int finalJ = j;
                assertTrue(added,
                        () -> "Collision detected! Value " + value +
                                " already seen from another stream (current stream: " + finalStream +
                                ") on iteration=" + finalJ
                );
            }
        }

        // Final assertion: total number of unique values should match expected count
        int expected = streamCount * iterations;
        assertEquals(expected, globalSet.size(),
                "Unexpected number of unique values. Expected: " + expected +
                        ", but got: " + globalSet.size());
    }
    @Test
    void testSimpleHyperExponential(){
        long seed = 1234;
        Random rng = new Random(seed);
        Random rng2 = new Random(seed);
        Distribution distribution = new HyperExponential(0.35, 1.0);
        rng.setDistribution(distribution);
        rng2.setDistribution(distribution);
        boolean passed = true;
        for (int i = 0; i < 100000; i++) {
            if (rng.nextDist() != rng2.nextDist()) {
                passed = false;
                break;
            }
        }
        assertTrue(passed);

    }

    @Test
    void testComplexHyperExponential() {
        long seed = 1234;
        Random rng = new Random(seed);
        Random rng2 = new Random(seed);
        Distribution distribution = new HyperExponential(new double[]{0.2, 0.3, 0.5}, new double[]{1.0, 1.2, 1.4});
        rng.setDistribution(distribution);
        rng2.setDistribution(distribution);
        boolean passed = true;
        for (int i = 0; i < 100000; i++) {
            if (rng.nextDist() != rng2.nextDist()) {
                passed = false;
                break;
            }
        }
        assertTrue(passed);
    }
}
