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
package nzilbb.labbcat.server.search;

import nzilbb.util.CloneableBean;
import nzilbb.util.ClonedProperty;

/**
 * One cell in a search matrix, containing a pattern to match on one layer.
 * <p> General principles, which are not enforced by these classes, are:
 * <ul>
 *  <li> If {@link #pattern} is set, then {@link #min} and {@link #max} should be null, and
 *       vice-versa</li>
 *  <li> Only one LayerMatch in a {@link Matrix} has {@link #target} == true.</li>
 * </ul>
 */
public class LayerMatch implements CloneableBean {
  
  /**
   * The Layer ID to match.
   * @see #getId()
   * @see #setId(String)
   */
  protected String id;
  /**
   * Getter for {@link #id}: The Layer ID to match.
   * @return The Layer ID to match.
   */
  @ClonedProperty
  public String getId() { return id; }
  /**
   * Setter for {@link #id}: The Layer ID to match.
   * @param newId The Layer ID to match.
   */
  public LayerMatch setId(String newId) { id = newId; return this; }
  
  /**
   * The regular expression to match the label against.
   * @see #getPattern()
   * @see #setPattern(String)
   */
  protected String pattern;
  /**
   * Getter for {@link #pattern}: The regular expression to match the label against.
   * @return The regular expression to match the label against.
   */
  @ClonedProperty
  public String getPattern() { return pattern; }
  /**
   * Setter for {@link #pattern}: The regular expression to match the label against.
   * @param newPattern The regular expression to match the label against. An
   * empty string results in null being assigned.
   */
  public LayerMatch setPattern(String newPattern) {
    pattern = newPattern != null && newPattern.length() == 0? null : newPattern;
    return this;
  }

  /**
   * Whether the {@link #pattern} is being negated (i.e. selecting tokens that don't match)
   * or not.
   * @see #getNot()
   * @see #setNot(Boolean)
   */
  protected Boolean not;
  /**
   * Getter for {@link #not}: Whether the {@link #pattern} is being negated
   * (i.e. selecting tokens that don't match) or not. 
   * @return Whether the {@link #pattern} is being negated (i.e. selecting tokens that don't
   * match) or not. 
   */
  @ClonedProperty
  public Boolean getNot() { return not; }
  /**
   * Setter for {@link #not}: Whether the {@link #pattern} is being negated
   * (i.e. selecting tokens that don't match) or not. 
   * @param newNot Whether the {@link #pattern} is being negated (i.e. selecting tokens
   * that don't match) or not. 
   */
  public LayerMatch setNot(Boolean newNot) { not = newNot; return this; }

  /**
   * Whether the {@link #pattern} is to matched in a case-sensitive manner.
   * @see #getCaseSensitive()
   * @see #setCaseSensitive(Boolean)
   */
  protected Boolean caseSensitive;
  /**
   * Getter for {@link #caseSensitive}: Whether the {@link #pattern} is to matched in a
   * case-sensitive manner.
   * @return Whether the {@link #pattern} is to matched in a case-sensitive manner.
   */
  @ClonedProperty
  public Boolean getCaseSensitive() { return caseSensitive; }
  /**
   * Setter for {@link #caseSensitive}: Whether the {@link #pattern} is to matched in a
   * case-sensitive manner.
   * @param newCaseSensitive Whether the {@link #pattern} is to matched in a
   * case-sensitive manner.
   */
  public LayerMatch setCaseSensitive(Boolean newCaseSensitive) { caseSensitive = newCaseSensitive; return this; }
  
  /**
   * The minimum value for the label, assuming it represents a number.
   * @see #getMin()
   * @see #setMin(Double)
   */
  protected Double min;
  /**
   * Getter for {@link #min}: The minimum value for the label.
   * @return The minimum value for the label.
   */
  @ClonedProperty
  public Double getMin() { return min; }
  /**
   * Setter for {@link #min}: The minimum value for the label.
   * @param newMin The minimum value for the label.
   */
  public LayerMatch setMin(Double newMin) {
    min = newMin;
    return this;
  }
  /**
   * Setter for {@link #min}.
   * @param newMin A string representing the minimum value for the label. An
   * empty string results in null being assigned.
   */
  public LayerMatch setMinString(String newMin) {
    min = newMin != null && newMin.length() == 0? null : Double.valueOf(newMin);
    return this;
  }
  
  /**
   * The maximum value for the label.
   * @see #getMax()
   * @see #setMax(Double)
   */
  protected Double max;
  /**
   * Getter for {@link #max}: The maximum value for the label.
   * @return The maximum value for the label.
   */
  @ClonedProperty
  public Double getMax() { return max; }
  /**
   * Setter for {@link #max}: The maximum value for the label.
   * @param newMax The maximum value for the label.
   */
  public LayerMatch setMax(Double newMax) {
    max = newMax;
    return this;
  }
  /**
   * Setter for {@link #max}.
   * @param newMax A string representing the maximum value for the label.
   */
  public LayerMatch setMaxString(String newMax) {
    max = newMax != null && newMax.length() == 0? null : Double.valueOf(newMax);
    return this;
  }
    
  /**
   * Whether this matrix cell is the target of the search.
   * @see #getTarget()
   * @see #setTarget(Boolean)
   */
  protected Boolean target;
  /**
   * Getter for {@link #target}: Whether this matrix cell is the target of the search.
   * @return Whether this matrix cell is the target of the search.
   */
  @ClonedProperty
  public Boolean getTarget() { return target; }
  /**
   * Setter for {@link #target}: Whether this matrix cell is the target of the search.
   * @param newTarget Whether this matrix cell is the target of the search.
   */
  public LayerMatch setTarget(Boolean newTarget) { target = newTarget; return this; }

  /**
   * Whether this condition is anchored to the start of the word token.
   * @see #getAnchorStart()
   * @see #setAnchorStart(Boolean)
   */
  protected Boolean anchorStart;
  /**
   * Getter for {@link #anchorStart}: Whether this condition is anchored to the start of
   * the word token. 
   * @return Whether this condition is anchored to the start of the word token.
   */
  @ClonedProperty
  public Boolean getAnchorStart() { return anchorStart; }
  /**
   * Setter for {@link #anchorStart}: Whether this condition is anchored to the start of
   * the word token. 
   * @param newAnchorStart Whether this condition is anchored to the start of the word token.
   */
  public LayerMatch setAnchorStart(Boolean newAnchorStart) { anchorStart = newAnchorStart; return this; }
  
  /**
   * Whether this condition is anchored to the end of the word token.
   * @see #getAnchorEnd()
   * @see #setAnchorEnd(Boolean)
   */
  protected Boolean anchorEnd;
  /**
   * Getter for {@link #anchorEnd}: Whether this condition is anchored to the end of the
   * word token. 
   * @return Whether this condition is anchored to the end of the word token.
   */
  @ClonedProperty
  public Boolean getAnchorEnd() { return anchorEnd; }
  /**
   * Setter for {@link #anchorEnd}: Whether this condition is anchored to the end of the
   * word token. 
   * @param newAnchorEnd Whether this condition is anchored to the end of the word token.
   */
  public LayerMatch setAnchorEnd(Boolean newAnchorEnd) { anchorEnd = newAnchorEnd; return this; }
  
  /**
   * Ensures that Boolean attributes ({@link #target}, {@link #not}, {@link #caseSensitive},
   * {@link #anchorStart}, {@link #anchorEnd}) have non-null values. Any with null values
   * are assumed to be false.
   */
  public LayerMatch setNullBooleans() {
    if (target == null) target = Boolean.FALSE;
    if (not == null) not = Boolean.FALSE;
    if (caseSensitive == null) caseSensitive = Boolean.FALSE;
    if (anchorStart == null) anchorStart = Boolean.FALSE;
    if (anchorEnd == null) anchorEnd = Boolean.FALSE;
    return this;
  } // end of setNullBooleans()  
  
  /**
   * Ensures that the pattern includes anchoring to the beginning (^) and end ($) of
   * input.
   * <p> After this method is called, <q><var>pattern</var></q> will instead be
   * <q>^(<var>pattern</var>)$</q>
   */
  public LayerMatch ensurePatternAnchored() {
    if (pattern != null) {
      if (!pattern.startsWith("^")) pattern = "^(" + pattern + ")";
      if (!pattern.endsWith("$")) pattern += "$";
    }
    return this;
  } // end of ensurePatternAnchored()

  /**
   * Returns the JSON serialization of this layer.
   * @return The JSON serialization of this layer.
   */
  @Override public String toString() {
    return toJson().toString();
  } // end of toString()
  
  /**
   * Determines whether the given LayerMatch actually specifies a {@link #pattern},
   * {@link #min}, or {@link #max}.
   * @param layer The layer match to check.
   * @return true if the given LayerMatch specifies a {@link #pattern}, {@link #min},
   * or {@link #max}, false otherwise.
   */
  public static boolean HasCondition(LayerMatch layer) {
    return layer.getPattern() != null
      || layer.getMin() != null
      || layer.getMax() != null;
  } // end of HasPattern()

  /**
   * Determines whether the given LayerMatch is the target or not.
   * @param layer The layer match to check.
   * @return true if the given LayerMatch has {@link #target} set to TRUE, false otherwise.
   * @see #NotTarget(LayerMatch)
   */
  public static boolean IsTarget(LayerMatch layer) {
    return layer.getTarget() != null && layer.getTarget();
  } // end of HasPattern()

  /**
   * Determines whether the given LayerMatch is the target or not, but the revers of
   * {@link #IsTarget(LayerMatch)}.
   * @param layer The layer match to check.
   * @return false if the given LayerMatch has {@link #target} set to TRUE, true otherwise.
   */
  public static boolean NotTarget(LayerMatch layer) {
    return !IsTarget(layer);
  } // end of HasPattern()

}
