//
// Copyright 2023 New Zealand Institute of Language, Brain and Behaviour, 
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

package nzilbb.labbcat.server.api;
	      
import org.junit.*;
import static org.junit.Assert.*;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import nzilbb.ag.Anchor;
import nzilbb.ag.Annotation;
import nzilbb.ag.Layer;
import nzilbb.ag.MediaFile;
import nzilbb.ag.MediaTrackDefinition;
import nzilbb.ag.PermissionException;
import nzilbb.ag.StoreException;
import nzilbb.ag.serialize.SerializationDescriptor;
import nzilbb.labbcat.LabbcatView;
import nzilbb.labbcat.PatternBuilder;
import nzilbb.labbcat.ResponseException;
import nzilbb.labbcat.model.TaskStatus;
import nzilbb.labbcat.http.HttpRequestGet;
import nzilbb.labbcat.model.Match;

/**
 * These tests assume that there is a working LaBB-CAT instance with the latest version of
 * nzilbb.labbcat.server.jar installed.  
 */
public class TestSearch {
  static String labbcatUrl = "http://localhost:8080/labbcat/";
  static String username = "labbcat";
  static String password = "labbcat";
  static LabbcatView labbcat;
  
  @BeforeClass public static void setBaseUrl() throws MalformedURLException {    
    try {
      labbcat = new LabbcatView(labbcatUrl, username, password);
      labbcat.setBatchMode(true);
    } catch(MalformedURLException exception) {
      fail("Could not create Labbcat object");
    }
  }
  
  /** Ensure searching with an invalid pattern correctly fails. */
  @Test(expected = StoreException.class) public void searchInvalidPattern()
    throws Exception {
    String threadId = labbcat.search(
      Json.createObjectBuilder().build(), null, null, false, null, null, null);
  }

  /** Ensure searches can be cancelled. */
  @Test public void searchAndCancelTask()
    throws Exception {
    // start a long-running search - all words
    JsonObject pattern = new PatternBuilder().addMatchLayer("orthography", ".*").build();
    String threadId = labbcat.search(pattern, null, null, false, null, null, null);
    labbcat.cancelTask(threadId);
  }
  
  /** Ensure workflow of searching works, from specifying the search to retrieving results */
  @Test public void searchAndGetMatchesAndGetMatchAnnotations()
    throws Exception {
    // get a participant ID to use
    String[] ids = labbcat.getParticipantIds();
    assertTrue("getParticipantIds: Some IDs are returned",
               ids.length > 0);
    String[] participantId = { ids[0] };

    // all instances of "and"
    JsonObject pattern = new PatternBuilder().addMatchLayer("orthography", "and").build();
    String threadId = labbcat.search(pattern, participantId, null, false, null, null, null);
    try {
      TaskStatus task = labbcat.waitForTask(threadId, 30);
      // if the task is still running, it's taking too long, so cancel it
      if (task.getRunning()) try { labbcat.cancelTask(threadId); } catch(Exception exception) {}
      assertFalse("Search task finished in a timely manner",
                  task.getRunning());
         
      Match[] matches = labbcat.getMatches(threadId, 2);
      if (matches.length == 0) {
        System.out.println(
          "getMatches: No matches were returned, cannot test getMatchAnnotations");
      } else {
        int upTo = Math.min(10, matches.length);
        // for (int m = 0; m < upTo; m++) System.out.println("Match: " + matches[m]);

        matches = labbcat.getMatches(threadId, 2, upTo, 0);
        assertEquals("pagination works ("+upTo+")",
                     upTo, matches.length);

        String[] layerIds = { "orthography" };
        Annotation[][] annotations = labbcat.getMatchAnnotations(matches, layerIds, 0, 1);
        assertEquals("annotations array is same size as matches array",
                     matches.length, annotations.length);
        assertEquals("row arrays are the right size",
                     1, annotations[0].length);

        layerIds[0] = "invalid layer ID";
        try {
          labbcat.getMatchAnnotations(matches, layerIds, 0, 1);
          fail("getMatchAnnotations with invalid layerId should fail");
        } catch(StoreException exception) {}
      }
    } finally {
      labbcat.releaseTask(threadId);
    }
  }

  /** Ensure search for non-ASCII patterns, particularly IPA ones like "iː", work. */
  @Test public void searchIPA()
    throws Exception {
    // get a participant ID to use
    String[] ids = labbcat.getParticipantIds();
    assertTrue("getParticipantIds: Some IDs are returned",
               ids.length > 0);
    String[] participantId = { ids[0] };

    // all instances of "and"
    JsonObject pattern = new PatternBuilder().addMatchLayer("segment", "iː").build();
    String threadId = labbcat.search(pattern, participantId, null, false, null, null, null);
    try {
      TaskStatus task = labbcat.waitForTask(threadId, 30);
      // if the task is still running, it's taking too long, so cancel it
      if (task.getRunning()) try { labbcat.cancelTask(threadId); } catch(Exception exception) {}
      assertFalse("Search task finished in a timely manner",
                  task.getRunning());
    } finally {
      labbcat.releaseTask(threadId);
    }
  }

  /** Ensure exclusion of overlapping speech in search results works. */
  @Test public void searchExcludingOverlappingSpeech()
    throws Exception {
    
    // all instances of "mmm", which are frequently used in overlapping speech
    JsonObject pattern = new PatternBuilder().addMatchLayer("orthography", "mmm").build();
    Match[] includingOverlapping = labbcat.getMatches(
      pattern, null, null, false, null, null, null, 0);
    Match[] excludingOverlapping = labbcat.getMatches(
      pattern, null, null, false, null, null, 5, 0);
    assertTrue("There are fewer matches when overlapping speech is excluded",
               includingOverlapping.length > excludingOverlapping.length);
  }

  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.TestSearch");
  }
}
