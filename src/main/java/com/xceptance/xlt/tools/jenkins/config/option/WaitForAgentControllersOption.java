package com.xceptance.xlt.tools.jenkins.config.option;

import org.kohsuke.stapler.DataBoundConstructor;

public class WaitForAgentControllersOption
{
    private Integer initialResponseTimeout;

    private boolean checked;

    @DataBoundConstructor
    public WaitForAgentControllersOption(Integer initialResponseTimeout)
    {
        this.initialResponseTimeout = initialResponseTimeout;
    }

    public WaitForAgentControllersOption()
    {
        this(null);
    }

    public Integer getInitialResponseTimeout()
    {
        return initialResponseTimeout;
    }

    public Integer getInitialResponseTimeout(int defaultIfNull)
    {
        return initialResponseTimeout != null ? initialResponseTimeout : defaultIfNull;
    }

    public void setInitialResponseTimeout(Integer initialResponseTimeout)
    {
        this.initialResponseTimeout = initialResponseTimeout;
    }

    public boolean getChecked()
    {
        return checked;
    }

    public void setChecked(boolean checked)
    {
        this.checked = checked;
    }

}
