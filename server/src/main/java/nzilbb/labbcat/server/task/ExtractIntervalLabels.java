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

package nzilbb.labbcat.server.task;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Vector;
import nzilbb.ag.Anchor;
import nzilbb.ag.Annotation;
import nzilbb.ag.Graph;
import nzilbb.ag.Layer;
import nzilbb.ag.Schema;
import nzilbb.labbcat.server.db.SqlConstants;
import nzilbb.labbcat.server.db.SqlGraphStore;
import nzilbb.util.IO;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

/**
 * Concatenates annotation labels for given labels during given time intervals.
 * @author Robert Fromont robert@fromont.net.nz
 */
public class ExtractIntervalLabels extends Task {

  /**
   * CSV data file, which will be deleted after processing.
   * @see #getDataFile()
   * @see #setDataFile(File)
   */
  protected File dataFile;
  /**
   * Getter for {@link #dataFile}: CSV data file
   * @return CSV data file
   */
  public File getDataFile() { return dataFile; }
  /**
   * Setter for {@link #dataFile}: CSV data file, which will be deleted after processing.
   * @param fNewDataFile CSV data file, which will be deleted after processing.
   */
  public ExtractIntervalLabels setDataFile(File fNewDataFile) { dataFile = fNewDataFile; return this; }
   
  /**
   * The field delimiter character for the CSV file.
   * @see #getFieldDelimiter()
   * @see #setFieldDelimiter(char)
   */
  protected char fieldDelimiter = ',';
  /**
   * Getter for {@link #fieldDelimiter}: The field delimiter character for the CSV file.
   * @return The field delimiter character for the CSV file.
   */
  public char getFieldDelimiter() { return fieldDelimiter; }
  /**
   * Setter for {@link #fieldDelimiter}: The field delimiter character for the CSV file.
   * @param cNewFieldDelimiter The field delimiter character for the CSV file.
   */
  public ExtractIntervalLabels setFieldDelimiter(char cNewFieldDelimiter) { fieldDelimiter = cNewFieldDelimiter; return this; }
      
  /**
   * Colum that contains the transcript_id
   * @see #getTranscriptIdColumn()
   * @see #setTranscriptIdColumn(int)
   */
  protected int transcriptIdColumn;
  /**
   * Getter for {@link #transcriptIdColumn}: Colum that contains the transcript_id
   * @return Colum that contains the transcript_id
   */
  public int getTranscriptIdColumn() { return transcriptIdColumn; }
  /**
   * Setter for {@link #transcriptIdColumn}: Colum that contains the transcript_id
   * @param iNewTranscriptIdColumn Colum that contains the transcript_id
   */
  public ExtractIntervalLabels setTranscriptIdColumn(int iNewTranscriptIdColumn) { transcriptIdColumn = iNewTranscriptIdColumn; return this; }

  /**
   * Column that identifies the time, or start time if an end column is specified, at
   * which the formant should be evaluated. 
   * @see #getStartTimeColumn()
   * @see #setStartTimeColumn(int)
   */
  protected int startTimeColumn;
  /**
   * Getter for {@link #startTimeColumn}: Column that identifies the time, or start time if an
   * end column is specified, at which the formant should be evaluated. 
   * @return Column that identifies the time, or start time if an end column is specified,
   * at which the formant should be evaluated. 
   */
  public int getStartTimeColumn() { return startTimeColumn; }
  /**
   * Setter for {@link #startTimeColumn}: Column that identifies the time, or start time if a
   * {@link #markEndColumn} is specified, at which the formant should be evaluated. 
   * @param iNewStartTimeColumn Column that identifies the time, or start time if an end column
   * is specified, at which the formant should be evaluated. 
   */
  public ExtractIntervalLabels setStartTimeColumn(int iNewStartTimeColumn) { startTimeColumn = iNewStartTimeColumn; return this; }

  /**
   * Column that identifies the end time of the span for which formants should be
   * evaluated. null if MarkColumn is the target time, or non-null if MarkColumn and
   * EndTimeColumn should be used to define a mid-point at which the formants should be
   * extracted. 
   * @see #getEndTimeColumn()
   * @see #setEndTimeColumn(Integer)
   */
  protected Integer endTimeColumn;
  /**
   * Getter for {@link #endTimeColumn}: Column that identifies the end time of the span
   * for which formants should be evaluated. null if MarkColumn is the target time, or
   * non-null if {@link #markColumn} and EndTimeColumn should be used to define a
   * mid-point at which the formants should be extracted. 
   * @return Column that identifies the end time of the span for which formants should be
   * evaluated. null if {@link #markColumn} is the target time, or non-null if 
   * {@link #markColumn} and MarkEndColum n should be used to define a mid-point at which the
   * formants should be extracted. 
   */
  public Integer getEndTimeColumn() { return endTimeColumn; }
  /**
   * Setter for {@link #endTimeColumn}: Column that identifies the end time of the span
   * for which formants should be evaluated. null if {@link #markColumn} is the target
   * time, or non-null if {@link #markColumn} and EndTimeColumn should be used to define
   * a mid-point at which the formants should be extracted. 
   * @param iNewEndTimeColumn Column that identifies the end time of the span for which
   * formants should be evaluated. null if {@link #markColumn} is the target time, or
   * non-null if {@link #markColumn} and EndTimeColumn should be used to define a
   * mid-point at which the formants should be extracted. 
   */
  public ExtractIntervalLabels setEndTimeColumn(Integer iNewEndTimeColumn) { endTimeColumn = iNewEndTimeColumn; return this; }
      
  /**
   * Name to use as the basis for an output file name
   * @see #getFileName()
   * @see #setFileName(String)
   */
  protected String fileName = "formants";
  /**
   * Getter for {@link #fileName}: Name to use as the basis for an output file name
   * @return Name to use as the basis for an output file name
   */
  public String getFileName() { return fileName; }
  /**
   * Setter for {@link #fileName}: Name to use as the basis for an output file name
   * @param sNewFileName Name to use as the basis for an output file name
   */
  public ExtractIntervalLabels setFileName(String sNewFileName) { fileName = sNewFileName; return this; }

  /**
   * The index of the column that specifies the name of the speaker,
   * or -1 for no partipant column.
   * @see #getSpeakerNameColumn()
   * @see #setSpeakerNameColumn(int)
   */
  protected int participantNameColumn = -1;
  /**
   * Getter for {@link #participantNameColumn}: The index of the column that specifies the
   * name of the speaker. 
   * @return The index of the column that specifies the name of the speaker.
   */
  public int getParticipantNameColumn() { return participantNameColumn; }
  /**
   * Setter for {@link #participantNameColumn}: The index of the column that specifies the
   * name of the speaker. 
   * @param iNewParticipantNameColumn The index of the column that specifies the name of the speaker.
   */
  public ExtractIntervalLabels setParticipantNameColumn(int iNewParticipantNameColumn) { participantNameColumn = iNewParticipantNameColumn; return this; }

  /**
   * Whether the extracted annotations are allowed to begin before the
   * start time or finish after the end time of the interval. 
   * @see #getPartialContainmentAllowed()
   * @see #setPartialContainmentAllowed(boolean)
   */
  protected boolean partialContainmentAllowed = false;
  /**
   * Getter for {@link #partialContainmentAllowed}: Whether the
   * extracted annotations are allowed to begin before the start time
   * or finish after the end time of the interval. 
   * @return Whether the extracted annotations are allowed to begin
   * before the start time or finish after the end time of the
   * interval. 
   */
  public boolean getPartialContainmentAllowed() { return partialContainmentAllowed; }
  /**
   * Setter for {@link #partialContainmentAllowed}: Whether the
   * extracted annotations are allowed to begin before the start time
   * or finish after the end time of the interval. 
   * @param newPartialContainmentAllowed Whether the extracted
   * annotations are allowed to begin before the start time or finish
   * after the end time of the interval. 
   */
  public ExtractIntervalLabels setPartialContainmentAllowed(boolean newPartialContainmentAllowed) { partialContainmentAllowed = newPartialContainmentAllowed; return this; }
  
  /**
   * Delimiter to use between labels. The default is " ".
   * @see #getLabelDelimiter()
   * @see #setLabelDelimiter(String)
   */
  protected String labelDelimiter = " ";
  /**
   * Getter for {@link #labelDelimiter}: Delimiter to use between labels.
   * @return Delimiter to use between labels. The default is " ".
   */
  public String getLabelDelimiter() { return labelDelimiter; }
  /**
   * Setter for {@link #labelDelimiter}: Delimiter to use between labels.
   * @param newLabelDelimiter Delimiter to use between labels.
   */
  public ExtractIntervalLabels setLabelDelimiter(String newLabelDelimiter) {
    labelDelimiter = Optional.ofNullable(newLabelDelimiter).orElse(" ");
    return this;
  }
  
  /**
   * IDs of layers to extract annotations for.
   * @see #getLayerIds()
   * @see #setLayerIds(List<String>)
   */
  protected List<String> layerIds = new Vector<String>();
  /**
   * Getter for {@link #layerIds}: IDs of layers to extract annotations for.
   * @return IDs of layers to extract annotations for.
   */
  public List<String> getLayerIds() { return layerIds; }
  /**
   * Setter for {@link #layerIds}: IDs of layers to extract annotations for.
   * @param newLayerIds IDs of layers to extract annotations for.
   */
  public ExtractIntervalLabels setLayerIds(List<String> newLayerIds) { layerIds = newLayerIds; return this; }

  /**
   * Whether to pass the original columns through from the input CSV
   * file to the output CSV file. 
   * @see #getCopyColumns()
   * @see #setCopyColumns(boolean)
   */
  protected boolean copyColumns;
  /**
   * Getter for {@link #copyColumns}: Whether to pass the original
   * columns through from the input CSV file to the output CSV file. 
   * @return Whether to pass the original columns through from the
   * input CSV file to the output CSV file. 
   */
  public boolean getCopyColumns() { return copyColumns; }
  /**
   * Setter for {@link #copyColumns}: Whether to pass the original
   * columns through from the input CSV file to the output CSV file. 
   * @param newCopyColumns Whether to pass the original columns
   * through from the input CSV file to the output CSV file. 
   */
  public ExtractIntervalLabels setCopyColumns(boolean newCopyColumns) { copyColumns = newCopyColumns; return this; }

  // Methods:
      
  /**
   * Default constructor.
   */
  public ExtractIntervalLabels() {
  } // end of constructor
   
  /**
   * Generates a descriptive name for the processing.
   * @return A name that hints at the processing being done (e.g. "praat-F1-F2") if possible, 
   * or "praat" if not.
   */
  public String descriptiveName() {
    StringBuilder name = new StringBuilder("intervals");
    for (String layerId: layerIds) name.append("-").append(layerId);
    return name.toString();
  } // end of descriptiveName()

  /**
   * Run the task.
   */
  public void run() {
    CSVParser in = null;
    CSVPrinter out = null;
    File outputFile = null;
    
    String baseUrl = getStore().getBaseUrl();

    SqlGraphStore store = getStore();
    try {
      runStart();

      // count records
      long recordCount = -1; // dis-count header line
      try (BufferedReader r =  new BufferedReader(new FileReader(dataFile))) {
        String line = r.readLine();
        while (line != null) {
            line = r.readLine();
            recordCount++;
        } // next blank line
      } // reader
      

      setStatus("Extracting labels for "+fileName+" ...");
      iPercentComplete = 1; // so the progress bar stops displaying as 'indeterminate'

      outputFile = File.createTempFile(
        IO.WithoutExtension(fileName)
        + "-"+descriptiveName()
        +"-__-", "-__.csv", // ...-__-xxx-__. will have the -__-xxx-__ removed by MediaServlet
        store.getFiles());
      outputFile.deleteOnExit();
      CSVFormat format = CSVFormat.EXCEL.withDelimiter(fieldDelimiter);
      out = new CSVPrinter(new FileWriter(outputFile), format);
      in = new CSVParser(new FileReader(dataFile), format);
      Iterator<CSVRecord> records = in.iterator();
        
      // headers
        
      CSVRecord headers = records.next();
      if (copyColumns) for (String sHeader : headers) out.print(sHeader);

      Connection db = store.getConnection();
      
      // selected layers
      LinkedHashMap<String,PreparedStatement> queries
        = new LinkedHashMap<String,PreparedStatement>();
      HashSet<String> participantBasedLayers = new HashSet<String>();
      Schema schema = store.getSchema();
      for (String layerId : layerIds) {
        Layer layer = schema.getLayer(layerId);
        Integer layer_id = layer == null?null : ((Integer)layer.get("layer_id"));
        if (layer != null && layer_id != null) {
          out.print(layerId);
          out.print(layerId + " start");
          out.print(layerId + " end");
          
          String labelClause = "a.label";
          String labelJoin = "";
          if (layerId.equals(schema.getTurnLayerId())
              || layerId.equals(schema.getUtteranceLayerId())) {
            labelClause = "speaker.name AS label";
            labelJoin = " INNER JOIN speaker ON speaker.speaker_number = a.label";
          }
          String containmentClause =
                              " AND ROUND(start.offset,4) >= ROUND(?,4) AND ROUND(end.offset,4) <= ROUND(?,4)";
          if (partialContainmentAllowed) {
            containmentClause =
              " AND ROUND(end.offset,4) > ROUND(?,4) AND ROUND(start.offset,4) < ROUND(?,4)";
          }
          StringBuilder participantJoin = new StringBuilder();
          if (participantNameColumn >= 0 // there's a participant column
              && !"F".equals(layer.get("scope"))) { // and it's not freeform
            participantJoin
              .append(" INNER JOIN annotation_layer_").append(SqlConstants.LAYER_TURN)
              .append(" turn")
              .append(" ON a.turn_annotation_id = turn.annotation_id AND turn.label = ?");
            participantBasedLayers.add(layerId);
          }
          StringBuilder sql = new StringBuilder();
          sql.append("SELECT ")
            .append(labelClause)
            .append(", start.offset AS start_offset, end.offset AS end_offset")
            .append(" FROM annotation_layer_").append(layer_id).append(" a")
            .append(participantJoin)
            .append(labelJoin)
            .append(" INNER JOIN anchor start ON a.start_anchor_id = start.anchor_id")
            .append(" INNER JOIN anchor end ON a.end_anchor_id = end.anchor_id")
            .append(" WHERE a.ag_id = ?")
            .append(containmentClause)
            .append("  ORDER BY start.offset, end.offset DESC, a.annotation_id DESC");			   
          queries.put(layerId, db.prepareStatement(sql.toString()));
        }
      } // next layer

      try { // now process data rows
        // for each data row...
        while (records.hasNext()) {
          out.println();
          if (bCancelling) break;
          
          CSVRecord record = records.next();
          if (copyColumns) {
            // original fields:
            for (String sValue : record) out.print(sValue);
          }
          
          // get the attributes for this line
          String transcriptId = record.get(transcriptIdColumn);
          String participantName = participantNameColumn>=0?
            record.get(participantNameColumn) : null;
          String sStart = record.get(startTimeColumn);
          String sEnd = record.get(endTimeColumn);
          
          try {
            Graph graph = store.getGraph(transcriptId, null);
            Annotation speaker = participantName != null?
              store.getParticipant(participantName) : null;
            double start = Double.parseDouble(sStart);
            double end = Double.parseDouble(sEnd);
            
            for (String layerId : queries.keySet()) {
              PreparedStatement sql = queries.get(layerId);
              int p = 1;
              if (participantBasedLayers.contains(layerId)) { // id is m_-2_{speaker_number}
                sql.setString(p++, speaker.getId().substring("m_-2_".length()));
              }
              sql.setInt(p++, (Integer)graph.get("@ag_id"));
              sql.setDouble(p++, start);
              sql.setDouble(p++, end);
              ResultSet rs = sql.executeQuery();
              StringBuilder s = new StringBuilder();
              Double startAnnotations = null;
              Double endAnnotations = null;
              boolean firstAnnotation = true;
              while (rs.next()) {
                if (firstAnnotation) firstAnnotation = false;
                else s.append(labelDelimiter);
                
                s.append(rs.getString("label"));
                if (startAnnotations == null) {
                  startAnnotations = rs.getDouble("start_offset");
                } else {
                  startAnnotations = Math.min(
                    startAnnotations, rs.getDouble("start_offset"));
                }
                if (endAnnotations == null) {
                  endAnnotations = rs.getDouble("end_offset");
                } else {
                  endAnnotations = Math.max(
                    endAnnotations, rs.getDouble("end_offset"));
                }
              }
              rs.close();
              out.print(s.toString());
              out.print(startAnnotations == null?"":startAnnotations.toString());
              out.print(endAnnotations == null?"":endAnnotations.toString());
            } // next layer
          } catch (NumberFormatException nX) {
            out.print(""); // label(s)
            out.print(""); // start
            out.print(""); // end
          } catch (Throwable x) {
            out.print("ERROR: "+x.toString());
            out.print(""); // start
            out.print(""); // end
          }
          
          iPercentComplete = (int)(100 * record.getRecordNumber() / recordCount);
        } // next record
        
        // close prepared statements
        for (PreparedStatement sql : queries.values()) sql.close();
      } catch (java.lang.ArrayIndexOutOfBoundsException ex) {
        System.err.println("extractIntervals: " + ex);
      }
      
      setStatus("Finished.");
      
      iPercentComplete = 100;
    } catch (Exception x) {
      setLastException(x);
      if (out != null) {
        try {
          out.println();
          StringWriter sw = new StringWriter();
          PrintWriter pw = new PrintWriter(sw);
          x.printStackTrace(pw);
          out.print(x.getClass().getSimpleName() + ": " + x.getMessage() + "\n"+sw);
          sw.close();
          pw.close();
          out.println();
        } catch(IOException exception) {
          System.err.println("ExtractIntervalLabels: " + x.toString());
        }
      }
    } finally {
      if (out != null) try {out.close();} catch(IOException exception) {}
      if (in != null) try {in.close();} catch(IOException exception) {}
      if (outputFile != null) {
        try {
          setResultUrl(baseUrl +"/" + store.getFiles().getName() + "/"
                       + URLEncoder.encode(outputFile.getName(), "UTF-8"));
        } catch(UnsupportedEncodingException impossible) { 
          setResultUrl(baseUrl +"/" + store.getFiles().getName() + "/"
                       + outputFile.getName());
        }
        setResultText("CSV file with measurements");
      }
      
      runEnd();
    }
    if (bCancelling) {
      setStatus(getStatus() + " - cancelled.");
    }
    waitToDie();
    outputFile.delete();
    dataFile.delete();
  }
  
} // end of class ExtractIntervalLabels
