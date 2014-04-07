package com.xceptance.xlt.tools.jenkins;

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
import hudson.util.FormValidation;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import javax.servlet.ServletException;
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
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;



/**
 * Readout the configuration of XLT in Jenkins, perform load testing and plot build results on project page.
 *
 * 
 * @author Michael Aleithe, Randolph Straub
 */
public class LoadTestBuilder extends Builder {

    private final String testProperties;
    
    private boolean testPropertiesFileAvailable = true;
    
    private final String machineHost;
    
    private String xltConfig;    
    
    private JSONObject config = new JSONObject();

    private final Map<String,Plot> plots = new Hashtable<String, Plot>();
    
	private int plotWidth;
	
	private int plotHeight ;
	
	private String plotTitle;
	
	private String builderID;

	private boolean isPlotVertical;
    
    public enum CONFIG_CRITERIA_PARAMETER { id, xPath, condition, plotID , name };
    public enum CONFIG_PLOT_PARAMETER { id, title, buildCount, enabled };    
    public enum CONFIG_SECTIONS_PARAMETER { criteria, plots };
       
    @DataBoundConstructor
    public LoadTestBuilder(String testProperties, String machineHost, String xltConfig, int plotWidth, int plotHeight, String plotTitle, String builderID, boolean isPlotVertical) 
    {      	
    	if (testProperties==null || testProperties.isEmpty()){
    		testPropertiesFileAvailable = false;
    	}
        this.testProperties = testProperties;
        this.machineHost = machineHost;
        
        if(StringUtils.isBlank(xltConfig)){
        	xltConfig = getDescriptor().getDefaultXltConfig();
        }
        this.xltConfig = xltConfig;   
        
        if(plotWidth == 0){
        	plotWidth = getDescriptor().getDefaultPlotWidth();
        }
        this.plotWidth = plotWidth;
        
        if(plotHeight == 0){
        	plotHeight = getDescriptor().getDefaultPlotHeight();
        }
        this.plotHeight = plotHeight;
        
        if(plotTitle == null){
        	plotTitle = getDescriptor().getDefaultPlotTitle(); 
        }
        this.plotTitle = plotTitle;
        
        if(builderID == null){
        	builderID = UUID.randomUUID().toString();
        }   
        this.builderID = builderID;
        
        this.isPlotVertical = isPlotVertical;
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
    
    public String getPlotTitle() {
		return plotTitle;
	}
    
    public String getBuilderID() {
		return builderID;
	}
    
    public boolean getIsPlotVertical() {
		return isPlotVertical;
	}
    
    public List<Plot> sortPlots(Map<String,Plot> unsortedPlots){
    	List<Plot> sortedPlots = new ArrayList<Plot>();
		try {
			for (String eachID :  getPlotConfigIDs()) {
				if(unsortedPlots.containsKey(eachID)){
					sortedPlots.add(unsortedPlots.get(eachID));
				}
			}			
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return sortedPlots;
    }
    
    public Map<String,Plot> getEnabledPlots(){
    	Map<String,Plot> enabledPlots = new HashMap<String,Plot>();
    	for (Entry<String, Plot> eachEntry : plots.entrySet()) {
			String plotID = eachEntry.getKey();
			String enabled = optPlotConfigValue(plotID, CONFIG_PLOT_PARAMETER.enabled) ;
			
			if(enabled != null && !enabled.trim().replace(" ", "").isEmpty() && "yes".equals(enabled)){
				enabledPlots.put(plotID,eachEntry.getValue());
			}
		}
    	return enabledPlots;
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
				String plotCount = optPlotConfigValue(plotID, CONFIG_PLOT_PARAMETER.buildCount);			
				if(plotCount == null || plotCount.trim().replace(" ", "").isEmpty())
					plotCount = String.valueOf(Integer.MAX_VALUE);
				
				String title = optPlotConfigValue(plotID, CONFIG_PLOT_PARAMETER.title);
				if(title == null)
					title = "";
				
				Plot plot = new Plot(title,"","XLT",plotCount,"xltPlot"+plotID+builderID,"line",false);		
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
    	ArrayList<Action> actions = new ArrayList<Action>();
    	
		try {
			updateConfig();
	    	plots.clear();
			
			List<String> ids = getCriteriaConfigIDs();
	    	for (String name : ids) {
	    		createPlot(name);
	    	}
	    	if(!ids.isEmpty()){
	    		actions.add(new XLTChartAction(project, sortPlots(getEnabledPlots()), plotWidth, plotHeight, plotTitle, builderID, isPlotVertical));
	    	}
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	return actions;
    }
    
    private void updateConfig(){    	
		 try {
			config = new JSONObject(xltConfig);
		} catch (JSONException e) {
			//TODO 
			e.printStackTrace();
		}
    }
    
    public String getCriteriaConfigValue(String configName, CONFIG_CRITERIA_PARAMETER parameter) throws JSONException{
    	JSONArray criteriaArray = config.getJSONArray(CONFIG_SECTIONS_PARAMETER.criteria.name());
    	for (int i = 0; i < criteriaArray.length(); i++) {
    		JSONObject each = criteriaArray.getJSONObject(i);
    		if(configName.equals(each.getString(CONFIG_CRITERIA_PARAMETER.id.name()))){
    			return each.getString(parameter.name());
    		}
		}
    	return null;
    }
    
    public String getCriteriaPlotConfigValue(String configName, CONFIG_PLOT_PARAMETER parameter) throws JSONException{
    	String plotID = getCriteriaConfigValue(configName, CONFIG_CRITERIA_PARAMETER.plotID);
    	return getPlotConfigValue(plotID, parameter);
    }    
    
    public String optPlotConfigValue(String plotID, CONFIG_PLOT_PARAMETER parameter){
    	try {
			return getPlotConfigValue(plotID, parameter);
		} catch (JSONException e) {
			return null;
		}
    }
    
    public String getPlotConfigValue(String plotID, CONFIG_PLOT_PARAMETER parameter) throws JSONException{
    	JSONArray plotsArray = config.getJSONArray(CONFIG_SECTIONS_PARAMETER.plots.name());
    	for (int i = 0; i < plotsArray.length(); i++) {
			JSONObject each = plotsArray.getJSONObject(i);
			if(plotID.equals(each.getString(CONFIG_PLOT_PARAMETER.id.name()))){
				return each.getString(parameter.name());
			}
		}
    	return null;
    }
    
    public ArrayList<String> getCriteriaConfigIDs() throws JSONException{    	
    	ArrayList<String> criteriaList = new ArrayList<String>();
    	
    	if(config != null){
	    	JSONArray criteriaSection = config.getJSONArray(CONFIG_SECTIONS_PARAMETER.criteria.name());
	    	for (int i = 0; i < criteriaSection.length(); i++) {
	    		JSONObject each = criteriaSection.getJSONObject(i);
	    		criteriaList.add(each.getString(CONFIG_CRITERIA_PARAMETER.id.name()));
			}
    	}
    	return criteriaList;
    }
    
    public ArrayList<String> getPlotConfigIDs() throws JSONException{
    	ArrayList<String> plotIDs = new ArrayList<String>();
    	
    	if(config != null){
	    	JSONArray plotSection = config.getJSONArray(CONFIG_SECTIONS_PARAMETER.plots.name());
	    	for (int i = 0; i < plotSection.length(); i++) {
	    		JSONObject each = plotSection.getJSONObject(i);
	    		plotIDs.add(each.getString(CONFIG_PLOT_PARAMETER.id.name()));
			}
    	}
    	return plotIDs;
    }

    
    private void postTestExecution(AbstractBuild<?,?> build, BuildListener listener){
    	List<CriteriaResult> failedAlerts = new ArrayList<CriteriaResult>();    	
		listener.getLogger().println();
		
    	File dataFile = null;
    	try{
			// copy testreport.xml to workspace
    		File testReportFileXml = new File(build.getRootDir(), "report/testreport.xml");
    		dataFile = new File(new File(build.getModuleRoot().toURI()),"testreport.xml");    		
    		if(!testReportFileXml.exists()){
    			CriteriaResult criteriaResult = CriteriaResult.error("No test data found at: "+testReportFileXml.getAbsolutePath());    			
				failedAlerts.add(criteriaResult);
				listener.getLogger().println(criteriaResult.getLogMessage());
    		}else{
    			FileUtils.copyFile(testReportFileXml, dataFile);
	    		if(!dataFile.exists()){
	    			CriteriaResult criteriaResult = CriteriaResult.error("Expected copy of test data at: "+dataFile.getAbsolutePath());
					failedAlerts.add(criteriaResult);
					listener.getLogger().println(criteriaResult.getLogMessage());
	    		}else{		        	
					Document dataXml =DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(dataFile);
					
			    	try {
						List<String> criteriaIDs = getCriteriaConfigIDs();
						for (String eachID : criteriaIDs) {
							listener.getLogger().println();
							listener.getLogger().println("Start processiong. Criteria:\""+eachID+"\"");
							String xPath = null;
							String condition = null;
							try {
								xPath = getCriteriaConfigValue(eachID, CONFIG_CRITERIA_PARAMETER.xPath);
								condition = getCriteriaConfigValue(eachID, CONFIG_CRITERIA_PARAMETER.condition);	
								if(xPath == null){
					    			CriteriaResult criteriaResult = CriteriaResult.error("No xPath for Criteria");
					    			criteriaResult.setCriteriaID(eachID);
									failedAlerts.add(criteriaResult);
									listener.getLogger().println(criteriaResult.getLogMessage());
									continue;
								}
								if(condition == null){
									condition = "";
								}								
								String conditionPath = xPath+condition;
								
								Element node = (Element)XPathFactory.newInstance().newXPath().evaluate(xPath, dataXml, XPathConstants.NODE);
								if(node == null){
					    			CriteriaResult criteriaResult = CriteriaResult.error("No result found for xPath");
					    			criteriaResult.setCriteriaID(eachID);
					    			criteriaResult.setXPath(xPath);
									failedAlerts.add(criteriaResult);
									listener.getLogger().println(criteriaResult.getLogMessage());
									continue;
								}

								String label = getCriteriaConfigValue(eachID, CONFIG_CRITERIA_PARAMETER.name);
								if(label == null || label.trim().replace(" ", "").isEmpty()){
									label = eachID;
								}
								node.setAttribute("name", label);							
								
								Double number = (Double)XPathFactory.newInstance().newXPath().evaluate(xPath, dataXml, XPathConstants.NUMBER);
								if(!number.isNaN()){
									Plot plot = getPlot(eachID);
									if(plot != null){
										listener.getLogger().println("Add plot value. Criteria \""+eachID+"\"\t Value: \""+number+ "\"\t Path: \"" + xPath);
										plot.series.add(new XMLSeries("testreport.xml", xPath, "NODE", ""));
									}	
								}else{
									listener.getLogger().println("Plot value is not a number. Criteria \""+eachID+"\"\t Value: \""+number+ "\"\t Path: \"" + xPath);
								}

								if(!condition.isEmpty()){
									//test the condition
									listener.getLogger().println("Test condition. Criteria: \""+eachID+ "\"\t Path: \"" + xPath+"\t Condition: \""+condition + "\"");
									Node result =  (Node)XPathFactory.newInstance().newXPath().evaluate(conditionPath, dataXml, XPathConstants.NODE);
									if (result == null)
									{
										String value = XPathFactory.newInstance().newXPath().evaluate(xPath, dataXml);
						    			CriteriaResult criteriaResult = CriteriaResult.failed("Condition failed");
						    			criteriaResult.setCriteriaID(eachID);
						    			criteriaResult.setValue(value);
						    			criteriaResult.setCondition(condition);
						    			criteriaResult.setXPath(xPath);
										failedAlerts.add(criteriaResult);
										listener.getLogger().println(criteriaResult.getLogMessage());
										continue;
									}							
								}else{
									listener.getLogger().println("No condition to test. Criteria: \""+eachID+"\"");
								}
							} catch (JSONException e) {
				    			CriteriaResult criteriaResult = CriteriaResult.error("Failed to get parameter from configuration");
				    			criteriaResult.setCriteriaID(eachID);
				    			criteriaResult.setExceptionMessage(e.getMessage());
				    			criteriaResult.setCauseMessage(e.getCause() != null ? e.getCause().getMessage() : null);
								failedAlerts.add(criteriaResult);
								listener.getLogger().println(criteriaResult.getLogMessage());
							} catch (XPathExpressionException e) {
				    			CriteriaResult criteriaResult = CriteriaResult.error("Incorrect xPath expression");
				    			criteriaResult.setCriteriaID(eachID);
				    			criteriaResult.setCondition(condition);
				    			criteriaResult.setXPath(xPath);
				    			criteriaResult.setExceptionMessage(e.getMessage());
				    			criteriaResult.setCauseMessage(e.getCause() != null ? e.getCause().getMessage() : null);
								failedAlerts.add(criteriaResult);
								listener.getLogger().println(criteriaResult.getLogMessage());
							}							
						}
					}catch (JSONException e) {
						//we have no citeria section or one criteria has no id defined...
						e.printStackTrace();
					}
			    	
					Transformer transformer = TransformerFactory.newInstance().newTransformer();
					javax.xml.transform.Result output = new StreamResult(dataFile);
					Source input = new DOMSource(dataXml);
			
					transformer.transform(input, output);
					
			    	//must happen after everything is in place... this will start the data collection for the plot
			    	for (Plot eachPlot : plots.values()) {
				        eachPlot.addBuild(build, listener.getLogger());
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
    		listener.getLogger().println();
    		listener.getLogger().println("Set state to UNSTABLE by alerts.");
    		build.setResult(Result.UNSTABLE);
    		for (CriteriaResult eachAlert : failedAlerts) {
    			listener.getLogger().println(eachAlert.getLogMessage());
    		}
    		listener.getLogger().println();
    	}
    	
    	XltRecorderAction printReportAction = new XltRecorderAction(build, failedAlerts);
    	build.getActions().add(printReportAction);
    }
    
    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
    	return true;
    }    
    
    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
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
        
        if (testPropertiesFileAvailable==true)
        {
        	commandLine.add("-testPropertiesFile");
            commandLine.add(testProperties);
            	
        }
        
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
    	listener.getLogger().println("mastercontroller return code: " + process.waitFor());
    	
    	    	
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
				return new File(new File(Jenkins.getInstance().getPlugin("xlt-jenkins").getWrapper().baseResourceURL.toURI()), "xltConfig.json");				
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
        
        public String getDefaultPlotTitle(){
        	return "";
        }
        
        public boolean getDefaultIsPlotVertical(){
        	return false;
        }

        
        /**
         * Performs on-the-fly validation of the form field 'testProperties'.
         */
        public FormValidation doCheckTestProperties(@QueryParameter String value)
        		throws IOException, ServletException{
        	//TODO warning if empty that test.properties is used        	
        	
        	return FormValidation.ok();
        } 
        
        /**
         * Performs on-the-fly validation of the form field 'machineHost'.
         */
        public FormValidation doCheckMachineHost(@QueryParameter String value)
        		throws IOException, ServletException{
        	if (value.isEmpty())
        		return FormValidation.ok("-embedded is enabled");
        	//TODO validate if there is the right syntax. <protocol>://<hostname>:<port>        	
        	//TODO validate port number
        	//TODO validate protocol
        	
        	return FormValidation.ok();
        }
        
        /**
         * Performs on-the-fly validation of the form field 'parsers'.
         */
        public FormValidation doCheckXltConfig(@QueryParameter String value){
        	if(value == null || value.trim().replace(" ", "").isEmpty())
        		return FormValidation.ok("The default config will be used for empty field.");

        	JSONObject validConfig;
        	try {
				validConfig = new JSONObject(value);
			} catch (JSONException e) {
				return FormValidation.error(e, "Invalid JSON");
			} 
        	
        	try{
        		List<String> criteriaIDs = new ArrayList<String>();
        		Map<String, String> criteriaPlotIDs = new HashMap<String, String>();
	        	JSONArray validCriterias = validConfig.getJSONArray(CONFIG_SECTIONS_PARAMETER.criteria.name());
				for (int i = 0; i < validCriterias.length(); i++) {
					JSONObject eachCriteria = validCriterias.optJSONObject(i);
					String id = null;
					try{
						id = eachCriteria.getString(CONFIG_CRITERIA_PARAMETER.id.name());
						if(id == null || id.trim().replace(" ", "").isEmpty())
							return FormValidation.error("Criteria id is empty. (criteria index: "+i+")");
						if(criteriaIDs.contains(id))
							return FormValidation.error("Criteria id already exists. (criteria id: "+id+")");
						criteriaIDs.add(id);
						
						String path = eachCriteria.getString(CONFIG_CRITERIA_PARAMETER.xPath.name());
						if(path == null || path.trim().replace(" ", "").isEmpty())
							return FormValidation.error("Criteria xPath is empty. (criteria id: "+id+")");
						
						try {
							XPathFactory.newInstance().newXPath().compile(path);
						} catch (XPathExpressionException e) {
							return FormValidation.error(e, "Invalid xPath. (criteria id:"+id+")");
						}
						
						String condition = eachCriteria.getString(CONFIG_CRITERIA_PARAMETER.condition.name());
						if(condition != null && !condition.trim().replace(" ", "").isEmpty()){
							try {
								XPathFactory.newInstance().newXPath().compile(path+condition);
							} catch (XPathExpressionException e) {
								return FormValidation.error(e, "Condition does not form a valid xPath. (criteria id:"+id+")");
							}
						}							
						
						String criteriaPlotID = eachCriteria.getString(CONFIG_CRITERIA_PARAMETER.plotID.name());
						if(criteriaPlotID != null && !criteriaPlotID.trim().replace(" ", "").isEmpty()){
							criteriaPlotIDs.put(id,criteriaPlotID);
						}
						
						eachCriteria.getString(CONFIG_CRITERIA_PARAMETER.name.name());
					}catch(JSONException e){
						return FormValidation.error(e, "Missing criteria JSON section. (criteria index: "+i+ " "+ (id != null ? ("criteria id: "+ id) : "")+")");
					}
				}        	
			
        		List<String> plotIDs = new ArrayList<String>();
				JSONArray validPlots = validConfig.getJSONArray(CONFIG_SECTIONS_PARAMETER.plots.name());
				for (int i = 0; i < validPlots.length(); i++) {
					JSONObject eachPlot = validPlots.getJSONObject(i);
					String id = null;
					try{
						eachPlot.getString(CONFIG_PLOT_PARAMETER.id.name());
						id = eachPlot.getString(CONFIG_PLOT_PARAMETER.id.name());
						if(id == null || id.trim().replace(" ", "").isEmpty())
							return FormValidation.error("Plot id is empty. (plot index: "+i+")");
						if(plotIDs.contains(id))
							return FormValidation.error("Plot id already exists. (plot id: "+id+")");
						plotIDs.add(id);
	
						eachPlot.getString(CONFIG_PLOT_PARAMETER.title.name());
						String buildCount = eachPlot.getString(CONFIG_PLOT_PARAMETER.buildCount.name());
						if(buildCount != null && !buildCount.trim().replace(" ", "").isEmpty()){
				        	double number = -1;
				        	try {
				        		number = Double.valueOf(buildCount);
							} catch (NumberFormatException e) {
								return FormValidation.error("Plot buildCount is not a number. (plot id: "+id+")");
							}
				        	if(number < 1){
								return FormValidation.error("Plot buildCount must be positive. (plot id: "+id+")");
				        	}
				        	if(number != (int)number){
								return FormValidation.error("Plot buildCount is a dezimal number. (plot id: "+id+")");
				        	}
						}
						String plotEnabled = eachPlot.getString(CONFIG_PLOT_PARAMETER.enabled.name());
						if(plotEnabled != null && !plotEnabled.trim().replace(" ", "").isEmpty()){
							if(!("yes".equals(plotEnabled) || "no".equals(plotEnabled))){
								return FormValidation.error("Invalid value for plot enabled. Only yes or no is allowed. (plot id: "+id+")");
							}
						}
					}catch(JSONException e){
						return FormValidation.error(e, "Missing plot JSON section. (plot index: "+i+ " "+ (id != null ? ("plot id: "+ id) : "")+")");
					}
				}
				
				for (Entry<String, String> eachEntry : criteriaPlotIDs.entrySet()) {
					if(!plotIDs.contains(eachEntry.getValue())){
						return FormValidation.error("Missing plot config for plot id:"+eachEntry.getValue()+" at criteria id: "+eachEntry.getKey()+".");
					}
				}
        	}catch(JSONException e){
        		return FormValidation.error(e, "Missing JSON section");
        	}        	
        	        	
        	return FormValidation.ok();
        } 
        
        public FormValidation doCheckPlotWidth(@QueryParameter String value){
        	if(value == null || value.trim().replace(" ", "").isEmpty())
        		return FormValidation.ok("The default width will be used for empty field. ("+getDefaultPlotWidth()+")");
        	   
        	double number = -1;
        	try {
        		number = Double.valueOf(value);
			} catch (NumberFormatException e) {
				return FormValidation.error("Please enter a valid number for width.");
			}
        	if(number < 1){
        		return FormValidation.error("Please enter a valid positive number for width.");
        	}
        	if(number != (int)number){
        		return FormValidation.warning("Dezimal number for width. Width will be "+(int)number); 
        	}
        	return FormValidation.ok();
        }    
        
        public FormValidation doCheckPlotHeight(@QueryParameter String value){
        	if(value == null || value.trim().replace(" ", "").isEmpty())
        		return FormValidation.ok("The default height will be used for empty field. ("+getDefaultPlotHeight()+")");
        	   
        	double number = -1;
        	try {
        		number = Double.valueOf(value);
			} catch (NumberFormatException e) {
				return FormValidation.error("Please enter a valid number for height.");
			}
        	if(number < 1){
        		return FormValidation.error("Please enter a valid positive number for height.");
        	}
        	if(number != (int)number){
        		return FormValidation.warning("Dezimal number for height. Height will be "+(int)number); 
        	}
        	return FormValidation.ok();
        }    
        
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
