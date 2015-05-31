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

package de.chbosync.android.syncmlclient.services;

import java.util.Hashtable;
import java.util.Vector;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;

import com.funambol.client.configuration.Configuration;
import com.funambol.client.controller.Controller;
import com.funambol.client.controller.SynchronizationController;
import com.funambol.client.source.AppSyncSource;
import com.funambol.client.source.AppSyncSourceManager;
import com.funambol.sync.SyncSource;
import com.funambol.sync.client.ChangesTracker;
import com.funambol.sync.client.TrackableSyncSource;
import com.funambol.util.Log;

import de.chbosync.android.syncmlclient.AndroidAppSyncSource;
import de.chbosync.android.syncmlclient.AndroidConfiguration;
import de.chbosync.android.syncmlclient.AndroidCustomization;
import de.chbosync.android.syncmlclient.App;
import de.chbosync.android.syncmlclient.AppInitializer;
import de.chbosync.android.syncmlclient.controller.AndroidHomeScreenController;
import de.chbosync.android.syncmlclient.source.AndroidChangesTracker;


/**
 * This class is responsible for handling automatic synchronizations. In the
 * client there are different types of automatic syncs:
 *
 * 1) scheduled syncs (periodic)
 * 2) pending syncs waiting for some triggering event (for example syncs pending
 *    on the WiFi availability)
 * 3) syncs pushed from the server
 */
public class AutoSyncService extends Service {

    private final String TAG = "AutoSyncService";

    public static final String AUTO_SYNC_ACTION  = "de.chbosync.android.syncmlclient.services.AUTO_SYNC";

    public static final String OPERATION = "OPERATION";
    public static final String PROGRAM_SCHEDULED_SYNC = "de.chbosync.android.syncmlclient.PROGRAM_SCHEDULED_SYNC";
    public static final String CANCEL_SCHEDULED_SYNC = "de.chbosync.android.syncmlclient.CANCEL_SCHEDULED_SYNC";
    public static final String PROGRAM_SYNC_RETRY = "de.chbosync.android.syncmlclient.PROGRAM_SYNC_RETRY";
    public static final String CANCEL_SYNC_RETRY = "de.chbosync.android.syncmlclient.CANCEL_SYNC_RETRY";
    public static final String START_MONITORING_URI = "de.chbosync.android.syncmlclient.START_MONITORING_URI";
    public static final String START_SYNC = "de.chbosync.android.syncmlclient.START_SYNC";

    public static final String SOURCE_ID = "SOURCE_ID";
    public static final String SOURCES_ID = "SOURCES_ID";
    public static final String DELAY = "DELAY";
    public static final String COUNT = "COUNT";
    public static final String URI = "URI";
    public static final String SYNC_MODE = "SYNC_MODE";
    public static final String DIRECTORY = "DIRECTORY";
    public static final String EXTENSIONS = "EXTENSIONS";

    public static final String EXTRA_RETRY_COUNT = "RETRY_COUNT";

    private Context context;

    private PendingIntent scheduledSyncIntent;
    private PendingIntent syncRetryIntent;

    private AndroidConfiguration configuration;
    private AndroidCustomization customization;
    private AppSyncSourceManager appSyncSourceManager;

    private AlarmManager   alarmManager;
    private AppInitializer initializer;

    private Hashtable <Uri,AndroidAppSyncSource> monitoredUris = new Hashtable<Uri,AndroidAppSyncSource>();

    private Hashtable fileObservers = new Hashtable();

    public AutoSyncService() {
        super();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Log.isLoggable(Log.DEBUG)) {
            Log.debug(TAG, "Service Created");
        }

        initializer = App.i().getAppInitializer();
        initializer.init();

        configuration = initializer.getConfiguration();
        customization = AndroidCustomization.getInstance();
        appSyncSourceManager = initializer.getAppSyncSourceManager();

        context = getApplicationContext();
    }

    @Override
    public IBinder onBind(Intent intent) {
        startForeground(Notification.FLAG_FOREGROUND_SERVICE,
                new Notification(0, null, System.currentTimeMillis()));
        return new AutoSyncBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Log.isLoggable(Log.DEBUG)) {
            Log.debug(TAG, "Service Started");
        }
        String operation = null;
        if(intent != null) {
            operation = intent.getStringExtra(OPERATION);
        }
        if (START_MONITORING_URI.equals(operation)) {
            int sourceId = intent.getIntExtra(SOURCE_ID, -1);
            String uri = intent.getStringExtra(URI);
            startMonitoringUri(uri, sourceId);
        } else if (START_SYNC.equals(operation)) {
            String syncMode = intent.getStringExtra(SYNC_MODE);
            int sourcesId[] = intent.getIntArrayExtra(SOURCES_ID);
            int delay = intent.getIntExtra(DELAY, 0);
            startSync(syncMode, sourcesId, delay);
        } else if (PROGRAM_SCHEDULED_SYNC.equals(operation)) {
            programScheduledSync();
        } else if (CANCEL_SCHEDULED_SYNC.equals(operation)) {
            cancelScheduledSync();
        } else if (PROGRAM_SYNC_RETRY.equals(operation)) {
            int delay = intent.getIntExtra(DELAY, 0);
            int count = intent.getIntExtra(COUNT, 0);
            programSingleSync(delay, count);
        } else if (CANCEL_SYNC_RETRY.equals(operation)) {
            cancelSyncRetry();
        } else if (operation == null) {
            startForeground(Notification.FLAG_FOREGROUND_SERVICE,
                    new Notification(0, null, System.currentTimeMillis()));

            // Program scheduled sync if necessary
            if(!configuration.getCredentialsCheckPending()) {
                programScheduledSync();
            }
            // Program sync retry if necessary
            if(customization.getSyncRetryEnabled()) {
                int retryCount = configuration.getCurrentSyncRetryCount();
                if(retryCount >= 0) {
                    if (Log.isLoggable(Log.DEBUG)) {
                        Log.debug(TAG, "A previous sync (retry) didn't end correctly. "
                                + "Retry in 10 seconds");
                    }
                    programSingleSyncSeconds(10, retryCount);
                }
            }
        }
        return START_STICKY;
    }

    private void programScheduledSync() {
        // Check in the configuration if a form of auto synchronization is set
        if (configuration.getSyncMode() == Configuration.SYNC_MODE_SCHEDULED) {
            int interval = configuration.getPollingInterval();
            if (interval > 0) {
                // Scheduled sync
                if (Log.isLoggable(Log.TRACE)) {
                    Log.trace(TAG, "Programming scheduled sync at " + interval);
                }
                programRepeatingSync(interval);
            }
        }
    }

    @Override
    public void onDestroy() {
        if (Log.isLoggable(Log.DEBUG)) {
            Log.debug(TAG, "Service Stopped");
        }
    }

    /**
     * LocalBinder used by activities that wish to be notified of the sync events.
     */
    public class AutoSyncBinder extends Binder {

        public void startMonitoringUri(String u, int sourceId) {
            AutoSyncService.this.startMonitoringUri(u, sourceId);
        }

        public void programScheduledSync() {
            AutoSyncService.this.programScheduledSync();
        }

        public void programSyncRetry(int minutes, int retryCount) {
            AutoSyncService.this.programSingleSync(minutes, retryCount);
        }

        public void cancelScheduledSync() {
            AutoSyncService.this.cancelScheduledSync();
        }

        public void cancelSyncRetry() {
            AutoSyncService.this.cancelSyncRetry();
        }
    }

    private void startMonitoringUri(String u, int sourceId) {

        AndroidAppSyncSource appSource = (AndroidAppSyncSource)appSyncSourceManager.getSource(sourceId);
        Uri uri = Uri.parse(u);

        ContentResolver contentResolver = context.getContentResolver();
        AppSyncSource s = AutoSyncService.this.monitoredUris.get(uri);
        if (s == null) {
            if (Log.isLoggable(Log.INFO)) {
                Log.info(TAG, "Start monitoring uri " + uri);
            }
            monitoredUris.put(uri, appSource);
            contentResolver.registerContentObserver(uri, true, new AndroidContentObserver(appSource, uri));
        }
    }

    
    private void programRepeatingSync(int minutes) {

        if (Log.isLoggable(Log.DEBUG)) {
            Log.debug(TAG, "Programming repeating sync with interval: " + minutes);
        }

        int interval = minutes * 1000 * 60;

        Intent i = new Intent(AUTO_SYNC_ACTION);
        scheduledSyncIntent = PendingIntent.getBroadcast(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        
        alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + interval, interval, scheduledSyncIntent);
        
    }

    private void programSingleSyncSeconds(int seconds, int retryCount) {

        if (Log.isLoggable(Log.DEBUG)) {
            Log.debug(TAG, "Programming sync with delay: " + seconds);
        }

        int interval = seconds * 1000;

        Intent i = new Intent(AUTO_SYNC_ACTION);
        i.putExtra(EXTRA_RETRY_COUNT, retryCount);

        syncRetryIntent = PendingIntent.getBroadcast(context, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT);

        alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        alarmManager.set(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + interval, syncRetryIntent);

        configuration.setCurrentSyncRetryCount(retryCount);
        configuration.save();
    }
    
    private void programSingleSync(int minutes, int retryCount) {
        int seconds = minutes * 60;
        programSingleSyncSeconds(seconds, retryCount);
    }

    private void cancelScheduledSync() {
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG, "cancelScheduledSync");
        }
        if (alarmManager != null) {
            alarmManager.cancel(scheduledSyncIntent);
        }
    }

    private void cancelSyncRetry() {
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG, "cancelSyncRetry");
        }
        if (alarmManager != null) {
            alarmManager.cancel(syncRetryIntent);
        }
    }

    private void startSync(String syncMode, int sourcesId[], int delay) {
        Controller controller = initializer.getController();
        AndroidHomeScreenController hsc = (AndroidHomeScreenController)controller.getHomeScreenController();
        Vector sources = new Vector();
        for(int i=0;i<sourcesId.length;++i) {
            AndroidAppSyncSource appSource = (AndroidAppSyncSource)appSyncSourceManager.getSource(sourcesId[i]);
            sources.addElement(appSource);
        }
        if(delay > 0) {
            hsc.fireSynchronization(syncMode, sources, delay);
        } else {
            hsc.fireSynchronization(syncMode, sources);
        }
    }

    /**
     * Implements a <code>ContentObserver</code> in order to keep track of
     * changes done on specific sources.
     */
    private class AndroidContentObserver extends ContentObserver {

        private static final String TAG_LOG = "AndroidContentObserver";

        private AndroidAppSyncSource appSource;
        private Uri uri;

        public AndroidContentObserver(AndroidAppSyncSource appSource, Uri uri) {
            super(null);
            this.appSource = appSource;
            this.uri = uri;
        }

        @Override
        public void onChange(boolean selfChange) {
            if (Log.isLoggable(Log.TRACE)) {
                Log.trace(TAG_LOG, "Detected change for uri: " + uri);
            }
            if(!configuration.isC2SPushEnabled()) {
                if(Log.isLoggable(Log.TRACE)) {
                    Log.trace(TAG_LOG, "C2S push is not enabled");
                }
                return;
            }
            Controller controller = initializer.getController();
            AndroidHomeScreenController hsc = (AndroidHomeScreenController)controller.getHomeScreenController();
            if (hsc.isSynchronizing() && hsc.getCurrentSource() == appSource) {
                if (Log.isLoggable(Log.TRACE)) {
                    Log.trace(TAG_LOG, "Ignoring change during sync for " + appSource.getId());
                }
            } else {
                // Does this change belong to us?
                boolean checked = false;
                boolean hasChanges = true;
                SyncSource ss = appSource.getSyncSource();
                if (ss instanceof TrackableSyncSource) {
                    ChangesTracker tracker = ((TrackableSyncSource)ss).getTracker();
                    if (tracker instanceof ChangesTracker) {
                        hasChanges = ((AndroidChangesTracker)tracker).hasChanges();
                        checked = true;
                    }
                }
                if (!checked) {
                    if (Log.isLoggable(Log.INFO)) {
                        Log.info(TAG_LOG, "Cannot check if change is on our account, schedule a sync");
                    }
                    hasChanges = true;
                }
                if (hasChanges) {
                    Vector sources = new Vector();
                    sources.addElement(appSource);
                    int delay = customization.getC2SPushDelay();
                    AutoSyncServiceHandler autoSyncHandler = new AutoSyncServiceHandler(context);
                    autoSyncHandler.startSync(SynchronizationController.PUSH, sources, delay);
                }
            }
        }
    }

}
