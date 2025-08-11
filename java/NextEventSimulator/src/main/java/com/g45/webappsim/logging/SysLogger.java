package com.g45.webappsim.logging;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class SysLogger {

    private SysLogger(){

        // this class must be invisible outside
    }

    public static final String ELAPSED_TIME = "Elapsed Time: ";
    public static final String SECONDS = " seconds";
    private static SysLogger instance = null;
    private Logger logger = null;

    /**
     * Returns the singleton instance of SysLogger.
     * If the instance is null, it initializes it.
     *
     * @return the singleton instance of SysLogger
     */
    public static synchronized SysLogger getInstance() {
        if (instance == null) {
            instance = new SysLogger();
        }
        return instance;
    }

    /**
     * Returns the logger instance.
     * If the logger is null, it initializes it using the logging.properties file.
     *
     * @return the logger instance
     */
    public Logger getLogger() {
        if (logger == null) {
            try(InputStream inputStream = getClass().getClassLoader().getResourceAsStream("logging.properties")){
                LogManager.getLogManager().readConfiguration(inputStream);
                this.logger = Logger.getLogger(SysLogger.class.getSimpleName());
            } catch (IOException ignored) {
                System.exit(-1);
            }
        }
        return this.logger;
    }

}
