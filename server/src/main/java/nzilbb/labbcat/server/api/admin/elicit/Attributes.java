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
import nzilbb.ag.Layer;
import nzilbb.ag.PermissionException;
import nzilbb.ag.StoreException;
import nzilbb.labbcat.server.api.APIRequestHandler;
import nzilbb.labbcat.server.api.RequestParameters;
import nzilbb.labbcat.server.api.RequiredRole;
import nzilbb.labbcat.server.api.admin.elicit.Tasks;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.util.IO;

/**
 * Lists <tt>transcript</tt> or <tt>participant</tt> attributes elicited during task.
 * <h4>/api/admin/elicit/attributes/{transcript|participant}/<var>task_id</var></h4>
 *  <p> The following operation, specified by the HTTP method, is supported:
 *   <dl>
 *    <dt> GET </dt><dd> List the attributes elicited by the task.
 *     <ul>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       array of objects with the same structure as returned by <tt>store/getLayer</tt>
 *       specifically:
 *      <dl>
 *        <dt> id </dt><dd> The attribute's layer ID. </dd>
 *        <dt> attribute </dt><dd> The attribute's name. </dd>
 *        <dt> description </dt><dd> The layer's user-facing description. </dd>
 *        <dt> type </dt><dd> The type of values the attribute can have. </dd>
 *        <dt> validLabels </dt><dd> An object definition possible labels and their
 *             user-facing descriptions. </dd>
 *      </dl>
 *      </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The attributes (if any) were listed.</li>
 *         <li><em> 400 </em> : The requested path was invalid.</li>
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *   </dl>
 *  </li>
 * @author Robert Fromont robert@fromont.net.nz
 */
@RequiredRole("admin")
public class Attributes extends APIRequestHandler {

  /**
   * Default constructor.
   */
  public Attributes() {
  } // end of constructor
  
  /**
   * GET handler - receive an uploaded stimulus file.
   * @param pathInfo The request path.
   * @param httpStatus Receives the response status code, in case of error.
   * @param annotatorDir The directory in which annotator jars and their files are stored.
   * @return JSON-encoded object representing the response
   */
  @SuppressWarnings("rawtypes")
  public JsonObject get(String pathInfo, Consumer<Integer> httpStatus) {

    context.servletLog(pathInfo);
    try {
      SqlGraphStoreAdministration store = getStore();
      try {
        if (!hasAccess(store.getConnection())) {
          return null;
        }
        // interpret path:
        // /api/admin/elicit/attributes/${scope}/${task_id}
        String[] path = pathInfo.split("/");
        if (path.length < 2) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("Invalid path: {0}", pathInfo);
        }
        String urlScope = path[path.length - 2];
        String dbScope = urlScope.equals("participant")?"speaker":urlScope;
        String taskId = path[path.length - 1];
        context.servletLog("scope " + urlScope + " taskId " + taskId);
        try {
          int task_id = Integer.parseInt(taskId);
          context.servletLog("task_id " + task_id);
        
          Vector<Layer> attributes = new Vector<Layer>();
          Connection connection = store.getConnection();
          try (PreparedStatement sql = connection.prepareStatement(
                 "SELECT attribute_definition.attribute"
                 +" FROM attribute_definition"
                 +" INNER JOIN elicitation_step ON elicitation_step.task_id = ?"
                 +" AND CONCAT(?, '_', attribute_definition.attribute)"
                 +" = elicitation_step.attribute"
                 +" WHERE class_id = ?"
                 +" ORDER BY attribute_definition.display_order")) {
            sql.setInt(1, task_id);
            sql.setString(2, urlScope);
            sql.setString(3, dbScope);
            try (ResultSet rs = sql.executeQuery()) {
              while (rs.next()) {
                attributes.add(store.getLayer(urlScope+"_"+rs.getString("attribute")));
              } // next attribute
            } // close rs
          } // close sql

          context.servletLog("attributes " + attributes);
          
          return successResult(attributes.toArray(new Layer[0]), null);
          
        } catch (NumberFormatException x) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("Invalid ID: {0}", taskId);
        }
      } finally {
        cacheStore(store);
      }
    } catch (PermissionException x) {
      httpStatus.accept(SC_FORBIDDEN);
      return failureResult(x);
    } catch (SQLException x) {
      System.err.println("task.Attributes: " + x);
      x.printStackTrace(System.err);
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      return failureResult("Cannot connect to database: {0}", x.getMessage());
    } catch (StoreException x) {
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      return failureResult("Communcation error: {0}", x.getMessage());
    }
  }
} // end of class Attributes
