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
import java.util.List;
import java.util.Vector;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;

/**
 * <tt>/api/admin/roles[/<var>role_id</var>]</tt> : Administration of <em> role </em> records.
 *  <p> Allows administration (Create/Read/Update/Delete) of user role records via
 *  JSON-encoded objects with the following attributes:
 *   <dl>
 *    <dt> role_id </dt><dd> The name of the role. </dd>
 *    <dt> description </dt><dd> The description of the role. </dd>
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
 *         <li><em> 200 </em> : The record was sucessfully created. </li>
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
 *    <dt> PUT </dt><dd> Update an existing record, specified by the <var> role </var> given in the
 *    request body.
 *     <ul>
 *      <li><em> Request Body </em> - a JSON-encoded object representing the record. </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object representing the record. </li> 
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was sucessfully updated. </li>
 *         <li><em> 404 </em> : The record was not found. </li>
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *    
 *    <dt> DELETE </dt><dd> Delete an existing record.
 *     <ul>
 *      <li><em> Request Path </em> - /api/admin/roles/<var>role_id</var> where 
 *          <var> role_id </var> is the ID of the record to delete.</li>
 *      <li><em> Response Body </em> - the standard JSON envelope, including a message if
 *          the request succeeds or an error explaining the reason for failure. </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was sucessfully deleted. </li>
 *         <li><em> 400 </em> : No <var> role </var> was specified in the URL path,
 *             or the record exists but could not be deleted. </li> 
 *         <li><em> 404 </em> : The record was not found. </li>
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *   </dl>
 *  </p>
 * @author Robert Fromont robert@fromont.net.nz
 */
@WebServlet(urlPatterns = "/api/admin/roles/*", loadOnStartup = 20)
@RequiredRole("admin")
public class AdminRoles extends TableServletBase {   

   public AdminRoles() {
      super("role_definition", // table
            new Vector<String>() {{ // primary/URL keys
               add("role_id");
            }},
            new Vector<String>() {{ // columns
               add("description");
            }},
            "role_id"); // order
      
      create = true;
      read = true;
      update = true;
      delete = true;
      
      deleteChecks = new Vector<DeleteCheck>() {{
            add(new DeleteCheck(
                   "SELECT COUNT(*) FROM role_definition"
                   +" WHERE role_id = ? AND role_id IN ('view','edit','admin')",
                   "role_id",
                   "System roles cannot be deleted."));
         }};
      beforeDelete = new Vector<DeleteCheck>() {{
            add(new DeleteCheck("DELETE FROM role_permission WHERE role_id = ?", "role_id", null));
         }};
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
         if (!record.containsKey("role_id") || record.isNull("role_id")) {
            errors = new Vector<String>() {{ add(localize(request, "No role ID was provided.")); }};
         } else {
            // trim name
            if (!record.getString("role_id").equals(record.getString("role_id").trim())) {
               record = createMutableCopy(record, "role_id")
                  .add("role_id", record.getString("role_id").trim())
                  .build();
            }
            if (record.getString("role_id").length() == 0) {
               errors = new Vector<String>() {{ add(localize(request, "Role ID cannot be blank.")); }};
            }
         }
      } catch (JsonException x) {
         if (errors == null) errors = new Vector<String>();
         errors.add(x.toString());
         // not expecting this, so log it:
         System.err.println("AdminRoles.validateBeforeUpdate: ERROR " + x);
      }
      if (errors != null) throw new ValidationException(errors);
      return record;
   } // end of validateBeforeUpdate()
   
   private static final long serialVersionUID = 1;
} // end of class AdminRoles
