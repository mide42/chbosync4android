/*
 * Funambol is a mobile platform developed by Funambol, Inc.
 * Copyright (C) 2011 Funambol, Inc.
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

import java.io.ByteArrayOutputStream;

import android.content.Context;

import com.funambol.client.configuration.Configuration;
import com.funambol.client.source.AppSyncSource;
import com.funambol.sync.SourceConfig;
import com.funambol.sync.SyncException;
import com.funambol.sync.SyncItem;
import com.funambol.sync.SyncSource;
import com.funambol.sync.client.ChangesTracker;
import com.funambol.syncml.protocol.SyncML;
import com.funambol.util.Log;

import de.chbosync.android.syncmlclient.source.AbstractDataManager;
import de.chbosync.android.syncmlclient.source.pim.PIMSyncSource;


public class NoteSyncSource extends PIMSyncSource<Note> {
	
	/** Tag for writing log message. */
    private static final String TAG4LOGGING = "NoteSyncSource";

    
    /**
     * CalendarSyncSource constructor: initialize source config.
     */
    public NoteSyncSource(SourceConfig config, ChangesTracker tracker, Context context,
                          Configuration configuration, AppSyncSource appSource, @SuppressWarnings("rawtypes") AbstractDataManager dm) {
    	
        super(config, tracker, context, configuration, appSource, dm);
    }
    

    /** 
     * Logs the new item from the server. 
     * */
    @Override
    public int addItem(SyncItem item) {
        if (Log.isLoggable(Log.INFO)) {
            Log.info(TAG4LOGGING, "New item " + item.getKey() + " from server.");
        }
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG4LOGGING, new String(item.getContent()));
        }

        if (syncMode == SyncML.ALERT_CODE_REFRESH_FROM_CLIENT || syncMode == SyncML.ALERT_CODE_ONE_WAY_FROM_CLIENT) {
            Log.error(TAG4LOGGING, "Server is trying to update items for a one way sync! " + "(syncMode: " + syncMode + ")");
            return SyncSource.ERROR_STATUS;
        }

        // Create a new note
        try {
            Note note = new Note();
            note.setPlainText(item.getContent());
            String id = dm.add(note);
            // Update the LUID for the mapping
            item.setKey(id);

            // Make sure the tracker is updated
            super.addItem(item);

            return SyncSource.SUCCESS_STATUS;
            
        } catch (Exception e) {
            Log.error(TAG4LOGGING, "Cannot save note", e);
            return SyncSource.ERROR_STATUS;
        }
    }

    
    /** 
     * Update a given SyncItem stored on the source backend. 
     */
    @Override
    public int updateItem(SyncItem item) {
        if (Log.isLoggable(Log.INFO)) {
            Log.info(TAG4LOGGING, "Updated item " + item.getKey() + " from server.");
        }

        if (syncMode == SyncML.ALERT_CODE_REFRESH_FROM_CLIENT || syncMode == SyncML.ALERT_CODE_ONE_WAY_FROM_CLIENT) {
            Log.error(TAG4LOGGING, "Server is trying to update items for a one way sync! " + "(syncMode: " + syncMode + ")");
            return SyncSource.ERROR_STATUS;
        }

        // Create a new note
        try {
        	
            Note note = new Note();
            note.setPlainText(item.getContent());
            dm.update(item.getKey(), note);

            super.updateItem(item);
            return SyncSource.SUCCESS_STATUS;
            
        } catch (Exception e) {
            Log.error(TAG4LOGGING, "Cannot update note ", e);
            return SyncSource.ERROR_STATUS;
        }
    }

    
    @Override
    protected SyncItem getItemContent(final SyncItem item) throws SyncException {
        try {
            // Load all the item content
            Note note = dm.load(item.getKey());
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            note.toPlainText(os, true);
            SyncItem res = new SyncItem(item);
            res.setContent(os.toByteArray());
            return res;
        } catch (Exception e) {
            Log.error(TAG4LOGGING, "Cannot get note content for " + item.getKey(), e);
            throw new SyncException(SyncException.CLIENT_ERROR, "Cannot get note content");
        }
    }
}
