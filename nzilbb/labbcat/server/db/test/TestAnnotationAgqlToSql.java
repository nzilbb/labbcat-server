//
// Copyright 2020 New Zealand Institute of Language, Brain and Behaviour, 
// University of Canterbury
// Written by Robert Fromont - robert.fromont@canterbury.ac.nz
//
//    This file is part of nzilbb.ag.
//
//    nzilbb.ag is free software; you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation; either version 3 of the License, or
//    (at your option) any later version.
//
//    nzilbb.ag is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with nzilbb.ag; if not, write to the Free Software
//    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//

package nzilbb.labbcat.server.db.test;

import org.junit.*;
import static org.junit.Assert.*;

import nzilbb.ag.Constants;
import nzilbb.ag.Layer;
import nzilbb.ag.Schema;
import nzilbb.ag.ql.AGQLException;
import nzilbb.labbcat.server.db.AnnotationAgqlToSql;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

public class TestAnnotationAgqlToSql {
  
   /**
    * Return a plausible schema, including SQL attributes.
    * @return A test schema.
    */
   public Schema getSchema() {
      return new Schema(
         "who", "turn", "utterance", "transcript",
            
         (Layer)(new Layer("transcript_language", "Language").setAlignment(Constants.ALIGNMENT_NONE)
                 .setPeers(false).setPeersOverlap(false).setSaturated(true))
         .with("@class_id", "transcript").with("@attribute", "language"),
      
         (Layer)(new Layer("transcript_scribe", "Scribe").setAlignment(Constants.ALIGNMENT_NONE)
                 .setPeers(false).setPeersOverlap(false).setSaturated(true))
         .with("@class_id", "transcript").with("@attribute", "scribe"),
      
         new Layer("transcript_type", "Type").setAlignment(Constants.ALIGNMENT_NONE)
         .setPeers(false).setPeersOverlap(false).setSaturated(true),
      
         (Layer)(new Layer("transcript_rating", "Ratings").setAlignment(Constants.ALIGNMENT_NONE)
                 .setPeers(true).setPeersOverlap(true).setSaturated(true))
         .with("@class_id", "transcript").with("@attribute", "rating"),
      
         (Layer)(new Layer("corpus", "Corpus").setAlignment(Constants.ALIGNMENT_NONE)
                 .setPeers(false).setPeersOverlap(false).setSaturated(true))
         .with("@layer_id", -100),
      
         (Layer)(new Layer("episode", "Episode").setAlignment(Constants.ALIGNMENT_NONE)
                 .setPeers(false).setPeersOverlap(false).setSaturated(true))
         .with("@layer_id", -50),
      
         (Layer)(new Layer("recording_date", "Recording Date").setAlignment(Constants.ALIGNMENT_NONE)
                 .setPeers(false).setPeersOverlap(false).setSaturated(true).setParentId("episode"))
         .with("@layer_id", -200),
      
         (Layer)(new Layer("who", "Participants").setAlignment(Constants.ALIGNMENT_NONE)
                 .setPeers(true).setPeersOverlap(true).setSaturated(true))
         .with("@layer_id", -2),
      
         (Layer)(new Layer("main_participant", "Main Participant").setAlignment(Constants.ALIGNMENT_NONE)
                 .setPeers(true).setPeersOverlap(true).setSaturated(true).setParentId("who"))
         .with("@layer_id", -3),
      
         (Layer)(new Layer("participant_gender", "Gender").setAlignment(Constants.ALIGNMENT_NONE)
                 .setPeers(true).setPeersOverlap(true).setSaturated(true).setParentId("who"))
         .with("@class_id", "speaker").with("@attribute", "gender"),
      
         (Layer)(new Layer("participant_age", "Age").setAlignment(Constants.ALIGNMENT_NONE)
                 .setPeers(true).setPeersOverlap(true).setSaturated(true).setParentId("who"))
         .with("@class_id", "speaker").with("@attribute", "age"),
      
         (Layer)(new Layer("comment", "Comment").setAlignment(Constants.ALIGNMENT_INTERVAL)
                 .setPeers(true).setPeersOverlap(false).setSaturated(false))
         .with("@layer_id", 31).with("@scope", "F"),
      
         (Layer)(new Layer("noise", "Noise")
                 .setAlignment(2).setPeers(true).setPeersOverlap(false).setSaturated(false))
         .with("@layer_id", 32).with("@scope", "F"),
      
         (Layer)(new Layer("turn", "Speaker turns").setAlignment(Constants.ALIGNMENT_INTERVAL)
                 .setPeers(true).setPeersOverlap(false).setSaturated(false)
                 .setParentId("who").setParentIncludes(true))
         .with("@layer_id", 11).with("@scope", "M"),
      
         (Layer)(new Layer("utterance", "Utterances").setAlignment(Constants.ALIGNMENT_INTERVAL)
                 .setPeers(true).setPeersOverlap(false).setSaturated(true)
                 .setParentId("turn").setParentIncludes(true))
         .with("@layer_id", 12).with("@scope", "M"),
      
         (Layer)(new Layer("transcript", "Words").setAlignment(Constants.ALIGNMENT_INTERVAL)
                 .setPeers(true).setPeersOverlap(false).setSaturated(false)
                 .setParentId("turn").setParentIncludes(true))
         .with("@layer_id", 0).with("@scope", "W"),
      
         (Layer)(new Layer("orthography", "Orthography").setAlignment(Constants.ALIGNMENT_NONE)
                 .setPeers(false).setPeersOverlap(false).setSaturated(false)
                 .setParentId("word").setParentIncludes(true))
         .with("@layer_id", 2).with("@scope", "W"),
      
         (Layer)(new Layer("segments", "Phones").setAlignment(Constants.ALIGNMENT_INTERVAL)
                 .setPeers(true).setPeersOverlap(false).setSaturated(true)
                 .setParentId("word").setParentIncludes(true))
         .with("@layer_id", 1).with("@scope", "S"),
      
         (Layer)(new Layer("pronounce", "Pronounce").setAlignment(Constants.ALIGNMENT_NONE)
                 .setPeers(false).setPeersOverlap(false).setSaturated(true)
                 .setParentId("word").setParentIncludes(true))
         .with("@layer_id", 23).with("@scope", "W"))

         .setEpisodeLayerId("episode")
         .setCorpusLayerId("corpus");
   } // end of getSchema()

   @Test public void idMatch() throws AGQLException {
      AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
      AnnotationAgqlToSql.Query q = transformer.sqlFor(
         "id == 'ew_0_456'",
         "DISTINCT annotation.*", null, null);
      assertEquals("SQL - id ==",
                   "SELECT DISTINCT annotation.*,"
                   +" 'transcript' AS layer"
                   +" FROM annotation_layer_0 annotation"
                   +" WHERE CONCAT('ew_0_', annotation.annotation_id) = 'ew_0_456'"
                   +" ORDER BY ag_id, parent_id, annotation_id",
                   q.sql);

      q = transformer.sqlFor(
         "['ew_2_456', 'ew_2_789', 'ew_2_101112'].includes(id)",
         "DISTINCT annotation.*, graph.transcript_id AS graph", null, "LIMIT 1,1");
      assertEquals("SQL - id IN",
                   "SELECT DISTINCT annotation.*, graph.transcript_id AS graph,"
                   +" 'orthography' AS layer"
                   +" FROM annotation_layer_2 annotation"
                   +" INNER JOIN transcript graph ON annotation.ag_id = graph.ag_id"
                   +" WHERE CONCAT('ew_2_', annotation.annotation_id)"
                   +" IN ('ew_2_456','ew_2_789','ew_2_101112')"
                   +" ORDER BY graph.transcript_id, parent_id, annotation_id LIMIT 1,1",
                   q.sql);
    
      q = transformer.sqlFor(
         "['ew_2_456', 'ew_2_789', 'ew_2_101112'].includes(id)",
         "COUNT(*)", null, null);
      assertEquals("SQL - id IN",
                   "SELECT COUNT(*),"
                   +" 'orthography' AS layer"
                   +" FROM annotation_layer_2 annotation"
                   +" WHERE CONCAT('ew_2_', annotation.annotation_id)"
                   +" IN ('ew_2_456','ew_2_789','ew_2_101112')",
                   q.sql);
    
   }

   @Test public void layerId() throws AGQLException {
      AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
      AnnotationAgqlToSql.Query q = transformer.sqlFor(
         "layer.id == 'orthography'",
         "DISTINCT annotation.*", null, null);
      assertEquals("SQL - layer.id ==",
                   "SELECT DISTINCT annotation.*,"
                   +" 'orthography' AS layer"
                   +" FROM annotation_layer_2 annotation"
                   +" WHERE 'orthography' = 'orthography'"
                   +" ORDER BY ag_id, parent_id, annotation_id",
                   q.sql);
      
      q = transformer.sqlFor(
         "layerId == 'orthography' && !/th[aeiou].*/.test(label)",
         "DISTINCT annotation.*, graph.transcript_id AS graph", null, "LIMIT 1,1");
      assertEquals("SQL - layerId ==",
                   "SELECT DISTINCT annotation.*, graph.transcript_id AS graph,"
                   +" 'orthography' AS layer"
                   +" FROM annotation_layer_2 annotation"
                   +" INNER JOIN transcript graph ON annotation.ag_id = graph.ag_id"
                   +" WHERE 'orthography' = 'orthography'"
                   +" AND annotation.label NOT REGEXP 'th[aeiou].*'"
                   +" ORDER BY graph.transcript_id, parent_id, annotation_id LIMIT 1,1",
                   q.sql);
      
      q = transformer.sqlFor(
         "!/th[aeiou].*/.test(label) && layerId == 'orthography'",
         "DISTINCT annotation.*, graph.transcript_id AS graph", null, "LIMIT 1,1");
      assertEquals("SQL - order isn't important",
                   "SELECT DISTINCT annotation.*, graph.transcript_id AS graph,"
                   +" 'orthography' AS layer"
                   +" FROM annotation_layer_2 annotation"
                   +" INNER JOIN transcript graph ON annotation.ag_id = graph.ag_id"
                   +" WHERE annotation.label NOT REGEXP 'th[aeiou].*'"
                   +" AND 'orthography' = 'orthography'"
                   +" ORDER BY graph.transcript_id, parent_id, annotation_id LIMIT 1,1",
                   q.sql);
   }

   @Test public void emptyExpression() throws AGQLException {
      AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
      try {
         transformer.sqlFor(
            null, "DISTINCT annotation.*, graph.transcript_id AS graph", null, null);
         fail("Null expression throws exception");
      } catch(AGQLException exception) {}

      try {
         transformer.sqlFor(
            "", "DISTINCT annotation.*", "transcript.annotated_by = 'user'", null);
         fail("Empty expression throws exception");
      } catch(AGQLException exception) {}
   }

   @Test public void inList() throws AGQLException {
      AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
      AnnotationAgqlToSql.Query q = transformer.sqlFor(
         "layer.id == 'utterance' && list('transcript').includes('ew_0_456')",
         "DISTINCT annotation.*", null, null);
      assertEquals("SQL",
                   "SELECT DISTINCT annotation.*, 'utterance' AS layer"
                   +" FROM annotation_layer_12 annotation"
                   +" INNER JOIN anchor start ON annotation.start_anchor_id = start.anchor_id"
                   +" INNER JOIN anchor end ON annotation.end_anchor_id = end.anchor_id"
                   +" WHERE 'utterance' = 'utterance'"
                   +" AND 'ew_0_456' IN"
                   +" (SELECT CONCAT('ew_0_', otherLayer.annotation_id)"
                   +" FROM annotation_layer_0 otherLayer"
                   +" INNER JOIN anchor otherLayer_start"
                   +" ON otherLayer.start_anchor_id = otherLayer_start.anchor_id"
                   +" AND otherLayer_start.offset <= end.offset"
                   +" INNER JOIN anchor otherLayer_end"
                   +" ON otherLayer.end_anchor_id = otherLayer_end.anchor_id"
                   +" AND start.offset <= otherLayer_end.offset"
                   +" WHERE otherLayer.ag_id = annotation.ag_id"
                   +" AND otherLayer.turn_annotation_id = annotation.turn_annotation_id)"
                   +" ORDER BY ag_id, start.offset, end.offset, parent_id, annotation_id",
                   q.sql);
   }

   @Test public void whoLabels() throws AGQLException {
      AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
      AnnotationAgqlToSql.Query q = transformer.sqlFor(
         "layer.id == 'orthography' && my('who').label == 'Robert'",
         "DISTINCT annotation.*", null, null);
      assertEquals("Who",
                   "SELECT DISTINCT annotation.*, 'orthography' AS layer"
                   +" FROM annotation_layer_2 annotation"
                   +" WHERE 'orthography' = 'orthography'"
                   +" AND (SELECT speaker.name"
                   +" FROM speaker"
                   +" INNER JOIN annotation_layer_11 turn"
                   +" ON speaker.speaker_number = turn.label"
                   +" WHERE turn.annotation_id = annotation.turn_annotation_id) = 'Robert'"
                   +" ORDER BY ag_id, parent_id, annotation_id",
                   q.sql);
   }

   @Test public void episodeLabel() throws AGQLException {
     AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
   AnnotationAgqlToSql.Query q = transformer.sqlFor(
         "layer.id == 'orthography' && my('who').label == 'Robert'"
         +" && my(\"episode\").label == 'some-episode'",
         "DISTINCT annotation.*", null, null);
      assertEquals("SQL",
                   "SELECT DISTINCT annotation.*, 'orthography' AS layer"
                   +" FROM annotation_layer_2 annotation"
                   +" INNER JOIN transcript graph ON annotation.ag_id = graph.ag_id"
                   +" WHERE 'orthography' = 'orthography'"
                   +" AND (SELECT speaker.name"
                   +" FROM speaker INNER JOIN annotation_layer_11 turn"
                   +" ON speaker.speaker_number = turn.label"
                   +" WHERE turn.annotation_id = annotation.turn_annotation_id) = 'Robert'"
                   +" AND (SELECT name FROM transcript_family"
                   +" WHERE transcript_family.family_id = graph.family_id) = 'some-episode'"
                   +" ORDER BY graph.transcript_id, parent_id, annotation_id",
                   q.sql);
   }

   @Test public void transcriptTypeLabel() throws AGQLException {
      AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
      AnnotationAgqlToSql.Query q = transformer.sqlFor(
         "layer.id == 'utterance' && my('who').label == 'Robert'"
         +" && my('transcript_type').label == 'wordlist'",
         "DISTINCT annotation.*", null, null);
      assertEquals("SQL",
                   "SELECT DISTINCT annotation.*, 'utterance' AS layer"
                   +" FROM annotation_layer_12 annotation"
                   +" INNER JOIN transcript graph ON annotation.ag_id = graph.ag_id"
                   +" WHERE 'utterance' = 'utterance'"
                   +" AND (SELECT speaker.name FROM speaker"
                   +" INNER JOIN annotation_layer_11 turn ON speaker.speaker_number = turn.label"
                   +" WHERE turn.annotation_id = annotation.turn_annotation_id) = 'Robert'"
                   +" AND (SELECT type FROM transcript_type"
                   +" WHERE transcript_type.type_id = graph.type_id LIMIT 1) = 'wordlist'"
                   +" ORDER BY graph.transcript_id, parent_id, annotation_id",
                   q.sql);
   }

   @Test public void startOffset() throws AGQLException {
      AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
      AnnotationAgqlToSql.Query q = transformer.sqlFor(
         "layer.id == 'orthography' && my('who').label == 'Robert'"
         +" && start.offset < 12.345",
         "DISTINCT annotation.*", null, null);
      assertEquals("Who",
                   "SELECT DISTINCT annotation.*, 'orthography' AS layer"
                   +" FROM annotation_layer_2 annotation"
                   +" INNER JOIN anchor start ON annotation.start_anchor_id = start.anchor_id"
                   +" INNER JOIN anchor end ON annotation.end_anchor_id = end.anchor_id"
                   +" WHERE 'orthography' = 'orthography'"
                   +" AND (SELECT speaker.name"
                   +" FROM speaker INNER JOIN annotation_layer_11 turn"
                   +" ON speaker.speaker_number = turn.label"
                   +" WHERE turn.annotation_id = annotation.turn_annotation_id) = 'Robert'"
                   +" AND start.offset < 12.345"
                   +" ORDER BY ag_id, start.offset, end.offset, parent_id, annotation_id",
                   q.sql);

      // q = transformer.sqlFor( TODO
      //    "layer.id == 'orthography' && my('who').label == 'Robert'"
      //    +" && my('utterances').start.offset = 12.345",
      //    "DISTINCT annotation.*", null, null);
      // assertEquals("Who",
      //              "SELECT DISTINCT annotation.*, 'orthography' AS layer"
      //              +" FROM annotation_layer_2 annotation"
      //              +" WHERE 'orthography' = 'orthography'"
      //              +" AND (SELECT speaker.name"
      //              +" FROM speaker"
      //              +" INNER JOIN annotation_layer_11 turn"
      //              +" ON speaker.speaker_number = turn.label"
      //              +" WHERE turn.annotation_id = annotation.turn_annotation_id) = 'Robert'"
      //              +" ORDER BY ag_id, parent_id, annotation_id",
      //              q.sql);
   }

   @Test public void graphId() throws AGQLException {
      AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
      AnnotationAgqlToSql.Query q = transformer.sqlFor(
         "graph.id == 'AdaAicheson-01.trs' && layer.id == 'orthography'",
         "DISTINCT annotation.*", null, null);
      assertEquals("Graph ID",
                   "SELECT DISTINCT annotation.*, 'orthography' AS layer"
                   +" FROM annotation_layer_2 annotation"
                   +" INNER JOIN transcript graph ON annotation.ag_id = graph.ag_id"
                   +" WHERE graph.transcript_id = 'AdaAicheson-01.trs'"
                   +" AND 'orthography' = 'orthography'"
                   +" ORDER BY graph.transcript_id, parent_id, annotation_id",
                   q.sql);
   }
   @Test public void labels() throws AGQLException {
      AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
      AnnotationAgqlToSql.Query q = transformer.sqlFor(
         "layerId = 'utterance' && labels('orthography').includes('foo')",
         "DISTINCT annotation.*", null, null);
      assertEquals("Transcript attribute - SQL",
                   "SELECT DISTINCT annotation.*, 'utterance' AS layer"
                   +" FROM annotation_layer_12 annotation"
                   +" INNER JOIN anchor start ON annotation.start_anchor_id = start.anchor_id"
                   +" INNER JOIN anchor end ON annotation.end_anchor_id = end.anchor_id"
                   +" WHERE 'utterance' = 'utterance'"
                   +" AND 'foo' IN (SELECT label"
                   +" FROM annotation_layer_2 otherLayer"
                   +" INNER JOIN anchor otherLayer_start"
                   +" ON otherLayer.start_anchor_id = otherLayer_start.anchor_id"
                   +" AND otherLayer_start.offset <= end.offset"
                   +" INNER JOIN anchor otherLayer_end"
                   +" ON otherLayer.end_anchor_id = otherLayer_end.anchor_id"
                   +" AND start.offset <= otherLayer_end.offset"
                   +" WHERE otherLayer.ag_id = annotation.ag_id"
                   +" AND otherLayer.turn_annotation_id = annotation.turn_annotation_id)"
                   +" ORDER BY ag_id, start.offset, end.offset, parent_id, annotation_id",
                   q.sql);

      q = transformer.sqlFor(
         "layerId = 'utterance' && labels('who').includes('Ada')",
         "DISTINCT annotation.*", null, null);
      assertEquals("Transcript attribute - SQL",
                   "SELECT DISTINCT annotation.*, 'utterance' AS layer"
                   +" FROM annotation_layer_12 annotation"
                   +" WHERE 'utterance' = 'utterance'"
                   +" AND 'Ada' IN (SELECT speaker.name"
                   +" FROM speaker"
                   +" INNER JOIN annotation_layer_11 turn ON speaker.speaker_number = turn.label"
                   +" WHERE turn.annotation_id = annotation.turn_annotation_id)"
                   +" ORDER BY ag_id, parent_id, annotation_id",
                   q.sql);
       }

   // // @Test public void attributeLabel() throws AGQLException {
   // //    AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
   // //    AnnotationAgqlToSql.Query q = transformer.sqlFor(
   // //       "my('transcript_scribe').label == 'someone'",
   // //       "DISTINCT annotation.*", null, null, null);
   // //    assertEquals("Transcript attribute - SQL",
   // //                 "SELECT DISTINCT annotation.* FROM transcript"
   // //                 +" WHERE"
   // //                 +" (SELECT label"
   // //                 +" FROM annotation_transcript USE INDEX(IDX_AG_ID_NAME)"
   // //                 +" WHERE annotation_transcript.layer = 'scribe'"
   // //                 +" AND annotation_transcript.ag_id = transcript.ag_id ORDER BY annotation_id"
   // //                 +" LIMIT 1) = 'someone'"
   // //                 +" ORDER BY DISTINCT annotation.*",
   // //                 q.sql);

   // //    q = transformer.sqlFor(
   // //       "my('participant_gender').label == 'NA'",
   // //       "DISTINCT annotation.*", null, null, null);
   // //    assertEquals("Participant attribute - SQL",
   // //                 "SELECT DISTINCT annotation.* FROM transcript"
   // //                 +" WHERE"
   // //                 +" (SELECT label"
   // //                 +" FROM annotation_participant"
   // //                 +" INNER JOIN transcript_speaker"
   // //                 +" ON annotation_participant.speaker_number = transcript_speaker.speaker_number"
   // //                 +" AND annotation_participant.layer = 'gender'"
   // //                 +" WHERE transcript_speaker.ag_id = transcript.ag_id"
   // //                 +" ORDER BY annotation_id LIMIT 1) = 'NA'"
   // //                 +" ORDER BY DISTINCT annotation.*",
   // //                 q.sql);

   // //    q = transformer.sqlFor(
   // //       "my('recording_date').label > '2019-06-17'",
   // //       "DISTINCT annotation.*", null, null, null);
   // //    assertEquals("Episode attribute - SQL",
   // //                 "SELECT DISTINCT annotation.* FROM transcript"
   // //                 +" WHERE"
   // //                 +" (SELECT label"
   // //                 +" FROM `annotation_layer_-200` annotation"
   // //                 +" WHERE annotation.family_id = transcript.family_id"
   // //                 +" ORDER BY annotation.ordinal LIMIT 1) > '2019-06-17'"
   // //                 +" ORDER BY DISTINCT annotation.*",
   // //                 q.sql);

   // //    q = transformer.sqlFor(
   // //       "/.*bell.*/.test(my('noise').label)",
   // //       "DISTINCT annotation.*", null, null, null);
   // //    assertEquals("Annotation - SQL",
   // //                 "SELECT DISTINCT annotation.* FROM transcript"
   // //                 +" WHERE"
   // //                 +" (SELECT label"
   // //                 +" FROM annotation_layer_32 annotation"
   // //                 +" INNER JOIN anchor ON annotation.start_anchor_id = anchor.anchor_id"
   // //                 +" WHERE annotation.ag_id = transcript.ag_id"
   // //                 +" ORDER BY anchor.offset, annotation.annotation_id LIMIT 1) REGEXP '.*bell.*'"
   // //                 +" ORDER BY DISTINCT annotation.*",
   // //                 q.sql);
   // // }

   // // @Test public void listLength() throws AGQLException {
   // //    AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
   // //    AnnotationAgqlToSql.Query q = transformer.sqlFor(
   // //       "list('transcript_rating').length > 10",
   // //       "DISTINCT annotation.*", null, null, null);
   // //    assertEquals("Transcript attribute - SQL",
   // //                 "SELECT DISTINCT annotation.* FROM transcript"
   // //                 +" WHERE (SELECT COUNT(*)"
   // //                 +" FROM annotation_transcript USE INDEX(IDX_AG_ID_NAME)"
   // //                 +" WHERE annotation_transcript.layer = 'rating'"
   // //                 +" AND annotation_transcript.ag_id = transcript.ag_id) > 10"
   // //                 +" ORDER BY DISTINCT annotation.*",
   // //                 q.sql);

   // //    q = transformer.sqlFor(
   // //       "list('participant_gender').length < 1",
   // //       "DISTINCT annotation.*", null, null, null);
   // //    assertEquals("Transcript attribute - SQL",
   // //                 "SELECT DISTINCT annotation.* FROM transcript"
   // //                 +" WHERE (SELECT COUNT(*)"
   // //                 +" FROM annotation_participant"
   // //                 +" INNER JOIN transcript_speaker"
   // //                 +" ON annotation_participant.speaker_number = transcript_speaker.speaker_number"
   // //                 +" AND annotation_participant.layer = 'gender'"
   // //                 +" WHERE transcript_speaker.ag_id = transcript.ag_id) < 1"
   // //                 +" ORDER BY DISTINCT annotation.*",
   // //                 q.sql);
    
   // //    q = transformer.sqlFor(
   // //       "list('recording_date').length > 0",
   // //       "DISTINCT annotation.*", null, null, null);
   // //    assertEquals("Episode attribute - SQL",
   // //                 "SELECT DISTINCT annotation.* FROM transcript"
   // //                 +" WHERE"
   // //                 +" (SELECT COUNT(*)"
   // //                 +" FROM `annotation_layer_-200` annotation"
   // //                 +" WHERE annotation.family_id = transcript.family_id) > 0"
   // //                 +" ORDER BY DISTINCT annotation.*",
   // //                 q.sql);

   // //    q = transformer.sqlFor(
   // //       "list('transcript').length > 100",
   // //       "DISTINCT annotation.*", null, null, null);
   // //    assertEquals("Annotation - SQL",
   // //                 "SELECT DISTINCT annotation.* FROM transcript"
   // //                 +" WHERE (SELECT COUNT(*)"
   // //                 +" FROM annotation_layer_0 annotation"
   // //                 +" WHERE annotation.ag_id = transcript.ag_id) > 100"
   // //                 +" ORDER BY DISTINCT annotation.*",
   // //                 q.sql);
   // // }

   // // @Test public void annotators() throws AGQLException {
   // //    AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
   // //    AnnotationAgqlToSql.Query q = transformer.sqlFor(
   // //       "annotators('transcript_rating').includes('someone')",
   // //       "DISTINCT annotation.*", null, null, null);
   // //    assertEquals("Transcript attribute - SQL",
   // //                 "SELECT DISTINCT annotation.* FROM transcript"
   // //                 +" WHERE 'someone' IN"
   // //                 +" (SELECT DISTINCT annotated_by"
   // //                 +" FROM annotation_transcript USE INDEX(IDX_AG_ID_NAME)"
   // //                 +" WHERE annotation_transcript.layer = 'rating'"
   // //                 +" AND annotation_transcript.ag_id = transcript.ag_id)"
   // //                 +" ORDER BY DISTINCT annotation.*",
   // //                 q.sql);

   // //    q = transformer.sqlFor(
   // //       "annotators('participant_gender').includes('someone')",
   // //       "DISTINCT annotation.*", null, null, null);
   // //    assertEquals("Transcript attribute - SQL",
   // //                 "SELECT DISTINCT annotation.* FROM transcript"
   // //                 +" WHERE 'someone' IN"
   // //                 +" (SELECT DISTINCT annotated_by"
   // //                 +" FROM annotation_participant"
   // //                 +" INNER JOIN transcript_speaker"
   // //                 +" ON annotation_participant.speaker_number = transcript_speaker.speaker_number"
   // //                 +" AND annotation_participant.layer = 'gender'"
   // //                 +" WHERE transcript_speaker.ag_id = transcript.ag_id)"
   // //                 +" ORDER BY DISTINCT annotation.*",
   // //                 q.sql);
    
   // //    q = transformer.sqlFor(
   // //       "annotators('recording_date').includes('someone')",
   // //       "DISTINCT annotation.*", null, null, null);
   // //    assertEquals("Episode attribute - SQL",
   // //                 "SELECT DISTINCT annotation.* FROM transcript"
   // //                 +" WHERE 'someone' IN (SELECT DISTINCT annotated_by"
   // //                 +" FROM `annotation_layer_-200` annotation"
   // //                 +" WHERE annotation.family_id = transcript.family_id)"
   // //                 +" ORDER BY DISTINCT annotation.*",
   // //                 q.sql);

   // //    q = transformer.sqlFor(
   // //       "annotators('noise').includes('someone')",
   // //       "DISTINCT annotation.*", null, null, null);
   // //    assertEquals("Annotation - SQL",
   // //                 "SELECT DISTINCT annotation.* FROM transcript"
   // //                 +" WHERE 'someone' IN (SELECT DISTINCT annotated_by"
   // //                 +" FROM annotation_layer_32 annotation"
   // //                 +" WHERE annotation.ag_id = transcript.ag_id)"
   // //                 +" ORDER BY DISTINCT annotation.*",
   // //                 q.sql);
   // // }
  
   // // @Test public void invalidLayers() throws AGQLException {
   // //    AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
   // //    try {
   // //       AnnotationAgqlToSql.Query q = transformer.sqlFor(
   // //          "my('invalid layer 1').label == 'NA'"
   // //          + " AND list('invalid layer 2').length > 2"
   // //          + " AND my('invalid layer 3').label = 'NA'"
   // //          + " AND 'labbcat' NOT IN annotators('invalid layer 4')",
   // //          "DISTINCT annotation.*", null, null, null);
   // //       fail("sqlFor fails: " + q.sql);
   // //    } catch(AGQLException exception) {
   // //       assertEquals("Number of errors: " + exception.getErrors(), 4, exception.getErrors().size());
   // //    }
   // // }

   // // @Test public void userWhereClause() throws AGQLException {
   // //    AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
   // //    AnnotationAgqlToSql.Query q = transformer.sqlFor(
   // //       "/Ada.+/.test(label)",
   // //       "DISTINCT annotation.*, transcript.ag_id",
   // //       "(EXISTS (SELECT * FROM role"
   // //       + " INNER JOIN role_permission ON role.role_id = role_permission.role_id" 
   // //       + " INNER JOIN annotation_transcript access_attribute" 
   // //       + " ON access_attribute.layer = role_permission.attribute_name" 
   // //       + " AND access_attribute.label REGEXP role_permission.value_pattern"
   // //       + " AND role_permission.entity REGEXP '.*t.*'"
   // //       + " WHERE user_id = 'test'"
   // //       + " AND access_attribute.ag_id = transcript.ag_id)"
   // //       + " OR EXISTS (SELECT * FROM role"
   // //       + " INNER JOIN role_permission ON role.role_id = role_permission.role_id" 
   // //       + " AND role_permission.attribute_name = 'corpus'" 
   // //       + " AND role_permission.entity REGEXP '.*t.*'"
   // //       + " WHERE transcript.corpus_name REGEXP role_permission.value_pattern"
   // //       + " AND user_id = 'test')"
   // //       + " OR NOT EXISTS (SELECT * FROM role_permission))",
   // //       null, null);
   // //    assertEquals(
   // //       "SQL - label",
   // //       "SELECT DISTINCT annotation.*, transcript.ag_id FROM transcript"
   // //       + " WHERE DISTINCT annotation.* REGEXP 'Ada.+'"
   // //       + " AND (EXISTS (SELECT * FROM role"
   // //       + " INNER JOIN role_permission ON role.role_id = role_permission.role_id" 
   // //       + " INNER JOIN annotation_transcript access_attribute" 
   // //       + " ON access_attribute.layer = role_permission.attribute_name" 
   // //       + " AND access_attribute.label REGEXP role_permission.value_pattern"
   // //       + " AND role_permission.entity REGEXP '.*t.*'"
   // //       + " WHERE user_id = 'test'"
   // //       + " AND access_attribute.ag_id = transcript.ag_id)"
   // //       + " OR EXISTS (SELECT * FROM role"
   // //       + " INNER JOIN role_permission ON role.role_id = role_permission.role_id" 
   // //       + " AND role_permission.attribute_name = 'corpus'" 
   // //       + " AND role_permission.entity REGEXP '.*t.*'"
   // //       + " WHERE transcript.corpus_name REGEXP role_permission.value_pattern"
   // //       + " AND user_id = 'test')"
   // //       + " OR NOT EXISTS (SELECT * FROM role_permission))"
   // //       + " ORDER BY DISTINCT annotation.*",
   // //       q.sql);
   // //    assertEquals("Parameter count - label", 0, q.parameters.size());
   // // }

   public static void main(String args[])  {
      org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.db.test.TestAnnotationAgqlToSql");
   }

}
