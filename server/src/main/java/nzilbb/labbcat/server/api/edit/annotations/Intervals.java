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

package nzilbb.labbcat.server.api.edit.annotations;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import nzilbb.ag.*;
import nzilbb.ag.ql.QL;
import nzilbb.ag.serialize.GraphDeserializer;
import nzilbb.ag.serialize.SerializationDescriptor;
import nzilbb.ag.serialize.SerializationException;
import nzilbb.ag.serialize.SerializationException;
import nzilbb.ag.serialize.SerializationParametersMissingException;
import nzilbb.ag.serialize.SerializationParametersMissingException;
import nzilbb.ag.serialize.SerializerNotConfiguredException;
import nzilbb.ag.serialize.util.ConfigurationHelper;
import nzilbb.ag.serialize.util.IconHelper;
import nzilbb.ag.serialize.util.NamedStream;
import nzilbb.ag.serialize.util.Utility;
import nzilbb.ag.util.DefaultOffsetGenerator;
import nzilbb.ag.util.Merger;
import nzilbb.ag.util.Normalizer;
import nzilbb.ag.util.ParticipantRenamer;
import nzilbb.configure.Parameter;
import nzilbb.configure.ParameterSet;
import nzilbb.labbcat.server.api.APIRequestHandler;
import nzilbb.labbcat.server.api.RequestParameters;
import nzilbb.labbcat.server.api.RequiredRole;
import nzilbb.labbcat.server.db.IdMatch;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.labbcat.server.db.SqlGraphStore;
import nzilbb.labbcat.server.db.StoreCache;
import nzilbb.labbcat.server.task.Task;
import nzilbb.util.IO;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * <tt>/api/edit/annotations/intervals</tt>
 * : Handler for receiving and processing interval labels in an annotated CSV results file.
 * <h3 id="POST"> <tt>/api/edit/annotations/intervals</tt> </h3>
 * <p> <b> POST </b> uploads an annotated CSV results file and imports the
 *  specified column(s) of labels for annotating intervals - i.e. from given start times
 *  to given end times. 
 * <p> The multipart-encoded parameters are:
 *  <dl>
 *   <dt> csv </dt>
 *       <dd> CSV file containing the attribute values to import. </dd>
 *   <dt> transcriptColumn </dt>
 *       <dd> The (zero based) index of the column that contains the transcript name
 *            to annotate. 
 *   <dt> startTimeColumn </dt>
 *       <dd> The (zero based) index of the column that contains the start time for the
 *            annotation.</dd>
 *   <dt> endTimeColumn </dt>
 *       <dd> The (zero based) index of the column that contains the end time for the
 *            annotation.</dd>
 *   <dt> columnLayer </dt>
 *       <dd> Multiple values, where the index of the value corresponds to the
 *            (zero based) CSV column index, and the value is blank to ignore
 *            the column, or the layer ID of a layer to update with an annotation
 *            labelled with the CSV cell's content.</dd>
 *  </dl>
 * <p><b>Output</b>: A JSON-encoded response containing the threadId of a task that is
 * processing the request.
 * @author Robert Fromont robert@fromont.net.nz
 */
@RequiredRole("edit")
public class Intervals extends APIRequestHandler {
  
  /**
   * Default constructor.
   */
  public Intervals() {
  } // end of constructor
  
  /**
   * The POST method for the servlet.
   * @param requestParameters Request parameter map.
   * @param httpStatus Receives the response status code, in case of error.
   * @return JSON-encoded object representing the response
   */
  public JsonObject post(RequestParameters requestParameters, Consumer<Integer> httpStatus) {
    // context.servletLog(
    //   "POST post " + requestParameters
    //   + (requestParameters.getFile("csv") != null?
    //      requestParameters.getFile("csv").getPath():"(no data file)"));
    File dir = null;
    try {
      final SqlGraphStoreAdministration store = getStore();
      if (!hasAccess(store.getConnection())) {
        cacheStore(store);
        httpStatus.accept(SC_FORBIDDEN);
        return null;
      }
      Schema schema = store.getSchema();
      File csvFile = requestParameters.getFile("csv");
      if (csvFile == null) {
        cacheStore(store);
        httpStatus.accept(SC_BAD_REQUEST);
        return failureResult("No file received.");
      }
      // deduce field delimiter
      char csvFieldDelimiter = ',';
      long l = 0;
      try (BufferedReader r =  new BufferedReader(new FileReader(csvFile))) {
        String headers = r.readLine();
        while (headers != null && headers.trim().length() == 0) headers = r.readLine();
        if (headers != null) {
          if (headers.contains("\t")) csvFieldDelimiter = '\t';
          else if (headers.contains(";")) csvFieldDelimiter = ';';
          else if (headers.contains(",")) csvFieldDelimiter = ',';
        }
        // now count lines
        while (r.readLine() != null) l++;
      } // close r
      final long totalLines = l;
      final char finalCsvFieldDelimiter = csvFieldDelimiter;

      String transcriptColumnString = requestParameters.getString("transcriptColumn");
      int transcriptColumn = -1;
      if (transcriptColumnString == null) {
        httpStatus.accept(SC_BAD_REQUEST);
        return failureResult("Transcript column not supplied.");
      }
      try {
        transcriptColumn = Integer.parseInt(transcriptColumnString);
      } catch(NumberFormatException exception) {
        httpStatus.accept(SC_BAD_REQUEST);
        return failureResult(
          "Transcript column \"{0}\" is not an integer", transcriptColumnString);
      }
      // some things for generating new participant names if required
      if (transcriptColumn < 0) {
        httpStatus.accept(SC_BAD_REQUEST);
        return failureResult(
          "Transcript column \"{0}\" must be a positive integer", transcriptColumn);
      }
      final int finalTranscriptColumn = transcriptColumn;
        
      String startTimeColumnString = requestParameters.getString("startTimeColumn");
      int startTimeColumn = -1;
      if (startTimeColumnString == null) {
        httpStatus.accept(SC_BAD_REQUEST);
        return failureResult("Start time column not supplied.");
      }
      try {
        startTimeColumn = Integer.parseInt(startTimeColumnString);
      } catch(NumberFormatException exception) {
        httpStatus.accept(SC_BAD_REQUEST);
        return failureResult(
          "Start time column \"{0}\" is not an integer", startTimeColumnString);
      }
      // some things for generating new participant names if required
      if (startTimeColumn < 0) {
        httpStatus.accept(SC_BAD_REQUEST);
        return failureResult(
          "Start time column \"{0}\" must be a positive integer", startTimeColumn);
      }
      final int finalStartTimeColumn = startTimeColumn;
        
      String endTimeColumnString = requestParameters.getString("endTimeColumn");
      int endTimeColumn = -1;
      if (endTimeColumnString == null) {
        httpStatus.accept(SC_BAD_REQUEST);
        return failureResult("End time column not supplied.");
      }
      try {
        endTimeColumn = Integer.parseInt(endTimeColumnString);
      } catch(NumberFormatException exception) {
        httpStatus.accept(SC_BAD_REQUEST);
        return failureResult(
          "End time column \"{0}\" is not an integer", endTimeColumnString);
      }
      // some things for generating new participant names if required
      if (endTimeColumn < 0) {
        httpStatus.accept(SC_BAD_REQUEST);
        return failureResult(
          "End time column \"{0}\" must be a positive integer", endTimeColumn);
      }
      final int finalEndTimeColumn = endTimeColumn;
        
      String[] columnLayer = requestParameters.getStrings("columnLayer");
      if (columnLayer == null || columnLayer.length == 0) {
        httpStatus.accept(SC_BAD_REQUEST);
        return failureResult("No layers specified.");
      }
        
      Vector<String> messages = new Vector<String>();

      // open CSV file to check the headers
      CSVParser parser = CSVParser.parse(
        csvFile, java.nio.charset.Charset.forName("UTF-8"),
        CSVFormat.EXCEL
        .withDelimiter(csvFieldDelimiter)
        .withIgnoreEmptyLines(true));
      Iterator<CSVRecord> records = parser.iterator();
      CSVRecord headers = records.next();
      if (columnLayer.length > headers.size()) {
        httpStatus.accept(SC_BAD_REQUEST);
        return failureResult(
          "There are more column to layer mappings ({0}) than columns ({1})",
          columnLayer.length, headers.size());
      }
      if (transcriptColumn >= headers.size()) {
        httpStatus.accept(SC_BAD_REQUEST);
        return failureResult(
          "Transcript column ({0}) is greater than the number of columns ({1})",
          transcriptColumn, headers.size());
      }
      if (startTimeColumn >= headers.size()) {
        httpStatus.accept(SC_BAD_REQUEST);
        return failureResult(
          "Start time column ({0}) is greater than the number of columns ({1})",
          startTimeColumn, headers.size());
      }
      if (endTimeColumn >= headers.size()) {
        httpStatus.accept(SC_BAD_REQUEST);
        return failureResult(
          "End time column ({0}) is greater than the number of columns ({1})",
          endTimeColumn, headers.size());
      }
      String[] fields = new String[headers.size()];
      for (int c = 0; c < headers.size(); c++) fields[c] = headers.get(c);
        
      // check all layers exist and are transcript attributes
      Layer[] fieldLayer = new Layer[columnLayer.length];
      int mappingCount = 0;
      for (int c = 0; c < columnLayer.length; c++) {
        if (columnLayer[c].length() > 0) {
          Layer layer = fieldLayer[c] = schema.getLayer(columnLayer[c]);
          if (layer == null) {
            httpStatus.accept(SC_BAD_REQUEST);
            return failureResult("Invalid layer ID: {0}", columnLayer[c]);
          }
          mappingCount++;
          if (layer.getParentId().equals(schema.getRoot().getId())) {
            if (layer.getAlignment() != Constants.ALIGNMENT_INTERVAL
                || (layer.get("layer_manager_id") != null
                    && ((String)layer.get("layer_manager_id")).length() > 0)) {
              httpStatus.accept(SC_BAD_REQUEST);
              return failureResult("Cannot annotate layer: {0}", columnLayer[c]);
            }
          } else {
            return failureResult("Cannot annotate layer: {0}", columnLayer[c]);
          }
        } else {
          messages.add(localize("Ignoring column: {0}", fields[c]));
        }
      } // next column

      if (mappingCount == 0) {
        httpStatus.accept(SC_BAD_REQUEST);
        return failureResult("No layers specified");
      }
      
      final String[] layerIds = Arrays.stream(fieldLayer)
        .filter(layer -> layer != null)
        .map(layer -> layer.getId())
        .collect(Collectors.toList())
        .toArray(new String[0]);
      parser.close();

      Task task = new Task() {
          public void run() {
            try {
              runStart();

              long recordCount = 0;
              long skippedCount = 0;
              Vector<String> warnings = new Vector<String>();

              try(CSVParser parser = CSVParser.parse(
                    csvFile, java.nio.charset.Charset.forName("UTF-8"),
                    CSVFormat.EXCEL
                    .withDelimiter(finalCsvFieldDelimiter)
                    .withIgnoreEmptyLines(true))) {
                
                Iterator<CSVRecord> records = parser.iterator();
                // skip headers
                CSVRecord headers = records.next();
                
                // now import all data
                Graph graph = null;
                long row = 0;
                while (records.hasNext()) {	       
                  CSVRecord record = records.next();
                  row++;
                  iPercentComplete = (int)((row*100)/totalLines);
                      
                  String identifier = record.get(finalTranscriptColumn);
                  try {
                    if (identifier == null || identifier.length() == 0) continue;
                    if (identifier.matches("^http.*#e.?_[0-9]+_[0-9]+ *$")) {
                      // we've been given a URL rather than a name
                      identifier = identifier.replaceAll(
                        ".*(ag_id|transcript|id)=([^&#]+).*","$2");
                    } // we've been given a URL rather than a name
                    
                    if (graph == null || !graph.get("@identifier").equals(identifier)) {
                      // graph is changing
                      if (graph != null && graph.getId() != null) {
                        // (not a dummy in place of one that wasn't found)
                        // save previous graph
                        store.saveTranscript(graph);
                      }
                      
                      try {
                        graph = store.getTranscript(identifier, layerIds);
                        graph.setTracker(new ChangeTracker()); // so changes are saved
                      } catch(GraphNotFoundException notFound) {
                        String warning = "Row "+ row + ": " + notFound.getMessage();
                        warnings.add(warning);
                        setStatus(warning);
                        // create a dummy graph, so that we don't repeat the error a gazillion times
                        graph = new Graph();
                      }
                      // the ID in the CSV file may be ag_id or recording name rather than
                      // the transcript name, so we store the CSV ID separately, for
                      // detecting graph change
                      graph.put("@identifier", identifier);
                    } // graph is changing
                    
                    if (graph.getId() == null) {
                      skippedCount++;
                      continue;
                    }
                    double startOffset = Double.valueOf(record.get(finalStartTimeColumn));
                    double endOffset = Double.valueOf(record.get(finalEndTimeColumn));
                    
                    if (endOffset <= startOffset) {
                      warnings.add("Row "+ row + ": " + identifier
                                   + ": Interval length must be greater than zero: "
                                   + startOffset + "-" + endOffset);
                      skippedCount++;
                    } else {
                      Anchor start = graph.getOrCreateAnchorAt(
                        startOffset, Constants.CONFIDENCE_MANUAL);
                      Anchor end = graph.getOrCreateAnchorAt(
                        endOffset, Constants.CONFIDENCE_MANUAL);
                      recordCount++;
                      // for each column
                      for (int c = 0; c < columnLayer.length; c++) {
                        String label = record.get(c);
                        String layerId = columnLayer[c];
                        Layer layer = fieldLayer[c];
                        if (layer != null) { // column has been mapped
                          // delete any old overlapping annotations here
                          for (Annotation overlapping : graph.overlappingAnnotations(
                                 start, end, layer.getId())) {
                            overlapping.destroy();
                          } // next overlapping annotation
                          if (label != null) {
                            label = label.trim();
                            if (label.length() > 0) {
                              Annotation annotation = graph.createAnnotation(
                                start, end, layer.getId(), label, graph);
                            } // non-empty label
                          } // non-null label
                        } // column mapped to layer
                      } // next column
                    } // start <= end
                  } catch (NumberFormatException parseError) {
                    String warning = "Row "+ row + ": " + identifier
                      + ": Could not parse offset: " + parseError.getMessage();
                    warnings.add(warning);
                    setStatus(warning);
                    skippedCount++;
                  } catch (Exception ex) {
                    String warning = "Row "+ row + ": " + identifier
                      + ": " + ex.getClass().getSimpleName() + " " + ex.getMessage();
                    warnings.add(warning);
                    setStatus(warning);
                    ex.printStackTrace(System.out);
                    skippedCount++;
                  } // skip this line
                } // next line
            
                if (graph != null && graph.getId() != null) {
                  // (not a dummy in place of one that wasn't found))
                  // save last graph
                  store.saveTranscript(graph);
                }
              } // close parser

              iPercentComplete = 100;
              if (warnings.size() == 0) {
                if (skippedCount == 0) {
                  setStatus("Processed " + recordCount + " intervals");
                } else {
                  setStatus(
                    "Processed " + recordCount + " intervals (skipped "+skippedCount+")");
                }
              } else {
                throw new Exception(
                  "Processed " + recordCount + " intervals"
                  +" (skipped "+skippedCount+")"
                  +" with " + warnings.size()
                  +(warnings.size()!=1?" warnings":" warning:"+warnings.elementAt(0)));
              }
            } catch (Exception ex) {
              setLastException(ex);
              if (ex.getClass().getName().equals("java.lang.Exception")) { // generic error
                setStatus(ex.getMessage());
              } else {
                setStatus("run(): " + ex.getClass().getName() + " - " + ex.getMessage());
              }
            } finally {
              csvFile.delete();
              runEnd();
            }
              
            if (bCancelling) {
              setStatus(getStatus() + " - cancelled.");
            }
            
            waitToDie();
              
            release(); // ensure any resources are released
          } // run
        };
      try {
        task.setStoreCache(new StoreCache() {
            public SqlGraphStore get() {
              return store;
            }
            public void accept(SqlGraphStore store) {
              cacheStore((SqlGraphStoreAdministration)store);
            }
          });
        if (context.getUser() != null) {	
          task.setWho(context.getUser());
        } else {
          task.setWho(context.getUserHost());
        }
          
        task.start();
          
        // return its ID
        JsonObjectBuilder jsonResult = Json.createObjectBuilder()
          .add("threadId", ""+task.getId());
        return successResult(jsonResult.build(), null);
      } catch(Exception ex) {
        cacheStore(store);
        return failureResult(ex);
      }
    } catch(Exception ex) {
      try {
        httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      } catch(Exception exception) {}
      context.servletLog("POST Intervals.post: unhandled exception: " + ex);
      ex.printStackTrace(System.err);
      return failureResult(ex);
    }
  }
} // end of class Intervals
