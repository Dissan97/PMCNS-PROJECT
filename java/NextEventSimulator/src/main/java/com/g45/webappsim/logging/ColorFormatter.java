package com.g45.webappsim.logging;

import java.util.logging.*;
import org.jetbrains.annotations.NotNull;
public class ColorFormatter extends Formatter {

    private static final String WHITE = "\u001B[97m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String GREEN = "\u001B[32m";
    private static final String BLUE = "\u001B[34m";
    private static final String RESET = WHITE; // instead of \u001B[0m
    private static final String LOGGER_NAME = SysLogger.class.getSimpleName();

    /**
     * Formats the log record with color based on the log level.
     *
     * @param logRecord the log record to format
     * @return the formatted log message with color
     */
    @Override
    public String format(@NotNull LogRecord logRecord) {
        String color = switch (logRecord.getLevel().getName()) {
            case "SEVERE" -> RED;
            case "WARNING" -> YELLOW;
            case "INFO" -> BLUE;
            case "CONFIG" -> GREEN;
            default -> RESET;
        };

        return RESET + String.format("%s[%s%s%s] %s%n",LOGGER_NAME, color, logRecord.getLevel(), RESET, logRecord.getMessage());
    }
}