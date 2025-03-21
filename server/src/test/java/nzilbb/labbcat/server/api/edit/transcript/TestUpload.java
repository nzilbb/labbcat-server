//
// Copyright 2025 New Zealand Institute of Language, Brain and Behaviour, 
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

package nzilbb.labbcat.server.api.edit.transcript;
	      
import org.junit.*;
import static org.junit.Assert.*;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
import nzilbb.labbcat.model.*;
import nzilbb.labbcat.ResponseException;
import nzilbb.labbcat.http.HttpRequestGet;
import nzilbb.labbcat.model.Match;

/**
 * These tests assume that there is a working LaBB-CAT instance with the latest version of
 * nzilbb.labbcat.server.jar installed.  
 */
public class TestUpload
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
   * Test /api/edit/transcript/* API, specifically:
   * <ul>
   *  <li> transcriptUpload </li>
   *  <li> transcriptUploadDelete </li>
   * </ul>
   */
  @Test public void transcriptUploadDelete()
    throws Exception {
    
    File transcript = new File(getDir().getParentFile(), "nzilbb.labbcat.server.test.txt");
    assertTrue("Ensure transcript exists: " + transcript.getPath(), transcript.exists());
    try {
      
      // upload transcript
      // l.setVerbose(true);
      nzilbb.labbcat.model.Upload upload = l.transcriptUpload(transcript, false);
      assertNotNull("ID returned", upload.getId());

      // delete upload
      l.transcriptUploadDelete(upload);
      
    } finally {
      l.setVerbose(false);
    }
  }
    
  /**
   * Test /api/edit/transcript/upload/* API; uploading a new file.
   */
  @Test public void transcriptUploadNew() throws Exception {
    File transcript = new File(getDir().getParentFile(), "nzilbb.labbcat.server.test.txt");
    File[] media = {
      new File(getDir().getParentFile(), "nzilbb.labbcat.server.test.wav")
    };
    File document = new File(getDir().getParentFile(), "nzilbb.labbcat.server.test.doc");
    String participantId = "UnitTester";
    assertTrue("Ensure transcript exists: " + transcript.getPath(), transcript.exists());
    assertTrue("Ensure media exists: " + media[0].getPath(), media[0].exists());
    assertTrue("Ensure document exists: " + document.getPath(), document.exists());
    try {
      
      // ensure transcript/participant don't already exist
      try {
        l.deleteTranscript(transcript.getName());
      } catch(ResponseException exception) {}
      try {
        l.deleteParticipant(participantId);
      } catch(ResponseException exception) {}

      // upload transcript
      //l.setVerbose(true);
      nzilbb.labbcat.model.Upload upload = l.transcriptUpload(transcript, media, false);
      assertTrue("corpus parameter" + upload.getParameters(),
                 upload.getParameters().containsKey("labbcat_corpus"));
      assertNotNull("corpus parameter has default",
                    upload.getParameters().get("labbcat_corpus").getValue());
      assertTrue("episode parameter" + upload.getParameters(),
                 upload.getParameters().containsKey("labbcat_episode"));
      assertNotNull("episode parameter has default",
                    upload.getParameters().get("labbcat_episode").getValue());
      assertTrue("transcript_type parameter" + upload.getParameters(),
                 upload.getParameters().containsKey("labbcat_transcript_type"));
      assertNotNull("transcript_type parameter has default",
                    upload.getParameters().get("labbcat_transcript_type").getValue());

      // finalize upload
      //l.setVerbose(true);
      upload = l.transcriptUploadParameters(upload);
      assertNotNull("transcript threads returned",
                    upload.getTranscripts());
      assertEquals("There's one thread " + upload.getTranscripts(),
                   1, upload.getTranscripts().size());

      // transcript name should match file name
      
      String threadId = upload.getTranscripts().get(transcript.getName());
      assertNotNull("transcript thread is specified " +upload.getTranscripts(),
                    threadId);
      // cancel layer generation, we don't care about it         
      l.cancelTask(threadId);
      l.releaseTask(threadId);      
      
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
   * Test /api/edit/transcript/upload/* API; uploading an existing file.
   */
  @Test public void transcriptUploadExisting() throws Exception {
    File transcript = new File(getDir().getParentFile(), "nzilbb.labbcat.server.test.txt");
    File[] media = {
      new File(getDir().getParentFile(), "nzilbb.labbcat.server.test.wav")
    };
    File document = new File(getDir().getParentFile(), "nzilbb.labbcat.server.test.doc");
    String participantId = "UnitTester";
    assertTrue("Ensure transcript exists: " + transcript.getPath(), transcript.exists());
    assertTrue("Ensure media exists: " + media[0].getPath(), media[0].exists());
    assertTrue("Ensure document exists: " + document.getPath(), document.exists());
    try {
      
      // ensure transcript/participant don't already exist
      try {
        l.deleteTranscript(transcript.getName());
      } catch(ResponseException exception) {}
      try {
        l.deleteParticipant(participantId);
      } catch(ResponseException exception) {}

      // upload transcript
      nzilbb.labbcat.model.Upload upload = l.transcriptUpload(transcript, media, false);
      // finalize upload
      upload = l.transcriptUploadParameters(upload);
      String threadId = upload.getTranscripts().get(transcript.getName());
      assertNotNull("transcript thread is specified " +upload.getTranscripts(),
                    threadId);
      // cancel layer generation, we don't care about it         
      l.cancelTask(threadId);
      l.releaseTask(threadId);

      // ensure upload was successful
      assertEquals("Transcript has been added to the store",
                   1, l.countMatchingTranscriptIds("id = '"+transcript.getName()+"'"));

      // now upload again
      // l.setVerbose(true);
      upload = l.transcriptUpload(transcript, true);

      assertTrue("generate parameter" + upload.getParameters(),
                 upload.getParameters().containsKey("labbcat_generate"));
      assertTrue("generation enabled by default",
                 (Boolean)upload.getParameters().get("labbcat_generate").getValue());
      
      // // disable generation
      // upload.getParameters().get("labbcat_generate").setValue(Boolean.FALSE);

      upload = l.transcriptUploadParameters(upload);
      threadId = upload.getTranscripts().get(transcript.getName());
      assertNotNull("transcript thread is specified " +upload.getTranscripts(),
                    threadId);
      // cancel layer generation, we don't care about it         
      l.cancelTask(threadId);
      l.releaseTask(threadId);
      
    } finally {
      l.setVerbose(false);
      try {
        // delete transcript/participant
        // l.deleteTranscript(transcript.getName());
        // l.deleteParticipant(participantId);
            
        // // ensure the transcript/participant no longer exist
        // assertEquals("Transcript has been deleted from the store",
        //              0, l.countMatchingTranscriptIds("id = '"+transcript.getName()+"'"));
        // assertEquals("Participant has been deleted from the store",
        //              0, l.countMatchingParticipantIds("id = '"+participantId+"'"));
      } catch (Exception x) {
        System.err.println("Unexpectedly can't delete test transcript: " + x);
      }
    }    
  }
  
  /** Test episode document can be saved and deleted. */
  @Test public void saveDeleteEpisodeDocument() throws Exception {
    
    // first get a corpus and transcript type
    String[] ids = l.getCorpusIds();
    assertTrue("There is at least one corpus", ids.length > 0);
    String corpus = ids[0];
    Layer typeLayer = l.getLayer("transcript_type");
    assertTrue("There is at least one transcript type", typeLayer.getValidLabels().size() > 0);
    String transcriptType = typeLayer.getValidLabels().keySet().iterator().next();

    File transcript = new File(getDir(), "nzilbb.labbcat.server.test.txt");
    File media = new File(getDir(), "nzilbb.labbcat.server.test.doc");
    String participantId = "UnitTester";
    assertTrue("Ensure transcript exists: " + transcript.getPath(), transcript.exists());
    assertTrue("Ensure document exists: " + media.getPath(), media.exists());
    try {
      
      // ensure transcript/participant don't already exist
      try {
        l.deleteTranscript(transcript.getName());
      } catch(ResponseException exception) {}
      try {
        l.deleteParticipant(participantId);
      } catch(ResponseException exception) {}
      
      String threadId = l.newTranscript(
        transcript, null, null, transcriptType, corpus, "test");      
      // cancel layer generation, we don't care about it         
      l.cancelTask(threadId);
      l.releaseTask(threadId);

      // ensure there is no media
      MediaFile[] files = l.getEpisodeDocuments(transcript.getName());
      assertTrue("No documents present: " + Arrays.asList(files), files.length == 0);

      // upload document
      MediaFile file = l.saveEpisodeDocument(transcript.getName(), media.toURI().toString());
      assertNotNull("File returned", file);
      assertNotNull("File name returned", file.getName());      
      
      // ensure there is now media
      files = l.getEpisodeDocuments(transcript.getName());
      assertTrue("Document is now present", files.length > 0);

      // delete media
      l.deleteMedia(transcript.getName(), file.getName());
      // ensure the media is now gone
      files = l.getEpisodeDocuments(transcript.getName());
      assertTrue("Document was deleted: " + Arrays.asList(files), files.length == 0);
    } finally {
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
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.edit.test.TestUpload");
  }
}
