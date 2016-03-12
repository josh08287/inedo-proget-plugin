package com.inedo.proget.api;

import java.io.File;
import java.io.IOException;

import com.google.common.net.MediaType;
import com.inedo.http.HttpEasy;
import com.inedo.http.LogWriter;
//import java.net.InetSocketAddress;
//import java.net.Proxy;
//import java.util.Arrays;
//import org.apache.http.auth.AuthScope;
//import org.apache.http.auth.UsernamePasswordCredentials;
//import org.apache.http.auth.NTCredentials;
//import org.apache.http.client.CredentialsProvider;
//import org.apache.http.client.HttpClient;
//import org.apache.http.client.config.AuthSchemes;
//import org.apache.http.client.config.RequestConfig;
//import org.apache.http.impl.client.BasicCredentialsProvider;
//import org.apache.http.impl.client.HttpClientBuilder;
//import org.apache.http.impl.client.HttpClients;
//import com.inedo.proget.ConnectionType;
import com.inedo.proget.domain.Feed;
import com.inedo.proget.domain.PackageMetadata;
import com.inedo.proget.domain.ProGetPackage;

/**
 * BuildMaster json api interface 
 * 
 * http://localhost:81/api/json
 * http://inedo.com/support/documentation/proget/reference/universal-feed-api-and-package-format
 * 
 * @author Andrew Sumner
 */
public class ProGet {
	private ProGetConfig config;
	private LogWriter logWriter;
//	private boolean logRequest = true;
	
	public ProGet(ProGetConfig config, LogWriter logWriter) {
		this.config = config;
		this.logWriter = logWriter;
		
		HttpEasy.withDefaults()
			.allowAllHosts()
			.trustAllCertificates()
			.baseUrl(config.url);
	}

	/**
	 * Ensure can call BuildMaster api. An exception will be thrown if cannot.
	 * 
	 * @throws IOException
	 */
	public void checkConnection() throws IOException {
		getFeeds();
	}

	public void upload(String feedName) throws IOException {
		
	}
	
	/** Gets the details of a feed by its name */
	public Feed getFeed(String feedName) throws IOException {
		Feed feed = HttpEasy.request().
				baseURI(config.url).
				path("api/json/Feeds_GetFeed?API_Key={}&Feed_Name={}").
				urlParameters(config.apiKey, feedName).
				get().asJson(Feed.class);
		
		if (feed == null) {
			throw new IOException("Feed " + feedName + " was not found");
		}
		
		return feed;
	}

	/** Get all active feeds */
	public Feed[] getFeeds() throws IOException {
		Feed[] result = HttpEasy.request().
				baseURI(config.url).
				path("api/json/Feeds_GetFeeds?API_Key={}&IncludeInactive_Indicator={}").
				urlParameters(config.apiKey, "N").
				get().asJson(Feed[].class);
		
		return result;
	}
	
	/** Gets the packages in a ProGet feed */
	public ProGetPackage[] getPackageList(String feedId) throws IOException {
		return HttpEasy.request().
				baseURI(config.url).
				path("api/json/ProGetPackages_GetPackages?API_Key={}&Feed_Id={}&IncludeVersions_Indicator=Y").
				urlParameters(config.apiKey, feedId, "Y").
				get().asJson(ProGetPackage[].class);
	}

	/**
	 * 
	 * @param feedName		Required
	 * @param groupName		Required
	 * @param packageName	Required
	 * @param version		Optional - empty string returns latest version
	 * @param toFolder		Folder to save file to
	 * @return	Reference to downloaded file
	 * @throws IOException
	 */
	public File downloadPackage(String feedName, String groupName, String packageName, String version, String toFolder) throws IOException {
		String path = "upack/{�feed-name�}/download/{�group-name�}/{�package-name�}";
		
		if (version == null || version.trim().isEmpty()) {
			version = "";
		} else {
			path += "/{�package-version�}";
		}
		
		return HttpEasy.request().
				baseURI(config.url).
				path(path).
				urlParameters(feedName, groupName, packageName, version).
				withLogWriter(logWriter).
				get().
				downloadFile(toFolder);
	}

	public File createPackage(File sourceFolder, PackageMetadata metadata) throws IOException {
		return new ProGetPackageUtils().createPackage(sourceFolder, metadata);
	}
	
	public void uploadPackage(String feedName, File progetPackage) throws IOException {
		HttpEasy.request().
				baseURI(config.url).
				path("upack/{�feed-name�}/upload").
				urlParameters(feedName).
				data(progetPackage, MediaType.ZIP).
				authorization("Admin", "Admin").
				post();
	}	
}