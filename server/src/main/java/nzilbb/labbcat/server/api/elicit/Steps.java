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

package nzilbb.labbcat.server.api.elicit;

import java.sql.*;
import java.util.function.Consumer;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import nzilbb.labbcat.server.api.APIRequestHandler;
import nzilbb.labbcat.server.api.RequestParameters;

/**
 * <tt>/api/elicit/steps</tt> : Elicitation task definition.
 *  <p> Provides the definition of the given elicitation task.
 *   <p> Only the GET HTTP method is supported:
 *   <dl>
 *    <dt> GET </dt><dd>
 *     <ul>
 *      <li><em> URL parameters </em>:
 *       <dl>
 *        <dt> task </dt> <dd> The name of the task to provide the definition of. </dd>
 *       </dl></li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object defining the structure of the elicitation task.  </li>
 *      <li><em> Response Status </em> <ul>
 *         <li><em> 200 </em> : Success.</li>
 *         <li><em> 400 </em> : Task not specified.</li>
 *         <li><em> 404 </em> : Task not found.</li>
 *       </ul></li>
 *     </ul></dd> 
 *   </dl>
 *  </p>
 * @author Robert Fromont
 */
public class Steps extends APIRequestHandler { // TODO automated tests
  
  /**
   * Constructor
   */
  public Steps() {
  } // end of constructor
  
  /**
   * The GET method for the servlet.
   * @param remoteUser The user's ID if any.
   * @param requestParameters Request parameter map.
   * @param fileName Receives the filename for specification in the response headers.
   * @param httpStatus Receives the response status code, in case of error.
   * @return JSON-encoded object representing the response
   */
  public JsonObject get(String remoteUser, RequestParameters requestParameters,
                        Consumer<String> fileName, Consumer<Integer> httpStatus) {
    String task = requestParameters.getString("task");
    if (task == null || task.length() == 0) {
      httpStatus.accept(SC_BAD_REQUEST);
      return failureResult("No ID specified.");
    }
    try {
      try (Connection db = newConnection()) {
        try (PreparedStatement sqlTask = db.prepareStatement(
               "SELECT task_id, task_name, description,"
               +" corpus_name AS corpus,"
               +" transcript_type AS transcriptType,"
               +" preamble, consent, endUrl"
               +" FROM elicitation_task"
               +" WHERE task_name = ?")) {
          sqlTask.setString(1, task);
          try (ResultSet rsTask = sqlTask.executeQuery()) {
            if (!rsTask.next()) {
              httpStatus.accept(SC_NOT_FOUND);
              return failureResult("Invalid ID: {0}", task);
            } else {
              int taskId = rsTask.getInt("task_id");
              JsonObjectBuilder model = Json.createObjectBuilder()
                .add("task_id", rsTask.getInt("task_id"))
                .add("task_name", rsTask.getString("task_name"))
                .add("description", rsTask.getString("description"))
                .add("corpus", rsTask.getString("corpus"))
                .add("transcriptType", rsTask.getString("transcriptType"))
                .add("preamble", rsTask.getString("preamble"))
                .add("consent", rsTask.getString("consent"))
                .add("endUrl", rsTask.getString("endUrl"));
                
              try (PreparedStatement sqlTopSteps = db.prepareStatement(
                     "SELECT * FROM elicitation_step"
                     +" WHERE task_id = ? AND parent_id IS NULL ORDER BY display_order");
                   PreparedStatement sqlChildSteps = db.prepareStatement(
                     "SELECT * FROM elicitation_step"
                     +" WHERE task_id = ? AND parent_id = ? ORDER BY display_order");
                   PreparedStatement sqlField = db.prepareStatement(
                     "SELECT * FROM ("
                     +"SELECT class_id, attribute, type, style"
                     +" FROM attribute_definition"
                     +" WHERE CONCAT(CASE class_id WHEN 'speaker'"
                     +" THEN 'participant' ELSE class_id END, '_', attribute) = ?"
                     +" UNION ALL "
                     +"SELECT '' AS class_id, short_description AS attribute, type, style"
                     +" FROM layer"
                     +" WHERE scope = 'E' AND short_description = ?) a"
                     );
                   PreparedStatement sqlFieldOptions = db.prepareStatement(
                     "SELECT * FROM ("
                     +"SELECT a.value, ta.description"
                     +" FROM attribute_option a"
                     +" INNER JOIN elicitation_speaker_attribute_option ta"
                     +" ON a.attribute = ta.attribute AND a.value = ta.value"
                     +" WHERE ta.task_id = ? AND class_id = ? AND a.attribute = ?"
                     +" UNION ALL "
                     +"SELECT a.value, ta.description"
                     +" FROM attribute_option a"
                     +" INNER JOIN elicitation_transcript_attribute_option ta"
                     +" ON a.attribute = ta.attribute AND a.value = ta.value"
                     +" WHERE ta.task_id = ? AND class_id = ? AND a.attribute = ?) a"
                     +" ORDER BY description")) {
                sqlFieldOptions.setInt(1, taskId);
                sqlFieldOptions.setInt(4, taskId);
                
                // phase groups
                JsonArrayBuilder steps = Json.createArrayBuilder();
                sqlTopSteps.setInt(1, taskId);
                try (ResultSet rsSteps = sqlTopSteps.executeQuery()) {
                  while (rsSteps.next()) {
                    JsonObjectBuilder step = ObjectFromResultSet(rsSteps);
                    setAttributeDefinition(
                      step, rsSteps.getString("attribute"), taskId, sqlField,
                      sqlFieldOptions);
                    steps.add(step);
                    JsonArrayBuilder children = getChildSteps(
                      taskId, rsSteps.getInt("step_id"), sqlChildSteps, sqlField,
                      sqlFieldOptions);
                    if (children != null) step.add("steps", children);
                  } // next phase group
                } // rs.close
                model.add("steps", steps);
              } // close sqlTopSteps, sqlChildSteps, sqlField, sqlFieldOptions
              
              // resources for localization
              try (PreparedStatement sql = db.prepareStatement(
                     "SELECT resource_id, message FROM elicitation_resource_string"
                     +" WHERE task_id = ? ORDER BY resource_id")) {
                sql.setInt(1, taskId);
                try (ResultSet rs = sql.executeQuery()) {
                  JsonObjectBuilder resources = Json.createObjectBuilder();
                  while (rs.next()) {
                    resources.add(rs.getString("resource_id"), rs.getString("message"));
                  } // next resource message
                  model.add("resources", resources);
                } // rs.close()
              } // sql.close()

              // reminder schedule
              try (PreparedStatement sql = db.prepareStatement(
                     "SELECT label, reminder_day, reminder_time, from_day, to_day,"
                     + "participant_pattern"
                     +" FROM elicitation_reminder"
                     +" WHERE task_id = ? ORDER BY reminder_id")) {
                sql.setInt(1, taskId);
                try (ResultSet rs = sql.executeQuery()) {
                  JsonArrayBuilder reminders = ArrayFromResultSet(rs);
                  if (reminders != null) model.add("reminders", reminders);
                } // rs.close()
              } //sql.close()

              // load public participant attributes
              try (PreparedStatement sql = db.prepareStatement(
                     "SELECT a.attribute, a.type, a.style, ta.label, ta.description,"
                     +" ta.condition_attribute, ta.condition_value, ta.validation_javascript"
                     +" FROM attribute_definition a"
                     +" INNER JOIN elicitation_speaker_attribute ta"
                     +" ON a.attribute = ta.attribute"
                     +" WHERE class_id = 'speaker' AND ta.task_id = ?"
                     +" ORDER BY ta.display_order");
                   PreparedStatement sqlOptions = db.prepareStatement(
                     "SELECT a.value, ta.description"
                     +" FROM attribute_option a"
                     +" INNER JOIN elicitation_speaker_attribute_option ta"
                     +" ON a.attribute = ta.attribute AND a.value = ta.value"
                     +" WHERE class_id = 'speaker' AND ta.task_id = ? AND a.attribute = ?"
                     +" ORDER BY ta.description")) {
                sql.setInt(1, taskId);
                sqlOptions.setInt(1, taskId);
                try (ResultSet rs = sql.executeQuery()) {
                  JsonArrayBuilder participantFields = Json.createArrayBuilder();
                  while (rs.next()) {
                    JsonObjectBuilder attribute = ObjectFromResultSet(rs);
                    participantFields.add(attribute);
                    // get select options if needed
                    if ("select".equals(rs.getString("type"))) { // it's a select field
                      // load the options
                      sqlOptions.setString(2, rs.getString("attribute"));
                      try (ResultSet rsOptions = sqlOptions.executeQuery()) {
                        attribute.add("options", ArrayFromResultSet(rsOptions));
                      } // rsOptions.close()
                    } // select field
                  } // next field
                  model.add("participantFields", participantFields);
                } // rs.close()
              } // close sql, sqlOptions

              // load public transcript attributes
              try (PreparedStatement sql = db.prepareStatement(
                     "SELECT a.attribute, a.type, a.style, ta.label, ta.description,"
                     +" ta.condition_attribute, ta.condition_value, ta.validation_javascript"
                     +" FROM attribute_definition a"
                     +" INNER JOIN elicitation_transcript_attribute ta"
                     +" ON a.attribute = ta.attribute"
                     +" WHERE class_id = 'transcript' AND ta.task_id = ?"
                     +" ORDER BY ta.display_order");
                   PreparedStatement sqlOptions = db.prepareStatement(
                     "SELECT a.value, ta.description"
                     +" FROM attribute_option a"
                     +" INNER JOIN elicitation_transcript_attribute_option ta"
                     +" ON a.attribute = ta.attribute AND a.value = ta.value"
                     +" WHERE class_id = 'transcript' AND ta.task_id = ? AND a.attribute = ?"
                     +" ORDER BY ta.description")) {
                sql.setInt(1, taskId);
                sqlOptions.setInt(1, taskId);
                try (ResultSet rs = sql.executeQuery()) {
                  JsonArrayBuilder transcriptFields = Json.createArrayBuilder();
                  while (rs.next()) {
                    JsonObjectBuilder attribute = ObjectFromResultSet(rs);
                    transcriptFields.add(attribute);
                    // get select options if needed
                    if ("select".equals(rs.getString("type"))) { // it's a select field
                      // load the options
                      sqlOptions.setString(2, rs.getString("attribute"));
                      try (ResultSet rsOptions = sqlOptions.executeQuery()) {
                        attribute.add("options", ArrayFromResultSet(rsOptions));
                      } // rsOptions.close()
                    } // select field
                  } // next field
                  model.add("transcriptFields", transcriptFields);
                } // rs.close()
                
                model.add("version", context.getVersion());
                if (remoteUser != null) model.add("username", remoteUser);
                model.add(
                  "uploadUrl", context.getBaseUrl().toString() + "/api/elicit/upload");
                model.add("imageBaseUrl", context.getBaseUrl().toString() + "/elicit/");
                model.add(
                  "consentUrl", context.getBaseUrl().toString() + "/elicit/consent");
                model.add(
                  "newParticipantUrl", context.getBaseUrl() + "/elicit/participant");
                model.add("verifyUrl", context.getBaseUrl() + "/elicit/verify");
                
                // set filename for cases where this is a task definition export
                fileName.accept(task + ".json");
                return successResult(model.build(), null);
                
              } // close sql, sqlOptions
            } // task found
          } // close rsTask
        } // close sqlTask
        
      } // db.close
    } catch(SQLException exception) {
      System.err.println("api.elicit.Steps SQL ERROR: " + exception);
      exception.printStackTrace(System.err);
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      return failureResult("Unexpected error.");
    } catch(Exception exception) {
      System.err.println("api.elicit.Steps ERROR: " + exception);
      exception.printStackTrace(System.err);
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      return failureResult(exception.toString());
    }
  }
  
  /**
   * Constructs a JSON object with all the fields for a given ResultSet Row
   * @param row
   * @return A JsonObjectBuilder with a value set for each of the ResultSet fields
   * @throws Exception
   */
  public static JsonObjectBuilder ObjectFromResultSet(ResultSet row) throws Exception {
    ResultSetMetaData meta = row.getMetaData();
    int iCols = meta.getColumnCount();
    JsonObjectBuilder object = Json.createObjectBuilder();
    for (int iCol = 1; iCol <= iCols; iCol++) {
      Object o = row.getObject(iCol);
      if (o != null) {
        if (o instanceof Integer) {
          object.add(meta.getColumnLabel(iCol), (Integer)o);
        } else if (o instanceof Long) {
          object.add(meta.getColumnLabel(iCol), (Long)o);
        } else if (o instanceof Double) {
          object.add(meta.getColumnLabel(iCol), (Double)o);
        } else if (o instanceof Boolean) {
          object.add(meta.getColumnLabel(iCol), (Boolean)o);
        } else {
          object.add(meta.getColumnLabel(iCol), o.toString());
        }
      }
    } // next field
    return object;
  } // end of ObjectFromResultSet()
  
  /**
   * Constructs a JSON array of object, one for each row returned by the given ResultSet,
   * each object having a value set for each field in the ResultSet.
   * @param rs
   * @return A Vector of Hashtable objects
   * @throws Exception
   */
  public static JsonArrayBuilder ArrayFromResultSet(ResultSet rs) throws Exception {
    JsonArrayBuilder array = Json.createArrayBuilder();
    while (rs.next()) {
      array.add(ObjectFromResultSet(rs));
    } // next row
    return array;
  } // end of ArrayFromResultSet()
  
  /**
   * Gets the child steps of the given step.
   */
  public JsonArrayBuilder getChildSteps(
    int taskId, int parentId, PreparedStatement sqlChildSteps, PreparedStatement sqlField,
    PreparedStatement sqlFieldOptions) throws Exception {
    JsonArrayBuilder steps = Json.createArrayBuilder();
    sqlChildSteps.setInt(1, taskId);
    sqlChildSteps.setInt(2, parentId);
    boolean thereWereSteps = false;
    try (ResultSet rs = sqlChildSteps.executeQuery()) {
      while (rs.next()) {
        JsonObjectBuilder step = ObjectFromResultSet(rs);
        setAttributeDefinition(
          step, rs.getString("attribute"), taskId, sqlField, sqlFieldOptions);
        JsonArrayBuilder children = getChildSteps(
          taskId, rs.getInt("step_id"), sqlChildSteps, sqlField, sqlFieldOptions);
        if (children != null) step.add("steps", children);
        steps.add(step);
      } // next step
    } // close rs
    return thereWereSteps?steps:null;
  } // end of getChildSteps()
  
  /**
   * Sets the definition for the participant/transcript attribute for the given step,
   * if any.
   */
  public void setAttributeDefinition(
    JsonObjectBuilder step, String attribute, int taskId, PreparedStatement sqlField,
    PreparedStatement sqlFieldOptions) throws Exception {
    if (attribute != null && attribute.length() > 0) {
      sqlField.setString(1, attribute);
      sqlField.setString(2, attribute);
      try (ResultSet rs = sqlField.executeQuery()) {
        if (rs.next()) {
          String type = rs.getString("type");
          if (type.equals("select")) {
	    sqlFieldOptions.setString(2, rs.getString("class_id"));
	    sqlFieldOptions.setString(3, rs.getString("attribute"));
	    sqlFieldOptions.setString(5, rs.getString("class_id"));
	    sqlFieldOptions.setString(6, rs.getString("attribute"));
	    try (ResultSet rsOptions = sqlFieldOptions.executeQuery()) {
              step.add("options", ArrayFromResultSet(rsOptions));
	    } // rsOptions.close()
          } // a select attribute
          step.add("type", type);
          step.add("style", rs.getString("style"));
        } // next attribute
      } // rs.close()
    } // attribute set
  } // end of getChildSteps()
} // end of class Steps
