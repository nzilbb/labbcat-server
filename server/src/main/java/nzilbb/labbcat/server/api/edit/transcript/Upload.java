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
import java.util.TreeSet;
import java.util.Optional;
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
import nzilbb.ag.serialize.SerializationException;
import nzilbb.ag.serialize.SerializationParametersMissingException;
import nzilbb.ag.serialize.SerializationParametersMissingException;
import nzilbb.ag.serialize.SerializerNotConfiguredException;
import nzilbb.ag.serialize.util.ConfigurationHelper;
import nzilbb.ag.serialize.util.IconHelper;
import nzilbb.ag.serialize.util.NamedStream;
import nzilbb.ag.serialize.util.Utility;
import nzilbb.configure.Parameter;
import nzilbb.configure.ParameterSet;
import nzilbb.labbcat.server.api.APIRequestHandler;
import nzilbb.labbcat.server.api.RequestParameters;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.util.IO;

/**
 * <tt>/api/edit/transcript/upload</tt>
 * : Handler for receiving and analysing one transcript file with associated
 * media/document files.
 * <p> The request method must be <b> POST </b>
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
 *        subsequent call to {@link /api/edit/transcript/save Save}. This subsequent call
 *        must be made to finish the upload process, even if <q>parameters</q> is an empty
 *        array.</dd> 
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
 *       <dd> A list of possible values, if the possibilities are limited to a finite set.</dd> 
 *  </dl>
 * @author Robert Fromont robert@fromont.net.nz
 */
public class Upload extends APIRequestHandler {
  
  /**
   * Default constructor.
   */
  public Upload() {
  } // end of constructor

  /**
   * The POST method for the servlet.
   * @param requestParameters Request parameter map.
   * @param httpStatus Receives the response status code, in case or error.
   * @return JSON-encoded object representing the response
   */
  public JsonObject post(RequestParameters requestParameters, Consumer<Integer> httpStatus) {
    context.servletLog("post " + requestParameters.getFile("transcript").getPath());
    File dir = null;
    try {
      SqlGraphStoreAdministration store = getStore();
      try {
        boolean merge = !Optional.ofNullable(requestParameters.getString("merge")).orElse("false")
          .equals("false");
        String dirPrefix = merge?"_merge_":"_new_";
        context.servletLog("merge " + merge);
        
        // generate an ID/directory to save files
        dir = Files.createTempDirectory(store.getFiles().toPath(), dirPrefix).toFile();
        dir.deleteOnExit();
        String id = dir.getName();
        context.servletLog("id " + id);

        Vector<NamedStream> streams = new Vector<NamedStream>();

        // get transcript file(s)
        Vector<File> uploadedTranscripts = requestParameters.getFiles("transcript");
        context.servletLog("transcripts " + uploadedTranscripts.size());
        if (uploadedTranscripts.size() == 0) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No file received.");
        }
        for (File uploadedTranscript : uploadedTranscripts) {
          context.servletLog("transcript " + uploadedTranscript.getPath());
          File transcript = new File(dir, uploadedTranscript.getName());
          IO.Rename(uploadedTranscript, transcript);
          context.servletLog(" now " + transcript.getPath() + " " + transcript.exists());
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
              streams.add(new NamedStream(media));
            }
          } // media file
        } // next parameter
        
        // get the serializer using first transcript name (there's usually only one anyway)
        File transcript = requestParameters.getFile("transcript");
        context.servletLog("main transcript " + transcript.getName());
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

        JsonObjectBuilder model = Json.createObjectBuilder().add("id", id);
        context.servletLog("model.id " + id);
        JsonArrayBuilder parameters = Json.createArrayBuilder();
        if (!merge) {
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
        context.servletLog("success " + localize("Uploaded: {0}", transcript.getName()));
        return successResult(model.build(), "Uploaded: {0}", transcript.getName());
      } finally {
        cacheStore(store);
      }
    } catch(Exception ex) {
      if (dir != null) IO.RecursivelyDelete​(dir);
      try {
        httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      } catch(Exception exception) {}
      context.servletLog("Upload.post: unhandled exception: " + ex);
      ex.printStackTrace(System.err);
      return failureResult(ex);
    }
  }

} // end of class Upload
