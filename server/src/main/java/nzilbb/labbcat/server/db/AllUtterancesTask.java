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
import java.util.Arrays;
import java.util.Vector;
import java.util.stream.Collectors;
import nzilbb.ag.*;
import nzilbb.labbcat.server.search.SearchTask;

/**
 * A task that identifies all utterances of given participants, making them available as a
 * set of search results.
 * @author Robert Fromont robert@fromont.net.nz
 */
public class AllUtterancesTask extends SearchTask {
  
  /**
   * AGQL query identifying which participants to get all the utterances of.
   * @see #getParticipantQuery()
   * @see #setParticipantQuery(String)
   */
  protected String participantQuery;
  /**
   * Getter for {@link #participantQuery}: AGQL query identifying which participants to
   * get all the utterances of. 
   * @return AGQL query identifying which participants to get all the utterances of.
   */
  public String getParticipantQuery() { return participantQuery; }
  /**
   * Setter for {@link #participantQuery}: AGQL query identifying which participants to
   * get all the utterances of. 
   * @param newParticipantQuery AGQL query identifying which participants to get all the utterances of.
   */
  public AllUtterancesTask setParticipantQuery(String newParticipantQuery) { participantQuery = newParticipantQuery; return this; }
  
  /**
   * AGQL query identifying which transcripts to get all the utterances from.
   * @see #getTranscriptQuery()
   * @see #setTranscriptQuery(String)
   */
  protected String transcriptQuery;
  /**
   * Getter for {@link #transcriptQuery}: AGQL query identifying which transcripts to get
   * all the utterances from. 
   * @return AGQL query identifying which transcripts to get all the utterances from.
   */
  public String getTranscriptQuery() { return transcriptQuery; }
  /**
   * Setter for {@link #transcriptQuery}: AGQL query identifying which transcripts to get
   * all the utterances from. 
   * @param newTranscriptQuery AGQL query identifying which transcripts to get all the
   * utterances from. 
    */
   public AllUtterancesTask setTranscriptQuery(String newTranscriptQuery) { transcriptQuery = newTranscriptQuery; return this; }

  /**
   * Override prevents validation from failing because of lack of matrix.
   * @return A message describing a validation error, or null if the conditions are valid
   */
  @Override public String validate() {
    if (participantQuery == null || participantQuery.trim().length() == 0) {
      return "No participants specified";// TODO i18n
    }
    return null;
  }

  /**
   * Identifies all utterances of the given participants. 
   * @throws Exception
   */
  protected void search() throws Exception {

    iPercentComplete = 1;
    Connection connection = getStore().getConnection();
    final Schema schema = getStore().getSchema();

    // word columns
	 
    // list of results
    results = new SqlSearchResults(this);

    // gather participants and check they're accessible
    String[] participantIds = getStore().getMatchingParticipantIds(participantQuery);
    Vector<Annotation> participants = new Vector<Annotation>();
    for (String participantId : participantIds) {
      participants.add(getStore().getParticipant(participantId));
    }
    if (participants.size() == 0) throw new Exception("No participants matched."); // TODO i18n
    StringBuilder speakerNumberList = new StringBuilder(); 
    for (Annotation participant : participants) {
      if (speakerNumberList.length() > 0) speakerNumberList.append(",");
      speakerNumberList.append(participant.getId().replace("m_-2_",""));
    } // next participant
    switch(participants.size()) {
      case 1:
        description = participants.elementAt(0).getLabel();
        break;
      case 2:
        description = 
          participants.elementAt(0).getLabel() + " " + participants.elementAt(1).getLabel();
        break;
      default:
        description = 
          participants.elementAt(0).getLabel() + " et al. (" + participants.size() + ")";
        break;
    }
    ((SqlSearchResults)results).setName(description);
    setName(description);

    // transcripts
    StringBuilder transcriptClause = new StringBuilder();
    // (getting IDs from store ensures that access permissions are checked)
    String finalTranscriptQuery = "labels('participant').includesAny(["
      +Arrays.stream(participantIds)
      .map(id->"'"+id.replace("'","\\'")+"'")
      .collect(Collectors.joining(","))
      +"])";
    if (transcriptQuery != null && transcriptQuery.trim().length() > 0) {
      finalTranscriptQuery += " && "+transcriptQuery;
    }
    //setStatus(finalTranscriptQuery);
    String[] transcriptIds = getStore().getMatchingTranscriptIds(finalTranscriptQuery);
    if (transcriptIds.length == 0) throw new Exception("No transcripts matched."); // TODO i18n
    transcriptClause.append(" AND transcript.transcript_id IN (");
    transcriptClause.append(Arrays.stream(transcriptIds)
                            .map(id->"'"+id.replace("'","\\'")+"'")
                            .collect(Collectors.joining(",")));
    transcriptClause.append(")");

    StringBuilder mainSpeakerClause = new StringBuilder();
    if (getMainParticipantOnly()) {
      mainSpeakerClause.append(" AND transcript_speaker.main_speaker = 1");
    }    
    
    // generate an SQL statement from the conditions

    StringBuilder q = new StringBuilder();
    q.append("INSERT INTO _result");
    q.append(" (search_id,ag_id,speaker_number,");
    q.append(" start_anchor_id,end_anchor_id,defining_annotation_id,");
    q.append(" segment_annotation_id,target_annotation_id,first_matched_word_annotation_id,");
    q.append(" last_matched_word_annotation_id,complete,target_annotation_uid)");
    q.append(" SELECT ?,meta.ag_id,meta.label,");
    q.append(" meta_start.anchor_id,meta_end.anchor_id,meta.annotation_id,");
    q.append(" NULL,NULL,NULL,");
    q.append(" NULL,0,NULL");
    q.append(" FROM annotation_layer_" + SqlConstants.LAYER_UTTERANCE + " meta");
    q.append(" INNER JOIN anchor meta_start");
    q.append(" ON meta_start.anchor_id = meta.start_anchor_id");
    q.append(" INNER JOIN anchor meta_end");
    q.append(" ON meta_end.anchor_id = meta.end_anchor_id");
    q.append(" INNER JOIN transcript_speaker");
    q.append(" ON transcript_speaker.speaker_number = meta.label");
    q.append(" AND transcript_speaker.ag_id = meta.ag_id");
    q.append(" INNER JOIN speaker");
    q.append(" ON speaker.speaker_number = meta.label");
    q.append(" INNER JOIN transcript");
    q.append(" ON transcript.ag_id = meta.ag_id");
    q.append(" LEFT OUTER JOIN transcript_family");
    q.append(" ON transcript_family.family_id = transcript.family_id");
    q.append(" WHERE meta.label IN (").append(speakerNumberList).append(")");
    q.append(transcriptClause);
    q.append(mainSpeakerClause);
    q.append(" ORDER BY transcript.family_id, transcript.family_sequence, meta_start.offset");
    
    // Create temporary table so that multiple users can query at once without locking each other
    PreparedStatement sql = connection.prepareStatement(
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
    sql.executeUpdate();
    sql.close();
    
    setStatus(q.toString());
    sql = connection.prepareStatement(q.toString());
    sql.setLong(1, ((SqlSearchResults)results).getId());      
        
    iPercentComplete = SQL_STARTED_PERCENT; 
    setStatus("Identifying utterances...");        
    if (!bCancelling) executeUpdate(sql); 
    sql.close();
    iPercentComplete = SQL_FINISHED_PERCENT;

    // copy the results back into the global table
    sql = connection.prepareStatement(
      "INSERT INTO result"
      +" (search_id,ag_id,speaker_number,"
      +" start_anchor_id,end_anchor_id,defining_annotation_id,"
      +" segment_annotation_id,target_annotation_id,first_matched_word_annotation_id,"
      +" last_matched_word_annotation_id,complete,target_annotation_uid)"
      +" SELECT search_id,ag_id,speaker_number,"
      +" start_anchor_id,end_anchor_id,defining_annotation_id,"
      +" segment_annotation_id,target_annotation_id,first_matched_word_annotation_id,"
      +" last_matched_word_annotation_id,0,target_annotation_uid"
      +" FROM _result unsorted"
      +" ORDER BY unsorted.match_id");
    if (!bCancelling) executeUpdate(sql);
    sql.close();

    // delete the temporary results
    sql = connection.prepareStatement("DROP TABLE _result");
    executeUpdate(sql);
    sql.close();    

    setStatus("Identifying bounding words...");
    PreparedStatement sqlResult = connection.prepareStatement(
      "SELECT COUNT(*) FROM result WHERE search_id = ?");
    sqlResult.setLong(1, ((SqlSearchResults)results).getId());      
    ResultSet rsResult = sqlResult.executeQuery();
    rsResult.next();
    int resultCount = rsResult.getInt(1);
    rsResult.close();
    sqlResult.close();
    sqlResult = connection.prepareStatement(
      "SELECT * FROM result WHERE search_id = ?",
      ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE);
    sqlResult.setLong(1, ((SqlSearchResults)results).getId());      
    String selectUtteranceWords = "SELECT word.word_annotation_id"
      +" FROM result"
      +" INNER JOIN annotation_layer_12 utterance"
      +" ON result.defining_annotation_id = utterance.annotation_id" 
      +" INNER JOIN anchor utterance_start"
      +" ON utterance.start_anchor_id = utterance_start.anchor_id" 
      +" INNER JOIN anchor utterance_end"
      +" ON utterance.end_anchor_id = utterance_end.anchor_id" 
      // use orthography layer to exclude utterances with not orthography
      +" INNER JOIN annotation_layer_2 word" 
      +" ON utterance.turn_annotation_id = word.turn_annotation_id" 
      +" INNER JOIN anchor word_start"
      +" ON word.start_anchor_id = word_start.anchor_id"
      +" AND word_start.offset >= utterance_start.offset"
      +" AND word_start.offset < utterance_end.offset"
      +" WHERE search_id = ? AND utterance.annotation_id = ?";
    PreparedStatement sqlFirstWord = connection.prepareStatement(
      selectUtteranceWords+" ORDER BY word_start.offset ASC LIMIT 1");
    PreparedStatement sqlLastWord = connection.prepareStatement(
      selectUtteranceWords+" ORDER BY word_start.offset DESC LIMIT 1");
    rsResult = sqlResult.executeQuery();
    int r = 0;
    try {
      while (rsResult.next() && !bCancelling) {
        sqlFirstWord.setLong(1, ((SqlSearchResults)results).getId());      
        sqlFirstWord.setLong(2, rsResult.getLong("defining_annotation_id"));
        ResultSet rsWord = sqlFirstWord.executeQuery();
        if (rsWord.next()) { // there is a first word
          rsResult.updateLong("target_annotation_id", rsWord.getLong(1));
          rsResult.updateLong("first_matched_word_annotation_id", rsWord.getLong(1));
          rsResult.updateString("target_annotation_uid", "ew_0_"+rsWord.getLong(1));
          rsWord.close();
          sqlLastWord.setLong(1, ((SqlSearchResults)results).getId());      
          sqlLastWord.setLong(2, rsResult.getLong("defining_annotation_id"));
          rsWord = sqlLastWord.executeQuery();
          if (rsWord.next()) {
            rsResult.updateLong("last_matched_word_annotation_id", rsWord.getLong(1));
          }
          rsResult.updateInt("complete", 1);
          rsResult.updateRow();
        } else { // no first word
          // remove this utterance, it has no real words
          rsResult.deleteRow();
        }
        rsWord.close();
        
        r++;
        iPercentComplete = SQL_FINISHED_PERCENT + (r*(100-SQL_FINISHED_PERCENT))/resultCount;
      }
    } finally {
      rsResult.close();
      sqlResult.close();
      sqlFirstWord.close();
      sqlLastWord.close();
    }

    iPercentComplete = 95;
    
    results.reset();
    results.hasNext(); // force it to recheck the database to get size etc.

    int size = results.size();
    setStatus("There " + (size==1?"was ":"were ") + size + (size==1?" utterance":" utterances")
              + " identified in " + (((getDuration()/1000)+30)/60)
              + " minutes [" + getDuration() + "ms]");
    
    iPercentComplete = 100;
    
  }

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
  
  /** Percent progress indicating that the main SQL query has started */
  static final int SQL_STARTED_PERCENT = 10;
  /** Percent progress indicating that the main SQL query has finished */
  static final int SQL_FINISHED_PERCENT = 50;

}
