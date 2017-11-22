package com.xceptance.xlt.tools.jenkins;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.xceptance.xlt.tools.jenkins.Chart.ChartLine;
import com.xceptance.xlt.tools.jenkins.Chart.ChartLineValue;

import hudson.model.Action;
import hudson.model.InvisibleAction;
import hudson.model.Job;
import hudson.model.Run;
import jenkins.model.RunAction2;
import jenkins.tasks.SimpleBuildStep.LastBuildAction;

public class XltChartAction extends InvisibleAction implements RunAction2, LastBuildAction
{
    private final List<Chart<Integer, Double>> charts;

    private final int plotWidth;

    private final int plotHeight;

    private final String title;

    private final String stepId;

    private final boolean isPlotVertical;

    private final boolean isTrendReportEnabled;

    private final boolean isSummaryReportEnabled;

    private final int buildCount;

    private transient Run<?, ?> run;

    public XltChartAction(List<Chart<Integer, Double>> charts, int plotWidth, int plotHeight, String title, String stepId,
                          final int buildCount, boolean isPlotVertical, boolean isTrendReportEnabled, boolean isSummaryReportEnabled)
    {
        this.charts = charts;
        this.plotWidth = plotWidth;
        this.plotHeight = plotHeight;
        this.title = title;
        this.stepId = stepId;
        this.buildCount = buildCount;
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

    public int getBuildCount()
    {
        return buildCount;
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

    private List<Run<?, ?>> getRuns()
    {
        final List<Run<?, ?>> runs = new ArrayList<>();
        Run<?, ?> r = run;
        while (r != null && (buildCount < 0 || buildCount > runs.size()))
        {
            runs.add(r);
            r = r.getPreviousBuild();
        }

        return runs;
    }

    private Chart<Integer, Double> lookupChart(final List<Chart<Integer, Double>> theCharts, final String chartID)
    {
        for (final Chart<Integer, Double> c : theCharts)
        {
            if (c.getChartID().equals(chartID))
            {
                return c;
            }
        }
        return null;
    }

    private List<Chart<Integer, Double>> initCharts()
    {
        final List<Chart<Integer, Double>> list = new ArrayList<>();
        for (final Chart<Integer, Double> c : charts)
        {
            final Chart<Integer, Double> c2 = new Chart<Integer, Double>(c.getChartID(), c.getTitle());
            list.add(c2);

            for (final ChartLine<Integer, Double> line : c.getLines())
            {

                c2.getLines()
                  .add(new ChartLine<Integer, Double>(line.getLineID(), line.getName(), line.getMaxCount(), line.getShowNoValues()));
            }
        }

        return list;
    }

    private List<XltChartAction> getActions(final Run<?, ?> run)
    {
        final List<XltChartAction> list = new ArrayList<>();
        for (final XltChartAction axn : run.getActions(XltChartAction.class))
        {
            if (stepId.equals(axn.stepId))
            {
                list.add(axn);
            }
        }
        return list;
    }

    public List<Chart<Integer, Double>> getAllCharts()
    {
        final List<Run<?, ?>> runs = getRuns();
        final List<Chart<Integer, Double>> allCharts = initCharts();
        for (int i = runs.size() - 1; i > -1; i--)
        {
            final Run<?, ?> r = runs.get(i);
            for (final XltChartAction axn : getActions(r))
            {
                for (final Chart<Integer, Double> c : axn.charts)
                {
                    final Chart<Integer, Double> match = lookupChart(allCharts, c.getChartID());
                    if (match != null)
                    {
                        boolean added = false;
                        for (final ChartLine<Integer, Double> line : c.getLines())
                        {
                            final ChartLine<Integer, Double> l = match.getLine(line.getLineID());
                            if (l != null)
                            {
                                for (final ChartLineValue<Integer, Double> val : line.getValues())
                                {
                                    final ChartLineValue<Integer, Double> theVal = val.clone(match.getXIndex());
                                    l.addLineValue(theVal);

                                    added = true;
                                }
                            }
                        }
                        if (added)
                        {
                            match.nextXIndex();
                        }
                    }
                }
            }
        }

        return allCharts;
    }
}
