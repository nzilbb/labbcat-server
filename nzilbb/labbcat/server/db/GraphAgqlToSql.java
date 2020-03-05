//
// Copyright 2019-2020 New Zealand Institute of Language, Brain and Behaviour, 
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
import java.util.List;
import java.util.Vector;
import java.util.Stack;
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
public class GraphAgqlToSql {
   
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
   public GraphAgqlToSql setSchema(Schema schema) { this.schema = schema; return this; }
  
   // Methods:
  
   /**
    * Default constructor.
    */
   public GraphAgqlToSql() {
   } // end of constructor
  
   /**
    * Attribute constructor.
    */
   public GraphAgqlToSql(Schema schema) {
      setSchema(schema);
   } // end of constructor
  
   /**
    * Transforms the given AGQL query into an SQL query.
    * @param expression The graph-matching expression, for example:
    * <ul>
    *  <li><code>id MATCHES 'Ada.+'</code></li>
    *  <li><code>'Robert' IN labels('participant')</code></li>
    *  <li><code>my('corpus').label = 'CC'</code></li>
    *  <li><code>my('episode').label = 'Ada Aitcheson'</code></li>
    *  <li><code>my('transcript_scribe').label = 'Robert'</code></li>
    *  <li><code>my('participant_languages').label = 'en'</code></li>
    *  <li><code>my('noise').label = 'bell'</code></li>
    *  <li><code>'en' IN labels('transcript_languages')</code></li>
    *  <li><code>'en' IN labels('participant_languages')</code></li>
    *  <li><code>'bell' IN labels('noise')</code></li>
    *  <li><code>list('transcript_languages').length gt; 1</code></li>
    *  <li><code>list('participant_languages').length gt; 1</code></li>
    *  <li><code>list('transcript').length gt; 100</code></li>
    *  <li><code>'Robert' IN annotators('transcript_rating')</code></li>
    *  <li><code>id NOT MATCHES 'Ada.+' AND my('corpus').label = 'CC' AND 'Robert' IN
    *   labels('participant')</code></li> 
    * </ul>
    * </ul>
    * @param sqlSelectClause The SQL expression that is to go between SELECT and FROM.
    * @param userWhereClause The expression to add to the WHERE clause to ensure the user doesn't
    * get access to data to which they're not entitled, or null.
    * @param orderClause A comma-separated list of AGQL expressions to determine the order of
    * results; e.g. "my('corpus').label, id", or null. 
    * @param sqlLimitClause The SQL LIMIT clause to append, or null for no LIMIT clause. 
    * @throws AGQLException If the expression is invalid.
    */
   public Query sqlFor(String expression, String sqlSelectClause, String userWhereClause, String orderClause, String sqlLimitClause)
      throws AGQLException {
      // ensure there's always a sensible order
      if (orderClause == null || orderClause.trim().length() == 0) orderClause = "id ASC";
      final Query q = new Query();
      final Stack<String> conditions = new Stack<String>();
      final Flags flags = new Flags();      
      final Vector<String> errors = new Vector<String>();

      AGQLBaseListener listener = new AGQLBaseListener() {
            private void space() {
               if (conditions.size() > 0
                   && conditions.peek().charAt(conditions.peek().length() - 1) != ' ') {
                  conditions.push(conditions.pop() + " ");
               }
            }
            private String unquote(String s) {
               return s.substring(1, s.length() - 1);
            }
            private String attribute(String s) {
               return s.replaceAll("^(participant|transcript)_","");
            }
            private String escape(String s) {
               return s.replaceAll("\\'", "\\\\'");
            }
            @Override public void exitIdExpression(AGQLParser.IdExpressionContext ctx) {
               space();
               if (ctx.other != null) {
                  errors.add("Invalid construction, only \"id\" is supported: "
                             + ctx.getText());
               } else {
                  conditions.push("transcript.transcript_id");
               }
            }
            @Override public void exitLabelExpression(AGQLParser.LabelExpressionContext ctx) {
               space();
               if (ctx.other == null) {
                  conditions.push("transcript.transcript_id");
               } else {
                  if (ctx.other.myMethodCall() == null) {
                     errors.add("Invalid construction, only my('layer').label is supported: "
                                + ctx.getText());
                  } else {
                     String layerId = unquote(
                        ctx.other.myMethodCall().layer.quotedString.getText());
                     if (layerId.equals(schema.getCorpusLayerId())) { // corpus
                        conditions.push("transcript.corpus_name");
                     } else if (layerId.equals(schema.getEpisodeLayerId())) { // episode
                        conditions.push(
                           "(SELECT name"
                           +" FROM transcript_family"
                           +" WHERE transcript_family.family_id = transcript.family_id)");
                     } else if (layerId.equals(schema.getParticipantLayerId())) { // participant
                        conditions.push(
                           "(SELECT speaker.name"
                           +" FROM transcript_speaker"
                           +" INNER JOIN speaker ON transcript_speaker.speaker_number = speaker.speaker_number"
                           +" WHERE transcript_speaker.ag_id = transcript.ag_id"
                           // the first one
                           +" ORDER BY speaker.name LIMIT 1)");
                     } else if (layerId.equals(schema.getRoot().getId())) { // graph
                        conditions.push("transcript.transcript_id");
                     } else { // other layer
                        Layer layer = getSchema().getLayer(layerId);
                        if (layer == null) {
                           errors.add("Invalid layer: " + ctx.getText());
                        } else {
                           String attribute = attribute(layerId);
                           if ("transcript".equals(layer.get("@class_id"))) {
                              conditions.push(
                                 "(SELECT label"
                                 +" FROM annotation_transcript USE INDEX(IDX_AG_ID_NAME)"
                                 +" WHERE annotation_transcript.layer = '"+escape(attribute)+"'"
                                 +" AND annotation_transcript.ag_id = transcript.ag_id"
                                 +" ORDER BY annotation_id LIMIT 1)");
                           } else if ("speaker".equals(layer.get("@class_id"))) { // participant attribute
                              conditions.push(
                                 "(SELECT label"
                                 +" FROM annotation_participant"
                                 +" INNER JOIN transcript_speaker"
                                 +" ON annotation_participant.speaker_number = transcript_speaker.speaker_number"
                                 +" AND annotation_participant.layer = '"+escape(attribute)+"'"
                                 +" WHERE transcript_speaker.ag_id = transcript.ag_id"
                                 +" ORDER BY annotation_id LIMIT 1)");
                           } else if (layer.getId().equals("transcript_type")) {
                              // transcript type TODO this should be a join
                              conditions.push(
                                 "(SELECT transcript_type AS label"
                                 +" FROM transcript_type"
                                 +" WHERE transcript_type.type_id = transcript.type_id)");
                           } else if (schema.getEpisodeLayerId().equals(layer.getParentId())) {
                              // episode attribute
                              conditions.push(
                                 "(SELECT label"
                                 +" FROM `annotation_layer_" + layer.get("@layer_id") + "` annotation"
                                 +" WHERE annotation.family_id = transcript.family_id"
                                 +" ORDER BY annotation.ordinal LIMIT 1)");
                           } else { // regular temporal layer
                              conditions.push(
                                 "(SELECT label"
                                 +" FROM annotation_layer_" + layer.get("@layer_id") + " annotation"
                                 +" INNER JOIN anchor ON annotation.start_anchor_id = anchor.anchor_id"
                                 +" WHERE annotation.ag_id = transcript.ag_id"
                                 +" ORDER BY anchor.offset, annotation.annotation_id LIMIT 1)");
                           } // regular temporal layer
                        } // valid label
                     } // other layer
                  } // my(...).label
               } // something.label
            }
            @Override public void exitGraphIdExpression(AGQLParser.GraphIdExpressionContext ctx) {
               space();
               conditions.push("transcript.transcript_id");
            }
            @Override public void enterLabelsMethodCall(AGQLParser.LabelsMethodCallContext ctx) {
               if (flags.inListLength) return; // exitListLengthExpression will handle this
               space();
               String layerId = unquote(ctx.layer.quotedString.getText());
               if (layerId.equals(schema.getCorpusLayerId())) { // corpus                  
                  conditions.push("(SELECT transcript.corpus_name)");
               } else if (layerId.equals(schema.getEpisodeLayerId())) { // episode
                  conditions.push(
                     "(SELECT name"
                     +" FROM transcript_family"
                     +" WHERE transcript_family.family_id = graph.family_id)");
               } else if (layerId.equals(schema.getParticipantLayerId())) { // participant
                  conditions.push(
                     "(SELECT speaker.name"
                     +" FROM transcript_speaker"
                     +" INNER JOIN speaker ON transcript_speaker.speaker_number = speaker.speaker_number"
                     +" WHERE transcript_speaker.ag_id = transcript.ag_id)");
               } else { // other layer
                  Layer layer = getSchema().getLayer(layerId);
                  if (layer == null) {
                     errors.add("Invalid layer: " + ctx.getText());
                  } else {
                     String attribute = attribute(layerId);
                     if ("transcript".equals(layer.get("@class_id"))) {
                        conditions.push(
                           "(SELECT DISTINCT label"
                           +" FROM annotation_transcript USE INDEX(IDX_AG_ID_NAME)"
                           +" WHERE annotation_transcript.layer = '"+escape(attribute)+"'"
                           +" AND annotation_transcript.ag_id = transcript.ag_id)");
                     } else if ("speaker".equals(layer.get("@class_id"))) {
                        conditions.push(
                           "(SELECT DISTINCT label"
                           +" FROM annotation_participant"
                           +" INNER JOIN transcript_speaker"
                           +" ON annotation_participant.speaker_number = transcript_speaker.speaker_number"
                           +" AND annotation_participant.layer = '"+escape(attribute)+"'"
                           +" WHERE transcript_speaker.ag_id = transcript.ag_id)");
                     } else if (layer.getId().equals("transcript_type")) { // transcript type TODO this should be a join
                        conditions.push(
                           "(SELECT transcript_type AS label"
                           +" FROM transcript_type"
                           +" WHERE transcript_type.type_id = transcript.type_id)");
                     } else if (schema.getEpisodeLayerId().equals(layer.getParentId())) { // episode attribute
                        conditions.push(
                           "(SELECT label"
                           +" FROM `annotation_layer_" + layer.get("@layer_id") + "` annotation"
                           +" WHERE annotation.family_id = transcript.family_id)");
                     } else { // regular temporal layer
                        conditions.push(
                           "(SELECT label"
                           +" FROM annotation_layer_" + layer.get("@layer_id") + " annotation"
                           +" WHERE annotation.ag_id = transcript.ag_id)");
                     } // regular temporal layer
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
                  Layer layer = getSchema().getLayer(layerId);
                  if (layer == null) {
                     errors.add("Invalid layer: " + ctx.getText());
                  } else {
                     String attribute = attribute(layerId);
                     if ("transcript".equals(layer.get("@class_id"))) {
                        conditions.push(
                           "(SELECT COUNT(*)"
                           +" FROM annotation_transcript USE INDEX(IDX_AG_ID_NAME)"
                           +" WHERE annotation_transcript.layer = '"+escape(attribute)+"'"
                           +" AND annotation_transcript.ag_id = transcript.ag_id)");
                     } else if ("speaker".equals(layer.get("@class_id"))) {
                        conditions.push(
                           "(SELECT COUNT(*)"
                           +" FROM annotation_participant"
                           +" INNER JOIN transcript_speaker"
                           +" ON annotation_participant.speaker_number = transcript_speaker.speaker_number"
                           +" AND annotation_participant.layer = '"+escape(attribute)+"'"
                           +" WHERE transcript_speaker.ag_id = transcript.ag_id)");
                     } else if (layer.getId().equals("transcript_type")) { // transcript type
                        conditions.push("1");
                     } else if (schema.getEpisodeLayerId().equals(layer.getParentId())) {
                        // episode attribute
                        conditions.push(
                           "(SELECT COUNT(*)"
                           +" FROM `annotation_layer_" + layer.get("@layer_id") + "` annotation"
                           +" WHERE annotation.family_id = transcript.family_id)");
                     } else { // regular temporal layer
                        conditions.push(
                           "(SELECT COUNT(*)"
                           +" FROM annotation_layer_" + layer.get("@layer_id") + " annotation"
                           +" WHERE annotation.ag_id = transcript.ag_id)");
                     } // regular temporal layer
                  } // valid layer
               } // layerId identified
               flags.inListLength = false;
            }
            @Override public void enterAnnotatorsMethodCall(AGQLParser.AnnotatorsMethodCallContext ctx) {
               if (flags.inListLength) return; // exitListLengthExpression will handle this
               space();
               String layerId = unquote(ctx.layer.quotedString.getText());
               Layer layer = getSchema().getLayer(layerId);
               if (layer == null) {
                  errors.add("Invalid layer: " + ctx.getText());
               } else {
                  String attribute = attribute(layerId);
                  if ("transcript".equals(layer.get("@class_id"))) {
                     conditions.push(
                        "(SELECT DISTINCT annotated_by"
                        +" FROM annotation_transcript USE INDEX(IDX_AG_ID_NAME)"
                        +" WHERE annotation_transcript.layer = '"+escape(attribute)+"'"
                        +" AND annotation_transcript.ag_id = transcript.ag_id)");
                  } else if ("speaker".equals(layer.get("@class_id"))) {
                     conditions.push(
                        "(SELECT DISTINCT annotated_by"
                        +" FROM annotation_participant"
                        +" INNER JOIN transcript_speaker"
                        +" ON annotation_participant.speaker_number = transcript_speaker.speaker_number"
                        +" AND annotation_participant.layer = '"+escape(attribute)+"'"
                        +" WHERE transcript_speaker.ag_id = transcript.ag_id)");
                  } else if (schema.getEpisodeLayerId().equals(layer.getParentId())) {
                     // episode attribute
                     conditions.push(
                        "(SELECT DISTINCT annotated_by"
                        +" FROM `annotation_layer_" + layer.get("@layer_id") + "` annotation"
                        +" WHERE annotation.family_id = transcript.family_id)");
                  } else { // regular temporal layer
                     conditions.push(
                        "(SELECT DISTINCT annotated_by"
                        +" FROM annotation_layer_" + layer.get("@layer_id") + " annotation"
                        +" WHERE annotation.ag_id = transcript.ag_id)");
                  } // regular temporal layer
               } // valid layer
            }
            @Override public void exitOrdinalOperand(AGQLParser.OrdinalOperandContext ctx) {
               space();
               conditions.push("transcript.family_sequence");
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
            @Override public void enterAscendingOrderExpression(AGQLParser.AscendingOrderExpressionContext ctx) {
               if (conditions.size() > 0) conditions.push(", ");
            }
            @Override public void exitAscendingOrderExpression(AGQLParser.AscendingOrderExpressionContext ctx) {
               // no need for: conditions.push(" ASC");
            }
            @Override public void enterDescendingOrderExpression(AGQLParser.DescendingOrderExpressionContext ctx) {
               if (conditions.size() > 0) conditions.push(", ");
            }
            @Override public void exitDescendingOrderExpression(AGQLParser.DescendingOrderExpressionContext ctx) {
               conditions.push(" DESC");
            }
            @Override public void visitErrorNode(ErrorNode node) {
               errors.add(node.getText());
            }
         };
      AGQLLexer lexer = new AGQLLexer(CharStreams.fromString(expression));
      CommonTokenStream tokens = new CommonTokenStream(lexer);
      AGQLParser parser = new AGQLParser(tokens);
      AGQLParser.BooleanExpressionContext tree = parser.booleanExpression();
      if (expression != null && expression.trim().length() > 0)
      {
         ParseTreeWalker.DEFAULT.walk(listener, tree);
      }

      if (errors.size() > 0) {
         throw new AGQLException(expression, errors);
      }
      StringBuilder sql = new StringBuilder();
      sql.append("SELECT ");
      sql.append(sqlSelectClause);
      sql.append(" FROM transcript");
      if (conditions.size() > 0) {
         sql.append(" WHERE ");
         for (String condition : conditions) sql.append(condition);
      }
      if (userWhereClause != null && userWhereClause.trim().length() > 0) {
         sql.append(conditions.size() > 0?" AND ":" WHERE ");
         sql.append(userWhereClause);
      }

      // now order clause
      StringBuilder order = new StringBuilder();
      order.append(" ORDER BY ");
      conditions.clear();
      lexer.setInputStream(CharStreams.fromString(orderClause));
      tokens = new CommonTokenStream(lexer);
      parser = new AGQLParser(tokens);
      AGQLParser.OrderListExpressionContext orderTree = parser.orderListExpression();
      ParseTreeWalker.DEFAULT.walk(listener, orderTree);
      for (String condition : conditions) order.append(condition);
      sql.append(order);

      if (sqlLimitClause != null && sqlLimitClause.trim().length() > 0) {
         sql.append(" ");
         sql.append(sqlLimitClause);
      }

      q.sql = sql.toString();
      return q;
   } // end of sqlFor()

   class Flags {
      boolean inListLength = false;
   }
   
   /** 
    * Encapsulates the results of {@link #sqlFor(String,String,String,String,String)}
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
} // end of class GraphAgqlToSql
