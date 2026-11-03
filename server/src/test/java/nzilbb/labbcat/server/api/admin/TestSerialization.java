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
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.json.JsonObject;
import nzilbb.ag.Anchor;
import nzilbb.ag.Annotation;
import nzilbb.ag.Constants;
import nzilbb.ag.Graph;
import nzilbb.ag.Layer;
import nzilbb.ag.MediaFile;
import nzilbb.ag.MediaTrackDefinition;
import nzilbb.ag.PermissionException;
import nzilbb.ag.StoreException;
import nzilbb.ag.serialize.SerializationDescriptor;
import nzilbb.labbcat.LabbcatAdmin;
import nzilbb.labbcat.Response;
import nzilbb.labbcat.ResponseException;
import nzilbb.labbcat.http.HttpRequestPost;
import nzilbb.labbcat.http.HttpRequestPostMultipart;
import nzilbb.labbcat.model.*;
import nzilbb.labbcat.model.Match;

/**
 * These tests assume that there is a working LaBB-CAT instance with the latest version of
 * nzilbb.labbcat.server.jar installed.
 */
public class TestSerialization {
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
   * Test /api/admin/serialization endpoint rejects non-formatter files.
   */
  @Test public void invalidFileUpload()
    throws Exception {
    
    // upload no file
    HttpRequestPostMultipart request = l.postMultipart("api/admin/serialization");
    try {
      Response response = new Response(request.post(), false);
      response.checkForErrors();
      fail("Should fail when uploading no file.");
    } catch (ResponseException x) {
      System.out.println(x.toString());
      assertEquals("Correct status",
                   400, x.getResponse().getHttpStatus());
    }
    File file = new File(new File(getDir().getParentFile(), "edit"),
                         "nzilbb.labbcat.server.test.txt");
    assertTrue("Test file exists " + file.getPath(), file.exists());
    
    // upload a file
    request = l.postMultipart("api/admin/serialization")
      .setParameter("jar", file);
    try {
      Response response = new Response(request.post(), false);
      response.checkForErrors();
      fail("Should fail to upload a file that's not a serializer.");
    } catch (ResponseException x) {
      System.out.println(x.toString());
      assertEquals("Correct status",
                   400, x.getResponse().getHttpStatus());
    }
  }  
  
  /**
   * Test /api/admin/serialization endpoint rejects invalid parameters.
   */
  @Test public void invalidParameters()
    throws Exception {
    
    // upload no file
    HttpRequestPost request = l.post("api/admin/serialization")
      .setParameter("jar", "invalid");
    try {
      Response response = new Response(request.post(), false);
      response.checkForErrors();
      fail("Should fail when no action is specified.");
    } catch (ResponseException x) {
      System.out.println(x.toString());
      assertEquals("Correct status",
                   400, x.getResponse().getHttpStatus());
      assertTrue("Error is correct: "+x.getMessage(),
                 x.getMessage().endsWith("action"));
    }
    request = l.post("api/admin/serialization")
      .setParameter("action", "install");
    try {
      Response response = new Response(request.post(), false);
      response.checkForErrors();
      fail("Should fail when no action is specified.");
    } catch (ResponseException x) {
      System.out.println(x.toString());
      assertEquals("Correct status",
                   400, x.getResponse().getHttpStatus());
      assertTrue("Error is correct: "+x.getMessage(),
                 x.getMessage().endsWith("jar"));
    }
    request = l.post("api/admin/serialization")
      .setParameter("action", "install")
      .setParameter("jar", "invalid");
    try {
      Response response = new Response(request.post(), false);
      response.checkForErrors();
      fail("Should fail when no action is specified.");
    } catch (ResponseException x) {
      System.out.println(x.toString());
      assertEquals("Correct status",
                   404, x.getResponse().getHttpStatus());
      assertTrue("Error is correct: "+x.getMessage(),
                 x.getMessage().endsWith("invalid"));
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
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.admin.TestSerialization");
  }
}
