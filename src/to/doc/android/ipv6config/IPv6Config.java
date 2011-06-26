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
import android.widget.TextView.BufferType;

public class IPv6Config extends Activity {
	private CheckBox autoStart;
	private CheckBox enablePrivacy;
	private CheckBox enable6to4Tunnel;
	private CheckBox force6to4Tunnel;
	private TextView localAddresses;
	private TextView v6GlobalAddress;
	private TextView v4GlobalAddress;
	private TextView v4LocalDefaultAddress;
	
	private SharedPreferences prefsPrivate;

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    
        prefsPrivate = getSharedPreferences(Constants.PREFERENCES_STORE, Context.MODE_PRIVATE);
        
        autoStart = (CheckBox) findViewById(R.id.checkboxAutostart);
        autoStart.setChecked(prefsPrivate.getBoolean(Constants.PREFERENCE_AUTOSTART, false));
        enablePrivacy = (CheckBox) findViewById(R.id.checkboxEnablePrivacy);
        enablePrivacy.setChecked(prefsPrivate.getBoolean(Constants.PREFERENCE_ENABLE_PRIVACY, true));
        enable6to4Tunnel = (CheckBox) findViewById(R.id.checkBoxEnable6to4Tunnel);
        enable6to4Tunnel.setChecked(prefsPrivate.getBoolean(Constants.PREFERENCE_CREATE_TUNNEL, false));
        force6to4Tunnel = (CheckBox) findViewById(R.id.checkBoxIgnoreExternalIPs);
        force6to4Tunnel.setChecked(prefsPrivate.getBoolean(Constants.PREFERENCE_FORCE_TUNNEL, false));

        localAddresses = (TextView) findViewById(R.id.viewLocalAddresses);
        v6GlobalAddress = (TextView) findViewById(R.id.viewv6GlobalAddress);
        v4GlobalAddress = (TextView) findViewById(R.id.viewv4GlobalAddress);
        v4LocalDefaultAddress = (TextView) findViewById(R.id.viewv4LocalDefaultAddress);

        displayLocalAddresses();

        /* and make sure that the service is running so that it registers for
         * connection change events, but don't let it do anything just now - noop 
         */
    	Intent serviceCall = new Intent(getApplicationContext(), StartAtBootService.class);
    	serviceCall.putExtra(StartAtBootService.SERVICE_COMMAND_PARAM, StartAtBootService.SERVICE_COMMAND_NOOP);
   		getApplicationContext().startService(serviceCall);
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
		prefsPrivateEditor.putBoolean(Constants.PREFERENCE_AUTOSTART, autoStart.isChecked());
		prefsPrivateEditor.putBoolean(Constants.PREFERENCE_ENABLE_PRIVACY, enablePrivacy.isChecked());
		prefsPrivateEditor.putBoolean(Constants.PREFERENCE_CREATE_TUNNEL, enable6to4Tunnel.isChecked());
		prefsPrivateEditor.putBoolean(Constants.PREFERENCE_FORCE_TUNNEL, force6to4Tunnel.isChecked());
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
    		Log.w(Constants.LOG_TAG, "v6Global = " + globalIPv6Addr);
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
				//enable6to4Tunnel.setEnabled(true);
				enable6to4Tunnel.setText(R.string.create6to4Tunnel);
				enable6to4Tunnel.setTextColor(Color.WHITE);
    		} else {
				v4Text += "\n" + getString(R.string.ipv4GlobalAddressNotMatchesLocal);
				v4GlobalAddress.setTextColor(Color.RED);
				//enable6to4Tunnel.setEnabled(false);
				enable6to4Tunnel.setText(getString(R.string.create6to4Tunnel) + " " + 
						getString(R.string.create6to4TunnelInvalid));
				enable6to4Tunnel.setTextColor(Color.YELLOW);
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
    			Log.e(Constants.LOG_TAG, "Unable to generate Inet6Address object from string " + v6Text, e);
    		}
    		v6GlobalAddress.setText(v6Text);
    	}
    }

    public void determineAddress(View v) {
    	Log.d(Constants.LOG_TAG, "determineAddress clicked");

    	displayLocalAddresses();

    	// this can take a few seconds, so do it asynchronously
    	v6GlobalAddress.setTextColor(Color.LTGRAY);
    	v6GlobalAddress.setText(R.string.determining);
    	v4GlobalAddress.setTextColor(Color.LTGRAY);
    	v4GlobalAddress.setText(R.string.determining);
    	new DetermineAddressTask().execute();
    }
    
    public void changeAddressPrivacyState(View v) {
    	Log.d(Constants.LOG_TAG, "checkBoxEnablePrivacy clicked/changed status");

    	// apply change immediately when clicking the checkbox, but don't reload until forced
    	Intent serviceCall = new Intent(getApplicationContext(), StartAtBootService.class);
    	serviceCall.putExtra(Constants.PREFERENCE_ENABLE_PRIVACY, enablePrivacy.isChecked());
   		getApplicationContext().startService(serviceCall);

    	// and reload address display
    	displayLocalAddresses();
    }

    public void forceAddressReload(View v) {
    	Log.d(Constants.LOG_TAG, "forceAddressReload clicked");
    	savePreferences();

    	Intent serviceCall = new Intent(getApplicationContext(), StartAtBootService.class);
    	serviceCall.putExtra(Constants.PREFERENCE_ENABLE_PRIVACY, enablePrivacy.isChecked());
    	serviceCall.putExtra(Constants.PREFERENCE_CREATE_TUNNEL, enable6to4Tunnel.isChecked());
    	serviceCall.putExtra(Constants.PREFERENCE_FORCE_TUNNEL, force6to4Tunnel.isChecked());
    	// force an address reload
    	serviceCall.putExtra(StartAtBootService.SERVICE_COMMAND_PARAM, StartAtBootService.SERVICE_COMMAND_RELOAD);
   		getApplicationContext().startService(serviceCall);

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
				Log.e(Constants.LOG_TAG, "Interface " + iface.name + " with MAC " + iface.mac + 
						" has addresses " + addrs + 
						(hasPrivacySensitiveAddress ? " and one of them is a globally traceable IPv6 address, WARNING" : ""));
				
				localAddresses.setText(addrs, BufferType.SPANNABLE);
			}
		} catch (IOException e) {
			Log.e(Constants.LOG_TAG, "Unable to get interface detail, most probably because system command " + 
					" could not be executed. Missing access rights? ", e);
		}
    }
}
