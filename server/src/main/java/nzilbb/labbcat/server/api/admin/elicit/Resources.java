//
// Copyright 2026 New Zealand Institute of Language, Brain and Behaviour, 
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
package nzilbb.labbcat.server.api.admin.elicit;

import java.util.Vector;
import nzilbb.labbcat.server.api.TableServletBase;
import nzilbb.labbcat.server.api.RequiredRole;

/**
 * <tt>/api/admin/elicit/resources/<var>task_id</var>[/<var>resource_id</var>]</tt> 
 * : Administration of elicitation task localization resources.
 *  <p> Allows administration (Read/Update/Delete) of participant-facing elicitation task
 *  memessages via JSON-encoded objects with the following attributes:
 *   <dl>
 *    <dt> task_id </dt><dd> The database key for the elicitation task. </dd>
 *    <dt> resource_id </dt><dd> The database key for the resource (message). </dd>
 *    <dt> help </dt><dd> A description of the message to help administrators/translators
 *         understand the purpose of the resource. </dd>
 *    <dt> message </dt><dd> The participant-facing message. </dd>
 *   </dl>
 *  <p> The following operations, specified by the HTTP method, are supported:
 *   <dl>
 *    <dt> GET </dt><dd> Read the records. 
 *     <ul>
 *      <li><em> Request Path </em> - /api/admin/resources/<var>task_id</var> where 
 *          <var> task_id </var> identifies the task of the messages.</li>
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
 *    <dt> PUT </dt><dd> Update an existing record, specified by the
 *         <var> task_name </var> given in the request body.
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
 *   </dl>
 *  </p>
 * @author Robert Fromont robert@fromont.net.nz
 */
@RequiredRole("admin")
public class Resources extends TableServletBase {   
  
  public Resources() {
    super("elicitation_resource_string", // table
          new Vector<String>() {{ // primary keys
            add("task_id");
            add("resource_id");
          }},
          new Vector<String>() {{ // columns
            add("help");
            add("message");
          }},
          "resource_id"); // order
    
    create = false;
    read = true;
    update = true;
    delete = false;
  }
  
} // end of class Resources
