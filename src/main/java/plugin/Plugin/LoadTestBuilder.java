package plugin.Plugin;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.plugins.plot.Plot;
import hudson.plugins.plot.Series;
import hudson.plugins.plot.XMLSeries;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import jenkins.model.Jenkins;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.SystemUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;



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
    
    public enum CONFIG_CRITERIA_PARAMETER { xPath, plotID, condition, name};
    public enum CONFIG_PLOT_PARAMETER { enabled, buildCount, title};    
    public enum CONFIG_SECTIONS_PARAMETER { criterias, plots};
    
    private XLTChartAction chartAction;
   
    @DataBoundConstructor
    public LoadTestBuilder(List <String> qualitiesToPush, String testConfiguration, String machineHost) 
    {
            	System.out.println("new LoadTestBuilder");
    	this.qualityList = qualitiesToPush;
        this.testConfiguration = testConfiguration;
        this.machineHost = machineHost;
    	    
    }
    
    public List<Plot> getPlots(){
    	return new ArrayList<Plot>(plots.values());
    }
    
    private Plot getPlot(String configName){
		try {
			String plotID = getCriteriaConfigValue(configName, CONFIG_CRITERIA_PARAMETER.plotID);
			if(!plots.containsKey(plotID)){
				String plotCount = getPlotConfigValue(configName, CONFIG_PLOT_PARAMETER.buildCount);
				String title = getPlotConfigValue(configName, CONFIG_PLOT_PARAMETER.title);
				
				Plot plot = new Plot(title,"","XLT",plotCount,"xltPlot"+plotID,"line",false);		
				plot.series = new ArrayList<Series>();
				plots.put(plotID, plot);
			}
			if("yes".equals(getPlotConfigValue(configName, CONFIG_PLOT_PARAMETER.enabled))){
				return plots.get(plotID);
			}
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}    	
		return null;
    }
    
    private void createPlot(String configName){
    	getPlot(configName);
    }
    
    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
    	System.out.println("LoadTestBuilder.getProjectActions");
    	
    	ArrayList<Action> actions = new ArrayList<Action>();
    	
    	updateConfig(project);
    	List<String> names = getConfigNames();
    	for (String name : names) {
    		createPlot(name);
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
		 }else{
			 //TODO
			 System.out.println("no config file found at: "+configFile.getAbsolutePath());
		 }
    }
    
    public String getCriteriaConfigValue(String configName, CONFIG_CRITERIA_PARAMETER parameter) throws JSONException{
    	return config.getJSONObject(CONFIG_SECTIONS_PARAMETER.criterias.name()).getJSONObject(configName).getString(parameter.name());
    }
    
    public String getPlotConfigValue(String configName, CONFIG_PLOT_PARAMETER parameter) throws JSONException{
    	String plotConfigName = getCriteriaConfigValue(configName, CONFIG_CRITERIA_PARAMETER.plotID);
    	return config.getJSONObject(CONFIG_SECTIONS_PARAMETER.plots.name()).getJSONObject(plotConfigName).getString(parameter.name());
    }
    
    
    public List<String> getConfigNames(){
    	JSONObject criteriaSection = config.optJSONObject(CONFIG_SECTIONS_PARAMETER.criterias.name());
    	String[] names = null;
    	if(criteriaSection != null){
    		names = JSONObject.getNames(criteriaSection);
    	}
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
    
    private void postTestExecution(AbstractBuild<?,?> build, BuildListener listener){
    	List<String> failedAlerts = new ArrayList<String>();
    	
    	XltRecorderAction printReportAction = new XltRecorderAction(build);
    	build.getActions().add(printReportAction);

    	updateConfig(build.getProject());
    	File dataFile = null;
    	try{
			// copy testreport.xml to workspace
    		File testReportFileXml = new File(build.getRootDir(), "report/testreport.xml");
    		dataFile = new File(new File(build.getModuleRoot().toURI()),"testreport.xml");    		
    		if(!testReportFileXml.exists()){
    			failedAlerts.add("No test data found at: "+testReportFileXml.getAbsolutePath());
    		}else{
    			FileUtils.copyFile(testReportFileXml, dataFile);
	    		if(!dataFile.exists()){
	    			failedAlerts.add("Expected copy of test data at: "+dataFile.getAbsolutePath());
	    		}else{		        	
					Document dataXml =DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(dataFile);
			    	List<String> names = getConfigNames();
			    	for (String name : names) {
						try {
							String xPath = getCriteriaConfigValue(name, CONFIG_CRITERIA_PARAMETER.xPath);
							String condition = getCriteriaConfigValue(name, CONFIG_CRITERIA_PARAMETER.condition);							
							String conditionPath = xPath+condition;
							
							System.out.println(name+" : "+conditionPath+" : "+xPath);					

							String value =  XPathFactory.newInstance().newXPath().evaluate(conditionPath, dataXml);
							// validate value and collect failed validations then set the build state
							if (value != null && value.isEmpty())
							{
								failedAlerts.add("Condition failed: "+name + " : " + xPath);
							}
							
							Element node = (Element)XPathFactory.newInstance().newXPath().evaluate(xPath, dataXml, XPathConstants.NODE);
							String label = getCriteriaConfigValue(name, CONFIG_CRITERIA_PARAMETER.name);
							node.setAttribute("name", label);							
							
							Plot plot = getPlot(name);
							if(plot != null){
								plot.series.add(new XMLSeries("testreport.xml", xPath, "NODE", ""));
							}
						} catch (JSONException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (XPathExpressionException e) {
							e.printStackTrace();
							String message = name + " xPath evaluation failed \n" +e.getMessage();					
							failedAlerts.add(message);
						}
					}
			    	
					Transformer transformer = TransformerFactory.newInstance().newTransformer();
					javax.xml.transform.Result output = new StreamResult(dataFile);
					Source input = new DOMSource(dataXml);
			
					transformer.transform(input, output);
					
			    	//must happen after everything is in place... this will start the data collection for the plot
			    	for (Plot eachPlot : getPlots()) {
				        eachPlot.addBuild(build, System.out);
					}
	    		}
    		}
    	}catch(IOException e){
			// TODO Auto-generated catch block
    		e.printStackTrace();
    	} catch (SAXException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (ParserConfigurationException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (TransformerConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TransformerFactoryConfigurationError e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TransformerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}finally{
			if(dataFile != null){
				try {
					Files.deleteIfExists(dataFile.toPath());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
    	
    	if (!failedAlerts.isEmpty())
    	{
    		build.setResult(Result.FAILURE);
    		for (String eachAlert : failedAlerts) {
    			listener.getLogger().println(eachAlert);
    			System.out.println(eachAlert);
    		}
    	}    	
    }
    
    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
    	System.out.println("LoadTestBuilder.prebuild");
    	return true;
    }    
    
    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
    	System.out.println("LoadTestBuilder.perform");
    	
    	postTestExecution(build, listener);
    	
    	// generate certain directory
    	File destDir = new File(build.getProject().getRootDir(),"xlt-iteration-number/"+ Integer.toString(build.getNumber()));
       	listener.getLogger().println(destDir.getAbsolutePath());
    	destDir.mkdirs();    	    	
    	
    	// copy XLT to certain directory
    	File srcDir = new File(Jenkins.getInstance().getRootDir(),"xlt-4.3.3");
    	listener.getLogger().println(srcDir.getAbsolutePath());
    	
    	FileUtils.copyDirectory(srcDir, destDir, true);
 
        // perform XLT              
        List<String> commandLine = new ArrayList<String>();

        if (SystemUtils.IS_OS_WINDOWS)
        {
            commandLine.add("cmd.exe");
            commandLine.add("/c");
            commandLine.add("mastercontroller.cmd");
        }
        else
        {
            commandLine.add("./mastercontroller.sh");
        }
     
        // check if machineHost is localhost
        if(machineHost.contains("localhost"))
        {
        	commandLine.add("-embedded");
        }
        
        commandLine.add("-auto");
        commandLine.add("-report");
        commandLine.add("-testPropertiesFile");
        commandLine.add(testConfiguration);
        commandLine.add("-Dcom.xceptance.xlt.mastercontroller.testSuitePath=" + build.getModuleRoot().toString());
        commandLine.add("-Dcom.xceptance.xlt.mastercontroller.agentcontrollers.ac1.url=" + machineHost);

        ProcessBuilder builder = new ProcessBuilder(commandLine);
    	
    	File path = new File(destDir + "/bin");
    	
		
		// access files
		for (File child : path.listFiles())
		{
			child.setExecutable(true);
		}
    	
    	builder.directory(path);
    	
    	// print error-stream in jenkins-console
    	builder.redirectErrorStream(true);

        // start XLT
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
    	
    	
    	// waiting until XLT is finished and set FAILED in case of unexpected termination
    	if(process.waitFor()!=0)
    	{
    		build.setResult(Result.FAILURE);
    	}
    	
    	    	
    	listener.getLogger().println("XLT_FINISHED");
    	
    	
    	// copy xlt-report to build directory
    	File srcXltReport = new File(destDir, "reports");
    	File[] files = srcXltReport.listFiles();
    	File lastFile = files[files.length-1];
    	srcXltReport = lastFile;
    	File destXltReport = new File(build.getRootDir(), "report");
    	
    	FileUtils.copyDirectory(srcXltReport, destXltReport, true);    	
    	
    	postTestExecution(build, listener);    	
    	    	
    	return true;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }


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

    }
}

