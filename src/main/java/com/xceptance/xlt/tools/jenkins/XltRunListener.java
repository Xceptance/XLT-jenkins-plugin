package com.xceptance.xlt.tools.jenkins;

import java.util.ArrayList;
import java.util.List;

//import com.xceptance.xlt.tools.jenkins.LoadTestBuilder.XLTBuilderAction;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

@Extension
public class XltRunListener extends RunListener<Run<?, ?>>
{
    @Override
    public void onDeleted(Run<?,?> r)
    {
    }


    @Override
    public void onCompleted(Run<?, ?> r, TaskListener listener)
    {
    }
}
