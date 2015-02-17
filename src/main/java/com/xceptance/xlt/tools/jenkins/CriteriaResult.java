package com.xceptance.xlt.tools.jenkins;

@Deprecated
// this is required for old builds which depend on the structure of this class to be backward compatible
public class CriteriaResult extends CriterionResult
{
    @Deprecated
    private String criteriaID;

    @Deprecated
    public CriteriaResult(String message, Type type)
    {
        super(message, type);
    }

    @Override
    @Deprecated
    public String getCriterionID()
    {
        return criteriaID;
    }
}
