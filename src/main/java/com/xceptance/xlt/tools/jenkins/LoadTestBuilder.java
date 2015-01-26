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
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import hudson.tasks.Builder;
import hudson.util.StreamTaskListener;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import jenkins.model.Jenkins;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.xceptance.xlt.tools.jenkins.Chart.ChartLine;
import com.xceptance.xlt.tools.jenkins.Chart.ChartLineValue;
import com.xceptance.xlt.tools.jenkins.config.option.MarkCriticalOption;
import com.xceptance.xlt.tools.jenkins.config.option.SummaryReportOption;
import com.xceptance.xlt.tools.jenkins.config.option.TrendReportOption;

/**
 * Readout the configuration of XLT in Jenkins, perform load testing and plot build results on project page.
 * 
 * @author Michael Aleithe, Randolph Straub
 */
public class LoadTestBuilder extends Builder
{
    private final String testPropertiesFile;

    private AgentControllerConfig agentControllerConfig;

    private final String xltConfig;

    private final String xltTemplateDir;

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

    transient public static final Logger LOGGER = Logger.getLogger(LoadTestBuilder.class);

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
        XLT_RUN_FAILED, XLT_CONDITION_CRITICAL, XLT_CONDITION_FAILED, XLT_CONDITION_ERROR
    };

    static
    {
        try
        {
            File logFile = new File(
                                    new File(
                                             Jenkins.getInstance().getPlugin(XltDescriptor.PLUGIN_NAME).getWrapper().baseResourceURL.toURI()),
                                    "xltPlugin.log");
            LOGGER.addAppender(new FileAppender(
                                                new PatternLayout("%d{yyyy-MMM-dd} : %d{HH:mm:ss,SSS} | [%t] %p %C.%M line:%L | %x - %m%n"),
                                                logFile.getAbsolutePath(), true));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        catch (URISyntaxException e)
        {
            e.printStackTrace();
        }
    }

    @DataBoundConstructor
    public LoadTestBuilder(String xltTemplateDir, String testPropertiesFile, String xltConfig, int plotWidth, int plotHeight,
                           String plotTitle, String builderID, boolean isPlotVertical, TrendReportOption trendReportOption,
                           SummaryReportOption summaryReportOption, int numberOfBuildsForSummaryReport,
                           AgentControllerConfig agentControllerConfig, String timeFormatPattern, boolean showBuildNumber,
                           MarkCriticalOption markCriticalOption, boolean markCriticalEnabled)
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
        this.testPropertiesFile = StringUtils.defaultIfBlank(testPropertiesFile, null);
        this.agentControllerConfig = (agentControllerConfig != null) ? agentControllerConfig : new AgentControllerConfig();

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

    private String[] parseAgentControllerUrlsFromFile(FilePath file) throws IOException
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
            LOGGER.warn("No test data found for build. (build: \"" + build.number + "\" searchLocation: \"" + testDataFile.getRemote() +
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
                        LOGGER.warn("No chart found for plot. (build: \"" + eachBuild.number + "\" plotID: \"" + eachPlotID + "\")");
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
                                LOGGER.warn("No line found for value. (build: \"" + eachBuild.number + "\" valueID: \"" + eachValueID +
                                            "\" chartID:\"" + chart.getChartID() + "\")");
                                continue;
                            }

                            boolean addedValueToLine = false;
                            if (dataXml == null)
                            {
                                LOGGER.warn("No test data found for build. (build: \"" + eachBuild.number + "\")");
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
            LOGGER.warn("Plot config parameter \"showNoValues\" is undefined (plotID: \"" + plotID + "\")");
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

    private FilePath getXltResultsFolder(AbstractBuild<?, ?> build)
    {
        return new FilePath(getXltFolder(build), "results");
    }

    private FilePath getXltLogFolder(AbstractBuild<?, ?> build)
    {
        return new FilePath(getXltFolder(build), "log");
    }

    private FilePath getFirstXltResultsFolder(AbstractBuild<?, ?> build)
    {
        return getFirstSubFolder(getXltResultsFolder(build));
    }

    private FilePath getXltReportsFolder(AbstractBuild<?, ?> build)
    {
        return new FilePath(getXltFolder(build), "reports");
    }

    private FilePath getFirstXltReportFolder(AbstractBuild<?, ?> build)
    {
        return getFirstSubFolder(getXltReportsFolder(build));
    }

    private FilePath getFirstSubFolder(FilePath dir)
    {
        try
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
        }
        catch (IOException e)
        {
            LOGGER.error("", e);
        }
        catch (InterruptedException e)
        {
            LOGGER.error("", e);
        }
        return null;
    }

    private FilePath getBuildResultConfigFolder(AbstractBuild<?, ?> build)
    {
        return new FilePath(getBuildResultsFolder(build), "config");
    }

    private FilePath getBuildLogsFolder(AbstractBuild<?, ?> build)
    {
        return new FilePath(new FilePath(build.getArtifactsDir()), builderID + "/log");
    }

    private FilePath getBuildReportFolder(AbstractBuild<?, ?> build)
    {
        return new FilePath(new FilePath(build.getArtifactsDir()), builderID + "/report/" + Integer.toString(build.getNumber()));
    }

    private FilePath getBuildResultsFolder(AbstractBuild<?, ?> build)
    {
        return new FilePath(new FilePath(build.getArtifactsDir()), builderID + "/results");
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

    private FilePath getXltFolder(AbstractBuild<?, ?> build)
    {
        return new FilePath(build.getWorkspace(), Integer.toString(build.getNumber()));
    }

    private FilePath getXltTemplateFilePath()
    {
        return XltDescriptor.resolvePath(getXltTemplateDir());
    }

    private FilePath getXltBinFolder(AbstractBuild<?, ?> build)
    {
        return new FilePath(getXltFolder(build), "bin");
    }

    private FilePath getXltBinFolderOnMaster()
    {
        return new FilePath(getXltTemplateFilePath(), "bin");
    }

    private FilePath getXltConfigFolder(AbstractBuild<?, ?> build)
    {
        return new FilePath(getXltFolder(build), "config");
    }

    private List<CriteriaResult> validateCriteria(AbstractBuild<?, ?> build, BuildListener listener)
        throws IOException, InterruptedException
    {
        listener.getLogger().println("-----------------------------------------------------------------\n"
                                         + "Checking success criteria ...\n");

        List<CriteriaResult> failedAlerts = new ArrayList<CriteriaResult>();

        Document dataXml = getDataDocument(build);
        if (dataXml == null)
        {
            CriteriaResult criteriaResult = CriteriaResult.error("No test data found.");
            failedAlerts.add(criteriaResult);
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
                            LOGGER.debug("No condition for criteria. (criteriaID: \"" + eachID + "\")");
                            continue;
                        }
                        if (StringUtils.isBlank(xPath))
                        {
                            CriteriaResult criteriaResult = CriteriaResult.error("No xPath for Criteria");
                            criteriaResult.setCriteriaID(eachID);
                            failedAlerts.add(criteriaResult);
                            continue;
                        }
                        String conditionPath = xPath + condition;

                        Element node = (Element) XPathFactory.newInstance().newXPath().evaluate(xPath, dataXml, XPathConstants.NODE);
                        if (node == null)
                        {
                            CriteriaResult criteriaResult = CriteriaResult.error("No result for XPath");
                            criteriaResult.setCriteriaID(eachID);
                            criteriaResult.setXPath(xPath);
                            failedAlerts.add(criteriaResult);
                            continue;
                        }

                        // test the condition
                        Node result = (Node) XPathFactory.newInstance().newXPath().evaluate(conditionPath, dataXml, XPathConstants.NODE);
                        if (result == null)
                        {
                            String value = XPathFactory.newInstance().newXPath().evaluate(xPath, dataXml);
                            CriteriaResult criteriaResult = CriteriaResult.failed("Condition");
                            criteriaResult.setCriteriaID(eachID);
                            criteriaResult.setValue(value);
                            criteriaResult.setCondition(condition);
                            criteriaResult.setXPath(xPath);
                            failedAlerts.add(criteriaResult);
                            continue;
                        }
                    }
                    catch (JSONException e)
                    {
                        CriteriaResult criteriaResult = CriteriaResult.error("Failed to get parameter from configuration");
                        criteriaResult.setCriteriaID(eachID);
                        criteriaResult.setExceptionMessage(e.getMessage());
                        criteriaResult.setCauseMessage(e.getCause() != null ? e.getCause().getMessage() : null);
                        failedAlerts.add(criteriaResult);
                    }
                    catch (XPathExpressionException e)
                    {
                        CriteriaResult criteriaResult = CriteriaResult.error("Incorrect xPath expression");
                        criteriaResult.setCriteriaID(eachID);
                        criteriaResult.setCondition(condition);
                        criteriaResult.setXPath(xPath);
                        criteriaResult.setExceptionMessage(e.getMessage());
                        criteriaResult.setCauseMessage(e.getCause() != null ? e.getCause().getMessage() : null);
                        failedAlerts.add(criteriaResult);
                    }
                }
            }
            catch (JSONException e)
            {
                CriteriaResult criteriaResult = CriteriaResult.error("Failed to start process criteria.");
                criteriaResult.setExceptionMessage(e.getMessage());
                criteriaResult.setCauseMessage(e.getCause() != null ? e.getCause().getMessage() : null);
                failedAlerts.add(criteriaResult);
            }
        }
        return failedAlerts;
    }

    private void validateCriterias(AbstractBuild<?, ?> build, BuildListener listener) throws IOException, InterruptedException
    {
        List<CriteriaResult> failedAlerts = validateCriteria(build, listener);

        if (!failedAlerts.isEmpty())
        {
            listener.getLogger().println();
            for (CriteriaResult eachAlert : failedAlerts)
            {
                listener.getLogger().println(eachAlert.getLogMessage());
            }
            listener.getLogger().println();
            listener.getLogger().println("Set state to UNSTABLE");
            build.setResult(Result.UNSTABLE);
        }

        XltRecorderAction recorderAction = new XltRecorderAction(build, failedAlerts, builderID);
        build.getActions().add(recorderAction);

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
    }

    private void checkForCritical(AbstractBuild<?, ?> currentBuild)
    {
        if (markCriticalEnabled &&
            (markCriticalBuildCount > 0 && markCriticalConditionCount > 0 && markCriticalBuildCount >= markCriticalConditionCount))
        {
            List<AbstractBuild<?, ?>> builds = new ArrayList<AbstractBuild<?, ?>>();
            builds.addAll(getBuilds(currentBuild.getProject(), 0, markCriticalBuildCount));

            int failedCriteriaBuilds = 0;
            for (AbstractBuild<?, ?> eachBuild : builds)
            {
                XltRecorderAction recorderAction = eachBuild.getAction(XltRecorderAction.class);
                if (recorderAction != null)
                {
                    if (!recorderAction.getFailedAlerts().isEmpty())
                    {
                        failedCriteriaBuilds++;
                        if (failedCriteriaBuilds == markCriticalConditionCount)
                        {
                            setBuildParameterXLT_CONDITION_CRITICAL(true);
                            break;
                        }
                    }
                }
            }
        }
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

    public String getResourcePath(String fileName)
    {
        return "/plugin/" + Jenkins.getInstance().getPlugin(XltDescriptor.PLUGIN_NAME).getWrapper().getShortName() + "/" + fileName;
    }

    public void publishBuildParameters(AbstractBuild<?, ?> build)
    {
        build.addAction(new ParametersAction(new ArrayList<ParameterValue>(buildParameterMap.values()))
        {
            @Override
            public String getDisplayName()
            {
                return "XLT Parameters";
            }

            @Override
            public String getIconFileName()
            {
                return getResourcePath("logo_24_24.png");
            }
        });
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

    public void initializeBuildParameter()
    {
        buildParameterMap = new Hashtable<ENVIRONMENT_KEYS, ParameterValue>();
        setBuildParameterXLT_RUN_FAILED(false);
        setBuildParameterXLT_CONDITION_FAILED(false);
        setBuildParameterXLT_CONDITION_ERROR(false);
        setBuildParameterXLT_CONDITION_CRITICAL(false);
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
    {
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
            saveArtifacts(build, listener);
            artifactsSaved = true;

            validateCriterias(build, listener);
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
            listener.getLogger().println("Failed to archive xlt logs");
            LOGGER.error("Failed to archive xlt logs", e);
        }

        listener.getLogger().println("\n\n-----------------------------------------------------------------\nCleanup ...\n");
        // delete any temporary directory with local XLT
        FilePath xltDir = getXltFolder(build);
        try
        {
            if (xltDir.exists())
            {
                FileUtils.forceDelete(new File(xltDir.toURI()));
            }
        }
        catch (Exception e)
        {
            listener.getLogger().println("Failed to remove " + xltDir.getRemote());
            LOGGER.error("Failed to remove " + xltDir.getRemote(), e);
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
            expandedUrls[i] = "com.xceptance.xlt.mastercontroller.agentcontrollers.ac" + i + ".url=" + agentControllerUrls[i];
        }
        return expandedUrls;
    }

    private String[] startEc2Machine(AbstractBuild<?, ?> build, BuildListener listener) throws Exception
    {
        listener.getLogger()
                .println("-----------------------------------------------------------------\nStarting agent controller with EC2 admin tool ...\n");

        // build the EC2 admin command line
        List<String> commandLine = new ArrayList<String>();

        if (SystemUtils.IS_OS_WINDOWS)
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

        FilePath acUrlFile = new FilePath(getXltConfigFolder(build), "acUrls.properties");
        commandLine.add("-o");
        commandLine.add(acUrlFile.absolutize().getRemote());

        // run the EC2 admin tool
        FilePath workingDirectory = getXltBinFolder(build);
        int commandResult = executeCommand(build.getBuiltOn(), workingDirectory, commandLine, listener.getLogger());
        listener.getLogger().println("EC2 admin tool returned with exit code: " + commandResult);

        if (commandResult != 0)
        {
            throw new Exception("Failed to start instances. EC2 admin tool returned with exit code: " + commandResult);
        }
        // open the file that contains the agent controller URLs
        listener.getLogger().printf("\nRead agent controller URLs from file %s:\n\n", acUrlFile);

        // read the lines from agent controller file
        List<?> lines = FileUtils.readLines(new File(acUrlFile.toURI()));
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

    private void terminateEc2Machine(AbstractBuild<?, ?> build, BuildListener listener) throws Exception
    {
        listener.getLogger()
                .println("-----------------------------------------------------------------\nTerminating agent controller with EC2 admin tool ...\n");

        // build the EC2 admin command line
        List<String> commandLine = new ArrayList<String>();

        if (SystemUtils.IS_OS_WINDOWS)
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
        int commandResult = executeCommand(build.getBuiltOn(), workingDirectory, commandLine, listener.getLogger());
        listener.getLogger().println("EC2 admin tool returned with exit code: " + commandResult);

        if (commandResult != 0)
        {
            throw new Exception("Failed to terminate instances. EC2 admin tool returned with exit code: " + commandResult);
        }
    }

    private FilePath getTestSuiteConfigFolder(AbstractBuild<?, ?> build)
    {
        return new FilePath(build.getModuleRoot(), "/config/");
    }

    private void initialCleanUp(AbstractBuild<?, ?> build, BuildListener listener) throws IOException, InterruptedException
    {
        listener.getLogger().println("-----------------------------------------------------------------\n"
                                         + "Cleaning up project directory ...\n");

        List<FilePath> DIR = build.getWorkspace().list();

        for (FilePath eachFilePath : DIR)
        {
            if (eachFilePath.getBaseName().matches("[0-9]+"))
            {
                eachFilePath.deleteRecursive();
                listener.getLogger().println("Deleted directory: " + eachFilePath.getRemote());
            }
        }

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
        FilePath destDir = getXltFolder(build);
        listener.getLogger().println("Target directory: " + destDir.getRemote());

        // copy XLT to a local directory
        srcDir.copyRecursiveTo(destDir);

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

        if (SystemUtils.IS_OS_WINDOWS)
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
            String[] agentControllerProperties = expandAgentControllerUrls(agentControllerUrls);
            for (int i = 0; i < agentControllerProperties.length; i++)
            {
                commandLine.add("-D" + agentControllerProperties[i]);
            }
        }

        commandLine.add("-auto");
        commandLine.add("-report");

        if (testPropertiesFile != null)
        {
            commandLine.add("-testPropertiesFile");
            commandLine.add(testPropertiesFile);
        }

        commandLine.add("-Dcom.xceptance.xlt.mastercontroller.testSuitePath=" + build.getModuleRoot().getRemote());

        // run the master controller
        FilePath workingDirectory = getXltBinFolder(build);
        int commandResult = executeCommand(build.getBuiltOn(), workingDirectory, commandLine, listener.getLogger());
        listener.getLogger().println("Master controller returned with exit code: " + commandResult);

        if (commandResult != 0)
        {
            throw new Exception("Master controller returned with exit code: " + commandResult);
        }
    }

    private void saveArtifacts(AbstractBuild<?, ?> build, BuildListener listener) throws IOException, InterruptedException
    {
        listener.getLogger()
                .println("\n\n-----------------------------------------------------------------\nArchive results and report...\n");
        // save load test results and report
        saveArtifact(getFirstXltResultsFolder(build), getBuildResultsFolder(build));
        saveArtifact(getFirstXltReportFolder(build), getBuildReportFolder(build));
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
            FilePath resultsFolder = getBuildResultsFolder(build);
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
