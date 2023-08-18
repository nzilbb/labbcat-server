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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import nzilbb.util.CloneableBean;
import nzilbb.util.ClonedProperty;

/**
 * One column in a search matrix, containing patterns matching one word token.
 */
public class Column implements CloneableBean {
  
  /**
   * The layer patterns that match a word token. Keys are layer IDs.
   * @see #getLayers()
   * @see #setLayers(Map)
   */
  protected Map<String,List<LayerMatch>> layers = new LinkedHashMap<String,List<LayerMatch>>();
  /**
   * Getter for {@link #layers}: The layer patterns that match a word token. Keys are layer IDs.
   * @return The layer patterns that match a word token.
   */
  @ClonedProperty
  public Map<String,List<LayerMatch>> getLayers() { return layers; }
  /**
   * Setter for {@link #layers}: The layer patterns that match a word token. Keys are layer IDs.
   * @param newLayers The layer patterns that match a word token.
   */
  public Column setLayers(Map<String,List<LayerMatch>> newLayers) { layers = newLayers; return this; }
  
  /**
   * Adjecency; how far matches of the following column in the matrix can be from matches
   * of this column. 1 means it must immediately follow, 2 means there can be one
   * intervening token, etc.
   * @see #getAdj()
   * @see #setAdj(int)
   */
  protected int adj = 1;
  /**
   * Getter for {@link #adj}: Adjecency; how far matches of the following column in the
   * matrix can be from matches of this column. 1 means it must immediately follow, 2
   * means there can be one intervening token, etc.
   * @return Adjecency; how far matches of the following column in the matrix can be from
   * matches of this column. 
   */
  @ClonedProperty
  public int getAdj() { return adj; }
  /**
   * Setter for {@link #adj}: Adjecency; how far matches of the following column in the
   * matrix can be from matches of this column. 1 means it must immediately follow, 2
   * means there can be one intervening token, etc.
   * @param newAdj Adjecency; how far matches of the following column in the matrix can be
   * from matches of this column. 
   */
  public Column setAdj(int newAdj) { adj = newAdj; return this; }
  
  /**
   * Convenience builder-pattern method for adding a layer pattern.
   * @param layerPattern
   * @return This column.
   */
  public Column addLayerMatch(LayerMatch layerPattern) {
    if (!layers.containsKey(layerPattern.getId())) {
      layers.put(layerPattern.getId(), new Vector<LayerMatch>());
    }
    layers.get(layerPattern.getId()).add(layerPattern);
    return this;
  } // end of addLayerMatch()
  
  /**
   * Gets the first match for the given layer, if any.
   * @param layerId
   * @return The first match for the given layer, or null if there is none.
   */
  public LayerMatch getFirstLayerMatch(String layerId) {
    List<LayerMatch> matches = layers.get(layerId);
    if (matches != null && matches.size() > 0) {
      return matches.get(0);
    }
    return null;
  } // end of getFirstLayerMatch()
  
  /**
   * Initializes the bean with the given JSON representation.
   * @param json
   * @return A reference to this object.
   */
  public CloneableBean fromJson(JsonObject json) {
    if (json.containsKey("layers")) {
      JsonObject jsonLayers = json.getJsonObject("layers");
      for (String key : jsonLayers.keySet()) {
        if (jsonLayers.get(key).getValueType() == JsonValue.ValueType.OBJECT) {
          JsonObject jsonLayer = jsonLayers.getJsonObject(key);
          LayerMatch layer = (LayerMatch)(new LayerMatch().fromJson(jsonLayer));
          // the JSON object itself may not include the ID, so ensure it's set from the key
          layer.setId(key);
          addLayerMatch(layer);
        } else { // an array
          JsonArray jsonArray = jsonLayers.getJsonArray(key);
          for (int l = 0; l < jsonArray.size(); l++) {
            JsonObject jsonLayer = jsonArray.getJsonObject(l);
            LayerMatch layer = (LayerMatch)(new LayerMatch().fromJson(jsonLayer));
            // the JSON object itself may not include the ID, so ensure it's set from the key
            layer.setId(key);
            addLayerMatch(layer);
          } // next element of array
        } // array
      } // next element
    }
    if (json.containsKey("adj")) {
      setAdj(json.getInt("adj"));
    }
    return this;
  }

  /**
   * Returns the JSON serialization of this column.
   * @return The JSON serialization of this column.
   */
  @Override public String toString() {
    return toJson().toString();
  } // end of toString()
}
