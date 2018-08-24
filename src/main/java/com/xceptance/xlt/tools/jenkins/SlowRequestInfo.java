package com.xceptance.xlt.tools.jenkins;

import java.io.Serializable;

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;

/**
 * Simple data object holding basic infos about a slow request.
 */
public class SlowRequestInfo implements Serializable
{
    /** The serialVersionUID. */
    private static final long serialVersionUID = -5211214833674013358L;

    private String url;

    private String runtime;

    public SlowRequestInfo(String url, String runtime)
    {
        this.url = url;
        this.runtime = runtime;
    }

    @Whitelisted
    public String getUrl()
    {
        return url;
    }

    @Whitelisted
    public String getRuntime()
    {
        return runtime;
    }
}
