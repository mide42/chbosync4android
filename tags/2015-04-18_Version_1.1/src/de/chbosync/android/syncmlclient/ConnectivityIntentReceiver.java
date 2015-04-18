/*
 * Funambol is a mobile platform developed by Funambol, Inc.
 * Copyright (C) 2010 Funambol, Inc.
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
import java.util.Vector;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.funambol.client.configuration.Configuration;
import com.funambol.client.controller.SynchronizationController;
import com.funambol.client.source.AppSyncSourceConfig;
import com.funambol.client.source.AppSyncSourceManager;
import com.funambol.util.Log;

import de.chbosync.android.syncmlclient.controller.AndroidController;
import de.chbosync.android.syncmlclient.controller.AndroidHomeScreenController;


/**
 * BroadcastReceiver implementation for the Wifi status.
 */
public class ConnectivityIntentReceiver extends BroadcastReceiver {
    private static final String TAG = "ConnectivityIntentReceiver";

    private final Object lock = new Object();
    
    /**
     * Defines what to do when the BroadcastReceiver is triggered.
     * @param context the application Context
     * @param intent the Intent to be launched to start the service - Not used
     * use a custom defined intent with a predefined service action
     */
    @Override
    public void onReceive(Context context, Intent intent) {

        final String action = intent.getAction();
        if (Log.isLoggable(Log.INFO)) {
            Log.info(TAG, "onReceiveConnectionChange " + action);
        }

        synchronized(lock) {
            NetworkInfo netInfo = (NetworkInfo)intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
            if (netInfo != null) {
                if (netInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                    if (netInfo.isConnected()) {
                        if (Log.isLoggable(Log.INFO)) {
                            Log.info(TAG, "WiFi connected");
                        }

                        // Grab the list of sources and trigger a sync for the ones
                        // that have a pending sync
                        AppInitializer appInitializer = App.i().getAppInitializer();
                        appInitializer.init();
                        Configuration configuration = appInitializer.getConfiguration();
                        AndroidController controller = appInitializer.getController();
                        AppSyncSourceManager appSourceManager = appInitializer.getAppSyncSourceManager();
                        Enumeration appSources = appSourceManager.getEnabledAndWorkingSources();
                        if (Log.isLoggable(Log.INFO)) {
                            Log.info(TAG, "WiFi connected");
                        }

                        Vector sources = null;
                        AndroidHomeScreenController hsc;
                        hsc = (AndroidHomeScreenController)controller.getHomeScreenController();
                        sources = new Vector();
                        while(appSources.hasMoreElements()) {
                            AndroidAppSyncSource appSource = (AndroidAppSyncSource)appSources.nextElement();
                            // If this source authority is already being
                            // synchronized (or pending) then there is no need
                            // to check. We need this extra check because the
                            // platform may generate several Intents when WiFi
                            // gets connected

                            AppSyncSourceConfig  appSourceConfig = appSource.getConfig();
                            if (Log.isLoggable(Log.TRACE)) {
                                Log.trace(TAG, "Checking if source " + appSource.getName() + " must be synced");
                            }
                            if (appSourceConfig.getPendingSyncType() != null &&
                                appSource.getBandwidthSaverUse() &&
                                configuration.getBandwidthSaverActivated())
                            {
                                if (Log.isLoggable(Log.TRACE)) {
                                    Log.trace(TAG, "Yes, a sync is pending");
                                }
                                sources.addElement(appSource);
                            }
                        }
                        if (sources.size() > 0) {
                            if (Log.isLoggable(Log.DEBUG)) {
                                Log.debug(TAG, "HomeScreenController available, use it to sync");
                            }
                            hsc.synchronize(SynchronizationController.PUSH, sources);
                        }
                    } else {
                        if (Log.isLoggable(Log.INFO)) {
                            Log.info(TAG, "WiFi disconnected");
                        }
                    }
                }
            }
        }
    }
}

