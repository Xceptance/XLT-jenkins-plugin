package com.xceptance.xlt.tools.jenkins;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;

import org.apache.commons.io.FileUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Result;
import hudson.model.AbstractProject;

public class XltChartAction implements Action
{

    private List<Chart<Integer, Double>> charts;

    private int plotWidth;

    private int plotHeight;

    private String title;

    private String builderID;

    private boolean isPlotVertical;

    private AbstractProject<?, ?> project;

    public XltChartAction(AbstractProject<?, ?> project, List<Chart<Integer, Double>> charts, int plotWidth, int plotHeight, String title,
                          String builderID, boolean isPlotVertical)
    {
        this.project = project;
        this.charts = charts;
        this.plotWidth = plotWidth;
        this.plotHeight = plotHeight;
        this.title = title;
        this.builderID = builderID;
        this.isPlotVertical = isPlotVertical;
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

    public AbstractProject<?, ?> getProject()
    {
        return project;
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

    public boolean isTrendReportAvailable()
    {

        File trendReportDirectory = new File(project.getRootDir() + "/trendreport/" + builderID);
        if (trendReportDirectory.isDirectory() && trendReportDirectory.listFiles().length != 0)
        {
            return true;
        }
        else
        {
            return false;
        }

    }

    // called from jelly files
    public void doTrendReport(StaplerRequest req, StaplerResponse rsp) throws MalformedURLException, ServletException, IOException
    {
        rsp.serveFile(req, new File(new File(project.getRootDir() + "/trendreport", builderID), req.getRestOfPath()).toURI().toURL());
    }

    public boolean isSummaryReportAvailable()
    {
        File summaryReport = new File(new File(project.getRootDir(), "summaryReport"), builderID);
        return summaryReport.exists() && summaryReport.isDirectory() && summaryReport.listFiles().length > 0;
    }

    public void doSummaryReport(StaplerRequest req, StaplerResponse rsp) throws MalformedURLException, ServletException, IOException
    {
        rsp.serveFile(req, new File(new File(new File(project.getRootDir(), "summaryReport"), builderID), req.getRestOfPath()).toURI()
                                                                                                                              .toURL());
    }

}
