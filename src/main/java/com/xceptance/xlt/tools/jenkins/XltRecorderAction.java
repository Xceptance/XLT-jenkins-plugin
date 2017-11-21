package com.xceptance.xlt.tools.jenkins;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.servlet.ServletException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import com.xceptance.xlt.tools.jenkins.logging.LOGGER;

import hudson.FilePath;
import hudson.model.Run;
import jenkins.model.RunAction2;

public class XltRecorderAction implements RunAction2
{
    private transient Run<?, ?> run;

    private final List<CriterionResult> failedAlerts;

    private final List<TestCaseInfo> failedTestCases;

    private final List<SlowRequestInfo> slowestRequests;

    private final String builderID;

    private final String reportURL;

    public static String URL_NAME = "xltResult";

    public static String RELATIVE_REPORT_URL = URL_NAME + "/report/";

    @DataBoundConstructor
    public XltRecorderAction(final String builderID, final String reportURL, final List<CriterionResult> failedAlerts,
                             final List<TestCaseInfo> failedTestCases, final List<SlowRequestInfo> slowestRequests)
    {
        this.failedAlerts = failedAlerts;
        this.builderID = builderID;
        this.reportURL = reportURL;
        this.failedTestCases = failedTestCases;
        this.slowestRequests = slowestRequests;
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

    public String getReportURL()
    {
        return reportURL;
    }

    public String getBuilderID()
    {
        return builderID;
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
        final FilePath reportPath =  LoadTestBuilder.getArtifact(run, request.getRestOfPath());
        LOGGER.warn("reportPath: " + reportPath);
        response.serveFile(request,reportPath.toURI().toURL());
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
