package com.xceptance.xlt.tools.jenkins;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import hudson.model.Action;
import hudson.model.AbstractBuild;

public class XltRecorderAction implements Action {

	public String reportPath;
	public AbstractBuild<?, ?> build;
	private List<CriteriaResult> failedAlerts;
	
	public XltRecorderAction(AbstractBuild<?, ?> build, List<CriteriaResult> failedAlerts) {
		this.build = build;
		this.failedAlerts = failedAlerts;
	}

	public String getIconFileName() {
		return null;
	}

	public String getDisplayName() {
		return null;
	}

	public String getUrlName() {
		// TODO Auto-generated method stub
		return "xltResult";
	}
	
	public List<CriteriaResult> getFailedAlerts(){
		List<CriteriaResult> failed = new ArrayList<CriteriaResult>();
		for (CriteriaResult eachAlert : failedAlerts) {
			if(eachAlert.getType() == CriteriaResult.Type.FAILED){
				failed.add(eachAlert);
			}
		}
		return failed;
	}
	
	public List<CriteriaResult> getErrorAlerts(){
		List<CriteriaResult> errors = new ArrayList<CriteriaResult>();
		for (CriteriaResult eachError : failedAlerts) {
			if(eachError.getType() == CriteriaResult.Type.ERROR){
				errors.add(eachError);
			}
		}
		return errors;
	}
	
	public void doReport(StaplerRequest request, StaplerResponse response) throws MalformedURLException, ServletException, IOException{
		response.serveFile(request, new File(new File(build.getRootDir(),"report"), request.getRestOfPath()).toURI().toURL());
	}	

}
