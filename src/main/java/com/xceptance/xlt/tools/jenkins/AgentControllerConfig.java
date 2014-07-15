package com.xceptance.xlt.tools.jenkins;

import org.kohsuke.stapler.DataBoundConstructor;

public class AgentControllerConfig
{
    public enum TYPE
    {
        embedded, list, file
    };

    public final String type;

    public final String urlFile;

    public final String urlList;

    public AgentControllerConfig()
    {
        this(TYPE.embedded.toString(), null, null);
    }

    @DataBoundConstructor
    public AgentControllerConfig(String value, String urlList, String urlFile)
    {
        this.type = value;
        this.urlList = urlList;
        this.urlFile = urlFile;
    }
}
