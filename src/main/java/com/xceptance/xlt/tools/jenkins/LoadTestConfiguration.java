package com.xceptance.xlt.tools.jenkins;

import com.xceptance.xlt.tools.jenkins.config.AgentControllerConfig;

public interface LoadTestConfiguration
{

    public AgentControllerConfig getAgentControllerConfig();

    public Integer getInitialResponseTimeout();

    public String getPathToTestSuite();

    public String getTestPropertiesFile();

    public String getXltConfig();

    public String getXltTemplateDir();

    public String getTimeFormatPattern();

    public boolean isShowBuildNumber();

    public int getPlotWidth();

    public int getPlotHeight();

    public String getPlotTitle();

    public String getStepId();

    public boolean isPlotVertical();

    public boolean getCreateTrendReport();

    public boolean getCreateSummaryReport();

    public int getNumberOfBuildsForTrendReport();

    public int getNumberOfBuildsForSummaryReport();

    public boolean getMarkCriticalEnabled();

    public int getMarkCriticalConditionCount();

    public int getMarkCriticalBuildCount();
}
