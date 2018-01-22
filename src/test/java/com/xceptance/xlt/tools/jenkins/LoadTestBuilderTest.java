package com.xceptance.xlt.tools.jenkins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import org.apache.commons.lang.StringUtils;
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

public class LoadTestBuilderTest
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

    private static <R extends Run<?, ?>> void validateParametersAction(R run, final String... strings)
    {
        final List<XltParametersAction> paramAxn = run.getActions(XltParametersAction.class);
        assertNotNull(paramAxn);
        assertEquals(strings.length, paramAxn.size());

        final String allSteps = StringUtils.join(strings, "_");
        for (final XltParametersAction axn : paramAxn)
        {
            for (final XltTask.ENVIRONMENT_KEYS k : XltTask.ENVIRONMENT_KEYS.values())
            {
                assertNotNull(axn.getParameter(k.name()));
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

        final List<XltParametersAction> paramAxn = run.getActions(XltParametersAction.class);
        assertNotNull(paramAxn);
        assertEquals(strings.length, paramAxn.size());

        for (final XltParametersAction axn : paramAxn)
        {
            for (final XltTask.ENVIRONMENT_KEYS k : XltTask.ENVIRONMENT_KEYS.values())
            {
                assertNotNull(axn.getParameter(k.name()));
            }
            final String sid = StringUtils.substringAfter(axn.getUrlName(), "_");
            assertFalse(StringUtils.isEmpty(sid));
            assertTrue(allSteps.contains(sid));
        }

    }
}
