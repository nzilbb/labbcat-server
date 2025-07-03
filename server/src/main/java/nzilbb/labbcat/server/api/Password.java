//
// Copyright 2025 New Zealand Institute of Language, Brain and Behaviour, 
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

package nzilbb.labbcat.server.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.zip.*;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.xml.parsers.*;
import javax.xml.xpath.*;
import org.w3c.dom.*;
import org.xml.sax.*;
import nzilbb.configure.ParameterSet;
import nzilbb.ag.GraphStoreAdministration;
import nzilbb.ag.Schema;
import nzilbb.ag.Graph;
import nzilbb.ag.serialize.GraphSerializer;
import nzilbb.ag.serialize.SerializationException;
import nzilbb.ag.serialize.util.ConfigurationHelper;
import nzilbb.ag.serialize.util.NamedStream;
import nzilbb.ag.serialize.util.Utility;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.util.IO;
import nzilbb.labbcat.server.api.APIRequestHandler;
import nzilbb.labbcat.server.api.RequiredRole;

/**
 * <tt>/api/password</tt> : change the current user's password.
 *  <p> Allows Updating of the currently-logged-in user's password.
 *   <p> Only the PUT HTTP method is supported:
 *   <dl>
 *    <dt> PUT </dt><dd>
 *     <ul>
 *      <li><em> Request Body </em> - a JSON-encoded object with the following structure:
 *       <dl>
 *        <dt> currentPassword </dt><dd> Current password string. </dd>
 *        <dt> newPassword </dt><dd> New password string. </dd>
 *       </dl>
 *      </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with a null model. </li> 
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The current user's password was successfully updated. </li>
 *         <li><em> 400 </em> : The <var>newPassword</var> was unacceptable
 *                              (e.g. blank or missing). </li> 
 *         <li><em> 403 </em> : The <var>currentPassword</var> was incorrect. </li>
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *   </dl>
 *  </p>
 * @author Robert Fromont
 */
public class Password extends APIRequestHandler {
  
  /**
   * Constructor
   */
  public Password() {
  } // end of constructor
  
  /**
   * The handler for the request
   * <p> This set the user password as specified
   * @param requestBody Stream supplying the body of the request.
   * @param httpStatus Receives the response status code, in case or error.
   * @return A JSON object as the request response.
   */
  public JsonObject put(InputStream requestBody, Consumer<Integer> httpStatus) {
    try {
      Connection connection = newConnection();
      String user = context.getUser();
      if (user == null || user.length() == 0) { // must be logged in
        httpStatus.accept(SC_FORBIDDEN);
        return failureResult("No user specified.");
      }
      
      try {
        // read the incoming object
        JsonReader reader = Json.createReader( // ensure we read as UTF-8
          new InputStreamReader(requestBody, "UTF-8"));
        JsonObject json = reader.readObject();
        if (!json.containsKey("currentPassword")) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No current password specified.");
        } else if (!json.containsKey("newPassword")) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No password specified.");
        } else {
          String currentPassword = json.getString("currentPassword");
          if (currentPassword == null || currentPassword.length() == 0) {
            httpStatus.accept(SC_BAD_REQUEST);
            return failureResult("No current password specified.");
          } else {
            String newPassword = json.getString("newPassword");
            if (newPassword == null || newPassword.length() == 0) {
              httpStatus.accept(SC_BAD_REQUEST);
              return failureResult("No password specified.");
            } else {
              String passwordDigest = context.getInitParameter("passwordDigest");
              String passwordExpression = passwordDigest == null?"?":passwordDigest+"(?)";
              PreparedStatement sql = connection.prepareStatement(
                "UPDATE miner_user"
                +" SET password = " + passwordExpression + ","
                +" reset_password = 0, expiry = NULL"
                +" WHERE user_id = ? AND password = " + passwordExpression); 
              sql.setString(1, newPassword);
              sql.setString(2, user);
              sql.setString(3, currentPassword);
              int rowCount = sql.executeUpdate();
              if (rowCount != 1) { // it didn't work 
                httpStatus.accept(SC_FORBIDDEN);
                return failureResult("Current password incorrect.");
              } else {
                return successResult(null, "Password changed.");
              }
            }
          }
        }
      } finally {
        connection.close();
      }
    } catch(SQLException exception) {
      context.servletLog("Password.handleRequest: Database operation failed: " + exception);
      context.servletLog(exception);
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      // for security, don't return the specific error to the client
      return failureResult("Unexpected error.");
    } catch(Exception exception) {
      context.servletLog("Password.handleRequest: Failed: " + exception);
      context.servletLog(exception);
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      // for security, don't return the specific error to the client
      return failureResult("Unexpected error.");
    }
  }
   
} // end of class Password
