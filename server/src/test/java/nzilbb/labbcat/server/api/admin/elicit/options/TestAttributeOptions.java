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

package nzilbb.labbcat.server.api.admin.elicit.options;
	      
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
public class TestAttributeOptions {
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
      new File(getDir().getParentFile(), "task"), "nonempty-unit-test-task.json");
    assertTrue("json file exists " + someSteps.getPath(), someSteps.exists());
    noSteps = new File(
      new File(getDir().getParentFile(), "task"), "empty-unit-test-task.json");
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
  
  /** Ensure elicitation task transcript attribute options can be
   * created, read, updated, and deleted.*/
  @Test public void transcriptAttributeCRUD() throws Exception {
    // start with none
    ElicitationTaskAttributeOption[] options =
      l.readElicitationTaskTranscriptAttributeOptions(taskId, "language");
    assertEquals("There are no options " + Arrays.asList(options),
                 0, options.length);
    
    // create
    ElicitationTaskAttributeOption en = new ElicitationTaskAttributeOption()
      .setTaskId(taskId)
      .setAttribute("language")
      .setValue("en")
      .setDescription("Inglés");
    ElicitationTaskAttributeOption option =
      l.createElicitationTaskTranscriptAttributeOption(en);
    assertNotNull("New option returned", option);
    assertEquals("Task ID correct", en.getTaskId(), option.getTaskId());
    assertEquals("Attribute correct", en.getAttribute(), option.getAttribute());
    assertEquals("Value correct", en.getValue(), option.getValue());
    assertEquals("Description correct", en.getDescription(), option.getDescription());
      
    // read
    options = l.readElicitationTaskTranscriptAttributeOptions(taskId, "language");
    assertEquals("There is one option " + Arrays.asList(options),
                 1, options.length);
    assertEquals("Task ID correct", en.getTaskId(), options[0].getTaskId());
    assertEquals("Attribute correct", en.getAttribute(), options[0].getAttribute());
    assertEquals("Value correct", en.getValue(), options[0].getValue());
    assertEquals("Description correct",
                 en.getDescription(), options[0].getDescription());

    // update
    en.setDescription("English");
    option = l.updateElicitationTaskTranscriptAttributeOption(en);
    assertNotNull("New option returned", option);
    assertEquals("Task ID correct", en.getTaskId(), option.getTaskId());
    assertEquals("Attribute correct", en.getAttribute(), option.getAttribute());
    assertEquals("Value correct", en.getValue(), option.getValue());
    assertEquals("Description correct", en.getDescription(), option.getDescription());

    // delete
    l.deleteElicitationTaskTranscriptAttributeOption(en);
    options = l.readElicitationTaskTranscriptAttributeOptions(taskId, "language");
    assertEquals("There are no longer any options " + Arrays.asList(options),
                 0, options.length);
  }

  /** Ensure elicitation task participant attribute options can be
   * created, read, updated, and deleted.*/
  @Test public void participantAttributeCRUD() throws Exception {
    // start with none
    ElicitationTaskAttributeOption[] options
      = l.readElicitationTaskTranscriptAttributeOptions(taskId, "participant_gender");
    assertEquals("There are no options " + Arrays.asList(options),
                 0, options.length);

    // create
    ElicitationTaskAttributeOption male = new ElicitationTaskAttributeOption()
      .setTaskId(taskId)
      .setAttribute("gender")
      .setValue("M")
      .setDescription("Masculino");
    ElicitationTaskAttributeOption option
      = l.createElicitationTaskParticipantAttributeOption(male);
    assertNotNull("New option returned", option);
    assertEquals("Task ID correct", male.getTaskId(), option.getTaskId());
    assertEquals("Attribute correct", male.getAttribute(), option.getAttribute());
    assertEquals("Value correct", male.getValue(), option.getValue());
    assertEquals("Description correct", male.getDescription(), option.getDescription());

    // read
    options = l.readElicitationTaskParticipantAttributeOptions(taskId, "gender");
    assertEquals("There is one option " + Arrays.asList(options),
                 1, options.length);
    assertEquals("Task ID correct", male.getTaskId(), options[0].getTaskId());
    assertEquals("Attribute correct", male.getAttribute(), options[0].getAttribute());
    assertEquals("Value correct", male.getValue(), options[0].getValue());
    assertEquals("Description correct",
                 male.getDescription(), options[0].getDescription());

    // update
    male.setDescription("male");
    option = l.updateElicitationTaskParticipantAttributeOption(male);
    assertNotNull("New option returned", option);
    assertEquals("Task ID correct", male.getTaskId(), option.getTaskId());
    assertEquals("Attribute correct", male.getAttribute(), option.getAttribute());
    assertEquals("Value correct", male.getValue(), option.getValue());
    assertEquals("Description correct", male.getDescription(), option.getDescription());

    // delete
    l.deleteElicitationTaskParticipantAttributeOption(male);
    options = l.readElicitationTaskTranscriptAttributeOptions(taskId, "gender");
    assertEquals("There are no longer any options " + Arrays.asList(options),
                 0, options.length);

  }

  /** Ensure elicitation task attribute options are properly validated .*/
  @Test public void validation() throws Exception {
    ElicitationTaskAttributeOption en = new ElicitationTaskAttributeOption()
      .setTaskId(taskId)
      .setAttribute("language")
      .setValue("en")
      .setDescription("Inglés");
    ElicitationTaskAttributeOption male = new ElicitationTaskAttributeOption()
      .setTaskId(taskId)
      .setAttribute("gender")
      .setValue("M")
      .setDescription("Masculino");
    try {
      ElicitationTaskAttributeOption option =
        l.createElicitationTaskParticipantAttributeOption(en);
      fail("Should fail to create an option for an invalid participant attribute: "+option);
    } catch (Exception x) {
      System.out.println(x.getMessage());
    }
    try {
      ElicitationTaskAttributeOption option =
        l.createElicitationTaskTranscriptAttributeOption(male);
      fail("Should fail to create an option for an invalid transcript attribute: "+option);
    } catch (Exception x) {
      System.out.println(x.getMessage());
    }
    try {
      male.setValue("nonexistent");
      ElicitationTaskAttributeOption option =
        l.createElicitationTaskParticipantAttributeOption(male);
      fail("Should fail to create an option for an invalid participant attribute value: "
           + option);
    } catch (Exception x) {
      System.out.println(x.getMessage());
    } finally {
      male.setValue("M");
    }
    try {
      en.setValue("nonexistent");
      ElicitationTaskAttributeOption option =
        l.createElicitationTaskTranscriptAttributeOption(en);
      fail("Should fail to create an option for an invalid transcript attribute value: "
           + option);
    } catch (Exception x) {
      System.out.println(x.getMessage());
    } finally {
      en.setValue("en");
    }
    try {
      male.setAttribute("nonexistent");
      ElicitationTaskAttributeOption option =
        l.createElicitationTaskParticipantAttributeOption(male);
      fail("Should fail to create an option for an invalid participant attribute: "
           + option);
    } catch (Exception x) {
      System.out.println(x.getMessage());
    } finally {
      male.setAttribute("gender");
    }
    try {
      en.setAttribute("nonexistent");
      ElicitationTaskAttributeOption option =
        l.createElicitationTaskTranscriptAttributeOption(en);
      fail("Should fail to create an option for an invalid transcript attribute: "
           + option);
    } catch (Exception x) {
      System.out.println(x.getMessage());
    } finally {
      en.setAttribute("language");
    }
    try {
      male.setTaskId(taskId*100);
      ElicitationTaskAttributeOption option =
        l.createElicitationTaskParticipantAttributeOption(male);
      fail("Should fail to create an option for an invalid task ID: "
           + option);
    } catch (Exception x) {
      System.out.println(x.getMessage());
    } finally {
      male.setTaskId(taskId);
    }
    try {
      en.setTaskId(taskId*100);
      ElicitationTaskAttributeOption option =
        l.createElicitationTaskTranscriptAttributeOption(en);
      fail("Should fail to create an option for an invalid task: "
           + option);
    } catch (Exception x) {
      System.out.println(x.getMessage());
    } finally {
      en.setTaskId(taskId);
    }
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
        URL urlThisClass = TestAttributeOptions.class.getResource(
          TestAttributeOptions.class.getSimpleName() + ".class");
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
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.admin.elicit.options.TestAttributeOptions");
  }
}
