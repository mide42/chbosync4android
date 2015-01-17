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

package de.chbosync.android.syncmlclient.activities;

import java.util.Vector;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;

import com.funambol.client.controller.DevSettingsScreenController;
import com.funambol.client.localization.Localization;
import com.funambol.client.ui.DevSettingsScreen;
import com.funambol.client.ui.DevSettingsUISyncSource;
import com.funambol.util.Log;

import de.chbosync.android.syncmlclient.R;
import de.chbosync.android.syncmlclient.AndroidCustomization;
import de.chbosync.android.syncmlclient.activities.settings.AndroidDevSettingsUISyncSource;
import de.chbosync.android.syncmlclient.controller.AndroidController;
import de.chbosync.android.syncmlclient.controller.AndroidHomeScreenController;


/**
 * This is the container Activity for all the settings (Sync, Advanced)
 */
public class AndroidDevSettingsScreen extends Activity implements DevSettingsScreen {

    private static final String TAG = "AndroidDevSettingsScreen";

    private Localization localization;
    private AndroidCustomization customization;

    private AndroidDisplayManager displayManager;
    private LinearLayout mainLayout;
    private LinearLayout sourcesLayout;
    private LinearLayout miscLayout;
    private Vector<AndroidDevSettingsUISyncSource> sourceItems = new Vector<AndroidDevSettingsUISyncSource>();
    private DevSettingsScreenController devSettingsScreenController;

    /**
     * Called with the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AndroidController gc = AndroidController.getInstance();
        this.localization = gc.getLocalization();
        this.customization = (AndroidCustomization)gc.getCustomization();
        this.displayManager = (AndroidDisplayManager)gc.getDisplayManager();
        
        setContentView(R.layout.dev_settings);

        mainLayout = (LinearLayout)findViewById(R.id.dev_settings_main_layout);
        miscLayout = (LinearLayout)findViewById(R.id.dev_settings_misc_layout);
        sourcesLayout = (LinearLayout)findViewById(R.id.dev_settings_sources_layout);

        // Init button listeners
        Button saveButton = (Button)findViewById(R.id.save_button);
        saveButton.setOnClickListener(new SaveListener());

        Button cancelButton = (Button)findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(new CancelListener());

        devSettingsScreenController = gc.getDevSettingsScreenController();
        devSettingsScreenController.setDevSettingsScreen(this);
        devSettingsScreenController.updateListOfSources();
    }

    public void setDevSettingsUISyncSource(DevSettingsUISyncSource item, int index) {
        sourceItems.setElementAt((AndroidDevSettingsUISyncSource)item, index);
    }

    public void setDevSettingsUISyncSourceCount(int count) {
        sourceItems.setSize(count);
    }

    public void setWbxml(boolean value) {
    }

    public boolean getWbxml() {
        return true;
    }

    public void setMaxMsgSize(int maxMsgSize) {
    }

    public int getMaxMsgSize() {
        return 0;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == KeyEvent.KEYCODE_BACK && hasChanges()) {
            // Ask to the user if he want to save settings since he did changes
            displayManager.askYesNoQuestion(this, localization.getLanguage(
                    "settings_changed_alert"),
                    new Runnable() {
                        public void run() {
                            save(true);
                        }
                    },
                    new Runnable() {
                        public void run() {
                            // Shall we close the settings screen?
                        }
                    }, 0);
            return false;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    public void layout() {
        // Add misc settings

        // Add all sources
        for(AndroidDevSettingsUISyncSource source : sourceItems) {
            sourcesLayout.addView(source);
        }
    }

    public void removeAllItems() {
        miscLayout.removeAllViews();
        sourcesLayout.removeAllViews();
    }


    /**
     * Save settings for all the tabs, and eventually close the screen.
     * 
     * @param close true if the settings screen must be closed after saving
     * @param callback Used to be notified when tab settings are saved
     */
    private void save(boolean close) {

        if (Log.isLoggable(Log.INFO)) {
            Log.info(TAG, "Saving settings");
        }

        AndroidController gc = AndroidController.getInstance();
        AndroidHomeScreenController ahsc = (AndroidHomeScreenController)gc
                .getHomeScreenController();
        if (ahsc.isSynchronizing() || ahsc.isFirstSyncDialogDisplayed()){
            displayManager.showMessage(this, localization.getLanguage(
                    "sync_in_progress_dialog_save"));
            if (Log.isLoggable(Log.INFO)) {
                Log.info(TAG, "Cannot save settings. Synchronization in progress...");
            }
            return;
        }

        devSettingsScreenController.saveSettings();

        if (close) {
            finish();
        }
    }

    /**
     * @return true if there are changes in settings
     */
    public boolean hasChanges() {
        // TODO: has Changes?
        return false;
    }

    public Object getUiScreen() {
        return this;
    }

    private void cancel() {
        finish();
    }

    /**
     * A call-back for when the user presses the save button.
     */
    private class SaveListener implements OnClickListener {
        public void onClick(View v) {
            save(true);
        }
    }

    /**
     * A call-back for when the user presses the cancel button.
     */
    private class CancelListener implements OnClickListener {
        public void onClick(View v) {
            cancel();
        }
    }
}
