package com.xceptance.xlt.tools.jenkins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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

    public String getDataString(String toolTipFormatter)
    {
        String data = "[";
        Iterator<ChartLine<X, Y>> iterator = lines.iterator();
        while (iterator.hasNext())
        {
            data += iterator.next().getDataString(toolTipFormatter);
            if (iterator.hasNext())
            {
                data += ",";
            }
        }
        data += "]";
        return data;
    }

    public String getXData()
    {
        List<Integer> processedValues = new ArrayList<Integer>();

        String data = "{";
        for (ChartLine<X, Y> eachLine : lines)
        {
            Iterator<ChartLineValue<X, Y>> iterator = eachLine.getValues().iterator();
            while (iterator.hasNext())
            {
                ChartLineValue<X, Y> value = iterator.next();
                if (!processedValues.contains(value.index))
                {
                    processedValues.add(value.index);

                    data += "\"" + value.index + "\":{" + value.getDataObjectValues() + "}";
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

        private int indexCounter = 0;

        private boolean showNoValues;

        public ChartLine(String lineID, String name, int maxCount, boolean showNoValues)
        {
            this.lineID = lineID;
            this.maxCount = maxCount;
            this.name = name;
            this.showNoValues = showNoValues;
        }

        public String getLineID()
        {
            return lineID;
        }

        public int getMaxCount()
        {
            return maxCount;
        }

        public boolean getShowNoValues()
        {
            return showNoValues;
        }

        public String getDataString(String toolTipFormatter)
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
            mouse += "trackFormatter:function(o){ return (" + toolTipFormatter + ")(\"" + name + "\", o, xData);},";

            // mouse += "trackFormatter:function(o){ var xData = " + chart.getXData() + "; return (" + toolTipFormatter
            // + ")(\"" + name +
            // "\", o, xData);},";
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
            value.index = indexCounter;
            indexCounter += 1;
        }
    }

    public static class ChartLineValue<X, Y>
    {

        private X xValue;

        private Y yValue;

        private int index;

        private Map<String, String> dataObjectValues = new HashMap<String, String>();

        public ChartLineValue(X xValue, Y yValue)
        {
            this.xValue = xValue;
            this.yValue = yValue;
        }

        public void setDataObjectValue(String key, String value)
        {
            dataObjectValues.put(key, value);
        }

        public String getDataObjectValues()
        {
            String data = "";
            for (Entry<String, String> eachEntry : dataObjectValues.entrySet())
            {
                data += "," + eachEntry.getKey() + ":" + eachEntry.getValue();
            }
            data = data.substring(1);

            return data;
        }

        public String getDataString()
        {
            return "[" + String.valueOf(index) + "," + String.valueOf(yValue) + "]";
        }
    }
}
