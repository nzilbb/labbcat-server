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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import nzilbb.labbcat.server.db.SqlSearchResults;
import nzilbb.labbcat.server.search.SearchTask;
import nzilbb.util.IO;
import org.apache.commons.csv.*;

/**
 * Task that parses a given search-results CSV file, loading the matches found into
 * the results database table.
 * @author Robert Fromont robert@fromont.net.nz
 */
public class ParseResultsFile extends SearchTask {
  
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
  public ParseResultsFile setCsvFieldDelimiter(char newCsvFieldDelimiter) { csvFieldDelimiter = newCsvFieldDelimiter; return this; }
  
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
  public ParseResultsFile setTargetColumn(String newTargetColumn) { targetColumn = newTargetColumn; return this; }

  /**
   * Constructor.
   * @param csvFile The results file to parse.
   */
  public ParseResultsFile(File csvFile) {
    this.csvFile = csvFile;
  }

  /**
   * Pass validation, this is not a real search.
   * @return null.
   */
  @Override public String validate() {
    return null;
  } // end of validate()

  /**
   * Parses the CSV file, and loads the matches found into the results table.
   * @throws Exception
   */
  protected void search() throws Exception {
    
    iPercentComplete = 1;
    Connection connection = getStore().getConnection();
    setDescription(IO.WithoutExtension(csvFile));
    setStatus("Parsing " + csvFile.getName());
    
    // count rows so we can report progress
    long resultCount = -1; // dis-count header row
    try (BufferedReader r =  new BufferedReader(new FileReader(csvFile))) {
      while (r.readLine() != null) resultCount++;
    } // reader
    
    // create results
    results = new SqlSearchResults(this);
    
    CSVFormat format = CSVFormat.EXCEL
      .withDelimiter(csvFieldDelimiter)
      .withHeader();

    try(
      CSVParser parser = new CSVParser(new FileReader(csvFile), format);
      PreparedStatement sql = connection.prepareStatement(
        "INSERT INTO result"
        +" (search_id,ag_id,speaker_number,"
        +" start_anchor_id,end_anchor_id,defining_annotation_id,"
        +" segment_annotation_id,target_annotation_id,first_matched_word_annotation_id,"
        +" last_matched_word_annotation_id,complete,target_annotation_uid)"
        +" VALUES (?,?,?,?,?,?,?,?,?,?,0,?)")
      ) {
      
      ((SqlSearchResults)results).setCsvFile(csvFile);
      ((SqlSearchResults)results).setCsvColumns(parser.getHeaderNames());
      
      sql.setLong(1, ((SqlSearchResults)results).getId());
      
      Iterator<CSVRecord> records = parser.iterator();
      Pattern matchIdPattern = Pattern.compile(
        "g_(\\d+);em_12_(\\d+);n_(\\d+)-n_(\\d+);p_(\\d+);#=e(.?)_(\\d+)_(\\d+);.*\\[0\\]=ew_0_(\\d+)($|;.*)");
      Integer ag_id_group = 1;
      Integer utterance_annotation_id_group = 2;
      Integer start_anchor_id_group = 3;
      Integer end_anchor_id_group = 4;
      Integer participant_speaker_number_group = 5;
      Integer target_scope_group = 6;
      Integer target_layer_id_group = 7;
      Integer target_annotation_id_group = 8;
      Integer first_word_annotation_id_group = 9;
      long r = 0;
      while (records.hasNext() && !bCancelling) {
        CSVRecord row = records.next();
        String targetId = row.get(targetColumn);
        Matcher idMatcher = matchIdPattern.matcher(targetId);
        // TODO include URL options from SqlGraphStore#getMatchAnnotations
        if (idMatcher.matches()) {
          sql.setString(2, idMatcher.group(ag_id_group));
          sql.setString(3, idMatcher.group(participant_speaker_number_group));
          sql.setString(4, idMatcher.group(start_anchor_id_group));
          sql.setString(5, idMatcher.group(end_anchor_id_group));
          sql.setString(6, idMatcher.group(utterance_annotation_id_group));
          if ("1".equals(idMatcher.group(target_layer_id_group))) { // segment target
            sql.setString(7, idMatcher.group(target_annotation_id_group)); //segment
          } else { // not a segment target
            sql.setNull(7, java.sql.Types.DOUBLE); // segment
          }
          sql.setString(8, idMatcher.group(target_annotation_id_group));
          sql.setString(9, idMatcher.group(first_word_annotation_id_group));
          sql.setString(10, idMatcher.group(first_word_annotation_id_group)); // last
          sql.setString(
            11, "e"+idMatcher.group(target_scope_group)
            +"_"+idMatcher.group(target_layer_id_group)+"_"
            +idMatcher.group(target_annotation_id_group));
          sql.executeUpdate();
          iPercentComplete = (int)((++r*100)/resultCount);
        }
      } // next match
      setStatus("Finished " + csvFile.getName());

    } catch (Exception x) {
      if (bCancelling) {
        setStatus("Cancelled.");
      } else {
        throw x;
      }
    }

    // force it to recheck the database to get size etc.
    results.reset();    
    results.hasNext();
    
    iPercentComplete = 100;    
  }

  /**
   * Release resources.
   */
  @Override public void release() {
    if (csvFile != null) csvFile.delete();
    super.release();
  }
  
}
