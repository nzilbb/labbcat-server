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

package nzilbb.labbcat.server.api.admin;
	      
import org.junit.*;
import static org.junit.Assert.*;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.stream.Collectors;
import javax.json.JsonArray;
import javax.json.JsonObject;
import nzilbb.ag.serialize.SerializationDescriptor;
import nzilbb.labbcat.LabbcatAdmin;
import nzilbb.labbcat.Response;
import nzilbb.labbcat.ResponseException;
import nzilbb.labbcat.http.HttpRequestGet;
import nzilbb.labbcat.http.HttpRequestPost;
import nzilbb.labbcat.http.HttpRequestPostMultipart;

/**
 * These tests assume that there is a working LaBB-CAT instance with the latest version of
 * nzilbb.labbcat.server.jar installed.
 */
public class TestUpgrade {
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
  
  /**
   * Test /api/admin/upgrade endpoint supports uploading an upgrader
   * and then deleting it.
   */
  @Test public void uploadDelete() throws Exception {

    // create an upgrader
    String newVersion = new SimpleDateFormat("yyyyMMdd.HHmm").format(new Date());
    File war = new File(getDir(), "nzilbb.labbcat.server.test.war");
    try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(war))) {
      zip.putNextEntry(new ZipEntry("version.txt"));
      zip.write(newVersion.getBytes());
      zip.closeEntry();
    }
    try {
      
      // upload upgrader
      HttpRequestPostMultipart upload = l.postMultipart("api/admin/upgrade")
        .setParameter("war", war);
      Response response = new Response(upload.post(), false);
      response.checkForErrors();
      assertEquals("Correct status",
                   200, response.getHttpStatus());
      JsonObject model = (JsonObject)response.getModel();
      String id = model.getString("id");
      assertNotNull("ID returned",
                    id);
      String oldVersion = model.getString("oldVersion");
      assertNotNull("Old version returned",
                    oldVersion);
      String newVersionCheck = model.getString("newVersion");
      assertNotNull("New version returned",
                    newVersionCheck);
      assertEquals("New version is correct",
                   newVersion, newVersionCheck);

      // delete upgrader
      HttpRequestPost delete = l.post("api/admin/upgrade/" + id)
        .setMethod("DELETE");
      response = new Response(delete.post(), false);
      response.checkForErrors();
      assertEquals("Correct status",
                   200, response.getHttpStatus());

      // can't delete it again
      delete = l.post("api/admin/upgrade/" + id)
        .setMethod("DELETE");
      try {
        response = new Response(delete.post(), false);
        response.checkForErrors();
        fail("Should fail when upload has already been deleted.");
      } catch(ResponseException exception) {
        System.out.println(exception.getMessage());
        assertEquals("Correct status",
                     404, exception.getResponse().getHttpStatus());
      }
      
    } finally {
      l.setVerbose(false);
    }
  }
  
  /**
   * Test /api/admin/upgrade endpoint supports uploading an upgrader
   * and then deleting it.
   */
  @Test public void upgrade() throws Exception {

    // create an upgrader (that upgrades only the version file)
    String newVersion = new SimpleDateFormat("yyyyMMdd.HHmm").format(new Date());
    File war = new File(getDir(), "nzilbb.labbcat.server.test.war");
    try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(war))) {
      zip.putNextEntry(new ZipEntry("version.txt"));
      zip.write(newVersion.getBytes());
      zip.closeEntry();
    }
    try {
      
      // upload upgrader
      HttpRequestPostMultipart upload = l.postMultipart("api/admin/upgrade")
        .setParameter("war", war);
      Response response = new Response(upload.post(), false);
      response.checkForErrors();
      assertEquals("Correct status",
                   200, response.getHttpStatus());
      JsonObject model = (JsonObject)response.getModel();
      String id = model.getString("id");
      assertNotNull("ID returned",
                    id);
      String oldVersion = model.getString("oldVersion");
      assertNotNull("Old version returned",
                    oldVersion);
      System.out.println("Upgrading from " + oldVersion + " to " + newVersion);
      String newVersionCheck = model.getString("newVersion");
      assertNotNull("New version returned",
                    newVersionCheck);
      assertEquals("New version is correct",
                   newVersion, newVersionCheck);

      // confirm upgrader
      HttpRequestPost put = l.post("api/admin/upgrade/" + id)
        .setMethod("PUT");
      response = new Response(put.post(), false);
      response.checkForErrors();
      assertEquals("Correct status",
                   200, response.getHttpStatus());
      
      // monitor upgrade until the new version is reported
      boolean running = true;
      boolean printedMessages = false;
      for (int i = 0; i < 60 && running; i++) {
        HttpRequestGet get = l.get("api/admin/upgrade");
        try {
          response = new Response(get.get(), false);
          response.checkForErrors();
          model = (JsonObject)response.getModel();
          if (!printedMessages) {
            assertTrue("Status includes messages",
                       model.containsKey("messages"));
            JsonArray messages = model.getJsonArray("messages");
            for (int m = 0; m < messages.size(); m++) {
              System.out.println(messages.getString(m));
              printedMessages = true;
            }
          } else {
            running = model.containsKey("threadId");
          }
        } catch(Exception x) {
          running = false;
        }
        Thread.sleep(1000);
      } // next check      

      // revert to the previous version file
      File revertWar = new File(getDir(), "revert.war");
      try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(revertWar))) {
        zip.putNextEntry(new ZipEntry("version.txt"));
        zip.write(oldVersion.getBytes());
        zip.closeEntry();
      }

      // upload upgrader
      System.out.println("Reverting to " + oldVersion);
      upload = l.postMultipart("api/admin/upgrade")
        .setParameter("war", revertWar);
      response = new Response(upload.post(), false);
      response.checkForErrors();
      assertEquals("Correct status",
                   200, response.getHttpStatus());
      model = (JsonObject)response.getModel();
      id = model.getString("id");
      assertNotNull("ID returned",
                    id);
      String newOldVersion = model.getString("oldVersion");
      assertNotNull("Old version returned",
                    newOldVersion);
      assertEquals("Old version is correct",
                   newVersion, newOldVersion);
      String newNewVersion = model.getString("newVersion");
      assertNotNull("New version returned",
                    newNewVersion);
      assertEquals("New version is correct",
                   oldVersion, newNewVersion);

      // confirm upgrader
      put = l.post("api/admin/upgrade/" + id)
        .setMethod("PUT");
      response = new Response(put.post(), false);
      response.checkForErrors();
      assertEquals("Correct status",
                   200, response.getHttpStatus());
      
      // monitor upgrade until the new version is reported
      running = true;
      for (int i = 0; i < 60 && running; i++) {
        HttpRequestGet get = l.get("api/admin/upgrade");
        try {
          response = new Response(get.get(), false);
          response.checkForErrors();
          model = (JsonObject)response.getModel();
          running = model.containsKey("threadId");
        } catch(Exception x) {
          running = false;
        }
        Thread.sleep(1000);
      } // next check            
    } finally {
      l.setVerbose(false);
    }
  }
  
  /**
   * Test /api/admin/validate endpoint parameter validation.
   */
  @Test public void validation()
    throws Exception {
    
    try {
      HttpRequestPostMultipart request = l.postMultipart("api/admin/upgrade");
      try {
        Response response = new Response(request.post(), false);
        response.checkForErrors();
        fail("Should fail when no war file is specified.");
      } catch(ResponseException exception) {
        System.out.println(exception.getMessage());
        assertEquals("Correct status",
                     400, exception.getResponse().getHttpStatus());
      }

      File nonWar = new File(new File(getDir().getParentFile(), "edit"),
                             "nzilbb.labbcat.server.test.txt");
      assertTrue("File exists", nonWar.exists());
      request = l.postMultipart("api/admin/upgrade")
        .setParameter("war", nonWar);
      try {
        Response response = new Response(request.post(), false);
        response.checkForErrors();
        fail("Should fail when non-war file is specified.");
      } catch(ResponseException exception) {
        System.out.println(exception.getMessage());
        assertEquals("Correct status",
                     400, exception.getResponse().getHttpStatus());
      }

      File invalidWar = new File(getDir(), "invalid.war");
      try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(invalidWar))) {
        zip.putNextEntry(new ZipEntry("not-version.txt"));
        zip.write("some content".getBytes());
        zip.closeEntry();
      }
      assertTrue("File exists", invalidWar.exists());
      request = l.postMultipart("api/admin/upgrade")
        .setParameter("war", invalidWar);
      try {
        Response response = new Response(request.post(), false);
        response.checkForErrors();
        fail("Should fail when war file with incorrect structure is specified.");
      } catch(ResponseException exception) {
        System.out.println(exception.getMessage());
        assertEquals("Correct status",
                     400, exception.getResponse().getHttpStatus());
      }

      String invalidId = "invalid_id";
      
      // delete with no ID
      HttpRequestPost delete = l.post("api/admin/upgrade/")
        .setMethod("DELETE");
      try {
        Response response = new Response(delete.post(), false);
        response.checkForErrors();
        fail("Should fail when deleting with no ID specified.");
      } catch(ResponseException exception) {
        System.out.println(exception.getMessage());
        assertEquals("Correct status",
                     400, exception.getResponse().getHttpStatus());
      }

      // delete with invalid ID
      delete = l.post("api/admin/upgrade/" + invalidId)
        .setMethod("DELETE");
      try {
        Response response = new Response(delete.post(), false);
        response.checkForErrors();
        fail("Should fail when deleting a nonexistent ID.");
      } catch(ResponseException exception) {
        System.out.println(exception.getMessage());
        assertEquals("Correct status",
                     404, exception.getResponse().getHttpStatus());
      }

      // put with no ID
      HttpRequestPost put = l.post("api/admin/upgrade/")
        .setMethod("PUT");
      try {
        Response response = new Response(put.post(), false);
        response.checkForErrors();
        fail("Should fail when deleting with no ID specified.");
      } catch(ResponseException exception) {
        System.out.println(exception.getMessage());
        assertEquals("Correct status",
                     400, exception.getResponse().getHttpStatus());
      }

      // put with invalid ID
      put = l.post("api/admin/upgrade/" + invalidId)
        .setMethod("PUT");
      try {
        Response response = new Response(put.post(), false);
        response.checkForErrors();
        fail("Should fail when deleting a nonexistent ID.");
      } catch(ResponseException exception) {
        System.out.println(exception.getMessage());
        assertEquals("Correct status",
                     404, exception.getResponse().getHttpStatus());
      }
    } finally {
      l.setVerbose(false);
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
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.edit.participants.layers.TestUpgrade");
  }
}
