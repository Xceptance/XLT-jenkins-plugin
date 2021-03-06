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

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import com.xceptance.xlt.tools.jenkins.util.Helper;

import hudson.FilePath;
import hudson.model.Run;
import jenkins.model.RunAction2;

public class XltRecorderAction implements RunAction2
{
    private transient Run<?, ?> run;

    private final List<CriterionResult> failedAlerts;

    private final List<TestCaseInfo> failedTestCases;

    private final List<SlowRequestInfo> slowestRequests;

    private final String reportURL;

    private final String diffReportURL;

    private final String stepId;

    public static String URL_NAME = "xltResult";

    public static String RELATIVE_REPORT_URL = URL_NAME + "/report/";

    @DataBoundConstructor
    public XltRecorderAction(final String stepId, final String reportURL, final List<CriterionResult> failedAlerts,
                             final List<TestCaseInfo> failedTestCases, final List<SlowRequestInfo> slowestRequests,
                             final String diffReportURL)
    {
        this.stepId = stepId;
        this.failedAlerts = failedAlerts;
        this.reportURL = reportURL;
        this.failedTestCases = failedTestCases;
        this.slowestRequests = slowestRequests;
        this.diffReportURL = diffReportURL;
    }

    public String getIconFileName()
    {
        return null;
    }

    public String getDisplayName()
    {
        return null;
    }

    public String getUrlName()
    {
        return URL_NAME;
    }

    public String getStepId()
    {
        return stepId != null ? stepId : determineStepFromReportURL();
    }

    private String determineStepFromReportURL()
    {
        return StringUtils.substringAfterLast(StringUtils.substringBeforeLast(getReportURL(), "/report/index.html"), "/");
    }

    public String getReportURL()
    {
        return reportURL;
    }

    public String getDiffReportURL()
    {
        return diffReportURL;
    }

    public List<CriterionResult> getAlerts()
    {
        return failedAlerts;
    }

    public List<CriterionResult> getFailedAlerts()
    {
        List<CriterionResult> failed = new ArrayList<CriterionResult>();
        for (CriterionResult eachAlert : failedAlerts)
        {
            if (eachAlert.getType() == CriterionResult.Type.FAILED)
            {
                failed.add(eachAlert);
            }
        }
        return failed;
    }

    public List<CriterionResult> getErrorAlerts()
    {
        List<CriterionResult> errors = new ArrayList<CriterionResult>();
        for (CriterionResult eachError : failedAlerts)
        {
            if (eachError.getType() == CriterionResult.Type.ERROR)
            {
                errors.add(eachError);
            }
        }
        return errors;
    }

    public List<TestCaseInfo> getFailedTestCases()
    {
        return Collections.unmodifiableList(failedTestCases);
    }

    public List<SlowRequestInfo> getSlowestRequests()
    {
        return Collections.unmodifiableList(slowestRequests);
    }

    public String getConditionMessage()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append(CriterionResult.getFormattedConditionMessage("Failed Conditions", getFailedAlerts())).append('\n')
          .append(CriterionResult.getFormattedConditionMessage("Errors", getErrorAlerts()));

        return sb.toString();
    }

    public void doReport(StaplerRequest request, StaplerResponse response)
        throws MalformedURLException, ServletException, IOException, InterruptedException
    {
        final FilePath reportPath = Helper.getArtifact(run, request.getRestOfPath());
        response.serveFile(request, reportPath.toURI().toURL());
    }

    @Override
    public void onAttached(Run<?, ?> r)
    {
        this.run = r;
    }

    @Override
    public void onLoad(Run<?, ?> r)
    {
        onAttached(r);
    }
}
