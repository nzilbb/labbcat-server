//
// Copyright 2023 New Zealand Institute of Language, Brain and Behaviour, 
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
 * <tt>/api/categories/<var>class_id</var>[/<var>category</var>]</tt> 
 * : Listing of <em> category </em> records.
 *  <p> Allows listing of layer/attribute category records, returning
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
 *  <p> The following operation, specified by the HTTP method, is supported:
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
 *   </dl>
 *  </p>
 * @author Robert Fromont robert@fromont.net.nz
 */
@WebServlet(urlPatterns = "/api/categories/*", loadOnStartup = 20)
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
    
    create = false;
    read = true;
    update = false;
    delete = false;      
  }
  private static final long serialVersionUID = 1;
} // end of class Categories
