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
import nzilbb.labbcat.server.api.RequiredRole;
import nzilbb.labbcat.server.api.RequestParameters;
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
 * <tt>/api/edit/annotations/tokens</tt>
 * : Handler for receiving and processing token labels in an annotated CSV results file.
 * <h3 id="POST"> <tt>/api/edit/annotations/tokens</tt> </h3>
 * <p> <b> POST </b> uploads an annotated CSV results file and imports the
 *  specified column(s) of annotation labels. 
 * <p> The multipart-encoded parameters are:
 *  <dl>
 *   <dt> csv </dt>
 *       <dd> CSV file containing the attribute values to import. </dd>
 *   <dt> idColumn </dt>
 *       <dd> The (zero based) index of the column that contains the MatchId or other
 *            ID, which may identify a single word token, multiple consecutive tokens
 *            (e.g. matches from searches spanning multiple tokens) or a segment token.
 *            If the token exists, it will be annotated on the layers specified by
 *            <var>columnLayer</var>. If the token can't be identified, the row is ignored.</dd>
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
public class Tokens extends APIRequestHandler {
  
  /**
   * Default constructor.
   */
  public Tokens() {
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

      String idColumnString = requestParameters.getString("idColumn");
      int idColumn = -1;
      if (idColumnString == null) {
        httpStatus.accept(SC_BAD_REQUEST);
        return failureResult("No ID specified.");
      }
      try {
        idColumn = Integer.parseInt(idColumnString);
      } catch(NumberFormatException exception) {
        httpStatus.accept(SC_BAD_REQUEST);
        return failureResult(
          "Transcript column \"{0}\" is not an integer", idColumnString);
      }
      // some things for generating new participant names if required
      if (idColumn < 0) {
        httpStatus.accept(SC_BAD_REQUEST);
        return failureResult(
          "Transcript column \"{0}\" must be a positive integer", idColumn);
      }
      final int finalIdColumn = idColumn;
        
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
      if (idColumn >= headers.size()) {
        httpStatus.accept(SC_BAD_REQUEST);
        return failureResult(
          "ID column ({0}) is greater than the number of columns ({1})",
          idColumn, headers.size());
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
          if (layer.getParentId().equals(schema.getWordLayerId())) {
            if (layer.getAlignment() != Constants.ALIGNMENT_NONE
                || (layer.get("layer_manager_id") != null
                    && ((String)layer.get("layer_manager_id")).length() > 0)) {
              httpStatus.accept(SC_BAD_REQUEST);
              return failureResult("Cannot annotate layer: {0}", columnLayer[c]);
            }
          } else if (layer.getParentId().equals(schema.getTurnLayerId())) {
            if (layer.getId().equals(schema.getWordLayerId())
                || layer.getId().equals(schema.getUtteranceLayerId())
                || (layer.get("layer_manager_id") != null
                    && ((String)layer.get("layer_manager_id")).length() > 0)) {
              httpStatus.accept(SC_BAD_REQUEST);
              return failureResult("Cannot annotate layer: {0}", columnLayer[c]);
            }
          } else if (layer.getParentId().equals("segment")) {
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
        return failureResult("No layers specified.");
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
                      
                  String identifier = record.get(finalIdColumn);
                  if (identifier == null || identifier.length() == 0) {
                    setStatus(localize("Row {0} was ignored: no ID specified", row));
                    skippedCount++;
                    continue;
                  }
                  recordCount++;
                  
                  String turnId = null;
                  HashMap<String,Annotation> words = new HashMap<String,Annotation>();
                  String targetId = null;
                  Annotation target = null;
                  String startId = null;
                  String endId = null;
                  String graphId = null;
                  Collection<String> matchAnnotationUids = null;

                  if (identifier.matches("^g_.*;\\[0\\]=e.?_[0-9]+_[0-9]+(;.*)?$")) {
                    // like: g_340;em_12_123;n_234-n_345;p_13;#=ew_0_456;[0]=ew_0_456
                    IdMatch id = new IdMatch(identifier);
                    graphId = id.getGraphId().toString();
                    targetId = id.getTargetAnnotationUid();
                    matchAnnotationUids = id.getMatchAnnotationUids().values();
                    if (target == null) { // need to get target separately (it's not a word)
                      Annotation[] matches = store.getMatchingAnnotations(
                        "id = '"+targetId+"'");
                      if (matches.length > 0) {
                        target = matches[0];
                      }
                    }
                  } else if (identifier.matches("^http.*#e.?_[0-9]+_[0-9]+ *$")) {
                    String transcriptId = identifier.replaceAll(
                      ".*(ag_id|transcript|id)=([^&#]+).*","$2");
                    // the suffix is the annotation_id
                    identifier = identifier.substring(identifier.lastIndexOf("#") + 1).trim();
                    
                    // is it an ID like ew_0_456?
                    targetId = identifier;
                    matchAnnotationUids = new Vector<String>();
                    matchAnnotationUids.add(identifier);
                    graphId = transcriptId;
                  } // maybe URL
              
                  Annotation word = null;

                  // for each column
                  for (int c = 0; c < columnLayer.length; c++) {
                    String label = record.get(c);
                    String layerId = columnLayer[c];
                    Layer layer = fieldLayer[c];
                    if (layer != null) { // column has been mapped
                      try {
                        if (layer.getParentId().equals(target.getLayerId())) {
                          // layer is child of target
                          if (target != null) {
                            Annotation[] matches = store.getMatchingAnnotations(
                              "layer.id = '"+layer.getId().replaceAll("'","\\'")+"'"
                              +" AND parent.id = '" + target.getId() + "'");
                            for (Annotation child : matches) {
                              store.destroyAnnotation(graphId, child.getId());
                            }
                            if (label.length() > 0) { // add annotation
                              store.createAnnotation(
                                graphId, null, null, layer.getId(),
                                label, Constants.CONFIDENCE_MANUAL, target.getId());
                            } // delete annotation
                          } // target was found
                        } else if (layer.getParentId().equals(schema.getWordLayerId())) {
                          // word scope (and the target is a segment)
                          if (target.getParentId() != null) {
                            if (layer.getAlignment() == Constants.ALIGNMENT_NONE) {
                              // by parent
                              Annotation[] matches = store.getMatchingAnnotations(
                                "layer.id = '"+layer.getId().replaceAll("'","\\'")+"'"
                                +" AND parent.id = '" + target.getParentId() + "'");
                              for (Annotation child : matches) {
                                store.destroyAnnotation(graphId, child.getId());
                              }
                              if (label.length() > 0) { // add annotation
                                store.createAnnotation(
                                  graphId, null, null, layer.getId(),
                                  label, Constants.CONFIDENCE_MANUAL, target.getParentId());
                              } // delete annotation
                            } else { // by anchor
                              if (startId == null) {
                                StringBuffer query = new StringBuffer();
                                for (String uid : matchAnnotationUids) {
                                  if (query.length() == 0) {
                                    query.append("id IN (");
                                  } else {
                                    query.append(",");
                                  }
                                  query.append("'");
                                  query.append(uid);
                                  query.append("'");
                                } // next matched UID
                                query.append(")");
                                Annotation[] matches = store.getMatchingAnnotations(
                                  query.toString());
                                if (matches.length > 0) {
                                  for (Annotation match : matches) {
                                    words.put(match.getId(), match);
                                    if (match.getId().equals(targetId)) {
                                      target = match;
                                      word = target;
                                    }
                                  }
                                  Annotation firstWord = matches[0];
                                  Annotation lastWord = matches[matches.length - 1];
                                  startId = firstWord.getStartId(); 
                                  endId = lastWord.getEndId();
                                }
                              }
                              // delete existing annotation(s) first
                              Annotation[] matches = store.getMatchingAnnotations(
                                "layer.id = '"+layer.getId().replaceAll("'","\\'")+"'"
                                +" AND start.id = '" + startId + "'"
                                +" AND end.id = '" + endId + "'");
                              for (Annotation child : matches) {
                                store.destroyAnnotation(graphId, child.getId());
                              }
                              
                              if (label.length() > 0) { // add annotation
                                store.createAnnotation(
                                  graphId, startId, endId, layer.getId(),
                                  label, Constants.CONFIDENCE_MANUAL, word.getId());
                              } // delete annotation
                            } // by anchor
                          } // word was found
                        } else if (layer.getParentId().equals(schema.getTurnLayerId())) {
                          // meta scope
                          if (word == null) {
                            StringBuffer query = new StringBuffer();
                            for (String uid : matchAnnotationUids) {
                              if (query.length() == 0) {
                                query.append("id IN (");
                              } else {
                                query.append(",");
                              }
                              query.append("'");
                              query.append(uid);
                              query.append("'");
                            } // next matched UID
                            query.append(")");
                            Annotation[] matches = store.getMatchingAnnotations(
                              query.toString());
                            if (matches.length > 0) {
                              for (Annotation match : matches) {
                                words.put(match.getId(), match);
                                if (match.getId().equals(targetId) || word == null) {
                                  word = match;
                                }
                              }
                              Annotation firstWord = matches[0];
                              Annotation lastWord = matches[matches.length - 1];
                              startId = firstWord.getStartId(); 
                              endId = lastWord.getEndId();
                            }
                          } // word == null
                          if (word.getParentId() != null) {
                            // delete existing annotation(s) first
                            Annotation[] matches = store.getMatchingAnnotations(
                              "layer.id = '"+layer.getId().replaceAll("'","\\'")+"'"
                              +" AND start.id = '" + startId + "'"
                              +" AND end.id = '" + endId + "'");
                            for (Annotation child : matches) {
                              store.destroyAnnotation(graphId, child.getId());
                            }
                      
                            if (label.length() > 0)
                            { // add annotation
                              store.createAnnotation(
                                graphId, startId, endId, layer.getId(),
                                label, Constants.CONFIDENCE_MANUAL, word.getParentId());
                            } // delete annotation
                          } // turn was found
                        } // meta scope
                      } catch(Exception exception) {
                        setStatus(
                          "Line "+ row + ": Error adding " + label + " for " + identifier
                          + ": " + exception);
                        warnings.add(
                          "Line "+ row + ": Error adding " + label + " for " + identifier
                          + ": " + exception);
                      }
                    } // column has been mapped
                    
                  } // next column                      
                } // next record
              } // close parser

              iPercentComplete = 100;
              if (warnings.size() == 0) {
                if (skippedCount == 0) {
                  setStatus("Processed " + recordCount + " tokens");
                } else {
                  setStatus(
                    "Processed " + recordCount + " tokens (skipped "+skippedCount+")");
                }
              } else {
                throw new Exception(
                  "Processed " + recordCount + " tokens"
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
      context.servletLog("POST Tokens.post: unhandled exception: " + ex);
      ex.printStackTrace(System.err);
      return failureResult(ex);
    }
  }
} // end of class Tokens
