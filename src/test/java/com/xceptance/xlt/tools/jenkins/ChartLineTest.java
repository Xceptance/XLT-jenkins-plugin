package com.xceptance.xlt.tools.jenkins;

import static org.junit.Assert.*;

import java.util.List;

import net.sourceforge.htmlunit.corejs.javascript.Context;

import org.junit.Test;

import com.xceptance.xlt.tools.jenkins.Chart.ChartLine;
import com.xceptance.xlt.tools.jenkins.Chart.ChartLineValue;

public class ChartLineTest
{
    public static class TestData
    {        
        public static ChartLine<String, String> newDefaultInitializedChartLine()
        {
            return new ChartLine<String, String>("lineID", "lineName", 2, false);
        }
        
        public static ChartLine<String, String> newDefaultInitializedChartLineWithCustomMaxCount(int maxCount)
        {
            return new ChartLine<String, String>("lineID", "lineName", maxCount, false);
        }

        public static ChartLine<String, String> newDefaultInitializedChartLineWithMaxValueEntries()
        {
            ChartLine<String, String> line = newDefaultInitializedChartLineWithCustomMaxCount(2);
            line.addLineValue(new ChartLineValue<String, String>("x1", "y1"));
            line.addLineValue(new ChartLineValue<String, String>("x2", "y2"));

            return line;
        }                
    }
    
    /**
     * #2149 Negative built count in plot configuration ends in IndexOutOfBoundsException
     */
    @Test
    public final void AddLineValue_ChartLineWithoutValuesAndMaxCountIsZero_ChartLineIsStillEmpty()
    {
        ChartLine<String, String> line = TestData.newDefaultInitializedChartLineWithCustomMaxCount(0);

        line.addLineValue(new ChartLineValue<String, String>("nextX", "nextY"));
        
        assertEquals("ChartLine should be empty", line.getValues().size(), 0);
    }
        
    /**
     * #2149 Negative built count in plot configuration ends in IndexOutOfBoundsException
     */
    @Test
    public final void AddLineValue_ChartLineWithoutValuesAndMaxCountIsNegative_ChartLineIsStillEmpty()
    {
        ChartLine<String, String> line = TestData.newDefaultInitializedChartLineWithCustomMaxCount(-1);

        line.addLineValue(new ChartLineValue<String, String>("nextX", "nextY"));
        
        assertEquals("ChartLine should be empty", line.getValues().size(), 0);
    }
    
    @Test
    public final void AddLineValue_DefaultInitializedChartLineWithoutValueEntries_ValueCountIsTwo()
    {
        ChartLine<String, String> line = TestData.newDefaultInitializedChartLine();

        line.addLineValue(new ChartLineValue<String, String>("nextX", "nextY"));
        line.addLineValue(new ChartLineValue<String, String>("lastX", "lastY"));
        int currentValueCount = line.getValues().size();
        int expectedValueCount = 2;

        assertSame("Values not added as expected. The value count should be 2.", expectedValueCount, currentValueCount);
    }

    @Test
    public final void AddLineValue_DefaultInitializedChartLineWithMaxValueEntries_ValueIsLastItemInTheList()
    {
        ChartLine<String, String> line = TestData.newDefaultInitializedChartLineWithMaxValueEntries();

        ChartLineValue<String, String> lineValueToAdd = new ChartLineValue<String, String>("nextX", "nextY");
        line.addLineValue(lineValueToAdd);

        List<ChartLineValue<String, String>> lineValues = line.getValues();
        ChartLineValue<String, String> currentLastLineValue = lineValues.get(lineValues.size() - 1);

        assertSame("The last line item should be the same as the last added line value", lineValueToAdd, currentLastLineValue);
    }

    @Test
    public final void AddLineValue_DefaultInitializedChartLineWithMaxValueEntries_ValueCountDoesNotChange()
    {
        ChartLine<String, String> line = TestData.newDefaultInitializedChartLineWithMaxValueEntries();

        int originalValueCount = line.getValues().size();
        line.addLineValue(new ChartLineValue<String, String>("nextX", "nextY"));
        int currentValueCount = line.getValues().size();

        assertSame("When adding a value, the value count should stay the same if the maximum count was already reached.",
                   originalValueCount, currentValueCount);
    }

    @Test
    public final void GeneratedJsDataString_DefaultInitializedChartLineWithSomeValueEntries_MatchExpectedDataStringPattern()
    {
        ChartLine<String, String> line = TestData.newDefaultInitializedChartLineWithMaxValueEntries();

        String generatedDataString = line.getDataString("function(){ return \"My Test Formatter\"}");
        String expectedDataStringPattern = "{data:[[x1,y1],[x2,y2]],mouse:{trackFormatter:function(o){ return (function(){ return \"My Test Formatter\"})(\"lineName\", o, xData);},},label:\"lineName\",}";

        assertEquals("Generated js data string does not match the expected data string pattern.", expectedDataStringPattern,
                     generatedDataString);
    }

    @Test
    public final void GeneratedJsDataString_DefaultInitializedChartLineWithSomeValueEntries_IsCompilableJS()
    {
        ChartLine<String, String> line = TestData.newDefaultInitializedChartLineWithMaxValueEntries();
        String generatedDataString = line.getDataString("function(){ return \"My Test Formatter\"}");

        Context jsContext = Context.enter();
        jsContext.setLanguageVersion(Context.VERSION_1_0);
        jsContext.compileString("var a = " + generatedDataString, "dataString", 0, null);
    }

}
