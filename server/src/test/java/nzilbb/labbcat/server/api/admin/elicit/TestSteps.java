//
// Copyright 2026 New Zealand Institute of Language, Brain and Behaviour, 
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

package nzilbb.labbcat.server.api.admin.elicit;
	      
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
import nzilbb.ag.PermissionException;
import nzilbb.ag.StoreException;
import nzilbb.ag.serialize.SerializationDescriptor;
import nzilbb.labbcat.LabbcatAdmin;
import nzilbb.labbcat.Response;
import nzilbb.labbcat.ResponseException;
import nzilbb.labbcat.http.HttpRequestGet;
import nzilbb.labbcat.http.HttpRequestPost;
import nzilbb.labbcat.http.HttpRequestPostMultipart;
import nzilbb.labbcat.model.*;
import nzilbb.labbcat.model.Match;
import nzilbb.util.IO;

/**
 * These tests assume that there is a working LaBB-CAT instance with the latest version of
 * nzilbb.labbcat.server.jar installed.
 */
public class TestSteps {
  static String labbcatUrl = "http://localhost:8080/labbcat/";
  static String username = "labbcat";
  static String password = "labbcat";
  static LabbcatAdmin l;

  @BeforeClass public static void setBaseUrl() throws MalformedURLException {

    try {
      l = new LabbcatAdmin(labbcatUrl, username, password);
      l.setBatchMode(true);
    } catch(MalformedURLException exception) {
      fail("Could not create Labbcat object");
    }
  }
  
  /** Ensure step PUT works. */
  @Test public void stepsUpdate() throws Exception {
    File files = new File(getDir(), "task");
    File someSteps = new File(files, "nonempty-unit-test-task.json");
    assertTrue("json file exists " + someSteps.getPath(), someSteps.exists());
    File noSteps = new File(files, "empty-unit-test-task.json");
    assertTrue("json file exists " + noSteps.getPath(), noSteps.exists());
    File json = new File(files, "unit-test-task.json");
    IO.Copy(someSteps, json);
    int taskId = l.elicitatationTaskUpload(json);
    try {      
      // three setps
      JsonArray steps = Json.createArrayBuilder()
        .add(Json.createObjectBuilder()
             .add("step_id", 1000)
             .add("title", "Unit test step 1")
             .add("prompt", "test step")
             .add("transcript", "")
             .add("shuffle_words", 0)
             .add("record", 0)
             .add("max_seconds", 30)
             .add("suppress_next", 0)
             .add("next_delay_seconds", 0)
             .add("suppress_rerecord", 1)
             .add("image","")
             .add("countdown_seconds", 0)
             .add("step_count", 0)
             .build())
        .add(Json.createObjectBuilder()
             .add("step_id", 1001)
             .add("title", "Unit test step 2")
             .add("prompt", "test step")
             .add("transcript", "Testing 1 2 3")
             .add("shuffle_words", 0)
             .add("record", 1)
             .add("max_seconds", 30)
             .add("suppress_next", 0)
             .add("next_delay_seconds", 0)
             .add("suppress_rerecord", 1)
             .add("image","")
             .add("countdown_seconds", 0)
             .add("step_count", 0)
             .build())
        .add(Json.createObjectBuilder()
             .add("step_id", 1002)
             .add("title", "Unit test step 3")
             .add("prompt", "test step")
             .add("transcript", "")
             .add("shuffle_words", 0)
             .add("record", 0)
             .add("max_seconds", 30)
             .add("suppress_next", 0)
             .add("next_delay_seconds", 0)
             .add("suppress_rerecord", 1)
             .add("image","")
             .add("countdown_seconds", 0)
             .add("step_count", 0)
             .build())
        .build();
      l.updateElicitationTaskSteps(taskId, steps);

      // empty steps
      l.updateElicitationTaskSteps(taskId, Json.createArrayBuilder().build());

      // should be able to delete
      l.deleteElicitationTask(taskId);

    } finally {
      // update it so it has no steps (so we can delete it)
      IO.Copy(noSteps, json);
      try {
        l.elicitatationTaskUpload(json);
      } catch(Exception exception) { // should be already gone
      }
      // delete it
      try {
        l.deleteElicitationTask(taskId);
      } catch(Exception exception) { // should be already gone
      }
    }
  }

  /** Ensure elicitation task upload validation works. */
  @Test public void stepsValidation() throws Exception {
    File files = new File(getDir(), "task");
    File someSteps = new File(files, "nonempty-unit-test-task.json");
    assertTrue("json file exists " + someSteps.getPath(), someSteps.exists());
    File noSteps = new File(files, "empty-unit-test-task.json");
    assertTrue("json file exists " + noSteps.getPath(), noSteps.exists());
    File json = new File(files, "unit-test-task.json");
    IO.Copy(someSteps, json);
    int taskId = l.elicitatationTaskUpload(json);
    
    // three steps
    JsonArray steps = Json.createArrayBuilder()
      .add(Json.createObjectBuilder()
           .add("step_id", 1000)
           .add("title", "Unit test step 1")
           .add("prompt", "test step")
           .add("transcript", "")
           .add("shuffle_words", 0)
           .add("record", 0)
           .add("max_seconds", 30)
           .add("suppress_next", 0)
           .add("next_delay_seconds", 0)
           .add("suppress_rerecord", 1)
           .add("image","")
           .add("countdown_seconds", 0)
           .add("step_count", 0)
           .build())
      .add(Json.createObjectBuilder()
           .add("step_id", 1001)
           .add("title", "Unit test step 2")
           .add("prompt", "test step")
           .add("transcript", "Testing 1 2 3")
           .add("shuffle_words", 0)
           .add("record", 1)
           .add("max_seconds", 30)
           .add("suppress_next", 0)
           .add("next_delay_seconds", 0)
           .add("suppress_rerecord", 1)
           .add("image","")
           .add("countdown_seconds", 0)
           .add("step_count", 0)
           .build())
      .add(Json.createObjectBuilder()
           .add("step_id", 1002)
           .add("title", "Unit test step 3")
           .add("prompt", "test step")
           .add("transcript", "")
           .add("shuffle_words", 0)
           .add("record", 0)
           .add("max_seconds", 30)
           .add("suppress_next", 0)
           .add("next_delay_seconds", 0)
           .add("suppress_rerecord", 1)
           .add("image","")
           .add("countdown_seconds", 0)
           .add("step_count", 0)
           .build())
      .build();

    try {
      try {
        l.updateElicitationTaskSteps(taskId*2, steps);
        fail("Should fail invalid task ID " + (taskId*2));
      } catch (Exception x) {
        System.out.println(x.getMessage());
      }
      
    } finally {
      // update it so it has no steps (so we can delete it)
      IO.Copy(noSteps, json);
      l.elicitatationTaskUpload(json);
      // delete it
      try {
        l.deleteElicitationTask(taskId);
       } catch(Exception exception) {
        System.out.println("Could now delete task " + taskId + ": "+exception);
      }
    }
  }
  
  /**
   * Directory for text files.
   * @see #getDir()
   * @see #setDir(File)
   */
  protected File fDir;
  /**
   * Getter for {@link #fDir}: Directory for text files.
   * @return Directory for text files.
   */
  public File getDir() { 
    if (fDir == null) {
      try {
        URL urlThisClass = getClass().getResource(getClass().getSimpleName() + ".class");
        File fThisClass = new File(urlThisClass.toURI());
        fDir = fThisClass.getParentFile();
      } catch(Throwable t) {
        System.out.println("" + t);
      }
    }
    return fDir; 
  }
  /**
   * Setter for {@link #fDir}: Directory for text files.
   * @param fNewDir Directory for text files.
   */
  public void setDir(File fNewDir) { fDir = fNewDir; }

  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.admin.elicit.TestSteps");
  }
}
