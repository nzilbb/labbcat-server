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
package nzilbb.labbcat.server.db;

import java.sql.*;
import java.text.NumberFormat;
import java.util.NoSuchElementException;
import nzilbb.ag.Layer;
import nzilbb.ag.StoreException;
import nzilbb.labbcat.server.db.IdMatch;
import nzilbb.labbcat.server.search.SearchResults;
import nzilbb.labbcat.server.search.SearchTask;

/**
 * An implementation of SearchResults that uses the results tables for match data.
 * @author Robert Fromont robert@fromont.net.nz
 */
public class SqlSearchResults implements SearchResults {
  
  /**
   * The database key of the search/results rows.
   * @see #getId()
   * @see #setId(long)
   */
  protected long id = -1;
  /**
   * Getter for {@link #id}: The database key of the search/results rows.
   * @return The database key of the search/results rows.
   */
  public long getId() { return id; }
  /**
   * Setter for {@link #id}: The database key of the search/results rows.
   * @param newId The database key of the search/results rows.
   */
  public SqlSearchResults setId(long newId) { id = newId; return this; }
  
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
  public SqlSearchResults setName(String newName) {
    name = newName;
    if (connection != null && id >= 0) {
      try {
        PreparedStatement sql = connection.prepareStatement(
          "UPDATE search SET name = ? WHERE search_id = ?");
        if (name.length() > 100) name = name.substring(0,100);
        sql.setString(1, name);
        sql.setLong(2, id);
        sql.executeUpdate();
        sql.close();
      } catch(SQLException exception) {
        System.err.println("SqlSearchResults.setName: " + exception);
      }
    }
    return this;
  }
  
  /**
   * SearchResults method: Resets the iterator to the beginning of the list
   */
  public void reset() {
    try { if (rsIterator != null) rsIterator.close(); } catch(SQLException exception) {}
    rsIterator = null;
  }

  int size = 0;
  /**
   * SearchResults method: Returns the number of utterances in the collection.
   * @return The number of utterances in the collection.
   */
  public int size() {
    return size;
  }

  /**
   * Go to the nth item in the list, so it will be the next returned by {@link #next()}.
   * @param n The number of the item to seek to.
   * @return true if the nth item exists, false otherwise.
   */
  public boolean seek(int n) {
    try { if (rsIterator != null) rsIterator.close(); } catch(SQLException exception) {}
    rsIterator = null;
    try { checkIterator(n); } catch(SQLException exception) {}
    return hasNext();
  }

  /**
   * Iterator method: Returns true if the iteration has more elements.
   * @return true if the iteration has more elements.
   */
  public boolean hasNext() {
    try { checkIterator(); } catch(SQLException exception) {}
    return nextRow <= size && (pageLength <= 0 || nextCount < pageLength);
  } // end of hasNext()

  int nextCount = 0;
  /**
   * Iterator method: Returns the next result ID.
   * @return The next result ID.
   */
  public String next() {
    if (!hasNext()) throw new NoSuchElementException();
    nextCount++;
    try { 
      checkIterator();
      rsIterator.next();
      lastMatchId = new IdMatch()
        .addMatchAnnotationUid("0", "ew_0_"+rsIterator.getInt("first_matched_word_annotation_id"))
        .addMatchAnnotationUid("1", "ew_0_"+rsIterator.getInt("last_matched_word_annotation_id"))
        .setTargetAnnotationUid(rsIterator.getString("target_annotation_uid"))
        .setPrefix(resultNumberFormatter.format(nextRow)+"-")
        .setGraphId(rsIterator.getInt("ag_id"))
        .setStartAnchorId(rsIterator.getLong("start_anchor_id"))
        .setEndAnchorId(rsIterator.getLong("end_anchor_id"))
        .setSpeakerNumber(rsIterator.getInt("speaker_number"))
        .setDefiningAnnotationUid("em_12_"+rsIterator.getInt("defining_annotation_id"))
        .getId();
      nextRow++;
      return lastMatchId;
    } catch(Exception exception) {
      throw (NoSuchElementException)(new NoSuchElementException(exception.toString())
                                     .initCause(exception));
    }
  } // end of next()

  /**
   * Iterator method: Removes from the underlying collection the last element returned by
   * the iterator. This method can be called only once per call to next. The
   * @throws UnsupportedOperationException If the remove operation is not supported by
   * this Iterator. 
   * @throws IllegalStateException If the next method has not yet been called, or the
   * remove method has already been called after the last call to the next method. 
   **/
  public void remove() throws UnsupportedOperationException, IllegalStateException {
    try {
      if (lastMatchId != null) rsIterator.deleteRow();
    } catch(SQLException exception) {
      throw (NoSuchElementException)(new NoSuchElementException(exception.toString())
                                     .initCause(exception));
    }
  }
  
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

  Connection connection = null;
  PreparedStatement sqlIterator = null;
  ResultSet rsIterator = null;
  int nextRow = 0;
  NumberFormat resultNumberFormatter = NumberFormat.getInstance();

  /**
   * Constructor that creates a new search record based on the given search task.
   * @param search The search to hold the results for.
   * @throws Exception If there's a problem inserting the search row.
   */
  public SqlSearchResults(SearchTask search) throws Exception {
    connection = search.getStore().getConnection();
    PreparedStatement sql
      = connection.prepareStatement(
        "INSERT INTO search (name, who, target_layer_id, context_words, definition)"
        +" VALUES (?,?,?,0,?)");
    name = search.getName();
    if (name.length() > 100) name = name.substring(0,100);
    sql.setString(1, name);
    sql.setString(2, search.getWho());
    Integer target_layer_id = SqlConstants.LAYER_TRANSCRIPTION;
    String matrixTarget = search.getMatrix().getTargetLayerId();
    if (matrixTarget != null) {
      Layer layer = search.getStore().getSchema().getLayer(matrixTarget);
      if (layer != null && layer.containsKey("layer_id")) {
        target_layer_id = (Integer)layer.get("layer_id");
      }
    }
    sql.setInt(3, target_layer_id);
    sql.setString(4, search.getMatrix().toString());
    sql.executeUpdate();
    sql.close();
    PreparedStatement sqlLastId = connection.prepareStatement("SELECT LAST_INSERT_ID()");
    ResultSet rs = sqlLastId.executeQuery();
    rs.next();
    id = rs.getLong(1);
    rs.close();
    sqlLastId.close();
  }

  /**
   * Constructor that provides access to an existing search record based on the given results.
   * @param results The existing search results collection.
   * @param connection A valid database connection.
   */
  public SqlSearchResults(SqlSearchResults results, Connection connection) {    
    this.connection = connection;
    this.id = results.id;
    this.name = results.name;
  }

  /**
   * Ensures that the iterator is in a valid state for iterating.
   * @throws SQLException
   */
  private void checkIterator() throws SQLException {
    checkIterator(1);
  }
  
  /**
   * Ensures that the iterator is in a valid state for iterating.
   * @param firstMatch Start from this match.
   * @throws SQLException
   */
  private void checkIterator(int firstMatch) throws SQLException {
    if (rsIterator == null) {
      if (sqlIterator != null) try { sqlIterator.close(); } catch(SQLException exception) {}
      sqlIterator = connection.prepareStatement(
        "SELECT result.* FROM result"
        +" WHERE result.search_id = ?"
        +" ORDER BY match_id"
        +(firstMatch!=1?" LIMIT "+(firstMatch-1)+",18446744073709551615":""));
      sqlIterator.setLong(1, id);
      PreparedStatement sqlSize = connection.prepareStatement(
        "SELECT COUNT(*) FROM result WHERE result.search_id = ?");
      sqlSize.setLong(1, getId());
      rsIterator = sqlSize.executeQuery();
      rsIterator.next();
      size = rsIterator.getInt(1);
      resultNumberFormatter.setGroupingUsed(false);
      resultNumberFormatter.setMinimumIntegerDigits((int)(Math.log10(size)) + 1);
      rsIterator.close();
      sqlSize.close();
      rsIterator = sqlIterator.executeQuery();
      nextRow = firstMatch;
    }      
  } // end of checkIterator()

  /**
   * Close all open resources.
   */
  public void close() {
    try { if (rsIterator != null) rsIterator.close(); } catch(SQLException exception) {}
    try { if (sqlIterator != null) sqlIterator.close(); } catch(SQLException exception) {}
  } // end of close()

}
