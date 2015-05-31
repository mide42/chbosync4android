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

package de.chbosync.android.syncmlclient.controller;

import android.content.Context;

import com.funambol.client.configuration.Configuration;
import com.funambol.client.controller.Controller;
import com.funambol.client.controller.SyncModeHandler;

import de.chbosync.android.syncmlclient.services.AutoSyncServiceHandler;


/**
 * This is the class which handles the sync mode set by the user.
 */
public class AndroidSyncModeHandler extends SyncModeHandler {

    private static final String TAG = "AndroidSyncModeHandler";
    
    private AutoSyncServiceHandler autoSyncHandler;

    public AndroidSyncModeHandler(Context context, Configuration configuration) {
        super(configuration);
        this.autoSyncHandler = new AutoSyncServiceHandler(context);
    }

    /**
     * Handles the given sync mode
     * @param mode
     * @param controller
     */
    @Override
    public void setSyncMode(final Controller controller) {
        int mode = configuration.getSyncMode();
        // We must run the service binding in a separate thread because this
        // code can be invoked by intent receivers and Android throws an
        // exception if an intent receiver binds to a service.
        if(mode == Configuration.SYNC_MODE_MANUAL ||
           mode == Configuration.SYNC_MODE_PUSH) {
            (new Thread() {
                @Override
                public void run() {
                    cancelScheduledSync();
                }
            }).start();
        } else if(mode == Configuration.SYNC_MODE_SCHEDULED) {
            (new Thread() {
                @Override
                public void run() {
                    programScheduledSync(controller);
                }
            }).start();
        }
    }

    protected void programScheduledSync(Controller controller) {
        autoSyncHandler.programScheduledSync();
    }
    
    public void cancelScheduledSync() {
        autoSyncHandler.cancelScheduledSync();
    }

    public void programSyncRetry(int minutes, int retryCount) {
        autoSyncHandler.programSyncRetry(minutes, retryCount);
    }
    
    public void cancelSyncRetry() {
        autoSyncHandler.cancelSyncRetry();
    }

}
