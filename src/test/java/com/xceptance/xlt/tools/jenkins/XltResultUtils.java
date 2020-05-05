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

import java.util.Arrays;
import java.util.Random;

import org.apache.commons.lang.RandomStringUtils;

public final class XltResultUtils
{

    public static XltResult createRandomResult()
    {
        return new XltResultUtils().randomResult();
    }

    public static CriterionResult createRandomCriterionResult()
    {
        return new XltResultUtils().randomCriterionResult();
    }

    public static TestCaseInfo createRandomTestCaseInfo()
    {
        return new XltResultUtils().randomTestCaseInfo();
    }

    public static SlowRequestInfo createRandomRequestInfo()
    {
        return new XltResultUtils().randomRequestInfo();
    }

    private final Random random = new Random();

    private XltResult randomResult()
    {
        final XltResult r = new XltResult();
        r.setConditionError(random.nextBoolean());
        r.setConditionCritical(random.nextBoolean());
        r.setConditionFailed(random.nextBoolean());

        r.setDiffReportUrl("file:///" + RandomStringUtils.randomAlphanumeric(1 + random.nextInt(32)) + "/index.html");
        r.setTestFailures(Arrays.asList(randomTestCaseInfo(), randomTestCaseInfo()));
        r.setRunFailed(random.nextBoolean());
        r.setSlowestRequests(Arrays.asList(randomRequestInfo(), randomRequestInfo(), randomRequestInfo()));
        r.setFailedCriteria(Arrays.asList(randomCriterionResult()));

        return r;
    }

    private CriterionResult randomCriterionResult()
    {
        final CriterionResult.Type[] types = CriterionResult.Type.values();

        final CriterionResult crit = new CriterionResult(RandomStringUtils.random(32 + random.nextInt(16)),
                                                         types[random.nextInt(types.length)]);
        if (random.nextBoolean())
        {
            crit.setExceptionMessage(RandomStringUtils.random(64 + random.nextInt(32)));
        }
        if (random.nextBoolean())
        {
            crit.setCauseMessage(RandomStringUtils.random(1 + random.nextInt(32)));
        }
        if (random.nextBoolean())
        {
            crit.setCondition(RandomStringUtils.random(1 + random.nextInt(24)));
        }
        if (random.nextBoolean())
        {
            crit.setCriterionID("runtime");
        }
        crit.setValue(RandomStringUtils.randomAlphanumeric(5));
        crit.setXPath("//runtime[number(@value) < " + Integer.toString(1 + random.nextInt(2000)) + "]");

        return crit;
    }

    private TestCaseInfo randomTestCaseInfo()
    {
        return new TestCaseInfo("T" + RandomStringUtils.randomAlphabetic(1 + random.nextInt(5)),
                                "A" + RandomStringUtils.randomAlphabetic(1 + random.nextInt(10)),
                                RandomStringUtils.random(32 + random.nextInt(32)));
    }

    private SlowRequestInfo randomRequestInfo()
    {
        return new SlowRequestInfo("https://" + RandomStringUtils.randomAlphanumeric(1 + random.nextInt(32)),
                                   Integer.toString(random.nextInt(5000)));
    }
}
