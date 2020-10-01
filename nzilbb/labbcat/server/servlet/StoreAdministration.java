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
import javax.json.JsonReader;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.*;
import javax.xml.xpath.*;
import nzilbb.ag.*;
import nzilbb.labbcat.server.db.*;
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
   protected JsonObject invokeFunction(HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
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
      JsonObject json = null;
      String pathInfo = request.getPathInfo().toLowerCase(); // case-insensitive
      // only allow POST requests
      if (request.getMethod().equals("POST") || request.getMethod().equals("PUT")) {
         if (pathInfo.endsWith("savelayer")) {
            json = saveLayer(request, response, store);
         } else if (pathInfo.endsWith("newannotatortask")) {
            json = newAnnotatorTask(request, response, store);
         } else if (pathInfo.endsWith("saveannotatortaskdescription")) {
            json = saveAnnotatorTaskDescription(request, response, store);
         } else if (pathInfo.endsWith("saveannotatortaskparameters")) {
            json = saveAnnotatorTaskParameters(request, response, store);
         } else if (pathInfo.endsWith("deleteannotatortask")) {
            json = deleteAnnotatorTask(request, response, store);
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

      // these can be GET or POST
      if (pathInfo.endsWith("getannotatortasks")) {
         json = getAnnotatorTasks(request, response, store);
      } else if (pathInfo.endsWith("getannotatortaskparameters")) {
         json = getAnnotatorTaskParameters(request, response, store);
      } 
      
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
   protected JsonObject saveLayer(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
      Vector<String> errors = new Vector<String>();
      // read the incoming object
      JsonReader reader = Json.createReader(request.getReader());
      // incoming object:
      JsonObject json = reader.readObject();
      Layer layer = new Layer(json);
      if (layer.getId() == null) errors.add(localize(request, "No ID specified."));
      if (errors.size() > 0) return failureResult(errors);
      return successResult(
         request, store.saveLayer(layer), null);
   }      
   
   /**
    * Create a new annotator task with the given ID and description.
    * @param request The HTTP request with parameters:
    * <dl>
    *  <dt> annotatorId </dt><dd> The ID of the annotator that will perform the task. </dd>
    *  <dt> taskId </dt>     <dd> The ID of the task, which must not already exist. </dd>
    *  <dt> description </dt><dd> The description of the task. </dd>
    * </dl>
    * @param response The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject newAnnotatorTask(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
      Vector<String> errors = new Vector<String>();
      // get/validate the parameters
      String annotatorId = request.getParameter("annotatorId");
      if (annotatorId == null) errors.add(localize(request, "No Annotator ID specified.")); // TODO i18n
      String taskId = request.getParameter("taskId");
      if (taskId == null) errors.add(localize(request, "No ID specified."));
      String description = request.getParameter("description");
      if (description == null) description = "";
      if (errors.size() > 0) return failureResult(errors);
      
      try {
         store.newAnnotatorTask(annotatorId, taskId, description);
         return successResult(
            request, null, localize(request, "Record created.")); // TODO i18n
      } catch(ExistingIdException exception) {
         errors.add(localize(
                       request, "A task with that ID already exists: {0}", exception.getId())); // TODO i18n
         return failureResult(errors);
      } catch(InvalidIdException exception) {
         errors.add(localize(request, "That annotator isn't installed: {0}", exception.getId())); // TODO i18n
         return failureResult(errors);
      }
   }      

   /**
    * Supplies a list of automation tasks for the identified annotator.
    * @param request The HTTP request with parameter:
    * <dl>
    *  <dt> annotatorId </dt><dd> The ID of the annotator that performs the tasks. </dd>
    * </dl>
    * @param response The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject getAnnotatorTasks(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
      Vector<String> errors = new Vector<String>();
      // get/validate the parameters
      String annotatorId = request.getParameter("annotatorId");
      if (annotatorId == null) errors.add(localize(request, "No Annotator ID specified.")); // TODO i18n
      if (errors.size() > 0) return failureResult(errors);

      return successResult(
         request, store.getAnnotatorTasks(annotatorId), null);
   }      

   /**
    * Supplies the given task's parameter string.
    * @param request The HTTP request with parameter:
    * <dl>
    *  <dt> taskId </dt><dd> The ID of the automation task. </dd>
    * </dl>
    * @param response The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject getAnnotatorTaskParameters(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
      Vector<String> errors = new Vector<String>();
      // get/validate the parameters
      String taskId = request.getParameter("taskId");
      if (taskId == null) errors.add(localize(request, "No ID specified."));
      if (errors.size() > 0) return failureResult(errors);

      return successResult(
         request, store.getAnnotatorTaskParameters(taskId), null);
   }
   
   /**
    * Update the annotator task description.
    * @param request The HTTP request with parameters:
    * <dl>
    *  <dt> taskId </dt>     <dd> The ID of the task, which must already exist. </dd>
    *  <dt> description </dt><dd> The new description of the task. </dd>
    * </dl>
    * @param response The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject saveAnnotatorTaskDescription(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
      Vector<String> errors = new Vector<String>();
      // get/validate the parameters
      String taskId = request.getParameter("taskId");
      if (taskId == null) errors.add(localize(request, "No ID specified."));
      String description = request.getParameter("description");
      if (description == null) description = "";
      if (errors.size() > 0) return failureResult(errors);

      store.saveAnnotatorTaskDescription(taskId, description);
      return successResult(
         request, null, localize(request, "Record updated.")); // TODO i18n
   }      

   /**
    * Update the annotator task parameters.
    * @param request The HTTP request with parameters:
    * <dl>
    *  <dt> taskId </dt>    <dd> The ID of the task, which must already exist. </dd>
    *  <dt> parameters </dt><dd> The task parameters, serialized as a string. </dd>
    * </dl>
    * @param response The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject saveAnnotatorTaskParameters(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
      Vector<String> errors = new Vector<String>();
      // get/validate the parameters
      String taskId = request.getParameter("taskId");
      if (taskId == null) errors.add(localize(request, "No ID specified."));
      String parameters = request.getParameter("parameters");
      if (parameters == null) parameters = "";
      if (errors.size() > 0) return failureResult(errors);

      store.saveAnnotatorTaskParameters(taskId, parameters);
      return successResult(
         request, null, localize(request, "Record updated.")); // TODO i18n
   }      

   /**
    * Delete the identified automation task.
    * @param request The HTTP request with parameter:
    * <dl>
    *  <dt> taskId </dt><dd> The ID of the automation task. </dd>
    * </dl>
    * @param response The HTTP response.
    * @param store A graph store object.
    * @return A JSON response for returning to the caller.
    */
   protected JsonObject deleteAnnotatorTask(
      HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
      throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
      Vector<String> errors = new Vector<String>();
      // get/validate the parameters
      String taskId = request.getParameter("taskId");
      if (taskId == null) errors.add(localize(request, "No ID specified."));
      if (errors.size() > 0) return failureResult(errors);

      store.deleteAnnotatorTask(taskId);
      return successResult(
         request, null, localize(request, "Record deleted.")); // TODO i18n
   }

   private static final long serialVersionUID = 1;
} // end of class StoreAdministration
