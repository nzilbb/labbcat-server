//
// Copyright 2025 New Zealand Institute of Language, Brain and Behaviour, 
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

package nzilbb.labbcat.server.api.edit.transcript;

import java.io.File;
import java.util.Vector;
import java.util.function.Consumer;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import nzilbb.ag.*;
import nzilbb.ag.serialize.GraphDeserializer;
import nzilbb.ag.serialize.SerializationDescriptor;
import nzilbb.ag.serialize.SerializationException;
import nzilbb.ag.serialize.SerializationParametersMissingException;
import nzilbb.ag.serialize.SerializerNotConfiguredException;
import nzilbb.ag.serialize.json.JSONSerialization;
import nzilbb.ag.serialize.util.ConfigurationHelper;
import nzilbb.ag.serialize.util.IconHelper;
import nzilbb.ag.serialize.util.NamedStream;
import nzilbb.ag.serialize.util.Utility;
import nzilbb.util.IO;
import nzilbb.configure.Parameter;
import nzilbb.configure.ParameterSet;
import nzilbb.labbcat.server.api.APIRequestHandler;
import nzilbb.labbcat.server.api.RequestParameters;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;


/**
 * <tt>/api/edit/transcript/save</tt>
 * : Handler for requests that finalize handling of a prior request to
 *   {@link /api/edit/transcript/upload Upload}.
 * This receives parameters required to finish parsing the uploaded transcript file and
 * save changes to LaBB-CAT's annotation graph store.
 * It may be the case that, for a single <tt>upload</tt>, multiple calls to <tt>save</tt>
 * are required, depending on what information is required during finalization; e.g. after
 * specifying serialization parameters, LaBB-CAT may still want information about who the
 * main participant is (but may not because the transcript specifies it, or there's only
 * one participant). A thread ID may be returned with parameters that are still required;
 * this means that although further information can be supplied, there is sufficient
 * information to merge/add the transcript and generate annotation layers, etc.
 * <p> The request method must be <b> POST </b>
 * <p> The URL-encoded parameters must include:
 *  <dl>
 *   <dt> id </dt>
 *       <dd> The upload identifier returned by {@link /api/edit/transcript/upload Upload}. </dd>
 *  </dl>
 * <p> The parameters should also include values for the parameters returned by 
 *  {@link /api/edit/transcript/upload Upload}. These may include both information
 * required by the format  deserializer (e.g. mappings from tiers to LaBB-CAT layers) 
 * and also general information required by LaBB-CAT, such as:
 *  <dl>
 *   <dt> labbcat_corpus </dt>
 *       <dd> The corpus the new transcript(s) belong(s) to. </dd> 
 *   <dt> labbcat_episode </dt>
 *       <dd> The episode the new transcript(s) belong(s) to. </dd> 
 *   <dt> labbcat_transcript_type </dt>
 *       <dd> The transcript type for the new transcript(s). </dd> 
 *  </dl>
 * <p><b>Output</b>: A JSON-encoded response containing a <q>model</q> with the following
 * attributes:
 *  <dl>
 *   <dt> id </dt>
 *       <dd> The upload ID passed in, which can be passed into subsequent calls if necessary. </dd> 
 *   <dt> parameters </dt>
 *       <dd> An array of parameter objects representing information that's still required
 *        to finalize the upload. These represent parameters that must be passed into a
 *        subsequent call to {@link /api/edit/transcript/save Save}. This subsequent call
 *        must be made to finish the upload process, even if <q>parameters</q> is an empty
 *        array.</dd> 
 *   <dt> transcripts </dt>
 *       <dd> An object for which each attribute key is the name of a transcript, with the
 *        value set to the taskId of layer generation, for passing into subsequent calls
 *        to <i>thread</i>.</dd> 
 *  </dl>
 * <p> The <q>parameters</q> returned may include both information required by the format
 * deserializer (e.g. mappings from tiers to LaBB-CAT layers) and also general information
 * required by LaBB-CAT (e.g. the corpus, episode, and type of the transcript).
 * <p> Each parameter may contain the following attributes:
 *  <dl>
 *   <dt> name </dt>
 *       <dd> The name that should be used when specifying the value for the parameter
 *        when calling {@link /api/edit/transcript/save Save}. </dd> 
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
 *       <dd> A list of possibe values, if the possibilities are limited to a finite set.</dd> 
 *  </dl>
 * @author Robert Fromont robert@fromont.net.nz
 */
public class Save extends APIRequestHandler {
  
  /**
   * Default constructor.
   */
  public Save() {
  } // end of constructor

  /**
   * The POST method for the servlet.
   * @param requestParameters Request parameter map.
   * @param fileName Receives the filename for specification in the response headers.
   * @param httpStatus Receives the response status code, in case or error.
   * @return JSON-encoded object representing the response
   */
  public JsonObject post(RequestParameters requestParameters, Consumer<Integer> httpStatus) {
    context.servletLog("post " + requestParameters);
    File dir = null;
    try {
      SqlGraphStoreAdministration store = getStore();
      context.servletLog("store " + store.getId());
      try {

        // get ID/directory of saved files
        String id = requestParameters.getString("id");
        context.servletLog("id " + id);
        if (id == null || id.length() == 0) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No ID specified.");
        }        
        boolean merge = id.startsWith("_merge_");
        dir = new File(store.getFiles(), id);
        if (!dir.exists()) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("Invalid ID: {0}", id);
        }
        context.servletLog("merge " + merge);

        Vector<NamedStream> streams = new Vector<NamedStream>();

        // get transcript files
        File transcript = null;
        for (File t : dir.listFiles(f->f.isFile())) {
          if (transcript == null) transcript = t;
          streams.add(new NamedStream(t));
        }
        context.servletLog("transcript " + transcript);
        if (transcript == null) {
          // TODO are we setting main speakers?
          IO.RecursivelyDelete​(dir);
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No transcripts found for: {0}", id);
        }

        // get media files in directories named after their parameters
        for (File mediaDir : dir.listFiles(f->f.isDirectory())) {
          String trackSuffix = mediaDir.getName().substring(5);
          for (File media : mediaDir.listFiles(f->f.isFile())) {
            context.servletLog("media " + media);
            streams.add(new NamedStream(media));
          } // next media file
        } // next track 
        
        // get the serializer
        GraphDeserializer deserializer = store.deserializerForFilesSuffix(
          "."+IO.Extension(transcript));
        if (deserializer == null) {
          IO.RecursivelyDelete​(dir);
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No converter installed for: {0}", transcript.getName());
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
          if (value != null) deserializerParameters.get(name).setValue(value);
        } // next deserializer parameter
        try {
          deserializer.setParameters(deserializerParameters);
        } catch (SerializationParametersMissingException x) {
          if (dir != null) IO.RecursivelyDelete​(dir);
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult(x);
        }

        // deserialize
        Graph[] graphs = deserializer.deserialize();
        context.servletLog("graphs " + graphs.length);
        context.servletLog("graph " + graphs[0].getId());
        Vector<String> messages = new Vector<String>();
        messages.add(localize("Saved: {0}", transcript.getName())); // TODO i18n
        for (String warning : deserializer.getWarnings()) {
          messages.add(warning);
        } // next error
        
        JsonObjectBuilder model = Json.createObjectBuilder().add("id", id);

        // TODO check ID

        // TODO check existence

        // TODO set corpus/episode/type

        // TODO structure standardization

        // TODO merge

        // TODO report changes

        // TODO check participants, set their corpora

        // TODO save

        // TODO save media

        // TODO generate missing media

        // TODO generate layers

        // TODO ask for main participant settings?

        // TODO delete temporary files

        //model.add("parameters", parameters.toJson());
        return successResult(model.build(), messages);
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
  }

} // end of class Save
