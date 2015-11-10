package com.xceptance.xlt.tools.jenkins;

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

    public String getUrl()
    {
        return url;
    }

    public String getRuntime()
    {
        return runtime;
    }
}
