//
// Copyright 2019-2020 New Zealand Institute of Language, Brain and Behaviour, 
// University of Canterbury
// Written by Robert Fromont - robert.fromont@canterbury.ac.nz
//
//    This file is part of LaBB-CAT.
//
//    LaBB-CAT is free software; you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation; either version 2 of the License, or
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

package nzilbb.labbcat.server.servlet;

import java.io.IOException;
import java.io.InputStreamReader;
import javax.servlet.*; // d:/jakarta-tomcat-5.0.28/common/lib/servlet-api.jar
import javax.servlet.http.*;
import javax.servlet.annotation.WebServlet;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
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
@WebServlet("/api/admin/password")
@RequiredRole("admin")
public class AdminPassword extends LabbcatServlet {
   
   // Attributes:
   
   /**
    * Constructor
    */
   public AdminPassword() {
   } // end of constructor

   // Servlet methods
   
   /**
    * The GET method for the servlet.
    * <p> This returns information about the current user - their ID and the roles they have.
    * @param request HTTP request
    * @param response HTTP response
    */
   @Override
   protected void doPut(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {      
      try {
         Connection connection = newConnection();
         if (!hasAccess(request, response, connection)) return;
         
         response.setContentType("application/json");
         response.setCharacterEncoding("UTF-8");
         try {
            // read the incoming object
           JsonReader reader = Json.createReader( // ensure we read as UTF-8
             new InputStreamReader(request.getInputStream(), "UTF-8"));
            JsonObject json = reader.readObject();
            if (!json.containsKey("user")) {
               response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
               writeResponse(response, failureResult(request, "No user specified."));
            } else if (!json.containsKey("password")) {
               response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
               writeResponse(response, failureResult(request, "No password specified."));
            } else {
               String user = json.getString("user");
               String password = json.getString("password");
               if (password == null || password.length() == 0) {
                  response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                  writeResponse(response, failureResult(request, "No password specified."));
               } else {
                  boolean resetPassword = json.containsKey("resetPassword")
                     && json.getBoolean("resetPassword");
                  String passwordDigest = getServletContext().getInitParameter("passwordDigest");
                  String passwordExpression = passwordDigest == null?"?":passwordDigest+"(?)";
                  PreparedStatement sql = connection.prepareStatement(
                     "UPDATE miner_user SET password = " + passwordExpression 
                     + ", reset_password = ?, expiry = NULL WHERE user_id = ?"); 
                  sql.setString(1, password);
                  sql.setInt(2, resetPassword?1:0);
                  sql.setString(3, user);
                  int rowCount = sql.executeUpdate();
                  if (rowCount != 1) { // it didn't work 
                     response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                     writeResponse(response, failureResult(request, "User not found: {0}", user));
                  } else {
                     writeResponse(response, successResult(request, null, "Password changed."));
                  }
               }
            }
         } finally {
            connection.close();
         }
      } catch(SQLException exception) {
         log("AdminPassword PUT: Database operation failed: " + exception);
         response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
         writeResponse(response, failureResult(exception));
      } catch(Exception exception) {
         log("AdminPassword PUT: Failed: " + exception);
         response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
         writeResponse(response, failureResult(exception));
      }
   }
   
   private static final long serialVersionUID = -1;
} // end of class AdminPassword
