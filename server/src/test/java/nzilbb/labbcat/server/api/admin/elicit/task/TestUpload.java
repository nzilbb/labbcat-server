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

package nzilbb.labbcat.server.api.admin.elicit.task;
	      
import org.junit.*;
import static org.junit.Assert.*;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.json.JsonObject;
import javax.json.JsonArray;
import nzilbb.ag.Anchor;
import nzilbb.ag.Annotation;
import nzilbb.ag.Constants;
import nzilbb.ag.Graph;
import nzilbb.ag.Layer;
import nzilbb.ag.MediaFile;
import nzilbb.ag.MediaTrackDefinition;
import nzilbb.ag.PermissionException;
import nzilbb.ag.StoreException;
import nzilbb.ag.serialize.SerializationDescriptor;
import nzilbb.util.IO;
import nzilbb.labbcat.LabbcatAdmin;
import nzilbb.labbcat.Response;
import nzilbb.labbcat.ResponseException;
import nzilbb.labbcat.http.HttpRequestGet;
import nzilbb.labbcat.http.HttpRequestPost;
import nzilbb.labbcat.http.HttpRequestPostMultipart;
import nzilbb.labbcat.model.*;
import nzilbb.labbcat.model.Match;

/**
 * These tests assume that there is a working LaBB-CAT instance with the latest version of
 * nzilbb.labbcat.server.jar installed.
 */
public class TestUpload {
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
  
  /** Ensure elicitation task upload works. */
  @Test public void elicitationTaskUpload() throws Exception {
    File someSteps = new File(getDir(), "nonempty-unit-test-task.json");
    assertTrue("json file exists " + someSteps.getPath(), someSteps.exists());
    File noSteps = new File(getDir(), "empty-unit-test-task.json");
    assertTrue("json file exists " + noSteps.getPath(), noSteps.exists());
    File json = new File(getDir(), "unit-test-task.json");
    IO.Copy(someSteps, json);
    
    // upload the task
    int taskId = l.elicitatationTaskUpload(json);
    try {      
      
      ElicitationTask[] tasks = l.readElicitationTasks();
      // ensure the task exists
      ElicitationTask foundTask = null;
      for (ElicitationTask t : tasks) {
        if (t.getTaskId() == taskId) {
          foundTask = t;
          break;
        }
      }
      assertNotNull("Task was added", foundTask);
      assertEquals("Name correct",
                   "unit-test-task", foundTask.getTaskName());
      assertEquals("Description correct",
                   "This task is for automated testing purposes.",
                   foundTask.getDescription());
      assertEquals("Preamble correct",
                   "<p>Preamble</p>", foundTask.getPreamble());
      assertEquals("Consent correct",
                   "<p>Consent</p>", foundTask.getConsent());
      assertEquals("EndUrl correct",
                   "https://example.com/", foundTask.getEndUrl());

      // update it so it has no steps (so we can delete it)
      IO.Copy(noSteps, json);
      assertTrue("Can rename empty task file "
                 + noSteps.getPath() + " -> " + json.getPath(),
                 json.exists());
      int emptyTaskId = l.elicitatationTaskUpload(json);
      assertEquals("Updated task_id is correct",
                   taskId, emptyTaskId);

    } finally {
      // delete it
      try {
        l.deleteElicitationTask(taskId);
       } catch(Exception exception) {
        System.out.println("Could now delete task " + taskId + ": "+exception);
      }
    }
  }

  /** Ensure elicitation task upload validation works. */
  @Test public void elicitationTaskUploadValidation() throws Exception {
    
    try {
      int taskId = l.elicitatationTaskUpload(null);
      fail("Should fail with no file but created task " + taskId);
    } catch (Exception x) {
      System.out.println(x.getMessage());
    }
    
    try {
      File invalidFile = new File(
        getDir().getParentFile(), "nzilbb.labbcat.server.test.svg");
      int taskId = l.elicitatationTaskUpload(invalidFile);
      fail("Should fail with invalid file but created task " + taskId);
    } catch (Exception x) {
      System.out.println(x.getMessage());
    }
    
    File someSteps = new File(getDir(), "nonempty-unit-test-task.json");
    File noTaskName = new File(getDir(), ".json");
    try {
      IO.Copy(someSteps, noTaskName);
      int taskId = l.elicitatationTaskUpload(noTaskName);
      fail("Should fail with file without task name but created task " + taskId);
    } catch (Exception x) {
      System.out.println(x.getMessage());
    } finally {
      noTaskName.delete();
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
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.admin.elicit.task.TestUpload");
  }
}
