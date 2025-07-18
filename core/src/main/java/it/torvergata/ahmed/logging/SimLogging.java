package it.torvergata.ahmed.logging;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;
import java.util.logging.Logger;


/**
 * Utility Logging class
 */
public class SimLogging {


    private static SimLogging instance = null;
    private Logger logger = null;

    private SimLogging() {}

    /**
     * Static method used to retrieve the logger
     *
     * @return the instance of the logger
     */
    public static synchronized SimLogging getInstance() {
        if (instance == null) {
            instance = new SimLogging();
        }
        return instance;
    }

    /**
     * Method that return the singleton Logger
     * @return Logger
     */
    public Logger getLogger() {
        if (logger == null) {
            try(InputStream inputStream = getClass().getClassLoader().getResourceAsStream("logging.properties")){
                LogManager.getLogManager().readConfiguration(inputStream);
                this.logger = Logger.getLogger(SimLogging.class.getSimpleName());
            } catch (IOException ignored) {
                System.exit(-1);
            }
        }
        return this.logger;
    }
}
