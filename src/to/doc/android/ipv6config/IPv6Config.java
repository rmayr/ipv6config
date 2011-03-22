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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.TextView.BufferType;

public class IPv6Config extends Activity {
	public final static String LOG_TAG = "IPv6Config";
	
	private CheckBox autoStart;
	private CheckBox enablePrivacy;
	private TextView localAddresses;
	private TextView globalAddress;
	
	private SharedPreferences prefsPrivate;

	protected final static String PREFERENCES_STORE = "IPv6Config";
	protected final static String PREFERENCE_AUTOSTART = "autostart";
	protected final static String PREFERENCE_ENABLE_PRIVACY = "enablePrivacyExtensions";
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    
        prefsPrivate = getSharedPreferences(PREFERENCES_STORE, Context.MODE_PRIVATE);
        
        autoStart = (CheckBox) findViewById(R.id.checkboxAutostart);
        autoStart.setChecked(prefsPrivate.getBoolean(PREFERENCE_AUTOSTART, false));
        enablePrivacy = (CheckBox) findViewById(R.id.checkboxEnablePrivacy);
        enablePrivacy.setChecked(prefsPrivate.getBoolean(PREFERENCE_ENABLE_PRIVACY, true));

        localAddresses = (TextView) findViewById(R.id.viewLocalAddresses);
        globalAddress = (TextView) findViewById(R.id.viewGlobalAddress);

        displayLocalAddresses();
    }
    
    @Override
    public void onPause() {
    	Editor prefsPrivateEditor = prefsPrivate.edit();
		prefsPrivateEditor.putBoolean(PREFERENCE_AUTOSTART, autoStart.isChecked());
		prefsPrivateEditor.putBoolean(PREFERENCE_ENABLE_PRIVACY, enablePrivacy.isChecked());
		prefsPrivateEditor.commit();
		
		super.onPause();
    }

    public void determineAddress(View v) {
    	Log.d(LOG_TAG, "determineAddress clicked");

    	displayLocalAddresses();
    }

    public void forceAddressReload(View v) {
    	Log.d(LOG_TAG, "forceAddressReload clicked");
	
		/* Do the major processing in a background service that will 
		 * terminate after it's done so as not to block the main thread.
		 */
    	if (enablePrivacy.isChecked())
    		getApplicationContext().startService( new Intent(getApplicationContext(), StartAtBootService.class));
    }
    
    public void displayLocalAddresses() {
        // doesn't work on Android < 3.0
    	//getLocalAddresses();
    	
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
				
				localAddresses.setText(addrs, BufferType.SPANNABLE);
			}
		} catch (IOException e) {
			Log.e(LOG_TAG, "Unable to get interface detail, most probably because system command " + 
					LinuxIPCommandHelper.GET_INTERFACES_LINUX + " could not be executed. " +
					"Missing access rights? " + e.toString());
			e.printStackTrace();
		}
    }
    
    /** This method doesn't work on Android pre-Honeycomb (3.0) systems for getting IPv6 addresses. */ 
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