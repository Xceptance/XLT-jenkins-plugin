package com.xceptance.xlt.tools.jenkins;

import hudson.model.ParameterValue;
import hudson.model.ParametersAction;

import java.util.Collections;
import java.util.List;

public class XltParametersAction extends ParametersAction
{
    private final List<ParameterValue> parameters;

    public XltParametersAction(List<ParameterValue> parameters)
    {
        super(parameters);
        this.parameters = parameters;
    }

    @Override
    public String getDisplayName()
    {
        return "XLT Parameters";
    }

    @Override
    public String getUrlName()
    {
        return "xltParameters";
    }

    @Override
    public String getIconFileName()
    {
        return LoadTestBuilder.getResourcePath("logo_24_24.png");
    }

    @Override
    public List<ParameterValue> getParameters()
    {
        return Collections.unmodifiableList(parameters);
    }

}
