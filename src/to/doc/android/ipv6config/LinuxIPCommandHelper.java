package to.doc.android.ipv6config;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LinuxIPCommandHelper {
	/** Our logger for this class. */
	private final static Logger logger = java.util.logging.Logger.getLogger(LinuxIPCommandHelper.class.getName());

	/**constant for the String that identifies an ethernet interface*/
	private static final String ETHERNET_INTERFACE = "link/ether";

	/**constant for the String that starts the MTU option in the interface line*/
	private final static String INTERFACE_MTU = "mtu";

	/**constant for the String that starts the state option in the interface line*/
	//private final static String INTERFACE_STATE = "state";

	/**constant for the String that identifies an IPv4 address*/
	private final static String ADDRESS_IPV4 = "inet";

	/**constant for the String that identifies an IPv6 address*/
	private final static String ADDRESS_IPV6 = "inet6";

	/**constant for the command to get the interfaces in linux*/
	public static final String GET_INTERFACES_LINUX = "/sbin/ip addr";
	public static final String GET_INTERFACES_LINUX_SELECTOR = " show dev ";

	private final static String ETHTOOL_COMMAND = "/usr/sbin/ethtool ";

	public static class InterfaceDetail {
		public String name;
		public String mac;
		public boolean isUp;
		public int mtu;
		public LinkedList<Inet4Address> ipv4Addresses = new LinkedList<Inet4Address>();
		public LinkedList<Inet6Address> ipv6Addresses = new LinkedList<Inet6Address>();
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
	 *              empty. If set to null, return all interfaces.
	 * @return the output of the ip addr command to iterate and initialize the interfaces
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
						false, null), "\n");
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
//				logger.debug("Read interface line: " + cur.name + ", " + cur.mtu + ", " + cur.isUp);
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
//							logger.debug("getIfaceOutput: found mac " + cur.mac
//									+ " for " + cur.name);
						} else if (opt.equals(ADDRESS_IPV4)) {
							IPv4 addr;
							if (value.contains("/"))
								addr = new IPv4SubnetMask(value);
							else
								addr = new IPv4(value);
							cur.ipv4Addresses.add(addr);
//							logger.debug("getIfaceOutput: found IPv4 " + addr
//									+ " for " + cur.name);
						} else if (opt.equals(ADDRESS_IPV6)) {
							IPv6SubnetMask addr = new IPv6SubnetMask(value);
							cur.ipv6Addresses.add(addr);
//							logger.debug("getIfaceOutput: found IPv6 " + addr
//									+ " for " + cur.name);
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
			lines = new StringTokenizer(Command.executeCommand(ETHTOOL_COMMAND + device, false, null), "\n");
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
}
