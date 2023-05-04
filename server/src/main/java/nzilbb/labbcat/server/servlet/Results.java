//
// Copyright 2023 New Zealand Institute of Language, Brain and Behaviour, 
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
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Optional;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.stream.JsonGenerator;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import nzilbb.ag.Anchor;
import nzilbb.ag.Annotation;
import nzilbb.ag.Graph;
import nzilbb.ag.Schema;
import nzilbb.labbcat.server.db.IdMatch;
import nzilbb.labbcat.server.db.OneQuerySearch;
import nzilbb.labbcat.server.db.SqlGraphStore;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.labbcat.server.db.SqlSearchResults;
import nzilbb.labbcat.server.db.StoreCache;
import nzilbb.labbcat.server.search.Column;
import nzilbb.labbcat.server.search.Matrix;
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
 *  <li><i>words_context</i> - How many words of context before and after the match to
 *      include in the result text. </li>
 *  <li><i>pageLength</i> - How many results to return at a time. </li>
 *  <li><i>pageNumber</i> - The zero-based number of the page of results to return. </li>
 *  <li><i>content-type</i> - (Optional) A parameter to specify the content type of the
 *      response, whose value can be <q>text/csv</q> or <q>application/json</q> - this can
 *      also be achieved by setting the value of the <i>Accept</i> request header. If
 *      neither the request parameter nor the requets header are set, the response is
 *      <q>application/json</q> </li>
 *  <li><i>todo</i> - (Optional) A legacy parameter whose value can be <q>csv</q> to
 *      specify that the content-type of the result be <q>text/csv</q></li>
 * </ul>
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
@WebServlet({"/api/results"} )
public class Results extends LabbcatServlet { // TODO unit test
  
  /**
   * Constructor
   */
  public Results() {
  } // end of constructor
  
  // Servlet methods
  
  /**
   * The POST method for the servlet.
   * <p> This expects a multipart request body with parameters as defined above.
   * @param request HTTP request
   * @param response HTTP response
   */
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    response.setCharacterEncoding("UTF-8");

    // parameters
    String threadId = request.getParameter("threadId");
    if (threadId == null) {
      response.setContentType("application/json");
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      writeResponse(response, failureResult(request, "No task ID specified."));
      return;
    }
    int wordsContext = 0;
    if (request.getParameter("words_context") != null) {
      try {
         wordsContext = Integer.parseInt(request.getParameter("words_context"));
      } catch(Exception exception) {
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        writeResponse(response, failureResult(
                        request, "Invalid words context: {0}",
                        request.getParameter("words_context")));
        return;
      }
   }
    Integer pageLength = null;
    if (request.getParameter("pageLength") != null) {
      try {
        pageLength = Integer.valueOf(request.getParameter("pageLength"));
      } catch(NumberFormatException x) {
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        writeResponse(response, failureResult(
                        request, "Invalid pageLength: {0}", request.getParameter("pageLength")));
        return;
      }
    }
    Integer pageNumber = Integer.valueOf(0);
    if (request.getParameter("pageNumber") != null) {
      try {
        pageNumber = Integer.valueOf(request.getParameter("pageNumber"));
      } catch(NumberFormatException x) {
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        writeResponse(response, failureResult(
                        request, "Invalid pageNumber: {0}", request.getParameter("pageNumber")));
        return;
      }
    }
    String contentType = request.getParameter("todo");
    if (contentType == null) {
      contentType = request.getParameter("content-type");
      if (contentType == null) {
        contentType = request.getHeader("Accept");
      }
    }
    if ("csv".equalsIgnoreCase(contentType)
        || "text/csv".equalsIgnoreCase(contentType)) {
      contentType = "text/csv";
    } else {
      contentType = "application/json";
    }
    response.setContentType(contentType);
    // output will be either CSV or JSON - define an appropriate output streamer
    boolean sendCsv = contentType.equals("text/csv");
    CSVPrinter csvOut = sendCsv?new CSVPrinter(
      response.getWriter(), CSVFormat.EXCEL.withDelimiter( 
        // guess the best delimiter - comma if English speaking user, tab otherwise
        request.getHeader("Accept-Language").contains("en")?',':'\t')):null;
    JsonGenerator jsonOut = !sendCsv?Json.createGenerator(response.getWriter()):null;
      
    try {
      Task task = Task.findTask(Long.valueOf(threadId));
      if (task == null) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        writeResponse(response, failureResult(
                        request, "Invalid task ID: {0}", "\""+threadId+"\""));
        return;
      } else if (!(task instanceof SearchTask)) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        writeResponse(response, failureResult(
                        request, "Invalid task ID: {0}", task.getClass().getName()));
        return;
      }
      SearchTask search = (SearchTask)task;
      search.keepAlive(); // prevent the task from dying while we're still interested
      SqlSearchResults results = (SqlSearchResults)search.getResults();
      // the original results object presumably has a dead connection
      // we want a new copy of our own
      SqlGraphStoreAdministration store = getStore(request);
      Schema schema = store.getSchema();
      String[] corpusLayer = { schema.getCorpusLayerId() };

      try {
        Connection connection = store.getConnection();
        results = new SqlSearchResults(results, connection);
        results.hasNext(); // load size etc.

        PreparedStatement sqlMatchTranscriptContext = connection.prepareStatement(
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
        
        try {
          String searchName = "";
          if (search.getMatrix() != null) {
            searchName = search.getMatrix().getDescription();
          }
          if (searchName == null) searchName = search.getDescription();
          
          if (sendCsv) {
            String fileName = IO.SafeFileNameUrl(searchName);
            if (fileName.length() > 150) fileName = fileName.substring(0, 150);
            fileName = "results_" + fileName + ".csv";
            response.setHeader(
              "Content-Disposition", "attachment; filename=" + fileName.toString());            
          }

          outputStart(jsonOut, csvOut, searchName);
          try {
            if (pageLength == null) pageLength = results.size();
            if (pageNumber == null) pageNumber = Integer.valueOf(0);
            int seekTo = (pageLength * pageNumber) + 1;
            if (seekTo > 1) results.seek(seekTo);
            // cache graph/participant IDs to save database lookups
            HashMap<Integer,Graph> agIdToGraph = new HashMap<Integer,Graph>();
            HashMap<Integer,String> speakerNumberToName = new HashMap<Integer,String>();
            for (int r = 0; r < pageLength && results.hasNext(); r++) {
              search.keepAlive(); // prevent the task from dying while we're still interested
              try {
                
                String matchId = results.next();
                IdMatch result = new IdMatch(matchId);
                
                outputMatchStart(jsonOut, csvOut, searchName, result);
                // convert ag_id to transcript_id
                if (!agIdToGraph.containsKey(result.getGraphId())) {
                  Graph t = store.getTranscript("g_"+result.getGraphId(), corpusLayer);
                  agIdToGraph.put(result.getGraphId(), t);
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
                        String interveningTokenQuery =
                          "layer == '"+boundingTokens[0].getLayerId()+"'"
                          +" && parentId == '"+boundingTokens[0].getParentId()+"'"
                          +" && ordinal > "+boundingTokens[0].getOrdinal()
                          +" && ordinal < "+boundingTokens[1].getOrdinal();
                        Annotation[] interveningTokens = store.getMatchingAnnotations(
                          interveningTokenQuery);
                        Arrays.sort(
                          interveningTokens, Comparator.comparingInt(a->a.getOrdinal()));
                        for (Annotation token : interveningTokens) {
                          // add the token to the text
                          text.append(" ");
                          text.append(token.getLabel());
                        }
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
                      sqlMatchTranscriptContext.setInt(
                        3, firstToken.getOrdinal() - wordsContext);
                      sqlMatchTranscriptContext.setInt(
                        4, firstToken.getOrdinal() - 1);
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
                outputMatchAttribute(jsonOut, csvOut, "Transcript", t.getLabel());
                outputMatchAttribute(
                  jsonOut, csvOut,
                  "Participant", speakerNumberToName.get(result.getSpeakerNumber()));
                outputMatchAttribute(
                  jsonOut, csvOut, "Corpus", t.first(schema.getCorpusLayerId()).getLabel());
                outputMatchAttribute(jsonOut, csvOut, "Line", result.getStartOffset());
                outputMatchAttribute(jsonOut, csvOut, "LineEnd", result.getEndOffset());
                outputMatchAttribute(jsonOut, csvOut, "MatchId", matchId);
                outputMatchAttribute(
                  jsonOut, csvOut, "URL",
                  store.getId()
                  + "transcript?transcript=" + URLEncoder.encode(t.getLabel(), "UTF-8")
                  + "#" + Optional.ofNullable(result.getTargetAnnotationUid())
                  .orElse(Optional.ofNullable(result.getDefiningAnnotationUid())
                          .orElse("")));
                outputMatchAttribute(jsonOut, csvOut, "BeforeMatch", beforeMatch.toString());
                outputMatchAttribute(jsonOut, csvOut, "Text", text.toString());
                outputMatchAttribute(jsonOut, csvOut, "AfterMatch", afterMatch.toString());
                
              } finally {
                outputMatchEnd(jsonOut, csvOut); // end this match
              }
            } // next result
          } finally {
            outputMatchesEnd(jsonOut, csvOut); // end all matches
            sqlMatchTranscriptContext.close();
          }
          outputSuccessfulEnd(jsonOut, csvOut, request);
        } catch (Exception x) {
          log(x.toString());
          x.printStackTrace(System.err);
          outputFailedEnd(jsonOut, csvOut, request, x.getMessage());
        }
      } finally {
        results.close();
        cacheStore(store);
      }
      
    } catch(Exception ex) {
      throw new ServletException(ex);
    }
  }
  
  /**
   * Sends output for starting the results stream.
   * <p> Assumes no output has yet been written.
   * @param jsonOut Generator for JSON output, or null for CSV output.
   * @param csvOut Printer for CSV output, or null for JSON output.
   * @param searchName The name search that produced the results.
   */
  void outputStart(JsonGenerator jsonOut, CSVPrinter csvOut, String searchName)
    throws IOException {
    if (csvOut != null) {
      // Send column headers
      csvOut.print("SearchName");
      csvOut.print("Number");
      csvOut.print("Transcript");
      csvOut.print("Speaker");
      csvOut.print("Corpus");
      csvOut.print("Line");
      csvOut.print("LineEnd");
      csvOut.print("MatchId");
      csvOut.print("URL");
      csvOut.print("Before Match");
      csvOut.print("Text");
      csvOut.print("After Match");
    } else {
      // initialise JSON envelope
      startResult(jsonOut, false);
      // set initial structure of model
      jsonOut.write("name", searchName);
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
   */
  void outputMatchStart(
    JsonGenerator jsonOut, CSVPrinter csvOut, String searchName, IdMatch result)
    throws IOException {
    if (csvOut != null) {
      // start new record
      csvOut.println();
      csvOut.print(searchName);
      csvOut.print(
        Optional.ofNullable(result.getPrefix()).orElse("")
        .replace("-","") // strip hyphens
        .replaceAll("^0+","")); // strip leading zeroes
    } else {
      // start match object
      jsonOut.writeStartObject();
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
      // send new field value TODO
      csvOut.print(Optional.ofNullable(value).map(v->v.toString()).orElse(""));
    } else {
      // send the name/value
      jsonOut.write(name, value);
    }
  } // end of outputMatchAttribute()

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
   * @param request The HTTP request.
   */
  void outputSuccessfulEnd(JsonGenerator jsonOut, CSVPrinter csvOut, HttpServletRequest request)
    throws IOException {
    if (csvOut != null) {
      csvOut.close();
    } else {
      // finish the JSON envelope
      endSuccessResult(request, jsonOut, null);
    }
  } // end of outputSuccessfulEnd()

  /**
   * Sends output for ending the results stream with an error.
   * <p> Errors can occur at any point, so no assumptions can be made about previous output calls.
   * @param jsonOut Generator for JSON output, or null for CSV output.
   * @param csvOut Printer for CSV output, or null for JSON output.
   * @param request The HTTP request.
   * @param error The error message to return.
   */
  void outputFailedEnd(
    JsonGenerator jsonOut, CSVPrinter csvOut, HttpServletRequest request, String error)
    throws IOException {
    if (csvOut != null) {
      // Send column headers
      csvOut.println();
      csvOut.print("ERROR: " + error);
      csvOut.println();
      csvOut.close();
    } else {
      // finish the JSON envelope
      endFailureResult(request, jsonOut, error);
    }
  } // end of outputSuccessfulEnd()

  private static final long serialVersionUID = -1;
} // end of class Results
