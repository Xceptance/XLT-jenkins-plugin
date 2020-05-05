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
 * Simple data object holding basic infos about a (failed) test case.
 */
public class TestCaseInfo implements Comparable<TestCaseInfo>, Serializable
{
    /** The serialVersionUID. */
    private static final long serialVersionUID = -5174008040474052005L;

    private String testCaseName;

    private String actionName;

    private String message;

    public TestCaseInfo(String testCaseName, String actionName, String message)
    {
        this.testCaseName = testCaseName;
        this.actionName = actionName;
        this.message = message;
    }

    @Whitelisted
    public String getTestCaseName()
    {
        return testCaseName;
    }

    @Whitelisted
    public String getActionName()
    {
        return actionName;
    }

    @Whitelisted
    public String getMessage()
    {
        return message;
    }

    @Override
    public int compareTo(TestCaseInfo info)
    {
        return testCaseName.compareTo(info.testCaseName);
    }
}
