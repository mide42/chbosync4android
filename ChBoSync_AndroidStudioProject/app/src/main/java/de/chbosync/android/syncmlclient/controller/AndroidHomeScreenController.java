/*
# * Funambol is a mobile platform developed by Funambol, Inc.
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

package de.chbosync.android.syncmlclient.controller;

import java.util.List;
import java.util.Vector;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.funambol.client.controller.Controller;
import com.funambol.client.controller.HomeScreenController;
import com.funambol.client.source.AppSyncSource;
import com.funambol.client.ui.DisplayManager;
import com.funambol.client.ui.HomeScreen;
import com.funambol.platform.NetworkStatus;
import com.funambol.util.Log;

import de.chbosync.android.syncmlclient.R;
import de.chbosync.android.syncmlclient.AndroidCustomization;
import de.chbosync.android.syncmlclient.App;
import de.chbosync.android.syncmlclient.AppInitializer;
import de.chbosync.android.syncmlclient.activities.AndroidDisplayManager;
import de.chbosync.android.syncmlclient.activities.AndroidHomeScreen;
import de.chbosync.android.syncmlclient.services.AutoSyncServiceHandler;


public class AndroidHomeScreenController extends HomeScreenController {

    private static final String TAG_LOG = "AndroidHomeScreenController";

    private final int AUTO_SYNC_NOTIFICATION_ID = 1000;

    public static final String SYNC_TYPE = "SyncType";
    public static final String REFRESH   = "Refresh";
    public static final String REFRESH_DIRECTION = "RefreshDirection";
    public static final String AUTHORITY_TYPE = "AuthorityType";

    protected Context context;

    private final AndroidDisplayManager dm;

    private boolean syncAll = false;

    protected AutoSyncServiceHandler autoSyncServiceHandler;

    private final NotificationManager notificationManager;

    public AndroidHomeScreenController(Context context, Controller controller, HomeScreen homeScreen, NetworkStatus networkStatus) {
        super(controller, homeScreen, networkStatus);
        this.dm = (AndroidDisplayManager)controller.getDisplayManager();
        this.context = context;
        engine.setSpawnThread(true);
        engine.setNetworkStatus(networkStatus);
        autoSyncServiceHandler = new AutoSyncServiceHandler(context);
        notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public synchronized void synchronize(String syncType, List<AppSyncSource> syncSources,
                                         int delay, boolean fromOutside) {

        if (Log.isLoggable(Log.INFO)) {
            Log.info(TAG_LOG, "synchronize " + syncType);
        }

        // Check if the sync retry feature is enabled
        if(((AndroidCustomization)customization).getSyncRetryEnabled()) {
            // Keep track of a push sync attempt if nobody did it
            if(configuration.getCurrentSyncRetryCount() == -1 &&
                    PUSH.equals(syncType)) {
                configuration.setCurrentSyncRetryCount(0);
                configuration.save();
            }
        }

        if (!PUSH.equals(syncType) && isSynchronizing()) {
            if (Log.isLoggable(Log.INFO)) {
                Log.info(TAG_LOG, "A sync is already in progress");
            }
            //if(MANUAL.equals(syncType)) {
            //    showSyncInProgressMessage();
            //}
            return;
        }

        // Start the sync through the service
        autoSyncServiceHandler.startSync(syncType, syncSources);
    }

    public void fireSynchronization(String syncType, Vector syncSources) {
       fireSynchronization(syncType, syncSources, 0);
    }

    public void fireSynchronization(String syncType, Vector syncSources, int delay) {
        super.synchronize(syncType, syncSources, delay, false);
    }

    @Override
    public boolean syncStarted(Vector sources) {
        boolean result = super.syncStarted(sources);
        showSyncNotification();
        //lock wifi
        AppInitializer appInitializer = App.i().getAppInitializer();
        appInitializer.acquireWiFiLock();
        return result;
    }

    @Override
    public void syncEnded() {
        // Even during a sync all we are sure the code will end up here
        // because the syncAll is set to false in both sourceEnded and
        // sourceFailed.
        super.syncEnded(); // and all end-of-sync tasks are managed by its superclasses

        //stops wifi lock
        AppInitializer appInitializer = App.i().getAppInitializer();
        appInitializer.releaseWiFiLock(true);
        syncAll = false;

        hideSyncNotification();
    }

    private void showSyncNotification() {
        if(Log.isLoggable(Log.DEBUG)) {
            Log.debug(TAG_LOG, "Show sync notification");
        }
        int icon = android.R.drawable.stat_notify_sync;
        CharSequence tickerText = context.getString(R.string.notification_sync_in_progress_ticker_started);
        
        long when = System.currentTimeMillis();

        Notification.Builder notification = new Notification.Builder(context);
        notification.setSmallIcon(icon);
        notification.setTicker(tickerText);
        notification.setWhen(when);

        CharSequence contentTitle = context.getString(R.string.app_name);
        CharSequence contentText = context.getString(R.string.notification_sync_in_progress_message);

        Intent notificationIntent = new Intent(context, AndroidHomeScreen.class);
        //prevent to open the activity twice
        notificationIntent.setAction("android.intent.action.MAIN");
        notificationIntent.addCategory("android.intent.category.LAUNCHER");

        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);
        notification.setContentTitle(contentTitle);
        notification.setContentText(contentText);
        notification.setContentIntent(contentIntent);

        notification.setOngoing(true);
        Notification n = notification.getNotification();
        n.flags |= Notification.FLAG_NO_CLEAR;
        notificationManager.notify(AUTO_SYNC_NOTIFICATION_ID, n);
    }

    private void hideSyncNotification() {
        if(Log.isLoggable(Log.DEBUG)) {
            Log.debug(TAG_LOG, "Hide sync notification");
        }
        notificationManager.cancel(AUTO_SYNC_NOTIFICATION_ID);
    }

    @Override
    public void showConfigurationScreen() {
        if (syncAll) {
            showSyncInProgressMessage();
        } else {
            super.showConfigurationScreen();
        }
    }

    @Override
    final protected void syncSource(String syncType, AppSyncSource appSource) {

        if (networkStatus != null && !networkStatus.isConnected()) {
            if (networkStatus.isRadioOff()) {
                noConnection();
            } else {
                noSignal();
            }
            //The sync all button could have been pressed when a running sync
            //wasn't detected. This avoid the user not allowed to enter the
            //settings when the network is down. 
            syncAll = false;
            return;
        }

        // Check if the sync retry feature is enabled
        if(((AndroidCustomization)customization).getSyncRetryEnabled()) {
            // Keep track of a sync attempt if nobody did it
            if(configuration.getCurrentSyncRetryCount() == -1
                    || MANUAL.equals(syncType)) {
                configuration.setCurrentSyncRetryCount(0);
                configuration.save();
            }
        }

        super.syncSource(syncType, appSource);
    }

    public void attachToRunningSyncIfAny() {
        AppSyncSource appSource = getCurrentSource();
        if (appSource != null) {
            if (Log.isLoggable(Log.DEBUG)) {
                Log.debug(TAG_LOG, "Attaching to running sync");
            }
            attachToRunningSync(appSource);
        }
    }

    public void syncAllSources(String syncMode, int retryCount) {
        super.syncAllSources(syncMode);
    }

    public boolean isFirstSyncDialogDisplayed() {
        return dm.isAlertPending(DisplayManager.FIRST_SYNC_DIALOG_ID);
    }

    public boolean getSyncAll() {
        return syncAll;
    }

    @Override
    public void endSync(Vector sources, boolean hadErrors) {

        // Check if the sync retry feature is enabled
        if(((AndroidCustomization)customization).getSyncRetryEnabled()) {

            int retryCount = configuration.getCurrentSyncRetryCount();
            
            // Reset the current sync retry count, -1 means no retry
            configuration.setCurrentSyncRetryCount(-1);
            configuration.save();

            try {
                // Cancel any pending sync retry
                AndroidSyncModeHandler smHandler =
                            ((AndroidController)controller).getSyncModeHandler();
                smHandler.cancelSyncRetry();

                if(logConnectivityError) {
                    int[] intervals = ((AndroidCustomization)customization)
                            .getSyncRetryIntervals();

                    if (Log.isLoggable(Log.INFO)) {
                        Log.info(TAG_LOG, "Sync failed due to a connectivity "
                                + "problem. Retry count: " + retryCount);
                    }
                    
                    // If we had a connectivity error we schedule a new sync retry
                    if(intervals != null && (retryCount < intervals.length)) {
                        if(retryCount < 0) {
                            retryCount = 0;
                        }
                        int retryMinutes = intervals[retryCount];
                        if (Log.isLoggable(Log.INFO)) {
                            Log.info(TAG_LOG, "Retrying in " + retryMinutes + " minutes");
                        }
                        smHandler.programSyncRetry(retryMinutes, retryCount+1);
                        
                        // Reset the retry count value, it will be refreshed 
                        // once a retry sync comes
                        retryCount = 0;
                    }
                }
            } catch(Throwable t) {
                Log.error(TAG_LOG, "Failed to schedule sync retry");
            }
        }
        super.endSync(sources, hadErrors);
    }

    @Override
    public synchronized void continueSynchronizationAfterFirstSyncDialog(String syncType,    
							    									     List<AppSyncSource> filteredSources,
							                                             boolean refresh,
							                                             int direction,
							                                             int delay,
							                                             boolean fromOutside,
							                                             boolean continueSyncFromDialog) {
    	
        // If the sync shall continue after dialog check, the sync must proceed
        // through the native sync application
        if(!continueSyncFromDialog || filteredSources.isEmpty()) {
            super.continueSynchronizationAfterFirstSyncDialog(syncType, filteredSources,
                    refresh, direction, delay, fromOutside, continueSyncFromDialog);
        } else {
            AppSyncSource first = filteredSources.get(0);
            if(filteredSources.size() > 1) {
                this.syncAll = true;
            }
            syncSource(syncType, first);
        }
    }

    public void logout() {
        // Cannot logout if a sync is in progress
        if (isSynchronizing()) {
            showSyncInProgressMessage();
            return;
        }
        configuration.setCredentialsCheckPending(true);
        configuration.save();
        try {
            controller.getDisplayManager().showScreen(screen, Controller.LOGIN_SCREEN_ID);
        } catch(Exception ex) {
            Log.error(TAG_LOG, "Unable to switch to logout", ex);
        }
    }
    
    /**
     * Update which buttons have to be shown.
     * 
     * Added for ChBoSync
     */
    public void updateSyncButtons() {
    	
    	if (this.homeScreen instanceof AndroidHomeScreen) {
    		AndroidHomeScreen ahs = (AndroidHomeScreen) this.homeScreen;
    		ahs.updateVisibleItems();
    	}    	
    }
    
}
