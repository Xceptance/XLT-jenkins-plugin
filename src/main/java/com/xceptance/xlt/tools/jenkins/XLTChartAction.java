package com.xceptance.xlt.tools.jenkins;

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
	private int plotWidth;
	private int plotHeight;
	private String title;
	private String builderID;
	private boolean isPlotVertical;
	private AbstractProject<?, ?> project;
		
	public XLTChartAction(AbstractProject<?, ?> project, List<Plot> plots, int plotWidth, int plotHeight, String title, String builderID, boolean isPlotVertical) {
		this.project = project;
		report = new PlotReport(project, "XLT", new ArrayList<Plot>());
		report.getPlots().addAll(plots);
		this.plotWidth = plotWidth;
		this.plotHeight = plotHeight;
		this.title = title;
		this.builderID = builderID;
		this.isPlotVertical = isPlotVertical;
	}

	public String getDisplayName() {
		return "XLT Chart - "+title; //no link to action page
	}	
	
	public String getIconFileName() {
		return null; //no link to action page
	}
	
	public String getUrlName() {	
		return "xltChart"+builderID;
	}
	
	public AbstractProject<?, ?> getProject() {
		return project;
	}
	
	public String getTitle() {
		return title;
	}
	
	// called from jelly files
	public List<Plot> getPlots(){
		return report.getPlots();
	}
	
	public int getPlotWidth(){
		return plotWidth;
	}
	
	public int getPlotHeight(){
		return plotHeight;
	}	
	
	public boolean isPlotVertical(){
		return isPlotVertical;
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