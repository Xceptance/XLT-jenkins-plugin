package com.xceptance.xlt.tools.jenkins;

public class CriteriaResult
{

    private Type type;

    private String message;

    private String criteriaID;

    private String xPath;

    private String value;

    private String condition;

    private String causeMessage;

    private String exceptionMessage;

    public enum Type
    {
        FAILED, ERROR
    };

    public CriteriaResult(String message, Type type)
    {
        this.type = type;
        this.message = message;
    }

    public static CriteriaResult error(String message)
    {
        return new CriteriaResult(message, Type.ERROR);
    }

    public static CriteriaResult failed(String message)
    {
        return new CriteriaResult(message, Type.FAILED);
    }

    public Type getType()
    {
        return type;
    }

    public String getMessage()
    {
        return message;
    }

    public void setCriteriaID(String criteriaID)
    {
        this.criteriaID = criteriaID;
    }

    public String getCriteriaID()
    {
        return criteriaID;
    }

    public void setXPath(String xPath)
    {
        this.xPath = xPath;
    }

    public String getXPath()
    {
        return xPath;
    }

    public void setValue(String value)
    {
        this.value = value;
    }

    public String getValue()
    {
        return value;
    }

    public void setCondition(String condition)
    {
        this.condition = condition;
    }

    public String getCondition()
    {
        return condition;
    }

    public void setCauseMessage(String causeMessage)
    {
        this.causeMessage = causeMessage;
    }

    public String getCauseMessage()
    {
        return causeMessage;
    }

    public void setExceptionMessage(String exceptionMessage)
    {
        this.exceptionMessage = exceptionMessage;
    }

    public String getExceptionMessage()
    {
        return exceptionMessage;
    }

    public String getLogMessage()
    {
        String logMessage = type.name() + ": " + message;
        if (criteriaID != null)
        {
            logMessage += "\t\t\n Criteria: \"" + criteriaID + "\"";
        }
        if (value != null)
        {
            logMessage += "\t\t\n Value: \"" + value + "\"";
        }
        if (condition != null)
        {
            logMessage += "\t\t\n Condition: \"" + condition + "\"";
        }
        if (xPath != null)
        {
            logMessage += "\t\t\n XPath: \"" + xPath + "\"";
        }
        if (exceptionMessage != null)
        {
            logMessage += "\t\t\n Error: \"" + exceptionMessage + "\"";
        }
        if (causeMessage != null)
        {
            logMessage += "\t\t\n Cause: \"" + causeMessage + "\"";
        }
        return logMessage;
    }

}
