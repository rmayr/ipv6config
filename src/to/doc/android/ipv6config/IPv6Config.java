/*****************************************************************************
 *  Project: Android IPv6Config
 *  Description: Android application to change IPv6 kernel configuration
 *  Author: René Mayrhofer
 *  Copyright: René Mayrhofer, 2011-2014
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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.net.Uri;
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
	private final static String RECOMMENDED_BUSYBOX_INSTALLER_LINK = "market://details?id=com.jrummy.busybox.installer";
	
	private CheckBox autoStart;
	private CheckBox displayNotifications;
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
        displayNotifications = (CheckBox) findViewById(R.id.checkBoxDisplayNotifications);
        displayNotifications.setChecked(prefsPrivate.getBoolean(Constants.PREFERENCE_DISPLAY_NOTIFICATIONS, true));
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
        
        Log.v(Constants.LOG_TAG, "IPv6Config.onCreate starting sanity checks");
        
        // TODO: use AsyncTask or IntentService to query the system using SU 
        
        ////// SANITY CHECK 1 //////
        // before doing anything fancy, try to detect if we can get root privileges
        boolean canSu = false;
        try {
			if (Command.executeCommand(LinuxIPCommandHelper.SH_COMMAND, true, "", null, null) == 0)
				canSu = true;
			else
				Log.e(Constants.LOG_TAG, "Unable to execute sh with superuser access, notifying user and exiting");
		} catch (IOException e) {
			Log.e(Constants.LOG_TAG, "Unable to execute sh with superuser access, notifying user and exiting", e);
		} catch (InterruptedException e) {
			Log.e(Constants.LOG_TAG, "Unable to execute sh with superuser access, notifying user and exiting", e);
		}
		if (!canSu) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(R.string.noSuDetected)
		       .setCancelable(false)
		       .setNeutralButton(R.string.exit, new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		                IPv6Config.this.finish();
		           }
		       });
		    builder.create().show();
		}
		
        ////// SANITY CHECK 2 //////
		// check kernel /proc/sys/net/ipv6 tree to see if the kernel actually supports IPv6 address privacy
		if (!LinuxIPCommandHelper.isIPv6PrivacySupportInKernel()) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(R.string.ipv6PrivacyOptionsNotSupported).setCancelable(true);
		    builder.create().show();
		    enablePrivacy.setEnabled(false);
		    enablePrivacy.setChecked(false);
		}
		
        ////// SANITY CHECK 3 //////
		// and then verify if we have a working ip binary
		if (LinuxIPCommandHelper.getIPCommandLocation() == null) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(getString(R.string.noWorkingIpBinaryDetected) + 
					LinuxIPCommandHelper.getAllTriedIPCommandLocations())
		       .setCancelable(true);
		    builder.create().show();
		    enable6to4Tunnel.setEnabled(false);
		    enable6to4Tunnel.setChecked(false);
		    force6to4Tunnel.setEnabled(false);
		    force6to4Tunnel.setChecked(false);
		}

        Log.v(Constants.LOG_TAG, "IPv6Config.onCreate sanity checks passed, starting main display");

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
            	break;
            case R.id.email_menuitem:
            	Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
            	String aEmailList[] = { "rene@mayrhofer.eu.org" };
            	emailIntent.setType("plain/text");
            	emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, aEmailList);
            	emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "[IPv6Config feedback]");
            	startActivity(Intent.createChooser(emailIntent, getString(R.string.emailChooser)));
            	break;
            case R.id.install_busybox_menuitem:
            	Intent installIntent = new Intent(Intent.ACTION_VIEW);
            	installIntent.setData(Uri.parse(RECOMMENDED_BUSYBOX_INSTALLER_LINK));
            	startActivity(installIntent);
            	break;
        }
        return true;
    }
    
    private void savePreferences() {
    	Editor prefsPrivateEditor = prefsPrivate.edit();
		prefsPrivateEditor.putBoolean(Constants.PREFERENCE_AUTOSTART, autoStart.isChecked());
		prefsPrivateEditor.putBoolean(Constants.PREFERENCE_DISPLAY_NOTIFICATIONS, displayNotifications.isChecked());
		prefsPrivateEditor.putBoolean(Constants.PREFERENCE_ENABLE_PRIVACY, enablePrivacy.isChecked());
		prefsPrivateEditor.putBoolean(Constants.PREFERENCE_CREATE_TUNNEL, enable6to4Tunnel.isChecked());
		prefsPrivateEditor.putBoolean(Constants.PREFERENCE_FORCE_TUNNEL, force6to4Tunnel.isChecked());
		prefsPrivateEditor.commit();
    }
    
    /** A helper class to query the doc.to server for the externally visible 
     * IPv6 address asynchronously.
     */
    private class DetermineAddressTask extends AsyncTask<Void, Void, String> {
    	private boolean doIPv6;
    	private TextView globalAddress;
    	
    	protected DetermineAddressTask(boolean doIPv6) {
    		this.doIPv6 = doIPv6;
    		if (doIPv6)
    			globalAddress = v6GlobalAddress;
    		else
    			globalAddress = v4GlobalAddress;
    	}
    	
    	/** This method will be executed in a background thread when execute() is called. */
    	protected String doInBackground(Void... noParms) {
    		return IPv6AddressesHelper.getOutboundIPAddress(doIPv6);
    	}
    	
    	/** This method will be executed in the UI thread after doInBackground finishes. */
    	protected void onPostExecute(String outboundAddr) {
    		if (outboundAddr == null) {
    			globalAddress.setTextColor(Color.YELLOW);
    			outboundAddr = getString(R.string.determineFailed) + 
    				(doIPv6 ? " IPv6" : " IPv4");
    		} else if (!doIPv6) {
    			// special handling for IPv4 addresses
    			if (outboundAddr.equals(v4LocalDefaultAddress.getText())) {
    				outboundAddr += "\n" + getString(R.string.ipv4GlobalAddressMatchesLocal);
    				globalAddress.setTextColor(Color.LTGRAY);
				
    				//enable6to4Tunnel.setEnabled(true);
    				enable6to4Tunnel.setText(R.string.create6to4Tunnel);
    				enable6to4Tunnel.setTextColor(Color.WHITE);
    			} else {
    				outboundAddr += "\n" + getString(R.string.ipv4GlobalAddressNotMatchesLocal);
    				globalAddress.setTextColor(Color.RED);
				
    				//enable6to4Tunnel.setEnabled(false);
    				enable6to4Tunnel.setText(getString(R.string.create6to4Tunnel) + " " + 
						getString(R.string.create6to4TunnelInvalid));
    				enable6to4Tunnel.setTextColor(Color.YELLOW);
    			}
    		} else {
    			// special handling for IPv6 addresses
            	try {
            		if (IPv6AddressesHelper.isIPv6GlobalMacDerivedAddress(Inet6Address.getByName(outboundAddr))) {
        				outboundAddr += "\n" + getString(R.string.ipv6GlobalAddressIsMacDerived);
        				globalAddress.setTextColor(Color.RED);
        			}
        			else {
        				outboundAddr += "\n" + getString(R.string.ipv6GlobalAddressIsNotMacDerived);
        				globalAddress.setTextColor(Color.GREEN);
        			}
        		} catch (UnknownHostException e) {
        			Log.e(Constants.LOG_TAG, "Unable to generate Inet6Address object from string " + outboundAddr, e);
        		}
    		}
    		
    		globalAddress.setText(outboundAddr);
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
    	new DetermineAddressTask(false).execute();
    	new DetermineAddressTask(true).execute();
    }
    
    public void changeAddressPrivacyState(View v) {
    	Log.d(Constants.LOG_TAG, "checkBoxEnablePrivacy clicked/changed status");

    	// apply change immediately when clicking the checkbox, but don't reload until forced
    	Intent serviceCall = new Intent(getApplicationContext(), NetOpsService.class);
    	serviceCall.putExtra(Constants.PREFERENCE_ENABLE_PRIVACY, enablePrivacy.isChecked());
   		getApplicationContext().startService(serviceCall);

    	// and reload address display
    	displayLocalAddresses();
    }

    public void forceAddressReload(View v) {
    	Log.d(Constants.LOG_TAG, "forceAddressReload clicked");
    	savePreferences();

    	Intent serviceCall = new Intent(this, NetOpsService.class);
    	serviceCall.putExtra(Constants.PREFERENCE_ENABLE_PRIVACY, enablePrivacy.isChecked());
    	serviceCall.putExtra(Constants.PREFERENCE_CREATE_TUNNEL, enable6to4Tunnel.isChecked());
    	serviceCall.putExtra(Constants.PREFERENCE_FORCE_TUNNEL, force6to4Tunnel.isChecked());
    	// force an address reload
    	serviceCall.putExtra(NetOpsService.SERVICE_COMMAND_PARAM, NetOpsService.SERVICE_COMMAND_RELOAD);
   		startService(serviceCall);

    	// and reload address display
    	displayLocalAddresses();
    }
    
    public void displayLocalAddresses() {
    	// try to determine local address associated with default route
    	Inet4Address outboundIPv4Addr = LinuxIPCommandHelper.getOutboundIPv4Address();
    	Log.i(Constants.LOG_TAG, "test1");
    	if (outboundIPv4Addr != null)
    		v4LocalDefaultAddress.setText(outboundIPv4Addr.getHostAddress());
    	else
    		v4LocalDefaultAddress.setText(R.string.determineLocalFailed);
    	Log.i(Constants.LOG_TAG, "test1");
    	
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
