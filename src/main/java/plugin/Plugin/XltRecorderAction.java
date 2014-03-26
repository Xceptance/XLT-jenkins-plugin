package plugin.Plugin;

import hudson.model.Action;

public class XltRecorderAction implements Action {

	public String reportPath;
	
	public XltRecorderAction() {
	
		
	}

	public String getIconFileName() {
		// TODO Auto-generated method stub
		return null;
	}

	public String getDisplayName() {
		// TODO Auto-generated method stub
		return "xltrecorderaction";
	}

	public String getUrlName() {
		// TODO Auto-generated method stub
		return "XltRecoredrAction";
	}
	
	public String getReportPath() {
		return reportPath;
	}

	public void setReportPath(String string) {
		reportPath = string;		
	}

}
