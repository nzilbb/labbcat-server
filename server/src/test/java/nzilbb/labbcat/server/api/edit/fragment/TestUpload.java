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

package nzilbb.labbcat.server.api.edit.fragment;
	      
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
import nzilbb.labbcat.PatternBuilder;
import nzilbb.labbcat.ResponseException;
import nzilbb.labbcat.http.HttpRequestGet;
import nzilbb.labbcat.model.*;
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
   * Test /api/edit/fragment/* API, specifically:
   * <ul>
   *  <li> fragmentUpload </li>
   *  <li> fragmentUploadDelete </li>
   * </ul>
   */
  @Test public void fragmentUploadDelete()
    throws Exception {
    // use a real fragment...
    
    // get a participant ID to use
    String[] ids = l.getParticipantIds();
    assertTrue("getParticipantIds: Some IDs are returned", ids.length > 0);
    String[] participantId = { ids[0] };

    // all instances of "and"
    JsonObject pattern = new PatternBuilder().addMatchLayer("orthography", "and").build();
    String threadId = l.search(pattern, participantId, null, false, null, null, null);
    TaskStatus task = l.waitForTask(threadId, 30);
    try {
      // if the task is still running, it's taking too long, so cancel it
      if (task.getRunning()) try { l.cancelTask(threadId); } catch(Exception exception) {}
      assertFalse("Search task finished in a timely manner", task.getRunning());
      
      Match[] matches = l.getMatches(threadId, 2);
      if (matches.length == 0) {
        fail("getMatches: No matches were returned, cannot test getFragments");
      } else {
        Match[] match = { matches[0] };
        
        File dir = new File("fragmentUploadDelete");
        String[] layerIds = { "word", "segment" };
        File[] fragments = l.getFragments(match, layerIds, "text/praat-textgrid", dir);
        File fragment = fragments[0];
        assertTrue("Ensure fragment exists: " + fragment.getPath(), fragment.exists());
        try {
          
          // upload fragment
          // l.setVerbose(true);
          nzilbb.labbcat.model.Upload upload = l.fragmentUpload(fragment, false);
          assertEquals("Transcript is identified",
                       match[0].getTranscript(), upload.getTranscript());
          assertEquals("There are are two tier mappings",
                       2, upload.getParameters().size());
          assertTrue("First tier mapping",
                     upload.getParameters().containsKey("tier0"));
          assertNotNull("First tier mapping has default",
                        upload.getParameters().get("tier0").getValue());
          assertEquals("First tier mapping correct",
                       "word", upload.getParameters().get("tier0").getValue().toString());
          assertTrue("Second tier mapping",
                     upload.getParameters().containsKey("tier1"));
          assertNotNull("Second tier mapping has default",
                        upload.getParameters().get("tier1").getValue());
          assertEquals("Second tier mapping correct",
                       "segment", upload.getParameters().get("tier1").getValue().toString());
          
          // delete upload
          l.fragmentUploadDelete(upload);
          
        } finally {
          l.setVerbose(false);
          fragment.delete();
          dir.delete();
        }
      } // there was a match
    } finally {
      l.releaseTask(threadId);
    }
  }
    
  /**
   * Test /api/edit/fragment/* API, specifically:
   * <ul>
   *  <li> fragmentUpload </li>
   *  <li> fragmentUploadParameters </li>
   * </ul>
   */
  @Test public void fragmentUploadTwoPhase() throws Exception {
    // use a real fragment...
    
    // get a participant ID to use
    String[] ids = l.getParticipantIds();
    assertTrue("getParticipantIds: Some IDs are returned", ids.length > 0);
    String[] participantId = { ids[0] };
    
    // all instances of "and"
    JsonObject pattern = new PatternBuilder().addMatchLayer("orthography", "and").build();
    String threadId = l.search(pattern, participantId, null, false, null, null, null);
    TaskStatus task = l.waitForTask(threadId, 30);
    try {
      // if the task is still running, it's taking too long, so cancel it
      if (task.getRunning()) try { l.cancelTask(threadId); } catch(Exception exception) {}
      assertFalse("Search task finished in a timely manner", task.getRunning());
      
      Match[] matches = l.getMatches(threadId, 2);
      if (matches.length == 0) {
        fail("getMatches: No matches were returned, cannot test getFragments");
      } else {
        Match[] match = { matches[0] };
        
        File dir = new File("fragmentUploadTwoPhase");
        String[] layerIds = { "utterance", "word", "segment" };
        File[] fragments = l.getFragments(match, layerIds, "text/praat-textgrid", dir);
        File fragment = fragments[0];
        assertTrue("Ensure fragment exists: " + fragment.getPath(), fragment.exists());
        
        try {
          // upload fragment
          // l.setVerbose(true);
          nzilbb.labbcat.model.Upload upload = l.fragmentUpload(fragment, false);
          assertEquals("Transcript is identified",
                       match[0].getTranscript(), upload.getTranscript());
          assertEquals("There are are three tier mappings",
                       3, upload.getParameters().size());
          assertTrue("First tier mapping",
                     upload.getParameters().containsKey("tier0"));
          assertNotNull("First tier mapping has default",
                        upload.getParameters().get("tier0").getValue());
          assertEquals("First tier mapping correct",
                       "utterance", upload.getParameters().get("tier0").getValue().toString());
          assertTrue("Second tier mapping",
                     upload.getParameters().containsKey("tier1"));
          assertNotNull("Second tier mapping has default",
                        upload.getParameters().get("tier1").getValue());
          assertEquals("Second tier mapping correct",
                       "word", upload.getParameters().get("tier1").getValue().toString());
          assertTrue("Third tier mapping",
                     upload.getParameters().containsKey("tier2"));
          assertNotNull("Third tier mapping has default",
                        upload.getParameters().get("tier2").getValue());
          assertEquals("Third tier mapping correct",
                       "segment", upload.getParameters().get("tier2").getValue().toString());
          
          // finalize parameters
          upload = l.fragmentUploadParameters(upload);
          assertNotNull("ID ", upload.getId());
          assertNotNull("fragment URL returned", upload.getUrl());
        } finally {
          l.setVerbose(false);
          fragment.delete();
          dir.delete();
        }
      } // there was a match
    } finally {
      l.releaseTask(threadId);
    }
  }

  /**
   * Test /api/edit/fragment/* API using automaticMapping, i.e. one call to fragmentUpload.
   */
  @Test public void fragmentUploadAutomaticMapping() throws Exception {
    // use a real fragment...
    
    // get a participant ID to use
    String[] ids = l.getParticipantIds();
    assertTrue("getParticipantIds: Some IDs are returned", ids.length > 0);
    String[] participantId = { ids[0] };
    
    // all instances of "and"
    JsonObject pattern = new PatternBuilder().addMatchLayer("orthography", "and").build();
    String threadId = l.search(pattern, participantId, null, false, null, null, null);
    TaskStatus task = l.waitForTask(threadId, 30);
    try {
      // if the task is still running, it's taking too long, so cancel it
      if (task.getRunning()) try { l.cancelTask(threadId); } catch(Exception exception) {}
      assertFalse("Search task finished in a timely manner", task.getRunning());
      
      Match[] matches = l.getMatches(threadId, 2);
      if (matches.length == 0) {
        fail("getMatches: No matches were returned, cannot test getFragments");
      } else {
        Match[] match = { matches[0] };
        
        File dir = new File("fragmentUploadAutomaticMapping");
        String[] layerIds = { "utterance", "word", "segment" };
        File[] fragments = l.getFragments(match, layerIds, "text/praat-textgrid", dir);
        File fragment = fragments[0];
        assertTrue("Ensure fragment exists: " + fragment.getPath(), fragment.exists());
        
        try {
          // upload fragment
          // l.setVerbose(true);
          nzilbb.labbcat.model.Upload upload = l.fragmentUpload(
            fragment, true); //automaticMapping=true
          assertEquals("Transcript is identified",
                       match[0].getTranscript(), upload.getTranscript());
          assertNull("There are are no parameters",
                     upload.getParameters());
          assertNotNull("fragment URL returned", upload.getUrl());
        } finally {
          l.setVerbose(false);
          fragment.delete();
          dir.delete();
        }
      } // there was a match
    } finally {
      l.releaseTask(threadId);
    }
  }

  /**
   * Test validation of /api/edit/fragment/* API.
   */
  @Test public void invalidInputs()
    throws Exception {
    
    try {
      nzilbb.labbcat.model.Upload upload = l.fragmentUpload(null, false);
      fail("Should fail to upload null file " + upload);
    } catch (StoreException x) {
      System.out.println(""+x);
    }
    try {
      File nonexistent = new File(getDir(), "nonexistent__0.0-1.0.TextGrid");
      assertFalse("Nonexistent file really doesn't exist", nonexistent.exists());
      nzilbb.labbcat.model.Upload upload = l.fragmentUpload(nonexistent, false);
      fail("Should fail to upload nonexistent file " + upload);
    } catch (Exception x) {
      System.out.println(""+x);
    }    
    try {
      File transcript = new File(
        getDir().getParentFile(), "nzilbb.labbcat.server.test.txt");
      assertTrue("Transcript file exists", transcript.exists());
      nzilbb.labbcat.model.Upload upload = l.fragmentUpload(transcript, false);
      fail("Should fail to upload nonexistent file " + upload);
    } catch (StoreException x) {
      System.out.println(""+x);
    }
    String invalidId = "_fragment_999";
    try {
      nzilbb.labbcat.model.Upload upload = l.fragmentUploadParameters(
        new nzilbb.labbcat.model.Upload().setId(invalidId));
      fail("Should fail to set parameters for nonexistent upload");
    } catch (StoreException x) {
      System.out.println(""+x);
    }
    try {
      l.fragmentUploadDelete(invalidId);
      fail("Should fail to delete nonexistent upload");
    } catch (StoreException x) {
      System.out.println(""+x);
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
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.edit.fragment.TestUpload");
  }
}
