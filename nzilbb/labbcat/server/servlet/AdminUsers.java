//
// Copyright 2020 New Zealand Institute of Language, Brain and Behaviour, 
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;
import javax.json.JsonException;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.stream.JsonGenerator;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;

/**
 * <tt>/api/admin/users[/<var>user</var>]</tt> 
 * : Administration of <em> user </em> records.
 *  <p> Allows administration (Create/Read/Update/Delete) of users records (including roles) via
 *  JSON-encoded objects with the following attributes:
 *   <dl>
 *    <dt> user </dt><dd> ID of the user. </dd>
 *    <dt> email </dt><dd> Email address of the user. </dd>
 *    <dt> resetPassword </dt><dd> Whether the user is flagged to reset their password the
 *                                 next time they log in. </dd>
 *    <dt> roles </dt><dd> An array of strings, which are the roles or groups the user
 *                         belongs to. </dd> 
 *    <dt> _cantDelete </dt><dd> This is not a database field, but rather is present in
 *         records returned from the server that can not currently be deleted; 
 *         a string representing the reason the record can't be deleted. </dd>
 *   </dl>
 *  <p> The following operations, specified by the HTTP method, are supported:
 *   <dl>
 *    <dt> POST </dt><dd> Create a new record.
 *     <ul>
 *      <li><em> Request Body </em> - a JSON-encoded object representing the new record
 *       (excluding <var>mediaTrack_id</var>). </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object representing the new record (including <var>mediaTrack_id</var>). </li>
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
 *    <dt> PUT </dt><dd> Update an existing record, specified by the <var> mediaTrack </var>
 *      given in the request body.
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
 *      <li><em> Request Path </em> - /api/admin/users/<var>user</var> where 
 *          <var> user </var> is the user ID of the user to delete.</li>
 *      <li><em> Response Body </em> - the standard JSON envelope, including a message if
 *          the request succeeds or an error explaining the reason for failure. </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was successfully deleted. </li>
 *         <li><em> 400 </em> : No <var> user </var> was specified in the URL path,
 *             or the record exists but could not be deleted. </li> 
 *         <li><em> 404 </em> : The record was not found. </li>
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *   </dl>
 *  </p>
 * @author Robert Fromont robert@fromont.net.nz
 */
@WebServlet(urlPatterns = "/api/admin/users/*", loadOnStartup = 20)
@RequiredRole("admin")
public class AdminUsers extends TableServletBase {   
   
   public AdminUsers() {
      super("miner_user", // table
            new Vector<String>() {{ // primary keys
               add("user_id");
            }},
            new Vector<String>() {{ // columns
               add("email");
               add("reset_password");
            }},
            "user_id"); // order
      
      setAliases(new HashMap<String,String>() {{
         put("user_id", "user");
         put("reset_password", "resetPassword");
      }});
      
      deleteChecks = new Vector<DeleteCheck>() {{
            add(new DeleteCheck(
                   "SELECT CASE WHEN COUNT(*) > 0 THEN 0 ELSE 1 END FROM role"
                   +" WHERE role_id = 'admin' AND user_id <> ?",
                   "user_id",
                   "Last admin user cannot be deleted.")); // TODO prevent last admin user from being removed from the admin role
         }};
      beforeDelete = new Vector<DeleteCheck>() {{
            add(new DeleteCheck("DELETE FROM role WHERE user_id = ?", "user_id", null));
         }};
      
      create = true;
      read = true;
      update = true;
      delete = true;
      
      emptyKeyAllowed = false;
   }

   /**
    * Validates a record before UPDATEing it.
    * @param request The request.
    * @param record The incoming record to validate, to which attributes can be added.
    * @param connection A connection to th database.
    * @return A JSON representation of the valid record, which may or may not be the same
    * object as <var>record</var>.
    * @throws ValidationException If the record is invalid.
    */
   @Override
   protected JsonObject validateBeforeUpdate(
      HttpServletRequest request, JsonObject record,
      Connection connection) throws ValidationException {
      
      Vector<String> errors = null;
      try {
         // user ID
         if (!record.containsKey("user") || record.isNull("user")) {
            errors = new Vector<String>() {{
                  add(localize(request, "No user ID was provided.")); }};
         } else {
            // trim name
            if (!record.getString("user").equals(record.getString("user").trim())) {
               record = createMutableCopy(record, "user")
                  .add("user", record.getString("user").trim())
                  .build();
            }
            if (record.getString("user").length() == 0) {
               errors = new Vector<String>() {{
                     add(localize(request, "User ID cannot be blank.")); }};
            }
         }
         // email
         if (record.containsKey("email") && !record.isNull("email")
             && record.getString("email").trim().length() > 0) {
            // there is an email address
            if (record.getString("email").indexOf("@") <= 0) {
               final String email = record.getString("email");
               errors = new Vector<String>() {{
                     add(localize(
                            request, "Invalid email address: {0}", email)); }};
            }
         }
         // resetPassword default value
         if (!record.containsKey("resetPassword") || record.isNull("resetPassword")) {
            record = createMutableCopy(record, "resetPassword")
               .add("resetPassword", 0)
               .build();
         }

      } catch (JsonException x) {
         if (errors == null) errors = new Vector<String>();
         errors.add(x.toString());
         // not expecting this, so log it:
         System.err.println("AdminCorpora.validateBeforeUpdate: ERROR " + x);
      }
      if (errors != null) throw new ValidationException(errors);
      return record;
   } // end of validateBeforeCreate()
   
   /**
    * Add roles to the returned object.
    * @param rs The record.
    * @param jsonOut The object to return in the response, after main columns have been
    * written, and before <var> _canDelete </var> and the object end has been written.
    * @param connection Database connection
    * @throws SQLException If there's an error operating with the database.
    */
   protected void editOutputRecord(ResultSet rs, JsonGenerator jsonOut, Connection connection)
      throws SQLException {
      try {
         PreparedStatement sql = connection.prepareStatement(
            "SELECT role_id FROM role WHERE user_id = ? ORDER BY role_id");
         sql.setString(1, rs.getString("user_id"));
         ResultSet rsRole = sql.executeQuery();
         jsonOut.writeStartArray("roles");
         try {
            while (rsRole.next()) {
               jsonOut.write(rsRole.getString("role_id"));
            } // next role
         } finally {
            rsRole.close();
            sql.close();
            jsonOut.writeEnd(); // end roles array
         }
      } catch (SQLException x) {
         log("editOutputRecord: " + x);
      }
   } // end of editOutputRecord()

   /**
    * Ensures that the roles match the incoming object, and that a random initial password
    * is set.
    * @param jsonIn The JSON representation of the object received with the request.
    * @param jsonOut The object to return in the response, after main columns have been
    * written, and before <var> _canDelete </var> and the object end has been written.
    * @param connection Database connection
    */
   protected void editNewRecord(
      JsonObject jsonIn, JsonObjectBuilder jsonOut, Connection connection) {
      try {
         // ensure password is not blank, nor predictable
         PreparedStatement sql = connection.prepareStatement(
            "UPDATE miner_user SET password = MD5(RAND()) WHERE user_id = ?");
         sql.setString(1, jsonIn.getString("user"));
         sql.executeUpdate();
      } catch (SQLException x) {
         log("editNewRecord: " + x);
      }
      editUpdatedRecord(jsonIn, jsonOut, connection);
   } // end of editNewRecord()
   
   /**
    * Ensures that the roles match the incoming object.
    * @param jsonIn The JSON representation of the object received with the request.
    * @param jsonOut The object to return in the response, after main columns have been
    * written, and before <var> _canDelete </var> and the object end has been written.
    * @param connection Database connection
    */
   protected void editUpdatedRecord(
      JsonObject jsonIn, JsonObjectBuilder jsonOut, Connection connection) {
      // only if the incoming object specifies roles...
      if (jsonIn.containsKey("roles")) {
         try {
            
            // get old roles
            PreparedStatement sql = connection.prepareStatement(
               "SELECT role_id FROM role WHERE user_id = ? ORDER BY role_id");
            sql.setString(1, jsonIn.getString("user"));
            ResultSet rs = sql.executeQuery();
            HashSet<String> oldRoles = new HashSet<String>();
            while (rs.next()) {
               oldRoles.add(rs.getString("role_id"));
            } // next role
            rs.close();
            sql.close();
            
            // get new roles
            HashSet<String> newRoles = new HashSet<String>();
            JsonArray newRolesArray = jsonIn.getJsonArray("roles");
            for (int r = 0; r < newRolesArray.size(); r++) {
               newRoles.add(newRolesArray.getString(r));
            } // next element

            // add new ones
            HashSet<String> rolesToAdd = new HashSet<String>(newRoles);
            rolesToAdd.removeAll(oldRoles);
            if (rolesToAdd.size() > 0) {
               sql = connection.prepareStatement(
                  "INSERT INTO role (user_id, role_id) VALUES (?,?)");
               sql.setString(1, jsonIn.getString("user"));
               for (String role : rolesToAdd) {
                  sql.setString(2, role);
                  sql.executeUpdate();
               } // next role
               sql.close();
            }
            
            // remove missing ones
            HashSet<String> rolesToRemove = new HashSet<String>(oldRoles);
            rolesToRemove.removeAll(newRoles);
            if (rolesToRemove.size() > 0) {
               sql = connection.prepareStatement(
                  "DELETE FROM role WHERE user_id = ? AND role_id = ?");
               sql.setString(1, jsonIn.getString("user"));
               for (String role : rolesToRemove) {
                  sql.setString(2, role);
                  sql.executeUpdate();
               } // next role
               sql.close();
            }

         } catch (SQLException x) {
            log("editOutputRecord: " + x);
         } catch (ClassCastException x) {
            log("editOutputRecord: " + x);
         }
      } // "roles" is specified
   } // end of editUpdatedRecord()

   private static final long serialVersionUID = 1;
} // end of class AdminUsers
