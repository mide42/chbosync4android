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

package de.chbosync.android.syncmlclient.activities.settings;

import java.util.Vector;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.funambol.client.configuration.Configuration;
import com.funambol.client.controller.SyncSettingsScreenController;
import com.funambol.client.ui.SettingsUIItem;
import com.funambol.client.ui.SettingsUISyncSource;
import com.funambol.client.ui.SyncSettingsScreen;

import de.chbosync.android.syncmlclient.R;


/**
 * Represents the Android Sync Settings tab.
 */
public class AndroidSyncSettingsTab extends AndroidSettingsTab
        implements SyncSettingsScreen, OnItemSelectedListener {

    private static final String TAB_TAG = "sync_settings";
    
    private static final int LAYOUT_ID = R.id.sync_settings_tab;

    private SyncModeSettingView      syncModeView;
    private SyncIntervallSettingView syncIntervalView;
    private C2SPushSettingView       c2sPushView;

    private boolean syncIntervalViewShown = false;
    
    private Vector<AndroidSettingsUISyncSource> sourceItems =
            new Vector<AndroidSettingsUISyncSource>();

    private SyncSettingsScreenController screenController;

    private LinearLayout linearLayout;
    
    public AndroidSyncSettingsTab(Activity a, Bundle state) {
        super(a, state);
        screenController = new SyncSettingsScreenController(controller, this);
        initialize();
    }

    private void initialize() {
    	
        // Prepare container layout
        linearLayout = new LinearLayout(activity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT,
                                                         LayoutParams.WRAP_CONTENT));
        
        View.inflate(activity, R.layout.powered_by_textview, linearLayout);


        // Add the global sync mode setting
        if(syncModeView != null) {
            linearLayout.addView(syncModeView);
        }

        if(syncIntervalView != null && !syncIntervalViewShown){
            linearLayout.addView(syncIntervalView);
            syncIntervalViewShown = true;
        }

        if(c2sPushView != null) {
            if(syncModeView != null) {
                addDivider(linearLayout);
            }
            linearLayout.addView(c2sPushView);
        }

        // Add all the source settings
        boolean first = true;
        for(AndroidSettingsUISyncSource item : sourceItems) {
            if(item == null) {
                continue;
            }
            if(!first) {
                addDivider(linearLayout);
            } else {
                first = false;
                if(syncModeView != null) {
                    addBigDivider(linearLayout);
                }
            }
            linearLayout.addView(item);
        }

        this.addView(linearLayout);
    }

    /**
     * Add a divider View to a ViewGroup object
     * @param vg
     */
    private void addBigDivider(ViewGroup vg) {
        ImageView divider = new ImageView(activity);
        divider.setBackgroundResource(R.drawable.divider_big_shape);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(
                LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
        vg.addView(divider, dl);
    }

    //---------------------------------------- AndroidSettingsTab implementation
    
    public String getTag() {
        return TAB_TAG;
    }

    public int getLayoutId() {
        return LAYOUT_ID;
    }

    public void saveSettings(SaveSettingsCallback callback) {
        screenController.saveSettings();
        callback.saveSettingsResult(true);
    }

    public boolean hasChanges() {
        return screenController.hasChanges();
    }

    public Drawable getIndicatorIcon() {
        return getResources().getDrawable(R.drawable.ic_sync_tab);
    }

    public String getIndicatorLabel() {
        return localization.getLanguage("settings_sync_tab");
    }

    //---------------------------------------- SyncSettingsScreen implementation

    public SettingsUISyncSource createSettingsUISyncSource() {
        return new AndroidSettingsUISyncSource(activity);
    }

    public void setSettingsUISyncSource(SettingsUISyncSource item, int index) {
        sourceItems.setElementAt((AndroidSettingsUISyncSource)item, index);
    }

    public void setSettingsUISyncSourceCount(int count) {
        sourceItems.setSize(count);
    }
    
    public SettingsUIItem addSyncModeSetting() {
        // Setup SyncMode setting View
        int[] modes = customization.getAvailableSyncModes();
        if(modes.length > 1) {
            syncModeView = new SyncModeSettingView(activity, modes);
            syncModeView.setSelectedItemListener(this);
            syncModeView.loadSettings(configuration);
        }
        return syncModeView;
    }

    public SettingsUIItem addSyncIntervalSetting() {
        // Setup SyncInterval setting View
        int[] intervals = customization.getPollingPimIntervalChoices();
        syncIntervalView = new SyncIntervallSettingView(activity, intervals);
        syncIntervalView.loadSettings(configuration);
        return syncIntervalView;
    }

    public SettingsUIItem addC2SPushSetting() {
        c2sPushView = new C2SPushSettingView(activity);

        c2sPushView.setChecked(configuration.isC2SPushEnabled());

        // Get global auto-sync setting
        boolean autoSyncEnabled = ContentResolver.getMasterSyncAutomatically();

        // Get the background data setting
        ConnectivityManager cm = (ConnectivityManager)getContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean backgroundDataEnabled = cm.getBackgroundDataSetting();
        
        c2sPushView.setEnabled(autoSyncEnabled && backgroundDataEnabled);

        return c2sPushView;
    }

    public void removeAllItems() {
        syncModeView     = null;
        syncIntervalView = null;
        c2sPushView      = null;
        sourceItems.clear();
    }

    public Object getUiScreen() {
        return activity;
    }

    public void cancelSettings() {
    }

    public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
        if(syncModeView.getSyncMode() == Configuration.SYNC_MODE_SCHEDULED) {
            if(syncIntervalView != null && !syncIntervalView.isShown() &&
                    !syncIntervalViewShown) {
                // This is the position of the interval
                linearLayout.addView(syncIntervalView, 1);
                syncIntervalViewShown = true;
            }
        } else {
            if(syncIntervalView!= null && syncIntervalView.isShown() &&
                    syncIntervalViewShown) {
                linearLayout.removeView(syncIntervalView);
                syncIntervalViewShown = false;
            }
        }
    }

    public void onNothingSelected(AdapterView<?> arg0) {
    }
}
