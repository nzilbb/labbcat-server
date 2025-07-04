//
// Copyright 2019-2021 New Zealand Institute of Language, Brain and Behaviour, 
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
import nzilbb.ag.Schema;
import nzilbb.ag.Layer;
import nzilbb.ag.ql.AGQLException;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

public class TestParticipantAgqlToSql {
  
  /**
   * Return a plausible schema, including SQL attributes.
   * @return A test schema.
   */
  public Schema getSchema() {
    return new Schema(
      "who", "turn", "utterance", "transcript",
            
      (Layer)(new Layer("transcript_language", "Language").setAlignment(Constants.ALIGNMENT_NONE)
              .setPeers(false).setPeersOverlap(false).setSaturated(true))
      .with("class_id", "transcript").with("attribute", "language"),
      
      (Layer)(new Layer("transcript_scribe", "Scribe").setAlignment(Constants.ALIGNMENT_NONE)
              .setPeers(false).setPeersOverlap(false).setSaturated(true))
      .with("class_id", "transcript").with("attribute", "scribe"),
      
      (Layer)(new Layer("transcript_rating", "Ratings").setAlignment(Constants.ALIGNMENT_NONE)
              .setPeers(true).setPeersOverlap(true).setSaturated(true))
      .with("class_id", "transcript").with("attribute", "rating"),
      
      (Layer)(new Layer("corpus", "Corpus").setAlignment(Constants.ALIGNMENT_NONE)
              .setPeers(false).setPeersOverlap(false).setSaturated(true))
      .with("layer_id", -100),
      
      (Layer)(new Layer("episode", "Episode").setAlignment(Constants.ALIGNMENT_NONE)
              .setPeers(false).setPeersOverlap(false).setSaturated(true))
      .with("layer_id", -50),
      
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
      
      (Layer)(new Layer("participant_languages", "Languages").setAlignment(Constants.ALIGNMENT_NONE)
              .setPeers(true).setPeersOverlap(true).setSaturated(true).setParentId("who"))
      .with("class_id", "speaker").with("attribute", "languages"),
      
      (Layer)(new Layer("comment", "Comment").setAlignment(Constants.ALIGNMENT_INTERVAL)
              .setPeers(true).setPeersOverlap(false).setSaturated(false))
      .with("layer_id", 31),
      
      (Layer)(new Layer("noise", "Noise")
              .setAlignment(2).setPeers(true).setPeersOverlap(false).setSaturated(false))
      .with("layer_id", 32),
      
      (Layer)(new Layer("turn", "Speaker turns").setAlignment(Constants.ALIGNMENT_INTERVAL)
              .setPeers(true).setPeersOverlap(false).setSaturated(false)
              .setParentId("who").setParentIncludes(true))
      .with("layer_id", 11),
      
      (Layer)(new Layer("utterance", "Utterances").setAlignment(Constants.ALIGNMENT_INTERVAL)
              .setPeers(true).setPeersOverlap(false).setSaturated(true)
              .setParentId("turn").setParentIncludes(true))
      .with("layer_id", 12),
      
      (Layer)(new Layer("transcript", "Words").setAlignment(Constants.ALIGNMENT_INTERVAL)
              .setPeers(true).setPeersOverlap(false).setSaturated(false)
              .setParentId("turn").setParentIncludes(true))
      .with("layer_id", 0),
      
      (Layer)(new Layer("orthography", "Orthography").setAlignment(Constants.ALIGNMENT_NONE)
              .setPeers(false).setPeersOverlap(false).setSaturated(false)
              .setParentId("word").setParentIncludes(true))
      .with("layer_id", 2),
      
      (Layer)(new Layer("segments", "Phones").setAlignment(Constants.ALIGNMENT_INTERVAL)
              .setPeers(true).setPeersOverlap(false).setSaturated(true)
              .setParentId("word").setParentIncludes(true))
      .with("layer_id", 1),
      
      (Layer)(new Layer("pronounce", "Pronounce").setAlignment(Constants.ALIGNMENT_NONE)
              .setPeers(false).setPeersOverlap(false).setSaturated(true)
              .setParentId("word").setParentIncludes(true))
      .with("layer_id", 23))

      .setEpisodeLayerId("episode")
      .setCorpusLayerId("corpus");
  } // end of getSchema()

  @Test public void idMatch() throws AGQLException {
    ParticipantAgqlToSql transformer = new ParticipantAgqlToSql(getSchema());
    ParticipantAgqlToSql.Query q = transformer.sqlFor(
      "/Ada.+/.test(label)", "speaker_number, name", null, false, "ORDER BY speaker.name");
    assertEquals("SQL - label",
                 "SELECT speaker_number, name FROM speaker"
                 +" WHERE speaker.name REGEXP 'Ada.+' ORDER BY speaker.name",
                 q.sql);
    assertEquals("Parameter count - label", 0, q.parameters.size());

    q = transformer.sqlFor(
      "!/Ada.+/.test(id)", "speaker_number, name", null, false, "ORDER BY speaker.name");
    assertEquals("SQL - id",
                 "SELECT speaker_number, name FROM speaker"
                 +" WHERE speaker.name NOT REGEXP 'Ada.+' ORDER BY speaker.name",
                 q.sql);
    assertEquals("Parameter count - id", 0, q.parameters.size());
  }

  /** SQL injection tests */
  @Test public void sqlInjection() throws AGQLException {
    ParticipantAgqlToSql transformer = new ParticipantAgqlToSql(getSchema());
    ParticipantAgqlToSql.Query q = transformer.sqlFor(
      "/' 1\\'' union select @@version -- /.test(id)",
      "speaker_number, name", null, false, "ORDER BY speaker.name");
    assertEquals("SQL - bad quote escaping eith REGEXP incl. initial quote",
                 "SELECT speaker_number, name FROM speaker"
                 +" WHERE speaker.name REGEXP '\\' 1\\'\\' union select @@version -- '"
                 +" ORDER BY speaker.name",
                 q.sql);

    q = transformer.sqlFor(
      "/\\\\' union select @@version -- /.test(id)",
      "speaker_number, name", null, false, "ORDER BY speaker.name");
    assertEquals("SQL - bad backslash escaping with REGEXP",
                 "SELECT speaker_number, name FROM speaker"
                 +" WHERE speaker.name REGEXP '\\\\\\' union select @@version -- '"
                 +" ORDER BY speaker.name",
                 q.sql);

    q = transformer.sqlFor(
      "id == '1\\'' union select @@version -- '",
      "speaker_number, name", null, false, "ORDER BY speaker.name");
    assertEquals("SQL - bad quote escaping with ==",
                 "SELECT speaker_number, name FROM speaker"
                 +" WHERE speaker.name = '1\\''"
                 +" ORDER BY speaker.name",
                 q.sql);

    q = transformer.sqlFor(
      "/1'' union select table_name from information_schema.tables where table_schema=database() -- /.test(id)",
      "speaker_number, name", null, false, "ORDER BY speaker.name");
    assertEquals("SQL - bad escaping with REGEXP - not initial quote",
                 "SELECT speaker_number, name FROM speaker"
                 +" WHERE speaker.name REGEXP '1\\'\\' union select table_name from information_schema.tables where table_schema=database() -- '"
                 +" ORDER BY speaker.name",
                 q.sql);
  }

  @Test public void emptyExpression() throws AGQLException {
    ParticipantAgqlToSql transformer = new ParticipantAgqlToSql(getSchema());
    ParticipantAgqlToSql.Query q = transformer.sqlFor(
      "", "speaker_number, name", null, false, "ORDER BY speaker.name");
    assertEquals("SQL - no userWhere",
                 "SELECT speaker_number, name FROM speaker ORDER BY speaker.name",
                 q.sql);
    assertEquals("Parameter count - no userWhere", 0, q.parameters.size());

    q = transformer.sqlFor(
      "", "speaker_number, name", "speaker.annotated_by = 'user'", false,
      "ORDER BY speaker.name");
    assertEquals("SQL - id",
                 "SELECT speaker_number, name FROM speaker"
                 +" WHERE speaker.annotated_by = 'user' ORDER BY speaker.name",
                 q.sql);
    assertEquals("Parameter count - id", 0, q.parameters.size());
  }

  @Test public void corpusLabel() throws AGQLException {
    ParticipantAgqlToSql transformer = new ParticipantAgqlToSql(getSchema());
    ParticipantAgqlToSql.Query q = transformer.sqlFor(
      "first(\"corpus\").label == \"CC\"", "speaker_number, name", null, false, "ORDER BY speaker.name");
    assertEquals("SQL",
                 "SELECT speaker_number, name FROM speaker"
                 +" WHERE (SELECT corpus.corpus_name"
                 +" FROM speaker_corpus"
                 +" INNER JOIN corpus ON speaker_corpus.corpus_id = corpus.corpus_id"
                 +" WHERE speaker_corpus.speaker_number = speaker.speaker_number LIMIT 1)"
                 +" = 'CC'"
                 +" ORDER BY speaker.name",
                 q.sql);
    assertEquals("Parameter count", 0, q.parameters.size());
  }

  @Test public void literalAll() throws AGQLException {
    ParticipantAgqlToSql transformer = new ParticipantAgqlToSql(getSchema());
    ParticipantAgqlToSql.Query q = transformer.sqlFor(
      "[\"CC\", 'IA', 'MU', \"graph\", 'who'].includes(first(\"corpus\").label)",
      "speaker_number, name", null, false, "ORDER BY speaker.name");
    assertEquals("SQL",
                 "SELECT speaker_number, name FROM speaker"
                 +" WHERE (SELECT corpus.corpus_name"
                 +" FROM speaker_corpus"
                 +" INNER JOIN corpus ON speaker_corpus.corpus_id = corpus.corpus_id"
                 +" WHERE speaker_corpus.speaker_number = speaker.speaker_number LIMIT 1)"
                 +" IN ('CC','IA','MU','graph','who')"
                 +" ORDER BY speaker.name",
                 q.sql);
    assertEquals("Parameter count", 0, q.parameters.size());
  }

  @Test public void corpusLabels() throws AGQLException {
    ParticipantAgqlToSql transformer = new ParticipantAgqlToSql(getSchema());
    ParticipantAgqlToSql.Query q = transformer.sqlFor(
      "labels('corpus').includes('CC')", "speaker_number, name", null, false, "ORDER BY speaker.name");
    assertEquals("SQL",
                 "SELECT speaker_number, name FROM speaker"
                 +" WHERE 'CC' IN (SELECT corpus.corpus_name"
                 +" FROM speaker_corpus"
                 +" INNER JOIN corpus ON speaker_corpus.corpus_id = corpus.corpus_id"
                 +" WHERE speaker_corpus.speaker_number = speaker.speaker_number)"
                 +" ORDER BY speaker.name",
                 q.sql);
    assertEquals("Parameter count", 0, q.parameters.size());

    q = transformer.sqlFor(
      "['CC','IA'].includesAny(labels('corpus'))", "speaker_number, name", null, false, "ORDER BY speaker.name");
    assertEquals("SQL",
                 "SELECT speaker_number, name FROM speaker"
                 +" WHERE ('CC' IN (SELECT corpus.corpus_name"
                 +" FROM speaker_corpus"
                 +" INNER JOIN corpus ON speaker_corpus.corpus_id = corpus.corpus_id"
                 +" WHERE speaker_corpus.speaker_number = speaker.speaker_number)"
                 +" OR 'IA' IN (SELECT corpus.corpus_name"
                 +" FROM speaker_corpus"
                 +" INNER JOIN corpus ON speaker_corpus.corpus_id = corpus.corpus_id"
                 +" WHERE speaker_corpus.speaker_number = speaker.speaker_number))"
                 +" ORDER BY speaker.name",
                 q.sql);
    assertEquals("Parameter count", 0, q.parameters.size());
  }

  @Test public void listLength() throws AGQLException {
    ParticipantAgqlToSql transformer = new ParticipantAgqlToSql(getSchema());
    ParticipantAgqlToSql.Query q = transformer.sqlFor(
      "all('transcript_rating').length > 2", "speaker_number, name", null, false, "ORDER BY speaker.name");
    assertEquals("Transcript attribute - list - SQL",
                 "SELECT speaker_number, name FROM speaker"
                 +" WHERE (SELECT COUNT(*)"
                 +" FROM annotation_transcript"
                 +" INNER JOIN transcript_speaker"
                 +" ON annotation_transcript.ag_id = transcript_speaker.ag_id"
                 +" WHERE annotation_transcript.layer = 'rating'"
                 +" AND transcript_speaker.speaker_number = speaker.speaker_number)"
                 +" > 2"
                 +" ORDER BY speaker.name",
                 q.sql);
    assertEquals("Parameter count", 0, q.parameters.size());

    q = transformer.sqlFor(
      "all('participant_gender').length = 0", "speaker_number, name", null, false, "ORDER BY speaker.name");
    assertEquals("Participant attribute - list - SQL",
                 "SELECT speaker_number, name FROM speaker"
                 +" WHERE (SELECT COUNT(*)"
                 +" FROM annotation_participant"
                 +" WHERE annotation_participant.layer = 'gender'"
                 +" AND annotation_participant.speaker_number = speaker.speaker_number)"
                 +" = 0"
                 +" ORDER BY speaker.name",
                 q.sql);
    assertEquals("Parameter count", 0, q.parameters.size());
    
    q = transformer.sqlFor(
      "labels('transcript_rating').length > 2", "speaker_number, name", null, false, "ORDER BY speaker.name");
    assertEquals("Transcript attribute - labels - SQL",
                 "SELECT speaker_number, name FROM speaker"
                 +" WHERE (SELECT COUNT(*)"
                 +" FROM annotation_transcript"
                 +" INNER JOIN transcript_speaker"
                 +" ON annotation_transcript.ag_id = transcript_speaker.ag_id"
                 +" WHERE annotation_transcript.layer = 'rating'"
                 +" AND transcript_speaker.speaker_number = speaker.speaker_number)"
                 +" > 2"
                 +" ORDER BY speaker.name",
                 q.sql);
    assertEquals("Parameter count", 0, q.parameters.size());
    
    q = transformer.sqlFor(
      "labels('participant_gender').length == 0", "speaker_number, name", null, false, "ORDER BY speaker.name");
    assertEquals("Participant attribute - labels - SQL",
                 "SELECT speaker_number, name FROM speaker"
                 +" WHERE (SELECT COUNT(*)"
                 +" FROM annotation_participant"
                 +" WHERE annotation_participant.layer = 'gender'"
                 +" AND annotation_participant.speaker_number = speaker.speaker_number)"
                 +" = 0"
                 +" ORDER BY speaker.name",
                 q.sql);
    assertEquals("Parameter count", 0, q.parameters.size());
  }

  @Test public void labels() throws AGQLException {
    ParticipantAgqlToSql transformer = new ParticipantAgqlToSql(getSchema());
    ParticipantAgqlToSql.Query q = transformer.sqlFor(
      "labels('transcript_language').includes('en')", "speaker_number, name", null, false, "ORDER BY speaker.name");
    assertEquals("Transcript attribute - SQL",
                 "SELECT speaker_number, name FROM speaker"
                 +" WHERE 'en' IN (SELECT DISTINCT label"
                 +" FROM annotation_transcript"
                 +" INNER JOIN transcript_speaker"
                 +" ON annotation_transcript.ag_id = transcript_speaker.ag_id"
                 +" WHERE annotation_transcript.layer = 'language'"
                 +" AND transcript_speaker.speaker_number = speaker.speaker_number)"
                 +" ORDER BY speaker.name",
                 q.sql);
    assertEquals("Parameter count", 0, q.parameters.size());

    q = transformer.sqlFor(
      "labels('participant_gender').includes('NA')", "speaker_number, name", null, false, "ORDER BY speaker.name");
    assertEquals("Participant attribute - SQL",
                 "SELECT speaker_number, name FROM speaker"
                 +" WHERE 'NA' IN (SELECT DISTINCT label"
                 +" FROM annotation_participant"
                 +" WHERE annotation_participant.layer = 'gender'"
                 +" AND annotation_participant.speaker_number = speaker.speaker_number)"
                 +" ORDER BY speaker.name",
                 q.sql);
    assertEquals("Parameter count", 0, q.parameters.size());
    
    q = transformer.sqlFor(
      "labels('transcript').includes('test.eaf')", "speaker_number, name", null, false, "ORDER BY speaker.name");
    assertEquals("Participant attribute - SQL",
                 "SELECT speaker_number, name FROM speaker"
                 +" WHERE 'test.eaf' IN (SELECT DISTINCT transcript_id"
                 +" FROM transcript_speaker"
                 +" INNER JOIN transcript ON transcript_speaker.ag_id = transcript.ag_id"
                 +" WHERE transcript_speaker.speaker_number = speaker.speaker_number"
                 +")"
                 +" ORDER BY speaker.name",
                 q.sql);
    assertEquals("Parameter count", 0, q.parameters.size());
  }

  @Test public void labelsWithIncludesAny() throws AGQLException {
    ParticipantAgqlToSql transformer = new ParticipantAgqlToSql(getSchema());
    ParticipantAgqlToSql.Query q = transformer.sqlFor(
      "[\"en\",\"es\"].includesAny(labels('participant_languages'))", "speaker_number, name", null, false, "ORDER BY speaker.name");
    assertEquals("Participant attribute (double quotes, literal first) - SQL",
                 "SELECT speaker_number, name FROM speaker"
                 +" WHERE ('en' IN (SELECT DISTINCT label"
                 +" FROM annotation_participant"
                 +" WHERE annotation_participant.layer = 'languages'"
                 +" AND annotation_participant.speaker_number = speaker.speaker_number)"
                 +" OR 'es' IN (SELECT DISTINCT label"
                 +" FROM annotation_participant"
                 +" WHERE annotation_participant.layer = 'languages'"
                 +" AND annotation_participant.speaker_number = speaker.speaker_number))"
                 +" ORDER BY speaker.name",
                 q.sql);
    assertEquals("Parameter count", 0, q.parameters.size());

    q = transformer.sqlFor(
      "!labels('participant_languages').includesAny(['en','es'])", "speaker_number, name", null, false, "ORDER BY speaker.name");
    assertEquals("Participant attribute (single quotes, literal second) - SQL",
                 "SELECT speaker_number, name FROM speaker"
                 +" WHERE ('en' NOT IN (SELECT DISTINCT label"
                 +" FROM annotation_participant"
                 +" WHERE annotation_participant.layer = 'languages'"
                 +" AND annotation_participant.speaker_number = speaker.speaker_number)"
                 +" AND 'es' NOT IN (SELECT DISTINCT label"
                 +" FROM annotation_participant"
                 +" WHERE annotation_participant.layer = 'languages'"
                 +" AND annotation_participant.speaker_number = speaker.speaker_number))"
                 +" ORDER BY speaker.name",
                 q.sql);
    assertEquals("Parameter count", 0, q.parameters.size());
  }

  @Test public void participantAttributeLabel() throws AGQLException {
    ParticipantAgqlToSql transformer = new ParticipantAgqlToSql(getSchema());
    ParticipantAgqlToSql.Query q = transformer.sqlFor(
      "first('participant_gender').label == 'NA'", "speaker_number, name", null, false, "ORDER BY speaker.name");
    assertEquals("SQL",
                 "SELECT speaker_number, name FROM speaker"
                 +" WHERE (SELECT label"
                 +" FROM annotation_participant"
                 +" WHERE annotation_participant.layer = 'gender'"
                 +" AND annotation_participant.speaker_number = speaker.speaker_number LIMIT 1)"
                 +" = 'NA'"
                 +" ORDER BY speaker.name",
                 q.sql);
    assertEquals("Parameter count", 0, q.parameters.size());
  }

  @Test public void transcriptCount() throws AGQLException {
    ParticipantAgqlToSql transformer = new ParticipantAgqlToSql(getSchema());
    ParticipantAgqlToSql.Query q = transformer.sqlFor(
      "all('transcript').length == 0", "speaker_number, name", null, false, "ORDER BY speaker.name");
    assertEquals("SQL",
                 "SELECT speaker_number, name FROM speaker"
                 +" WHERE (SELECT COUNT(*)"
                 +" FROM transcript_speaker"
                 +" WHERE transcript_speaker.speaker_number = speaker.speaker_number)"
                 +" = 0"
                 +" ORDER BY speaker.name",
                 q.sql);
    assertEquals("Parameter count", 0, q.parameters.size());
  }

  @Test public void episodeMatch() throws AGQLException {
    ParticipantAgqlToSql transformer = new ParticipantAgqlToSql(getSchema());
    ParticipantAgqlToSql.Query q = transformer.sqlFor(
      "/Ada.+/.test(first('episode').label)", "speaker_number, name", null, false, "ORDER BY speaker.name");
    assertEquals("SQL",
                 "SELECT speaker_number, name FROM speaker"
                 +" WHERE (SELECT DISTINCT transcript_family.name"
                 +" FROM transcript_family"
                 +" INNER JOIN transcript"
                 +" ON transcript_family.family_id = transcript.family_id"
                 +" INNER JOIN transcript_speaker"
                 +" ON transcript.ag_id = transcript_speaker.ag_id"
                 +" WHERE transcript_speaker.speaker_number = speaker.speaker_number"
                 +" ORDER BY transcript_family.name LIMIT 1)"
                 +" REGEXP 'Ada.+'"
                 +" ORDER BY speaker.name",
                 q.sql);
    assertEquals("Parameter count", 0, q.parameters.size());
      
    q = transformer.sqlFor(         
      // RegExp.test(Array) is a kludge that allows matching *any* of the annotation layers
      "/Ada.+/.test(labels('episode'))", "speaker_number, name", null, false, "ORDER BY speaker.name");
    assertEquals("SQL",
                 "SELECT speaker_number, name FROM speaker"
                 +" WHERE (EXISTS (SELECT *"
                 +" FROM transcript_family"
                 +" INNER JOIN transcript ON transcript_family.family_id = transcript.family_id"
                 +" INNER JOIN transcript_speaker ON transcript.ag_id = transcript_speaker.ag_id"
                 +" WHERE transcript_speaker.speaker_number = speaker.speaker_number"
                 +" AND transcript_family.name REGEXP 'Ada.+'))"
                 +" ORDER BY speaker.name",
                 q.sql);
    assertEquals("Parameter count", 0, q.parameters.size());
  }

  @Test public void annotators() throws AGQLException {
    ParticipantAgqlToSql transformer = new ParticipantAgqlToSql(getSchema());
    ParticipantAgqlToSql.Query q = transformer.sqlFor(
      "!annotators('transcript_rating').includes('labbcat')", "speaker_number, name", null, false, "ORDER BY speaker.name");
    assertEquals("Transcript Attribute - SQL",
                 "SELECT speaker_number, name FROM speaker"
                 +" WHERE 'labbcat' NOT IN (SELECT annotated_by"
                 +" FROM annotation_transcript"
                 +" INNER JOIN transcript_speaker"
                 +" ON annotation_transcript.ag_id = transcript_speaker.ag_id"
                 +" WHERE annotation_transcript.layer = 'rating'"
                 +" AND transcript_speaker.speaker_number = speaker.speaker_number)"
                 +" ORDER BY speaker.name",
                 q.sql);
    assertEquals("Transcript Attribute - Parameter count", 0, q.parameters.size());

    q = transformer.sqlFor(
      "!annotators('participant_gender').includes('labbcat')", "speaker_number, name", null, false, "ORDER BY speaker.name");
    assertEquals("Participant Attribute - SQL",
                 "SELECT speaker_number, name FROM speaker"
                 +" WHERE 'labbcat' NOT IN (SELECT annotated_by"
                 +" FROM annotation_participant"
                 +" WHERE annotation_participant.layer = 'gender'"
                 +" AND annotation_participant.speaker_number = speaker.speaker_number)"
                 +" ORDER BY speaker.name",
                 q.sql);
    assertEquals("Participant Attribute - Parameter count", 0, q.parameters.size());
  }
  
  @Test public void invalidLayers() throws AGQLException {
    ParticipantAgqlToSql transformer = new ParticipantAgqlToSql(getSchema());
    try {
      ParticipantAgqlToSql.Query q = transformer.sqlFor(
        "first('invalid layer 1').label == 'NA'"
        + " AND all('invalid layer 2').length > 2"
        + " AND first('invalid layer 3').label = 'NA'"
        + " AND 'labbcat' NOT IN annotators('invalid layer 4')",
        "speaker_number, name", null, false, "ORDER BY speaker.name");
      fail("sqlFor fails: " + q.sql);
    } catch(AGQLException exception) {
      assertEquals("Number of errors: " + exception.getErrors(), 4, exception.getErrors().size());
    }
  }

  @Test public void userWhereClause() throws AGQLException {
    ParticipantAgqlToSql transformer = new ParticipantAgqlToSql(getSchema());
    ParticipantAgqlToSql.Query q = transformer.sqlFor(
      "/Ada.+/.test(label)", "speaker_number, name",
      "(EXISTS (SELECT * FROM role"
      + " INNER JOIN role_permission ON role.role_id = role_permission.role_id" 
      + " INNER JOIN annotation_transcript access_attribute" 
      + " ON access_attribute.layer = role_permission.attribute_name" 
      + " AND access_attribute.label REGEXP role_permission.value_pattern"
      + " AND role_permission.entity REGEXP '.*t.*'"
      + " INNER JOIN transcript_speaker ON access_attribute.ag_id = transcript_speaker.ag_id"
      + " WHERE transcript_speaker.speaker_number = speaker.speaker_number"
      + " AND user_id = 'test')"
      + " OR EXISTS (SELECT * FROM role"
      + " INNER JOIN role_permission ON role.role_id = role_permission.role_id" 
      + " AND role_permission.attribute_name = 'corpus'" 
      + " AND role_permission.entity REGEXP '.*t.*'"
      + " INNER JOIN transcript_speaker"
      + " INNER JOIN transcript ON transcript_speaker.ag_id = transcript.ag_id"
      + " WHERE transcript_speaker.speaker_number = speaker.speaker_number"
      + " AND transcript.corpus_name REGEXP role_permission.value_pattern"
      + " AND INNER JOIN transcript_speaker ON access_attribute.ag_id = transcript_speaker.ag_id"
      + " WHERE transcript_speaker.speaker_number = speaker.speaker_number"
      + " AND user_id = 'label')"
      + " OR NOT EXISTS (SELECT * FROM role_permission)"
      + " OR NOT EXISTS (SELECT * FROM transcript_speaker"
      + " WHERE transcript_speaker.speaker_number = speaker.speaker_number))", false,
      "ORDER BY speaker.name");
    assertEquals(
      "SQL - label",
      "SELECT speaker_number, name FROM speaker"
      + " WHERE speaker.name REGEXP 'Ada.+'"
      + " AND (EXISTS (SELECT * FROM role"
      + " INNER JOIN role_permission ON role.role_id = role_permission.role_id" 
      + " INNER JOIN annotation_transcript access_attribute" 
      + " ON access_attribute.layer = role_permission.attribute_name" 
      + " AND access_attribute.label REGEXP role_permission.value_pattern"
      + " AND role_permission.entity REGEXP '.*t.*'"
      + " INNER JOIN transcript_speaker ON access_attribute.ag_id = transcript_speaker.ag_id"
      + " WHERE transcript_speaker.speaker_number = speaker.speaker_number"
      + " AND user_id = 'test')"
      + " OR EXISTS (SELECT * FROM role"
      + " INNER JOIN role_permission ON role.role_id = role_permission.role_id" 
      + " AND role_permission.attribute_name = 'corpus'" 
      + " AND role_permission.entity REGEXP '.*t.*'"
      + " INNER JOIN transcript_speaker"
      + " INNER JOIN transcript ON transcript_speaker.ag_id = transcript.ag_id"
      + " WHERE transcript_speaker.speaker_number = speaker.speaker_number"
      + " AND transcript.corpus_name REGEXP role_permission.value_pattern"
      + " AND INNER JOIN transcript_speaker ON access_attribute.ag_id = transcript_speaker.ag_id"
      + " WHERE transcript_speaker.speaker_number = speaker.speaker_number"
      + " AND user_id = 'label')"
      + " OR NOT EXISTS (SELECT * FROM role_permission)"
      + " OR NOT EXISTS (SELECT * FROM transcript_speaker"
      + " WHERE transcript_speaker.speaker_number = speaker.speaker_number))"
      + " ORDER BY speaker.name", 
      q.sql);
    assertEquals("Parameter count - label", 0, q.parameters.size());
  }

  public static void main(String args[])  {
    org.junit.runner.JUnitCore.main("nzilbb.ag.sql.test.TestParticipantAgqlToSql");
  }

}
