/*
 * Copyright (c) 2014-2020 Xceptance Software Technologies GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
