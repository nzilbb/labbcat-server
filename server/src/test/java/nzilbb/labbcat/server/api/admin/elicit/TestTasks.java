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

/**
 * These tests assume that there is a working LaBB-CAT instance with the latest version of
 * nzilbb.labbcat.server.jar installed.
 */
public class TestTasks {
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
  
  /** Ensure elicitation task CRUD operations are supported, including tasks
      and resource strings. */
  @Test public void basicCRUD() throws Exception {
    Corpus[] corpora = l.readCorpora();
    assertTrue("readCorpora: Some IDs are returned", corpora.length > 0);
    String corpusName = corpora[0].getName();
    Layer transcriptTypeLayer = l.getLayer("transcript_type");
    assertTrue("transcript_type: Some valid labels are returned",
               transcriptTypeLayer.getValidLabels().size() > 0);
    Iterator<String> transcriptTypes = transcriptTypeLayer.getValidLabels()
      .keySet().iterator();
    String transcriptType = transcriptTypes.next();
    
    int taskId = -1;
    
    ElicitationTask task = new ElicitationTask()
      .setTaskName("unit-test")
      .setDescription("Temporary task for unit testing")
      .setCorpusName(corpusName)
      .setTranscriptType(transcriptType)
      .setPreamble("<p>This is a test task.</p>")
      .setConsent("<p>I consent to my recordings being used for research.</p>")
      .setEndUrl("https://l.canterbury.ac.nz/");
    
    try {
      ElicitationTask newTask = l.createElicitationTask(task);
      assertNotNull("Task returned", newTask);
      assertEquals("Name correct",
                   task.getTaskName(), newTask.getTaskName());
      assertEquals("Description correct",
                   task.getDescription(), newTask.getDescription());
      assertEquals("Preamble correct",
                   task.getPreamble(), newTask.getPreamble());
      assertEquals("Consent correct",
                   task.getConsent(), newTask.getConsent());
      assertEquals("EndUrl correct",
                   task.getEndUrl(), newTask.getEndUrl());
      assertEquals("corpus correct",
                   task.getCorpusName(), newTask.getCorpusName());
      assertEquals("transcript type correct",
                   task.getTranscriptType(), newTask.getTranscriptType());
      assertTrue("taskId set",
                 newTask.getTaskId() >= 0);
      taskId = newTask.getTaskId();
         
      ElicitationTask[] tasks = l.readElicitationTasks();
      // ensure the role exists
      ElicitationTask foundTask = null;
      for (ElicitationTask t : tasks) {
        if (t.getTaskId() == taskId) {
          foundTask = t;
          break;
        }
      }
      assertNotNull("Task was added", foundTask);
      assertEquals("Name correct",
                   task.getTaskName(), foundTask.getTaskName());
      assertEquals("Description correct",
                   task.getDescription(), foundTask.getDescription());
      assertEquals("Preamble correct",
                   task.getPreamble(), foundTask.getPreamble());
      assertEquals("Consent correct",
                   task.getConsent(), foundTask.getConsent());
      assertEquals("EndUrl correct",
                   task.getEndUrl(), foundTask.getEndUrl());
      assertEquals("corpus correct",
                   task.getCorpusName(), foundTask.getCorpusName());
      assertEquals("transcript type correct",
                   task.getTranscriptType(), foundTask.getTranscriptType());

      // update it
      String editedCorpusName = corpora.length > 1?corpora[1].getName():corpusName;
      String editedTranscriptType = transcriptTypes.hasNext()?transcriptTypes.next():transcriptType;
      ElicitationTask editedTask = new ElicitationTask()
        .setTaskId(taskId)
        .setTaskName("unit-test-changed")
        .setDescription("Temporary task for unit testing-changed")
        .setCorpusName(editedCorpusName)
        .setTranscriptType(editedTranscriptType)
        .setPreamble("<p>This is a test task that has been changed.</p>")
        .setConsent("<p>I don't consent to my recordings being used for research.</p>")
        .setEndUrl("https://l.canterbury.ac.nz");
      
      ElicitationTask updatedTask = l.updateElicitationTask(editedTask);
      assertEquals("taskId correct",
                   taskId, updatedTask.getTaskId());
      assertEquals("Name correct",
                   editedTask.getTaskName(), updatedTask.getTaskName());
      assertEquals("Description correct",
                   editedTask.getDescription(), updatedTask.getDescription());
      assertEquals("Preamble correct",
                   editedTask.getPreamble(), updatedTask.getPreamble());
      assertEquals("Consent correct",
                   editedTask.getConsent(), updatedTask.getConsent());
      assertEquals("EndUrl correct",
                   editedTask.getEndUrl(), updatedTask.getEndUrl());
      assertEquals("corpus correct",
                   editedTask.getCorpusName(), updatedTask.getCorpusName());
      assertEquals("transcript type correct",
                   editedTask.getTranscriptType(), updatedTask.getTranscriptType());

      // resources should exist
      ElicitationTaskResource[] resources = l.readElicitationTaskResources(taskId);
      assertNotNull("resources returned", resources);
      assertTrue("multiple resources returned",
                 resources.length > 1);

      // delete task
      l.deleteElicitationTask(foundTask);
      
      ElicitationTask[] tasksAfter = l.readElicitationTasks();
      // ensure the role no longer exists
      ElicitationTask foundAfter = null;
      for (ElicitationTask t : tasksAfter) {
        if (t.getTaskId() == taskId) {
          foundAfter = t;
          break;
        }
      }
      assertNull("Task is gone", foundAfter);

      // resources should be gone
      resources = l.readElicitationTaskResources(taskId);
      assertNotNull("resources returned", resources);
      assertTrue("list is empty", resources.length == 0);
      
    } finally {
      // ensure it's not there
      if (taskId >= 0) {
        try {
          l.deleteElicitationTask(taskId);
        } catch(Exception exception) {}
      }
    }
  }

  /** Ensure elicitation task creation, update, and deletion is correctly validated. */
  @Test public void validation() throws Exception {
    Corpus[] corpora = l.readCorpora();
    assertTrue("readCorpora: Some IDs are returned", corpora.length > 0);
    String corpusName = corpora[0].getName();
    Layer transcriptTypeLayer = l.getLayer("transcript_type");
    assertTrue("transcript_type: Some valid labels are returned",
               transcriptTypeLayer.getValidLabels().size() > 0);
    Iterator<String> transcriptTypes = transcriptTypeLayer.getValidLabels()
      .keySet().iterator();
    String transcriptType = transcriptTypes.next();
    
    int taskId = -1;
    
    ElicitationTask task = new ElicitationTask()
      .setTaskName("unit-test")
      .setDescription("Temporary task for unit testing")
      .setCorpusName(corpusName)
      .setTranscriptType(transcriptType)
      .setPreamble("<p>This is a test task.</p>")
      .setConsent("<p>I consent to my recordings being used for research.</p>")
      .setEndUrl("https://l.canterbury.ac.nz/");
    
    try {
      try {
        task.setTaskName("");
        ElicitationTask invalid = l.createElicitationTask(task);
        fail("Can't create a task with no name " + invalid);
      } catch(Exception exception) {
        System.out.println(exception.getMessage());
      }
      task.setTaskName("unit-test");
      
      try {
        task.setCorpusName("");
        ElicitationTask invalid = l.createElicitationTask(task);
        fail("Can't create a task with no corpus " + invalid);
      } catch(Exception exception) {
        System.out.println(exception.getMessage());
      }
      
      try {
        task.setCorpusName("nonexistent");
        ElicitationTask invalid = l.createElicitationTask(task);
        fail("Can't create a task with invalid corpus " + invalid);
      } catch(Exception exception) {
        System.out.println(exception.getMessage());
      }
      task.setCorpusName(corpusName);
      
      try {
        task.setTranscriptType("");
        ElicitationTask invalid = l.createElicitationTask(task);
        fail("Can't create a task with no transcript type " + invalid);
      } catch(Exception exception) {
        System.out.println(exception.getMessage());
      }
      
      try {
        task.setTranscriptType("nonexistent");
        ElicitationTask invalid = l.createElicitationTask(task);
        fail("Can't create a task with invalid transcript type " + invalid);
      } catch(Exception exception) {
        System.out.println(exception.getMessage());
      }
      task.setTranscriptType(transcriptType);
      
      task = l.createElicitationTask(task);
      assertNotNull("Task returned", task);
      taskId = task.getTaskId();
         
      try {
        l.createElicitationTask(task);
        fail("Can't create a task with existing name");
      } catch(Exception exception) {
        System.out.println(exception.getMessage());
      }
      
      try {
        task.setTaskName("");
        ElicitationTask invalid = l.updateElicitationTask(task);
        fail("Can't update a task to have no name " + invalid);
      } catch(Exception exception) {
        System.out.println(exception.getMessage());
      }
      task.setTaskName("unit-test");
      
      try {
        task.setCorpusName("");
        ElicitationTask invalid = l.updateElicitationTask(task);
        fail("Can't update a task to have no corpus " + invalid);
      } catch(Exception exception) {
        System.out.println(exception.getMessage());
      }
      
      try {
        task.setCorpusName("nonexistent");
        ElicitationTask invalid = l.updateElicitationTask(task);
        fail("Can't udpate a task to have invalid corpus " + invalid);
      } catch(Exception exception) {
        System.out.println(exception.getMessage());
      }
      task.setCorpusName(corpusName);
      
      try {
        task.setTranscriptType("");
        ElicitationTask invalid = l.updateElicitationTask(task);
        fail("Can't update a task to have no transcript type " + invalid);
      } catch(Exception exception) {
        System.out.println(exception.getMessage());
      }
      
      try {
        task.setTranscriptType("nonexistent");
        ElicitationTask invalid = l.updateElicitationTask(task);
        fail("Can't update a task to have invalid transcript type " + invalid);
      } catch(Exception exception) {
        System.out.println(exception.getMessage());
      }
      task.setTranscriptType(transcriptType);      
      
      // delete task
      l.deleteElicitationTask(task);
            
      try {
        ElicitationTask invalid = l.updateElicitationTask(task);
        fail("Can't update a deleted task " + invalid);
      } catch(Exception exception) {
        System.out.println(exception.getMessage());
      }
      try {
        // can't delete it again
        l.deleteElicitationTask(taskId);
        fail("Can't delete task that doesn't exist");
      } catch(Exception exception) {
        System.out.println(exception.getMessage());
      }
      
    } finally {
      // ensure it's not there
      if (taskId >= 0) {
        try {
          l.deleteElicitationTask(taskId);
        } catch(Exception exception) {}
      }
    }
  }

  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.admin.elicit.TestTasks");
  }
}
