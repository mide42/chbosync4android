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

package de.chbosync.android.syncmlclient.source.pim.contact;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Vector;

import android.accounts.Account;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.ContactsContract;

import com.funambol.client.configuration.Configuration;
import com.funambol.client.source.AppSyncSource;
import com.funambol.common.pim.model.common.FormatterException;
import com.funambol.common.pim.vcard.ParseException;
import com.funambol.sync.SourceConfig;
import com.funambol.sync.SyncException;
import com.funambol.sync.SyncItem;
import com.funambol.sync.SyncSource;
import com.funambol.sync.client.ChangesTracker;
import com.funambol.syncml.protocol.SyncML;
import com.funambol.util.Log;

import de.chbosync.android.syncmlclient.AndroidCustomization;
import de.chbosync.android.syncmlclient.IntKeyValueSQLiteStore;
import de.chbosync.android.syncmlclient.controller.AndroidController;
import de.chbosync.android.syncmlclient.source.AndroidChangesTracker;
import de.chbosync.android.syncmlclient.source.pim.PIMSyncSource;


/**
 * This is a sync source for contacts on Android (syncing on the standard
 * provider).
 * The source syncs contacts and optionally groups. There are three things to do
 * to enable groups sync:
 *
 * <ul>
 *   <li> Construct the object with a GroupManager instance </li>
 *   <li> Extends the class and reimplement the isGroup method </li>
 *   <li> Use a tracker which is capable of tracking both contacts and groups
 *   (e.g. ContactsGroupsVersionCacheTracker) </li>
 * </ul>
 *
 * When groups sync is turned on, the method isGroup(String) is responsible for
 * discriminating between contacts and groups. Since the method takes the LUID
 * as only input, it must be possible to discriminate the two categories bases
 * on this value. The class by default use a 'G' prefix for group LUIDs and use
 * it as discriminator.
 *
 */
public class ContactSyncSource extends PIMSyncSource<Contact> {

    private static final String TAG_LOG = "ContactSyncSource";

    protected GroupManager gm = null;

    public ContactSyncSource(SourceConfig config, ChangesTracker tracker, Context context,
                             Configuration configuration, AppSyncSource appSource,
                             ContactManager contactManager) {
        super(config, tracker, context, configuration, appSource, contactManager);
        this.gm = null;
    }

    public ContactSyncSource(SourceConfig config, ChangesTracker tracker, Context context,
                             Configuration configuration, AppSyncSource appSource,
                             ContactManager contactManager, GroupManager groupManager) {
        super(config, tracker, context, configuration, appSource, contactManager);
        this.gm = groupManager;
    }

    @Override
    public void applyChanges(Vector items) throws SyncException {
        // Groups can be at the beginning or at the end of the list
        // (depending on how groups info is stored)
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "applyChanges" + items);
        }

        dm.beginTransaction();

        for(int i=0;i<items.size();++i) {

            cancelIfNeeded();
            
            SyncItem item = (SyncItem)items.elementAt(i);
            if (item.getState() == SyncItem.STATE_NEW) {
                try {
                    int itemStatus = addItem(item);
                    item.setSyncStatus(itemStatus);
                } catch (Exception e) {
                    Log.error(TAG_LOG, "Cannot add item", e);
                    item.setSyncStatus(SyncSource.ERROR_STATUS);
                }
            } else if (item.getState() == SyncItem.STATE_UPDATED) {
                try {
                    int itemStatus = updateItem(item);
                    item.setSyncStatus(itemStatus);
                } catch (Exception e) {
                    Log.error(TAG_LOG, "Cannot update item", e);
                    item.setSyncStatus(SyncSource.ERROR_STATUS);
                }
            } else {
                try {
                    int itemStatus = deleteItem(item.getKey());
                    item.setSyncStatus(itemStatus);
                } catch (Exception e) {
                    Log.error(TAG_LOG, "Cannot delete item", e);
                    item.setSyncStatus(SyncSource.ERROR_STATUS);
                }
            }
        }

        // Now commit all the changes
        try {
            Vector newContactKeys = dm.commit();
            Vector newGroupKeys = null;
            if (gm != null) {
                newGroupKeys = gm.commit();
            }

            // Update the keys for the newly created items and invoke the super.add/update/delete
            // methods now that everything is finalized
            if (newContactKeys != null) {
                int cKeysIdx = 0;
                int gKeysIdx = 0;
                for(int i=0;i<items.size();++i) {
                    SyncItem item = (SyncItem)items.elementAt(i);
                    // If the item is in error, then we shall not process it
                    // here
                    if (item.getSyncStatus() != SyncSource.ERROR_STATUS) {
                        if (item.getState() == SyncItem.STATE_NEW) {

                            String key;
                            if (isGroup(item)) {
                                // If there are no keys returned, then we assume the group manager has
                                // applied keys on the fly
                                if (newGroupKeys != null) {
                                    if (gKeysIdx >= newGroupKeys.size()) {
                                        Log.error(TAG_LOG, "Items mismatch while setting group keys");
                                        throw new SyncException(SyncException.CLIENT_ERROR, "Items mismatch");
                                    }
                                    key = (String)newGroupKeys.elementAt(gKeysIdx);
                                    if (key.length() == 0) {
                                        // This item was not inserted correctly
                                        item.setSyncStatus(SyncSource.ERROR_STATUS);
                                    } else {
                                        key = getGroupLuid(key);
                                    }
                                    gKeysIdx++;
                                } else {
                                    key = item.getKey();
                                }
                            } else {
                                if (newContactKeys != null) {
                                    if (cKeysIdx >= newContactKeys.size()) {
                                        Log.error(TAG_LOG, "Items mismatch while setting contact keys");
                                        throw new SyncException(SyncException.CLIENT_ERROR, "Items mismatch");
                                    }
                                    key = (String)newContactKeys.elementAt(cKeysIdx);
                                    if (key.length() == 0) {
                                        // This item was not inserted correctly
                                        item.setSyncStatus(SyncSource.ERROR_STATUS);
                                    }
                                    cKeysIdx++;
                                } else {
                                    key = item.getKey();
                                }
                            }
                            item.setKey(key);

                            // This will take care of cleaning up the tracker and perform other common operations
                            super.addItem(item);
                        } else if (item.getState() == SyncItem.STATE_UPDATED) {
                            super.updateItem(item);
                        } else {
                            // We cannot invoke the super because it tries to remove
                            // the item again and does not have group/contact
                            // knowledge
                            tracker.removeItem(item);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.error(TAG_LOG, "Cannot commit all changes", e);
            throw new SyncException(SyncException.CLIENT_ERROR, "Cannot commit changes");
        }
    }

    /** Logs the new item from the server. */
    @Override
    protected int addItem(SyncItem item) {
        if (Log.isLoggable(Log.INFO)) {
            Log.info(TAG_LOG, "New item " + item.getKey() + " from server.");
        }

        byte[] itemContent = item.getContent();

        if(itemContent.length > 2048) {
            String logContent = new String(itemContent, 0, 2048);
            if (Log.isLoggable(Log.TRACE)) {
                Log.trace(TAG_LOG, logContent);
            }
            if (Log.isLoggable(Log.TRACE)) {
                Log.trace(TAG_LOG, "Item content is too big, logging 2KB only");
            }
        } else {
            String logContent = new String(itemContent);
            if (Log.isLoggable(Log.TRACE)) {
                Log.trace(TAG_LOG, logContent);
            }
        }

        if (syncMode == SyncSource.FULL_UPLOAD || syncMode == SyncSource.INCREMENTAL_UPLOAD) {
            Log.error(TAG_LOG, "Server is trying to update items for a one way sync! " + "(syncMode: " + syncMode + ")");
            return SyncSource.ERROR_STATUS;
        }

        try {
            String id = addItemToDataManager(item);
            // Note that at this point the key is set to null because we actually add the items later
            item.setKey(id);
            // Note that the super.addItem is invoked later when changes are actually applied
            return SyncSource.SUCCESS_STATUS;
        } catch (Throwable t) {
            Log.error(TAG_LOG, "Cannot save contact", t);
            return SyncSource.ERROR_STATUS;
        }
    }

    /**
     * Add the given item using the proper data manager.
     * 
     * @param item
     * @return
     * @throws Exception
     */
    protected String addItemToDataManager(SyncItem item) throws Exception {
        byte[] itemContent = item.getContent();
        boolean isGroup = isGroup(item);
        String id;
        if (isGroup) {
            Group g = parseGroup(itemContent);
            id = gm.add(g);
            id = getGroupLuid(id);
        } else {
            Contact c = parseContact(itemContent);
            id = dm.add(c);
        }
        return id;
    }

    /** Update a given SyncItem stored on the source backend */
    @Override
    protected int updateItem(SyncItem item) {
        if (Log.isLoggable(Log.INFO)) {
            Log.info(TAG_LOG, "Updated item " + item.getKey() + " from server.");
        }

        if (syncMode == SyncSource.FULL_UPLOAD || syncMode == SyncSource.INCREMENTAL_UPLOAD) {
            Log.error(TAG_LOG, "Server is trying to update items for a one way sync! " + "(syncMode: " + syncMode + ")");
            return SyncSource.ERROR_STATUS;
        }

        // Create a new contact
        try {

            boolean isGroup = isGroup(item.getKey());
            if (isGroup) {
                Group g = parseGroup(item.getContent());
                String id = getGroupId(item.getKey());
                gm.update(id, g);
            } else {
                // If the contact does not exist already, then this is like a new
                Contact c = parseContact(item.getContent());
                dm.update(item.getKey(), c);
            }

            return SyncSource.SUCCESS_STATUS;
        } catch (Throwable t) {
            Log.error(TAG_LOG, "Cannot update contact ", t);
            return SyncSource.ERROR_STATUS;
        }
    }

    @Override
    public int deleteItem(String key) {
        boolean isGroup = isGroup(key);
        if (isGroup) {
            try {
                String id = getGroupId(key);
                gm.delete(id);
            } catch (IOException ioe) {
                Log.error(TAG_LOG, "Cannot delete group " + key, ioe);
                return SyncSource.ERROR_STATUS;
            }
            // a new item was replaced during the sync. we must notify our tracker
            SyncItem item = new SyncItem(key,getType(),SyncItem.STATE_DELETED,null);
            boolean done = tracker.removeItem(item);
            return done ? SyncSource.SUCCESS_STATUS : SyncSource.ERROR_STATUS;
        } else {
            try {
                dm.delete(key);
                return SyncSource.SUCCESS_STATUS;
            } catch (IOException ioe) {
                Log.error(TAG_LOG, "Cannot delete contact", ioe);
                return SyncSource.ERROR_STATUS;
            }
        }
    }

    @Override
    protected SyncItem getItemContent(final SyncItem item) throws SyncException {
        try {
            // Load all the item content

            boolean isGroup = isGroup(item.getKey());
            String key;
            if (isGroup) {
                key = getGroupId(item.getKey());
            } else {
                key = item.getKey();
            }
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            if (isGroup) {
                Group g = gm.load(key);
                formatGroup(os, g, gm.getSupportedFields());
            } else {
                Contact c = dm.load(key);
                formatContact(os, c, dm.getSupportedFields());
            }
            SyncItem res = new SyncItem(item);
            res.setContent(os.toByteArray());
            return res;
        } catch (Throwable t) {
            Log.error(TAG_LOG, "Cannot get contact content for " + item.getKey(), t);
            throw new SyncException(SyncException.CLIENT_ERROR, "Cannot get contact content");
        }
    }

    @Override
    public Enumeration getAllItemsKeys() throws SyncException {

        if (Log.isLoggable(Log.INFO)) {
            Log.info(TAG_LOG, "getAllItemsKeys");
        }
        if (gm == null) {
            return super.getAllItemsKeys();
        } else {
            // If this source has group sync enabled, then we must grab both
            // sets
            try {
                Enumeration contactsEnum = dm.getAllKeys();
                Enumeration groupsEnum   = gm.getAllKeys();
                return createJoinedEnumeration(groupsEnum, contactsEnum);
            } catch (IOException ioe) {
                Log.error(TAG_LOG, "Cannot get all keys", ioe);
                throw new SyncException(SyncException.CLIENT_ERROR, "Cannot get all keys");
            }
        }
    }

    @Override
    public int getAllItemsCount() throws SyncException {
        try {
            int tot = dm.getAllCount();
            if (gm != null) {
                int gn = gm.getAllCount();
                if (gn != -1) {
                    tot += gn;
                }
            }
            return tot;
        } catch (IOException ioe) {
            Log.error(TAG_LOG, "Cannot get all count", ioe);
            throw new SyncException(SyncException.CLIENT_ERROR, "Cannot get all keys count");
        }
    }

    @Override
    protected void deleteAllItems() throws SyncException {
        super.deleteAllItems();
        try {
            dm.deleteAll();
            if(gm != null) {
                gm.deleteAll();
            }
        } catch (IOException ioe) {
            throw new SyncException(SyncException.STORAGE_ERROR, ioe.getMessage());
        }
    }

    /**
     * This method detects if an item that has just been sent to the server is a
     * group or not. It must be possible to discriminate items depending on the
     * key only, so the LUIDs must be carefully generated.
     */
    public boolean isGroup(String key) {
        return false;
    }

    public boolean isGroup(SyncItem item) {
        return false;
    }

    public String getGroupLuid(String id) {
        return "G" + id;
    }

    public String getGroupId(String luid) {
        if (luid.charAt(0) != 'G') {
            // This is not expected
            throw new IllegalArgumentException("Illegal group luid " + luid);
        } else {
            return luid.substring(1);
        }
    }

    protected Contact parseContact(byte[] itemContent) throws ParseException {
        Contact c = new Contact();
        c.setVCard(itemContent);
        return c;
    }

    protected void formatContact(OutputStream os, Contact c, Vector supportedFields)
    throws FormatterException, IOException {
        c.toVCard(os, supportedFields);
    }

    protected Group parseGroup(byte[] itemContent) throws ParseException {
        Group g = new Group();
        g.parse(itemContent);
        return g;
    }

    protected void formatGroup(OutputStream os, Group g, Vector supportedFields)
    throws FormatterException, IOException {
        g.format(os, supportedFields);
    }

    protected Enumeration createJoinedEnumeration(Enumeration groupsEnum, Enumeration contactsEnum) {
        Enumeration res = new JoinedEnumerationContactsFirst(groupsEnum, contactsEnum, this);
        return res;
    }

    /**
     * Migrates the changes tracker based on the version to the one based on the dirty flag.
     * @param sc
     * @param cm
     * @param c
     */
    public static void migrateToDirtyChangesTracker(SourceConfig sc, ContactManager cm, Context c) {
        if (Log.isLoggable(Log.DEBUG)) {
            Log.debug(TAG_LOG, "Migrating contacts changes tracker");
        }

        AndroidCustomization customization = AndroidCustomization.getInstance();
        
        // Initialize the old tracker store in order to retrieve the current
        // updated items. All contacts except these ones must have 0 dirty flag

        IntKeyValueSQLiteStore trackerStore = new IntKeyValueSQLiteStore(c,
            ((AndroidCustomization)customization).getFunambolSQLiteDbName(),
            sc.getName());

        // Retrieve current modifications
        AndroidChangesTracker oldTracker = new VersionCacheTracker(
                trackerStore, c, cm);
        oldTracker.begin(SyncML.ALERT_CODE_FAST, true);

        Enumeration newItems     = oldTracker.getNewItems();
        Enumeration updatedItems = oldTracker.getUpdatedItems();
        Enumeration deletedItems = oldTracker.getDeletedItems();

        oldTracker.end();

        StringBuffer whereClause = new StringBuffer();
        Account account = AndroidController.getNativeAccount();
        String  accountType = account != null ? account.type : "";
        String  accountName = account != null ? account.name : "";

        if(accountName != null && accountType != null) {
            whereClause.append(ContactsContract.RawContacts.ACCOUNT_NAME)
                    .append("='").append(accountName).append("'");
            whereClause.append(" AND ");
            whereClause.append(ContactsContract.RawContacts.ACCOUNT_TYPE)
                    .append("='").append(accountType).append("'");
        }

        ContentValues cv = new ContentValues();
        cv.put(ContactsContract.RawContacts.DIRTY, 0);

        Uri.Builder b = ContactsContract.RawContacts.CONTENT_URI.buildUpon();
        b.appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true");

        // First of all we reset the dirty flag for all our contacts
        if (Log.isLoggable(Log.DEBUG)) {
            Log.debug(TAG_LOG, "Reset dirty flag for all contacts");
        }
        c.getContentResolver().update(b.build(),
                cv, whereClause.toString(), null);

        // Then we set the dirty flag for all the new/updated/deleted items
        setDirtyFlagForItems(newItems, b.build(), c);
        setDirtyFlagForItems(updatedItems, b.build(), c);
        setDirtyFlagForItems(deletedItems, b.build(), c);
    }

    private static void setDirtyFlagForItems(Enumeration items, Uri uri, Context c) {

        final int MAX_ITEMS_PER_QUERY = 100;
        int count = 0;

        ContentValues cv = new ContentValues();
        cv.put(ContactsContract.RawContacts.DIRTY, 1);

        StringBuffer whereClause = new StringBuffer();
        
        while(items.hasMoreElements()) {

            String key = (String)items.nextElement();
            count++;

            if (Log.isLoggable(Log.DEBUG)) {
                Log.debug(TAG_LOG, "Set dirty flag for contact: " + key);
            }

            whereClause.append(ContactsContract.RawContacts._ID);
            whereClause.append("=");
            whereClause.append(key);

            if(count >= MAX_ITEMS_PER_QUERY || !items.hasMoreElements()) {
                c.getContentResolver().update(uri, cv, whereClause.toString(), null);
                count = 0;
                whereClause = new StringBuffer();
            } else {
                whereClause.append(" OR ");
            }
        }
    }
}
