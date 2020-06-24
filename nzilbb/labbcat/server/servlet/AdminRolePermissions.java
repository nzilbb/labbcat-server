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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.List;
import java.util.Vector;
import javax.servlet.annotation.WebServlet;
import org.json.JSONObject;
import org.json.JSONException;

/**
 * Servlet that allows administration of rows in the the <em> role_permissions </em> table.
 * <p> See <a href="package-summary.html#/api/admin/rolepermissions">API summary</a> for more details.
 * @author Robert Fromont robert@fromont.net.nz
 */
@WebServlet(urlPatterns = "/api/admin/roles/permissions/*", loadOnStartup = 20)
@RequiredRole("admin")
public class AdminRolePermissions extends TableServletBase {   

   public AdminRolePermissions() {
      super("role_permission", // table
            new Vector<String>() {{ // primary/URL keys
               add("role_id");
               add("entity");
            }},
            new Vector<String>() {{ // columns
               add("attribute_name");
               add("value_pattern");
            }},
            "role_id, entity"); // order
      
      create = true;
      read = true;
      update = true;
      delete = true;      
   }

   /**
    * Validates a record before UPDATEing it.
    * @param record The incoming record to validate.
    * @param connection A connection to th database.
    * @return A list of validation errors, which should be null if the record is valid.
    */
   @Override
   protected List<String> validateBeforeUpdate(JSONObject record, Connection connection) {
      Vector<String> errors = null;
      try {
         if (!record.has("role_id") || record.isNull("role_id")
             || record.getString("role_id").length() == 0) {
            errors = new Vector<String>() {{ add("No role ID was provided."); }};
         } 
         if (!record.has("entity") || record.isNull("entity")) {
            if (errors == null) errors = new Vector<String>();
            errors.add("No entity was provided.");
         } else {
            // check it includes at least one of: t(ranscript), i(mage), a(udio), v(ideo)
            if (!record.getString("entity").matches("^[tiav]+$")) {
               if (errors == null) errors = new Vector<String>();
               errors.add("Invalid entity specifier: " + record.get("entity"));
            }
         }
         if (!record.has("attribute_name") || record.isNull("attribute_name")
             || record.getString("attribute_name").length() == 0) {
            if (errors == null) errors = new Vector<String>();
            errors.add("No transcript attribute was specified.");
         }
         if (!record.has("value_pattern") || record.isNull("value_pattern")
             || record.getString("value_pattern").length() == 0) {
            if (errors == null) errors = new Vector<String>();
            errors.add("No attribute value pattern was specified.");
         } else {
            try { Pattern.compile(record.getString("value_pattern")); }
            catch(PatternSyntaxException exception) {
               if (errors == null) errors = new Vector<String>();
               errors.add(
                  "Invalid value pattern: " + record.get("value_pattern") + " - " + exception);
            }
         }
      } catch (JSONException x) {
         if (errors == null) errors = new Vector<String>();
         errors.add(x.toString());
         // not expecting this, so log it:
         System.err.println("AdminRolePermissions.validateBeforeUpdate: ERROR " + x);
      }
      return errors;
   } // end of validateBeforeUpdate()

   /**
    * Validates a record before INSERTing it.
    * @param record The incoming record to validate.
    * @param connection A connection to th database.
    * @return A list of validation errors, which should be null if the record is valid.
    */
   @Override
   protected List<String> validateBeforeCreate(JSONObject record, Connection connection) {
      List<String> errors = validateBeforeUpdate(record, connection);
      try {
         // check it's a valid role
         PreparedStatement sql = connection.prepareStatement(
            "SELECT role_id FROM role_definition WHERE role_id = ?");
         sql.setString(1, record.getString("role_id"));
         ResultSet rs = sql.executeQuery();
         try {
            if (!rs.next()) {
               if (errors == null) errors = new Vector<String>();
               errors.add(
                  "Invalid role ID: " + record.getString("role_id"));
            }
         }
         finally {
            rs.close();
            sql.close();
         }
         
         // check it's a valid transcript attribute, or "corpus"
         if (!record.getString("attribute_name").equals("corpus")) {
            sql = connection.prepareStatement(
               "SELECT attribute FROM attribute_definition"
               +" WHERE class_id = 'transcript' AND attribute = ?");
            sql.setString(1, record.getString("attribute_name"));
            rs = sql.executeQuery();
            try {
               if (!rs.next()) {
                  if (errors == null) errors = new Vector<String>();
                  errors.add(
                     "Invalid transcript attribute: " + record.getString("attribute_name"));
               }
            }
            finally {
               rs.close();
               sql.close();
            }
         } // attribute_name != "corpus"
      } catch (SQLException x) {
         if (errors == null) errors = new Vector<String>();
         errors.add(x.toString());
         // not expecting this, so log it:
         System.err.println("AdminRolePermissions.validateBeforeInsert: ERROR " + x);
      }
      return errors;
   } // end of validateBeforeUpdate()
   
   private static final long serialVersionUID = 1;
} // end of class AdminRolePermissions
