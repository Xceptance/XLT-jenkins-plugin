package plugin.Plugin;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.BuildListener;

import hudson.model.Result;
import hudson.model.WorkspaceBrowser;

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
import org.w3c.dom.Document;

import org.xml.sax.SAXException;

import sun.security.krb5.SCDynamicStoreConfig;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;


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


import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;


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
    	XltRecorderAction printReportAction = new XltRecorderAction();
    	printReportAction.setReportPath(build.getProject().getBuildDir().toPath().toString());    	
    	build.getActions().add(printReportAction);

    	List<String> failedAlerts = new ArrayList<String>();
    	
    	try{
			// copy testreport.xml to workspace
        	
    		
    		
    		File destXltReport = new File(build.getModuleRoot().toString() + "/../builds/" + Integer.toString(build.getNumber()) + "/report");
    		File testReportFileXml = new File(destXltReport.toString() + "/testreport.xml");
    		File dataFile = new File(new File(build.getProject().getWorkspace().toURI()),"testreport.xml");        	
        	FileUtils.copyFile(testReportFileXml, dataFile);

        	
	    	
			Document dataXml =DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(dataFile);
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
					
					String value = XPathFactory.newInstance().newXPath().evaluate(valuePath, dataXml);
					
					// validate value and collect failed validations then set the build state
					if (value == null || value.isEmpty())
					{
						failedAlerts.add(name + " " + xPath);
					}
					
					
					Element node = (Element)XPathFactory.newInstance().newXPath().evaluate(valuePath, dataXml, XPathConstants.NODE);
					node.setAttribute("name", name);
					
					Plot plot = plots.get(name);
					plot.series.add(new XMLSeries("testreport.xml", valuePath, "NODE", ""));
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (XPathExpressionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
	    	
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			javax.xml.transform.Result output = new StreamResult(dataFile);
			Source input = new DOMSource(dataXml);
	
			transformer.transform(input, output);
			
    	}catch(IOException e){
    		e.printStackTrace();
    	} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
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
		}

    	//must happen after everything is in place... this will start the data collection for the plot
    	for (Plot eachPlot : getPlots()) {
	        eachPlot.addBuild(build, System.out);
		}
    	
    	if (!failedAlerts.isEmpty())
    	{
    		build.setResult(Result.FAILURE);
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
    	
    	//TODO print error-console in jenkins
    	
    	
    	// waiting until XLT is finished
    	process.waitFor();
    	
    	listener.getLogger().println("XLT_FINISHED");
    	
    	
    	// copy xlt-report to build directory
    	File srcXltReport = new File(build.getModuleRoot().toString() + "/../xlt-iteration-number/" + Integer.toString(build.getNumber()) + "/reports/");
    	File[] files = srcXltReport.listFiles();
    	File lastFile = files[files.length-1];
    	srcXltReport = lastFile;
    	File destXltReport = new File(build.getModuleRoot().toString() + "/../builds/" + Integer.toString(build.getNumber()) + "/report");
    	
    	FileUtils.copyDirectory(srcXltReport, destXltReport, true);
    	

    	postTestExecution(build);    	

    	    	
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

