package plugin.Plugin;

import hudson.Launcher;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel.Option;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.servlet.ServletException;


/**
 * Readout the configuration of XLT in Jenkins.
 *
 * 
 * @author Michael Aleithe
 */
public class LoadTestBuilder extends Builder {

    private final String testsuite;

    private final String testProfileSelected;
    
    private final boolean errorSelected;
    
    private final boolean throughputSelected;
    
    private final boolean responseTimeSelected;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public LoadTestBuilder(String testsuite, String testConfiguration, Boolean errorSelected, Boolean throughputSelected, Boolean responseTimeSelected) {
        
    	this.testsuite = testsuite;
        this.testProfileSelected = testConfiguration;
        this.errorSelected = errorSelected;
        this.throughputSelected = throughputSelected;
        this.responseTimeSelected = responseTimeSelected;
    
    }

   
    public String getName() {
        return testsuite;
    }

    public String getTestprofileSelected() {
        return testProfileSelected;
    }
    
    public Boolean getErrorSelected() {
        return errorSelected;
    }
    
    public Boolean getThroughputSelected() {
        return throughputSelected;
    }
    
    public Boolean getResponseTimeSelected() {
        return responseTimeSelected;
    }



    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {

    	// plot adjustments of XLT Plugin
    	listener.getLogger().println("test-suite    : " + testsuite);
    	listener.getLogger().println("test-profile  : " + testProfileSelected);
    	listener.getLogger().println("errors        : " + errorSelected);
    	listener.getLogger().println("throughput    : " + throughputSelected);
    	listener.getLogger().println("response times: " + responseTimeSelected);
    	
    	
    	// replace the complete line for testsuite in mastercontroller.properties
    	String actualTestsuite = new String("beispiel");
    	
    	// perform XLT      	    	
    	ProcessBuilder builder = new ProcessBuilder("./mastercontroller.sh", "-auto", "-embedded", "-report", "-testPropertiesFile", testProfileSelected + ".properties");
    	
    	File path = new File("/home/maleithe/xlt-4.3.2_jenkins/bin");    	
    	builder.directory(path);    	
    	Process process = builder.start();
    	
    	// print XLT console output in Jenkins   	
    	InputStream is = process.getInputStream();
    	BufferedReader br = new BufferedReader(new InputStreamReader(is));
    	String line;
    	String lastline = null;
    	
    	while ((line = br.readLine()) != null)
    	{
    		if (line != null)
    		{	
    			lastline = line;    			
    			listener.getLogger().println(lastline);	
    		}
    		
    		try
    		{
    			process.exitValue();
    		}
    		catch(Exception e)
    		{
    			continue;
    		}
    		break;
    	}
    	
    	
    	// waiting until XLT is finished
    	process.waitFor();
    	
    	listener.getLogger().println("XLT_FINISHED");
    	
    	
    	return true;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }


 // This indicates to Jenkins that this is an implementation of an extension point.
    @Extension 
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * In order to load the persisted global configuration, you have to 
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

//        /**
//         * Performs on-the-fly validation of the form field 'name'.
//         *
//         * @param value
//         *      This parameter receives the value that the user has typed.
//         * @return
//         *      Indicates the outcome of the validation. This is sent to the browser.
//         */
//        public FormValidation doCheckName(@QueryParameter String value)
//                throws IOException, ServletException {
//            if (value.length() == 0)
//                return FormValidation.error("Please set a name");
//            if (value.length() < 4)
//                return FormValidation.warning("Isn't the name too short?");
//            return FormValidation.ok();
//        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "XLT Plugin";
        }

//        @Override
//        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
//            // To persist global configuration information,
//            // set that to properties and call save().
//            useFrench = formData.getBoolean("useFrench");
//            // ^Can also use req.bindJSON(this, formData);
//            //  (easier when there are many fields; need set* methods for this, like setUseFrench)
//            save();
//            return super.configure(req,formData);
//        }

//        /**
//         * This method returns true if the global configuration says we should speak French.
//         *
//         * The method name is bit awkward because global.jelly calls this method to determine
//         * the initial state of the checkbox by the naming convention.
//         */
//        public boolean getUseFrench() {
//            return useFrench;
//        }
    }
}

