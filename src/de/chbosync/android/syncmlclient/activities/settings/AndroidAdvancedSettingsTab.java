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

import java.util.Hashtable;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;

import com.funambol.client.controller.Controller;
import com.funambol.client.ui.AdvancedSettingsScreen;
import com.funambol.client.ui.Screen;
import com.funambol.util.Log;

import de.chbosync.android.syncmlclient.AndroidConfiguration;
import de.chbosync.android.syncmlclient.R;
import de.chbosync.android.syncmlclient.activities.AndroidDisplayManager;
import de.chbosync.android.syncmlclient.activities.AndroidSettingsScreen;
import de.chbosync.android.syncmlclient.controller.AndroidAdvancedSettingsScreenController;
import de.chbosync.android.syncmlclient.controller.AndroidController;
import de.chbosync.android.syncmlclient.source.pim.note.OINoteManager;
import de.chbosync.android.syncmlclient.source.pim.note.OINotepadInstallationHelper;


/**
 * Realize the Advanced Settings screen tab into the android sync client. The
 * command items to send and view log and perform reset actions are represented
 * by buttons in this realization. Refer to the related controllers and screen
 * interfaces for further informations.
 */
public class AndroidAdvancedSettingsTab extends AndroidSettingsTab
                                        implements AdvancedSettingsScreen {

    private static final String TAG_LOG = "AndroidAdvancedSettingsTag";

    /** The string that references this class into the log content*/
    private static final String TAB_TAG = "advanced_settings";

    private static final int LAYOUT_ID = R.id.advanced_settings_tab;

    // Log section elements
    private Spinner spin;
    private Button  viewLogBtn;
    private Button  sendLogBtn;
    private LinearLayout logSection;
    private LinearLayout logButtonsRaw;

    // Reset section elements
    private Button resetBtn;
    private LinearLayout resetSection;

    // Import section elements
    private Button importBtn;
    private LinearLayout importSection;

    // Source remote uris elements
    private Button devSettingsBtn;
    private LinearLayout devSettingsSection;

    // Bandwidth saver elements
    private LinearLayout bandwidthSaverSection;
    private TwoLinesCheckBox saveBandwidthCheckBox;
    
    // Added for ChBoSync: Elements concerning "OI Notepad"
    private Button installOINotepadButton;
    private TwoLinesCheckBox showNoteDummySyncButton;
    private LinearLayout oiNotepadSection;
    private boolean originalShowNoteDummySyncButton;
    
    private Hashtable<String, Integer> logLevelReference = new Hashtable<String, Integer>();
    private LinearLayout settingsContainer;

    private int originalLogLevel;
    private boolean originalBandwidthStatus;
    //private boolean originalRemoteUriStatus;

    private AndroidDisplayManager dm;

    private AndroidAdvancedSettingsScreenController screenController;

    /**
     * Default constructor.
     * @param activity the Activity which contains this View
     * @param state
     */
    public AndroidAdvancedSettingsTab(Activity activity, Bundle state) {
        super(activity, state);

        AndroidController cont = AndroidController.getInstance();

        this.dm = (AndroidDisplayManager)cont.getDisplayManager();

        screenController = new AndroidAdvancedSettingsScreenController(cont, this);

        initScreenElements();
        screenController.initialize();
    }

    /**
     * get the tag of this class
     * @return String the TAG that represents this class' name
     */
    public String getTag() {
        return TAB_TAG;
    }

    /**
     * Accessor method to retrieve the layout id for this class in order it to
     * be referenced from external classes
     * @return int the layout id as an int value
     */
    public int getLayoutId() {
        return LAYOUT_ID;
    }

    /**
     * Get the icon related to the AdvancedSettingsScreenTab that is visible
     * on the edge of the tab over the tab title
     * @return Drawable the indicator icon related to this tab
     */
    public Drawable getIndicatorIcon() {
        return getResources().getDrawable(R.drawable.ic_advanced_tab);
    }

    /**
     * Get the title related to the AdvancedSettingsScreenTab that is visible
     * under the tab icon.
     * @return Stirng the title related to this tab
     */
    public String getIndicatorLabel() {
        return localization.getLanguage("settings_advanced_tab");
    }

    /**
     * Save the values contained into this view using the dedicated controller
     */
    public void saveSettings(SaveSettingsCallback callback) {
        //FIX-ME - return true if and only if the save action is successful
        screenController.checkAndSave();
        
        originalLogLevel        = configuration.getLogLevel();
        originalBandwidthStatus = configuration.getBandwidthSaverActivated();
        
        if (configuration instanceof AndroidConfiguration) { // added for ChBoSync
        	AndroidConfiguration ac = (AndroidConfiguration)configuration;
        	originalShowNoteDummySyncButton = ac.getShowDummyButtonForNotesSyncing();
        }
        
        
        callback.saveSettingsResult(true);
    }

    /**
     * Method returns <tt>true</tt>, when at least one of the following three preferences was changed:
     * loglevel, bandwith saver, dummy button for notes syncing.
     */
    public boolean hasChanges() {
        boolean hasChanges = false;

        if( (logSection != null) && (originalLogLevel != getViewLogLevel()) ) {
            hasChanges = true;
        }
        
        if( (bandwidthSaverSection != null) && (originalBandwidthStatus != getBandwidthSaver()) ) {
            hasChanges = true;
        }

        // the next one was added for ChBoSync
        if ( (showNoteDummySyncButton != null) && (originalShowNoteDummySyncButton != getShowDummyButtonForNotesSyncing()) ){
        	hasChanges = true;
        }
        
        return hasChanges;
    }

    
    /**
     * Same as method <tt>hasChanges</tt> in this class, but just for one preference
     * (nedeed because based on a change of this preference a refresh of the buttons
     * to be shown on the homescreen might have to be triggered).
     * 
     * Added for ChBoSync
     */
    public boolean hasShowNotesDummySyncButtonChanges() {
        if ( (showNoteDummySyncButton != null) && 
        	 (originalShowNoteDummySyncButton != getShowDummyButtonForNotesSyncing()) )
        	
        	return true;
        else
    	    return false;
    }
    
    public void enableResetCommand(boolean enable) {
        resetBtn.setEnabled(enable);
    }

    public void enableSendLogCommand(boolean enable) {
        sendLogBtn.setEnabled(enable);
    }

    public void hideLogsSection() {
        settingsContainer.removeView(logSection);
        logSection = null;
    }

    public void hideDevSettingsSection() {
        settingsContainer.removeView(devSettingsSection);
        devSettingsSection = null;
    }

    public void hideImportContactsSection() {
        settingsContainer.removeView(importSection);
        importSection = null;
    }

    public void hideSendLogCommand() {
        logButtonsRaw.removeView(sendLogBtn);
    }

    public void hideViewLogCommand() {
        //removeView(viewLogBtn);
    }

    /**
     * The implementation returns the activity related to this view.
     * @return Object the Activity passed to this view as a constructor
     * parameter
     */
    public Object getUiScreen() {
        return activity;
    }

    public void setViewLogLevel(int logLevel) {
        originalLogLevel = logLevel;
        spin.setSelection(logLevel + 1);
    }

    public int getViewLogLevel() {
        String item = (String) spin.getSelectedItem();
        return logLevelReference.get(item).intValue();
    }

    public void hideBandwidthSaverSection() {
        settingsContainer.removeView(bandwidthSaverSection);
        bandwidthSaverSection = null;
    }

    public void hideResetSection(){
        settingsContainer.removeView(resetSection);
        resetSection = null;
    }

    public void setBandwidthSaver(boolean enable){
        originalBandwidthStatus = enable;
        saveBandwidthCheckBox.setChecked(enable);
    }

    public boolean getBandwidthSaver(){
        return saveBandwidthCheckBox.isChecked();
    }
    
    /**
     * Setter for state of <i>TwoLinesCheckBox</i> for preference "showDummyButtonForNotesSyncing".
     * Added for ChBoSync
     */
    public void setShowDummyButtonForNotesSyncing(boolean enabled) {
    	originalShowNoteDummySyncButton = enabled;
        showNoteDummySyncButton.setChecked(enabled);
    }

    /**
     * Getter for current state of <i>TwoLinesCheckBox</i> for preference "showDummyButtonForNotesSyncing". 
     * Added for ChBoSync
     * 
     */
    public boolean getShowDummyButtonForNotesSyncing() {
    	return showNoteDummySyncButton.isChecked();
    }

    private void initScreenElements() {

        logLevelReference.put( localization.getLanguage("advanced_settings_log_level_none" ), Log.DISABLED);
        logLevelReference.put( localization.getLanguage("advanced_settings_log_level_error"), Log.ERROR   );
        logLevelReference.put( localization.getLanguage("advanced_settings_log_level_info" ), Log.INFO    );
        logLevelReference.put( localization.getLanguage("advanced_settings_log_level_debug"), Log.DEBUG   );
        logLevelReference.put( localization.getLanguage("advanced_settings_log_level_trace"), Log.TRACE   );

        saveBandwidthCheckBox = new TwoLinesCheckBox(activity);
        saveBandwidthCheckBox.setText1(localization.getLanguage("conf_save_bandwidth"));
        saveBandwidthCheckBox.setText2(localization.getLanguage("conf_save_bandwidth_description"));
        saveBandwidthCheckBox.setPadding(0, saveBandwidthCheckBox.getPaddingBottom(), saveBandwidthCheckBox.getPaddingRight(),
                saveBandwidthCheckBox.getPaddingBottom());

        View.inflate(activity, R.layout.advanced_settings_view, this);

        importSection = (LinearLayout) findViewById(R.id.advanced_settings_import_section);
        importBtn = (Button) findViewById(R.id.advanced_settings_import_button);
        importBtn.setOnClickListener(new ImportListener());
        addDivider(importSection);
        
        settingsContainer = (LinearLayout) findViewById(R.id.advanced_settings_view);

        bandwidthSaverSection = (LinearLayout) findViewById(R.id.advanced_settings_band_saver_section);
        bandwidthSaverSection.addView(saveBandwidthCheckBox);
        addDivider(bandwidthSaverSection);

        logSection = (LinearLayout) findViewById(R.id.advanced_settings_log_section);
        addDivider(logSection);

        spin = (Spinner) findViewById(R.id.advanced_settings_log_level_spinner);

        ArrayAdapter<CharSequence> aa = new ArrayAdapter<CharSequence>(activity, android.R.layout.simple_spinner_item);

        aa.add(localization.getLanguage("advanced_settings_log_level_none"));
        aa.add(localization.getLanguage("advanced_settings_log_level_error"));
        aa.add(localization.getLanguage("advanced_settings_log_level_info"));
        aa.add(localization.getLanguage("advanced_settings_log_level_debug"));
        aa.add(localization.getLanguage("advanced_settings_log_level_trace"));

        aa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spin.setAdapter(aa);

        logButtonsRaw = (LinearLayout) findViewById(R.id.advanced_settings_send_log_button_raw);
        viewLogBtn = (Button) findViewById(R.id.advanced_settings_view_log_button);
        viewLogBtn.setOnClickListener(new ViewLogListener());
        sendLogBtn = (Button) findViewById(R.id.advanced_settings_send_log_button);
        sendLogBtn.setOnClickListener(new SendLogListener());

        resetSection = (LinearLayout) findViewById(R.id.advanced_settings_reset_section);
        resetBtn = (Button) findViewById(R.id.advanced_settings_reset_button);
        resetBtn.setOnClickListener(new ResetListener());
        addDivider(resetSection);

        devSettingsSection = (LinearLayout) findViewById(R.id.advanced_settings_dev_settings_section);
        devSettingsBtn = (Button) findViewById(R.id.advanced_settings_dev_settings_button);
        devSettingsBtn.setOnClickListener(new DevSettingsListener());
        addDivider(devSettingsSection);
        
        
        // Added for ChBoSync
        oiNotepadSection = (LinearLayout) findViewById(R.id.advanced_settings_show_oi_notepad_missing_button);
        installOINotepadButton = (Button) findViewById(R.id.advanced_settings_button_install_oi_notepad);
        installOINotepadButton.setOnClickListener( new OINotepadInstallListener() );
        showNoteDummySyncButton = new TwoLinesCheckBox(activity);
        showNoteDummySyncButton.setText1(localization.getLanguage("conf_show_oinotepad_dummy_sync_button"));
        //showNoteDummySyncButton.setText2(localization.getLanguage("conf_...")); // for further description below text1 in a smaller font
        showNoteDummySyncButton.setPadding(0, showNoteDummySyncButton.getPaddingBottom(), 
        		                              showNoteDummySyncButton.getPaddingRight (),
        		                              showNoteDummySyncButton.getPaddingBottom() );
        oiNotepadSection.addView(showNoteDummySyncButton);
    }

    public void cancelSettings() {
    }

    
    /**
     * Inner class for call-back invoked when the user presses the button for installing "OI Notepad"
     * Added for ChBoSync
     */
    private class OINotepadInstallListener implements OnClickListener {
        public void onClick(View view) {
        	Activity activity = AndroidAdvancedSettingsTab.this.getActivity();
        	
        	if ( OINoteManager.getOINotepadInstalled() ) {
        		
				// Display dialog saying that installing of apps on the device seems not to be possible
				AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(activity);
				dialogBuilder.setTitle  ( R.string.dialog_title_operation_not_possible );
				dialogBuilder.setMessage( R.string.dialog_text_oinotepad_already_installed );
				
				dialogBuilder.setPositiveButton( R.string.dialog_continue, null );
				dialogBuilder.setCancelable(false); // no Cancel-Button needed
				
				AlertDialog dialog = dialogBuilder.create();
				dialog.show();		
        		
        	} else {
        		
				if ( OINotepadInstallationHelper.isIntentToOpenAppStoreClientSupported(activity) == true ) {
					
					OINotepadInstallationHelper.showDialog_ConfirmQuestionGoToAppstoreClient(activity);		
										
				} else {

					OINotepadInstallationHelper.showDialog_AppstoreClientNotAvailable(activity);	
				}        		
        		
        	}
        }
    } // end of inner class OINotepadInstallListener
    
    /**
     * A call-back invoked when the user presses the reset button.
     */
    private class ResetListener implements OnClickListener {
        public void onClick(View v) {
            AndroidSettingsScreen ass = (AndroidSettingsScreen) getUiScreen();
            //check the changes on other settings tabs before refresh
            if (ass.hasChanges()) {
                    dm.askYesNoQuestion(ass, localization.getLanguage(
                    "settings_changed_alert"),
                    new Runnable() {
                        AndroidSettingsScreen ass = (AndroidSettingsScreen) getUiScreen();
                        public void run() {
                            // Start reset through the SaveCallback
                            ass.save(false, new ResetSaveCallback());
                        }
                    },
                    new Runnable() {
                        public void run() {
                        }
                }, 0);
            } else {
                screenController.reset();
            }
        }
    }

    /**
     * A call-back invoked when the user presses the reset button.
     */
    private class DevSettingsListener implements OnClickListener {
        public void onClick(View v) {
            // We open a new activity here
            AndroidSettingsScreen ass = (AndroidSettingsScreen) getUiScreen();
            try {
                dm.showScreen(ass, Controller.DEV_SETTINGS_SCREEN_ID);
            } catch (Exception e) {
                Log.error(TAG_LOG, "Cannot show dev settings screen", e);
            }
        }
    }

    private class ResetSaveCallback extends SaveSettingsCallback {

        public ResetSaveCallback() {
            super(false, 0);
        }
        
        @Override
        public void tabSettingsSaved(boolean changes) {
            super.tabSettingsSaved(changes);

            if(count == 0 && result == true) {

                controller.getDisplayManager().showMessage((Screen)getUiScreen(),
                            localization.getLanguage("settings_saved"));
                screenController.reset();
            }
        }
    }

    /**
     * A call-back for when the user presses the view log button.
     */
    private class ViewLogListener implements OnClickListener {
        public void onClick(View v) {
            screenController.viewLog();
        }
    }

    /**
     * A call-back for when the user presses the send log button.
     */
    private class SendLogListener implements OnClickListener {
        public void onClick(View v) {
            screenController.sendLog();
        }
    }

    /**
     * A call-back for when the user presses the Import button.
     */
    private class ImportListener implements OnClickListener {
        public void onClick(View v) {
            screenController.importContacts();
        }
    }
}
