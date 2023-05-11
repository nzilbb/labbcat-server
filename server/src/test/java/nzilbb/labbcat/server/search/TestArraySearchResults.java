//
// Copyright 2023 New Zealand Institute of Language, Brain and Behaviour, 
// University of Canterbury
// Written by Robert Fromont - robert.fromont@canterbury.ac.nz
//
//    This file is part of LaBB-CAT.
//
//    LaBB-CAT is free software; you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation; either version 3 of the License, or
//    (at your option) any later version.
//
//    LaBB-CAT is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with nzilbb.ag; if not, write to the Free Software
//    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//

package nzilbb.labbcat.server.search;

import org.junit.*;
import static org.junit.Assert.*;

public class TestArraySearchResults {

  /** Ensure that an explicit array of IDs can be correctly iterated. */
  @Test public void iteration() {
    String[] IDs = { "1", "2", "3", "4", "5" };
    ArraySearchResults results = new ArraySearchResults(IDs);
    assertEquals("Size", 5, results.size());    
    assertTrue("hasNext at the beginning", results.hasNext());
    assertNull("lastMatchId at the beginning", results.getLastMatchId());    
    assertEquals("next 1", "1", results.next());    
    assertEquals("lastMatchId 1", "1", results.getLastMatchId());    
    assertTrue("hasNext after 1", results.hasNext());
    assertEquals("next 2", "2", results.next());    
    assertEquals("lastMatchId 2", "2", results.getLastMatchId());    
    assertTrue("hasNext after 2", results.hasNext());
    assertEquals("next 3", "3", results.next());    
    assertEquals("lastMatchId 3", "3", results.getLastMatchId());    
    assertTrue("hasNext after 3", results.hasNext());
    assertEquals("next 4", "4", results.next());    
    assertEquals("lastMatchId 4", "4", results.getLastMatchId());    
    assertTrue("hasNext after 4", results.hasNext());
    assertEquals("next 5", "5", results.next());    
    assertEquals("lastMatchId 5", "5", results.getLastMatchId());    
    assertFalse("hasNext after 5", results.hasNext());
    results.close();// nothing bad happens
  }

  /** Ensure pagination functionality works. */
  @Test public void pagination() {
    String[] IDs = { "1", "2", "3", "4", "5" };
    ArraySearchResults results = new ArraySearchResults(IDs);
    results.setPageLength(2);
    results.seek(2);
    assertEquals("Size", 5, results.size());    
    assertTrue("hasNext at the beginning", results.hasNext());
    assertNull("lastMatchId at the beginning", results.getLastMatchId());    
    assertEquals("next 2", "2", results.next());    
    assertEquals("lastMatchId 2", "2", results.getLastMatchId());    
    assertTrue("hasNext after 2", results.hasNext());
    assertEquals("next 3", "3", results.next());    
    assertEquals("lastMatchId 3", "3", results.getLastMatchId());    
    assertFalse("hasNext at end of page", results.hasNext());
    
    results.reset();
    assertTrue("hasNext after reset", results.hasNext());
    assertEquals("next 1", "1", results.next());    
  }

}
