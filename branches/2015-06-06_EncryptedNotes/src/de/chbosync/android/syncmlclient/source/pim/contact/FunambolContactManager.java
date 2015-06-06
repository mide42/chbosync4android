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

import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import android.content.ContentProviderOperation;
import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;

import com.funambol.common.pim.model.common.Property;
import com.funambol.common.pim.model.contact.BusinessDetail;
import com.funambol.common.pim.model.contact.Email;
import com.funambol.common.pim.model.contact.Name;
import com.funambol.common.pim.model.contact.PersonalDetail;
import com.funambol.syncml.protocol.PropParam;
import com.funambol.util.Log;


/**
 * Represents a specific ContactManager used to manage fields that need to be
 * managed differently from the standard way.
 */
public class FunambolContactManager extends ContactManager {

    private static final String TAG_LOG = "FunambolContactManager";

    // Constructors------------------------------------------------
    public FunambolContactManager(Context context) {
        super(context);
    }

    public FunambolContactManager(Context context, boolean callerIsSyncAdapter) {
        super(context, callerIsSyncAdapter);
    }

    @Override
    protected void loadImField(Contact contact, Cursor cur, HashMap<String,List<Integer>> fieldsMap) {

        long id = contact.getId();
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Loading Im for: " + id);
        }

        PersonalDetail pd = contact.getPersonalDetail();
        BusinessDetail bd = contact.getBusinessDetail();

        String im = cur.getString(cur.getColumnIndexOrThrow(CommonDataKinds.Im.DATA));
        int imType =  cur.getInt(cur.getColumnIndexOrThrow(CommonDataKinds.Im.TYPE));
        int imProtocol = cur.getInt(cur.getColumnIndexOrThrow(CommonDataKinds.Im.PROTOCOL));

        if(imProtocol == CommonDataKinds.Im.PROTOCOL_AIM) {

            Email emailObj = new Email(im);
            emailObj.setEmailType("IMAddress");

            HashMap<String,String> params = new HashMap<String,String>();
            params.put("X-FUNAMBOL-INSTANTMESSENGER", null);
            emailObj.setXParams(params);

            if (imType == CommonDataKinds.Im.TYPE_HOME ||
                imType == CommonDataKinds.Im.TYPE_OTHER) {
                pd.addEmail(emailObj);
            } else if (imType == CommonDataKinds.Im.TYPE_WORK) {
                bd.addEmail(emailObj);
            } else {
                Log.error(TAG_LOG, "Ignoring unknown Im type: " + imType);
            }
        } else {
            Log.error(TAG_LOG, "Ignoring unknown Im protocol: " + imProtocol);
        }

        loadFieldToMap(CommonDataKinds.Im.CONTENT_ITEM_TYPE, imType, cur, fieldsMap);
    }
    
    @Override
    protected void loadRelationField(Contact contact, Cursor cur, HashMap<String,List<Integer>> fieldsMap) {

        long id = contact.getId();
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Loading Relation for: " + id);
        }

        PersonalDetail pd = contact.getPersonalDetail();

        String relName = cur.getString(cur.getColumnIndexOrThrow(CommonDataKinds.Relation.NAME));
        int relType =  cur.getInt(cur.getColumnIndexOrThrow(CommonDataKinds.Relation.TYPE));

        if (relType == CommonDataKinds.Relation.TYPE_CHILD) {
            pd.setChildren(relName);
        } else if (relType == CommonDataKinds.Relation.TYPE_SPOUSE) {
            pd.setSpouse(relName);
        } else {
            Log.error(TAG_LOG, "Ignoring unknown Relation type: " + relType);
        }

        loadFieldToMap(CommonDataKinds.Relation.CONTENT_ITEM_TYPE, relType, cur, fieldsMap);
    }

    @Override
    protected void loadNickNameField(Contact contact, Cursor cur, HashMap<String,List<Integer>> fieldsMap) {

        long id = contact.getId();
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Loading nickname for: " + id);
        }

        Name nameField = contact.getName();
        if(nameField == null) {
            nameField = new Name();
        }

        String nickName = cur.getString(cur.getColumnIndexOrThrow(CommonDataKinds.Nickname.NAME));
        int nickNameType = cur.getInt(cur.getColumnIndexOrThrow(CommonDataKinds.Nickname.TYPE));
        if (nickNameType == CommonDataKinds.Nickname.TYPE_DEFAULT) {
            Property nickNameProp = new Property(nickName);
            nameField.setNickname(nickNameProp);
        }
        contact.setName(nameField);

        loadFieldToMap(CommonDataKinds.Nickname.CONTENT_ITEM_TYPE, 0, cur, fieldsMap);
    }

    @Override
    protected void prepareRelation(Contact contact,
                                HashMap<String,List<Integer>> fieldsMap,
                                List<ContentProviderOperation> ops)
    {
        PersonalDetail pd = contact.getPersonalDetail();

        prepareRelation(contact, pd.getChildren(),
                CommonDataKinds.Relation.TYPE_CHILD, fieldsMap, ops);
        prepareRelation(contact, pd.getSpouse(),
                CommonDataKinds.Relation.TYPE_SPOUSE, fieldsMap, ops);
    }

    @Override
    protected void prepareNickname(Contact contact,
                               HashMap<String, List<Integer>> fieldsMap,
                               List<ContentProviderOperation> ops)
    {
        String fieldId = createFieldId(CommonDataKinds.Nickname.CONTENT_ITEM_TYPE, 0);
        Name nameField = contact.getName();
        if (nameField == null) {
            // No name specified, do not add anything
            return;
        }
        Property nnProp = nameField.getNickname();
        if (nnProp == null) {
            // Check if the server did not send anything. In this case we simply
            // return
            return;
        }
        String nickName = nnProp.getPropertyValueAsString();
        if (nickName == null) {
            // Check if the server did not send anything. In this case we simply
            // return
            return;
        } else if ("".equals(nickName)) {
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
                CommonDataKinds.Nickname.CONTENT_ITEM_TYPE);

        builder = builder.withValue(CommonDataKinds.Nickname.TYPE,
                CommonDataKinds.Nickname.TYPE_DEFAULT);

        builder = builder.withValue(CommonDataKinds.Nickname.NAME, nickName);

        ContentProviderOperation operation = builder.build();
        ops.add(operation);
    }

    public Vector<com.funambol.syncml.protocol.Property> getSupportedProperties() {
        Vector<com.funambol.syncml.protocol.Property> properties =
                new Vector<com.funambol.syncml.protocol.Property>();

        addProperty(properties, "BEGIN",   new String[] {"VCARD"}, null);
        addProperty(properties, "END",     new String[] {"VCARD"}, null);
        addProperty(properties, "VERSION", new String[] {"2.1"},   null);

        addProperty(properties, "N",                   null, null);
        addProperty(properties, "NICKNAME",            null, null);
        addProperty(properties, "BDAY",                null, null);
        addProperty(properties, "TITLE",               null, null);
        addProperty(properties, "ORG",                 null, null);
        addProperty(properties, "NOTE",                null, null);
        addProperty(properties, "X-ANNIVERSARY",       null, null);
        addProperty(properties, "X-FUNAMBOL-CHILDREN", null, null);
        addProperty(properties, "X-SPOUSE",            null, null);
        addProperty(properties, "UID",                 null, null);
        addProperty(properties, "TZ",                  null, null);
        addProperty(properties, "REV",                 null, null);
        addProperty(properties, "GEO",                 null, null);
       if(customization.isDisplayNameSupported()) {
            addProperty(properties, "FN",              null, null);
        }

        addProperty(properties, "TEL", null, new PropParam[] {
            new PropParam("TYPE", null,
                    new String[] {"VOICE,HOME",
                                  "VOICE,WORK",
                                  "CELL",
                                  "VOICE",
                                  "FAX,HOME",
                                  "FAX,WORK",
                                  "PAGER",
                                  "WORK,PREF",
                                  "FAX",
                                  "PREF,VOICE"},
            null)
        });

        addProperty(properties, "EMAIL", null, new PropParam[] {
            new PropParam("TYPE", null,
                    new String[] {"INTERNET",
                                  "INTERNET,HOME",
                                  "INTERNET,WORK",
                                  "INTERNET,HOME,X-FUNAMBOL-INSTANTMESSENGER",},
            null)
        });

        addProperty(properties, "ADR", null, new PropParam[] {
            new PropParam("TYPE", null,
                    new String[] {"HOME",
                                  "WORK"},
            null)
        });

        addProperty(properties, "URL", null, new PropParam[] {
            new PropParam("TYPE", null,
                    new String[] {"HOME",
                                  "WORK"},
            null)
        });

        addProperty(properties, "PHOTO", null, new PropParam[] {
            new PropParam("TYPE", null,
                    new String[] {"BMP", "JPEG", "PNG", "GIF"}, null),
            new PropParam("ENCODING", null, new String[] {"BASE64"}, null)
        });

        return properties;
    }
}
