/*
 * Funambol is a mobile platform developed by Funambol, Inc.
 * Copyright (C) 2008 Funambol, Inc.
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

package de.chbosync.android.syncmlclient.source.pim.contact;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Vector;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;

import com.funambol.sync.ItemStatus;
import com.funambol.sync.SyncItem;
import com.funambol.sync.SyncSource;
import com.funambol.sync.client.ChangesTracker;
import com.funambol.sync.client.TrackableSyncSource;
import com.funambol.sync.client.TrackerException;
import com.funambol.util.Log;
import com.funambol.util.StringUtil;

import de.chbosync.android.syncmlclient.controller.AndroidController;
import de.chbosync.android.syncmlclient.source.AndroidChangesTracker;


/**
 * DirtyChangesTracker is a ChangesTracker that makes use of the dirty flag in
 * order to detect changes in the contact sync source.
 */
public class DirtyChangesTracker implements ChangesTracker, AndroidChangesTracker {

    private final String TAG_LOG = "DirtyChangesTracker";

    private ContentResolver resolver;

    protected Vector<String> newItems;
    protected Vector<String> deletedItems;
    protected Vector<String> updatedItems;

    protected int syncMode;

    protected TrackableSyncSource ss;
    protected ContactManager cm;

    public DirtyChangesTracker(Context context, ContactManager cm) {
        this.cm = cm;
        this.resolver = context.getContentResolver();
    }

    public void begin(int syncMode, boolean resume) throws TrackerException {

        if(Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "begin");
        }

        this.syncMode = syncMode;
        
        this.newItems      = new Vector<String>();
        this.updatedItems  = new Vector<String>();
        this.deletedItems  = new Vector<String>();

        if(syncMode == SyncSource.INCREMENTAL_SYNC ||
           syncMode == SyncSource.INCREMENTAL_UPLOAD ||
           syncMode == SyncSource.INCREMENTAL_DOWNLOAD) {

            computeIncrementalChanges();
        } else if (syncMode == SyncSource.FULL_SYNC ||
                   syncMode == SyncSource.FULL_UPLOAD ||
                   syncMode == SyncSource.FULL_DOWNLOAD) {
            // Reset the status when performing a slow sync
            if (resume && syncMode != SyncSource.FULL_DOWNLOAD) {
                // In this case we need to know if items that were sent in the
                // previous sync attempt have changed
                computeIncrementalChanges();
                // We only need to keep the list of updated items
                newItems = null;
                deletedItems = null;
            } else {
                reset();
            }
        }
    }

    public void end() throws TrackerException {
        if(Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "end");
        }
        // Allow the GC to pick this memory
        newItems      = null;
        updatedItems  = null;
        deletedItems  = null;
    }

    public boolean hasChangedSinceLastSync(String key, long ts) {
        if (updatedItems != null) {
            return updatedItems.contains(key);
        } else {
            return false;
        }
    }

    public boolean supportsResume() {
        return true;
    }

    private void computeIncrementalChanges() {

        // Initialize the items snapshot
        String cols[] = {RawContacts._ID, RawContacts.SOURCE_ID, RawContacts.DELETED, RawContacts.DIRTY};

        StringBuffer whereClause = getAccountWhereClause();

        // Look for dirty contacts only
        whereClause.append(" AND ");
        whereClause.append(RawContacts.DIRTY).append("=1");

        Cursor snapshot = resolver.query(RawContacts.CONTENT_URI, cols,
                whereClause.toString(), null, null);

        try {
            // Get the snapshot column indexes
            int keyColumnIndex      = 0;
            int sourceIdColumnIndex = 1;
            int deletedColumnIndex  = 2;

            snapshot.moveToFirst();

            String snapshotKey = null;
            String sourceId = null;

             // Look for the same element in the snapshot
            while(!snapshot.isAfterLast()) {

                // Get the snapshot key/value
                snapshotKey = snapshot.getString(keyColumnIndex);
                sourceId = snapshot.getString(sourceIdColumnIndex);
                int snapshotDeleted = snapshot.getInt(deletedColumnIndex);

                if(snapshotDeleted == 1) {
                    if(Log.isLoggable(Log.DEBUG)) {
                        Log.debug(TAG_LOG, "Found a deleted item with key: " + snapshotKey);
                    }
                    deletedItems.addElement(snapshotKey);
                } else if(StringUtil.isNullOrEmpty(sourceId)) {
                    if(Log.isLoggable(Log.DEBUG)) {
                        Log.debug(TAG_LOG, "Found a new item with key: " + snapshotKey);
                    }
                    newItems.addElement(snapshotKey);
                } else {
                    if(Log.isLoggable(Log.DEBUG)) {
                        Log.debug(TAG_LOG, "Found an updated item with key: " + snapshotKey);
                    }
                    updatedItems.addElement(snapshotKey);
                }
                snapshot.moveToNext();
            }
        } finally {
            if(snapshot != null) {
                snapshot.close();
            }
        }
    }

    public Enumeration getNewItems() throws TrackerException {
        if(Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "getNewItems");
        }
        // Any item in the sync source which is not part of the
        // old state is a new item
        if (newItems != null) {
            return newItems.elements();
        } else {
            return null;
        }
    }

    public int getNewItemsCount() throws TrackerException {
        if (newItems != null) {
            return newItems.size();
        } else {
            return 0;
        }
    }

    public Enumeration getUpdatedItems() throws TrackerException {
        if(Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "getUpdatedItems");
        }
        // Any item whose fingerprint has changed is a new item
        if (updatedItems != null) {
            return updatedItems.elements();
        } else {
            return null;
        }
    }

    public int getUpdatedItemsCount() throws TrackerException {
        if (updatedItems != null) {
            return updatedItems.size();
        } else {
            return 0;
        }
    }

    public Enumeration getDeletedItems() throws TrackerException {
        if(Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "getDeletedItems");
        }
        // Any item in the sync source which is not part of the
        // old state is a new item
        if (deletedItems != null) {
            return deletedItems.elements();
        } else {
            return null;
        }
    }

    public int getDeletedItemsCount() throws TrackerException {
        if (deletedItems != null) {
            return deletedItems.size();
        } else {
            return 0;
        }
    }

    public void setItemsStatus(Vector itemsStatus) throws TrackerException {
        Vector filteredItemsStatus = new Vector();
        for(int i=0;i<itemsStatus.size();++i) {
            ItemStatus status = (ItemStatus)itemsStatus.elementAt(i);
            String key = status.getKey();
            long id = Long.parseLong(key);
            int itemStatus = status.getStatus();
            if (isSuccess(itemStatus) && itemStatus != SyncSource.CHUNK_SUCCESS_STATUS) {
                if (deletedItems.contains(key)) {
                    cm.hardDelete(id);
                } else {
                    filteredItemsStatus.addElement(status);
                }
            }
        }

        // Now apply all the changes in one shot
        try {
            cm.refreshSourceIdAndDirtyFlag(filteredItemsStatus);
        } catch (IOException ioe) {
            throw new TrackerException("Cannot set dirty flag");
        }
    }

    public boolean filterItem(String key, boolean removed) {
        return false;
    }

    protected Uri addCallerIsSyncAdapterFlag(Uri uri) {
        Uri.Builder b = uri.buildUpon();
        b.appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true");
        return b.build();
    }
    
    protected boolean isSuccess(int status) {
        if(Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "isSuccess " + status);
        }
        return SyncSource.SUCCESS_STATUS == status;
    }

    public void empty() throws TrackerException {
        // Nothing to do
    }

    public boolean removeItem(SyncItem item) throws TrackerException {
        // Nothing to do
        return true;
    }

    public void reset() throws TrackerException {

        StringBuffer whereClause = getAccountWhereClause();

        // Here we have to reset all the possible changes by resetting
        // the dirty flag for all the items
        Uri uri = addCallerIsSyncAdapterFlag(
                ContactsContract.RawContacts.CONTENT_URI);
        
        ContentValues cv = new ContentValues();
        cv.put(ContactsContract.RawContacts.DIRTY, 0);
        
        resolver.update(uri, cv, whereClause.toString(), null);

        whereClause.append(" AND ");
        whereClause.append(RawContacts.DELETED).append("=1");

        // Then we have to hard delete all of the temp deleted items
        resolver.delete(uri, whereClause.toString(), null);
    }

    public void setSyncSource(TrackableSyncSource ss) {
        this.ss = ss;
    }

    public boolean hasChanges() {
        boolean result = false;

        StringBuffer whereClause = getAccountWhereClause();
        whereClause.append(" AND ");
        whereClause.append(RawContacts.DIRTY).append("=1");

        Cursor items = resolver.query(RawContacts.CONTENT_URI, 
                new String[] {ContactsContract.RawContacts.DIRTY},
                whereClause.toString(), null, null);

        result = items.getCount() > 0;
        items.close();

        return result;
    }

    private StringBuffer getAccountWhereClause() {

        StringBuffer whereClause = new StringBuffer();
        
        Account account = AndroidController.getNativeAccount();
        if(account != null) {
            String accountType = account.type;
            String accountName = account.name;
            
            if(accountName != null && accountType != null) {
                whereClause.append(RawContacts.ACCOUNT_NAME).append("='")
                        .append(accountName).append("'");
                whereClause.append(" AND ");
                whereClause.append(RawContacts.ACCOUNT_TYPE).append("='")
                        .append(accountType).append("'");
            }
        } else {
            whereClause.append("(0)");
        }
        return whereClause;
    }
}
