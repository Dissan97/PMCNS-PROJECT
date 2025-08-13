package com.g45.webappsim.util;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * A custom PrintStream that writes to the same underlying stream as System.out,
 * but exposes convenience methods for uniform, styled console output.
 * This avoids using System.out directly across the codebase.
 */
public class PersonalPrintStream extends PrintStream {

    /**
     * Builds the custom stream targeting FileDescriptor.out with UTF-8.
     */
    public PersonalPrintStream() {
        super(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
    }

    /** Prints a section header line. */
    public void header(String text) {
        println();
        println("=== " + text + " ===");
    }

    /** Prints a normal informational line. */
    public void info(String text) {
        println("• " + text);
    }

    /** Prints a warning line. */
    public void warn(String text) {
        println("! " + text);
    }

    /** Prints an error line. */
    public void error(String text) {
        println("✖ " + text);
    }

    /** Prints a prompt without newline (for user input). */
    public void prompt(String text) {
        print("? " + text + " ");
        flush();
    }
}
