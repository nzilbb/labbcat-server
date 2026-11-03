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
import nzilbb.util.IO;

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

  /**
   * Test /api/elicit/upload endpoint works with parameters used by the ElicitSpeech webapp.
   */
  @Test public void elicitSpeechUpload()
    throws Exception {

    File files = new File(getDir().getParentFile(), "edit");
    File transcript = new File(files, "nzilbb.labbcat.server.test.txt");
    String participantId = "UnitTester";
    File wav = new File(files, "nzilbb.labbcat.server.test.wav");
    File document = new File(files, "nzilbb.labbcat.server.test.doc");
    File pdf = new File(files, "nzilbb.labbcat.server.test.pdf");
    IO.Copy(document, pdf);
    assertTrue("Test transcript exists " + transcript.getPath(), transcript.exists());
    assertTrue("Test wav exists " + wav.getPath(), wav.exists());
    assertTrue("Test pdf exists " + pdf.getPath(), pdf.exists());

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
        .setParameter("episode", "TestUpload")
        .setParameter("uploadfile1_0", transcript)
        .setParameter("uploadmedia1", wav)
        .setParameter("doc", pdf);
      Response response = new Response(request.post(), false);
      response.checkForErrors();
      assertEquals("Correct status",
                   200, response.getHttpStatus());
      assertEquals("Transcript is in the store",
                   1, l.countMatchingTranscriptIds("id = '"+transcript.getName()+"'"));
      MediaFile[] media = l.getAvailableMedia(transcript.getName());
      assertTrue("Media is in the store",
                 media.length > 0);
      media = l.getEpisodeDocuments(transcript.getName());
      assertEquals("Doc is in the store "
                   + Arrays.stream(media).map(f->f.getName()).collect(Collectors.toList()),
                   1, media.length);
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
   * Test /api/elicit/upload endpoint works with minimal parameters.
   */
  @Test public void minimalUpload()
    throws Exception {

    File files = new File(getDir().getParentFile(), "edit");
    File transcript = new File(files, "nzilbb.labbcat.server.test.txt");
    String participantId = "UnitTester";
    assertTrue("Test transcript exists " + transcript.getPath(), transcript.exists());

    try {
      
      // ensure transcript doesn't already exist
      try {
        l.deleteTranscript(transcript.getName());
      } catch(ResponseException exception) {}
      
      // upload a file
      HttpRequestPostMultipart request = l.postMultipart("api/elicit/upload")
        // no transcript_type, corpus, episode, wav, nor doc
        .setParameter("transcript", transcript); // parameter name correct
      Response response = new Response(request.post(), false);
      response.checkForErrors();
      assertEquals("Correct status",
                   200, response.getHttpStatus());
      assertEquals("Transcript is in the store",
                   1, l.countMatchingTranscriptIds("id = '"+transcript.getName()+"'"));
      assertEquals("No media is in the store",
                   0, l.getAvailableMedia(transcript.getName()).length);
      assertEquals("No doc is in the store",
                   0, l.getEpisodeDocuments(transcript.getName()).length);
    } finally {
      l.setVerbose(false);
      try {
        // delete transcript/participant
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
   * Test /api/elicit/upload endpoint validates parameters correctly.
   */
  @Test public void validation()
    throws Exception {

    File files = new File(getDir().getParentFile(), "edit");
    File transcript = new File(files, "nzilbb.labbcat.server.test.txt");
    String participantId = "UnitTester";
    File wav = new File(files, "nzilbb.labbcat.server.test.wav");
    File document = new File(files, "nzilbb.labbcat.server.test.doc");
    File pdf = new File(files, "nzilbb.labbcat.server.test.pdf");
    IO.Copy(document, pdf);
    assertTrue("Test transcript exists " + transcript.getPath(), transcript.exists());
    assertTrue("Test wav exists " + wav.getPath(), wav.exists());
    assertTrue("Test pdf exists " + pdf.getPath(), pdf.exists());

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
        .setParameter("transcript_type", transcriptType)
        .setParameter("corpus", corpus)
        .setParameter("episode", "TestUpload")
        // no transcript
        .setParameter("media", wav)
        .setParameter("doc", pdf);
      
      try {
        Response response = new Response(request.post(), false);
        response.checkForErrors();
        fail("Should fail when no transcript is specified.");
      } catch(Exception exception) {
        System.out.println(exception.getMessage());
      }

      // wrong file type for transcript
      request = l.postMultipart("api/elicit/upload")
        .setParameter("transcript_type", transcriptType)
        .setParameter("corpus", corpus)
        .setParameter("episode", "TestUpload")
        // no incorrect type
        .setParameter("transcript", document)
        .setParameter("media", wav)
        .setParameter("doc", pdf);
      try {
        Response response = new Response(request.post(), false);
        response.checkForErrors();
        fail("Should fail when incorrect transcript file type is specified.");
      } catch(Exception exception) {
        System.out.println(exception.getMessage());
      }
      
      // wrong file type for media
      File mp3 = new File(files, "nzilbb.labbcat.server.test.mp3");
      IO.Copy(wav, mp3);
      request = l.postMultipart("api/elicit/upload")
        .setParameter("transcript_type", transcriptType)
        .setParameter("corpus", corpus)
        .setParameter("episode", "TestUpload")
        // no incorrect type
        .setParameter("transcript", transcript)
        .setParameter("media", mp3)
        .setParameter("doc", pdf);
      try {
        Response response = new Response(request.post(), false);
        response.checkForErrors();
        fail("Should fail when incorrect media file type is specified.");
      } catch(Exception exception) {
        System.out.println(exception.getMessage());
      }
      
      // other file type pretending to be wav
      File badWav = new File(files, "bad.wav");
      IO.Copy(pdf, badWav);
      request = l.postMultipart("api/elicit/upload")
        .setParameter("transcript_type", transcriptType)
        .setParameter("corpus", corpus)
        .setParameter("episode", "TestUpload")
        // no incorrect type
        .setParameter("transcript", transcript)
        .setParameter("media", badWav)
        .setParameter("doc", pdf);
      try {
        Response response = new Response(request.post(), false);
        response.checkForErrors();
        fail("Should fail when non-media file type pretending to be wav is specified.");
      } catch(Exception exception) {
        System.out.println(exception.getMessage());
      }
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
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.admin.TestUpload");
  }
}
