package com.xceptance.xlt.tools.jenkins.util;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.xceptance.xlt.tools.jenkins.CriterionResult;
import com.xceptance.xlt.tools.jenkins.PlotValuesConfiguration;
import com.xceptance.xlt.tools.jenkins.PlotValuesConfiguration.Value;
import com.xceptance.xlt.tools.jenkins.logging.LOGGER;

public final class CriterionChecker
{
    private CriterionChecker()
    {
    }

    public static List<CriterionResult> getFailed(final Document document, final PlotValuesConfiguration config)
    {
        final List<CriterionResult> failedAlerts = new ArrayList<>();
        if (document == null)
        {
            CriterionResult criterionResult = CriterionResult.error("No test data found.");
            failedAlerts.add(criterionResult);
        }
        else
        {
            for (final Value val : config.getValues())
            {
                final String critId = val.getId();
                final String xPath = val.getXpath();
                final String condition = val.getCondition();

                if (StringUtils.isBlank(condition))
                {
                    LOGGER.debug("No condition for criterion. (criterionID: \"" + critId + "\")");
                    continue;
                }
                if (StringUtils.isBlank(xPath))
                {
                    CriterionResult criterionResult = CriterionResult.error("No xPath for Criterion");
                    criterionResult.setCriterionID(critId);
                    failedAlerts.add(criterionResult);
                    continue;
                }
                final String conditionPath = xPath + condition;
                final Node node = XmlUtils.evaluateXPath(document, xPath);

                if (node == null)
                {
                    CriterionResult criterionResult = CriterionResult.error("No result for XPath");
                    criterionResult.setCriterionID(critId);
                    criterionResult.setXPath(xPath);
                    failedAlerts.add(criterionResult);
                    continue;
                }

                // test the condition
                final Node no2 = XmlUtils.evaluateXPath(document, conditionPath);
                if (no2 == null)
                {
                    CriterionResult criterionResult = CriterionResult.failed("Condition");
                    criterionResult.setCriterionID(critId);
                    criterionResult.setValue(node.getTextContent().trim());
                    criterionResult.setCondition(condition);
                    criterionResult.setXPath(xPath);
                    failedAlerts.add(criterionResult);
                }
            }
        }
        return failedAlerts;
    }
}
