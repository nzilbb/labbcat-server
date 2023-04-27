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
import nzilbb.labbcat.model.Role;
import nzilbb.labbcat.http.HttpRequestGet;

/**
 * These tests assume that there is a working LaBB-CAT instance with the latest version of
 * nzilbb.labbcat.server.jar installed.  
 */
public class TestAdminRoles
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
         l.createRole(new Role());
         fail("Can't create a role with null name");
      }
      catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Create failed for lack of name: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      400, x.getResponse().getHttpStatus());
      }
      
      try {
         l.createRole(new Role().setRoleId(""));
         fail("Can't create a role with blank name");
      }
      catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Create failed for blank name: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      400, x.getResponse().getHttpStatus());
      }

      try {
         l.createRole(new Role().setRoleId("\t "));
         fail("Can't create a role with all-whitespace name");
      }
      catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Create failed for all-whitespace name: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      400, x.getResponse().getHttpStatus());
      }
   }
   
   @Test public void newRoleUpdateRoleAndDeleteRole() throws Exception {
      Role originalRole = new Role()
         .setRoleId("unit-test")
         .setDescription("Temporary role for unit testing");
      
      try {
         Role newRole = l.createRole(originalRole);
         assertNotNull("Role returned", newRole);
         assertEquals("Name correct",
                      originalRole.getRoleId(), newRole.getRoleId());
         assertEquals("Description correct",
                      originalRole.getDescription(), newRole.getDescription());
         
         try {
            l.createRole(originalRole);
            fail("Can't create a role with existing name");
         }
         catch(Exception exception) {}
         
         Role[] roles = l.readRoles();
         // ensure the role exists
         assertTrue("There's at least one role", roles.length >= 1);
         boolean found = false;
         for (Role c : roles) {
            if (c.getRoleId().equals(originalRole.getRoleId())) {
               found = true;
               break;
            }
         }
         assertTrue("Role was added", found);

         // update it
         Role updatedRole = new Role()
            .setRoleId("unit-test")
            .setDescription("Changed description");
         
         Role changedRole = l.updateRole(updatedRole);
         assertNotNull("Role returned", changedRole);
         assertEquals("Updated Name correct",
                      updatedRole.getRoleId(), changedRole.getRoleId());
         assertEquals("Updated Description correct",
                      updatedRole.getDescription(), changedRole.getDescription());

         // delete it
         l.deleteRole(originalRole.getRoleId());

         Role[] rolesAfter = l.readRoles();
         // ensure the role no longer exists
         boolean foundAfter = false;
         for (Role c : rolesAfter) {
            if (c.getRoleId().equals(originalRole.getRoleId())) {
               foundAfter = true;
               break;
            }
         }
         assertFalse("Role is gone", foundAfter);

         try {
            // can't delete it again
            l.deleteRole(originalRole);
            fail("Can't delete role that doesn't exist");
         } catch(Exception exception) {
         }
         
      } finally {
         // ensure it's not there
         try {
            l.deleteRole(originalRole);
         } catch(Exception exception) {}         
     }
   }
   
   @Test public void readonlyAccessEnforced() throws IOException, StoreException, PermissionException {
      // create a role to work with
      Role testRole = new Role()
         .setRoleId("unit-test")
         .setDescription("Temporary role for unit testing");
      
      try {
         l.createRole(testRole);

         // now try operations with read-only ID, all should fail because of lack of authorization

         try {
            ro.createRole(testRole);
            fail("Can't create a role as non-admin user");
         } catch(ResponseException x) {
            // check it's for the right reason
            assertEquals("Create failed for lack of auth: "
                         + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                         403, x.getResponse().getHttpStatus());
         }

         try {
            ro.readRoles();
            fail("Can't read roles as non-admin user");
         } catch(ResponseException x) {
            // check it's for the right reason
            assertEquals("Read failed for lack of auth: "
                         + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                         403, x.getResponse().getHttpStatus());
         }
         
         try {
            ro.updateRole(testRole);
            fail("Can't update a role as non-admin user");
         } catch(ResponseException x) {
            // check it's for the right reason
            assertEquals("Update failed for lack of auth: "
                         + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                         403, x.getResponse().getHttpStatus());
         }
         
         try {
            ro.deleteRole(testRole.getRoleId());
            fail("Can't delete role as non-admin user");
         } catch(ResponseException x) {
            // check it's for the right reason
            assertEquals("Create failed for lack of auth: "
                         + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                         403, x.getResponse().getHttpStatus());
         }
      
      } finally {         
         try { // ensure test role is deleted
            l.deleteRole(testRole);
         } catch(Exception exception) {}         
      }
   }

   public static void main(String args[]) {
      org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.servlet.test.TestAdminRoles");
   }
}
