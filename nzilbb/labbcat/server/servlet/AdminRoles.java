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
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import org.json.JSONObject;
import org.json.JSONException;

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
    * @param record The incoming record to validate.
    * @param connection A connection to th database.
    * @return A list of validation errors, which should be null if the record is valid.
    */
   @Override
   protected List<String> validateBeforeUpdate(HttpServletRequest request, JSONObject record, Connection connection) {
      Vector<String> errors = null;
      try {
         if (!record.has("role_id") || record.isNull("role_id")) {
            errors = new Vector<String>() {{ add(localize(request, "No role ID was provided.")); }};
         } else {
            // trim name
            record.put("role_id", record.getString("role_id").trim());
            if (record.getString("role_id").length() == 0) {
               errors = new Vector<String>() {{ add(localize(request, "Role ID cannot be blank.")); }};
            }
         }
      } catch (JSONException x) {
         if (errors == null) errors = new Vector<String>();
         errors.add(x.toString());
         // not expecting this, so log it:
         System.err.println("AdminRoles.validateBeforeUpdate: ERROR " + x);
      }
      return errors;
   } // end of validateBeforeUpdate()
   
   private static final long serialVersionUID = 1;
} // end of class AdminRoles
