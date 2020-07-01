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

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Vector;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.JSONWriter;

/**
 * Servlet that allows administration of rows in the the <em> system_attributes </em> table.
 * <p> See <a href="package-summary.html#/api/admin/systemattributes">API summary</a> for
 * more details.
 * @author Robert Fromont robert@fromont.net.nz
 */
@WebServlet({"/api/admin/systemattributes"} )
@RequiredRole("admin")
public class AdminSystemAttributes extends LabbcatServlet {

   /**
    * Default constructor.
    */
   public AdminSystemAttributes() {
   } // end of constructor
   
   /**
    * GET handler lists all rows. 
    * <p> The return is JSON encoded, unless the "Accept" request header, or the "Accept"
    * request parameter, is "text/csv", in which case CSV is returned.
    */
   @Override
   protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
      try {
         Connection connection = newConnection();
         try {
            if (!hasAccess(request, response, connection)) {
               return;
            } else {
               response.setContentType("application/json");               
               response.setCharacterEncoding("UTF-8");
               try {
                  PreparedStatement sql = connection.prepareStatement(
                     "SELECT attribute, type, style, label, description, value"
                     +" FROM attribute_definition"
                     +" LEFT OUTER JOIN system_attribute"
                     +" ON attribute_definition.attribute = system_attribute.name"
                     +" WHERE class_id = ''"
                     +" ORDER BY display_order, attribute");
                  PreparedStatement sqlOptions = connection.prepareStatement(
                     "SELECT value, description"
                     +" FROM attribute_option"
                     +" WHERE class_id = '' AND attribute = ?"
                     +" ORDER BY value");
                  ResultSet rs = sql.executeQuery();
                  JSONWriter jsonOut = new JSONWriter(response.getWriter());
                  startResult(jsonOut);
                  jsonOut.array();
                  try {
                     while (rs.next()) {
                        jsonOut.object();
                        try {
                           jsonOut.key("attribute").value(rs.getString("attribute"));
                           String type = rs.getString("type");
                           if (type.startsWith("SELECT ")) type = "select";
                           jsonOut.key("type").value(type);
                           jsonOut.key("style").value(rs.getString("style"));
                           jsonOut.key("label").value(rs.getString("label"));
                           jsonOut.key("description").value(rs.getString("description"));
                           // are there options?
                           PreparedStatement sqlOptionQuery = null;
                           
                           if ("select".equals(rs.getString("type"))) { // predefined options
                              sqlOptionQuery = sqlOptions;
                              sqlOptionQuery.setString(1, rs.getString("attribute"));
                           } else if (rs.getString("type").startsWith("SELECT ")) { // SQL based
                              sqlOptionQuery = connection.prepareStatement(rs.getString("type"));
                           }
                           if (sqlOptionQuery != null) {
                              ResultSet rsOptions = sqlOptionQuery.executeQuery();
                              jsonOut.key("options");
                              jsonOut.object();
                              try {
                                 while (rsOptions.next()) {
                                    jsonOut.key(rsOptions.getString(1));
                                    jsonOut.value(rsOptions.getString(2));
                                 } // next option
                              } finally {
                                 rsOptions.close();
                                 if (sqlOptionQuery != sqlOptions) {
                                    sqlOptionQuery.close();
                                 }
                                 jsonOut.endObject();
                              }                              
                           } // options
                           jsonOut.key("value").value(rs.getString("value"));
                        } finally {
                           jsonOut.endObject();
                        }
                     } // next attribute
                  } finally {
                     jsonOut.endArray(); // all rows, finish array
                     rs.close();
                     sql.close();
                     sqlOptions.close();
                  }
                  endSuccessResult(request, jsonOut, null);
               } catch(SQLException exception) {
                  response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                  log("AdminSystemAttributes GET: ERROR: " + exception);
                  response.setContentType("application/json");
                  failureResult(exception)
                     .write(response.getWriter());
               }
            }
         } finally {
            connection.close();
         }
      } catch(SQLException exception) {
         log("AdminSystemAttributes GET: Couldn't connect to database: " + exception);
         response.setContentType("application/json");
         response.setCharacterEncoding("UTF-8");
         failureResult(exception)
            .write(response.getWriter());
      }      
   }

   /**
    * PUT handler - update an existing row.
    */
   @Override
   protected void doPut(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
      try {
         Connection connection = newConnection();
         try {
            if (!hasAccess(request, response, connection)) {
               return;
            } else {
               response.setContentType("application/json");
               response.setCharacterEncoding("UTF-8");
               
               JSONTokener reader = new JSONTokener(request.getReader());
               JSONObject json = new JSONObject(reader);

               // check it exists and isn't readonly
               PreparedStatement sqlCheck = connection.prepareStatement(
                  "SELECT type FROM attribute_definition"
                  +" WHERE attribute = ? AND class_id = ''");
               sqlCheck.setString(1, json.getString("attribute"));
               ResultSet rsCheck = sqlCheck.executeQuery();
               try {
                  if (rsCheck.next()) { // readonly
                     if (!"readonly".equals(rsCheck.getString("type"))) { // not readonly
                        PreparedStatement sql = connection.prepareStatement(
                           "UPDATE system_attribute SET value = ? WHERE name = ?");
                        sql.setString(1, json.getString("value"));
                        sql.setString(2, json.getString("attribute"));
                        int rows = sql.executeUpdate();
                        if (rows == 0) { // shouldn't be possible
                           response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                           failureResult(
                              request, "Record not updated: {0}", json.getString("attribute"))
                              .write(response.getWriter());
                        } else {
                           successResult(request, json, "Record updated.")
                              .write(response.getWriter());
                        }
                        sql.close();
                     } else { // readonly
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        failureResult(
                           request, "Read-only record: {0}", json.getString("attribute"))
                           .write(response.getWriter());
                     }
                  } else { // not found
                     response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                     failureResult(request, "Record not found: {0}", json.getString("attribute"))
                        .write(response.getWriter());
                  }
               } finally {
                  rsCheck.close();
                  sqlCheck.close();
               }
            }
         } finally {
            connection.close();
         }
      } catch(SQLException exception) {
         log("TableServletBase PUT: Couldn't connect to database: " + exception);
         response.setContentType("application/json");
         response.setCharacterEncoding("UTF-8");
         failureResult(exception)
            .write(response.getWriter());
      }      
   }
   
   private static final long serialVersionUID = 1;
}
