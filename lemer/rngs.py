# -------------------------------------------------------------------------
# This is an ANSI C library for multi-stream random number generation.  
#  * The use of this library is recommended as a replacement for the ANSI C 
#  * rand() and srand() functions, particularly in simulation applications 
#  * where the statistical 'goodness' of the random number generator is 
#  * important.  The library supplies 256 streams of random numbers; use 
#  * SelectStream(s) to switch between streams indexed s = 0,1,...,255.
#  *
#  * The streams must be initialized.  The recommended way to do this is by
#  * using the function PlantSeeds(x) with the value of x used to initialize 
#  * the default stream and all other streams initialized automatically with
#  * values dependent on the value of x.  The following convention is used 
#  * to initialize the default stream:
#  *    if x > 0 then x is the state
#  *    if x < 0 then the state is obtained from the system clock
#  *    if x = 0 then the state is to be supplied interactively.
#  *
#  * The generator used in this library is a so-called 'Lehmer random number
#  * generator' which returns a pseudo-random number uniformly distributed
#  * 0.0 and 1.0.  The period is (m - 1) where m = 2,147,483,647 and the
#  * smallest and largest possible values are (1 / m) and 1 - (1 / m)
#  * respectively.  For more details see:
#  * 
#  *       "Random Number Generators: Good Ones Are Hard To Find"
#  *                   Steve Park and Keith Miller
#  *              Communications of the ACM, October 1988
#  *
#  * Name            : rngs.c  (Random Number Generation - Multiple Streams)
#  * Authors         : Steve Park & Dave Geyer
#  * Language        : ANSI C
#  * Latest Revision : 09-22-98
#  * Translated by     : Philip Steele 
#  * Language          : Python 3.3
#  * Latest Revision   : 3/26/14
#  *
#  * ------------------------------------------------------------------------- 

from time import time


class MultiStreamRNG:
    """
    Multi-stream Lehmer random number generator (Park & Miller).
    Provides 256 independent streams.
    """

    # global consts
    MODULUS = 2147483647  # /* DON'T CHANGE THIS VALUE                  */
    MULTIPLIER = 48271  # /* DON'T CHANGE THIS VALUE                  */
    CHECK = 399268537  # /* DON'T CHANGE THIS VALUE                  */
    STREAMS = 256  # /* # of streams, DON'T CHANGE THIS VALUE    */
    A256 = 22925  # /* jump multiplier, DON'T CHANGE THIS VALUE */
    DEFAULT = 123456789  # /* initial seed, use 0 < DEFAULT < MODULUS  */

    def __init__(self, seed_val=None):
        """
        Random is a Lehmer generator that returns a pseudo-random real number
        uniformly distributed between 0.0 and 1.0.  The period is (m - 1)
        where m = 2,147,483,647 amd the smallest and largest possible values
        are (1 / m) and 1 - (1 / m) respectively
        """
        # Internal state
        self.stream = 0
        self.initialized = False
        # Initialize array of seeds
        self.seed = [self.DEFAULT] * self.STREAMS

        # Plant seeds: if seed_val is None, use default; if negative, use time
        if seed_val is None:
            seed_val = self.DEFAULT
        self.plant_seeds(seed_val)

    def random(self, stream=0):
        """
        Returns a pseudo-random float in (0,1).
        """
        Q = self.MODULUS // self.MULTIPLIER
        R = self.MODULUS % self.MULTIPLIER
        stream = stream % self.STREAMS
        s = self.seed[stream]
        t = self.MULTIPLIER * (s % Q) - R * (s // Q)

        if t > 0:
            self.seed[stream] = t
        else:
            self.seed[stream] = t + self.MODULUS
        return self.seed[stream] / self.MODULUS

    def plant_seeds(self, x):
        """
        Initialize all streams based on one seed.
        The sequence of planted states is separated one from the next by
        8,367,782 calls to Random
        """

        Q = self.MODULUS // self.A256
        R = self.MODULUS % self.A256

        self.initialized = True
        # seed stream 0
        current = self.stream
        self.select_stream(0)
        self.put_seed(x)
        # generate seeds for other streams
        for j in range(1, self.STREAMS):
            prev = self.seed[j - 1]
            t = self.A256 * (prev % Q) - R * (prev // Q)
            self.seed[j] = t if t > 0 else t + self.MODULUS
        # restore current stream
        self.stream = current

    def put_seed(self, x):
        """
        Use this (optional) procedure to initialize or reset the state of
        the random number generator according to the following conventions:
        if x > 0 then x is the initial seed (unless too large)
        if x < 0 then the initial seed is obtained from the system clock
        if x = 0 then the initial seed is to be supplied interactively
        """

        if x > 0:
            x = x % self.MODULUS
        elif x < 0:
            x = int(time()) % self.MODULUS
        else:
            ok = False
            while not ok:
                val = int(input("Enter a positive integer seed (<=9 digits): "))
                ok = 0 < val < self.MODULUS
                if not ok:
                    print("Input out of range, riprova.")
                x = val
        self.seed[self.stream] = int(x)

    def get_seed(self):
        """Return the current seed"""
        return self.seed[self.stream]

    def select_stream(self, index):
        """select the current seed."""
        self.stream = index % self.STREAMS
        if not self.initialized and self.stream != 0:
            self.plant_seeds(self.DEFAULT)

    def test_random(self):
        """Testing the prng"""
        # Test stream 0
        self.select_stream(0)
        self.put_seed(1)
        for _ in range(10000):
            _ = self.random()
        ok = (self.get_seed() == self.CHECK)
        # Test stream 1
        self.select_stream(1)
        self.plant_seeds(1)
        ok = ok and (self.get_seed() == self.A256)
        if ok:
            print("The implementation is correct")
        else:
            print("Error in implementation")
