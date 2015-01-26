package com.xceptance.xlt.tools.jenkins.config.option;

import org.kohsuke.stapler.DataBoundConstructor;

public class TrendReportOption
{
    private int numberOfBuildsForTrendReport;

    @DataBoundConstructor
    public TrendReportOption(int numberOfBuildsForTrendReport)
    {
        this.numberOfBuildsForTrendReport = numberOfBuildsForTrendReport;
    }

    public int getNumberOfBuildsForTrendReport()
    {
        return numberOfBuildsForTrendReport;
    }
}
