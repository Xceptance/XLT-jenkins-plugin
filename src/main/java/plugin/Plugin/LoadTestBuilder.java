package plugin.Plugin;

import hudson.Launcher;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.plugins.plot.Plot;
import hudson.plugins.plot.Series;
import hudson.plugins.plot.XMLSeries;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;

import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Scanner;


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
    
    private JSONObject config = new JSONObject();

    private final Map<String,Plot> plots = new Hashtable<String, Plot>();
    
    public enum CONFIG_PARAMETER { xPath };
    
    private XLTChartAction chartAction;
   
    @DataBoundConstructor
    public LoadTestBuilder(List <String> qualitiesToPush, String testConfiguration, String machineHost) 
    {
            	System.out.println("new LoadTestBuilder");
    	this.qualityList = qualitiesToPush;
        this.testConfiguration = testConfiguration;
        this.machineHost = machineHost;

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
    	return new ArrayList<Plot>(plots.values());
    }

    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
    	System.out.println("LoadTestBuilder.getProjectActions");
    	
    	ArrayList<Action> actions = new ArrayList<Action>();
    	
    	updateConfig(project);
    	List<String> names = getConfigNames();
    	for (String name : names) {
	        Plot plot = new Plot("","","XLT","3","xltPlot"+name,"line",false);		
	        plot.series = new ArrayList<Series>();
	        plots.put(name, plot);
    	}
    	chartAction = new XLTChartAction(project, getPlots());
    	
    	actions.add(chartAction);
    	return actions;
    }
    
    private void updateConfig(AbstractProject<?, ?> project){
    	System.out.println("LoadTestBuilder.updateConfig");
    	
		 File configFile = new File(project.getRootDir(),"xltConfig.json");
		 if(configFile.exists() && configFile.isFile()){
			 try {
				 String content = "";
				 Scanner scanner = new Scanner(configFile);
				 scanner.useDelimiter("\\Z");
				 while(scanner.hasNext()){
					 content += scanner.next();
				 }
				 scanner.close();
				 config = new JSONObject(content);
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		 }
    }
    
    public String getConfigValue(String configName, CONFIG_PARAMETER parameter) throws JSONException{
    	return config.getJSONObject(configName).getString(parameter.name());
    }
    
    public List<String> getConfigNames(){
    	String[] names = JSONObject.getNames(config);
    	if(names == null || names.length == 0)
    		return new ArrayList<String>();
    	return Arrays.asList(names);
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
    
    private void postTestExecution(AbstractBuild<?,?> build){
    	XltRecorderAction printReportAction = new XltRecorderAction(build);
    	printReportAction.setReportPath(build.getProject().getBuildDir().toPath().toString());    	
    	build.getActions().add(printReportAction);
    	
    	List<String> names = getConfigNames();
    	for (String name : names) {
			try {
				String xPath = getConfigValue(name, CONFIG_PARAMETER.xPath);
				int last = xPath.lastIndexOf("[");
				String valuePath = xPath;
				if(last > -1){
					valuePath = xPath.substring(0, last);
				}
				System.out.println(name+" : "+xPath+" : "+valuePath);
				
				//TODO: validate xPath and set build state
								
				Plot plot = plots.get(name);
				plot.series.add(new XMLSeries("testreport.xml", valuePath, "NODE", ""));
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
    	for (Plot eachPlot : getPlots()) {
	        eachPlot.addBuild(build, System.out);
		}
    }
    
    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
    	System.out.println("LoadTestBuilder.prebuild");
    	updateConfig(build.getProject());
    	return true;
    }    
    
    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
    	System.out.println("LoadTestBuilder.perform");
    	
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
    	

    	postTestExecution(build);    	
    	
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

