package com.xceptance.xlt.tools.jenkins;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.xceptance.xlt.tools.jenkins.Chart.ChartLineValue;
import com.xceptance.xlt.tools.jenkins.config.AgentControllerConfig;
import com.xceptance.xlt.tools.jenkins.config.AmazonEC2;
import com.xceptance.xlt.tools.jenkins.config.Embedded;
import com.xceptance.xlt.tools.jenkins.config.UrlFile;
import com.xceptance.xlt.tools.jenkins.config.UrlList;
import com.xceptance.xlt.tools.jenkins.logging.LOGGER;
import com.xceptance.xlt.tools.jenkins.pipeline.LoadTestResult;
import com.xceptance.xlt.tools.jenkins.util.ChartUtils;
import com.xceptance.xlt.tools.jenkins.util.ChartUtils.ChartLineListener;
import com.xceptance.xlt.tools.jenkins.util.CriterionChecker;
import com.xceptance.xlt.tools.jenkins.util.Helper;
import com.xceptance.xlt.tools.jenkins.util.Helper.FOLDER_NAMES;
import com.xceptance.xlt.tools.jenkins.util.XmlUtils;

import hudson.AbortException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.util.Secret;
import jenkins.model.Jenkins;

public class XltTask
{
    private final LoadTestConfiguration taskConfig;

    private transient PlotValuesConfiguration config;

    private transient SimpleDateFormat dateFormat;

    private transient final List<Chart<Integer, Double>> charts = new ArrayList<Chart<Integer, Double>>();

    private transient XltResult result;

    private transient JSONObject critOutJSON;

    public XltTask(final LoadTestConfiguration cfg)
    {
        taskConfig = cfg;
    }

    private Document getDataDocument(final Run<?, ?> run) throws IOException, InterruptedException
    {
        FilePath testDataFile = getTestReportDataFile(run);
        if (testDataFile.exists())
        {
            return XmlUtils.parse(testDataFile);
        }
        else
        {
            LOGGER.info("No test data found for build. (build: \"" + run.number + "\" searchLocation: \"" + testDataFile.getRemote() +
                        "\")");
        }
        return null;
    }

    private void addBuildToCharts(final Run<?, ?> run)
    {
        Document dataXml = null;

        try
        {
            dataXml = getDataDocument(run);
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to read test data xml", e);

        }

        if (dataXml == null)
            return;

        charts.addAll(ChartUtils.xml2Charts(dataXml, config, new ChartLineListener()
        {
            @Override
            public void onValueAdded(ChartLineValue<Integer, Double> value)
            {
                if (value != null)
                {
                    value.setDataObjectValue("buildNumber", "\"" + run.number + "\"");
                    value.setDataObjectValue("showBuildNumber", Boolean.toString(taskConfig.isShowBuildNumber()));
                    value.setDataObjectValue("buildTime", "\"" + getDateFormat().format(run.getTime()) + "\"");
                }
            }
        }));

    }

    private void publishChartData(final Run<?, ?> run)
    {
        // Clear list of charts
        charts.clear();

        addBuildToCharts(run);

        run.addAction(new XltChartAction(ChartUtils.getEnabledCharts(charts, config), taskConfig.getPlotWidth(), taskConfig.getPlotHeight(),
                                         taskConfig.getPlotTitle(), taskConfig.getStepId(), ChartUtils.getMaxBuildCount(config),
                                         taskConfig.isPlotVertical(), taskConfig.getCreateTrendReport(),
                                         taskConfig.getCreateSummaryReport()));
    }

    private void updateConfig()
    {
        try
        {
            config = PlotValuesConfiguration.fromJson(new JSONObject(taskConfig.getXltConfig()));
        }
        catch (final JSONException e)
        {
            LOGGER.error("Failed to parse XLT config as JSON object", e);
            config = null;
        }
    }

    private FilePath getTestReportDataFile(final Run<?, ?> run)
    {
        return getBuildReportFolder(run).child("testreport.xml");
    }

    private FilePath getXltResultFolder(final Run<?, ?> run, final Launcher launcher) throws BuildNodeGoneException
    {
        return getTemporaryXltFolder(run, launcher).child(FOLDER_NAMES.ARTIFACT_RESULT);
    }

    private FilePath getXltLogFolder(final Run<?, ?> run, final Launcher launcher) throws BuildNodeGoneException
    {
        return getTemporaryXltFolder(run, launcher).child("log");
    }

    private FilePath getXltReportFolder(final Run<?, ?> run, final Launcher launcher) throws BuildNodeGoneException
    {
        return getTemporaryXltFolder(run, launcher).child(FOLDER_NAMES.ARTIFACT_REPORT);
    }

    private FilePath getXltDiffReportFolder(final Run<?, ?> run, final Launcher launcher) throws BuildNodeGoneException
    {
        return getTemporaryXltFolder(run, launcher).child(FOLDER_NAMES.ARTIFACT_DIFFREPORT);
    }

    private FilePath getFirstSubFolder(final FilePath dir) throws IOException, InterruptedException
    {
        if (dir != null && dir.isDirectory())
        {
            final List<FilePath> subFiles = dir.list();
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

    private FilePath getBuildResultConfigFolder(final Run<?, ?> run)
    {
        return new FilePath(getBuildResultFolder(run), "config");
    }

    private FilePath getBuildLogsFolder(final Run<?, ?> build)
    {
        return Helper.getArtifact(build, taskConfig.getStepId() + "/log");
    }

    private FilePath getBuildReportFolder(final Run<?, ?> run)
    {
        return Helper.getArtifact(run, taskConfig.getStepId() + "/" + FOLDER_NAMES.ARTIFACT_REPORT);
    }

    private FilePath getBuildDiffReportFolder(final Run<?, ?> run)
    {
        return Helper.getArtifact(run, taskConfig.getStepId() + "/" + FOLDER_NAMES.ARTIFACT_DIFFREPORT);
    }

    private FilePath getBuildResultFolder(final Run<?, ?> run)
    {
        return Helper.getArtifact(run, taskConfig.getStepId() + "/" + FOLDER_NAMES.ARTIFACT_RESULT);
    }

    private String getBuildReportURL(final Run<?, ?> run)
    {
        final StringBuilder sb = new StringBuilder();

        sb.append(StringUtils.defaultString(Jenkins.getActiveInstance().getRootUrl(), "/")).append(run.getUrl())
          .append(XltRecorderAction.RELATIVE_REPORT_URL).append(taskConfig.getStepId()).append('/').append(FOLDER_NAMES.ARTIFACT_REPORT)
          .append("/index.html");
        return sb.toString();
    }

    private String getBuildDiffReportURL(final Run<?, ?> run)
    {
        final StringBuilder sb = new StringBuilder();

        sb.append(StringUtils.defaultString(Jenkins.getActiveInstance().getRootUrl(), "/")).append(run.getUrl())
          .append(XltRecorderAction.RELATIVE_REPORT_URL).append(taskConfig.getStepId()).append('/').append(FOLDER_NAMES.ARTIFACT_DIFFREPORT)
          .append("/index.html");
        return sb.toString();
    }

    private FilePath getTrendReportFolder(final Job<?, ?> job)
    {
        return new FilePath(job.getRootDir()).child("trendReport").child(taskConfig.getStepId());
    }

    private FilePath getTrendResultsFolder(final Job<?, ?> job)
    {
        return new FilePath(job.getRootDir()).child("trendResults").child(taskConfig.getStepId());
    }

    private FilePath getSummaryReportFolder(final Job<?, ?> job)
    {
        return new FilePath(job.getRootDir()).child("summaryReport").child(taskConfig.getStepId());
    }

    private FilePath getSummaryResultsFolder(final Job<?, ?> job)
    {
        return new FilePath(job.getRootDir()).child("summaryResults").child(taskConfig.getStepId());
    }

    private FilePath getSummaryResultsConfigFolder(final Job<?, ?> job)
    {
        return new FilePath(getSummaryResultsFolder(job), "config");
    }

    private static FilePath getTemporaryXltBaseFolder(final Run<?, ?> run, final Launcher launcher) throws BuildNodeGoneException
    {
        final hudson.model.Node node = Helper.getBuildNodeIfOnlineOrFail(launcher);
        FilePath base = new FilePath(run.getParent().getRootDir());
        if (node != Jenkins.getInstance())
        {
            base = node.getRootPath();
        }
        return new FilePath(base, "tmp-xlt");
    }

    private FilePath getTemporaryXltProjectFolder(final Run<?, ?> run, final Launcher launcher) throws BuildNodeGoneException
    {
        return new FilePath(getTemporaryXltBaseFolder(run, launcher), run.getParent().getName());
    }

    private FilePath getTemporaryXltBuildFolder(final Run<?, ?> run, final Launcher launcher) throws BuildNodeGoneException
    {
        return new FilePath(getTemporaryXltProjectFolder(run, launcher), "" + run.getNumber());
    }

    private FilePath getTemporaryXltFolder(final Run<?, ?> run, final Launcher launcher) throws BuildNodeGoneException
    {
        return new FilePath(new FilePath(getTemporaryXltBuildFolder(run, launcher), taskConfig.getStepId()), "xlt");
    }

    private FilePath getXltTemplateFilePath()
    {
        return Helper.resolvePath(taskConfig.getXltTemplateDir());
    }

    private FilePath getXltBinFolder(final Run<?, ?> run, final Launcher launcher) throws BuildNodeGoneException
    {
        return new FilePath(getTemporaryXltFolder(run, launcher), "bin");
    }

    private FilePath getXltBinFolderOnMaster()
    {
        return new FilePath(getXltTemplateFilePath(), "bin");
    }

    private FilePath getXltConfigFolder(final Run<?, ?> run, final Launcher launcher) throws BuildNodeGoneException
    {
        return new FilePath(getTemporaryXltFolder(run, launcher), "config");
    }

    private void validateCriteria(final Run<?, ?> run, final TaskListener listener) throws IOException, InterruptedException
    {
        // get the test report document
        final Document dataXml = getDataDocument(run);

        listener.getLogger().println("-----------------------------------------------------------------\nChecking success criteria ...\n");

        // process the test report
        final List<CriterionResult> failedAlerts = CriterionChecker.getFailed(dataXml, config);

        final List<TestCaseInfo> failedTestCases = determineFailedTestCases(run, listener, dataXml);
        final List<SlowRequestInfo> slowestRequests = determineSlowestRequests(run, listener, dataXml);

        final boolean hasDiffReport = result.getDiffReportUrl() != null;
        if (hasDiffReport && critOutJSON != null)
        {
            failedAlerts.addAll(CriterionChecker.parseCriteriaValidationResult(critOutJSON));
        }

        result.setFailedCriteria(failedAlerts);
        result.setSlowestRequests(slowestRequests);
        result.setTestFailures(failedTestCases);

        // create the action with all the collected data
        XltRecorderAction recorderAction = new XltRecorderAction(taskConfig.getStepId(), getBuildReportURL(run), failedAlerts,
                                                                 failedTestCases, slowestRequests,
                                                                 hasDiffReport ? getBuildDiffReportURL(run) : null);
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
                result.setConditionFailed(true);
                checkForCritical(run);
            }

            if (!recorderAction.getErrorAlerts().isEmpty())
            {
                result.setConditionError(true);
            }
        }
        result.setConditionMessage(recorderAction.getConditionMessage());
    }

    private void checkForCritical(final Run<?, ?> run)
    {
        final int mcBuildCount = taskConfig.getMarkCriticalBuildCount();
        final int mcCondCount = taskConfig.getMarkCriticalConditionCount();
        if (taskConfig.getMarkCriticalEnabled() && (mcBuildCount > 0 && mcCondCount > 0 && mcBuildCount >= mcCondCount))
        {
            int failedCriterionBuilds = 0;
            for (Run<?, ?> eachBuild : Helper.getRuns(run, 0, mcBuildCount))
            {
                XltRecorderAction recorderAction = eachBuild.getAction(XltRecorderAction.class);
                if (recorderAction != null)
                {
                    if (!recorderAction.getFailedAlerts().isEmpty())
                    {
                        failedCriterionBuilds++;
                        if (failedCriterionBuilds == mcCondCount)
                        {
                            result.setConditionCritical(true);
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
     * @param run
     * @param listener
     * @param document
     *            the test report document
     * @return the list of failure info objects
     * @throws IOException
     * @throws InterruptedException
     */
    private List<TestCaseInfo> determineFailedTestCases(final Run<?, ?> run, final TaskListener listener, final Document document)
        throws IOException, InterruptedException
    {
        final List<TestCaseInfo> failedTestCases = new ArrayList<TestCaseInfo>();

        // get the info from the test report
        if (document != null)
        {
            @SuppressWarnings("unchecked")
            final List<Node> matches = XmlUtils.evaluateXPath(document, "/testreport/errors/error", List.class);
            for (final Node n : matches)
            {
                final String testCaseName = XmlUtils.evaluateXPath(n, "testCaseName");
                // ensure action name is null if it was not given in test report
                final String actionName = StringUtils.defaultIfBlank(XmlUtils.evaluateXPath(n, "actionName"), null);
                final String message = XmlUtils.evaluateXPath(n, "message");

                failedTestCases.add(new TestCaseInfo(testCaseName, actionName, message));
            }
        }

        // sort the list by test case name
        Collections.sort(failedTestCases);

        return failedTestCases;
    }

    /**
     * Extracts URL and runtime from the slowest requests in the test report.
     * 
     * @param run
     * @param listener
     * @param document
     *            the test report document
     * @return the list of info objects
     * @throws IOException
     * @throws InterruptedException
     */
    private List<SlowRequestInfo> determineSlowestRequests(final Run<?, ?> run, final TaskListener listener, final Document document)
        throws IOException, InterruptedException
    {
        final List<SlowRequestInfo> slowestRequests = new ArrayList<SlowRequestInfo>();

        // get the info from the test report
        if (document != null)
        {
            @SuppressWarnings("unchecked")
            final List<Node> matches = XmlUtils.evaluateXPath(document, "/testreport/general/slowestRequests/request", List.class);
            for (final Node n : matches)
            {
                final String url = XmlUtils.evaluateXPath(n, "url");
                final String runtime = XmlUtils.evaluateXPath(n, "runtime");

                slowestRequests.add(new SlowRequestInfo(url, runtime));
            }
        }

        return slowestRequests;
    }

    private SimpleDateFormat getDateFormat()
    {
        if (dateFormat == null)
        {
            final String timeFormatPattern = taskConfig.getTimeFormatPattern();
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

    public void publishBuildParameters(final Run<?, ?> run)
    {
        run.addAction(new XltParametersAction(result.getParameterList(), taskConfig.getStepId()));
    }

    protected void init()
    {
        result = new XltResult();

        updateConfig();
    }

    private void performPostTestSteps(final Run<?, ?> run, final Launcher launcher, final TaskListener listener, boolean resultsSaved, boolean reportsSaved)
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

        if (taskConfig.getCreateSummaryReport() && resultsSaved)
        {
            try
            {
                if (taskConfig.getArchiveResults())
                {
                    createSummaryReport(run, listener);
                }
                else
                {
                    listener.getLogger().println("Test results were not archived => SKIPPING creation of summary report");
                }
            }
            catch (Exception e)
            {
                listener.getLogger().println("Summary report failed. " + e);
                LOGGER.error("Summary report failed", e);
            }
        }

        if (taskConfig.getCreateTrendReport() && reportsSaved)
        {
            try
            {
                createTrendReport(run, listener);
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
            Helper.moveFolder(getXltLogFolder(run, launcher), getBuildLogsFolder(run));
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
            if (tempFolder.exists() && (tempFolder.list() == null || tempFolder.list().isEmpty()))
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
        return taskConfig.getAgentControllerConfig() instanceof AmazonEC2;
    }

    private void configureAgentController(final Run<?, ?> run, final FilePath workspace, final Launcher launcher,
                                          final TaskListener listener)
        throws Exception
    {
        listener.getLogger()
                .println("-----------------------------------------------------------------\nConfiguring agent controllers ...\n");

        List<String> agentControllerUrls = Collections.emptyList();

        final AgentControllerConfig acConfig = taskConfig.getAgentControllerConfig();
        if (acConfig instanceof Embedded)
        {
            listener.getLogger().println("Set to embedded mode");
        }
        else
        {
            if (acConfig instanceof UrlFile)
            {
                final UrlFile uFile = (UrlFile) acConfig;
                String urlFile = uFile.getUrlFile();
                if (StringUtils.isNotBlank(urlFile))
                {
                    final FilePath testSuiteConfig = getTestSuiteConfigFolder(workspace);
                    FilePath file = new FilePath(testSuiteConfig, urlFile);

                    listener.getLogger().printf("Read agent controller URLs from file %s:\n\n", file);

                    ((UrlFile) acConfig).parse(testSuiteConfig);
                    agentControllerUrls = uFile.getItems();
                }

            }
            else if (isEC2UsageEnabled())
            {
                agentControllerUrls = startEc2Machine(run, launcher, listener);
            }
            else if (acConfig instanceof UrlList)
            {
                listener.getLogger().println("Read agent controller URLs from configuration:\n");
                agentControllerUrls = ((UrlList) acConfig).getItems();
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

    private List<String> startEc2Machine(final Run<?, ?> run, final Launcher launcher, final TaskListener listener) throws Exception
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
        final AmazonEC2 ec2Config = (AmazonEC2) taskConfig.getAgentControllerConfig();
        commandLine.addAll(ec2Config.toEC2AdminArgs(getXltConfigFolder(run, launcher)));

        // path to output file is last argument
        final String acUrlsPath = commandLine.get(commandLine.size() - 1);

        appendEc2Properties(run, launcher, listener);

        // run the EC2 admin tool
        FilePath workingDirectory = getXltBinFolder(run, launcher);
        int commandResult = Helper.executeCommand(launcher, workingDirectory, commandLine, listener);
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

        ec2Config.setUrlList(StringUtils.join(urls, "\n"));

        return urls;
    }

    private void appendEc2Properties(final Run<?, ?> run, final Launcher launcher, final TaskListener listener) throws Exception
    {
        BufferedWriter writer = null;

        final String awsCredentialId = ((AmazonEC2) taskConfig.getAgentControllerConfig()).getAwsCredentials();
        if (StringUtils.isBlank(awsCredentialId))
        {
            return;
        }

        try
        {

            // append properties to the end of the file
            // the last properties will win so this would overwrite the original properties
            final FilePath ec2PropertiesFile = new FilePath(getXltConfigFolder(run, launcher), "ec2_admin.properties");
            final String originalContent = ec2PropertiesFile.readToString();

            // this will overwrite the existing file so we need the original content to recreate the old content
            final OutputStream outStream = ec2PropertiesFile.write();

            writer = new BufferedWriter(new OutputStreamWriter(outStream));

            writer.write(originalContent);
            writer.newLine();

            final List<AwsCredentials> availableCredentials = CredentialsProvider.lookupCredentials(AwsCredentials.class, run.getParent(),
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

    private void terminateEc2Machine(final Run<?, ?> run, final Launcher launcher, final TaskListener listener) throws Exception
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
            final AmazonEC2 ec2Config = (AmazonEC2) taskConfig.getAgentControllerConfig();
            commandLine.add(ec2Config.getRegion());
            commandLine.add(ec2Config.getTagName());

        }

        // run the EC2 admin tool
        FilePath workingDirectory = getXltBinFolder(run, launcher);
        int commandResult = Helper.executeCommand(launcher, workingDirectory, commandLine, listener);
        listener.getLogger().println("EC2 admin tool returned with exit code: " + commandResult);

        if (commandResult != 0)
        {
            throw new Exception("Failed to terminate instances. EC2 admin tool returned with exit code: " + commandResult);
        }
    }

    private FilePath getTestSuiteFolder(final FilePath workspace)
    {
        final String pathToTestSuite = taskConfig.getPathToTestSuite();
        if (StringUtils.isBlank(pathToTestSuite))
        {
            return workspace;
        }
        else
        {
            return new FilePath(workspace, pathToTestSuite);
        }
    }

    private FilePath getTestSuiteConfigFolder(final FilePath workspace)
    {
        return new FilePath(getTestSuiteFolder(workspace), "config");
    }

    private FilePath getTestPropertiesFile(final FilePath workspace)
    {
        final String testPropertiesFile = taskConfig.getTestPropertiesFile();
        if (StringUtils.isNotBlank(testPropertiesFile))
        {
            return new FilePath(getTestSuiteConfigFolder(workspace), testPropertiesFile);
        }
        return null;
    }

    private FilePath getMCPropertiesFile(final FilePath workspace)
    {
        final String testPropertiesFile = taskConfig.getAdditionalMCPropertiesFile();
        if (StringUtils.isNotBlank(testPropertiesFile))
        {
            return new FilePath(getTestSuiteConfigFolder(workspace), testPropertiesFile);
        }
        return null;
    }

    private void initialCleanUp(final Run<?, ?> run, final Launcher launcher, final TaskListener listener)
        throws IOException, InterruptedException, BuildNodeGoneException
    {
        listener.getLogger()
                .println("-----------------------------------------------------------------\nCleaning up project directory ...\n");

        getTemporaryXltProjectFolder(run, launcher).deleteRecursive();

        listener.getLogger().println("\nFinished");
    }

    private void copyXlt(final Run<?, ?> run, final Launcher launcher, final TaskListener listener) throws Exception
    {
        listener.getLogger().println("-----------------------------------------------------------------\nCopying XLT ...\n");

        // the directory with the XLT template installation
        if (StringUtils.isBlank(taskConfig.getXltTemplateDir()))
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

    private void runMasterController(final Run<?, ?> run, final Launcher launcher, final FilePath workspace, final TaskListener listener)
        throws Exception
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

        final AgentControllerConfig acConfig = taskConfig.getAgentControllerConfig();
        commandLine.addAll(acConfig.toCmdLineArgs());

        // set the initialResponseTimeout property
        if (!(acConfig instanceof Embedded))
        {
            commandLine.add("-Dcom.xceptance.xlt.mastercontroller.initialResponseTimeout=" +
                            (taskConfig.getInitialResponseTimeout() * 1000));
        }
        commandLine.add("-auto");

        final String mcPropertiesFile = taskConfig.getAdditionalMCPropertiesFile();
        if (StringUtils.isNotBlank(mcPropertiesFile))
        {
            validateMCPropertiesFile(launcher, workspace);
            commandLine.add("-pf");
            commandLine.add(getMCPropertiesFile(workspace).getRemote());
        }

        final String testPropertiesFile = taskConfig.getTestPropertiesFile();
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
        int commandResult = Helper.executeCommand(launcher, workingDirectory, commandLine, listener);
        listener.getLogger().println("Master controller returned with exit code: " + commandResult);

        if (commandResult != 0)
        {
            throw new Exception("Master controller returned with exit code: " + commandResult);
        }
    }

    private void validateTestPropertiesFile(final Launcher launcher, final FilePath workspace) throws Exception
    {
        final String testPropertiesFile = taskConfig.getTestPropertiesFile();
        if (StringUtils.isBlank(testPropertiesFile))
        {
            return;
        }

        if (!Helper.isRelativeFilePathOnNode(launcher, testPropertiesFile))
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

    private void validateMCPropertiesFile(final Launcher launcher, final FilePath workspace) throws Exception
    {
        final String propertiesFile = taskConfig.getAdditionalMCPropertiesFile();
        if (StringUtils.isBlank(propertiesFile))
        {
            return;
        }

        final FilePath testProperties = getMCPropertiesFile(workspace);
        if (testProperties == null || !testProperties.exists())
        {
            throw new Exception("Additional master controller properties file does not exists. (" + testProperties.getRemote() + ")");
        }
        else if (testProperties.isDirectory())
        {
            throw new Exception("Path to additional master controller properties denotes a directory instead of a file. (" +
                                testProperties.getRemote() + ")");
        }
    }

    private void validateTestSuiteDirectory(final FilePath workspace) throws Exception
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

    private void createReport(final Run<?, ?> run, final Launcher launcher, final TaskListener listener) throws Exception
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

        // Only link to results when we're going to archive them
        commandLine.add("-linkToResults");
        commandLine.add(BooleanUtils.toStringYesNo(taskConfig.getArchiveResults()));

        commandLine.add(getXltResultFolder(run, launcher).getRemote());
        commandLine.add("-Dmonitoring.trackSlowestRequests=true");

        // run the report generator
        FilePath workingDirectory = getXltBinFolder(run, launcher);
        int commandResult = Helper.executeCommand(launcher, workingDirectory, commandLine, listener);
        listener.getLogger().println("Report generator returned with exit code: " + commandResult);

        if (commandResult != 0)
        {
            throw new Exception("Report generator returned with exit code: " + commandResult);
        }
    }

    private void saveResults(final Run<?, ?> run, final Launcher launcher, final TaskListener listener)
        throws IOException, InterruptedException, BuildNodeGoneException
    {
        listener.getLogger().println("\n\n-----------------------------------------------------------------\nArchive results...\n");

        run.pickArtifactManager();

        // save load test results and report (copy from node)
        if (taskConfig.getArchiveResults())
        {
            Helper.copyFolder(getXltResultFolder(run, launcher), getBuildResultFolder(run));
        }
    }

    private void saveReports(final Run<?, ?> run, final Launcher launcher, final TaskListener listener)
        throws IOException, InterruptedException, BuildNodeGoneException
    {
        listener.getLogger().println("\n\n-----------------------------------------------------------------\nArchive report(s)...\n");

        // take care of load test report
        {
            final FilePath reportFolder = getXltReportFolder(run, launcher);
            if (reportFolder.exists())
            {
                Helper.moveFolder(reportFolder, getBuildReportFolder(run));

                result.setReportUrl(getBuildReportURL(run));
            }
        }

        // take care of difference report
        {
            final FilePath diffReportFolder = getXltDiffReportFolder(run, launcher);
            if (diffReportFolder.exists())
            {
                Helper.moveFolder(diffReportFolder, getBuildDiffReportFolder(run));

                result.setDiffReportUrl(getBuildDiffReportURL(run));
            }
        }
    }

    private void createSummaryReport(final Run<?, ?> run, final TaskListener listener) throws Exception
    {
        listener.getLogger().println("-----------------------------------------------------------------\nCreating summary report ...\n");

        // copy the results of the last n builds to the summary results directory
        copyResults(run, listener);

        // build report generator command line
        List<String> commandLine = new ArrayList<String>();

        final jenkins.model.Jenkins masterNode = Jenkins.getActiveInstance();
        final Launcher launcher = masterNode.createLauncher(listener);
        launcher.decorateFor(masterNode);

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

        FilePath outputFolder = getSummaryReportFolder(run.getParent());
        commandLine.add("-o");
        commandLine.add(outputFolder.getRemote());

        commandLine.add(getSummaryResultsFolder(run.getParent()).getRemote());

        // Redmine #2999: Agent charts pile up in summary report
        // -> wipe out output folder before generating the report
        outputFolder.deleteRecursive();

        // run the report generator on the master
        int commandResult = Helper.executeCommand(launcher, getXltBinFolderOnMaster(), commandLine, listener);
        listener.getLogger().println("Load report generator returned with exit code: " + commandResult);
        if (commandResult != 0)
        {
            run.setResult(Result.FAILURE);
        }
    }

    private void copyResults(final Run<?, ?> run, final TaskListener listener) throws InterruptedException, IOException
    {
        // recreate a fresh summary results directory
        FilePath summaryResultsFolder = getSummaryResultsFolder(run.getParent());

        summaryResultsFolder.deleteRecursive();
        summaryResultsFolder.mkdirs();

        // copy config from the current build's results
        FilePath configFolder = getBuildResultConfigFolder(run);
        FilePath summaryConfigFolder = getSummaryResultsConfigFolder(run.getParent());

        configFolder.copyRecursiveTo(summaryConfigFolder);

        // copy timer data from the last n builds

        List<Run<?, ?>> builds = new ArrayList<Run<?, ?>>(run.getPreviousBuildsOverThreshold(taskConfig.getNumberOfBuildsForSummaryReport() -
                                                                                             1, Result.UNSTABLE));
        builds.add(0, run);

        for (Run<?, ?> build : builds)
        {
            FilePath resultsFolder = getBuildResultFolder(build);
            if (resultsFolder.isDirectory())
            {
                copyResults(summaryResultsFolder, resultsFolder, build.getNumber());
            }
        }
    }

    private void copyResults(final FilePath targetDir, final FilePath srcDir, final int buildNumber)
        throws IOException, InterruptedException
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

    private FilePath copyReport(final FilePath targetDirectory, final FilePath reportDirectory, final int buildNumber)
        throws IOException, InterruptedException
    {
        final FilePath dest = targetDirectory.child(Integer.toString(buildNumber));
        for (final FilePath file : reportDirectory.list())
        {
            if (file.getName().endsWith(".xml"))
            {
                FileUtils.copyFile(new File(file.getRemote()), new File(dest.getRemote(), file.getName()));
            }
        }
        return dest;
    }

    private void createTrendReport(final Run<?, ?> run, final TaskListener listener) throws Exception
    {
        listener.getLogger().println("-----------------------------------------------------------------\nCreating trend report ...\n");

        List<String> commandLine = new ArrayList<String>();

        final jenkins.model.Jenkins masterNode = Jenkins.getActiveInstance();
        final Launcher launcher = masterNode.createLauncher(listener);
        launcher.decorateFor(masterNode);

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

        FilePath trendReportDest = getTrendReportFolder(run.getParent());
        commandLine.add("-o");
        commandLine.add(trendReportDest.getRemote());

        // get some previous builds that were either UNSTABLE or SUCCESS
        List<Run<?, ?>> builds = new ArrayList<Run<?, ?>>(run.getPreviousBuildsOverThreshold(taskConfig.getNumberOfBuildsForTrendReport() -
                                                                                             1, Result.UNSTABLE));
        // add the current build
        builds.add(0, run);

        final FilePath trendResultsFolder = getTrendResultsFolder(run.getParent());

        try
        {
            // add the report directories
            int numberOfBuildsWithReports = 0;
            for (Run<?, ?> eachBuild : builds)
            {
                final FilePath reportDirectory = getBuildReportFolder(eachBuild);
                if (reportDirectory.isDirectory())
                {
                    // copy all XML files to a temporary folder whose name is the number of the build
                    final FilePath tempReportCopy = copyReport(trendResultsFolder, reportDirectory, eachBuild.getNumber());
                    // folder might not exist (e.g. no XML file copied)
                    if (tempReportCopy.exists())
                    {
                        commandLine.add(tempReportCopy.getRemote());
                        numberOfBuildsWithReports++;
                    }
                }
            }

            // check whether we have enough builds with reports to create a trend report
            if (numberOfBuildsWithReports > 1)
            {
                // run trend report generator on master
                int commandResult = Helper.executeCommand(launcher, getXltBinFolderOnMaster(), commandLine, listener);
                listener.getLogger().println("Trend report generator returned with exit code: " + commandResult);
                if (commandResult != 0)
                {
                    run.setResult(Result.FAILURE);
                }
            }
            else
            {
                listener.getLogger().println("Cannot create trend report because no previous reports available!");
            }
        }
        finally
        {
            // trend report generation done -> temporary folder can be removed
            trendResultsFolder.deleteRecursive();
        }
    }

    private void createDiffReport(final Run<?, ?> run, final Launcher launcher, final FilePath workspace, final TaskListener listener)
        throws Exception
    {
        if (!taskConfig.getCreateDiffReport())
        {
            return;
        }

        listener.getLogger().println("-----------------------------------------------------------------\nCreating difference report ...\n");

        final String baseLine = taskConfig.getDiffReportBaseline();
        FilePath baseLinePath = null;
        if (StringUtils.isBlank(baseLine) || baseLine.startsWith("#"))
        {
            Run<?, ?> r = null;

            if (StringUtils.isBlank(baseLine))
            {
                r = run.getPreviousSuccessfulBuild();
                if (r == null)
                {
                    listener.getLogger()
                            .println("Did not find a previous build that was successful => Creation of difference report will be SKIPPED");
                    return;
                }
            }
            else
            {
                r = run.getParent().getBuildByNumber(Integer.parseInt(baseLine.substring(1)));
            }

            if (r != null)
            {
                baseLinePath = getBuildReportFolder(r);
            }
        }
        else
        {
            baseLinePath = new FilePath(Jenkins.getInstance().getRootPath(), baseLine);
        }

        if (baseLinePath == null || !baseLinePath.exists() || !baseLinePath.isDirectory())
        {
            throw new Exception("Failed to resolve difference report directory for '" + baseLine + "'");
        }

        // copy baseline report to temporary directory
        final FilePath tempBaseLineDir = workspace.createTempDir("diff-report", "base");
        baseLinePath.copyRecursiveTo(tempBaseLineDir);

        final ArrayList<String> cmdLine = new ArrayList<>();
        if (launcher.isUnix())
        {
            cmdLine.add("./create_diff_report.sh");
        }
        else
        {
            cmdLine.add("cmd.exe");
            cmdLine.add("/c");
            cmdLine.add("create_diff_report.cmd");
        }

        // configure output directory
        FilePath diffReportDest = getXltDiffReportFolder(run, launcher);
        cmdLine.add("-o");
        cmdLine.add(diffReportDest.getRemote());

        // add remaining args
        cmdLine.add(tempBaseLineDir.getRemote());
        cmdLine.add(getXltReportFolder(run, launcher).getRemote());

        // run difference report generator
        final FilePath xltBinFolder = getXltBinFolder(run, launcher);
        int commandResult;
        try
        {
            commandResult = Helper.executeCommand(launcher, xltBinFolder, cmdLine, listener);
            listener.getLogger().println("Difference report generator returned with exit code: " + commandResult);

            if (commandResult != 0)
            {
                run.setResult(Result.FAILURE);
                return;
            }
        }
        finally
        {
            tempBaseLineDir.deleteRecursive();
        }

        final String critFile = Helper.environmentResolve(taskConfig.getDiffReportCriteriaFile());
        if (StringUtils.isNotBlank(critFile))
        {
            cmdLine.clear();
            final FilePath critFilePath = new FilePath(workspace, critFile);
            if (launcher.isUnix())
            {
                cmdLine.add("./check_criteria.sh");
            }
            else
            {
                cmdLine.add("cmd.exe");
                cmdLine.add("/c");
                cmdLine.add("check_criteria.cmd");
            }

            cmdLine.add("-c");
            cmdLine.add(critFilePath.getRemote());
            cmdLine.add(diffReportDest.child("diffreport.xml").getRemote());

            final ByteArrayOutputStream os = new ByteArrayOutputStream(4096);
            final BufferedOutputStream buf = new BufferedOutputStream(os);
            try
            {
                commandResult = Helper.executeCommand(launcher, xltBinFolder, cmdLine, buf);
            }
            finally
            {
                buf.flush();
                buf.close();
            }

            final byte[] bytes = os.toByteArray();
            final String out = new String(bytes, Charsets.UTF_8);

            listener.getLogger().println("Criteria check returned with exit code: " + commandResult);
            if (commandResult != 0)
            {
                run.setResult(Result.FAILURE);
                listener.getLogger().println("check_criteria output: " + out);
            }
            else
            {
                if (StringUtils.isNotBlank(out))
                {
                    JSONObject o = null;
                    try
                    {
                        o = new JSONObject(out);
                    }
                    catch (JSONException e)
                    {
                        // ignore
                    }

                    critOutJSON = o;
                }
            }
        }
    }

    public LoadTestResult perform(final Run<?, ?> run, final FilePath workspace, final TaskListener listener, final Launcher launcher)
        throws InterruptedException, IOException
    {
        if (StringUtils.isBlank(taskConfig.getStepId()))
        {
            throw new AbortException("Parameter 'stepId' must not be blank");
        }

        if (StringUtils.isBlank(taskConfig.getXltTemplateDir()))
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

        boolean reportsSaved = false, resultsSaved = false;
        try
        {
            copyXlt(run, launcher, listener);

            configureAgentController(run, workspace, launcher, listener);
            runMasterController(run, launcher, workspace, listener);

            saveResults(run, launcher, listener);
            resultsSaved = true;

            createReport(run, launcher, listener);
            createDiffReport(run, launcher, workspace, listener);

            saveReports(run, launcher, listener);
            reportsSaved = true;

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
            performPostTestSteps(run, launcher, listener, resultsSaved, reportsSaved);

            if (run.getResult() == Result.FAILURE)
            {
                result.setRunFailed(true);
            }
            publishBuildParameters(run);

            if (reportsSaved)
            {
                publishChartData(run);
            }
        }

        return new LoadTestResult(result);
    }
}
