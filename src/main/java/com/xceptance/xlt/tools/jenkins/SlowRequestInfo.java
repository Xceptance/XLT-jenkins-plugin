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
package com.xceptance.xlt.tools.jenkins;

import java.io.Serializable;

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;

/**
 * Simple data object holding basic infos about a slow request.
 */
public class SlowRequestInfo implements Serializable
{
    /** The serialVersionUID. */
    private static final long serialVersionUID = -5211214833674013358L;

    private String url;

    private String runtime;

    public SlowRequestInfo(String url, String runtime)
    {
        this.url = url;
        this.runtime = runtime;
    }

    @Whitelisted
    public String getUrl()
    {
        return url;
    }

    @Whitelisted
    public String getRuntime()
    {
        return runtime;
    }
}
