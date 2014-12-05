package com.xceptance.xlt.tools.jenkins;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import jenkins.model.Jenkins;

import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;

import com.xceptance.xlt.tools.jenkins.LoadTestBuilder.CONFIG_PLOT_PARAMETER;
import com.xceptance.xlt.tools.jenkins.LoadTestBuilder.CONFIG_SECTIONS_PARAMETER;
import com.xceptance.xlt.tools.jenkins.LoadTestBuilder.CONFIG_VALUE_PARAMETER;

@Extension
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
        catch (URISyntaxException e)
        {
            LoadTestBuilder.LOGGER.error("", e);
        }
        catch (IOException e)
        {
            LoadTestBuilder.LOGGER.error("", e);
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

    /**
     * Performs on-the-fly validation of the form field 'testPropertiesFile'.
     */
    // public FormValidation doCheckTestPropertiesFile(@QueryParameter String value)
    // {
    // if (StringUtils.isBlank(value))
    // {
    // return FormValidation.ok();
    // }
    // else
    // {
    // return doCheckFile(value);
    // }
    // }

    /**
     * Performs on-the-fly validation of the form field 'urlList'.
     */
    public FormValidation doCheckUrlList(@QueryParameter String value)
    {
        if (StringUtils.isBlank(value))
        {
            return FormValidation.validateRequired(value);
        }

        // create a check pattern
        String regex = "^https://([a-z\\d\\.-]+?)(:\\d+)?$";
        Pattern p = Pattern.compile(regex);

        // test each URL
        String[] urls = LoadTestBuilder.parseAgentControllerUrls(value);
        for (String url : urls)
        {
            Matcher matcher = p.matcher(url);
            if (!matcher.matches())
            {
                return FormValidation.error("Invalid agent controller URL found: " + url);
            }
        }

        return FormValidation.ok();
    }

    /**
     * Performs on-the-fly validation of the form field 'urlFile'.
     */
    public FormValidation doCheckUrlFile(@QueryParameter String value)
    {
        return FormValidation.validateRequired(value);
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
        if (StringUtils.isBlank(value))
            return FormValidation.ok("The default width will be used for empty field. (" + getDefaultPlotWidth() + ")");

        double number = -1;
        try
        {
            number = Double.valueOf(value);
        }
        catch (NumberFormatException e)
        {
            return FormValidation.error("Please enter a valid number for width.");
        }
        if (number < 1)
        {
            return FormValidation.error("Please enter a valid positive number for width.");
        }
        if (number != (int) number)
        {
            return FormValidation.warning("Decimal number for width. Width will be " + (int) number);
        }
        return FormValidation.ok();
    }

    public FormValidation doCheckPlotHeight(@QueryParameter String value)
    {
        if (StringUtils.isBlank(value))
            return FormValidation.ok("The default height will be used for empty field. (" + getDefaultPlotHeight() + ")");

        double number = -1;
        try
        {
            number = Double.valueOf(value);
        }
        catch (NumberFormatException e)
        {
            return FormValidation.error("Please enter a valid number for height.");
        }
        if (number < 1)
        {
            return FormValidation.error("Please enter a valid positive number for height.");
        }
        if (number != (int) number)
        {
            return FormValidation.warning("Decimal number for height. Height will be " + (int) number);
        }
        return FormValidation.ok();
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

    public FormValidation doCheckXltTemplateDir(@QueryParameter String value) throws IOException, InterruptedException
    {
        return doCheckDirectory(value);
    }

    private FormValidation doCheckDirectory(String value) throws IOException, InterruptedException
    {
        if (StringUtils.isBlank(value) || !new FilePath(new File(value)).isDirectory())
        {
            return FormValidation.error("The specified directory does not exist (yet).");
        }
        return FormValidation.ok();
    }

    /**
     * Fills the region select box.
     * 
     * @return A {@link ListBoxModel} with the available regions.
     */
    public ListBoxModel doFillRegionItems()
    {
        ListBoxModel items = new ListBoxModel();
        for (String region : AgentControllerConfig.getAllRegions().keySet())
        {
            items.add(region + " - " + AgentControllerConfig.getAllRegions().get(region), region);
        }
        return items;
    }

    /**
     * Fills the machine type select box.
     * 
     * @return A {@link ListBoxModel} with the available machine types.
     */
    public ListBoxModel doFillEc2TypeItems()
    {
        ListBoxModel items = new ListBoxModel();
        for (String type : AgentControllerConfig.getAllTypes().keySet())
        {
            items.add(type + " - " + AgentControllerConfig.getAllTypes().get(type), type);
        }
        return items;
    }

    /**
     * Performs on-the-fly validation of the form field 'amiId'.
     */
    public FormValidation doCheckAmiId(@QueryParameter String value)
    {
        return FormValidation.validateRequired(value);
    }

    /**
     * Performs on-the-fly validation of the form field 'countMachines'.
     */
    public FormValidation doCheckCountMachines(@QueryParameter String value)
    {
        if (StringUtils.isBlank(value))
        {
            return FormValidation.validateRequired(value);
        }
        int count = -1;
        try
        {
            count = Integer.parseInt(value);
        }
        catch (NumberFormatException e)
        {
            return FormValidation.error("Please enter a valid count of machines.");
        }
        if (count < 1)
        {
            return FormValidation.error("Please enter a valid positive count of machines.");
        }
        return FormValidation.ok();
    }

    /**
     * Performs on-the-fly validation of the form field 'tagName'.
     */
    public FormValidation doCheckTagName(@QueryParameter String value)
    {
        return FormValidation.validateRequired(value);
    }

    /**
     * Performs on-the-fly validation of the form field 'markCriticalConditionCount'.
     */
    public FormValidation doCheckMarkCriticalConditionCount(@QueryParameter String value)
    {
        if (StringUtils.isBlank(value))
            return FormValidation.ok();

        double number = -1;
        try
        {
            number = Double.valueOf(value);
        }
        catch (NumberFormatException e)
        {
            return FormValidation.error("Please enter a valid positive number.");
        }
        if (number < 0)
        {
            return FormValidation.error("Please enter a valid number greater or equal 0.");
        }
        if (number != (int) number)
        {
            return FormValidation.error("Please enter a valid number greater or equal 0. Decimal number is not allowed.");
        }
        return FormValidation.ok();
    }

    /**
     * Performs on-the-fly validation of the form field 'markCriticalBuildCount'.
     */
    public FormValidation doCheckMarkCriticalBuildCount(@QueryParameter String value)
    {
        if (StringUtils.isBlank(value))
            return FormValidation.ok();

        double number = -1;
        try
        {
            number = Double.valueOf(value);
        }
        catch (NumberFormatException e)
        {
            return FormValidation.error("Please enter a valid number.");
        }
        if (number < 0)
        {
            return FormValidation.error("Please enter a valid number greater or equal 0.");
        }
        if (number != (int) number)
        {
            return FormValidation.error("Please enter a valid number greater or equal 0. Decimal number is not allowed.");
        }
        return FormValidation.ok();
    }

}
