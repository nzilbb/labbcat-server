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

import java.io.InputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Consumer;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
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
 * <h4>/api/admin/elicit/steps/<var>task_id</var></h4>
 *  <p> The following operations, specified by the HTTP method, is supported:
 *   <dl>
 *    <dt> PUT </dt><dd> Update the given task's steps, completely replacing any previous
 *                       steps.
 *     <ul>
 *      <li><em> Request Body </em> - A JSON-encoded array of nested step objects.
 *      </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with a null model.
 *      </li>
 *      <li><em> Response Status </em>
*        <ul>
 *         <li><em> 200 </em> : The existing task steps were successfully replaced.</li>
 *         <li><em> 400 </em> : No task was specified. </li> 
 *         <li><em> 404 </em> : The specified task was not found. </li> 
 *         <li><em> 422 </em> : The request body had an invalid structure. </li> 
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *   </dl>
 *  </li>
 * @author Robert Fromont robert@fromont.net.nz
 */
@RequiredRole("admin")
public class Steps extends APIRequestHandler {
  
  /**
   * Default constructor.
   */
  public Steps() {
  } // end of constructor
  
  /**
   * POST handler - receive an uploaded stimulus file.
   * @param pathInfo The URL path.
   * @param requestBody Stream supplying the body of the request.
   * @param httpStatus Receives the response status code, in case of error.
   * @return JSON-encoded object representing the response
   */
  @SuppressWarnings("rawtypes")
  public JsonObject put(String pathInfo, InputStream requestBody, Consumer<Integer> httpStatus) {
    
    try {
      SqlGraphStoreAdministration store = getStore();
      Connection connection = store.getConnection();
      try {
        if (!hasAccess(connection)) {
          return null;
        }
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.indexOf('/') < 0) {
          // no path component
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No ID specified.");
        }
        String taskId = pathInfo.substring(pathInfo.lastIndexOf('/') + 1);
        if (taskId.length() == 0) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No ID specified.");
        }
        int task_id = -1;
        try {
          task_id = Integer.parseInt(taskId);
        } catch(NumberFormatException exception) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("Invalid ID: {0}", taskId);
        }
        String taskName = null;
        try (PreparedStatement sql = connection.prepareStatement( 
               "SELECT task_name FROM elicitation_task WHERE task_id = ?")) {
          sql.setInt(1, task_id);
          try (ResultSet rs = sql.executeQuery()) {
            if (!rs.next()) {
              httpStatus.accept(SC_NOT_FOUND);
              return failureResult("Invalid ID: {0}", taskId);
            }
            taskName = rs.getString(1);
          } // rs.close()
        } // sql.close()
        
        try {
          JsonArray taskSteps = Json.createReader(requestBody).readArray();
          if (taskSteps == null) {
            httpStatus.accept(SC_UNPROCESSABLE_CONTENT);
            return failureResult("Invalid JSON: {0}", "body");
          }
          
          // steps...
          JsonArrayBuilder stepsBuilder = Json.createArrayBuilder();
          SanitizeSteps(taskSteps, null, stepsBuilder);
          taskSteps = stepsBuilder.build();
          
          // delete any existing steps
          try (PreparedStatement sql = connection.prepareStatement( 
                 "DELETE FROM elicitation_step WHERE task_id = ?")) {
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
                 +" VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            sql.setInt(1, task_id);
            int stepsAdded = addSteps(taskSteps, null, sql);
            
            httpStatus.accept(SC_OK);
            return successResult(
              null, "Updated task {0} with {1} steps", taskName, stepsAdded);
          } // close sqlAttributeExists, sqlInsertAttribute, sql etc.
        } catch(JsonException exception) {
          httpStatus.accept(SC_UNPROCESSABLE_CONTENT);
          return failureResult("Invalid JSON: {0}", exception.getMessage());
        } catch(SQLException exception) {
          System.err.println("task.Upload: " + exception);
          exception.printStackTrace(System.err);
          httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
          return failureResult("Database error: {0}", exception.getMessage());
        }
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
    }
  }
  
  /**
   * Recursively validates and sanitizes steps, includeing ensuring that all
   * step objects are well formed, have unique IDs, etc.
   * @param steps An array of possibly nested steps.
   * @param parent_id The step_id of the parent, if any.
   * @param sql A prepared INSERT statement for adding a step row.
   * @return A copy of the steps array, with checked, well-formed step objects
   * @throws JSONException If the JSON array/objects are malformed.
   */
  public static int SanitizeSteps(
    JsonArray steps, Integer parent_id, JsonArrayBuilder stepsBuilder)
    throws JsonException {
    int stepsAdded = 0;
    if (steps != null) {
      int idOffset = Optional.ofNullable(parent_id).orElse(0);
      int numSteps = steps.size();
      for (int s = 0; s < numSteps; s++) {
        JsonObject step = steps.getJsonObject(s);
        int step_id = idOffset+s; // all new step_ids
        JsonObjectBuilder newStep = Json.createObjectBuilder()
          .add("step_id", step_id) 
          .add("display_order", s) // order the steps are listed in the array
          .add("title", step.getString("title",""))
          .add("prompt", step.getString("prompt", ""))
          .add("transcript", step.getString("transcript", ""));
        int record = 0;
        if (step.containsKey("record")) {
          if (step.get("record").getValueType() == JsonValue.ValueType.NUMBER) {
            record = step.getInt("record");
          } else if (step.get("record").getValueType() == JsonValue.ValueType.STRING) {
            record = Integer.parseInt(step.getString("record"));
          }
        }
        newStep.add("record", record);
        int max_seconds = 0;
        if (step.containsKey("max_seconds")) {
          if (step.get("max_seconds").getValueType() == JsonValue.ValueType.NUMBER) {
            max_seconds = step.getInt("max_seconds");
          } else if (step.get("max_seconds").getValueType() == JsonValue.ValueType.STRING) {
            max_seconds = Integer.parseInt(step.getString("max_seconds"));
          }
        }
        newStep.add("max_seconds", max_seconds);
        int suppress_next = 0;
        if (step.containsKey("suppress_next")) {
          if (step.get("suppress_next").getValueType() == JsonValue.ValueType.NUMBER) {
            suppress_next = step.getInt("suppress_next");
          } else if (step.get("suppress_next").getValueType() == JsonValue.ValueType.STRING) {
            suppress_next = Integer.parseInt(step.getString("suppress_next"));
          }
        }
        newStep.add("suppress_next", suppress_next);
        int next_delay_seconds = 0;
        if (step.containsKey("next_delay_seconds")) {
          if (step.get("next_delay_seconds").getValueType() == JsonValue.ValueType.NUMBER) {
            next_delay_seconds = step.getInt("next_delay_seconds");
          } else if (step.get("next_delay_seconds").getValueType() == JsonValue.ValueType.STRING) {
            next_delay_seconds = Integer.parseInt(step.getString("next_delay_seconds"));
          }
        }
        newStep.add("next_delay_seconds", next_delay_seconds);
        newStep.add("image", step.getString("image", ""));
        int countdown_seconds = 0;
        if (step.containsKey("countdown_seconds")) {
          if (step.get("countdown_seconds").getValueType() == JsonValue.ValueType.NUMBER) {
            countdown_seconds = step.getInt("countdown_seconds");
          } else if (step.get("countdown_seconds").getValueType() == JsonValue.ValueType.STRING) {
            countdown_seconds = Integer.parseInt(step.getString("countdown_seconds"));
          }
        }
        newStep.add("countdown_seconds", countdown_seconds);
        int group_id = 0;
        if (step.containsKey("group_id")) {
          if (step.get("group_id").getValueType() == JsonValue.ValueType.NUMBER) {
            group_id = step.getInt("group_id");
          } else if (step.get("group_id").getValueType() == JsonValue.ValueType.STRING) {
            group_id = Integer.parseInt(step.getString("group_id"));
          }
        }
        newStep.add("group_id", parent_id!=null?parent_id:0);
        newStep.add("tags", step.getString("tags", ""));
        if (parent_id == null) {
          newStep.add("parent_id", JsonValue.NULL);
        } else {
          newStep.add("parent_id", parent_id);
        }
        newStep.add("sample", step.getString("sample", "ordered"));
        int step_count = 0;
        if (step.containsKey("step_count")) {
          if (step.get("step_count").getValueType() == JsonValue.ValueType.NUMBER) {
            step_count = step.getInt("step_count");
          } else if (step.get("step_count").getValueType() == JsonValue.ValueType.STRING) {
            step_count = Integer.parseInt(step.getString("step_count"));
          }
        }
        newStep.add("step_count", step_count);
        newStep.add("attribute", step.getString("attribute", ""));
        newStep.add("condition_attribute", step.getString("condition_attribute", ""));
        newStep.add("condition_value", step.getString("condition_value", ""));
        newStep.add("validation_javascript", step.getString("validation_javascript", ""));
        newStep.add(
          "prompt_before_recording", step.getString("prompt_before_recording",""));
        newStep.add(
          "prompt_during_recording", step.getString("prompt_during_recording",""));
        newStep.add(
          "prompt_after_recording", step.getString("prompt_after_recording",""));
        stepsAdded++;
        if (step.containsKey("steps")) {
          if (step.get("steps").getValueType() == JsonValue.ValueType.ARRAY) {
            JsonArray substeps = step.getJsonArray("steps");
            if (substeps.size() > 0) {
              JsonArrayBuilder substepsBuilder = Json.createArrayBuilder();
              // recursive call
              stepsAdded += SanitizeSteps(substeps, step_id, substepsBuilder);
              newStep.add("steps", substepsBuilder.build());
            } // there are substeps
          } // "steps" is an array
        } // there's a "steps" attribute
      } // next step
    } // there are steps
    return stepsAdded;
  }
  /**
   * Adds the given array of steps.
   * @param steps An array of possibly nested steps.
   * @param parent_id The step_id of the parent, if any.
   * @param sql A prepared INSERT statement for adding a step row.
   * @throws SQLException If an SQL error occures while inserting a row.
   * @throws JSONException If the JSON array/objects are malformed.
   */
  private int addSteps(JsonArray steps, Integer parent_id, PreparedStatement sql)
    throws SQLException, JsonException {
    int stepsAdded = 0;
    if (steps != null) {
      int numSteps = steps.size();
      for (int s = 0; s < numSteps; s++) {
        JsonObject step = steps.getJsonObject(s);
        sql.setInt(2, step.getInt("step_id"));
        sql.setInt(3, step.getInt("display_order", s));
        sql.setString(4, step.getString("title",""));
        sql.setString(5, step.getString("prompt",""));
        sql.setString(6, step.getString("transcript",""));
        sql.setInt(7, step.getInt("record", 0));
        sql.setInt(8, step.getInt("max_seconds", 30));
        sql.setInt(9, step.getInt("suppress_next", 0));
        sql.setInt(10, step.getInt("next_delay_seconds", 0));
        sql.setString(11, step.getString("image", ""));
        sql.setInt(12, step.getInt("countdown_seconds", 0));
        sql.setInt(13, step.getInt("group_id", parent_id!=null?parent_id:0));
        sql.setString(14, step.getString("tags", ""));
        if (parent_id == null) {
          sql.setNull(15, java.sql.Types.INTEGER);
        } else {
          sql.setInt(15, parent_id);
        }
        sql.setString(16, step.getString("sample", "ordered"));
        sql.setInt(17, step.getInt("step_count", 0));
        sql.setString(18, step.getString("attribute", ""));
        sql.setString(19, step.getString("condition_attribute", ""));
        sql.setString(20, step.getString("condition_value", ""));
        sql.setString(21, step.getString("validation_javascript", ""));
        sql.setString(22, step.getString("prompt_before_recording", ""));
        sql.setString(23, step.getString("prompt_during_recording", ""));
        sql.setString(24, step.getString("prompt_after_recording", ""));
        sql.executeUpdate();
        stepsAdded++;
        if (step.containsKey("steps")) {
          // recursive call
          stepsAdded += addSteps(
            step.getJsonArray("steps"), step.getInt("step_id"), sql);
        }
      } // next step
    }
    return stepsAdded;
  } // end of addSteps()
  
} // end of class Upload
