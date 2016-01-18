package com.xceptance.xlt.tools.jenkins.logging;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.xceptance.xlt.tools.jenkins.XltDescriptor;

public class LOGGER
{
    private static final String LOGGER_NAME = XltDescriptor.PLUGIN_NAME;

    private static final Logger LOGGER = Logger.getLogger(LOGGER_NAME);

    private LOGGER()
    {

    }

    public static void error(String msg, Throwable t)
    {
        LOGGER.log(Level.SEVERE, msg, t);
    }

    public static void info(String msg)
    {
        LOGGER.log(Level.INFO, msg);
    }

    public static void warn(String msg, Throwable t)
    {
        LOGGER.log(Level.WARNING, msg, t);
    }

    public static void warn(String msg)
    {
        LOGGER.log(Level.WARNING, msg);
    }

    public static void debug(String msg)
    {
        LOGGER.log(Level.FINE, msg);
    }
}
