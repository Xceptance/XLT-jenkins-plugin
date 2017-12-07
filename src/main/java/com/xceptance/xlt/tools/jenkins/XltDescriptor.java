package com.xceptance.xlt.tools.jenkins;

import static com.xceptance.xlt.tools.jenkins.util.ValidationUtils.validateNumber;

import java.io.File;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;

import com.xceptance.xlt.tools.jenkins.LoadTestBuilder.CONFIG_PLOT_PARAMETER;
import com.xceptance.xlt.tools.jenkins.LoadTestBuilder.CONFIG_SECTIONS_PARAMETER;
import com.xceptance.xlt.tools.jenkins.LoadTestBuilder.CONFIG_VALUE_PARAMETER;
import com.xceptance.xlt.tools.jenkins.config.AgentControllerConfig;
import com.xceptance.xlt.tools.jenkins.config.Embedded;
import com.xceptance.xlt.tools.jenkins.logging.LOGGER;
import com.xceptance.xlt.tools.jenkins.util.ValidationUtils.Flags;

import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AbstractProject;
import hudson.model.Items;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;

@Extension
@Symbol("xlt")
public class XltDescriptor extends BuildStepDescriptor<Builder>
{
    /**
     * The plug-in's name, i.e. the name of the HPI file.
     */
    public static final String PLUGIN_NAME = "xlt-jenkins-plugin";

    /**
     * In order to load the persisted global configuration, you have to call load() in the constructor.
     */
    public XltDescriptor()
    {
        super(LoadTestBuilder.class);

        load();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public boolean isApplicable(Class<? extends AbstractProject> aClass)
    {
        // Indicates that this builder can be used with all kinds of project types
        return true;
    }

    /**
     * This human readable name is used in the configuration screen.
     */
    @Override
    public String getDisplayName()
    {
        return "Run a load test with XLT";
    }

    public String getErrorMessage(Throwable throwable)
    {
        if (throwable.getMessage() != null)
        {
            return throwable.getMessage();
        }
        else if (throwable.getCause() != null)
        {
            return getErrorMessage(throwable.getCause());
        }
        else
        {
            return "";
        }
    }

    public String getDefaultXltConfig()
    {
        try
        {
            return getXltConfigFile().readToString();
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to read default XLT config", e);
        }
        return null;
    }

    private FilePath getXltConfigFile() throws URISyntaxException
    {
        return new FilePath(new File(new File(Jenkins.getInstance().getPlugin(PLUGIN_NAME).getWrapper().baseResourceURL.toURI()),
                                     "xltConfig.json"));
    }

    public int getDefaultPlotWidth()
    {
        return 400;
    }

    public int getDefaultPlotHeight()
    {
        return 250;
    }

    public String getDefaultPlotTitle()
    {
        return "";
    }

    public boolean getDefaultIsPlotVertical()
    {
        return false;
    }

    public boolean getDefaultCreateTrendReport()
    {
        return false;
    }

    public boolean getDefaultCreateSummaryReport()
    {
        return false;
    }

    public int getDefaultNumberOfBuildsForTrendReport()
    {
        return 50;
    }

    public int getDefaultNumberOfBuildsForSummaryReport()
    {
        return 5;
    }

    public int getDefaultInitialResponseTimeout()
    {
        return 360;
    }

    public String getDefaultStepId()
    {
        return UUID.randomUUID().toString();
    }

    public AgentControllerConfig getDefaultAgentControllerConfig()
    {
        return Embedded.INSTANCE;
    }

    /**
     * Performs on-the-fly validation of the form field 'testPropertiesFile'.
     */
    public FormValidation doCheckTestPropertiesFile(@QueryParameter String value)
    {
        if (StringUtils.isNotBlank(value))
        {
            String resolvedFilePath = environmentResolve(value);
            File file = new File(resolvedFilePath);
            FilePath filePath = new FilePath(file);

            if (StringUtils.isBlank(FilenameUtils.getName(resolvedFilePath)))
            {
                return FormValidation.error("The test properties file path must specify a file, not a directory. (" + filePath.getRemote() +
                                            ")");
            }
            return FormValidation.ok("(" + "<testSuite>/config/" + filePath.getRemote() + ")");
        }
        return FormValidation.ok();
    }

    public FormValidation doCheckStepId(@QueryParameter String value)
    {
        if (StringUtils.isBlank(value))
        {
            return FormValidation.validateRequired(value);
        }

        if (Pattern.matches("^[a-zA-Z0-9_\\-]+$", value))
        {
            return FormValidation.ok();
        }

        return FormValidation.error("Step identifier must not contain characters other than 'a'-'z', 'A'-'Z', '0'-'9', '-' and '_'.");
    }

    /**
     * Performs on-the-fly validation of the form field 'parsers'.
     */
    public FormValidation doCheckXltConfig(@QueryParameter String value)
    {
        if (StringUtils.isBlank(value))
            return FormValidation.ok("The default config will be used for empty field.");

        JSONObject validConfig;
        try
        {
            validConfig = new JSONObject(value);
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
                            return FormValidation.error("Plot buildCount is a decimal number. (plot id: " + id + ")");
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

    public FormValidation doCheckPlotWidth(@QueryParameter String value)
    {
        return validateNumber(value, 0, null, Flags.IGNORE_BLANK_VALUE, Flags.IGNORE_MAX, Flags.IS_INTEGER);
    }

    public FormValidation doCheckPlotHeight(@QueryParameter String value)
    {
        return validateNumber(value, 0, null, Flags.IGNORE_BLANK_VALUE, Flags.IGNORE_MAX, Flags.IS_INTEGER);
    }

    public FormValidation doCheckTimeFormatPattern(@QueryParameter String value)
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

    public FormValidation doCheckXltTemplateDir(@QueryParameter String value)
    {
        if (StringUtils.isBlank(value))
        {
            return FormValidation.error("Please enter a valid directory path.");
        }

        String resolvedPath = environmentResolve(value);
        FilePath filePath = resolvePath(resolvedPath, Jenkins.getInstance().getRootPath());

        try
        {
            if (!filePath.exists())
            {
                return FormValidation.error("The specified directory does not exists. (" + filePath.getRemote() + ")");
            }
        }
        catch (Exception e)
        {
            return FormValidation.warning("Can not check if the directory exists. (" + filePath.getRemote() + ")");
        }

        try
        {
            if (!filePath.isDirectory())
            {
                return FormValidation.error("The path must specified a directory, not a file. (" + filePath.getRemote() + ")");
            }
        }
        catch (Exception e)
        {
            return FormValidation.warning("Can not check if the path specifies a directory. (" + filePath.getRemote() + ")");
        }
        return FormValidation.ok("(" + filePath.getRemote() + ")");
    }

    public FormValidation doCheckPathToTestSuite(@QueryParameter String value, @AncestorInPath AbstractProject<?, ?> project)
    {
        if (StringUtils.isBlank(value))
        {
            return FormValidation.ok("(<workspace>/)");
        }

        String resolvedPath = environmentResolve(value);
        FilePath filePath = new FilePath(new File(resolvedPath));
        if (!StringUtils.isBlank(FilenameUtils.getExtension(resolvedPath)))
        {
            return FormValidation.error("The path must specify a directory, not a file. (" + filePath.getRemote() + ")");
        }
        return FormValidation.ok("(<workspace>/" + filePath.getRemote() + ") or (" + filePath.getRemote() + ")");
    }

    public static String environmentResolve(String value)
    {
        if (value == null)
            return null;

        return Util.replaceMacro(value, System.getenv());
    }

    public static FilePath resolvePath(String path)
    {
        return resolvePath(path, Jenkins.getInstance().getRootPath());
    }

    public static FilePath resolvePath(String path, FilePath baseDir)
    {
        String dir = environmentResolve(path);
        if (StringUtils.isBlank(dir))
        {
            return null;
        }

        File file = new File(dir);
        FilePath filePath = new FilePath(file);
        if (!file.isAbsolute() && baseDir != null)
        {
            filePath = new FilePath(baseDir, dir);
        }
        return filePath;
    }

    /**
     * Performs on-the-fly validation of the form field 'markCriticalConditionCount'.
     */
    public FormValidation doCheckMarkCriticalConditionCount(@QueryParameter String value)
    {
        return validateNumber(value, 0, null, Flags.IGNORE_BLANK_VALUE, Flags.IGNORE_MAX, Flags.IS_INTEGER);
    }

    /**
     * Performs on-the-fly validation of the form field 'markCriticalBuildCount'.
     */
    public FormValidation doCheckMarkCriticalBuildCount(@QueryParameter String value)
    {
        return validateNumber(value, 0, null, Flags.IGNORE_BLANK_VALUE, Flags.IGNORE_MAX, Flags.IS_INTEGER);
    }

    /**
     * Performs on-the-fly validation of the form field 'numberOfBuildsForTrendReport'.
     */
    public FormValidation doCheckNumberOfBuildsForTrendReport(@QueryParameter String value)
    {
        return validateNumber(value, 2, null, Flags.IGNORE_MAX, Flags.IGNORE_BLANK_VALUE, Flags.IS_INTEGER);
    }

    /**
     * Performs on-the-fly validation of the form field 'numberOfBuildsForSummaryReport'.
     */
    public FormValidation doCheckNumberOfBuildsForSummaryReport(@QueryParameter String value)
    {
        return validateNumber(value, 2, null, Flags.IGNORE_MAX, Flags.IGNORE_BLANK_VALUE, Flags.IS_INTEGER);
    }

    public FormValidation doCheckInitialResponseTimeout(@QueryParameter String value)
    {
        return validateNumber(value, 0, null, Flags.IGNORE_BLANK_VALUE, Flags.IGNORE_MAX, Flags.IS_INTEGER);
    }

    @Initializer(before = InitMilestone.PLUGINS_STARTED)
    public static void addAliases()
    {
        Items.XSTREAM2.addDefaultImplementation(Embedded.class, AgentControllerConfig.class);
    }
}
