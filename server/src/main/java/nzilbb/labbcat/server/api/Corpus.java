//
// Copyright 2017-2025 New Zealand Institute of Language, Brain and Behaviour, 
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

import java.sql.*;
import java.text.DecimalFormat;
import java.util.function.Consumer;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

/**
 * <tt>/api/corpus/{ID}</tt> : statistics about a given corpus.
 *  <p> Allows access to information about a given corpus, including participant count,
 *  count of distinc word types, durations, etc. 
 *   <p> Only the GET HTTP method is supported:
 *   <dl>
 *    <dt> GET </dt><dd>
 *     <ul>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object where each key is the name of a statistic and the value is the statistic's
 *       value.  </li>
 *      <li><em> Response Status </em> <em> 200 </em> on success, or 404 if the
 *       corpus ID is invalid. </li>
 *     </ul></dd> 
 *   </dl>
 *  </p>
 * @author Robert Fromont
 */
public class Corpus extends APIRequestHandler {
  
  DecimalFormat num = new DecimalFormat();
  String hoursMinutesSeconds(double dSeconds) {
    DecimalFormat num = new DecimalFormat();
    DecimalFormat num2 = new DecimalFormat("00");
    DecimalFormat num22 = new DecimalFormat("00.00");
    int iHours = (int)(dSeconds / 3600);
    dSeconds -= (iHours * 3600);
    int iMinutes = (int)(dSeconds / 60);
    dSeconds -= (iMinutes * 60);
    return num.format(iHours) + ":" + num2.format(iMinutes) + ":" + num22.format(dSeconds);
  }

  /**
   * Constructor
   */
  public Corpus() {
  } // end of constructor
  
  /**
   * Generate the response to a request.
   * <p> This returns statistical information about the given corpus.
   * @param pathInfo The URL path from which the upload ID can be inferred.
   * @param httpStatus Receives the response status code, in case of error.
   * @return JSON-encoded object representing the response
   */
  public JsonObject get(String pathInfo, Consumer<Integer> httpStatus) {
    // get ID
    if (pathInfo == null || pathInfo.equals("/") || pathInfo.indexOf('/') < 0) {
      // no path component
      httpStatus.accept(SC_BAD_REQUEST);
      return failureResult("No ID specified.");
    }
    String corpusName = pathInfo.substring(pathInfo.lastIndexOf('/') + 1);
    if (corpusName.length() == 0) {
      httpStatus.accept(SC_BAD_REQUEST);
      return failureResult("No ID specified.");
    }        
    try (Connection db = newConnection()) {     
      PreparedStatement sql = db.prepareStatement(
        "SELECT corpus_id FROM corpus WHERE corpus_name = ?");
      sql.setString(1, corpusName);
      ResultSet rs = sql.executeQuery();
      try {
        if (!rs.next()) {
          httpStatus.accept(SC_NOT_FOUND);
          return failureResult("Invalid ID: {0}", corpusName);
        }
        int corpus_id = rs.getInt(1);
        JsonObjectBuilder model = Json.createObjectBuilder();

        rs.close();
        sql.close();
        sql = db.prepareStatement(
          "SELECT COUNT(*) AS theCount FROM transcript WHERE corpus_name = ?");
        sql.setString(1, corpusName);
        rs = sql.executeQuery();
        rs.next();
        model.add("Transcripts", num.format(rs.getInt("theCount")));
        
        rs.close();
        sql.close();
        sql = db.prepareStatement(
          "SELECT COUNT(*) AS theCount FROM speaker"
          +" INNER JOIN speaker_corpus ON speaker.speaker_number = speaker_corpus.speaker_number"
          +" WHERE speaker_corpus.corpus_id = ?");
        sql.setInt(1, corpus_id);
        rs = sql.executeQuery();
        rs.next();
        model.add("Participants", num.format(rs.getInt("theCount")));
        
        rs.close();
        sql.close();
        sql = db.prepareStatement(
          "SELECT COUNT(*) AS theCount FROM annotation_layer_0 o"
          +" INNER JOIN transcript ON o.ag_id = transcript.ag_id WHERE corpus_name = ?");
        sql.setString(1, corpusName);
        rs = sql.executeQuery();
        rs.next();
        model.add("Word tokens", num.format(rs.getInt("theCount")));
        rs.close();
        sql.close();
        
        sql = db.prepareStatement(
          "SELECT COUNT(DISTINCT label) AS theCount FROM annotation_layer_2 o"
          +" INNER JOIN transcript ON o.ag_id = transcript.ag_id WHERE corpus_name = ?");
        sql.setString(1, corpusName);
        rs = sql.executeQuery();
        rs.next();
        model.add("Distinct word types", num.format(rs.getInt("theCount")));
        
        rs.close();
        sql.close();
        sql = db.prepareStatement(
          "SELECT SUM(duration) AS duration FROM"
          +" (SELECT MAX(`offset`) - MIN(`offset`) AS duration"
          +" FROM anchor INNER JOIN transcript on anchor.ag_id = transcript.ag_id"
          +" WHERE transcript.corpus_name = ?"
          +" GROUP BY transcript.ag_id) durations");
        sql.setString(1, corpusName);
        rs = sql.executeQuery();
        rs.next();
        model.add(
          "Total transcript duration", hoursMinutesSeconds(rs.getDouble("duration")));
        
        rs.close();
        sql.close();
        sql = db.prepareStatement(
          "SELECT COUNT(*) AS theCount FROM annotation_layer_0 word"
          +" INNER JOIN transcript on word.ag_id = transcript.ag_id "
          +" INNER JOIN annotation_layer_11 turn ON word.turn_annotation_id = turn.annotation_id"
          +" INNER JOIN transcript_speaker ON turn.ag_id = transcript_speaker.ag_id"
          +" AND transcript_speaker.speaker_number = turn.label"
          +" AND transcript_speaker.main_speaker = 1"
          +" WHERE transcript.corpus_name = ?");
        sql.setString(1, corpusName);
        rs = sql.executeQuery();
        rs.next();
        model.add("Main-participant word tokens", num.format(rs.getInt("theCount")));

        rs.close();
        sql.close();        
        sql = db.prepareStatement(
          "SELECT COUNT(DISTINCT word.label) AS theCount FROM annotation_layer_2 word"
          +" INNER JOIN transcript on word.ag_id = transcript.ag_id "
          +" INNER JOIN annotation_layer_11 turn ON word.turn_annotation_id = turn.annotation_id"
          +" INNER JOIN transcript_speaker ON turn.ag_id = transcript_speaker.ag_id"
          +" AND transcript_speaker.speaker_number = turn.label"
          +" AND transcript_speaker.main_speaker = 1"
          +" WHERE transcript.corpus_name = ?");
        sql.setString(1, corpusName);
        rs = sql.executeQuery();
        rs.next();
        model.add("Main-participant word types", num.format(rs.getInt("theCount")));
        
        return successResult(model.build(), null);
      } finally {
        rs.close();
        sql.close();
      }
    } catch(Exception exception) {
      context.servletLog("Corpus Exception " + exception);
      context.servletLog(exception);
      try {
        httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      } catch(Exception x) {}
      return failureResult("Unexpected error.");
    }
  }
} // end of class Corpus
