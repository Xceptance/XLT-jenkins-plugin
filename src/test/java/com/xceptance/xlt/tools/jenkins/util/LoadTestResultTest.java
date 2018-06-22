package com.xceptance.xlt.tools.jenkins.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import org.junit.Test;

import com.xceptance.xlt.tools.jenkins.SlowRequestInfo;
import com.xceptance.xlt.tools.jenkins.TestCaseInfo;
import com.xceptance.xlt.tools.jenkins.XltResultUtils;
import com.xceptance.xlt.tools.jenkins.pipeline.LoadTestResult;

public class LoadTestResultTest
{

    @Test
    public void testSerializable() throws Exception
    {
        final LoadTestResult result = new LoadTestResult(XltResultUtils.createRandomResult());
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try(final ObjectOutputStream os = new ObjectOutputStream(bos))
        {
            os.writeObject(result);
        }
        catch(final Exception e)
        {
            fail("Failed to serialize result: " + e);
        }
        
        final LoadTestResult res;
        try(final ObjectInputStream is = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray())))
        {
             res = (LoadTestResult) is.readObject();
        }
        catch(final Exception e)
        {
            fail("Failed to de-serialize result: " + e);
            return;
        }
        
        assertEquals(result.getConditionCritical(), res.getConditionCritical());
        assertEquals(result.getConditionError(), res.getConditionError());
        assertEquals(result.getConditionFailed(), res.getConditionFailed());
        assertEquals(result.getConditionMessage(), res.getConditionMessage());
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
            List<LoadTestResult.CriterionResult> failures = result.getCriteriaFailures();
            List<LoadTestResult.CriterionResult> failures2 = res.getCriteriaFailures();
            assertEquals(failures.size(), failures2.size());

            for (int i = 0; i < failures.size(); i++)
            {
                final LoadTestResult.CriterionResult crit = failures.get(i);
                final LoadTestResult.CriterionResult crit2 = failures2.get(i);
                assertEquals(crit.getCondition(), crit2.getCondition());
                assertEquals(crit.getMessage(), crit2.getMessage());
                assertEquals(crit.getId(), crit2.getId());

            }

            failures = result.getCriteriaErrors();
            failures2 = res.getCriteriaErrors();

            assertEquals(failures.size(), failures2.size());

            for (int i = 0; i < failures.size(); i++)
            {
                final LoadTestResult.CriterionResult crit = failures.get(i);
                final LoadTestResult.CriterionResult crit2 = failures2.get(i);
                assertEquals(crit.getCondition(), crit2.getCondition());
                assertEquals(crit.getMessage(), crit2.getMessage());
                assertEquals(crit.getId(), crit2.getId());
            }

        }
        
    }

}
