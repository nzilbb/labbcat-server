//
// Copyright 2019-2025 New Zealand Institute of Language, Brain and Behaviour, 
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

package nzilbb.labbcat.server.api.admin;

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
 * <tt>/api/admin/password</tt> : sets user's password.
 *  <p> Allows Updating of a user's password.
 *   <p> Only the PUT HTTP method is supported:
 *   <dl>
 *    <dt> PUT </dt><dd>
 *     <ul>
 *      <li><em> Request Body </em> - a JSON-encoded object with the following structure:
 *       <dl>
 *        <dt> user </dt><dd> User ID to update. </dd>
 *        <dt> password </dt><dd> New password string. </dd>
 *        <dt> resetPassword </dt><dd> Boolean representing whether or not the user should be
 *              asked to change their password when they next log in. </dd> 
 *       </dl>
 *      </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with a null model. </li> 
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The user's password was successfully updated. </li>
 *         <li><em> 404 </em> : The user was not found. </li>
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *   </dl>
 *  </p>
 * @author Robert Fromont
 */
@RequiredRole("admin")
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
      if (!hasAccess(connection)) {
        httpStatus.accept(SC_FORBIDDEN);
        return null;
      }
      
      try {
        // read the incoming object
        JsonReader reader = Json.createReader( // ensure we read as UTF-8
          new InputStreamReader(requestBody, "UTF-8"));
        JsonObject json = reader.readObject();
        if (!json.containsKey("user")) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No user specified.");
        } else if (!json.containsKey("password")) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No password specified.");
        } else {
          String user = json.getString("user");
          String password = json.getString("password");
          if (password == null || password.length() == 0) {
            httpStatus.accept(SC_BAD_REQUEST);
            return failureResult("No password specified.");
          } else {
            boolean resetPassword = json.containsKey("resetPassword")
              && json.getBoolean("resetPassword");
            String passwordDigest = context.getInitParameter("passwordDigest");
            String passwordExpression = passwordDigest == null?"?":passwordDigest+"(?)";
            PreparedStatement sql = connection.prepareStatement(
              "UPDATE miner_user SET password = " + passwordExpression 
              + ", reset_password = ?, expiry = NULL WHERE user_id = ?"); 
            sql.setString(1, password);
            sql.setInt(2, resetPassword?1:0);
            sql.setString(3, user);
            int rowCount = sql.executeUpdate();
            if (rowCount != 1) { // it didn't work 
              httpStatus.accept(SC_NOT_FOUND);
              return failureResult("User not found: {0}", user);
            } else {
              return successResult(null, "Password changed.");
            }
          }
        }
      } finally {
        connection.close();
      }
    } catch(SQLException exception) {
      System.err.println("Password.handleRequest: Database operation failed: " + exception);
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      return failureResult(exception);
    } catch(Exception exception) {
      System.err.println("Password.handleRequest: Failed: " + exception);
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      return failureResult(exception);
    }
  }
   
} // end of class Password
