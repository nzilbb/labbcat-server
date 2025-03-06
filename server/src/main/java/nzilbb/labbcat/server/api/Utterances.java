//
// Copyright 2023-2025 New Zealand Institute of Language, Brain and Behaviour, 
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

package nzilbb.labbcat.server.api;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
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
public class Utterances extends APIRequestHandler { // TODO unit test
  
  /**
   * Constructor
   */
  public Utterances() {
  } // end of constructor
  
  /**
   * The POST method for the servlet.
   * @param parameters Request parameter map.
   * @param out Response body output stream.
   * @param contentType Receives the content type for specification in the response headers.
   * @param fileName Receives the filename for specification in the response headers.
   * @param httpStatus Receives the response status code, in case or error.
   * @param redirectUrl Receives a URL for the request to be redirected to.
   * @return JSON-encoded object representing the response
   */
  public JsonObject post(RequestParameters parameters, Consumer<String> fileName, Consumer<Integer> httpStatus, Consumer<String> redirectUrl) {
    
    // parameters
    AllUtterancesTask task = new AllUtterancesTask();
    task.setResources(context.getResourceBundle());
    task.setParticipantQuery(parameters.getString("participant_expression"));
    if (task.getParticipantQuery() == null) {
      if (parameters.getString("id") != null) {
        task.setParticipantQuery(
          "id IN ("
          +Arrays.stream(parameters.getStrings("id"))
          .map(id->"'"+esc(id)+"'")
          .collect(Collectors.joining(","))
          +")");
      } else if (parameters.getString("speaker_number") != null) {
        task.setParticipantQuery(
          "speaker_number IN ("
          +Arrays.stream(parameters.getStrings("speaker_number"))
          .collect(Collectors.joining(","))
          +")");
      } else {
        httpStatus.accept(SC_BAD_REQUEST);
        return failureResult("No participants specified."); // TODO i18n
      }
    }
    task.setTranscriptQuery(parameters.getString("transcript_expression"));
    try {
      final SqlGraphStoreAdministration store = getStore();
      if (task.getTranscriptQuery() == null && parameters.getString("transcript_type") != null) {
        // parameter values are numeric transcript_type.type_id values, but we need the text labels
        PreparedStatement sql = store.getConnection().prepareStatement(
          "SELECT transcript_type"
          + " FROM transcript_type"
          + " WHERE type_id IN ("
          +Arrays.stream(parameters.getStrings("transcript_type"))
          .collect(Collectors.joining(","))
          + ")");
        StringBuilder transcriptQuery = new StringBuilder();
        // something like ['wordlist','interview'].includes(first('transcript_type').label)
        try {
          ResultSet rs = sql.executeQuery();
          while(rs.next()) {
            if (transcriptQuery.length() == 0) {
              transcriptQuery.append("[");
            } else {
              transcriptQuery.append(",");
            }
            transcriptQuery.append("'").append(esc(rs.getString(1))).append("'");
            rs.close();
          } // next transcript type
        } catch (SQLException x) { // probably transcript type labels not numeric type_id
          transcriptQuery.append("[");
          transcriptQuery.append(Arrays.stream(parameters.getStrings("transcript_type"))
                                 .map(type -> "'"+esc(type)+"'")
                                 .collect(Collectors.joining(",")));
        }
        sql.close();
        if (transcriptQuery.length() > 0) {
          transcriptQuery.append("].includes(first('transcript_type').label)");
          task.setTranscriptQuery(transcriptQuery.toString());
        }
      } // transcript_type parameter
      if (parameters.getString("only_main_speaker") != null) task.setMainParticipantOnly(true);
      boolean redirect = parameters.getString("redirect") != null;
      
      task.setStoreCache(new StoreCache() {
          public SqlGraphStore get() {
            try {
              return store;
            } catch(Exception exception) {
              System.err.println("Utterances.StoreCache: " + exception);
              return null;
            }
          }
          public void accept(SqlGraphStore store) {
            cacheStore((SqlGraphStoreAdministration)store);
          }
        });
      if (context.getUser() != null) {	
        task.setWho(context.getUser());
        // admin users have access to everything
        if (!context.isUserInRole("admin")
            // if they're using using access permissions in general
            && task.getStore().getPermissionsSpecified()) {
          // other users may have restricted access to some things
          task.setRestrictByUser(context.getUser());
        } // not an admin user
      } else {
        task.setWho(context.getUserHost());
      }
      
      String validationError = task.validate();
      if (validationError != null) {
        cacheStore(store);
        return failureResult(validationError);
      } else {
        task.start();
        
        if (redirect) {
          redirectUrl.accept("../thread?threadId="+task.getId());
          return null;
        } else {
          // return its ID
          JsonObjectBuilder jsonResult = Json.createObjectBuilder()
            .add("threadId", ""+task.getId());
          return successResult(jsonResult.build(), null);
        }
      } // valid search
    } catch(Exception ex) {
      try {
        httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      } catch(Exception exception) {}
      System.err.println("Praat.post: unhandled exception: " + ex);
      ex.printStackTrace(System.err);
      return failureResult(ex);
    }
  }
} // end of class Search
