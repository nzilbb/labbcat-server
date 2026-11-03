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

package nzilbb.labbcat.server.api.elicit;
	      
import org.junit.*;
import static org.junit.Assert.*;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import javax.json.JsonObject;
import javax.json.JsonValue;
import nzilbb.ag.Layer;
import nzilbb.ag.MediaFile;
import nzilbb.ag.serialize.SerializationDescriptor;
import nzilbb.labbcat.LabbcatAdmin;
import nzilbb.labbcat.Response;
import nzilbb.labbcat.ResponseException;
import nzilbb.labbcat.http.HttpRequestGet;
import nzilbb.labbcat.http.HttpRequestPostMultipart;

/**
 * These tests assume that there is a working LaBB-CAT instance with the latest version of
 * nzilbb.labbcat.server.jar installed.
 */
public class TestVerify {
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
   * Test /api/elicit/verify endpoint works with a successful upload.
   */
  @Test public void verifySuccess()
    throws Exception {

    File files = new File(getDir().getParentFile(), "edit");
    File transcript = new File(files, "nzilbb.labbcat.server.test.txt");
    String participantId = "UnitTester";

    try {
      
      String[] ids = l.getCorpusIds();
      // for (String id : ids) System.out.println("corpus " + id);
      assertTrue("There is at least one corpus", ids.length > 0);
      String corpus = ids[0];
      Layer typeLayer = l.getLayer("transcript_type");
      assertTrue("There is at least one transcript type", typeLayer.getValidLabels().size() > 0);
      String transcriptType = typeLayer.getValidLabels().keySet().iterator().next();
      
      // ensure transcript doesn't already exist
      try {
        l.deleteTranscript(transcript.getName());
      } catch(ResponseException exception) {}
      
      // upload a file
      HttpRequestPostMultipart request = l.postMultipart("api/elicit/upload")
        .setParameter("content-type", "application/json")
        .setParameter("todo", "new")
        .setParameter("auto", "true")
        .setParameter("transcript_type", transcriptType)
        .setParameter("corpus", corpus)
        .setParameter("episode", "TestVerify")
        .setParameter("uploadfile1_0", transcript);
      Response response = new Response(request.post(), false);
      response.checkForErrors();
      assertEquals("Correct status",
                   200, response.getHttpStatus());
      assertEquals("Transcript is in the store",
                   1, l.countMatchingTranscriptIds("id = '"+transcript.getName()+"'"));

      HttpRequestGet verify = l.get("api/elicit/verify")
        .setParameter("transcript_id", transcript.getName());
      response = new Response(verify.get(), false);
      response.checkForErrors();
      assertEquals("Correct status",
                   200, response.getHttpStatus());
      JsonObject model = (JsonObject)response.getModel();
      assertTrue("ag_id returned " + model,
                 model.containsKey("ag_id"));
      int ag_id = model.getInt("ag_id");
      assertTrue("ag_id is sensible " + model,
                 ag_id > 0);
      assertTrue("No errors returned",
                 response.getErrors().size() == 0);
    } finally {
      l.setVerbose(false);
      try {
        // delete transcript/participant
        for (MediaFile f : l.getEpisodeDocuments(transcript.getName())) { // docs
          l.deleteMedia(transcript.getName(), f.getName());
        }
        l.deleteTranscript(transcript.getName());
        l.deleteParticipant(participantId);
        
        // ensure the transcript/participant no longer exist
        assertEquals("Transcript has been deleted from the store",
                     0, l.countMatchingTranscriptIds("id = '"+transcript.getName()+"'"));
        assertEquals("Participant has been deleted from the store",
                     0, l.countMatchingParticipantIds("id = '"+participantId+"'"));
      } catch (Exception x) {
        System.err.println("Unexpectedly can't delete test transcript: " + x);
      }
    }    
  }  
  
  /**
   * Test /api/elicit/verify endpoint works with a failed upload.
   */
  @Test public void verifyFailure()
    throws Exception {

    try {
      HttpRequestGet verify = l.get("api/elicit/verify")
        .setParameter("transcript_id", "nonexistent.txt");
      Response response = new Response(verify.get(), false);
      try {
        response.checkForErrors();
        fail("Should fail error check as there are errors.");
      } catch (ResponseException x) {
        assertEquals("Correct status",
                     200, response.getHttpStatus());
        assertEquals("No model returned",
                     JsonValue.NULL, response.getModel());
        assertTrue("An error returned",
                   response.getErrors().size() > 0);
        System.out.println(""+response.getErrors());
      }
    } finally {
      l.setVerbose(false);
    }
  }  
  
  /**
   * Test /api/elicit/verify endpoint validated its parameter.
   */
  @Test public void validation()
    throws Exception {

    try {
      HttpRequestGet verify = l.get("api/elicit/verify"); // no parameter
      Response response = new Response(verify.get(), false);
      try {
        response.checkForErrors();
        fail("Should fail error check as there are errors.");
      } catch (ResponseException x) {
        assertEquals("Correct status",
                     400, response.getHttpStatus());
        System.out.println(x.getMessage());
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
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.admin.TestVerify");
  }
}
