package com.xceptance.xlt.tools.jenkins;

import java.util.ArrayList;
import java.util.List;

import com.xceptance.xlt.tools.jenkins.LoadTestBuilder.XLTBuilderAction;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

@Extension
public class XltRunListener extends RunListener<AbstractBuild<?, ?>>
{
    private List<LoadTestBuilder> getLoadTestBuilder(AbstractBuild<?, ?> r)
    {
        AbstractProject<?, ?> project = r.getProject();
        List<XLTBuilderAction> xltActions = project.getActions(LoadTestBuilder.XLTBuilderAction.class);
        List<LoadTestBuilder> builders = new ArrayList<LoadTestBuilder>();
        for (XLTBuilderAction eachAction : xltActions)
        {
            builders.add(eachAction.getLoadTestBuilder());
        }
        return builders;
    }

    @Override
    public void onDeleted(AbstractBuild<?, ?> r)
    {
        List<LoadTestBuilder> builders = getLoadTestBuilder(r);
        for (LoadTestBuilder eachBuilder : builders)
        {
            eachBuilder.removeBuildFromCharts(r.getProject(), r);
        }
    }

    @Override
    public void onStarted(AbstractBuild<?, ?> r, TaskListener listener)
    {
        List<LoadTestBuilder> builders = getLoadTestBuilder(r);
        for (LoadTestBuilder eachBuilder : builders)
        {
            eachBuilder.initializeBuildParameter();
        }
    }

    @Override
    public void onCompleted(AbstractBuild<?, ?> r, TaskListener listener)
    {
        List<LoadTestBuilder> builders = getLoadTestBuilder(r);
        for (LoadTestBuilder eachBuilder : builders)
        {
            eachBuilder.addBuildToCharts(r);
        }
    }
}
