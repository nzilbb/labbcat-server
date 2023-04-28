//
// Copyright 2013 New Zealand Institute of Language, Brain and Behaviour, 
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

import java.util.Map;
import java.util.TreeMap;

/**
 * Serializer/deserializer for a string-encoded identifier for a single utterance.
 * <p> To make scripting and a number of other things easier, we need a schema for
 * identifying a graph fragment with a single string identifier.
 * <p> Some combination of
 * <ul>
 * 	<li> graph ID (integer)</li>
 * 	<li> start anchor ID (long integer)</li>
 * 	<li> end anchor ID (long integer)</li>
 * 	<li> speaker number (integer)</li>
 * 	<li> defining annotation UID (string)</li>
 * </ul>
 * <p> ...can be used to construct a graph fragment that starts and ends at particular
 * anchors and maybe filters out annotations that belong to other speakers. Strictly
 * speaking the graph ID is redundant, as an anchor ID or annotation UID can be used to
 * identify the graph (but having it is convenient).</p> 
 * <p> Being able to recover the fragment from anchor offsets would be useful too.
 * <p> It is also handy to be able to append optional extra info to the ID, e.g.
 * <ul>
 * 	<li> prefix/result number</li>
 * 	<li> maybe search name</li>
 * 	<li> pointers into the fragment, e.g. the match word, or ideally, a range or list
 *           of match annotations, for search results.</li>
 * </ul>
 * <p> It should be short, and it should be possible to reconstruct a working URL from the ID.
 * <p> The schema is:
 * <ul>
 * 	<li>
 * 	        when there's a defining annotation UID:<br>
 * 		g_<i>ag_id</i>;<em>uid</em><br>
 * 		e.g. <tt>g_243;em_12_20035</tt></li>
 * 	<li>
 * 		when there's anchor IDs:<br>
 * 		g_<i>ag_id</i>;<em>startuid</em>-<em>enduid</em><br>
 * 		e.g. <tt>g_243;n_72700-n_72709</tt></li>
 * 	<li>
 * 		when there's anchor offsets:<br>
 * 		g_<i>ag_id</i>;<em>startoffset</em>-<em>endoffset</em><br>
 * 		e.g. <tt>g_243;39.400-46.279</tt></li>
 * 	<li>
 * 		when there's a participant/speaker number, it will be appended:<br>
 * 		<em>...</em>;p_<em>speakernumber</em><br>
 * 		e.g. <tt>g_243;n_72700-n_72709;p_76</tt></li>
 * 	<li>
 * 		other items (search name or prefix) could then come after all that, and
 *              key=value pairs:<br> 
 * 		...;<em>key</em>=<em>value</em><br>
 * 		e.g. <tt>g_243;n_72700-n_72709;ew_0_123-ew_0_234;prefix=024-;name=the_aeiou</tt></li>
 * </ul>
 * @author Robert Fromont robert@fromont.net.nz
 */
public class IdUtterance {
  
  /**
   * ag_id of the transcript.
   * @see #getGraphId()
   * @see #setGraphId(Integer)
   */
  protected Integer graphId;
  /**
   * Getter for {@link #graphId}: ag_id of the transcript.
   * @return ag_id of the transcript.
   */
  public Integer getGraphId() { return graphId; }
  /**
   * Setter for {@link #graphId}: ag_id of the transcript.
   * @param newGraphId ag_id of the transcript.
   */
  public IdUtterance setGraphId(Integer newGraphId) { graphId = newGraphId; return this; }

  /**
   * anchor_id of the anchor that is the start of the utterance.
   * @see #getStartAnchorId()
   * @see #setStartAnchorId(Long)
   */
  protected Long startAnchorId;
  /**
   * Getter for {@link #startAnchorId}: anchor_id of the anchor that is the start of the
   * utterance. 
   * @return anchor_id of the anchor that is the start of the utterance.
   */
  public Long getStartAnchorId() { return startAnchorId; }
  /**
   * Setter for {@link #startAnchorId}: anchor_id of the anchor that is the start of the
   * utterance. 
   * @param newStartAnchorId anchor_id of the anchor that is the start of the utterance.
   */
  public IdUtterance setStartAnchorId(Long newStartAnchorId) { startAnchorId = newStartAnchorId; return this; }

  /**
   * anchor_id of the anchor that is the end of the utterance.
   * @see #getEndAnchorId()
   * @see #setEndAnchorId(Long)
   */
  protected Long endAnchorId;
  /**
   * Getter for {@link #endAnchorId}: anchor_id of the anchor that is the end of the utterance.
   * @return anchor_id of the anchor that is the end of the utterance.
   */
  public Long getEndAnchorId() { return endAnchorId; }
  /**
   * Setter for {@link #endAnchorId}: anchor_id of the anchor that is the end of the utterance.
   * @param newEndAnchorId anchor_id of the anchor that is the end of the utterance.
   */
  public IdUtterance setEndAnchorId(Long newEndAnchorId) { endAnchorId = newEndAnchorId; return this; }

  /**
   * The start offset, if known.
   * @see #getStartOffset()
   * @see #setStartOffset(Double)
   */
  protected Double startOffset;
  /**
   * Getter for {@link #startOffset}: The start offset, if known.
   * @return The start offset, if known.
   */
  public Double getStartOffset() { return startOffset; }
  /**
   * Setter for {@link #startOffset}: The start offset, if known.
   * @param newStartOffset The start offset, if known.
   */
  public IdUtterance setStartOffset(Double newStartOffset) { startOffset = newStartOffset; return this; }

  /**
   * The end offset, if known.
   * @see #getEndOffset()
   * @see #setEndOffset(Double)
   */
  protected Double endOffset;
  /**
   * Getter for {@link #endOffset}: The end offset, if known.
   * @return The end offset, if known.
   */
  public Double getEndOffset() { return endOffset; }
  /**
   * Setter for {@link #endOffset}: The end offset, if known.
   * @param newEndOffset The end offset, if known.
   */
  public IdUtterance setEndOffset(Double newEndOffset) { endOffset = newEndOffset; return this; }

  /**
   * speaker_number of the participant who spoke the utterance.
   * @see #getSpeakerNumber()
   * @see #setSpeakerNumber(Integer)
   */
  protected Integer speakerNumber;
  /**
   * Getter for {@link #speakerNumber}: speaker_number of the participant who spoke the utterance.
   * @return speaker_number of the participant who spoke the utterance.
   */
  public Integer getSpeakerNumber() { return speakerNumber; }
  /**
   * Setter for {@link #speakerNumber}: speaker_number of the participant who spoke the utterance.
   * @param newSpeakerNumber speaker_number of the participant who spoke the utterance.
   */
  public IdUtterance setSpeakerNumber(Integer newSpeakerNumber) { speakerNumber = newSpeakerNumber; return this; }

  /**
   * UID of the annotation that defines the bounds of this utterance, if any.
   * @see #getDefiningAnnotationUid()
   * @see #setDefiningAnnotationUid(String)
   */
  protected String definingAnnotationUid;
  /**
   * Getter for {@link #definingAnnotationUid}: UID of the annotation that defines the
   * bounds of this utterance, if any. 
   * @return UID of the annotation that defines the bounds of this utterance, if any.
   */
  public String getDefiningAnnotationUid() { return definingAnnotationUid; }
  /**
   * Setter for {@link #definingAnnotationUid}: UID of the annotation that defines the
   * bounds of this utterance, if any. 
   * @param newDefiningAnnotationUid UID of the annotation that defines the bounds of this
   * utterance, if any. 
   */
  public IdUtterance setDefiningAnnotationUid(String newDefiningAnnotationUid) { definingAnnotationUid = newDefiningAnnotationUid; return this; }

  /**
   * Extended attributes of the utterance, including "prefix" for the result prefix,
   * "name" for the search name, etc. 
   * @see #getAttributes()
   */
  protected Map<String,String> attributes = new TreeMap<String,String>();
  /**
   * Getter for {@link #attributes}: Extended attributes of the utterance, including
   * "prefix" for the result prefix, "name" for the search name, etc. 
   * @return Extended attributes of the utterance, including "prefix" for the result
   * prefix, "name" for the search name, etc. 
   */
  public Map<String,String> getAttributes() { return attributes; }

  /**
   * Default constructor
   */
  public IdUtterance() {
  } // end of constructor

  /**
   * Constructor for a string ID.
   * @param sId The utterance ID.
   */
  public IdUtterance(String sId) {
    setId(sId);
  } // end of constructor

  /**
   * Sets the utterance's attributes using the given identifier
   * @param sId The ID.
   */
  public void setId(String sId) {
    // break the string on semicolons
    for (String sPart : sId.split(";")) {
      if (sPart.length() == 0) continue;
      int iEquals = sPart.indexOf('=');
      if (iEquals > 0) { // it's an extended attribute
        attributes.put(sPart.substring(0, iEquals), sPart.substring(iEquals+1));
      } else {
        int iRangeDelimiter = sPart.indexOf('-');
        if (iRangeDelimiter > 0) { // it's a range of somethings
          String sFrom = sPart.substring(0, iRangeDelimiter);
          String sTo = sPart.substring(iRangeDelimiter+1);
          if (sFrom.startsWith("n") && sTo.startsWith("n")) { // nodes/anchors
            if (startAnchorId == null && endAnchorId == null) {
              setStartAnchorId(Long.valueOf(sFrom.substring(2)));
              setEndAnchorId(Long.valueOf(sTo.substring(2)));
            } else {
              // TODO sub-annotation range not yet supported
            }
          } else if (sFrom.startsWith("e") && sTo.startsWith("e")) { // edges/annotations
            // TODO annotation range not yet supported
          } else {
            // presumably an offset range
            setStartOffset(Double.valueOf(sFrom));
            setEndOffset(Double.valueOf(sTo));
          }
        } else { // must be a uid
          switch (sPart.charAt(0)) {
            case 'g': setGraphId(new Integer(sPart.substring(sPart.indexOf('_')+1))); break;
            case 'p': setSpeakerNumber(new Integer(sPart.substring(sPart.indexOf('_')+1))); break;
            case 'e': 
              if (definingAnnotationUid == null // not defined yet
                  // not defined by anchors yet either:
                  && startAnchorId == null && endAnchorId == null) { 
                // it's the defining annotation
                setDefiningAnnotationUid(sPart);
              } else { // target annotation within the fragment
                // TODO target annotation UID not yet supported
              }
          }
        }
      }
    }
  } // end of setId()
  
  /**
   * Generates an identifier for this utterance.
   * @return A uniformly-formatted identifier for this utterance.
   */
  public String getId() {
    StringBuilder s = new StringBuilder();
    if (graphId != null) {
      s.append("g_");
      s.append(graphId.toString());
    }
    if (definingAnnotationUid != null) {
      if (s.length() > 0) s.append(";");
      s.append(definingAnnotationUid);
    }
    if (startAnchorId != null && endAnchorId != null) {
      if (s.length() > 0) s.append(";");
      s.append("n_");
      s.append(startAnchorId.toString());
      s.append("-n_");
      s.append(endAnchorId.toString());
    }
    if (speakerNumber != null) {
      if (s.length() > 0) s.append(";");
      s.append("p_");
      s.append(speakerNumber.toString());
    }
    for (Object oKey : attributes.keySet()) {
      if (attributes.get(oKey) == null) continue;
      if (s.length() > 0) s.append(";");
      s.append(oKey.toString());
      s.append("=");
      s.append(attributes.get(oKey).toString());
    }
    return s.toString();
  } // end of getId()

  /**
   * Result prefix, if any.
   * @return Result prefix, if any.
   */
  public String getPrefix() { return attributes.get("prefix"); }
  
  /**
   * Setter for result prefix, if any.
   * @param sNewPrefix Result prefix, if any.
   */
  public IdUtterance setPrefix(String sNewPrefix) { attributes.put("prefix", sNewPrefix); return this; }
  
  /**
   * String representation of this object - the same string returned by {@link #getId()}
   * @return The same string returned by {@link #getId()}
   */
  public String toString() {
    return getId();
  } // end of toString()
  
  /**
   * Determines whether this identifies that same utterance as another object. This method
   * ignores Prefix and any other extraneous properties. 
   * @param o The other object.
   * @return true if they both identify the same stretch of speech by the same participant, false otherwise
   */
  @Override public boolean equals(Object o) {
    if (o instanceof IdUtterance) {
      IdUtterance other = (IdUtterance)o;
      return getGraphId().equals(other.getGraphId())
        && getStartAnchorId().equals(other.getStartAnchorId())
        && getEndAnchorId().equals(other.getEndAnchorId())
        && (getSpeakerNumber() == null || getSpeakerNumber().equals(other.getSpeakerNumber()))
        && (getDefiningAnnotationUid() == null
            || getDefiningAnnotationUid().equals(other.getDefiningAnnotationUid()));
    }
    return false;
  } // end of equals()

}
