//
// Copyright 2019-2020 New Zealand Institute of Language, Brain and Behaviour, 
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

import java.io.*;
import java.net.*;
import javax.servlet.*; // d:/jakarta-tomcat-5.0.28/common/lib/servlet-api.jar
import javax.servlet.http.*;
import javax.servlet.annotation.WebServlet;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;
import javax.xml.parsers.*;
import javax.xml.xpath.*;
import org.json.*;
import org.w3c.dom.*;
import org.xml.sax.*;
import nzilbb.configure.ParameterSet;
import nzilbb.ag.GraphStoreAdministration;
import nzilbb.ag.Schema;
import nzilbb.ag.Graph;
import nzilbb.ag.serialize.GraphSerializer;
import nzilbb.ag.serialize.SerializationException;
import nzilbb.ag.serialize.util.ConfigurationHelper;
import nzilbb.ag.serialize.util.NamedStream;
import nzilbb.ag.serialize.util.Utility;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.util.IO;

/**
 * Servlet that provides information about the current user.
 * <p> See <a href="package-summary.html#/api/user">API summary</a> for more details.
 * @author Robert Fromont
 */
@WebServlet("/api/user")
public class User extends LabbcatServlet {
   
   // Attributes:

   /**
    * Constructor
    */
   public User() {
   } // end of constructor

   // Servlet methods
   
   /**
    * The GET method for the servlet.
    * <p> This returns information about the current user - their ID and the roles they have.
    * @param request HTTP request
    * @param response HTTP response
    */
   @Override
   public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {      
      response.setContentType("application/json");
      JSONWriter jsonOut = new JSONWriter(response.getWriter());
      startResult(jsonOut);
      jsonOut.object();
      String user = request.getRemoteUser();
      jsonOut.key("user").value(user);
      jsonOut.key("roles").array();
      try {
         if (user == null) { // not using authentication
         jsonOut
            .value("view")
            .value("edit")
            .value("admin");
         } else {
            Connection db = newConnection();
            PreparedStatement sqlUserGroups = db.prepareStatement(
               "SELECT role_id FROM role WHERE user_id = ?");
            sqlUserGroups.setString(1, user);
            ResultSet rstUserGroups = sqlUserGroups.executeQuery();
            while (rstUserGroups.next()) {
               jsonOut.value(rstUserGroups.getString("role_id"));
            } // next group
            rstUserGroups.close();
            sqlUserGroups.close();
            db.close();
         }
         jsonOut.endArray();
         jsonOut.endObject();
         endSuccessResult(request, jsonOut, null);
      } catch(SQLException exception) {
         log("User GET: Database operation failed: " + exception);
         jsonOut.endArray();
         jsonOut.endObject();
         endFailureResult(request, jsonOut, exception.getMessage());
      }
   }
   
   private static final long serialVersionUID = -1;
} // end of class User
