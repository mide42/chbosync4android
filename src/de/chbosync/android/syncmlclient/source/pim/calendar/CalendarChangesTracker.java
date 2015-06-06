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

package de.chbosync.android.syncmlclient.source.pim.calendar;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import com.funambol.storage.StringKeyValuePair;
import com.funambol.storage.StringKeyValueStore;
import com.funambol.sync.SyncItem;
import com.funambol.sync.SyncSource;
import com.funambol.sync.client.CacheTracker;
import com.funambol.sync.client.TrackerException;
import com.funambol.util.Log;

import de.chbosync.android.syncmlclient.source.AndroidChangesTracker;


/**
 * This interface can be used by TrackableSyncSource to detect changes occourred
 * since the last synchronization. The API provides a basic implementation
 * in CacheTracker which detects changes comparing fingerprints.
 * Client can implement this interface and use it in the TrackableSyncSource if more
 * efficient methods are available.
 */
public class CalendarChangesTracker extends CacheTracker implements AndroidChangesTracker {

    private static final String TAG_LOG = "CalendarChangesTracker";

    protected ContentResolver resolver;

    protected CalendarAppSyncSourceConfig calendarAppSyncSourceConfig;

	private String accountName;
	private String accountType;


    public CalendarChangesTracker(Context context, StringKeyValueStore status,
                                  CalendarAppSyncSourceConfig calendarAppSyncSourceConfig) {
        super(status);
        this.resolver = context.getContentResolver();
        this.calendarAppSyncSourceConfig = calendarAppSyncSourceConfig;
    }


    @Override
    public void begin(int syncMode, boolean resume) throws TrackerException {

        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "beginning changes computation");
        }

        long calendarId = calendarAppSyncSourceConfig.getCalendarId();
        if (calendarId == -1) {
            throw new TrackerException("Cannot track undefined calendar");
        }

        this.syncMode = syncMode;
    
        newItems      = new Hashtable();
        updatedItems  = new Hashtable();
        deletedItems  = new Hashtable();

        // Initialize the status
        try {
            this.status.load();
        } catch (Exception ex) {
            if (Log.isLoggable(Log.DEBUG)) {
                Log.debug(TAG_LOG, "Cannot load tracker status: " + ex.toString());
            }
            throw new TrackerException("Cannot load tracker status");
        }

        if(syncMode == SyncSource.INCREMENTAL_SYNC ||
           syncMode == SyncSource.INCREMENTAL_UPLOAD ||
           syncMode == SyncSource.INCREMENTAL_DOWNLOAD) {

            // Initialize the items snapshot
            String cols[] = {CalendarContract.Events._ID, CalendarContract.Events.DIRTY};

            // Grab only the rows which are sync dirty and belong to the
            // calendar being synchronized
            StringBuffer whereClause = new StringBuffer();
            whereClause.append(CalendarContract.Events.CALENDAR_ID).append("='").append(calendarId).append("'");

            // Get all the items belonging to the calendar being
            // synchronized in ascending order
            Cursor snapshot = resolver.query(CalendarContract.Events.CONTENT_URI, cols, whereClause.toString(), null, CalendarContract.Events._ID + " ASC");

            // Get the snapshot column indexes
            int keyColumnIndex     = snapshot.getColumnIndexOrThrow(CalendarContract.Events._ID);

            Enumeration statusPairs = status.keyValuePairs();

            // We have two ordered sets to compare
            try {
                boolean snapshotDone = !snapshot.moveToFirst();
                boolean statusDone   = !statusPairs.hasMoreElements();
                String statusIdStr   = null;
                long statusId = -1;
                long snapshotId = -1;
                StringKeyValuePair kvp = null;
                do {
                    if (Log.isLoggable(Log.TRACE)) {
                    	Log.trace(TAG_LOG, "snapshotDone = " + snapshotDone);
                    	Log.trace(TAG_LOG, "statusDone = " + statusDone);
                    }

                    String snapshotIdStr = null;

                    if (!snapshotDone) {
                        // Get the item id in the snapshot
                        snapshotIdStr = snapshot.getString(keyColumnIndex);
                        snapshotId = Long.parseLong(snapshotIdStr);
                    } else {
                        snapshotId = -1;
                    }

                    statusDone = !statusPairs.hasMoreElements();
                    if (statusIdStr == null && !statusDone) {
                        kvp = (StringKeyValuePair)statusPairs.nextElement();
                        statusIdStr = kvp.getKey();
                        statusId = Long.parseLong(statusIdStr);
                    }

                    if (Log.isLoggable(Log.TRACE)) {
                        Log.trace(TAG_LOG, "snapshotId = " + snapshotId);
                        Log.trace(TAG_LOG, "statusId = " + statusId);
                    }

                    if (!statusDone || !snapshotDone) {
                        if (snapshotId == statusId) {
                            // Check if the item is updated. Note that on
                            // Android LUIDs can be reused, therefore it is
                            // possible that if the user removes the last item
                            // and add a new one, we detect a replace instead of
                            // a pair delete/add
                            if (Log.isLoggable(Log.TRACE)) {
                                Log.trace(TAG_LOG, "Same id: " + statusId);
                            }

                            if (isDirty(snapshot, kvp)) {
                                if (Log.isLoggable(Log.TRACE)) {
                                    Log.trace(TAG_LOG, "Found updated item: " + snapshotId);
                                }
                                updatedItems.put(snapshotIdStr, computeFingerprint(snapshotIdStr, snapshot));
                            }
                            // Advance both pointers
                            snapshotDone = !snapshot.moveToNext();
                            statusIdStr = null;
                        } else if ((snapshotId < statusId && snapshotId != -1) || statusDone) {
                            if (Log.isLoggable(Log.TRACE)) {
                                Log.trace(TAG_LOG, "Found new item: " + snapshotId);
                            }
                            // This item was added
                            newItems.put(snapshotIdStr, computeFingerprint(snapshotIdStr, snapshot));
                            // Move only the snapshot pointer
                            snapshotDone = !snapshot.moveToNext();
                        } else {
                            if (Log.isLoggable(Log.TRACE)) {
                                Log.trace(TAG_LOG, "Found deleted item: " + statusId);
                            }
                            // The item was deleted
                            deletedItems.put(statusIdStr, "1");
                            // Move only the status pointer
                            statusIdStr = null;
                        }
                    }
                } while(!statusDone || !snapshotDone);
            } catch (Exception e) {
                Log.error(TAG_LOG, "Cannot compute changes", e);
                throw new TrackerException(e.toString());
            } finally {
                snapshot.close();
            }
        } else if(syncMode == SyncSource.FULL_SYNC ||
                  syncMode == SyncSource.FULL_UPLOAD ||
                  syncMode == SyncSource.FULL_DOWNLOAD) {
            // Reset the status when performing a slow sync
            try {
                status.reset();
            } catch(IOException ex) {
                Log.error(TAG_LOG, "Cannot reset status", ex);
                throw new TrackerException("Cannot reset status");
            }
        }
    }

    @Override
    public void setItemStatus(String key, int itemStatus) throws TrackerException {
        if(syncMode == SyncSource.FULL_SYNC ||
           syncMode == SyncSource.FULL_UPLOAD) {
            if(status.get(key) == null) {
                status.add(key, "1");
            }
        } else if (isSuccess(itemStatus) && itemStatus != SyncSource.CHUNK_SUCCESS_STATUS) {
            // We must update the fingerprint store with the value of the
            // fingerprint at the last sync
            if (newItems.get(key) != null) {
                // Update the fingerprint
                status.add(key, "1");
            } else if (deletedItems.get(key) != null) {
                // Update the fingerprint
                status.remove(key);
            }
        }
        // If the item was succesfully synchronized, then we clear the dirty
        // flag
        if (isSuccess(itemStatus) && itemStatus != SyncSource.CHUNK_SUCCESS_STATUS) {
            clearSyncDirty(key);
        }
    }

    @Override
    public boolean removeItem(SyncItem item) throws TrackerException {

        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Removing item " + item.getKey());
        }

        if (item.getState() == SyncItem.STATE_DELETED) {
            status.remove(item.getKey());
        } else {
            if (Log.isLoggable(Log.TRACE)) {
                Log.trace(TAG_LOG, "Updating status");
            }
            if (item.getState() == SyncItem.STATE_NEW) {
                status.add(item.getKey(), "1");
            }
            if (Log.isLoggable(Log.TRACE)) {
                Log.trace(TAG_LOG, "Updating events table");
            }
            // Removing item from the list of changes
            String key = item.getKey();
            clearSyncDirty(key);
        }
        return true;
    }

    protected boolean isDirty(Cursor snapshot, StringKeyValuePair kvp) throws IOException {
        int syncDirtyColIdx = snapshot.getColumnIndexOrThrow(CalendarContract.Events.DIRTY);
        return 1 == snapshot.getInt(syncDirtyColIdx);
    }

    protected String computeFingerprint(String key, Cursor cursor) throws IOException {
        return "1";
    }

    private void clearSyncDirty(String key) {
        // This item was succesfully synced, mark it as such
        long id = Long.parseLong(key);
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Updating sync dirty flag for " + id);
        }
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Events.DIRTY, 0);
        if (this.accountName==null)
        	resolveAccount(id);
        
        Uri uri = ContentUris.withAppendedId(CalendarManager.asSyncAdapter(CalendarContract.Events.CONTENT_URI, this.accountName, this.accountType), id);
        
        int numUpdates = resolver.update(uri, values, null, null);
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Number of updated rows = " + numUpdates);
        }
    }

    /**
     * Determines accountName and type via given event
     * @param eventId
     */
	private void resolveAccount(long eventId) {
		String projection[] = {CalendarContract.Events.ACCOUNT_NAME, CalendarContract.Events.ACCOUNT_TYPE};
		Cursor c = this.resolver.query(CalendarContract.Events.CONTENT_URI, projection, CalendarContract.Events._ID + "=?", new String[]{Long.toString(eventId)}, null);
		if (c.moveToFirst()) {
			this.accountName = c.getString(c.getColumnIndexOrThrow(CalendarContract.Events.ACCOUNT_NAME));
			this.accountType = c.getString(c.getColumnIndexOrThrow(CalendarContract.Events.ACCOUNT_TYPE));
		}
	}


	public boolean hasChanges() {
        boolean result = false;
        begin(SyncSource.INCREMENTAL_SYNC, false);
        result |= getNewItemsCount() > 0;
        result |= getUpdatedItemsCount() > 0;
        result |= getDeletedItemsCount() > 0;
        end();
        return result;
    }
}

