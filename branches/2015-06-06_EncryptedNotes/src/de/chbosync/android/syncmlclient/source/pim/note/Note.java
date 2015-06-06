/*
 * Funambol is a mobile platform developed by Funambol, Inc.
 * Copyright (C) 2011 Funambol, Inc.
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

package de.chbosync.android.syncmlclient.source.pim.note;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.funambol.common.pim.model.common.Property;
import com.funambol.util.Log;


/**
 * A Note item with a body and title.
 */
public class Note {

    /** Regular expression for heuristic detection of encrypted notes; lazy creation. */ 
    protected static Pattern _regexpPatternForEncryptionDetection = null;
    
    
    private long     id     = -1;
    private Property title  = null;
    private Property body   = null;
    
    /** Fallback text for empty notes, to be used in title and/or body. */
    protected static String TEXT_EMPTY_NOTE = "<Empty Note>";


    /**
     * Default constructor.
     */
    public Note() {
        super();
    }

    public void setId(String id) {
        setId(Long.parseLong(id));
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public void setTitle(Property title) {
        this.title = title;
    }

    public Property getTitle() {
        return title;
    }

    public void setBody(Property body) {
        this.body = body;
    }

    public Property getBody() {
        return body;
    }

    public void setPlainText(byte note[]) throws UnsupportedEncodingException {
        // there is just a body for plain notes
        String bodyValue = new String(note, "UTF-8");
        
        if (bodyValue.trim().length() > 0) {
            body  = new Property(bodyValue);
            title = new Property( extractTitle(bodyValue) );        	
        } else {
        	body  = new Property(TEXT_EMPTY_NOTE);
        	title = new Property(TEXT_EMPTY_NOTE);
        }
        
    }    
    
    
    /**
     * Method to obtain title from the whole body of a note
     * (first non-empty line is returned as title).
     * Added for ChBoSync.
     * 
     * @param body Whole payload text of the note.
     * @return String to be used as title (first non-empty line).
     */
    protected String extractTitle(String bodyString) {
    	String currentLine = null;    	   
    	
    	try {
    		BufferedReader reader = new BufferedReader( new StringReader(bodyString) );
    		while ( (currentLine = reader.readLine() ) != null ) {
    		
    			currentLine = currentLine.trim();
    			if ( currentLine.length() > 0) {
    				return currentLine;
    			}
    		
    		} // end-while
    	}
    	catch (IOException ex) {
    		Log.error("Exception when parsing note's body for extraction of title: " + ex);
    		return TEXT_EMPTY_NOTE;
    	}
    	
    	return TEXT_EMPTY_NOTE;   	
    }
    
    
    /**
     * Heuristic check if the note is an encrypted one (encrypted by OISafe as companion app for OINotepad).
     * The payload (body) of an encrypted note consists just of hexadecimal numbers without blanks, 
     * so a regular expression is used to find out if this note is encrypted or not.
     * If a note with just one letter is encrypted then the cipher has a length of 129 hex numbers,
     * so it is checked for at least 100 hex numbers.
     * <br/><br/>
     * Added for ChBoSync.
     * 
     * @return <tt>true</tt> if note seems to be an encrypted one, <tt>false</tt> otherwise.
     */
    public boolean isNoteEncrypted() {
    	
    	if ( _regexpPatternForEncryptionDetection == null) {
    		// Regular expression to check for at least 100 hexadecimal numbers
    		_regexpPatternForEncryptionDetection = Pattern.compile("[[a-f][0-9]]{100,}", Pattern.CASE_INSENSITIVE);
    	}
    	

    	// Check if body of note is empty
    	String bodyString = getBody().getPropertyValueAsString();
    	if (bodyString == null) 
    		return false;
    	
    	bodyString = bodyString.trim();
        if (bodyString.length() == 0)
        	return false;
    	        
        
        // If we come to this point then the note is not empty
        
    	Matcher matcher = _regexpPatternForEncryptionDetection.matcher(bodyString) ;
    	
    	boolean resultBool = matcher.matches();
    	
    	return resultBool;
    }

    
    /**
     * Write plain text into OutputStream
     * 
     * @param os         Stream into which the plain text is written
     * @param allFields  Not used
     * @throws IOException
     */
    public void toPlainText(OutputStream os, boolean allFields) throws IOException {
        try {
            String bodyValue = Property.stringFrom(body);
            if (bodyValue == null) {
                bodyValue = "";
            }
            byte bytes[] = bodyValue.getBytes("UTF-8");
            os.write(bytes);
        } catch (Exception ex) {
            Log.error("Cannot format plain note" + ex);
            throw new IOException("Cannot format note");
        }
    }
}
