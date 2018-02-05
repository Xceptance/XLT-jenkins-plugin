package com.xceptance.xlt.tools.jenkins.pipeline;

import static com.xceptance.xlt.tools.jenkins.util.ValidationUtils.validateNumber;

import java.util.Set;
import java.util.UUID;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.google.common.collect.ImmutableSet;
import com.xceptance.xlt.tools.jenkins.LoadTestConfiguration;
import com.xceptance.xlt.tools.jenkins.config.AgentControllerConfig;
import com.xceptance.xlt.tools.jenkins.config.Embedded;
import com.xceptance.xlt.tools.jenkins.config.option.DiffReportOption;
import com.xceptance.xlt.tools.jenkins.config.option.MarkCriticalOption;
import com.xceptance.xlt.tools.jenkins.config.option.SummaryReportOption;
import com.xceptance.xlt.tools.jenkins.config.option.TrendReportOption;
import com.xceptance.xlt.tools.jenkins.util.ConfigurationValidator;
import com.xceptance.xlt.tools.jenkins.util.PluginDefaults;
import com.xceptance.xlt.tools.jenkins.util.ValidationUtils.Flags;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;

public class LoadTestStep extends Step implements LoadTestConfiguration
{
    @CheckForNull
    private String testPropertiesFile;

    @Nonnull
    private AgentControllerConfig agentControllerConfig;

    @Nonnull
    private Integer initialResponseTimeout;

    @Nonnull
    private String xltConfig;

    @CheckForNull
    private final String xltTemplateDir;

    @CheckForNull
    private String pathToTestSuite;

    @CheckForNull
    private String timeFormatPattern;

    private boolean showBuildNumber;

    private int plotWidth;

    private int plotHeight;

    @Nonnull
    private String plotTitle;

    @Nonnull
    private String stepId;

    private boolean plotVertical;

    @CheckForNull
    private TrendReportOption trendReport;

    @CheckForNull
    private SummaryReportOption summaryReport;

    @CheckForNull
    private MarkCriticalOption markCritical;

    @CheckForNull
    private DiffReportOption diffReport;

    @DataBoundConstructor
    public LoadTestStep(@Nonnull final String stepId, @Nonnull final String xltTemplateDir)
    {
        this.stepId = stepId;
        this.xltTemplateDir = xltTemplateDir;

        // load test configuration
        this.agentControllerConfig = getDescriptor().getDefaultAgentControllerConfig();

        this.initialResponseTimeout = getDescriptor().getDefaultInitialResponseTimeout();

        // plot/value configuration
        this.xltConfig = getDescriptor().getDefaultXltConfig();

        // advanced plot configuration
        this.plotWidth = getDescriptor().getDefaultPlotWidth();
        this.plotHeight = getDescriptor().getDefaultPlotHeight();
        this.plotTitle = getDescriptor().getDefaultPlotTitle();

    }

    @Nonnull
    public AgentControllerConfig getAgentControllerConfig()
    {
        return agentControllerConfig;
    }

    @DataBoundSetter
    public void setAgentControllerConfig(@Nonnull final AgentControllerConfig acConfig)
    {
        if (acConfig != null)
        {
            this.agentControllerConfig = acConfig;
        }
    }

    @Nonnull
    public Integer getInitialResponseTimeout()
    {
        return initialResponseTimeout;
    }

    @DataBoundSetter
    public void setInitialResponseTimeout(@Nonnull final Integer timeout)
    {
        initialResponseTimeout = timeout;
    }

    @CheckForNull
    public TrendReportOption getTrendReport()
    {
        return trendReport;
    }

    @DataBoundSetter
    public void setTrendReport(@CheckForNull final TrendReportOption opt)
    {
        this.trendReport = opt;
    }

    @CheckForNull
    public SummaryReportOption getSummaryReport()
    {
        return summaryReport;
    }

    @DataBoundSetter
    public void setSummaryReport(@CheckForNull final SummaryReportOption opt)
    {
        this.summaryReport = opt;
    }

    @CheckForNull
    public MarkCriticalOption getMarkCritical()
    {
        return markCritical;
    }

    @DataBoundSetter
    public void setMarkCritical(@CheckForNull final MarkCriticalOption opt)
    {
        this.markCritical = opt;
    }

    public boolean getMarkCriticalEnabled()
    {
        return markCritical != null;
    }

    public int getMarkCriticalConditionCount()
    {
        if (markCritical == null)
        {
            return 0;
        }

        return markCritical.getMarkCriticalConditionCount();
    }

    public int getMarkCriticalBuildCount()
    {
        if (markCritical == null)
        {
            return 0;
        }

        final int mcCondCount = markCritical.getMarkCriticalConditionCount();
        final int mcBuildCount = markCritical.getMarkCriticalBuildCount();
        return mcCondCount > 0 ? Math.max(mcCondCount, mcBuildCount) : mcBuildCount;
    }

    @CheckForNull
    public String getPathToTestSuite()
    {
        return pathToTestSuite;
    }

    @DataBoundSetter
    public void setPathToTestSuite(@CheckForNull final String path)
    {
        this.pathToTestSuite = StringUtils.isNotBlank(path) ? path : null;
    }

    @CheckForNull
    public String getTestPropertiesFile()
    {
        return testPropertiesFile;
    }

    @DataBoundSetter
    public void setTestPropertiesFile(@CheckForNull final String propertyFilePath)
    {
        this.testPropertiesFile = StringUtils.isNotBlank(propertyFilePath) ? propertyFilePath : null;
    }

    @Nonnull
    public String getXltConfig()
    {
        return xltConfig;
    }

    @DataBoundSetter
    public void setXltConfig(@Nonnull final String config)
    {
        if (StringUtils.isNotBlank(config))
        {
            this.xltConfig = config;
        }
    }

    @Nonnull
    public String getXltTemplateDir()
    {
        return xltTemplateDir;
    }

    @CheckForNull
    public String getTimeFormatPattern()
    {
        return timeFormatPattern;
    }

    @DataBoundSetter
    public void setTimeFormatPattern(@CheckForNull final String timeFormatPattern)
    {
        this.timeFormatPattern = Util.fixEmptyAndTrim(timeFormatPattern);
    }

    public boolean isShowBuildNumber()
    {
        return showBuildNumber;
    }

    @DataBoundSetter
    public void setShowBuildNumber(final boolean showBuildNumber)
    {
        this.showBuildNumber = showBuildNumber;
    }

    public int getPlotWidth()
    {
        return plotWidth;
    }

    @DataBoundSetter
    public void setPlotWidth(final int plotWidth)
    {
        if (plotWidth > 0)
        {
            this.plotWidth = plotWidth;
        }
    }

    public int getPlotHeight()
    {
        return plotHeight;
    }

    @DataBoundSetter
    public void setPlotHeight(final int plotHeight)
    {
        if (plotHeight > 0)
        {
            this.plotHeight = plotHeight;
        }
    }

    @Nonnull
    public String getPlotTitle()
    {
        return plotTitle;
    }

    @DataBoundSetter
    public void setPlotTitle(@Nonnull final String title)
    {
        if (StringUtils.isNotBlank(title))
        {
            this.plotTitle = title;
        }
    }

    @Nonnull
    public String getStepId()
    {
        return stepId;
    }

    public boolean isPlotVertical()
    {
        return plotVertical;
    }

    @DataBoundSetter
    public void setPlotVertical(boolean isPlotVertical)
    {
        this.plotVertical = isPlotVertical;
    }

    public boolean getCreateTrendReport()
    {
        return trendReport != null;
    }

    public boolean getCreateSummaryReport()
    {
        return summaryReport != null;
    }

    public int getNumberOfBuildsForTrendReport()
    {
        final int nbBuilds = trendReport != null ? trendReport.getNumberOfBuildsForTrendReport() : -1;
        return nbBuilds > 0 ? nbBuilds : getDescriptor().getDefaultNumberOfBuildsForTrendReport();
    }

    public int getNumberOfBuildsForSummaryReport()
    {
        final int nbBuilds = summaryReport != null ? summaryReport.getNumberOfBuildsForSummaryReport() : -1;
        return nbBuilds > 0 ? nbBuilds : getDescriptor().getDefaultNumberOfBuildsForSummaryReport();
    }

    @CheckForNull
    public DiffReportOption getDiffReport()
    {
        return diffReport;
    }

    @DataBoundSetter
    public void setDiffReport(@CheckForNull final DiffReportOption diffReportOption)
    {
        this.diffReport = diffReportOption;
    }

    @Override
    public boolean getCreateDiffReport()
    {
        return diffReport != null;
    }

    @Override
    public String getDiffReportBaseline()
    {
        return diffReport != null ? StringUtils.defaultString(diffReport.getBaseline()) : null;
    }

    @Override
    public String getDiffReportCriteriaFile()
    {
        return diffReport != null ? diffReport.getCriteriaFile() : null;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception
    {
        return new LoadTestStepExecution(this, context);
    }

    @Override
    public DescriptorImpl getDescriptor()
    {
        return (DescriptorImpl) super.getDescriptor();
    }

    // @OptionalExtension(requirePlugins = {"pipeline-stage-step","workflow-cps","workflow-job"})

    @Extension
    public static class DescriptorImpl extends StepDescriptor
    {

        @Override
        public Set<? extends Class<?>> getRequiredContext()
        {
            return ImmutableSet.of(FilePath.class, Run.class, TaskListener.class, Launcher.class);
        }

        @Override
        public String getFunctionName()
        {
            return "xlt";
        }

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
    }
}
