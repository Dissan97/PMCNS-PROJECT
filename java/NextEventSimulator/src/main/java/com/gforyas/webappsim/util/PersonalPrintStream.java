package com.gforyas.webappsim.util;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

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

    /** Prints/updates a single progress line in-place (no newline). */
    public void progress(String text) {
        // ANSI "erase line" + carriage return; falls back gracefully if ANSI unsupported
        final String ERASE_LINE = "\u001B[2K";
        print("\r" + ERASE_LINE + text);
        flush();
    }

    /** Ends the current progress line by emitting a newline. */
    public void progressDone() {
        println(); // move to the next line
        flush();
    }
    /** Renders a single-line progress status like: [#####.....]  40%  2,000/5,000 ev | 50k ev/s | ETA 00:00:30 */
    public static String renderProgress(int pct, double rateEvPerSec, double etaSec, int barWidth) {
        String bar = renderBar(pct, barWidth);
        String rateStr = rateEvPerSec > 9999 ? String.format(Locale.ROOT, "%.0fk", rateEvPerSec / 1000.0)
                : String.format(Locale.ROOT, "%.0f", rateEvPerSec);
        String etaStr = Double.isFinite(etaSec) ? formatHMS(etaSec) : "--:--:--";
        return String.format(Locale.ROOT,
                "Progress %s %3d%%  |  %s ev/s  |  ETA %s",
                bar, pct, rateStr, etaStr);
    }

    /** Builds an ASCII progress bar: [##########--------] */
    public static String renderBar(int pct, int width) {
        int filled = (int) Math.round(width * (pct / 100.0));
        StringBuilder sb = new StringBuilder(width + 2);
        sb.append('[');
        for (int i = 0; i < width; i++) {
            sb.append(i < filled ? '#' : '-');
        }
        sb.append(']');
        return sb.toString();
    }

    /** Formats seconds as HH:MM:SS. */
    public static String formatHMS(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) return "--:--:--";
        long s = Math.round(seconds);
        long h = s / 3600; s %= 3600;
        long m = s / 60;   s %= 60;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s);
    }
}
