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
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import nzilbb.ag.*;
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
import nzilbb.ag.ql.QL;
import nzilbb.configure.Parameter;
import nzilbb.configure.ParameterSet;
import nzilbb.labbcat.server.api.APIRequestHandler;
import nzilbb.labbcat.server.api.RequestParameters;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.util.IO;

/**
 * <tt>/api/edit/transcript/upload[/*]</tt>
 * : Handler for receiving, analysing, and processing one transcript file with associated
 * media/document files.
 * <h3 id="POST"> <tt>/api/edit/transcript/upload</tt> </h3>
 * <p> <b> POST </b> method requests start the process by uploading a transcript and optionally
 * associated media files. 
 * <p> The multipart-encoded parameters are:
 *  <dl>
 *   <dt> transcript </dt>
 *       <dd> Transcript file to upload. (Normally the file will correspond to a single
 *        transcript, but for some formats a single file will contain multiple transcripts.) </dd>
 *   <dt> media... </dt>
 *       <dd> Media file(s) associated with the transcript(s). 
 *         The parameter can be repeated for multiple files, and the name of the parameter
 *         is <q>media</q> followed by the track suffix; by default there is one track
 *         defined with a suffix of <q></q> (empty string), corresponding to a parameter
 *         name <q>media</q>, but there may be other tracks defined, e.g. there may be a
 *         track with suffix <q>_interviewer</q>, which would correspond to a parameter name
 *         <q>media_interviewer</q>.<br>
 *         See {@link Store#getMediaTracks} for a list of defined track suffixes.
 *       </dd>
 *   <dt> merge </dt>
 *       <dd> If present, this parameter indicates that the upload corresponds to
 *         updates to an existing transcript (or transcripts). If absent, the upload is assumed to
 *         represent (a) new transcript(s). </dd> 
 *  </dl>
 * <p><b>Output</b>: A JSON-encoded response containing a <q>model</q> with the following
 * attributes:
 *  <dl>
 *   <dt> id </dt>
 *       <dd> A unique identifier for the upload which can be passed into subsequent calls
 *        to {@link /api/edit/transcript/save Save} for finalizing the upload parameters. </dd> 
 *   <dt> parameters </dt>
 *       <dd> An array of parameter objects representing information that's still required
 *        to finalize the upload. These represent parameters that must be passed into a
 *        subsequent call to {@link /api/edit/transcript/upload/${id} PUT}. This subsequent call
 *        must be made to finish the upload process, even if <q>parameters</q> is an empty
 *        array.</dd> 
 *  </dl>
 * <p id="parameters"> The <q>parameters</q> returned may include both information
 * required by the format deserializer (e.g. mappings from tiers to LaBB-CAT layers) and
 * also general information required by LaBB-CAT (e.g. the corpus, episode, and type of
 * the transcript). 
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
 *       <dd> A list of possible values, if the possibilities are limited to a finite set.</dd> 
 *  </dl>
 *
 * <h2 id="PUT"> <tt>/api/edit/transcript/upload/...</tt> </h2>
 * <p> <b> PUT </b> method requests receive parameters required to finish parsing the
 * uploaded transcript file and save changes to LaBB-CAT's annotation graph store.
 * It may be the case that, for a single <a href="#POST">POST</a> request, multiple calls
 * to <tt>PUT</tt> requests are required, depending on what information is required during
 * finalization; e.g. after specifying serialization parameters, LaBB-CAT may still want
 * information about who the main participant is (but may not because the transcript
 * specifies it, or there's only one participant). A thread ID may be returned with
 * parameters that are still required; this means that although further information can be
 * supplied, there is sufficient information to merge/add the transcript and generate
 * annotation layers, etc. 
 * <p> The request method must be <b> PUT </b> and the URL path following
 * <tt>.../upload/</tt> must be the <var>id</var> that was returned by the earlier 
 * <a href="POST">POST</a>. 
 * <p> The URL-encoded parameters should include values for the parameters returned by 
 *  the earlier POST request. These may include both information
 * required by the format deserializer (e.g. mappings from tiers to LaBB-CAT layers) 
 * and also general information required by LaBB-CAT, such as:
 *  <dl>
 *   <dt> labbcat_corpus </dt>
 *       <dd> The corpus the new transcript(s) belong(s) to. </dd> 
 *   <dt> labbcat_episode </dt>
 *       <dd> The episode the new transcript(s) belong(s) to. </dd> 
 *   <dt> labbcat_transcript_type </dt>
 *       <dd> The transcript type for the new transcript(s). </dd> 
 *   <dt> labbcat_generate </dt>
 *       <dd> Whether to re-regenerate layers of automated annotations or not. </dd> 
 *  </dl>
 * <p><b>Output</b>: A JSON-encoded response containing a <q>model</q> with the following
 * attributes:
 *  <dl>
 *   <dt> id </dt>
 *       <dd> The upload ID passed in, which can be passed into subsequent calls if necessary. </dd> 
 *   <dt> parameters </dt>
 *       <dd> An array of parameter objects representing information that's still required
 *        to finalize the upload. These represent parameters that must be passed into a
 *        subsequent PUT request. This subsequent request must be made to finish the
 *        upload process, even if <q>parameters</q> is an empty array.</dd> 
 *   <dt> transcripts </dt>
 *       <dd> An object for which each attribute key is the name of a transcript, with the
 *        value set to the taskId of layer generation, for passing into subsequent calls
 *        to <i>thread</i>.</dd> 
 *  </dl>
 * <p> The <q>parameters</q> returned have the <a href="#parameters">same structure</a> as
 * used by the POST request. 
 * @author Robert Fromont robert@fromont.net.nz
 */
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
   * @param httpStatus Receives the response status code, in case or error.
   * @return JSON-encoded object representing the response
   */
  public JsonObject post(RequestParameters requestParameters, Consumer<Integer> httpStatus) {
    context.servletLog("POST post " + requestParameters.getFile("transcript").getPath());
    File dir = null;
    try {
      SqlGraphStoreAdministration store = getStore();
      try {
        boolean merge = !Optional.ofNullable(requestParameters.getString("merge")).orElse("false")
          .equals("false");
        String dirPrefix = merge?"_merge_":"_new_";
        context.servletLog("POST merge " + merge);
        
        // generate an ID/directory to save files
        dir = Files.createTempDirectory(uploadsDir.toPath(), dirPrefix).toFile();
        dir.deleteOnExit();
        String id = dir.getName();
        context.servletLog("POST id " + id);

        Vector<NamedStream> streams = new Vector<NamedStream>();

        // get transcript file(s)
        Vector<File> uploadedTranscripts = requestParameters.getFiles("transcript");
        context.servletLog("POST transcripts " + uploadedTranscripts.size());
        if (uploadedTranscripts.size() == 0) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No file received.");
        }
        for (File uploadedTranscript : uploadedTranscripts) {
          context.servletLog("POST transcript " + uploadedTranscript.getPath());
          File transcript = new File(dir, uploadedTranscript.getName());
          IO.Rename(uploadedTranscript, transcript);
          transcript.deleteOnExit();
          context.servletLog("POST  now " + transcript.getPath() + " " + transcript.exists());
          streams.add(new NamedStream(transcript));
        }

        // save media files in directories named after their parameters
        for (String parameterName : requestParameters.keySet()) {
          if (parameterName.startsWith("media")) { // media file
            File subdir = new File(dir, parameterName);
            if (!subdir.mkdir()) {
              IO.RecursivelyDelete​(dir);
              httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
              return failureResult("Could not create media directory: {0}", subdir.getPath());
            }
            for (File uploadedMedia : requestParameters.getFiles(parameterName)) {
              File media = new File(subdir, uploadedMedia.getName());
              IO.Rename(uploadedMedia, media);
              media.deleteOnExit();
              streams.add(new NamedStream(media));
            }
          } // media file
        } // next parameter
        
        // get the serializer using first transcript name (there's usually only one anyway)
        File transcript = requestParameters.getFile("transcript");
        context.servletLog("POST main transcript " + transcript.getName());
        GraphDeserializer deserializer = store.deserializerForFilesSuffix(
          "."+IO.Extension(transcript));
        if (deserializer == null) {
          IO.RecursivelyDelete​(dir);
          httpStatus.accept(SC_UNSUPPORTED_MEDIA_TYPE);
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

        JsonObjectBuilder model = Json.createObjectBuilder().add("id", id);
        context.servletLog("POST model.id " + id);
        JsonArrayBuilder parameters = Json.createArrayBuilder();
        if (merge) {
          deserializerParameters.addParameter(
            new Parameter(
              "labbcat_generate", Boolean.class, "Generate Annotations",
              "Whether to (re)generate layers of automated annotations", true))
            .setValue(Boolean.TRUE);
        } else { // new transcript
          // ask for corpus, episode, and transcript type
          TreeSet<String> options = new TreeSet<String>() {{
              for (String option : store.getCorpusIds()) add(option);
            }};
          deserializerParameters.addParameter(
            new Parameter(
              "labbcat_corpus", String.class, "Corpus",
              "The broad collection the transcript belongs to", true))
            .setPossibleValues(options)
            .setValue(options.first());
          deserializerParameters.addParameter(
            new Parameter(
              "labbcat_episode", String.class, "Episode",
              "The recording episode the transcript belongs to", true)
            .setValue(IO.WithoutExtension(transcript.getName())));
          options = options = new TreeSet<String>() {{
              for (String option : store.getLayer("transcript_type").getValidLabels().keySet()) {
                add(option);
              }
            }};
          deserializerParameters.addParameter(
            new Parameter(
              "labbcat_transcript_type", String.class, "Transcript Type",
              "What the type of language use is the transcript of", true))
            .setPossibleValues(options)
            .setValue(options.first());
        }
        model.add("parameters", deserializerParameters.toJson());
        context.servletLog("POST success " + localize("Uploaded: {0}", transcript.getName()));
        return successResult(model.build(), "Uploaded: {0}", transcript.getName());
      } finally {
        cacheStore(store);
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
   * @param fileName Receives the filename for specification in the response headers.
   * @param httpStatus Receives the response status code, in case or error.
   * @param layerGenerator A function that will start a layer generation thread for the
   * given transcript, and return the thread ID.
   * @return JSON-encoded object representing the response
   */
  public JsonObject put(
    String pathInfo, RequestParameters requestParameters,Consumer<Integer> httpStatus,
    Function<Graph,String> layerGenerator) {
    context.servletLog("PUT " + pathInfo + " " + requestParameters);
    
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
    context.servletLog("PUT id " + id);
    
    File dir = null;
    try {
      SqlGraphStoreAdministration store = getStore();
      context.servletLog("PUT store " + store.getId());
      boolean keepOriginal = !"0".equals(store.getSystemAttribute("keepOriginal"));
      boolean generateMissingMedia = !"0".equals(store.getSystemAttribute("generateMissingMedia"));
      boolean generateLayers =
        !Optional.ofNullable(requestParameters.getString("labbcat_generate")).orElse("false")
        .equals("false");
      context.servletLog("PUT generateLayers " + generateLayers + " - " + requestParameters);

      try {
        boolean merge = id.startsWith("_merge_");
        dir = new File(uploadsDir, id);
        if (!dir.exists()) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("Invalid ID: {0}", id);
        }
        context.servletLog("PUT merge " + merge);

        Vector<NamedStream> streams = new Vector<NamedStream>();

        // get transcript files
        File transcript = null;
        Vector<File> transcripts = new Vector<File>();
        for (File t : dir.listFiles(f->f.isFile())) {
          if (transcript == null) transcript = t;
          transcripts.add(t);
          streams.add(new NamedStream(t));
        }
        context.servletLog("PUT transcript " + transcript);
        if (transcript == null) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No transcripts found for: {0}", id);
        }

        // get media files in directories named after their parameters
        HashMap<String,File[]> trackSuffixToMediaFiles = new HashMap<String,File[]>();
        for (File mediaDir : dir.listFiles(f->f.isDirectory())) {
          String trackSuffix = mediaDir.getName().substring(5);
          trackSuffixToMediaFiles.put(trackSuffix, mediaDir.listFiles(f->f.isFile()));
          for (File media : trackSuffixToMediaFiles.get(trackSuffix)) {
            context.servletLog("PUT media " + media);
            streams.add(new NamedStream(media));
          } // next media file
        } // next track 
        
        // get the serializer
        GraphDeserializer deserializer = store.deserializerForFilesSuffix(
          "."+IO.Extension(transcript));
        if (deserializer == null) {
          httpStatus.accept(SC_UNSUPPORTED_MEDIA_TYPE);
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
          if (value != null) {
            Parameter parameter = deserializerParameters.get(name); 
            if (parameter.getType().equals(Layer.class)) {
              parameter.setValue(schema.getLayer(value));
            } else {
              parameter.setValue(value);
            }
          }
        } // next deserializer parameter
        try {
          deserializer.setParameters(deserializerParameters);
        } catch (SerializationParametersMissingException x) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult(x);
        }

        // deserialize
        Graph[] graphs = deserializer.deserialize();
        context.servletLog("PUT graphs " + graphs.length);
        context.servletLog("PUT graph " + graphs[0].getId());
        Vector<String> messages = new Vector<String>();
        Vector<String> errors = new Vector<String>();
        for (String warning : deserializer.getWarnings()) {
          messages.add(warning);
        } // next error
        
        JsonObjectBuilder model = Json.createObjectBuilder().add("id", id);
        JsonObjectBuilder transcriptThreads = Json.createObjectBuilder();

        // pass through the graph list twice, once for merging and error checks, then again to save
        
        // for each resulting graph
        for (int g = 0; g < graphs.length; g++) {
          Graph graph = graphs[g];
          context.servletLog("PUT graph " + graph.getId());
          graph.trackChanges();
          if (graph.getId() == null) { // no ID is set
            // use the name of the (first) uploaded file
            if (graphs.length == 1) {
              graph.setId(transcript.getName());
            } else { // use number the IDs
              graph.setId(
                IO.WithoutExtension(transcript.getName())
                + "-" + (g+1)
                + "." + IO.Extension(transcript.getName()));
            }
          } // no ID set
          
          // check existence
          String regexpSafeID = QL.Esc(IO.WithoutExtension(graph.getId()))
            // escape regexp special characters
            .replaceAll("([/\\[\\]()?.])","\\\\$1");
          boolean existingTranscript = store.countMatchingTranscriptIds​(
            "/^"+regexpSafeID+"\\.[^.]+$/.test(id)") > 0;
          context.servletLog("PUT existingTranscript " + existingTranscript + " \"/^"+regexpSafeID+"\\.[^.]+$/.test(id)\"");
          if (!existingTranscript && merge) {
            httpStatus.accept(SC_NOT_FOUND);
            return failureResult(messages, "Transcript not found: {0}", graph.getId());
          } else if (existingTranscript && !merge) {
            httpStatus.accept(SC_BAD_REQUEST);
            return failureResult(messages, "Transcript already exists: {0}", graph.getId());
          }

          if (!existingTranscript) {
            context.servletLog("PUT !existingTranscript");
            
            // set corpus
            String corpusParameter = Optional.ofNullable(
              requestParameters.getString("labbcat_corpus")).orElse("");
            String corpus = corpusParameter; // given corpus...
            if (corpus.length() == 0) { //... or the first corpus
              corpus = store.getCorpusIds()[0];
            }
            if (graph.getLayer(schema.getCorpusLayerId()) == null) {
              graph.addLayer(store.getLayer(schema.getCorpusLayerId()));
              graph.getSchema().setCorpusLayerId(schema.getCorpusLayerId());
            }
            Annotation corpusAttribute = graph.first(schema.getCorpusLayerId());
            context.servletLog("PUT corpus " + corpus);
            if (corpusAttribute == null) { // create annotation
              graph.createTag(graph, schema.getCorpusLayerId(), corpus);
            } else { // transcript has it's own corpus already
              // only update it if we're explicitly given an corpus
              if (corpusParameter.length() > 0) {
                corpusAttribute.setLabel(corpusParameter);
              }
            }		   

            // set episode
            String episodeParameter = Optional.ofNullable( // given episode, or the transcript file name
              requestParameters.getString("labbcat_episode")).orElse("");
            String episode = episodeParameter.length() > 0?
              episodeParameter : IO.WithoutExtension(transcript.getName());
            if (graph.getLayer(schema.getEpisodeLayerId()) == null) {
              graph.addLayer(store.getLayer(schema.getEpisodeLayerId()));
              graph.getSchema().setEpisodeLayerId(schema.getEpisodeLayerId());
            }
            Annotation episodeAttribute = graph.first(schema.getEpisodeLayerId());
            context.servletLog("PUT episode " + episode);
            if (episodeAttribute == null) { // create annotation
              graph.createTag(graph, schema.getEpisodeLayerId(), episode);
            } else { // transcript has it's own episode already
              // only update it if we're explicitly given an episode
              if (episodeParameter.length() > 0) {
                episodeAttribute.setLabel(episodeParameter);
              }
            }		   

            // set transcript type
            String transcriptTypeParameter = Optional.ofNullable(
              requestParameters.getString("labbcat_transcript_type")).orElse("");
            String transcriptType = transcriptTypeParameter; // given transcript_type...
            if (transcriptType.length() == 0) { //... or the first transcript_type
              transcriptType = store.getLayer("transcript_type").getValidLabels()
                .keySet().iterator().next();
            }
            if (graph.getLayer("transcript_type") == null) {
              graph.addLayer(store.getLayer("transcript_type"));
            }
            Annotation transcriptTypeAttribute = graph.first("transcript_type");
            context.servletLog("PUT transcriptType " + transcriptType);
            if (transcriptTypeAttribute == null) { // create annotation
              graph.createTag(graph, "transcript_type", transcriptType);
            } else { // transcript has it's own transcript_type already
              // only update it if we're explicitly given an transcript_type
              if (transcriptTypeParameter.length() > 0) {
                transcriptTypeAttribute.setLabel(transcriptTypeParameter);
              }
            }
          } // not an existing transcript
          
          // structure standardization
          new DefaultOffsetGenerator().transform(graph);
          graph.commit();

          if (merge) {
            context.servletLog("PUT merge...");
            
            Graph newGraph = graph;
            
            // normalize the graph before merge
            if (newGraph.getSchema().getParticipantLayer() != null // (if we have required layers)
                && newGraph.getSchema().getTurnLayer() != null
                && newGraph.getSchema().getUtteranceLayerId() != null) {
              Normalizer normalizer = new Normalizer();
              normalizer.setMinimumTurnPauseLength(
                Double.parseDouble(store.getSystemAttribute("minTurnPause")));
              normalizer.transform(newGraph);
              newGraph.commit();
            }
            if (newGraph.getOffsetGranularity() == null) newGraph.setOffsetGranularity(0.001);
			   
            // existing graph is the "original" version
            graphs[g] = store.getTranscript(graph.getId());
            graph = graphs[g];
            graph.trackChanges();
            context.servletLog("getTrascript " + graph.getId() + " episode " + graph.first("episode"));
            
            // check participant IDs, set main participant(s)
            context.servletLog("PUT processParticipants...");
            processParticipants(
              graph, graph.first(schema.getEpisodeLayerId()).getLabel(), store, schema,
              transcript.getName());

            Merger merger = new Merger(newGraph);
            try { // merge
              //merger.setDebug(true);
              merger.transform(graph);
              Set<Change> changes = graph.getTracker().getChanges();
              if (merger.getDebug()) messages.addAll(merger.getLog());
              
              if (merger.getErrors().size() > 0) {
                errors.add(localize("Could not merge changes into {0}: {1}", graph.getId(), ""));
                errors.addAll(merger.getErrors());
                continue;
              }
              
              // report changes
              int anchorChanges = 0;
              for (Change change : changes) {
                if (change.getObject() instanceof Anchor) {
                  anchorChanges++;
                } else { // Annotation
                  Annotation a = (Annotation)change.getObject();
                  if (change.getOperation() == Change.Operation.Update) {
                    if (!"label".equals(change.getKey())) { // only count label changes
                      continue;
                    }
                  }
                  String operation = "@" + a.getChange();
                  Layer layer = a.getLayer();
                  if (layer.containsKey(operation)) {
                    layer.put(operation, new Integer(((Integer)layer.get(operation)) + 1));
                  } else {
                    layer.put(operation, new Integer(1));
                  }
                }
              } // next change
              for (String layerId : new TreeSet<String>(graph.getSchema().getLayers().keySet())) {
                Layer layer = graph.getLayer(layerId);
                String message = "";
                if (layer.containsKey("@" + Change.Operation.Create)) 
                  message += " " + localize("New: {0,number,integer}",
                                            layer.get("@" + Change.Operation.Create));
                if (layer.containsKey("@" + Change.Operation.Update)) 
                  message += " " + localize("Changed: {0,number,integer}",
                                            layer.get("@" + Change.Operation.Update));
                if (layer.containsKey("@" + Change.Operation.Destroy)) 
                  message += " " + localize("Removed: {0,number,integer}",
                                            layer.get("@" + Change.Operation.Destroy));
                if (message.length() > 1) {
                  messages.add(graph.getId() + ": " + layerId + " -" + message);
                }
              }
              if (anchorChanges > 0) messages.add("Changed offsets: " + anchorChanges);
            } catch(TransformationException exception) {
              if (merger.getDebug()) messages.addAll(merger.getLog());
              errors.add(localize("Could not merge changes into {0}: {1}",
                                  graph.getId(), exception.getMessage()));
            } // merge failed

          } else { // a new transcript
            context.servletLog("PUT new ");
            
            // check participant IDs, set main participant(s)
            context.servletLog("PUT processParticipants...");
            processParticipants(
              graph, graph.first(schema.getEpisodeLayerId()).getLabel(), store, schema,
              transcript.getName());
            
            // mark for creation
            graph.create();
          }

        } // next graph

        if (errors.size() > 0) {
          httpStatus.accept(SC_CONFLICT);
          return failureResult(messages, errors);
        }

        // no errors, so we can save the transcripts
        
        // for each resulting graph
        for (int g = 0; g < graphs.length; g++) {
          Graph graph = graphs[g];
          context.servletLog("PUT ...graph " + graph.getId());

          // save graph into graph store
          store.saveTranscript(graph);
          context.servletLog("PUT transcript saved");
              
          // save files
          if (keepOriginal) {
            context.servletLog("PUT keepOriginal ");
            // usually there's one transcript file, and it should be the 'source' of the one graph
            // but if there are multiple files, the rest are saved as 'documents',
            // and if there are multiple graphs, *all* transcript files are documents of the first
            boolean fileIsSource = graphs.length == 1;
            for (File file : transcripts) {
              if (fileIsSource) {
                try {
                  context.servletLog("PUT source " + file.getName());
                  store.saveSource(graph.getId(), file.toURI().toString());
                } catch(Exception exception) {
                  errors.add(localize("Error saving transcript {0}: {1}",
                                      file.toURI().toString(), exception.getMessage()));
                }
                fileIsSource = false;
              } else {
                try {
                  context.servletLog("PUT document " + file.getName());
                  store.saveEpisodeDocument(graph.getId(), file.toURI().toString());
                } catch(Exception exception) {
                  errors.add(localize("Error saving document {0}: {1}",
                                      file.toURI().toString(), exception.getMessage()));
                }
              }
            } // next transcript
            
            // save media files
            for (String suffix : trackSuffixToMediaFiles.keySet()) {
              for (File file : trackSuffixToMediaFiles.get(suffix)) {
                if (suffix.length() == 0) { // no track
                  // is it media, or a document?
                  MediaFile media = new MediaFile(file);
                  if (media.getMimeType() == null
                      || (!media.getMimeType().startsWith("audio")
                          && !media.getMimeType().startsWith("video")
                          && !media.getMimeType().startsWith("image"))) {
                    try {
                      context.servletLog("PUT document " + file.getName());
                      store.saveEpisodeDocument(graph.getId(), file.toURI().toString());
                    } catch(Exception exception) {
                      errors.add(localize("Error saving document {0}: {1}",
                                          file.toURI().toString(), exception.getMessage()));
                    }
                    break; // don's save as media
                  } // not a known media file
                } // no track suffix
                try {
                  context.servletLog("PUT media " + file.getName());
                  store.saveMedia(graph.getId(), file.toURI().toString(), suffix);
                } catch(Exception exception) {
                  errors.add(localize("Error saving media {0}: {1}",
                                      file.toURI().toString(), exception.getMessage()));
                }
              } // next file
            } // next track
          } // keepOriginal
          
          if (generateMissingMedia) {
            // generate any missing media
            try {
              store.generateMissingMedia(graph.getId());
            } catch(Exception exception) {
              errors.add(localize("Error generating missing media: {0}",
                                  exception.getMessage()));
            }
          } // generateMissingMedia
          
          // generate layers
          if (generateLayers || !merge) { // TODO
            if (layerGenerator != null) {
              context.servletLog("PUT generateLayers " + graph.getId());
              transcriptThreads.add(graph.getId(), layerGenerator.apply(graph));
            }
          }
          
          messages.add(localize("Saved: {0}", graph.getId()));
          
        } // next graph
        model.add("transcripts", transcriptThreads.build());
        
        return successResult(model.build(), messages);
        
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
   * Set main participants and rename participants if required.
   */
  void processParticipants(
    Graph graph, String episode, SqlGraphStoreAdministration store, Schema schema,
    String transcriptName) throws Exception {
    if (graph.getSchema().getParticipantLayerId() != null) {
      // rename generic speakers, and mark default main speakers
      if (graph.getLayer("main_participant") == null) {
        graph.addLayer(store.getLayer("main_participant"));
      }
      boolean needMainSpeakers = graph.all("main_participant").length == 0;
      String regularExpression = store.getSystemAttribute("genericSpeakerRegexp");    
      Pattern genericPattern = null;
      if (regularExpression != null && regularExpression.trim().length() > 0) {
        genericPattern = Pattern.compile(regularExpression);
      } // generic speaker pattern defined
      // for each participant
      Annotation[] participants = graph.list(schema.getParticipantLayerId());
      for (Annotation participant : participants) {
        // does the participant have a 'generic' name?
        if (genericPattern != null
            && genericPattern.matcher(participant.getLabel()).matches()) {
          // rename the participant to something more specific
          String oldName = participant.getLabel();
          new ParticipantRenamer(
            participant.getLabel(), participant.getLabel() + " " + episode)
            .transform(graph);
        }
        if (needMainSpeakers) {
          // is the participant's name in the transcript name?
          if (graph.getId().replaceAll("\\.[a-zA-Z][^.]*$","").toLowerCase().replaceAll(" ","")
              .indexOf(participant.getLabel().toLowerCase().replaceAll(" ","")) >= 0) {
            // mark it as a main participant
            graph.createTag(participant, "main_participant", participant.getLabel());
          }
        }
      } // next participant
      // if nobody has been marked as a main participant
      if (graph.list("main_participant").length == 0) {
        // mark everyone as a main participant
        for (Annotation participant : participants) {
          graph.createTag(participant, "main_participant", participant.getLabel());
        }
      }
      graph.commit();
    }
  }

  /**
   * The DELETE method for the servlet.
   * @param pathInfo The URL path from which the upload ID can be inferred.
   * @param requestParameters Request parameter map.
   * @param fileName Receives the filename for specification in the response headers.
   * @param httpStatus Receives the response status code, in case or error.
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
    context.servletLog("id " + id);
    File dir = null;
    try {
      SqlGraphStoreAdministration store = getStore();
      context.servletLog("store " + store.getId());
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
