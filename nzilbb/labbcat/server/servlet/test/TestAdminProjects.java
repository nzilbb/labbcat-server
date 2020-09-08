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

package nzilbb.labbcat.server.servlet.test;
	      
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
import nzilbb.labbcat.model.Project;
import nzilbb.labbcat.http.HttpRequestGet;

/**
 * These tests assume that there is a working LaBB-CAT instance with the latest version of
 * nzilbb.labbcat.server.jar installed.  
 */
public class TestAdminProjects
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
         l.createProject(new Project());
         fail("Can't create a project with null name");
      }
      catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Create failed for lack of name: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      400, x.getResponse().getHttpStatus());
      }
      
      try {
         l.createProject(new Project().setProject(""));
         fail("Can't create a project with blank name");
      }
      catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Create failed for blank name: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      400, x.getResponse().getHttpStatus());
      }

      try {
         l.createProject(new Project().setProject("\t "));
         fail("Can't create a project with all-whitespace name");
      }
      catch(ResponseException x) {
         // check it's for the right reason
         assertEquals("Create failed for all-whitespace name: "
                      + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                      400, x.getResponse().getHttpStatus());
      }
 }
   
   @Test public void newProjectUpdateProjectAndDeleteProject() throws Exception {
      Project originalProject = new Project()
         .setProject("unit-test")
         .setDescription("Temporary project for unit testing");
      
      try {
         Project newProject = l.createProject(originalProject);
         assertNotNull("Project returned", newProject);
         assertEquals("Name correct",
                      originalProject.getProject(), newProject.getProject());
         assertEquals("Description correct",
                      originalProject.getDescription(), newProject.getDescription());

         try {
            l.createProject(originalProject);
            fail("Can't create a project with existing name");
         }
         catch(ResponseException exception) {
            // check it's for the right reason
         }

         Project[] projects = l.readProjects();
         // ensure the project exists
         assertTrue("There's at least one project", projects.length >= 1);
         boolean found = false;
         for (Project c : projects) {
            if (c.getProject().equals(originalProject.getProject())) {
               found = true;
               break;
            }
         }
         assertTrue("Project was added", found);

         // update it
         Project updatedProject = new Project()
            .setProject("unit-test")
            .setDescription("Temporary Spanish project for unit testing");

         Project changedProject = l.updateProject(updatedProject);
         assertNotNull("Project returned", changedProject);
         assertEquals("Updated Name correct",
                      updatedProject.getProject(), changedProject.getProject());
         assertEquals("Updated Description correct",
                      updatedProject.getDescription(), changedProject.getDescription());

         // delete it
         l.deleteProject(originalProject.getProject());

         Project[] projectsAfter = l.readProjects();
         // ensure the project no longer exists
         boolean foundAfter = false;
         for (Project c : projectsAfter) {
            if (c.getProject().equals(originalProject.getProject())) {
               foundAfter = true;
               break;
            }
         }
         assertFalse("Project is gone", foundAfter);

         try {
            // can't delete it again
            l.deleteProject(originalProject);
            fail("Can't delete project that doesn't exist");
         } catch(Exception exception) {
         }

      } finally {
         // ensure it's not there
         try {
            l.deleteProject(originalProject);
         } catch(Exception exception) {}         
     }
   }
   
   @Test public void readonlyAccessEnforced() throws IOException, StoreException, PermissionException {
      // create a project to work with
      Project testProject = new Project()
         .setProject("unit-test")
         .setDescription("Temporary project for unit testing");
      
      try {
         l.createProject(testProject);

         // now try operations with read-only ID, all should fail because of lack of authorization

         try {
            ro.createProject(testProject);
            fail("Can't create a project as non-admin user");
         } catch(ResponseException x) {
            // check it's for the right reason
            assertEquals("Create failed for lack of auth: "
                         + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                         403, x.getResponse().getHttpStatus());
         }

         try {
            ro.readProjects();
            fail("Can't read projects as non-admin user");
         } catch(ResponseException x) {
            // check it's for the right reason
            assertEquals("Read failed for lack of auth: "
                         + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                         403, x.getResponse().getHttpStatus());
         }
         
         try {
            ro.updateProject(testProject);
            fail("Can't update a project as non-admin user");
         } catch(ResponseException x) {
            // check it's for the right reason
            assertEquals("Update failed for lack of auth: "
                         + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                         403, x.getResponse().getHttpStatus());
         }
         
         try {
            ro.deleteProject(testProject.getProject());
            fail("Can't delete project as non-admin user");
         } catch(ResponseException x) {
            // check it's for the right reason
            assertEquals("Create failed for lack of auth: "
                         + x.getResponse().getHttpStatus() + " " + x.getResponse().getRaw(),
                         403, x.getResponse().getHttpStatus());
         }
      
      } finally {         
         try { // ensure test project is deleted
            l.deleteProject(testProject);
         } catch(Exception exception) {}         
      }
   }

   public static void main(String args[]) {
      org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.servlet.TestAdminProjects");
   }
}
