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

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import nzilbb.ag.Anchor;
import nzilbb.ag.Annotation;
import nzilbb.ag.Layer;
import nzilbb.ag.MediaFile;
import nzilbb.ag.MediaTrackDefinition;
import nzilbb.ag.PermissionException;
import nzilbb.ag.StoreException;
import nzilbb.labbcat.LabbcatView;
import nzilbb.labbcat.ResponseException;
import nzilbb.labbcat.model.Role;
import nzilbb.labbcat.model.User;
import nzilbb.labbcat.http.HttpRequestGet;

/**
 * These tests assume that there is a working LaBB-CAT instance with the latest version of
 * nzilbb.labbcat.server.jar installed.  
 */
public class TestPassword {
  static String labbcatUrl = "http://localhost:8080/labbcat/";
  static String username = "labbcat";
  static String password = "labbcat";
  static String readonly_username = "readonly";
  static String readonly_password = "labbcat";
  static LabbcatView l;
  static LabbcatView ro;
  
  @BeforeClass public static void setBaseUrl() throws MalformedURLException {
    
    try {
      l = new LabbcatView(labbcatUrl, username, password);
      l.setBatchMode(true);
      ro = new LabbcatView(labbcatUrl, readonly_username, readonly_password);
      ro.setBatchMode(true);
    } catch(MalformedURLException exception) {
      fail("Could not create Labbcat object");
    }
  }

  /** Ensures /api/password requests are correctly validated. */
  @Test public void validation() throws Exception {
    try {
      ro.changePassword(null, readonly_password);
      fail("Can't pass null current password");
    } catch(ResponseException x) {
      // check it's for the right reason
      assertEquals("Expected response status: "
                   + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                   400, x.getResponse().getHttpStatus());
    }
    try {
      ro.changePassword("", readonly_password);
      fail("Can't pass blank current password");
    } catch(ResponseException x) {
      // check it's for the right reason
      assertEquals("Expected response status: "
                   + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                   400, x.getResponse().getHttpStatus());
    }
    try {
      ro.changePassword(readonly_password, null);
      fail("Can't pass null new password");
    } catch(ResponseException x) {
      // check it's for the right reason
      assertEquals("Expected response status: "
                   + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                   400, x.getResponse().getHttpStatus());
    }
    try {
      ro.changePassword(readonly_password, "");
      fail("Can't pass blank new password");
    } catch(ResponseException x) {
      // check it's for the right reason
      assertEquals("Expected response status: "
                   + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                   400, x.getResponse().getHttpStatus());
    }
    try {
      ro.changePassword("incorrect current password", readonly_password);
      fail("Can't use incorrect current password");
    } catch(ResponseException x) {
      // check it's for the right reason
      assertEquals("Expected response status: "
                   + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                   403, x.getResponse().getHttpStatus());
    }
  }
   
  /** Test successful request to /api/password. */
  @Test public void changePassword() throws Exception {
    
    String changedPassword = readonly_password + " changed";
    
    // make a valid request
    ro.changePassword(readonly_password, changedPassword);

    // check the password was changed
    LabbcatView changedPasswordAccess = new LabbcatView(
      labbcatUrl, readonly_username, changedPassword);
    changedPasswordAccess.setBatchMode(true);

    // change it back
    changedPasswordAccess.changePassword(changedPassword, readonly_password);
  }
  
  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.TestPassword");
  }
}
