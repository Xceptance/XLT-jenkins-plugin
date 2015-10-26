package com.xceptance.xlt.tools.jenkins;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import hudson.FilePath;
import hudson.model.Action;
import hudson.model.AbstractBuild;

public class XltRecorderAction implements Action
{
    public String reportPath;

    public AbstractBuild<?, ?> build;

    private List<CriterionResult> failedAlerts;

    private List<TestCaseInfo> failedTestCases;

    private String builderID;

    private String reportURL;

    public static String URL_NAME = "xltResult";

    public static String RELATIVE_REPORT_URL = URL_NAME + "/report/";

    public XltRecorderAction(AbstractBuild<?, ?> build, List<CriterionResult> failedAlerts, String builderID, String reportURL,
                             List<TestCaseInfo> failedTestCases)
    {
        this.build = build;
        this.failedAlerts = failedAlerts;
        this.builderID = builderID;
        this.reportURL = reportURL;
        this.failedTestCases = failedTestCases;
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
        return failedTestCases;
    }

    public String getBuildNumber()
    {
        return String.valueOf(build.number);
    }

    public String getConditionMessage()
    {
        String message = CriterionResult.getFormattedConditionMessage("Failed Conditions", getFailedAlerts());
        message += "\n";
        message += CriterionResult.getFormattedConditionMessage("Errors", getErrorAlerts());

        return message;
    }

    public void doReport(StaplerRequest request, StaplerResponse response)
        throws MalformedURLException, ServletException, IOException, InterruptedException
    {
        response.serveFile(request,
                           new FilePath(new File(new File(build.getArtifactsDir().getAbsolutePath()), request.getRestOfPath())).toURI()
                                                                                                                               .toURL());
    }
}
