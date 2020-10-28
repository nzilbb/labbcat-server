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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Vector;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.stream.JsonGenerator;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import nzilbb.util.IO;

/**
 * Servlet that allows administration of the corpus information file.
 * <p> See <a href="package-summary.html#/api/admin/info">API summary</a> for
 * more details.
 * @author Robert Fromont robert@fromont.net.nz
 */
@WebServlet({"/api/admin/info"} )
@RequiredRole("admin")
public class AdminInfo extends LabbcatServlet {

   /**
    * Default constructor.
    */
   public AdminInfo() {
   } // end of constructor
   
   /**
    * GET handler: get the corpus information.
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
               response.setContentType("text/html");               
               response.setCharacterEncoding("UTF-8");
               File infoHtml = new File(getServletContext().getRealPath("info.html"));
               if (infoHtml.exists()) {
                  IO.Pump(new FileInputStream(infoHtml), response.getOutputStream());
               }
            }
         } finally {
            connection.close();
         }
      } catch(SQLException exception) {
         log("AdminInfo GET: Couldn't connect to database: " + exception);
         response.setContentType("application/json");
         response.setCharacterEncoding("UTF-8");
         writeResponse(response, failureResult(exception));
      }      
   }
   
   /**
    * PUT handler: update corpus information.
    */
   @Override
   protected void doPut(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
      System.out.println("doPut");
      try {
         Connection connection = newConnection();
         try {
            if (!hasAccess(request, response, connection)) {
               System.out.println("no Access");
               return;
            } else {
               response.setContentType("application/json");
               response.setCharacterEncoding("UTF-8");
               
               File infoHtml = new File(getServletContext().getRealPath("info.html"));
               System.out.println("file " + infoHtml.getPath());
               IO.Pump(request.getInputStream(), new FileOutputStream(infoHtml));
               System.out.println("written " + infoHtml.getPath());
               
               writeResponse(response, successResult(request, null, "Record updated."));
               System.out.println("response written");
            }
         } finally {
            connection.close();
         }
      } catch(IOException exception) {
         log("TableServletBase PUT: IO error: " + exception);
         response.setContentType("application/json");
         response.setCharacterEncoding("UTF-8");
         writeResponse(response, failureResult(exception));
      } catch(SQLException exception) {
         log("TableServletBase PUT: Couldn't connect to database: " + exception);
         response.setContentType("application/json");
         response.setCharacterEncoding("UTF-8");
         writeResponse(response, failureResult(exception));
      }      
      System.out.println("doPut finished");
   }
   
   private static final long serialVersionUID = 1;
}
