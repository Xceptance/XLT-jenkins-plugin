package com.xceptance.xlt.tools.jenkins;

import static com.xceptance.xlt.tools.jenkins.util.ValidationUtils.validateNumber;

import java.util.UUID;

import org.kohsuke.stapler.AncestorInPath;
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
import hudson.model.Job;
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
        return "Run a load test with XLT";
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
     */
    public FormValidation doCheckTestPropertiesFile(@QueryParameter String value)
    {
        return ConfigurationValidator.validateTestProperties(value);
    }

    public FormValidation doCheckStepId(@QueryParameter String value)
    {
        return ConfigurationValidator.validateStepId(value);
    }

    /**
     * Performs on-the-fly validation of the form field 'parsers'.
     */
    public FormValidation doCheckXltConfig(@QueryParameter String value)
    {
        return ConfigurationValidator.validateXltConfig(value);
    }

    public FormValidation doCheckPlotWidth(@QueryParameter String value)
    {
        return ConfigurationValidator.validatePlotWidth(value);

    }

    public FormValidation doCheckPlotHeight(@QueryParameter String value)
    {
        return doCheckPlotWidth(value);
    }

    public FormValidation doCheckTimeFormatPattern(@QueryParameter String value)
    {
        return ConfigurationValidator.validateTimeFormat(value);
    }

    public FormValidation doCheckXltTemplateDir(@QueryParameter String value)
    {
        return ConfigurationValidator.validateXltTemplateDir(value);
    }

    public FormValidation doCheckPathToTestSuite(@QueryParameter String value, @AncestorInPath Job<?, ?> project)
    {
        return ConfigurationValidator.validateTestSuitePath(value, project);
    }

    /**
     * Performs on-the-fly validation of the form field 'markCriticalConditionCount'.
     */
    public FormValidation doCheckMarkCriticalConditionCount(@QueryParameter String value)
    {
        return validateNumber(value, 0, null, Flags.IGNORE_BLANK_VALUE, Flags.IGNORE_MAX, Flags.IS_INTEGER);
    }

    /**
     * Performs on-the-fly validation of the form field 'markCriticalBuildCount'.
     */
    public FormValidation doCheckMarkCriticalBuildCount(@QueryParameter String value)
    {
        return validateNumber(value, 0, null, Flags.IGNORE_BLANK_VALUE, Flags.IGNORE_MAX, Flags.IS_INTEGER);
    }

    /**
     * Performs on-the-fly validation of the form field 'numberOfBuildsForTrendReport'.
     */
    public FormValidation doCheckNumberOfBuildsForTrendReport(@QueryParameter String value)
    {
        return validateNumber(value, 2, null, Flags.IGNORE_MAX, Flags.IGNORE_BLANK_VALUE, Flags.IS_INTEGER);
    }

    /**
     * Performs on-the-fly validation of the form field 'numberOfBuildsForSummaryReport'.
     */
    public FormValidation doCheckNumberOfBuildsForSummaryReport(@QueryParameter String value)
    {
        return validateNumber(value, 2, null, Flags.IGNORE_MAX, Flags.IGNORE_BLANK_VALUE, Flags.IS_INTEGER);
    }

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
