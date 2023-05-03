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

import java.util.Iterator;
import java.io.Closeable;

/**
 * Represents an iterable collection of results.
 */
public interface SearchResults extends Iterator<String>, Closeable {

  /**
   * A descriptive name for the collection.
   * @return A descriptive name for the collection.
   */
  public String getName();
  
  /**
   * Resets the iterator to the beginning of the list
   */
  public void reset();
  
  /**
   * Returns the number of utterances in the collection.
   * @return The number of utterances in the collection.
   */
  public int size();
  
  /**
   * Go to the nth item in the list, so it will be the next returned by {@link #next()}.
   * @param n The number of the item to seek to.
   * @return true if the nth item exists, false otherwise.
   */
  public boolean seek(int n);
}
