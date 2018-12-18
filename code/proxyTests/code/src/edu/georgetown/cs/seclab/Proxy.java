package edu.georgetown.cs.seclab;

import java.util.Vector;

public class Proxy {

	// note that anything marked "transient" won't be serialized by GSON to a JSON
	
	public static transient final String PROXY_TYPE_SOCKS = "socks";
	public static transient final String PROXY_TYPE_HTTP = "http";
	public static transient final String PROXY_TYPE_CONNECT = "connect";
	
	protected transient String ip = null;
	protected transient int port = 0;
	
	public String name;
	public Vector<String> sources;
	public String type;	
	public String geolite2country;
	public long dateLastChecked;
	public Integer asn;
	public String asnOrg;
	
	
	protected void parseName() {
		String[] parts = name.split(":");
		ip = parts[0];
		port = Integer.parseInt(parts[1]);
	}
	

	protected java.net.Proxy.Type getNetType() {
		if (type.equals(Proxy.PROXY_TYPE_CONNECT)) {
			return java.net.Proxy.Type.HTTP;
		} else if (type.equals(Proxy.PROXY_TYPE_HTTP)) {
			return java.net.Proxy.Type.HTTP;
		} else if (type.equals(Proxy.PROXY_TYPE_SOCKS)) {
			return java.net.Proxy.Type.SOCKS;
		} else {
			return null;
		}
	}
	
	
	public String toString() {
		return name;
	}
	
	
}
