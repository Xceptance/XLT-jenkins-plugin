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

import java.io.IOException;

import javax.annotation.Nonnull;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Descriptor;
import hudson.util.FormValidation;

/**
 * Configuration to use agent controllers whose URLs are already known and contained in the given file.
 */
public class UrlFile extends UrlList
{
    @Nonnull
    private final String urlFile;

    /**
     * Constructor.
     * 
     * @param urlFile
     *            absolute or relative path to a text file containing the agent controller URLs
     */
    @DataBoundConstructor
    public UrlFile(@Nonnull final String urlFile)
    {
        super(null);
        this.urlFile = urlFile;
    }

    public String getUrlFile()
    {
        return urlFile;
    }

    public void parse(final FilePath basedir) throws IOException, InterruptedException
    {
        final String content = basedir.child(urlFile).readToString();
        setUrlList(content);
    }

    @Symbol("urlFile")
    @Extension
    public static class DescriptorImpl extends Descriptor<AgentControllerConfig>
    {
        @Override
        public String getDisplayName()
        {
            return "Read Agent Controller URLs from text file";
        }

        /**
         * Performs on-the-fly validation of the form field 'urlFile'.
         * 
         * @param value
         *            the input value
         * @return form validation object
         */
        public FormValidation doCheckUrlFile(@QueryParameter String value)
        {
            return FormValidation.validateRequired(value);
        }

    }
}
