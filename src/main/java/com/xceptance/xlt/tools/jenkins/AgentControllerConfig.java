package com.xceptance.xlt.tools.jenkins;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

import com.amazonaws.services.ec2.model.InstanceType;

public class AgentControllerConfig
{
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
        FRIENDLY_REGION_NAMES.put("ap-southeast-1", "Asia Pacific - Singapore");
        FRIENDLY_REGION_NAMES.put("ap-southeast-2", "Asia Pacific - Sydney");
        FRIENDLY_REGION_NAMES.put("eu-central-1", "EU - Frankfurt");
        FRIENDLY_REGION_NAMES.put("eu-west-1", "EU - Ireland");
        FRIENDLY_REGION_NAMES.put("sa-east-1", "South America - Sao Paulo");
        FRIENDLY_REGION_NAMES.put("us-east-1", "US East - North Virginia");
        FRIENDLY_REGION_NAMES.put("us-west-1", "US West - North California");
        FRIENDLY_REGION_NAMES.put("us-west-2", "US West - Oregon");
    };

    /**
     * The default AMI IDs, keyed by region name.
     */
    private static final Map<String, String> DEFAULT_AMI_IDS = new TreeMap<String, String>();
    static
    {
        DEFAULT_AMI_IDS.put("eu-west-1", "ami-a0d20cd7");
        DEFAULT_AMI_IDS.put("us-east-1", "ami-b26ab4da");
        DEFAULT_AMI_IDS.put("us-west-1", "ami-75fcf030");
        DEFAULT_AMI_IDS.put("us-west-2", "ami-5f6d296f");
        DEFAULT_AMI_IDS.put("ap-southeast-2", "ami-27aecf1d");
    }

    /**
     * The descriptions of the instance types suitable for load tests, keyed by instance type.
     */
    private static final Map<String, String> INSTANCE_TYPES = new LinkedHashMap<String, String>();
    static
    {
        INSTANCE_TYPES.put(InstanceType.C3Large.toString(), " 2 cores,   7.0 compute units,  3.75 GB RAM, 64 bit, $0.11...0.14/h");
        INSTANCE_TYPES.put(InstanceType.M3Large.toString(), " 2 cores,   6.5 compute units,  7.50 GB RAM, 64 bit, $0.14...0.21/h");
        INSTANCE_TYPES.put(InstanceType.C3Xlarge.toString(), " 4 cores,  14.0 compute units,  7.50 GB RAM, 64 bit, $0.21...0.27/h");
        INSTANCE_TYPES.put(InstanceType.M3Xlarge.toString(), " 4 cores,  13.0 compute units, 15.00 GB RAM, 64 bit, $0.28...0.41/h");
        INSTANCE_TYPES.put(InstanceType.C1Xlarge.toString(), " 8 cores,  20.0 compute units,  7.00 GB RAM, 64 bit, $0.52...0.66/h");
        INSTANCE_TYPES.put(InstanceType.C32xlarge.toString(), " 8 cores,  28.0 compute units, 15.00 GB RAM, 64 bit, $0.42...0.53/h");
        INSTANCE_TYPES.put(InstanceType.C34xlarge.toString(), "16 cores,  55.0 compute units, 30.00 GB RAM, 64 bit, $0.84...1.06/h");
        INSTANCE_TYPES.put(InstanceType.C38xlarge.toString(), "32 cores, 108.0 compute units, 60.00 GB RAM, 64 bit, $1.68...2.12/h");
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

    @Exported
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
     * Returns a default AMI in the given region that includes XLT.
     * 
     * @param region
     * @return XLT AMI ID
     */
    public static String getAmiIdByRegion(String region)
    {
        return DEFAULT_AMI_IDS.get(region);
    }

    /**
     * Returns all available instance types suitable for load tests.
     * 
     * @return descriptions of instance types, keyed by instance type
     */
    public static Map<String, String> getAllTypes()
    {
        return INSTANCE_TYPES;
    }
}
