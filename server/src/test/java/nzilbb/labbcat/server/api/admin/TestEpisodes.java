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

package nzilbb.labbcat.server.api.admin;
	      
import org.junit.*;
import static org.junit.Assert.*;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
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
import nzilbb.labbcat.LabbcatAdmin;
import nzilbb.labbcat.Response;
import nzilbb.labbcat.ResponseException;
import nzilbb.labbcat.model.Episode;
import nzilbb.labbcat.model.Upload;

/**
 * These tests assume that there is a working LaBB-CAT instance with the latest version of
 * nzilbb.labbcat.server.jar installed.
 */
public class TestEpisodes {
  static String labbcatUrl = "http://localhost:8080/labbcat/";
  static String username = "labbcat";
  static String password = "labbcat";
  static LabbcatAdmin l;
  static String readonly_username = "readonly";
  static String readonly_password = "labbcat";
  static LabbcatAdmin ro;
  static String transcriptName = "nzilbb.labbcat.server.test.txt";
  static String episodeName = "server-test";
  static String corpus = null;
  static String otherCorpus = null;
  static int episode_id = -1;

  @BeforeClass public static void setup() {

    try {
      l = new LabbcatAdmin(labbcatUrl, username, password);
      l.setBatchMode(true);
      ro = new LabbcatAdmin(labbcatUrl, readonly_username, readonly_password);
      ro.setBatchMode(true);
      
      corpus = l.getCorpusIds()[0];
      otherCorpus = l.getCorpusIds()[1];
    
      try {
        // ensure transcript doesn't already exist
        l.deleteTranscript(transcriptName);
      } catch(ResponseException exception) {}
      
      // update a transcript with a know corpus/episode
      File transcript = new File(new File(getDir().getParentFile(),"edit"), transcriptName);
      Upload upload = l.transcriptUpload(transcript, false);
      upload.getParameters().get("labbcat_corpus").setValue(corpus);
      upload.getParameters().get("labbcat_episode").setValue(episodeName);
      upload = l.transcriptUploadParameters(upload);
      String threadId = upload.getTranscripts().get(transcript.getName());
      if (threadId != null) {
        // cancel layer generation, we don't care about it         
        l.cancelTask(threadId);
        l.releaseTask(threadId);
      }
    } catch(Exception exception) {
      fail("Could not set up: " + exception);
    }
  }
  
  @AfterClass public static void teardown() {
    try {
      // change name back so tests are re-runnable
      // (deleteTranscript doesn't remove episode directory, so it'll still be there next
      //  time, preventing the first change)
      if (episode_id >= 0) {
        l.updateEpisode(
          new Episode().setEpisodeId(episode_id)
          .setName(episodeName)
          .setCorpus(corpus));
      }
    } catch(Exception exception) {
      fail("Could not tear down: " + exception);
    }
    try { // delete transcript
      l.deleteTranscript(transcriptName);
    } catch(Exception exception) {}
  }
  
  /**
   * Test /api/admin/episode endpoint for listing and update.
   */
  @Test public void basicReadUpdate() throws Exception {
    
      // get episodes in corpus
      Episode[] episodes = l.readEpisodes(corpus);
      Optional<Episode> episode = Arrays.stream(episodes)
        .filter(e->e.getName().equals(episodeName))
        .findAny();
      assertTrue("Episode is in corpus", episode.isPresent());
      assertEquals("Episode corpus is correct", corpus, episode.get().getCorpus());
      if (episode_id < 0) episode_id = episode.get().getEpisodeId();
      
      // change name
      String newEpisodeName = episodeName+"-changed";
      episode.get().setName(newEpisodeName);
      Episode changedName = l.updateEpisode(episode.get());
      assertEquals("Episode name is changed", newEpisodeName, changedName.getName());

      // changed in listing
      episodes = l.readEpisodes(corpus);
      episode = Arrays.stream(episodes)
        .filter(e->e.getName().equals(newEpisodeName))
        .findAny();
      assertTrue("Episode is stil in corpus", episode.isPresent());
      assertEquals("Episode corpus is still correct", corpus, episode.get().getCorpus());
      
      // change corpus
      episode.get().setCorpus(otherCorpus);
      Episode changedCorpus = l.updateEpisode(episode.get());
      assertEquals("Corpus is changed", otherCorpus, otherCorpus);
      
      // listed in other corpus
      episodes = l.readEpisodes(otherCorpus);
      episode = Arrays.stream(episodes)
        .filter(e->e.getName().equals(newEpisodeName))
        .findAny();
      assertTrue("Episode is in other corpus", episode.isPresent());
      assertEquals("Episode corpus changed", otherCorpus, episode.get().getCorpus());

      // simultaneously change name and corpus
      Episode restored = l.updateEpisode(
        new Episode().setEpisodeId(episode_id)
        .setName(episodeName)
        .setCorpus(corpus));

      // episode is back where it started
      episodes = l.readEpisodes(corpus);
      episode = Arrays.stream(episodes)
        .filter(e->e.getName().equals(episodeName))
        .findAny();
      assertTrue("Episode is in corpus", episode.isPresent());
      assertEquals("Episode corpus is correct", corpus, episode.get().getCorpus());
  }  

  /** Ensure functions only accept valid parameters. */
  @Test public void validation() throws StoreException, PermissionException {

    // a nonexistent corpus just returns an empty list
    Episode[] episodes = l.readEpisodes("nonexistent corpus");
    assertEquals("Can't read episodes of invalid corpus",
                 0, episodes.length);

    if (episode_id < 0) { // get episode_id
      episodes = l.readEpisodes(corpus);
      Optional<Episode> episode = Arrays.stream(episodes)
        .filter(e->e.getName().equals(episodeName))
        .findAny();
      episode_id = episode.get().getEpisodeId();
    }
    
    try {
      l.updateEpisode(
        new Episode().setEpisodeId(episode_id)
        .setName("") // blank name
        .setCorpus(corpus));
      fail("Can set episode name to empty string");
    } catch (ResponseException x) {
      System.out.println(""+x);
    }
    
    try {
      l.updateEpisode(
        new Episode().setEpisodeId(episode_id)
        .setName(episodeName)
        .setCorpus(""));
      fail("Can set episode corpus to blank string");
    } catch (ResponseException x) {
      System.out.println(""+x);
    }
    try {
      l.updateEpisode(
        new Episode().setEpisodeId(episode_id)
        .setName(episodeName)
        .setCorpus("nonexistent corpus"));
      fail("Can set episode corpus to nonexistent corpus");
    } catch (ResponseException x) {
      System.out.println(""+x);
    }
  }
  
  /** Ensure only admin users can access functions. */
  @Test public void adminAccessEnforced() throws StoreException, PermissionException {
    
    try {
      Episode[] episodes = ro.readEpisodes(corpus);
      fail("Can't read episodes as non-admin user");
    } catch(ResponseException x) {
      System.out.println(""+x);
    }
    
    if (episode_id < 0) { // get episode_id
      Episode[] episodes = l.readEpisodes(corpus);
      Optional<Episode> episode = Arrays.stream(episodes)
        .filter(e->e.getName().equals(episodeName))
        .findAny();
      episode_id = episode.get().getEpisodeId();
    }
    
    try {
      ro.updateEpisode(
        new Episode().setEpisodeId(episode_id)
        .setName(episodeName)
        .setCorpus(corpus));
      fail("Can't update episode as non-admin user");
    } catch(ResponseException x) {
      System.out.println(""+x);
    }
  }

  /**
   * Directory for text files.
   * @see #getDir()
   */
  protected static File fDir;
  /**
   * Getter for {@link #fDir}: Directory for text files.
   * @return Directory for text files.
   */
  public static File getDir() { 
    if (fDir == null) {
      try {
        URL urlThisClass = TestEpisodes.class.getResource(
          TestEpisodes.class.getSimpleName() + ".class");
        File fThisClass = new File(urlThisClass.toURI());
        fDir = fThisClass.getParentFile();
      } catch(Throwable t) {
        System.out.println("" + t);
      }
    }
    return fDir; 
  }

  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.admin.TestEpisodes");
  }
}
