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
 * A serializer/deserializer which can be used to generate or interpret a String ID for a
 * search results match.  
 * <p> This is a subclass of {@link IdUtterance}, using the same schema for IDs, with the
 * following extensions: 
 * <ul>
 * 	<li>
 * 		matching subparts can be identified by appending a list of annotation UIDs
 *              for insertion into {@link #mMatchAnnotationUids}, the keys being enclosed
 *              in square brackets:<br> 
 * 		...;<em>[key]=uid;[key]=uid</em><br>
 * 		e.g. <samp>g_243;n_72700-n_72709;[0,0]=ew_0_123;[1,0]ew_0_234</samp></li>
 * 	<li>
 * 		a target annotation by appending a uid prefixed by <samp>#=</samp>:<br>
 * 		...;#=<em>uid</em><br>
 * 		e.g. <samp>g_243;n_72700-n_72709;#=ew_0_123</samp></li>
 * </ul>
 * @author Robert Fromont robert@fromont.net.nz
 */
public class IdMatch extends IdUtterance {
  
  /**
   * A map of keys to annotation UIDs that identify the annotations that matched the
   * query. The keys identify which part of the query was matched. 
   * @see #getMatchAnnotationUids()
   * @see #setMatchAnnotationUids(Map<String,String>)
   */
  protected Map<String,String> matchAnnotationUids = new TreeMap<String,String>();
  /**
   * Getter for {@link #matchAnnotationUids}: A map of keys to annotation UIDs that
   * identify the annotations that matched the query. The keys identify which part of the
   * query was matched. 
   * @return A map of keys to annotation UIDs that identify the annotations that matched
   * the query. The keys identify which part of the query was matched. 
   */
  public Map<String,String> getMatchAnnotationUids() { return matchAnnotationUids; }
  
  /**
   * The UID of the targeted annotation, which may or may not be listed in
   * {@link #matchAnnotationUids}.
   * @return The UID of the targeted annotation, which may or may not be listed in
   * {@link #matchAnnotationUids}. 
   */
  public String getTargetAnnotationUid() {
    return attributes.get("#");
  }
  /**
   * The UID of the targeted annotation, which may or may not be listed in
   * {@link #matchAnnotationUids}.
   * @param newTargetAnnotationUid The UID of the targeted annotation, which may or may
   * not be listed in {@link #matchAnnotationUids}.
   */
  public IdMatch setTargetAnnotationUid(String newTargetAnnotationUid) {
    attributes.put("#", newTargetAnnotationUid);
    return this;
  }
  
  /**
   * Default constructor
   */
  public IdMatch() {
  } // end of constructor
  
  /**
   * Constructor from string ID.
   */
  public IdMatch(String sId) {
    setId(sId);
  } // end of constructor
  
  /**
   * Copy constructor.
   */
  public IdMatch(IdMatch other) {
    setGraphId(other.getGraphId());
    setDefiningAnnotationUid(other.getDefiningAnnotationUid());
    setStartAnchorId(other.getStartAnchorId());
    setEndAnchorId(other.getEndAnchorId());
    setSpeakerNumber(other.getSpeakerNumber());
    setTargetAnnotationUid(other.getTargetAnnotationUid());
    for (String sKey: other.getMatchAnnotationUids().keySet()) {
      matchAnnotationUids.put(sKey, other.getMatchAnnotationUids().get(sKey));
    }
  } // end of constructor
  
  /**
   * Adds a single match UID to {@link #matchAnnotationUids}.
   * @param key
   * @param uid
   * @return This object.
   */
  public IdMatch addMatchAnnotationUid(String key, String uid) {
    matchAnnotationUids.put(key, uid);
    return this;
  } // end of addMatchAnnotationUid()
  
  /**
   * Sets the utterance's attributes using the given identifier.
   * @param sId
   */
  public void setId(String sId) {
    super.setId(sId);
    
    // now transfer the matches from the attributes to the matches collection
    for (Object oKey : attributes.keySet()) {
      String sKey = (String)oKey;
      if (sKey.startsWith("[") && sKey.endsWith("]")) {
        matchAnnotationUids.put(sKey.substring(1, sKey.length()-1), attributes.get(sKey));
      }
    }
    for (String sKey : matchAnnotationUids.keySet()) {
      attributes.remove("["+sKey+"]");
    }
  }
  
  /**
   * Generates an identifier for this utterance.
   * @return A uniformly-formatted identifier for this utterance.
   */
  public String getId() {
    StringBuilder sId = new StringBuilder(super.getId());
    // append the matches
    for (String sKey : matchAnnotationUids.keySet()) {
      sId.append(";[");
      sId.append(sKey);
      sId.append("]=");
      sId.append(matchAnnotationUids.get(sKey));
    }
    return sId.toString();
  }
  
  /**
   * Determines whether another IMatch is equal to this one.
   * @param other
   * @return true if the critical attributes match, false otherwise.
   */
  @Override public boolean equals(Object o) {
    if (o == null) return false;
    if (o instanceof IdMatch) {
      IdMatch other = (IdMatch)o;
      if (!getGraphId().equals(other.getGraphId())) return false;
      if (getDefiningAnnotationUid() == null && other.getDefiningAnnotationUid() != null)
        return false;
      if (getDefiningAnnotationUid() != null && other.getDefiningAnnotationUid() == null)
        return false;
      if (getDefiningAnnotationUid() != null
          && !getDefiningAnnotationUid().equals(other.getDefiningAnnotationUid())) return false;
      if (getStartAnchorId() == null && other.getStartAnchorId() != null) return false;
      if (getStartAnchorId() != null && other.getStartAnchorId() == null) return false;
      if (getStartAnchorId() != null
          && !getStartAnchorId().equals(other.getStartAnchorId())) return false;
      if (getEndAnchorId() == null && other.getEndAnchorId() != null) return false;
      if (getEndAnchorId() != null && other.getEndAnchorId() == null) return false;
      if (getEndAnchorId() != null
          && !getEndAnchorId().equals(other.getEndAnchorId())) return false;
      if (getSpeakerNumber() == null && other.getSpeakerNumber() != null) return false;
      if (getSpeakerNumber() != null && other.getSpeakerNumber() == null) return false;
      if (getSpeakerNumber() != null
          && !getSpeakerNumber().equals(other.getSpeakerNumber())) return false;
      if (getTargetAnnotationUid() == null && other.getTargetAnnotationUid() != null)
        return false;
      if (getTargetAnnotationUid() != null && other.getTargetAnnotationUid() == null)
        return false;
      if (getTargetAnnotationUid() != null
          && !getTargetAnnotationUid().equals(other.getTargetAnnotationUid())) return false;
      if (getMatchAnnotationUids().size() != other.getMatchAnnotationUids().size()) return false;
      for (String sKey: getMatchAnnotationUids().keySet()) {
        if (!matchAnnotationUids.get(sKey).equals(other.getMatchAnnotationUids().get(sKey)))
          return false;
      }
      return true;
    }
    return false;
  } // end of equals()
  
  /**
   * Returns a hash code value for the object. This method is supported for the benefit of
   * hashtables such as those provided by java.util.Hashtable. 
   */
  @Deprecated public int hashCode() {
    return getId().hashCode();
  }

}
