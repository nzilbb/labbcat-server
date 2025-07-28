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

import java.net.MalformedURLException;
import java.util.Map;
import nzilbb.labbcat.LabbcatView;
import nzilbb.labbcat.ResponseException;
import nzilbb.labbcat.model.DashboardItem;

/**
 * Test the <tt>api/dashboard</tt> endpoint.
 * <p> These tests assume that there is a working LaBB-CAT instance with the latest version
 * of nzilbb.labbcat.server.jar installed.  
 */
public class TestDashboard {
  static String labbcatUrl = "http://localhost:8080/labbcat/";
  static String username = "labbcat";
  static String password = "labbcat";
  static LabbcatView l;

  @BeforeClass public static void setBaseUrl() throws MalformedURLException {
    try {
      l = new LabbcatView(labbcatUrl, username, password);
      l.setBatchMode(true);
    } catch(MalformedURLException exception) {
      fail("Could not create Labbcat object");
    }
  }

  /** Test home dashboard items are returned */
  @Test public void dashboard() throws Exception {
    DashboardItem[] items = l.getDashboardItems("home");
    assertTrue("Some items are returned", items.length > 0);
    for (DashboardItem item : items) {
      assertNotNull("ID set - item " + item, item.getItemId());
      assertNotNull("Type set - item " + item, item.getType());
      assertNotNull("Label set - item " + item, item.getLabel());
      assertNotNull("Icon set - item " + item, item.getIcon());
    }

    // null dashboard means home
    items = l.getDashboardItems(null);
    assertTrue("Some items are returned for null dashboard", items.length > 0);
    for (DashboardItem item : items) {
      assertNotNull("null dashboard: ID set - item " + item, item.getItemId());
      assertNotNull("null dashboard: Type set - item " + item, item.getType());
      assertNotNull("null dashboard: Label set - item " + item, item.getLabel());
      assertNotNull("null dashboard: Icon set - item " + item, item.getIcon());
    }
  }

  /** Test invalid dashboard request */
  @Test public void invalidDashboard() throws Exception {
    try {
       l.getDashboardItems("invalid-dashboard");
       fail("Invalid dashboard returns error");
    } catch(ResponseException exception) {
      assertEquals("Not found error", 404, exception.getResponse().getHttpStatus());
    }
  }
  
  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.TestDashboard");
  }
}
