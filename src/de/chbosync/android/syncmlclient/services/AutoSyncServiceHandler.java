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

package de.chbosync.android.syncmlclient.services;

import java.util.List;

import android.content.Context;
import android.content.Intent;

import com.funambol.client.source.AppSyncSource;


public class AutoSyncServiceHandler {

    public static final String SYNC_SERVICE = "de.chbosync.android.syncmlclient.services.AutoSyncService";

	private static final String TAG_LOG = "AutoSyncServiceHandler";

    private Context context;

    public AutoSyncServiceHandler(Context context) {
        this.context = context;
    }

    public void programScheduledSync() {
        Intent serviceIntent = new Intent();
        serviceIntent.setAction(SYNC_SERVICE);
        serviceIntent.putExtra(AutoSyncService.OPERATION, AutoSyncService.PROGRAM_SCHEDULED_SYNC);
        context.startService(serviceIntent);
    }
    
    public void cancelScheduledSync() {
        Intent serviceIntent = new Intent();
        serviceIntent.setAction(SYNC_SERVICE);
        serviceIntent.putExtra(AutoSyncService.OPERATION, AutoSyncService.CANCEL_SCHEDULED_SYNC);
        context.startService(serviceIntent);
    }

    public void programSyncRetry(int minutes, int retryCount) {
        Intent serviceIntent = new Intent();
        serviceIntent.setAction(SYNC_SERVICE);
        serviceIntent.putExtra(AutoSyncService.OPERATION, AutoSyncService.PROGRAM_SYNC_RETRY);
        serviceIntent.putExtra(AutoSyncService.DELAY, minutes);
        serviceIntent.putExtra(AutoSyncService.COUNT, retryCount);
        context.startService(serviceIntent);
    }
    
    public void cancelSyncRetry() {
        Intent serviceIntent = new Intent();
        serviceIntent.setAction(SYNC_SERVICE);
        serviceIntent.putExtra(AutoSyncService.OPERATION, AutoSyncService.CANCEL_SYNC_RETRY);
        context.startService(serviceIntent);
    }

    public void startSync(String syncMode, List<AppSyncSource> sources) {
        startSync(syncMode, sources, 0);
    }

    public void startSync(String syncMode, List<AppSyncSource> sources, int delay) {
        Intent serviceIntent = new Intent();
        serviceIntent.setAction(SYNC_SERVICE);
        serviceIntent.putExtra(AutoSyncService.OPERATION, AutoSyncService.START_SYNC);

        int sourcesId[] = new int[sources.size()];
        int i = 0;
        for(AppSyncSource s : sources) {
            sourcesId[i++] = s.getId();
        }

        serviceIntent.putExtra(AutoSyncService.SOURCES_ID, sourcesId);
        serviceIntent.putExtra(AutoSyncService.SYNC_MODE, syncMode);
        serviceIntent.putExtra(AutoSyncService.DELAY, delay);
        context.startService(serviceIntent);
    }

    public void startMonitoringUri(String uri, int sourceId) {
        Intent serviceIntent = new Intent();
        serviceIntent.setAction(SYNC_SERVICE);
        serviceIntent.putExtra(AutoSyncService.OPERATION, AutoSyncService.START_MONITORING_URI);
        serviceIntent.putExtra(AutoSyncService.URI, uri);
        serviceIntent.putExtra(AutoSyncService.SOURCE_ID, sourceId);
        context.startService(serviceIntent);
    }

}

