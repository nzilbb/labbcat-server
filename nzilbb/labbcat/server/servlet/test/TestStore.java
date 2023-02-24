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

package nzilbb.labbcat.server.servlet.test;
	      
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

  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.servlet.test.TestStore");
  }
}
