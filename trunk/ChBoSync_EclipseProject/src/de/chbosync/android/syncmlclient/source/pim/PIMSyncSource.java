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

package de.chbosync.android.syncmlclient.source.pim;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Environment;
import android.os.StatFs;

import com.funambol.client.configuration.Configuration;
import com.funambol.client.source.AppSyncSource;
import com.funambol.sync.ResumableSource;
import com.funambol.sync.SourceConfig;
import com.funambol.sync.SyncAnchor;
import com.funambol.sync.SyncException;
import com.funambol.sync.SyncItem;
import com.funambol.sync.SyncSource;
import com.funambol.sync.client.ChangesTracker;
import com.funambol.sync.client.TrackableSyncSource;
import com.funambol.util.Log;

import de.chbosync.android.syncmlclient.source.AbstractDataManager;


public abstract class PIMSyncSource<E> extends TrackableSyncSource implements ResumableSource {

	private static final String TAG = "PIMSyncSource";

    protected Context context;
    protected ContentResolver resolver;
    protected AbstractDataManager<E>  dm = null;
    protected Configuration configuration;
    protected AppSyncSource appSource;

    protected long totalMemory;
    // This is the percentage of the total memory before the sync is aborted.
    // When the available memory is below this threshold, then the sync is
    // aborted
    protected long lowSpaceThreshold = 0;

    /**
     * PIMSyncSource constructor: initialize source config.
     * 
     * @param config
     * @param tracker
     * @param context
     * @param configuration
     * @param appSource
     */
    public PIMSyncSource(SourceConfig config, ChangesTracker tracker, Context context,
                         Configuration configuration, AppSyncSource appSource, AbstractDataManager dm) {
        super(config, tracker);
        this.context  = context;
        this.configuration = configuration;
        this.appSource = appSource;
        this.resolver = context.getContentResolver();
        this.dm       = dm;
    }

    /**
     * Sets the low space threshold. When the available memory goes below this
     * threshold (percentage) then the sync is aborted. If this threshold is set
     * to 5 then the sync is aborted as soon as the available memory goes below
     * 5%. Setting this value to 0 disable the feature.
     */
    public void setLowSpaceThreshold(long threshold) {
        this.lowSpaceThreshold = threshold;
    }

    /** Delete a SyncItem stored on the related Items list */
    @Override
    protected int deleteItem(String key) {
        if (Log.isLoggable(Log.INFO)) {
            Log.info(TAG, "Delete from server for item " + key);
        }

        if (syncMode == SyncSource.FULL_UPLOAD || syncMode == SyncSource.INCREMENTAL_UPLOAD) {
            Log.error(TAG, "Server is trying to delete items for a one way sync! " + "(syncMode: " + syncMode + ")");
            return 500;
        }

        try {
            dm.delete(key);

            SyncItem tmpItem = new SyncItem(key);
            tmpItem.setState(SyncItem.STATE_DELETED);
            
            // Call super method
            super.deleteItem(key);

            return SyncSource.SUCCESS_STATUS;
        } catch (Exception e) {
            Log.error(TAG, "Cannot delete item", e);
            return SyncSource.ERROR_STATUS;
        }
    }

    @Override
    public void beginSync(int syncMode, boolean resume) throws SyncException {

        // Init the data manager account at each sync as it may change at any
        // time
        dm.initAccount();

        super.beginSync(syncMode, resume);

        // For any refresh we reset the anchors so that if this sync does not
        // terminate properly, the next sync will be a slow one
        if(syncMode == SyncSource.FULL_DOWNLOAD ||
           syncMode == SyncSource.FULL_UPLOAD)
        {
            SyncAnchor anchor = getConfig().getSyncAnchor();
            anchor.reset();
        }

        // Initialize the total memory on the device
        totalMemory = getTotalInternalMemorySize();
    }

    @Override
    public void endSync() throws SyncException {
        super.endSync();
        // Save the source configuration. We save the configuration here because
        // this piece of code is always executed on successfull sync, no matter
        // if they are triggered from the native app or our client.
        appSource.getConfig().saveSourceSyncConfig();
        appSource.getConfig().commit();
    }

    @Override
    public Enumeration getAllItemsKeys() throws SyncException {

        if (Log.isLoggable(Log.INFO)) {
            Log.info(TAG, "getAllItemsKeys");
        }

        try {
            Enumeration keys = dm.getAllKeys();
            return keys;
        } catch (IOException ioe) {
            Log.error(TAG, "Cannot get all keys", ioe);
            throw new SyncException(SyncException.CLIENT_ERROR, "Cannot get all keys");
        }
    }

    @Override
    public int getAllItemsCount() throws SyncException {
        try {
            return dm.getAllCount();
        } catch (IOException ioe) {
            Log.error(TAG, "Cannot get all count", ioe);
            throw new SyncException(SyncException.CLIENT_ERROR, "Cannot get all keys count");
        }
    }

    @Override
    protected void deleteAllItems() throws SyncException {
        super.deleteAllItems();
        try {
            dm.deleteAll();
        } catch (IOException ioe) {
            throw new SyncException(SyncException.STORAGE_ERROR, ioe.getMessage());
        }
    }

    /**
     * This method has two purposes:
     *
     * 1) check if there is enough space to add the item (if the source is
     * configured)
     *
     * 2) allow test case recording
     */
    @Override
    protected int addItem(SyncItem item) throws SyncException {
        checkAvailableSpace();

        int res;
        // Depending on the actual sync source, an item key can be set or not.
        // In particular sources that apply all changes in one shot do not have the key set at this point.
        // If this is the case we avoid invoking the super addItem which requires the key to be set.
        if (item.getKey() != null) {
            res = super.addItem(item);
        } else {
            res = SyncSource.SUCCESS_STATUS;
        }

        return res;
    }

    @Override
    protected int updateItem(SyncItem item) throws SyncException {
        checkAvailableSpace();
        return super.updateItem(item);
    }

    /**
     * Indicates if the source is ready to resume.
     */
    public boolean readyToResume() {
        return tracker.supportsResume();
    }

    public boolean exists(String key) throws SyncException {

        try {
            return dm.exists(key);
        } catch (Exception e) {
            Log.error(TAG, "Cannot check item existence", e);
            throw new SyncException(SyncException.CLIENT_ERROR, e.toString());
        }
    }

    public boolean hasChangedSinceLastSync(String key, long ts) {
        // Check if the given item has changed since the given timestamp
        return true;
    }

    /**
     * No support for single item resume.
     */
    public long getPartiallyReceivedItemSize(String key) {
        return -1;
    }

    public String getLuid(SyncItem item) {
        return null;
    }

    protected void checkAvailableSpace() throws SyncException {
        if (lowSpaceThreshold > 0) {
            // check if we have enough space for new items
            long availableSpace = getAvailableInternalMemorySize();
            if (availableSpace < ((lowSpaceThreshold * totalMemory) / 100)) {
                // We reached the low limit, abort the sync with a proper error
                // message
                lowSpaceReached();
            }
        }
    }

    protected void lowSpaceReached() throws SyncException {
        throw new SyncException(SyncException.LOCAL_DEVICE_FULL, "Insufficient space available on device");
    }

    protected long getAvailableInternalMemorySize() {  
        File path = Environment.getDataDirectory();  
        StatFs stat = new StatFs(path.getPath());  
        long blockSize = stat.getBlockSize();  
        long availableBlocks = stat.getAvailableBlocks();  
        return availableBlocks * blockSize;  
    }

    protected long getTotalInternalMemorySize() {  
        File path = Environment.getDataDirectory();  
        StatFs stat = new StatFs(path.getPath());  
        long blockSize = stat.getBlockSize();  
        long totalBlocks = stat.getBlockCount();  
        return totalBlocks * blockSize;  
    }
}
