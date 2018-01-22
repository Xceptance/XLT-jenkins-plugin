package com.xceptance.xlt.tools.jenkins.pipeline;

import java.util.HashMap;
import java.util.Map;

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;

import com.xceptance.xlt.tools.jenkins.XltTask.ENVIRONMENT_KEYS;

public class LoadTestResult
{
    private final Map<ENVIRONMENT_KEYS, Object> params;

    public LoadTestResult(final Map<ENVIRONMENT_KEYS, Object> parameters)
    {
        params = new HashMap<>(parameters);
    }

    @Whitelisted
    public boolean getRunFailed()
    {
        final Object o = params.get(ENVIRONMENT_KEYS.XLT_RUN_FAILED);
        if (o != null && o instanceof Boolean)
        {
            return ((Boolean) o).booleanValue();
        }
        return false;
    }

    @Whitelisted
    public boolean getConditionFailed()
    {
        final Object o = params.get(ENVIRONMENT_KEYS.XLT_CONDITION_FAILED);
        if (o != null && o instanceof Boolean)
        {
            return ((Boolean) o).booleanValue();
        }
        return false;
    }

    @Whitelisted
    public boolean getConditionError()
    {
        final Object o = params.get(ENVIRONMENT_KEYS.XLT_CONDITION_ERROR);
        if (o != null && o instanceof Boolean)
        {
            return ((Boolean) o).booleanValue();
        }
        return false;
    }

    @Whitelisted
    public boolean getConditionCritical()
    {
        final Object o = params.get(ENVIRONMENT_KEYS.XLT_CONDITION_CRITICAL);
        if (o != null && o instanceof Boolean)
        {
            return ((Boolean) o).booleanValue();
        }
        return false;
    }

    @Whitelisted
    public String getConditionMessage()
    {
        final Object o = params.get(ENVIRONMENT_KEYS.XLT_CONDITION_MESSAGE);
        return o != null ? o.toString() : null;
    }

    @Whitelisted
    public String getReportUrl()
    {
        final Object o = params.get(ENVIRONMENT_KEYS.XLT_REPORT_URL);
        return o != null ? o.toString() : null;
    }
}
