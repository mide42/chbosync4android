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

package de.chbosync.android.syncmlclient.source.pim.contact;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;

import com.funambol.common.pim.model.common.Property;
import com.funambol.common.pim.model.common.XTag;
import com.funambol.common.pim.model.contact.Address;
import com.funambol.common.pim.model.contact.BusinessDetail;
import com.funambol.common.pim.model.contact.Email;
import com.funambol.common.pim.model.contact.Name;
import com.funambol.common.pim.model.contact.Note;
import com.funambol.common.pim.model.contact.PersonalDetail;
import com.funambol.common.pim.model.contact.Phone;
import com.funambol.common.pim.model.contact.Photo;
import com.funambol.common.pim.model.contact.Title;
import com.funambol.common.pim.model.contact.WebPage;
import com.funambol.sync.ItemStatus;
import com.funambol.syncml.protocol.PropParam;
import com.funambol.util.Log;
import com.funambol.util.StringUtil;

import de.chbosync.android.syncmlclient.R;
import de.chbosync.android.syncmlclient.AndroidCustomization;
import de.chbosync.android.syncmlclient.source.AbstractDataManager;


public abstract class ContactManager extends AbstractDataManager<Contact> {

    private static final String TAG_LOG = "ContactManager";

    private static final Uri RAW_CONTACT_URI = ContactsContract.RawContacts.CONTENT_URI;

    public static final String CONTACTS_AUTHORITY = "com.android.contacts";

    private static final String FUNAMBOL_SOURCE_ID_PREFIX = "chbosync-";

    private static final int MAX_OPS_PER_BATCH = 499;

    private static final int COMMIT_THRESHOLD = MAX_OPS_PER_BATCH - 80;

    protected AndroidCustomization customization = AndroidCustomization.getInstance();

    // TODO FIXME: get this from the server caps
    protected boolean multipleFieldsSupported = false;

    protected boolean preferredFieldsSupported = false;

    protected boolean callerIsSyncAdapter = true;

    protected int allItemsCount = Integer.MIN_VALUE;

    protected ArrayList<ContentProviderOperation> ops;

    protected int lastAddBackReferenceId = 0;

    protected Vector newKeys = null;
    protected ArrayList<Integer> rawContactIdx = null;


    // Constructors------------------------------------------------
    public ContactManager(Context context) {
        this(context, true);
    }

    public ContactManager(Context context, boolean callerIsSyncAdapter) {
        super(context);
        this.callerIsSyncAdapter = callerIsSyncAdapter;
    }

    public void beginTransaction() {
        newKeys = new Vector();
        ops = new ArrayList<ContentProviderOperation>();
        rawContactIdx = new ArrayList<Integer>();
    }

    protected String getAuthority() {
        return CONTACTS_AUTHORITY;
    }

    public Contact load(String key) throws IOException {

        HashMap<String,List<Integer>> fieldsMap = new HashMap<String, List<Integer>>();
        
        return load(key, fieldsMap);
    }

    private Contact load(String key, HashMap<String, List<Integer>> fieldsMap) 
            throws IOException {

        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Loading contact: " + key);
        }

        // Set the id for this contact
        Contact contact = new Contact();
        contact.setId(key);

        // Load all pieces of information
        loadAllFields(contact, fieldsMap);

        return contact;
    }

    /**
     * Add an item into the store. The operation is actually not committed and must be encapsulated into
     * a transaction. In other words the call must be performed after a {@link beginTransaction} and before
     * a {@link commit}.
     * @param item the item to be committed
     * @return null. The key of the new item is not returned here but by the commit method.
     * @throws IOException
     */
    @Override
    public String add(Contact item) throws IOException {
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Saving contact");
        }

        // Commit if it is time to do it
        if (ops.size() >= COMMIT_THRESHOLD) {
            commitSingleBatch();
        }

        Uri uri = addCallerIsSyncAdapterFlag(RAW_CONTACT_URI);

        // This is the first insert into the raw contacts table
        ContentProviderOperation i1 = ContentProviderOperation.newInsert(uri)
                                      .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType)
                                      .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, accountName)
                                      .build();
        ops.add(i1);
        // Save the backreference for the raw contact id
        lastAddBackReferenceId = ops.size() - 1;
        rawContactIdx.add(new Integer(lastAddBackReferenceId));

        prepareAllFields(item, null, ops);

        // At this point this contact cannot have a valid id. Make sure this is true.
        //item.setId(-1);

        return null;
    }

    /**
     * Update an item into the store. The operation is actually not committed and must be encapsulated into
     * a transaction. In other words the call must be performed after a {@link beginTransaction} and before
     * a {@link commit}.
     * @param key the identified of the item to change
     * @param item the new item
     * @throws IOException
     */
    @Override
    public void update(String key, Contact item) throws IOException {

        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Updating contact: " + key);
        }

        long rawContactId;
        try {
            rawContactId = Long.parseLong(key);
        } catch(Exception e) {
            Log.error(TAG_LOG, "Invalid item key " + key, e);
            throw new IOException("Invalid item key");
        }

        // If the contact does not exist, then we perform an add
        if (!exists(key)) {
            if (Log.isLoggable(Log.INFO)) {
                Log.info(TAG_LOG, "Tried to update a non existing contact. Creating a new one ");
            }
            add(item);
            return;
        }

        // Commit if it is time to do it
        if (ops.size() >= COMMIT_THRESHOLD) {
            commitSingleBatch();
        }

        // Set the id
        item.setId(rawContactId);

        // Load the old contact and fill the fields map
        HashMap<String,List<Integer>> fieldsMap = new HashMap<String, List<Integer>>();
        load(key, fieldsMap);

        prepareAllFields(item, fieldsMap, ops);
    }

    /**
     * Delete an item from the store. The operation is actually not committed and must be encapsulated into
     * a transaction. In other words the call must be performed after a {@link beginTransaction} and before
     * a {@link commit}.
     * @param key the identifier of the item to remove
     * @throws IOException
     */
    @Override
    public void delete(String key) throws IOException {

        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Deleting contact: " + key);
        }

        if (ops.size() >= COMMIT_THRESHOLD) {
            commitSingleBatch();
        }

        long rawContactId;
        try {
            rawContactId = Long.parseLong(key);
        } catch (Exception e) {
            Log.error(TAG_LOG, "Invalid item id " + key, e);
            throw new IOException("Invalid item id");
        }

        // If we are in a transaction then we just prepare the delete operation,
        // otherwise we actually perform it
        prepareHardDelete(rawContactId);
    }

    /**
     * Permanently remove the given contact from the store. A transaction is not required for
     * this operation to succeed.
     * @param rawContactId the contact id in the raw_contacts table
     * @return the number of deleted rows
     */
    public int hardDelete(long rawContactId) {

        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Hard deleting contact: " + rawContactId);
        }

        Uri uri = addCallerIsSyncAdapterFlag(RAW_CONTACT_URI);

        // Delete from raw_contacts (related rows in Data table are
        // automatically deleted)
        return resolver.delete(uri,
                ContactsContract.RawContacts._ID+"="+rawContactId, null);
    }

    @Override
    public void deleteAll() throws IOException {

        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Deleting all contacts");
        }

        Uri uri = addCallerIsSyncAdapterFlag(RAW_CONTACT_URI);
        
        // Delete from raw_contacts (related rows in Data table are
        // automatically deleted)
        // Note: delete only contacts from funambol accounts
        int count = resolver.delete(uri,
                ContactsContract.RawContacts.ACCOUNT_TYPE+"='"+accountType+"'", null);

        if (Log.isLoggable(Log.DEBUG)) {
            Log.debug(TAG_LOG, "Deleted contacts count: " + count);
        }
        if (count < 0) {
            Log.error(TAG_LOG, "Cannot delete all contacts");
            throw new IOException("Cannot delete contacts");
        }
    }

    @Override
    public boolean exists(String key) {
        
        long id;
        try {
            id = Long.parseLong(key);
        } catch (Exception e) {
            Log.error(TAG_LOG, "Invalid item key " + key, e);
            return false;
        }

        String cols[] = {ContactsContract.RawContacts._ID, ContactsContract.RawContacts.DELETED};
        Uri uri = ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, id);
        Cursor cur = resolver.query(uri, cols, null, null, null);

        boolean found;
        if (!cur.moveToFirst()) {
            found = false;
        } else {
            int deleted = cur.getInt(1);
            if (deleted == 0) {
                found = true;
            } else {
                found = false;
            }
        }

        cur.close();
        return found;
    }

    @Override
    public Enumeration getAllKeys() throws IOException {

        Cursor peopleCur = getContactsCursor();

        try {
            int contactListSize = peopleCur.getCount();
            Vector<String> itemKeys = new Vector<String>(contactListSize);

            if (!peopleCur.moveToFirst()) {
                return itemKeys.elements();
            }

            for (int i = 0; i < contactListSize; i++) {
                String key = peopleCur.getString(0);
                if (Log.isLoggable(Log.TRACE)) {
                    Log.trace(TAG_LOG, "Found item with key: " + key);
                }
                itemKeys.addElement(key);
                peopleCur.moveToNext();
            }

            // Update the all items count
            allItemsCount = contactListSize;

            return itemKeys.elements();
        } catch (Exception e) {
            Log.error(TAG_LOG, "Cannot get all items keys: ", e);
            throw new IOException("Cannot get all items keys");
        } finally {
            peopleCur.close();
        }
    }

    @Override
    public int getAllCount() throws IOException {
        if (Integer.MIN_VALUE != allItemsCount) {
            //variable already initialized
            return allItemsCount;
        }
        
        int items = 0;
        Cursor peopleCur = getContactsCursor();
        try {
            items = peopleCur.getCount();
        } catch (Exception e) {
            Log.error(TAG_LOG, "Cannot get all items keys: ", e);
            throw new IOException("Cannot get all items keys");
        } finally {
            peopleCur.close();
        }
        return items;
    }

    public void refreshSourceIdAndDirtyFlag(Vector itemsStatus) throws IOException {

         if(Log.isLoggable(Log.TRACE)) {
             Log.trace(TAG_LOG, "Refreshing source id and dirty flag for groups of contacts");
         }
         ArrayList<ContentProviderOperation> ops2 = new ArrayList<ContentProviderOperation>();
         for(int i=0;i<itemsStatus.size();++i) {
             ItemStatus itemStatus = (ItemStatus)itemsStatus.elementAt(i);
             String key = itemStatus.getKey();
             long id = Long.parseLong(key);
             prepareSourceIdAndDirtyFlagOperation(id, ops2);
         }
         try {
             // Apply all the operations to set the sourceId and the dirty flag
             resolver.applyBatch(ContactsContract.AUTHORITY, ops2);
        } catch (Exception e) {
            Log.error(TAG_LOG, "Cannot set items status", e);
            throw new IOException("Cannot set items status");
        }
    }


    public void refreshSourceIdAndDirtyFlag(long contactId) {
        if(Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Refreshing source id and dirty flag for contact: " + contactId);
        }
        Uri uri = ContentUris.withAppendedId(RAW_CONTACT_URI, contactId);
        uri = addCallerIsSyncAdapterFlag(uri);

        ContentValues cv = new ContentValues();
        cv.put(ContactsContract.RawContacts.SOURCE_ID,
                FUNAMBOL_SOURCE_ID_PREFIX + contactId);
        cv.put(ContactsContract.RawContacts.DIRTY, 0);
        resolver.update(uri, cv, null, null);
    }

    public void setPreferredFieldsSupported(boolean preferredFieldsSupported) {
        this.preferredFieldsSupported = preferredFieldsSupported;
    }

    public void setMultipleFieldsSupported(boolean multipleFieldsSupported) {
        this.multipleFieldsSupported = multipleFieldsSupported;
    }

    public Vector commit() throws IOException {
        commitSingleBatch();
        return newKeys;
    }


    protected void addProperty(Vector<com.funambol.syncml.protocol.Property>
            properties, String propName, String[] values, PropParam[] propParams,
            String displayName) {
        addProperty(properties, propName, values, propParams, displayName, 0, 0);
    }

    protected void addProperty(Vector<com.funambol.syncml.protocol.Property>
            properties, String propName, String[] values, PropParam[] propParams) {
        addProperty(properties, propName, values, propParams, null, 0, 0);
    }

    protected void addProperty(Vector<com.funambol.syncml.protocol.Property>
            properties, String propName, String[] values, PropParam[] propParams,
            int maxOccur, int maxSize) {
        addProperty(properties, propName, values, propParams, null, maxOccur, maxSize);
    }

    protected void addProperty(Vector<com.funambol.syncml.protocol.Property>
            properties, String propName, String[] values, PropParam[] propParams,
            String displayName, int maxOccur, int maxSize) {
        if(values == null) {
            values = new String[0];
        }
        if(propParams == null) {
            propParams = new PropParam[0];
        }
        properties.add(new com.funambol.syncml.protocol.Property(propName, 
                null /* We don't specify the property data type    */,
                maxOccur,
                maxSize,
                false, values, 
                displayName /* We don't specify the property display name */,
                propParams));
    }

    protected void loadAllFields(Contact contact, HashMap<String,List<Integer>> fieldsMap)
            throws IOException {
        
        long id = contact.getId();
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Loading all fields for: " + id);
        }

        PersonalDetail pd = contact.getPersonalDetail();
        if (pd == null) {
            pd = new PersonalDetail();
            contact.setPersonalDetail(pd);
        }
        BusinessDetail bd = contact.getBusinessDetail();
        if (bd == null) {
            bd = new BusinessDetail();
            contact.setBusinessDetail(bd);
        }

        Cursor allFields = resolver.query(ContactsContract.Data.CONTENT_URI, null,
                ContactsContract.Data.RAW_CONTACT_ID+"="+id, null, null);

        // Move to first element
        if (!allFields.moveToFirst()) {
            if(!exists("" + id)) {
                throw new IOException("Cannot find person " + id);
            } else {
                // The contact exists but there is nothing to load
                return;
            }
        }

        loadFromCursor(contact, allFields, fieldsMap);
    }

    protected void loadFromCursor(Contact contact, Cursor cur,
            HashMap<String,List<Integer>> fieldsMap) throws IOException {

        try {
            do {
                String mimeType = cur.getString(cur.getColumnIndexOrThrow(
                        ContactsContract.Data.MIMETYPE));
                if (Log.isLoggable(Log.TRACE)) {
                    Log.trace(TAG_LOG, "Found a raw of type: " + mimeType);
                }

                if (CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    loadNameField(contact, cur, fieldsMap);
                } else if (CommonDataKinds.Nickname.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    loadNickNameField(contact, cur, fieldsMap);
                } else if (CommonDataKinds.Phone.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    loadPhoneField(contact, cur, fieldsMap);
                } else if (CommonDataKinds.Email.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    loadEmailField(contact, cur, fieldsMap);
                } else if (CommonDataKinds.Photo.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    loadPhotoField(contact, cur, fieldsMap);
                } else if (CommonDataKinds.Organization.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    loadOrganizationField(contact, cur, fieldsMap);
                } else if (CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    loadPostalAddressField(contact, cur, fieldsMap);
                } else if (CommonDataKinds.Event.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    loadEventField(contact, cur, fieldsMap);
                } else if (CommonDataKinds.Im.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    loadImField(contact, cur, fieldsMap);
                } else if (CommonDataKinds.Note.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    loadNoteField(contact, cur, fieldsMap);
                } else if (CommonDataKinds.Website.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    loadWebsiteField(contact, cur, fieldsMap);
                } else if (CommonDataKinds.Relation.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    loadRelationField(contact, cur, fieldsMap);
                } else if (AdditionalDataKinds.UID.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    loadUidField(contact, cur, fieldsMap);
                } else if (AdditionalDataKinds.TimeZone.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    loadTimeZoneField(contact, cur, fieldsMap);
                } else if (AdditionalDataKinds.Revision.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    loadRevisionField(contact, cur, fieldsMap);
                } else if (AdditionalDataKinds.Geo.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    loadGeoField(contact, cur, fieldsMap);
                } else if (AdditionalDataKinds.Ext.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    loadExtField(contact, cur, fieldsMap);
                } else {
                    loadCustomField(mimeType, contact, cur, fieldsMap);
                }
            } while(cur.moveToNext());
        } catch (Exception e) {
            Log.error(TAG_LOG, "Cannot load contact ", e);
            throw new IOException("Cannot load contact");
        } finally {
            cur.close();
        }
    }

    private void prepareAllFields(Contact contact,
                                  HashMap<String, List<Integer>> fieldsMap,
                                  List<ContentProviderOperation> ops)
    {
        prepareName          (contact, fieldsMap, ops);
        prepareNickname      (contact, fieldsMap, ops);
        preparePhones        (contact, fieldsMap, ops);
        prepareEmail         (contact, fieldsMap, ops);
        prepareIm            (contact, fieldsMap, ops);
        preparePhoto         (contact, fieldsMap, ops);
        prepareOrganization  (contact, fieldsMap, ops);
        preparePostalAddress (contact, fieldsMap, ops);
        prepareEvent         (contact, fieldsMap, ops);
        prepareNote          (contact, fieldsMap, ops);
        prepareWebsite       (contact, fieldsMap, ops);
        prepareRelation      (contact, fieldsMap, ops);
        prepareUid           (contact, fieldsMap, ops);
        prepareTimeZone      (contact, fieldsMap, ops);
        prepareRevision      (contact, fieldsMap, ops);
        prepareGeo           (contact, fieldsMap, ops);
        prepareExtFields     (contact, fieldsMap, ops);
        prepareCustomFields  (contact, fieldsMap, ops);
    }

    private void appendFieldId(HashMap<String,List<Integer>> fieldsMap,
                               String key, int rowId)
    {
        List<Integer> l = fieldsMap.get(key);
        if (l == null) {
            l = new ArrayList<Integer>();
            fieldsMap.put(key, l);
        }
        l.add(new Integer(rowId));
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Appended fieldId " + rowId + " for " + key);
        }
    }

    /**
     * Retrieve the People fields from a Cursor
     */
    protected void loadNameField(Contact contact, Cursor cur, HashMap<String,List<Integer>> fieldsMap) {

        long id = contact.getId();
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Loading name for: " + id);
        }

        Name nameField = contact.getName();
        if(nameField == null) {
            nameField = new Name();
        }
        String dn = cur.getString(cur.getColumnIndexOrThrow(CommonDataKinds.StructuredName.DISPLAY_NAME));
        if (dn != null && customization.isDisplayNameSupported()) {
            // setting firstName and lastName from the combined name
            Property dnProp = new Property(dn);
            nameField.setDisplayName(dnProp);
        }
        String firstName = cur.getString(cur.getColumnIndexOrThrow(CommonDataKinds.StructuredName.GIVEN_NAME));
        if (firstName != null) {
            Property firstNameProp = new Property(firstName);
            nameField.setFirstName(firstNameProp);
        }
        String middleName = cur.getString(cur.getColumnIndexOrThrow(CommonDataKinds.StructuredName.MIDDLE_NAME));
        if (middleName != null) {
            Property middleNameProp = new Property(middleName);
            nameField.setMiddleName(middleNameProp);
        }
        String lastName = cur.getString(cur.getColumnIndexOrThrow(CommonDataKinds.StructuredName.FAMILY_NAME));
        if (lastName != null) {
            Property lastNameProp = new Property(lastName);
            nameField.setLastName(lastNameProp);
        }
        String prefixName = cur.getString(cur.getColumnIndexOrThrow(CommonDataKinds.StructuredName.PREFIX));
        if (prefixName != null) {
            Property prefixNameProp = new Property(prefixName);
            nameField.setSalutation(prefixNameProp);
        }
        String suffixName = cur.getString(cur.getColumnIndexOrThrow(CommonDataKinds.StructuredName.SUFFIX));
        if (suffixName != null) {
            Property suffixNameProp = new Property(suffixName);
            nameField.setSuffix(suffixNameProp);
        }
        contact.setName(nameField);

        loadFieldToMap(CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, 0, cur, fieldsMap);
    }

    /**
     * Retrieve the NickName field from a Cursor
     */
    protected void loadNickNameField(Contact contact, Cursor cur, HashMap<String,List<Integer>> fieldsMap) {
        Log.error(TAG_LOG, "Nickname fields are not supported");
    }


    /**
     * Retrieve the Phone fields from a Cursor
     */
    protected void loadPhoneField(Contact contact, Cursor cur,
            HashMap<String, List<Integer>> fieldsMap) {

        long id = contact.getId();
        if (Log.isLoggable(Log.DEBUG)) {
            Log.debug(TAG_LOG, "Load Phone Field for: " + id);
        }

        PersonalDetail pd = contact.getPersonalDetail();
        BusinessDetail bd = contact.getBusinessDetail();

        String number = cur.getString(cur.getColumnIndexOrThrow(CommonDataKinds.Phone.NUMBER));
        String label = null;
        int phoneType = cur.getInt(cur.getColumnIndexOrThrow(CommonDataKinds.Phone.TYPE));

        String fieldId = createFieldId(new Object[] {
            CommonDataKinds.Phone.CONTENT_ITEM_TYPE, phoneType, label });
        List<Integer> items = fieldsMap.get(fieldId);
        int idx = items != null ? items.size() + 1 : 1;

        Phone phone = new Phone(number);
        if(preferredFieldsSupported) {
            boolean preferred = cur.getInt(cur.getColumnIndexOrThrow(
                    CommonDataKinds.Phone.IS_PRIMARY)) != 0;
            phone.setPreferred(preferred);
        }
        
        if (phoneType == CommonDataKinds.Phone.TYPE_HOME) {
            phone.setPhoneType(Phone.HOME_PHONE_NUMBER);
            pd.addPhone(phone);
        } else if (phoneType == CommonDataKinds.Phone.TYPE_WORK) {
            phone.setPhoneType(Phone.BUSINESS_PHONE_NUMBER);
            bd.addPhone(phone);
        } else if (phoneType == CommonDataKinds.Phone.TYPE_MOBILE) {
            phone.setPhoneType(Phone.MOBILE_PHONE_NUMBER);
            pd.addPhone(phone);
        } else if (phoneType == CommonDataKinds.Phone.TYPE_OTHER) {
            phone.setPhoneType(Phone.OTHER_PHONE_NUMBER);
            pd.addPhone(phone);
        } else if (phoneType == CommonDataKinds.Phone.TYPE_FAX_HOME) {
            phone.setPhoneType(Phone.HOME_FAX_NUMBER);
            pd.addPhone(phone);
        } else if (phoneType == CommonDataKinds.Phone.TYPE_FAX_WORK) {
            phone.setPhoneType(Phone.BUSINESS_FAX_NUMBER);
            bd.addPhone(phone);
        } else if (phoneType == CommonDataKinds.Phone.TYPE_PAGER) {
            phone.setPhoneType(Phone.PAGER_NUMBER);
            pd.addPhone(phone);
        } else if (phoneType == CommonDataKinds.Phone.TYPE_COMPANY_MAIN) {
            phone.setPhoneType(Phone.COMPANY_PHONE_NUMBER);
            bd.addPhone(phone);
        } else if (phoneType == CommonDataKinds.Phone.TYPE_OTHER_FAX) {
            phone.setPhoneType(Phone.OTHER_FAX_NUMBER);
            pd.addPhone(phone);
        } else if (phoneType == CommonDataKinds.Phone.TYPE_MAIN) {
            phone.setPhoneType(Phone.PRIMARY_PHONE_NUMBER);
            pd.addPhone(phone);
        } else if (phoneType == CommonDataKinds.Phone.TYPE_CUSTOM) {
            if (context.getString(R.string.label_work2_phone).equals(label)) {
                phone.setPhoneType(Phone.BUSINESS_PHONE_NUMBER);
                bd.addPhone(phone);
            } else {
                if (Log.isLoggable(Log.INFO)) {
                    Log.info(TAG_LOG, "Ignoring custom phone number: " + label);
                }
            }
        } else {
            Log.error(TAG_LOG, "Ignoring unknwon phone type: " + phoneType);
        }

        loadFieldToMap(CommonDataKinds.Phone.CONTENT_ITEM_TYPE, phoneType, label, cur, fieldsMap);
    }

    /**
     * Retrieve the email fields from a Cursor
     */
    protected void loadEmailField(Contact contact, Cursor cur, HashMap<String, List<Integer>> fieldsMap) {

        long id = contact.getId();
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Load Email Field for: " + id);
        }

        PersonalDetail pd = contact.getPersonalDetail();
        BusinessDetail bd = contact.getBusinessDetail();

        String email = cur.getString(cur.getColumnIndexOrThrow(CommonDataKinds.Email.DATA));

        if (StringUtil.isNullOrEmpty(email)) {
            if (Log.isLoggable(Log.DEBUG)) {
                Log.debug(TAG_LOG, "Ignoring null or empty email address");
            }
            return;
        }

        int emailType = cur.getInt(cur.getColumnIndexOrThrow(CommonDataKinds.Email.TYPE));

        Email emailObj = new Email(email);
        if(preferredFieldsSupported) {
            boolean preferred = cur.getInt(cur.getColumnIndexOrThrow(
                    CommonDataKinds.Email.IS_PRIMARY)) != 0;
            emailObj.setPreferred(preferred);
        }
        
        switch(emailType) {
            case CommonDataKinds.Email.TYPE_HOME:
            {
                emailObj.setEmailType(Email.HOME_EMAIL);
                pd.addEmail(emailObj);
                break;
            }
            case CommonDataKinds.Email.TYPE_WORK:
            {
                emailObj.setEmailType(Email.WORK_EMAIL);
                bd.addEmail(emailObj);
                break;
            }
            case CommonDataKinds.Email.TYPE_OTHER:
            {
                emailObj.setEmailType(Email.OTHER_EMAIL);
                pd.addEmail(emailObj);
                break;
            }
            default:
                Log.error(TAG_LOG, "Ignoring unknown email type: " + emailType);
        }

        loadFieldToMap(CommonDataKinds.Email.CONTENT_ITEM_TYPE, emailType, cur, fieldsMap);
    }

    /**
     * Retrieve the photo fields from a Cursor
     */
    protected void loadPhotoField(Contact contact, Cursor cur, HashMap<String, List<Integer>> fieldsMap) {

        long id = contact.getId();
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Loading Photo Field for: " + id);
        }

        PersonalDetail pd = contact.getPersonalDetail();

        byte data[] = cur.getBlob(cur.getColumnIndexOrThrow(CommonDataKinds.Photo.PHOTO));
        if (data != null) {
            if (Log.isLoggable(Log.TRACE)) {
                Log.trace(TAG_LOG, "This contact has a photo associated");
            }

            String type = detectImageFormat(data);
            if (Log.isLoggable(Log.TRACE)) {
                Log.trace(TAG_LOG, "Photo type: " + type);
            }

            Photo photo = new Photo();
            photo.setImage(data);
            photo.setType(type);
            if(preferredFieldsSupported) {
                boolean preferred = cur.getInt(cur.getColumnIndexOrThrow(
                        CommonDataKinds.Photo.IS_PRIMARY)) != 0;
                photo.setPreferred(preferred);
            }
            pd.addPhotoObject(photo);

            loadFieldToMap(CommonDataKinds.Photo.CONTENT_ITEM_TYPE, 0, cur, fieldsMap);
        }
    }

    /**
     * Detects the file format of the given image.
     * Supported formats are:
     *  image/bmp
     *  image/jpeg
     *  image/png
     *  image/gif
     * @param image
     * @return the image mime type or null if unknown
     */
    private String detectImageFormat(byte[] image) {

        final byte[] BMP_PREFIX  = {(byte)0x42, (byte)0x4D};
        final byte[] JPEG_PREFIX = {(byte)0xFF, (byte)0xD8, (byte)0xFF};
        final byte[] PNG_PREFIX  = {(byte)0x89, (byte)0x50, (byte)0x4E, (byte)0x47};
        final byte[] GIF_PREFIX  = {(byte)0x47, (byte)0x49, (byte)0x46, (byte)0x38};

        final String BMP_MIME_TYPE  = "image/bmp";
        final String JPEG_MIME_TYPE = "image/jpeg";
        final String PNG_MIME_TYPE  = "image/png";
        final String GIF_MIME_TYPE  = "image/gif";
        
        // Check image prefixes
        if(checkImagePrefix(image, BMP_PREFIX))  return BMP_MIME_TYPE;
        if(checkImagePrefix(image, JPEG_PREFIX)) return JPEG_MIME_TYPE;
        if(checkImagePrefix(image, PNG_PREFIX))  return PNG_MIME_TYPE;
        if(checkImagePrefix(image, GIF_PREFIX))  return GIF_MIME_TYPE;

        // Image type not detected
        return null;
    }

    private boolean checkImagePrefix(byte[] image, byte[] prefix) {
        if(image == null || prefix == null) {
            return false;
        }
        boolean match = true;
        for(int i=0; i<prefix.length; i++) {
            if(i < image.length) {
                match &= image[i] == prefix[i];
            } else {
                match = false;
                break;
            }
        }
        return match;
    }

    /**
     * Retrieve the Organization fields from a Cursor
     */
    protected void loadOrganizationField(Contact contact, Cursor cur,
                                       HashMap<String,List<Integer>> fieldsMap) throws IOException
    {
        long id = contact.getId();
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Load Organization Field for: " + id);
        }

        BusinessDetail bd = contact.getBusinessDetail();

        int orgType = cur.getInt(cur.getColumnIndexOrThrow(CommonDataKinds.Organization.TYPE));
        if (orgType == CommonDataKinds.Organization.TYPE_WORK) {
            int colId = cur.getColumnIndexOrThrow(CommonDataKinds.Organization.COMPANY);
            String company = cur.getString(colId);
            if (company != null) {
                Property companyProp = new Property(company);
                bd.setCompany(companyProp);
            }

            colId = cur.getColumnIndexOrThrow(CommonDataKinds.Organization.TITLE);
            String title = cur.getString(colId);
            if (title != null) {
                ArrayList titles = new ArrayList();
                Title titleProp = new Title(title);
                titles.add(titleProp);
                bd.setTitles(titles);
            }

            colId = cur.getColumnIndexOrThrow(CommonDataKinds.Organization.DEPARTMENT);
            String department = cur.getString(colId);
            if (department != null) {
                Property departmentProp = new Property(department);
                bd.setDepartment(departmentProp);
            }

            colId = cur.getColumnIndexOrThrow(CommonDataKinds.Organization.OFFICE_LOCATION);
            String location = cur.getString(colId);
            if (location != null) {
                bd.setOfficeLocation(location);
            }
        } else {
            if (Log.isLoggable(Log.INFO)) {
                Log.info(TAG_LOG, "Ignoring organization of type " + orgType);
            }
        }

        loadFieldToMap(CommonDataKinds.Organization.CONTENT_ITEM_TYPE, 0, cur, fieldsMap);
    }

    /**
     * Retrieve the postal address fields from a Cursor
     */
    protected void loadPostalAddressField(Contact contact, Cursor cur, HashMap<String, List<Integer>> fieldsMap) {

        long id = contact.getId();
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Load Address Fields for: " + id);
        }

        PersonalDetail pd = contact.getPersonalDetail();
        BusinessDetail bd = contact.getBusinessDetail();
        Address address = new Address();

        if(preferredFieldsSupported) {
            boolean preferred = cur.getInt(cur.getColumnIndexOrThrow(
                    CommonDataKinds.StructuredPostal.IS_PRIMARY)) != 0;
            address.setPreferred(preferred);
        }

        String city  = cur.getString(cur.getColumnIndexOrThrow(CommonDataKinds.StructuredPostal.CITY));
        if (city != null) {
            Property cityProp = new Property(city);
            address.setCity(cityProp);
        }
        String country  = cur.getString(cur.getColumnIndexOrThrow(CommonDataKinds.StructuredPostal.COUNTRY));
        if (country != null) {
            Property countryProp = new Property(country);
            address.setCountry(countryProp);
        }
        String pobox  = cur.getString(cur.getColumnIndexOrThrow(CommonDataKinds.StructuredPostal.POBOX));
        if (pobox != null) {
            Property poboxProp = new Property(pobox);
            address.setPostOfficeAddress(poboxProp);
        }
        String poCode  = cur.getString(cur.getColumnIndexOrThrow(CommonDataKinds.StructuredPostal.POSTCODE));
        if (poCode != null) {
            Property poCodeProp = new Property(poCode);
            address.setPostalCode(poCodeProp);
        }
        String region  = cur.getString(cur.getColumnIndexOrThrow(CommonDataKinds.StructuredPostal.REGION));
        if (region != null) {
            Property stateProp = new Property(region);
            address.setState(stateProp);
        }
        String street  = cur.getString(cur.getColumnIndexOrThrow(CommonDataKinds.StructuredPostal.STREET));
        if (street != null) {
            Property streetProp = new Property(street);
            address.setStreet(streetProp);
        }
        String extAddress  = cur.getString(cur.getColumnIndexOrThrow(CommonDataKinds.StructuredPostal.NEIGHBORHOOD));
        if (extAddress != null) {
            Property extAddressProp = new Property(extAddress);
            address.setExtendedAddress(extAddressProp);
        }

        int type  = cur.getInt(cur.getColumnIndexOrThrow(CommonDataKinds.StructuredPostal.TYPE));
        if (type == CommonDataKinds.StructuredPostal.TYPE_WORK) {
            address.setAddressType(Address.WORK_ADDRESS);
            bd.addAddress(address);
        } else if (type == CommonDataKinds.StructuredPostal.TYPE_HOME) {
            address.setAddressType(Address.HOME_ADDRESS);
            pd.addAddress(address);
        } else if (type == CommonDataKinds.StructuredPostal.TYPE_OTHER) {
            address.setAddressType(Address.OTHER_ADDRESS);
            pd.addAddress(address);
        } else {
            if (Log.isLoggable(Log.INFO)) {
                Log.info(TAG_LOG, "Ignoring other address");
            }
        }

        loadFieldToMap(CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE, type, cur, fieldsMap);
    }

    /**
     * Retrieve the Event field from a Cursor
     */
    protected void loadEventField(Contact contact, Cursor cur, HashMap<String,List<Integer>> fieldsMap) {

        long id = contact.getId();
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Loading event for: " + id);
        }

        PersonalDetail pd = contact.getPersonalDetail();

        String eventDate = cur.getString(cur.getColumnIndexOrThrow(CommonDataKinds.Event.START_DATE));
        int eventType = cur.getInt(cur.getColumnIndexOrThrow(CommonDataKinds.Event.TYPE));

        if (eventType == CommonDataKinds.Event.TYPE_BIRTHDAY) {
            pd.setBirthday(eventDate);
        } else if (eventType == CommonDataKinds.Event.TYPE_ANNIVERSARY) {
            pd.setAnniversary(eventDate);
        } else {
            Log.error(TAG_LOG, "Ignoring unknown event type: " + eventType);
        }

        loadFieldToMap(CommonDataKinds.Event.CONTENT_ITEM_TYPE, eventType, cur, fieldsMap);
    }

    /**
     * Retrieve the Im field from a Cursor. Shall be defined by subclasses in
     * order to support it.
     */
    protected void loadImField(Contact contact, Cursor cur, HashMap<String,List<Integer>> fieldsMap) {
        Log.error(TAG_LOG, "IM fields are not supported");
    }

    /**
     * Retrieve the Note field from a Cursor
     */
    protected void loadNoteField(Contact contact, Cursor cur, HashMap<String,List<Integer>> fieldsMap) {

        long id = contact.getId();
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Loading note for: " + id);
        }

        String note = cur.getString(cur.getColumnIndexOrThrow(CommonDataKinds.Note.NOTE));

        if (note != null) {
            // The device cannot display \r characters, so we add them back here
            note = StringUtil.replaceAll(note, "\n", "\r\n");
        }

        Note noteField = new Note();
        noteField.setPropertyValue(note);
        // Ensure the property type is set
        noteField.setPropertyType("");
        contact.addNote(noteField);

        loadFieldToMap(CommonDataKinds.Note.CONTENT_ITEM_TYPE, 0, cur, fieldsMap);
    }

    /**
     * Retrieve the Website field from a Cursor
     */
    protected void loadWebsiteField(Contact contact, Cursor cur, HashMap<String,List<Integer>> fieldsMap) {

        long id = contact.getId();
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Loading Website for: " + id);
        }

        PersonalDetail pd = contact.getPersonalDetail();
        BusinessDetail bd = contact.getBusinessDetail();
        
        String url = cur.getString(cur.getColumnIndexOrThrow(CommonDataKinds.Website.URL));
        int websiteType =  cur.getInt(cur.getColumnIndexOrThrow(CommonDataKinds.Website.TYPE));
        
        WebPage website = new WebPage(url);
        if(preferredFieldsSupported) {
            boolean preferred = cur.getInt(cur.getColumnIndexOrThrow(
                CommonDataKinds.Website.IS_PRIMARY)) != 0;
            website.setPreferred(preferred);
        }
        
        if (websiteType == CommonDataKinds.Website.TYPE_OTHER) {
            website.setPropertyType(WebPage.OTHER_WEBPAGE);
            pd.addWebPage(website);
        } else if (websiteType == CommonDataKinds.Website.TYPE_HOME) {
            website.setPropertyType(WebPage.HOME_WEBPAGE);
            pd.addWebPage(website);
        } else if (websiteType == CommonDataKinds.Website.TYPE_WORK) {
            website.setPropertyType(WebPage.WORK_WEBPAGE);
            bd.addWebPage(website);
        } else {
            Log.error(TAG_LOG, "Ignoring unknown Website type: " + websiteType);
        }

        loadFieldToMap(CommonDataKinds.Website.CONTENT_ITEM_TYPE, websiteType, cur, fieldsMap);
    }

    /**
     * Retrieve the Relation field from a Cursor. Shall be defined by subclasses in
     * order to support it.
     */
    protected void loadRelationField(Contact contact, Cursor cur, HashMap<String,List<Integer>> fieldsMap) {
        Log.error(TAG_LOG, "Relation fields are not supported");
    }

    protected void loadUidField(Contact contact, Cursor cur, HashMap<String,List<Integer>> fieldsMap) {

        long id = contact.getId();
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Loading Uid for: " + id);
        }

        String uid = cur.getString(cur.getColumnIndexOrThrow(AdditionalDataKinds.UID.VALUE));
        contact.setUid(uid);

        loadFieldToMap(AdditionalDataKinds.UID.CONTENT_ITEM_TYPE, 0, cur, fieldsMap);
    }

    protected void loadTimeZoneField(Contact contact, Cursor cur, HashMap<String,List<Integer>> fieldsMap) {

        long id = contact.getId();
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Loading TimeZone for: " + id);
        }

        String tz = cur.getString(cur.getColumnIndexOrThrow(AdditionalDataKinds.TimeZone.VALUE));
        contact.setTimezone(tz);

        loadFieldToMap(AdditionalDataKinds.TimeZone.CONTENT_ITEM_TYPE, 0, cur, fieldsMap);
    }

    protected void loadRevisionField(Contact contact, Cursor cur, HashMap<String,List<Integer>> fieldsMap) {

        long id = contact.getId();
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Loading Revision for: " + id);
        }

        String rev = cur.getString(cur.getColumnIndexOrThrow(AdditionalDataKinds.Revision.VALUE));
        contact.setRevision(rev);

        loadFieldToMap(AdditionalDataKinds.Revision.CONTENT_ITEM_TYPE, 0, cur, fieldsMap);
    }

    protected void loadGeoField(Contact contact, Cursor cur, HashMap<String,List<Integer>> fieldsMap) {

        long id = contact.getId();
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Loading Geo for: " + id);
        }

        PersonalDetail pd = contact.getPersonalDetail();

        String geo = cur.getString(cur.getColumnIndexOrThrow(AdditionalDataKinds.Geo.VALUE));
        pd.setGeo(new Property(geo));

        loadFieldToMap(AdditionalDataKinds.Geo.CONTENT_ITEM_TYPE, 0, cur, fieldsMap);
    }

    protected void loadExtField(Contact contact, Cursor cur,
            HashMap<String,List<Integer>> fieldsMap) {

        long id = contact.getId();
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Loading ext field for: " + id);
        }

        String fieldName = cur.getString(cur.getColumnIndexOrThrow(AdditionalDataKinds.Ext.KEY));
        String fieldValue = cur.getString(cur.getColumnIndexOrThrow(AdditionalDataKinds.Ext.VALUE));

        XTag tmpxTag = new XTag();
        tmpxTag.getXTag().setPropertyValue(fieldValue);
        tmpxTag.setXTagValue(fieldName);

        contact.addXTag(tmpxTag);

        loadFieldToMap(AdditionalDataKinds.Ext.CONTENT_ITEM_TYPE, fieldName, cur, fieldsMap);
    }

    protected void loadCustomField(String mimetype, Contact contact, Cursor cur,
            HashMap<String,List<Integer>> fieldsMap) {
        // There is not custom field to load
    }

    /**
     * Load the given field to the fieldsMap if needed
     */
    protected void loadFieldToMap(String field, Object fieldType, Cursor cur,
            HashMap<String,List<Integer>> fieldsMap) {
        loadFieldToMap(field, fieldType, null, cur, fieldsMap);
    }

    protected void loadFieldToMap(String field, Object fieldType, String label,
            Cursor cur, HashMap<String,List<Integer>> fieldsMap) {
        if (fieldsMap != null) {
            String fieldId = createFieldId(new Object[] {field, fieldType, label});
            if (multipleFieldsSupported || fieldsMap.get(fieldId) == null) {
                int rowId = cur.getInt(cur.getColumnIndexOrThrow("_ID"));
                appendFieldId(fieldsMap, fieldId, rowId);
            }
        }
    }

    /**
     * Check if the given date string is well formatted. Supported formats:
     *  1. yyyymmdd
     *  2. yyyy-mm-dd
     *  3. yyyy/mm/dd
     */
    private boolean checkDate(String date) {

        if (date.length() == 8) {
            try {
                // date must contain only digits
                Integer.parseInt(date);
                return true;
            } catch(NumberFormatException ex) {
                return false;
            }
        } else if (date.length() == 10) {
            if((date.charAt(4) == '-' && date.charAt(7) == '-') ||
               (date.charAt(4) == '/' && date.charAt(7) == '/')) {
                try {
                    Integer.parseInt(date.substring(0, 4));  // yyyy
                    Integer.parseInt(date.substring(5, 7));  // mm
                    Integer.parseInt(date.substring(8, 10)); // dd
                    return true;
                } catch(NumberFormatException ex) {
                    return false;
                }
            } else {
                // Invalid separators
                return false;
            }
        } else {
            // Invalid length
            return false;
        }
    }

    protected ContentProviderOperation.Builder prepareBuilder(long contactId, String fieldId,
                                                            HashMap<String, List<Integer>> fieldsMap,
                                                            List<ContentProviderOperation> ops,
                                                            boolean multipleField)
    {
        List<Integer> rowIds = null;
        if (fieldsMap != null) {
            rowIds = fieldsMap.get(fieldId);
            if (Log.isLoggable(Log.TRACE)) {
                Log.trace(TAG_LOG, "Found " + rowIds + " for " + fieldId);
            }
        }
        boolean insert = true;
        ContentProviderOperation.Builder builder = null;
        if (rowIds != null && rowIds.size() > 0) {
            if (multipleField && multipleFieldsSupported) {
                if (Log.isLoggable(Log.TRACE)) {
                    Log.trace(TAG_LOG, "This is a multiple field. prepareBuilder will delete old data");
                }
                // We must delete all the old fields
                prepareRowDeletion(rowIds, ops);
                // After deleting all the entries of the given type, we
                // add them back with the new values
            } else {
                // We update the first one here
                // TODO: check if we actually need to delete this entry
                if (Log.isLoggable(Log.TRACE)) {
                    Log.trace(TAG_LOG, "prepareBuilder will perform an update");
                }
                long rowId = rowIds.get(0);
                Uri uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, rowId);
                uri = addCallerIsSyncAdapterFlag(uri);
                builder = ContentProviderOperation.newUpdate(uri);
                insert = false;
            }
        }
        if (insert) {
            if (Log.isLoggable(Log.TRACE)) {
                Log.trace(TAG_LOG, "prepareBuilder will perform an insert");
            }
            Uri uri = addCallerIsSyncAdapterFlag(ContactsContract.Data.CONTENT_URI);
            builder = ContentProviderOperation.newInsert(uri);
        }
        // Set the contact id
        if (contactId != -1) {
            if (Log.isLoggable(Log.TRACE)) {
                Log.trace(TAG_LOG, "Updating contact data: " + contactId);
            }
            builder = builder.withValue(ContactsContract.Data.RAW_CONTACT_ID, contactId);
        } else {
            if (Log.isLoggable(Log.TRACE)) {
                Log.trace(TAG_LOG, "Inserting new contact data: " + lastAddBackReferenceId);
            }
            builder = builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID,
                                                     lastAddBackReferenceId);
        }

        return builder;
    }

    protected void prepareName(Contact contact,
                               HashMap<String, List<Integer>> fieldsMap,
                               List<ContentProviderOperation> ops)
    {
        Name nameField = contact.getName();
        if (nameField == null) {
            return;
        }

        String fieldId = createFieldId(CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, 0);

        // Set all the various bits and pieces
        Property dnProp = nameField.getDisplayName();
        Property firstNameProp = nameField.getFirstName();
        Property middleNameProp = nameField.getMiddleName();
        Property lastNameProp = nameField.getLastName();
        Property suffixProp = nameField.getSuffix();
        Property salutationProp = nameField.getSalutation();

        Property props[] = {firstNameProp, middleNameProp, lastNameProp,
                            suffixProp, salutationProp};
        if (isNull(props)) {
            // Simply return if the server didn't send anything
            return;
        }

        String displayName = dnProp != null ? dnProp.getPropertyValueAsString() : null;
        String firstName   = firstNameProp.getPropertyValueAsString();
        String middleName  = middleNameProp.getPropertyValueAsString();
        String lastName    = lastNameProp.getPropertyValueAsString();
        String suffix      = suffixProp.getPropertyValueAsString();
        String salutation  = salutationProp.getPropertyValueAsString();

        String propValues[] = {firstName, middleName, lastName, suffix, salutation};

        if (isNull(propValues)) {
            // Simply return if the server didn't send anything
            return;
        } else if (isFieldEmpty(propValues)) {
            if(fieldsMap != null) {
                // In this case the server sent an empty name, we shall remove the
                // old entry to clean it
                prepareRowDeletion(fieldsMap.get(fieldId), ops);
            }
            return;
        }

        ContentProviderOperation.Builder builder;
        builder = prepareBuilder(contact.getId(), fieldId, fieldsMap, ops, false);
        builder = builder.withValue(ContactsContract.Data.MIMETYPE,
                                    CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);

        if (customization.isDisplayNameSupported()) {
            if (displayName != null) {
                builder = builder.withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, displayName);
            }
        } else {
            // If the display name is not supported as a stand alone field, then
            // we create it as the concatenation of the name components
            StringBuffer dn = new StringBuffer();
            if (firstName != null) {
                dn.append(firstName);
            }
            if (middleName != null && middleName.length() > 0) {
                if (dn.length() > 0) {
                    dn.append(" ");
                }
                dn.append(middleName);
            }
            if (lastName != null && lastName.length() > 0) {
                if (dn.length() > 0) {
                    dn.append(" ");
                }
                dn.append(lastName);
            }
            if (dn.length() > 0) {
                builder = builder.withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, dn.toString());
            }
        }

        if (firstName != null) {
            builder = builder.withValue(CommonDataKinds.StructuredName.GIVEN_NAME, firstName);
        }

        if (middleName != null) {
            builder = builder.withValue(CommonDataKinds.StructuredName.MIDDLE_NAME, middleName);
        }

        if (lastName != null) {
            builder = builder.withValue(CommonDataKinds.StructuredName.FAMILY_NAME, lastName);
        }

        if (suffix != null) {
            builder = builder.withValue(CommonDataKinds.StructuredName.SUFFIX, suffix);
        }

        if (salutation != null) {
            builder = builder.withValue(CommonDataKinds.StructuredName.PREFIX, salutation);
        }

        // Append the operation
        ContentProviderOperation operation = builder.build();
        ops.add(operation);
    }

    protected void prepareNickname(Contact contact,
                               HashMap<String, List<Integer>> fieldsMap,
                               List<ContentProviderOperation> ops)
    {
        Log.error(TAG_LOG, "Nickname fields are not supported");
    }

    protected void preparePhones(Contact contact,
                               HashMap<String,List<Integer>> fieldsMap,
                               List<ContentProviderOperation> ops)
    {
        PersonalDetail pd = contact.getPersonalDetail();
        if (pd != null) {
            List<Phone> phones = pd.getPhones();
            if (phones != null) {
                preparePhones(contact, fieldsMap, phones, ops);
            }
        }
        BusinessDetail bd = contact.getBusinessDetail();
        if (bd != null) {
            List<Phone> phones = bd.getPhones();
            if (phones != null) {
                preparePhones(contact, fieldsMap, phones, ops);
            }
        }
    }

    protected void preparePhones(Contact contact,
                               HashMap<String,List<Integer>> fieldsMap,
                               List<Phone> phones,
                               List<ContentProviderOperation> ops)
    {
        for( Phone phone : phones) {

            String number = phone.getPropertyValueAsString();
            StringBuffer label = new StringBuffer();
            int type = getPhoneType(phone, label);
            if(label.length() == 0) {
                label = null;
            }
            if (type != -1) {

                String fieldId = createFieldId(new Object[] 
                {CommonDataKinds.Phone.CONTENT_ITEM_TYPE, type, label});
                
                if (StringUtil.isNullOrEmpty(number)) {
                    if(fieldsMap != null) {
                        // The field is empty, we shall remove it as the server
                        // wants to emtpy it
                        prepareRowDeletion(fieldsMap.get(fieldId), ops);
                    }
                    continue;
                }

                ContentProviderOperation.Builder builder;
                builder = prepareBuilder(contact.getId(), fieldId, fieldsMap, ops, true);
                builder = builder.withValue(ContactsContract.Data.MIMETYPE,
                                            CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
                builder = builder.withValue(CommonDataKinds.Phone.NUMBER, number);
                builder = builder.withValue(CommonDataKinds.Phone.TYPE, type);
                if (label != null && label.length() > 0) {
                    builder = builder.withValue(CommonDataKinds.Phone.LABEL, label.toString());
                }
                builder = builder.withValue(CommonDataKinds.Phone.IS_PRIMARY,
                        phone.isPreferred() ? 1 : 0);
                builder = builder.withValue(CommonDataKinds.Phone.IS_SUPER_PRIMARY,
                        phone.isPreferred() ? 1 : 0);
                
                ContentProviderOperation operation = builder.build();
                ops.add(operation);
            } else {
                if (Log.isLoggable(Log.INFO)) {
                    Log.info(TAG_LOG, "Ignoring unknown phone number of type: " + phone.getPhoneType());
                }
            }
        }
    }

    protected int getPhoneType(Phone phone, StringBuffer customLabel) {
        String phoneType = phone.getPhoneType();

        int t = -1;
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Getting phone type for: " + phoneType);
        }

        for(int i=1;i<=2;++i) {
            if (Phone.COMPANY_PHONE_NUMBER.equals(phoneType)) {
                t = CommonDataKinds.Phone.TYPE_COMPANY_MAIN;
                break;
            } else if (Phone.PAGER_NUMBER.equals(phoneType)) {
                t = CommonDataKinds.Phone.TYPE_PAGER;
                break;
            } else if (Phone.MOBILE_PHONE_NUMBER.equals(phoneType)) {
                t = CommonDataKinds.Phone.TYPE_MOBILE;
                break;
            } else if (Phone.OTHER_PHONE_NUMBER.equals(phoneType)) {
                t = CommonDataKinds.Phone.TYPE_OTHER;
                break;
            } else if (Phone.HOME_PHONE_NUMBER.equals(phoneType)) {
                t = CommonDataKinds.Phone.TYPE_HOME;
                break;
            } else if (Phone.BUSINESS_PHONE_NUMBER.equals(phoneType)) {
                if (i == 1) {
                    t = CommonDataKinds.Phone.TYPE_WORK;
                } else {
                    customLabel.append(context.getString(R.string.label_work2_phone));
                    t = CommonDataKinds.Phone.TYPE_CUSTOM;
                }
                break;
            } else if (Phone.OTHER_FAX_NUMBER.equals(phoneType)) {
                t = CommonDataKinds.Phone.TYPE_OTHER_FAX;
                break;
            } else if (Phone.HOME_FAX_NUMBER.equals(phoneType)) {
                t = CommonDataKinds.Phone.TYPE_FAX_HOME;
                break;
            } else if (Phone.BUSINESS_FAX_NUMBER.equals(phoneType)) {
                t = CommonDataKinds.Phone.TYPE_FAX_WORK;
                break;
            } else if (Phone.PRIMARY_PHONE_NUMBER.equals(phoneType)) {
                t = CommonDataKinds.Phone.TYPE_MAIN;
                break;
            }
        }
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Phone type mapped to: " + t);
        }
        return t;
    }

    protected void prepareEmail(Contact contact,
                              HashMap<String, List<Integer>> fieldsMap,
                              List<ContentProviderOperation> ops)
    {
        long id = contact.getId();
        PersonalDetail pd = contact.getPersonalDetail();
        if (pd != null) {
            List<Email> emails = pd.getEmails();
            for(Email email : emails) {
                String addr = email.getPropertyValueAsString();
                if (Email.HOME_EMAIL.equals(email.getEmailType())) {
                    prepareEmail(id, email, CommonDataKinds.Email.TYPE_HOME,
                            fieldsMap, ops);
                } else if (Email.OTHER_EMAIL.equals(email.getEmailType())) {
                    prepareEmail(id, email, CommonDataKinds.Email.TYPE_OTHER,
                            fieldsMap, ops);
                } else if (Email.IM_ADDRESS.equals(email.getEmailType())) {
                    prepareIm(id, addr, CommonDataKinds.Im.TYPE_HOME,
                            email.isPreferred(), fieldsMap, ops);
                } else {
                    if (Log.isLoggable(Log.INFO)) {
                        Log.info(TAG_LOG, "Ignoring email address " + email.getEmailType());
                    }
                }
            }
        }

        BusinessDetail bd = contact.getBusinessDetail();
        if (bd != null) {
            List<Email> emails = bd.getEmails();
            for(Email email : emails) {
                if (Email.WORK_EMAIL.equals(email.getEmailType())) {
                    prepareEmail(id, email, CommonDataKinds.Email.TYPE_WORK,
                            fieldsMap, ops);
                } else if (Email.IM_ADDRESS.equals(email.getEmailType())) {
                    prepareIm(id, email.getPropertyValueAsString(), CommonDataKinds.Im.TYPE_WORK,
                            email.isPreferred(), fieldsMap, ops);
                } else {
                    if (Log.isLoggable(Log.INFO)) {
                        Log.info(TAG_LOG, "Ignoring email address " + email.getEmailType());
                    }
                }
            }
        }
    }

    protected void prepareEmail(long id, Email email, int type,
            HashMap<String,List<Integer>> fieldsMap,
            List<ContentProviderOperation> ops)
    {
        String address = email.getPropertyValueAsString();

        String fieldId = createFieldId(CommonDataKinds.Email.CONTENT_ITEM_TYPE, type);
        if (StringUtil.isNullOrEmpty(address)) {
            if(fieldsMap != null) {
                // The field is empty, so we can just remove it
                prepareRowDeletion(fieldsMap.get(fieldId), ops);
            }
            return;
        }
        ContentProviderOperation.Builder builder;
        builder = prepareBuilder(id, fieldId, fieldsMap, ops, true);

        builder = builder.withValue(ContactsContract.Data.MIMETYPE,
                                    CommonDataKinds.Email.CONTENT_ITEM_TYPE);

        builder = builder.withValue(CommonDataKinds.Email.DATA, address);
        builder = builder.withValue(CommonDataKinds.Email.TYPE, type);
        builder = builder.withValue(CommonDataKinds.Email.IS_PRIMARY,
                 email.isPreferred() ? 1 : 0);
        builder = builder.withValue(CommonDataKinds.Email.IS_SUPER_PRIMARY,
                 email.isPreferred() ? 1 : 0);
        
        ops.add(builder.build());
    }

    protected void prepareIm(Contact contact,
                             HashMap<String, List<Integer>> fieldsMap,
                             List<ContentProviderOperation> ops)
    {
        if (Log.isLoggable(Log.DEBUG)) {
            Log.debug(TAG_LOG, "Direct Im fields are not supported");
        }
    }

    protected void prepareIm(long id, String im, int type, boolean preferred,
            HashMap<String,List<Integer>> fieldsMap,
            List<ContentProviderOperation> ops)
    {
        String fieldId = createFieldId(CommonDataKinds.Im.CONTENT_ITEM_TYPE, type);
        if (StringUtil.isNullOrEmpty(im)) {
            if(fieldsMap != null) {
                // The field is empty, so we can just remove it
                prepareRowDeletion(fieldsMap.get(fieldId), ops);
            }
            return;
        }
        ContentProviderOperation.Builder builder;
        builder = prepareBuilder(id, fieldId, fieldsMap, ops, true);

        builder = builder.withValue(ContactsContract.Data.MIMETYPE,
                                    CommonDataKinds.Im.CONTENT_ITEM_TYPE);

        builder = builder.withValue(CommonDataKinds.Im.DATA, im);
        builder = builder.withValue(CommonDataKinds.Im.PROTOCOL,
                CommonDataKinds.Im.PROTOCOL_AIM);
        builder = builder.withValue(CommonDataKinds.Im.TYPE, type);
        builder = builder.withValue(CommonDataKinds.Im.IS_PRIMARY,
                preferred ? 1 : 0);
        builder = builder.withValue(CommonDataKinds.Im.IS_SUPER_PRIMARY,
                preferred ? 1 : 0);

        ContentProviderOperation operation = builder.build();
        ops.add(operation);
    }

    protected void preparePhoto(Contact contact,
                                HashMap<String,List<Integer>> fieldsMap,
                                List<ContentProviderOperation> ops)
    {
        PersonalDetail pd = contact.getPersonalDetail();
        List<Photo> photos = pd.getPhotoObjects();
        if(photos == null || photos.isEmpty()) {
            return;
        }
        for(Photo photo : photos) {
            if (photo != null) {
                byte[] photoBytes = photo.getImage();
                String fieldId = createFieldId(CommonDataKinds.Photo.CONTENT_ITEM_TYPE, 0);
                if (photoBytes != null && photoBytes.length > 0) {
                    ContentProviderOperation.Builder builder;
                    builder = prepareBuilder(contact.getId(), fieldId, fieldsMap, ops, true);
                    builder = builder.withValue(ContactsContract.Data.MIMETYPE,
                                                CommonDataKinds.Photo.CONTENT_ITEM_TYPE);
                    builder = builder.withValue(CommonDataKinds.Photo.PHOTO, photoBytes);
                    builder = builder.withValue(CommonDataKinds.Photo.IS_PRIMARY,
                        photo.isPreferred() ? 1 : 0);
                    builder = builder.withValue(CommonDataKinds.Photo.IS_SUPER_PRIMARY,
                        photo.isPreferred() ? 1 : 0);
                    ContentProviderOperation operation = builder.build();
                    ops.add(operation);
                } else if(fieldsMap != null) {
                    // The photo is sent empty, we need to remove it
                    prepareRowDeletion(fieldsMap.get(fieldId), ops);
                    return;
                }
            }
        }
    }

    protected void prepareOrganization(Contact contact,
                                       HashMap<String,List<Integer>> fieldsMap,
                                       List<ContentProviderOperation> ops)
    {
        BusinessDetail bd = contact.getBusinessDetail();

        Property companyProp = bd.getCompany();
        Property depProp     = bd.getDepartment();
        String location      = bd.getOfficeLocation();

        Title t = null;
        List<Title> titles = bd.getTitles();
        if (titles != null && titles.size() > 0) {
            t = titles.get(0);
        }

        String company    = null;
        String title      = null;
        String department = null;

        if (companyProp != null) {
            company = companyProp.getPropertyValueAsString();
        }
        if (t != null) {
            title = t.getPropertyValueAsString();
        }
        if (depProp != null) {
            department = depProp.getPropertyValueAsString();
        }

        String fieldId = createFieldId(CommonDataKinds.Organization.CONTENT_ITEM_TYPE, 0);

        String allFields[] = {company, title, department, location};
        if (isNull(allFields)) {
            // If all the properties are empty, then the server did not send this
            // field. We can simply return in this case.
            return;
        } else if (isFieldEmpty(allFields)) {
            if(fieldsMap != null) {
                // The field is empty, so we can just remove it
                prepareRowDeletion(fieldsMap.get(fieldId), ops);
            }
            return;
        } else {
            ContentProviderOperation.Builder builder;
            builder = prepareBuilder(contact.getId(), fieldId, fieldsMap, ops, false);

            builder = builder.withValue(ContactsContract.Data.MIMETYPE,
                    CommonDataKinds.Organization.CONTENT_ITEM_TYPE);

            if (company != null) {
                builder = builder.withValue(CommonDataKinds.Organization.COMPANY, company);
            }

            if (title != null) {
                builder = builder.withValue(CommonDataKinds.Organization.TITLE, title);
            }

            if (department != null) {
                builder = builder.withValue(CommonDataKinds.Organization.DEPARTMENT, department);
            }

            if (location != null) {
                builder = builder.withValue(CommonDataKinds.Organization.OFFICE_LOCATION, location);
            }

            // Add the type
            builder = builder.withValue(CommonDataKinds.Organization.TYPE, CommonDataKinds.Organization.TYPE_WORK);

            ContentProviderOperation operation = builder.build();
            ops.add(operation);
        }
    }

    protected void preparePostalAddress(Contact contact,
                                        HashMap<String,List<Integer>> fieldsMap,
                                        List<ContentProviderOperation> ops)
    {
        PersonalDetail pd = contact.getPersonalDetail();
        if (pd != null) {
            List<Address> addresses = pd.getAddresses();
            for(Address address : addresses) {
                if (address != null) {
                    if (Address.HOME_ADDRESS.equals(address.getAddressType())) {
                        preparePostalAddress(contact, address,
                                CommonDataKinds.StructuredPostal.TYPE_HOME,
                                fieldsMap, ops);
                    } else if (Address.OTHER_ADDRESS.equals(address.getAddressType())) {
                        preparePostalAddress(contact, address,
                                CommonDataKinds.StructuredPostal.TYPE_OTHER,
                                fieldsMap, ops);
                    }
                }
            }
        }
        BusinessDetail bd = contact.getBusinessDetail();
        if (bd != null) {
            List<Address> addresses = bd.getAddresses();
            for(Address address : addresses) {
                if (address != null) {
                    if (Address.WORK_ADDRESS.equals(address.getAddressType())) {
                        preparePostalAddress(contact, address,
                                CommonDataKinds.StructuredPostal.TYPE_WORK,
                                fieldsMap, ops);
                    }
                }
            }
        }
    }

    protected void preparePostalAddress(Contact contact, Address address, int type,
                                        HashMap<String,List<Integer>> fieldsMap,
                                        List<ContentProviderOperation> ops)
    {
        String city = null;
        String country = null;
        String pobox = null;
        String poCode = null;
        String region = null;
        String street = null;
        String extAddress = null;

        Property cityProp = address.getCity();
        if (cityProp != null) {
            city = cityProp.getPropertyValueAsString();
        }
        Property countryProp = address.getCountry();
        if (countryProp != null) {
            country = countryProp.getPropertyValueAsString();
        }
        Property poboxProp = address.getPostOfficeAddress();
        if (poboxProp != null) {
            pobox = poboxProp.getPropertyValueAsString();
        }
        Property pocodeProp = address.getPostalCode();
        if (pocodeProp != null) {
            poCode = pocodeProp.getPropertyValueAsString();
        }
        Property regionProp = address.getState();
        if (regionProp != null) {
            region = regionProp.getPropertyValueAsString();
        }
        Property streetProp = address.getStreet();
        if (streetProp != null) {
            street = streetProp.getPropertyValueAsString();
        }
        Property extAddressProp = address.getExtendedAddress();
        if (extAddressProp != null) {
            extAddress = extAddressProp.getPropertyValueAsString();
        }

        String fieldId = createFieldId(CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE, type);

        String allFields[] = {city,country,pobox,poCode,region,street};
        if (isNull(allFields)) {
            // The server did not send this field, just ignore it
            return;
        } else if (isFieldEmpty(allFields)) {
            if(fieldsMap != null) {
                prepareRowDeletion(fieldsMap.get(fieldId), ops);
            }
            return;
        } else {
            ContentProviderOperation.Builder builder;
            builder = prepareBuilder(contact.getId(), fieldId, fieldsMap, ops, true);

            builder = builder.withValue(ContactsContract.Data.MIMETYPE,
                    CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE);

            if (city != null) {
                builder = builder.withValue(CommonDataKinds.StructuredPostal.CITY, city);
            }
            if (country != null) {
                builder = builder.withValue(CommonDataKinds.StructuredPostal.COUNTRY, country);
            }
            if (pobox != null) {
                builder = builder.withValue(CommonDataKinds.StructuredPostal.POBOX, pobox);
            }
            if (poCode != null) {
                builder = builder.withValue(CommonDataKinds.StructuredPostal.POSTCODE, poCode);
            }
            if (region != null) {
                builder = builder.withValue(CommonDataKinds.StructuredPostal.REGION, region);
            }
            if (street != null) {
                builder = builder.withValue(CommonDataKinds.StructuredPostal.STREET, street);
            }
            if (extAddress != null) {
                builder = builder.withValue(CommonDataKinds.StructuredPostal.NEIGHBORHOOD, extAddress);
            }
            builder = builder.withValue(CommonDataKinds.StructuredPostal.TYPE, type);
            builder = builder.withValue(CommonDataKinds.StructuredPostal.IS_PRIMARY,
                    address.isPreferred() ? 1 : 0);
            builder = builder.withValue(CommonDataKinds.StructuredPostal.IS_SUPER_PRIMARY,
                    address.isPreferred() ? 1 : 0);
            ContentProviderOperation operation = builder.build();
            ops.add(operation);
        }
    }

    protected void prepareEvent(Contact contact,
                                HashMap<String,List<Integer>> fieldsMap,
                                List<ContentProviderOperation> ops)
    {
        PersonalDetail pd = contact.getPersonalDetail();
        
        prepareEvent(contact, pd.getBirthday(),
                CommonDataKinds.Event.TYPE_BIRTHDAY, fieldsMap, ops);
        prepareEvent(contact, pd.getAnniversary(),
                CommonDataKinds.Event.TYPE_ANNIVERSARY, fieldsMap, ops);
    }

    protected void prepareEvent(Contact contact, String eventDate, int type,
            HashMap<String,List<Integer>> fieldsMap,
            List<ContentProviderOperation> ops)
    {
        String fieldId = createFieldId(CommonDataKinds.Event.CONTENT_ITEM_TYPE, type);

        if (eventDate == null) {
            // The server did not send this field, just ignore it
            return;
        } else if ("".equals(eventDate)) {
            if(fieldsMap != null) {
                prepareRowDeletion(fieldsMap.get(fieldId), ops);
            }
            return;
        } else {
            // Insert date separator if needed
            if(eventDate.length() == 8) {
                StringBuffer date = new StringBuffer(10);
                date.append(eventDate.substring(0, 4)).append("-");
                date.append(eventDate.substring(4, 6)).append("-");
                date.append(eventDate.substring(6));
                eventDate = date.toString();
            }
            ContentProviderOperation.Builder builder;
            builder = prepareBuilder(contact.getId(), fieldId, fieldsMap, ops, false);

            builder = builder.withValue(ContactsContract.Data.MIMETYPE,
                    CommonDataKinds.Event.CONTENT_ITEM_TYPE);
            builder = builder.withValue(CommonDataKinds.Event.START_DATE,
                    eventDate);
            builder = builder.withValue(CommonDataKinds.Event.TYPE, type);

            ContentProviderOperation operation = builder.build();
            ops.add(operation);
        }
    }

    protected void prepareNote(Contact contact,
                               HashMap<String, List<Integer>> fieldsMap,
                               List<ContentProviderOperation> ops)
    {
        String fieldId = createFieldId(CommonDataKinds.Note.CONTENT_ITEM_TYPE, 0);

        List<Note> notes = contact.getNotes();

        if(notes != null) {
            for( Note noteField : notes) {
                String note = noteField.getPropertyValueAsString();

                if (note != null) {
                    // The device cannot display \r characters, so we remove them
                    // here and add them back to outgoing items
                    note = StringUtil.replaceAll(note, "\r\n", "\n");
                    note = StringUtil.replaceAll(note, "\r",   "\n");
                }

                if (StringUtil.isNullOrEmpty(note)) {
                    if(fieldsMap != null) {
                        // The field is empty, we shall remove it as the server
                        // wants to emtpy it
                        prepareRowDeletion(fieldsMap.get(fieldId), ops);
                    }
                    continue;
                }

                ContentProviderOperation.Builder builder;
                builder = prepareBuilder(contact.getId(), fieldId, fieldsMap, ops, false);

                builder = builder.withValue(ContactsContract.Data.MIMETYPE,
                                            CommonDataKinds.Note.CONTENT_ITEM_TYPE);
                builder = builder.withValue(CommonDataKinds.Note.NOTE, note);

                ContentProviderOperation operation = builder.build();
                ops.add(operation);
            }
        }
    }

    protected void prepareWebsite(Contact contact,
                                  HashMap<String,List<Integer>> fieldsMap,
                                  List<ContentProviderOperation> ops)
    {
        PersonalDetail pd = contact.getPersonalDetail();
        if (pd != null) {
            List<WebPage> websites = pd.getWebPages();
            if (websites != null) {
                prepareWebsite(contact, CommonDataKinds.Website.TYPE_HOME,
                        fieldsMap, websites, ops);
            }
        }
        BusinessDetail bd = contact.getBusinessDetail();
        if (bd != null) {
            List<WebPage> websites = bd.getWebPages();
            if (websites != null) {
                prepareWebsite(contact, CommonDataKinds.Website.TYPE_WORK,
                        fieldsMap, websites, ops);
            }
        }
    }

    private void prepareWebsite(Contact contact, int baseType,
                                HashMap<String,List<Integer>> fieldsMap,
                                List<WebPage> websites,
                                List<ContentProviderOperation> ops)
    {
        for( WebPage website : websites) {

            int type = baseType;
            
            if(WebPage.OTHER_WEBPAGE.equals(website.getPropertyType())) {
                type = CommonDataKinds.Website.TYPE_OTHER;
            }

            String url = website.getPropertyValueAsString();

            String fieldId = createFieldId(CommonDataKinds.Website.CONTENT_ITEM_TYPE, type);

            if (StringUtil.isNullOrEmpty(url)) {
                if(fieldsMap != null) {
                    // The field is empty, we shall remove it as the server
                    // wants to emtpy it
                    prepareRowDeletion(fieldsMap.get(fieldId), ops);
                }
                continue;
            }

            ContentProviderOperation.Builder builder;
            builder = prepareBuilder(contact.getId(), fieldId, fieldsMap, ops, true);

            builder = builder.withValue(ContactsContract.Data.MIMETYPE,
                                        CommonDataKinds.Website.CONTENT_ITEM_TYPE);
            builder = builder.withValue(CommonDataKinds.Website.URL, url);
            builder = builder.withValue(CommonDataKinds.Website.TYPE, type);
            builder = builder.withValue(CommonDataKinds.Website.IS_PRIMARY,
                    website.isPreferred() ? 1 : 0);
            builder = builder.withValue(CommonDataKinds.Website.IS_SUPER_PRIMARY,
                    website.isPreferred() ? 1 : 0);
            ContentProviderOperation operation = builder.build();
            ops.add(operation);
        }
    }

    protected void prepareRelation(Contact contact,
                                   HashMap<String,List<Integer>> fieldsMap,
                                   List<ContentProviderOperation> ops)
    {
        Log.error(TAG_LOG, "Relation fields are not supported");
    }

    protected void prepareRelation(Contact contact, String relName, int type,
            HashMap<String,List<Integer>> fieldsMap,
            List<ContentProviderOperation> ops)
    {
        String fieldId = createFieldId(CommonDataKinds.Relation.CONTENT_ITEM_TYPE, type);

        if (relName == null) {
            // The server did not send this field, just ignore it
            return;
        } else if ("".equals(relName)) {
            if(fieldsMap != null) {
                prepareRowDeletion(fieldsMap.get(fieldId), ops);
            }
            return;
        } else {
            ContentProviderOperation.Builder builder;
            builder = prepareBuilder(contact.getId(), fieldId, fieldsMap, ops, false);

            builder = builder.withValue(ContactsContract.Data.MIMETYPE,
                    CommonDataKinds.Relation.CONTENT_ITEM_TYPE);
            builder = builder.withValue(CommonDataKinds.Relation.NAME,
                    relName);
            builder = builder.withValue(CommonDataKinds.Relation.TYPE, type);

            ContentProviderOperation operation = builder.build();
            ops.add(operation);
        }
    }

    protected void prepareUid(Contact contact,
                              HashMap<String,List<Integer>> fieldsMap,
                              List<ContentProviderOperation> ops)
    {
        prepareSimpleStringField(contact.getUid(), 
                AdditionalDataKinds.UID.CONTENT_ITEM_TYPE, 0,
                AdditionalDataKinds.UID.VALUE,
                contact, fieldsMap, ops);
    }

    protected void prepareTimeZone(Contact contact,
                                   HashMap<String,List<Integer>> fieldsMap,
                                   List<ContentProviderOperation> ops)
    {
        prepareSimpleStringField(contact.getTimezone(),
                AdditionalDataKinds.TimeZone.CONTENT_ITEM_TYPE, 0,
                AdditionalDataKinds.TimeZone.VALUE,
                contact, fieldsMap, ops);
    }

    protected void prepareRevision(Contact contact,
                                   HashMap<String,List<Integer>> fieldsMap,
                                   List<ContentProviderOperation> ops)
    {
        prepareSimpleStringField(contact.getRevision(),
                AdditionalDataKinds.Revision.CONTENT_ITEM_TYPE, 0,
                AdditionalDataKinds.Revision.VALUE,
                contact, fieldsMap, ops);
    }

    protected void prepareGeo(Contact contact,
                              HashMap<String,List<Integer>> fieldsMap,
                              List<ContentProviderOperation> ops)
    {
        Property geo = contact.getPersonalDetail().getGeo();
        if(geo != null) {
            prepareSimpleStringField(geo.getPropertyValueAsString(),
                    AdditionalDataKinds.Geo.CONTENT_ITEM_TYPE, 0,
                    AdditionalDataKinds.Geo.VALUE,
                    contact, fieldsMap, ops);
        }
    }

    protected void prepareSimpleStringField(String fieldValue, String mimetype,
            int type, String dataColumn, Contact contact,
            HashMap<String,List<Integer>> fieldsMap,
            List<ContentProviderOperation> ops)
    {
        String fieldId = createFieldId(mimetype, type);
        if(fieldValue == null) {
            // The server did not send this field, just ignore it
            return;
        } else if ("".equals(fieldValue)) {
            if(fieldsMap != null) {
                prepareRowDeletion(fieldsMap.get(fieldId), ops);
            }
            return;
        } else {
            ContentProviderOperation.Builder builder;
            builder = prepareBuilder(contact.getId(), fieldId, fieldsMap, ops, false);

            builder = builder.withValue(ContactsContract.Data.MIMETYPE, mimetype);
            builder = builder.withValue(dataColumn, fieldValue);

            ContentProviderOperation operation = builder.build();
            ops.add(operation);
        }
    }

    protected void prepareExtFields(Contact contact,
                                HashMap<String,List<Integer>> fieldsMap,
                                List<ContentProviderOperation> ops)
    {
        List<XTag> xTags = contact.getXTags();
        if(xTags == null) {
            return;
        }
        for(XTag xTag : xTags) {
            Property pTag = xTag.getXTag();
            String pName = xTag.getXTagValue();
            String pValue = pTag.getPropertyValueAsString();
            prepareExtField(contact, pName, pValue, fieldsMap, ops);
        }
    }

    protected void prepareExtField(Contact contact, String fieldName,
            String fieldValue, HashMap<String,List<Integer>> fieldsMap,
            List<ContentProviderOperation> ops)
    {
        String fieldId = createFieldId(AdditionalDataKinds.Ext.CONTENT_ITEM_TYPE, fieldName);
        if ("".equals(fieldValue)) {
            if(fieldsMap != null) {
                prepareRowDeletion(fieldsMap.get(fieldId), ops);
            }
            return;
        } else {
            ContentProviderOperation.Builder builder;
            builder = prepareBuilder(contact.getId(), fieldId, fieldsMap, ops, false);

            builder = builder.withValue(ContactsContract.Data.MIMETYPE,
                    AdditionalDataKinds.Ext.CONTENT_ITEM_TYPE);
            builder = builder.withValue(AdditionalDataKinds.Ext.KEY, fieldName);
            builder = builder.withValue(AdditionalDataKinds.Ext.VALUE, fieldValue);

            ContentProviderOperation operation = builder.build();
            ops.add(operation);
        }
    }

    protected void prepareCustomFields(Contact contact,
                                HashMap<String,List<Integer>> fieldsMap,
                                List<ContentProviderOperation> ops)
    {
        // There is no custom fields to prepare
    }

    protected String createFieldId(String mimeType, int type) {
        return createFieldId(mimeType, Integer.toString(type));
    }

    protected String createFieldId(String mimeType, String type) {
        return createFieldId(new String[] { mimeType, type });
    }
    
    protected String createFieldId(Object[] values) {
        if(values == null || values.length == 0) {
            return "";
        }
        StringBuffer res = new StringBuffer();
        Object value = values[0];
        if(value != null) {
            res.append(value.toString());
        }
        for(int i=1; i<values.length; i++) {
            value = values[i];
            if(value != null) {
                res.append("-").append(values[i].toString());
            }
        }
        return res.toString();
    }

    private boolean isFieldEmpty(String allFields[]) {
        boolean empty = true;

        for(int i=0;i<allFields.length;++i) {
            String field = allFields[i];
            if (!StringUtil.isNullOrEmpty(field)) {
                empty = false;
                break;
            }
        }
        return empty;
    }

    private boolean isNull(Object objs[]) {
        for(int i=0;i<objs.length;++i) {
            if (objs[i] != null) {
                return false;
            }
        }
        return true;
    }

    protected void prepareRowDeletion(List<Integer> rows, List<ContentProviderOperation> ops) {
        if (rows != null) {
            ContentProviderOperation.Builder builder;
            for(int rowId: rows) {
                Uri uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, rowId);
                uri = addCallerIsSyncAdapterFlag(uri);
                builder = ContentProviderOperation.newDelete(uri);
                ops.add(builder.build());
            }
        }
    }

    protected Uri addCallerIsSyncAdapterFlag(Uri uri) {
        if(callerIsSyncAdapter) {
            Uri.Builder b = uri.buildUpon();
            b.appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true");
            return b.build();
        } else {
            return uri;
        }
    }

    /**
     * @return
     */
    protected Cursor getContactsCursor() {
        String cols[] = {ContactsContract.RawContacts._ID};
        StringBuffer whereClause = new StringBuffer();
        if (accountName != null) {
            whereClause.append(ContactsContract.RawContacts.ACCOUNT_NAME).append("='").append(accountName).append("'");
            whereClause.append(" AND ");
            whereClause.append(ContactsContract.RawContacts.ACCOUNT_TYPE).append("='").append(accountType).append("'");
            whereClause.append(" AND ");
        }
        whereClause.append(ContactsContract.RawContacts.DELETED).append("=").append("0");
        Cursor peopleCur = resolver.query(ContactsContract.RawContacts.CONTENT_URI,
                cols, whereClause.toString(), null, null);
        return peopleCur;
    }

    protected void deriveFieldFromProperty(com.funambol.syncml.protocol.Property property, Vector supportedFields,
                                           boolean includeBasicProperty) {

        if ("PHOTO".equals(property.getPropName())) {
            supportedFields.addElement("PHOTO");
        } else if ("BEGIN".equals(property.getPropName()) ||
                   "END".equals(property.getPropName()) ||
                   "VERSION".equals(property.getPropName())) {
            // These are not fields, just VCard metadata, ignore them
        } else if ("TEL".equals(property.getPropName())) {
            // There is a mismatch here, between the caps and what the client
            // does. If we change it we shall update the automatic tests
            // accordingly
            super.deriveFieldFromProperty(property, supportedFields, false);
        } else if ("EMAIL".equals(property.getPropName())) {
            // There is a mismatch here, between the caps and what the client
            // does. If we change it we shall update the automatic tests
            // accordingly
            super.deriveFieldFromProperty(property, supportedFields, false);
        } else {
            super.deriveFieldFromProperty(property, supportedFields, includeBasicProperty);
        }
    }



    private void prepareSourceIdAndDirtyFlagOperation(long contactId, ArrayList<ContentProviderOperation> ops) {
        if(Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Refreshing source id and dirty flag for contact: " + contactId);
        }

        Uri uri = ContentUris.withAppendedId(RAW_CONTACT_URI, contactId);
        uri = addCallerIsSyncAdapterFlag(uri);
        ContentProviderOperation.Builder builder;
        builder = ContentProviderOperation.newUpdate(uri);

        builder.withValue(ContactsContract.RawContacts.SOURCE_ID, FUNAMBOL_SOURCE_ID_PREFIX + contactId);
        builder.withValue(ContactsContract.RawContacts.DIRTY, 0);

        ops.add(builder.build());
    }

    /**
     * Prepare a hard delete query for the contact in the store
     * @param rawContactId
     */
    private void prepareHardDelete(long rawContactId) {

        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Preparing to hard delete contact: " + rawContactId);
        }

        // Delete from raw_contacts (related rows in Data table are
        // automatically deleted)
        Uri uri = addCallerIsSyncAdapterFlag(RAW_CONTACT_URI);
        uri = ContentUris.withAppendedId(uri, rawContactId);
        ContentProviderOperation.Builder builder;
        builder = ContentProviderOperation.newDelete(uri);
        ops.add(builder.build());
    }

    private void commitSingleBatch() throws IOException {
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "commitSingleBatch " + ops.size());
        }
        // Now perform all the operations in one shot
        try {
            if (ops.size() > 0) {
                ContentProviderResult[] res = resolver.applyBatch(ContactsContract.AUTHORITY, ops);
                ArrayList<ContentProviderOperation> ops2 = new ArrayList<ContentProviderOperation>();
                for(int i=0;i<rawContactIdx.size();++i) {
                    int idx = rawContactIdx.get(i).intValue();
                    if (res[idx].uri == null) {
                        // This item was not properly inserted. Mark this as an error
                        // (A zero length key is the marker)
                        if (Log.isLoggable(Log.INFO)) {
                            Log.info(TAG_LOG, "Cannot find uri for inserted item, will be marked as failed");
                        }
                        newKeys.addElement("");
                        continue;
                    }
                    long id = ContentUris.parseId(res[idx].uri);
                    if (Log.isLoggable(Log.TRACE)) {
                        Log.trace(TAG_LOG, "The new contact has id: " + id);
                    }
                    if(callerIsSyncAdapter) {
                        prepareSourceIdAndDirtyFlagOperation(id, ops2);
                    }
                    newKeys.addElement("" + id);
                }
                // Apply all the operations to set the sourceId and the dirty flag
                if (ops2.size() > 0) {
                    if (Log.isLoggable(Log.DEBUG)) {
                        Log.debug(TAG_LOG, "Clearing dirty flag " + callerIsSyncAdapter);
                    }
                    resolver.applyBatch(ContactsContract.AUTHORITY, ops2);
                }
            }
        } catch (Exception e) {
            Log.error(TAG_LOG, "Cannot commit to database", e);
            throw new IOException("Cannot create contact in db");
        } finally {
            ops.clear();
            rawContactIdx.clear();
        }
    }




}
