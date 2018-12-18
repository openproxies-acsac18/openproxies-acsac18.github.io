package edu.georgetown.cs.seclab;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.zip.GZIPInputStream;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.Country;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;




public class Classify {

	private static final Logger LOGGER = Logger.getLogger( Prober.class.getName() );
	public static final long TIMESTAMP = new Date().getTime() / 1000;  

	private static String geoCityDB;
	private static String geoCityASN;
	
	private Map<String, Proxy> proxyDefinitions;
	private Namespace options;
	private Prober prober;
	private DatabaseReader geoIPReader = null;
	private DatabaseReader geoASNReader = null;
	
	private List<Future<String>> workerResults;
	
	private int doneCounter;
	private int totalToEvaluate;
	
	
	public Classify() {
		String pattern = "yyyyMMdd";
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
		geoCityDB = "/tmp/city_" + simpleDateFormat.format(new Date()) + ".mmdb";
		geoCityASN = "/tmp/asn_" + simpleDateFormat.format(new Date()) + ".mmdb";
		
		proxyDefinitions = null;
		workerResults = new Vector<Future<String>>();
		doneCounter = 0;
		totalToEvaluate = 0;
		try {
			prober = new Prober( TIMESTAMP );
		} catch (KeyManagementException | NoSuchAlgorithmException e) {
			LOGGER.severe( "cannot instantiate prober" );
			e.printStackTrace();
			System.exit(1);
		}
		initializeGeoIP();
	}


	private void initializeGeoIP() {
		Utils.downloadAndExtract("http://geolite.maxmind.com/download/geoip/database/GeoLite2-City.tar.gz", ".mmdb", Classify.geoCityDB, true );
		Utils.downloadAndExtract("http://geolite.maxmind.com/download/geoip/database/GeoLite2-ASN.tar.gz", ".mmdb", Classify.geoCityASN, true );
	}
	
	
	private synchronized void increaseDoneCounter() {
		doneCounter += 1;
	}
	
	
	private void determineProxyLocation( Proxy proxy ) {
		try {
			if (geoIPReader == null) {
				File database = new File(Classify.geoCityDB);
				geoIPReader = new DatabaseReader.Builder(database).withCache(new CHMCache()).build();
			}
			InetAddress ipAddress = InetAddress.getByName(proxy.ip);
			CityResponse response = geoIPReader.city(ipAddress);
			Country country = response.getCountry();
			proxy.geolite2country = country.getIsoCode();
			LOGGER.info( "determined that " + proxy.name + " is in " + proxy.geolite2country );
		} catch (IOException | GeoIp2Exception e) {
			LOGGER.warning( "could not determine location of proxy " + proxy.name );
			e.printStackTrace();
		}
	}
	
	
	private void determineASN( Proxy proxy ) {
		try {
			if (geoASNReader == null) {
				File database = new File(Classify.geoCityASN);
				geoASNReader = new DatabaseReader.Builder(database).build();
			} 
			InetAddress ipAddress = InetAddress.getByName(proxy.ip);
		    AsnResponse response = geoASNReader.asn(ipAddress);
		    proxy.asn = response.getAutonomousSystemNumber();
		    proxy.asnOrg = response.getAutonomousSystemOrganization();
			LOGGER.info( "determined that " + proxy.name + " is in ASN " + proxy.asn + " (" + proxy.asnOrg + ")" );
		} catch (IOException | GeoIp2Exception e) {
			LOGGER.warning( "could not determine ASN of proxy " + proxy.name );
			e.printStackTrace();
		}
	}
	
	
	
	private class ClassifyHelper implements Callable<String> {
		private Proxy proxy;

		ClassifyHelper( Proxy proxy ) {
			this.proxy = proxy;
		}

		/**
		 * Actually perform the classification
		 */
		@Override
		public String call() {
			LOGGER.info( "I'm a worker thread.  I'm going to classify " + proxy.name );
					
			proxy.parseName();	// parse IP and port
			proxy.dateLastChecked = TIMESTAMP;			
			determineProxyLocation(proxy);
			determineASN(proxy);
			prober.determineProxyType(proxy, options);
			LOGGER.fine( "done classifying " + proxy.name );
			increaseDoneCounter();
			reportProgress();
			return "Success";
		}
		
		
		void reportProgress() {
			if ((doneCounter % 50) == 0) {
				double percent = 100.0 * ((double)doneCounter / (double)totalToEvaluate);
				LOGGER.info( "we are " + percent + "% done" );
			}
		}
		
	};
	
	
	
	/**
	 * Loads an unclassified JSON file that describes proxies
	 * @throws JsonIOException
	 * @throws JsonSyntaxException
	 * @throws FileNotFoundException
	 */
	private void loadUnclassifiedJSONProxyList() { 
		String filename = options.getString("import");
		Gson gson = new Gson();  
		LOGGER.fine("attempting parsing");
		Type type = new TypeToken<Map<String, Proxy>>() {}.getType();
		try {
			proxyDefinitions = gson.fromJson( 
					new InputStreamReader(
							new GZIPInputStream(
									new FileInputStream(
											new File(filename)))), 
					type);
		} catch (JsonIOException | JsonSyntaxException | IOException e) {
			LOGGER.severe( "could not parse JSON file" );
			e.printStackTrace();
			System.exit(1);
		}
		LOGGER.fine("parsing complete");
	}

	

	/**
	 * Perform the actual classifications
	 */
	private void launchWorkers() {
		int numWorkers = options.getInt("processes");
		LOGGER.fine("creating thread pool with " + numWorkers + " workers" );
		ExecutorService executor = Executors.newFixedThreadPool(numWorkers);

		int numToTest = options.getInt("number");
		
		Set<String> proxyKeys = proxyDefinitions.keySet();
		totalToEvaluate = proxyKeys.size();
		if ((numToTest > 0) && (numToTest < totalToEvaluate) ) {
			totalToEvaluate = numToTest;
		}
		
		int counter = 0;
		for (String proxyName : proxyKeys ) {
			Proxy proxy = proxyDefinitions.get(proxyName);
			// launch the thing!
			workerResults.add( 
					executor.submit(new ClassifyHelper(proxy)) );
			counter++;
			if ((numToTest > 0) && (counter >= numToTest)) {
				LOGGER.fine( "stopping classification, since max number of proxies examined" );
				break;
			}
		}
		
		executor.shutdown();
		while (!executor.isTerminated()) { }
		LOGGER.info("Finished all threads");
	}
	
	
	
	/**
	 * Parse command line arguments
	 * @param args
	 * @return the parsed arguments
	 */
	private void parseArgs( String[] args ) {
		ArgumentParser parser = ArgumentParsers.newFor("Classify").build()
				.defaultHelp(true)
				.description("Classifies proxies");
		parser.addArgument("-l", "--log" )
			.required(true)
			.dest("logfile")
			.help("log file to write to");
		parser.addArgument("-i", "--import")
			.required(true)
			.help("JSON file containing unclassified proxy instances");
		parser.addArgument("-o", "--output")
			.required(true)
			.help("output file (in JSON)");
		parser.addArgument("--debug")
			.action(Arguments.storeTrue())
			.help("debug mode");
		parser.addArgument("-p", "--processes")
			.setDefault(64)
			.type(Integer.class)
			.help("number of threads to use (parallelism)");
		parser.addArgument("-t", "--timeout")
			.setDefault("1.5")
			.dest("timeout")
			.help("how many seconds before a timeout");
		parser.addArgument("-n", "--number")
			.type(Integer.class)
			.setDefault(0)
			.help("number of proxies to test");
	
		try {
			options = parser.parseArgs(args);
		} catch (ArgumentParserException e) {
			parser.handleError(e);
			System.exit(1);
		}
	}

	
	
	
	public static void main(String[] args) {
		System.setProperty("user.timezone", "UTC");

		Instant startInstant = Instant.now();
		
		Classify classify = new Classify();
		classify.parseArgs(args);
		
		// set log format
		System.setProperty("java.util.logging.SimpleFormatter.format", 
				"%1$tF %1$tT %4$s %2$s: %5$s%6$s%n");
		if (!(classify.options.getString("logfile").equals("-"))) {
			FileHandler fh;
			try {
				fh = new FileHandler(classify.options.getString("logfile"));
				LOGGER.addHandler(fh);
				fh.setFormatter(new SimpleFormatter());
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
		if (classify.options.getBoolean("debug")) {
			LOGGER.setLevel(Level.ALL);
		}
		
		LOGGER.info( "program called with arguments " + classify.options.toString() );
		LOGGER.info( "logging times are in UTC/GMT; current timestamp is " + TIMESTAMP );
		
		LOGGER.info( "detected local IP address: " + Utils.getLocalIPAddress() );
		LOGGER.info( "detected local hostname: " + Utils.getLocalHostname() );
		
		LOGGER.info( "loading JSON proxy definitions from " + classify.options.getString("import"));
 
		// import JSON file
		classify.loadUnclassifiedJSONProxyList();
		
		// perform the classification
		classify.launchWorkers();

		// save the results
		Utils.makeCompressedJSONFile( classify.options.getString("output"), classify.proxyDefinitions );
		
		// make sure we didn't miss anything
		Utils.checkForBrokenWorkers( classify.workerResults );
		
		Instant endInstant = Instant.now();
		long secs = Duration.between(startInstant, endInstant).toMillis() / 1000;
		long mins = Duration.between(startInstant, endInstant).toMinutes();
		double rate = classify.totalToEvaluate / (double)secs;
		LOGGER.info( "completed pass in " + secs + " seconds (" + mins + " minutes)" );
		LOGGER.info( "processed " + rate + " proxies per second" );
	}


}
