package edu.georgetown.cs.seclab;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketAddress;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import net.sourceforge.argparse4j.inf.Namespace;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;


public class Prober  {

	private static final Logger LOGGER = Logger.getLogger( Prober.class.getName() );

	private static String HTTP_URL = "http://spider.cs-georgetown.net";
	private static String HTTPS_URL = "https://security.cs.georgetown.edu";
	
	private static String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/62.0.3202.94 Safari/5OB37.36";

	private long TIMESTAMP;
	
	
	Prober( long timestamp ) throws NoSuchAlgorithmException, KeyManagementException {
		this.TIMESTAMP = timestamp;
		// Install the all-trusting trust manager
		// (taken from http://www.nakov.com/blog/2009/07/16/disable-certificate-validation-in-java-ssl-connections/)
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        // Install the all-trusting host verifier
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
	}


	// taken from http://www.nakov.com/blog/2009/07/16/disable-certificate-validation-in-java-ssl-connections/
	TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager() {
		public java.security.cert.X509Certificate[] getAcceptedIssuers() {
			return null;
		}
		public void checkClientTrusted(X509Certificate[] certs, String authType) { 		}
		public void checkServerTrusted(X509Certificate[] certs, String authType) {		}
	}
	};

	// Create all-trusting host name verifier
	HostnameVerifier allHostsValid = new HostnameVerifier() {
		public boolean verify(String hostname, SSLSession session) {
			return true;
		}
	};

	
	
	/**
	 * checks a certificate to see whether it matches expectations.  if it doesn't, save the
	 * certificate to a file
	 * @param proxy
	 * @param fileToFetch
	 * @param result
	 * @param options
	 * @param hc
	 */
	private void checkCertificate( Proxy proxy, FileToFetch fileToFetch, FetchResult result, Namespace options, HttpsURLConnection hc )
	{
		result.certCompleted = true;
		try {
			Certificate cert = hc.getServerCertificates()[0];
			String retrievedCertHash = Utils.computeSHA256( cert.getEncoded() );
			if (retrievedCertHash.equals(fileToFetch.certhash256)) {
				LOGGER.fine( "retrieved correct certificate in fetch of " + fileToFetch.name + " from " + proxy.name);
				result.certCorrect = true;
			} else {
				LOGGER.fine( "retrieved incorrect certificate in fetch of " + fileToFetch.name + " from " + proxy.name);
				String filename = options.get("downloads") + "/" + TIMESTAMP + "_cert_" + options.getString("thishost") + "_" + proxy.name + "_" + fileToFetch.name + ".cer";
				LOGGER.info( "storing incorrect certificate from fetch of " + fileToFetch.name + " from " + proxy.name + " to " + filename );
				FileOutputStream out = new FileOutputStream(new File(filename));
				out.write(cert.getEncoded());
				out.close();
				result.certCorrect = false;
				result.certFilename = filename;
			}
		} catch (CertificateEncodingException | IOException e) {
			LOGGER.warning("could not compute digest of TLS certificate.  this is likely a programming error: " + e.getMessage() );
			result.certCorrect = false;
			result.certCompleted = true;
			e.printStackTrace();
		}
	}
	
	
	
	
	/**
	 * checks whether a web fetch yielded the expected content.  
	 * if it doesn't, save the content to a file
	 *	
	 * @param proxy
	 * @param fileToFetch
	 * @param result
	 * @param options
	 * @param hc
	 */
	private void checkContent( Proxy proxy, FileToFetch fileToFetch, FetchResult result, Namespace options, byte[] content )
	{
		// does the content match expectations?
		String hash = Utils.computeSHA256(content);
		if (hash.equals(fileToFetch.hash256)) {
			LOGGER.fine( "fetch of " + fileToFetch.name + " from " + proxy.name + " yielded expected content" );
			result.expectedContent = true;
		} else {
			LOGGER.fine( "fetch of " + fileToFetch.name + " from " + proxy.name + " yielded unexpected content" );
			result.expectedContent = false;
			String filename = options.get("downloads") + "/" + TIMESTAMP + "_content_" + options.getString("thishost") + "_" + proxy.name + "_" + fileToFetch.name + ".gz";
			LOGGER.info( "storing unexpected content from fetch of " + fileToFetch.name + " from " + proxy.name + " to " + filename );
			try {
				GZIPOutputStream out = new GZIPOutputStream(new FileOutputStream(new File(filename)));
				out.write(content);
				out.close();	
				result.contentFilename = filename;
			} catch (IOException e) {
				LOGGER.warning( "could not save content to " + filename + "; this is most likely a programming error: " + e.getMessage() );
				e.printStackTrace();
			}
		}
	}
	
	
	/**
	 * Grabs the headers from a URL connection
	 * @param hc
	 * @return
	 */
	private Map<String,String> getHeaders( HttpURLConnection hc ) {
		Map<String,String> result = new HashMap<String,String>(); 
		Map<String,List<String>> headers = hc.getHeaderFields();
		for (String var : headers.keySet() ) {
			String concat = "";
			for (String val : headers.get(var)) {
				if (!concat.equals("")) { concat += '\n'; }
				concat += val;
			}
			result.put(var, concat);
		}
		return result;
	}
	
	
	
	/**
	 * Fetch a file via a proxy
	 * @param proxy the proxy used to perform the fetch
	 * @param fileToFetch the file that we should fetch
	 * @param options the command-line options
	 * @return
	 */
	public FetchResult fetchFile( Proxy proxy, FileToFetch fileToFetch, Namespace options ) {
		return fetchFile(  proxy, fileToFetch, options, false, false );
	}
	

	/**
	 * Fetch a file via a proxy
	 * @param proxy the proxy used to perform the fetch
	 * @param fileToFetch the file that we should fetch
	 * @param options the command-line options
	 * @param skipTests don't perform any checking
	 * @param storeContent store the content in the results
	 * @return
	 */
	public FetchResult fetchFile( Proxy proxy, FileToFetch fileToFetch, Namespace options, boolean skipTests, boolean storeContent ) {
		LOGGER.fine( "fetching " + fileToFetch.name + " from " + proxy.name );
		SocketAddress proxyAddress = new InetSocketAddress(proxy.ip, proxy.port);
		java.net.Proxy.Type netType = proxy.getNetType();
		HttpURLConnection hc = null;
		FetchResult result = new FetchResult(proxy.name, proxy.type, fileToFetch.url);
		boolean https = false;
		
		if (netType == null) {
			LOGGER.warning( "invalid proxy type.  this is likely a programming error." );
			return null;
		}
		
		try {
			java.net.Proxy netProxy = new java.net.Proxy(netType, proxyAddress);
			URL url = new URL(fileToFetch.url);
			
			if (url.getProtocol().equals("http")) {
				hc = (HttpURLConnection)url.openConnection(netProxy);
			} else if (url.getProtocol().equals("https")) {
				hc = (HttpsURLConnection)url.openConnection(netProxy);
				https = true;
			}
			hc.setRequestProperty("User-Agent", Prober.USER_AGENT );
			int connectTimeout = (int)(1000.0 * new Float(options.getString("connecttimeout")).floatValue());
			int downloadTimeout = (int)(1000.0 * new Float(options.getString("downloadtimeout")).floatValue());
			hc.setConnectTimeout(connectTimeout);	// in ms
			hc.setReadTimeout(downloadTimeout);	// also in ms
			
			Instant fetchStart = Instant.now(); // start a timer
			
			hc.connect();	// connect!
			if (https == true && skipTests == false) {			// get cert statistics
				checkCertificate(proxy, fileToFetch, result, options, (HttpsURLConnection)hc);
			}
			
			// if we got here, than yay!
			result.httpStatus = hc.getResponseCode();
			result.reason = hc.getResponseMessage();
			byte[] content = Utils.grabAllBytesFromStream(hc.getInputStream());
			
			// we're done with the fetch -- assuming it succeeded
			Instant fetchEnd = Instant.now();
			if (content != null) {
				result.fetchTime = Duration.between(fetchStart, fetchEnd).toMillis();
				result.completed = true;
				result.contentSizeInBytes = content.length;

				result.headers = getHeaders(hc);

				if (storeContent == true) {
					result.content = new String(content);
				}
				
				// does the content match expectations?
				if (skipTests == false) {
					checkContent(proxy, fileToFetch, result, options, content);
				}
			} else {
				result.completed = false;
				result.reason = "read timeout";
				LOGGER.fine("aborting attempts to use proxy " + proxy.name );
				result.abortRemainder = true;	// once we timeout, let's skip the rest
			}
 			
		} catch (MalformedURLException e) {
			LOGGER.warning( "invalid URL: " + fileToFetch.url );
			return null;
		} catch (IOException e) {
			LOGGER.info( "fetch of " + fileToFetch.name + " from " + proxy.name + " failed: " + e.getMessage() );
			result.reason = e.getMessage();
			result.completed = false;
			if (https == false) {	// if we can't even fetch something over HTTP, let's not try for anything else
				LOGGER.fine("aborting attempts to use proxy " + proxy.name );
				result.abortRemainder = true;
			}
			return result;
		}

		return result;
	}
	
	
	
	/**
	 * determine the type of proxy.  this function will update the ".type" member
	 * variable of proxy
	 * @param proxy the proxy which should be investigated, and updated
	 * @param options the command-line options
	 */
	public void determineProxyType( Proxy proxy, Namespace options ) {
		LOGGER.info( "determining the type of proxy " + proxy.name );
		SocketAddress proxyAddress = new InetSocketAddress(proxy.ip, proxy.port);

		Vector<Object[]> tests = new Vector<Object[]>();
		tests.add( new Object[] { Proxy.PROXY_TYPE_SOCKS, java.net.Proxy.Type.SOCKS, Prober.HTTP_URL } );
		tests.add( new Object[] { Proxy.PROXY_TYPE_CONNECT, java.net.Proxy.Type.HTTP, Prober.HTTPS_URL } );
		tests.add( new Object[] { Proxy.PROXY_TYPE_HTTP, java.net.Proxy.Type.HTTP, Prober.HTTP_URL } );

		URL url;
		
		for (Object[] testDefs : tests ) {
			HttpURLConnection hc = null;

			String stringType = (String)testDefs[0];
			java.net.Proxy.Type netType = (java.net.Proxy.Type)testDefs[1];
			String u = (String)testDefs[2];

			LOGGER.fine( "testing whether " + proxy.name + " is a " + stringType + " proxy" );
			
			java.net.Proxy netProxy = new java.net.Proxy(netType, proxyAddress);
			try {
				url = new URL(u);
				if (url.getProtocol().equals("http")) {
					hc = (HttpURLConnection)url.openConnection(netProxy);
				} else if (url.getProtocol().equals("https")) {
					hc = (HttpsURLConnection)url.openConnection(netProxy);
				}
				hc.setRequestProperty("User-Agent", Prober.USER_AGENT );
				int timeout = (int)(1000.0 * new Float(options.getString("timeout")).floatValue());
				hc.setConnectTimeout(timeout);	// in ms
				hc.setReadTimeout(timeout);
				LOGGER.fine( "attempting to fetch " + u );
				hc.connect();
				int responseCode = hc.getResponseCode();
				LOGGER.info( "!!!proxy " + proxy.name + " is a " + stringType.toUpperCase() + " proxy; response code is " + responseCode );
				proxy.type = stringType;
				return;
			} catch (MalformedURLException e) {
				LOGGER.severe("PROGRAMMING ERROR");
				e.printStackTrace();
				System.exit(1);
			} catch (IOException e) {
				LOGGER.fine( "proxy " + proxy.name + " is not a " + stringType + " proxy" );
			}			
		}
		
	}


}
