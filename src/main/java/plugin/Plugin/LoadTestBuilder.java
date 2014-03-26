package plugin.Plugin;

import hudson.Launcher;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.plugins.plot.Plot;
import hudson.plugins.plot.Series;
import hudson.plugins.plot.XMLSeries;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;





import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;

import org.apache.commons.io.FileUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import sun.security.krb5.SCDynamicStoreConfig;

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
import java.util.Collection;
import java.util.List;



/**
 * Readout the configuration of XLT in Jenkins, perform load testing and plot build results on project page.
 *
 * 
 * @author Michael Aleithe
 */
public class LoadTestBuilder extends Builder {

    private final String testConfiguration;
       
    private List<String> qualityList;
    
    private final String machineHost;

    private final List<Plot> plots = new ArrayList<Plot>();
   
    @DataBoundConstructor
    public LoadTestBuilder(List <String> qualitiesToPush, String testConfiguration, String machineHost) 
    {
            	
    	this.qualityList = qualitiesToPush;
        this.testConfiguration = testConfiguration;
        this.machineHost = machineHost;
                
        Plot plot = new Plot(null,null,"XLT","3","xltPlot1","line",false);		
        plot.series = new ArrayList<Series>();	
        
        Plot plot2 = new Plot(null,null,"XLT","3","xltPlot2","line",false);		
        plot2.series = new ArrayList<Series>();
        
        plots.add(plot);
        plots.add(plot2);

//        // Unpack XLT from *.zip
//        String url = new String("/home/maleithe/.jenkins/plugins/Plugin/xlt-4.3.3.zip");
//        
//        try
//    	{
//    		ZipFile xltZip = new ZipFile(url);
//    		xltZip.extractAll("/home/maleithe/.jenkins");    		
//    	}
//    	catch(ZipException e)
//    	{
//    		e.printStackTrace();
//    	}
    	    
    }
    
    public List<Plot> getPlots(){
    	return plots;
    }

    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
    	ArrayList<Action> actions = new ArrayList<Action>();
    	actions.add(new XLTChartAction(project, this));
    	return actions;
    }

    public List<String> getQualityList() {
        return qualityList;
    }

    public String getTestprofileSelected() {
        return testConfiguration;
    }
    
    public String getMachineHost() {
        return machineHost;
    }
    
    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
    	plots.get(0).series.add(new XMLSeries("testreport.xml", "/testreport/transactions/transaction/max", "NODE", ""));
    	plots.get(1).series.add(new XMLSeries("testreport.xml", "/testreport/transactions/transaction/min", "NODE", ""));		
    	for (Plot eachPlot : plots) {
    		eachPlot.addBuild(build, System.out);
    	}	


    	XltRecorderAction printReportAction = new XltRecorderAction();
    	printReportAction.setReportPath(build.getProject().getBuildDir().toPath().toString());
    	
    	build.getActions().add(printReportAction);
    	

    	
    	
    	// generate certain directory
    	String targetDirectory = build.getModuleRoot().toString() + "/../xlt-iteration-number/" + Integer.toString(build.getNumber());
    	
    	listener.getLogger().println(targetDirectory);
    	
    	File directory = new File(targetDirectory);    	
    	directory.mkdirs();
    	
    	String srcXlt = new String(build.getModuleRoot().toString() + "/../../../../xlt-4.3.3");
    	
    	listener.getLogger().println(srcXlt);
    	
    	
    	// copy XLT to certain directory
    	File srcDir = new File(srcXlt); 
    	File destDir = new File(targetDirectory);
    	
    	FileUtils.copyDirectory(srcDir, destDir, true);
    	
 
    	
    	
    	// perform XLT      	    	
    	ProcessBuilder builder = new ProcessBuilder("./mastercontroller.sh", "-auto", "-embedded", "-report", "-testPropertiesFile", testConfiguration, "-Dcom.xceptance.xlt.mastercontroller.testSuitePath=" + build.getModuleRoot().toString(), "-Dcom.xceptance.xlt.mastercontroller.agentcontrollers.ac1.url=" + machineHost);
    	
    	File path = new File(targetDirectory + "/bin");
    	
		
		// access files
		for (File child : path.listFiles())
		{
			child.setExecutable(true);
		}
    	
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
    	

    	
    	// view report link on build page
    	build.getActions().add(new XltRecorderAction());
    	
    	
		// build.setResult(Result.ABORTED);
    	//build.setResult(Result.)
    	
    	
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

