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
public class TestAdminSystemAttributes
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
   
   
   @Test public void readSystemAttributeAndUpdateSystemAttribute() throws Exception {
      SystemAttribute[] systemAttributes = l.readSystemAttributes();
      // ensure the systemAttribute exists
      assertTrue("There's at least one systemAttribute", systemAttributes.length >= 1);
      SystemAttribute originalTitle = null;
      SystemAttribute originalTranscriptSubdir = null;
      for (SystemAttribute a : systemAttributes) {
         if (a.getAttribute().equals("title")) {
            originalTitle = a;
         } else if (a.getAttribute().equals("transcriptSubdir")) {
            originalTranscriptSubdir = a;
         }
      }
      assertNotNull("title attribute returned", originalTitle);
      assertNotNull("title attribute has value", originalTitle.getValue());
      assertNotNull("transcriptSubdir attribute returned", originalTranscriptSubdir);
      assertNotNull("transcriptSubdir attribute has value", originalTranscriptSubdir.getValue());
      assertEquals("transcriptSubdir attribute is read-only",
                   "readonly", originalTranscriptSubdir.getType());

      try {
         // update it
         SystemAttribute updatedTitle = new SystemAttribute()
            .setAttribute("title")
            .setValue("unit-test") // should be updated
            .setType("Changed type") // should not be updated
            .setStyle("Changed style") // should not be updated
            .setLabel("Changed label"); // should not be updated
      
         SystemAttribute changedTitle = l.updateSystemAttribute(updatedTitle);
         assertNotNull("SystemAttribute returned after object update", changedTitle);
         assertEquals("Updated Value correct after object update",
                      updatedTitle.getValue(), changedTitle.getValue());

         SystemAttribute[] systemAttributesAfter = l.readSystemAttributes();
         // ensure only the value has been updated
         SystemAttribute listedTitle = null;
         for (SystemAttribute a : systemAttributesAfter) {
            if (a.getAttribute().equals("title")) {
               listedTitle = a;
               break;
            }
         }
         assertNotNull("SystemAttribute is still there", listedTitle);
         assertEquals("Updated Value correct", updatedTitle.getValue(), listedTitle.getValue());
         assertEquals("type unchanged", originalTitle.getType(), listedTitle.getType());
         assertEquals("style unchanged", originalTitle.getStyle(), listedTitle.getStyle());
         assertEquals("label unchanged", originalTitle.getLabel(), listedTitle.getLabel());
         assertEquals("description unchanged",
                      originalTitle.getDescription(), listedTitle.getDescription());

         changedTitle = l.updateSystemAttribute("title", "updated-value");
         assertNotNull("SystemAttribute returned after string update", changedTitle);
         assertEquals("Updated Value correct after string update",
                      "updated-value", changedTitle.getValue());
         
         try {
            SystemAttribute updatedTranscriptSubdir = new SystemAttribute()
               .setAttribute("transcriptSubdir")
               .setValue("unit-test");
            // can't update read-only attribute
            l.updateSystemAttribute(updatedTranscriptSubdir);
            // if we got here, it was incorrectly updated, so put back the original value
            l.updateSystemAttribute(originalTranscriptSubdir);
            // ... and then fail
            fail("Can't update read-only attribute");
         } catch(Exception exception) {
         }
         
         try {
            SystemAttribute nonexistentAttribute = new SystemAttribute()
               .setAttribute("unit-test")
               .setValue("unit-test");
            // can't update read-only attribute
            l.updateSystemAttribute(nonexistentAttribute);
            fail("Can't update nonexistent attribute");
         } catch(Exception exception) {
         }
         
      } finally {
         // put back original title
         try {
            l.updateSystemAttribute(originalTitle);
         } catch(Exception exception) {
            System.out.println("ERROR restoring title: " + exception);
         }
     }
   }
   
   @Test public void readonlyAccessEnforced()
      throws IOException, StoreException, PermissionException {
      try {
         ro.readSystemAttributes();
         fail("Can't read system attributes as non-admin user");
      } catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Read failed for lack of auth: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      403, x.getResponse().getHttpStatus());
      }
      
      try {
         ro.updateSystemAttribute("title", "LaBB-CAT");
         fail("Can't update a system attribute as non-admin user");
      } catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Update failed for lack of auth: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      403, x.getResponse().getHttpStatus());
      }      
   }

   public static void main(String args[]) {
      org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.servlet.TestAdminSystemAttributes");
   }
}
