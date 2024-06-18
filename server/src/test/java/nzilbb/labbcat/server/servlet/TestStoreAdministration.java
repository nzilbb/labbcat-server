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
import nzilbb.ag.Constants;
import nzilbb.ag.Layer;
import nzilbb.ag.MediaFile;
import nzilbb.ag.MediaTrackDefinition;
import nzilbb.ag.PermissionException;
import nzilbb.ag.StoreException;
import nzilbb.ag.serialize.SerializationDescriptor;
import nzilbb.labbcat.LabbcatAdmin;
import nzilbb.labbcat.model.*;
import nzilbb.labbcat.ResponseException;
import nzilbb.labbcat.http.HttpRequestGet;
import nzilbb.labbcat.model.Match;

/**
 * These tests assume that there is a working LaBB-CAT instance with the latest version of
 * nzilbb.labbcat.server.jar installed.  
 */
public class TestStoreAdministration
{
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
   
  @Test public void newCorpusUpdateCorpusAndDeleteCorpus() throws Exception {
    Corpus originalCorpus = new Corpus()
      .setName("unit-test")
      .setLanguage("en")
      .setDescription("Temporary corpus for unit testing");
      
    try {
      Corpus newCorpus = l.createCorpus(originalCorpus);
      assertNotNull("Corpus returned", newCorpus);
      assertEquals("Name correct",
                   originalCorpus.getName(), newCorpus.getName());
      assertEquals("Language correct",
                   originalCorpus.getLanguage(), newCorpus.getLanguage());
      assertEquals("Description correct",
                   originalCorpus.getDescription(), newCorpus.getDescription());

      try {
        l.createCorpus(originalCorpus);
        fail("Can't create a corpus with existing name");
      }
      catch(Exception exception) {}

      Corpus[] corpora = l.readCorpora();
      // ensure the corpus exists
      assertTrue("There's at least one corpus", corpora.length >= 1);
      boolean found = false;
      for (Corpus c : corpora) {
        if (c.getName().equals(originalCorpus.getName())) {
          found = true;
          break;
        }
      }
      assertTrue("Corpus was added", found);

      // update it
      Corpus updatedCorpus = new Corpus()
        .setName("unit-test")
        .setLanguage("es")
        .setDescription("Temporary Spanish corpus for unit testing");

      Corpus changedCorpus = l.updateCorpus(updatedCorpus);
      assertNotNull("Corpus returned", changedCorpus);
      assertEquals("Updated Name correct",
                   updatedCorpus.getName(), changedCorpus.getName());
      assertEquals("Updated Language correct",
                   updatedCorpus.getLanguage(), changedCorpus.getLanguage());
      assertEquals("Updated Description correct",
                   updatedCorpus.getDescription(), changedCorpus.getDescription());

      // delete it
      l.deleteCorpus(originalCorpus.getName());

      Corpus[] corporaAfter = l.readCorpora();
      // ensure the corpus no longer exists
      boolean foundAfter = false;
      for (Corpus c : corporaAfter) {
        if (c.getName().equals(originalCorpus.getName())) {
          foundAfter = true;
          break;
        }
      }
      assertFalse("Corpus is gone", foundAfter);

      try {
        // can't delete it again
        l.deleteCorpus(originalCorpus);
        fail("Can't delete corpus that doesn't exist");
      } catch(Exception exception) {
      }

    } finally {
      // ensure it's not there
      try {
        l.deleteCorpus(originalCorpus);
      } catch(Exception exception) {}         
    }
  }

  @Test public void newCategoryUpdateCategoryAndDeleteCategory() throws Exception {
    Category originalCategory = new Category()
      .setClassId("transcript")
      .setCategory("unit-test")
      .setDescription("Temporary category for unit testing")
      .setDisplayOrder(999);
      
    try {
      Category newCategory = l.createCategory(originalCategory);
      assertNotNull("Category returned", newCategory);
      assertEquals("Class correct",
                   originalCategory.getClassId(), newCategory.getClassId());
      assertEquals("Name correct",
                   originalCategory.getCategory(), newCategory.getCategory());
      assertEquals("Description correct",
                   originalCategory.getDescription(), newCategory.getDescription());
      assertEquals("displayOrder correct",
                   originalCategory.getDisplayOrder(), newCategory.getDisplayOrder());
         
      try {
        l.createCategory(originalCategory);
        fail("Can't create a category with existing name");
      }
      catch(Exception exception) {}
         
      Category[] categories = l.readCategories("transcript");
      // ensure the category exists
      assertTrue("There's at least one category", categories.length >= 1);
      boolean found = false;
      for (Category c : categories) {
        if (c.getCategory().equals(originalCategory.getCategory())) {
          found = true;
          break;
        }
      }
      assertTrue("Category was added", found);

      // update it
      Category updatedCategory = new Category()
        .setClassId("transcript")
        .setCategory("unit-test")
        .setDescription("Changed description")
        .setDisplayOrder(888);
      
      Category changedCategory = l.updateCategory(updatedCategory);
      assertNotNull("Category returned", changedCategory);
      assertEquals("Updated Name correct",
                   updatedCategory.getCategory(), changedCategory.getCategory());
      assertEquals("Updated Description correct",
                   updatedCategory.getDescription(), changedCategory.getDescription());
      assertEquals("Updated displayOrder correct",
                   updatedCategory.getDisplayOrder(), changedCategory.getDisplayOrder());

      // delete it
      l.deleteCategory("transcript", originalCategory.getCategory());
      
      Category[] categoriesAfter = l.readCategories("transcript");
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
        l.deleteCategory(originalCategory);
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

  @Test public void newMediaTrackUpdateMediaTrackAndDeleteMediaTrack() throws Exception {
    MediaTrack originalMediaTrack = new MediaTrack()
      .setSuffix("unit-test")
      .setDescription("Temporary mediaTrack for unit testing")
      .setDisplayOrder(99);
      
    try {
      MediaTrack newMediaTrack = l.createMediaTrack(originalMediaTrack);
      assertNotNull("MediaTrack returned", newMediaTrack);
      assertEquals("Name correct",
                   originalMediaTrack.getSuffix(), newMediaTrack.getSuffix());
      assertEquals("Description correct",
                   originalMediaTrack.getDescription(), newMediaTrack.getDescription());
      assertEquals("Display order correct",
                   originalMediaTrack.getDisplayOrder(), newMediaTrack.getDisplayOrder());
         
      try {
        l.createMediaTrack(originalMediaTrack);
        fail("Can't create a mediaTrack with existing name");
      }
      catch(Exception exception) {}
         
      MediaTrack[] mediaTracks = l.readMediaTracks();
      // ensure the mediaTrack exists
      assertTrue("There's at least one mediaTrack", mediaTracks.length >= 1);
      boolean found = false;
      for (MediaTrack c : mediaTracks) {
        if (c.getSuffix().equals(originalMediaTrack.getSuffix())) {
          found = true;
          break;
        }
      }
      assertTrue("MediaTrack was added", found);

      // update it
      MediaTrack updatedMediaTrack = new MediaTrack()
        .setSuffix("unit-test")
        .setDescription("Changed description")
        .setDisplayOrder(100);
         
      MediaTrack changedMediaTrack = l.updateMediaTrack(updatedMediaTrack);
      assertNotNull("MediaTrack returned", changedMediaTrack);
      assertEquals("Updated Name correct",
                   updatedMediaTrack.getSuffix(), changedMediaTrack.getSuffix());
      assertEquals("Updated Description correct",
                   updatedMediaTrack.getDescription(), changedMediaTrack.getDescription());
      assertEquals("Updated Display order correct",
                   updatedMediaTrack.getDisplayOrder(), changedMediaTrack.getDisplayOrder());

      // delete it
      l.deleteMediaTrack(originalMediaTrack.getSuffix());

      MediaTrack[] mediaTracksAfter = l.readMediaTracks();
      // ensure the mediaTrack no longer exists
      boolean foundAfter = false;
      for (MediaTrack c : mediaTracksAfter) {
        if (c.getSuffix().equals(originalMediaTrack.getSuffix())) {
          foundAfter = true;
          break;
        }
      }
      assertFalse("MediaTrack is gone", foundAfter);

      try {
        // can't delete it again
        l.deleteMediaTrack(originalMediaTrack);
        fail("Can't delete mediaTrack that doesn't exist");
      } catch(Exception exception) {
      }

    } finally {
      // ensure it's not there
      try {
        l.deleteMediaTrack(originalMediaTrack);
      } catch(Exception exception) {}         
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

  @Test public void newRolePermissionUpdateRolePermissionAndDeleteRolePermission()
    throws Exception {
    RolePermission originalRolePermission = new RolePermission()
      .setRoleId("admin")
      .setEntity("t")
      .setLayerId("corpus")
      .setValuePattern("unit-test.*");
      
    // ensure the record doesn't exist to start with
    try {
      l.deleteRolePermission(originalRolePermission);
    } catch(Exception exception) {}
      
    try
    {
      RolePermission newRolePermission = l.createRolePermission(originalRolePermission);
      assertNotNull("RolePermission returned", newRolePermission);
      assertEquals("roleId correct",
                   originalRolePermission.getRoleId(), newRolePermission.getRoleId());
      assertEquals("entity correct",
                   originalRolePermission.getEntity(), newRolePermission.getEntity());
      assertEquals("layerId correct",
                   originalRolePermission.getLayerId(), newRolePermission.getLayerId());
      assertEquals("valudPattern correct",
                   originalRolePermission.getValuePattern(), newRolePermission.getValuePattern());
         
      try {
        l.createRolePermission(originalRolePermission);
        fail("Can't create a rolePermission with existing name");
      }
      catch(Exception exception) {}
         
      RolePermission[] rolePermissions
        = l.readRolePermissions(originalRolePermission.getRoleId());
      // ensure the rolePermission exists
      assertTrue("There's at least one rolePermission", rolePermissions.length >= 1);
      boolean found = false;
      for (RolePermission c : rolePermissions) {
        assertEquals("Only correct role listed",
                     originalRolePermission.getRoleId(), c.getRoleId());
        if (c.getRoleId().equals(originalRolePermission.getRoleId())
            && c.getEntity().equals(originalRolePermission.getEntity())) {
          found = true;
        }
      }
      assertTrue("RolePermission was added", found);

      // update it
      RolePermission updatedRolePermission = new RolePermission()
        .setRoleId("admin")
        .setEntity("t")
        .setLayerId("transcript_language")
        .setValuePattern("en.*");
         
      RolePermission changedRolePermission = l.updateRolePermission(updatedRolePermission);
      assertNotNull("RolePermission returned", changedRolePermission);
      assertEquals("roleId unchanged",
                   originalRolePermission.getRoleId(), changedRolePermission.getRoleId());
      assertEquals("entity unchanged",
                   originalRolePermission.getEntity(), changedRolePermission.getEntity());
      assertEquals("layerId updated",
                   updatedRolePermission.getLayerId(), changedRolePermission.getLayerId());
      assertEquals("valudPattern updated",
                   updatedRolePermission.getValuePattern(), changedRolePermission.getValuePattern());
      // delete it
      l.deleteRolePermission(
        originalRolePermission.getRoleId(), originalRolePermission.getEntity());
         
      RolePermission[] rolePermissionsAfter = l.readRolePermissions(
        originalRolePermission.getRoleId());
      // ensure the rolePermission no longer exists
      boolean foundAfter = false;
      for (RolePermission c : rolePermissionsAfter) {
        if (c.getRoleId().equals(originalRolePermission.getRoleId())
            && c.getEntity().equals(originalRolePermission.getEntity())) {
          foundAfter = true;
          break;
        }
      }
      assertFalse("RolePermission is gone", foundAfter);

      try {
        // can't delete it again
        l.deleteRolePermission(originalRolePermission);
        fail("Can't delete rolePermission that doesn't exist");
      } catch(Exception exception) {
      }
         
    } finally {
      // ensure it's not there
      try {
        l.deleteRolePermission(originalRolePermission);
      } catch(Exception exception) {}         
    }
  }
   
  @Test public void readSystemAttributeAndUpdateSystemAttribute() throws Exception {
    SystemAttribute[] systemAttributes = l.readSystemAttributes();
    // ensure the systemAttribute exists
    assertTrue("There's at least one systemAttribute", systemAttributes.length >= 1);
    SystemAttribute originalTitle = null;
    SystemAttribute originalTranscriptSubdir = null;
    for (SystemAttribute a : systemAttributes) {
      if (a.getAttribute().equals("title")) {
        originalTitle = a;
      } else if (a.getAttribute().equals("transcriptSubdir")) {
        originalTranscriptSubdir = a;
      }
    }
    assertNotNull("title attribute returned", originalTitle);
    assertNotNull("title attribute has value", originalTitle.getValue());
    assertNotNull("transcriptSubdir attribute returned", originalTranscriptSubdir);
    assertNotNull("transcriptSubdir attribute has value", originalTranscriptSubdir.getValue());
    assertEquals("transcriptSubdir attribute is read-only",
                 "readonly", originalTranscriptSubdir.getType());

    try {
      // update it
      SystemAttribute updatedTitle = new SystemAttribute()
        .setAttribute("title")
        .setValue("unit-test") // should be updated
        .setType("Changed type") // should not be updated
        .setStyle("Changed style") // should not be updated
        .setLabel("Changed label"); // should not be updated
      
      SystemAttribute changedTitle = l.updateSystemAttribute(updatedTitle);
      assertNotNull("SystemAttribute returned after object update", changedTitle);
      assertEquals("Updated Value correct after object update",
                   updatedTitle.getValue(), changedTitle.getValue());

      SystemAttribute[] systemAttributesAfter = l.readSystemAttributes();
      // ensure only the value has been updated
      SystemAttribute listedTitle = null;
      for (SystemAttribute a : systemAttributesAfter) {
        if (a.getAttribute().equals("title")) {
          listedTitle = a;
          break;
        }
      }
      assertNotNull("SystemAttribute is still there", listedTitle);
      assertEquals("Updated Value correct", updatedTitle.getValue(), listedTitle.getValue());
      assertEquals("type unchanged", originalTitle.getType(), listedTitle.getType());
      assertEquals("style unchanged", originalTitle.getStyle(), listedTitle.getStyle());
      assertEquals("label unchanged", originalTitle.getLabel(), listedTitle.getLabel());
      assertEquals("description unchanged",
                   originalTitle.getDescription(), listedTitle.getDescription());

      changedTitle = l.updateSystemAttribute("title", "updated-value");
      assertNotNull("SystemAttribute returned after string update", changedTitle);
      assertEquals("Updated Value correct after string update",
                   "updated-value", changedTitle.getValue());
         
      try {
        SystemAttribute updatedTranscriptSubdir = new SystemAttribute()
          .setAttribute("transcriptSubdir")
          .setValue("unit-test");
        // can't update read-only attribute
        l.updateSystemAttribute(updatedTranscriptSubdir);
        // if we got here, it was incorrectly updated, so put back the original value
        l.updateSystemAttribute(originalTranscriptSubdir);
        // ... and then fail
        fail("Can't update read-only attribute");
      } catch(Exception exception) {
      }
         
      try {
        SystemAttribute nonexistentAttribute = new SystemAttribute()
          .setAttribute("unit-test")
          .setValue("unit-test");
        // can't update read-only attribute
        l.updateSystemAttribute(nonexistentAttribute);
        fail("Can't update nonexistent attribute");
      } catch(Exception exception) {
      }
         
    } finally {
      // put back original title
      try {
        l.updateSystemAttribute(originalTitle);
      } catch(Exception exception) {
        System.out.println("ERROR restoring title: " + exception);
      }
    }
  }

  @Test public void updateInfo() throws Exception {
    String originalInfo = "<!DOCTYPE html>"
      +"\n<html>"
      +"  <head>"
      +"    <title>LaBB-CAT</title>"
      +"    <base href=\"${base}\">"
      +"    <meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\" />"
      +"    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />"
      +"    <link rel=\"stylesheet\" href=\"../wysiwiki/wysiwiki.css\" type=\"text/css\">"
      +"    <link rel=\"shortcut icon\" href=\"../favicon.ico\" />"
      +"    <script src=\"../jquery.js\"></script>"
      +"    <script src=\"../wysiwiki/wysiwiki.js\" defer></script>"
      +"  </head>"
      +"  <body>"
      +"    <header><div class=\"loading\"></div></header>"
      +"    <div id=\"main\">"
      +"      <aside></aside>"
      +"      <article></article>"
      +"      <nav><div class=\"loading\"></div></nav>"
      +"    </div>"
      +"    <footer><div class=\"loading\"></div></footer>"
      +"  </body>"
      +"</html>";
    try {
      originalInfo = l.getInfo();
    } catch(StoreException exception) {}

    try {
      // update it
      String changedInfo = originalInfo + " <div>unit-test</div>";
      l.updateInfo(changedInfo);
         
      String newInfo = l.getInfo();
      assertEquals("Updated info correct", changedInfo, newInfo);         
         
    } finally {
      l.updateInfo(originalInfo);
    }
  }

  @Test public void saveTranscriptTypeOptions() throws Exception {
    Layer originalTranscriptType = l.getLayer("transcript_type");
    assertNotNull("There's a transcript_type layer",
                  originalTranscriptType);
    assertTrue("There's at least one transcript type",
               originalTranscriptType.getValidLabels().size() > 0);

    try {

      Layer editedTranscriptType1 = (Layer)originalTranscriptType.clone();
         
      // add an option
      String newOption1 = "unit-test-1";
      editedTranscriptType1.getValidLabels().put(newOption1, newOption1);

      Layer editedTranscriptType2 = l.saveLayer(editedTranscriptType1);

      assertTrue("new option 1 is there: " + editedTranscriptType2.getValidLabels(),
                 editedTranscriptType2.getValidLabels().keySet().contains(newOption1));
      assertEquals("All options are what we expect",
                   editedTranscriptType1.getValidLabels(),
                   editedTranscriptType2.getValidLabels());
      // remove an option
      editedTranscriptType2.getValidLabels().remove(newOption1);      
         
      // add an option
      String newOption2 = "unit-test-2";
      editedTranscriptType2.getValidLabels().put(newOption2, newOption2);
      
      Layer finalTranscriptType = l.saveLayer(editedTranscriptType2);

      assertFalse("old option 1 isn't there",
                  finalTranscriptType.getValidLabels().keySet().contains(newOption1));
      assertTrue("new option 2 is there",
                 finalTranscriptType.getValidLabels().keySet().contains(newOption2));
      assertEquals("All options are what we expect",
                   editedTranscriptType2.getValidLabels(),
                   finalTranscriptType.getValidLabels());
         
    } finally {
      // put back original options
      try {
        l.saveLayer(originalTranscriptType);
      } catch(Exception exception) {
        System.out.println("ERROR restoring title: " + exception);
      }
    }
  }
   
  @Test public void newSaveDeleteLayer() throws Exception {
    Layer testLayer = new Layer("unit-test", "Unit test layer→") // including non ASCII char
      .setParentId("word")
      .setAlignment(Constants.ALIGNMENT_NONE)
      .setPeers(true)
      .setPeersOverlap(true)
      .setParentIncludes(true)
      .setSaturated(true)
      .setType(Constants.TYPE_STRING);
     
    // TODO validLabels

    try {
      l.getLayer(testLayer.getId());
      fail("Test layer doesn't already exist: " + testLayer.getId());
    } catch (StoreException x) {
    }
      
    try {
      l.saveLayer(testLayer);
      fail("Test layer can't be saved before it exists: " + testLayer.getId());
    } catch (StoreException x) {
    }
      
    try {

      // create the layer
      Layer newLayer = l.newLayer(testLayer);
      assertNotNull("new layer returned", newLayer);
      assertEquals("created ID",
                   newLayer.getId(), testLayer.getId());
      assertEquals("created Description",
                   newLayer.getDescription(), testLayer.getDescription());
      assertEquals("created parent",
                   newLayer.getParentId(), testLayer.getParentId());
      assertEquals("created alignment",
                   newLayer.getAlignment(), testLayer.getAlignment());
      assertEquals("created peers",
                   newLayer.getPeers(), testLayer.getPeers());
      assertEquals("created peersOverlap",
                   newLayer.getPeersOverlap(), testLayer.getPeersOverlap());
      assertEquals("created parentIncludes",
                   newLayer.getParentIncludes(), testLayer.getParentIncludes());
      assertEquals("created saturated",
                   newLayer.getSaturated(), testLayer.getSaturated());
      assertEquals("created Type",
                   newLayer.getType(), testLayer.getType());
      // TODO validLabels

      // ensure it exists
      newLayer = l.getLayer(testLayer.getId());
      assertNotNull("new layer returned", newLayer);
      assertEquals("created ID",
                   newLayer.getId(), testLayer.getId());
      assertEquals("created Description",
                   newLayer.getDescription(), testLayer.getDescription());
      assertEquals("created parent",
                   newLayer.getParentId(), testLayer.getParentId());
      assertEquals("created alignment",
                   newLayer.getAlignment(), testLayer.getAlignment());
      assertEquals("created peers",
                   newLayer.getPeers(), testLayer.getPeers());
      assertEquals("created peersOverlap",
                   newLayer.getPeersOverlap(), testLayer.getPeersOverlap());
      assertEquals("created parentIncludes",
                   newLayer.getParentIncludes(), testLayer.getParentIncludes());
      assertEquals("created saturated",
                   newLayer.getSaturated(), testLayer.getSaturated());
      assertEquals("created Type",
                   newLayer.getType(), testLayer.getType());
      // TODO validLabels

      // can't create it again
      try {
        l.newLayer(testLayer);
        fail("Test layer can't be created if it already exists: " + testLayer.getId());
      } catch (StoreException x) {
      }

      // edit it
      testLayer.setDescription("Changed description")
        .setParentId("turns") // this shouldn't be updated
        .setAlignment(Constants.ALIGNMENT_INTERVAL)
        .setPeers(false)
        .setPeersOverlap(false)
        .setParentIncludes(false)
        .setSaturated(false)
        .setType(Constants.TYPE_NUMBER);
      // TODO validLabels
      newLayer = l.saveLayer(testLayer);
      assertNotNull("new layer returned", newLayer);
      assertEquals("saved ID",
                   newLayer.getId(), testLayer.getId());
      assertEquals("saved Description",
                   newLayer.getDescription(), testLayer.getDescription());
      assertEquals("parent not saved",
                   newLayer.getParentId(), "word");
      assertEquals("saved alignment",
                   newLayer.getAlignment(), testLayer.getAlignment());
      assertEquals("saved peers",
                   newLayer.getPeers(), testLayer.getPeers());
      assertEquals("saved peersOverlap",
                   newLayer.getPeersOverlap(), testLayer.getPeersOverlap());
      assertEquals("saved parentIncludes",
                   newLayer.getParentIncludes(), testLayer.getParentIncludes());
      assertEquals("saved saturated",
                   newLayer.getSaturated(), testLayer.getSaturated());
      assertEquals("saved Type",
                   newLayer.getType(), testLayer.getType());
      // TODO validLabels
         
      // ensure changes are saved
      newLayer = l.getLayer(testLayer.getId());
      assertNotNull("new layer returned", newLayer);
      assertEquals("saved ID",
                   newLayer.getId(), testLayer.getId());
      assertEquals("saved Description",
                   newLayer.getDescription(), testLayer.getDescription());
      assertEquals("parent not saved",
                   newLayer.getParentId(), "word");
      assertEquals("saved alignment",
                   newLayer.getAlignment(), testLayer.getAlignment());
      assertEquals("saved peers",
                   newLayer.getPeers(), testLayer.getPeers());
      assertEquals("saved peersOverlap",
                   newLayer.getPeersOverlap(), testLayer.getPeersOverlap());
      assertEquals("saved parentIncludes",
                   newLayer.getParentIncludes(), testLayer.getParentIncludes());
      assertEquals("saved saturated",
                   newLayer.getSaturated(), testLayer.getSaturated());
      assertEquals("saved Type",
                   newLayer.getType(), testLayer.getType());
      // TODO validLabels

      // delete it
      l.deleteLayer(testLayer.getId());

      // ensure it's been deleted
      try {
        l.getLayer(testLayer.getId());
        fail("Should not be able to get layer that has been deleted: " + testLayer.getId());
      } catch (StoreException x) {
      }
         
    } finally {
      // ensure layer is deleted
      try {
        l.deleteLayer(testLayer.getId());
      } catch(Exception exception) {
      }
    }
  }

  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.servlet.test.TestStoreAdministration");
  }
}
