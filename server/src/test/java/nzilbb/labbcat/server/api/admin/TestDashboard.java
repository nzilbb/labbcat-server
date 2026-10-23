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
import nzilbb.labbcat.ResponseException;
import nzilbb.labbcat.model.DashboardItem;
import nzilbb.labbcat.http.HttpRequestGet;

/**
 * These tests assume that there is a working LaBB-CAT instance with the latest version of
 * nzilbb.labbcat.server.jar installed.  
 */
public class TestDashboard {
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
  
  @Test public void dashboardItemCRUD() throws Exception {
    DashboardItem originalDashboardItem = new DashboardItem()
      .setType("sql")
      .setLabel("unit-test")
      .setIcon("")
      .setDefinition("SELECT COUNT(*) FROM transcript")
      .setDashboard(true)
      .setExpress(false)
      .setStatistics(true)
      .setDisplayOrder(99);
    int itemId = -1;
    try {
      DashboardItem newDashboardItem = l.createDashboardItem(originalDashboardItem);
      assertNotNull("DashboardItem returned", newDashboardItem);
      assertEquals("Type correct",
                   originalDashboardItem.getType(), newDashboardItem.getType());
      assertEquals("Label correct",
                   originalDashboardItem.getLabel(), newDashboardItem.getLabel());
      assertEquals("Icon correct",
                   originalDashboardItem.getIcon(), newDashboardItem.getIcon());
      assertEquals("Definition correct",
                   originalDashboardItem.getDefinition(), newDashboardItem.getDefinition());
      assertEquals("Dashboard correct",
                   originalDashboardItem.getDashboard(), newDashboardItem.getDashboard());
      assertEquals("Express correct",
                   originalDashboardItem.getExpress(), newDashboardItem.getExpress());
      assertEquals("Statistics correct",
                   originalDashboardItem.getStatistics(), newDashboardItem.getStatistics());
      assertEquals("Display order correct",
                   originalDashboardItem.getDisplayOrder(), newDashboardItem.getDisplayOrder());
      itemId = newDashboardItem.getItemId();
         
      DashboardItem[] dashboardItems = l.readDashboardItems("sql");
      // ensure the dashboardItem exists
      assertTrue("There's at least one dashboardItem", dashboardItems.length >= 1);
      boolean found = false;
      for (DashboardItem c : dashboardItems) {
        if (c.getItemId() == itemId) {
          found = true;
          break;
        }
      }
      assertTrue("DashboardItem was added", found);

      // update it
      DashboardItem updatedDashboardItem = new DashboardItem()
        .setItemId(itemId)
        .setType("sql")
        .setLabel("unit-test-changed")
        .setIcon("search.svg")
        .setDefinition("SELECT COUNT(*) FROM participant")
        .setDashboard(false)
        .setExpress(true)
        .setStatistics(false)
        .setDisplayOrder(98);
         
      DashboardItem changedDashboardItem = l.updateDashboardItem(updatedDashboardItem);
      assertNotNull("DashboardItem returned", changedDashboardItem);
      assertEquals("Type correct",
                   updatedDashboardItem.getType(), changedDashboardItem.getType());
      assertEquals("Label correct",
                   updatedDashboardItem.getLabel(), changedDashboardItem.getLabel());
      assertEquals("Icon correct",
                   updatedDashboardItem.getIcon(), changedDashboardItem.getIcon());
      assertEquals("Definition correct",
                   updatedDashboardItem.getDefinition(), changedDashboardItem.getDefinition());
      assertEquals("Dashboard correct",
                   updatedDashboardItem.getDashboard(), changedDashboardItem.getDashboard());
      assertEquals("Express correct",
                   originalDashboardItem.getExpress(), newDashboardItem.getExpress());
      assertEquals("Statistics correct",
                   originalDashboardItem.getStatistics(), newDashboardItem.getStatistics());
      assertEquals("Display order correct",
                   updatedDashboardItem.getDisplayOrder(), changedDashboardItem.getDisplayOrder());

      // delete it
      l.deleteDashboardItem(changedDashboardItem.getType(), itemId);

      DashboardItem[] dashboardItemsAfter = l.readDashboardItems("sql");
      // ensure the dashboardItem no longer exists
      boolean foundAfter = false;
      for (DashboardItem c : dashboardItemsAfter) {
        if (c.getItemId() == itemId) {
          foundAfter = true;
          break;
        }
      }
      assertFalse("DashboardItem is gone", foundAfter);

      try {
        // can't delete it again
        l.deleteDashboardItem(changedDashboardItem.getType(), itemId);
        fail("Can't delete dashboardItem that doesn't exist");
      } catch(Exception exception) {
      }
      
    } finally {
      l.setVerbose(false);
      // ensure it's not there
      try {
        l.deleteDashboardItem("sql", itemId);
      } catch(Exception exception) {}         
    }
  }
   
  @Test public void readonlyAccessEnforced() throws IOException, StoreException, PermissionException {
    // create a mediaTrack to work with
    DashboardItem testDashboardItem = new DashboardItem()
      .setType("link")
      .setLabel("unit-test")
      .setIcon("")
      .setDefinition("transcripts")
      .setDashboard(true)
      .setExpress(false)
      .setStatistics(true)
      .setDisplayOrder(99);
    
    try {
      l.createDashboardItem(testDashboardItem);
      
      // now try operations with read-only ID, all should fail because of lack of authorization
      
      try {
        ro.createDashboardItem(testDashboardItem);
        fail("Can't create a mediaTrack as non-admin user");
      } catch(ResponseException x) {
        // check it's for the right reason
        assertEquals("Create failed for lack of auth: "
                     + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                     403, x.getResponse().getHttpStatus());
      }
      
      try {
        ro.readDashboardItems("link");
        fail("Can't read dashboard as non-admin user");
      } catch(ResponseException x) {
        // check it's for the right reason
        assertEquals("Read failed for lack of auth: "
                     + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                     403, x.getResponse().getHttpStatus());
      }
      
      try {
        ro.updateDashboardItem(testDashboardItem);
        fail("Can't update a dashboard item as non-admin user");
      } catch(ResponseException x) {
        // check it's for the right reason
        assertEquals("Update failed for lack of auth: "
                     + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                     403, x.getResponse().getHttpStatus());
      }
      
      try {
        ro.deleteDashboardItem(testDashboardItem);
        fail("Can't delete dashboard item as non-admin user");
      } catch(ResponseException x) {
        // check it's for the right reason
        assertEquals("Create failed for lack of auth: "
                     + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                     403, x.getResponse().getHttpStatus());
      }
      
    } finally {         
      try { // ensure test mediaTrack is deleted
        l.deleteDashboardItem(testDashboardItem);
      } catch(Exception exception) {}         
    }
  }
  
  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.admin.TestDashboard");
  }
}
