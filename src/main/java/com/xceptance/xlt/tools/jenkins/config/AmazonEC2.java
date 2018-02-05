package com.xceptance.xlt.tools.jenkins.config;

import static com.xceptance.xlt.tools.jenkins.util.ValidationUtils.validateNumber;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.xceptance.xlt.tools.jenkins.AwsCredentials;
import com.xceptance.xlt.tools.jenkins.util.ValidationUtils.Flags;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

/**
 * Configuration to use on-demand Amazon EC2 instances that are started prior to test start and terminated once it has
 * completed.
 */
public class AmazonEC2 extends UrlList
{
    @Nonnull
    private final String region;

    @Nonnull
    private final String amiId;

    @Nonnull
    private final String ec2Type;

    @Nonnull
    private final String countMachines;

    @Nonnull
    private final String tagName;

    @CheckForNull
    private String awsCredentials;

    // TODO #2339: ec2_admin does not support security groups for now
    // private List<AWSSecurityGroup> securityGroups;

    @CheckForNull
    private String awsUserData;

    @DataBoundConstructor
    public AmazonEC2(@Nonnull String region, @Nonnull String amiId, @Nonnull String ec2Type, @Nonnull String countMachines,
                     @Nonnull String tagName)
    {
        super(null);
        this.region = region;
        this.amiId = amiId;
        this.ec2Type = ec2Type;
        this.countMachines = countMachines;
        this.tagName = tagName;
    }

    @DataBoundSetter
    public void setAwsCredentials(@CheckForNull String awsCredentials)
    {

        this.awsCredentials = StringUtils.isNotBlank(awsCredentials) ? awsCredentials : null;
    }

    @DataBoundSetter
    public void setAwsUserData(@CheckForNull String awsUserData)
    {
        this.awsUserData = StringUtils.isNotBlank(awsUserData) ? awsUserData : null;
    }

    // TODO #2339: ec2_admin does not support security groups for now
    // @DataBoundSetter
    // public void setSecurityGroups(final List<AWSSecurityGroup> securityGroups)
    // {
    // if(securityGroups != null && !securityGroups.isEmpty())
    // {
    // this.securityGroups = securityGroups;
    // }
    // }
    //
    // public List<AWSSecurityGroup> getSecurityGroups()
    // {
    // return this.securityGroups;
    // }

    @Nonnull
    public String getRegion()
    {
        return region;
    }

    @Nonnull
    public String getAmiId()
    {
        return amiId;
    }

    @Nonnull
    public String getEc2Type()
    {
        return ec2Type;
    }

    @Nonnull
    public String getCountMachines()
    {
        return countMachines;
    }

    @Nonnull
    public String getTagName()
    {
        return tagName;
    }

    @CheckForNull
    public String getAwsCredentials()
    {
        return awsCredentials;
    }

    @CheckForNull
    public String getAwsUserData()
    {
        return awsUserData;
    }

    private String getSecurityGroupParameter()
    {
        // TODO #2339: ec2_admin does not support security groups for now
        // if(securityGroups != null && !securityGroups.isEmpty())
        // {
        // final StringBuilder sb = new StringBuilder();
        // for(int i = 0; i<securityGroups.size(); i++)
        // {
        // if(i > 0)
        // {
        // sb.append(",");
        // }
        // sb.append(securityGroups.get(i).getID());
        // }
        // return sb.toString();
        // }
        return null;
    }

    public List<String> toEC2AdminArgs(final FilePath xltConfigDir) throws InterruptedException, IOException
    {
        final FilePath userDataFile = xltConfigDir.child("userdata.txt");
        final FilePath acUrlsFile = xltConfigDir.child("acUrls.properties");

        final List<String> args = new ArrayList<>();
        args.add("run");
        args.add(getRegion());
        args.add(getAmiId());
        args.add(getEc2Type());
        args.add(getCountMachines());
        args.add(getTagName());

        final String securityGroupsParameter = getSecurityGroupParameter();
        if (StringUtils.isNotBlank(securityGroupsParameter))
        {
            args.add("-s");
            args.add(securityGroupsParameter);
        }

        if (StringUtils.isNotBlank(getAwsUserData()))
        {
            userDataFile.write(getAwsUserData(), null);

            args.add("-uf");
            args.add(userDataFile.absolutize().getRemote());
        }

        args.add("-o");
        args.add(acUrlsFile.absolutize().getRemote());

        return args;
    }

    @Symbol("ec2")
    @Extension
    public static class DescriptorImpl extends Descriptor<AgentControllerConfig>
    {
        private static Map<String, String> FRIENDLY_REGION_NAMES;

        private static Map<String, String> INSTANCE_TYPE_MAP;

        static
        {
            FRIENDLY_REGION_NAMES = new TreeMap<String, String>();
            FRIENDLY_REGION_NAMES.put("ap-northeast-1", "Asia Pacific - Tokyo");
            FRIENDLY_REGION_NAMES.put("ap-northeast-2", "Asia Pacific - Seoul");
            FRIENDLY_REGION_NAMES.put("ap-south-1", "Asia Pacific - Mumbai");
            FRIENDLY_REGION_NAMES.put("ap-southeast-1", "Asia Pacific - Singapore");
            FRIENDLY_REGION_NAMES.put("ap-southeast-2", "Asia Pacific - Sydney");
            FRIENDLY_REGION_NAMES.put("eu-central-1", "EU - Frankfurt");
            FRIENDLY_REGION_NAMES.put("eu-west-1", "EU - Ireland");
            FRIENDLY_REGION_NAMES.put("sa-east-1", "South America - Sao Paulo");
            FRIENDLY_REGION_NAMES.put("us-east-1", "US East - North Virginia");
            FRIENDLY_REGION_NAMES.put("us-east-2", "US East - Ohio");
            FRIENDLY_REGION_NAMES.put("us-west-1", "US West - North California");
            FRIENDLY_REGION_NAMES.put("us-west-2", "US West - Oregon");

            INSTANCE_TYPE_MAP = new LinkedHashMap<>();
            for (final InstanceType iType : InstanceType.values())
            {
                INSTANCE_TYPE_MAP.put(iType.value, iType.description);
            }
        }

        @Override
        public String getDisplayName()
        {
            return "Use URLs of EC2 instances started on-demand";
        }

        /**
         * Returns all available regions of Amazons EC2 machines.
         * 
         * @return user-friendly region names, keyed by region name.
         */
        public Map<String, String> getAllRegions()
        {
            return FRIENDLY_REGION_NAMES;
        }

        /**
         * Returns all available instance types suitable for load tests.
         * 
         * @return descriptions of instance types, keyed by instance type
         */
        public Map<String, String> getAllTypes()
        {
            return INSTANCE_TYPE_MAP;
        }

        /**
         * Performs on-the-fly validation of the form field 'amiId'.
         * 
         * @param value
         *            the input value
         * @return form validation object
         */
        public FormValidation doCheckAmiId(@QueryParameter String value)
        {
            return FormValidation.validateRequired(value);
        }

        /**
         * Performs on-the-fly validation of the form field 'countMachines'.
         * 
         * @param value
         *            the input value
         * @return form validation object
         */
        public FormValidation doCheckCountMachines(@QueryParameter String value)
        {
            return validateNumber(value, 1, null, Flags.IGNORE_MAX, Flags.IS_INTEGER);
        }

        /**
         * Performs on-the-fly validation of the form field 'tagName'.
         * 
         * @param value
         *            the input value
         * @return form validation object
         */
        public FormValidation doCheckTagName(@QueryParameter String value)
        {
            return FormValidation.validateRequired(value);
        }

        public ListBoxModel doFillAwsCredentialsItems(@AncestorInPath Item context, @QueryParameter String value)
        {
            if (context == null || !context.hasPermission(Item.CONFIGURE))
            {
                return new StandardListBoxModel().includeCurrentValue(value);
            }
            return new StandardListBoxModel().includeEmptyValue().includeMatchingAs(ACL.SYSTEM, context, AwsCredentials.class, null,
                                                                                    CredentialsMatchers.always())
                                             .includeCurrentValue(value);
        }

        /**
         * Fills the region select box.
         * 
         * @return A {@link ListBoxModel} with the available regions.
         */
        public ListBoxModel doFillRegionItems()
        {
            ListBoxModel items = new ListBoxModel();
            for (Map.Entry<String, String> e : getAllRegions().entrySet())
            {
                items.add(e.getKey() + " - " + e.getValue(), e.getKey());
            }
            return items;
        }

        /**
         * Fills the machine type select box.
         * 
         * @return A {@link ListBoxModel} with the available machine types.
         */
        public ListBoxModel doFillEc2TypeItems()
        {
            ListBoxModel items = new ListBoxModel();
            for (Map.Entry<String, String> e : getAllTypes().entrySet())
            {
                items.add(e.getKey() + " - " + e.getValue(), e.getKey());
            }
            return items;
        }

        /**
         * Supported EC2 Instance Types.
         */
        private static enum InstanceType
        {
         M3Large("m3.large", " 2 cores,   6.5 compute units,   7.50 GB RAM, 1x  32 GB SSD, $0.13...0.16/h"),
         M3Xlarge("m3.xlarge", " 4 cores,  13.0 compute units,  15.00 GB RAM, 2x  40 GB SSD, $0.27...0.32/h"),
         M32xlarge("m3.2xlarge", " 8 cores,  26.0 compute units,  30.00 GB RAM, 2x  80 GB SSD, $0.53...0.63/h"),
         M4Large("m4.large", " 2 cores,   6.5 compute units,   8.00 GB RAM,      EBS-only, $0.12...0.14/h"),
         M4Xlarge("m4.xlarge", " 4 cores,  13.0 compute units,  16.00 GB RAM,      EBS-only, $0.24...0.29/h"),
         M42xlarge("m4.2xlarge", " 8 cores,  26.0 compute units,  32.00 GB RAM,      EBS-only, $0.48...0.57/h"),
         M44xlarge("m4.4xlarge", "16 cores,  53.5 compute units,  64.00 GB RAM,      EBS-only, $0.96...1.14/h"),
         C3Large("c3.large", " 2 cores,   7.0 compute units,   3.75 GB RAM, 2x  16 GB SSD, $0.11...0.13/h"),
         C3Xlarge("c3.xlarge", " 4 cores,  14.0 compute units,   7.50 GB RAM, 2x  40 GB SSD, $0.21...0.26/h"),
         C32xlarge("c3.2xlarge", " 8 cores,  28.0 compute units,  15.00 GB RAM, 2x  80 GB SSD, $0.42...0.52/h"),
         C34xlarge("c3.4xlarge", "16 cores,  55.0 compute units,  30.00 GB RAM, 2x 160 GB SSD, $0.84...1.03/h"),
         C38xlarge("c3.8xlarge", "32 cores, 108.0 compute units,  60.00 GB RAM, 2x 320 GB SSD, $1.68...2.06/h"),
         C4Large("c4.large", " 2 cores,   8.0 compute units,   3.75 GB RAM,      EBS-only, $0.11...0.13/h"),
         C4Xlarge("c4.xlarge", " 4 cores,  16.0 compute units,   7.50 GB RAM,      EBS-only, $0.21...0.27/h"),
         C42xlarge("c4.2xlarge", " 8 cores,  31.0 compute units,  15.00 GB RAM,      EBS-only, $0.42...0.53/h"),
         C44xlarge("c4.4xlarge", "16 cores,  62.0 compute units,  30.00 GB RAM,      EBS-only, $0.84...1.07/h"),
         R3Large("r3.large", " 2 cores,   6.5 compute units,  15.25 GB RAM,      EBS-only, $0.17...0.20/h"),
         R3Xlarge("r3.xlarge", " 4 cores,  13.0 compute units,  30.50 GB RAM,      EBS-only, $0.33...0.40/h"),
         R32xlarge("r3.2xlarge", " 8 cores,  26.0 compute units,  61.00 GB RAM,      EBS-only, $0.67...0.80/h"),
         R34xlarge("r3.4xlarge", "16 cores,  52.0 compute units, 122.00 GB RAM,      EBS-only, $1.33...1.60/h"),
         R38xlarge("r3.8xlarge", "32 cores, 104.0 compute units, 244.00 GB RAM,      EBS-only, $2.66...3.20/h");

            private final String value;

            private final String description;

            private InstanceType(String value, String description)
            {
                this.value = value;
                this.description = description;
            }

            @Override
            public String toString()
            {
                return this.value;
            }

        }
    }
}
