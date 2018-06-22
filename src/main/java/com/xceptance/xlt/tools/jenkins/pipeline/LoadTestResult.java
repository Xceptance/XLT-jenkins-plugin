package com.xceptance.xlt.tools.jenkins.pipeline;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;

import com.xceptance.xlt.tools.jenkins.SlowRequestInfo;
import com.xceptance.xlt.tools.jenkins.TestCaseInfo;
import com.xceptance.xlt.tools.jenkins.XltResult;

/**
 * The (read-only view on the) result of executing the custom pipeline step {@code xlt}.
 */
public class LoadTestResult implements Serializable
{
    /** The serialVersionUID. */
    private static final long serialVersionUID = 615594859843167094L;
    
    /** The wrapped execution result. */
    private final XltResult _result;

    public LoadTestResult(final XltResult result)
    {
        _result = result;
    }

    @Whitelisted
    public boolean getRunFailed()
    {
        return _result.getRunFailed();
    }

    @Whitelisted
    public boolean getConditionFailed()
    {
        return _result.getConditionFailed();
    }

    @Whitelisted
    public boolean getConditionError()
    {
        return _result.getConditionError();
    }

    @Whitelisted
    public boolean getConditionCritical()
    {
        return _result.getConditionFailed();
    }

    @Whitelisted
    public String getConditionMessage()
    {
        return _result.getConditionMessage();
    }

    @Whitelisted
    public String getReportUrl()
    {
        return _result.getReportUrl();
    }

    @Whitelisted
    public String getDiffReportUrl()
    {
        return _result.getDiffReportUrl();
    }

    @Whitelisted
    public List<CriterionResult> getCriteriaFailures()
    {
        return transform(_result.getCriteriaFailures());
    }

    @Whitelisted
    public List<CriterionResult> getCriteriaErrors()
    {
        return transform(_result.getCriteriaErrors());
    }

    @Whitelisted
    public List<TestCaseInfo> getTestFailures()
    {
        return _result.getTestFailures();
    }

    @Whitelisted
    public List<SlowRequestInfo> getSlowestRequests()
    {
        return _result.getSlowestRequests();
    }

    private List<CriterionResult> transform(final List<com.xceptance.xlt.tools.jenkins.CriterionResult> criteria)
    {
        final ArrayList<CriterionResult> list = new ArrayList<>();
        for (final com.xceptance.xlt.tools.jenkins.CriterionResult cr : criteria)
        {
            list.add(new CriterionResult(cr.getCriterionID(), StringUtils.defaultString(cr.getXPath()) + cr.getCondition(), cr.getMessage()));
        }
        return list;
    }

    public static class CriterionResult
    {
        private final String _id;

        private final String _condition;

        private final String _message;

        private CriterionResult(final String id, final String condition, final String message)
        {
            _id = id;
            _condition = condition;
            _message = message;
        }

        @Whitelisted
        public String getId()
        {
            return _id;
        }

        @Whitelisted
        public String getCondition()
        {
            return _condition;
        }

        @Whitelisted
        public String getMessage()
        {
            return _message;
        }
    }
}
