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
import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;
import nzilbb.ag.*;
import nzilbb.util.MonitorableSeries;

/**
 * An implementation of Spliterator&lt;Graph&gt; that enumerates fragments corresponding
 * to a list of selected fragment Ids. 
 * @author Robert Fromont robert@fromont.net.nz
 */

public class FragmentSeries implements MonitorableSeries<Graph> {
   
   // Attributes:

   private long nextRow = 0;
   private long rowCount = -1;
   private Iterator<String> iterator;
   private boolean cancelling = false;

   /**
    * Whether the task is currently running.
    * @see #getRunning()
    * @see #setRunning(boolean)
    */
   protected boolean running = false;
   /**
    * Getter for {@link #running}: Whether the task is currently running.
    * @return Whether the task is currently running.
    */
   public boolean getRunning() { return running; }
   /**
    * Setter for {@link #running}: Whether the task is currently running.
    * @param newRunning Whether the task is currently running.
    */
   public void setRunning(boolean newRunning) { running = newRunning; }

   /**
    * The graph store object.
    * @see #getStore()
    * @see #setStore(SqlGraphStore)
    */
   protected SqlGraphStore store;
   /**
    * Getter for {@link #store}: The graph store object.
    * @return The graph store object.
    */
   public SqlGraphStore getStore() { return store; }
   /**
    * Setter for {@link #store}: The graph store object.
    * @param newStore The graph store object.
    */
   public FragmentSeries setStore(SqlGraphStore newStore) { store = newStore; return this; }

   /**
    * A collection of strings that identify a graph fragment.
    * @see #getFragmentIds()
    * @see #setFragmentIds(Collection)
    */
   protected Collection<String> fragmentIds;
   /**
    * Getter for {@link #fragmentIds}: A collection of strings that identify a graph fragment.
    * @return A collection of strings that identify a graph fragment.
    */
   public Collection<String> getFragmentIds() { return fragmentIds; }
   /**
    * Setter for {@link #fragmentIds}: A collection of strings that identify a graph fragment.
    * @param newFragmentIds A collection of strings that identify a graph fragment.
    */
   public FragmentSeries setFragmentIds(Collection<String> newFragmentIds) { fragmentIds = newFragmentIds; return this; }
   
   /**
    * Layers to load into the fragments.
    * @see #getLayers()
    * @see #setLayers(String[])
    */
   protected String[] layers;
   /**
    * Getter for {@link #layers}: Layers to load into the fragments.
    * @return Layers to load into the fragments.
    */
   public String[] getLayers() { return layers; }
   /**
    * Setter for {@link #layers}: Layers to load into the fragments.
    * @param newLayers Layers to load into the fragments.
    */
   public FragmentSeries setLayers(String[] newLayers) { layers = newLayers; return this; }
   
   // Methods:
   
   /**
    * Constructor.
    * @param fragmentIds A collection of strings that identify a graph fragment.
    * <p>These can be something like:
    * <ul>
    * <li><q>g_3;em_11_23;n_19985-n_20003;p_4;#=ew_0_12611;prefix=001-;[0]=ew_0_12611</q></li>
    * <li><q>AgnesShacklock-01.trs;60.897-67.922;prefix=001-</q></li>
    * <li><q>AgnesShacklock-01.trs;60.897-67.922;m_-1_23-</q></li>
    * </ul>
    * @throws SQLException If an error occurs retrieving results.
    */
   public FragmentSeries(Collection<String> fragmentIds, SqlGraphStore store, String[] layers)
      throws SQLException {
      
      setFragmentIds(fragmentIds);
      setStore(store);
      setLayers(layers);
      rowCount = fragmentIds.size();
      iterator = fragmentIds.iterator();
   } // end of constructor

   // Spliterator implementations
   
   public int characteristics() {
      
      return ORDERED | DISTINCT | IMMUTABLE | NONNULL | SUBSIZED | SIZED;
   }
   
   /**
    * Returns the next element of this enumeration if this enumeration object has at least
    * one more element to provide.
    */
   public boolean tryAdvance(Consumer<? super Graph> action) {
      
      running = false;
      
      if (cancelling) {
         running = false;
         return false;
      }
      if (!iterator.hasNext()) return false;
      String spec = iterator.next();
      try {
	 nextRow++;
         String[] parts = spec.split(";");
	 String graphId = parts[0];
         if (graphId.startsWith("g_")) graphId = graphId.substring(2);
         String intervalPart = null;
         for (int p = 1; p < parts.length; p++) {
            if (parts[p].indexOf("-") > 0) {
               intervalPart = parts[p];
               break;
            }
         }
	 String[] interval = intervalPart.split("-");
	 double start = 0.0;
	 double end = 0.0;
         if (interval[0].startsWith("n_")) { // anchor IDs
            Anchor[] anchors = store.getAnchors(graphId, interval);
            start = anchors[0].getOffset();
            end = anchors[1].getOffset();
         } else { // offsets
            start = Double.parseDouble(interval[0]);
            end = Double.parseDouble(interval[1]);
         }
	 String prefix = "";
	 String filterId = "";
         for (int p = 1; p < parts.length; p++) {
            if (parts[p].startsWith("prefix=")) {
               prefix = parts[p].substring("prefix=".length());
            }
            if ((parts[p].startsWith("em_") || parts[p].startsWith("m_"))
                // don't filter by utterance - it usually has no descendants, so just makes things
                // slower without actually discarding anything
                && !parts[p].startsWith("em_12_")) {
               filterId = parts[p];
            }
         }
        
         Graph fragment = store.getFragment(graphId, start, end, layers);
         fragment.shiftAnchors(-start);
         if (prefix.length() > 0) fragment.setId(prefix + fragment.getId());
         if (filterId.length() > 0) { // filter annotation is specified
            // remove annotations that don't belong to the specified filter annotation
            Annotation targetAncestor = fragment.getAnnotationsById().get(filterId);
            if (targetAncestor != null) { // target is in the graph
               for (Annotation a : fragment.getAnnotationsById().values()) {
                  if (a.getLayer().isAncestor(targetAncestor.getLayerId())) {
                     // annotation is a descendent of the participant layer
                     if (a.first(targetAncestor.getLayerId()) != targetAncestor) {
                        a.destroy();
                     } // annotation has a different ancestor on the same layer
                  } // annotation is a descendent of the target layer
               } // next annotation
            } // participant is in the graph
         } // target is specified
         fragment.commit();
	 action.accept(fragment);
         return true;
      } catch(Exception exception) {
         System.err.println("FragmentSeries: Could not get fragment from spec \""+spec+"\": "
                            + exception);
         running = false;
	 return false;
      }
   }

   /**
    * Counts the elements in the series, if possible.
    * @return The number of elements in the series, or null if the number is unknown.
    */
   public long estimateSize() {
      
      if (rowCount >= 0) return rowCount;
      return Long.MAX_VALUE;
   }

   public Spliterator<Graph> trySplit() {
      
      return null;
   }

   // GraphSeries methods
   
   /**
    * Determines how far through the serialization is.
    * @return An integer between 0 and 100 (inclusive), or null if progress can not be calculated.
    */
   public Integer getPercentComplete() {
      
      if (rowCount > 0) {
	 return (int)((nextRow * 100) / rowCount);
      }
      return null;
   }
   
   /**
    * Cancels spliteration; the next call to tryAdvance will return false.
    */
   public void cancel() {
      
      cancelling = true;
   }
} // end of class FragmentSeries
