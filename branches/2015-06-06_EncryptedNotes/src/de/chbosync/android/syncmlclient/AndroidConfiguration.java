/*
 * Funambol is a mobile platform developed by Funambol, Inc.
 * Copyright (C) 2009 Funambol, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License version 3 as published by
 * the Free Software Foundation with the addition of the following permission
 * added to Section 15 as permitted in Section 7(a): FOR ANY PART OF THE COVERED
 * WORK IN WHICH THE COPYRIGHT IS OWNED BY FUNAMBOL, FUNAMBOL DISCLAIMS THE
 * WARRANTY OF NON INFRINGEMENT  OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, see http://www.gnu.org/licenses or write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301 USA.
 *
 * You can contact Funambol, Inc. headquarters at 643 Bair Island Road, Suite
 * 305, Redwood City, CA 94063, USA, or at email address info@funambol.com.
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License version 3.
 *
 * In accordance with Section 7(b) of the GNU Affero General Public License
 * version 3, these Appropriate Legal Notices must retain the display of the
 * "Powered by Funambol" logo. If the display of the logo is not reasonably
 * feasible for technical reasons, the Appropriate Legal Notices must display
 * the words "Powered by Funambol".
 */

package de.chbosync.android.syncmlclient;


import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.telephony.TelephonyManager;

import com.funambol.client.configuration.Configuration;
import com.funambol.client.customization.Customization;
import com.funambol.client.source.AppSyncSourceManager;
import com.funambol.syncml.spds.DeviceConfig;
import com.funambol.util.Base64;

/**
 * Container for the main client client configuration information. Realized using
 * the singleton pattern. Access this class using the getInstance() method
 * invocation.
 */
public class AndroidConfiguration extends Configuration {

    private static final String TAG_LOG = "AndroidConfiguration";

    /** Key for the preferences file, will lead to file "fnblPref.xml"
     * in the app's folder "shared_prefs".
     */
    public static final String KEY_FUNAMBOL_PREFERENCES = "fnblPref";
    
    private static AndroidConfiguration instance = null;
    private        Context context;
    protected      SharedPreferences settings;
    protected      SharedPreferences.Editor editor;
    private        DeviceConfig devconf;

    /**
     * Private contructor to enforce the Singleton implementation.
     * 
     * The SharedPreferences will be saved at the following path on the Android device:
     * /data/data/de.chbosync.android.syncmlclient/shared_prefs/fnblPref.xml
     * 
     * @param context the application Context
     * @param customization the Customization object passed by the getInstance
     * call
     * @param appSyncSourceManager the AppSyncSourceManager object. Better to
     * use an AndroidAppSyncSourceManager or an extension of its super class
     */
    private AndroidConfiguration(Context context,
                                 Customization customization,
                                 AppSyncSourceManager appSyncSourceManager)
    {
        super(customization, appSyncSourceManager);
        this.context = context;
        settings = context.getSharedPreferences(KEY_FUNAMBOL_PREFERENCES, 0); // Name for preferences file: fnblPref.xml
        editor = settings.edit();
    }

    /**
     * Static method that returns the AndroidConfiguration unique instance
     * @param context the application Context object
     * @param customization the AndoidCustomization object used in this client
     * @param appSyncSourceManager the AppSyncSourceManager object. Better to
     * use an AndroidAppSyncSourceManager or an extension of its super class
     * @return AndroidConfiguration an AndroidConfiguration unique instance
     */
    public static AndroidConfiguration getInstance(Context context,
                                                   Customization customization,
                                                   AppSyncSourceManager appSyncSourceManager)
    {
        if (instance == null) {
            instance = new AndroidConfiguration(context, customization, appSyncSourceManager);
        }
        return instance;
    }

    /**
     * Dispose this object referencing it with the null object
     */
    public static void dispose() {
        instance = null;
    }

    /**
     * Load the value referred to the configuration given the key
     * @param key the String formatted key representing the value to be loaded
     * @return String String formatted value related to the given key
     */
    protected String loadKey(String key) {
        return settings.getString(key, null);
    }

    /**
     * Save the loaded twin key-value using the android context package
     * SharedPreferences.Editor instance
     * @param key the key to be saved
     * @param value the value related to the key String formatted
     */
    protected void saveKey(String key, String value) {
        editor.putString(key, value);
    }

    /**
     * Save the loaded twin key-value using the android context package
     * SharedPreferences.Editor instance
     * @param key the key to be saved
     * @param value the value related to the key byte[] formatted
     */
    public void saveByteArrayKey(String key, byte[] value) {
        String b64 = new String(Base64.encode(value));
        saveKey(key, b64);
    }

    /**
     * Load the value referred to the configuration given the key and the
     * default value
     * @param key the String formatted key representing the value to be loaded
     * @param defaultValue the default byte[] formatted value related to the
     * given key
     * @return byte[] String formatted vlaue related to the give key byte[]
     * formatted
     */
    public byte[] loadByteArrayKey(String key, byte[] defaultValue) {
        String b64 = loadKey(key);
        if (b64 != null) {
            return Base64.decode(b64);
        } else {
            return defaultValue;
        }
    }

    /**
     * Commit the changes
     * @return true if new values were correctly written into the persistent
     * storage
     */
    public boolean commit() {
        return editor.commit();
    }

    /**
     * Get the device id related to this client. Useful when doing syncml
     * requests
     * @return String the device id that is formatted as the string "fac-" plus
     * the information of the deviceId field got by the TelephonyManager service
     */
    protected String getDeviceId() {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        // must have android.permission.READ_PHONE_STATE
        String deviceId = tm.getDeviceId();
        return ((AndroidCustomization)customization).getDeviceIdPrefix() + deviceId;
    }

    /**
     * Get the device related configuration
     * @return DeviceConfig the DeviceConfig object related to this device
     */
    public DeviceConfig getDeviceConfig() {
        if (devconf != null) {
            return devconf;
        }
        devconf = new DeviceConfig();
        devconf.setMan(Build.MANUFACTURER);
        devconf.setMod(Build.MODEL);
        // See here for possible values of SDK_INT
        // http://developer.android.com/reference/android/os/Build.VERSION_CODES.html
        devconf.setSwV(Build.VERSION.CODENAME + "(" + Build.VERSION.SDK_INT + ")");
        devconf.setFwV(devconf.getSwV());
        devconf.setHwV(Build.FINGERPRINT);
        devconf.setDevID(getDeviceId());
        devconf.setMaxMsgSize(64 * 1024);
        devconf.setLoSupport(true);
        devconf.setUtc(true);
        devconf.setNocSupport(true);
        devconf.setWBXML(customization.getUseWbxml());
        return devconf;
    }

    /**
     * Get the user agent id related to this client. Useful when doing syncml
     * requests.
     */
    protected String getUserAgent() {
    	
        StringBuffer ua = new StringBuffer( ((AndroidCustomization)customization).getUserAgentName() );
                        
        ua.append(" ");
        
        String appVersion = AndroidUtils.getVersionNumberFromManifest(context);
        
        ua.append(appVersion);
        return ua.toString();
    }

    /**
     * Migrate the configuration (anything specific to the client)
     */
    @Override
    protected void migrateConfig() {

        // From 6 to 7 means from Diablo to Gallardo, where we introduced a new
        // mechanism for picture sync. We need to check what the server supports
        // to switch to the new method.
        if ("6".equals(version)) {
            setForceServerCapsRequest(true);
        }

        // In version 11 we introduced the c2sPushEnabled property. On Android
        // we can use the master auto sync in order to initialize it to a proper
        // value.
        int versionNumber = Integer.parseInt(version);
        if(versionNumber < 11) {
            boolean masterAutoSync = ContentResolver.getMasterSyncAutomatically();
            setC2SPushEnabled(masterAutoSync);
        }

        // Now migrate the basic configuration (this will update version)
        super.migrateConfig();
    }
    
    
    // *** Starting here: Methods/attributes added for ChBoSync ***
    
    /** Key for the preference "showDummyButtonForNotesSyncing". */
    protected static final String CONF_SHOW_DUMMY_BUTTON_FOR_SYNCING_NOTES = "DUMMY_BUTTON_FOR_SYNCING_NOTES";
    
    /** Key for the preference "detectionOfEncryptedNotesEnabled". */
    protected static final String CONF_DETECTION_OF_ENCRYPTED_NOTES_ENABLED = "DETECTION_OF_ENCRYPTED_NOTES";
    
    /** Member variable holding the current state of the preference value "showDummyButtonForNotesSyncing". */
    protected boolean showDummyButtonForNotesSyncing = true;
    
    /** Member variable holding the current state of the preferen value "detectionOfEncryptedNotesEnabled". */
    protected boolean detectionOfEncryptedNotesEnabled = false;
    
    
    /**
     * Getter for preference "showDummyButtonForNotesSyncing".
     * 
     * @return <tt>true</tt> if dummy button instead of sync button
     *         for notes should be shown when "OI Notepad" is not available
     *         on the device; <tt>false</tt> otherwise.
     */
    public boolean getShowDummyButtonForNotesSyncing() {
    	return showDummyButtonForNotesSyncing;
    }
    
    
    /**
     * Setter for preference "showDummyButtonForNotesSyncing".
     * 
     * @param enabled <tt>true</tt> if dummy button instead of sync button
     *        for notes should be shown when "OI Notepad" is not available
     *        on the device; <tt>false</tt> otherwise.
     */
    public void setShowDummyButtonForNotesSyncing(boolean enabled) {
    	showDummyButtonForNotesSyncing = enabled;
    }

    
    /**
     * Getter for preference "detectionOfEncryptedNotesEnabled".
     * 
     * @return <tt>true</tt> iff detection of encrypted notes is currently enabled.
     */
    public boolean getDetectionOfEncryptedNotesEnabled() {
    	return detectionOfEncryptedNotesEnabled;
    }
    
    /**
     * Setter for preference "detectionOfEncryptedNotesEnabled".
     * 
     * @param <tt>true</tt> iff detection of encrypted notes is to be enabled. 
     */
    public void setDetectionOfEncryptedNotesEnabled(boolean enabled) {
    	detectionOfEncryptedNotesEnabled = enabled;
    }
    
    /**
     * Overwriting of method for loading of preferences from shared preferences, so 
     * that preference for "showDummyButtonForNotesSyncing" is also loaded.
     */
	@Override
	public int load() {

		if (loaded) {
			return CONF_OK;
		}
		
		showDummyButtonForNotesSyncing   = loadBooleanKey(CONF_SHOW_DUMMY_BUTTON_FOR_SYNCING_NOTES, true  ); // "true" is default value
		
		detectionOfEncryptedNotesEnabled = loadBooleanKey(CONF_DETECTION_OF_ENCRYPTED_NOTES_ENABLED, false); // false: disabled by default
		
		return super.load();
	}
    
	
	/**
	 * Overwriting of method for loading of preferences from shared preferences, so 
     * that preference for "showDummyButtonForNotesSyncing" is also saved.
     * 
	 * @return Either {@link com.funambol.client.configuration.Configuration.CONF_OK} or
	 *         {@link com.funambol.client.configuration.Configuration.CONF_INVALID}.
	 */
	@Override
	public int save() {
		
		saveBooleanKey(CONF_SHOW_DUMMY_BUTTON_FOR_SYNCING_NOTES, showDummyButtonForNotesSyncing);
		
		return super.save();
	}
    

}
