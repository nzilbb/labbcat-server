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
 * Controller that handles
 * <a href="https://nzilbb.github.io/ag/javadoc/nzilbb/ag/IGraphStoreAdministration.html">nzilbb.ag.IGraphStoreAdministration</a>
 * requests. This includes all requests supported by {@link StoreQuery} and {@link Store}.
 * @author Robert Fromont robert@fromont.net.nz
 */
@WebServlet({"/admin/store/*", "/api/admin/store/*"})
public class StoreAdministration extends Store {
   // Attributes:

   // Methods:
   
   /**
    * Default constructor.
    */
   public StoreAdministration() {
   } // end of constructor

   /** 
    * Initialise the servlet
    */
   public void init() {
      super.init();
   }

   // StoreQuery overrides

   /**
    * Interprets the URL path, and executes the corresponding function on the store. This
    * method is an override of 
    * {@link StoreQuery#invokeFunction(HttpServletRequest,HttpServletResponse,SqlGraphStoreAdministration)}.
    * <p> This implementation only allows POST or PUT HTTP requests.
    * @param request The request.
    * @param response The response.
    * @param store The connected graph store.
    * @return The response to send to the caller, or null if the request could not be interpreted.
    */
   @Override
   protected JSONObject invokeFunction(HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
      try {
         if (!isUserInRole("admin", request, store.getConnection())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return failureResult(request, "User has no admin permission.");         
         }
      } catch(SQLException x) {
         response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
         return failureResult(x);
      }
      JSONObject json = null;
      String pathInfo = request.getPathInfo().toLowerCase(); // case-insensitive
      // only allow POST requests
      if (request.getMethod().equals("POST") || request.getMethod().equals("PUT")) {
         if (pathInfo.endsWith("savelayer"))
         {
            json = saveLayer(request, response, store);
         }
         // TODO
         // if (pathInfo.endsWith("createannotation"))
         // {
         //    json = createAnnotation(request, response, store);
         // }
         // else if (pathInfo.endsWith("destroyannotation"))
         // {
         //    json = destroyAnnotation(request, response, store);
         // }
         // else if (pathInfo.endsWith("deletegraph"))
         // {
         //    json = deleteGraph(request, response, store);
         // }
      } // only if it's a POST request
      
      if (json == null) { // either not POST or not a recognized function
         json = super.invokeFunction(request, response, store);
      }
      return json;
   } // end of invokeFunction()

   // IGraphStoreAdministration method handlers
   
   // TODO registerDeserializer
   // TODO deregisterDeserializer
   // TODO getDeserializerDescriptors
   // TODO deserializerForMimeType
   // TODO deserializerForFilesSuffix
   // TODO registerSerializer
   // TODO deregisterSerializer
   // TODO getSerializerDescriptors
   // TODO serializerForMimeType
   // TODO serializerForFilesSuffix

   /**
    * Implementation of {@link nzilbb.ag.IGraphStoreQuery#saveLayer(Layer)}
    * @param request The HTTP request, the body of which must be a JSON-encoded {@link Layer}.
    * @param request The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JSONObject saveLayer(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
      Vector<String> errors = new Vector<String>();
      JSONTokener reader = new JSONTokener(request.getReader());
      JSONObject json = new JSONObject(reader);
      Layer layer = new Layer(json);
      if (layer.getId() == null) errors.add(localize(request, "No ID specified."));
      if (errors.size() > 0) return failureResult(errors);
      return successResult(
         request, store.saveLayer(layer), null);
   }      
   

   private static final long serialVersionUID = 1;
} // end of class StoreAdministration
