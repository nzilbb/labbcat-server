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
import java.util.List;
import java.util.Vector;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;

/**
 * <tt>/api/admin/projects[/<var>project</var>]</tt> 
 * : Administration of <em> project </em> records.
 *  <p> Allows administration (Create/Read/Update/Delete) of project records via
 *  JSON-encoded objects with the following attributes:
 *   <dl>
 *    <dt> project_id </dt><dd> The database key for the record. </dd>
 *    <dt> project </dt><dd> The name of the project. </dd>
 *    <dt> description </dt><dd> The description of the project. </dd>
 *    <dt> _cantDelete </dt><dd> This is not a database field, but rather is present in
 *         records returned from the server that can not currently be deleted; 
 *         a string representing the reason the record can't be deleted. </dd>
 *   </dl>
 *  <p> The following operations, specified by the HTTP method, are supported:
 *   <dl>
 *    <dt> POST </dt><dd> Create a new record.
 *     <ul>
 *      <li><em> Request Body </em> - a JSON-encoded object representing the new record
 *       (excluding <var>project_id</var>). </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object representing the new record (including <var>project_id</var>). </li>
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
 *    <dt> PUT </dt><dd> Update an existing record, specified by the <var> project </var> given in the
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
 *      <li><em> Request Path </em> - /api/admin/projects/<var>project</var> where 
 *          <var> project </var> is the database ID of the record to delete.</li>
 *      <li><em> Response Body </em> - the standard JSON envelope, including a message if
 *          the request succeeds or an error explaining the reason for failure. </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was successfully deleted. </li>
 *         <li><em> 400 </em> : No <var> project </var> was specified in the URL path,
 *             or the record exists but could not be deleted. </li> 
 *         <li><em> 404 </em> : The record was not found. </li>
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *   </dl>
 *  </p>
 * @author Robert Fromont robert@fromont.net.nz
 */
@WebServlet(urlPatterns = "/api/admin/projects/*", loadOnStartup = 20)
@RequiredRole("admin")
public class AdminProjects extends TableServletBase {   

   public AdminProjects() {
      super("project", // table
            new Vector<String>() {{ // primary keys
               add("project_id");
            }},
            new Vector<String>() {{ // URL keys
               add("project");
            }},
            new Vector<String>() {{ // columns
               add("description");
            }},
            "project"); // order
      
      create = true;
      read = true;
      update = true;
      delete = true;
      
      autoKey = "project_id";
      autoKeyQuery = "SELECT COALESCE(max(project_id) + 1, 1) FROM project";
      
      deleteChecks = new Vector<DeleteCheck>() {{
            add(new DeleteCheck(
                   "SELECT COUNT(*), MIN(short_description) FROM layer WHERE project_id = ?",
                   "project_id",
                   "{0,choice,1#There is still a layer using this project: {1}"
                   +"|1<There are still {0} layers using this project, including {1}}"));
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
         if (!record.containsKey("project") || record.isNull("project")) {
            errors = new Vector<String>() {{
                  add(localize(request, "No project name was provided.")); }};
         } else {
            // trim name
            if (!record.getString("project").equals(record.getString("project").trim())) {
               record = createMutableCopy(record, "project")
                  .add("project", record.getString("project").trim())
                  .build();
            }
            if (record.getString("project").length() == 0) {
               errors = new Vector<String>() {{
                     add(localize(request, "Project name cannot be blank.")); }};
            }
         }
      } catch (JsonException x) {
         if (errors == null) errors = new Vector<String>();
         errors.add(x.toString());
         // not expecting this, so log it:
         System.err.println("AdminProjects.validateBeforeUpdate: ERROR " + x);
      }
      if (errors != null) throw new ValidationException(errors);
      return record;
   } // end of validateBeforeUpdate()
   
   private static final long serialVersionUID = 1;
} // end of class AdminProjects
