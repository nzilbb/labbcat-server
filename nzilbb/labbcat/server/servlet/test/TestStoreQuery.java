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
import nzilbb.ag.serialize.SerializationDescriptor;
import nzilbb.labbcat.LabbcatView;
import nzilbb.labbcat.ResponseException;
import nzilbb.labbcat.http.HttpRequestGet;
import nzilbb.labbcat.model.Match;
import org.json.JSONObject;

/**
 * These tests assume that there is a working LaBB-CAT instance with the latest version of
 * nzilbb.labbcat.server.jar installed.  
 */
public class TestStoreQuery
{
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
   
   @Test public void store() throws Exception {
      
      String id = l.getId();
      assertEquals("getId: ID matches the url",
                   labbcatUrl, id);

      String[] ids = l.getLayerIds();
      //for (String id : ids) System.out.println("layer " + id);
      assertTrue("getLayerIds: Some IDs are returned",
                 ids.length > 0);
      Set<String> idSet = Arrays.asList(ids).stream().collect(Collectors.toSet());
      assertTrue("getLayerIds: Has transcript layer",
                 idSet.contains("transcript"));

      Layer[] layers = l.getLayers();
      //for (String id : ids) System.out.println("layer " + id);
      assertTrue("getLayers: Some IDs are returned",
                 layers.length > 0);

      ids = l.getCorpusIds();
      // for (String id : ids) System.out.println("corpus " + id);
      assertTrue("getCorpusIds: Some IDs are returned",
                 ids.length > 0);
      String corpus = ids[0];

      ids = l.getParticipantIds();
      // for (String id : ids) System.out.println("participant " + id);
      assertTrue("getParticipantIds: Some IDs are returned",
                 ids.length > 0);
      String participantId = ids[0];

      ids = l.getTranscriptIds();
      // for (String id : ids) System.out.println("graph " + id);
      assertTrue("getTranscriptIds: Some IDs are returned",
                 ids.length > 0);

      long count = l.countMatchingParticipantIds("/.+/.test(id)");
      assertTrue("countMatchingParticipantIds: There are some matches",
                 count > 0);

      ids = l.getMatchingParticipantIds("/.+/.test(id)");
      assertTrue("getMatchingParticipantIds: Some IDs are returned",
                 ids.length > 0);
      if (ids.length < 2)
      {
         System.out.println("getMatchingParticipantIds: Too few participants to test pagination");
      }
      else
      {
         ids = l.getMatchingParticipantIds("/.+/.test(id)", 2, 0);
         assertEquals("getMatchingParticipantIds: Two IDs are returned",
                      2, ids.length);
      }

      ids = l.getTranscriptIdsInCorpus(corpus);
      assertTrue("getTranscriptIdsInCorpus: Some IDs are returned for corpus " + corpus,
                 ids.length > 0);

      ids = l.getTranscriptIdsWithParticipant(participantId);
      assertTrue("getTranscriptIdsWithParticipant: Some IDs are returned for participant " + participantId,
                 ids.length > 0);

      count = l.countMatchingTranscriptIds("/.+/.test(id)");
      assertTrue("countMatchingTranscriptIds: There are some matches",
                 count > 0);

      ids = l.getMatchingTranscriptIds("/.+/.test(id)");
      assertTrue("countMatchingTranscriptIds: Some IDs are returned",
                 ids.length > 0);
      String graphId = ids[0];
      if (ids.length < 2)
      {
         System.out.println("countMatchingTranscriptIds: Too few graphs to test pagination");
      }
      else
      {
         ids = l.getMatchingTranscriptIds("/.+/.test(id)", 2, 0, "id DESC");
         assertEquals("getMatchingTranscriptIds: Two IDs are returned",
                      2, ids.length);
      }         
      
      count = l.countAnnotations(graphId, "orthography");
      assertTrue("countAnnotations: There are some matches",
                 count > 0);
      
      Annotation[] annotations = l.getAnnotations(graphId, "orthography", 2, 0);
      if (count < 2)
      {
         System.out.println("getAnnotations: Too few annotations to test pagination");
      }
      else
      {
         assertEquals("getAnnotations: Two annotations are returned",
                      2, annotations.length);
      }
      if (annotations.length == 0)
      {
         System.out.println("getAnchors: Can't test getAnchors() - no annotations in " + graphId);
      }
      else
      {
         // create an array of anchorIds
         String[] anchorIds = new String[annotations.length];
         for (int i = 0; i < annotations.length; i++) anchorIds[i] = annotations[i].getStartId();

         // finally, get the anchors
         Anchor[] anchors = l.getAnchors(graphId, anchorIds);         
         assertEquals("getAnchors: Correct number of anchors is returned",
                      anchorIds.length, anchors.length);
      }

      String url = l.getMedia(graphId, "", "audio/wav");
      assertNotNull("getMedia: There is some media",
                    url);

      Layer layer = l.getLayer("orthography");
      assertEquals("getLayer: Correct layer",
                   "orthography", layer.getId());

      Annotation participant = l.getParticipant(participantId);
      assertEquals("getParticipant: Correct participant",
                   participantId, participant.getLabel()); // not getId()

      count = l.countMatchingAnnotations(
         "layer.id == 'orthography' && label == 'and'");
      assertTrue("countMatchingAnnotations: There are some matches",
                 count > 0);

      annotations = l.getMatchingAnnotations(
         "layer.id == 'orthography' && label == 'and'", 2, 0);
      assertEquals("getMatchingAnnotations: Two annotations are returned",
                   2, annotations.length);

      MediaTrackDefinition[] tracks = l.getMediaTracks();
      assertTrue("getMediaTracks: Some tracks are returned",
                 tracks.length > 0);

      // get some annotations so we have valid anchor IDs
      MediaFile[] files = l.getAvailableMedia(graphId);
      assertTrue("getAvailableMedia: " + graphId + " has some tracks",
                 files.length > 0);

      // get some annotations so we have valid anchor IDs
      MediaFile[] docs = l.getEpisodeDocuments(graphId);
      if (docs.length == 0)
      {
         System.out.println("getEpisodeDocuments: " + graphId + " has no documents");
      }

      SerializationDescriptor[] descriptors = l.getSerializerDescriptors();
      // for (SerializationDescriptor descriptor : descriptors) System.out.println("descriptor " + descriptor);
      assertTrue("Some descriptors are returned",
                 descriptors.length > 0);
      Set<Object> mimeTypeSet = Arrays.asList(descriptors).stream()
         .map(l->l.getMimeType())
         .collect(Collectors.toSet());
      assertTrue("Has plain text serialization: " + mimeTypeSet,
                 mimeTypeSet.contains("text/plain"));

      descriptors = l.getDeserializerDescriptors();
      // for (SerializationDescriptor descriptor : descriptors) System.out.println("descriptor " + descriptor);
      assertTrue("Some descriptors are returned",
                 descriptors.length > 0);
      mimeTypeSet = Arrays.asList(descriptors).stream()
         .map(l->l.getMimeType())
         .collect(Collectors.toSet());
      assertTrue("Has plain text serialization: " + mimeTypeSet,
                 mimeTypeSet.contains("text/plain"));
   }

   public static void main(String args[]) {
      org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.servlet.test.TestStoreQuery");
   }
}
