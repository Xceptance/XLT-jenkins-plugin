package plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;

import javax.servlet.ServletException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import hudson.model.Action;
import hudson.model.AbstractBuild;

public class XltRecorderAction implements Action {

	public String reportPath;
	public AbstractBuild<?, ?> build;
	
	public XltRecorderAction(AbstractBuild<?, ?> build) {
		this.build = build;
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
	
	public void doReport(StaplerRequest request, StaplerResponse response) throws MalformedURLException, ServletException, IOException{
		response.serveFile(request, new File(new File(build.getRootDir(),"report"), request.getRestOfPath()).toURI().toURL());
	}	

}
