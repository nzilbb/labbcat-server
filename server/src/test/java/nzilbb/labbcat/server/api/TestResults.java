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

package nzilbb.labbcat.server.api;
	      
import org.junit.*;
import static org.junit.Assert.*;

import java.net.MalformedURLException;
import java.util.Map;
import nzilbb.labbcat.LabbcatView;
import nzilbb.labbcat.ResponseException;
import nzilbb.labbcat.PatternBuilder;
import nzilbb.labbcat.model.Match;
import nzilbb.labbcat.model.TaskStatus;
import javax.json.JsonObject;

/**
 * Test the <tt>api/results</tt> endpoint.
 * <p> These tests assume that there is a working LaBB-CAT instance with the latest version
 * of nzilbb.labbcat.server.jar installed.  
 */
public class TestResults {
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

  /** Ensure search results can be retrieved. */
  @Test public void results() throws Exception {
    // get a participant ID to use
    String[] ids = l.getParticipantIds();
    assertTrue("getParticipantIds: Some IDs are returned", ids.length > 0);
    String[] participantId = { ids[0] };
    
    // all instances of "and"
    JsonObject pattern = new PatternBuilder().addMatchLayer("orthography", "and").build();
    String threadId = l.search(pattern, participantId, null, false, null, null, null);
    try {
      TaskStatus task = l.waitForTask(threadId, 30);
      // if the task is still running, it's taking too long, so cancel it
      if (task.getRunning()) try { l.cancelTask(threadId); } catch(Exception exception) {}
      assertFalse("Search task finished in a timely manner", task.getRunning());
         
      Match[] matches = l.getMatches(threadId, 2);
      if (matches.length == 0) {
        System.out.println(
          "getMatches: No matches were returned, cannot test getMatchAnnotations");
      } else {
        int upTo = Math.min(10, matches.length);
        // for (int m = 0; m < upTo; m++) System.out.println("Match: " + matches[m]);
        
        matches = l.getMatches(threadId, 2, upTo, 0);
        assertEquals("pagination works ("+upTo+")",
                     upTo, matches.length);
      }
    } finally {
      l.releaseTask(threadId);
    }
  }
  
  /** Test invalid results request */
  @Test public void invalidResults() throws Exception {
    try {
      l.getMatches("invalid-id", 0);
      fail("Invalid thread ID returns error");
    } catch(ResponseException exception) {
      assertEquals("Invalid Request error", 400, exception.getResponse().getHttpStatus());
    }
  }
  
  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.TestResults");
  }
}
