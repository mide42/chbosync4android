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

package de.chbosync.android.syncmlclient.controller;

import java.util.Enumeration;
import java.util.Vector;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;

import com.funambol.client.source.AppSyncSource;
import com.funambol.sync.client.ChangesTracker;
import com.funambol.sync.client.TrackableSyncSource;
import com.funambol.util.Log;

import de.chbosync.android.syncmlclient.AndroidAccountManager;
import de.chbosync.android.syncmlclient.AndroidAppSyncSource;
import de.chbosync.android.syncmlclient.AndroidAppSyncSourceManager;
import de.chbosync.android.syncmlclient.App;
import de.chbosync.android.syncmlclient.AppInitializer;
import de.chbosync.android.syncmlclient.source.AndroidChangesTracker;


/**
 * <code>AutoSyncSwitcher</code> allows to change the auto sync mode:
 * <li><code>AUTO_SYNC_MODE_NATIVE</code>: leave the auto sync handling to the
 * native sync manager</li>
 * <li><code>AUTO_SYNC_MODE_CUSTOM</code>: start a custom auto sync handling
 * based on <code>ContentObserver</code>s</li>
 * <br/><br/>
 * Switching from <code>AUTO_SYNC_MODE_CUSTOM</code> to
 * <code>AUTO_SYNC_MODE_NATIVE</code> will immadiately
 * start a synchronization of the sources which has meen modified during the
 * custom auto sync period.
 */
public class AutoSyncSwitcher {

    private final String TAG_LOG = "AutoSyncSwitcher";

    public static final int AUTO_SYNC_MODE_NATIVE = 0;
    public static final int AUTO_SYNC_MODE_CUSTOM = 1;

    private Context mContext;
    private Handler mHandler;

    private AndroidAppSyncSourceManager appSyncSourceManager;
    private ContentResolver contentResolver;

    private Vector<AppSyncSource>   pendingSources;
    private Vector<String>          authoritiesWithAutoSync;
    private Vector<ContentObserver> observers;

    private AndroidHomeScreenController homeScreenController;

    private int status = AUTO_SYNC_MODE_NATIVE;

    public AutoSyncSwitcher(Context context, Handler handler,
            AndroidHomeScreenController homeScreenController) {

        this.mContext = context;
        this.mHandler = handler;
        this.contentResolver = mContext.getContentResolver();
        this.homeScreenController = homeScreenController;

        AppInitializer initializer = App.i().getAppInitializer();
        this.appSyncSourceManager = initializer.getAppSyncSourceManager();
    }

    /**
     * Switch to the given auto sync mode.
     * 
     * @param mode
     * @throws IllegalArgumentException
     */
    public void setAutoSyncMode(int mode) throws IllegalArgumentException {
        switch(mode) {
            case AUTO_SYNC_MODE_CUSTOM:
                if(status == AUTO_SYNC_MODE_CUSTOM) {
                    if (Log.isLoggable(Log.DEBUG)) {
                        Log.debug(TAG_LOG, "Auto sync is already in custom mode");
                    }
                } else {
                    switchToCustomAutoSync();
                    status = AUTO_SYNC_MODE_CUSTOM;
                }
                break;
            case AUTO_SYNC_MODE_NATIVE:
                if(status == AUTO_SYNC_MODE_NATIVE) {
                    if (Log.isLoggable(Log.DEBUG)) {
                        Log.debug(TAG_LOG, "Auto sync is already in native mode");
                    }
                } else {
                    switchToNativeAutoSync();
                    status = AUTO_SYNC_MODE_NATIVE;
                }
                break;
            default:
                Log.error(TAG_LOG, "Invalid auto sync mode: " + mode);
                throw new IllegalArgumentException("Invalid auto sync mode: " + mode);
        }
    }

    /**
     * @return the current auto sync mode
     */
    public int getAutoSyncMode() {
        return status;
    }

    private void switchToCustomAutoSync() {

        if (Log.isLoggable(Log.DEBUG)) {
            Log.debug(TAG_LOG, "Switching to custom auto sync");
        }

        Account account = AndroidAccountManager.getNativeAccount(mContext);
        Enumeration sources = appSyncSourceManager.getEnabledAndWorkingSources();

        observers               = new Vector<ContentObserver>();
        authoritiesWithAutoSync = new Vector<String>();
        pendingSources          = new Vector<AppSyncSource>();

        AndroidAppSyncSource currentSource = (AndroidAppSyncSource)
                homeScreenController.getCurrentSource();
    }

    private void switchToNativeAutoSync() {

        if (Log.isLoggable(Log.DEBUG)) {
            Log.debug(TAG_LOG, "Switching to native auto sync");
        }

        // Remove content observers
        if (Log.isLoggable(Log.DEBUG)) {
            Log.debug(TAG_LOG, "Unregistering content observers");
        }
        for(int i=0; i<observers.size(); i++) {
            contentResolver.unregisterContentObserver(observers.get(i));
        }
    }

    /**
     * Filters the given sources Vector, keep only the ones which really need to
     * be synchronized.
     * 
     * @param sources
     * @return
     */
    private Vector<AppSyncSource> getSourcesToSync(Vector<AppSyncSource> sources) {
        if (Log.isLoggable(Log.DEBUG)) {
            Log.debug(TAG_LOG, "Retrieving sources to synchronize");
        }

        for(int i=0; i<sources.size(); i++) {
            AndroidAppSyncSource appSource = (AndroidAppSyncSource)sources.get(i);
            TrackableSyncSource tss = (TrackableSyncSource)appSource.getSyncSource();
            ChangesTracker tracker = tss.getTracker();

            if(tracker instanceof AndroidChangesTracker) {
                AndroidChangesTracker aTracker = (AndroidChangesTracker)tracker;
                if(aTracker.hasChanges()) {
                    if (Log.isLoggable(Log.DEBUG)) {
                        Log.debug(TAG_LOG, "Changes found in source: " + appSource.getName());
                    }
                } else {
                    if (Log.isLoggable(Log.DEBUG)) {
                        Log.debug(TAG_LOG, "Changes not found in source: " + appSource.getName());
                    }
                    sources.remove(appSource);
                }
            }
        }
        return sources;
    }
}
