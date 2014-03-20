package plugin.Plugin;

import hudson.Launcher;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;

import org.kohsuke.stapler.DataBoundConstructor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;



/**
 * Readout the configuration of XLT in Jenkins.
 *
 * 
 * @author Michael Aleithe
 */
public class LoadTestBuilder extends Builder {

    //private final String testsuite;

    private final String testConfiguration;
    
    private final boolean errorSelected;
    
    private final boolean throughputSelected;
    
    private final boolean responseTimeSelected;
    
//    private boolean spielerei = false;
//    
//    private List<String> qualityList = Arrays.asList("Advanced", "World!", "Throughput", "Error");

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    
    @DataBoundConstructor
    public LoadTestBuilder( String testConfiguration, Boolean errorSelected, Boolean throughputSelected, Boolean responseTimeSelected) throws IOException {
        
    	
    	//TODO  Unzip XLT on a defined directory as a global XLT
  
    	
//    	Run r;
//    	r.getPreviousBuildInProgress();
//    	r.getDisplayName().toString();
    	
    	//this.testsuite = testsuite;
        this.testConfiguration = testConfiguration;
        this.errorSelected = errorSelected;
        this.throughputSelected = throughputSelected;
        this.responseTimeSelected = responseTimeSelected;
        
        //System.err.println("error");
    
    }
 
   // spielerei
//    public Boolean getSpielerei() {
//        return spielerei;
//    }
    
//    public List<String> getQualityList() {
//        return qualityList;
//    }

    public String getTestprofileSelected() {
        return testConfiguration;
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

    	//TODO make a copy of XLT to the specific job directory before starting mastercontroller
    	//getClass().get
    	
    	// plot adjustments of XLT Plugin
    	//listener.getLogger().println("test-suite    : " + testsuite);
    	listener.getLogger().println("testConfig    : " + testConfiguration);
    	listener.getLogger().println("errors        : " + errorSelected);
    	listener.getLogger().println("throughput    : " + throughputSelected);
    	listener.getLogger().println("response times: " + responseTimeSelected);
    	
    	
    	build.getModuleRoot();
    	
    	listener.getLogger().println(build.getModuleRoot());
    	
    	    	
//    	// Download XLT from XC-website
//    	URL xcSite = new URL("https://www.xceptance.com/products/xlt/download.html");
//    	ReadableByteChannel rbc = Channels.newChannel(xcSite.openStream());
//    	
//    	try
//    	{
//    			FileOutputStream fos = new FileOutputStream(build.getModuleRoot() + "/xlt-4.3.3.zip");
//    			fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
//    	}
//    	catch(Exception e)
//    	{
//    			listener.getLogger().println(e.getMessage());
//    	}
    	
    	


    	
//    	// perform XLT      	    	
//    	ProcessBuilder builder = new ProcessBuilder("./mastercontroller.sh", "-auto", "-embedded", "-report", "-testPropertiesFile", testConfiguration);
//    	
//    	File path = new File("/home/maleithe/xlt-4.3.2_jenkins/bin");    	
//    	builder.directory(path);    	
//    	Process process = builder.start();
//    	
//    	// print XLT console output in Jenkins   	
//    	InputStream is = process.getInputStream();
//    	BufferedReader br = new BufferedReader(new InputStreamReader(is));
//    	String line;
//    	String lastline = null;
//    	
//    	while ((line = br.readLine()) != null)
//    	{
//    		if (line != null)
//    		{	
//    			lastline = line;    			
//    			listener.getLogger().println(lastline);	
//    		}
//    		
//    		try
//    		{
//    			process.exitValue();
//    		}
//    		catch(Exception e)
//    		{
//    			continue;
//    		}
//    		break;
//    	}
//    	
//    	
//    	// waiting until XLT is finished
//    	process.waitFor();
//    	
//    	listener.getLogger().println("XLT_FINISHED");
    	
    	
    	
    	
		//build.setResult(Result.ABORTED);
    	
    	
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

