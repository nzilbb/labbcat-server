//
// Copyright 2021 New Zealand Institute of Language, Brain and Behaviour, 
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

import java.util.Collection;
import java.util.HashSet;
import java.util.Spliterator;
import java.util.function.Consumer;
import nzilbb.ag.*;
import nzilbb.util.MonitorableSeries;

/**
 * A Graph series that filters another Graph series, collapsing consecutive instances of
 * the same graph into one graph.
 * <p> Annotations from selected layers a copied from subsequent instances into the first,
 * before the first graph is returned.
 * @author Robert Fromont robert@fromont.net.nz
 */
public class ConsolidatedGraphSeries implements MonitorableSeries<Graph> {
  
  /**
   * The source of the graphs to consolidate.
   * @see #getSource()
   * @see #setSource(MonitorableSeries<Graph>)
   */
  protected MonitorableSeries<Graph> source;
  /**
   * Getter for {@link #source}: The source of the graphs to consolidate.
   * @return The source of the graphs to consolidate.
   */
  public MonitorableSeries<Graph> getSource() { return source; }
  /**
   * Setter for {@link #source}: The source of the graphs to consolidate.
   * @param newSource The source of the graphs to consolidate.
   */
  public ConsolidatedGraphSeries setSource(MonitorableSeries<Graph> newSource) { source = newSource; return this; }
  
  /**
   * List of layers to consolidate the annotations of.
   * @see #getCopyLayers()
   * @see #setCopyLayers(Collection<String>)
   */
  protected Collection<String> copyLayers = new HashSet<String>();
  /**
   * Getter for {@link #copyLayers}: List of layers to consolidate the annotations of.
   * @return List of layers to consolidate the annotations of.
   */
  public Collection<String> getCopyLayers() { return copyLayers; }
  /**
   * Setter for {@link #copyLayers}: List of layers to consolidate the annotations of.
   * @param newCopyLayers List of layers to consolidate the annotations of.
   */
  public ConsolidatedGraphSeries setCopyLayers(Collection<String> newCopyLayers) { copyLayers = newCopyLayers; return this; }

  /** Default constructor **/
  public ConsolidatedGraphSeries() {
  }

  /** 
   * Constructor from source series.
   * @param source The source of the graphs to consolidate.
   */
  public ConsolidatedGraphSeries(MonitorableSeries<Graph> source) {
    setSource(source);
  }
  
  /**
   * Add a layer to #copyLayers.
   * @param layerId
   * @return this
   */
  public ConsolidatedGraphSeries copyLayer(String layerId) {
    copyLayers.add(layerId);
    return this;
  } // end of copyLayer()

  // MonitorableTask implementations
  
  /**
   * Determines how far through the task is is.
   * @return An integer between 0 and 100 (inclusive), or null if progress can not be calculated.
   */
  public Integer getPercentComplete() {
    return source.getPercentComplete();
  }
   
   /** Cancels the task. */
  public void cancel() {
    source.cancel();
  }

   /**
    * Reveals whether the task is still running or not.
    * @return true if the task is currently running, false otherwise.
    */
  public boolean getRunning() {
    return source.getRunning();
  }

  // Spliterator implementations
   
  public int characteristics() {      
    return source.characteristics();
  }

  /**
   * Counts the elements in the series, if possible.
   * @return The number of elements in the series, or null if the number is unknown.
   */
  public long estimateSize() {
    return source.estimateSize();
  }

  public Spliterator<Graph> trySplit() {
    return null;
  }

  protected Graph nextGraph = null;
  protected Graph lastGraph = null;

  /**
   * If a remaining element exists, performs the given action on it, returning true; else
   * returns false.
   */
  public boolean tryAdvance(Consumer<? super Graph> action) {
    while (nextGraph == null && source.tryAdvance(graph -> {
          if (lastGraph == null) { // first time
            lastGraph = graph;
          } else if (graph.getId().equals(lastGraph.getId())) { // same graph as last one
            // copy annotations from selected layers
            for (String layerId : copyLayers) {
              for (Annotation annotation : graph.all(layerId)) {
                lastGraph.addAnnotation(new Annotation(annotation));
              } // next annotation
            } // next layer
          } else {
            nextGraph = graph;
          }
        })) {} // next graph from source
    if (lastGraph != null) {
      lastGraph.commit();
      if (action != null) action.accept(lastGraph);
      lastGraph = nextGraph;
      nextGraph = null;
      return true;
    } else {
      return false;
    }    
  }
}
