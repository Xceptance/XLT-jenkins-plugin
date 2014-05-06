package com.xceptance.xlt.tools.jenkins;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Fingerprint.RangeSet;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
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
import org.kohsuke.stapler.QueryParameter;
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

    private final String testProperties;

    private boolean testPropertiesFileAvailable = true;

    private final String machineHost;

    private final String xltConfig;

    transient private JSONObject config = new JSONObject();

    private final int plotWidth;

    private final int plotHeight;

    private final String plotTitle;

    private final String builderID;

    private final boolean isPlotVertical;

    transient public static final Logger LOGGER = Logger.getLogger(LoadTestBuilder.class);

    transient private List<Chart<Integer, Double>> charts = new ArrayList<Chart<Integer, Double>>();

    transient private boolean isSave = false;

    transient private XltChartAction chartAction;

    public enum CONFIG_CRITERIA_PARAMETER
    {
        id, xPath, condition, plotID, name
    };

    public enum CONFIG_PLOT_PARAMETER
    {
        id, title, buildCount, enabled
    };

    public enum CONFIG_SECTIONS_PARAMETER
    {
        criteria, plots
    };

    static
    {
        try
        {
            File logFile = new File(new File(Jenkins.getInstance().getPlugin("xlt-jenkins").getWrapper().baseResourceURL.toURI()),
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
    public LoadTestBuilder(String testProperties, String machineHost, String xltConfig, int plotWidth, int plotHeight, String plotTitle,
                           String builderID, boolean isPlotVertical)
    {
        isSave = true;
        Thread.currentThread().setUncaughtExceptionHandler(new UncaughtExceptionHandler()
        {
            public void uncaughtException(Thread t, Throwable e)
            {
                LOGGER.error("Uncaught exception", e);
                e.printStackTrace();
            }
        });

        if (testProperties == null || testProperties.isEmpty())
        {
            testPropertiesFileAvailable = false;
        }
        this.testProperties = testProperties;
        this.machineHost = machineHost;

        if (StringUtils.isBlank(xltConfig))
        {
            xltConfig = getDescriptor().getDefaultXltConfig();
        }
        this.xltConfig = xltConfig;

        if (plotWidth == 0)
        {
            plotWidth = getDescriptor().getDefaultPlotWidth();
        }
        this.plotWidth = plotWidth;

        if (plotHeight == 0)
        {
            plotHeight = getDescriptor().getDefaultPlotHeight();
        }
        this.plotHeight = plotHeight;

        if (plotTitle == null)
        {
            plotTitle = getDescriptor().getDefaultPlotTitle();
        }
        this.plotTitle = plotTitle;

        if (StringUtils.isBlank(builderID))
        {
            builderID = UUID.randomUUID().toString();
        }
        this.builderID = builderID;

        this.isPlotVertical = isPlotVertical;
    }

    public String getTestProperties()
    {
        return testProperties;
    }

    public String getMachineHost()
    {
        return machineHost;
    }

    public String getXltConfig()
    {
        return xltConfig;
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

    public List<Chart<Integer, Double>> getEnabledCharts()
    {
        List<Chart<Integer, Double>> enabledCharts = new ArrayList<Chart<Integer, Double>>();
        for (Chart<Integer, Double> eachChart : charts)
        {
            String enabled = optPlotConfigValue(eachChart.getChartID(), CONFIG_PLOT_PARAMETER.enabled);

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
            int largestBuildCount = 0;
            for (String eachPlotID : getPlotConfigIDs())
            {
                String buildCountValue = optPlotConfigValue(eachPlotID, CONFIG_PLOT_PARAMETER.buildCount);
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
                        e.printStackTrace();
                    }
                }
            }
            if (largestBuildCount < 1)
            {
                largestBuildCount = -1;
            }
            List<? extends AbstractBuild<?, ?>> builds = getBuilds(project, 0, largestBuildCount);
            addBuildsToCharts(builds);
        }
        catch (JSONException e)
        {
            LOGGER.error("Failed to config section.", e);
            e.printStackTrace();
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

    public List<? extends AbstractBuild<?, ?>> getBuilds(AbstractProject<?, ?> project, int startFrom, int count)
    {
        List<? extends AbstractBuild<?, ?>> allBuilds = project.getBuilds();
        int to = startFrom + count - 1;
        if (to < 0)
        {
            to = 0;
        }
        else
        {
            to = Math.min(to, allBuilds.size());
        }

        return allBuilds.subList(startFrom, to);
    }

    public Document getDataDocument(AbstractBuild<?, ?> build)
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
                e.printStackTrace();
            }
            catch (IOException e)
            {
                LOGGER.error("", e);
                e.printStackTrace();
            }
            catch (ParserConfigurationException e)
            {
                LOGGER.error("", e);
                e.printStackTrace();
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
            if (dataXml != null)
            {
                try
                {
                    for (String eachPlotID : getPlotConfigIDs())
                    {
                        Chart<Integer, Double> chart = getChart(eachPlotID);
                        if (chart == null)
                        {
                            LOGGER.warn("No chart found for plotID: " + eachPlotID);
                            continue;
                        }

                        try
                        {
                            for (String eachCriteriaID : getCriteriaConfigIDs(eachPlotID))
                            {
                                ChartLine<Integer, Double> line = chart.getLine(eachCriteriaID);
                                if (line == null)
                                {
                                    LOGGER.warn("No line found for criteria. (criteriaID: \"" + eachCriteriaID + "\" chartID:\"" +
                                                chart.getChartID() + "\")");
                                    continue;
                                }

                                try
                                {
                                    String xPath = getCriteriaConfigValue(eachCriteriaID, CONFIG_CRITERIA_PARAMETER.xPath);
                                    try
                                    {
                                        Double number = (Double) XPathFactory.newInstance().newXPath()
                                                                             .evaluate(xPath, dataXml, XPathConstants.NUMBER);
                                        if (number.isNaN())
                                        {
                                            LOGGER.warn("Value is not a number. (criteria id: \"" + eachCriteriaID + "\" XPath: \"" +
                                                        xPath + "\"");
                                            continue;
                                        }
                                        else
                                        {
                                            line.addLineValue(new ChartLineValue<Integer, Double>(eachBuild.number, number.doubleValue()));
                                        }
                                    }
                                    catch (XPathExpressionException e)
                                    {
                                        LOGGER.error("Invalid XPath. (criteriaID: \"" + eachCriteriaID + "\" XPath: \"" + xPath + "\")", e);
                                        e.printStackTrace();
                                    }
                                }
                                catch (JSONException e)
                                {
                                    LOGGER.error("Failed to config section. (criteriaID: \"" + eachCriteriaID + "\")", e);
                                    e.printStackTrace();
                                }
                            }
                        }
                        catch (JSONException e)
                        {
                            LOGGER.error("Failed to config section.", e);
                            e.printStackTrace();
                        }
                    }
                }
                catch (JSONException e)
                {
                    LOGGER.error("Failed to config section.", e);
                    e.printStackTrace();
                }
            }
            else
            {
                LOGGER.warn("No test data found for build. (build: \"" + eachBuild.number + "\")");
            }
        }
    }

    private Chart<Integer, Double> createChart(String plotID) throws JSONException
    {
        String chartTitle = optPlotConfigValue(plotID, CONFIG_PLOT_PARAMETER.title);
        if (chartTitle == null)
        {
            chartTitle = "";
        }

        String buildCountValue = optPlotConfigValue(plotID, CONFIG_PLOT_PARAMETER.buildCount);
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
                e.printStackTrace();
            }
        }

        Chart<Integer, Double> chart = new Chart<Integer, Double>(plotID, chartTitle);
        for (String eachCriteriaID : getCriteriaConfigIDs(plotID))
        {
            String lineName = optCriteriaConfigValue(eachCriteriaID, CONFIG_CRITERIA_PARAMETER.name);
            if (lineName == null)
            {
                lineName = "";
            }
            ChartLine<Integer, Double> line = new ChartLine<Integer, Double>(eachCriteriaID, lineName, maxCount);
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
                e.printStackTrace();
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
                e.printStackTrace();
            }
            loadCharts(project);

            chartAction = new XltChartAction(project, getEnabledCharts(), plotWidth, plotHeight, plotTitle, builderID, isPlotVertical);
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
            e.printStackTrace();
        }
    }

    public String getCriteriaConfigValue(String configName, CONFIG_CRITERIA_PARAMETER parameter) throws JSONException
    {
        JSONArray criteriaArray = config.getJSONArray(CONFIG_SECTIONS_PARAMETER.criteria.name());
        for (int i = 0; i < criteriaArray.length(); i++)
        {
            JSONObject each = criteriaArray.getJSONObject(i);
            if (configName.equals(each.getString(CONFIG_CRITERIA_PARAMETER.id.name())))
            {
                return each.getString(parameter.name());
            }
        }
        return null;
    }

    public String optCriteriaConfigValue(String configName, CONFIG_CRITERIA_PARAMETER parameter)
    {
        try
        {
            return getCriteriaConfigValue(configName, parameter);
        }
        catch (JSONException e)
        {
            LOGGER.error("", e);
            e.printStackTrace();
        }
        return null;
    }

    public String getCriteriaPlotConfigValue(String configName, CONFIG_PLOT_PARAMETER parameter) throws JSONException
    {
        String plotID = getCriteriaConfigValue(configName, CONFIG_CRITERIA_PARAMETER.plotID);
        return getPlotConfigValue(plotID, parameter);
    }

    public String optPlotConfigValue(String plotID, CONFIG_PLOT_PARAMETER parameter)
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

    public String getPlotConfigValue(String plotID, CONFIG_PLOT_PARAMETER parameter) throws JSONException
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

    public ArrayList<String> getCriteriaConfigIDs() throws JSONException
    {
        ArrayList<String> criteriaList = new ArrayList<String>();

        if (config != null)
        {
            JSONArray criteriaSection = config.getJSONArray(CONFIG_SECTIONS_PARAMETER.criteria.name());
            for (int i = 0; i < criteriaSection.length(); i++)
            {
                JSONObject each = criteriaSection.getJSONObject(i);
                criteriaList.add(each.getString(CONFIG_CRITERIA_PARAMETER.id.name()));
            }
        }
        return criteriaList;
    }

    public ArrayList<String> getCriteriaConfigIDs(String plotID) throws JSONException
    {
        ArrayList<String> criteriaList = new ArrayList<String>();

        JSONArray criteriaSection = config.getJSONArray(CONFIG_SECTIONS_PARAMETER.criteria.name());
        for (int i = 0; i < criteriaSection.length(); i++)
        {
            String criteriaID = null;
            try
            {
                JSONObject each = criteriaSection.getJSONObject(i);
                criteriaID = each.getString(CONFIG_CRITERIA_PARAMETER.id.name());
                if (plotID.equals(each.getString(CONFIG_CRITERIA_PARAMETER.plotID.name())))
                {
                    criteriaList.add(criteriaID);
                }
            }
            catch (JSONException e)
            {
                String message = "";
                if (criteriaID != null)
                    message = "criteriaID: \"" + criteriaID + "\"";

                LOGGER.error("Failed to get plot id for criteria. (index: " + i + " " + message + ")", e);
                e.printStackTrace();
            }
        }
        return criteriaList;
    }

    public ArrayList<String> getPlotConfigIDs() throws JSONException
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

    public File getTestReportDataFile(AbstractBuild<?, ?> build)
    {
        return new File(build.getArtifactsDir(), builderID + "/report/" + Integer.toString(build.getNumber()) + "/testreport.xml");
    }

    public File getXltResultsFolder(AbstractBuild<?, ?> build)
    {
        return new File(getXltFolder(build), "results");
    }

    public File getFirstXltResultFolder(AbstractBuild<?, ?> build)
    {
        File reportFolder = getXltResultsFolder(build);
        if (reportFolder.exists() && reportFolder.isDirectory())
        {
            File[] subFiles = reportFolder.listFiles();
            for (int i = 0; i < subFiles.length; i++)
            {
                if (subFiles[i].isDirectory())
                {
                    return subFiles[i];
                }
            }
        }
        return null;
    }

    public File getXltReportsFolder(AbstractBuild<?, ?> build)
    {
        return new File(getXltFolder(build), "reports");
    }

    public File getFirstXltReportFolder(AbstractBuild<?, ?> build)
    {
        File reportFolder = getXltReportsFolder(build);
        if (reportFolder.exists() && reportFolder.isDirectory())
        {
            File[] subFiles = reportFolder.listFiles();
            for (int i = 0; i < subFiles.length; i++)
            {
                if (subFiles[i].isDirectory())
                {
                    return subFiles[i];
                }
            }
        }
        return null;
    }

    public File getBuildResultConfigFolder(AbstractBuild<?, ?> build)
    {
        return new File(getBuildResultsFolder(build), "config");
    }

    public File getBuildReportsFolder(AbstractBuild<?, ?> build)
    {
        return new File(build.getArtifactsDir(), builderID + "/report/" + Integer.toString(build.getNumber()));
    }

    public File getBuildResultsFolder(AbstractBuild<?, ?> build)
    {
        return new File(build.getArtifactsDir(), builderID + "/result");
    }

    public File getTrendreportFolder(AbstractProject<?, ?> project)
    {
        return new File(project.getRootDir() + "/trendreport/" + builderID);
    }

    public File getSummaryReportFolder(AbstractProject<?, ?> project)
    {
        return new File(new File(project.getRootDir(), "summaryReport"), builderID);
    }

    public File getSummaryResultFolder(AbstractProject<?, ?> project)
    {
        return new File(new File(project.getRootDir(), "summaryResult"), builderID);
    }

    public File getSummaryResultConfigFolder(AbstractProject<?, ?> project)
    {
        return new File(getSummaryResultFolder(project), "config");
    }

    public File getXltFolder(AbstractBuild<?, ?> build)
    {
        return new File(build.getProject().getRootDir(), Integer.toString(build.getNumber()));
    }

    public File getXltExecutablesFolder(AbstractBuild<?, ?> build)
    {
        return new File(getXltFolder(build), "bin");
    }

    private List<CriteriaResult> validateCriteria(AbstractBuild<?, ?> build, BuildListener listener)
    {
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
                List<String> criteriaIDs = getCriteriaConfigIDs();
                for (String eachID : criteriaIDs)
                {
                    listener.getLogger().println();
                    listener.getLogger().println("Start processing. Criteria:\"" + eachID + "\"");
                    String xPath = null;
                    String condition = null;
                    try
                    {
                        xPath = getCriteriaConfigValue(eachID, CONFIG_CRITERIA_PARAMETER.xPath);
                        condition = optCriteriaConfigValue(eachID, CONFIG_CRITERIA_PARAMETER.condition);
                        if (StringUtils.isBlank(condition))
                        {
                            LOGGER.warn("No condition for criteria. (criteriaID: \"" + eachID + "\")");
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
        listener.getLogger().println();

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
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener)
    {
        return true;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException
    {
        // generate temporary directory for local xlt
        File destDir = getXltFolder(build);
        listener.getLogger().println(destDir.getAbsolutePath());
        destDir.mkdirs();

        try
        {
            // copy XLT to certain directory
            File srcDir = new File(Jenkins.getInstance().getRootDir(), "xlt");
            listener.getLogger().println(srcDir.getAbsolutePath());

            FileUtils.copyDirectory(srcDir, destDir, true);

            // perform XLT
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

            // if no specific machineHost set -embedded
            if (machineHost.isEmpty())
            {
                commandLine.add("-embedded");
            }
            else
            {
                commandLine.add("-Dcom.xceptance.xlt.mastercontroller.agentcontrollers.ac1.url=" + machineHost);
            }

            commandLine.add("-auto");
            commandLine.add("-report");

            if (testPropertiesFileAvailable == true)
            {
                commandLine.add("-testPropertiesFile");
                commandLine.add(testProperties);

            }
            commandLine.add("-Dcom.xceptance.xlt.mastercontroller.testSuitePath=" + build.getModuleRoot().toString());

            File workingDirectory = getXltExecutablesFolder(build);

            // access files
            for (File child : workingDirectory.listFiles())
            {
                child.setExecutable(true);
            }

            boolean interrupted = false;
            int commandResult = 0;
            try
            {
                // start XLT
                commandResult = executeCommand(workingDirectory, commandLine, listener.getLogger());
            }
            catch (InterruptedException e)
            {
                interrupted = true;
                build.setResult(Result.ABORTED);
            }

            if (!interrupted)
            {
                // waiting until XLT is finished and set FAILED in case of unexpected termination
                if (commandResult != 0)
                {
                    build.setResult(Result.FAILURE);
                }
                listener.getLogger().println("mastercontroller return code: " + commandResult);

                listener.getLogger().println("XLT_FINISHED");

                // perform only if XLT was successful
                if (build.getResult() == null || !build.getResult().equals(Result.FAILURE))
                {
                    // copy xlt-report to build directory
                    FileUtils.copyDirectory(getFirstXltReportFolder(build), getBuildReportsFolder(build), true);

                    // copy xlt-result to build directory
                    FileUtils.copyDirectory(getFirstXltResultFolder(build), getBuildResultsFolder(build), true);

                    // copy xlt-logs to build directory
                    File srcXltLog = new File(getXltFolder(build), "log");
                    File destXltLog = new File(build.getArtifactsDir(), builderID + "/log");
                    FileUtils.copyDirectory(srcXltLog, destXltLog, true);

                    postTestExecution(build, listener);

                    updateSummaryReportData(build, listener);
                    createSummaryReport(build, listener);

                    // update trend-report
                    createTrendReport(build, listener);
                }
            }

            // delete temporary directory with local xlt
            FileUtils.deleteDirectory(destDir);
        }
        catch (Exception e)
        {
            build.setResult(Result.FAILURE);
            listener.getLogger().println("Build " + Integer.toString(build.getNumber()) + " is failed: " + e);
            LOGGER.error("", e);
        }
        finally
        {
            FileUtils.deleteDirectory(destDir);
        }

        return true;
    }

    /**
     * @param workingDirectory
     * @param commandLines
     * @param outputLogger
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    private int executeCommand(File workingDirectory, List<String> commandLines, OutputStream outputLogger)
        throws IOException, InterruptedException
    {
        PrintStream logger = new PrintStream(outputLogger);

        ProcessBuilder builder = new ProcessBuilder(commandLines);
        builder.directory(workingDirectory);
        builder.redirectErrorStream(true);

        Process process = builder.start();

        InputStream is = process.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line;
        String lastline = null;

        while ((line = br.readLine()) != null)
        {
            if (Thread.currentThread().isInterrupted())
            {
                br.close();
                process.destroy();
                throw new InterruptedException();
            }

            if (line != null)
            {
                lastline = line;
                logger.println(lastline);
            }

            try
            {
                process.exitValue();
            }
            catch (Exception e)
            {
                // TODO not so nice
                continue;
            }
            break;
        }

        return process.waitFor();
    }

    private void createSummaryReport(AbstractBuild<?, ?> build, BuildListener listener) throws IOException
    {
        try
        {
            List<String> commandLines = new ArrayList<String>();

            if (SystemUtils.IS_OS_WINDOWS)
            {
                commandLines.add("cmd.exe");
                commandLines.add("/c");
                commandLines.add("create_report.cmd");
            }
            else
            {
                commandLines.add("./create_report.sh");
            }

            File outpotFolder = getSummaryReportFolder(build.getProject());
            outpotFolder.mkdirs();

            commandLines.add("-o");
            commandLines.add(outpotFolder.getAbsolutePath());
            commandLines.add(getSummaryResultFolder(build.getProject()).getAbsolutePath());

            int commandResult = executeCommand(getXltExecutablesFolder(build), commandLines, listener.getLogger());
            if (commandResult != 0)
            {
                listener.error("Create report returned with code: " + commandResult);
                build.setResult(Result.FAILURE);
            }
        }
        catch (InterruptedException e)
        {
            listener.error("Creating summary report aborted.");
            build.setResult(Result.ABORTED);
        }
    }

    private void updateSummaryReportData(AbstractBuild<?, ?> build, BuildListener listener) throws IOException
    {
        File resultFolder = getBuildResultsFolder(build);
        File summaryFolder = getSummaryResultFolder(build.getProject());

        File configFolder = getBuildResultConfigFolder(build);
        File summaryConfigFolder = getSummaryResultConfigFolder(build.getProject());

        if (summaryConfigFolder.exists())
        {
            summaryConfigFolder.delete();
        }

        FileUtils.copyDirectory(configFolder, summaryConfigFolder, true);

        updateSummaryCSV(summaryFolder, resultFolder);
    }

    private void updateSummaryCSV(File summaryDestination, File resultSource) throws IOException
    {
        File[] resultFiles = resultSource.listFiles();
        for (int i = 0; i < resultFiles.length; i++)
        {
            File eachResultFile = resultFiles[i];
            if (eachResultFile.isDirectory())
            {
                updateSummaryCSV(new File(summaryDestination, eachResultFile.getName()), eachResultFile);
            }
            else if (eachResultFile.getName().endsWith(".csv"))
            {
                File summaryFile = new File(summaryDestination, eachResultFile.getName());
                if (summaryFile.exists() == false)
                {
                    summaryDestination.mkdirs();
                    summaryFile.createNewFile();
                }
                appendSummaryData(summaryFile, eachResultFile);
            }
        }
    }

    private void appendSummaryData(File summaryDestination, File resultSource) throws IOException
    {
        FileOutputStream writer = new FileOutputStream(summaryDestination, true);
        writer.write(Files.readAllBytes(resultSource.toPath()));
        writer.flush();
        writer.close();
    }

    private void createTrendReport(AbstractBuild<?, ?> build, BuildListener listener) throws IOException, InterruptedException
    {
        List<String> commandLines = new ArrayList<String>();

        if (SystemUtils.IS_OS_WINDOWS)
        {
            commandLines.add("cmd.exe");
            commandLines.add("/c");
            commandLines.add("create_trend_report.cmd");
        }
        else
        {
            commandLines.add("./create_trend_report.sh");
        }

        File trendReportDest = getTrendreportFolder(build.getProject());
        if (!trendReportDest.isDirectory())
        {
            trendReportDest.mkdirs();
        }
        commandLines.add("-o");
        commandLines.add(trendReportDest.toString());

        // get all previous build objects they were UNSTABLE or SUCCESS
        List<AbstractBuild<?, ?>> trendReportPath = new ArrayList<AbstractBuild<?, ?>>(
                                                                                       build.getPreviousBuildsOverThreshold(build.getNumber(),
                                                                                                                            Result.UNSTABLE));
        int numberOfPreviousBuilds = 0;
        File reportDirectory;
        for (AbstractBuild<?, ?> eachBuild : trendReportPath)
        {
            reportDirectory = getBuildReportsFolder(eachBuild);
            if (reportDirectory.isDirectory())
            {
                commandLines.add(reportDirectory.toString());
                numberOfPreviousBuilds++;
            }
        }

        // add also report from current build to testReportProperties
        File currentReportDirectory = getBuildReportsFolder(build);
        if (currentReportDirectory.isDirectory())
        {
            commandLines.add(currentReportDirectory.toString());
        }

        // in case of no previous builds no trend report will created
        if (numberOfPreviousBuilds != 0)
        {

            boolean interrupted = false;
            int commandResult = 0;
            try
            {
                // start XLT
                commandResult = executeCommand(getXltExecutablesFolder(build), commandLines, listener.getLogger());
            }
            catch (InterruptedException e)
            {
                interrupted = true;
                build.setResult(Result.ABORTED);
            }

            if (!interrupted)
            {
                // waiting until trend-report is created
                if (commandResult != 0)
                {
                    listener.getLogger().println("Abort creating trend-report!");
                }
                listener.getLogger().println("return code trend-report-generator: " + commandResult);
            }
        }
        else
        {
            listener.getLogger().println("Cannot create trend report because no previous reports available!");
        }

    }

    @Override
    public DescriptorImpl getDescriptor()
    {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder>
    {
        /**
         * In order to load the persisted global configuration, you have to call load() in the constructor.
         */
        public DescriptorImpl()
        {
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
                LOGGER.error("", e);
                e.printStackTrace();
            }
            catch (IOException e)
            {
                LOGGER.error("", e);
                e.printStackTrace();
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

        /**
         * Performs on-the-fly validation of the form field 'testProperties'.
         */
        public FormValidation doCheckTestProperties(@QueryParameter String value) throws IOException, ServletException
        {
            if (value.isEmpty())
            {
                return FormValidation.warning("Please specify test configuration!");
            }
            return FormValidation.ok();
        }

        /**
         * Performs on-the-fly validation of the form field 'machineHost'.
         */
        public FormValidation doCheckMachineHost(@QueryParameter String value) throws IOException, ServletException
        {
            if (value.isEmpty())
                return FormValidation.ok("-embedded is enabled");

            String regex = "^https://.*";
            Pattern p = Pattern.compile(regex);
            Matcher matcher = p.matcher(value);
            if (!matcher.find())
            {
                return FormValidation.error("invalid host-url");
            }

            return FormValidation.ok();
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
                                return FormValidation.error("Plot buildCount is a dezimal number. (plot id: " + id + ")");
                            }
                        }
                        String plotEnabled = eachPlot.getString(CONFIG_PLOT_PARAMETER.enabled.name());
                        if (StringUtils.isNotBlank(plotEnabled))
                        {
                            if (!("yes".equals(plotEnabled) || "no".equals(plotEnabled)))
                            {
                                return FormValidation.error("Invalid value for plot enabled. Only yes or no is allowed. (plot id: " + id +
                                                            ")");
                            }
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
                return FormValidation.warning("Dezimal number for width. Width will be " + (int) number);
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
                return FormValidation.warning("Dezimal number for height. Height will be " + (int) number);
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
            return "XLT Plugin";
        }

    }
}
