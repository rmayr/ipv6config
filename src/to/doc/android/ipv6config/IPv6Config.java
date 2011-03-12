package to.doc.android.ipv6config;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.Vector;

import to.doc.android.ipv6config.LinuxIPCommandHelper.InterfaceDetail;
import to.doc.android.ipv6config.LinuxIPCommandHelper.InetAddressWithNetmask;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

public class IPv6Config extends Activity {
	public final static String LOG_TAG = "IPv6Config";
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        getLocalAddresses();
    }
    
    public void forceAddressReload(View v) {
    	Log.e(LOG_TAG, "clicked");
    	getLocalAddresses();
    	
    	try {
			LinkedList<InterfaceDetail> ifaces = LinuxIPCommandHelper.getIfaceOutput();
			for (InterfaceDetail iface : ifaces) {
				StringBuilder addrs = new StringBuilder();
				boolean hasPrivacySensitiveAddress = false;
				for (InetAddressWithNetmask addr : iface.addresses) {
					addrs.append(addr.address.toString() + " ");
					if (addr.isIPv6GlobalMacDerivedAddress()) hasPrivacySensitiveAddress = true;
				}
				Log.e(LOG_TAG, "Interface " + iface.name + " with MAC " + iface.mac + 
						" has addresses " + addrs + 
						(hasPrivacySensitiveAddress ? " and one of them is a globally traceable IPv6 address, WARNING" : ""));
			}
		} catch (IOException e) {
			Log.e(LOG_TAG, "Unable to get interface detail, most probably because system command " + 
					LinuxIPCommandHelper.GET_INTERFACES_LINUX + " could not be executed. " +
					"Missing access rights? " + e.toString());
			e.printStackTrace();
		}
		
		// enable
		LinuxIPCommandHelper.enableIPv6AddressPrivacy();
    }
    
    public Vector<String> getLocalAddresses() {
    	Vector<String> addrs = new Vector<String>();
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
               	for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
               		InetAddress inetAddress = enumIpAddr.nextElement();
               		if (!inetAddress.isLoopbackAddress()) {
               			Log.e(LOG_TAG, "Found non-loopback address: " + inetAddress.getHostAddress());
               			addrs.add(inetAddress.getHostAddress());
               		}
               		
               		if (inetAddress instanceof Inet6Address) {
               			Log.e(LOG_TAG, "Found IPv6 address: " + inetAddress.getHostAddress());
               		}
                }
            }
        } catch (SocketException ex) {
            Log.e(LOG_TAG, ex.toString());
        }
        return addrs;
    }
}