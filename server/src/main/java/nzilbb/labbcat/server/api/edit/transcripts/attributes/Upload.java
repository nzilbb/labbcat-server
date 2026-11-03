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

package nzilbb.labbcat.server.api.edit.transcripts.attributes;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.Arrays;
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
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.util.IO;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * <tt>/api/edit/transcripts/attributes/upload</tt>
 * : Handler for receiving and processing transcript attribute values in a CSV file.
 * <h3 id="POST"> <tt>/api/edit/transcripts/attributes/upload</tt> </h3>
 * <p> <b> POST </b> uploads a CSV file and imports the specified transcript attributes. 
 * <p> The multipart-encoded parameters are:
 *  <dl>
 *   <dt> csv </dt>
 *       <dd> CSV file containing the attribute values to import. </dd>
 *   <dt> idColumn </dt>
 *       <dd> The (zero based) index of the column that identifies the transcript;
 *            if the transcript exists, its attribute values will be updated,
 *            otherwise, the row is ignored.</dd>
 *   <dt> columnLayer </dt>
 *       <dd> Multiple values, where the index of the value corresponds to the
 *            (zero based) CSV column index, and the value is blank to ignore
 *            the column, or the layer ID of the transcript attribute to update.</dd>
 *  </dl>
 * <p><b>Output</b>: A JSON-encoded response containing a <q>model</q> with the following
 * attributes:
 *  <dl>
 *   <dt> updated </dt>
 *       <dd> the number of existing transcript that were updated. </dd> 
 *   <dt> missing </dt>
 *       <dd> the number of transcript that were not found. </dd> 
 *  </dl>
 * @author Robert Fromont robert@fromont.net.nz
 */
@RequiredRole("edit")
public class Upload extends APIRequestHandler {

  /**
   * Default constructor.
   */
  public Upload() {
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
      SqlGraphStoreAdministration store = getStore();
      Schema schema = store.getSchema();
      File csvFile = requestParameters.getFile("csv");
      try {
        if (!hasAccess(store.getConnection())) {
          httpStatus.accept(SC_FORBIDDEN);
          return null;
        }
        if (csvFile == null) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No file received.");
        }
        // deduce field delimiter
        char csvFieldDelimiter = ',';
        try (BufferedReader r =  new BufferedReader(new FileReader(csvFile))) {
          String headers = r.readLine();
          while (headers != null && headers.trim().length() == 0) headers = r.readLine();
          if (headers != null) {
            if (headers.contains("\t")) csvFieldDelimiter = '\t';
            else if (headers.contains(";")) csvFieldDelimiter = ';';
            else if (headers.contains(",")) csvFieldDelimiter = ',';
          }
        } // close r

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
        MessageFormat speakerIdFormat = null;
        if (idColumn < 0) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult(
            "Transcript column \"{0}\" must be a positive integer", idColumn);
        }
        
        String[] columnLayer = requestParameters.getStrings("columnLayer");
        if (columnLayer == null || columnLayer.length == 0) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No layers specified.");
        }
        
        // counts
        int updated = 0;
        int missing = 0;
        Vector<String> messages = new Vector<String>();

        // open CSV file
        try (CSVParser parser = CSVParser.parse(
               csvFile, java.nio.charset.Charset.forName("UTF-8"),
               CSVFormat.EXCEL
               .withDelimiter(csvFieldDelimiter)
               .withIgnoreEmptyLines(true))) {
          Iterator<CSVRecord> records = parser.iterator();
          CSVRecord headers = records.next();
          if (columnLayer.length > headers.size()) {
            httpStatus.accept(SC_BAD_REQUEST);
            return failureResult(
              "There are more column to layer mappings ({0}) than columns ({1}).",
              columnLayer.length, headers.size());
          }
          String[] fields = new String[headers.size()];
          for (int c = 0; c < headers.size(); c++) fields[c] = headers.get(c);
          
          // check all layers exist and are transcript attributes
          Layer[] fieldLayer = new Layer[columnLayer.length];
          for (int c = 0; c < columnLayer.length; c++) {
            if (columnLayer[c].length() > 0) {
              fieldLayer[c] = schema.getLayer(columnLayer[c]);
              if (fieldLayer[c] == null) {
                httpStatus.accept(SC_BAD_REQUEST);
                return failureResult("Invalid layer ID: {0}", columnLayer[c]);
              }
              if (!fieldLayer[c].getId().startsWith("transcript_")
                  || !fieldLayer[c].getParentId().equals(schema.getRoot().getId())
                  || fieldLayer[c].getAlignment() != 0
                  || !"transcript".equals(fieldLayer[c].get("class_id"))) {
                httpStatus.accept(SC_BAD_REQUEST);
                return failureResult("Not a transcript attribute: {0}", columnLayer[c]);
              }
            } else {
              messages.add(localize("Ignoring column: {0}", fields[c]));
            }
          } // next column
          String[] attributeLayerIds = Arrays.stream(fieldLayer)
            .filter(layer -> layer != null)
            .map(layer -> layer.getId())
            .collect(Collectors.toList())
            .toArray(new String[0]);
          
          // now import all data
          int row = 0;
          while (records.hasNext()) {	       
            CSVRecord record = records.next();
            row++;
            String id = record.get(idColumn);
            if (id == null || id.length() == 0) {
              messages.add(localize("Row {0} was ignored: no ID specified."));
              continue;
            }
            try { // find the transcript            
              Graph transcript = store.getTranscript(id, attributeLayerIds);
              
              updated++;
              transcript.setTracker(new ChangeTracker());
            
              // now process the columns...
              
              // there may be multiple columns mapped to the same peer-allowing layer
              // for these, we keep track of all the values we've added
              HashMap<String,HashSet<String>> layerToValues
                = new HashMap<String,HashSet<String>>();
              
              // for each column
              for (int c = 0; c < columnLayer.length; c++) {
                String value = record.get(c);
                String layerId = columnLayer[c];
                Layer layer = fieldLayer[c];
                if (layer != null) { // layer mapping specified
                  value = standardizeLabel(value, layer);
                  
                  if (!layer.getPeers()) { // single value
                    Annotation annotation = transcript.first(layer.getId());
                    if (annotation != null) { // existing value
                      if (value == null || value.length() == 0) { // delete value
                        annotation.destroy();
                      } else { // update
                        annotation.setLabel(value);
                      }
                    } else { // insert
                      transcript.createTag(transcript, layer.getId(), value);
                    }
                  } else { // possibly multiple values
                    // if multiple columns map to this same layer, there may already be values
                    if (!layerToValues.containsKey(layerId)) {
                      layerToValues.put(layerId, new HashSet<String>()); 
                    }
                    HashSet<String> newValues = layerToValues.get(layerId);
                    // possibly multiple values delimited by newline
                    String[] multipleValues = value.split("\n");
                    if (multipleValues.length > 0) { // multiple values
                      for (String v : multipleValues) {
                        v = v.trim();
                        if (v.length() > 0 // if the value is not blank
                            // (unless blank is explicitly valid)
                            || layer.getValidLabels().containsKey("")) {
                          newValues.add(v);
                        }
                      } // next value
                    }
                    // defer merging values until after we're done with all columns...
                    
                  } // possibly multiple values      
                } // layer mapping specified
              } // next column
              
              // merge accumulated values of multi-value attributes
              for (String layerId : layerToValues.keySet()) {
                HashSet<String> newValues = layerToValues.get(layerId);
                
                HashMap<String,Annotation> currentAnnotations
                  = new HashMap<String,Annotation>();
                for (Annotation annotation : transcript.getAnnotations(layerId)) {
                  currentAnnotations.put(annotation.getLabel(), annotation);
                } // next annotation
                
                // add values that aren't already present
                HashSet<String> valuesToAdd = new HashSet<String>(newValues);
                valuesToAdd.removeAll(currentAnnotations.keySet());
                for (String l : valuesToAdd) {
                  transcript.createTag(transcript, layerId, l);
                } // next value
                
                // delete values are aren't specified
                HashSet<String> valuesToRemove = new HashSet<String>(
                  currentAnnotations.keySet());
                valuesToRemove.removeAll(newValues);
                for (String l : valuesToRemove) {
                  currentAnnotations.get(l).destroy();
                } // next value
              } // next multi-value layer
              
              // save
              store.saveTranscript(transcript);
            } catch (GraphNotFoundException notFound) {
              messages.add(localize("Transcript not found: {0}", id));
              missing++;
            }
          } // next record
        } // close parser
        
        JsonObjectBuilder model = Json.createObjectBuilder()
          .add("updated", updated)
          .add("missing", missing);
        messages.add(
          localize(
            "Imported data for {0} {0,choice,1#transcript|1<transcripts} ({1} not found)",
            updated, missing));
        return successResult(model.build(), messages);
      } finally {
        if (csvFile != null) csvFile.delete();
        cacheStore(store);
      }
    } catch(Exception ex) {
      try {
        httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      } catch(Exception exception) {}
      context.servletLog("POST Upload.post: unhandled exception: " + ex);
      ex.printStackTrace(System.err);
      return failureResult(ex);
    }
  }
  
  /**
   * Attempts to standardize the label for a given layer.
   * @param label The proposed label.
   * @param layer The layer the label corresponds to.
   * @return The standardized label, or the raw label if no standardization is appropriate.
   */
  public String standardizeLabel(String label, Layer layer) {
    if (layer != null) {
      if (layer.getValidLabels().size() > 0) {
        if (!layer.getValidLabels().containsKey(label)) { // not a standard label
          // maybe the raw label is a description
          for (String l : layer.getValidLabels().keySet()) {
            if (l.equalsIgnoreCase(label)) { // wrong case?
              // use the correct-case version
              label = l;
              break;
            }
            if (layer.getValidLabels().get(l).equalsIgnoreCase(label)) {
              // description instead of label - use the label
              label = l;
              break;
            }
          }
        }
      } else if (layer.getType().equals("boolean")) {
        if (label.equalsIgnoreCase("true")
            || label.equalsIgnoreCase("t")
            || label.equalsIgnoreCase("yes")) {
          label = "1";
        } else if (label.equalsIgnoreCase("false")
                   || label.equalsIgnoreCase("f")
                   || label.equalsIgnoreCase("no")) {
          label = "0";
        }
      }
    }
    return label;
  } // end of standardizeLabel()

} // end of class Upload
