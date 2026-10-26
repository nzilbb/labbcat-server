//
// Copyright 2020-2025 New Zealand Institute of Language, Brain and Behaviour, 
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Vector;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import nzilbb.labbcat.server.api.TableServletBase;
import nzilbb.labbcat.server.api.RequiredRole;

/**
 * <tt>/api/admin/dashboard/{link|sql}[/<var>item_id</var>]</tt> 
 * : Administration of <em> dahboard item </em> records.
 *  <p> Allows administration (Create/Read/Update/Delete) of dashboard items via
 *  JSON-encoded objects with the following attributes:
 *   <dl>
 *    <dt> item_id </dt><dd> A unique identidier for the item. </dd>
 *    <dt> label </dt><dd> The label for the item. </dd>
 *    <dt> icon </dt><dd> The item's icon. </dd>
 *    <dt> definition </dt><dd> The items' definition - i.e. a URL/fragment for "link" items
 *         or an SQL query for "sql" items. </dd>
 *    <dt> dashboard </dt><dd> Whether the item is shown on the home page. </dd>
 *    <dt> express </dt><dd> Whether the item is shown on the 'express' home page. </dd>
 *    <dt> statistics </dt><dd> Whether the item is shown on the statistics page. </dd>
 *    <dt> display_order </dt><dd> The position of the track amongst other tracks. </dd>
 *   </dl>
 *  <p> The following operations, specified by the HTTP method, are supported:
 *   <dl>
 *    <dt> POST </dt><dd> Create a new record.
 *     <ul>
 *      <li><em> Request Body </em> - a JSON-encoded object representing the new record
 *       (excluding <var>item_id</var>). </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object representing the new record (including <var>item_id</var>). </li>

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
 *    <dt> PUT </dt><dd> Update an existing record, specified by the <var> item_id </var>
 *    given in the JSON-encoded request body.
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
 *      <li><em> Request Path </em> - /api/admin/dashboard/{link|sql}[/<var>item_id</var>
 *          where <var> item_id </var> is the ID of the item to delete.</li>
 *      <li><em> Response Body </em> - the standard JSON envelope, including a message if
 *          the request succeeds or an error explaining the reason for failure. </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was successfully deleted. </li>
 *         <li><em> 400 </em> : No <var> item_id </var> was specified in the URL path,
 *             or the record exists but could not be deleted. </li> 
 *         <li><em> 404 </em> : The record was not found. </li>
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *   </dl>
 *  </p>
 * @author Robert Fromont robert@fromont.net.nz
 */
@RequiredRole("admin")
public class Dashboard extends TableServletBase {   
   
   public Dashboard() {
      super("dashboard_item", // table
            new Vector<String>() {{ // primary keys
               add("type");
               add("item_id");
            }},
            new Vector<String>() {{ // columns
               add("label");
               add("icon");
               add("definition");
               add("dashboard");
               add("express");
               add("statistics");
               add("display_order");
            }},
            "display_order"); // order

      whereClause = "type IN ('link','sql')";
      autoKey = "item_id";
      autoKeyQuery = "SELECT COALESCE(max(item_id) + 1, 1) FROM dashboard_item";
      
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
     JsonObject record,
     Connection connection) throws ValidationException {
     Vector<String> errors = null;
     
     final String type = record.getString("type");
     if (!"link".equals(type)
         && !"sql".equals(type)) {
       errors.add(localize("Invalid type: {0}", type));
     }
     try {
       if (!record.containsKey("label") || record.isNull("label")) {
         errors = new Vector<String>() {{
             add(localize("No label was provided.")); }}; // TODO i18n
       }
       if (record.getString("label").trim().length() == 0) {
         errors = new Vector<String>() {{
             add(localize("Label cannot be blank.")); }}; // TODO i18n
       }
     } catch (JsonException x) {
       if (errors == null) errors = new Vector<String>();
       errors.add(x.toString());
     }
     if (errors != null) throw new ValidationException(errors);
     
     if (!record.containsKey("display_order") || record.isNull("display_order")
         || record.get("display_order").toString().equals("")) {
       try {
         // default is one more that the MAX
         PreparedStatement sql = connection.prepareStatement(
           "SELECT COALESCE(MAX(display_order) + 1, 1) FROM dashboard_item");
         ResultSet rs = sql.executeQuery();
         try {
           rs.next();
           record = createMutableCopy(record, "display_order")
             .add("display_order", rs.getInt(1))
             .build();
         } finally {
           rs.close();
           sql.close();
         }            
       } catch(SQLException exception) {
         context.servletLog("ERROR getting default value for display_order: " + exception);
         record = createMutableCopy(record, "display_order")
           .add("display_order", 0)
           .build();
       }
     } 
     return record;
   } // end of validateBeforeUpdate()
   
   private static final long serialVersionUID = 1;
} // end of class Dashboard
