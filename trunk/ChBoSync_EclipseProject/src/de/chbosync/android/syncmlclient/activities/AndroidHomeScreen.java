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

package de.chbosync.android.syncmlclient.activities;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.LinearLayout;

import com.funambol.client.controller.Controller;
import com.funambol.client.controller.HomeScreenController;
import com.funambol.client.controller.UISyncSourceController;
import com.funambol.client.customization.Customization;
import com.funambol.client.localization.Localization;
import com.funambol.client.source.AppSyncSource;
import com.funambol.client.ui.Bitmap;
import com.funambol.client.ui.DisplayManager;
import com.funambol.client.ui.HomeScreen;
import com.funambol.client.ui.UISyncSourceContainer;
import com.funambol.util.Log;

import de.chbosync.android.syncmlclient.AndroidAppSyncSourceManager;
import de.chbosync.android.syncmlclient.AndroidConfiguration;
import de.chbosync.android.syncmlclient.App;
import de.chbosync.android.syncmlclient.AppInitializer;
import de.chbosync.android.syncmlclient.R;
import de.chbosync.android.syncmlclient.controller.AndroidHomeScreenController;
import de.chbosync.android.syncmlclient.source.pim.note.OINoteManager;
import de.chbosync.android.syncmlclient.source.pim.note.OINotepadInstallationHelper;


/**
 * Activity for presenting the main screen of the app, i.e. the screen with the
 * buttons to trigger the syncing of individual syncing sources (e.g., calendar, notes). 
 * Class contains several inner classes.
 */
public class AndroidHomeScreen extends Activity implements HomeScreen, UISyncSourceContainer {

    private static final String TAG = "AndroidHomeScreen";

    private static final String FIRST_SYNC_ALERT_PENDING         = "FirstSyncAlertPending";
    private static final String WIFI_NOT_AVAILABLE_ALERT_PENDING = "WifiNotAvailableAlertPending";

    // some constants for the options menu
    private static final int MENU_ITEM_SETTINGS_ID = Menu.FIRST;
    private static final int MENU_ITEM_LOGOUT_ID   = MENU_ITEM_SETTINGS_ID + 1;
    private static final int MENU_ITEM_ABOUT_ID    = MENU_ITEM_LOGOUT_ID   + 1;
    //private static final int MENU_ITEM_REFRESH_ID  = MENU_ITEM_ABOUT_ID    + 1;

    private final int SYNC_SOURCE_ID     = Menu.FIRST;
    private final int GOTO_SOURCE_ID     = SYNC_SOURCE_ID + 1;
    private final int SETTINGS_SOURCE_ID = GOTO_SOURCE_ID + 1;
    private final int CANCEL_SOURCE_ID   = SETTINGS_SOURCE_ID + 1;

    private AndroidHomeScreenController homeScreenController;
    private List<AndroidUISyncSource> listItems = new ArrayList<AndroidUISyncSource>();

    private Localization localization;
    private Customization customization;
    private AndroidAppSyncSourceManager appSyncSourceManager;
    private AndroidDisplayManager dm;

    private Button syncAllButton;
    private LinearLayout mainLayout;
    private LinearLayout linLayoutForButtons;
    private String syncAllText = null;

    private SetSyncAllTextUIThread setSyncAllTextUIThread = new SetSyncAllTextUIThread();
    private SetSyncAllEnabledUIThread setSyncAllEnabledUIThread = new SetSyncAllEnabledUIThread();
    private RedrawUIThread redrawUIThread = new RedrawUIThread();
    private UpdateAvailableSourcesUIThread updateAvailableSourcesUIThread = new UpdateAvailableSourcesUIThread(this);

    // This is the sync item menu entry. It is global because we need to
    // dynamically change its label, depending on the sync status
    private String syncItemText;

    private boolean screenLocked = false;
    
    // Changed for ChBoSync: Local variable of onCreate() method was made a member variable
    protected AndroidConfiguration configuration = null;

    /**
     * Called with the activity is first created.
     */
    @Override
    public void onCreate(Bundle icicle) {

        // Set up the activity
        super.onCreate(icicle);

        // Lock the screen orientation to vertical for this screen
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        AppInitializer initializer = App.i().getAppInitializer();
        initializer.init(this);

        // Initialize the localization
        localization = initializer.getLocalization();

        // By default we set the multi buttons layout
        setMultiButtonsLayout();

        // Now initialize everything
        customization = initializer.getCustomization();
        configuration = initializer.getConfiguration();
        appSyncSourceManager = initializer.getAppSyncSourceManager();

        Controller controller = initializer.getController();
        homeScreenController = (AndroidHomeScreenController)controller.getHomeScreenController();
        homeScreenController.setHomeScreen(this);
        
        

        this.dm = (AndroidDisplayManager) controller.getDisplayManager();

        // We have to explicitely call the initialize here
        initialize(homeScreenController);

        // Refresh the set of available sources
        homeScreenController.updateEnabledSources();
        homeScreenController.selectFirstAvailable();

        homeScreenController.attachToRunningSyncIfAny();

        int firstSyncAlertId = 0;
        int wifiNotAvailableId = 1;

        if (icicle != null) {
            firstSyncAlertId = icicle.getInt(FIRST_SYNC_ALERT_PENDING);
            wifiNotAvailableId = icicle.getInt(WIFI_NOT_AVAILABLE_ALERT_PENDING);
        }
        if (firstSyncAlertId == DisplayManager.FIRST_SYNC_DIALOG_ID) {
            if (Log.isLoggable(Log.INFO)) {
                Log.info(TAG, "Removing bundle property and displaying alert after rotation");
            }
            icicle.remove(FIRST_SYNC_ALERT_PENDING);
            //Resume the last sync dialog alert if it was displayed before
            //resuming this activity
            controller.getDialogController().resumeLastFirstSyncDialog(this);
        } else if(wifiNotAvailableId == DisplayManager.NO_WIFI_AVAILABLE_ID) {
            if (Log.isLoggable(Log.INFO)) {
                Log.info(TAG, "Removing bundle property and displaying alert after rotation");
            }
            icicle.remove(WIFI_NOT_AVAILABLE_ALERT_PENDING);
            //Resume the WI-FI not available dialog alert if it was displayed before
            //resuming this activity
            controller.getDialogController().resumeWifiNotAvailableDialog(this);
        } else {
            // We shall remove all pending alerts here, in the case the app was
            // closed and restarted
            dm.removePendingAlert(DisplayManager.FIRST_SYNC_DIALOG_ID);
            // There is another case we must handle. The application (UI) was closed but a
            // automatic sync triggered the first sync dialog. In this case we
            // don't have anything in the activity state, but we need to show
            // the alert
            homeScreenController.showPendingFirstSyncQuestion();
        }

        // If during the upgrade some source was disabled because its sync type
        // is no longer supported, then we shall inform the user        
        if (Log.isLoggable(Log.INFO)) {
            Log.info(TAG, "source sync type changed = " + configuration.getPimSourceSyncTypeChanged());
        }
        if (configuration.getPimSourceSyncTypeChanged()) {
            dm.showOkDialog(this, localization.getLanguage("upg_one_way_no_longer_supported"),
                                  localization.getLanguage("dialog_ok"));
            configuration.setPimSourceSyncTypeChanged(false);
            configuration.commit();
        }
        
    }

        
    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        // Do not change anything
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (dm.isAlertPending(DisplayManager.FIRST_SYNC_DIALOG_ID)) {
            dm.dismissSelectionDialog(DisplayManager.FIRST_SYNC_DIALOG_ID);
            outState.putInt(FIRST_SYNC_ALERT_PENDING, DisplayManager.FIRST_SYNC_DIALOG_ID);
        } else if (dm.isAlertPending(DisplayManager.NO_WIFI_AVAILABLE_ID)) {
            dm.dismissSelectionDialog(DisplayManager.NO_WIFI_AVAILABLE_ID);
            outState.putInt(WIFI_NOT_AVAILABLE_ALERT_PENDING, DisplayManager.NO_WIFI_AVAILABLE_ID);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG, "onCreateDialog: " + id);
        }
        Dialog result = null;
        if(dm != null) {
            result = dm.createDialog(id);
        }
        if(result != null) {
            return result;
        } else {
            return super.onCreateDialog(id);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (Log.isLoggable(Log.DEBUG)) {
            Log.debug(TAG, "Nullifying home screen controller reference");
        }
        homeScreenController.setHomeScreen(null);
    }

    /** Create the Activity menu. */
    
    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
    	super.onCreateOptionsMenu(menu);
    	
        if (syncItemText == null) {
            syncItemText = localization.getLanguage("menu_sync");
        }

        MenuItem settingsItem = menu.add(0, MENU_ITEM_SETTINGS_ID, Menu.NONE, localization.getLanguage("menu_settings"));
        settingsItem.setIcon(android.R.drawable.ic_menu_preferences);
        
        MenuItem logoutItem = menu.add(0, MENU_ITEM_LOGOUT_ID, Menu.NONE, localization.getLanguage("menu_logout"));
        logoutItem.setIcon(R.drawable.ic_menu_logout);
        
        MenuItem aboutItem = menu.add(0, MENU_ITEM_ABOUT_ID, Menu.NONE, localization.getLanguage("menu_about"));
        aboutItem.setShortcut('0', 'A');
        aboutItem.setIcon(android.R.drawable.ic_menu_info_details);
                
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return super.onPrepareOptionsMenu(menu);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        homeScreenController.setForegroundStatus(false);
        Log.trace(TAG, "Paused activity (foreground status off)");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        homeScreenController.setForegroundStatus(true);
        Log.trace(TAG, "Resumed activity (foreground status on)");
    }

    /**
     * Event-Handler for the option menu items
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle all of the possible menu actions
        switch ( item.getItemId() ) {
            case MENU_ITEM_SETTINGS_ID:
                homeScreenController.showConfigurationScreen();
                break;
                
            case MENU_ITEM_LOGOUT_ID:
                showConfirmQuestionLogout();
                break;
                
            case MENU_ITEM_ABOUT_ID:
                homeScreenController.showAboutScreen();
                break;
        }
        
        return super.onOptionsItemSelected(item);
    }

        
    /**
     * Method shows dialog with confirmation question asking the user if he really wants to log out.  
     * Added for CboSync
     */
    protected void showConfirmQuestionLogout() {
    	
		AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
		dialogBuilder.setTitle  ( R.string.dialog_title_confirm_logout    );
		dialogBuilder.setMessage( R.string.dialog_confirm_logout_question );
		
		final DialogInterface.OnClickListener onYesButtonHandler = new DialogInterface.OnClickListener() {						
			@Override
			public void onClick(DialogInterface dialog, int which) {
				homeScreenController.logout();
			}
		};
		
		dialogBuilder.setPositiveButton( R.string.button_yes, onYesButtonHandler );
		dialogBuilder.setNegativeButton( R.string.button_no,  null );
		
		AlertDialog dialog = dialogBuilder.create();
		dialog.show();	    	
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        v.requestFocus();

        AppSyncSource appSource = appSyncSourceManager.getSource(v.getId());
        createContextMenuForSource(appSource, menu);
    }

    public void createContextMenuForSource(AppSyncSource appSource, ContextMenu menu) {
        if (appSource != null) {
            if (!appSource.isEnabled() || !appSource.isWorking()) {
                // If we get a requirement to allow sources to be enabled via
                // context menu, this can be done here
                return;
            }

            if (homeScreenController.isSynchronizing()) {
                // If a sync is in progress, the context menu can only be used
                // to stop the current sync of the current source
                AppSyncSource currentSource = homeScreenController.getCurrentSource();
                if (currentSource != null && currentSource.getId() == appSource.getId()) {
                    int cancelId = appSource.getId() << 16 | CANCEL_SOURCE_ID;
                    menu.add(0, cancelId, 0, localization.getLanguage("menu_cancel_sync"));
                }
            } else {
                // This works if the number of sources is < 16 which is a fairly
                // safe assumption
                int syncId = appSource.getId() << 16 | SYNC_SOURCE_ID;
                int gotoId = appSource.getId() << 16 | GOTO_SOURCE_ID;
                int settingsId = appSource.getId() << 16 | SETTINGS_SOURCE_ID;

                StringBuffer label = new StringBuffer();
                label.append(localization.getLanguage("menu_sync")).append(" ").append(appSource.getName());
                menu.add(0, syncId, 0, label.toString());
                // Add goto menu option only if an external app manager is set
                if(appSource.getAppManager() != null) {
                    label = new StringBuffer();
                    label.append(localization.getLanguage("menu_goto")).append(" ").append(appSource.getName());
                    menu.add(0, gotoId, 0, label.toString());
                }
                menu.add(0, settingsId, 0, localization.getLanguage("menu_settings"));
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
    	
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();

        int id = item.getItemId();
        int sourceId = id >> 16;
        int itemId   = id & 0xFFFF;

        AppSyncSource appSource = appSyncSourceManager.getSource(sourceId);

        if (appSource == null) {
            Log.error(TAG, "Cannot find view associated to this context menu");
            return super.onContextItemSelected(item);
        }

        switch (itemId) {
            case SYNC_SOURCE_ID:
                homeScreenController.syncMenuSelected();
                return true;
            case GOTO_SOURCE_ID:
                homeScreenController.gotoMenuSelected();
                return true;
            case SETTINGS_SOURCE_ID:
                homeScreenController.showConfigurationScreen();
                return true;
            case CANCEL_SOURCE_ID:
                homeScreenController.cancelMenuSelected();
                return true;
            default:
                Log.error(TAG, "Unknwon context menu id " + id);
                return super.onContextItemSelected(item);
        }
    }


    /**************** Home Screen Implementation **********************/

    public void initialize(HomeScreenController controller) {
        if (Log.isLoggable(Log.INFO)) {
            Log.info(TAG, "Initializing");
        }

        // We force the controller to recompute the available sources
        homeScreenController.updateAvailableSources();
        homeScreenController.redraw();
        // Now update the list of visible items in the UI
        updateVisibleItems();
    }

    public void lock() {
        screenLocked = true;
    }

    public void unlock() {
        screenLocked = false;
    }

    public boolean isLocked() {
        return screenLocked;
    }

    public void setSelectedIndex(int index) {
        // We can receive events before the list is actually populated. Just
        // ignore them
        if (listItems.isEmpty()) {
            return;
        }
        AndroidUISyncSource button = listItems.get(index);
        // Show the given element as selected
        button.setSelection(true, false);
        button.requestFocus();
    }

    public void deselectIndex(int index) {
        // We can receive events before the list is actually populated. Just
        // ignore them
        if (listItems.isEmpty()) {
            return;
        }
        AndroidUISyncSource button = listItems.get(index);
        // Show the given element as selected
        button.setSelected(false);
    }

    public void redraw() {
        runOnUiThread(redrawUIThread);
    }

    public void updateVisibleItems() {
        runOnUiThread(updateAvailableSourcesUIThread);
    }

    //////////////////////////////////////////////////////////////////////

    public Object getUiScreen() {
        return this;
    }

    private void setMultiButtonsLayout() {
        // Set the content view
        setContentView(R.layout.homescreen);

        // Grab the views 
        mainLayout = (LinearLayout)findViewById(R.id.mainLayout);
        linLayoutForButtons = (LinearLayout)findViewById(R.id.buttons);
        syncAllButton = (Button)findViewById(R.id.syncAllButton);

        // Add the sync all button if required
        if (syncAllText != null) {
            syncAllButton.setFocusable(false);
            syncAllButton.setText(syncAllText);
            SyncAllButtonListener buttonListener = new SyncAllButtonListener();
            syncAllButton.setOnClickListener(buttonListener);
        } else {
            // Remove the button bar
            LinearLayout buttonBar = (LinearLayout)findViewById(R.id.homeScreenButtonBar);
            mainLayout.removeView(buttonBar);
        }
    }

    private void setSingleButtonLayout() {
        setContentView(R.layout.homescreen_single);
        mainLayout = (LinearLayout)findViewById(R.id.mainLayoutSingle);
        linLayoutForButtons = mainLayout;
        syncAllButton = null;
    }

    /**
     * Inner class
     */
    private class ButtonListener implements OnClickListener {

        private int idx;

        public ButtonListener(int idx) {
            this.idx = idx;
        }

        /**
         * Method for event-handling when on one of the buttons for syncing (e.g. for syncing notes) 
         * is pressed. 
         */
        public void onClick(View view) {
        	
            if (Log.isLoggable(Log.DEBUG)) {
                Log.debug(TAG, "Clicked on item: " + idx + " hasFocus=" + view.hasFocus());
            }
            
            if (screenLocked == false) {
            	
                view.requestFocus();                                
                homeScreenController.buttonPressed(idx);
            }
        }
    }
    
    

    /**
     * Inner class
     */
    private class SyncAllButtonListener implements OnClickListener {

        public SyncAllButtonListener() {
        }

        public void onClick(View v) {
            if (Log.isLoggable(Log.DEBUG)) {
                Log.debug(TAG, "Clicked on sync all");
            }
            homeScreenController.syncAllPressed();
        }
    }

    /**
     * Inner class
     */
    private class AloneButtonListener implements OnClickListener {

        public AloneButtonListener() {
        }

        public void onClick(View v) {
            if (Log.isLoggable(Log.DEBUG)) {
                Log.debug(TAG, "Clicked on the alone source");
            }
            homeScreenController.aloneSourcePressed();
        }
    }

    /**
     * Inner class. A call-back for when the sync buttons focus changes.
     */
    private class FocusListener implements OnFocusChangeListener {
        private int idx;

        public FocusListener(int idx) {
            this.idx = idx;
        }

        public void onFocusChange(View v, boolean hasFocus) {
            if (Log.isLoggable(Log.DEBUG)) {
                Log.debug(TAG, "Focus moved to item: " + idx);
            }
            if (!screenLocked && hasFocus) {
                homeScreenController.buttonSelected(idx);
            }
        }
    }

    public void addSyncAllButton(String text, Bitmap icon, Bitmap bg, Bitmap bgSel) {
        // We ignore the icons because on Android we keep the button very
        // simple, just text
        syncAllText = text;
    }

    public void setSyncAllText(String text) {
        setSyncAllTextUIThread.setText(text);
        runOnUiThread(setSyncAllTextUIThread);
    }

    public String getSyncAllText() {
        if (syncAllButton != null) {
            return syncAllButton.getText().toString();
        } else {
            return null;
        }
    }

    public void setSyncAllEnabled(boolean enabled) {
        setSyncAllEnabledUIThread.setEnabled(enabled);
        runOnUiThread(setSyncAllEnabledUIThread);
    }

    public void setSyncAllSelected(boolean selected) {
        // Ignore this because in this view the button is separate from the
        // sources list
    }

    public void setSyncMenuText(String text) {
        syncItemText = text;
    }

    /**
     * Inner class
     */
    private class RedrawUIThread implements Runnable {

        public RedrawUIThread() {
        }

        public void run() {
            mainLayout.invalidate();
        }
    }

    /**
     * Inner class
     */
    private class SetSyncAllTextUIThread implements Runnable {
        private String text;

        public SetSyncAllTextUIThread() {
        }

        public void setText(String text) {
            this.text = text;
        }

        public void run() {
            if (syncAllButton != null) {
                syncAllButton.setText(text);
            }
        }
    }

    /**
     * Inner class
     */
    private class SetSyncAllEnabledUIThread implements Runnable {
        private boolean enabled;

        public SetSyncAllEnabledUIThread() {
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void run() {
            if (syncAllButton != null) {
                syncAllButton.setClickable(enabled);
            }
        }
    }

    /**
     * Inner class, adds individual buttons (for different syncSources) 
     * to linear layout.
     */
    private class UpdateAvailableSourcesUIThread implements Runnable {

        private AndroidHomeScreen screen;

        public UpdateAvailableSourcesUIThread(AndroidHomeScreen screen) {
            this.screen = screen;
        }

        public void run() {

            if (homeScreenController == null) { return; }
                        
            LinearLayout.LayoutParams linLayoutParams = null;

            // Remove all buttons first
            linLayoutForButtons.removeAllViews();

            // Initialize the sources listed
            List<AppSyncSource> appSources = AndroidHomeScreen.this.homeScreenController.getVisibleItems();
            Iterator<AppSyncSource> iter  = appSources.iterator();

            int idx = 0;

            while( iter.hasNext() ) {
            	            	            	
                AppSyncSource appSource = iter.next();
                
                UISyncSourceController itemController;
                itemController = appSource.getUISyncSourceController();

                // Prepare the source icon to be displayed
                Bitmap sourceIcon = null;
                if (appSource.isWorking() && appSource.isEnabled() ) {
                    sourceIcon = customization.getSourceIcon(appSource.getId());
                } else {
                    sourceIcon = customization.getSourceDisabledIcon(appSource.getId());
                }

                // Create an item for each entry, if there is only one entry,
                // then we build a stand alone representation
                AndroidUISyncSource item = null;                
                if (idx == 0 && !iter.hasNext()) {
                    // The source is alone
                    item = (AndroidUISyncSource)appSource.createAloneUISyncSource(screen);
                    if (item != null) {
                        // Change the overall layout
                        setSingleButtonLayout();
                        // Remove the sync all button
                        linLayoutParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
                        int margin = adaptSizeToDensity(10);
                        linLayoutParams.setMargins(0, 0, 0, margin); // bottom=margin
                        // Disable the status animation supplied by the controller
                        itemController.disableStatusAnimation();
                        // Set localization and customization
                        if (item instanceof AndroidAloneUISyncSource) {
                            AndroidAloneUISyncSource aloneItem = (AndroidAloneUISyncSource)item;
                            aloneItem.setLocalization(localization);
                            aloneItem.setCustomization(customization);
                            aloneItem.setHomeScreenView(AndroidHomeScreen.this);
                        }
                        // Register the button listener
                        AloneButtonListener buttonListener = new AloneButtonListener();
                        item.setOnClickListener(buttonListener);
                    }
                }
                if (item == null) {
                    if (idx == 0) {
                        setMultiButtonsLayout();
                    }
                    item = (AndroidUISyncSource)appSource.createButtonUISyncSource(screen);
                    // The buttons shall only wrap the content
                    linLayoutParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                    int margin = adaptSizeToDensity(2);
                    linLayoutParams.setMargins(margin, margin, margin, margin);
                    // Enable the status animation supplied by the controller
                    itemController.enableStatusAnimation();
                    // Register the button listener
                    ButtonListener buttonListener = new ButtonListener(idx);
                    item.setOnClickListener(buttonListener);

                }
                item.setSource(appSource);

                itemController.setUISyncSource(item);

                // All these buttons are associated to a given application
                // source
                item.setSource(appSource);
                item.setContainer(screen);

                if (sourceIcon != null) {
                    item.setIcon(sourceIcon);
                }

                item.setTitle(appSource.getName());
                listItems.add(item);

                // Add this button to the main list and the appropriate listeners
                FocusListener  focusListener = new FocusListener(idx);
                item.setOnFocusChangeListener(focusListener);
                // We use the app source id as view id so we can quickly recognize
                // it in the context menu handling
                item.setId(appSource.getId());
                registerForContextMenu(item);
                linLayoutForButtons.addView(item, linLayoutParams);
                idx++;
            }
            
            // If "OI Notepad" is not installed then display button for installation of this app        
            
            if (configuration.getShowDummyButtonForNotesSyncing() && OINoteManager.getOINotepadInstalled() == false) {
            	 LinearLayout.LayoutParams linLayoutParams2 = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            	 
            	 int margin = adaptSizeToDensity(20); // add a bigger gap to indicate that now another type of buttons is comming
            	 linLayoutParams2.setMargins(0, margin, 0, 0); // top=margin
            	 
            	 linLayoutForButtons.addView( createSyncingNotesDisabledButton(), linLayoutParams2);            	
            } 
            	                         
        } // run()
        
    } // end of inner class
    
    
    /**
     * Creates a Dummy-Sync-Button for notes when "OI Notepad" is not installed.
     * This button gets also an event-handler for trying to install "OI Notepad". 
     * Added for ChBoSync
     * 
     * @return UI-Element to be added when syncing of notes is disabled.
     */
    protected View createSyncingNotesDisabledButton() {
    	
    	AndroidButtonUISyncSource notesDisabledButton = new AndroidButtonUISyncSource(AndroidHomeScreen.this);
    	notesDisabledButton.setTitle( getString(R.string.syncing_notes_disabled) );
    	notesDisabledButton.setStatusString( "Click here to install \"OI Notepad\"" );
    	notesDisabledButton.setIcon(new Bitmap(R.drawable.icon_notes_grey));
    	notesDisabledButton.setStatusIcon(new Bitmap(R.drawable.icon_notes_grey));
    	
    	notesDisabledButton.setOnClickListener(new OnClickListener() {			
			@Override
			public void onClick(View view) {

				if ( OINotepadInstallationHelper.isIntentToOpenAppStoreClientSupported(AndroidHomeScreen.this) == true ) {
					
					OINotepadInstallationHelper.showDialog_ConfirmQuestionGoToAppstoreClient(AndroidHomeScreen.this);		
										
				} else {

					OINotepadInstallationHelper.showDialog_AppstoreClientNotAvailable(AndroidHomeScreen.this);	
				}
			}
		});
    	
    	return notesDisabledButton;
    }

    
    private int adaptSizeToDensity(int size) {
        return (int)(size*getResources().getDisplayMetrics().density);
    }
    
}
