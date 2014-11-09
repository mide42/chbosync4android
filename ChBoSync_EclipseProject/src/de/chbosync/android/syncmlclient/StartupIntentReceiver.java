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

package de.chbosync.android.syncmlclient;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.funambol.util.Log;

import de.chbosync.android.syncmlclient.services.AutoSyncServiceHandler;


/**
 * BroadcastReceiver implementation for the background sync service. Triggers
 * the service intent at startup (installation time and boot time)
 */
public class StartupIntentReceiver extends BroadcastReceiver {
    private static final String TAG = "StartupIntentReceiver";
    
    /**
     * Defines what to do when the BroadcastReceiver is triggered.
     * @param context the application Context
     * @param intent the Intent to be launched to start the service - Not used
     * use a custom defined intent with a predefined service action
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Log.isLoggable(Log.INFO)) {
            Log.info(TAG, "onReceive");
        }
        Intent serviceIntent = new Intent();
        serviceIntent.setAction(AutoSyncServiceHandler.SYNC_SERVICE);
        context.startService(serviceIntent);
    }
}

