package com.xceptance.xlt.tools.jenkins.config;

import java.util.List;

import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;

/**
 * Base class of all agent controller configuration variants.
 */
public abstract class AgentControllerConfig extends AbstractDescribableImpl<AgentControllerConfig> implements ExtensionPoint
{
    
    public abstract List<String> toCmdLineArgs();
}
