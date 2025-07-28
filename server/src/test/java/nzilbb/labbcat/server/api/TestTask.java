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
 * Test the <tt>api/task/...</tt> endpoints.
 * <p> These tests assume that there is a working LaBB-CAT instance with the latest version
 * of nzilbb.labbcat.server.jar installed.  
 */
public class TestTask {
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
  
  /** Ensure tasks can be listed and their statuses retrieved. */
  @Test public void tasks() throws Exception {
    // ensure there's at least one task
    JsonObject pattern = new PatternBuilder().addMatchLayer("orthography", "xxx").build();
    String threadId = l.search(pattern, null, null, false, null, null, null);
    try {
      
      String[] tasks = l.getTasks();
      assertNotNull("tasks were returned", tasks);
      assertTrue("At least one task was returned", tasks.length > 0);
      boolean taskFound = false;
      for (String id : tasks) {
        if (id.equals(threadId)) {
          taskFound = true;
          break;
        }
      }
      assertTrue("Our task ("+threadId+") is among those listed.", taskFound);

      TaskStatus task = l.taskStatus(threadId);
      assertEquals("Correct task", threadId, task.getThreadId());
      assertNotNull("Has a status", task.getStatus());
      assertNull("Has no log", task.getLog());
      
      // ask for log
      task = l.taskStatus(threadId, true, false);
      assertEquals("Correct task", threadId, task.getThreadId());
      assertNotNull("Has a status", task.getStatus());
      assertNotNull("Has log", task.getLog());
      
    } finally {
      l.releaseTask(threadId);
    }    
  }

  /** Test invalid task request. */
  @Test public void invalidTaskStatus() throws Exception {
    try {
      TaskStatus task = l.taskStatus(""+Integer.MAX_VALUE);
      fail("Should fail with invalid ID");
    } catch(ResponseException exception) {
      assertEquals("Invalid request error", 404, exception.getResponse().getHttpStatus());
    }
  }

  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.TestTask");
  }
}
