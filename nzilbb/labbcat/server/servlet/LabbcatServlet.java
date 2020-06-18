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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Vector;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.*;
import javax.xml.xpath.*;
import nzilbb.ag.*;
import nzilbb.labbcat.server.db.*;
import nzilbb.util.IO;
import org.json.*;
import org.w3c.dom.*;
import org.xml.sax.*;

/**
 * Base class for other servlets.
 * @author Robert Fromont robert@fromont.net.nz
 */
public class LabbcatServlet extends HttpServlet {
   
   // Attributes:   

   protected String driverName;
   protected String connectionURL;
   protected String connectionName;
   protected String connectionPassword;

   protected String title;
   protected String version;
   
   // Methods:
   
   /**
    * Default constructor.
    */
   public LabbcatServlet() {
   } // end of constructor

   /** 
    * Initialise the servlet by loading the database connection settings.
    */
   public void init() {
      try {
         log("init...");

         // get version info
         File versionTxt = new File(getServletContext().getRealPath("version.txt"));
         if (versionTxt.exists()) {
            try {
               version = IO.InputStreamToString(new FileInputStream(versionTxt));
            } catch(IOException exception) {
               log("Can't read version.txt: " + exception);
            }
         }

         // get database connection info
         File contextXml = new File(getServletContext().getRealPath("META-INF/context.xml"));
         if (contextXml.exists()) { // get database connection configuration from context.xml
            Document doc = DocumentBuilderFactory.newInstance()
               .newDocumentBuilder().parse(new InputSource(new FileInputStream(contextXml)));
            
            // locate the node(s)
            XPath xpath = XPathFactory.newInstance().newXPath();
            driverName = "com.mysql.cj.jdbc.Driver";
            connectionURL = xpath.evaluate("//Realm/@connectionURL", doc);
            connectionName = xpath.evaluate("//Realm/@connectionName", doc);
            connectionPassword = xpath.evaluate("//Realm/@connectionPassword", doc);

            // ensure it's registered with the driver manager
            Class.forName(driverName).getConstructor().newInstance();
         } else {
            log("Configuration file not found: " + contextXml.getPath());
         }
      } catch (Exception x) {
         log("failed", x);
      } 
   }

   /**
    * Gets a graph store, creating a new one if required.
    * @return A connected store.
    */
   protected synchronized SqlGraphStoreAdministration getStore(HttpServletRequest request)
      throws SQLException, PermissionException {
      Connection connection = newConnection();
      return new SqlGraphStoreAdministration(
         baseUrl(request), connection, request.getRemoteUser());
   } // end of getStore()

   /**
    * Saves a store for later use.
    * @param store
    */
   protected synchronized void cacheStore(SqlGraphStoreAdministration store) {
      // disconnect database connection
      try {
         store.getConnection().close();
      } catch(SQLException exception) {}
   } // end of cacheStore()

   /**
    * POST handler simply invokes the GET handler. Any functions that can only execute
    * with GET but not POST must themselves validate the request method.
    * @see #doGet(HttpServletRequest,HttpServletResponse)
    */
   @Override
   protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
      
      doGet(request, response);
   }

   /**
    * Creates a new database connection object
    * @return A connected connection object
    * @throws Exception
    */
   protected Connection newConnection()
      throws SQLException { 
      return DriverManager.getConnection(connectionURL, connectionName, connectionPassword);
   } // end of newDatabaseConnection()
   
   /**
    * Determine the baseUrl for the server.
    * @param request The request.
    * @return The baseUrl.
    */
   protected String baseUrl(HttpServletRequest request) {
      if (request.getSession() != null && request.getSession().getAttribute("baseUrl") != null) {
         // get it from the session
         return request.getSession().getAttribute("baseUrl").toString();
      } else if (getServletContext().getInitParameter("baseUrl") != null
               && getServletContext().getInitParameter("baseUrl").length() > 0) {
         // get it from the webapp configuration
         return getServletContext().getInitParameter("baseUrl");
      } else { // infer it from the request itself
         try {
            URL url = new URL(request.getRequestURL().toString());
            return url.getProtocol() + "://"
               + url.getHost() + (url.getPort() < 0?"":":"+url.getPort())
               + ("/".equals(
                     getServletContext().getContextPath())?""
                  :getServletContext().getContextPath());
         } catch(MalformedURLException exception) {
            return request.getRequestURI().replaceAll("/api/store/.*","");
         }
      }
   } // end of baseUrl()
   
   /**
    * Determines whether the request can continue. If the servlet is annotated with {@link
    * RequiredRole}, whether the user is in that role.
    * <p> If this method returns false, a side-effect is that response.setStatus() has been
    * called with an appropriate status code.
    * @param request The request for identifying the user.
    * @param response The response for possibly setting the status.
    * @param db A connected database connection.
    * @return true if the request is allowed, false otherwise.
    * @throws SQLException If a database error occurs.
    */
   protected boolean hasAccess(HttpServletRequest request, HttpServletResponse response, Connection db)
      throws ServletException, IOException {
      RequiredRole requiredRole = getClass().getAnnotation(RequiredRole.class);
      if (requiredRole != null) {
         try {
            if (!isUserInRole(requiredRole.value(), request, db)) {
               response.setStatus(HttpServletResponse.SC_FORBIDDEN);
               return false;
            } else {
               return true;
            }
         } catch(SQLException exception) {
            log("hasAccess: ERROR: " + exception);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return false;
         }
      } else { // no particular role required
         return true;
      }
   }

   /**
    * Determines whether the logged-in user is in the given role.
    * <p> Side effects:
    * <ul>
    *  <li>The session has a possibly new attribute called "security" which indicates what
    *      type of security is being used; one of "none", "JDBCRealm", "JNDIRealm"</li>
    *  <li>The session has a possibly new set of attributes called "group_<i>role</i>",
    *      one for each role the user has.</li>
    *  <li>The request may have a new attribute called "reset_password", if they are
    *      marked for resetting their password.</li>
    * </ul>
    * @param role The desired role.
    * @param request The request.
    * @param db A connected database connection.
    * @return true if the user is in the given role, false otherwise.
    */
   public static boolean isUserInRole(String role, HttpServletRequest request, Connection db)
      throws SQLException {
      // load user groups
      if (request.getSession().getAttribute("security") == null) {
         String user = request.getRemoteUser();
         if (user == null) { // not using authentication
            request.getSession().setAttribute("security", "none");
            request.getSession().setAttribute("group_view", Boolean.TRUE);
            request.getSession().setAttribute("group_edit", Boolean.TRUE);
            request.getSession().setAttribute("group_admin", Boolean.TRUE);
         } else { // using authentication
            PreparedStatement sqlUserGroups = db.prepareStatement(
               "SELECT role_id FROM role WHERE user_id = ?");
            sqlUserGroups.setString(1, user);
            ResultSet rstUserGroups = sqlUserGroups.executeQuery();
            while (rstUserGroups.next()) {
               request.getSession().setAttribute(
                  "group_" + rstUserGroups.getString("role_id"), Boolean.TRUE);
            } // next group
            rstUserGroups.close();
            sqlUserGroups.close();
		     
            // check what kind of security we're using
            PreparedStatement sqlUser = db.prepareStatement(
               "SELECT reset_password FROM miner_user WHERE user_id = ?");
            sqlUser.setString(1, user);
            ResultSet rstUser = sqlUser.executeQuery();
            if (rstUser.next()) {
               // this user id is in the user table - this means we're
               // using JDBCRealm security to connect to our own DB
               request.getSession().setAttribute("security", "JDBCRealm");
               if (rstUser.getInt("reset_password") == 1) {
                  request.setAttribute("reset_password", Boolean.TRUE);
               }
            } else {
               // this user id is not in the user table - this means
               // we're using some other security mechanism - probably
               // LDAP via JNDI
               request.getSession().setAttribute("security", "JNDIRealm");
            } // user is in user table
            rstUser.close();
            sqlUser.close();
         } // using authentication
      } // security not set yet, must be logging on
         
      return request.getSession().getAttribute("group_" + role) != null;
   } // end of isUserInRole()
   
   /**
    * Creates a JSON object representing a success result, with the given model.
    * @param result The result object.
    * @param message An optional message to include in the response envelope.
    * @return An object for returning as the request result.
    */
   protected JSONObject successResult(Object result, String message) {
      JSONObject response = new JSONObject();
      response.put("title", title);
      response.put("version", version);
      response.put("code", 0); // TODO deprecate?
      response.put("errors", new JSONArray());
      if (message == null) {
         response.put("messages", new JSONArray());
      } else {
         response.append("messages", message);
      }
      if (result != null) {
         if (result instanceof IJSONableBean) {
            response.put("model", new JSONObject((IJSONableBean)result));
         } else {
            response.put("model", result);
         }
      } else {
         response.put("model", JSONObject.NULL);
      }
      return response;
   } // end of successResult()

   /**
    * Start writing a standard JSON result, including the model key.
    * <p> The caller should write the model object next, and then call 
    * {@link #endSuccessResult(JSONWriter,String} or {@link #endFailureResult(JSONWriter,String)} 
    * @param writer The object to write to.
    * @return The given writer.
    */
   protected JSONWriter startResult(JSONWriter writer) {
      writer.object();
      writer.key("title").value(title);
      writer.key("version").value(version);
      writer.key("model");
      return writer;
   } // end of successResult()
   
   /**
    * Finish writing a standard JSON success result that was started with 
    * {@link #startResult(JSONWriter)}
    * @param writer The object to write to.
    * @param message An optional message
    * @return The given writer.
    */
   protected JSONWriter endSuccessResult(JSONWriter writer, String message) {
      writer.key("messages").array();
      if (message != null) writer.value(message);
      writer.endArray();
      writer.key("code").value(0); // TODO deprecate?
      writer.key("errors").array().endArray();
      writer.endObject();
      return writer;
   } // end of successResult()
   
   /**
    * Finish writing a standard JSON failure result that was started with 
    * {@link #startResult(JSONWriter)}
    * @param writer The object to write to.
    * @param message An error message
    * @return The given writer.
    */
   protected JSONWriter endFailureResult(JSONWriter writer, String message) {
      writer.key("errors").array();
      if (message != null) writer.value(message);
      writer.endArray();
      writer.key("code").value(1); // TODO deprecate?
      writer.key("messages").array().endArray();
      writer.endObject();
      return writer;
   } // end of successResult()
   
   /**
    * Creates a JSON object representing a failure result.
    * @param messages The error messages to return.
    * @return An object for returning as the request result.
    */
   protected JSONObject failureResult(Collection<String> messages) {
      JSONObject result = new JSONObject();
      result.put("title", title);
      result.put("version", version);
      result.put("code", 1); // TODO deprecate?
      if (messages == null) {
         result.put("errors", new JSONArray());
      } else {
         result.put("errors", messages);
      }
      result.put("messages", new JSONArray());
      result.put("model", JSONObject.NULL);
      return result;
   } // end of failureResult()

   /**
    * Creates a JSON object representing a failure result.
    * @param message The error message to return.
    * @return An object for returning as the request result.
    */
   protected JSONObject failureResult(String message) {
      JSONObject result = new JSONObject();
      result.put("title", title);
      result.put("version", version);
      result.put("code", 1); // TODO deprecate?
      if (message == null) {
         result.put("errors", new JSONArray());
      } else {
         result.append("errors", message);
      }
      result.put("messages", new JSONArray());
      result.put("model", JSONObject.NULL);
      return result;
   } // end of failureResult()

   /**
    * Creates a JSON object representing a failure result.
    * @param message The error message to return.
    * @return An object for returning as the request result.
    */
   protected JSONObject failureResult(Throwable t) {
      JSONObject result = new JSONObject();
      result.put("title", title);
      result.put("version", version);
      result.put("code", 1); // TODO deprecate?
      result.append("errors", t.getMessage());
      result.put("exception", new JSONObject()
                 .put("type", t.getClass().getSimpleName())
                 .put("message", t.getMessage()));
      result.put("messages", new JSONArray());
      result.put("model", JSONObject.NULL);
      return result;
   } // end of failureResult()

   private static final long serialVersionUID = 1;
} // end of class LabbcatServlet
