/*
 * Copyright (c) 2014-2020 Xceptance Software Technologies GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.xceptance.xlt.tools.jenkins;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class Chart<X, Y>
{
    private String chartID;

    private String title;

    private int xIndex = 0;

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

    public void nextXIndex()
    {
        xIndex++;
    }

    public int getXIndex()
    {
        return xIndex;
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
            data += iterator.next().getDataString(toolTipFormatter) + ",";
        }
        data += "]";
        return data;
    }

    public String getXData()
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

                    data += "\"" + value.xValue + "\":{" + value.getDataObjectValues() + "},";
                }
            }
        }
        data += "}";
        return data;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("ID: ").append(chartID).append(", title: ").append(title).append(", values: [");
        boolean first = true;
        for (final ChartLine<X, Y> line : lines)
        {
            if (first)
            {
                first = false;
            }
            else
            {
                sb.append(", ");
            }
            sb.append(line);
        }
        sb.append("]");

        return sb.toString();
    }

    public static class ChartLine<X, Y>
    {

        private final List<ChartLineValue<X, Y>> values = new ArrayList<Chart.ChartLineValue<X, Y>>(20);

        private final String lineID;

        private final int maxCount;

        private final String name;

        private final boolean showNoValues;

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
            values.add(value);
            if (!values.isEmpty() && values.size() > maxCount)
            {
                values.remove(0);
            }
        }

        public String getName()
        {
            return name;
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("lineID: ").append(lineID).append(", name: ").append(name).append(", values: [");
            boolean first = true;
            for(final ChartLineValue<X, Y> val : values)
            {
                if(first)
                {
                    first = false;
                }
                else
                {
                    sb.append(", ");
                }
                sb.append(val.getDataString());
            }
            sb.append("]");

            return sb.toString();
        }
    }

    public static class ChartLineValue<X, Y>
    {

        private final X xValue;

        private final Y yValue;

        private final Map<String, String> dataObjectValues = new LinkedHashMap<String, String>();

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
            if (!dataObjectValues.isEmpty())
            {
                for (Entry<String, String> eachEntry : dataObjectValues.entrySet())
                {
                    data += "," + eachEntry.getKey() + ":" + eachEntry.getValue();
                }
                data = data.substring(1);
            }

            return data;
        }

        public String getDataString()
        {
            return "[" + String.valueOf(xValue) + "," + String.valueOf(yValue) + "]";
        }

        public Y getYValue()
        {
            return yValue;
        }

        public ChartLineValue<X, Y> forNewX(final X newX)
        {
            final ChartLineValue<X, Y> val = new ChartLineValue<>(newX, yValue);
            val.dataObjectValues.putAll(dataObjectValues);

            return val;
        }
    }
}
