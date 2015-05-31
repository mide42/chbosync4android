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

package de.chbosync.android.syncmlclient.source.pim.calendar;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.TimeZone;
import java.util.Vector;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.text.format.Time;

import com.funambol.client.source.AppSyncSource;
import com.funambol.common.pim.icalendar.ICalendarSyntaxParser;
import com.funambol.common.pim.model.calendar.Event;
import com.funambol.common.pim.model.calendar.ExceptionToRecurrenceRule;
import com.funambol.common.pim.model.calendar.RecurrencePattern;
import com.funambol.common.pim.model.calendar.Reminder;
import com.funambol.common.pim.model.common.Property;
import com.funambol.common.pim.model.common.PropertyWithTimeZone;
import com.funambol.common.pim.model.converter.ConverterException;
import com.funambol.common.pim.model.converter.VCalendarConverter;
import com.funambol.common.pim.model.icalendar.ICalendarSyntaxParserListenerImpl;
import com.funambol.common.pim.model.model.VCalendar;
import com.funambol.common.pim.model.utility.TimeUtils;
import com.funambol.common.pim.vcalendar.CalendarUtils;
import com.funambol.util.DateUtil;
import com.funambol.util.Log;
import com.funambol.util.StringUtil;

import de.chbosync.android.syncmlclient.source.AbstractDataManager;


/**
 * AbstractDataManager for the calendars entries: does the effective events'
 * addition, modifications and deletion.
 */
public class CalendarManager extends AbstractDataManager<Calendar> {

    private static final String LEGACY_ACCESS_LEVEL_STRING_PRIVATE = "PRIVATE";
    private static final String LEGACY_ACCESS_LEVEL_STRING_PUBLIC  = "PUBLIC";

	/** Log entries tag */
    private static final String TAG_LOG = "CalendarManager";

    private static final int MAX_OPS_PER_BATCH = 499;

    private static final int COMMIT_THRESHOLD = MAX_OPS_PER_BATCH - 20;

    private AppSyncSource appSource = null;

    private int lastEventBackRef = -1;

    private ArrayList<ContentProviderOperation> ops = null;

    private Vector<String> newKeys = null;
    private List<Integer> eventsIdx = null;

    /**
     * Default constructor.
     * @param context the Context object 
     * @param appSource the AppSyncSource object to be related to this manager
     */
    public CalendarManager(Context context, AppSyncSource appSource) {
        super(context);
        this.appSource = appSource;
    }

    @Override
    public void beginTransaction() {
        ops = new ArrayList<ContentProviderOperation>();
        eventsIdx = new ArrayList<Integer>();
        newKeys = new Vector<String>();
    }

    /**
     * Load a particular calendar entry
     * @param key the long formatted entry key to load
     * @return Calendar the Calendar object related to that entry
     * @throws IOException if anything went wrong accessing the calendar db
     */
    public Calendar load(String key) throws IOException {

        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Loading Event: " + key);
        }

        long id;
        try {
            id = Long.parseLong(key);
        } catch (Exception e) {
            Log.error(TAG_LOG, "Invalid key: " + key, e);
            throw new IOException("Invalid key: " + key);
        }

        Calendar cal = new Calendar();
        cal.setId(id);

        Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id);
        Cursor cursor = resolver.query(uri, null, null, null, null);
        try {
            if(cursor != null && cursor.moveToFirst()) {
                loadCalendarFields(cursor, cal, id);
            } else {
                // Item not found
                throw new IOException("Cannot find event " + key);
            }
        } finally {
            cursor.close();
        }
        return cal;
    }

    public Calendar load(Cursor cursor) throws IOException {
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Loading event from cursor");
        }

        Calendar cal = new Calendar();
        String key = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events._ID));
        Long k = Long.parseLong(key);
        loadCalendarFields(cursor, cal, k);
        return cal;
    }

    /**
     * Add a Calendar item to the db. This operation does not actually create the event, but it setup things
     * so that the event will be created during the commit. This call must be encapsulated into a {@link
     * beginTransaction} and {@link commit}.
     *
     * @param item the Calendar object to be added
     * @return null as the event id is unknown until the event is actually created.
     * @throws IOException if anything went wrong accessing the calendar db
     */
    @Override
    public String add(Calendar item) throws IOException {

        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Adding Event");
        }

        // Commit if it is time to do it
        if (ops.size() >= COMMIT_THRESHOLD) {
            commitSingleBatch();
        }

        Event event = item.getEvent();

        ContentValues cv = createEventContentValues(event);
        Uri uri = asSyncAdapter(CalendarContract.Events.CONTENT_URI, this.accountName, this.accountType);

        ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(uri);
        builder.withValues(cv);
        ops.add(builder.build());
        lastEventBackRef = ops.size() - 1;
        eventsIdx.add(Integer.valueOf(lastEventBackRef));

        addReminders(item, -1);

        return null;
    }

    /**
     * Update a calendar item in the db. This operation does not actually modify the event, but it setup things
     * so that the event will be modified during the commit. This call must be encapsulated into a {@link
     * beginTransaction} and {@link commit}.
     *
     * @param id the calendar key that represents the calendar to be updated
     * @param newItem the Calendar object taht must replace the existing one
     * @throws IOException if anything went wrong accessing the calendar db
     */
    @Override
    public void update(String key, Calendar newItem) throws IOException {

        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Updating event: " + key);
        }

        // Commit if it is time to do it
        if (ops.size() >= COMMIT_THRESHOLD) {
            commitSingleBatch();
        }

        long id;
        try {
            id = Long.parseLong(key);
        } catch(Exception e) {
            Log.error(TAG_LOG, "Invalid item key " + key, e);
            throw new IOException("Invalid item key");
        }

        // If the contact does not exist, then we perform an add
        if (!exists(key)) {
            if (Log.isLoggable(Log.INFO)) {
                Log.info(TAG_LOG, "Tried to update a non existing event. Creating a new one ");
            }
            add(newItem);
            return;
        }

        Event event = newItem.getEvent();

        ContentValues cv = createEventContentValues(event);
        Uri uri = asSyncAdapter(CalendarContract.Events.CONTENT_URI, this.accountName, this.accountType);
        Uri eventUri = ContentUris.withAppendedId(uri, id);
        ContentProviderOperation.Builder builder = ContentProviderOperation.newUpdate(eventUri);
        builder.withValues(cv);
        ops.add(builder.build());

        // For reminders we remove the old ones and add the new one
        deleteRemindersForEvent(id, false);
        addReminders(newItem, id);
    }

    /**
     * Delete a calendar item in the db. This operation does not actually remove the event, but it setup things
     * so that the event will be removed during the commit. This call must be encapsulated into a {@link
     * beginTransaction} and {@link commit}.
     *
     * @param id the calendar key that must be deleted
     * @throws IOException if anything went wrong accessing the calendar db
     */
    @Override
    public void delete(String key) throws IOException {

        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Deleting event with id: " + key);
        }

        // Commit if it is time to do it
        if (ops.size() >= COMMIT_THRESHOLD) {
            commitSingleBatch();
        }

        long itemId;
        try {
            itemId = Long.parseLong(key);
        } catch (Exception e) {
            Log.error(TAG_LOG, "Invalid item key " + key, e);
            throw new IOException("Invalid item key");
        }

        Uri uri = asSyncAdapter(CalendarContract.Events.CONTENT_URI, this.accountName, this.accountType);
        Uri eventUri = ContentUris.withAppendedId(uri, itemId);
        ContentProviderOperation.Builder builder = ContentProviderOperation.newDelete(eventUri);
        ops.add(builder.build());

        deleteRemindersForEvent(itemId, false);
    }

    /**
     * Delete all calendars from the calendar db. This operation does not need to be encapsulated into
     * a transaction as it begins/commit automatically.
     * @throws IOException if anything went wrong accessing the calendar db
     */
    @Override
    public void deleteAll() throws IOException {
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Deleting all events");
        }
        Enumeration<?> keys = getAllKeys();
        beginTransaction();
        while(keys.hasMoreElements()) {
            String key = (String)keys.nextElement();
            delete(key);
            // Delete all reminders associated to this item
            long id = Long.parseLong(key);
            deleteRemindersForEvent(id, false);
        }
        commit();
    }

    @Override
    public Vector<String> commit() throws IOException {
        commitSingleBatch();
        return newKeys;
    }

    private void commitSingleBatch() throws IOException {
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "commitSingleBatch " + ops.size());
        }
        // Now perform all the operations in one shot
        try {
            if (ops.size() > 0) {
                ContentProviderResult[] res = resolver.applyBatch(getAuthority(), ops);
                for(int i=0;i<eventsIdx.size();++i) {
                    int idx = eventsIdx.get(i).intValue();
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
                    newKeys.addElement("" + id);
                }
            }
        } catch (Exception e) {
            Log.error(TAG_LOG, "Cannot commit to database", e);
            throw new IOException("Cannot create event in db");
        } finally {
            ops.clear();
            eventsIdx.clear();
        }
    }

    /**
     * Check if a calendar with the given id exists in the calendar db
     * @param id the id which existence is to be checked
     * @return true if the given id exists in the db false otherwise
     */
    @Override
    public boolean exists(String key) {
        long id;
        try {
            id = Long.parseLong(key);
        } catch (Exception e) {
            Log.error(TAG_LOG, "Invalid item key " + key, e);
            return false;
        }
        Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id);
        Cursor cur = resolver.query(uri, null, null, null, null);
        if(cur == null) {
            return false;
        }
        boolean found = cur.getCount() > 0;
        cur.close();
        return found;
    }

    /**
     * Get all of the calendar keys that exist into the DB
     * @return Enumeration the enumeration object that contains alll of the
     * calendar keys
     * @throws IOException if anything went wrong accessing the calendar db
     */
    public Enumeration getAllKeys() throws IOException {

        CalendarAppSyncSourceConfig config = (CalendarAppSyncSourceConfig)appSource.getConfig();
        if (config.getCalendarId() == -1) {
            throw new IOException("Cannot access undefined calendar");
        }

        String cols[] = {CalendarContract.Events._ID};
        Cursor cursor = resolver.query(CalendarContract.Events.CONTENT_URI, cols,
        		CalendarContract.Events.CALENDAR_ID + "='" + config.getCalendarId() + "'", null, null);
        // The cursor can only be null if the content URI is not correct
        if (cursor == null) {
            Log.error(TAG_LOG, "query returned null, probably the content uri is wrong on this device");
            throw new IOException("Cannot find content provider " + CalendarContract.Events.CONTENT_URI);
        }
        try {
            int size = cursor.getCount();
            Vector<String> itemKeys = new Vector<String>(size);
            if (!cursor.moveToFirst()) {
                return itemKeys.elements();
            }
            for (int i = 0; i < size; i++) {
                String key = cursor.getString(0);
                if (Log.isLoggable(Log.TRACE)) {
                    Log.trace(TAG_LOG, "Found item with key: " + key);
                }
                itemKeys.addElement(key);
                cursor.moveToNext();
            }
            return itemKeys.elements();
        } catch (Exception e) {
            Log.error(TAG_LOG, "Cannot get all items keys: ", e);
            throw new IOException("Cannot get all items keys");
        } finally {
            cursor.close();
        }
    }

    public Vector<com.funambol.syncml.protocol.Property> getSupportedProperties() {
        // TODO: FIXME
        return null;
    }

    private void loadCalendarFields(Cursor cursor, Calendar cal, long key) {

        Event event = new Event();

        // Load SUMMARY
        String summary = cursor.getString(cursor.getColumnIndex(CalendarContract.Events.TITLE));
        if(summary != null) {
            event.setSummary(new Property(summary));
        }
        // Load DESCRIPTION
        String description = cursor.getString(cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION));
        if(description != null) {
            event.setDescription(new Property(description));
        }
        // Load LOCATION
        String location = cursor.getString(cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION));
        if(location != null) {
            event.setLocation(new Property(location));
        }

        // Load TIMEZONE
        String tz = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_TIMEZONE));

        // Load ALL_DAY
        boolean allday = cursor.getInt(cursor.getColumnIndex(CalendarContract.Events.ALL_DAY)) == 1;
        if (allday) {
            event.setAllDay(allday);
        }

        // Load DTSTART
        String dtstart = cursor.getString(cursor.getColumnIndex(CalendarContract.Events.DTSTART));
        if(!StringUtil.isNullOrEmpty(dtstart)) {
            // The dstart was shifted to UTC because expressed as msecs
            dtstart = CalendarUtils.formatDateTime(Long.parseLong(dtstart), allday, tz);
            event.setDtStart(new PropertyWithTimeZone(dtstart, tz));
        }
        // Load DTEND
        String dtend = cursor.getString(cursor.getColumnIndex(CalendarContract.Events.DTEND));
        if(!StringUtil.isNullOrEmpty(dtend)) {
            // Substract a day if this is an all day event
            long endMillis = Long.parseLong(dtend);
            if(allday) {
                endMillis -= CalendarUtils.DAY_FACTOR;
            }
            dtend = CalendarUtils.formatDateTime(endMillis, allday, tz);
            event.setDtEnd(new PropertyWithTimeZone(dtend, tz));
        }
        // Load DURATION
        String duration = cursor.getString(cursor.getColumnIndex(CalendarContract.Events.DURATION));
        if (duration != null) {
            event.setDuration(new Property(duration));
        }

        // Load VISIBILITY_CLASS
        int vclass  = cursor.getInt(cursor.getColumnIndex(CalendarContract.Events.ACCESS_LEVEL));
        if(vclass != CalendarContract.Events.ACCESS_DEFAULT && vclass != CalendarContract.Events.ACCESS_CONFIDENTIAL) {
            if(vclass == CalendarContract.Events.ACCESS_PRIVATE) {
                event.setAccessClass(new Property(LEGACY_ACCESS_LEVEL_STRING_PRIVATE)); //legacy Funambol value
            } else if(vclass == CalendarContract.Events.ACCESS_PUBLIC) {
                event.setAccessClass(new Property(LEGACY_ACCESS_LEVEL_STRING_PUBLIC)); //legacy Funambol value
            }
        }

        // Load REMINDER
        int hasRem = cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Events.HAS_ALARM));
        if (hasRem == 1) {
            if (Log.isLoggable(Log.TRACE)) {
                Log.trace(TAG_LOG, "This event has an alarm associated");
            }
            String fields[] = { CalendarContract.Reminders.MINUTES };
            String whereClause = CalendarContract.Reminders.EVENT_ID + " = " + key;
            Cursor rems = resolver.query(CalendarContract.Reminders.CONTENT_URI,
                                         fields, whereClause, null, null);
            try {
                if (rems != null && rems.moveToFirst()) {
                    int mins = rems.getInt(rems.getColumnIndexOrThrow(CalendarContract.Reminders.MINUTES));
                    Reminder rem = new Reminder();
                    rem.setMinutes(mins);
                    rem.setActive(true);
                    event.setReminder(rem);
                } else {
                    Log.error(TAG_LOG, "Internal error: cannot find reminder for: " + key);
                }
                if (rems != null) {
                    if(rems.moveToNext()) {
                        Log.error(TAG_LOG, "Only one reminder is currently supported, ignoring the others");
                    }
                }
            } finally {
                if (rems != null) {
                    rems.close();
                }
            }
        }

        // Load recurrence
        String rrule  = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.RRULE));
        if (rrule != null && rrule.length() > 0) {
            try {
                String exdate  = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.EXDATE));
                String exrule  = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.EXRULE));
                String rdate   = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.RDATE));
        
                RecurrencePattern rp = createRecurrencePattern(dtstart, rrule, 
                        exdate, exrule, rdate, tz, allday);
                if (rp == null) {
                    Log.error(TAG_LOG, "Cannot load recurrence");
                } else {
                    event.setRecurrencePattern(rp);
                }
            } catch (Exception e) {
                Log.error(TAG_LOG, "Cannot load recurrence", e);
            }
        }

        cal.setEvent(event);
    }

    /**
     * Fills a new ContentValues objects with all the given Event's properties
     * @param event the event to be used to fill the ContentValue object
     * @return ContentValues the filled ContenValues object.
     * @throws IOException if anything went wrong accessing the calendar db
     */
    private ContentValues createEventContentValues(Event event) throws IOException {

        CalendarAppSyncSourceConfig config = (CalendarAppSyncSourceConfig)appSource.getConfig();
        long calendarId = config.getCalendarId();
        if (calendarId == -1) {
            throw new IOException("Cannot use undefined calendar");
        }

        ContentValues cv = new ContentValues();

        // Put String properties
        putStringProperty(CalendarContract.Events.TITLE,       event.getSummary(), cv);
        putStringProperty(CalendarContract.Events.DESCRIPTION, event.getDescription(), cv);
        putStringProperty(CalendarContract.Events.EVENT_LOCATION,    event.getLocation(), cv);

        // Put date properties
        PropertyWithTimeZone start = event.getDtStart();
        PropertyWithTimeZone end = event.getDtEnd();

        boolean allDay = false;
        if(putAllDay(event, cv)) {
            // We must take the event TZ into account for this to work
            allDay = true;
        }

        putDateTimeProperty(CalendarContract.Events.DTSTART, start, cv, allDay, false);

        // Android requires that we set DURATION or DTEND for all events
        Property duration = event.getDuration();
        if (!Property.isEmptyProperty(duration)) {
            putStringProperty(CalendarContract.Events.DURATION, duration, cv);
        } else if (!Property.isEmptyProperty(end)) {
            // For rucurring events we must save the duration instead of the dtend
            if(event.getRecurrencePattern() != null) {
                // This piece of code computes the duration given a dtstart and
                // dtend. Probably redundant, do do not set it.
                if (!Property.isEmptyProperty(start)) {

                    java.util.Calendar startCal = DateUtil.parseDateTime(
                            start.getPropertyValueAsString());
                    java.util.Calendar endCal = DateUtil.parseDateTime(
                            end.getPropertyValueAsString());

                    long endMillis = endCal.getTimeInMillis();
                    long startMillis = startCal.getTimeInMillis();
                    long d = endMillis - startMillis;
                    int seconds = (int)(d / (1000));

                    // Duration should be formatted as 'P<seconds>S'
                    StringBuffer newDuration = new StringBuffer(10);
                    newDuration.append("P");
                    newDuration.append(seconds);
                    newDuration.append("S");

                    if (Log.isLoggable(Log.TRACE)) {
                        Log.trace(TAG_LOG, "Setting duration to: " + newDuration);
                    }
                    putStringProperty(CalendarContract.Events.DURATION, new Property(
                            newDuration.toString()), cv);
                } else {
                    // Should never happen
                    // Use a default DURATION of 1 in this case
                    putStringProperty(CalendarContract.Events.DURATION, new Property("P1D"), cv);
                }
            } else {
                putDateTimeProperty(CalendarContract.Events.DTEND, end, cv, allDay, true);
            }
        } else {
            // Use a default DURATION of 1 in this case
            putStringProperty(CalendarContract.Events.DURATION, new Property("P1D"), cv);
        }

        // Put Timezone
        putTimeZone(event.getDtStart(), cv, allDay);

        // Put visibility class property
        putVisibilityClass(event.getAccessClass(), cv);

        // Put constant values
        cv.put(CalendarContract.Events.HAS_ATTENDEE_DATA, 1);

        // Set the hasAlarm property
        Reminder rem = event.getReminder();
        cv.put(CalendarContract.Events.HAS_ALARM, rem != null);

        try {
            putRecurrence(event, cv);
        } catch (Exception e) {
            Log.error(TAG_LOG, "Cannot convert recurrence rule", e);
            throw new IOException("Cannot write recurrence rule");
        }

        // Put Calendar references
        cv.put(CalendarContract.Events.CALENDAR_ID, calendarId);

        return cv;
    }

    /**
     * Put a String property to the given ContentValues.
     * @param column the culumn to be written
     * @param property the property to be written into the column
     * @param cv the content values related to the property
     */
    private void putStringProperty(String column,
            Property property, ContentValues cv) {
        if(property != null) {
            String value = property.getPropertyValueAsString();
            if(value != null) {
                value = StringUtil.replaceAll(value, "\r\n", "\n");
                value = StringUtil.replaceAll(value, "\r",   "\n");
                cv.put(column, value);
            }
        }
    }

    /**
     * Put a date time property to the given ContentValues.
     * @param column the culumn to be written
     * @param property the property to be written into the column
     * @param cv the content values related to the property
     * @param allday tells that the given date is an all day
     * @param addOneDay add one day in milliseconds to the given date
     */
    private void putDateTimeProperty(String column, PropertyWithTimeZone property,
                                     ContentValues cv, boolean allDay, boolean addOneDay)
    {
        if (property != null) {
            if (allDay) {
                String date = property.getPropertyValueAsString();
                try {
                    TimeZone tz = null;
                    if (property.getTimeZone() != null) {
                        tz = TimeZone.getTimeZone(property.getTimeZone());
                    }
                    date = TimeUtils.convertUTCDateToLocal(date, tz);
                } catch(Exception ex) {
                    Log.error(TAG_LOG, "Cannot convert to local time", ex);
                }

                // Android dislike events all day with a date whose hour/min/sec
                // are not zero (the calendar app crashes). For this reason we
                // remove any time info
                int tIdx = date.indexOf("T");
                if (tIdx != -1) {
                    date = date.substring(0, tIdx);
                }
                // TimeZone for all day properties must be UTC
                property = new PropertyWithTimeZone(date, "UTC");
            }

            String value = property.getPropertyValueAsString();
            if(value != null) {
                long time = CalendarUtils.getLocalDateTime(value, property.getTimeZone());
                if(allDay && addOneDay) {
                    time += CalendarUtils.DAY_FACTOR;
                }
                cv.put(column, time);
            }
        }
    }

    /**
     * Put the allday property to the given ContentValues.
     * @param event the event that contains the all day property
     * @param cv the content values related to the event
     */
    private boolean putAllDay(Event event, ContentValues cv) {
        int allday = event.isAllDay() ? 1 : 0;
        cv.put(CalendarContract.Events.ALL_DAY, allday);
        return event.isAllDay();
    }

    /**
     * Put the timezone property to the given ContentValues.
     * @param property the TZ to be set
     * @param cv the contentValues where to put the TZ
     */
    private void putTimeZone(PropertyWithTimeZone property, ContentValues cv,
            boolean allDay) {
        if(property != null) {
            String tz = allDay ? "UTC" : property.getTimeZone();
            if(!StringUtil.isNullOrEmpty(tz)) {
                cv.put(CalendarContract.Events.EVENT_TIMEZONE, tz);
            }
        }
    }

    /**
     * Put the visibility class property to the given ContentValues.
     * @param property the visibility property container
     * @param cv the Content value to be updated
     */
    private void putVisibilityClass(Property property, ContentValues cv) {
        if(property != null) {
            String vclass = property.getPropertyValueAsString();
            if(!StringUtil.isNullOrEmpty(vclass)) {
                if(LEGACY_ACCESS_LEVEL_STRING_PRIVATE.equals(vclass)) {
                    cv.put(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_PRIVATE);
                } else if(LEGACY_ACCESS_LEVEL_STRING_PUBLIC.equals(vclass)) {
                    cv.put(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_PUBLIC);
                }
            }
        }
    }

    private void addReminders(Calendar item, long eventId) throws IOException {
        // Create a new entry in the Reminders table if necessary
        Reminder rem = item.getEvent().getReminder();
        if (rem != null) {
            int mins = -1;
            if (rem.getMinutes() > 0) {
                mins = rem.getMinutes();
            } else if (rem.getTime() != null) {
                Log.error(TAG_LOG, "Reminder as absloute value not implemented yet");
                /* TODO FIXME
                String time = rem.getTime();
                if (Log.isLoggable(Log.INFO)) {
                    Log.info(TAG_LOG, "Reminder time: " + time);
                }
                try {
                    Date remDate = DateFormat.getDateInstance().parse(time);
                    if (Log.isLoggable(Log.INFO)) {
                        Log.info(TAG_LOG, "remDate=" + remDate.toString());
                    }
                } catch (Exception e) {
                    Log.error(TAG_LOG, "Cannot parse reminder date, ignoring reminder");
                }
                */
            }

            if (mins != -1) {
                // We need to specify the time as minutes before the start
            	Uri uri = asSyncAdapter(CalendarContract.Reminders.CONTENT_URI, this.accountName, this.accountType);
                ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(uri);

                // When inserting we shall use a back reference, while on update we have the event id
                if (eventId == -1) {
                    builder.withValueBackReference(CalendarContract.Reminders.EVENT_ID, lastEventBackRef);
                } else {
                    builder.withValue(CalendarContract.Reminders.EVENT_ID, eventId);
                }
                builder.withValue(CalendarContract.Reminders.MINUTES, mins);
                builder.withValue(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_DEFAULT);
                ops.add(builder.build());
            }
        }
    }

    private void deleteRemindersForEvent(long itemId, boolean resetHasAlarm) throws IOException {

        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Deleting reminders for item: " + itemId);
        }

        // TODO FIXME: if we don't sync all of the reminders, we shall remove
        // only the first one, which is the one we sync...
        Uri uri = asSyncAdapter(CalendarContract.Reminders.CONTENT_URI, this.accountName, this.accountType);
        uri = ContentUris.withAppendedId(uri, itemId);
        ContentProviderOperation.Builder builder = ContentProviderOperation.newDelete(uri);
        ops.add(builder.build());

        if (resetHasAlarm) {
            // TODO: reset the has alarm in the original item
        }
    }

    private void putRecurrence(Event event, ContentValues cv) throws ConverterException {
        // We basically need to transform a vCal rec rule into an iCal rec rule
        // This can be done in different ways. One possibility was to used the
        // VCalendarConverter and the VComponentWriter, but this would not give
        // us any ability to modify the generated RRULE to fix issues. For this
        // reason that method has been discarded even if implementation wise it
        // would have been simpler.
        if (Log.isLoggable(Log.TRACE)) {
            Log.trace(TAG_LOG, "Saving recurrence");
        }
        RecurrencePattern rp = event.getRecurrencePattern();
        if (rp != null) {
            StringBuffer result = new StringBuffer(60); // Estimate 60 is needed
            String typeDesc = rp.getTypeDesc();

            if (typeDesc != null) {
                result.append("FREQ=");
                if ("D".equals(typeDesc)) {
                    result.append("DAILY");
                } else if ("W".equals(typeDesc)) {
                    result.append("WEEKLY");
                } else if ("YM".equals(typeDesc) || "YD".equals(typeDesc)) {
                    result.append("YEARLY");
                } else if ("MP".equals(typeDesc) || "MD".equals(typeDesc)) {
                    // This ia by position recurrence
                    result.append("MONTHLY");
                }
                int interval = rp.getInterval();
                if(interval > 1) {
                    result.append(";INTERVAL=").append(interval);
                }
            }
            int count = rp.getOccurrences();
            if (count > 0 && rp.isNoEndDate()) {
                result.append(";COUNT=").append(count);
            }
            if (!rp.isNoEndDate()               &&
                 rp.getEndDatePattern() != null &&
                !rp.getEndDatePattern().equals("")) {

                rp = fixEndDatePattern(rp, true);
                String enddate = rp.getEndDatePattern();
                
                result.append(";UNTIL=").append(enddate);
            }

            if ("W".equals(typeDesc)) {
                StringBuffer days = new StringBuffer();
                for (int i=0; i<rp.getDayOfWeek().size(); i++) {
                    if (days.length() > 0) {
                        days.append(",");
                    }
                    days.append(rp.getDayOfWeek().get(i));
                }
                if (days.length() > 0) {
                    result.append(";BYDAY=").append(days.toString());
                }
            } else if ("MD".equals(typeDesc)) {
                if (Log.isLoggable(Log.TRACE)) {
                    Log.trace(TAG_LOG, "getDayOfMonth=" + rp.getDayOfMonth());
                }
                result.append(";BYMONTHDAY=").append(rp.getDayOfMonth());
            } else if ("MP".equals(typeDesc)) {
                int instance = rp.getInstance();
                short mask = rp.getDayOfWeekMask();
                addDaysOfWeek(result, instance, mask);
            } else if ("YM".equals(typeDesc)) {
                short monthOfYear = rp.getMonthOfYear();
                if (monthOfYear > 0) {
                    result.append(";BYMONTH=").append(monthOfYear);
                }
                int instance = rp.getInstance();
                short mask = rp.getDayOfWeekMask();
                addDaysOfWeek(result, instance, mask);
            } else if ("YD".equals(typeDesc)) {
                // This is not supported by the calendar model
            }

            // Add the RRULE field
            String rule = result.toString();
            if (Log.isLoggable(Log.INFO)) {
                Log.info(TAG_LOG, "Setting rrule in event to: " + rule);
            }
            cv.put(CalendarContract.Events.RRULE, rule);

            // Add the EXDATE and RDATE fields
            List<ExceptionToRecurrenceRule> exceptions = rp.getExceptions();
            StringBuffer exdate = new StringBuffer();
            StringBuffer rdate = new StringBuffer();
            for(ExceptionToRecurrenceRule ex : exceptions) {
                String date = ex.getDate();
                if(ex.isAddition()) {
                    if(rdate.length() > 0) {
                        rdate.append(',');
                    }
                    rdate.append(date);
                } else {
                    if(exdate.length() > 0) {
                        exdate.append(',');
                    }
                    exdate.append(date);
                }
            }
            if (Log.isLoggable(Log.INFO)) {
                Log.info(TAG_LOG, "Setting exdate in event to: " + exdate.toString());
            }
            cv.put(CalendarContract.Events.EXDATE, exdate.toString());
            if (Log.isLoggable(Log.INFO)) {
                Log.info(TAG_LOG, "Setting rdate in event to: " + rdate.toString());
            }
            cv.put(CalendarContract.Events.RDATE, rdate.toString());
        }
    }

    private void addDaysOfWeek(StringBuffer result, int instance, short mask) {
        StringBuffer daysOfWeek = new StringBuffer();
        if ((mask & RecurrencePattern.DAY_OF_WEEK_SUNDAY) != 0) {
            addDayOfWeek(daysOfWeek, instance, "SU");
        }
        if ((mask & RecurrencePattern.DAY_OF_WEEK_MONDAY) != 0) {
            addDayOfWeek(daysOfWeek, instance, "MO");
        }
        if ((mask & RecurrencePattern.DAY_OF_WEEK_TUESDAY) != 0) {
            addDayOfWeek(daysOfWeek, instance, "TU");
        }
        if ((mask & RecurrencePattern.DAY_OF_WEEK_WEDNESDAY) != 0) {
            addDayOfWeek(daysOfWeek, instance, "WE");
        }
        if ((mask & RecurrencePattern.DAY_OF_WEEK_THURSDAY) != 0) {
            addDayOfWeek(daysOfWeek, instance, "TH");
        }
        if ((mask & RecurrencePattern.DAY_OF_WEEK_FRIDAY) != 0) {
            addDayOfWeek(daysOfWeek, instance, "FR");
        }
        if ((mask & RecurrencePattern.DAY_OF_WEEK_SATURDAY) != 0) {
            addDayOfWeek(daysOfWeek, instance, "SA");
        }
        if (daysOfWeek.length() > 0) {
            result.append(";BYDAY=").append(daysOfWeek.toString());
        }
    }

    private void addDayOfWeek(StringBuffer dayOfWeek, int instance, String day) {
        if (dayOfWeek.length() > 0) {
            dayOfWeek.append(",");
        }
        if (instance != 0) {
            dayOfWeek.append(instance);
        }
        dayOfWeek.append(day);
    }


    private RecurrencePattern createRecurrencePattern(String dtstart, String rrule,
            String exdate, String exrule, String rdate, String tz, boolean allday)
            throws Exception {

        // We must parse an ICalendar recurrence
        StringBuffer event = new StringBuffer();
        event.append("BEGIN:VCALENDAR\r\n")
             .append("VERSION:2.0\r\n")
             .append("BEGIN:VEVENT\r\n")
             .append("DTSTART:").append(dtstart).append("\r\n");

        event.append("RRULE:").append(rrule).append("\r\n");
        if (exdate != null) {
            event.append("EXDATE:").append(exdate).append("\r\n");
        }
        if (exrule != null) {
            event.append("EXRULE:").append(exrule).append("\r\n");
        }
        if (rdate != null) {
            event.append("RDATE:").append(rdate).append("\r\n");
        }

        event.append("END:VEVENT\r\n")
             .append("END:VCALENDAR\r\n");

        ByteArrayInputStream buffer = new ByteArrayInputStream(event.toString().getBytes());

        VCalendar vcalendar = new VCalendar();
        ICalendarSyntaxParserListenerImpl listener = new ICalendarSyntaxParserListenerImpl(vcalendar);
        ICalendarSyntaxParser parser = new ICalendarSyntaxParser(buffer);
        parser.setListener(listener);
        parser.parse();

        vcalendar.addProperty("VERSION", "2.0");
        // In the iCalendar event we synthetized, we did not specify any
        // vtimezone. In this case the converter will consider the event in the
        // timezone supplied in the constructor. For this reason we create the
        // converter in the timezone of the vcal event
        TimeZone tZone;
        if (tz != null) {
            tZone = TimeZone.getTimeZone(tz);
        } else {
            // Use the device default TZ in this case
            tZone = TimeZone.getDefault();
        }
        VCalendarConverter vcf = getConverter(tZone, allday);
        Event e = vcf.vcalendar2calendar(vcalendar).getEvent();

        RecurrencePattern rp = e.getRecurrencePattern();

        return fixEndDatePattern(rp, false);
    }

    /**
     * Fix the end date in the recurrence pattern.
     * Android saves the end date pattern as the first occurence to exclude
     * minus 1 second. According to the vCalendar specification the end date
     * "Controls when a repeating event terminates. The enddate is the last
     * time an event can occur."
     * So if the end date we read from the database is in the form:
     * 20101010T105959Z we should retrieve the last occurence previous to
     * that one.
     */
    private RecurrencePattern fixEndDatePattern(RecurrencePattern rp, boolean incoming) {
        if(rp != null) {
            String enddate = rp.getEndDatePattern();
            if((enddate != null) && 
                ((!incoming && (enddate.endsWith("59") || enddate.endsWith("59Z"))) ||
                   incoming)) {

                if (Log.isLoggable(Log.DEBUG)) {
                    Log.debug(TAG_LOG, "Fixing end date in recurrence field");
                }
                if (Log.isLoggable(Log.DEBUG)) {
                    Log.debug(TAG_LOG, "Old end date: " + enddate);
                }

                String tz = rp.getTimeZone();
                Time endDateTime = new Time(tz != null ? tz : "UTC");
                endDateTime.parse(enddate);
                if(incoming) {
                    endDateTime.second--;
                } else {
                    endDateTime.second++;
                }
                switch(rp.getTypeId()) {
                    case RecurrencePattern.TYPE_MONTHLY:
                        if(incoming) {
                            endDateTime.month++;
                        } else {
                            endDateTime.month--;
                        }
                        break;
                    case RecurrencePattern.TYPE_YEARLY:
                        if(incoming) {
                            endDateTime.year++;
                        } else {
                            endDateTime.year--;
                        }
                        break;
                    case RecurrencePattern.TYPE_DAILY:
                    default:
                        if(incoming) {
                            endDateTime.monthDay++;
                        } else {
                            endDateTime.monthDay--;
                        }
                        break;
                }
                endDateTime.normalize(false); // Do not ignore DST

                // We cannot use the format2445 method because we found a bug
                // where certain dates are not properly formatted, resulting in
                // invalid dates that are later rejected by the calendar
                // provider
                long t = endDateTime.toMillis(false);
                enddate = DateUtil.formatDateTimeUTC(t);

                if (Log.isLoggable(Log.DEBUG)) {
                    Log.debug(TAG_LOG, "New end date: " + enddate);
                }
                rp.setEndDatePattern(enddate);
            }
        }
        return rp;
    }

    private VCalendarConverter getConverter(TimeZone tZone, boolean allday) {
        if (allday) {
            // For all day events we should use the device's default timezone in
            // order to convert the pattern end date to the related local time.
            return new VCalendarConverter(TimeZone.getDefault(), "UTF-8", false);
        } else {
            return new VCalendarConverter(tZone, "UTF-8", false);
        }
    }

    /**
     * Adds a parameter which tells Android NOT to mark modified entries as (sync-)dirty
     * @param uri
     * @param account
     * @param accountType
     * @return
     */
    public static Uri asSyncAdapter(Uri uri, String account, String accountType) {
        return uri.buildUpon()
            .appendQueryParameter(android.provider.CalendarContract.CALLER_IS_SYNCADAPTER,"true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, account)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, accountType).build();
     }

	@Override
	protected String getAuthority() {
		return CalendarContract.AUTHORITY;
	}

}
