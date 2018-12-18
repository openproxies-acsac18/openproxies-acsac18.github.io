package edu.georgetown.cs.seclab;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class FetchResult {

	public String proxyName = null;
	public String proxyProtocol = null;
	public String url = null;
	public Boolean completed = null;
	public Integer httpStatus = null;
	public Boolean expectedContent = null;
	public Integer contentSizeInBytes = null;
	public String reason = null;
	public Map<String,String> headers = null;
	public String contentFilename = null;
	public Long fetchTime = null;	// in ms
	public Boolean certCorrect = null;
	public Boolean certCompleted = null;
	public String certFilename = null;

	// the actual content retrieved (don't store this in JSON)
	protected transient String content = null;
	
	// if true, then don't bother fetching anything else from this proxy
	protected transient boolean abortRemainder = false;
	
	FetchResult( String proxy ) {
		this.proxyName = proxy;
		this.completed = false;
	}
	
	FetchResult( String proxy, String proxyProtocol, String url ) {
		this.proxyName = proxy;
		this.proxyProtocol = proxyProtocol;
		this.url = url;
		this.completed = false;
	}
	
	public String toString() {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String json = gson.toJson(this);
		return json;
	}

}
