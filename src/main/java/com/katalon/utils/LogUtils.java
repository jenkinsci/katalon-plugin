package com.katalon.utils;

public class LogUtils {

    public static void info(Logger logger, String message) {
        if (logger == null) {
            logger = new ConsoleLogger();
        }
        logger.info(message);
    }
}
