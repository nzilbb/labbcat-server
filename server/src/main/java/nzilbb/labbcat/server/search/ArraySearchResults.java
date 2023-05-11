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

import java.util.NoSuchElementException;

/**
 * Search results constructed from an array of selected MatchId strings.
 * @author Robert Fromont robert@fromont.net.nz
 */
public class ArraySearchResults implements SearchResults {

  String[] IDs;
  int nextRow = 0;
  
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
  public ArraySearchResults setName(String newName) {
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
   * Constructor from MatchId array.
   * @param IDs An array of MatchIDs.
   */
  public ArraySearchResults(String[] IDs) {
    this.IDs = IDs;
  } // end of constructor

  /**
   * SearchResults method: Resets the iterator to the beginning of the list
   */
  public void reset() {
    nextRow = 0;
    nextCount = 0;
  }

  /**
   * SearchResults method: Returns the number of utterances in the collection.
   * @return The number of utterances in the collection.
   */
  public int size() {
    return IDs.length;
  }

  /**
   * Go to the nth item in the list, so it will be the next returned by {@link #next()}.
   * @param n The number of the item to seek to.
   * @return true if the nth item exists, false otherwise.
   */
  public boolean seek(int n) {
    nextRow = n-1;
    return hasNext();
  }

  /**
   * Iterator method: Returns true if the iteration has more elements.
   * @return true if the iteration has more elements.
   */
  public boolean hasNext() {
    return nextRow < IDs.length && (pageLength <= 0 || nextCount < pageLength);
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
      lastMatchId = IDs[nextRow++];
      return lastMatchId;
    } catch(Exception exception) {
      throw (NoSuchElementException)(new NoSuchElementException(exception.toString())
                                     .initCause(exception));
    }
  } // end of next()
  
  /**
   * Close all open resources (there are none).
   */
  public void close() {
  } // end of close()
} // end of class ArraySearchResults
