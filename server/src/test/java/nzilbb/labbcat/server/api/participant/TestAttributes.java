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

package nzilbb.labbcat.server.api.participant;
	      
import org.junit.*;
import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.net.MalformedURLException;
import nzilbb.util.IO;
import nzilbb.labbcat.LabbcatView;

/**
 * Test the <tt>api/participant/attributes</tt> endpoint.
 * <p> These tests assume that there is a working LaBB-CAT instance with the latest version
 * of nzilbb.labbcat.server.jar installed.  
 */
public class TestAttributes {
  static String labbcatUrl = "http://localhost:8080/labbcat/";
  static String username = "labbcat";
  static String password = "labbcat";
  static LabbcatView l;

  @BeforeClass public static void setBaseUrl() throws MalformedURLException {
    
    try {
      l = new LabbcatView(labbcatUrl, username, password);
      l.setBatchMode(true);
    } catch(MalformedURLException exception) {
      fail("Could not create Labbcat object");
    }
  }

  /** Test that endpoint returns correct requested CSV data. */
  @Test public void participantAttributes()
    throws Exception {
    // get a participant ID to use
    String[] ids = l.getMatchingParticipantIds("/BR.+/.test(id)");
    assertTrue("Some IDs are returned", ids.length > 0);
    String[] layerIds = {
      "participant_gender", "participant_notes", "invalid_attribute"};
    File csv = l.getParticipantAttributes(ids, layerIds);
    assertNotNull("File returned", csv);
    assertTrue("File exists", csv.exists());
    String data = IO.InputStreamToString(new FileInputStream(csv));
    System.out.println(data);
    String[] lines = data.split("\n");
    assertEquals("Correct columns are returned, including invalid attributes",
                 "participant,participant_gender,participant_notes,invalid_attribute",
                 lines[0]);
    csv.delete();
  }

  /** Test invalid participant request */
  @Test public void invalidParticipant() throws Exception {
    String[] layerIds = {
      "participant_gender", "participant_notes", "invalid_attribute"};
    String[] invalidId = { "invalid-id" };
    File csv = l.getParticipantAttributes(invalidId, layerIds);
    assertNotNull("File returned", csv);
    assertTrue("File exists", csv.exists());
    String data = IO.InputStreamToString(new FileInputStream(csv));
    System.out.println(data);
    String[] lines = data.split("\n");
    assertEquals("Correct columns are returned, including invalid attributes",
                 "participant,participant_gender,participant_notes,invalid_attribute",
                 lines[0]);
    assertEquals("No data rows returned", 1, lines.length);
    csv.delete();
  }
  
  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.participant.TestAttributes");
  }
}
