package com.xceptance.xlt.tools.jenkins.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.CheckForNull;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.FormValidation;

/**
 * Configuration to use agent controllers whose URLs are already known and given as list.
 */
public class UrlList extends AgentControllerConfig
{
    @CheckForNull
    private String urlList;

    /**
     * Constructor.
     * 
     * @param urlList
     *            one or more agent controller URLs separated by ' ', ',', ';', '|', '\r', '\n' or '\t'.
     */
    @DataBoundConstructor
    public UrlList(@CheckForNull final String urlList)
    {
        this.urlList = urlList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> toCmdLineArgs()
    {
        final List<String> args = new ArrayList<>();
        final List<String> urlList = parseList(getUrlList());
        if (urlList != null)
        {
            for (int i = 0; i < urlList.size(); i++)
            {
                args.add(String.format("-Dcom.xceptance.xlt.mastercontroller.agentcontrollers.ac%03d.url=%s", i + 1, urlList.get(i)));
            }
        }
        return args;
    }

    @CheckForNull
    public String getUrlList()
    {
        return urlList;
    }

    public void setUrlList(String list)
    {
        this.urlList = list;
    }

    public List<String> getItems()
    {
        return parseList(urlList);
    }

    private static List<String> parseList(final String urlList)
    {
        if (StringUtils.isNotBlank(urlList))
        {
            return Arrays.asList(StringUtils.split(urlList, "\r\n\t|,; "));
        }
        return null;
    }

    @Symbol("urlList")
    @Extension
    public static class DescriptorImpl extends Descriptor<AgentControllerConfig>
    {
        @Override
        public String getDisplayName()
        {
            return "List of Agent Controller URLs";
        }

        /**
         * Performs on-the-fly validation of the form field 'urlList'.
         */
        public FormValidation doCheckUrlList(@QueryParameter String value)
        {
            if (StringUtils.isBlank(value))
            {
                return FormValidation.validateRequired(value);
            }

            // create a check pattern
            String regex = "^https://([a-z\\d\\.-]+?)(:\\d+)?$";
            Pattern p = Pattern.compile(regex);

            // test each URL
            List<String> urls = parseList(value);
            if (urls != null)
            {
                for (String url : urls)
                {
                    Matcher matcher = p.matcher(url);
                    if (!matcher.matches())
                    {
                        return FormValidation.error("Invalid agent controller URL found: " + url);
                    }
                }
            }
            return FormValidation.ok();
        }
    }
}
