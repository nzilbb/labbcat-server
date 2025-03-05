//
// Copyright 2023-2024 New Zealand Institute of Language, Brain and Behaviour, 
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

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonWriter;
import javax.json.stream.JsonGenerator;
import nzilbb.ag.Anchor;
import nzilbb.ag.Annotation;
import nzilbb.ag.Constants;
import nzilbb.ag.Graph;
import nzilbb.ag.Layer;
import nzilbb.ag.Schema;
import nzilbb.labbcat.server.db.IdMatch;
import nzilbb.labbcat.server.db.OneQuerySearch;
import nzilbb.labbcat.server.db.SqlGraphStore;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.labbcat.server.db.SqlSearchResults;
import nzilbb.labbcat.server.db.StoreCache;
import nzilbb.labbcat.server.search.ArraySearchResults;
import nzilbb.labbcat.server.search.Column;
import nzilbb.labbcat.server.search.Matrix;
import nzilbb.labbcat.server.search.SearchResults;
import nzilbb.labbcat.server.search.SearchTask;
import nzilbb.labbcat.server.task.Task;
import nzilbb.util.IO;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/**
 * <tt>/api/results</tt>
 * : Provides access to search results.
 * <p><b>Input HTTP parameters</b>:
 * <ul>
 *  <li><i>threadId</i> - The search task ID returned by a previous call to
 *      <tt>/api/search</tt>. </li>
 *  <li><i>utterance</i> - MatchIds for the selected results to return, if only a subset
 *      is required. This parameter is specified multiple times for multiple values.</li>
 *  <li><i>words_context</i> - How many words of context before and after the match to
 *      include in the result text. </li>
 *  <li><i>pageLength</i> - How many results to return at a time. </li>
 *  <li><i>pageNumber</i> - The zero-based number of the page of results to return. </li>
 *  <li><i>content-type</i> - (Optional) A parameter to specify the content type of the
 *      response, whose value can be <q>text/csv</q> or <q>application/json</q> - this can
 *      also be achieved by setting the value of the <i>Accept</i> request header. If
 *      neither the request parameter nor the requets header are set, the response is
 *      <q>application/json</q> </li>
 *  <li><i>csv_layer</i> - (Optional) IDs of which layers to include in the CSV output. This
 *      parameter is specified multiple times for multiple values. </li>
 *  <li><i>include_count_...</i> - (Optional) Parameters that specify the number of
 *      annotations on a given layer to return. The name of the paramater is
 *      "include_count_" followed by the layer ID, and the value must be an
 *      integer. Layers for which no include_count_ parameter is specified will include
 *      one annotation in the output (i.e. the first one, if any).</li>
 *  <li><i>csv_option</i> - (Optional) Additional fields to include in the output. This
 *      parameter is specified multiple times for multiple values, which can include:
 *      <ul>
 *       <li><q>labbcat_title</q> - the title of this LaBB-CAT instance </li>
 *       <li><q>labbcat_version</q> - the current version of this LaBB-CAT instance </li>
 *       <li><q>collection_name</q> - the name/description of the search </li>
 *       <li><q>result_number</q> - the ordinal of each result/match </li>
 *       <li><q>series_offset</q> - how far through the episode, in seconds, this
 *           transcript occurs. </li>
 *       <li><q>series_length</q> - the total duration, in seconds, of all transcripts in the
 *           episode </li>
 *       <li><q>line_time</q> - the start offset of the utterance </li>
 *       <li><q>line_end_time</q> - the end offset of the utterance </li>
 *       <li><q>match</q> - the unique ID of the match </li>
 *       <li><q>target</q> - the unique ID of the match's target annotation </li>
 *       <li><q>word_url</q> - the URL for locating the word token in the transcript </li>
 *       <li><q>result_text</q> - the match text, and the context before and after if
 *           required  </li>
 *      </ul>
 *  </li>
 *  <li><i>offsetThreshold</i> - the minimum anchor confidence for returning anchor
 *      offsets. The default is 50, which means that offsets that are at least
 *      automatically aligned will be returned. Use 100 for manually-aligned offsets only,
 *      and 0 to return all offsets regardless of confidence.</li>
 *  <li><i>todo</i> - (Optional) A legacy parameter whose value can be <q>csv</q> to
 *      specify that the content-type of the result be <q>text/csv</q></li>
 * </ul>
 * <p> At least one of <i>threadId</i> or <i>utterance</i> must be specified.
 * <br><b>Output</b>: a JSON-encoded response object of the usual structure for which the
 * "model" contains the results of the search, e.g.
 * <pre>{
 *    "title":"search",
 *    "version" : "20230502.0924",
 *    "code" : 0,
 *    "errors" : [],
 *    "messages" : [],
 *    "model" : {
 *        "name" : "_^(test)$",
 *        "matches" : [{
 *             "MatchId" : "g_22;em_12_73;n_1130-n_1130;p_7;#=ew_2_8110;prefix=1-;[0]=ew_0_641",
 *             "Transcript" : "mop03-2b-06.trs",
 *             "Participant" : "mop03-2b",
 *             "Corpus" : "CC",
 *             "Line" : 89.627,
 *             "LineEnd" : 92.532,
 *             "BeforeMatch" : "and",
 *             "Text" : "test -",
 *             "AfterMatch" : "the",
 *        },{
 *             "MatchId" : "g_24;em_12_86;n_1486-n_1486;p_2;#=ew_2_8116;prefix=2-;[0]=ew_0_806",
 *             "Transcript" : "mop03-2b-07.trs",
 *             "Participant" : "mop03-2b",
 *             "Corpus" : "CC",
 *             "Line" : 318.485,
 *             "LineEnd" : 322.282,
 *             "BeforeMatch" : "impairment",
 *             "Text" : "test",
 *             "AfterMatch" : "on",
 *        }]
 *    }
 * }</pre>
 * <br> The task, when finished, will output a URL for accessing the matches of the search.
 * @author Robert Fromont
 */
public class Results extends APIRequestHandler { // TODO unit test
  // TODO add support for annotation anchoring

  /**
   * Constructor
   */
  public Results() {
  } // end of constructor
  
  // Servlet methods
  
  /**
   * The GET method for the servlet.
   * @param parameters Request parameter map.
   * @param requestHeaders Access to HTTP request headers.
   * @param out Response body output stream.
   * @param contentType Receives the content type for specification in the response headers.
   * @param fileName Receives the filename for specification in the response headers.
   * @param httpStatus Receives the response status code, in case or error.
   */
  public void get(RequestParameters parameters, UnaryOperator<String> requestHeaders, OutputStream out, Consumer<String> contentTypeConsumer, Consumer<String> fileName, Consumer<Integer> httpStatus) {
    
    try {
      final SqlGraphStoreAdministration store = getStore();
      final Schema schema = store.getSchema();
      
      // parameters
      String threadId = parameters.getString("threadId");
      String[] utterances = parameters.getStrings("utterance");
      if (threadId == null && utterances.length == 0) {
        contentTypeConsumer.accept("application/json;charset=UTF-8");
        httpStatus.accept(SC_BAD_REQUEST);
        JsonWriter writer = Json.createWriter(out);
        writer.writeObject(failureResult("No task ID specified."));
        writer.close();
        return;
      }
      int words_context = 0;
      if (parameters.getString("words_context") != null) {
        try {
          words_context = Integer.parseInt(parameters.getString("words_context"));
        } catch(Exception exception) {
          contentTypeConsumer.accept("application/json;charset=UTF-8");
          httpStatus.accept(SC_BAD_REQUEST);
          JsonWriter writer = Json.createWriter(out);
          writer.writeObject(failureResult(
                               "Invalid words context: {0}",
                               parameters.getString("words_context")));
          writer.close();
          return;
        }
      }
      final int wordsContext = words_context;
      Integer pageLength = null;
      if (parameters.getString("pageLength") != null) {
        try {
          pageLength = Integer.valueOf(parameters.getString("pageLength"));
        } catch(NumberFormatException x) {
          contentTypeConsumer.accept("application/json;charset=UTF-8");
          httpStatus.accept(SC_BAD_REQUEST);
          JsonWriter writer = Json.createWriter(out);
          writer.writeObject(failureResult(
                               "Invalid pageLength: {0}",
                               parameters.getString("pageLength")));
          writer.close();
          return;
        }
      }
      Integer pageNumber = Integer.valueOf(0);
      if (parameters.getString("pageNumber") != null) {
        try {
          pageNumber = Integer.valueOf(parameters.getString("pageNumber"));
        } catch(NumberFormatException x) {
          contentTypeConsumer.accept("application/json;charset=UTF-8");
          httpStatus.accept(SC_BAD_REQUEST);
          JsonWriter writer = Json.createWriter(out);
          writer.writeObject(failureResult(
                               "Invalid pageNumber: {0}",
                               parameters.getString("pageNumber")));
          writer.close();
          return;
        }
      }
      Integer offsetThreshold = Integer.valueOf(Constants.CONFIDENCE_AUTOMATIC);
      if (parameters.getString("offsetThreshold") != null) {
        try {
          offsetThreshold = Integer.valueOf(parameters.getString("offsetThreshold"));
        } catch(NumberFormatException x) {
          contentTypeConsumer.accept("application/json;charset=UTF-8");
          httpStatus.accept(SC_BAD_REQUEST);
          JsonWriter writer = Json.createWriter(out);
          writer.writeObject(failureResult(
                               "Invalid offsetThreshold: {0}",
                               parameters.getString("offsetThreshold")));
          writer.close();
          return;
        }
      }
      final int finalOffsetThreshold = offsetThreshold;

      String[] csv_option = parameters.getStrings("csv_option");
      final LinkedHashSet<String> options = new LinkedHashSet<String>();
      for (String o : csv_option) options.add(o);
      if (options.size() == 0) { // default options
        options.add("labbcat_title");
        options.add("labbcat_version");
        options.add("collection_name");
        options.add("result_number");
        options.add("line_time");
        options.add("line_end_time");
        options.add("match");
        options.add("word_url");
        options.add("result_text");
      }

      String[] csv_layer = parameters.getStrings("csv_layer");
      final LinkedHashSet<String> layers = new LinkedHashSet<String>();
      for (String l : csv_layer) layers.add(l);
      if (layers.size() == 0) { // default layers
        layers.add(schema.getRoot().getId());
        layers.add(schema.getParticipantLayerId());
        layers.add(schema.getCorpusLayerId());
        layers.add(schema.getWordLayerId());
      }
      // selected layers ordered hierarchically for CSV output
      final LinkedHashMap<String,Integer> csvLayers = new LinkedHashMap<String,Integer>();
      // participant attributes
      schema.getParticipantLayer().getChildren().values().stream()
        .filter(l -> l.getAlignment() == Constants.ALIGNMENT_NONE)
        .map(l -> l.getId())
        .filter(id -> layers.contains(id))
        .forEach(id -> {
            csvLayers.put(
              id, Integer.valueOf(
                Optional.ofNullable(parameters.getString("include_count_"+id)).orElse("1")));
          });
      // transcript attributes
      schema.getRoot().getChildren().values().stream()
        .filter(l -> l.getAlignment() == Constants.ALIGNMENT_NONE)
        .map(l -> l.getId())
        .filter(id -> !id.equals(schema.getParticipantLayerId()))
        .filter(id -> !id.equals(schema.getCorpusLayerId()))
        .filter(id -> !id.equals(schema.getEpisodeLayerId()))
        .filter(id -> layers.contains(id))
        .forEach(id -> {
            csvLayers.put(
              id, Integer.valueOf(
                Optional.ofNullable(parameters.getString("include_count_"+id)).orElse("1")));
          });
      // span layers
      schema.getRoot().getChildren().values().stream()
        .filter(l -> l.getAlignment() != Constants.ALIGNMENT_NONE)
        .map(l -> l.getId())
        .filter(id -> layers.contains(id))
        .forEach(id -> {
            csvLayers.put(
              id, Integer.valueOf(
                Optional.ofNullable(parameters.getString("include_count_"+id)).orElse("1")));
          });
      // phrase layers
      schema.getTurnLayer().getChildren().values().stream()
        .map(l -> l.getId())
        .filter(id -> !schema.getWordLayerId().equals(id))
        .filter(id -> layers.contains(id))
        .forEach(id -> {
            csvLayers.put(
              id, Integer.valueOf(
                Optional.ofNullable(parameters.getString("include_count_"+id)).orElse("1")));
          });
      // word layers
      if (layers.contains(schema.getWordLayerId())) {
        csvLayers.put(schema.getWordLayerId(), 1);
      }
      schema.getWordLayer().getChildren().values().stream()
        .map(l -> l.getId())
        .filter(id -> !"segment".equals(id))
        .filter(id -> layers.contains(id))
        .forEach(id -> {
            csvLayers.put(
              id, Integer.valueOf(
                Optional.ofNullable(parameters.getString("include_count_"+id)).orElse("1")));
          });
      // segment layers
      if (schema.getLayers().containsKey("segment")) {
        if (layers.contains("segment")) {
          csvLayers.put(
            "segment", Integer.valueOf(
              Optional.ofNullable(parameters.getString("include_count_segment")).orElse("1")));
        }
        schema.getLayer("segment").getChildren().values().stream()
          .map(l -> l.getId())
          .filter(id -> layers.contains(id))
          .forEach(id -> {
              csvLayers.put(
                id, Integer.valueOf(
                  Optional.ofNullable(parameters.getString("include_count_"+id)).orElse("1")));
            });
      }    
      
      String contentType = parameters.getString("todo");
      if (contentType == null) {
        contentType = parameters.getString("content-type");
        if (contentType == null) {
          contentType = requestHeaders.apply("Accept");
        }
      }
      String language = Optional.ofNullable(requestHeaders.apply("Accept-Language")).orElse("en");
      if ("csv".equalsIgnoreCase(contentType)
          || "text/csv".equalsIgnoreCase(contentType)) {
        contentType = "text/csv;charset=UTF-8";
      } else {
        contentType = "application/json;charset=UTF-8";
      }
      contentTypeConsumer.accept(contentType);
      // output will be either CSV or JSON - define an appropriate output streamer
      final CSVPrinter csvOut = contentType.startsWith("text/csv")?
        new CSVPrinter(new PrintWriter(new OutputStreamWriter(out, "UTF-8")),
                       CSVFormat.EXCEL.withDelimiter( 
                         // guess the best delimiter - comma if English speaking user, tab otherwise
                         language.contains("en")?',':'\t')):null;
      final JsonGenerator jsonOut = !contentType.startsWith("text/csv")?
        Json.createGenerator(new PrintWriter(new OutputStreamWriter(out, "UTF-8"))):null;
      
      Task task = threadId==null?null:Task.findTask(Long.valueOf(threadId));
      if (threadId != null && task == null && utterances.length == 0) {
        httpStatus.accept(SC_BAD_REQUEST);
        JsonWriter writer = Json.createWriter(out);
        writer.writeObject(failureResult("Invalid task ID: {0}", "\""+threadId+"\""));
        writer.close();
        return;
      } else if (task != null && !(task instanceof SearchTask)) {
        httpStatus.accept(SC_BAD_REQUEST);
        JsonWriter writer = Json.createWriter(out);
        writer.writeObject(failureResult("Invalid task ID: {0}", task.getClass().getName()));
        writer.close();
        return;
      }
      SearchTask search = (SearchTask)task;
      if (search != null) {
        search.keepAlive(); // prevent the task from dying while we're still interested
      }
      SearchResults searchResults = utterances.length > 0?
        new ArraySearchResults(utterances) // explicit selection only
        :search.getResults(); // all results from database

      try {
        Connection connection = store.getConnection();
        final SearchResults results = utterances.length > 0?searchResults
          // the original results object presumably has a dead connection
          // we want a new copy of our own
          :new SqlSearchResults((SqlSearchResults)searchResults, connection);
        if (utterances.length > 0 && search != null) { // both threadId and utterance specified
          // copy name
          ((ArraySearchResults)results).setName(search.getResults().getName());
        }
        boolean distinctStartEndTokens = false;
        // peek at first result so we can figure out if it's a one-word-wide result or not
        if (results.hasNext()) { // (hasNext also loads size etc.)
          IdMatch result = new IdMatch(results.next());
          String startTokenId = result.getMatchAnnotationUids().get("0");
          String endTokenId = result.getMatchAnnotationUids().get("1");
          distinctStartEndTokens = startTokenId != null && endTokenId != null
            && !startTokenId.equals(endTokenId);
          results.reset(); // back to the beginning so we don't skip the first result
        }
        final boolean multiWordMatches = distinctStartEndTokens;

        final PreparedStatement sqlMatchTranscriptContext = connection.prepareStatement(
          "SELECT COALESCE(GROUP_CONCAT(word.label ORDER BY ordinal_in_turn SEPARATOR ' '),'')"
          + " FROM annotation_layer_0 word"
          + (wordsContext < 0?" INNER JOIN anchor start ON word.start_anchor_id = start.anchor_id"
             :"")
          + " WHERE word.ag_id = ?"
          + " AND turn_annotation_id = ?"
          + " AND ordinal_in_turn >= ? AND ordinal_in_turn <= ?"
          + (wordsContext < 0?" AND start.offset >= ? AND start.offset < ?"
             :"")
          + " ORDER BY ordinal_in_turn");
        final PreparedStatement sqlMatchLayerLabels = connection.prepareStatement( // TODO handle segment layers too
          "SELECT COALESCE(GROUP_CONCAT(annotation.label"
          + " ORDER BY annotation.ordinal_in_turn SEPARATOR ' '),'')"
          + " FROM annotation_layer_0 first_word"
          + " INNER JOIN annotation_layer_0 last_word"
          + " ON last_word.annotation_id = ?"
          + " INNER JOIN annotation_layer_? annotation"
          + " ON annotation.turn_annotation_id = first_word.turn_annotation_id"
          + " AND annotation.ordinal_in_turn >= first_word.ordinal_in_turn"
          + " AND annotation.ordinal_in_turn <= last_word.ordinal_in_turn"
          + " AND annotation.ordinal = 1"
          + " WHERE first_word.annotation_id = ?"
          + " ORDER BY annotation.ordinal_in_turn");
        
        try {
          String searchName = "";
          if (search.getMatrix() != null) {
            searchName = search.getMatrix().getDescription();
          }
          if (searchName == null || searchName.trim().length() == 0) {
            searchName = search.getDescription();
          }
          final String finalSearchName = searchName;
          
          if (contentType.startsWith("text/csv")) {
            String name = IO.SafeFileNameUrl(searchName);
            if (name.length() > 150) name = name.substring(0, 150);
            name = "results_" + name + ".csv";
            fileName.accept(name);
          }
          
          outputStart(
            jsonOut, csvOut, searchName, results.size(), multiWordMatches, options, layers, csvLayers, schema);
          try {
            if (pageLength == null) pageLength = results.size();
            if (pageNumber == null) pageNumber = Integer.valueOf(0);
            int seekTo = (pageLength * pageNumber) + 1;
            if (seekTo > 1) results.seek(seekTo);
            results.setPageLength(pageLength);
            // cache graph/participant IDs to save database lookups
            final HashMap<Integer,Graph> agIdToGraph = new HashMap<Integer,Graph>();
            final HashMap<Integer,String> speakerNumberToName = new HashMap<Integer,String>();
            final Set<String> anchorStartLayers = csvLayers.keySet().stream()
              .filter(layerId -> parameters.getString("share_start_"+layerId) != null)
              .collect(Collectors.toSet());
            final Set<String> anchorEndLayers = csvLayers.keySet().stream()
              .filter(layerId -> parameters.getString("share_end_"+layerId) != null)
              .collect(Collectors.toSet());
            for (String layerId : csvLayers.keySet())
              
              if (contentType.startsWith("text/csv")) {
                // process the data rows
                store.getMatchAnnotations(
                  results, csvLayers, anchorStartLayers, anchorEndLayers, 0,
                  annotations -> {
                    search.keepAlive(); // prevent the task from dying while we're still interested
                    try {
                      // write the initial non-layer fields
                      String matchId = results.getLastMatchId();
                      IdMatch result = new IdMatch(matchId);
                      outputMatchStart(
                        jsonOut, csvOut, finalSearchName, result, agIdToGraph, speakerNumberToName,
                        store, schema, sqlMatchTranscriptContext, wordsContext, options, layers);
                      
                      // write the annotations
                      Iterator<String> layerIds = csvLayers.keySet().stream()
                        .map(id -> {
                            Vector<String> reps = new Vector<String>();
                            for (int i = 0; i < csvLayers.get(id); i++) reps.add(id);
                            return reps.stream();
                          })
                        .flatMap(i -> i)
                        .iterator();
                      Layer lastLayer = schema.getWordLayer();
                      for (Annotation annotation : annotations) {
                        Layer layer = schema.getLayer(layerIds.next());
                        
                        // if we've changed layer, finish off the last layer...
                        if (lastLayer != null && !lastLayer.getId().equals(layer.getId())) {
                          outputMatchLayerLabels(
                            csvOut, schema, sqlMatchLayerLabels, multiWordMatches, result, lastLayer);
                        } // layer changed
                        
                        // now output this annotation
                        if (annotation == null) {
                          csvOut.print("");
                          switch (layer.getAlignment()) { // offsets
                            case Constants.ALIGNMENT_INTERVAL:
                              csvOut.print(""); // start
                              csvOut.print(""); // end
                              break;
                            case Constants.ALIGNMENT_INSTANT:
                              csvOut.print(""); // time
                          }
                        } else {
                          assert layer.getId().equals(annotation.getLayerId())
                            : "layer.getId().equals(annotation.getLayerId()) - "
                            + layer + " <> " + annotation.getLayerId() + " " + annotation.getLabel();
                          csvOut.print(annotation.getLabel());
                          if (layer.getAlignment() != Constants.ALIGNMENT_NONE) { // offsets
                            String[] anchorIds = {
                              annotation.getStartId(), annotation.getEndId() };
                            Anchor[] anchors = store.getAnchors(null, anchorIds);
                            for (Anchor anchor : anchors) {
                              if (anchor != null && anchor.getOffset() != null
                                  && anchor.getConfidence() >= finalOffsetThreshold) {
                                csvOut.print(anchor.getOffset().toString());
                              } else { // no anchor offset available, or not confident enough
                                csvOut.print("");
                              }
                              if (layer.getAlignment() == Constants.ALIGNMENT_INSTANT) {
                              // only one offset
                                break;
                              }
                            } // next anchor
                          } // offsets
                        } // annotation present
                        lastLayer = layer;
                      } // next annotation
                      if (lastLayer != null) {
                        outputMatchLayerLabels(
                          csvOut, schema, sqlMatchLayerLabels, multiWordMatches, result, lastLayer);
                      } // layer changed
                    } catch(Exception x) {
                      System.err.println("ERROR Results-consumer: " + x);
                      x.printStackTrace(System.err);
                    }
                  });
              } else { // JSON output
                results.forEachRemaining(matchId -> {
                    search.keepAlive(); // prevent the task from dying while we're still interested
                    try {
                      IdMatch result = new IdMatch(matchId);                
                      outputMatchStart(
                        jsonOut, csvOut, finalSearchName, result, agIdToGraph, speakerNumberToName,
                        store, schema, sqlMatchTranscriptContext, wordsContext, options, layers);
                    } catch(Exception x) {
                      System.err.println("ERROR Results-consumer: " + x);
                      x.printStackTrace(System.err);
                    } finally {
                      outputMatchEnd(jsonOut, csvOut); // end this match
                    }
                  });
              } // JSON output
          } finally {
            outputMatchesEnd(jsonOut, csvOut); // end all matches
            sqlMatchTranscriptContext.close();
            sqlMatchLayerLabels.close();
          }
          outputSuccessfulEnd(jsonOut, csvOut);
        } catch (Exception x) {
          System.err.println(x.toString());
          x.printStackTrace(System.err);
          outputFailedEnd(jsonOut, csvOut, x.getMessage());
        } finally {
          results.close();
        }
      } finally {
        cacheStore(store);
      }
      
    } catch(Exception ex) {
      try {
        contentTypeConsumer.accept("application/json;charset=UTF-8");
      } catch(Exception exception) {}
      try {
        httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      } catch(Exception exception) {}
      writeResponse(out, failureResult(ex));
    }
  }
  
  /**
   * Sends output for starting the results stream.
   * <p> Assumes no output has yet been written.
   * @param jsonOut Generator for JSON output, or null for CSV output.
   * @param csvOut Printer for CSV output, or null for JSON output.
   * @param searchName The name search that produced the results.
   * @param matchCount The number of matches.
   * @param multiWordMatches Whether matches span multiple tokens (or just one token)
   * @param layers The layers to output.
   * @param options The additional fields to output.
   */
  void outputStart(
    JsonGenerator jsonOut, CSVPrinter csvOut, String searchName, long matchCount,
    boolean multiWordMatches, LinkedHashSet<String> options, LinkedHashSet<String> layers,
    LinkedHashMap<String,Integer> csvLayers, Schema schema)
    throws IOException {
    if (csvOut != null) {
      // Send column headers
      if (options.contains("labbcat_title")) csvOut.print("Title");
      if (options.contains("labbcat_version")) csvOut.print("Version");
      if (options.contains("collection_name")) csvOut.print("SearchName");
      if (options.contains("result_number")) csvOut.print("Number");
      if (layers.contains(schema.getRoot().getId())) csvOut.print("Transcript");
      if (options.contains("series_offset")) csvOut.print("SeriesOffset");
      if (options.contains("series_length")) csvOut.print("SeriesLength");
      if (layers.contains(schema.getParticipantLayerId())) csvOut.print("Speaker");
      if (layers.contains(schema.getCorpusLayerId())) csvOut.print("Corpus");
      if (layers.contains(schema.getEpisodeLayerId())) csvOut.print("Episode");
      if (options.contains("line_time")) csvOut.print("Line");
      if (options.contains("line_end_time")) csvOut.print("LineEnd");
      if (options.contains("match")) csvOut.print("MatchId");
      if (options.contains("target")) csvOut.print("TargetId");
      if (options.contains("word_url")) csvOut.print("URL");
      if (options.contains("result_text")){
        csvOut.print("Before Match");
        csvOut.print("Text");
        csvOut.print("After Match");
      }
      csvLayers.keySet().forEach(id -> {
          try {
            Layer layer = schema.getLayer(id);
            switch (layer.getAlignment()) {
              case Constants.ALIGNMENT_INTERVAL:
                if (csvLayers.get(id) == 1) {
                  csvOut.print("Target " + id);
                  csvOut.print("Target " + id + " start");
                  csvOut.print("Target " + id + " end");
                } else {
                  for (int i = 1; i <= csvLayers.get(id) ; i++) {
                    csvOut.print("Target " + id + " " + i);
                    csvOut.print("Target " + id + " " + i + " start");
                    csvOut.print("Target " + id + " " + i + " end");
                  }
                }
                break;
              case Constants.ALIGNMENT_INSTANT:
                if (csvLayers.get(id) == 1) {
                  csvOut.print("Target " + id);
                  csvOut.print("Target " + id + " offset");
                } else {
                  for (int i = 1; i <= csvLayers.get(id) ; i++) {
                    csvOut.print("Target " + id + " " + i);
                    csvOut.print("Target " + id + " " + i + " offset");
                  }
                }
                break;
              default:
                if (layer.getParentId() == null
                    || layer.getParentId().equals(schema.getRoot().getId())
                    || layer.getParentId().equals(schema.getParticipantLayerId())) { // attribute
                  if (csvLayers.get(id) == 1) {
                    csvOut.print(id);
                  } else {
                    for (int i = 1; i <= csvLayers.get(id) ; i++) {
                      csvOut.print(id + " " + i);
                    }
                  }
                } else { // tag
                  if (csvLayers.get(id) == 1) {
                    csvOut.print("Target " + id);
                  } else {
                    for (int i = 1; i <= csvLayers.get(id) ; i++) {
                      csvOut.print("Target " + id + " " + i);
                    }
                  }
                }
            } // switch alignment
            
            // if the matches are multi-word, we not only return the target label
            // but also the concatenation of all the labels within the match
            if (multiWordMatches
                // for word layers only
                && schema.getWordLayerId().equals(layer.getParentId())
                && !layer.getId().equals("segment")) {
              csvOut.print("Match " + id);
            }
          } catch(IOException x) {
          }
        });
    } else {
      // initialise JSON envelope
      startResult(jsonOut, false);
      // set initial structure of model
      jsonOut.write("name", searchName);
      jsonOut.write("matchCount", matchCount);
      if (options.contains("labbcat_title")) jsonOut.write("labbcatTitle", context.getTitle());
      if (options.contains("labbcat_version")) jsonOut.write("labbcatVersion", context.getVersion());
      jsonOut.writeStartArray("matches");      
    }
  } // end of startResults()
  
  /**
   * Sends output required for starting a match record.
   * <p> Assumes that the last output call made was outputStart or outputMatchEnd.
   * @param jsonOut Generator for JSON output, or null for CSV output.
   * @param csvOut Printer for CSV output, or null for JSON output.
   * @param searchName The name of the search that produced the results.
   * @param result The match result that is being started.
   * @param agIdToGraph Cache mapping ag_ids to transcript annotation graphs.
   * @param speakerNumberToName Cache mapping speaker_numbers to participant IDs.
   * @param store The graph store.
   * @param sqlMatchTranscriptContext Query for retrieving context transcript.
   * @param wordsContext Number of words context to include.
   * @param layers Layers to include in the output.
   * @param options Additional fields to include in the output.
   */
  void outputMatchStart(
    JsonGenerator jsonOut, CSVPrinter csvOut, String searchName, IdMatch result,
    HashMap<Integer,Graph> agIdToGraph, HashMap<Integer,String> speakerNumberToName,
    SqlGraphStoreAdministration store, Schema schema, PreparedStatement sqlMatchTranscriptContext,
    int wordsContext, LinkedHashSet<String> options, LinkedHashSet<String> layers)
    throws Exception {
    if (csvOut != null) {
      // start new record
      csvOut.println();
      if (options.contains("labbcat_title")) csvOut.print(context.getTitle());
      if (options.contains("labbcat_version")) csvOut.print(context.getVersion());
      if (options.contains("collection_name")) csvOut.print(searchName);
      if (options.contains("result_number")) {
        csvOut.print(
          Optional.ofNullable(result.getPrefix()).orElse("")
          .replace("-","") // strip hyphens
          .replaceAll("^0+","")); // strip leading zeroes
      }
    } else {
      // start match object
      jsonOut.writeStartObject();
    }
    // convert ag_id to transcript_id
    if (!agIdToGraph.containsKey(result.getGraphId())) {
      String[] systemLayers = { schema.getCorpusLayerId(), schema.getEpisodeLayerId() };
      Graph t = store.getTranscript("g_"+result.getGraphId(), systemLayers);
      agIdToGraph.put(result.getGraphId(), t);
      if (options.contains("series_length")) {
        PreparedStatement sqlSeriesLength = store.getConnection().prepareStatement(
          "SELECT COALESCE(MAX(transcript.family_offset + anchor.offset),0) AS length"
          + " FROM transcript"
          + " INNER JOIN anchor ON transcript.ag_id = anchor.ag_id"
          + " WHERE transcript.family_id = ?");
        sqlSeriesLength.setInt(1, (Integer)t.get("@family_id"));
        ResultSet rsSeriesLength = sqlSeriesLength.executeQuery();
        rsSeriesLength.next();
        t.put("@series_length", rsSeriesLength.getInt(1));
        rsSeriesLength.close();
        sqlSeriesLength.close();
      }
    }
    Graph t = agIdToGraph.get(result.getGraphId());
    
    // convert speaker_number to name
    if (!speakerNumberToName.containsKey(result.getSpeakerNumber())) {
      Annotation p = store.getParticipant("m_-2_"+result.getSpeakerNumber(), null);
      speakerNumberToName.put(result.getSpeakerNumber(), p.getLabel());
    }
    
    // convert anchor_ids to offsets
    String[] anchorIds = {
      "n_"+result.getStartAnchorId(), "n_"+result.getEndAnchorId() };
    Anchor[] anchors = store.getAnchors(
      agIdToGraph.get(result.getGraphId()).getId(), anchorIds);
    result.setStartOffset(anchors[0].getOffset());
    result.setEndOffset(anchors[1].getOffset());
    
    // get the match start/end tokens
    StringBuilder beforeMatch = new StringBuilder();
    StringBuilder text = new StringBuilder();
    StringBuilder afterMatch = new StringBuilder();
    String startTokenId = result.getMatchAnnotationUids().get("0");
    String endTokenId = result.getMatchAnnotationUids().get("1");
    String boundingTokenQuery = null;
    if (startTokenId != null) {
      if (endTokenId != null) {
        boundingTokenQuery = "id IN ('"+startTokenId+"','"+endTokenId+"')";
      } else { // one token only
        boundingTokenQuery = "id == '"+startTokenId+"'";
      }
    } else if (result.getTargetAnnotationUid() != null) { // no tokens, use target
      boundingTokenQuery = "id == '"+result.getTargetAnnotationUid()+"'";
    }
    if (boundingTokenQuery != null) {
      
      // get the matched text
      Annotation[] boundingTokens = store.getMatchingAnnotations(boundingTokenQuery);
      if (boundingTokens.length > 0) {
        // the label of the first token the start of the text
        text.append(boundingTokens[0].getLabel());
        if (boundingTokens.length > 1) { // there's a start and end token
          if (boundingTokens[0].getOrdinal() + 1 < boundingTokens[1].getOrdinal()) {
            // there are intervening tokens

            Annotation firstToken = boundingTokens[0];
            Annotation lastToken = boundingTokens[boundingTokens.length - 1];
            // get the context before the match
            sqlMatchTranscriptContext.setInt(1, result.getGraphId());
            sqlMatchTranscriptContext.setLong(
              2, Long.valueOf(firstToken.getParentId().replace("em_11_","")));
            sqlMatchTranscriptContext.setInt(3, firstToken.getOrdinal() + 1);
            sqlMatchTranscriptContext.setInt(4, lastToken.getOrdinal() - 1);
            if (wordsContext < 0) { // in line bounds
              sqlMatchTranscriptContext.setDouble(5, result.getStartOffset());
              sqlMatchTranscriptContext.setDouble(6, result.getEndOffset());
            }
            ResultSet rs = sqlMatchTranscriptContext.executeQuery();
            rs.next();
            text.append(" ");
            text.append(rs.getString(1));
            rs.close();
          } // there are intervening tokens
          // add final token to the text
          text.append(" ");
          text.append(boundingTokens[1].getLabel());
        } // there's a start and end token
        
        if (wordsContext != 0) { // get the context
          Annotation firstToken = boundingTokens[0];
          Annotation lastToken = boundingTokens[boundingTokens.length - 1];
          // get the context before the match
          sqlMatchTranscriptContext.setInt(1, result.getGraphId());
          sqlMatchTranscriptContext.setLong(
            2, Long.valueOf(firstToken.getParentId().replace("em_11_","")));
          sqlMatchTranscriptContext.setInt(3, firstToken.getOrdinal() - wordsContext);
          sqlMatchTranscriptContext.setInt(4, firstToken.getOrdinal() - 1);
          if (wordsContext < 0) { // in line bounds
            sqlMatchTranscriptContext.setInt(3, 0);
            sqlMatchTranscriptContext.setDouble(5, result.getStartOffset());
            sqlMatchTranscriptContext.setDouble(6, result.getEndOffset());
          }
          ResultSet rs = sqlMatchTranscriptContext.executeQuery();
          rs.next();
          beforeMatch.append(rs.getString(1));
          rs.close();
          
          // get the context after the match
          sqlMatchTranscriptContext.setInt(
            3, lastToken.getOrdinal() + 1);
          sqlMatchTranscriptContext.setInt(
            4, lastToken.getOrdinal() + wordsContext);
          if (wordsContext < 0) { // in line bounds
            sqlMatchTranscriptContext.setInt(4, Integer.MAX_VALUE);
            sqlMatchTranscriptContext.setDouble(5, result.getStartOffset());
            sqlMatchTranscriptContext.setDouble(6, result.getEndOffset());
          }
          rs = sqlMatchTranscriptContext.executeQuery();
          rs.next();
          afterMatch.append(rs.getString(1));
          rs.close();
        } // get the context
      } // there are bounding tokens                  
    } // there are match tokens
    if (layers.contains(schema.getRoot().getId())) {
      outputMatchAttribute(jsonOut, csvOut, "Transcript", t.getLabel());
    }
    if (options.contains("series_offset")) csvOut.print(t.get("@offset_in_series").toString());
    if (options.contains("series_length")) csvOut.print(t.get("@series_length").toString());
    if (layers.contains(schema.getParticipantLayerId())) {
      outputMatchAttribute(
        jsonOut, csvOut, "Participant", speakerNumberToName.get(result.getSpeakerNumber()));
    }
    if (layers.contains(schema.getCorpusLayerId()))  {
      outputMatchAttribute(
        jsonOut, csvOut, "Corpus",
        Optional.ofNullable(t.first(schema.getCorpusLayerId())).map(a->a.getLabel()).orElse(""));
    }
    if (layers.contains(schema.getEpisodeLayerId())) {
      outputMatchAttribute(
        jsonOut, csvOut, "Episode",
        Optional.ofNullable(t.first(schema.getEpisodeLayerId())).map(a->a.getLabel()).orElse(""));
    }
    if (options.contains("line_time")) {
      outputMatchAttribute(jsonOut, csvOut, "Line", result.getStartOffset());
    }
    if (options.contains("line_end_time"))  {
      outputMatchAttribute(jsonOut, csvOut, "LineEnd", result.getEndOffset());
    }
    if (options.contains("match"))  {
      outputMatchAttribute(jsonOut, csvOut, "MatchId", result.getId());
    }
    if (options.contains("target")) {
      outputMatchAttribute(jsonOut, csvOut, "TargetId", result.getTargetAnnotationUid());
    }
    if (options.contains("word_url"))  {
      outputMatchAttribute(
        jsonOut, csvOut, "URL",
        store.getId()
        + "transcript?transcript=" + URLEncoder.encode(t.getLabel(), "UTF-8")
        // skip to the first word 
        + "#" + Optional.ofNullable(result.getMatchAnnotationUids().get("0"))
        // or else the target
        .orElse(Optional.ofNullable(result.getTargetAnnotationUid())
                // or else the utterance
                .orElse(Optional.ofNullable(result.getDefiningAnnotationUid()) 
                        .orElse(""))));
    }
    if (options.contains("result_text")){
      outputMatchAttribute(jsonOut, csvOut, "BeforeMatch", beforeMatch.toString());
      outputMatchAttribute(jsonOut, csvOut, "Text", text.toString());
      outputMatchAttribute(jsonOut, csvOut, "AfterMatch", afterMatch.toString());
    }
  } // end of startMatch()
  
  /**
   * Sends output required for a single match field.
   * <p> Assumes that outputMatchStart has previously been called for this match.
   * @param jsonOut Generator for JSON output, or null for CSV output.
   * @param csvOut Printer for CSV output, or null for JSON output.
   * @param name Name of the field/attribute.
   * @param value String value of the field/attribute, or null.
   */
  private void outputMatchAttribute(
    JsonGenerator jsonOut, CSVPrinter csvOut, String name, String value)
    throws IOException {
    if (csvOut != null) {
      // send new field value
      csvOut.print(Optional.ofNullable(value).orElse(""));
    } else {
      // send the name/value
      jsonOut.write(name, value);
    }
  } // end of outputMatchAttribute()

  /**
   * Sends output required for a single match field.
   * <p> Assumes that outputMatchStart has previously been called for this match.
   * @param jsonOut Generator for JSON output, or null for CSV output.
   * @param csvOut Printer for CSV output, or null for JSON output.
   * @param name Name of the field/attribute.
   * @param value Double value of the field/attribute, or null.
   */
  private void outputMatchAttribute(
    JsonGenerator jsonOut, CSVPrinter csvOut, String name, Double value)
    throws IOException {
    if (csvOut != null) {
      // send field value
      csvOut.print(Optional.ofNullable(value).map(v->v.toString()).orElse(""));
    } else {
      // send the name/value
      jsonOut.write(name, value);
    }
  } // end of outputMatchAttribute()
  
  /**
   * Outputs the concatenated labels of the match for the given layer.
   * @param csvOut
   * @param schema
   * @param sqlMatchLayerLabels
   * @param multiWordMatches
   * @param result
   * @param layer
   */
  public void outputMatchLayerLabels(
    CSVPrinter csvOut, Schema schema, PreparedStatement sqlMatchLayerLabels,
    boolean multiWordMatches, IdMatch result,
    Layer layer) throws SQLException, IOException {
    if (multiWordMatches
        && schema.getWordLayerId().equals(layer.getParentId())  // word annotation
        && !layer.getId().equals("segment")) {
      String firstTokenId = result.getMatchAnnotationUids().get("0");
      long firstWordAnnotationId = Long.parseLong(
        firstTokenId.replace("ew_0_",""));
      String lastTokenId = result.getMatchAnnotationUids().get("1");
      long lastWordAnnotationId = Long.parseLong(
        lastTokenId.replace("ew_0_",""));
      sqlMatchLayerLabels.setLong(1, lastWordAnnotationId);
      sqlMatchLayerLabels.setInt(2, (Integer)layer.get("layer_id"));
      sqlMatchLayerLabels.setLong(3, firstWordAnnotationId);
      ResultSet rsMatchLayerLabels = sqlMatchLayerLabels.executeQuery();
      try {
        if (rsMatchLayerLabels.next()) {
          csvOut.print(rsMatchLayerLabels.getString(1));
        } else {
          csvOut.print("");
        }
      } finally {
        rsMatchLayerLabels.close();
      }
    } // last layer was a word annotation layer
  } // end of outputMatchLayerLabels()
  
  /**
   * Sends output required for ending a match record.
   * <p> Assumes that outputMatchStart has previously been called for this match.
   * @param jsonOut Generator for JSON output, or null for CSV output.
   * @param csvOut Printer for CSV output, or null for JSON output.
   */
  void outputMatchEnd(JsonGenerator jsonOut, CSVPrinter csvOut) {
    if (csvOut != null) {
      // nothing to output - new line happens in outputMatchStart
    } else {
      // end match object
      jsonOut.writeEnd();
    }
  } // end of outputMatchEnd()  

  /**
   * Sends output required for ending all match records.
   * <p> Assumes that outputMatchEnd has previously been called for the last match.
   * @param jsonOut Generator for JSON output, or null for CSV output.
   * @param csvOut Printer for CSV output, or null for JSON output.
   */
  void outputMatchesEnd(JsonGenerator jsonOut, CSVPrinter csvOut) {
    if (csvOut != null) {
      // nothing to output
    } else {
      // end match object
      jsonOut.writeEnd();
    }
  } // end of outputMatchesEnd()  

  /**
   * Sends output for ending the results stream successfully.
   * <p> Assumes outputMatchesEnd has been called.
   * @param jsonOut Generator for JSON output, or null for CSV output.
   * @param csvOut Printer for CSV output, or null for JSON output.
   */
  void outputSuccessfulEnd(JsonGenerator jsonOut, CSVPrinter csvOut)
    throws IOException {
    if (csvOut != null) {
      csvOut.close();
    } else {
      // finish the JSON envelope
      endSuccessResult(jsonOut, null);
    }
  } // end of outputSuccessfulEnd()

  /**
   * Sends output for ending the results stream with an error.
   * <p> Errors can occur at any point, so no assumptions can be made about previous output calls.
   * @param jsonOut Generator for JSON output, or null for CSV output.
   * @param csvOut Printer for CSV output, or null for JSON output.
   * @param error The error message to return.
   */
  void outputFailedEnd(
    JsonGenerator jsonOut, CSVPrinter csvOut, String error)
    throws IOException {
    if (csvOut != null) {
      // Send column headers
      csvOut.println();
      csvOut.print("ERROR: " + error);
      csvOut.println();
      csvOut.close();
    } else {
      // finish the JSON envelope
      endFailureResult(jsonOut, error);
    }
  } // end of outputSuccessfulEnd()

} // end of class Results
