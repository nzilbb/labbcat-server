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

package nzilbb.labbcat.server.api.edit.participants.layers;
	      
import org.junit.*;
import static org.junit.Assert.*;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.json.JsonObject;
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
import nzilbb.labbcat.http.HttpRequestPost;
import nzilbb.labbcat.http.HttpRequestPostMultipart;
import nzilbb.labbcat.model.*;
import nzilbb.labbcat.model.Match;

/**
 * These tests assume that there is a working LaBB-CAT instance with the latest version of
 * nzilbb.labbcat.server.jar installed.
 */
public class TestRegenerate
{
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
  
  /**
   * Test /api/edit/participants/layers/regenerate endpoint will regenerate layers
   * using IDs specified by parameter.
   */
  @Test public void regenerateByParameter()
    throws Exception {
    
    String[] ids = l.getParticipantIds();
    assertTrue("getParticipantIds: At leas two IDs are returned",
               ids.length >= 2);
    try {
      
      // regenerate a layer
      HttpRequestPost request = l.post("api/edit/participants/layers/regenerate")
        .setParameter("layerId", "orthography")
        .setParameter("id", ids[0])
        .setParameter("id", ids[1]);
      Response response = new Response(request.post(), false);
      response.checkForErrors();
      JsonObject model = (JsonObject)response.getModel();
      String threadId = model.getString("threadId");
      assertNotNull("Thread ID returned",
                    threadId);
      TaskStatus task = l.taskStatus(threadId, true, false);
      System.out.println(task.getLog());
      l.cancelTask(threadId);
      l.releaseTask(threadId);
      
    } finally {
      l.setVerbose(false);
    }
  }
    
  /**
   * Test /api/edit/participants/layers/regenerate endpoint will regenerate layers
   * using IDs specified by an uploaded file.
   */
  @Test public void regenerateByFile()
    throws Exception {
    
    String[] ids = l.getParticipantIds();
    assertTrue("getParticipantIds: At leas two IDs are returned",
               ids.length >= 2);
    File listFile = new File(getDir().getParentFile(), "participants.txt");
    try (PrintWriter listWriter = new PrintWriter(listFile)) {
      listWriter.println(ids[0]);
      listWriter.println(ids[1]);
    }
    
    try {
      
      // regenerate a layer
      HttpRequestPostMultipart request = l.postMultipart(
        "api/edit/participants/layers/regenerate")
        .setParameter("layerId", "orthography")
        .setParameter("ids", listFile);
      Response response = new Response(request.post(), false);
      response.checkForErrors();
      JsonObject model = (JsonObject)response.getModel();
      String threadId = model.getString("threadId");
      assertNotNull("Thread ID returned",
                    threadId);
      TaskStatus task = l.taskStatus(threadId, true, false);
      System.out.println(task.getLog());
      l.cancelTask(threadId);
      l.releaseTask(threadId);
      
    } finally {
      l.setVerbose(false);
    }
  }
  
  /**
   * Test /api/edit/participants/layers/regenerate endpoint parameter validation.
   */
  @Test public void validation()
    throws Exception {
    
    String[] ids = l.getTranscriptIds();
    assertTrue("getTranscriptIds: At leas two IDs are returned",
               ids.length >= 2);
    try {
      HttpRequestPost request = l.post("api/edit/participants/layers/regenerate")
        .setParameter("id", ids[0])
        .setParameter("id", ids[1]);
      try {
        Response response = new Response(request.post(), false);
        response.checkForErrors();
        fail("Should fail when no layerId is specified.");
      } catch(Exception exception) {
        System.out.println(exception.getMessage());
      }

      request = l.post("api/edit/participants/layers/regenerate")
        .setParameter("layerId", "nonexistent")
        .setParameter("id", ids[0])
        .setParameter("id", ids[1]);
      try {
        Response response = new Response(request.post(), false);
        response.checkForErrors();
        fail("Should fail when a nonexistent layerId is specified.");
      } catch(Exception exception) {
        System.out.println(exception.getMessage());
      }

      request = l.post("api/edit/participants/layers/regenerate")
        .setParameter("layerId", "")
        .setParameter("id", ids[0])
        .setParameter("id", ids[1]);
      try {
        Response response = new Response(request.post(), false);
        response.checkForErrors();
        fail("Should fail when a blank layerId is specified.");
      } catch(Exception exception) {
        System.out.println(exception.getMessage());
      }

      request = l.post("api/edit/participants/layers/regenerate")
        .setParameter("layerId", "turn")
        .setParameter("id", ids[0])
        .setParameter("id", ids[1]);
      try {
        Response response = new Response(request.post(), false);
        response.checkForErrors();
        fail("Should fail when a non-generable layerId is specified.");
      } catch(Exception exception) {
        System.out.println(exception.getMessage());
      }

      request = l.post("api/edit/participants/layers/regenerate")
        .setParameter("layerId", "orthography");
      try {
        Response response = new Response(request.post(), false);
        response.checkForErrors();
        fail("Should fail when no id/ids are specified.");
      } catch(Exception exception) {
        System.out.println(exception.getMessage());
      }
      
    } finally {
      l.setVerbose(false);
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
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.edit.participants.layers.TestRegenerate");
  }
}
