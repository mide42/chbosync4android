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

import java.io.IOException;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import com.funambol.client.controller.AdvancedSettingsScreenController;
import com.funambol.client.controller.Controller;
import com.funambol.client.controller.DialogController;
import com.funambol.util.Log;
import com.funambol.util.LogContent;

import de.chbosync.android.syncmlclient.AndroidConfiguration;
import de.chbosync.android.syncmlclient.App;
import de.chbosync.android.syncmlclient.AppInitializer;
import de.chbosync.android.syncmlclient.ContactsImporter;
import de.chbosync.android.syncmlclient.activities.settings.AndroidAdvancedSettingsTab;


/**
 * Realize the control of the AndroidAdvancedSettingsTab elements. Uses the 
 * activity passed as parameter in the constructor in order to launch intents
 * to view and send log using the native android application (if there is some
 * registered as intent filter for the required intent action of send and view).
 */
public class AndroidAdvancedSettingsScreenController extends AdvancedSettingsScreenController {

    private static final String TAG = "AndroidAdvancedSettingsScreenController";

    private Activity a;

    /**
     * Public constructor
     * @param controller the client clontroller that contains localization,
     * customization and configuration informations
     * @param screen the view to be controlled by this class
     */
    public AndroidAdvancedSettingsScreenController(Controller controller, AndroidAdvancedSettingsTab screen) {
        super(controller, screen);
        a = (Activity) screen.getUiScreen();        
    }
    
    
    /**
     * Overwritten for setting initial value of CheckBox for preference 
     * "showDummyButtonForNotesSyncing".
     * Saving is done in method <tt>checkAndSave()</tt>. 
     * Added for ChBoSync 
     */
    @Override
    public void initialize() {    	
    	super.initialize();
    	
    	if(this.screen instanceof AndroidAdvancedSettingsTab) {
    		
    		AndroidAdvancedSettingsTab aast = (AndroidAdvancedSettingsTab) screen;
    		
    		if (this.configuration instanceof AndroidConfiguration) {
    			AndroidConfiguration ac = (AndroidConfiguration)this.configuration;
    			
    			aast.setShowDummyButtonForNotesSyncing( ac.getShowDummyButtonForNotesSyncing()   );
    			aast.setEncryptedNotesEnabled         ( ac.getDetectionOfEncryptedNotesEnabled() );
    			
    		}    		    		
    	}    	    	
    }

    
    /**
     * Realize the Send log action. Implement the client dependent send log
     * feature using the android native intent implementation in order to call
     * a mail application (or other app registered for this intent) and send the
     * log the log is sent as a mail with attachment if the log appender writes
     * on a file, a mail with the log content as the body otherwise. The
     * receiver is the funambol client log address set for the android sync
     * client.
     */
    public void sendLog() {
        try {
            LogContent lc = Log.getCurrentLogContent();

            String log = lc.getContent();

            
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            
            //String[] to = {customization.getSupportEmailAddress()};
            //sendIntent.putExtra(Intent.EXTRA_EMAIL, to);
            
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, "ChBoSync Client Log");

            //if the sdcard is present the log is made in
            if (lc.getContentType() == LogContent.FILE_CONTENT) {
                sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + log));
                sendIntent.putExtra(Intent.EXTRA_TEXT, "ChBoSync Client Log Attached");
                sendIntent.setType("text/plain");
            } else {
                sendIntent.putExtra(Intent.EXTRA_TEXT, "ChBoSync Client Log:\n" + log);
                sendIntent.setType("text/plain");
            }

            ((Activity) screen.getUiScreen()).startActivity(Intent.createChooser(sendIntent, "Select Email Service:"));
        } catch (IOException ioe) {
            Log.error(TAG, "IO Error: Cannot send Log from the client", ioe);
        } catch (Exception ex) {
            Log.error(TAG, "Generic Error: Cannot send Log from the client", ex);
        }
    }

    /**
     * Realize the View log action. Implement the client dependent view log
     * feature using the android native intent implementation in order to call
     * a viewer application (an app registered for this intent) and show the log
     * content to the user.
     */
    public void viewLog() {
        try {
            LogContent lc = Log.getCurrentLogContent();
            String log = lc.getContent();
            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            viewIntent.setDataAndType(Uri.parse("file://" + log), "text/plain");
            ((Activity) screen.getUiScreen()).startActivity(viewIntent);
            
        } catch (IOException ioe) {
            Log.error(TAG, "IO Error: Cannot view Log from the client", ioe);
        } catch (Exception ex) {
            Log.error(TAG, "Generic Error: Cannot view Log from the client", ex);
        }
    }

    @Override
    public void reset() {
        DialogController dc = controller.getDialogController();
        AndroidHomeScreenController ahsc = (AndroidHomeScreenController)controller.getHomeScreenController();
        if (ahsc.isSynchronizing()||ahsc.isFirstSyncDialogDisplayed()){
            dc.showMessage(screen, localization.getLanguage("sync_in_progress_dialog"));
        } else {
            super.reset();
        }
    }

    @Override
    public void checkAndSave() {
        super.checkAndSave();
        
        AppInitializer appInitializer = App.i().getAppInitializer();
        if (screen.getBandwidthSaver()) {
            // We need to acquire a WiFi lock to keep the WiFi active and
            // scanning for available access points
            // Try to acquire a lock to keep scanning for Wifi
            appInitializer.acquireWiFiLock();
        } else {
            appInitializer.releaseWiFiLock(false);
        }
        
        // Added for ChBoSync
    	if(this.screen instanceof AndroidAdvancedSettingsTab) {
    		
    		AndroidAdvancedSettingsTab aast = (AndroidAdvancedSettingsTab) screen;
    		
    		if (this.configuration instanceof AndroidConfiguration) {
    			
    			AndroidConfiguration ac = (AndroidConfiguration)this.configuration;    			
    			ac.setShowDummyButtonForNotesSyncing  ( aast.getShowDummyButtonForNotesSyncing() );
    			ac.setDetectionOfEncryptedNotesEnabled( aast.getDetectEncryptedNotes()           );
    		}    		
    	}    	    	
    }

    @Override
    public void importContacts() {
        ContactsImporter importer = new ContactsImporter(Controller.ADVANCED_SETTINGS_SCREEN_ID, a, this);
        importer.importContacts(false);
    }

    public void importContactsCompleted() {
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG, "importContactsCompleted");
        }
        // We can go back to the home screen if the user did not change anything
        // else
        AndroidController c = (AndroidController)controller;
        AndroidSettingsScreenController settingsScreenController = c.getSettingsScreenController();
        // Since there are no pending changes, we just cancel
        if (!settingsScreenController.hasChanges()) {
            settingsScreenController.cancel();
        }
    }
}
