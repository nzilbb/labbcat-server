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
 * Servlet that allows administration of rows in the the <em> role </em> table.
 * <p> See <a href="package-summary.html#/api/admin/roles">API summary</a> for more details.
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
