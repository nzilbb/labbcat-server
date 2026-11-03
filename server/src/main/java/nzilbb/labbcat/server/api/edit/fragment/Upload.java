//
// Copyright 2004-2026 New Zealand Institute of Language, Brain and Behaviour, 
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

package nzilbb.labbcat.server.api.edit.fragment;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import nzilbb.ag.*;
import nzilbb.ag.ql.QL;
import nzilbb.ag.serialize.GraphDeserializer;
import nzilbb.ag.serialize.SerializationDescriptor;
import nzilbb.ag.serialize.SerializationException;
import nzilbb.ag.serialize.SerializationException;
import nzilbb.ag.serialize.SerializationParametersMissingException;
import nzilbb.ag.serialize.SerializationParametersMissingException;
import nzilbb.ag.serialize.SerializerNotConfiguredException;
import nzilbb.ag.serialize.util.ConfigurationHelper;
import nzilbb.ag.serialize.util.IconHelper;
import nzilbb.ag.serialize.util.NamedStream;
import nzilbb.ag.serialize.util.Utility;
import nzilbb.ag.util.DefaultOffsetGenerator;
import nzilbb.ag.util.Merger;
import nzilbb.ag.util.Normalizer;
import nzilbb.ag.util.ParticipantRenamer;
import nzilbb.configure.Parameter;
import nzilbb.configure.ParameterSet;
import nzilbb.labbcat.server.api.APIRequestHandler;
import nzilbb.labbcat.server.api.RequestParameters;
import nzilbb.labbcat.server.api.RequiredRole;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.util.IO;

/**
 * <tt>/api/edit/fragment/upload[/*]</tt>
 * : Handler for receiving, analysing, and processing one fragment annotation file.
 * <h3 id="POST"> <tt>/api/edit/fragment/upload</tt> </h3>
 * <p> <b> POST </b> method requests start the process by uploading a fragment file,
 * e.g. a Praat TextGrid representing several layers of annotation for a single utterance
 * in a transcript. 
 * <p> The multipart-encoded parameters are:
 *  <dl>
 *   <dt> fragment (or 'uploadfile', for backward compatibility) </dt>
 *       <dd> Fragment annotation file to upload.
 *       The name must include the transcript name, and the time range of the fragment,
 *       e.g. to update layers in interview01.trs for the fragment from 12.345s to 678.910s,
 *       the file should be called something like interview01__12.345-678.910.TextGrid </dd>
 *   <dt> automaticMapping </dt>
 *       <dd> If present and set to "true", this parameter indicates that default parameters
 *       for processing the upload (e.g. mappings from TextGrid tiers to LaBB-CAT layers)
 *       should be automatically applied, and the resulting data should be immediately
 *       saved, instead of using the two-step process of first uploading the file via
 *       POST and then setting parameters via PUT. </dd> 
 *  </dl>
 * <p><b>Output</b>: A JSON-encoded response containing a <q>model</q> with the following
 * attributes:
 *  <dl>
 *   <dt> id </dt>
 *       <dd> A unique identifier for the upload which can be passed into subsequent PUT calls
 *        to {@link #put(String,RequestParameters,Consumer) /api/edit/fragment/upload/...} 
 *        for finalizing the upload parameters. </dd> 
 *   <dt> transcript </dt>
 *       <dd> The name of the transcript, inferred from the fragment file name. </dd> 
 *   <dt> start </dt>
 *       <dd> The start time of the fragment, inferred from the fragment file name. </dd> 
 *   <dt> end </dt>
 *       <dd> The end time of the fragment, inferred from the fragment file name. </dd> 
 *   <dt> parameters </dt>
 *       <dd> An array of parameter objects representing information that's still required
 *        to finalize the upload. These represent parameters that must be passed into a
 *        subsequent PUT call to {@link #put(String,RequestParameters,Consumer) /api/edit/fragment/upload/...}. This subsequent call
 *        must be made to finish the upload process, even if <q>parameters</q> is an empty
 *        array.</dd> 
 *  </dl>
 * <p id="parameters"> The <q>parameters</q> e.g. mappings from tiers to LaBB-CAT layers. 
 * <p> Each parameter may contain the following attributes:
 *  <dl>
 *   <dt> name </dt>
 *       <dd> The name that should be used when specifying the value for the parameter
 *        when PUTting {@link #put(String,RequestParameters,Consumer) /api/edit/fragment/upload/...}. </dd> 
 *   <dt> label </dt>
 *       <dd> A label for the parameter intended for display to the user.</dd> 
 *   <dt> hint </dt>
 *       <dd> A description of the purpose of the parameter, for display to the user.</dd> 
 *   <dt> type </dt>
 *       <dd> The type of the parameter, e.g. <q>String</q>, <q>Double</q>, <q>Integer</q>,  
 *         <q>Boolean</q>.</dd> 
 *   <dt> required </dt>
 *       <dd> <tt>true</tt> if the value must be specified, <tt>false</tt> if it is optional.</dd> 
 *   <dt> value </dt>
 *       <dd> A default value for the parameter.</dd> 
 *   <dt> possibleValues </dt>
 *       <dd> A list of possible values, if the possibilities are limited to a finite set.</dd> 
 *  </dl>
 *
 * <h2 id="PUT"> <tt>/api/edit/fragment/upload/...</tt> </h2>
 * <p> <b> PUT </b> method requests receive parameters required to finish parsing the
 * uploaded fragment file and save changes to LaBB-CAT's annotation graph store.
 * <p> The request method must be <b> PUT </b> and the URL path following
 * <tt>.../upload/</tt> must be the <var>id</var> that was returned by the earlier 
 * <a href="POST">POST</a>. 
 * <p> The URL-encoded parameters should include values for the parameters returned by 
 *  the earlier POST request(e.g. mappings from tiers to LaBB-CAT layers).
 * <p><b>Output</b>: A JSON-encoded response containing a <q>model</q> with the following
 * attributes:
 *  <dl>
 *   <dt> id </dt>
 *       <dd> The upload ID passed in. </dd> 
 *   <dt> transcript </dt>
 *       <dd> The name of the transcript, inferred from the fragment file name. </dd> 
 *   <dt> start </dt>
 *       <dd> The start time of the fragment, inferred from the fragment file name. </dd> 
 *   <dt> end </dt>
 *       <dd> The end time of the fragment, inferred from the fragment file name. </dd> 
 *   <dt> url </dt>
 *       <dd> The URL for the fragment, in the context of the whole transcript. </dd> 
 *  </dl>
 * @author Robert Fromont robert@fromont.net.nz
 */
@RequiredRole("edit")
public class Upload extends APIRequestHandler {
  
  File uploadsDir;
  
  /**
   * Default constructor.
   */
  public Upload() {
    uploadsDir = new File(new File(System.getProperty("java.io.tmpdir")), "LaBB-CAT.Upload");
    if (!uploadsDir.exists()) uploadsDir.mkdir();
  } // end of constructor

  /**
   * The POST method for the servlet.
   * @param requestParameters Request parameter map.
   * @param httpStatus Receives the response status code, in case of error.
   * @return JSON-encoded object representing the response
   */
  public JsonObject post(RequestParameters requestParameters, Consumer<Integer> httpStatus) {
    context.servletLog(
      "POST post " + requestParameters
      + (requestParameters.getFile("fragment") != null?
         requestParameters.getFile("fragment").getPath():
         (requestParameters.getFile("uploadfile") != null?
          requestParameters.getFile("uploadfile").getPath():"(no fragment)")));
    File dir = null;
    boolean automaticMapping = Optional.ofNullable(
      requestParameters.getString("automaticMapping")).orElse("false")
      .equals("true");
    try {
      SqlGraphStoreAdministration store = getStore();
      try {
        if (!hasAccess(store.getConnection())) {
          httpStatus.accept(SC_FORBIDDEN);
          return null;
        }
        
        // generate an ID/directory to save files
        dir = Files.createTempDirectory(uploadsDir.toPath(), "_fragment_").toFile();
        dir.deleteOnExit();
        String id = dir.getName();
        // context.servletLog("POST id " + id); // TODO
        
        Vector<NamedStream> streams = new Vector<NamedStream>();

        // get fragment file
        File uploadedFragment = requestParameters.getFile("fragment");
        if (uploadedFragment == null) {
          uploadedFragment = requestParameters.getFile("uploadfile");
        }
        if (uploadedFragment == null) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No file received.");
        }
        // context.servletLog("POST fragment " + uploadedFragment.getName()); // TODO
        FileNameInterpreter interpreter = new FileNameInterpreter(
          uploadedFragment.getName(), store);
        // context.servletLog("transcript " + interpreter.getTranscriptId() + " start " + interpreter.getStartTime() + " end " + interpreter.getEndTime()); // TODO
        if (interpreter.getEndTime() < 0) {
          uploadedFragment.delete();
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult(
            "File does not represent a fragment: {0}", uploadedFragment.getName());
        }
        
        File fragment = new File(dir, uploadedFragment.getName());
        IO.Rename(uploadedFragment, fragment);
        fragment.deleteOnExit();
        // context.servletLog("POST  now " + fragment.getPath() + " " + fragment.exists()); // TODO
        streams.add(new NamedStream(fragment));
        
        // get the serializer using fragment name
        // context.servletLog("POST fragment " + fragment.getName());
        GraphDeserializer deserializer = store.deserializerForFilesSuffix(
          "."+IO.Extension(fragment));
        if (deserializer == null) {
          IO.RecursivelyDelete​(dir);
          httpStatus.accept(SC_UNSUPPORTED_MEDIA_TYPE);
          return failureResult("No converter installed for: {0}", fragment.getName());
        }
        // configure deserializer
        Schema schema = store.getSchema();
        ParameterSet configuration = new ParameterSet();
        // default values
        deserializer.configure(configuration, schema);
        // load saved ones
        ConfigurationHelper.LoadConfiguration(
          deserializer.getDescriptor(), configuration, store.getSerializersDirectory(),
          schema);
        deserializer.configure(configuration, schema);
        ParameterSet deserializerParameters = deserializer.load(
          streams.toArray(new NamedStream[0]), schema);
        
        JsonObjectBuilder model = Json.createObjectBuilder().add("id", id);
        model.add("transcript", interpreter.getTranscriptId());
        model.add("start", interpreter.getStartTime());
        model.add("end", interpreter.getEndTime());
        // context.servletLog("POST model.id " + id);
        context.servletLog("automaticMapping " + automaticMapping); // TODO
        if (automaticMapping) {
          return finishUpload(
            store, schema, id, fragment, interpreter, deserializer, deserializerParameters,
            httpStatus);
        } else {
          JsonArrayBuilder parameters = Json.createArrayBuilder();
          model.add("parameters", deserializerParameters.toJson());
          // context.servletLog("POST success " + localize("Uploaded: {0}", fragment.getName())); // TODO 
          return successResult(model.build(), "Uploaded: {0}", fragment.getName());
        }
      } finally {
        cacheStore(store);
        if (automaticMapping && dir != null) { // automatic mapping
          // so PUT will never be called, and we should delete the uploaded files
          IO.RecursivelyDelete​(dir);
        }
      }
    } catch(Exception ex) {
      if (dir != null) IO.RecursivelyDelete​(dir);
      try {
        httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      } catch(Exception exception) {}
      context.servletLog("POST Upload.post: unhandled exception: " + ex);
      ex.printStackTrace(System.err);
      return failureResult(ex);
    }
  }

  /**
   * The PUT method for the servlet.
   * @param pathInfo The URL path from which the upload ID can be inferred.
   * @param requestParameters Request parameter map.
   * @param httpStatus Receives the response status code, in case of error.
   * @return JSON-encoded object representing the response
   */
  public JsonObject put(
    String pathInfo, RequestParameters requestParameters, Consumer<Integer> httpStatus) {
    context.servletLog("PUT " + pathInfo);
    
    // get ID/directory of saved files
    if (pathInfo == null || pathInfo.equals("/") || pathInfo.indexOf('/') < 0) {
      // no path component
      httpStatus.accept(SC_BAD_REQUEST);
      return failureResult("No ID specified.");
    }
    String id = pathInfo.substring(pathInfo.lastIndexOf('/') + 1);
    if (id.length() == 0) {
      httpStatus.accept(SC_BAD_REQUEST);
      return failureResult("No ID specified.");
    }        
    if (!id.startsWith("_fragment_")) {
      httpStatus.accept(SC_BAD_REQUEST);
      return failureResult("Invalid ID: {0}", id);
    }        
    // context.servletLog("PUT id " + id);
    
    File dir = null;
    try {
      SqlGraphStoreAdministration store = getStore();
      // context.servletLog("PUT store " + store.getId());
      
      try {
        dir = new File(uploadsDir, id);
        if (!dir.exists()) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("Invalid ID: {0}", id);
        }
        // context.servletLog("PUT merge " + merge);
        
        Vector<NamedStream> streams = new Vector<NamedStream>();

        // get transcript files
        File fragment = null;
        for (File t : dir.listFiles(f->f.isFile())) {
          if (fragment == null) fragment = t;
          streams.add(new NamedStream(t));
        }
        // context.servletLog("PUT fragment " + fragment); // TODO
        if (fragment == null) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No fragments found for: {0}", id);
        }

        // infer transcript ID and fragment bounds from file name
        FileNameInterpreter interpreter = new FileNameInterpreter(
          fragment.getName(), store);
        
        // get the serializer
        GraphDeserializer deserializer = store.deserializerForFilesSuffix(
          "."+IO.Extension(fragment));
        if (deserializer == null) {
          httpStatus.accept(SC_UNSUPPORTED_MEDIA_TYPE);
          return failureResult("No converter installed for: {0}", fragment.getName());
        }
        
        // configure deserializer
        Schema schema = store.getSchema();
        ParameterSet configuration = new ParameterSet();
        // default values
        deserializer.configure(configuration, schema);
        // load saved ones
        ConfigurationHelper.LoadConfiguration(
          deserializer.getDescriptor(), configuration, store.getSerializersDirectory(), schema);
        deserializer.configure(configuration, schema);
        ParameterSet deserializerParameters = deserializer.load(
          streams.toArray(new NamedStream[0]), schema);
        
        // set parameter values from request
        for (String name : deserializerParameters.keySet()) {
          String value = requestParameters.getString(name);
          if (value != null) {
            Parameter parameter = deserializerParameters.get(name); 
            if (parameter.getType().equals(Layer.class)) {
              parameter.setValue(schema.getLayer(value));
            } else {
              parameter.setValue(value);
            }
          }
        } // next deserializer parameter

        return finishUpload(
          store, schema, id, fragment, interpreter, deserializer, deserializerParameters,
          httpStatus);
      } finally {
        // close database etc.
        cacheStore(store);
        // always delete files
        if (dir != null) IO.RecursivelyDelete​(dir);
      }
    } catch(Exception ex) {
      context.servletLog("PUT Exception " + ex);
      if (dir != null) IO.RecursivelyDelete​(dir);
      try {
        httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      } catch(Exception exception) {}
      context.servletLog("PUT Upload.post: unhandled exception: " + ex);
      ex.printStackTrace(System.err);
      return failureResult(ex);
    }
  }
  
  /**
   * The second phase of the upload process, which deserializes the fragment
   * with the given parameters, and saves the result to the graph store.
   * @param httpStatus Receives the response status code, in case of error.
   * @return JSON-encoded object representing the response
   */
  protected JsonObject finishUpload(
    SqlGraphStoreAdministration store, Schema schema, String id, File fragment,
    FileNameInterpreter interpreter, GraphDeserializer deserializer,
    ParameterSet deserializerParameters, Consumer<Integer> httpStatus)
    throws Exception {
    // context.servletLog("finishUpload " + id + " - " + fragment.getName()); // TODO

    try {
      deserializer.setParameters(deserializerParameters);
    } catch (SerializationParametersMissingException x) {
      httpStatus.accept(SC_BAD_REQUEST);
      return failureResult(x);
    }
    
    // deserialize
    Graph[] graphs = deserializer.deserialize();
    // context.servletLog("PUT graphs " + graphs.length);
    // context.servletLog("PUT graph " + graphs[0].getId());
    Vector<String> messages = new Vector<String>();
    Vector<String> errors = new Vector<String>();
    for (String warning : deserializer.getWarnings()) {
      messages.add(warning);
    } // next error
    
    JsonObjectBuilder model = Json.createObjectBuilder().add("id", id);
    
    Graph changed = graphs[0];
    // context.servletLog("changed " + changed); // TODO
    // check for orphans
    Vector<Annotation> orphans = new Vector<Annotation>();
    for (Annotation a : changed.getAnnotationsById().values()) {
      if (a.getParent() == null) orphans.add(a);
    } // next annotation
    if (orphans.size() > 0) {
      StringBuilder message = new StringBuilder();
      for (Annotation o : orphans) {
        if (message.length() == 0) {
          message.append("Invalid fragment: there are annotations with no parent: ");
        } else {
          message.append(",");
        }
        message.append(o.getLayerId());
        message.append(":");
        message.append(o.getLabel());
        message.append(" (");
        message.append(o.getStart().toString());
        message.append("-");
        message.append(o.getEnd().toString());
        message.append(")");
      } // next orphan
      errors.add(message.toString());
    } else {
      // pass warnings on
      for (String warning : deserializer.getWarnings()) {
        messages.add(warning);
      } // next warning
      
      // shift anchors from fragment-relative to graph-relative
      changed.shiftAnchors(interpreter.getStartTime());
      
      // normalize
      Normalizer normalizer = new Normalizer();
      normalizer.transform(changed);
      
      changed.commit();
      
      // load database version of fragment
      Graph original = store.getFragment(
        interpreter.getTranscriptId(),
        interpreter.getStartTime(), interpreter.getEndTime(),
        // all layers:
        store.getLayerIds());
      // context.servletLog("original " + original.getGraph().getId());
      
      // merge the changes into the original
      Merger merger = new Merger(changed);
      
      // ignore any changes to the turn/utterance/transcript layers
      merger.getNoChangeLayers().add(schema.getParticipantLayerId());
      merger.getNoChangeLayers().add(schema.getTurnLayerId());
      merger.getNoChangeLayers().add(schema.getUtteranceLayerId());
      merger.getNoChangeLayers().add(schema.getWordLayerId());
      
      // speakers can't change, so ensure the participant name in the edited version is the same
      // as in the original version
      Annotation participant = original.first(schema.getParticipantLayerId());
      if (participant != null) {
        for (Annotation a : changed.all(schema.getParticipantLayerId())) {
          a.setLabel(participant.getLabel());
        }
        for (Annotation a : changed.all(schema.getTurnLayerId())) {
          a.setLabel(participant.getLabel());
        }
        for (Annotation a : changed.all(schema.getUtteranceLayerId())) {
          a.setLabel(participant.getLabel());
        }
      }
      
      //merger.setDebug(true);
      original.trackChanges();
      merger.transform(original);
      if (merger.getDebug()) messages.addAll(merger.getLog());	
      errors.addAll(merger.getErrors());
      List<Change> changes = original.getChanges();
      // context.servletLog("Changes:"); // TODO
      // for (Change c : changes) {
      //   context.servletLog(c.getObject().toString() + " : " + c.toString()); // TODO
      // }
      // report changes
      TreeMap<String,Integer> layerToChangeCount = new TreeMap<String,Integer>();
      for (Annotation annotation : original.getAnnotationsById().values()) {
        if (annotation.getChanges().size() > 0) {
          if (!layerToChangeCount.containsKey(annotation.getLayerId())) {
            layerToChangeCount.put(annotation.getLayerId(), 0);
          }
          layerToChangeCount.put(
            annotation.getLayerId(), layerToChangeCount.get(annotation.getLayerId())+1);
        } // annotation changed
      } // next annotation
      for (Anchor anchor : original.getAnchors().values()) {
        if (anchor.getChanges().size() > 0) {
          if (!layerToChangeCount.containsKey("anchors")) {
            layerToChangeCount.put("anchors", 0);
          }
          layerToChangeCount.put("anchors", layerToChangeCount.get("anchors") + 1);
        } // annotation changed
      } // next annotation
      for (String key : layerToChangeCount.keySet()) {
        int changeCount = layerToChangeCount.get(key);
        messages.add(key + ": " + changeCount + " change"+(changeCount==1?"":"s"));
      }
      if (layerToChangeCount.size() == 0) {
        messages.add("No changes to save");
      } else {
        // save changes
        store.saveTranscript(original);
      }
      
      model.add("transcript", interpreter.getTranscriptId());
      model.add("start", interpreter.getStartTime());
      model.add("end", interpreter.getEndTime());
      String hash = original.getStartId();
      Annotation[] utterance = original.list(schema.getUtteranceLayerId());
      if (utterance.length > 0) hash = utterance[0].getId();
      model.add(
        "url", store.getId()+"transcript?id="+original.sourceGraph().getId()+"#"+hash);
    }
    
    return successResult(model.build(), messages);
  }

  /** convenience class for interpreting the fragment filename */
  class FileNameInterpreter {
    private final Pattern fragmentPattern = Pattern.compile(
      "^([0-9]+-)?(.+)__([0-9]+)[._]([0-9]+)[-_]([0-9]+)[._]([0-9]+)$");
    
    /** xxxx.ext name passed to the constructor */
    String sFileName;
    /** xxxx.trs interpreted transcript file name */
    String sTranscriptFileName;
    /** Textgrid filename prefix, if any */
    String sPrefix;
    /** Textgrid start time, if the textgrid represents a fragment of a recording */
    String sStartTime;
    /** Textgrid end time, if the textgrid represents a fragment of a recording */
    String sEndTime;
    
    /**
     * Constructor.  Interprets the TextGrid file name and sets the object
     * attributes appropriately. <br>
     * The text grid filename can be of the following formats
     * <ul>
     *  <li><i>tttt</i>.ext</li>
     *  <li><i>tttt</i>_<i>ss.ss</i>_<i>ee.ee</i>.ext</li>
     *  <li><i>pppp</i>-<i>tttt</i>_<i>ss.ss</i>_<i>ee.ee</i>.ext</li>
     *  <li><i>tttt</i>_<i>ss_ss</i>_<i>ee_ee</i>.ext</li>
     *  <li><i>pppp</i>-<i>tttt</i>_<i>ss_ss</i>-<i>ee_ee</i>.ext</li>
     * </ul> 
     * where
     * <ul>
     *  <li><i>tttt</i> = the transcript base name (i.e. the transcript
     *      filename will be <tt>tttt.trs</tt>)</li>
     *  <li><i>ss.ss</i> or <i>ss_ss</i> = the start time of the fragment, in seconds 
     *      relative to the beginning of the entire recording</li>
     *  <li><i>ee.ee</i> o <i>ee_ee</i> = the end time of the fragment, in seconds relative
     *      to the beginning of the entire recording</li>
     *  <li><i>pppp</i> = the filename prefix (which would normally be a 
     *      serial number)</li>
     *  <li><i>.ext</i> = the file extension, e.g. ".TextGrid", ".txt", etc.</li>
     * </ul> 
     * 
     * @param sFileName Filename of textgrid.
     * @param store Graph store for retrieving graph from.
     */
    public FileNameInterpreter(String sFileName, SqlGraphStoreAdministration store)
      throws Exception {
      String sWithoutExtension
        = sFileName.substring(0, sFileName.lastIndexOf('.'))
        // Praat changes " " to "_", but that is important for us in
        // delimiting sync times, so to avoid confusion, we have converted
        // " " to "--".  We convert it back here...
        .replaceAll("--", " ");
      // provisional transcript name
      sTranscriptFileName = sFileName;
      
      Graph graph = null;
      
      Matcher matcher = fragmentPattern.matcher(sWithoutExtension);
      if (matcher.matches()) {
        String graphName = matcher.group(2);
        graph = store.getGraph(graphName, null);
        
        sStartTime = matcher.group(3) + "." + matcher.group(4);
        sEndTime = matcher.group(5) + "." + matcher.group(6);
        sPrefix = matcher.group(1);
        if (sPrefix != null) sPrefix = sPrefix.replaceAll("-$", ""); // strip off trailing -
      }
      if (graph != null) {
        sTranscriptFileName = graph.getId();
      }
    }
    
    /**
     * The name of the transcript.
     * @return The name of the transcript.
     */
    public String getTranscriptId() {
      return sTranscriptFileName;
    } // end of getTranscriptId()
    
    /** The start time as a double, or -1.0 if unset */
    public double getStartTime() {
      try {
        return Double.parseDouble(sStartTime);
      } catch(Throwable exception) {
        return -1.0;
      }
    }
    
    /** The end time as a double, or -1.0 if unset */
    public double getEndTime() {
      try {
        return Double.parseDouble(sEndTime);
      } catch(Throwable exception) {
        return -1.0;
      }
    }
  }

  /**
   * The DELETE method for the servlet.
   * @param pathInfo The URL path from which the upload ID can be inferred.
   * @param httpStatus Receives the response status code, in case of error.
   * @return JSON-encoded object representing the response
   */
  public JsonObject delete(
    String pathInfo, Consumer<Integer> httpStatus) {
    context.servletLog("delete " + pathInfo);
    
    // get ID/directory of saved files
    if (pathInfo == null || pathInfo.equals("/") || pathInfo.indexOf('/') < 0) {
      // no path component
      httpStatus.accept(SC_BAD_REQUEST);
      return failureResult("No ID specified.");
    }
    String id = pathInfo.substring(pathInfo.lastIndexOf('/') + 1);
    if (id.length() == 0) {
      httpStatus.accept(SC_BAD_REQUEST);
      return failureResult("No ID specified.");
    }        
    if (!id.startsWith("_fragment_")) {
      httpStatus.accept(SC_BAD_REQUEST);
      return failureResult("Invalid ID: {0}", id);
    }        
    // context.servletLog("id " + id);
    File dir = null;
    try {
      SqlGraphStoreAdministration store = getStore();
      // context.servletLog("store " + store.getId());
      try {
        dir = new File(uploadsDir, id);
        if (!dir.exists()) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("Invalid ID: {0}", id);
        }
        
        IO.RecursivelyDelete​(dir);
        return successResult(null, "Upload deleted: {0}");
      } finally {
        cacheStore(store);
      }
    } catch(Exception ex) {
      context.servletLog("Exception " + ex);
      if (dir != null) IO.RecursivelyDelete​(dir);
      try {
        httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      } catch(Exception exception) {}
      context.servletLog("Upload.post: unhandled exception: " + ex);
      ex.printStackTrace(System.err);
      return failureResult(ex);
    }
  } // end of delete    

} // end of class Upload
