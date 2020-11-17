//
// Copyright 2020 New Zealand Institute of Language, Brain and Behaviour, 
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
import nzilbb.ag.Layer;
import nzilbb.ag.MediaFile;
import nzilbb.ag.MediaTrackDefinition;
import nzilbb.ag.PermissionException;
import nzilbb.ag.StoreException;
import nzilbb.labbcat.LabbcatAdmin;
import nzilbb.labbcat.ResponseException;
import nzilbb.labbcat.model.SystemAttribute;
import nzilbb.labbcat.http.HttpRequestGet;

/**
 * These tests assume that there is a working LaBB-CAT instance with the latest version of
 * nzilbb.labbcat.server.jar installed.  
 */
public class TestAdminInfo
{
   static String labbcatUrl = "http://localhost:8080/labbcat/";
   static String username = "labbcat";
   static String password = "labbcat";
   static String readonly_username = "readonly";
   static String readonly_password = "labbcat";
   static LabbcatAdmin l;
   static LabbcatAdmin ro;

   @BeforeClass public static void setBaseUrl() throws MalformedURLException {

      try {
         l = new LabbcatAdmin(labbcatUrl, username, password);
         l.setBatchMode(true);
         ro = new LabbcatAdmin(labbcatUrl, readonly_username, readonly_password);
         ro.setBatchMode(true);
      } catch(MalformedURLException exception) {
         fail("Could not create Labbcat object");
      }
   }
   
   
   @Test public void updateInfo() throws Exception {
      String originalInfo = l.getInfo();
      assertNotNull("There is info", originalInfo);
      
      try {
         // update it
         String changedInfo = originalInfo + " <div>unit-test</div>";
         l.updateInfo(changedInfo);
         
         String newInfo = l.getInfo();
         assertEquals("Updated info correct", changedInfo, newInfo);         
         
      } finally {
         l.updateInfo(originalInfo);
      }
   }

   
   @Test public void readonlyAccessEnforced()
      throws IOException, StoreException, PermissionException {
      try {
         ro.updateInfo("<p>readonlyAccessEnforced</p>");
         fail("Can't update info as non-admin user");
      } catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Read failed for lack of auth: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      403, x.getResponse().getHttpStatus());
      }
   }

   public static void main(String args[]) {
      org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.servlet.test.TestAdminInfo");
   }
}
