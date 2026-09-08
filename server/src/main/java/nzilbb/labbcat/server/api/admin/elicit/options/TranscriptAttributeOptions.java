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
package nzilbb.labbcat.server.api.admin.elicit.options;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Vector;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import nzilbb.labbcat.server.api.TableServletBase;
import nzilbb.labbcat.server.api.RequiredRole;

/**
 * <tt>/api/admin/elicit/options/transcript/<var>task_id</var>/<var>attribute</var>[/<var>value</var>]</tt> 
 * : Administration of options for a transcript attribute that is elicitated during a task.
 *  <p> Allows administration (Create/Read/Update/Delete) of participant-facing options
 *  for a transcript attribute that's elicited during a task. This allows restriction of
 *  available options, and also rewording or translation of the labels each option value
 *  shows. JSON-encoded objects have the following attributes:
 *   <dl>
 *    <dt> task_id </dt><dd> The database key for the record. </dd>
 *    <dt> attribute </dt><dd> The transcript attribute. </dd>
 *    <dt> value </dt><dd> The possible value presented to the participant, which
 *         must be one of the valid labels for the given attribute. </dd>
 *    <dt> description </dt><dd> The participant-facing text describing the option. </dd>
 *   </dl>
 *  <p> The following operations, specified by the HTTP method, are supported:
 *   <dl>
 *    <dt> POST </dt><dd> Create a new record.
 *     <ul>
 *      <li><em> Request Body </em> - a JSON-encoded object representing the new record. </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object representing the new record. </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was successfully created. </li>
 *         <li><em> 409 </em> : The record could not be added because it was already there. </li> 
 *        </ul>
 *      </li>
 *     </ul></dd> 
 * 
 *    <dt> GET </dt><dd> Read the records. 
 *     <ul>
 *      <li><em> Request Path </em> - /api/admin/elicit/options/transcript/<var>task_id</var>/<var>attribute</var> where 
 *          <var> task_id </var> identifies the task and <var> attribute </var> identifies
 *          the attribute for which options may be presented to participants.</li>
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
 *         <var> task_name </var> given in the request body.
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
 *      <li><em> Request Path </em> - /api/admin/elicit/options/transcript/<var>task_id</var>/<var>attribute</var>/<var>value</var> where 
 *          <var> task_id </var> identifies the task, <var> attribute </var> identifies
 *          the attribute, and <var> value </var> is the value to remove from the options
 *          presented to participants.</li>
 *      <li><em> Response Body </em> - the standard JSON envelope, including a message if
 *          the request succeeds or an error explaining the reason for failure. </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was successfully deleted. </li>
 *         <li><em> 400 </em> : One of <var> task_id </var>, <var> attribute </var>
 *          or <var> value </var> was missing. </li> 
 *         <li><em> 404 </em> : The record was not found. </li>
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *   </dl>
 *  </p>
 * @author Robert Fromont robert@fromont.net.nz
 */
@RequiredRole("admin")
public class TranscriptAttributeOptions extends TableServletBase {   
  
  public TranscriptAttributeOptions() {
    super("elicitation_transcript_attribute_option", // table
          new Vector<String>() {{ // primary keys
            add("task_id");
            add("attribute");
            add("value");
          }},
          new Vector<String>() {{ // columns
            add("description");
          }},
          "description"); // order
    
    create = true;
    read = true;
    update = true;
    delete = true;
  }
  
  /**
   * Validates a record before UPDATEing it.
   * <p> This can be overridden by subclasses. The default implementation returns null.
   * <p> This method may change the record to standardize or provide default values, by
   * adding attributes to <var>jsonRecord</var>.
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
    try {
      if (!record.containsKey("task_id") || record.isNull("task_id")) {
        errors = new Vector<String>() {{
            add(localize("No task ID was provided.")); }};
      } else {
        if (!record.containsKey("attribute") || record.isNull("attribute")) {
          errors = new Vector<String>() {{
              add(localize("No attribute was provided.")); }};
        } else {
          // ensure task_id is valid
          try (PreparedStatement sql = connection.prepareStatement(
                 "SELECT COUNT(*) FROM elicitation_transcript_attribute"
                 +" WHERE task_id = ? AND attribute = ?")) {
            sql.setInt(1, record.getInt("task_id"));
            sql.setString(2, record.getString("attribute"));
            try(ResultSet rs = sql.executeQuery()) {
              rs.next();
              if (rs.getInt(1) != 0) {
                errors = new Vector<String>() {{
                    add(localize("Attribute \"{0}\" is not elicited in task {1}",
                                 record.getString("attribute"),
                                 ""+record.getInt("task_id"))); }};
              }
              if (!record.containsKey("value") || record.isNull("value")) {
                errors = new Vector<String>() {{
                    add(localize("No value was provided.")); }};
              } else {
                // ensure task_id is valid
                try (PreparedStatement sqlValue = connection.prepareStatement(
                       "SELECT description FROM attribute_option"
                       +" WHERE class_id = 'transcript' AND attribute = ? AND value = ?")) {
                  sqlValue.setString(1, record.getString("attribute"));
                  sqlValue.setString(2, record.getString("value"));
                  try(ResultSet rsValue = sqlValue.executeQuery()) {
                    if (!rsValue.next()) {
                      errors = new Vector<String>() {{
                          add(localize("Value \"{0}\" is not valid for attribute \"{1}\"",
                                       record.getString("value"),
                                       record.getString("attribute"))); }};
                    }
                  } // close rsValue
                } // close sqlValue
              } // value is there
            } // close rs
          } // close sql
        } // attribute is there
      } // task_id is there
      
    } catch (SQLException x) {
      if (errors == null) errors = new Vector<String>();
      errors.add(x.toString());
      // not expecting this, so log it:
      context.servletLog("Tasks.validateBeforeUpdate: ERROR " + x);
    } catch (JsonException x) {
      if (errors == null) errors = new Vector<String>();
      errors.add(x.toString());
      // not expecting this, so log it:
      context.servletLog("Tasks.validateBeforeUpdate: ERROR " + x);
    }
    if (errors != null) throw new ValidationException(errors);
      return record;
  } // end of validateBeforeUpdate()
  
  
} // end of class TranscriptAttributeOptions
