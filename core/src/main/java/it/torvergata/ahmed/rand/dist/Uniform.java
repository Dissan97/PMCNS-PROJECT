package it.torvergata.ahmed.rand.dist;

import it.torvergata.ahmed.rand.Random;
import it.torvergata.ahmed.rand.MultiRandomStream;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * Generates random numbers following a uniform distribution within a specified interval.
 * The interval is defined by two parameters: first (start) and last (end).
 */
public class Uniform implements Distribution {

    @Getter
    private double first;
    @Getter
    private double last;

    /**
     * Constructs a uniform random number generator.
     *
     * @param a start of the interval (must be < b)
     * @param b end of the interval
     * @throws IllegalArgumentException if a >= b
     */
    public Uniform(double a, double b) {
        if (a < b) {
            this.first = a;
            this.last = b;
        } else {
            throw new IllegalArgumentException("a must be less than b");
        }
    }

    /**
     * Set the start of the interval.
     *
     * @param a the new start of the interval
     * @throws IllegalArgumentException if a >= current end
     */
    public void setFirst(double a) {
        if (a < this.last) {
            this.first = a;
        } else {
            throw new IllegalArgumentException("a must be less than b: " + this.last);
        }
    }

    /**
     * Set the end of the interval.
     *
     * @param b the new end of the interval
     * @throws IllegalArgumentException if b <= current start
     */
    public void setLast(double b) {
        if (b > this.first) {
            this.last = b;
        } else {
            throw new IllegalArgumentException("b must be greater than a: " + this.first);
        }
    }

    /**
     * Generates the next uniform random number between first and last.
     *
     * @param random The random number generator
     * @return A uniform random number in [first, last)
     */
    @Override
    public double nextDouble(@NotNull Random random) {
        return this.first + (this.last - this.first) * random.nextDouble();
    }

    /**
     * Generates the next uniform random number between first and last using a multi-stream generator.
     *
     * @param random The multi-stream random generator
     * @param i the stream index
     * @return A uniform random number in [first, last)
     */
    @Override
    public double nextMultiDouble(@NotNull MultiRandomStream random, int i) {
        return this.first + (this.last - this.first) * random.nextDouble(i);
    }
}
