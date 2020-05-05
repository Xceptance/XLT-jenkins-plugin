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

import static org.junit.Assert.*;
import net.sourceforge.htmlunit.corejs.javascript.Context;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import com.xceptance.xlt.tools.jenkins.Chart.ChartLine;
import com.xceptance.xlt.tools.jenkins.Chart.ChartLineValue;

public class ChartTest
{
    public static class TestData
    {
        public static Chart<String, String> newDefaultInitializedChart()
        {
            return new Chart<String, String>("chartID", "chartTitle");
        }

        public static Chart<String, String> newDefaultInitializedChartWithAsynchronousLines()
        {
            Chart<String, String> chart = newDefaultInitializedChart();

            ChartLine<String, String> line1 = new ChartLine<String, String>("lineID1", "lineName1", 10, false);
            ChartLineValue<String, String> value1_1 = new ChartLineValue<String, String>("1", "1.1");
            ChartLineValue<String, String> value1_2 = new ChartLineValue<String, String>("3", "1.2");
            value1_1.setDataObjectValue("\"key\"", "value");
            value1_2.setDataObjectValue("\"key\"", "value");

            line1.addLineValue(value1_1);
            line1.addLineValue(value1_2);

            ChartLine<String, String> line2 = new ChartLine<String, String>("lineID2", "lineName2", 10, false);
            ChartLineValue<String, String> value2_1 = new ChartLineValue<String, String>("1", "2.1");
            ChartLineValue<String, String> value2_2 = new ChartLineValue<String, String>("2", "2.2");
            ChartLineValue<String, String> value2_3 = new ChartLineValue<String, String>("4", "2.3");
            value2_1.setDataObjectValue("\"key\"", "value");
            value2_2.setDataObjectValue("\"key2_2\"", "value");
            value2_3.setDataObjectValue("\"key\"", "value2_2");

            line2.addLineValue(value2_1);
            line2.addLineValue(value2_2);
            line2.addLineValue(value2_3);

            chart.getLines().add(line1);
            chart.getLines().add(line2);

            return chart;
        }
    }

    @Test
    public final void GeneratedJsDataString_DefaultInitializedChartWithAsynchronousLines_MatchExpectedDataStringPattern()
    {
        Chart<String, String> chart = TestData.newDefaultInitializedChartWithAsynchronousLines();
        String generatedJsDataString = chart.getDataString("function(){ return \"My Test Formatter\"}");
        String expectedDataStringPattern = "[{data:[[1,1.1],[3,1.2]],mouse:{trackFormatter:function(o){ return (function(){ return \"My Test Formatter\"})(\"lineName1\", o, xData);},},label:\"lineName1\",},{data:[[1,2.1],[2,2.2],[4,2.3]],mouse:{trackFormatter:function(o){ return (function(){ return \"My Test Formatter\"})(\"lineName2\", o, xData);},},label:\"lineName2\",},]";

        assertEquals("Generated js data string does not match the expected data string pattern.", expectedDataStringPattern,
                     generatedJsDataString);
    }

    @Test
    public final void GeneratedJsDataString_DefaultInitializedChartWithAsynchronousLines_IsCompilableJS()
    {
        Chart<String, String> chart = TestData.newDefaultInitializedChartWithAsynchronousLines();
        String generatedJsDataString = chart.getDataString("function(){ return \"My Test Formatter\"}");

        Context jsContext = Context.enter();
        jsContext.setLanguageVersion(Context.VERSION_1_0);
        jsContext.compileString(generatedJsDataString, "dataString", 0, null);
    }

    @Test
    public final void GeneratedXDataString_DefaultInitializedChartWithAsynchronousLines_MatchExpectedDataStringPattern()
    {
        Chart<String, String> chart = TestData.newDefaultInitializedChartWithAsynchronousLines();
        String generatedXDataString = chart.getXData();
        String expectedDataStringPattern = "{\"1\":{\"key\":value},\"3\":{\"key\":value},\"2\":{\"key2_2\":value},\"4\":{\"key\":value2_2},}";

        assertEquals("Generated xData json string does not match the expected data string pattern.", expectedDataStringPattern,
                     generatedXDataString);
    }

    @Test
    public final void GeneratedXDataString_DefaultInitializedChartWithAsynchronousLines_IsValidJsonStringWithUniqueEntryKeys()
        throws JSONException
    {
        Chart<String, String> chart = TestData.newDefaultInitializedChartWithAsynchronousLines();
        String generatedXDataString = chart.getXData();

        new JSONObject(generatedXDataString);
    }

    @Test
    public final void GeneratedXDataString_DefaultInitializedChartWithAsynchronousLines_JsonHasFourEntries() throws JSONException
    {
        Chart<String, String> chart = TestData.newDefaultInitializedChartWithAsynchronousLines();
        String generatedXDataString = chart.getXData();

        JSONObject json = new JSONObject(generatedXDataString);
        int currentJsonEntryCount = JSONObject.getNames(json).length;
        int expectedJsonEntryCount = 4;

        assertSame("The json object should contain 4 entries.", expectedJsonEntryCount, currentJsonEntryCount);
    }

    @Test
    public final void GeneratedXDataString_DefaultInitializedChartWithAsynchronousLines_JsonHasExpectedEntries() throws JSONException
    {
        Chart<String, String> chart = TestData.newDefaultInitializedChartWithAsynchronousLines();
        String generatedXDataString = chart.getXData();

        JSONObject json = new JSONObject(generatedXDataString);
        for (int i = 1; i <= 4; i++)
        {
            if (!json.has("" + i))
            {
                fail("The xData json is missing the entry for key: " + i + " got data: <" + generatedXDataString + ">");
            }
        }
    }

}
