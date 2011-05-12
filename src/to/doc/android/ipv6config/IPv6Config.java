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

    /** Called when the activity is sent to the background or is terminated. */
    @Override
    public void onPause() {
		savePreferences();
		super.onPause();
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
		prefsPrivateEditor.commit();
    }
    
    /** A helper class to query the doc.to server for the externally visible 
     * IPv6 address asynchronously.
     */
    private class DetermineAddressTask extends AsyncTask<Void, Void, String> {
    	/** This method will be executed in a background thread when execute() is called. */
    	protected String doInBackground(Void... noParms) {
    		// this can take a few seconds
        	return IPv6AddressesHelper.getOutboundIPv6Address();
    	}
    	
    	/** This method will be executed in the UI thread after doInBackground finishes. */
    	protected void onPostExecute(String outboundIPv6Addr) {
    		String text = outboundIPv6Addr;
        	try {
        		if (outboundIPv6Addr == null) {
        			globalAddress.setTextColor(Color.YELLOW);
        			text = "Unable to determine address, resolver server not reachable";
        		} else if (IPv6AddressesHelper.isIPv6GlobalMacDerivedAddress(Inet6Address.getByName(outboundIPv6Addr))) {
    				text += "\nWARNING: privacy extensions not in use, device can be globally tracked";
    				globalAddress.setTextColor(Color.RED);
    			}
    			else
    				globalAddress.setTextColor(Color.GREEN);
    		} catch (UnknownHostException e) {
    			Log.e(LOG_TAG, "Unable to generate Inet6Address object from string " + outboundIPv6Addr, e);
    		}
    		globalAddress.setText(text);
    	}
    }

    public void determineAddress(View v) {
    	Log.d(LOG_TAG, "determineAddress clicked");

    	displayLocalAddresses();

    	// this can take a few seconds, so do it asynchronously
    	globalAddress.setTextColor(Color.LTGRAY);
    	globalAddress.setText("Determining address...");
    	new DetermineAddressTask().execute();
    }
    
    public void changeAddressPrivacyState(View v) {
    	Log.d(LOG_TAG, "checkBoxEnablePrivacy clicked/changed status");

    	// apply change immediately when clicking the checkbox, but don't reload until forced
    	applySettingsWithGuiFeedback(getApplicationContext(), enablePrivacy.isChecked(), false);

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
        	applySettingsWithGuiFeedback(getApplicationContext(), enablePrivacy.isChecked(), true);

    	// and reload address display
    	displayLocalAddresses();
    }
    
    public void displayLocalAddresses() {
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
					" could not be executed. Missing access rights? " + e.toString());
		}
    }
    
    public static void applySettingsWithGuiFeedback(Context context, boolean enablePrivacy, boolean forceReload) {
		if (LinuxIPCommandHelper.enableIPv6AddressPrivacy(enablePrivacy, forceReload))
		    Toast.makeText(context, 
		    		enablePrivacy ? context.getString(R.string.toastEnableSuccess) : context.getString(R.string.toastDisableSuccess), 
	        		Toast.LENGTH_LONG).show();
		else
		    Toast.makeText(context, 
		    		enablePrivacy ? context.getString(R.string.toastEnableFailure) : context.getString(R.string.toastDisableFailure),
	        		Toast.LENGTH_LONG).show();
    }
}