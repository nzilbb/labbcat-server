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
public class TestResources {
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
  
  /** Ensure elicitation task resources can be read and updated. */
  @Test public void readUpdate() throws Exception {
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
      task = l.createElicitationTask(task);
      taskId = task.getTaskId();
      
      // resources should exist
      ElicitationTaskResource[] resources = l.readElicitationTaskResources(taskId);
      assertNotNull("resources returned", resources);
      assertTrue("multiple resources returned",
                 resources.length > 1);
      for (ElicitationTaskResource r : resources) {
        assertEquals("resource is for correct task " + r,
                     taskId, r.getTaskId());
      }

      // update one
      ElicitationTaskResource resource = resources[0];
      resource.setMessage("unit-test");
      ElicitationTaskResource updatedResource = l.updateElicitationTaskResource(
        resources[0]);
      assertNotNull("resource returned", updatedResource);
      assertEquals("Message updated",
                   "unit-test", updatedResource.getMessage());

      // check it was updated in the database
      resources = l.readElicitationTaskResources(taskId);
      for (ElicitationTaskResource r : resources) {
        if (r.getResourceId().equals(resource.getResourceId())) {
          assertEquals("Message is correct",
                       "unit-test", r.getMessage());
          break;
        }
      }

      // delete task
      l.deleteElicitationTask(task);
      
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

  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.admin.elicit.TestResources");
  }
}
