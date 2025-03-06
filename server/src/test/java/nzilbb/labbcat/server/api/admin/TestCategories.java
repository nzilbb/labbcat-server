//
// Copyright 2024-2025 New Zealand Institute of Language, Brain and Behaviour, 
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
import nzilbb.ag.MediaFile;
import nzilbb.ag.MediaTrackDefinition;
import nzilbb.ag.PermissionException;
import nzilbb.ag.StoreException;
import nzilbb.labbcat.LabbcatAdmin;
import nzilbb.labbcat.ResponseException;
import nzilbb.labbcat.model.Category;
import nzilbb.labbcat.http.HttpRequestGet;

/**
 * These tests assume that there is a working LaBB-CAT instance with the latest version of
 * nzilbb.labbcat.server.jar installed.  
 */
public class TestCategories {
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
      l.createCategory(new Category().setCategory("unit-test"));
      fail("Can't create a category with no classId");
    }
    catch(ResponseException x) {
      // check it's for the right reason
      assertEquals("Create failed for lack of name: "
                   + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                   400, x.getResponse().getHttpStatus());
    }
    
    try {
      l.createCategory(new Category().setClassId("invalid").setCategory("unit-test"));
      fail("Can't create a category with invalid classId");
    }
    catch(ResponseException x) {
      // check it's for the right reason
      assertEquals("Create failed for lack of name: "
                   + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                   400, x.getResponse().getHttpStatus());
    }
    
    try {
      l.createCategory(new Category().setClassId("layer"));
      fail("Can't create a category with null name");
    }
    catch(ResponseException x) {
      // check it's for the right reason
      assertEquals("Create failed for lack of name: "
                   + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                   400, x.getResponse().getHttpStatus());
    }
      
    try {
      l.createCategory(new Category().setClassId("layer").setCategory(""));
      fail("Can't create a category with blank name");
    }
    catch(ResponseException x) {
      // check it's for the right reason
      assertEquals("Create failed for blank name: "
                   + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                   400, x.getResponse().getHttpStatus());
    }

    try {
      l.createCategory(new Category().setClassId("layer").setCategory("\t "));
      fail("Can't create a category with all-whitespace name");
    }
    catch(ResponseException x) {
      // check it's for the right reason
      assertEquals("Create failed for all-whitespace name: "
                   + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                   400, x.getResponse().getHttpStatus());
    }
  }
   
  @Test public void newCategoryUpdateCategoryAndDeleteCategory() throws Exception {
    Category originalCategory = new Category()
      .setClassId("layer")
      .setCategory("unit-test")
      .setDescription("Temporary category for unit testing");
      
    try {
      Category newCategory = l.createCategory(originalCategory);
      assertNotNull("Category returned", newCategory);
      assertEquals("Name correct",
                   originalCategory.getCategory(), newCategory.getCategory());
      assertEquals("Description correct",
                   originalCategory.getDescription(), newCategory.getDescription());

      try {
        l.createCategory(originalCategory);
        fail("Can't create a category with existing name");
      }
      catch(ResponseException exception) {
        // check it's for the right reason
      }

      Category[] categories = l.readCategories("layer");
      // ensure the category exists
      assertTrue("There's at least one category", categories.length >= 1);
      boolean found = false;
      for (Category c : categories) {
        assertEquals("All categories have correct class - " + c.getCategory(),
                     "layer", c.getClassId());
        if (c.getCategory().equals(originalCategory.getCategory())) {
          found = true;
          break;
        }
      }
      assertTrue("Category was added", found);

      // update it
      Category updatedCategory = new Category()
        .setClassId("layer")
        .setCategory("unit-test")
        .setDescription("Edited category for unit testing");

      Category changedCategory = l.updateCategory(updatedCategory);
      assertNotNull("Category returned", changedCategory);
      assertEquals("Updated Description correct",
                   updatedCategory.getDescription(), changedCategory.getDescription());

      // delete it
      l.deleteCategory(originalCategory.getClassId(), originalCategory.getCategory());

      Category[] categoriesAfter = l.readCategories("layer");
      // ensure the category no longer exists
      boolean foundAfter = false;
      for (Category c : categoriesAfter) {
        if (c.getCategory().equals(originalCategory.getCategory())) {
          foundAfter = true;
          break;
        }
      }
      assertFalse("Category is gone", foundAfter);

      try {
        // can't delete it again
        l.deleteCategory(originalCategory.getClassId(), originalCategory.getCategory());
        fail("Can't delete category that doesn't exist");
      } catch(Exception exception) {
      }

    } finally {
      // ensure it's not there
      try {
        l.deleteCategory(originalCategory);
      } catch(Exception exception) {}         
    }
  }
   
  @Test public void readonlyAccessEnforced() throws IOException, StoreException, PermissionException {
    // create a category to work with
    Category testCategory = new Category()
      .setClassId("layer")
      .setCategory("unit-test")
      .setDescription("Temporary category for unit testing");
      
    try {
      // now try operations with read-only ID, all should fail because of lack of authorization

      try {
        ro.createCategory(testCategory);
        fail("Can't create a category as non-admin user");
      } catch(ResponseException x) {
        // check it's for the right reason
        assertEquals("Create failed for lack of auth: "
                     + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                     403, x.getResponse().getHttpStatus());
      }

      // create it with read/write ID
      l.createCategory(testCategory);

      try {
        ro.readCategories("layer");
        fail("Can't read categories as non-admin user");
      } catch(ResponseException x) {
        // check it's for the right reason
        assertEquals("Read failed for lack of auth: "
                     + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                     403, x.getResponse().getHttpStatus());
      }
         
      try {
        ro.updateCategory(testCategory);
        fail("Can't update a category as non-admin user");
      } catch(ResponseException x) {
        // check it's for the right reason
        assertEquals("Update failed for lack of auth: "
                     + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                     403, x.getResponse().getHttpStatus());
      }
         
      try {
        ro.deleteCategory(testCategory.getClassId(), testCategory.getCategory());
        fail("Can't delete category as non-admin user");
      } catch(ResponseException x) {
        // check it's for the right reason
        assertEquals("Create failed for lack of auth: "
                     + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                     403, x.getResponse().getHttpStatus());
      }
      
    } finally {         
      try { // ensure test category is deleted
        l.deleteCategory(testCategory);
      } catch(Exception exception) {}         
    }
  }

  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.admin.TestCategories");
  }
}
