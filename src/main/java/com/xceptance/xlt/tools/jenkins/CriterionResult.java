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
import java.util.List;

public class CriterionResult implements Serializable
{
    /** The serialVersionUID. */
    private static final long serialVersionUID = -6839553617591586597L;

    private Type type;

    private String message;

    private String criterionID;

    private String xPath;

    private String value;

    private String condition;

    private String causeMessage;

    private String exceptionMessage;

    public enum Type
    {
        FAILED, ERROR
    };

    public CriterionResult(String message, Type type)
    {
        this.type = type;
        this.message = message;
    }

    public static CriterionResult error(String message)
    {
        return new CriterionResult(message, Type.ERROR);
    }

    public static CriterionResult failed(String message)
    {
        return new CriterionResult(message, Type.FAILED);
    }

    public Type getType()
    {
        return type;
    }

    public String getMessage()
    {
        return message;
    }

    public void setCriterionID(String criterionID)
    {
        this.criterionID = criterionID;
    }

    public String getCriterionID()
    {
        return criterionID;
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
        if (criterionID != null)
        {
            logMessage += "\n    Criterion: \"" + criterionID + "\"";
        }
        if (value != null)
        {
            logMessage += "\n    Value: \"" + value + "\"";
        }
        if (condition != null)
        {
            logMessage += "\n    Condition: \"" + condition + "\"";
        }
        if (xPath != null)
        {
            logMessage += "\n    XPath: \"" + xPath + "\"";
        }
        if (exceptionMessage != null)
        {
            logMessage += "\n    Error: \"" + exceptionMessage + "\"";
        }
        if (causeMessage != null)
        {
            logMessage += "\n    Cause: \"" + causeMessage + "\"";
        }
        return logMessage;
    }

    public static String getFormattedConditionMessage(String title, List<CriterionResult> resultList)
    {
        String message = "";
        if (!resultList.isEmpty())
        {
            message += title + ":\n";
            for (CriterionResult eachAlert : resultList)
            {
                message += eachAlert.getLogMessage() + "\n";
            }
        }
        return message;
    }
}
