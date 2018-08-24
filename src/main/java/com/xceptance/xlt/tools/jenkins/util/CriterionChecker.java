package com.xceptance.xlt.tools.jenkins.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
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
            CriterionResult criterionResult = CriterionResult.error("No XML document given.");
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
                    CriterionResult criterionResult = CriterionResult.error("No XPath for Criterion");
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

    public static List<CriterionResult> parseCriteriaValidationResult(JSONObject critOutJSON)
    {
        final ArrayList<CriterionResult> list = new ArrayList<>();
        if (critOutJSON != null)
        {
            final HashMap<String, JSONObject> critById = new HashMap<>();
            final JSONArray criteria = critOutJSON.optJSONArray("criteria");
            if (criteria != null)
            {
                for (int j = 0, l = criteria.length(); j < l; j++)
                {
                    final JSONObject c = criteria.optJSONObject(j);
                    if (c != null)
                    {
                        final String cid = c.optString("id");
                        if (StringUtils.isNotBlank(cid))
                        {
                            critById.put(cid, c);
                        }
                    }
                }
            }

            parseCriteriaChecks(critById, critOutJSON.optJSONArray("checks"), list);
        }

        return list;
    }

    private static void parseCriteriaChecks(HashMap<String, JSONObject> critById, JSONArray checks, ArrayList<CriterionResult> list)
    {
        if (checks == null)
        {
            return;
        }

        for (int i = 0, l = checks.length(); i < l; i++)
        {
            final JSONObject check = checks.optJSONObject(i);
            if (check != null)
            {
                parseCriteriaCheck(critById, check, list);
            }
        }
    }

    private static void parseCriteriaCheck(HashMap<String, JSONObject> critById, JSONObject check, ArrayList<CriterionResult> list)
    {
        final JSONObject details = check.optJSONObject("details");
        if (details != null)
        {
            for (final String critID : details.keySet())
            {
                if (StringUtils.isBlank(critID))
                {
                    continue;
                }

                final JSONObject detail = details.optJSONObject(critID);
                final JSONObject crit = critById.get(critID);
                if (detail == null || crit == null)
                {
                    continue;
                }
                final String status = detail.optString("status").toLowerCase();
                final String message = detail.optString("message");
                CriterionResult cr = null;
                if ("error".equals(status))
                {
                    cr = CriterionResult.error(message);
                }
                else if ("failed".equals(status))
                {
                    cr = CriterionResult.failed(message);
                }

                if (cr != null)
                {
                    cr.setCondition(crit.optString("condition"));
                    cr.setCriterionID(critID);
                    list.add(cr);
                }
            }
        }
    }

}
