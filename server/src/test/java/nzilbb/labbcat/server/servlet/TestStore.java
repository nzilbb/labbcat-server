//
// Copyright 2023 New Zealand Institute of Language, Brain and Behaviour, 
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

package nzilbb.labbcat.server.servlet;
	      
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
public class TestStore
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
  
  @Test public void deleteTranscriptNotExists() throws Exception {
    try {
      // get some annotations so we have valid anchor IDs
      l.deleteTranscript("nonexistent graph ID");
      fail("deleteTranscript should fail for nonexistant graph ID");
    } catch(ResponseException exception) {
      assertEquals("404 not found",
                   404, exception.getResponse().getHttpStatus());
      assertEquals("Failure code",
                   1, exception.getResponse().getCode());
    }
  }

  @Test public void saveParticipantDeleteParticipant() throws Exception {

    // create a participant with saveParticipant
    String originalId = "TestStore-participant";
    String changedId = "TestStore-participant-changed";
    String otherId = "TestStore-other-participant";
    Annotation participant = new Annotation(originalId, originalId, "participant");
    // add an attribute
    Annotation gender = new Annotation(null, "X", "participant_gender");
    String[] layerIds = { "participant_gender" };
    participant.addAnnotation(gender);
    try {
      assertTrue("new participant saved",
                 l.saveParticipant(participant));
      // they're really there
      participant = l.getParticipant(originalId, layerIds);
      assertNotNull("new participant exists", participant);
      gender = participant.first("participant_gender");
      assertNotNull("attribute saved", gender);
      assertEquals("attribute label correct", "X", gender.getLabel());
      assertFalse("no changes saved", l.saveParticipant(participant));

      // change their attribute
      gender.setLabel("Y");
      assertTrue("Record updated", l.saveParticipant(participant));
      participant = l.getParticipant(originalId, layerIds);
      gender = participant.first("participant_gender");
      assertNotNull("attribute saved", gender);
      assertEquals("attribute label correct", "Y", gender.getLabel());

      // change their ID, without attributes
      participant = new Annotation(originalId, changedId, "participant");
      assertTrue("ID updated", l.saveParticipant(participant));
      participant = l.getParticipant(originalId, layerIds);
      assertNull("participant not there under the old ID", participant);      
      participant = l.getParticipant(changedId, layerIds);
      assertNotNull("participant there under the new ID", participant);
      assertEquals("label changed", changedId, participant.getLabel());
      gender = participant.first("participant_gender");
      assertNotNull("attribute no deleted during ID change", gender);
      assertEquals("attribute label correct after ID change", "Y", gender.getLabel());

      // change their ID to one that already exists
      Annotation otherParticipant = new Annotation(otherId, otherId, "participant");
      assertTrue("other participant saved",
                 l.saveParticipant(otherParticipant));
      participant = new Annotation(changedId, otherId, "participant");
      try {
        l.saveParticipant(participant);
        fail("saveParticipant should fail when changing ID if new ID already exists");
      } catch(Exception exception) {
        System.out.println(""+exception);
      }

    } finally {
      // delete participant
      try {
        l.deleteParticipant(changedId);
      } catch (ResponseException x) {} // if above tests fail before participant is created
      assertNull("participant deleted", l.getParticipant(changedId));
      try {
        l.deleteParticipant(originalId);
        fail("deleteParticipant should fail for nonexistant ID");
      } catch (ResponseException exception) {}
      assertNull("participant not there under the old ID", l.getParticipant(originalId));
      try {
        l.deleteParticipant(otherId);
      } catch (ResponseException x) {} // if above tests fail before participant is created
      assertNull("other participant deleted", l.getParticipant(otherId));
    }
  }

  /** Test transcript attributes can be saved */
  @Test public void saveTranscript() throws Exception {
    
    // get the language of a transcript
    String[] ids = l.getMatchingTranscriptIds("/AP511.+\\.eaf/.test(id)", 1, 0);
    assertTrue("Some graph IDs are returned",
               ids.length > 0);
    String graphId = ids[0];
    String[] attributes = { "transcript_language" };
    Graph graph = l.getTranscript(graphId, attributes);
    assertNotNull("Graph retrieved: " + graphId, graph);

    // change the language
    Annotation language = graph.first("transcript_language");
    String originalLanguage = language != null?language.getLabel():null;
    graph.createTag(graph, "transcript_language", "test-value");
    l.saveTranscript(graph);

    // check the new value was saved
    graph = l.getTranscript(graphId, attributes);
    language = graph.first("transcript_language");
    assertNotNull("language attribute exists", language);
    assertEquals("new label applied", "test-value", language.getLabel());

    // save original value
    if (originalLanguage != null) {
      language.setLabel(originalLanguage);
    } else { // delete the annotation
      graph.trackChanges();
      language.destroy();
      graph.commit();
    }
    l.saveTranscript(graph);
  }
  
  /** Test transcript media can be saved and deleted */
  @Test public void saveDeleteMedia() throws Exception {
    
    // first get a corpus and transcript type
    String[] ids = l.getCorpusIds();
    assertTrue("There is at least one corpus", ids.length > 0);
    String corpus = ids[0];
    Layer typeLayer = l.getLayer("transcript_type");
    assertTrue("There is at least one transcript type", typeLayer.getValidLabels().size() > 0);
    String transcriptType = typeLayer.getValidLabels().keySet().iterator().next();

    File transcript = new File(getDir(), "nzilbb.labbcat.server.test.txt");
    File media = new File(getDir(), "nzilbb.labbcat.server.test.wav");
    String participantId = "UnitTester";
    assertTrue("Ensure transcript exists: " + transcript.getPath(), transcript.exists());
    assertTrue("Ensure media exists: " + media.getPath(), media.exists());
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
      MediaFile[] files = l.getAvailableMedia(transcript.getName());
      assertTrue("No media is present: " + Arrays.asList(files), files.length == 0);

      // upload media
      MediaFile file = l.saveMedia(transcript.getName(), media.toURI().toString(), null);
      assertNotNull("File returned", file);
      assertNotNull("File name returned", file.getName());
      
      // ensure there is now media
      files = l.getAvailableMedia(transcript.getName());
      assertTrue("Media is now present", files.length > 0);

      // delete media
      l.deleteMedia(transcript.getName(), file.getName());
      // ensure the media is now gone
      files = l.getAvailableMedia(transcript.getName());
      assertTrue("Media was deleted: " + Arrays.asList(files), files.length == 0);

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
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.servlet.test.TestStore");
  }
}
