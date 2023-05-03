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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
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
    nzilbb.util.Timers timer = new nzilbb.util.Timers();
    timer.start("GET");
    response.setContentType("application/json"); // TODO support CSV
    response.setCharacterEncoding("UTF-8");

    // parameters
    String threadId = request.getParameter("threadId");
    if (threadId == null) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      writeResponse(response, failureResult(request, "No task ID specified."));
      return;
    }
    int wordsContext = 0;
    if (request.getParameter("words_context") != null) {
      try {
         wordsContext = Integer.parseInt(request.getParameter("words_context"));
      } catch(Exception exception) {
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
    if (contentType == null) contentType = "application/json";

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
          "SELECT GROUP_CONCAT(word.label ORDER BY ordinal_in_turn SEPARATOR ' ')"
          + " FROM annotation_layer_0 word"
          + (wordsContext < 0?" INNER JOIN anchor start ON word.start_anchor_id = start.anchor_id"
             :"")
          + " WHERE word.ag_id = ?"
          + " AND turn_annotation_id = ?"
          + " AND ordinal_in_turn >= ? AND ordinal_in_turn <= ?"
          + (wordsContext < 0?" AND start.offset >= ? AND start.offset < ?"
             :"")
          + " ORDER BY ordinal_in_turn");
        
        JsonGenerator jsonOut = startResult(Json.createGenerator(response.getWriter()), false);
        try {
          String name = "results";
          if (search.getMatrix() != null) {
            name = search.getMatrix().getDescription();
          }
          if (name == null) name = search.getDescription();
          jsonOut.write("name", name);
          jsonOut.writeStartArray("matches");
          try {
            if (pageLength == null) pageLength = results.size();
            if (pageNumber == null) pageNumber = Integer.valueOf(0);
            int seekTo = (pageLength * pageNumber) + 1;
            if (seekTo > 1) results.seek(seekTo);
            // cache graph/participant IDs to save database lookups
            HashMap<Integer,Graph> agIdToGraph = new HashMap<Integer,Graph>();
            HashMap<Integer,String> speakerNumberToName = new HashMap<Integer,String>();
            timer.start("results");
            for (int r = 0; r < pageLength && results.hasNext(); r++) {
              search.keepAlive(); // prevent the task from dying while we're still interested
              jsonOut.writeStartObject(); // match
              try {
                
                timer.start("results.next");
                String matchId = results.next();
                timer.end("results.next");
                jsonOut.write("MatchId", matchId);
                
                timer.start("IdMatch");
                IdMatch result = new IdMatch(matchId);
                timer.end("IdMatch");
                // convert ag_id to transcript_id
                if (!agIdToGraph.containsKey(result.getGraphId())) {
                  timer.start("getTranscript");
                  Graph t = store.getTranscript("g_"+result.getGraphId(), corpusLayer);
                  timer.end("getTranscript");
                  agIdToGraph.put(result.getGraphId(), t);
                }
                Graph t = agIdToGraph.get(result.getGraphId());
                jsonOut.write("Transcript", t.getLabel());
                jsonOut.write("Corpus", t.first(schema.getCorpusLayerId()).getLabel());
                
                // convert speaker_number to name
                if (!speakerNumberToName.containsKey(result.getSpeakerNumber())) {
                  timer.start("getParticipant");
                  Annotation p = store.getParticipant("m_-2_"+result.getSpeakerNumber(), null);
                  timer.end("getParticipant");
                  speakerNumberToName.put(result.getSpeakerNumber(), p.getLabel());
                }
                jsonOut.write("Participant", speakerNumberToName.get(result.getSpeakerNumber()));
                
                // convert anchor_ids to offsets
                String[] anchorIds = {
                  "n_"+result.getStartAnchorId(), "n_"+result.getEndAnchorId() };
                timer.start("getAnchors");
                Anchor[] anchors = store.getAnchors(
                  agIdToGraph.get(result.getGraphId()).getId(), anchorIds);
                timer.end("getAnchors");
                result.setStartOffset(anchors[0].getOffset());
                result.setEndOffset(anchors[1].getOffset());
                jsonOut.write("Line", result.getStartOffset());
                jsonOut.write("LineEnd", result.getEndOffset());

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
                  timer.start("boundingTokenQuery");
                  Annotation[] boundingTokens = store.getMatchingAnnotations(boundingTokenQuery);
                  timer.end("boundingTokenQuery");
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
                        timer.start("interveningTokens");
                        Annotation[] interveningTokens = store.getMatchingAnnotations(
                          interveningTokenQuery);
                        timer.end("interveningTokens");
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
                timer.start("texts out");
                jsonOut.write("BeforeMatch", beforeMatch.toString());
                jsonOut.write("Text", text.toString());
                jsonOut.write("AfterMatch", afterMatch.toString());
                timer.end("texts out");
                
              } finally {
                jsonOut.writeEnd(); // match
              }
            } // next result
            timer.end("results");
          } finally {
            jsonOut.writeEnd(); // end "matches"
            sqlMatchTranscriptContext.close();
          }
          endSuccessResult(request, jsonOut, null);
        } catch (Exception x) {
          log(x.toString());
          endFailureResult(request, jsonOut, x.getMessage());
        }
      } finally {
        cacheStore(store);
        timer.end("GET");
        log(timer.toString());
      }
      
    } catch(Exception ex) {
      throw new ServletException(ex);
    }
  }
  
  private static final long serialVersionUID = -1;
} // end of class Results
