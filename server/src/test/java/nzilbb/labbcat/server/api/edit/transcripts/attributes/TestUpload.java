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

package nzilbb.labbcat.server.api.edit.transcripts.attributes;
	      
import org.junit.*;
import static org.junit.Assert.*;

import java.io.*;
import java.net.*;
import java.util.Arrays;
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
  
  /** Test basic transcript attributes upload from CSV. */
  @Test public void uploadTranscriptAttributes() throws Exception {

    File transcript = new File(
      getDir(), "nzilbb.labbcat.server.attributes.txt");
    String[] ids = l.getCorpusIds();
    assertTrue("There is at least one corpus", ids.length > 0);
    String corpus = ids[0];
    Layer typeLayer = l.getLayer("transcript_type");
    assertTrue("There is at least one transcript type", typeLayer.getValidLabels().size() > 0);
    String transcriptType = typeLayer.getValidLabels().keySet().iterator().next();

    try {
      // create a transcript for testing
      String threadId = l.newTranscript(
        transcript, null, null, transcriptType, corpus, "test");
      
      String[] layerIds = { "transcript_version", "transcript_versionDate" };
      Graph graph = l.getGraph(transcript.getName(), layerIds);
      // exception thrown if it's not there
      
      File csv = new File(getDir(), "transcripts.csv");
      int idColumn = 0;
      String[] columnLayer = {
        null, "", "transcript_version", "transcript_versionDate" };
      int[] counts = l.uploadTranscriptAttributes(csv, idColumn, columnLayer);
      assertEquals("Correct number of counts returned " + Arrays.asList(counts),
                   2, counts.length);
      assertEquals("One transcript updated", 1, counts[0]);
      assertEquals("One transcript missing", 1, counts[1]);      
      
      graph = l.getTranscript(transcript.getName(), layerIds);
      assertNotNull("Version exists", graph.first("transcript_version"));
      assertEquals("Version correct",
                   "CSV", graph.first("transcript_version").getLabel());
      assertNotNull("Version date exists", graph.first("transcript_versionDate"));
      assertEquals("Version date correct",
                   "2026-08-13", graph.first("transcript_versionDate").getLabel());
      
    } finally {
      l.setVerbose(false);
      try {
        l.deleteTranscript(transcript.getName());
      } catch(ResponseException exception) {}
    }
  }
    
  /** Test parameter validation. */
  @Test public void invalidParameters() throws Exception {

    File csv = new File(getDir(), "transcripts.csv");
    int idColumn = 0;
    String[] columnLayer = { null, "", "transcript_version", "transcript_versionDate" };
    try {
      l.uploadTranscriptAttributes(null, idColumn, columnLayer);
      fail("Should fail when no CSV is supplied");
    } catch(Exception exception) {
      System.out.println(exception.toString());
    }
    
    try {
      l.uploadTranscriptAttributes(csv, 100, columnLayer);
      fail("Should fail when invalid ID column is supplied");
    } catch(Exception exception) {
      System.out.println(exception.toString());
    }
    
    try {
      l.uploadTranscriptAttributes(csv, idColumn, null);
      fail("Should fail when no column-to-layer mapping is supplied");
    } catch(Exception exception) {
      System.out.println(exception.toString());
    }
    
    try {
      String[] moreColumnsThanCsv = {
        null, "", "transcript_version", "transcript_versionDate", "transcript_versionDate" };
      l.uploadTranscriptAttributes(csv, idColumn, moreColumnsThanCsv);
      fail("Should fail when more column mappings than columns are supplied");
    } catch(Exception exception) {
      System.out.println(exception.toString());
    }
    
    try {
      String[] invalidLayer = { null, "", "transcript_nonexistent", "transcript_versionDate" };
      l.uploadTranscriptAttributes(csv, idColumn, invalidLayer);
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
    
  /** Test multi-value attribute correctly accumulate values across lines and fields. */
  @Test public void multivalueAttribute() throws Exception {

    String multiValueAttribute = "transcript_test_multivalue";
    
    File transcript = new File(
      getDir(), "nzilbb.labbcat.server.attributes.txt");
    String[] ids = l.getCorpusIds();
    assertTrue("There is at least one corpus", ids.length > 0);
    String corpus = ids[0];
    Layer typeLayer = l.getLayer("transcript_type");
    assertTrue("There is at least one transcript type", typeLayer.getValidLabels().size() > 0);
    String transcriptType = typeLayer.getValidLabels().keySet().iterator().next();
    
    try {
      // create a transcript for testing
      String threadId = l.newTranscript(
        transcript, null, null, transcriptType, corpus, "test");
      // create a multi-value transcript attribute for testing
      Layer multi = new Layer(multiValueAttribute, "Unit test attribute")
        .setParentId("transcript")
        .setPeers(true);
      l.newLayer(multi);
      
      File csv = new File(getDir(), "multivalue.csv");
      int idColumn = 1;
      String[] columnLayer = {
        multiValueAttribute, // first field has multi-line value
        "", // (ID field is not the first column)
        multiValueAttribute }; // last field is mapped to the same layer as first
      int[] counts = l.uploadTranscriptAttributes(csv, idColumn, columnLayer);
      assertEquals("Correct number of counts returned " + Arrays.asList(counts),
                   2, counts.length);
      assertEquals("One transcript updated", 1, counts[0]);
      assertEquals("No missing transcripts", 0, counts[1]);
      
      String[] layerIds = { multiValueAttribute };
      Graph graph = l.getTranscript(transcript.getName(), layerIds);
      SortedSet<Annotation> attributes = graph.getAnnotations()
        .get(multiValueAttribute);
      assertEquals("Correct number of values: " + attributes,
                   4, attributes.size());
      Set<String> values = attributes.stream()
        .map(annotation->annotation.getLabel())
        .collect(Collectors.toSet());
      for (int v = 1; v <= 4; v++) {
        assertTrue("Value is present: " + v, values.contains(""+v));
      } // next value
      
    } finally {
      l.setVerbose(false);
      try {
        l.deleteTranscript(transcript.getName());
      } catch(ResponseException exception) {}
      try {
        l.deleteLayer(multiValueAttribute);
      } catch(ResponseException exception) {}
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
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.edit.transcript.attributes.TestUpload");
  }
}
