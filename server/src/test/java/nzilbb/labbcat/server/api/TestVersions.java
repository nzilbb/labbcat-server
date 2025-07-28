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

package nzilbb.labbcat.server.api;
	      
import org.junit.*;
import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.net.MalformedURLException;
import java.util.Map;
import nzilbb.util.IO;
import nzilbb.labbcat.LabbcatView;
import nzilbb.labbcat.ResponseException;

/**
 * Test the <tt>api/versions</tt> endpoint.
 * <p> These tests assume that there is a working LaBB-CAT instance with the latest version
 * of nzilbb.labbcat.server.jar installed.  
 */
public class TestVersions {
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

  /** Ensure expected version information is returned. */
  @Test public void versions()
    throws Exception {
    // labbcat.setVerbose(true);
    Map<String,Map<String,String>> versions = l.versionInfo();
    assertNotNull("Versions returned", versions);
    assertTrue("System info present", versions.containsKey("System"));
    assertTrue("LaBB-CAT version present", versions.get("System").containsKey("LaBB-CAT"));
    assertTrue("Database schema version present",
               versions.get("System").containsKey("Latest Upgrader"));
    assertTrue("nzilbb.ag version present",
               versions.get("System").containsKey("nzilbb.ag API"));
    assertTrue("nzilbb.labbcat.server version present",
               versions.get("System").containsKey("nzilbb.labbcat.server"));
    assertTrue("Format info present", versions.containsKey("Formats"));
    assertTrue("Annotator info present", versions.containsKey("Annotators"));
    assertTrue("3rd party info present", versions.containsKey("ThirdPartySoftware"));
    assertTrue("RDBMS info present", versions.containsKey("RDBMS"));
    assertTrue("RDBMS version present",
               versions.get("RDBMS").containsKey("version"));
  }

  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.TestVersions");
  }
}
