package com.g45.webappsim.util;

/**
 * Singleton accessor for the application's personal print stream.
 * Keeps a single instance to be reused everywhere.
 */
public final class Printer {

    private static final PersonalPrintStream OUT = new PersonalPrintStream();

    private Printer() {
        // Utility class
    }

    /** @return the shared PersonalPrintStream instance */
    public static PersonalPrintStream out() {
        return OUT;
    }
}
