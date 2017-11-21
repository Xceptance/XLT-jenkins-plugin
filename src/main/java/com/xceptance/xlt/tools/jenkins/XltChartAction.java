package com.xceptance.xlt.tools.jenkins;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import hudson.model.Action;
import hudson.model.InvisibleAction;
import hudson.model.Job;
import hudson.model.Run;
import jenkins.model.RunAction2;
import jenkins.tasks.SimpleBuildStep.LastBuildAction;

public class XltChartAction extends InvisibleAction implements RunAction2, LastBuildAction
{
    private List<Chart<Integer, Double>> charts;

    private int plotWidth;

    private int plotHeight;

    private String title;

    private String stepId;

    private boolean isPlotVertical;

    private boolean isTrendReportEnabled;

    private boolean isSummaryReportEnabled;

    private transient Run<?, ?> run;

    public XltChartAction(List<Chart<Integer, Double>> charts, int plotWidth, int plotHeight, String title, String stepId,
                          boolean isPlotVertical, boolean isTrendReportEnabled, boolean isSummaryReportEnabled)
    {
        this.charts = charts;
        this.plotWidth = plotWidth;
        this.plotHeight = plotHeight;
        this.title = title;
        this.stepId = stepId;
        this.isPlotVertical = isPlotVertical;
        this.isTrendReportEnabled = isTrendReportEnabled;
        this.isSummaryReportEnabled = isSummaryReportEnabled;
    }

    public String getStepId()
    {
        return stepId;
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

    @Override
    public Collection<? extends Action> getProjectActions()
    {
        final Job<?, ?> job = run.getParent();
        return Collections.singletonList(new XltChartProjectAction(job, getStepId()));
    }

}
