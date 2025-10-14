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

package nzilbb.labbcat.server.api.media;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.*;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import nzilbb.ag.Anchor;
import nzilbb.ag.Graph;
import nzilbb.ag.MediaFile;
import nzilbb.ag.Schema;
import nzilbb.ag.GraphNotFoundException;
import nzilbb.ag.PermissionException;
import nzilbb.ag.serialize.GraphSerializer;
import nzilbb.ag.serialize.SerializationException;
import nzilbb.ag.serialize.util.ConfigurationHelper;
import nzilbb.ag.serialize.util.NamedStream;
import nzilbb.ag.serialize.util.Utility;
import nzilbb.configure.ParameterSet;
import nzilbb.labbcat.server.api.APIRequestHandler;
import nzilbb.labbcat.server.api.RequestParameters;
import nzilbb.labbcat.server.db.ConsolidatedGraphSeries;
import nzilbb.labbcat.server.db.FragmentSeries;
import nzilbb.labbcat.server.db.IdMatch;
import nzilbb.labbcat.server.db.ResultSeries;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.labbcat.server.db.SqlSearchResults;
import nzilbb.labbcat.server.search.ArraySearchResults;
import nzilbb.labbcat.server.search.CsvResults;
import nzilbb.labbcat.server.search.SearchResults;
import nzilbb.labbcat.server.search.SearchTask;
import nzilbb.labbcat.server.task.SerializeFragmentsTask;
import nzilbb.labbcat.server.task.Task;
import nzilbb.util.IO;
import nzilbb.util.MonitorableSeries;
import org.apache.commons.csv.CSVRecord;

/**
 * <tt>/api/media/fragments</tt>
 * : Extracts fragments of media files.
 *  <p> The request method can be <b> GET </b> or <b> POST </b>
 * The servlet expects an array of graph <i>id</i>s, <i>start</i> times and <i>end</i> times,
 * or an array of <i>utterance</i> parameters which identify utterances using the MatchId format.
 * or alternatively a <i>threadId</i> parameter which identifies a search
 * that identifies results utterances.
 * <p>or alternatively <i>ag_id</i>, <i>start</i>, and <i>end</i> parameters
 * that identify samples, with optional <i>prefix</i> parameters for prefixing file names.
 * <p><b>Input HTTP parameters</b>:
 * <ul>
 *  <li><i>mimeType</i> - (optional) content-type of the format to serialize to;
 *      default is <q>audio/wav</q>. </li>
 *  <li><i>id</i> or <i>ag_id</i> - one or more graph IDs. </li>
 *  <li><i>start</i> - one or more start times (in seconds).</li>
 *  <li><i>end</i> - one or more end times (in seconds).</li>
 *  <li><i>threadId</i> - (optional) The search task ID returned by a previous call to
 *      <tt>/api/search</tt> or <tt>/api/results/upload</tt>.</li>
 *  <li><i>utterance</i> - (optional) MatchIds for the selected results to return, if only
 *      a subset is required. This can be specifed instead of id/start/end parameters.
 *      This parameter is specified multiple times for multiple values.</li> 
 *  <li><i>sampleRate</i> - (optional) sample rate (Hz) to encode the mono WAV files with.</li>
 *  <li><i>name</i> or <i>collection_name</i> - (optional) name of the collection.</li>
 *  <li><i>prefix</i> - (optional) prefix fragment names with a numeric serial number.</li>
 *  <li><i>startOffsetColumn</i> - (optional) if threadId identifies a
 *      CSV results upload task, this can be used to specify the CSV
 *      column that identifies the start time of each fragment. If not
 *      specified, the utterance start time is used. 
 *      Must be specified with endOffsetColumn. </li>
 *  <li><i>endOffsetColumn</i> - (optional) if threadId identifies a
 *      CSV results upload task, this can be used to specify the CSV
 *      column that identifies the end time of each fragment. If not
 *      specified, the utterance end time is used. 
 *      Must be specified with startOffsetColumn. </li>
 * </ul>
 * <p><b>Output</b>: A wav file for each of the sound fragments
 * specified by the input parameters.  If there is only one, the
 * file in returned as the response to the request.  If there
 * are more than one, the response is a zipfile containing the
 * output files. 
 * @author Robert Fromont
 */
public class Fragments extends APIRequestHandler { // TODO unit test
   
  /**
   * Constructor
   */
  public Fragments() {
  } // end of constructor

  /**
   * The request method for the handler. 
   * @param parameters Request parameter map.
   * @param out Response body stream.
   * @param contentType Receives the content type for specification in the response headers.
   * @param fileName Receives the filename for specification in the response headers.
   * @param httpStatus Receives the response status code, in case of error.
   */
  public void get(RequestParameters parameters, OutputStream out, Consumer<String> contentType, Consumer<String> fileName, Consumer<Integer> httpStatus) {
          
    // check parameters
    String name = parameters.getString("collection_name");
    if (name == null || name.trim().length() == 0) name = parameters.getString("name");
    if (name == null || name.trim().length() == 0) name = "media";
    else name = "media_"+IO.SafeFileNameUrl(name.trim());

    String threadId = parameters.getString("threadId");
    String[] utterance = parameters.getStrings("utterance");
    
    Task task = threadId==null?null:Task.findTask(Long.valueOf(threadId));
    if (threadId != null && task == null && utterance.length == 0) {
      httpStatus.accept(SC_BAD_REQUEST);
      contentType.accept("text/plain;charset=UTF-8");
      try {
        out.write(localize("Invalid task ID: {0}", "\""+threadId+"\"").getBytes());
      } catch(IOException exception) {}
      return;
    } else if (task != null && !(task instanceof SearchTask)) {
      httpStatus.accept(SC_BAD_REQUEST);
      contentType.accept("text/plain;charset=UTF-8");
      try {
        out.write(localize("Invalid task ID: {0}", "\""+threadId+"\"").getBytes());
      } catch(IOException exception) {}
    }
    SearchTask search = (SearchTask)task;
    if (search != null) {
      search.keepAlive(); // prevent the task from dying while we're still interested
    }
    SearchResults searchResults = utterance != null && utterance.length > 0?
      new ArraySearchResults(utterance) // explicit selection only
      :search != null?search.getResults(): // all results
      null;
    
    // arrays of transcripts and delimiters
    String[] id = parameters.getStrings("id");
    if (id.length == 0) id = parameters.getStrings("ag_id");
    if (id.length == 0 && searchResults == null) {
      contentType.accept("text/plain;charset=UTF-8");
      httpStatus.accept(SC_BAD_REQUEST);
      try {
        out.write(localize("No IDs specified").getBytes()); // TODO i18n       
      } catch(IOException exception) {}
      return;
    }
    String[] start = parameters.getStrings("start");
    if (start.length == 0 && searchResults == null) {
      contentType.accept("text/plain;charset=UTF-8");
      httpStatus.accept(SC_BAD_REQUEST);
      try {
        out.write(localize("No start offsets specified").getBytes()); // TODO i18n
      } catch(IOException exception) {}
      return;
    }
    String[] end = parameters.getStrings("end");
    if (end.length == 0 && searchResults == null) {
      contentType.accept("text/plain;charset=UTF-8");
      httpStatus.accept(SC_BAD_REQUEST);
      try {
        out.write(localize("No end offsets specified").getBytes()); // TODO i18n
      } catch(IOException exception) {}
      return;
    }
    String[] prefix = parameters.getStrings("prefix");

    final boolean prefixNames = prefix.length > 0;
    NumberFormat resultNumberFormatter = NumberFormat.getInstance();
    resultNumberFormatter.setGroupingUsed(false);
    if (id.length > 0) {
      resultNumberFormatter.setMinimumIntegerDigits((int)(Math.log10(id.length)) + 1);
    } // TODO minimum integer digits for utterance/threadId cases
    
    String mimeType = parameters.getString("mimeType");
    if (mimeType == null || mimeType.length() == 0) mimeType = "audio/wav";
    final String extension = "."+MediaFile.MimeTypeToSuffix().get(mimeType);
    String sampleRate = parameters.getString("sampleRate");
    if (sampleRate != null) {
      try {
        mimeType += "; samplerate="+Integer.parseInt(sampleRate);
      } catch(Exception exception) {
        httpStatus.accept(SC_BAD_REQUEST);
        try {
          out.write(localize("Invalid sample rate: {0}", sampleRate).getBytes()); // TODO i18n
        } catch(IOException x) {}
      }
    }
    
    // for CsvResults only:
    String startOffsetColumn = null;
    String endOffsetColumn = null;
    if (searchResults instanceof CsvResults) {
      startOffsetColumn = parameters.getString("startOffsetColumn");
      endOffsetColumn = parameters.getString("endOffsetColumn");
    }    
    final String finalStartOffsetColumn = startOffsetColumn;
    final String finalEndOffsetColumn = endOffsetColumn;
    
    try {
      final SqlGraphStoreAdministration store = getStore();
      // unset baseUrl so getMedia gives us file: URLs
      store.setBaseUrl(null);
      final Vector<IdMatch> fragments = new Vector<IdMatch>();
      if (searchResults != null) {
        if (searchResults instanceof SqlSearchResults) {
          // the original results object presumably has a dead connection
          // we want a new copy of our own
          searchResults = new SqlSearchResults((SqlSearchResults)searchResults, store.getConnection());
        } else if (searchResults instanceof CsvResults) {
          // copy object, so that iteration is thread safe
          searchResults = new CsvResults((CsvResults)searchResults);
        }
        final SearchResults results = searchResults;
        results.forEachRemaining(matchId -> {
            // prevent the task from dying while we're still interested
            if (search != null) search.keepAlive();
            
            try {
              IdMatch result = new IdMatch(matchId);
              if (finalStartOffsetColumn != null) {
                CSVRecord lastRow = ((CsvResults)results).getLastRecord();
                String startString = lastRow.get(finalStartOffsetColumn);
                String endString = lastRow.get(finalEndOffsetColumn);
                result.setStartOffset(Double.valueOf(startString));
                result.setEndOffset(Double.valueOf(endString));
              } else { // use utterance boundaries
                String[] anchorIds = {
                  "n_"+result.getStartAnchorId(), "n_"+result.getEndAnchorId() };
                Anchor[] anchors = store.getAnchors(result.getTranscriptId(), anchorIds);
                result.setStartOffset(anchors[0].getOffset());
                result.setEndOffset(anchors[1].getOffset());
              }
              fragments.add(result);
            } catch(Exception x) {
              context.servletLog("ERROR Results-consumer: " + x);
              x.printStackTrace(System.err);
            }
          });
      } else if (id != null) {
        for (int f = 0; f < id.length; f++) {
          IdMatch result = new IdMatch();
          result.setTranscriptId(id[f]);
          try { result.setStartOffset(Double.valueOf(start[f])); } catch (Exception x) {}
          try { result.setEndOffset(Double.valueOf(end[f])); } catch (Exception x) {}
          if (!prefixNames) {
            result.setPrefix("");
          } else {
            if (prefix.length > f) {
              result.setPrefix(prefix[f]);
            } else {
              result.setPrefix(resultNumberFormatter.format(f+1)+"-");
            }
          }
          fragments.add(result);
        } // next ID
      }
        
      if (fragments.size() > 1) { // multiple files
        contentType.accept("application/zip");
        fileName.accept(name + ".zip");                
        // create a stream to pump from
        PipedInputStream inStream = new PipedInputStream();
        final PipedOutputStream outStream = new PipedOutputStream(inStream);
        final String finalMimeType = mimeType;
        // start a new thread to extract the data and stream it back
        new Thread(new Runnable() {
            public void run() {
              try {
                ZipOutputStream zipOut = new ZipOutputStream(outStream);
                HashMap<Integer,String> agIdToTranscript = new HashMap<Integer,String>();
                HashSet<String> alreadyAdded = new HashSet<String>();
                  
                for (IdMatch fragment : fragments) {
                  if (fragment.getTranscriptId() != null
                      && fragment.getStartOffset() != null
                      && fragment.getEndOffset() != null) {
                    try {
                      if (fragment.getGraphId() != null
                          && fragment.getTranscriptId().startsWith("g_")) {
                        // we don't have the full name of the transcript, so get it
                        if (!agIdToTranscript.containsKey(fragment.getGraphId())) {
                          // load it from the graph store
                          Graph g = store.getTranscript(fragment.getTranscriptId(), null);
                          agIdToTranscript.put(fragment.getGraphId(), g.getId());
                        }
                        fragment.setTranscriptId(
                          agIdToTranscript.get(fragment.getGraphId()));
                      }
                      String fragmentFileName = (prefixNames?fragment.getPrefix():"")
                        +Graph.FragmentId(
                          fragment.getTranscriptId(),
                          fragment.getStartOffset(),
                          fragment.getEndOffset())
                        +extension;
                      if (!alreadyAdded.contains(fragmentFileName)) {
                        String uri = store.getMedia(
                          fragment.getTranscriptId(), ""/*TODO*/, finalMimeType,
                          fragment.getStartOffset(), fragment.getEndOffset());
                        File tempFile = new File(new URI(uri));
                        try {
                          // create the zip entry
                          zipOut.putNextEntry(new ZipEntry(fragmentFileName));
                          IO.Pump(new FileInputStream(tempFile), zipOut, false);
                          alreadyAdded.add(fragmentFileName);
                        } finally {
                          tempFile.delete();
                        }
                      } // file not already added
                    } catch (GraphNotFoundException notFound) {
                      context.servletLog("Transcript not found \""+fragment.getId()+"\"");
                    } catch (PermissionException nope) {
                      context.servletLog("Media access denied for \""+fragment.getId()+"\"");
                    } catch (ZipException zx) {
                      context.servletLog("Zip error for \""+fragment.getId()+"\": " +zx);
                    } catch(Exception exception) {
                      context.servletLog("Error for \""+fragment.getId()+"\": " +exception);
                    }
                  } // valid fragment
                } // next fragment
                try {
                  zipOut.close();
                } catch(Exception exception) {
                  context.servletLog("Cannot close ZIP file: " + exception);
                }
              } finally {
                cacheStore(store);
              }
            }}).start();
          
        // send headers immediately, so that the browser shows the 'save' prompt
        out.flush();
          
        IO.Pump(inStream, out);
      } else if (fragments.size() == 1) { // single file, don't zip it
        for (IdMatch fragment : fragments) { // (there's only one)
          if (fragment.getTranscriptId() != null
              && fragment.getStartOffset() != null
              && fragment.getEndOffset() != null) {
            try {
              if (fragment.getGraphId() != null
                  && fragment.getTranscriptId().startsWith("g_")) {
                // we don't have the full name of the transcript, so get it
                Graph g = store.getTranscript(fragment.getTranscriptId(), null);
                fragment.setTranscriptId(g.getId());
              }
              String fragmentFileName = (prefixNames?fragment.getPrefix():"")
                +Graph.FragmentId(
                  fragment.getTranscriptId(),
                  fragment.getStartOffset(),
                  fragment.getEndOffset())
                +extension;
              String uri = store.getMedia(
                fragment.getTranscriptId(), ""/*TODO*/, mimeType,
                fragment.getStartOffset(), fragment.getEndOffset());
              final File tempFile = new File(new URI(uri));
              contentType.accept(mimeType);
              fileName.accept(fragmentFileName);
              PipedInputStream inStream = new PipedInputStream();
              final PipedOutputStream outStream = new PipedOutputStream(inStream);
              // start a new thread to extract the data and stream it back
              new Thread(new Runnable() {
                  public void run() {
                    try {
                      try {
                        IO.Pump(new FileInputStream(tempFile), outStream);
                      } finally {
                        tempFile.delete();
                      }
                    } catch(Exception exception) {
                      context.servletLog("Error for \""+tempFile.getName()+"\": " +exception);
                    }
                  }}).start();
              
                // send headers immediately, so that the browser shows the 'save' prompt
              out.flush();          
              IO.Pump(inStream, out);
            } catch (GraphNotFoundException notFound) {
              contentType.accept("text/plain;charset=UTF-8");
              httpStatus.accept(SC_NOT_FOUND);
              out.write(localize("Transcript not found: {0}", fragment.getId()).getBytes());
            } catch (PermissionException nope) {
              contentType.accept("text/plain;charset=UTF-8");
              httpStatus.accept(SC_NOT_FOUND);
              out.write(localize("Media access denied: {0}", fragment.getId()).getBytes());
            } finally {
              cacheStore(store);
            }
          } else {
            contentType.accept("text/plain;charset=UTF-8");
            httpStatus.accept(SC_NOT_FOUND);
            out.write(localize("No files were generated.").getBytes()); // TODO i18n
          }
        } // next fragment
      } else { // no files
        contentType.accept("text/plain;charset=UTF-8");
        httpStatus.accept(SC_NOT_FOUND);
        out.write(localize("No files were generated.").getBytes()); // TODO i18n
        cacheStore(store);
      }      
    } catch(Exception ex) {
      contentType.accept("text/plain;charset=UTF-8");
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      ex.printStackTrace(System.err);
      try {
        out.write(ex.toString().getBytes());
      } catch(IOException exception) {
        context.servletLog("Files.get: could not report unhandled exception: " + ex);
      }
    }
  }
  
} // end of class Fragments
