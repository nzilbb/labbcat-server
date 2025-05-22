//
// Copyright 2023-2025 New Zealand Institute of Language, Brain and Behaviour, 
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
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Vector;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import nzilbb.ag.*;
import nzilbb.labbcat.server.search.Column;
import nzilbb.labbcat.server.search.LayerMatch;
import nzilbb.labbcat.server.search.Matrix;
import nzilbb.labbcat.server.search.SearchTask;

/**
 * Implementation of search that uses a single monolithic SQL query to identify matches.
 * @author Robert Fromont robert@fromont.net.nz
 */
public class OneQuerySearch extends SearchTask {
  
  /**
   * Create an SQL query that identifies results that match the search matrix patterns,
   * filling in the given lists with information about parameters to set.
   * @param parameters List of parameter values, which must be Double, Integer, or String.
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
  public String generateSql(
    Vector<Object> parameters,
    Schema schema, Predicate<Layer> layerIsSpanningAndWordAnchored,
    UnaryOperator<String> participantCondition, UnaryOperator<String> transcriptCondition)
    throws Exception {
    
    // if there's no explicit target layer
    if (matrix.getTargetLayerId() == null) {
      // and there's a segment condition
      matrix.layerMatchStream()
        .filter(match -> "segment".equals(match.getId()))
        .filter(LayerMatch::HasCondition)
        .findAny()
        // make the segment condition the target
        .ifPresent(match -> match.setTarget(true));
    }
    setDescription(matrix.getDescription());
    
    // column 0 first
    int iWordColumn = 0;
    StringBuilder sSqlExtraJoinsFirst = new StringBuilder();
    StringBuilder sSqlLayerMatchesFirst = new StringBuilder();
    StringBuilder sSqlExtraFieldsFirst = new StringBuilder();
    boolean bTargetAnnotation = false;
    String targetExpression = "word_0.annotation_id";
    String targetSegmentExpression = "NULL";
    String targetSegmentOrder = "";
    
    Object[] columnSuffix = { "_" + iWordColumn };
    String sSqlWordStartJoin = sqlWordStartJoin.format(columnSuffix);
    String sSqlWordEndJoin = sqlWordEndJoin.format(columnSuffix);
    String sSqlSegmentStartJoin = sqlSegmentStartJoin.format(columnSuffix);
    String sSqlSegmentEndJoin = sqlSegmentEndJoin.format(columnSuffix);
    String sSqlLineJoin = sqlLineJoinViaToken.format(columnSuffix);
    String sSqlLineStartJoin = sqlLineStartJoin.format(columnSuffix);
    String sSqlLineEndJoin = sqlLineEndJoin.format(columnSuffix);
    String sSqlEndLineJoin = sqlEndLineJoin.format(columnSuffix);
    String sSqlEndTurnJoin = sqlEndTurnJoin.format(columnSuffix);

    // ensure we don't have any null Booleans in the search matrix
    matrix.layerMatchStream().forEach(layerMatch -> layerMatch.setNullBooleans());
    
    // we need a primary word table to join to for each column (to determine adjacency, etc.)
    // but to minimise the number of joins, we don't just make that the TRANSCRIPTION layer
    // instead, we use any word layer that there's a match for (there will be one usually)
    // and fall back to joining to the TRANSCRIPTION layer if all patterns in a given column
    // are for non-word layers...

    Vector<Optional<LayerMatch>> primaryWordLayers = new Vector<Optional<LayerMatch>>();
    Optional<LayerMatch> firstPrimaryWordLayer = matrix.getColumns().get(iWordColumn)
      .getLayers().values().stream()
      .flatMap(m -> m.stream())
      .filter(LayerMatch::HasCondition)
      // not a "NOT .+" expression
      .filter(layerMatch -> !layerMatch.getNot() || !".+".equals(layerMatch.getPattern()))
      .filter(layerMatch -> schema.getLayer(layerMatch.getId()) != null)
      // word scope
      .filter(layerMatch -> IsWordLayer(schema.getLayer(layerMatch.getId()), schema))
      .findAny();
    primaryWordLayers.add(firstPrimaryWordLayer);
    
    // check for segment layer search

    // first determine whether there are any segment layers in the whole matrix
    List<LayerMatch> allSegmentMatches = matrix.layerMatchStream()
      .filter(LayerMatch::HasCondition)
      .filter(layerMatch -> schema.getLayer(layerMatch.getId()) != null)
      .filter(layerMatch -> IsSegmentLayer(schema.getLayer(layerMatch.getId()), schema))
      .collect(Collectors.toList());
    Optional<LayerMatch> overallTargetSegmentLayer = allSegmentMatches.stream()
      .filter(LayerMatch::IsTarget)
      .findAny();
    if (allSegmentMatches.size() > 0                 // there are segments matched
        && !overallTargetSegmentLayer.isPresent()) { // but none is explicitly the target
      // target the first one
      overallTargetSegmentLayer = Optional.of(allSegmentMatches.get(0));
      overallTargetSegmentLayer.get().setTarget(true);
    }
    
    // this affects how we match aligned word layers in each column
    Vector<Optional<LayerMatch>> primarySegmentLayers = new Vector<Optional<LayerMatch>>();
    List<LayerMatch> segmentMatches = matrix.getColumns().get(iWordColumn)
      .getLayers().values().stream()
      .flatMap(m -> m.stream())
      .filter(LayerMatch::HasCondition)
      .filter(layerMatch -> schema.getLayer(layerMatch.getId()) != null)
      .filter(layerMatch -> IsSegmentLayer(schema.getLayer(layerMatch.getId()), schema))
      .collect(Collectors.toList());
    // if there's an explicitly targetted one, us that
    Optional<LayerMatch> columnTargetSegmentLayer = segmentMatches.stream()
      .filter(LayerMatch::IsTarget)
      .findAny();
    if (!columnTargetSegmentLayer.isPresent()) { // no explicitly targeted layer
      // target the first segment layer
      columnTargetSegmentLayer = segmentMatches.stream().findAny();
    }
    primarySegmentLayers.add(columnTargetSegmentLayer);
    boolean anchoredSegmentMatch = matrix.getColumns().get(iWordColumn)
      .getLayers().values().stream()
      .flatMap(m -> m.stream())
      .filter(LayerMatch::HasCondition)
      .filter(layerMatch -> schema.getLayer(layerMatch.getId()) != null)
      .filter(layerMatch -> IsSegmentLayer(schema.getLayer(layerMatch.getId()), schema))
      .filter(layerMatch -> layerMatch.getAnchorStart() || layerMatch.getAnchorEnd())
      .findAny().isPresent();

    if (firstPrimaryWordLayer.isPresent()) {
      // change word start/end joins to match word layer table
      sSqlWordStartJoin = sSqlWordStartJoin
        .replace("word_"+iWordColumn+".", "search_"+iWordColumn+"_"
                 +schema.getLayer(firstPrimaryWordLayer.get().getId()).get("layer_id")+".");
      sSqlWordEndJoin = sSqlWordEndJoin
        .replace("word_"+iWordColumn+".", "search_"+iWordColumn+"_"
                 +schema.getLayer(firstPrimaryWordLayer.get().getId()).get("layer_id")+".");
    } else { // no primary word layer 
      // we need a word layer to anchor to
      sSqlExtraJoinsFirst.append(
        " INNER JOIN annotation_layer_"+ SqlConstants.LAYER_TRANSCRIPTION
        +" word_"+iWordColumn+" ON word_"+iWordColumn+".turn_annotation_id = turn.annotation_id");
      sSqlLineJoin = sqlDirectLineJoin.format(columnSuffix);
    }
    
    Optional<Layer> alignedWordLayer = matrix.getColumns().get(iWordColumn)
      .getLayers().values().stream()
      .flatMap(m -> m.stream())
      .filter(LayerMatch::HasCondition)
      .map(layerMatch -> schema.getLayer(layerMatch.getId()))
      .filter(Objects::nonNull)
      .filter(layer -> IsWordLayer(layer, schema))
      .filter(layer -> !layer.getId().equals(schema.getWordLayerId()))
      .filter(layer -> layer.getAlignment() == Constants.ALIGNMENT_INTERVAL)
      .findAny();
    boolean bUseWordContainsJoins = columnTargetSegmentLayer.isPresent()
      && alignedWordLayer.isPresent(); 
    
    int iTargetLayer = SqlConstants.LAYER_TRANSCRIPTION; // by default
    int iTargetColumn = 0; // by default
    int iTargetIndex = 0; // word-internal index for segment context
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
      .flatMap(m -> m.stream())
      .filter(LayerMatch::HasCondition)
      // existing layers
      .filter(layerMatch -> schema.getLayer(layerMatch.getId()) != null)
      // temporal layers
      .filter(layerMatch -> schema.getLayer(layerMatch.getId()).containsKey("layer_id"))
      .forEach(layerMatch -> layersPrimaryFirst.add(layerMatch));
    if (columnTargetSegmentLayer.isPresent()) { // target segment layer
      // move primary segment layer to the front
      int i = 0; // there may be multiple matches for the same layer
      for (LayerMatch seg :matrix.getColumns().get(iWordColumn)
             .getLayers().get(columnTargetSegmentLayer.get().getId())) {
        if (seg.getTarget()) iTargetIndex = i;
        if (layersPrimaryFirst.remove(seg)) { // it was there
          layersPrimaryFirst.add(i++, seg);
        }
      } // next segment layer match of the same ID
    } else if (firstPrimaryWordLayer.isPresent()) { // move primary word layer to the front
      if (layersPrimaryFirst.remove(firstPrimaryWordLayer.get())) { // it was there
        layersPrimaryFirst.add(0, firstPrimaryWordLayer.get());
      }
    }
    // for each specified layer matching pattern
    String lastLayerId = null;
    int sameLastLayerCount = 0;
    StringBuilder anchoredSegmentClause = new StringBuilder();
    for (LayerMatch layerMatch : layersPrimaryFirst) {
      if (bCancelling) break;
      
      layerMatch.setNullBooleans(); // to ensure if()s below don't need to check for null
      Layer layer = schema.getLayer(layerMatch.getId());
      Integer layer_id = (Integer)(layer.get("layer_id"));
      StringBuilder sExtraMetaCondition = new StringBuilder();
      Object[] oLayerId = { layer_id, "_" + iWordColumn };

      if (layer.getId().equals(lastLayerId)) {
        sameLastLayerCount++;
        oLayerId[1] = "_" + iWordColumn + "_" + sameLastLayerCount;
      } else {
        sameLastLayerCount = 0;
      }
      
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
            sExtraMetaCondition.append(fmtSqlStartMetaSpanCondition.format(oLayerId));
          } else {
            sExtraMetaCondition.append(fmtSqlStartFreeformSpanCondition.format(oLayerId));
          }
        }
        if (layerMatch.getAnchorEnd()) {
          if (bPhraseLayer) {
            sExtraMetaCondition.append(fmtSqlEndMetaSpanCondition.format(oLayerId));
          } else {
            sExtraMetaCondition.append(fmtSqlEndFreeformSpanCondition.format(oLayerId));
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
        sExtraMetaCondition.toString(), // e.g. anchoring to the start/end of a span
        (Integer)(columnTargetSegmentLayer.isPresent()?
                  schema.getLayer(columnTargetSegmentLayer.get().getId()).get("layer_id"):null),
        oLayerId[1]
      };
      
      if (LayerMatch.HasCondition(layerMatch)) {
        if (layerMatch.getMin() == null && layerMatch.getMax() != null) { // max only
          if (bPhraseLayer || bSpanLayer) {
            // meta/freeform layer
            if (layerMatch.getAnchorStart() && bWordAnchoredMetaLayer) {
              // anchored to start of word
              sSqlExtraJoinsFirst.append(START_ANCHORED_NUMERIC_MAX_JOIN.format(oArgs));
            } else if (layerMatch.getAnchorEnd() && bWordAnchoredMetaLayer) {
              // anchored to end of word
              sSqlExtraJoinsFirst.append(END_ANCHORED_NUMERIC_MAX_JOIN.format(oArgs));
            } else { // un-anchored meta condition
              if (sSqlExtraJoinsFirst.indexOf(sSqlWordStartJoin) < 0) {
                sSqlExtraJoinsFirst.append(sSqlWordStartJoin);
              }
              if (bPhraseLayer) {
                sSqlExtraJoinsFirst.append(CONTAINING_META_NUMERIC_MAX_JOIN.format(oArgs));
              } else {
                sSqlExtraJoinsFirst.append(CONTAINING_FREEFORM_NUMERIC_MAX_JOIN.format(oArgs));
              }
            } // un-anchored meta condition
          } else if (bUseWordContainsJoins
                     && layer.getAlignment() == Constants.ALIGNMENT_INTERVAL) {
            // word-containing-segment layer
            if (sSqlExtraJoinsFirst.indexOf(sSqlSegmentStartJoin) < 0) {
              sSqlExtraJoinsFirst.append(sSqlSegmentStartJoin);
            }
            sSqlExtraJoinsFirst.append(CONTAINING_WORD_NUMERIC_MAX_JOIN.format(oArgs));
          } else {
            sSqlExtraJoinsFirst.append(NUMERIC_MAX_JOIN.format(oArgs));
          }
          parameters.add(layerMatch.getMax());
        } else if (layerMatch.getMin() != null && layerMatch.getMax() == null) { // min only
          if (bPhraseLayer || bSpanLayer) {
            // meta/freeform layer
            if (layerMatch.getAnchorStart() && bWordAnchoredMetaLayer) {
              // anchored to start of word
              sSqlExtraJoinsFirst.append(START_ANCHORED_NUMERIC_MIN_JOIN.format(oArgs));
            } else if (layerMatch.getAnchorEnd() && bWordAnchoredMetaLayer) {
              // anchored to end of word
              sSqlExtraJoinsFirst.append(END_ANCHORED_NUMERIC_MIN_JOIN.format(oArgs));
            } else { // un-anchored meta condition
              if (sSqlExtraJoinsFirst.indexOf(sSqlWordStartJoin) < 0) {
                sSqlExtraJoinsFirst.append(sSqlWordStartJoin);
              }
              if (bPhraseLayer) {
                sSqlExtraJoinsFirst.append(CONTAINING_META_NUMERIC_MIN_JOIN.format(oArgs));
              } else {
                sSqlExtraJoinsFirst.append(CONTAINING_FREEFORM_NUMERIC_MIN_JOIN.format(oArgs));
              }
            } // un-anchors meta condition
          } else if (bUseWordContainsJoins
                     && layer.getAlignment() == Constants.ALIGNMENT_INTERVAL) {
            // word-containing-segment layer
            if (sSqlExtraJoinsFirst.indexOf(sSqlSegmentStartJoin) < 0) {
              sSqlExtraJoinsFirst.append(sSqlSegmentStartJoin);
            }
            sSqlExtraJoinsFirst.append(CONTAINING_WORD_NUMERIC_MIN_JOIN.format(oArgs));
          } else {
            sSqlExtraJoinsFirst.append(NUMERIC_MIN_JOIN.format(oArgs));
          }
          parameters.add(layerMatch.getMin());
        } else if (layerMatch.getMin() != null && layerMatch.getMax() != null) { // min & max
          if (bPhraseLayer || bSpanLayer) {
            // meta/freeform layer
            if (layerMatch.getAnchorStart() && bWordAnchoredMetaLayer) {
              // anchored to start of word
              sSqlExtraJoinsFirst.append(START_ANCHORED_NUMERIC_RANGE_JOIN.format(oArgs));
            } else if (layerMatch.getAnchorEnd() && bWordAnchoredMetaLayer) {
              // anchored to end of word
              sSqlExtraJoinsFirst.append(END_ANCHORED_NUMERIC_RANGE_JOIN.format(oArgs));
            }  else { // un-anchored meta condition
              if (sSqlExtraJoinsFirst.indexOf(sSqlWordStartJoin) < 0) {
                sSqlExtraJoinsFirst.append(sSqlWordStartJoin);
              }
              if (bPhraseLayer) {
                sSqlExtraJoinsFirst.append(CONTAINING_META_NUMERIC_RANGE_JOIN.format(oArgs));
              } else {
                sSqlExtraJoinsFirst.append(CONTAINING_FREEFORM_NUMERIC_RANGE_JOIN.format(oArgs));
              }
            } // un-anchored meta condition
          } else if (bUseWordContainsJoins
                     && layer.getAlignment() == Constants.ALIGNMENT_INTERVAL) {
            // word-containing-segment layer
            if (sSqlExtraJoinsFirst.indexOf(sSqlSegmentStartJoin) < 0) {
              sSqlExtraJoinsFirst.append(sSqlSegmentStartJoin);
            }
            sSqlExtraJoinsFirst.append(CONTAINING_WORD_NUMERIC_RANGE_JOIN.format(oArgs));
          } else {
            sSqlExtraJoinsFirst.append(NUMERIC_RANGE_JOIN.format(oArgs));
          }
          parameters.add(layerMatch.getMin());
          parameters.add(layerMatch.getMax());
        } else if (layerMatch.getPattern() != null) { // use regexp
          StringBuilder sSqlExtraJoin = new StringBuilder();
          if (bPhraseLayer || bSpanLayer) {
            if (layer.getAlignment() == Constants.ALIGNMENT_INSTANT) {
              if (sSqlExtraJoinsFirst.indexOf(sSqlWordStartJoin) < 0) {
                sSqlExtraJoin.append(sSqlWordStartJoin);
              }
              if (sSqlExtraJoinsFirst.indexOf(sSqlWordEndJoin) < 0) {
                sSqlExtraJoin.append(sSqlWordEndJoin);
              }
              if (bPhraseLayer) {
                sSqlExtraJoin.append(PHRASE_INSTANTS_REGEXP_JOIN.format(oArgs));
              } else {
                sSqlExtraJoin.append(SPAN_INSTANTS_REGEXP_JOIN.format(oArgs));
              }
            } else {
              // meta/freeform layer
              if (layerMatch.getAnchorStart() && bWordAnchoredMetaLayer) {
                // anchored to start of word
                sSqlExtraJoinsFirst.append(START_ANCHORED_REGEXP_JOIN.format(oArgs));
              } else if (layerMatch.getAnchorEnd() && bWordAnchoredMetaLayer) {
                // anchored to end of word
                sSqlExtraJoinsFirst.append(END_ANCHORED_REGEXP_JOIN.format(oArgs));
              } else { // un-anchored meta condition
                if (sSqlExtraJoinsFirst.indexOf(sSqlWordStartJoin) < 0) {
                  sSqlExtraJoin.append(sSqlWordStartJoin);
                }
                if (bPhraseLayer) {
                  sSqlExtraJoin.append(CONTAINING_META_REGEXP_JOIN.format(oArgs));
                } else {
                  sSqlExtraJoin.append(CONTAINING_FREEFORM_REGEXP_JOIN.format(oArgs));
                }
              } // un-anchored meta condition
            }
          } else if (bSegmentLayer) {
            if (segmentMatches.get(0).getId().equals(layer.getId())) { // first segment layer
              if (sameLastLayerCount == 0) {
                sSqlExtraJoinsFirst.append(REGEXP_JOIN.format(oArgs));
                if (layerMatch.getAnchorStart()) {
                   // clause to maybe defer until we join to a word layer
                  anchoredSegmentClause.append(
                    "  AND search_"+iWordColumn+"_"+layer_id+".start_anchor_id"
                    +" = word_0.start_anchor_id");
                  if (!firstPrimaryWordLayer.isPresent()) { // no later word join to defer to
                    sSqlExtraJoinsFirst.append(anchoredSegmentClause.toString());
                    anchoredSegmentClause.setLength(0); // don't defer to later word join
                  }
                }
                if (layerMatch.getAnchorEnd()) {
                  anchoredSegmentClause.append(
                    "  AND search_"+iWordColumn+"_"+layer_id+".end_anchor_id"
                    +" = word_0.end_anchor_id");
                  if (!firstPrimaryWordLayer.isPresent()) { // no later word join to defer to
                    sSqlExtraJoinsFirst.append(anchoredSegmentClause.toString());
                    anchoredSegmentClause.setLength(0); // don't defer to later word join
                  }
                }
                if (bUseWordContainsJoins) {
                  sSqlExtraJoinsFirst.append(
                    " INNER JOIN anchor segment_"+iWordColumn+"_start"
                    +" ON segment_"+iWordColumn+"_start.anchor_id"
                    +" = segment_"+iWordColumn+".start_anchor_id");
                }
              } else { // already have a segment condition
                // join via first segment
                sSqlExtraJoinsFirst.append(
                  REGEXP_JOIN.format(oArgs)
                  .replace("word"+oLayerId[1]+".word_annotation_id",
                           "search_"+iWordColumn+"_"+layer_id+".word_annotation_id"
                           +" AND search"+oLayerId[1]+"_"+layer_id+".ordinal_in_word"
                           +" = search_"+iWordColumn+"_"+layer_id+".ordinal_in_word + "
                           + sameLastLayerCount));
                if (layerMatch.getAnchorStart()) {
                  throw new Exception("Can only anchor first segment to word start.");
                }
                if (layerMatch.getAnchorEnd()) {
                  anchoredSegmentClause.append(
                    "  AND search"+oLayerId[1]+"_"+layer_id+".end_anchor_id"
                    +" = word_0.end_anchor_id");
                  if (!firstPrimaryWordLayer.isPresent()) { // no later word join to defer to
                    sSqlExtraJoinsFirst.append(anchoredSegmentClause.toString());
                    anchoredSegmentClause.setLength(0); // don't defer to later word join
                  }
                }
              }
            } else {
              sSqlExtraJoin.append(SEGMENT_REGEXP_JOIN.format(oArgs));
            }
          } else if (bUseWordContainsJoins
                     && layer.getAlignment() == Constants.ALIGNMENT_INTERVAL) {
            sSqlExtraJoin.append(CONTAINING_WORD_REGEXP_JOIN.format(oArgs));
          } else {
            if (columnTargetSegmentLayer.isPresent()) {
              sSqlExtraJoin.append(
                REGEXP_JOIN.format(oArgs).replace(
                  "word_"+iWordColumn+".",
                  "search_"+iWordColumn+"_"+schema.getLayer(columnTargetSegmentLayer.get().getId())
                  .get("layer_id")+"."));
            } else {
              sSqlExtraJoin.append(REGEXP_JOIN.format(oArgs));
            }
          }
          if (layerMatch.getNot() && ".+".equals(layerMatch.getPattern())) { // NOT EXISTS
            // special case: "NOT .+" means "not anything" - i.e. missing annotations
            sSqlExtraJoin = new StringBuilder(
              sSqlExtraJoin.toString()
              // change join to be LEFT OUTER...
              .replace("INNER JOIN annotation_layer_"+layer_id,
                       "LEFT OUTER JOIN annotation_layer_"+layer_id)
              // ...or meta join to be LEFT OUTER...
              .replace("INNER JOIN (annotation_layer_"+layer_id,
                       "LEFT OUTER JOIN (annotation_layer_"+layer_id)
              // and remove the pattern match
              .replaceAll("AND (CAST\\()?search_[0-9]+_"+layer_id
                          +"\\.label( AS BINARY\\))? NOT REGEXP (BINARY)? \\?", ""));
            // and add a test for NULL to the WHERE clause
            sSqlLayerMatchesFirst.append(NULL_ANNOTATION_CONDITION.format(oArgs));
          } else { // REGEXP MATCH
            // add implicit ^ and $
            layerMatch.ensurePatternAnchored();
            // save the layer and string
            // for later adding to the parameters of the query
            parameters.add(layerMatch.getPattern());
          }
          sSqlExtraJoinsFirst.append(sSqlExtraJoin);
        } // use regexp

        // do we need to add a segment-to-word-anchoring clause that was deferred?
        if (anchoredSegmentClause.length() > 0
            && firstPrimaryWordLayer.isPresent()
            && firstPrimaryWordLayer.get() == layerMatch) {
          sSqlExtraJoinsFirst.append(anchoredSegmentClause.toString());
          anchoredSegmentClause.setLength(0); // don't defer to later word join
        }
        
        setStatus(
          "Layer: '" + layer.getId() 
          + "' pattern: " + (layerMatch.getNot()?"NOT":"")
          + " " + layerMatch.getPattern()
          + " " + layerMatch.getMin() + " " + layerMatch.getMax()
          + " " + (layerMatch.getAnchorStart()?"[":"")
          + " " + (layerMatch.getAnchorEnd()?"]":""));
	       
        if (targetLayerId.equals(layerMatch.getId()) && iWordColumn == iTargetColumn) {
          sSqlExtraFieldsFirst.append(
            ", search_" + iTargetLayer + ".annotation_id AS target_annotation_id");
          targetExpression = "search_" + iWordColumn
            +(iTargetIndex==0?"":"_"+iTargetIndex)
            + "_" + iTargetLayer
            + ".annotation_id";
          bTargetAnnotation = true;
        }

        // note this layer ID, in case the next layer is the same (e.g. intra-word phone context)
        lastLayerId = layerMatch.getId();
      } // matching this layer
    } // next layer

    if (bCancelling) throw new Exception("Cancelled.");

    if (columnTargetSegmentLayer.isPresent()
        && columnTargetSegmentLayer.get().getTarget()) {
      sSqlExtraFieldsFirst.append(
        ", search_" + schema.getLayer(columnTargetSegmentLayer.get().getId()).get("layer_id")
        + ".segment_annotation_id AS target_segment_id");
      targetSegmentExpression = "search_0_"
        + (iTargetIndex==0?"":""+iTargetIndex+"_")
        + schema.getLayer(columnTargetSegmentLayer.get().getId()).get("layer_id")
        + ".segment_annotation_id";
      targetSegmentOrder = ", search_0_"
        + schema.getLayer(columnTargetSegmentLayer.get().getId()).get("layer_id")
        + ".ordinal_in_word";
    } else if (alignedWordLayer.isPresent()
               && alignedWordLayer.get().getId().equals(targetLayerId)
               && iTargetColumn == iWordColumn) {
      // if we're targeting a word child layer, we want to add "ordinal" to the ORDER BY
      targetSegmentOrder = ", search_"+iWordColumn+"_"
        + schema.getLayer(alignedWordLayer.get().getId()).get("layer_id")
        + ".ordinal";
    }

    // start border condition
    String sStartConditionFirst = "";
    if (matrix.getColumns().get(0).getLayers().values().stream()
        .flatMap(m -> m.stream())
        .filter(layerMatch -> layerMatch.getAnchorStart()) // start anchored to utterance
        .filter(layerMatch -> schema.getUtteranceLayerId().equals(layerMatch.getId()))
        .findAny().isPresent()) { // start of utterance
      if (sSqlExtraJoinsFirst.indexOf(sSqlWordStartJoin) < 0) {
        sSqlExtraJoinsFirst.append(sSqlWordStartJoin);
      }
      if (sSqlExtraJoinsFirst.indexOf(sSqlLineJoin) < 0) {
        sSqlExtraJoinsFirst.append(sSqlLineJoin);
      }
      if (sSqlExtraJoinsFirst.indexOf(sSqlLineStartJoin) < 0) {
        sSqlExtraJoinsFirst.append(sSqlLineStartJoin);
      }
      if (sSqlExtraJoinsFirst.indexOf(sSqlStartLineJoin) < 0) {
        sSqlExtraJoinsFirst.append(sSqlStartLineJoin);
      }
      sStartConditionFirst = sSqlStartLineCondition;
    } else if (matrix.getColumns().get(0).getLayers().values().stream()
               .flatMap(m -> m.stream())
               .filter(layerMatch -> layerMatch.getAnchorStart()) // start anchored to turn
               .filter(layerMatch -> schema.getTurnLayerId().equals(layerMatch.getId()))
               .findAny().isPresent()) { // start of turn
      sStartConditionFirst = sqlStartTurnCondition;
    }
	 
    // is this also the last column?
    if (matrix.getColumns().size() == 1) {   
      // end condition
      if (matrix.getColumns().get(0).getLayers().values().stream()
          .flatMap(m -> m.stream())
          .filter(layerMatch -> layerMatch.getAnchorEnd()) // end anchored to utterance
          .filter(layerMatch -> schema.getUtteranceLayerId().equals(layerMatch.getId()))
          .findAny().isPresent()) { // end of utterance
        if (sSqlExtraJoinsFirst.indexOf(sSqlWordStartJoin) < 0) {
          sSqlExtraJoinsFirst.append(sSqlWordStartJoin);
        }
        if (sSqlExtraJoinsFirst.indexOf(sSqlLineJoin) < 0) {
          sSqlExtraJoinsFirst.append(sSqlLineJoin);
        }
        if (sSqlExtraJoinsFirst.indexOf(sSqlLineEndJoin) < 0) {
          sSqlExtraJoinsFirst.append(sSqlLineEndJoin);
        }
        if (sSqlExtraJoinsFirst.indexOf(sSqlEndLineJoin) < 0) {
          sSqlExtraJoinsFirst.append(sSqlEndLineJoin);
        }
        sSqlLayerMatchesFirst.append(sqlEndLineCondition.format(columnSuffix));
      } else if (matrix.getColumns().get(0).getLayers().values().stream()
                 .flatMap(m -> m.stream())
                 .filter(layerMatch -> layerMatch.getAnchorEnd()) // end anchored to turn
                 .filter(layerMatch -> schema.getTurnLayerId().equals(layerMatch.getId()))
                 .findAny().isPresent()) { // end of turn
        if (sSqlExtraJoinsFirst.indexOf(sSqlEndTurnJoin) < 0) {
          sSqlExtraJoinsFirst.append(sSqlEndTurnJoin);
        }
        sSqlLayerMatchesFirst.append(sqlEndTurnCondition.format(columnSuffix));
      }
    } // last column

    // aligned words only?
    if (anchorConfidenceThreshold != null) {
      if (sSqlExtraJoinsFirst.indexOf(sSqlWordStartJoin) < 0) {
        sSqlExtraJoinsFirst.append(
          sSqlWordStartJoin
          + " AND word_"+iWordColumn+"_start.alignment_status >= "
          + anchorConfidenceThreshold);
      } else { // update existing join
        String primaryWordTable = "word_"+iWordColumn;
        if (firstPrimaryWordLayer.isPresent()) {
          primaryWordTable = "search_"+iWordColumn+"_"
            +schema.getLayer(firstPrimaryWordLayer.get().getId()).get("layer_id");
        }
        sSqlExtraJoinsFirst = new StringBuilder(
          sSqlExtraJoinsFirst.toString().replaceAll(
            " ON word_"+iWordColumn+"_start\\.anchor_id = "+primaryWordTable+"\\.start_anchor_id",
            " ON word_"+iWordColumn+"_start\\.anchor_id = "+primaryWordTable+"\\.start_anchor_id"
            + " AND word_"+iWordColumn+"_start.alignment_status >= "
            + anchorConfidenceThreshold));
      }
      if (sSqlExtraJoinsFirst.indexOf(sSqlWordEndJoin) < 0) {
        sSqlExtraJoinsFirst.append(
          sSqlWordEndJoin
          + " AND word_"+iWordColumn+"_end.alignment_status >= "
          + anchorConfidenceThreshold);
      } else { // update existing join
        sSqlExtraJoinsFirst = new StringBuilder(
          sSqlExtraJoinsFirst.toString().replaceAll(
            " ON word_"+iWordColumn+"_end\\.anchor_id = word_"+iWordColumn+"\\.end_anchor_id",
            " ON word_"+iWordColumn+"_start\\.end_id = word_"+iWordColumn+"\\.end_anchor_id"
            + " AND word_"+iWordColumn+"_end.alignment_status >= "
            + anchorConfidenceThreshold));
      }
      if (columnTargetSegmentLayer.isPresent()) {
        // segment threshold too
        if (sSqlExtraJoinsFirst.indexOf(sSqlSegmentStartJoin) < 0) {
          sSqlExtraJoinsFirst.append(
            sSqlSegmentStartJoin
            + " AND segment_"+iWordColumn+"_start.alignment_status >= "
            + anchorConfidenceThreshold);
        } else { // update existing join
          sSqlExtraJoinsFirst = new StringBuilder(
            sSqlExtraJoinsFirst.toString().replaceAll(
              " ON segment_"+iWordColumn+"_start\\.anchor_id"
              +" = segment_"+iWordColumn+"\\.start_anchor_id",
              " ON segment_"+iWordColumn+"_start\\.anchor_id"
              +" = segment_"+iWordColumn+"\\.start_anchor_id"
              + " AND segment_"+iWordColumn+"_start.alignment_status >= "
              + anchorConfidenceThreshold));
        }
        if (sSqlExtraJoinsFirst.indexOf(sSqlSegmentEndJoin) < 0) {
          sSqlExtraJoinsFirst.append(
            sSqlSegmentEndJoin
            + " AND segment_"+iWordColumn+"_end.alignment_status >= "
            + anchorConfidenceThreshold);
        } else { // update existing join
          sSqlExtraJoinsFirst = new StringBuilder(
            sSqlExtraJoinsFirst.toString().replaceAll(
              " ON segment_"+iWordColumn+"_end\\.anchor_id"
              +" = segment_"+iWordColumn+"\\.end_anchor_id",
              " ON segment_"+iWordColumn+"_end\\.end_id"
              +" = segment_"+iWordColumn+"\\.end_anchor_id"
              + " AND segment_"+iWordColumn+"_end.alignment_status >= "
              + anchorConfidenceThreshold));
        }
      } // there's a target segment layer
    } // aligned only

    // access restrictions?
    String strAccessWhere = "";
    if (restrictByUser != null) {
      strAccessWhere = " AND (EXISTS (SELECT * FROM role"
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
        + " AND user_id = ?))";
      if (sSqlExtraJoinsFirst.indexOf(sSqlTranscriptJoin) < 0) {
        sSqlExtraJoinsFirst.append(sSqlTranscriptJoin);
      }
    } // filtering by role
    String strSpeakerWhere = participantCondition.apply(matrix.getParticipantQuery());
    if (strSpeakerWhere == null && getLastException() != null) {
      throw (Exception)getLastException();
    }
    
    // now match subsequent columns
    StringBuilder strSubsequentSelect = new StringBuilder();
    StringBuilder strSubsequentJoin = new StringBuilder();
    StringBuilder strSubsequentWhere = new StringBuilder();
    for (
      iWordColumn = 1; 
      iWordColumn < matrix.getColumns().size() && !bCancelling; 
      iWordColumn++) {
      
      StringBuilder sSqlExtraJoins = new StringBuilder();
      StringBuilder sSqlExtraFields = new StringBuilder();
      StringBuilder sSqlLayerMatches = new StringBuilder();
      Column column = matrix.getColumns().get(iWordColumn);
      segmentMatches = column
        .getLayers().values().stream()
        .flatMap(m -> m.stream())
        .filter(LayerMatch::HasCondition)
        .filter(layerMatch -> schema.getLayer(layerMatch.getId()) != null)
        .filter(layerMatch -> IsSegmentLayer(schema.getLayer(layerMatch.getId()), schema))
        .collect(Collectors.toList());
      // if there's an explicitly targetted one, us that
      columnTargetSegmentLayer = segmentMatches.stream()
        .filter(LayerMatch::IsTarget)
        .findAny();
      if (!columnTargetSegmentLayer.isPresent()) { // no explicitly targeted layer
        // target the first segment layer
        columnTargetSegmentLayer = segmentMatches.stream().findAny();
      }
      primarySegmentLayers.add(columnTargetSegmentLayer);
      
      // do we need a word layer to anchor to?
      Optional<LayerMatch> primaryWordLayer = column.getLayers().values().stream()
        .flatMap(m -> m.stream())
        .filter(LayerMatch::HasCondition)
        // not a "NOT .+" expression
        .filter(layerMatch -> !layerMatch.getNot() || !".+".equals(layerMatch.getPattern()))
        .filter(layerMatch -> schema.getLayer(layerMatch.getId()) != null)
        // word scope
        .filter(layerMatch -> schema.getWordLayerId().equals(layerMatch.getId())
                || (schema.getWordLayerId().equals(
                      schema.getLayer(layerMatch.getId()).getParentId())
                    && !layerMatch.getId().equals("segment")))
        .findAny();
      primaryWordLayers.add(primaryWordLayer);
      if (primaryWordLayer.isPresent()) {
        // change word start/end joins to match word layer table
        sSqlWordStartJoin = sSqlWordStartJoin
          .replace("word_"+iWordColumn+".", "search_"+iWordColumn+"_"
                   +schema.getLayer(primaryWordLayer.get().getId()).get("layer_id")+".");
        sSqlWordEndJoin = sSqlWordEndJoin
          .replace("word_"+iWordColumn+".", "search_"+iWordColumn+"_"
                   +schema.getLayer(primaryWordLayer.get().getId()).get("layer_id")+".");
      } else { // no primary word layer
        Object oSubPatternMatchArgs[] = { 
          null, // extra JOINs
          null, // border conditions
          null, // regexp/range subqueries
          null, // for line info
          "_" + iWordColumn, // column suffix
          "_" + (iWordColumn-1) // previous column suffix
        };
        sSqlExtraJoins.append(sqlPatternMatchSubsequentJoin.format(oSubPatternMatchArgs));        
      }
      
      columnSuffix[0] = "_" + iWordColumn;
      sSqlWordStartJoin = sqlWordStartJoin.format(columnSuffix);
      sSqlWordEndJoin = sqlWordEndJoin.format(columnSuffix);
      sSqlSegmentStartJoin = sqlSegmentStartJoin.format(columnSuffix);
      sSqlSegmentEndJoin = sqlSegmentEndJoin.format(columnSuffix);
      sSqlLineJoin = primaryWordLayer.isPresent()?
        sqlLineJoinViaToken.format(columnSuffix)
        :sqlDirectLineJoin.format(columnSuffix);
      sSqlLineStartJoin = sqlLineStartJoin.format(columnSuffix);
      sSqlLineEndJoin = sqlLineEndJoin.format(columnSuffix);
      sSqlEndLineJoin = sqlEndLineJoin.format(columnSuffix);
      sSqlEndTurnJoin = sqlEndTurnJoin.format(columnSuffix);
      
      alignedWordLayer = matrix.getColumns().get(iWordColumn)
        .getLayers().values().stream()
        .flatMap(m -> m.stream())
        .filter(LayerMatch::HasCondition)
        .map(layerMatch -> schema.getLayer(layerMatch.getId()))
        .filter(Objects::nonNull)
        .filter(layer -> IsWordLayer(layer, schema))
        .filter(layer -> !layer.getId().equals(schema.getWordLayerId()))
        .filter(layer -> layer.getAlignment() == Constants.ALIGNMENT_INTERVAL)
        .findAny();
      bUseWordContainsJoins = columnTargetSegmentLayer.isPresent()
        && alignedWordLayer.isPresent();
    
      layersPrimaryFirst.clear();
      column.getLayers().values().stream()
        .flatMap(m -> m.stream())
        .filter(LayerMatch::HasCondition)
        // existing layers
        .filter(layerMatch -> schema.getLayer(layerMatch.getId()) != null)
        // temporal layers
        .filter(layerMatch -> schema.getLayer(layerMatch.getId()).containsKey("layer_id"))
        .forEach(layerMatch -> layersPrimaryFirst.add(layerMatch));
      if (columnTargetSegmentLayer.isPresent()) { // move primary segment layer to the front
        int i = 0; // there may be multiple matches for the same segment layer
        for (LayerMatch seg :matrix.getColumns().get(iWordColumn)
               .getLayers().get(columnTargetSegmentLayer.get().getId())) {
          if (seg.getTarget()) iTargetIndex = i;
          if (layersPrimaryFirst.remove(seg)) { // it was there
            layersPrimaryFirst.add(i++, seg);
          }
        } // next segment layer match of the same ID
      } else if (primaryWordLayer.isPresent()) {
        // move it to the front
        if (layersPrimaryFirst.remove(primaryWordLayer.get())) { // it was there
          layersPrimaryFirst.add(0, primaryWordLayer.get());
        }
      }
      
      // look for a layer with a search specification
      lastLayerId = null;
      sameLastLayerCount = 0;
      anchoredSegmentClause.setLength(0);
      for (LayerMatch layerMatch : layersPrimaryFirst) {
        if (bCancelling) break;
        layerMatch.setNullBooleans();        
        Layer layer = schema.getLayer(layerMatch.getId());
        Integer layer_id = (Integer)(layer.get("layer_id"));
        StringBuilder sExtraMetaCondition = new StringBuilder();
        Object[] oLayerId = { (Integer)(layer.get("layer_id")), "_" + iWordColumn };

        if (layer.getId().equals(lastLayerId)) {
          sameLastLayerCount++;
          oLayerId[1] = "_" + iWordColumn + "_" + sameLastLayerCount;
        } else {
          sameLastLayerCount = 0;
        }
	
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
              sExtraMetaCondition.append(fmtSqlStartMetaSpanCondition.format(oLayerId));
            } else {
              sExtraMetaCondition.append(fmtSqlStartFreeformSpanCondition.format(oLayerId));
            }
          }
          if (layerMatch.getAnchorEnd()) {
            if (bPhraseLayer) {
              sExtraMetaCondition.append(fmtSqlEndMetaSpanCondition.format(oLayerId));
            } else {
              sExtraMetaCondition.append(fmtSqlEndFreeformSpanCondition.format(oLayerId));
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
          sExtraMetaCondition.toString(), // e.g. anchoring to the start of a span
          null,
          oLayerId[1]
        };
        
        if (LayerMatch.HasCondition(layerMatch)) {
          if (layerMatch.getMin() == null && layerMatch.getMax() != null) { // max only
            if (bPhraseLayer || bSpanLayer) {
              // meta/freeform layer
              if (layerMatch.getAnchorStart() && bWordAnchoredMetaLayer) {
                // anchored to start of word
                sSqlExtraJoins.append(START_ANCHORED_NUMERIC_MAX_JOIN.format(oArgs));
              } else if (layerMatch.getAnchorEnd() && bWordAnchoredMetaLayer) {
                // anchored to end of word
                sSqlExtraJoins.append(END_ANCHORED_NUMERIC_MAX_JOIN.format(oArgs));
              } else { // un-anchored meta condition
                if (sSqlExtraJoins.indexOf(sSqlWordStartJoin) < 0) {
                  sSqlExtraJoins.append(sSqlWordStartJoin);
                }
                if (bPhraseLayer) {
                  sSqlExtraJoins.append(CONTAINING_META_NUMERIC_MAX_JOIN.format(oArgs));
                } else {
                  sSqlExtraJoins.append(CONTAINING_FREEFORM_NUMERIC_MAX_JOIN.format(oArgs));
                }
              } // un-anchored meta condition
            } else if (bUseWordContainsJoins
                       && layer.getAlignment() == Constants.ALIGNMENT_INTERVAL) {
              // word-containing-segment layer
              if (sSqlExtraJoins.indexOf(sSqlSegmentStartJoin) < 0) {
                sSqlExtraJoins.append(sSqlSegmentStartJoin);
              }
              sSqlExtraJoins.append(CONTAINING_WORD_NUMERIC_MAX_JOIN.format(oArgs));
            } else {
              sSqlExtraJoins.append(NUMERIC_MAX_JOIN.format(oArgs));
            }
            parameters.add(layerMatch.getMax());
          } else if (layerMatch.getMin() != null && layerMatch.getMax() == null) { // min only
            if (bPhraseLayer || bSpanLayer) {
              // meta/freeform layer
              if (layerMatch.getAnchorStart() && bWordAnchoredMetaLayer) {
                // anchored to start of word
                sSqlExtraJoins.append(START_ANCHORED_NUMERIC_MIN_JOIN.format(oArgs));
              } else if (layerMatch.getAnchorEnd() && bWordAnchoredMetaLayer) {
                // anchored to end of word
                sSqlExtraJoins.append(END_ANCHORED_NUMERIC_MIN_JOIN.format(oArgs));
              } else { // un-anchored meta condition
                if (sSqlExtraJoins.indexOf(sSqlWordStartJoin) < 0) {
                  sSqlExtraJoins.append(sSqlWordStartJoin);
                }
                if (bPhraseLayer) {
                  sSqlExtraJoins.append(CONTAINING_META_NUMERIC_MIN_JOIN.format(oArgs));
                } else {
                  sSqlExtraJoins.append(CONTAINING_FREEFORM_NUMERIC_MIN_JOIN.format(oArgs));
                }
              } // un-anchored meta condition
            } else if (bUseWordContainsJoins
                       && layer.getAlignment() == Constants.ALIGNMENT_INTERVAL) {
              // word-containing-segment layer
              if (sSqlExtraJoins.indexOf(sSqlSegmentStartJoin) < 0) {
                sSqlExtraJoins.append(sSqlSegmentStartJoin);
              }
              sSqlExtraJoins.append(CONTAINING_WORD_NUMERIC_MIN_JOIN.format(oArgs));
            } else {
              sSqlExtraJoins.append(NUMERIC_MIN_JOIN.format(oArgs));
            }
            parameters.add(layerMatch.getMin());
          } else if (layerMatch.getMin() != null && layerMatch.getMax() != null) { // min&max
            if (bPhraseLayer || bSpanLayer) {
              // meta/freeform layer
              if (layerMatch.getAnchorStart() && bWordAnchoredMetaLayer) {
                // anchored to start of word
                sSqlExtraJoins.append(START_ANCHORED_NUMERIC_RANGE_JOIN.format(oArgs));
              } else if (layerMatch.getAnchorEnd() && bWordAnchoredMetaLayer) {
                // anchored to end of word
                sSqlExtraJoins.append(END_ANCHORED_NUMERIC_RANGE_JOIN.format(oArgs));
              } else { // un-anchored meta condition
                if (sSqlExtraJoins.indexOf(sSqlWordStartJoin) < 0) {
                  sSqlExtraJoins.append(sSqlWordStartJoin);
                }
                if (bPhraseLayer) {
                  sSqlExtraJoins.append(CONTAINING_META_NUMERIC_RANGE_JOIN.format(oArgs));
                } else {
                  sSqlExtraJoins.append(CONTAINING_FREEFORM_NUMERIC_RANGE_JOIN.format(oArgs));
                }
              } // un-anchored meta condition
            } else if (bUseWordContainsJoins
                       && layer.getAlignment() == Constants.ALIGNMENT_INTERVAL) {
              // word-containing-segment layer
              if (sSqlExtraJoins.indexOf(sSqlSegmentStartJoin) < 0) {
                sSqlExtraJoins.append(sSqlSegmentStartJoin);
              }
              sSqlExtraJoins.append(CONTAINING_WORD_NUMERIC_RANGE_JOIN.format(oArgs));
            } else {
              sSqlExtraJoins.append(NUMERIC_RANGE_JOIN.format(oArgs));
            }
            parameters.add(layerMatch.getMin());
            parameters.add(layerMatch.getMax());
          } else if (layerMatch.getPattern() != null) { // use regexp
            StringBuilder sSqlExtraJoin = new StringBuilder();
            if (bPhraseLayer || bSpanLayer) {
              if (layer.getAlignment() == Constants.ALIGNMENT_INSTANT) {
                // meta/freeform layer
                if (sSqlExtraJoinsFirst.indexOf(sSqlWordStartJoin) < 0) {
                  sSqlExtraJoin.append(sSqlWordStartJoin);
                }
                if (sSqlExtraJoins.indexOf(sSqlWordEndJoin) < 0) {
                  sSqlExtraJoin.append(sSqlWordEndJoin);
                }
                if (bPhraseLayer) {
                  sSqlExtraJoin.append(PHRASE_INSTANTS_REGEXP_JOIN.format(oArgs));
                } else {
                  sSqlExtraJoin.append(SPAN_INSTANTS_REGEXP_JOIN.format(oArgs));
                }
              } else {
                // meta/freeform layer
                if (layerMatch.getAnchorStart() && bWordAnchoredMetaLayer) {
                  // anchored to start of word
                  sSqlExtraJoin.append(START_ANCHORED_REGEXP_JOIN.format(oArgs));
                } else if (layerMatch.getAnchorEnd() && bWordAnchoredMetaLayer) {
                  // anchored to end of word
                  sSqlExtraJoin.append(END_ANCHORED_REGEXP_JOIN.format(oArgs));
                }  else { // un-anchored meta condition
                  if (sSqlExtraJoins.indexOf(sSqlWordStartJoin) < 0) {
                    sSqlExtraJoin.append(sSqlWordStartJoin);
                  }
                  if (bPhraseLayer) {
                    sSqlExtraJoin.append(CONTAINING_META_REGEXP_JOIN.format(oArgs));
                  } else {
                    sSqlExtraJoin.append(CONTAINING_FREEFORM_REGEXP_JOIN.format(oArgs));
                  }
                } // un-anchored meta condition
              }
            } else if (bSegmentLayer) {
              if (segmentMatches.get(0).getId().equals(layer.getId())) { // first segment layer
                if (sameLastLayerCount == 0) {
                  sSqlExtraJoin.append(REGEXP_JOIN.format(oArgs));
                  if (layerMatch.getAnchorStart()) {
                    // clause to maybe defer until we join to a word layer
                    anchoredSegmentClause.append(
                      "  AND search_"+iWordColumn+"_"+layer_id+".start_anchor_id"
                      +" = word_"+iWordColumn+".start_anchor_id");
                    if (!primaryWordLayer.isPresent()) { // no later word join to defer to
                      sSqlExtraJoin.append(anchoredSegmentClause.toString());
                      anchoredSegmentClause.setLength(0); // don't defer to later word join
                    }
                  }
                  if (layerMatch.getAnchorEnd()) {
                    anchoredSegmentClause.append(
                      "  AND search_"+iWordColumn+"_"+layer_id+".end_anchor_id"
                      +" = word_"+iWordColumn+".end_anchor_id");
                    if (!primaryWordLayer.isPresent()) { // no later word join to defer to
                      sSqlExtraJoin.append(anchoredSegmentClause.toString());
                      anchoredSegmentClause.setLength(0); // don't defer to later word join
                    }
                  }
                  if (bUseWordContainsJoins) {
                    sSqlExtraJoin.append(
                      " INNER JOIN anchor segment_"+iWordColumn+"_start"
                      +" ON segment_"+iWordColumn+"_start.anchor_id"
                      +" = segment_"+iWordColumn+".start_anchor_id");
                  }
                } else { // already have a segment condition
                  // join via first segment
                  sSqlExtraJoin.append(
                    REGEXP_JOIN.format(oArgs)
                    .replace("word"+oLayerId[1]+".word_annotation_id",
                             "search_"+iWordColumn+"_"+layer_id+".word_annotation_id"
                             +" AND search"+oLayerId[1]+"_"+layer_id+".ordinal_in_word"
                             +" = search_"+iWordColumn+"_"+layer_id+".ordinal_in_word + "
                             + sameLastLayerCount));
                  if (layerMatch.getAnchorStart()) {
                    throw new Exception("Can only anchor first segment to word start.");
                  }
                  if (layerMatch.getAnchorEnd()) {
                    anchoredSegmentClause.append(
                      "  AND search"+oLayerId[1]+"_"+layer_id+".end_anchor_id"
                      +" = word_"+iWordColumn+".end_anchor_id");
                    if (!primaryWordLayer.isPresent()) { // no later word join to defer to
                      sSqlExtraJoin.append(anchoredSegmentClause.toString());
                      anchoredSegmentClause.setLength(0); // don't defer to later word join
                    }
                  }
                }
              } else {
                sSqlExtraJoin.append(SEGMENT_REGEXP_JOIN.format(oArgs));
              }
            } else if (bUseWordContainsJoins
                       && layer.getAlignment() == Constants.ALIGNMENT_INTERVAL) {
              // word-containing-segment layer
              if (sSqlExtraJoins.indexOf(sSqlSegmentStartJoin) < 0) {
                sSqlExtraJoin.append(sSqlSegmentStartJoin);
              }
              sSqlExtraJoin.append(CONTAINING_WORD_REGEXP_JOIN.format(oArgs));
            } else {
              if (columnTargetSegmentLayer.isPresent()) {
                sSqlExtraJoin.append(
                  REGEXP_JOIN.format(oArgs).replace(
                    "word_"+iWordColumn+".",
                    "search_"+iWordColumn+"_"+schema.getLayer(columnTargetSegmentLayer.get().getId())
                    .get("layer_id")+"."));
              } else {
                sSqlExtraJoin.append(REGEXP_JOIN.format(oArgs));
              }
            }
            if (layerMatch.getNot() && ".+".equals(layerMatch.getPattern())) { // NOT EXISTS
              // special case: "NOT .+" means "not anything" - i.e. missing annotations
              sSqlExtraJoin = new StringBuilder(
                sSqlExtraJoin.toString()
                // change join to be LEFT OUTER...
                .replace("INNER JOIN annotation_layer_"+layer_id,
                         "LEFT OUTER JOIN annotation_layer_"+layer.getId())
                // ...or meta join to be LEFT OUTER...
                .replace("INNER JOIN (annotation_layer_"+layer_id,
                         "LEFT OUTER JOIN (annotation_layer_"+layer.getId())
                // and remove the pattern match
                .replaceAll("AND (CAST\\()?search_[0-9]+_"+layer_id
                            +"\\.label( AS BINARY\\))? NOT REGEXP (BINARY)? \\?", ""));
              // and add a test for NULL to the WHERE clause
              sSqlLayerMatches.append(NULL_ANNOTATION_CONDITION.format(oArgs));
            } else { // REGEXP MATCH
              // regexp - add implicit ^ and $
              layerMatch.ensurePatternAnchored();
              // save the layer and string
              // for later adding to the parameters of the query
              parameters.add(layerMatch.getPattern());
            }
            sSqlExtraJoins.append(sSqlExtraJoin);
          }

          if (layer.getId().equals(targetLayerId) && iWordColumn == iTargetColumn) {
            sSqlExtraFields.append(
              ", search_"+iWordColumn+"_" + iTargetLayer
              + ".annotation_id AS target_annotation_id");
            targetExpression = "search_"+iWordColumn
              +(iTargetIndex==0?"":"_"+iTargetIndex)
              +"_" + iTargetLayer
              + ".annotation_id";
            bTargetAnnotation = true;
          }

          // note this layer ID, in case the next layer is the same (e.g. intra-word phone context)
          lastLayerId = layerMatch.getId();

          // do we need to add a segment-to-word-anchoring clause that was deferred?
          if (anchoredSegmentClause.length() > 0
              && primaryWordLayer.isPresent()
              && primaryWordLayer.get() == layerMatch) {
            sSqlExtraJoins.append(anchoredSegmentClause.toString());
            anchoredSegmentClause.setLength(0); // don't defer to later word join
          }        
          
          setStatus(
            "Layer: '" + layer.getId() 
            + "' pattern: " + (layerMatch.getNot()?"NOT":"")
            + " " + layerMatch.getPattern()
            + " " + layerMatch.getMin() + " " + layerMatch.getMax()
            + " " + (layerMatch.getAnchorStart()?"[":"")
            + " " + (layerMatch.getAnchorEnd()?"]":""));
          
        } // matching this layer
      } // next layer
      if (results != null) ((SqlSearchResults)results).setName(matrix.getDescription());
      if (bCancelling) throw new Exception("Cancelled.");
      
      // aligned words only?
      if (anchorConfidenceThreshold != null) {
        if (sSqlExtraJoins.indexOf(sSqlWordStartJoin) < 0) {
          sSqlExtraJoins.append(
            sSqlWordStartJoin
            + " AND word_"+iWordColumn+"_start.alignment_status >= "
            + anchorConfidenceThreshold);
        } else { // update existing join
          sSqlExtraJoins = new StringBuilder(
            sSqlExtraJoins.toString().replaceAll(
              " ON word_"+iWordColumn+"_start\\.anchor_id = word_"
              +iWordColumn+"\\.start_anchor_id",
              " ON word_"+iWordColumn+"_start\\.anchor_id = word_"
              +iWordColumn+"\\.start_anchor_id"
              + " AND word_"+iWordColumn+"_start.alignment_status >= "
              + anchorConfidenceThreshold));
        }
        if (sSqlExtraJoins.indexOf(sSqlWordEndJoin) < 0) {
          sSqlExtraJoins.append(
            sSqlWordEndJoin
            + " AND word_"+iWordColumn+"_end.alignment_status >= "
            + anchorConfidenceThreshold);
        } else { // update existing join
          sSqlExtraJoins = new StringBuilder(
            sSqlExtraJoins.toString().replaceAll(
              " ON word_"+iWordColumn+"_end\\.anchor_id = word_"+iWordColumn+"\\.end_anchor_id",
              " ON word_"+iWordColumn+"_start\\.end_id = word_"+iWordColumn+"\\.end_anchor_id"
              + " AND word_"+iWordColumn+"_end.alignment_status >= "
              + anchorConfidenceThreshold));
        }
        if (columnTargetSegmentLayer.isPresent()) {
          // segment threshold too
          if (sSqlExtraJoins.indexOf(sSqlSegmentStartJoin) < 0) {
            sSqlExtraJoins.append(
              sSqlSegmentStartJoin
              + " AND segment_"+iWordColumn+"_start.alignment_status >= "
              + anchorConfidenceThreshold);
          } else { // update existing join
            sSqlExtraJoins = new StringBuilder(
              sSqlExtraJoins.toString().replaceAll(
                " ON segment_"+iWordColumn+"_start\\.anchor_id"
                +" = segment_"+iWordColumn+"\\.start_anchor_id",
                " ON segment_"+iWordColumn+"_start\\.anchor_id"
                +" = segment_"+iWordColumn+"\\.start_anchor_id"
                + " AND segment_"+iWordColumn+"_start.alignment_status >= "
                + anchorConfidenceThreshold));
          }
          if (sSqlExtraJoins.indexOf(sSqlSegmentEndJoin) < 0) {
            sSqlExtraJoins.append(
              sSqlSegmentEndJoin
              + " AND segment_"+iWordColumn+"_end.alignment_status >= "
              + anchorConfidenceThreshold);
          } else { // update existing join
            sSqlExtraJoins = new StringBuilder(
              sSqlExtraJoins.toString().replaceAll(
                " ON segment_"+iWordColumn+"_end\\.anchor_id"
                +" = segment_"+iWordColumn+"\\.end_anchor_id",
                " ON segment_"+iWordColumn+"_end\\.end_id"
                +" = segment_"+iWordColumn+"\\.end_anchor_id"
              + " AND segment_"+iWordColumn+"_end.alignment_status >= "
                + anchorConfidenceThreshold));
          }
        } // there's a target segment layer
      } // aligned only
      
      // is this the last column?
      String sBorderCondition = "";
      if (iWordColumn == matrix.getColumns().size() - 1) {
        // end condition
        if (matrix.getColumns().get(matrix.getColumns().size() - 1).getLayers().values().stream()
            .flatMap(m -> m.stream())
            .filter(layerMatch -> layerMatch.getAnchorEnd()) // end anchored to utterance
            .filter(layerMatch -> schema.getUtteranceLayerId().equals(layerMatch.getId()))
            .findAny().isPresent()) { // end of utterance
          if (sSqlExtraJoins.indexOf(sSqlWordStartJoin) < 0) {
            sSqlExtraJoins.append(sSqlWordStartJoin);
          }
          if (sSqlExtraJoins.indexOf(sSqlLineJoin) < 0) {
            sSqlExtraJoins.append(sSqlLineJoin);
          }
          if (sSqlExtraJoins.indexOf(sSqlLineEndJoin) < 0) {
            sSqlExtraJoins.append(sSqlLineEndJoin);
          }
          if (sSqlExtraJoins.indexOf(sSqlEndLineJoin) < 0) {
            sSqlExtraJoins.append(sSqlEndLineJoin);
          }
          sBorderCondition = sqlEndLineCondition.format(columnSuffix);
        } else if (matrix.getColumns().get(matrix.getColumns().size() - 1).getLayers().values()
                   .stream()
                   .flatMap(m -> m.stream())
                   .filter(layerMatch -> layerMatch.getAnchorEnd()) // end anchored to turn
                   .filter(layerMatch -> schema.getTurnLayerId().equals(layerMatch.getId()))
                   .findAny().isPresent()) { // end of turn
          if (sSqlExtraJoins.indexOf(sSqlEndTurnJoin) < 0) {
            sSqlExtraJoins.append(sSqlEndTurnJoin);
          }
          sBorderCondition = sqlEndTurnCondition.format(columnSuffix);          
        }
      } // last column
      
      if (columnTargetSegmentLayer.isPresent() // it's the target segment layer
          && columnTargetSegmentLayer.get().getTarget()) { 
        sSqlExtraFields.append(
          ", search"+iWordColumn+"_"
          + schema.getLayer(columnTargetSegmentLayer.get().getId()).get("layer_id")
          + ".segment_annotation_id AS target_segment_id");
        targetSegmentExpression
          = "search_"+iWordColumn+"_"
          + (iTargetIndex==0?"":""+iTargetIndex+"_")
          + schema.getLayer(columnTargetSegmentLayer.get().getId()).get("layer_id")
          + ".segment_annotation_id"; 
        targetSegmentOrder =
          ", search_"+iWordColumn+"_"
          + schema.getLayer(columnTargetSegmentLayer.get().getId()).get("layer_id")
          + ".ordinal_in_word";
      } else if (alignedWordLayer.isPresent()
                 && alignedWordLayer.get().getId().equals(targetLayerId)
                 && iTargetColumn == iWordColumn) {
        // if we're targeting a word child layer, we want to add "ordinal" to the ORDER BY
        targetSegmentOrder = ", search_"+iWordColumn+"_"
          + schema.getLayer(alignedWordLayer.get().getId()).get("layer_id")
        + ".ordinal";
      }
      
      Object oSubPatternMatchArgs[] = { 
        sSqlExtraJoins.toString(), // extra JOINs
        sBorderCondition, // border conditions
        sSqlLayerMatches.toString(), // regexp/range subqueries
        sSqlExtraFields.toString(), // for line info
        "_" + iWordColumn, // column suffix
        "_" + (iWordColumn-1) // previous column suffix
      };
      
      strSubsequentSelect.append(sqlPatternMatchSubsequentSelect.format(oSubPatternMatchArgs));
      strSubsequentJoin.append(sSqlExtraJoins.toString());
      if (matrix.getColumns().get(iWordColumn-1).getAdj() == 1) { // adj from previous column
        strSubsequentWhere.append(sqlPatternMatchNextWhere.format(oSubPatternMatchArgs));
      } else {
        strSubsequentWhere.append(
          sqlPatternMatchSubsequentRangeWhere.format(oSubPatternMatchArgs));
      }
      
      setStatus("Adjacency for col " + iWordColumn + " is: " + column.getAdj());
    } // next column
    
    if (mainParticipantOnly) {
      sSqlExtraJoinsFirst.append(sSqlTranscriptSpeakerJoin);
    }
    
    String sTranscriptCondition = transcriptCondition.apply(matrix.getTranscriptQuery());
    if (sTranscriptCondition == null && getLastException() != null) {
      throw (Exception)getLastException();
    }
    if (sTranscriptCondition.length() > 0) {
      if (sSqlExtraJoinsFirst.indexOf(sSqlTranscriptJoin) < 0) {
        sSqlExtraJoinsFirst.append(sSqlTranscriptJoin);
      }
    }
    
    Object oPatternMatchArgs[] = { 
      sSqlExtraJoinsFirst.toString(), // extra JOINs
      sStartConditionFirst, // border conditions
      sSqlLayerMatchesFirst.toString(), // regexp/range subqueries
      sSqlExtraFieldsFirst.toString(), // for line info
      strSpeakerWhere, // speakers
      sTranscriptCondition, // transcripts
      mainParticipantOnly? strMainParticipantClause : "", // main
      strAccessWhere, // user-based access restrictions
      strSubsequentSelect.toString(),
      strSubsequentJoin.toString(),
      strSubsequentWhere.toString(),
      "_" + (matrix.getColumns().size() - 1), // last column suffix
      targetSegmentExpression, // segment_annotation_id expression
      targetExpression, // target_annotation_id expression
      (targetLayer==null? "w"
       :targetLayer.getParentId() == null
       || targetLayer.getParentId().equals(schema.getRoot().getId())? ""
       :targetLayer.get("scope").toString().toLowerCase()),// target scope (lowercase)
      targetLayer==null?0:targetLayer.get("layer_id"), // target layer_id
      maxMatches>0?"LIMIT " + maxMatches:"",
      targetSegmentOrder,
      matrix.getColumns().size()-1 // last column index
    };
    
    // create sql query
    String q = sqlPatternMatchFirst.format(oPatternMatchArgs);
    //setStatus(q);
    // now, a number of expressions are expressed in terms of "word_c" where they need to be
    // "search_c_l", so correct that now...
    for (int c = 0; c < matrix.getColumns().size(); c++) {
      Column column = matrix.getColumns().get(c);
      Optional<LayerMatch> primarySegmentLayer = primarySegmentLayers.get(c);      
      Optional<LayerMatch> primaryWordLayer = primaryWordLayers.get(c);
       if (primarySegmentLayer.isPresent()) {
        Optional<Integer> primarySegmentLayerId = primarySegmentLayer
          .filter(LayerMatch::HasCondition)
          // not a "NOT .+" expression
          .filter(layerMatch -> !layerMatch.getNot() || !".+".equals(layerMatch.getPattern()))
          .filter(layerMatch -> schema.getLayer(layerMatch.getId()) != null)
          // segment scope
          .filter(layerMatch -> "segment".equals(layerMatch.getId())
                  || ("segment".equals(
                        schema.getLayer(layerMatch.getId()).getParentId())))
          .map(layerMatch -> schema.getLayer(layerMatch.getId()))
          .filter(Objects::nonNull)
          .map(layer -> (Integer)layer.get("layer_id"))
          .filter(Objects::nonNull);
        int l = primarySegmentLayerId.orElse(-999);
        if (l >= 0 && primaryWordLayer.isPresent()) {
          // fix join
          q = q.replaceAll(
            "search_"+c+"_"+l
            +"  ON search_"+c+"_"+l+"\\.word_annotation_id = word_"+c+"\\.word_annotation_id",
            "search_"+c+"_"+l
            +"  ON search_"+c+"_"+l+".turn_annotation_id = turn.annotation_id");
        }
        // fix mentions
        q = q.replaceAll(
          " segment_"+c+"\\.",
          " search_"+c+"_"+l+".");
        q = q.replaceAll(
          " segment_"+c+" ",
          " search_"+c+"_"+l+" ");        
      }
      
      if (primaryWordLayer.isPresent() // first primary word layer isn't "word"
          && !primaryWordLayer.get().getId().equals(schema.getWordLayerId())
          // and there's no segment layer in this column
          && !primarySegmentLayer.isPresent()) { 
        // (if there's no word-based matching, leave default join)        
        // fix segment join
        q = q.replaceAll(
          "segment_"+c+" ON segment_"+c+"\\.word_annotation_id = word_"+c+"\\.word_annotation_id",
          "segment_"+c+" ON segment_"+c+".turn_annotation_id = turn.annotation_id");
      }
      Optional<Integer> primaryWordLayerId = primaryWordLayer
        .filter(LayerMatch::HasCondition)
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
        .filter(Objects::nonNull);
      int l = primaryWordLayerId.orElse(-999);
      if (l >= 0) {
        if (!primarySegmentLayer.isPresent()) {
          // fix join
          q = q.replaceAll(
            "search_"+c+"_"+l
            +"  ON search_"+c+"_"+l+"\\.word_annotation_id = word_"+c+"\\.word_annotation_id",
            "search_"+c+"_"+l
            +"  ON search_"+c+"_"+l+".turn_annotation_id = turn.annotation_id");
        }
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

    if (restrictByUser != null) {
      // add restricted user parameters
      parameters.add(restrictByUser);
      parameters.add(restrictByUser);
    }

    // add adjacency parameters
    for (int c = 0; c < matrix.getColumns().size()-1; c++) {
      int adj = matrix.getColumns().get(c).getAdj();
      if (adj != 1) {
        parameters.add(matrix.getColumns().get(c).getAdj());
      }
    } // next adjacency
    
    return q;
  } // end of generateSql()
  
  /**
   * Create an SQL query that identifies results that match the search matrix patterns,
   * optimised for a matrix that only searches the orthography layer,  filling in the
   * given lists with information about parameters to set. 
   * <p>This implementation makes speed gains over {@link #generateSql} by 
   * supporting only the orthography layer, and no border conditions, 
   * main-participant or transcript-type filtering. Allowing only plain, 
   * full-database searches of the orthography layer means that the initial
   * search query can be completed with no SQL JOINs, meaning that searches can 
   * complete in something like one sixth of the time, on very large databases.
   * @param parameters List of parameter values, which must be Double, Integer, or String.
   * @param schema The layer schema.
   * @return An SQL query to implement the query.
   * @throws Exception If the search should be halted for any reason
   * - e.g. the {@link Matrix#participantQuery} identifies no participants.
   */
  public String generateOrthographySql(Vector<Object> parameters, Schema schema)
    throws Exception {
    setDescription(matrix.getDescription().replaceAll("orthography=",""));
    int targetCol = Math.max(0, matrix.getTargetColumn());
    int iWordColumn = 0;
    StringBuffer q = new StringBuffer();
    q.append("INSERT INTO _result");
    q.append(" (search_id, ag_id, speaker_number, start_anchor_id, end_anchor_id,");
    q.append(" defining_annotation_id, segment_annotation_id, target_annotation_id,");
    q.append(" turn_annotation_id, first_matched_word_annotation_id,");
    q.append(" last_matched_word_annotation_id, complete, target_annotation_uid)");
    q.append(" SELECT ?, token_0.ag_id AS ag_id, 0 AS speaker_number,");
    q.append(" token_"+targetCol+".start_anchor_id, token_"+targetCol+".end_anchor_id,");
    q.append(" 0, NULL AS segment_annotation_id,");
    q.append(" token_"+targetCol+".word_annotation_id AS target_annotation_id,");
    q.append(" token_0.turn_annotation_id AS turn_annotation_id,");
    q.append(" token_0.word_annotation_id AS first_matched_word_annotation_id,");
    q.append(" token_"+(matrix.getColumns().size()-1)+".word_annotation_id")
      .append(" AS last_matched_word_annotation_id,");
    q.append(" 0 AS complete,");
    q.append(" CONCAT('ew_2_', token_"+targetCol+".annotation_id) AS target_annotation_uid");
    q.append(" FROM annotation_layer_2 token_0");
    // susequent word joins
    for (int c = 1; c < matrix.getColumns().size(); c++) {
      q.append(" INNER JOIN annotation_layer_2 token_"+c);
      q.append(" ON token_"+c+".turn_annotation_id = token_0.turn_annotation_id");
      q.append(" AND token_"+c+".ordinal_in_turn = token_"+(c-1)+".ordinal_in_turn + 1");
    }
    if (restrictByUser != null) {
      q.append(sSqlTranscriptJoin.replace("turn.ag_id", "token_0.ag_id"));
    } // filtering by role
    int c = 0;
    for (Column column : matrix.getColumns()) {
      // set the WHERE condition for this column
      q.append(c==0?" WHERE ":" AND ");
      // ensure there really is a pattern with a condition
      LayerMatch match = column.getFirstLayerMatch("orthography");
      if (match == null) throw new Exception("No orthography match for column "+c);
      if (match.getPattern() != null) {
        match.ensurePatternAnchored();
        q.append("token_"+c+".label REGEXP ?");
        parameters.add(match.getPattern());
      } else { // numeric
        if (match.getMin() != null) {
          q.append("CAST(token_"+c+".label AS DECIMAL) >= ?");
          parameters.add(Double.valueOf(match.getMin()));
        }
        if (match.getMax() != null) {
          if (match.getMin() != null) q.append(" AND ");
          q.append("CAST(token_"+c+".label AS DECIMAL) < ?");
          parameters.add(Double.valueOf(match.getMax()));
        } else if (match.getMin() == null) { // no condition set at all
          throw new Exception("No orthography condition for column "+c); // TODO i18n
        }
      }
      c++;
    } // next column
    if (restrictByUser != null) {
      q.append(" AND (EXISTS (SELECT * FROM role")
        .append(" INNER JOIN role_permission ON role.role_id = role_permission.role_id")
        .append(" INNER JOIN annotation_transcript access_attribute")
        .append(" ON access_attribute.layer = role_permission.attribute_name")
        .append(" AND access_attribute.label REGEXP role_permission.value_pattern")
        .append(" AND role_permission.entity REGEXP '.*t.*'") // transcript access
        .append(" WHERE user_id = ? AND access_attribute.ag_id = transcript.ag_id)")
        .append(" OR EXISTS (SELECT * FROM role")
        .append(" INNER JOIN role_permission ON role.role_id = role_permission.role_id")
        .append(" AND role_permission.attribute_name = 'corpus'")
        .append(" AND role_permission.entity REGEXP '.*t.*'") // transcript access
        .append(" WHERE transcript.corpus_name REGEXP role_permission.value_pattern")
        .append(" AND user_id = ?))");
    } // filtering by role
    q.append(" ORDER BY token_0.turn_annotation_id, token_0.ordinal_in_turn");
    
    if (restrictByUser != null) {
      // add restricted user parameters
      parameters.add(restrictByUser);
      parameters.add(restrictByUser);
    }
    // (no adjacency parameters)

    return q.toString();
  } // end of generateOrthographySql()

  /**
   * Create an SQL query that identifies results that match the search matrix patterns,
   * optimised for a matrix that only searches one span layer,  filling in the
   * given lists with information about parameters to set. 
   * <p>This implementation makes speed gains over {@link #generateSql} by 
   * supporting only a single span (freeform) layer, identifying the matching spans first,
   * and then identifying the first word in each to provide the match token.
   * @param parameters List of parameter values, which must be Double, Integer, or String.
   * @param schema The layer schema.
   * @param spanLayer The one span layer being searched.
   * @param layerMatch The one span's match condition.
   * @return An SQL query to implement the query.
   * @throws Exception If the search should be halted for any reason
   * - e.g. the {@link Matrix#participantQuery} identifies no participants.
   */
  public String generateOneSpanSql(
    Vector<Object> parameters, Schema schema, Layer spanLayer, LayerMatch layerMatch)
    throws Exception {
    setDescription(matrix.getDescription());
    StringBuilder q = new StringBuilder()
      .append("INSERT INTO _result")
      .append(" (search_id, ag_id, speaker_number, start_anchor_id, end_anchor_id,")
      .append(" defining_annotation_id, segment_annotation_id, target_annotation_id,")
      .append(" turn_annotation_id, first_matched_word_annotation_id,")
      .append(" last_matched_word_annotation_id, complete, target_annotation_uid)")
      .append(" SELECT ?, token.ag_id AS ag_id, 0 AS speaker_number,")
      .append(" token.start_anchor_id, token.end_anchor_id,")
      .append(" NULL AS defining_annotation_id,")
      .append(" NULL AS segment_annotation_id,")
      .append(" token.annotation_id AS target_annotation_id,")
      .append(" NULL AS turn_annotation_id,")
      .append(" NULL AS first_matched_word_annotation_id,")
      .append(" NULL AS last_matched_word_annotation_id,")
      .append(" 0 AS complete,")
      .append(" CONCAT('e_',").append(spanLayer.get("layer_id").toString())
      .append(",'_', token.annotation_id) AS target_annotation_uid")
      .append(" FROM annotation_layer_").append(spanLayer.get("layer_id").toString())
      .append(" token")
      .append(" INNER JOIN anchor start ON token.start_anchor_id = start.anchor_id");
    if (matrix.getTranscriptQuery() != null) {
      q.append(" INNER JOIN transcript ON token.ag_id = transcript.ag_id");
    }
    q.append(" WHERE ");
    // ensure there really is a pattern with a condition
    if (layerMatch.getPattern() != null) {
        layerMatch.ensurePatternAnchored();
        if (layerMatch.getNot()) q.append("NOT ");
        q.append("token.label REGEXP ?");
        parameters.add(layerMatch.getPattern());
    } else { // numeric
      if (layerMatch.getMin() != null) {
        q.append("CAST(token.label AS DECIMAL) >= ?");
        parameters.add(layerMatch.getMin());
      }
      if (layerMatch.getMax() != null) {
        if (layerMatch.getMin() != null) q.append(" AND ");
        q.append("CAST(token.label AS DECIMAL) < ?");
        parameters.add(layerMatch.getMax());
      } else if (layerMatch.getMin() == null) { // no condition set at all
        throw new Exception(
          "No span condition specified on layer " + layerMatch.getId()); // TODO i18n
      }
    }
    
    if (matrix.getTranscriptQuery() != null) {
      GraphAgqlToSql transformer = new GraphAgqlToSql(getStore().getSchema());
      GraphAgqlToSql.Query query = transformer.sqlFor(
        matrix.getTranscriptQuery(), "transcript.transcript_id", null, null, null);
      q.append(query.sql
               .replaceFirst("^SELECT transcript.transcript_id FROM transcript WHERE ", " AND ")
               .replaceFirst(" ORDER BY transcript.transcript_id$",""));
    }
    if (getRestrictByUser() != null) {
      q.append(" AND (EXISTS (SELECT * FROM role")
        .append(" INNER JOIN role_permission ON role.role_id = role_permission.role_id")
        .append(" INNER JOIN annotation_transcript access_attribute")
        .append(" ON access_attribute.layer = role_permission.attribute_name")
        .append(" AND access_attribute.label REGEXP role_permission.value_pattern")
        .append(" AND role_permission.entity REGEXP '.*t.*'") // transcript access
        .append(" WHERE user_id = ? AND access_attribute.ag_id = token.ag_id)");
      parameters.add(getRestrictByUser());
      q.append(" OR EXISTS (SELECT * FROM role")
        .append(" INNER JOIN role_permission ON role.role_id = role_permission.role_id")
        .append(" AND role_permission.attribute_name = 'corpus'")
        .append(" AND role_permission.entity REGEXP '.*t.*'") // transcript access
        .append(" INNER JOIN transcript")
        .append(" ON transcript.corpus_name REGEXP role_permission.value_pattern")
        .append(" WHERE transcript.ag_id = token.ag_id")
        .append(" AND user_id = ?))");
      parameters.add(getRestrictByUser());
    } // filtering by role
    q.append(" ORDER BY token.ag_id, start.offset");
    return q.toString();
  }
  
  /**
   * Completes a layered search by first identifying the first 'column' of matches
   * (i.e. words that match the first pattern) then matching the results against
   * subsequent 'columns' until what remains are matches for the entire matrix. 
   * @throws Exception
   */
  protected void search() throws Exception {

    iPercentComplete = 1;
    Connection connection = getStore().getConnection();
    final Schema schema = getStore().getSchema();
    if (matrix != null) setName(matrix.getDescription());
    setDescription(matrix.getDescription());

    // word columns
	 
    // list of Word objects that match matrix
    results = new SqlSearchResults(this);
	 
    // the participant condition is a list of turn labels, which are speaker_numbers
    UnaryOperator<String> participantCondition = participantQuery -> {
      if (participantQuery != null && participantQuery.trim().length() > 0) {
        setStatus("Identifying participants...");
        try {
          String[] participantIds = getStore().getMatchingParticipantIds(participantQuery);
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
          if (getStore().countMatchingTranscriptIds(transcriptQuery) == 0) {
            // bail out by returning null and setting last exception
            setLastException(
              new Exception("Transcript query matches no transcripts: " + transcriptQuery));
            return null;
          }
          GraphAgqlToSql transformer = new GraphAgqlToSql(getStore().getSchema());
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

    // we can generate optimized SQL for orthography-only searches
    boolean orthographyOnly = !mainParticipantOnly
      && anchorConfidenceThreshold == null // not only aligned
      && matrix.getParticipantQuery() == null
      && matrix.getTranscriptQuery() == null
      && !matrix.layerMatchStream().filter(LayerMatch::HasCondition) // no non-orthography layers
      .filter(match -> !match.getId().equals("orthography"))
      .findAny().isPresent()
      && !matrix.getColumns().stream() // no adj > 1
      .filter(column -> column.getAdj() > 1)
      .findAny().isPresent()
      && !matrix.layerMatchStream() // no negations
      .filter(LayerMatch::HasCondition).filter(match -> match.getNot())
      .findAny().isPresent()
      && !matrix.layerMatchStream() // no anchoring
      .filter(match -> match.getAnchorStart() || match.getAnchorEnd())
      .findAny().isPresent();
    
    // we can generate optimized SQL one-span-only searches
    boolean noNonSpanLayers = !matrix.getColumns().stream() // no adj > 1
      .filter(column -> column.getAdj() > 1)
      .findAny().isPresent()
      && !matrix.layerMatchStream().filter(LayerMatch::HasCondition)
      .map(layerMatch -> schema.getLayer(layerMatch.getId()))
      .filter(Objects::nonNull)
      .filter(layer -> layer.getParentId() != null) // no non-top-level layers
      .filter(layer -> !layer.getParentId().equals(schema.getRoot().getId())
              // nor top level layers that are not aligned (i.e. transcript attributes)
              || layer.getAlignment() == Constants.ALIGNMENT_NONE)
      .findAny().isPresent();
    List<Layer> spanLayers = matrix.layerMatchStream().filter(LayerMatch::HasCondition)
      .map(layerMatch -> schema.getLayer(layerMatch.getId()))
      .filter(Objects::nonNull)
      .filter(layer -> layer.getAlignment() != Constants.ALIGNMENT_NONE // aligned
              && (layer.getParentId() == null // top-level layers
                  || layer.getParentId().equals(schema.getRoot().getId())))
      .collect(Collectors.toList());
    LayerMatch spanLayerMatch = spanLayers.size() != 1?null
      :matrix.layerMatchStream()
      .filter(LayerMatch::HasCondition)
      .filter(match -> match.getId().equals(spanLayers.get(0).getId()))
      .findAny().get();

    // generate an SQL statement from the search matrix
    String q = null;
    // Parameter values
    Vector<Object> parameters = new Vector<Object>();
    try {
      setStatus("Creating query...");
      if (orthographyOnly) {
        setStatus("Optimising for orthography-only search...");
        q = generateOrthographySql(parameters, schema);
      } else if (noNonSpanLayers && spanLayers.size() == 1
                 && spanLayerMatch.getTarget()) {
        setStatus("Optimising for one-span-only search...");
        q = generateOneSpanSql(parameters, schema, spanLayers.get(0), spanLayerMatch);
      } else {
        q = generateSql(
          parameters, schema,
          layerIsSpanningAndWordAnchored, participantCondition, transcriptCondition);
      }
      setStatus("SQL: " + q);
    } catch (Exception x) {
      setStatus(x.getMessage());
      if (!x.getMessage().equals("Cancelled.")) {
        setLastException(x);
      }
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
    try {
      if (!bCancelling) executeUpdate(sqlPatternMatch);
    } catch (Exception x) {
      if (bCancelling) {
        setStatus("Cancelled.");
      } else {
        throw x;
      }
    } finally {
      sqlPatternMatch.close();
    }
    
    // setStatus(q);
    sqlPatternMatch = connection.prepareStatement(q);
    
    // set the layer search parameters
    int iLayerParameter = 1;     
    sqlPatternMatch.setLong(iLayerParameter++, ((SqlSearchResults)results).getId());      

    // set parameter values
    for (Object parameter : parameters) {
      if (parameter instanceof Double) {
        sqlPatternMatch.setDouble(
          iLayerParameter++, (Double)parameter);
      } else if (parameter instanceof Integer) {
        sqlPatternMatch.setInt(
          iLayerParameter++, (Integer)parameter);
      } else {
        sqlPatternMatch.setString(iLayerParameter++, parameter.toString());
      }
    }
    
    iPercentComplete = SQL_STARTED_PERCENT; 
    setStatus("Querying...");
    try {
      if (!bCancelling) executeUpdate(sqlPatternMatch);
    } catch (Exception x) {
      if (bCancelling) {
        setStatus("Cancelled.");
      } else {
        throw x;
      }
    } finally {
      sqlPatternMatch.close();
    }
    iPercentComplete = SQL_FINISHED_PERCENT;

    // if it's a single-span search, we need to fill in the token IDs
    if (noNonSpanLayers && spanLayers.size() == 1
        && spanLayerMatch.getTarget()) {
      setTokenIds(connection, spanLayers.get(0), spanLayerMatch, participantCondition);
    }
    
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
    try {
      if (!bCancelling) executeUpdate(sqlPatternMatch);
    } catch (Exception x) {
      if (bCancelling) {
        setStatus("Cancelled.");
      } else {
        throw x;
      }
    } finally {
      sqlPatternMatch.close();
    }
    sqlPatternMatch = connection.prepareStatement(
      "INSERT INTO _result_copy SELECT * FROM _result");
    try {
      if (!bCancelling) executeUpdate(sqlPatternMatch);
    } catch (Exception x) {
      if (bCancelling) {
        setStatus("Cancelled.");
      } else {
        throw x;
      }
    } finally {
      sqlPatternMatch.close();
    }

    sqlPatternMatch = connection.prepareStatement(
      "DELETE dup.*"
      +" FROM _result dup"
      +" INNER JOIN _result_copy orig"
      +" ON dup.search_id = orig.search_id AND dup.target_annotation_uid = orig.target_annotation_uid"
      +" AND dup.match_id > orig.match_id"
      +" WHERE dup.search_id = ?");
    sqlPatternMatch.setLong(1, ((SqlSearchResults)results).getId());      
    try {
      if (!bCancelling) executeUpdate(sqlPatternMatch);
    } catch (Exception x) {
      if (bCancelling) {
        setStatus("Cancelled.");
      } else {
        throw x;
      }
    } finally {
      sqlPatternMatch.close();
    }

    sqlPatternMatch = connection.prepareStatement(
      "DROP TEMPORARY TABLE _result_copy");
    try {
      sqlPatternMatch.executeUpdate();
    } catch (SQLException x) {
      // maybe we're cancelling before it was created?
    } finally {
      sqlPatternMatch.close();
    }

    iPercentComplete = 92;
    
    // set defining annotation and its anchors, and sort the results by speaker and transcript...
    
    // copy the incomplete results back into the table, sorting as we go      
    sqlPatternMatch = connection.prepareStatement(
      "INSERT INTO result"
      +" (search_id,ag_id,speaker_number,"
      +" start_anchor_id,end_anchor_id,defining_annotation_id,"
      +" segment_annotation_id,target_annotation_id,first_matched_word_annotation_id,"
      +" last_matched_word_annotation_id,complete,target_annotation_uid)"
      +" SELECT unsorted.search_id,unsorted.ag_id,"
      // get speaker_number from line, optimised orth SQL doesn't include it
      +" CAST(line.label AS SIGNED)," 
      +" line.start_anchor_id,line.end_anchor_id,line.annotation_id,"
      +" unsorted.segment_annotation_id,unsorted.target_annotation_id,"
      +" unsorted.first_matched_word_annotation_id,unsorted.last_matched_word_annotation_id,"
      +" 1,unsorted.target_annotation_uid"
      +" FROM _result unsorted"
      +" INNER JOIN transcript ON unsorted.ag_id = transcript.ag_id"
      +" INNER JOIN annotation_layer_0 word"
      +" ON word.word_annotation_id = unsorted.first_matched_word_annotation_id"
      // line bounds are outside word bounds...
      +" INNER JOIN annotation_layer_"+SqlConstants.LAYER_UTTERANCE + " line" 
      +" ON line.annotation_id = word.utterance_annotation_id"
      // get speaker_number from line, optimised orth SQL doesn't include it
      +" INNER JOIN speaker ON"
      +" line.label REGEXP '^[0-9]+$' AND CAST(line.label AS SIGNED) = speaker.speaker_number"
      +" WHERE unsorted.search_id = ? AND complete = 0"
      +" ORDER BY speaker.name, transcript.transcript_id, unsorted.match_id");
    sqlPatternMatch.setLong(1, ((SqlSearchResults)results).getId());      
    try {
      if (!bCancelling) executeUpdate(sqlPatternMatch);
    } catch (Exception x) {
      if (bCancelling) {
        setStatus("Cancelled.");
      } else {
        throw x;
      }
    } finally {
      sqlPatternMatch.close();
    }

    // delete the unsorted results
    sqlPatternMatch = connection.prepareStatement(
      "DROP TEMPORARY TABLE _result");
    try {
      sqlPatternMatch.executeUpdate();
    } catch (Exception x) {
      if (bCancelling) {
        setStatus("Cancelled.");
      } else {
        throw x;
      }
    } finally {
      sqlPatternMatch.close();
    }
    
    iPercentComplete = 95;
    
    results.reset();
    
    // exclude simultaneous speech, etc.
    filterResults(); 

    results.reset();
    // force it to recheck the database to get size etc.
    results.hasNext();
    
    iPercentComplete = 100;
    
  }
  
  /**
   * Fills in the token IDs for the result set, assuming they're missing because the
   * search query identified the target annotation ID but not the word tokens IDs.
   * <p> This will try to ensure that the token is <i>contained entirely by</i> the
   * duration of the target annotation, but if that's not possible (e.g. because it's
   * a noise annotation strung between two words) then linked words, or the nearest word 
   * to the start of the target will be assigned instead.
   * <p> This ensures that single-span-only searches (e.g. for noises) match with words 
   * whether they're strictly contained within the span or not, so that instantaneous 
   * noises, noises chained between words, and noises overlap with but don't contain 
   * words are returned by searches.
   * @param spanLayer The one span layer being searched.
   * @param layerMatch The one span layer's match conditions.
   * @throws SQLException
   */
  protected void setTokenIds(
    Connection connection, Layer spanLayer, LayerMatch layerMatch,
    UnaryOperator<String> participantCondition) throws Exception {

    // try to find a suitable first word token:
    // 1. try for a token that is temporally contained by the target
    //  (keeping containment where possible, to match word-based searches), and if not:
    // 2. try for the nearest linked word (e.g. noise strung between two words)
    //  (if they're linked to a word, they presumably relate to the word speaker), and if not:
    // 3. try for the nearest word to the target start (e.g. instantanous/short noise)
    //  (to maximise possible tokens returned)
    StringBuilder wordLine = new StringBuilder()
      .append("SELECT")
      .append(" line.label, line.start_anchor_id, line.end_anchor_id,")
      .append(" line.annotation_id AS defining_annotation_id, word.turn_annotation_id,")
      .append(" word.annotation_id AS first_matched_word_annotation_id,")
      .append(" word.annotation_id AS last_matched_word_annotation_id")
      .append(" FROM annotation_layer_0 word")
      .append(" INNER JOIN anchor word_start ON word.start_anchor_id = word_start.anchor_id")
      .append(" INNER JOIN annotation_layer_12 line")
      .append(" ON word.turn_annotation_id = line.turn_annotation_id")
      .append(" INNER JOIN anchor line_start ON line.start_anchor_id = line_start.anchor_id")
      .append(" INNER JOIN anchor line_end ON line.end_anchor_id = line_end.anchor_id");
    if(mainParticipantOnly) {
      wordLine.append(" INNER JOIN transcript_speaker ON transcript_speaker.ag_id = word.ag_id")
        .append(" AND transcript_speaker.speaker_number = line.label AND main_speaker = 1");
    }
    wordLine.append(" INNER JOIN anchor span_start ON span_start.anchor_id = ?")
      .append(" INNER JOIN anchor span_end ON span_end.anchor_id = ?")
      .append(" AND word.utterance_annotation_id = line.annotation_id")
      .append(" WHERE word.ag_id = ?");
    String speakerWhere = participantCondition.apply(matrix.getParticipantQuery());
    if (speakerWhere == null && getLastException() != null) {
      throw (Exception)getLastException();
    }
    wordLine.append(speakerWhere.replace("turn.label","line.label"));
    if (layerMatch.getAnchorStart()) {
      wordLine.append(" AND word.start_anchor_id = span_start.anchor_id");
    }
    if (layerMatch.getAnchorEnd()) {
      wordLine.append(" AND word.end_anchor_id = span_end.anchor_id");
    }

    // up to here, all of the three possibilities use the same query
    // but the WHERE/ORDER BY conditions vary for each...

    StringBuilder containedWord = new StringBuilder(wordLine);
    StringBuilder linkedWord = new StringBuilder(wordLine);
    StringBuilder nearestWord = new StringBuilder(wordLine);
    
    if (!layerMatch.getAnchorStart() && !layerMatch.getAnchorEnd()) {
      containedWord.append(" AND word_start.offset >= span_start.offset")
        .append(" AND word_start.offset < span_end.offset");
      linkedWord.append(" AND (word.start_anchor_id = span_start.anchor_id")
        .append(" OR word.end_anchor_id = span_start.anchor_id")
        .append(" OR word.start_anchor_id = span_end.anchor_id")
        .append(" OR word.end_anchor_id = span_end.anchor_id)");
    }
    // first word by offset:
    containedWord.append(" ORDER BY word_start.offset LIMIT 1");
    // word nearest to the start of the span:
    linkedWord.append(" ORDER BY ABS(span_start.offset - word_start.offset) LIMIT 1");
    nearestWord.append(" ORDER BY ABS(span_start.offset - word_start.offset) LIMIT 1");
    PreparedStatement sqlContainedWordLine = connection.prepareStatement(containedWord.toString());
    PreparedStatement sqlLinkedWordLine = connection.prepareStatement(linkedWord.toString());
    PreparedStatement sqlNearestWordLine = connection.prepareStatement(nearestWord.toString());
    
    PreparedStatement sqlResults = connection.prepareStatement("SELECT * FROM _result");
    PreparedStatement sqlUpdateResult = connection.prepareStatement(
      "UPDATE _result SET speaker_number = ?, start_anchor_id = ?, end_anchor_id = ?,"
      +" defining_annotation_id = ?, turn_annotation_id = ?,"
      +" first_matched_word_annotation_id = ?, last_matched_word_annotation_id = ?"
      +" WHERE search_id = ? AND match_id = ?");
    PreparedStatement sqlDeleteResult = connection.prepareStatement(
      "DELETE FROM _result WHERE search_id = ? AND match_id = ?");
    ResultSet rsResults = sqlResults.executeQuery();
    try {
      while (rsResults.next() && !bCancelling) {
        sqlContainedWordLine.setLong(1, rsResults.getLong("start_anchor_id"));
        sqlContainedWordLine.setLong(2, rsResults.getLong("end_anchor_id"));
        sqlContainedWordLine.setInt(3, rsResults.getInt("ag_id"));
        ResultSet rsWordLine = sqlContainedWordLine.executeQuery();
        boolean found = rsWordLine.next();
        if (!found) { // fall back to linked word
          rsWordLine.close();
          sqlLinkedWordLine.setLong(1, rsResults.getLong("start_anchor_id"));
          sqlLinkedWordLine.setLong(2, rsResults.getLong("end_anchor_id"));
          sqlLinkedWordLine.setInt(3, rsResults.getInt("ag_id"));
          rsWordLine = sqlLinkedWordLine.executeQuery();
          found = rsWordLine.next();
          if (!found                          // fall back to nearest word
              && !layerMatch.getAnchorStart() // only if not anchoring to start or end
              && !layerMatch.getAnchorEnd()) { 
            rsWordLine.close();
            sqlNearestWordLine.setLong(1, rsResults.getLong("start_anchor_id"));
            sqlNearestWordLine.setLong(2, rsResults.getLong("end_anchor_id"));
            sqlNearestWordLine.setInt(3, rsResults.getInt("ag_id"));
            rsWordLine = sqlNearestWordLine.executeQuery();
            found = rsWordLine.next();
          }
        }
        if (found) { // only the first one
          sqlUpdateResult.setString(1, rsWordLine.getString("label")); // speaker_number
          sqlUpdateResult.setLong(2, rsWordLine.getLong("start_anchor_id"));
          sqlUpdateResult.setLong(3, rsWordLine.getLong("end_anchor_id"));
          sqlUpdateResult.setLong(4, rsWordLine.getLong("defining_annotation_id"));
          sqlUpdateResult.setLong(5, rsWordLine.getLong("turn_annotation_id"));
          sqlUpdateResult.setLong(
            6, rsWordLine.getLong("first_matched_word_annotation_id"));
          sqlUpdateResult.setLong(
            7, rsWordLine.getLong("last_matched_word_annotation_id"));
          sqlUpdateResult.setInt(8, rsResults.getInt("search_id"));
          sqlUpdateResult.setInt(9, rsResults.getInt("match_id"));
          sqlUpdateResult.executeUpdate();
        } else { // no matching token, so invalid result
          sqlDeleteResult.setInt(1, rsResults.getInt("search_id"));
          sqlDeleteResult.setInt(2, rsResults.getInt("match_id"));
          sqlDeleteResult.executeUpdate();
        }
        rsWordLine.close();
        
      } // next result
    } finally {
      rsResults.close();
      sqlResults.close();
      sqlContainedWordLine.close();
      sqlLinkedWordLine.close();
      sqlNearestWordLine.close();
      sqlUpdateResult.close();
      sqlDeleteResult.close();
    }
  } // end of setTokenIds()

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
            Graph transcript = getStore().getTranscript(""+match.getGraphId());
            graphIds.put(match.getGraphId(), transcript.getId());
          }
          String[] utteranceAnchorIds = {
            "n_" + match.getStartAnchorId(), "n_" + match.getEndAnchorId()
          };
          Anchor[] utteranceAnchors = getStore().getAnchors(
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
          Annotation[] overlappingUtterances = getStore().getMatchingAnnotations(query);
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
          Anchor[] anchors = getStore().getAnchors(
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
   *  <li>17: segment order clause, if any</li>
   *  <li>18: index of last word column</li>
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
    +", word_0.start_anchor_id, word_{18}.end_anchor_id,0" // start_anchor_id, end_anchor_id, defining_annotation_id, updated later
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
  static final MessageFormat sqlPatternMatchSubsequentRangeWhere = new MessageFormat(
    " /* column {4}: */ "
    +" AND word{4}.ordinal_in_turn"
    +" BETWEEN word{5}.ordinal_in_turn + 1 AND word{5}.ordinal_in_turn + ?"
    + "{1} {2}");
  
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
  static final MessageFormat sqlPatternMatchNextWhere = new MessageFormat(
    " /* column {4}: */ "
    +" AND word{4}.ordinal_in_turn = word{5}.ordinal_in_turn + 1"
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
    " AND ( word{1}.end_anchor_id = search{1}_{0}.end_anchor_id"
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
    // TODO use word.utterance_annotation_id instead of anchor offsets
    // TODO + " ON line{0}.annotation_id = word{0}.utterance_annotation_id"
    + "  INNER JOIN anchor line{0}_start"
    + "  ON line{0}_start.anchor_id = line{0}.start_anchor_id"
    + "  INNER JOIN anchor line{0}_end"
    + "  ON line{0}_end.anchor_id = line{0}.end_anchor_id)"
    + " ON line{0}.turn_annotation_id = word{0}.turn_annotation_id"
    // line bounds are outside word bounds...
    + "  AND line{0}_start.offset <= word{0}_start.offset"
    + "  AND line{0}_end.offset > word{0}_start.offset");
  
  /** Join to utterance directly from word table */
  static final MessageFormat sqlDirectLineJoin = new MessageFormat(
    " INNER JOIN annotation_layer_"+SqlConstants.LAYER_UTTERANCE + " line{0}"
    +" ON line{0}.annotation_id = word{0}.utterance_annotation_id");
  
  /** Join to utterance directly from via system word table table */
  static final MessageFormat sqlLineJoinViaToken = new MessageFormat(
    " INNER JOIN annotation_layer_"+SqlConstants.LAYER_TRANSCRIPTION + " token{0}"
    +" ON token{0}.annotation_id = word{0}.word_annotation_id"
    +" INNER JOIN annotation_layer_"+SqlConstants.LAYER_UTTERANCE + " line{0}"
    +" ON line{0}.annotation_id = token{0}.utterance_annotation_id");

  /** Join to utterance start */
  static final MessageFormat sqlLineStartJoin = new MessageFormat(
    " INNER JOIN anchor line{0}_start"
    + " ON line{0}_start.anchor_id = line{0}.start_anchor_id");
  
  /** Join to utterance end */
  static final MessageFormat sqlLineEndJoin = new MessageFormat(
    " INNER JOIN anchor line{0}_end"
    + " ON line{0}_end.anchor_id = line{0}.end_anchor_id");
  
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
   * JOIN to get the start offset of the segment. The end offset isn't usually needed,
   * as the start time is enough to deduce whether a segment is contained by
   * some other annotation.
   * <p> Arguments are:
   * <ul>
   *  <li>0: column suffix </li>
   * </ul>
   */
  static final MessageFormat sqlSegmentStartJoin = new MessageFormat(
    " INNER JOIN anchor segment{0}_start"
    +" ON segment{0}_start.anchor_id = segment{0}.start_anchor_id");
   
  /** 
   * JOIN to get the end offset of the segment. Assumes that {@link #sqlSegmentStartJoin}
   * has already been included.
   */
  static final MessageFormat sqlSegmentEndJoin = new MessageFormat(
    " INNER JOIN anchor segment{0}_end"
    +" ON segment{0}_end.anchor_id = segment{0}.end_anchor_id");
   
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
    + "  ON search_{0}.ag_id = word.ag_id" // TODO word... is not a thing
    // same turn...
    + "  AND search_{0}.turn_annotation_id = word.turn_annotation_id" // TODO word... is not a thing
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
    + "  ON search_{0}.ag_id = word.ag_id" // TODO word... is not a thing
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
  static final MessageFormat PHRASE_INSTANTS_REGEXP_JOIN  = new MessageFormat(
    " INNER JOIN (annotation_layer_{0} search{6}_{0}" 
    + "  INNER JOIN anchor meta{6}_start_{0}"
    + "  ON meta{6}_start_{0}.anchor_id = search{6}_{0}.start_anchor_id)"
    + "  ON search_{0}.ag_id = word{6}.ag_id"
    // same turn...
    + "  AND search{6}_{0}.turn_annotation_id = word{6}.turn_annotation_id"
    // meta bounds enclose word end time...
    + "  AND meta_start_{0}.offset >= word{6}_start.offset"
    + "  AND meta_start_{0}.offset < word{6}_end.offset"
    + "  AND {2,choice,0#search{6}_{0}.label|1#CAST(search{6}_{0}.label AS BINARY)}"
    + " {1} REGEXP {2,choice,0#|1#BINARY} ? {4}");
    
  /**
   * Query for matching a trailing freeform-instants-layer label
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
  static final MessageFormat SPAN_INSTANTS_REGEXP_JOIN  = new MessageFormat(
    " INNER JOIN (annotation_layer_{0} search_{0}" 
    + "  INNER JOIN anchor meta_start_{0}"
    + "  ON meta_start_{0}.anchor_id = search_{0}.start_anchor_id)"
    + "  ON search_{0}.ag_id = word{6}.ag_id"
    // word bounds enclose annotation start time...
    + "  AND meta_start_{0}.offset >= word{6}_start.offset"
    + "  AND meta_start_{0}.offset < word{6}_end.offset"
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
    + "  ON search{6}_{0}.segment_annotation_id = segment{6}.segment_annotation_id"
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
    " INNER JOIN annotation_layer_{0} search{6}_{0}"
    + "  ON search{6}_{0}.word_annotation_id = segment{6}.word_annotation_id"
    + "  AND {2,choice,0#search{6}_{0}.label|1#CAST(search{6}_{0}.label AS BINARY)}"
    + " {1} REGEXP {2,choice,0#|1#BINARY} ? {4}"
    + " INNER JOIN anchor word{6}_start_{0}"
    + "  ON word{6}_start_{0}.anchor_id = search{6}_{0}.start_anchor_id"
    + "  AND word{6}_start_{0}.offset <= segment{6}_start.offset"
    + " INNER JOIN anchor word{6}_end_{0}"
    + "  ON word{6}_end_{0}.anchor_id = search{6}_{0}.end_anchor_id"
    + "  AND word{6}_end_{0}.offset > segment{6}_start.offset");

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
