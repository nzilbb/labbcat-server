//
// Copyright 2026 New Zealand Institute of Language, Brain and Behaviour, 
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

package nzilbb.labbcat.server.api.elicit;

import java.sql.*;
import java.util.function.Consumer;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.labbcat.server.api.APIRequestHandler;
import nzilbb.labbcat.server.api.RequestParameters;
import nzilbb.ag.Graph;
import nzilbb.ag.GraphNotFoundException;

/**
 * <tt>/api/elicit/verify</tt> : Verify an elicited transcript upload.
 *  <p> Verifies the given transcript was successfull uploaded by a previous call to
 *  <tt>/api/elicit/upload</tt>
 *   <p> Only the GET HTTP method is supported:
 *   <dl>
 *    <dt> GET </dt><dd>
 *     <ul>
 *      <li><em> URL parameters </em>:
 *       <dl>
 *        <dt> transcript_id </dt> <dd> The name of the uploaded transcript. </dd>
 *       </dl></li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object the following attributes:
 *  <dl>
 *   <dt> transcript_id </dt>
 *       <dd> This given transcript ID. </dd> 
 *   <dt> ag_id </dt>
 *       <dd> The database ID of the transcript. </dd> 
 *  </dl>
 *       If the model is an empty object, the upload did not succeed.
 *      </li>
 *      <li><em> Response Status </em> <ul>
 *         <li><em> 200 </em> : Success.</li>
 *         <li><em> 400 </em> : Transcript not specified.</li>
 *       </ul></li>
 *     </ul></dd> 
 *   </dl>
 *  </p>
 * @author Robert Fromont
 */
public class Verify extends APIRequestHandler { // TODO automated tests
  
  /**
   * Constructor
   */
  public Verify() {
  } // end of constructor
  
  /**
   * The GET method for the servlet.
   * @param requestParameters Request parameter map.
   * @param httpStatus Receives the response status code, in case of error.
   * @return JSON-encoded object representing the response
   */
  public JsonObject get(RequestParameters requestParameters, Consumer<Integer> httpStatus) {
    String transcript_id = requestParameters.getString("transcript_id");
    if (transcript_id == null || transcript_id.length() == 0) {
      httpStatus.accept(SC_BAD_REQUEST);
      return failureResult("No ID specified.");
    }
    try {
      final SqlGraphStoreAdministration store = getStore();
      try {
        Graph transcript = store.getTranscript(transcript_id, null);
        JsonObjectBuilder jsonResult = Json.createObjectBuilder()
          .add("transcript_id", transcript_id)
          .add("ag_id", (Integer)transcript.get("@ag_id"));
        return successResult(jsonResult.build(), null);
      } catch(GraphNotFoundException ex) {
        return failureResult("Transcript not found: {0}", transcript_id);
      } catch(Exception ex) {
        cacheStore(store);
        return failureResult(ex);
      }
    } catch(Exception ex) {
      try {
        httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      } catch(Exception exception) {}
      context.servletLog("GET elicit.Verify: unhandled exception: " + ex);
      ex.printStackTrace(System.err);
      return failureResult(ex);
    }
  }
  
} // end of class Verify
