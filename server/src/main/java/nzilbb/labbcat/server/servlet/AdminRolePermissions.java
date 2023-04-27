//
// Copyright 2020 New Zealand Institute of Language, Brain and Behaviour, 
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
package nzilbb.labbcat.server.servlet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.List;
import java.util.Vector;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;

/**
 * <tt>/api/admin/roles/permissions[/<var>role_id</var>[/<var>entity</var>]]</tt> 
 * : Administration <em> role permissions </em> records.
 *  <p> Allows administration (Create/Read/Update/Delete) of user role permission records via
 *  JSON-encoded objects with the following attributes:
 *   <dl>
 *    <dt> role_id </dt><dd> The ID of the role this permission applies to. </dd>
 *    <dt> entity </dt><dd> The media entity this permission applies to - a string made
 *         up of "t" (transcript), "a" (audio), "v" (video), or "i" (image). </dd>
 *    <dt> attribute_name </dt><dd> Name of a transcript attribute for which the value determines
 *         access. This is either a valid transcript attribute name (i.e. excluding the
 *         "transcript_" prefix in the layer ID), or "corpus". </dd>
 *    <dt> value_pattern </dt><dd> Regular expression for matching against the 
 *         <var> attribute_name </var> value. If the regular expression matches the value,
 *         access is allowed. </dd>
 *    <dt> _cantDelete </dt><dd> This is not a database field, but rather is present in
 *         records returned from the server that can not currently be deleted; 
 *         a string representing the reason the record can't be deleted. </dd>
 *   </dl>
 *  <p> The following operations, specified by the HTTP method, are supported:
 *   <dl>
 *    <dt> POST </dt><dd> Create a new record.
 *     <ul>
 *      <li><em> Request Body </em> - a JSON-encoded object representing the new record
 *       (excluding <var>role_id</var>). </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object representing the new record (including <var>role_id</var>). </li>
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
 *      <li><em> Request Path </em> - /api/admin/roles/permissions/<var>role_id</var> where 
 *          <var> role_id </var> is the ID of the role the permissions belong to.</li>
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
 *    <dt> PUT </dt><dd> Update an existing record, specified by the <var> role </var> given in the
 *    request body.
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
 *      <li><em> Request Path </em> - /api/admin/roles/permissions/<var>role_id</var>/<var>entity</var>  
 *          where <var> role_id </var> is the ID of the role the permissions belong to and
 *          <var> entity </var> is the entity to delete the permission for..</li>
 *      <li><em> Response Body </em> - the standard JSON envelope, including a message if
 *          the request succeeds or an error explaining the reason for failure. </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was successfully deleted. </li>
 *         <li><em> 400 </em> : No <var> role </var> was specified in the URL path,
 *             or the record exists but could not be deleted. </li> 
 *         <li><em> 404 </em> : The record was not found. </li>
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *   </dl>
 * @author Robert Fromont robert@fromont.net.nz
 */
@WebServlet(urlPatterns = "/api/admin/roles/permissions/*", loadOnStartup = 20)
@RequiredRole("admin")
public class AdminRolePermissions extends TableServletBase {   

   public AdminRolePermissions() {
      super("role_permission", // table
            new Vector<String>() {{ // primary/URL keys
               add("role_id");
               add("entity");
            }},
            new Vector<String>() {{ // columns
               add("attribute_name");
               add("value_pattern");
            }},
            "role_id, entity"); // order
      
      create = true;
      read = true;
      update = true;
      delete = true;      
   }

   /**
    * Validates a record before UPDATEing it.
    * @param request The request.
    * @param record The incoming record to validate, to which attributes can be added.
    * @param connection A connection to the database.
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
         if (!record.containsKey("role_id") || record.isNull("role_id")
             || record.getString("role_id").length() == 0) {
            errors = new Vector<String>() {{
                  add(localize(request, "No role ID was provided.")); }};
         } 
         if (!record.containsKey("entity") || record.isNull("entity")) {
            if (errors == null) errors = new Vector<String>();
            errors.add(localize(request, "No entity was provided."));
         } else {
            // check it includes at least one of: t(ranscript), i(mage), a(udio), v(ideo)
            if (!record.getString("entity").matches("^[tiav]+$")) {
               if (errors == null) errors = new Vector<String>();
               errors.add(localize(request, "Invalid entity specifier: {0}", record.get("entity")));
            }
         }
         if (!record.containsKey("attribute_name") || record.isNull("attribute_name")
             || record.getString("attribute_name").length() == 0) {
            if (errors == null) errors = new Vector<String>();
            errors.add(localize(request, "No transcript attribute was specified."));
         }
         if (!record.containsKey("value_pattern") || record.isNull("value_pattern")
             || record.getString("value_pattern").length() == 0) {
            if (errors == null) errors = new Vector<String>();
            errors.add(localize(request, "No attribute value pattern was specified."));
         } else {
            try { Pattern.compile(record.getString("value_pattern")); }
            catch(PatternSyntaxException exception) {
               if (errors == null) errors = new Vector<String>();
               errors.add(
                  localize(request, "Invalid value pattern: {0} - {1}",
                           record.get("value_pattern"), exception.getMessage()));
            }
         }
      } catch (JsonException x) {
         if (errors == null) errors = new Vector<String>();
         errors.add(x.toString());
         // not expecting this, so log it:
         System.err.println("AdminRolePermissions.validateBeforeUpdate: ERROR " + x);
      }
      if (errors != null) throw new ValidationException(errors);
      return record;
   } // end of validateBeforeUpdate()

   /**
    * Validates a record before INSERTing it.
    * @param request The request.
    * @param record The incoming record to validate, to which attributes can be added.
    * @param connection A connection to th database.
    * @return A JSON representation of the valid record, which may or may not be the same
    * object as <var>record</var>.
    * @throws ValidationException If the record is invalid.
    */
   @Override
   protected JsonObject validateBeforeCreate(
      HttpServletRequest request, JsonObject record,
      Connection connection) throws ValidationException {

      record = validateBeforeUpdate(request, record, connection);
      Vector<String> errors = null;
      try {
         // check it's a valid role
         PreparedStatement sql = connection.prepareStatement(
            "SELECT role_id FROM role_definition WHERE role_id = ?");
         sql.setString(1, record.getString("role_id"));
         ResultSet rs = sql.executeQuery();
         try {
            if (!rs.next()) {
               if (errors == null) errors = new Vector<String>();
               errors.add(
                  localize(request, "Invalid role ID: {0}", record.getString("role_id")));
            }
         }
         finally {
            rs.close();
            sql.close();
         }
         
         // check it's a valid transcript attribute, or "corpus"
         if (!record.getString("attribute_name").equals("corpus")) {
            sql = connection.prepareStatement(
               "SELECT attribute FROM attribute_definition"
               +" WHERE class_id = 'transcript' AND attribute = ?");
            sql.setString(1, record.getString("attribute_name"));
            rs = sql.executeQuery();
            try {
               if (!rs.next()) {
                  if (errors == null) errors = new Vector<String>();
                  errors.add(
                     localize(request, "Invalid transcript attribute: {0}",
                              record.getString("attribute_name")));
               }
            }
            finally {
               rs.close();
               sql.close();
            }
         } // attribute_name != "corpus"
      } catch (SQLException x) {
         if (errors == null) errors = new Vector<String>();
         errors.add(x.toString());
         // not expecting this, so log it:
         System.err.println("AdminRolePermissions.validateBeforeInsert: ERROR " + x);
      }
      if (errors != null) throw new ValidationException(errors);
      return record;
   } // end of validateBeforeCreate()
   
   private static final long serialVersionUID = 1;
} // end of class AdminRolePermissions
