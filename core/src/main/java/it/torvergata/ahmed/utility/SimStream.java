package it.torvergata.ahmed.utility;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;

/**
 * Utility class used to Simple OUTSTREAM in terminal
 */
public class SimStream {

    private SimStream(){
        // should never be instantiated
        throw new IllegalStateException("Utility class");
    }

    /**
     * Simple OutStream that uses FileDescriptor out to execute outStream
     */
    public static final PrintStream OUT =
            new PrintStream(new FileOutputStream(FileDescriptor.out), true);
}
