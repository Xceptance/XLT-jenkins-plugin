package com.xceptance.xlt.tools.jenkins;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.xceptance.xlt.tools.jenkins.util.Helper;

import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import jenkins.model.RunAction2;

public class XltParametersAction extends ParametersAction implements RunAction2
{
    private transient Run<?, ?> run;

    private final String stepId;

    public XltParametersAction(List<ParameterValue> parameters, final String stepId)
    {
        super(parameters);
        this.stepId = stepId;
    }

    @Override
    public String getDisplayName()
    {
        final StringBuilder sb = new StringBuilder("XLT Parameters");
        if (StringUtils.isNotBlank(stepId))
        {
            sb.append(" (").append(StringUtils.abbreviate(stepId, 12)).append(")");
        }
        return sb.toString();
    }

    @Override
    public String getUrlName()
    {
        final StringBuilder sb = new StringBuilder("xltParameters");
        if (StringUtils.isNotBlank(stepId))
        {
            sb.append('_').append(stepId);
        }

        return sb.toString();
    }

    @Override
    public String getIconFileName()
    {
        return Helper.getResourcePath("logo_24_24.png");
    }

    @Override
    public void onAttached(Run<?, ?> r)
    {
        run = r;
    }

    @Override
    public void onLoad(Run<?, ?> r)
    {
        run = r;
    }

    public Run<?, ?> getRun()
    {
        return run;
    }
}
