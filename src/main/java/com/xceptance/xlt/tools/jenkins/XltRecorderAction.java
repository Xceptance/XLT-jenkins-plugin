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

    private List<CriteriaResult> failedAlerts;

    private String builderID;

    private String reportURL;

    public static String URL_NAME = "xltResult";

    public static String RELATIVE_REPORT_URL = URL_NAME + "/report/";

    public XltRecorderAction(AbstractBuild<?, ?> build, List<CriteriaResult> failedAlerts, String builderID, String reportURL)
    {
        this.build = build;
        this.failedAlerts = failedAlerts;
        this.builderID = builderID;
        this.reportURL = reportURL;
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

    public List<CriteriaResult> getAlerts()
    {
        return failedAlerts;
    }

    public List<CriteriaResult> getFailedAlerts()
    {
        List<CriteriaResult> failed = new ArrayList<CriteriaResult>();
        for (CriteriaResult eachAlert : failedAlerts)
        {
            if (eachAlert.getType() == CriteriaResult.Type.FAILED)
            {
                failed.add(eachAlert);
            }
        }
        return failed;
    }

    public List<CriteriaResult> getErrorAlerts()
    {
        List<CriteriaResult> errors = new ArrayList<CriteriaResult>();
        for (CriteriaResult eachError : failedAlerts)
        {
            if (eachError.getType() == CriteriaResult.Type.ERROR)
            {
                errors.add(eachError);
            }
        }
        return errors;
    }

    public String getBuildNumber()
    {
        return String.valueOf(build.number);
    }

    public String getConditionMessage()
    {
        String message = CriteriaResult.getFormattedConditionMessage("Failed Conditions", getFailedAlerts());
        message += "\n";
        message += CriteriaResult.getFormattedConditionMessage("Errors", getErrorAlerts());

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
