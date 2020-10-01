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
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonWriter;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.*;
import javax.xml.xpath.*;
import nzilbb.ag.*;
import nzilbb.ag.automation.util.AnnotatorDescriptor;
import nzilbb.ag.serialize.SerializationDescriptor;
import nzilbb.labbcat.server.db.*;
import nzilbb.util.IO;
import org.w3c.dom.*;
import org.xml.sax.*;

/**
 * Controller that handles
 * <a href="https://nzilbb.github.io/ag/javadoc/nzilbb/ag/IGraphStoreQuery.html">nzilbb.ag.IGraphStoreQuery</a>
 * requests.
 * @author Robert Fromont robert@fromont.net.nz
 */
@WebServlet({"/api/store/*"} )
public class StoreQuery extends LabbcatServlet {
   
   // Attributes:

   // Methods:
   
   /**
    * Default constructor.
    */
   public StoreQuery() {
   } // end of constructor

   /**
    * GET handler
    */
   @Override
   protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
      
      JsonObject json = null;
      try {
         SqlGraphStoreAdministration store = getStore(request);
         try {
            if (title == null) {
               title = store.getSystemAttribute("title");
            }
            json = invokeFunction(request, response, store);
            if (json == null) {
               response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
               json = failureResult(request, "Invalid path: {0}", request.getPathInfo());
            }
         } finally {
            cacheStore(store);
         }
      } catch (GraphNotFoundException x) {         
         response.setStatus(HttpServletResponse.SC_NOT_FOUND);
         json = failureResult(x);
      } catch (PermissionException x) {
         response.setStatus(HttpServletResponse.SC_FORBIDDEN);
         json = failureResult(x);
      } catch (StoreException x) {
         response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
         json = failureResult(x);
      } catch (SQLException x) {
         response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
         json = failureResult(request, "Cannot connect to database: {0}", x.getMessage());
      }
      if (json != null) {
         response.setContentType("application/json");
         response.setCharacterEncoding("UTF-8");
         JsonWriter writer = Json.createWriter(response.getWriter());
         writer.writeObject(json);
         writer.close();
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
   protected JsonObject invokeFunction(HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
      JsonObject json = null;
      if (request.getPathInfo() == null || request.getPathInfo().equals("/")) {
         // no path component
         json = successResult(
            // send the version in the model for backwards compatibility with labbcat-R <= 0.4-2
            request, Json.createObjectBuilder().add("version", version).build(), null);
         // redirect /store?call=getXXX to /store/getXXX
         if (request.getMethod().equals("GET")
             && request.getParameter("call") != null
             && request.getParameter("call").length() > 0) {
            response.sendRedirect(
               request.getRequestURI()
               + "/" + request.getParameter("call")
               + "?" + request.getQueryString());
         }
      } else {
         String pathInfo = request.getPathInfo().toLowerCase(); // case-insensitive
         if (pathInfo.endsWith("getid")) {
            json = getId(request, response, store);
         } else if (pathInfo.endsWith("getschema")) {
            json = getSchema(request, response, store);
         } else if (pathInfo.endsWith("getlayerids")) {
            json = getLayerIds(request, response, store);
         } else if (pathInfo.endsWith("getlayers")) {
            json = getLayers(request, response, store);
         } else if (pathInfo.endsWith("getlayer")) {
            json = getLayer(request, response, store);
         } else if (pathInfo.endsWith("getcorpusids")) {
            json = getCorpusIds(request, response, store);
         } else if (pathInfo.endsWith("getparticipantids")) {
            json = getParticipantIds(request, response, store);
         } else if (pathInfo.endsWith("getparticipant")) {
            json = getParticipant(request, response, store);
         } else if (pathInfo.endsWith("countmatchingparticipantids")) {
            json = countMatchingParticipantIds(request, response, store);
         } else if (pathInfo.endsWith("getmatchingparticipantids")) {
            json = getMatchingParticipantIds(request, response, store);
         } else if (pathInfo.endsWith("gettranscriptids")
                    // support deprecated name
                    || pathInfo.endsWith("getgraphids")) {
            json = getTranscriptIds(request, response, store);
         } else if (pathInfo.endsWith("gettranscriptidsincorpus")
                    // support deprecated name
                    || pathInfo.endsWith("getgraphidsincorpus")) {
            json = getTranscriptIdsInCorpus(request, response, store);
         } else if (pathInfo.endsWith("gettranscriptidswithparticipant")
                    // support deprecated name
                    || pathInfo.endsWith("getgraphidswithparticipant")) {
            json = getTranscriptIdsWithParticipant(request, response, store);
         } else if (pathInfo.endsWith("countmatchingtranscriptids")
                    // support deprecated name
                    || pathInfo.endsWith("countmatchinggraphids")) {
            json = countMatchingTranscriptIds(request, response, store);
         } else if (pathInfo.endsWith("getmatchingtranscriptids")
                    // support deprecated name
                    || pathInfo.endsWith("getmatchinggraphids")) {
            json = getMatchingTranscriptIds(request, response, store);
         } else if (pathInfo.endsWith("countmatchingannotations")) {
            json = countMatchingAnnotations(request, response, store);
         } else if (pathInfo.endsWith("getmatchingannotations")) {
            json = getMatchingAnnotations(request, response, store);
         } else if (pathInfo.endsWith("countannotations")) {
            json = countAnnotations(request, response, store);
         } else if (pathInfo.endsWith("getannotations")) {
            json = getAnnotations(request, response, store);
         } else if (pathInfo.endsWith("getanchors")) {
            json = getAnchors(request, response, store);
         } else if (pathInfo.endsWith("getmediatracks")) {
            json = getMediaTracks(request, response, store);
         } else if (pathInfo.endsWith("getavailablemedia")) {
            json = getAvailableMedia(request, response, store);
         } else if (pathInfo.endsWith("gettranscript")
                    // support deprecated name
                    || pathInfo.endsWith("getgraph")) {
            json = getTranscript(request, response, store);
         } else if (pathInfo.endsWith("getmedia")) {
            json = getMedia(request, response, store);
         } else if (pathInfo.endsWith("getepisodedocuments")) {
            json = getEpisodeDocuments(request, response, store);
         } else if (pathInfo.endsWith("getserializerdescriptors")) {
            json = getSerializerDescriptors(request, response, store);
         } else if (pathInfo.endsWith("getdeserializerdescriptors")) {
            json = getDeserializerDescriptors(request, response, store);
         } else if (pathInfo.endsWith("getannotatordescriptors")) {
            json = getAnnotatorDescriptors(request, response, store);
         }
      }
      return json;
   } // end of invokeFunction()
   
   // IGraphStoreQuery method handlers

   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getId()}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject getId(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException {
      return successResult(request, store.getId(), null);
   }      
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getLayerIds()}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject getLayerIds(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException {
      
      return successResult(request, store.getLayerIds(), null);
   }      
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getLayers()}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject getLayers(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException {
      
      Layer[] layers = store.getLayers();
      // unset children so that JSON serialization doesn't double-up layers
      for (Layer layer : layers) layer.setChildren(null);
     return successResult(request, layers, null);
   }      
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getSchema()}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject getSchema(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException {
      
      return successResult(request, store.getSchema(), null);
   }      
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getLayer(String)}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject getLayer(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException {
      
      String id = request.getParameter("id");
      if (id == null) return failureResult(request, "No ID specified.");
      return successResult(request, store.getLayer(id), null);
   }      
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getCorpusIds()}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject getCorpusIds(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException {
      
      String[] ids = store.getCorpusIds();
      return successResult(request, ids, ids.length == 0?"There are no corpora.":null);
   }      
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getParticipantIds()}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject getParticipantIds(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException {
      
      String[] ids = store.getParticipantIds();
      return successResult(request, ids, ids.length == 0?"There are no participants.":null);
   }
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getParticipant(String)}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject getParticipant(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException {
      
      String id = request.getParameter("id");
      if (id == null) return failureResult(request, "No ID specified.");
      Annotation participant = store.getParticipant(id);
      if (participant == null)
      {
         response.setStatus(HttpServletResponse.SC_NOT_FOUND);
         return failureResult(request, "Participant not found: {0}", id);
      }
      return successResult(request, participant, null);
   }      
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#countMatchingParticipantIds(String)}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject countMatchingParticipantIds(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException {
      
      String expression = request.getParameter("expression");
      if (expression == null) return failureResult(request, "No expression specified.");
      return successResult(request, store.countMatchingParticipantIds(expression), null);
   }      
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getMatchingParticipantIds(String)}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject getMatchingParticipantIds(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException {
      
      Vector<String> errors = new Vector<String>();
      String expression = request.getParameter("expression");
      if (expression == null) errors.add("No expression specified.");
      Integer pageLength = null;
      if (request.getParameter("pageLength") != null) {
         try {
            pageLength = Integer.valueOf(request.getParameter("pageLength"));
         } catch(NumberFormatException x) {
            errors.add("Invalid page length: " + x.getMessage());
         }
      }
      Integer pageNumber = null;
      if (request.getParameter("pageNumber") != null) {
         try {
            pageNumber = Integer.valueOf(request.getParameter("pageNumber"));
         } catch(NumberFormatException x) {
            errors.add("Invalid page number: " + x.getMessage());
         }
      }
      if (errors.size() > 0) return failureResult(errors);
      String[] ids = store.getMatchingParticipantIds(expression, pageLength, pageNumber);
      return successResult(request, ids, ids.length == 0?"There are no matching IDs.":null);
   }         
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getTranscriptIds()}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject getTranscriptIds(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException {
      
      return successResult(request, store.getTranscriptIds(), null);
   }      
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getTranscriptIdsInCorpus(String)}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject getTranscriptIdsInCorpus(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException {
      
      String id = request.getParameter("id");
      if (id == null) return failureResult(request, "No ID specified.");
      String[] ids = store.getTranscriptIdsInCorpus(id);
      return successResult(request, ids, ids.length == 0?"There are no matching IDs.":null);
   }      
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getTranscriptIdsWithParticipant(String)}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject getTranscriptIdsWithParticipant(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException {
      
      String id = request.getParameter("id");
      if (id == null) return failureResult(request, "No ID specified.");
      String[] ids = store.getTranscriptIdsWithParticipant(id);
      return successResult(request, ids, ids.length == 0?"There are no matching IDs.":null);
   }      
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#countMatchingTranscriptIds(String)}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject countMatchingTranscriptIds(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException {
      
      String expression = request.getParameter("expression");
      if (expression == null) return failureResult(request, "No expression specified.");
      return successResult(request, store.countMatchingTranscriptIds(expression), null);
   }      
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getMatchingTranscriptIds(String)}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject getMatchingTranscriptIds(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException {

      Vector<String> errors = new Vector<String>();
      String expression = request.getParameter("expression");
      if (expression == null) errors.add(localize(request, "No expression specified."));
      Integer pageLength = null;
      if (request.getParameter("pageLength") != null) {
         try {
            pageLength = Integer.valueOf(request.getParameter("pageLength"));
         } catch(NumberFormatException x) {
            errors.add(localize(request, "Invalid page length: " + x.getMessage()));
         }
      }
      Integer pageNumber = null;
      if (request.getParameter("pageNumber") != null) {
         try {
            pageNumber = Integer.valueOf(request.getParameter("pageNumber"));
         } catch(NumberFormatException x) {
            errors.add(localize(request, "Invalid page number: " + x.getMessage()));
         }
      }
      if (errors.size() > 0) return failureResult(errors);
      String[] ids = store.getMatchingTranscriptIds(expression, pageLength, pageNumber);
      return successResult(request, ids, ids.length == 0?"There are no matching IDs.":null);
   }         
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#countMatchingAnnotations(String)}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject countMatchingAnnotations(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException {
      
      String expression = request.getParameter("expression");
      if (expression == null) return failureResult(request, "No expression specified.");
      return successResult(request, store.countMatchingAnnotations(expression), null);
   }      
   
   /**
    * Implementation of
    * {@link nzilbb.ag.IGraphStoreQuery#getMatchingAnnotations(String,Integer,Integer)}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject getMatchingAnnotations(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException {
      
      Vector<String> errors = new Vector<String>();
      String expression = request.getParameter("expression");
      if (expression == null) errors.add(localize(request, "No expression specified."));
      Integer pageLength = null;
      if (request.getParameter("pageLength") != null) {
         try {
            pageLength = Integer.valueOf(request.getParameter("pageLength"));
         } catch(NumberFormatException x) {
            errors.add(localize(request, "Invalid page length: {0}", x.getMessage()));
         }
      }
      Integer pageNumber = null;
      if (request.getParameter("pageNumber") != null) {
         try {
            pageNumber = Integer.valueOf(request.getParameter("pageNumber"));
         } catch(NumberFormatException x) {
            errors.add(localize(request, "Invalid page number: {0}", x.getMessage()));
         }
      }
      if (errors.size() > 0) return failureResult(errors);
      Annotation[] annotations = store.getMatchingAnnotations(expression, pageLength, pageNumber);
      return successResult(request, annotations, annotations.length == 0?"There are no annotations.":null);
   }         

   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#countAnnotations(String,String)}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject countAnnotations(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
      
      Vector<String> errors = new Vector<String>();
      String id = request.getParameter("id");
      if (id == null) errors.add(localize(request, "No ID specified."));
      String layerId = request.getParameter("layerId");
      if (layerId == null) errors.add(localize(request, "No layer ID specified."));
      if (errors.size() > 0) return failureResult(errors);
      return successResult(request, store.countAnnotations(id, layerId), null);
   }
   
   /**
    * Implementation of
    * {@link nzilbb.ag.IGraphStoreQuery#getAnnotations(String,String,Integer,Integer)}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject getAnnotations(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
      
      Vector<String> errors = new Vector<String>();
      String id = request.getParameter("id");
      if (id == null) errors.add("No ID specified.");
      String layerId = request.getParameter("layerId");
      if (layerId == null) errors.add(localize(request, "No layer ID specified."));
      Integer pageLength = null;
      if (request.getParameter("pageLength") != null) {
         try {
            pageLength = Integer.valueOf(request.getParameter("pageLength"));
         } catch(NumberFormatException x) {
            errors.add(localize(request, "Invalid page length: {0}", x.getMessage()));
         }
      }
      Integer pageNumber = null;
      if (request.getParameter("pageNumber") != null) {
         try {
            pageNumber = Integer.valueOf(request.getParameter("pageNumber"));
         } catch(NumberFormatException x) {
            errors.add(localize(request, "Invalid page number: {0}", x.getMessage()));
         }
      }
      if (errors.size() > 0) return failureResult(errors);
      Annotation[] annotations = store.getAnnotations(id, layerId, pageLength, pageNumber);
      return successResult(request, annotations, annotations.length == 0?"There are no annotations.":null);
   }

   // TODO getMatchAnnotations
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getAnchors(String,String[])}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject getAnchors(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
      
      Vector<String> errors = new Vector<String>();
      String id = request.getParameter("id");
      if (id == null) errors.add(localize(request, "No ID specified."));
      String[] anchorIds = request.getParameterValues("anchorIds");
      if (anchorIds == null) errors.add(localize(request, "No anchor IDs specified."));
      if (errors.size() > 0) return failureResult(errors);
      return successResult(request, store.getAnchors(id, anchorIds), null);
   }
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getTranscript(String,String[])}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject getTranscript(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
      
      Vector<String> errors = new Vector<String>();
      String id = request.getParameter("id");
      if (id == null) errors.add(localize(request, "No ID specified."));
      String[] layerIds = request.getParameterValues("layerIds");
      if (layerIds == null) layerIds = new String[0];
      if (errors.size() > 0) return failureResult(errors);
      return successResult(request, store.getTranscript(id, layerIds), null);
   }

   // TODO getFragment
   // TODO getFragmentSeries
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getMediaTracks()}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject getMediaTracks(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException {
      
      return successResult(request, store.getMediaTracks(), null);
   }      
   
   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getAvailableMedia(String)}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject getAvailableMedia(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
      
      String id = request.getParameter("id");
      if (id == null) return failureResult(request, "No ID specified.");

      MediaFile[] media = store.getAvailableMedia(id);
      
      // strip out local file paths
      for (MediaFile file : media) file.setFile(null);
      
      return successResult(request, media, media.length == 0?"There is no media.":null);
   }

   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getAvailableMedia(String)}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject getMedia(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
      
      Vector<String> errors = new Vector<String>();
      String id = request.getParameter("id");
      if (id == null) errors.add(localize(request, "No ID specified."));
      String trackSuffix = request.getParameter("trackSuffix"); // optional
      String mimeType = request.getParameter("mimeType");
      if (mimeType == null) errors.add(localize(request, "No Content Type specified."));
      Double startOffset = null;
      if (request.getParameter("startOffset") != null) {
         try {
            startOffset = Double.valueOf(request.getParameter("startOffset"));
         } catch(NumberFormatException x) {
            errors.add(localize(request, "Invalid start offset: {0}", x.getMessage()));
         }
      }
      Double endOffset = null;
      if (request.getParameter("endOffset") != null) {
         try {
            endOffset = Double.valueOf(request.getParameter("endOffset"));
         } catch(NumberFormatException x) {
            errors.add(localize(request, "Invalid end offset: {0}", x.getMessage()));
         }
      }
      if (startOffset == null && endOffset == null) {
         if (errors.size() > 0) return failureResult(errors);
         return successResult(request, store.getMedia(id, trackSuffix, mimeType), null);
      } else {
         if (startOffset == null) errors.add(localize(request, "Start offset not specified."));
         if (endOffset == null) errors.add(localize(request, "End offset not specified."));
         if (endOffset <= startOffset)
            errors.add(localize(request, "Start offset ({0,number,#.###}) must be before end offset ({1,number,#.###})", startOffset, endOffset));
         if (errors.size() > 0) return failureResult(errors);
         return successResult(request, store.getMedia(id, trackSuffix, mimeType, startOffset, endOffset), null);
      }
   }

   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getEpisodeDocuments(String)}
    * @param request The HTTP request.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject getEpisodeDocuments(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
      
      String id = request.getParameter("id");
      if (id == null) return failureResult(request, "No ID specified.");

      MediaFile[] media = store.getEpisodeDocuments(id);
      
      // strip out local file paths
      for (MediaFile file : media) file.setFile(null);
      
      return successResult(request, media, media.length == 0?"There are no documents.":null);
   }

   /**
    * Lists the descriptors of all registered serializers.
    * <p> Serializers are modules that export annotation structures as a specific file
    * format, e.g. Praat TextGrid, plain text, etc., so the mimeType of descriptors reflects what 
    * <var>mimeType</var>s can be specified for {@link SerializeGraphs}.
    * @return A list of the descriptors of all registered serializers.
    * @throws StoreException If an error prevents the operation from completing.
    * @throws PermissionException If the operation is not permitted.
    */
   protected JsonObject getSerializerDescriptors(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
      SerializationDescriptor[] descriptors = store.getSerializerDescriptors();
      return successResult(
         request, descriptors, descriptors.length == 0?"There are no serializers.":null);
   }
   
   /**
    * Lists the descriptors of all registered deserializers.
    * <p> Deserializers are modules that import annotation structures from a specific file
    * format, e.g. Praat TextGrid, plain text, etc.
    * @return A list of the descriptors of all registered deserializers.
    * @throws StoreException If an error prevents the descriptors from being listed.
    * @throws PermissionException If listing the deserializers is not permitted.
    */
   protected JsonObject getDeserializerDescriptors(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
      SerializationDescriptor[] descriptors = store.getDeserializerDescriptors();
      return successResult(
         request, descriptors, descriptors.length == 0?"There are no deserializers.":null);
   }

   /**
    * Lists descriptors of all annotators that are installed.
    * <p> Annotators are modules that perform automated annations of existing transcripts.
    * @return A list of the descriptors of all registered annotators.
    * @throws StoreException If an error prevents the descriptors from being listed.
    * @throws PermissionException If listing the deserializers is not permitted.
    */
   protected JsonObject getAnnotatorDescriptors(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
      AnnotatorDescriptor[] descriptors = store.getAnnotatorDescriptors();
      return successResult(
         request, descriptors, descriptors.length == 0?"There are no annotators.":null);
   }

   private static final long serialVersionUID = 1;
} // end of class StoreQuery
