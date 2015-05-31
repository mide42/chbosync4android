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

package de.chbosync.android.syncmlclient.source.pim.contact;

import java.util.Enumeration;
import java.util.Vector;

import com.funambol.sync.ItemStatus;
import com.funambol.sync.SyncItem;
import com.funambol.sync.client.CacheTracker;
import com.funambol.sync.client.TrackableSyncSource;
import com.funambol.sync.client.TrackerException;
import com.funambol.util.Log;

import de.chbosync.android.syncmlclient.source.AndroidChangesTracker;


public class ContactsGroupsVersionCacheTracker extends CacheTracker
                                               implements AndroidChangesTracker {

    private final String TAG_LOG = "ContactsGroupsVersionCacheTracker";

    private AndroidChangesTracker ct;
    private AndroidChangesTracker gt;

    public ContactsGroupsVersionCacheTracker(AndroidChangesTracker ct, AndroidChangesTracker gt) {
        super(null);
        this.ct = ct;
        this.gt = gt;
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
            Log.trace(TAG_LOG, "begin");
        }

        ct.begin(syncMode, resume);
        gt.begin(syncMode, resume);
    }

    @Override
    public boolean hasChangedSinceLastSync(String key, long ts) {

        if (isGroup(key)) {
            return gt.hasChangedSinceLastSync(key, ts);
        } else {
            return ct.hasChangedSinceLastSync(key, ts);
        }
    }

    @Override
    public boolean supportsResume() {
        return true;
    }

    @Override
    public void setItemsStatus(Vector itemsStatus) throws TrackerException {
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "setItemsStatus " + itemsStatus.size());
        }
        Vector groupItemsStatus = new Vector();
        Vector contactItemsStatus = new Vector();
        for(int i=0;i<itemsStatus.size();++i) {
            ItemStatus itemStatus = (ItemStatus) itemsStatus.elementAt(i);
            String key = itemStatus.getKey();
            if (isGroup(key)) {
                key = ((ContactSyncSource)ss).getGroupId(key);
                itemStatus.setKey(key);
                groupItemsStatus.addElement(itemStatus);
            } else {
                contactItemsStatus.addElement(itemStatus);
            }
        }
        if (contactItemsStatus.size() > 0) {
            ct.setItemsStatus(contactItemsStatus);
        }
        if (groupItemsStatus.size() > 0) {
            gt.setItemsStatus(groupItemsStatus);
        }
    }

    @Override
    public boolean removeItem(SyncItem item) throws TrackerException {
        String key = item.getKey();

        if (isGroup(key)) {
            key = ((ContactSyncSource)ss).getGroupId(key);
            SyncItem tmpItem = new SyncItem(key);
            return gt.removeItem(tmpItem);
        } else {
            return ct.removeItem(item);
        }
    }

    public boolean hasChanges() {
        if (ct.hasChanges()) {
            return true;
        } else {
            return gt.hasChanges();
        }
    }

    @Override
    public void setSyncSource(TrackableSyncSource ss) {
        ct.setSyncSource(ss);
        gt.setSyncSource(ss);
        this.ss = ss;
    }

    /**
     * This method cleans any pending change. In the cache sync source
     * this means that the fingerprint of each item is updated to its current
     * value. The fingerprint tables will contain exactly the same items that
     * are currently in the Sync source.
     */
    @Override
    public void reset() throws TrackerException {
        ct.reset();
        gt.reset();
    }

    @Override
    public void end() throws TrackerException {
        ct.end();
        gt.end();
    }

    @Override
    public int getNewItemsCount() throws TrackerException {
        return ct.getNewItemsCount() + gt.getNewItemsCount();
    }

    @Override
    public Enumeration getNewItems() throws TrackerException {
        Enumeration contactsEnum = ct.getNewItems();
        Enumeration groupsEnum   = gt.getNewItems();

        return new JoinedEnumerationContactsFirst(groupsEnum, contactsEnum, (ContactSyncSource)ss);
    }

    @Override
    public int getDeletedItemsCount() throws TrackerException {
        return ct.getDeletedItemsCount() + gt.getDeletedItemsCount();
    }

    @Override
    public Enumeration getDeletedItems() throws TrackerException {
        Enumeration contactsEnum = ct.getDeletedItems();
        Enumeration groupsEnum   = gt.getDeletedItems();

        return new JoinedEnumerationContactsFirst(groupsEnum, contactsEnum, (ContactSyncSource)ss);
    }

    /**
     * Returns the number of deleted items that will be returned by the getDeletedItems
     * method
     *
     * @return the number of items
     */
    @Override
    public int getUpdatedItemsCount() throws TrackerException {
        return ct.getUpdatedItemsCount() + gt.getUpdatedItemsCount();
    }

    @Override
    public Enumeration getUpdatedItems() throws TrackerException {
        Enumeration contactsEnum = ct.getUpdatedItems();
        Enumeration groupsEnum   = gt.getUpdatedItems();

        return new JoinedEnumerationContactsFirst(groupsEnum, contactsEnum, (ContactSyncSource)ss);
    }

    @Override
    public void empty() throws TrackerException {
        ct.empty();
        gt.empty();
    }

    protected boolean isGroup(String key) {
        // We can only be monitoring a contacts source, but we are very
        // conservative here
        if (ss instanceof ContactSyncSource) {
            return ((ContactSyncSource)ss).isGroup(key);
        } else {
            return false;
        }
    }
}
