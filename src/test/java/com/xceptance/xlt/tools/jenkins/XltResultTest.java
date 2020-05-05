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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import org.junit.Test;

public class XltResultTest
{

    @Test
    public void testSerializable() throws Exception
    {
        final XltResult result = XltResultUtils.createRandomResult();
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();

        try (final ObjectOutputStream os = new ObjectOutputStream(bos))
        {
            os.writeObject(result);
        }
        catch (final Exception e)
        {
            fail("Failed to serialize result: " + e);
        }

        final XltResult res;
        try (final ObjectInputStream is = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray())))
        {
            res = (XltResult) is.readObject();

        }
        catch (final Exception e)
        {
            fail("Failed to de-serilize result: " + e);
            return;
        }
        assertEquals(result.getConditionError(), res.getConditionError());
        assertEquals(result.getConditionCritical(), res.getConditionCritical());
        assertEquals(result.getConditionFailed(), res.getConditionFailed());
        assertEquals(result.getDiffReportUrl(), res.getDiffReportUrl());
        assertEquals(result.getReportUrl(), res.getReportUrl());
        assertEquals(result.getRunFailed(), res.getRunFailed());
        {
            final List<TestCaseInfo> failures = result.getTestFailures();
            final List<TestCaseInfo> failures2 = res.getTestFailures();
            assertEquals(failures.size(), failures2.size());

            for (int i = 0; i < failures.size(); i++)
            {
                final TestCaseInfo info = failures.get(i);
                final TestCaseInfo info2 = failures2.get(i);
                assertEquals(info.getActionName(), info2.getActionName());
                assertEquals(info.getMessage(), info2.getMessage());
                assertEquals(info.getTestCaseName(), info2.getTestCaseName());
            }
        }

        {
            final List<SlowRequestInfo> slow = result.getSlowestRequests();
            final List<SlowRequestInfo> slow2 = res.getSlowestRequests();
            assertEquals(slow.size(), slow2.size());

            for (int i = 0; i < slow.size(); i++)
            {
                final SlowRequestInfo info = slow.get(i);
                final SlowRequestInfo info2 = slow2.get(i);
                assertEquals(info.getUrl(), info2.getUrl());
                assertEquals(info.getRuntime(), info2.getRuntime());
            }
        }

        {
            List<CriterionResult> failures = result.getCriteriaFailures();
            List<CriterionResult> failures2 = res.getCriteriaFailures();
            assertEquals(failures.size(), failures2.size());

            for (int i = 0; i < failures.size(); i++)
            {
                final CriterionResult crit = failures.get(i);
                final CriterionResult crit2 = failures2.get(i);
                assertEquals(crit.getCauseMessage(), crit2.getCauseMessage());
                assertEquals(crit.getCondition(), crit2.getCondition());
                assertEquals(crit.getCriterionID(), crit2.getCriterionID());
                assertEquals(crit.getExceptionMessage(), crit2.getExceptionMessage());
                assertEquals(crit.getMessage(), crit2.getMessage());
                assertEquals(crit.getValue(), crit2.getValue());
                assertEquals(crit.getXPath(), crit2.getXPath());

            }

            failures = result.getCriteriaErrors();
            failures2 = res.getCriteriaErrors();

            assertEquals(failures.size(), failures2.size());

            for (int i = 0; i < failures.size(); i++)
            {
                final CriterionResult crit = failures.get(i);
                final CriterionResult crit2 = failures2.get(i);
                assertEquals(crit.getCauseMessage(), crit2.getCauseMessage());
                assertEquals(crit.getCondition(), crit2.getCondition());
                assertEquals(crit.getCriterionID(), crit2.getCriterionID());
                assertEquals(crit.getExceptionMessage(), crit2.getExceptionMessage());
                assertEquals(crit.getMessage(), crit2.getMessage());
                assertEquals(crit.getValue(), crit2.getValue());
                assertEquals(crit.getXPath(), crit2.getXPath());

            }

        }
    }

}
