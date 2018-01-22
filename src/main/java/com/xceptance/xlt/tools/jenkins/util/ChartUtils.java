package com.xceptance.xlt.tools.jenkins.util;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;

import com.xceptance.xlt.tools.jenkins.Chart;
import com.xceptance.xlt.tools.jenkins.Chart.ChartLine;
import com.xceptance.xlt.tools.jenkins.Chart.ChartLineValue;
import com.xceptance.xlt.tools.jenkins.PlotValuesConfiguration;
import com.xceptance.xlt.tools.jenkins.PlotValuesConfiguration.Plot;
import com.xceptance.xlt.tools.jenkins.logging.LOGGER;

public final class ChartUtils
{
    private ChartUtils()
    {

    }

    public static interface ChartLineListener
    {
        public void onValueAdded(final ChartLineValue<Integer, Double> value);
    }

    public static List<Chart<Integer, Double>> xml2Charts(Document document, PlotValuesConfiguration config, ChartLineListener listener)
    {
        final List<Chart<Integer, Double>> charts = new ArrayList<>();
        if (document != null && config != null)
        {
            for (final Plot plot : config.getPlots())
            {
                final Chart<Integer, Double> chart = createChart(plot, config);
                if (chart == null)
                {
                    continue;
                }

                boolean addedValueToChart = false;
                for (String eachValueID : plot.getValueRefs())
                {
                    ChartLine<Integer, Double> line = chart.getLine(eachValueID);
                    if (line == null)
                    {
                        LOGGER.debug("No such value for ID: " + eachValueID);
                        continue;
                    }

                    ChartLineValue<Integer, Double> lineValue = null;

                    final String xPath = config.getValueById(eachValueID).getXpath();
                    final Double number = XmlUtils.evaluateXPath(document, xPath, Double.class);
                    if (number == null || number.isNaN())
                    {
                        LOGGER.warn(String.format("Value is not a number. (ID: \"%s\", XPath: \"%s\"", eachValueID, xPath));
                    }
                    else
                    {
                        lineValue = new ChartLineValue<Integer, Double>(chart.getXIndex(), number.doubleValue());

                    }
                    if (lineValue == null && line.getShowNoValues())
                    {
                        lineValue = new ChartLineValue<Integer, Double>(chart.getXIndex(), 0.0);
                    }

                    if (lineValue != null)
                    {
                        line.addLineValue(lineValue);
                        if (listener != null)
                        {
                            listener.onValueAdded(lineValue);
                        }

                        addedValueToChart = true;
                    }
                }

                if (addedValueToChart)
                {
                    chart.nextXIndex();
                }

                // finally, add chart to list
                charts.add(chart);

            }

        }

        return charts;
    }

    public static List<Chart<Integer, Double>> getEnabledCharts(final List<Chart<Integer, Double>> charts,
                                                                final PlotValuesConfiguration config)
    {
        List<Chart<Integer, Double>> enabledCharts = new ArrayList<Chart<Integer, Double>>();
        for (Chart<Integer, Double> eachChart : charts)
        {
            final Plot p = config.getPlotById(eachChart.getChartID());
            if (p != null && p.isEnabled())
            {
                enabledCharts.add(eachChart);
            }
        }
        return enabledCharts;
    }

    public static Chart<Integer, Double> createChart(final Plot plotConfig, final PlotValuesConfiguration config)
    {
        Chart<Integer, Double> chart = null;
        if (plotConfig != null)
        {
            final int maxCount = Math.min(Math.max(1, plotConfig.getBuildCount()), Integer.MAX_VALUE);

            chart = new Chart<Integer, Double>(plotConfig.getId(), plotConfig.getTitle());
            for (final String eachValueID : plotConfig.getValueRefs())
            {
                final String lineName = config.getValueById(eachValueID).getName();
                final ChartLine<Integer, Double> line = new ChartLine<Integer, Double>(eachValueID, lineName, maxCount,
                                                                                       plotConfig.isShowNoValues());
                chart.getLines().add(line);
            }
        }

        return chart;
    }

    public static int getMaxBuildCount(PlotValuesConfiguration config)
    {
        int maxBuildCount = 1;
        if (config != null)
        {
            for (final Plot p : config.getPlots())
            {
                maxBuildCount = Math.max(maxBuildCount, p.getBuildCount());
            }
        }
        return maxBuildCount;
    }

}
