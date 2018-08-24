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
