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

package nzilbb.labbcat.server.api.edit.participants;

import java.util.Vector;
import java.util.function.Consumer;
import javax.json.JsonObject;
import nzilbb.ag.Annotation;
import nzilbb.ag.GraphNotFoundException;
import nzilbb.labbcat.server.api.APIRequestHandler;
import nzilbb.labbcat.server.api.RequestParameters;
import nzilbb.labbcat.server.api.RequiredRole;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;

/**
 * <tt>/api/edit/participants/merge</tt>
 * : Merging a list of participants into one participant.
 * <h3 id="POST"> <tt>/api/edit/participants/merge</tt> </h3>
 * <p> <b> POST </b> Specifies a list of existing participant IDs, which will be merged
 * into an existing target participant, re-assigning all turns/utterances to the
 * new participant record. Once this is complete, the source participants are deleted.
 * <p> The URL-encoded parameters are:
 *  <dl>
 *   <dt> source </dt>
 *       <dd> The IDs of the existing participants to merge and then delete.
 *            Multiple values of this parameter can be specified,
 *            e.g. <tt>source=Interviewer&amp;source=interviewer</tt>. </dd>
 *   <dt> destination </dt>
 *       <dd> The ID of the participant that all turns/utterances will be assigned to. </dd>
 *  </dl>
 * <p><em>Response Body</em>: - the standard JSON envelope, returning as the <q>model</q> 
 *    the ID of the new participant record.
 * <p><em>Response Status</em>:
 *  <ul>
 *    <li><em>201</em>: The participants were successfully merged into one. </li>
 *    <li><em>400</em>: The request was malformed or invalid, e.g.
 *     <ul>
 *      <li>The "source" parameter was missing.</li>
 *      <li>The "destination" parameter was missing.</li>
 *      <li>Any of the "source" parameter values includes tha value of "destination".</li>
 *     </ul>
 *    </li>
 *    <li><em>404</em>: One or more of the participants identified by "source"
 *        or "destination" was not found. </li>
 *  </ul>
 * @author Robert Fromont robert@fromont.net.nz
 */
@RequiredRole("edit")
public class Merge extends APIRequestHandler {
  
  /**
   * Default constructor.
   */
  public Merge() {
  } // end of constructor

  /**
   * The POST method for the servlet.
   * @param requestParameters Request parameter map.
   * @param httpStatus Receives the response status code, in case of error.
   * @param layerGenerator A function that will start a layer generation thread
   * for the given list of participant annotations and the given layer,
   * and return the thread ID.
   * @return JSON-encoded object representing the response
   */
  public JsonObject post(
    RequestParameters requestParameters, Consumer<Integer> httpStatus) {
    // context.servletLog("POST post " + requestParameters);
    try {
      Vector<String> messages = new Vector<String>();
      SqlGraphStoreAdministration store = getStore();
      try {
        if (!hasAccess(store.getConnection())) {
          httpStatus.accept(SC_FORBIDDEN);
          return null;
        }
        String[] sources = requestParameters.getStrings("source");
        if (sources == null || sources.length == 0) {
          messages.add("No source specified."); // TODO i18n
        }
        String destination = requestParameters.getString("destination");
        if (destination == null || destination.length() == 0) {
          messages.add("No destination specified."); // TODO i18n
        }
        for (String source : sources) {
          if (source.equals(destination)) {
            messages.add("Sources include destination."); // TODO i18n
            break;
          }
        } // next source ID

        if (messages.size() > 0) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult(messages);
        }

        try {
          String[] successMessages = store.mergeParticipants(sources, destination);
          for (String m : successMessages) messages.add(m);
        } catch (GraphNotFoundException x) {
          httpStatus.accept(SC_NOT_FOUND);
          return failureResult("Invalid ID: {0}", x.getId());
        }
        
        Annotation dest = store.getParticipant(destination, null);
        return successResult(dest.getId(), messages);
      } finally {
        cacheStore(store);
      }
    } catch(Exception ex) {
      try {
        httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      } catch(Exception exception) {}
      context.servletLog("POST: unhandled exception: " + ex);
      ex.printStackTrace(System.err);
      return failureResult(ex);
    }
  }
}
