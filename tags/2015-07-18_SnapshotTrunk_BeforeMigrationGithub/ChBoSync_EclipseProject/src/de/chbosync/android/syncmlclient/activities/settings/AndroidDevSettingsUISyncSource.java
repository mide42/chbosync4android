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

import android.app.Activity;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.funambol.client.configuration.Configuration;
import com.funambol.client.localization.Localization;
import com.funambol.client.source.AppSyncSource;
import com.funambol.client.source.AppSyncSourceConfig;
import com.funambol.client.ui.Bitmap;
import com.funambol.client.ui.DevSettingsUISyncSource;
import com.funambol.sync.SourceConfig;
import com.funambol.sync.SyncSource;

import de.chbosync.android.syncmlclient.R;
import de.chbosync.android.syncmlclient.AndroidCustomization;
import de.chbosync.android.syncmlclient.App;
import de.chbosync.android.syncmlclient.controller.AndroidController;


public class AndroidDevSettingsUISyncSource extends RelativeLayout implements DevSettingsUISyncSource {

    private static final String TAG = "AndroidDevSettingsUISyncSource";

    private static final int TOP_PADDING = 5;
    private static final int LEFT_PADDING = 5;
    private static final int BOTTOM_PADDING = 5;
    private static final int RIGHT_PADDING = 5;
    private static final int TITLE_LEFT_PADDING = 2;
    private static final int TITLE_BOTTOM_PADDING = 2;

    protected AppSyncSource appSyncSource;
    protected Localization loc;
    protected Bitmap disabledIcon;
    protected Bitmap enabledIcon;
    protected TextView titleTextView;
    protected EditText remoteUriEditText;
    protected boolean remoteUriSet = true;
    protected LinearLayout mainLayout;
    protected LinearLayout remoteUriLayout;
    protected TextView titleRemoteUri;

    protected String originalRemoteUri;

    public AndroidDevSettingsUISyncSource(Activity activity) {

        super(activity);

        AndroidController ac = AndroidController.getInstance();
        loc = ac.getLocalization();

        titleTextView  = new TextView(activity, null, R.style.sync_title);
        titleTextView.setPadding(adaptSizeToDensity(TITLE_LEFT_PADDING), 0, 0,
                adaptSizeToDensity(TITLE_BOTTOM_PADDING));
        titleTextView.setTextAppearance(activity, R.style.funambol_title);

        // Create a linear layout for the remote uri label and its value
        remoteUriLayout = new LinearLayout(activity);
        remoteUriLayout.setOrientation(LinearLayout.HORIZONTAL);
        remoteUriLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));

        titleRemoteUri = new TextView(activity, null, R.style.funambol_standard_text);
        titleRemoteUri.setText(loc.getLanguage("source_remote_db_name"));
        titleRemoteUri.setPadding(adaptSizeToDensity(2), 0, adaptSizeToDensity(4), 0);
        remoteUriLayout.addView(titleRemoteUri);

        remoteUriEditText = new EditText(activity);
        remoteUriEditText.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));
        remoteUriLayout.addView(remoteUriEditText);
    }

    public void setEnabledIcon(Bitmap image) {
        enabledIcon = image;
    }

    public void setDisabledIcon(Bitmap image) {
        disabledIcon = image;
    }

    public void setTitle(String title) {
        titleTextView.setText(title);
    }

    public void setRemoteUri(String remoteUri) {
        originalRemoteUri = remoteUri;
        remoteUriEditText.setText(remoteUri);
        remoteUriSet = true;
    }

    public String getRemoteUri() {
        return new StringBuilder(remoteUriEditText.getText()).toString();
    }

    public boolean hasChanges() {
        return ( (remoteUriSet && (!getRemoteUri().equals(originalRemoteUri))) );
    }

    public void loadSettings(Configuration configuration) {

        String remoteUri = appSyncSource.getConfig().getUri();
        setRemoteUri(remoteUri);
    }

    public void saveSettings(Configuration conf) {
        String remoteUri = getRemoteUri();

        AppSyncSourceConfig config = appSyncSource.getConfig();
        SyncSource source = appSyncSource.getSyncSource();

        SourceConfig sc = source.getConfig();
        sc.setRemoteUri(remoteUri);
        config.setUri(remoteUri);

        // Update the current value
        originalRemoteUri = remoteUri;
    }

    /**
     * @return the AppSyncSource this item represents
     */
    public AppSyncSource getSource() {
        return appSyncSource;
    }

    /**
     * Set the AppSyncSource this item represents
     *
     * @param source
     */
    public void setSource(AppSyncSource source) {
        appSyncSource = source;
    }

    public void layout() {

        // Sets the linear layout for the icon and the title
        LinearLayout ll1 = new LinearLayout(getContext());

        // All items in ll1 are vertically centered
        ll1.setGravity(Gravity.CENTER_VERTICAL);
        ll1.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT,
                    LayoutParams.WRAP_CONTENT));
        //ll1.addView(sourceIconView);
        ll1.addView(titleTextView);

        // Container layout for all the items
        if (mainLayout == null) {
            mainLayout = new LinearLayout(getContext());
            mainLayout.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT,
                        LayoutParams.WRAP_CONTENT));
            mainLayout.setPadding(adaptSizeToDensity(LEFT_PADDING),
                                  adaptSizeToDensity(TOP_PADDING),
                                  adaptSizeToDensity(RIGHT_PADDING),
                                  adaptSizeToDensity(BOTTOM_PADDING));
            mainLayout.setOrientation(LinearLayout.VERTICAL);
        }

        mainLayout.addView(ll1);

        // Add the remote uri setting
        AndroidCustomization customization = AndroidCustomization.getInstance();
        Configuration configuration = App.i().getAppInitializer().getConfiguration();
        if (   remoteUriSet && customization.isSourceUriVisible()
            && customization.syncUriEditable())
        {
            mainLayout.addView(remoteUriLayout);
        }

        this.addView(mainLayout);
    }

    private int adaptSizeToDensity(int size) {
        return (int)(size*getContext().getResources().getDisplayMetrics().density);
    }
}


