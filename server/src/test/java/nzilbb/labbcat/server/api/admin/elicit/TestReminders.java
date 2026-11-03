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
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
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
public class TestReminders {
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
  
  /** Ensure elicitation task reminder CRUD operations are supported. */
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
      .setTaskName("unit-test-reminders")
      .setDescription("Temporary task for unit testing")
      .setCorpusName(corpusName)
      .setTranscriptType(transcriptType)
      .setPreamble("Temporary task for unit testing");
    
    try {
      ElicitationTask newTask = l.createElicitationTask(task);
      assertNotNull("Task returned", newTask);
      taskId = newTask.getTaskId();

      JsonObject newReminder = Json.createObjectBuilder()
        .add("task_id", taskId)
        .add("label", "Test reminder")
        .add("participant_pattern", "")
        .add("reminder_day", "daily")
        .add("reminder_time", "10:30") // minutes accuracy is ok
        .add("from_day", 1)
        .add("to_day", 14)
        .build();
      
      // create
      HttpRequestPost create = l.post("api/admin/elicit/reminders/"+taskId)
        .setHeader("Accept", "application/json");
      Response response = new Response(create.post(newReminder), false);      
      response.checkForErrors();
      JsonObject createdReminder = (JsonObject)response.getModel();
      assertNotNull("Object returned", createdReminder);
      assertEquals("task_id correct",
                   newReminder.getInt("task_id"), createdReminder.getInt("task_id"));
      assertTrue("reminder_id returned", createdReminder.containsKey("reminder_id"));
      int reminderId = createdReminder.getInt("reminder_id");
      assertEquals("label correct",
                   newReminder.getString("label"), createdReminder.getString("label"));
      assertEquals("participant_pattern correct",
                   newReminder.getString("participant_pattern"),
                   createdReminder.getString("participant_pattern"));
      assertEquals("reminder_day correct",
                   newReminder.getString("reminder_day"),
                   createdReminder.getString("reminder_day"));
      assertEquals("reminder_time correct",
                   newReminder.getString("reminder_time"),
                   createdReminder.getString("reminder_time"));
      assertEquals("from_day correct",
                   newReminder.getInt("from_day"), createdReminder.getInt("from_day"));
      assertEquals("to_day correct",
                   newReminder.getInt("to_day"), createdReminder.getInt("to_day"));

      // read
      HttpRequestGet read = l.get("api/admin/elicit/reminders/"+taskId)
        .setHeader("Accept", "application/json");
      response = new Response(read.get(), false);      
      response.checkForErrors();
      JsonArray reminders = (JsonArray)response.getModel();
      assertNotNull("Array returned", reminders);
      assertEquals("One reminder", 1, reminders.size());
      JsonObject readReminder = reminders.getJsonObject(0);
      assertEquals("task_id correct",
                   newReminder.getInt("task_id"), readReminder.getInt("task_id"));
      assertEquals("reminder_id correct",
                   reminderId, readReminder.getInt("reminder_id"));
      assertEquals("label correct",
                   newReminder.getString("label"), readReminder.getString("label"));
      assertEquals("participant_pattern correct",
                   newReminder.getString("participant_pattern"),
                   readReminder.getString("participant_pattern"));
      assertEquals("reminder_day correct",
                   newReminder.getString("reminder_day"),
                   readReminder.getString("reminder_day"));
      assertEquals("reminder_time correct",
                   newReminder.getString("reminder_time")+":00", // database returns seconds
                   readReminder.getString("reminder_time"));
      assertEquals("from_day correct",
                   newReminder.getInt("from_day"), readReminder.getInt("from_day"));
      assertEquals("to_day correct",
                   newReminder.getInt("to_day"), readReminder.getInt("to_day"));

      // update
      JsonObject updatedReminder = Json.createObjectBuilder()
        .add("task_id", taskId)
        .add("reminder_id", reminderId)
        .add("label", "Test reminder edited")
        .add("participant_pattern", "test.*")
        .add("reminder_day", "monday")
        .add("reminder_time", "23:00:00") // seconds accuracy is ok
        .add("from_day", 15)
        .add("to_day", 28)
        .build();
      
      // create
      HttpRequestPost update = l.put("api/admin/elicit/reminders/"+taskId+"/"+reminderId)
        .setHeader("Accept", "application/json");
      response = new Response(update.post(updatedReminder), false);      
      response.checkForErrors();
      JsonObject savedReminder = (JsonObject)response.getModel();
      assertNotNull("Object returned", savedReminder);
      assertEquals("task_id correct",
                   updatedReminder.getInt("task_id"), savedReminder.getInt("task_id"));
      assertEquals("reminder_id correct",
                   reminderId, savedReminder.getInt("reminder_id"));
      assertEquals("label correct",
                   updatedReminder.getString("label"), savedReminder.getString("label"));
      assertEquals("participant_pattern correct",
                   updatedReminder.getString("participant_pattern"),
                   savedReminder.getString("participant_pattern"));
      assertEquals("reminder_day correct",
                   updatedReminder.getString("reminder_day"),
                   savedReminder.getString("reminder_day"));
      assertEquals("reminder_time correct",
                   updatedReminder.getString("reminder_time"),
                   savedReminder.getString("reminder_time"));
      assertEquals("from_day correct",
                   updatedReminder.getInt("from_day"), savedReminder.getInt("from_day"));
      assertEquals("to_day correct",
                   updatedReminder.getInt("to_day"), savedReminder.getInt("to_day"));

      // delete
      HttpRequestPost delete = l.delete(
        "api/admin/elicit/reminders/"+taskId+"/"+reminderId);
      response = new Response(delete.post(), false);
      response.checkForErrors(); // throws a StoreException on error

      read = l.get("api/admin/elicit/reminders/"+taskId)
        .setHeader("Accept", "application/json");
      response = new Response(read.get(), false);      
      response.checkForErrors();
      reminders = (JsonArray)response.getModel();
      assertNotNull("Array returned", reminders);
      assertEquals("No reminders", 0, reminders.size());
      
    } finally { 
      l.setVerbose(false);
      // ensure it's not there
      if (taskId >= 0) {
        try {
          l.deleteElicitationTask(taskId);
        } catch(Exception exception) {}
      }
    }
  }

  /** Ensure elicitation task reminder creation, update, and deletion is
   * correctly validated. */
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
      .setTaskName("unit-test-reminders")
      .setDescription("Temporary task for unit testing")
      .setCorpusName(corpusName)
      .setTranscriptType(transcriptType)
      .setPreamble("Temporary task for unit testing");
    
    try {
      ElicitationTask newTask = l.createElicitationTask(task);
      assertNotNull("Task returned", newTask);
      taskId = newTask.getTaskId();

      try {
        JsonObject newReminder = Json.createObjectBuilder()
          .add("label", "Test reminder")
          .add("participant_pattern", "")
          .add("reminder_day", "daily")
          .add("reminder_time", "10:30")
          .add("from_day", 1)
          .add("to_day", 14)
          .build();
        
        // create
        HttpRequestPost create = l.post("api/admin/elicit/reminders/"+taskId)
          .setHeader("Accept", "application/json");
        Response response = new Response(create.post(newReminder), false);      
        response.checkForErrors();
        fail("Reminder with no task_id should fail");
      } catch (Exception x) {
        System.out.println(x.getMessage());
      }
      
      try {
        JsonObject newReminder = Json.createObjectBuilder()
          .add("task_id", taskId)
          .add("label", "Test reminder")
          .add("participant_pattern", "[") // invalid regular expression
          .add("reminder_day", "daily")
          .add("reminder_time", "10:30")
          .add("from_day", 1)
          .add("to_day", 14)
          .build();
        
        // create
        HttpRequestPost create = l.post("api/admin/elicit/reminders/"+taskId)
          .setHeader("Accept", "application/json");
        Response response = new Response(create.post(newReminder), false);      
        response.checkForErrors();
        fail("Reminder with invalid regular expression should fail");
      } catch (Exception x) {
        System.out.println(x.getMessage());
      }
      
       try {
        JsonObject newReminder = Json.createObjectBuilder()
          .add("task_id", taskId)
          .add("label", "Test reminder")
          .add("participant_pattern", "")
          .add("reminder_day", "invalid") // not a valid value
          .add("reminder_time", "10:30")
          .add("from_day", 1)
          .add("to_day", 14)
          .build();
        
        // create
        HttpRequestPost create = l.post("api/admin/elicit/reminders/"+taskId)
          .setHeader("Accept", "application/json");
        Response response = new Response(create.post(newReminder), false);      
        response.checkForErrors();
        fail("Reminder with invalid reminder_day should fail");
      } catch (Exception x) {
        System.out.println(x.getMessage());
      }

      try {
        JsonObject newReminder = Json.createObjectBuilder()
          .add("task_id", taskId)
          .add("label", "Test reminder")
          .add("participant_pattern", "")
          .add("reminder_day", "daily")
          .add("reminder_time", "10 o'clock") // invalid time
          .add("from_day", 1)
          .add("to_day", 14)
          .build();
        
        // create
        HttpRequestPost create = l.post("api/admin/elicit/reminders/"+taskId)
          .setHeader("Accept", "application/json");
        Response response = new Response(create.post(newReminder), false);      
        response.checkForErrors();
        fail("Reminder with invalid time should fail");
      } catch (Exception x) {
        System.out.println(x.getMessage());
      }
      
      try {
        JsonObject newReminder = Json.createObjectBuilder()
          .add("task_id", taskId)
          .add("label", "Test reminder")
          .add("participant_pattern", "")
          .add("reminder_day", "daily")
          .add("reminder_time", "10:30")
          .add("from_day", 14) // from after to
          .add("to_day", 1)
          .build();
        
        // create
        HttpRequestPost create = l.post("api/admin/elicit/reminders/"+taskId)
          .setHeader("Accept", "application/json");
        Response response = new Response(create.post(newReminder), false);      
        response.checkForErrors();
        fail("Reminder with from_day after to_day should fail");
      } catch (Exception x) {
        System.out.println(x.getMessage());
      }
    } finally { 
      l.setVerbose(false);
      // ensure it's not there
      if (taskId >= 0) {
        try {
          l.deleteElicitationTask(taskId);
        } catch(Exception exception) {}
      }
    }
  }

  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.admin.elicit.TestReminders");
  }
}
