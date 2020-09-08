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
import nzilbb.labbcat.model.MediaTrack;
import nzilbb.labbcat.http.HttpRequestGet;

/**
 * These tests assume that there is a working LaBB-CAT instance with the latest version of
 * nzilbb.labbcat.server.jar installed.  
 */
public class TestAdminMediaTracks
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
   

   @Test public void newMediaTrackUpdateMediaTrackAndDeleteMediaTrack() throws Exception {
      MediaTrack originalMediaTrack = new MediaTrack()
         .setSuffix("unit-test")
         .setDescription("Temporary mediaTrack for unit testing")
         .setDisplayOrder(99);
      
      try {
         MediaTrack newMediaTrack = l.createMediaTrack(originalMediaTrack);
         assertNotNull("MediaTrack returned", newMediaTrack);
         assertEquals("Name correct",
                      originalMediaTrack.getSuffix(), newMediaTrack.getSuffix());
         assertEquals("Description correct",
                      originalMediaTrack.getDescription(), newMediaTrack.getDescription());
         assertEquals("Display order correct",
                      originalMediaTrack.getDisplayOrder(), newMediaTrack.getDisplayOrder());
         
         try {
            l.createMediaTrack(originalMediaTrack);
            fail("Can't create a mediaTrack with existing name");
         }
         catch(Exception exception) {}
         
         MediaTrack[] mediaTracks = l.readMediaTracks();
         // ensure the mediaTrack exists
         assertTrue("There's at least one mediaTrack", mediaTracks.length >= 1);
         boolean found = false;
         for (MediaTrack c : mediaTracks) {
            if (c.getSuffix().equals(originalMediaTrack.getSuffix())) {
               found = true;
               break;
            }
         }
         assertTrue("MediaTrack was added", found);

         // update it
         MediaTrack updatedMediaTrack = new MediaTrack()
            .setSuffix("unit-test")
            .setDescription("Changed description")
            .setDisplayOrder(100);
         
         MediaTrack changedMediaTrack = l.updateMediaTrack(updatedMediaTrack);
         assertNotNull("MediaTrack returned", changedMediaTrack);
         assertEquals("Updated Name correct",
                      updatedMediaTrack.getSuffix(), changedMediaTrack.getSuffix());
         assertEquals("Updated Description correct",
                      updatedMediaTrack.getDescription(), changedMediaTrack.getDescription());
         assertEquals("Updated Display order correct",
                      updatedMediaTrack.getDisplayOrder(), changedMediaTrack.getDisplayOrder());

         // delete it
         l.deleteMediaTrack(originalMediaTrack.getSuffix());

         MediaTrack[] mediaTracksAfter = l.readMediaTracks();
         // ensure the mediaTrack no longer exists
         boolean foundAfter = false;
         for (MediaTrack c : mediaTracksAfter) {
            if (c.getSuffix().equals(originalMediaTrack.getSuffix())) {
               foundAfter = true;
               break;
            }
         }
         assertFalse("MediaTrack is gone", foundAfter);

         try {
            // can't delete it again
            l.deleteMediaTrack(originalMediaTrack);
            fail("Can't delete mediaTrack that doesn't exist");
         } catch(Exception exception) {
         }

      } finally {
         // ensure it's not there
         try {
            l.deleteMediaTrack(originalMediaTrack);
         } catch(Exception exception) {}         
     }
   }
   
   @Test public void canAddDeleteMediaTrackWithNoSuffix() throws Exception {

      // if the test system has one already, ensure we restore it afterwards
      MediaTrack existingTrack = null;
      MediaTrack[] mediaTracks = l.readMediaTracks();
      for (MediaTrack c : mediaTracks) {
         if (c.getSuffix().length() == 0) {
            existingTrack = c;
            break;
         }
      }
      
      try {

         if (existingTrack != null) l.deleteMediaTrack(existingTrack);

         MediaTrack testTrack = new MediaTrack()
            .setSuffix("")
            .setDescription("Media")
            .setDisplayOrder(0);
         l.createMediaTrack(testTrack);
         l.deleteMediaTrack(testTrack);

      } finally {
         // put the original track back
         if (existingTrack != null) l.createMediaTrack(existingTrack);
     }
   }
   
   @Test public void readonlyAccessEnforced() throws IOException, StoreException, PermissionException {
      // create a mediaTrack to work with
      MediaTrack testMediaTrack = new MediaTrack()
         .setSuffix("unit-test")
         .setDescription("Temporary mediaTrack for unit testing")
         .setDisplayOrder(99);
      
      try {
         l.createMediaTrack(testMediaTrack);

         // now try operations with read-only ID, all should fail because of lack of authorization

         try {
            ro.createMediaTrack(testMediaTrack);
            fail("Can't create a mediaTrack as non-admin user");
         } catch(ResponseException x) {
            // check it's for the right reason
            assertEquals("Create failed for lack of auth: "
                         + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                         403, x.getResponse().getHttpStatus());
         }

         try {
            ro.readMediaTracks();
            fail("Can't read mediaTracks as non-admin user");
         } catch(ResponseException x) {
            // check it's for the right reason
            assertEquals("Read failed for lack of auth: "
                         + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                         403, x.getResponse().getHttpStatus());
         }
         
         try {
            ro.updateMediaTrack(testMediaTrack);
            fail("Can't update a mediaTrack as non-admin user");
         } catch(ResponseException x) {
            // check it's for the right reason
            assertEquals("Update failed for lack of auth: "
                         + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                         403, x.getResponse().getHttpStatus());
         }
         
         try {
            ro.deleteMediaTrack(testMediaTrack);
            fail("Can't delete mediaTrack as non-admin user");
         } catch(ResponseException x) {
            // check it's for the right reason
            assertEquals("Create failed for lack of auth: "
                         + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                         403, x.getResponse().getHttpStatus());
         }
      
      } finally {         
         try { // ensure test mediaTrack is deleted
            l.deleteMediaTrack(testMediaTrack);
         } catch(Exception exception) {}         
      }
   }

   public static void main(String args[]) {
      org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.servlet.TestAdminMediaTracks");
   }
}
