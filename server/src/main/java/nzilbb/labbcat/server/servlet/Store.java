//
// Copyright 2020-2023 New Zealand Institute of Language, Brain and Behaviour, 
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
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.SortedSet;
import java.util.Vector;
import java.util.stream.Collectors;
import javax.json.JsonObject;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.*;
import javax.xml.xpath.*;
import nzilbb.ag.*;
import nzilbb.ag.serialize.SerializationDescriptor;
import nzilbb.ag.serialize.SerializationException;
import nzilbb.ag.serialize.SerializationParametersMissingException;
import nzilbb.ag.serialize.SerializerNotConfiguredException;
import nzilbb.ag.serialize.json.JSONSerialization;
import nzilbb.ag.serialize.util.NamedStream;
import nzilbb.ag.serialize.util.Utility;
import nzilbb.ag.util.Merger;
import nzilbb.configure.Parameter;
import nzilbb.configure.ParameterSet;
import nzilbb.labbcat.server.db.*;
import org.apache.commons.fileupload.FileItem;
import org.w3c.dom.*;
import org.xml.sax.*;

/**
 * Endpoints starting <tt>/api/edit/store/&hellip;</tt> provide an HTTP-based API for access to
 * <a href="https://nzilbb.github.io/ag/apidocs/nzilbb/ag/GraphStore.html">GraphStore</a>
 * functions. This includes all requests supported by {@link StoreQuery}.
 * <p> The endpoints documented here only work for <b>POST</b> HTTP requests and return a JSON response with the same standard envelope structure:
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
 * <tt>http://localhost:8080/labbcat/api/edit/store/deleteTranscript</tt>
 *  might be:
 * <pre>{
 *    "title":"Store",
 *    "version":"20230403.1833",
 *    "code":0,
 *    "errors":[],
 *    "messages":[
 *        "Transcript deleted: test.trs"
 *    ],
 *    "model":null
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
 <a id="createAnnotation(java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.Integer,java.lang.String)">
 <!--   -->
 </a>
 <ul class="blockList">
 <li class="blockList">
 <h4>/api/edit/store/createAnnotation</h4>
 <div class="block">Creates an annotation starting at <var>from</var> and ending at <var>to</var>.</div>
 <dl>
 <dt><span class="paramLabel">Parameters:</span></dt>
 <dd><code>id</code> - The ID of the transcript.</dd>
 <dd><code>fromId</code> - The start anchor's ID.</dd>
 <dd><code>toId</code> - The end anchor's ID.</dd>
 <dd><code>layerId</code> - The layer ID of the resulting annotation.</dd>
 <dd><code>label</code> - The label of the resulting annotation.</dd>
 <dd><code>confidence</code> - The confidence rating.</dd>
 <dd><code>parentId</code> - The new annotation's parent's ID.</dd>
 <dt><span class="returnLabel">Returns:</span></dt>
 <dd>The ID of the new annotation.</dd>
 </dl>
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
 <a id="saveParticipant(Annotation)">
 <!--   -->
 </a>
 <ul class="blockList">
 <li class="blockList">
 <h4>/api/edit/store/saveParticipant</h4>
 <div class="block">Saves a participant, and all its tags, to the database.
 <p> If the participant ID does not already exist in the database, a new participant record is created.
 </div>
 <dl>
 <dt><span class="paramLabel">Parameters:</span></dt>
 <dd><code>id</code> - The ID of the participant.</dd>
 <dd><code>label</code> - The new ID of the participant, if it's changing.</dd>
 <dd>A series of parameters whose names are prefixed "participant_", representing the participant attribute values. <br>
 <dd><code>_password</code> - An optional parameter for specifying a new pass phrase for the participant.</dd>
 </dd>
 <dt><span class="returnLabel">Returns:</span></dt>
 <dd>A JSON representation of the new participant record, structured as an Annotation.</dd>
 </dl>
 </li>
 </ul>
 <a id="saveTranscript(Graph)">
 <!--   -->
 </a>
 <ul class="blockList">
 <li class="blockList">
 <h4>/api/edit/store/saveTranscript</h4>
 <div class="block">Saved changes to transcript attributes to the database.
 </div>
 <dl>
 <dt><span class="paramLabel">Body:</span></dt>
 <dd>JSON-encoded representation of the transcript's annotation graph, including only
 * transcript attribute layers.</dd>
 </dd>
 </dl>
 </li>
 </ul>
 <a id="deleteTranscript(java.lang.String)">
 <!--   -->
 </a>
 <ul class="blockListLast">
 <li class="blockList">
 <h4>/api/edit/store/deleteTranscript</h4>
 <pre class="methodSignature">void&nbsp;deleteTranscript&#8203;(String&nbsp;id)
 throws <a href="StoreException.html" title="class in nzilbb.ag">StoreException</a>,
 <a href="PermissionException.html" title="class in nzilbb.ag">PermissionException</a>,
 <a href="GraphNotFoundException.html" title="class in nzilbb.ag">GraphNotFoundException</a></pre>
 <div class="block">Deletes the given transcript, and all associated files.</div>
 <dl>
 <dt><span class="paramLabel">Parameters:</span></dt>
 <dd><code>id</code> - The ID transcript to delete.</dd>
 </dl>
 </li>
 </ul>
 <a id="deleteParticipant(java.lang.String)">
 <!--   -->
 </a>
 <ul class="blockListLast">
 <li class="blockList">
 <h4>/api/edit/store/deleteParticipant</h4>
 <pre class="methodSignature">void&nbsp;deleteParticipant&#8203;(String&nbsp;id)
 throws <a href="StoreException.html" title="class in nzilbb.ag">StoreException</a>,
 <a href="PermissionException.html" title="class in nzilbb.ag">PermissionException</a>,
 <a href="GraphNotFoundException.html" title="class in nzilbb.ag">GraphNotFoundException</a></pre>
 <div class="block">Deletes the given participant, and all associated meta-data.</div>
 <dl>
 <dt><span class="paramLabel">Parameters:</span></dt>
 <dd><code>id</code> - The ID participant to delete.</dd>
 </dl>
 </li>
 </ul>
 <a id="deleteMatchingAnnotations(java.lang.String)">
 <!--   -->
 </a>
 <ul class="blockListLast">
 <li class="blockList">
 <h4>/api/edit/store/deleteMatchingAnnotations</h4>
 <pre class="methodSignature">void&nbsp;deleteMatchingAnnotations&#8203;(String&nbsp;expression)
 throws <a href="StoreException.html" title="class in nzilbb.ag">StoreException</a>,
 <a href="PermissionException.html" title="class in nzilbb.ag">PermissionException</a>,
 <div class="block">Deletes all annotations that match a particular pattern.</div>
 <dl>
 <dt><span class="paramLabel">Parameters:</span></dt>
 <dd><code>expression</code> - An expression that determines which annotations match.
  <p> The expression language is loosely based on JavaScript; expressions such as the
  following can be used: 
  <ul>
   <li><code>layer.id == 'pronunciation' 
        &amp;&amp; first('orthography').label == 'the'</code></li>
   <li><code>first('language').label == 'en' &amp;&amp; layer.id == 'pronunciation' 
        &amp;&amp; first('orthography').label == 'the'</code></li> 
  </ul>
  <p><em>NB</em> all expressions must match by either id or layer.id.
 </dd>
 <dt><span class="returnLabel">Returns:</span></dt>
 <dd>The number of new annotations deleted.</dd>
 </dl>
 </li>
 </ul>
 <a id="tagMatchingAnnotations(java.lang.String,java.lang.String,java.lang.String,java.lang.Integer)">
 <!--   -->
 </a>
 <ul class="blockListLast">
 <li class="blockList">
 <h4>/api/edit/store/tagMatchingAnnotations</h4>
 <pre class="methodSignature">void&nbsp;tagMatchingAnnotations&#8203;(String&nbsp;expression,&nbsp;String&nbsp;layerId,&nbsp;String&nbsp;label,&nbsp;Integer&nbsp;confidence)
 throws <a href="StoreException.html" title="class in nzilbb.ag">StoreException</a>,
 <a href="PermissionException.html" title="class in nzilbb.ag">PermissionException</a>,
 <div class="block">Identifies a list of annotations that match a particular pattern, and tags them on
   the given layer with the given label. If the specified layer ID does not allow peers,
   all existing tags will be deleted. Otherwise, tagging does not affect any existing tags on
   the matching annotations.</div>
 <dl>
 <dt><span class="paramLabel">Parameters:</span></dt>
 <dd><code>expression</code> - An expression that determines which annotations match.
   <p> The expression language is loosely based on JavaScript; expressions such as the
   following can be used: 
   <ul>
    <li><code>layer.id == 'orthography' &amp;&amp; label == 'word'</code></li>
    <li><code>first('language').label == 'en' &amp;&amp; layer.id == 'orthography'
         &amp;&amp; label == 'word'</code></li> 
   </ul>
   <p><em>NB</em> all expressions must match by either id or layer.id.
 </dd>
 <dd><code>layerId</code> - The layer ID of the resulting annotation.</dd>
 <dd><code>label</code> - The label of the resulting annotation.</dd>
 <dd><code>confidence</code> - The confidence rating.</dd>
 <dt><span class="returnLabel">Returns:</span></dt>
 <dd>The number of new annotations added.</dd>
 </dl>
 </li>
 </ul>
 <a id="saveMedia(String,String,String)">
 <!--   -->
 </a>
 <ul class="blockList">
 <li class="blockList">
 <h4>/api/edit/store/saveMedia</h4>
 <div class="block">Saves the given media for the given transcript.
 </div>
 <dl>
 <dt><span class="paramLabel">Body: multipart POST request, with the following parameters</span></dt>
 <dd><code>id</code> - The ID of the transcript.</dd>
 <dd><code>trackSuffix</code> (optional) - The track suffix of the media - see
      {@link MediaTrackDefinition#suffix}.</dd>  
 <dd><code>media</code> - The sound or video file to save for the transcript.</dd>
 </dl>
 </li>
 </ul>
 <a id="saveEpisodeDocument(String,String)">
 <!--   -->
 </a>
 <ul class="blockList">
 <li class="blockList">
 <h4>/api/edit/store/saveMedia</h4>
 <div class="block">Saves the given document for the episode of the given transcript.
 </div>
 <dl>
 <dt><span class="paramLabel">Body: multipart POST request, with the following parameters</span></dt>
 <dd><code>id</code> - The ID of the transcript.</dd>
 <dd><code>document</code> - The document file to save for the transcript.</dd>
 </dl>
 </li>
 </ul>
 <a id="deleteMedia(String,String)">
 <!--   -->
 </a>
 <ul class="blockList">
 <li class="blockList">
 <h4>/api/edit/store/deleteMedia</h4>
 <div class="block">Delete a given media or episode document file.
 </div>
 <dl>
 <dt><span class="paramLabel">Body: multipart POST request, with the following parameters</span></dt>
 <dd><code>id</code> - The associated transcript ID.</dd>
 <dd><code>fileName</code> - The media file name, e.g. {@link MediaFile#name}.</dd>
 </dl>
 </li>
 </ul>

 * @author Robert Fromont robert@fromont.net.nz
 */
@WebServlet({"/edit/store/*", "/api/edit/store/*"})
public class Store extends StoreQuery {
   
  /**
   * Default constructor.
   */
  public Store() {
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
   * <p> This implementation only allows POST HTTP requests.
   * @param request The request.
   * @param response The response.
   * @param store The connected graph store.
   * @return The response to send to the caller, or null if the request could not be interpreted.
   */
  @Override
  protected JsonObject invokeFunction(HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
    try { // check they have edit permission
      if (!IsUserInRole("edit", request, store.getConnection())) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return failureResult(request, "User has no edit permission.");
      }
    } catch(SQLException x) {
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return failureResult(x);
    }
      
    JsonObject json = null;
    String pathInfo = request.getPathInfo().toLowerCase(); // case-insensitive
    // only allow POST requests
    if (request.getMethod().equals("POST")) {
         
      if (pathInfo.endsWith("createannotation")) {
        json = createAnnotation(request, response, store);
      } else if (pathInfo.endsWith("destroyannotation")) {
        json = destroyAnnotation(request, response, store);
      } else if (pathInfo.endsWith("saveparticipant")) {
        json = saveParticipant(request, response, store);
      } else if (pathInfo.endsWith("savetranscript")) {
        json = saveTranscript(request, response, store);
      } else if (pathInfo.endsWith("deletetranscript")
                 // support deprecated name
                 || pathInfo.endsWith("deletegraph")) {
        json = deleteTranscript(request, response, store);
      } else if (pathInfo.endsWith("deleteparticipant")) {
        json = deleteParticipant(request, response, store);
      } else if (pathInfo.endsWith("deletematchingannotations")) {
        json = deleteMatchingAnnotations(request, response, store);
      } else if (pathInfo.endsWith("tagmatchingannotations")) {
        json = tagMatchingAnnotations(request, response, store);
      } else if (pathInfo.endsWith("savemedia")) {
        json = saveMedia(request, response, store);
      } else if (pathInfo.endsWith("saveepisodedocument")) {
        json = saveEpisodeDocument(request, response, store);
      } else if (pathInfo.endsWith("deletemedia")) {
        json = deleteMedia(request, response, store);
      }
    } // only if it's a POST request
      
    if (json == null) { // either not POST or not a recognized function
      json = super.invokeFunction(request, response, store);
    }
    return json;
  } // end of invokeFunction()

  // GraphStore method handlers

  /**
   * Implementation of {@link nzilbb.ag.GraphStore#saveTranscript(Graph)}, which currently
   * only supports transcript attribute update.
   * @param request The HTTP request.
   * @param response The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject saveTranscript(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException,
    GraphNotFoundException {

    // parse body as JSON to construct incoming graph
    // serialize with JSON serialization
    Schema schema = store.getSchema();
    JSONSerialization s = new JSONSerialization();
    s.configure(s.configure(new ParameterSet(), schema), schema);
    try {
      s.setParameters( // set the default parameters from...
        s.load( // ... loading the incoming stream
          Utility.OneNamedStreamArray(
            new NamedStream().setStream(request.getInputStream()).setMimeType("application/json")),
          schema));
      Graph editedGraph = s.deserialize()[0];
    
      // list layers, check they're all transcript attribute layers
      Vector<String> layerIds = new Vector<String>();
      Vector<String> errors = new Vector<String>();
      for (Layer layer : editedGraph.getSchema().getLayers().values()) {
        if (layer.getParent() != null
            && layer.getParent().equals(editedGraph.getSchema().getRoot())
            && layer.getAlignment() == Constants.ALIGNMENT_NONE) {
          layerIds.add(layer.getId());
        } else if (!layer.equals(editedGraph.getSchema().getRoot())) {
          errors.add(localize(
                       request, "Only transcript attributes can be updated: {0}", layer.getId())); 
        }
      } // next layer
      if (errors.size() > 0) return failureResult(errors);
      
      // get server version of graph
      Graph ag = store.getTranscript(editedGraph.getId(), layerIds.toArray(new String[0]));
      ag.trackChanges();
      
      // merge attribute changes
      Merger merger = new Merger(editedGraph);
      merger.transform(ag);      

      // save changes to graph store
      boolean thereWereChanges = store.saveTranscript(ag);
      if (thereWereChanges) {
            return successResult(request, true, "Transcript saved: {0}", ag.getId());
      } else {
        return successResult(request, false, "No changes to save: {0}", ag.getId());
      }
      
    } catch(TransformationException exception) {
      throw new StoreException(exception);
    } catch(SerializerNotConfiguredException exception) { // shouldn't happen
      throw new StoreException(exception);
    } catch(SerializationParametersMissingException exception) { // shouldn't happen
      throw new StoreException(exception);
    } catch(SerializationException exception) { // shouldn't happen
      throw new StoreException(exception);
    } catch(Throwable t) {
      t.printStackTrace(System.out);
      throw new StoreException(t);
    }
  }
   
  /**
   * Implementation of {@link nzilbb.ag.GraphStore#createAnnotation(String,String,String,String,String,Integer,String)}
   * @param request The HTTP request.
   * @param response The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject createAnnotation(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
    Vector<String> errors = new Vector<String>();
    String id = request.getParameter("id");
    if (id == null) errors.add(localize(request, "No ID specified."));
    String fromId = request.getParameter("fromId");
    if (fromId == null) errors.add(localize(request, "No From ID specified."));
    String toId = request.getParameter("toId");
    if (toId == null) errors.add(localize(request, "No To ID specified."));
    String layerId = request.getParameter("layerId");
    if (layerId == null) errors.add(localize(request, "No layer ID specified."));
    String label = request.getParameter("label");
    if (label == null) errors.add(localize(request, "No label specified."));
    Integer confidence = null;
    if (request.getParameter("confidence") == null) {
      errors.add(localize(request, "No confidence specified."));
    } else {
      try {
        confidence = Integer.valueOf(request.getParameter("confidence"));
      } catch(NumberFormatException x) {
        errors.add(localize(request, "Invalid confidence: {0}", x.getMessage()));
      }
    }
    String parentId = request.getParameter("parentId");
    if (parentId == null) errors.add(localize(request, "No parent ID specified."));
    if (errors.size() > 0) return failureResult(errors);
    return successResult(
      request, store.createAnnotation(id, fromId, toId, layerId, label, confidence, parentId), null);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.GraphStore#destroyAnnotation(String,String)}
   * @param request The HTTP request.
   * @param response The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject destroyAnnotation(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
    Vector<String> errors = new Vector<String>();
    String id = request.getParameter("id");
    if (id == null) errors.add(localize(request, "No ID specified."));
    String annotationId = request.getParameter("annotationId");
    if (annotationId == null) errors.add(localize(request, "No annotation ID specified."));
    if (errors.size() > 0) return failureResult(errors);
    store.destroyAnnotation(id, annotationId);
    return successResult(request, null, "Annotation deleted: {0}", id);
  }
  
  /**
   * Implementation of {@link nzilbb.ag.GraphStore#saveParticipant(Annotation)}
   * @param request The HTTP request.
   * @param response The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject saveParticipant(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
    Vector<String> errors = new Vector<String>();
    String id = request.getParameter("id");
    if (id == null) errors.add(localize(request, "No ID specified."));

    Schema schema = store.getSchema();
    // identify participant attribute layers
    Vector<Layer> participantAttributeLayers = new Vector<Layer>();
    for (Layer child : schema.getLayer(schema.getParticipantLayerId()).getChildren().values()) {
      if ("speaker".equals(child.get("class_id"))) {
        if (request.getParameter(child.getId()) != null) {
          participantAttributeLayers.add(child);
        } // there is a matching http parameter
      } // participant attribute
    } // next child
    if (request.getParameter(schema.getCorpusLayerId()) != null) {
      Layer corpusLayer = (Layer)schema.getLayer(schema.getCorpusLayerId()).clone();
      // the corpus layer has slightly different characteristics in relation to participants
      corpusLayer.setPeers(true);
      corpusLayer.setCategory("Corpus");      
      participantAttributeLayers.add(corpusLayer);
    }

    // the participant password can be updated using a pseudo-layer "_password"
    if (request.getParameter("_password") != null) {
      participantAttributeLayers.add(new Layer("_password", "Password"));
    }
    
    Annotation participant = store.getParticipant(
      id, participantAttributeLayers.stream()
      .map(l->l.getId())
      .collect(Collectors.toList())
      .toArray(new String[0]));
    if (participant == null) { // create a new one
      participant = new Annotation()
        .setLayerId(schema.getParticipantLayerId())
        .setLabel(id);
      participant.setId(id);
      participant.create();
    } 
    // ensure changes are tracked for the participant and all children
    participant.setTracker(new ChangeTracker());
    for (SortedSet<Annotation> layers : participant.getAnnotations().values()) {
      for (Annotation child : layers) {
        child.setTracker(participant.getTracker());
      } // next child
    } // next child layer
    String label = request.getParameter("label");
    if (label != null) {
      participant.setLabel(label);
    }
    
    // identify participant attribute layers
    HashSet<Annotation> toRemove = new HashSet<Annotation>();
    for (Layer layer : participantAttributeLayers) {
      if ("readonly".equals(layer.get("type"))) continue; // ignore readonly layers
      if (!layer.getPeers()) { // single value
        Annotation annotation = participant.first(layer.getId());
        if (annotation != null) annotation.setTracker(participant.getTracker());
        String value = request.getParameter(layer.getId());
        if (layer.get("other") != null) {
          String otherValue = request.getParameter(layer.getId() + "_other");
          if (otherValue.length() > 0) {
            value = otherValue;
          }
        }
        if (value != null) { // value to save
          if (annotation != null) { // update
            if (!annotation.getLabel().equals(value)) { // only if it's changing.
              annotation.setLabel(value);
            }
          } else { // insert
            // can't use createTag, because it requires that the annotation be in a graph
            Annotation tag = new Annotation(null, value, layer.getId());
            tag.create(); // don't setTracker, that uses the ID and we don't have one
            participant.addAnnotation(tag);
          }
        }
      } else { // possibly multiple values
        HashSet<String> newValues = new HashSet<String>();
        String[] multipleValues = request.getParameterValues(layer.getId());
        if (multipleValues != null) { // multiple values
          for (String value : multipleValues) {
            if (value.length() > 0 // if the value is not blank
                || layer.getValidLabels().containsKey("")) { // (unless blank is explicitly valid)
                newValues.add(value);
              }
          }
        }
        if (layer.get("other") != null) {
          String otherValue = request.getParameter(layer.getId() + "_other");
          if (otherValue.length() > 0) {
            newValues.add(otherValue);
          }
        }
        HashMap<String,Annotation> currentAnnotations = new HashMap<String,Annotation>();
        for (Annotation annotation : participant.getAnnotations(layer.getId())) {
          annotation.setTracker(participant.getTracker());
          currentAnnotations.put(annotation.getLabel(), annotation);
        } // next annotation
        
        // add values that aren't already present
        HashSet<String> valuesToAdd = new HashSet<String>(newValues);
        valuesToAdd.removeAll(currentAnnotations.keySet());
        for (String l : valuesToAdd) {
          // can't use createTag, because it requires that the annotation be in a graph
          Annotation tag = new Annotation(null, l, layer.getId());
          tag.create(); // don't setTracker, because that uses the ID and we don't have one
          participant.addAnnotation(tag);
        } // next value
	
        // delete values are aren't specified
        HashSet<String> valuesToRemove = new HashSet<String>(currentAnnotations.keySet());
        valuesToRemove.removeAll(newValues);
        for (String l : valuesToRemove) {
          currentAnnotations.get(l).destroy();
          // remove it from our cached model below...
          toRemove.add(currentAnnotations.get(l));
        } // next value
      } // possibly multiple values      
    } // next layer
    
    // save the changes
    try {
      if (store.saveParticipant(participant)) {
        // remove the deleted annotations from our local model before rendering
        for (Annotation removed : toRemove) {
          participant.getAnnotations(removed.getLayerId()).remove(removed);
        }
        return successResult(request, true, "Participant saved: {0}", id);
      } else {
        return successResult(request, false, "No changes to save: {0}", id);
      }
    } catch(Exception exception) {
      errors.add(exception.getMessage());
    }
    return failureResult(errors);
  }
  
  /**
   * Implementation of {@link nzilbb.ag.GraphStore#saveMediaString,String,String)}
   * @param request The HTTP request.
   * @param response The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject saveMedia(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
    Vector<String> errors = new Vector<String>();

    try {
      // interpret request parameters
      MultipartRequestParameters parameters = new MultipartRequestParameters(request);
      String id = parameters.getString("id");
      if (id == null) errors.add(localize(request, "No ID specified."));
      String trackSuffix = parameters.getString("trackSuffix");
      Vector<FileItem> files =  parameters.getFiles("media");
      FileItem media = files.size() == 0? null : files.firstElement();
      if (media == null) errors.add("No media received."); // TODO i18n
      if (errors.size() > 0) return failureResult(errors);

      String fileName = MultipartRequestParameters.SanitizedFileName(media);
      // save the file
      File temporaryMediaFile = File.createTempFile("saveMedia-", "-"+fileName);
      temporaryMediaFile.delete();
      temporaryMediaFile.deleteOnExit();
      media.write(temporaryMediaFile);
      MediaFile mediaFile = store.saveMedia(id, temporaryMediaFile.toURI().toString(), trackSuffix);

      // ensure the temporary file is deleted
      temporaryMediaFile.delete();

      return successResult(
        request, mediaFile, "Added {0} to {1}", fileName, id); // TODO i18n

    } catch(Exception ex) {
      throw new ServletException(ex);
    }    
  }
  
  /**
   * Implementation of {@link nzilbb.ag.GraphStore#saveEpisodeDocument(String,String)}
   * @param request The HTTP request.
   * @param response The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject saveEpisodeDocument(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
    Vector<String> errors = new Vector<String>();

    try {
      // interpret request parameters
      MultipartRequestParameters parameters = new MultipartRequestParameters(request);
      String id = parameters.getString("id");
      if (id == null) errors.add(localize(request, "No ID specified."));
      Vector<FileItem> files =  parameters.getFiles("document");
      FileItem media = files.size() == 0? null : files.firstElement();
      if (media == null) errors.add("No media received."); // TODO i18n
      if (errors.size() > 0) return failureResult(errors);
      
      String fileName = MultipartRequestParameters.SanitizedFileName(media);
      // save the file
      File temporaryMediaFile = File.createTempFile("saveEpisodeDocument-", "-"+fileName);
      temporaryMediaFile.delete();
      temporaryMediaFile.deleteOnExit();
      media.write(temporaryMediaFile);
      MediaFile mediaFile = store.saveEpisodeDocument(id, temporaryMediaFile.toURI().toString());

      // ensure the temporary file is deleted
      temporaryMediaFile.delete();
      
      return successResult(
        request, mediaFile, "Added {0} to {1}", fileName, id); // TODO i18n
      
    } catch(Exception ex) {
      throw new ServletException(ex);
    }    
  }

  /**
   * Implementation of {@link nzilbb.ag.GraphStore#deleteMedia(String,String)}
   * @param request The HTTP request.
   * @param response The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject deleteMedia(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
    Vector<String> errors = new Vector<String>();
    String id = request.getParameter("id");
    if (id == null) errors.add(localize(request, "No ID specified."));
    String fileName = request.getParameter("fileName");
    if (fileName == null) errors.add(localize(request, "No file name specified.")); // TODO i18n
    if (errors.size() > 0) return failureResult(errors);
    store.deleteMedia(id, fileName);
    return successResult(request, null, "Media deleted: {0}", id); // TODO i18n
  }

  // TODO saveSource

  /**
   * Implementation of {@link nzilbb.ag.GraphStore#deleteTranscript(String)}
   * @param request The HTTP request.
   * @param response The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject deleteTranscript(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
    Vector<String> errors = new Vector<String>();
    String id = request.getParameter("id");
    if (id == null) errors.add(localize(request, "No ID specified."));
    if (errors.size() > 0) return failureResult(errors);
    store.deleteTranscript(id);
    return successResult(request, null, "Transcript deleted: {0}", id);
  }
   
  /**
   * Implementation of {@link nzilbb.ag.GraphStore#deleteParticipant(String)}
   * @param request The HTTP request.
   * @param response The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject deleteParticipant(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
    Vector<String> errors = new Vector<String>();
    String id = request.getParameter("id");
    if (id == null) errors.add(localize(request, "No ID specified."));
    if (errors.size() > 0) return failureResult(errors);
    try {
      store.deleteParticipant(id);
      return successResult(request, null, "Participant deleted: {0}", id);
    } catch (StoreException exception) {
      errors.add(exception.getMessage());
    }
    return failureResult(errors);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.GraphStore#deleteMatchingAnnotations(String,String)}
   * @param request The HTTP request.
   * @param response The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject deleteMatchingAnnotations(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
    Vector<String> errors = new Vector<String>();
    String expression = request.getParameter("expression");
    if (expression == null) errors.add(localize(request, "No expression specified."));
    if (errors.size() > 0) return failureResult(errors);
    int deleteCount = store.deleteMatchingAnnotations(expression);
    return successResult(request, deleteCount, "Annotations deleted: {0}", deleteCount);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.GraphStore#tagMatchingAnnotations(String,String,String,Integer)}
   * @param request The HTTP request.
   * @param response The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject tagMatchingAnnotations(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
    Vector<String> errors = new Vector<String>();
    String expression = request.getParameter("expression");
    if (expression == null) errors.add(localize(request, "No expression specified."));
    String layerId = request.getParameter("layerId");
    if (layerId == null) errors.add(localize(request, "No layerId specified."));
    String label = request.getParameter("label");
    if (label == null) errors.add(localize(request, "No label specified."));
    String confidenceString = request.getParameter("confidence");
    if (confidenceString == null) errors.add(localize(request, "No confidence specified."));
    Integer confidence = null;
    if (confidenceString != null) {
      try {
        confidence = Integer.parseInt(confidenceString);
      } catch(Exception exception) {
        errors.add(localize(request, "Confidence \""+confidenceString+"\" is not an integer."));
      }
    }
    if (errors.size() > 0) return failureResult(errors);
    int tagCount = store.tagMatchingAnnotations(expression, layerId, label, confidence);
    return successResult(request, tagCount, "Annotations added: {0}", tagCount);
  }      
   
  private static final long serialVersionUID = 1;
} // end of class Store
