//
// Copyright 2021-2024 New Zealand Institute of Language, Brain and Behaviour, 
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

package nzilbb.labbcat.server.db;

import org.junit.*;
import static org.junit.Assert.*;

import nzilbb.ag.Constants;
import nzilbb.ag.Layer;
import nzilbb.ag.Schema;
import nzilbb.ag.ql.AGQLException;
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
      "who", "turn", "utterance", "word",
            
      (Layer)(new Layer("transcript_language", "Language").setAlignment(Constants.ALIGNMENT_NONE)
              .setPeers(false).setPeersOverlap(false).setSaturated(true))
      .with("class_id", "transcript").with("attribute", "language"),
      
      (Layer)(new Layer("transcript_scribe", "Scribe").setAlignment(Constants.ALIGNMENT_NONE)
              .setPeers(false).setPeersOverlap(false).setSaturated(true))
      .with("class_id", "transcript").with("attribute", "scribe"),
      
      new Layer("transcript_type", "Type").setAlignment(Constants.ALIGNMENT_NONE)
      .setPeers(false).setPeersOverlap(false).setSaturated(true),
      
      (Layer)(new Layer("transcript_rating", "Ratings").setAlignment(Constants.ALIGNMENT_NONE)
              .setPeers(true).setPeersOverlap(true).setSaturated(true))
      .with("class_id", "transcript").with("attribute", "rating"),
      
      (Layer)(new Layer("corpus", "Corpus").setAlignment(Constants.ALIGNMENT_NONE)
              .setPeers(false).setPeersOverlap(false).setSaturated(true))
      .with("layer_id", -100),
      
      (Layer)(new Layer("episode", "Episode").setAlignment(Constants.ALIGNMENT_NONE)
              .setPeers(false).setPeersOverlap(false).setSaturated(true))
      .with("layer_id", -50),
      
      (Layer)(new Layer("recording_date", "Recording Date").setAlignment(Constants.ALIGNMENT_NONE)
              .setPeers(false).setPeersOverlap(false).setSaturated(true).setParentId("episode"))
      .with("layer_id", -200),
      
      (Layer)(new Layer("who", "Participants").setAlignment(Constants.ALIGNMENT_NONE)
              .setPeers(true).setPeersOverlap(true).setSaturated(true))
      .with("layer_id", -2),
      
      (Layer)(new Layer("main_participant", "Main Participant").setAlignment(Constants.ALIGNMENT_NONE)
              .setPeers(true).setPeersOverlap(true).setSaturated(true).setParentId("who"))
      .with("layer_id", -3),
      
      (Layer)(new Layer("participant_gender", "Gender").setAlignment(Constants.ALIGNMENT_NONE)
              .setPeers(true).setPeersOverlap(true).setSaturated(true).setParentId("who"))
      .with("class_id", "speaker").with("attribute", "gender"),
      
      (Layer)(new Layer("participant_age", "Age").setAlignment(Constants.ALIGNMENT_NONE)
              .setPeers(true).setPeersOverlap(true).setSaturated(true).setParentId("who"))
      .with("class_id", "speaker").with("attribute", "age"),
      
      (Layer)(new Layer("comment", "Comment").setAlignment(Constants.ALIGNMENT_INTERVAL)
              .setPeers(true).setPeersOverlap(false).setSaturated(false))
      .with("layer_id", 31).with("scope", "F"),
      
      (Layer)(new Layer("noise", "Noise")
              .setAlignment(2).setPeers(true).setPeersOverlap(false).setSaturated(false))
      .with("layer_id", 32).with("scope", "F"),
      
      (Layer)(new Layer("turn", "Speaker turns").setAlignment(Constants.ALIGNMENT_INTERVAL)
              .setPeers(true).setPeersOverlap(false).setSaturated(false)
              .setParentId("who").setParentIncludes(true))
      .with("layer_id", 11).with("scope", "M"),
      
      (Layer)(new Layer("utterance", "Utterances").setAlignment(Constants.ALIGNMENT_INTERVAL)
              .setPeers(true).setPeersOverlap(false).setSaturated(true)
              .setParentId("turn").setParentIncludes(true))
      .with("layer_id", 12).with("scope", "M"),
      
      (Layer)(new Layer("language", "Other Language").setAlignment(Constants.ALIGNMENT_INTERVAL)
              .setPeers(true).setPeersOverlap(false).setSaturated(false)
              .setParentId("turn").setParentIncludes(true))
      .with("layer_id", 20).with("scope", "M"),
      
      (Layer)(new Layer("word", "Words").setAlignment(Constants.ALIGNMENT_INTERVAL)
              .setPeers(true).setPeersOverlap(false).setSaturated(false)
              .setParentId("turn").setParentIncludes(true))
      .with("layer_id", 0).with("scope", "W"),
      
      (Layer)(new Layer("orthography", "Orthography").setAlignment(Constants.ALIGNMENT_NONE)
              .setPeers(false).setPeersOverlap(false).setSaturated(false)
              .setParentId("word").setParentIncludes(true))
      .with("layer_id", 2).with("scope", "W"),
      
      (Layer)(new Layer("segment", "Phones").setAlignment(Constants.ALIGNMENT_INTERVAL)
              .setPeers(true).setPeersOverlap(false).setSaturated(true)
              .setParentId("word").setParentIncludes(true))
      .with("layer_id", 1).with("scope", "S"),
      
      (Layer)(new Layer("pronounce", "Pronounce").setAlignment(Constants.ALIGNMENT_NONE)
              .setPeers(false).setPeersOverlap(false).setSaturated(true)
              .setParentId("word").setParentIncludes(true))
      .with("layer_id", 23).with("scope", "W"))

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
                 +" 'word' AS layer"
                 +" FROM annotation_layer_0 annotation"
                 +" WHERE annotation.annotation_id = 456"
                 +" ORDER BY ag_id, annotation.parent_id, annotation.ordinal, annotation.annotation_id",
                 q.sql);

    q = transformer.sqlFor(
      "['ew_2_456', 'ew_2_789', 'ew_2_101112'].includes(id)",
      "DISTINCT annotation.*, graph.transcript_id AS graph", null, "LIMIT 1,1");
    assertEquals("SQL - id IN",
                 "SELECT DISTINCT annotation.*, graph.transcript_id AS graph,"
                 +" 'orthography' AS layer"
                 +" FROM annotation_layer_2 annotation"
                 +" INNER JOIN transcript graph ON annotation.ag_id = graph.ag_id"
                 +" WHERE annotation.annotation_id"
                 +" IN (456,789,101112)"
                 +" ORDER BY graph.transcript_id, annotation.parent_id, annotation.ordinal, annotation.annotation_id LIMIT 1,1",
                 q.sql);
    
    q = transformer.sqlFor(
      "['ew_2_456', 'ew_2_789', 'ew_2_101112'].includes(id)",
      "COUNT(*)", null, null);
    assertEquals("SQL - id IN",
                 "SELECT COUNT(*),"
                 +" 'orthography' AS layer"
                 +" FROM annotation_layer_2 annotation"
                 +" WHERE annotation.annotation_id"
                 +" IN (456,789,101112)",
                 q.sql);
    
    q = transformer.sqlFor(
      "['ew_2_456'].includes(id)",
      "COUNT(*)", null, null);
    assertEquals("SQL - works when therre's only one item",
                 "SELECT COUNT(*),"
                 +" 'orthography' AS layer"
                 +" FROM annotation_layer_2 annotation"
                 +" WHERE annotation.annotation_id"
                 +" IN (456)",
                 q.sql);
    
  }

  /** Ensure that identifying by a temporal layer works */
  @Test public void temporalLayerId() throws AGQLException {
    AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
    AnnotationAgqlToSql.Query q = transformer.sqlFor(
      "layer.id == 'orthography'",
      "DISTINCT annotation.*", null, null);
    assertEquals("SQL - layer.id ==",
                 "SELECT DISTINCT annotation.*,"
                 +" 'orthography' AS layer"
                 +" FROM annotation_layer_2 annotation"
                 +" WHERE 'orthography' = 'orthography'"
                 +" ORDER BY ag_id, annotation.parent_id, annotation.ordinal, annotation.annotation_id",
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
                 +" ORDER BY graph.transcript_id, annotation.parent_id, annotation.ordinal, annotation.annotation_id LIMIT 1,1",
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
                 +" ORDER BY graph.transcript_id, annotation.parent_id, annotation.ordinal, annotation.annotation_id LIMIT 1,1",
                 q.sql);
  }
  
  /** Ensure that identifying by a participant attribute layer ID works */
  @Test public void participantLayerId() throws AGQLException {
    AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
    AnnotationAgqlToSql.Query q = transformer.sqlFor(
      "layer.id == 'participant_gender'",
      "DISTINCT annotation.*", null, null);
    assertEquals(
      "SQL - layer.id ==",
      "SELECT DISTINCT annotation.label AS label, annotation.annotation_id AS annotation_id,"
      +" annotation.label_status AS label_status, 0 AS ordinal,"
      +" NULL AS start_anchor_id, NULL AS end_anchor_id,"
      +" annotation.annotated_by AS annotated_by,"
      +" annotation.annotated_when AS annotated_when,"
      +" 'participant_gender' AS layer"
      +" FROM annotation_participant annotation"
      +" INNER JOIN speaker ON annotation.speaker_number = speaker.speaker_number"
      +" INNER JOIN transcript_speaker"
      +" ON transcript_speaker.speaker_number = speaker.speaker_number"
      +" INNER JOIN transcript graph ON transcript_speaker.ag_id = graph.ag_id"
      +" WHERE annotation.layer = 'gender'"
      +" AND 'participant_gender' = 'participant_gender'"
      +" ORDER BY graph.transcript_id, annotation.annotation_id",
      q.sql);
      
    q = transformer.sqlFor(
      "graph.id == 'AdaAicheson-01.trs' && layerId == 'participant_gender'",
      "DISTINCT annotation.*", null, null);
    assertEquals(
      "SQL - graph.id and layerId ==",
      "SELECT DISTINCT annotation.label AS label, annotation.annotation_id AS annotation_id,"
      +" annotation.label_status AS label_status, 0 AS ordinal,"
      +" NULL AS start_anchor_id, NULL AS end_anchor_id,"
      +" annotation.annotated_by AS annotated_by,"
      +" annotation.annotated_when AS annotated_when,"
      +" 'participant_gender' AS layer"
      +" FROM annotation_participant annotation"
      +" INNER JOIN speaker ON annotation.speaker_number = speaker.speaker_number"
      +" INNER JOIN transcript_speaker"
      +" ON transcript_speaker.speaker_number = speaker.speaker_number"
      +" INNER JOIN transcript graph ON transcript_speaker.ag_id = graph.ag_id"
      +" WHERE annotation.layer = 'gender'"
      +" AND graph.transcript_id = 'AdaAicheson-01.trs'"
      +" AND 'participant_gender' = 'participant_gender'"
      +" ORDER BY graph.transcript_id, annotation.annotation_id",
      q.sql);

    q = transformer.sqlFor(
      "layer.id == 'participant_gender'",
      "DISTINCT annotation.label", null, null);
    assertEquals(
      "SQL - DISTINCT label",
      "SELECT DISTINCT annotation.label,"
      +" 'participant_gender' AS layer"
      +" FROM annotation_participant annotation"
      +" INNER JOIN speaker ON annotation.speaker_number = speaker.speaker_number"
      +" INNER JOIN transcript_speaker"
      +" ON transcript_speaker.speaker_number = speaker.speaker_number"
      +" INNER JOIN transcript graph ON transcript_speaker.ag_id = graph.ag_id"
      +" WHERE annotation.layer = 'gender'"
      +" AND 'participant_gender' = 'participant_gender'"
      +" ORDER BY graph.transcript_id, annotation.annotation_id",
      q.sql);
  }

  /** Ensure that identifying by a transcript attribute layer ID works */
  @Test public void transcriptAttributeLayerId() throws AGQLException {
    AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
    AnnotationAgqlToSql.Query q = transformer.sqlFor(
      "layer.id == 'transcript_language'",
      "DISTINCT annotation.*", null, null);
    assertEquals(
      "SQL - layer.id ==",
      "SELECT DISTINCT annotation.label AS label, annotation.annotation_id AS annotation_id,"
      +" annotation.label_status AS label_status, 0 AS ordinal,"
      +" NULL AS start_anchor_id, NULL AS end_anchor_id,"
      +" annotation.annotated_by AS annotated_by,"
      +" annotation.annotated_when AS annotated_when,"
      +" 'transcript_language' AS layer"
      +" FROM annotation_transcript annotation"
      +" INNER JOIN transcript graph ON annotation.ag_id = graph.ag_id"
      +" WHERE annotation.layer = 'language'"
      +" AND 'transcript_language' = 'transcript_language'"
      +" ORDER BY graph.transcript_id, annotation.annotation_id",
      q.sql);
      
    q = transformer.sqlFor(
      "graph.id == 'AdaAicheson-01.trs' && layerId == 'transcript_language'",
      "DISTINCT annotation.*", null, null);
    assertEquals(
      "SQL - graph.id and layerId ==",
      "SELECT DISTINCT annotation.label AS label, annotation.annotation_id AS annotation_id,"
      +" annotation.label_status AS label_status, 0 AS ordinal,"
      +" NULL AS start_anchor_id, NULL AS end_anchor_id,"
      +" annotation.annotated_by AS annotated_by,"
      +" annotation.annotated_when AS annotated_when,"
      +" 'transcript_language' AS layer"
      +" FROM annotation_transcript annotation"
      +" INNER JOIN transcript graph ON annotation.ag_id = graph.ag_id"
      +" WHERE annotation.layer = 'language'"
      +" AND graph.transcript_id = 'AdaAicheson-01.trs'"
      +" AND 'transcript_language' = 'transcript_language'"
      +" ORDER BY graph.transcript_id, annotation.annotation_id",
      q.sql);

    q = transformer.sqlFor(
      "layer.id == 'transcript_language'",
      "DISTINCT annotation.label", null, null);
    assertEquals(
      "SQL - DISTINCT label",
      "SELECT DISTINCT annotation.label,"
      +" 'transcript_language' AS layer"
      +" FROM annotation_transcript annotation"
      +" INNER JOIN transcript graph ON annotation.ag_id = graph.ag_id"
      +" WHERE annotation.layer = 'language'"
      +" AND 'transcript_language' = 'transcript_language'"
      +" ORDER BY graph.transcript_id, annotation.annotation_id",
      q.sql);
  }

  @Test public void parentId() throws AGQLException {
    AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
    AnnotationAgqlToSql.Query q = transformer.sqlFor(
      "layer.id == 'orthography' AND parent.id = 'ew_0_123'",
      "DISTINCT annotation.*", null, null);
    assertEquals("SQL - layer.id ==",
                 "SELECT DISTINCT annotation.*,"
                 +" 'orthography' AS layer"
                 +" FROM annotation_layer_2 annotation"
                 +" WHERE 'orthography' = 'orthography'"
                 +" AND annotation.parent_id = 123"
                 +" ORDER BY ag_id, annotation.parent_id, annotation.ordinal, annotation.annotation_id",
                 q.sql);
      
    q = transformer.sqlFor(
      "layerId == 'orthography' && parentId = 'ew_0_123'",
      "DISTINCT annotation.*, graph.transcript_id AS graph", null, "LIMIT 1,1");
    assertEquals("SQL - layer.id ==",
                 "SELECT DISTINCT annotation.*, graph.transcript_id AS graph,"
                 +" 'orthography' AS layer"
                 +" FROM annotation_layer_2 annotation"
                 +" INNER JOIN transcript graph ON annotation.ag_id = graph.ag_id"
                 +" WHERE 'orthography' = 'orthography'"
                 +" AND annotation.parent_id = 123"
                 +" ORDER BY graph.transcript_id, annotation.parent_id, annotation.ordinal, annotation.annotation_id"
                 +" LIMIT 1,1",
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

  @Test public void inAll() throws AGQLException {
    AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
    AnnotationAgqlToSql.Query q = transformer.sqlFor(
      "layer.id == 'utterance' && all('word').includes('ew_0_456')",
      "DISTINCT annotation.*", null, null);
    assertEquals("SQL",
                 "SELECT DISTINCT annotation.*, 'utterance' AS layer, start.offset, end.offset"
                 +" FROM annotation_layer_12 annotation"
                 +" INNER JOIN anchor start ON annotation.start_anchor_id = start.anchor_id"
                 +" INNER JOIN anchor end ON annotation.end_anchor_id = end.anchor_id"
                 +" WHERE 'utterance' = 'utterance'"
                 +" AND 456 IN"
                 +" (SELECT otherLayer.annotation_id"
                 +" FROM annotation_layer_0 otherLayer"
                 +" INNER JOIN anchor otherLayer_start"
                 +" ON otherLayer.start_anchor_id = otherLayer_start.anchor_id"
                 +" AND otherLayer_start.offset <= end.offset"
                 +" INNER JOIN anchor otherLayer_end"
                 +" ON otherLayer.end_anchor_id = otherLayer_end.anchor_id"
                 +" AND start.offset <= otherLayer_end.offset"
                 +" WHERE otherLayer.ag_id = annotation.ag_id"
                 +" AND otherLayer.turn_annotation_id = annotation.turn_annotation_id)"
                 +" ORDER BY ag_id, start.offset, end.offset, annotation.parent_id, annotation.ordinal, annotation.annotation_id",
                 q.sql);
  }


  @Test public void annotationLabels() throws AGQLException {
    AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
    AnnotationAgqlToSql.Query q = transformer.sqlFor(
      "layer.id == 'orthography' && label = 'test'",
      "DISTINCT annotation.*", null, null);
    assertEquals("single =",
                 "SELECT DISTINCT annotation.*, 'orthography' AS layer"
                 +" FROM annotation_layer_2 annotation"
                 +" WHERE 'orthography' = 'orthography'"
                 +" AND annotation.label = 'test'"
                 +" ORDER BY ag_id, annotation.parent_id, annotation.ordinal, annotation.annotation_id",
                 q.sql);
    
    q = transformer.sqlFor(
      "layer.id == 'orthography' && label == 'test'",
      "DISTINCT annotation.*", null, null);
    assertEquals("double ==",
                 "SELECT DISTINCT annotation.*, 'orthography' AS layer"
                 +" FROM annotation_layer_2 annotation"
                 +" WHERE 'orthography' = 'orthography'"
                 +" AND annotation.label = 'test'"
                 +" ORDER BY ag_id, annotation.parent_id, annotation.ordinal, annotation.annotation_id",
                 q.sql);
    
    q = transformer.sqlFor(
      "layer.id == 'orthography' && label === 'test'",
      "DISTINCT annotation.*", null, null);
    assertEquals("triple === - BINARY comparison",
                 "SELECT DISTINCT annotation.*, 'orthography' AS layer"
                 +" FROM annotation_layer_2 annotation"
                 +" WHERE 'orthography' = 'orthography'"
                 +" AND annotation.label = BINARY 'test'"
                 +" ORDER BY ag_id, annotation.parent_id, annotation.ordinal, annotation.annotation_id",
                 q.sql);
  }
  
  @Test public void whoLabels() throws AGQLException {
    AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
    AnnotationAgqlToSql.Query q = transformer.sqlFor(
      "layer.id == 'orthography' && first('who').label == 'Robert'",
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
                 +" ORDER BY ag_id, annotation.parent_id, annotation.ordinal, annotation.annotation_id",
                 q.sql);
  }

  @Test public void episodeLabel() throws AGQLException {
    AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
    AnnotationAgqlToSql.Query q = transformer.sqlFor(
      "layer.id == 'orthography' && first('who').label == 'Robert'"
      +" && first(\"episode\").label == 'some-episode'",
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
                 +" ORDER BY graph.transcript_id, annotation.parent_id, annotation.ordinal, annotation.annotation_id",
                 q.sql);
  }

  @Test public void transcriptTypeLabel() throws AGQLException {
    AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
    AnnotationAgqlToSql.Query q = transformer.sqlFor(
      "layer.id == 'utterance' && first('who').label == 'Robert'"
      +" && first('transcript_type').label == 'wordlist'",
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
                 +" ORDER BY graph.transcript_id, annotation.parent_id, annotation.ordinal, annotation.annotation_id",
                 q.sql);
  }

  @Test public void startOffset() throws AGQLException {
    AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
    AnnotationAgqlToSql.Query q = transformer.sqlFor(
      "layer.id == 'orthography' && first('who').label == 'Robert'"
      +" && start.offset < 12.345",
      "DISTINCT annotation.*", null, null);
    assertEquals("Who",
                 "SELECT DISTINCT annotation.*, 'orthography' AS layer, start.offset, end.offset"
                 +" FROM annotation_layer_2 annotation"
                 +" INNER JOIN anchor start ON annotation.start_anchor_id = start.anchor_id"
                 +" INNER JOIN anchor end ON annotation.end_anchor_id = end.anchor_id"
                 +" WHERE 'orthography' = 'orthography'"
                 +" AND (SELECT speaker.name"
                 +" FROM speaker INNER JOIN annotation_layer_11 turn"
                 +" ON speaker.speaker_number = turn.label"
                 +" WHERE turn.annotation_id = annotation.turn_annotation_id) = 'Robert'"
                 +" AND start.offset < 12.345"
                 +" ORDER BY ag_id, start.offset, end.offset, annotation.parent_id, annotation.ordinal, annotation.annotation_id",
                 q.sql);

    // q = transformer.sqlFor( TODO
    //    "layer.id == 'orthography' && first('who').label == 'Robert'"
    //    +" && first('utterances').start.offset = 12.345",
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
    //              +" ORDER BY ag_id, annotation.parent_id, annotation.annotation_id",
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
                 +" ORDER BY graph.transcript_id, annotation.parent_id, annotation.ordinal, annotation.annotation_id",
                 q.sql);

    q = transformer.sqlFor(
      "graph.id == 'Ada\\'Aicheson-01.trs' && layer.id == 'orthography'",
      "DISTINCT annotation.*", null, null);
    assertEquals("Graph ID with apostrophe",
                 "SELECT DISTINCT annotation.*, 'orthography' AS layer"
                 +" FROM annotation_layer_2 annotation"
                 +" INNER JOIN transcript graph ON annotation.ag_id = graph.ag_id"
                 +" WHERE graph.transcript_id = 'Ada\\'Aicheson-01.trs'"
                 +" AND 'orthography' = 'orthography'"
                 +" ORDER BY graph.transcript_id, annotation.parent_id, annotation.ordinal, annotation.annotation_id",
                 q.sql);
  }

  @Test public void graphAnnotationsByLayer() throws AGQLException {
    AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
    AnnotationAgqlToSql.Query q = transformer.sqlFor(
      "graph.id == 'AdaAicheson-01.trs' && layer.id == 'who'",
      "DISTINCT annotation.*", null, null);
    assertEquals("Participant ",
                 "SELECT DISTINCT"
                 +" annotation.name AS label, annotation.speaker_number AS annotation_id,"
                 +" 100 AS label_status, 0 AS ordinal,"
                 +" NULL AS start_anchor_id, NULL AS end_anchor_id,"
                 +" NULL AS annotated_by, NULL AS annotated_when,"
                 +" 'who' AS layer"
                 +" FROM transcript_speaker"
                 +" INNER JOIN transcript graph"
                 +" ON transcript_speaker.ag_id = graph.ag_id"
                 +" INNER JOIN speaker annotation"
                 +" ON transcript_speaker.speaker_number = annotation.speaker_number"
                 +" WHERE graph.transcript_id = 'AdaAicheson-01.trs'"
                 +" AND 'who' = 'who'"
                 +" ORDER BY graph.transcript_id, annotation.name",
                 q.sql);

    q = transformer.sqlFor(
      "graph.id == 'AdaAicheson-01.trs' && layer.id == 'main_participant'",
      "DISTINCT annotation.*", null, null);
    assertEquals("Participant ",
                 "SELECT DISTINCT"
                 +" annotation.name AS label, annotation.speaker_number AS annotation_id,"
                 +" 100 AS label_status, 0 AS ordinal,"
                 +" NULL AS start_anchor_id, NULL AS end_anchor_id,"
                 +" NULL AS annotated_by, NULL AS annotated_when,"
                 +" 'main_participant' AS layer"
                 +" FROM transcript_speaker"
                 +" INNER JOIN transcript graph"
                 +" ON transcript_speaker.ag_id = graph.ag_id"
                 +" INNER JOIN speaker annotation"
                 +" ON transcript_speaker.speaker_number = annotation.speaker_number"
                 +" WHERE graph.transcript_id = 'AdaAicheson-01.trs'"
                 +" AND 'main_participant' = 'main_participant'"
                 +" AND main_speaker <> 0"
                 +" ORDER BY graph.transcript_id, annotation.name",
                 q.sql);
  }

  @Test public void labels() throws AGQLException {
    AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
    AnnotationAgqlToSql.Query q = transformer.sqlFor(
      "layerId = 'utterance' && labels('orthography').includes('foo')",
      "DISTINCT annotation.*", null, null);
    assertEquals("words in utterance",
                 "SELECT DISTINCT annotation.*, 'utterance' AS layer, start.offset, end.offset"
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
                 +" ORDER BY ag_id, start.offset, end.offset, annotation.parent_id, annotation.ordinal, annotation.annotation_id",
                 q.sql);

    q = transformer.sqlFor(
      "layerId = 'word' && first('language').label = 'mi'",
      "DISTINCT annotation.*", null, null);
    assertEquals("language of word",
                 "SELECT DISTINCT annotation.*, 'word' AS layer, start.offset, end.offset"
                 +" FROM annotation_layer_0 annotation"
                 +" INNER JOIN anchor start ON annotation.start_anchor_id = start.anchor_id"
                 +" INNER JOIN anchor end ON annotation.end_anchor_id = end.anchor_id"
                 +" LEFT OUTER JOIN (annotation_layer_20 otherLayer_0"
                 +" INNER JOIN anchor otherLayer_start_0"
                 +" ON otherLayer_0.start_anchor_id = otherLayer_start_0.anchor_id"
                 +" INNER JOIN anchor otherLayer_end_0"
                 +" ON otherLayer_0.end_anchor_id = otherLayer_end_0.anchor_id)"
                 +" ON otherLayer_0.ag_id = annotation.ag_id"
                 +" AND otherLayer_0.turn_annotation_id = annotation.turn_annotation_id"
                 +" AND otherLayer_start_0.offset < end.offset"
                 +" AND start.offset < otherLayer_end_0.offset"
                 +" WHERE 'word' = 'word'"
                 +" AND otherLayer_0.label = 'mi'"
                 +" ORDER BY ag_id, start.offset, end.offset, annotation.parent_id, annotation.ordinal, annotation.annotation_id",
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
                 +" ORDER BY ag_id, annotation.parent_id, annotation.ordinal, annotation.annotation_id",
                 q.sql);
    
    q = transformer.sqlFor(
      "layerId = 'utterance' && labels('who').includesAny(['Ada','Interviewer'])",
      "DISTINCT annotation.*", null, null);
    assertEquals("Transcript attribute - SQL",
                 "SELECT DISTINCT annotation.*, 'utterance' AS layer"
                 +" FROM annotation_layer_12 annotation"
                 +" WHERE 'utterance' = 'utterance'"
                 +" AND ('Ada' IN (SELECT speaker.name"
                 +" FROM speaker"
                 +" INNER JOIN annotation_layer_11 turn ON speaker.speaker_number = turn.label"
                 +" WHERE turn.annotation_id = annotation.turn_annotation_id)"
                 +" OR 'Interviewer' IN (SELECT speaker.name"
                 +" FROM speaker"
                 +" INNER JOIN annotation_layer_11 turn ON speaker.speaker_number = turn.label"
                 +" WHERE turn.annotation_id = annotation.turn_annotation_id))"
                 +" ORDER BY ag_id, annotation.parent_id, annotation.ordinal, annotation.annotation_id",
                 q.sql);

  }

  @Test public void attributeLabel() throws AGQLException {
    AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
    AnnotationAgqlToSql.Query q = transformer.sqlFor(
      "layerId = 'utterance' && first('transcript_scribe').label == 'someone'",
      "DISTINCT annotation.*", null, null);
    assertEquals("Transcript attribute - SQL",
                 "SELECT DISTINCT annotation.*, 'utterance' AS layer"
                 +" FROM annotation_layer_12 annotation"
                 +" WHERE 'utterance' = 'utterance'"
                 +" AND (SELECT label FROM annotation_transcript USE INDEX(IDX_AG_ID_NAME)"
                 +" WHERE annotation_transcript.layer = 'scribe'"
                 +" AND annotation_transcript.ag_id = annotation.ag_id LIMIT 1) = 'someone'"
                 +" ORDER BY ag_id, annotation.parent_id, annotation.ordinal, annotation.annotation_id",
                 q.sql);

    // TODO add support for participant attributs
    // q = transformer.sqlFor(
    //    "layerId = 'utterance' && first('participant_gender').label == 'NA'",
    //    "DISTINCT annotation.*", null, null);
    // assertEquals("Participant attribute - SQL",
    //              "SELECT DISTINCT annotation.* FROM transcript"
    //              +" WHERE"
    //              +" (SELECT label"
    //              +" FROM annotation_participant"
    //              +" INNER JOIN transcript_speaker"
    //              +" ON annotation_participant.speaker_number = transcript_speaker.speaker_number"
    //              +" AND annotation_participant.layer = 'gender'"
    //              +" WHERE transcript_speaker.ag_id = transcript.ag_id"
    //              +" ORDER BY annotation_id LIMIT 1) = 'NA'"
    //              +" ORDER BY DISTINCT annotation.*",
    //              q.sql);
  }

  @Test public void languageLabel() throws AGQLException {
    AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
    AnnotationAgqlToSql.Query q = transformer.sqlFor(
      "layerId = 'utterance' && first('transcript_language').label == 'en'",
      "DISTINCT annotation.*", null, null);
    assertEquals("Transcript attribute - SQL",
                 "SELECT DISTINCT annotation.*, 'utterance' AS layer"
                 +" FROM annotation_layer_12 annotation"
                 +" INNER JOIN transcript graph ON annotation.ag_id = graph.ag_id"
                 +" WHERE 'utterance' = 'utterance'"
                 +" AND COALESCE("
                 +"(SELECT label FROM annotation_transcript USE INDEX(IDX_AG_ID_NAME)"
                 +" WHERE annotation_transcript.layer = 'language' AND label <> ''"
                 +" AND annotation_transcript.ag_id = annotation.ag_id LIMIT 1),"
                 +"(SELECT corpus_language FROM corpus"
                 +" WHERE corpus.corpus_name = graph.corpus_name)"
                 +") = 'en'"
                 +" ORDER BY graph.transcript_id, annotation.parent_id, annotation.ordinal, annotation.annotation_id",
                 q.sql);
  }

  /** Test SQL generated by matching phrase language, falling back to transcript language */
  @Test public void cascadingLanguageMatch() throws AGQLException {
    AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
    AnnotationAgqlToSql.Query q = transformer.sqlFor(
      "layerId = 'word' && /en.*/.test("
      +"first('language').label ?? first('transcript_language').label)",
      "DISTINCT annotation.*", null, null);
    assertEquals("phrase language falls back to transcript language",
                 "SELECT DISTINCT annotation.*, 'word' AS layer, start.offset, end.offset"
                 +" FROM annotation_layer_0 annotation"
                 +" INNER JOIN transcript graph ON annotation.ag_id = graph.ag_id"
                 +" INNER JOIN anchor start ON annotation.start_anchor_id = start.anchor_id"
                 +" INNER JOIN anchor end ON annotation.end_anchor_id = end.anchor_id"
                 +" LEFT OUTER JOIN (annotation_layer_20 otherLayer_0"
                 +" INNER JOIN anchor otherLayer_start_0"
                 +" ON otherLayer_0.start_anchor_id = otherLayer_start_0.anchor_id"
                 +" INNER JOIN anchor otherLayer_end_0"
                 +" ON otherLayer_0.end_anchor_id = otherLayer_end_0.anchor_id)"
                 +" ON otherLayer_0.ag_id = annotation.ag_id"
                 +" AND otherLayer_0.turn_annotation_id = annotation.turn_annotation_id"
                 +" AND otherLayer_start_0.offset < end.offset"
                 +" AND start.offset < otherLayer_end_0.offset"
                 +" WHERE 'word' = 'word'"
                 +" AND COALESCE(otherLayer_0.label,"
                 +" COALESCE("
                 +"(SELECT label FROM annotation_transcript USE INDEX(IDX_AG_ID_NAME)"
                 +" WHERE annotation_transcript.layer = 'language' AND label <> ''"
                 +" AND annotation_transcript.ag_id = annotation.ag_id LIMIT 1),"
                 +"(SELECT corpus_language FROM corpus"
                 +" WHERE corpus.corpus_name = graph.corpus_name)"
                 +")) REGEXP 'en.*'"
                 +" ORDER BY graph.transcript_id, start.offset, end.offset,"
                 +" annotation.parent_id, annotation.ordinal, annotation.annotation_id",
                 q.sql);
  }
  @Test public void listLength() throws AGQLException {
    AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
    AnnotationAgqlToSql.Query q = transformer.sqlFor(
      "layerId = 'utterance' && all('transcript_rating').length > 10",
      "DISTINCT annotation.*", null, null);
    assertEquals("Transcript attribute - SQL",
                 "SELECT DISTINCT annotation.*, 'utterance' AS layer"
                 +" FROM annotation_layer_12 annotation"
                 +" WHERE 'utterance' = 'utterance' AND"
                 +" (SELECT COUNT(*) FROM annotation_transcript USE INDEX(IDX_AG_ID_NAME)"
                 +" WHERE annotation_transcript.layer = 'rating'"
                 +" AND annotation_transcript.ag_id = annotation.ag_id) > 10"
                 +" ORDER BY ag_id, annotation.parent_id, annotation.ordinal, annotation.annotation_id",
                 q.sql);
      
    q = transformer.sqlFor(
      "layerId = 'utterance' && all('word').length > 100",
      "DISTINCT annotation.*", null, null);
    assertEquals("Annotation - SQL",
                 "SELECT DISTINCT annotation.*, 'utterance' AS layer, start.offset, end.offset"
                 +" FROM annotation_layer_12 annotation"
                 +" INNER JOIN anchor start ON annotation.start_anchor_id = start.anchor_id"
                 +" INNER JOIN anchor end ON annotation.end_anchor_id = end.anchor_id"
                 +" WHERE 'utterance' = 'utterance'"
                 +" AND (SELECT COUNT(*) FROM annotation_layer_0 otherLayer"
                 +" INNER JOIN anchor otherLayer_start"
                 +" ON otherLayer.start_anchor_id = otherLayer_start.anchor_id"
                 +" INNER JOIN anchor otherLayer_end"
                 +" ON otherLayer.end_anchor_id = otherLayer_end.anchor_id"
                 +" WHERE otherLayer.ag_id = annotation.ag_id"
                 +" AND otherLayer.turn_annotation_id = annotation.turn_annotation_id"
                 +" AND otherLayer_start.offset <= end.offset"
                 +" AND start.offset <= otherLayer_end.offset) > 100"
                 +" ORDER BY ag_id, start.offset, end.offset, annotation.parent_id, annotation.ordinal, annotation.annotation_id",
                 q.sql);
  }

  @Test public void annotators() throws AGQLException {
    AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
    AnnotationAgqlToSql.Query q = transformer.sqlFor(
      "layerId = 'utterance' && annotators('transcript_rating').includes('someone')",
      "DISTINCT annotation.*", null, null);
    assertEquals("Transcript attribute - SQL",
                 "SELECT DISTINCT annotation.*, 'utterance' AS layer"
                 +" FROM annotation_layer_12 annotation"
                 +" WHERE 'utterance' = 'utterance'"
                 +" AND 'someone' IN (SELECT annotated_by"
                 +" FROM annotation_transcript USE INDEX(IDX_AG_ID_NAME)"
                 +" WHERE annotation_transcript.layer = 'rating'"
                 +" AND annotation_transcript.ag_id = annotation.ag_id)"
                 +" ORDER BY ag_id, annotation.parent_id, annotation.ordinal, annotation.annotation_id",
                 q.sql);

    //    q = transformer.sqlFor(
    //       "annotators('participant_gender').includes('someone')",
    //       "DISTINCT annotation.*", null, null, null);
    //    assertEquals("Transcript attribute - SQL",
    //                 "SELECT DISTINCT annotation.* FROM transcript"
    //                 +" WHERE 'someone' IN"
    //                 +" (SELECT DISTINCT annotated_by"
    //                 +" FROM annotation_participant"
    //                 +" INNER JOIN transcript_speaker"
    //                 +" ON annotation_participant.speaker_number = transcript_speaker.speaker_number"
    //                 +" AND annotation_participant.layer = 'gender'"
    //                 +" WHERE transcript_speaker.ag_id = transcript.ag_id)"
    //                 +" ORDER BY DISTINCT annotation.*",
    //                 q.sql);
    
    q = transformer.sqlFor(
      "layerId = 'utterance' && annotators('noise').includes('someone')",
      "DISTINCT annotation.*", null, null);
    assertEquals("Annotation - SQL",
                 "SELECT DISTINCT annotation.*, 'utterance' AS layer, start.offset, end.offset"
                 +" FROM annotation_layer_12 annotation"
                 +" INNER JOIN anchor start ON annotation.start_anchor_id = start.anchor_id"
                 +" INNER JOIN anchor end ON annotation.end_anchor_id = end.anchor_id"
                 +" WHERE 'utterance' = 'utterance'"
                 +" AND 'someone' IN (SELECT annotated_by"
                 +" FROM annotation_layer_32 otherLayer"
                 +" INNER JOIN anchor otherLayer_start"
                 +" ON otherLayer.start_anchor_id = otherLayer_start.anchor_id"
                 +" AND otherLayer_start.offset <= end.offset"
                 +" INNER JOIN anchor otherLayer_end"
                 +" ON otherLayer.end_anchor_id = otherLayer_end.anchor_id"
                 +" AND start.offset <= otherLayer_end.offset"
                 +" WHERE otherLayer.ag_id = annotation.ag_id)"
                 +" ORDER BY ag_id, start.offset, end.offset, annotation.parent_id, annotation.ordinal, annotation.annotation_id",
                 q.sql);

    q = transformer.sqlFor(
      "layerId = 'word' && annotators('language').includes('someone')",
      "DISTINCT annotation.*", null, null);
    assertEquals("Annotation - SQL",
                 "SELECT DISTINCT annotation.*, 'word' AS layer, start.offset, end.offset"
                 +" FROM annotation_layer_0 annotation"
                 +" INNER JOIN anchor start ON annotation.start_anchor_id = start.anchor_id"
                 +" INNER JOIN anchor end ON annotation.end_anchor_id = end.anchor_id"
                 +" WHERE 'word' = 'word'"
                 +" AND 'someone' IN (SELECT annotated_by"
                 +" FROM annotation_layer_20 otherLayer"
                 +" INNER JOIN anchor otherLayer_start"
                 +" ON otherLayer.start_anchor_id = otherLayer_start.anchor_id"
                 +" AND otherLayer_start.offset <= end.offset"
                 +" INNER JOIN anchor otherLayer_end"
                 +" ON otherLayer.end_anchor_id = otherLayer_end.anchor_id"
                 +" AND start.offset <= otherLayer_end.offset"
                 +" WHERE otherLayer.ag_id = annotation.ag_id"
                 +" AND otherLayer.turn_annotation_id = annotation.turn_annotation_id)"
                 +" ORDER BY ag_id, start.offset, end.offset, annotation.parent_id, annotation.ordinal, annotation.annotation_id",
                 q.sql);
  }
   
  @Test public void invalidLayers() throws AGQLException {
    AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
    try {
      AnnotationAgqlToSql.Query q = transformer.sqlFor(
        "layerId = 'utterance' && first('invalid layer 1').label == 'NA'"
        + " AND all('invalid layer 2').length > 2"
        + " AND first('invalid layer 3').label = 'NA'"
        + " AND 'labbcat' NOT IN annotators('invalid layer 4')",
        "DISTINCT annotation.*", null, null);
      fail("sqlFor fails: " + q.sql);
    } catch(AGQLException exception) {
      assertEquals("Number of errors: " + exception.getErrors(), 4, exception.getErrors().size());
    }
  }

  @Test public void userWhereClause() throws AGQLException {
    AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
    AnnotationAgqlToSql.Query q = transformer.sqlFor(
      "id == 'ew_0_456'",
      "DISTINCT annotation.*, graph.ag_id",
      "(EXISTS (SELECT * FROM role"
      + " INNER JOIN role_permission ON role.role_id = role_permission.role_id" 
      + " INNER JOIN annotation_transcript access_attribute" 
      + " ON access_attribute.layer = role_permission.attribute_name" 
      + " AND access_attribute.label REGEXP role_permission.value_pattern"
      + " AND role_permission.entity REGEXP '.*t.*'"
      + " WHERE user_id = 'test'"
      + " AND access_attribute.ag_id = graph.ag_id)"
      + " OR EXISTS (SELECT * FROM role"
      + " INNER JOIN role_permission ON role.role_id = role_permission.role_id" 
      + " AND role_permission.attribute_name = 'corpus'" 
      + " AND role_permission.entity REGEXP '.*t.*'"
      + " WHERE graph.corpus_name REGEXP role_permission.value_pattern"
      + " AND user_id = 'test')"
      + " OR NOT EXISTS (SELECT * FROM role_permission))",
      null);
    assertEquals(
      "SQL - label",
      "SELECT DISTINCT annotation.*, graph.ag_id, 'word' AS layer"
      +" FROM annotation_layer_0 annotation"
      +" INNER JOIN transcript graph ON annotation.ag_id = graph.ag_id"
      +" WHERE annotation.annotation_id = 456"
      +" AND ("
      +"EXISTS (SELECT * FROM role"
      +" INNER JOIN role_permission ON role.role_id = role_permission.role_id"
      +" INNER JOIN annotation_transcript access_attribute"
      +" ON access_attribute.layer = role_permission.attribute_name"
      +" AND access_attribute.label REGEXP role_permission.value_pattern"
      +" AND role_permission.entity REGEXP '.*t.*'"
      +" WHERE user_id = 'test'"
      +" AND access_attribute.ag_id = graph.ag_id)"
      +" OR EXISTS (SELECT * FROM role"
      +" INNER JOIN role_permission ON role.role_id = role_permission.role_id"
      +" AND role_permission.attribute_name = 'corpus'"
      +" AND role_permission.entity REGEXP '.*t.*'"
      +" WHERE graph.corpus_name REGEXP role_permission.value_pattern"
      +" AND user_id = 'test')"
      +" OR NOT EXISTS (SELECT * FROM role_permission))"
      +" ORDER BY graph.transcript_id, annotation.parent_id, annotation.ordinal, annotation.annotation_id",
      q.sql);
    assertEquals("Parameter count - label", 0, q.parameters.size());
  }

  @Test public void confidence() throws AGQLException {
    AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
    AnnotationAgqlToSql.Query q = transformer.sqlFor(
      "layer.id == 'segment' AND confidence >= 100",
      "COUNT(*)", null, null);
    assertEquals("SQL - layer.id ==",
                 "SELECT COUNT(*),"
                 +" 'segment' AS layer"
                 +" FROM annotation_layer_1 annotation"
                 +" WHERE 'segment' = 'segment'"
                 +" AND annotation.label_status >= 100",
                 q.sql);
  }

  @Test public void anchorConfidence() throws AGQLException {
    AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
    AnnotationAgqlToSql.Query q = transformer.sqlFor(
      "layer.id == 'segment' AND start.confidence >= 100",
      "COUNT(*)", null, null);
    assertEquals("SQL - layer.id ==",
                 "SELECT COUNT(*),"
                 +" 'segment' AS layer"
                 +" FROM annotation_layer_1 annotation"
                 +" INNER JOIN anchor start ON annotation.start_anchor_id = start.anchor_id"
                 +" INNER JOIN anchor end ON annotation.end_anchor_id = end.anchor_id"
                 +" WHERE 'segment' = 'segment'"
                 +" AND start.alignment_status >= 100",
                 q.sql);

    q = transformer.sqlFor(
      "layer.id == 'segment' AND end.confidence == 50",
      "COUNT(*)", null, null);
    assertEquals("SQL - layer.id ==",
                 "SELECT COUNT(*),"
                 +" 'segment' AS layer"
                 +" FROM annotation_layer_1 annotation"
                 +" INNER JOIN anchor start ON annotation.start_anchor_id = start.anchor_id"
                 +" INNER JOIN anchor end ON annotation.end_anchor_id = end.anchor_id"
                 +" WHERE 'segment' = 'segment'"
                 +" AND end.alignment_status = 50",
                 q.sql);
  }

  public static void main(String args[])  {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.db.test.TestAnnotationAgqlToSql");
  }

}
