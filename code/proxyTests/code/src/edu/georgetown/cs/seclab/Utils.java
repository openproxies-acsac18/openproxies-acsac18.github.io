package edu.georgetown.cs.seclab;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.codec.binary.Hex;


public class Utils {

	private static final Logger LOGGER = Logger.getLogger( Utils.class.getName() );


	/**
	 * Downloads and extracts a file
	 * @param url url from which to fetch
	 * @param filePattern the pattern of the file of which to extract
	 * @param destFile the destination file
	 * @param nopIfExists do nothing if the file already exists
	 */
	public static boolean downloadAndExtract( String url, String filePattern, String destFile, boolean nopIfExists) {
		File d = new File(destFile);
		if (nopIfExists && d.exists()) {
			return true;
		}
		try {
			LOGGER.fine( "retrieving " + url );
			File f = File.createTempFile("utils",null);
			FileUtils.copyURLToFile( new URL(url), f );
			TarArchiveInputStream tar = new TarArchiveInputStream( new GZIPInputStream( new FileInputStream(f)));
			TarArchiveEntry entry = null;
			while ((entry = tar.getNextTarEntry()) != null) {
				if (entry.isFile() && entry.getName().contains(".mmdb")) {
					FileOutputStream out = new FileOutputStream(d); 
					byte[] buffer = new byte[2048];
					int length = 0;
					while ((length = tar.read(buffer, 0, 2048)) != -1) {
						out.write(buffer, 0, length);
					}
					LOGGER.fine( "extracted file" );
					out.close();
					tar.close();
					return true;
				}
			}
			tar.close();
			f.delete();
		} catch (IOException e) {
			LOGGER.severe( "Cannot download GEOIP database file" );
			e.printStackTrace();
			System.exit(1);
		}
		LOGGER.fine( "could not find matching file" );
		return false;
	}


	/**
	 * computes a hexlified SHA256 digest of something
	 * @param bytes
	 * @return
	 */
	public static String computeSHA256( byte[] bytes ) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] h = digest.digest(bytes);
			return Hex.encodeHexString(h);
		} catch (NoSuchAlgorithmException e) {
			LOGGER.severe( "no sha-256 support. exiting." );
			System.exit( 1 );
		}
		assert(false);	// shouldn't get here
		return null;
	}


	public static String getLocalIPAddress() {
		InetAddress inetAddress;
		try {
			inetAddress = InetAddress.getLocalHost();
			return inetAddress.getHostAddress();
		} catch (UnknownHostException e) {
			return null;
		}
	}

	public static String getLocalHostname() {
		InetAddress inetAddress;
		try {
			inetAddress = InetAddress.getLocalHost();
			return inetAddress.getHostName();
		} catch (UnknownHostException e) {
			return null;
		}
	}
	
	
	/**
	 * grabs all bytes from an input stream
	 * @param in the input stream
	 * @return the bytes
	 */
	public static byte[] grabAllBytesFromStream( InputStream in ) {
		byte[] buf = new byte[2048];
		ByteArrayOutputStream res = new ByteArrayOutputStream();
		int len;
		
		try {
			while ((len = in.read(buf)) > 0) {
				res.write(buf,0,len);
			}
			in.close();
			return res.toByteArray();
		} catch (IOException e) {
			LOGGER.warning( "IO exception:  " + e.getMessage() );
			LOGGER.warning( Utils.exception2string(e) );
			return null;
		}
	}
	
	
	
	/**
	 * converts an exception stack trace to a string
	 * @param e the exception 
	 * @return
	 */
	public static String exception2string( Exception e ) {
		String res = "";
		StackTraceElement[] elements = e.getStackTrace();
		for (StackTraceElement element : elements ) {
			res += element.toString() + "\n";
		}
		return res;
	}
	
	
	/**
	 * Check the worker threads for broken workers, and log the ones that broke
	 * 
	 * @param workerResults
	 */
	protected static void checkForBrokenWorkers( List<Future<String>> workerResults ) {
		for ( Future<String> res : workerResults ) {
			if (res.isCancelled()) {
				LOGGER.warning( "a worker thread was canceled");
			}
			try {
				res.get();
			} catch (InterruptedException | ExecutionException e) {
				LOGGER.warning( "a thread did not terminate ; message was " + e.getMessage() );
				LOGGER.warning( "stack trace: " + Utils.exception2string(e) );
			}
		}
	}
	
	
	

	/**
	 * Saves obj to gzip compressed JSON file 
	 * @param outputFilename the filename
	 * @param obj the object
	 */
	protected static void makeCompressedJSONFile( String outputFilename, Object obj ) {
		LOGGER.info( "saving output to " + outputFilename );
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String json = gson.toJson(obj);
		try {			
			PrintWriter out = new PrintWriter(			// this is why people hate Java
					new OutputStreamWriter(
							new GZIPOutputStream(
									new FileOutputStream(
											new File(outputFilename)))));
			out.print(json);
			out.close();			
		} catch (IOException e) {
			LOGGER.severe( "cannot write output file: " + outputFilename );
			e.printStackTrace();
			System.exit(1);
		}
	}	
}


