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

package nzilbb.labbcat.server.api.admin.roles;
	      
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
import nzilbb.labbcat.model.RolePermission;
import nzilbb.labbcat.http.HttpRequestGet;

/**
 * These tests assume that there is a working LaBB-CAT instance with the latest version of
 * nzilbb.labbcat.server.jar installed.  
 */
public class TestPermissions
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
         l.createRolePermission(new RolePermission());
         fail("Can't create a rolePermission with null name");
      } catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Create failed for lack of attributes: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      400, x.getResponse().getHttpStatus());
      }
      
      try {
         l.createRolePermission(new RolePermission()
                                .setRoleId("")
                                .setEntity("t")
                                .setLayerId("corpus")
                                .setValuePattern(".*"));
         fail("Can't create a rolePermission with blank role");
      } catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Create failed for blank role: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      400, x.getResponse().getHttpStatus());
      }

      try {
         l.createRolePermission(new RolePermission()
                                .setRoleId("nonexistent role")
                                .setEntity("t")
                                .setLayerId("corpus")
                                .setValuePattern(".*"));
         fail("Can't create a rolePermission with invalid role");
      } catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Create failed for invalid role: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      400, x.getResponse().getHttpStatus());
      }

      try {
         l.createRolePermission(new RolePermission()
                                .setRoleId("admin")
                                .setLayerId("corpus")
                                .setValuePattern(".*"));
         fail("Can't create a rolePermission with blank entity");
      } catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Create failed for blank entity: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      400, x.getResponse().getHttpStatus());
      }
      try {
         l.createRolePermission(new RolePermission()
                                .setRoleId("admin")
                                .setEntity("invalid entity")
                                .setLayerId("corpus")
                                .setValuePattern(".*"));
         fail("Can't create a rolePermission with invalid entity");
      } catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Create failed for invalid entity: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      400, x.getResponse().getHttpStatus());
      }

      try {
         l.createRolePermission(new RolePermission()
                                .setRoleId("admin")
                                .setEntity("t")
                                .setValuePattern(".*"));
         fail("Can't create a rolePermission with no layer");
      } catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Create failed for no layer: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      400, x.getResponse().getHttpStatus());
      }
      
      try {
         l.createRolePermission(new RolePermission()
                                .setRoleId("admin")
                                .setEntity("t")
                                .setLayerId("graph")
                                .setValuePattern(".*"));
         fail("Can't create a rolePermission with invalid layer");
      } catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Create failed for invalid layer: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      400, x.getResponse().getHttpStatus());
      }

      try {
         l.createRolePermission(new RolePermission()
                                .setRoleId("admin")
                                .setEntity("t")
                                .setLayerId("corpus"));
         fail("Can't create a rolePermission with no pattern");
      } catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Create failed for no pattern: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      400, x.getResponse().getHttpStatus());
      }
      
      try {
         l.createRolePermission(new RolePermission()
                                .setRoleId("admin")
                                .setEntity("t")
                                .setLayerId("corpus")
                                .setValuePattern(""));
         fail("Can't create a rolePermission with blank pattern");
      } catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Create failed for blank pattern: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      400, x.getResponse().getHttpStatus());
      }
      
      try {
         l.createRolePermission(new RolePermission()
                                .setRoleId("admin")
                                .setEntity("t")
                                .setLayerId("corpus")
                                .setValuePattern("*"));
         fail("Can't create a rolePermission with invalid pattern");
      } catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Create failed for invalid pattern: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      400, x.getResponse().getHttpStatus());
      }
   }
   
   @Test public void newPermissionUpdatePermissionAndDeletePermission()
      throws Exception {
      RolePermission originalPermission = new RolePermission()
         .setRoleId("admin")
         .setEntity("t")
         .setLayerId("corpus")
         .setValuePattern("unit-test.*");
      
      // ensure the record doesn't exist to start with
      try {
         l.deleteRolePermission(originalPermission);
      } catch(Exception exception) {}
      
      try
      {
         RolePermission newPermission = l.createRolePermission(originalPermission);
         assertNotNull("Permission returned", newPermission);
         assertEquals("roleId correct",
                      originalPermission.getRoleId(), newPermission.getRoleId());
         assertEquals("entity correct",
                      originalPermission.getEntity(), newPermission.getEntity());
         assertEquals("layerId correct",
                      originalPermission.getLayerId(), newPermission.getLayerId());
         assertEquals("valudPattern correct",
                      originalPermission.getValuePattern(), newPermission.getValuePattern());
         
         try {
            l.createRolePermission(originalPermission);
            fail("Can't create a rolePermission with existing name");
         }
         catch(Exception exception) {}
         
         RolePermission[] rolePermissions
            = l.readRolePermissions(originalPermission.getRoleId());
         // ensure the rolePermission exists
         assertTrue("There's at least one rolePermission", rolePermissions.length >= 1);
         boolean found = false;
         for (RolePermission c : rolePermissions) {
            assertEquals("Only correct role listed",
                         originalPermission.getRoleId(), c.getRoleId());
            if (c.getRoleId().equals(originalPermission.getRoleId())
                && c.getEntity().equals(originalPermission.getEntity())) {
               found = true;
            }
         }
         assertTrue("Permission was added", found);

         // update it
         RolePermission updatedPermission = new RolePermission()
            .setRoleId("admin")
            .setEntity("t")
            .setLayerId("transcript_language")
            .setValuePattern("en.*");
         
         RolePermission changedPermission = l.updateRolePermission(updatedPermission);
         assertNotNull("Permission returned", changedPermission);
         assertEquals("roleId unchanged",
                      originalPermission.getRoleId(), changedPermission.getRoleId());
         assertEquals("entity unchanged",
                      originalPermission.getEntity(), changedPermission.getEntity());
         assertEquals("layerId updated",
                      updatedPermission.getLayerId(), changedPermission.getLayerId());
         assertEquals("valudPattern updated",
                      updatedPermission.getValuePattern(), changedPermission.getValuePattern());
         // delete it
         l.deleteRolePermission(
            originalPermission.getRoleId(), originalPermission.getEntity());
         
         RolePermission[] rolePermissionsAfter = l.readRolePermissions(
            originalPermission.getRoleId());
         // ensure the rolePermission no longer exists
         boolean foundAfter = false;
         for (RolePermission c : rolePermissionsAfter) {
            if (c.getRoleId().equals(originalPermission.getRoleId())
                && c.getEntity().equals(originalPermission.getEntity())) {
               foundAfter = true;
               break;
            }
         }
         assertFalse("Permission is gone", foundAfter);

         try {
            // can't delete it again
            l.deleteRolePermission(originalPermission);
            fail("Can't delete rolePermission that doesn't exist");
         } catch(Exception exception) {
         }
         
      } finally {
         // ensure it's not there
         try {
            l.deleteRolePermission(originalPermission);
         } catch(Exception exception) {}         
      }
   }
   
   @Test public void readonlyAccessEnforced() throws IOException, StoreException, PermissionException {
      // create a rolePermission to work with
      RolePermission testPermission = new RolePermission()
         .setRoleId("admin")
         .setEntity("t")
         .setLayerId("corpus")
         .setValuePattern("unit-test.*");
      
      try {
         l.createRolePermission(testPermission);

         // now try operations with read-only ID, all should fail because of lack of authorization

         try {
            ro.createRolePermission(testPermission);
            fail("Can't create a rolePermission as non-admin user");
         } catch(ResponseException x) {
            // check it's for the right reason
            assertEquals("Create failed for lack of auth: "
                         + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                         403, x.getResponse().getHttpStatus());
         }
         
         try {
            ro.readRolePermissions(testPermission.getRoleId());
            fail("Can't read rolePermissions as non-admin user");
         } catch(ResponseException x) {
            // check it's for the right reason
            assertEquals("Read failed for lack of auth: "
                         + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                         403, x.getResponse().getHttpStatus());
         }
         
         try {
            ro.updateRolePermission(testPermission);
            fail("Can't update a rolePermission as non-admin user");
         } catch(ResponseException x) {
            // check it's for the right reason
            assertEquals("Update failed for lack of auth: "
                         + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                         403, x.getResponse().getHttpStatus());
         }
         
         try {
            ro.deleteRolePermission(testPermission);
            fail("Can't delete rolePermission as non-admin user");
         } catch(ResponseException x) {
            // check it's for the right reason
            assertEquals("Create failed for lack of auth: "
                         + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                         403, x.getResponse().getHttpStatus());
         }
      
      } finally {         
         try { // ensure test rolePermission is deleted
            l.deleteRolePermission(testPermission);
         } catch(Exception exception) {}         
      }
   }

   public static void main(String args[]) {
      org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.admin.roles.TestPermissions");
   }
}
