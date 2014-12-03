package com.xceptance.xlt.tools.jenkins;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

@Extension
public class XltRunListener extends RunListener<AbstractBuild<?, ?>>
{
    private LoadTestBuilder getLoadTestBuilder(AbstractBuild<?, ?> r)
    {
        AbstractProject<?, ?> project = r.getProject();
        LoadTestBuilder.XLTBuilderAction xltAction = project.getAction(LoadTestBuilder.XLTBuilderAction.class);
        if (xltAction != null)
        {
            return xltAction.getLoadTestBuilder();
        }
        return null;
    }

    @Override
    public void onDeleted(AbstractBuild<?, ?> r)
    {
        System.out.println("onDeleted");
        LoadTestBuilder builder = getLoadTestBuilder(r);
        if (builder != null)
        {
            builder.removeBuildFromCharts(r.getProject(), r);
        }
    }

    @Override
    public void onStarted(AbstractBuild<?, ?> r, TaskListener listener)
    {
        System.out.println("onStarted");
        LoadTestBuilder builder = getLoadTestBuilder(r);
        if (builder != null)
        {
            builder.initializeBuildParameter();
        }
    }

    @Override
    public void onCompleted(AbstractBuild<?, ?> r, TaskListener listener)
    {
        System.out.println("onCompleted");

        LoadTestBuilder builder = getLoadTestBuilder(r);
        if (builder != null)
        {
            builder.addBuildToCharts(r);
            builder.publishBuildParameters(r);
        }
    }
}
