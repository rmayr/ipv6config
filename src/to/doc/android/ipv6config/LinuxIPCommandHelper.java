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

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** This is a helper class for interacting with modern Linux network interface
 * settings.
 * 
 * @author Rene Mayrhofer <rene@mayrhofer.eu.org>
 * @version 1.3
 */
public class LinuxIPCommandHelper {
	/** Our logger for this class. */
	private final static Logger logger = java.util.logging.Logger.getLogger(LinuxIPCommandHelper.class.getName());

	/** Identifies an Ethernet interface and, funnily enough, also the GPRS/UMTS interfaces. */
	private final static String ETHERNET_INTERFACE = "link/ether";
	
	/** Identifier for starting the MTU option in the interface line. */
	private final static String INTERFACE_MTU = "mtu";

	/** Identifier that starts the state option in the interface line. */
	//private final static String INTERFACE_STATE = "state";

	/** Identifies an IPv4 address. */
	private final static String ADDRESS_IPV4 = "inet";
	/** Identifies an IPv6 address. */
	private final static String ADDRESS_IPV6 = "inet6";
	/** Identifies a secondary IPv4 address. */
	private final static String ADDRESS_MODIFIER_SECONDARY = "secondary";
	/** Identifies a temporary IPv6 address. */
	private final static String ADDRESS_MODIFIER_TEMPORARY = "temporary";
	/** Identifies a deprecated IPv6 address. */
	private final static String ADDRESS_MODIFIER_DEPRECATED = "deprecated";
	
	/** Identifies the gateway of a route. */
	private final static String ROUTE_GATEWAY = "via";
	/** Identifies the device of a route. */
	private final static String ROUTE_DEVICE = "dev";

	public final static String[] GET_INTERFACES_LINUX_BINARY_LOCATIONS = { 
		"/sbin/ip",
		"/bin/ip",
		"/system/bin/ip",
		"/system/xbin/ip" };

	/** Command to get and set network interface addresses and options under modern Linux systems. */
	private final static String ADDRESSES_COMMAND = " addr";
	/** Option to the GET_INTERFACES_LINUX command to select a specific interface. */
	private final static String INTERFACES_SELECTOR = " show dev ";
	
	/** Command to get and set routes under modern Linux systems. */
	private final static String ROUTES_COMMAND = " route";
	
	/** Option to select only IPv6 addresses/routes. */
	private final static String OPTION_IPv6_ONLY = " -6 ";

	/** Command to get and set Ethernet interface details under Linux systems. */
	public final static String ETHTOOL_COMMAND = "/usr/sbin/ethtool ";
	
	/** Simply the shell command (and if necessary path) to execute a standard POSIX shell. */
	public final static String SH_COMMAND = "sh";
	/** Path for the IPv6 configuration kernel options. */
	public final static String IPV6_CONFIG_TREE = "/proc/sys/net/ipv6/conf/";
	/** First part of the command to enable IPv6 address privacy (before interface name). */
	private final static String ENABLE_ADDRESS_PRIVACY_PART1 = "echo 2 > " + IPV6_CONFIG_TREE;
	/** First part of the command to disable IPv6 address privacy (before interface name). */
	private final static String DISABLE_ADDRESS_PRIVACY_PART1 = "echo 0 > " + IPV6_CONFIG_TREE;
	/** Second part of the command to enable/disable IPv6 address privacy (after interface name). */
	private final static String ADDRESS_PRIVACY_PART2 = "/use_tempaddr";
	/** Interface "name" to denote all network interface for kernel configuration options. */ 
	private final static String CONF_INTERFACES_ALL = "all";
	/** Interface "name" to denote the default kernel configuration options for new (hotplug enabled) network interfaces. */ 
	private final static String CONF_INTERFACES_DEFAULT = "default";

	/** Command to get and set network interface status under modern Linux systems (up/down mostly). */
	private final static String SET_INTERFACE = " link set ";
	/** Option to set network interface up. */
	private final static String UP = " up";
	/** Option to set network interface down. */
	private final static String DOWN = " down";
	/** Option to add network interface / addresses / routes. */
	private final static String ADD = " add ";
	/** Option to delete network interface / addresses / routes. */
	private final static String DEL = " del ";
	/** Option to set network interface MTU. */
	private final static String MTU = " mtu ";
	/** Delay between setting an interface down and up to force its IPv6 address to be reset (in milliseconds). */
	public final static int INTERFACE_DOWN_UP_DELAY = 100;

	/** Command to the "ip" binary to delete a tunnel interface under modern Linux systems. */
	private final static String DELETE_TUNNEL_INTERFACE = " tunnel" + DEL;
	/** Command to the "ip" binary to create a tunnel interface under modern Linux systems. */
	private final static String ADD_TUNNEL_INTERFACE = " tunnel" + ADD;
	private final static String ADD_TUNNEL_INTERFACE_OPTIONS_1 = " mode sit remote any local ";
	private final static String ADD_TUNNEL_INTERFACE_OPTIONS_2 = " ttl 255 ";

	/** This class represents an (IPv4 or IPv6) address with an optional network mask. */
	public static class InetAddressWithNetmask {
		public InetAddress address;
		public int subnetLength;
		/* Set to true if the "secondary" keyword is listed for this address. */ 
		public boolean markedSecondary = false;
		/* Set to true if the "temporary" keyword is listed for this address. */ 
		public boolean markedTemporary = false;
		/* Set to true if the "deprecated" keyword is listed for this address. */ 
		public boolean markedDeprecated = false;

		public InetAddressWithNetmask() {}
		public InetAddressWithNetmask(InetAddress addr, int maskLength) {
			this.address = addr;
			this.subnetLength = maskLength;
		}
		
		/** Returns true if this address is an IPv6 address, is globally routeable (i.e.
		 * it is not a link- or site-local address), and has been derived from a MAC
		 * address using the EUI scheme.
		 */
		public boolean isIPv6GlobalMacDerivedAddress() {
			return IPv6AddressesHelper.isIPv6GlobalMacDerivedAddress(address);
		}
	}
	
	/** This class represents a network interface with its most important 
	 * details: addresses and network masks, up/down status, MAC address,
	 * and MTU. 
	 */
	public static class InterfaceDetail {
		public String name;
		public String mac;
		public boolean isUp;
		public int mtu;
		public LinkedList<InetAddressWithNetmask> addresses = new LinkedList<InetAddressWithNetmask>();
		
		public LinkedList<Inet4Address> getLocalIpv4Addresses() {
			LinkedList<Inet4Address> ret = new LinkedList<Inet4Address>();
			for (InetAddressWithNetmask addr : addresses) 
				if (addr.address != null && addr.address instanceof Inet4Address)
					ret.add((Inet4Address) addr.address);
			return ret;
		}
		
		public LinkedList<Inet6Address> getLocalIpv6Addresses() {
			LinkedList<Inet6Address> ret = new LinkedList<Inet6Address>();
			for (InetAddressWithNetmask addr : addresses) 
				if (addr.address != null && addr.address instanceof Inet6Address)
					ret.add((Inet6Address) addr.address);
			return ret;
		}
	}
	
	/** Returns interface details for all currently known interfaces. */
	public static LinkedList<InterfaceDetail> getIfaceOutput() throws IOException {
		return getIfaceOutput(null);
	}
	
	/** Returns interface details (current system status) for interfaces.
	 * AW: 24.10.2008
	 * RM: 15.04.2009 Updated from ip link to ip addr and actually implemented....
	 * @param iface If set, then only fetch information for this interface name.
	 *              When an invalid interface name is given, output will be
	 *              empty. If set to null, returns all interfaces.
	 * @return the output of the ip addr command appropriately parsed for the options in InterfaceDetail.
	 * @throws IOException 
	 */
	public static LinkedList<InterfaceDetail> getIfaceOutput(String iface) throws IOException {
		logger.finer("Acquiring interface details for iface " + iface);
		
		String cmd = getIPCommandLocation() + ADDRESSES_COMMAND;
		StringTokenizer lines = null;
		LinkedList<InterfaceDetail> list = new LinkedList<InterfaceDetail>();
		
		try {
			lines =	new StringTokenizer(Command.executeCommand(cmd + 
						(iface != null ? (INTERFACES_SELECTOR + iface) : ""),
						false, false, null), "\n");
		} catch (Exception e) {
			if (iface == null)
				logger.log(Level.WARNING, "Tried to parse interface stati for all interfaces, but could not", e);
			else {
				logger.log(Level.WARNING, "Tried to parse interface status for interface " + iface +
					" but could not - most probably the interface doesn't exist at this time. " +
					"Will generate a dummy interface description", e);
				InterfaceDetail cur = new InterfaceDetail();
				cur.name = iface;
				cur.isUp = false;
				list.add(cur);
				return list;
			}
		}
		
		InterfaceDetail cur = null;
		while (lines != null && lines.hasMoreTokens()) {
			String line = lines.nextToken();
			logger.finest("getIfaceOutput: parsing line '" + line + "'");
			if (! Character.isWhitespace(line.charAt(0))) {
				logger.finest("getIfaceOutput: start of new block");
				
				// lines that start without whitespace start a new block
				// thus write the old one (if set) - only link/ether for now
				// in the future, might skip the cur.mac != null check to include all interface types
				if (cur != null && cur.mac != null) {
					logger.finest("getIfaceOutput: adding to list: " + cur.name);
					list.add(cur);
				}
				
				StringTokenizer fields = new StringTokenizer(line, ":");
				// ignore the first field - just a number
				fields.nextToken();
				
				// the second is the interface name
				cur = new InterfaceDetail();
				cur.name = fields.nextToken().trim();
				cur.isUp = false;
				
				// the third "field" contains multiple options, now separated by space
				String remainder = fields.nextToken();
				logger.finest("Starting to parse remainder of interface line '" + remainder + "'");
				StringTokenizer options = new StringTokenizer(remainder);
				while (options.hasMoreTokens()) {
					String opt = options.nextToken().trim();
					logger.finest("Parsing option " + opt);
					if (opt.equals(INTERFACE_MTU)) {
						String mtu = options.nextToken().trim();
						logger.finest("Interface " + cur.name + " mtu field: '" + mtu + "'");
						cur.mtu = Integer.parseInt(mtu);
					}
					// hmm, this seems to be "UNKNOWN instead of UP - don't use the state option but the other syntax
					/*else if (opt.equals(INTERFACE_STATE)) {
						String state = options.nextToken();
						logger.finest("Interface " + cur.name + " state field: '" + state + "'");
						cur.isUp = state.equals("UP");
					}*/
					// this handles the first options block embedded in <...>
					else if (opt.startsWith("<")) {
						String tmp = opt.substring(1, opt.length()-1);
						logger.finest("Parsing embedded options '" + tmp + "'");
						// these embedded options are again separated by ","
						StringTokenizer options2 = new StringTokenizer(tmp, ",");
						while (options2.hasMoreTokens()) {
							String opt2 = options2.nextToken();
							// at the moment, only look for the "UP" option
							// in the future, might want to read NO-CARRIER, BROADCAST, and MULTICAST as well
							if (opt2.equals("UP"))
								cur.isUp = true;
						}
					}
				}
				logger.finer("Read interface line: " + cur.name + ", " + cur.mtu + ", " + cur.isUp);
			}
			else {
				logger.finest("getIfaceOutput: block continued");
				// within a block
				StringTokenizer options = new StringTokenizer(line.trim(), " \t");
				while (options.hasMoreTokens()) {
					String opt = options.nextToken();
					logger.finest("getIfaceOutput: trying to parse option '" + opt + "'");
					// "lo" marks the end of line, but also check explicitly
					if (opt.equals("lo") || !options.hasMoreTokens()) break;
					
					String value = options.nextToken();
					logger.finest("getIfaceOutput: trying to parse value '" + value + "'");

						if (opt.equals(ETHERNET_INTERFACE)) {
							cur.mac = value;
							logger.finest("getIfaceOutput: found mac " + cur.mac
									+ " for " + cur.name);
						} else if (opt.equals(ADDRESS_IPV4) || opt.equals(ADDRESS_IPV6)) {
							InetAddressWithNetmask addr = new InetAddressWithNetmask();
							if (value.contains("/")) {
								addr.address = InetAddress.getByName(value.substring(0, value.indexOf('/')));
								addr.subnetLength = Integer.parseInt(value.substring(value.indexOf('/')+1));
							}
							else {
								addr.address = InetAddress.getByName(value);
								addr.subnetLength = addr.address instanceof Inet4Address ? 32 : 128;
							}
							
							// try to find additional modifiers
							if (line.indexOf(ADDRESS_MODIFIER_SECONDARY) >= 0)
								addr.markedSecondary = true;
							if (line.indexOf(ADDRESS_MODIFIER_TEMPORARY) >= 0)
								addr.markedTemporary = true;
							if (line.indexOf(ADDRESS_MODIFIER_DEPRECATED) >= 0)
								addr.markedDeprecated = true;
							
							cur.addresses.add(addr);
							logger.finest("getIfaceOutput: found IP address " + addr
									+ " for " + cur.name);
						}
				}
			}
		}		
		// save the last block info
		if (cur != null && cur.mac != null)
			list.add(cur);
		return list;
	}

	/** This class represents a route with a target (as a string, because it 
	 * can take on special values such as "default" in addition to target 
	 * networks), a gateway, and an interface. Gateway or (exclusive or) iface
	 * may be null. 
	 */
	public static class RouteDetail {
		public String target;
		public InetAddress gateway;
		public String iface;
	}

	/** Returns the list of routes in the main routing table. 
	 *  
	 * @param queryIPv6 If true, then IPv6 routes are queried. If false, then IPv4 routes are queried.
	 */
	public static LinkedList<RouteDetail> getRouteOutput(boolean queryIPv6) throws IOException {
		logger.finer("Acquiring route details");
		
		String cmd = getIPCommandLocation() + ROUTES_COMMAND +
			(queryIPv6 ? OPTION_IPv6_ONLY : "");
		StringTokenizer lines = null;
		LinkedList<RouteDetail> list = new LinkedList<RouteDetail>();
		
		try {
			lines =	new StringTokenizer(Command.executeCommand(cmd,	false, false, null), "\n");
		} catch (Exception e) {
			logger.log(Level.WARNING, "Tried to parse interface stati for all interfaces, but could not", e);
		}
		
		RouteDetail cur = null;
		while (lines != null && lines.hasMoreTokens()) {
			String line = lines.nextToken();
			logger.finest("getRouteOutput: parsing line '" + line + "'");

			StringTokenizer fields = new StringTokenizer(line, " \t");
			
			// the first field is always the target
			cur = new RouteDetail();
			cur.target = fields.nextToken();

			// then we get options defined by "dev" or "via" (and others that we ignore)
			while (fields.hasMoreTokens()) {
				String opt = fields.nextToken().trim();
				logger.finest("getRouteOutput: trying to parse option '" + opt + "'");
				
				String value = fields.nextToken().trim();
				logger.finest("getRouteOutput: trying to parse value '" + value + "'");

				if (opt.equals(ROUTE_GATEWAY)) {
					cur.gateway = InetAddress.getByName(value);
					logger.finest("getRouteOutput: found gateway " + cur.gateway + " for target " + cur.target);
				} else if (opt.equals(ROUTE_DEVICE)) {
					cur.iface = value;
					logger.finest("getRouteOutput: found interface " + cur.iface + " for target " + cur.target);
				} else {
					logger.finest("getRouteOutput: ignoring unknown option '" + opt + "'");
				}
			}
			
			// line finished, add route to list
			list.add(cur);
		}
		
		return list;
	}

	
	/** Helper to locate a usable "ip" command or null if none is found. */
	private static String getIPCommandLocation() {
		String triedPaths = "";
		// sanity check: can we actually execute our command?
		for (String binary : GET_INTERFACES_LINUX_BINARY_LOCATIONS) {
			if (new File(binary).canRead())
				return binary;
			triedPaths = triedPaths + " " + binary;
		}
		logger.warning("Could not find ip binary in" + triedPaths + 
				", unable to read network interface details");
		return null;
	}

	/** Executes the command ethtool for the given network interface and returns the output within a HashMap.
	 * @param device Get the information of this network interface card.
	 * @return A map of options and their values, e.g. "Link detected", "Speed", "Duplex", and "Auto-negotiation". 
	 */
	public static HashMap<String, String> getInterfaceDetails(String device) throws ExitCodeException, IOException {
		// first check if the interface is up
		HashMap<String, String> options = new HashMap<String, String>();
		LinkedList<InterfaceDetail> ifaceDetail = getIfaceOutput(device);
		if (ifaceDetail.get(0).isUp) {
			StringTokenizer lines;
			lines = new StringTokenizer(Command.executeCommand(ETHTOOL_COMMAND + device, false, false, null), "\n");
			String supportedLinkModes = "";
			boolean supportedLinkModesDone = false;
			while ((lines.hasMoreTokens())) {
				String line = lines.nextToken();
				// skip the first line
				if (line.startsWith("Settings for ")) continue;
				// special handling for the "Supported link modes"
				// The problem with the supported link modes is that they are printed after the key "Supported link modes" within more than one line.
				// So the output for this option can take one, two, or more lines.
				if (line.trim().startsWith("Supported link modes")) {
					// get the first line of "Supported link modes"
					supportedLinkModes = StringHelper.getToken(line, ":", 2).trim();
					continue;
				}
				if (!supportedLinkModesDone) {
					if (!line.trim().startsWith("Supports auto-negotiation")) {
						// get the other lines before the next option "Supports auto-negotiation" appears
						supportedLinkModes = supportedLinkModes + " " + line.trim();
						continue;
					} else {
						// now we add supportedLinkModes to the HashMap and continue with the next line "Supports auto-negotiation"
						options.put("Supported link modes", supportedLinkModes. trim());
						supportedLinkModesDone = true;
						logger.finer("Possible values for the link mode of the interface " + device + " are: " + supportedLinkModes);
					}
				}
				// but parse others with ":" as delimiter
				StringTokenizer parts = new StringTokenizer(line, ":");
				// can only work with "key: value" lines
				if (parts.countTokens() != 2) continue;
				options.put(parts.nextToken().trim(), parts.nextToken().trim());
			}
		} else {
			//TODO: fill options
		}
		return options;
	}
	
    /** Returns the IPv4 address of the interface that is used for the default 
     * route. This is the IPv4 address that can be used for determining the 
     * prefix for a 6to4 tunneling address.  
     */
    public static Inet4Address getOutboundIPv4Address() {
    	try {
    		/* loop over all IPv4 routes to find the default route which would
    		 * be used for establishing a 6to4 tunnel */
			LinkedList<RouteDetail> routes = LinuxIPCommandHelper.getRouteOutput(false);
			for (RouteDetail route : routes) {
				if (route.target.equalsIgnoreCase("default") || route.target.equals("0.0.0.0/0")) {
					// ok, default route found
					logger.info("Found default IPv4 route pointing to gateway '" +
							route.gateway + "' on interface '" + route.iface + "'");
					if (route.iface == null || route.iface.length() == 0) {
						logger.warning("Default IPv4 route with empty interface specifier, can't determine outbound interface");
						continue;
					}
					
					// now try to find the outbound IPv4 address on this interface
					LinkedList<InterfaceDetail> ifaceDetails = LinuxIPCommandHelper.getIfaceOutput(route.iface);
					if (ifaceDetails.size() != 1) {
						logger.severe("Interface " + route.iface + " is listed for IPv4 default route, but can't parse interface details");
						continue;
					}
					InterfaceDetail ifaceDetail = ifaceDetails.peek();
					for (InetAddressWithNetmask addr : ifaceDetail.addresses) {
						// only accept non-secondary IPv4 addresses
						if (addr.address != null && addr.address instanceof Inet4Address &&
							!addr.markedSecondary) {
							logger.info("Found outbound IPv4 address " + addr.address +
									" on interface " + route.iface + 
									", assuming as the one used for default routing");
							return (Inet4Address) addr.address;
						}
					}
				}
			}
			// when we get here, no default route with associated outbound addreess could be found
			logger.warning("Unable to find IPv4 default route with outbound IP address");
			return null;
		} catch (IOException e) {
			logger.warning("Unable to query Linux IPv4 main routing table" + e);
			return null;
		} 
    }
	
	/** Enable address privacy for all interfaces and potentially try to force reload.
	 * 
	 * @param enablePrivacy If true, enable privacy. If false, disable address privacy. 
	 * @param forceAddressReload If set to true, each interface will also be 
	 *        reset by calling forceAddressReload.
	 * @return false if address privacy could not be set on any of the interfaces,
	 *         true if all of them could be set.
	 */
	public static boolean enableIPv6AddressPrivacy(boolean enablePrivacy, boolean forceAddressReload) {
		logger.fine((enablePrivacy ? "Enabling" : "Disabling") + " IPv6 address privacy" +
				(forceAddressReload ? " and forcing reload of interfaces" : ""));
		
		boolean ret = true;
		LinkedList<String> allIfaces = new LinkedList<String>();
		LinkedList<String> modifiedIfaces = new LinkedList<String>();
		
		// include the special "default" and "all" trees
		allIfaces.add(CONF_INTERFACES_ALL);
		allIfaces.add(CONF_INTERFACES_DEFAULT);
		
		// for now, use static interface names
		// TODO: take all other interfaces with IPv6 addresses as well
		allIfaces.add("eth0"); // WLAN interface on HTC Desire, Desire HD and Google Nexus S (and probably others)
		allIfaces.add("rmnet0"); // GPRS/UMTS interface
		allIfaces.add("rmnet1");
		allIfaces.add("rmnet2");
		allIfaces.add("ip6tnl0"); // IPv6 in/over IPv4 tunnel
		allIfaces.add("tiwlan0"); // WLAN interface on Motorola Milestone
		
		for (String iface: allIfaces) {
			File configDir = new File(IPV6_CONFIG_TREE + iface); 
			// only try to enable if this is indeed known as an IPv6-capable interface to the kernel
			if (configDir.isDirectory())
				if (enableIPv6AddressPrivacy(iface, enablePrivacy))
					modifiedIfaces.add(iface);
				else
					ret = false;
		}
		
		if (forceAddressReload)
			forceAddressReload(modifiedIfaces);
		
		return ret;
	}
	
	/** Enable address privacy for a specific interface. This sets the 
	 * "use_tempaddr" kernel option to "2" for the given interface.
	 * 
	 * @param enablePrivacy If true, enable privacy. If false, disable address privacy. 
	 * @return true if the kernel option could be set, false otherwise. 
	 */
	public static boolean enableIPv6AddressPrivacy(String iface, boolean enablePrivacy) {
		try {
			if (Command.executeCommand(SH_COMMAND, true, 
					(enablePrivacy ? ENABLE_ADDRESS_PRIVACY_PART1 : DISABLE_ADDRESS_PRIVACY_PART1) + 
							iface + ADDRESS_PRIVACY_PART2, null, null) == 0) {
				logger.finer("Enabled address privacy on interface " + iface);
				return true;
			}
			else {
				return false;
			}
		} catch (IOException e) {
			logger.severe("Unable to execute system command, address privacy may not be enabled (access privileges missing?) " + e);
			return false;
		} catch (InterruptedException e) {
			return false;
		}
	}
	
	/** Tries to force the interface to reset its addresses by setting it down and then up. */
	public static boolean forceAddressReload(String iface) {
		String cmd = getIPCommandLocation() + SET_INTERFACE + iface + " ";

		try {
			if (Command.executeCommand(SH_COMMAND, true, cmd + DOWN, null, null) == 0) {
				// wait just a little for the interface to properly go down
				Thread.sleep(INTERFACE_DOWN_UP_DELAY);
				if (Command.executeCommand(SH_COMMAND, true, cmd + UP, null, null) == 0) {
					logger.finer("Reset interface " + iface + " to force address reload");
					return true;
				}
				else {
					logger.warning("Set interface " + iface + " down but was unable to set it up again");
					return false;
				}
			}
			else {
				logger.warning("Unable to set interface " + iface + " down");
				return false;
			}
		} catch (IOException e) {
			logger.severe("Unable to execute system command, new addresses may not have been set (access privileges missing?) " + e);
			return false;
		} catch (InterruptedException e) {
			return false;
		}
	}

	/** Tries to force all specified interfaces to reset their addresses by setting them down and then up. */
	public static boolean forceAddressReload(List<String> ifaces) {
		boolean ret = true;
		LinkedList<String> downedIfaces = new LinkedList<String>();
		
		String cmd = getIPCommandLocation() + SET_INTERFACE;
		
		try {
			// first set all interfaces down
			for (String iface : ifaces) {
				// only try to enable if this is indeed known as an IPv6-capable interface to the kernel
				File configDir = new File(IPV6_CONFIG_TREE + iface);
				if (configDir.isDirectory() && !iface.equals(CONF_INTERFACES_ALL) && !iface.equals(CONF_INTERFACES_DEFAULT)) {
					if (Command.executeCommand(SH_COMMAND, true, cmd + iface + DOWN, null, null) == 0)
						downedIfaces.add(iface);
					else {
						logger.warning("Unable to set interface " + iface + " down, will not try to set it up again");
						ret = false;
					}
				}
			}
			
			// then wait just a little for the interfaces to properly go down
			Thread.sleep(INTERFACE_DOWN_UP_DELAY);
			
			// and start all those again that were set down
			for (String iface : downedIfaces) {
				if (Command.executeCommand(SH_COMMAND, true, cmd + iface + UP, null, null) == 0) 
					logger.finer("Reset interface " + iface + " to force address reload");
				else {
					logger.warning("Set interface " + iface + " down but was unable to set it up again");
					ret = false;
				}
			}
			
			return ret;
		} catch (IOException e) {
			logger.severe("Unable to execute system command, new addresses may not have been set (access privileges missing?) " + e);
			return false;
		} catch (InterruptedException e) {
			return false;
		}
	}
	
	/** Delete a tunnel interface that was previously created.
	 * 
	 * @param iface The interface name to delete.
	 * @return true if successfully deleted, false otherwise.
	 */
	public static boolean deleteTunnelInterface(String iface) {
		String cmd = getIPCommandLocation() + DELETE_TUNNEL_INTERFACE + iface;

		try {
			if (Command.executeCommand(SH_COMMAND, true, cmd, null, null) == 0) { 
				logger.finer("Deleted tunnel interface " + iface);
				return true;
			}
			else {
				logger.warning("Unable to delete tunnel interface " + iface + ", it probably has not been created beforehand");
				return false;
			}
		} catch (IOException e) {
			logger.severe("Unable to execute system command, tunnel interface not deleted (access privileges missing?) " + e);
			return false;
		} catch (InterruptedException e) {
			return false;
		}
	}
	
	/** Create a new 6to4 tunnel interface.
	 * 
	 * @param iface The interface name to create.
	 * @param localIPv4Endpoint The local IPv4 endpoint address to assign to the tunnel.
	 * @param ipv6Prefix The 6to4 prefix derived from the endpoint address that will be
	 *                   used to create the IPv6 address.
	 * @param mtu The maximum transfer unit for the new interface. If a value 
	 *            <=0 is passed as argument, a default of 1430 will be used.
	 * @return true if successfully created and addresses and routes added, false otherwise.
	 */
	public static boolean create6to4TunnelInterface(String iface, 
			Inet4Address localIPv4Endpoint, String ipv6Prefix, int mtu) {
		if (mtu <= 0) mtu = 1430;

		String cmdTunnel = getIPCommandLocation() + ADD_TUNNEL_INTERFACE +
			iface + ADD_TUNNEL_INTERFACE_OPTIONS_1 + 
			localIPv4Endpoint.getHostAddress() + 
			ADD_TUNNEL_INTERFACE_OPTIONS_2;
		String cmdSetUp = getIPCommandLocation() + SET_INTERFACE + iface + UP + MTU + mtu;
		/* Experienced IPv6 users will wonder why the netmask for sit0 is /16, not /64; 
		 * by setting the netmask to /16, you instruct your system to send packets 
		 * directly to the IPv4 address of other 6to4 users; if it was /64, you'd send 
		 * packets via the nearest relay router, increasing latency. */
		String cmd6to4Addr = getIPCommandLocation() + OPTION_IPv6_ONLY + 
			ADDRESSES_COMMAND + ADD + ipv6Prefix + "::/16 dev " + iface;
		String cmd6to4Route1 = getIPCommandLocation() + OPTION_IPv6_ONLY +
			ROUTES_COMMAND + ADD + " 0:0:0:0:0:ffff::/96 dev " + iface + " metric 1";
		String cmd6to4Route2 = getIPCommandLocation() + OPTION_IPv6_ONLY +
			ROUTES_COMMAND + ADD + " 2000::/3 via ::192.88.99.1 dev " + iface + " metric 1";

		try {
			logger.finer("Trying to create 6to4 tunnel interface " + iface + 
					" with local endpoint " + localIPv4Endpoint.getHostAddress() +
					" for prefix " + ipv6Prefix + " with MTU " + mtu);
			
			if (Command.executeCommand(SH_COMMAND, true, cmdTunnel, null, null) == 0) {
				logger.severe("Unable to create tunnel interface " + iface);
				return false;
			}
			if (Command.executeCommand(SH_COMMAND, true, cmdSetUp, null, null) == 0) {
				logger.severe("Unable to set tunnel interface " + iface + " up with MTU " + mtu);
				return false;
			}
			if (Command.executeCommand(SH_COMMAND, true, cmd6to4Addr, null, null) == 0) {
				logger.severe("Unable to add 6to4 address " + ipv6Prefix + 
						" to tunnel interface " + iface);
				return false;
			}
			if (Command.executeCommand(SH_COMMAND, true, cmd6to4Route1, null, null) == 0) {
				logger.severe("Unable to add 6to4 route 1 to tunnel interface " + iface);
				return false;
			}
			if (Command.executeCommand(SH_COMMAND, true, cmd6to4Route2, null, null) == 0) {
				logger.severe("Unable to add 6to4 route 1 to tunnel interface " + iface);
				return false;
			}
			
			logger.info("Successfully created 6to4 tunnel interface " + iface + 
					" with local endpoint " + localIPv4Endpoint.getHostAddress() +
					" for prefix " + ipv6Prefix + " with MTU " + mtu);
			return true;
		} catch (IOException e) {
			logger.severe("Unable to execute system command, tunnel interface not deleted (access privileges missing?) " + e);
			return false;
		} catch (InterruptedException e) {
			return false;
		}
	}
}
