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
 * <tt>/api/admin/info</tt> : Administration of the corpus information document.
 *  <p> Allows administration (Read/Update) of the corpus information document, which is 
 *   an HTML document that provides information about the database as a whole; the corpora
 *   it contains, where the data comes from, what meta-data is specified, and any other
 *   information.
 *   <p> The following operations, specified by the HTTP method, are supported:
 *   <dl>
 *    <dt> GET </dt><dd> Read the HTML document. 
 *     <ul>
 *      <li><em> Response Body </em> - an HTML document, which may be completely empty.  </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The information was retrieved successfully. </li>
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *    
 *    <dt> PUT </dt><dd> Update the HTML document.
 *     <ul>
 *      <li><em> Request Body </em> - the HTML document to save. </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object representing the record. </li> 
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The document was updated. </li>
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *   </dl>
 *  </p>
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
