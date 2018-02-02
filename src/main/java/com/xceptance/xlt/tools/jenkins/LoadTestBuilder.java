package com.xceptance.xlt.tools.jenkins;

import java.io.IOException;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.xceptance.xlt.tools.jenkins.config.AgentControllerConfig;
import com.xceptance.xlt.tools.jenkins.config.option.DiffReportOption;
import com.xceptance.xlt.tools.jenkins.config.option.MarkCriticalOption;
import com.xceptance.xlt.tools.jenkins.config.option.SummaryReportOption;
import com.xceptance.xlt.tools.jenkins.config.option.TrendReportOption;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import jenkins.tasks.SimpleBuildStep;

/**
 * Readout the configuration of XLT in Jenkins, perform load testing and plot build results on project page.
 * 
 * @author Randolph Straub
 */
public class LoadTestBuilder extends Builder implements SimpleBuildStep, LoadTestConfiguration
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
    private TrendReportOption trendReportOption;

    @CheckForNull
    private SummaryReportOption summaryReportOption;

    @CheckForNull
    private MarkCriticalOption markCriticalOption;

    @CheckForNull
    private DiffReportOption diffReportOption;

    /*
     * Backward compatibility
     */
    @Deprecated
    private transient String builderID;

    @Deprecated
    private transient boolean isPlotVertical;

    @Deprecated
    private transient boolean createTrendReport;

    @Deprecated
    private transient int numberOfBuildsForTrendReport;

    @Deprecated
    private transient boolean createSummaryReport;

    @Deprecated
    private transient int numberOfBuildsForSummaryReport;

    @Deprecated
    private transient boolean markCriticalEnabled;

    @Deprecated
    private transient int markCriticalConditionCount;

    @Deprecated
    private transient int markCriticalBuildCount;

    @DataBoundConstructor
    public LoadTestBuilder(@Nonnull final String stepId, @Nonnull final String xltTemplateDir)
    {
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

        // misc.
        this.stepId = stepId;
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
    public TrendReportOption getTrendReportOption()
    {
        return trendReportOption;
    }

    @DataBoundSetter
    public void setTrendReportOption(@CheckForNull final TrendReportOption opt)
    {
        this.trendReportOption = opt;
    }

    @CheckForNull
    public SummaryReportOption getSummaryReportOption()
    {
        return summaryReportOption;
    }

    @DataBoundSetter
    public void setSummaryReportOption(@CheckForNull final SummaryReportOption opt)
    {
        this.summaryReportOption = opt;
    }

    @CheckForNull
    public MarkCriticalOption getMarkCriticalOption()
    {
        return markCriticalOption;
    }

    @DataBoundSetter
    public void setMarkCriticalOption(@CheckForNull final MarkCriticalOption opt)
    {
        this.markCriticalOption = opt;
    }

    public boolean getMarkCriticalEnabled()
    {
        return markCriticalOption != null;
    }

    public int getMarkCriticalConditionCount()
    {
        if (markCriticalOption == null)
        {
            return 0;
        }

        return markCriticalOption.getMarkCriticalConditionCount();
    }

    public int getMarkCriticalBuildCount()
    {
        if (markCriticalOption == null)
        {
            return 0;
        }

        final int mcCondCount = markCriticalOption.getMarkCriticalConditionCount();
        final int mcBuildCount = markCriticalOption.getMarkCriticalBuildCount();
        return mcCondCount > 0 ? Math.max(mcCondCount, mcBuildCount) : mcBuildCount;
    }

    @CheckForNull
    public DiffReportOption getDiffReportOption()
    {
        return diffReportOption;
    }

    @DataBoundSetter
    public void setDiffReportOption(@CheckForNull final DiffReportOption opt)
    {
        this.diffReportOption = opt;
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
        return trendReportOption != null;
    }

    public boolean getCreateSummaryReport()
    {
        return summaryReportOption != null;
    }

    public int getNumberOfBuildsForTrendReport()
    {
        final int nbBuilds = trendReportOption != null ? trendReportOption.getNumberOfBuildsForTrendReport() : -1;
        return nbBuilds > 0 ? nbBuilds : getDescriptor().getDefaultNumberOfBuildsForTrendReport();
    }

    public int getNumberOfBuildsForSummaryReport()
    {
        final int nbBuilds = summaryReportOption != null ? summaryReportOption.getNumberOfBuildsForSummaryReport() : -1;
        return nbBuilds > 0 ? nbBuilds : getDescriptor().getDefaultNumberOfBuildsForSummaryReport();
    }

    @Override
    public boolean getCreateDiffReport()
    {
        return diffReportOption != null;
    }

    @Override
    public String getDiffReportBaseline()
    {
        return diffReportOption != null ? StringUtils.defaultString(diffReportOption.getBaseline()) : null;
    }

    @Override
    public String getDiffReportCriteriaFile()
    {
        return diffReportOption != null ? diffReportOption.getCriteriaFile() : null;
    }

    @Override
    public XltDescriptor getDescriptor()
    {
        return (XltDescriptor) super.getDescriptor();
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
        throws InterruptedException, IOException
    {
        final XltTask task = new XltTask(this);
        task.perform(run, workspace, listener, launcher);

    }

    protected Object readResolve()
    {
        if (builderID != null)
        {
            stepId = builderID;
        }
        if (isPlotVertical)
        {
            plotVertical = true;
        }
        if (createTrendReport)
        {
            trendReportOption = new TrendReportOption(numberOfBuildsForTrendReport);
        }
        if (createSummaryReport)
        {
            summaryReportOption = new SummaryReportOption(numberOfBuildsForSummaryReport);
        }
        if (markCriticalEnabled)
        {
            markCriticalOption = new MarkCriticalOption(markCriticalConditionCount, markCriticalBuildCount);
        }

        return this;
    }
}
