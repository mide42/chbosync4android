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

package de.chbosync.android.syncmlclient.activities.settings;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import android.accounts.Account;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.net.ConnectivityManager;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.funambol.client.configuration.Configuration;
import com.funambol.client.customization.Customization;
import com.funambol.client.localization.Localization;
import com.funambol.client.ui.SettingsUIItem;
import com.funambol.util.Log;

import de.chbosync.android.syncmlclient.R;
import de.chbosync.android.syncmlclient.AndroidAccountManager;
import de.chbosync.android.syncmlclient.AndroidAppSyncSource;
import de.chbosync.android.syncmlclient.controller.AndroidController;


/**
 * Implements a LinearLayout to display the sync mode setting (manual, push or
 * scheduled) in Android.
 */
public class SyncModeSettingView extends LinearLayout implements SettingsUIItem {

    private static final String TAG_LOG = "SyncModeSettingView";

    private static final int TOP_PADDING = 12;
    private static final int BOTTOM_PADDING = 12;
    private static final int LEFT_PADDING = 12;
    private static final int RIGHT_PADDING = 12;

    private static final int TITLE_LEFT_PADDING = 0;
    private static final int TITLE_BOTTOM_PADDING = 6;

    private Spinner syncModeSpinner;

    private final Vector<String> syncModeStrings = new Vector<String>();

    private int[] syncModes;
    private int originalSyncMode;

    private Hashtable<String, Integer> syncModesReference = new Hashtable<String, Integer>();

    public SyncModeSettingView(Activity activity, int[] syncModes) {

        super(activity);

        AndroidController ac = AndroidController.getInstance();
        Localization loc = ac.getLocalization();
        
        this.syncModes = syncModes;

        // Init the sourceSyncModes
        syncModeStrings.add(loc.getLanguage("sync_mode_manual"));
        syncModeStrings.add(loc.getLanguage("sync_mode_scheduled"));
        syncModeStrings.add(loc.getLanguage("sync_mode_automatically"));
        syncModesReference.put(syncModeStrings.get(0), Configuration.SYNC_MODE_MANUAL);
        syncModesReference.put(syncModeStrings.get(1), Configuration.SYNC_MODE_SCHEDULED);
        syncModesReference.put(syncModeStrings.get(2), Configuration.SYNC_MODE_PUSH);

        boolean isGlobalAutoSyncEnabled = isGlobalAutoSyncEnabled();
        Customization customization = ac.getCustomization();
        boolean isAutoSyncAvailable = isGlobalAutoSyncEnabled && 
                                      customization.isS2CSmsPushEnabled();

        TextView titleTextView  = new TextView(activity, null, R.style.sync_title);
        titleTextView.setPadding(adaptSizeToDensity(TITLE_LEFT_PADDING), 0, 0,
                                 adaptSizeToDensity(TITLE_BOTTOM_PADDING));
        titleTextView.setTextAppearance(activity, R.style.funambol_title);
        titleTextView.setText(loc.getLanguage("conf_sync_mode_title"));
 
        syncModeSpinner = new Spinner(activity);
        syncModeSpinner.setPrompt(loc.getLanguage("conf_sync_mode_title"));
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(activity,
                android.R.layout.simple_spinner_item);

        // Add the sync modes to the spinner adapter
        for(int i=0; i<syncModes.length; i++) {
            if (syncModes[i] != Configuration.SYNC_MODE_PUSH ||
                isAutoSyncAvailable)
            {
                adapter.add(getSyncModeString(syncModes[i]));
            }
        }

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        syncModeSpinner.setAdapter(adapter);

        syncModeSpinner.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT,
                                                          LayoutParams.WRAP_CONTENT);
        this.setLayoutParams(lp);
        this.setPadding(adaptSizeToDensity(LEFT_PADDING),
                        adaptSizeToDensity(TOP_PADDING),
                        adaptSizeToDensity(RIGHT_PADDING),
                        adaptSizeToDensity(BOTTOM_PADDING));
        this.setOrientation(LinearLayout.VERTICAL);

        this.addView(titleTextView);
        this.addView(syncModeSpinner);

        if (!isGlobalAutoSyncEnabled && customization.isS2CSmsPushEnabled()) {
            TextView autoSyncHelpView = new TextView(activity, null);
            autoSyncHelpView.setText(loc.getLanguage("conf_automatically_help"));
            this.addView(autoSyncHelpView);
        }
    }

    public void setSyncMode(int mode) {

        ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>)syncModeSpinner.getAdapter();
        String syncModeLabel = getSyncModeString(mode);

        boolean done = false;
        for(int i=0;i<adapter.getCount();++i) {
            String currentLabel = adapter.getItem(i).toString();
            if (currentLabel.equals(syncModeLabel)) {
                originalSyncMode = mode;
                syncModeSpinner.setSelection(i);
                done = true;
                break;
            }
        }

        if (!done) {
            if (Log.isLoggable(Log.DEBUG)) {
                Log.debug(TAG_LOG, "Reverting to default sync mode");
            }
            syncModeSpinner.setSelection(0);
        }
    }

    public void setSelectedItemListener(OnItemSelectedListener listener){
        syncModeSpinner.setOnItemSelectedListener(listener);
    }

    public int getSyncMode() {
        String selectedMode = (String)syncModeSpinner.getSelectedItem();
        return syncModesReference.get(selectedMode);
    }

    public boolean hasChanges() {
        return originalSyncMode != getSyncMode();
    }

    public void loadSettings(Configuration configuration) {
        setSyncMode(configuration.getSyncMode());
    }
    
    public void saveSettings(Configuration conf) {
        int syncMode = getSyncMode();

        conf.setSyncMode(syncMode);

        // If the automatic sync mode is available we shall update the native
        // config here
        AndroidController ac = AndroidController.getInstance();
        Customization customization = ac.getCustomization();
        if (customization.isS2CSmsPushEnabled()) {
            boolean enableAuto = syncMode == Configuration.SYNC_MODE_PUSH;
            Account account = AndroidAccountManager.getNativeAccount(getContext());

            // Save the auto sync setting for each source
            Enumeration sources = AndroidController.getInstance()
                .getAppSyncSourceManager().getWorkingSources();
            while(sources.hasMoreElements()) {
                AndroidAppSyncSource source = (AndroidAppSyncSource)sources.nextElement();
                String authority = source.getAuthority();
                if(authority != null) {
                    ContentResolver.setSyncAutomatically(account, authority, enableAuto);
                }
            }
        }
    }

    private String getSyncModeString(int mode) {
        for(int i=0; i<syncModeStrings.size(); i++) {
            if(syncModesReference.get(syncModeStrings.get(i)) == mode) {
                return syncModeStrings.get(i);
            }
        }
        return null;
    }

    private int adaptSizeToDensity(int size) {
        return (int)(size*getContext().getResources().getDisplayMetrics().density);
    }

    private boolean isGlobalAutoSyncEnabled() {

        // Get global auto-sync setting
        boolean autoSyncEnabled = ContentResolver.getMasterSyncAutomatically();

        // Get the background data setting
        ConnectivityManager cm = (ConnectivityManager)getContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean backgroundDataEnabled = cm.getBackgroundDataSetting();

        return autoSyncEnabled && backgroundDataEnabled;
    }
 
}

