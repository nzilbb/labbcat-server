//
// Copyright 2023-2025 New Zealand Institute of Language, Brain and Behaviour, 
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
import java.util.List;
import java.util.Vector;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import nzilbb.labbcat.server.api.TableServletBase;
import nzilbb.labbcat.server.api.RequiredRole;

/**
 * <tt>/api/admin/categories/<var>class_id</var>[/<var>category</var>]</tt> 
 * : Administration of <em> category </em> records.
 *  <p> Allows administration (Create/Read/Update/Delete) of layer/attribute category records via
 *  JSON-encoded objects with the following attributes:
 *   <dl>
 *    <dt> class_id </dt><dd> The scope of the category - either "transcript" or "speaker". </dd>
 *    <dt> category </dt><dd> The name of the category. </dd>
 *    <dt> description </dt><dd> The description of t/he category. </dd>
 *    <dt> display_order </dt><dd> The order in which the category appears amongst others. </dd>
 *    <dt> _cantDelete </dt><dd> This is not a database field, but rather is present in
 *         records returned from the server that can not currently be deleted; 
 *         a string representing the reason the record can't be deleted. </dd>
 *   </dl>
 *  <p> The following operations, specified by the HTTP method, are supported:
 *   <dl>
 *    <dt> POST </dt><dd> Create a new record.
 *     <ul>
 *      <li><em> Request Body </em> - a JSON-encoded object representing the new record
 *       (excluding <var>category_id</var>). </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object representing the new record (including <var>category_id</var>). </li>
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
 *    <dt> PUT </dt><dd> Update an existing record, specified by the <var> category </var> given in the
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
 *      <li><em> Request Path </em> - /api/admin/categories/<var>class_id</var>/<var>category</var> where 
 *          <var> category </var> is the database ID of the record to delete.</li>
 *      <li><em> Response Body </em> - the standard JSON envelope, including a message if
 *          the request succeeds or an error explaining the reason for failure. </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was successfully deleted. </li>
 *         <li><em> 400 </em> : No <var> category </var> was specified in the URL path,
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
public class Categories extends TableServletBase {   
  
  public Categories() {
    super("attribute_category", // table
          new Vector<String>() {{ // primary keys
            add("class_id");
            add("category");
          }},
          new Vector<String>() {{ // columns
            add("description");
            add("display_order");
          }},
          "class_id, display_order, category"); // order
    
    create = true;
    read = true;
    update = true;
    delete = true;
    
    deleteChecks = new Vector<DeleteCheck>() {{
        add(new DeleteCheck(
              "SELECT COUNT(*), MIN(attribute) FROM attribute_definition"
              +" WHERE class_id = ? AND category = ?",
              new Vector<String>(){{add("class_id");add("category");}},
              "{0,choice,1#There is still a layer using this category: {1}"
              +"|1<There are still {0} layers using this category, including {1}}"));
        add(new DeleteCheck(
              "SELECT COUNT(*), MIN(short_description) FROM layer"
              +" WHERE 'layer' = ? AND category = ?",
              new Vector<String>(){{add("class_id");add("category");}},
              "{0,choice,1#There is still a layer using this category: {1}"
              +"|1<There are still {0} layers using this category, including {1}}"));
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
    JsonObject record,
    Connection connection) throws ValidationException {
    
    Vector<String> errors = new Vector<String>();
    try {
      if (!record.containsKey("class_id") || record.isNull("class_id")) {
        errors.add(localize("No scope was provided."));
      } else {
        // trim class_id
        if (!record.getString("class_id").equals(record.getString("class_id").trim())) {
          record = createMutableCopy(record, "class_id")
            .add("class_id", record.getString("class_id").trim())
            .build();
        }
        if (record.getString("class_id").length() == 0) {
          errors.add(localize("Scope cannot be blank."));
        }
        // validate class_id
        if (!record.getString("class_id").equals("transcript")
            && !record.getString("class_id").equals("speaker")
            && !record.getString("class_id").equals("layer")) {
          errors.add(localize("Scope invalid: {0}", record.getString("class_id")));
        }
      }

      if (!record.containsKey("category") || record.isNull("category")) {
        errors.add(localize("No category name was provided."));
      } else {
        // trim name
        if (!record.getString("category").equals(record.getString("category").trim())) {
          record = createMutableCopy(record, "category")
            .add("category", record.getString("category").trim())
            .build();
        }
        if (record.getString("category").length() == 0) {
          errors.add(localize("Category name cannot be blank."));
        }
      }
    } catch (JsonException x) {
      errors.add(x.toString());
      // not expecting this, so log it:
      context.servletLog("Categories.validateBeforeUpdate: ERROR " + x);
    }
    if (errors.size() > 0) throw new ValidationException(errors);
    return record;
  } // end of validateBeforeUpdate()
  
} // end of class Categories
