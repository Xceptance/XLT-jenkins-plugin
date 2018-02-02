package com.xceptance.xlt.tools.jenkins.config.option;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.xceptance.xlt.tools.jenkins.util.ConfigurationValidator;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.FormValidation;

public class DiffReportOption extends AbstractOption
{
    private final String baseline;

    private final String criteriaFile;

    @DataBoundConstructor
    public DiffReportOption(final String baseline, final String criteriaFile)
    {
        this.baseline = baseline;
        this.criteriaFile = criteriaFile;
    }

    public String getBaseline()
    {
        return baseline;
    }

    public String getCriteriaFile()
    {
        return criteriaFile;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<AbstractOption>
    {
        public FormValidation doCheckBaseline(@QueryParameter String value)
        {
            return ConfigurationValidator.validateDiffReportBaseline(value);
        }

        public FormValidation doCheckCriteriaFile(@QueryParameter String value)
        {
            return ConfigurationValidator.validateDiffReportCriteriaFile(value);
        }
    }
}
