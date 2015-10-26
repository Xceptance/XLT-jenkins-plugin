package com.xceptance.xlt.tools.jenkins;

/**
 * Simple data object holding basic infos about a (failed) test case.
 */
public class TestCaseInfo implements Comparable<TestCaseInfo>
{
    private String testCaseName;

    private String actionName;

    private String message;

    public TestCaseInfo(String testCaseName, String actionName, String message)
    {
        this.testCaseName = testCaseName;
        this.actionName = actionName;
        this.message = message;
    }

    public String getTestCaseName()
    {
        return testCaseName;
    }

    public String getActionName()
    {
        return actionName;
    }

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
