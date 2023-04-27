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

package nzilbb.labbcat.server.servlet;
	      
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
import nzilbb.labbcat.model.Corpus;
import nzilbb.labbcat.http.HttpRequestGet;

/**
 * These tests assume that there is a working LaBB-CAT instance with the latest version of
 * nzilbb.labbcat.server.jar installed.  
 */
public class TestAdminCorpora
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
   
   @Test public void validation() throws Exception {
      try {
         l.createCorpus(new Corpus());
         fail("Can't create a corpus with null name");
      }
      catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Create failed for lack of name: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      400, x.getResponse().getHttpStatus());
      }
      
      try {
         l.createCorpus(new Corpus().setName(""));
         fail("Can't create a corpus with blank name");
      }
      catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Create failed for blank name: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      400, x.getResponse().getHttpStatus());
      }

      try {
         l.createCorpus(new Corpus().setName("\t "));
         fail("Can't create a corpus with all-whitespace name");
      }
      catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Create failed for all-whitespace name: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      400, x.getResponse().getHttpStatus());
      }
 }
   
   @Test public void newCorpusUpdateCorpusAndDeleteCorpus() throws Exception {
      Corpus originalCorpus = new Corpus()
         .setName("unit-test")
         .setLanguage("en")
         .setDescription("Temporary corpus for unit testing");
      try {
         Corpus newCorpus = l.createCorpus(originalCorpus);
         assertNotNull("Corpus returned", newCorpus);
         assertEquals("Name correct",
                      originalCorpus.getName(), newCorpus.getName());
         assertEquals("Language correct",
                      originalCorpus.getLanguage(), newCorpus.getLanguage());
         assertEquals("Description correct",
                      originalCorpus.getDescription(), newCorpus.getDescription());

         try {
            l.createCorpus(originalCorpus);
            fail("Can't create a corpus with existing name");
         }
         catch(ResponseException exception) {
            // check it's for the right reason
         }

         Corpus[] corpora = l.readCorpora();
         // ensure the corpus exists
         assertTrue("There's at least one corpus", corpora.length >= 1);
         boolean found = false;
         for (Corpus c : corpora) {
            if (c.getName().equals(originalCorpus.getName())) {
               found = true;
               break;
            }
         }
         assertTrue("Corpus was added", found);

         // update it
         Corpus updatedCorpus = new Corpus()
            .setName("unit-test")
            .setLanguage("es")
            .setDescription("Temporary Spanish corpus for unit testing");

         Corpus changedCorpus = l.updateCorpus(updatedCorpus);
         assertNotNull("Corpus returned", changedCorpus);
         assertEquals("Updated Name correct",
                      updatedCorpus.getName(), changedCorpus.getName());
         assertEquals("Updated Language correct",
                      updatedCorpus.getLanguage(), changedCorpus.getLanguage());
         assertEquals("Updated Description correct",
                      updatedCorpus.getDescription(), changedCorpus.getDescription());

         // delete it
         l.deleteCorpus(originalCorpus.getName());

         Corpus[] corporaAfter = l.readCorpora();
         // ensure the corpus no longer exists
         boolean foundAfter = false;
         for (Corpus c : corporaAfter) {
            if (c.getName().equals(originalCorpus.getName())) {
               foundAfter = true;
               break;
            }
         }
         assertFalse("Corpus is gone", foundAfter);

         try {
            // can't delete it again
            l.deleteCorpus(originalCorpus);
            fail("Can't delete corpus that doesn't exist");
         } catch(Exception exception) {
         }

      } finally {
         // ensure it's not there
         try {
            l.deleteCorpus(originalCorpus);
         } catch(Exception exception) {}         
     }
   }
   
   @Test public void readonlyAccessEnforced() throws IOException, StoreException, PermissionException {
      // create a corpus to work with
      Corpus testCorpus = new Corpus()
         .setName("unit-test")
         .setLanguage("en")
         .setDescription("Temporary corpus for unit testing");
      
      try {
         l.createCorpus(testCorpus);

         // now try operations with read-only ID, all should fail because of lack of authorization

         try {
            ro.createCorpus(testCorpus);
            fail("Can't create a corpus as non-admin user");
         } catch(ResponseException x) {
            // check it's for the right reason
            assertEquals("Create failed for lack of auth: "
                         + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                         403, x.getResponse().getHttpStatus());
         }

         try {
            ro.readCorpora();
            fail("Can't read corpora as non-admin user");
         } catch(ResponseException x) {
            // check it's for the right reason
            assertEquals("Read failed for lack of auth: "
                         + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                         403, x.getResponse().getHttpStatus());
         }
         
         try {
            ro.updateCorpus(testCorpus);
            fail("Can't update a corpus as non-admin user");
         } catch(ResponseException x) {
            // check it's for the right reason
            assertEquals("Update failed for lack of auth: "
                         + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                         403, x.getResponse().getHttpStatus());
         }
         
         try {
            ro.deleteCorpus(testCorpus.getName());
            fail("Can't delete corpus as non-admin user");
         } catch(ResponseException x) {
            // check it's for the right reason
            assertEquals("Create failed for lack of auth: "
                         + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                         403, x.getResponse().getHttpStatus());
         }
      
      } finally {         
         try { // ensure test corpus is deleted
            l.deleteCorpus(testCorpus);
         } catch(Exception exception) {}         
      }
   }

   public static void main(String args[]) {
      org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.servlet.test.TestAdminCorpora");
   }
}
