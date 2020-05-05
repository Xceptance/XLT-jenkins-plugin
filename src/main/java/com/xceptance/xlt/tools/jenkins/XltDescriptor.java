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
package com.xceptance.xlt.tools.jenkins;

import static com.xceptance.xlt.tools.jenkins.util.ValidationUtils.validateNumber;

import java.util.UUID;

import org.kohsuke.stapler.QueryParameter;

import com.xceptance.xlt.tools.jenkins.config.AgentControllerConfig;
import com.xceptance.xlt.tools.jenkins.config.Embedded;
import com.xceptance.xlt.tools.jenkins.util.ConfigurationValidator;
import com.xceptance.xlt.tools.jenkins.util.PluginDefaults;
import com.xceptance.xlt.tools.jenkins.util.ValidationUtils.Flags;

import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AbstractProject;
import hudson.model.Items;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

@Extension
public class XltDescriptor extends BuildStepDescriptor<Builder>
{

    /**
     * In order to load the persisted global configuration, you have to call load() in the constructor.
     */
    public XltDescriptor()
    {
        super(LoadTestBuilder.class);

        load();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public boolean isApplicable(Class<? extends AbstractProject> aClass)
    {
        // Indicates that this builder can be used with all kinds of project types
        return true;
    }

    /**
     * This human readable name is used in the configuration screen.
     */
    @Override
    public String getDisplayName()
    {
        return "Perform a Load Test with XLT";
    }

    public String getDefaultXltConfig()
    {
        return PluginDefaults.getXltConfig();
    }

    public int getDefaultPlotWidth()
    {
        return PluginDefaults.PLOT_WIDTH;
    }

    public int getDefaultPlotHeight()
    {
        return PluginDefaults.PLOT_HEIGHT;
    }

    public String getDefaultPlotTitle()
    {
        return PluginDefaults.PLOT_TITLE;
    }

    public boolean getDefaultIsPlotVertical()
    {
        return false;
    }

    public boolean getDefaultCreateTrendReport()
    {
        return false;
    }

    public boolean getDefaultCreateSummaryReport()
    {
        return false;
    }

    public boolean getDefaultArchiveResults()
    {
        return true;
    }

    public int getDefaultNumberOfBuildsForTrendReport()
    {
        return PluginDefaults.HISTORYSIZE_TRENDREPORT;
    }

    public int getDefaultNumberOfBuildsForSummaryReport()
    {
        return PluginDefaults.HISTORYSIZE_SUMMARYREPORT;
    }

    public int getDefaultInitialResponseTimeout()
    {
        return PluginDefaults.INITIAL_RESPONSE_TIMEOUT;
    }

    public String getDefaultStepId()
    {
        return UUID.randomUUID().toString();
    }

    public AgentControllerConfig getDefaultAgentControllerConfig()
    {
        return Embedded.INSTANCE;
    }

    /**
     * Performs on-the-fly validation of the form field 'testPropertiesFile'.
     * 
     * @param value
     *            the input value
     * @return form validation object
     */
    public FormValidation doCheckTestPropertiesFile(@QueryParameter String value)
    {
        return ConfigurationValidator.validateTestProperties(value);
    }

    /**
     * Performs on-the-fly validation of the form field 'additionalMCPropertiesFile'.
     * 
     * @param value
     *            the input value
     * @return form validation object
     */
    public FormValidation doCheckAdditionalMCPropertiesFile(@QueryParameter String value)
    {
        return ConfigurationValidator.validateMCProperties(value);
    }

    /**
     * Performs on-the-fly validation of the form field 'stepId'.
     * 
     * @param value
     *            the input value
     * @return form validation object
     */
    public FormValidation doCheckStepId(@QueryParameter String value)
    {
        return ConfigurationValidator.validateStepId(value);
    }

    /**
     * Performs on-the-fly validation of the form field 'xltConfig'.
     * 
     * @param value
     *            the input value
     * @return form validation object
     */
    public FormValidation doCheckXltConfig(@QueryParameter String value)
    {
        return ConfigurationValidator.validateXltConfig(value);
    }

    /**
     * Performs on-the-fly validation of the form field 'plotWidth'.
     * 
     * @param value
     *            the input value
     * @return form validation object
     */
    public FormValidation doCheckPlotWidth(@QueryParameter String value)
    {
        return ConfigurationValidator.validatePlotWidth(value);
    }

    /**
     * Performs on-the-fly validation of the form field 'plotHeight'.
     * 
     * @param value
     *            the input value
     * @return form validation object
     */
    public FormValidation doCheckPlotHeight(@QueryParameter String value)
    {
        return doCheckPlotWidth(value);
    }

    /**
     * Performs on-the-fly validation of the form field 'timeFormatPattern'.
     * 
     * @param value
     *            the input value
     * @return form validation object
     */
    public FormValidation doCheckTimeFormatPattern(@QueryParameter String value)
    {
        return ConfigurationValidator.validateTimeFormat(value);
    }

    /**
     * Performs on-the-fly validation of the form field 'xltTemplateDir'.
     * 
     * @param value
     *            the input value
     * @return form validation object
     */
    public FormValidation doCheckXltTemplateDir(@QueryParameter String value)
    {
        return ConfigurationValidator.validateXltTemplateDir(value);
    }

    /**
     * Performs on-the-fly validation of the form field 'pathToTestSuite'.
     * 
     * @param value
     *            the input value
     * @return form validation object
     */
    public FormValidation doCheckPathToTestSuite(@QueryParameter String value)
    {
        return ConfigurationValidator.validateTestSuitePath(value);
    }

    /**
     * Performs on-the-fly validation of the form field 'markCriticalConditionCount'.
     * 
     * @param value
     *            the input value
     * @return form validation object
     */
    public FormValidation doCheckMarkCriticalConditionCount(@QueryParameter String value)
    {
        return validateNumber(value, 0, null, Flags.IGNORE_BLANK_VALUE, Flags.IGNORE_MAX, Flags.IS_INTEGER);
    }

    /**
     * Performs on-the-fly validation of the form field 'markCriticalBuildCount'.
     * 
     * @param value
     *            the input value
     * @return form validation object
     */
    public FormValidation doCheckMarkCriticalBuildCount(@QueryParameter String value)
    {
        return validateNumber(value, 0, null, Flags.IGNORE_BLANK_VALUE, Flags.IGNORE_MAX, Flags.IS_INTEGER);
    }

    /**
     * Performs on-the-fly validation of the form field 'numberOfBuildsForTrendReport'.
     * 
     * @param value
     *            the input value
     * @return form validation object
     */
    public FormValidation doCheckNumberOfBuildsForTrendReport(@QueryParameter String value)
    {
        return validateNumber(value, 2, null, Flags.IGNORE_MAX, Flags.IGNORE_BLANK_VALUE, Flags.IS_INTEGER);
    }

    /**
     * Performs on-the-fly validation of the form field 'numberOfBuildsForSummaryReport'.
     * 
     * @param value
     *            the input value
     * @return form validation object
     */
    public FormValidation doCheckNumberOfBuildsForSummaryReport(@QueryParameter String value)
    {
        return validateNumber(value, 2, null, Flags.IGNORE_MAX, Flags.IGNORE_BLANK_VALUE, Flags.IS_INTEGER);
    }

    /**
     * Performs on-the-fly validation of the form field 'initialResponseTimeout'.
     * 
     * @param value
     *            the input value
     * @return form validation object
     */
    public FormValidation doCheckInitialResponseTimeout(@QueryParameter String value)
    {
        return validateNumber(value, 0, null, Flags.IGNORE_BLANK_VALUE, Flags.IGNORE_MAX, Flags.IS_INTEGER);
    }

    @Initializer(before = InitMilestone.PLUGINS_STARTED)
    public static void addAliases()
    {
        Items.XSTREAM2.addDefaultImplementation(Embedded.class, AgentControllerConfig.class);
    }
}
