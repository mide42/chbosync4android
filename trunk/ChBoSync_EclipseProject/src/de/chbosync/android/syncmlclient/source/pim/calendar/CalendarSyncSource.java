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

package de.chbosync.android.syncmlclient.source.pim.calendar;

import java.io.ByteArrayOutputStream;
import java.util.Vector;

import android.content.Context;

import com.funambol.client.configuration.Configuration;
import com.funambol.client.source.AppSyncSource;
import com.funambol.sync.SourceConfig;
import com.funambol.sync.SyncException;
import com.funambol.sync.SyncItem;
import com.funambol.sync.SyncSource;
import com.funambol.sync.client.ChangesTracker;
import com.funambol.util.Log;

import de.chbosync.android.syncmlclient.source.AbstractDataManager;
import de.chbosync.android.syncmlclient.source.pim.PIMSyncSource;


public class CalendarSyncSource extends PIMSyncSource<Calendar> {
    private static final String TAG = "CalendarSyncSource";

    /**
     * CalendarSyncSource constructor: initialize source config.
     * 
     * @param config
     * @param tracker
     * @param context
     * @param configuration
     * @param appSource
     */
    public CalendarSyncSource(SourceConfig config, ChangesTracker tracker, Context context,
                              Configuration configuration, AppSyncSource appSource, @SuppressWarnings("rawtypes") AbstractDataManager dm) {
        super(config, tracker, context, configuration, appSource, dm);
    }

    /** Logs the new item from the server. */
    @Override
    public int addItem(SyncItem item) {
        if (Log.isLoggable(Log.INFO)) {
            Log.info(TAG, "New item " + item.getKey() + " from server.");
        }
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG, new String(item.getContent()));
        }

        if (syncMode == SyncSource.FULL_UPLOAD || syncMode == SyncSource.INCREMENTAL_UPLOAD) {
            Log.error(TAG, "Server is trying to update items for a one way sync! " + "(syncMode: " + syncMode + ")");
            return SyncSource.ERROR_STATUS;
        }

        // Create a new calendar
        try {
            Calendar cal = new Calendar();
            cal.setVCalendar(item.getContent());
            String id = dm.add(cal);
            // Update the LUID for the mapping
            item.setKey(id);
            return SyncSource.SUCCESS_STATUS;
        } catch (Exception e) {
            Log.error(TAG, "Cannot save calendar", e);
            return SyncSource.ERROR_STATUS;
        }
    }

    /** Update a given SyncItem stored on the source backend */
    @Override
    public int updateItem(SyncItem item) {
        if (Log.isLoggable(Log.INFO)) {
            Log.info(TAG, "Updated item " + item.getKey() + " from server.");
        }

        if (syncMode == SyncSource.FULL_UPLOAD || syncMode == SyncSource.INCREMENTAL_UPLOAD) {
            Log.error(TAG, "Server is trying to update items for a one way sync! " + "(syncMode: " + syncMode + ")");
            return SyncSource.ERROR_STATUS;
        }

        // Create a new calendar
        try {
            // If the calendar does not exist already, then this is like a new
            Calendar c = new Calendar();
            c.setVCalendar(item.getContent());
            dm.update(item.getKey(), c);
            return SyncSource.SUCCESS_STATUS;
        } catch (Exception e) {
            Log.error(TAG, "Cannot update calendar ", e);
            return SyncSource.ERROR_STATUS;
        }
    }

    public void applyChanges(Vector items) throws SyncException {
        // Groups can be at the beginning or at the end of the list
        // (depending on how groups info is stored)
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG, "applyChanges" + items);
        }

        dm.beginTransaction();

        for(int i=0;i<items.size();++i) {
            SyncItem item = (SyncItem)items.elementAt(i);
            if (item.getState() == SyncItem.STATE_NEW) {
                try {
                    int itemStatus = addItem(item);
                    item.setSyncStatus(itemStatus);
                } catch (Exception e) {
                    Log.error(TAG, "Cannot add item", e);
                    item.setSyncStatus(SyncSource.ERROR_STATUS);
                }
            } else if (item.getState() == SyncItem.STATE_UPDATED) {
                try {
                    int itemStatus = updateItem(item);
                    item.setSyncStatus(itemStatus);
                } catch (Exception e) {
                    Log.error(TAG, "Cannot update item", e);
                    item.setSyncStatus(SyncSource.ERROR_STATUS);
                }
            } else {
                try {
                    int itemStatus = deleteItem(item.getKey());
                    item.setSyncStatus(itemStatus);
                } catch (Exception e) {
                    Log.error(TAG, "Cannot delete item", e);
                    item.setSyncStatus(SyncSource.ERROR_STATUS);
                }
            }
        }

        // Now commit all the changes
        try {
            Vector newContactKeys = dm.commit();

            // Update the keys for the newly created items and invoke the super.add/update/delete
            // methods now that everything is finalized
            if (newContactKeys != null) {
                int cKeysIdx = 0;
                for(int i=0;i<items.size();++i) {
                    SyncItem item = (SyncItem)items.elementAt(i);
                    if (item.getState() == SyncItem.STATE_NEW) {
                        String key;
                        if (cKeysIdx >= newContactKeys.size()) {
                            Log.error(TAG, "Items mismatch while setting contact keys");
                            throw new SyncException(SyncException.CLIENT_ERROR, "Items mismatch");
                        }
                        key = (String)newContactKeys.elementAt(cKeysIdx);
                        if (key.length() == 0) {
                            // This item was not inserted correctly
                            item.setSyncStatus(SyncSource.ERROR_STATUS);
                        }
                        cKeysIdx++;
                        item.setKey(key);

                        // This will take care of cleaning up the tracker and perform other common operations
                        super.addItem(item);
                    } else if (item.getState() == SyncItem.STATE_UPDATED) {
                        super.updateItem(item);
                    } else {
                        super.deleteItem(item.getKey());
                    }
                }
            }
        } catch (Exception e) {
            Log.error(TAG, "Cannot commit all changes", e);
            throw new SyncException(SyncException.CLIENT_ERROR, "Cannot commit changes");
        }
    }


    @Override
    protected SyncItem getItemContent(final SyncItem item) throws SyncException {
        try {
            // Load all the item content
            Calendar c = dm.load(item.getKey());
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            c.toVCalendar(os, true);
            SyncItem res = new SyncItem(item);
            res.setContent(os.toByteArray());
            return res;
        } catch (Exception e) {
            Log.error(TAG, "Cannot get calendar content for " + item.getKey(), e);
            throw new SyncException(SyncException.CLIENT_ERROR, "Cannot get calendar content");
        }
    }
}
