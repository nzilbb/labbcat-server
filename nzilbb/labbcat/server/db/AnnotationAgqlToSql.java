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
package nzilbb.labbcat.server.db;

import java.sql.*;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.List;
import java.util.Stack;
import java.util.Vector;
import java.util.function.UnaryOperator;
import nzilbb.ag.Layer;
import nzilbb.ag.Schema;
import nzilbb.ag.ql.*;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

/**
 * Converts AGQL expressions into SQL queries for matching graphs (transcripts).
 * @author Robert Fromont robert@fromont.net.nz
 */
@SuppressWarnings("serial")
public class AnnotationAgqlToSql {
   
   // Attributes:
   
   /**
    * Layer schema.
    * @see #getSchema()
    * @see #setSchema(Schema)
    */
   protected Schema schema;
   /**
    * Getter for {@link #schema}.
    * @return Layer schema.
    */
   public Schema getSchema() { return schema; }
   /**
    * Setter for {@link #schema}.
    * @param schema Layer schema.
    * @return <var>this</var>.
    */
   public AnnotationAgqlToSql setSchema(Schema schema) { this.schema = schema; return this; }
  
   // Methods:
  
   /**
    * Default constructor.
    */
   public AnnotationAgqlToSql() {
   } // end of constructor
  
   /**
    * Attribute constructor.
    */
   public AnnotationAgqlToSql(Schema schema) {
      setSchema(schema);
   } // end of constructor
  
   /**
    * Transforms the given AGQL query into an SQL query.
    * @param expression The graph-matching expression, for example:
    * <ul>
    *  <li><code>id == 'ew_0_456'</code></li>
    *  <li><code>layer.id == 'orthography' &amp;&amp; /th[aeiou].+/.test(label)</code></li>
    *  <li><code>layer.id == 'orthography' &amp;&amp; my('participant').label == 'Robert' &amp;&amp;
    * my('utterances').start.offset == 12.345</code></li> 
    *  <li><code>graph.id == 'AdaAicheson-01.trs' &amp;&amp; layer.id == 'orthography' &amp;&amp;
    * start.offset &gt; 10.5</code></li> 
    *  <li><code>layer.id == 'utterances' &amp;&amp; list('transcript').includes('ew_0_456')</code></li>
    *  <li><code>previous.id == 'ew_0_456'</code></li>
    * </ul>
    * Also currently supported are the legacy SQL-style expressions:
    * <ul>
    *  <li><code>id = 'ew_0_456'</code></li>
    *  <li><code>layer.id = 'orthography' AND label NOT MATCHES 'th[aeiou].*'</code></li>
    *  <li><code>layer.id = 'orthography' AND my('participant').label = 'Robert' AND
    * my('utterances').start.offset = 12.345</code></li> 
    *  <li><code>graph.id = 'AdaAicheson-01.trs' AND layer.id = 'orthography' AND
    * start.offset &gt; 10.5</code></li> 
    *  <li><code>layer.id = 'utterances' AND 'ew_0_456' IN list('transcript')</code></li>
    *  <li><code>previous.id = 'ew_0_456'</code></li>
    * </ul>
    * @param sqlSelectClause The SQL expression that is to go between SELECT and FROM.
    * @param userWhereClause The expression to add to the WHERE clause to ensure the user doesn't
    * get access to data to which they're not entitled, or null.
    * @param sqlLimitClause The SQL LIMIT clause to append, or null for no LIMIT clause. 
    * @throws AGQLException If the expression is invalid.
    */
   public Query sqlFor(String expression, String sqlSelectClause, String userWhereClause, String sqlLimitClause)
      throws AGQLException {

      if (expression == null || expression.trim().length() == 0)
      {
         throw new AGQLException("No expression specified");
      }

      Layer layer = deducePrimaryLayer(expression);
      if (layer == null) {
         throw new AGQLException("Could not identify primary layer")
            .setExpression(expression);
      }
      if (!layer.containsKey("@layer_id")) {
         throw new AGQLException("Primary layer is not a temporal layer: " + layer.getId())
            .setExpression(expression);
      }
      int iLayerId = ((Integer)layer.get("@layer_id")).intValue();
      switch (iLayerId)
      {
         case SqlConstants.LAYER_PARTICIPANT:
         case SqlConstants.LAYER_MAIN_PARTICIPANT: 
            return sqlForParticipantLayer(
               expression, sqlSelectClause, userWhereClause, sqlLimitClause, layer);
         case SqlConstants.LAYER_GRAPH: 
         case SqlConstants.LAYER_SERIES: 
         case SqlConstants.LAYER_CORPUS: 
            throw new AGQLException("Primary layer is an unsupported structural layer: " + layer.getId())
               .setExpression(expression);
         default:
            return sqlForTemporalLayer(
               expression, sqlSelectClause, userWhereClause, sqlLimitClause, layer);
      }
   } // sqlFor

   /**
    * Transforms the given AGQL query for a normal temporal layer into an SQL query.
    * @param expression The graph-matching expression, for example:
    * <ul>
    *  <li><code>id == 'ew_0_456'</code></li>
    *  <li><code>layer.id == 'orthography' &amp;&amp; /th[aeiou].+/.test(label)</code></li>
    *  <li><code>layer.id == 'orthography' &amp;&amp; my('participant').label == 'Robert' &amp;&amp;
    * my('utterances').start.offset == 12.345</code></li> 
    *  <li><code>graph.id == 'AdaAicheson-01.trs' &amp;&amp; layer.id == 'orthography' &amp;&amp;
    * start.offset &gt; 10.5</code></li> 
    *  <li><code>layer.id == 'utterances' &amp;&amp; list('transcript').includes('ew_0_456')</code></li>
    *  <li><code>previous.id == 'ew_0_456'</code></li>
    * </ul>
    * Also currently supported are the legacy SQL-style expressions:
    * <ul>
    *  <li><code>id = 'ew_0_456'</code></li>
    *  <li><code>layer.id = 'orthography' AND label NOT MATCHES 'th[aeiou].*'</code></li>
    *  <li><code>layer.id = 'orthography' AND my('participant').label = 'Robert' AND
    * my('utterances').start.offset = 12.345</code></li> 
    *  <li><code>graph.id = 'AdaAicheson-01.trs' AND layer.id = 'orthography' AND
    * start.offset &gt; 10.5</code></li> 
    *  <li><code>layer.id = 'utterances' AND 'ew_0_456' IN list('transcript')</code></li>
    *  <li><code>previous.id = 'ew_0_456'</code></li>
    * </ul>
    * @param sqlSelectClause The SQL expression that is to go between SELECT and FROM.
    * @param userWhereClause The expression to add to the WHERE clause to ensure the user doesn't
    * get access to data to which they're not entitled, or null.
    * @param sqlLimitClause The SQL LIMIT clause to append, or null for no LIMIT clause. 
    * @param layer The primary layer, which is a normal temporal layer.
    * @throws AGQLException If the expression is invalid.
    */
   protected Query sqlForTemporalLayer(String expression, String sqlSelectClause, String userWhereClause, String sqlLimitClause, final Layer layer)
      throws AGQLException {

      final Query q = new Query();
      final Stack<String> conditions = new Stack<String>();
      final Flags flags = new Flags();      
      final Vector<String> extraJoins = new Vector<String>();
      final Vector<String> errors = new Vector<String>();

      if (sqlSelectClause.contains("graph.")
          || (userWhereClause != null && userWhereClause.contains("graph."))) {
         flags.transcriptJoin = true;
      }
      
      AGQLBaseListener listener = new AGQLBaseListener() {
            private void space() {
               if (conditions.size() > 0
                   && conditions.peek().charAt(conditions.peek().length() - 1) != ' ') {
                  conditions.push(conditions.pop() + " ");
               }
            }
            private String unquote(String s) {
               return s.substring(1, s.length() - 1)
                  // unescape any remaining quotes
                  .replace("\\'","'").replace("\\\"","\"").replace("\\/","/");
            }
            private String attribute(String s) {
               return s.replaceAll("^(participant|transcript)_","");
            }
            private String escape(String s) {
               return s.replaceAll("\\'", "\\\\'");
            }
            @Override public void exitIdExpression(AGQLParser.IdExpressionContext ctx) { 
               space();
               if (ctx.other == null) {
                  String scope = (String)layer.get("@scope");
                  if (scope == null || scope.equalsIgnoreCase(SqlConstants.SCOPE_FREEFORM)){
                     scope = "";
                  }
                  scope = scope.toLowerCase();
                  conditions.push(
                     "CONCAT('e"+scope+"_"+layer.get("@layer_id")+"_', annotation.annotation_id)");
               } else { // other.id
                  if (ctx.other.myMethodCall() == null) {
                     errors.add("Invalid construction, only my('layer').id is supported: "
                                + ctx.getText());
                  } else {
                     String layerId = unquote(
                        ctx.other.myMethodCall().layer.quotedString.getText());
                     Layer operandLayer = getSchema().getLayer(layerId);
                     if (operandLayer == null) {
                        errors.add("Invalid layer: " + ctx.getText());
                     } else {
                        String attribute = attribute(layerId);
                        if ("transcript".equals(operandLayer.get("@class_id"))) {
                           conditions.push(
                              "(SELECT label"
                              +" FROM annotation_transcript USE INDEX(IDX_AG_ID_NAME)"
                              +" WHERE annotation_transcript.layer = '"+escape(attribute)+"'"
                              +" AND annotation_transcript.ag_id = annotation.ag_id LIMIT 1)");
                        } else if ("speaker".equals(operandLayer.get("@class_id"))) {
                           errors.add("Cannot get participant attribute labels: " + ctx.getText()); // TODO
                           return;
                        } else if (operandLayer.getId().equals("transcript_type")) { // transcript type
                           conditions.push("graph.type_id");
                           flags.transcriptJoin = true;
                        } else if (schema.getEpisodeLayerId().equals(operandLayer.getParentId())) {
                           // episode attribute
                           conditions.push(
                              "(SELECT label"
                              +" FROM `annotation_layer_" + layer.get("@layer_id") + "` annotation"
                              +" WHERE annotation.family_id = graph.family_id) LIMIT 1");
                           flags.transcriptJoin = true;
                        } else { // regular temporal layer
                           // join by the finest-grain compatible with both layers
                           String scope = ((String)layer.get("@scope")).toLowerCase();
                           String operandScope = (String)operandLayer.get("@scope");
                           operandScope = operandScope.toLowerCase();
                           String[] joinFields = {
                              null,
                              "turn_annotation_id",
                              "word_annotation_id",
                              "segment_annotation_id" };
                           int joinFieldIndex = 3;
                           int joinIndex = extraJoins.size();
                           if (scope.equals(SqlConstants.SCOPE_WORD)) {
                              joinFieldIndex = Math.min(joinFieldIndex, 2);
                           }
                           if (operandScope.equals(SqlConstants.SCOPE_WORD) && joinFieldIndex > 2) {
                              joinFieldIndex = Math.min(joinFieldIndex, 2);
                           }
                           if (scope.equals(SqlConstants.SCOPE_META)) {
                              joinFieldIndex = Math.min(joinFieldIndex, 1);
                           }
                           if (operandScope.equals(SqlConstants.SCOPE_META) && joinFieldIndex > 1) {
                              joinFieldIndex = Math.min(joinFieldIndex, 1);
                           }
                           if (scope.equals(SqlConstants.SCOPE_FREEFORM)) {
                              joinFieldIndex = Math.min(joinFieldIndex, 0);
                           }
                           if (operandScope.equals(SqlConstants.SCOPE_FREEFORM) && joinFieldIndex > 0) {
                              joinFieldIndex = Math.min(joinFieldIndex, 0);
                           }
                           // if both layers are the same scope, we don't need to compare anchors
                           boolean temporalJoin = !scope.equals(operandScope);
                           String order = "otherLayer_start_"+joinIndex+".offset,"
                              +" otherLayer_end_"+joinIndex+".offset DESC";
                           if (!temporalJoin) {
                              order = "otherLayer_"+joinIndex+".ordinal";
                           }
                           
                           String joinField = joinFields[joinFieldIndex];
                           extraJoins.add(
                              " INNER JOIN"
                              +" annotation_layer_"+operandLayer.get("@layer_id")+" otherLayer_"+joinIndex
                              +" ON otherLayer_"+joinIndex+".ag_id = annotation.ag_id"
                              +(joinField==null?""
                                :" AND otherLayer_"+joinIndex+"."+joinField+" = annotation."+joinField)
                              +(!temporalJoin?"":
                                " INNER JOIN anchor otherLayer_start_"+joinIndex
                                +" ON otherLayer_"+joinIndex+".start_anchor_id"
                                +" = otherLayer_start_"+joinIndex+".anchor_id"
                                +" AND otherLayer_start_"+joinIndex+".offset <= end.offset"
                                +" INNER JOIN anchor otherLayer_end_"+joinIndex
                                +" ON otherLayer_"+joinIndex+".end_anchor_id"
                                +" = otherLayer_end_"+joinIndex+".anchor_id"
                                +" AND start.offset <= otherLayer_end_"+joinIndex+".offset"));
                           String scopePrefix = operandScope.toLowerCase();
                           if (operandScope.equals(SqlConstants.SCOPE_FREEFORM)) scopePrefix = "";
                           conditions.push(
                              "CONCAT('e"+scopePrefix+"_"+operandLayer.get("@layer_id")+"_',"
                              +" otherLayer_"+joinIndex+".annotation_id)");
                           if (temporalJoin) flags.anchorsJoin = true;
                        } // temporal layer
                     } // valid layer
                  } // my(...).id construction
               } // other annotation
            }
            @Override public void exitLabelExpression(AGQLParser.LabelExpressionContext ctx) {
               space();
               if (ctx.other == null) {
                  conditions.push("annotation.label");
               } else { // other.label
                  if (ctx.other.myMethodCall() == null) {
                     errors.add("Invalid construction, only my('layer').label is supported: "
                                + ctx.getText());
                  } else {
                     String layerId = unquote(
                        ctx.other.myMethodCall().layer.quotedString.getText());
                     if (layerId.equals(schema.getCorpusLayerId())) { // corpus
                        conditions.push("graph.corpus_name");
                        flags.transcriptJoin = true;
                     } else if (layerId.equals(schema.getEpisodeLayerId())) { // episode
                        conditions.push(
                           "(SELECT name"
                           +" FROM transcript_family"
                           +" WHERE transcript_family.family_id = graph.family_id)");
                        flags.transcriptJoin = true;
                     } else if (layerId.equals(schema.getParticipantLayerId())) { // participant
                        if (layer.get("@scope").toString().equalsIgnoreCase(SqlConstants.SCOPE_FREEFORM)) {
                           // turn based on anchor offsets
                           conditions.push(
                              "(SELECT speaker.name"
                              +" FROM speaker"
                              +" INNER JOIN annotation_layer_11 turn ON speaker.speaker_number = turn.label"
                              +" INNER JOIN anchor turn_start_anchor ON turn.start_anchor_id = turn_start_anchor.id"
                              +" INNER JOIN anchor turn_end_anchor ON turn.end_anchor_id = turn_end_anchor.id"
                              +" WHERE turn.ag_id = graph.ag_id"
                              // any overlap with the turn
                              +" AND turn_start_anchor.offset <= end.offset"
                              +" AND start.offset <= turn_end_anchor.offset"
                              +" ORDER BY turn_start_anchor.offset, turn_end_anchor.offset DESC"
                              +((ctx != null)?" LIMIT 1":"")
                              +")");
                           flags.anchorsJoin = true;
                        } else { // turn based on turn_annotation_id
                           // (no need to distinguish between my() and list() because there can be only one turn)
                           conditions.push(
                              "(SELECT speaker.name"
                              +" FROM speaker"
                              +" INNER JOIN annotation_layer_11 turn ON speaker.speaker_number = turn.label"
                              // match turn_annotation_id
                              +" WHERE turn.annotation_id = annotation.turn_annotation_id"
                              +")");
                        } // turn based on turn_annotation_id
                     } else { // other layer
                        Layer operandLayer = getSchema().getLayer(layerId);
                        if (operandLayer == null) {
                           errors.add("Invalid layer: " + ctx.getText());
                        } else {
                           String attribute = attribute(layerId);
                           if ("transcript".equals(operandLayer.get("@class_id"))) {
                              conditions.push(
                                 "(SELECT label"
                                 +" FROM annotation_transcript USE INDEX(IDX_AG_ID_NAME)"
                                 +" WHERE annotation_transcript.layer = '"+escape(attribute)+"'"
                                 +" AND annotation_transcript.ag_id = annotation.ag_id LIMIT 1)");
                           } else if ("speaker".equals(operandLayer.get("@class_id"))) {
                              errors.add("Cannot get participant attribute labels: " + ctx.getText()); // TODO
                              return;
                           } else if (operandLayer.getId().equals("transcript_type")) { // transcript type
                              conditions.push(
                                 "(SELECT type"
                                 +" FROM transcript_type"
                                 +" WHERE transcript_type.type_id = graph.type_id LIMIT 1)");
                              flags.transcriptJoin = true;
                           } else { // regular temporal layer
                              // join by the finest-grain compatible with both layers
                              String scope = ((String)layer.get("@scope")).toLowerCase();
                              String operandScope = (String)operandLayer.get("@scope");
                              operandScope = operandScope.toLowerCase();
                              String[] joinFields = {
                                 null,
                                 "turn_annotation_id",
                                 "word_annotation_id",
                                 "segment_annotation_id" };
                              int joinFieldIndex = 3;
                              int joinIndex = extraJoins.size();
                              if (scope.equals(SqlConstants.SCOPE_WORD)) {
                                 joinFieldIndex = Math.min(joinFieldIndex, 2);
                              }
                              if (operandScope.equals(SqlConstants.SCOPE_WORD) && joinFieldIndex > 2) {
                                 joinFieldIndex = Math.min(joinFieldIndex, 2);
                              }
                              if (scope.equals(SqlConstants.SCOPE_META)) {
                                 joinFieldIndex = Math.min(joinFieldIndex, 1);
                              }
                              if (operandScope.equals(SqlConstants.SCOPE_META) && joinFieldIndex > 1) {
                                 joinFieldIndex = Math.min(joinFieldIndex, 1);
                              }
                              if (scope.equals(SqlConstants.SCOPE_FREEFORM)) {
                                 joinFieldIndex = Math.min(joinFieldIndex, 0);
                              }
                              if (operandScope.equals(SqlConstants.SCOPE_FREEFORM) && joinFieldIndex > 0) {
                                 joinFieldIndex = Math.min(joinFieldIndex, 0);
                              }
                              // if both layers are the same scope, we don't need to compare anchors
                              boolean temporalJoin = !scope.equals(operandScope);
                              String order = "otherLayer_start_"+joinIndex+".offset,"
                                 +" otherLayer_end_"+joinIndex+".offset DESC";
                              if (!temporalJoin) {
                                 order = "otherLayer_"+joinIndex+".ordinal";
                              }
                              
                              String joinField = joinFields[joinFieldIndex];
                              extraJoins.add(
                                 " INNER JOIN"
                                 +" annotation_layer_"+operandLayer.get("@layer_id")+" otherLayer_"+joinIndex
                                 +" ON otherLayer_"+joinIndex+".ag_id = annotation.ag_id"
                                 +(joinField==null?""
                                   :" AND otherLayer_"+joinIndex+"."+joinField+" = annotation."+joinField)
                                 +(!temporalJoin?"":
                                   " INNER JOIN anchor otherLayer_start_"+joinIndex
                                   +" ON otherLayer_"+joinIndex+".start_anchor_id"
                                   +" = otherLayer_start_"+joinIndex+".anchor_id"
                                   +" AND otherLayer_start_"+joinIndex+".offset <= end.offset"
                                   +" INNER JOIN anchor otherLayer_end_"+joinIndex
                                   +" ON otherLayer_"+joinIndex+".end_anchor_id"
                                   +" = otherLayer_end_"+joinIndex+".anchor_id"
                                   +" AND start.offset <= otherLayer_end_"+joinIndex+".offset"));
                              conditions.push("otherLayer_"+joinIndex+".label");
                              if (temporalJoin) flags.anchorsJoin = true;
                           } // temporal layer
                        } // valid layer
                     } // other layer
                  } // my(...).label
               } // other.label
            }
            @Override public void exitGraphIdExpression(AGQLParser.GraphIdExpressionContext ctx) {
               space();
               conditions.push("graph.transcript_id");
               flags.transcriptJoin = true;
            }
            @Override public void exitLabelsMethodCall(AGQLParser.LabelsMethodCallContext ctx) { 
               if (flags.inListLength) return; // exitListLengthExpression will handle this
               space();
               String layerId = unquote(ctx.layer.quotedString.getText());
               if (layerId.equals(schema.getCorpusLayerId())) { // corpus                  
                  conditions.push("(SELECT graph.corpus_name)");
                  flags.transcriptJoin = true;
               } else if (layerId.equals(schema.getEpisodeLayerId())) { // episode
                  conditions.push(
                     "(SELECT name"
                     +" FROM transcript_family"
                     +" WHERE transcript_family.family_id = graph.family_id)");
                  flags.transcriptJoin = true;
               } else if (layerId.equals(schema.getParticipantLayerId())) { // participant
                  if (layer.get("@scope").toString().equalsIgnoreCase(SqlConstants.SCOPE_FREEFORM)) {
                     // turn based on anchor offsets
                     conditions.push(
                        "(SELECT speaker.name"
                        +" FROM speaker"
                        +" INNER JOIN annotation_layer_11 turn ON speaker.speaker_number = turn.label"
                        +" INNER JOIN anchor turn_start_anchor ON turn.start_anchor_id = turn_start_anchor.id"
                        +" INNER JOIN anchor turn_end_anchor ON turn.end_anchor_id = turn_end_anchor.id"
                        +" WHERE turn.ag_id = graph.ag_id"
                        // any overlap with the turn
                        +" AND turn_start_anchor.offset <= end.offset"
                        +" AND start.offset <= turn_end_anchor.offset"
                        +" ORDER BY turn_start_anchor.offset, turn_end_anchor.offset DESC"
                        +((ctx != null)?" LIMIT 1":"")
                        +")");
                     flags.anchorsJoin = true;
                  } else { // turn based on turn_annotation_id
                     // (no need to distinguish between my() and list() because there can be only one turn)
                     conditions.push(
                        "(SELECT speaker.name"
                        +" FROM speaker"
                        +" INNER JOIN annotation_layer_11 turn ON speaker.speaker_number = turn.label"
                        // match turn_annotation_id
                        +" WHERE turn.annotation_id = annotation.turn_annotation_id"
                        +")");
                  } // turn based on turn_annotation_id
               } else { // other layer
                  Layer operandLayer = getSchema().getLayer(layerId);
                  if (operandLayer == null) {
                     errors.add("Invalid layer: " + ctx.getText());
                  } else { // valid layer
                     String attribute = attribute(layerId);
                     if ("transcript".equals(operandLayer.get("@class_id"))) {
                        conditions.push(
                           "(SELECT label"
                           +" FROM annotation_transcript USE INDEX(IDX_AG_ID_NAME)"
                           +" WHERE annotation_transcript.layer = '"+escape(attribute)+"'"
                           +" AND annotation_transcript.ag_id = annotation.ag_id)");
                     } else if ("speaker".equals(operandLayer.get("@class_id"))) {
                        errors.add("Cannot get participant attribute annotators: " + ctx.getText()); // TODO
                        return;
                     } else if (operandLayer.getId().equals("transcript_type")) { // transcript type
                        errors.add("Cannot get transcript type annotator: " + ctx.getText()); // TODO
                        return;
                     } else if (schema.getEpisodeLayerId().equals(operandLayer.getParentId())) {
                        // episode attribute
                        conditions.push(
                           "(SELECT label"
                           +" FROM `annotation_layer_" + layer.get("@layer_id") + "` annotation"
                           +" WHERE annotation.family_id = graph.family_id)");
                        flags.transcriptJoin = true;
                     } else { // regular temporal layer
                        // join by the finest-grain compatible with both layers
                        String scope = ((String)layer.get("@scope")).toLowerCase();
                        String operandScope = (String)operandLayer.get("@scope");
                        operandScope = operandScope.toLowerCase();
                        String[] joinFields = {
                           null,
                           "turn_annotation_id",
                           "word_annotation_id",
                           "segment_annotation_id" };
                        int joinFieldIndex = 3;
                        int joinIndex = extraJoins.size();
                        String selectedValue = "otherLayer_"+joinIndex+".label";
                        if (scope.equals(SqlConstants.SCOPE_WORD)) {
                           joinFieldIndex = Math.min(joinFieldIndex, 2);
                        }
                        if (operandScope.equals(SqlConstants.SCOPE_WORD) && joinFieldIndex > 2) {
                           joinFieldIndex = Math.min(joinFieldIndex, 2);
                        }
                        if (scope.equals(SqlConstants.SCOPE_META)) {
                           joinFieldIndex = Math.min(joinFieldIndex, 1);
                        }
                        if (operandScope.equals(SqlConstants.SCOPE_META) && joinFieldIndex > 1) {
                           joinFieldIndex = Math.min(joinFieldIndex, 1);
                        }
                        if (scope.equals(SqlConstants.SCOPE_FREEFORM)) {
                           joinFieldIndex = Math.min(joinFieldIndex, 0);
                        }
                        if (operandScope.equals(SqlConstants.SCOPE_FREEFORM) && joinFieldIndex > 0) {
                           joinFieldIndex = Math.min(joinFieldIndex, 0);
                        }
                        // if both layers are the same scope, we don't need to compare anchors
                        boolean temporalJoin = !scope.equals(operandScope);
                        String order = "otherLayer_start_"+joinIndex+".offset,"
                           +" otherLayer_end_"+joinIndex+".offset DESC";
                        if (!temporalJoin) {
                           order = "otherLayer_"+joinIndex+".ordinal";
                        }
                        
                        String joinField = joinFields[joinFieldIndex];
                        conditions.push(
                           "(SELECT label"
                           +" FROM annotation_layer_"+operandLayer.get("@layer_id")+" otherLayer"
                           +(!temporalJoin?"":
                             " INNER JOIN anchor otherLayer_start"
                             +" ON otherLayer.start_anchor_id = otherLayer_start.anchor_id"
                             +" AND otherLayer_start.offset <= end.offset"
                             +" INNER JOIN anchor otherLayer_end"
                             +" ON otherLayer.end_anchor_id = otherLayer_end.anchor_id"
                             +" AND start.offset <= otherLayer_end.offset")
                           +" WHERE otherLayer.ag_id = annotation.ag_id"
                           +(joinField==null?""
                             :" AND otherLayer."+joinField+" = annotation."+joinField)
                           +")");
                        if (temporalJoin) flags.anchorsJoin = true;
                     } // temporal layer
                  } // valid layer
               } // other layer
            }
            @Override public void enterListLengthExpression(AGQLParser.ListLengthExpressionContext ctx) {
               flags.inListLength = true;
            }
            @Override public void exitListLengthExpression(AGQLParser.ListLengthExpressionContext ctx) {
               space();
               String layerId = null;
               if (ctx.listExpression().valueListExpression() != null) {
                  if (ctx.listExpression().valueListExpression().labelsMethodCall() != null) {
                     layerId = ctx.listExpression().valueListExpression().labelsMethodCall()
                        .layer.quotedString.getText();
                  } else if (ctx.listExpression().valueListExpression().annotatorsMethodCall() != null) {
                     layerId = ctx.listExpression().valueListExpression().annotatorsMethodCall()
                        .layer.quotedString.getText();
                  }
               } else if (ctx.listExpression().annotationListExpression() != null) {
                  layerId = ctx.listExpression().annotationListExpression().listMethodCall()
                     .layer.quotedString.getText();
               }
               if (layerId == null) {
                  errors.add("Could not identify layer: " + ctx.getText());
               } else {
                  layerId = unquote(layerId);
                  Layer operandLayer = getSchema().getLayer(layerId);
                  if (operandLayer == null) {
                     errors.add("Invalid layer: " + ctx.getText());
                  } else {
                     String attribute = attribute(layerId);
                     if ("transcript".equals(operandLayer.get("@class_id"))) {
                        conditions.push(
                           "(SELECT COUNT(*)"
                           +" FROM annotation_transcript USE INDEX(IDX_AG_ID_NAME)"
                           +" WHERE annotation_transcript.layer = '"+escape(attribute)+"'"
                           +" AND annotation_transcript.ag_id = annotation.ag_id)");
                     } else if ("speaker".equals(operandLayer.get("@class_id"))) {
                        conditions.push(
                           "(SELECT COUNT(*)"
                           +" FROM annotation_participant"
                           +" INNER JOIN transcript_speaker"
                           +" ON annotation_participant.speaker_number = transcript_speaker.speaker_number"
                           +" AND annotation_participant.layer = '"+escape(attribute)+"'"
                           +" WHERE transcript_speaker.ag_id = annotation.ag_id)");
                     } else if (operandLayer.getId().equals("transcript_type")) { // transcript type
                        conditions.push("1");
                     } else if (schema.getEpisodeLayerId().equals(operandLayer.getParentId())) {
                        // episode attribute
                        conditions.push(
                           "(SELECT COUNT(*)"
                           +" FROM `annotation_layer_" + layer.get("@layer_id") + "` annotation"
                           +" WHERE annotation.family_id = graph.family_id)");
                        flags.transcriptJoin = true;
                     } else { // regular temporal layer
                        // join by the finest-grain compatible with both layers
                        String scope = ((String)layer.get("@scope")).toLowerCase();
                        String operandScope = (String)operandLayer.get("@scope");
                        operandScope = operandScope.toLowerCase();
                        String[] joinFields = {
                           null,
                           "turn_annotation_id",
                           "word_annotation_id",
                           "segment_annotation_id" };
                        int joinFieldIndex = 3;
                        if (scope.equals(SqlConstants.SCOPE_WORD)) {
                           joinFieldIndex = Math.min(joinFieldIndex, 2);
                        }
                        if (operandScope.equals(SqlConstants.SCOPE_WORD) && joinFieldIndex > 2) {
                           Math.min(joinFieldIndex, 2);
                        }
                        if (scope.equals(SqlConstants.SCOPE_META)) {
                           joinFieldIndex = Math.min(joinFieldIndex, 1);
                        }
                        if (operandScope.equals(SqlConstants.SCOPE_META) && joinFieldIndex > 1) {
                           Math.min(joinFieldIndex, 1);
                        }
                        if (scope.equals(SqlConstants.SCOPE_FREEFORM)) {
                           joinFieldIndex = Math.min(joinFieldIndex, 0);
                        }
                        if (operandScope.equals(SqlConstants.SCOPE_FREEFORM) && joinFieldIndex > 0) {
                           Math.min(joinFieldIndex, 0);
                        }
                        String joinField = joinFields[joinFieldIndex];
                        conditions.push(
                           "(SELECT COUNT(*)"
                           +" FROM annotation_layer_"+operandLayer.get("@layer_id")+" otherLayer"
                           +" INNER JOIN anchor otherLayer_start"
                           +" ON otherLayer.start_anchor_id = otherLayer_start.anchor_id"
                           +" INNER JOIN anchor otherLayer_end"
                           +" ON otherLayer.end_anchor_id = otherLayer_end.anchor_id"
                           +" WHERE otherLayer.ag_id = annotation.ag_id"
                           +(joinField==null?""
                             :" AND otherLayer."+joinField+" = annotation."+joinField)
                           // any overlap
                           +" AND otherLayer_start.offset <= end.offset"
                           +" AND start.offset <= otherLayer_end.offset"
                           +")");
                        flags.anchorsJoin = true;
                     } // regular temporal layer
                  } // valid layer
               } // layer identified
               flags.inListLength = false;
            }
            @Override public void exitListMethodCall(AGQLParser.ListMethodCallContext ctx) {
               if (flags.inListLength) return; // exitListLengthExpression will handle this
               space();
               String layerId = unquote(ctx.layer.getText());
               Layer operandLayer = getSchema().getLayer(layerId);
               if (operandLayer == null) {
                  errors.add("Invalid layer: " + ctx.getText());
               } else { // valid layer
                  String attribute = attribute(layerId);
                  if ("transcript".equals(operandLayer.get("@class_id"))) {
                     conditions.push(
                        "(SELECT CONCAT('t|','"+escape(attribute)+"'|annotation_id)"
                        +" FROM annotation_transcript USE INDEX(IDX_AG_ID_NAME)"
                        +" WHERE annotation_transcript.layer = '"+escape(attribute)+"'"
                        +" AND annotation_transcript.ag_id = annotation.ag_id)");
                  } else if ("speaker".equals(operandLayer.get("@class_id"))) {
                     errors.add("Cannot get participant attribute annotators: " + ctx.getText()); // TODO
                     return;
                  } else if (operandLayer.getId().equals("transcript_type")) { // transcript type
                     errors.add("Cannot get transcript type annotator: " + ctx.getText()); // TODO
                     return;
                  } else if (schema.getEpisodeLayerId().equals(operandLayer.getParentId())) {
                     // episode attribute
                     conditions.push(
                        "CONCAT('m_', '" + layer.get("@layer_id") + "',graph.family_id)");
                  } else { // regular temporal layer
                     // join by the finest-grain compatible with both layers
                     String scope = ((String)layer.get("@scope")).toLowerCase();
                     String operandScope = (String)operandLayer.get("@scope");
                     operandScope = operandScope.toLowerCase();
                     String[] joinFields = {
                        null,
                        "turn_annotation_id",
                        "word_annotation_id",
                        "segment_annotation_id" };
                     int joinFieldIndex = 3;
                     int joinIndex = extraJoins.size();
                     String selectedValue = "otherLayer_"+joinIndex+".annotated_by";
                     if (scope.equals(SqlConstants.SCOPE_WORD)) {
                        joinFieldIndex = Math.min(joinFieldIndex, 2);
                     }
                     if (operandScope.equals(SqlConstants.SCOPE_WORD) && joinFieldIndex > 2) {
                        joinFieldIndex = Math.min(joinFieldIndex, 2);
                     }
                     if (scope.equals(SqlConstants.SCOPE_META)) {
                        joinFieldIndex = Math.min(joinFieldIndex, 1);
                     }
                     if (operandScope.equals(SqlConstants.SCOPE_META) && joinFieldIndex > 1) {
                        joinFieldIndex = Math.min(joinFieldIndex, 1);
                     }
                     if (scope.equals(SqlConstants.SCOPE_FREEFORM)) {
                        joinFieldIndex = Math.min(joinFieldIndex, 0);
                     }
                     if (operandScope.equals(SqlConstants.SCOPE_FREEFORM) && joinFieldIndex > 0) {
                        joinFieldIndex = Math.min(joinFieldIndex, 0);
                     }
                     boolean temporalJoin = !scope.equals(operandScope);
                     String order = "otherLayer_start_"+joinIndex+".offset,"
                        +" otherLayer_end_"+joinIndex+".offset DESC";
                     if (!temporalJoin) {
                        order = "otherLayer_"+joinIndex+".ordinal";
                     }
                     
                     String joinField = joinFields[joinFieldIndex];
                     if (operandScope.equals(SqlConstants.SCOPE_FREEFORM)) operandScope = "";
                     conditions.push(
                        "(SELECT CONCAT('e"
                        +operandScope.toLowerCase()+"_"
                        +operandLayer.get("@layer_id")+"_', otherLayer.annotation_id)"
                        +" FROM annotation_layer_"+operandLayer.get("@layer_id")+" otherLayer"
                        +(!temporalJoin?"":
                          " INNER JOIN anchor otherLayer_start"
                          +" ON otherLayer.start_anchor_id = otherLayer_start.anchor_id"
                          +" AND otherLayer_start.offset <= end.offset"
                          +" INNER JOIN anchor otherLayer_end"
                          +" ON otherLayer.end_anchor_id = otherLayer_end.anchor_id"
                          +" AND start.offset <= otherLayer_end.offset")
                        +" WHERE otherLayer.ag_id = annotation.ag_id"
                        +(joinField==null?""
                          :" AND otherLayer."+joinField+" = annotation."+joinField)
                        +")");
                     if (temporalJoin) flags.anchorsJoin = true;
                  } // temporal layer
               } // valid layer
            }
            @Override public void exitAnnotatorsMethodCall(AGQLParser.AnnotatorsMethodCallContext ctx) { 
               if (flags.inListLength) return; // exitListLengthExpression will handle this
               space();
               String layerId = unquote(ctx.layer.quotedString.getText());
               Layer operandLayer = getSchema().getLayer(layerId);
               if (operandLayer == null) {
                  errors.add("Invalid layer: " + ctx.getText());
               } else { // valid layer
                  String attribute = attribute(layerId);
                  if ("transcript".equals(operandLayer.get("@class_id"))) {
                     conditions.push(
                        "(SELECT annotated_by"
                        +" FROM annotation_transcript USE INDEX(IDX_AG_ID_NAME)"
                        +" WHERE annotation_transcript.layer = '"+escape(attribute)+"'"
                        +" AND annotation_transcript.ag_id = annotation.ag_id)");
                  } else if ("speaker".equals(operandLayer.get("@class_id"))) {
                     errors.add("Cannot get participant attribute annotators: " + ctx.getText()); // TODO
                     return;
                  } else if (operandLayer.getId().equals("transcript_type")) { // transcript type
                     errors.add("Cannot get transcript type annotators: " + ctx.getText()); // TODO
                     return;
                  } else if (schema.getEpisodeLayerId().equals(operandLayer.getParentId())) {
                     // episode attribute
                     conditions.push(
                        "(SELECT annotated_by"
                        +" FROM `annotation_layer_" + layer.get("@layer_id") + "` annotation"
                        +" WHERE annotation.family_id = graph.family_id)");
                     flags.transcriptJoin = true;
                  } else { // regular temporal layer
                     // join by the finest-grain compatible with both layers
                     String scope = ((String)layer.get("@scope")).toLowerCase();
                     String operandScope = (String)operandLayer.get("@scope");
                     operandScope = operandScope.toLowerCase();
                     String[] joinFields = {
                        null,
                        "turn_annotation_id",
                        "word_annotation_id",
                        "segment_annotation_id" };
                     int joinFieldIndex = 3;
                     int joinIndex = extraJoins.size();
                     String selectedValue = "otherLayer_"+joinIndex+".annotated_by";
                     if (scope.equals(SqlConstants.SCOPE_WORD)) {
                        joinFieldIndex = Math.min(joinFieldIndex, 2);
                     }
                     if (operandScope.equals(SqlConstants.SCOPE_WORD) && joinFieldIndex > 2) {
                        joinFieldIndex = Math.min(joinFieldIndex, 2);
                     }
                     if (scope.equals(SqlConstants.SCOPE_META)) {
                        joinFieldIndex = Math.min(joinFieldIndex, 1);
                     }
                     if (operandScope.equals(SqlConstants.SCOPE_META) && joinFieldIndex > 1) {
                        joinFieldIndex = Math.min(joinFieldIndex, 1);
                     }
                     if (scope.equals(SqlConstants.SCOPE_FREEFORM)) {
                        joinFieldIndex = Math.min(joinFieldIndex, 0);
                     }
                     if (operandScope.equals(SqlConstants.SCOPE_FREEFORM) && joinFieldIndex > 0) {
                        joinFieldIndex = Math.min(joinFieldIndex, 0);
                     }
                     // TODO if (operand.endsWith("').id")) // getting id, not label
                     // { // construct id from scope, layer_id, and annotation_id
                     //    String scopePrefix = operandScope.toLowerCase();
                     //    if (operandScope.equals("F")) scopePrefix = "";
                     //    selectedValue = "CONCAT('e"+scopePrefix+"_"+operandLayer.get("@layer_id")+"_', otherLayer_"+xj+".annotation_id)";
                     // }
                     // if both layers are the same scope, we don't need to compare anchors
                     boolean temporalJoin = !scope.equals(operandScope);
                     String order = "otherLayer_start_"+joinIndex+".offset,"
                        +" otherLayer_end_"+joinIndex+".offset DESC";
                     if (!temporalJoin) {
                        order = "otherLayer_"+joinIndex+".ordinal";
                     }
                     
                     String joinField = joinFields[joinFieldIndex];
                     conditions.push(
                        "(SELECT annotated_by"
                        +" FROM annotation_layer_"+operandLayer.get("@layer_id")+" otherLayer"
                        +(!temporalJoin?"":
                          " INNER JOIN anchor otherLayer_start"
                          +" ON otherLayer.start_anchor_id = otherLayer_start.anchor_id"
                          +" AND otherLayer_start.offset <= end.offset"
                          +" INNER JOIN anchor otherLayer_end"
                          +" ON otherLayer.end_anchor_id = otherLayer_end.anchor_id"
                          +" AND start.offset <= otherLayer_end.offset")
                        +" WHERE otherLayer.ag_id = annotation.ag_id"
                        +(joinField==null?""
                          :" AND otherLayer."+joinField+" = annotation."+joinField)
                        +")");
                     if (temporalJoin) flags.anchorsJoin = true;
                  } // temporal layer
               } // valid layer
            }
            @Override public void exitOrdinalOperand(AGQLParser.OrdinalOperandContext ctx) {
               space();
               conditions.push("annotation.ordinal");
            }
            @Override public void exitAtomListExpression(AGQLParser.AtomListExpressionContext ctx) {
               // pop all the elements off the stack
               Stack<String> atoms = new Stack<String>();
               for (int i = 0; i < ctx.subsequentAtom().size(); i++) {
                  atoms.push(conditions.pop().trim()); // subsequentAtom
               }
               atoms.push(conditions.pop().trim()); // firstAtom

               // create a single element with all of them
               StringBuilder element = new StringBuilder();
               element.append("(");
               element.append(atoms.pop()); // firstAtom
               while (!atoms.empty()) {
                  element.append(",");
                  element.append(atoms.pop()); // subsequentAtom
               } // next atom
               element.append(")");

               // and add the whole list to conditions
               conditions.push(element.toString());
            }
            @Override public void enterComparisonOperator(AGQLParser.ComparisonOperatorContext ctx) {
               space();
               String operator = ctx.operator.getText().trim();
               if (operator.equals("==")) operator = "="; // from JS to SQL equality operator
               conditions.push(operator);
            }
            @Override public void exitPatternMatchExpression(AGQLParser.PatternMatchExpressionContext ctx) {
               if (ctx.negation != null) {
                  conditions.push(" NOT REGEXP ");
               } else {
                  conditions.push(" REGEXP ");
               }
               try
               { // ensure string literals use single, not double, quotes
                  conditions.push("'"+unquote(ctx.patternOperand.getText())+"'");
               }
               catch(Exception exception)
               { // not a string literal
                  conditions.push(ctx.patternOperand.getText());
               }
            }
            @Override public void exitIncludesExpression(AGQLParser.IncludesExpressionContext ctx) {
               if (ctx.IN() != null) {
                  // infix it - i.e. pop the last operand...
                  String listOperand = conditions.pop();
                  // ... insert the operator
                  if (ctx.negation != null) {
                     conditions.push("NOT IN ");
                  } else {
                     conditions.push("IN ");
                  }
                  // ... and push the operand back
                  conditions.push(listOperand);
               } else { // a.includes(b)
                  // need to swap the order of the operands as well
                  String singletonOperand = conditions.pop().trim();
                  String listOperand = conditions.pop().trim();

                  // first singletonOperand
                  conditions.push(singletonOperand);
                  // then operator
                  if (ctx.negation != null) {
                     conditions.push(" NOT IN ");
                  } else {
                     conditions.push(" IN ");
                  }
                  // finally push listOperand
                  conditions.push(listOperand);
               }
            }
            @Override public void exitLogicalOperator(AGQLParser.LogicalOperatorContext ctx) {
               space();
               String operator = ctx.operator.getText().trim();
               if (operator.equals("&&")) operator = "AND";
               else if (operator.equals("||")) operator = "OR";
               conditions.push(operator);
            }
            @Override public void exitLiteralAtom(AGQLParser.LiteralAtomContext ctx) {
               space();
               try
               { // ensure string literals use single, not double, quotes
                  conditions.push("'"+unquote(ctx.literal().stringLiteral().getText())+"'");
               }
               catch(Exception exception)
               { // not a string literal
                  conditions.push(ctx.getText());
               }
            }
            @Override public void exitIdentifierAtom(AGQLParser.IdentifierAtomContext ctx) {
               space();
               conditions.push(ctx.getText());
            }
            @Override public void exitAnchorIdExpression(AGQLParser.AnchorIdExpressionContext ctx) {
               space();
               if (ctx.other != null
                   || (ctx.anchorExpression() != null && ctx.anchorExpression().other != null)) {
                  errors.add("Can only reference this annotation's anchor offsets: " + ctx.getText());
               } else if (ctx.getText().contains("end")) {
                  conditions.push("CONCAT('n_',annotation.end_anchor_id)");
               } else {
                  conditions.push("CONCAT('n_',annotation.start_anchor_id)");
               }
            }
            @Override public void exitAnchorOffsetExpression(AGQLParser.AnchorOffsetExpressionContext ctx) {
               space();
               if (ctx.anchorExpression().other != null) {
                  errors.add("Can only reference this annotation's anchor offsets: " + ctx.getText());
               } else if (ctx.getText().contains("end")) {
                  conditions.push("end.offset");
                  flags.anchorsJoin = true;
               } else {
                  conditions.push("start.offset");
                  flags.anchorsJoin = true;
               }
            }
            @Override public void exitAnnotatorOperand(AGQLParser.AnnotatorOperandContext ctx) {
               space();
               conditions.push("annotation.annotated_by");
            }
            @Override public void exitWhenOperand(AGQLParser.WhenOperandContext ctx) {
               space();
               conditions.push("annotation.annotated_when");
            }
            @Override public void exitLayerExpression(AGQLParser.LayerExpressionContext ctx) { 
               space();
               if (ctx.other != null) {
                  errors.add("Can only reference this annotation's layer: " + ctx.getText());
               } else {
                  conditions.push("'"+layer.getId().replaceAll("'","\\'")+"'");
               }
            }
            @Override public void exitParentIdExpression(AGQLParser.ParentIdExpressionContext ctx) {
               space();
               Layer parentLayer = schema.getLayer(layer.getParentId());
               String parentScope = ((String)parentLayer.get("@scope")).toLowerCase();
               if (parentScope.equals(SqlConstants.SCOPE_FREEFORM)) parentScope = "";
               conditions.push(
                  "CONCAT('e"+parentScope+"_"+parentLayer.get("@layer_id")+"_',"
                  +" annotation.parent_id)");
            }
            @Override public void visitErrorNode(ErrorNode node) {
               errors.add(node.getText());
            }
         };
      AGQLLexer lexer = new AGQLLexer(CharStreams.fromString(expression));
      CommonTokenStream tokens = new CommonTokenStream(lexer);
      AGQLParser parser = new AGQLParser(tokens);
      AGQLParser.BooleanExpressionContext tree = parser.booleanExpression();
      ParseTreeWalker.DEFAULT.walk(listener, tree);

      if (errors.size() > 0) {
         throw new AGQLException(expression, errors);
      }
      StringBuilder sql = new StringBuilder();
      sql.append("SELECT ");
      sql.append(sqlSelectClause);
      sql.append(", '");
      sql.append(layer.getId().replaceAll("\\'", "\\\\'"));
      sql.append("' AS layer");
      sql.append(" FROM annotation_layer_"+layer.get("@layer_id")+" annotation");
      if (flags.transcriptJoin) {
         sql.append(" INNER JOIN transcript graph ON annotation.ag_id = graph.ag_id");
      }
      if (flags.anchorsJoin) {
         sql.append(" INNER JOIN anchor start ON annotation.start_anchor_id = start.anchor_id");
         sql.append(" INNER JOIN anchor end ON annotation.end_anchor_id = end.anchor_id");
      }
      for (String extraJoin : extraJoins) sql.append(extraJoin);
      if (conditions.size() > 0) {
         sql.append(" WHERE "); // TODO ?
         for (String condition : conditions) sql.append(condition);
      }
      if (userWhereClause != null && userWhereClause.trim().length() > 0) {
         sql.append(conditions.size() > 0?" AND ":" WHERE ");
         sql.append(userWhereClause);
      }

      if (!sqlSelectClause.equals("COUNT(*)")) {
         sql.append(" ORDER BY ");
         sql.append(flags.transcriptJoin?"graph.transcript_id":"ag_id");
         sql.append(", ");
         if (flags.anchorsJoin) sql.append("start.offset, end.offset, ");
         sql.append("parent_id, annotation_id");
         if (sqlLimitClause != null) sql.append(" " + sqlLimitClause);
      }

      q.sql = sql.toString();
      return q;
   } // end of sqlForTemporalLayer()

   /**
    * Transforms the given AGQL query for a participant-table layer into an SQL query.
    * @param expression The graph-matching expression, for example:
    * <ul>
    *  <li><code>graph.id == 'AdaAicheson-01.trs' &amp;&amp; layer.id == 'participant'</code></li> 
    *  <li><code>graph.id == 'AdaAicheson-01.trs' &amp;&amp; layer.id == 'main_participant'</code></li> 
    * </ul>
    * @param sqlSelectClause The SQL expression that is to go between SELECT and FROM.
    * @param userWhereClause The expression to add to the WHERE clause to ensure the user doesn't
    * get access to data to which they're not entitled, or null.
    * @param sqlLimitClause The SQL LIMIT clause to append, or null for no LIMIT clause. 
    * @param layer The primary layer, which is a normal temporal layer.
    * @throws AGQLException If the expression is invalid.
    */
   protected Query sqlForParticipantLayer(String expression, String sqlSelectClause, String userWhereClause, String sqlLimitClause, final Layer layer)
      throws AGQLException {

      final Query q = new Query();
      final Stack<String> conditions = new Stack<String>();
      final Flags flags = new Flags();      
      final Vector<String> extraJoins = new Vector<String>();
      final Vector<String> errors = new Vector<String>();

      if (sqlSelectClause.contains("graph.")
          || (userWhereClause != null && userWhereClause.contains("graph."))) {
         flags.transcriptJoin = true;
      }
      
      AGQLBaseListener listener = new AGQLBaseListener() {
            private void space() {
               if (conditions.size() > 0
                   && conditions.peek().charAt(conditions.peek().length() - 1) != ' ') {
                  conditions.push(conditions.pop() + " ");
               }
            }
            private String unquote(String s) {
               return s.substring(1, s.length() - 1)
                  // unescape any remaining quotes
                  .replace("\\'","'").replace("\\\"","\"").replace("\\/","/");
            }
            private String escape(String s) {
               return s.replaceAll("\\'", "\\\\'");
            }
            @Override public void exitIdExpression(AGQLParser.IdExpressionContext ctx) { 
               space();
               if (ctx.other == null) {
                  String scope = (String)layer.get("@scope");
                  if (scope == null || scope.equalsIgnoreCase(SqlConstants.SCOPE_FREEFORM)){
                     scope = "";
                  }
                  scope = scope.toLowerCase();
                  conditions.push(
                     "CONCAT('m_"+layer.get("@layer_id")+"_', annotation.speaker_number)");
               } else { // other.id
                  errors.add("Invalid idExpression, only id is supported: " + ctx.getText());
               } // other annotation
            }
            @Override public void exitLabelExpression(AGQLParser.LabelExpressionContext ctx) {
               space();
               if (ctx.other == null) {
                  conditions.push("annotation.name");
               } else { // other.label TODO add support for participant attributes
                  errors.add("Invalid labelExpression, only label is supported: " + ctx.getText());
               } // other.label
            }
            @Override public void exitGraphIdExpression(AGQLParser.GraphIdExpressionContext ctx) {
               space();
               conditions.push("graph.transcript_id");
               flags.transcriptJoin = true;
            }
            @Override public void exitOrdinalOperand(AGQLParser.OrdinalOperandContext ctx) {
               space();
               conditions.push("0");
            }
            @Override public void exitAtomListExpression(AGQLParser.AtomListExpressionContext ctx) {
               // pop all the elements off the stack
               Stack<String> atoms = new Stack<String>();
               for (int i = 0; i < ctx.subsequentAtom().size(); i++) {
                  atoms.push(conditions.pop().trim()); // subsequentAtom
               }
               atoms.push(conditions.pop().trim()); // firstAtom

               // create a single element with all of them
               StringBuilder element = new StringBuilder();
               element.append("(");
               element.append(atoms.pop()); // firstAtom
               while (!atoms.empty()) {
                  element.append(",");
                  element.append(atoms.pop()); // subsequentAtom
               } // next atom
               element.append(")");

               // and add the whole list to conditions
               conditions.push(element.toString());
            }
            @Override public void enterComparisonOperator(AGQLParser.ComparisonOperatorContext ctx) {
               space();
               String operator = ctx.operator.getText().trim();
               if (operator.equals("==")) operator = "="; // from JS to SQL equality operator
               conditions.push(operator);
            }
            @Override public void exitPatternMatchExpression(AGQLParser.PatternMatchExpressionContext ctx) {
               if (ctx.negation != null) {
                  conditions.push(" NOT REGEXP ");
               } else {
                  conditions.push(" REGEXP ");
               }
               try
               { // ensure string literals use single, not double, quotes
                  conditions.push("'"+unquote(ctx.patternOperand.getText())+"'");
               }
               catch(Exception exception)
               { // not a string literal
                  conditions.push(ctx.patternOperand.getText());
               }
            }
            @Override public void exitIncludesExpression(AGQLParser.IncludesExpressionContext ctx) {
               if (ctx.IN() != null) {
                  // infix it - i.e. pop the last operand...
                  String listOperand = conditions.pop();
                  // ... insert the operator
                  if (ctx.negation != null) {
                     conditions.push("NOT IN ");
                  } else {
                     conditions.push("IN ");
                  }
                  // ... and push the operand back
                  conditions.push(listOperand);
               } else { // a.includes(b)
                  // need to swap the order of the operands as well
                  String singletonOperand = conditions.pop().trim();
                  String listOperand = conditions.pop().trim();

                  // first singletonOperand
                  conditions.push(singletonOperand);
                  // then operator
                  if (ctx.negation != null) {
                     conditions.push(" NOT IN ");
                  } else {
                     conditions.push(" IN ");
                  }
                  // finally push listOperand
                  conditions.push(listOperand);
               }
            }
            @Override public void exitLogicalOperator(AGQLParser.LogicalOperatorContext ctx) {
               space();
               String operator = ctx.operator.getText().trim();
               if (operator.equals("&&")) operator = "AND";
               else if (operator.equals("||")) operator = "OR";
               conditions.push(operator);
            }
            @Override public void exitLiteralAtom(AGQLParser.LiteralAtomContext ctx) {
               space();
               try
               { // ensure string literals use single, not double, quotes
                  conditions.push("'"+unquote(ctx.literal().stringLiteral().getText())+"'");
               }
               catch(Exception exception)
               { // not a string literal
                  conditions.push(ctx.getText());
               }
            }
            @Override public void exitIdentifierAtom(AGQLParser.IdentifierAtomContext ctx) {
               space();
               conditions.push(ctx.getText());
            }
            @Override public void exitLayerExpression(AGQLParser.LayerExpressionContext ctx) { 
               space();
               if (ctx.other != null) {
                  errors.add("Can only reference this annotation's layer: " + ctx.getText());
               } else {
                  conditions.push("'"+layer.getId().replaceAll("'","\\'")+"'");
               }
            }
            @Override public void exitParentIdExpression(AGQLParser.ParentIdExpressionContext ctx) {
               space();
               Layer parentLayer = schema.getLayer(layer.getParentId());
               if (parentLayer.equals(schema.getRoot().getId())) {
                  conditions.push("graph.transcript_id");
               } else {
                  conditions.push(
                     "CONCAT('m_"+parentLayer.get("@layer_id")+"_', annotation.parent_id)");
               }
            }
            @Override public void visitErrorNode(ErrorNode node) {
               errors.add(node.getText());
            }
         };
      AGQLLexer lexer = new AGQLLexer(CharStreams.fromString(expression));
      CommonTokenStream tokens = new CommonTokenStream(lexer);
      AGQLParser parser = new AGQLParser(tokens);
      AGQLParser.BooleanExpressionContext tree = parser.booleanExpression();
      ParseTreeWalker.DEFAULT.walk(listener, tree);

      if (errors.size() > 0) {
         throw new AGQLException(expression, errors);
      }
      StringBuilder sql = new StringBuilder();
      sql.append("SELECT ");
      sql.append(sqlSelectClause
                 .replace("annotation.*",
                          "annotation.name AS label, annotation.speaker_number AS annotation_id,"
                          +" 100 AS label_status, 0 AS ordinal,"
                          +" NULL AS start_anchor_id, NULL AS end_anchor_id,"
                          +" NULL AS annotated_by, NULL AS annotated_when"));
      sql.append(", '");
      sql.append(layer.getId().replaceAll("\\'", "\\\\'"));
      sql.append("' AS layer");
      sql.append(" FROM transcript_speaker");
      sql.append(" INNER JOIN transcript graph ON transcript_speaker.ag_id = graph.ag_id");
      sql.append(" INNER JOIN speaker annotation ON transcript_speaker.speaker_number = annotation.speaker_number");
      for (String extraJoin : extraJoins) sql.append(extraJoin);
      if (conditions.size() > 0) {
         sql.append(" WHERE "); // TODO ?
         for (String condition : conditions) sql.append(condition);
      }
      if (SqlConstants.LAYER_MAIN_PARTICIPANT == ((Integer)layer.get("@layer_id")).intValue()) {
         sql.append(conditions.size() > 0?" AND ":" WHERE ");
         sql.append("main_speaker <> 0");
      }
      if (userWhereClause != null && userWhereClause.trim().length() > 0) {
         sql.append(conditions.size() > 0?" AND ":" WHERE ");
         sql.append(userWhereClause);
      }


      if (!sqlSelectClause.equals("COUNT(*)")) {
         sql.append(" ORDER BY ");
         sql.append("graph.transcript_id, annotation.name");
         if (sqlLimitClause != null) sql.append(" " + sqlLimitClause);
      }
      
      q.sql = sql.toString();
      return q;
   } // end of sqlForParticipantLayer()
   
   /**
    * Deduces the primary layer of the given expression.
    * @param expression
    * @return The primary layer, or null if none could be identified.
    */
   protected Layer deducePrimaryLayer(String expression)
   {
      final Vector<Layer> candidateLayers = new Vector<Layer>();

      // we look for either something like "layer.id == 'layer'" 
      // or something like "id == 'ew_0_123'" or "['ew_0_123','ew_0_123'].includes(id)"
      // from which the layer can be deduced
      AGQLBaseListener listener = new AGQLBaseListener() {
            MessageFormat fmtAnnotationId = new MessageFormat("e{0}_{1,number,0}_{2,number,0}");
            String unquote(String s) {
               return s.substring(1, s.length() - 1)
                  // unescape any remaining quotes
                  .replace("\\'","'").replace("\\\"","\"").replace("\\/","/");
            }
            @Override public void exitComparisonPredicate(AGQLParser.ComparisonPredicateContext ctx) {
               if (ctx.comparisonOperator().EQ() != null) { // LHS == RHS
                  AGQLParser.OperandContext lhs = ctx.operand(0);
                  AGQLParser.OperandContext rhs = ctx.operand(1);
                  // is LHS id?
                  if (lhs instanceof AGQLParser.IdOperandContext // "id == ..."
                      || lhs instanceof AGQLParser.LayerOperandContext) { // "layer.id == ..."

                     // is RHS a string literal?
                     if (rhs instanceof AGQLParser.AtomOperandContext) {
                        AGQLParser.AtomOperandContext rhsAtom = (AGQLParser.AtomOperandContext)rhs;
                        if (rhsAtom.atom() instanceof AGQLParser.LiteralAtomContext) {
                           AGQLParser.LiteralAtomContext literalAtom
                              = (AGQLParser.LiteralAtomContext)rhsAtom.atom();
                           if (literalAtom.literal().stringLiteral() != null) {
                              String rhsQuotedString =
                                 literalAtom.literal().stringLiteral().quotedString.getText();
                              String rhsString = unquote(rhsQuotedString);
                              
                              if (lhs instanceof AGQLParser.IdOperandContext) { // "id == 'ew_0_123'"
                                 // parse ID
                                 try {
                                    Object[] o = fmtAnnotationId.parse(rhsString);
                                    // get layer_id part
                                    Integer layer_id = Integer.valueOf(((Long)o[1]).intValue());
                                    // find a layer with that layer_id
                                    for (Layer l : schema.getLayers().values()) {
                                       if (layer_id.equals(l.get("@layer_id"))) {
                                          candidateLayers.add(l);
                                          break;
                                       } // do the layer_ids match
                                    } // next layer
                                 } catch(ParseException exception) {}
                              } else { // "layer.id == 'layer'"
                                 candidateLayers.add(schema.getLayer(rhsString));
                              }
                           } // string literal
                        } // literal
                     } // rhs is atom
                  } // lhs is id or layerId
               } // EQ
            }
            @Override public void exitIncludesExpression(AGQLParser.IncludesExpressionContext ctx) {
               if (ctx.listOperand.atomListExpression() != null) {
                  // something like "['ew_0_123','ew_0_123'].includes(id)"
                  AGQLParser.FirstAtomContext firstAtom
                     = ctx.listOperand.atomListExpression().firstAtom();
                  if (firstAtom.atom() instanceof AGQLParser.LiteralAtomContext) {
                     AGQLParser.LiteralAtomContext literalAtom
                        = (AGQLParser.LiteralAtomContext)firstAtom.atom();
                     if (literalAtom.literal().stringLiteral() != null) {
                        String firstAtomQuotedString =
                           literalAtom.literal().stringLiteral().quotedString.getText();
                        String firstAtomString = unquote(firstAtomQuotedString);
                        
                        // parse ID
                        try {
                           Object[] o = fmtAnnotationId.parse(firstAtomString);
                           // get layer_id part
                           Integer layer_id = Integer.valueOf(((Long)o[1]).intValue());
                           // find a layer with that layer_id
                           for (Layer l : schema.getLayers().values()) {
                              if (layer_id.equals(l.get("@layer_id"))) {
                                 candidateLayers.add(l);
                                 break;
                              } // do the layer_ids match
                           } // next layer
                        } catch(ParseException exception) {}
                     } // string literal
                  } // literal
               } // atom list
            }
         };
      AGQLLexer lexer = new AGQLLexer(CharStreams.fromString(expression));
      CommonTokenStream tokens = new CommonTokenStream(lexer);
      AGQLParser parser = new AGQLParser(tokens);
      AGQLParser.BooleanExpressionContext tree = parser.booleanExpression();
      ParseTreeWalker.DEFAULT.walk(listener, tree);
      
      if (candidateLayers.size() > 0) {
         return candidateLayers.firstElement();
      }
      else
      {
         return null;
      }
   } // end of deducePrimaryLayer()

   class Flags {
      boolean transcriptJoin = false;
      boolean anchorsJoin = false; // TODO split into startAnchorJoin and endAnchorJoin
      boolean inListLength = false;
   }

   /** 
    * Encapsulates the results of {@link #sqlFor(String,String,String,String)}
    * including the SQL.
    * string and the parameters to set.
    */
   public static class Query
   {
      public String sql;
      public List<Object> parameters = new Vector<Object>();
    
      /**
       * Creates a prepared statement from the sql string and the parameters.
       * @param db A connection to the database.
       * @return A prepared statement with parameters set.
       * @throws SqlException
       */
      public PreparedStatement prepareStatement(Connection db)
         throws SQLException {
         PreparedStatement query = db.prepareStatement(sql);
         int p = 1;
         for (Object parameter : parameters) {
            if (parameter instanceof Integer) query.setInt(p++, (Integer)parameter);
            else if (parameter instanceof Double) query.setDouble(p++, (Double)parameter);
            else query.setString(p++, parameter.toString());
         } // next parameter
         return query;
      } // end of prepareStatement()
   }
} // end of class AnnotationAgqlToSql
