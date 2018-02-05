package com.xceptance.xlt.tools.jenkins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;

import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;

public class XltResult
{
    enum ENVIRONMENT_KEYS
    {
     XLT_RUN_FAILED,
     XLT_CONDITION_CRITICAL,
     XLT_CONDITION_FAILED,
     XLT_CONDITION_ERROR,
     XLT_CONDITION_MESSAGE,
     XLT_REPORT_URL,
     XLT_DIFFREPORT_URL
    }

    private final Map<ENVIRONMENT_KEYS, Object> params;

    private List<CriterionResult> failedCriteria;

    private List<TestCaseInfo> failedTests;

    private List<SlowRequestInfo> slowestRequests;

    public XltResult()
    {
        params = new TreeMap<>();

        setRunFailed(false);
        setConditionFailed(false);
        setConditionError(false);
        setConditionCritical(false);
        setConditionMessage("");
        setReportUrl(null);
    }

    public boolean getRunFailed()
    {
        final Object o = params.get(ENVIRONMENT_KEYS.XLT_RUN_FAILED);
        if (o != null && o instanceof Boolean)
        {
            return ((Boolean) o).booleanValue();
        }
        return false;
    }

    void setRunFailed(final boolean failed)
    {
        params.put(ENVIRONMENT_KEYS.XLT_RUN_FAILED, Boolean.valueOf(failed));
    }

    public boolean getConditionFailed()
    {
        final Object o = params.get(ENVIRONMENT_KEYS.XLT_CONDITION_FAILED);
        if (o != null && o instanceof Boolean)
        {
            return ((Boolean) o).booleanValue();
        }
        return false;
    }

    void setConditionFailed(final boolean failed)
    {
        params.put(ENVIRONMENT_KEYS.XLT_CONDITION_FAILED, Boolean.valueOf(failed));
    }

    public boolean getConditionError()
    {
        final Object o = params.get(ENVIRONMENT_KEYS.XLT_CONDITION_ERROR);
        if (o != null && o instanceof Boolean)
        {
            return ((Boolean) o).booleanValue();
        }
        return false;
    }

    void setConditionError(final boolean failed)
    {
        params.put(ENVIRONMENT_KEYS.XLT_CONDITION_ERROR, Boolean.valueOf(failed));
    }

    public boolean getConditionCritical()
    {
        final Object o = params.get(ENVIRONMENT_KEYS.XLT_CONDITION_CRITICAL);
        if (o != null && o instanceof Boolean)
        {
            return ((Boolean) o).booleanValue();
        }
        return false;
    }

    void setConditionCritical(final boolean critical)
    {
        params.put(ENVIRONMENT_KEYS.XLT_CONDITION_CRITICAL, Boolean.valueOf(critical));
    }

    public String getConditionMessage()
    {
        final Object o = params.get(ENVIRONMENT_KEYS.XLT_CONDITION_MESSAGE);
        return o != null ? o.toString() : null;
    }

    void setConditionMessage(final String msg)
    {
        params.put(ENVIRONMENT_KEYS.XLT_CONDITION_MESSAGE, StringUtils.defaultString(msg));
    }

    public String getReportUrl()
    {
        final Object o = params.get(ENVIRONMENT_KEYS.XLT_REPORT_URL);
        return o != null ? o.toString() : null;
    }

    void setReportUrl(final String reportUrl)
    {
        params.put(ENVIRONMENT_KEYS.XLT_REPORT_URL, StringUtils.defaultString(reportUrl));
    }

    public String getDiffReportUrl()
    {
        final Object o = params.get(ENVIRONMENT_KEYS.XLT_DIFFREPORT_URL);
        return o != null ? o.toString() : null;
    }

    void setDiffReportUrl(final String diffReportUrl)
    {
        params.put(ENVIRONMENT_KEYS.XLT_DIFFREPORT_URL, StringUtils.defaultString(diffReportUrl));
    }

    public List<CriterionResult> getCriteriaFailures()
    {
        final List<CriterionResult> list = new ArrayList<>();
        if (failedCriteria != null)
        {
            for (final CriterionResult r : failedCriteria)
            {
                if (r.getType() == CriterionResult.Type.FAILED)
                {
                    list.add(r);
                }
            }
        }
        return list;
    }

    public List<CriterionResult> getCriteriaErrors()
    {
        final List<CriterionResult> list = new ArrayList<>();
        if (failedCriteria != null)
        {
            for (final CriterionResult r : failedCriteria)
            {
                if (r.getType() == CriterionResult.Type.ERROR)
                {
                    list.add(r);
                }
            }
        }
        return list;
    }

    public List<TestCaseInfo> getTestFailures()
    {
        return failedTests;
    }

    public List<SlowRequestInfo> getSlowestRequests()
    {
        return slowestRequests;
    }

    List<ParameterValue> getParameterList()
    {
        final ArrayList<ParameterValue> list = new ArrayList<>();
        for (Map.Entry<ENVIRONMENT_KEYS, Object> entry : params.entrySet())
        {
            final Object val = entry.getValue();
            list.add(new StringParameterValue(entry.getKey().name(), val == null ? "null" : val.toString()));
        }
        return list;
    }

    void setParameter(final ENVIRONMENT_KEYS key, final Object value)
    {
        params.put(key, value);
    }

    void setTestFailures(final List<TestCaseInfo> failures)
    {
        this.failedTests = emptyOrUnmodifiable(failures);
    }

    void setFailedCriteria(final List<CriterionResult> failures)
    {
        this.failedCriteria = emptyOrUnmodifiable(failures);
    }

    void setSlowestRequests(final List<SlowRequestInfo> slowest)
    {
        this.slowestRequests = emptyOrUnmodifiable(slowest);
    }

    private static <T> List<T> emptyOrUnmodifiable(final List<T> list)
    {
        return list == null ? Collections.<T>emptyList() : Collections.unmodifiableList(list);
    }
}
