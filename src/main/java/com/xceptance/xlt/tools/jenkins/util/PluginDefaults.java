package com.xceptance.xlt.tools.jenkins.util;

import java.io.File;
import java.net.URISyntaxException;

import com.xceptance.xlt.tools.jenkins.logging.LOGGER;

import hudson.FilePath;

public final class PluginDefaults
{
    private PluginDefaults()
    {
        // Empty
    }


    public static final int PLOT_HEIGHT = 250;

    public static final int PLOT_WIDTH = 400;

    public static final String PLOT_TITLE = "";

    public static final int HISTORYSIZE_TRENDREPORT = 50;

    public static final int HISTORYSIZE_SUMMARYREPORT = 5;

    public static final int INITIAL_RESPONSE_TIMEOUT = 360;

    public static String getXltConfig()
    {
        try
        {
            return getXltConfigFile().readToString();
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to read default XLT config", e);
        }
        return null;
    }

    private static FilePath getXltConfigFile() throws URISyntaxException
    {   
        return new FilePath(new File(new File(Helper.getBaseResourceURI()), "xltConfig.json"));
    }

}
