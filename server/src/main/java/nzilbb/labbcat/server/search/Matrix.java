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
package nzilbb.labbcat.server.search;

import java.io.StringReader;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import nzilbb.util.CloneableBean;
import nzilbb.util.ClonedProperty;

/**
 * Complete search matrix.
 */
public class Matrix implements CloneableBean {
  
  /**
   * Query to identify participants whose utterances should be searched.
   * <p> e.g. "first('participant_gender').label == 'NB'"
   * @see #getParticipantQuery()
   * @see #setParticipantQuery(String)
   */
  protected String participantQuery;
  /**
   * Getter for {@link #participantQuery}: Query to identify participants whose utterances
   * should be searched. 
   * @return Query to identify participants whose utterances should be searched.
   */
  @ClonedProperty
  public String getParticipantQuery() { return participantQuery; }
  /**
   * Setter for {@link #participantQuery}: Query to identify participants whose utterances
   * should be searched.  
   * @param newParticipantQuery Query to identify participants whose utterances should be
   * searched. An empty string results in null being assigned.
   */
  public Matrix setParticipantQuery(String newParticipantQuery) {
    participantQuery = newParticipantQuery != null && newParticipantQuery.length() == 0? null
      : newParticipantQuery;
    return this;
  }
  
  /**
   * Query to identify transcripts whose utterances should be searched.
   * <p> e.g. "['CC','IA'].includesAny(labels('corpus'))"
   * @see #getTranscriptQuery()
   * @see #setTranscriptQuery(String)
   */
  protected String transcriptQuery;
  /**
   * Getter for {@link #transcriptQuery}: Query to identify transcripts whose utterances
   * should be searched. 
   * @return Query to identify transcripts whose utterances should be searched.
   */
  @ClonedProperty
  public String getTranscriptQuery() { return transcriptQuery; }
  /**
   * Setter for {@link #transcriptQuery}: Query to identify transcripts whose utterances
   * should be searched. 
   * @param newTranscriptQuery Query to identify transcripts whose utterances should be
   * searched. An empty string results in null being assigned.
   */
  public Matrix setTranscriptQuery(String newTranscriptQuery) {
    transcriptQuery = newTranscriptQuery != null && newTranscriptQuery.length() == 0? null
      : newTranscriptQuery;
    return this;
  }

  /**
   * The columns of the search matrix, each representing patterns matching one word token.
   * @see #getColumns()
   * @see #setColumns(List)
   */
  protected Vector<Column> columns = new Vector<Column>();
  /**
   * Getter for {@link #columns}: The columns of the search matrix, each representing
   * patterns matching one word token. 
   * @return The columns of the search matrix, each representing patterns matching one word token.
   */
  @ClonedProperty
  public Vector<Column> getColumns() { return columns; }
  /**
   * Setter for {@link #columns}: The columns of the search matrix, each representing
   * patterns matching one word token. 
   * @param newColumns The columns of the search matrix, each representing patterns
   * matching one word token. 
   */
  public Matrix setColumns(Vector<Column> newColumns) { columns = newColumns; return this; }
  
  /**
   * Convenience builder-pattern method for adding a column.
   * @param column
   * @return This matrix.
   */
  public Matrix addColumn(Column column) {
    columns.add(column);
    return this;
  } // end of addColumn()

  /**
   * Initializes the bean with the given JSON representation.
   * @param json
   * @return A reference to this object.
   */
  public CloneableBean fromJson(JsonObject json) {
    if (json.containsKey("columns")) {
      JsonArray jsonColumns = json.getJsonArray("columns");
      for (int c = 0; c < jsonColumns.size(); c++) {
        JsonObject jsonColumn = jsonColumns.getJsonObject(c);
        addColumn((Column)(new Column().fromJson(jsonColumn)));
      } // next element
    }
    if (json.containsKey("participantQuery")) {
      participantQuery = json.getString("participantQuery");
    }
    if (json.containsKey("transcriptQuery")) {
      transcriptQuery = json.getString("transcriptQuery");
    }
    return this;
  }
  
  /**
   * Deserialize a search matrix from a JSON-encoded string.
   * @param jsonString
   * @return This matrix.
   */
  public Matrix fromJsonString(String jsonString) {
    return (Matrix)fromJson(
      Json.createReader(new StringReader(jsonString)).readObject());
  } // end of fromJsonString()
  
  /**
   * A stream of LayerMatchs defined in the matrix.
   * @return A stream of LayerMatchs defined in the matrix.
   */
  public Stream<LayerMatch> layerMatchStream() {
    return columns.stream()
      .map(column -> column.getLayers().values().stream())
      .reduce(Stream.empty(), Stream::concat);
  } // end of layerPatternStream()
  
  /**
   * Returns the layer ID of the (first) LayerMatch where {@link LayerMatch#target} == true.
   * @return The layer ID of the (first) LayerMatch where {@link LayerMatch#target} ==
   * true, or null if there is no such LayerMatch.
   */
  public String getTargetLayerId() {
    return layerMatchStream()
      .filter(l -> l.getTarget())
      .map(l -> l.getId())
      .findAny()
      .orElse(null);
  } // end of getTargetLayerId()

  /**
   * Determines which column the (first) LayerMatch where
   * {@link LayerPatter#target} == true is located in. 
   * @return The index of the column the (first) LayerMatch where
   * {@link LayerPatter#target} == true is located in, or -1 if there is no such LayerMatch.
   */
  public int getTargetColumn() {
    for (int c = 0; c < columns.size(); c++) {
      if (columns.get(c).getLayers().values().stream()
          .filter(l -> l.getTarget())
          .findAny().isPresent()) {
        return c;
      }
    } // next column
    return -1;
  } // end of getTargetColumn()
  
  /**
   * A curt semi-human-readable summary of the matrix that can be used for
   * probably-unique, more or less descriptive file names, etc. 
   * @return A string describing the layer matches.
   */
  public String getDescription() {
    return layerMatchStream()
      .filter(LayerMatch::HasPattern)
      .map(layerMatch -> {
          StringBuilder description = new StringBuilder(layerMatch.getId());
          if (layerMatch.getPattern() != null) {
            if (layerMatch.getNot() != null && layerMatch.getNot()) {
              description.append("≉");
            } else {
              description.append("≈");
            }
            description.append(layerMatch.getPattern());
          }
          if (layerMatch.getMin() != null) {
            description.append("≥").append(layerMatch.getMin());
          }
          if (layerMatch.getMax() != null) {
            description.append("<").append(layerMatch.getMax());
          }
          return description.toString();
        })
      .collect(Collectors.joining(" "));
  } // end of getDescription()

  /**
   * Returns the JSON serialization of this search matrix.
   * @return The JSON serialization of this search matrix.
   */
  @Override public String toString() {
    return toJson().toString();
  } // end of toString()

}
