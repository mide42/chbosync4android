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

package de.chbosync.android.syncmlclient;

import android.content.Context;
import android.os.Environment;
import android.telephony.TelephonyManager;
import android.util.Log;


/**
 * Container class for static utility methods.
 */
public class AndroidUtils {

	private static final String TAG_LOG = "AndroidUtils";
	
    /**
     * Uses the telephony manager to understand if the client is running on a
     * simulator or a real device. On the simulator the device id is a 15 chars
     * long 0 sequence (os < 2.1).
     * @param context the application Context
     * @return true if the device id is a 0s sequence, false otherwise.
     */
    public static boolean isSimulator(Context context) {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(
                Context.TELEPHONY_SERVICE);
        String deviceId = tm.getDeviceId();
        return "000000000000000".equals(deviceId);
    }

    /**
     * Checks if the sdcard is mounted.
     * 
     * @return <tt>true</tt> when the SDCard is mounted, <tt>false</tt> otherwise. 
     */
    public static boolean isSDCardMounted() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }
    
    /**
     * Get app's version from manifest file (attribute <i>android:versionName</i>).
     * (Method added for ChBoSync)
     * 
     * @param context Reference to context needed for obtaining package manager.
     * @return Version of App as specified manifest file.
     */
    public static String getVersionNumberFromManifest(Context context) {
    	
		final int flags = 0;
		String appVersionStr = "";
		
		try {			
			appVersionStr =  context.getPackageManager().getPackageInfo(context.getPackageName(), flags).versionName;
		}
		catch (Exception ex) {
			Log.w(TAG_LOG, "Could not obtain version of app from manifest file: " + ex);
			appVersionStr = "???"; // Fallback for version number
		}    	
    	
    	return appVersionStr;
    }
    
     
}
