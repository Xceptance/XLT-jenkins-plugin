package com.xceptance.xlt.tools.jenkins;

import hudson.Extension;
import hudson.util.Secret;

import org.kohsuke.stapler.DataBoundConstructor;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;

public class AwsCredentials extends BaseStandardCredentials implements StandardUsernameCredentials
{
    private static final long serialVersionUID = 1L;

    private String username;

    private Secret accessKey;

    private Secret secretKey;

    @DataBoundConstructor
    public AwsCredentials(CredentialsScope scope, String id, String description, String username, Secret accessKey, Secret secretKey)
    {
        super(scope, id, description);
        this.username = username;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    public String getUsername()
    {
        return username;
    }

    public Secret getAccessKey()
    {
        return accessKey;
    }

    public Secret getSecretKey()
    {
        return secretKey;
    }

    @Extension
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor
    {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName()
        {
            return "XLT AWS Credentials";
        }
    }
}
