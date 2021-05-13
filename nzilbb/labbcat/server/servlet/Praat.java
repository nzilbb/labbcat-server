//
// Copyright 2021 New Zealand Institute of Language, Brain and Behaviour, 
// University of Canterbury
// Written by Robert Fromont - robert.fromont@canterbury.ac.nz
//
//    This file is part of LaBB-CAT.
//
//    LaBB-CAT is free software; you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation; either version 2 of the License, or
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

import java.io.*;
import java.net.*;
import javax.servlet.*; // d:/jakarta-tomcat-5.0.28/common/lib/servlet-api.jar
import javax.servlet.http.*;
import javax.servlet.annotation.WebServlet;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;
import javax.xml.parsers.*;
import javax.xml.xpath.*;
import org.w3c.dom.*;
import org.xml.sax.*;
import nzilbb.configure.ParameterSet;
import nzilbb.ag.GraphStoreAdministration;
import nzilbb.ag.Schema;
import nzilbb.ag.Graph;
import nzilbb.ag.serialize.GraphSerializer;
import nzilbb.ag.serialize.SerializationException;
import nzilbb.ag.serialize.util.ConfigurationHelper;
import nzilbb.ag.serialize.util.NamedStream;
import nzilbb.ag.serialize.util.Utility;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.labbcat.server.task.ProcessWithPraat;
import nzilbb.util.IO;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import org.apache.commons.fileupload.FileItem;

/**
 * <tt>/api/praat</tt>
 * : Processes a given set of audio intervals with Praat.
 * <p> A given CSV file, with given column indices identifying the transcript,
 * participant, start, and end columns, is traversed, and each row is processed with Praat
 * to extract given acoustic measurements.
 * <p> The request method must be <b> POST </b>
 * <p> The multipart-encoded parameters are:
 *   <dl>
 *     <dd><code>csv</code> - CSV results file containing tokens to measure.</dd>
 *     <dd><code>transcript</code> - CSV column index of the transcript name.</dd>
 *     <dd><code>participant</code> - CSV column index of the participant name.</dd>
 *     <dd><code>startTime</code> - CSV column index of the start time.</dd>
 *     <dd><code>endTime</code> - CSV column index of the end time name.</dd>
 *     <dd><code>windowOffset</code> - How much surrounsing context to include, in seconds.</dd>
 *     <dd><code>samplePoints</code> - Space-delimited series of real numbers between 0
 *                                     and 1, specifying the proportional time points to
 *                                     measure. e.g. "0.5" will measure only the
 *                                     mid-point, "0 0.2 0.4 0.6 0.8 1" will measure six
 *                                     points evenly spread across the duration of the
 *                                     segment, etc.</dd> 
 *     <dd><code>gender_attribute</code> - Gender participant attribute layer ID.</dd>
 *     <dd><code>attribute</code> - Participant attribute layer IDs to include in for the
 *                                  custom script.</dd>
 *     <dd><code>pass_through_data</code> - Whether to include all CSV columns from the
 *                                          input file in the output file - "false" if not.</dd>
 *     <dd><code>extractF1</code> - Extract F1.</dd>
 *     <dd><code>extractF2</code> - Extract F2.</dd>
 *     <dd><code>extractF3</code> - Extract F3.</dd>
 *     <dd><code>extractMinimumPitch</code> - Extract minimum pitch.</dd>
 *     <dd><code>extractMeanPitch</code> - Extract mean pitch.</dd>
 *     <dd><code>extractMaximumPitch</code> - Extract maximum pitch.</dd>
 *     <dd><code>extractMaximumIntensity</code> - Extract maximum intensity.</dd>
 *     <dd><code>extractCOG1</code> - Extract COG 1.</dd>
 *     <dd><code>extractCOG2</code> - Extract COG 2.</dd>
 *     <dd><code>extractCOG23</code> - Extract COG 2/3.</dd>
 *     <dd><code>maximumFormantFemale</code> - Maximum Formant for Females.</dd>
 *     <dd><code>maximumFormantMale</code> - Maximum Formant for Males.</dd>
 *     <dd><code>pitchFloorFemale</code> - Pitch Floor for Females.</dd>
 *     <dd><code>pitchFloorMale</code> - Pitch Floor for Males.</dd>
 *     <dd><code>pitchCeilingFemale</code> - Pitch Ceiling for Females.</dd>
 *     <dd><code>pitchCeilingMale</code> - Pitch Ceiling for Males.</dd>
 *     <dd><code>voicingThresholdFemale</code> - Voicing Threshold for Females.</dd>
 *     <dd><code>voicingThresholdMale</code> - Voicing Threshold for Males.</dd>
 *     <dd><code>scriptFormant</code> - Formant extraction script command.</dd>
 *     <dd><code>scriptPitch</code> - Pitch extraction script command.</dd>
 *     <dd><code>scriptIntensity</code> - Intensity extraction script command.</dd>
 *     <dd><code>script</code> - A user-specified Praat script to execute on each segment.</dd>
 *   </dl>
 * <p><b>Output</b>: A JSON-encoded response containing the threadId of a task that is
 * processing the request. The task, when finished, will output a CSV files with one line
 * for each line of the input file, and fields containing the selected acoustic
 * measurements. If <code>pass_through_data</code> is "false", the output will
 * <em>not</em> contain the data from the input file, otherwise the input file data will
 * be passed through into the output file, with the acoustic measurement fields appended
 * to each line. 
 * @author Robert Fromont
 */
@WebServlet({"/api/praat"} )
public class Praat extends LabbcatServlet { // TODO unit test
   
  // Attributes:
     
  /**
   * Constructor
   */
  public Praat() {
  } // end of constructor
  
  // Servlet methods
  
  /**
   * The POST method for the servlet.
   * <p> This expects a multipart request body with parameters as defined above.
   * @param request HTTP request
   * @param response HTTP response
   */
  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    
      try {
        SqlGraphStoreAdministration store = getStore(request);
        try {
          // interpret request parameters
          MultipartRequestParameters parameters = new MultipartRequestParameters(request); 
          Vector<FileItem> files =  parameters.getFiles("csv");
          if (files.size() == 0) {
            writeResponse(response, failureResult(request, "No file received."));
            return;
          }
          // save the input file
          FileItem csv = files.elementAt(0);
          String fileName = csv.getName();
          // some browsers provide a full path, which must be truncated
          int lastSlash = fileName.lastIndexOf('/');
          if (lastSlash < 0) lastSlash = fileName.lastIndexOf('\\');
          if (lastSlash >= 0) {
            fileName = fileName.substring(lastSlash + 1);
          }
          // save the file
          File uploadedCsvFile = File.createTempFile("Praat-", "-"+fileName);
          uploadedCsvFile.delete();
          uploadedCsvFile.deleteOnExit();
          csv.write(uploadedCsvFile);        
          
          ProcessWithPraat task = new ProcessWithPraat();
          task.setStore(store); // TODO check this doesn't leak!
          task.setDataFile(uploadedCsvFile);
          
          if (parameters.getString("transcript") != null) {
            try {
              task.setTranscriptIdColumn(Integer.parseInt(parameters.getString("transcript")));
            } catch(NumberFormatException exception) {
              writeResponse(
                response, failureResult(
                  request, "Transcript column \""+parameters.getString("transcript")
                  +"\" is not an integer.")); // TODO i18n
              return;
            }
          } else {
            writeResponse(
              response, failureResult(
                request, "Transcript column not supplied.")); // TODO i18n
          }
          
          if (parameters.getString("participant") != null) {
            try {
              task.setParticipantNameColumn(
                Integer.parseInt(parameters.getString("participant")));
            } catch(NumberFormatException exception) {
              writeResponse(
                response, failureResult(
                  request, "Participant column \""+parameters.getString("participant")
                  +"\" is not an integer.")); // TODO i18n
              return;
            }
          } else {
            writeResponse(
              response, failureResult(
                request, "Participant column not supplied.")); // TODO i18n
          }
          
          if (parameters.getString("startTime") != null) {
            try {
              task.setMarkColumn(Integer.parseInt(parameters.getString("startTime")));
            } catch(NumberFormatException exception) {
              writeResponse(
                response, failureResult(
                  request, "Start time column \""+parameters.getString("startTime")
                  +"\" is not an integer.")); // TODO i18n
              return;
            }
          } else {
            writeResponse(
              response, failureResult(
                request, "Start time column not supplied.")); // TODO i18n
          }
          
          if (parameters.getString("endTime") != null) {
            try {
              task.setMarkEndColumn(Integer.parseInt(parameters.getString("endTime")));
            } catch(NumberFormatException exception) {
              writeResponse(
                response, failureResult(
                  request, "End time column \""+parameters.getString("endTime")
                  +"\" is not an integer.")); // TODO i18n
              return;
            }
          } else {
            writeResponse(
              response, failureResult(
                request, "End time column not supplied.")); // TODO i18n
          }
          
          if (parameters.getString("windowOffset") != null) {
            try {
              task.setWindowOffset(Double.parseDouble(parameters.getString("windowOffset")));
            } catch(NumberFormatException exception) {
              writeResponse(
                response, failureResult(
                  request, "Window offset \""+parameters.getString("windowOffset")
                  +"\" is not a number.")); // TODO i18n
              return;
            }
          }
          
          if (parameters.getString("samplePoints") != null) {
            StringTokenizer tokens = new StringTokenizer(
              parameters.getString("samplePoints"), " ,;:-");
            task.getSamplePoints().clear();
            while (tokens.hasMoreTokens()) {
              task.getSamplePoints().add(Double.valueOf(tokens.nextToken()));
            }            
          }
          
          if (parameters.getString("gender_attribute") != null) {
            task.setGenderAttribute(parameters.getString("gender_attribute")); // TODO deprecate
          }

          // TODO other participant attributes
          
          if (parameters.getString("pass_through_data") != null
              && parameters.getString("pass_through_data").equalsIgnoreCase("false")) {
            task.setPassThroughData(false);
          }
          task.setExtractF1("true".equalsIgnoreCase(parameters.getString("extractF1")));
          task.setExtractF2("true".equalsIgnoreCase(parameters.getString("extractF2")));
          task.setExtractF3("true".equalsIgnoreCase(parameters.getString("extractF3")));
          task.setExtractMinimumPitch(
            "true".equalsIgnoreCase(parameters.getString("extractMinimumPitch")));
          task.setExtractMeanPitch(
            "true".equalsIgnoreCase(parameters.getString("extractMeanPitch")));
          task.setExtractMaximumPitch(
            "true".equalsIgnoreCase(parameters.getString("extractMaximumPitch")));
          task.setExtractMaximumIntensity(
            "true".equalsIgnoreCase(parameters.getString("extractMaximumIntensity")));
          task.setExtractCOG1("true".equalsIgnoreCase(parameters.getString("extractCOG1")));
          task.setExtractCOG2("true".equalsIgnoreCase(parameters.getString("extractCOG2")));
          task.setExtractCOG23("true".equalsIgnoreCase(parameters.getString("extractCOG23")));
          
          if (parameters.getString("maximumFormantFemale") != null) {
            try {
              task.setMaximumFormantFemale(
                Integer.parseInt(parameters.getString("maximumFormantFemale")));
            } catch(NumberFormatException exception) {
              writeResponse(
                response, failureResult(
                  request, "maximumFormantFemale \""+parameters.getString("maximumFormantFemale")
                  +"\" is not an integer.")); // TODO i18n
              return;
            }
          }
          if (parameters.getString("maximumFormantMale") != null) {
            try {
              task.setMaximumFormantMale(
                Integer.parseInt(parameters.getString("maximumFormantMale")));
            } catch(NumberFormatException exception) {
              writeResponse(
                response, failureResult(
                  request, "maximumFormantMale \""+parameters.getString("maximumFormantMale")
                  +"\" is not an integer.")); // TODO i18n
              return;
            }
          }
          
          if (parameters.getString("pitchFloorFemale") != null) {
            try {
              task.setPitchFloorFemale(
                Integer.parseInt(parameters.getString("pitchFloorFemale")));
            } catch(NumberFormatException exception) {
              writeResponse(
                response, failureResult(
                  request, "pitchFloorFemale \""+parameters.getString("pitchFloorFemale")
                  +"\" is not an integer.")); // TODO i18n
              return;
            }
          }
          if (parameters.getString("pitchFloorMale") != null) {
            try {
              task.setPitchFloorMale(
                Integer.parseInt(parameters.getString("pitchFloorMale")));
            } catch(NumberFormatException exception) {
              writeResponse(
                response, failureResult(
                  request, "pitchFloorMale \""+parameters.getString("pitchFloorMale")
                  +"\" is not an integer.")); // TODO i18n
              return;
            }
          }
          
          if (parameters.getString("pitchCeilingFemale") != null) {
            try {
              task.setPitchCeilingFemale(
                Integer.parseInt(parameters.getString("pitchCeilingFemale")));
            } catch(NumberFormatException exception) {
              writeResponse(
                response, failureResult(
                  request, "pitchCeilingFemale \""+parameters.getString("pitchCeilingFemale")
                  +"\" is not an integer.")); // TODO i18n
              return;
            }
          }
          if (parameters.getString("pitchCeilingMale") != null) {
            try {
              task.setPitchCeilingMale(
                Integer.parseInt(parameters.getString("pitchCeilingMale")));
            } catch(NumberFormatException exception) {
              writeResponse(
                response, failureResult(
                  request, "pitchCeilingMale \""+parameters.getString("pitchCeilingMale")
                  +"\" is not an integer.")); // TODO i18n
              return;
            }
          }
          
          if (parameters.getString("voicingThresholdFemale") != null) {
            try {
              task.setVoicingThresholdFemale(
                Double.parseDouble(parameters.getString("voicingThresholdFemale")));
            } catch(NumberFormatException exception) {
              writeResponse(
                response, failureResult(
                  request, "voicingThresholdFemale \""+parameters.getString("voicingThresholdFemale")
                  +"\" is not a number.")); // TODO i18n
              return;
            }
          }
          if (parameters.getString("voicingThresholdMale") != null) {
            try {
              task.setVoicingThresholdMale(
                Double.parseDouble(parameters.getString("voicingThresholdMale")));
            } catch(NumberFormatException exception) {
              writeResponse(
                response, failureResult(
                  request, "voicingThresholdMale \""+parameters.getString("voicingThresholdMale")
                  +"\" is not a number.")); // TODO i18n
              return;
            }
          }
          
          if (parameters.getString("scriptFormant") != null
              && parameters.getString("scriptFormant").length() > 0) {
            task.setScriptFormant(parameters.getString("scriptFormant"));
          }
          if (parameters.getString("scriptPitch") != null
              && parameters.getString("scriptPitch").length() > 0) {
            task.setScriptPitch(parameters.getString("scriptPitch"));
          }
          if (parameters.getString("scriptIntensity") != null
              && parameters.getString("scriptIntensity").length() > 0) {
            task.setScriptIntensity(parameters.getString("scriptIntensity"));
          }
          if (parameters.getString("script") != null
              && parameters.getString("script").length() > 0) {
            task.setCustomScript(parameters.getString("script"));
          }
          
          // TODO fasttrack

          // start the task
          task.setName(uploadedCsvFile.getName());
          if (request.getRemoteUser() != null) {	
            task.setWho(request.getRemoteUser());
          } else {
            task.setWho(request.getRemoteHost());
          }
          task.start();
          
          // return its ID
          JsonObjectBuilder jsonResult = Json.createObjectBuilder()
            .add("threadId", task.getId());
          writeResponse(
            response, successResult(request, jsonResult.build(), null));
          
        } finally {
          // TODO cacheStore(store);
        }
      } catch(Exception ex) {
        throw new ServletException(ex);
      }
  }
  
   private static final long serialVersionUID = -1;
} // end of class SerializeGraphs
