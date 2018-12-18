package edu.georgetown.cs.seclab;



import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Vector;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;




public class ManualFetch {

	private static final Logger LOGGER = Logger.getLogger( Prober.class.getName() );
	public static final long TIMESTAMP = new Date().getTime() / 1000;  
	private Namespace options;

	private Prober prober;
	
	
	public ManualFetch() throws KeyManagementException, NoSuchAlgorithmException {
		prober = new Prober( TIMESTAMP );
	}


	
	
	/**
	 * Parse command line arguments
	 * @param args
	 * @return the parsed arguments
	 */
	private void parseArgs( String[] args ) {
		ArgumentParser parser = ArgumentParsers.newFor("ManualFetch").build()
				.defaultHelp(true)
				.description("Fetches files from the proxies");
		parser.addArgument("-l", "--log" )
			.required(true)
			.dest("logfile")
			.help("log file to write to");
		parser.addArgument("-P", "--proxy")
			.required(true)
			.dest("proxy")
			.help("proxy to test");
		parser.addArgument("-p", "--port")
			.required(true)
			.dest("port")
			.help("port number");
		parser.addArgument("-T", "--type")
			.required(true)
			.dest("type")
			.choices(Proxy.PROXY_TYPE_SOCKS,Proxy.PROXY_TYPE_CONNECT,Proxy.PROXY_TYPE_HTTP)
			.help("type of proxy");
		parser.addArgument("-u", "--url")
			.setDefault("http://spider.cs-georgetown.net")
			.dest("url")
			.help("url to retrieve");
		parser.addArgument("-t", "--timeout")
			.setDefault("15.0")
			.dest("downloadtimeout")
			.help("how many seconds before a download times out");
		parser.addArgument("-c", "--connecttimeout")
			.setDefault("6.0")
			.help("how many seconds before a connection attempt times out");
		
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
	 * @throws NoSuchAlgorithmException 
	 * @throws KeyManagementException 
	 */
	public static void main(String[] args) throws KeyManagementException, NoSuchAlgorithmException {
		System.setProperty("user.timezone", "UTC");
		
		ManualFetch manualFetch = new ManualFetch();
		manualFetch.parseArgs(args);
		

		
		// set log format
		System.setProperty("java.util.logging.SimpleFormatter.format", 
				"%1$tF %1$tT %4$s %2$s: %5$s%6$s%n");
		if (!(manualFetch.options.getString("logfile").equals("-"))) {
			FileHandler fh;
			try {
				fh = new FileHandler(manualFetch.options.getString("logfile"));
				LOGGER.addHandler(fh);
				fh.setFormatter(new SimpleFormatter());
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
		LOGGER.setLevel(Level.ALL);


		
		LOGGER.info( "program called with arguments " + manualFetch.options.toString() );
		LOGGER.info( "logging times are in UTC/GMT; current timestamp is " + TIMESTAMP );
		LOGGER.info( "detected local IP address: " + Utils.getLocalIPAddress() );
		LOGGER.info( "detected local hostname: " + Utils.getLocalHostname() );

		Proxy proxy = new Proxy();
		proxy.name = manualFetch.options.getString("proxy") + ":" + manualFetch.options.getString("port");
		proxy.type = manualFetch.options.getString("type");
		proxy.sources = new Vector<String>();
		proxy.sources.add("manual");
		proxy.parseName();
		
		FileToFetch fileToFetch = new FileToFetch();
		fileToFetch.name = "manual";
		fileToFetch.url = manualFetch.options.getString("url");
		
		FetchResult fetchResult = manualFetch.prober.fetchFile(proxy, fileToFetch, manualFetch.options, true, true );
		LOGGER.info( "result: " + fetchResult );
		if (fetchResult.completed == true) {
			LOGGER.info( "dumping content retrieved.");
			System.out.println( fetchResult.content );
		}
	}






}
