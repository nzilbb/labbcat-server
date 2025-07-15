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
package nzilbb.labbcat.server.search;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import nzilbb.sql.ConnectionFactory;
import nzilbb.util.IO;
import nzilbb.labbcat.server.db.IdMatch;
import org.apache.commons.csv.*;

/**
 * Search results constructed from a CSV file.
 * @author Robert Fromont robert@fromont.net.nz
 */
public class CsvResults implements SearchResults {

  int recordCount = 0;
  CSVParser parser;
  
  // for parsing MatchIds
  Pattern matchIdPattern;
  Integer ag_id_group;
  Integer transcript_id_group;
  Integer utterance_annotation_id_group;
  Integer start_anchor_id_group;
  Integer end_anchor_id_group;
  Integer participant_speaker_number_group;
  Integer target_scope_group;
  Integer target_layer_id_group;
  Integer target_annotation_id_group;
  Integer first_word_annotation_id_group;

  /**
   * The results file to parse.
   * @see #getCsvFile()
   */
  protected File csvFile;
  /**
   * Getter for {@link #csvFile}: The results file to parse.
   * @return The results file to parse.
   */
  public File getCsvFile() { return csvFile; }
  
  /**
   * Columns available in csv source file, if any.
   * @see #getCsvColumns()
   * @see #setCsvColumns(List<String>)
   */
  protected List<String> csvColumns;
  /**
   * Getter for {@link #csvColumns}: Columns available in csv source file, if any.
   * @return Columns available in csv source file, if any.
   */
  public List<String> getCsvColumns() { return csvColumns; }
  
  /**
   * Field delimiter to use when reading the file.
   * @see #getCsvFieldDelimiter()
   * @see #setCsvFieldDelimiter(char)
   */
  protected char csvFieldDelimiter = ',';
  /**
   * Getter for {@link #csvFieldDelimiter}: Field delimiter to use when reading the file.
   * @return Field delimiter to use when reading the file.
   */
  public char getCsvFieldDelimiter() { return csvFieldDelimiter; }
  /**
   * Setter for {@link #csvFieldDelimiter}: Field delimiter to use when reading the file.
   * @param newCsvFieldDelimiter Field delimiter to use when reading the file.
   */
  public CsvResults setCsvFieldDelimiter(char newCsvFieldDelimiter) { csvFieldDelimiter = newCsvFieldDelimiter; return this; }
  
  /**
   * Name of the column that identifies each match (default "MatchId").
   * @see #getTargetColumn()
   * @see #setTargetColumn(String)
   */
  protected String targetColumn;
  /**
   * Getter for {@link #targetColumn}: Name of the column that
   * identifies each match (default "MatchId"). 
   * @return Name of the column that identifies each match (default "MatchId").
   */
  public String getTargetColumn() { return targetColumn; }
  /**
   * Setter for {@link #targetColumn}: Name of the column that
   * identifies each match (default "MatchId"). 
   * @param newTargetColumn Name of the column that identifies each
   * match (default "MatchId"). 
   */
  public CsvResults setTargetColumn(String newTargetColumn) { targetColumn = newTargetColumn; return this; }

  
  /**
   * Name of result set.
   * @see #getName()
   * @see #setName(String)
   */
  protected String name;
  /**
   * SearchResults method: A descriptive name for the collection.
   * @return A descriptive name for the collection.
   */
  public String getName() { return name; }
  /**
   * Setter for {@link #name}: Name of result set.
   * @param newName Name of result set.
   */
  public CsvResults setName(String newName) {
    name = newName;
    return this;
  }
  
  /**
   * The maximum number of results to return from {@link #next()}, or 0 for no maximum.
   * @see #getPageLength()
   * @see #setPageLength(int)
   */
  protected int pageLength = 0;
  /**
   * Getter for {@link #pageLength}: The maximum number of results to return from {@link #next()},
   * or 0 for no maximum. 
   * @return The maximum number of results to return from {@link #next()}, or 0 for no maximum.
   */
  public int getPageLength() { return pageLength; }
  /**
   * Setter for {@link #pageLength}: The maximum number of results to return from {@link #next()},
   * or 0 for no maximum. 
   * @param newPageLength The maximum number of results to return from {@link #next()}, or 0 for
   * no maximum. 
   */
  public SearchResults setPageLength(int newPageLength) { pageLength = newPageLength; return this; }
  
  /**
   * The ID of the last match the iterator returned from {@link #next()}
   * @see #getLastMatchId()
   */
  protected String lastMatchId;
  /**
   * Getter for {@link #lastMatchId}: The ID of the last match the iterator returned from
   * {@link #next()}
   * @return The ID of the last match the iterator returned from {@link #next()}
   */
  public String getLastMatchId() { return lastMatchId; }
  
  /**
   * The last CSV record parsed, if any.
   * @see #getLastRecord()
   */
  protected CSVRecord lastRecord;
  /**
   * Getter for {@link #lastRecord}: The last CSV record parsed, if any.
   * @return The last CSV record parsed, if any.
   */
  public CSVRecord getLastRecord() { return lastRecord; }
  
  /**
   * Database connection.
   * @see #getConnection()
   * @see #setConnection(Connection)
   */
  protected Connection connection;
  /**
   * Getter for {@link #connection}: Database connection.
   * @return Database connection.
   * @throws SQLException If a new connection is required, but can't be created.
   */
  public Connection getConnection() throws SQLException {
    if (connection == null && db != null) {
      connection = db.newConnection();
    }
    return connection;
  }
  /**
   * Setter for {@link #connection}: Database connection.
   * @param newConnection Database connection.
   */
  public CsvResults setConnection(Connection newConnection) {
    connection = newConnection; return this;
  }
   
  /**
   * Factory for generating connections to the database.
   * @see #getDb()
   * @see #setDb(ConnectionFactory)
   */
  protected ConnectionFactory db;
  /**
   * Getter for {@link #db}: Factory for generating connections to the database.
   * @return Factory for generating connections to the database.
   */
  public ConnectionFactory getDb() { return db; }
  /**
   * Setter for {@link #db}: Factory for generating connections to the database.
   * @param newDb Factory for generating connections to the database.
   */
  public CsvResults setDb(ConnectionFactory newDb) {
    db = newDb;
    return this;
  }
  
  /**
   * Constructor from CSV File.
   * @param csvFile The results file to parse.
   */
  public CsvResults(File csvFile, ConnectionFactory db) throws IOException {
    this.csvFile = csvFile;
    setDb(db);
    setName(IO.WithoutExtension(csvFile).replaceAll("^results_",""));
    if (name.length() == 0) setName("csv_results");
    // infer the field delimiter from the header line
    try (BufferedReader r =  new BufferedReader(new FileReader(csvFile))) {
      String line = r.readLine();
      while (line != null && line.trim().length() == 0) {
        line = r.readLine();
      } // next blank line
      if (line != null) {
        if (line.contains("\t")) csvFieldDelimiter = '\t';
        else if (line.contains(";")) csvFieldDelimiter = ';';
        else if (line.contains(",")) csvFieldDelimiter = ',';
      }
      // count the remaining lines, which is (probably) the number of records
      recordCount = 0;
      while (r.readLine() != null) recordCount++;
    } // reader
  } // end of constructor

  /**
   * Copy constructor.
   * @param other The other results object.
   */
  public CsvResults(CsvResults other) throws IOException {
    this.csvFile = other.csvFile;
    this.csvColumns = other.csvColumns;
    this.recordCount = other.recordCount;
    this.name = other.name;
    this.csvFieldDelimiter = other.csvFieldDelimiter;
    this.targetColumn = other.targetColumn;
    this.pageLength = other.pageLength;
    this.setDb(other.getDb());
  } // end of constructor
  
  /**
   * SearchResults method: Resets the iterator to the beginning of the list
   */
  public void reset() {
    try {
      CSVFormat format = CSVFormat.EXCEL
        .withDelimiter(csvFieldDelimiter)
        .withHeader();
      parser = new CSVParser(new FileReader(csvFile), format);
      csvColumns = parser.getHeaderNames();
    
      // determine the result ID pattern from the first record
      CSVRecord firstRow = parser.iterator().next();
      String targetId = firstRow.get(targetColumn);
      matchIdPattern = Pattern.compile(
        "g_(\\d+);em_12_(\\d+);n_(\\d+)-n_(\\d+);p_(\\d+);#=e(.?)_(\\d+)_(\\d+);.*\\[0\\]=ew_0_(\\d+)($|;.*)");
      Matcher idMatcher = matchIdPattern.matcher(targetId);
      ag_id_group = 1;
      transcript_id_group = null;
      utterance_annotation_id_group = 2;
      start_anchor_id_group = 3;
      end_anchor_id_group = 4;
      participant_speaker_number_group = 5;
      target_scope_group = 6;
      target_layer_id_group = 7;
      target_annotation_id_group = 8;
      first_word_annotation_id_group = 9;
      if (idMatcher.matches()) { // the column contains MatchIds
        // so we don't actually need the pattern to parse IDs
        matchIdPattern = null;
      } else {
        // not IdMatch - maybe they've passed in the URL
        // something like:
        // http://localhost:8080/labbcat/transcript?ag_id=6#ew_0_16783
        matchIdPattern = Pattern.compile(
          "https?://.+/transcript\\?ag_id=(\\d+)#e(.?)_(\\d+)_(\\d+)");
        idMatcher = matchIdPattern.matcher(targetId);
        if (idMatcher.matches()) { // URL pattern matches
          ag_id_group = 1;
          transcript_id_group = null;
          utterance_annotation_id_group = null;
          start_anchor_id_group = null;
          end_anchor_id_group = null;
          participant_speaker_number_group = null;
          target_scope_group = 2;
          target_layer_id_group = 3;
          target_annotation_id_group = 4;
          first_word_annotation_id_group = 4;
        } else {
          // URL something like:
          // http://localhost:8080/labbcat/transcript?transcript=foo.trs#ew_0_16783
          matchIdPattern = Pattern.compile(
            "https?://.+/transcript\\?transcript=(.+)#e(.?)_(\\d+)_(\\d+)");
          idMatcher = matchIdPattern.matcher(targetId);
          if (idMatcher.matches()) { // URL pattern matches
            ag_id_group = null;
            transcript_id_group = 1;
            utterance_annotation_id_group = null;
            start_anchor_id_group = null;
            end_anchor_id_group = null;
            participant_speaker_number_group = null;
            target_scope_group = 2;
            target_layer_id_group = 3;
            target_annotation_id_group = 4;
            first_word_annotation_id_group = 4;
          } else {
            // not IdMatch or URL - maybe they've passed in annotation UIDs
            // something like:
            // "em_12_2671" or "ew_0_16783"
            matchIdPattern = Pattern.compile("e(.?)_(\\d+)_(\\d+)");
            idMatcher = matchIdPattern.matcher(targetId);
            if (idMatcher.matches()) { // UID pattern matches
              ag_id_group = null;
              transcript_id_group = null;
              utterance_annotation_id_group = null;
              start_anchor_id_group = null;
              end_anchor_id_group = null;
              participant_speaker_number_group = null;
              target_scope_group = 1;
              target_layer_id_group = 2;
              target_annotation_id_group = 3;
              first_word_annotation_id_group = 3; // might not be word
            } else {
              System.err.println("CsvResults.reset: Malformed ID: " + targetId);
              // fall back to no parsing
              matchIdPattern = null;
            } // not a UID
          } // not a transcript URL
        } // not an ag_id URL
      } // not a MatchId
      parser.close();
    
      // now re-open the parser
      parser = new CSVParser(new FileReader(csvFile), format);
      nextCount = 0;
      lastRecord = null;
      
    } catch(IOException exception) {
      System.err.println("CsvResults.reset: " + exception);
    }
  }
  /**
   * Ensures that the parser is in a valid state for iterating.
   */
  private void checkParser() {
    if (parser == null) reset();
  }
  
  /**
   * SearchResults method: Returns the number of utterances in the collection.
   * @return The number of utterances in the collection.
   */
  public int size() {
    return recordCount;
  }

  /**
   * Go to the nth item in the list, so it will be the next returned by {@link #next()}.
   * @param n The number of the item to seek to.
   * @return true if the nth item exists, false otherwise.
   */
  public boolean seek(int n) {
    checkParser();
    if (parser.getRecordNumber() - 1 > n) {
      reset(); // start again from the beginning
    }
    // seek forward until the record number is the one we want
    long iterationsLeft = (n-1) - parser.getRecordNumber();
    while (--iterationsLeft >= 0 && parser.iterator().hasNext()){
      parser.iterator().next();
    }
    return hasNext();
  }

  /**
   * Iterator method: Returns true if the iteration has more elements.
   * @return true if the iteration has more elements.
   */
  public boolean hasNext() {
    checkParser();
    return parser.iterator().hasNext() && (pageLength <= 0 || nextCount < pageLength);
  } // end of hasNext()

  int nextCount = 0;
  HashMap<String,Integer> transcriptIdToAgId = new HashMap<String,Integer>();
  PreparedStatement sqlTranscriptIdToAgId;
  PreparedStatement sqlWordIdToSpeakerNumber;
  PreparedStatement sqlWordIdToUtteranceId;
  NumberFormat resultNumberFormatter;
  /**
   * Iterator method: Returns the next result ID.
   * @return The next result ID.
   */
  public String next() {
    checkParser();
    lastRecord = parser.iterator().next();
    nextCount++;
    lastMatchId = lastRecord.get(targetColumn);
    // parse the ID into a MatchId if necessary
    if (matchIdPattern != null) {
      Matcher idMatcher = matchIdPattern.matcher(lastMatchId);
      if (idMatcher.matches()) {
        IdMatch match = new IdMatch();
        if (resultNumberFormatter == null) {
          resultNumberFormatter = NumberFormat.getInstance();
          resultNumberFormatter.setGroupingUsed(false);
          resultNumberFormatter.setMinimumIntegerDigits((int)(Math.log10(recordCount)) + 1);
        }
        match.getAttributes().put(
          "prefix", resultNumberFormatter.format(parser.getRecordNumber()) + "-");
        if (ag_id_group != null) {
          match.setGraphId(Integer.valueOf(idMatcher.group(ag_id_group)));
        }
        if (transcript_id_group != null) {
          match.setTranscriptId(idMatcher.group(transcript_id_group));
          if (match.getGraphId() == null) {
            if (!transcriptIdToAgId.containsKey(match.getTranscriptId())) {
              // look up the ag_id in the database
              try {
                if (sqlTranscriptIdToAgId == null) {
                  sqlTranscriptIdToAgId = getConnection().prepareStatement(
                    "SELECT ag_id FROM transcript WHERE transcript_id = ?");
                }
                sqlTranscriptIdToAgId.setString(1, match.getTranscriptId());
                ResultSet rs = sqlTranscriptIdToAgId.executeQuery();
                if (rs.next()) {
                  transcriptIdToAgId.put(match.getTranscriptId(), rs.getInt(1));
                }
              } catch(SQLException exception) {
                System.err.println("CsvResults.next: " + exception);
              }
            } // database ag_id lookup
            // lookup transcript ID from our cache
            match.setGraphId(transcriptIdToAgId.get(match.getTranscriptId()));
          }
        }
        if (utterance_annotation_id_group != null) {
          match.setDefiningAnnotationUid(
            "em_12_"+idMatcher.group(utterance_annotation_id_group));
        }
        if (start_anchor_id_group != null) {
          match.setStartAnchorId(
            Long.valueOf(idMatcher.group(start_anchor_id_group)));
        }
        if (end_anchor_id_group != null) {
          match.setEndAnchorId(
            Long.valueOf(idMatcher.group(end_anchor_id_group)));
        }
        if (participant_speaker_number_group != null) {
          match.setSpeakerNumber(
            Integer.valueOf(idMatcher.group(participant_speaker_number_group)));
        }
        if (target_scope_group != null
            && target_layer_id_group != null
            && target_annotation_id_group != null) {
          match.setTargetAnnotationUid(
            "e"+idMatcher.group(target_scope_group)
            +"_"+idMatcher.group(target_layer_id_group)
            +"_"+idMatcher.group(target_annotation_id_group));
        }
        if (first_word_annotation_id_group != null) {
          
          match.getMatchAnnotationUids().put(
            "0", "ew_0_"+idMatcher.group(first_word_annotation_id_group));
          
          if (match.getSpeakerNumber() == null) {
            // lookup speaker_number in database
            try {
              if (sqlWordIdToSpeakerNumber == null) {
                sqlWordIdToSpeakerNumber = getConnection().prepareStatement(
                  "SELECT turn.label FROM annotation_layer_11 turn"
                  +" INNER JOIN annotation_layer_0 word"
                  +" ON word.turn_annotation_id = turn.annotation_id"
                  +" WHERE word.annotation_id = ?");
              }                
              sqlWordIdToSpeakerNumber.setString(
                1, idMatcher.group(first_word_annotation_id_group));
              ResultSet rs = sqlWordIdToSpeakerNumber.executeQuery();
              if (rs.next()) {
                match.setSpeakerNumber(rs.getInt(1));
              }
            } catch(SQLException exception) {
              System.err.println("CsvResults.next: " + exception);
            }
          }
          
          if (match.getDefiningAnnotationUid() == null) {
            // lookup utterance ID (and anchors) in database
            try {
              if (sqlWordIdToUtteranceId == null) {
                sqlWordIdToUtteranceId = getConnection().prepareStatement(
                  "SELECT utterance.annotation_id,"
                  +" utterance.start_anchor_id, utterance.end_anchor_id"
                  +" FROM annotation_layer_12 utterance"
                  +" INNER JOIN annotation_layer_0 word"
                  +" ON word.utterance_annotation_id = utterance.annotation_id"
                  +" WHERE word.annotation_id = ?");
              }                
              sqlWordIdToUtteranceId.setString(
                1, idMatcher.group(first_word_annotation_id_group));
              ResultSet rs = sqlWordIdToUtteranceId.executeQuery();
              if (rs.next()) {
                match.setDefiningAnnotationUid("em_12_"+rs.getInt(1));
                if (match.getStartAnchorId() == null) match.setStartAnchorId(rs.getLong(2));
                if (match.getEndAnchorId() == null) match.setEndAnchorId(rs.getLong(3));
              }
            } catch(SQLException exception) {
              System.err.println("CsvResults.next: " + exception);
            }
          }
          
        }
        lastMatchId = match.getId();
      }
    }
    return lastMatchId;
  } // end of next()
  
  /**
   * Close all open resources (there are none).
   */
  public void close() {
    if (parser != null) {
      try { parser.close(); } catch(IOException exception) {}
      parser = null;
    }
    if (sqlTranscriptIdToAgId != null) {
      try { sqlTranscriptIdToAgId.close(); } catch(Exception exception) {}
      sqlTranscriptIdToAgId = null;
    }
    if (sqlWordIdToSpeakerNumber != null) {
      try { sqlWordIdToSpeakerNumber.close(); } catch(Exception exception) {}
      sqlWordIdToSpeakerNumber = null;
    }
    if (sqlWordIdToUtteranceId != null) {
      try { sqlWordIdToUtteranceId.close(); } catch(Exception exception) {}
      sqlWordIdToSpeakerNumber = null;
    }
    if (connection != null) {
      try { connection.close(); } catch(Exception exception) {}
      connection = null;
    }
  } // end of close()
} // end of class CsvResults
