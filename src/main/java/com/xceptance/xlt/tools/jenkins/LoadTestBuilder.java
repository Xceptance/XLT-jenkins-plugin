package com.xceptance.xlt.tools.jenkins;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.InvisibleAction;
import hudson.model.ParameterValue;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.StringParameterValue;
import hudson.security.ACL;
import hudson.tasks.Builder;
import hudson.util.Secret;
import hudson.util.StreamTaskListener;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.StringReader;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import jenkins.model.Jenkins;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.xceptance.xlt.tools.jenkins.Chart.ChartLine;
import com.xceptance.xlt.tools.jenkins.Chart.ChartLineValue;
import com.xceptance.xlt.tools.jenkins.config.option.MarkCriticalOption;
import com.xceptance.xlt.tools.jenkins.config.option.SummaryReportOption;
import com.xceptance.xlt.tools.jenkins.config.option.TrendReportOption;
import com.xceptance.xlt.tools.jenkins.logging.LOGGER;

/**
 * Readout the configuration of XLT in Jenkins, perform load testing and plot build results on project page.
 * 
 * @author Michael Aleithe, Randolph Straub
 */
public class LoadTestBuilder extends Builder
{
    private final String testPropertiesFile;

    private AgentControllerConfig agentControllerConfig;

    private int initialResponseTimeout;

    private final String xltConfig;

    private final String xltTemplateDir;

    private final String pathToTestSuite;

    transient private JSONObject config = new JSONObject();

    private String timeFormatPattern = null;

    transient private SimpleDateFormat dateFormat;

    private boolean showBuildNumber;

    private final int plotWidth;

    private final int plotHeight;

    private final String plotTitle;

    private final String builderID;

    private final boolean isPlotVertical;

    private TrendReportOption trendReportOption;

    private final boolean createTrendReport;

    private final int numberOfBuildsForTrendReport;

    private SummaryReportOption summaryReportOption;

    private final boolean createSummaryReport;

    private final int numberOfBuildsForSummaryReport;

    transient private static Map<String, String> loggerLookupMap = new HashMap<String, String>();

    transient private List<Chart<Integer, Double>> charts = new ArrayList<Chart<Integer, Double>>();

    transient private boolean isSave = false;

    transient private XltChartAction chartAction;

    private MarkCriticalOption markCriticalOption;

    private boolean markCriticalEnabled;

    private int markCriticalConditionCount;

    private int markCriticalBuildCount;

    private transient Map<ENVIRONMENT_KEYS, ParameterValue> buildParameterMap;

    public enum CONFIG_VALUE_PARAMETER
    {
        id, xPath, condition, plotID, name
    };

    public enum CONFIG_PLOT_PARAMETER
    {
        id, title, buildCount, enabled, showNoValues
    };

    public enum CONFIG_SECTIONS_PARAMETER
    {
        values, plots
    };

    public enum ENVIRONMENT_KEYS
    {
        XLT_RUN_FAILED, XLT_CONDITION_CRITICAL, XLT_CONDITION_FAILED, XLT_CONDITION_ERROR, XLT_REPORT_URL, XLT_CONDITION_MESSAGE
    };

    public static class FOLDER_NAMES
    {
        public static String ARTIFACT_REPORT = "report";

        public static String ARTIFACT_RESULT = "results";
    }

    @DataBoundConstructor
    public LoadTestBuilder(String xltTemplateDir, String pathToTestSuite, String testPropertiesFile, String xltConfig, int plotWidth,
                           int plotHeight, String plotTitle, String builderID, boolean isPlotVertical, TrendReportOption trendReportOption,
                           SummaryReportOption summaryReportOption, int numberOfBuildsForSummaryReport,
                           AgentControllerConfig agentControllerConfig, String timeFormatPattern, boolean showBuildNumber,
                           MarkCriticalOption markCriticalOption, boolean markCriticalEnabled, Integer initialResponseTimeout)
    {
        isSave = true;
        Thread.currentThread().setUncaughtExceptionHandler(new UncaughtExceptionHandler()
        {
            public void uncaughtException(Thread t, Throwable e)
            {
                LOGGER.error("Uncaught exception", e);
            }
        });

        // load test configuration
        this.xltTemplateDir = StringUtils.defaultIfBlank(xltTemplateDir, null);
        this.pathToTestSuite = StringUtils.defaultIfBlank(pathToTestSuite, null);
        this.testPropertiesFile = StringUtils.defaultIfBlank(testPropertiesFile, null);
        this.agentControllerConfig = (agentControllerConfig != null) ? agentControllerConfig : new AgentControllerConfig();

        this.initialResponseTimeout = initialResponseTimeout != null ? initialResponseTimeout
                                                                    : getDescriptor().getDefaultInitialResponseTimeout();

        // plot/value configuration
        this.xltConfig = StringUtils.defaultIfBlank(xltConfig, getDescriptor().getDefaultXltConfig());

        // advanced plot configuration
        this.plotWidth = (plotWidth > 0) ? plotWidth : getDescriptor().getDefaultPlotWidth();
        this.plotHeight = (plotHeight > 0) ? plotHeight : getDescriptor().getDefaultPlotHeight();
        this.plotTitle = StringUtils.defaultIfBlank(plotTitle, getDescriptor().getDefaultPlotTitle());
        this.isPlotVertical = isPlotVertical;
        this.timeFormatPattern = StringUtils.defaultIfBlank(timeFormatPattern, null);
        this.showBuildNumber = showBuildNumber;

        // criteria configuration
        this.markCriticalOption = markCriticalOption;
        if (markCriticalOption == null)
        {
            this.markCriticalEnabled = false;
            this.markCriticalConditionCount = 0;
            this.markCriticalBuildCount = 0;
        }
        else
        {
            this.markCriticalEnabled = true;
            this.markCriticalConditionCount = markCriticalOption.getMarkCriticalConditionCount();
            this.markCriticalBuildCount = (markCriticalConditionCount > 0 && markCriticalConditionCount > markCriticalOption.getMarkCriticalBuildCount())
                                                                                                                                                         ? markCriticalConditionCount
                                                                                                                                                         : markCriticalOption.getMarkCriticalBuildCount();
        }

        // trend report configuration
        this.trendReportOption = trendReportOption;
        if (trendReportOption == null)
        {
            this.createTrendReport = false;
            this.numberOfBuildsForTrendReport = getDescriptor().getDefaultNumberOfBuildsForTrendReport();
        }
        else
        {
            this.createTrendReport = true;
            this.numberOfBuildsForTrendReport = (trendReportOption.getNumberOfBuildsForTrendReport() > 0)
                                                                                                         ? trendReportOption.getNumberOfBuildsForTrendReport()
                                                                                                         : getDescriptor().getDefaultNumberOfBuildsForTrendReport();
        }

        // trend report configuration
        this.summaryReportOption = summaryReportOption;
        if (summaryReportOption == null)
        {
            this.createSummaryReport = false;
            this.numberOfBuildsForSummaryReport = getDescriptor().getDefaultNumberOfBuildsForSummaryReport();
        }
        else
        {
            this.createSummaryReport = true;
            this.numberOfBuildsForSummaryReport = (summaryReportOption.getNumberOfBuildsForSummaryReport() > 0)
                                                                                                               ? summaryReportOption.getNumberOfBuildsForSummaryReport()
                                                                                                               : getDescriptor().getDefaultNumberOfBuildsForSummaryReport();
        }

        // misc.
        this.builderID = StringUtils.defaultIfBlank(builderID, UUID.randomUUID().toString());
    }

    public class XLTBuilderAction extends InvisibleAction
    {
        public LoadTestBuilder getLoadTestBuilder()
        {
            return LoadTestBuilder.this;
        }
    }

    public static String getLoggerLookup(String key)
    {
        return loggerLookupMap.get(key);
    }

    public List<AWSSecurityGroup> getSecurityGroups()
    {
        return agentControllerConfig.getSecurityGroups();
    }

    public String getAwsUserData()
    {
        return agentControllerConfig.getAwsUserData();
    }

    private SimpleDateFormat getDateFormat()
    {
        if (dateFormat == null)
        {
            if (timeFormatPattern != null)
            {
                try
                {
                    dateFormat = new SimpleDateFormat(timeFormatPattern);
                }
                catch (Exception ex)
                {
                    LOGGER.warn("Failed to create date format for pattern: " + timeFormatPattern, ex);
                }
            }

            if (dateFormat == null)
            {
                dateFormat = new SimpleDateFormat();
            }
        }
        return dateFormat;
    }

    private String[] parseAgentControllerUrlsFromFile(FilePath file) throws Exception
    {
        String fileContent = file.readToString();

        return parseAgentControllerUrls(fileContent);
    }

    static String[] parseAgentControllerUrls(String agentControllerUrls)
    {
        return StringUtils.split(agentControllerUrls, "\r\n\t|,; ");
    }

    public AgentControllerConfig getAgentControllerConfig()
    {
        return agentControllerConfig;
    }

    public int getInitialResponseTimeout()
    {
        return initialResponseTimeout;
    }

    public TrendReportOption getTrendReportOption()
    {
        return trendReportOption;
    }

    public SummaryReportOption getSummaryReportOption()
    {
        return summaryReportOption;
    }

    public MarkCriticalOption getMarkCriticalOption()
    {
        return markCriticalOption;
    }

    public boolean getMarkCriticalEnabled()
    {
        return this.markCriticalEnabled;
    }

    public int getMarkCriticalConditionCount()
    {
        return markCriticalConditionCount;
    }

    public int getMarkCriticalBuildCount()
    {
        return markCriticalBuildCount;
    }

    public String getPathToTestSuite()
    {
        return pathToTestSuite;
    }

    public String getTestPropertiesFile()
    {
        return testPropertiesFile;
    }

    public String getXltConfig()
    {
        return xltConfig;
    }

    public String getXltTemplateDir()
    {
        return xltTemplateDir;
    }

    public String getTimeFormatPattern()
    {
        return timeFormatPattern;
    }

    public boolean getShowBuildNumber()
    {
        return showBuildNumber;
    }

    public int getPlotWidth()
    {
        return plotWidth;
    }

    public int getPlotHeight()
    {
        return plotHeight;
    }

    public String getPlotTitle()
    {
        return plotTitle;
    }

    public String getBuilderID()
    {
        return builderID;
    }

    public boolean getIsPlotVertical()
    {
        return isPlotVertical;
    }

    public boolean getCreateTrendReport()
    {
        return createTrendReport;
    }

    public boolean getCreateSummaryReport()
    {
        return createSummaryReport;
    }

    public int getNumberOfBuildsForTrendReport()
    {
        return numberOfBuildsForTrendReport;
    }

    public int getNumberOfBuildsForSummaryReport()
    {
        return numberOfBuildsForSummaryReport;
    }

    private Chart<Integer, Double> getChart(String plotID)
    {
        for (Chart<Integer, Double> eachChart : charts)
        {
            if (eachChart.getChartID().equals(plotID))
            {
                return eachChart;
            }
        }
        return null;
    }

    private List<Chart<Integer, Double>> getEnabledCharts()
    {
        List<Chart<Integer, Double>> enabledCharts = new ArrayList<Chart<Integer, Double>>();
        for (Chart<Integer, Double> eachChart : charts)
        {
            String enabled = getOptionalPlotConfigValue(eachChart.getChartID(), CONFIG_PLOT_PARAMETER.enabled);

            if (StringUtils.isNotBlank(enabled) && "yes".equals(enabled))
            {
                enabledCharts.add(eachChart);
            }
        }
        return enabledCharts;
    }

    private void loadCharts(AbstractProject<?, ?> project, AbstractBuild<?, ?>... excludeBuilds)
    {
        List<? extends AbstractBuild<?, ?>> allBuilds = project.getBuilds();
        if (allBuilds.isEmpty())
            return;

        try
        {
            int largestBuildCount = -1;
            for (String eachPlotID : getPlotConfigIDs())
            {
                String buildCountValue = getOptionalPlotConfigValue(eachPlotID, CONFIG_PLOT_PARAMETER.buildCount);
                if (StringUtils.isNotBlank(buildCountValue))
                {
                    try
                    {
                        int buildCount = Integer.parseInt(buildCountValue);
                        if (buildCount > largestBuildCount)
                        {
                            largestBuildCount = buildCount;
                        }
                    }
                    catch (NumberFormatException e)
                    {
                        LOGGER.error("Build count is not a number (plotID: \"" + eachPlotID + "\")", e);
                    }
                }
                else
                {
                    largestBuildCount = -1;
                    break;
                }
            }

            List<? extends AbstractBuild<?, ?>> builds = getBuilds(project, 0, largestBuildCount);
            builds.removeAll(Arrays.asList(excludeBuilds));

            addBuildsToCharts(builds);
        }
        catch (JSONException e)
        {
            LOGGER.error("Failed to config section.", e);
        }
    }

    /**
     * @param project
     * @param startFrom
     *            (inclusive)
     * @param count
     *            < 0 means all builds up to end
     * @return
     */
    private List<? extends AbstractBuild<?, ?>> getBuilds(AbstractProject<?, ?> project, int startFrom, int count)
    {
        List<? extends AbstractBuild<?, ?>> allBuilds = project.getBuilds();
        int maxBuilds = allBuilds.size();
        int to = Math.min(startFrom + count, maxBuilds);
        if (to < 0 || to > maxBuilds)
        {
            to = maxBuilds;
        }

        return allBuilds.subList(startFrom, to);
    }

    private Document getDataDocument(AbstractBuild<?, ?> build) throws IOException, InterruptedException
    {
        FilePath testDataFile = getTestReportDataFile(build);
        if (testDataFile.exists())
        {
            try
            {
                return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(testDataFile.getRemote());
            }
            catch (SAXException e)
            {
                LOGGER.error("", e);
            }
            catch (IOException e)
            {
                LOGGER.error("", e);
            }
            catch (ParserConfigurationException e)
            {
                LOGGER.error("", e);
            }
        }
        else
        {
            LOGGER.info("No test data found for build. (build: \"" + build.number + "\" searchLocation: \"" + testDataFile.getRemote() +
                        "\")");
        }
        return null;
    }

    public void addBuildToCharts(AbstractBuild<?, ?> build)
    {
        List<AbstractBuild<?, ?>> builds = new ArrayList<AbstractBuild<?, ?>>();
        builds.add(build);
        addBuildsToCharts(builds);
    }

    private void addBuildsToCharts(List<? extends AbstractBuild<?, ?>> builds)
    {
        for (int i = builds.size() - 1; i > -1; i--)
        {
            AbstractBuild<?, ?> eachBuild = builds.get(i);
            Document dataXml = null;
            try
            {
                dataXml = getDataDocument(eachBuild);
            }
            catch (Exception e)
            {
                LOGGER.error("Failed to read test data xml", e);
                return;
            }

            try
            {
                for (String eachPlotID : getPlotConfigIDs())
                {
                    Chart<Integer, Double> chart = getChart(eachPlotID);
                    if (chart == null)
                    {
                        LOGGER.debug("No chart found for plot. (build: \"" + eachBuild.number + "\" plotID: \"" + eachPlotID + "\")");
                        continue;
                    }
                    boolean addedValueToChart = false;

                    try
                    {
                        for (String eachValueID : getValueConfigIDs(eachPlotID))
                        {
                            ChartLine<Integer, Double> line = chart.getLine(eachValueID);
                            if (line == null)
                            {
                                LOGGER.debug("No line found for value. (build: \"" + eachBuild.number + "\" valueID: \"" + eachValueID +
                                             "\" chartID:\"" + chart.getChartID() + "\")");
                                continue;
                            }

                            boolean addedValueToLine = false;
                            if (dataXml == null)
                            {
                                LOGGER.info("No test data found for build. (build: \"" + eachBuild.number + "\")");
                            }
                            else
                            {
                                try
                                {
                                    String xPath = getvalueConfigValue(eachValueID, CONFIG_VALUE_PARAMETER.xPath);
                                    try
                                    {
                                        Double number = (Double) XPathFactory.newInstance().newXPath()
                                                                             .evaluate(xPath, dataXml, XPathConstants.NUMBER);

                                        if (number.isNaN())
                                        {
                                            LOGGER.warn("Value is not a number. (build: \"" + eachBuild.number + "\" valueID: \"" +
                                                        eachValueID + "\" XPath: \"" + xPath + "\"");
                                        }
                                        else
                                        {
                                            addChartLineValue(line, eachBuild, chart.getXIndex(), number.doubleValue());
                                            addedValueToLine = true;
                                            addedValueToChart = true;
                                        }
                                    }
                                    catch (XPathExpressionException e)
                                    {
                                        LOGGER.error("Invalid XPath. (build: \"" + eachBuild.number + "\" valueID: \"" + eachValueID +
                                                     "\" XPath: \"" + xPath + "\")", e);
                                    }
                                }
                                catch (JSONException e)
                                {
                                    LOGGER.error("Failed to get config section. (build: \"" + eachBuild.number + "\" valueID: \"" +
                                                 eachValueID + "\")", e);
                                }
                            }
                            if (addedValueToLine == false && line.getShowNoValues())
                            {
                                addChartLineValue(line, eachBuild, chart.getXIndex(), 0);
                                addedValueToChart = true;
                            }
                        }
                    }
                    catch (JSONException e)
                    {
                        LOGGER.error("Failed to get config section. (build: \"" + eachBuild.number + "\")", e);
                    }

                    if (addedValueToChart)
                    {
                        chart.nextXIndex();
                    }
                }
            }
            catch (JSONException e)
            {
                LOGGER.error("Failed to get config section. (build: \"" + eachBuild.number + "\")", e);
            }
        }
    }

    private void addChartLineValue(ChartLine<Integer, Double> chartLine, AbstractBuild<?, ?> build, int xIndex, double dataValue)
    {
        ChartLineValue<Integer, Double> lineValue = new ChartLineValue<Integer, Double>(xIndex, dataValue);

        lineValue.setDataObjectValue("buildNumber", "\"" + build.number + "\"");
        lineValue.setDataObjectValue("showBuildNumber", "" + showBuildNumber);
        lineValue.setDataObjectValue("buildTime", "\"" + getDateFormat().format(build.getTime()) + "\"");
        chartLine.addLineValue(lineValue);
    }

    private Chart<Integer, Double> createChart(String plotID) throws JSONException
    {
        String chartTitle = getOptionalPlotConfigValue(plotID, CONFIG_PLOT_PARAMETER.title);
        if (chartTitle == null)
        {
            chartTitle = "";
        }

        String buildCountValue = getOptionalPlotConfigValue(plotID, CONFIG_PLOT_PARAMETER.buildCount);
        int maxCount = Integer.MAX_VALUE;
        if (StringUtils.isNotBlank(buildCountValue))
        {
            try
            {
                maxCount = Integer.parseInt(buildCountValue);
            }
            catch (NumberFormatException e)
            {
                LOGGER.error("Build count is not a number (plotID: \"" + plotID + "\")", e);
            }
        }

        boolean showNoValues = false;
        String showNoValuesValue = getOptionalPlotConfigValue(plotID, CONFIG_PLOT_PARAMETER.showNoValues);
        if (showNoValuesValue != null)
        {
            if (StringUtils.isNotBlank(showNoValuesValue) && "yes".equals(showNoValuesValue))
            {
                showNoValues = true;
            }
        }
        else
        {
            LOGGER.info("Plot config parameter \"showNoValues\" is undefined (plotID: \"" + plotID + "\")");
        }

        Chart<Integer, Double> chart = new Chart<Integer, Double>(plotID, chartTitle);
        for (String eachValueID : getValueConfigIDs(plotID))
        {
            String lineName = getOptionalValueConfigValue(eachValueID, CONFIG_VALUE_PARAMETER.name);
            if (lineName == null)
            {
                lineName = "";
            }
            ChartLine<Integer, Double> line = new ChartLine<Integer, Double>(eachValueID, lineName, maxCount, showNoValues);
            chart.getLines().add(line);
        }
        return chart;
    }

    private void initializeCharts() throws JSONException
    {
        for (String eachPlotID : getPlotConfigIDs())
        {
            try
            {
                charts.add(createChart(eachPlotID));
            }
            catch (JSONException e)
            {
                LOGGER.error("Failed to initialize chart. (plotID: \"" + eachPlotID + "\")", e);
            }
        }
    }

    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project)
    {
        ArrayList<Action> actions = new ArrayList<Action>();
        if (isSave || chartAction == null)
        {
            isSave = false;

            updateConfig();

            reloadCharts(project);

            chartAction = new XltChartAction(project, getEnabledCharts(), plotWidth, plotHeight, plotTitle, builderID, isPlotVertical,
                                             createTrendReport, createSummaryReport);
        }
        actions.add(chartAction);
        actions.add(new XLTBuilderAction());

        return actions;
    }

    private void reloadCharts(AbstractProject<?, ?> project, AbstractBuild<?, ?>... exludeBuilds)
    {
        if (charts == null)
        {
            charts = new ArrayList<Chart<Integer, Double>>();
        }
        else
        {
            charts.clear();
        }

        try
        {
            initializeCharts();
        }
        catch (JSONException e)
        {
            LOGGER.error("Failed to initialize charts", e);
        }

        loadCharts(project, exludeBuilds);

        if (chartAction != null)
        {
            chartAction.setCharts(getEnabledCharts());
        }
    }

    public void removeBuildFromCharts(AbstractProject<?, ?> project, AbstractBuild<?, ?> build)
    {
        reloadCharts(project, build);
    }

    private void updateConfig()
    {
        try
        {
            config = new JSONObject(xltConfig);
        }
        catch (JSONException e)
        {
            LOGGER.error("", e);
        }
    }

    private String getvalueConfigValue(String configName, CONFIG_VALUE_PARAMETER parameter) throws JSONException
    {
        JSONArray valueArray = config.getJSONArray(CONFIG_SECTIONS_PARAMETER.values.name());
        for (int i = 0; i < valueArray.length(); i++)
        {
            JSONObject each = valueArray.getJSONObject(i);
            if (configName.equals(each.getString(CONFIG_VALUE_PARAMETER.id.name())))
            {
                return each.getString(parameter.name());
            }
        }
        return null;
    }

    private String getOptionalValueConfigValue(String configName, CONFIG_VALUE_PARAMETER parameter)
    {
        try
        {
            return getvalueConfigValue(configName, parameter);
        }
        catch (JSONException e)
        {
            LOGGER.error("", e);
        }
        return null;
    }

    private String getOptionalPlotConfigValue(String plotID, CONFIG_PLOT_PARAMETER parameter)
    {
        try
        {
            return getPlotConfigValue(plotID, parameter);
        }
        catch (JSONException e)
        {
            LOGGER.error("", e);
            return null;
        }
    }

    private String getPlotConfigValue(String plotID, CONFIG_PLOT_PARAMETER parameter) throws JSONException
    {
        JSONArray plotsArray = config.getJSONArray(CONFIG_SECTIONS_PARAMETER.plots.name());
        for (int i = 0; i < plotsArray.length(); i++)
        {
            JSONObject each = plotsArray.getJSONObject(i);
            if (plotID.equals(each.getString(CONFIG_PLOT_PARAMETER.id.name())))
            {
                return each.getString(parameter.name());
            }
        }
        return null;
    }

    private ArrayList<String> getValueConfigIDs() throws JSONException
    {
        ArrayList<String> valueList = new ArrayList<String>();

        if (config != null)
        {
            JSONArray valuesSection = config.getJSONArray(CONFIG_SECTIONS_PARAMETER.values.name());
            for (int i = 0; i < valuesSection.length(); i++)
            {
                JSONObject each = valuesSection.getJSONObject(i);
                valueList.add(each.getString(CONFIG_VALUE_PARAMETER.id.name()));
            }
        }
        return valueList;
    }

    private ArrayList<String> getValueConfigIDs(String plotID) throws JSONException
    {
        ArrayList<String> valueList = new ArrayList<String>();

        JSONArray valuesSection = config.getJSONArray(CONFIG_SECTIONS_PARAMETER.values.name());
        for (int i = 0; i < valuesSection.length(); i++)
        {
            String valueID = null;
            try
            {
                JSONObject each = valuesSection.getJSONObject(i);
                valueID = each.getString(CONFIG_VALUE_PARAMETER.id.name());
                if (plotID.equals(each.getString(CONFIG_VALUE_PARAMETER.plotID.name())))
                {
                    valueList.add(valueID);
                }
            }
            catch (JSONException e)
            {
                String message = "";
                if (valueID != null)
                    message = "valueID: \"" + valueID + "\"";

                LOGGER.error("Failed to get plot id for value. (index: " + i + " " + message + ")", e);
            }
        }
        return valueList;
    }

    private ArrayList<String> getPlotConfigIDs() throws JSONException
    {
        ArrayList<String> plotIDs = new ArrayList<String>();

        if (config != null)
        {
            JSONArray plotSection = config.getJSONArray(CONFIG_SECTIONS_PARAMETER.plots.name());
            for (int i = 0; i < plotSection.length(); i++)
            {
                JSONObject each = plotSection.getJSONObject(i);
                plotIDs.add(each.getString(CONFIG_PLOT_PARAMETER.id.name()));
            }
        }
        return plotIDs;
    }

    private FilePath getTestReportDataFile(AbstractBuild<?, ?> build)
    {
        return new FilePath(getBuildReportFolder(build), "testreport.xml");
    }

    private FilePath getXltResultFolder(AbstractBuild<?, ?> build) throws BuildNodeGoneException
    {
        return new FilePath(getTemporaryXltFolder(build), FOLDER_NAMES.ARTIFACT_RESULT);
    }

    private FilePath getXltLogFolder(AbstractBuild<?, ?> build) throws BuildNodeGoneException
    {
        return new FilePath(getTemporaryXltFolder(build), "log");
    }

    private FilePath getXltReportFolder(AbstractBuild<?, ?> build) throws BuildNodeGoneException
    {
        return new FilePath(getTemporaryXltFolder(build), FOLDER_NAMES.ARTIFACT_REPORT + "/" + Integer.toString(build.getNumber()));
    }

    private FilePath getFirstSubFolder(FilePath dir) throws IOException, InterruptedException
    {
        if (dir != null && dir.isDirectory())
        {
            List<FilePath> subFiles = dir.list();
            if (subFiles != null)
            {
                for (FilePath subFile : subFiles)
                {
                    if (subFile.isDirectory())
                    {
                        return subFile;
                    }
                }
            }
        }
        return null;
    }

    private static URI getArtifactsDir(AbstractBuild<?, ?> build)
    {
        return build.getArtifactManager().root().toURI();
    }

    public static FilePath getArtifact(AbstractBuild<?, ?> build, String artifactPath)
    {
        return new FilePath(new File(new File(getArtifactsDir(build)), artifactPath));
    }

    private FilePath getBuildResultConfigFolder(AbstractBuild<?, ?> build)
    {
        return new FilePath(getBuildResultFolder(build), "config");
    }

    private FilePath getBuildLogsFolder(AbstractBuild<?, ?> build)
    {
        return getArtifact(build, builderID + "/log");
    }

    private FilePath getBuildReportFolder(AbstractBuild<?, ?> build)
    {
        return getArtifact(build, builderID + "/" + FOLDER_NAMES.ARTIFACT_REPORT + "/" + Integer.toString(build.getNumber()));
    }

    private String getBuildReportURL(AbstractBuild<?, ?> build)
    {
        String jenkinsURL = Jenkins.getInstance().getRootUrl();
        if (jenkinsURL == null)
        {
            jenkinsURL = "";
        }
        return jenkinsURL + build.getUrl() + XltRecorderAction.RELATIVE_REPORT_URL + builderID + "/" + FOLDER_NAMES.ARTIFACT_REPORT + "/" +
               build.getNumber() + "/index.html";
    }

    private FilePath getBuildResultFolder(AbstractBuild<?, ?> build)
    {
        return getArtifact(build, builderID + "/" + FOLDER_NAMES.ARTIFACT_RESULT);
    }

    private FilePath getTrendReportFolder(AbstractProject<?, ?> project)
    {
        return new FilePath(new FilePath(project.getRootDir()), "trendreport/" + builderID);
    }

    private FilePath getSummaryReportFolder(AbstractProject<?, ?> project)
    {
        return new FilePath(new File(project.getRootDir(), "summaryReport/" + builderID));
    }

    private FilePath getSummaryResultsFolder(AbstractProject<?, ?> project)
    {
        return new FilePath(new File(project.getRootDir(), "summaryResults/" + builderID));
    }

    private FilePath getSummaryResultsConfigFolder(AbstractProject<?, ?> project)
    {
        return new FilePath(getSummaryResultsFolder(project), "config");
    }

    private FilePath getTemporaryXltBaseFolder(AbstractBuild<?, ?> build) throws BuildNodeGoneException
    {
        hudson.model.Node node = getBuildNodeIfOnlineOrFail(build);
        FilePath base = new FilePath(build.getParent().getRootDir());
        if (node != Jenkins.getInstance())
        {
            base = build.getBuiltOn().getRootPath();
        }
        return new FilePath(base, "tmp-xlt");
    }

    private FilePath getTemporaryXltProjectFolder(AbstractBuild<?, ?> build) throws BuildNodeGoneException
    {
        return new FilePath(getTemporaryXltBaseFolder(build), build.getProject().getName());
    }

    private FilePath getTemporaryXltBuildFolder(AbstractBuild<?, ?> build) throws BuildNodeGoneException
    {
        return new FilePath(getTemporaryXltProjectFolder(build), "" + build.getNumber());
    }

    private FilePath getTemporaryXltFolder(AbstractBuild<?, ?> build) throws BuildNodeGoneException
    {
        return new FilePath(new FilePath(getTemporaryXltBuildFolder(build), getBuilderID()), "xlt");
    }

    private FilePath getXltTemplateFilePath()
    {
        return XltDescriptor.resolvePath(getXltTemplateDir());
    }

    private FilePath getXltBinFolder(AbstractBuild<?, ?> build) throws BuildNodeGoneException
    {
        return new FilePath(getTemporaryXltFolder(build), "bin");
    }

    private FilePath getXltBinFolderOnMaster()
    {
        return new FilePath(getXltTemplateFilePath(), "bin");
    }

    private FilePath getXltConfigFolder(AbstractBuild<?, ?> build) throws BuildNodeGoneException
    {
        return new FilePath(getTemporaryXltFolder(build), "config");
    }

    private List<CriterionResult> getFailedCriteria(AbstractBuild<?, ?> build, BuildListener listener, Document dataXml)
        throws IOException, InterruptedException
    {
        listener.getLogger().println("-----------------------------------------------------------------\n"
                                         + "Checking success criteria ...\n");

        List<CriterionResult> failedAlerts = new ArrayList<CriterionResult>();

        if (dataXml == null)
        {
            CriterionResult criterionResult = CriterionResult.error("No test data found.");
            failedAlerts.add(criterionResult);
        }
        else
        {
            try
            {
                List<String> criteriaIDs = getValueConfigIDs();
                for (String eachID : criteriaIDs)
                {
                    listener.getLogger().println(eachID);
                    String xPath = null;
                    String condition = null;
                    try
                    {
                        xPath = getvalueConfigValue(eachID, CONFIG_VALUE_PARAMETER.xPath);
                        condition = getOptionalValueConfigValue(eachID, CONFIG_VALUE_PARAMETER.condition);
                        if (StringUtils.isBlank(condition))
                        {
                            LOGGER.debug("No condition for criterion. (criterionID: \"" + eachID + "\")");
                            continue;
                        }
                        if (StringUtils.isBlank(xPath))
                        {
                            CriterionResult criterionResult = CriterionResult.error("No xPath for Criterion");
                            criterionResult.setCriterionID(eachID);
                            failedAlerts.add(criterionResult);
                            continue;
                        }
                        String conditionPath = xPath + condition;

                        Element node = (Element) XPathFactory.newInstance().newXPath().evaluate(xPath, dataXml, XPathConstants.NODE);
                        if (node == null)
                        {
                            CriterionResult criterionResult = CriterionResult.error("No result for XPath");
                            criterionResult.setCriterionID(eachID);
                            criterionResult.setXPath(xPath);
                            failedAlerts.add(criterionResult);
                            continue;
                        }

                        // test the condition
                        Node result = (Node) XPathFactory.newInstance().newXPath().evaluate(conditionPath, dataXml, XPathConstants.NODE);
                        if (result == null)
                        {
                            String value = XPathFactory.newInstance().newXPath().evaluate(xPath, dataXml);
                            CriterionResult criterionResult = CriterionResult.failed("Condition");
                            criterionResult.setCriterionID(eachID);
                            criterionResult.setValue(value);
                            criterionResult.setCondition(condition);
                            criterionResult.setXPath(xPath);
                            failedAlerts.add(criterionResult);
                            continue;
                        }
                    }
                    catch (JSONException e)
                    {
                        CriterionResult criterionResult = CriterionResult.error("Failed to get parameter from configuration");
                        criterionResult.setCriterionID(eachID);
                        criterionResult.setExceptionMessage(e.getMessage());
                        criterionResult.setCauseMessage(e.getCause() != null ? e.getCause().getMessage() : null);
                        failedAlerts.add(criterionResult);
                    }
                    catch (XPathExpressionException e)
                    {
                        CriterionResult criterionResult = CriterionResult.error("Incorrect xPath expression");
                        criterionResult.setCriterionID(eachID);
                        criterionResult.setCondition(condition);
                        criterionResult.setXPath(xPath);
                        criterionResult.setExceptionMessage(e.getMessage());
                        criterionResult.setCauseMessage(e.getCause() != null ? e.getCause().getMessage() : null);
                        failedAlerts.add(criterionResult);
                    }
                }
            }
            catch (JSONException e)
            {
                CriterionResult criterionResult = CriterionResult.error("Failed to start process criterion.");
                criterionResult.setExceptionMessage(e.getMessage());
                criterionResult.setCauseMessage(e.getCause() != null ? e.getCause().getMessage() : null);
                failedAlerts.add(criterionResult);
            }
        }
        return failedAlerts;
    }

    private void validateCriteria(AbstractBuild<?, ?> build, BuildListener listener) throws IOException, InterruptedException
    {
        // get the test report document
        final Document dataXml = getDataDocument(build);

        // process the test report
        final List<CriterionResult> failedAlerts = getFailedCriteria(build, listener, dataXml);
        final List<TestCaseInfo> failedTestCases = determineFailedTestCases(build, listener, dataXml);
        final List<SlowRequestInfo> slowestRequests = determineSlowestRequests(build, listener, dataXml);

        // create the action with all the collected data
        XltRecorderAction recorderAction = new XltRecorderAction(build, failedAlerts, builderID, getBuildReportURL(build), failedTestCases,
                                                                 slowestRequests);
        build.addAction(recorderAction);

        // log failed criteria to the build's console
        if (!failedAlerts.isEmpty())
        {
            listener.getLogger().println();
            for (CriterionResult eachAlert : failedAlerts)
            {
                listener.getLogger().println(eachAlert.getLogMessage());
            }
            listener.getLogger().println();
            listener.getLogger().println("Set state to UNSTABLE");
            build.setResult(Result.UNSTABLE);
        }

        // set build parameters
        if (!recorderAction.getAlerts().isEmpty())
        {
            if (!recorderAction.getFailedAlerts().isEmpty())
            {
                setBuildParameterXLT_CONDITION_FAILED(true);
                checkForCritical(build);
            }

            if (!recorderAction.getErrorAlerts().isEmpty())
            {
                setBuildParameterXLT_CONDITION_ERROR(true);
            }
        }
        setBuildParameterXLT_CONDITION_MESSAGE(recorderAction.getConditionMessage());
    }

    private void checkForCritical(AbstractBuild<?, ?> currentBuild)
    {
        if (markCriticalEnabled &&
            (markCriticalBuildCount > 0 && markCriticalConditionCount > 0 && markCriticalBuildCount >= markCriticalConditionCount))
        {
            List<AbstractBuild<?, ?>> builds = new ArrayList<AbstractBuild<?, ?>>();
            builds.addAll(getBuilds(currentBuild.getParent(), 0, markCriticalBuildCount));

            int failedCriterionBuilds = 0;
            for (AbstractBuild<?, ?> eachBuild : builds)
            {
                XltRecorderAction recorderAction = eachBuild.getAction(XltRecorderAction.class);
                if (recorderAction != null)
                {
                    if (!recorderAction.getFailedAlerts().isEmpty())
                    {
                        failedCriterionBuilds++;
                        if (failedCriterionBuilds == markCriticalConditionCount)
                        {
                            setBuildParameterXLT_CONDITION_CRITICAL(true);
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Extracts test case name, action name, and error message from each failed test case.
     * 
     * @param build
     * @param listener
     * @param dataXml
     *            the test report document
     * @return the list of failure info objects
     * @throws IOException
     * @throws InterruptedException
     */
    private List<TestCaseInfo> determineFailedTestCases(AbstractBuild<?, ?> build, BuildListener listener, Document dataXml)
        throws IOException, InterruptedException
    {
        List<TestCaseInfo> failedTestCases = new ArrayList<TestCaseInfo>();

        // get the info from the test report
        if (dataXml != null)
        {
            try
            {
                final XPath xpath = XPathFactory.newInstance().newXPath();

                NodeList nodeList = (NodeList) xpath.evaluate("/testreport/errors/error", dataXml, XPathConstants.NODESET);
                for (int i = 0; i < nodeList.getLength(); i++)
                {
                    Node item = nodeList.item(i);

                    String testCaseName = xpath.evaluate("testCaseName", item);
                    String actionName = xpath.evaluate("actionName", item);
                    String message = xpath.evaluate("message", item);

                    // ensure action name is null if it was not in the test report
                    actionName = StringUtils.defaultIfBlank(actionName, null);

                    failedTestCases.add(new TestCaseInfo(testCaseName, actionName, message));
                }
            }
            catch (XPathExpressionException e)
            {
                // should never happen
                listener.error("Failed to determine failed test cases", e);
            }
        }

        // sort the list by test case name
        Collections.sort(failedTestCases);

        return failedTestCases;
    }

    /**
     * Extracts URL and runtime from the slowest requests in the test report.
     * 
     * @param build
     * @param listener
     * @param dataXml
     *            the test report document
     * @return the list of info objects
     * @throws IOException
     * @throws InterruptedException
     */
    private List<SlowRequestInfo> determineSlowestRequests(AbstractBuild<?, ?> build, BuildListener listener, Document dataXml)
        throws IOException, InterruptedException
    {
        List<SlowRequestInfo> slowestRequests = new ArrayList<SlowRequestInfo>();

        // get the info from the test report
        if (dataXml != null)
        {
            try
            {
                final XPath xpath = XPathFactory.newInstance().newXPath();

                NodeList nodeList = (NodeList) xpath.evaluate("/testreport/general/slowestRequests/request", dataXml,
                                                              XPathConstants.NODESET);
                for (int i = 0; i < nodeList.getLength(); i++)
                {
                    Node item = nodeList.item(i);

                    String url = xpath.evaluate("url", item);
                    String runtime = xpath.evaluate("runtime", item);

                    slowestRequests.add(new SlowRequestInfo(url, runtime));
                }
            }
            catch (XPathExpressionException e)
            {
                // should never happen
                listener.error("Failed to determine slowest requests", e);
            }
        }

        return slowestRequests;
    }

    @Override
    public XltDescriptor getDescriptor()
    {
        return (XltDescriptor) super.getDescriptor();
    }

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener)
    {
        return true;
    }

    public static String getResourcePath(String fileName)
    {
        return "/plugin/" + Jenkins.getInstance().getPlugin(XltDescriptor.PLUGIN_NAME).getWrapper().getShortName() + "/" + fileName;
    }

    public void publishBuildParameters(AbstractBuild<?, ?> build)
    {
        build.addAction(new XltParametersAction(new ArrayList<ParameterValue>(buildParameterMap.values())));
    }

    private void setBuildParameter(ENVIRONMENT_KEYS parameter, String value)
    {
        buildParameterMap.put(parameter, new StringParameterValue(parameter.name(), value));
    }

    private void setBuildParameterXLT_RUN_FAILED(boolean value)
    {
        setBuildParameter(ENVIRONMENT_KEYS.XLT_RUN_FAILED, Boolean.toString(value));
    }

    private void setBuildParameterXLT_CONDITION_FAILED(boolean value)
    {
        setBuildParameter(ENVIRONMENT_KEYS.XLT_CONDITION_FAILED, Boolean.toString(value));
    }

    private void setBuildParameterXLT_CONDITION_ERROR(boolean value)
    {
        setBuildParameter(ENVIRONMENT_KEYS.XLT_CONDITION_ERROR, Boolean.toString(value));
    }

    private void setBuildParameterXLT_CONDITION_CRITICAL(boolean value)
    {
        setBuildParameter(ENVIRONMENT_KEYS.XLT_CONDITION_CRITICAL, Boolean.toString(value));
    }

    private void setBuildParameterXLT_CONDITION_MESSAGE(String message)
    {
        setBuildParameter(ENVIRONMENT_KEYS.XLT_CONDITION_MESSAGE, message);
    }

    private void setBuildParameterXLT_REPORT_URL(String reportURL)
    {
        setBuildParameter(ENVIRONMENT_KEYS.XLT_REPORT_URL, reportURL != null ? reportURL : "");
    }

    public void initializeBuildParameter()
    {
        buildParameterMap = new Hashtable<ENVIRONMENT_KEYS, ParameterValue>();
        setBuildParameterXLT_RUN_FAILED(false);
        setBuildParameterXLT_CONDITION_FAILED(false);
        setBuildParameterXLT_CONDITION_ERROR(false);
        setBuildParameterXLT_CONDITION_CRITICAL(false);
        setBuildParameterXLT_REPORT_URL(null);
        setBuildParameterXLT_CONDITION_MESSAGE("");
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
    {
        initializeBuildParameter();

        try
        {
            initialCleanUp(build, listener);
        }
        catch (Exception e)
        {
            listener.getLogger().println("Cleanup failed: " + e);
            LOGGER.error("Cleanup failed: ", e);
        }

        boolean artifactsSaved = false;
        try
        {
            copyXlt(build, listener);

            String[] agentControllerProperties = configureAgentController(build, listener);
            runMasterController(build, listener, agentControllerProperties);
            createReport(build, listener);
            saveArtifacts(build, listener);
            artifactsSaved = true;

            validateCriteria(build, listener);
        }
        catch (InterruptedException e)
        {
            build.setResult(build.getExecutor().abortResult());
        }
        catch (Exception e)
        {
            build.setResult(Result.FAILURE);
            listener.getLogger().println("Build failed: " + e);
            LOGGER.error("Build failed", e);
        }
        finally
        {
            performPostTestSteps(build, listener, artifactsSaved);

            if (build.getResult() == Result.FAILURE)
            {
                setBuildParameterXLT_RUN_FAILED(true);
            }
            publishBuildParameters(build);
        }
        return true;
    }

    private void performPostTestSteps(AbstractBuild<?, ?> build, BuildListener listener, boolean artifactsSaved)
    {
        // terminate Amazon's EC2 instances
        if (isEC2UsageEnabled())
        {
            try
            {
                terminateEc2Machine(build, listener);
            }
            catch (Exception e)
            {
                listener.getLogger().println("Could not terminate Amazon EC2 instances! " + e);
                LOGGER.error("Could not terminate Amazon EC2 instances!", e);
                build.setResult(Result.FAILURE);
            }
        }

        if (createSummaryReport && artifactsSaved)
        {
            try
            {
                createSummaryReport(build, listener);
            }
            catch (Exception e)
            {
                listener.getLogger().println("Summary report failed. " + e);
                LOGGER.error("Summary report failed", e);
            }
        }

        if (createTrendReport && artifactsSaved)
        {
            try
            {
                createTrendReport(build, listener);
            }
            catch (Exception e)
            {
                listener.getLogger().println("Trend report failed. " + e);
                LOGGER.error("Trend report failed", e);
            }
        }

        listener.getLogger().println("\n\n-----------------------------------------------------------------\nArchive logs ...\n");
        // save logs
        try
        {
            saveArtifact(getXltLogFolder(build), getBuildLogsFolder(build));
        }
        catch (Exception e)
        {
            listener.getLogger().println("Archive logs failed: " + e);
            LOGGER.error("Archive logs failed: ", e);
        }

        listener.getLogger().println("\n\n-----------------------------------------------------------------\nCleanup ...\n");
        // delete any temporary directory with local XLT
        try
        {
            FilePath tempProjectFolder = getTemporaryXltProjectFolder(build);
            tempProjectFolder.deleteRecursive();

            FilePath tempFolder = getTemporaryXltBaseFolder(build);
            if (tempFolder.exists() || tempFolder.list() == null || tempFolder.list().isEmpty())
            {
                tempFolder.delete();
            }
        }
        catch (Exception e)
        {
            listener.getLogger().println("Cleanup Failed: " + e);
            LOGGER.error("Cleanup Failed: ", e);
        }

        listener.getLogger().println("\nFinished");
    }

    private boolean isEC2UsageEnabled()
    {
        return agentControllerConfig.type.equals(AgentControllerConfig.TYPE.ec2.toString());
    }

    private String[] configureAgentController(AbstractBuild<?, ?> build, BuildListener listener) throws Exception
    {
        listener.getLogger().println("-----------------------------------------------------------------\n"
                                         + "Configuring agent controllers ...\n");

        String[] agentControllerUrls = new String[0];
        if (agentControllerConfig.type.equals(AgentControllerConfig.TYPE.embedded.toString()))
        {
            listener.getLogger().println("Set to embedded mode");
        }
        else
        {
            if (agentControllerConfig.type.equals(AgentControllerConfig.TYPE.list.toString()))
            {
                listener.getLogger().println("Read agent controller URLs from configuration:\n");
                agentControllerUrls = parseAgentControllerUrls(agentControllerConfig.urlList);
            }
            else if (agentControllerConfig.type.equals(AgentControllerConfig.TYPE.file.toString()))
            {
                FilePath file = new FilePath(getTestSuiteConfigFolder(build), agentControllerConfig.urlFile);

                listener.getLogger().printf("Read agent controller URLs from file %s:\n\n", file);

                if (agentControllerConfig.urlFile != null)
                {
                    agentControllerUrls = parseAgentControllerUrlsFromFile(file);
                }
            }
            // run Amazon's EC2 machine
            else if (isEC2UsageEnabled())
            {
                agentControllerUrls = startEc2Machine(build, listener);
            }

            if (ArrayUtils.isEmpty(agentControllerUrls))
            {
                throw new IllegalStateException("No agent controller URLs found.");
            }
            else
            {
                for (String agentControllerUrl : agentControllerUrls)
                {
                    listener.getLogger().println(agentControllerUrl);
                }
            }
        }

        listener.getLogger().println("\nFinished");
        return agentControllerUrls;
    }

    private String[] expandAgentControllerUrls(String[] agentControllerUrls)
    {
        String[] expandedUrls = new String[agentControllerUrls.length];
        for (int i = 0; i < agentControllerUrls.length; i++)
        {
            expandedUrls[i] = String.format("com.xceptance.xlt.mastercontroller.agentcontrollers.ac%03d.url=%s", i + 1,
                                            agentControllerUrls[i]);
        }
        return expandedUrls;
    }

    private String[] startEc2Machine(AbstractBuild<?, ?> build, BuildListener listener) throws Exception
    {
        listener.getLogger()
                .println("-----------------------------------------------------------------\nStarting agent controller with EC2 admin tool ...\n");

        // build the EC2 admin command line
        List<String> commandLine = new ArrayList<String>();

        if (isBuildOnWindows(build))
        {
            commandLine.add("cmd.exe");
            commandLine.add("/c");
            commandLine.add("ec2_admin.cmd");
        }
        else
        {
            commandLine.add("./ec2_admin.sh");
        }
        commandLine.add("run");
        commandLine.add(agentControllerConfig.getRegion());
        commandLine.add(agentControllerConfig.getAmiId());
        commandLine.add(agentControllerConfig.getEc2Type());
        commandLine.add(agentControllerConfig.getCountMachines());
        commandLine.add(agentControllerConfig.getTagName());

        String securityGroupsParameter = getSecurityGroupParameter();
        if (StringUtils.isNotBlank(securityGroupsParameter))
        {
            commandLine.add("-s");
            commandLine.add(securityGroupsParameter);
        }

        String awsUserData = agentControllerConfig.getAwsUserData();
        if (StringUtils.isNotBlank(awsUserData))
        {
            FilePath userData = new FilePath(getXltConfigFolder(build), "userData.txt");
            userData.write(awsUserData, null);

            commandLine.add("-uf");
            commandLine.add(userData.absolutize().getRemote());
        }

        FilePath acUrlFile = new FilePath(getXltConfigFolder(build), "acUrls.properties");
        commandLine.add("-o");
        commandLine.add(acUrlFile.absolutize().getRemote());

        appendEc2Properties(build, listener);

        // run the EC2 admin tool
        FilePath workingDirectory = getXltBinFolder(build);
        int commandResult = executeCommand(getBuildNodeIfOnlineOrFail(build), workingDirectory, commandLine, listener.getLogger());
        listener.getLogger().println("EC2 admin tool returned with exit code: " + commandResult);

        if (commandResult != 0)
        {
            throw new Exception("Failed to start instances. EC2 admin tool returned with exit code: " + commandResult);
        }
        // open the file that contains the agent controller URLs
        listener.getLogger().printf("\nRead agent controller URLs from file %s:\n\n", acUrlFile);

        // read the lines from agent controller file
        List<?> lines = IOUtils.readLines(new StringReader(acUrlFile.readToString()));
        List<String> urls = new ArrayList<String>(lines.size());
        for (Object eachLine : lines)
        {
            String line = (String) eachLine;
            if (StringUtils.isNotBlank(line))
            {
                int start = line.indexOf('=') + 1;
                if (start > 0 && start < line.length() - 1)
                {
                    String url = line.substring(start);
                    if (StringUtils.isNotBlank(line))
                    {
                        urls.add(url.trim());
                    }
                }
            }
        }

        return urls.toArray(new String[urls.size()]);
    }

    private String getSecurityGroupParameter()
    {
        String securityGroupsParameter = "";
        List<AWSSecurityGroup> securityGroups = agentControllerConfig.getSecurityGroups();
        for (int i = 0; i < securityGroups.size(); i++)
        {
            if (i > 0)
            {
                securityGroupsParameter += ",";
            }
            securityGroupsParameter += securityGroups.get(i).getID();
        }
        return StringUtils.isNotBlank(securityGroupsParameter) ? securityGroupsParameter : null;
    }

    private void appendEc2Properties(AbstractBuild<?, ?> build, BuildListener listener) throws Exception
    {
        // append properties to the end of the file
        // the last properties will win so this would overwrite the original properties
        FilePath ec2PropertiesFile = new FilePath(getXltConfigFolder(build), "ec2_admin.properties");
        BufferedWriter writer = null;
        try
        {
            String originalContent = ec2PropertiesFile.readToString();
            OutputStream outStream = ec2PropertiesFile.write(); // this will overwrite the existing file so we need the
                                                                // original content to recreate the old content
            writer = new BufferedWriter(new OutputStreamWriter(outStream));

            writer.write(originalContent);
            writer.newLine();

            if (StringUtils.isNotBlank(agentControllerConfig.getAwsCredentials()))
            {
                List<AwsCredentials> availableCredentials = CredentialsProvider.lookupCredentials(AwsCredentials.class, build.getParent(),
                                                                                                  ACL.SYSTEM, new DomainRequirement[0]);

                AwsCredentials credentials = CredentialsMatchers.firstOrNull(availableCredentials,
                                                                             CredentialsMatchers.withId(agentControllerConfig.getAwsCredentials()));
                if (credentials != null)
                {
                    writer.write("aws.accessKey=" + Secret.toString(credentials.getAccessKey()));
                    writer.newLine();
                    writer.write("aws.secretKey=" + Secret.toString(credentials.getSecretKey()));
                }
                else
                {
                    LOGGER.warn("Credentials no longer available. (id: \"" + agentControllerConfig.getAwsCredentials() + "\")");
                    listener.getLogger().println("Credentials no longer available. (id: \"" + agentControllerConfig.getAwsCredentials() +
                                                     "\")");
                    throw new Exception("Credentials no longer available.");
                }
            }
            writer.flush();
        }
        finally
        {
            if (writer != null)
            {
                IOUtils.closeQuietly(writer);
            }
        }
    }

    private void terminateEc2Machine(AbstractBuild<?, ?> build, BuildListener listener) throws Exception
    {
        listener.getLogger()
                .println("-----------------------------------------------------------------\nTerminating agent controller with EC2 admin tool ...\n");

        // build the EC2 admin command line
        List<String> commandLine = new ArrayList<String>();

        if (isBuildOnWindows(build))
        {
            commandLine.add("cmd.exe");
            commandLine.add("/c");
            commandLine.add("ec2_admin.cmd");
        }
        else
        {
            commandLine.add("./ec2_admin.sh");
        }
        commandLine.add("terminate");
        commandLine.add(agentControllerConfig.getRegion());
        commandLine.add(agentControllerConfig.getTagName());

        // run the EC2 admin tool
        FilePath workingDirectory = getXltBinFolder(build);
        int commandResult = executeCommand(getBuildNodeIfOnlineOrFail(build), workingDirectory, commandLine, listener.getLogger());
        listener.getLogger().println("EC2 admin tool returned with exit code: " + commandResult);

        if (commandResult != 0)
        {
            throw new Exception("Failed to terminate instances. EC2 admin tool returned with exit code: " + commandResult);
        }
    }

    private FilePath getTestSuiteFolder(AbstractBuild<?, ?> build)
    {
        if (StringUtils.isBlank(pathToTestSuite))
        {
            return build.getModuleRoot();
        }
        else
        {
            return new FilePath(build.getModuleRoot(), pathToTestSuite);
        }
    }

    private FilePath getTestSuiteConfigFolder(AbstractBuild<?, ?> build)
    {
        return new FilePath(getTestSuiteFolder(build), "config");
    }

    private FilePath getTestPropertiesFile(AbstractBuild<?, ?> build)
    {
        if (StringUtils.isBlank(testPropertiesFile))
        {
            return null;
        }
        else
        {
            return new FilePath(getTestSuiteConfigFolder(build), testPropertiesFile);
        }
    }

    private void initialCleanUp(AbstractBuild<?, ?> build, BuildListener listener)
        throws IOException, InterruptedException, BuildNodeGoneException
    {
        listener.getLogger().println("-----------------------------------------------------------------\n"
                                         + "Cleaning up project directory ...\n");

        getTemporaryXltProjectFolder(build).deleteRecursive();

        listener.getLogger().println("\nFinished");
    }

    private void copyXlt(AbstractBuild<?, ?> build, BuildListener listener) throws Exception
    {
        listener.getLogger().println("-----------------------------------------------------------------\nCopying XLT ...\n");

        // the directory with the XLT template installation
        if (StringUtils.isBlank(getXltTemplateDir()))
        {
            throw new Exception("Path to xlt not set");
        }

        FilePath srcDir = getXltTemplateFilePath();
        listener.getLogger().println("XLT template directory: " + srcDir.getRemote());

        if (!srcDir.exists())
        {
            throw new Exception("The directory does not exists: " + srcDir.getRemote());
        }
        else if (!srcDir.isDirectory())
        {
            throw new Exception("The path does not point to a directory: " + srcDir.getRemote());
        }
        else if (!new FilePath(new FilePath(srcDir, "bin"), "mastercontroller.sh").exists() ||
                 !new FilePath(new FilePath(srcDir, "bin"), "mastercontroller.cmd").exists())
        {
            throw new Exception("No \"mastercontroller\" script found for path: " + new FilePath(srcDir, "bin").getRemote());
        }

        // the target directory in the project folder
        FilePath destDir = getTemporaryXltFolder(build);
        listener.getLogger().println("Target directory: " + destDir.getRemote());

        // copy XLT to a remote directory
        String excludePattern = "config/scriptdoc/, config/externaldataconfig.xml.sample, config/scriptdocgenerator.properties";
        int copyCount = srcDir.copyRecursiveTo("bin/**, config/**, lib/**", excludePattern, destDir);
        if (copyCount == 0 || destDir.list() == null || destDir.list().isEmpty())
        {
            throw new Exception("Copy template failed. Nothing was copyed from xlt template \"" + srcDir.getRemote() +
                                "\" to destination \"" + destDir + "\"");
        }

        // make XLT start scripts executable
        FilePath workingDirectory = getXltBinFolder(build);

        for (FilePath child : workingDirectory.list())
        {
            child.chmod(0777);
        }

        listener.getLogger().println("\nFinished");
    }

    private void runMasterController(AbstractBuild<?, ?> build, BuildListener listener, String[] agentControllerUrls) throws Exception
    {
        listener.getLogger().println("-----------------------------------------------------------------\nRunning master controller ...\n");

        // build the master controller command line
        List<String> commandLine = new ArrayList<String>();

        if (isBuildOnWindows(build))
        {
            commandLine.add("cmd.exe");
            commandLine.add("/c");
            commandLine.add("mastercontroller.cmd");
        }
        else
        {
            commandLine.add("./mastercontroller.sh");
        }

        if (agentControllerConfig.type.equals(AgentControllerConfig.TYPE.embedded.toString()))
        {
            commandLine.add("-embedded");

            // use any free port
            commandLine.add("-Dcom.xceptance.xlt.agentcontroller.port=0");
        }
        else
        {
            // set the initialResponseTimeout property
            commandLine.add("-Dcom.xceptance.xlt.mastercontroller.initialResponseTimeout=" + (initialResponseTimeout * 1000));

            // set agent controllers
            String[] agentControllerProperties = expandAgentControllerUrls(agentControllerUrls);
            for (int i = 0; i < agentControllerProperties.length; i++)
            {
                commandLine.add("-D" + agentControllerProperties[i]);
            }
        }

        commandLine.add("-auto");

        if (StringUtils.isNotBlank(testPropertiesFile))
        {
            validateTestPropertiesFile(build);
            commandLine.add("-testPropertiesFile");
            commandLine.add(testPropertiesFile);
        }

        validateTestSuiteDirectory(build);
        commandLine.add("-Dcom.xceptance.xlt.mastercontroller.testSuitePath=" + getTestSuiteFolder(build).getRemote());
        commandLine.add("-Dcom.xceptance.xlt.mastercontroller.results=" + getXltResultFolder(build).getRemote());

        // run the master controller
        FilePath workingDirectory = getXltBinFolder(build);
        int commandResult = executeCommand(getBuildNodeIfOnlineOrFail(build), workingDirectory, commandLine, listener.getLogger());
        listener.getLogger().println("Master controller returned with exit code: " + commandResult);

        if (commandResult != 0)
        {
            throw new Exception("Master controller returned with exit code: " + commandResult);
        }
    }

    public static boolean isBuildOnWindows(AbstractBuild<?, ?> build) throws BuildNodeGoneException
    {
        return !isBuildOnUnix(build);
    }

    public static boolean isBuildOnUnix(AbstractBuild<?, ?> build) throws BuildNodeGoneException
    {
        return getBuildNodeIfOnlineOrFail(build).createLauncher(null).isUnix();
    }

    public static boolean isBuildNodeOnline(AbstractBuild<?, ?> build)
    {
        return getBuildNode(build) != null;
    }

    public static hudson.model.Node getBuildNodeIfOnlineOrFail(AbstractBuild<?, ?> build) throws BuildNodeGoneException
    {
        hudson.model.Node node = getBuildNode(build);
        if (node != null)
        {
            return node;
        }
        throw new BuildNodeGoneException("Build node is not available");
    }

    public static hudson.model.Node getBuildNode(AbstractBuild<?, ?> build)
    {
        hudson.model.Node node = build.getBuiltOn();
        if (node != null && node.toComputer() != null && node.toComputer().isOnline())
        {
            return node;
        }
        return null;
    }

    public static boolean isRelativeFilePathOnNode(AbstractBuild<?, ?> build, String filePath) throws BuildNodeGoneException
    {
        if (isBuildOnUnix(build) && (filePath.startsWith("/") || filePath.startsWith("~")))
        {
            return false;
        }
        else if (filePath.startsWith("\\") || filePath.startsWith("\\") || filePath.contains(":"))
        {
            return false;
        }
        return true;
    }

    private void validateTestPropertiesFile(AbstractBuild<?, ?> build) throws Exception
    {
        if (StringUtils.isBlank(testPropertiesFile))
        {
            return;
        }

        if (!isRelativeFilePathOnNode(build, testPropertiesFile))
        {
            throw new Exception("The test properties file path must be relative to the \"<testSuite>/config/\" directory. (" +
                                testPropertiesFile + ")");
        }

        FilePath testProperties = getTestPropertiesFile(build);
        if (testProperties == null || !testProperties.exists())
        {
            throw new Exception("The test properties file does not exists. (" + testProperties.getRemote() + ")");
        }
        else if (testProperties.isDirectory())
        {
            throw new Exception("The test properties file path  must specify a file, not a directory. (" + testProperties.getRemote() + ")");
        }
    }

    private void validateTestSuiteDirectory(AbstractBuild<?, ?> build) throws Exception
    {
        FilePath testSuiteDirectory = getTestSuiteFolder(build);
        if (!testSuiteDirectory.exists())
        {
            throw new Exception("The test suite path does not exists. (" + testSuiteDirectory.getRemote() + ")");
        }
        else if (!testSuiteDirectory.isDirectory())
        {
            throw new Exception("The test suite path must specify a directory, not a file. (" + testSuiteDirectory.getRemote() + ")");
        }
        else if (testSuiteDirectory.list() == null || testSuiteDirectory.list().isEmpty())
        {
            throw new Exception("The test suite directory is empty. (" + testSuiteDirectory.getRemote() + ")");
        }
    }

    private void createReport(AbstractBuild<?, ?> build, BuildListener listener) throws Exception
    {
        listener.getLogger().println("-----------------------------------------------------------------\nRunning report generator ...\n");

        FilePath resultSubFolder = getFirstSubFolder(getXltResultFolder(build));
        if (resultSubFolder == null || resultSubFolder.list() == null || resultSubFolder.list().isEmpty())
        {
            throw new Exception("No results found at: " + getXltResultFolder(build).getRemote());
        }
        resultSubFolder.moveAllChildrenTo(getXltResultFolder(build));

        // build the master controller command line
        List<String> commandLine = new ArrayList<String>();

        if (isBuildOnWindows(build))
        {
            commandLine.add("cmd.exe");
            commandLine.add("/c");
            commandLine.add("create_report.cmd");
        }
        else
        {
            commandLine.add("./create_report.sh");
        }

        commandLine.add("-o");
        commandLine.add(getXltReportFolder(build).getRemote());
        commandLine.add("-linkToResults");
        commandLine.add("yes");
        commandLine.add(getXltResultFolder(build).getRemote());
        commandLine.add("-Dmonitoring.trackSlowestRequests=true");

        // run the report generator
        FilePath workingDirectory = getXltBinFolder(build);
        int commandResult = executeCommand(getBuildNodeIfOnlineOrFail(build), workingDirectory, commandLine, listener.getLogger());
        listener.getLogger().println("Report generator returned with exit code: " + commandResult);

        if (commandResult != 0)
        {
            throw new Exception("Report generator returned with exit code: " + commandResult);
        }
    }

    private void saveArtifacts(AbstractBuild<?, ?> build, BuildListener listener)
        throws IOException, InterruptedException, BuildNodeGoneException
    {
        listener.getLogger()
                .println("\n\n-----------------------------------------------------------------\nArchive results and report...\n");
        // save load test results and report (copy from node)
        saveArtifact(getXltResultFolder(build), getBuildResultFolder(build));
        saveArtifact(getXltReportFolder(build), getBuildReportFolder(build));
        setBuildParameterXLT_REPORT_URL(getBuildReportURL(build));
    }

    private void saveArtifact(FilePath srcFolder, FilePath destFolder) throws IOException, InterruptedException
    {
        if (srcFolder != null && srcFolder.isDirectory() && destFolder != null)
        {
            // move folder to save time
            srcFolder.copyRecursiveTo(destFolder);
            srcFolder.deleteRecursive();
        }
    }

    private int executeCommand(hudson.model.Node buildNode, FilePath workingDirectory, List<String> commandLine, PrintStream logger)
        throws IOException, InterruptedException
    {
        StreamTaskListener streamListener = new StreamTaskListener((OutputStream) logger);

        Launcher launcher = buildNode.createLauncher(streamListener);
        launcher.decorateFor(buildNode);

        ProcStarter starter = launcher.launch();
        starter.pwd(workingDirectory);
        starter.cmds(commandLine);

        starter.stdout(logger);

        // starts process and waits for its completion
        return starter.join();
    }

    private void createSummaryReport(AbstractBuild<?, ?> build, BuildListener listener) throws Exception
    {
        listener.getLogger().println("-----------------------------------------------------------------\nCreating summary report ...\n");

        // copy the results of the last n builds to the summary results directory
        copyResults(build, listener);

        // build report generator command line
        List<String> commandLine = new ArrayList<String>();

        if (SystemUtils.IS_OS_WINDOWS)
        {
            commandLine.add("cmd.exe");
            commandLine.add("/c");
            commandLine.add("create_report.cmd");
        }
        else
        {
            commandLine.add("./create_report.sh");
        }

        FilePath outputFolder = getSummaryReportFolder(build.getProject());
        commandLine.add("-o");
        commandLine.add(outputFolder.getRemote());

        commandLine.add(getSummaryResultsFolder(build.getProject()).getRemote());
        // run the report generator on the master
        int commandResult = executeCommand(Jenkins.getInstance(), getXltBinFolderOnMaster(), commandLine, listener.getLogger());
        listener.getLogger().println("Load report generator returned with exit code: " + commandResult);
        if (commandResult != 0)
        {
            build.setResult(Result.FAILURE);
        }
    }

    private void copyResults(AbstractBuild<?, ?> currentBuild, BuildListener listener) throws InterruptedException, IOException
    {
        // recreate a fresh summary results directory
        FilePath summaryResultsFolder = getSummaryResultsFolder(currentBuild.getProject());

        summaryResultsFolder.deleteRecursive();
        summaryResultsFolder.mkdirs();

        // copy config from the current build's results
        FilePath configFolder = getBuildResultConfigFolder(currentBuild);
        FilePath summaryConfigFolder = getSummaryResultsConfigFolder(currentBuild.getProject());

        configFolder.copyRecursiveTo(summaryConfigFolder);

        // copy timer data from the last n builds
        List<AbstractBuild<?, ?>> builds = new ArrayList<AbstractBuild<?, ?>>(
                                                                              currentBuild.getPreviousBuildsOverThreshold(numberOfBuildsForSummaryReport - 1,
                                                                                                                          Result.UNSTABLE));
        builds.add(0, currentBuild);

        for (AbstractBuild<?, ?> build : builds)
        {
            FilePath resultsFolder = getBuildResultFolder(build);
            if (resultsFolder.isDirectory())
            {
                copyResults(summaryResultsFolder, resultsFolder, build.getNumber());
            }
        }
    }

    private void copyResults(FilePath targetDir, FilePath srcDir, int buildNumber) throws IOException, InterruptedException
    {
        List<FilePath> files = srcDir.list();
        if (files != null)
        {
            for (FilePath file : files)
            {
                if (file.isDirectory())
                {
                    copyResults(new FilePath(targetDir, file.getName()), file, buildNumber);
                }
                else if (file.getName().startsWith("timers.csv"))
                {
                    // regular timer files
                    // use File instead of FilePath to prevent error on Windows systems
                    // in this case using File is ok, because copying results is done on master
                    File renamedResultsFile = new File(targetDir.getRemote(), file.getName() + "." + buildNumber);
                    FileUtils.copyFile(new File(file.getRemote()), renamedResultsFile);
                }
                else if (file.getName().endsWith(".csv"))
                {
                    // WebDriver timer files
                    FileUtils.copyFileToDirectory(new File(file.getRemote()), new File(targetDir.getRemote()));
                }
            }
        }
    }

    private void createTrendReport(AbstractBuild<?, ?> build, BuildListener listener) throws Exception
    {
        listener.getLogger().println("-----------------------------------------------------------------\nCreating trend report ...\n");

        List<String> commandLine = new ArrayList<String>();

        if (SystemUtils.IS_OS_WINDOWS)
        {
            commandLine.add("cmd.exe");
            commandLine.add("/c");
            commandLine.add("create_trend_report.cmd");
        }
        else
        {
            commandLine.add("./create_trend_report.sh");
        }

        FilePath trendReportDest = getTrendReportFolder(build.getProject());
        commandLine.add("-o");
        commandLine.add(trendReportDest.getRemote());

        // get some previous builds that were either UNSTABLE or SUCCESS
        List<AbstractBuild<?, ?>> builds = new ArrayList<AbstractBuild<?, ?>>(
                                                                              build.getPreviousBuildsOverThreshold(numberOfBuildsForTrendReport - 1,
                                                                                                                   Result.UNSTABLE));
        // add the current build
        builds.add(build);

        // add the report directories
        int numberOfBuildsWithReports = 0;
        for (AbstractBuild<?, ?> eachBuild : builds)
        {
            FilePath reportDirectory = getBuildReportFolder(eachBuild);
            if (reportDirectory.isDirectory())
            {
                commandLine.add(reportDirectory.getRemote());
                numberOfBuildsWithReports++;
            }
        }

        // check whether we have enough builds with reports to create a trend report
        if (numberOfBuildsWithReports >= 2)
        {
            // run trend report generator on master
            int commandResult = executeCommand(Jenkins.getInstance(), getXltBinFolderOnMaster(), commandLine, listener.getLogger());
            listener.getLogger().println("Trend report generator returned with exit code: " + commandResult);
            if (commandResult != 0)
            {
                build.setResult(Result.FAILURE);
            }
        }
        else
        {
            listener.getLogger().println("Cannot create trend report because no previous reports available!");
        }
    }
}
