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

import java.util.Enumeration;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Bundle;
import android.os.Environment;

import com.funambol.client.controller.Controller;
import com.funambol.client.controller.DevSettingsScreenController;
import com.funambol.client.controller.UISyncSourceController;
import com.funambol.client.customization.Customization;
import com.funambol.client.localization.Localization;
import com.funambol.client.source.AppSyncSource;
import com.funambol.platform.NetworkStatus;
import com.funambol.storage.StringKeyValueStoreFactory;
import com.funambol.sync.SyncSource;
import com.funambol.util.AndroidLogAppender;
import com.funambol.util.FileAppender;
import com.funambol.util.Log;
import com.funambol.util.MultipleAppender;

import de.chbosync.android.syncmlclient.activities.AndroidActivitiesFactory;
import de.chbosync.android.syncmlclient.activities.AndroidDisplayManager;
import de.chbosync.android.syncmlclient.controller.AndroidController;
import de.chbosync.android.syncmlclient.controller.AndroidHomeScreenController;
import de.chbosync.android.syncmlclient.controller.AndroidSettingsScreenController;


/**
 * This class is used to initialize the entire application. It can be invoked by
 * a starting activity or by a service. Once the application got initialized
 * once, any call to init has no effect. The Singleton instance is realized, so
 * this class reference can be got using the static related getter method.
 */
public class AppInitializer {

    private static final String TAG_LOG = "AppInitializer";

    private Localization localization;
    private AndroidConfiguration configuration;
    private Customization customization;
    private AndroidAppSyncSourceManager appSyncSourceManager;
    private AndroidController controller;
    private Context context;
    private WifiLock wifiLock = null;
    private SyncLock syncLock;

    private boolean initialized = false;


    /**
     * @param app the application reference
     */
    public AppInitializer(Application app) {
        this.context = app.getApplicationContext();
    }

    /**
     * AndroidController instance Getter method
     * @return AndroidController the instance of AndroidController object
     * initialized by this class
     */
    public AndroidController getController() {
        return controller;
    }
    
    /**
     * AndroidLocalization instance Getter method
     * @return AndroidLocalization the instance of AndroidLocalization object
     * used by this class
     */
    public Localization getLocalization() {
        return localization;
    }

    /**
     * AndroidConfiguration instance Getter method
     * @return AndroidConfiguration the instance of AndroidConfiguration object
     * used by this class
     */
    public AndroidConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * AndroidCustomization instance Getter method
     * @return AndroidCustomization the instance of AndroidCustomization object
     * used by this class
     */
    public Customization getCustomization() {
        return customization;
    }

    public SyncLock getSyncLock() {
        return syncLock;
    }

    /**
     * AndroidAppSyncSourceManager instance Getter method
     * @return AndroidAppSyncSourceManager the instance of
     * AndroidAppSyncSourceManager initialized by this class
     */
    public AndroidAppSyncSourceManager getAppSyncSourceManager() {
        return appSyncSourceManager;
    }


    /**
     * Initialize Funambol's custom logger.
     */
    private void initLog() {
    	
        MultipleAppender multiAppender = new MultipleAppender();

        if ( isFileTracingAllowed() ) { // returns always false
        	String fileName = "synclog.txt";
        	String userDir;

        	if (AndroidUtils.isSDCardMounted()) {
        		userDir = Environment.getExternalStorageDirectory().getPath() +
        				System.getProperty("file.separator");
        	} else {
        		userDir = context.getFilesDir().getAbsolutePath() +
        				System.getProperty("file.separator");
        	}

        	FileAppender fileAppender = new FileAppender(userDir, fileName);
        	fileAppender.setLogContentType(!AndroidUtils.isSDCardMounted());
        	fileAppender.setMaxFileSize(256*1024); // Set 256KB log size
        	multiAppender.addAppender(fileAppender);

            if (Log.isLoggable(Log.INFO)) {
                Log.info(TAG_LOG, "Memory card present: " + AndroidUtils.isSDCardMounted());
                Log.info(TAG_LOG, "Log file created into: " + userDir + fileName);
            }
        }

        // If we are running in the emulator, we also use the AndroidLogger
        if (AndroidUtils.isAndroidEmulator() || "debug".equals(BuildInfo.MODE)) {
            // This is an emulator, or a debug build
            AndroidLogAppender androidLogAppender = new AndroidLogAppender("ChBoSync");
            multiAppender.addAppender(androidLogAppender);            
        }

        Log.initLog(multiAppender, Log.TRACE);
        
        //for customer who wants to have log locked on specified level
        if( customization.lockLogLevel() ){
            Log.lockLogLevel( customization.getLockedLogLevel() );
        }
    }

    
    /**
     * Whether a trace/log file may be written. Requires Android permission, which is currently not granted
     * @return
     */
    private boolean isFileTracingAllowed() {
		return false;
	}

	/**
     * Initialize the application. Call this method to have the main application
     * objects fully initialized.
     * Call this method more than once per instance produces no effect.
     * This realize the same effect of init(Activity activity) but passing a
     * null value and generating the condition to not create the funambol
     * account. Use it carefully.
     */
    public synchronized void init() {
        init(null);
    }

    /**
     * Initialize the application. Call this method to have the main application
     * objects fully initialized. Call this method more than once per instance
     * produces no effect.
     * 
     * @param Activity is the Activity that is used to trigger the account 
     * creation if it doesn't exist. Pass null if you don't want to create the
     * account.
     */
    public synchronized void init(Activity activity) {

        if (initialized) {
            if(configuration.getCredentialsCheckPending()) {
                // Show the login screen if the credential check is still pending
                initAccount(activity);
            }
            return;
        }

        // Init all the Controller components
        initController();

        // Init account information
        initAccount(activity);

        // Init the SyncLock
        syncLock = new SyncLock();
        
        // Reset the proper log level
        Log.setLogLevel(configuration.getLogLevel());

        // Init the wifi lock if we need one
        initWifiLock();

        initialized = true;
    }

    
    /**
     * Initializes the controller of this application. this represents the core
     * of the initialization logic; in particular:
     *  - Init the log system
     *  - Set our own contacts activity as preferred
     *  - Try to create all the necessary sources
     *  - Create the home screen controller
     *  - Set the HomeScreenController reference to the Controller
     */
    public synchronized void initController() {

        boolean isControllerNull = controller == null;
        if(isControllerNull) {
             customization = AndroidCustomization.getInstance();
            // Init the log system
            initLog();

            localization = AndroidLocalization.getInstance(context);
           
            appSyncSourceManager = AndroidAppSyncSourceManager.getInstance(customization, localization, context);

            configuration = AndroidConfiguration.getInstance(context,
                    customization, appSyncSourceManager);
            configuration.load();
        }
           
        controller = AndroidController.getInstance(App.i(),
                new AndroidActivitiesFactory(), configuration, customization,
                localization, appSyncSourceManager);

        configuration.setController(controller);

        if(isControllerNull) {

            // Initialize the StringKeyValueStoreFactory
            StringKeyValueStoreFactory.getInstance().init(context, ((AndroidCustomization)customization).getFunambolSQLiteDbName());

            // Register all the necessary sources
            Enumeration<Integer> sources = this.customization.getAvailableSources();
            while(sources.hasMoreElements()) {
                Integer appSourceId = sources.nextElement();
                try {
                    AppSyncSource appSource = appSyncSourceManager.setupSource(appSourceId.intValue(), configuration);
                    // Create the sync source controller
                    UISyncSourceController itemController = appSource.createUISyncSourceController();
                    appSource.setUISyncSourceController(itemController);
                    itemController.init(customization, localization,
                                        appSyncSourceManager,
                                        controller,
                                        appSource);
                    SyncSource source = appSource.getSyncSource();
                    source.setListener(itemController);
                    appSyncSourceManager.registerSource(appSource);
                } catch (Exception e) {
                    Log.error(TAG_LOG, "Cannot setup source: " + appSourceId, e);
                }
            }
        }

        NetworkStatus netStatus = new NetworkStatus(context);
        AndroidHomeScreenController homeScreenController =
                new AndroidHomeScreenController(context, controller, null, netStatus);
        controller.setHomeScreenController(homeScreenController);

        AndroidSettingsScreenController settingsScreenController =
                new AndroidSettingsScreenController(context, controller);
        controller.setSettingsScreenController(settingsScreenController);

        DevSettingsScreenController devSettingsScreenController =
                new DevSettingsScreenController(controller, null);
        controller.setDevSettingsScreenController(devSettingsScreenController);

        initialized = true;
    }

    /**
     * Acquires a lock on wifi connection if it is not already locked
     */
    public void acquireWiFiLock() {
        if (wifiLock == null) {
            WifiManager wm = (WifiManager) context.getSystemService (Context.WIFI_SERVICE);
            // WIFI_FULL_MODE is bugged, couldn't work with some devices :(
            // http://www.mail-archive.com/android-developers@googlegroups.com/msg145553.html
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL, "Funambol sync");
        }

        if (!wifiLock.isHeld()) {
            wifiLock.setReferenceCounted(false);
            wifiLock.acquire();
            if (Log.isLoggable(Log.INFO)) {
                Log.info(TAG_LOG, "wifi lock=" + wifiLock.toString());
            }
        }
    }

    /**
     * Releases a lock on wifi, only if it's already locked and
     * if the bandwidth saver feature is not enabled.
     * 
     * @param requestComesFromSync True if the release request comes from a
     *        sync. In this case, the lock is released only if the bandwidth
     *        option is disabled. If false, releases the wifi without further checks
     */
    public void releaseWiFiLock(boolean requestComesFromSync) {
        if (wifiLock != null && wifiLock.isHeld()) {
            if (!requestComesFromSync ||
               (requestComesFromSync && !configuration.getBandwidthSaverActivated())) {
                if (Log.isLoggable(Log.INFO)) {
                    Log.info(TAG_LOG, "Releasing wifi lock");
                }
                wifiLock.release();
            }
        }
    }

    private void initWifiLock() {
        if (configuration.getBandwidthSaverActivated()) {
            acquireWiFiLock();
        }
    }

    private void initAccount(Activity activity) {

        Account account = AndroidController.getNativeAccount();

        // Do nothing if the request doesn't come from an activity
        if(activity == null) {
            return;
        }

        // Check if there is not funambol account
        if(account == null) {
            if (Log.isLoggable(Log.DEBUG)) {
                Log.debug(TAG_LOG, "Account not found, create a default one");
            }
            // Create the account through our account authenticator
            AccountManager am  = AccountManager.get(context);
            am.addAccount(context.getString(R.string.account_type), null, null, null, activity,
                new AccountManagerCallback<Bundle>() {
                    public void run(AccountManagerFuture<Bundle> result) {
                        try {
                            // Get the authenticator result, it is blocking until the
                            // account authenticator completes
                            result.getResult();
                            if (Log.isLoggable(Log.DEBUG)) {
                                Log.debug(TAG_LOG, "Account created");
                            }
                        } catch (Exception e) {
                            Log.error(TAG_LOG, "Exception during account creation: ", e);
                        }
                    }
                }, null);
        } else {
            if (Log.isLoggable(Log.DEBUG)) {
                Log.debug(TAG_LOG, "Account already defined");
            }
            // Show the login screen if credentials check is pending and
            // if the init request comes from the main activity
            if(configuration.getCredentialsCheckPending()) {
                try {
                    ((AndroidDisplayManager)controller.getDisplayManager())
                            .showScreen(context, Controller.LOGIN_SCREEN_ID, null);
                } catch(Exception ex) {
                    Log.error(TAG_LOG, "Cannot show login screen", ex);
                }
            }
        }
    }

}
