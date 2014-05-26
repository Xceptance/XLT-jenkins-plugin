package com.xceptance.xlt.tools.jenkins;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Chart<X, Y>
{
    private String chartID;

    private String title;

    private List<ChartLine<X, Y>> lines = new ArrayList<Chart.ChartLine<X, Y>>(3);

    public Chart(String chartID, String title)
    {
        this.chartID = chartID;
        this.title = title;
    }

    public String getChartID()
    {
        return chartID;
    }

    public String getTitle()
    {
        return title;
    }

    public List<ChartLine<X, Y>> getLines()
    {
        return lines;
    }

    public ChartLine<X, Y> getLine(String lineID)
    {
        for (ChartLine<X, Y> eachLine : lines)
        {
            if (eachLine.getLineID().equals(lineID))
                return eachLine;
        }
        return null;
    }

    public String getDataString()
    {
        String data = "[";
        Iterator<ChartLine<X, Y>> iterator = lines.iterator();
        while (iterator.hasNext())
        {
            data += iterator.next().getDataString();
            if (iterator.hasNext())
            {
                data += ",";
            }
        }
        data += "]";
        return data;
    }

    public String getXLabelsData()
    {
        List<X> processedValues = new ArrayList<X>();

        String data = "{";
        for (ChartLine<X, Y> eachLine : lines)
        {
            Iterator<ChartLineValue<X, Y>> iterator = eachLine.getValues().iterator();
            while (iterator.hasNext())
            {
                ChartLineValue<X, Y> value = iterator.next();
                if (!processedValues.contains(value.xValue))
                {
                    processedValues.add(value.xValue);

                    data += "\"" + value.xValue + "\":\"" + value.xLabel + "\"";
                    if (iterator.hasNext())
                    {
                        data += ",";
                    }
                }
            }
        }
        data += "}";
        return data;
    }

    public static class ChartLine<X, Y>
    {

        private List<ChartLineValue<X, Y>> values = new ArrayList<Chart.ChartLineValue<X, Y>>(20);

        private String lineID;

        private int maxCount;

        private String name;

        private Chart<X, Y> chart;

        public ChartLine(Chart<X, Y> chart, String lineID, String name, int maxCount)
        {
            this.chart = chart;
            this.lineID = lineID;
            this.maxCount = maxCount;
            this.name = name;
        }

        public String getLineID()
        {
            return lineID;
        }

        public int getMaxCount()
        {
            return maxCount;
        }

        public String getDataString()
        {
            String lineObject = "{";

            String data = "data:[";
            Iterator<ChartLineValue<X, Y>> iterator = values.iterator();
            while (iterator.hasNext())
            {
                data += iterator.next().getDataString();
                if (iterator.hasNext())
                {
                    data += ",";
                }
            }
            data += "],";

            String mouse = "mouse:{";
            mouse += "trackFormatter:function(o){ var xLabelsMap = " + chart.getXLabelsData() + "; return \"" + name +
                     ": \"+o.y+\" Date: \"+xLabelsMap[new String(parseInt(o.x))]},";
            mouse += "},";

            String label = "label:\"" + name + "\",";

            lineObject += data + mouse + label + "}";
            return lineObject;
        }

        public List<ChartLineValue<X, Y>> getValues()
        {
            return values;
        }

        public void addLineValue(ChartLineValue<X, Y> value)
        {
            if (values.size() < maxCount)
            {
                values.add(value);
            }
            else
            {
                values.remove(0);
                values.add(value);
            }
        }
    }

    public static class ChartLineValue<X, Y>
    {

        private X xValue;

        private Y yValue;

        private String xLabel;

        public ChartLineValue(X xValue, Y yValue, String xLabel)
        {
            this.xValue = xValue;
            this.yValue = yValue;
            this.xLabel = xLabel;
        }

        public String getDataString()
        {
            return "[" + String.valueOf(xValue) + "," + String.valueOf(yValue) + "]";
        }
    }
}
