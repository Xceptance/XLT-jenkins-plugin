package com.xceptance.xlt.tools.jenkins;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.kohsuke.stapler.DataBoundConstructor;


public class AgentControllerConfig
{
    /**
     * Supported EC2 Instance Types.
     */
    private enum InstanceType
    {
        M3Large("m3.large"),
        M3Xlarge("m3.xlarge"),
        M32xlarge("m3.2xlarge"),
        M4Large("m4.large"),
        M4Xlarge("m4.xlarge"),
        M42xlarge("m4.2xlarge"),
        M44xlarge("m4.4xlarge"),
        C1Xlarge("c1.xlarge"),
        C3Large("c3.large"),
        C3Xlarge("c3.xlarge"),
        C32xlarge("c3.2xlarge"),
        C34xlarge("c3.4xlarge"),
        C38xlarge("c3.8xlarge"),
        C4Large("c4.large"),
        C4Xlarge("c4.xlarge"),
        C42xlarge("c4.2xlarge"),
        C44xlarge("c4.4xlarge"),
        R3Large("r3.large"),
        R3Xlarge("r3.xlarge"),
        R32xlarge("r3.2xlarge"),
        R34xlarge("r3.4xlarge"),
        R38xlarge("r3.8xlarge");

        private final String value;

        private InstanceType(String value)
        {
            this.value = value;
        }

        @Override
        public String toString()
        {
            return this.value;
        }

    }
    public enum TYPE
    {
        embedded, list, file, ec2
    };

    /**
     * The user-friendly region names, keyed by region name.
     */
    private static final Map<String, String> FRIENDLY_REGION_NAMES = new TreeMap<String, String>();
    static
    {
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
    };

    /**
     * The descriptions of the instance types suitable for load tests.
     */
    private static final String[] INSTANCE_TYPE_DESCRIPTIONS =
        {
            " 2 cores,   7.0 compute units,   3.75 GB RAM, 2x  16 GB SSD, $0.11...0.13/h", // c3.large
            " 4 cores,  14.0 compute units,   7.50 GB RAM, 2x  40 GB SSD, $0.21...0.26/h", // c3.xlarge
            " 8 cores,  28.0 compute units,  15.00 GB RAM, 2x  80 GB SSD, $0.42...0.52/h", // c3.2xlarge
            "16 cores,  55.0 compute units,  30.00 GB RAM, 2x 160 GB SSD, $0.84...1.03/h", // c3.4xlarge
            "32 cores, 108.0 compute units,  60.00 GB RAM, 2x 320 GB SSD, $1.68...2.06/h", // c3.8xlarge
            " 2 cores,   8.0 compute units,   3.75 GB RAM,      EBS-only, $0.11...0.13/h", // c4.large
            " 4 cores,  16.0 compute units,   7.50 GB RAM,      EBS-only, $0.21...0.27/h", // c4.xlarge
            " 8 cores,  31.0 compute units,  15.00 GB RAM,      EBS-only, $0.42...0.53/h", // c4.2xlarge
            "16 cores,  62.0 compute units,  30.00 GB RAM,      EBS-only, $0.84...1.07/h", // c4.4xlarge
            " 2 cores,   6.5 compute units,   7.50 GB RAM, 1x  32 GB SSD, $0.13...0.16/h", // m3.large
            " 4 cores,  13.0 compute units,  15.00 GB RAM, 2x  40 GB SSD, $0.27...0.32/h", // m3.xlarge
            " 8 cores,  26.0 compute units,  30.00 GB RAM, 2x  80 GB SSD, $0.53...0.63/h", // m3.2xlarge
            " 2 cores,   6.5 compute units,   8.00 GB RAM,      EBS-only, $0.12...0.14/h", // m4.large
            " 4 cores,  13.0 compute units,  16.00 GB RAM,      EBS-only, $0.24...0.29/h", // m4.xlarge
            " 8 cores,  26.0 compute units,  32.00 GB RAM,      EBS-only, $0.48...0.57/h", // m4.2xlarge
            "16 cores,  53.5 compute units,  64.00 GB RAM,      EBS-only, $0.96...1.14/h", // m4.4xlarge
            " 2 cores,   6.5 compute units,  15.25 GB RAM,      EBS-only, $0.17...0.20/h", // r3.large
            " 4 cores,  13.0 compute units,  30.50 GB RAM,      EBS-only, $0.33...0.40/h", // r3.xlarge
            " 8 cores,  26.0 compute units,  61.00 GB RAM,      EBS-only, $0.67...0.80/h", // r3.2xlarge
            "16 cores,  52.0 compute units, 122.00 GB RAM,      EBS-only, $1.33...1.60/h", // r3.4xlarge
            "32 cores, 104.0 compute units, 244.00 GB RAM,      EBS-only, $2.66...3.20/h", // r3.8xlarge
        };

    /**
     * The instance types suitable for load tests.
     */
    private static final String[] INSTANCE_TYPES =
        {
            InstanceType.C3Large.toString(), InstanceType.C3Xlarge.toString(), InstanceType.C32xlarge.toString(),
            InstanceType.C34xlarge.toString(), InstanceType.C38xlarge.toString(), InstanceType.C4Large.toString(),
            InstanceType.C4Xlarge.toString(), InstanceType.C42xlarge.toString(), InstanceType.C44xlarge.toString(),
            InstanceType.M3Large.toString(), InstanceType.M3Xlarge.toString(), InstanceType.M32xlarge.toString(),
            InstanceType.M4Large.toString(), InstanceType.M4Xlarge.toString(), InstanceType.M42xlarge.toString(),
            InstanceType.M44xlarge.toString(), InstanceType.R3Large.toString(), InstanceType.R3Xlarge.toString(),
            InstanceType.R32xlarge.toString(), InstanceType.R34xlarge.toString(), InstanceType.R38xlarge.toString()
        };

    /**
     * The descriptions of the instance types suitable for load tests, keyed by instance type.
     */
    private static final Map<String, String> INSTANCE_TYPE_MAP = new LinkedHashMap<String, String>();
    static
    {
        for (int i = 0; i < INSTANCE_TYPES.length; i++)
        {
            INSTANCE_TYPE_MAP.put(INSTANCE_TYPES[i], INSTANCE_TYPE_DESCRIPTIONS[i]);
        }
    }

    public final String type;

    public final String urlFile;

    public final String urlList;

    private final String region;

    private final String amiId;

    private final String ec2Type;

    private final String countMachines;

    private final String tagName;

    private String awsCredentials;

    private List<AWSSecurityGroup> securityGroups;

    private String awsUserData;

    public AgentControllerConfig()
    {
        this(TYPE.embedded.toString(), null, null, null, null, null, null, null, null, null, null);
    }

    @DataBoundConstructor
    public AgentControllerConfig(String value, String urlList, String urlFile, String region, String amiId, String ec2Type,
                                 String countMachines, String tagName, String awsCredentials, List<AWSSecurityGroup> securityGroups,
                                 String awsUserData)
    {
        this.type = value;
        this.urlList = urlList;
        this.urlFile = urlFile;
        this.region = region;
        this.amiId = amiId;
        this.ec2Type = ec2Type;
        this.countMachines = countMachines;
        this.tagName = tagName;
        this.awsCredentials = awsCredentials;
        this.securityGroups = securityGroups != null ? securityGroups : new ArrayList<AWSSecurityGroup>();
        this.awsUserData = awsUserData;
    }

    public String getRegion()
    {
        return this.region;
    }

    public String getAmiId()
    {
        return this.amiId;
    }

    public String getEc2Type()
    {
        return this.ec2Type;
    }

    public String getCountMachines()
    {
        return this.countMachines;
    }

    public String getTagName()
    {
        return this.tagName;
    }

    public String getAwsCredentials()
    {
        return awsCredentials;
    }

    public List<AWSSecurityGroup> getSecurityGroups()
    {
        return securityGroups;
    }

    public String getAwsUserData()
    {
        return awsUserData;
    }

    /**
     * Returns all available regions of Amazons EC2 machines.
     * 
     * @return user-friendly region names, keyed by region name.
     */
    public static Map<String, String> getAllRegions()
    {
        return FRIENDLY_REGION_NAMES;
    }

    /**
     * Returns all available instance types suitable for load tests.
     * 
     * @return descriptions of instance types, keyed by instance type
     */
    public static Map<String, String> getAllTypes()
    {
        return INSTANCE_TYPE_MAP;
    }
}
