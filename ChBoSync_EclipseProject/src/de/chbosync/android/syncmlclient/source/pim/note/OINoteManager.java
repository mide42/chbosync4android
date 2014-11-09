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

package de.chbosync.android.syncmlclient.source.pim.note;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Vector;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.funambol.client.source.AppSyncSource;
import com.funambol.common.pim.model.common.Property;
import com.funambol.util.Log;
import com.funambol.util.StringUtil;

import de.chbosync.android.syncmlclient.source.AbstractDataManager;


public class OINoteManager extends AbstractDataManager<Note> {

    /** Log entries tag */
    private static final String TAG_LOG  = "OINoteManager";
    public static final String AUTHORITY = "org.openintents.notepad";
    

    /** Flag to store if app "OI Notepad" is installed on device (added for ChBoSync). */ 
    protected static boolean sOINotepadInstalled = false;
    
    /**
     * Added for ChBoSync; upon startup in method 
     * {@link de.chbosync.android.syncmlclient.AndroidAppSyncSourceManager.setupNotesSource(AndroidConfiguration)}
     * it is checked if "OI Notepad" is installed on the current device or not.
     * 
     * @param isInstalled <tt>true</tt> if "OI Notepad" is installed on the current device,
     *        <tt>false</tt> otherwise.
     */
    public static void setOINotepadInstalled(boolean isInstalled) { 
    	sOINotepadInstalled = isInstalled; 
    }
    
    
    /**
     * Added for ChBoSync
     * 
     * @return <tt>true</tt> if "OI Notepad" is installed on the current device,
     *        <tt>false</tt> otherwise.
     */
    public static boolean getOINotepadInstalled() {
    	return sOINotepadInstalled;
    }


    public static final class Notes {
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/notes");

        public static final String _ID = "_id";
        public static final String TITLE = "title";
        public static final String NOTE = "note";
        public static final String CREATED_DATE = "created";
        public static final String MODIFIED_DATE = "modified";
        public static final String TAGS = "tags";
        public static final String[] PROJECTION = { _ID,
                                                    TITLE,
                                                    NOTE,
                                                  };

    }

    private AppSyncSource appSource = null;

    /**
     * Default constructor.
     * @param context the Context object 
     * @param appSource the AppSyncSource object to be related to this manager
     */
    public OINoteManager(Context context, AppSyncSource appSource) {
        super(context);
        this.appSource = appSource;
    }

    /**
     * Accessor method: get the calendar authority that manages calendars in the
     * system
     * @return String the String formatted representation of the authority
     */
    protected String getAuthority() {
        return AUTHORITY;
    }

    /**
     * Load a particular calendar entry
     * @param key the long formatted entry key to load
     * @return Calendar the Calendar object related to that entry
     * @throws IOException if anything went wrong accessing the calendar db
     */
    public Note load(String key) throws IOException {

        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Loading Note: " + key);
        }

        long id;
        try {
            id = Long.parseLong(key);
        } catch (Exception e) {
            Log.error(TAG_LOG, "Invalid key: " + key, e);
            throw new IOException("Invalid key: " + key);
        }

        Note note = new Note();
        note.setId(id);

        Uri uri = ContentUris.withAppendedId(Notes.CONTENT_URI, id);
        Cursor cursor = resolver.query(uri, null, null, null, null);
        try {
            if(cursor != null && cursor.moveToFirst()) {
                loadNoteFields(cursor, note, id);
            } else {
                // Item not found
                throw new IOException("Cannot find event " + key);
            }
        } finally {
            cursor.close();
        }
        return note;
    }

    /**
     */
    @Override
    public String add(Note item) throws IOException {

        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Adding Note");
        }

        ContentValues cv = createNoteContentValues(item);
        Uri taskUri = resolver.insert(Notes.CONTENT_URI, cv);

        long id = Long.parseLong(taskUri.getLastPathSegment());
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "The new note has id: " + id);
        }

        return "" + id;
    }

    /**
     */
    @Override
    public void update(String key, Note newItem) throws IOException {

        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Updating note: " + key);
        }

        long id;
        try {
            id = Long.parseLong(key);
        } catch(Exception e) {
            Log.error(TAG_LOG, "Invalid item key " + key, e);
            throw new IOException("Invalid item key");
        }

        // If the contact does not exist, then we perform an add
        if (!exists(key)) {
            if (Log.isLoggable(Log.INFO)) {
                Log.info(TAG_LOG, "Tried to update a non existing event. Creating a new one ");
            }
            add(newItem);
            return;
        }

        ContentValues cv = createNoteContentValues(newItem);
        Uri uri = ContentUris.withAppendedId(Notes.CONTENT_URI, id);
        resolver.update(uri, cv, null, null);
    }

    /**
     */
    public void delete(String key) throws IOException {

        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Deleting note with id: " + key);
        }

        long itemId;
        try {
            itemId = Long.parseLong(key);
        } catch (Exception e) {
            Log.error(TAG_LOG, "Invalid item key " + key, e);
            throw new IOException("Invalid item key");
        }

        Uri uri = ContentUris.withAppendedId(Notes.CONTENT_URI, itemId);
        int count = resolver.delete(uri, null, null);

        if (Log.isLoggable(Log.DEBUG)) {
            Log.debug(TAG_LOG, "Deleted note count: " + count);
        }
        if (count < 0) {
            Log.error(TAG_LOG, "Cannot delete note");
            throw new IOException("Cannot delete note");
        }
    }

    /**
     * Delete all calendars from the calendar db
     * @throws IOException if anything went wrong accessing the calendar db
     */
    public void deleteAll() throws IOException {
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Deleting all notes");
        }
        Enumeration keys = getAllKeys();
        while(keys.hasMoreElements()) {
            String key = (String)keys.nextElement();
            delete(key);
        }
    }

    /**
     * Check if a calendar with the given id exists in the calendar db
     * @param id the id which existence is to be checked
     * @return true if the given id exists in the db false otherwise
     */
    public boolean exists(String key) {
        long id;
        try {
            id = Long.parseLong(key);
        } catch (Exception e) {
            Log.error(TAG_LOG, "Invalid item key " + key, e);
            return false;
        }

        Uri uri = ContentUris.withAppendedId(Notes.CONTENT_URI, id);
        Cursor cur = resolver.query(uri, null, null, null, null);
        if(cur == null) {
            return false;
        }
        boolean found = cur.getCount() > 0;
        cur.close();
        return found;
    }

    /**
     * Get all of the calendar keys that exist into the DB
     * @return Enumeration the enumeration object that contains alll of the
     * calendar keys
     * @throws IOException if anything went wrong accessing the calendar db
     */
    public Enumeration getAllKeys() throws IOException {

        String cols[] = {Notes._ID};
        Cursor cursor = resolver.query(Notes.CONTENT_URI, cols, null, null, null);
        try {
            int size = cursor.getCount();
            Vector<String> itemKeys = new Vector<String>(size);
            if (!cursor.moveToFirst()) {
                return itemKeys.elements();
            }
            for (int i = 0; i < size; i++) {
                String key = cursor.getString(0);
                if (Log.isLoggable(Log.TRACE)) {
                    Log.trace(TAG_LOG, "Found item with key: " + key);
                }
                itemKeys.addElement(key);
                cursor.moveToNext();
            }
            return itemKeys.elements();
        } catch (Exception e) {
            Log.error(TAG_LOG, "Cannot get all items keys: ", e);
            throw new IOException("Cannot get all items keys");
        } finally {
            cursor.close();
        }
    }

    public Vector<com.funambol.syncml.protocol.Property> getSupportedProperties() {
        // TODO: FIXME
        return null;
    }

    private void loadNoteFields(Cursor cursor, Note note, long key) {

        // Load TITLE
        String name = cursor.getString(cursor.getColumnIndex(Notes.TITLE));
        if(name != null) {
            note.setTitle(new Property(name));
        }

        // Load NOTE
        String noteValue = cursor.getString(cursor.getColumnIndex(Notes.NOTE));
        if(noteValue != null) {
            note.setBody(new Property(noteValue));
        }
    }

    public Vector commit() {
        return null;
    }

    /**
     */
    private ContentValues createNoteContentValues(Note note) throws IOException {

        ContentValues cv = new ContentValues();

        // Put title property
        putStringProperty(Notes.TITLE, note.getTitle(), cv);

        // Body
        putStringProperty(Notes.NOTE, note.getBody(), cv);

        return cv;
    }

    /**
     * Put a String property to the given ContentValues.
     * @param column the culumn to be written
     * @param property the property to be written into the column
     * @param cv the content values related to the property
     */
    private void putStringProperty(String column,
            Property property, ContentValues cv) {
        if(property != null) {
            String value = property.getPropertyValueAsString();
            if(value != null) {
                value = StringUtil.replaceAll(value, "\r\n", "\n");
                value = StringUtil.replaceAll(value, "\r",   "\n");
                cv.put(column, value);
            }
        }
    }
}

