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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Vector;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import javax.json.JsonWriter;
import javax.json.stream.JsonGenerator;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.*;
import javax.xml.xpath.*;
import nzilbb.ag.*;
import nzilbb.labbcat.server.db.*;
import nzilbb.sql.ConnectionFactory;
import nzilbb.sql.mysql.MySQLConnectionFactory;
import nzilbb.util.CloneableBean;
import nzilbb.util.IO;
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
   protected ConnectionFactory connectionFactory;

   protected String title;
   protected String version;

   // Methods:
   
   /**
    * Default constructor.
    */
   public LabbcatServlet() {
      // load a default set of localization resources
      lastBundle = ResourceBundle.getBundle(
         "nzilbb.labbcat.server.locale.Resources", Locale.UK);
      title = getClass().getSimpleName();
   } // end of constructor

   /** 
    * Initialise the servlet by loading the database connection settings.
    */
   public void init() {
      try {
         log("init...");

         // get version info
         File versionTxt = new File(getServletContext().getRealPath("/version.txt"));
         if (versionTxt.exists()) {
            try {
               version = IO.InputStreamToString(new FileInputStream(versionTxt));
            } catch(IOException exception) {
               log("Can't read version.txt: " + exception);
            }
         }

         // get database connection info
         File contextXml = new File(getServletContext().getRealPath("/META-INF/context.xml"));
         if (contextXml.exists()) { // get database connection configuration from context.xml
            Document doc = DocumentBuilderFactory.newInstance()
               .newDocumentBuilder().parse(new InputSource(new FileInputStream(contextXml)));
            
            // locate the node(s)
            XPath xpath = XPathFactory.newInstance().newXPath();
            driverName = "com.mysql.cj.jdbc.Driver";
            connectionURL = xpath.evaluate("//Realm/@connectionURL", doc);
            connectionName = xpath.evaluate("//Realm/@connectionName", doc);
            connectionPassword = xpath.evaluate("//Realm/@connectionPassword", doc);
            connectionFactory = new MySQLConnectionFactory(
               connectionURL, connectionName, connectionPassword);

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
      return new SqlGraphStoreAdministration(
         baseUrl(request), connectionFactory, request.getRemoteUser());
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
      if (Optional.ofNullable(System.getenv("LABBCAT_BASE_URL")).orElse("").length() > 0) {
         // get it from the environment variable
         return System.getenv("LABBCAT_BASE_URL");
      } else if (request.getSession() != null
                 && request.getSession().getAttribute("baseUrl") != null) {
         // get it from the session
         return request.getSession().getAttribute("baseUrl").toString();
      } else if (Optional.ofNullable(getServletContext().getInitParameter("baseUrl"))
                 .orElse("").length() > 0) {
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
    * Determines whether the request can continue. If the servlet is annotated with 
    * {@link RequiredRole}, whether the user is in that role.
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
    * @param args Arguments to be substituted into the message, if any
    * @return An object for returning as the request result.
    */
   @SuppressWarnings("unchecked")
   protected JsonObject successResult(
      HttpServletRequest request, Object result, String message, Object... args) {
      
      JsonObjectBuilder response = Json.createObjectBuilder()
         .add("title", title)
         .add("version", version)
         .add("code", 0) // TODO deprecate?
         .add("errors", Json.createArrayBuilder());      
      if (message == null) {
         response = response.add("messages", Json.createArrayBuilder());
      } else {
         response = response.add(
            "messages", Json.createArrayBuilder()
            .add(localize(request, message, args)));
      }
      if (result != null) {
         if (result instanceof JsonValue) {
            response = response.add("model", (JsonValue)result);
         } else if (result instanceof CloneableBean) {
            response = response.add("model", ((CloneableBean)result).toJson());
         } else if (result instanceof Integer) {
            response = response.add("model", (Integer)result);
         } else if (result instanceof Long) {
            response = response.add("model", (Long)result);
         } else if (result instanceof Double) {
            response = response.add("model", (Double)result);
         } else if (result instanceof Boolean) {
            response = response.add("model", (Boolean)result);
         } else if (result instanceof CloneableBean[]) {
            CloneableBean[] array = (CloneableBean[])result;
            JsonArrayBuilder a = Json.createArrayBuilder();
            for (CloneableBean object : array) {
               a = a.add(object.toJson());
            }
            response = response.add("model", a);
         } else if (result instanceof Object[]) {
            Object[] array = (Object[])result;
            JsonArrayBuilder a = Json.createArrayBuilder();
            for (Object object : array) {
               a = a.add(object.toString());
            }
            response = response.add("model", a);
         } else if (result instanceof Map) {
            Map<String,String> map = (Map<String,String>)result;
            JsonObjectBuilder o = Json.createObjectBuilder();
            for (String k : map.keySet()) {
               o = o.add(k, map.get(k));
            }
            response = response.add("model", o);
         } else {
            response = response.add("model", result.toString());
         }
      } else {
         response = response.add("model", JsonValue.NULL);
      }
      return response.build();
   } // end of successResult()

   /**
    * Creates a JSON object representing a failure result.
    * @param messages The error messages to return, which must be already localized using
    * {@link #localize(HttpServletRequest,String,String)}.
    * @return An object for returning as the request result.
    */
   protected JsonObject failureResult(Collection<String> messages) {
      JsonObjectBuilder response = Json.createObjectBuilder()
         .add("title", title)
         .add("version", version)
         .add("code", 1); // TODO deprecate?
      JsonArrayBuilder errors = Json.createArrayBuilder();
      if (messages != null) {
         for (String error : messages) {
            errors = errors.add(error);
         }
      }
      response = response.add("errors", errors)
         .add("messages", Json.createArrayBuilder())
         .add("model", JsonValue.NULL);
      return response.build();
   } // end of failureResult()

   /**
    * Creates a JSON object representing a failure result.
    * @param message The error message to return.
    * @param args Arguments to be substituted into the message, if any
    * @return An object for returning as the request result.
    */
   protected JsonObject failureResult(HttpServletRequest request, String message, Object... args) {
      
      JsonObjectBuilder response = Json.createObjectBuilder()
         .add("title", title)
         .add("version", version)
         .add("code", 1); // TODO deprecate?
      if (message == null) {
         response = response
            .add("errors", Json.createArrayBuilder());
      } else {
         response = response
            .add("errors", Json.createArrayBuilder().add(
                    localize(request, message, args)));
      }
      response = response
         .add("messages", Json.createArrayBuilder())
         .add("model", JsonValue.NULL);
      return response.build();
   } // end of failureResult()

   /**
    * Creates a JSON object representing a failure result.
    * @param message The error message to return.
    * @return An object for returning as the request result.
    */
   protected JsonObject failureResult(Throwable t) {
      String message = ""+t.getMessage();
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      t.printStackTrace(pw);
      JsonObjectBuilder exception = Json.createObjectBuilder()
         .add("type", t.getClass().getSimpleName())
         .add("message", message)
         .add("stackTrace", sw.toString());
      if (t.getCause() != null) {
         sw = new StringWriter();
         pw = new PrintWriter(sw);
         t.getCause().printStackTrace(pw);
         exception.add("cause", Json.createObjectBuilder()
                       .add("type", t.getCause().getClass().getSimpleName())
                       .add("message", ""+t.getCause().getMessage())
                       .add("stackTrace", sw.toString()));
      }
      
      JsonObjectBuilder result = Json.createObjectBuilder()
         .add("title", title)
         .add("version", version)
         .add("code", 1) // TODO deprecate?
         .add("errors", Json.createArrayBuilder().add(message))
         .add("exception", exception)
         .add("messages", Json.createArrayBuilder())
         .add("model", JsonValue.NULL);
      return result.build();
   } // end of failureResult()

   /**
    * Start writing a standard JSON result, including the model key.
    * <p> The caller should write the model object next, and then call 
    * {@link #endSuccessResult(JsonGenerator,String} 
    * or {@link #endFailureResult(JsonGenerator,String)} 
    * @param writer The object to write to.
    * @return The given writer.
    */
   protected JsonGenerator startResult(JsonGenerator writer, boolean modelIsArray) {
      writer.writeStartObject();
      writer.write("title", title);
      writer.write("version", version);
      if (modelIsArray) {
         writer.writeStartArray("model");
      } else {         
         writer.writeStartObject("model");
      }
      return writer;
   } // end of startResult()
   
   /**
    * Finish writing a standard JSON success result that was started with 
    * {@link #startResult(JsonGenerator)}
    * @param writer The object to write to.
    * @param message An optional message
    * @param args Arguments to be substituted into the message, if any
    * @return The given writer.
    */
   protected JsonGenerator endSuccessResult(
      HttpServletRequest request, JsonGenerator writer, String message, Object... args) {
      writer.writeEnd(); // end whatever we started in startResult
      writer.writeStartArray("messages");
      if (message != null) writer.write(localize(request, message, args));
      writer.writeEnd(); // array
      writer.write("code", 0); // TODO deprecate?
      writer.writeStartArray("errors").writeEnd(); // array
      writer.writeEnd(); // object started in startResult
      writer.flush();
      return writer;
   } // end of endSuccessResult()
   
   /**
    * Finish writing a standard JSON failure result that was started with 
    * {@link #startResult(JsonGenerator)}
    * @param writer The object to write to.
    * @param message An error message
    * @param args Arguments to be substituted into the message, if any
    * @return The given writer.
    */
   protected JsonGenerator endFailureResult(
      HttpServletRequest request, JsonGenerator writer, String message, Object... args) {
      writer.writeStartArray("errors");
      if (message != null) writer.write(localize(request, message, args));
      writer.writeEnd(); // array
      writer.write("code", 1); // TODO deprecate?
      writer.writeStartArray("messages").writeEnd(); // array
      writer.writeEnd(); // obect started in startResult
      writer.flush();
      return writer;
   } // end of endFailureResult()
   
   /**
    * Writes the given JSON-encoded response to the given writer.
    * @param response
    * @param json
    * @throws IOException
    */
   protected void writeResponse(HttpServletResponse response, JsonObject json)
      throws IOException {
      JsonWriter writer = Json.createWriter(response.getWriter());
      writer.writeObject(json);
      writer.close();
   } // end of writeResponse()
   
   /**
    * Writes the given JSON-encoded array response to the given writer.
    * @param response
    * @param json
    * @throws IOException
    */
   protected void writeResponse(HttpServletResponse response, JsonArray json)
      throws IOException {
      JsonWriter writer = Json.createWriter(response.getWriter());
      writer.writeArray(json);
      writer.close();
   } // end of writeResponse()
   
   String lastLanguage = "en";
   Locale lastLocale = Locale.UK;
   ResourceBundle lastBundle;
   /**
    * Localizes the given message to the language found in the "Accept-Language" header of
    * the given request, substituting in the given arguments if any.
    * <p> The message is assumed to be a MessageFormat template like 
    * "Row could not be added: {0}"
    * @param request The request, for discovering the locale.
    * @param message The message format to localize.
    * @param args Arguments to be substituted into the message. 
    * @return The localized message (or if the messages couldn't be localized, the
    * original message) with the given arguments substituted. 
    */
   protected String localize(HttpServletRequest request, String message, Object... args) {

      // determine the Locale/ResourceBundle
      
      String language = request.getHeader("Accept-Language");
      if (language == null) language = lastLanguage;
      if (language == null) language = "en";
      language = language
         // if multiple are specified, use the first one TODO process all possibilities?
         .replaceAll(",.*","")
         // and ignore q-factor weighting
         .replaceAll(";.*","");
      // fall back to English if they don't care
      if (language.equals("*")) language = "en";
      Locale locale = lastLocale; // keep a local locale for thread safety
      ResourceBundle resources = lastBundle;
      if (!language.equals(lastLanguage)) {
         // is it just a language code ("en")? or does it include th country ("en-NZ")?
         int dash = language.indexOf('-');
         if (dash < 0) {
            locale = new Locale(language);
         } else {
            locale = new Locale(language.substring(0, dash), language.substring(dash+1));
         }
         resources = ResourceBundle.getBundle(
            "nzilbb.labbcat.server.locale.Resources", locale);
      }

      // get the localized version of the message
      String localizedString = message;
      try {
         localizedString = resources.getString(message);
      } catch(Throwable exception) {
         log("i18n: missing resource in " + language + ": " + message);
      }

      // do we need to substitute in arguments?
      if (args.length > 0) {
         localizedString = new MessageFormat(localizedString).format(args);
      }

      lastLanguage = language;
      lastLocale = locale;
      lastBundle = resources;
      return localizedString;
   } // end of localize()
   
   /**
    * Returns the root of the persistent file system.
    * @return The "files" directory.
    */
   public File getFilesDir() {
      return new File(getServletContext().getRealPath("/files"));
   } // end of getFilesDir()
   
   /**
    * Returns the location of the annotators directory.
    * @return The annotator installation directory.
    */
   public File getAnnotatorDir() {
      File dir = new File(getFilesDir(), "annotators");
      if (!dir.exists()) dir.mkdir();
      return dir;
   } // end of getAnnotatorDir()   

   /**
    * Returns the location of the transcribers directory.
    * @return The transcriber installation directory.
    */
   public File getTranscriberDir() {
      File dir = new File(getFilesDir(), "transcribers");
      if (!dir.exists()) dir.mkdir();
      return dir;
   } // end of getTranscriberDir()   

   private static final long serialVersionUID = 1;
} // end of class LabbcatServlet
