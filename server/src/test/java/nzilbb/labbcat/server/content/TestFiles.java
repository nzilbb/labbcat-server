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

package nzilbb.labbcat.server.content;
	      
import org.junit.*;
import static org.junit.Assert.*;

import java.net.MalformedURLException;
import java.util.Map;
import java.util.Random;
import java.net.URL;
import java.net.URLEncoder;
import java.net.HttpURLConnection;
import nzilbb.ag.Graph;
import nzilbb.ag.MediaFile;
import nzilbb.labbcat.LabbcatAdmin;
import nzilbb.labbcat.LabbcatView;
import nzilbb.labbcat.ResponseException;
import nzilbb.labbcat.PatternBuilder;
import nzilbb.labbcat.http.HttpRequestGet;
import nzilbb.labbcat.model.User;
import nzilbb.labbcat.model.Role;
import nzilbb.labbcat.model.RolePermission;
import javax.json.JsonObject;

/**
 * Test the <tt>files/...</tt> endpoints.
 * <p> These tests assume that there is a working LaBB-CAT instance with the latest version
 * of nzilbb.labbcat.server.jar installed, and the first transcript (alphabetically)
 * has at least one media file available.
 */
public class TestFiles {
  static String labbcatUrl = "http://localhost:8080/labbcat/";
  static String username = "labbcat";
  static String password = "labbcat";
  static LabbcatAdmin l; // admin access
  static LabbcatView r; // restricted access
  static URL transcriptUrl;
  static URL mediaUrl;
  static URL invalidUrl;
  static URL otherUrl;
  static URL backupFileUrl;
  static Role role;
  static RolePermission rolePermission;
  static User user;
  static String userPassword;
  
  @BeforeClass public static void getUrlsSetupRestrictedUser() throws MalformedURLException {
    try {
      l = new LabbcatAdmin(labbcatUrl, username, password);
      l.setBatchMode(true);
    } catch(MalformedURLException exception) {
      fail("Could not create Labbcat object");
    }

    try {
      String[] ids = l.getTranscriptIds();
      // for (String id : ids) System.out.println("graph " + id);
      assertTrue("getTranscriptIds: Some IDs are returned",
                 ids.length > 0);
      String transcriptId = ids[0];
      
      String[] layerIds = { "corpus", "episode" };
      Graph graph = l.getTranscript(transcriptId, layerIds);
      String corpus = graph.first("corpus").getLabel();
      String episode = graph.first("episode").getLabel();
      transcriptUrl = l.makeUrl(
        "files/"+URLEncoder.encode(corpus)+"/" +URLEncoder.encode(episode)+"/trs/"
        +URLEncoder.encode(transcriptId));
      
      MediaFile[] media = l.getAvailableMedia(transcriptId); 
      assertTrue("getAvailableMedia: Some media files are returned",
                 media.length > 0);
      mediaUrl = new URL(media[0].getUrl());
      
      invalidUrl = l.makeUrl(
        "files/"+URLEncoder.encode(corpus)+"/" +URLEncoder.encode(episode)+"/trs/"
        +URLEncoder.encode(transcriptId+"-"+transcriptId)); // invalid file name
      
      otherUrl = l.makeUrl("files/trans-13.dtd"); // existing non-transcript file

      // existing backup files on the server should be inaccessible
      // (comment this out if you don't have a backup file on the test server)
      backupFileUrl = l.makeUrl(
        "files/QB/AP511_MikeThorpe/mp4/AP511_MikeThorpe.mp4.bak-2025-06-23-18-00-10.mp4");
      
    } catch (Exception x) {
      fail("Could not generate URLs: " + x);
    }

    try {
      // create a read-only user
      role = new Role().setRoleId("unit-test").setDescription("For testing only");
      l.createRole(role);
      rolePermission = l.createRolePermission(
        new RolePermission().setRoleId(role.getRoleId())
        .setLayerId("corpus").setValuePattern(".*") // will not match
        .setEntity("t")); // transcript allowed, nothing else

      String[] userRoles = { "view", role.getRoleId() };
      user = l.createUser(new User().setUser("unit-test").setRoles(userRoles));

      userPassword = randomString();
      l.setPassword(user.getUser(), userPassword, false);
      
      r = new LabbcatView(labbcatUrl, user.getUser(), userPassword);
      r.setBatchMode(true);
      
    } catch (Exception x) {
      fail("Could not set up restricted access: " + x);
    }
  }

  /** Thanks <a href="https://www.baeldung.com/java-random-string">Baeldung</a> */
  private static String randomString() {
    int leftLimit = 97; // letter 'a'
    int rightLimit = 122; // letter 'z'
    int targetStringLength = 10;
    Random random = new Random();
    return random.ints(leftLimit, rightLimit + 1)
      .limit(targetStringLength)
      .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
      .toString();
  }
        
  @AfterClass public static void removeRestrictedUser() throws MalformedURLException {
    try {
      l.deleteUser(user);
    } catch(Exception exception) {}
    try {
      l.deleteRolePermission(rolePermission);
    } catch(Exception exception) {}
    try {
      l.deleteRole(role);
    } catch(Exception exception) {}
  }
  
  /** Ensure correct media is available to admin users. */
  @Test public void adminAccess() throws Exception {

    // should be able to access the transcript file
    HttpRequestGet request = l.get(transcriptUrl.toString());
    HttpURLConnection connection = request.get();
    assertEquals("Can get transcript " + transcriptUrl,
                 200, connection.getResponseCode());
    
    request = l.get(mediaUrl.toString());
    connection = request.get();
    assertEquals("Can get media " + mediaUrl,
                 200, connection.getResponseCode());
    
    // shouldn't be able to access an invalid file
    request = l.get(invalidUrl.toString());
    connection = request.get();
    assertEquals("Can't get nonexistent transcript " + invalidUrl,
                 404, connection.getResponseCode());
    
    // should be able to access an unrelated file
    request = l.get(otherUrl.toString());
    connection = request.get();
    assertEquals("Can get existing non transcript file " + otherUrl,
                 200, connection.getResponseCode());
    
    // shouldn't be able to access a backup file
    if (backupFileUrl != null) {
      request = l.get(backupFileUrl.toString());
      connection = request.get();
      assertEquals("Can't get existing backup file " + backupFileUrl,
                   403, connection.getResponseCode());
    }
  }
  
  /** Ensure correct media is available to restricted users. */
  @Test public void restrictedAccess() throws Exception {

    assertNotNull(r);
    assertNotNull(transcriptUrl);
    HttpRequestGet request = r.get(transcriptUrl.toString());
    HttpURLConnection connection = request.get();
    assertEquals("Can get transcript " + transcriptUrl,
                 200, connection.getResponseCode());
    
    // shouldn't be able to access media
    request = r.get(mediaUrl.toString());
    connection = request.get();
    assertEquals("Can get media " + mediaUrl,
                 403, connection.getResponseCode());
    
    // shouldn't be able to access an invalid file
    request = r.get(invalidUrl.toString());
    connection = request.get();
    assertEquals("Can't get nonexistent transcript " + invalidUrl,
                 // 403 bacause name is similar to transcript for which access is retricted
                 403, connection.getResponseCode());
    
    // should be able to access an unrelated file
    request = r.get(otherUrl.toString());
    connection = request.get();
    assertEquals("Can get existing non transcript file " + otherUrl,
                 200, connection.getResponseCode());
    
    // shouldn't be able to access a backup file
    if (backupFileUrl != null) {
      request = l.get(backupFileUrl.toString());
      connection = request.get();
      assertEquals("Can't get existing backup file " + backupFileUrl,
                   403, connection.getResponseCode());
    }
  }
      
  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.content.TestFiles");
  }
}
