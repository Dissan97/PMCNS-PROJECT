package it.torvergata.ahmed.rand.dist;

import it.torvergata.ahmed.rand.Random;
import it.torvergata.ahmed.rand.MultiRandomStream;

/**
 * The interface for all distributions.
 */
public interface Distribution {

    /**
     * return the next double value by given the distribution
     * @param random The Random Number Generator
     * @return the value of the distribution
     */
    double nextDouble(Random random);

    /**
     * return the next double value by given the distribution
     * @param random The MultiRandom Number Generator
     * @param i The index of the stream
     * @return the value of the distribution
     */
    double nextMultiDouble(MultiRandomStream random, int i);
}
