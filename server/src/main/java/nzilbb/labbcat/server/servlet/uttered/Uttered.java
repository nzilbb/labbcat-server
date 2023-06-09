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
package nzilbb.labbcat.server.servlet.uttered;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonArrayBuilder;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import nzilbb.ag.Annotation;
import nzilbb.ag.Graph;
import nzilbb.ag.PermissionException;
import nzilbb.ag.GraphNotFoundException;
import nzilbb.ag.Schema;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.labbcat.server.servlet.*;
import nzilbb.util.IO;

/**
 * <tt>/api/uttered[/transcriptId[/utteranceId]]</tt>
 * : List transcripts, utterances, and receive utterance corrections.
 * <p> This servlet performs three functions:
 * <ol>
 *  <li> Provide a list of transcripts that require correction. </li>
 *  <li> Provide media and a list of correctable utterances for a given transcript. </li>
 *  <li> Receive utterance transcript updates. </li>
 * </ol>
 * <h3> 1. List transcripts </h3>
 * <p> A <tt>GET</tt> request to <tt>/api/uttered</tt> returns a JSON-encoded array of
 * transcript IDs. 
 * <h3> 2. List utterances </h3>
 * <p> A <tt>GET</tt> request to <tt>/api/uttered/<var>transcriptId</var></tt> returns a
 * list of utterances for correction. The structure of the JSON-encoded response object is:
 * <dl>
 *  <dt>media</dt><dd>A URL to the media for the transcription.</dd>
 *  <dt>utterances</dt>
 *  <dd>An array of utterance objects, each structured:
 *    <dl>
 *     <dt>utteranceId</dt><dd> A unique identifier for the utterance. </dd>
 *     <dt>speakerId</dt><dd> A unique identifier for the speaker. </dd>
 *     <dt>start</dt><dd> The start time of the utterance in seconds. </dd>
 *     <dt>end</dt><dd> The end time of the utterance in seconds. </dd>
 *     <dt>confidence</dt><dd> Transcript confidence rating from 0 (low) to 100 (high). </dd>
 *     <dt>text</dt><dd> The current text of the utterance's transcript for correction. </dd>
 *    </dl>
 *  </dd>
 * </dl>
 * <h3> 3. Update/confirm utterance transcription </h3>
 * <p> A <tt>PUT</tt> request to
 * <tt>/api/uttered/<var>transcriptId</var>/<var>utteraceId</var></tt>
 * updates/confirms the transcript of the given utterance .
 * <p> The body of the request is the confirmed utterance transcript.
 * <p> If successful, the response status is <tt>200</tt>
 * <p> If an error occurs, the response status will be a value other than <tt>200</tt>
 * and the plain text body of the response contains the error message.
 * @author Robert Fromont robert@fromont.net.nz
 */
@WebServlet("/api/uttered/*")
@RequiredRole("edit")
public class Uttered extends LabbcatServlet {
  
  /**
   * GET handler.
   */
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    log(request.getPathInfo());
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    if (request.getPathInfo() == null || request.getPathInfo().equals("/")) {
      // TODO implement this
      writeResponse(response, Json.createArrayBuilder().add("AP511_MikeThorpe.eaf").build());
    } else { // pathInfo identifies the transcript
      String id = request.getPathInfo().split("/")[1];
      // TODO check its in the list of editable transcripts
      try {
        SqlGraphStoreAdministration store = getStore(request);
        Schema schema = store.getSchema();
        String[] layers = {
          schema.getTurnLayerId(), schema.getUtteranceLayerId(), schema.getWordLayerId() };
        Graph transcript = store.getTranscript(id, layers);
        JsonObjectBuilder json = Json.createObjectBuilder();
        json.add("media", store.getMedia(id, "", "audio/wav"));
        JsonArrayBuilder utterances = Json.createArrayBuilder();
        for (Annotation utterance : transcript.all(schema.getUtteranceLayerId())) {
          utterances.add(
            Json.createObjectBuilder()
            .add("utteranceId", utterance.getId())
            .add("speakerId", utterance.getLabel())
            .add("start", utterance.getStart().getOffset())
            .add("end", utterance.getEnd().getOffset())
            .add("confidence", utterance.getConfidence())
            .add("text",
                 Arrays.stream(utterance.all(schema.getWordLayerId()))
                 .map(annotation -> annotation.getLabel())
                 .collect(Collectors.joining(" "))));
        } // next utterance
        json.add("utterances", utterances);
        writeResponse(response, json.build());
      } catch(GraphNotFoundException notFound) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        writeResponse(response, Json.createObjectBuilder()
                      .add("error", "Invalid transcriptId: " + id).build());
      } catch(Exception x) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        writeResponse(response, Json.createObjectBuilder().add("error", x.getMessage()).build());
      }
    }
  }

  /**
   * PUT handler - update an existing row.
   */
  @Override
  protected void doPut(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    response.setContentType("text/plain");
    response.setCharacterEncoding("UTF-8");
    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "TODO");
  }
  private static final long serialVersionUID = 1;
} // end of class Uttered
