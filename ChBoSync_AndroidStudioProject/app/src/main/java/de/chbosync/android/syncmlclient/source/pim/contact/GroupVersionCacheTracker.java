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

import android.content.Context;
import android.provider.ContactsContract;

import com.funambol.storage.StringKeyValueStore;
import com.funambol.sync.SyncException;
import com.funambol.sync.SyncItem;


/**
 * <code>VersionCacheTracker</code> extends the <code>CacheTracker</code>
 * implementation and overloads the changes retrieving and the fingerprint
 * computing algorithms.
 *
 * The fingerprint used to retrieve changes is the contact version.
 *
 */
public class GroupVersionCacheTracker extends VersionCacheTracker {

    private final String LOG_TAG = "GroupVersionCacheTracker";

    /**
     * Creates a GroupVersionCacheTracker. The constructor detects changes so that
     * the method to get the changes can be used right away
     *
     * @param status is the key value store with stored data
     * @param context the application Context
     * @param uri is the uri of the table that this tracker tracks
     */
    public GroupVersionCacheTracker(StringKeyValueStore status, Context context) {
        super(status, context, ContactsContract.Groups.CONTENT_URI, null);
    }

    @Override
    protected SyncItem getItemContent(SyncItem item) throws SyncException {
        String key = item.getKey();
        // If the key is specified without the G discriminator, we need to add
        // it back so that the sync source understand the data type
        if (key.charAt(0) != 'G') {
            item = new SyncItem("G" + key);
        }
        return super.getItemContent(item);
    }

}

