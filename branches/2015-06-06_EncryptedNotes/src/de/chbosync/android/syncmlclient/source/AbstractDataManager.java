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

package de.chbosync.android.syncmlclient.source;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Vector;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Context;

import com.funambol.syncml.protocol.PropParam;
import com.funambol.syncml.protocol.Property;
import com.funambol.util.Log;
import com.funambol.util.StringUtil;

import de.chbosync.android.syncmlclient.AndroidAccountManager;


public abstract class AbstractDataManager<E> {

    private static final String TAG_LOG = "AbstractDataManager";

    protected Context context = null;
    
    protected ContentResolver resolver = null;

    protected String accountType = null;
    protected String accountName = null;

    protected Vector<String> supportedFields = null;
    
    
    public AbstractDataManager(Context context) {
        this.context = context;
        resolver = context.getContentResolver();
        initAccount();
    }

    /**
     * @return the specific authority for this source
     */
    protected abstract String getAuthority();

    /**
     * Loads a generic item, given the key.
     * 
     * @param key
     * @return
     * @throws IOException
     */
    public abstract E load(String key) throws IOException;

    /**
     * Adds a generic item to the source.
     * 
     * @param item
     * @return
     * @throws IOException
     */
    public abstract String add(E item) throws IOException;

    /**
     * Updates a existing item.
     * 
     * @param id
     * @param newItem
     * @throws IOException
     */
    public abstract void update(String id, E newItem) throws IOException;


    /**
     * Deletes a specific item given the id.
     * 
     * @param id
     * @throws IOException
     */
    public abstract void delete(String id) throws IOException;

    /**
     * Deletes all the items from this source
     * 
     * @throws IOException
     */
    public abstract void deleteAll() throws IOException;

    /**
     * Checks if and item exists.
     * 
     * @param id
     * @return
     */
    public abstract boolean exists(String id);

    /**
     * @return an <code>Enumeration</code> containing all the items keys.
     * 
     * @throws IOException
     */
    public abstract Enumeration getAllKeys() throws IOException;

    /**
     * This method guarantees that items added/updated/deleted are really persisted
     */
    public abstract Vector commit() throws IOException;

    public void beginTransaction() {
    }

    /**
     * @return the total number of items (cardinality of getAllKeys) or -1 if
     * unknown
     */
    public int getAllCount() throws IOException {
        return -1;
    }

    /**
     * @return a Vector containing all the supported properties. They are used
     * to fill the client capabilities for this specific source.
     */
    public Vector<Property> getSupportedProperties() {
        return null;
    }

    /**
     * Coverts the Property Vector returned by getSupportedProperties into a
     * String Vector containing the property's name and params.
     * 
     * @return a Vector containing all the supported fields.
     */
    @SuppressWarnings("unchecked")
    public Vector<String> getSupportedFields() {
        if(supportedFields == null) {
            supportedFields = new Vector<String>();
            Vector<Property> properties = getSupportedProperties();
            if(properties != null) {
                for(int i=0; i<properties.size();i++) {
                    Property property = properties.get(i);
                    deriveFieldFromProperty(property, supportedFields, true);
                }
            }
        }
        return copyOf(supportedFields);
    }

    /**
     * This is a default implementation to retrieve a fieldname from a CTCap
     * property.
     */
    protected void deriveFieldFromProperty(Property property, Vector supportedFields, boolean includeBasicProperty) {

        if (includeBasicProperty) {
            supportedFields.addElement(property.getPropName());
        }

        Vector<PropParam> params = property.getPropParams();

        if (params != null) {
            for(int j=0; j<params.size();j++) {
                PropParam param = (PropParam)params.get(j);
                if ("TYPE".equals(param.getParamName())) {
                    Vector enumValues = param.getValEnums();
                    if (enumValues != null) {
                        for(int k=0;k<enumValues.size();++k) {
                            StringBuffer field = new StringBuffer(property.getPropName());
                            String value = (String)enumValues.get(k);
                            value = StringUtil.replaceAll(value, ",", ";");
                            field.append(";").append(value);
                            supportedFields.addElement(field.toString());
                        }
                    }
                }
            }
        }
    }

    protected Vector<String> copyOf(Vector<String> fields) {
        Vector<String> res = new Vector<String>();
        res.setSize(fields.size());
        for(int i=0; i<fields.size(); i++) {
            res.setElementAt(fields.elementAt(i), i);
        }
        return res;
    }
    
    /**
     * Initializes the account information
     */
    public void initAccount() {
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Initializing");
        }
        Account account = AndroidAccountManager.getNativeAccount(context);
        if (account != null) {
            if (Log.isLoggable(Log.INFO)) {
                Log.info(TAG_LOG, "Account found " + accountType + "," + accountName);
            }
            accountName = account.name;
            accountType = account.type;
        }
    }

}
