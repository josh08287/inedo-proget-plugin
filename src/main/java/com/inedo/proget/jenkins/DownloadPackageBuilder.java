package com.inedo.proget.jenkins;

import hudson.Launcher;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import jenkins.security.MasterToSlaveCallable;
import hudson.tasks.BuildStepDescriptor;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.inedo.proget.api.ProGetApi;
import com.inedo.proget.api.ProGetConfig;
import com.inedo.proget.api.ProGetPackager;
import com.inedo.proget.domain.Feed;
import com.inedo.proget.domain.ProGetPackage;
import com.inedo.proget.domain.Version;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

/**
 * Downloads a universal package from ProGet.
 *
 * @author Andrew Sumner
 */
public class DownloadPackageBuilder extends Builder {
	private final String feedName;
	private final String groupName;
	private final String packageName;
	private final String version;
	private final String downloadFormat;
	private final String downloadFolder;
	
	// Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
	@DataBoundConstructor
	public DownloadPackageBuilder(String feedName, String groupName, String packageName, String version, String downloadFormat, String downloadFolder) {
		this.feedName = feedName;
		this.groupName = groupName;
		this.packageName = packageName;
		this.version = version;
		this.downloadFormat = downloadFormat;
		this.downloadFolder = downloadFolder;
	}
	
	public String getFeedName() {
		return feedName;
	}
	
	public String getGroupName() {
		return groupName;
	}
	
	public String getPackageName() {
		return packageName;
	}
	
	public String getVersion() {
		return version;
	}
	
	public String getDownloadFormat() {
		return downloadFormat;
	}
	
	public String getDownloadFolder() {
		return downloadFolder;
	}
	
	@Override
    public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) throws IOException, InterruptedException {
        JenkinsLogWriter logWriter = new JenkinsLogWriter(listener);
        
        if (!GlobalConfig.isProGetRequiredFieldsConfigured(false)) {
            logWriter.error("Please configure ProGet Plugin global settings");
            return false;
        }

        JenkinsEnvrionmentHelper helper = new JenkinsEnvrionmentHelper(build, listener);

        ProGetConfig config = GlobalConfig.getProGetConfig();
        
        String downloadTo = helper.expandVariable(downloadFolder);
        logWriter.info("Download package to " + new File(downloadTo).getAbsolutePath());

        String downloaded = launcher.getChannel().call(new GetPackage(listener, config, feedName, groupName, packageName, version, downloadFormat, downloadTo));

        if (!downloaded.isEmpty()) {
            helper.injectEnvrionmentVariable("PROGET_FILE", downloaded);
        }
        
        return true;
	}

	// Define what should be run on the slave for this build
	private static class GetPackage extends MasterToSlaveCallable<String, IOException> {
	    private final BuildListener listener;
	    private ProGetConfig config;
	    private final String feedName;
	    private final String groupName;
	    private final String packageName;
	    private final String version;
	    private final String downloadFormat;
	    private final String downloadFolder;
	    
	    public GetPackage(final BuildListener listener, ProGetConfig config, String feedName, String groupName, String packageName, String version, String downloadFormat, String downloadFolder) {
	        this.listener = listener;
	        this.config = config;
	        this.feedName = feedName;
	        this.groupName = groupName;
	        this.packageName = packageName;
	        this.version = version;
	        this.downloadFormat = downloadFormat;
	        this.downloadFolder = downloadFolder;
	    }

        public String call() throws IOException {
            JenkinsLogWriter logWriter = new JenkinsLogWriter(listener);
            
            ProGetApi proget = new ProGetApi(config, logWriter);
            DownloadFormat format = DownloadFormat.fromFormat(downloadFormat);
            
            logWriter.info(this.feedName);
            logWriter.info(this.groupName);
            logWriter.info(this.packageName);
            logWriter.info(this.version);
            logWriter.info(this.downloadFormat);
            logWriter.info(this.downloadFolder);
            logWriter.info(format.toString());
            
            File downloaded = proget.downloadPackage(feedName, groupName, packageName, version, downloadFolder, format);
                    
            if (format == DownloadFormat.EXTRACT_CONTENT) {
                logWriter.info("Unpack " + downloaded.getName());
                ProGetPackager.unpackContent(downloaded);
                downloaded.delete();
            } else {
                return downloaded.getName();
            }
            
            return "";
        }

        private static final long serialVersionUID = 1L;
    }
	
	
	@Extension
	// This indicates to Jenkins that this is an implementation of an extension point.
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
		private ProGetApi proget = null;
		private String connectionError = "";
		private String connectionWarning = "";
		private Boolean isProGetAvailable = null;
		
		public DescriptorImpl() {
			super(DownloadPackageBuilder.class);
		}
		
		public String defaultFolder() {
			return "${WORKSPACE}";
		}
		
		@SuppressWarnings("rawtypes")
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			// Indicates that this builder can be used with all kinds of project types
			return true;
		}

		@Override
		public String getDisplayName() {
			return "ProGet Package Download";
		}
	
		public boolean isConnectionError() {
			getIsProGetAvailable();
			
			return !connectionError.isEmpty();
		}
		
		public String getConnectionError() {
    		return connectionError;
    	}
		
		public boolean isConnectionWarning() {
			getIsProGetAvailable();
			
			return !connectionWarning.isEmpty();
		}
		
		public String getConnectionWarning() {
			return connectionWarning;
    	}
		
		/**
    	 * Check if can connect to ProGet - if not prevent any more calls
    	 */
    	public boolean getIsProGetAvailable() {
    		if (isProGetAvailable != null) {
    			return isProGetAvailable;
    		}
    		
			if (!GlobalConfig.isProGetRequiredFieldsConfigured(false)) {
				connectionError = "Please configure ProGet Plugin global settings";
				isProGetAvailable = false;
				return false;
			}
			
			proget = new ProGetApi();

			try {
            	proget.canConnect();
			} catch (Exception ex) {
            	connectionError = "Unable to connect to Proget, please check the global settings: " + ex.getClass().getName() + " - " + ex.getMessage();
            	isProGetAvailable = false;
            	return false;
            }   

			if (!GlobalConfig.isProGetApiKeyFieldConfigured()) {
				connectionWarning = "The ApiKey has not been configured in global settings, some features have been disabled.";
				isProGetAvailable = false;
			} else {
	    		connectionError = "";
	        	isProGetAvailable = true;
			}

        	return isProGetAvailable;
    	}
    	
    	public ListBoxModel doFillFeedNameItems() throws IOException {
        	if (!getIsProGetAvailable()) {
        		return null;
        	}
        	
        	Set<String> set = new TreeSet<String>();
        	ListBoxModel items = new ListBoxModel();
        	Feed[] feeds = proget.getFeeds();
            
        	for (Feed feed : feeds) {
        		set.add(feed.Feed_Name);
			}
        	
        	for (String value : set) {
        		items.add(value);
			}
        	
            return items;
        }
    	
    	public ListBoxModel doFillGroupNameItems(@QueryParameter String feedName) throws IOException {
        	if (!getIsProGetAvailable()) {
        		return null;
        	}
        	
        	Set<String> set = new TreeSet<String>();
        	ListBoxModel items = new ListBoxModel();
        	Feed feed = proget.getFeed(feedName);
    		ProGetPackage[] packages = proget.getPackages(feed.Feed_Id);
    		
        	for (ProGetPackage pkg : packages) {
        		set.add(pkg.Group_Name);
			}
        	
        	items.add("");
        	for (String value : set) {
        		items.add(value);
			}
        	
            return items;
        }
    	
    	public ListBoxModel doFillPackageNameItems(@QueryParameter String feedName, @QueryParameter String groupName) throws IOException {
        	if (!getIsProGetAvailable()) {
        		return null;
        	}
        	
        	Set<String> set = new TreeSet<String>();
        	ListBoxModel items = new ListBoxModel();
        	Feed feed = proget.getFeed(feedName);
    		ProGetPackage[] packages = proget.getPackages(feed.Feed_Id);
    		
        	for (ProGetPackage pkg : packages) {
        		if (pkg.Group_Name.equals(groupName)) {
        			set.add(pkg.Package_Name);
        		}
			}
        	
        	items.add("");
        	for (String value : set) {
        		items.add(value);
			}
        	
            return items;
        }
    	
    	public ListBoxModel doFillVersionItems(@QueryParameter String feedName, @QueryParameter String groupName, @QueryParameter String packageName) throws IOException {
        	if (!getIsProGetAvailable()) {
        		return null;
        	}
        	
        	Set<String> set = new TreeSet<String>();
        	ListBoxModel items = new ListBoxModel();
        	Feed feed = proget.getFeed(feedName);
    		Version[] versions = proget.getPackageVersions(feed.Feed_Id, groupName, packageName);
        	
    		
    		for (Version version : versions) {
        		set.add(version.Version_Text);
			}
        	
    		items.add("");
    		items.add("Latest");
        	for (String value : set) {
        		items.add(value);
			}
        	
            return items;
        }
    	
    	public ListBoxModel doFillDownloadFormatItems() throws IOException {
        	ListBoxModel items = new ListBoxModel();
        	
        	for (DownloadFormat format : DownloadFormat.values()) {
        		items.add(format.getDisplay(), format.getFormat());
			}
        	
            return items;
        }
	}
}
