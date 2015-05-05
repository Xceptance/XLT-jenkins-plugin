package com.xceptance.xlt.tools.jenkins;

public class BuildNodeGoneException extends Exception
{
    public BuildNodeGoneException(String message)
    {
        super(message);
    }

    public BuildNodeGoneException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
