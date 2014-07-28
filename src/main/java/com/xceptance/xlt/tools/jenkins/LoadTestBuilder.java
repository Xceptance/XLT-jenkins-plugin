package com.xceptance.xlt.tools.jenkins;

import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
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
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import jenkins.model.Jenkins;

import org.apache.commons.io.FileUtils;
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

/**
 * Readout the configuration of XLT in Jenkins, perform load testing and plot build results on project page.
 * 
 * @author Michael Aleithe, Randolph Straub
 */
public class LoadTestBuilder extends Builder
{
    private final String testPropertiesFile;

    private AgentControllerConfig agentControllerConfig;

    transient private String[] agentControllerUrls;

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

    private final boolean createTrendReport;

    private final boolean createSummaryReport;

    private final int numberOfBuildsForTrendReport;

    private final int numberOfBuildsForSummaryReport;

    transient public static final Logger LOGGER = Logger.getLogger(LoadTestBuilder.class);

    transient private List<Chart<Integer, Double>> charts = new ArrayList<Chart<Integer, Double>>();

    transient private boolean isSave = false;

    transient private XltChartAction chartAction;

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
                           String plotTitle, String builderID, boolean isPlotVertical, boolean createTrendReport,
                           int numberOfBuildsForTrendReport, boolean createSummaryReport, int numberOfBuildsForSummaryReport,
                           AgentControllerConfig agentControllerConfig, String timeFormatPattern, boolean showBuildNumber)
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

        // report configuration
        this.createTrendReport = createTrendReport;
        this.createSummaryReport = createSummaryReport;

        this.numberOfBuildsForTrendReport = (numberOfBuildsForTrendReport > 0) ? numberOfBuildsForTrendReport
                                                                              : getDescriptor().getDefaultNumberOfBuildsForTrendReport();

        this.numberOfBuildsForSummaryReport = (numberOfBuildsForSummaryReport > 0)
                                                                                  ? numberOfBuildsForSummaryReport
                                                                                  : getDescriptor().getDefaultNumberOfBuildsForSummaryReport();

        // misc.
        this.builderID = StringUtils.defaultIfBlank(builderID, UUID.randomUUID().toString());
    }

    private SimpleDateFormat getDateFormat()
    {
        if (dateFormat == null)
        {
            try
            {
                dateFormat = new SimpleDateFormat(timeFormatPattern);
            }
            catch (Exception ex)
            {
                LOGGER.warn("Failed to create date format for pattern: " + timeFormatPattern, ex);
                dateFormat = new SimpleDateFormat();
            }
        }
        return dateFormat;
    }

    private String[] parseAgentControllerUrlsFromFile(File file) throws IOException
    {
        String fileContent = FileUtils.readFileToString(file, "UTF-8");

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

    private void loadCharts(AbstractProject<?, ?> project)
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
        int to = Math.min(startFrom + count - 1, allBuilds.size());
        if (to < 0)
        {
            to = allBuilds.size();
        }

        return allBuilds.subList(startFrom, to);
    }

    private Document getDataDocument(AbstractBuild<?, ?> build)
    {
        File testDataFile = getTestReportDataFile(build);
        if (testDataFile.exists())
        {
            try
            {
                return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(testDataFile);
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
            LOGGER.warn("No test data found for build. (build: \"" + build.number + "\" searchLocation: \"" +
                        testDataFile.getAbsolutePath() + "\")");
        }
        return null;
    }

    private void addBuildToCharts(AbstractBuild<?, ?> build)
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
            Document dataXml = getDataDocument(eachBuild);

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

            charts = new ArrayList<Chart<Integer, Double>>();
            updateConfig();

            try
            {
                initializeCharts();
            }
            catch (JSONException e)
            {
                LOGGER.error("Failed to initialize charts", e);
            }
            loadCharts(project);

            chartAction = new XltChartAction(project, getEnabledCharts(), plotWidth, plotHeight, plotTitle, builderID, isPlotVertical,
                                             createTrendReport, createSummaryReport);
        }
        actions.add(chartAction);

        return actions;
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

    private File getTestReportDataFile(AbstractBuild<?, ?> build)
    {
        return new File(getBuildReportFolder(build), "testreport.xml");
    }

    private File getXltResultsFolder(AbstractBuild<?, ?> build)
    {
        return new File(getXltFolder(build), "results");
    }

    private File getXltLogFolder(AbstractBuild<?, ?> build)
    {
        return new File(getXltFolder(build), "log");
    }

    private File getFirstXltResultsFolder(AbstractBuild<?, ?> build)
    {
        return getFirstSubFolder(getXltResultsFolder(build));
    }

    private File getXltReportsFolder(AbstractBuild<?, ?> build)
    {
        return new File(getXltFolder(build), "reports");
    }

    private File getFirstXltReportFolder(AbstractBuild<?, ?> build)
    {
        return getFirstSubFolder(getXltReportsFolder(build));
    }

    private File getFirstSubFolder(File dir)
    {
        if (dir != null && dir.isDirectory())
        {
            File[] subFiles = dir.listFiles();
            if (subFiles != null)
            {
                for (int i = 0; i < subFiles.length; i++)
                {
                    if (subFiles[i].isDirectory())
                    {
                        return subFiles[i];
                    }
                }
            }
        }
        return null;
    }

    private File getBuildResultConfigFolder(AbstractBuild<?, ?> build)
    {
        return new File(getBuildResultsFolder(build), "config");
    }

    private File getBuildLogsFolder(AbstractBuild<?, ?> build)
    {
        return new File(build.getArtifactsDir(), builderID + "/log");
    }

    private File getBuildReportFolder(AbstractBuild<?, ?> build)
    {
        return new File(build.getArtifactsDir(), builderID + "/report/" + Integer.toString(build.getNumber()));
    }

    private File getBuildResultsFolder(AbstractBuild<?, ?> build)
    {
        return new File(build.getArtifactsDir(), builderID + "/results");
    }

    private File getTrendReportFolder(AbstractProject<?, ?> project)
    {
        return new File(project.getRootDir(), "trendreport/" + builderID);
    }

    private File getSummaryReportFolder(AbstractProject<?, ?> project)
    {
        return new File(project.getRootDir(), "summaryReport/" + builderID);
    }

    private File getSummaryResultsFolder(AbstractProject<?, ?> project)
    {
        return new File(project.getRootDir(), "summaryResults/" + builderID);
    }

    private File getSummaryResultsConfigFolder(AbstractProject<?, ?> project)
    {
        return new File(getSummaryResultsFolder(project), "config");
    }

    private File getXltFolder(AbstractBuild<?, ?> build)
    {
        return new File(build.getProject().getRootDir(), Integer.toString(build.getNumber()));
    }

    private File getXltBinFolder(AbstractBuild<?, ?> build)
    {
        return new File(getXltFolder(build), "bin");
    }

    private List<CriteriaResult> validateCriteria(AbstractBuild<?, ?> build, BuildListener listener)
    {
        listener.getLogger().println("-----------------------------------------------------------------\n"
                                         + "Checking success criteria ...\n");

        List<CriteriaResult> failedAlerts = new ArrayList<CriteriaResult>();

        Document dataXml = getDataDocument(build);
        if (dataXml == null)
        {
            CriteriaResult criteriaResult = CriteriaResult.error("No test data found.");
            failedAlerts.add(criteriaResult);
            listener.getLogger().println(criteriaResult.getLogMessage());
        }
        else
        {
            try
            {
                List<String> criteriaIDs = getValueConfigIDs();
                for (String eachID : criteriaIDs)
                {
                    listener.getLogger().println();
                    listener.getLogger().println("Start processing. Criteria:\"" + eachID + "\"");
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
                            listener.getLogger().println(criteriaResult.getLogMessage());
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
                            listener.getLogger().println(criteriaResult.getLogMessage());
                            continue;
                        }

                        // test the condition
                        listener.getLogger().println("Test condition. Criteria: \"" + eachID + "\"\t Path: \"" + xPath +
                                                         "\t Condition: \"" + condition + "\"");
                        Node result = (Node) XPathFactory.newInstance().newXPath().evaluate(conditionPath, dataXml, XPathConstants.NODE);
                        if (result == null)
                        {
                            String value = XPathFactory.newInstance().newXPath().evaluate(xPath, dataXml);
                            CriteriaResult criteriaResult = CriteriaResult.failed("Condition failed");
                            criteriaResult.setCriteriaID(eachID);
                            criteriaResult.setValue(value);
                            criteriaResult.setCondition(condition);
                            criteriaResult.setXPath(xPath);
                            failedAlerts.add(criteriaResult);
                            listener.getLogger().println(criteriaResult.getLogMessage());
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
                        listener.getLogger().println(criteriaResult.getLogMessage());
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
                        listener.getLogger().println(criteriaResult.getLogMessage());
                    }
                }
            }
            catch (JSONException e)
            {
                CriteriaResult criteriaResult = CriteriaResult.error("Failed to start process criteria.");
                criteriaResult.setExceptionMessage(e.getMessage());
                criteriaResult.setCauseMessage(e.getCause() != null ? e.getCause().getMessage() : null);
                failedAlerts.add(criteriaResult);
                listener.getLogger().println(criteriaResult.getLogMessage());
            }
        }
        return failedAlerts;
    }

    private void postTestExecution(AbstractBuild<?, ?> build, BuildListener listener)
    {
        addBuildToCharts(build);
        List<CriteriaResult> failedAlerts = validateCriteria(build, listener);

        if (!failedAlerts.isEmpty())
        {
            listener.getLogger().println();
            listener.getLogger().println("Set state to UNSTABLE by alerts.");
            build.setResult(Result.UNSTABLE);
            for (CriteriaResult eachAlert : failedAlerts)
            {
                listener.getLogger().println(eachAlert.getLogMessage());
            }
            listener.getLogger().println();
        }

        XltRecorderAction printReportAction = new XltRecorderAction(build, failedAlerts, builderID);
        build.getActions().add(printReportAction);
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

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException
    {
        try
        {
            initialCleanUp(build, listener);
            copyXlt(build, listener);
            configureAgentController(build, listener);
            runMasterController(build, listener);
            postTestExecution(build, listener);

            if (createSummaryReport)
            {
                createSummaryReport(build, listener);
            }

            if (createTrendReport)
            {
                createTrendReport(build, listener);
            }
        }
        catch (InterruptedException e)
        {
            build.setResult(Result.ABORTED);
        }
        catch (Exception e)
        {
            build.setResult(Result.FAILURE);
            listener.getLogger().println("Build failed: " + e);
            LOGGER.error("", e);
        }
        finally
        {
            // save logs
            saveArtifact(getXltLogFolder(build), getBuildLogsFolder(build));

            // delete any temporary directory with local XLT
            File xltDir = getXltFolder(build);
            FileUtils.deleteQuietly(xltDir);
        }

        return true;
    }

    private void configureAgentController(AbstractBuild<?, ?> build, BuildListener listener) throws IOException
    {
        listener.getLogger().println("-----------------------------------------------------------------\n"
                                         + "Configuring agent controllers ...\n");

        if (agentControllerConfig.type.equals(AgentControllerConfig.TYPE.embedded.toString()))
        {
            agentControllerUrls = null;
            listener.getLogger().println("Set to embedded mode");
        }
        else
        {
            if (agentControllerConfig.type.equals(AgentControllerConfig.TYPE.list.toString()))
            {
                listener.getLogger().println("Read agent controller URLs from configuration:\n");

                if (agentControllerConfig.urlList != null)
                {
                    agentControllerUrls = parseAgentControllerUrls(agentControllerConfig.urlList);
                }
            }
            else if (agentControllerConfig.type.equals(AgentControllerConfig.TYPE.file.toString()))
            {
                File file = new File(getTestSuiteConfigFolder(build), agentControllerConfig.urlFile);

                listener.getLogger().printf("Read agent controller URLs from file %s:\n\n", file);

                if (agentControllerConfig.urlFile != null)
                {
                    agentControllerUrls = parseAgentControllerUrlsFromFile(file);
                }
            }

            if (agentControllerUrls == null || agentControllerUrls.length == 0)
            {
                throw new IllegalStateException("No agent controller URLs found.");
            }
            else
            {
                for (String agentControllerUrl : agentControllerUrls)
                {
                    listener.getLogger().printf("- %s\n", agentControllerUrl);
                }
            }
        }

        listener.getLogger().println("\nFinished");
    }

    private File getTestSuiteConfigFolder(AbstractBuild<?, ?> build)
    {
        return new File(build.getModuleRoot() + "/config/");
    }

    private void initialCleanUp(AbstractBuild<?, ?> build, BuildListener listener) throws IOException
    {
        listener.getLogger().println("-----------------------------------------------------------------\n"
                                         + "Cleaning up project directory ...\n");

        String[] DIR = build.getProject().getRootDir().list();

        for (int i = 0; i < DIR.length; i++)
        {
            if (DIR[i].matches("[0-9]+"))
            {
                File file = new File(build.getProject().getRootDir() + "/" + DIR[i]);
                FileUtils.deleteDirectory(file);
                listener.getLogger().println("Deleted directory: " + file);
            }
        }

        listener.getLogger().println("\nFinished");
    }

    private void copyXlt(AbstractBuild<?, ?> build, BuildListener listener) throws IOException
    {
        listener.getLogger().println("-----------------------------------------------------------------\nCopying XLT ...\n");

        // the directory with the XLT template installation
        if (StringUtils.isBlank(getXltTemplateDir()))
        {
            LOGGER.error("Path to xlt not set.");
            throw new IllegalStateException("Path to xlt not set.");
        }

        File srcDir = new File(getXltTemplateDir());
        listener.getLogger().println("XLT template directory: " + srcDir.getAbsolutePath());

        // the target directory in the project folder
        File destDir = getXltFolder(build);
        listener.getLogger().println("Target directory: " + destDir.getAbsolutePath());

        // copy XLT to a local directory
        FileUtils.copyDirectory(srcDir, destDir, true);

        // make XLT start scripts executable
        File workingDirectory = getXltBinFolder(build);
        for (File child : workingDirectory.listFiles())
        {
            child.setExecutable(true);
        }

        listener.getLogger().println("\nFinished");
    }

    private void runMasterController(AbstractBuild<?, ?> build, BuildListener listener) throws Exception
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

        if (agentControllerUrls == null)
        {
            commandLine.add("-embedded");

            // use any free port
            commandLine.add("-Dcom.xceptance.xlt.agentcontroller.port=0");
        }
        else
        {
            for (int i = 1; i <= agentControllerUrls.length; i++)
            {
                commandLine.add("-Dcom.xceptance.xlt.mastercontroller.agentcontrollers.ac" + i + ".url=" + agentControllerUrls[i - 1]);
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
        File workingDirectory = getXltBinFolder(build);
        int commandResult = executeCommand(build, workingDirectory, commandLine, listener.getLogger());
        listener.getLogger().println("Master controller returned with exit code: " + commandResult);

        if (commandResult != 0)
        {
            build.setResult(Result.FAILURE);
        }

        // save load test results and report
        saveArtifact(getFirstXltResultsFolder(build), getBuildResultsFolder(build));
        saveArtifact(getFirstXltReportFolder(build), getBuildReportFolder(build));
    }

    private void saveArtifact(File srcFolder, File destFolder) throws IOException
    {
        if (srcFolder != null && srcFolder.isDirectory() && destFolder != null)
        {
            // move folder to save time
            FileUtils.moveDirectory(srcFolder, destFolder);
        }
    }

    private int executeCommand(AbstractBuild<?, ?> build, File workingDirectory, List<String> commandLine, PrintStream logger)
        throws IOException, InterruptedException
    {
        hudson.model.Node buildNode = build.getBuiltOn();
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

        File outputFolder = getSummaryReportFolder(build.getProject());
        commandLine.add("-o");
        commandLine.add(outputFolder.getAbsolutePath());

        commandLine.add(getSummaryResultsFolder(build.getProject()).getAbsolutePath());

        // run the report generator
        int commandResult = executeCommand(build, getXltBinFolder(build), commandLine, listener.getLogger());
        listener.getLogger().println("Load report generator returned with exit code: " + commandResult);
        if (commandResult != 0)
        {
            build.setResult(Result.FAILURE);
        }
    }

    private void copyResults(AbstractBuild<?, ?> currentBuild, BuildListener listener) throws IOException
    {
        // recreate a fresh summary results directory
        File summaryResultsFolder = getSummaryResultsFolder(currentBuild.getProject());

        FileUtils.deleteQuietly(summaryResultsFolder);
        summaryResultsFolder.mkdirs();

        // copy config from the current build's results
        File configFolder = getBuildResultConfigFolder(currentBuild);
        File summaryConfigFolder = getSummaryResultsConfigFolder(currentBuild.getProject());

        FileUtils.copyDirectory(configFolder, summaryConfigFolder, true);

        // copy timer data from the last n builds
        List<AbstractBuild<?, ?>> builds = (List<AbstractBuild<?, ?>>) currentBuild.getPreviousBuildsOverThreshold(numberOfBuildsForSummaryReport - 1,
                                                                                                                   Result.UNSTABLE);
        builds.add(0, currentBuild);

        for (AbstractBuild<?, ?> build : builds)
        {
            File resultsFolder = getBuildResultsFolder(build);
            if (resultsFolder.isDirectory())
            {
                copyResults(summaryResultsFolder, resultsFolder, build.getNumber());
            }
        }
    }

    private void copyResults(File targetDir, File srcDir, int buildNumber) throws IOException
    {
        File[] files = srcDir.listFiles();
        if (files != null)
        {
            for (File file : files)
            {
                if (file.isDirectory())
                {
                    copyResults(new File(targetDir, file.getName()), file, buildNumber);
                }
                else if (file.getName().startsWith("timers.csv"))
                {
                    // regular timer files
                    File renamedResultsFile = new File(targetDir, file.getName() + "." + buildNumber);
                    FileUtils.copyFile(file, renamedResultsFile);
                }
                else if (file.getName().endsWith(".csv"))
                {
                    // WebDriver timer files
                    FileUtils.copyFileToDirectory(file, targetDir);
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

        File trendReportDest = getTrendReportFolder(build.getProject());
        commandLine.add("-o");
        commandLine.add(trendReportDest.toString());

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
            File reportDirectory = getBuildReportFolder(eachBuild);
            if (reportDirectory.isDirectory())
            {
                commandLine.add(reportDirectory.toString());
                numberOfBuildsWithReports++;
            }
        }

        // check whether we have enough builds with reports to create a trend report
        if (numberOfBuildsWithReports >= 2)
        {
            // run trend report generator
            int commandResult = executeCommand(build, getXltBinFolder(build), commandLine, listener.getLogger());
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
