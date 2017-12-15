package com.xceptance.xlt.tools.jenkins;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
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
import com.xceptance.xlt.tools.jenkins.config.AgentControllerConfig;
import com.xceptance.xlt.tools.jenkins.config.AmazonEC2;
import com.xceptance.xlt.tools.jenkins.config.Embedded;
import com.xceptance.xlt.tools.jenkins.config.UrlFile;
import com.xceptance.xlt.tools.jenkins.config.UrlList;
import com.xceptance.xlt.tools.jenkins.config.option.MarkCriticalOption;
import com.xceptance.xlt.tools.jenkins.config.option.SummaryReportOption;
import com.xceptance.xlt.tools.jenkins.config.option.TrendReportOption;
import com.xceptance.xlt.tools.jenkins.logging.LOGGER;

import hudson.AbortException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Job;
import hudson.model.ParameterValue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.tasks.Builder;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import jenkins.util.VirtualFile;

/**
 * Readout the configuration of XLT in Jenkins, perform load testing and plot build results on project page.
 * 
 * @author Randolph Straub
 */
public class LoadTestBuilder extends Builder implements SimpleBuildStep
{
    @CheckForNull
    private String testPropertiesFile;

    @Nonnull
    private AgentControllerConfig agentControllerConfig;

    @Nonnull
    private Integer initialResponseTimeout;

    @Nonnull
    private String xltConfig;

    @CheckForNull
    private final String xltTemplateDir;

    @CheckForNull
    private String pathToTestSuite;

    private transient JSONObject config;

    @CheckForNull
    private String timeFormatPattern;

    transient private SimpleDateFormat dateFormat;

    private boolean showBuildNumber;

    private int plotWidth;

    private int plotHeight;

    @Nonnull
    private String plotTitle;

    @Nonnull
    private String stepId;

    private boolean plotVertical;

    @CheckForNull
    private TrendReportOption trendReportOption;

    @CheckForNull
    private SummaryReportOption summaryReportOption;

    @CheckForNull
    private MarkCriticalOption markCriticalOption;

    transient private List<Chart<Integer, Double>> charts = new ArrayList<Chart<Integer, Double>>();

    private transient Map<ENVIRONMENT_KEYS, ParameterValue> buildParameterMap;

    /*
     * Backward compatibility
     */
    @Deprecated
    private transient String builderID;

    @Deprecated
    private transient boolean isPlotVertical;

    @Deprecated
    private transient boolean createTrendReport;

    @Deprecated
    private transient int numberOfBuildsForTrendReport;

    @Deprecated
    private transient boolean createSummaryReport;

    @Deprecated
    private transient int numberOfBuildsForSummaryReport;

    @Deprecated
    private transient boolean markCriticalEnabled;

    @Deprecated
    private transient int markCriticalConditionCount;

    @Deprecated
    private transient int markCriticalBuildCount;

    public enum CONFIG_VALUE_PARAMETER
    {
     id,
     xPath,
     condition,
     plotID,
     name
    };

    public enum CONFIG_PLOT_PARAMETER
    {
     id,
     title,
     buildCount,
     enabled,
     showNoValues
    };

    public enum CONFIG_SECTIONS_PARAMETER
    {
     values,
     plots
    };

    public enum ENVIRONMENT_KEYS
    {
     XLT_RUN_FAILED,
     XLT_CONDITION_CRITICAL,
     XLT_CONDITION_FAILED,
     XLT_CONDITION_ERROR,
     XLT_REPORT_URL,
     XLT_CONDITION_MESSAGE
    };

    public static class FOLDER_NAMES
    {
        public static String ARTIFACT_REPORT = "report";

        public static String ARTIFACT_RESULT = "results";
    }

    @DataBoundConstructor
    public LoadTestBuilder(@Nonnull final String stepId, @Nonnull final String xltTemplateDir)
    {
        this.xltTemplateDir = xltTemplateDir;

        // load test configuration
        this.agentControllerConfig = getDescriptor().getDefaultAgentControllerConfig();

        this.initialResponseTimeout = getDescriptor().getDefaultInitialResponseTimeout();

        // plot/value configuration
        this.xltConfig = getDescriptor().getDefaultXltConfig();

        // advanced plot configuration
        this.plotWidth = getDescriptor().getDefaultPlotWidth();
        this.plotHeight = getDescriptor().getDefaultPlotHeight();
        this.plotTitle = getDescriptor().getDefaultPlotTitle();

        // misc.
        this.stepId = stepId;
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

    @Nonnull
    public AgentControllerConfig getAgentControllerConfig()
    {
        return agentControllerConfig;
    }

    @DataBoundSetter
    public void setAgentControllerConfig(@Nonnull final AgentControllerConfig acConfig)
    {
        if (acConfig != null)
        {
            this.agentControllerConfig = acConfig;
        }
    }

    @Nonnull
    public Integer getInitialResponseTimeout()
    {
        return initialResponseTimeout;
    }

    @DataBoundSetter
    public void setInitialResponseTimeout(@Nonnull final Integer timeout)
    {
        initialResponseTimeout = timeout;
    }

    @CheckForNull
    public TrendReportOption getTrendReportOption()
    {
        return trendReportOption;
    }

    @DataBoundSetter
    public void setTrendReportOption(@CheckForNull final TrendReportOption opt)
    {
        this.trendReportOption = opt;
    }

    @CheckForNull
    public SummaryReportOption getSummaryReportOption()
    {
        return summaryReportOption;
    }

    @DataBoundSetter
    public void setSummaryReportOption(@CheckForNull final SummaryReportOption opt)
    {
        this.summaryReportOption = opt;
    }

    @CheckForNull
    public MarkCriticalOption getMarkCriticalOption()
    {
        return markCriticalOption;
    }

    @DataBoundSetter
    public void setMarkCriticalOption(@CheckForNull final MarkCriticalOption opt)
    {
        this.markCriticalOption = opt;
    }

    public boolean getMarkCriticalEnabled()
    {
        return markCriticalOption != null;
    }

    public int getMarkCriticalConditionCount()
    {
        if (markCriticalOption == null)
        {
            return 0;
        }

        return markCriticalOption.getMarkCriticalConditionCount();
    }

    public int getMarkCriticalBuildCount()
    {
        if (markCriticalOption == null)
        {
            return 0;
        }

        final int mcCondCount = markCriticalOption.getMarkCriticalConditionCount();
        final int mcBuildCount = markCriticalOption.getMarkCriticalBuildCount();
        return mcCondCount > 0 ? Math.max(mcCondCount, mcBuildCount) : mcBuildCount;
    }

    @CheckForNull
    public String getPathToTestSuite()
    {
        return pathToTestSuite;
    }

    @DataBoundSetter
    public void setPathToTestSuite(@CheckForNull final String path)
    {
        this.pathToTestSuite = StringUtils.isNotBlank(path) ? path : null;
    }

    @CheckForNull
    public String getTestPropertiesFile()
    {
        return testPropertiesFile;
    }

    @DataBoundSetter
    public void setTestPropertiesFile(@CheckForNull final String propertyFilePath)
    {
        this.testPropertiesFile = StringUtils.isNotBlank(propertyFilePath) ? propertyFilePath : null;
    }

    @Nonnull
    public String getXltConfig()
    {
        return xltConfig;
    }

    @DataBoundSetter
    public void setXltConfig(@Nonnull final String config)
    {
        if (StringUtils.isNotBlank(config))
        {
            this.xltConfig = config;
        }
    }

    @Nonnull
    public String getXltTemplateDir()
    {
        return xltTemplateDir;
    }

    @CheckForNull
    public String getTimeFormatPattern()
    {
        return timeFormatPattern;
    }

    @DataBoundSetter
    public void setTimeFormatPattern(@CheckForNull final String timeFormatPattern)
    {
        this.timeFormatPattern = Util.fixEmptyAndTrim(timeFormatPattern);
        dateFormat = null;
    }

    public boolean isShowBuildNumber()
    {
        return showBuildNumber;
    }

    @DataBoundSetter
    public void setShowBuildNumber(final boolean showBuildNumber)
    {
        this.showBuildNumber = showBuildNumber;
    }

    public int getPlotWidth()
    {
        return plotWidth;
    }

    @DataBoundSetter
    public void setPlotWidth(final int plotWidth)
    {
        if (plotWidth > 0)
        {
            this.plotWidth = plotWidth;
        }
    }

    public int getPlotHeight()
    {
        return plotHeight;
    }

    @DataBoundSetter
    public void setPlotHeight(final int plotHeight)
    {
        if (plotHeight > 0)
        {
            this.plotHeight = plotHeight;
        }
    }

    @Nonnull
    public String getPlotTitle()
    {
        return plotTitle;
    }

    @DataBoundSetter
    public void setPlotTitle(@Nonnull final String title)
    {
        if (StringUtils.isNotBlank(title))
        {
            this.plotTitle = title;
        }
    }

    @Nonnull
    public String getStepId()
    {
        return stepId;
    }

    public boolean isPlotVertical()
    {
        return plotVertical;
    }

    @DataBoundSetter
    public void setPlotVertical(boolean isPlotVertical)
    {
        this.plotVertical = isPlotVertical;
    }

    public boolean getCreateTrendReport()
    {
        return trendReportOption != null;
    }

    public boolean getCreateSummaryReport()
    {
        return summaryReportOption != null;
    }

    public int getNumberOfBuildsForTrendReport()
    {
        final int nbBuilds = trendReportOption != null ? trendReportOption.getNumberOfBuildsForTrendReport() : -1;
        return nbBuilds > 0 ? nbBuilds : getDescriptor().getDefaultNumberOfBuildsForTrendReport();
    }

    public int getNumberOfBuildsForSummaryReport()
    {
        final int nbBuilds = summaryReportOption != null ? summaryReportOption.getNumberOfBuildsForSummaryReport() : -1;
        return nbBuilds > 0 ? nbBuilds : getDescriptor().getDefaultNumberOfBuildsForSummaryReport();
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

    /**
     * @param project
     * @param startFrom
     *            (inclusive)
     * @param count
     *            < 0 means all builds up to end
     * @return
     */
    private List<Run<?, ?>> getRuns(Run<?, ?> currentRun, int startFrom, int count)
    {
        Job<?, ?> job = currentRun.getParent();

        List<Run<?, ?>> allBuilds = new ArrayList<Run<?, ?>>(job.getBuilds());
        int maxBuilds = allBuilds.size();
        int to = Math.min(startFrom + count, maxBuilds);
        if (to < 0 || to > maxBuilds)
        {
            to = maxBuilds;
        }

        return allBuilds.subList(startFrom, to);
    }

    private Document getDataDocument(Run<?, ?> build) throws IOException, InterruptedException
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

    private void addBuildToCharts(Run<?, ?> build)
    {
        Document dataXml = null;
        try
        {
            dataXml = getDataDocument(build);
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
                    LOGGER.debug("No chart found for plot. (build: \"" + build.number + "\" plotID: \"" + eachPlotID + "\")");
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
                            LOGGER.debug("No line found for value. (build: \"" + build.number + "\" valueID: \"" + eachValueID +
                                         "\" chartID:\"" + chart.getChartID() + "\")");
                            continue;
                        }

                        boolean addedValueToLine = false;
                        if (dataXml == null)
                        {
                            LOGGER.info("No test data found for build. (build: \"" + build.number + "\")");
                        }
                        else
                        {
                            try
                            {
                                String xPath = getvalueConfigValue(eachValueID, CONFIG_VALUE_PARAMETER.xPath);
                                try
                                {
                                    Double number = (Double) XPathFactory.newInstance().newXPath().evaluate(xPath, dataXml,
                                                                                                            XPathConstants.NUMBER);

                                    if (number.isNaN())
                                    {
                                        LOGGER.warn("Value is not a number. (build: \"" + build.number + "\" valueID: \"" + eachValueID +
                                                    "\" XPath: \"" + xPath + "\"");
                                    }
                                    else
                                    {
                                        addChartLineValue(line, build, chart.getXIndex(), number.doubleValue());
                                        addedValueToLine = true;
                                        addedValueToChart = true;
                                    }
                                }
                                catch (XPathExpressionException e)
                                {
                                    LOGGER.error("Invalid XPath. (build: \"" + build.number + "\" valueID: \"" + eachValueID +
                                                 "\" XPath: \"" + xPath + "\")", e);
                                }
                            }
                            catch (JSONException e)
                            {
                                LOGGER.error("Failed to get config section. (build: \"" + build.number + "\" valueID: \"" + eachValueID +
                                             "\")", e);
                            }
                        }
                        if (addedValueToLine == false && line.getShowNoValues())
                        {
                            addChartLineValue(line, build, chart.getXIndex(), 0);
                            addedValueToChart = true;
                        }
                    }
                }
                catch (JSONException e)
                {
                    LOGGER.error("Failed to get config section. (build: \"" + build.number + "\")", e);
                }

                if (addedValueToChart)
                {
                    chart.nextXIndex();
                }
            }
        }
        catch (JSONException e)
        {
            LOGGER.error("Failed to get config section. (build: \"" + build.number + "\")", e);
        }

    }

    private void addChartLineValue(ChartLine<Integer, Double> chartLine, Run<?, ?> build, int xIndex, double dataValue)
    {
        ChartLineValue<Integer, Double> lineValue = new ChartLineValue<Integer, Double>(xIndex, dataValue);

        lineValue.setDataObjectValue("buildNumber", "\"" + build.number + "\"");
        lineValue.setDataObjectValue("showBuildNumber", "" + showBuildNumber);
        lineValue.setDataObjectValue("buildTime", "\"" + getDateFormat().format(build.getTime()) + "\"");
        chartLine.addLineValue(lineValue);
    }

    private Chart<Integer, Double> createChart(final String plotID) throws JSONException
    {
        final String chartTitle = Util.fixNull(getOptionalPlotConfigValue(plotID, CONFIG_PLOT_PARAMETER.title));
        final String buildCountValue = getOptionalPlotConfigValue(plotID, CONFIG_PLOT_PARAMETER.buildCount);

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
        final String showNoValuesValue = getOptionalPlotConfigValue(plotID, CONFIG_PLOT_PARAMETER.showNoValues);
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

        final Chart<Integer, Double> chart = new Chart<Integer, Double>(plotID, chartTitle);
        for (final String eachValueID : getValueConfigIDs(plotID))
        {
            final String lineName = Util.fixNull(getOptionalValueConfigValue(eachValueID, CONFIG_VALUE_PARAMETER.name));
            final ChartLine<Integer, Double> line = new ChartLine<Integer, Double>(eachValueID, lineName, maxCount, showNoValues);
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

    private int getMaxBuildCount()
    {
        int maxBuildCount = 1;
        if (config != null)
        {
            JSONArray plotsArray = config.optJSONArray(CONFIG_SECTIONS_PARAMETER.plots.name());
            if (plotsArray != null)
            {
                for (int i = 0; i < plotsArray.length(); i++)
                {
                    final JSONObject plot = plotsArray.optJSONObject(i);
                    if (plot != null)
                    {
                        final String s = plot.optString(CONFIG_PLOT_PARAMETER.buildCount.name());
                        if (s != null)
                        {
                            try
                            {
                                final int buildCount = Integer.parseInt(s);
                                maxBuildCount = Math.max(maxBuildCount, buildCount);
                            }
                            catch (NumberFormatException nfe)
                            {
                                LOGGER.error("Failed to parse '" + s + "' as integer", nfe);
                            }
                        }
                    }

                }
            }
        }

        return maxBuildCount;
    }

    private void publishChartData(final Run<?, ?> run)
    {
        reloadCharts(run);

        run.addAction(new XltChartAction(getEnabledCharts(), plotWidth, plotHeight, plotTitle, stepId, getMaxBuildCount(), plotVertical,
                                         getCreateTrendReport(), getCreateSummaryReport()));
    }

    private void reloadCharts(Run<?, ?> run)
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

        addBuildToCharts(run);
    }

    private void updateConfig()
    {
        try
        {
            config = new JSONObject(xltConfig);
        }
        catch (final JSONException e)
        {
            LOGGER.error("Failed to parse XLT config as JSON object", e);
            config = null;
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
        return getValueConfigIDs(null);
    }

    private ArrayList<String> getValueConfigIDs(String plotID) throws JSONException
    {
        ArrayList<String> valueList = new ArrayList<String>();

        if (config != null)
        {
            JSONArray valuesSection = config.getJSONArray(CONFIG_SECTIONS_PARAMETER.values.name());
            for (int i = 0; i < valuesSection.length(); i++)
            {
                String valueID = null;
                try
                {
                    JSONObject each = valuesSection.getJSONObject(i);
                    valueID = each.getString(CONFIG_VALUE_PARAMETER.id.name());
                    if (plotID == null || plotID.equals(each.getString(CONFIG_VALUE_PARAMETER.plotID.name())))
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
        }
        return valueList;
    }

    private ArrayList<String> getPlotConfigIDs() throws JSONException
    {
        final ArrayList<String> plotIDs = new ArrayList<String>();
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

    private FilePath getTestReportDataFile(Run<?, ?> build)
    {
        return getBuildReportFolder(build).child("testreport.xml");
    }

    private FilePath getXltResultFolder(Run<?, ?> build, Launcher launcher) throws BuildNodeGoneException
    {
        return getTemporaryXltFolder(build, launcher).child(FOLDER_NAMES.ARTIFACT_RESULT);
    }

    private FilePath getXltLogFolder(Run<?, ?> build, Launcher launcher) throws BuildNodeGoneException
    {
        return getTemporaryXltFolder(build, launcher).child("log");
    }

    private FilePath getXltReportFolder(Run<?, ?> build, Launcher launcher) throws BuildNodeGoneException
    {
        return getTemporaryXltFolder(build, launcher).child(FOLDER_NAMES.ARTIFACT_REPORT);
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

    private static URI getArtifactsDir(Run<?, ?> build)
    {
        final VirtualFile artiDir = build.getArtifactManager().root();
        return artiDir.toURI();
    }

    public static FilePath getArtifact(Run<?, ?> build, String artifactPath)
    {
        return new FilePath(new File(new File(getArtifactsDir(build)), artifactPath));
    }

    private FilePath getBuildResultConfigFolder(Run<?, ?> build)
    {
        return new FilePath(getBuildResultFolder(build), "config");
    }

    private FilePath getBuildLogsFolder(Run<?, ?> build)
    {
        return getArtifact(build, stepId + "/log");
    }

    private FilePath getBuildReportFolder(Run<?, ?> build)
    {
        return getArtifact(build, stepId + "/" + FOLDER_NAMES.ARTIFACT_REPORT);
    }

    private String getBuildReportURL(Run<?, ?> build)
    {
        final StringBuilder sb = new StringBuilder();

        sb.append(StringUtils.defaultString(Jenkins.getActiveInstance().getRootUrl(), "/")).append(build.getUrl())
          .append(XltRecorderAction.RELATIVE_REPORT_URL).append(stepId).append('/').append(FOLDER_NAMES.ARTIFACT_REPORT)
          .append("/index.html");
        return sb.toString();
    }

    private FilePath getBuildResultFolder(Run<?, ?> build)
    {
        return getArtifact(build, stepId + "/" + FOLDER_NAMES.ARTIFACT_RESULT);
    }

    private FilePath getTrendReportFolder(Job<?, ?> project)
    {
        return new FilePath(project.getRootDir()).child("trendReport").child(stepId);
    }

    private FilePath getSummaryReportFolder(Job<?, ?> project)
    {
        return new FilePath(project.getRootDir()).child("summaryReport").child(stepId);
    }

    private FilePath getSummaryResultsFolder(Job<?, ?> project)
    {
        return new FilePath(project.getRootDir()).child("summaryResults").child(stepId);
    }

    private FilePath getSummaryResultsConfigFolder(Job<?, ?> project)
    {
        return new FilePath(getSummaryResultsFolder(project), "config");
    }

    private FilePath getTemporaryXltBaseFolder(Run<?, ?> run, Launcher launcher) throws BuildNodeGoneException
    {
        hudson.model.Node node = getBuildNodeIfOnlineOrFail(launcher);
        FilePath base = new FilePath(run.getParent().getRootDir());
        if (node != Jenkins.getInstance())
        {
            base = node.getRootPath();
        }
        return new FilePath(base, "tmp-xlt");
    }

    private FilePath getTemporaryXltProjectFolder(Run<?, ?> run, Launcher launcher) throws BuildNodeGoneException
    {
        return new FilePath(getTemporaryXltBaseFolder(run, launcher), run.getParent().getName());
    }

    private FilePath getTemporaryXltBuildFolder(Run<?, ?> run, Launcher launcher) throws BuildNodeGoneException
    {
        return new FilePath(getTemporaryXltProjectFolder(run, launcher), "" + run.getNumber());
    }

    private FilePath getTemporaryXltFolder(Run<?, ?> run, Launcher launcher) throws BuildNodeGoneException
    {
        return new FilePath(new FilePath(getTemporaryXltBuildFolder(run, launcher), getStepId()), "xlt");
    }

    private FilePath getXltTemplateFilePath()
    {
        return XltDescriptor.resolvePath(getXltTemplateDir());
    }

    private FilePath getXltBinFolder(Run<?, ?> run, Launcher launcher) throws BuildNodeGoneException
    {
        return new FilePath(getTemporaryXltFolder(run, launcher), "bin");
    }

    private FilePath getXltBinFolderOnMaster()
    {
        return new FilePath(getXltTemplateFilePath(), "bin");
    }

    private FilePath getXltConfigFolder(Run<?, ?> run, Launcher launcher) throws BuildNodeGoneException
    {
        return new FilePath(getTemporaryXltFolder(run, launcher), "config");
    }

    private List<CriterionResult> getFailedCriteria(Run<?, ?> build, TaskListener listener, Document dataXml)
        throws IOException, InterruptedException
    {
        listener.getLogger()
                .println("-----------------------------------------------------------------\n" + "Checking success criteria ...\n");

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

    private void validateCriteria(Run<?, ?> run, TaskListener listener) throws IOException, InterruptedException
    {
        // get the test report document
        final Document dataXml = getDataDocument(run);

        // process the test report
        final List<CriterionResult> failedAlerts = getFailedCriteria(run, listener, dataXml);
        final List<TestCaseInfo> failedTestCases = determineFailedTestCases(run, listener, dataXml);
        final List<SlowRequestInfo> slowestRequests = determineSlowestRequests(run, listener, dataXml);

        // create the action with all the collected data
        XltRecorderAction recorderAction = new XltRecorderAction(getStepId(), getBuildReportURL(run), failedAlerts, failedTestCases, slowestRequests);
        run.addAction(recorderAction);

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
            run.setResult(Result.UNSTABLE);
        }

        // set build parameters
        if (!recorderAction.getAlerts().isEmpty())
        {
            if (!recorderAction.getFailedAlerts().isEmpty())
            {
                setBuildParameterXLT_CONDITION_FAILED(true);
                checkForCritical(run);
            }

            if (!recorderAction.getErrorAlerts().isEmpty())
            {
                setBuildParameterXLT_CONDITION_ERROR(true);
            }
        }
        setBuildParameterXLT_CONDITION_MESSAGE(recorderAction.getConditionMessage());
    }

    private void checkForCritical(Run<?, ?> currentBuild)
    {
        final int mcBuildCount = getMarkCriticalBuildCount();
        final int mcCondCount = getMarkCriticalConditionCount();
        if (getMarkCriticalEnabled() && (mcBuildCount > 0 && mcCondCount > 0 && mcBuildCount >= mcCondCount))
        {
            int failedCriterionBuilds = 0;
            for (Run<?, ?> eachBuild : getRuns(currentBuild, 0, mcBuildCount))
            {
                XltRecorderAction recorderAction = eachBuild.getAction(XltRecorderAction.class);
                if (recorderAction != null)
                {
                    if (!recorderAction.getFailedAlerts().isEmpty())
                    {
                        failedCriterionBuilds++;
                        if (failedCriterionBuilds == mcCondCount)
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
    private List<TestCaseInfo> determineFailedTestCases(Run<?, ?> build, TaskListener listener, Document dataXml)
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
    private List<SlowRequestInfo> determineSlowestRequests(Run<?, ?> build, TaskListener listener, Document dataXml)
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

    public static String getResourcePath(String fileName)
    {
        return "/plugin/" + Jenkins.getInstance().getPlugin(XltDescriptor.PLUGIN_NAME).getWrapper().getShortName() + "/" + fileName;
    }

    public void publishBuildParameters(Run<?, ?> run)
    {
        run.addAction(new XltParametersAction(new ArrayList<ParameterValue>(buildParameterMap.values()), stepId));
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
        setBuildParameter(ENVIRONMENT_KEYS.XLT_REPORT_URL, StringUtils.defaultString(reportURL));
    }

    protected void init()
    {
        buildParameterMap = new TreeMap<ENVIRONMENT_KEYS, ParameterValue>();
        setBuildParameterXLT_RUN_FAILED(false);
        setBuildParameterXLT_CONDITION_FAILED(false);
        setBuildParameterXLT_CONDITION_ERROR(false);
        setBuildParameterXLT_CONDITION_CRITICAL(false);
        setBuildParameterXLT_REPORT_URL(null);
        setBuildParameterXLT_CONDITION_MESSAGE("");

        updateConfig();
    }

    private void performPostTestSteps(Run<?, ?> run, Launcher launcher, TaskListener listener, boolean artifactsSaved)
    {
        // terminate Amazon's EC2 instances
        if (isEC2UsageEnabled())
        {
            try
            {
                terminateEc2Machine(run, launcher, listener);
            }
            catch (Exception e)
            {
                listener.getLogger().println("Could not terminate Amazon EC2 instances! " + e);
                LOGGER.error("Could not terminate Amazon EC2 instances!", e);
                run.setResult(Result.FAILURE);
            }
        }

        if (getCreateSummaryReport() && artifactsSaved)
        {
            try
            {
                createSummaryReport(run, launcher, listener);
            }
            catch (Exception e)
            {
                listener.getLogger().println("Summary report failed. " + e);
                LOGGER.error("Summary report failed", e);
            }
        }

        if (getCreateTrendReport() && artifactsSaved)
        {
            try
            {
                createTrendReport(run, launcher, listener);
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
            saveArtifact(getXltLogFolder(run, launcher), getBuildLogsFolder(run));
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
            FilePath tempProjectFolder = getTemporaryXltProjectFolder(run, launcher);
            tempProjectFolder.deleteRecursive();

            FilePath tempFolder = getTemporaryXltBaseFolder(run, launcher);
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
        return agentControllerConfig instanceof AmazonEC2;
    }

    private void configureAgentController(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws Exception
    {
        listener.getLogger()
                .println("-----------------------------------------------------------------\nConfiguring agent controllers ...\n");

        List<String> agentControllerUrls = Collections.emptyList();
        if (agentControllerConfig instanceof Embedded)
        {
            listener.getLogger().println("Set to embedded mode");
        }
        else
        {
            if (agentControllerConfig instanceof UrlFile)
            {
                final UrlFile uFile = (UrlFile) agentControllerConfig;
                String urlFile = uFile.getUrlFile();
                if (StringUtils.isNotBlank(urlFile))
                {
                    FilePath file = new FilePath(getTestSuiteConfigFolder(workspace), urlFile);

                    listener.getLogger().printf("Read agent controller URLs from file %s:\n\n", file);

                    ((UrlFile) agentControllerConfig).parse(file.getParent());
                    agentControllerUrls = uFile.getItems();
                }

            }
            else if (isEC2UsageEnabled())
            {
                agentControllerUrls = startEc2Machine(run, launcher, listener);
            }
            else if (agentControllerConfig instanceof UrlList)
            {
                listener.getLogger().println("Read agent controller URLs from configuration:\n");
                agentControllerUrls = ((UrlList) agentControllerConfig).getItems();
            }

            if (agentControllerUrls.isEmpty())
            {
                throw new IllegalStateException("No agent controller URLs found.");
            }

            for (String agentControllerUrl : agentControllerUrls)
            {
                listener.getLogger().println(agentControllerUrl);
            }
        }

        listener.getLogger().println("\nFinished");

    }

    private List<String> startEc2Machine(Run<?, ?> build, Launcher launcher, TaskListener listener) throws Exception
    {
        listener.getLogger()
                .println("-----------------------------------------------------------------\nStarting agent controller with EC2 admin tool ...\n");

        // build the EC2 admin command line
        final List<String> commandLine = new ArrayList<String>();

        if (launcher.isUnix())
        {
            commandLine.add("./ec2_admin.sh");
        }
        else
        {
            commandLine.add("cmd.exe");
            commandLine.add("/c");
            commandLine.add("ec2_admin.cmd");
        }
        final AmazonEC2 ec2Config = (AmazonEC2) agentControllerConfig;
        commandLine.addAll(ec2Config.toEC2AdminArgs(getXltConfigFolder(build, launcher)));

        // path to output file is last argument
        final String acUrlsPath = commandLine.get(commandLine.size() - 1);

        appendEc2Properties(build, launcher, listener);

        // run the EC2 admin tool
        FilePath workingDirectory = getXltBinFolder(build, launcher);
        int commandResult = executeCommand(launcher, workingDirectory, commandLine, listener);
        listener.getLogger().println("EC2 admin tool returned with exit code: " + commandResult);

        if (commandResult != 0)
        {
            throw new Exception("Failed to start instances. EC2 admin tool returned with exit code: " + commandResult);
        }

        final FilePath acUrlFile = new FilePath(new File(acUrlsPath));

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

        return urls;
    }

    private void appendEc2Properties(Run<?, ?> build, Launcher launcher, TaskListener listener) throws Exception
    {
        BufferedWriter writer = null;

        final String awsCredentialId = ((AmazonEC2) agentControllerConfig).getAwsCredentials();
        if (StringUtils.isBlank(awsCredentialId))
        {
            return;
        }

        try
        {

            // append properties to the end of the file
            // the last properties will win so this would overwrite the original properties
            final FilePath ec2PropertiesFile = new FilePath(getXltConfigFolder(build, launcher), "ec2_admin.properties");
            final String originalContent = ec2PropertiesFile.readToString();

            // this will overwrite the existing file so we need the original content to recreate the old content
            final OutputStream outStream = ec2PropertiesFile.write();

            writer = new BufferedWriter(new OutputStreamWriter(outStream));

            writer.write(originalContent);
            writer.newLine();

            final List<AwsCredentials> availableCredentials = CredentialsProvider.lookupCredentials(AwsCredentials.class, build.getParent(),
                                                                                                    ACL.SYSTEM, new DomainRequirement[0]);

            final AwsCredentials credentials = CredentialsMatchers.firstOrNull(availableCredentials,
                                                                               CredentialsMatchers.withId(awsCredentialId));
            if (credentials != null)
            {
                writer.write("aws.accessKey=" + Secret.toString(credentials.getAccessKey()));
                writer.newLine();
                writer.write("aws.secretKey=" + Secret.toString(credentials.getSecretKey()));
                writer.newLine();
            }
            else
            {
                final String logMsg = String.format("Credentials no longer available. (id: \"%s\")", awsCredentialId);
                LOGGER.warn(logMsg);
                listener.getLogger().println(logMsg);

                throw new Exception("Credentials no longer available.");
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

    private void terminateEc2Machine(Run<?, ?> build, Launcher launcher, TaskListener listener) throws Exception
    {
        listener.getLogger()
                .println("-----------------------------------------------------------------\nTerminating agent controller with EC2 admin tool ...\n");

        // build the EC2 admin command line
        List<String> commandLine = new ArrayList<String>();

        if (launcher.isUnix())
        {
            commandLine.add("./ec2_admin.sh");
        }
        else
        {
            commandLine.add("cmd.exe");
            commandLine.add("/c");
            commandLine.add("ec2_admin.cmd");
        }
        commandLine.add("terminate");

        {
            final AmazonEC2 ec2Config = (AmazonEC2) agentControllerConfig;
            commandLine.add(ec2Config.getRegion());
            commandLine.add(ec2Config.getTagName());

        }

        // run the EC2 admin tool
        FilePath workingDirectory = getXltBinFolder(build, launcher);
        int commandResult = executeCommand(launcher, workingDirectory, commandLine, listener);
        listener.getLogger().println("EC2 admin tool returned with exit code: " + commandResult);

        if (commandResult != 0)
        {
            throw new Exception("Failed to terminate instances. EC2 admin tool returned with exit code: " + commandResult);
        }
    }

    private FilePath getTestSuiteFolder(FilePath workspace)
    {
        if (StringUtils.isBlank(pathToTestSuite))
        {
            return workspace;
        }
        else
        {
            return new FilePath(workspace, pathToTestSuite);
        }
    }

    private FilePath getTestSuiteConfigFolder(FilePath workspace)
    {
        return new FilePath(getTestSuiteFolder(workspace), "config");
    }

    private FilePath getTestPropertiesFile(FilePath workspace)
    {
        if (StringUtils.isBlank(testPropertiesFile))
        {
            return null;
        }
        else
        {
            return new FilePath(getTestSuiteConfigFolder(workspace), testPropertiesFile);
        }
    }

    private void initialCleanUp(Run<?, ?> run, Launcher launcher, TaskListener listener)
        throws IOException, InterruptedException, BuildNodeGoneException
    {
        listener.getLogger()
                .println("-----------------------------------------------------------------\nCleaning up project directory ...\n");

        getTemporaryXltProjectFolder(run, launcher).deleteRecursive();

        listener.getLogger().println("\nFinished");
    }

    private void copyXlt(Run<?, ?> run, Launcher launcher, TaskListener listener) throws Exception
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
        FilePath destDir = getTemporaryXltFolder(run, launcher);
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
        FilePath workingDirectory = getXltBinFolder(run, launcher);

        for (FilePath child : workingDirectory.list())
        {
            child.chmod(0777);
        }

        listener.getLogger().println("\nFinished");
    }

    private void runMasterController(Run<?, ?> run, Launcher launcher, FilePath workspace, TaskListener listener) throws Exception
    {
        listener.getLogger().println("-----------------------------------------------------------------\nRunning master controller ...\n");

        // build the master controller command line
        List<String> commandLine = new ArrayList<String>();

        if (launcher.isUnix())
        {
            commandLine.add("./mastercontroller.sh");
        }
        else
        {
            commandLine.add("cmd.exe");
            commandLine.add("/c");
            commandLine.add("mastercontroller.cmd");
        }

        commandLine.addAll(agentControllerConfig.toCmdLineArgs());

        // set the initialResponseTimeout property
        if (agentControllerConfig != null && !(agentControllerConfig instanceof Embedded))
        {
            commandLine.add("-Dcom.xceptance.xlt.mastercontroller.initialResponseTimeout=" + (initialResponseTimeout * 1000));
        }
        commandLine.add("-auto");

        if (StringUtils.isNotBlank(testPropertiesFile))
        {
            validateTestPropertiesFile(launcher, workspace);
            commandLine.add("-testPropertiesFile");
            commandLine.add(testPropertiesFile);
        }

        validateTestSuiteDirectory(workspace);
        commandLine.add("-Dcom.xceptance.xlt.mastercontroller.testSuitePath=" + getTestSuiteFolder(workspace).getRemote());
        commandLine.add("-Dcom.xceptance.xlt.mastercontroller.results=" + getXltResultFolder(run, launcher).getRemote());

        // run the master controller
        FilePath workingDirectory = getXltBinFolder(run, launcher);
        int commandResult = executeCommand(launcher, workingDirectory, commandLine, listener);
        listener.getLogger().println("Master controller returned with exit code: " + commandResult);

        if (commandResult != 0)
        {
            throw new Exception("Master controller returned with exit code: " + commandResult);
        }
    }

    public static hudson.model.Node getBuildNodeIfOnlineOrFail(Launcher launcher) throws BuildNodeGoneException
    {
        hudson.model.Node node = getBuildNode(launcher);
        if (node != null)
        {
            return node;
        }
        throw new BuildNodeGoneException("Build node is not available");
    }

    public static hudson.model.Node getBuildNode(Launcher launcher)
    {
        hudson.model.Node node = null;
        for (final Computer c : Jenkins.getActiveInstance().getComputers())
        {
            if (c.getChannel() == launcher.getChannel() && c.isOnline())
            {
                node = c.getNode();
                break;
            }
        }

        return node;
    }

    public static boolean isRelativeFilePathOnNode(Launcher launcher, String filePath) throws BuildNodeGoneException
    {
        if (launcher.isUnix() && (filePath.startsWith("/") || filePath.startsWith("~")))
        {
            return false;
        }
        else if (filePath.startsWith("\\") || filePath.contains(":"))
        {
            return false;
        }
        return true;
    }

    private void validateTestPropertiesFile(Launcher launcher, FilePath workspace) throws Exception
    {
        if (StringUtils.isBlank(testPropertiesFile))
        {
            return;
        }

        if (!isRelativeFilePathOnNode(launcher, testPropertiesFile))
        {
            throw new Exception("The test properties file path must be relative to the \"<testSuite>/config/\" directory. (" +
                                testPropertiesFile + ")");
        }

        final FilePath testProperties = getTestPropertiesFile(workspace);
        if (testProperties == null || !testProperties.exists())
        {
            throw new Exception("The test properties file does not exists. (" + testProperties.getRemote() + ")");
        }
        else if (testProperties.isDirectory())
        {
            throw new Exception("The test properties file path  must specify a file, not a directory. (" + testProperties.getRemote() +
                                ")");
        }
    }

    private void validateTestSuiteDirectory(FilePath workspace) throws Exception
    {
        FilePath testSuiteDirectory = getTestSuiteFolder(workspace);
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

    private void createReport(Run<?, ?> run, Launcher launcher, TaskListener listener) throws Exception
    {
        listener.getLogger().println("-----------------------------------------------------------------\nRunning report generator ...\n");

        FilePath resultSubFolder = getFirstSubFolder(getXltResultFolder(run, launcher));
        if (resultSubFolder == null || resultSubFolder.list() == null || resultSubFolder.list().isEmpty())
        {
            throw new Exception("No results found at: " + getXltResultFolder(run, launcher).getRemote());
        }
        resultSubFolder.moveAllChildrenTo(getXltResultFolder(run, launcher));

        // build the master controller command line
        List<String> commandLine = new ArrayList<String>();

        if (launcher.isUnix())
        {
            commandLine.add("./create_report.sh");
        }
        else
        {
            commandLine.add("cmd.exe");
            commandLine.add("/c");
            commandLine.add("create_report.cmd");
        }

        commandLine.add("-o");
        commandLine.add(getXltReportFolder(run, launcher).getRemote());
        commandLine.add("-linkToResults");
        commandLine.add("yes");
        commandLine.add(getXltResultFolder(run, launcher).getRemote());
        commandLine.add("-Dmonitoring.trackSlowestRequests=true");

        // run the report generator
        FilePath workingDirectory = getXltBinFolder(run, launcher);
        int commandResult = executeCommand(launcher, workingDirectory, commandLine, listener);
        listener.getLogger().println("Report generator returned with exit code: " + commandResult);

        if (commandResult != 0)
        {
            throw new Exception("Report generator returned with exit code: " + commandResult);
        }
    }

    private void saveArtifacts(Run<?, ?> run, Launcher launcher, TaskListener listener)
        throws IOException, InterruptedException, BuildNodeGoneException
    {
        listener.getLogger()
                .println("\n\n-----------------------------------------------------------------\nArchive results and report...\n");

        run.pickArtifactManager();
        // save load test results and report (copy from node)
        saveArtifact(getXltResultFolder(run, launcher), getBuildResultFolder(run));
        saveArtifact(getXltReportFolder(run, launcher), getBuildReportFolder(run));
        setBuildParameterXLT_REPORT_URL(getBuildReportURL(run));
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

    private int executeCommand(Launcher launcher, FilePath workingDirectory, List<String> commandLine, TaskListener logger)
        throws IOException, InterruptedException
    {

        ProcStarter starter = launcher.launch();
        starter.pwd(workingDirectory);
        starter.cmds(commandLine);

        starter.stdout(logger);

        // starts process and waits for its completion
        return starter.join();
    }

    private void createSummaryReport(Run<?, ?> build, Launcher launcher, TaskListener listener) throws Exception
    {
        listener.getLogger().println("-----------------------------------------------------------------\nCreating summary report ...\n");

        // copy the results of the last n builds to the summary results directory
        copyResults(build, launcher, listener);

        // build report generator command line
        List<String> commandLine = new ArrayList<String>();

        if (launcher.isUnix())
        {
            commandLine.add("./create_report.sh");
        }
        else
        {
            commandLine.add("cmd.exe");
            commandLine.add("/c");
            commandLine.add("create_report.cmd");
        }

        FilePath outputFolder = getSummaryReportFolder(build.getParent());
        commandLine.add("-o");
        commandLine.add(outputFolder.getRemote());

        commandLine.add(getSummaryResultsFolder(build.getParent()).getRemote());
        // run the report generator on the master
        int commandResult = executeCommand(launcher, getXltBinFolderOnMaster(), commandLine, listener);
        listener.getLogger().println("Load report generator returned with exit code: " + commandResult);
        if (commandResult != 0)
        {
            build.setResult(Result.FAILURE);
        }
    }

    private void copyResults(Run<?, ?> currentBuild, Launcher launcher, TaskListener listener) throws InterruptedException, IOException
    {
        // recreate a fresh summary results directory
        FilePath summaryResultsFolder = getSummaryResultsFolder(currentBuild.getParent());

        summaryResultsFolder.deleteRecursive();
        summaryResultsFolder.mkdirs();

        // copy config from the current build's results
        FilePath configFolder = getBuildResultConfigFolder(currentBuild);
        FilePath summaryConfigFolder = getSummaryResultsConfigFolder(currentBuild.getParent());

        configFolder.copyRecursiveTo(summaryConfigFolder);

        // copy timer data from the last n builds

        List<Run<?, ?>> builds = new ArrayList<Run<?, ?>>(currentBuild.getPreviousBuildsOverThreshold(getNumberOfBuildsForSummaryReport() -
                                                                                                      1, Result.UNSTABLE));
        builds.add(0, currentBuild);

        for (Run<?, ?> build : builds)
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

    private void createTrendReport(Run<?, ?> build, Launcher launcher, TaskListener listener) throws Exception
    {
        listener.getLogger().println("-----------------------------------------------------------------\nCreating trend report ...\n");

        List<String> commandLine = new ArrayList<String>();

        if (launcher.isUnix())
        {
            commandLine.add("./create_trend_report.sh");
        }
        else
        {
            commandLine.add("cmd.exe");
            commandLine.add("/c");
            commandLine.add("create_trend_report.cmd");
        }

        FilePath trendReportDest = getTrendReportFolder(build.getParent());
        commandLine.add("-o");
        commandLine.add(trendReportDest.getRemote());

        // get some previous builds that were either UNSTABLE or SUCCESS
        List<Run<?, ?>> builds = new ArrayList<Run<?, ?>>(build.getPreviousBuildsOverThreshold(getNumberOfBuildsForTrendReport() - 1,
                                                                                               Result.UNSTABLE));
        // add the current build
        builds.add(build);

        // add the report directories
        int numberOfBuildsWithReports = 0;
        for (Run<?, ?> eachBuild : builds)
        {
            FilePath reportDirectory = getBuildReportFolder(eachBuild);
            if (reportDirectory.isDirectory())
            {
                commandLine.add(reportDirectory.getRemote());
                numberOfBuildsWithReports++;
            }
        }

        // check whether we have enough builds with reports to create a trend report
        if (numberOfBuildsWithReports > 1)
        {
            // run trend report generator on master
            int commandResult = executeCommand(launcher, getXltBinFolderOnMaster(), commandLine, listener);
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

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
        throws InterruptedException, IOException
    {

        if (StringUtils.isBlank(stepId))
        {
            throw new AbortException("Parameter 'stepId' must not be blank");
        }

        if (StringUtils.isBlank(xltTemplateDir))
        {
            throw new AbortException("Parameter 'xltTemplateDir' must not be blank");
        }

        if (workspace == null)
        {
            throw new AbortException("Cannot run without a workspace");
        }

        init();

        try
        {
            initialCleanUp(run, launcher, listener);
        }
        catch (Exception e)
        {
            listener.getLogger().println("Cleanup failed: " + e.getMessage());
            LOGGER.error("Cleanup failed: ", e);
        }

        boolean artifactsSaved = false;
        try
        {
            copyXlt(run, launcher, listener);

            configureAgentController(run, workspace, launcher, listener);
            runMasterController(run, launcher, workspace, listener);

            createReport(run, launcher, listener);
            saveArtifacts(run, launcher, listener);
            artifactsSaved = true;

            validateCriteria(run, listener);
        }
        catch (InterruptedException e)
        {
            run.setResult(run.getExecutor().abortResult());
        }
        catch (Exception e)
        {
            run.setResult(Result.FAILURE);
            listener.getLogger().println("Build failed: " + e.getMessage());
            LOGGER.error("Build failed", e);
        }
        finally
        {
            performPostTestSteps(run, launcher, listener, artifactsSaved);

            if (run.getResult() == Result.FAILURE)
            {
                setBuildParameterXLT_RUN_FAILED(true);
            }
            publishBuildParameters(run);

            if (artifactsSaved)
            {
                publishChartData(run);
            }
        }

    }

    protected Object readResolve()
    {
        if (builderID != null)
        {
            stepId = builderID;
        }
        if (isPlotVertical)
        {
            plotVertical = true;
        }
        if (createTrendReport)
        {
            trendReportOption = new TrendReportOption(numberOfBuildsForTrendReport);
        }
        if (createSummaryReport)
        {
            summaryReportOption = new SummaryReportOption(numberOfBuildsForSummaryReport);
        }
        if (markCriticalEnabled)
        {
            markCriticalOption = new MarkCriticalOption(markCriticalConditionCount, markCriticalBuildCount);
        }

        return this;
    }
}
