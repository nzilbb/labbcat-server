//
// Copyright 2020-2025 New Zealand Institute of Language, Brain and Behaviour, 
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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
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
import javax.xml.parsers.*;
import javax.xml.xpath.*;
import nzilbb.ag.*;
import nzilbb.labbcat.server.db.*;
import nzilbb.sql.ConnectionFactory;
import nzilbb.sql.mysql.MySQLConnectionFactory;
import nzilbb.util.CloneableBean;
import nzilbb.util.IO;
import nzilbb.util.SemanticVersionComparator;
import org.w3c.dom.*;
import org.xml.sax.*;

/**
 * Base class for request enpoints, which implents common functionality.
 * @author Robert Fromont robert@fromont.net.nz
 */
public class APIRequestHandler {
  
  // Attributes:   
  
  protected String driverName;
  protected String connectionURL;
  protected String connectionName;
  protected String connectionPassword;
  protected ConnectionFactory connectionFactory;
  
  protected String title;
  protected String version;

  protected ResourceBundle resources;
  
  // Methods:
  
  /**
   * Default constructor.
   */
  public APIRequestHandler() {
    // load a default set of localization resources
    resources = ResourceBundle.getBundle(
      "nzilbb.labbcat.server.locale.Resources", Locale.UK);
    title = getClass().getSimpleName();
  } // end of constructor
  
  /** 
   * Initialise the endpoint data.
   */
  public void init(String title, String version, ConnectionFactory connectionFactory, ResourceBundle resources) {
    this.title = title;
    this.version = version;
    this.connectionFactory = connectionFactory;
    if (resources != null) {
      this.resources = resources;
    }
  }

  /**
   * Creates a new database connection object
   * @return A connected connection object
   * @throws Exception
   */
  protected Connection newConnection()
    throws SQLException { 
    return connectionFactory.newConnection();
  } // end of newDatabaseConnection()   
     
  /**
   * Gets the value of the given system attribute.
   * @param name
   * @param db
   * @return Value for the given system attribute, or null if it's not set.
   * @throws SQLException
   */
  public static String GetSystemAttribute(String name, Connection db) throws SQLException {
    PreparedStatement sql = db.prepareStatement(
      "SELECT value FROM system_attribute WHERE name = ?");
    sql.setString(1, name);
    ResultSet rs = sql.executeQuery();
    try {
      if (rs.next()) {
        return rs.getString(1);
      }
      return null;
    } finally {
      rs.close();
      sql.close();
    }
  } // end of getSystemAttribute()
  
  /**
   * Creates a JSON object representing a success result, with the given model.
   * @param result The result object.
   * @param message An optional message to include in the response envelope.
   * @param args Arguments to be substituted into the message, if any
   * @return An object for returning as the request result.
   */
  @SuppressWarnings("unchecked")
  protected JsonObject successResult(
    Object result, String message, Object... args) {
    
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
        .add(localize(message, args)));
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
  protected JsonObject failureResult(String message, Object... args) {
    
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
               localize(message, args)));
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
    JsonGenerator writer, String message, Object... args) {
    writer.writeEnd(); // end whatever we started in startResult
    writer.writeStartArray("messages");
    if (message != null) writer.write(localize(message, args));
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
    JsonGenerator writer, String message, Object... args) {
    writer.writeStartArray("errors");
    if (message != null) writer.write(localize(message, args));
    writer.writeEnd(); // array
    writer.write("code", 1); // TODO deprecate?
    writer.writeStartArray("messages").writeEnd(); // array
    writer.writeEnd(); // obect started in startResult
    writer.flush();
    return writer;
  } // end of endFailureResult()
  
  /**
   * Escapes quotes in the given string for inclusion in QL or SQL queries.
   * @param s The string to escape.
   * @return The given string, with quotes escapeed.
   */
  protected String esc(String s) {
    if (s == null) return "";
    return s.replace("\\","\\\\").replace("'","\\'");
  } // end of esc()
    
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
  protected String localize(String message, Object... args) {

    // get the localized version of the message
    String localizedString = message;
    try {
      localizedString = resources.getString(message);
    } catch(Throwable exception) {
      System.err.println("i18n: missing resource in " + resources.getLocale() + ": " + message);
    }

    // do we need to substitute in arguments?
    if (args.length > 0) {
      localizedString = new MessageFormat(localizedString).format(args);
    }
    
    return localizedString;
  } // end of localize()
  
} // end of class APIRequestHandler
