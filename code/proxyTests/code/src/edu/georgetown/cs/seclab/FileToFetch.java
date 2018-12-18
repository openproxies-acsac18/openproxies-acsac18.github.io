package edu.georgetown.cs.seclab;

public class FileToFetch {

	public String name;
	public String hash256;
	public String proto;
	public String url;
	public String certhash256;
	
	public String toString() {
		return "" + name + " @ " + url + " via " + proto;
	}
}
