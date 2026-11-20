//
// Copyright 2026 New Zealand Institute of Language, Brain and Behaviour, 
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

package nzilbb.labbcat.server.api.edit.participants;
	      
import org.junit.*;
import static org.junit.Assert.*;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.json.JsonString;
import nzilbb.ag.Annotation;
import nzilbb.ag.PermissionException;
import nzilbb.ag.StoreException;
import nzilbb.ag.serialize.SerializationDescriptor;
import nzilbb.labbcat.LabbcatAdmin;
import nzilbb.labbcat.Response;
import nzilbb.labbcat.ResponseException;
import nzilbb.labbcat.http.HttpRequestPost;
import nzilbb.labbcat.http.HttpRequestPostMultipart;
import nzilbb.labbcat.model.*;
import nzilbb.labbcat.model.Match;

/**
 * These tests assume that there is a working LaBB-CAT instance with the latest version of
 * nzilbb.labbcat.server.jar installed.
 */
public class TestMerge
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
  
  /**
   * Test /api/edit/participants/merge endpoint will merge participants.
   */
  @Test public void basicMerge()
    throws Exception {

    Annotation source1 = new Annotation()
      .setLayerId("participant")
      .setLabel("TestMerge source1 " + new java.util.Date().getTime());
    l.saveParticipant(source1);
    assertNotNull("Source1 exists", l.getParticipant(source1.getLabel(), null));
    Annotation source2 = new Annotation()
      .setLayerId("participant")
      .setLabel("TestMerge source2 " + new java.util.Date().getTime());
    l.saveParticipant(source2);
    assertNotNull("Source2 exists", l.getParticipant(source2.getLabel(), null));
    Annotation destination = new Annotation()
      .setLayerId("participant")
      .setLabel("TestMerge destination " + new java.util.Date().getTime());
    l.saveParticipant(destination);
    assertNotNull("Destination exists", l.getParticipant(destination.getLabel(), null));
    
    try {
      
      HttpRequestPost request = l.post("api/edit/participants/merge")
        .setParameter("source", source1.getLabel())
        .setParameter("source", source2.getLabel())
        .setParameter("destination", destination.getLabel());
      Response response = new Response(request.post(), false);
      response.checkForErrors();
      String id = ((JsonString)response.getModel()).getString();
      assertNotNull("Destination ID returned.", id);
      // System.out.println(id);
      // for (String m : response.getMessages()) System.out.println(m);
      assertNotNull("Destination still exists",
                    l.getParticipant(id, null));
      assertNull("Source1 no longer exists",
                 l.getParticipant(source1.getLabel(), null));
      assertNull("Source2 no longer exists",
                 l.getParticipant(source2.getLabel(), null));
      
    } finally {
      l.setVerbose(false);
      try { l.deleteParticipant(source1.getLabel()); } catch(Exception exception) {
        System.err.println(""+exception);
      }
      try { l.deleteParticipant(source2.getLabel()); } catch(Exception exception) {
        System.err.println(""+exception);
      }
      try { l.deleteParticipant(destination.getLabel()); } catch(Exception exception) {
        System.err.println(""+exception);
      }
    }
  }
    
  /**
   * Test /api/edit/participants/merge validates input.
   */
  @Test public void validation()
    throws Exception {

    Annotation source1 = new Annotation()
      .setLayerId("participant")
      .setLabel("TestMerge source1 " + new java.util.Date().getTime());
    l.saveParticipant(source1);
    assertNotNull("Source1 exists", l.getParticipant(source1.getLabel(), null));
    Annotation source2 = new Annotation()
      .setLayerId("participant")
      .setLabel("TestMerge source2 " + new java.util.Date().getTime());
    l.saveParticipant(source2);
    assertNotNull("Source2 exists", l.getParticipant(source2.getLabel(), null));
    Annotation destination = new Annotation()
      .setLayerId("participant")
      .setLabel("TestMerge destination " + new java.util.Date().getTime());
    l.saveParticipant(destination);
    assertNotNull("Destination exists", l.getParticipant(destination.getLabel(), null));
    
    try {
      
      try {
        HttpRequestPost request = l.post("api/edit/participants/merge")
          .setParameter("destination", destination.getLabel());
        Response response = new Response(request.post(), false);
        response.checkForErrors();
        fail("Should fail with no sources");
       } catch(Exception exception) {
        System.out.println(exception.getMessage());
      }
      
      try {
        HttpRequestPost request = l.post("api/edit/participants/merge")
          .setParameter("source", source1.getLabel())
          .setParameter("source", source2.getLabel());
        Response response = new Response(request.post(), false);
        response.checkForErrors();
        fail("Should fail with no destination");
       } catch(Exception exception) {
        System.out.println(exception.getMessage());
      }
      
      try {
        HttpRequestPost request = l.post("api/edit/participants/merge")
          .setParameter("source", source1.getLabel())
          .setParameter("source", "Doesn't exist")
          .setParameter("destination", destination.getLabel());
        Response response = new Response(request.post(), false);
        response.checkForErrors();
        fail("Should fail with invalid source");
       } catch(Exception exception) {
        System.out.println(exception.getMessage());
      }
      
      try {
        HttpRequestPost request = l.post("api/edit/participants/merge")
          .setParameter("source", source1.getLabel())
          .setParameter("source", source2.getLabel())
          .setParameter("destination", "Doesn't exist");
        Response response = new Response(request.post(), false);
        response.checkForErrors();
        fail("Should fail with invalid destination");
       } catch(Exception exception) {
        System.out.println(exception.getMessage());
      }
      
      try {
        HttpRequestPost request = l.post("api/edit/participants/merge")
          .setParameter("source", source1.getLabel())
          .setParameter("source", destination.getLabel())
          .setParameter("destination", destination.getLabel());
        Response response = new Response(request.post(), false);
        response.checkForErrors();
        fail("Should fail id destination is also source");
       } catch(Exception exception) {
        System.out.println(exception.getMessage());
      }
      
    } finally {
      l.setVerbose(false);
      try { l.deleteParticipant(source1.getLabel()); } catch(Exception exception) {
        System.err.println(""+exception);
      }
      try { l.deleteParticipant(source2.getLabel()); } catch(Exception exception) {
        System.err.println(""+exception);
      }
      try { l.deleteParticipant(destination.getLabel()); } catch(Exception exception) {
        System.err.println(""+exception);
      }
    }
  }
    
  public static void main(String args[]) {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.api.edit.participants.TestMerge");
  }
}
