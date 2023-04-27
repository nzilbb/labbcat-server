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
import java.util.List;
import java.util.Vector;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;

/**
 * <tt>/api/admin/mediatracks[/<var>suffix</var>]</tt> 
 * : Administration of <em> media track </em> records.
 *  <p> Allows administration (Create/Read/Update/Delete) of media track records via
 *  JSON-encoded objects with the following attributes:
 *   <dl>
 *    <dt> suffix </dt><dd> The suffix associated with the media track. </dd>
 *    <dt> description </dt><dd> The description of the track. </dd>
 *    <dt> display_order </dt><dd> The position of the track amongst other tracks. </dd>
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
 *    <dt> PUT </dt><dd> Update an existing record, specified by the <var> mediaTrack </var> given in the
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
 *      <li><em> Request Path </em> - /api/admin/mediatracks/<var>suffix</var> where 
 *          <var> suffix </var> is the suffix of the media track to delete.</li>
 *      <li><em> Response Body </em> - the standard JSON envelope, including a message if
 *          the request succeeds or an error explaining the reason for failure. </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was successfully deleted. </li>
 *         <li><em> 400 </em> : No <var> mediaTrack </var> was specified in the URL path,
 *             or the record exists but could not be deleted. </li> 
 *         <li><em> 404 </em> : The record was not found. </li>
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *   </dl>
 *  </p>
 * @author Robert Fromont robert@fromont.net.nz
 */
@WebServlet(urlPatterns = "/api/admin/mediatracks/*", loadOnStartup = 20)
@RequiredRole("admin")
public class AdminMediaTracks extends TableServletBase {   
   
   public AdminMediaTracks() {
      super("media_track", // table
            new Vector<String>() {{ // primary keys
               add("suffix");
            }},
            new Vector<String>() {{ // columns
               add("description");
               add("display_order");
            }},
            "display_order"); // order
      
      create = true;
      read = true;
      update = true;
      delete = true;
      
      emptyKeyAllowed = true;
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
      
      if (!record.containsKey("display_order") || record.isNull("display_order")
          || record.get("display_order").toString().equals("")) {
         try {
            // default is one more that the MAX
            PreparedStatement sql = connection.prepareStatement(
               "SELECT COALESCE(MAX(display_order) + 1, 1) FROM media_track");
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
            log("ERROR getting default value for display_order: " + exception);
            record = createMutableCopy(record, "display_order")
               .add("display_order", 0)
               .build();
         }
      } 
      return record;
   } // end of validateBeforeUpdate()
   
   private static final long serialVersionUID = 1;
} // end of class AdminMediaTracks
