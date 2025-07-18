/**
 * Provides classes for random number generation and statistical distributions.
 * <p>
 * This package contains implementations for:
 * <ul>
 *   <li>Lehmer random number generator ({@link it.uniroma2.ahmed.rand.Random})</li>
 *   <li>Statistical distributions ({@link it.uniroma2.ahmed.rand.dist.Distribution})</li>
 *   <li>Random generator parameters ({@link it.torvergata.ahmed.rand.RandParameters})</li>
 * </ul>
 * <p>
 * The random number generation is based on the Lehmer algorithm which uses the formula:
 * X(n+1) = (A * X(n)) mod M
 * where A and M are carefully chosen constants defined in {@link it.torvergata.ahmed.rand.RandParameters}.
 *
 * @see it.uniroma2.ahmed.rand.Random
 * @see it.uniroma2.ahmed.rand.dist.Distribution
 * @see it.torvergata.ahmed.rand.RandParameters
 */
package it.torvergata.ahmed.rand;