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
