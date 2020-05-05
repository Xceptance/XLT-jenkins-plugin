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
package com.xceptance.xlt.tools.jenkins.logging;

import java.util.logging.Level;
import java.util.logging.Logger;

public class LOGGER
{
    private static final String LOGGER_NAME = "XltPlugin";

    private static final Logger LOGGER = Logger.getLogger(LOGGER_NAME);

    private LOGGER()
    {

    }

    public static void error(String msg, Throwable t)
    {
        LOGGER.log(Level.SEVERE, msg, t);
    }

    public static void info(String msg)
    {
        LOGGER.log(Level.INFO, msg);
    }

    public static void warn(String msg, Throwable t)
    {
        LOGGER.log(Level.WARNING, msg, t);
    }

    public static void warn(String msg)
    {
        LOGGER.log(Level.WARNING, msg);
    }

    public static void debug(String msg)
    {
        LOGGER.log(Level.FINE, msg);
    }
}
