package plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import hudson.model.Action;
import hudson.model.AbstractProject;
import hudson.plugins.plot.Plot;
import hudson.plugins.plot.PlotReport;

public class XLTChartAction implements Action{
	
	private final PlotReport report;
		
	public XLTChartAction(AbstractProject<?, ?> project, List<Plot> plots) {		
		report = new PlotReport(project, "XLT", new ArrayList<Plot>());
		report.getPlots().addAll(plots);
	}

	public String getDisplayName() {
		return null; //no link to action page
	}	
	
	public String getIconFileName() {
		return null; //no link to action page
	}
	
	public String getUrlName() {	
		return "xltChart";
	}
	
	// called from jelly files
	public List<Plot> getPlots(){
		return report.getPlots();
	}
		
	// called from jelly files
	public void doGetPlot(StaplerRequest req, StaplerResponse rsp) {		
		report.doGetPlot(req, rsp);
	}
	
	// called from jelly files
	public void doGetPlotMap(StaplerRequest req, StaplerResponse rsp) {
		report.doGetPlotMap(req, rsp);
	}		
}
