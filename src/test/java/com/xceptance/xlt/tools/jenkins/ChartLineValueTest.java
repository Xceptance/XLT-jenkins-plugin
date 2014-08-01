package com.xceptance.xlt.tools.jenkins;

import static org.junit.Assert.*;
import net.sourceforge.htmlunit.corejs.javascript.Context;

import org.junit.Test;

import com.xceptance.xlt.tools.jenkins.Chart.ChartLineValue;

public class ChartLineValueTest
{
    public static class TestData
    {
        public static ChartLineValue<String, String> newDefaultInitializedChartLineValue()
        {
            return new ChartLineValue<String, String>("1", "\"text\"");
        }

        public static ChartLineValue<String, String> newDefaultInitializedChartLineValueWithOneDataEntry()
        {
            ChartLineValue<String, String> chartLineValue = newDefaultInitializedChartLineValue();
            chartLineValue.setDataObjectValue("\"key\"", "value");

            return chartLineValue;
        }

        public static ChartLineValue<String, String> newDefaultInitializedChartLineValueWithTwoDataEntries()
        {
            ChartLineValue<String, String> chartLineValue = newDefaultInitializedChartLineValueWithOneDataEntry();
            chartLineValue.setDataObjectValue("key2", "\"value2\"");

            return chartLineValue;
        }
    }

    @Test
    public final void GeneratedJSDataString_DefaultInitializedChartLineValue_MatchExpectedDataStringPattern()
    {
        final String generatedDataString = TestData.newDefaultInitializedChartLineValue().getDataString();
        final String expectedDataString = "[1,\"text\"]";

        assertEquals("Generated js data array string does not match expected data string pattern.", expectedDataString, generatedDataString);
    }

    @Test
    public final void GeneratedJSDataString_DefaultInitializedChartLineValue_IsCompilableJS()
    {
        final String generatedDataString = TestData.newDefaultInitializedChartLineValue().getDataString();

        Context jsContext = Context.enter();
        jsContext.setLanguageVersion(Context.VERSION_1_0);
        jsContext.compileString(generatedDataString, "dataString", 0, null);
    }

    @Test
    public final void GeneratedDataMappingString_DefaultInitializedChartLineValueWithNoDataEntry_ValidDataMappingFormat()
    {
        final String generatedDataMappingString = TestData.newDefaultInitializedChartLineValue().getDataObjectValues();
        final String expectedDataMappingString = "";

        assertEquals("Generated data mapping string should be an empty string.", expectedDataMappingString, generatedDataMappingString);
    }

    @Test
    public final void GeneratedDataMappingString_DefaultInitializedChartLineValueWithOneDataEntry_MatchExpectedDataStringPattern()
    {
        final String generatedDataMappingString = TestData.newDefaultInitializedChartLineValueWithOneDataEntry().getDataObjectValues();
        final String expectedDataMappingString = "\"key\":value";

        assertEquals("Generated data mapping string does not match the expected data string pattern.", expectedDataMappingString,
                     generatedDataMappingString);
    }

    @Test
    public final void GeneratedDataMappingString_DefaultInitializedChartLineValueWithTwoDataEntries_MatchExpectedDataStringPattern()
    {
        final String generatedDataMappingString = TestData.newDefaultInitializedChartLineValueWithTwoDataEntries().getDataObjectValues();
        final String expectedDataMappingString = "\"key\":value,key2:\"value2\"";

        assertEquals("Generated data mapping string does not match the expected data string pattern.", expectedDataMappingString,
                     generatedDataMappingString);
    }

}
