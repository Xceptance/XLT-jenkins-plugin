package com.xceptance.xlt.tools.jenkins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.FilePath;
import hudson.Util;
import hudson.model.Result;
import hudson.model.Run;

public class LoadTestStepTest
{

    @Rule
    public final JenkinsRule rule = new JenkinsRule();

    @ClassRule
    public final static BuildWatcher buildWatcher = new BuildWatcher();

    @Test
    public void noArgs() throws Exception
    {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "noArgs");
        j.setDefinition(new CpsFlowDefinition("node {\necho 'hi'\nxlt()\n}\n", true));

        WorkflowRun r = j.scheduleBuild2(0).waitForStart();
        rule.assertBuildStatus(Result.FAILURE, rule.waitForCompletion(r));
        rule.assertLogContains("ERROR: Parameter 'stepId' must not be blank", r);

        assertNull(r.getAction(XltChartAction.class));
        assertNull(r.getAction(XltRecorderAction.class));
        assertNull(r.getAction(XltParametersAction.class));
    }

    @Test
    public void templateDirOnly() throws Exception
    {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "templateDirOnly");
        j.setDefinition(new CpsFlowDefinition("node {\necho 'hi'\nxlt xltTemplateDir: '/tmp'\n}\n", true));

        WorkflowRun r = j.scheduleBuild2(0).waitForStart();
        rule.assertBuildStatus(Result.FAILURE, rule.waitForCompletion(r));
        rule.assertLogContains("ERROR: Parameter 'stepId' must not be blank", r);

        assertNull(r.getAction(XltChartAction.class));
        assertNull(r.getAction(XltRecorderAction.class));
        assertNull(r.getAction(XltParametersAction.class));
    }

    @Test
    public void stepIdOnly() throws Exception
    {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "stepIdOnly");
        j.setDefinition(new CpsFlowDefinition("node {\necho 'hi'\nxlt stepId: 'step1'\n}\n", true));

        WorkflowRun r = j.scheduleBuild2(0).waitForStart();
        rule.assertBuildStatus(Result.FAILURE, rule.waitForCompletion(r));
        rule.assertLogContains("ERROR: Parameter 'xltTemplateDir' must not be blank", r);

        assertNull(r.getAction(XltChartAction.class));
        assertNull(r.getAction(XltRecorderAction.class));
        assertNull(r.getAction(XltParametersAction.class));
    }

    @Test
    public void noMCScript() throws Exception
    {
        WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "stepIdAndTemplateDir");
        j.setDefinition(new CpsFlowDefinition("node {\necho 'hi'\nxlt xltTemplateDir: '/tmp', stepId: 'step1'\n}\n", true));

        WorkflowRun r = j.scheduleBuild2(0).waitForStart();
        rule.assertBuildStatus(Result.FAILURE, rule.waitForCompletion(r));
        rule.assertLogContains("Build failed: No \"mastercontroller\" script found for path: /tmp/bin", r);

        assertNull(r.getAction(XltChartAction.class));
        assertNull(r.getAction(XltRecorderAction.class));

        validateParametersAction(r, "step1");
    }

    @Test
    public void singleStep() throws Exception
    {
        final File tempDir = Util.createTempDir();
        try
        {
            FilePath target = new FilePath(tempDir);
            new FilePath(new File(getClass().getResource("dummy-xlt").toURI())).copyRecursiveTo(target);

            WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "singleStep");
            j.setDefinition(new CpsFlowDefinition("node {\nxlt xltTemplateDir: '" + target.getRemote() + "', stepId: 'step1'\n}\n", true));
            FilePath ws = rule.jenkins.getWorkspaceFor(j);
            ws.createTextTempFile("test", ".properties", "");
            WorkflowRun r = j.scheduleBuild2(0).waitForStart();

            rule.assertBuildStatus(Result.SUCCESS, rule.waitForCompletion(r));
            rule.assertLogContains("Master controller returned with exit code: 0", r);
            rule.assertLogContains("Report generator returned with exit code: 0", r);

            validateChartsAndParameterAction(r, "step1");

            assertEquals(1, r.getActions(XltRecorderAction.class).size());

        }
        finally
        {
            Util.deleteRecursive(tempDir);
        }
    }

    @Test
    public void twoSteps() throws Exception
    {
        final File tempDir = Util.createTempDir();
        try
        {
            FilePath target = new FilePath(tempDir);
            new FilePath(new File(getClass().getResource("dummy-xlt").toURI())).copyRecursiveTo(target);

            WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "twoSteps");
            j.setDefinition(new CpsFlowDefinition("node {\nxlt xltTemplateDir: '" + target.getRemote() + "', stepId: 'step1'\n" +
                                                  "xlt xltTemplateDir: '" + target.getRemote() + "', stepId: 'step2'\n" + "}\n", true));
            FilePath ws = rule.jenkins.getWorkspaceFor(j);
            ws.createTextTempFile("test", ".properties", "");
            WorkflowRun r = j.scheduleBuild2(0).waitForStart();

            rule.assertBuildStatus(Result.SUCCESS, rule.waitForCompletion(r));
            rule.assertLogContains("Master controller returned with exit code: 0", r);
            rule.assertLogContains("Report generator returned with exit code: 0", r);

            validateChartsAndParameterAction(r, "step1", "step2");

            assertEquals(2, r.getActions(XltRecorderAction.class).size());

        }
        finally
        {
            Util.deleteRecursive(tempDir);
        }

    }

    @Test
    public void urlFile_RelativeNonExisting() throws Exception
    {
        final File tempDir = Util.createTempDir();
        try
        {
            FilePath target = new FilePath(tempDir);
            new FilePath(new File(getClass().getResource("dummy-xlt").toURI())).copyRecursiveTo(target);

            WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "urlFileRelNonExisting");
            FilePath ws = rule.jenkins.getWorkspaceFor(j);
            FilePath acUrlsFile = ws.child("acUrls.txt");
            j.setDefinition(new CpsFlowDefinition("node {\nxlt xltTemplateDir: '" + target.getRemote() +
                                                  "', stepId: 'step1', agentControllerConfig: urlFile('" + acUrlsFile.getName() + "') \n" +
                                                  "}\n", true));
            WorkflowRun r = j.scheduleBuild2(0).waitForStart();

            rule.assertBuildStatus(Result.FAILURE, rule.waitForCompletion(r));
            rule.assertLogContains("Read agent controller URLs from file", r);
            rule.assertLogContains("Build failed: " + (ws.child("config").child(acUrlsFile.getName()).getRemote()) +
                                   " (No such file or directory)", r);
        }
        finally
        {
            Util.deleteRecursive(tempDir);
        }

    }

    @Test
    public void urlFile_AbsoluteNonExisting() throws Exception
    {
        final File tempDir = Util.createTempDir();
        try
        {
            FilePath target = new FilePath(tempDir);
            new FilePath(new File(getClass().getResource("dummy-xlt").toURI())).copyRecursiveTo(target);

            WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "urlFileAbsNonExisting");
            FilePath ws = rule.jenkins.getWorkspaceFor(j);
            FilePath acUrlsFile = ws.child("acUrls.txt");
            j.setDefinition(new CpsFlowDefinition("node {\nxlt xltTemplateDir: '" + target.getRemote() +
                                                  "', stepId: 'step1', agentControllerConfig: urlFile('" + acUrlsFile.getRemote() +
                                                  "') \n" + "}\n", true));
            WorkflowRun r = j.scheduleBuild2(0).waitForStart();

            rule.assertBuildStatus(Result.FAILURE, rule.waitForCompletion(r));
            rule.assertLogContains("Read agent controller URLs from file", r);
            rule.assertLogContains("Build failed: " + acUrlsFile.getRemote() + " (No such file or directory)", r);
        }
        finally
        {
            Util.deleteRecursive(tempDir);
        }

    }

    @Test
    public void urlFile_RelativeExisting() throws Exception
    {
        final File tempDir = Util.createTempDir();
        try
        {
            FilePath target = new FilePath(tempDir);
            new FilePath(new File(getClass().getResource("dummy-xlt").toURI())).copyRecursiveTo(target);

            WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "urlFileRel");
            FilePath ws = rule.jenkins.getWorkspaceFor(j);
            final String urls = "https://12.34.56.78 ; https://23.45.67.89";
            FilePath acUrlsFile = ws.child("config").createTextTempFile("acUrls", ".txt", urls);
            j.setDefinition(new CpsFlowDefinition("node {\nxlt xltTemplateDir: '" + target.getRemote() +
                                                  "', stepId: 'step1', agentControllerConfig: urlFile('" + acUrlsFile.getName() + "') \n" +
                                                  "}\n", true));
            WorkflowRun r = j.scheduleBuild2(0).waitForStart();

            rule.assertBuildStatus(Result.SUCCESS, rule.waitForCompletion(r));
            rule.assertLogContains("Read agent controller URLs from file", r);
            validateACUrls(urls, r);
        }
        finally
        {
            Util.deleteRecursive(tempDir);
        }

    }

    @Test
    public void urlFile_AbsoluteExisting() throws Exception
    {
        final File tempDir = Util.createTempDir();
        try
        {
            FilePath target = new FilePath(tempDir);
            new FilePath(new File(getClass().getResource("dummy-xlt").toURI())).copyRecursiveTo(target);

            WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "urlFileAbs");
            final String urls = "https://12.34.56.78 ; https://23.45.67.89";
            FilePath acUrlsFile = target.createTextTempFile("acUrls", ".txt", urls);
            j.setDefinition(new CpsFlowDefinition("node {\nxlt xltTemplateDir: '" + target.getRemote() +
                                                  "', stepId: 'step1', agentControllerConfig: urlFile('" + acUrlsFile.getRemote() +
                                                  "') \n" + "}\n", true));

            rule.jenkins.getWorkspaceFor(j).createTextTempFile("test", ".properties", "");

            WorkflowRun r = j.scheduleBuild2(0).waitForStart();

            rule.assertBuildStatus(Result.SUCCESS, rule.waitForCompletion(r));
            rule.assertLogContains("Read agent controller URLs from file", r);
            validateACUrls(urls, r);
        }
        finally
        {
            Util.deleteRecursive(tempDir);
        }

    }

    @Test
    public void mcFile_RelativeNonExisting() throws Exception
    {
        final File tempDir = Util.createTempDir();
        try
        {
            FilePath target = new FilePath(tempDir);
            new FilePath(new File(getClass().getResource("dummy-xlt").toURI())).copyRecursiveTo(target);

            WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "mcFileRelNonExisting");
            FilePath ws = rule.jenkins.getWorkspaceFor(j);
            ws.createTextTempFile("test", ".properties", "");

            FilePath mcPropsFile = target.createTextTempFile("mastercontroller", ".properties", "");
            j.setDefinition(new CpsFlowDefinition("node {\nxlt xltTemplateDir: '" + target.getRemote() +
                                                  "', stepId: 'step1', additionalMCPropertiesFile: '" + mcPropsFile.getName() + "' \n}\n",
                                                  true));

            WorkflowRun r = j.scheduleBuild2(0).waitForStart();

            rule.assertBuildStatus(Result.FAILURE, rule.waitForCompletion(r));
            rule.assertLogContains("Build failed: Additional master controller properties file does not exists.", r);

        }
        finally
        {
            Util.deleteRecursive(tempDir);
        }
    }

    @Test
    public void mcFile_AbsoluteNonExisting() throws Exception
    {
        final File tempDir = Util.createTempDir();
        try
        {
            FilePath target = new FilePath(tempDir);
            new FilePath(new File(getClass().getResource("dummy-xlt").toURI())).copyRecursiveTo(target);

            WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "mcFileAbsNonExisting");
            FilePath ws = rule.jenkins.getWorkspaceFor(j);
            ws.createTextTempFile("test", ".properties", "");

            FilePath mcPropsFile = ws.child("mastercontroller.properties");
            j.setDefinition(new CpsFlowDefinition("node {\nxlt xltTemplateDir: '" + target.getRemote() +
                                                  "', stepId: 'step1', additionalMCPropertiesFile: '" + mcPropsFile.getRemote() + "' \n}\n",
                                                  true));

            WorkflowRun r = j.scheduleBuild2(0).waitForStart();

            rule.assertBuildStatus(Result.FAILURE, rule.waitForCompletion(r));
            rule.assertLogContains("Build failed: Additional master controller properties file does not exists.", r);

        }
        finally
        {
            Util.deleteRecursive(tempDir);
        }
    }

    @Test
    public void mcFile_RelativeExisting() throws Exception
    {
        final File tempDir = Util.createTempDir();
        try
        {
            FilePath target = new FilePath(tempDir);
            new FilePath(new File(getClass().getResource("dummy-xlt").toURI())).copyRecursiveTo(target);

            WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "mcFileRel");
            FilePath ws = rule.jenkins.getWorkspaceFor(j);

            FilePath mcPropsFile = ws.child("config").createTextTempFile("mastercontroller", ".properties", "");
            j.setDefinition(new CpsFlowDefinition("node {\nxlt xltTemplateDir: '" + target.getRemote() +
                                                  "', stepId: 'step1', additionalMCPropertiesFile: '" + mcPropsFile.getName() + "' \n}\n",
                                                  true));

            WorkflowRun r = j.scheduleBuild2(0).waitForStart();

            rule.assertBuildStatus(Result.SUCCESS, rule.waitForCompletion(r));
            assertThat(JenkinsRule.getLog(r),
                       Matchers.stringContainsInOrder(Arrays.asList("mastercontroller.sh", "-pf " + mcPropsFile.getRemote())));

        }
        finally
        {
            Util.deleteRecursive(tempDir);
        }
    }

    @Test
    public void mcFile_AbsoluteExisting() throws Exception
    {
        final File tempDir = Util.createTempDir();
        try
        {
            FilePath target = new FilePath(tempDir);
            new FilePath(new File(getClass().getResource("dummy-xlt").toURI())).copyRecursiveTo(target);

            WorkflowJob j = rule.jenkins.createProject(WorkflowJob.class, "mcFileAbs");
            FilePath ws = rule.jenkins.getWorkspaceFor(j);

            ws.createTextTempFile("test", ".properties", "");

            FilePath mcPropsFile = target.createTextTempFile("mastercontroller", ".properties", "");
            j.setDefinition(new CpsFlowDefinition("node {\nxlt xltTemplateDir: '" + target.getRemote() +
                                                  "', stepId: 'step1', additionalMCPropertiesFile: '" + mcPropsFile.getRemote() + "' \n}\n",
                                                  true));

            WorkflowRun r = j.scheduleBuild2(0).waitForStart();

            rule.assertBuildStatus(Result.SUCCESS, rule.waitForCompletion(r));
            assertThat(JenkinsRule.getLog(r),
                       Matchers.stringContainsInOrder(Arrays.asList("mastercontroller.sh", "-pf " + mcPropsFile.getRemote())));

        }
        finally
        {
            Util.deleteRecursive(tempDir);
        }
    }

    private void validateACUrls(final String urls, final Run<?, ?> run) throws Exception
    {
        int no = 0;
        for (final String u : StringUtils.split(urls, " ;,"))
        {
            rule.assertLogContains(String.format("-Dcom.xceptance.xlt.mastercontroller.agentcontrollers.ac%03d.url=%s", ++no, u), run);
        }
    }

    private static <R extends Run<?, ?>> void validateParametersAction(R run, final String... strings)
    {
        final List<XltParametersAction> paramAxn = run.getActions(XltParametersAction.class);
        assertNotNull(paramAxn);
        assertEquals(strings.length, paramAxn.size());

        final String allSteps = StringUtils.join(strings, "_");
        for (final XltParametersAction axn : paramAxn)
        {
            for (final XltResult.ENVIRONMENT_KEYS k : XltResult.ENVIRONMENT_KEYS.values())
            {
                if (k != XltResult.ENVIRONMENT_KEYS.XLT_DIFFREPORT_URL)
                {
                    assertNotNull(axn.getParameter(k.name()));
                }
            }
            final String sid = StringUtils.substringAfter(axn.getUrlName(), "_");
            assertFalse(StringUtils.isEmpty(sid));
            assertTrue(allSteps.contains(sid));
        }
    }

    private static <R extends Run<?, ?>> void validateChartsAndParameterAction(R run, final String... strings)
    {
        final List<XltChartAction> chartAxns = run.getActions(XltChartAction.class);
        assertNotNull(chartAxns);
        assertEquals(strings.length, chartAxns.size());

        final String allSteps = StringUtils.join(strings, "_");
        for (final XltChartAction axn : chartAxns)
        {
            assertFalse(axn.getCharts().isEmpty());
            assertTrue(allSteps.contains(axn.getStepId()));
        }

        validateParametersAction(run, strings);

    }
}
