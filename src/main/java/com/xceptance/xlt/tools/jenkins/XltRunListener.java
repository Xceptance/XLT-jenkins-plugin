package com.xceptance.xlt.tools.jenkins;

import java.util.ArrayList;
import java.util.List;

import com.xceptance.xlt.tools.jenkins.LoadTestBuilder.XLTBuilderAction;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

@Extension
public class XltRunListener extends RunListener<Run<?, ?>>
{
    private List<LoadTestBuilder> getLoadTestBuilder(Run<?, ?> r)
    {

        List<XLTBuilderAction> xltActions =  r.getParent().getActions(LoadTestBuilder.XLTBuilderAction.class);
        List<LoadTestBuilder> builders = new ArrayList<LoadTestBuilder>();
        for (XLTBuilderAction eachAction : xltActions)
        {
            builders.add(eachAction.getLoadTestBuilder());
        }
        return builders;
    }

    
    
    @Override
    public void onDeleted(Run<?,?> r)
    {
        List<LoadTestBuilder> builders = getLoadTestBuilder(r);
        for (LoadTestBuilder eachBuilder : builders)
        {
            eachBuilder.removeBuildFromCharts(r);
        }
    }


    @Override
    public void onCompleted(Run<?, ?> r, TaskListener listener)
    {
        List<LoadTestBuilder> builders = getLoadTestBuilder(r);
        for (LoadTestBuilder eachBuilder : builders)
        {
            eachBuilder.addBuildToCharts(r);
        }
    }
}
