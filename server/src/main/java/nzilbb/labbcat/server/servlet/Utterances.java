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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
import nzilbb.labbcat.server.db.AllUtterancesTask;
import nzilbb.labbcat.server.db.SqlGraphStore;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.labbcat.server.db.StoreCache;
import nzilbb.labbcat.server.search.Column;
import nzilbb.labbcat.server.search.Matrix;

/**
 * <tt>/api/utterances</tt>
 * : Starts a task to identify targeted utterances for given participants.
 * <p><b>Input HTTP parameters</b>:
 * <ul>
 *  <li><i>participant_expression</i> - AGQL expression for defining which
 *      participants to search the utterances of,
 *      e.g. <q>first('participant_gender').label == 'NB'</q></li>
 *  <li><i>id</i> - A list of participant IDs, as an alternative to the
 *      <i>participant_expression</i> parameter.</li> 
 *  <li><i>transcript_expression</i> - Optional AGQL expression for defining which
 *      transcript to search,
 *      e.g. <q>['CC','IA'].includesAny(labels('corpus'))</q></li>
 *  <li><i>only_main_speaker</i> - Optional: "true" if only main-participant utterances should be
 *      searched, absent otherwise. </li>
 *  <li><i>redirect</i> - Optional: "true" if the request should redirect to the
 *      user-interface for monitoring the task, rather than returning a JSON body. </li>
 *  <li><i>speaker_number</i> - For backwards compatibility, a list of participant
 *      speaker_numbers as an alternative to the <i>participant_expression</i> parameter.</li>
 *  <li><i>transcript_type</i> - For backwards compatibility, a list of transcript_types
 *      as an alternative to the <i>transcript_expression</i> parameter.</li>
 * </ul>
 * <br><b>Output</b>: a JSON-encoded response object of the usual structure for which the
 * "model" is an object with a "threadId" attribute, which is the ID of the server task to
 * monitor for results. e.g. 
 * <pre>{
 *    "title":"utterances",
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
@WebServlet({"/api/utterances"} )
public class Utterances extends LabbcatServlet { // TODO unit test
  
  /**
   * Constructor
   */
  public Utterances() {
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
    AllUtterancesTask task = new AllUtterancesTask();
    task.setParticipantQuery(request.getParameter("participant_expression"));
    if (task.getParticipantQuery() == null) {
      if (request.getParameter("id") != null) {
        task.setParticipantQuery(
          "id IN ("
          +Arrays.stream(request.getParameterValues("id"))
          .map(id->"'"+id+"'")
          .collect(Collectors.joining(","))
          +")");
      } else if (request.getParameter("speaker_number") != null) {
        task.setParticipantQuery(
          "speaker_number IN ("
          +Arrays.stream(request.getParameterValues("speaker_number"))
          .collect(Collectors.joining(","))
          +")");
      } else {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        writeResponse(response, failureResult(request, "No participants specified.")); // TODO i18n
        return;
      }
    }
    task.setTranscriptQuery(request.getParameter("transcript_expression"));
    try {
      final SqlGraphStoreAdministration store = getStore(request);
      if (task.getTranscriptQuery() == null && request.getParameter("transcript_type") != null) {
        // parameter values are numeric transcript_type.type_id values, but we need the text labels
        PreparedStatement sql = store.getConnection().prepareStatement(
          "SELECT transcript_type"
          + " FROM transcript_type"
          + " WHERE type_id IN ("
          +Arrays.stream(request.getParameterValues("transcript_type"))
          .collect(Collectors.joining(","))
          + ")");
        ResultSet rs = sql.executeQuery();
        StringBuilder transcriptQuery = new StringBuilder();
        while(rs.next()) {
          // something like ['wordlist','interview'].includes(first('transcript_type').label)
          if (transcriptQuery.length() == 0) {
            transcriptQuery.append("[");
          } else {
            transcriptQuery.append(",");
          }
          transcriptQuery.append("'").append(rs.getString(1).replace("'","\\'")).append("'");
        } // next transcript type
        if (transcriptQuery.length() > 0) {
          transcriptQuery.append("].includes(first('transcript_type').label)");
          task.setTranscriptQuery(transcriptQuery.toString());
        }
      } // transcript_type parameter
      if (request.getParameter("only_main_speaker") != null) task.setMainParticipantOnly(true);
      boolean redirect = request.getParameter("redirect") != null;
      
      task.setStoreCache(new StoreCache() {
          public SqlGraphStore get() {
            try {
              return store;
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
        cacheStore(store);
        writeResponse(response, failureResult(request, validationError));
      } else {
        task.start();

        if (redirect) {
          response.sendRedirect("../thread?threadId="+task.getId());
        } else {
          // return its ID
          JsonObjectBuilder jsonResult = Json.createObjectBuilder()
            .add("threadId", ""+task.getId());
          writeResponse(response, successResult(request, jsonResult.build(), null));
        }
      } // valid search
    } catch(Exception ex) {
      throw new ServletException(ex);
    }
  }
  
  private static final long serialVersionUID = -1;
} // end of class Search
