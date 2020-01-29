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
import org.json.*;
import org.w3c.dom.*;
import org.xml.sax.*;

/**
 * Controller that handles {@link nzilbb.ag.IGraphStoreQuery} requests.
 * @author Robert Fromont robert@fromont.net.nz
 */
@WebServlet("/api/store/*")
public class StoreQuery
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
   public StoreQuery()
   {
   } // end of constructor

   /** 
    * Initialise the servlet by loading the database connection settings.
    */
   public void init()
   {
      try
      {
         log("StoreQuery.init...");
         
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

            // ensure it's registered with the driver manager
            Class.forName(driverName).getConstructor().newInstance();
         } // get database connection configuration from context.xml
         else
         {
            log("Configuration file not found: " + contextXml.getPath());
         }
      }
      catch (Exception x)
      {
         log("StoreQuery.init() failed", x);
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
                  baseUrl(request), connection, request.getRemoteUser());

               if (title == null)
               {
                  title = store.getSystemAttribute("title");
               }
            }
            try
            {
               json = invokeFunction(request, response, store);
               if (json == null)
               {
                  response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                  json = failureResult("Invalid path: " + request.getPathInfo());
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
      catch (GraphNotFoundException x)
      {         
         response.setStatus(HttpServletResponse.SC_NOT_FOUND);
         json = failureResult(x);
      }
      catch (PermissionException x)
      {
         response.setStatus(HttpServletResponse.SC_FORBIDDEN);
         json = failureResult(x);
      }
      catch (StoreException x)
      {
         response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
         json = failureResult(x);
      }
      catch (SQLException x)
      {
         response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
         json = failureResult("Cannot connect to database." + x.getMessage());
      }
      if (json != null)
      {
         response.setContentType("application/json");
         response.setCharacterEncoding("UTF-8");
         json.write(response.getWriter());
         response.getWriter().flush();
      }
   }
   
   /**
    * Interprets the URL path, and executes the corresponding function on the store. This
    * method should be overridden by subclasses to interpret their own functions.
    * @param request The request.
    * @param response The response.
    * @param store The connected graph store.
    * @return The response to send to the caller, or null if the request could not be interpreted.
    */
   protected JSONObject invokeFunction(HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException
   {
      JSONObject json = null;
      String pathInfo = request.getPathInfo().toLowerCase(); // case-insensitive
      if (pathInfo.endsWith("getid"))
      {
         json = getId(request, response, store);
      }
      else if (pathInfo.endsWith("getschema"))
      {
         json = getSchema(request, response, store);
      }
      else if (pathInfo.endsWith("getlayerids"))
      {
         json = getLayerIds(request, response, store);
      }
      else if (pathInfo.endsWith("getlayers"))
      {
         json = getLayers(request, response, store);
      }
      else if (pathInfo.endsWith("getlayer"))
      {
         json = getLayer(request, response, store);
      }
      else if (pathInfo.endsWith("getcorpusids"))
      {
         json = getCorpusIds(request, response, store);
      }
      else if (pathInfo.endsWith("getparticipantids"))
      {
         json = getParticipantIds(request, response, store);
      }
      else if (pathInfo.endsWith("getparticipant"))
      {
         json = getParticipant(request, response, store);
      }
      else if (pathInfo.endsWith("countmatchingparticipantids"))
      {
         json = countMatchingParticipantIds(request, response, store);
      }
      else if (pathInfo.endsWith("getmatchingparticipantids"))
      {
         json = getMatchingParticipantIds(request, response, store);
      }
      else if (pathInfo.endsWith("getgraphids"))
      {
         json = getGraphIds(request, response, store);
      }
      else if (pathInfo.endsWith("getgraphidsincorpus"))
      {
         json = getGraphIdsInCorpus(request, response, store);
      }
      else if (pathInfo.endsWith("getgraphidswithparticipant"))
      {
         json = getGraphIdsWithParticipant(request, response, store);
      }
      else if (pathInfo.endsWith("countmatchinggraphids"))
      {
         json = countMatchingGraphIds(request, response, store);
      }
      else if (pathInfo.endsWith("getmatchinggraphids"))
      {
         json = getMatchingGraphIds(request, response, store);
      }
      else if (pathInfo.endsWith("countannotations"))
      {
         json = countAnnotations(request, response, store);
      }
      else if (pathInfo.endsWith("getannotations"))
      {
         json = getAnnotations(request, response, store);
      }
      else if (pathInfo.endsWith("getanchors"))
      {
         json = getAnchors(request, response, store);
      }
      else if (pathInfo.endsWith("getmediatracks"))
      {
         json = getMediaTracks(request, response, store);
      }
      else if (pathInfo.endsWith("getavailablemedia"))
      {
         json = getAvailableMedia(request, response, store);
      }
      else if (pathInfo.endsWith("getgraph"))
      {
         json = getGraph(request, response, store);
      }
      return json;
   } // end of invokeFunction()

   /**
    * Creates a new database connection object
    * @return A connected connection object
    * @throws Exception
    */
   protected Connection newConnection()
      throws SQLException
   { 
      return DriverManager.getConnection(connectionURL, connectionName, connectionPassword);
   } // end of newDatabaseConnection()
   
   /**
    * Determine the baseUrl for the server.
    * @param request The request.
    * @return The baseUrl.
    */
   protected String baseUrl(HttpServletRequest request)
   {
      if (request.getSession() != null && request.getSession().getAttribute("baseUrl") != null)
      { // get it from the session
         return request.getSession().getAttribute("baseUrl").toString();
      }
      else if (getServletContext().getInitParameter("baseUrl") != null
               && getServletContext().getInitParameter("baseUrl").length() > 0)
      { // get it from the webapp configuration
         return getServletContext().getInitParameter("baseUrl");
      }
      else
      { // infer it from the request itself
         try
         {
            URL url = new URL(request.getRequestURL().toString());
            return url.getProtocol() + "://"
               + url.getHost() + (url.getPort() < 0?"":":"+url.getPort())
               + ("/".equals(
                     getServletContext().getContextPath())?""
                  :getServletContext().getContextPath());
         }
         catch(MalformedURLException exception)
         {
            return request.getRequestURI().replaceAll("/api/store/.*","");
         }
      }

   } // end of baseUrl()
   
   /**
    * Creates a JSON object representing a success result, with the given model.
    * @param result The result object.
    * @param message An optional message to include in the response envelope.
    * @return An object for returning as the request result.
    */
   protected JSONObject successResult(Object result, String message)
   {
      JSONObject response = new JSONObject();
      response.put("title", title);
      response.put("code", 0); // TODO deprecate?
      response.put("errors", new JSONArray());
      if (message == null)
      {
         response.put("messages", new JSONArray());
      }
      else
      {
         response.append("messages", message);
      }
      if (result != null)
      {
         if (result instanceof IJSONableBean)
         {
            response.put("model", new JSONObject((IJSONableBean)result));
         }
         else
         {
            response.put("model", result);
         }
      }
      else
      {
         response.put("model", JSONObject.NULL);
      }
      return response;
   } // end of successResult()

   /**
    * Creates a JSON object representing a failure result.
    * @param messages The error messages to return.
    * @return An object for returning as the request result.
    */
   protected JSONObject failureResult(Collection<String> messages)
   {
      JSONObject result = new JSONObject();
      result.put("title", title);
      result.put("code", 1); // TODO deprecate?
      if (messages == null)
      {
         result.put("errors", new JSONArray());
      }
      else
      {
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
   protected JSONObject failureResult(String message)
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
      result.put("model", JSONObject.NULL);
      return result;
   } // end of failureResult()

   /**
    * Creates a JSON object representing a failure result.
    * @param message The error message to return.
    * @return An object for returning as the request result.
    */
   protected JSONObject failureResult(Throwable t)
   {
      JSONObject result = new JSONObject();
      result.put("title", title);
      result.put("code", 1); // TODO deprecate?
      result.append("errors", t.getMessage());
      result.put("exception", new JSONObject()
                 .put("type", t.getClass().getSimpleName())
                 .put("message", t.getMessage()));
      result.put("messages", new JSONArray());
      result.put("model", JSONObject.NULL);
      return result;
   } // end of failureResult()

   // IGraphStoreQuery method handlers

   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getId()}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JSONObject getId(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException
   {
      return successResult(store.getId(), null);
   }      
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getLayerIds()}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JSONObject getLayerIds(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException
   {
      return successResult(store.getLayerIds(), null);
   }      
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getLayers()}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JSONObject getLayers(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException
   {
      Layer[] layers = store.getLayers();
      // unset children so that JSON serialization doesn't double-up layers
      for (Layer layer : layers) layer.setChildren(null);
     return successResult(layers, null);
   }      
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getSchema()}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JSONObject getSchema(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException
   {
      return successResult(store.getSchema(), null);
   }      
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getLayer(String)}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JSONObject getLayer(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException
   {
      String id = request.getParameter("id");
      if (id == null) return failureResult("No id specified.");
      return successResult(store.getLayer(id), null);
   }      
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getCorpusIds()}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JSONObject getCorpusIds(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException
   {
      return successResult(store.getCorpusIds(), null);
   }      
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getParticipantIds()}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JSONObject getParticipantIds(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException
   {
      return successResult(store.getParticipantIds(), null);
   }
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getParticipant(String)}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JSONObject getParticipant(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException
   {
      String id = request.getParameter("id");
      if (id == null) return failureResult("No id specified.");
      Annotation participant = store.getParticipant(id);
      if (participant == null)
      {
         response.setStatus(HttpServletResponse.SC_NOT_FOUND);
         return failureResult("Participant not found: " + id);
      }
      return successResult(participant, null);
   }      
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#countMatchingParticipantIds(String)}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JSONObject countMatchingParticipantIds(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException
   {
      String expression = request.getParameter("expression");
      if (expression == null) return failureResult("No expression specified.");
      return successResult(store.countMatchingParticipantIds(expression), null);
   }      
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getMatchingParticipantIds(String)}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JSONObject getMatchingParticipantIds(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException
   {
      Vector<String> errors = new Vector<String>();
      String expression = request.getParameter("expression");
      if (expression == null) errors.add("No expression specified.");
      Integer pageLength = null;
      if (request.getParameter("pageLength") != null)
      {
         try
         {
            pageLength = Integer.valueOf(request.getParameter("pageLength"));
         }
         catch(NumberFormatException x)
         {
            errors.add("Invalid pageLength: " + x.getMessage());
         }
      }
      Integer pageNumber = null;
      if (request.getParameter("pageNumber") != null)
      {
         try
         {
            pageNumber = Integer.valueOf(request.getParameter("pageNumber"));
         }
         catch(NumberFormatException x)
         {
            errors.add("Invalid pageNumber: " + x.getMessage());
         }
      }
      if (errors.size() > 0) return failureResult(errors);
      return successResult(
         store.getMatchingParticipantIds(expression, pageLength, pageNumber), null);
   }         
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getGraphIds()}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JSONObject getGraphIds(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException
   {
      return successResult(store.getGraphIds(), null);
   }      
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getGraphIdsInCorpus(String)}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JSONObject getGraphIdsInCorpus(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException
   {
      String id = request.getParameter("id");
      if (id == null) return failureResult("No id specified.");
      return successResult(store.getGraphIdsInCorpus(id), null);
   }      
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getGraphIdsWithParticipant(String)}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JSONObject getGraphIdsWithParticipant(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException
   {
      String id = request.getParameter("id");
      if (id == null) return failureResult("No id specified.");
      return successResult(store.getGraphIdsWithParticipant(id), null);
   }      
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#countMatchingGraphIds(String)}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JSONObject countMatchingGraphIds(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException
   {
      String expression = request.getParameter("expression");
      if (expression == null) return failureResult("No expression specified.");
      return successResult(store.countMatchingGraphIds(expression), null);
   }      
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getMatchingGraphIds(String)}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JSONObject getMatchingGraphIds(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException
   {
      Vector<String> errors = new Vector<String>();
      String expression = request.getParameter("expression");
      if (expression == null) errors.add("No expression specified.");
      Integer pageLength = null;
      if (request.getParameter("pageLength") != null)
      {
         try
         {
            pageLength = Integer.valueOf(request.getParameter("pageLength"));
         }
         catch(NumberFormatException x)
         {
            errors.add("Invalid pageLength: " + x.getMessage());
         }
      }
      Integer pageNumber = null;
      if (request.getParameter("pageNumber") != null)
      {
         try
         {
            pageNumber = Integer.valueOf(request.getParameter("pageNumber"));
         }
         catch(NumberFormatException x)
         {
            errors.add("Invalid pageNumber: " + x.getMessage());
         }
      }
      if (errors.size() > 0) return failureResult(errors);
      return successResult(
         store.getMatchingGraphIds(expression, pageLength, pageNumber), null);
   }         
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#countAnnotations(String,String)}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JSONObject countAnnotations(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException
   {
      Vector<String> errors = new Vector<String>();
      String id = request.getParameter("id");
      if (id == null) errors.add("No id specified.");
      String layerId = request.getParameter("layerId");
      if (layerId == null) errors.add("No layerId specified.");
      if (errors.size() > 0) return failureResult(errors);
      return successResult(store.countAnnotations(id, layerId), null);
   }
   
   /**
    * Implementation of
    * {@link nzilbb.ag.IGraphStoreQuery#getAnnotations(String,String,Integer,Integer)}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JSONObject getAnnotations(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException
   {
      Vector<String> errors = new Vector<String>();
      String id = request.getParameter("id");
      if (id == null) errors.add("No id specified.");
      String layerId = request.getParameter("layerId");
      if (layerId == null) errors.add("No layerId specified.");
      Integer pageLength = null;
      if (request.getParameter("pageLength") != null)
      {
         try
         {
            pageLength = Integer.valueOf(request.getParameter("pageLength"));
         }
         catch(NumberFormatException x)
         {
            errors.add("Invalid pageLength: " + x.getMessage());
         }
      }
      Integer pageNumber = null;
      if (request.getParameter("pageNumber") != null)
      {
         try
         {
            pageNumber = Integer.valueOf(request.getParameter("pageNumber"));
         }
         catch(NumberFormatException x)
         {
            errors.add("Invalid pageNumber: " + x.getMessage());
         }
      }
      if (errors.size() > 0) return failureResult(errors);
      return successResult(store.getAnnotations(id, layerId, pageLength, pageNumber), null);
   }
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getAnchors(String,String[])}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JSONObject getAnchors(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException
   {
      Vector<String> errors = new Vector<String>();
      String id = request.getParameter("id");
      if (id == null) errors.add("No id specified.");
      String[] anchorIds = request.getParameterValues("anchorIds");
      if (anchorIds == null) errors.add("No anchorIds specified.");
      if (errors.size() > 0) return failureResult(errors);
      return successResult(store.getAnchors(id, anchorIds), null);
   }
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getMediaTracks()}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JSONObject getMediaTracks(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException
   {
      return successResult(store.getMediaTracks(), null);
   }      
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getAvailableMedia(String)}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JSONObject getAvailableMedia(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException
   {
      String id = request.getParameter("id");
      if (id == null) return failureResult("No id specified.");

      MediaFile[] media = store.getAvailableMedia(id);
      
      // strip out local file paths
      for (MediaFile file : media) file.setFile(null);
      
      return successResult(media, null);
   }      
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getGraph(String,String[])}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JSONObject getGraph(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException
   {
      Vector<String> errors = new Vector<String>();
      String id = request.getParameter("id");
      if (id == null) errors.add("No id specified.");
      String[] layerIds = request.getParameterValues("layerIds");
      if (layerIds == null) layerIds = new String[0];
      if (errors.size() > 0) return failureResult(errors);
      return successResult(store.getGraph(id, layerIds), null);
   }      
   
   private static final long serialVersionUID = 1;
} // end of class StoreQuery
