package to.doc.android.ipv6config;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Vector;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

public class IPv6Config extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        getLocalAddresses();
    }
    
    public void forceAddressReload(View v) {
    	Log.e("IPv6Config", "clicked");
    	getLocalAddresses();
    }
    
    public Vector<String> getLocalAddresses() {
    	Vector<String> addrs = new Vector<String>();
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
               	for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
               		InetAddress inetAddress = enumIpAddr.nextElement();
               		if (!inetAddress.isLoopbackAddress()) {
               			Log.e("IPv6Config", "Found non-loopback address: " + inetAddress.getHostAddress());
               			addrs.add(inetAddress.getHostAddress());
               		}
               		
               		if (inetAddress instanceof Inet6Address) {
               			Log.e("IPv6Config", "Found IPv6 address: " + inetAddress.getHostAddress());
               		}
                }
            }
        } catch (SocketException ex) {
            Log.e("IPv6Config", ex.toString());
        }
        return addrs;
    }
}