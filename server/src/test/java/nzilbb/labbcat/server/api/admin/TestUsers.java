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
import nzilbb.ag.MediaFile;
import nzilbb.ag.MediaTrackDefinition;
import nzilbb.ag.PermissionException;
import nzilbb.ag.StoreException;
import nzilbb.labbcat.LabbcatAdmin;
import nzilbb.labbcat.ResponseException;
import nzilbb.labbcat.model.Role;
import nzilbb.labbcat.model.User;
import nzilbb.labbcat.http.HttpRequestGet;

/**
 * These tests assume that there is a working LaBB-CAT instance with the latest version of
 * nzilbb.labbcat.server.jar installed.  
 */
public class TestUsers
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
         l.createUser(new User());
         fail("Can't create a user with null ID");
      }
      catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Create failed for lack of name: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      400, x.getResponse().getHttpStatus());
      }
      
      try {
         l.createUser(new User().setUser(""));
         fail("Can't create a user with blank ID");
      }
      catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Create failed for blank ID: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      400, x.getResponse().getHttpStatus());
      }

      try {
         l.createUser(new User().setUser("\t "));
         fail("Can't create a user with all-whitespace ID");
      }
      catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Create failed for all-whitespace ID: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      400, x.getResponse().getHttpStatus());
      }
      try {
         l.createUser(new User().setUser("unit-test").setEmail("invalid-address"));
         fail("Can't create a user with invalid email address");
      }
      catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Create failed for invalid email address: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      400, x.getResponse().getHttpStatus());
      }
   }
   
   @Test public void newUserUpdateUserAndDeleteUser() throws Exception {
      Role testRole = new Role()
         .setRoleId("unit-test")
         .setDescription("Temporary role for unit testing");
      l.createRole(testRole);

      String[] roles = { testRole.getRoleId() };
      User originalUser = new User()
         .setUser("unit-test")
         .setEmail("unit-test@tld.org")
         .setResetPassword(true)
         .setRoles(roles);
      
      try {
         User newUser = l.createUser(originalUser);
         assertNotNull("User returned", newUser);
         assertEquals("ID correct",
                      originalUser.getUser(), newUser.getUser());
         assertEquals("Email correct",
                      originalUser.getEmail(), newUser.getEmail());
         assertEquals("Reset Password correct",
                      originalUser.getResetPassword(), newUser.getResetPassword());
         assertArrayEquals("Roles correct",
                      originalUser.getRoles(), newUser.getRoles());
         
         try {
            l.createUser(originalUser);
            fail("Can't create a user with existing ID");
         }
         catch(Exception exception) {}
         
         User[] users = l.readUsers();
         // ensure the user exists
         assertTrue("There's at least one user", users.length >= 1);
         boolean found = false;
         for (User c : users) {
            if (c.getUser().equals(originalUser.getUser())) {
               found = true;
               break;
            }
         }
         assertTrue("User was added", found);

         // update it
         String[] editedRoles = { "view" };
         User updatedUser = new User()
            .setUser("unit-test")
            .setEmail("new@tld.org")
            .setResetPassword(true)
            .setRoles(roles);
         
         User changedUser = l.updateUser(updatedUser);
         assertNotNull("User returned", changedUser);
         assertEquals("ID correct",
                      updatedUser.getUser(), changedUser.getUser());
         assertEquals("Email correct",
                      updatedUser.getEmail(), changedUser.getEmail());
         assertEquals("Reset Password correct",
                      updatedUser.getResetPassword(), changedUser.getResetPassword());
         assertArrayEquals("Roles correct",
                      updatedUser.getRoles(), changedUser.getRoles());

         // delete it
         l.deleteUser(originalUser.getUser());

         User[] usersAfter = l.readUsers();
         // ensure the user no longer exists
         boolean foundAfter = false;
         for (User c : usersAfter) {
            if (c.getUser().equals(originalUser.getUser())) {
               foundAfter = true;
               break;
            }
         }
         assertFalse("User is gone", foundAfter);

         try {
            // can't delete it again
            l.deleteUser(originalUser);
            fail("Can't delete user that doesn't exist");
         } catch(Exception exception) {
         }

         // can create with ID only
         l.createUser(new User().setUser(originalUser.getUser()));
         // delete it (with ID instead of model)
         l.deleteUser(originalUser.getUser());
      } finally {
         // ensure it's not there
         try {
            l.deleteUser(originalUser);
         } catch(Exception exception) {}
         // remove the test role
         try {
            l.deleteRole(testRole);
         } catch(Exception exception) {}         
     }
   }
   
   @Test public void readonlyAccessEnforced() throws IOException, StoreException, PermissionException {
      // create a user to work with
      User testUser = new User()
         .setUser("unit-test");
      
      try {

         // now try operations with read-only ID, all should fail because of lack of authorization

         try {
            ro.createUser(testUser);
            fail("Can't create a user as non-admin user");
         } catch(ResponseException x) {
            // check it's for the right reason
            assertEquals("Create failed for lack of auth: "
                         + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                         403, x.getResponse().getHttpStatus());
         }

         try {
            ro.readUsers();
            fail("Can't read users as non-admin user");
         } catch(ResponseException x) {
            // check it's for the right reason
            assertEquals("Read failed for lack of auth: "
                         + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                         403, x.getResponse().getHttpStatus());
         }
         
         try {
            ro.updateUser(testUser);
            fail("Can't update a user as non-admin user");
         } catch(ResponseException x) {
            // check it's for the right reason
            assertEquals("Update failed for lack of auth: "
                         + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                         403, x.getResponse().getHttpStatus());
         }
         
         try {
            ro.deleteUser(testUser.getUser());
            fail("Can't delete user as non-admin user");
         } catch(ResponseException x) {
            // check it's for the right reason
            assertEquals("Create failed for lack of auth: "
                         + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                         403, x.getResponse().getHttpStatus());
         }
      
      } finally {         
         try { // ensure test user is deleted
            l.deleteUser(testUser);
         } catch(Exception exception) {}         
      }
   }

   public static void main(String args[]) {
      org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.admin.TestUsers");
   }
}
