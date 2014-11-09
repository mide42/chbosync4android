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
import java.util.Hashtable;
import java.util.Vector;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;

import com.funambol.storage.StringKeyValuePair;
import com.funambol.storage.StringKeyValueStore;
import com.funambol.sync.ItemStatus;
import com.funambol.sync.SyncItem;
import com.funambol.sync.SyncSource;
import com.funambol.sync.client.CacheTracker;
import com.funambol.sync.client.TrackerException;
import com.funambol.util.Log;

import de.chbosync.android.syncmlclient.controller.AndroidController;
import de.chbosync.android.syncmlclient.source.AndroidChangesTracker;


/**
 * <code>VersionCacheTracker</code> extends the <code>CacheTracker</code>
 * implementation and overloads the changes retrieving and the fingerprint
 * computing algorithms.
 *
 * The fingerprint used to retrieve changes is the contact version.
 *
 */
public class VersionCacheTracker extends CacheTracker
        implements AndroidChangesTracker {

    private final String LOG_TAG = "VersionCacheTracker";

    private ContentResolver resolver;
    private Uri uri;
    private ContactManager cm;

    /**
     * Creates a VersionCacheTracker. The constructor detects changes so that
     * the method to get the changes can be used right away
     *
     * @param status is the key value store with stored data
     * @param context the application Context
     * @param uri is the uri of the table that this tracker tracks
     */
    public VersionCacheTracker(StringKeyValueStore status, Context context,
            ContactManager cm) {
        this(status, context, RawContacts.CONTENT_URI, cm);
    }

    /**
     * Creates a VersionCacheTracker. The constructor detects changes so that
     * the method to get the changes can be used right away
     *
     * @param status is the key value store with stored data
     * @param context the application Context
     * @param uri the tracked table uri
     */
    public VersionCacheTracker(StringKeyValueStore status, Context context, 
            Uri uri, ContactManager cm) {
        super(status);
        this.uri = uri;
        this.cm = cm;
        this.resolver = context.getContentResolver();
    }

    /**
     * Implements the changes tracking logic. It retrieves changes based to the
     * cache of the items version (the status).
     *
     * @param syncMode is the logic sync mode
     * @param resume true if the sync is being resumed
     *
     * @throws TrackerException
     */
    @Override
    public void begin(int syncMode, boolean resume) throws TrackerException {

        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(LOG_TAG, "begin");
        }

        // Init account info
        Account account = AndroidController.getNativeAccount();
        String  accountType = null;
        String  accountName = null;
        
        if(account != null) {
            accountType = account.type;
            accountName = account.name;
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
                Log.debug(LOG_TAG, "Cannot load tracker status: " + ex.toString());
            }
            throw new TrackerException("Cannot load tracker status");
        }

        if(syncMode == SyncSource.INCREMENTAL_SYNC ||
           syncMode == SyncSource.INCREMENTAL_UPLOAD ||
           syncMode == SyncSource.INCREMENTAL_DOWNLOAD) {

            computeIncrementalChanges(accountType, accountName);
        } else if (syncMode == SyncSource.FULL_SYNC ||
                   syncMode == SyncSource.FULL_UPLOAD ||
                   syncMode == SyncSource.FULL_DOWNLOAD) {
            // Reset the status when performing a slow sync
            if (resume && syncMode != SyncSource.FULL_DOWNLOAD) {
                // In this case we need to know if items that were sent in the
                // previous sync attempt have changed
                computeIncrementalChanges(accountType, accountName);
                // We only need to keep the list of updated items
                newItems = null;
                deletedItems = null;
            } else {
                try {
                    status.reset();
                } catch(IOException ex) {
                    Log.error(LOG_TAG, "Cannot reset status", ex);
                    throw new TrackerException("Cannot reset status");
                }
            }
        }
    }

    @Override
    public boolean hasChangedSinceLastSync(String key, long ts) {
        if (updatedItems != null) {
            return updatedItems.get(key) != null;
        } else {
            return false;
        }
    }

    @Override
    public boolean supportsResume() {
        return true;
    }

    private void computeIncrementalChanges(String accountType, String accountName) {

        // Initialize the items snapshot
        String cols[] = {RawContacts._ID, RawContacts.VERSION, RawContacts.DELETED};

        StringBuffer whereClause = new StringBuffer();
        if(accountName != null && accountType != null) {
            whereClause.append(RawContacts.ACCOUNT_NAME).append("='").append(accountName).append("'");
            whereClause.append(" AND ");
            whereClause.append(RawContacts.ACCOUNT_TYPE).append("='").append(accountType).append("'");
        }

        Cursor snapshot = resolver.query(uri, cols, whereClause.toString(), null, RawContacts._ID + " ASC");
        try {
            // Get the snapshot column indexes
            int keyColumnIndex     = snapshot.getColumnIndexOrThrow(RawContacts._ID);
            int valueColumnIndex   = snapshot.getColumnIndexOrThrow(RawContacts.VERSION);
            int deletedColumnIndex = snapshot.getColumnIndexOrThrow(RawContacts.DELETED);

            // Get the status key/value pairs
            Enumeration statusKVPs = status.keyValuePairs();

            snapshot.moveToFirst();

            StringKeyValuePair statusKVP = null;
            String statusKey       = null;
            String statusVersion   = null;
            String snapshotKey     = null;
            String snapshotVersion = null;

            // Iterate on the status elements
            while(statusKVPs.hasMoreElements()) {

                // Get the status key/value
                statusKVP = (StringKeyValuePair)statusKVPs.nextElement();
                statusKey     = statusKVP.getKey();
                statusVersion = statusKVP.getValue();

                boolean found = false;

                // Look for the same element in the snapshot
                while(!snapshot.isAfterLast() && !found) {

                    // Get the snapshot key/value
                    snapshotKey     = snapshot.getString(keyColumnIndex);
                    snapshotVersion = snapshot.getString(valueColumnIndex);
                    int snapshotDeleted = snapshot.getInt(deletedColumnIndex);

                    if(snapshotKey.equals(statusKey)) {
                        found = true;
                        if(snapshotDeleted == 1) {
                            if (Log.isLoggable(Log.DEBUG)) {
                                Log.debug(LOG_TAG, "Found a deleted item with key: " + statusKey);
                            }
                            deletedItems.put(statusKey, statusVersion);
                        } else if(!statusVersion.equals(snapshotVersion)) {
                            if (Log.isLoggable(Log.DEBUG)) {
                                Log.debug(LOG_TAG, "Found an updated item with key: " + snapshotKey);
                                Log.debug(LOG_TAG, "statusVersion: " + statusVersion +
                                                   ",snapshotVersion=" + snapshotVersion);
                            }
                            updatedItems.put(snapshotKey, snapshotVersion);
                        }
                    } else if(!(snapshotDeleted == 1)) {
                        if (Log.isLoggable(Log.DEBUG)) {
                            Log.debug(LOG_TAG, "Found a new item with key: " + snapshotKey);
                        }
                        newItems.put(snapshotKey, snapshotVersion);
                    }
                    snapshot.moveToNext();
                }
                if(!found) {
                    if (Log.isLoggable(Log.DEBUG)) {
                        Log.debug(LOG_TAG, "Found a deleted item with key: " + statusKey);
                    }
                    deletedItems.put(statusKey, statusVersion);
                }
            }
            while(!snapshot.isAfterLast()) {

                snapshotKey     = snapshot.getString(keyColumnIndex);
                snapshotVersion = snapshot.getString(valueColumnIndex);
                int snapshotDeleted = snapshot.getInt(deletedColumnIndex);

                if(!(snapshotDeleted == 1)) {
                    if (Log.isLoggable(Log.DEBUG)) {
                        Log.debug(LOG_TAG, "Found a new item with key: " + snapshotKey);
                    }
                    newItems.put(snapshotKey, snapshotVersion);
                }
                snapshot.moveToNext();
            }
        } finally {
            if(snapshot != null) {
                snapshot.close();
            }
        }
    }

 

    /**
     * Computes the item fingerprint using the Andoid Contact Version. The
     * Version attribute is incremented everytime a Contact is modified.
     * 
     * @param item The SyncItem object.
     * @return The item version.
     */
    @Override
    protected String computeFingerprint(SyncItem item) {

        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(LOG_TAG, "computeFingerprint");
        }

        String fp = "1";
        
        String cols[] = {RawContacts.VERSION};
        Cursor versionCursor = resolver.query(uri, cols,
                RawContacts._ID + " = \"" + item.getKey() + "\"", null, null);
       
        if(versionCursor.getCount() > 0) {
            versionCursor.moveToFirst();
            fp = versionCursor.getString(0);
        } 
        versionCursor.close();
        return fp;
    }

    public void setItemsStatus(Vector itemsStatus) throws TrackerException {
        for(int i=0;i<itemsStatus.size();++i) {
            ItemStatus itemStatus = (ItemStatus)itemsStatus.elementAt(i);
            String key = itemStatus.getKey();
            int status = itemStatus.getStatus();
            setItemStatus(key, status);
        }
    }

    @Override
    public void setItemStatus(String key, int itemStatus) throws TrackerException {
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(LOG_TAG, "setItemStatus " + key + "," + itemStatus);
        }

        // First of all update the contacts store
        long id = Long.parseLong(key);
        if (isSuccess(itemStatus) && 
            itemStatus != SyncSource.CHUNK_SUCCESS_STATUS && cm != null) {
            if (deletedItems.contains(key)) {
                cm.hardDelete(id);
            } else {
                cm.refreshSourceIdAndDirtyFlag(id);
            }
        }

        // Update the tracker status
        if(syncMode == SyncSource.FULL_SYNC ||
           syncMode == SyncSource.FULL_UPLOAD) {
            SyncItem item = new SyncItem(key);
            if(status.get(key) != null) {
                status.update(key, computeFingerprint(item));
            } else {
                status.add(key, computeFingerprint(item));
            }
        } else if (isSuccess(itemStatus) && itemStatus != SyncSource.CHUNK_SUCCESS_STATUS) {
            // We must update the fingerprint store with the value of the
            // fingerprint at the last sync
            if (newItems.get(key) != null) {
                // This is a new item
                String itemFP = (String)newItems.get(key);
                // Update the fingerprint
                status.add(key, itemFP);
            } else if (updatedItems.get(key) != null) {
                // This is a new item
                String itemFP = (String)updatedItems.get(key);
                // Update the fingerprint
                status.update(key, itemFP);
            } else if (deletedItems.get(key) != null) {
                // Update the fingerprint
                status.remove(key);
            }
        }
    }

    protected Uri addCallerIsSyncAdapterFlag(Uri uri) {
        Uri.Builder b = uri.buildUpon();
        b.appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true");
        return b.build();
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
