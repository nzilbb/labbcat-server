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
import java.util.Optional;
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
public class TestPassword
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
         l.setPassword(null, new java.util.Date().toString(), true);
         fail("Can't update user with null ID");
      }
      catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Create failed for lack of name: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      400, x.getResponse().getHttpStatus());
      }
      
      try {
         l.setPassword("doesn't exist", new java.util.Date().toString(), true);
         fail("Can't update a nonexistent user");
      }
      catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Create failed for blank ID: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      404, x.getResponse().getHttpStatus());
      }

   }
   
   @Test public void setPassword() throws Exception {

      User originalUser = new User()
         .setUser("unit-test")
         .setEmail("unit-test@tld.org")
         .setResetPassword(true)
         .setRoles(new String[0]);
      
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

         // change password and reset password
         l.setPassword(originalUser.getUser(), new java.util.Date().toString(), true);
         
         Optional<User> updatedUser = Arrays.stream(l.readUsers())
            .filter(u->u.getUser().equals(originalUser.getUser()))
            .findFirst();
         assertTrue("Found user", updatedUser.isPresent());
         assertTrue("resetPassword is correct", updatedUser.get().getResetPassword());

         // change password and don't reset password
         l.setPassword(originalUser.getUser(), new java.util.Date().toString(), false);
         
         updatedUser = Arrays.stream(l.readUsers())
            .filter(u->u.getUser().equals(originalUser.getUser()))
            .findFirst();
         assertTrue("Found user", updatedUser.isPresent());
         assertFalse("resetPassword is correct", updatedUser.get().getResetPassword());

         try {
            // can't set blank password
            l.setPassword(originalUser.getUser(), "", true);
            fail("can't set blank password");
         } catch(Exception exception) {
         }

         try {
            // can't set null password
            l.setPassword(originalUser.getUser(), null, true);
            fail("can't set null password");
         } catch(Exception exception) {
         }

         // delete it
         l.deleteUser(originalUser.getUser());
      } finally {
         // ensure it's not there
         try {
            l.deleteUser(originalUser);
         } catch(Exception exception) {}
      }
   }
   
   @Test public void readonlyAccessEnforced() throws IOException, StoreException, PermissionException {
      try {
         ro.setPassword("test", "test", true);
         fail("Can't set password as non-admin user");
      } catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Create failed for lack of auth: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      403, x.getResponse().getHttpStatus());
      }      
   }
   
   public static void main(String args[]) {
      org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.admin.TestPassword");
   }
}
