package com.xceptance.xlt.tools.jenkins;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.xceptance.xlt.tools.jenkins.Chart.ChartLineValue;

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

    public static class ChartLine<X, Y>
    {

        private List<ChartLineValue<X, Y>> values = new ArrayList<Chart.ChartLineValue<X, Y>>(20);

        private String lineID;

        private int maxCount;

        public ChartLine(String lineID, int maxCount)
        {
            this.lineID = lineID;
            this.maxCount = maxCount;
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
            String data = "[";
            Iterator<ChartLineValue<X, Y>> iterator = values.iterator();
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

        public ChartLineValue(X xValue, Y yValue)
        {
            this.xValue = xValue;
            this.yValue = yValue;
        }

        public String getDataString()
        {
            return "[" + String.valueOf(xValue) + "," + String.valueOf(yValue) + "]";
        }
    }
}
