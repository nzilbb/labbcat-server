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
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.function.Consumer;
import nzilbb.ag.PermissionException;
import nzilbb.ag.StoreException;
import nzilbb.labbcat.server.api.APIRequestHandler;
import nzilbb.labbcat.server.api.RequiredRole;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.util.IO;

/**
 * <tt>/api/admin/elicit/validation/<var>task_id</var>/{participant|transcript}/<var>attribute</var></tt>
 * : Administration of validation scripts for a participant/transcript attribute that is
 * elicitated during a task.
 *  <p> The following operations, specified by the HTTP method, are supported:
 *   <dl>
 *    <dt> GET </dt><dd> Get validation script for given attribute. 
 *     <ul>
 *      <li><em> Response Body </em> - the JavaScript validation script for the given
 *      attribute (which may be empty). </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The given attribute is elicited during the given task. </li>
 *         <li><em> 400 </em> : The URL was malformed. </li>
 *         <li><em> 404 </em> : The given attribute is not elicited during the given task. </li>
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *    <dt> PUT </dt><dd> Set the JavaScript validation script for the given attribute.
 *     <ul>
 *      <li><em> Request Body </em> - The validation JavaScript.
 *      </li>
 *      <li><em> Response Body </em> - empty.
 *      </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The given attribute is elicited during the given task. </li>
 *         <li><em> 400 </em> : The URL was malformed. </li>
 *         <li><em> 404 </em> : The given attribute is not elicited during the given task. </li>
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *   </dl>
 *  </li>
 * @author Robert Fromont robert@fromont.net.nz
 */
@RequiredRole("admin")
public class Validation extends APIRequestHandler {

  /**
   * Default constructor.
   */
  public Validation() {
  } // end of constructor

  MessageFormat fmtPath = new MessageFormat("/{0,number,integer}/{1}/{2}");
  
  /**
   * GET handler - list stimulus files.
   * @param pathInfo The URL path.
   * @param httpStatus Receives the response status code, in case of error.
   * @return The validation script, or an empty string.
   */
  public String get(String pathInfo, Consumer<Integer> httpStatus) {
    try {
      SqlGraphStoreAdministration store = getStore();
      if (!hasAccess(store.getConnection())) { 
        httpStatus.accept(SC_FORBIDDEN);
        return "";
      }
      
      Object[] pathParts = fmtPath.parse(pathInfo);
      Long task_id = (Long)pathParts[0];
      String attributeType = (String)pathParts[1];
      if ("participant".equals(attributeType) || "transcript".equals(attributeType)) {
        String attribute = (String)pathParts[2];
        try {
          Connection connection = store.getConnection();
          try (PreparedStatement sql = connection.prepareStatement(
                 "SELECT validation_javascript FROM elicitation_step"
                 +" WHERE task_id = ? AND attribute = CONCAT(?, '_', ?)")) {
            sql.setLong(1, task_id);
            sql.setString(2, attributeType);
            sql.setString(3, attribute);
            try (ResultSet rs = sql.executeQuery()) {
              if (rs.next()) {
                return rs.getString("validation_javascript");
              } else {
                context.servletLog(pathInfo + " not found");
                httpStatus.accept(SC_NOT_FOUND);
              }
            } // close rs
          } // close sql
        } finally {
          cacheStore(store);
        }
      } else { // neither participant nor transcript
        context.servletLog(pathInfo + " not participant/transcript");
        httpStatus.accept(SC_BAD_REQUEST);
      }
    } catch (PermissionException x) {
      httpStatus.accept(SC_FORBIDDEN);
    } catch (SQLException x) {
      context.servletLog(pathInfo + " " + x);
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
    } catch (ClassCastException x) { // should be impossible
      context.servletLog(pathInfo + " " + x);
      httpStatus.accept(SC_BAD_REQUEST);
    } catch (ParseException x) {
      context.servletLog(pathInfo + " " + x);
      httpStatus.accept(SC_BAD_REQUEST);
    }
    return "";
  }

  /**
   * PUT handler - define a validation script.
   * @param pathInfo The URL path.
   * @param requestBody Stream supplying the body of the request.
   * @param httpStatus Receives the response status code, in case of error.
   * @return JSON-encoded object representing the response
   */
  @SuppressWarnings("rawtypes")
  public void put(String pathInfo, InputStream requestBody, Consumer<Integer> httpStatus) {
    try {
      SqlGraphStoreAdministration store = getStore();
      if (!hasAccess(store.getConnection())) { 
        httpStatus.accept(SC_FORBIDDEN);
        return;
      }
      
      Object[] pathParts = fmtPath.parse(pathInfo);
      Long task_id = (Long)pathParts[0];
      String attributeType = (String)pathParts[1];
      if ("participant".equals(attributeType) || "transcript".equals(attributeType)) {
        String attribute = (String)pathParts[2];
        try {
          Connection connection = store.getConnection();
          try (PreparedStatement sql = connection.prepareStatement(
                 "SELECT validation_javascript FROM elicitation_step"
                 +" WHERE task_id = ? AND attribute = CONCAT(?, '_', ?)")) {
            sql.setLong(1, task_id);
            sql.setString(2, attributeType);
            sql.setString(3, attribute);
            try (ResultSet rs = sql.executeQuery()) {
              if (rs.next()) {
                try (PreparedStatement sqlUpdate = connection.prepareStatement(
                       "UPDATE elicitation_step SET validation_javascript = ?"
                       +" WHERE task_id = ? AND attribute = CONCAT(?, '_', ?)")) {
                  sqlUpdate.setString(1, IO.InputStreamToString(requestBody));
                  sqlUpdate.setLong(2, task_id);
                  sqlUpdate.setString(3, attributeType);
                  sqlUpdate.setString(4, attribute);
                  sqlUpdate.executeUpdate();
                } // close sqlUpdate
              } else {
                httpStatus.accept(SC_NOT_FOUND);
              }
            } // close rs
          } // close sql
        } finally {
          cacheStore(store);
        }
      } else { // neither participant nor transcript
        httpStatus.accept(SC_BAD_REQUEST);
      }
    } catch (PermissionException x) {
      httpStatus.accept(SC_FORBIDDEN);
    } catch (SQLException x) {
      context.servletLog(pathInfo + " " + x);
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
    } catch (IOException x) {
      context.servletLog(pathInfo + " " + x);
      httpStatus.accept(SC_BAD_REQUEST);
    } catch (ClassCastException x) { // should be impossible
      context.servletLog(pathInfo + " " + x);
      httpStatus.accept(SC_BAD_REQUEST);
    } catch (ParseException x) {
      context.servletLog(pathInfo + " " + x);
      httpStatus.accept(SC_BAD_REQUEST);
    }
  }
  
} // end of class Stimulus
