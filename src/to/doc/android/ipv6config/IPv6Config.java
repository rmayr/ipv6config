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

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.LinkedList;

import to.doc.android.ipv6config.LinuxIPCommandHelper.InterfaceDetail;
import to.doc.android.ipv6config.LinuxIPCommandHelper.InetAddressWithNetmask;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TextView.BufferType;

public class IPv6Config extends Activity {
	/** This tag is used for Android logging. */
	public final static String LOG_TAG = "IPv6Config";

	private CheckBox autoStart;
	private CheckBox enablePrivacy;
	private CheckBox enable6to4Tunnel;
	private TextView localAddresses;
	private TextView v6GlobalAddress;
	private TextView v4GlobalAddress;
	private TextView v4LocalDefaultAddress;
	
	private SharedPreferences prefsPrivate;

	protected final static String PREFERENCES_STORE = "IPv6Config";
	protected final static String PREFERENCE_AUTOSTART = "autostart";
	protected final static String PREFERENCE_ENABLE_PRIVACY = "enablePrivacyExtensions";
	protected final static String PREFERENCE_CREATE_TUNNEL = "enable6to4Tunneling";
	
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
        enable6to4Tunnel = (CheckBox) findViewById(R.id.checkBoxEnable6to4Tunnel);
        enable6to4Tunnel.setChecked(prefsPrivate.getBoolean(PREFERENCE_CREATE_TUNNEL, false));

        localAddresses = (TextView) findViewById(R.id.viewLocalAddresses);
        v6GlobalAddress = (TextView) findViewById(R.id.viewv6GlobalAddress);
        v4GlobalAddress = (TextView) findViewById(R.id.viewv4GlobalAddress);
        v4LocalDefaultAddress = (TextView) findViewById(R.id.viewv4LocalDefaultAddress);

        displayLocalAddresses();
    }

    /** Called when the activity is sent to the background or is terminated. */
    @Override
    public void onPause() {
		savePreferences();
		super.onPause();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        displayLocalAddresses();
    }
    
    /** Called when the activity menu is created */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    /** Called whenever a menu entry is selected. */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.about_menuitem:
            	startActivity(new Intent(this, About.class));
        }
        return true;
    }
    
    private void savePreferences() {
    	Editor prefsPrivateEditor = prefsPrivate.edit();
		prefsPrivateEditor.putBoolean(PREFERENCE_AUTOSTART, autoStart.isChecked());
		prefsPrivateEditor.putBoolean(PREFERENCE_ENABLE_PRIVACY, enablePrivacy.isChecked());
		prefsPrivateEditor.putBoolean(PREFERENCE_CREATE_TUNNEL, enable6to4Tunnel.isChecked());
		prefsPrivateEditor.commit();
    }
    
    /** A helper class to query the doc.to server for the externally visible 
     * IPv6 address asynchronously.
     */
    private class DetermineAddressTask extends AsyncTask<Void, Void, String[]> {
    	/** This method will be executed in a background thread when execute() is called. */
    	protected String[] doInBackground(Void... noParms) {
    		// this can take a few seconds
    		String globalIPv4Addr = IPv6AddressesHelper.getOutboundIPAddress(false);
    		String globalIPv6Addr = IPv6AddressesHelper.getOutboundIPAddress(true);
    		Log.w(LOG_TAG, "v6Global = " + globalIPv6Addr);
        	return new String[] {globalIPv4Addr, globalIPv6Addr};
    	}
    	
    	/** This method will be executed in the UI thread after doInBackground finishes. */
    	protected void onPostExecute(String[] outboundAddrs) {
    		String v4Text = outboundAddrs[0];
    		if (v4Text == null) {
    			v4GlobalAddress.setTextColor(Color.YELLOW);
    			v4Text = getString(R.string.determineFailed);
    		} else if (v4Text.equals(v4LocalDefaultAddress.getText())) {
				v4Text += "\n" + getString(R.string.ipv4GlobalAddressMatchesLocal);
				v4GlobalAddress.setTextColor(Color.BLUE);
				enable6to4Tunnel.setEnabled(true);
    		} else {
				v4Text += "\n" + getString(R.string.ipv4GlobalAddressNotMatchesLocal);
				v4GlobalAddress.setTextColor(Color.RED);
				enable6to4Tunnel.setEnabled(false);
    		}
    		v4GlobalAddress.setText(v4Text);
    		
    		String v6Text = outboundAddrs[1];
        	try {
        		if (v6Text == null) {
        			v6GlobalAddress.setTextColor(Color.YELLOW);
        			v6Text = getString(R.string.determineFailed);
        		} else if (IPv6AddressesHelper.isIPv6GlobalMacDerivedAddress(Inet6Address.getByName(v6Text))) {
    				v6Text += "\n" + getString(R.string.ipv6GlobalAddressIsMacDerived);
    				v6GlobalAddress.setTextColor(Color.RED);
    			}
    			else
    				v6GlobalAddress.setTextColor(Color.GREEN);
    		} catch (UnknownHostException e) {
    			Log.e(LOG_TAG, "Unable to generate Inet6Address object from string " + v6Text, e);
    		}
    		v6GlobalAddress.setText(v6Text);
    	}
    }

    public void determineAddress(View v) {
    	Log.d(LOG_TAG, "determineAddress clicked");

    	displayLocalAddresses();

    	// this can take a few seconds, so do it asynchronously
    	v6GlobalAddress.setTextColor(Color.LTGRAY);
    	v6GlobalAddress.setText(R.string.determining);
    	v4GlobalAddress.setTextColor(Color.LTGRAY);
    	v4GlobalAddress.setText(R.string.determining);
    	new DetermineAddressTask().execute();
    }
    
    public void changeAddressPrivacyState(View v) {
    	Log.d(LOG_TAG, "checkBoxEnablePrivacy clicked/changed status");

    	// apply change immediately when clicking the checkbox, but don't reload until forced
    	applySettingsWithGuiFeedback(getApplicationContext(), enablePrivacy.isChecked(), false,
    			enable6to4Tunnel.isChecked());

    	// and reload address display
    	displayLocalAddresses();
    }

    public void forceAddressReload(View v) {
    	Log.d(LOG_TAG, "forceAddressReload clicked");
    	savePreferences();
	
		/* Do the major processing in a background service that will 
		 * terminate after it's done so as not to block the main thread.
		 */
    	if (enablePrivacy.isChecked() && autoStart.isChecked())
    		getApplicationContext().startService( new Intent(getApplicationContext(), StartAtBootService.class));
    	else
    		/* TODO in the case that we can't rely on the StartAtBootService (which will only force reload when
    		 * both fields are checked), we simply call the force in the main thread. This is not optimal, 
    		 * should also do in the background here - most probably by making StartAtBootService more
    		 * configurable with service calling parameters? */
        	applySettingsWithGuiFeedback(getApplicationContext(), 
        			enablePrivacy.isChecked(), true, enable6to4Tunnel.isChecked());

    	// and reload address display
    	displayLocalAddresses();
    }
    
    public void displayLocalAddresses() {
    	// try to determine local address associated with default route
    	Inet4Address outboundIPv4Addr = LinuxIPCommandHelper.getOutboundIPv4Address();
    	if (outboundIPv4Addr != null)
    		v4LocalDefaultAddress.setText(outboundIPv4Addr.getHostAddress());
    	else
    		v4LocalDefaultAddress.setText(R.string.determineLocalFailed);
    	
        // doesn't work on Android < 3.0
    	//getLocalAddresses();
    	
    	try {
			LinkedList<InterfaceDetail> ifaces = LinuxIPCommandHelper.getIfaceOutput();
			if (ifaces == null) return;
			
			for (InterfaceDetail iface : ifaces) {
				StringBuilder addrs = new StringBuilder();
				boolean hasPrivacySensitiveAddress = false;
				for (InetAddressWithNetmask addr : iface.addresses) {
					addrs.append(addr.address.getHostAddress() + " ");
					if (addr.isIPv6GlobalMacDerivedAddress()) hasPrivacySensitiveAddress = true;
				}
				Log.e(LOG_TAG, "Interface " + iface.name + " with MAC " + iface.mac + 
						" has addresses " + addrs + 
						(hasPrivacySensitiveAddress ? " and one of them is a globally traceable IPv6 address, WARNING" : ""));
				
				localAddresses.setText(addrs, BufferType.SPANNABLE);
			}
		} catch (IOException e) {
			Log.e(LOG_TAG, "Unable to get interface detail, most probably because system command " + 
					" could not be executed. Missing access rights? ", e);
		}
    }
    
    public static void applySettingsWithGuiFeedback(Context context, 
    		boolean enablePrivacy, boolean forceReload,
    		boolean enable6to4Tunnel) {
    	if (LinuxIPCommandHelper.enableIPv6AddressPrivacy(enablePrivacy, forceReload))
		    Toast.makeText(context, 
		    		enablePrivacy ? context.getString(R.string.toastEnableSuccess) : context.getString(R.string.toastDisableSuccess), 
	        		Toast.LENGTH_LONG).show();
		else
		    Toast.makeText(context, 
		    		enablePrivacy ? context.getString(R.string.toastEnableFailure) : context.getString(R.string.toastDisableFailure),
	        		Toast.LENGTH_LONG).show();

    	if (enable6to4Tunnel) {
			// determine outbound IPv4 address based on routes
			Inet4Address outboundIPv4Addr = LinuxIPCommandHelper.getOutboundIPv4Address();
			// determine outbound IPv4 address as seen from the outside
    		String globalIPv4AddrStr = IPv6AddressesHelper.getOutboundIPAddress(false);
    		Inet4Address globalIPv4Addr;
			try {
				globalIPv4Addr = (Inet4Address) Inet4Address.getByName(globalIPv4AddrStr);
			} catch (UnknownHostException e) {
				Log.w(LOG_TAG, "Unable to parse globally visible IPv4 address '" +
						globalIPv4AddrStr + "', probably unable to contact resolver server", e);
				globalIPv4Addr = null;
			} catch (ClassCastException e) {
				Log.e(LOG_TAG, "Unable to parse globally visible IPv4 address '" +
						globalIPv4AddrStr + "', unknown reason", e);
				globalIPv4Addr = null;
			}
			
				// check if we should create a tunnel now (i.e. if there is any IPv6 default route)
			if (! LinuxIPCommandHelper.existsIPv6DefaultRoute() &&
				// check if we could create a tunnel now (i.e. if local and global IPv4 addresses match)
				globalIPv4Addr != null && outboundIPv4Addr.equals(globalIPv4Addr)) {
				
				// both yes: do it. first delete tunnel if it exists (if it doesn't, don't mind)
				LinuxIPCommandHelper.deleteTunnelInterface(IPv6AddressesHelper.IPv6_6to4_TUNNEL_INTERFACE_NAME);

				// then create tunnel
				if (LinuxIPCommandHelper.create6to4TunnelInterface(
						IPv6AddressesHelper.IPv6_6to4_TUNNEL_INTERFACE_NAME, 
						outboundIPv4Addr, 
						IPv6AddressesHelper.compute6to4Prefix(outboundIPv4Addr), 0))
				    Toast.makeText(context, 
				    		context.getString(R.string.toast6to4Success), 
			        		Toast.LENGTH_LONG).show();
				else
				    Toast.makeText(context, 
				    		context.getString(R.string.toast6to4Failure), 
			        		Toast.LENGTH_LONG).show();
			}
			else if (globalIPv4Addr == null || ! outboundIPv4Addr.equals(globalIPv4Addr)) {
			    Toast.makeText(context, 
			    		context.getString(R.string.toast6to4AddressMismatch), 
		        		Toast.LENGTH_LONG).show();
			}
		}
    }
}
