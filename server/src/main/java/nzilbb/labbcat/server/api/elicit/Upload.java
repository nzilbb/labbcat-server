//
// Copyright 2026 New Zealand Institute of Language, Brain and Behaviour, 
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

package nzilbb.labbcat.server.api.elicit;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
import nzilbb.labbcat.server.api.RequiredRole;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.util.IO;

/**
 * <tt>/api/elicit/upload</tt>
 * : Handler for adding an elicited transcript file with associated media/document files.
 *   <dl>
 *    <dt> POST </dt><dd> This is the only supported method. 
 *     <ul>
 *      <li><em> Request Body </em> - A multi-part encoded request with the following
 *          parameters:
 *  <dl>
 *   <dt> transcript_type </dt> <dd> The transcript type for the new transcript. </dd>
 *   <dt> corpus </dt> <dd> The corpus to add the transcript to. </dd>
 *   <dt> episode </dt> <dd> The elicitation episode the transcript belongs to. </dd>
 *   <dt> transcript </dt>
 *       <dd> Transcript file to upload, which must be a plain-text file with a file name
 *        ending in <q>.txt</q>. (<q>transcript</q> is nominally the parameter to use, but
 *        any .txt file parameter will be taken to be the transcript, regardless of the
 *        name of the parameter.) </dd>
 *   <dt> media </dt>
 *       <dd> Recording associated with the transcript, if any. If specified, this
 *        must be an <q>audio/wav</q> file with a file name ending in <q>.wav</q>.
 *        (<q>media</q> is nominally the parameter to use, but any .wav file parameter
 *        will be taken to be the media file, regardless of the name of the parameter.) </dd>
 *   <dt> doc </dt>
 *       <dd> Document associated with the transcript, if any. If specified, this
 *        must be an <q>application/pdf</q> file with a file name ending in <q>.pdf</q>.
 *        (<q>doc</q> is nominally the parameter to use, but any .pdf file parameter
 *        will be taken to be the document file, regardless of the name of the parameter.) </dd>
 *  </dl></li>
 *      <li><em> Response Body </em> - a JSON-encoded reponse of the following structure:
 * <pre>{
 *    "title":"Elicited upload",
 *    "version":"20260428.1901",
 *    "code":0,
 *    "errors":[],
 *    "messages":[],
 *    "model":{}
 *}</pre>
 *      The <q>messages</q> and <q>errors</q> arrays may contain one or more messages for
 *      the client.
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The upload was nominally successful (but should be
 *             confirmed with a call the the <tt>verify</tt> endpoint.</li>
 *         <li><em> 400 </em> : The upload request was invalid, e.g. no transcript was
 *             received.</li>
 *         <li><em> 415 </em> : Files of unsupported media types were received.</li>
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *   </dl>
 * @author Robert Fromont robert@fromont.net.nz
 */
public class Upload extends APIRequestHandler {

  File uploadsDir;
  
  /**
   * Default constructor.
   */
  public Upload() {
    uploadsDir = new File(new File(System.getProperty("java.io.tmpdir")), "Elicitit.Upload");
    if (!uploadsDir.exists()) uploadsDir.mkdir();
  } // end of constructor
  
  /**
   * The POST method for the servlet.
   * @param requestParameters Request parameter map.
   * @param httpStatus Receives the response status code, in case of error.
   * @param layerGenerator A function that will start a layer generation thread for the
   * given transcript, and return the thread ID.
   * @return JSON-encoded object representing the response
   */
  public JsonObject post(RequestParameters requestParameters, Consumer<Integer> httpStatus,
    Function<Graph,String> layerGenerator) {
    context.servletLog(
      "POST post " + requestParameters
      + (requestParameters.getFile("transcript") != null?
         requestParameters.getFile("transcript").getPath():"(no transcript)"));
    File dir = null;
    try {
      SqlGraphStoreAdministration store = getStore();
      try {
        Vector<NamedStream> streams = new Vector<NamedStream>();
        
        // parameters
        List<File> allFiles = requestParameters.getAllFiles();
        try {
          String transcript_type = requestParameters.getString("transcript_type");
          String corpus = requestParameters.getString("corpus");
          String episode = requestParameters.getString("episode");
          File transcript = requestParameters.getFile("transcript");
          if (transcript == null) { // no transcript parameter
            // use the first .txt file
            for (File f : allFiles) {
              if (f.getName().toLowerCase().endsWith(".txt")) {
                transcript = f;
                break;
              }
            }
          }
          File wav = requestParameters.getFile("media");
          if (wav == null) { // no media parameter
            // use the first .wav file
            for (File f : allFiles) {
              if (f.getName().toLowerCase().endsWith(".wav")) {
                wav = f;
                break;
              }
            }
          }
          File doc = requestParameters.getFile("doc");
          if (doc == null) { // no doc parameter
            // use the first .pdf file
            for (File f : allFiles) {
              if (f.getName().toLowerCase().endsWith(".pdf")) {
                doc = f;
                break;
              }
            }
          }
          
          // validation
          if (transcript == null) {
            httpStatus.accept(SC_BAD_REQUEST);
            return failureResult("No file received.");
          }
          if (!transcript.getName().toLowerCase().endsWith(".txt")) {
            httpStatus.accept(SC_UNSUPPORTED_MEDIA_TYPE);
            return failureResult("Invalid type: {0}", transcript.getName());
          }
          streams.add(new NamedStream(transcript));
          if (wav != null) {
            String wavError = validateWav(wav);
            if (wavError != null) {
              httpStatus.accept(SC_UNSUPPORTED_MEDIA_TYPE);
              return failureResult(wavError);
            }
            streams.add(new NamedStream(wav));
          }
          if (doc != null) {
            if (!doc.getName().toLowerCase().endsWith(".pdf")) {
              httpStatus.accept(SC_UNSUPPORTED_MEDIA_TYPE);
              return failureResult("Invalid type: {0}", doc.getName());
            }
            streams.add(new NamedStream(doc));
          }
          
          // get the serializer using  transcript name
          // context.servletLog("POST main transcript " + transcript.getName());
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
            deserializer.getDescriptor(), configuration, store.getSerializersDirectory(),
            schema);
          deserializer.configure(configuration, schema);
          
          // parameters relating to this upload
          ParameterSet deserializerParameters = deserializer.load(
            streams.toArray(new NamedStream[0]), schema);
          try { // use default values
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
          Graph graph = graphs[0]; // only one graph for txt files
          graph.trackChanges();
          
          // check existence
          String regexpSafeID = QL.Esc(IO.WithoutExtension(graph.getId()))
            // escape regexp special characters
            .replaceAll("([/\\[\\]()?.])","\\\\$1");
          boolean existingTranscript = store.countMatchingTranscriptIds​(
            "/^"+regexpSafeID+"\\.[^.]+$/.test(id)") > 0;
          // context.servletLog("PUT existingTranscript " + existingTranscript + " \"/^"+regexpSafeID+"\\.[^.]+$/.test(id)\"");
          if (existingTranscript) {
            httpStatus.accept(SC_BAD_REQUEST);
            return failureResult(messages, "Transcript already exists: {0}", graph.getId());
          }

          // set corpus
          String corpusParameter = Optional.ofNullable(corpus).orElse("");
          if (corpus == null || corpus.length() == 0) { //... or the first corpus
            corpus = store.getCorpusIds()[0];
          }
          String corpusForParticipants = corpus;
          if (graph.getLayer(schema.getCorpusLayerId()) == null) {
            graph.addLayer(store.getLayer(schema.getCorpusLayerId()));
            graph.getSchema().setCorpusLayerId(schema.getCorpusLayerId());
          }
          Annotation corpusAttribute = graph.first(schema.getCorpusLayerId());
          // context.servletLog("PUT corpus " + corpus);
          if (corpusAttribute == null) { // create annotation
            graph.createTag(graph, schema.getCorpusLayerId(), corpus);
          } else { // transcript has it's own corpus already
            // only update it if we're explicitly given an corpus
            if (corpusParameter.length() > 0) {
              corpusAttribute.setLabel(corpusParameter);
            }
          }
          
          // set episode
          String episodeParameter = Optional.ofNullable(episode).orElse("");
          if (episode == null || episode.length() == 0) {
            episode = IO.WithoutExtension(transcript.getName());
          }
          if (graph.getLayer(schema.getEpisodeLayerId()) == null) {
            graph.addLayer(store.getLayer(schema.getEpisodeLayerId()));
            graph.getSchema().setEpisodeLayerId(schema.getEpisodeLayerId());
          }
          Annotation episodeAttribute = graph.first(schema.getEpisodeLayerId());
          // context.servletLog("PUT episode " + episode);
          if (episodeAttribute == null) { // create annotation
            graph.createTag(graph, schema.getEpisodeLayerId(), episode);
          } else { // transcript has it's own episode already
            // only update it if we're explicitly given an episode
            if (episodeParameter.length() > 0) {
              episodeAttribute.setLabel(episodeParameter);
            }
          }		   
          
          // set transcript type
          String transcriptTypeParameter = Optional.ofNullable(transcript_type).orElse("");
          if (transcript_type == null || transcript_type.length() == 0) { //... or the first transcript_type
            transcript_type = store.getLayer("transcript_type").getValidLabels()
              .keySet().iterator().next();
          }
          if (graph.getLayer("transcript_type") == null) {
            graph.addLayer(store.getLayer("transcript_type"));
          }
          Annotation transcriptTypeAttribute = graph.first("transcript_type");
          // context.servletLog("PUT transcriptType " + transcriptType);
          if (transcriptTypeAttribute == null) { // create annotation
            graph.createTag(graph, "transcript_type", transcript_type);
          } else { // transcript has it's own transcript_type already
            // only update it if we're explicitly given an transcript_type
            if (transcriptTypeParameter.length() > 0) {
              transcriptTypeAttribute.setLabel(transcriptTypeParameter);
            }
          }
          
          // structure standardization
          new DefaultOffsetGenerator().transform(graph);
          graph.commit();
          
          // check participant IDs, set main participant(s)
          // context.servletLog("PUT processParticipants...");
          processParticipants(
            graph, graph.first(schema.getEpisodeLayerId()).getLabel(), store, schema,
            transcript.getName());
          
          // mark for creation
          graph.create();
          
          store.saveTranscript(graph);
          // context.servletLog("PUT transcript saved");
          if (corpusForParticipants != null) { // set corpus of participants
            String[] corpusOnly = { schema.getCorpusLayerId() };
            final String corpusLabel = corpusForParticipants;
            for (Annotation participant : graph.all(schema.getParticipantLayerId())) {
              Annotation p = store.getParticipant(participant.getId(), corpusOnly);
              if (!p.getAnnotations(schema.getCorpusLayerId())
                  .stream()
                  .filter(c->c.getLabel().equals(corpusLabel))
                  .findAny()
                  .isPresent()) { // not already there, so add it
                p.addAnnotation(new Annotation(
                                  null, corpusForParticipants, schema.getCorpusLayerId()))
                  .create();
                store.saveParticipant(p);
                // context.servletLog("Saved participant corpus " + p + " " + p.getAnnotations().get("corpus"));
              }
            } // next participant
          }
          // save files
          boolean keepOriginal = !"0".equals(store.getSystemAttribute("keepOriginal"))
            && !"false".equals(store.getSystemAttribute("keepOriginal"));
          if (keepOriginal) {
            // context.servletLog("PUT keepOriginal ");
            // usually there's one transcript file, and it should be the 'source' of the one graph
            // but if there are multiple files, the rest are saved as 'documents',
            // and if there are multiple graphs, *all* transcript files are documents of the first
            try {
              // context.servletLog("PUT source " + file.getName());
              store.saveSource(graph.getId(), transcript.toURI().toString());
            } catch(Exception exception) {
              errors.add(localize("Error saving transcript {0}: {1}",
                                  transcript.toURI().toString(), exception.getMessage()));
            }
            if (doc != null) {
              try {
                // context.servletLog("PUT document " + file.getName());
                store.saveEpisodeDocument(graph.getId(), doc.toURI().toString());
              } catch(Exception exception) {
                errors.add(localize("Error saving document {0}: {1}",
                                    doc.toURI().toString(), exception.getMessage()));
              }
            } // doc uploaded
            if (wav != null) {
              try {
                // context.servletLog("PUT media " + file.getName());
                store.saveMedia(graph.getId(), wav.toURI().toString(), "");
              } catch(Exception exception) {
                errors.add(localize("Error saving media {0}: {1}",
                                    wav.toURI().toString(), exception.getMessage()));
              }
            } // media uploaded
          } // keepOriginal
          
          boolean generateMissingMedia =
            !"0".equals(store.getSystemAttribute("generateMissingMedia"))
            && !"false".equals(store.getSystemAttribute("generateMissingMedia"));
          if (generateMissingMedia) {
            // generate any missing media
            try {
              store.generateMissingMedia(graph.getId());
            } catch(Exception exception) {
              errors.add(localize("Error generating missing media: {0}",
                                  exception.getMessage()));
            }
          } // generateMissingMedia
          
          if (layerGenerator != null) {
            // context.servletLog("PUT generateLayers " + graph.getId());
            layerGenerator.apply(graph);
          }
          
          return successResult(
            Json.createObjectBuilder().build(), "Saved: {0}", transcript.getName());
        } finally { // delete all uploaded file parameters
          for (File uploadFile : allFiles) uploadFile.delete();
        }
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
        // mark everyone who says anything as a main participant
        for (Annotation participant : participants) {
          // do they have any turns?
          if (participant.all(schema.getTurnLayerId()).length > 0) {
            graph.createTag(participant, "main_participant", participant.getLabel());
          }
        }
      }
      graph.commit();
    }
  }

  /**
   * Checks the given file actually appears to be a WAV file.
   * @param wav The file to validate.
   * @return An error if validation fails, null if the WAV file appears to be ok.
   * @throws IOException If the file could not be read.
   * @throws FileNotFoundException If the file doesn't exist.
   */
  public String validateWav(File wav) throws IOException, FileNotFoundException {
    if (!wav.getName().toLowerCase().endsWith(".wav")) {
      return localize("Invalid type: {0}", wav.getName());
    }
    // check it's really wav formatted
    // WAV header is "RIFF"+${file.length()-4)+"WAVE"+"fmt "
    FileInputStream in = new FileInputStream(wav);
    byte[] chunk = new byte[4];
    int read = in.read(chunk);
    if (read != 4) {
      System.err.println(
        "ElicitSpeech.Upload: invalid WAV: could not read first 4 bytes");
      return localize("Media not WAV: {0}", wav.getName());
    }
    if (!"RIFF".equals(new String(chunk))) {
      System.err.println(
        "ElicitSpeech.Upload: invalid WAV: First 4 bytes not RIFF: "
        + new String(chunk));
      return localize("Media not WAV: {0}", wav.getName());
    }
    read = in.read(chunk); // file size - 4
    if (read != 4) {
      System.err.println(
        "ElicitSpeech.Upload: invalid WAV: could not read second 4 bytes");
      return localize("Media not WAV: {0}", wav.getName());
    }
    read = in.read(chunk);
    if (read != 4) {
      System.err.println(
        "ElicitSpeech.Upload: invalid WAV: could not read third 4 bytes");
      return localize("Media not WAV: {0}", wav.getName());
    }
    if (!"WAVE".equals(new String(chunk))) {
      System.err.println(
        "ElicitSpeech.Upload: invalid WAV: Second 4 bytes not WAV: "
        + new String(chunk));
      return localize("Media not WAV: {0}", wav.getName());
    }
    read = in.read(chunk);
    if (read != 4) {
      System.err.println(
        "ElicitSpeech.Upload: invalid WAV: could not read fourth 4 bytes");
      return localize("Media not WAV: {0}", wav.getName());
    }
    if (!"fmt ".equals(new String(chunk))) {
      System.err.println(
        "ElicitSpeech.Upload: invalid WAV: First 4 bytes not \"fmt \": "
        + new String(chunk));
      return localize("Media not WAV: {0}", wav.getName());
    }
    return null;
  } // end of validateWav()
  
} // end of class Upload
