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
      try {
        results = new SqlSearchResults(results, store.getConnection());
        results.hasNext(); // load size etc.
        JsonGenerator jsonOut = startResult(Json.createGenerator(response.getWriter()), false);
        try {
          String name = "results";
          if (search.getMatrix() != null) {
            name = search.getMatrix().getDescription();
          }
          if (name == null) name = search.getDescription();
          log("name: " + name);
          jsonOut.write("name", name);
          jsonOut.writeStartArray("matches");
          try {
            if (pageLength == null) pageLength = results.size();
            if (pageNumber == null) pageNumber = Integer.valueOf(0);
            log("pageLength: " + pageLength + " pageNumber " + pageNumber);
            int seekTo = (pageLength * pageNumber) + 1;
            log("seekTo: " + seekTo);
            if (seekTo > 1) results.seek(seekTo);
            // cache graph/participant IDs to save database lookups
            HashMap<Integer,String> agIdToName = new HashMap<Integer,String>();
            HashMap<Integer,String> speakerNumberToName = new HashMap<Integer,String>();
            for (int r = 0; r < pageLength && results.hasNext(); r++) {
              search.keepAlive(); // prevent the task from dying while we're still interested
              jsonOut.writeStartObject(); // match
              try {
                
                String matchId = results.next();
                log("matchId: " + matchId);
                jsonOut.write("MatchId", matchId);
                
                IdMatch result = new IdMatch(matchId);
                // convert ag_id to transcript_id
                if (!agIdToName.containsKey(result.getGraphId())) {
                  Graph t = store.getTranscript("g_"+result.getGraphId(), null);
                  agIdToName.put(result.getGraphId(), t.getLabel());
                }
                jsonOut.write("Transcript", agIdToName.get(result.getGraphId()));
                
                // convert speaker_number to name
                if (!speakerNumberToName.containsKey(result.getSpeakerNumber())) {
                  Annotation p = store.getParticipant("m_-2_"+result.getSpeakerNumber(), null);
                  speakerNumberToName.put(result.getSpeakerNumber(), p.getLabel());
                }
                jsonOut.write("Participant", speakerNumberToName.get(result.getSpeakerNumber()));
                
                // convert anchor_ids to offsets
                String[] anchorIds = {
                  "n_"+result.getStartAnchorId(), "n_"+result.getEndAnchorId() };
                Anchor[] anchors = store.getAnchors(
                  agIdToName.get(result.getGraphId()), anchorIds);
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
                      if (wordsContext > 0) { // number of words
                        // get the context before the match
                        String contextQuery = "layer == '"+firstToken.getLayerId()+"'"
                          +" && parentId == '"+firstToken.getParentId()+"'"
                          +" && ordinal >= "+(firstToken.getOrdinal() - wordsContext)
                          +" && ordinal < "+firstToken.getOrdinal();
                        Annotation[] contextTokens = store.getMatchingAnnotations(contextQuery);
                        Arrays.sort(contextTokens, Comparator.comparingInt(a->a.getOrdinal()));
                        for (Annotation token : contextTokens) {
                          // add the token to the text
                          if (beforeMatch.length() > 0) beforeMatch.append(" ");
                          beforeMatch.append(token.getLabel());
                        } // next token
                        
                        // get the context after the match
                        contextQuery = "layer == '"+lastToken.getLayerId()+"'"
                          +" && parentId == '"+lastToken.getParentId()+"'"
                          +" && ordinal > "+lastToken.getOrdinal()
                          +" && ordinal <= "+(lastToken.getOrdinal() + wordsContext);
                        contextTokens = store.getMatchingAnnotations(contextQuery);
                        Arrays.sort(contextTokens, Comparator.comparingInt(a->a.getOrdinal()));
                        for (Annotation token : contextTokens) {
                          // add the token to the text
                          if (afterMatch.length() > 0) afterMatch.append(" ");
                          afterMatch.append(token.getLabel());
                        } // next token
                        
                      } else { // whole utterance

                        if (result.getStartOffset() != null) {
                          // get the context before the match
                          String contextQuery = "layer == '"+firstToken.getLayerId()+"'"
                            +" && parentId == '"+firstToken.getParentId()+"'"
                            +" && start.offset >= "+result.getStartOffset()
                            +" && ordinal < "+firstToken.getOrdinal();
                          Annotation[] contextTokens = store.getMatchingAnnotations(contextQuery);
                          Arrays.sort(contextTokens, Comparator.comparingInt(a->a.getOrdinal()));
                          for (Annotation token : contextTokens) {
                            // add the token to the text
                            if (beforeMatch.length() > 0) beforeMatch.append(" ");
                            beforeMatch.append(token.getLabel());
                          } // next token
                        } // result.startOffset != null

                        if (result.getEndOffset() != null) {
                          // get the context after the match
                          String contextQuery = "layer == '"+lastToken.getLayerId()+"'"
                            +" && parentId == '"+lastToken.getParentId()+"'"
                            +" && ordinal > "+lastToken.getOrdinal()
                            +" && end.offset <= "+result.getEndOffset();
                          Annotation[] contextTokens = store.getMatchingAnnotations(contextQuery);
                          Arrays.sort(contextTokens, Comparator.comparingInt(a->a.getOrdinal()));
                          for (Annotation token : contextTokens) {
                            // add the token to the text
                            if (afterMatch.length() > 0) afterMatch.append(" ");
                            afterMatch.append(token.getLabel());
                          } // next token
                        } // result.endOffset != null
                        
                      } // whole utterance
                    } // get the context
                  } // there are bounding tokens                  
                } // there are match tokens
                jsonOut.write("BeforeMatch", beforeMatch.toString());
                jsonOut.write("Text", text.toString());
                jsonOut.write("AfterMatch", afterMatch.toString());
                
              } finally {
                jsonOut.writeEnd(); // match
              }
            } // next result
          } finally {
            jsonOut.writeEnd(); // end "matches"
          }
          endSuccessResult(request, jsonOut, null);
        } catch (Exception x) {
          log(x.toString());
          endFailureResult(request, jsonOut, x.getMessage());
        }
      } finally {
        cacheStore(store);
      }
      
    } catch(Exception ex) {
      throw new ServletException(ex);
    }
  }
  
  private static final long serialVersionUID = -1;
} // end of class Results
