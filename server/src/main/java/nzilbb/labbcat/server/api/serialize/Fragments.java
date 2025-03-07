//
// Copyright 2017-2025 New Zealand Institute of Language, Brain and Behaviour, 
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

package nzilbb.labbcat.server.api.serialize;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import nzilbb.ag.Graph;
import nzilbb.ag.GraphStoreAdministration;
import nzilbb.ag.Schema;
import nzilbb.ag.serialize.GraphSerializer;
import nzilbb.ag.serialize.SerializationException;
import nzilbb.ag.serialize.util.ConfigurationHelper;
import nzilbb.ag.serialize.util.NamedStream;
import nzilbb.ag.serialize.util.Utility;
import nzilbb.configure.ParameterSet;
import nzilbb.labbcat.server.db.ConsolidatedGraphSeries;
import nzilbb.labbcat.server.db.FragmentSeries;
import nzilbb.labbcat.server.db.ResultSeries;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.labbcat.server.db.SqlSearchResults;
import nzilbb.labbcat.server.search.SearchTask;
import nzilbb.labbcat.server.task.SerializeFragmentsTask;
import nzilbb.labbcat.server.task.Task;
import nzilbb.util.IO;
import nzilbb.util.MonitorableSeries;
import nzilbb.labbcat.server.api.APIRequestHandler;
import nzilbb.labbcat.server.api.RequestParameters;

/**
 * <tt>/api/serialize/fragments</tt>
 * : Converts transcript fragments to specific formats.
 *  <p> Converts parts of transcripts to annotation file formats. 
 *  <p> The request method can be <b> GET </b> or <b> POST </b>
 * The servlet expects an array of graph <i>id</i>s, <i>start</i> times and <i>end</i> times, 
 * a list of <i>layerId</i>s in include, and a <i>mimetype</i>.
 * <p><b>Input HTTP parameters</b>:
 * <ul>
 *  <li><i>mimeType</i> - content-type of the format to serialize to. </li>
 *  <li><i>layerId</i> - a list of layer IDs to include in the serialization. </li>
 *  <li><i>id</i> - one or more graph IDs. </li>
 *  <li><i>start</i> - one or more start times (in seconds).</li>
 *  <li><i>end</i> - one or more end times (in seconds).</li>
 *  <li><i>filter</i> - (optional) one or more annotation IDs to filter by. e.g. a turn
 * annotation ID, which would ensure that only words within the specified turn are
 * included, not words from other turns.</li>
 *  <li><i>threadId</i> - (optional) The search task ID returned by a previous call to
 *      <tt>/api/search</tt>.</li>
 *  <li><i>utterance</i> - (optional) MatchIds for the selected results to return, if only
 *      a subset is required. This can be specifed instead of id/start/end parameters.
 *      This parameter is specified multiple times for multiple values.</li> 
 *  <li><i>name</i> - (optional) name of the collection.</li>
 *  <li><i>prefix</i> - (optional) prefix fragment names with a numeric serial number.</li>
 *  <li><i>tag</i> - (optional) add a tag identifying the target annotation.</li>
 *  <li><i>async</i> - (optional) "true" to start a serialization server task and
 *      immediately it's <var>threadId</var> rather than return the actual serialization
 *      results. </li>
 * </ul>
 * <br><b>Output</b>: if <i>async</i> is ommited, the result is each of the transcript
 * fragments  specified by the input parameters converted to the given  format. This may
 * be a single file or multiple files, depending on the converter behaviour and how many
 * fragments are specified. If there is only one, the file in returned as the response to
 * the request.  If there are more than one, the response is a zip file containing the
 * output files.  
 * <br> If <i>async</i> == true, the result is a JSON-encoded response object of the usual
 * structure for which the "model" is an object with a "threadId" attribute, which is the
 * ID of the server task to monitor for results. e.g.
 * <pre>{
 *    "title":"Fragments",
 *    "version" : "20220303.1143",
 *    "code" : 0,
 *    "errors" : [],
 *    "messages" : [],
 *    "model" : {
 *        "threadId" : 80
 *    }
 * }</pre>
 * <br> The task, when finished, will output the same as above (a zip or other file with
 * the fragments in the given format).
 * <p> In general, <i>async</i> should not be specified, as it uses more resources
 * (results files must be stored on the server until the whole serialization is
 * finished). </p>
 * <p> However, for some serializers (e.g. <q>"text/x-kaldi-text"</q>), it's not
 * possible to return any content until all fragments are processed anyway. In these
 * cases, if the set of fragments is very large, the delay between making the request and
 * receiving the response can be so long that client libraries time out. In such cases,
 * using <i>async</i>=true is preferable, as the serialization can be monitored with a
 * long series of short requests, and then the data tranferred when finally ready.</p>
 * @author Robert Fromont
 */
public class Fragments extends APIRequestHandler { // TODO unit test
   
  // Attributes:
  private boolean bCancel = false;
  private MonitorableSeries<Graph> utterances;
  private GraphSerializer serializer;
  
  /**
   * How far through the speakers.
   */
  private int iPercentComplete = 0;
  /**
   * PercentComplete accessor 
   * @return How far through the speakers.
   */
   public int getPercentComplete() {
     if (serializer != null && utterances != null) {
       iPercentComplete =
         Optional.ofNullable(utterances.getPercentComplete()).orElse(0)/2
         + Optional.ofNullable(serializer.getPercentComplete()).orElse(0)/2;
     }
     return iPercentComplete;
   }
  /**
   * PercentComplete mutator
   * @param iNewPercentComplete How far through the speakers.
   */
  public void setPercentComplete(int iNewPercentComplete) { iPercentComplete = iNewPercentComplete; }
  
  /**
   * Constructor
   */
  public Fragments() {
  } // end of constructor
  
  // Servlet methods
  
  /**
   * The GET method for the servlet - this expects an array of 
   * graph <i>id</i>s, <i>start</i> times and <i>end</i> times, 
   * a list of <i>layerId</i>s in include, and a <i>mimetype</i>.
   * <p><b>Input HTTP parameters</b>:
   * <ul>
   *  <li><i>mimeType</i> - content-type of the format to serialize to. </li>
   *  <li><i>layerId</i> - a list of layer IDs to include in the serialization. </li>
   *  <li><i>id</i> - one or more graph IDs. </li>
   *  <li><i>start</i> - one or more start times (in seconds).</li>
   *  <li><i>end</i> - one or more end times (in seconds).</li>
   *  <li><i>filter</i> - (optional) one or more annotation IDs to filter by. e.g. a turn
   * annotation ID, which would ensure that only words within the specified turn are
   * included, not words from other turns.</li>
   *  <li><i>name</i> or <i>collection_name</i> - (optional) name of the collection.</li>
   *  <li><i>prefix</i> - (optional) prefix fragment names with a numeric serial number.</li>
   *  <li><i>tag</i> - (optional) add a tag identifying the target annotation.</li>
   * </ul>
   * <br><b>Output</b>: A each of the transcript fragments 
   * specified by the input parameters converted to the given 
   * format.  
   * This may be a single file or multiple files, depending on
   * the converter behaviour and how many fragments are specified.
   * If there is only one, the file in returned as the response to 
   * the request.  If there are more than one, the response is a
   * zipfile containing the output files. 
   * @param parameters Request parameter map.
   * @param out Response body stream.
   * @param contentType Receives the content type for specification in the response headers.
   * @param fileName Receives the filename for specification in the response headers.
   * @param httpStatus Receives the response status code, in case or error.
   */
  public void get(RequestParameters parameters, OutputStream out, Consumer<String> contentType, Consumer<String> fileName, Consumer<Integer> httpStatus) {
    
    bCancel = false;
      
    // check parameters
    String name = parameters.getString("collection_name");
    if (name == null || name.trim().length() == 0) name = parameters.getString("name");
    if (name == null || name.trim().length() == 0) name = "fragments";
    name = "fragments_"+IO.SafeFileNameUrl(name.trim());
    
    String mimeType = parameters.getString("mimeType");
    if (mimeType == null) mimeType = parameters.getString("content-type");
    if (mimeType == null) {
      contentType.accept("text/plain;charset=UTF-8");
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      try {
        out.write(localize("No MIME type specified").getBytes()); // TODO i18n
      } catch(IOException exception) {}
      return;
    }
    
    // an array of layer names
    String[] layerId = parameters.getStrings("layerId");
    if (layerId.length == 0) {
      contentType.accept("text/plain;charset=UTF-8");
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      try {
        out.write(localize("No layers specified").getBytes()); // TODO i18n
      } catch(IOException exception) {}
      return;
    }
    
    long searchId = -1;
    String threadId = parameters.getString("threadId");
    if (threadId != null) {
      Task task = Task.findTask(Long.valueOf(threadId));
      if (task != null && task instanceof SearchTask) {
        SearchTask search = (SearchTask)task;
        if (search.getResults() != null && search.getResults() instanceof SqlSearchResults) {
          searchId = ((SqlSearchResults)search.getResults()).getId();
        }
      }
    }
    String[] utterance = parameters.getStrings("utterance");
    
    // arrays of transcripts and delimiters
    String[] id = parameters.getStrings("id");
    if (id.length == 0 && utterance.length == 0 && threadId == null) {
      contentType.accept("text/plain;charset=UTF-8");
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      try {
        out.write(localize("No IDs specified").getBytes()); // TODO i18n       
      } catch(IOException exception) {}
      return;
    }
    String[] start = parameters.getStrings("start");
    if (start.length == 0 && utterance.length == 0 && threadId == null) {
      contentType.accept("text/plain;charset=UTF-8");
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      try {
        out.write(localize("No start offsets specified").getBytes()); // TODO i18n
      } catch(IOException exception) {}
      return;
    }
    String[] end = parameters.getStrings("end");
    if (end.length == 0 && utterance.length == 0 && threadId == null) {
      contentType.accept("text/plain;charset=UTF-8");
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      try {
        out.write(localize("No end offsets specified").getBytes()); // TODO i18n
      } catch(IOException exception) {}
      return;
    }
    String[] filter = parameters.getStrings("filter");
    if (utterance == null && threadId == null &&
        (id.length != start.length || id.length != end.length
         || (filter.length != 0 && id.length != filter.length))) {
      contentType.accept("text/plain;charset=UTF-8");
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      try {
        out.write(localize("Mismatched number of id, start, end, and filter parameters").getBytes()); // TODO i18n
      } catch(IOException exception) {}
      return;
    }
    
    boolean prefixNames = parameters.getString("prefix") != null;
    boolean tagTarget = parameters.getString("tag") != null;
    NumberFormat resultNumberFormatter = NumberFormat.getInstance();
    resultNumberFormatter.setGroupingUsed(false);
    if (id != null) {
      resultNumberFormatter.setMinimumIntegerDigits((int)(Math.log10(id.length)) + 1);
    } // TODO minimum integer digits for utterance/threadId cases
    
    try {
      if ("true".equalsIgnoreCase(parameters.getString("async"))) {
        // start a task and return its ID
        
        // layers
        HashSet<String> layers = new HashSet<String>();
        for (String l : layerId) layers.add(l);
        
        // utterances
        Vector<String> vUtterances = new Vector<String>();
        if (utterance != null) {
          for (String matchId : utterance) vUtterances.add(matchId);
        } else if (id != null) {
          if (filter == null) { // not filtering by turn etc.
            for (int f = 0; f < id.length; f++) {
              vUtterances.add(
                id[f]+";"+start[f]+"-"+end[f]
                +(prefixNames?";prefix="+resultNumberFormatter.format(f+1)+"-":""));
            }
          } else { // filtering by turn etc.
            for (int f = 0; f < id.length; f++) {
              vUtterances.add(
                id[f]+";"+start[f]+"-"+end[f]+";"+filter[f]
                +(prefixNames?";prefix="+resultNumberFormatter.format(f+1)+"-":""));
            }
          }
        } // id/start/end specified
        
        SerializeFragmentsTask task = new SerializeFragmentsTask(
          name, searchId, layers,
          mimeType, getStore())
          .setIncludeRequiredLayers(true)
          .setPrefixNames(prefixNames)
          .setTagTarget(tagTarget);
        if (vUtterances.size() > 0) task.setUtterances(vUtterances);
        if (context.getUser() != null) {	
          task.setWho(context.getUser());
        } else {
          task.setWho(context.getUserHost());
        }
        task.start();
        // return its ID
        JsonObjectBuilder jsonResult = Json.createObjectBuilder()
          .add("threadId", task.getId());
        contentType.accept("application/json;charset=UTF-8");
        writeResponse(
          out, successResult(jsonResult.build(), null));
        return;
      } // async
      
      File zipFile = null;
      SqlGraphStoreAdministration store = getStore();
      try {
        
        LinkedHashSet<String> layersToLoad = new LinkedHashSet<String>();
        for (String l : layerId) layersToLoad.add(l);
        
        Vector<String> vUtterances = new Vector<String>();
        if (utterance != null) {
          for (String matchId : utterance) vUtterances.add(matchId);
        } else if (id != null) {
          if (filter == null) { // not filtering by turn etc.
            for (int f = 0; f < id.length; f++) {
              vUtterances.add(
                id[f]+";"+start[f]+"-"+end[f]
                +(prefixNames?";prefix="+resultNumberFormatter.format(f+1)+"-":""));
            }
          } else { // filtering by turn etc.
            for (int f = 0; f < id.length; f++) {
              vUtterances.add(
                id[f]+";"+start[f]+"-"+end[f]+";"+filter[f]
                +(prefixNames?";prefix="+resultNumberFormatter.format(f+1)+"-":""));
            }
          }
        }
        
        GraphSerializer serializer = store.serializerForMimeType(mimeType);
        if (serializer == null) {
         contentType.accept("text/plain;charset=UTF-8");
         httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
         out.write(localize("Invalid MIME type: {0}", mimeType).getBytes()); // TODO i18n
         return;
        }
        Schema schema = store.getSchema();
        // configure serializer
        ParameterSet configuration = new ParameterSet();
        // default values
        serializer.configure(configuration, schema);
        // load saved ones
        ConfigurationHelper.LoadConfiguration(
          serializer.getDescriptor(), configuration, store.getSerializersDirectory(), schema);
        serializer.configure(configuration, schema);
        for (String l : serializer.getRequiredLayers()) layersToLoad.add(l);
        MonitorableSeries<Graph> fragmentSource = vUtterances.size() > 0?
          new FragmentSeries(vUtterances, store, layersToLoad.toArray(new String[0]))
          .setPrefixNames(prefixNames)
          .setTagTarget(tagTarget)
          :new ResultSeries(searchId, store, layersToLoad.toArray(new String[0]))
          .setPrefixNames(prefixNames)
          .setTagTarget(tagTarget);
        // if we're not prefixing names, and we're tagging targets
        if (!prefixNames && tagTarget) {
          // then we need to consolidate graphs - i.e. catch consecutive fragments that
          // are the same ID, and copy the target tags into the winning version of the graph
          fragmentSource = new ConsolidatedGraphSeries(fragmentSource)
            .copyLayer("target");
        }
        if (tagTarget) {
          // make sure serializer outputs target layer too
          Vector<String> layersIncludingTarget = new Vector<String>();
          for (String l : layerId) layersIncludingTarget.add(l);
          layersIncludingTarget.add("target");
          layerId = layersIncludingTarget.toArray(new String[0]);
        }
        final Vector<NamedStream> files = new Vector<NamedStream>();
        serializeFragments(
          name, fragmentSource,
          serializer,
          new Consumer<NamedStream>() {
            public void accept(NamedStream stream) {
              files.add(stream);
            }},
          new Consumer<SerializationException>() {
            public void accept(SerializationException exception) {
              context.servletLog("Fragments: " + exception);
            }},
          layerId, mimeType, store);
        
        // did we actually find any files?
        if (files.size() == 0) {
          contentType.accept("text/plain;charset=UTF-8");
          httpStatus.accept(SC_NOT_FOUND);
          out.write(localize("No files wer generated.").getBytes()); // TODO i18n
        } else if (files.size() == 1) { // one file only
            // don't zip a single file, just return the file
          contentType.accept(mimeType);
          NamedStream stream = files.firstElement();
          fileName.accept(stream.getName());               
          IO.Pump(stream.getStream(), out);
        } else { /// multiple files
          contentType.accept("application/zip");
          fileName.accept(name + ".zip");
          
          // create a stream to pump from
          PipedInputStream inStream = new PipedInputStream();
          final PipedOutputStream outStream = new PipedOutputStream(inStream);
          
          // start a new thread to extract the data and stream it back
          new Thread(new Runnable() {
              public void run() {
                try {
                  ZipOutputStream zipOut = new ZipOutputStream(outStream);
                  
                  // for each file
                  for (NamedStream stream : files) {
                    try {
                      // create the zip entry
                      zipOut.putNextEntry(
                        new ZipEntry(IO.SafeFileNameUrl(stream.getName())));
                      
                      IO.Pump(stream.getStream(), zipOut, false);
                    }
                    catch (ZipException zx) {
                    } finally {
                      stream.getStream().close();
                    }
                  } // next file
                  try {
                    zipOut.close();
                  } catch(Exception exception) {
                    context.servletLog("ConvertFragment: Cannot close ZIP file: " + exception);
                  }
                } catch(Exception exception) {
                  context.servletLog("ConvertFragment: open zip stream: " + exception);
                }
              }
            }).start();
          
          // send headers immediately, so that the browser shows the 'save' prompt
          out.flush();
          
          IO.Pump(inStream, out);
        } // multiple files
      } finally {
        cacheStore(store);
      }
    } catch(Exception ex) {
      contentType.accept("text/plain;charset=UTF-8");
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      try {
        out.write(ex.toString().getBytes());
      } catch(IOException exception) {
        context.servletLog("Files.get: could not report unhandled exception: " + ex);
        ex.printStackTrace(System.err);
      }
    }
  }
  
  /**
   * Cancels the currently running exportTextGrids() method.
   */
  public void cancel() {
    bCancel = true;
    if (utterances != null) utterances.cancel();
    if (serializer != null) serializer.cancel();
  } // end of cancel()
  
  /**
   * Serializes the given series of utterances using the given serializer.
   * @param name The name of the collection.
   * @param utterances Utterances to serialize.
   * @param serializer The serialization module.
   * @param streamConsumer Consumer for receiving the serialized streams.
   * @param errorConsumer Consumer for handling serialization errors.
   * @param layerIds A list of layer names.
   * @param mimeType
   * @param store Graph store.
   * @throws Exception
   */
  public void serializeFragments(
    String name, MonitorableSeries<Graph> utterances, GraphSerializer serializer,
    Consumer<NamedStream> streamConsumer, Consumer<SerializationException> errorConsumer,
    String[] layerIds, String mimeType, GraphStoreAdministration store)
    throws Exception {
      
    bCancel = false;
    this.utterances = utterances;
    this.serializer = serializer;
      
    if (utterances.getExactSizeIfKnown() <= 0) throw new Exception("No utterances specified");
      
    File fTempDir = new File(System.getProperty("java.io.tmpdir"));
	 
    // serialize the fragments
    final Vector<NamedStream> files = new Vector<NamedStream>();
    serializer.serialize(
      utterances, layerIds,
      streamConsumer,
      new Consumer<String>() {
        public void accept(String warning) {
          context.servletLog("WARNING: " + warning);
        }},
      errorConsumer);
    iPercentComplete = 100;
  } // end of serializeFragments()
   
} // end of class Fragments
