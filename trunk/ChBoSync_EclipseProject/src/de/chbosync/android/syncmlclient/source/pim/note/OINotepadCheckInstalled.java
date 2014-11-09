/*
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
 */
package de.chbosync.android.syncmlclient.source.pim.note;

import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.net.Uri;

 
/**
 * Syncing notes is only possible when the app "OI Notepad" is installed on the Android device
 * ("OI" stands for "OpenIntens", which is an open source project), see also
 * <a href="https://code.google.com/p/openintents/">https://code.google.com/p/openintents/</a>.
 * This app can be obtained in Google's appstore under the following URL:
 * <a href="https://play.google.com/store/apps/details?id=org.openintents.notepad">https://play.google.com/store/apps/details?id=org.openintents.notepad</a>.
 * The value of parameter "id" in this URL is the ID needed to check if the app is installed on the device. 
 * 
 * This class contains a method to open the appstore entry for "OI Notepad" in the appstore client by means
 * of an implicit intent.
 * 
 * So far all methods in this class are static.
 * 
 * Class added for ChBoSync (Nov 2014).
 */
public class OINotepadCheckInstalled {

	/** ID of app "OI Notepad" to find out if it is already installed on the device and
	 *  to open the entry in the appstore if needed.
	 */
	public static final String APPID_OF_OPENINTENT_NOTEPAD = "org.openintents.notepad";
	
			
	/**
	 * Creates implicit intent to open entry of app "OI Notepad" in device's appstore client. 
	 * 
	 * @return Intent to open entry of "OI Notepad" on appstore client.
	 */
	protected static Intent createIntentForOINotepadAppstoreEntry() {
		
		Uri uri = Uri.parse("market://details?id=" + APPID_OF_OPENINTENT_NOTEPAD );
		final Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setData(uri);
		
		return intent;
	}
	
	
	/**
	 * Method to open entry of app "OI Notepad" in Google's appstore.
	 * Before the intent is actually is dispatched method 
	 * {@link de.chbosync.android.syncmlclient.source.pim.note.OINoteCheckInstalled#isIntentSupported(Intent, Context)}
	 * is invoked to find out if the current device can handle this intent 
	 * (if we would dispatch this intent and the device is not capable of handling it, 
	 * then the app would crash). 
	 * 
	 * @param activity Reference to callling activity, needed to dispatch implicit intent.
	 * 
	 * @return <tt>True</tt> if no error occured, <tt>false</tt> if an error occured.
	 */
	public static boolean openEntryForOINotepadInAppstore(Activity activity) {
		
		Intent intent = createIntentForOINotepadAppstoreEntry();
		
		if ( isIntentToOpenAppStoreClientSupported(activity) ) {
			
			activity.startActivity(intent);
			return true;
			
		} else {
			
			return false;
		}		
	}
	
	
	/**
	 * Method to check if implicit intent to open entry for app "OI Notepad" in appstore client on current
	 * device can be handled.
	 * If you try to dispatch the request to open an entry in the appstore client when no appstore client
	 * is installed on the device, then the app will immediately crash. 
	 * 
	 * @param context Context (e.g. self-reference of calling activity) needed for accessing package manager.
	 * 
	 * @return <tt>True</tt> if intent to open appstore entry for "OI Notepad" can be handled, <tt>false</tt>
	 *         otherwise.
	 */
	public static boolean isIntentToOpenAppStoreClientSupported(Context context) {
		
		final int flags = PackageManager.MATCH_DEFAULT_ONLY;
		
		PackageManager packageManager = context.getPackageManager();
		
		Intent intent = createIntentForOINotepadAppstoreEntry();
				
		List<ResolveInfo> list = packageManager.queryIntentActivities(intent, flags); 
									 
									
		// If at least one app was found that can handle the intent then this method returns "true"
		return list.size() > 0;		
	}		
	
}
