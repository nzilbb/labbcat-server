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

package nzilbb.labbcat.server.api.edit.transcripts.layers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.function.BiFunction;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import nzilbb.ag.*;
import nzilbb.ag.ql.QL;
import nzilbb.labbcat.server.api.APIRequestHandler;
import nzilbb.labbcat.server.api.RequestParameters;
import nzilbb.labbcat.server.api.RequiredRole;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.util.IO;

/**
 * <tt>/api/edit/transcripts/layers/regenerate</tt>
 * : Handler for regenerating selected layers for selected transcripts.
 * <h3 id="POST"> <tt>/api/edit/transcripts/layers/regenerate</tt> </h3>
 * <p> <b> POST </b> starts layer generation in selected transcripts for
 * a given layer (or all layers). 
 * <p> Can be a multipart-encoded request, or have a plain URL-encoded body.
 * Parameters are:
 *  <dl>
 *   <dt> layerId </dt>
 *       <dd> The ID of the layer to regenerate, or an empty string for all layers.</dd>
 *   <dt> ids </dt>
 *       <dd> If a multipart-encoded request, this is a text file containing transcript IDs,
 *            one per line, identifying the transcripts to regenerate. </dd>
 *   <dt> id </dt>
 *       <dd> If a url-encoded request, this multiple value parameter, specifies
 *            the IDs of transcripts to regenerate.</dd>
 *  </dl>
 * <p>One of <em>ids</em> or <em>id</em> must be specified.
 * <p><b>Output</b>: A JSON-encoded response containing the threadId of a task that is
 * processing the request.
 * @author Robert Fromont robert@fromont.net.nz
 */
@RequiredRole("edit")
public class Regenerate extends APIRequestHandler {

  /**
   * Default constructor.
   */
  public Regenerate() {
  } // end of constructor

  /**
   * The POST method for the servlet.
   * @param requestParameters Request parameter map.
   * @param httpStatus Receives the response status code, in case of error.
   * @param layerGenerator A function that will start a layer generation thread for the
   * given list of ag_ids and the given layer, and return the thread ID.
   * @return JSON-encoded object representing the response
   */
  public JsonObject post(
    RequestParameters requestParameters, Consumer<Integer> httpStatus,
    BiFunction<Collection<Integer>,Integer,String> layerGenerator) {
    // context.servletLog(
    //   "POST post " + requestParameters
    //   + (requestParameters.getFile("ids") != null?
    //      requestParameters.getFile("ids").getPath():"(no data file)"));
    File idsFile = null;
    try {
      Vector<String> messages = new Vector<String>();
      SqlGraphStoreAdministration store = getStore();
      try {
        if (!hasAccess(store.getConnection())) {
          httpStatus.accept(SC_FORBIDDEN);
          return null;
        }
        String layerId = requestParameters.getString("layerId");
        if (layerId == null) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No layer ID specified.");
        }
        Layer layer = null;
        if (layerId != null && layerId.length() > 0) {
          layer = store.getLayer(layerId);
          if (layer == null
              || layer.get("layer_manager_id") == null
              || !layer.containsKey("enabled")
              || !layer.get("enabled").toString().matches(".*T.*") // T: transcript upload
              || layer.get("layer_id") == null) {
            httpStatus.accept(SC_BAD_REQUEST);
            return failureResult("Invalid layer ID: {0}", layerId);
          }
        }
        idsFile = requestParameters.getFile("ids");
        String[] id = requestParameters.getStrings("id");
        if (idsFile == null && (id == null || id.length == 0)) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No ID specified.");
        }

        LinkedHashSet<Integer> ag_ids = new LinkedHashSet<Integer>();
        if (idsFile != null) { // read IDs from file
          try (BufferedReader idReader = new BufferedReader(new FileReader(idsFile))) {
            String line = idReader.readLine();
            while (line != null) {
              // strip quotes if any
              line = line.replaceAll("^\"","").replaceAll("\"$","");
              // ignore blank lines
              if (line.trim().length() > 0) {
                try {
                  Graph transcript = store.getTranscript(line, null);
                  if (transcript.containsKey("@ag_id")) {
                    ag_ids.add((Integer)transcript.get("@ag_id"));
                  } else { // should be impossible, give it a differentiable error message:
                    messages.add(localize("Record not found: {0}", line));
                  }
                } catch (GraphNotFoundException notFound) {
                  if (!"transcript".equalsIgnoreCase(line)) { // might be header of CSV
                    messages.add(localize("Transcript not found: {0}", line));
                  }
                }                
              } // not a blank line
              line = idReader.readLine();
            } // next line
          }
        } else { // idsFile not specified
          for (String transcriptId : id) {
            try {
              Graph transcript = store.getTranscript(transcriptId, null);
              if (transcript.containsKey("@ag_id")) {
                ag_ids.add((Integer)transcript.get("@ag_id"));
              } else { // should be impossible, give it a differentiable error message:
                messages.add(localize("Record not found: {0}", transcriptId));
              }
            } catch (GraphNotFoundException notFound) {
              messages.add(localize("Transcript not found: {0}", transcriptId));
            }
          }
        }

        if (ag_ids.size() == 0) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No transcripts matched.");
        }

        Integer layer_id = layer != null?(Integer)layer.get("layer_id"):null;
        String threadId = layerGenerator.apply(ag_ids, layer_id);
        JsonObjectBuilder jsonResult = Json.createObjectBuilder()
          .add("threadId", ""+threadId);
        return successResult(jsonResult.build(), messages);
      } finally {
        if (idsFile != null) idsFile.delete();
        cacheStore(store);
      }
    } catch(Exception ex) {
      try {
        httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      } catch(Exception exception) {}
      context.servletLog("POST Regenerate.post: unhandled exception: " + ex);
      ex.printStackTrace(System.err);
      return failureResult(ex);
    }
  }
  
} // end of class Regenerate
