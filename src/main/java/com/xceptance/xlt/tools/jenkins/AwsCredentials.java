package com.xceptance.xlt.tools.jenkins;

import jenkins.model.Jenkins;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ModelObject;
import hudson.model.User;
import hudson.util.FormValidation;
import hudson.util.Secret;

import javax.annotation.CheckForNull;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
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
    public static class DescriptorImpl extends CredentialsDescriptor
    {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName()
        {
            return "XLT AWS Credentials";
        }

        public final FormValidation doCheckId(@QueryParameter String value, @AncestorInPath ModelObject context)
        {
            if (value.isEmpty())
            {
                return FormValidation.ok();
            }
            if (!value.matches("[a-zA-Z0-9_.-]+"))
            { // anything else considered kosher?
                return FormValidation.error("Unacceptable characters");
            }
            FormValidation problem = checkForDuplicates(value, context, context);
            if (problem != null)
            {
                return problem;
            }
            if (!(context instanceof User))
            {
                User me = User.current();
                if (me != null)
                {
                    problem = checkForDuplicates(value, context, me);
                    if (problem != null)
                    {
                        return problem;
                    }
                }
            }
            if (!(context instanceof Jenkins))
            {
                // CredentialsProvider.lookupStores(User) does not return SystemCredentialsProvider.
                Jenkins j = Jenkins.getInstance();
                if (j != null)
                {
                    problem = checkForDuplicates(value, context, j);
                    if (problem != null)
                    {
                        return problem;
                    }
                }
            }
            return FormValidation.ok();
        }

        private static @CheckForNull FormValidation checkForDuplicates(String value, ModelObject context, ModelObject object)
        {
            for (CredentialsStore store : CredentialsProvider.lookupStores(object))
            {
                if (!store.hasPermission(CredentialsProvider.VIEW))
                {
                    continue;
                }
                ModelObject storeContext = store.getContext();
                for (Domain domain : store.getDomains())
                {
                    if (CredentialsMatchers.firstOrNull(store.getCredentials(domain), CredentialsMatchers.withId(value)) != null)
                    {
                        if (storeContext == context)
                        {
                            return FormValidation.error("This ID is already in use");
                        }
                        else
                        {
                            return FormValidation.warning("The ID ‘%s’ is already in use in %s", value,
                                                          storeContext instanceof Item ? ((Item) storeContext).getFullDisplayName()
                                                                                      : storeContext.getDisplayName());
                        }
                    }
                }
            }
            return null;
        }

    }
}
