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
import java.util.Iterator;
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
public class TestValidation {
  static String labbcatUrl = "http://localhost:8080/labbcat/";
  static String username = "labbcat";
  static String password = "labbcat";
  static LabbcatAdmin l;
  static int taskId;
  static File someSteps;
  static File noSteps;
  static File json;

  @BeforeClass public static void setBaseUrlCreateTask() throws Exception {

    try {
      l = new LabbcatAdmin(labbcatUrl, username, password);
      l.setBatchMode(true);
    } catch(MalformedURLException exception) {
      fail("Could not create Labbcat object");
    }
    someSteps = new File(
      new File(getDir(), "task"), "nonempty-unit-test-task.json");
    assertTrue("json file exists " + someSteps.getPath(), someSteps.exists());
    noSteps = new File(
      new File(getDir(), "task"), "empty-unit-test-task.json");
    assertTrue("json file exists " + noSteps.getPath(), noSteps.exists());
    json = new File(getDir(), "unit-test-task.json");
    IO.Copy(someSteps, json);
    
    // upload the task
    taskId = l.elicitatationTaskUpload(json);
  }
  @AfterClass public static void deleteTask() throws Exception {
      l.setVerbose(false);
      // update it so it has no steps (so we can delete it)
      IO.Copy(noSteps, json);
      try {
        int emptyTaskId = l.elicitatationTaskUpload(json);
      } catch(Exception exception) {
        System.out.println("Could not reupload " + taskId + ": "+exception);
      }
      // delete it
      try {
        l.deleteElicitationTask(taskId);
      } catch(Exception exception) {
        System.out.println("Could not delete task " + taskId + ": "+exception);
      }
  }
  
  /** Ensure elicitation task transcript attribute validation scripts can be
   * read and updated.*/
  @Test public void transcriptAttributeRU() throws Exception {
    // start with none
    String emptyScript
      = l.readElicitedTranscriptAttributeValidation(taskId,"language");
    assertEquals("There is no script", "", emptyScript);
    
    // update
    String script = "isValid();";
    String updatedScript
      = l.updateElicitedTranscriptAttributeValidation(taskId,"language",script);
    assertEquals("Returned script correct", script, updatedScript);

    // read it
    updatedScript = l.readElicitedTranscriptAttributeValidation(taskId,"language");
    assertEquals("The script has been updated", script, updatedScript);
    
    // delete
    updatedScript = l.updateElicitedTranscriptAttributeValidation(taskId,"language","");
    assertEquals("Returned script correct", "", updatedScript);
    
    // read it
    updatedScript = l.readElicitedTranscriptAttributeValidation(taskId,"language");
    assertEquals("The script has been removed", "", updatedScript);
    
    String noScript = l.readElicitedTranscriptAttributeValidation(taskId, "nonexistent");
    assertNull("Null when attribute not found", noScript);

    noScript = l.readElicitedTranscriptAttributeValidation(taskId*taskId, "language");
    assertNull("Null when task not found", noScript);

  }
  
  /** Ensure elicitation task participant attribute validation scripts can be
   * read and updated.*/
  @Test public void participantAttributeRU() throws Exception {
    // start with none
    String emptyScript
      = l.readElicitedParticipantAttributeValidation(taskId,"gender");
    assertEquals("There is no script", "", emptyScript);
    
    // update
    String script = "isValid();";
    String updatedScript
      = l.updateElicitedParticipantAttributeValidation(taskId,"gender",script);
    assertEquals("Returned script correct", script, updatedScript);

    // read it
    updatedScript = l.readElicitedParticipantAttributeValidation(taskId,"gender");
    assertEquals("The script has been updated", script, updatedScript);
    
    // delete
    updatedScript = l.updateElicitedParticipantAttributeValidation(taskId,"gender","");
    assertEquals("Returned script correct", "", updatedScript);
    
    // read it
    updatedScript = l.readElicitedParticipantAttributeValidation(taskId,"gender");
    assertEquals("The script has been removed", "", updatedScript);
    
    String noScript = l.readElicitedParticipantAttributeValidation(taskId, "nonexistent");
    assertNull("Null when attribute not found", noScript);

    noScript = l.readElicitedParticipantAttributeValidation(taskId*taskId, "gender");
    assertNull("Null when task not found", noScript);

  }
  
  /**
   * Directory for text files.
   * @see #getDir()
   * @see #setDir(File)
   */
  protected static File fDir;
  /**
   * Getter for {@link #fDir}: Directory for text files.
   * @return Directory for text files.
   */
  public static File getDir() { 
    if (fDir == null) {
      try {
        URL urlThisClass = TestValidation.class.getResource(
          TestValidation.class.getSimpleName() + ".class");
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
  public static void setDir(File fNewDir) { fDir = fNewDir; }
  
  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.admin.elicit.TestValidation");
  }
}
