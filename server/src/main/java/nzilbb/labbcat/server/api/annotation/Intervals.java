//
// Copyright 2025 New Zealand Institute of Language, Brain and Behaviour, 
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

package nzilbb.labbcat.server.api.annotation;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.zip.*;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.xml.parsers.*;
import javax.xml.xpath.*;
import nzilbb.ag.Graph;
import nzilbb.ag.GraphStoreAdministration;
import nzilbb.ag.Schema;
import nzilbb.ag.serialize.GraphSerializer;
import nzilbb.ag.serialize.SerializationException;
import nzilbb.ag.serialize.util.ConfigurationHelper;
import nzilbb.ag.serialize.util.NamedStream;
import nzilbb.ag.serialize.util.Utility;
import nzilbb.configure.ParameterSet;
import nzilbb.labbcat.server.api.APIRequestHandler;
import nzilbb.labbcat.server.api.RequestParameters;
import nzilbb.labbcat.server.db.SqlGraphStore;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.labbcat.server.db.StoreCache;
import nzilbb.labbcat.server.task.ExtractIntervalLabels;
import nzilbb.util.IO;
import org.w3c.dom.*;
import org.xml.sax.*;

/**
 * <tt>/api/annotation/intervals</tt>
 * : Concatenates annotation labels for given labels contained in given time intervals.
 * <p> A given CSV file, with given column indices identifying the transcript,
 * participant, start, and end columns, is traversed, and for each row, annotations
 * contained by the intervals are identified, their labels are concatinated and
 * returned with the overall time boundaries added to a copy of the original CSV file.
 * <p> The request method must be <b> POST </b>
 * <p> The multipart-encoded parameters are:
 *  <dl>
 *   <dt> csv </dt>
 *       <dd> CSV results file containing tokens to measure. </dd>
 *   <dt> csvFieldDelimiter </dt>
 *       <dd> Optional character for delimiting the CSV field. If not specified, the
 *            delimiter will be inferred from the first line of the file. </dd> 
 *   <dt> transcriptColumn </dt>
 *       <dd> CSV column index of the transcript name.</dd>
 *   <dt> participantColumn </dt>
 *       <dd> CSV column index of the participant name.</dd>
 *   <dt> startTimeColumn </dt>
 *       <dd> CSV column index of the start time.</dd>
 *   <dt> endTimeColumn </dt>
 *       <dd> CSV column index of the end time name.</dd>
 *   <dt> containment </dt>
 *       <dd> "entire" if the annotations must be entirely between the start and end times,
 *            "partial" if they can extend before the start or after the end.</dd> 
 *   <dt> labelDelimiter </dt>
 *       <dd> Delimiter to use between labels. Defaults to a space " ".</dd> 
 *   <dt> passThroughData or copyColumns </dt>
 *       <dd> Whether to include all CSV columns from the input file in the output file -
 *            "false" if not.</dd> 
 *   <dt> layerId </dt>
 *       <dd> One or more layer IDs to extract.</dd>
 *  </dl>
 * <p><b>Output</b>: A JSON-encoded response containing the threadId of a task that is
 * processing the request. The task, when finished, will output a CSV files with one line
 * for each line of the input file, and fields containing the selected layers' labels and
 * boundaries. The input file data will be passed through into the output file, with 
 * new columns appended to each line. 
 * @author Robert Fromont
 */
public class Intervals extends APIRequestHandler {
  
  /**
   * Constructor
   */
  public Intervals() {
  } // end of constructor
  
  // Servlet methods
  
  /**
   * The POST method for the servlet.
   * @param parameters Request parameter map.
   * @param fileName Receives the filename for specification in the response headers.
   * @param httpStatus Receives the response status code, in case of error.
   * @return JSON-encoded object representing the response
   */
  public JsonObject post(RequestParameters parameters, Consumer<String> fileName, Consumer<Integer> httpStatus) {
    
    try {
      Vector<File> files =  parameters.getFiles("csv");
      if (files.size() == 0) {
        httpStatus.accept(APIRequestHandler.SC_BAD_REQUEST);
        return failureResult("No file received.");
      }
      // get the file
      File uploadedCsvFile = files.elementAt(0);
      
      ExtractIntervalLabels task = new ExtractIntervalLabels();
      task.setStoreCache(new StoreCache() {
          public SqlGraphStore get() {
            try {
              return getStore();
            } catch(Exception exception) {
              context.servletLog("Praat.StoreCache: " + exception);
              return null;
            }
          }
          public void accept(SqlGraphStore store) {
            cacheStore((SqlGraphStoreAdministration)store);
          }
        });
      // we will definitely need a store so get it now, before this servlet is receycled
      task.getStore();
      task.setDataFile(uploadedCsvFile);
      task.setFileName(uploadedCsvFile.getName());
      // determine field delimiter
      String csvFieldDelimiter = parameters.getString("csvFieldDelimiter");
      if (csvFieldDelimiter == null || csvFieldDelimiter.length() == 0) {
        // figure out the field delimiter          
        try (BufferedReader r =  new BufferedReader(new FileReader(uploadedCsvFile))) {
          String firstLine = r.readLine();
          while (firstLine != null && firstLine.trim().length() == 0) {
            firstLine = r.readLine();
          } // next blank line
          if (firstLine != null) {
            if (firstLine.contains("\t")) csvFieldDelimiter = "\t";
            else if (firstLine.contains(";")) csvFieldDelimiter = ";";
            else if (firstLine.contains(",")) csvFieldDelimiter = ",";
          }
        } // reader
      }
      if (csvFieldDelimiter != null && csvFieldDelimiter.length() > 0) {
        task.setFieldDelimiter(csvFieldDelimiter.charAt(0));
      }
      
      if (parameters.getString("transcriptColumn") != null) {
        try {
          task.setTranscriptIdColumn(
            Integer.parseInt(parameters.getString("transcriptColumn")));
        } catch(NumberFormatException exception) {
          httpStatus.accept(APIRequestHandler.SC_BAD_REQUEST);
          return failureResult(
            "Transcript column \"{0}\" is not an integer.",
            parameters.getString("transcriptColumn"));
        }
      } else {
        httpStatus.accept(APIRequestHandler.SC_BAD_REQUEST);
        return failureResult("Transcript column not supplied.");
      }
          
      if (parameters.getString("participantColumn") != null
          && parameters.getString("participantColumn").length() > 0) {
        try {
          task.setParticipantNameColumn(
            Integer.parseInt(parameters.getString("participantColumn")));
        } catch(NumberFormatException exception) {
          httpStatus.accept(APIRequestHandler.SC_BAD_REQUEST);
          return failureResult(
              "Participant column \"{0}\" is not an integer.",
              parameters.getString("participantColumn"));
        }
      }
          
      if (parameters.getString("startTimeColumn") != null) {
        try {
          task.setStartTimeColumn(Integer.parseInt(parameters.getString("startTimeColumn")));
        } catch(NumberFormatException exception) {
          httpStatus.accept(APIRequestHandler.SC_BAD_REQUEST);
          return failureResult(
            "Start time column \"{0}\" is not an integer.",
            parameters.getString("startTimeColumn"));
        }
      } else {
        httpStatus.accept(APIRequestHandler.SC_BAD_REQUEST);
        return failureResult("Start time column not supplied.");
      }
      
      if (parameters.getString("endTimeColumn") != null) {
        try {
          task.setEndTimeColumn(Integer.parseInt(parameters.getString("endTimeColumn")));
        } catch(NumberFormatException exception) {
          httpStatus.accept(APIRequestHandler.SC_BAD_REQUEST);
          return failureResult(
            "End time column \"{0}\" is not an integer.",
            parameters.getString("endTimeColumn"));
        }
      } else {
        httpStatus.accept(APIRequestHandler.SC_BAD_REQUEST);
        return failureResult("End time column not supplied.");
      }

      task.setLabelDelimiter(parameters.getString("labelDelimiter"));
      task.setPartialContainmentAllowed(
        "partial".equalsIgnoreCase(parameters.getString("containment")));

      task.setCopyColumns(
        !"false".equals(parameters.getString("copyColumns"))
        && !"false".equals(parameters.getString("passThroughData")));

      String[] layerIds = parameters.getStrings("layerId");
      if (layerIds != null && layerIds.length > 0) {
        task.setLayerIds(Arrays.asList(layerIds));
      } else {
        httpStatus.accept(APIRequestHandler.SC_BAD_REQUEST);
        return failureResult("No layers specified.");
      }
      
      // start the task
      // (parent dir is a unique version of the name)
      task.setName(uploadedCsvFile.getParentFile().getName()); 
      if (context.getUser() != null) {	
        task.setWho(context.getUser());
      } else {
        task.setWho(context.getUserHost());
      }
      task.start();
      
      // return its ID
      JsonObjectBuilder jsonResult = Json.createObjectBuilder()
        .add("threadId", task.getId());
      return successResult(jsonResult.build(), null);
      
    } catch(Exception ex) {
      try {
        httpStatus.accept(APIRequestHandler.SC_INTERNAL_SERVER_ERROR);
      } catch(Exception exception) {}
      context.servletLog("Praat.post: unhandled exception: " + ex);
      ex.printStackTrace(System.err);
      return failureResult(ex);
    }
  }
  
} // end of class Praat
