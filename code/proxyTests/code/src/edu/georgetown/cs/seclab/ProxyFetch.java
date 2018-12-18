package edu.georgetown.cs.seclab;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
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
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;




public class ProxyFetch {

	private static final Logger LOGGER = Logger.getLogger( Prober.class.getName() );
	public static final long TIMESTAMP = new Date().getTime() / 1000;  

	private Map<String, Proxy> proxyDefinitions;
	private List<FileToFetch> urlDefinitions;
	private Namespace options;
	private Prober prober;
	
	private List<Future<String>> workerResults;
	private List<FetchResult> fetchResults;
	
	private int doneCounter;
	private int totalToEvaluate;
	
	
	public ProxyFetch() {
		proxyDefinitions = null;
		urlDefinitions = null;
		workerResults = new Vector<Future<String>>();
		fetchResults =  new Vector<FetchResult>();
		doneCounter = 0;
		totalToEvaluate = 0;
		try {
			prober = new Prober( TIMESTAMP );
		} catch (KeyManagementException | NoSuchAlgorithmException e) {
			LOGGER.severe( "cannot instantiate prober" );
			e.printStackTrace();
			System.exit(1);
		}
	}


	
	private synchronized void increaseDoneCounter() {
		doneCounter += 1;
	}
	

	
	private class ProxyFetchHelper implements Callable<String> {
		private Proxy proxy;

		ProxyFetchHelper( Proxy proxy ) {
			this.proxy = proxy;
		}

		/**
		 * Actually perform the proxy fetch
		 */
		@Override
		public String call() {
			LOGGER.info( "I'm a worker thread.  I'm going to fetch files from " + proxy.name );
			
			proxy.parseName();	// parse IP and port
			
			for (FileToFetch fileToFetch : urlDefinitions) {
				LOGGER.info( "fetching " + fileToFetch.name + " from " + proxy.name );
				FetchResult fetchResult = prober.fetchFile(proxy, fileToFetch, options);
				fetchResults.add(fetchResult);	// vectors are thread-safe, so this is OK
				if (fetchResult.abortRemainder == true) {
					break; 	// don't fetch anything else from this proxy
				}
				try {
					Thread.sleep(1000);	// wait between requests
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}			
			LOGGER.fine( "done fetching files from " + proxy.name );
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
	 * Loads a classified JSON file that describes proxies
	 * @throws JsonIOException
	 * @throws JsonSyntaxException
	 * @throws FileNotFoundException
	 */
	private void loadClassifiedJSONProxyList() { 
		String filename = options.getString("import");
		Gson gson = new Gson();  
		LOGGER.fine("attempting parsing of proxy definitions");
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
	 * Loads a JSON file that describes the urls to fetch
	 * @throws JsonIOException
	 * @throws JsonSyntaxException
	 * @throws FileNotFoundException
	 */
	private void loadJSONURLList() { 
		String filename = options.getString("files");
		Gson gson = new Gson();  
		LOGGER.fine("attempting parsing of URL files list");

		Type type = new TypeToken<List<FileToFetch>>() {}.getType();
		try {
			urlDefinitions = gson.fromJson(new FileReader(filename), type);
			for (FileToFetch fileToFetch : urlDefinitions) {
				LOGGER.info("loaded definition for fetching: " + fileToFetch );
			}
		} catch (JsonIOException | JsonSyntaxException | FileNotFoundException e) {
			LOGGER.severe( "could not parse JSON file" );
			e.printStackTrace();
			System.exit(1);
		}
		LOGGER.fine("parsing complete");
	}
	
	

	/**
	 * Launch the workers that perform the fetching
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
			if (proxy.type == null) {
				LOGGER.info( "skipping " + proxy.name + ", which has no known proxy type" );
				increaseDoneCounter();
				continue;
			}
			
			// launch the thing!
			workerResults.add( 
					executor.submit(new ProxyFetchHelper(proxy)) );
			counter++;
			if ((numToTest > 0) && (counter >= numToTest)) {
				LOGGER.fine( "stopping proxy fetching, since max number of proxies examined" );
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
		ArgumentParser parser = ArgumentParsers.newFor("ProxyFetch").build()
				.defaultHelp(true)
				.description("Fetches files from the proxies");
		parser.addArgument("-l", "--log" )
			.required(true)
			.dest("logfile")
			.help("log file to write to");
		parser.addArgument("-i", "--import")
			.required(true)
			.help("JSON file containing classified proxy instances");
		parser.addArgument("-H", "--thishost")
			.required(true)
			.dest("thishost")
			.help("hostname of this machine (used to construct filenames for downloaded files)");
		parser.addArgument("-f", "--files")
			.required(true)
			.setDefault("files_to_fetch.json")
			.help("JSON file containing list of files to fetch");
		parser.addArgument("-o", "--output")
			.required(true)
			.help("output file (in JSON)");
		parser.addArgument("-d", "--downloads")
			.required(true)
			.help("directory to store suspicious downloads and certificates");
		parser.addArgument("--debug")
			.action(Arguments.storeTrue())
			.help("debug mode");
		parser.addArgument("-p", "--processes")
			.setDefault(92)
			.type(Integer.class)
			.help("number of threads to use (parallelism)");
		parser.addArgument("-t", "--timeout")
			.setDefault("15.0")
			.dest("downloadtimeout")
			.help("how many seconds before a download times out");
		parser.addArgument("-c", "--connecttimeout")
			.setDefault("4.0")
			.help("how many seconds before a connection attempt times out");
		parser.addArgument("-n", "--number")
			.type(Integer.class)
			.setDefault(0)
			.help("number of proxies to fetch files from");
	
		try {
			options = parser.parseArgs(args);
		} catch (ArgumentParserException e) {
			parser.handleError(e);
			System.exit(1);
		}
	}

	

	
	
	/**
	 * Where the fun begins
	 * @param args
	 */
	public static void main(String[] args) {
		System.setProperty("user.timezone", "UTC");

		Instant startInstant = Instant.now();
		
		ProxyFetch proxyFetch = new ProxyFetch();
		proxyFetch.parseArgs(args);
		
		// set log format
		System.setProperty("java.util.logging.SimpleFormatter.format", 
				"%1$tF %1$tT %4$s %2$s: %5$s%6$s%n");
		if (!(proxyFetch.options.getString("logfile").equals("-"))) {
			FileHandler fh;
			try {
				fh = new FileHandler(proxyFetch.options.getString("logfile"));
				LOGGER.addHandler(fh);
				fh.setFormatter(new SimpleFormatter());
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
		if (proxyFetch.options.getBoolean("debug")) {
			LOGGER.setLevel(Level.ALL);
		}
		
		LOGGER.info( "program called with arguments " + proxyFetch.options.toString() );
		LOGGER.info( "logging times are in UTC/GMT; current timestamp is " + TIMESTAMP );
		LOGGER.info( "detected local IP address: " + Utils.getLocalIPAddress() );
		LOGGER.info( "detected local hostname: " + Utils.getLocalHostname() );

		// grab list of URLs to fetch
		proxyFetch.loadJSONURLList();
		
		LOGGER.info( "loading JSON proxy definitions from " + proxyFetch.options.getString("import"));
 
		// import JSON file
		proxyFetch.loadClassifiedJSONProxyList();
		
		// perform the proxy fetching
		proxyFetch.launchWorkers();

		// save the results
		Utils.makeCompressedJSONFile( proxyFetch.options.getString("output"), proxyFetch.fetchResults );
		
		// make sure we didn't miss anything
		Utils.checkForBrokenWorkers( proxyFetch.workerResults );
		
		Instant endInstant = Instant.now();
		long secs = Duration.between(startInstant, endInstant).toMillis() / 1000;
		long mins = Duration.between(startInstant, endInstant).toMinutes();
		double rate = proxyFetch.totalToEvaluate / (double)secs;
		LOGGER.info( "completed pass in " + secs + " seconds (" + mins + " minutes)" );
		LOGGER.info( "processed " + rate + " proxies per second" );
	}

}
