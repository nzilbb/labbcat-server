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
package nzilbb.labbcat.server.api.admin.elicit.task;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Optional;
import java.util.Vector;
import java.util.function.Consumer;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import nzilbb.ag.PermissionException;
import nzilbb.ag.StoreException;
import nzilbb.labbcat.server.api.APIRequestHandler;
import nzilbb.labbcat.server.api.RequestParameters;
import nzilbb.labbcat.server.api.RequiredRole;
import nzilbb.labbcat.server.api.admin.elicit.Tasks;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.util.IO;

/**
 * Servlet that allows a full task definition to be added/updated from a JSON file.
 * <h4>/api/admin/elicit/task/upload</h4>
 *  <p> The following operation, specified by the HTTP method, is supported:
 *   <dl>
 *    <dt> POST </dt><dd> Upload a task definition JSON file using a multipart POST request.
 *       (the first file parameter encountered is taken, regardless of the parameter name).
 *       The name of the file (without the .json extension) is taken as the name of the
 *       task being uploaded. If there is already a task with that name, it will be
 *       <u>replaced</u> with the task in the file. Otherwise, a new task is created.
 *     <ul>
 *      <li><em> Request Body </em> - A multi-part encoded request with one file parameter
 *          (the first file parameter encountered is taken, regardless of its name).
 *        The file must be .json.
 *      </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object with the following attributes:
 *      <dl>
 *        <dt> task_id </dt><dd> The database ID of the elicitation task. </dd>
 *        <dt> task_name </dt><dd> The name of elicitation task. </dd>
 *      </dl>
 *      </li>
 *      <li><em> Response Status </em>
*        <ul>
 *         <li><em> 200 </em> : The existing task was successfully replaced.</li>
 *         <li><em> 201 </em> : The new task was successfully created.</li>
 *         <li><em> 400 </em> : No file was found. </li> 
 *         <li><em> 415 </em> : The file was not of a supported type. </li> 
 *         <li><em> 422 </em> : The file had an invalid structure. </li> 
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *   </dl>
 *  </li>
 * @author Robert Fromont robert@fromont.net.nz
 */
@RequiredRole("admin")
public class Upload extends APIRequestHandler {

  /**
   * Default constructor.
   */
  public Upload() {
  } // end of constructor
  
  /**
   * POST handler - receive an uploaded stimulus file.
   * @param parameters Request parameter map.
   * @param httpStatus Receives the response status code, in case of error.
   * @param annotatorDir The directory in which annotator jars and their files are stored.
   * @return JSON-encoded object representing the response
   */
  @SuppressWarnings("rawtypes")
  public JsonObject post(RequestParameters parameters, Consumer<Integer> httpStatus) {
    
    try {
      SqlGraphStoreAdministration store = getStore();
      try {
        if (!hasAccess(store.getConnection())) {
          return null;
        }
        Optional anyFileValue = parameters.keySet().stream()
          .map(key->parameters.getFile(key))
          .filter(file->file != null)
          .findAny();
        if (!anyFileValue.isPresent()) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No file received.");
        } else { // file being uploaded
          // take the first file we find
          File formFile = (File)anyFileValue.get();
          context.servletLog("task.Upload: " + formFile.getName());
          try {
            if (!formFile.getName().endsWith(".json")) {
              httpStatus.accept(SC_UNSUPPORTED_MEDIA_TYPE); // 415
              return failureResult("Invalid file: {0}", formFile.getName()); // TODO i18n
            }
            String taskName = IO.WithoutExtension(formFile);
            if (taskName.trim().length() == 0) {
              httpStatus.accept(SC_BAD_REQUEST);
              return failureResult("No file received.");
            }
            
            try {
              JsonObject model = Json.createReader(new FileInputStream(formFile))
                .readObject().getJsonObject("model");
              if (model == null) {
                httpStatus.accept(SC_UNPROCESSABLE_CONTENT);
                return failureResult("Invalid JSON: {0}", "model"); // TODO i18n
              }
              String[] topLevelAttributes = {
                "task_name", "description", "corpus", "transcriptType", "preamble",
                "steps", "resources"
              };
              for (String attribute : topLevelAttributes) {
                if (!model.containsKey(attribute)) {
                  httpStatus.accept(SC_UNPROCESSABLE_CONTENT);
                  return failureResult("Invalid JSON: {0}", attribute); // TODO i18n
                }
              }
              
              boolean updatedExistingTask = false;
              int task_id = -1;
              Connection connection = store.getConnection();
              try (PreparedStatement sql = connection.prepareStatement(
                     "SELECT task_id FROM elicitation_task WHERE task_name = ?")) {
                sql.setString(1, taskName);
                try (ResultSet rs = sql.executeQuery()) {
                  if (rs.next()) {
                    task_id = rs.getInt(1);
                    updatedExistingTask = true;
                  } else {
                    try (PreparedStatement sqlTaskId = connection.prepareStatement(
                           "SELECT COALESCE(max(task_id) + 1, 1) FROM elicitation_task")) {
                      try (ResultSet rsTaskId = sqlTaskId.executeQuery()) {
                        rsTaskId.next();
                        task_id = rsTaskId.getInt(1);
                      } // close rsTaskId
                    } // close sqlTaskId
                  } // no existsing task
                } // close rs
              } // close sql

              String corpus_name = !model.containsKey("corpus")?null
                :model.getString("corpus");
              boolean validCorpus = false;
              try { // validate
                for (String c : store.getCorpusIds()) {
                  if (c.equals(corpus_name)) {
                    validCorpus = true;
                    break;
                  }
                }
              } catch(Exception exception) {}
              if (!validCorpus || corpus_name == null || corpus_name.length() == 0) {
                try (PreparedStatement sql = connection.prepareStatement(
                       "SELECT corpus_name FROM corpus ORDER BY corpus_name LIMIT 1")) {
                  try (ResultSet rs = sql.executeQuery()) {
                    rs.next();
                    corpus_name = rs.getString(1);
                  } // rs.close();
                } // sql.close();
              } // corpus not specified

              String transcript_type = !model.containsKey("transcriptType")?null
                :model.getString("transcriptType");
              boolean validType = false;
              try { // validate
                validType = store.getLayer("transcript_type").getValidLabels()
                  .keySet().contains(transcript_type);
              } catch(Exception exception) {}
              if (!validType || transcript_type == null || transcript_type.length() == 0) {
                try (PreparedStatement sql = connection.prepareStatement(
                       "SELECT transcript_type FROM transcript_type"
                       +" ORDER BY transcript_type LIMIT 1")) {
                  try (ResultSet rs = sql.executeQuery()) {
                    rs.next();
                    transcript_type = rs.getString(1);
                  } // rs.close();
                } // sql.close();
              } // transcript_type not specified
              
              String description = !model.containsKey("description")?null
                :model.getString("description");
              if (description == null) description = ""; 
             
              String preamble = !model.containsKey("preamble")?null
                :model.getString("preamble");
              if (preamble == null) preamble = "";
              
              String consent = !model.containsKey("consent")?null
                :model.getString("consent");
              if (consent == null) consent = "";
              
              String endUrl = !model.containsKey("endUrl")?null
                :model.getString("endUrl");
              if (endUrl == null) endUrl = "";
              
              if (updatedExistingTask) {
                try (PreparedStatement sql = connection.prepareStatement(
                       "UPDATE elicitation_task"
                       +" SET description = ?, preamble = ?, consent = ?, endUrl = ?"
                       +" WHERE task_id = ?")) {
                  sql.setString(1, description);
                  sql.setString(2, preamble);
                  sql.setString(3, consent);
                  sql.setString(4, endUrl);
                  sql.setInt(5, task_id);
                  sql.executeUpdate();
                  context.servletLog("updating " + task_id + " " + taskName);
                } // close sql
              } else {
                try (PreparedStatement sql = connection.prepareStatement(
                       "INSERT INTO elicitation_task"
                       +" (task_name, description, corpus_name, transcript_type,"
                       +" preamble, consent, endUrl, task_id)"
                       +" VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                  sql.setString(1, taskName);
                  sql.setString(2, description);
                  sql.setString(3, corpus_name);
                  sql.setString(4, transcript_type);
                  sql.setString(5, preamble);
                  sql.setString(6, consent);
                  sql.setString(7, endUrl);
                  sql.setInt(8, task_id);
                  sql.executeUpdate();
                  context.servletLog("inserting " + task_id + " " + taskName);
                } // close sql
              } // not updatedExistingTask

              // resource strings ...
              
              // first, default strings with help text etc.
              Tasks.CreateDefaultResources(connection, task_id);

              // second, update strings from json
              try (PreparedStatement sql = connection.prepareStatement(
                     "REPLACE INTO elicitation_resource_string"
                     +" (task_id, resource_id, message) VALUES (?,?,?)")) {
                sql.setInt(1, task_id);
                JsonObject resources = model.getJsonObject("resources");
                if (resources != null) {
                  for (String key : resources.keySet()) {
                    sql.setString(2, key);
                    sql.setString(3, resources.getString(key));
                    sql.executeUpdate();
                  } // next resource
                } // there is a resource object
              } // sql.close();
              
              // steps...

              // delete any existing steps
              try (PreparedStatement sql = connection.prepareStatement( 
                     "DELETE FROM elicitation_step WHERE task_id = ?")) {
                sql.setInt(1, task_id);
                sql.executeUpdate();
              } // sql.close();
              // ...and attributes/options
              try (PreparedStatement sql = connection.prepareStatement( 
                     "DELETE FROM elicitation_speaker_attribute WHERE task_id = ?")) {
                sql.setInt(1, task_id);
                sql.executeUpdate();
              } // sql.close();
              try (PreparedStatement sql = connection.prepareStatement( 
                     "DELETE FROM elicitation_speaker_attribute_option WHERE task_id = ?")){
                sql.setInt(1, task_id);
                sql.executeUpdate();
              } // sql.close();
              try (PreparedStatement sql = connection.prepareStatement( 
                     "DELETE FROM elicitation_transcript_attribute WHERE task_id = ?")) {
                sql.setInt(1, task_id);
                sql.executeUpdate();
              } // sql.close();
              try (PreparedStatement sql = connection.prepareStatement( 
                     "DELETE FROM elicitation_transcript_attribute_option WHERE task_id = ?")){
                sql.setInt(1, task_id);
                sql.executeUpdate();
              } // sql.close();
              
              try (PreparedStatement sql = connection.prepareStatement(
                     "INSERT INTO elicitation_step"
                     +" (task_id, step_id, display_order, title, prompt, transcript,"
                     +" record, max_seconds, suppress_next, next_delay_seconds, image,"
                     +" countdown_seconds, group_id, tags, parent_id, sample, step_count,"
                     +" attribute, condition_attribute, condition_value,"
                     +" validation_javascript, prompt_before_recording,"
                     +" prompt_during_recording, prompt_after_recording)"
                     +" VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
                   PreparedStatement sqlAttributeExists = connection.prepareStatement(
                     "SELECT attribute FROM attribute_definition WHERE attribute = ?");
                   PreparedStatement sqlInsertAttribute = connection.prepareStatement(
                     "INSERT INTO attribute_definition"
                     +" (class_id, attribute, label, type, style, category)"
                     +" VALUES (?,?,?,?,?,'General')");
                   PreparedStatement sqlLayerExists = connection.prepareStatement(
                     "SELECT layer_id FROM layer WHERE short_description = ?");
                   PreparedStatement sqlInsertLayer = connection.prepareStatement(
                     "INSERT INTO layer"
                     +" (layer_id, description, short_description, type, style, scope,"
                     +" parent_id, alignment, peers, peers_overlap, parent_includes,"
                     +" saturated)"
                     +" SELECT"
                     +" COALESCE(max(layer_id) + 1, 1), ?, ?, ?, ?, 'E',"
                     +" -50, 0, 0, 0, 1,"
                     +" 1 FROM layer")) {
                sql.setInt(1, task_id);
                int stepsAdded = addSteps(
                  model.getJsonArray("steps"), null, sql, sqlAttributeExists,
                  sqlInsertAttribute, sqlLayerExists, sqlInsertLayer);
                
                // return information
                JsonObjectBuilder jsonResult = Json.createObjectBuilder()
                  .add("task_id", task_id)
                  .add("task_name", taskName);
                
                httpStatus.accept(updatedExistingTask?SC_OK:SC_CREATED);
                return successResult(
                  jsonResult.build(),
                  updatedExistingTask
                  ?"Updated task {0} with {1} steps" // TODO i18n
                  :"Added task {0} with {1} steps", // TODO i18n
                  taskName, stepsAdded);
              } // close sqlAttributeExists, sqlInsertAttribute, sql etc.
            } catch(JsonException exception) {
              httpStatus.accept(SC_UNPROCESSABLE_CONTENT);
              return failureResult("Invalid JSON: {0}", exception.getMessage()); // TODO i18n
            } catch(SQLException exception) {
              System.err.println("task.Upload: " + exception);
              exception.printStackTrace(System.err);
              httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
              return failureResult("Database error: {0}", exception.getMessage()); // TODO i18n
            }
          } finally {
            formFile.delete();
          }
        } // uploading a file
      } finally {
        cacheStore(store);
      }
    } catch (PermissionException x) {
      httpStatus.accept(SC_FORBIDDEN);
      return failureResult(x);
    } catch (SQLException x) {
      System.err.println("task.Upload: " + x);
      x.printStackTrace(System.err);
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      return failureResult("Cannot connect to database: {0}", x.getMessage());
    } catch (IOException x) {
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      return failureResult("Communcation error: {0}", x.getMessage());
    }
  }
  
  /**
   * Adds the given array of steps.
   * @param steps
   * @param sql
   * @throws SQLException, JSONException
   */
  private int addSteps(
    JsonArray steps, Integer parent_id, PreparedStatement sql,
    PreparedStatement sqlAttributeExists, PreparedStatement sqlInsertAttribute,
    PreparedStatement sqlLayerExists, PreparedStatement sqlInsertLayer)
    throws SQLException, JsonException {
    int stepsAdded = 0;
    if (steps != null) {
      int numSteps = steps.size();
      for (int s = 0; s < numSteps; s++) {
        JsonObject step = steps.getJsonObject(s);
        
        // check attribute
        String attribute = step.getString("attribute");
        if (attribute.length() > 0) {
          String class_id = "";
          if (attribute.startsWith("transcript_")) {
            class_id = "transcript";
            attribute = attribute.substring("transcript_".length());
          } else if (attribute.startsWith("participant_")) {
            class_id = "speaker";
            attribute = attribute.substring("speaker_".length());
          }
          if (class_id.length() > 0) { // it's an attribute
            sqlAttributeExists.setString(1, attribute);
            try(ResultSet rs = sqlAttributeExists.executeQuery()) {
              if (!rs.next()) {
                sqlInsertAttribute.setString(1, class_id);
                sqlInsertAttribute.setString(2, attribute);
                sqlInsertAttribute.setString(3, attribute);
                sqlInsertAttribute.setString(4, step.getString("type"));
                sqlInsertAttribute.setString(5, step.getString("style"));
                sqlInsertAttribute.executeUpdate();
              }
            } // rs.close();
          } else { // it's an episode layer
            sqlLayerExists.setString(1, attribute);
            try (ResultSet rs = sqlLayerExists.executeQuery()) {
              if (!rs.next())
              {
                sqlInsertLayer.setString(1, attribute);
                sqlInsertLayer.setString(2, attribute);
                sqlInsertLayer.setString(3, step.getString("type"));
                sqlInsertLayer.setString(4, step.getString("style"));
                sqlInsertLayer.executeUpdate();
              }
            } // rs.close();
          }
        }
        
        sql.setInt(2, step.getInt("step_id"));
        sql.setInt(3, step.getInt("display_order"));
        sql.setString(4, step.getString("title"));
        sql.setString(5, step.getString("prompt"));
        sql.setString(6, step.getString("transcript"));
        sql.setInt(7, step.getInt("record"));
        sql.setInt(8, step.getInt("max_seconds"));
        sql.setInt(9, step.getInt("suppress_next"));
        sql.setInt(10, step.getInt("next_delay_seconds"));
        sql.setString(11, step.getString("image"));
        sql.setInt(12, step.getInt("countdown_seconds"));
        sql.setInt(13, step.getInt("group_id"));
        sql.setString(14, step.getString("tags"));
        if (parent_id == null) {
          sql.setNull(15, java.sql.Types.INTEGER);
        } else {
          sql.setInt(15, parent_id);
        }
        sql.setString(16, step.getString("sample"));
        sql.setInt(17, step.getInt("step_count"));
        sql.setString(18, step.getString("attribute"));
        sql.setString(19, step.getString("condition_attribute"));
        sql.setString(20, step.getString("condition_value"));
        sql.setString(21, step.getString("validation_javascript"));
        sql.setString(22, step.getString("prompt_before_recording"));
        sql.setString(23, step.getString("prompt_during_recording"));
        sql.setString(24, step.getString("prompt_after_recording"));
        sql.executeUpdate();
        stepsAdded++;
        if (step.containsKey("steps")) {
          // recursive call
          stepsAdded += addSteps(
            step.getJsonArray("steps"), step.getInt("step_id"), sql, sqlAttributeExists,
            sqlInsertAttribute, sqlLayerExists, sqlInsertLayer);
        }
      } // next step
    }
    return stepsAdded;
  } // end of addSteps()
  
} // end of class Upload
