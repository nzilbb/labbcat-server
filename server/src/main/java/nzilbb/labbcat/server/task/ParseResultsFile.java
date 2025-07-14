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
import nzilbb.labbcat.server.search.CsvSearchResults;
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
  protected char csvFieldDelimiter = '\0';
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
    setDescription(IO.WithoutExtension(csvFile).replaceAll("^results_",""));
    setStatus("Parsing " + csvFile.getName());
    
    // create results
    results = new CsvSearchResults(csvFile)
      .setTargetColumn(targetColumn);
    if (csvFieldDelimiter != '\0') {
      // pass thruogh explicitly specified delimiter instead of inferring from first line
      ((CsvSearchResults)results).setCsvFieldDelimiter(csvFieldDelimiter);
    }
    // load csvColumns etc.
    ((CsvSearchResults)results).reset();
    setStatus("Ready: " + csvFile.getName());
    
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
