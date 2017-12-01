package com.xceptance.xlt.tools.jenkins.config;

import java.util.Arrays;
import java.util.List;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;


import hudson.Extension;
import hudson.model.Descriptor;

/**
 * Configuration to use the 'embedded' agent controller.
 */
public class Embedded extends AgentControllerConfig
{
    public static final AgentControllerConfig INSTANCE = new Embedded();

    @DataBoundConstructor
    public Embedded()
    {
        super();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> toCmdLineArgs()
    {
        // embedded and use any free port
        return Arrays.asList("-embedded", "-Dcom.xceptance.xlt.agentcontroller.port=0");
    }

    @Symbol("embedded")
    @Extension
    public static class DescriptorImpl extends Descriptor<AgentControllerConfig> 
    {
        @Override
        public String getDisplayName()
        {
            return "Embedded Agent Controller";
        }
    }
}
