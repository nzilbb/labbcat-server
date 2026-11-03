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

package nzilbb.labbcat.server.api.edit.participants.attributes;
	      
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
  
  /** Test basic participant attributes upload from CSV. */
  @Test public void uploadParticipantAttributes() throws Exception {

    String existingParticipantId = "UnitTester";
    String createdParticipantId1 = "UnitTester-created-1";
    String createdParticipantId2 = "UnitTester-created-2";
    
    try {
      // create participant for testing
      Annotation participant = new Annotation(null, existingParticipantId, "participant");
      assertTrue("Test participant created", l.saveParticipant(participant));
      participant = l.getParticipant(existingParticipantId, null);
      assertNotNull("Participant exists", participant);
      participant = l.getParticipant(createdParticipantId1, null);
      assertNull("Participant1 to create doesn't exist", participant);
      participant = l.getParticipant(createdParticipantId2, null);
      assertNull("Participant2 to create doesn't exist", participant);

      File csv = new File(getDir(), "participants.csv");
      int idColumn = 0;
      String[] columnLayer = {
        null, "participant_gender", "", "participant_notes", "_password" };
      int[] counts = l.uploadParticipantAttributes(csv, idColumn, columnLayer);
      assertEquals("Correct number of counts returned " + Arrays.asList(counts),
                   2, counts.length);
      assertEquals("One participant updated", 1, counts[0]);
      assertEquals("Two participant created", 2, counts[1]);
      // csv includes four rows, one is ignored because no ID is specified
      
      String[] layerIds = { "participant_gender", "participant_notes", "_password" };
      participant = l.getParticipant(existingParticipantId, layerIds);
      assertNotNull("Participant still exists", participant);
      assertNotNull("Gender exists", participant.first("participant_gender"));
      assertEquals("Gender correct",
                   "X", participant.first("participant_gender").getLabel());
      assertNotNull("Notes exist", participant.first("participant_notes"));
      assertEquals("Notes correct",
                   "UnitTester notes", participant.first("participant_notes").getLabel());
      assertNull("Password not returned", participant.first("_password"));

      participant = l.getParticipant(createdParticipantId1, layerIds);
      assertNotNull("Participant created", participant);
      assertNotNull("Gender exists", participant.first("participant_gender"));
      assertEquals("Gender correct",
                   "Y", participant.first("participant_gender").getLabel());
      assertNotNull("Notes exist", participant.first("participant_notes"));
      assertEquals("Notes correct",
                   "New one", participant.first("participant_notes").getLabel());
      
      participant = l.getParticipant(createdParticipantId2, layerIds);
      assertNotNull("Participant created", participant);
      assertNotNull("Gender exists", participant.first("participant_gender"));
      assertEquals("Gender correct",
                   "Z", participant.first("participant_gender").getLabel());
      assertNotNull("Notes exist", participant.first("participant_notes"));
      assertEquals("Notes correct",
                   "New two", participant.first("participant_notes").getLabel());
      
    } finally {
      l.setVerbose(false);
      try {
        l.deleteParticipant(existingParticipantId);
      } catch(ResponseException exception) {}
      try {
        l.deleteParticipant(createdParticipantId1);
      } catch(ResponseException exception) {}
      try {
        l.deleteParticipant(createdParticipantId2);
      } catch(ResponseException exception) {}
    }
  }
    
  /** Test parameter validation. */
  @Test public void invalidParameters() throws Exception {

    File csv = new File(getDir(), "participants.csv");
    int idColumn = 0;
    String[] columnLayer = { null, "participant_gender", "", "participant_notes" };
    try {
      l.uploadParticipantAttributes(null, idColumn, columnLayer);
      fail("Should fail when no CSV is supplied");
    } catch(Exception exception) {
      System.out.println(exception.toString());
    }
    
    try {
      l.uploadParticipantAttributes(csv, 100, columnLayer);
      fail("Should fail when invalid ID column is supplied");
    } catch(Exception exception) {
      System.out.println(exception.toString());
    }
    
    try {
      l.uploadParticipantAttributes(csv, idColumn, null);
      fail("Should fail when no column-to-layer mapping is supplied");
    } catch(Exception exception) {
      System.out.println(exception.toString());
    }
    
    try {
      String[] moreColumnsThanCsv = {
        null, "participant_gender", "", "participant_notes", "", "participant_notes" };
      l.uploadParticipantAttributes(csv, idColumn, moreColumnsThanCsv);
      fail("Should fail when more column mappings than columns are supplied");
    } catch(Exception exception) {
      System.out.println(exception.toString());
    }
    
    try {
      String[] invalidLayer = { null, "nonexistent", "", "participant_notes" };
      l.uploadParticipantAttributes(csv, idColumn, invalidLayer);
      fail("Should fail when nonexistent layer is specified");
    } catch(Exception exception) {
      System.out.println(exception.toString());
    }
    
    try {
      String[] invalidLayer = { null, "corpus", "", "participant_notes" };
      l.uploadParticipantAttributes(csv, idColumn, invalidLayer);
      fail("Should fail when non-participant-attribute layer is specified");
    } catch(Exception exception) {
      System.out.println(exception.toString());
    }
  }
    
  /** Test multi-value attribute correctly accumulate values across lines and fields. */
  @Test public void multivalueAttribute() throws Exception {

    String participantId = "UnitTester";
    String multiValueAttribute = "participant_test_multivalue";

    try { // ensure participant doesn't exist, so we know it'll be created
      l.deleteParticipant(participantId);
    } catch(ResponseException exception) {}
    
    try {
      // create a multi-value participant attribute for testing
      Layer multi = new Layer(multiValueAttribute, "Unit test attribute")
        .setParentId("participant")
        .setPeers(true);
      l.newLayer(multi);

      File csv = new File(getDir(), "multivalue.csv");
      int idColumn = 1;
      String[] columnLayer = {
        multiValueAttribute, // first field has multi-line value
        "", // (ID field is not the first column)
        multiValueAttribute }; // last field is mapped to the same layer as first
      int[] counts = l.uploadParticipantAttributes(csv, idColumn, columnLayer);
      assertEquals("Correct number of counts returned " + Arrays.asList(counts),
                   2, counts.length);
      assertEquals("No participant updated", 0, counts[0]);
      assertEquals("One participants created", 1, counts[1]);
      
      String[] layerIds = { multiValueAttribute };
      Annotation participant = l.getParticipant(participantId, layerIds);
      assertNotNull("Participant still exists", participant);
      SortedSet<Annotation> attributes = participant.getAnnotations()
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
        l.deleteParticipant(participantId);
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
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.edit.participant.attributes.TestUpload");
  }
}
