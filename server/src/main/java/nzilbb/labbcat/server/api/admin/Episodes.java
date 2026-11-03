//
// Copyright 2026 New Zealand Institute of Language, Brain and Behaviour, 
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
package nzilbb.labbcat.server.api.admin;

import java.io.File;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Vector;
import java.util.function.Consumer;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;
import nzilbb.ag.PermissionException;
import nzilbb.ag.StoreException;
import nzilbb.labbcat.server.api.APIRequestHandler;
import nzilbb.labbcat.server.api.RequiredRole;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.util.IO;

/**
 * <tt>/api/admin/episodes/<var>corpus</var></tt>
 * : Administration of corpora/names of transcript episodes.
 *  <p> The following operations, specified by the HTTP method, are supported:
 *   <dl>
 *    <dt> GET </dt><dd> Get a list of episodes in the given corpus. 
 *     <ul>
 *      <li><em> Response Body </em> - A JSON-encoded array of objects with the following
 *       attributes:
 *   <dl>
 *    <dt> episode_id </dt><dd> The database key for the record. </dd>
 *    <dt> name </dt><dd> The name of the episode. </dd>
 *    <dt> corpus </dt><dd> The name of the corpus. </dd>
 *    <dt> transcript_count </dt><dd> The number of transcript in the episode. </dd>
 *   </dl>
 *      </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The episodes were listed. </li>
 *         <li><em> 400 </em> : No corpus was specified. </li>
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *    <dt> PUT </dt><dd> Set the JavaScript validation script for the given attribute.
 *     <ul>
 *      <li><em> Request Body </em> - A JSON-encoded object with the following structure:
 *   <dl>
 *    <dt> episode_id </dt><dd> The database key for the episode to update. </dd>
 *    <dt> name </dt><dd> The (new) name of the episode. </dd>
 *    <dt> corpus </dt><dd> The name for for the (new) corpus. </dd>
 *   </dl>
 *      </li>
 *      <li><em> Response Body </em> - empty.
 *      </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The given episode was successfully updated. </li>
 *         <li><em> 400 </em> : The object was invalid, e.g. the corpus specified
 *          doesn't exist, or name is blank or clashes with another episode. </li>
 *         <li><em> 404 </em> : The given episode does not exist. </li>
 *         <li><em> 409 </em> : The request couldn't be completed. </li>
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *   </dl>
 *  </li>
 * @author Robert Fromont robert@fromont.net.nz
 */
@RequiredRole("admin")
public class Episodes extends APIRequestHandler {

  File dirFiles;
  
  /**
   * Constructor.
   * @param dirFiles The 'files' directory than contains all corpus subdirectories.
   */
  public Episodes(File dirFiles) {
    this.dirFiles = dirFiles;
  } // end of constructor
  
  /**
   * GET handler - list stimulus files.
   * @param pathInfo The URL path.
   * @param httpStatus Receives the response status code, in case of error.
   * @return A JSON object as the request response.
   */
  public JsonObject get(String pathInfo, Consumer<Integer> httpStatus) {
    try {
      SqlGraphStoreAdministration store = getStore();
      try {
        if (!hasAccess(store.getConnection())) { 
          httpStatus.accept(SC_FORBIDDEN);
          return null;
        }
        if (pathInfo == null || pathInfo.length() <= 1) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No corpus name was provided.");
        }
        String corpus = pathInfo.replaceAll("^/",""); // strip leading slash
        
        Connection connection = store.getConnection();
        try (PreparedStatement sql = connection.prepareStatement(
               "SELECT s.family_id, s.name, COUNT(*) AS transcript_count"
               +" FROM transcript t"
               +" INNER JOIN transcript_family s ON t.family_id = s.family_id"
               +" WHERE t.corpus_name = ? AND s.name <> ''"
               + "GROUP BY s.family_id, s.name"
               +" ORDER BY s.name")) {
          sql.setString(1, corpus);
          try (ResultSet rs = sql.executeQuery()) {
            JsonArrayBuilder episodes = Json.createArrayBuilder();
            while (rs.next()) {
              episodes.add(Json.createObjectBuilder()
                           .add("episode_id", rs.getInt("family_id"))
                           .add("name", rs.getString("name"))
                           .add("corpus", corpus)
                           .add("transcript_count", rs.getInt("transcript_count")));
            }
            return successResult(episodes.build(), null);
          } // close rs
        } // close sql
      } finally {
        cacheStore(store);
      }
    } catch (PermissionException x) {
      httpStatus.accept(SC_FORBIDDEN);
      return failureResult(x);
    } catch (SQLException x) {
      context.servletLog(pathInfo + " " + x);
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      return failureResult(x);
    } catch (Exception x) {
      context.servletLog(pathInfo + " " + x);
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      return failureResult(x);
    }
  }
  
  /**
   * PUT handler - edit an episode.
   * @param requestBody Stream supplying the body of the request.
   * @param httpStatus Receives the response status code, in case of error.
   * @return A JSON object as the request response.
   */
  @SuppressWarnings("rawtypes")
  public JsonObject put(InputStream requestBody, Consumer<Integer> httpStatus) {
    try {
      SqlGraphStoreAdministration store = getStore();
      try {
        if (!hasAccess(store.getConnection())) { 
          httpStatus.accept(SC_FORBIDDEN);
        }
        // read the incoming object
        JsonReader reader = Json.createReader( // ensure we read as UTF-8
          new InputStreamReader(requestBody, "UTF-8"));
        JsonObject json = reader.readObject();
        if (!json.containsKey("episode_id")) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No episode ID was provided."); // TODO i18n
        }
        int episode_id = json.getInt("episode_id");
        if (!json.containsKey("name")) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No episode name was provided."); // TODO i18n
        }
        String newName = json.getString("name");
        if (newName.trim().length() == 0) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No episode name was provided."); // TODO i18n
        }
        if (!json.containsKey("corpus")) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No corpus name was provided.");
        }
        String newCorpus = json.getString("corpus");
        Connection connection = store.getConnection();
        try (PreparedStatement sql = connection.prepareStatement(
               "SELECT MIN(s.name) AS name, MIN(t.corpus_name) AS corpus"
               +" FROM transcript t"
               +" INNER JOIN transcript_family s ON t.family_id = s.family_id"
               +" WHERE s.family_id = ?")) {
          sql.setInt(1, episode_id);
          try (ResultSet rs = sql.executeQuery()) {
            if (!rs.next()) {
              httpStatus.accept(SC_NOT_FOUND);
              return failureResult("Invalid ID: {0}", episode_id);
            }
            Vector<String> messages = new Vector<String>();
            String oldName = rs.getString("name");
            String oldCorpus = rs.getString("corpus");
            if (!newName.equals(oldName)) { // changing name
              String error = renameEpisode(
                connection, episode_id, oldCorpus, oldName, newName);
              if (error == null) { // success
                messages.add(localize("Episode {0}  renamed to {1} ", oldName, newName));
                oldName = newName; // in case we're changing corpus too
              } else {
                httpStatus.accept(SC_CONFLICT);
                return failureResult(error);
              }
            }
            if (!newCorpus.equals(oldCorpus)) { // changing name
              String error = moveEpisode(
                connection, episode_id, oldName, oldCorpus, newCorpus);
              if (error == null) { // success
                messages.add(localize("Episode {0} moved to {1} ", oldName, newCorpus));
                oldName = newName; // in case we're changing corpus too
              } else {
                httpStatus.accept(SC_CONFLICT);
                if (messages.size() > 0) { // partial success
                  return failureResult(messages, error);
                } else {
                  return failureResult(error);
                }
              }
            }
            if (messages.size() == 0) {
              messages.add(localize("No changes to save: {0}", oldName));
            }
            try (PreparedStatement sqlEpisode = connection.prepareStatement(
                   "SELECT s.family_id, s.name, t.corpus_name, COUNT(*) AS transcript_count"
                   +" FROM transcript t"
                   +" INNER JOIN transcript_family s ON t.family_id = s.family_id"
                   +" WHERE s.family_id = ? AND s.name <> ''"
                   + "GROUP BY s.family_id, s.name"
                   +" ORDER BY s.name")) {
              sqlEpisode.setInt(1, episode_id);
              try (ResultSet rsEpisode = sqlEpisode.executeQuery()) {
                if (rsEpisode.next()) {
                  JsonObjectBuilder episode = Json.createObjectBuilder()
                    .add("episode_id", rsEpisode.getInt("family_id"))
                    .add("name", rsEpisode.getString("name"))
                    .add("corpus", rsEpisode.getString("corpus_name"))
                    .add("transcript_count", rsEpisode.getInt("transcript_count"));
                  return successResult(episode.build(), messages);
                } else {
                  return failureResult(messages, localize("Unexpected error."));
                }
              } // close rsEpisode
            } // close sqlEpisode
          } // close rs
        } // close sql
      } finally {
        cacheStore(store);
      }
    } catch (PermissionException x) {
      httpStatus.accept(SC_FORBIDDEN);
      return failureResult(x);
    } catch (SQLException x) {
      context.servletLog(x);
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      return failureResult(x);
    } catch (Exception x) {
      context.servletLog(x);
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      return failureResult(x);
    }
  }
  
  /**
   * Renames the given episode.
   * @param connection Database connection.
   * @param episode_id The database ID of the episode.
   * @param corpus The corpus the episode is in.
   * @param oldName The source name of the episode.
   * @param newName The destination name of the episode.
   * @return An error message, if any occured.
   */
  private String renameEpisode(
    Connection connection, int episode_id, String corpus, String oldName, String newName)
    throws SQLException {
          
    // rename the directory
    File corpusDir = new File(dirFiles, corpus);
    File oldEpisodeDir = new File(corpusDir, oldName);
    if (!oldEpisodeDir.exists()) {
      return localize("Source folder doesn't exist: {0}", oldEpisodeDir.getName());
    }
    File newEpisodeDir = new File(corpusDir, newName);
    if (newEpisodeDir.exists()) {
      return localize("Destination folder already exists: {0}", newEpisodeDir.getName());
    }
    
    if (!oldEpisodeDir.renameTo(newEpisodeDir)) {
      return localize("Could not move {0}: directory renaming failed.", oldName);
    }
      
    // update series name
    try (PreparedStatement sql = connection.prepareStatement(
           "UPDATE transcript_family SET name = ? WHERE family_id = ?")) {
      sql.setString(1, newName);
      sql.setInt(2, episode_id);
      sql.executeUpdate();
    } // close sql

    return null;
  } // end of renameEpisode()  
  
  /**
   * Moves the episode to a new corpus.
   * @param connection The database connection.
   * @param episode_id The episode's database ID.
   * @param name The name of the episode.
   * @param oldCorpus The source corpus name.
   * @param newCorpus The destination corpus name.
   * @return Error message, if any.
   */
  private String moveEpisode(
    Connection connection, int episode_id, String name, String oldCorpus, String newCorpus)
    throws SQLException {

    int old_corpus_id = -1;
    int new_corpus_id = -1;
    try (PreparedStatement sql = connection.prepareStatement(
           "SELECT corpus_id FROM corpus WHERE corpus_name = ?")) {
      sql.setString(1, oldCorpus);
      try (ResultSet rs = sql.executeQuery()) {
        if (!rs.next()) return localize("Invalid ID: {0}", oldCorpus);
        old_corpus_id = rs.getInt("corpus_id");
      } // close rs
      sql.setString(1, newCorpus);
      try (ResultSet rs = sql.executeQuery()) {
        if (!rs.next()) return localize("Invalid ID: {0}", newCorpus);
        new_corpus_id = rs.getInt("corpus_id");
      } // close rs
    } // close sql
    
    // move the directory from the old corpus to the new corpus
    File newCorpusDir = new File(dirFiles, newCorpus);
    newCorpusDir.mkdir(); // make sure it exists
    File newEpisodeDir = new File(newCorpusDir, name);
    if (newEpisodeDir.exists()) {
      return localize("Destination folder {0}/{1} already exists.", newCorpus, name);
    }
    File oldCorpusDir = new File(dirFiles, oldCorpus);
    File oldEpisodeDir = new File(oldCorpusDir, name);
    if (!oldEpisodeDir.exists()) {
      return localize("Source folder {0}/{1} doesn't exist.", oldCorpus + "/" + name);
    }
    
    if (!oldEpisodeDir.renameTo(newEpisodeDir)) {
      return localize("Could not move {0}: directory renaming failed.", name);
    }
    
    // update transcripts
    try (PreparedStatement sql = connection.prepareStatement(
           "UPDATE transcript SET corpus_name = ? WHERE family_id = ?")) {
      sql.setString(1, newCorpus);
      sql.setInt(2, episode_id);
      sql.executeUpdate();
    } // close sql
    
    // update participants
    try (PreparedStatement sql = connection.prepareStatement(
           "UPDATE speaker_corpus"
           + " INNER JOIN transcript_speaker"
           + " ON speaker_corpus.speaker_number = transcript_speaker.speaker_number"
           + " INNER JOIN transcript"
           + " ON transcript_speaker.ag_id = transcript.ag_id"
           + " LEFT OUTER JOIN speaker_corpus sc" // check for duplicates
           + " ON speaker_corpus.speaker_number = sc.speaker_number AND sc.corpus_id = ?"
           + " SET speaker_corpus.corpus_id = ?"
           + " WHERE transcript.family_id = ?"
           + " AND speaker_corpus.corpus_id = ?"
           + " AND sc.speaker_number IS NULL")) { // not already there
      sql.setInt(1, new_corpus_id);
      sql.setInt(2, new_corpus_id);
      sql.setInt(3, episode_id);
      sql.setInt(4, old_corpus_id);
      try {
        // this could fail if participants are already in the new corpus...
        sql.executeUpdate();
      } catch(SQLException x) {
        // ...in which case we don't fail, as they've benn manually put where they should be
      }
    } // close SQL
    return null;
   } // end of moveEpisode()

} // end of class Episodes
