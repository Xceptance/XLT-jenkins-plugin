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
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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

    private final String testProperties;
       
    private List<String> qualityList;
    
    private final String machineHost;
    
    private String xltConfig;    
    
    private JSONObject config = new JSONObject();

    private final Map<String,Plot> plots = new Hashtable<String, Plot>();
    
	private int plotWidth;
	
	private int plotHeight ;
    
    public enum CONFIG_CRITERIA_PARAMETER { xPath, plotID, condition, name};
    public enum CONFIG_PLOT_PARAMETER { buildCount, title, enabled};    
    public enum CONFIG_SECTIONS_PARAMETER { criterias, plots};
    
    private XLTChartAction chartAction;
   
    @DataBoundConstructor
    public LoadTestBuilder(List <String> qualitiesToPush, String testProperties, String machineHost, String xltConfig, int plotWidth, int plotHeight) 
    {      	
    	this.qualityList = qualitiesToPush;
        this.testProperties = testProperties;
        this.machineHost = machineHost;
        this.xltConfig = xltConfig;        
        this.plotWidth = plotWidth;
        this.plotHeight = plotHeight;
    }

    public List<String> getQualityList() {
        return qualityList;
    }

    public String getTestProperties() {
        return testProperties;
    }
    
    public String getMachineHost() {
        return machineHost;
    }    
    
    public String getXltConfig(){
    	return xltConfig;
    }        
    
    public int getPlotWidth() {
		return plotWidth;
	}
    
    public int getPlotHeight() {
		return plotHeight;
	}
    
    public List<Plot> getEnabledPlots(){
    	ArrayList<Plot> enabledPlots = new ArrayList<Plot>();
    	for (Entry<String, Plot> eachEntry : plots.entrySet()) {
			String plotID = eachEntry.getKey();
			String enabled = null;
			try {
				enabled = config.getJSONObject(CONFIG_SECTIONS_PARAMETER.plots.name()).getJSONObject(plotID).getString(CONFIG_PLOT_PARAMETER.enabled.name());
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			if("yes".equals(enabled)){
				enabledPlots.add(eachEntry.getValue());
			}
		}
    	return enabledPlots;
    }
    
    private List<Plot> getPlots(){
    	return new ArrayList<Plot>(plots.values());
    }
 
    
    private Plot getPlot(String configName){
		try {
			String plotID = getCriteriaConfigValue(configName, CONFIG_CRITERIA_PARAMETER.plotID);
			return plots.get(plotID);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}    	
		return null;
    }
    
    private Plot createPlot(String configName){
		try {
			String plotID = getCriteriaConfigValue(configName, CONFIG_CRITERIA_PARAMETER.plotID);
			if(!plots.containsKey(plotID)){
				String plotCount = getPlotConfigValue(configName, CONFIG_PLOT_PARAMETER.buildCount);
				String title = getPlotConfigValue(configName, CONFIG_PLOT_PARAMETER.title);
				
				Plot plot = new Plot(title,"","XLT",plotCount,"xltPlot"+plotID,"line",false);		
				plot.series = new ArrayList<Series>();
				plots.put(plotID, plot);
			}
			return plots.get(plotID);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}    	
		return null;
    }
    
    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
    	System.out.println("LoadTestBuilder.getProjectActions");
    	
    	ArrayList<Action> actions = new ArrayList<Action>();
    	
    	updateConfig();
    	plots.clear();
    	List<String> names = getConfigNames();
    	for (String name : names) {
    		createPlot(name);
    	}
    	if(!names.isEmpty()){
    		chartAction = new XLTChartAction(project, getEnabledPlots(), plotWidth, plotHeight);
    		actions.add(chartAction);
    	}
    	
    	return actions;
    }
    
    private void updateConfig(){
    	System.out.println("LoadTestBuilder.updateConfig");
    	
		 try {
			config = new JSONObject(xltConfig);
		} catch (JSONException e) {
			//TODO 
			e.printStackTrace();
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
    	if(config == null){
    		return new ArrayList<String>();
    	}
    	
    	JSONObject criteriaSection = config.optJSONObject(CONFIG_SECTIONS_PARAMETER.criterias.name());
    	String[] names = null;
    	if(criteriaSection != null){
    		names = JSONObject.getNames(criteriaSection);
    	}
    	if(names == null || names.length == 0)
    		return new ArrayList<String>();    	
    	return Arrays.asList(names);
    }

    
    private void postTestExecution(AbstractBuild<?,?> build, BuildListener listener){
    	List<String> failedAlerts = new ArrayList<String>();    	

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

							String result =  XPathFactory.newInstance().newXPath().evaluate(conditionPath, dataXml);
							// validate value and collect failed validations then set the build state
							if (result != null && result.isEmpty())
							{
								String value = XPathFactory.newInstance().newXPath().evaluate(xPath, dataXml);;
								failedAlerts.add("Condition \""+name+"\" failed. \n\t Value: \""+value+"\" Condition: \""+condition + "\" Path: \"" + xPath);
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
    		listener.getLogger().println("set failes by alerts");
    		build.setResult(Result.UNSTABLE);
    		for (String eachAlert : failedAlerts) {
    			listener.getLogger().println(eachAlert);
    			System.out.println(eachAlert);
    		}
    	}
    	
    	XltRecorderAction printReportAction = new XltRecorderAction(build, failedAlerts);
    	build.getActions().add(printReportAction);
    }
    
    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
    	System.out.println("LoadTestBuilder.prebuild");
    	return true;
    }    
    
    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
    	System.out.println("LoadTestBuilder.perform");
    	
    	// generate temporary directory for local xlt
    	File destDir = new File(build.getProject().getRootDir(), Integer.toString(build.getNumber()));
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
     
        // if no specific machineHost set -embedded
        if(machineHost.isEmpty())
        {
        	commandLine.add("-embedded");
        }
        else
        {
        	commandLine.add("-Dcom.xceptance.xlt.mastercontroller.agentcontrollers.ac1.url=" + machineHost);
        }
        
        commandLine.add("-auto");
        commandLine.add("-report");
        commandLine.add("-testPropertiesFile");
        commandLine.add(testProperties);
        commandLine.add("-Dcom.xceptance.xlt.mastercontroller.testSuitePath=" + build.getModuleRoot().toString());
        

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
    	listener.getLogger().println(process.waitFor());
    	
    	    	
    	listener.getLogger().println("XLT_FINISHED");
    	
    	
    	// copy xlt-report to build directory
    	File srcXltReport = new File(destDir, "reports");
    	File[] filesReport = srcXltReport.listFiles();
    	File lastFileReport = filesReport[filesReport.length-1];
    	srcXltReport = lastFileReport;
    	File destXltReport = new File(build.getRootDir(), "report");    	
    	FileUtils.copyDirectory(srcXltReport, destXltReport, true); 
    	
    	// copy xlt-result to build directory
    	File srcXltResult = new File(destDir, "results");
    	File[] filesResult = srcXltResult.listFiles();
    	File lastFileResult = filesResult[filesResult.length-1];
    	srcXltReport = lastFileResult;
    	File destXltResult = new File(build.getArtifactsDir(), "result");
    	FileUtils.copyDirectory(srcXltResult, destXltResult, true);

    	// copy xlt-logs to build directory
    	File srcXltLog = new File(destDir, "log");
    	File destXltLog = new File(build.getArtifactsDir(), "log");    	
    	FileUtils.copyDirectory(srcXltLog, destXltLog, true);
    	
    	postTestExecution(build, listener);
    	    	
    	// delete temporary directory with local xlt
    	FileUtils.deleteDirectory(destDir);
    	    	
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
        
        public File getXltConfigFile() throws URISyntaxException{
				return new File(new File(Jenkins.getInstance().getPlugin("Plugin").getWrapper().baseResourceURL.toURI()), "xltConfig.json");				
        }
        
        public String getDefaultXltConfig(){
        	try {
				return new String(Files.readAllBytes(getXltConfigFile().toPath()));
			} catch (URISyntaxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}        	
        	return "No default config file found.";
        }
        
        public int getDefaultPlotWidth(){
        	return 400;
        }
        
        public int getDefaultPlotHeight(){
        	return 250;
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
