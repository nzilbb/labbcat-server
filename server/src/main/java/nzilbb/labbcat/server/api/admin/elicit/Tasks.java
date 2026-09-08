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
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import nzilbb.labbcat.server.api.TableServletBase;
import nzilbb.labbcat.server.api.RequiredRole;

/**
 * <tt>/api/admin/elicit/tasks[/<var>task_id</var>]</tt> 
 * : Administration of elicitation task records.
 *  <p> Allows administration (Create/Read/Update/Delete) of task records via
 *  JSON-encoded objects with the following attributes:
 *   <dl>
 *    <dt> task_id </dt><dd> The database key for the record. </dd>
 *    <dt> task_name </dt><dd> The name of the task. </dd>
 *    <dt> description </dt><dd> Description of the task. </dd>
 *    <dt> corpus_name </dt><dd> The corpus for elicited transcripts/recordings. </dd>
 *    <dt> transcript_type </dt><dd> The transcript type for elicited transcripts/recordings. </dd>
 *    <dt> preamble </dt><dd> HTML-encoded text the participant sees when they are
 *         about to start the task. </dd>
 *    <dt> consent </dt><dd> Optional HTML-encoded consent form to be 'signed' by
 *         the participant. </dd>
 *    <dt> endUrl </dt><dd> Optional URL to send participants to after
 *         they finish the task (<tt>{participant}</tt>, if present in
 *         the URL, is replaced by the participant's ID). </dd>
 *    <dt> _cantDelete </dt><dd> This is not a database field, but rather is present in
 *         records returned from the server that can not currently be deleted; 
 *         a string representing the reason the record can't be deleted. </dd>
 *   </dl>
 *  <p> The following operations, specified by the HTTP method, are supported:
 *   <dl>
 *    <dt> POST </dt><dd> Create a new record.
 *     <ul>
 *      <li><em> Request Body </em> - a JSON-encoded object representing the new record
 *       (excluding <var>task_id</var>). </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object representing the new record (including <var>task_id</var>). </li>
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
 *         <var> task_id </var> given in the request body.
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
 *      <li><em> Request Path </em> - /api/admin/elicit/tasks/<var>task_id</var> where 
 *          <var> task_id </var> is the ID of the task to delete.</li>
 *      <li><em> Response Body </em> - the standard JSON envelope, including a message if
 *          the request succeeds or an error explaining the reason for failure. </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was successfully deleted. </li>
 *         <li><em> 400 </em> : No <var> task_id </var> was specified in the URL path,
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
public class Tasks extends TableServletBase {   
  
  public Tasks() {
    super("elicitation_task", // table
          new Vector<String>() {{ // primary keys
            add("task_id");
          }},
          new Vector<String>() {{ // columns
            add("task_name");
            add("description");
            add("corpus_name");
            add("transcript_type");
            add("preamble");
            add("consent");
            add("endUrl");
          }},
          "task_name"); // order
    
    create = true;
    read = true;
    update = true;
    delete = true;
    
    autoKey = "task_id";
    autoKeyQuery = "SELECT COALESCE(max(task_id) + 1, 1) FROM elicitation_task";      
    
    deleteChecks = new Vector<DeleteCheck>() {{
        add(new DeleteCheck(
              "SELECT COUNT(*) FROM elicitation_step WHERE task_id = ?",
              "task_id",
              "There are still steps defined for this task. Tasks cannot be deleted untill all steps are deleted."));
      }};
    beforeDelete = new Vector<DeleteCheck>() {{
        add(new DeleteCheck("DELETE FROM elicitation_resource_string WHERE task_id = ?",
                            "task_id", null));
        add(new DeleteCheck("DELETE FROM elicitation_step_group WHERE task_id = ?",
                            "task_id", null));
      }};
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
    try {
      if (!record.containsKey("task_name") || record.isNull("task_name")) {
        errors = new Vector<String>() {{
            add(localize("No task name was provided.")); }};
      } else {
        // trim name
        if (!record.getString("task_name").equals(record.getString("task_name").trim())) {
          record = createMutableCopy(record, "task_name")
            .add("task_name", record.getString("task_name").trim())
            .build();
        }
        if (record.getString("task_name").length() == 0) {
          errors = new Vector<String>() {{
              add(localize("Task name cannot be blank.")); }};
        }
        final String task_name = record.getString("task_name").trim();
        try (PreparedStatement sqlCount = connection.prepareStatement(
               "SELECT task_id FROM elicitation_task WHERE task_name = ?")){
          sqlCount.setString(1, task_name);
          try(ResultSet rsCount = sqlCount.executeQuery()) {
            if (rsCount.next()) {
              errors = new Vector<String>() {{
                  add(localize("A task with this name already exists: {0}", task_name)); }};
            }
          } // close rsCount
        } // close sqlCount
      }
      // validate corpus
      if (!record.containsKey("corpus_name") || record.isNull("corpus_name")) {
        errors = new Vector<String>() {{
            add(localize("No corpus name was provided.")); }};
      } else {
        final String corpus_name = record.getString("corpus_name");
        try (PreparedStatement sqlCount = connection.prepareStatement(
               "SELECT corpus_id FROM corpus WHERE corpus_name = ?")){
          sqlCount.setString(1, corpus_name);
          try(ResultSet rsCount = sqlCount.executeQuery()) {
            if (!rsCount.next()) {
              errors = new Vector<String>() {{
                  add(localize("Invalid corpus: {0}", corpus_name)); }};
            }
          } // close rsCount
        } // close sqlCount
      }
      // validate transcript type
      if (!record.containsKey("transcript_type") || record.isNull("transcript_type")) {
        errors = new Vector<String>() {{
            add(localize("No transcript type name was provided.")); }};
      } else {
        final String transcript_type = record.getString("transcript_type");
        try (PreparedStatement sqlCount = connection.prepareStatement(
               "SELECT type_id FROM transcript_type WHERE transcript_type = ?")){
          sqlCount.setString(1, transcript_type);
          try(ResultSet rsCount = sqlCount.executeQuery()) {
            if (!rsCount.next()) {
              errors = new Vector<String>() {{
                  add(localize("Invalid transcript type: {0}", transcript_type)); }};
            }
          } // close rsCount
        } // close sqlCount
      }
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
  
  /**
   * Ensures that transcript attributes associated with elicitation
   * tasks are set up, and creates internationalization records for
   * possible translation.
   * @param jsonIn The JSON representation of the object received with the request.
   * @param jsonOut The object to return in the response, after main columns have been
   * written, and before <var> _canDelete </var> and the object end has been written.
   * @param connection Database connection
   */
  protected void editNewRecord(
    JsonObject jsonIn, JsonObjectBuilder jsonOut, Connection connection) {
    
    try { // are the elicitation app transcript attributes set up?
      try (PreparedStatement sqlCount = connection.prepareStatement(
             "SELECT COUNT(*) FROM attribute_definition WHERE class_id = 'transcript'"
             +" AND attribute IN ('task','app','appVersion','appDevice','creation_date')")){
        try(ResultSet rsCount = sqlCount.executeQuery()) {
          rsCount.next();
          if (rsCount.getInt(1) == 0) { // need to create attributes
            try (PreparedStatement sql = connection.prepareStatement(
                   "INSERT INTO attribute_definition"
                   +" (class_id,attribute,category,type,style,label,description,"
                   +"display_order,searchable,access) VALUES (?,?,?,?,'',?,?,?,0,1)")) {
              sql.setString(1, "transcript"); // class_id
              sql.setString(2, "task"); // attribute
              sql.setString(3, "General"); // category
              sql.setString(4, "readonly"); // type
              sql.setString(5, "Task"); // label
              sql.setString(6, "Elicitation task"); // description
              sql.setInt(7, 1000); // display_order
              sql.executeUpdate();
              
              sql.setString(2, "creation_date"); // attribute
              sql.setString(5, "Creation Date"); // label
              sql.setString(6, "Date/time of elicitation"); // description
              sql.setInt(7, 1001); // display_order
              sql.executeUpdate();
              
              sql.setString(2, "app"); // attribute
              sql.setString(5, "App"); // label
              sql.setString(6, "App used for elicitation"); // description
              sql.setInt(7, 1002); // display_order
              sql.executeUpdate();
              
              sql.setString(2, "appVersion"); // attribute
              sql.setString(5, "App Version"); // label
              sql.setString(6, "Version of app used for elicitation"); // description
              sql.setInt(7, 1003); // display_order
              sql.executeUpdate();
              
              sql.setString(2, "appPlatform"); // attribute
              sql.setString(5, "Platform"); // label
              sql.setString(6, "Platform the app was run on during elicitation"); // description
              sql.setInt(7, 1004); // display_order
              sql.executeUpdate();
              
              sql.setString(2, "appDevice"); // attribute
              sql.setString(5, "Device"); // label
              sql.setString(6, "Device the app was run on during elicitation"); // description
              sql.setInt(7, 1005); // display_order
              sql.executeUpdate();
            } // close sql
          } // no elicitation app attributes exist
        } // close rsCount
      } // close sqlCount
    } catch (SQLException x) {
      context.servletLog("editNewRecord: " + x);
    }

    try { // create initial step group
      try (PreparedStatement sql = connection.prepareStatement(
             "INSERT INTO elicitation_step_group (task_id, group_id, sample)"
             +" VALUES (?, 0, 'ordered')")) {
        sql.setInt(1, jsonIn.getInt("task_id"));
        sql.executeUpdate();
      } // close sql
    } catch (SQLException x) {
      context.servletLog("editNewRecord: " + x);
    }
    
    try { // create resources for the new task
      try (PreparedStatement sql = connection.prepareStatement(
             "INSERT INTO elicitation_resource_string"
             +" (task_id, resource_id, message, help) VALUES (?,?,?,?)")) {
        sql.setInt(1, jsonIn.getInt("task_id"));

        sql.setString(2, "next"); // ID
        sql.setString(3, "Next"); // Default
        sql.setString(4, "Label for the 'Next' button"); // Help text
        sql.executeUpdate();
        
        sql.setString(2, "rerecord"); // ID
        sql.setString(3, "Re-record"); // Default
        sql.setString(4, "Label for the 're-record' button"); // Help text
        sql.executeUpdate();
        
        sql.setString(2, "uploadFinished"); // ID
        sql.setString(3, "All recordings have finished uploading."); // Default
        sql.setString(4, "Message shown when all recordingings have finished uploading."); // Help text
        sql.executeUpdate();
        
        sql.setString(2, "uploadingPleaseWait"); // ID
        sql.setString(3, "Your recordings are being uploaded..."); // Default
        sql.setString(4, "Message shown after recording is finished, and while the recordings are being uploaded."); // Help text
        sql.executeUpdate();
        
        sql.setString(2, "uploadingBeforeUnload"); // ID
        sql.setString(3, "Your recordings are still uploading. Please wait until the upload is finished."); // Default
        sql.setString(4, "Popup message shown if they try to close the window before uploading is finished."); // Help text
        sql.executeUpdate();
        
        sql.setString(2, "webAudioWarningTitle"); // ID
        sql.setString(3, "Check Your Microphone"); // Default
        sql.setString(4, "Title for the 'enable your microphone' page shown before recording starts"); // Help text
        sql.executeUpdate();
        
        sql.setString(2, "webAudioWarning"); // ID
        sql.setString(3, "<p>This task involves recording your speech.</p> <p>For this to work, your microphone must be enabled.</p> <p>If you use an external microphone, please plug it in now.</p> <p>&nbsp;</p> <p>When you're ready, click the arrow below. <br/>You will then be asked permission to share your microphone.</p>"); // Default
        sql.setString(4, "Message shown before recording begins, explaining that they must check their microphone before beginning."); // Help text
        sql.executeUpdate();
        
        sql.setString(2, "webAudioNotSupported"); // ID
        sql.setString(3, "<p>Sorry, your web browser doesn't support recording sound.</p> <p>Sound recording is known to work using recent versions of <a href=\"https://www.mozilla.org/en-US/firefox/new/\" target=\"download\">Mozilla Firefox</a> and <a href=\"https://encrypted.google.com/intl/en/chrome/browser/\" target=\"download\">Google Chrome</a>.</p>"); // Default
        sql.setString(4, "Message shown if they're using a web browser that does not support audio recording."); // Help text
        sql.executeUpdate();
        
        sql.setString(2, "beforeEnableMicrophone"); // ID
        sql.setString(3, "<p><br><br>In the next step<br/> you may be asked to enable access to your microphone,<br/> which you do by clicking the <q>Share Selected Device</q> or <q>Allow</q> button<br/> on the box that pops up above the page.</p>"); // Default
        sql.setString(4, "Message warning them they'll be asked to enable browser access to their microphone"); // Help text
        sql.executeUpdate();
        
        sql.setString(2, "pleaseEnableMicrophone"); // ID
        sql.setString(3, "<p><br><br>Please enable access to your microphone now, by clicking the <q>Share Selected Device</q> or <q>Allow</q> button.</p>"); // Default
        sql.setString(4, "Message prompting them to enable browser access to their microphone"); // Help text
        sql.executeUpdate();
        
        sql.setString(2, "getUserMediaFailed"); // ID
        sql.setString(3, "<p>Sorry, access to your microphone could not be obtained.</p>"); // Default
        sql.setString(4, "Message shown when microphone access could not be obtained e.g. because they disabled it."); // Help text
        sql.executeUpdate();
        
        sql.setString(2, "participantInfoPrompt"); // ID
        sql.setString(3, "<p>Please supply the following information.</p>"); // Default
        sql.setString(4, "Prompt shown when they must fill out the participant information form."); // Help text
        sql.executeUpdate();
        
        sql.setString(2, "pleaseSupplyAValueFor"); // ID
        sql.setString(3, "Please supply a value for"); // Default
        sql.setString(4, "Popup message displayed when they don't supply an attribute value, if the step title is set.  The message will be followed by the title of the step."); // Help text
        sql.executeUpdate();
        
        sql.setString(2, "pleaseSupplyAnAnswer"); // ID
        sql.setString(3, "Please supply an answer"); // Default
        sql.setString(4, "Popup message displayed when they don't supply an attribute value, if the step title is not set."); // Help text
        sql.executeUpdate();
        
        sql.setString(2, "pleaseSupplyANumberFor"); // ID
        sql.setString(3, "Please supply a number for"); // Default
        sql.setString(4, "Popup message displayed when they enter a non-numeric value for a numeric field on the participant form. The message will be followed by the name of the field."); // Help text
        sql.executeUpdate();
        
        sql.setString(2, "overallProgress"); // ID
        sql.setString(3, "Overall Progress"); // Default
        sql.setString(4, "Tool-tip text for progress bar during recording"); // Help text
        sql.executeUpdate();
        
        sql.setString(2, "countdownMessage"); // ID
        sql.setString(3, "<p>Please wait...</p>"); // Default
        sql.setString(4, "Message displayed during countdown before starting to record"); // Help text
        sql.executeUpdate();
        
        sql.setString(2, "recording"); // ID
        sql.setString(3, "Recording..."); // Default
        sql.setString(4, "Tool-tip text for recording indicator"); // Help text
        sql.executeUpdate();
        
        sql.setString(2, "yourParticipantIdIs"); // ID
        sql.setString(3, "<p>For your records, your Participant ID is:</p>"); // Default
        sql.setString(4, "Message displayed at the end of the task, preceding their Participant ID"); // Help text
        sql.executeUpdate();
        
        sql.setString(2, "pleaseEnterYourNameHere"); // ID
        sql.setString(3, "Please enter your name here"); // Default
        sql.setString(4, "Prompt text for the consent 'signature' box"); // Help text
        sql.executeUpdate();
        
        sql.setString(2, "pleaseEnterYourNameToIndicateYourConsent"); // ID
        sql.setString(3, "Please type your name in the box to indicate your consent"); // Default
        sql.setString(4, "Popup message when they don't fill in the 'signature' box"); // Help text
        sql.executeUpdate();
        
        sql.setString(2, "startAgain"); // ID
        sql.setString(3, "Start Again"); // Default
        sql.setString(4, "Text for 'start again' button that is shown at the end of the task"); // Help text
        
        sql.setString(2, "participantIdOrAccessCodeIncorrect"); // ID
        sql.setString(3, "Participant ID or Access Code incorrect, please try again."); // Default
        sql.setString(4, "Message displayed when login fails"); // Help text
        sql.executeUpdate();
        
        sql.setString(2, "timeFor"); // ID
        sql.setString(3, "Time for"); // Default
        sql.setString(4, "When task reminders are configured, the text of the reminder is this message followed by the task description"); // Help text
        sql.executeUpdate();
        
        sql.setString(2, "back"); // ID
        sql.setString(3, "Back"); // Default
        sql.setString(4, "Button text for back button"); // Help text
        sql.executeUpdate();
        
        sql.setString(2, "yes"); // ID
        sql.setString(3, "Yes"); // Default
        sql.setString(4, "Label for boolean attribute 'true' option"); // Help text
        sql.executeUpdate();
        
        sql.setString(2, "no"); // ID
        sql.setString(3, "No"); // Default
        sql.setString(4, "Label for boolean attribute 'false' option"); // Help text
        sql.executeUpdate();
        
        sql.setString(2, "history"); // ID
        sql.setString(3, "History:"); // Default
        sql.setString(4, "Heading for the task history list"); // Help text
        sql.executeUpdate();
        
      } // close sql
    } catch (SQLException x) {
      context.servletLog("editNewRecord: " + x);
    }
    
    editUpdatedRecord(jsonIn, jsonOut, connection);
  } // end of editNewRecord()
  
} // end of class Tasks
