//
// Copyright 2023 New Zealand Institute of Language, Brain and Behaviour, 
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

import java.io.StringReader;
import java.io.IOException;
import java.util.List;
import java.util.Vector;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import nzilbb.labbcat.server.task.Task;
import nzilbb.labbcat.server.db.SqlGraphStore;
import nzilbb.util.CloneableBean;
import nzilbb.util.ClonedProperty;

/**
 * Base class for search implementations, which return a set of search results.
 */
public abstract class SearchTask extends Task {
  
  /**
   * Matrix that defines this search.
   * @see #getMatrix()
   * @see #setMatrix(Matrix)
   */
  protected Matrix matrix;
  /**
   * Getter for {@link #matrix}: Matrix that defines this search.
   * @return Matrix that defines this search.
   */
  public Matrix getMatrix() { return matrix; }
  /**
   * Setter for {@link #matrix}: Matrix that defines this search.
   * @param newMatrix Matrix that defines this search.
   */
  public SearchTask setMatrix(Matrix newMatrix) { matrix = newMatrix; return this; }
  
  /**
   * Whether to seearch main-participant utterances only or not.
   * @see #getMainParticipantOnly()
   * @see #setMainParticipantOnly(boolean)
   */
  protected boolean mainParticipantOnly;
  /**
   * Getter for {@link #mainParticipantOnly}: Whether to seearch main-participant
   * utterances only or not. 
   * @return Whether to seearch main-participant utterances only or not.
   */
  public boolean getMainParticipantOnly() { return mainParticipantOnly; }
  /**
   * Setter for {@link #mainParticipantOnly}: Whether to seearch main-participant
   * utterances only or not. 
   * @param newMainParticipantOnly Whether to seearch main-participant utterances only or not.
   */
  public SearchTask setMainParticipantOnly(boolean newMainParticipantOnly) { mainParticipantOnly = newMainParticipantOnly; return this; }
  
  /**
   * Whether to suppress the results list (i.e. show only a summary of results)
   * @see #getSuppressResults()
   * @see #setSuppressResults(boolean)
   */
  protected boolean suppressResults;
  /**
   * Getter for {@link #suppressResults}: Whether to suppress the results list (i.e. show
   * only a summary of results) 
   * @return Whether to suppress the results list (i.e. show only a summary of results)
   */
  public boolean getSuppressResults() { return suppressResults; }
  /**
   * Setter for {@link #suppressResults}: Whether to suppress the results list (i.e. show
   * only a summary of results) 
   * @param newSuppressResults Whether to suppress the results list (i.e. show only a
   * summary of results) 
   */
  public SearchTask setSuppressResults(boolean newSuppressResults) { suppressResults = newSuppressResults; return this; }
  
  /**
   * Maximum number of matches per transcript to return, or null for all matches.
   * @see #getMatchesPerTranscript()
   * @see #setMatchesPerTranscript(Integer)
   */
  protected Integer matchesPerTranscript;
  /**
   * Getter for {@link #matchesPerTranscript}: Maximum number of matches per transcript to
   * return, or null for all matches. 
   * @return Maximum number of matches per transcript to return, or null for all matches.
   */
  public Integer getMatchesPerTranscript() { return matchesPerTranscript; }
  /**
   * Setter for {@link #matchesPerTranscript}: Maximum number of matches per transcript to
   * return, or null for all matches. 
   * @param newMatchesPerTranscript Maximum number of matches per transcript to return, or
   * null for all matches. 
   */
  public SearchTask setMatchesPerTranscript(Integer newMatchesPerTranscript) { matchesPerTranscript = newMatchesPerTranscript; return this; }

  /**
   * Maximum total number of matches to return, 0 meaning 'no limit'.
   * @see #getMaxMatches()
   * @see #setMaxMatches(int)
   */
  protected int maxMatches = 0;
  /**
   * Getter for {@link #maxMatches}: Maximum total number of matches to return.
   * @return Maximum total number of matches to return, 0 meaning 'no limit'.
   */
  public int getMaxMatches() { return maxMatches; }
  /**
   * Setter for {@link #maxMatches}: Maximum total number of matches to return.
   * @param newMaxMatches Maximum total number of matches to return, 0 meaning 'no limit'.
   */
  public SearchTask setMaxMatches(int newMaxMatches) { maxMatches = newMaxMatches; return this; }
  
  /**
   * Percentage overlap with other utterances before simultaeous speech is excluded, or
   * null to include all simultaneous speech. 
   * @see #getOverlapThreshold()
   * @see #setOverlapThreshold(Integer)
   */
  protected Integer overlapThreshold;
  /**
   * Getter for {@link #overlapThreshold}: Percentage overlap with other utterances before
   * simultaeous speech is excluded, or null to include all simultaneous speech. 
   * @return Percentage overlap with other utterances before simultaeous speech is
   * excluded, or null to include all simultaneous speech. 
   */
  public Integer getOverlapThreshold() { return overlapThreshold; }
  /**
   * Setter for {@link #overlapThreshold}: Percentage overlap with other utterances before
   * simultaeous speech is excluded, or null to include all simultaneous speech. 
   * @param newOverlapThreshold Percentage overlap with other utterances before
   * simultaeous speech is excluded, or null to include all simultaneous speech. 
   */
  public SearchTask setOverlapThreshold(Integer newOverlapThreshold) { overlapThreshold = newOverlapThreshold; return this; }

  /**
   * If non-null, then only targets whose anchors have an alignment status greater than or
   * equals to this value will be returned. 
   * @see #getAnchorConfidenceThreshold()
   * @see #setAnchorConfidenceThreshold(Byte)
   */
  protected Byte anchorConfidenceThreshold;
  /**
   * Getter for {@link #anchorConfidenceThreshold}: If non-null, then only targets whose
   * anchors have an alignment status greater than or equals to this value will be
   * returned. 
   * @return If non-null, then only targets whose anchors have an alignment status greate
   * than or equals to this value will be returned. 
   */
  public Byte getAnchorConfidenceThreshold() { return anchorConfidenceThreshold; }
  /**
   * Setter for {@link #anchorConfidenceThreshold}: If non-null, then only targets whose
   * anchors have an alignment status greater than or equals to this value will be
   * returned. 
   * @param newAnchorConfidenceThreshold If non-null, then only targets whose anchors have
   * an alignment status greater than or equals to this value will be returned. 
   */
  public SearchTask setAnchorConfidenceThreshold(Byte newAnchorConfidenceThreshold) { anchorConfidenceThreshold = newAnchorConfidenceThreshold; return this; }

  /**
   * The user ID to use to restrict access to search results, if any (e.g. admin users
   * should not have this set). 
   * @see #getRestrictByUser()
   * @see #setRestrictByUser(String)
   */
  protected String restrictByUser;
  /**
   * Getter for {@link #restrictByUser}: The user ID to use to restrict access to search
   * results, if any (e.g. admin users should not have this set). 
   * @return The user ID to use to restrict access to search results, if any (e.g. admin
   * users should not have this set). 
   */
  public String getRestrictByUser() { return restrictByUser; }
  /**
   * Setter for {@link #restrictByUser}: The user ID to use to restrict access to search
   * results, if any (e.g. admin users should not have this set). 
   * @param newRestrictByUser The user ID to use to restrict access to search results, if
   * any (e.g. admin users should not have this set). 
   */
  public SearchTask setRestrictByUser(String newRestrictByUser) { restrictByUser = newRestrictByUser; return this; }
  
  /**
   * A short, more or less human readable description of the search.
   * @see #getDescription()
   * @see #setDescription(String)
   */
  protected String description = "";
  /**
   * Getter for {@link #description}: A short, more or less human readable description of
   * the search. 
   * @return A short, more or less human readable description of the search.
   */
  public String getDescription() { return description; }
  /**
   * Setter for {@link #description}: A short, more or less human readable description of
   * the search. 
   * @param newDescription A short, more or less human readable description of the search.
   */
  public SearchTask setDescription(String newDescription) { description = newDescription; return this; }
  
  /**
   * The results of the search.
   * @see #getResults()
   * @see #setResults(SearchResults)
   */
  protected SearchResults results;
  /**
   * Getter for {@link #results}: The results of the search.
   * @return The results of the search.
   */
  public SearchResults getResults() { return results; }
  /**
   * Setter for {@link #results}: The results of the search.
   * @param newResults The results of the search.
   */
  public SearchTask setResults(SearchResults newResults) { results = newResults; return this; }

  /**
   * Validates the search conditions.
   * @return A message describing a validation error, or null if the conditions are valid
   */
  public String validate() {
    if (matrix == null || matrix.getColumns() == null || matrix.getColumns().size() == 0) {
      return "Search matrix was not specified"; // TODO i18n
    }
    if (!matrix.layerMatchStream()
        .filter(LayerMatch::HasCondition)
        .findAny().isPresent()) {
      return "No search text was specified"; // TODO i18n
    }
    return null;
  } // end of validate()
  
  /**
   * Implementors should <b>not</b> override this - it calls {@link #search()}
   * but also performs various housekeeping operations.
   */
  public void run() {
    try {
      runStart();
      
      String validationError = validate();
      if (validationError != null) {
        setStatus(validationError);
      } else {

        search();
        
        if (results == null) {
          setStatus("No results available.");
        } else {
          if (!suppressResults && results.size() > 0) {
            setResultUrl(getStore().getId() + "matches?threadId=" + getId() + "&wordsContext=1");
            setResultText("Display results");
          }
          
          setStatus("Found " + results.size() + " result" + (results.size() != 1?"s":""));
        }
        iPercentComplete = 100;
      }
    } catch (Exception ex) {
      setLastException(ex);
      if (ex.getClass().getName().equals("java.lang.Exception")) { // generic error
        setStatus(ex.getMessage());
      } else {
        setStatus("run(): " + ex.getClass().getName() + " - " + ex.getMessage());
      }
    } finally {
      if (results != null) try { results.close(); } catch(IOException exception) {}
      runEnd();
    }
    
    if (bCancelling) {
      setStatus(getStatus() + " - cancelled.");
    }

    waitToDie();
    
    release(); // ensure any resources are released
  } // run

  /**
   * Method for implementors to implement - performs the search and
   * sets the results.
   * @throws Exception
   */
  protected abstract void search() throws Exception;
  
}
