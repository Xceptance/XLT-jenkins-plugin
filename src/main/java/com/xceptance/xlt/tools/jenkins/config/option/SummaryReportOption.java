package com.xceptance.xlt.tools.jenkins.config.option;

import org.kohsuke.stapler.DataBoundConstructor;

public class SummaryReportOption
{
    private int numberOfBuildsForSummaryReport;

    @DataBoundConstructor
    public SummaryReportOption(int numberOfBuildsForSummaryReport)
    {
        this.numberOfBuildsForSummaryReport = numberOfBuildsForSummaryReport;
    }

    public int getNumberOfBuildsForSummaryReport()
    {
        return numberOfBuildsForSummaryReport;
    }
}
