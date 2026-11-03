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

package nzilbb.labbcat.server.api.edit.annotations;
	      
import org.junit.*;
import static org.junit.Assert.*;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
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
import nzilbb.labbcat.PatternBuilder;
import nzilbb.labbcat.model.*;
import nzilbb.labbcat.ResponseException;
import nzilbb.labbcat.http.HttpRequestGet;
import nzilbb.labbcat.model.Match;

/**
 * These tests assume that there is a working LaBB-CAT instance with the latest version of
 * nzilbb.labbcat.server.jar installed.  
 */
public class TestTokens {
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
  
  /** Test annotations can be uploaded from results CSV */
  @Test public void uploadTokenAnnotations() throws Exception {
    
    File csv = new File(getDir(), "results.csv");
    int idColumn = 8; // MatchId
    String[] columnLayer = {
      "","","","","","","","","","","","","","","","","","","","lexical" };
    String threadId = l.uploadTokenAnnotations(csv, idColumn, columnLayer);
    
    TaskStatus task = l.waitForTask(threadId, 30);
    assertFalse("Upload task finished in a timely manner",
                task.getRunning());
    System.out.println(task.getStatus());
    task = l.taskStatus(threadId, true, false);
    System.out.println(task.getLog());
    l.releaseTask(threadId);
  }

  /** Ensure annotations are successfully created and deleted */
  @Test public void creationDeletion() throws Exception {

    // find a token to annotate
    String[] participantIds = l.getParticipantIds();
    String[] firstParticipant = { participantIds[0] };
    Match[] matches = l.getMatches(
      l.search(
        new PatternBuilder().addMatchLayer("orthography", "the").build(),
        firstParticipant, null, true, null, null, null), 0);    
    Match token = matches[0];

    // create a csv file for tagging it
    File csv = new File(getDir(), "token.csv");
    String label = new Date().toString(); // alwaya a different label
    try(PrintWriter writer = new PrintWriter(csv)) {
      writer.println("MatchId,Label");
      writer.println(token.getMatchId()+","+label);
    }

    // upload CSV annotation
    String threadId = l.uploadTokenAnnotations(csv, 0, new String[] {"","lexical" });
    
    TaskStatus task = l.waitForTask(threadId, 30);
    assertFalse("Upload task finished in a timely manner",
                task.getRunning());
    task = l.taskStatus(threadId, true, false);
    System.out.println(task.getLog());
    l.releaseTask(threadId);

    // check the token has been tagged
    MatchId match = new MatchId(token);
    Annotation[] annotations = l.getMatchingAnnotations(
      "layerId == 'lexical' && parent.id == '"+match.getTargetId()+"'");
    assertEquals("There is a tag",
                 1, annotations.length);
    assertEquals("The label is correct.",
                 label, annotations[0].getLabel());

    // create CSV file for untagging it
    try(PrintWriter writer = new PrintWriter(csv)) {
      writer.println("MatchId,Label");
      writer.println(token.getMatchId()+","); // blank label should delete tag
    }

    // upload new CSV
    threadId = l.uploadTokenAnnotations(csv, 0, new String[] {"","lexical" });    
    task = l.waitForTask(threadId, 30);
    assertFalse("Upload task finished in a timely manner",
                task.getRunning());
    task = l.taskStatus(threadId, true, false);
    System.out.println(task.getLog());
    l.releaseTask(threadId);
    
    // check the token is no longer tagged
    annotations = l.getMatchingAnnotations(
      "layer.id == 'lexical' && parent.id == '"+match.getTargetId()+"'");
    assertEquals("There is no tag " + Arrays.asList(annotations),
                 0, annotations.length);

  }
    

  /** Test parameter validation. */
  @Test public void invalidParameters() throws Exception {

    File csv = new File(getDir(), "results.csv");
    int idColumn = 8; // MatchId
    String[] columnLayer = {
      "","","","","","","","","","","","","","","","","","","","lexical" };
    try {
      l.uploadTokenAnnotations(null, idColumn, columnLayer);
      fail("Should fail when no CSV is supplied");
    } catch(Exception exception) {
      System.out.println(exception.toString());
    }
    
    try {
      l.uploadTokenAnnotations(csv, 100, columnLayer);
      fail("Should fail when invalid ID column is supplied");
    } catch(Exception exception) {
      System.out.println(exception.toString());
    }
    
    try {
      l.uploadTokenAnnotations(csv, idColumn, null);
      fail("Should fail when no column-to-layer mapping is supplied");
    } catch(Exception exception) {
      System.out.println(exception.toString());
    }
    
    try {
      String[] noLayersMapped = {
        "","","","","","","","","","","","","","","","","","","","" };
      l.uploadTokenAnnotations(csv, idColumn, noLayersMapped);
      fail("Should fail when there are no column mappings");
    } catch(Exception exception) {
      System.out.println(exception.toString());
    }
    
    try {
      String[] moreColumnsThanCsv = {
        "","","","","","","","","","","","","","","","","","","","lexical","lexical" };
      l.uploadTokenAnnotations(csv, idColumn, moreColumnsThanCsv);
      fail("Should fail when more column mappings than columns are supplied");
    } catch(Exception exception) {
      System.out.println(exception.toString());
    }
    
    try {
      String[] invalidLayer = {
        "","","","","","","","","","","","","","","","","","","","nonexistent" };
      l.uploadTokenAnnotations(csv, idColumn, invalidLayer);
      fail("Should fail when nonexistent layer is specified");
    } catch(Exception exception) {
      System.out.println(exception.toString());
    }
    
    try {
      String[] invalidLayer = { null, "corpus", "transcript_version", "transcript_versionDate" };
      l.uploadTranscriptAttributes(csv, idColumn, invalidLayer);
      fail("Should fail when non-transcript-attribute layer is specified");
    } catch(Exception exception) {
      System.out.println(exception.toString());
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
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.edit.annotations.TestTokens");
  }
}
