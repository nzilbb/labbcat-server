//
// Copyright 2020-2025 New Zealand Institute of Language, Brain and Behaviour, 
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
import nzilbb.ag.Anchor;
import nzilbb.ag.Annotation;
import nzilbb.ag.Layer;
import nzilbb.ag.PermissionException;
import nzilbb.ag.StoreException;
import nzilbb.labbcat.LabbcatAdmin;
import nzilbb.labbcat.Response;
import nzilbb.labbcat.ResponseException;
import nzilbb.labbcat.model.DashboardItem;
import nzilbb.labbcat.http.HttpRequestGet;

/**
 * These tests assume that there is a working LaBB-CAT instance with the latest version of
 * nzilbb.labbcat.server.jar installed.  
 */
public class TestDashboardTest {
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

  /** A normal, valid query works. */
  @Test public void basicQueryTest() throws Exception {
    try {
      
      // regenerate a layer
      HttpRequestGet request = l.get("api/admin/dashboard/test")
        .setParameter("definition", "SELECT COUNT(*) FROM transcript");
      Response response = new Response(request.get(), false);
      response.checkForErrors();
      String result = response.getModel().toString();
      assertNotNull("A result returned", result);
      //System.out.println(result);
      
    } finally {
      l.setVerbose(false);
    }
  }

  /** Invalid queries are rejected */
  @Test public void validation() throws Exception {
    try {
      
      HttpRequestGet request = l.get("api/admin/dashboard/test")
        .setParameter("definition", "Not SQL");
      try {
        Response response = new Response(request.get(), false);
        response.checkForErrors();
        fail("Invalid SQL should fail, but returned" + response.getModel().toString());
      } catch(ResponseException x) {
        // check it's for the right reason
        System.out.println(""+x);
      }

      request = l.get("api/admin/dashboard/test")
        .setParameter("definition", "UPDATE transcript SET air_date = ''");
      try {
        Response response = new Response(request.get(), false);
        response.checkForErrors();
        fail("UPDATE should fail, but returned" + response.getModel().toString());
      } catch(ResponseException x) {
        // check it's for the right reason
        System.out.println(""+x);
      }
      
      request = l.get("api/admin/dashboard/test")
        .setParameter(
          "definition",
          "SELECT COUNT(*) FROM transcript; UPDATE transcript SET air_date = ''");
      try {
        Response response = new Response(request.get(), false);
        response.checkForErrors();
        fail("Multiple statements should fail, but returned" + response.getModel().toString());
      } catch(ResponseException x) {
        // check it's for the right reason
        System.out.println(""+x);
      }
      
      request = l.get("api/admin/dashboard/test")
        .setParameter(
          "definition",
          "SELECT value FROM system_attribute WHERE name = 'SMTPPassword'");
      try {
        Response response = new Response(request.get(), false);
        response.checkForErrors();
        fail("Password query should fail, but returned" + response.getModel().toString());
      } catch(ResponseException x) {
        // check it's for the right reason
        System.out.println(""+x);
      }
      
    } finally {
      l.setVerbose(false);
    }
  }
   
  @Test public void accessEnforced() throws IOException, StoreException, PermissionException {
      HttpRequestGet request = ro.get("api/admin/dashboard/test")
        .setParameter("definition", "Not SQL");
      try {
        Response response = new Response(request.get(), false);
        response.checkForErrors();
        fail("Invalid SQL should fail, but returned" + response.getModel().toString());
      } catch(ResponseException x) {
        // check it's for the right reason
        assertEquals("Create failed for lack of auth: "
                     + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                     403, x.getResponse().getHttpStatus());
      }    
  }
  
  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.admin.TestDashboardTest");
  }
}
