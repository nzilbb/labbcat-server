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
import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.SQLException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.*;
import javax.xml.xpath.*;
import org.json.*;
import org.w3c.dom.*;
import org.xml.sax.*;
import nzilbb.ag.*;
import nzilbb.labbcat.server.db.*;

/**
 * Controller that handles IGraphStore requests.
 * @author Robert Fromont robert@fromont.net.nz
 */
@WebServlet("/api/store/*")
public class Store
   extends HttpServlet
{
   // Attributes:

   protected String driverName;
   protected String connectionURL;
   protected String connectionName;
   protected String connectionPassword;

   protected String title;
   
   // Methods:
   
   /**
    * Default constructor.
    */
   public Store()
   {
   } // end of constructor

   /** 
    * Initialise the servlet
    */
   public void init()
   {
      try
      {
         log("Store.init...");
         
         // get database connection info
         File contextXml = new File(getServletContext().getRealPath("META-INF/context.xml"));
         if (contextXml.exists())
         { // get database connection configuration from context.xml
            Document doc = DocumentBuilderFactory.newInstance()
               .newDocumentBuilder().parse(new InputSource(new FileInputStream(contextXml)));
            
            // locate the node(s)
            XPath xpath = XPathFactory.newInstance().newXPath();
            driverName = "com.mysql.cj.jdbc.Driver";
            connectionURL = xpath.evaluate("//Realm/@connectionURL", doc);
            connectionName = xpath.evaluate("//Realm/@connectionName", doc);
            connectionPassword = xpath.evaluate("//Realm/@connectionPassword", doc);
            
            log("Controller connectionURL from context.xml: " + connectionURL);
         } // get database connection configuration from context.xml
         if (connectionURL == null || connectionURL.length() == 0)
         { // use old web.xml configuration
            driverName = "com.mysql.cj.jdbc.Driver";
            connectionURL = getServletContext().getInitParameter("dbConnectString");
            connectionName = getServletContext().getInitParameter("dbUser");
            connectionPassword = getServletContext().getInitParameter("dbPassword");
            log("Controller dbConnectString from web.xml: " + connectionURL);
         } // use old web.xml configuration
         
         Class.forName(driverName).newInstance();
      }
      catch (Exception x)
      {
         log("Store.init() failed", x);
      } 
   }

   /**
    * GET handler
    */
   @Override
   protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException
   {
      JSONObject json = null;
      try
      {
         Connection connection = newConnection();
         try
         {
            SqlGraphStoreAdministration store = (SqlGraphStoreAdministration)
               request.getSession().getAttribute("store");
            if (store != null)
            { // use this request's connection
               store.setConnection(connection);
               
               // stop other requests from using this store at the same time
               request.getSession().setAttribute("store", null);
            }
            else
            { // no store yet, so create one
               store = new SqlGraphStoreAdministration(
                  request.getSession().getAttribute("baseUrl").toString(), 
                  connection, request.getRemoteUser());

               if (title == null)
               {
                  title = store.getSystemAttribute("title");
               }
            }
            try
            {
               String pathInfo = request.getPathInfo().toLowerCase(); // case-insensitive
               if (pathInfo.endsWith("getid"))
               {
                  json = getId(request, response, store);
               }
               else
               {
                  response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                  json = failureResult(null, "Invalid path: " + request.getPathInfo());
               }
               if (json != null)
               {
                  response.setContentType("application/json");
                  response.setCharacterEncoding("UTF-8");
                  json.write(response.getWriter());
                  response.getWriter().flush();
               }
            }
            finally
            {
               // allow other requests in the session to use this store, to allow re-use of cached objects
               request.getSession().setAttribute("store", store);
            }
         }
         finally
         {
            connection.close();
         }
      }
      catch (PermissionException x)
      {
         response.setStatus(HttpServletResponse.SC_FORBIDDEN); // TODO JSON
      }
      catch (StoreException x)
      {
         throw new ServletException(x); // TODO JSON response
      }
      catch (SQLException x)
      {
         throw new ServletException("Cannot connect to database.", x); // TODO JSON response
      }
   }

   /**
    * Creates a new database connection object
    * @return A connected connection object
    * @throws Exception
    */
   public Connection newConnection()
      throws SQLException
   { 
      return DriverManager.getConnection(connectionURL, connectionName, connectionPassword);
   } // end of newDatabaseConnection()
   
   /**
    * Creates a JSON object representing a success result, with the given model.
    * @param model
    * @param message
    * @return An object for returning as the request result.
    */
   public JSONObject successResult(JSONObject model, String message)
   {
      JSONObject result = new JSONObject();
      result.put("title", title);
      result.put("code", 0); // TODO deprecate?
      result.put("errors", new JSONArray());
      if (message == null)
      {
         result.put("messages", new JSONArray());
      }
      else
      {
         result.append("messages", message);
      }
      result.put("model", model);
      return result;
   } // end of successResult()

   /**
    * Creates a JSON object representing a success result, with the given model.
    * @param model
    * @param message
    * @return An object for returning as the request result.
    */
   public JSONObject failureResult(JSONObject model, String message)
   {
      JSONObject result = new JSONObject();
      result.put("title", title);
      result.put("code", 1); // TODO deprecate?
      if (message == null)
      {
         result.put("errors", new JSONArray());
      }
      else
      {
         result.append("errors", message);
      }
      result.put("messages", new JSONArray());
      result.put("model", model);
      return result;
   } // end of failureResult()

   // IGraphStore method handlers

   protected JSONObject getId(HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException
   {
      String id = store.getId();
      return successResult(new JSONObject().put("result", id), null);
   }      
   
   private static final long serialVersionUID = 1;
} // end of class Store
