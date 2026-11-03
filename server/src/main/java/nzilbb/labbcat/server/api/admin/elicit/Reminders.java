//
// Copyright 2026 New Zealand Institute of Language, Brain and Behaviour, 
// University of Canterbury
// Written by Robert Fromont - robert.fromont@canterbury.ac.nz
//
//    This file is part of LaBB-CAT.
//
//    LaBB-CAT is free software; you can redistribute it and/or modify
//    it under the terms of the GNU Affero General Public License as published by
//    the Free Software Foundation; either version 3 of the License, or
//    (at your option) any later version.
//
//    LaBB-CAT is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with LaBB-CAT; if not, write to the Free Software
//    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
package nzilbb.labbcat.server.api.admin.elicit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Vector;
import java.util.regex.Pattern;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import nzilbb.labbcat.server.api.TableServletBase;
import nzilbb.labbcat.server.api.RequiredRole;

/**
 * <tt>/api/admin/elicit/reminders/<var>task_id</var>[/<var>reminder_id</var>]</tt> 
 * : Administration of elicitation task reminder records for mobile app deployments.
 *  <p> Allows administration (Create/Read/Update/Delete) of default task reminder records
 *  via JSON-encoded objects with the following attributes:
 *   <dl>
 *    <dt> task_id </dt><dd> The database key for the elicitation task. </dd>
 *    <dt> reminder_id </dt><dd> The database key for the reminder record. </dd>
 *    <dt> label </dt><dd> The participant-facing name of the reminder. </dd>
 *    <dt> participant_pattern </dt><dd> Regular expression that matches the participant ID
 *         of participants for whom these reminders will be set. </dd>
 *    <dt> reminder_day </dt><dd> The day of the reminder, one of:
 *     <ul>
 *      <li><tt>daily</tt> : Daily</li>
 *      <li><tt>2</tt> : Every other day</li>
 *      <li><tt>3</tt> : Every third day</li>
 *      <li><tt>4</tt> : Every fourth day</li>
 *      <li><tt>5</tt> : Every fifth day</li>
 *      <li><tt>6</tt> : Every sixth day</li>
 *      <li><tt>weekly</tt> : Weekly</li>
 *      <li><tt>monday</tt> : Monday</li>
 *      <li><tt>tuesday</tt> : Tuesday</li>
 *      <li><tt>wednesday</tt> : Wednesday</li>
 *      <li><tt>thursday</tt> : Thursday</li>
 *      <li><tt>friday</tt> : Friday</li>
 *      <li><tt>saturday</tt> : Saturday</li>
 *      <li><tt>sunday</tt> : Sunday</li>
 *      <tt>2-weekly</tt> : Fortnightly</li>
 *     </ul>
 *    </dd>
 *    <dt> reminder_time </dt><dd> Time of day of the reminder. </dd>
 *    <dt> from_day </dt><dd>Reminders start this many days after they install the app.</dd>
 *    <dt> to_day </dt><dd> Reminders end this many days after they install the app. </dd>
 *   </dl>
 *  <p> The following operations, specified by the HTTP method, are supported:
 *   <dl>
 *    <dt> POST </dt><dd> Create a new record.
 *     <ul>
 *      <li><em> Request Body </em> - a JSON-encoded object representing the new record
 *       (excluding <var>reminder_id</var>). </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object representing the new record (including <var>reminder_id</var>). </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was successfully created. </li>
 *        </ul>
 *      </li>
 *     </ul></dd> 
 * 
 *    <dt> GET </dt><dd> 
 *     Read the reminder records for the given task. 
 *     <ul>
 *      <li><em> Request Path </em> - <tt>/api/admin/elicit/reminders/<var>task_id</var></tt>
 *          where  <var> task_id </var> is the task ID.</li>
 *      <li><em> Parameters </em>
 *        <ul>
 *         <li><em> pageNumber </em> (integer) : The (zero-based) page to return. </li>
 *         <li><em> pageLength </em> (integer) : How many rows per page (default is 20). </li>
 *         <li><em> Accept </em> (string) : Equivalent of the "Accept" request header (see below). </li>
 *        </ul>
 *      </li>
 *      <li><em> "Accept" request header/parameter </em> "text/csv" to return records as
 *       Comma Separated Values. If not specified, records are returned as a JSON-encoded
 *       array of objects.</li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as a
 *       corresponding list of records.  </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The records could be listed. </li>
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *    
 *    <dt> PUT </dt><dd> Update an existing record, specified by the
 *         <var> task_id </var> and <var> reminder_id </var> given in the request body.
 *     <ul>
 *      <li><em> Request Body </em> - a JSON-encoded object representing the record. </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object representing the record. </li> 
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was successfully updated. </li>
 *         <li><em> 404 </em> : The record was not found. </li>
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *    
 *    <dt> DELETE </dt><dd> Delete an existing record.
 *     <ul>
 *      <li><em> Request Path </em> - <tt>/api/admin/elicit/reminders/<var>task_id</var>[/<var>reminder_id</var>]</tt> where 
 *          <var> task_id </var> is the task ID and <var> reminder_id </var> is the ID
 *          of the reminder to delete.</li>
 *      <li><em> Response Body </em> - the standard JSON envelope, including a message if
 *          the request succeeds or an error explaining the reason for failure. </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was successfully deleted. </li>
 *         <li><em> 400 </em> : No <var> reminder_id </var> was specified in the URL path,
 *             or the record exists but could not be deleted. </li> 
 *         <li><em> 404 </em> : The record was not found. </li>
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *   </dl>
 *  </p>
 * @author Robert Fromont robert@fromont.net.nz
 */
@RequiredRole("admin")
public class Reminders extends TableServletBase {   
  
  public Reminders() {
    super("elicitation_reminder", // table
          new Vector<String>() {{ // primary keys
            add("task_id");
            add("reminder_id");
          }},
          new Vector<String>() {{ // columns
            add("label");
            add("participant_pattern");
            add("reminder_day");
            add("reminder_time");
            add("from_day");
            add("to_day");
          }},
          "reminder_id"); // order
    
    create = true;
    read = true;
    update = true;
    delete = true;
    
    autoKey = "reminder_id";
    autoKeyQuery = "SELECT COALESCE(max(reminder_id) + 1, 1) FROM elicitation_reminder";
    
  }
  
  /**
   * Validates a record before UPDATEing it.
   * @param request The request.
   * @param record The incoming record to validate, to which attributes can be added.
   * @param connection A connection to the database.
   * @return A JSON representation of the valid record, which may or may not be the same
   * object as <var>record</var>.
   * @throws ValidationException If the record is invalid.
   * @see #createMutableCopy(JsonObject)
   */
  protected JsonObject validateBeforeUpdate(
    JsonObject record, Connection connection) throws ValidationException {
    
    Vector<String> errors = null;
    if (record.containsKey("participant_pattern") &&
        !record.isNull("participant_pattern")) {
      try { // ensure it's a value regular expression
        Pattern.compile(record.getString("participant_pattern"));
      } catch(Exception x) {
        if (errors == null) errors = new Vector<String>();
        errors.add(localize("\"{0}\" is not a valid regular expression: {1}",
                            record.getString("participant_pattern"), x.getMessage()));
      }
    }
    
    if (record.containsKey("reminder_day")
        && !record.isNull("reminder_day")) {
      // reminder_time has to be one of a number of options
      if (!record.getString("reminder_day")
          .matches("daily|2|3|4|5|6"
                   +"|weekly|monday|tuesday|wednesday|thursday|friday|saturday|sunday"
                   +"|2-weekly")) {
        if (errors == null) errors = new Vector<String>();
        errors.add(localize("Not a valid day: {0}", record.getString("reminder_day")));
      }
    }
        
    if (!record.containsKey("reminder_time")
        || record.isNull("reminder_time")
        || record.getString("reminder_time").trim().length() == 0) {
      if (errors == null) errors = new Vector<String>();
      errors.add(localize("No corpus name was provided."));
    } else {
      // reminder_time has to be formatted 99:99
      
      if (!record.getString("reminder_time").matches("[012][0-9]:[0-5][0-9]")
          && !record.getString("reminder_time").matches("[012][0-9]:[0-5][0-9]:[0-5][0-9]")) {
        if (errors == null) errors = new Vector<String>();
        errors.add(localize("Not a valid time: {0}", record.getString("reminder_time")));
      }
    }
    
    if (!record.containsKey("from_day")
        || record.isNull("from_day")) {
      if (errors == null) errors = new Vector<String>();
      errors.add(localize("No start day was provided."));
    } else {
      int fromDay = record.getInt("from_day");
      if (!record.containsKey("to_day")
          || record.isNull("to_day")) {
        if (errors == null) errors = new Vector<String>();
        errors.add(localize("No end day was provided."));
      } else {
        int toDay = record.getInt("to_day");
        if (fromDay > 0 && toDay > 0 && fromDay > toDay) {
          if (errors == null) errors = new Vector<String>();
          errors.add(localize("Start day ({0}) is after end day ({0})", fromDay, toDay));
        }
      }
    }
    if (errors != null) throw new ValidationException(errors);
    return record;
  } // end of validateBeforeUpdate()
  
} // end of class Reminders
