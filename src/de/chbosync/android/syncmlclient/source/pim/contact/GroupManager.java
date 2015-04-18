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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import com.funambol.util.Log;

import de.chbosync.android.syncmlclient.AndroidCustomization;
import de.chbosync.android.syncmlclient.source.AbstractDataManager;


public class GroupManager extends AbstractDataManager<Group> {

    private static final String TAG_LOG = "GroupManager";

    private static final Uri GROUPS_URI = ContactsContract.Groups.CONTENT_URI;

    private static final String FUNAMBOL_SOURCE_ID_PREFIX = "funambol-";

    protected AndroidCustomization customization = AndroidCustomization.getInstance();

    protected boolean callerIsSyncAdapter = true;

    protected int allItemsCount = 0;

    // Constructors------------------------------------------------
    public GroupManager(Context context) {
        this(context, true);
    }

    public GroupManager(Context context, boolean callerIsSyncAdapter) {
        super(context);
        this.callerIsSyncAdapter = callerIsSyncAdapter;
    }

    protected String getAuthority() {
        return ContactManager.CONTACTS_AUTHORITY;
    }

    @Override
    public Group load(String key) throws IOException {

        long id;
        try {
            id = Long.parseLong(key);
        } catch (Exception e) {
            Log.error(TAG_LOG, "Invalid key: " + key, e);
            throw new IOException("Invalid key " + key);
        }

        Uri uri = addCallerIsSyncAdapterFlag(ContactsContract.Groups.CONTENT_URI);
        uri = ContentUris.withAppendedId(uri, id);

        Cursor cursor = null;
        try {
            cursor = resolver.query(uri, null, null, null, null);
            if (!cursor.moveToFirst()) {
                throw new IOException("Item not found " + key);
            }
            // Load the fields we handle
            String title = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Groups.TITLE));
            String notes = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Groups.NOTES));
            String systemId = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Groups.SYSTEM_ID));

            Group g = new Group();
            g.setTitle(title);
            g.setNotes(notes);
            g.setSystemId(systemId);

            return g;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    @Override
    public String add(Group item) throws IOException {
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Saving group");
        }

        Uri uri = addCallerIsSyncAdapterFlag(ContactsContract.Groups.CONTENT_URI);

        // Create the content value
        ContentValues cv = prepareAllFields(item);
        // Set the group as visible by default
        cv.put(ContactsContract.Groups.GROUP_VISIBLE, 1);

        // Now create the contact with a single batch operation
        try {
            uri = resolver.insert(uri, cv);
            // The first insert is the one generating the ID for this contact
            long id = ContentUris.parseId(uri); 
            if (Log.isLoggable(Log.TRACE)) {
                Log.trace(TAG_LOG, "The new group has id: " + id);
            }
            item.setId(id);
            return "" + id;
        } catch (Exception e) {
            Log.error(TAG_LOG, "Cannot create group ", e);
            throw new IOException("Cannot create group in db");
        }
    }

    @Override
    public void update(String key, Group item) throws IOException {

        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Updating group: " + key);
        }

        long groupId;
        try {
            groupId = Long.parseLong(key);
        } catch(Exception e) {
            Log.error(TAG_LOG, "Invalid group key " + key, e);
            throw new IOException("Invalid group key");
        }

        // If the contact does not exist, then we perform an add
        if (!exists(key)) {
            if (Log.isLoggable(Log.INFO)) {
                Log.info(TAG_LOG, "Tried to update a non existing group. Creating a new one ");
            }
            add(item);
            return;
        }

        // Set the id
        item.setId(groupId);
        // Prepare the new fields and update
        Uri uri = addCallerIsSyncAdapterFlag(ContactsContract.Groups.CONTENT_URI);
        uri = ContentUris.withAppendedId(uri, groupId);
        ContentValues cv = prepareAllFields(item);
        resolver.update(uri, cv, null, null);
    }

    @Override
    public void delete(String key) throws IOException {

        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Deleting group: " + key);
        }

        long groupId;
        try {
            groupId = Long.parseLong(key);
        } catch (Exception e) {
            Log.error(TAG_LOG, "Invalid group id " + key, e);
            throw new IOException("Invalid group id");
        }

        int count = hardDelete(groupId);

        if (Log.isLoggable(Log.DEBUG)) {
            Log.debug(TAG_LOG, "Deleted group count: " + count);
        }
        if (count < 1) {
            Log.error(TAG_LOG, "Cannot delete groups: " + groupId);
            throw new IOException("Cannot delete groups: " + groupId);
        }
    }

    @Override
    public void deleteAll() throws IOException {

        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Deleting all groups");
        }

        Uri uri = addCallerIsSyncAdapterFlag(ContactsContract.Groups.CONTENT_URI);

        // Delete from groups
        // Note: delete only contacts from funambol accounts
        int count = resolver.delete(uri,
                ContactsContract.Groups.ACCOUNT_TYPE+"='"+accountType+"'", null);

        if (Log.isLoggable(Log.DEBUG)) {
            Log.debug(TAG_LOG, "Deleted groups count: " + count);
        }
        if (count < 0) {
            Log.error(TAG_LOG, "Cannot delete all groups");
            throw new IOException("Cannot delete groups");
        }
    }

    @Override
    public boolean exists(String key) {

        long id;
        try {
            id = Long.parseLong(key);
        } catch (Exception e) {
            Log.error(TAG_LOG, "Invalid item key " + key, e);
            return false;
        }

        String cols[] = {ContactsContract.Groups._ID, ContactsContract.Groups.DELETED};
        Uri uri = ContentUris.withAppendedId(ContactsContract.Groups.CONTENT_URI, id);
        Cursor cur = resolver.query(uri, cols, null, null, null);

        boolean found;
        if (!cur.moveToFirst()) {
            found = false;
        } else {
            int deleted = cur.getInt(1);
            if (deleted == 0) {
                found = true;
            } else {
                found = false;
            }
        }

        cur.close();
        return found;
    }

    @Override
    public Enumeration getAllKeys() throws IOException {

        String cols[] = {ContactsContract.Groups._ID};
        StringBuffer whereClause = new StringBuffer();
        if (accountName != null) {
            whereClause.append(ContactsContract.Groups.ACCOUNT_NAME).append("='").append(accountName).append("'");
            whereClause.append(" AND ");
            whereClause.append(ContactsContract.Groups.ACCOUNT_TYPE).append("='").append(accountType).append("'");
            whereClause.append(" AND ");
        }
        whereClause.append(ContactsContract.Groups.DELETED).append("=").append("0");
        Cursor groupsCur = resolver.query(ContactsContract.Groups.CONTENT_URI,
                                          cols, whereClause.toString(), null, null);

        try {
            int groupsSize = groupsCur.getCount();
            Vector<String> itemKeys = new Vector<String>(groupsSize);

            if (!groupsCur.moveToFirst()) {
                return itemKeys.elements();
            }

            for (int i = 0; i < groupsSize; i++) {
                String key = groupsCur.getString(0);
                if (Log.isLoggable(Log.TRACE)) {
                    Log.trace(TAG_LOG, "Found item with key: " + key);
                }
                itemKeys.addElement(key);
                groupsCur.moveToNext();
            }

            allItemsCount = groupsSize;

            return itemKeys.elements();
        } catch (Exception e) {
            Log.error(TAG_LOG, "Cannot get all items keys: ", e);
            throw new IOException("Cannot get all items keys");
        } finally {
            groupsCur.close();
        }
    }

    @Override
    public int getAllCount() throws IOException {
        return allItemsCount;
    }

    public Vector<com.funambol.syncml.protocol.Property> getSupportedProperties() {
        Vector<com.funambol.syncml.protocol.Property> properties =
                new Vector<com.funambol.syncml.protocol.Property>();
        return properties;
    }

    public Vector commit() {
        return null;
    }

    protected String getGroupLuid(String id) {
        return "G" + id;
    }

    protected String getGroupId(String luid) {
        if (luid.charAt(0) != 'G') {
            // This is not expected
            throw new IllegalArgumentException("Illegal group luid " + luid);
        } else {
            return luid.substring(1);
        }
    }

    protected Uri addCallerIsSyncAdapterFlag(Uri uri) {
        if(callerIsSyncAdapter) {
            Uri.Builder b = uri.buildUpon();
            b.appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true");
            return b.build();
        } else {
            return uri;
        }
    }

    /**
     * Hard delete the group from the store
     * @param rawContactId
     * @return
     */
    protected int hardDelete(long groupId) {

        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Hard deleting group: " + groupId);
        }

        Uri uri = addCallerIsSyncAdapterFlag(ContactsContract.Groups.CONTENT_URI);

        // Delete from raw_contacts (related rows in Data table are
        // automatically deleted)
        return resolver.delete(uri,
                ContactsContract.Groups._ID+"="+groupId, null);
    }

    private ContentValues prepareAllFields(Group group) {
        ContentValues cv = new ContentValues();
        String title = group.getTitle();
        String notes = group.getNotes();
        String systemId = group.getSystemId();

        if (title != null) {
            cv.put(ContactsContract.Groups.TITLE, title);
        }
        if (notes != null) {
            cv.put(ContactsContract.Groups.NOTES, notes);
        }
        if (systemId != null) {
            cv.put(ContactsContract.Groups.SYSTEM_ID, systemId);
        }

        // Add the account name and account type
        cv.put(ContactsContract.Groups.ACCOUNT_NAME, accountName);
        cv.put(ContactsContract.Groups.ACCOUNT_TYPE, accountType);

        return cv;
    }

    protected void linkContactsToGroup(String groupId, List<String> contacts) throws IOException {

        // The association is in the contacts table where we have one row per
        // group
        // We must add a row for each contact in the Data table.
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        Uri uri = addCallerIsSyncAdapterFlag(ContactsContract.Data.CONTENT_URI);

        try {

            for(String contactId : contacts) {

                if (Log.isLoggable(Log.TRACE)) {
                    Log.trace(TAG_LOG, "Creating query to associate " + contactId + " to group " + groupId);
                }

                ContentProviderOperation op = ContentProviderOperation.newInsert(uri)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, contactId)
                    .withValue(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID, groupId)
                    .withValue(ContactsContract.Data.MIMETYPE,
                               ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE)
                    .build();

                ops.add(op);
            }

            resolver.applyBatch(ContactsContract.AUTHORITY, ops);
        } catch (Exception e) {
            Log.error(TAG_LOG, "Cannot link contacts to group", e);
            throw new IOException("Cannot link contacts to group");
        }
    }

    protected void unlinkContactsFromGroup(String groupId) throws IOException {

        // The association is in the contacts table where we have one row per
        // group
        // We must delete all the rows of this account where the mime type is
        // the groupmembership and the groupid is the given one
        StringBuffer whereClause = new StringBuffer();
        whereClause.append(ContactsContract.Data.MIMETYPE).append("='")
                   .append(ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE).append("'")
                   .append(" AND ")
                   .append(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID).append("='")
                   .append(groupId).append("'");

        resolver.delete(ContactsContract.Data.CONTENT_URI, whereClause.toString(), null);
    }
}
