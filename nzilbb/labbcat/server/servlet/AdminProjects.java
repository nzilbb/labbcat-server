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
import org.json.JSONObject;
import org.json.JSONException;

/**
 * Servlet that allows administration of rows in the the <em> project </em> table.
 * <p> See <a href="package-summary.html#/api/admin/projects">API summary</a> for more details.
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
                   "{0,choice,1#There is still a layer using this corpus: {1}"
                   +"|1<There are still {0} layers using this corpus, including {1}}"));
         }};
   }

   /**
    * Validates a record before UPDATEing it.
    * @param record The incoming record to validate.
    * @param connection A connection to th database.
    * @return A list of validation errors, which should be null if the record is valid.
    */
   protected List<String> validateBeforeUpdate(JSONObject record, Connection connection) {
      Vector<String> errors = null;
      try {
         if (!record.has("project") || record.isNull("project")) {
            errors = new Vector<String>() {{ add("No project name was provided."); }};
         } else {
            // trim name
            record.put("project", record.getString("project").trim());
            if (record.getString("project").length() == 0) {
               errors = new Vector<String>() {{ add("Project name cannot be blank."); }};
            }
         }
      } catch (JSONException x) {
         if (errors == null) errors = new Vector<String>();
         errors.add(x.toString());
         // not expecting this, so log it:
         System.err.println("AdminProjects.validateBeforeUpdate: ERROR " + x);
      }
      return errors;
   } // end of validateBeforeUpdate()
   
   private static final long serialVersionUID = 1;
} // end of class AdminProjects
