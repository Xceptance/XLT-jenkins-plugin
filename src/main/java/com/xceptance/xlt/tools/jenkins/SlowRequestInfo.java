package com.xceptance.xlt.tools.jenkins;

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;

/**
 * Simple data object holding basic infos about a slow request.
 */
public class SlowRequestInfo
{
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
