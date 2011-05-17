/*****************************************************************************
 *  Project: Android IPv6Config
 *  Description: Android application to change IPv6 kernel configuration
 *  Author: René Mayrhofer
 *  Copyright: René Mayrhofer, 2011-2011
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 
 * as published by the Free Software Foundation.
 *****************************************************************************/

package to.doc.android.ipv6config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/** This is a helper class to query local and externally visible IPv6 addresses. */
public class IPv6AddressesHelper {
	/** Our logger for this class. */
	private final static Logger logger = java.util.logging.Logger.getLogger(IPv6AddressesHelper.class.getName());

	public final static String IPv6_6to4_TUNNEL_INTERFACE_NAME = "sit6to4";
	
	/** This is the host queried for the externally visible IPv6 address of 
	 * the client when connecting to Internet services. The host name is
	 * resolved to IPv6 addresses (AAAA DNS entries) to make sure that we
	 * connect via IPv6.
	 */
	public final static String GET_OUTBOUND_IP_SERVER = "doc.to";
	public final static String GET_OUTBOUND_IP_SERVER_ADDRESSv6 = "2002:5078:37d:1::19";
	public final static String GET_OUTBOUND_IP_SERVER_ADDRESSv4 = "80.120.3.103";
	public final static int GET_OUTBOUND_IP_PORT = 444;

	public final static String GET_OUTBOUND_IP_URL_PROTOCOL = "https://";
	public final static String GET_OUTBOUND_IP_URL_PATH = "/getip/";

	/** This is the URL queried for the externally visible IPv6 address of
	 * the client when connecting to Internet services.
	 */
	public final static String GET_OUTBOUND_IP_URL = 
		GET_OUTBOUND_IP_URL_PROTOCOL + GET_OUTBOUND_IP_SERVER + ":" + GET_OUTBOUND_IP_PORT + GET_OUTBOUND_IP_URL_PATH;

	/** This method tries to retrieve the IPv6/IPv4 address visible to servers by 
     * querying https://doc.to/getip/.
     * 
     * Attention: this may take a few seconds - don't do it in the foreground!
     * 
     * @param queryIPv6 If true, connects via IPv6. If false, connects via IPv4. 
     * @return the IPv6/IPv4 address of this host that is used to connect to other
     *         hosts or null if IPv6/IPv4 connections to https://doc.to are not
     *         possible.
     */
    public static String getOutboundIPAddress(boolean queryIPv6) {
    	try {
    		// first resolve the host's AAAA entries to make sure to connect to the host via IPv6
			InetAddress[] serverAddrs = InetAddress.getAllByName(GET_OUTBOUND_IP_SERVER);
			InetAddress server = null;
			for (InetAddress addr : serverAddrs) {
				if (queryIPv6 && addr instanceof Inet6Address) {
					logger.log(Level.FINE, "Resolved " + GET_OUTBOUND_IP_SERVER + " to IPv6 address " + addr.getHostAddress());
					if (server == null)
						server = (Inet6Address) addr;
					else
						logger.log(Level.WARNING, "Found multiple IPv6 addresses for host " + 
								GET_OUTBOUND_IP_SERVER + ", but expected only one. Will use the one found first " +
								server.getHostAddress() + " and ignore the one found now " + addr.getHostAddress());
				}
				else if (!queryIPv6 && addr instanceof Inet4Address) {
					logger.log(Level.FINE, "Resolved " + GET_OUTBOUND_IP_SERVER + " to IPv4 address " + addr.getHostAddress());
					if (server == null)
						server = (Inet4Address) addr;
					else
						logger.log(Level.WARNING, "Found multiple IPv4 addresses for host " + 
								GET_OUTBOUND_IP_SERVER + ", but expected only one. Will use the one found first " +
								server.getHostAddress() + " and ignore the one found now " + addr.getHostAddress());
				} 
			}
			if (server == null) {
				if (queryIPv6) server = InetAddress.getByName(GET_OUTBOUND_IP_SERVER_ADDRESSv6);
				else server = InetAddress.getByName(GET_OUTBOUND_IP_SERVER_ADDRESSv4);
				logger.log(Level.WARNING, "Could not resolve host " + GET_OUTBOUND_IP_SERVER + " to " + 
						(queryIPv6 ? "IPv6" : "IPv4" ) + " address, assuming DNS resolver/server to be broken. " +
						"Will now try with hard-coded address " + server + " although it may have changed.");
			}
			
			// now that we have the IPv6 address to connect to, query the URL
			String url = GET_OUTBOUND_IP_URL_PROTOCOL + 
				(queryIPv6 ? ("[" + server.getHostAddress() + "]") : server.getHostAddress()) + 
				":" + GET_OUTBOUND_IP_PORT + GET_OUTBOUND_IP_URL_PATH;
			logger.log(Level.FINER, "Querying URL " + url + " for outbound IPv6 address");
			return queryServerForOutboundAddress(url);
		} 
    	catch (UnknownHostException e) {
			logger.log(Level.WARNING, "Unable to resolve host " + GET_OUTBOUND_IP_SERVER, e);
			return null;
		} 
    }
    
    /** This method queries the passed customURL or https://doc.to/getip/ if 
     * null is given and expects to read the outbound IP address of this client
     * in return.
     * 
     * @param customURL The URL to query. If null, GET_OUTBOUND_IP_URL will 
     *                  be used. 
     * @return the outbound IP address of this host as seen be the server.
     */
    public static String queryServerForOutboundAddress(String customURL) {
	    String url = customURL != null ? customURL : GET_OUTBOUND_IP_URL;
	    
    	try {
			// setup 1 before querying the URL: enable following HTTP redirects
			HttpURLConnection.setFollowRedirects(true);
			
			// setup 2 before querying the URL: disable certificate checks
			// create a trust manager that does not validate certificate chains
			TrustManager[] trustAllCerts = new TrustManager[]{
			    new X509TrustManager() {
			        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
			            return null;
			        }
			        public void checkClientTrusted(
			            java.security.cert.X509Certificate[] certs, String authType) {
			        }
			        public void checkServerTrusted(
			            java.security.cert.X509Certificate[] certs, String authType) {
			        }
			    }
			};
			// install the all-trusting trust manager
		    SSLContext sc = SSLContext.getInstance("TLS");
		    sc.init(null, trustAllCerts, new java.security.SecureRandom());
		    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

		    // and also disable hostname verification
		    HttpsURLConnection.setDefaultHostnameVerifier( new HostnameVerifier() {
					@Override
					public boolean verify(String arg0, SSLSession arg1) {
						return true;
					}
		    	});
			
		    // finally query the HTTPS URL
			//URLConnection conn = new URL("https", "[2002:5078:37d:1::19]", 443, GET_OUTBOUND_IP_URL_PATH).openConnection();
		    URLConnection conn = new URL(url).openConnection();
			conn.setUseCaches(false);
			// doesn't seem to be required
			/*conn.setRequestProperty("User-Agent","Mozilla/5.0 ( compatible ) ");
			conn.setRequestProperty("Accept","[star]/[star]");*/
			logger.log(Level.FINE, "Connecting to URL " + url);
			conn.connect();
			
			if (conn instanceof HttpURLConnection) {
				int statusCode = ((HttpURLConnection) conn).getResponseCode();
				logger.log(Level.FINE, "URL " + url + " returned content type " + conn.getContentType() +
					" and status code " + statusCode);

				if (statusCode<200 || statusCode >=300) {
					logger.warning("Querying for server-resolved IP address resulted in status code " +
							statusCode + ", can not parse response");
					return ((HttpURLConnection) conn).getResponseMessage();
				}
				
				InputStreamReader in = new InputStreamReader((InputStream) conn.getContent());
			    BufferedReader buff = new BufferedReader(in, 2048);
			    StringBuffer reply = new StringBuffer();
			    String line = null;
			    do {
			    	line = buff.readLine();
			    	if (line != null) {
			    		if (reply.length() > 0) reply.append("\n");
			    		reply.append(line);
			    	}
			    } while (line != null);
			    
				return reply.toString();
			}
			return null;
    	}
    	catch (MalformedURLException e) {
			logger.log(Level.SEVERE, "Internal error: URL deemed invalid " + url, e);
			return null;
		} catch (IOException e) {
			logger.log(Level.WARNING, "Unable to connect to URL " + url + 
					" and/or host " + GET_OUTBOUND_IP_SERVER, e);
			return null;
		} catch (NoSuchAlgorithmException e) {
			logger.log(Level.WARNING, "Unable to install custom TrustManager/SSLContext without certificate validation", e);
			return null;
		} catch (KeyManagementException e) {
			logger.log(Level.WARNING, "Unable to install custom TrustManager/SSLContext without certificate validation", e);
			return null;
		}
    }
    
    /** This method doesn't work on Android pre-Honeycomb (3.0) systems for getting IPv6 addresses. */ 
    public static Vector<String> getLocalAddresses() {
    	Vector<String> addrs = new Vector<String>();
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
               	for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
               		InetAddress inetAddress = enumIpAddr.nextElement();
               		if (!inetAddress.isLoopbackAddress()) {
               			logger.log(Level.FINE, "Found non-loopback address: " + inetAddress.getHostAddress());
               			addrs.add(inetAddress.getHostAddress());
               		}
               		
               		if (inetAddress instanceof Inet6Address) {
               			logger.log(Level.FINE, "Found IPv6 address: " + inetAddress.getHostAddress());
               		}
                }
            }
        } catch (SocketException ex) {
            logger.log(Level.SEVERE, ex.toString());
        }
        return addrs;
    }
   
	/** Returns true if this address is an IPv6 address, is globally routeable (i.e.
	 * it is not a link- or site-local address), and has been derived from a MAC
	 * address using the EUI scheme.
	 */
	public static boolean isIPv6GlobalMacDerivedAddress(InetAddress address) {
		if (address == null || ! (address instanceof Inet6Address))
			// only check valid IPv6 addresses
			return false;
		Inet6Address addr6 = (Inet6Address) address;
		
		if (addr6.isLinkLocalAddress())
			// if it's link-local, it may be MAC-derived, but not privacy sensitive
			return false;

		byte[] addrByte = addr6.getAddress();
		// MAC-derivation adds "FFFE" in the middle of the 48 bits MAC
		return addrByte[11] == (byte) 0xff && addrByte[12] == (byte) 0xfe;
	}
	
	/** Computes the 64 bit 6to4 prefix from a given IPv4 base address. The 
	 * format is 2002:%02x%02x:%02x%02x with the 4 bytes of ipv4Base filled in.
	 */
	public static String compute6to4Prefix(Inet4Address ipv4Base) {
		if (ipv4Base == null)
			return null;
		
		byte[] addrBytes = ipv4Base.getAddress();
		String prefix = String.format("2002:%02x%02x:%02x%02x", 
				addrBytes[0], addrBytes[1], addrBytes[2], addrBytes[3]);
		return prefix;
	}
    
    /** Dummy main routine to call the helper methods and print on console. */
    public static void main(String[] args) throws UnknownHostException {
    	Vector<String> localAddrs = getLocalAddresses();
    	for (String a : localAddrs)
    		System.out.println("Found local non-loopback address: " + a);
    	
    	Inet4Address outboundIPv4Addr = LinuxIPCommandHelper.getOutboundIPv4Address();
    	if (outboundIPv4Addr != null) {
    		System.out.println("Found outbound (locally queried) IPv4 address: " + outboundIPv4Addr.getHostAddress());
    		System.out.println("Derived 6to4 prefix is: " + compute6to4Prefix(outboundIPv4Addr));
    	}
    	System.out.println("Found outbound (externally visible) IPv4 address: " + getOutboundIPAddress(false));
    	
    	String outboundIPv6Addr = getOutboundIPAddress(true);
    	System.out.println("Found outbound (externally visible) IPv6 address: " + outboundIPv6Addr);
    	System.out.println("Address is MAC-derived: " + 
    			isIPv6GlobalMacDerivedAddress(Inet6Address.getByName(outboundIPv6Addr)));
    }
}
