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

import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.funambol.client.controller.AboutScreenController;
import com.funambol.client.controller.Controller;
import com.funambol.client.customization.Customization;
import com.funambol.client.ui.AboutScreen;
import com.funambol.client.ui.Bitmap;

import de.chbosync.android.syncmlclient.R;
import de.chbosync.android.syncmlclient.AndroidUtils;
import de.chbosync.android.syncmlclient.App;
import de.chbosync.android.syncmlclient.AppInitializer;
import de.chbosync.android.syncmlclient.controller.AndroidController;


/**
 * About Activity (showing license information).
 * Contains inner class <tt>CloseListener</tt>.
 */
public class AndroidAboutScreen extends Activity implements AboutScreen {
	
    private AppInitializer initializer;

    private AboutScreenController aboutScreenController;

    private TextView  copyTitle;
    private TextView  copyText;
    private TextView  company;
    private TextView  copyUrl;
    private TextView  license;
    private TextView  poweredBy;
    private ImageView poweredByLogo;
    private TextView  portalInfo;

    
    /** 
     * Lifecycle method, called when the about screen is to be shown for the first time. 
     */
    @Override
    public void onCreate(Bundle icicle) { 
        super.onCreate(icicle);
        setContentView(R.layout.about);

        copyTitle     = (TextView)  findViewById(R.id.aboutCopyTitle   );
        copyText      = (TextView)  findViewById(R.id.aboutCopyText    );
        copyUrl       = (TextView)  findViewById(R.id.aboutCopyUrl     );
        license       = (TextView)  findViewById(R.id.aboutLicense     );
        poweredBy     = (TextView)  findViewById(R.id.poweredBy        );
        poweredByLogo = (ImageView) findViewById(R.id.poweredByLogo    );
        company       = (TextView)  findViewById(R.id.aboutCompanyName );
        portalInfo    = (TextView)  findViewById(R.id.aboutPortalInfo  );        
                

        Button closeButton = ((Button) findViewById(R.id.aboutClose));
        closeButton.setOnClickListener( new CloseListener() );
       
        
        // for ChBoSync: HTML formatting for text in TextView element.
        // String resource defined using CDATA block.
        TextView basedOnTextView = (TextView)  findViewById(R.id.basedOnPtbvAndFunambolText);
        basedOnTextView.setText( Html.fromHtml( getString(R.string.basedOnPtbvAndFunambol)) );
        
                        
        // Initialize the view for this controller
        initializer = App.i().getAppInitializer();
        Controller cont = AndroidController.getInstance();
        Customization customization = initializer.getCustomization();
        aboutScreenController = new AboutScreenController(cont, this, customization);
        aboutScreenController.addNecessaryFields();        
    }
    
    
    /**
     * Method to check if an intent can be handled by the current device
     * (if intent cannot be handled then the app would crash when the intent
     * is dispatched).
     * Method added for ChBoSync.
     * 
     * @param intent Intent to be checked
     * @return <tt>true</tt> if the <i>intent</i> can be handled by at least
     *         one app on the current device, <tt>false</tt> otherwise.
     */
	public boolean isIntentToOpenUrlInBrowserSupported(Intent intent) {
		
		final int flags = PackageManager.MATCH_DEFAULT_ONLY;
		
		PackageManager packageManager = this.getPackageManager();
						
		List<ResolveInfo> list = packageManager.queryIntentActivities(intent, flags); 
									 								
		// If at least one app was found that can handle the intent then this method returns "true"
		return list.size() > 0;		
	}	    
	

	/**
	 * @return Self-reference (this) of this activity.
	 */
    public Object getUiScreen() {
        return this;
    }

    
    /**
     * Adds version of this app to app's name (changed for ChBoSync).
     */
    public void addApplicationName(String name) {
    		    	
		String appVersionStr = AndroidUtils.getVersionNumberFromManifest(this);
    	
        copyTitle.setText(name + " " + appVersionStr );
        copyTitle.setVisibility(View.VISIBLE);
    }

    public void addCompanyName(String companyName) {
        company.setText(companyName);
        company.setVisibility(View.VISIBLE);
    }

    public void addCopyright(String copyright) {
        copyText.setText(copyright);
        copyText.setVisibility(View.VISIBLE);
    }

    public void addWebAddress(String url) {
        copyUrl.setText(url);
        copyUrl.setVisibility(View.VISIBLE);
    }

    public void addLicence(String license) {
        this.license.setText(license);
        this.license.setVisibility(View.VISIBLE);
    }

    public void addPoweredBy(String poweredBy) {
        this.poweredBy.setText(poweredBy);
        this.poweredBy.setVisibility(View.VISIBLE);
    }
    

    public void addPortalInfo(String portalInfo) {
        this.portalInfo.setText(portalInfo);
        this.portalInfo.setVisibility(View.VISIBLE);
    }

    public void addPoweredByLogo(Bitmap logo) {
        Integer id = (Integer)logo.getOpaqueDescriptor();
        poweredByLogo.setImageResource(id.intValue());
        poweredByLogo.setVisibility(View.VISIBLE);
    }

    public void close() {
        finish();
    }

    /**
     * A call-back for when the user presses the close button.
     */
    private class CloseListener implements OnClickListener {
        public void onClick(View v) {
            aboutScreenController.close();
        }
    }

}
