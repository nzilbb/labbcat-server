//
// Copyright 2021 New Zealand Institute of Language, Brain and Behaviour, 
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

import java.util.Vector;
import nzilbb.ag.Constants;
import nzilbb.ag.Layer;
import nzilbb.ag.Schema;
import nzilbb.labbcat.server.db.SqlConstants;
import nzilbb.labbcat.server.db.OneQuerySearch;
import nzilbb.labbcat.server.search.Column;
import nzilbb.labbcat.server.search.LayerMatch;
import nzilbb.labbcat.server.search.Matrix;

public class TestOneQuerySearch {

  /** Ensure the simplest search structure generates the correct SQL. */
  @Test public void oneWordSearch() throws Exception {    
    OneQuerySearch search = new OneQuerySearch();
    search.setMatrix(
      new Matrix().addColumn(
        new Column().addLayerMatch(
          new LayerMatch().setId("word").setPattern("needle"))));
    Vector<Object> parameters = new Vector<Object>();
    String sql = search.generateSql(parameters, getSchema(), l -> false, () -> "", () -> "");
    assertEquals(
      "INSERT INTO _result"
      +" (search_id, ag_id, speaker_number, start_anchor_id, end_anchor_id,"
      +" defining_annotation_id, segment_annotation_id, target_annotation_id, turn_annotation_id,"
      +" first_matched_word_annotation_id, last_matched_word_annotation_id, complete,"
      +" target_annotation_uid) SELECT ?, search_0_0.ag_id AS ag_id,"
      +" CAST(turn.label AS SIGNED) AS speaker_number, search_0_0.start_anchor_id,"
      +" search_0_0.end_anchor_id,0, NULL AS segment_annotation_id,"
      +" search_0_0.word_annotation_id AS target_annotation_id,"
      +" search_0_0.turn_annotation_id AS turn_annotation_id,"
      +" search_0_0.word_annotation_id AS first_matched_word_annotation_id,"
      +" search_0_0.word_annotation_id AS last_matched_word_annotation_id, 0 AS complete,"
      +" CONCAT('ew_0_', search_0_0.word_annotation_id) AS target_annotation_uid"
      +" FROM annotation_layer_11 turn"
      +" /* extra joins */"
      +"  INNER JOIN annotation_layer_0 search_0_0"
      +"  ON search_0_0.turn_annotation_id = turn.annotation_id"
      +"  AND search_0_0.label  REGEXP  ?"
      +" /* subsequent columns */"
      +"  WHERE 1=1"
      +" /* transcript type */"
      +"  /* who */"
      +"  /* main speaker clause */"
      +"  /* access clause */"
      +"  /* first column: */"
      +" /* border conditions */"
      +"  /* search criteria subqueries */"
      +"  /* subsequent columns */"
      +"  ORDER BY search_0_0.turn_annotation_id, search_0_0.ordinal_in_turn",
      sql);
    assertEquals("number of parameters" + parameters, 1, parameters.size());
    assertEquals("^(needle)$", parameters.get(0));
    assertTrue(parameters.get(0) instanceof String);
  }
  
  /** Ensure a one-column search with multiple word layers generates the correct SQL. */
  @Test public void multiWordLayerSearch() throws Exception {    
    OneQuerySearch search = new OneQuerySearch();
    search.setMatrix(
      new Matrix().addColumn(
        new Column()
        .addLayerMatch(new LayerMatch()
                       .setId("phonemes").setPattern("[^cCEFHiIPqQuUV0123456789~#\\{\\$@].*"))
        .addLayerMatch(new LayerMatch()
                       .setId("orthography").setPattern("[aeiou].*"))
        ));
    Vector<Object> parameters = new Vector<Object>();
    String sql = search.generateSql(parameters, getSchema(), l -> false, () -> "", () -> "");
    assertEquals(
      "INSERT INTO _result"
      +" (search_id, ag_id, speaker_number, start_anchor_id, end_anchor_id,"
      +" defining_annotation_id, segment_annotation_id, target_annotation_id, turn_annotation_id,"
      +" first_matched_word_annotation_id, last_matched_word_annotation_id, complete,"
      +" target_annotation_uid) SELECT ?, search_0_52.ag_id AS ag_id,"
      +" CAST(turn.label AS SIGNED) AS speaker_number, search_0_52.start_anchor_id,"
      +" search_0_52.end_anchor_id,0, NULL AS segment_annotation_id,"
      +" search_0_52.word_annotation_id AS target_annotation_id,"
      +" search_0_52.turn_annotation_id AS turn_annotation_id,"
      +" search_0_52.word_annotation_id AS first_matched_word_annotation_id,"
      +" search_0_52.word_annotation_id AS last_matched_word_annotation_id, 0 AS complete,"
      +" CONCAT('ew_0_', search_0_52.word_annotation_id) AS target_annotation_uid"
      +" FROM annotation_layer_11 turn"
      +" /* extra joins */"
      +"  INNER JOIN annotation_layer_52 search_0_52"
      +"  ON search_0_52.turn_annotation_id = turn.annotation_id"
      +"  AND CAST(search_0_52.label AS BINARY)  REGEXP BINARY ?"
      +" INNER JOIN annotation_layer_2 search_0_2"
      +"  ON search_0_2.word_annotation_id = search_0_52.word_annotation_id"
      +"  AND search_0_2.label  REGEXP  ?"
      +" /* subsequent columns */"
      +"  WHERE 1=1"
      +" /* transcript type */"
      +"  /* who */"
      +"  /* main speaker clause */"
      +"  /* access clause */"
      +"  /* first column: */"
      +" /* border conditions */"
      +"  /* search criteria subqueries */"
      +"  /* subsequent columns */"
      +"  ORDER BY search_0_52.turn_annotation_id, search_0_52.ordinal_in_turn",
      sql);
    assertEquals("number of parameters" + parameters, 2, parameters.size());
    assertEquals("^([^cCEFHiIPqQuUV0123456789~#\\{\\$@].*)$", parameters.get(0));
    assertTrue(parameters.get(0) instanceof String);
    assertEquals("^([aeiou].*)$", parameters.get(1));
    assertTrue(parameters.get(1) instanceof String);
  }
  
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
      .with("layer_id", SqlConstants.LAYER_CORPUS),
      
      (Layer)(new Layer("episode", "Episode").setAlignment(Constants.ALIGNMENT_NONE)
              .setPeers(false).setPeersOverlap(false).setSaturated(true))
      .with("layer_id", SqlConstants.LAYER_SERIES),
      
      (Layer)(new Layer("recording_date", "Recording Date").setAlignment(Constants.ALIGNMENT_NONE)
              .setPeers(false).setPeersOverlap(false).setSaturated(true).setParentId("episode")),
      
      (Layer)(new Layer("who", "Participants").setAlignment(Constants.ALIGNMENT_NONE)
              .setPeers(true).setPeersOverlap(true).setSaturated(true))
      .with("layer_id", SqlConstants.LAYER_PARTICIPANT),
      
      (Layer)(new Layer("main_participant", "Main Participant").setAlignment(Constants.ALIGNMENT_NONE)
              .setPeers(true).setPeersOverlap(true).setSaturated(true).setParentId("who"))
      .with("layer_id", SqlConstants.LAYER_MAIN_PARTICIPANT),
      
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
      
      (Layer)(new Layer("topic", "Topic").setAlignment(Constants.ALIGNMENT_INTERVAL)
              .setPeers(true).setPeersOverlap(false).setSaturated(false))
      .with("layer_id", 33).with("scope", "F"),
      
      (Layer)(new Layer("turn", "Speaker turns").setAlignment(Constants.ALIGNMENT_INTERVAL)
              .setPeers(true).setPeersOverlap(false).setSaturated(false)
              .setParentId("who").setParentIncludes(true))
      .with("layer_id", SqlConstants.LAYER_TURN).with("scope", "M"),
      
      (Layer)(new Layer("utterance", "Utterances").setAlignment(Constants.ALIGNMENT_INTERVAL)
              .setPeers(true).setPeersOverlap(false).setSaturated(true)
              .setParentId("turn").setParentIncludes(true))
      .with("layer_id", SqlConstants.LAYER_UTTERANCE).with("scope", "M"),
      
      (Layer)(new Layer("language", "Other Language").setAlignment(Constants.ALIGNMENT_INTERVAL)
              .setPeers(true).setPeersOverlap(false).setSaturated(false)
              .setParentId("turn").setParentIncludes(true))
      .with("layer_id", 20).with("scope", "M"),
      
      (Layer)(new Layer("word", "Words").setAlignment(Constants.ALIGNMENT_INTERVAL)
              .setPeers(true).setPeersOverlap(false).setSaturated(false)
              .setParentId("turn").setParentIncludes(true))
      .with("layer_id", SqlConstants.LAYER_TRANSCRIPTION).with("scope", "W"),
      
      (Layer)(new Layer("orthography", "Orthography").setAlignment(Constants.ALIGNMENT_NONE)
              .setPeers(false).setPeersOverlap(false).setSaturated(true)
              .setParentId("word").setParentIncludes(true))
      .with("layer_id", SqlConstants.LAYER_ORTHOGRAPHY).with("scope", "W"),
      
      (Layer)(new Layer("phonemes", "Pronunciation").setAlignment(Constants.ALIGNMENT_NONE)
              .setPeers(true).setPeersOverlap(false).setSaturated(true)
              .setParentId("word").setParentIncludes(true)
              .setType(Constants.TYPE_IPA))
      .with("layer_id", 52).with("scope", "W"),
      
      (Layer)(new Layer("segment", "Phones").setAlignment(Constants.ALIGNMENT_INTERVAL)
              .setPeers(true).setPeersOverlap(false).setSaturated(true)
              .setParentId("word").setParentIncludes(true))
      .with("layer_id", SqlConstants.LAYER_SEGMENT).with("scope", "S"),
      
      (Layer)(new Layer("pronounce", "Pronounce").setAlignment(Constants.ALIGNMENT_NONE)
              .setPeers(false).setPeersOverlap(false).setSaturated(true)
              .setParentId("word").setParentIncludes(true))
      .with("layer_id", 23).with("scope", "W"))

      .setEpisodeLayerId("episode")
      .setCorpusLayerId("corpus");
  } // end of getSchema()

  public static void main(String args[])  {
    org.junit.runner.JUnitCore.main("nzilbb.labbcat.server.db.test.TestOneQuerySearch");
  }

}
