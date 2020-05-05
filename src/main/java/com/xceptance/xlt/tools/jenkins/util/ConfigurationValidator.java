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
package com.xceptance.xlt.tools.jenkins.util;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.xceptance.xlt.tools.jenkins.PlotValuesConfiguration.CONFIG_PLOT_PARAMETER;
import com.xceptance.xlt.tools.jenkins.PlotValuesConfiguration.CONFIG_SECTIONS_PARAMETER;
import com.xceptance.xlt.tools.jenkins.PlotValuesConfiguration.CONFIG_VALUE_PARAMETER;
import com.xceptance.xlt.tools.jenkins.util.ValidationUtils.Flags;

import static com.xceptance.xlt.tools.jenkins.util.Helper.getErrorMessage;
import static com.xceptance.xlt.tools.jenkins.util.ValidationUtils.validateNumber;

import hudson.FilePath;
import hudson.util.FormValidation;

public final class ConfigurationValidator
{
    private ConfigurationValidator()
    {
        // Empty
    }

    public static FormValidation validateTestProperties(String pathToTestProperties)
    {
        pathToTestProperties = Helper.environmentResolve(pathToTestProperties);
        if (StringUtils.isNotBlank(pathToTestProperties))
        {
            final File file = new File(pathToTestProperties);
            final FilePath filePath = new FilePath(file);

            final String remote = filePath.getRemote();
            if (StringUtils.isBlank(FilenameUtils.getName(pathToTestProperties)))
            {
                return FormValidation.error("The test properties file path must specify a file, not a directory. (" + remote + ")");
            }
            return FormValidation.ok("(<testSuite>/config/" + remote + ")");
        }
        return FormValidation.ok();
    }

    public static FormValidation validateMCProperties(String pathToPropertiesFile)
    {
        pathToPropertiesFile = Helper.environmentResolve(pathToPropertiesFile);
        if (StringUtils.isNotBlank(pathToPropertiesFile))
        {
            final File file = new File(pathToPropertiesFile);
            final FilePath filePath = new FilePath(file);

            final String remote = filePath.getRemote();
            if (StringUtils.isBlank(FilenameUtils.getName(pathToPropertiesFile)))
            {
                return FormValidation.error("Path to mastercontroller override properties file must specify a file, not a directory. (" + remote + ")");
            }
            return FormValidation.ok("(<testSuite>/config/" + remote + ") or (" + remote + ")");
        }
        return FormValidation.ok();
    }

    public static FormValidation validateStepId(final String stepId)
    {
        if (StringUtils.isBlank(stepId))
        {
            return FormValidation.validateRequired(stepId);
        }

        if (Pattern.matches("^[a-zA-Z0-9_\\-]+$", stepId))
        {
            return FormValidation.ok();
        }

        return FormValidation.error("Step identifier must not contain characters other than 'a'-'z', 'A'-'Z', '0'-'9', '-' and '_'.");
    }

    public static FormValidation validateXltConfig(final String configJson)
    {
        if (StringUtils.isBlank(configJson))
            return FormValidation.ok("The default config will be used for empty field.");

        JSONObject validConfig;
        try
        {
            validConfig = new JSONObject(configJson);
        }
        catch (JSONException e)
        {
            return FormValidation.error("Invalid JSON (" + getErrorMessage(e) + ")");
        }

        try
        {
            List<String> valueIDs = new ArrayList<String>();
            Map<String, String> valuePlotIDs = new HashMap<String, String>();
            JSONArray validValues = validConfig.getJSONArray(CONFIG_SECTIONS_PARAMETER.values.name());
            for (int i = 0; i < validValues.length(); i++)
            {
                JSONObject eachValue = validValues.optJSONObject(i);
                String id = null;
                try
                {
                    id = eachValue.getString(CONFIG_VALUE_PARAMETER.id.name());
                    if (StringUtils.isBlank(id))
                        return FormValidation.error("Value id is empty. (value index: " + i + ")");
                    if (valueIDs.contains(id))
                        return FormValidation.error("value id already exists. (value id: " + id + ")");
                    valueIDs.add(id);

                    String path = eachValue.getString(CONFIG_VALUE_PARAMETER.xPath.name());
                    if (StringUtils.isBlank(path))
                        return FormValidation.error("Value xPath is empty. (value id: " + id + ")");

                    try
                    {
                        XPathFactory.newInstance().newXPath().compile(path);
                    }
                    catch (XPathExpressionException e)
                    {
                        return FormValidation.error("Invalid xPath. (value id:" + id + ") - " + getErrorMessage(e));
                    }

                    String condition = eachValue.getString(CONFIG_VALUE_PARAMETER.condition.name());
                    if (StringUtils.isNotBlank(condition))
                    {
                        try
                        {
                            XPathFactory.newInstance().newXPath().compile(path + condition);
                        }
                        catch (XPathExpressionException e)
                        {
                            return FormValidation.error("Condition does not form a valid xPath. (value id:" + id + ") - " +
                                                        getErrorMessage(e));
                        }
                    }

                    String valuePlotID = eachValue.getString(CONFIG_VALUE_PARAMETER.plotID.name());
                    if (StringUtils.isNotBlank(valuePlotID))
                    {
                        valuePlotIDs.put(id, valuePlotID);
                    }

                    eachValue.getString(CONFIG_VALUE_PARAMETER.name.name());
                }
                catch (JSONException e)
                {
                    return FormValidation.error("Missing value JSON section. (value index: " + i + " " +
                                                (id != null ? ("value id: " + id) : "") + ") - " + getErrorMessage(e));
                }
            }

            List<String> plotIDs = new ArrayList<String>();
            JSONArray validPlots = validConfig.getJSONArray(CONFIG_SECTIONS_PARAMETER.plots.name());
            for (int i = 0; i < validPlots.length(); i++)
            {
                JSONObject eachPlot = validPlots.getJSONObject(i);
                String id = null;
                try
                {
                    eachPlot.getString(CONFIG_PLOT_PARAMETER.id.name());
                    id = eachPlot.getString(CONFIG_PLOT_PARAMETER.id.name());
                    if (StringUtils.isBlank(id))
                        return FormValidation.error("Plot id is empty. (plot index: " + i + ")");
                    if (plotIDs.contains(id))
                        return FormValidation.error("Plot id already exists. (plot id: " + id + ")");
                    plotIDs.add(id);

                    eachPlot.getString(CONFIG_PLOT_PARAMETER.title.name());
                    String buildCount = eachPlot.getString(CONFIG_PLOT_PARAMETER.buildCount.name());
                    if (StringUtils.isNotBlank(buildCount))
                    {
                        double number = -1;
                        try
                        {
                            number = Double.valueOf(buildCount);
                        }
                        catch (NumberFormatException e)
                        {
                            return FormValidation.error("Plot buildCount is not a number. (plot id: " + id + ")");
                        }
                        if (number < 1)
                        {
                            return FormValidation.error("Plot buildCount must be positive. (plot id: " + id + ")");
                        }
                        if (number != (int) number)
                        {
                            return FormValidation.error("Plot buildCount must be an integral number. (plot id: " + id + ")");
                        }
                    }
                    String plotEnabled = eachPlot.getString(CONFIG_PLOT_PARAMETER.enabled.name());
                    if (!("yes".equals(plotEnabled) || "no".equals(plotEnabled)))
                    {
                        return FormValidation.error("Invalid value for plot enabled. Only yes or no is allowed. (plot id: " + id + ")");
                    }

                    String showNoValues = eachPlot.getString(CONFIG_PLOT_PARAMETER.showNoValues.name());
                    if (!("yes".equals(showNoValues) || "no".equals(showNoValues)))
                    {
                        return FormValidation.error("Invalid value for plot parameter \"showNoValues\". Only yes or no is allowed. (plot id: " +
                                                    id + ")");
                    }
                }
                catch (JSONException e)
                {
                    return FormValidation.error("Missing plot JSON section. (plot index: " + i + " " +
                                                (id != null ? ("plot id: " + id) : "") + ") - " + getErrorMessage(e));
                }
            }

            for (Entry<String, String> eachEntry : valuePlotIDs.entrySet())
            {
                if (!plotIDs.contains(eachEntry.getValue()))
                {
                    return FormValidation.error("Missing plot config for plot id:" + eachEntry.getValue() + " at value id: " +
                                                eachEntry.getKey() + ".");
                }
            }
        }
        catch (JSONException e)
        {
            return FormValidation.error("Missing JSON section (" + getErrorMessage(e) + ")");
        }

        return FormValidation.ok();

    }

    public static FormValidation validatePlotWidth(String value)
    {
        return validateNumber(value, 0, null, Flags.IGNORE_BLANK_VALUE, Flags.IGNORE_MAX, Flags.IS_INTEGER);
    }

    public static FormValidation validateTimeFormat(String value)
    {
        SimpleDateFormat format = new SimpleDateFormat();
        if (StringUtils.isNotBlank(value))
        {
            try
            {
                format = new SimpleDateFormat(value);
            }
            catch (Exception e)
            {
                return FormValidation.error("Invalid time format pattern (" + getErrorMessage(e) + ")");
            }
        }
        return FormValidation.ok("Preview: " + format.format(new Date()));

    }

    public static FormValidation validateXltTemplateDir(String templateDir)
    {
        if (StringUtils.isBlank(templateDir))
        {
            return FormValidation.error("Please enter a valid directory path.");
        }

        final FilePath filePath = Helper.resolvePath(templateDir);

        final String remote = filePath.getRemote();
        try
        {
            if (!filePath.exists())
            {
                return FormValidation.error("The specified directory does not exist. (" + remote + ")");
            }
        }
        catch (Exception e)
        {
            return FormValidation.warning("Can not check if the directory exists. (" + remote + ")");
        }

        try
        {
            if (!filePath.isDirectory())
            {
                return FormValidation.error("The path must specified a directory, not a file. (" + remote + ")");
            }
        }
        catch (Exception e)
        {
            return FormValidation.warning("Can not check if the path specifies a directory. (" + remote + ")");
        }
        return FormValidation.ok("(" + remote + ")");

    }

    public static FormValidation validateTestSuitePath(String value)
    {
        if (StringUtils.isBlank(value))
        {
            return FormValidation.ok("(<workspace>/)");
        }

        final String resolvedPath = Helper.environmentResolve(value);
        final FilePath filePath = new FilePath(new File(resolvedPath));
        final String remotePath = filePath.getRemote();
        if (!StringUtils.isBlank(FilenameUtils.getExtension(resolvedPath)))
        {
            return FormValidation.error("The path must specify a directory, not a file. (" + remotePath + ")");
        }
        return FormValidation.ok("(<workspace>/" + remotePath + ") or (" + remotePath + ")");

    }

    public static FormValidation validateDiffReportBaseline(String value)
    {
        if (StringUtils.isBlank(value))
        {
            return FormValidation.ok("Will use report of last run with same step identifier");
        }
        if (value.matches("^#[1-9]\\d*$"))
        {
            return FormValidation.ok("Will use report of run " + value + " with same step identifier");
        }
        final String resolvedPath = Helper.environmentResolve(value);
        final FilePath filePath = new FilePath(new File(resolvedPath));
        final String remotePath = filePath.getRemote();
        if (!StringUtils.isBlank(FilenameUtils.getExtension(resolvedPath)))
        {
            return FormValidation.error("The path must specify a directory, not a file. (" + remotePath + ")");
        }
        return FormValidation.ok("(<workspace>/" + remotePath + ") or (" + remotePath + ")");
    }

    public static FormValidation validateDiffReportCriteriaFile(String value)
    {
        value = Helper.environmentResolve(value);
        if (StringUtils.isNotBlank(value))
        {
            final FilePath filePath = new FilePath(new File(value));
            final String remote = filePath.getRemote();
            if (StringUtils.isBlank(FilenameUtils.getName(value)))
            {
                return FormValidation.error("The criteria file path must specify a file, not a directory. (" + remote + ")");
            }
            return FormValidation.ok("(<workspace>/" + remote + ") or (" + remote + ")");
        }
        return FormValidation.ok();
    }
}
