package com.xceptance.xlt.tools.jenkins;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import hudson.FilePath;
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.Run;

public class XltChartProjectAction implements Action
{
    private final Job<?, ?> job;

    private final String stepId;

    public XltChartProjectAction(final Job<?, ?> job, final String stepId)
    {
        this.job = job;
        this.stepId = stepId;
    }

    @Override
    public String getIconFileName()
    {
        return null;
    }

    @Override
    public String getDisplayName()
    {
        final XltChartAction lastBuildAction = getLastBuildAction();
        if (lastBuildAction != null)
        {
            return lastBuildAction.getTitle();
        }
        return null;
    }

    @Override
    public String getUrlName()
    {
        final XltChartAction lastBuildAction = getLastBuildAction();
        if (lastBuildAction != null)
        {
            return "xltChart" + lastBuildAction.getStepId();
        }
        return null;
    }

    public XltChartAction getLastBuildAction()
    {
        Run<?, ?> r = job.getLastSuccessfulBuild();

        Run<?, ?> run = job.getLastBuild();
        while (run != null)
        {
            final XltChartAction a = run.getAction(XltChartAction.class);
            if (a != null && !run.isBuilding())
            {
                final String stId = a.getStepId();
                if (stId != null && stId.equals(stepId))
                {
                    return a;
                }
            }
            if (run == r)
            {
                return null;
            }
            run = run.getPreviousBuild();
        }
        return null;
    }

    public boolean isTrendReportAvailable() throws IOException, InterruptedException
    {
        final XltChartAction action = getLastBuildAction();
        if (action != null && action.isTrendReportEnabled())
        {
            final FilePath trendReportDirectory = new FilePath(new File(job.getRootDir(), "trendReport")).child(action.getStepId());
            if (trendReportDirectory.exists() && trendReportDirectory.isDirectory() && trendReportDirectory.list().size() > 0)
            {
                return true;
            }
        }

        return false;
    }

    public void doTrendReport(StaplerRequest req, StaplerResponse rsp)
        throws MalformedURLException, ServletException, IOException, InterruptedException
    {
        FilePath trendReportDirectory = null;
        final XltChartAction action = getLastBuildAction();
        if (action != null)
        {
            trendReportDirectory = new FilePath(new File(new File(new File(job.getRootDir(), "trendReport"), action.getStepId()),
                                                         req.getRestOfPath()));
        }

        if (trendReportDirectory != null)
        {
            rsp.serveFile(req, trendReportDirectory.toURI().toURL());
        }
        else
        {
            rsp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    public boolean isSummaryReportAvailable() throws IOException, InterruptedException
    {
        final XltChartAction action = getLastBuildAction();
        if (action != null && action.isSummaryReportEnabled())
        {
            final FilePath summaryReport = new FilePath(new File(job.getRootDir(), "summaryReport")).child(action.getStepId());
            if (summaryReport.exists() && summaryReport.isDirectory() && summaryReport.list().size() > 0)
            {
                return true;
            }
        }
        return false;
    }

    public void doSummaryReport(StaplerRequest req, StaplerResponse rsp)
        throws MalformedURLException, ServletException, IOException, InterruptedException
    {
        FilePath summaryReport = null;
        final XltChartAction action = getLastBuildAction();
        if (action != null)
        {
            summaryReport = new FilePath(new File(new File(new File(job.getRootDir(), "summaryReport"), action.getStepId()),
                                                  req.getRestOfPath()));
        }
        if (summaryReport != null)
        {
            rsp.serveFile(req, summaryReport.toURI().toURL());
        }
        else
        {
            rsp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

}
