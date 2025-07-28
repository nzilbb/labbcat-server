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

package nzilbb.labbcat.server.api.results;
	      
import org.junit.*;
import static org.junit.Assert.*;

import java.net.MalformedURLException;
import java.util.Map;
import nzilbb.ag.Annotation;
import nzilbb.labbcat.LabbcatView;
import nzilbb.labbcat.ResponseException;
import nzilbb.labbcat.PatternBuilder;
import nzilbb.labbcat.model.Match;
import nzilbb.labbcat.model.TaskStatus;
import javax.json.JsonObject;

/**
 * Test the <tt>api/results/upload</tt> endpoint.
 * <p> These tests assume that there is a working LaBB-CAT instance with the latest version
 * of nzilbb.labbcat.server.jar installed.  
 */
public class TestUpload {
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

  /** Ensure search results can be uploaded and resulting annotaitions returned. */
  @Test public void resultsUpload() throws Exception {
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
      
      // now the upload tests...
      
      String[] layerIds = { "orthography" };
      Annotation[][] annotations = l.getMatchAnnotations(matches, layerIds, 0, 1);
      assertEquals("annotations array is same size as matches array",
                   matches.length, annotations.length);
      assertEquals("row arrays are the right size", 1, annotations[0].length);
      
      layerIds[0] = "invalid layer ID";
      try {
        l.getMatchAnnotations(matches, layerIds, 0, 1);
        fail("getMatchAnnotations with invalid layerId should fail");
      } catch(ResponseException exception) {
        System.out.println(""+exception);
        assertTrue("Invalid ID error",
                   exception.getMessage().startsWith("Invalid layer ID"));
      }
    } finally {
      l.releaseTask(threadId);
    }
  }

  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.results.TestUpload");
  }
}
