package com.xceptance.xlt.tools.jenkins.pipeline;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;

import com.xceptance.xlt.tools.jenkins.XltTask;

import hudson.AbortException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;

class LoadTestStepExecution extends SynchronousNonBlockingStepExecution<LoadTestResult>
{
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private transient final LoadTestStep step;

    protected LoadTestStepExecution(@Nonnull final LoadTestStep step, StepContext context)
    {
        super(context);
        this.step = step;
    }

    @Override
    protected LoadTestResult run() throws Exception
    {
        final FilePath ws = getContext().get(FilePath.class);
        final Run<?, ?> run = getContext().get(Run.class);
        final TaskListener listener = getContext().get(TaskListener.class);
        final Launcher launcher = getContext().get(Launcher.class);

        if (ws == null)
        {
            throw new Exception("Missing workspace");
        }
        if (run == null)
        {
            throw new Exception("Missing build");
        }
        if (listener == null)
        {
            throw new Exception("Missing listener");
        }
        if (launcher == null)
        {
            throw new Exception("Missing launcher");
        }

        if (StringUtils.isBlank(step.getStepId()))
        {
            throw new AbortException("Parameter 'stepId' must not be blank");
        }

        if (StringUtils.isBlank(step.getXltTemplateDir()))
        {
            throw new AbortException("Parameter 'xltTemplateDir' must not be blank");
        }

        final XltTask task = new XltTask(step);
        return task.perform(run, ws, listener, launcher);
    }

}
