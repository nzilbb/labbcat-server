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

public class TestIdMatch {
  /** Test parsing of ID strings works */
  @Test public void idComputation() {
    // basic graph+uid
    IdMatch utt = new IdMatch("g_243;em_12_20035");
    assertEquals(new Integer(243), utt.getGraphId());
    assertEquals("em_12_20035", utt.getDefiningAnnotationUid());
    assertNull(utt.getStartAnchorId());
    assertNull(utt.getEndAnchorId());
    assertNull(utt.getSpeakerNumber());
    assertNull(utt.getPrefix());
    assertNull(utt.getTargetAnnotationUid());
    assertEquals(0, utt.getMatchAnnotationUids().size());
    assertEquals("g_243;em_12_20035", utt.getId());
    
    // graph+anchor-range
    utt = new IdMatch("g_243;n_72700-n_72709");
    assertEquals(new Integer(243), utt.getGraphId());
    assertNull(utt.getDefiningAnnotationUid());
    assertEquals(new Long(72700), utt.getStartAnchorId());
    assertEquals(new Long(72709), utt.getEndAnchorId());
    assertNull(utt.getSpeakerNumber());
    assertNull(utt.getPrefix());
    assertNull(utt.getTargetAnnotationUid());
    assertEquals(0, utt.getMatchAnnotationUids().size());
    assertEquals("g_243;n_72700-n_72709", utt.getId());
    
    // graph+anchor-range+speaker
    utt = new IdMatch("g_243;n_72700-n_72709;p_123");
    assertEquals(new Integer(243), utt.getGraphId());
    assertNull(utt.getDefiningAnnotationUid());
    assertEquals(new Long(72700), utt.getStartAnchorId());
    assertEquals(new Long(72709), utt.getEndAnchorId());
    assertEquals(new Integer(123), utt.getSpeakerNumber());
    assertNull(utt.getPrefix());
    assertNull(utt.getTargetAnnotationUid());
    assertEquals(0, utt.getMatchAnnotationUids().size());
    assertEquals("g_243;n_72700-n_72709;p_123", utt.getId());
    
    // graph+anchor-range+speaker+prefix
    utt = new IdMatch("g_243;n_72700-n_72709;p_123;prefix=099-");
    assertEquals(new Integer(243), utt.getGraphId());
    assertNull(utt.getDefiningAnnotationUid());
    assertEquals(new Long(72700), utt.getStartAnchorId());
    assertEquals(new Long(72709), utt.getEndAnchorId());
    assertEquals(new Integer(123), utt.getSpeakerNumber());
    assertEquals("099-", utt.getPrefix());
    assertNull(utt.getTargetAnnotationUid());
    assertEquals(0, utt.getMatchAnnotationUids().size());
    assertEquals("g_243;n_72700-n_72709;p_123;prefix=099-", utt.getId());
    
    // target annotation
    utt = new IdMatch("g_243;n_72700-n_72709;p_123;prefix=099-;#=es_1_987");
    assertEquals(new Integer(243), utt.getGraphId());
    assertNull(utt.getDefiningAnnotationUid());
    assertEquals(new Long(72700), utt.getStartAnchorId());
    assertEquals(new Long(72709), utt.getEndAnchorId());
    assertEquals(new Integer(123), utt.getSpeakerNumber());
    assertEquals("099-", utt.getPrefix());
    assertEquals("es_1_987", utt.getTargetAnnotationUid());
    assertEquals(0, utt.getMatchAnnotationUids().size());
    assertEquals("g_243;n_72700-n_72709;p_123;#=es_1_987;prefix=099-", utt.getId());
    
    // matches
    IdMatch utt2 = new IdMatch(
      "g_243;n_72700-n_72709;p_123;prefix=099-;#=es_1_987;[first]=ew_0_876;[second]=ew_2_765");
    assertEquals(new Integer(243), utt2.getGraphId());
    assertNull(utt2.getDefiningAnnotationUid());
    assertEquals(new Long(72700), utt2.getStartAnchorId());
    assertEquals(new Long(72709), utt2.getEndAnchorId());
    assertEquals(new Integer(123), utt2.getSpeakerNumber());
    assertEquals("099-", utt2.getPrefix());
    assertEquals("es_1_987", utt2.getTargetAnnotationUid());
    assertEquals(2, utt2.getMatchAnnotationUids().size());
    assertEquals(
      "g_243;n_72700-n_72709;p_123;#=es_1_987;prefix=099-;[first]=ew_0_876;[second]=ew_2_765",
      utt2.getId());
    
    assertTrue(utt2.equals(utt2));
    assertFalse(utt2.equals(utt));
    assertFalse(utt.equals(utt2));
  }

  /** Test deep copy works. */
  @Test public void copy() {
    IdMatch other = new IdMatch() {
        public Integer getGraphId() { return new Integer(123); }
        public Long getStartAnchorId() { return new Long(234); }
        public Long getEndAnchorId() { return new Long(345); }
        public Integer getSpeakerNumber() { return new Integer(456); }
        public String getDefiningAnnotationUid() { return "e_567_8910"; }
        public Map<String,String> getMatchAnnotationUids()
        {
          HashMap<String,String> m = new HashMap<String,String>();
          m.put("0,0", "ew_0_11");
          m.put("1,0", "ew_0_22");
          return m;
        }
        public String getTargetAnnotationUid() { return "es_1_112233"; }
        
      };
    IdMatch utt = new IdMatch(other);
    assertEquals(new Integer(123), utt.getGraphId());
    assertEquals("e_567_8910", utt.getDefiningAnnotationUid());
    assertEquals(new Long(234), utt.getStartAnchorId());
    assertEquals(new Long(345), utt.getEndAnchorId());
    assertEquals(new Integer(456), utt.getSpeakerNumber());
    assertEquals("es_1_112233", utt.getTargetAnnotationUid());
    assertEquals("ew_0_11", utt.getMatchAnnotationUids().get("0,0"));
    assertEquals("ew_0_22", utt.getMatchAnnotationUids().get("1,0"));
    assertEquals(other.getGraphId(), utt.getGraphId());
    assertEquals(other.getDefiningAnnotationUid(), utt.getDefiningAnnotationUid());
    assertEquals(other.getStartAnchorId(), utt.getStartAnchorId());
    assertEquals(other.getEndAnchorId(), utt.getEndAnchorId());
    assertEquals(other.getSpeakerNumber(), utt.getSpeakerNumber());
    assertEquals(
      "g_123;e_567_8910;n_234-n_345;p_456;#=es_1_112233;[0,0]=ew_0_11;[1,0]=ew_0_22",
      utt.getId());
    assertTrue(utt.equals(other));
  }   
   
  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.search.TestIdMatch");
  }
}
