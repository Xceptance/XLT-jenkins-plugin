package com.xceptance.xlt.tools.jenkins;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.lookup.StrLookup;

@Plugin(name = "xlt", category = "Lookup")
public class LogContextLookup implements StrLookup
{

    public String lookup(String key)
    {
        return LoadTestBuilder.getLoggerLookup(key);
    }

    public String lookup(LogEvent event, String key)
    {
        return lookup(key);
    }

}
