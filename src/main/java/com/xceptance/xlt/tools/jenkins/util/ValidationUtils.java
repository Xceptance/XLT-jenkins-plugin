package com.xceptance.xlt.tools.jenkins.util;

import java.util.Arrays;
import java.util.EnumSet;

import org.apache.commons.lang3.StringUtils;

import hudson.util.FormValidation;

public final class ValidationUtils
{
    private ValidationUtils()
    {
    }

    public static enum Flags
    {
     IGNORE_BLANK_VALUE,
     IGNORE_MIN,
     IGNORE_MAX,
     IS_INTEGER
    }

    /**
     * Check if given string is valid number.
     * 
     * @param value
     *            - the string to test, can be null or empty
     * @param min
     *            - the lowest allowed number
     * @param max
     *            - the highest allowed number
     * @param flags
     *            - optional VALIDATION flags to skip some checks. If no flag is given then all checks will be done.
     * @return a FormValidation.OK if the value is a valid number within the bounds of min and max otherwise return a
     *         FormValidation.ERROR
     */
    public static FormValidation validateNumber(String value, Number min, Number max, Flags... flags)
    {
        EnumSet<Flags> flagSet = EnumSet.copyOf(Arrays.asList(flags));
        if (StringUtils.isBlank(value))
        {
            if (flagSet.contains(Flags.IGNORE_BLANK_VALUE))
                return FormValidation.ok();
            else
                return FormValidation.error("Please enter a number");
        }

        double number = -1;
        try
        {
            number = Double.valueOf(value);
        }
        catch (NumberFormatException e)
        {
            return FormValidation.error("Please enter a number");
        }
        if (flagSet.contains(Flags.IS_INTEGER) &&
            (number != (int) number || !StringUtils.trimToEmpty(value).equals(Integer.toString((int) number))))
        {
            return FormValidation.error("Please enter an integer");
        }
        if (!flagSet.contains(Flags.IGNORE_MIN))
        {
            if (min == null)
                return FormValidation.error("Min value is not defined.");

            if (number < min.doubleValue())
                return FormValidation.error("Please enter a valid number greater than or equal to " + min);
        }
        if (!flagSet.contains(Flags.IGNORE_MAX))
        {
            if (max == null)
                return FormValidation.error("Max value is not defined.");

            if (number > max.doubleValue())
                return FormValidation.error("Please enter a valid number lower than or equal to " + max);
        }
        return FormValidation.ok();
    }

}
