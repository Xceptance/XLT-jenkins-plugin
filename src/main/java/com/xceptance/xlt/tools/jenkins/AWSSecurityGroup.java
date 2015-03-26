package com.xceptance.xlt.tools.jenkins;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class AWSSecurityGroup extends AbstractDescribableImpl<AWSSecurityGroup>
{
    private String ID;

    @DataBoundConstructor
    public AWSSecurityGroup(String ID)
    {
        this.ID = ID;
    }

    public String getID()
    {
        return ID;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<AWSSecurityGroup>
    {
        @Override
        public String getDisplayName()
        {
            return "AWSSecurityGroup-Descriptor";
        }
    }
}
