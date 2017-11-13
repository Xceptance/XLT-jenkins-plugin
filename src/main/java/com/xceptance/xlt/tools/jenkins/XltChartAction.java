package com.xceptance.xlt.tools.jenkins;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;

import javax.servlet.ServletException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.model.Run;
import jenkins.model.RunAction2;

public class XltChartAction implements RunAction2
{
    private List<Chart<Integer, Double>> charts;

    private int plotWidth;

    private int plotHeight;

    private String title;

    private String builderID;

    private boolean isPlotVertical;

    private boolean isTrendReportEnabled;

    private boolean isSummaryReportEnabled;

    private transient Run<?, ?> run;

    public XltChartAction(List<Chart<Integer, Double>> charts, int plotWidth, int plotHeight, String title, String builderID,
                          boolean isPlotVertical, boolean isTrendReportEnabled, boolean isSummaryReportEnabled)
    {
        this.charts = charts;
        this.plotWidth = plotWidth;
        this.plotHeight = plotHeight;
        this.title = title;
        this.builderID = builderID;
        this.isPlotVertical = isPlotVertical;
        this.isTrendReportEnabled = isTrendReportEnabled;
        this.isSummaryReportEnabled = isSummaryReportEnabled;
    }

    public String getBuilderID()
    {
        return builderID;
    }

    public String getDisplayName()
    {
        return "XLT Chart - " + title; // no link to action page
    }

    public String getIconFileName()
    {
        return null; // no link to action page
    }

    public String getUrlName()
    {
        return "xltChart" + builderID;
    }

    public String getTitle()
    {
        return title;
    }

    // called from jelly files
    public List<Chart<Integer, Double>> getCharts()
    {
        return charts;
    }

    public void setCharts(List<Chart<Integer, Double>> charts)
    {
        this.charts = charts;
    }

    public int getPlotWidth()
    {
        return plotWidth;
    }

    public int getPlotHeight()
    {
        return plotHeight;
    }

    public boolean isPlotVertical()
    {
        return isPlotVertical;
    }

    public boolean isTrendReportEnabled()
    {
        return isTrendReportEnabled;
    }

    public boolean isSummaryReportEnabled()
    {
        return isSummaryReportEnabled;
    }

    public boolean isTrendReportAvailable() throws IOException, InterruptedException
    {

        FilePath trendReportDirectory = null;
        if (run != null)
        {
            trendReportDirectory = new FilePath(new File(new File(run.getParent().getRootDir(), "trendreport"), builderID));
        }
        if (isTrendReportEnabled && trendReportDirectory != null && trendReportDirectory.isDirectory() &&
            trendReportDirectory.list().size() != 0)
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    public void doTrendReport(StaplerRequest req, StaplerResponse rsp)
        throws MalformedURLException, ServletException, IOException, InterruptedException
    {
        rsp.serveFile(req, new FilePath(new File(new File(new File(run.getParent().getRootDir(), "trendreport"), builderID),
                                                 req.getRestOfPath())).toURI().toURL());
    }

    public boolean isSummaryReportAvailable() throws IOException, InterruptedException
    {
        FilePath summaryReport = null;
        if (run != null)
        {
            summaryReport = new FilePath(new File(new File(run.getParent().getRootDir(), "summaryReport"), builderID));
        }
        return isSummaryReportEnabled && summaryReport != null && summaryReport.exists() && summaryReport.isDirectory() &&
               summaryReport.list().size() > 0;
    }

    public void doSummaryReport(StaplerRequest req, StaplerResponse rsp)
        throws MalformedURLException, ServletException, IOException, InterruptedException
    {
        rsp.serveFile(req, new FilePath(new File(new File(new File(run.getParent().getRootDir(), "summaryReport"), builderID),
                                                 req.getRestOfPath())).toURI().toURL());
    }

    @Override
    public void onLoad(Run<?, ?> r)
    {
        run = r;
    }

    @Override
    public void onAttached(Run<?, ?> r)
    {
        run = r;
    }

    public Run<?, ?> getRun()
    {
        return run;
    }
}
