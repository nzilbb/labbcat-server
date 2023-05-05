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
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import nzilbb.ag.Schema;
import nzilbb.labbcat.server.db.OneQuerySearch;
import nzilbb.labbcat.server.db.SqlGraphStore;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.labbcat.server.db.StoreCache;
import nzilbb.labbcat.server.search.Column;
import nzilbb.labbcat.server.search.Matrix;

/**
 * <tt>/api/search</tt>
 * : Starts a search task to identify matches for the given search matrix.
 * <p> The search matrix defines a pattern of annotations to identify, which transcripts
 * to search, and which participant's utterances to target. Additional search parameters
 * can also be defined; whether to return only aligned matches, whether main-participants
 * only are being targeted, etc.
 * <p><b>Input HTTP parameters</b>:
 * <ul>
 *  <li><i>searchJson</i> - A JSON-encoded reprentation of the search matrix. </li>
 *  <li><i>search</i> - An alternative to <i>searchJson</i>, this encodes an
        orthography-layer-only search as a plain-text string. </li>
 *  <li><i>only_main_speaker</i> - Optional: "true" if only main-participant utterances should be
 *      searched, absent otherwise. </li>
 *  <li><i>only_aligned</i> - Optional: "true" if only aligned tokens should be returned
 *      (i.e. having an anchor confidence &ge; 50), or absent to include un-aligned tokens.</li> 
 *  <li><i>matches_per_transcript</i> - Optional maximum number of matches per transcript
 *      to return, or absent to return all matches in each transcript.</li>
 *  <li><i>overlap_threshold</i> - Optional (integer) percentage overlap with other
 *      utterances before simultaneous speech is excluded, or absent to include
 *      overlapping speech.</li>  
 *  <li><i>num_transcripts</i> - Optional maximum number of transcripts to include results
 *      for.</li>
 *  <li><i>suppress_results</i> - Optional: "true" if to return a summary of results only,
 *      instead of listing specific matches.</li>
 *  <li><i>participant_expression</i> - Optional AGQL expression for defining which
 *      participants to search the utterances of,
 *      e.g. <q>first('participant_gender').label == 'NB'</q></li>
 *  <li><i>transcript_expression</i> - Optional AGQL expression for defining which
 *      transcript to search,
 *      e.g. <q>['CC','IA'].includesAny(labels('corpus'))</q></li>
 * </ul>
 * <br><b>Output</b>: a JSON-encoded response object of the usual structure for which the
 * "model" is an object with a "threadId" attribute, which is the ID of the server task to
 * monitor for results. e.g. 
 * <pre>{
 *    "title":"search",
 *    "version" : "20230502.0924",
 *    "code" : 0,
 *    "errors" : [],
 *    "messages" : [],
 *    "model" : {
 *        "threadId" : "80"
 *    }
 * }</pre>
 * <br> The task, when finished, will output a URL for accessing the matches of the search.
 * @author Robert Fromont
 */
@WebServlet({"/api/search"} )
public class Search extends LabbcatServlet { // TODO unit test
  
  /**
   * Constructor
   */
  public Search() {
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
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    // parameters
    Matrix matrix = new Matrix();
    String searchJson = request.getParameter("searchJson");
    String search = request.getParameter("search");
    if (searchJson != null) {
      matrix.fromJsonString(searchJson);
    } else if (search != null) {
      matrix.fromLegacyString(search);
    } else {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      writeResponse(response, failureResult(request, "No search matrix specified."));
      return;
    }
    if (request.getParameter("participant_expression") != null) {
      matrix.setParticipantQuery(request.getParameter("participant_expression"));
    } else  if (request.getParameter("participant_id") != null) {
      matrix.setParticipantQuery(
        "id IN ("
        +Arrays.stream(request.getParameterValues("participant_id"))
        .map(id->"'"+id+"'")
        .collect(Collectors.joining(","))
        +")");
    }
    if (request.getParameter("transcript_expression") != null) {
      matrix.setTranscriptQuery(request.getParameter("transcript_expression"));
    }
    OneQuerySearch task = new OneQuerySearch();
    task.setMatrix(matrix);
    if (request.getParameter("only_main_speaker") != null) task.setMainParticipantOnly(true);
    if (request.getParameter("suppress_results") != null) task.setSuppressResults(true);
    if (request.getParameter("only_aligned") != null) task.setAnchorConfidenceThreshold((byte)50);
    if (request.getParameter("matches_per_transcript") != null) {
      try {
        task.setMatchesPerTranscript(
          Integer.valueOf(request.getParameter("matches_per_transcript")));
      } catch(NumberFormatException exception) {
        writeResponse(response, failureResult(
                        request, "Invalid matches per transcript \"{0}\": {1}",
                        request.getParameter("matches_per_transcript"),
                        exception.getMessage()));
        return;
      }
    }
    if (request.getParameter("overlap_threshold") != null) {
      try {
        task.setOverlapThreshold(
          Integer.valueOf(request.getParameter("overlap_threshold")));
      } catch(NumberFormatException exception) {
        writeResponse(response, failureResult(
                        request, "Invalid overlap threshold \"{0}\": {1}",
                        request.getParameter("overlap_threshold"),
                        exception.getMessage()));
        return;
      }
    }
    if (request.getParameter("num_transcripts") != null) {
      try {
        task.setMaxMatches(
          Integer.valueOf(request.getParameter("num_transcripts")));
      } catch(NumberFormatException exception) {
        writeResponse(response, failureResult(
                        request, "Invalid maximum number of matches \"{0}\": {1}",
                        request.getParameter("num_transcripts"),
                        exception.getMessage()));
        return;
      }
    }
    
    try {
      task.setStoreCache(new StoreCache() {
          public SqlGraphStore get() {
            try {
              return getStore(request);
            } catch(Exception exception) {
              System.err.println("Search.StoreCache: " + exception);
              return null;
            }
          }
          public void accept(SqlGraphStore store) {
            cacheStore((SqlGraphStoreAdministration)store);
          }
        });
      if (request.getRemoteUser() != null) {	
        task.setWho(request.getRemoteUser());
        // admin users have access to everything
        if (!isUserInRole("admin", request, task.getStore().getConnection())
            // if they're using using access permissions in general
            && task.getStore().getPermissionsSpecified()) {
          // other users may have restricted access to some things
          task.setRestrictByUser(request.getRemoteUser());
        } // not an admin user
      } else {
        task.setWho(request.getRemoteHost());
      }
      
      String validationError = task.validate();
      if (validationError != null) {
        writeResponse(response, failureResult(request, validationError));
      } else {
        task.start();
        
        // return its ID
        JsonObjectBuilder jsonResult = Json.createObjectBuilder()
          .add("threadId", ""+task.getId());
        writeResponse(
          response, successResult(request, jsonResult.build(), null));
      } // valid search
    } catch(Exception ex) {
      throw new ServletException(ex);
    }
  }
  
  private static final long serialVersionUID = -1;
} // end of class Search
