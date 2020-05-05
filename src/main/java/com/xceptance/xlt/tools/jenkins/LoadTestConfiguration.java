/*
 * Copyright (c) 2014-2020 Xceptance Software Technologies GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.xceptance.xlt.tools.jenkins;

import com.xceptance.xlt.tools.jenkins.config.AgentControllerConfig;

public interface LoadTestConfiguration
{
    public boolean getArchiveResults();

    public AgentControllerConfig getAgentControllerConfig();

    public Integer getInitialResponseTimeout();

    public String getPathToTestSuite();

    public String getAdditionalMCPropertiesFile();

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

    public boolean getCreateDiffReport();

    public String getDiffReportBaseline();

    public String getDiffReportCriteriaFile();
}
