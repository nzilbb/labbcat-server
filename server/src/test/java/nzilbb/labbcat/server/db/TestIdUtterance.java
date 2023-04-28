//
// Copyright 2013-2023 New Zealand Institute of Language, Brain and Behaviour, 
// University of Canterbury
// Written by Robert Fromont - robert.fromont@canterbury.ac.nz
//
//    This file is part of LaBB-CAT.
//
//    LaBB-CAT is free software; you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation; either version 2 of the License, or
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

import org.junit.*;
import static org.junit.Assert.*;
import java.util.*;
import java.io.*;
import java.net.*;

public class TestIdUtterance {
  /** Ensure parsing of ID strings works. */
  @Test public void idComputation() {
    // basic graph+uid
    IdUtterance utt = new IdUtterance("g_243;em_12_20035");
    assertEquals(new Integer(243), utt.getGraphId());
    assertEquals("em_12_20035", utt.getDefiningAnnotationUid());
    assertNull(utt.getStartAnchorId());
    assertNull(utt.getEndAnchorId());
    assertNull(utt.getSpeakerNumber());
    assertNull(utt.getPrefix());
    assertEquals("g_243;em_12_20035", utt.getId());
    
    // graph+anchor-range
    utt = new IdUtterance("g_243;n_72700-n_72709");
    assertEquals(new Integer(243), utt.getGraphId());
    assertNull(utt.getDefiningAnnotationUid());
    assertEquals(new Long(72700), utt.getStartAnchorId());
    assertEquals(new Long(72709), utt.getEndAnchorId());
    assertNull(utt.getSpeakerNumber());
    assertNull(utt.getPrefix());
    assertEquals("g_243;n_72700-n_72709", utt.getId());
    
    // graph+anchor-range+speaker
    utt = new IdUtterance("g_243;n_72700-n_72709;p_123");
    assertEquals(new Integer(243), utt.getGraphId());
    assertNull(utt.getDefiningAnnotationUid());
    assertEquals(new Long(72700), utt.getStartAnchorId());
    assertEquals(new Long(72709), utt.getEndAnchorId());
    assertEquals(new Integer(123), utt.getSpeakerNumber());
    assertNull(utt.getPrefix());
    assertEquals("g_243;n_72700-n_72709;p_123", utt.getId());
    
    // graph+anchor-range+speaker+prefix
    utt = new IdUtterance("g_243;n_72700-n_72709;p_123;prefix=099-");
    assertEquals(new Integer(243), utt.getGraphId());
    assertNull(utt.getDefiningAnnotationUid());
    assertEquals(new Long(72700), utt.getStartAnchorId());
    assertEquals(new Long(72709), utt.getEndAnchorId());
    assertEquals(new Integer(123), utt.getSpeakerNumber());
    assertEquals("099-", utt.getPrefix());
    assertEquals("g_243;n_72700-n_72709;p_123;prefix=099-", utt.getId());
    
    // TODO offsets
    // TODO search name
    // TODO target annotation
    // TODO match range
  }

  /** Test equals function works */
  @Test public void equals() {
    IdUtterance utt = new IdUtterance("g_243;n_72700-n_72709;p_123");
    IdUtterance utt2 = new IdUtterance("g_243;n_72700-n_72709;p_123");
    utt2.setPrefix("001-");
    assertEquals("reflexive", utt, utt);
    assertEquals("prefix ignored", utt, utt2);
    assertEquals("prefix ignored other way", utt2, utt);
  }   
  
  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.search.TestIdUtterance");
  }
}
