package com.xceptance.xlt.tools.jenkins.config.option;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.xceptance.xlt.tools.jenkins.util.ConfigurationValidator;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.FormValidation;

public class DiffReportOption extends AbstractOption
{
    private final String diffReportBaseline;

    private final String diffReportCriteriaFile;

    @DataBoundConstructor
    public DiffReportOption(final String diffReportBaseline, final String diffReportCriteriaFile)
    {
        this.diffReportBaseline = diffReportBaseline;
        this.diffReportCriteriaFile = diffReportCriteriaFile;
    }

    public String getDiffReportBaseline()
    {
        return diffReportBaseline;
    }

    public String getDiffReportCriteriaFile()
    {
        return diffReportCriteriaFile;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<AbstractOption>
    {
        public FormValidation doCheckDiffReportBaseline(@QueryParameter String value)
        {
            return ConfigurationValidator.validateDiffReportBaseline(value);
        }

        public FormValidation doCheckDiffReportCriteriaFile(@QueryParameter String value)
        {
            return ConfigurationValidator.validateDiffReportCriteriaFile(value);
        }
    }
}
