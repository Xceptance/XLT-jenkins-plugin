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

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;

/**
 * Base class of all agent controller configuration variants.
 */
public abstract class AgentControllerConfig extends AbstractDescribableImpl<AgentControllerConfig> implements ExtensionPoint
{
    @Deprecated
    private transient String type;

    @Deprecated
    private transient String urlFile;

    @Deprecated
    private transient String urlList;

    @Deprecated
    private transient String region;

    @Deprecated
    private transient String amiId;

    @Deprecated
    private transient String ec2Type;

    @Deprecated
    private transient String countMachines;

    @Deprecated
    private transient String tagName;

    @Deprecated
    private transient String awsCredentials;

    @Deprecated
    private transient String awsUserData;

    public Object readResolve()
    {
        if(StringUtils.isNotBlank(type))
        {
            if("embedded".equals(type))
            {
                return new Embedded();
            }
            if("list".equals(type))
            {
                return new UrlList(urlList);
            }
            if("file".equals(type))
            {
                return new UrlFile(urlFile);
            }
            if("ec2".equals(type))
            {
                final AmazonEC2 ec2 = new AmazonEC2(region, amiId, ec2Type, countMachines, tagName);
                ec2.setAwsCredentials(awsCredentials);
                ec2.setAwsUserData(awsUserData);
                return ec2;
            }
        }
        return this;
    }
    
    public abstract List<String> toCmdLineArgs();
}
