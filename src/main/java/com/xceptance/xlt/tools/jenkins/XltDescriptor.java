package com.xceptance.xlt.tools.jenkins;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletException;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import jenkins.model.Jenkins;

import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;

import com.xceptance.xlt.tools.jenkins.LoadTestBuilder.CONFIG_CRITERIA_PARAMETER;
import com.xceptance.xlt.tools.jenkins.LoadTestBuilder.CONFIG_PLOT_PARAMETER;
import com.xceptance.xlt.tools.jenkins.LoadTestBuilder.CONFIG_SECTIONS_PARAMETER;

@Extension
public class XltDescriptor extends BuildStepDescriptor<Builder>
{
    /**
     * In order to load the persisted global configuration, you have to call load() in the constructor.
     */
    public XltDescriptor()
    {
        super(LoadTestBuilder.class);

        load();
    }

    public File getXltConfigFile() throws URISyntaxException
    {
        return new File(new File(Jenkins.getInstance().getPlugin("xlt-jenkins").getWrapper().baseResourceURL.toURI()), "xltConfig.json");
    }

    public String getDefaultXltConfig()
    {
        try
        {
            return new String(Files.readAllBytes(getXltConfigFile().toPath()));
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
        return true;
    }

    public boolean getDefaultCreateSummaryReport()
    {
        return true;
    }

    public int getDefaultNumberOfBuildsForTrendReport()
    {
        return 50;
    }

    public int getDefaultNumberOfBuildsForSummaryReport()
    {
        return 50;
    }

    /**
     * Performs on-the-fly validation of the form field 'testProperties'.
     */
    public FormValidation doCheckTestProperties(@QueryParameter String value) throws IOException, ServletException
    {
        if (StringUtils.isBlank(value))
        {
            return FormValidation.warning("Please specify test configuration!");
        }
        return FormValidation.ok();
    }

    /**
     * Performs on-the-fly validation of the form field 'machineHost'.
     */
    // public FormValidation doCheckAgentControllerUrlList(@QueryParameter String value) throws IOException,
    // ServletException
    // {
    // if (StringUtils.isBlank(value))
    // {
    // return FormValidation.error("-embedded is enabled");
    // }
    //
    // String regex = "^https://.*";
    // Pattern p = Pattern.compile(regex);
    // Matcher matcher = p.matcher(value);
    // if (!matcher.find())
    // {
    // return FormValidation.error("invalid host-url");
    // }
    //
    // return FormValidation.ok();
    // }

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
            return FormValidation.error(e, "Invalid JSON");
        }

        try
        {
            List<String> criteriaIDs = new ArrayList<String>();
            Map<String, String> criteriaPlotIDs = new HashMap<String, String>();
            JSONArray validCriterias = validConfig.getJSONArray(CONFIG_SECTIONS_PARAMETER.criteria.name());
            for (int i = 0; i < validCriterias.length(); i++)
            {
                JSONObject eachCriteria = validCriterias.optJSONObject(i);
                String id = null;
                try
                {
                    id = eachCriteria.getString(CONFIG_CRITERIA_PARAMETER.id.name());
                    if (StringUtils.isBlank(id))
                        return FormValidation.error("Criteria id is empty. (criteria index: " + i + ")");
                    if (criteriaIDs.contains(id))
                        return FormValidation.error("Criteria id already exists. (criteria id: " + id + ")");
                    criteriaIDs.add(id);

                    String path = eachCriteria.getString(CONFIG_CRITERIA_PARAMETER.xPath.name());
                    if (StringUtils.isBlank(path))
                        return FormValidation.error("Criteria xPath is empty. (criteria id: " + id + ")");

                    try
                    {
                        XPathFactory.newInstance().newXPath().compile(path);
                    }
                    catch (XPathExpressionException e)
                    {
                        return FormValidation.error(e, "Invalid xPath. (criteria id:" + id + ")");
                    }

                    String condition = eachCriteria.getString(CONFIG_CRITERIA_PARAMETER.condition.name());
                    if (StringUtils.isNotBlank(condition))
                    {
                        try
                        {
                            XPathFactory.newInstance().newXPath().compile(path + condition);
                        }
                        catch (XPathExpressionException e)
                        {
                            return FormValidation.error(e, "Condition does not form a valid xPath. (criteria id:" + id + ")");
                        }
                    }

                    String criteriaPlotID = eachCriteria.getString(CONFIG_CRITERIA_PARAMETER.plotID.name());
                    if (StringUtils.isNotBlank(criteriaPlotID))
                    {
                        criteriaPlotIDs.put(id, criteriaPlotID);
                    }

                    eachCriteria.getString(CONFIG_CRITERIA_PARAMETER.name.name());
                }
                catch (JSONException e)
                {
                    return FormValidation.error(e, "Missing criteria JSON section. (criteria index: " + i + " " +
                                                   (id != null ? ("criteria id: " + id) : "") + ")");
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
                    return FormValidation.error(e, "Missing plot JSON section. (plot index: " + i + " " +
                                                   (id != null ? ("plot id: " + id) : "") + ")");
                }
            }

            for (Entry<String, String> eachEntry : criteriaPlotIDs.entrySet())
            {
                if (!plotIDs.contains(eachEntry.getValue()))
                {
                    return FormValidation.error("Missing plot config for plot id:" + eachEntry.getValue() + " at criteria id: " +
                                                eachEntry.getKey() + ".");
                }
            }
        }
        catch (JSONException e)
        {
            return FormValidation.error(e, "Missing JSON section");
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
                return FormValidation.error(e, "Invalid time format pattern.");
            }
        }
        return FormValidation.ok("Preview: " + format.format(new Date()));
    }

    public FormValidation doCheckXltTemplate(@QueryParameter String value)
    {
        return doCheckDirectory(value);
    }

    public FormValidation doCheckAgentControllerUrlFile(@QueryParameter String value)
    {
        return doCheckFile(value);
    }

    // public FormValidation doCheckTestPropertiesFile(@QueryParameter String value)
    // {
    // return doCheckFile(getTestPropertiesFile() + value);
    // }

    private FormValidation doCheckFile(String value)
    {
        if (value == null || !new File(value).isFile())
        {
            return FormValidation.error("The specified file does not exist (yet).");
        }
        return FormValidation.ok();
    }

    private FormValidation doCheckDirectory(String value)
    {
        if (value == null || !new File(value).isDirectory())
        {
            return FormValidation.error("The specified directory does not exist (yet).");
        }
        return FormValidation.ok();
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
}
