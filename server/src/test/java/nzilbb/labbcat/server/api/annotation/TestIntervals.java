//
// Copyright 2025 New Zealand Institute of Language, Brain and Behaviour, 
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

package nzilbb.labbcat.server.api.annotation;
	      
import org.junit.*;
import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import javax.json.JsonObject;
import nzilbb.labbcat.http.HttpRequestGet;
import nzilbb.labbcat.LabbcatView;
import nzilbb.labbcat.PatternBuilder;
import nzilbb.labbcat.ResponseException;
import nzilbb.labbcat.model.Match;
import nzilbb.labbcat.model.TaskStatus;
import nzilbb.util.IO;

/**
 * Test the <tt>api/annotation/intervals</tt> endpoint.
 * <p> These tests assume that there is a working LaBB-CAT instance with the latest version
 * of nzilbb.labbcat.server.jar installed.  
 */
public class TestIntervals {
  static String labbcatUrl = "http://localhost:8080/labbcat/";
  static String username = "labbcat";
  static String password = "labbcat";
  static LabbcatView l;
  
  @BeforeClass public static void setBaseUrl() throws MalformedURLException {    
    try {
      l = new LabbcatView(labbcatUrl, username, password);
      l.setBatchMode(true);
    } catch(MalformedURLException exception) {
      fail("Could not create Labbcat object");
    }
  }

  /** Test that annotation labels between start/end times can be extracted. */
  @Test public void annotationIntervals() throws Exception {
    // get a participant ID to use
    String[] ids = l.getParticipantIds();
    assertTrue("getParticipantIds: Some IDs are returned", ids.length > 0);
    String[] participantId = { ids[0] };      
    
    // all instances of and
    JsonObject pattern = new PatternBuilder().addMatchLayer("orthography", "and").build();
    String searchThreadId = l.search(pattern, participantId, null, false, null, null, null);
    try {
      TaskStatus task = l.waitForTask(searchThreadId, 30);
      // if the task is still running, it's taking too long, so cancel it
      if (task.getRunning()) {
        try { l.cancelTask(searchThreadId); } catch(Exception exception) {}
      }
      assertFalse("Search task finished in a timely manner", task.getRunning());
      
      String[] layerIds = { "orthography" };
      Match[] matches = l.getMatches(searchThreadId, 2);
      if (matches.length == 0) {
        fail("getMatches: No matches were returned, cannot test processWithPraat");
      } else {
        int upTo = Math.min(5, matches.length);
        Match[] subset = Arrays.copyOfRange(matches, 0, upTo);
        String[] transcriptIds = Arrays.stream(subset)
          .map(match -> match.getTranscript())
          .collect(Collectors.toList()).toArray(new String[0]);
        String[] participantIds = Arrays.stream(subset)
          .map(match -> match.getParticipant())
          .collect(Collectors.toList()).toArray(new String[0]);
        Double[] startOffsets = Arrays.stream(subset)
          .map(match -> match.getLine())
          .collect(Collectors.toList()).toArray(new Double[0]);
        Double[] endOffsets = Arrays.stream(subset)
          .map(match -> match.getLineEnd())
          .collect(Collectors.toList()).toArray(new Double[0]);
        String extractionThreadId = l.intervalAnnotations(
          transcriptIds, participantIds, startOffsets, endOffsets, layerIds, " ", false);
        try {
          task = l.waitForTask(extractionThreadId, 30);
          // if the task is still running, it's taking too long, so cancel it
          if (task.getRunning()) {
            try { l.cancelTask(extractionThreadId); } catch(Exception exception) {}
          }
          assertFalse("Extraction task finished in a timely manner",
                      task.getRunning());
          
          assertTrue("Output is a CSV URL: " + task.getResultUrl(),
                     task.getResultUrl().endsWith(".csv"));
          
          // download URL
          HttpRequestGet request = new HttpRequestGet(
            task.getResultUrl(), l.getRequiredHttpAuthorization());
          String csv = IO.InputStreamToString(request.get().getInputStream());
          String[] rows = csv.split("\n");
          assertEquals("CSV header is correct: " + csv,
                       "orthography,orthography start,orthography end", rows[0]);          
        } finally {
          l.releaseTask(extractionThreadId);
        }
      }
    } finally {
      l.releaseTask(searchThreadId);
    }
  }

  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.annotation.TestIntervals");
  }
}
