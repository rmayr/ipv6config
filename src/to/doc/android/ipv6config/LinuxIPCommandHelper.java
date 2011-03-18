/*****************************************************************************
 *  Project: Android IPv6Config
 *  Description: Android application to change IPv6 kernel configuration
 *  Author: Rene Mayrhofer
 *  Copyright: Rene Mayrhofer, 2011-2011
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 3 
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

	/** Command to get and set network interface addresses and options under modern Linux systems. */
	public final static String GET_INTERFACES_LINUX = "/system/bin/ip addr";
	/** Option to the GET_INTERFACES_LINUX command to select a specific interface. */
	public final static String GET_INTERFACES_LINUX_SELECTOR = " show dev ";

	/** Command to get and set Ethernet interface details under Linux systems. */
	public final static String ETHTOOL_COMMAND = "/usr/sbin/ethtool ";
	
	/** Simply the shell command (and if necessary path) to execute a standard POSIX shell. */
	public final static String SH_COMMAND = "sh";
	/** Path for the IPv6 configuration kernel options. */
	public final static String IPV6_CONFIG_TREE = "/proc/sys/net/ipv6/conf/";
	/** First part of the command to enable IPv6 address privacy (before interface name). */
	private final static String ENABLE_ADDRESS_PRIVACY_PART1 = "echo 2 > " + IPV6_CONFIG_TREE;
	/** Second part of the command to enable IPv6 address privacy (after interface name). */
	private final static String ENABLE_ADDRESS_PRIVACY_PART2 = "/use_tempaddr";
	/** Interface "name" to denote all network interface for kernel configuration options. */ 
	private final static String CONF_INTERFACES_ALL = "all";
	/** Interface "name" to denote the default kernel configuration options for new (hotplug enabled) network interfaces. */ 
	private final static String CONF_INTERFACES_DEFAULT = "default";

	/** Command to get and set network interface status under modern Linux systems (up/down mostly). */
	public final static String SET_INTERFACE = "/system/bin/ip link set";
	/** Option to set network interface up. */
	private final static String UP = "up";
	/** Option to set network interface down. */
	private final static String DOWN = "down";
	/** Delay between setting an interface down and up to force its IPv6 address to be reset (in milliseconds). */
	public final static int INTERFACE_DOWN_UP_DELAY = 500;

	/** This class represents an (IPv4 or IPv6) address with an optional network mask. */
	public static class InetAddressWithNetmask {
		public InetAddress address;
		public int subnetLength;

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
		
		StringTokenizer lines = null;
		LinkedList<InterfaceDetail> list = new LinkedList<InterfaceDetail>();
		
		try {
			lines =	new StringTokenizer(Command.executeCommand(
						GET_INTERFACES_LINUX + (iface != null ? 
						 (GET_INTERFACES_LINUX_SELECTOR + iface) : ""),
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
		while (lines.hasMoreTokens()) {
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

	/**
	 * Executes the command ethtool for the given network interface and returns the output within a HashMap.
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
	
	/** Enable address privacy for all interfaces and potentially try to force reload. 
	 * @param forceAddressReload If set to true, each interface will also be 
	 *        reset by calling forceAddressReload.
	 * @return false if address privacy could not be set on any of the interfaces,
	 *         true if all of them could be set.
	 */
	public static boolean enableIPv6AddressPrivacy(boolean forceAddressReload) {
		boolean ret = true;
		LinkedList<String> allIfaces = new LinkedList<String>();
		
		// include the special "default" and "all" trees
		allIfaces.add(CONF_INTERFACES_ALL);
		allIfaces.add(CONF_INTERFACES_DEFAULT);
		
		// for now, use static interface names
		// TODO: take all interfaces with IPv6 addresses as well
		allIfaces.add("eth0");
		allIfaces.add("rmnet0");
		allIfaces.add("rmnet1");
		allIfaces.add("rmnet2");
		allIfaces.add("ip6tnl0");
		
		for (String iface: allIfaces) {
			File configDir = new File(IPV6_CONFIG_TREE + iface); 
			// only try to enable if this is indeed known as an IPv6-capable interface to the kernel
			if (configDir.isDirectory())
				if (enableIPv6AddressPrivacy(iface)) {
						if (forceAddressReload && !iface.equals(CONF_INTERFACES_ALL) && !iface.equals(CONF_INTERFACES_DEFAULT))
							enableIPv6AddressPrivacy(iface);
				}
				else
					ret = false;
		}
		
		return ret;
	}
	
	/** Enable address privacy for a specific interface. This sets the 
	 * "use_tempaddr" kernel option to "2" for the given interface.
	 * 
	 * @return true if the kernel option could be set, false otherwise. 
	 */
	public static boolean enableIPv6AddressPrivacy(String iface) {
		try {
			if (Command.executeCommand(SH_COMMAND, true, ENABLE_ADDRESS_PRIVACY_PART1 + iface + ENABLE_ADDRESS_PRIVACY_PART2, null, null) == 0) {
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
		try {
			if (Command.executeCommand(SH_COMMAND, true, SET_INTERFACE + " " + iface + " " + DOWN, null, null) == 0) {
				// wait just a little for the interface to properly go down
				Thread.sleep(INTERFACE_DOWN_UP_DELAY);
				if (Command.executeCommand(SH_COMMAND, true, SET_INTERFACE + " " + iface + " " + UP, null, null) == 0) {
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
}
