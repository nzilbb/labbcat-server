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
package nzilbb.labbcat.server.api.admin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Vector;
import java.util.function.Consumer;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.xml.parsers.*;
import javax.xml.xpath.*;
import nzilbb.ag.*;
import nzilbb.labbcat.server.api.RequestParameters;
import nzilbb.labbcat.server.db.*;
import org.w3c.dom.*;
import org.xml.sax.*;

/**
 * Endpoints starting <tt>/api/admin/store/&hellip;</tt> provide an HTTP-based API for access to
 * <a href="https://nzilbb.github.io/ag/apidocs/nzilbb/ag/GraphStore.html">GraphStore</a>
 * functions. This includes all requests supported by {@link nzilbb.labbcat.server.api.Store} and {@link nzilbb.labbcat.server.api.edit.Store}.
 * <p> The endpoints documented here only work for <b>POST</b> or <b>PUT</b> HTTP requests and return a JSON response with the same standard envelope structure:
 * <dl>
 *  <dt>title</dt> <dd>(string) The title of the LaBB-CAT instance.</dd>
 *  <dt>version</dt> <dd>(string) The version of the LaBB-CAT instance</dd>
 *  <dt>code</dt> <dd>(int) 0 if the request was successful, 1 if there was a problem</dd>
 *  <dt>messages</dt> <dd>An array of message strings.</dd>
 *  <dt>errors</dt> <dd>An array of error message strings.</dd>
 *  <dt>model</dt> <dd>The result of the request, which may be a JSON object, JSON array,
 *   or a simple type.</dd>
 * </dl>
 * <p> e.g. the response to 
 * <tt>http://localhost:8080/labbcat/api/admin/store/newLayer</tt>
 *  might be:
 * <pre>{
 *    "title":"Store",
 *    "version":"20230403.1833",
 *    "code":0,
 *    "errors":[],
 *    "messages":[
 *        "Layer added: test-layer"
 *    ],
 *    "model":{
 *        "alignment":0,
 *        "category":"",
 *        "description":"",
 *        "id":"test-layer",
 *        "parentId":"segment",
 *        "parentIncludes":true,
 *        "peers":true,
 *        "peersOverlap":true,
 *        "saturated":true,
 *        "type":"string",
 *        "validLabels":{},
 *        "notes":""
 *    }
 *}</pre>
 * <p> If the <q>Accept-Language</q> request header is set, the server will endeavor to
 * localize messages to the specified language.
 *
 * <p> User authorization for password-protected instances of LaBB-CAT uses the 'Basic'
 * HTTP authentication scheme. This involves sending an <q>Authorization</q> request
 * header of the form <tt>Basic <var>TTTTTTTT</var></tt>, where <var>TTTTTTTT</var> is an
 * authentication token formed by base64-encoding a string of the form
 * <tt><var>username</var>:<var>password</var></tt>
 *
 <a id="newLayer(nzilbb.ag.Layer)">
 <!--   -->
 </a>
 <ul class="blockList">
 <li class="blockList">
 <h4>/api/admin/store/newLayer</h4>
 <div class="block">Adds a new layer.
 Only the <b> POST </b> or <b> PUT </b> HTTP method is supported.
 <ul>
 <li><em> Request Body </em> - a JSON-encoded object representing the layer definition, with the following structure:
 <ul>
 <li> <q> id </q> : The ID of the layer to create. </li>
 <li> <q> parentId </q> : The layer's parent layer id. </li>
 <li> <q> description </q> : The description of the layer. </li>
 <li> <q> alignment </q> : The layer's alignment 
 - 0 for none, 1 for point alignment, 2 for interval alignment. </li>
 <li> <q> peers </q> : Whether children on this layer have peers or not. </li>
 <li> <q> peersOverlap </q> : Whether child peers on this layer can overlap or not. </li>
 <li> <q> parentIncludes </q> : Whether the parent temporally includes the child. </li>
 <li> <q> saturated </q> : Whether children must temporally fill the entire parent
 duration (true) or not (false). </li>
 <li> <q> type </q> : The type for labels on this layer, e.g. string, number,
 boolean, ipa. </li>
 <li> <q> validLabels </q> : List of valid label values for this layer, or null 
 if the layer values are not restricted. The 'key' is the possible label value, and 
 each key is associated with a description of the value (e.g. for displaying to users).  
 </li>
 <li> <q> validLabelsDefinition </q> : Optional list of valid label values for this layer.  
    <p> LaBB-CAT extends the {@link Layer#validLabels} funcionality by supporting an
    alternative layer attribute: <tt>validLabelsDefinition</tt>, which is an array of
    label definitions, each definition being a map of string to string or integer. Each
    label definition is expected to have the following attributes:
    <dl>
    <dt>label</dt> 
     <dd>what the underlying label is in LaBB-CAT (i.e. the DISC label, for a DISC layer)</dd> 
    <dt>legend</dt> 
     <dd>the symbol on the label helper or in the transcript, for the label (e.g. the IPA
         version of the label) - if there's no legend specified, then there's no option
         on the label helper (so that type-able consonants like p, b, t, d etc. don't
         take up space on the label helper)</dd> 
    <dt>description</dt> 
     <dd>tool-tip text that appears if you hover the mouse over the IPA symbol in the helper</dd>
    <dt>category</dt> 
     <dd>the broad category of the symbol, for organizing the layout of the helper</dd>
    <dt>subcategory</dt> 
     <dd>the narrower category of the symbol, for listing subgroups of symbols together</dd>
    <dt>display_order</dt> 
     <dd>the order to process/list the labels in</dd>
    </dl>
    <p> <tt>validLabelsDefinition</tt> takes precedence over <tt>validLabels</tt> -
    i.e. if <tt>validLabelsDefinition</tt> is present, it's label options are
    saved. Otherwise, the <tt>validLabels</tt> options are saved.
 </li>
 <li> <q> category </q> : Category for the layer, if any. </li>
 </ul>
 </li>
 <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 object representing the layer defintion actually saved, using the same stucture as the body. </li>
 <li><em> Response Status </em>
 <ul>
 <li><em> 200 </em> : The layer was successfully saved. </li>
 <li><em> 400 </em> : The layer was not successfully saved. </li> 
 </ul></li> 
 </div>
 </li>
 </ul>
 <a id="saveLayer(nzilbb.ag.Layer)">
 <!--   -->
 </a>
 <ul class="blockList">
 <li class="blockList">
 <h4>/api/admin/store/saveLayer</h4>
 <div class="block">Saves changes to a layer, or adds a new layer.
 Only the <b> POST </b> or <b> PUT </b> HTTP method is supported.
 <ul>
 <li><em> Request Body </em> - a JSON-encoded object representing the layer definition, with the following structure:
 <ul>
 <li> <q> id </q> : The ID of the layer to update. </li>
 <li> <q> parentId </q> : The layer's parent layer id. </li>
 <li> <q> description </q> : The description of the layer. </li>
 <li> <q> alignment </q> : The layer's alignment 
 - 0 for none, 1 for point alignment, 2 for interval alignment. </li>
 <li> <q> peers </q> : Whether children on this layer have peers or not. </li>
 <li> <q> peersOverlap </q> : Whether child peers on this layer can overlap or not. </li>
 <li> <q> parentIncludes </q> : Whether the parent temporally includes the child. </li>
 <li> <q> saturated </q> : Whether children must temporally fill the entire parent
 duration (true) or not (false). </li>
 <li> <q> type </q> : The type for labels on this layer, e.g. string, number,
 boolean, ipa. </li>
 <li> <q> validLabels </q> : Map of valid label values for this layer, which may be empty
 if the layer values are not restricted. The 'key' is the possible label value, and 
 each key is associated with a description of the value (e.g. for displaying to users).  
 </li>
 <li> <q> validLabelsDefinition </q> : Optional list of valid label values for this layer.  
    <p> LaBB-CAT extends the {@link Layer#validLabels} funcionality by supporting an
    alternative layer attribute: <tt>validLabelsDefinition</tt>, which is an array of
    label definitions, each definition being a map of string to string or integer. Each
    label definition is expected to have the following attributes:
    <dl>
    <dt>label</dt> 
     <dd>what the underlying label is in LaBB-CAT (i.e. the DISC label, for a DISC layer)</dd> 
    <dt>legend</dt> 
     <dd>the symbol on the label helper or in the transcript, for the label (e.g. the IPA
         version of the label) - if there's no legend specified, then there's no option
         on the label helper (so that type-able consonants like p, b, t, d etc. don't
         take up space on the label helper)</dd> 
    <dt>description</dt> 
     <dd>tool-tip text that appears if you hover the mouse over the IPA symbol in the helper</dd>
    <dt>category</dt> 
     <dd>the broad category of the symbol, for organizing the layout of the helper</dd>
    <dt>subcategory</dt> 
     <dd>the narrower category of the symbol, for listing subgroups of symbols together</dd>
    <dt>display_order</dt> 
     <dd>the order to process/list the labels in</dd>
    </dl>
    <p> <tt>validLabelsDefinition</tt> takes precedence over <tt>validLabels</tt> -
    i.e. if <tt>validLabelsDefinition</tt> is present, it's label options are
    saved. Otherwise, the <tt>validLabels</tt> options are saved.
 </li>
 <li> <q> category </q> : Category for the layer, if any. </li>
 </ul>
 </li>
 <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 object representing the layer defintion actually saved, using the same stucture as the body. </li>
 <li><em> Response Status </em>
 <ul>
 <li><em> 200 </em> : The layer was successfully saved. </li>
 <li><em> 400 </em> : The layer was not successfully saved. </li> 
 </ul></li> 
 </div>
 </li>
 </ul>
 <a id="deleteLayer(String)">
 <!--   -->
 </a>
 <ul class="blockList">
 <li class="blockList">
 <h4>/api/admin/store/deleteLayer</h4>
 <div class="block">Deletes an existing layer.
 Only the <b> POST </b> or <b> PUT </b> HTTP method is supported.
 <ul>
 <li><em> Request Body </em> - a application/x-www-form-urlencoded body with the following parameter:
 <ul>
 <li> <q> id </q> : The ID of the layer to delete. </li>
 </ul>
 </li>
 <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 object representing the layer defintion actually saved, using the same stucture as the body. </li>
 <li><em> Response Status </em>
 <ul>
 <li><em> 200 </em> : The layer was successfully deleted. </li>
 <li><em> 400 </em> : The layer was not successfully deleted. </li> 
 </ul></li> 
 </div>
 </li>
 </ul>
 <a id="destroyAnnotation(java.lang.String,java.lang.String)">
 <!--   -->
 </a>
 <ul class="blockList">
 <li class="blockList">
 <h4>/api/edit/store/destroyAnnotation</h4>
 <div class="block">Destroys the annotation with the given ID.</div>
 <dl>
 <dt><span class="paramLabel">Parameters:</span></dt>
 <dd><code>id</code> - The ID of the transcript.</dd>
 <dd><code>annotationId</code> - The annotation's ID.</dd>
 </dl>
 </li>
 </ul>
 <a id="deleteTranscript(java.lang.String)">
 <!--   -->
 </a>
 <ul class="blockListLast">
 <li class="blockList">
 <h4>/api/edit/store/deleteTranscript</h4>
 <div class="block">Deletes the given transcript, and all associated files.</div>
 <dl>
 <dt><span class="paramLabel">Parameters:</span></dt>
 <dd><code>id</code> - The ID transcript to delete.</dd>
 </dl>
 </li>
 </ul>

 <a id="newAnnotatorTask(java.lang.String,java.lang.String,java.lang.String)">
 <!--   -->
 </a>
 <ul class="blockListLast">
 <li class="blockList">
 <h4>/api/admin/store/newAnnotatorTask</h4>
 <div class="block">Create a new annotator task with the given ID and description.</div>
 <dl>
 <dt><span class="paramLabel">Parameters:</span></dt>
 <dt> annotatorId </dt><dd> The ID of the annotator that will perform the task. </dd>
 <dt> taskId </dt>     <dd> The ID of the task, which must not already exist. </dd>
 <dt> description </dt><dd> The description of the task. </dd>
 </dl>
 </li>
 </ul>

 <a id="saveAnnotatorTaskDescription(java.lang.String,java.lang.String)">
 <!--   -->
 </a>
 <ul class="blockListLast">
 <li class="blockList">
 <h4>/api/admin/store/saveAnnotatorTaskDescription</h4>
 <div class="block">Update the annotator task description.</div>
 <dl>
 <dt><span class="paramLabel">Parameters:</span></dt>
 <dt> taskId </dt>     <dd> The ID of the task, which must already exist. </dd>
 <dt> description </dt><dd> The description of the task. </dd>
 </dl>
 </li>
 </ul>

 <a id="saveAnnotatorTaskParameters(java.lang.String,java.lang.String)">
 <!--   -->
 </a>
 <ul class="blockListLast">
 <li class="blockList">
 <h4>/api/admin/store/saveAnnotatorTaskParameters</h4>
 <div class="block">Update the annotator task parameters.</div>
 <dl>
 <dt><span class="paramLabel">Parameters:</span></dt>
 <dt> taskId </dt>     <dd> The ID of the task, which must already exist. </dd>
 <dt> parameters </dt><dd> The task parameters, serialized as a string. </dd>
 </dl>
 </li>
 </ul>

 <a id="deleteAnnotatorTask(java.lang.String)">
 <!--   -->
 </a>
 <ul class="blockListLast">
 <li class="blockList">
 <h4>/api/admin/store/deleteAnnotatorTask</h4>
 <div class="block">Delete the identified automation task..</div>
 <dl>
 <dt><span class="paramLabel">Parameters:</span></dt>
 <dt> taskId </dt>     <dd> The ID of the task, which must already exist. </dd>
 </dl>
 </li>
 </ul>

 * @author Robert Fromont robert@fromont.net.nz
 */
public class Store extends nzilbb.labbcat.server.api.edit.Store {
  /**
   * Default constructor.
   */
  public Store() {
  } // end of constructor

  // StoreQuery overrides

  /**
   * Interprets the URL path, and executes the corresponding function on the store. This
   * method is an override of 
   * {@link nzilbb.labbcat.server.api.edit.Store#invokeFunction(String,String,String,String,RequestParameters,InputStream,Consumer,Consumer,SqlGraphStoreAdministration)}.
   * <p> This implementation only allows POST or PUT HTTP requests.
   * @param url The URI of the request. 
   * @param method The HTTP request method, e.g. "GET".
   * @param pathInfo The URL path.
   * @param queryString The URL's query string.
   * @param parameters Request parameter map.
   * @param requestBody For access to the request body.
   * @param httpStatus Receives the response status code, in case of error.
   * @param redirectUrl Receives a URL for the request to be redirected to.
   * @return JSON-encoded object representing the response
   */
  @Override
  protected JsonObject invokeFunction(String url, String method, String pathInfo, String queryString, RequestParameters parameters, InputStream requestBody, Consumer<Integer> httpStatus, Consumer<String> redirectUrl, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException, GraphNotFoundException {
    if (!context.isUserInRole("admin")) {
      httpStatus.accept(SC_FORBIDDEN);
      return failureResult("User has no admin permission.");         
    }
    pathInfo = pathInfo.toLowerCase(); // case-insensitive
    // only allow POST requests
    context.servletLog("invokeFunction " + pathInfo + " " + method);
    if ("POST".equals(method) || "PUT".equals(method)) {
      if (pathInfo.endsWith("newlayer")) {
        return newLayer(requestBody, store);
      } else if (pathInfo.endsWith("savelayer")) {
        return saveLayer(requestBody, store);
      } else if (pathInfo.endsWith("deletelayer")) {
        return deleteLayer(parameters, store);
      } else if (pathInfo.endsWith("newannotatortask")) {
        return newAnnotatorTask(parameters, store);
      } else if (pathInfo.endsWith("saveannotatortaskdescription")) {
        return saveAnnotatorTaskDescription(parameters, store);
      } else if (pathInfo.endsWith("saveannotatortaskparameters")) {
        return saveAnnotatorTaskParameters(parameters, store);
      } else if (pathInfo.endsWith("deleteannotatortask")) {
        return deleteAnnotatorTask(parameters, store);
      }
    } // only if it's a POST request
      
    // either not POST or not a recognized function
    return super.invokeFunction(
      url, method, pathInfo, queryString, parameters, requestBody, httpStatus, redirectUrl, store);
  } // end of invokeFunction()

  // IGraphStore method handlers
   
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
   * Implementation of {@link nzilbb.ag.GraphStore#newLayer(Layer)}
   * @param parameters Request parameter map.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject newLayer(
    InputStream requestBody, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException, GraphNotFoundException {
    Vector<String> errors = new Vector<String>();
    // read the incoming object
    JsonReader reader = Json.createReader( // ensure we read as UTF-8
      new InputStreamReader(requestBody, "UTF-8"));
    // incoming object:
    JsonObject json = reader.readObject();
    Layer layer = new Layer(json);
    if (layer.getId() == null) errors.add(localize("No ID specified."));
    if (errors.size() > 0) return failureResult(errors);
    return successResult(
      store.newLayer(layer), "Layer added: {0}", layer.getId());
  }
   
  /**
   * Implementation of {@link nzilbb.ag.GraphStore#saveLayer(Layer)}
   * @param parameters Request parameter map.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject saveLayer(
    InputStream requestBody, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException, GraphNotFoundException {
    Vector<String> errors = new Vector<String>();
    // read the incoming object
    JsonReader reader = Json.createReader( // ensure we read as UTF-8
      new InputStreamReader(requestBody, "UTF-8"));
    // incoming object:
    JsonObject json = reader.readObject();
    Layer layer = new Layer(json);    
    if (layer.getId() == null) errors.add(localize("No ID specified."));
    if (errors.size() > 0) return failureResult(errors);
    return successResult(
      store.saveLayer(layer), "Layer saved: {0}", layer.getId());
  }
   
  /**
   * Implementation of {@link nzilbb.ag.GraphStore#deleteLayer(Layer)}
   * @param parameters Request parameter map.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject deleteLayer(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException, GraphNotFoundException {
    Vector<String> errors = new Vector<String>();
    String id = parameters.getString("id");
    if (id == null) errors.add(localize("No ID specified."));
    if (errors.size() > 0) return failureResult(errors);
    store.deleteLayer(id);
    return successResult(null, "Layer deleted: {0}", id);
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
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException, GraphNotFoundException {
    Vector<String> errors = new Vector<String>();
    // get/validate the parameters
    String annotatorId = parameters.getString("annotatorId");
    if (annotatorId == null) errors.add(localize("No Annotator ID specified."));
    String taskId = parameters.getString("taskId");
    if (taskId == null) errors.add(localize("No ID specified."));
    String description = parameters.getString("description");
    if (description == null) description = "";
    if (errors.size() > 0) return failureResult(errors);
      
    try {
      store.newAnnotatorTask(annotatorId, taskId, description);
      return successResult(null, localize("Record created."));
    } catch(ExistingIdException exception) {
      errors.add(localize("A task with that ID already exists: {0}", exception.getId()));
      return failureResult(errors);
    } catch(InvalidIdException exception) {
      errors.add(localize("That annotator isn't installed: {0}", exception.getId()));
      return failureResult(errors);
    }
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
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException, GraphNotFoundException {
    Vector<String> errors = new Vector<String>();
    // get/validate the parameters
    String taskId = parameters.getString("taskId");
    if (taskId == null) errors.add(localize("No ID specified."));
    String description = parameters.getString("description");
    if (description == null) description = "";
    if (errors.size() > 0) return failureResult(errors);

    store.saveAnnotatorTaskDescription(taskId, description);
    return successResult(
      null, localize("Record updated."));
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
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException, GraphNotFoundException {
    Vector<String> errors = new Vector<String>();
    // get/validate the parameters
    String taskId = parameters.getString("taskId");
    if (taskId == null) errors.add(localize("No ID specified."));
    String params = parameters.getString("parameters");
    if (params == null) params = "";
    if (errors.size() > 0) return failureResult(errors);

    store.saveAnnotatorTaskParameters(taskId, params);
    return successResult(
      null, localize("Record updated."));
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
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException, GraphNotFoundException {
    Vector<String> errors = new Vector<String>();
    // get/validate the parameters
    String taskId = parameters.getString("taskId");
    if (taskId == null) errors.add(localize("No ID specified."));
    if (errors.size() > 0) return failureResult(errors);

    store.deleteAnnotatorTask(taskId);
    return successResult(
      null, localize("Record deleted."));
  }

  private static final long serialVersionUID = 1;
} // end of class Store
