package com.xceptance.xlt.tools.jenkins.config.option;

import org.kohsuke.stapler.DataBoundConstructor;

public class MarkCriticalOption
{
    private int markCriticalConditionCount;

    private int markCriticalBuildCount;

    @DataBoundConstructor
    public MarkCriticalOption(int markCriticalConditionCount, int markCriticalBuildCount)
    {
        this.markCriticalConditionCount = markCriticalConditionCount;
        this.markCriticalBuildCount = markCriticalBuildCount;
    }

    public int getMarkCriticalConditionCount()
    {
        return markCriticalConditionCount;
    }

    public int getMarkCriticalBuildCount()
    {
        return markCriticalBuildCount;
    }
}
