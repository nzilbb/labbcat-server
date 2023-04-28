//
// Copyright 2023 New Zealand Institute of Language, Brain and Behaviour, 
// University of Canterbury
// Written by Robert Fromont - robert.fromont@canterbury.ac.nz
//
//    This file is part of LaBB-CAT.
//
//    LaBB-CAT is free software; you can redistribute it and/or modify
//    it under the terms of the GNU Affero General Public License as published by
//    the Free Software Foundation; either version 3 of the License, or
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Vector;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import nzilbb.ag.*;
import nzilbb.labbcat.server.search.Column;
import nzilbb.labbcat.server.search.LayerMatch;
import nzilbb.labbcat.server.search.Matrix;
import nzilbb.labbcat.server.search.SearchTask;

/**
 * A Graph series that filters another Graph series, collapsing consecutive instances of
 * the same graph into one graph.
 * <p> Annotations from selected layers a copied from subsequent instances into the first,
 * before the first graph is returned.
 * @author Robert Fromont robert@fromont.net.nz
 */
public class OneQuerySearch extends SearchTask {
  
  /**
   * Create an SQL query that identifies results that match the search matrix patterns,
   * filling in the given lists with information about parameters to set.
   * @param parameters List of parameter values, which must be either a Double or a String.
   * @param schema The layer schema.
   * @param layerIsSpanningAndWordAnchored A predicate that determines whether the given layer
   * is a phrase or span layer for which all annotations share anchors with words.
   * @param participantCondition Supplies the WHERE condition that identifies the target
   * participants, presumably based on {@link Matrix#participantQuery}. 
   * @param transcriptCondition Supplies the WHERE condition that identifies the target
   * transcripts, presumably based on {@link Matrix#transcriptQuery}. 
   * @return An SQL query to implement the query.
   * @throws Exception If the search should be halted for any reason
   * - e.g. the {@link Matrix#participantQuery} identifies no participants.
   */
  public String generateSql( // TODO use LinkedHasjet<String> for extra joins etc.
    Vector<Object> parameters,
    Schema schema, Predicate<Layer> layerIsSpanningAndWordAnchored,
    UnaryOperator<String> participantCondition, UnaryOperator<String> transcriptCondition)
    throws Exception {
    description = "";
    
    // column 0 first
    int iWordColumn = 0;
    String sSqlExtraJoinsFirst = "";
    String sSqlLayerMatchesFirst = "";
    String sSqlExtraFieldsFirst = "";
    boolean bTargetAnnotation = false;
    String targetExpression = "word_0.annotation_id";
    String targetSegmentExpression = "NULL";
    String targetSegmentOrder = "";
    
    Object[] columnSuffix = { "_" + iWordColumn };
    String sSqlWordStartJoin = sqlWordStartJoin.format(columnSuffix);
    String sSqlWordEndJoin = sqlWordEndJoin.format(columnSuffix);
    String sSegmentStartJoin = sqlSegmentStartJoin.format(columnSuffix);
    String sSqlLineJoin = sqlLineJoin.format(columnSuffix);
    String sSqlEndLineJoin = sqlEndLineJoin.format(columnSuffix);
    String sSqlEndTurnJoin = sqlEndTurnJoin.format(columnSuffix);

    // ensure we don't have any null Booleans in the search matrix
    matrix.layerMatchStream().forEach(layerMatch -> layerMatch.setNullBooleans());
    
    // we need a primary word table to join to for each column (to determine adjacency, etc.)
    // but to minimise the number of joins, we don't just make that the TRANSCRIPTION layer
    // instead, we use any word layer that there's a match for (there will be one usually)
    // and fall back to joining to the TRANSCRIPTION layer if all patterns in a given column
    // are for non-word layers...
    
    Optional<LayerMatch> firstPrimaryWordLayer = matrix.getColumns().get(iWordColumn)
      .getLayers().values().stream()
      .filter(LayerMatch::HasPattern)
      // not a "NOT .+" expression
      .filter(layerMatch -> !layerMatch.getNot() || !".+".equals(layerMatch.getPattern()))
      .filter(layerMatch -> schema.getLayer(layerMatch.getId()) != null)
      // word scope
      .filter(layerMatch -> IsWordLayer(schema.getLayer(layerMatch.getId()), schema))
      .findAny();
    
    // do we need a word layer to anchor to?
    if (!firstPrimaryWordLayer.isPresent()) {
      sSqlExtraJoinsFirst += " INNER JOIN annotation_layer_"+ SqlConstants.LAYER_TRANSCRIPTION
        +" word_"+iWordColumn+" ON word_"+iWordColumn+".turn_annotation_id = turn.annotation_id";
    }
    
    // check for segment layer search
    // this affects how we match aligned word layers
    Optional<Layer> targetSegmentLayer = matrix.getColumns().get(iWordColumn)
      .getLayers().values().stream()
      .filter(LayerMatch::HasPattern)
      .map(layerMatch -> schema.getLayer(layerMatch.getId()))
      .filter(Objects::nonNull)
      .filter(layer -> IsSegmentLayer(layer, schema))
      .findAny();
    boolean bUseWordContainsJoins = targetSegmentLayer.isPresent();
    
    int iTargetLayer = SqlConstants.LAYER_TRANSCRIPTION; // by default
    int iTargetColumn = 0; // by default
    String targetLayerId = matrix.getTargetLayerId();
    Layer targetLayer = null;
    if (targetLayerId == null) {
      targetLayerId = schema.getWordLayerId();
    } else { // there is a marked target
      targetLayer = schema.getLayer(targetLayerId);
      if (targetLayer != null && targetLayer.containsKey("layer_id")) {
        iTargetLayer = (Integer)targetLayer.get("layer_id");
        iTargetColumn = matrix.getTargetColumn();
      } else {
        iTargetLayer = SqlConstants.LAYER_TRANSCRIPTION;
      }
    } // there is a marked target
    
    // look for a layer with a search specification
    final Vector<LayerMatch> layersPrimaryFirst = new Vector<LayerMatch>();
    matrix.getColumns().get(iWordColumn).getLayers().values().stream()
      .filter(LayerMatch::HasPattern)
      // existing layers
      .filter(layerMatch -> schema.getLayer(layerMatch.getId()) != null)
      // temporal layers
      .filter(layerMatch -> schema.getLayer(layerMatch.getId()).containsKey("layer_id"))
      .forEach(layerMatch -> layersPrimaryFirst.add(layerMatch));
    // move primary word layer to the front
    if (firstPrimaryWordLayer.isPresent()) {
      if (layersPrimaryFirst.remove(firstPrimaryWordLayer.get())) { // it was there
        layersPrimaryFirst.add(0, firstPrimaryWordLayer.get());
      }
    }
    // for each specified layer matching pattern
    for (LayerMatch layerMatch : layersPrimaryFirst) {
      if (bCancelling) break;
      
      layerMatch.setNullBooleans(); // to ensure if()s below don't need to check for null
      Layer layer = schema.getLayer(layerMatch.getId());
      Integer layer_id = (Integer)(layer.get("layer_id"));
      String sExtraMetaCondition = "";
      Object[] oLayerId = { layer_id, "_" + iWordColumn };
      
      boolean bPhraseLayer = IsPhraseLayer(layer, schema);
      boolean bSpanLayer = IsSpanLayer(layer, schema);
        
      // can we assume that the annotations are anchored to words?
      boolean bWordAnchoredMetaLayer = layerIsSpanningAndWordAnchored.test(layer);
      
      if ((bPhraseLayer || bSpanLayer)
          // if spanning layer is anchored to words, we don't need conditions that confirm
          // the absence of preceding/following words in the span
          && !bWordAnchoredMetaLayer) {
        if (layerMatch.getAnchorStart()) {
          if (bPhraseLayer) {
            sExtraMetaCondition += fmtSqlStartMetaSpanCondition
              .format(oLayerId);
          } else {
            sExtraMetaCondition += fmtSqlStartFreeformSpanCondition
              .format(oLayerId);
          }
        }
        if (layerMatch.getAnchorEnd()) {
          if (bPhraseLayer) {
            sExtraMetaCondition += fmtSqlEndMetaSpanCondition
              .format(oLayerId);
          } else {
            sExtraMetaCondition += fmtSqlEndFreeformSpanCondition
              .format(oLayerId);
          }
        }
      }
      boolean bSegmentLayer = IsSegmentLayer(layer, schema);
      
      // text substitution args
      Object oArgs[] = { 
        layer_id, // table
        layerMatch.getNot()?"NOT":"", // [NOT] REGEXP 
        Integer.valueOf( // case sensitivity
          layer.getType().equals(Constants.TYPE_IPA) // ... for DISC
          || layer.getType().equals(Constants.TYPE_SELECT)?1:0), // ... and 'select' layers
        layer.getType().equals(Constants.TYPE_IPA)?"":" ", // segment seperator
        sExtraMetaCondition, // e.g. anchoring to the start/end of a span
        (Integer)(targetSegmentLayer.isPresent()?targetSegmentLayer.get().get("layer_id"):null),
        "_" + iWordColumn
      };
      
      if (LayerMatch.HasPattern(layerMatch)) {
        if (layerMatch.getMin() == null && layerMatch.getMax() != null) { // max only
          if (bPhraseLayer || bSpanLayer) {
            // meta/freeform layer
            if (layerMatch.getAnchorStart() && bWordAnchoredMetaLayer) {
              // anchored to start of word
              sSqlExtraJoinsFirst += START_ANCHORED_NUMERIC_MAX_JOIN.format(oArgs);
            } else if (layerMatch.getAnchorEnd() && bWordAnchoredMetaLayer) {
              // anchored to end of word
              sSqlExtraJoinsFirst += END_ANCHORED_NUMERIC_MAX_JOIN.format(oArgs);
            } else { // un-anchored meta condition
              if (!sSqlExtraJoinsFirst.contains(sSqlWordStartJoin)) {
                sSqlExtraJoinsFirst += sSqlWordStartJoin;
              }
              if (bPhraseLayer) {
                sSqlExtraJoinsFirst += CONTAINING_META_NUMERIC_MAX_JOIN.format(oArgs);
              } else {
                sSqlExtraJoinsFirst += CONTAINING_FREEFORM_NUMERIC_MAX_JOIN.format(oArgs);
              }
            } // un-anchored meta condition
          } else if (bUseWordContainsJoins) {
            // word-containing-segment layer
            if (!sSqlExtraJoinsFirst.contains(sSegmentStartJoin)) {
              sSqlExtraJoinsFirst += sSegmentStartJoin;
            }
            sSqlExtraJoinsFirst += CONTAINING_WORD_NUMERIC_MAX_JOIN.format(oArgs);
          } else {
            sSqlExtraJoinsFirst += NUMERIC_MAX_JOIN.format(oArgs);
          }
          parameters.add(Double.valueOf(layerMatch.getMax()));
        } else if (layerMatch.getMin() != null && layerMatch.getMax() == null) { // min only
          if (bPhraseLayer || bSpanLayer) {
            // meta/freeform layer
            if (layerMatch.getAnchorStart() && bWordAnchoredMetaLayer) {
              // anchored to start of word
              sSqlExtraJoinsFirst += START_ANCHORED_NUMERIC_MIN_JOIN.format(oArgs);
            } else if (layerMatch.getAnchorEnd() && bWordAnchoredMetaLayer) {
              // anchored to end of word
              sSqlExtraJoinsFirst += END_ANCHORED_NUMERIC_MIN_JOIN.format(oArgs);
            } else { // un-anchored meta condition
              if (!sSqlExtraJoinsFirst.contains(sSqlWordStartJoin)) {
                sSqlExtraJoinsFirst += sSqlWordStartJoin;
              }
              if (bPhraseLayer) {
                sSqlExtraJoinsFirst += CONTAINING_META_NUMERIC_MIN_JOIN.format(oArgs);
              } else {
                sSqlExtraJoinsFirst += CONTAINING_FREEFORM_NUMERIC_MIN_JOIN.format(oArgs);
              }
            } // un-anchors meta condition
          } else if (bUseWordContainsJoins) {
            // word-containing-segment layer
            if (!sSqlExtraJoinsFirst.contains(sSegmentStartJoin)) {
              sSqlExtraJoinsFirst += sSegmentStartJoin;
            }
            sSqlExtraJoinsFirst += CONTAINING_WORD_NUMERIC_MIN_JOIN.format(oArgs);
          } else {
            sSqlExtraJoinsFirst += NUMERIC_MIN_JOIN.format(oArgs);
          }
          parameters.add(Double.valueOf(layerMatch.getMin()));
        } else if (layerMatch.getMin() != null && layerMatch.getMax() != null) { // min & max
          if (bPhraseLayer || bSpanLayer) {
            // meta/freeform layer
            if (layerMatch.getAnchorStart() && bWordAnchoredMetaLayer) {
              // anchored to start of word
              sSqlExtraJoinsFirst += START_ANCHORED_NUMERIC_RANGE_JOIN.format(oArgs);
            } else if (layerMatch.getAnchorEnd() && bWordAnchoredMetaLayer) {
              // anchored to end of word
              sSqlExtraJoinsFirst += END_ANCHORED_NUMERIC_RANGE_JOIN.format(oArgs);
            }  else { // un-anchored meta condition
              if (!sSqlExtraJoinsFirst.contains(sSqlWordStartJoin)) {
                sSqlExtraJoinsFirst += sSqlWordStartJoin;
              }
              if (bPhraseLayer) {
                sSqlExtraJoinsFirst += CONTAINING_META_NUMERIC_RANGE_JOIN.format(oArgs);
              } else {
                sSqlExtraJoinsFirst += CONTAINING_FREEFORM_NUMERIC_RANGE_JOIN.format(oArgs);
              }
            } // un-anchored meta condition
          } else if (bUseWordContainsJoins) {
            // word-containing-segment layer
            if (!sSqlExtraJoinsFirst.contains(sSegmentStartJoin)) {
              sSqlExtraJoinsFirst += sSegmentStartJoin;
            }
            sSqlExtraJoinsFirst += CONTAINING_WORD_NUMERIC_RANGE_JOIN.format(oArgs);
          } else {
            sSqlExtraJoinsFirst += NUMERIC_RANGE_JOIN.format(oArgs);
          }
          parameters.add(Double.valueOf(layerMatch.getMin()));
          parameters.add(Double.valueOf(layerMatch.getMax()));
        } else if (layerMatch.getPattern() != null) { // use regexp
          String sSqlExtraJoin = "";
          if (bPhraseLayer || bSpanLayer) {
            if (layer.getAlignment() == Constants.ALIGNMENT_INSTANT
                || layer.getId().equals("noise")
                || layer.getId().equals("comment")) {
              if (!sSqlExtraJoinsFirst.contains(sSqlWordEndJoin)) {
                sSqlExtraJoin += sSqlWordEndJoin;
              }
              if (bPhraseLayer) {
                sSqlExtraJoin += TRAILING_META_REGEXP_JOIN.format(oArgs);
              } else {
                sSqlExtraJoin += TRAILING_FREEFORM_REGEXP_JOIN.format(oArgs);
              }
            } else {
              // meta/freeform layer
              if (layerMatch.getAnchorStart() && bWordAnchoredMetaLayer) {
                // anchored to start of word
                sSqlExtraJoinsFirst += START_ANCHORED_REGEXP_JOIN.format(oArgs);
              } else if (layerMatch.getAnchorEnd() && bWordAnchoredMetaLayer) {
                // anchored to end of word
                sSqlExtraJoinsFirst += END_ANCHORED_REGEXP_JOIN.format(oArgs);
              } else { // un-anchored meta condition
                if (!sSqlExtraJoinsFirst.contains(sSqlWordStartJoin)) {
                  sSqlExtraJoin += sSqlWordStartJoin;
                }
                if (bPhraseLayer) {
                  sSqlExtraJoin += CONTAINING_META_REGEXP_JOIN.format(oArgs);
                } else {
                  sSqlExtraJoin += CONTAINING_FREEFORM_REGEXP_JOIN.format(oArgs);
                }
              } // un-anchored meta condition
            }
          } else if (bSegmentLayer) {
            if (!sSqlExtraJoinsFirst.contains(sSegmentStartJoin)) {
              sSqlExtraJoin += sSegmentStartJoin;
            }
            sSqlExtraJoin += SEGMENT_REGEXP_JOIN.format(oArgs);
          } else if (bUseWordContainsJoins) {
            // word-containing-segment layer
            if (!sSqlExtraJoinsFirst.contains(sSegmentStartJoin)) {
              sSqlExtraJoin += sSegmentStartJoin;
            }
            sSqlExtraJoin += CONTAINING_WORD_REGEXP_JOIN.format(oArgs);
          } else {
            sSqlExtraJoin += REGEXP_JOIN.format(oArgs);
          }
          if (layerMatch.getNot() && ".+".equals(layerMatch.getPattern())) { // NOT EXISTS
            // special case: "NOT .+" means "not anything" - i.e. missing annotations
            sSqlExtraJoin = sSqlExtraJoin
              // change join to be LEFT OUTER...
              .replace("INNER JOIN annotation_layer_"+layer_id,
                       "LEFT OUTER JOIN annotation_layer_"+layer_id)
              // ...or meta join to be LEFT OUTER...
              .replace("INNER JOIN (annotation_layer_"+layer_id,
                       "LEFT OUTER JOIN (annotation_layer_"+layer_id)
              // and remove the pattern match
              .replaceAll("AND (CAST\\()?search_[0-9]+_"+layer_id
                          +"\\.label( AS BINARY\\))? NOT REGEXP (BINARY)? \\?", "");
            // and add a test for NULL to the WHERE clause
            sSqlLayerMatchesFirst += NULL_ANNOTATION_CONDITION.format(oArgs);
          } else { // REGEXP MATCH
            // add implicit ^ and $
            layerMatch.ensurePatternAnchored();
            // save the layer and string
            // for later adding to the parameters of the query
            parameters.add(layerMatch.getPattern());
          }
          sSqlExtraJoinsFirst += sSqlExtraJoin;
        } // use regexp
        
        setStatus(
          "Layer: '" + layer.getId() 
          + "' pattern: " + (layerMatch.getNot()?"NOT":"")
          + " " + layerMatch.getPattern()
          + " " + layerMatch.getMin() + " " + layerMatch.getMax()
          + " " + (layerMatch.getAnchorStart()?"[":"")
          + " " + (layerMatch.getAnchorEnd()?"]":""));
	       
        // for export filename TODO move this to Matrix
        if (layerMatch.getNot()) {
          description += "_NOT";
        }
        if (layerMatch.getPattern() != null) {
          description += "_" + layerMatch.getPattern();
        }
        if (layerMatch.getMin() != null) {
          description += "_" + layerMatch.getMin();
        }
        if (layerMatch.getMax() != null) {
          description += "_" + layerMatch.getMax();
        }
        if (targetLayerId.equals(layerMatch.getId()) && iWordColumn == iTargetColumn) {
          sSqlExtraFieldsFirst += ", search_" + iTargetLayer
            + ".annotation_id AS target_annotation_id";
          targetExpression = "search_" + iWordColumn + "_" + iTargetLayer
            + ".annotation_id";
          bTargetAnnotation = true;
        }
      } // matching this layer
    } // next layer

    if (bCancelling) throw new Exception("Cancelled.");

    if (targetSegmentLayer.isPresent()) {
      sSqlExtraFieldsFirst += ", search_" + targetSegmentLayer.get().get("layer_id")
        + ".segment_annotation_id AS target_segment_id";
      targetSegmentExpression = "search_0_" + targetSegmentLayer.get().get("layer_id")
        + ".segment_annotation_id";
      targetSegmentOrder = ", search_0_" + targetSegmentLayer.get().get("layer_id")
        + ".ordinal_in_word";
    }

    // start border condition
    String sStartConditionFirst = "";
    if (matrix.getColumns().get(0).getLayers().values().stream()
        .filter(layerMatch -> layerMatch.getAnchorStart()) // start anchored to utterance
        .filter(layerMatch -> schema.getUtteranceLayerId().equals(layerMatch.getId()))
        .findAny().isPresent()) { // start of utterance
      if (!sSqlExtraJoinsFirst.contains(sSqlWordStartJoin)) {
        sSqlExtraJoinsFirst += sSqlWordStartJoin;
      }
      if (!sSqlExtraJoinsFirst.contains(sSqlLineJoin)) {
        sSqlExtraJoinsFirst += sSqlLineJoin;
      }
      if (!sSqlExtraJoinsFirst.contains(sSqlStartLineJoin)) {
        sSqlExtraJoinsFirst += sSqlStartLineJoin;
      }
      sStartConditionFirst = sSqlStartLineCondition;
    } else if (matrix.getColumns().get(0).getLayers().values().stream()
               .filter(layerMatch -> layerMatch.getAnchorStart()) // start anchored to turn
               .filter(layerMatch -> schema.getTurnLayerId().equals(layerMatch.getId()))
               .findAny().isPresent()) { // start of turn
      sStartConditionFirst = sqlStartTurnCondition;
    }
	 
    // is this also the last column?
    if (matrix.getColumns().size() == 1) {   
      // end condition
      if (matrix.getColumns().get(0).getLayers().values().stream()
          .filter(layerMatch -> layerMatch.getAnchorEnd()) // end anchored to utterance
          .filter(layerMatch -> schema.getUtteranceLayerId().equals(layerMatch.getId()))
          .findAny().isPresent()) { // end of utterance
        if (!sSqlExtraJoinsFirst.contains(sSqlWordStartJoin)) {
          sSqlExtraJoinsFirst += sSqlWordStartJoin;
        }
        if (!sSqlExtraJoinsFirst.contains(sSqlLineJoin)) {
          sSqlExtraJoinsFirst += sSqlLineJoin;
        }
        if (!sSqlExtraJoinsFirst.contains(sSqlEndLineJoin)) {
          sSqlExtraJoinsFirst += sSqlEndLineJoin;
        }
        sSqlLayerMatchesFirst += sqlEndLineCondition.format(columnSuffix);
      } else if (matrix.getColumns().get(0).getLayers().values().stream()
                 .filter(layerMatch -> layerMatch.getAnchorEnd()) // end anchored to turn
                 .filter(layerMatch -> schema.getTurnLayerId().equals(layerMatch.getId()))
                 .findAny().isPresent()) { // end of turn
        if (!sSqlExtraJoinsFirst.contains(sSqlEndTurnJoin)) {
          sSqlExtraJoinsFirst += sSqlEndTurnJoin;
        }
        sSqlLayerMatchesFirst += sqlEndTurnCondition.format(columnSuffix);
      }
    } // last column

    // aligned words only?
    if (anchorConfidenceThreshold != null) { // TODO segment threshold too
      if (!sSqlExtraJoinsFirst.contains(sSqlWordStartJoin)) {
        sSqlExtraJoinsFirst += sSqlWordStartJoin
          + " AND word_"+iWordColumn+"_start.alignment_status >= "
          + anchorConfidenceThreshold;
      } else { // update existing join
        sSqlExtraJoinsFirst = sSqlExtraJoinsFirst.replaceAll(
          " ON word_"+iWordColumn+"_start\\.anchor_id = word_"+iWordColumn+"\\.start_anchor_id",
          " ON word_"+iWordColumn+"_start\\.anchor_id = word_"+iWordColumn+"\\.start_anchor_id"
          + " AND word_"+iWordColumn+"_start.alignment_status >= "
          + anchorConfidenceThreshold);
      }
      if (!sSqlExtraJoinsFirst.contains(sSqlWordEndJoin)) {
        sSqlExtraJoinsFirst += sSqlWordEndJoin
          + " AND word_"+iWordColumn+"_end.alignment_status >= "
          + anchorConfidenceThreshold;
      } else { // update existing join
        sSqlExtraJoinsFirst = sSqlExtraJoinsFirst.replaceAll(
          " ON word_"+iWordColumn+"_end\\.anchor_id = word_"+iWordColumn+"\\.end_anchor_id",
          " ON word_"+iWordColumn+"_start\\.end_id = word_"+iWordColumn+"\\.end_anchor_id"
          + " AND word_"+iWordColumn+"_end.alignment_status >= "
          + anchorConfidenceThreshold);
      }
    }

    // access restrictions?
    String strAccessWhere = "";
    if (restrictByUser != null) {
      strAccessWhere = " AND EXISTS (SELECT * FROM role"
        + " INNER JOIN role_permission ON role.role_id = role_permission.role_id" 
        + " INNER JOIN annotation_transcript access_attribute" 
        + " ON access_attribute.layer = role_permission.attribute_name" 
        + " AND access_attribute.label REGEXP role_permission.value_pattern"
        + " AND role_permission.entity REGEXP '.*t.*'" // transcript access
        + " WHERE user_id = ? AND access_attribute.ag_id = turn.ag_id)"
        +" OR EXISTS (SELECT * FROM role"
        + " INNER JOIN role_permission ON role.role_id = role_permission.role_id" 
        + " AND role_permission.attribute_name = 'corpus'" 
        + " AND role_permission.entity REGEXP '.*t.*'" // transcript access
        + " WHERE transcript.corpus_name REGEXP role_permission.value_pattern"
        + " AND user_id = ?)";
      if (sSqlExtraJoinsFirst.indexOf(sSqlTranscriptJoin) < 0) {
        sSqlExtraJoinsFirst += sSqlTranscriptJoin;
      }
    } // filtering by role
    String strSpeakerWhere = participantCondition.apply(matrix.getParticipantQuery());
    if (strSpeakerWhere == null && getLastException() != null) {
      throw (Exception)getLastException();
    }
    
    // now match subsequent columns
    String strSubsequentSelect = "";
    String strSubsequentJoin = "";
    String strSubsequentWhere = "";
    for (
      iWordColumn = 1; 
      iWordColumn < matrix.getColumns().size() && !bCancelling; 
      iWordColumn++) { 
      String sSqlExtraJoins = "";
      String sSqlExtraFields = "";
      String sSqlLayerMatches = "";
      targetSegmentLayer = matrix.getColumns().get(iWordColumn)
        .getLayers().values().stream()
        .filter(LayerMatch::HasPattern)
        .map(layerMatch -> schema.getLayer(layerMatch.getId()))
        .filter(Objects::nonNull)
        .filter(layer -> IsSegmentLayer(layer, schema))
        .findAny();
      Column column = matrix.getColumns().get(iWordColumn);
      
      // do we need a word layer to anchor to?
      Optional<LayerMatch> primaryWordLayer = column.getLayers().values().stream()
        .filter(LayerMatch::HasPattern)
        // not a "NOT .+" expression
        .filter(layerMatch -> !layerMatch.getNot() || !".+".equals(layerMatch.getPattern()))
        .filter(layerMatch -> schema.getLayer(layerMatch.getId()) != null)
        // word scope
        .filter(layerMatch -> schema.getWordLayerId().equals(layerMatch.getId())
                || (schema.getWordLayerId().equals(
                      schema.getLayer(layerMatch.getId()).getParentId())
                    && !layerMatch.getId().equals("segment")))
        .findAny();
      if (!primaryWordLayer.isPresent()) {
        Object oSubPatternMatchArgs[] = { 
          null, // extra JOINs
          null, // border conditions
          null, // regexp/range subqueries
          null, // for line info
          "_" + iWordColumn, // column suffix
          "_" + (iWordColumn-1) // previous column suffix
        };
        sSqlExtraJoins += sqlPatternMatchSubsequentJoin.format(oSubPatternMatchArgs);
      }
      
      columnSuffix[0] = "_" + iWordColumn;
      sSqlWordStartJoin = sqlWordStartJoin.format(columnSuffix);
      sSqlWordEndJoin = sqlWordEndJoin.format(columnSuffix);
      sSegmentStartJoin = sqlSegmentStartJoin.format(columnSuffix);
      sSqlLineJoin = sqlLineJoin.format(columnSuffix);
      sSqlEndLineJoin = sqlEndLineJoin.format(columnSuffix);
      sSqlEndTurnJoin = sqlEndTurnJoin.format(columnSuffix);
      
      // check for segment layer search
      // this affects how we match aligned word layers
      bUseWordContainsJoins = false;
      layersPrimaryFirst.clear();
      column.getLayers().values().stream()
        .filter(LayerMatch::HasPattern)
        // existing layers
        .filter(layerMatch -> schema.getLayer(layerMatch.getId()) != null)
        // temporal layers
        .filter(layerMatch -> schema.getLayer(layerMatch.getId()).containsKey("layer_id"))
        .forEach(layerMatch -> layersPrimaryFirst.add(layerMatch));
      if (primaryWordLayer.isPresent()) {
        // move it to the front
        if (layersPrimaryFirst.remove(primaryWordLayer.get())) { // it was there
          layersPrimaryFirst.add(0, primaryWordLayer.get());
        }
      }
      
      // look for a layer with a search specification
      for (LayerMatch layerMatch : layersPrimaryFirst) {
        if (bCancelling) break;
        layerMatch.setNullBooleans();        
        Layer layer = schema.getLayer(layerMatch.getId());
        Integer layer_id = (Integer)(layer.get("layer_id"));
        String sExtraMetaCondition = "";
        Object[] oLayerId = { (Integer)(layer.get("layer_id")), "_" + iWordColumn };
	
        boolean bPhraseLayer = IsPhraseLayer(layer, schema);
        boolean bSpanLayer = IsSpanLayer(layer, schema);
        
        // can we assume that the annotations are anchored to words?
        boolean bWordAnchoredMetaLayer = layerIsSpanningAndWordAnchored.test(layer);
	    
        if ((bPhraseLayer || bSpanLayer)
            // if spanning layer is anchored to words, we don't need conditions that confirm
            // the absence of preceding/following words in the span
            && !bWordAnchoredMetaLayer) {
          if (layerMatch.getAnchorStart()) {
            if (bPhraseLayer) {
              sExtraMetaCondition += fmtSqlStartMetaSpanCondition.format(oLayerId);
            } else {
              sExtraMetaCondition += fmtSqlStartFreeformSpanCondition.format(oLayerId);
            }
          }
          if (layerMatch.getAnchorEnd()) {
            if (bPhraseLayer) {
              sExtraMetaCondition += fmtSqlEndMetaSpanCondition.format(oLayerId);
            } else {
              sExtraMetaCondition += fmtSqlEndFreeformSpanCondition.format(oLayerId);
            }
          }
        }
        boolean bSegmentLayer = IsSegmentLayer(layer, schema);

        // text substitution args
        Object oArgs[] = { 
          layer_id, // table
          layerMatch.getNot()?"NOT":"", // [NOT] REGEXP 
          Integer.valueOf( // case sensitivity
            layer.getType().equals(Constants.TYPE_IPA) // ... for DISC
            || layer.getType().equals(Constants.TYPE_SELECT)?1:0), // ... and 'select' layers
          layer.getType().equals(Constants.TYPE_IPA)?"":" ", // segment seperator
          sExtraMetaCondition, // e.g. anchoring to the start of a span
          null,
          "_" + iWordColumn
        };
        
        if (LayerMatch.HasPattern(layerMatch)) {
          if (layerMatch.getMin() == null && layerMatch.getMax() != null) { // max only
            if (bPhraseLayer || bSpanLayer) {
              // meta/freeform layer
              if (layerMatch.getAnchorStart() && bWordAnchoredMetaLayer) {
                // anchored to start of word
                sSqlExtraJoins += START_ANCHORED_NUMERIC_MAX_JOIN.format(oArgs);
              } else if (layerMatch.getAnchorEnd() && bWordAnchoredMetaLayer) {
                // anchored to end of word
                sSqlExtraJoins += END_ANCHORED_NUMERIC_MAX_JOIN.format(oArgs);
              } else { // un-anchored meta condition
                if (!sSqlExtraJoins.contains(sSqlWordStartJoin)) {
                  sSqlExtraJoins += sSqlWordStartJoin;
                }
                if (bPhraseLayer) {
                  sSqlExtraJoins += CONTAINING_META_NUMERIC_MAX_JOIN.format(oArgs);
                } else {
                  sSqlExtraJoins += CONTAINING_FREEFORM_NUMERIC_MAX_JOIN.format(oArgs);
                }
              } // un-anchored meta condition
            } else if (bUseWordContainsJoins) {
              // word-containing-segment layer
              if (!sSqlExtraJoins.contains(sSegmentStartJoin)) {
                sSqlExtraJoins += sSegmentStartJoin;
              }
              sSqlExtraJoins += CONTAINING_WORD_NUMERIC_MAX_JOIN.format(oArgs);
            } else {
              sSqlExtraJoins += NUMERIC_MAX_JOIN.format(oArgs);
            }
            parameters.add(Double.valueOf(layerMatch.getMax()));
          } else if (layerMatch.getMin() != null && layerMatch.getMax() == null) { // min only
            if (bPhraseLayer || bSpanLayer) {
              // meta/freeform layer
              if (layerMatch.getAnchorStart() && bWordAnchoredMetaLayer) {
                // anchored to start of word
                sSqlExtraJoins += START_ANCHORED_NUMERIC_MIN_JOIN.format(oArgs);
              } else if (layerMatch.getAnchorEnd() && bWordAnchoredMetaLayer) {
                // anchored to end of word
                sSqlExtraJoins += END_ANCHORED_NUMERIC_MIN_JOIN.format(oArgs);
              } else { // un-anchored meta condition
                if (!sSqlExtraJoins.contains(sSqlWordStartJoin)) {
                  sSqlExtraJoins += sSqlWordStartJoin;
                }
                if (bPhraseLayer) {
                  sSqlExtraJoins += CONTAINING_META_NUMERIC_MIN_JOIN.format(oArgs);
                } else {
                  sSqlExtraJoins += CONTAINING_FREEFORM_NUMERIC_MIN_JOIN.format(oArgs);
                }
              } // un-anchored meta condition
            } else if (bUseWordContainsJoins) {
              // word-containing-segment layer
              if (!sSqlExtraJoins.contains(sSegmentStartJoin)) {
                sSqlExtraJoins += sSegmentStartJoin;
              }
              sSqlExtraJoins += CONTAINING_WORD_NUMERIC_MIN_JOIN.format(oArgs);
            } else {
              sSqlExtraJoins += NUMERIC_MIN_JOIN.format(oArgs);
            }
            parameters.add(Double.valueOf(layerMatch.getMin()));
          } else if (layerMatch.getMin() != null && layerMatch.getMax() != null) { // min&max
            if (bPhraseLayer || bSpanLayer) {
              // meta/freeform layer
              if (layerMatch.getAnchorStart() && bWordAnchoredMetaLayer) {
                // anchored to start of word
                sSqlExtraJoins += START_ANCHORED_NUMERIC_RANGE_JOIN.format(oArgs);
              } else if (layerMatch.getAnchorEnd() && bWordAnchoredMetaLayer) {
                // anchored to end of word
                sSqlExtraJoins += END_ANCHORED_NUMERIC_RANGE_JOIN.format(oArgs);
              } else { // un-anchored meta condition
                if (!sSqlExtraJoins.contains(sSqlWordStartJoin)) {
                  sSqlExtraJoins += sSqlWordStartJoin;
                }
                if (bPhraseLayer) {
                  sSqlExtraJoins += CONTAINING_META_NUMERIC_RANGE_JOIN.format(oArgs);
                } else {
                  sSqlExtraJoins += CONTAINING_FREEFORM_NUMERIC_RANGE_JOIN.format(oArgs);
                }
              } // un-anchored meta condition
            } else if (bUseWordContainsJoins) {
              // word-containing-segment layer
              if (!sSqlExtraJoins.contains(sSegmentStartJoin)) {
                sSqlExtraJoins += sSegmentStartJoin;
              }
              sSqlExtraJoins += CONTAINING_WORD_NUMERIC_RANGE_JOIN.format(oArgs);
            } else {
              sSqlExtraJoins += NUMERIC_RANGE_JOIN.format(oArgs);
            }
            parameters.add(Double.valueOf(layerMatch.getMin()));
            parameters.add(Double.valueOf(layerMatch.getMax()));
          } else if (layerMatch.getPattern() != null) { // use regexp
            String sSqlExtraJoin = "";
            if (bPhraseLayer || bSpanLayer) {
              if (layer.getAlignment() == Constants.ALIGNMENT_INSTANT
                  || layer.getId().equals("noise")
                  || layer.getId().equals("comment")) {
                // meta/freeform layer
                if (!sSqlExtraJoins.contains(sSqlWordEndJoin)) {
                  sSqlExtraJoin += sSqlWordEndJoin;
                }
                sSqlExtraJoin += TRAILING_META_REGEXP_JOIN.format(oArgs);
              } else {
                // meta/freeform layer
                if (layerMatch.getAnchorStart() && bWordAnchoredMetaLayer) {
                  // anchored to start of word
                  sSqlExtraJoin += START_ANCHORED_REGEXP_JOIN.format(oArgs);
                } else if (layerMatch.getAnchorEnd() && bWordAnchoredMetaLayer) {
                  // anchored to end of word
                  sSqlExtraJoin += END_ANCHORED_REGEXP_JOIN.format(oArgs);
                }  else { // un-anchored meta condition
                  if (!sSqlExtraJoins.contains(sSqlWordStartJoin)) {
                    sSqlExtraJoin += sSqlWordStartJoin;
                  }
                  if (bPhraseLayer) {
                    sSqlExtraJoin += CONTAINING_META_REGEXP_JOIN.format(oArgs);
                  } else {
                    sSqlExtraJoin += CONTAINING_FREEFORM_REGEXP_JOIN.format(oArgs);
                  }
                } // un-anchored meta condition
              }
            } else if (bSegmentLayer) {
              if (!sSqlExtraJoins.contains(sSegmentStartJoin)) {
                sSqlExtraJoin += sSegmentStartJoin;
              }
              sSqlExtraJoin += SEGMENT_REGEXP_JOIN.format(oArgs);
            } else if (bUseWordContainsJoins) {
              // word-containing-segment layer
              if (!sSqlExtraJoins.contains(sSegmentStartJoin)) {
                sSqlExtraJoin += sSegmentStartJoin;
              }
              sSqlExtraJoin += CONTAINING_WORD_REGEXP_JOIN.format(oArgs);
            } else {
              sSqlExtraJoin += REGEXP_JOIN.format(oArgs);
            }
            if (layerMatch.getNot() && ".+".equals(layerMatch.getPattern())) { // NOT EXISTS
              // special case: "NOT .+" means "not anything" - i.e. missing annotations
              sSqlExtraJoin = sSqlExtraJoin
                // change join to be LEFT OUTER...
                .replace("INNER JOIN annotation_layer_"+layer_id,
                         "LEFT OUTER JOIN annotation_layer_"+layer.getId())
                // ...or meta join to be LEFT OUTER...
                .replace("INNER JOIN (annotation_layer_"+layer_id,
                         "LEFT OUTER JOIN (annotation_layer_"+layer.getId())
                // and remove the pattern match
                .replaceAll("AND (CAST\\()?search_[0-9]+_"+layer_id
                            +"\\.label( AS BINARY\\))? NOT REGEXP (BINARY)? \\?", "");
              // and add a test for NULL to the WHERE clause
              sSqlLayerMatches += NULL_ANNOTATION_CONDITION.format(oArgs);
            } else { // REGEXP MATCH
              // regexp - add implicit ^ and $
              layerMatch.ensurePatternAnchored();
              // save the layer and string
              // for later adding to the parameters of the query
              parameters.add(layerMatch.getPattern());
            }
            sSqlExtraJoins += sSqlExtraJoin;
          }
          
          if (layer.getId().equals(targetLayerId) && iWordColumn == iTargetColumn) {
            sSqlExtraFields += ", search_"+iWordColumn+"_" + iTargetLayer
              + ".annotation_id AS target_annotation_id";
            targetExpression = "search_"+iWordColumn+"_" + iTargetLayer
              + ".annotation_id";
            bTargetAnnotation = true;
          }
          
          setStatus(
            "Layer: '" + layer.getId() 
            + "' pattern: " + (layerMatch.getNot()?"NOT":"")
            + " " + layerMatch.getPattern()
            + " " + layerMatch.getMin() + " " + layerMatch.getMax()
            + " " + (layerMatch.getAnchorStart()?"[":"")
            + " " + (layerMatch.getAnchorEnd()?"]":""));
          
          // for export filename
          if (layerMatch.getNot()) {
            description += "_NOT";
          }
          if (layerMatch.getPattern() != null) {
            description += "_" + layerMatch.getPattern();
          }
          if (layerMatch.getMin() != null) {
            description += "_" + layerMatch.getMin();
          }
          if (layerMatch.getMax() != null) {
            description += "_" + layerMatch.getMax();
          }
        } // matching this layer
      } // next layer
      if (bCancelling) throw new Exception("Cancelled.");
      
      // aligned words only?
      if (anchorConfidenceThreshold != null) { // TODO segment threshold too
        if (!sSqlExtraJoins.contains(sSqlWordStartJoin)) {
          sSqlExtraJoins += sSqlWordStartJoin
            + " AND word_"+iWordColumn+"_start.alignment_status >= "
            + anchorConfidenceThreshold;
        } else { // update existing join
          sSqlExtraJoins = sSqlExtraJoins.replaceAll(
            " ON word_"+iWordColumn+"_start\\.anchor_id = word_"+iWordColumn+"\\.start_anchor_id",
            " ON word_"+iWordColumn+"_start\\.anchor_id = word_"+iWordColumn+"\\.start_anchor_id"
            + " AND word_"+iWordColumn+"_start.alignment_status >= "
            + anchorConfidenceThreshold);
        }
        if (!sSqlExtraJoins.contains(sSqlWordEndJoin)) {
          sSqlExtraJoins += sSqlWordEndJoin
            + " AND word_"+iWordColumn+"_end.alignment_status >= "
            + anchorConfidenceThreshold;
        } else { // update existing join
          sSqlExtraJoins = sSqlExtraJoins.replaceAll(
            " ON word_"+iWordColumn+"_end\\.anchor_id = word_"+iWordColumn+"\\.end_anchor_id",
            " ON word_"+iWordColumn+"_start\\.end_id = word_"+iWordColumn+"\\.end_anchor_id"
            + " AND word_"+iWordColumn+"_end.alignment_status >= "
            + anchorConfidenceThreshold);
        }
      }
      
      // is this the last column?
      String sBorderCondition = "";
      if (iWordColumn == matrix.getColumns().size() - 1) {
        // end condition
        if (matrix.getColumns().get(matrix.getColumns().size() - 1).getLayers().values().stream()
            .filter(layerMatch -> layerMatch.getAnchorEnd()) // end anchored to utterance
            .filter(layerMatch -> schema.getUtteranceLayerId().equals(layerMatch.getId()))
            .findAny().isPresent()) { // end of utterance
          if (!sSqlExtraJoins.contains(sSqlWordStartJoin)) {
            sSqlExtraJoins += sSqlWordStartJoin;
          }
          if (!sSqlExtraJoins.contains(sSqlLineJoin)) {
            sSqlExtraJoins += sSqlLineJoin;
          }
          if (!sSqlExtraJoins.contains(sSqlEndLineJoin)) {
            sSqlExtraJoins += sSqlEndLineJoin;
          }
          sBorderCondition = sqlEndLineCondition.format(columnSuffix);
        } else if (matrix.getColumns().get(matrix.getColumns().size() - 1).getLayers().values()
                   .stream()
                   .filter(layerMatch -> layerMatch.getAnchorEnd()) // end anchored to turn
                   .filter(layerMatch -> schema.getTurnLayerId().equals(layerMatch.getId()))
                   .findAny().isPresent()) { // end of turn
          if (!sSqlExtraJoins.contains(sSqlEndTurnJoin)) {
            sSqlExtraJoins += sSqlEndTurnJoin;
          }
          sBorderCondition = sqlEndTurnCondition.format(columnSuffix);          
        }
      } // last column
      
      if (targetSegmentLayer.isPresent()) {
        sSqlExtraFields += ", search"+iWordColumn+"_" + targetSegmentLayer.get().get("layer_id")
          + ".segment_annotation_id AS target_segment_id";
        targetSegmentExpression
          = "search_"+iWordColumn+"_" + targetSegmentLayer.get().get("layer_id")
          + ".segment_annotation_id"; 
        targetSegmentOrder =
          ", search_"+iWordColumn+"_" + targetSegmentLayer.get().get("layer_id")
          + ".ordinal_in_word";
      }
      
      Object oSubPatternMatchArgs[] = { 
        sSqlExtraJoins, // extra JOINs
        sBorderCondition, // border conditions
        sSqlLayerMatches, // regexp/range subqueries
        sSqlExtraFields, // for line info
        "_" + iWordColumn, // column suffix
        "_" + (iWordColumn-1) // previous column suffix
      };
      
      strSubsequentSelect += sqlPatternMatchSubsequentSelect.format(oSubPatternMatchArgs);
      strSubsequentJoin += sSqlExtraJoins;
      strSubsequentWhere += sqlPatternMatchSubsequentWhere.format(oSubPatternMatchArgs);
      
      setStatus("Adjacency for col " + iWordColumn + " is: " + column.getAdj());
    } // next column
    
    if (mainParticipantOnly) {
      sSqlExtraJoinsFirst += sSqlTranscriptSpeakerJoin;
    }
    
    String sTranscriptCondition = transcriptCondition.apply(matrix.getTranscriptQuery());
    if (sTranscriptCondition == null && getLastException() != null) {
      throw (Exception)getLastException();
    }
    if (sTranscriptCondition.length() > 0) {
      if (sSqlExtraJoinsFirst.indexOf(sSqlTranscriptJoin) < 0) {
        sSqlExtraJoinsFirst += sSqlTranscriptJoin;
      }
    }
    
    Object oPatternMatchArgs[] = { 
      sSqlExtraJoinsFirst, // extra JOINs
      sStartConditionFirst, // border conditions
      sSqlLayerMatchesFirst, // regexp/range subqueries
      sSqlExtraFieldsFirst, // for line info
      strSpeakerWhere, // speakers
      sTranscriptCondition, // transcripts
      mainParticipantOnly? strMainParticipantClause : "", // main
      strAccessWhere, // user-based access restrictions
      strSubsequentSelect,
      strSubsequentJoin,
      strSubsequentWhere,
      "_" + (matrix.getColumns().size() - 1), // last column suffix
      targetSegmentExpression, // segment_annotation_id expression
      targetExpression, // target_annotation_id expression
      (targetLayer==null? "w"
       :targetLayer.getParentId() == null
       || targetLayer.getParentId().equals(schema.getRoot().getId())? ""
       :targetLayer.get("scope").toString().toLowerCase()),// target scope (lowercase)
      targetLayer==null?0:targetLayer.get("layer_id"), // target layer_id
      maxMatches>0?"LIMIT " + maxMatches:"",
      targetSegmentOrder
    };
    
    // create sql query
    String q = sqlPatternMatchFirst.format(oPatternMatchArgs);
    //setStatus(q);
    // now, a number of expressions are expressed in terms of "word_c" where they need to be
    // "search_c_l", so correct that now...
    for (int c = 0; c < matrix.getColumns().size(); c++) {
      Column column = matrix.getColumns().get(c);
      if (firstPrimaryWordLayer.isPresent()) {
        // (if there's no word-based matching, leave default join)        
        // fix segment join
        q = q.replaceAll(
          "segment_"+c+" ON segment_"+c+"\\.word_annotation_id = word_"+c+"\\.annotation_id",
          "segment_"+c+" ON segment_"+c+".turn_annotation_id = turn.annotation_id");
      }
      Optional<Integer> primaryWordLayerId = column.getLayers().values().stream()
        .filter(LayerMatch::HasPattern)
        // not a "NOT .+" expression
        .filter(layerMatch -> !layerMatch.getNot() || !".+".equals(layerMatch.getPattern()))
        .filter(layerMatch -> schema.getLayer(layerMatch.getId()) != null)
        // word scope
        .filter(layerMatch -> schema.getWordLayerId().equals(layerMatch.getId())
                || (schema.getWordLayerId().equals(
                      schema.getLayer(layerMatch.getId()).getParentId())
                    && !layerMatch.getId().equals("segment")))
        .map(layerMatch -> schema.getLayer(layerMatch.getId()))
        .filter(Objects::nonNull)
        .map(layer -> (Integer)layer.get("layer_id"))
        .filter(Objects::nonNull)
        .findAny();
      int l = primaryWordLayerId.orElse(-999);
      if (l >= 0) {
        // fix join
        q = q.replaceAll(
          "search_"+c+"_"+l+"  ON search_"+c+"_"+l+"\\.word_annotation_id = word_"+c+"\\.word_annotation_id",
          "search_"+c+"_"+l+"  ON search_"+c+"_"+l+".turn_annotation_id = turn.annotation_id");
        
        // fix mentions
        q = q.replaceAll(
          " word_"+c+"\\.",
          " search_"+c+"_"+l+".");
        q = q.replaceAll(
          " word_"+c+" ",
          " search_"+c+"_"+l+" ");
      }
    } // next column
    if (targetLayer == null) {
      // fix target fields
      q = q.replaceAll(
        "\\.annotation_id AS target_annotation_id",
        ".word_annotation_id AS target_annotation_id");
      q = q.replaceAll(
        "\\.annotation_id\\) AS target_annotation_uid",
        ".word_annotation_id) AS target_annotation_uid");
    }
    return q;
  } // end of generateSql()
  
  /**
   * Completes a layered search by first identifying the first 'column' of matches
   * (i.e. words that match the first pattern) then matching the results against
   * subsequent 'columns' until what remains are matches for the entire matrix. 
   * @throws Exception
   */
  protected void search() throws Exception {

    iPercentComplete = 1;
    Connection connection = store.getConnection();
    final Schema schema = store.getSchema();

    // word columns
	 
    // // list of Word objects that match matrix
    results = new SqlSearchResults(this);
	 
    long searchTime = new java.util.Date().getTime();

    // the participant condition is a list of turn labels, which are speaker_numbers
    UnaryOperator<String> participantCondition = participantQuery -> {
      if (participantQuery != null && participantQuery.trim().length() > 0) {
        setStatus("Identifying participants...");
        try {
          String[] participantIds = store.getMatchingParticipantIds(participantQuery);
          if (participantIds.length == 0) { // bail out by returning null & setting last exception
            setLastException(
              new Exception("Participant expression matches no participants: "
                            + participantQuery));
            return null; // indicate an error
          }
          
          // participantIds contains names, but we need speaker_numbers
          PreparedStatement selectSpeaker = connection.prepareStatement(
            "SELECT speaker_number FROM speaker WHERE name = ?");
          StringBuilder speakerWhere = new StringBuilder();
          try {
            for (String name : participantIds) {
              selectSpeaker.setString(1, name);
              ResultSet rstSpeaker = selectSpeaker.executeQuery();
              if (rstSpeaker.next()) {
                if (speakerWhere.length() == 0) {
                  speakerWhere.append(
                    " AND turn.label REGEXP '^[0-9]+$' AND CAST(turn.label AS SIGNED) IN (");
                } else {
                  speakerWhere.append(",");
                }
                speakerWhere.append(rstSpeaker.getString("speaker_number"));
              } // speaker is there
              rstSpeaker.close();
            }
            speakerWhere.append(")");
          } finally {
            selectSpeaker.close();
          }
          setStatus("Participant count: " + participantIds.length);
          return speakerWhere.toString();
        } catch (SQLException x) {
          setLastException(x);
          return null;
        } catch (StoreException x) {
          setLastException(x);
          return null;
        } catch (PermissionException x) {
          setLastException(x);
          return null;
        }
      } // participantQuery
      return "";
    };

    // the transcript condition uses a modified version of the 
    UnaryOperator<String> transcriptCondition = transcriptQuery -> {
      if (transcriptQuery != null && transcriptQuery.length() > 0) {
        try {
          // check there are some matches
          if (store.countMatchingTranscriptIds(transcriptQuery) == 0) {
            // bail out by returning null and setting last exception
            setLastException(
              new Exception("Transcript query matches no transcripts: " + transcriptQuery));
            return null;
          }
          GraphAgqlToSql transformer = new GraphAgqlToSql(store.getSchema());
          GraphAgqlToSql.Query q = transformer.sqlFor(
            transcriptQuery, "transcript.transcript_id", null, null, null);
          return q.sql
          .replaceFirst("^SELECT transcript.transcript_id FROM transcript WHERE ", " AND ")
          .replaceFirst(" ORDER BY transcript.transcript_id$","");
        } catch (StoreException x) {
          setLastException(x);
          return null;
        } catch (PermissionException x) {
          setLastException(x);
          return null;
        }
      } // transcriptQuery
      return "";
    };

    // function to detect whether spanning layers are anchored to words or not
    final PreparedStatement sqlAnnotationCount = connection.prepareStatement(
      "SELECT COUNT(*) FROM annotation_layer_?");
    final PreparedStatement sqlAnchoredToWordStartCount = connection.prepareStatement(
      "SELECT COUNT(*) FROM annotation_layer_? annotation"
      +" INNER JOIN annotation_layer_"+SqlConstants.LAYER_TRANSCRIPTION+" word"
      +" ON annotation.start_anchor_id = word.start_anchor_id");
    final PreparedStatement sqlAnchoredToWordEndCount = connection.prepareStatement(
      "SELECT COUNT(*) FROM annotation_layer_? annotation"
      +" INNER JOIN annotation_layer_"+SqlConstants.LAYER_TRANSCRIPTION+" word"
      +" ON annotation.end_anchor_id = word.end_anchor_id");
    Predicate<Layer> layerIsSpanningAndWordAnchored = layer -> {
      if (IsPhraseLayer(layer, schema) || IsSpanLayer(layer, schema)) {
        try {
          Integer layer_id = (Integer)layer.get("layer_id");
          sqlAnnotationCount.setInt(1, layer_id);
          ResultSet rsCount = sqlAnnotationCount.executeQuery();
          rsCount.next();
          int annotationCount = rsCount.getInt(1);
          rsCount.close();
          sqlAnchoredToWordStartCount.setInt(1, layer_id);
          rsCount = sqlAnchoredToWordStartCount.executeQuery();
          rsCount.next();
          int anchoredToWordStartCount = rsCount.getInt(1);
          rsCount.close();
          sqlAnchoredToWordEndCount.setInt(1, layer_id);
          rsCount = sqlAnchoredToWordEndCount.executeQuery();
          rsCount.next();
          int anchoredToWordEndCount = rsCount.getInt(1);
          rsCount.close();
          if (annotationCount == anchoredToWordStartCount
              && annotationCount == anchoredToWordEndCount) {
            return true;
          }
        } catch (SQLException x) {
          setLastException(x);
        }
      }
      return false;
    };

    // generate an SQL statement from the search matrix
    String q = null;
    // Parameter values
    Vector<Object> parameters = new Vector<Object>(); 
    try {
      setStatus("Creating query...");
      q = generateSql(parameters, schema,
                      layerIsSpanningAndWordAnchored, participantCondition, transcriptCondition);
    } catch (Exception x) {
      setStatus(x.getMessage());
      setLastException(x);
      return;
    } finally {
      sqlAnnotationCount.close();
      sqlAnchoredToWordStartCount.close();
      sqlAnchoredToWordEndCount.close();
    }
    setStatus("Finding matches...");    
    
    // Create temporary table so that multiple users can query at once without locking each other
    PreparedStatement sqlPatternMatch = connection.prepareStatement(
      "CREATE TEMPORARY TABLE _result ( "
      +" search_id INTEGER UNSIGNED NOT NULL, "
      +" match_id INTEGER UNSIGNED NOT NULL AUTO_INCREMENT,"
      +" ag_id INTEGER UNSIGNED NOT NULL,"
      +" speaker_number INTEGER UNSIGNED NOT NULL,"
      +" start_anchor_id INTEGER UNSIGNED NOT NULL,"
      +" end_anchor_id INTEGER UNSIGNED NOT NULL,"
      +" defining_annotation_id INTEGER UNSIGNED NULL,"
      +" segment_annotation_id INTEGER UNSIGNED NULL,"
      +" target_annotation_id INTEGER UNSIGNED NULL,"
      +" turn_annotation_id INTEGER UNSIGNED NULL,"
      +" first_matched_word_annotation_id INTEGER UNSIGNED NULL,"
      +" last_matched_word_annotation_id INTEGER UNSIGNED NULL,"
      +" complete BIT NULL,"
      +" target_annotation_uid VARCHAR(20) NULL,"
      +" PRIMARY KEY  (search_id, match_id),"
      +" INDEX IDX_UID (search_id, target_annotation_uid)"
      +") ENGINE=MyISAM;");
    sqlPatternMatch.executeUpdate();
    sqlPatternMatch.close();
    
    //setStatus(q);
    sqlPatternMatch = connection.prepareStatement(q);
    
    // set the layer search parameters
    int iLayerParameter = 1;     
    sqlPatternMatch.setLong(iLayerParameter++, ((SqlSearchResults)results).getId());      

    // set parameter values
    for (Object parameter : parameters) {
      if (parameter instanceof Double) {
        sqlPatternMatch.setDouble(
          iLayerParameter++, (Double)parameter);
      } else {
        sqlPatternMatch.setString(iLayerParameter++, parameter.toString());
      }
    }
    
    if (restrictByUser != null) {
      sqlPatternMatch.setString(iLayerParameter++, restrictByUser);
      sqlPatternMatch.setString(iLayerParameter++, restrictByUser);
    }
    for (int iWordColumn = 1; iWordColumn < matrix.getColumns().size(); iWordColumn++) {
      sqlPatternMatch.setInt(
        iLayerParameter++, matrix.getColumns().get(iWordColumn).getAdj());
    } // next adjacency
    
    iPercentComplete = SQL_STARTED_PERCENT; 
    setStatus("Querying...");
    if (!bCancelling) executeUpdate(sqlPatternMatch); 
    sqlPatternMatch.close();
    iPercentComplete = SQL_FINISHED_PERCENT;

    setStatus("Query complete, collating results...");
    sqlPatternMatch = connection.prepareStatement(
      "CREATE TEMPORARY TABLE _result_copy ( "
      +" search_id INTEGER UNSIGNED NOT NULL, "
      +" match_id INTEGER UNSIGNED NOT NULL AUTO_INCREMENT,"
      +" ag_id INTEGER UNSIGNED NOT NULL,"
      +" speaker_number INTEGER UNSIGNED NOT NULL,"
      +" start_anchor_id INTEGER UNSIGNED NOT NULL,"
      +" end_anchor_id INTEGER UNSIGNED NOT NULL,"
      +" defining_annotation_id INTEGER UNSIGNED NULL,"
      +" segment_annotation_id INTEGER UNSIGNED NULL,"
      +" target_annotation_id INTEGER UNSIGNED NULL,"
      +" turn_annotation_id INTEGER UNSIGNED NULL,"
      +" first_matched_word_annotation_id INTEGER UNSIGNED NULL,"
      +" last_matched_word_annotation_id INTEGER UNSIGNED NULL,"
      +" complete BIT NULL,"
      +" target_annotation_uid VARCHAR(20) NULL,"
      +" PRIMARY KEY  (search_id, match_id),"
      +" INDEX IDX_UID (search_id, target_annotation_uid)"
      +") ENGINE=MyISAM;");
    if (!bCancelling) executeUpdate(sqlPatternMatch);
    sqlPatternMatch.close();
    sqlPatternMatch = connection.prepareStatement(
      "INSERT INTO _result_copy SELECT * FROM _result");
    if (!bCancelling) executeUpdate(sqlPatternMatch);
    sqlPatternMatch.close();

    sqlPatternMatch = connection.prepareStatement(
      "DELETE dup.*"
      +" FROM _result dup"
      +" INNER JOIN _result_copy orig"
      +" ON dup.search_id = orig.search_id AND dup.target_annotation_uid = orig.target_annotation_uid"
      +" AND dup.match_id > orig.match_id"
      +" WHERE dup.search_id = ?");
    sqlPatternMatch.setLong(1, ((SqlSearchResults)results).getId());      
    if (!bCancelling) executeUpdate(sqlPatternMatch);
    sqlPatternMatch.close();

    sqlPatternMatch = connection.prepareStatement(
      "DROP TABLE _result_copy");
    executeUpdate(sqlPatternMatch);
    sqlPatternMatch.close();
      

    iPercentComplete = 92;
    
    // set defining annotation and its anchors, and sort the results by speaker and transcript...
    
    // copy the incomplete results back into the table, sorting as we go      
    sqlPatternMatch = connection.prepareStatement(
      "INSERT INTO result"
      +" (search_id,ag_id,speaker_number,"
      +" start_anchor_id,end_anchor_id,defining_annotation_id,"
      +" segment_annotation_id,target_annotation_id,first_matched_word_annotation_id,"
      +" last_matched_word_annotation_id,complete,target_annotation_uid)"
      +" SELECT unsorted.search_id,unsorted.ag_id,unsorted.speaker_number,"
      +" line_start.anchor_id,line_end.anchor_id,line.annotation_id,"
      +" unsorted.segment_annotation_id,unsorted.target_annotation_id,"
      +" unsorted.first_matched_word_annotation_id,unsorted.last_matched_word_annotation_id,"
      +" 1,unsorted.target_annotation_uid"
      +" FROM _result unsorted"
      +" INNER JOIN transcript ON unsorted.ag_id = transcript.ag_id"
      +" INNER JOIN speaker ON unsorted.speaker_number = speaker.speaker_number"
      +" INNER JOIN anchor word_start ON unsorted.start_anchor_id = word_start.anchor_id"
      +" INNER JOIN (annotation_layer_"+SqlConstants.LAYER_UTTERANCE + " line" 
      +"  INNER JOIN anchor line_start"
      +"  ON line_start.anchor_id = line.start_anchor_id"
      +"  INNER JOIN anchor line_end"
      +"  ON line_end.anchor_id = line.end_anchor_id)"
      +" ON line.turn_annotation_id = unsorted.turn_annotation_id"
      // line bounds are outside word bounds...
      +"  AND line_start.offset <= word_start.offset"
      +"  AND line_end.offset > word_start.offset"
      +" WHERE unsorted.search_id = ? AND complete = 0"
      +" ORDER BY speaker.name, transcript.transcript_id, unsorted.match_id");
    sqlPatternMatch.setLong(1, ((SqlSearchResults)results).getId());      
    if (!bCancelling) executeUpdate(sqlPatternMatch);
    sqlPatternMatch.close();

    // delete the unsorted results
    sqlPatternMatch = connection.prepareStatement(
      "DROP TABLE _result");
    executeUpdate(sqlPatternMatch);
    sqlPatternMatch.close();
    
    iPercentComplete = 95;
      
    results.reset();
    results.hasNext(); // force it to recheck the database to get size etc.
    filterResults(); // exclude simultaneous speech, etc.

    int size = results.size();
    setStatus("There " + (size==1?"was ":"were ") + size + (size==1?" match":" matches")
              + " in " + (((getDuration()/1000)+30)/60)
              + " minutes [" + getDuration() + "ms]");
    
    iPercentComplete = 100;
    
  }

  /**
   * Removes results that should be excluded; e.g. simultaneous speech as per the
   * {@link #overlapThreshold} attribute, and subsequent utterances if
   * {@link #matchesPerTranscript} is set.
   */
  protected void filterResults() throws Exception {
    if (matchesPerTranscript != null || overlapThreshold != null) {
      
      if (overlapThreshold == null) {
        setStatus(
          "Return only first "
          + (matchesPerTranscript.intValue() == 1 ?" result"
             :" "+matchesPerTranscript + " results")
          +" per transcript");
      } else if (matchesPerTranscript == null) {
        setStatus(
          "Exclude utterances that overlap more than " + overlapThreshold + "%"
          +" with another utterance");
      } else {
        setStatus(
          "Exclude utterances that overlap more than " + overlapThreshold + "%"
          +" with another utterance and include only first "
          + (matchesPerTranscript.intValue() == 1 ?" result"
             :" "+matchesPerTranscript + " results")
          +" per transcript");
      }
      Integer currentGraphId = null;
      HashMap<Integer,String> graphIds = new HashMap<Integer,String>();
      int soFarThisGraph = 0;
      results.reset();
      int matchNumber = 1;
      while (results.hasNext()) {
        IdUtterance match = new IdUtterance(results.next());
        boolean keepMatch = true;
        if (overlapThreshold != null) {
          if (!graphIds.containsKey(match.getGraphId())) { // get transcript name
            // get the graph using the ag_id
            Graph transcript = store.getTranscript(""+match.getGraphId());
            graphIds.put(match.getGraphId(), transcript.getId());
          }
          String[] utteranceAnchorIds = {
            "n_" + match.getStartAnchorId(), "n_" + match.getEndAnchorId()
          };
          Anchor[] utteranceAnchors = store.getAnchors(
            graphIds.get(match.getGraphId()), utteranceAnchorIds);
          if (utteranceAnchors[0].getOffset() == null
              || utteranceAnchors[1].getOffset() == null) { // not anchored, skip this one
            continue;
          }
          double dAnnotationDuration
            = utteranceAnchors[1].getOffset() - utteranceAnchors[0].getOffset();
          // get all utterances that overlap with this one (including this one)
          String query = "graph.id == '"
            +graphIds.get(match.getGraphId()).replace("\\","\\\\").replace("'","\\'")+"'"
            +" && layer.id == 'utterance'"
            +" && start.offset <= " + utteranceAnchors[1].getOffset()
            +" && end.offset >= " + utteranceAnchors[0].getOffset();
          Annotation[] overlappingUtterances = store.getMatchingAnnotations(query);
          // collect them all into a graph for comparing anchor offsets
          Graph g = new Graph();
          g.addAnchor(utteranceAnchors[0]);
          g.addAnchor(utteranceAnchors[1]);
          HashSet<String> anchorIds = new HashSet<String>();
          for (nzilbb.ag.Annotation other : overlappingUtterances) {
            g.addAnnotation(other);
            anchorIds.add(other.getStartId());
            anchorIds.add(other.getEndId());
          }
          Anchor[] anchors = store.getAnchors(
            graphIds.get(match.getGraphId()),
            anchorIds.toArray(new String[anchorIds.size()]));
          for (Anchor anchor : anchors) g.addAnchor(anchor);
          
          // look for an overlap that's greater than the threshold
          Annotation utterance = g.getAnnotation(match.getDefiningAnnotationUid());
          for (nzilbb.ag.Annotation other : overlappingUtterances) {
            if (other == utterance) continue; // skip this utterance
            double dOverlap = -other.distance(utterance);
            if (dOverlap / dAnnotationDuration > overlapThreshold / 100.0) {
              keepMatch = false;
              break;
            } // overlap over threshold
          } // next overlapping utterance
          // only keep the utterances that don't count as overlapping
        } // overlapThreshold is set
            
        if (matchesPerTranscript != null) {
          if (!match.getGraphId().equals(currentGraphId)) { // new graph
            currentGraphId = match.getGraphId();
            soFarThisGraph = 0;
          }
          if (soFarThisGraph < matchesPerTranscript) { // keep the first perTranscript matches
            soFarThisGraph++;
          } else {
            keepMatch = false;
          }
        }

        if (!keepMatch) { // remove it from database
          results.remove();
        }
      } // next match
      results.reset(); // recompute size, etc
    }
  } // end of filterResults()

  /**
   * Somebody is still interested in the thread, so keep it from dying.
   */
  public void keepAlive() {
    super.keepAlive();

    // if we're running a query, give the illusion of progress..
    if (getRunning() &&
        iPercentComplete >= SQL_STARTED_PERCENT
        && iPercentComplete < SQL_FINISHED_PERCENT) {
      // move forward a quarter of what's left
      int percentLeft = SQL_FINISHED_PERCENT - iPercentComplete;
      int aThird = percentLeft / 8;
      iPercentComplete += aThird;
    }
  } // end of keepAlive()
  
  private PreparedStatement currentUpdate = null;   
  /**
   * Executes a cancellable update statement.
   * @param sql
   * @throws SQLException
   */
  private void executeUpdate(PreparedStatement sql) throws SQLException {
    currentUpdate = sql;
    try {
      sql.executeUpdate();
    } finally {
      currentUpdate = null;
    }
  } // end of executeUpdate()
  /**
   * Override to allow update-statement cancellation
   */
  public void cancel() {
    super.cancel();
    if (currentUpdate != null) {
      try {currentUpdate.cancel(); } catch(Throwable t) {}
    }
  }
  
  /**
   * Determines whether the given layer is a 'segment' layer.
   * @param layer The layer to test.
   * @param schema The schema the layer comes from.
   * @return true if the given layer is the "segment" layer or a child of it.
   */
  public static boolean IsSegmentLayer(Layer layer, Schema schema) {
    return ("segment".equals(layer.getId())
            && schema.getWordLayerId().equals(layer.getParentId()))
      || "segment".equals(layer.getParentId());
  } // end of IsSegmentLayer()
  
  /**
   * Determines whether the given layer is a 'word' layer.
   * @param layer The layer to test.
   * @param schema The schema the layer comes from.
   * @return true if the given layer is the word layer or a child of it.
   */
  public static boolean IsWordLayer(Layer layer, Schema schema) {
    return schema.getWordLayerId().equals(layer.getId())
      || (schema.getWordLayerId().equals(layer.getParentId())
          && !layer.getId().equals("segment"));
  } // end of IsWordLayer()
  
  /**
   * Determines whether the given layer is a 'phrase' layer.
   * @param layer The layer to test.
   * @param schema The schema the layer comes from.
   * @return true if the given layer is the turn layer or a non-word child of it.
   */
  public static boolean IsPhraseLayer(Layer layer, Schema schema) {
    return !schema.getWordLayerId().equals(layer.getId())
      && (schema.getTurnLayerId().equals(layer.getId())
          || schema.getTurnLayerId().equals(layer.getParentId()));
  } // end of IsPhraseLayer()
  
  /**
   * Determines whether the given layer is a 'span' layer.
   * @param layer The layer to test.
   * @param schema The schema the layer comes from.
   * @return true if the given layer is an aligned top-level layer - i.e. parent is root.
   */
  public static boolean IsSpanLayer(Layer layer, Schema schema) {
    return layer.getAlignment() != Constants.ALIGNMENT_NONE
      && (layer.getParentId() == null
          || layer.getParentId().equals(schema.getRoot().getId()))
      && layer != schema.getRoot();
  } // end of IsSpanLayer()

  /** Percent progress indicating that the main SQL query has started */
  static final int SQL_STARTED_PERCENT = 10;
  /** Percent progress indicating that the main SQL query has finished */
  static final int SQL_FINISHED_PERCENT = 90;

  /** SQL condition that identifies only main participants */
  static final String strMainParticipantClause 
  = " AND transcript_speaker.main_speaker = 1";
   
  /**
   * Query for finding matches for first column, which includes the possibility of
   * inserting queries for subsequent columns, so all matches are identified with a single
   * SQL statement.
   * <p> Arguments are:
   * <ul>
   *  <li>0: any extra JOINs e.g. {@link #sSqlWordStartJoin} </li>
   *  <li>1: border conditions</li>
   *  <li>2: search criteria subqueries</li>
   *  <li>3: extra fields for the SELECT clause - e.g. for line annotation_id and start/end anchors for the last column result</li>
   *  <li>4: "turn.label IN (...)" if participantQuery is specified, or "" if not</li>
   *  <li>5: transcript-matching clause if transcriptQuery is specified, or "" if not</li>
   *  <li>6: main speaker clause</li>
   *  <li>7: access clause</li>
   *  <li>8: subsequent column select clauses</li>
   *  <li>9: subsequent column joins</li>
   *  <li>10: subsequent column where clauses</li>
   *  <li>11: last column suffix</li>
   *  <li>12: segment_annotation_id expression</li>
   *  <li>13: target_annotation_id expression</li>
   *  <li>14: target scope (lowercase)</li>
   *  <li>15: target layer_id</li>
   *  <li>16: LIMIT clause, if any</li>
   * </ul>
   */
  static final MessageFormat sqlPatternMatchFirst = new MessageFormat(
    "INSERT INTO _result"
    +" (search_id, ag_id, speaker_number, start_anchor_id, end_anchor_id, defining_annotation_id,"
    +" segment_annotation_id, target_annotation_id, turn_annotation_id,"
    +" first_matched_word_annotation_id, last_matched_word_annotation_id,"
    +" complete, target_annotation_uid)"
    +" SELECT ?"
    +", word_0.ag_id AS ag_id"
    +", CAST(turn.label AS SIGNED) AS speaker_number"
    +", word_0.start_anchor_id, word_0.end_anchor_id,0" // start_anchor_id, end_anchor_id, defining_annotation_id, updated later
    +", {12} AS segment_annotation_id"
    +", {13} AS target_annotation_id"
    +", word_0.turn_annotation_id AS turn_annotation_id"
    +", word_0.word_annotation_id AS first_matched_word_annotation_id"
    +", word{11}.word_annotation_id AS last_matched_word_annotation_id"
    +", 0 AS complete"
    +", CONCAT(''e{14}_{15}_'', {13}) AS target_annotation_uid"
    +" FROM annotation_layer_"+SqlConstants.LAYER_TURN +" turn"
    +" /* extra joins */ {0}"
    +" /* subsequent columns */ {9}" 
    +" WHERE 1=1" // ...so that everything that follows can start with "AND"
    +" /* transcripts */ {5}"
    +" /* participants */ {4}" 
    +" /* main participant clause */ {6}" 
    +" /* access clause */ {7}" 
    +" /* first column: */"
    +" /* border conditions */ {1}" 
    +" /* search criteria subqueries */ {2}" 
    +" /* subsequent columns */ {10}" 
    +" ORDER BY word_0.turn_annotation_id, word_0.ordinal_in_turn{17}");

  /** SQL JOIN for including the transcript table */
  static final String sSqlTranscriptJoin =
    " INNER JOIN transcript ON turn.ag_id = transcript.ag_id";

  /** SQL JOIN for including the transcript speaker table */
  static final String sSqlTranscriptSpeakerJoin =
    " INNER JOIN transcript_speaker ON transcript_speaker.ag_id = turn.ag_id"
    +" AND turn.label REGEXP '^[0-9]+$'"
    +" AND transcript_speaker.speaker_number = CAST(turn.label AS SIGNED) ";

  /** 
   * Query for finding matches for subsequent columns.
   * Speaker doesn't matter any more - the first query returned only
   * words spoken by our search speakers.
   * <p> Arguments are:
   * <ul>
   *  <li>0: any extra JOINs e.g. {@link #sSqlWordStartJoin} </li>
   *  <li>1: border conditions</li>
   *  <li>2: search criteria subqueries</li>
   *  <li>3: extra fields for the SELECT clause - e.g. for line annotation_id and
   *         start/end anchors for the last column result</li> 
   *  <li>4: column number</li>
   *  <li>5: previous column number</li>
   * </ul>
   */
  static final MessageFormat sqlPatternMatchSubsequentSelect = new MessageFormat(
    " /* column {4}: */, "
    + "word{4}.annotation_id AS word{4}_annotation_id, word{4}.ag_id AS word{4}_ag_id,"
    +" word{4}.label AS word{4}_label, word{4}.label_status AS word{4}_label_status,"
    +" word{4}.start_anchor_id AS word{4}_start_anchor_id,"
    +" word{4}.end_anchor_id AS word{4}_end_anchor_id,"
    +" word{4}.turn_annotation_id AS word{4}_turn_annotation_id,"
    +" word{4}.ordinal_in_turn AS word{4}_ordinal_in_turn,"
    +" word{4}.word_annotation_id AS word{4}_word_annotation_id,"
    +" word{4}.parent_id AS word{4}_parent_id, word{4}.ordinal AS word{4}_ordinal"
    // have to select a reliable layer; transcription is always there
    +" {3} "); // extra fields
  
  /** 
   * JOIN required for {@link #sqlPatternMatchSubsequentSelect}, for finding matches for
   * subsequent columns. Speaker doesn't matter any more - the first query returned only
   * words spoken by our search speakers.<br>
   * Arguments are:
   * <ul>
   *  <li>0: any extra JOINs e.g. {@link #sSqlWordStartJoin} </li>
   *  <li>1: border conditions</li>
   *  <li>2: search criteria subqueries</li>
   *  <li>3: extra fields for the SELECT clause - e.g. for line annotation_id and
   *         start/end anchors for the last column result</li> 
   *  <li>4: column number</li>
   *  <li>5: previous column number</li>
   * </ul>
   */
  static final MessageFormat sqlPatternMatchSubsequentJoin = new MessageFormat(
    " /* column {4}: */ "
    +" INNER JOIN annotation_layer_"+SqlConstants.LAYER_TRANSCRIPTION + " word{4}"
    +" ON word{4}.ag_id = word{5}.ag_id"
    +" AND word{4}.turn_annotation_id = word{5}.turn_annotation_id");
  
  /** 
   * WHERE required for {@link #sqlPatternMatchSubsequentSelect}, for finding matches for
   * subsequent columns. Speaker doesn't matter any more - the first query returned only
   * words spoken by our search speakers.
   * <p> Arguments are:
   * <ul>
   *  <li>0: any extra JOINs e.g. {@link #sSqlWordStartJoin} </li>
   *  <li>1: border conditions</li>
   *  <li>2: search criteria subqueries</li>
   *  <li>3: extra fields for the SELECT clause - e.g. for line annotation_id and
   *         start/end anchors for the last column result</li> 
   *  <li>4: column number</li>
   *  <li>5: previous column number</li>
   * </ul>
   */
  static final MessageFormat sqlPatternMatchSubsequentWhere = new MessageFormat(
    " /* column {4}: */ "
    +" AND word{4}.ordinal_in_turn"
    +" BETWEEN word{5}.ordinal_in_turn + 1 AND word{5}.ordinal_in_turn + 1 + ?"
    + "{1} {2}");
  
  // subqueries for matching the layer representation
  // NB: they can match any variant of the word, not just the one that 
  //     displays in the layered transcript page

  /** Extra join for matching the start line border conditions */
  static final String sSqlStartLineJoin =
    " LEFT OUTER JOIN annotation_layer_"+SqlConstants.LAYER_TRANSCRIPTION + " last_word" 
    + " ON last_word.turn_annotation_id = word_0.turn_annotation_id"
    // assumption: that ordinals are always right
    + " AND last_word.ordinal_in_turn = word_0.ordinal_in_turn - 1"
    // get start offset of last word (not end offset, in case of partial overflow)
    + "  LEFT OUTER JOIN anchor last_word_start"
    + "  ON last_word_start.anchor_id = last_word.start_anchor_id";
  /** Extra conditions for matching the start line border conditions */
  static final String sSqlStartLineCondition =
    // the last word doesn't start after the start of the line
    " AND (last_word.annotation_id IS NULL OR last_word_start.offset < line_0_start.offset)";
  
  /** Extra join for matching the end line border conditions */
  static final MessageFormat sqlEndLineJoin = new MessageFormat(
    " LEFT OUTER JOIN annotation_layer_"+SqlConstants.LAYER_TRANSCRIPTION + "  next_word{0}" 
    + " ON next_word{0}.turn_annotation_id = word{0}.turn_annotation_id"
    // assumption: that ordinals are always right
    + " AND next_word{0}.ordinal_in_turn = word{0}.ordinal_in_turn + 1"
    // get start offset of next word
    + "  LEFT OUTER JOIN anchor next_word{0}_start"
    + "  ON next_word{0}_start.anchor_id = next_word{0}.start_anchor_id");
  /** Extra conditions for matching the end line border conditions */
  static final MessageFormat sqlEndLineCondition = new MessageFormat(
    // the next word doesn't start before the end of the line
    " AND (next_word{0}.annotation_id IS NULL"
    +" OR next_word{0}_start.offset >= line{0}_end.offset)");
  
  /** Extra conditions for matching the first word in a meta-layer spanning annotation */
  static final MessageFormat fmtSqlStartMetaSpanCondition = new MessageFormat(
    " AND NOT EXISTS (SELECT other_word.label" 
    + "  FROM annotation_layer_"+SqlConstants.LAYER_TRANSCRIPTION
    + "  other_word" 
    // get start offset of other word
    + "  INNER JOIN anchor other_word_start"
    + "  ON other_word_start.anchor_id = other_word.start_anchor_id"
    // it's in the same turn
    + "  WHERE other_word.turn_annotation_id = word{1}.turn_annotation_id"
    // and the same spanning annotataion
    + "  AND other_word_start.offset >= meta{1}_start_{0}.offset"
    + "  AND other_word_start.offset < meta{1}_end_{0}.offset"
    // and the other word starts earlier than the target word
    + "  AND other_word_start.offset < word{1}_start.offset )");
  
  /** Extra conditions for matching the first word in a freeform spanning annotation */
  static final MessageFormat fmtSqlStartFreeformSpanCondition = new MessageFormat(
    " AND ( word{1}.start_anchor_id = search{1}_{0}.start_anchor_id"
    + " OR NOT EXISTS (SELECT other_word.label" 
    + "  FROM annotation_layer_"+SqlConstants.LAYER_TRANSCRIPTION
    + "  other_word" 
    // get turn
    + "  INNER JOIN annotation_layer_"+SqlConstants.LAYER_TURN
    + "  other_turn"
    + "  ON other_word.turn_annotation_id = other_turn.annotation_id"
    // get start offset of other word
    + "  INNER JOIN anchor other_word_start"
    + "  ON other_word_start.anchor_id = other_word.start_anchor_id"
    // it's in the same graph
    + "  WHERE other_word.ag_id = word{1}.ag_id"
    // and the same participant
    + "  AND other_turn.label = turn.label"
    // and the same spanning annotataion
    + "  AND other_word_start.offset >= meta{1}_start_{0}.offset"
    + "  AND other_word_start.offset < meta{1}_end_{0}.offset"
    // and the other word starts earlier than the target word
    + "  AND other_word_start.offset < word{1}_start.offset ) )");
  
  /** Extra conditions for matching the last word in a meta-layer spanning annotation */
  static final MessageFormat fmtSqlEndMetaSpanCondition = new MessageFormat(
    " AND NOT EXISTS (SELECT other_word.label" 
    + "  FROM annotation_layer_"+SqlConstants.LAYER_TRANSCRIPTION
    + "  other_word" 
    // get start offset of other word
    + "  INNER JOIN anchor other_word_start"
    + "  ON other_word_start.anchor_id = other_word.start_anchor_id"
    // it's in the same turn
    + "  WHERE other_word.turn_annotation_id = word{1}.turn_annotation_id"
    // and the same spanning annotataion
    + "  AND other_word_start.offset >= meta{1}_start_{0}.offset"
    + "  AND other_word_start.offset < meta{1}_end_{0}.offset"
    // and the other word starts earlier than the target word
    + "  AND other_word_start.offset > word{1}_start.offset )");
  
  /** Extra conditions for matching the last word in a freeform spanning annotation */
  static final MessageFormat fmtSqlEndFreeformSpanCondition = new MessageFormat(
    " AND ( word{1}.end_anchor_id = search_{0}.end_anchor_id"
    + " OR NOT EXISTS (SELECT other_word.label" 
    + "  FROM annotation_layer_"+SqlConstants.LAYER_TRANSCRIPTION
    + "  other_word" 
    // get start offset of other word
    + "  INNER JOIN anchor other_word_start"
    + "  ON other_word_start.anchor_id = other_word.start_anchor_id"
    // it's in the same graph
    + "  WHERE other_word.ag_id = word{1}.ag_id"
    // and the same spanning annotataion
    + "  AND other_word_start.offset >= meta{1}_start_{0}.offset"
    + "  AND other_word_start.offset < meta{1}_end_{0}.offset"
    // and the other word starts earlier than the target word
    + "  AND other_word_start.offset > word{1}_start.offset ) )");

  /** Extra conditions for matching the start turn border conditions */
  static final String sqlStartTurnCondition = " AND word_0.ordinal_in_turn = 1";

  /** Query for matching the start/end utterance (line) border conditions */
  static final MessageFormat sqlLineJoin = new MessageFormat(
    " INNER JOIN (annotation_layer_"+SqlConstants.LAYER_UTTERANCE
    + " line{0}" 
    + "  INNER JOIN anchor line{0}_start"
    + "  ON line{0}_start.anchor_id = line{0}.start_anchor_id"
    + "  INNER JOIN anchor line{0}_end"
    + "  ON line{0}_end.anchor_id = line{0}.end_anchor_id)"
    + " ON line{0}.turn_annotation_id = word{0}.turn_annotation_id"
    // line bounds are outside word bounds...
    + "  AND line{0}_start.offset <= word{0}_start.offset"
    + "  AND line{0}_end.offset > word{0}_start.offset");
  
  /** 
   * Fields that identify the line <code>annotation_id</code>
   * and also the line's start/end anchors
   */
  static final MessageFormat sqlLineFields = new MessageFormat(
    ", line{0}.annotation_id AS line{0}_annotation_id"
    +", line{0}_start.anchor_id AS line{0}_start_anchor_id"
    +", line{0}_start.offset AS line{0}_start_offset"
    +", line{0}_start.alignment_status AS line{0}_start_alignment_status"
    +", line{0}_end.anchor_id AS line{0}_end_anchor_id"
    +", line{0}_end.offset AS line{0}_end_offset"
    +", line{0}_end.alignment_status AS line{0}_end_alignment_status");

  /** Extra join for matching the end turn border conditions */
  static final MessageFormat sqlEndTurnJoin = new MessageFormat(
    " LEFT OUTER JOIN annotation_layer_"+SqlConstants.LAYER_TRANSCRIPTION + "  next_word{0}" 
    + " ON next_word{0}.turn_annotation_id = word{0}.turn_annotation_id"
    // assumption: that ordinals are always right
    + " AND next_word{0}.ordinal_in_turn = word{0}.ordinal_in_turn + 1");
  /** Query for matching the end turn border conditions */
  static final MessageFormat sqlEndTurnCondition = new MessageFormat(
    " AND next_word{0}.annotation_id IS NULL");

  /** 
   * JOIN to get the start offset of the word.  The end offset isn't needed,
   * as the start time is enough to deduce whether a word is contained by
   * some other annotation.
   * <p> Arguments are:
   * <ul>
   *  <li>0: column suffix </li>
   * </ul>
   */
  static final MessageFormat sqlWordStartJoin = new MessageFormat(
    " INNER JOIN anchor word{0}_start ON word{0}_start.anchor_id = word{0}.start_anchor_id");

  /** 
   * JOIN to get the start offset of the word.  The end offset isn't needed,
   * as the start time is enough to deduce whether a word is contained by
   * some other annotation.  Argument 5 is the segment olayer ID to use
   */
  static final MessageFormat sqlSegmentStartJoin = new MessageFormat(
    " INNER JOIN annotation_layer_1 segment{0}"
    +" ON segment{0}.word_annotation_id = word{0}.annotation_id"
    +" INNER JOIN anchor segment{0}_start"
    +" ON segment{0}_start.anchor_id = segment{0}.start_anchor_id");
   
  /** 
   * JOIN to get the end anchor of the word. e.g. for checking alignment status.
   * <p> Arguments are:
   * <ul>
   *  <li>0: column suffix </li>
   * </ul>
   */
  static final MessageFormat sqlWordEndJoin = new MessageFormat(
    " INNER JOIN anchor word{0}_end ON word{0}_end.anchor_id = word{0}.end_anchor_id");
   
  /**
   * Query for displaying the static final result for a given match.
   * <p> Arguments are:
   * <ul>
   * <li>0: subclause(s) to add to WHERE condition to identify context
   * e.g. {@link #sSqlResultsWordCountContext}
   * </li> 
   * <li>1: any extra JOINs required e.g. {@link #sqlLineJoin}</li> 
   * </ul>
   */
  static final MessageFormat sqlResultDisplay = new MessageFormat(
    "SELECT word_0annotation_id, word_0ag_id, word_0label, word_0label_status,"
    + " word_0start_anchor_id, word_0end_anchor_id, word_0turn_annotation_id,"
    + " word_0ordinal_in_turn, word_0word_annotation_id,"
    + " word_0parent_id, word_0ordinal".replaceAll("word\\.", "word_0")
    + ", COALESCE(speaker.name, transcript_speaker.name) AS name,"
    + " transcript_speaker.speaker_id,"
    + " transcript_speaker.speaker_number,"
    + " transcript_speaker.main_speaker"
    + " FROM annotation_layer_" + SqlConstants.LAYER_TRANSCRIPTION
    + " word_0"
    + " INNER JOIN anchor word_0_start"
    + " ON word_0_start.anchor_id = word_0.start_anchor_id"
    + " INNER JOIN anchor word_0_end"
    + " ON word_0_end.anchor_id = word_0.end_anchor_id "
    + " INNER JOIN annotation_layer_"+SqlConstants.LAYER_TURN
    + " turn ON word_0.turn_annotation_id = turn.annotation_id" 
    + " INNER JOIN transcript_speaker"
    + " ON turn.label REGEXP '^[0-9]+$'"
    + " AND transcript_speaker.speaker_number = CAST(turn.label AS SIGNED)" 
    + " AND transcript_speaker.ag_id = turn.ag_id" 
    + " LEFT OUTER JOIN speaker ON speaker.speaker_number = CAST(turn.label AS SIGNED)" 
    + "{1}"
    + " WHERE word_0.turn_annotation_id = ?"
    + "{0}"
    + " ORDER BY word_0.ordinal_in_turn");
      
  /** WHERE clause for identifying words within a range of values of
   * <code>word.ordinal_in_turn</code>, for use as an argument to {@link #sqlResultDisplay}. */
  static final String sSqlResultsWordCountContext
  = " AND word.ordinal_in_turn BETWEEN ? AND ?";
  
  /** WHERE clause for identifying words within a line, for use as an argument to
   * {@link #sqlResultDisplay}. Must be used with {@link #sqlLineJoin}
   */
  static final String sSqlResultsLineContext
  //      = " AND (line.annotation_id = ?"
  = " AND (word_start.offset BETWEEN ? AND ?"
    // ensure that if the result spills over to a new line, all the words
    // are returned
    +" OR word.ordinal_in_turn BETWEEN ? AND ?)";

  /**
   * Query for matching start-anchor-sharing layer by numerical maximum
   * - uses <code>DECIMAL</code>
   * <p> Arguments are:
   * <ul>
   *  <li>0: layer_id</li>
   *  <li>1: negativity - "" or "NOT"</li>
   *  <li>2: case sensitivity - "" for insensitive, "BINARY" for sensitive</li>
   *  <li>3: ignored</li>
   *  <li>4: extra meta condition - e.g. for anchoring to start of annotation</li>
   *  <li>6: search-column suffix </li>
   * </ul>
   */
  static final MessageFormat START_ANCHORED_NUMERIC_MAX_JOIN = new MessageFormat(
    " INNER JOIN annotation_layer_{0} search{6}_{0}" 
    + " ON search{6}_{0}.start_anchor_id = word{6}.start_anchor_id"
    + " AND CAST(search{6}_{0}.label AS DECIMAL) < ? {4}");
  
  /**
   * Query for matching end-anchor-sharing layer by numerical maximum - uses <code>DECIMAL</code>
   * <p> Arguments are:
   * <ul>
   *  <li>0: layer_id</li>
   *  <li>1: negativity - "" or "NOT"</li>
   *  <li>2: case sensitivity - "" for insensitive, "BINARY" for sensitive</li>
   *  <li>3: ignored</li>
   *  <li>4: extra meta condition - e.g. for anchoring to start of annotation</li>
   *  <li>6: search-column suffix </li>
   * </ul>
   */
  static final MessageFormat END_ANCHORED_NUMERIC_MAX_JOIN = new MessageFormat(
    " INNER JOIN annotation_layer_{0} search{6}_{0}" 
    + " ON search{6}_{0}.end_anchor_id = word{6}.end_anchor_id"
    + " AND CAST(search{6}_{0}.label AS DECIMAL) < ? {4}");
  
  /**
   * Join for matching containing meta-layer by numerical maximum - uses <code>DECIMAL</code>
   * <p> Arguments are:
   * <ul>
   *  <li>0: layer_id</li>
   *  <li>1: ignored</li>
   *  <li>2: ignored</li>
   *  <li>3: ignored</li>
   *  <li>4: extra meta condition - e.g. for anchoring to start of annotation</li>
   * </ul>
   */
  static final MessageFormat CONTAINING_META_NUMERIC_MAX_JOIN = new MessageFormat(
    " INNER JOIN (annotation_layer_{0} search_{0}" 
    + "  INNER JOIN anchor meta_start_{0}"
    + "  ON meta_start_{0}.anchor_id = search_{0}.start_anchor_id"
    + "  INNER JOIN anchor meta_end_{0}"
    + "  ON meta_end_{0}.anchor_id = search_{0}.end_anchor_id)"
    + "  ON search_{0}.ag_id = word.ag_id"
    // same turn...
    + "  AND search_{0}.turn_annotation_id = word.turn_annotation_id"
    // meta bounds enclose word start time...
    + "  AND meta_start_{0}.offset <= word_start.offset"
    + "  AND meta_end_{0}.offset > word_start.offset"
    + "  AND CAST(search_{0}.label AS DECIMAL) < ? {4}");
  
  /**
   * Join for matching containing freeform-layer by numerical maximum - uses <code>DECIMAL</code>
   * <p> Arguments are:
   * <ul>
   *  <li>0: layer_id</li>
   *  <li>1: ignored</li>
   *  <li>2: ignored</li>
   *  <li>3: ignored</li>
   *  <li>4: extra meta condition - e.g. for anchoring to start of annotation</li>
   * </ul>
   */
  static final MessageFormat CONTAINING_FREEFORM_NUMERIC_MAX_JOIN = new MessageFormat(
    " INNER JOIN (annotation_layer_{0} search_{0}" 
    + "  INNER JOIN anchor meta_start_{0}"
    + "  ON meta_start_{0}.anchor_id = search_{0}.start_anchor_id"
    + "  INNER JOIN anchor meta_end_{0}"
    + "  ON meta_end_{0}.anchor_id = search_{0}.end_anchor_id)"
    + "  ON search_{0}.ag_id = word.ag_id"
    // meta bounds enclose word start time...
    + "  AND meta_start_{0}.offset <= word_start.offset"
    + "  AND meta_end_{0}.offset > word_start.offset"
    + "  AND CAST(search_{0}.label AS DECIMAL) < ? {4}");
  
  /**
   * Join for matching containing meta-layer by numerical maximum - uses <code>DECIMAL</code>
   * <p> Arguments are:
   * <ul>
   *  <li>0: layer_id</li>
   *  <li>1: ignored</li>
   *  <li>2: ignored</li>
   *  <li>3: ignored</li>
   *  <li>4: extra meta condition - e.g. for anchoring to start of annotation</li>
   *  <li>6: search-column suffix </li>
   * </ul>
   */
  static final MessageFormat CONTAINING_WORD_NUMERIC_MAX_JOIN = new MessageFormat(
    " INNER JOIN (annotation_layer_{0} search{6}_{0}" 
    + "  INNER JOIN anchor word{6}_start_{0}"
    + "  ON word{6}_start_{0}.anchor_id = search{6}_{0}.start_anchor_id"
    + "  INNER JOIN anchor word{6}_end_{0}"
    + "  ON word{6}_end_{0}.anchor_id = search{6}_{0}.end_anchor_id)"
    + "  ON search{6}_{0}.word_annotation_id = segment{6}.word_annotation_id"
    // word bounds enclose word start time...
    + "  AND word{6}_start_{0}.offset <= segment{6}_start.offset"
    + "  AND word{6}_end_{0}.offset > segment{6}_start.offset"
    + "  AND CAST(search{6}_{0}.label AS DECIMAL) < ? {4}");
  
  /**
   * Query for numerical maximum - uses <code>DECIMAL</code>
   * <p> Arguments are:
   * <ul>
   *  <li>0: layer_id</li>
   *  <li>6: search-column suffix </li>
   * </ul>
   */
  static final MessageFormat NUMERIC_MAX_JOIN = new MessageFormat(
    " INNER JOIN annotation_layer_{0} search{6}_{0}" 
    + "  ON search{6}_{0}.word_annotation_id = word{6}.word_annotation_id"
    + "  AND CAST(search{6}_{0}.label AS DECIMAL) < ?");
  
  /**
   * Query for matching start-anchor-sharing layer by numerical minimum
   * - uses <code>DECIMAL</code>
   * <p> Arguments are:
   * <ul>
   *  <li>0: layer_id</li>
   *  <li>1: negativity - "" or "NOT"</li>
   *  <li>2: case sensitivity - "" for insensitive, "BINARY" for sensitive</li>
   *  <li>3: ignored</li>
   *  <li>4: extra meta condition - e.g. for anchoring to start of annotation</li>
   *  <li>6: search-column suffix </li>
   * </ul>
   */
  static final MessageFormat START_ANCHORED_NUMERIC_MIN_JOIN = new MessageFormat(
    " INNER JOIN annotation_layer_{0} search{6}_{0}" 
    + " ON search{6}_{0}.start_anchor_id = word{6}.start_anchor_id"
    + " AND CAST(search{6}_{0}.label AS DECIMAL) >= ? {4}");
  
  /**
   * Query for matching end-anchor-sharing layer by numerical minimum - uses <code>DECIMAL</code> 
   * <p> Arguments are:
   * <ul>
   *  <li>0: layer_id</li>
   *  <li>1: negativity - "" or "NOT"</li>
   *  <li>2: case sensitivity - "" for insensitive, "BINARY" for sensitive</li>
   *  <li>3: ignored</li>
   *  <li>4: extra meta condition - e.g. for anchoring to start of annotation</li>
   *  <li>6: search-column suffix </li>
   * </ul>
   */
  static final MessageFormat END_ANCHORED_NUMERIC_MIN_JOIN = new MessageFormat(
    " INNER JOIN annotation_layer_{0} search{6}_{0}" 
    + " ON search{6}_{0}.end_anchor_id = word{6}.end_anchor_id"
    + " AND CAST(search{6}_{0}.label AS DECIMAL) >= ? {4}");
  
  /**
   * Join for matching containing meta-layer by numerical minimum - uses <code>DECIMAL</code>
   * <p> Arguments are:
   * <ul>
   *  <li>0: layer_id</li>
   *  <li>1: ignored</li>
   *  <li>2: ignored</li>
   *  <li>3: ignored</li>
   *  <li>4: extra meta condition - e.g. for anchoring to start of annotation</li>
   *  <li>6: search-column suffix </li>
   * </ul>
   */
  static final MessageFormat CONTAINING_META_NUMERIC_MIN_JOIN = new MessageFormat(
    " INNER JOIN (annotation_layer_{0} search{6}_{0}" 
    + "  INNER JOIN anchor meta{6}_start_{0}"
    + "  ON meta{6}_start_{0}.anchor_id = search{6}_{0}.start_anchor_id"
    + "  INNER JOIN anchor meta{6}_end_{0}"
    + "  ON meta{6}_end_{0}.anchor_id = search{6}_{0}.end_anchor_id)"
    + "  ON search{6}_{0}.ag_id = word{6}.ag_id"
    // same turn...
    + "  AND search{6}_{0}.turn_annotation_id = word{6}.turn_annotation_id"
    // meta bounds enclose word start time...
    + "  AND meta{6}_start_{0}.offset <= word{6}_start.offset"
    + "  AND meta{6}_end_{0}.offset > word{6}_start.offset"
    + "  AND CAST(search{6}_{0}.label AS DECIMAL) >= ? {4}");
  /**
   * Join for matching containing freeform-layer by numerical minimum - uses <code>DECIMAL</code>
   * <p> Arguments are:
   * <ul>
   *  <li>0: layer_id</li>
   *  <li>1: ignored</li>
   *  <li>2: ignored</li>
   *  <li>3: ignored</li>
   *  <li>4: extra meta condition - e.g. for anchoring to start of annotation</li>
   *  <li>6: search-column suffix </li>
   * </ul>
   */
  static final MessageFormat CONTAINING_FREEFORM_NUMERIC_MIN_JOIN = new MessageFormat(
    " INNER JOIN (annotation_layer_{0} search{6}_{0}" 
    + "  INNER JOIN anchor meta{6}_start_{0}"
    + "  ON meta{6}_start_{0}.anchor_id = search{6}_{0}.start_anchor_id"
    + "  INNER JOIN anchor meta{6}_end_{0}"
    + "  ON meta{6}_end_{0}.anchor_id = search{6}_{0}.end_anchor_id)"
    + "  ON search{6}_{0}.ag_id = word{6}.ag_id"
    // meta bounds enclose word start time...
    + "  AND meta{6}_start_{0}.offset <= word{6}_start.offset"
    + "  AND meta{6}_end_{0}.offset > word{6}_start.offset"
    + "  AND CAST(search{6}_{0}.label AS DECIMAL) >= ? {4}");
  
  /**
   * Join for matching containing meta-layer by numerical minimum - uses <code>DECIMAL</code>
   * <p> Arguments are:
   * <ul>
   *  <li>0: layer_id</li>
   *  <li>1: ignored</li>
   *  <li>2: ignored</li>
   *  <li>3: ignored</li>
   *  <li>4: extra meta condition - e.g. for anchoring to start of annotation</li>
   *  <li>6: search-column suffix </li>
   * </ul>
   */
  static final MessageFormat CONTAINING_WORD_NUMERIC_MIN_JOIN = new MessageFormat(
    " INNER JOIN (annotation_layer_{0} search{6}_{0}" 
    + "  INNER JOIN anchor word{6}_start_{0}"
    + "  ON word{6}_start_{0}.anchor_id = search{6}_{0}.start_anchor_id"
    + "  INNER JOIN anchor word{6}_end_{0}"
    + "  ON word{6}_end_{0}.anchor_id = search{6}_{0}.end_anchor_id)"
    + "  ON search{6}_{0}.word_annotation_id = segment{6}.word_annotation_id"
    // word bounds enclose word start time...
    + "  AND word{6}_start_{0}.offset <= segment{6}_start.offset"
    + "  AND word{6}_end_{0}.offset > segment{6}_start.offset"
    + "  AND CAST(search{6}_{0}.label AS DECIMAL) >= ? {4}");
  
  /**
   * Query for numerical minimum - uses <code>DECIMAL</code>
   * <p> Arguments are:
   * <ul>
   *  <li>0: layer_id</li>
   *  <li>6: search-column suffix </li>
   * </ul>
   */
  static final MessageFormat NUMERIC_MIN_JOIN  = new MessageFormat(    
    " INNER JOIN annotation_layer_{0} search{6}_{0}" 
    + "  ON search{6}_{0}.word_annotation_id = word{6}.word_annotation_id"
    + "  AND CAST(search{6}_{0}.label AS DECIMAL) >= ?");
  
  /**
   * Query for matching start-anchor-sharing layer by numerical range - uses <code>DECIMAL</code>
   * <p> Arguments are:
   * <ul>
   *  <li>0: layer_id</li>
   *  <li>1: negativity - "" or "NOT"</li>
   *  <li>2: case sensitivity - "" for insensitive, "BINARY" for sensitive</li>
   *  <li>3: ignored</li>
   *  <li>4: extra meta condition - e.g. for anchoring to start of annotation</li>
   *  <li>6: search-column suffix </li>
   * </ul>
   */
  static final MessageFormat START_ANCHORED_NUMERIC_RANGE_JOIN  = new MessageFormat(
    " INNER JOIN annotation_layer_{0} search{6}_{0}" 
    + " ON search{6}_{0}.start_anchor_id = word{6}.start_anchor_id"
    + " AND CAST(search{6}_{0}.label AS DECIMAL) >= ?"
    + " AND CAST(search{6}_{0}.label AS DECIMAL) < ? {4}");
  
  /**
   * Query for matching end-anchor-sharing layer by numerical range - uses <code>DECIMAL</code>
   * <p> Arguments are:
   * <ul>
   *  <li>0: layer_id</li>
   *  <li>1: negativity - "" or "NOT"</li>
   *  <li>2: case sensitivity - "" for insensitive, "BINARY" for sensitive</li>
   *  <li>3: ignored</li>
   *  <li>4: extra meta condition - e.g. for anchoring to start of annotation</li>
   *  <li>6: search-column suffix </li>
   * </ul>
   */
  static final MessageFormat END_ANCHORED_NUMERIC_RANGE_JOIN  = new MessageFormat(
    " INNER JOIN annotation_layer_{0} search{6}_{0}" 
    + " ON search{6}_{0}.end_anchor_id = word{6}.end_anchor_id"
    + " AND CAST(search{6}_{0}.label AS DECIMAL) >= ?"
    + " AND CAST(search{6}_{0}.label AS DECIMAL) < ? {4}");
  
  /**
   * Join for matching containing meta-layer by numerical range - uses <code>DECIMAL</code>
   * <p> Arguments are:
   * <ul>
   *  <li>0: layer_id</li>
   *  <li>6: search-column suffix </li>
   * </ul>
   */
  static final MessageFormat CONTAINING_META_NUMERIC_RANGE_JOIN = new MessageFormat(
    " INNER JOIN (annotation_layer_{0} search{6}_{0}" 
    + "  INNER JOIN anchor meta{6}_start_{0}"
    + "  ON meta{6}_start_{0}.anchor_id = search{6}_{0}.start_anchor_id"
    + "  INNER JOIN anchor meta{6}_end_{0}"
    + "  ON meta{6}_end_{0}.anchor_id = search{6}_{0}.end_anchor_id)"
    + "  ON search{6}_{0}.ag_id = word{6}.ag_id"
    // same turn...
    + "  AND search{6}_{0}.turn_annotation_id = word{6}.turn_annotation_id"
    // meta bounds enclose word start time...
    + "  AND meta{6}_start_{0}.offset <= word{6}_start.offset"
    + "  AND meta{6}_end_{0}.offset > word{6}_start.offset"
    + "  AND CAST(search{6}_{0}.label AS DECIMAL) >= ?"
    + "  AND CAST(search{6}_{0}.label AS DECIMAL) < ? {4}");
  
  /**
   * Join for matching containing freeform-layer by numerical range - uses <code>DECIMAL</code>
   * <p> Arguments are:
   * <ul>
   *  <li>0: layer_id</li>
   *  <li>6: search-column suffix </li>
   * </ul>
   */
  static final MessageFormat CONTAINING_FREEFORM_NUMERIC_RANGE_JOIN = new MessageFormat(
    " INNER JOIN (annotation_layer_{0} search{6}_{0}" 
    + "  INNER JOIN anchor meta{6}_start_{0}"
    + "  ON meta{6}_start_{0}.anchor_id = search{6}_{0}.start_anchor_id"
    + "  INNER JOIN anchor meta{6}_end_{0}"
    + "  ON meta{6}_end_{0}.anchor_id = search{6}_{0}.end_anchor_id)"
    + "  ON search{6}_{0}.ag_id = word{6}.ag_id"
    // meta bounds enclose word start time...
    + "  AND meta{6}_start_{0}.offset <= word{6}_start.offset"
    + "  AND meta{6}_end_{0}.offset > word{6}_start.offset"
    + "  AND CAST(search{6}_{0}.label AS DECIMAL) >= ?"
    + "  AND CAST(search{6}_{0}.label AS DECIMAL) < ? {4}");
  
  /**
   * Join for matching containing meta-layer by numerical range - uses <code>DECIMAL</code>
   * <p> Arguments are:
   * <ul>
   *  <li>0: layer_id</li>
   *  <li>6: search-column suffix </li>
   * </ul>
   */
  static final MessageFormat CONTAINING_WORD_NUMERIC_RANGE_JOIN = new MessageFormat(
    " INNER JOIN (annotation_layer_{0} search{6}_{0}" 
    + "  INNER JOIN anchor word{6}_start_{0}"
    + "  ON word{6}_start_{0}.anchor_id = search{6}_{0}.start_anchor_id"
    + "  INNER JOIN anchor word{6}_end_{0}"
    + "  ON word{6}_end_{0}.anchor_id = search{6}_{0}.end_anchor_id)"
    + "  ON search{6}_{0}.word_annotation_id = segment{6}.word_annotation_id"
    // word bounds enclose segment start time...
    + "  AND word{6}_start_{0}.offset <= segment{6}_start.offset"
    + "  AND word{6}_end_{0}.offset > segment{6}_start.offset"
    + "  AND CAST(search{6}_{0}.label AS DECIMAL) >= ?"
    + "  AND CAST(search{6}_{0}.label AS DECIMAL) < ? {4}");
  
  /**
   * Query for numerical range - uses <code>DECIMAL</code>
   * <p> Arguments are:
   * <ul>
   *  <li>0: layer_id</li>
   *  <li>6: search-column suffix </li>
   * </ul>
   */
  static final MessageFormat NUMERIC_RANGE_JOIN = new MessageFormat(    
    " INNER JOIN annotation_layer_{0} search{6}_{0}" 
    + "  ON search{6}_{0}.word_annotation_id = word{6}.word_annotation_id"
    + "  AND CAST(search{6}_{0}.label AS DECIMAL) >= ?"
    + "  AND CAST(search{6}_{0}.label AS DECIMAL) < ?");
  
  /**
   * Query for matching a trailing (following the word) meta-layer label
   * - uses <code>REGEXP</code>
   * <p> Arguments are:
   * <ul>
   *  <li>0: layer_id</li>
   *  <li>1: negativity - "" or "NOT"</li>
   *  <li>2: case sensitivity - "" for insensitive, "BINARY" for sensitive</li>
   *  <li>3: ignored</li>
   *  <li>4: extra meta condition - e.g. for anchoring to start of annotation</li>
   *  <li>6: search-column suffix </li>
   * </ul>
   */
  static final MessageFormat TRAILING_META_REGEXP_JOIN  = new MessageFormat(
    " INNER JOIN (annotation_layer_{0} search{6}_{0}" 
    + "  INNER JOIN anchor meta{6}_start_{0}"
    + "  ON meta{6}_start_{0}.anchor_id = search{6}_{0}.start_anchor_id"
    + "  INNER JOIN anchor meta{6}_end_{0}"
    + "  ON meta{6}_end_{0}.anchor_id = search{6}_{0}.end_anchor_id)"
    + "  ON search{6}_{0}.ag_id = word{6}.ag_id"
    // same turn...
    + "  AND search{6}_{0}.turn_annotation_id = word{6}.turn_annotation_id"
    // meta bounds enclose word end time...
    + "  AND meta{6}_start_{0}.offset <= word{6}_end.offset"
    + "  AND meta{6}_end_{0}.offset >= word{6}_end.offset"
    + "  AND {2,choice,0#search{6}_{0}.label|1#CAST(search{6}_{0}.label AS BINARY)}"
    + " {1} REGEXP {2,choice,0#|1#BINARY} ? {4}");
  
  /**
   * Query for matching a trailing (following the word) freeform-layer label
   * - uses <code>REGEXP</code>
   * <p> Arguments are:
   * <ul>
   *  <li>0: layer_id</li>
   *  <li>1: negativity - "" or "NOT"</li>
   *  <li>2: case sensitivity - "" for insensitive, "BINARY" for sensitive</li>
   *  <li>3: ignored</li>
   *  <li>4: extra meta condition - e.g. for anchoring to start of annotation</li>
   * </ul>
   */
  static final MessageFormat TRAILING_FREEFORM_REGEXP_JOIN  = new MessageFormat(
    " INNER JOIN (annotation_layer_{0} search_{0}" 
    + "  INNER JOIN anchor meta_start_{0}"
    + "  ON meta_start_{0}.anchor_id = search_{0}.start_anchor_id"
    + "  INNER JOIN anchor meta_end_{0}"
    + "  ON meta_end_{0}.anchor_id = search_{0}.end_anchor_id)"
    + "  ON search_{0}.ag_id = word.ag_id"
    // meta bounds enclose word end time...
    + "  AND meta_start_{0}.offset <= word_end.offset"
    + "  AND meta_end_{0}.offset >= word_end.offset"
    + "  AND {2,choice,0#search_{0}.label|1#CAST(search_{0}.label AS BINARY)}"
    + " {1} REGEXP {2,choice,0#|1#BINARY} ? {4}");
  
  /**
   * Query for matching start-anchor-sharing layer label - uses <code>REGEXP</code>
   * <p> Arguments are:
   * <ul>
   *  <li>0: layer_id</li>
   *  <li>1: negativity - "" or "NOT"</li>
   *  <li>2: case sensitivity - "" for insensitive, "BINARY" for sensitive</li>
   *  <li>3: ignored</li>
   *  <li>4: extra meta condition - e.g. for anchoring to start of annotation</li>
   *  <li>6: search-column suffix </li>
   * </ul>
   */
  static final MessageFormat START_ANCHORED_REGEXP_JOIN  = new MessageFormat(
    " INNER JOIN annotation_layer_{0} search{6}_{0}" 
    + " ON search{6}_{0}.start_anchor_id = word{6}.start_anchor_id"
    + " AND {2,choice,0#search{6}_{0}.label|1#CAST(search{6}_{0}.label AS BINARY)}"
    + " {1} REGEXP {2,choice,0#|1#BINARY} ? {4}");
  
  /**
   * Query for matching end-anchor-sharing layer label - uses <code>REGEXP</code>
   * <p> Arguments are:
   * <ul>
   *  <li>0: layer_id</li>
   *  <li>1: negativity - "" or "NOT"</li>
   *  <li>2: case sensitivity - "" for insensitive, "BINARY" for sensitive</li>
   *  <li>3: ignored</li>
   *  <li>4: extra meta condition - e.g. for anchoring to start of annotation</li>
   *  <li>6: search-column suffix </li>
   * </ul>
   */
  static final MessageFormat END_ANCHORED_REGEXP_JOIN  = new MessageFormat(
    " INNER JOIN annotation_layer_{0} search{6}_{0}" 
    + " ON search{6}_{0}.end_anchor_id = word{6}.end_anchor_id"
    + " AND {2,choice,0#search{6}_{0}.label|1#CAST(search{6}_{0}.label AS BINARY)}"
    + " {1} REGEXP {2,choice,0#|1#BINARY} ? {4}");
  
  /**
   * Query for matching containing meta-layer label - uses <code>REGEXP</code>
   * <p> Arguments are:
   * <ul>
   *  <li>0: layer_id</li>
   *  <li>1: negativity - "" or "NOT"</li>
   *  <li>2: case sensitivity - "" for insensitive, "BINARY" for sensitive</li>
   *  <li>3: ignored</li>
   *  <li>4: extra meta condition - e.g. for anchoring to start of annotation</li>
   *  <li>6: search-column suffix </li>
   * </ul>
   */
  static final MessageFormat CONTAINING_META_REGEXP_JOIN  = new MessageFormat(
    " INNER JOIN (annotation_layer_{0} search{6}_{0}" 
    + "  INNER JOIN anchor meta{6}_start_{0}"
    + "  ON meta{6}_start_{0}.anchor_id = search{6}_{0}.start_anchor_id"
    + "  INNER JOIN anchor meta{6}_end_{0}"
    + "  ON meta{6}_end_{0}.anchor_id = search{6}_{0}.end_anchor_id)"
    + "  ON search{6}_{0}.ag_id = word{6}.ag_id"
    // same turn...
    + "  AND search{6}_{0}.turn_annotation_id = word{6}.turn_annotation_id"
    // meta bounds enclose word start time...
    + "  AND meta{6}_start_{0}.offset <= word{6}_start.offset"
    + "  AND meta{6}_end_{0}.offset > word{6}_start.offset"
    + "  AND {2,choice,0#search{6}_{0}.label|1#CAST(search{6}_{0}.label AS BINARY)}"
    + " {1} REGEXP {2,choice,0#|1#BINARY} ? {4}");

  /**
   * Query for matching containing freeform-layer label - uses <code>REGEXP</code>
   * <p> Arguments are:
   * <ul>
   *  <li>0: layer_id</li>
   *  <li>1: negativity - "" or "NOT"</li>
   *  <li>2: case sensitivity - "" for insensitive, "BINARY" for sensitive</li>
   *  <li>3: ignored</li>
   *  <li>4: extra meta condition - e.g. for anchoring to start of annotation</li>
   *  <li>6: search-column suffix </li>
   * </ul>
   */
  static final MessageFormat CONTAINING_FREEFORM_REGEXP_JOIN  = new MessageFormat(
    " INNER JOIN (annotation_layer_{0} search{6}_{0}" 
    + "  INNER JOIN anchor meta{6}_start_{0}"
    + "  ON meta{6}_start_{0}.anchor_id = search{6}_{0}.start_anchor_id"
    + "  INNER JOIN anchor meta{6}_end_{0}"
    + "  ON meta{6}_end_{0}.anchor_id = search{6}_{0}.end_anchor_id)"
    + "  ON search{6}_{0}.ag_id = word{6}.ag_id"
    // meta bounds enclose word start time...
    + "  AND meta{6}_start_{0}.offset <= word{6}_start.offset"
    + "  AND meta{6}_end_{0}.offset > word{6}_start.offset"
    + "  AND {2,choice,0#search{6}_{0}.label|1#CAST(search{6}_{0}.label AS BINARY)}"
    + " {1} REGEXP {2,choice,0#|1#BINARY} ? {4}");

  /**
   * Query for pattern matching - uses <code>REGEXP</code> 
   * <p> Arguments are:
   * <ul>
   *  <li>0: layer_id</li>
   *  <li>1: negativity - "" or "NOT"</li>
   *  <li>2: case sensitivity - "" for insensitive, "BINARY" for sensitive</li>
   *  <li>3: ignored</li>
   *  <li>4: ignored</li>
   *  <li>6: search-column suffix </li>
   * </ul>
   */
  static final MessageFormat SEGMENT_REGEXP_JOIN = new MessageFormat(    
    " INNER JOIN annotation_layer_{0} search{6}_{0}" 
    + "  ON search{6}_{0}.segment_annotation_id = segment{6}.annotation_id"
    + "  AND {2,choice,0#search{6}_{0}.label|1#CAST(search{6}_{0}.label AS BINARY)}"
    + " {1} REGEXP {2,choice,0#|1#BINARY} ?");
  
  /**
   * Query for matching containing word-layer label - uses <code>REGEXP</code>
   * <p> Arguments are:
   * <ul>
   *  <li>0: layer_id</li>
   *  <li>1: negativity - "" or "NOT"</li>
   *  <li>2: case sensitivity - "" for insensitive, "BINARY" for sensitive</li>
   *  <li>3: ignored</li>
   *  <li>4: ignored</li>
   *  <li>6: search-column suffix </li>
   * </ul>
   */
  static final MessageFormat CONTAINING_WORD_REGEXP_JOIN  = new MessageFormat(
    " INNER JOIN (annotation_layer_{0} search{6}_{0}" 
    + "  INNER JOIN anchor word{6}_start_{0}"
    + "  ON word{6}_start_{0}.anchor_id = search{6}_{0}.start_anchor_id"
    + "  INNER JOIN anchor word{6}_end_{0}"
    + "  ON word{6}_end_{0}.anchor_id = search{6}_{0}.end_anchor_id)"
    + "  ON search{6}_{0}.word_annotation_id = segment{6}.word_annotation_id"
    // word bounds enclose segment start time...
    + "  AND word{6}_start_{0}.offset <= segment{6}_start.offset"
    + "  AND word{6}_end_{0}.offset > segment{6}_start.offset"
    + "  AND {2,choice,0#search{6}_{0}.label|1#CAST(search{6}_{0}.label AS BINARY)}"
    + " {1} REGEXP {2,choice,0#|1#BINARY} ? {4}");

  /**
   * Query for pattern matching - uses <code>REGEXP</code> 
   * <p> Arguments are:
   * <ul>
   *  <li>0: layer_id</li>
   *  <li>1: negativity - "" or "NOT"</li>
   *  <li>2: case sensitivity - "" for insensitive, "BINARY" for sensitive</li>
   *  <li>6: search-column suffix </li>
   * </ul>
   */
  static final MessageFormat REGEXP_JOIN  = new MessageFormat(    
    " INNER JOIN annotation_layer_{0} search{6}_{0}" 
    + "  ON search{6}_{0}.word_annotation_id = word{6}.word_annotation_id"
    + "  AND {2,choice,0#search{6}_{0}.label|1#CAST(search{6}_{0}.label AS BINARY)}"
    + " {1} REGEXP {2,choice,0#|1#BINARY} ?");
  
  /**
   * Query condition for detecting nonexistent annotations<br>
   * Arguments are:
   * <ul>
   *  <li>0: layer_id</li>
   *  <li>1: ignored</li>
   *  <li>2: ignored</li>
   *  <li>3: ignored</li>
   *  <li>4: ignored</li>
   *  <li>6: search-column suffix </li>
   * </ul>
   */
  static final MessageFormat NULL_ANNOTATION_CONDITION = new MessageFormat(
    " AND search{6}_{0}.annotation_id IS NULL");
}
