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

package nzilbb.labbcat.server.api.serialize;
	      
import org.junit.*;
import static org.junit.Assert.*;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Map;
import nzilbb.labbcat.LabbcatView;
import nzilbb.labbcat.ResponseException;
import nzilbb.labbcat.PatternBuilder;
import nzilbb.labbcat.model.Match;
import nzilbb.labbcat.model.TaskStatus;
import javax.json.JsonObject;

/**
 * Test the <tt>api/serialize/fragments</tt> endpoint.
 * <p> These tests assume that there is a working LaBB-CAT instance with the latest version
 * of nzilbb.labbcat.server.jar installed.  
 */
public class TestFragments {
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

  /** Ensure transcript fragments cn be extracted. */
  @Test public void serializeFragments() throws Exception {
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
        fail("getMatches: No matches were returned, cannot test getFragments");
      } else {
        int upTo = Math.min(5, matches.length);
        Match[] subset = Arrays.copyOfRange(matches, 0, upTo);

        // now test endpoint...
        
        File dir = new File("getFragments");
        String[] layerIds = { "orthography" };
        File[] fragments = l.getFragments(subset, layerIds, "text/praat-textgrid", dir);
        try {
          assertEquals("files array is same size as matches array",
                       subset.length, fragments.length);
          
          for (int m = 0; m < upTo; m++) {
            assertNotNull("Non-null file: " + subset[m], fragments[m]);
            assertTrue("Non-zero sized file: " + subset[m], fragments[m].length() > 0);
            // System.out.println(fragments[m].getPath());
          }
        } finally {
          for (File fragment : fragments) if (fragment != null) fragment.delete();
          dir.delete();
        }
      }
    } finally {
      l.releaseTask(threadId);
    }
  }

  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.serialize.TestFragments");
  }
}
