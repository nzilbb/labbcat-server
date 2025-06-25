//
// Copyright 2015-2025 New Zealand Institute of Language, Brain and Behaviour, 
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.sql.*;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.Vector;
import java.util.List;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import nzilbb.ag.*;
import nzilbb.ag.automation.Annotator;
import nzilbb.ag.automation.Transcriber;
import nzilbb.ag.automation.UsesFileSystem;
import nzilbb.ag.automation.UsesGraphStore;
import nzilbb.ag.automation.UsesRelationalDatabase;
import nzilbb.ag.automation.util.AnnotatorDescriptor;
import nzilbb.ag.ql.AGQLException;
import nzilbb.ag.ql.QL;
import nzilbb.ag.serialize.*;
import nzilbb.ag.serialize.util.IconHelper;
import nzilbb.ag.util.AnnotationsByAnchor;
import nzilbb.ag.util.LayerHierarchyTraversal;
import nzilbb.ag.util.Normalizer;
import nzilbb.ag.util.Validator;
import nzilbb.configure.Parameter;
import nzilbb.configure.ParameterSet;
import nzilbb.encoding.ValidLabelsDefinitions;
import nzilbb.media.MediaCensor;
import nzilbb.media.MediaConverter;
import nzilbb.media.MediaException;
import nzilbb.media.MediaThread;
import nzilbb.media.ffmpeg.FfmpegCensor;
import nzilbb.media.ffmpeg.FfmpegConverter;
import nzilbb.media.wav.FragmentExtractor;
import nzilbb.media.wav.Resampler;
import nzilbb.sql.ConnectionFactory;
import nzilbb.sql.mysql.MySQLConnectionFactory;
import nzilbb.util.IO;
import nzilbb.util.ISO639;
import nzilbb.util.MonitorableSeries;
import nzilbb.util.SemanticVersionComparator;
import nzilbb.util.Timers;

/**
 * Graph store that uses a relational database as its back end.
 * @author Robert Fromont robert@fromont.net.nz
 */

public class SqlGraphStore implements GraphStore {
  // Attributes:

  /** Format of annotation IDs, where {0} = scope, {1} = layer_id, and {2} = annotation_id */
  protected MessageFormat fmtAnnotationId = new MessageFormat("e{0}_{1,number,0}_{2,number,0}");

  /** Format of annotation IDs for 'meta' layers, where {0} = layer_id and {1} = the id
   * of the entity (corpus_id, family_id, speaker_number, etc.)  */
  protected MessageFormat fmtMetaAnnotationId = new MessageFormat("m_{0,number,0}_{1}");

  /** Format of annotation IDs for transcript attributes, where {0} = attribute and {1} =
   * annotation_id */
  protected MessageFormat fmtTranscriptAttributeId = new MessageFormat("t|{0}|{1,number,0}");

  /** Format of annotation IDs for participant attributes, where {0} = attribute and {1}
   * = annotation_id */
  protected MessageFormat fmtParticipantAttributeId = new MessageFormat("p|{0}|{1,number,0}");

  /** Format of anchor IDs, where {0} = anchor_id */
  protected MessageFormat fmtAnchorId = new MessageFormat("n_{0,number,0}");
   
  // Timers timers = new Timers();
  
  /**
   * URL prefix for file access.
   * @see #getBaseUrl()
   * @see #setBaseUrl(String)
   */
  protected String baseUrl;
  /**
   * Getter for {@link #baseUrl}: URL prefix for file access.
   * @return URL prefix for file access.
   */
  public String getBaseUrl() { return baseUrl; }
  /**
   * Setter for {@link #baseUrl}: URL prefix for file access.
   * @param newBaseUrl URL prefix for file access.
   */
  public SqlGraphStore setBaseUrl(String newBaseUrl) { baseUrl = newBaseUrl; return this; }

  /**
   * Root directory for file structure.
   * @see #getFiles()
   * @see #setFiles(File)
   */
  protected File files;
  /**
   * Getter for {@link #files}: Root directory for file structure.
   * @return Root directory for file structure.
   */
  public File getFiles() { return files; }
  /**
   * Setter for {@link #files}: Root directory for file structure.
   * @param newFiles Root directory for file structure.
   */
  public SqlGraphStore setFiles(File newFiles) { files = newFiles; return this; }
   
  /**
   * Returns the location of the annotators directory.
   * @return The annotator installation directory.
   */
  public File getAnnotatorDir() {
    File dir = new File(getFiles(), "annotators");
    if (!dir.exists()) dir.mkdir();
    return dir;
  } // end of getAnnotatorDir()   
   
  /**
   * Database connection.
   * @see #getConnection()
   * @see #setConnection(Connection)
   */
  protected Connection connection;
  /**
   * Getter for {@link #connection}: Database connection.
   * @return Database connection.
   */
  public Connection getConnection() { return connection; }
  /**
   * Setter for {@link #connection}: Database connection.
   * @param newConnection Database connection.
   */
  public SqlGraphStore setConnection(Connection newConnection) {
    connection = newConnection; return this;
  }
   
  /**
   * Factory for generating connections to the database.
   * @see #getDb()
   * @see #setDb(ConnectionFactory)
   */
  protected ConnectionFactory db;
  /**
   * Getter for {@link #db}: Factory for generating connections to the database.
   * @return Factory for generating connections to the database.
   */
  public ConnectionFactory getDb() { return db; }
  /**
   * Setter for {@link #db}: Factory for generating connections to the database.
   * @param newDb Factory for generating connections to the database.
   */
  public SqlGraphStore setDb(ConnectionFactory newDb) throws SQLException {
    db = newDb;
    if (db != null) {
      connection = db.newConnection();
    }
    return this;
  }
   
  /**
   * Whether transcript-access permissions are specified (i.e. there are rows in
   * role_permission). 
   * @see #getPermissionsSpecified()
   * @see #setPermissionsSpecified(Boolean)
   */
  protected Boolean permissionsSpecified;
  /**
   * Getter for {@link #permissionsSpecified}: Whether transcript-access permissions are
   * specified (i.e. there are rows in role_permission). 
   * @return Whether transcript-access permissions are specified (i.e. there are rows in
   * role_permission). 
   */
  public Boolean getPermissionsSpecified() {
    if (permissionsSpecified == null && connection != null) {
      try {
        PreparedStatement sql = getConnection().prepareStatement(
          "SELECT COUNT(*) FROM role_permission");
        ResultSet rs = sql.executeQuery();
        try {
          rs.next();
          permissionsSpecified = rs.getInt(1) > 0;
        }
        finally
        {
          rs.close();
          sql.close();
        }
      }
      catch(Exception exception) {}
    }
    return permissionsSpecified;
  }

  /**
   * Whether to disconnect the connection when garbage collected.
   * @see #getDisconnectWhenFinished()
   * @see #setDisconnectWhenFinished(boolean)
   */
  protected boolean disconnectWhenFinished = false;
  /**
   * Getter for {@link #disconnectWhenFinished}: Whether to disconnect the connection
   * when garbage collected.
   * @return Whether to disconnect the connection when garbage collected.
   */
  public boolean getDisconnectWhenFinished() { return disconnectWhenFinished; }
  /**
   * Setter for {@link #disconnectWhenFinished}: Whether to disconnect the connection
   * when garbage collected.
   * @param newDisconnectWhenFinished Whether to disconnect the connection when garbage
   * collected. 
   */
  public SqlGraphStore setDisconnectWhenFinished(boolean newDisconnectWhenFinished) {
    disconnectWhenFinished = newDisconnectWhenFinished; return this;
  }
   
  /**
   * The store's ID.
   * @see #getId()
   * @see #setId(String)
   */
  protected String id;
  /**
   * GraphStore method and getter for {@link #id}: The store's ID.
   * @return The store's ID.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   */
  public String getId() throws StoreException, PermissionException { return id; }
  /**
   * Setter for {@link #id}: The store's ID.
   * @param newId The store's ID.
   */
  public SqlGraphStore setId(String newId) {
    id = newId;
    if (id != null && !id.endsWith("/")) id += "/";
    return this;
  }

  /**
   * ID of the user querying the store.
   * @see #getUser()
   * @see #setUser(String)
   */
  protected String user;
  /**
   * Getter for {@link #user}: ID of the user querying the store.
   * @return ID of the user querying the store.
   */
  public String getUser() { return user; }
  /**
   * Setter for {@link #user}: ID of the user querying the store.
   * @param newUser ID of the user querying the store.
   */
  public SqlGraphStore setUser(String newUser) {
    if (newUser != null && !newUser.equals(user)) { // user is changing, get their group membership
      userRoles.clear();
      try {
        PreparedStatement sqlUserGroups = getConnection().prepareStatement(
          "SELECT role_id FROM role WHERE user_id = ?");
        sqlUserGroups.setString(1, newUser);
        ResultSet rstUserGroups = sqlUserGroups.executeQuery();
        while (rstUserGroups.next()) {
          userRoles.add(rstUserGroups.getString("role_id"));
        } // next group
        rstUserGroups.close();
        sqlUserGroups.close();
      }
      catch(Exception exception) {}
    }
    user = newUser;
    return this;
  }

  /**
   * Roles the user fulfills.
   * @see #getUserRoles()
   */
  protected HashSet<String> userRoles = new HashSet<String>() {{
      // if there's no user auth, then the 'user' has all roles
      add("view");
      add("edit");
      add("admin");
    }};
  /**
   * Getter for {@link #userRoles}: Roles the user fulfills.
   * @return Roles the user fulfills.
   */
  public HashSet<String> getUserRoles() { return userRoles; }   
   
  // Methods:
   
  /**
   * Default constructor.
   */
  public SqlGraphStore() {
  } // end of constructor

  /**
   * Constructor with connection.
   * @param baseUrl URL prefix for file access.
   * @param connection An opened database connection.
   * @param user ID of the user
   */
  @Deprecated
  public SqlGraphStore(String baseUrl, Connection connection, String user) throws SQLException {
    setId(baseUrl);
    setBaseUrl(baseUrl);
    setConnection(connection);
    setFiles(new File(getSystemAttribute("transcriptPath")));
    setUser(user);
    loadSerializers();
  } // end of constructor

  /**
   * Constructor with connection.
   * @param baseUrl URL prefix for file access.
   * @param files Root directory for file structure.
   * @param connection An opened database connection.
   * @param user ID of the user
   */
  @Deprecated
  public SqlGraphStore(String baseUrl, File files, Connection connection, String user)
    throws SQLException {
    setId(baseUrl);
    setBaseUrl(baseUrl);
    setFiles(files);
    setConnection(connection);
    setUser(user);
    loadSerializers();
  } // end of constructor

  /**
   * Constructor with connection.
   * @param baseUrl URL prefix for file access.
   * @param db A database connection factory.
   * @param user ID of the user
   */
  public SqlGraphStore(String baseUrl, ConnectionFactory db, String user) throws SQLException {
    setId(baseUrl);
    setBaseUrl(baseUrl);
    setDb(db);
    setFiles(new File(getSystemAttribute("transcriptPath")));
    setUser(user);
    loadSerializers();
  } // end of constructor

  /**
   * Constructor with connection.
   * @param baseUrl URL prefix for file access.
   * @param files Root directory for file structure.
   * @param db A database connection factory.
   * @param user ID of the user
   */
  public SqlGraphStore(String baseUrl, File files, ConnectionFactory db, String user)
    throws SQLException {
    setId(baseUrl);
    setBaseUrl(baseUrl);
    setFiles(files);
    setDb(db);
    setUser(user);
    loadSerializers();
  } // end of constructor

  /**
   * Constructor with connection parameters.
   * @param baseUrl URL prefix for file access.
   * @param files Root directory for file structure.
   * @param connectString The database connection string.
   * @param databaseUser The database username.
   * @param password The databa password.
   * @param storeUser ID of the user
   * @throws SQLException If an error occurs during connection.
   */
  public SqlGraphStore(
    String baseUrl, File files, String connectString, String databaseUser, String password,
    String storeUser)
    throws SQLException {
    setId(baseUrl);
    setBaseUrl(baseUrl);
    setFiles(files);
    setDb(new MySQLConnectionFactory(connectString, user, password));
    setUser(storeUser);
    loadSerializers();
  } // end of constructor

  /**
   * Called when the object is garbage-collected.
   */
  @SuppressWarnings("deprecation")
  public void finalize() {
    if (getDisconnectWhenFinished() && getConnection() != null) {
      try { getConnection().close(); } catch(Throwable t) {}
    }
  } // end of finalize()

  
  /**
   * Checks the user has the 'edit' role, and throws PermissionException if not.
   * @throws PermissionException If {@link SqlGraphStore#getUserRoles()} doesn't contain "edit".
   */
  private void requireEdit() throws PermissionException {
    if (!getUserRoles().contains("edit")) throw new PermissionException("Use does not have edit role"); // TODO i18n
  } // end of requireEdit()

  // GraphStore methods

  /**
   * Gets a list of layer IDs (annotation 'types').
   * @return A list of layer IDs.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   */
  public String[] getLayerIds() throws StoreException, PermissionException {
    try {
      // temporal layers
      PreparedStatement sql = getConnection().prepareStatement(
        "SELECT short_description FROM layer"
        +" LEFT OUTER JOIN attribute_category ON layer.category = attribute_category.category"
        +" ORDER BY COALESCE(attribute_category.display_order, 100),"
        + " IF (layer_id <= 2, POWER(layer_id-1,2), 100) DESC," // orthography/word/segment last
        +" short_description");
      ResultSet rs = sql.executeQuery();
      Vector<String> layerIds = new Vector<String>();
      layerIds.add("main_participant"); // transcript_speaker.main_speaker
      while (rs.next()) {
        layerIds.add(rs.getString("short_description"));
      } // next layer
      rs.close();
      sql.close();

      // graph layers
      sql = getConnection().prepareStatement(
        "SELECT attribute, attribute_definition.category"
        +" FROM attribute_definition"
        +" LEFT OUTER JOIN attribute_category"
        +" ON attribute_definition.class_id = attribute_category.class_id"
        +" AND attribute_definition.category = attribute_category.category"
        +" WHERE attribute_definition.class_id = 'transcript'"
        +(getUserRoles().contains("edit")?"":" AND access = '1'")
        +" ORDER BY attribute_category.display_order, attribute_definition.display_order, attribute");
      rs = sql.executeQuery();
      // in order to get category ordering right, "transcript_type" is added as the first
      // "General" category attribute layer.
      boolean haveAddedTranscriptType = false; 
      while (rs.next()) {
        if (!haveAddedTranscriptType && "General".equals(rs.getString("category"))) {
          layerIds.add("transcript_type");
          haveAddedTranscriptType = true;
        }
        layerIds.add("transcript_"+rs.getString("attribute"));
      } // next layer
      if (!haveAddedTranscriptType) { // never encountered the "General" category
        layerIds.add("transcript_type");
      }
      rs.close();
      sql.close();

      // participant layers
      sql = getConnection().prepareStatement(
        "SELECT attribute"
        +" FROM attribute_definition"
        +" LEFT OUTER JOIN attribute_category"
        +" ON attribute_definition.class_id = attribute_category.class_id"
        +" AND attribute_definition.category = attribute_category.category"
        +" WHERE attribute_definition.class_id = 'speaker'"
        +" ORDER BY attribute_category.display_order, attribute_definition.display_order, attribute");
      rs = sql.executeQuery();
      while (rs.next()) {
        layerIds.add("participant_"+rs.getString("attribute"));
      } // next layer
      rs.close();
      sql.close();

      return layerIds.toArray(new String[0]);
    } catch(SQLException exception) {
      System.err.println("SQL error: " + exception);
      exception.printStackTrace(System.err);
      throw new StoreException("Invalid query."); // TODO i18n
    }
  }
   
  /**
   * Gets a list of layer definitions.
   * @return A list of layer definitions.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   */
  public Layer[] getLayers() throws StoreException, PermissionException {
    LinkedHashMap<String,Layer> layerLookup = new LinkedHashMap<String,Layer>();
    try {
      PreparedStatement sql = getConnection().prepareStatement(
        "SELECT layer.*, parent_layer.short_description AS parent_name"
        +" FROM layer"
        +" LEFT OUTER JOIN attribute_category ON layer.category = attribute_category.category"
        +" LEFT OUTER JOIN layer parent_layer ON layer.parent_id = parent_layer.layer_id"
        +" ORDER BY COALESCE(attribute_category.display_order, 100),"
        // orthography/word/segment last
        +" IF (layer.layer_id <= 2, POWER(layer.layer_id-1,2), 100) DESC," 
        +" short_description,"
        +" layer.layer_id");
      ResultSet rs = sql.executeQuery();
      try {
        while (rs.next()) {
          Layer layer = getTemporalLayer(rs);
          layerLookup.put(layer.getId(), layer);
        } // next temporal layer
      } finally {
        rs.close();
        sql.close();               
      }
         
      // transcript attributes
      layerLookup.put("transcript_type", getLayer("transcript_type"));
      sql = getConnection().prepareStatement(
        "SELECT attribute_definition.* FROM attribute_definition"
        +" LEFT OUTER JOIN attribute_category"
        +" ON attribute_definition.class_id = attribute_category.class_id"
        +" AND attribute_definition.category = attribute_category.category"
        +" WHERE attribute_definition.class_id = 'transcript'"
        +(getUserRoles().contains("edit")?"":" AND access = '1'")
        +" ORDER BY attribute_category.display_order, attribute_definition.display_order");
      rs = sql.executeQuery();
      try {
        while (rs.next()) {
          Layer layer = getTranscriptAttributeLayer(rs);
          layerLookup.put(layer.getId(), layer);
        }
      } finally {
        rs.close();
        sql.close();               
      }
         
      // participant attributes
      sql = getConnection().prepareStatement(
        "SELECT attribute_definition.* FROM attribute_definition"
        +" LEFT OUTER JOIN attribute_category"
        +" ON attribute_definition.class_id = attribute_category.class_id"
        +" AND attribute_definition.category = attribute_category.category"
        +" WHERE attribute_definition.class_id = 'speaker'"
        +(getUserRoles().contains("edit")?"":" AND access = '1'")
        +" ORDER BY attribute_category.display_order, attribute_definition.display_order");
      rs = sql.executeQuery();
      try {
        while (rs.next()) {
          Layer layer = getParticipantAttributeLayer(rs);
          layerLookup.put(layer.getId(), layer);
        }
      } finally {
        rs.close();
        sql.close();               
      }
    } catch(SQLException exception) {
      System.err.println("SQL error: " + exception);
      exception.printStackTrace(System.err);
      throw new StoreException("Invalid query."); // TODO i18n
    }
   
    // set parents
    for (Layer layer : layerLookup.values()) {
      layer.setParent(layerLookup.get(layer.getParentId()));
    }
    return layerLookup.values().toArray(new Layer[0]);
  }

  protected Schema schema = null;

  /**
   * Gets the layer schema. For performance reasons, this implementation only retrieves/builds
   * the schema once, and always returns a clone of that original object.
   * @return A schema defining the layers and how they relate to each other.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   */
  public Schema getSchema() throws StoreException, PermissionException {
    if (schema == null) {
      schema = new Schema();
      for (Layer layer : getLayers()) {
        schema.addLayer(layer);
        if (Integer.valueOf(SqlConstants.LAYER_TURN).equals(layer.get("layer_id"))) {
          schema.setTurnLayerId(layer.getId());
        } else if (Integer.valueOf(SqlConstants.LAYER_UTTERANCE)
                   .equals(layer.get("layer_id"))) {
          schema.setUtteranceLayerId(layer.getId());
        } 
        else if (Integer.valueOf(SqlConstants.LAYER_TRANSCRIPTION)
                 .equals(layer.get("layer_id"))) {
          schema.setWordLayerId(layer.getId());
        } 
        else if (Integer.valueOf(SqlConstants.LAYER_SERIES).equals(layer.get("layer_id"))) {
          schema.setEpisodeLayerId(layer.getId());
        } 
        else if (Integer.valueOf(SqlConstants.LAYER_CORPUS).equals(layer.get("layer_id"))) {
          schema.setCorpusLayerId(layer.getId());
        }
      } // next layer
      schema.setParticipantLayerId("participant");
      
      // category ordering
      try {
        PreparedStatement sql = getConnection().prepareStatement(
          "SELECT CONCAT (CASE class_id"
          +" WHEN 'speaker' THEN 'participant_'"
          +" WHEN 'transcript' THEN 'transcript_'"
          +" ELSE '' END, category) as category,"
          +" COALESCE(description, category) AS description"
          +" FROM attribute_category"
          +" ORDER BY class_id, display_order");
        ResultSet rs = sql.executeQuery();
        try {
          schema.getCategories().clear();
          while (rs.next()) {
            schema.getCategories().put(rs.getString("category"), rs.getString("description"));
          } // next category
        } finally {
          rs.close();
          sql.close();
        }
      } catch(SQLException exception) {
        System.err.println("SqlGraphStore.getSchema - can't get category order: " + exception);
      }
    }
    return schema;
  }

  /**
   * Gets a layer definition for a temporal layer.
   * @param rs A row from the <tt>layer</tt> table.
   * @return The definition of the given layer.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   * @throws SQException If a database retrieval error occurs.
   */
  protected Layer getTemporalLayer(ResultSet rs)
    throws StoreException, PermissionException, SQLException {
    Layer layer = new Layer();
    layer.setId(rs.getString("short_description"));
    layer.setDescription(rs.getString("notes"));
    layer.setAlignment(rs.getInt("alignment"));
    layer.setParentId(rs.getString("parent_name"));
    layer.setParentIncludes(rs.getInt("parent_includes") == 1);
    layer.setPeers(rs.getInt("peers") == 1);
    layer.setPeersOverlap(rs.getInt("peers_overlap") == 1);
    layer.setSaturated(rs.getInt("saturated") == 1);
      
    if (rs.getString("type").equals("N")
        || rs.getString("type").equals("number")
        || rs.getString("type").equals("integer")) {
      layer.setType(Constants.TYPE_NUMBER);
    } else if (rs.getString("type").equals("D")) {
      layer.setType(Constants.TYPE_IPA);
    } else if (rs.getString("type").equals("boolean")) {
      layer.setType(Constants.TYPE_BOOLEAN);
    } else if (rs.getString("type").equals("X")) {
      layer.setType(Constants.TYPE_TREE);
    } else if (rs.getString("type").indexOf('/') > 0) { // a MIME type for binary annotations
      // save as is
      layer.setType(rs.getString("type"));
    } else {
      layer.setType(Constants.TYPE_STRING);
    }
    if (rs.getString("category") != null) {
      layer.setCategory(rs.getString("category"));
    }
      
    // other attributes
    layer.put("layer_id", Integer.valueOf(rs.getInt("layer_id")));
    layer.put("subtype", rs.getString("type"));
    layer.put("layer_manager_id", rs.getString("layer_manager_id"));
    layer.put("extra", rs.getString("extra"));
    layer.put("scope", rs.getString("scope"));
    layer.put("enabled", rs.getString("enabled"));
    layer.put("notes", rs.getString("notes"));
    layer.put("data_mime_type", rs.getString("data_mime_type"));
    layer.put("style", rs.getString("style"));
      
    if (layer.getId().equals("corpus")) {
      LinkedHashMap<String,String> corpora = new LinkedHashMap<String,String>();
      for (String corpus : getCorpusIds()) {
        corpora.put(corpus, corpus);
      }
      layer.setValidLabels(corpora);
    } else { // not corpus layer
      // check for validLabels in label_option
      List<Map<String,Object>> validLabelsDefinition
        = new Vector<Map<String,Object>>();
      PreparedStatement sql = getConnection().prepareStatement(
        "SELECT * FROM label_option WHERE layer_id = ? ORDER BY display_order");
      sql.setInt(1, Integer.valueOf(rs.getInt("layer_id")));
      ResultSet rsOption = sql.executeQuery();
      while(rsOption.next()) {
        validLabelsDefinition.add(new HashMap<String,Object>() {{
          put("label", rsOption.getString("value"));
          put("display", rsOption.getString("display"));
          put("selector", rsOption.getString("selector"));
          put("description", rsOption.getString("description"));
          put("category", rsOption.getString("category"));
          put("subcategory", rsOption.getString("subcategory"));
          put("display_order", rsOption.getInt("display_order"));
        }});
      } // next validLabel

      if (layer.getId().equals("language") && validLabelsDefinition.size() == 0) {
        // add definitions for ISO639 2-letter codes
        ISO639 iso639 = new ISO639();
        for (String code : iso639.alpha2Codes()) {
          validLabelsDefinition.add(new HashMap<String,Object>() {{
            put("label", code);
            put("display", iso639.nameFromAlpha2(code).get());
          }});
        }
      }

      if (validLabelsDefinition.size() == 0 // no valid labels defined
            && rs.getString("type").equals("D")) { // but it's a Phonological layer
        // if ("segment".equals(layer.getId())) { // standard 'segment' layer
          // segments may have already been generated by a forced-aligner,
          // using DISC, ARPAbet, IPA, or other symbols.
          // we can tell which by peeking at the existing 'segment' layer labels
          
          // ARPAbet? if there are any labels that are 'DH' or 'HH'
          PreparedStatement sqlTest = getConnection().prepareStatement(
            "SELECT label FROM annotation_layer_1"
            +" WHERE CAST(label AS BINARY) = BINARY ? OR CAST(label AS BINARY) = BINARY ?"
            +" LIMIT 1");
          try {
            sqlTest.setString(1, "DH");
            sqlTest.setString(2, "HH");
            ResultSet rsTest = sqlTest.executeQuery();
            boolean arpabet = rsTest.next();
            rsTest.close();
            if (arpabet) {
              ValidLabelsDefinitions.AddARPAbetDefinitions(validLabelsDefinition);
              System.err.println("layer " + layer.getId() + " inferred labels: ARPAbet");
            } else { // not ARPAbet
              // IPA? if there are any labels that are 'ɪ' or 'ǝ'
              sqlTest.setString(1, "ɪ");
              sqlTest.setString(2, "ɔ");
              rsTest = sqlTest.executeQuery();
              boolean ipa = rsTest.next();
              rsTest.close();
              if (ipa) {
                // get a list of distinct labels, and construct a definition from it
                PreparedStatement sqlDistinctLabels = getConnection().prepareStatement(
                  "SELECT DISTINCT label FROM annotation_layer_1 ORDER BY label");
                rsTest = sqlDistinctLabels.executeQuery();
                Vector<String> distinctLabels = new Vector<String>();
                while (rsTest.next()) distinctLabels.add(rsTest.getString(1));
                rsTest.close();
                sqlDistinctLabels.close();
                if (distinctLabels.size() > 0) {
                  ValidLabelsDefinitions.AddIPADefinitions(validLabelsDefinition, distinctLabels);
                  System.err.println("layer " + layer.getId() + " inferred labels: IPA");
                }
              } else { // not IPA
                // DISC? if there are any labels that are '9' (CURE) or '$' (THOUGHT)
                sqlTest.setString(1, "9");
                sqlTest.setString(2, "$");
                rsTest = sqlTest.executeQuery();
                boolean disc = rsTest.next();
                rsTest.close();
                if (disc) { 
                  ValidLabelsDefinitions.AddDISCDefinitions(validLabelsDefinition);
                  System.err.println("layer " + layer.getId() + " inferred labels: DISC");
                } else { // not DISC
                  // MAUS's SAMPA? if there are any labels that are '@}' or 'Ae'
                  sqlTest.setString(1, "@}");
                  sqlTest.setString(2, "Ae");
                  rsTest = sqlTest.executeQuery();
                  boolean mausSampa = rsTest.next();
                  rsTest.close();
                  if (mausSampa) { 
                    ValidLabelsDefinitions.AddMausSAMPADefinitions(validLabelsDefinition);
                    System.err.println("layer " + layer.getId() + " inferred labels: MAUS SAMPA");
                  } else { // not MAUS SAMPA
                    // standard XSAMPA? if there are any labels that are 'r\' or 'l='
                    sqlTest.setString(1, "r\\");
                    sqlTest.setString(2, "l=");
                    rsTest = sqlTest.executeQuery();
                    boolean xSampa = rsTest.next();
                    rsTest.close();
                    if (xSampa) { 
                      ValidLabelsDefinitions.AddXSAMPADefinitions(validLabelsDefinition);
                      System.err.println("layer " + layer.getId() + " inferred labels: X-SAMPA");
                    } else { // not X-SAMPA
                      // standard SAMPA? if there are any labels that are 'i:' or 'I@'
                      sqlTest.setString(1, "O:");
                      sqlTest.setString(2, "I@");
                      rsTest = sqlTest.executeQuery();
                      boolean sampa = rsTest.next();
                      rsTest.close();
                      if (sampa) { 
                        ValidLabelsDefinitions.AddSAMPADefinitions(validLabelsDefinition);
                        System.err.println("layer " + layer.getId() + " inferred labels: SAMPA");
                      } // not SAMPA
                    } // not X-SAMPA
                  } // not MAUS SAMPA
                } // not DISC
              } // not IPA
            } // not ARPAbet
          } finally {
            sqlTest.close();
          }
          
          if (validLabelsDefinition.size() >= 0) { // labels were inferred
            PreparedStatement sqlInsert = getConnection().prepareStatement(
              "INSERT INTO label_option"
              +" (layer_id, value, display, selector, description, category, subcategory, display_order)"
              +" VALUES (?,?,?,?,?,?,?,?)");
            try {
              sqlInsert.setInt(1, (Integer)layer.get("layer_id"));
              for (Map<String,Object> option : validLabelsDefinition) {
                sqlInsert.setString(2, option.get("label").toString());
                sqlInsert.setString(
                  3, Optional.ofNullable(option.get("display")).orElse("").toString());
                sqlInsert.setString(
                  4, Optional.ofNullable(option.get("selector")).orElse("").toString());
                sqlInsert.setString(
                  5, Optional.ofNullable(option.get("description")).orElse("").toString());
                sqlInsert.setString(
                  6, Optional.ofNullable(option.get("category")).orElse("").toString());
                sqlInsert.setString(
                  7, Optional.ofNullable(option.get("subcategory")).orElse("").toString());
                sqlInsert.setInt(
                  8, Integer.parseInt(
                    Optional.ofNullable(option.get("display_order")).orElse("0").toString()));
                sqlInsert.executeUpdate();
              } // next valid label
            } catch (Exception x) {
              System.err.println("Could no save inferred valid labels: " + x);
              x.printStackTrace(System.err);
            } finally {
              sqlInsert.close();
            }
          } // labels were inferred
        // } // segment layer
        
        if (validLabelsDefinition.size() == 0) { // no inferred labels,
            // so just use DISC definition
            ValidLabelsDefinitions.AddDISCDefinitions(validLabelsDefinition);
        }
      } // new validLabelsDefinition, and a phonological layer
      
      // add the definitions to the layer
      // (validLabelsDefinition includes LaBB-CAT-specific hierarchy, order, and description)
      layer.put("validLabelsDefinition", validLabelsDefinition);
      
      if (validLabelsDefinition.size() > 0) { // valid labels are defined
        layer.setValidLabels(
          ValidLabelsDefinitions.ValidLabelsFromDefinition(validLabelsDefinition));
      }
      rsOption.close();
      sql.close();
    }
    return layer;
  }  

  /**
   * Gets a layer definition for a transcript attribute layer.
   * @param rs A row from the <tt>attribute_definition</tt> table.
   * @return The definition of the given layer.
   * @throws SQException If a database retrieval error occurs.
   */
  protected Layer getTranscriptAttributeLayer(ResultSet rs) throws SQLException {
    Layer layer = new Layer();
    layer.setId("transcript_" + rs.getString("attribute"));
    layer.setDescription(rs.getString("label"));
    layer.setAlignment(Constants.ALIGNMENT_NONE);
    layer.setParentId("transcript");
    layer.setParentIncludes(true);
    layer.setPeers(rs.getInt("peers") == 1
                   || rs.getString("style").matches(".*multiple.*"));
    layer.setPeersOverlap(true);
    layer.setSaturated(true);
      
    if (rs.getString("type").equals("N")
        || rs.getString("type").equals("number")
        || rs.getString("type").equals("integer")) {
      layer.setType(Constants.TYPE_NUMBER);
    } else if (rs.getString("type").equals("D")) {
      layer.setType(Constants.TYPE_IPA);
    } else if (rs.getString("type").equals("boolean")) {
      layer.setType(Constants.TYPE_BOOLEAN);
    } else {
      layer.setType(Constants.TYPE_STRING);
    }
    layer.setCategory("transcript_"+rs.getString("category"));
      
    // other attributes
    layer.put("class_id", rs.getString("class_id"));
    layer.put("attribute", rs.getString("attribute"));
    layer.put("subtype", rs.getString("type"));
    layer.put("style", rs.getString("style"));
    layer.put("hint", rs.getString("description"));
    layer.put("display_order", rs.getInt("display_order"));
    layer.put("searchable", rs.getString("searchable"));
    layer.put("access", rs.getString("access"));
      
    if (layer.get("subtype").equals("select")) {
      // possible values
      PreparedStatement sql = getConnection().prepareStatement(
        "SELECT value, description FROM attribute_option"
        +" WHERE class_id = 'transcript' AND attribute = ?"
        +" ORDER BY description");
      sql.setString(1, layer.get("attribute").toString());
      ResultSet rsValues = sql.executeQuery();
      try {
        if (rsValues.next()) {
          layer.setValidLabels(new LinkedHashMap<String,String>());
          do {
            layer.getValidLabels().put(
              rsValues.getString("value"), rsValues.getString("description"));
          } while (rsValues.next());
        }
      } finally {
        rsValues.close();
        sql.close();
      }
    }
      
    return layer;
  }
   
  /**
   * Gets a layer definition for a participant attribute layer.
   * @param rs A row from the <tt>attribute_definition</tt> table.
   * @return The definition of the given layer.
   * @throws SQException If a database retrieval error occurs.
   */
  protected Layer getParticipantAttributeLayer(ResultSet rs) throws SQLException {
    Layer layer = new Layer();
    layer.setId("participant_" + rs.getString("attribute"));
    layer.setDescription(rs.getString("label"));
    layer.setAlignment(Constants.ALIGNMENT_NONE);
    layer.setParentId("participant");
    layer.setParentIncludes(true);
    layer.setPeers(rs.getInt("peers") == 1
                   || rs.getString("style").matches(".*multiple.*"));
    layer.setPeersOverlap(false);
    layer.setSaturated(true);
      
    if (rs.getString("type").equals("N")
        || rs.getString("type").equals("number")
        || rs.getString("type").equals("integer")) {
      layer.setType(Constants.TYPE_NUMBER);
    } else if (rs.getString("type").equals("D")) {
      layer.setType(Constants.TYPE_IPA);
    } else if (rs.getString("type").equals("boolean")) {
      layer.setType(Constants.TYPE_BOOLEAN);
    } else {
      layer.setType(Constants.TYPE_STRING);
    }
    layer.setCategory("participant_"+rs.getString("category"));
      
    // other attributes
    layer.put("class_id", rs.getString("class_id"));
    layer.put("attribute", rs.getString("attribute"));
    layer.put("subtype", rs.getString("type"));
    layer.put("style", rs.getString("style"));
    layer.put("label", rs.getString("label"));
    layer.put("hint", rs.getString("description"));
    layer.put("display_order", rs.getInt("display_order"));
    layer.put("searchable", rs.getString("searchable"));
    layer.put("access", rs.getString("access"));
      
    if (layer.get("subtype").equals("select")) {
      // possible values
      PreparedStatement sql = getConnection().prepareStatement(
        "SELECT value, description FROM attribute_option"
        +" WHERE class_id = 'speaker' AND attribute = ?"
        +" ORDER BY description");
      sql.setString(1, layer.get("attribute").toString());
      ResultSet rsValues = sql.executeQuery();
      try {
        if (rsValues.next()) {
          layer.setValidLabels(new LinkedHashMap<String,String>());
          do {
            layer.getValidLabels().put(
              rsValues.getString("value"), rsValues.getString("description"));
          } while (rsValues.next());
        }
      } finally {
        rsValues.close();
        sql.close();
      }
    }
      
    return layer;
  }
   
  /**
   * Gets a layer definition.
   * @param id ID of the layer to get the definition for.
   * @return The definition of the given layer.
   * <p> LaBB-CAT extends the {@link Layer#validLabels} funcionality by supporting an
   * alternative layer attribute: <tt>validLabelsDefinition</tt>, which is an array of
   * label definitions, each definition being a map of string to string or integer. Each
   * label definition is expected to have the following attributes:
   * <dl>
   * <dt>label</dt> 
   *  <dd>what the underlying label is in LaBB-CAT (i.e. the DISC label, for a DISC layer)</dd> 
   * <dt>display</dt> 
   *  <dd>the symbol in the transcript, for the label (e.g. the IPA
   *      version of the label)</dd> 
   * <dt>selector</dt> 
   *  <dd>the symbol on the label helper, for the label (e.g. the IPA
   *      version of the label) - if there's no selector specified, then the value for display is
   *      used, and if there's no value for display specified, then there's no option
   *      on the label helper (so that type-able consonants like p, b, t, d etc. don't
   *      take up space on the label helper)</dd> 
   * <dt>description</dt> 
   *  <dd>tool-tip text that appears if you hover the mouse over the IPA symbol in the helper</dd>
   * <dt>category</dt> 
   *  <dd>the broad category of the symbol, for organizing the layout of the helper</dd>
   * <dt>subcategory</dt> 
   *  <dd>the narrower category of the symbol, for listing subgroups of symbols together</dd>
   * <dt>display_order</dt> 
   *  <dd>the order to process/list the labels in</dd>
   * </dl>
   * <p> <tt>validLabelsDefinition</tt> is returned if there is a hierarchical set of
   * options defined. Either way, <tt>validLabels</tt> (specifying the valid labels and
   * their 'display' values) is always returned.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   */
  public Layer getLayer(String id) throws StoreException, PermissionException {
    try {
      if (id.equals("transcript_type")) { // special case that's not (yet) in the database
        Layer layer = new Layer(id, "Transcript type")
          .setAlignment(Constants.ALIGNMENT_NONE)
          .setPeers(false)
          .setPeersOverlap(false)
          .setSaturated(true)
          .setParentId("transcript")
          .setParentIncludes(true);
        layer.setValidLabels(new LinkedHashMap<String,String>());
        PreparedStatement sql = getConnection().prepareStatement(
          "SELECT transcript_type FROM transcript_type ORDER BY type_id");
        ResultSet rs = sql.executeQuery();
        try {
          while (rs.next()) {
            layer.getValidLabels().put(
              rs.getString("transcript_type"), rs.getString("transcript_type"));
          }
        } finally {
          rs.close();
          sql.close();
        }
        return layer;
      } else if (id.equals("audio_prompt")) { // special case
        return new Layer(id, "Prompt for locally loaded media")
          .setAlignment(Constants.ALIGNMENT_NONE)
          .setPeers(false)
          .setPeersOverlap(false)
          .setSaturated(true)
          .setParentId("transcript")
          .setParentIncludes(true);
      } else if (id.equals("divergent")) { // special case
        return new Layer(id, "Whether words/utterances/turns has been edited since upload")
          .setAlignment(Constants.ALIGNMENT_NONE)
          .setPeers(false)
          .setPeersOverlap(false)
          .setSaturated(true)
          .setParentId("transcript")
          .setParentIncludes(true);
      } else if (id.equals("next-transcript")) { // special case
        return new Layer(id, "ID of the next transcript in the episode")
          .setAlignment(Constants.ALIGNMENT_NONE)
          .setPeers(false)
          .setPeersOverlap(false)
          .setSaturated(true)
          .setParentId("transcript")
          .setParentIncludes(true);
      } else if (id.equals("previous-transcript")) { // special case
        return new Layer(id, "ID of the previous transcript in the episode")
          .setAlignment(Constants.ALIGNMENT_NONE)
          .setPeers(false)
          .setPeersOverlap(false)
          .setSaturated(true)
          .setParentId("transcript")
          .setParentIncludes(true);
      }

      PreparedStatement sql = getConnection().prepareStatement(
        "SELECT layer.*, attribute_category.category, parent_layer.short_description AS parent_name"
        +" FROM layer"
        +" LEFT OUTER JOIN attribute_category ON layer.category = attribute_category.category"
        +" LEFT OUTER JOIN layer parent_layer ON layer.parent_id = parent_layer.layer_id"
        +" WHERE layer.short_description = ?");
      sql.setString(1, id);
      ResultSet rs = sql.executeQuery();
      if (rs.next()) {
        try {
          return getTemporalLayer(rs);
        } finally {
          rs.close();
          sql.close();               
        }
      } else {
        rs.close();
        sql.close();
	    
        // maybe a transcript attribute
        sql = getConnection().prepareStatement(
          "SELECT * FROM attribute_definition WHERE CONCAT('transcript_', attribute) = ?"
          +" AND class_id = 'transcript'"
          +(getUserRoles().contains("edit")?"":" AND access = '1'"));
        sql.setString(1, id);
        rs = sql.executeQuery();
        if (rs.next()) {
          try {
            return getTranscriptAttributeLayer(rs);
          } finally {
            rs.close();
            sql.close();               
          }
        } else {
          rs.close();
          sql.close();
	       
          // maybe a participant attribute
          sql = getConnection().prepareStatement(
            "SELECT * FROM attribute_definition WHERE CONCAT('participant_', attribute) = ?"
            +" AND class_id = 'speaker'");
          sql.setString(1, id);
          rs = sql.executeQuery();
          if (rs.next()) {
            try {
              return getParticipantAttributeLayer(rs);
            } finally {
              rs.close();
              sql.close();               
            }
          } else {
            rs.close();
            sql.close();

            if (id.matches("[0-9]+")) { // numeric
              // may be a numeric layer_id of a temporal layer
              sql = getConnection().prepareStatement(
                "SELECT layer.*,"
                +" attribute_category.category, parent_layer.short_description AS parent_name"
                +" FROM layer"
                +" LEFT OUTER JOIN attribute_category ON layer.category = attribute_category.category"
                +" LEFT OUTER JOIN layer parent_layer ON layer.parent_id = parent_layer.layer_id"
                +" WHERE layer.layer_id = ?");
              sql.setString(1, id);
              rs = sql.executeQuery();
              if (rs.next()) {
                try {
                  return getTemporalLayer(rs);
                } finally {
                  rs.close();
                  sql.close();               
                }
              } else {
                return null;
              }
            } else { // not numeric
              return null;
            }
          } // not a transcript attribute
        } // not a participant attribute
      } // not a temporal layer
    } catch(SQLException exception) {
      System.err.println("SQL error: " + exception);
      exception.printStackTrace(System.err);
      throw new StoreException("Invalid query."); // TODO i18n
    }
  }

  /**
   * Gets a list of corpus IDs.
   * @return A list of corpus IDs.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   */
  public String[] getCorpusIds() throws StoreException, PermissionException {
    try {
      PreparedStatement sql = getConnection().prepareStatement(
        "SELECT corpus_name FROM corpus ORDER BY corpus_name");
      if (getUser() != null
          && !getUserRoles().contains("admin") && getPermissionsSpecified()) {
        PreparedStatement sqlValuePattern = getConnection().prepareStatement(
          "SELECT value_pattern"
          + " FROM role"
          + " INNER JOIN role_permission ON role.role_id = role_permission.role_id" 
          + " AND role_permission.attribute_name = 'corpus'" 
          + " AND role_permission.entity REGEXP '.*t.*'" // transcript access
          + " WHERE user_id = '"+QL.Esc(getUser())+"'");
        ResultSet rsValuePattern = sqlValuePattern.executeQuery();
        if (rsValuePattern.next()) {
          sql.close();
          sql = getConnection().prepareStatement(
            "SELECT corpus_name FROM corpus WHERE corpus_name REGEXP ? ORDER BY corpus_name");
          sql.setString(1, rsValuePattern.getString(1));
        }
        rsValuePattern.close();
        sqlValuePattern.close();
      }

      ResultSet rs = sql.executeQuery();
      Vector<String> corpora = new Vector<String>();
      while (rs.next()) {
        corpora.add(rs.getString("corpus_name"));
      } // next layer
      rs.close();
      sql.close();
      return corpora.toArray(new String[0]);
    } catch(SQLException exception) {
      System.err.println("SQL error: " + exception);
      exception.printStackTrace(System.err);
      throw new StoreException("Invalid query."); // TODO i18n
    }
  }

  /**
   * Gets a list of participant IDs.
   * @return A list of participant IDs.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   */
  public String[] getParticipantIds() throws StoreException, PermissionException {
    try {
      PreparedStatement sql = getConnection().prepareStatement(
        "SELECT name FROM speaker WHERE COALESCE(name,'') <> ''"
        +userWhereClauseParticipant("AND")
        +" ORDER BY name");
      ResultSet rs = sql.executeQuery();
      Vector<String> participants = new Vector<String>();
      while (rs.next()) {
        participants.add(rs.getString("name"));
      } // next layer
      rs.close();
      sql.close();
      return participants.toArray(new String[0]);
    } catch(SQLException exception) {
      System.err.println("SQL error: " + exception);
      exception.printStackTrace(System.err);
      throw new StoreException("Invalid query."); // TODO i18n
    }
  }
   
  /**
   * Gets the participant record specified by the given identifier.
   * @param id The ID of the participant, which could be their name or their database annotation
   * ID. 
   * @param layerIds The IDs of the participant attribute layers to load, or null if only
   * participant data is required. 
   * @return An annotation representing the participant, or null if the participant was
   * not found.
   * @throws StoreException
   * @throws PermissionException
   */
  public Annotation getParticipant(String id, String[] layerIds)
    throws StoreException, PermissionException {
    try {
      String speakerNumber = null;
      String name = null;
      String userWhereClause = userWhereClauseParticipant("AND");
      if (id.startsWith("m_-2_")) {
        speakerNumber = id.substring("m_-2_".length());
        PreparedStatement sqlParticipant = getConnection().prepareStatement(
          "SELECT name FROM speaker WHERE speaker_number = ?" + userWhereClause);
        sqlParticipant.setString(1, speakerNumber);
        ResultSet rsParticipant = sqlParticipant.executeQuery();
        if (rsParticipant.next()) {
          name = rsParticipant.getString(1);
        }
        rsParticipant.close();
        sqlParticipant.close();
      } else {
        name = id;
        PreparedStatement sqlParticipant = getConnection().prepareStatement(
          "SELECT speaker_number FROM speaker WHERE name = ?" + userWhereClause);
        sqlParticipant.setString(1, name);
        ResultSet rsParticipant = sqlParticipant.executeQuery();
        if (rsParticipant.next()) {
          speakerNumber = rsParticipant.getString(1);
        }
        rsParticipant.close();
        sqlParticipant.close();
      }

      if (name != null && speakerNumber != null) {
        Schema schema = getSchema();
	    
        // add participant annotation
        Annotation participant = new Annotation(
          "m_-2_" + speakerNumber, name, schema.getParticipantLayerId());

        if (layerIds != null && layerIds.length > 0) {
          // read-only users get access to only public attributes
          boolean publicOnly = !getUserRoles().contains("edit");
          
          PreparedStatement sqlValue = getConnection().prepareStatement(
            "SELECT a.annotation_id, a.speaker_number, a.label, a.annotated_by, a.annotated_when"
            +" FROM annotation_participant a"
            +" WHERE a.layer = ? and a.speaker_number = ?"
            +" ORDER BY a.annotation_id");
          sqlValue.setString(2, speakerNumber);
               
          // load participant attributes
          for (String layerId : layerIds) {
            Layer layer = schema.getLayer(layerId);
            if (layer == null) continue;
            // participant tag layers
            if (schema.getParticipantLayerId().equals(layer.getParentId())
                && layer.getAlignment() == Constants.ALIGNMENT_NONE) {
              if ("speaker".equals(layer.get("class_id"))) {
                sqlValue.setString(1, layer.get("attribute").toString());
                if (!publicOnly || "1".equals(layer.get("access"))) {
                  ResultSet rsValue = sqlValue.executeQuery();
                  int ordinal = 1;
                  while (rsValue.next()) {
                    // add graph-tag annotation
                    Object[] annotationIdParts = {
                      layer.get("attribute"),
                      Integer.valueOf(rsValue.getInt("annotation_id"))};
                    Annotation attribute = new Annotation(
                      fmtParticipantAttributeId.format(annotationIdParts), 
                      rsValue.getString("label"), layerId);
                    if (rsValue.getString("annotated_by") != null) {
                      attribute.setAnnotator(rsValue.getString("annotated_by"));
                    }
                    if (rsValue.getTimestamp("annotated_when") != null) {
                      attribute.setWhen(rsValue.getTimestamp("annotated_when"));
                    }
                    attribute.setParentId("m_-2_"+rsValue.getString("speaker_number"));
                    attribute.setOrdinal(ordinal++);
                    participant.addAnnotation(attribute);
                  } // next annotation
                  rsValue.close();
                } // access granted
              } // class_id set
            } else if (layerId.equals(schema.getCorpusLayerId())) { // ad-hockery
              // participant corpora
              PreparedStatement sqlCorpora = getConnection().prepareStatement(
                "SELECT c.corpus_id, c.corpus_name"
                +" FROM speaker_corpus sc"
                +" INNER JOIN corpus c ON sc.corpus_id = c.corpus_id"
                +" WHERE sc.speaker_number = ?"
                +" ORDER BY c.corpus_name");
              sqlCorpora.setString(1, speakerNumber);
              ResultSet rsCorpora = sqlCorpora.executeQuery();
              int o = 1;
              while (rsCorpora.next()) {                     
                Object[] annotationIdParts = {
                  layer.get("layer_id"), rsCorpora.getString("corpus_id")};
                Annotation corpus = new Annotation(
                  fmtMetaAnnotationId.format(annotationIdParts), 
                  rsCorpora.getString("corpus_name"), layer.getId());
                participant.addAnnotation(corpus);
              } // next corpus
              rsCorpora.close();
              sqlCorpora.close();
            } else if (layerId.equals(schema.getRoot().getId())) { // ad-hockery
              // transcripts which the participant is in
              PreparedStatement sqlTranscripts = getConnection().prepareStatement(
                "SELECT DISTINCT t.transcript_id, t.family_sequence"
                +" FROM transcript t"
                +" INNER JOIN transcript_speaker ts ON t.ag_id = ts.ag_id"
                +" WHERE ts.speaker_number = ?"
                +" ORDER BY t.transcript_id, t.family_sequence");
              sqlTranscripts.setString(1, speakerNumber);
              ResultSet rsTranscripts = sqlTranscripts.executeQuery();
              int o = 1;
              while (rsTranscripts.next()) {                     
                Annotation transcript = new Annotation(
                  rsTranscripts.getString("transcript_id"), 
                  rsTranscripts.getString("transcript_id"), layer.getId())
                  .setOrdinal(o++);
                participant.addAnnotation(transcript);
              } // next corpus
              rsTranscripts.close();
              sqlTranscripts.close();
            } else if (layerId.equals(schema.getEpisodeLayerId())) { // ad-hockery
              // episodes of the transcripts which the participant is in
              PreparedStatement sqlEpisodes = getConnection().prepareStatement(
                "SELECT DISTINCT e.family_id, e.name"
                +" FROM transcript_family e"
                +" INNER JOIN transcript t ON e.family_id = t.family_id"
                +" INNER JOIN transcript_speaker ts ON t.ag_id = ts.ag_id"
                +" WHERE ts.speaker_number = ?"
                +" ORDER BY e.name");
              sqlEpisodes.setString(1, speakerNumber);
              ResultSet rsEpisodes = sqlEpisodes.executeQuery();
              int o = 1;
              while (rsEpisodes.next()) {                     
                Object[] annotationIdParts = {
                  layer.get("layer_id"), rsEpisodes.getString("family_id")};
                Annotation episode = new Annotation(
                  fmtMetaAnnotationId.format(annotationIdParts), 
                  rsEpisodes.getString("name"), layer.getId())
                  .setOrdinal(o++);
                participant.addAnnotation(episode);
              } // next corpus
              rsEpisodes.close();
              sqlEpisodes.close();
            }
          } // next layerId
          sqlValue.close();
        } // there are layerIds specified
        return participant;
      } else {
        return null;
      }
    } catch(SQLException exception) {
      System.err.println("SQL error: " + exception);
      exception.printStackTrace(System.err);
      throw new StoreException("Invalid query."); // TODO i18n
    }
  } // end of getParticipant()
   
  /**
   * Saves a participant, and all its tags, to the database.  The participant is
   * represented by an Annotation that isn't assumed to be part of a graph.
   * <p> If a participant with the corresponding ID does not exist, a new participant
   * record is created.
   * @param participant
   * @return true if changes were saved, false if there were no changes to save.
   * @throws StoreException If an error prevents the participant from being saved.
   * @throws PermissionException If saving the participant is not permitted.
   */
  public boolean saveParticipant(Annotation participant)
    throws StoreException, PermissionException {
    requireEdit();
    if (!"participant".equals(participant.getLayerId()))
      throw new StoreException("Annotation is not on the participant layer.");
    if (participant.getChange() == Change.Operation.Destroy )
      throw new StoreException("Deleting participants is not supported.");
      
    boolean thereWereChanges = false;
    try {
      // save the participant record itself
      switch(participant.getChange()) {
        case Update: {
          thereWereChanges = true;
          Object[] o = fmtMetaAnnotationId.parse(participant.getId());
          int speakerNumber = Integer.parseInt(o[1].toString().replace(",",""));
          // update the label (the only possible change)
          PreparedStatement sql = getConnection().prepareStatement(
            "UPDATE speaker SET name = ? WHERE speaker_number = ?");
          sql.setString(1, participant.getLabel());
          sql.setInt(2, speakerNumber);
          sql.executeUpdate();
          sql.close();
          break;
        } case Create: {
            thereWereChanges = true;
            // update the label (the only possible change)
            PreparedStatement sql = getConnection().prepareStatement(
              "INSERT INTO speaker (name) VALUES (?)");
            sql.setString(1, participant.getLabel());
            sql.executeUpdate();
            sql.close();
            sql = getConnection().prepareStatement("SELECT LAST_INSERT_ID()");
            ResultSet rsInsert = sql.executeQuery();
            rsInsert.next();
            int speakerNumber = rsInsert.getInt(1);
            rsInsert.close();
            sql.close();
            Object[] o = { Integer.valueOf(-2), Integer.valueOf(speakerNumber) };
            participant.setId(fmtMetaAnnotationId.format(o));
            break;
          }
      }

      // save the tags
      PreparedStatement sqlInsertParticipantAttribute = getConnection().prepareStatement(
        "INSERT INTO annotation_participant (speaker_number, layer, label, annotated_by, annotated_when) VALUES (?,?,?,?,?)");
      PreparedStatement sqlUpdateParticipantAttribute
        = getConnection().prepareStatement(
          "UPDATE annotation_participant SET label = ?, annotated_by = ?, annotated_when = ? WHERE layer = ? AND annotation_id = ?");
      PreparedStatement sqlUpdateParticipantPassword = connection.prepareStatement(
        "UPDATE speaker SET password = md5(?) WHERE speaker_number = ?");
      PreparedStatement sqlDeleteParticipantAttribute = getConnection().prepareStatement(
        "DELETE FROM annotation_participant WHERE layer = ? AND annotation_id = ?");
      PreparedStatement sqlDeleteAllParticipantAttributesOnLayer
        = getConnection().prepareStatement(
          "DELETE FROM annotation_participant WHERE speaker_number = ? AND layer = ?");
      PreparedStatement sqlInsertParticipantCorpus = getConnection().prepareStatement(
        "INSERT INTO speaker_corpus (speaker_number, corpus_id)"
        +" SELECT ?, corpus_id FROM corpus WHERE corpus_name = ?");
      PreparedStatement sqlDeleteParticipantCorpus = getConnection().prepareStatement(
        "DELETE speaker_corpus.* FROM speaker_corpus"
        +" INNER JOIN corpus ON speaker_corpus.corpus_id = corpus.corpus_id"
        +" WHERE speaker_number = ? AND corpus_name = ?");
      try {
        for (String layerId : participant.getAnnotations().keySet()) {
          for (Annotation attribute : participant.getAnnotations().get(layerId)) {
            if (attribute.getChange() != Change.Operation.NoChange) {
              thereWereChanges = true;
              if (attribute.getParentId() == null) attribute.setParentId(participant.getId());
              saveParticipantAttributeChanges(
                attribute, sqlInsertParticipantAttribute, sqlUpdateParticipantAttribute,
                sqlDeleteParticipantAttribute, sqlDeleteAllParticipantAttributesOnLayer,
                sqlUpdateParticipantPassword, sqlInsertParticipantCorpus,
                sqlDeleteParticipantCorpus);
            }
          } // next child
        } // next child layer
      } finally {
        sqlInsertParticipantAttribute.close();
        sqlUpdateParticipantAttribute.close();
        sqlUpdateParticipantPassword.close();
        sqlDeleteParticipantAttribute.close();
        sqlDeleteAllParticipantAttributesOnLayer.close();
        sqlInsertParticipantCorpus.close();
        sqlDeleteParticipantCorpus.close();
      }
    } catch(ParseException exception) {
      System.err.println("Error parsing ID for participant: "+participant.getId());
      throw new StoreException(
        "Error parsing ID for participant: "+participant.getId(), exception);
    } catch(SQLException exception) {
      System.err.println("SQL error: " + exception);
      exception.printStackTrace(System.err);
      throw new StoreException("Invalid query."); // TODO i18n
    }
    return thereWereChanges;
  } // end of saveParticipant()

  /**
   * Returns an SQL WHERE clause for restricting access by user ID, if the user is set.
   * <p>Assumes that the <tt>transcript</tt> table (with no alias) is in the FROM clause
   * of the query into which the WHERE clause will be embedded.
   * @param prefix What to prefix the clause with, e.g. "AND" or "WHERE".
   * @param transcriptTableAlias The alias for the transcript table.
   * @return A WHERE clause if appropriate, or an empty string if not.
   */
  private String userWhereClauseGraph(String prefix, String transcriptTableAlias) {
    if (getUser() != null && !getUserRoles().contains("admin") && getPermissionsSpecified()) {
      return " " + prefix
        +" (("+transcriptTableAlias+".create_user = '"+QL.Esc(getUser())+"')"
        +" OR EXISTS (SELECT * FROM role"
        + " INNER JOIN role_permission ON role.role_id = role_permission.role_id" 
        + " INNER JOIN annotation_transcript access_attribute" 
        + " ON access_attribute.layer = role_permission.attribute_name" 
        + " AND access_attribute.label REGEXP role_permission.value_pattern"
        + " AND role_permission.entity REGEXP '.*t.*'" // transcript access
        + " WHERE user_id = '"+QL.Esc(getUser())+"'"
        + " AND access_attribute.ag_id = "+transcriptTableAlias+".ag_id)"
        +" OR EXISTS (SELECT * FROM role"
        + " INNER JOIN role_permission ON role.role_id = role_permission.role_id" 
        + " AND role_permission.attribute_name = 'corpus'" 
        + " AND role_permission.entity REGEXP '.*t.*'" // transcript access
        + " WHERE "+transcriptTableAlias+".corpus_name REGEXP role_permission.value_pattern"
        + " AND user_id = '"+QL.Esc(getUser())+"')) ";
    }
    return "";
  } // end of userWhereClauseGraph()

  /**
   * Returns an SQL WHERE clauser for restricting access by userID, if the user is set.
   * <p>Assumes that the <tt>speaker</tt> table (with no alias) is in the FROM clause
   * of the query into which the WHERE clause will be embedded.
   * @param prefix What to prefix the clause with, e.g. "AND" or "WHERE".
   * @return A WHERE clause if appropriate, or an empty string if not.
   */
  private String userWhereClauseParticipant(String prefix) {
    String userWhereClauseSpeaker = "";
    String userWhereClause = userWhereClauseGraph(prefix, "transcript");
    if (userWhereClause.length() > 0) {
      // insert links to speaker table
      userWhereClauseSpeaker = userWhereClause
        .replace(
          "(transcript.create_user = ",
          "EXISTS (SELECT * FROM transcript"
          + " INNER JOIN transcript_speaker ON transcript.ag_id = transcript_speaker.ag_id"
          + " WHERE transcript_speaker.speaker_number = speaker.speaker_number"
          + " AND transcript.create_user = ")
        .replace(
          " WHERE user_id",
          " INNER JOIN transcript_speaker ON access_attribute.ag_id = transcript_speaker.ag_id"
          + " WHERE transcript_speaker.speaker_number = speaker.speaker_number"
          +" AND user_id")
        .replace("AND access_attribute.ag_id = transcript.ag_id", "")
        .replace(
          " WHERE transcript.corpus_name",
          " INNER JOIN transcript_speaker"
          +" INNER JOIN transcript ON transcript_speaker.ag_id = transcript.ag_id"
          + " WHERE transcript_speaker.speaker_number = speaker.speaker_number"
          + " AND transcript.corpus_name")
        .replace(
          "))",
          ")"
          // include those with no transcripts too
          + " OR NOT EXISTS (SELECT * FROM transcript_speaker"
          + " WHERE transcript_speaker.speaker_number = speaker.speaker_number))");
    }
    return userWhereClauseSpeaker;
  } // end of userWhereClauseParticipant()
   
  /**
   * Determines whether the current {@link #user} has access to the given content
   * (entity) related to the given transcript. 
   * @param id Transcript (graph) ID
   * @param entity What they may or may not have access to - one of "t"(ranscript),
   * "i"(mage), "a"(udio), or  "v"(ideo), or null for "has access to anything".
   * @return true if the user has access, false otherwise.
   */
  protected boolean hasAccess(String id, String entity) throws SQLException {
    boolean bHasAccess = false;

    if (getUser() == null) bHasAccess = true;

    if (!bHasAccess) {
      // members of "admin" get access to everything
      bHasAccess = getUserRoles().contains("admin");
    }
      
    if (!bHasAccess) {
      // if they're not using permissions in general, then anyone gets access to everything
      PreparedStatement sqlAccess = getConnection().prepareStatement(
        "SELECT * FROM role_permission LIMIT 1");
      ResultSet rsAccess = sqlAccess.executeQuery();
      if (!rsAccess.next()) bHasAccess = true;
      rsAccess.close();
      sqlAccess.close();
    }
    if (!bHasAccess) { // no a super user, and using permissions, so check the permission tables
      if (id.startsWith("g_")) { // could be a g_nnn ID, where nnn is the ag_id
        try { // we need the transcript_id
          int ag_id = Integer.parseInt(id.substring(2));
          PreparedStatement sqlId = getConnection().prepareStatement(
            "SELECT transcript_id FROM transcript WHERE ag_id = ?");
          sqlId.setInt(1, ag_id);
          ResultSet rsId = sqlId.executeQuery();
          try {
            if (rsId.next()) {
              id = rsId.getString("transcript_id");
            }
          } finally {
            rsId.close();
            sqlId.close();
          }
        } catch(Exception exception) {
          System.err.println(
            "SqlGraphStore.hasAccess("+id+"): can't parse " + id.substring(2) + ": " + exception);
        }
      }
      PreparedStatement sqlAccess = getConnection().prepareStatement(
        "SELECT role.role_id FROM role"
        + " INNER JOIN role_permission ON role.role_id = role_permission.role_id" 
        + " INNER JOIN annotation_transcript access_attribute" 
        + " ON access_attribute.layer = role_permission.attribute_name" 
        + " AND access_attribute.label REGEXP role_permission.value_pattern"
        + " AND role_permission.entity REGEXP CONCAT('.*', ? , '.*')" // what they can access
        + " INNER JOIN transcript ON access_attribute.ag_id = transcript.ag_id" 
        + " WHERE user_id = ?"
        + " AND transcript.transcript_id = ?");
      sqlAccess.setString(1, entity);
      sqlAccess.setString(2, getUser());
      sqlAccess.setString(3, id);
      ResultSet rsAccess = sqlAccess.executeQuery();
      if (rsAccess.next()) bHasAccess = true;
      rsAccess.close();
      sqlAccess.close();

      if (!bHasAccess) { // check for 'corpus' as an attribute
        sqlAccess = getConnection().prepareStatement(
          "SELECT role.role_id FROM role"
          + " INNER JOIN role_permission ON role.role_id = role_permission.role_id" 
          + " INNER JOIN transcript" 
          + " ON role_permission.attribute_name = 'corpus'" 
          + " AND transcript.corpus_name REGEXP role_permission.value_pattern"
          + " AND role_permission.entity REGEXP CONCAT('.*', ? , '.*')" // what they can access
          + " WHERE user_id = ?"
          + " AND transcript.transcript_id = ?");
        sqlAccess.setString(1, entity);
        sqlAccess.setString(2, getUser());
        sqlAccess.setString(3, id);
        rsAccess = sqlAccess.executeQuery();
        if (rsAccess.next()) bHasAccess = true;
        rsAccess.close();
        sqlAccess.close();
      }
    } // need to check permission tables
    return bHasAccess;
  }

  /**
   * Gets a list of transcript IDs.
   * @return A list of transcript IDs.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   */
  public String[] getTranscriptIds() throws StoreException, PermissionException {
    try {
      PreparedStatement sql = getConnection().prepareStatement(
        "SELECT transcript_id FROM transcript " + userWhereClauseGraph("WHERE", "transcript")
        +" ORDER BY transcript_id");
      ResultSet rs = sql.executeQuery();
      Vector<String> graphs = new Vector<String>();
      while (rs.next()) {
        graphs.add(rs.getString("transcript_id"));
      } // next layer
      rs.close();
      sql.close();
      return graphs.toArray(new String[0]);
    } catch(SQLException exception) {
      System.err.println("SQL error: " + exception);
      exception.printStackTrace(System.err);
      throw new StoreException("Invalid query."); // TODO i18n
    }
  }

  /**
   * Gets a list of transcript IDs in the given corpus.
   * @param id A corpus ID.
   * @return A list of transcript IDs.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   */
  public String[] getTranscriptIdsInCorpus(String id)
    throws StoreException, PermissionException {
    return getMatchingTranscriptIds("first('corpus').label = '"+QL.Esc(id)+"'");
  }

  /**
   * Gets a list of IDs of transcripts that include the given participant.
   * @param id A participant ID.
   * @return A list of transcript IDs.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   */
  public String[] getTranscriptIdsWithParticipant(String id)
    throws StoreException, PermissionException {
    return getMatchingTranscriptIds("'"+QL.Esc(id)+"' IN labels('participant')");
  }
   
  /**
   * Converts a participant-matching expression into a resultset (SELECT selectedClause
   * FROM... WHERE... orderClause).
   * <p> The expression language is loosely based on JavaScript; expressions such as
   * the following can be used:
   * <ul>
   *  <li><code>/Ada.+/.test(id)</code></li>
   *  <li><code>labels('corpus').includes('CC')</code></li>
   *  <li><code>labels('participant_languages').includes('en')</code></li>
   *  <li><code>labels('transcript_language').includes('en')</code></li>
   *  <li><code>!/Ada.+/.test(id) &amp;&amp; first('corpus').label == 'CC'</code></li>
   *  <li><code>all('transcript_rating').length &gt; 2</code></li>
   *  <li><code>all('participant_rating').length = 0</code></li>
   *  <li><code>!annotators('transcript_rating').includes('labbcat')</code></li>
   *  <li><code>first('participant_gender').label == 'NA'</code></li>
   * </ul>
   * @param expression The participant-matching expression.
   * @param sqlSelectClause The expression that is to go between SELECT and FROM.
   * @param sqlOrderClause The expression that appended to the end of the SQL query.
   * @return A PreparedStatement for the given expression, with parameters already set.
   * @throws SQLException
   * @throws StoreException If the expression is invalid.
   */
  private PreparedStatement participantMatchSql(
    String expression, String sqlSelectClause, String sqlOrderClause)
    throws SQLException, StoreException, PermissionException {
    String userWhereClause = userWhereClauseParticipant("");
    boolean publicAttributesOnly = getUser() != null && !getUserRoles().contains("edit");
    ParticipantAgqlToSql transformer = new ParticipantAgqlToSql(getSchema());
    ParticipantAgqlToSql.Query q = transformer.sqlFor(
      expression, sqlSelectClause, userWhereClause, publicAttributesOnly, sqlOrderClause);
    // System.err.println("QL: " + expression);
    // System.err.println("SQL: " + q.sql);
    PreparedStatement sql = q.prepareStatement(getConnection());
    return sql;
  } // end of participantMatchSql()

  /**
   * Counts the number of participants that match a particular pattern.
   * @param expression An expression that determines which participants match.
   * <p> The expression language is loosely based on JavaScript; expressions such as the
   * following can be used: 
   * <ul>
   *  <li><code>/Ada.+/.test(id)</code></li>
   *  <li><code>labels('corpus').includes('CC')</code></li>
   *  <li><code>labels('participant_languages').includes('en')</code></li>
   *  <li><code>labels('transcript_language').includes('en')</code></li>
   *  <li><code>!/Ada.+/.test(id) &amp;&amp; first('corpus').label == 'CC'</code></li>
   *  <li><code>all('transcript_rating').length &gt; 2</code></li>
   *  <li><code>all('participant_rating').length = 0</code></li>
   *  <li><code>!annotators('transcript_rating').includes('labbcat')</code></li>
   *  <li><code>first('participant_gender').label == 'NA'</code></li>
   * </ul>
   * @return The number of matching participants.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   */
  public int countMatchingParticipantIds(String expression)
    throws StoreException, PermissionException {
    try {
      PreparedStatement sql = participantMatchSql(expression, "COUNT(*)", "");
      ResultSet rs = sql.executeQuery();
      try {
        rs.next();
        return rs.getInt(1);
      } finally {
        rs.close();
        sql.close();
      }
    } catch(SQLException exception) {
      System.err.println("SQL error from expression \""+expression+"\": " + exception);
      exception.printStackTrace(System.err);
      throw new StoreException("Invalid query."); // TODO i18n
    }
  }
   
  /**
   * Gets a list of IDs of participants that match a particular pattern.
   * @param expression An expression that determines which participants match.
   * <p> The expression language is loosely based on JavaScript; expressions such as the
   * following can be used: 
   * <ul>
   *  <li><code>/Ada.+/.test(id)</code></li>
   *  <li><code>labels('corpus').includes('CC')</code></li>
   *  <li><code>labels('participant_languages').includes('en')</code></li>
   *  <li><code>labels('transcript_language').includes('en')</code></li>
   *  <li><code>!/Ada.+/.test(id) &amp;&amp; first('corpus').label == 'CC'</code></li>
   *  <li><code>all('transcript_rating').length &gt; 2</code></li>
   *  <li><code>all('participant_rating').length = 0</code></li>
   *  <li><code>!annotators('transcript_rating').includes('labbcat')</code></li>
   *  <li><code>first('participant_gender').label == 'NA'</code></li>
   *  <li><code>all('transcript').length == 0</code></li>
   * </ul>
   * @return A list of participant IDs.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   */
  public String[] getMatchingParticipantIds(String expression)
    throws StoreException, PermissionException {
    return getMatchingParticipantIds(expression, null, null);
  }

  /**
   * Gets a list of IDs of participants that match a particular pattern.
   * @param expression An expression that determines which participants match.
   * <p> The expression language is loosely based on JavaScript; expressions such as the
   * following can be used: 
   * <ul>
   *  <li><code>/Ada.+/.test(id)</code></li>
   *  <li><code>labels('corpus').includes('CC')</code></li>
   *  <li><code>labels('participant_languages').includes('en')</code></li>
   *  <li><code>labels('transcript_language').includes('en')</code></li>
   *  <li><code>!/Ada.+/.test(id) &amp;&amp; first('corpus').label == 'CC'</code></li>
   *  <li><code>all('transcript_rating').length &gt; 2</code></li>
   *  <li><code>all('participant_rating').length = 0</code></li>
   *  <li><code>!annotators('transcript_rating').includes('labbcat')</code></li>
   *  <li><code>first('participant_gender').label == 'NA'</code></li>
   *  <li><code>all('transcript').length == 0</code></li>
   * </ul>
   * @param pageLength The maximum number of IDs to return, or null to return all.
   * @param pageNumber The page number to return, or null to return the first page.
   * @return A list of participant IDs.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   */
  public String[] getMatchingParticipantIds(
    String expression, Integer pageLength, Integer pageNumber)
    throws StoreException, PermissionException {
    try {
      String limit = "";
      if (pageLength != null) {
        if (pageNumber == null) pageNumber = 0;
        limit = " LIMIT " + (pageNumber * pageLength) + "," + pageLength;
      }
      PreparedStatement sql = participantMatchSql(
        expression, "DISTINCT speaker.name", "ORDER BY speaker.name" + limit);
      ResultSet rs = sql.executeQuery();
      Vector<String> ids = new Vector<String>();
      while (rs.next()) {
        ids.add(rs.getString("name"));
      } // next layer
      rs.close();
      sql.close();
      return ids.toArray(new String[0]);
    } catch(SQLException exception) {
      System.err.println("SQL error from expression \""+expression+"\": " + exception);
      exception.printStackTrace(System.err);
      throw new StoreException("Invalid query."); // TODO i18n
    }
  }

  /**
   * Converts a graph-matching expression into a resultset (SELECT selectedClause
   * FROM... WHERE... orderClause).
   * <p> The expression language is loosely based on JavaScript; expressions such as
   * the following can be used:
   * <ul>
   *  <li><code>/Ada.+/.test(id)</code></li>
   *  <li><code>labels('participant').includes('Robert')</code></li>
   *  <li><code>['CC', 'IA', 'MU'].includes(first('corpus').label)</code></li>
   *  <li><code>first('episode').label == 'Ada Aitcheson'</code></li>
   *  <li><code>first('transcript_scribe').label == 'Robert'</code></li>
   *  <li><code>first('participant_languages').label == 'en'</code></li>
   *  <li><code>first('noise').label == 'bell'</code></li>
   *  <li><code>labels('transcript_languages').includes('en')</code></li>
   *  <li><code>labels('participant_languages').includes('en')</code></li>
   *  <li><code>labels('noise').includes('bell')</code></li>
   *  <li><code>all('transcript_languages').length gt; 1</code></li>
   *  <li><code>all('participant_languages').length gt; 1</code></li>
   *  <li><code>all('word').length &gt; 100</code></li>
   *  <li><code>annotators('transcript_rating').includes('Robert')</code></li>
   *  <li><code>!/Ada.+/.test(id) &amp;&amp; first('corpus').label == 'CC' &amp;&amp;
   * labels('participant').includes('Robert')</code></li> 
   * </ul>
   * @param expression The graph-matching expression.
   * @param selectClause The expression that is to go between SELECT and FROM.
   * @param order The expression that appended to the end of the SQL query.
   * @param limit SQL LIMIT clause, if any.
   * @return A PreparedStatement for the given expression, with parameters already set.
   * @throws SQLException
   * @throws StoreException If the expression is invalid.
   */
  private PreparedStatement graphMatchSql(
    String expression, String selectClause, String order, String limit)
    throws SQLException, StoreException, PermissionException {
    String userWhereClause = userWhereClauseGraph("", "transcript");
    GraphAgqlToSql transformer = new GraphAgqlToSql(getSchema());
    GraphAgqlToSql.Query q = transformer.sqlFor(
      expression, selectClause, userWhereClause, order, limit);
    // System.out.println("QL: " + expression);
    // System.out.println("SQL: " + q.sql);
    PreparedStatement sql = q.prepareStatement(getConnection());
    return sql;
  } // end of graphMatchSql()

  /**
   * Counts the number of transcript IDs that match a particular pattern.
   * @param expression An expression that determines which transcripts match.
   * <p> The expression language is loosely based on JavaScript; expressions such as
   * the following can be used:
   * <ul>
   *  <li><code>/Ada.+/.test(id)</code></li>
   *  <li><code>labels('participant').includes('Robert')</code></li>
   *  <li><code>['CC', 'IA', 'MU'].includes(first('corpus').label)</code></li>
   *  <li><code>first('episode').label == 'Ada Aitcheson'</code></li>
   *  <li><code>first('transcript_scribe').label == 'Robert'</code></li>
   *  <li><code>first('participant_languages').label == 'en'</code></li>
   *  <li><code>first('noise').label == 'bell'</code></li>
   *  <li><code>labels('transcript_languages').includes('en')</code></li>
   *  <li><code>labels('participant_languages').includes('en')</code></li>
   *  <li><code>labels('noise').includes('bell')</code></li>
   *  <li><code>all('transcript_languages').length gt; 1</code></li>
   *  <li><code>all('participant_languages').length gt; 1</code></li>
   *  <li><code>all('word').length &gt; 100</code></li>
   *  <li><code>annotators('transcript_rating').includes('Robert')</code></li>
   *  <li><code>!/Ada.+/.test(id) &amp;&amp; first('corpus').label == 'CC' &amp;&amp;
   * labels('participant').includes('Robert')</code></li> 
   * </ul>
   * @return The number of matching transcripts.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   */
  public int countMatchingTranscriptIds(String expression)
    throws StoreException, PermissionException {
    try {
      PreparedStatement sql = graphMatchSql(expression, "COUNT(*)", null, null);
      ResultSet rs = sql.executeQuery();
      try {
        rs.next();
        return rs.getInt(1);
      } finally {
        rs.close();
        sql.close();
      }
    } catch(SQLException exception) {
      System.err.println("SQL error from expression \""+expression+"\": " + exception);
      exception.printStackTrace(System.err);
      throw new StoreException("Invalid query."); // TODO i18n
    }
  }
   
  /**
   * Gets a list of IDs of transcripts that match a particular pattern.
   * @param expression An expression that determines which transcripts match.
   * <p> The expression language is loosely based on JavaScript; expressions such as
   * the following can be used:
   * <ul>
   *  <li><code>/Ada.+/.test(id)</code></li>
   *  <li><code>labels('participant').includes('Robert')</code></li>
   *  <li><code>['CC', 'IA', 'MU'].includes(first('corpus').label)</code></li>
   *  <li><code>first('episode').label == 'Ada Aitcheson'</code></li>
   *  <li><code>first('transcript_scribe').label == 'Robert'</code></li>
   *  <li><code>first('participant_languages').label == 'en'</code></li>
   *  <li><code>first('noise').label == 'bell'</code></li>
   *  <li><code>labels('transcript_languages').includes('en')</code></li>
   *  <li><code>labels('participant_languages').includes('en')</code></li>
   *  <li><code>labels('noise').includes('bell')</code></li>
   *  <li><code>all('transcript_languages').length gt; 1</code></li>
   *  <li><code>all('participant_languages').length gt; 1</code></li>
   *  <li><code>all('word').length &gt; 100</code></li>
   *  <li><code>annotators('transcript_rating').includes('Robert')</code></li>
   *  <li><code>!/Ada.+/.test(id) &amp;&amp; first('corpus').label == 'CC' &amp;&amp;
   * labels('participant').includes('Robert')</code></li> 
   * </ul>
   * @param pageLength The maximum number of IDs to return, or null to return all.
   * @param pageNumber The zero-based page number to return, or null to return the first page.
   * @param order The ordering for the list of IDs, a string containing a comma-separated
   * list of epxressions, which may be appended by " ASC" or " DESC", or null for transcript
   * ID order.
   * @return A list of transcript IDs.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   */
  public String[] getMatchingTranscriptIds(
    String expression, Integer pageLength, Integer pageNumber, String order)
    throws StoreException, PermissionException {
    try {
      String limit = "";
      if (pageLength != null) {
        if (pageNumber == null) pageNumber = 0;
        limit = " LIMIT " + (pageNumber * pageLength) + "," + pageLength;
      }
      if (order == null) order = "id ASC";
      PreparedStatement sql = graphMatchSql(
        expression, "transcript_id", order, limit);
      ResultSet rs = sql.executeQuery();
      Vector<String> graphs = new Vector<String>();
      while (rs.next()) {
        graphs.add(rs.getString("transcript_id"));
      } // next layer
      rs.close();
      sql.close();
      return graphs.toArray(new String[0]);
    } catch(SQLException exception) {
      System.err.println("SQL error from expression \""+expression+"\": " + exception);
      exception.printStackTrace(System.err);
      throw new StoreException("Invalid query."); // TODO i18n
    }
  }
   
  /**
   * Gets a transcript given its ID.
   * @param id The given transcript ID.
   * @return The identified transcript with annotations from all layers.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   * @throws GraphNotFoundException If the transcript was not found in the store.
   */
  public Graph getTranscript(String id) throws StoreException, PermissionException, GraphNotFoundException {
    return getTranscript(id, getLayerIds());
  }

  /**
   * Gets a transcript given its ID, containing only the given layers.
   * @param id The given transcript ID.
   * @param layerIds The IDs of the layers to load, or null if only transcript data is required.
   * @return The identified transcript.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   * @throws GraphNotFoundException If the transcript was not found in the store.
   */
  public Graph getTranscript(String id, String[] layerIds)
    throws StoreException, PermissionException, GraphNotFoundException {
    try {
      Graph graph = new Graph();
      graph.setMediaProvider(new StoreGraphMediaProvider(graph, this));
      Schema mainSchema = getSchema();
      graph.getSchema().setParticipantLayerId(mainSchema.getParticipantLayerId());
      graph.getSchema().setTurnLayerId(mainSchema.getTurnLayerId());
      graph.getSchema().setUtteranceLayerId(mainSchema.getUtteranceLayerId());
      graph.getSchema().setWordLayerId(mainSchema.getWordLayerId());
      graph.getSchema().setEpisodeLayerId(mainSchema.getEpisodeLayerId());
      graph.getSchema().setCorpusLayerId(mainSchema.getCorpusLayerId());

      PreparedStatement sql = getConnection().prepareStatement(
        "SELECT transcript.*, COALESCE(transcript_family.name,'') AS series,"
        +" COALESCE(transcript_type.transcript_type,'') AS transcript_type, divergent.label AS divergent"
        +" FROM transcript"
        +" LEFT OUTER JOIN transcript_family ON transcript.family_id = transcript_family.family_id"
        +" LEFT OUTER JOIN transcript_type ON transcript.type_id = transcript_type.type_id"
        +" LEFT OUTER JOIN annotation_transcript divergent ON transcript.ag_id = divergent.ag_id AND divergent.layer = 'divergent'"
        +" WHERE transcript.transcript_id = ?"+userWhereClauseGraph("AND", "transcript"));
      sql.setString(1, id);
      ResultSet rs = sql.executeQuery();
      if (!rs.next()) { // graph not found - maybe we've been given a name without the extension?
        rs.close();
        sql.close();
        sql = getConnection().prepareStatement(
          "SELECT transcript.*, COALESCE(transcript_family.name,'') AS series,"
          +" COALESCE(transcript_type.transcript_type,'') AS transcript_type, divergent.label AS divergent"
          +" FROM transcript"
          +" LEFT OUTER JOIN transcript_family ON transcript.family_id = transcript_family.family_id"
          +" LEFT OUTER JOIN transcript_type ON transcript.type_id = transcript_type.type_id"
          +" LEFT OUTER JOIN annotation_transcript divergent ON transcript.ag_id = divergent.ag_id AND divergent.layer = 'divergent'"
          +" WHERE transcript.transcript_id REGEXP ?"
          +userWhereClauseGraph("AND", "transcript"));
        sql.setString(1, "^" + id
                      .replace("(","\\(").replace(")","\\)") // parentheses are literal
                      + "\\.[^.]+$");
        rs = sql.executeQuery();
        if (!rs.next()) { // graph not found - maybe we've been given a name with a mismatching extension?
          rs.close();
          sql.close();
          sql = getConnection().prepareStatement(
            "SELECT transcript.*, COALESCE(transcript_family.name,'') AS series,"
            +" COALESCE(transcript_type.transcript_type,'') AS transcript_type, divergent.label AS divergent"
            +" FROM transcript"
            +" LEFT OUTER JOIN transcript_family ON transcript.family_id = transcript_family.family_id"
            +" LEFT OUTER JOIN transcript_type ON transcript.type_id = transcript_type.type_id"
            +" LEFT OUTER JOIN annotation_transcript divergent ON transcript.ag_id = divergent.ag_id AND divergent.layer = 'divergent'"
            +" WHERE transcript.transcript_id REGEXP ?"
            +userWhereClauseGraph("AND", "transcript"));
          sql.setString(1, "^" + id.replaceAll("\\.[^.]+$","")
                        .replace("(","\\(").replace(")","\\)") // parentheses are literal
                        + "\\.[^.]+$");
          rs = sql.executeQuery();
          if (!rs.next()) { // graph not found - maybe we've been given an ag_id?
            try {
              rs.close();
              sql.close();
              int iAgId = Integer.parseInt(
                id
                // (could be from a MatchId - i.e. prefixed by "g_")
                .replaceAll("^g_","")); 
              sql = getConnection().prepareStatement(
                "SELECT transcript.*, COALESCE(transcript_family.name,'') AS series,"
                +" COALESCE(transcript_type.transcript_type,'') AS transcript_type, divergent.label AS divergent"
                +" FROM transcript"
                +" LEFT OUTER JOIN transcript_family ON transcript.family_id = transcript_family.family_id"
                +" LEFT OUTER JOIN transcript_type ON transcript.type_id = transcript_type.type_id"
                +" LEFT OUTER JOIN annotation_transcript divergent ON transcript.ag_id = divergent.ag_id AND divergent.layer = 'divergent'"
                +" WHERE transcript.ag_id = ?"+userWhereClauseGraph("AND", "transcript"));
              sql.setInt(1, iAgId);
              rs = sql.executeQuery();
              if (!rs.next()) throw new GraphNotFoundException(id);
            } catch(NumberFormatException exception) {
              throw new GraphNotFoundException(id);
            }
          }
        }
      }
	 
      graph.setId(rs.getString("transcript_id"));
      graph.setOffsetUnits(rs.getString("offset_units"));
      int iAgId = rs.getInt("ag_id");
      // these "@" attributes can be included as special layerIds
      graph.put("@ag_id", Integer.valueOf(iAgId));
      graph.setCorpus(rs.getString("corpus_name"));
      graph.put("@audio_prompt", rs.getString("audio_prompt"));
      graph.put("@transcript_type", rs.getString("transcript_type"));
      graph.put("@series", rs.getString("series"));
      graph.put("@family_id", rs.getInt("family_id"));
      graph.setOrdinal(rs.getInt("family_sequence"));
      graph.put("@offset_in_series", rs.getDouble("family_offset"));
      if (rs.getString("divergent") != null) graph.put("@divergent", Boolean.TRUE);

      rs.close();

      Vector<String> setStartEndLayers = new Vector<String>();

      if (layerIds != null) {
        // read-only users get access to only public attributes
        boolean publicOnly = !getUserRoles().contains("edit");
        
        // for each layer specified, all ancestor layers must also be loaded, to
        // ensure the graph is well structured.
        final LinkedHashSet<String> layersToLoad = new LinkedHashSet<String>();
        for (String layerId : layerIds) {
          Layer layer = mainSchema.getLayer(layerId);
          if (layer != null) {
            for (Layer ancestor : layer.getAncestors()) {
              layersToLoad.add(ancestor.getId());
            }		  
            layersToLoad.add(layerId);
          } else { // layer doesn't exist
            // special layer? - e.g. "audio_prompt", "ag_id", etc.
            if (graph.containsKey("@"+layerId)) {
              try { // only if getLayer recognizes the id
                graph.addLayer(getLayer(layerId));
                Object label = graph.get("@" + layerId);
                if (label != null) {
                  graph.addAnnotation(new Annotation(layerId, label.toString(), layerId));
                } // value set
              } catch(StoreException exception) { // getLayer didn't like it
                // just ignore it
              }
            } else if (layerId.equals("previous-transcript")) { // Previous transcript in episode
              graph.addLayer(getLayer(layerId));
              PreparedStatement adjacentTranscript = getConnection().prepareStatement(
                "SELECT transcript_id FROM transcript"
                +" WHERE family_id = ? AND family_sequence < ?"
                +" ORDER BY family_sequence DESC LIMIT 1");
              adjacentTranscript.setInt(1, (Integer)graph.get("@family_id"));
              adjacentTranscript.setInt(2, graph.getOrdinal());
              ResultSet rsAdjacent = adjacentTranscript.executeQuery();
              if (rsAdjacent.next()) {
                graph.addAnnotation(
                  new Annotation(layerId, rsAdjacent.getString("transcript_id"), layerId));
              }
              rsAdjacent.close();
              adjacentTranscript.close();
            } else if (layerId.equals("next-transcript")) { // Next transcript in episode
              graph.addLayer(getLayer(layerId));
              PreparedStatement adjacentTranscript = getConnection().prepareStatement(
                "SELECT transcript_id FROM transcript"
                +" WHERE family_id = ? AND family_sequence > ?"
                +" ORDER BY family_sequence ASC LIMIT 1");
              adjacentTranscript.setInt(1, (Integer)graph.get("@family_id"));
              adjacentTranscript.setInt(2, graph.getOrdinal());
              ResultSet rsAdjacent = adjacentTranscript.executeQuery();
              if (rsAdjacent.next()) {
                graph.addAnnotation(
                  new Annotation(layerId, rsAdjacent.getString("transcript_id"), layerId));
              }
              rsAdjacent.close();
              adjacentTranscript.close();
            }
          } // nonexistent layer
        } // next specified layer

        // order them top-down to ensure parents are available if required
        LayerHierarchyTraversal<LinkedHashSet<String>> topDown
          = new LayerHierarchyTraversal<LinkedHashSet<String>>(
            new LinkedHashSet<String>(), mainSchema) {
              protected void pre(Layer layer) {
                if (layersToLoad.contains(layer.getId())) {
                  result.add(layer.getId());
                }
              }
            };
	    
        // load annotations
        PreparedStatement sqlAnnotation = getConnection().prepareStatement(
          "SELECT layer.*,"
          +" start.offset AS start_offset, start.alignment_status AS start_alignment_status,"
          +" start.annotated_by AS start_annotated_by, start.annotated_when AS start_annotated_when,"
          +" end.offset AS end_offset, end.alignment_status AS end_alignment_status,"
          +" end.annotated_by AS end_annotated_by, end.annotated_when AS end_annotated_when"
          +" FROM annotation_layer_? layer"
          +" INNER JOIN anchor start ON layer.start_anchor_id = start.anchor_id"
          +" INNER JOIN anchor end ON layer.end_anchor_id = end.anchor_id"
          +" WHERE layer.ag_id = ? ORDER BY start.offset, end.offset DESC, annotation_id");
        sqlAnnotation.setInt(2, iAgId);
        for (String layerId : topDown.getResult()) {
          Layer layer = getLayer(layerId);
          if (layerId.equals("transcript")) { // special case
            continue;
          } else if (layerId.equals("participant")) {
            // create participant layer...
            // thereby creating a lookup list of participant names
            graph.addLayer(layer);
            Layer mainParticipantLayer = getLayer("main_participant");
            graph.addLayer(mainParticipantLayer);
            PreparedStatement sqlParticipant = getConnection().prepareStatement(
              "SELECT speaker.speaker_number, speaker.name, transcript_speaker.main_speaker"
              +" FROM speaker"
              +" INNER JOIN transcript_speaker ON transcript_speaker.speaker_number = speaker.speaker_number"
              +" WHERE ag_id = ? ORDER BY speaker.name");
            sqlParticipant.setInt(1, iAgId);
            ResultSet rsParticipant = sqlParticipant.executeQuery();
            while (rsParticipant.next()) {
              // add graph-tag annotation
              Object[] annotationIdParts = {
                layer.get("layer_id"), rsParticipant.getString("speaker_number")};
              Annotation participant = new Annotation(
                fmtMetaAnnotationId.format(annotationIdParts), 
                rsParticipant.getString("name"), layer.getId());
              participant.setParentId(graph.getId());		     
              graph.addAnnotation(participant);
		     
              // are they a main participant?
              if (rsParticipant.getInt("main_speaker") == 1) {
                Object[] annotationIdParts2 = {
                  mainParticipantLayer.get("layer_id"), rsParticipant.getString("speaker_number")};
                Annotation mainParticipant = new Annotation(
                  fmtMetaAnnotationId.format(annotationIdParts2), 
                  rsParticipant.getString("name"), "main_participant");
                mainParticipant.setParentId(participant.getId());
                graph.addAnnotation(mainParticipant);
              }
            } // next participant
            rsParticipant.close();
            sqlParticipant.close();

            setStartEndLayers.add(layerId);
            continue;
          } else if (layerId.equals("main_participant")) { // loaded with "participant"
            setStartEndLayers.add(layerId);
            continue;
          } else if (layerId.equals("episode")) {
            graph.addLayer(layer);
            PreparedStatement sqlEpisode = getConnection().prepareStatement(
              "SELECT t.family_id, e.name"
              +" FROM transcript t"
              +" INNER JOIN transcript_family e ON e.family_id = t.family_id"
              +" WHERE t.ag_id = ?");
            sqlEpisode.setInt(1, iAgId);
            ResultSet rsEpisode = sqlEpisode.executeQuery();
            if (rsEpisode.next()) {
              // add episode annotation
              Object[] annotationIdParts = {
                layer.get("layer_id"), rsEpisode.getString("family_id")};
              Annotation episode = new Annotation(
                fmtMetaAnnotationId.format(annotationIdParts), 
                rsEpisode.getString("name"), layer.getId());
              episode.setParentId(graph.getId());
              graph.addAnnotation(episode);		     
            }
            rsEpisode.close();
            sqlEpisode.close();
            setStartEndLayers.add(layerId);
            continue;
          } else if ("episode".equals(layer.getParentId())) { // episode tag layer
            graph.addLayer(layer);
            PreparedStatement sqlEpisode = getConnection().prepareStatement(
              "SELECT a.annotation_id, a.label, a.annotated_by, a.annotated_when, t.family_id, data"
              +" FROM `annotation_layer_" + layer.get("layer_id") + "` a"
              +" INNER JOIN transcript t ON t.family_id = a.family_id"
              +" WHERE t.ag_id = ?");
            sqlEpisode.setInt(1, iAgId);
            ResultSet rsEpisode = sqlEpisode.executeQuery();
            while (rsEpisode.next()) {
              // add graph-tag annotation
              Object[] annotationIdParts = {
                "e", layer.get("layer_id"), rsEpisode.getInt("annotation_id")};
              Object[] parentAnnotationIdParts = {
                Integer.valueOf(-50), rsEpisode.getString("family_id")};
              Annotation episodeTag = new Annotation(
                fmtAnnotationId.format(annotationIdParts), 
                rsEpisode.getString("label"), layer.getId());
              episodeTag.setParentId(fmtMetaAnnotationId.format(parentAnnotationIdParts));
              if (rsEpisode.getString("annotated_by") != null) {
                episodeTag.setAnnotator(rsEpisode.getString("annotated_by"));
              }
              if (rsEpisode.getTimestamp("annotated_when") != null) {
                episodeTag.setWhen(rsEpisode.getTimestamp("annotated_when"));
              }
              byte[] data = rsEpisode.getBytes("data");
              try {
                if (data != null) episodeTag.put("data", data);
              } catch(Exception exception) {
                System.err.println(
                  "Could not get data for " + episodeTag.getId() + ": " + exception);
              }
              graph.addAnnotation(episodeTag);		     
            }
            rsEpisode.close();
            sqlEpisode.close();
            setStartEndLayers.add(layerId);
            continue;
          } else if ("E".equals(layer.get("scope"))) { // episode tag tag layer
            graph.addLayer(layer);
            PreparedStatement sqlEpisode = getConnection().prepareStatement(
              "SELECT a.annotation_id, a.label, a.annotated_by, a.annotated_when, a.parent_id, t.family_id, data"
              +" FROM `annotation_layer_" + layer.get("layer_id") + "` a"
              +" INNER JOIN transcript t ON t.family_id = a.family_id"
              +" WHERE t.ag_id = ?");
            sqlEpisode.setInt(1, iAgId);
            ResultSet rsEpisode = sqlEpisode.executeQuery();
            while (rsEpisode.next()) {
              // add graph-tag annotation
              Object[] annotationIdParts = {
                "e", layer.get("layer_id"), rsEpisode.getInt("annotation_id")};
              Object[] parentAnnotationIdParts = {
                "e", layer.getParent().get("layer_id"), rsEpisode.getInt("parent_id")};
              Annotation episodeTag = new Annotation(
                fmtAnnotationId.format(annotationIdParts), 
                rsEpisode.getString("label"), layer.getId());
              episodeTag.setParentId(fmtAnnotationId.format(parentAnnotationIdParts));
              if (rsEpisode.getString("annotated_by") != null) {
                episodeTag.setAnnotator(rsEpisode.getString("annotated_by"));
              }
              if (rsEpisode.getTimestamp("annotated_when") != null) {
                episodeTag.setWhen(rsEpisode.getTimestamp("annotated_when"));
              }
              byte[] data = rsEpisode.getBytes("data");
              try {
                if (data != null) episodeTag.put("data", data);
              } catch(Exception exception) {
                System.err.println(
                  "Could not get data for " + episodeTag.getId() + ": " + exception);
              }
              graph.addAnnotation(episodeTag);		     
            }
            rsEpisode.close();
            sqlEpisode.close();
            setStartEndLayers.add(layerId);
            continue;
          } else if (layerId.equals("corpus")) {
            graph.addLayer(layer);
            PreparedStatement sqlCorpus = getConnection().prepareStatement(
              "SELECT t.corpus_name, COALESCE(c.corpus_id, t.corpus_name) AS corpus_id"
              +" FROM transcript t"
              +" LEFT OUTER JOIN corpus c ON c.corpus_name = t.corpus_name"
              +" WHERE t.ag_id = ?");
            sqlCorpus.setInt(1, iAgId);
            ResultSet rsCorpus = sqlCorpus.executeQuery();
            if (rsCorpus.next()) {
              // add graph-tag annotation
              Object[] annotationIdParts = {
                layer.get("layer_id"), rsCorpus.getString("corpus_id")};
              Annotation corpus = new Annotation(
                fmtMetaAnnotationId.format(annotationIdParts), 
                rsCorpus.getString("corpus_name"), layer.getId());
              corpus.setParentId(graph.getId());
              graph.addAnnotation(corpus);		     
            }
            rsCorpus.close();
            sqlCorpus.close();
            setStartEndLayers.add(layerId);
            continue;
          } else if (layerId.equals("transcript_type")) {
            graph.addLayer(layer);
            PreparedStatement sqlType = getConnection().prepareStatement(
              "SELECT transcript_type, t.type_id"
              +" FROM transcript t"
              +" INNER JOIN transcript_type tt ON tt.type_id = t.type_id"
              +" WHERE t.ag_id = ?");
            sqlType.setInt(1, iAgId);
            ResultSet rsType = sqlType.executeQuery();
            if (rsType.next()) {
              // add graph-tag annotation
              Object[] annotationIdParts = {"type", Integer.valueOf(iAgId)};
              Annotation type = new Annotation(
                fmtTranscriptAttributeId.format(annotationIdParts), 
                rsType.getString("transcript_type"), layer.getId());
              type.setParentId(graph.getId());
              graph.addAnnotation(type);
            }
            rsType.close();
            sqlType.close();
            setStartEndLayers.add(layerId);
            continue;
          }
	       
          if (layerId.startsWith("transcript_")) { // probably a transcript attribute layer
            if ("transcript".equals(layer.get("class_id"))) {
              if (!publicOnly || "1".equals(layer.get("access"))) {
                // definitedly a transcript attribute layer
                graph.addLayer(layer);
                PreparedStatement sqlValue = getConnection().prepareStatement(
                  "SELECT annotation_id, label, annotated_by, annotated_when"
                  +" FROM annotation_transcript"
                  +" WHERE ag_id = ? AND layer = ?");
                sqlValue.setInt(1, iAgId);
                sqlValue.setString(2, layer.get("attribute").toString());
                ResultSet rsValue = sqlValue.executeQuery();
                boolean attributeFound = false;
                while (rsValue.next()) {
                  attributeFound = true;
                  // add graph-tag annotation
                  Object[] annotationIdParts = {
                    layer.get("attribute"),
                    Integer.valueOf(rsValue.getInt("annotation_id"))};
                  Annotation attribute = new Annotation(
                    fmtTranscriptAttributeId.format(annotationIdParts), 
                    rsValue.getString("label"), layer.getId());
                  if (rsValue.getString("annotated_by") != null) {
                    attribute.setAnnotator(rsValue.getString("annotated_by"));
                  }
                  if (rsValue.getTimestamp("annotated_when") != null) {
                    attribute.setWhen(rsValue.getTimestamp("annotated_when"));
                  }
                  attribute.setParentId(graph.getId());
                  graph.addAnnotation(attribute);
                } 
                if (!attributeFound && layer.getId().equals("transcript_language")) {
                  // transcript_language can magically inherit from corpus
                  PreparedStatement sqlCorpusLanguage = getConnection().prepareStatement(
                    "SELECT corpus_language FROM corpus"
                    +" INNER JOIN transcript ON transcript.corpus_name = corpus.corpus_name"
                    +" WHERE ag_id = ?");
                  sqlCorpusLanguage.setInt(1, iAgId);
                  ResultSet rsCorpusLanguage = sqlCorpusLanguage.executeQuery();
                  if (rsCorpusLanguage.next()) {
                    Object[] annotationIdParts = {
                      layer.get("attribute"), Integer.valueOf(iAgId)};
                    Annotation attribute = new Annotation(
                      fmtTranscriptAttributeId.format(annotationIdParts),
                      rsCorpusLanguage.getString("corpus_language"), 
                      layer.getId());
                    attribute.setParentId(graph.getId());
                    graph.addAnnotation(attribute);
                  }
                  rsCorpusLanguage.close();
                  sqlCorpusLanguage.close();
                }
                rsValue.close();
                sqlValue.close();
                setStartEndLayers.add(layerId);
              } // has access
              continue;
            } // definitely a transcript attribute layer
          } // probably a transcript attribute layer
	       
          if (layerId.startsWith("participant_")) { // probably a participant attribute layer
            if ("speaker".equals(layer.get("class_id"))) { // definitedly a participant attribute layer
              if (!publicOnly || "1".equals(layer.get("access"))) {
                graph.addLayer(layer);
                PreparedStatement sqlValue = getConnection().prepareStatement(
                  "SELECT a.annotation_id, a.speaker_number, a.label, a.annotated_by, a.annotated_when"
                  +" FROM annotation_participant a"
                  +" INNER JOIN transcript_speaker ts ON ts.speaker_number = a.speaker_number"
                  +" WHERE ts.ag_id = ? AND a.layer = ?");
                sqlValue.setInt(1, iAgId);
                sqlValue.setString(2, layer.get("attribute").toString());
                ResultSet rsValue = sqlValue.executeQuery();
                int ordinal = 1;
                while (rsValue.next()) {
                  // add graph-tag annotation
                  Object[] annotationIdParts = {
                    layer.get("attribute"),
                    Integer.valueOf(rsValue.getInt("annotation_id"))};
                  Annotation attribute = new Annotation(
                    fmtParticipantAttributeId.format(annotationIdParts), 
                    rsValue.getString("label"), layer.getId());
                  if (rsValue.getString("annotated_by") != null) {
                    attribute.setAnnotator(rsValue.getString("annotated_by"));
                  }
                  if (rsValue.getTimestamp("annotated_when") != null) {
                    attribute.setWhen(rsValue.getTimestamp("annotated_when"));
                  }
                  attribute.setParentId("m_-2_"+rsValue.getString("speaker_number"));
                  attribute.setOrdinal(ordinal++);
                  graph.addAnnotation(attribute);
                }
                rsValue.close();
                sqlValue.close();
                setStartEndLayers.add(layerId);
              } // has access
              continue;
            } // definitely a particiapant attribute layer
          } // probably a participant attribute layer

          graph.addLayer(layer);
          int iLayerId = ((Integer)layer.get("layer_id")).intValue();
          String scope = (String)layer.get("scope");
          sqlAnnotation.setInt(1, iLayerId);
          ResultSet rsAnnotation = sqlAnnotation.executeQuery();
          while (rsAnnotation.next()) {
            Annotation annotation = annotationFromResult(rsAnnotation, layer, graph);

            // start anchor
            if (graph.getAnchor(annotation.getStartId()) == null) {
              // start anchor isn't in graph yet
              graph.addAnchor(anchorFromResult(rsAnnotation, "start_"));
            } // start anchor isn't in graph yet 		  

            // end anchor
            if (graph.getAnchor(annotation.getEndId()) == null) {
              // start anchor isn't in graph yet
              graph.addAnchor(anchorFromResult(rsAnnotation, "end_"));
            } // start anchor isn't in graph yet 

            graph.addAnnotation(annotation);

          } // next annotation
          rsAnnotation.close();
        } // next layerId
        sqlAnnotation.close();
      } // layerIds specified

      // set anchors for graph tag layers
      SortedSet<Anchor> anchors = graph.getSortedAnchors();
      if (anchors.size() > 0) {
        Anchor firstAnchor = anchors.first();
        Anchor lastAnchor = anchors.last();
        for (String layerId : setStartEndLayers) {
          for (Annotation a : graph.all(layerId)) {
            a.setStartId(firstAnchor.getId());
            a.setEndId(lastAnchor.getId());
          } // next annotation
        } // next layer
      } // there are anchors

      graph.commit();
      return graph;
    } catch(SQLException exception) {
      System.err.println("SQL error: " + exception);
      exception.printStackTrace(System.err);
      throw new StoreException("Invalid query."); // TODO i18n
    }
  }

  /**
   * Gets a fragment of a transcript, given its ID and the ID of an annotation in it that
   * defines the  desired fragment.
   * <p>The given annotation defines both the start and end anchors of the fragment, and also 
   * which annotations on descendant layers will be included. 
   * So the resulting fragment will include:
   * <ul>
   *  <li>the given defining annotation</li>
   *  <li>its parent annotation, and all ancestors, but their anchors are included only if the 
   *      defining annotation t-includes the ancestor</li>
   *  <li>all descendants of the defining annotation or any of its ancestors, 
   *      which are t-included by the defining annotation (but not annotations
   *      that the defining annotation t-includes but which aren't directly related,
   *      so in the case of simultaneous speech, only the words of the speaker of the defining
   *      utterance will be included, not words spoken by other speakers.
   *      </li>
   * </ul>
   * @param transcriptId The ID of the transcript.
   * @param annotationId The ID of an annotation that defines the bounds of the fragment.
   * @return The identified transcript fragment.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   * @throws GraphNotFoundException If the transcript was not found in the store.
   */
  public Graph getFragment(String transcriptId, String annotationId) 
    throws StoreException, PermissionException, GraphNotFoundException {
    return getFragment(transcriptId, annotationId, getLayerIds());
  }

  /**
   * Converts an annotation-matching expression into a resultset (SELECT selectedClause FROM... WHERE... orderClause).
   * <ul>
   *  <li><code>id == 'ew_0_456'</code></li>
   *  <li><code>['ew_2_456', 'ew_2_789', 'ew_2_101112'].includes(id)</code></li>
   *  <li><code>layerId == 'orthography' &amp;&amp; !/th[aeiou].+/.test(label)</code></li>
   *  <li><code>layer.id == 'orthography' &amp;&amp; first('participant').label == 'Robert'
   *            &amp;&amp; first('utterances').start.offset = 12.345</code> - TODO</li> 
   *  <li><code>graph.id == 'AdaAicheson-01.trs' &amp;&amp; layer.id == 'orthography' &amp;&amp;
   * start.offset &gt; 10.5</code></li> 
   *  <li><code>layer.id == 'utterance' &amp;&amp; all('word').includes('ew_0_456')</code></li>
   *  <li><code>previous.id = 'ew_0_456'</code> - TODO</li>
   *  <li><code>layerId = 'utterance' &amp;&amp; labels('orthography').includes('foo')</code></li>
   *  <li><code>layerId = 'utterance' &amp;&amp; labels('participant').includes('Ada')</code></li>
   * </ul>
   * <p><em>NB</em> all expressions must match by either id or layer.id.
   * @param expression The annotation-matching expression.
   * @param limit SQL LIMIT clause, if any. If this is "COUNT(*)" then the returned query
   * counts matches instead of listing them.
   * @return A PreparedStatement for the given expression, with parameters already set.
   * @throws SQLException
   * @throws StoreException If the expression is invalid.
   */
  private PreparedStatement annotationMatchSql(String expression, String limit)
    throws SQLException, StoreException, PermissionException {
    // read-only users get access to only public attributes
    boolean publicOnly = !getUserRoles().contains("edit");
    Schema schema = getSchema();
    String sSql = null;
    Layer layer = null;
    Pattern wordBasedQueryPattern = Pattern.compile(
      // TODO change hardcoded schema.wordLayerId
      // TODO use first instead of my
      "my\\('word'\\)\\.id ==? 'ew_0_(\\d+)' (AND|&&) layer\\.id ==? '([^']+)'");
    Matcher wordBasedQueryMatcher = wordBasedQueryPattern.matcher(expression);
    if (wordBasedQueryMatcher.matches()) {
      // optimization for common word_annotation_id-based queries may be possible
      String word_annotation_id = wordBasedQueryMatcher.group(1);
      String layerId = wordBasedQueryMatcher.group(3);
      layer = schema.getLayer(layerId);
      if (layer.getParentId().equals(schema.getWordLayerId()) // word layer
          || (layer.getParent() != null
              && layer.getParent().getParentId() != null
              && layer.getParent().getParentId().equals(
                schema.getWordLayerId()))) { // segment child
        // table has word_annotation_id
        String select = "DISTINCT annotation.*, '"+QL.Esc(layerId)+"' AS layer,"
          +" graph.transcript_id AS graph";
        if (limit.equals("COUNT(*)")) {
          select = "COUNT(*), '"+QL.Esc(layerId)+"' AS layer";
          limit = "";
        }
            
        sSql = "SELECT " + select
          +" FROM annotation_layer_"+layer.get("layer_id")+" annotation"
          +" INNER JOIN transcript graph ON annotation.ag_id = graph.ag_id"
          +" WHERE word_annotation_id = " + word_annotation_id
          + userWhereClauseGraph("AND", "graph")
          +" ORDER BY annotation.parent_id, annotation.ordinal, annotation_id"
          + " " + limit;
      } else if ("transcript".equals(layer.get("class_id"))) { // transcript attribute
        if (!publicOnly || "1".equals(layer.get("access"))) {
          String select = "DISTINCT annotation.annotation_id, annotation.ag_id,"
            +" annotation.label, annotation.label_status,"
            +" annotation.annotated_by, annotation.annotated_when,"
            +" NULL AS start_anchor_id, NULL AS end_anchor_id,"
            +" '"+QL.Esc(layerId)+"' AS layer, graph.transcript_id AS graph";
          if (limit.equals("COUNT(*)")) {
            select = "COUNT(*), '"+QL.Esc(layerId)+"' AS layer";
            limit = "";
          }
          
          sSql = "SELECT " + select
            +" FROM annotation_transcript annotation"
            +" INNER JOIN transcript graph ON annotation.ag_id = graph.ag_id"
            +" INNER JOIN annotation_layer_0 word ON word.ag_id = graph.ag_id"
            +" WHERE word.annotation_id = " + word_annotation_id
            +" AND layer = '"+QL.Esc(""+layer.get("attribute"))+"'"
            + userWhereClauseGraph("AND", "graph")
            +" ORDER BY annotation_id"
            + " " + limit;
        } else { // no access to layer
          if (limit.equals("COUNT(*)")) {
            sSql = "SELECT 0, '"+QL.Esc(layerId)+"' AS layer";
          } else {
            sSql = "SELECT NULL AS annotation_id, NULL AS speaker_number,"
              +" NULL AS label, NULL AS label_status,"
              +" NULL AS annotated_by, NULL AS annotated_when,"
              +" NULL AS start_anchor_id, NULL AS end_anchor_id,"
              +" '"+QL.Esc(layerId)+"' AS layer, NULL AS graph";
          }
        }
      } else if ("speaker".equals(layer.get("class_id"))) { // participant attribute
        if (!publicOnly || "1".equals(layer.get("access"))) {
          String select = "DISTINCT annotation.annotation_id, annotation.speaker_number,"
            +" annotation.label, annotation.label_status,"
            +" annotation.annotated_by, annotation.annotated_when,"
            +" NULL AS start_anchor_id, NULL AS end_anchor_id,"
            +" '"+QL.Esc(layerId)+"' AS layer, NULL AS graph";
          if (limit.equals("COUNT(*)")) {
            select = "COUNT(*), '"+QL.Esc(layerId)+"' AS layer";
            limit = "";
          }
          
          sSql = "SELECT " + select
            +" FROM annotation_layer_0 word"
            +" INNER JOIN annotation_layer_11 turn ON turn.annotation_id = word.turn_annotation_id"
            +" INNER JOIN annotation_participant annotation ON turn.label = annotation.speaker_number"
            +" WHERE word.annotation_id = " + word_annotation_id
            +" AND layer = '"+QL.Esc(""+layer.get("attribute"))+"'"
            + userWhereClauseGraph("AND", "graph")
            +" ORDER BY annotation_id"
            + " " + limit;
        } else { // no access to layer
          if (limit.equals("COUNT(*)")) {
            sSql = "SELECT 0, '"+QL.Esc(layerId)+"' AS layer";
          } else {
            sSql = "SELECT NULL AS annotation_id, NULL AS speaker_number,"
              +" NULL AS label, NULL AS label_status,"
              +" NULL AS annotated_by, NULL AS annotated_when,"
              +" NULL AS start_anchor_id, NULL AS end_anchor_id,"
              +" '"+QL.Esc(layerId)+"' AS layer, NULL AS graph";
          }
        }
      } else if (layer.getParentId().equals(schema.getRoot().getId())
                 && layer.getAlignment() == Constants.ALIGNMENT_INTERVAL) { // freeform layer
        String select =
          "DISTINCT annotation.*, '"+QL.Esc(layerId)+"' AS layer, graph.transcript_id AS graph,"
          // these required because they're in the order clause:
          +" start.offset, end.offset, parent_id, annotation_id";
        String order = " ORDER BY start.offset, end.offset DESC, parent_id, annotation_id";
        if (limit.equals("COUNT(*)")) {
          select = "COUNT(*), '"+QL.Esc(layerId)+"' AS layer";
          limit = "";
          order = "";
        }
            
        sSql = "SELECT " + select
          +" FROM annotation_layer_"+layer.get("layer_id")+" annotation"
          +" INNER JOIN transcript graph ON annotation.ag_id = graph.ag_id"
          +" INNER JOIN anchor start ON annotation.start_anchor_id = start.anchor_id"
          +" INNER JOIN anchor end ON annotation.end_anchor_id = end.anchor_id"
          +" WHERE " + word_annotation_id + " IN"
          +" (SELECT word.annotation_id"
          +" FROM annotation_layer_0 word"
          +" INNER JOIN anchor word_start"
          +" ON word.start_anchor_id = word_start.anchor_id"
          +" INNER JOIN anchor word_end"
          +" ON word.end_anchor_id = word_end.anchor_id"
          +" WHERE word.ag_id = annotation.ag_id"
          +" AND word_start.offset <= end.offset"
          +" AND start.offset <= word_end.offset)"
          + userWhereClauseGraph("AND", "graph")
          + order
          + " " + limit;
      } // freeform layer
    } // optimization for common word_annotation_id-based queries may be possible 

    Pattern transcriptAttributeQueryPattern = Pattern.compile(
      "graph\\.id ==? '(.+)' (AND|&&) layer\\.id ==? '(transcript_[^']+)'");
    Matcher transcriptAttributeQueryMatcher
      = transcriptAttributeQueryPattern.matcher(expression);
    if (transcriptAttributeQueryMatcher.matches()) {
      // optimization for common id/attribute-based queries may be possible
      String transcriptId = transcriptAttributeQueryMatcher.group(1);
      String layerId = transcriptAttributeQueryMatcher.group(3);
      layer = schema.getLayer(layerId);
      if ("transcript".equals(layer.get("class_id")) // transcript attribute
          // read-only users get access to only public attributes
          && ("1".equals(layer.get("access")) || getUserRoles().contains("edit")))
      {

        String select = "DISTINCT annotation.annotation_id, annotation.ag_id,"
          +" annotation.label, annotation.label_status,"
          +" annotation.annotated_by, annotation.annotated_when,"
          +" NULL AS start_anchor_id, NULL AS end_anchor_id,"
          +" '"+QL.Esc(layerId)+"' AS layer, graph.transcript_id AS graph";
        if (limit.equals("COUNT(*)")) {
          select = "COUNT(*), '"+QL.Esc(layerId)+"' AS layer";
          limit = "";
        }
            
        sSql = "SELECT " + select
          +" FROM annotation_transcript annotation"
          +" INNER JOIN transcript graph ON annotation.ag_id = graph.ag_id"
          +" WHERE graph.transcript_id = '" + QL.Esc(transcriptId) + "'"
          +" AND layer = '"+QL.Esc(""+layer.get("attribute"))+"'"
          + userWhereClauseGraph("AND", "graph")
          +" ORDER BY annotation_id"
          + " " + limit;
      } // table has word_annotation_id, so use it and save a JOIN
    } // optimization for common id/attribute-based queries may be possible
      
    Pattern wordUtteranceQueryPattern = Pattern.compile(
      "layer.id +==? +'utterance' +&& +all\\('word'\\)\\.includes\\('ew_0_(\\d+)'\\)");
    Matcher wordUtteranceQueryMatcher
      = wordUtteranceQueryPattern.matcher(expression);
    if (wordUtteranceQueryMatcher.matches()) {
      // optimization for common id/attribute-based queries may be possible
      String wordId = wordUtteranceQueryMatcher.group(1);
      String select = "DISTINCT annotation.annotation_id, annotation.ag_id,"
        +" annotation.label, annotation.label_status,"
        +" annotation.annotated_by, annotation.annotated_when,"
        +" NULL AS start_anchor_id, NULL AS end_anchor_id,"
        +" 'utterance' AS layer, graph.transcript_id AS graph,"
        +" annotation.turn_annotation_id, annotation.ordinal";
        if (limit.equals("COUNT(*)")) {
          select = "COUNT(*), 'utterance' AS layer";
          limit = "";
        }
            
        sSql = "SELECT " + select
          +" FROM annotation_layer_12 annotation"
          +" INNER JOIN transcript graph ON annotation.ag_id = graph.ag_id"
          +" INNER JOIN anchor start ON annotation.start_anchor_id = start.anchor_id"
          +" INNER JOIN anchor end ON annotation.end_anchor_id = end.anchor_id"
          +" INNER JOIN annotation_layer_0 word"
          +" ON annotation.turn_annotation_id = word.turn_annotation_id"
          +" INNER JOIN anchor word_start ON word.start_anchor_id = word_start.anchor_id"
          +" WHERE word.annotation_id = '" + wordId + "'"
          +" AND word_start.offset BETWEEN start.offset AND end.offset"
          + userWhereClauseGraph("AND", "graph")
          +" ORDER BY annotation.parent_id, annotation.ordinal, annotation_id"
          + " " + limit; 
    } // optimization for utterance given word ID query
      
    if (sSql == null) { // no optimization found

      if (limit == null) limit = "";
      String select = "DISTINCT annotation.*, graph.transcript_id AS graph";
      if (limit.equals("COUNT(*)")) {
        select = "COUNT(*)";
        limit = "";
      }
      AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
      AnnotationAgqlToSql.Query query = transformer.sqlFor(
        expression, select, userWhereClauseGraph("", "graph"), limit);
      sSql = query.sql;
    } // no optimization found
      
    // System.out.println("QL: " + expression);
    // System.out.println("SQL: " + sSql);
    PreparedStatement sql = getConnection().prepareStatement(sSql);
    return sql;
  } // end of annotationMatchSql()

  /**
   * Gets the number of annotations on the given layer of the given transcript, but only
   * those with an ordinal less than or equal to the given maximum. 
   * @param id The ID of the transcript.
   * @param layerId The ID of the layer.
   * @param maxOrdinal The maximum ordinal for the counted annotations.
   * e.g. a <var>maxOrdinal</var> of 1 will ensure that only the first annotation for each
   * parent is couned. If <var>maxOrdinal</var> is null, then all annotations are
   * counted, regardless of their ordinal.
   * @return A (possibly empty) array of annotations.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   * @throws GraphNotFoundException If the transcript was not found in the store.
   */
  public long countAnnotations(String id, String layerId, Integer maxOrdinal)
    throws StoreException, PermissionException, GraphNotFoundException {
    return countMatchingAnnotations(
      "graph.id = '" + QL.Esc(id) + "' AND layer.id = '" + QL.Esc(layerId) + "'"
      +(maxOrdinal==null?"":" AND ordinal <= " + maxOrdinal));
  }
  /**
   * Gets the annotations on the given layer of the given transcript, but only those with
   * an ordinal less than or equal to the given maximum..
   * @param id The ID of the transcript.
   * @param layerId The ID of the layer.
   * @param maxOrdinal The maximum ordinal for the returned annotations.
   * e.g. a <var>maxOrdinal</var> of 1 will ensure that only the first annotation for each
   * parent is returned. If <var>maxOrdinal</var> is null, then all annotations are
   * returned, regardless of their ordinal.
   * @param pageLength The maximum number of IDs to return, or null to return all.
   * @param pageNumber The page number to return, or null to return the first page.
   * @return A (possibly empty) array of annotations.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   * @throws GraphNotFoundException If the transcript was not found in the store.
   */
  public Annotation[] getAnnotations(
    String id, String layerId, Integer maxOrdinal, Integer pageLength, Integer pageNumber)
    throws StoreException, PermissionException, GraphNotFoundException {
    Annotation[] annotations = getMatchingAnnotations(
      "graph.id = '" + QL.Esc(id) + "' AND layer.id = '" + QL.Esc(layerId) + "'"
      +(maxOrdinal==null?"":" AND ordinal <= " + maxOrdinal), pageLength, pageNumber);
    Layer layer = getLayer(layerId);
    if (layer != null && layer.getType().indexOf('/') > 0) { // a MIME type
      // the annotations may have files
      // do they have access to them?
      try {
        if (hasAccess(id, new MediaFile().setMimeType(layer.getType()).getType().substring(0,1))) {
          String[] layers = { "corpus", "episode" };
          Graph graph = getTranscript(id, layers);
          for (Annotation annotation : annotations) {
            // is there a data file associated with the annotation?
            try {
              String dataUrl = urlForAnnotationFile(annotation, graph, layer.getType());
              if (dataUrl != null) annotation.put("dataUrl", dataUrl);
            } catch (Exception x) {
              System.err.println("Error getting dataUrl for "+annotation.getId() + ": " + x);
            }            
          } // next annotation
        } // they have access 
      } catch (SQLException x) {
        System.err.println(
          "Error getting media access info for layer "+layerId + " ("+layer.getType()+"): " + x);
      }            
    }
    return annotations;
  }
   
  /**
   * Counts the number of annotations that match a particular pattern.
   * @param expression An expression that determines which participants match.
   * @return The number of matching annotations.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   */
  public int countMatchingAnnotations(String expression)
    throws StoreException, PermissionException {
    try {
      PreparedStatement sql = annotationMatchSql(expression, "COUNT(*)");
      ResultSet rs = sql.executeQuery();
      try {
        rs.next();
        return rs.getInt(1);
      } finally {
        rs.close();
        sql.close();
      }
    } catch(SQLException exception) {
      System.err.println("SQL error from expression \""+expression+"\": " + exception);
      exception.printStackTrace(System.err);
      throw new StoreException("Invalid query."); // TODO i18n
    }
  }

  /**
   * Gets a list of annotations that match a particular pattern.
   * @param expression An expression that determines which transcripts match.
   * @param pageLength The maximum number of annotations to return, or null to return all.
   * @param pageNumber The page number to return, or null to return the first page.
   * @return A list of matching {@link Annotation}s.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   */
  public Annotation[] getMatchingAnnotations(
    String expression, Integer pageLength, Integer pageNumber)
    throws StoreException, PermissionException {
    return getMatchingAnnotations(expression, pageLength, pageNumber, false);
  }

  /**
   * Gets a list of annotations that match a particular pattern.
   * @param expression An expression that determines which transcripts match.
   * @param pageLength The maximum number of annotations to return, or null to return all.
   * @param pageNumber The page number to return, or null to return the first page.
   * @param setGraph true to include the graph of all returned annotations, false otherwise.
   * @return A list of matching {@link Annotation}s.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   */
  public Annotation[] getMatchingAnnotations(
    String expression, Integer pageLength, Integer pageNumber, boolean setGraph)
    throws StoreException, PermissionException {

    Schema schema = getSchema();
    try {
      String limit = "";
      if (pageLength != null) {
        if (pageNumber == null) pageNumber = 0;
        limit = " LIMIT " + (pageNumber * pageLength) + "," + pageLength;
      }
      PreparedStatement sql = annotationMatchSql(expression, limit);

      ResultSet rsAnnotation = sql.executeQuery();
      Layer layer = null;
      Graph graph = null;
      Vector<Annotation> annotations = new Vector<Annotation>();
      String[] defaultGraphLayers = { "corpus", "episode" };
      while (rsAnnotation.next()) {
        if (layer == null) { // they'll all be on the same layer
          layer = schema.getLayer(rsAnnotation.getString("layer"));
        }
        if (setGraph // the caller wants the graph
            || "F".equals(layer.get("scope")) // freeform layer
            || layer.getId().equals(schema.getUtteranceLayerId())
            || layer.getId().equals(schema.getTurnLayerId())
            || layer.getId().equals(schema.getParticipantLayerId())
            || "transcript".equals(layer.get("class_id"))) { // transcript attribute
          // we need graph for annotationFromResult	       
          if (graph == null || !graph.getId().equals(rsAnnotation.getString("graph"))) {
            // graph can change
            try {
              graph = getTranscript(rsAnnotation.getString("graph"), defaultGraphLayers);
            } catch(GraphNotFoundException exception) {
              System.err.println(
                "getMatchingAnnotations: " + expression + " : " + exception);
              continue;
            }
          } // need graph
        } // freeform/turn/utterance
        Annotation annotation = annotationFromResult(rsAnnotation, layer, graph);
        if (layer.getId().equals(schema.getUtteranceLayerId())
            || layer.getId().equals(schema.getTurnLayerId())) {
          // label is speaker.speaker_number but should be speaker.name
          String id = "m_-2_" + annotation.getLabel();
          Annotation participant = getParticipant(id);
          if (participant != null) annotation.setLabel(participant.getLabel());
        }
        annotations.add(annotation);
      } // next annotation
      rsAnnotation.close();
      sql.close();
	 
      return annotations.toArray(new Annotation[0]);	 
    } catch(SQLException exception) {
      System.err.println("SQL error from expression \""+expression+"\": " + exception);
      exception.printStackTrace(System.err);
      throw new StoreException("Invalid query."); // TODO i18n
    }
  }

  /**
   * Identifies a list of annotations that match a particular pattern, and aggregates
   * their labels. 
   * <p> This allows for counting, listing distinct labels, etc.
   * @param operation The aggregation operation(s) - e.g. 
   * <dl>
   *  <dt> DISTINCT </dt><dd> List the distinct labels (case/accent insensitive). </dd>
   *  <dt> DISTINCT BINARY </dt><dd> List the distinct labels (case/accent sensitive). </dd>
   *  <dt> MAX </dt><dd> Return the highest label. </dd>
   *  <dt> MIN </dt><dd> Return the lowest label. </dd>
   *  <dt> COUNT </dt><dd> Return the number of annotations. </dd>
   *  <dt> COUNT DISTINCT </dt><dd> Return the number of distinct labels (case/accent insensitive). </dd>
   *  <dt> COUNT DISTINCT BINARY</dt><dd> Return the number of distinct labels (case/accent sensitive). </dd>
   * </dl>
   * More than one operation can be specified, by using a comma delimiter. 
   * e.g. "DISTINCT,COUNT" will return each distinct label, followed by its count
   * (i.e. the array will have twice the number of elements as there are distinct words,
   * even-indexed elements are the word labels, and odd-indexed elements are the counts).
   * @param expression An expression that determines which annotations match.
   * <p> The expression language is loosely based on JavaScript; expressions such as the
   * following can be used: 
   * <ul>
   *  <li><code>layer.id == 'orthography'</code></li>
   *  <li><code>graph.id == 'AdaAicheson-01.trs' &amp;&amp; layer.id == 'orthography'</code></li> 
   * </ul>
   * <p><em>NB</em> all expressions must match by either id or layer.id.
   * @return A list of results. This may have a single element (e.g. when
   * <var>operation</var> == <q>COUNT</q>), or may be a (long) list of labels (e.g. when
   * <var>operation</var> == <q>DISTINCT</q>. If there are multiple operations then the
   * array will contain a multiple of the number of matching annotations. 
   * (e.g. if <var>operation</var> == <q>DISTINCT,COUNT</q> then the array will have
   * twice the number of elements as there are distinct words, even-indexed elements are
   * the word labels, and odd-indexed elements are the counts.) 
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   */
  public String[] aggregateMatchingAnnotations(String operation, String expression)
    throws StoreException, PermissionException {
    String select = "COUNT(*)";
    String suffix = "";
    if (operation.equalsIgnoreCase("DISTINCT")) {
      select = "DISTINCT annotation.label";
      suffix = " ORDER BY annotation.label";
    } else if (operation.equalsIgnoreCase("DISTINCT BINARY")) { // case/accent sensitive
      select = "DISTINCT annotation.label COLLATE utf8mb4_bin";
      suffix = " ORDER BY annotation.label COLLATE utf8mb4_bin";
    } else if (operation.equalsIgnoreCase("MAX")) {
      select = "MAX(annotation.label)";
      suffix = " ORDER BY annotation.label";
    } else if (operation.equalsIgnoreCase("MIN")) {
      select = "MIN(annotation.label)";
      suffix = " ORDER BY annotation.label";
    } else if (operation.equalsIgnoreCase("COUNT DISTINCT")) {
      select = "COUNT(DISTINCT annotation.label)";
      suffix = "";
    } else if (operation.equalsIgnoreCase("COUNT DISTINCT BINARY")) { // case/accent sensitive
      select = "COUNT(DISTINCT annotation.label COLLATE utf8mb4_bin)";
      suffix = "";
    } else if (operation.equalsIgnoreCase("DISTINCT,COUNT")) {
      select = "annotation.label, COUNT(*)";
      suffix = " GROUP BY annotation.label";
    } else if (operation.equalsIgnoreCase("DISTINCT BINARY,COUNT")) {
      select = "annotation.label COLLATE utf8mb4_bin, COUNT(*)";
      suffix = " GROUP BY annotation.label COLLATE utf8mb4_bin";
    }
    AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
    AnnotationAgqlToSql.Query query = transformer.sqlFor(
      expression, select, userWhereClauseGraph("", "graph"), "");
    String sSql = query.sql
      // remove layer expression from SELECT clause
      .replaceAll(", '.+' AS layer(, start.offset, end.offset)? FROM"," FROM")
      // remove ORDER BY clause if any
      .replaceAll(" ORDER BY [a-z_., ]+$","")
      // add our own ORDER/GROUP BY if any
      + suffix;
    // System.out.println("Aggregate QL: ("+operation+") " + expression);
    // System.out.println("Aggregate SQL: " + sSql);
    try {
      PreparedStatement sql = getConnection().prepareStatement(sSql);
      ResultSet rs = sql.executeQuery();
      try {
        int fieldsPerRow = rs.getMetaData().getColumnCount();
        Vector<String> values = new Vector<String>();
        while (rs.next()) {
          for (int f = 1; f <= fieldsPerRow; f++) {
            values.add(rs.getString(f));
          } // next field
        } // next row
        return values.toArray(new String[0]);
      } finally {
        rs.close();
        sql.close();
      }
    } catch(SQLException exception) {
      System.err.println("SQL error from expression \""+expression+"\": " + exception);
      exception.printStackTrace(System.err);
      throw new StoreException("Invalid query."); // TODO i18n
    }
  }

  /**
   * Gets the annotations on given layers for a set of match IDs.
   * @param matchIds An iterator that supplies match IDs - these may be the contents of
   * the MatchId column in exported search results, token URLs, or annotation IDs.
   * @param layerIds The layer IDs of the layers to get.
   * @param targetOffset Which token to get the annotations of; 0 means the match target
   * itself, 1 means the token after the target, -1 means the token before the target, etc. 
   * @param annotationsPerLayer The number of annotations per layer to get; if there's a
   * smaller number of annotations available, the unfilled array elements will be null.
   * @param consumer A consumer for handling the resulting
   * annotations. Consumer.accept() will be invoked once for each element returned by the
   * <var>matchIds</var> iterator, with an array of {@link Annotation} objects. The size
   * of this array will be <var>layerIds.length</var> * <var>annotationsPerLayer</var>,
   * and will be filled in with the available annotations for each layer; when
   * annotations are not available, null is supplied.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   */
  public void getMatchAnnotations(Iterator<String> matchIds, String[] layerIds, int targetOffset, int annotationsPerLayer, Consumer<Annotation[]> consumer)
    throws StoreException, PermissionException {
    if (annotationsPerLayer == 0) return; // they don't want any annotations
    LinkedHashMap<String,Integer> layerIdToMaxAnnotations = new LinkedHashMap<String,Integer>();
    Integer maxAnnotationsPerLayer = Integer.valueOf(annotationsPerLayer);
    for (String id : layerIds) layerIdToMaxAnnotations.put(id, maxAnnotationsPerLayer);
    getMatchAnnotations(matchIds, layerIdToMaxAnnotations, null, null, targetOffset, consumer);
  }
  
  /**
   * Gets the annotations on given layers for a set of match IDs.
   * @param matchIds An iterator that supplies match IDs - these may be the contents of
   * the MatchId column in exported search results, token URLs, or annotation IDs.
   * @param layerIdToMaxAnnotations A map where the keys are the layer IDs of the layers
   * to get, and each value is the maximum number of annotations to retrieve for that layer.
   * @param anchorStartLayers A set of layer IDs identifying which layer annotations must
   * share a start anchor with the target.
   * @param anchorEndLayers A set of layer IDs identifying which layer annotations must
   * share a end anchor with the target.
   * @param targetOffset Which token to get the annotations of; 0 means the match target
   * itself, 1 means the token after the target, -1 means the token before the target, etc. 
   * @param consumer A consumer for handling the resulting
   * annotations. Consumer.accept() will be invoked once for each element returned by the
   * <var>matchIds</var> iterator, with an array of {@link Annotation} objects. The size
   * of this array will be the sum of the values in <var>layerIds</var>,
   * and will be filled in with the available annotations for each layer; when
   * annotations are not available, null is supplied.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   */
  public void getMatchAnnotations(
    Iterator<String> matchIds, Map<String,Integer> layerIdToMaxAnnotations,
    Set<String> anchorStartLayers, Set<String> anchorEndLayers, int targetOffset,
    Consumer<Annotation[]> consumer)
    throws StoreException, PermissionException {
    if (matchIds == null || !matchIds.hasNext()) return; // there were no IDs
    if (layerIdToMaxAnnotations == null) return; // they don't want any layers
    if (consumer == null) return; // they don't want the results
    if (anchorStartLayers == null) anchorStartLayers = Collections.emptySet(); 
    if (anchorEndLayers == null) anchorEndLayers = Collections.emptySet(); 
      
    Schema schema = getSchema();

    String matchId = matchIds.next();
      
    // we'll assume that the first matchId is representative of the rest;
    // specifically, that the target layer is the same for all matches.
    // TODO remove the assumption that the first target layer is the same as the rest
 
    // we'll need a pattern to extract the parts of the ID

    // first try the normal MatchId pattern, of the form:
    // g_3;em_12_2671;n_19877-n_19939;p_4;#=es_1_17;prefix=0001-;[0]=ew_0_12574
    // where:
    // - g_3 identifies the graph
    // - em_12_2671 identifies the line
    // - p_4 identifies the participant
    // - #=es_1_17 identifies the target
    // - [0]=ew_0_12574 identifies the first word in the match
    Pattern matchIdPattern = Pattern.compile(
      "g_(\\d+);em_12_(\\d+);n_\\d+-n_\\d+;p_(\\d+);#=e.?_(\\d+)_(\\d+);.*\\[0\\]=ew_0_(\\d+)($|;.*)");
    // so groups are:
    Integer ag_id_group = 1;
    Integer utterance_annotation_id_group = 2;
    Integer participant_speaker_number_group = 3;
    Integer target_layer_id_group = 4;
    Integer target_annotation_id_group = 5;
    Integer first_word_annotation_id_group = 6;
    Matcher idMatcher = matchIdPattern.matcher(matchId);
    if (!idMatcher.matches()) {
      // not IdMatch - maybe they've passed in the URL
      // something like:
      // http://localhost:8080/labbcat/transcript?ag_id=6#ew_0_16783
      matchIdPattern = Pattern.compile(
        "https?://.+/transcript\\?ag_id=(\\d+)#e.?_(\\d+)_(\\d+)");
      idMatcher = matchIdPattern.matcher(matchId);
      if (idMatcher.matches()) { // URL pattern matches
        ag_id_group = 1;
        utterance_annotation_id_group = null;
        participant_speaker_number_group = null;
        target_layer_id_group = 2;
        target_annotation_id_group = 3;
        first_word_annotation_id_group = 3;
      } else {
        // URL something like:
        // http://localhost:8080/labbcat/transcript?transcript=foo.trs#ew_0_16783
        matchIdPattern = Pattern.compile(
          "https?://.+/transcript\\?transcript=(.+)#e.?_(\\d+)_(\\d+)");
        idMatcher = matchIdPattern.matcher(matchId);
        if (idMatcher.matches()) { // URL pattern matches
          ag_id_group = null; // TODO a way to include name!
          utterance_annotation_id_group = null;
          participant_speaker_number_group = null;
          target_layer_id_group = 2;
          target_annotation_id_group = 3;
          first_word_annotation_id_group = 3;
        } else {
          // not IdMatch or URL - maybe they've passed in annotation UIDs
          // something like:
          // "em_12_2671" or "ew_0_16783"
          matchIdPattern = Pattern.compile("e.?_(\\d+)_(\\d+)");
          idMatcher = matchIdPattern.matcher(matchId);
          if (idMatcher.matches()) { // UID pattern matches
            ag_id_group = null;
            utterance_annotation_id_group = null;
            participant_speaker_number_group = null;
            target_layer_id_group = 1;
            target_annotation_id_group = 2;
            first_word_annotation_id_group = 2;
          } else {
            throw new StoreException("Malformed ID: " + matchId);
          }
        }
      }
    } // MatchId pattern matches
    Integer target_layer_id = Integer.valueOf(idMatcher.group(target_layer_id_group));
    Layer targetLayer = schema.getWordLayer();
    if (target_layer_id != 0) { // scan for the layer with the matching layer_id
      for (Layer l : schema.getLayers().values()) {
        if (target_layer_id.equals(l.get("layer_id"))) {
          targetLayer = l;
          break;
        }
      } // next layer
    }
      
    // we'll create a list of prepared queries, one for each layerId, corresponding to the
    // method for getting that layer's annotations.
    HashMap<String,PreparedStatement> queries = new HashMap<String,PreparedStatement>();
    // in some cases, if the main query reqturns no results, there's an alternative query to
    // try, e.g. for 'next segment', if the target is the last segment of the word, we can
    // return the first segment of the next word.
    HashMap<String,PreparedStatement> altQueries = new HashMap<String,PreparedStatement>();

    // we'll also need, for each query, a list of query parameters
    // String are literal, Integers are match group numbers
    HashMap<String,Object[]> parameterGroups = new HashMap<String,Object[]>();
    // ...and parameters for altQueries
    HashMap<String,Object[]> altParameterGroups = new HashMap<String,Object[]>();

    // track the layers too
    HashMap<String,Layer> layers = new HashMap<String,Layer>();

    try {
      // for each layer
      for (String layerId : layerIdToMaxAnnotations.keySet()) {
        Layer layer = schema.getLayer(layerId);
        assert layer != null : "layer != null - layerId: " + layerId;
        layers.put(layerId, layer);
        if (targetLayer.containsKey("layer_id") // target is segment
            && targetLayer.get("layer_id").equals(Integer.valueOf(SqlConstants.LAYER_SEGMENT))
            // segment child:
            && (layer.getParentId() != null && layer.getParentId().equals(targetLayer.getId())
                || layer.getId().equals(targetLayer.getId()))) {// or the segment layer itself
          // target is segment layer and layer is segment child
          Integer layer_id = (Integer)layer.get("layer_id");
          String sql = "SELECT DISTINCT annotation.*, ? AS layer, annotation.ag_id AS graph"
            +" FROM annotation_layer_"+layer_id+" annotation"
            +(!anchorStartLayers.contains(layer.getId())
              && !anchorEndLayers.contains(layer.getId())?"":
              " INNER JOIN annotation_layer_"+targetLayer.get("layer_id")+" target"
              +" ON annotation.segment_annotation_id = target.annotation_id")
            +(!anchorStartLayers.contains(layer.getId())?"":
              " AND annotation.start_anchor_id = target.start_anchor_id")
            +(!anchorEndLayers.contains(layer.getId())?"":
              " AND annotation.end_anchor_id = target.end_anchor_id")
            +" WHERE annotation.segment_annotation_id = ?"
            +" ORDER BY annotation.ordinal, annotation.annotation_id"
            +" LIMIT 0, " + layerIdToMaxAnnotations.get(layerId);
          if (targetOffset != 0) {
            sql = "SELECT DISTINCT annotation.*, ? AS layer, annotation.ag_id AS graph"
              +" FROM annotation_layer_"+layer_id+" annotation"
              +" INNER JOIN annotation_layer_"+targetLayer.get("layer_id")+" target"
              +" ON annotation.segment_annotation_id = target.annotation_id"
              +" INNER JOIN annotation_layer_"+targetLayer.get("layer_id")+" token"
              +" ON target.word_annotation_id = token.word_annotation_id"
              +" AND target.ordinal_in_word = token.ordinal_in_word + "+targetOffset
              +(!anchorStartLayers.contains(layer.getId())?"":
                " AND token.start_anchor_id = target.start_anchor_id")
              +(!anchorEndLayers.contains(layer.getId())?"":
                " AND token.end_anchor_id = target.end_anchor_id")
              +" WHERE token.annotation_id = ?"
              +" ORDER BY annotation.ordinal, annotation.annotation_id"
              +" LIMIT 0, " + layerIdToMaxAnnotations.get(layerId);
          }
          Object[] groups = { layer.getId(), target_annotation_id_group };
          queries.put(layerId, getConnection().prepareStatement(sql));
          parameterGroups.put(layerId, groups);
          if (targetOffset != 0 // if we're looking for neighboring segments
              && layer.get("layer_id").equals( // and we're after the segment itself
                Integer.valueOf(SqlConstants.LAYER_SEGMENT))
              && !anchorStartLayers.contains(layer.getId())  // and not anchoring to start...
              && !anchorEndLayers.contains(layer.getId())) { // ...nor end
            // and the query above doesn't return rows, it's because we hit the word boundary
            // but we can look into the neighboring word
            if (targetOffset > 0) { // following segment
              sql = "SELECT DISTINCT annotation.*, ? AS layer, annotation.ag_id AS graph"
                +" FROM annotation_layer_"+targetLayer.get("layer_id")+" token"
                // word = the matching segment's word
                +" INNER JOIN annotation_layer_0 word"
                +" ON token.word_annotation_id = word.annotation_id"
                // next_word = the word after that in the same turn
                +" INNER JOIN annotation_layer_0 next_word"
                +" ON next_word.turn_annotation_id = word.turn_annotation_id"
                // next word in the turn
                +" AND next_word.ordinal_in_turn = word.ordinal_in_turn + 1"
                // annotation = the segment in next_word that we're going to return
                +" INNER JOIN annotation_layer_"+targetLayer.get("layer_id")+" annotation"
                +" ON next_word.word_annotation_id = annotation.word_annotation_id"
                // segment.ordinal is the annotation offset
                // (if targetOffset > next_word.all("segment").length,
                //  i.e. if there are fewer segments in the next word than targetOffset,
                // then we get no result, but that's ok, because currently targetOffset
                // is only ever 1)
                +" AND annotation.ordinal_in_word = "+targetOffset
                +" WHERE token.annotation_id = ?"
                +" ORDER BY annotation.ordinal, annotation.annotation_id"
                +" LIMIT 0, " + layerIdToMaxAnnotations.get(layerId);
              Object[] altGroups = { layer.getId(), target_annotation_id_group };
              altQueries.put(layerId, getConnection().prepareStatement(sql));
              altParameterGroups.put(layerId, altGroups);
            } else if (targetOffset == -1) { // immediately prior segment
              sql = "SELECT DISTINCT annotation.*, ? AS layer, annotation.ag_id AS graph"
                +" FROM annotation_layer_"+targetLayer.get("layer_id")+" token"
                +" INNER JOIN annotation_layer_0 word"
                +" ON token.word_annotation_id = word.annotation_id"
                +" INNER JOIN annotation_layer_0 next_word"
                +" ON next_word.turn_annotation_id = word.turn_annotation_id"
                // previous word in the turn
                +" AND next_word.ordinal_in_turn = word.ordinal_in_turn - 1"
                +" INNER JOIN annotation_layer_"+targetLayer.get("layer_id")+" annotation"
                +" ON next_word.word_annotation_id = annotation.word_annotation_id"
                +" WHERE token.annotation_id = ?"
                // list segments in reverse order
                +" ORDER BY annotation.ordinal DESC, annotation.annotation_id"
                // and take the first one - i.e. the last segment of the previous word
                +" LIMIT 1"; // segment.peers == false, so there's only one
              Object[] altGroups = { layer.getId(), target_annotation_id_group };
              altQueries.put(layerId, getConnection().prepareStatement(sql));
              altParameterGroups.put(layerId, altGroups);
            } else { // more distant previous segment TODO
              altQueries.put(layerId, null);
              altParameterGroups.put(layerId, null);
            }
          } else {
            altQueries.put(layerId, null); // there is no alternative query
            altParameterGroups.put(layerId, null);
          }
        } else if (targetLayer.containsKey("layer_id")
                   // target is aligned word child
                   && targetLayer.getParentId().equals(schema.getWordLayerId()) 
                   && targetLayer.getAlignment() == Constants.ALIGNMENT_INTERVAL
                   // layer is also word child
                   && (layer.getParentId() != null
                       && layer.getParentId().equals(targetLayer.getParentId()))) {// word child
          // target is segment layer (or other aligned word child) and layer is word child
          Integer layer_id = (Integer)layer.get("layer_id");
          String sql = "SELECT DISTINCT annotation.*, ? AS layer, annotation.ag_id AS graph,"
            // these required for ORDER BY
            +" annotation_start.offset AS annotation_start_offset,"
            +" annotation_end.offset - annotation_start.offset AS annotation_length"
            +" FROM annotation_layer_"+layer_id+" annotation"
            +" INNER JOIN annotation_layer_"+targetLayer.get("layer_id")+" target"
            +" ON annotation.word_annotation_id = target.word_annotation_id"
            +(!anchorStartLayers.contains(layer.getId())?"":
              " AND annotation.start_anchor_id = target.start_anchor_id")
            +(!anchorEndLayers.contains(layer.getId())?"":
              " AND annotation.end_anchor_id = target.end_anchor_id")
            // annotation anchors
            +" INNER JOIN anchor annotation_start"
            +" ON annotation.start_anchor_id = annotation_start.anchor_id"
            +" INNER JOIN anchor annotation_end"
            +" ON annotation.end_anchor_id = annotation_end.anchor_id"
            // target anchors
            +" INNER JOIN anchor target_start"
            +" ON target.start_anchor_id = target_start.anchor_id"
            // annotation includes target start time
            +" AND annotation_start.offset <= target_start.offset"
            +" AND target_start.offset < annotation_end.offset"
            +" WHERE target.annotation_id = ?"
            +" ORDER BY annotation_start.offset," // earliest first
            +" annotation_end.offset - annotation_start.offset," // then shortest first
            +" annotation.annotation_id"
            +" LIMIT 0, " + layerIdToMaxAnnotations.get(layerId);
          if (targetOffset != 0) {
            sql = "SELECT DISTINCT annotation.*, ? AS layer, annotation.ag_id AS graph,"
              // these required for ORDER BY
              +" annotation_start.offset AS annotation_start_offset,"
              +" annotation_end.offset - annotation_start.offset AS annotation_length"
              +" FROM annotation_layer_"+layer_id+" annotation"
              +" INNER JOIN annotation_layer_"+SqlConstants.LAYER_TRANSCRIPTION+" target"
              +" ON annotation.word_annotation_id = target.annotation_id"
              +" INNER JOIN annotation_layer_"+targetLayer.get("layer_id")+" token"
              +" ON target.turn_annotation_id = token.turn_annotation_id"
              +" AND target.ordinal_in_turn = token.ordinal_in_turn + " + targetOffset
              +(!anchorStartLayers.contains(layer.getId())?"":
                " AND token.start_anchor_id = target.start_anchor_id")
              +(!anchorEndLayers.contains(layer.getId())?"":
                " AND token.end_anchor_id = target.end_anchor_id")
              // annotation anchors
              +" INNER JOIN anchor annotation_start"
              +" ON annotation.start_anchor_id = annotation_start.anchor_id"
              +" INNER JOIN anchor annotation_end"
              +" ON annotation.end_anchor_id = annotation_end.anchor_id"
              // target anchors
              +" INNER JOIN anchor target_start"
              +" ON target.start_anchor_id = target_start.anchor_id"
              // annotation includes target start time
              +" AND annotation_start.offset <= target_start.offset"
              +" AND target_start.offset < annotation_end.offset"
              +" WHERE token.annotation_id = ?"
              +" ORDER BY annotation_start.offset," // earliest first
              +" annotation_end.offset - annotation_start.offset," // then shortest first
              +" annotation.annotation_id"
              +" LIMIT 0, " + layerIdToMaxAnnotations.get(layerId);
          }
          Object[] groups = { layer.getId(), target_annotation_id_group };
          queries.put(layerId, getConnection().prepareStatement(sql));
          parameterGroups.put(layerId, groups);
          altQueries.put(layerId, null); // there is no alternative query
          altParameterGroups.put(layerId, null);
        } else if (layer.equals(schema.getRoot())) { // graph itself
          if (ag_id_group != null) { // the MatchId includes to ag_id
            String sql = "SELECT graph.transcript_id AS label,"
              +" graph.ag_id AS annotation_id,"
              +" 0 AS ordinal, 100 AS label_status,"
              +" NULL AS annotated_by, NULL AS annotated_when,"
              +" NULL AS start_anchor_id, NULL AS end_anchor_id,"
              +" ? AS layer, graph.transcript_id AS graph, graph.ag_id"
              +" FROM transcript graph"
              +" WHERE graph.ag_id = ?";
            Object[] groups = { layer.getId(), ag_id_group };
            queries.put(layerId, getConnection().prepareStatement(sql));
            parameterGroups.put(layerId, groups);
            altQueries.put(layerId, null); // there is no alternative query
            altParameterGroups.put(layerId, null);
          } else { // the MatchId doesn't include to ag_id
            // get the ag_id from the target
            String sql = "SELECT graph.transcript_id AS label,"
              +" graph.ag_id AS annotation_id,"
              +" 0 AS ordinal, 100 AS label_status,"
              +" NULL AS annotated_by, NULL AS annotated_when,"
              +" NULL AS start_anchor_id, NULL AS end_anchor_id,"
              +" ? AS layer, graph.transcript_id AS graph, graph.ag_id"
              +" FROM transcript graph"
              +" INNER JOIN annotation_layer_"+targetLayer.get("layer_id")+" target"
              +" ON graph.ag_id = target.ag_id"
              +" WHERE target.annotation_id = ?";
            Object[] groups = { layer.getId(), target_annotation_id_group };
            queries.put(layerId, getConnection().prepareStatement(sql));
            parameterGroups.put(layerId, groups);
            altQueries.put(layerId, null); // there is no alternative query
            altParameterGroups.put(layerId, null);
          } // the MatchId doesn't include to ag_id
        } else if (layer.isAncestor(schema.getWordLayerId()) // a word child layer
                   || layer.getId().equals(schema.getWordLayerId())) { // or the word layer itself
          // layer table has word_annotation_id
          Integer layer_id = (Integer)layer.get("layer_id");
          if (targetLayer.getId().equals(schema.getWordLayerId())) { // target is word layer
            // target is transcript layer
            String sql = "SELECT DISTINCT annotation.*, ? AS layer, annotation.ag_id AS graph"
              +" FROM annotation_layer_"+layer_id+" annotation"
              +(!anchorStartLayers.contains(layer.getId())
                && !anchorEndLayers.contains(layer.getId())?"":
                " INNER JOIN annotation_layer_"+targetLayer.get("layer_id")+" target"
                +" ON annotation.word_annotation_id = target.word_annotation_id")
              +" WHERE annotation.word_annotation_id = ?"
              +(!anchorStartLayers.contains(layer.getId())?"":
                " AND annotation.start_anchor_id = target.start_anchor_id")
              +(!anchorEndLayers.contains(layer.getId())?"":
                " AND annotation.end_anchor_id = target.end_anchor_id")
              +" ORDER BY annotation.ordinal, annotation.annotation_id"
              +" LIMIT 0, " + layerIdToMaxAnnotations.get(layerId);
            if (targetOffset != 0
                && targetLayer.getId().equals(schema.getWordLayerId())) { // offset word
              sql = "SELECT DISTINCT annotation.*, ? AS layer, annotation.ag_id AS graph"
                +" FROM annotation_layer_"+layer_id+" annotation"
                +" INNER JOIN annotation_layer_"+layer_id+" token"
                +" ON annotation.turn_annotation_id = token.turn_annotation_id"
                +" AND annotation.ordinal_in_turn = token.ordinal_in_turn + "
                + targetOffset
                +(!anchorStartLayers.contains(layer.getId())
                  && !anchorEndLayers.contains(layer.getId())?"":
                  " INNER JOIN annotation_layer_"+targetLayer.get("layer_id")+" target"
                  +" ON annotation.word_annotation_id = target.word_annotation_id")
                +" WHERE token.word_annotation_id = ?"
                +(!anchorStartLayers.contains(layer.getId())?"":
                  " AND token.start_anchor_id = target.start_anchor_id")
                +(!anchorEndLayers.contains(layer.getId())?"":
                  " AND token.end_anchor_id = target.end_anchor_id")
                +" ORDER BY annotation.ordinal, annotation.annotation_id"
                +" LIMIT 0, " + layerIdToMaxAnnotations.get(layerId);
            } // offset word
            Object[] groups = { layer.getId(), target_annotation_id_group };
            queries.put(layerId, getConnection().prepareStatement(sql));
            parameterGroups.put(layerId, groups);
            altQueries.put(layerId, null); // there is no alternative query
            altParameterGroups.put(layerId, null);
          } else if (targetLayer.getId().equals(layer.getId()) // target is selected layer
                     && targetOffset == 0) { // and we're after our own details
            String sql = "SELECT DISTINCT annotation.*, ? AS layer, annotation.ag_id AS graph"
              +" FROM annotation_layer_"+layer_id+" annotation"
              +" WHERE annotation.annotation_id = ?"
              +" ORDER BY annotation.ordinal, annotation.annotation_id"
              +" LIMIT 1"; // there can be only one match 
            Object[] groups = { layer.getId(), target_annotation_id_group };
            queries.put(layerId, getConnection().prepareStatement(sql));
            parameterGroups.put(layerId, groups);
            altQueries.put(layerId, null); // there is no alternative query
            altParameterGroups.put(layerId, null);
            // TODO if targetLayer is an aligned word child, and layer is too, order matches
            // TODO so that the layer annotations most temporally near the target are first
            // TODO this ensures that by default (annotationsPerLayer=1)
            // TODO tags of the target are returned
          } else if (targetLayer.isAncestor(schema.getWordLayerId())) { // word layer
            // target table has word_annotation_id field
            String sql = "SELECT DISTINCT annotation.*, ? AS layer, annotation.ag_id AS graph"
              +" FROM annotation_layer_"+layer_id+" annotation"
              +" INNER JOIN annotation_layer_"+targetLayer.get("layer_id")+" target"
              +" ON annotation.word_annotation_id = target.word_annotation_id"
              +(!anchorStartLayers.contains(layer.getId())?"":
                " AND annotation.start_anchor_id = target.start_anchor_id")
              +(!anchorEndLayers.contains(layer.getId())?"":
                " AND annotation.end_anchor_id = target.end_anchor_id")
              +" WHERE target.annotation_id = ?"
              +" ORDER BY annotation.ordinal, annotation.annotation_id"
              +" LIMIT 0, " + layerIdToMaxAnnotations.get(layerId);
            if (targetOffset != 0
                && (SqlConstants.SCOPE_WORD.equalsIgnoreCase(
                      (String)targetLayer.get("scope"))
                    || SqlConstants.SCOPE_SEGMENT.equalsIgnoreCase(
                      (String)targetLayer.get("scope")))) { // offset word
              sql = "SELECT DISTINCT annotation.*, ? AS layer, annotation.ag_id AS graph"
                +" FROM annotation_layer_"+layer_id+" annotation"
                +" INNER JOIN annotation_layer_"+SqlConstants.LAYER_TRANSCRIPTION+" target"
                +" ON annotation.word_annotation_id = target.annotation_id"
                +" INNER JOIN annotation_layer_"+targetLayer.get("layer_id")+" token"
                +" ON target.turn_annotation_id = token.turn_annotation_id"
                +" AND target.ordinal_in_turn = token.ordinal_in_turn + " + targetOffset
                +(!anchorStartLayers.contains(layer.getId())?"":
                  " AND token.start_anchor_id = target.start_anchor_id")
                +(!anchorEndLayers.contains(layer.getId())?"":
                  " AND token.end_anchor_id = target.end_anchor_id")
                +" WHERE token.annotation_id = ?"
                +" ORDER BY annotation.ordinal, annotation.annotation_id"
                +" LIMIT 0, " + layerIdToMaxAnnotations.get(layerId);
            } // offset word
            Object[] groups = { layer.getId(), target_annotation_id_group };
            queries.put(layerId, getConnection().prepareStatement(sql));
            parameterGroups.put(layerId, groups);
            altQueries.put(layerId, null); // there is no alternative query
            altParameterGroups.put(layerId, null);
          } else if (targetLayer.getParentId().equals(schema.getTurnLayerId())
                     && targetLayer.getAlignment() != Constants.ALIGNMENT_NONE) { // phrase layer
            String sql = "SELECT DISTINCT annotation.*, ? AS layer, annotation.ag_id AS graph,"
              +" start.offset"
              +" FROM annotation_layer_"+layer_id+" annotation"
              +" INNER JOIN anchor start ON annotation.start_anchor_id = start.anchor_id"
              +" INNER JOIN anchor end ON annotation.end_anchor_id = end.anchor_id"
              +" INNER JOIN (annotation_layer_"+targetLayer.get("layer_id")+" target"
              +" INNER JOIN anchor target_start ON target.start_anchor_id = target_start.anchor_id"
              +" INNER JOIN anchor target_end ON target.end_anchor_id = target_end.anchor_id)"
              +" ON annotation.turn_annotation_id = target.turn_annotation_id"
              +" AND end.offset > target_start.offset"
              +" AND start.offset < target_end.offset"
              +(!anchorStartLayers.contains(layer.getId())?"":
                " AND annotation.start_anchor_id = target.start_anchor_id")
              +(!anchorEndLayers.contains(layer.getId())?"":
                " AND annotation.end_anchor_id = target.end_anchor_id")
              +" WHERE target.annotation_id = ?"
              +" ORDER BY start.offset, annotation.annotation_id"
              +" LIMIT 0, " + layerIdToMaxAnnotations.get(layerId);
            Object[] groups = { layer.getId(), target_annotation_id_group };
            queries.put(layerId, getConnection().prepareStatement(sql));
            parameterGroups.put(layerId, groups);
            altQueries.put(layerId, null); // there is no alternative query
            altParameterGroups.put(layerId, null);
          } else if (targetLayer.getParentId().equals(schema.getRoot().getId())
                     && targetLayer.getAlignment() != Constants.ALIGNMENT_NONE) { // span layer
            String sql = "SELECT DISTINCT annotation.*, ? AS layer, annotation.ag_id AS graph,"
              +" start.offset"
              +" FROM annotation_layer_"+layer_id+" annotation"
              +" INNER JOIN anchor start ON annotation.start_anchor_id = start.anchor_id"
              +" INNER JOIN anchor end ON annotation.end_anchor_id = end.anchor_id"
              +" INNER JOIN (annotation_layer_"+targetLayer.get("layer_id")+" target"
              +" INNER JOIN anchor target_start ON target.start_anchor_id = target_start.anchor_id"
              +" INNER JOIN anchor target_end ON target.end_anchor_id = target_end.anchor_id)"
              +" ON annotation.ag_id = target.ag_id"
              +" AND end.offset > target_start.offset"
              +" AND start.offset < target_end.offset"
              +(!anchorStartLayers.contains(layer.getId())?"":
                " AND annotation.start_anchor_id = target.start_anchor_id")
              +(!anchorEndLayers.contains(layer.getId())?"":
                " AND annotation.end_anchor_id = target.end_anchor_id")
              +" WHERE target.annotation_id = ?"
              +" ORDER BY start.offset, annotation.annotation_id"
              +" LIMIT 0, " + layerIdToMaxAnnotations.get(layerId);
            Object[] groups = { layer.getId(), target_annotation_id_group };
            queries.put(layerId, getConnection().prepareStatement(sql));
            parameterGroups.put(layerId, groups);
            altQueries.put(layerId, null); // there is no alternative query
            altParameterGroups.put(layerId, null);
          } else { // can't process this layer
            queries.put(layerId, null);
            Object[] reason = { "Couldn't get " + layer + " for targets from " + targetLayer };
            parameterGroups.put(layerId, reason);
            altQueries.put(layerId, null);
            altParameterGroups.put(layerId, null);
          }
        } else if ("transcript".equals(layer.get("class_id"))) { // transcript attribute
          // read-only users get access to only public attributes
          if (!"1".equals(layer.get("access")) && !getUserRoles().contains("edit")) { // no access
            queries.put(layerId, null);
            Object[] reason = { "" };
            parameterGroups.put(layerId, reason);
            altQueries.put(layerId, null);
            altParameterGroups.put(layerId, null);
          } else { // has access to attribute          
            if (ag_id_group != null) { // the MatchId includes to ag_id
              String sql = "SELECT DISTINCT annotation.annotation_id, annotation.ag_id,"
                +" annotation.label, annotation.label_status,"
                +" annotation.annotated_by, annotation.annotated_when,"
                +" 0 AS ordinal, NULL AS start_anchor_id, NULL AS end_anchor_id,"
                +" ? AS layer, annotation.ag_id AS graph"
                +" FROM annotation_transcript annotation"
                +" WHERE annotation.ag_id = ?"
                +" AND layer = ?"
                +" ORDER BY annotation_id"
                +" LIMIT 0, " + layerIdToMaxAnnotations.get(layerId);
              Object[] groups = {
                layer.getId(), ag_id_group, layer.get("attribute") };
              queries.put(layerId, getConnection().prepareStatement(sql));
              parameterGroups.put(layerId, groups);
              altQueries.put(layerId, null); // there is no alternative query
              altParameterGroups.put(layerId, null);
            }  else { // the MatchId doesn't include to ag_id
              // get the ag_id from the target
              String sql = "SELECT DISTINCT annotation.annotation_id, annotation.ag_id,"
                +" annotation.label, annotation.label_status,"
                +" annotation.annotated_by, annotation.annotated_when,"
                +" 0 AS ordinal, NULL AS start_anchor_id, NULL AS end_anchor_id,"
                +" ? AS layer, annotation.ag_id AS graph"
                +" FROM annotation_transcript annotation"
                +" INNER JOIN annotation_layer_"+targetLayer.get("layer_id")+" target"
                +" ON annotation.ag_id = target.ag_id"
                +" WHERE target.annotation_id = ?"
                +" AND layer = ?"
                +" ORDER BY annotation_id"
                +" LIMIT 0, " + layerIdToMaxAnnotations.get(layerId);
              Object[] groups = {
                layer.getId(), target_annotation_id_group, layer.get("attribute") };
              queries.put(layerId, getConnection().prepareStatement(sql));
              parameterGroups.put(layerId, groups);
              altQueries.put(layerId, null); // there is no alternative query
              altParameterGroups.put(layerId, null);
            } // the MatchId doesn't include to ag_id
          } // has access to attribute
        }  else if ("speaker".equals(layer.get("class_id"))) { // participant attribute
          // read-only users get access to only public attributes
          if (!"1".equals(layer.get("access")) && !getUserRoles().contains("edit")) { // no access
            queries.put(layerId, null);
            Object[] reason = { "" };
            parameterGroups.put(layerId, reason);
            altQueries.put(layerId, null);
            altParameterGroups.put(layerId, null);
          } else { // has access to attribute
            if (participant_speaker_number_group != null) {
              // the MatchId includes the speaker number
              String sql  = "SELECT DISTINCT"+
                " annotation.annotation_id, annotation.speaker_number,"
                +" annotation.label, annotation.label_status,"
                +" annotation.annotated_by, annotation.annotated_when,"
                +" 0 AS ordinal, NULL AS start_anchor_id, NULL AS end_anchor_id,"
                +" ? AS layer, NULL AS graph"
                +" FROM annotation_participant annotation"
                +" WHERE annotation.speaker_number = ? AND layer = ?"
                +" ORDER BY annotation_id"
                +" LIMIT 0, " + layerIdToMaxAnnotations.get(layerId);
              Object[] groups = {
                layer.getId(), participant_speaker_number_group, layer.get("attribute") };
              queries.put(layerId, getConnection().prepareStatement(sql));
              parameterGroups.put(layerId, groups);
              altQueries.put(layerId, null); // there is no alternative query
              altParameterGroups.put(layerId, null);
            } else { // the MatchId doesn't include the speaker number
              // use the turn label to get the speaker of the token instead
              String sql = "SELECT DISTINCT"
                +" annotation.annotation_id, annotation.speaker_number,"
                +" annotation.label, annotation.label_status,"
                +" annotation.annotated_by, annotation.annotated_when,"
                +" 0 AS ordinal, NULL AS start_anchor_id, NULL AS end_anchor_id,"
                +" ? AS layer, NULL AS graph"
                +" FROM annotation_layer_"+SqlConstants.LAYER_TURN+" turn"
                // same turn as target
                +" INNER JOIN annotation_layer_"+targetLayer.get("layer_id")+" target"
                +" ON turn.turn_annotation_id = target.turn_annotation_id"
                +" INNER JOIN annotation_participant annotation"
                +" ON annotation.speaker_number = turn.label AND layer = ?"
                +" WHERE target.annotation_id = ?"
                +" ORDER BY annotation.annotation_id"
                +" LIMIT 0, " + layerIdToMaxAnnotations.get(layerId);
              Object[] groups = {
                layer.getId(), layer.get("attribute"), target_annotation_id_group };
              queries.put(layerId, getConnection().prepareStatement(sql));
              parameterGroups.put(layerId, groups);
              altQueries.put(layerId, null); // there is no alternative query
              altParameterGroups.put(layerId, null);
            } // the MatchId doesn't include the speaker number
          } // has access to attribute
        }  else if (layer.getId().equals(schema.getParticipantLayerId())) { // participant
          if (participant_speaker_number_group != null) { // the MatchId includes the speaker number
            String sql  = "SELECT annotation.speaker_number AS annotation_id,"
              +" annotation.speaker_number,"
              +" annotation.name AS label, 100 AS label_status,"
              +" 0 AS ordinal, NULL AS annotated_by, NULL AS annotated_when,"
              +" NULL AS start_anchor_id, NULL AS end_anchor_id,"
              +" ? AS layer, NULL AS graph"
              +" FROM speaker annotation"
              +" WHERE annotation.speaker_number = ?";
            Object[] groups = {
              layer.getId(), participant_speaker_number_group };
            queries.put(layerId, getConnection().prepareStatement(sql));
            parameterGroups.put(layerId, groups);
            altQueries.put(layerId, null); // there is no alternative query
            altParameterGroups.put(layerId, null);
          } else { // the MatchId doesn't include the speaker number
            // use the turn label to get the speaker of the token instead
            String sql  = "SELECT annotation.speaker_number AS annotation_id,"
              +" annotation.speaker_number,"
              +" annotation.name AS label, 100 AS label_status,"
              +" 0 AS ordinal, NULL AS annotated_by, NULL AS annotated_when,"
              +" NULL AS start_anchor_id, NULL AS end_anchor_id,"
              +" ? AS layer, NULL AS graph"
              +" FROM annotation_layer_"+SqlConstants.LAYER_TURN+" turn"
              // same turn as target
              +" INNER JOIN annotation_layer_"+targetLayer.get("layer_id")+" target"
              +" ON turn.turn_annotation_id = target.turn_annotation_id"
              +" INNER JOIN speaker annotation ON annotation.speaker_number = turn.label"
              +" WHERE target.annotation_id = ?"
              +" ORDER BY annotation.speaker_number"
              +" LIMIT 1"; // there's only one speaker
            Object[] groups = {layer.getId(), target_annotation_id_group };
            queries.put(layerId, getConnection().prepareStatement(sql));
            parameterGroups.put(layerId, groups);
            altQueries.put(layerId, null); // there is no alternative query
            altParameterGroups.put(layerId, null);
          } // the MatchId doesn't include the speaker number
        }  else if (layer.getId().equals("main_participant")) { // main_participant
          if (ag_id_group != null) { // the MatchId includes to ag_id
            String sql = "SELECT speaker.name AS label,"
              +" CONCAT('m_-3', speaker.speaker_number) AS annotation_id,"
              +" 0 AS ordinal, 100 AS label_status,"
              +" NULL AS annotated_by, NULL AS annotated_when,"
              +" NULL AS start_anchor_id, NULL AS end_anchor_id,"
              +" ? AS layer, graph.transcript_id AS graph"
              +" FROM transcript graph"
              +" INNER JOIN transcript_speaker ON graph.ag_id = transcript_speaker.ag_id AND transcript_speaker.main_speaker = 1"
              +" INNER JOIN speaker ON transcript_speaker.speaker_number = speaker.speaker_number"
              +" WHERE graph.ag_id = ? LIMIT 1";
            Object[] groups = { layer.getId(), ag_id_group };
            queries.put(layerId, getConnection().prepareStatement(sql));
            parameterGroups.put(layerId, groups);
            altQueries.put(layerId, null); // there is no alternative query
            altParameterGroups.put(layerId, null);
          } else { // the MatchId doesn't include to ag_id
            // get the ag_id from the target
            String sql = "SELECT speaker.name AS label,"
              +" CONCAT('m_-3', speaker.speaker_number) AS annotation_id,"
              +" 0 AS ordinal, 100 AS label_status,"
              +" NULL AS annotated_by, NULL AS annotated_when,"
              +" NULL AS start_anchor_id, NULL AS end_anchor_id,"
              +" ? AS layer, graph.transcript_id AS graph"
              +" FROM transcript graph"
              +" INNER JOIN annotation_layer_"+targetLayer.get("layer_id")+" target"
              +" ON graph.ag_id = target.ag_id"
              +" INNER JOIN transcript_speaker ON graph.ag_id = transcript_speaker.ag_id AND transcript_speaker.main_speaker = 1"
              +" INNER JOIN speaker ON transcript_speaker.speaker_number = speaker.speaker_number"
              +" WHERE target.annotation_id = ? LIMIT 1";
            Object[] groups = { layer.getId(), target_annotation_id_group };
            queries.put(layerId, getConnection().prepareStatement(sql));
            parameterGroups.put(layerId, groups);
            altQueries.put(layerId, null); // there is no alternative query
            altParameterGroups.put(layerId, null);
          } // the MatchId doesn't include to ag_id
        } else if (layer.getId().equals(schema.getCorpusLayerId())) { // corpus
          if (ag_id_group != null) { // the MatchId includes to ag_id
            String sql = "SELECT graph.corpus_name AS label,"
              +" COALESCE(c.corpus_id, graph.corpus_name) AS annotation_id,"
              +" 0 AS ordinal, 100 AS label_status,"
              +" NULL AS annotated_by, NULL AS annotated_when,"
              +" NULL AS start_anchor_id, NULL AS end_anchor_id,"
              +" ? AS layer, graph.transcript_id AS graph"
              +" FROM transcript graph"
              +" LEFT OUTER JOIN corpus c ON c.corpus_name = graph.corpus_name"
              +" WHERE graph.ag_id = ?";
            Object[] groups = { layer.getId(), ag_id_group };
            queries.put(layerId, getConnection().prepareStatement(sql));
            parameterGroups.put(layerId, groups);
            altQueries.put(layerId, null); // there is no alternative query
            altParameterGroups.put(layerId, null);
          } else { // the MatchId doesn't include to ag_id
            // get the ag_id from the target
            String sql = "SELECT graph.corpus_name AS label,"
              +" COALESCE(c.corpus_id, graph.corpus_name) AS annotation_id,"
              +" 0 AS ordinal, 100 AS label_status,"
              +" NULL AS annotated_by, NULL AS annotated_when,"
              +" NULL AS start_anchor_id, NULL AS end_anchor_id,"
              +" ? AS layer, graph.transcript_id AS graph"
              +" FROM transcript graph"
              +" INNER JOIN annotation_layer_"+targetLayer.get("layer_id")+" target"
              +" ON graph.ag_id = target.ag_id"
              +" LEFT OUTER JOIN corpus c ON c.corpus_name = graph.corpus_name"
              +" WHERE target.annotation_id = ?";
            Object[] groups = { layer.getId(), target_annotation_id_group };
            queries.put(layerId, getConnection().prepareStatement(sql));
            parameterGroups.put(layerId, groups);
            altQueries.put(layerId, null); // there is no alternative query
            altParameterGroups.put(layerId, null);
          } // the MatchId doesn't include to ag_id
        } else if (layer.getId().equals(schema.getEpisodeLayerId())) { // episode
          if (ag_id_group != null) { // the MatchId includes to ag_id
            String sql = "SELECT e.name AS label,"
              +" graph.family_id AS annotation_id,"
              +" 0 AS ordinal, 100 AS label_status,"
              +" NULL AS annotated_by, NULL AS annotated_when,"
              +" NULL AS start_anchor_id, NULL AS end_anchor_id,"
              +" ? AS layer, graph.transcript_id AS graph"
              +" FROM transcript graph"
              +" INNER JOIN transcript_family e ON e.family_id = graph.family_id"
              +" WHERE graph.ag_id = ?";
            Object[] groups = { layer.getId(), ag_id_group };
            queries.put(layerId, getConnection().prepareStatement(sql));
            parameterGroups.put(layerId, groups);
            altQueries.put(layerId, null); // there is no alternative query
            altParameterGroups.put(layerId, null);
          } else { // the MatchId doesn't include to ag_id
            // get the ag_id from the target
            String sql = "SELECT e.name AS label,"
              +" graph.family_id AS annotation_id,"
              +" 0 AS ordinal, 100 AS label_status,"
              +" NULL AS annotated_by, NULL AS annotated_when,"
              +" NULL AS start_anchor_id, NULL AS end_anchor_id,"
              +" ? AS layer, graph.transcript_id AS graph"
              +" FROM transcript graph"
              +" INNER JOIN annotation_layer_"+targetLayer.get("layer_id")+" target"
              +" ON graph.ag_id = target.ag_id"
              +" INNER JOIN transcript_family e ON e.family_id = graph.family_id"
              +" WHERE target.annotation_id = ?";
            Object[] groups = { layer.getId(), target_annotation_id_group };
            queries.put(layerId, getConnection().prepareStatement(sql));
            parameterGroups.put(layerId, groups);
            altQueries.put(layerId, null); // there is no alternative query
            altParameterGroups.put(layerId, null);
          } // the MatchId doesn't include to ag_id
        }  else if (layer.getId().equals(schema.getTurnLayerId())) { // turn
          Integer layer_id = (Integer)layer.get("layer_id");
          if (targetLayer.isAncestor(schema.getTurnLayerId())) {
            // target table has turn_annotation_id field
            String sql = "SELECT DISTINCT annotation.annotation_id,"
              +" annotation.start_anchor_id, annotation.end_anchor_id,"
              +" annotation.label_status, annotation.ordinal,"
              +" annotation.annotated_by, annotation.annotated_when,"
              +" speaker.name AS label,"
              +" ? AS layer,"
              +" annotation.ag_id AS graph"
              +" FROM annotation_layer_"+layer_id+" annotation"
              // same turn as target
              +" INNER JOIN annotation_layer_"+targetLayer.get("layer_id")+" target"
              +" ON annotation.turn_annotation_id = target.turn_annotation_id"
              +" INNER JOIN speaker ON speaker.speaker_number = annotation.label"
              +" WHERE target.annotation_id = ?"
              +" ORDER BY annotation.annotation_id"
              +" LIMIT 1"; // there's only one turn
            Object[] groups = { layer.getId(), target_annotation_id_group };
            queries.put(layerId, getConnection().prepareStatement(sql));
            parameterGroups.put(layerId, groups);
            altQueries.put(layerId, null); // there is no alternative query
            altParameterGroups.put(layerId, null);
          }  else { // can't process this layer
            queries.put(layerId, null);
            Object[] reason = { "Couldn't get " + layer + " for targets from " + targetLayer };
            parameterGroups.put(layerId, reason);
            altQueries.put(layerId, null);
            altParameterGroups.put(layerId, null);
          }
        } else if (layer.getId().equals(schema.getUtteranceLayerId())) { // utterance
          Integer layer_id = (Integer)layer.get("layer_id");
          if (targetLayer.isAncestor(schema.getTurnLayerId())) {
            // target table has turn_annotation_id field
            String sql = "SELECT DISTINCT annotation.annotation_id,"
              +" annotation.start_anchor_id, annotation.end_anchor_id,"
              +" annotation.label_status, annotation.ordinal,"
              +" annotation.annotated_by, annotation.annotated_when,"
              +" speaker.name AS label,"
              +" annotation.turn_annotation_id,"
              +" ? AS layer,"
              +" annotation.ag_id AS graph,"
              // these required for ORDER BY
              +" annotation_start.offset AS annotation_start_offset,"
              +" annotation_end.offset - annotation_start.offset AS annotation_length"
              +" FROM annotation_layer_"+layer_id+" annotation"
              // same turn as target
              +" INNER JOIN annotation_layer_"+targetLayer.get("layer_id")+" target"
              +" ON annotation.turn_annotation_id = target.turn_annotation_id"
              +" INNER JOIN speaker ON speaker.speaker_number = annotation.label"
              // annotation anchors
              +" INNER JOIN anchor annotation_start"
              +" ON annotation.start_anchor_id = annotation_start.anchor_id"
              +" INNER JOIN anchor annotation_end"
              +" ON annotation.end_anchor_id = annotation_end.anchor_id"
              // target anchors
              +" INNER JOIN anchor target_start"
              +" ON target.start_anchor_id = target_start.anchor_id"
              +" WHERE target.annotation_id = ?"
              // annotation includes target start time
              +" AND annotation_start.offset <= target_start.offset"
              +" AND target_start.offset < annotation_end.offset"
              +" ORDER BY annotation.annotation_id"
              +" LIMIT 1"; // there's only one utterance
            if (targetOffset != 0
                && SqlConstants.SCOPE_WORD.equalsIgnoreCase(
                  (String)targetLayer.get("scope"))) { // offset word
              sql = "SELECT DISTINCT annotation.annotation_id,"
                +" annotation.start_anchor_id, annotation.end_anchor_id,"
                +" annotation.label_status, annotation.ordinal,"
                +" annotation.annotated_by, annotation.annotated_when,"
                +" speaker.name AS label,"
                +" annotation.turn_annotation_id,"
                +" ? AS layer,"
                +" annotation.ag_id AS graph,"
                // these required for ORDER BY
                +" annotation_start.offset AS annotation_start_offset,"
                +" annotation_end.offset - annotation_start.offset AS annotation_length"
                +" FROM annotation_layer_"+layer_id+" annotation"
                // same turn as target
                +" INNER JOIN annotation_layer_"+SqlConstants.LAYER_TRANSCRIPTION+" target"
                +" ON annotation.turn_annotation_id = target.turn_annotation_id"
                        
                // token is the original target before applying offset
                +" INNER JOIN annotation_layer_"+targetLayer.get("layer_id")+" token"
                +" ON target.turn_annotation_id = token.turn_annotation_id"
                +" AND target.ordinal_in_turn = token.ordinal_in_turn + " + targetOffset
                        
                +" INNER JOIN speaker ON speaker.speaker_number = annotation.label"
                // annotation anchors
                +" INNER JOIN anchor annotation_start"
                +" ON annotation.start_anchor_id = annotation_start.anchor_id"
                +" INNER JOIN anchor annotation_end"
                +" ON annotation.end_anchor_id = annotation_end.anchor_id"
                // target anchors
                +" INNER JOIN anchor target_start"
                +" ON target.start_anchor_id = target_start.anchor_id"
                        
                // use token.annotation_id instead of target.annotation_id
                +" WHERE token.annotation_id = ?"
                        
                // annotation includes target start time
                +" AND annotation_start.offset <= target_start.offset"
                +" AND target_start.offset < annotation_end.offset"
                +" ORDER BY annotation.annotation_id"
                +" LIMIT 1"; // there's only one utterance
            } // offset word
            Object[] groups = { layer.getId(), target_annotation_id_group };
            queries.put(layerId, getConnection().prepareStatement(sql));
            parameterGroups.put(layerId, groups);
            altQueries.put(layerId, null); // there is no alternative query
            altParameterGroups.put(layerId, null);
          } else { // can't process this layer
            queries.put(layerId, null);
            Object[] reason = { "Couldn't get " + layer + " for targets from " + targetLayer };
            parameterGroups.put(layerId, reason);
            altQueries.put(layerId, null);
            altParameterGroups.put(layerId, null);
          }
        } // utterance
        else if (layer.getParentId() != null
                 && layer.getParentId().equals(schema.getTurnLayerId())) { // phrase layer
          Integer layer_id = (Integer)layer.get("layer_id");
          if (targetLayer.getId().equals(layer.getId()) // target is selected layer
              && targetOffset == 0) { // and we're after our own details
            String sql = "SELECT DISTINCT annotation.*, ? AS layer, annotation.ag_id AS graph"
              +" FROM annotation_layer_"+layer_id+" annotation"
              +" WHERE annotation.annotation_id = ?"
              +" ORDER BY annotation.ordinal, annotation.annotation_id"
              +" LIMIT 1"; // there can be only one match 
            Object[] groups = { layer.getId(), target_annotation_id_group };
            queries.put(layerId, getConnection().prepareStatement(sql));
            parameterGroups.put(layerId, groups);
            altQueries.put(layerId, null); // there is no alternative query
            altParameterGroups.put(layerId, null);
          } else if (targetLayer.isAncestor(schema.getTurnLayerId())) {
            // target table has turn_annotation_id field
            String sql = "SELECT DISTINCT annotation.*, ? AS layer,"
              +" annotation.ag_id AS graph,"
              // these required for ORDER BY
              +" annotation_start.offset AS annotation_start_offset,"
              +" annotation_end.offset - annotation_start.offset AS annotation_length"
              +" FROM annotation_layer_"+layer_id+" annotation"
              // same turn as target
              +" INNER JOIN annotation_layer_"+targetLayer.get("layer_id")+" target"
              +" ON annotation.turn_annotation_id = target.turn_annotation_id"
              +(!anchorStartLayers.contains(layer.getId())?"":
                " AND annotation.start_anchor_id = target.start_anchor_id")
              +(!anchorEndLayers.contains(layer.getId())?"":
                " AND annotation.end_anchor_id = target.end_anchor_id")
              // annotation anchors
              +" INNER JOIN anchor annotation_start"
              +" ON annotation.start_anchor_id = annotation_start.anchor_id"
              +" INNER JOIN anchor annotation_end"
              +" ON annotation.end_anchor_id = annotation_end.anchor_id"
              // target anchors
              +" INNER JOIN anchor target_start"
              +" ON target.start_anchor_id = target_start.anchor_id"
              +" WHERE target.annotation_id = ?"
              // annotation includes target start time
              +" AND annotation_start.offset <= target_start.offset"
              +" AND target_start.offset < annotation_end.offset"
              +" ORDER BY annotation_start.offset," // earliest first
              +" annotation_end.offset - annotation_start.offset," // then shortest first
              +" annotation.annotation_id"
              +" LIMIT 0, " + layerIdToMaxAnnotations.get(layerId);
            if (targetOffset != 0
                && SqlConstants.SCOPE_WORD.equalsIgnoreCase(
                  (String)targetLayer.get("scope"))) { // offset word
              sql = "SELECT DISTINCT annotation.*, ? AS layer,"
                +" annotation.ag_id AS graph,"
                // these required for ORDER BY
                +" annotation_start.offset AS annotation_start_offset,"
                +" annotation_end.offset - annotation_start.offset AS annotation_length"
                +" FROM annotation_layer_"+layer_id+" annotation"
                // same turn as target
                +" INNER JOIN annotation_layer_"+SqlConstants.LAYER_TRANSCRIPTION+" target"
                +" ON annotation.turn_annotation_id = target.turn_annotation_id"
                
                // token is the original target before applying offset
                +" INNER JOIN annotation_layer_"+targetLayer.get("layer_id")+" token"
                +" ON target.turn_annotation_id = token.turn_annotation_id"
                +" AND target.ordinal_in_turn = token.ordinal_in_turn + " + targetOffset
                
                +(!anchorStartLayers.contains(layer.getId())?"":
                  " AND token.start_anchor_id = target.start_anchor_id")
                +(!anchorEndLayers.contains(layer.getId())?"":
                  " AND token.end_anchor_id = target.end_anchor_id")
                // annotation anchors
                +" INNER JOIN anchor annotation_start"
                +" ON annotation.start_anchor_id = annotation_start.anchor_id"
                +" INNER JOIN anchor annotation_end"
                +" ON annotation.end_anchor_id = annotation_end.anchor_id"
                // target anchors
                +" INNER JOIN anchor target_start"
                +" ON target.start_anchor_id = target_start.anchor_id"
                
                // use token.annotation_id instead of target.annotation_id
                +" WHERE token.annotation_id = ?"
                // annotation includes target start time
                +" AND annotation_start.offset <= target_start.offset"
                +" AND target_start.offset < annotation_end.offset"
                +" ORDER BY annotation_start.offset," // earliest first
                +" annotation_end.offset - annotation_start.offset," // then shortest first
                +" annotation.annotation_id"
                +" LIMIT 0, " + layerIdToMaxAnnotations.get(layerId);
            } // offset word
            Object[] groups = { layer.getId(), target_annotation_id_group };
            queries.put(layerId, getConnection().prepareStatement(sql));
            parameterGroups.put(layerId, groups);
            altQueries.put(layerId, null); // there is no alternative query
            altParameterGroups.put(layerId, null);
          } else { // can't process this layer
            queries.put(layerId, null);
            Object[] reason = {
              "Couldn't get " + layer + " (phrase) for targets from " + targetLayer };
            parameterGroups.put(layerId, reason);
            altQueries.put(layerId, null);
            altParameterGroups.put(layerId, null);
          }
        } else if ((layer.getParentId() == null
                     || layer.getParentId().equals(schema.getRoot().getId()))
                    && layer.getAlignment() != Constants.ALIGNMENT_NONE) { // freeform layer
          Integer layer_id = (Integer)layer.get("layer_id");
          String sql = "SELECT DISTINCT annotation.*, ? AS layer,"
            +" annotation.ag_id AS graph,"
            // these required for ORDER BY
            +" annotation_start.offset AS annotation_start_offset,"
            +" annotation_end.offset - annotation_start.offset AS annotation_length"
            +" FROM annotation_layer_"+layer_id+" annotation"
            // same graph as target
            +" INNER JOIN annotation_layer_"+targetLayer.get("layer_id")+" target"
            +" ON annotation.ag_id = target.ag_id"
            +(!anchorStartLayers.contains(layer.getId())?"":
              " AND annotation.start_anchor_id = target.start_anchor_id")
            +(!anchorEndLayers.contains(layer.getId())?"":
              " AND annotation.end_anchor_id = target.end_anchor_id")
            // annotation anchors
            +" INNER JOIN anchor annotation_start"
            +" ON annotation.start_anchor_id = annotation_start.anchor_id"
            +" INNER JOIN anchor annotation_end"
            +" ON annotation.end_anchor_id = annotation_end.anchor_id"
            // target anchors
            +" INNER JOIN anchor target_start"
            +" ON target.start_anchor_id = target_start.anchor_id"
            +" WHERE target.annotation_id = ?"
            // annotation includes target start time
            +" AND annotation_start.offset <= target_start.offset"
            +" AND target_start.offset < annotation_end.offset"
            +" ORDER BY annotation_start.offset," // earliest first
            +" annotation_end.offset - annotation_start.offset," // then shortest first
            +" annotation.annotation_id"
            +" LIMIT 0, " + layerIdToMaxAnnotations.get(layerId);
          if (targetOffset != 0
              && SqlConstants.SCOPE_WORD.equalsIgnoreCase(
                (String)targetLayer.get("scope"))) { // offset word
            sql = "SELECT DISTINCT annotation.*, ? AS layer,"
              +" annotation.ag_id AS graph,"
              // these required for ORDER BY
              +" annotation_start.offset AS annotation_start_offset,"
              +" annotation_end.offset - annotation_start.offset AS annotation_length"
              +" FROM annotation_layer_"+layer_id+" annotation"
              // same graph as target
              +" INNER JOIN annotation_layer_"+SqlConstants.LAYER_TRANSCRIPTION+" target"
              +" ON annotation.ag_id = target.ag_id"
                        
              // token is the original target before applying offset
              +" INNER JOIN annotation_layer_"+targetLayer.get("layer_id")+" token"
              +" ON target.turn_annotation_id = token.turn_annotation_id"
              +" AND target.ordinal_in_turn = token.ordinal_in_turn + " + targetOffset

              +(!anchorStartLayers.contains(layer.getId())?"":
                " AND token.start_anchor_id = target.start_anchor_id")
              +(!anchorEndLayers.contains(layer.getId())?"":
                " AND token.end_anchor_id = target.end_anchor_id")

              // annotation anchors
              +" INNER JOIN anchor annotation_start"
              +" ON annotation.start_anchor_id = annotation_start.anchor_id"
              +" INNER JOIN anchor annotation_end"
              +" ON annotation.end_anchor_id = annotation_end.anchor_id"
              // target anchors
              +" INNER JOIN anchor target_start"
              +" ON target.start_anchor_id = target_start.anchor_id"

              // use token.annotation_id instead of target.annotation_id
              +" WHERE token.annotation_id = ?"
              // annotation includes target start time
              +" AND annotation_start.offset <= target_start.offset"
              +" AND target_start.offset < annotation_end.offset"
              +" ORDER BY annotation_start.offset," // earliest first
              +" annotation_end.offset - annotation_start.offset," // then shortest first
              +" annotation.annotation_id"
              +" LIMIT 0, " + layerIdToMaxAnnotations.get(layerId);
          } // offset word
          Object[] groups = { layer.getId(), target_annotation_id_group };
          queries.put(layerId, getConnection().prepareStatement(sql));
          parameterGroups.put(layerId, groups);
          altQueries.put(layerId, null); // there is no alternative query
          altParameterGroups.put(layerId, null);
        } else if (layer.getId().equals("transcript_type")) { // TODO one day this will be a vanilla transcript attribute 
          // transcript type
          if (ag_id_group != null) { // the MatchId includes to ag_id
            String sql = "SELECT transcript_type.transcript_type AS label,"
              +" transcript_type.type_id AS annotation_id,"
              +" 0 AS ordinal, 100 AS label_status,"
              +" NULL AS annotated_by, NULL AS annotated_when,"
              +" NULL AS start_anchor_id, NULL AS end_anchor_id,"
              +" ? AS layer, graph.transcript_id AS graph, graph.ag_id"
              +" FROM transcript graph"
              +" INNER JOIN transcript_type ON transcript_type.type_id = graph.type_id"
              +" WHERE graph.ag_id = ?";
            Object[] groups = { layer.getId(), ag_id_group };
            queries.put(layerId, getConnection().prepareStatement(sql));
            parameterGroups.put(layerId, groups);
            altQueries.put(layerId, null); // there is no alternative query
            altParameterGroups.put(layerId, null);
          } else { // the MatchId doesn't include to ag_id
            // get the ag_id from the target
            String sql = "SELECT transcript_type.transcript_type AS label,"
              +" transcript_type.type_id AS annotation_id,"
              +" 0 AS ordinal, 100 AS label_status,"
              +" NULL AS annotated_by, NULL AS annotated_when,"
              +" NULL AS start_anchor_id, NULL AS end_anchor_id,"
              +" ? AS layer, graph.transcript_id AS graph, graph.ag_id"
              +" FROM transcript graph"
              +" INNER JOIN annotation_layer_"+targetLayer.get("layer_id")+" target"
              +" ON graph.ag_id = target.ag_id"
              +" INNER JOIN transcript_type ON transcript_type.type_id = graph.type_id"
              +" WHERE target.annotation_id = ?";
            Object[] groups = { layer.getId(), target_annotation_id_group };
            queries.put(layerId, getConnection().prepareStatement(sql));
            parameterGroups.put(layerId, groups);
            altQueries.put(layerId, null); // there is no alternative query
            altParameterGroups.put(layerId, null);
          } // the MatchId doesn't include to ag_id
        } else { // can't process this layer
          queries.put(layerId, null);
          Object[] reason = { "Could not process layer: " + layer };
          parameterGroups.put(layerId, reason);
          altQueries.put(layerId, null);
          altParameterGroups.put(layerId, null);
        }

      } // next layer
    } catch (SQLException sqlX) {
      throw new StoreException(sqlX);
    }

    // for each match...
    boolean firstRow = true;
    do {
      Annotation[] annotations
        = new Annotation[layerIdToMaxAnnotations.values().stream().reduce(0,Integer::sum)];
         
      idMatcher = matchIdPattern.matcher(matchId);
      if (idMatcher.matches()) {
        int precedingAnnotationCount = 0; // number of preceding annotations
        // for each layer
        for (String layerId : layerIdToMaxAnnotations.keySet()) {
          Layer layer = layers.get(layerId);
          PreparedStatement sql = queries.get(layerId);
          Object[] parameters = parameterGroups.get(layerId);
          int a = precedingAnnotationCount; // index into annotations array
               
          if (layer == null || sql == null) {
            if (firstRow) { // give feedback about the failure reason (once) 
              Annotation error = null;
              if (layer == null) {
                error = new Annotation(layerId, "Layer not found: " + layerId, "error");
              } else if (parameters != null && parameters.length >= 0) {
                error = new Annotation(layerId, parameters[0].toString(), "error");
              }
              annotations[a] = error;
            } // firstRow
            continue; // next layer
          } // layer/sql missing
               
          try {
            // set query parameters
            int p = 1;
            for (Object parameter : parameters) {
              String value = parameter.toString();
              if (parameter instanceof Integer) {
                value = idMatcher.group((Integer)parameter);
              }
              sql.setString(p++, value);
            } // next parameter
                  
            // run query
            ResultSet rs = sql.executeQuery();
            boolean matched = false;
            while (rs.next()) { // there won't be more than annotationsPerLayer results
              matched = true;
              annotations[a++] = annotationFromResult(rs, layer, null);
            }
            rs.close();
            if (!matched) { // no results from query
              // is there an alternative query?
              sql = altQueries.get(layerId);
              parameters = altParameterGroups.get(layerId);
              if (sql != null) {
                // set query parameters
                p = 1;
                for (Object parameter : parameters) {
                  String value = parameter.toString();
                  if (parameter instanceof Integer) {
                    value = idMatcher.group((Integer)parameter);
                  }
                  sql.setString(p++, value);
                } // next parameter
                      
                // run alternative query
                rs = sql.executeQuery();
                while (rs.next()) {
                  annotations[a++] = annotationFromResult(rs, layer, null);
                }
                rs.close();
              } // there is an alternative query
            } // no matches for query
          } catch (SQLException sqlX) {
            System.err.println("Query error for layer: " + layerId + ": " + sqlX);
            if (firstRow) { // give feedback about the failure reason (once) 
              annotations[a] = new Annotation(
                layerId, "Query error for layer: " + layerId + ": " + sqlX, "error");
            } // firstRow
          }
          precedingAnnotationCount += layerIdToMaxAnnotations.get(layerId);
        } // next layer
      } // well-formed matchId
      consumer.accept(annotations);

      // next matchId
      matchId = matchIds.hasNext()?matchIds.next():null;
      firstRow = false;
    } while (matchId != null);

    // close all the statements we prepared
    for (PreparedStatement sql : queries.values()) {
      try { if (sql != null) sql.close(); } catch(SQLException x) {}
    }
    for (PreparedStatement sql : altQueries.values()) {
      try { if (sql != null) sql.close(); } catch(SQLException x) {}
    }
       
  } // getMatchAnnotations

  /**
   * Deletes all annotations that match a particular pattern.
   * @param expression An expression that determines which annotations match.
   * <p> The expression language is loosely based on JavaScript; expressions such as the
   * following can be used: 
   * <ul>
   *  <li><code>layer.id == 'pronunciation' 
   *       &amp;&amp; first('orthography').label == 'the'</code></li>
   *  <li><code>first('language').label == 'en' &amp;&amp; layer.id == 'pronunciation' 
   *       &amp;&amp; first('orthography').label == 'the'</code></li> 
   * </ul>
   * <p><em>NB</em> all expressions must match by either id or layer.id.
   * @return The number of new annotations deleted.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   */
  public int deleteMatchingAnnotations(String expression)
    throws StoreException, PermissionException {
    requireEdit();
    try {
      
      AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
      AnnotationAgqlToSql.Query query = transformer.sqlFor(
        expression, "annotation_id", userWhereClauseGraph("", "graph"), "");
      if (query.primaryLayer.getType().indexOf('/') > 0) { // type is a MIME-type
        // there will be a file associated with each annotation, which must be deleted
        Annotation[] annotations = getMatchingAnnotations(expression, null, null, true);
        for (Annotation annotation : annotations) {
          try {
            File file = annotationDataFile(
              annotation, annotation.getGraph(), query.primaryLayer.getType());
            if (file != null && file.exists()) file.delete();
          } catch(GraphNotFoundException exception) {}
        } // next annotation        
      }
      String delete = query.sql
        .replaceFirst("SELECT .*? FROM", "DELETE annotation.* FROM")
        .replaceAll("ORDER BY [^)]+$","");
      // System.out.println("QL: " + expression);
      // System.out.println("SQL: " + delete);
      PreparedStatement sql = getConnection().prepareStatement(delete);
      try {
        return sql.executeUpdate();
      } finally {
        sql.close();
      }
    } catch(SQLException exception) {
      System.err.println("SQL error from expression \""+expression+"\": " + exception);
      exception.printStackTrace(System.err);
      throw new StoreException("Invalid query."); // TODO i18n
    }
  }
  
  /**
   * Identifies a list of annotations that match a particular pattern, and tags them on
   * the given layer with the given label. If the specified layer ID does not allow peers,
   * all existing tags will be deleted. Otherwise, tagging does not affect any existing tags on
   * the matching annotations.
   * @param expression An expression that determines which annotations match.
   * <p> The expression language is loosely based on JavaScript; expressions such as the
   * following can be used: 
   * <ul>
   *  <li><code>layer.id == 'word' &amp;&amp; label == 'foo'</code></li>
   *  <li><code>first('language').label == 'en' &amp;&amp; layer.id == 'orthography'
   *       &amp;&amp; label == 'word'</code></li> 
   * </ul>
   * <p><em>NB</em> all expressions must match by either id or layer.id.
   * @param layerId The layer ID of the resulting annotation.
   * @param label The label of the resulting annotation.
   * @param confidence The confidence rating.
   * @return The number of new annotations added.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   */
  public int tagMatchingAnnotations(
    String expression, String layerId, String label, Integer confidence)
    throws StoreException, PermissionException {
    requireEdit();
    try {
      // get the first example to tag so we can get the layer and other info
      Annotation[] toTag = getMatchingAnnotations(expression, 1, 0, false);
      if (toTag.length == 0) return 0; // nothing to tag
      
      Layer layer = getSchema().getLayer(layerId);
      // can be optimise for word tagging?
      Matcher wordByLabel = Pattern.compile(
        "^layer.id =+ '(?<sourceLayer>.*)' (?<extraConditions>.*)&& label =+ '(?<wordLabel>.*)'$")
        .matcher(expression);
      if (wordByLabel.matches()
          && "word".equals(layer.getParentId())
          && (wordByLabel.group("sourceLayer").equals("word")
              || wordByLabel.group("sourceLayer").equals("orthography"))) {
        // optimise word tagging via orthography
        String sourceLayer = wordByLabel.group("sourceLayer");
        String wordLabel = wordByLabel.group("wordLabel");
        int ordinal = 0;
        if (!layer.getPeers()) {
          String equals = expression.indexOf("===") > 0?"===":"==="; // match exactitude
          deleteMatchingAnnotations(
            "layer == '"+QL.Esc(layerId)+"' "
            +wordByLabel.group("extraConditions") // e.g. language conditions etc.
            +"&& first('"+sourceLayer+"').label "+equals+" '"+wordLabel+"'");
          ordinal = 1;
        } else {
          // figure out what the ordinal should be by assuming the first match is representative
          // of all other words
          String wordId = toTag[0].getId();
          if (!wordId.startsWith("ew_0")) wordId = toTag[0].getParentId();
          try {
            long word_annotation_id = Long.parseLong(wordId.replace("ew_0_",""));
            PreparedStatement sql = getConnection().prepareStatement(
              "SELECT COALESCE(MAX(ordinal)+1,1) FROM annotation_layer_"+layer.get("layer_id")
              +" WHERE word_annotation_id = ?");
            sql.setLong(1, word_annotation_id);
            ResultSet rs = sql.executeQuery();
            try {
              if (rs.next()) {
                ordinal = rs.getInt(1);              
              } else { // should be impossible:
                System.err.println(
                  "tagMatchingAnnotations: no next ordinal for "+toTag[0].getId());
              }
            } finally {
              rs.close();
              sql.close();
            }
          } catch(Exception exception) {
            System.err.println(
              "tagMatchingAnnotations: cannot compute ordinal for "+toTag[0].getId()
              +": " + exception);
          }
        }
        String select = 
          "annotation.ag_id, ?, ?, annotation.start_anchor_id, annotation.end_anchor_id,"
          +" annotation.turn_annotation_id, annotation.ordinal_in_turn,"
          +" annotation.word_annotation_id, annotation.word_annotation_id, ?, ?, Now()";
        AnnotationAgqlToSql transformer = new AnnotationAgqlToSql(getSchema());
        AnnotationAgqlToSql.Query query = transformer.sqlFor(
          expression, select, userWhereClauseGraph("", "graph"), "");
        String insert = "INSERT INTO annotation_layer_" + layer.get("layer_id")
          +" (ag_id, label, label_status, start_anchor_id, end_anchor_id,"
          +" turn_annotation_id, ordinal_in_turn, word_annotation_id,"
          +" parent_id, ordinal, annotated_by, annotated_when) "
          +query.sql
          // remove layer field from SELECT clause generated by AnnotationAgqlToSql
          .replace(", '"+sourceLayer+"' AS layer","")
          // remove dummy condition
          .replace("'"+sourceLayer+"' = '"+sourceLayer+"' AND ", "")
          // remove order
          .replace("ORDER BY ag_id, parent_id, annotation_id","");
        try {
          // System.out.println(insert);
          PreparedStatement sql = getConnection().prepareStatement(insert);
          try {
            sql.setString(1, label);
            sql.setInt(2, confidence);
            sql.setInt(3, ordinal);
            sql.setString(4, getUser());
            int count = sql.executeUpdate();
            if (ordinal == 0) { // need to set ordinal
              sql.close();
              // the fastest way is by way of a temporary table
              sql = getConnection().prepareStatement(
                "CREATE TEMPORARY TABLE ords"
                +" SELECT word_annotation_id, MAX(ordinal)+1 AS ordinal, MIN(ordinal) AS min"
                +" FROM annotation_layer_"+layer.get("layer_id")
                +" GROUP BY word_annotation_id HAVING MIN(ordinal) = 0");
              sql.executeUpdate();
              sql.close();
              sql = getConnection().prepareStatement(
                "UPDATE annotation_layer_"+layer.get("layer_id")+" tags"
                +" INNER JOIN ords ON tags.word_annotation_id = ords.word_annotation_id"
                +" SET tags.ordinal = ords.ordinal"
                +" WHERE tags.ordinal = 0");
              sql.executeUpdate();
              sql.close();
              sql = getConnection().prepareStatement("DROP TEMPORARY TABLE ords");
              sql.executeUpdate();
            } // need to set ordinal
            return count;
          } finally {
            sql.close();
          }
        } catch (SQLException x) {
          throw new StoreException(x);
        }
      } else { // this is slow but correct
        boolean toTagIsParent = toTag[0].getLayerId().equals(layer.getParentId());
        if (!toTagIsParent) { // ensure they share a parent layer
          Layer toTagLayer = getLayer(toTag[0].getLayerId());
          if (!layer.getParentId().equals(toTagLayer.getParentId())) {
            throw new StoreException(
              "Tag layer \""+layerId+"\" is not a child or peer layer of the layer to tag \""
              +toTag[0].getLayerId()+"\"");
          }
        }
        
        // get them all
        toTag = getMatchingAnnotations(expression, null, null, true);
        // for each token...
        for (Annotation token : toTag) {
          createAnnotation( // (createAnnotation deletes existing if layer.peers == false)
            token.getGraph().getId(), token.getStartId(), token.getEndId(),
            layerId, label, confidence,
            toTagIsParent? token.getId() : token.getParentId());
        } // next token to tag
        return toTag.length;
      }
    } catch(GraphNotFoundException exception) {
      throw new StoreException(exception);
    }
  }
  
  /**
   * Gets the given anchors in the given transcript.
   * @param id The ID of the transcript.
   * @param anchorIds An array of anchor IDs.
   * @return A (possibly empty) array of anchors.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   * @throws GraphNotFoundException If the transcript was not found in the store.
   */
  public Anchor[] getAnchors(String id, String[] anchorIds)
    throws StoreException, PermissionException, GraphNotFoundException {
    try {
      Vector<Anchor> anchors = new Vector<Anchor>();
      PreparedStatement sqlAnchor = getConnection().prepareStatement(
        "SELECT anchor_id, `offset`, alignment_status, annotated_by, annotated_when"
        +" FROM anchor"
        +" WHERE anchor_id = ?");
      try {
        for (String anchorId : anchorIds) {
          // allow null anchorIds
          if (anchorId == null) {
            anchors.add(null);
          } else {
            try {
              Object[] o = fmtAnchorId.parse(anchorId);
              Long databaseId = (Long)o[0];
              sqlAnchor.setLong(1, databaseId);
              ResultSet rsAnchor = sqlAnchor.executeQuery();
              if (rsAnchor.next()) {
                Anchor anchor = anchorFromResult(rsAnchor, "");
                anchors.add(anchor);
              }
              rsAnchor.close();
            } catch(ClassCastException castX) {
              throw new StoreException("Not a valid anchor ID: " + anchorId);
            } catch(ParseException parseX) {
              throw new StoreException("Not an anchor ID: " + anchorId);
            }
          }
        } // next anchor
      } finally {
        sqlAnchor.close();
      }
      return anchors.toArray(new Anchor[0]);
    } catch(SQLException exception) {
      System.err.println("SQL error: " + exception);
      exception.printStackTrace(System.err);
      throw new StoreException("Invalid query."); // TODO i18n
    }
  }
   
  /**
   * Gets a fragment of a transcript, given its ID and the ID of an annotation in it that
   * defines the desired fragment, and containing only the given layers.
   * <p>The given annotation defines both the start and end anchors of the fragment, and also 
   * which annotations on descendant layers will be included. 
   * So the resulting fragment will include:
   * <ul>
   *  <li>the given defining annotation</li>
   *  <li>its parent annotation, and all ancestors, but their anchors are included only if the 
   *      defining annotation t-includes the ancestor</li>
   *  <li>all descendants of the defining annotation or any of its ancestors, in the given
   *      layers, which are t-included by the defining annotation (but not annotations from
   *      those layers that the defining annotation t-includes but which aren't directly related,
   *      so in the case of simultaneous speech, only the words of the speaker of the defining
   *      utterance will be included, not words spoken by other speakers.
   *      </li>
   * </ul>
   * All annotations included in the fragment also have their {@link Layer} definition appear in 
   * transcript's {@link Schema}, whether or not they're mentioned in the
   * <var>layerId</var> list. 
   * @param transcriptId The ID of the transcript.
   * @param annotationId The ID of an annotation that defines the bounds of the fragment.
   * @param layerIds The IDs of the layers to load, or null if only transcript data is required.
   * @return The identified transcript fragment.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   * @throws GraphNotFoundException If the transcript was not found in the store.
   */
  public Graph getFragment(String transcriptId, String annotationId, String[] layerIds) 
    throws StoreException, PermissionException, GraphNotFoundException {
    try {
      final Graph graph = getTranscript(transcriptId, null); // load just basic information
      final int ag_id = (Integer)graph.get("@ag_id");
      Schema schema = getSchema();

      final Graph fragment = new Graph();
      fragment.setGraph(graph);
      fragment.setMediaProvider(new StoreGraphMediaProvider(fragment, this));
      fragment.put("@ag_id", graph.get("@ag_id")); 
      fragment.getSchema().copyLayerIdsFrom(schema);

      // decompose the annotation ID
      String scope = null;
      Integer layer_id = null;
      Long annotation_id = null;
      try { // most likely a spanning annotation like 'utterance'
        Object[] o = fmtAnnotationId.parse(annotationId);
        scope = o[0].toString();
        layer_id = ((Long)o[1]).intValue();
        annotation_id = (Long)o[2];
      } catch(ParseException exception) {
        throw new StoreException("Invalid annotation ID: " + annotationId);
      }

      // keep track of layer loading, so we don't cover old ground
      HashSet<String> loadedLayers = new HashSet<String>();
      loadedLayers.add(fragment.getLayerId()); // count the schema root as loaded

      // get the layer of the defining annotation
      PreparedStatement sqlLayer = getConnection().prepareStatement(
        "SELECT short_description FROM layer WHERE layer_id = ?");
      sqlLayer.setInt(1, layer_id);
      ResultSet rsLayer = sqlLayer.executeQuery();
      if (!rsLayer.next())
        throw new StoreException("Invalid layer_id for " + annotationId + ": " + layer_id);
      final Layer definingLayer = schema.getLayer(rsLayer.getString("short_description"));
      rsLayer.close();
      sqlLayer.close();
      fragment.addLayer(definingLayer);
      loadedLayers.add(definingLayer.getId());
	 
      // get the defining annotation and its anchors
      PreparedStatement sqlAnnotation = getConnection().prepareStatement(
        "SELECT layer.*,"
        +" start.offset AS start_offset, start.alignment_status AS start_alignment_status,"
        +" start.annotated_by AS start_annotated_by, start.annotated_when AS start_annotated_when,"
        +" end.offset AS end_offset, end.alignment_status AS end_alignment_status,"
        +" end.annotated_by AS end_annotated_by, end.annotated_when AS end_annotated_when"
        +" FROM annotation_layer_? layer"
        +" INNER JOIN anchor start ON layer.start_anchor_id = start.anchor_id"
        +" INNER JOIN anchor end ON layer.end_anchor_id = end.anchor_id"
        +" WHERE layer.ag_id = ? AND annotation_id = ?");
      sqlAnnotation.setInt(1, layer_id);
      sqlAnnotation.setInt(2, ag_id);
      sqlAnnotation.setLong(3, annotation_id);
      ResultSet rsAnnotation = sqlAnnotation.executeQuery();
      if (!rsAnnotation.next())
        throw new StoreException("Could not find annotation: " + annotationId);
      final Annotation definingAnnotation
        = annotationFromResult(rsAnnotation, definingLayer, fragment);
      final Anchor definingStart = anchorFromResult(rsAnnotation, "start_");
      final Anchor definingEnd = anchorFromResult(rsAnnotation, "end_");
      int definingOrdinal = definingAnnotation.getOrdinal();
      fragment.addAnchor(definingStart);
      fragment.addAnchor(definingEnd);
      rsAnnotation.close();
      if (definingStart.getOffset() == null)
        throw new StoreException("Fragment for " + annotationId + ": start has no offset");
      if (definingEnd.getOffset() == null)
        throw new StoreException("Fragment for " + annotationId + ": end has no offset");	 
      fragment.setId(Graph.FragmentId(graph, definingStart, definingEnd));
      fragment.addAnnotation(definingAnnotation);
	 
      // get all ancestors too
      Layer ancestorLayer = getLayer(definingLayer.getParentId());
      String ancestorId = definingAnnotation.getParentId();
      String childLayerId = definingLayer.getId();
      int childOrdinal = definingOrdinal;
      while (!ancestorLayer.getId().equals(fragment.getSchema().getRoot().getId())) {
        // add layer to schema
        fragment.addLayer(ancestorLayer);
        loadedLayers.add(ancestorLayer.getId());

        // load the parent annotation
        try { // most likely a spanning annotation like 'utterance'
          Object[] o = fmtAnnotationId.parse(ancestorId);
          scope = o[0].toString();
          layer_id = ((Long)o[1]).intValue();
          annotation_id = (Long)o[2];
        } catch(ParseException exception) {
          try { // most likely a spanning annotation like 'utterance'
            Object[] o = fmtMetaAnnotationId.parse(ancestorId);
            scope = "";
            layer_id = ((Long)o[0]).intValue();
            annotation_id = Long.valueOf(o[1].toString());
          } catch(ParseException exception2) {
            throw new StoreException(
              "Invalid ancestor ID on layer "+ancestorLayer+": " + ancestorId);
          }
        }
	    
        if (ancestorLayer.getId().equals(schema.getParticipantLayerId())) {
          // get the participant's name
          PreparedStatement sqlParticipant = getConnection().prepareStatement(
            "SELECT name FROM speaker WHERE speaker_number = ?");
          sqlParticipant.setInt(1, annotation_id.intValue());
          ResultSet rsParticipant = sqlParticipant.executeQuery();
          if (rsParticipant.next()) {
            // add participant annotation
            Annotation participant = new Annotation(
              ancestorId, rsParticipant.getString("name"), ancestorLayer.getId());
            participant.setParentId(fragment.getId());
            participant.setOrdinalMinimum(childLayerId, childOrdinal);
            fragment.addAnnotation(participant);
            ancestorId = participant.getParentId();

            childLayerId = ancestorLayer.getId();
            childOrdinal = participant.getOrdinal();
          } // participant found

          rsParticipant.close();
          sqlParticipant.close();
        } else { // not participant layer
          sqlAnnotation.setInt(1, layer_id);
          sqlAnnotation.setLong(3, annotation_id);
          rsAnnotation = sqlAnnotation.executeQuery();
          if (!rsAnnotation.next()) break;
          Annotation annotation = annotationFromResult(
            rsAnnotation, ancestorLayer, fragment);

          annotation.setOrdinalMinimum(childLayerId, childOrdinal);

          fragment.addAnnotation(annotation);
          ancestorId = annotation.getParentId();

          childLayerId = ancestorLayer.getId();
          childOrdinal = annotation.getOrdinal();
	       
          // add anchors?
          Anchor ancestorStart = anchorFromResult(rsAnnotation, "start_");
          Anchor ancestorEnd = anchorFromResult(rsAnnotation, "end_");
          rsAnnotation.close();
          if (definingAnnotation.includesOffset(ancestorStart.getOffset())
              && (definingAnnotation.includesOffset(ancestorEnd.getOffset())
                  || definingEnd.getOffset().equals(ancestorEnd.getOffset()))) {
            if (fragment.getAnchor(ancestorStart.getId()) == null) {
              // start anchor isn't in graph yet
              fragment.addAnchor(ancestorStart);
            }
            if (fragment.getAnchor(ancestorEnd.getId()) == null) {
              // end anchor isn't in graph yet
              fragment.addAnchor(ancestorEnd);
            }
          }
        } // not participant layer
	    
        // next ancestor
        ancestorLayer = getLayer(ancestorLayer.getParentId());	 
      } // next ancestor
      sqlAnnotation.close();
	 
      if (layerIds != null) { // set up full schema
        // add specified layers
        for (String layerId : layerIds) {
          if (loadedLayers.contains(layerId)) continue;
          Layer layer = getLayer(layerId);
          fragment.addLayer(layer);
        }
        // we also need to add any missing layers to ensure the hierarchy is complete
        // this is because we can't tell a grandchild is a descendant of the defining 
        // annotation unless the intervening child is present
        boolean foundMissingLayers = true;
        while (foundMissingLayers) {
          foundMissingLayers = false;
          for (Layer layer : new Vector<Layer>(fragment.getSchema().getLayers().values())) {
            // is the parent in the schema?
            if (layer.getParent() == null && layer != fragment.getSchema().getRoot()) {
              fragment.addLayer(getLayer(layer.getParentId()));
              foundMissingLayers = true;
            }
          } // next existing layer
        } // next round of checking

        final PreparedStatement sqlAnnotationsByParent = getConnection().prepareStatement(
          "SELECT layer.*,"
          +" start.offset AS start_offset, start.alignment_status AS start_alignment_status,"
          +" start.annotated_by AS start_annotated_by, start.annotated_when AS start_annotated_when,"
          +" end.offset AS end_offset, end.alignment_status AS end_alignment_status,"
          +" end.annotated_by AS end_annotated_by, end.annotated_when AS end_annotated_when"
          +" FROM annotation_layer_? layer"
          +" INNER JOIN anchor start ON layer.start_anchor_id = start.anchor_id"
          +" INNER JOIN anchor end ON layer.end_anchor_id = end.anchor_id"
          +" WHERE layer.ag_id = ? AND parent_id = ?"
          +" ORDER BY start.offset, end.offset DESC, annotation_id");
        sqlAnnotationsByParent.setInt(2, ag_id);
        final PreparedStatement sqlAnnotationsByOffset = getConnection().prepareStatement(
          "SELECT layer.*,"
          +" start.offset AS start_offset, start.alignment_status AS start_alignment_status,"
          +" start.annotated_by AS start_annotated_by, start.annotated_when AS start_annotated_when,"
          +" end.offset AS end_offset, end.alignment_status AS end_alignment_status,"
          +" end.annotated_by AS end_annotated_by, end.annotated_when AS end_annotated_when"
          +" FROM annotation_layer_? layer"
          +" INNER JOIN anchor start ON layer.start_anchor_id = start.anchor_id"
          +" INNER JOIN anchor end ON layer.end_anchor_id = end.anchor_id"
          +" WHERE layer.ag_id = ? AND parent_id = ?"
          + " AND start.offset >= ? AND end.offset <= ?"
          +" ORDER BY start.offset, end.offset DESC, annotation_id");
        sqlAnnotationsByOffset.setInt(2, ag_id);

        final PreparedStatement sqlTranscriptAttribute = getConnection().prepareStatement(
          "SELECT * FROM annotation_transcript WHERE ag_id = ? AND layer = ?");
        sqlTranscriptAttribute.setInt(1, ag_id);
        final PreparedStatement sqlCorpusLanguage = getConnection().prepareStatement(
          "SELECT corpus_language FROM corpus"
          +" INNER JOIN transcript ON transcript.corpus_name = corpus.corpus_name"
          +" WHERE ag_id = ?");
        sqlCorpusLanguage.setInt(1, ag_id);

        // now we've got a complete schema, we traverse top-down through it, adding annotations
        new LayerHierarchyTraversal<HashSet<String>>(loadedLayers, fragment.getSchema()) {
          protected void pre(Layer layer) {
            if (!getResult().contains(layer.getId())) {
              try {
                if (layer.get("layer_id") != null) {
                  PreparedStatement sql = sqlAnnotationsByParent;
                  if (!layer.getAncestors().contains(definingLayer)) { // not a descendant of defining annotation
                    // has to be t-included as well
                    sql = sqlAnnotationsByOffset;
                    sql.setDouble(4, definingStart.getOffset());
                    sql.setDouble(5, definingEnd.getOffset());
                  } // not a descendant of defining annotation
                  // load all annotations on this layer that are children of known annotations
                  Integer layer_id = (Integer)layer.get("layer_id");
                  if (layer_id >= 0) {
                    sql.setInt(1, layer_id);
                    for (Annotation parent : fragment.all(layer.getParentId())) {
                      String scope = null;
                      layer_id = null;
                      Long annotation_id = null;
                      if (layer.getParent().equals(fragment.getSchema().getRoot())) {
                        annotation_id = ((Integer)fragment.get("@ag_id")).longValue();
                        scope = (String)layer.get("scope");
                      } else {
                        try { // most likely a spanning annotation like 'utterance'
                          Object[] o = fmtAnnotationId.parse(parent.getId());
                          scope = o[0].toString();
                          layer_id = ((Long)o[1]).intValue();
                          annotation_id = (Long)o[2];
                        } catch(ParseException exception) {
                          System.err.println(
                            "Could not parse parent ID " + parent.getId()
                            + " on layer "+layer+": " + exception);
                        }
                      }
                      if (annotation_id == null) continue;
                      sql.setLong(3, annotation_id);
                      ResultSet rs = sql.executeQuery();
                      boolean setOrdinalMinimum = true;
                      while (rs.next()) {
                        Annotation annotation = annotationFromResult(
                          rs, layer, fragment);
				    
                        if (setOrdinalMinimum) {
                          parent.setOrdinalMinimum(
                            layer.getId(), annotation.getOrdinal());
                          setOrdinalMinimum = false;
                        }
				    
                        fragment.addAnnotation(annotation);
				    
                        // add anchors?
                        Anchor start = anchorFromResult(rs, "start_");
                        Anchor end = anchorFromResult(rs, "end_");
                        if (definingAnnotation.includesOffset(start.getOffset())
                            && (definingAnnotation.includesOffset(end.getOffset())
                                || definingEnd.getOffset().equals(end.getOffset()))) {
                          // add anchors too
                          if (fragment.getAnchor(start.getId()) == null) {
                            // start anchor isn't in graph yet
                            fragment.addAnchor(start);
                          }
                          if (fragment.getAnchor(end.getId()) == null) {
                            // end anchor isn't in graph yet
                            fragment.addAnchor(end);
                          }
                        } // add anchors too
                      } // next child
                      rs.close();
                    } // next parent
                  } else if (layer.getId().equals("episode")) { // episode
                    Object[] annotationIdParts = {
                      layer.get("layer_id"), graph.get("@family_id")};
                    Annotation episode = new Annotation(
                      fmtMetaAnnotationId.format(annotationIdParts), 
                      ""+graph.get("@series"), layer.getId());
                    episode.setParentId(fragment.getId());
                    fragment.addAnnotation(episode);		     
                  }
                  // TODO 'system' layers...
                } else if (layer.getId().equals("transcript_type")) { // transcript type
                  PreparedStatement sqlType = getConnection().prepareStatement(
                    "SELECT transcript_type, t.type_id FROM transcript t"
                    +" INNER JOIN transcript_type tt ON tt.type_id = t.type_id"
                    +" WHERE t.ag_id = ?");
                  sqlType.setInt(1, ag_id);
                  ResultSet rsType = sqlType.executeQuery();
                  if (rsType.next()) {
                    // add graph-tag annotation
                    Object[] annotationIdParts = {"type", Integer.valueOf(ag_id)};
                    Annotation type = new Annotation(
                      fmtTranscriptAttributeId.format(annotationIdParts), 
                      rsType.getString("transcript_type"), layer.getId());
                    type.setParentId(fragment.getId());
                    fragment.addAnnotation(type);
                  }
                  rsType.close();
                  sqlType.close();
                } else if (layer.getId().startsWith("transcript_")) {
                  // transcript attribute
                  sqlTranscriptAttribute.setString(2, layer.get("attribute").toString());
                  ResultSet rs = sqlTranscriptAttribute.executeQuery();
                  if (rs.next() && rs.getString("label").length() > 0) {
                    Object[] annotationIdParts = {
                      layer.get("attribute"),
                      Integer.valueOf(rs.getInt("annotation_id"))};
                    Annotation attribute = new Annotation(
                      fmtTranscriptAttributeId.format(annotationIdParts),
                      rs.getString("label"), layer.getId());
                    attribute.setParentId(fragment.getId());
                    fragment.addAnnotation(attribute);
                  } else if (layer.getId().equals("transcript_language")) {
                    // transcript_language can magically inherit from corpus
                    rs.close();
                    rs = sqlCorpusLanguage.executeQuery();
                    if (rs.next()) {
                      Object[] annotationIdParts = {
                        layer.get("attribute"), Integer.valueOf(ag_id)};
                      Annotation attribute = new Annotation(
                        fmtTranscriptAttributeId.format(annotationIdParts),
                        rs.getString("corpus_language"), layer.getId());
                      attribute.setParentId(fragment.getId());
                      fragment.addAnnotation(attribute);
                    }
                  }			  
                  rs.close();
                } else if (layer.getId().startsWith("participant_")) {
                  // participant attribute TODO
                }
              } catch(SQLException exception) {
                System.err.println(
                  "Couldn't load layer for fragment " + fragment.getId() + ": " + layer
                  +": " + exception);
              }
              getResult().add(layer.getId());
            } // not already loaded
          }
        };
        sqlCorpusLanguage.close();
        sqlTranscriptAttribute.close();
        sqlAnnotationsByParent.close();
        sqlAnnotationsByOffset.close();
      } // layerIds specified
	 
      // ensure that turn and utterance labels are set to the participant name
      for (Annotation a : fragment.all(fragment.getSchema().getTurnLayerId())) {
        Annotation who = a.first(fragment.getSchema().getParticipantLayerId());
        if (who != null) a.setLabel(who.getLabel());
      }
      for (Annotation a : fragment.all(fragment.getSchema().getUtteranceLayerId())) {
        Annotation who = a.first(fragment.getSchema().getParticipantLayerId());
        if (who != null) a.setLabel(who.getLabel());
      }

      fragment.commit();
      return fragment;
    } catch(SQLException exception) {
      System.err.println("SQL error: " + exception);
      exception.printStackTrace(System.err);
      throw new StoreException("Invalid query."); // TODO i18n
    }
  }   

  /**
   * Gets a fragment of a transcript, given its ID and the start/end offsets that define the 
   * desired fragment, and containing only the given layers.
   * @param transcriptId The ID of the transcript.
   * @param start The start offset of the fragment.
   * @param end The end offset of the fragment.
   * @param layerIds The IDs of the layers to load, or null if only transcript data is required.
   * @return The identified transcript fragment.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   * @throws GraphNotFoundException If the transcript was not found in the store.
   */
  public Graph getFragment(String transcriptId, double start, double end, String[] layerIds) 
    throws StoreException, PermissionException, GraphNotFoundException {
    try {
      final Graph graph = getTranscript(transcriptId, null); // load just basic information
      final int ag_id = (Integer)graph.get("@ag_id");
      Schema schema = getSchema();

      final Graph fragment = new Graph();
      fragment.setId(Graph.FragmentId(graph, start, end));
      fragment.setGraph(graph);
      fragment.setMediaProvider(new StoreGraphMediaProvider(fragment, this));
      fragment.put("@ag_id", graph.get("@ag_id")); 
      fragment.getSchema().copyLayerIdsFrom(schema);

      // load all annotations in the specified layers, by offset
      final PreparedStatement sqlAnnotationsByOffset = getConnection().prepareStatement(
        "SELECT layer.*,"
        +" start.offset AS start_offset, start.alignment_status AS start_alignment_status,"
        +" start.annotated_by AS start_annotated_by, start.annotated_when AS start_annotated_when,"
        +" end.offset AS end_offset, end.alignment_status AS end_alignment_status,"
        +" end.annotated_by AS end_annotated_by, end.annotated_when AS end_annotated_when"
        +" FROM annotation_layer_? layer"
        +" INNER JOIN anchor start ON layer.start_anchor_id = start.anchor_id"
        +" INNER JOIN anchor end ON layer.end_anchor_id = end.anchor_id"
        +" WHERE layer.ag_id = ?"
        + " AND start.offset >= ? AND end.offset <= ?"
        +" ORDER BY start.offset, end.offset DESC, annotation_id");
      double granularity = Constants.GRANULARITY_MILLISECONDS / 2; // TODO this should be a parameter
      sqlAnnotationsByOffset.setInt(2, ag_id);
      sqlAnnotationsByOffset.setDouble(3, start - granularity);
      sqlAnnotationsByOffset.setDouble(4, end + granularity);
      
      final PreparedStatement sqlTranscriptAttribute = getConnection().prepareStatement(
        "SELECT * FROM annotation_transcript WHERE ag_id = ? AND layer = ?");
      sqlTranscriptAttribute.setInt(1, ag_id);
      final PreparedStatement sqlCorpusLanguage = getConnection().prepareStatement(
        "SELECT corpus_language FROM corpus"
        +" INNER JOIN transcript ON transcript.corpus_name = corpus.corpus_name"
        +" WHERE ag_id = ?");
      sqlCorpusLanguage.setInt(1, ag_id);

      final HashSet<String> layersToLoad = new HashSet<String>(Arrays.asList(layerIds));

      // loading word tokens with sqlAnnotationsByOffset during the first phase below is
      // faster than loading them one at a time with getMatchingAnnotations in the second phase
      // if layersToLoad includes any word layers, then add "word" to layersToLoad too
      if (!layersToLoad.contains(schema.getWordLayerId())) { // "word" not selected
        for (String layerId : layersToLoad) {
          Layer layer = schema.getLayer(layerId);
          if (schema.getWordLayerId().equals(layer.getParentId())) { // word child layer
            // we need "word" anyway, so add it to layersToLoad
            layersToLoad.add(schema.getWordLayerId());
            break; // no need to keep looking
          }
        } // next layer to load
      } // "word" is not selected as a layer
      
      // traverse top-down through the schema, looking for layers to add
      final HashSet<String> loadedLayers = new HashSet<String>();
      new LayerHierarchyTraversal<HashSet<String>>(loadedLayers, schema) {
        // pre; before children means top-down
        protected void pre(Layer layer) {
          if (layersToLoad.contains(layer.getId())) { // load this layer
            Integer layer_id = (Integer)layer.get("layer_id");
            try {
              if (layer_id != null && layer_id >= 0) { // a temporal layer
                fragment.addLayer((Layer)layer.clone());
                getResult().add(layer.getId());
                sqlAnnotationsByOffset.setInt(1, layer_id);
                ResultSet rs = sqlAnnotationsByOffset.executeQuery();
                boolean setOrdinalMinimum = true;
                while (rs.next()) {
                  Annotation annotation = annotationFromResult(rs, layer, fragment);
                  if (layer.getId().equals(schema.getUtteranceLayerId())
                      || layer.getId().equals(schema.getTurnLayerId())) {
                    // label is speaker.speaker_number but should be speaker.name
                    String id = "m_-2_" + annotation.getLabel();
                    try {
                      // the participant may already be in the fragment
                      Annotation participant = fragment.getAnnotation(id);
                      if (participant == null) { // if not...
                        // ...load it from the database...
                        participant = getParticipant(id);
                        if (participant != null) { // ... and add it to the fragment
                          if (fragment.getLayer(participant.getLayerId()) == null) {
                            fragment.addLayer(
                              (Layer)schema.getLayer(participant.getLayerId()).clone());
                          }
                          fragment.addAnnotation(participant);
                        }
                      }
                      if (participant != null) {
                        annotation.setLabel(participant.getLabel());
                        // add participant to graph (it wouldn't otherwise be loaded)
                      }
                    } catch(Exception exception) {
                    }
                  }
                  
                  Annotation parent = fragment.getAnnotation(annotation.getParentId());
                  if (setOrdinalMinimum && parent != null) {
                    parent.setOrdinalMinimum(layer.getId(), annotation.getOrdinal());
                    setOrdinalMinimum = false;
                  }
                  
                  fragment.addAnnotation(annotation);
                  
                  // add anchors?
                  if (fragment.getAnchor(annotation.getStartId()) == null) {
                    // start anchor isn't in graph yet
                    fragment.addAnchor(anchorFromResult(rs, "start_"));
                  }
                  if (fragment.getAnchor(annotation.getEndId()) == null) {
                    // end anchor isn't in graph yet
                    fragment.addAnchor(anchorFromResult(rs, "end_"));
                  }
                } // next annotation
                rs.close();
              } else if (layer.getId().equals("transcript_type")) { // transcript type
                fragment.addLayer((Layer)layer.clone());
                PreparedStatement sqlType = getConnection().prepareStatement(
                  "SELECT transcript_type, t.type_id FROM transcript t"
                  +" INNER JOIN transcript_type tt ON tt.type_id = t.type_id"
                  +" WHERE t.ag_id = ?");
                sqlType.setInt(1, ag_id);
                ResultSet rsType = sqlType.executeQuery();
                if (rsType.next()) {
                  // add graph-tag annotation
                  Object[] annotationIdParts = {"type", Integer.valueOf(ag_id)};
                  Annotation type = new Annotation(
                    fmtTranscriptAttributeId.format(annotationIdParts), 
                    rsType.getString("transcript_type"), layer.getId());
                  type.setParentId(fragment.getId());
                  fragment.addAnnotation(type);
                }
                rsType.close();
                sqlType.close();
              } else if (layer.getId().startsWith("transcript_")) {
                fragment.addLayer((Layer)layer.clone());
                // transcript attribute
                sqlTranscriptAttribute.setString(2, layer.get("attribute").toString());
                ResultSet rs = sqlTranscriptAttribute.executeQuery();
                if (rs.next() && rs.getString("label").length() > 0) {
                  Object[] annotationIdParts = {
                    layer.get("attribute"),
                    Integer.valueOf(rs.getInt("annotation_id"))};
                  Annotation attribute = new Annotation(
                    fmtTranscriptAttributeId.format(annotationIdParts),
                    rs.getString("label"), layer.getId());
                  attribute.setParentId(fragment.getId());
                  fragment.addAnnotation(attribute);
                } else if (layer.getId().equals("transcript_language")) {
                  // transcript_language can magically inherit from corpus
                  rs.close();
                  rs = sqlCorpusLanguage.executeQuery();
                  if (rs.next()) {
                    Object[] annotationIdParts = {
                      layer.get("attribute"), Integer.valueOf(ag_id)};
                    Annotation attribute = new Annotation(
                      fmtTranscriptAttributeId.format(annotationIdParts),
                      rs.getString("corpus_language"), layer.getId());
                    attribute.setParentId(fragment.getId());
                    fragment.addAnnotation(attribute);
                  }
                }			  
                rs.close();
              } else if (layer.getId().startsWith("participant_")) {
                // participant attribute TODO
              }
            } catch (SQLException x) {
              System.err.println(
                "SqlGraphStore.getFragment: LayerHierarchyTraversal.pre: " + x);
            }
          } // selected layer
        } // end of pre()
      }; // end of LayerHierarchyTraversal
      sqlTranscriptAttribute.close();
      sqlCorpusLanguage.close();
      sqlAnnotationsByOffset.close();

      // now load all missing ancestors...

      // traverse bottom-up through the schema, looking for annotations without parents
      new LayerHierarchyTraversal<HashSet<String>>(loadedLayers, schema) {
        // post; after children means bottom-up
        protected void post(Layer layer) {
          // if we've loaded this layer
          if (getResult().contains(layer.getId())) { // check for missing parents
            Layer parentLayer = layer.getParent();
            if (parentLayer != null
                && !parentLayer.getId().equals(schema.getRoot().getId())) {
              if (!getResult().contains(parentLayer.getId())) {
                // haven't included the layer in the fragment yet
                fragment.addLayer((Layer)parentLayer.clone());
                getResult().add(parentLayer.getId());
              }

              // check for orphans
              HashSet<String> parentsToLoad = new HashSet<String>();
              for (Annotation annotation : fragment.all(layer.getId())) {
                if (annotation.getParent() == null) { // need to load the parent too
                  if (layer.getParentId().equals(schema.getParticipantLayerId())) {
                    // participant
                    try {
                      Annotation parent = getParticipant(annotation.getParentId());
                      if (parent != null) {
                        fragment.addAnnotation(parent);
                      }
                    } catch (Exception x) {
                      System.err.println(
                        "SqlGraphStore.getFragment : could not load participant: "+x);
                    }
                  } else { // other layer
                    parentsToLoad.add(annotation.getParentId());
                  } // other layer
                } // need to load parent
              } // next annotation

              if (parentsToLoad.size() > 0) {
                // load them all at once, which is quicker than one at a time
                StringBuilder idList = new StringBuilder();

                for (String id : parentsToLoad) {
                  if (idList.length() > 0) idList.append("','");
                  idList.append(id);                                             
                } // next annotation ID

                // we'll load their anchors too
                HashSet<String> anchorsToLoad = new HashSet<String>();

                try {
                  Annotation[] matches = getMatchingAnnotations("id IN ('"+idList+"')");
                  for (Annotation parent : matches) {
                    for (String childLayerId : parentLayer.getChildren().keySet()) {
                      Annotation firstChild = fragment.first(childLayerId);
                      if (firstChild != null) {
                        parent.setOrdinalMinimum(
                          childLayerId, firstChild.getOrdinal());
                      }
                    } // next child layer
                    fragment.addAnnotation(parent);
                    // load anchors?
                    if (fragment.getAnchor(parent.getStartId()) == null) {
                      // start anchor isn't in graph yet
                      anchorsToLoad.add(parent.getStartId());
                    }
                    if (fragment.getAnchor(parent.getEndId()) == null) {
                      // end anchor isn't in graph yet
                      anchorsToLoad.add(parent.getEndId());
                    }
                  } // next parent
                  
                  if (anchorsToLoad.size() > 0) {
                    try {
                      Anchor[] anchors = getAnchors(
                        graph.getId(), anchorsToLoad.toArray(new String[0]));
                      for (Anchor anchor : anchors) {
                        if (anchor.getOffset() != null
                            && anchor.getOffset() >= start
                            && anchor.getOffset() <= end) {
                          graph.addAnchor(anchor);
                        }
                      } // next anchor to load
                    } catch (Exception x) {
                      System.err.println(
                        "SqlGraphStore.getFragment : could not load parent anchors: "+x);
                    }
                  } // there are anchors to load
                } catch (Exception x) {
                  System.err.println(
                    "SqlGraphStore.getFragment : could not load parents: "+x);
                }
              } // there are parents to load on this layer
            } // layer parent should be processed
          } // have loaded this layer
        } // end of post
      }; // end of LayerHierarchyTraversal

      // if there are no bounding anchors,
      // add dummy anchors so that media fragment will be correct
      fragment.getOrCreateAnchorAt(start);
      fragment.getOrCreateAnchorAt(end);

      fragment.commit();
      return fragment;
    } catch(SQLException exception) {
      System.err.println("SQL error: " + exception);
      exception.printStackTrace(System.err);
      throw new StoreException("Invalid query."); // TODO i18n
    }
  }
  
  /**
   * Gets a series of fragments, given the series' ID, and only the given layers.
   * <p>This implementation expects <var>seriesId</var> to be a current
   * <tt>result.search_id</tt>. 
   * <p>The fragments are created lazily as required, so this method should return quickly.
   * @param seriesId The ID of the series.
   * @param layerIds The IDs of the layers to load, or null if only transcript data is required.
   * @return An enumerable series of fragments.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   * @throws GraphNotFoundException If the series identified by <var>seriesId</var> was
   * not found in the store. 
   */
  public MonitorableSeries<Graph> getFragmentSeries(String seriesId, String[] layerIds) 
    throws StoreException, PermissionException, GraphNotFoundException {
    try {
      return new ResultSeries(Long.parseLong(seriesId), this, layerIds);
    } catch(Exception exception) {
      throw new StoreException(exception);
    }
  }
  
  /**
   * Saves the given transcript. The graph can be partial e.g. include only some of the layers
   * that the stored version of the transcript contains, or be a fragment.
   * <p>The graph deltas are assumed to be set correctly, so if this is a new transcript, then
   * {@link Graph#getChange()} should return Change.Operation.Create, if it's an update,
   * Change.Operation.Update, and to delete, Change.Operation.Delete.  Correspondingly,
   * all {@link Anchor}s and {@link Annotation}s should have their changes set also.  If
   * {@link Graph#getChanges()} returns no changes, no action will be taken, and this
   * method returns false.
   * <p>After this method has executed, {@link Graph#commit()} is <em>not</em> called -
   * this must be done by the caller, if they want changes to be committed.
   * @param transcript The transcript to save.
   * @return true if changes were saved, false if there were no changes to save.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   * @throws GraphNotFoundException If the transcript was not found in the store.
   */
  public boolean saveTranscript(Graph transcript) 
    throws StoreException, PermissionException, GraphNotFoundException {
    requireEdit();
    if (transcript.getChange() == Change.Operation.NoChange) return false;
      
    Timers timers = new Timers();
    // timers.start("saveGraph");
    Schema schema = getSchema();
    Graph graph = transcript;
    
    try {
      // validate the graph before saving it
      // TODO ensure all layers are loaded before validation
      Validator v = new Validator();
      v.setMaxLabelLength(247);
      if (graph.getChange() == Change.Operation.Create) {
        v.setFullValidation(true);

        // a new transcript must have corpus, episode, and transcript type

        Layer layer = graph.getLayer("corpus");
        if (layer == null) { // add the layer
          layer = schema.getLayer("corpus");
          graph.addLayer(layer);
        }
        // if corpus isn't set, set to the first corpus
        if (graph.first("corpus") == null) { 
          graph.createTag(graph, layer.getId(), layer.getValidLabels().keySet().iterator().next())
            .setConfidence(Constants.CONFIDENCE_AUTOMATIC);
        } else if (graph.first("corpus").getLabel().length() == 0) {
          graph.first("corpus")
            .setLabel(layer.getValidLabels().keySet().iterator().next())
            .setConfidence(Constants.CONFIDENCE_AUTOMATIC);
        }

        layer = graph.getLayer("episode");
        if (layer == null) { // add the layer
          layer = schema.getLayer("episode");
          graph.addLayer(layer);
        }
        // if episode isn't set, set to the graph name without its file extension
        if (graph.first("episode") == null) { // set to the graph name, without the extension
          graph.createTag(graph, layer.getId(), graph.getId().replaceAll("\\.[^.]*$",""))
            .setConfidence(Constants.CONFIDENCE_AUTOMATIC);
        } else if (graph.first("episode").getLabel().length() == 0) {
          graph.first("episode")
            .setLabel(graph.getId().replaceAll("\\.[^.]*$",""))
            .setConfidence(Constants.CONFIDENCE_AUTOMATIC);
        }

        layer = graph.getLayer("transcript_type");
        if (layer == null) { // add the layer
          layer = schema.getLayer("transcript_type");
          graph.addLayer(layer);
        }
        // if transcript_type is not set, set it to the first option
        if (graph.first("transcript_type") == null) {
          graph.createTag(graph, layer.getId(), layer.getValidLabels().keySet().iterator().next())
            .setConfidence(Constants.CONFIDENCE_AUTOMATIC);
        } else if (graph.first("transcript_type").getLabel().length() == 0) {
          graph.first("transcript_type")
            .setLabel(layer.getValidLabels().keySet().iterator().next())
            .setConfidence(Constants.CONFIDENCE_AUTOMATIC);
        }
      }
      //v.setDebug(true);
      
      // detect media-processor type changes (mediapipe, reaper) and skip unnecessary validation
      // i.e. create/delete of annotations only on top-level childless non-interval layers
      boolean topLevelChildlessNonIntervalChangesOnly
        // it's a graph update:
        = graph.getChange() != Change.Operation.Create
        // there are no non-top-level/aligned changes:
        && !transcript.getChangedAnnotations()
        .filter(annotation -> annotation.getLayer() != null)
        .map(annotation -> annotation.getLayer())
        .distinct()
        .filter(
          // interval layers (interval boundaries would need checking)
          layer -> layer.getAlignment() == Constants.ALIGNMENT_INTERVAL
          // layers with child layers (children would need checking)
          || layer.getChildren().size() > 0
          // non-top-level layers (media processers are all top-level at this point)
          || (layer.getParent() != null
              && !layer.getParentId().equals(schema.getRoot().getId())))
        .findAny().isPresent();
      timers.end("topLevelChildlessNonIntervalChangesOnly"); // TODO false when it should be true
      // System.err.println("topLevelChildlessNonIntervalChangesOnly " + topLevelChildlessNonIntervalChangesOnly + " - " + timers);
      
      if (topLevelChildlessNonIntervalChangesOnly
          || graph.containsKey("@valid")) { // TODO remove this workaround
        // System.err.println(
        //   "Graph " + graph.getId() + ": skipping validation - "
        //   + (topLevelChildlessNonIntervalChangesOnly?
        //      "all top-level childless non-interval changes":"externally tagged as valid"));
        timers.start("topLevelChildlessNonIntervalChangesOnly");
        
        // but normalize anyway if it's new
        if (!topLevelChildlessNonIntervalChangesOnly // don't need to normalize with these changes
            && graph.getChange() == Change.Operation.Create
            && transcript.getSchema().getParticipantLayer() != null
            && transcript.getSchema().getTurnLayer() != null
            && transcript.getSchema().getUtteranceLayer() != null
            && transcript.getSchema().getWordLayer() != null) { // normalizable
          // normalize
          new Normalizer()
            .setMinimumTurnPauseLength(Double.parseDouble(getSystemAttribute("minTurnPause")))
            .transform(transcript);
        }
      } else {
        // timers.start("normalize");
        if (transcript.getSchema().getParticipantLayer() != null
            && transcript.getSchema().getTurnLayer() != null
            && transcript.getSchema().getUtteranceLayer() != null
            && transcript.getSchema().getWordLayer() != null) { // normalizable
          // normalize
          new Normalizer()
            .setMinimumTurnPauseLength(Double.parseDouble(getSystemAttribute("minTurnPause")))
            .transform(transcript);
        }
        // timers.end("normalize");
      
        // timers.start("validate");
        v.transform(graph);
        // timers.end("validate");
        // System.err.println("saveGraph: " + timers);
        if (v.getErrors().size() != 0) {
          StringBuffer messages = new StringBuffer();
          for (String s : v.getErrors()) {
            messages.append(s);
            messages.append("\n");
          }
          System.err.println("Invalid graph: " + graph.getId() + "\n" + messages);
          throw new StoreException("Invalid graph: " + graph.getId() + "\n" + messages);
          // } else {
          // System.err.println("Graph " + graph.getId() + " OK");
        }
      }

      // censor the graph?
      if (!topLevelChildlessNonIntervalChangesOnly) {
        String censorshipRegexp = getSystemAttribute("censorshipRegexp");
        if (censorshipRegexp != null && censorshipRegexp.length() > 0) { // censorship required
          Pattern censorshipPattern = Pattern.compile(censorshipRegexp);
          String censorshipLayer = getSystemAttribute("censorshipLayer");
          String censorshipLabel = getSystemAttribute("censorshipLabel");
          int censoredCount = 0;
          for (Annotation annotation : graph.all(censorshipLayer)) {
            if (censorshipPattern.matcher(annotation.getLabel()).matches()) {
              // matching annotation
              censoredCount++;
              // change all words to censorshipLabel
              for (Annotation word : annotation.all(schema.getWordLayerId())) {
                word.setLabel(censorshipLabel);
              } // next word
            } // matching annotation
          } // next annotation
        } // censorshipRegexp required
        
        // last minute reversed-anchor check, just in case
        for (Annotation a : graph.getAnnotationsById().values()) {
          if (a.getChange() == Change.Operation.Destroy) continue;
          Layer layer = a.getLayer();
          if (layer != null && layer.getAlignment() > 0) {
            if (a.getStart() != null) { // (could be in a fragment)
              if (a.getStart().getOffset() == null) {
                throw new StoreException(a.getId() + " (" + a.getLabel() + ") - no start offset.");
              }
              if (a.getEnd() != null) { // (could be in a fragment)
                if (a.getEnd().getOffset() == null) {
                  throw new StoreException(a.getId() + " (" + a.getLabel() + ") - no end offset.");
                }
                if (a.getStart().getOffset() > a.getEnd().getOffset()) {
                  throw new StoreException(
                    a.getId() + " (" + a.getLabel() + ") - backwards: "
                    +a.getStart()+"["+a.getStart().getId()+"]-"
                    +a.getEnd()+"["+a.getEnd().getId()+"].");
                }
            } // end is set
            } // start is set
          }
        } // next annotation
      } // !mediaProcesserTypeChanges
      
      if (graph.getChange() == Change.Operation.Create) {
        // create the graph, to generate the ag_id
        PreparedStatement sql = getConnection().prepareStatement(
          "INSERT INTO transcript (transcript_id, offset_units, create_user, create_date)"
          +" VALUES (?,?,?,Now())");
        sql.setString(1, graph.getId());
        sql.setString(2, graph.getOffsetUnits());
        sql.setString(3, user);
        sql.executeUpdate();
        sql.close();
        sql = getConnection().prepareStatement("SELECT LAST_INSERT_ID()");
        ResultSet rs = sql.executeQuery();
        rs.next();
        graph.put("@ag_id", Integer.valueOf(rs.getInt(1)));
        rs.close();
        sql.close();
        saveGraphChanges(graph);
      } else {
        // find the ag_id if it's not already known
        if (!graph.containsKey("@ag_id")) {
          PreparedStatement sql = getConnection().prepareStatement(
            "SELECT ag_id FROM transcript WHERE transcript_id = ?");
          sql.setString(1, graph.sourceGraph().getId()); // (sourceGraph in case of fragment)
          ResultSet rs = sql.executeQuery();
          try {		  
            if (!rs.next()) throw new GraphNotFoundException(graph.getId());
            graph.put("@ag_id", Integer.valueOf(rs.getInt("ag_id")));
          } finally {
            sql.close();
            rs.close();
          }
	       
        }
      }
      // parse from string, just in case it was set as a String not an Integer
      int iAgId = Integer.parseInt(graph.get("@ag_id").toString());

      // check changes
      List<Annotation> changedAnnotations = transcript.getChangedAnnotationsOrdered();
      Set<Anchor> changedAnchors = transcript.getChangedAnchors().collect(Collectors.toSet());
      if (changedAnchors.size() == 0 && changedAnnotations.size() == 0) return false;

      boolean utteranceChanges = graph.getChange() == Change.Operation.Create
        || changedAnnotations.stream()
        .filter(a -> schema.getUtteranceLayerId().equals(a.getLayerId()))
        .findAny().isPresent();
      boolean wordChanges = changedAnnotations.stream()
        .filter(a -> schema.getWordLayerId().equals(a.getLayerId()))
        .findAny().isPresent();
      boolean segmentChanges = changedAnnotations.stream()
        .filter(a -> "segment".equals(a.getLayerId()))
        .findAny().isPresent();
      // forced-alignment to a phrase layer can lead to the phrase annotation ordinals
      // starting at 1 at the beginning of each utterance
      // we need to track the phrase layers with new annotations,
      // and the corresponding turn parents, so we can correct the ordinals based on previous
      // peers that are not in the fragment
      boolean updatingFragment = graph.isFragment();
      // the key is the layer_id, the value is a set of parent IDs of annotations to fix:
      HashMap<Integer,HashSet<String>> adjustPhraseOrdinals
        = new HashMap<Integer,HashSet<String>>();
      Set<Anchor> createdThenDestroyedAnchors = new HashSet<Anchor>();
      for (Anchor anchor : changedAnchors) {
        // must be able to parse object's ID
        try {
          if (anchor.getChange() != Change.Operation.Create) {
            Object[] o = fmtAnchorId.parse(anchor.getId());
            try {
              Long databaseId = (Long)o[0];
            } catch(ClassCastException castX) {
              throw new StoreException("Parsed anchor ID is not a Long integer:"
                                       + anchor.getId());
            }
          }
        } catch(ParseException parseX) {
          if (anchor.getChange() == Change.Operation.Destroy) {
            // if an anchor was created then destroyed in the same set of changes
            // the ID will be invalid, so ignore it
            createdThenDestroyedAnchors.add(anchor);
          } else {
            throw new StoreException(
              "Could not parse anchor ID:" + anchor.getId()
              + ":" + anchor.getChange() + " - "
              + anchor.endingAnnotations()
              .map(a->a.getId()+":"+a.getLayerId()+":"+a.getChange()+":"+a.getLabel())
              .collect(Collectors.joining(";"))
              + "->"
              + anchor.startingAnnotations()
              .map(a->a.getId()+":"+a.getLayerId()+":"+a.getChange()+":"+a.getLabel())
              .collect(Collectors.joining(";")));
          }
        }
      } // next anchor
      
      // remove created-then-destroyed anchors, we won't try to save them
      for (Anchor a : createdThenDestroyedAnchors) changedAnchors.remove(a);
      
      for (Annotation annotation : changedAnnotations) {
        if (annotation.getId().startsWith("m_")) {
          try {
            if (annotation.getChange() != Change.Operation.Create) {
              Object[] o = fmtMetaAnnotationId.parse(annotation.getId());
            }
          } catch(ParseException parseX) {
            throw new StoreException("Could not parse special annotation ID:"
                                     + annotation.getId());
          }
        } else if (annotation.getId().startsWith("t|")) {
          try {
            if (annotation.getChange() != Change.Operation.Create) {
              Object[] o = fmtTranscriptAttributeId.parse(annotation.getId());
            }
          } catch(ParseException parseX) {
            throw new StoreException("Could not parse transcript attribute ID:"
                                     + annotation.getId());
          }
        } else if (annotation.getId().startsWith("p|")) {
          try {
            if (annotation.getChange() != Change.Operation.Create) {
              Object[] o = fmtParticipantAttributeId.parse(annotation.getId());
            }
          } catch(ParseException parseX) {
            throw new StoreException("Could not parse participant attribute ID:"
                                     + annotation.getId());
          }
        } else if (annotation.getId().equals("audio_prompt")) {
          // no particular validation, so drop through
        } else {
          Layer layer = schema.getLayer(annotation.getLayerId());
          if (layer != null) {
            if (updatingFragment
                && annotation.getChange() == Change.Operation.Create
                && layer.getParentId().equals(schema.getTurnLayerId())
                && layer.getAlignment() == Constants.ALIGNMENT_INTERVAL
                && layer.getPeers()
                && !layer.getId().equals(schema.getWordLayerId())
                /* technically, peers shouldn't overlap, but in reality
                   layer created with the current LaBB-CAT UI have peersOverlap = true
                   && !layer.getPeersOverlap()*/) {
              // we're updating a span layer from within a fragment (e.g. forced alignment),
              // so the ordinals might incorrecty start at 1
              // so we'll fix them up later
              Integer layer_id = (Integer)layer.get("layer_id");
              if (layer_id != null && !adjustPhraseOrdinals.containsKey(layer_id)) {
                // haven't noticed this layer yet, so add it to the collection
                adjustPhraseOrdinals.put(layer_id, new HashSet<String>());
              }
              // ensure the turn_annotation_id is included in the ordinal fix below
              adjustPhraseOrdinals.get(layer_id)
                .add(annotation.getParentId());
            }
          } // layer isn't null
          try {
            if (annotation.getChange() != Change.Operation.Create) {
              Object[] o = fmtAnnotationId.parse(annotation.getId());
              String scope = o[0].toString();
              if (scope.length() != 0 
                  && !scope.equalsIgnoreCase(SqlConstants.SCOPE_EPISODE) 
                  && !scope.equalsIgnoreCase(SqlConstants.SCOPE_META) 
                  && !scope.equalsIgnoreCase(SqlConstants.SCOPE_WORD) 
                  && !scope.equalsIgnoreCase(SqlConstants.SCOPE_SEGMENT) 
                  && !scope.equalsIgnoreCase(SqlConstants.SCOPE_PARTICIPANT)) {
                throw new StoreException("Parsed annotation scope is not recognised:"
                                         + annotation.getId() + " - " + scope);
              }
              try {
                Long layerId = (Long)o[1];
              } catch(ClassCastException castX) {
                throw new StoreException(
                  "Parsed annotation layer ID is not an Integer:"
                  + annotation.getId() + " - " + o[1]);
              }
              try {
                Long databaseId = (Long)o[2];
              } catch(ClassCastException castX) {
                throw new StoreException(
                  "Parsed annotation ID is not a Long integer:"
                  + annotation.getId() + " - " + o[2]);
              }
            } // not creating
          } catch(ParseException parseX) {
            // if it's for destroying, we don't care
            if (annotation.getChange() != Change.Operation.Destroy) {
              throw new StoreException(
                "Could not parse annotation ID " + annotation.getId()
                + " : " + annotation.toJsonString() + " ("+annotation.getChange());//+" - "+change.getOperation()+")");
            }
          }
        } // not a participant annotation
      } // next change

      // create a lookup list of participant names
      HashMap<String,String> participantNameToNumber = new HashMap<String,String>();
      PreparedStatement sqlParticipant = getConnection().prepareStatement(
        "SELECT speaker.speaker_number, speaker.name"
        +" FROM speaker"
        +" INNER JOIN transcript_speaker ON transcript_speaker.speaker_number = speaker.speaker_number"
        +" WHERE ag_id = ? ORDER BY speaker.name");
      sqlParticipant.setInt(1, iAgId);
      ResultSet rsParticipant = sqlParticipant.executeQuery();
      while (rsParticipant.next()) {
        participantNameToNumber.put(
          rsParticipant.getString("name"), rsParticipant.getString("speaker_number"));
      } // next participant
      rsParticipant.close();
      sqlParticipant.close();
      
      // process changes
      // timers.start("process changes");      
      PreparedStatement sqlLastId = getConnection().prepareStatement("SELECT LAST_INSERT_ID()");
      PreparedStatement sqlInsertAnchor = getConnection().prepareStatement(
        "INSERT INTO anchor"
        +" (ag_id, `offset`, alignment_status, annotated_by, annotated_when)"
        +" VALUES (?, ?, ?, ?, ?)");
      sqlInsertAnchor.setInt(1, iAgId);
      PreparedStatement sqlUpdateAnchor = getConnection().prepareStatement(
        "UPDATE anchor"
        +" SET `offset` = ?, alignment_status = ?, annotated_by = ?, annotated_when = ?"
        +" WHERE anchor_id = ?");
      PreparedStatement sqlCheckAnchor = getConnection().prepareStatement(
        "SELECT COUNT(*) FROM annotation_layer_?"
        +" WHERE start_anchor_id = ? OR end_anchor_id = ?");
      // create a list of layers to check before deleting an anchor
      PreparedStatement sqlLayers = getConnection().prepareStatement(
        "SELECT layer_id FROM layer ORDER BY layer_id");
      ResultSet rsLayers = sqlLayers.executeQuery();
      HashSet<Integer> layerIds = new HashSet<Integer>();
      while (rsLayers.next()) layerIds.add(Integer.valueOf(rsLayers.getInt("layer_id")));
      rsLayers.close();
      sqlLayers.close();
      PreparedStatement sqlDeleteAnchor = getConnection().prepareStatement(
        "DELETE FROM anchor WHERE anchor_id = ?");

      PreparedStatement sqlInsertFreeformAnnotation = getConnection().prepareStatement(
        "INSERT INTO annotation_layer_?"
        + " (ag_id, label, label_status, start_anchor_id, end_anchor_id,"
        + " parent_id, ordinal, annotated_by, annotated_when)"
        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
      sqlInsertFreeformAnnotation.setInt(2, iAgId);
      PreparedStatement sqlInsertMetaAnnotation = getConnection().prepareStatement(
        "INSERT INTO annotation_layer_?"
        + " (ag_id, label, label_status, start_anchor_id, end_anchor_id,"
        + " parent_id, ordinal, annotated_by, annotated_when,"
        + " turn_annotation_id)"
        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
      sqlInsertMetaAnnotation.setInt(2, iAgId);
      PreparedStatement sqlInsertWordAnnotation = getConnection().prepareStatement(
        "INSERT INTO annotation_layer_?"
        + " (ag_id, label, label_status, start_anchor_id, end_anchor_id,"
        + " parent_id, ordinal, annotated_by, annotated_when,"
        + " turn_annotation_id,"
        + " ordinal_in_turn, word_annotation_id)"
        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
      sqlInsertWordAnnotation.setInt(2, iAgId);
      PreparedStatement sqlInsertSegmentAnnotation = getConnection().prepareStatement(
        "INSERT INTO annotation_layer_?"
        + " (ag_id, label, label_status, start_anchor_id, end_anchor_id,"
        + " parent_id, ordinal, annotated_by, annotated_when,"
        + " turn_annotation_id," 
        + " ordinal_in_turn, word_annotation_id,"
        + " ordinal_in_word, segment_annotation_id)"
        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
      sqlInsertSegmentAnnotation.setInt(2, iAgId);
      PreparedStatement sqlUpdateFreeformAnnotation = getConnection().prepareStatement(
        "UPDATE annotation_layer_?"
        + " SET label = ?, label_status = ?, start_anchor_id = ?, end_anchor_id = ?,"
        + " parent_id = ?, ordinal = ?, annotated_by = ?, annotated_when = ?"
        + " WHERE annotation_id = ?");
      PreparedStatement sqlUpdateMetaAnnotation = getConnection().prepareStatement(
        "UPDATE annotation_layer_?"
        + " SET label = ?, label_status = ?, start_anchor_id = ?, end_anchor_id = ?,"
        + " turn_annotation_id = ?,"
        + " parent_id = ?, ordinal = ?, annotated_by = ?, annotated_when = ?"
        + " WHERE annotation_id = ?");
      PreparedStatement sqlUpdateWordAnnotation = getConnection().prepareStatement(
        "UPDATE annotation_layer_?"
        + " SET label = ?, label_status = ?, start_anchor_id = ?, end_anchor_id = ?,"
        + " turn_annotation_id = ?,"
        + " ordinal_in_turn = ?, word_annotation_id = ?,"
        + " parent_id = ?, ordinal = ?, annotated_by = ?, annotated_when = ?"
        + " WHERE annotation_id = ?");
      PreparedStatement sqlUpdateSegmentAnnotation = getConnection().prepareStatement(
        "UPDATE annotation_layer_?"
        + " SET label = ?, label_status = ?, start_anchor_id = ?,end_anchor_id = ?,"
        + " turn_annotation_id = ?,"
        + " ordinal_in_turn = ?, word_annotation_id = ?,"
        + " ordinal_in_word = ?, segment_annotation_id = ?,"
        + " parent_id = ?, ordinal = ?, annotated_by = ?, annotated_when = ?" 
        + " WHERE annotation_id = ?");
      PreparedStatement sqlSelectWordFields = getConnection().prepareStatement(
        "SELECT turn_annotation_id, ordinal_in_turn"
        + " FROM annotation_layer_" + SqlConstants.LAYER_TRANSCRIPTION
        + " WHERE annotation_id = ?");
      PreparedStatement sqlSelectSegmentFields = getConnection().prepareStatement(
        "SELECT ordinal_in_word"
        + " FROM annotation_layer_" + SqlConstants.LAYER_SEGMENT
        + " WHERE annotation_id = ?");
      PreparedStatement sqlUpdateTurnAnnotationId = getConnection().prepareStatement(
        "UPDATE annotation_layer_? SET turn_annotation_id = ? WHERE annotation_id = ?");
      PreparedStatement sqlUpdateWordAnnotationId = getConnection().prepareStatement(
        "UPDATE annotation_layer_? SET word_annotation_id = ? WHERE annotation_id = ?");
      PreparedStatement sqlUpdateSegmentAnnotationId = getConnection().prepareStatement(
        "UPDATE annotation_layer_? SET segment_annotation_id = ? WHERE annotation_id = ?");
      PreparedStatement sqlDeleteAnnotation = getConnection().prepareStatement(
        "DELETE FROM annotation_layer_? WHERE annotation_id = ?");

      PreparedStatement sqlInsertTranscriptAttribute = getConnection().prepareStatement(
        "INSERT INTO annotation_transcript"
        +" (ag_id, layer, label, annotated_by, annotated_when) VALUES (?,?,?,?,?)");
      sqlInsertTranscriptAttribute.setInt(1, iAgId);
      PreparedStatement sqlUpdateTranscriptAttribute = getConnection().prepareStatement(
        "UPDATE annotation_transcript"
        +" SET label = ?, annotated_by = ?, annotated_when = ?"
        +" WHERE ag_id = ? AND layer = ? AND annotation_id = ?");
      sqlUpdateTranscriptAttribute.setInt(4, iAgId);
      PreparedStatement sqlDeleteTranscriptAttribute = getConnection().prepareStatement(
        "DELETE FROM annotation_transcript"
        +" WHERE ag_id = ? AND layer = ? AND annotation_id = ?");
      sqlDeleteTranscriptAttribute.setInt(1, iAgId);
      // participant attributes look like children of graph in the schema
      // but they're actually store-wide entities.
      // for this reason, REPLACE INTO is required - an attribute that already exists
      // for the speaker may also be created by an uploaded graph.
      PreparedStatement sqlInsertParticipantAttribute = getConnection().prepareStatement(
        "REPLACE INTO annotation_participant"
        +" (speaker_number, layer, label, annotated_by, annotated_when) VALUES (?,?,?,?,?)");
      PreparedStatement sqlUpdateParticipantAttribute = getConnection().prepareStatement(
        "UPDATE annotation_participant SET label = ?, annotated_by = ?, annotated_when = ?"
        +" WHERE layer = ? AND annotation_id = ?");
      PreparedStatement sqlDeleteParticipantAttribute = getConnection().prepareStatement(
        "DELETE FROM annotation_participant WHERE layer = ? AND annotation_id = ?");
      PreparedStatement sqlDeleteAllParticipantAttributesOnLayer
        = getConnection().prepareStatement(
          "DELETE FROM annotation_participant WHERE speaker_number = ? AND layer = ?");
      PreparedStatement sqlUpdateParticipantPassword = getConnection().prepareStatement(
        "UPDATE speaker SET password = md5(?) WHERE speaker_number = ?");
      PreparedStatement sqlInsertParticipantCorpus = getConnection().prepareStatement(
        "INSERT INTO speaker_corpus (speaker_number, corpus_id)"
        +" SELECT ?, corpus_id FROM corpus WHERE corpus_name = ?");
      PreparedStatement sqlDeleteParticipantCorpus = getConnection().prepareStatement(
        "DELETE speaker_corpus.* FROM speaker_corpus"
        +" INNER JOIN corpus ON speaker_corpus.corpus_id = corpus.corpus_id"
        +" WHERE speaker_number = ? AND corpus_name = ?");

      PreparedStatement sqlAttributeLayers = getConnection().prepareStatement(
        "SELECT attribute FROM attribute_definition WHERE class_id = ?");
      HashSet<String> transcriptAttributeLayers = new HashSet<String>();
      sqlAttributeLayers.setString(1, "transcript");
      ResultSet rsAttributeLayers = sqlAttributeLayers.executeQuery();
      while (rsAttributeLayers.next()) {
        transcriptAttributeLayers.add("transcript_"+rsAttributeLayers.getString("attribute"));
      } // next layer
      rsAttributeLayers.close();
      HashSet<String> participantAttributeLayers = new HashSet<String>();
      sqlAttributeLayers.setString(1, "speaker");
      rsAttributeLayers = sqlAttributeLayers.executeQuery();
      while (rsAttributeLayers.next()) {
        participantAttributeLayers.add(
          "participant_"+rsAttributeLayers.getString("attribute"));
      } // next layer
      rsAttributeLayers.close();
      sqlAttributeLayers.close();

      try {
        // it's possible that some annotations will change on the way that were
        // otherwise unchanged - e.g. as final anchor IDs are set, etc.
        HashSet<Annotation> extraUpdates = new HashSet<Annotation>();

        // multi-value attributes are implemented by concatenating values together,
        // so we gather up attribute changes, and process them afterwards
        LinkedHashMap<String,Annotation> transcriptAttributes
          = new LinkedHashMap<String,Annotation>(); 
        LinkedHashMap<String,Annotation> participantAttributes
          = new LinkedHashMap<String,Annotation>(); 
        saveGraphChanges(graph);

        // anchor creations/updates
        for (Anchor anchor : changedAnchors) {
          if (anchor.getChange() != Change.Operation.Destroy) {
            saveAnchorChanges(anchor, extraUpdates, 
                              sqlInsertAnchor, sqlLastId, sqlUpdateAnchor, 
                              sqlCheckAnchor, layerIds, sqlDeleteAnchor);
          }
        } // next anchor
        for (Annotation annotation : changedAnnotations) {
          // Annotation annotation = (Annotation)change.getObject();
          if (annotation.getLayerId().equals("episode")
              || annotation.getLayerId().equals("corpus")
              || annotation.getLayerId().equals("transcript_type")
              || annotation.getLayerId().equals("main_participant")
              || annotation.getLayerId().equals("participant")
              || annotation.getLayerId().equals("audio_prompt")) { // special layer annotation
            saveSpecialAnnotationChanges(annotation, participantNameToNumber, schema);
          } else if (annotation.getLayer().getAncestors()
                     .contains(graph.getLayer("episode"))) {
            // episode tag annotation
            saveEpisodeAnnotationChanges(annotation, sqlLastId, schema);
          } else if (transcriptAttributeLayers.contains(annotation.getLayerId())) {
            // transcript attribute
            saveTranscriptAttributeChanges(
              annotation, 
              sqlInsertTranscriptAttribute, 
              sqlUpdateTranscriptAttribute, 
              sqlDeleteTranscriptAttribute);
          }
          else if (participantAttributeLayers.contains(annotation.getLayerId())) {
            // participant attribute
            saveParticipantAttributeChanges(
              annotation, 
              sqlInsertParticipantAttribute, 
              sqlUpdateParticipantAttribute, 
              sqlDeleteParticipantAttribute,
              sqlDeleteAllParticipantAttributesOnLayer,
              sqlUpdateParticipantPassword, sqlInsertParticipantCorpus,
              sqlDeleteParticipantCorpus);
          } else { // temporal annotation
            saveAnnotationChanges(
              annotation, extraUpdates, 
              sqlInsertFreeformAnnotation, sqlInsertMetaAnnotation, 
              sqlInsertWordAnnotation, sqlInsertSegmentAnnotation, sqlLastId, 
              sqlUpdateTurnAnnotationId, sqlUpdateWordAnnotationId, sqlUpdateSegmentAnnotationId, 
              sqlUpdateFreeformAnnotation, sqlUpdateMetaAnnotation, 
              sqlSelectWordFields, sqlSelectSegmentFields, 
              sqlUpdateWordAnnotation, sqlUpdateSegmentAnnotation, 
              sqlDeleteAnnotation,
              participantNameToNumber,
              schema);
          }
        } // next change
        // anchor deletions
        for (Anchor anchor : changedAnchors) {
          if (anchor.getChange() == Change.Operation.Destroy) {
            saveAnchorChanges(anchor, extraUpdates, 
                              sqlInsertAnchor, sqlLastId, sqlUpdateAnchor, 
                              sqlCheckAnchor, layerIds, sqlDeleteAnchor);
          }
        } // next anchor
        // timers.end("process changes");
        // System.err.println("saveGraph: " + timers);
	    
        // extras
        HashSet<Annotation> newExtraUpdates = new HashSet<Annotation>();
        for (Annotation annotation : extraUpdates) {
          saveAnnotationChanges(
            annotation, newExtraUpdates, 
            sqlInsertFreeformAnnotation, sqlInsertMetaAnnotation, 
            sqlInsertWordAnnotation, sqlInsertSegmentAnnotation, sqlLastId, 
            sqlUpdateTurnAnnotationId, sqlUpdateWordAnnotationId, sqlUpdateSegmentAnnotationId, 
            sqlUpdateFreeformAnnotation, sqlUpdateMetaAnnotation, 
            sqlSelectWordFields, sqlSelectSegmentFields, 
            sqlUpdateWordAnnotation, sqlUpdateSegmentAnnotation, 
            sqlDeleteAnnotation,
            participantNameToNumber,
            schema);
        } 
        assert newExtraUpdates.size() == 0 : "newExtraUpdates.size() == 0";

        // untag anchors and annotations
        for (Anchor a : graph.getAnchors().values()) a.remove("@SqlUpdated");
        for (Annotation a : graph.getAnnotationsById().values()) a.remove("@SqlUpdated");

        if (graph.getChange() != Change.Operation.Create) { // updating
          // ensure that tag annotations have their anchors updated with their parents,
          // even if they're not mentioned in the given graph
               
          // timers.start("update tag anchors");
          if (wordChanges) {
            PreparedStatement sqlFixWordTagAnchors = connection.prepareStatement(
              "UPDATE annotation_layer_?"
              +" INNER JOIN annotation_layer_0"
              +" ON annotation_layer_?.word_annotation_id = annotation_layer_0.annotation_id"
              +" SET annotation_layer_?.start_anchor_id = annotation_layer_0.start_anchor_id,"
              +" annotation_layer_?.end_anchor_id = annotation_layer_0.end_anchor_id"
              +" WHERE annotation_layer_?.ag_id = ?");
            sqlFixWordTagAnchors.setInt(6, iAgId);
            for (Layer child : schema.getWordLayer().getChildren().values()) {
              if (child.getAlignment() == Constants.ALIGNMENT_NONE) { // tag
                Integer layer_id = (Integer)child.get("layer_id");
                sqlFixWordTagAnchors.setInt(1, layer_id);
                sqlFixWordTagAnchors.setInt(2, layer_id);
                sqlFixWordTagAnchors.setInt(3, layer_id);
                sqlFixWordTagAnchors.setInt(4, layer_id);
                sqlFixWordTagAnchors.setInt(5, layer_id);
                sqlFixWordTagAnchors.executeUpdate();
              }
            } // next child
            sqlFixWordTagAnchors.close();
          }

          if (segmentChanges) {
            PreparedStatement sqlFixSegmentTagAnchors = connection.prepareStatement(
              "UPDATE annotation_layer_?"
              +" INNER JOIN annotation_layer_1"
              +" ON annotation_layer_?.segment_annotation_id = annotation_layer_1.annotation_id"
              +" SET annotation_layer_?.start_anchor_id = annotation_layer_1.start_anchor_id,"
              +" annotation_layer_?.end_anchor_id = annotation_layer_1.end_anchor_id"
              +" WHERE annotation_layer_?.ag_id = ?");
            sqlFixSegmentTagAnchors.setInt(6, iAgId);
            for (Layer child : schema.getLayer("segment").getChildren().values()) {
              if (child.getAlignment() == Constants.ALIGNMENT_NONE) { // tag
                Integer layer_id = (Integer)child.get("layer_id");
                sqlFixSegmentTagAnchors.setInt(1, layer_id);
                sqlFixSegmentTagAnchors.setInt(2, layer_id);
                sqlFixSegmentTagAnchors.setInt(3, layer_id);
                sqlFixSegmentTagAnchors.setInt(4, layer_id);
                sqlFixSegmentTagAnchors.setInt(5, layer_id);
                sqlFixSegmentTagAnchors.executeUpdate();
              }
            } // next child
            sqlFixSegmentTagAnchors.close();
          } // segmentChanges
          // timers.end("update tag anchors");

          // forced-alignment to a phrase layer can lead to the phrase annotation ordinals
          // starting at 1 at the beginning of each utterance
          // so we update the ordinals to be chronological
          // timers.start("update ordinals");
          for (Integer layer_id : adjustPhraseOrdinals.keySet()) {
            String turnIdList = adjustPhraseOrdinals.get(layer_id).stream()
              .map(uid -> uid.replace("em_11_", "")) // convert uid to turn_annotation_id
              .collect(Collectors.joining(","));
            if (turnIdList.length() > 0) {
              String sqlCreate = "CREATE TEMPORARY TABLE annotation_ordinal"
                +" SELECT u.annotation_id,"
                +" (SELECT COUNT(*)"
                +"  FROM annotation_layer_"+layer_id+" o"
                +"  INNER JOIN anchor o_start"
                +"  ON o.start_anchor_id = o_start.anchor_id"
                +"  WHERE o.turn_annotation_id = u.turn_annotation_id"
                +"   AND o_start.offset <= start.offset) ordinal"
                +"  FROM annotation_layer_"+layer_id+" u"
                +" INNER JOIN anchor start ON u.start_anchor_id = start.anchor_id"
                +" WHERE u.turn_annotation_id IN ("+turnIdList+")";
              PreparedStatement sql = connection.prepareStatement(sqlCreate);
              sql.executeUpdate();
              sql.close();

              String sqlUpdate = "UPDATE annotation_layer_"+layer_id+" u"
                +" INNER JOIN annotation_ordinal on u.annotation_id"
                +"  = annotation_ordinal.annotation_id"
                +" SET u.ordinal = annotation_ordinal.ordinal";
              sql = connection.prepareStatement(sqlUpdate);
              sql.executeUpdate();
              sql.close();

              String sqlDrop = "DROP TEMPORARY TABLE annotation_ordinal";
              sql = connection.prepareStatement(sqlDrop);
              sql.executeUpdate();
              sql.close();
              
            } // there are turn IDs
              
          } // next updating phrase layer
          // timers.end("update ordinals");
        } // not a new graph

        if (wordChanges || utteranceChanges) {
          // timers.start("word utterances");
          // (re-)link words to utterances for fast search result collation
          PreparedStatement sqlFindWordUtterance = connection.prepareStatement(
            "UPDATE annotation_layer_0"
            +" INNER JOIN anchor word_start"
            +" ON annotation_layer_0.start_anchor_id = word_start.anchor_id"
            +" INNER JOIN annotation_layer_12 utterance"
            +" ON utterance.turn_annotation_id = annotation_layer_0.turn_annotation_id"
            +" INNER JOIN anchor utterance_start"
            +"  ON utterance_start.anchor_id = utterance.start_anchor_id"
            +"  AND utterance_start.offset <= word_start.offset"
            +" INNER JOIN anchor utterance_end"
            +"  ON utterance_end.anchor_id = utterance.end_anchor_id"
            +"  AND (utterance_end.offset > word_start.offset"
            +"   OR (utterance_end.offset = word_start.offset"
            +"    AND utterance_end.offset = utterance_start.offset))"
            +" SET annotation_layer_0.utterance_annotation_id = utterance.annotation_id"
            +" WHERE annotation_layer_0.ag_id = ?");
          sqlFindWordUtterance.setInt(1, iAgId);
          sqlFindWordUtterance.executeUpdate();
          sqlFindWordUtterance.close();       
          // timers.end("word utterances");
        }

      } finally {
        sqlInsertAnchor.close();
        sqlUpdateAnchor.close();
        sqlCheckAnchor.close();
        sqlDeleteAnchor.close();
        sqlInsertFreeformAnnotation.close();
        sqlInsertMetaAnnotation.close();
        sqlInsertWordAnnotation.close();
        sqlInsertSegmentAnnotation.close();
        sqlLastId.close();
        sqlUpdateTurnAnnotationId.close();
        sqlUpdateWordAnnotationId.close();
        sqlUpdateSegmentAnnotationId.close();
        sqlUpdateFreeformAnnotation.close();
        sqlUpdateMetaAnnotation.close();
        sqlUpdateParticipantPassword.close();
        sqlSelectWordFields.close();
        sqlSelectSegmentFields.close();
        sqlUpdateWordAnnotation.close();
        sqlUpdateSegmentAnnotation.close();
        sqlDeleteAnnotation.close();
        sqlInsertTranscriptAttribute.close();
        sqlUpdateTranscriptAttribute.close();
        sqlDeleteTranscriptAttribute.close();
        sqlInsertParticipantAttribute.close();
        sqlUpdateParticipantAttribute.close();
        sqlDeleteParticipantAttribute.close();
        sqlDeleteAllParticipantAttributesOnLayer.close();
        sqlInsertParticipantCorpus.close();
        sqlDeleteParticipantCorpus.close();
      }
    } catch(SQLException exception) {
      System.err.println("saveTranscript: " + exception.toString());
      exception.printStackTrace(System.err);
      throw new StoreException("Invalid query."); // TODO i18n
    } catch(TransformationException invalid) {
      System.err.println("saveTranscript: " + invalid.toString());
      throw new StoreException("Graph was not valid", invalid);
    } catch(Throwable exception) {
      System.err.println("saveTranscript: " + exception.toString());
      exception.printStackTrace(System.err);
      throw new StoreException(exception);
    }
    // System.err.println("saveGraph finished.");
    // timers.end("saveGraph");
    // System.err.println("saveGraph: " + timers);
    return true;
  }

  /**
   * Saves the changes to the given graph.
   * @param graph The graph to save the changes of.
   * @throws SQLException If a database error occurs.
   */
  protected void saveGraphChanges(Graph graph) throws SQLException {
    // TODO save ordinal as episode index
    // TODO update offset in episode
  }

  /**
   * Constructs an Annotation from the given query result row.
   * @param rsAnnotation The query result row.
   * @param layer The annotation layer.
   * @param graph The graph. This can be null, in which case turns/utterances will not
   * have their participant name resolved, and freeform layers will not have their parent
   * set.
   * @return The annotation defined by the given row.
   * @throws SQLException
   */
  protected Annotation annotationFromResult(ResultSet rsAnnotation, Layer layer, Graph graph)
    throws SQLException {
    if (layer.containsKey("layer_id")) { // it's a layer from the layer table
      int iLayerId = ((Integer)layer.get("layer_id")).intValue();
      String scope = (String)layer.get("scope");
      Annotation annotation = new Annotation();
      annotation.setLabel(rsAnnotation.getString("label"));
      annotation.setConfidence(Integer.valueOf(rsAnnotation.getInt("label_status")));
      annotation.setLayerId(layer.getId());

      if (rsAnnotation.getString("annotated_by") != null) {
        annotation.setAnnotator(rsAnnotation.getString("annotated_by"));
      }
      if (rsAnnotation.getTimestamp("annotated_when") != null) {
        annotation.setWhen(rsAnnotation.getTimestamp("annotated_when"));
      }         
         
      if (iLayerId >= 0) { // normal temporal layer
        Object[] annotationIdParts = {
          scope.toLowerCase(), Integer.valueOf(iLayerId), 
          Long.valueOf(rsAnnotation.getLong("annotation_id"))};
        if (scope.equalsIgnoreCase(SqlConstants.SCOPE_FREEFORM)) annotationIdParts[0] = "";
        annotation.setId(fmtAnnotationId.format(annotationIdParts));
            
        String turnParentId = null;
        if (iLayerId == SqlConstants.LAYER_TURN 
            || iLayerId == SqlConstants.LAYER_UTTERANCE) { // turn or utterance
          // convert speaker_number label into participant name
          turnParentId = "m_-2_"+rsAnnotation.getString("label");
          Annotation participant = null;
          if (graph != null) {
            participant = graph.getAnnotation(turnParentId);
          }
          if (participant != null) {
            annotation.setLabel(participant.getLabel());
          }
        }
        // parent:
        if (iLayerId == SqlConstants.LAYER_SEGMENT) { // segment
          annotationIdParts[0] = SqlConstants.SCOPE_WORD;
          annotationIdParts[1] = Integer.valueOf(SqlConstants.LAYER_TRANSCRIPTION); // transcript word
          annotationIdParts[2] = Long.valueOf(rsAnnotation.getLong("word_annotation_id"));
          annotation.setParentId(fmtAnnotationId.format(annotationIdParts));
          annotation.setOrdinal(rsAnnotation.getInt("ordinal_in_word"));
        } else if (iLayerId == SqlConstants.LAYER_TRANSCRIPTION) { // transcription word
          annotationIdParts[0] = SqlConstants.SCOPE_META;
          annotationIdParts[1] = Integer.valueOf(SqlConstants.LAYER_TURN); // turn
          annotationIdParts[2] = Long.valueOf(rsAnnotation.getLong("turn_annotation_id"));
          annotation.setParentId(fmtAnnotationId.format(annotationIdParts));
          annotation.setOrdinal(rsAnnotation.getInt("ordinal_in_turn"));
        } else if (iLayerId == SqlConstants.LAYER_UTTERANCE) { // utterance
          annotationIdParts[0] = SqlConstants.SCOPE_META;
          annotationIdParts[1] = Integer.valueOf(SqlConstants.LAYER_TURN); // turn
          annotationIdParts[2] = Long.valueOf(rsAnnotation.getLong("turn_annotation_id"));
          annotation.setParentId(fmtAnnotationId.format(annotationIdParts));
          annotation.setOrdinal(rsAnnotation.getInt("ordinal"));
        } else if (iLayerId == SqlConstants.LAYER_TURN) { // turn
          annotation.setParentId(turnParentId);
          annotation.setOrdinal(rsAnnotation.getInt("ordinal"));
        } else if (scope.equalsIgnoreCase(SqlConstants.SCOPE_SEGMENT)) { // segment scope
          annotationIdParts[0] = SqlConstants.SCOPE_SEGMENT;
          annotationIdParts[1] = Integer.valueOf(SqlConstants.LAYER_SEGMENT); // segment
          annotationIdParts[2] = Long.valueOf(rsAnnotation.getLong("segment_annotation_id"));
          annotation.setParentId(fmtAnnotationId.format(annotationIdParts));
          annotation.setOrdinal(rsAnnotation.getInt("ordinal"));
        } else if (scope.equalsIgnoreCase(SqlConstants.SCOPE_WORD)) { // word scope
          annotationIdParts[0] = SqlConstants.SCOPE_WORD;
          annotationIdParts[1] = Integer.valueOf(SqlConstants.LAYER_TRANSCRIPTION); // transcription word
          annotationIdParts[2] = Long.valueOf(rsAnnotation.getLong("word_annotation_id"));
          annotation.setParentId(fmtAnnotationId.format(annotationIdParts));
          annotation.setOrdinal(rsAnnotation.getInt("ordinal"));
        } else if (scope.equalsIgnoreCase(SqlConstants.SCOPE_META)) { // meta scope
          annotationIdParts[0] = SqlConstants.SCOPE_META;
          annotationIdParts[1] = Integer.valueOf(SqlConstants.LAYER_TURN); // turn
          annotationIdParts[2] = Long.valueOf(rsAnnotation.getLong("turn_annotation_id"));
          annotation.setParentId(fmtAnnotationId.format(annotationIdParts));
          annotation.setOrdinal(rsAnnotation.getInt("ordinal"));
        } else { // freeform scope
          if (graph != null) {
            annotation.setParentId(graph.getId());
          }
          annotation.setOrdinal(rsAnnotation.getInt("ordinal"));
        } // freeform scope
            
        // anchor IDs
        Object[] anchorIdParts = { Long.valueOf(rsAnnotation.getLong("start_anchor_id"))};
        annotation.setStartId(fmtAnchorId.format(anchorIdParts));
        anchorIdParts[0] = Long.valueOf(rsAnnotation.getLong("end_anchor_id"));
        annotation.setEndId(fmtAnchorId.format(anchorIdParts));

      } else { // 'structural' layer
        Object[] annotationIdParts = {
          layer.get("layer_id"), rsAnnotation.getString("annotation_id")};
        annotation.setId(fmtMetaAnnotationId.format(annotationIdParts));
      } // 'structural' layer

      if (graph != null) annotation.setGraph(graph);

      return annotation;
    }  else if ("transcript".equals(layer.get("class_id"))) { // transcript attribute
      // read-only users get access to only public attributes
      if (!getUserRoles().contains("edit") && !"1".equals(layer.get("access"))) {
        return null;
      }
      Object[] annotationIdParts = {
        layer.get("attribute"), Integer.valueOf(rsAnnotation.getInt("annotation_id"))};
      Annotation attribute = new Annotation(
        fmtTranscriptAttributeId.format(annotationIdParts), 
        rsAnnotation.getString("label"), layer.getId());
      if (rsAnnotation.getString("annotated_by") != null) {
        attribute.setAnnotator(rsAnnotation.getString("annotated_by"));
      }
      if (rsAnnotation.getTimestamp("annotated_when") != null) {
        attribute.setWhen(rsAnnotation.getTimestamp("annotated_when"));
      }
      if (graph != null) {
        attribute.setParentId(graph.getId());
      }
      return attribute;
    } else if ("speaker".equals(layer.get("class_id"))) { // participant attribute
      // read-only users get access to only public attributes
      if (!getUserRoles().contains("edit") && !"1".equals(layer.get("access"))) {
        return null;
      }
      Object[] annotationIdParts = {
        layer.get("attribute"), Integer.valueOf(rsAnnotation.getInt("annotation_id"))};
      Annotation attribute = new Annotation(
        fmtParticipantAttributeId.format(annotationIdParts), 
        rsAnnotation.getString("label"), layer.getId());
      if (rsAnnotation.getString("annotated_by") != null) {
        attribute.setAnnotator(rsAnnotation.getString("annotated_by"));
      }
      if (rsAnnotation.getTimestamp("annotated_when") != null) {
        attribute.setWhen(rsAnnotation.getTimestamp("annotated_when"));
      }
      Object[] parentAnnotationIdParts = {
        Integer.valueOf(-2), rsAnnotation.getString("speaker_number")};         
      attribute.setParentId(fmtMetaAnnotationId.format(parentAnnotationIdParts));
      return attribute;
    } // transcript attribute
    else if (layer.getId().equals("transcript_type")) {
      Object[] annotationIdParts = {"type", rsAnnotation.getInt("ag_id")};
      Annotation type = new Annotation(
        fmtTranscriptAttributeId.format(annotationIdParts), 
        rsAnnotation.getString("label"), layer.getId());
      return type;
    } else if (layer.getParentId() == null) { // root layer - i.e. the graph itself
      if (graph == null) {
        graph = new Graph();
        graph.setId(rsAnnotation.getString("label"));
        graph.setLabel(rsAnnotation.getString("label"));
      }
      return graph;
    }
      
    return null; // could not identify the annotation type
  } // end of annotationFromResult()

  /**
   * Constructs an anchor from the given query result row.
   * @param rsAnchor The query results row containing the definition.
   * @param prefix The prefix for field names.
   * @return The anchor defined by the row.
   * @throws SQLException
   */
  protected Anchor anchorFromResult(ResultSet rsAnchor, String prefix) throws SQLException {
    Object[] anchorIdParts = { Long.valueOf(rsAnchor.getLong(prefix + "anchor_id"))};
    Anchor anchor = new Anchor().setId(fmtAnchorId.format(anchorIdParts));
    if (rsAnchor.getString(prefix + "offset") != null) { // offset not null
      anchor.setOffset(Double.valueOf(rsAnchor.getDouble(prefix + "offset")));
      anchor.setConfidence(Integer.valueOf(rsAnchor.getInt(prefix + "alignment_status")));
    }
    if (rsAnchor.getString(prefix+"annotated_by") != null) {
      anchor.setAnnotator(rsAnchor.getString(prefix+"annotated_by"));
    }
    if (rsAnchor.getTimestamp(prefix+"annotated_when") != null) {
      anchor.setWhen(rsAnchor.getTimestamp(prefix+"annotated_when"));
    }
    return anchor;
  } // end of anchorFromResult()


  /**
   * Saves the changes to the given anchor, and updates related annotations if the anchor
   * ID is changed.
   * @param anchor The anchor whose changes should be saved.
   * @param extraUpdates A set to add annotations which had no changes to save, but which
   * now must be updated because the anchor's ID is changing.
   * @param sqlInsertAnchor Prepared statement for inserting an anchor row.
   * @param sqlLastId Prepared statement for retrieving the last database ID created.
   * @param sqlUpdateAnchor Prepared statement for updating an anchor row.
   * @param sqlCheckAnchor Prepared statement for counting the number of anchors
   * currently using an anchor.
   * @param layerIds List of all layer_ids, for checking for annotations that use this anchor.
   * @param sqlDeleteAnchor Prepared statement for deleteing an anchor row.
   * @throws StoreException If an ID can't be parsed.
   * @throws SQLException If a database error occurs.
   */
  protected void saveAnchorChanges(
    Anchor anchor, HashSet<Annotation> extraUpdates, PreparedStatement sqlInsertAnchor,
    PreparedStatement sqlLastId, PreparedStatement sqlUpdateAnchor,
    PreparedStatement sqlCheckAnchor, HashSet<Integer> layerIds,
    PreparedStatement sqlDeleteAnchor) throws SQLException, StoreException {
      
    if (anchor.getConfidence() == null) {
      anchor.setConfidence(Integer.valueOf(Constants.CONFIDENCE_UNKNOWN));
    }
    switch (anchor.getChange()) {
      case Create: {
        // create anchor record
        if (anchor.getOffset() != null) {
          sqlInsertAnchor.setDouble(2, anchor.getOffset());
        } else {
          sqlInsertAnchor.setNull(2, java.sql.Types.DOUBLE);
        }
        sqlInsertAnchor.setInt(3, anchor.getConfidence());
        if (anchor.getAnnotator() != null) {
          sqlInsertAnchor.setString(4, anchor.getAnnotator());
        } else {
          sqlInsertAnchor.setString(4, getUser());
        }
        // if (anchor.getWhen() != null)
        // {
        //    sqlInsertAnchor.setTimestamp(5, new Timestamp(anchor.getWhen().getTime()));
        // }
        // else
        {
          sqlInsertAnchor.setTimestamp(5, new Timestamp(new java.util.Date().getTime()));
        }
        sqlInsertAnchor.executeUpdate();
        ResultSet rs = sqlLastId.executeQuery();
        rs.next();
        String oldId = anchor.getId();
        Object[] anchorIdParts = { Long.valueOf(rs.getLong(1)) };
        String newId = fmtAnchorId.format(anchorIdParts);
        rs.close();

        // change anchor ID (this updates referencing annotations)
        anchor.setId(newId);
	    
        break;
      } case Update: {
          // deduce the database anchor.anchor_id from the object anchor.id
          try {
            Object[] o = fmtAnchorId.parse(anchor.getId());
            Long anchorId = (Long)o[0];
            if (anchor.getOffset() != null) {
              sqlUpdateAnchor.setDouble(1, anchor.getOffset());
            } else {
              sqlUpdateAnchor.setNull(1, java.sql.Types.DOUBLE);
            }
            sqlUpdateAnchor.setInt(2, anchor.getConfidence());
            if (anchor.getAnnotator() != null) {
              sqlUpdateAnchor.setString(3, anchor.getAnnotator());
            } else {
              sqlUpdateAnchor.setString(3, getUser());
            }
            // if (anchor.getWhen() != null)
            // {
            // 	  sqlUpdateAnchor.setTimestamp(4, new Timestamp(anchor.getWhen().getTime()));
            // }
            // else
            {
              sqlUpdateAnchor.setTimestamp(4, new Timestamp(new java.util.Date().getTime()));
            }
            sqlUpdateAnchor.setLong(5, anchorId);
            sqlUpdateAnchor.executeUpdate();
	       
          } catch(ParseException exception) {
            System.err.println("Error parsing anchor ID for "+anchor.getId());
            throw new StoreException("Error parsing anchor ID for "+anchor.getId(), exception);
          }
          break;
        } case Destroy: {
            // deduce the database anchor.anchor_id from the object anchor.id
            Long anchorId = null;
            try {
              Object[] o = fmtAnchorId.parse(anchor.getId());
              anchorId = (Long)o[0];
            } catch(ParseException exception) {
              System.err.println("Error parsing anchor ID for "+anchor.getId());
              throw new StoreException("Error parsing anchor ID for "+anchor.getId(), exception);
            }
            // check all layers in the database to see in any existing annotation uses the anchor
            for (Integer layerId : layerIds) {
              sqlCheckAnchor.setInt(1, layerId);
              sqlCheckAnchor.setLong(2, anchorId);
              sqlCheckAnchor.setLong(3, anchorId);
              ResultSet rs = sqlCheckAnchor.executeQuery();
              rs.next();
              if (rs.getInt(1) > 0) {
                // this anchor still has a reference to it so we can't delete it
                anchor.rollback();
              }
              rs.close();
            } // next layer
	    
            if (anchor.getChange() == Change.Operation.Destroy) { // wasn't rolled back, so go ahead and delete
              sqlDeleteAnchor.setLong(1, anchorId);
              sqlDeleteAnchor.executeUpdate();
            }

            break;
          } // Destroy
    } // switch on change type

    anchor.put("@SqlUpdated", Boolean.TRUE); // flag the anchor as having been updated
  } // end of saveAnchorChanges()

  /**
   * Saves the changes to the given anchor, and updates related annotations if the anchor
   * ID is changed.
   * @param annotation The annotation whose changes should be saved.
   * @param extraUpdates A set to add annotations which had no changes to save, but which
   * now must be updated because the anchor's ID is changing.
   * @param sqlInsertFreeformAnnotation Prepared statement for inserting a freeform
   * annotation row.
   * @param sqlInsertMetaAnnotation Prepared statement for inserting a meta annotation row.
   * @param sqlInsertWordAnnotation Prepared statement for inserting a word annotation row.
   * @param sqlInsertSegmentAnnotation Prepared statement for inserting a segment annotation row.
   * @param sqlLastId Prepared statement for retrieving the last database ID created.
   * @param sqlUpdateTurnAnnotationId Prepared statement for updating turn_annotation_id.
   * @param sqlUpdateWordAnnotationId Prepared statement for updating word_annotation_id.
   * @param sqlUpdateSegmentAnnotationId Prepared statement for updating segment_annotation_id.
   * @param sqlUpdateFreeformAnnotation Prepared statement for updating a freeform annotation row.
   * @param sqlUpdateMetaAnnotation Prepared statement for updating a meta annotation row.
   * @param sqlSelectWordFields Prepared statement for finding word field values.
   * @param sqlSelectSegmentFields Prepared statement for finding segment field values.
   * @param sqlUpdateWordAnnotation Prepared statement for updating a word annotation row.
   * @param sqlUpdateSegmentAnnotation Prepared statement for updating a segment annotation row.
   * @param sqlDeleteAnnotation Prepared statement for deleteing an annotation row.
   * @param participantNameToNumber A lookup table for participant numbers, for turns/utterances
   * @throws SQLException If a database error occurs.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   */
  protected void saveAnnotationChanges(
    Annotation annotation, HashSet<Annotation> extraUpdates, 
    PreparedStatement sqlInsertFreeformAnnotation, PreparedStatement sqlInsertMetaAnnotation, 
    PreparedStatement sqlInsertWordAnnotation, PreparedStatement sqlInsertSegmentAnnotation,
    PreparedStatement sqlLastId, PreparedStatement sqlUpdateTurnAnnotationId,
    PreparedStatement sqlUpdateWordAnnotationId,
    PreparedStatement sqlUpdateSegmentAnnotationId, 
    PreparedStatement sqlUpdateFreeformAnnotation, PreparedStatement sqlUpdateMetaAnnotation, 
    PreparedStatement sqlSelectWordFields, PreparedStatement sqlSelectSegmentFields,
    PreparedStatement sqlUpdateWordAnnotation, PreparedStatement sqlUpdateSegmentAnnotation, 
    PreparedStatement sqlDeleteAnnotation,
    HashMap<String,String> participantNameToNumber,
    Schema schema)
    throws SQLException, PermissionException, StoreException {
    if (annotation.getId().startsWith("m_")) return; // ignore participant changes for now

    if (annotation.getConfidence() == null) {
      annotation.setConfidence(Integer.valueOf(Constants.CONFIDENCE_UNKNOWN));
    }

    Layer layer = annotation.getLayer();
    
    switch (annotation.getChange()) {
      case Create: {
        // get the layer_id and its scope, so we can deduce what kind of row to insert
        if (layer == null || !layer.containsKey("layer_id")) { // load our own info
          layer = schema.getLayer(annotation.getLayerId());
        }
        Integer layerId = (Integer)layer.get("layer_id");
        String scope = (String)layer.get("scope");
        assert scope != null : "scope != null - " + layer.getId() + " - " + layerId;

        PreparedStatement sql = sqlInsertFreeformAnnotation;
        if (scope.equalsIgnoreCase(SqlConstants.SCOPE_META)) {
          sql = sqlInsertMetaAnnotation;
        } else if (scope.equalsIgnoreCase(SqlConstants.SCOPE_WORD)) {
          sql = sqlInsertWordAnnotation;
        } else if (scope.equalsIgnoreCase(SqlConstants.SCOPE_SEGMENT)) {
          sql = sqlInsertSegmentAnnotation;
        }
        sql.setInt(1, layerId);
        // parameter 2 is ag_id, which is already set
        if ((layerId.intValue() == SqlConstants.LAYER_TURN
             || layerId.intValue() == SqlConstants.LAYER_UTTERANCE)
            && participantNameToNumber.containsKey(annotation.getLabel())) { // label should be the speaker number, not the name
          sql.setString(3, participantNameToNumber.get(annotation.getLabel()));
        } else {
          sql.setString(3, annotation.getLabel());
        }
        sql.setInt(4, annotation.getConfidence());
        try {
          Object[] o = fmtAnchorId.parse(annotation.getStartId());
          Long anchorId = (Long)o[0];
          sql.setLong(5, anchorId);
        } catch(ParseException exception) {
          System.err.println("Error parsing start anchor for "+annotation.getLayerId()
                             +":"+annotation.getId()+": " + annotation.getStartId());
          throw new StoreException("Error parsing start anchor for "
                                   +annotation.getLayerId()+":"+annotation.getId()+": "
                                   + annotation.getStartId(), exception);
        }
        try {
          Object[] o = fmtAnchorId.parse(annotation.getEndId());
          Long anchorId = (Long)o[0];
          sql.setLong(6, anchorId);
        } catch(ParseException exception) {
          System.err.println("Error parsing end anchor for "+annotation.getId()
                             +": " + annotation.getEndId());
          throw new StoreException("Error parsing end anchor for "+annotation.getId()
                                   +": " + annotation.getEndId(), exception);
        }
        if (annotation.getParentId() == null) {
          sql.setNull(7, java.sql.Types.INTEGER);
        } else {
          if (scope.equalsIgnoreCase(SqlConstants.SCOPE_FREEFORM)) { // freeform layers have the graph as the parent
            sql.setLong(7, ((Integer)annotation.getGraph().get("@ag_id")).longValue());
            // for ID generation, scope must be empty string for freeform layers
            scope = "";
          } else if (annotation.getLayer().getParentId().equals("participant")) { // turn layer
            try {
              // parent id has as a special layer format
              Object[] o = fmtMetaAnnotationId.parse(annotation.getParentId());
              sql.setLong(7, Long.parseLong(o[1].toString()));
            } catch(ParseException exception) {
              System.err.println("Error parsing parent id for "+annotation.getId()
                                 +": " + annotation.getParentId()
                                 + " on " + annotation.getLayerId());
              throw new StoreException("Error parsing participant parent id for "
                                       +annotation.getId()+": " + annotation.getParentId()
                                       + " on " + annotation.getLayerId(), exception);
            }
          } else {
            try {
              Object[] o = fmtAnnotationId.parse(annotation.getParentId());
              sql.setLong(7, ((Long)o[2]).longValue());
            } catch(ParseException exception) {
              System.err.println("Error parsing parent id for "+annotation.getId()+": "
                                 + annotation.getParentId()
                                 + " on " + annotation.getLayerId());
              throw new StoreException("Error parsing parent id for "+annotation.getId()
                                       +": " + annotation.getParentId()
                                       + " on " + annotation.getLayerId(), exception);
            }
          }
        }
        sql.setInt(8, annotation.getOrdinal());
        if (annotation.getAnnotator() != null) {
          sql.setString(9, annotation.getAnnotator());
        } else {
          sql.setString(9, getUser());
        }
        // if (annotation.getWhen() != null)
        // {
        //    sql.setTimestamp(10, new Timestamp(annotation.getWhen().getTime()));
        // }
        // else
        {
          sql.setTimestamp(10, new Timestamp(new java.util.Date().getTime()));
        }

        if (sql != sqlInsertFreeformAnnotation) { // meta, word, or segment annotation
          // must set turn_annotation_id too
          if (layerId.intValue() == SqlConstants.LAYER_TURN) {
            // turn_annotation_id = annotation_id
            sql.setNull(11, java.sql.Types.INTEGER); // turn_annotation_id - set it later...
          } else if (sql == sqlInsertMetaAnnotation) {
            // turn is annotation.parent
            try {
              Object[] o = fmtAnnotationId.parse(annotation.getParentId());
              sql.setLong(11, ((Long)o[2]).longValue()); // turn_annotation_id
            } catch(ParseException exception) {
              System.err.println("Error parsing turn id for "+annotation.getId()
                                 +" (" + annotation.getStart() + "): "
                                 + annotation.getParentId());
              throw new StoreException("Error parsing turn id for "+annotation.getId()
                                       +" (" + annotation.getStart() + "): "
                                       + annotation.getParentId(), exception);
            }
          } else { // word or segment annotation
            if (layerId.intValue() == SqlConstants.LAYER_TRANSCRIPTION) {
              try {
                Object[] o = fmtAnnotationId.parse(annotation.getParentId());
                sql.setLong(11, ((Long)o[2]).longValue()); // turn_annotation_id
              } catch(ParseException exception) {
                System.err.println("Error parsing turn id for "+annotation.getId()
                                   +" (" + annotation.getStart() + "): "
                                   + annotation.getParentId());
                throw new StoreException("Error parsing turn id for "+annotation.getId()
                                         +" (" + annotation.getStart() + "): "
                                         + annotation.getParentId(), exception);
              }
              sql.setInt(12, annotation.getOrdinal()); // ordinal_in_turn
              sql.setNull(13, java.sql.Types.INTEGER); // word_annotation_id - set it later
            } else { // other word or segment annotation
              if (sql == sqlInsertWordAnnotation) {
                try {
                  Object[] o = fmtAnnotationId.parse(annotation.getParentId());
                  Long wordAnnotationId = ((Long)o[2]).longValue();
                  sqlSelectWordFields.setLong(1, wordAnnotationId); // word_annotation_id
                  ResultSet rs = sqlSelectWordFields.executeQuery();
                  rs.next();
                  sql.setLong(11, rs.getLong("turn_annotation_id"));
                  sql.setInt(12, rs.getInt("ordinal_in_turn"));
                  rs.close();
                  sql.setLong(13, wordAnnotationId); // word_annotation_id
                } catch(ParseException exception) {
                  System.err.println("Error parsing word ID for "+annotation.getId()
                                     +" (" + annotation.getStart() + "): "
                                     + annotation.getParentId());
                  throw new StoreException("Error parsing word ID for "+annotation.getId()
                                           +" (" + annotation.getStart() + "): "
                                           + annotation.getParentId(), exception);
                }
              } else { // segment annotation
                if (layerId.intValue() == SqlConstants.LAYER_SEGMENT) {
                  try {
                    Object[] o = fmtAnnotationId.parse(annotation.getParentId());
                    Long wordAnnotationId = ((Long)o[2]).longValue();
                    sqlSelectWordFields.setLong(1, wordAnnotationId); // word_annotation_id
                    ResultSet rs = sqlSelectWordFields.executeQuery();
                    rs.next();
                    sql.setLong(11, rs.getLong("turn_annotation_id"));
                    sql.setInt(12, rs.getInt("ordinal_in_turn"));
                    rs.close();
                    sql.setLong(13, wordAnnotationId); // word_annotation_id
                    sql.setInt(14, annotation.getOrdinal()); // ordinal_in_word
                    sql.setNull(15, java.sql.Types.INTEGER); // segment_annotation_id - set it later
                  } catch(ParseException exception) {
                    System.err.println("Error parsing word ID for "+annotation.getId()
                                       +" (" + annotation.getStart() + "): "
                                       + annotation.getParentId());
                    throw new StoreException("Error parsing word ID for "
                                             +annotation.getId()
                                             +" (" + annotation.getStart() + "): "
                                             + annotation.getParentId(), exception);
                  }
                } else { // other segment annotation
                  try {
                    Object[] o = fmtAnnotationId.parse(annotation.getParent().getParentId());
                    Long wordAnnotationId = ((Long)o[2]).longValue();
                    sqlSelectWordFields.setLong(1, wordAnnotationId); // word_annotation_id
                    ResultSet rs = sqlSelectWordFields.executeQuery();
                    rs.next();
                    sql.setLong(11, rs.getLong("turn_annotation_id"));
                    sql.setInt(12, rs.getInt("ordinal_in_turn"));
                    rs.close();
                    sql.setLong(13, wordAnnotationId); // word_annotation_id
			      
                  } catch(ParseException exception) {
                    System.err.println("Error parsing word ID for segment "
                                       +annotation.getId()
                                       +" (" + annotation.getStart() + "): "
                                       + annotation.getParent().getParentId());
                    throw new StoreException("Error parsing word ID for segment "
                                             +annotation.getId()
                                             +" (" + annotation.getStart() + "): "
                                             + annotation.getParent().getParentId(),
                                             exception);
                  }
                  try {
                    Object[] o = fmtAnnotationId.parse(annotation.getParentId());
                    Long segmentAnnotationId = ((Long)o[2]).longValue();
                    sqlSelectSegmentFields.setLong(1, segmentAnnotationId); // segment_annotation_id
                    ResultSet rs = sqlSelectSegmentFields.executeQuery();
                    rs.next();
                    sql.setInt(14, rs.getInt("ordinal_in_word")); // ordinal_in_word
                    sql.setLong(15, segmentAnnotationId); // segment_annotation_id
                    rs.close();
                  } catch(ParseException exception) {
                    System.err.println("Error parsing segment ID for "
                                       +annotation.getId()
                                       +" (" + annotation.getStart() + "): "
                                       + annotation.getParentId());
                    throw new StoreException("Error parsing segment ID for "
                                             +annotation.getId()
                                             +" (" + annotation.getStart() + "): "
                                             + annotation.getParentId(), exception);
                  }
                } // other segment annotation
              } // segment annotation
            } // other word or segment annotation
          } // word or segment annotation
        } // meta, word, or segment annotation
        sql.executeUpdate();

        ResultSet rs = sqlLastId.executeQuery();
        rs.next();
        String oldId = annotation.getId();
        long annotationId = rs.getLong(1);
        Object[] annotationIdParts = { scope, layerId, Long.valueOf(annotationId) };
        String newId = fmtAnnotationId.format(annotationIdParts);
        
        rs.close();
	    
        // change annotation ID
        annotation.setId(newId);

        // does it need to update its own ID in the database?
        switch (layerId.intValue()) {
          case SqlConstants.LAYER_TURN: {		  
            sqlUpdateTurnAnnotationId.setInt(1, layerId);
            sqlUpdateTurnAnnotationId.setLong(2, annotationId);
            sqlUpdateTurnAnnotationId.setLong(3, annotationId);
            sqlUpdateTurnAnnotationId.executeUpdate();
            break;
          } case SqlConstants.LAYER_TRANSCRIPTION: {
              sqlUpdateWordAnnotationId.setInt(1, layerId);
              sqlUpdateWordAnnotationId.setLong(2, annotationId);
              sqlUpdateWordAnnotationId.setLong(3, annotationId);
              sqlUpdateWordAnnotationId.executeUpdate();
              break;
            } case SqlConstants.LAYER_SEGMENT: {
                sqlUpdateSegmentAnnotationId.setInt(1, layerId);
                sqlUpdateSegmentAnnotationId.setLong(2, annotationId);
                sqlUpdateSegmentAnnotationId.setLong(3, annotationId);
                sqlUpdateSegmentAnnotationId.executeUpdate();
                break;
              }
        }

        break;
      } case Update: {
          // deduce the database anchor.anchor_id from the object anchor.id
          String scope = null;
          Long layerId = null;
          Long annotationId = null;
          try {
            Object[] o = fmtAnnotationId.parse(annotation.getId());
            scope = o[0].toString();
            layerId = (Long)o[1];
            annotationId = (Long)o[2];
          } catch(ParseException exception) {
            System.err.println("Error parsing ID for "+annotation.getId());
            throw new StoreException("Error parsing ID for "+annotation.getId(), exception);
          }
          PreparedStatement sql = sqlUpdateFreeformAnnotation;
          if (scope.equalsIgnoreCase(SqlConstants.SCOPE_META)) {
            sql = sqlUpdateMetaAnnotation;
          } else if (scope.equalsIgnoreCase(SqlConstants.SCOPE_WORD)) {
            sql = sqlUpdateWordAnnotation;
          } else if (scope.equalsIgnoreCase(SqlConstants.SCOPE_SEGMENT)) {
            sql = sqlUpdateSegmentAnnotation;
          }
          sql.setInt(1, layerId.intValue());
          if ((layerId.intValue() == SqlConstants.LAYER_TURN
               || layerId.intValue() == SqlConstants.LAYER_UTTERANCE)
              && participantNameToNumber.containsKey(annotation.getLabel())) { // label should be the speaker number, not the name
            sql.setString(2, participantNameToNumber.get(annotation.getLabel()));
          } else {
            sql.setString(2, annotation.getLabel());
          }
          sql.setInt(3, annotation.getConfidence());
          try {
            Object[] o = fmtAnchorId.parse(annotation.getStartId());
            Long anchorId = (Long)o[0];
            sql.setLong(4, anchorId);
          } catch(ParseException exception) {
            System.err.println("Error parsing start anchor for "+annotation.getId()+": "
                               + annotation.getStartId());
            throw new StoreException("Error parsing start anchor for "+annotation.getId()
                                     +": " + annotation.getStartId(), exception);
          }
          try {
            Object[] o = fmtAnchorId.parse(annotation.getEndId());
            Long anchorId = (Long)o[0];
            sql.setLong(5, anchorId);
          } catch(ParseException exception) {
            System.err.println("Error parsing end anchor for "+annotation.getId()
                               +": " + annotation.getEndId());
            throw new StoreException("Error parsing end anchor for "+annotation.getId()
                                     +": " + annotation.getEndId(), exception);
          }
          if (sql == sqlUpdateFreeformAnnotation) {
            // for freeform layers, the parent is the graph
            sql.setLong(6, ((Integer)annotation.getGraph().get("@ag_id")).longValue());

            sql.setInt(7, annotation.getOrdinal());
            if (annotation.getAnnotator() != null) {
              sql.setString(8, annotation.getAnnotator());
            } else {
              sql.setString(8, getUser());
            }
            // if (annotation.getWhen() != null)
            // {
            // 	  sql.setTimestamp(9, new Timestamp(annotation.getWhen().getTime()));
            // }
            // else
            {
              sql.setTimestamp(9, new Timestamp(new java.util.Date().getTime()));
            }
            sql.setLong(10, annotationId);
          } else { // meta, word, or segment annotation
            // must set turn_annotation_id too
            if (layerId.intValue() == SqlConstants.LAYER_TURN) {
              // turn_annotation_id = annotation_id
              sql.setLong(6, annotationId); // turn_annotation_id
              // parent_id for turns is paticipant number
              if (annotation.getParentId() == null) {
                sql.setLong(7, Long.parseLong(annotation.getLabel()));
              } else {
                try {
                  Object[] o = fmtMetaAnnotationId.parse(annotation.getParentId());
                  sql.setLong(7, Long.parseLong(o[1].toString()));
                } catch(ParseException exception) {
                  System.err.println("Error parsing parent id for "+annotation.getId()
                                     +": " + annotation.getParentId());
                  throw new StoreException("Error parsing parent id for "+annotation.getId()
                                           +": " + annotation.getParentId(), exception);
                }
              }
              sql.setInt(8, annotation.getOrdinal());
              if (annotation.getAnnotator() != null) {
                sql.setString(9, annotation.getAnnotator());
              } else {
                sql.setString(9, getUser());
              }
              // if (annotation.getWhen() != null)
              // {
              //    sql.setTimestamp(10, new Timestamp(annotation.getWhen().getTime()));
              // }
              // else
              {
                sql.setTimestamp(10, new Timestamp(new java.util.Date().getTime()));
              }
              // annotation_id
              sql.setLong(11, annotationId);
            } else if (sql == sqlUpdateMetaAnnotation) {
              // turn is annotation.parent
              try {
                Object[] o = fmtAnnotationId.parse(annotation.getParentId());
                sql.setLong(6, ((Long)o[2]).longValue()); // turn_annotation_id
              } catch(ParseException exception) {
                System.err.println("Error parsing turn ID for "+annotation.getId()
                                   +": " + annotation.getParentId());
                throw new StoreException("Error parsing turn ID for "+annotation.getId()
                                         +": " + annotation.getParentId(), exception);
              }
              if (annotation.getParentId() == null) {
                sql.setNull(7, java.sql.Types.INTEGER);
              } else {
                try {
                  Object[] o = fmtAnnotationId.parse(annotation.getParentId());
                  sql.setLong(7, ((Long)o[2]).longValue());
                } catch(ParseException exception) {
                  System.err.println("Error parsing parent id for "+annotation.getId()
                                     +": " + annotation.getParentId());
                  throw new StoreException("Error parsing parent id for "+annotation.getId()
                                           +": " + annotation.getParentId(), exception);
                }
              }
              sql.setInt(8, annotation.getOrdinal());
              if (annotation.getAnnotator() != null) {
                sql.setString(9, annotation.getAnnotator());
              } else {
                sql.setString(9, getUser());
              }
              // if (annotation.getWhen() != null)
              // {
              //    sql.setTimestamp(10, new Timestamp(annotation.getWhen().getTime()));
              // }
              // else
              {
                sql.setTimestamp(10, new Timestamp(new java.util.Date().getTime()));
              }
              // annotation_id
              sql.setLong(11, annotationId);
            } else { // word or segment annotation
              if (layerId.intValue() == SqlConstants.LAYER_TRANSCRIPTION) {
                try {
                  Object[] o = fmtAnnotationId.parse(annotation.getParentId());
                  sql.setLong(6, ((Long)o[2]).longValue()); // turn_annotation_id
                } catch(ParseException exception) {
                  System.err.println("Error parsing turn ID for "+annotation.getId()
                                     +": " + annotation.getParentId());
                  throw new StoreException("Error parsing turn ID for "+annotation.getId()
                                           +": " + annotation.getParentId(), exception);
                }
                sql.setInt(7, annotation.getOrdinal()); // ordinal_in_turn
                sql.setLong(8, annotationId); // word_annotation_id		     
                if (annotation.getParentId() == null) {
                  sql.setNull(9, java.sql.Types.INTEGER);
                } else {
                  try {
                    Object[] o = fmtAnnotationId.parse(annotation.getParentId());
                    sql.setLong(9, ((Long)o[2]).longValue());
                  } catch(ParseException exception) {
                    System.err.println("Error parsing parent id for "+annotation.getId()
                                       +": " + annotation.getParentId());
                    throw new StoreException("Error parsing parent id for "
                                             +annotation.getId()
                                             +": " + annotation.getParentId(), exception);
                  }
                }
                sql.setInt(10, annotation.getOrdinal());
                if (annotation.getAnnotator() != null) {
                  sql.setString(11, annotation.getAnnotator());
                } else {
                  sql.setString(11, getUser());
                }
                // if (annotation.getWhen() != null)
                // {
                // 	sql.setTimestamp(12, new Timestamp(annotation.getWhen().getTime()));
                // }
                // else
                {
                  sql.setTimestamp(12, new Timestamp(new java.util.Date().getTime()));
                }
                sql.setLong(13, annotationId); // annotation_id		     
              } else { // other word or segment annotation
                if (sql == sqlUpdateWordAnnotation) {
                  try {
                    Object[] o = fmtAnnotationId.parse(annotation.getParentId());
                    Long wordAnnotationId = ((Long)o[2]).longValue();
                    sqlSelectWordFields.setLong(1, wordAnnotationId); // word_annotation_id
                    ResultSet rs = sqlSelectWordFields.executeQuery();
                    rs.next();
                    sql.setLong(6, rs.getLong("turn_annotation_id"));
                    sql.setInt(7, rs.getInt("ordinal_in_turn"));
                    sql.setLong(8, wordAnnotationId); // word_annotation_id
                    if (annotation.getParentId() == null) {
                      sql.setNull(9, java.sql.Types.INTEGER);
                    } else {
                      try {
                        o = fmtAnnotationId.parse(annotation.getParentId());
                        sql.setLong(9, ((Long)o[2]).longValue());
                      } catch(ParseException exception) {
                        System.err.println("Error parsing parent id for "
                                           +annotation.getId()
                                           +": " + annotation.getParentId());
                        throw new StoreException("Error parsing parent id for "
                                                 +annotation.getId()
                                                 +": " + annotation.getParentId(),
                                                 exception);
                      }
                    }
                    sql.setInt(10, annotation.getOrdinal());
                    if (annotation.getAnnotator() != null) {
                      sql.setString(11, annotation.getAnnotator());
                    } else {
                      sql.setString(11, getUser());
                    }
                    // if (annotation.getWhen() != null)
                    // {
                    //    sql.setTimestamp(12, new Timestamp(annotation.getWhen().getTime()));
                    // }
                    // else
                    {
                      sql.setTimestamp(12, new Timestamp(new java.util.Date().getTime()));
                    }
                    sql.setLong(13, annotationId); // annotation_id
                    rs.close();
                  } catch(ParseException exception) {
                    System.err.println("Error parsing word ID for "+annotation.getId()
                                       +": " + annotation.getParentId());
                    throw new StoreException("Error parsing word ID for "
                                             +annotation.getId()
                                             +": " + annotation.getParentId(), exception);
                  }
                } else { // segment annotation
                  if (layerId.intValue() == SqlConstants.LAYER_SEGMENT) {
                    try {
                      Object[] o = fmtAnnotationId.parse(annotation.getParentId());
                      Long wordAnnotationId = ((Long)o[2]).longValue();
                      sqlSelectWordFields.setLong(1, wordAnnotationId); // word_annotation_id
                      ResultSet rs = sqlSelectWordFields.executeQuery();
                      rs.next();
                      sql.setLong(6, rs.getLong("turn_annotation_id"));
                      sql.setInt(7, rs.getInt("ordinal_in_turn"));
                      rs.close();
                      sql.setLong(8, wordAnnotationId); // word_annotation_id
                      sql.setInt(9, annotation.getOrdinal()); // ordinal_in_word
                      sql.setLong(10, annotationId); // segment_annotation_id
                      if (annotation.getParentId() == null) {
                        sql.setNull(11, java.sql.Types.INTEGER);
                      } else {
                        try {
                          o = fmtAnnotationId.parse(annotation.getParentId());
                          sql.setLong(11, ((Long)o[2]).longValue());
                        } catch(ParseException exception) {
                          System.err.println("Error parsing parent id for "
                                             +annotation.getId()
                                             +": " + annotation.getParentId());
                          throw new StoreException("Error parsing parent id for "
                                                   +annotation.getId()
                                                   +": " + annotation.getParentId(),
                                                   exception);
                        }
                      }
                      sql.setInt(12, annotation.getOrdinal());
                      if (annotation.getAnnotator() != null) {
                        sql.setString(13, annotation.getAnnotator());
                      } else {
                        sql.setString(13, getUser());
                      }
                      // if (annotation.getWhen() != null)
                      // {
                      // 	 sql.setTimestamp(14, new Timestamp(annotation.getWhen().getTime()));
                      // }
                      // else
                      {
                        sql.setTimestamp(14, new Timestamp(new java.util.Date().getTime()));
                      }
                      sql.setLong(15, annotationId); // annotation_id
                    } catch(ParseException exception) {
                      System.err.println("Error parsing word ID for "
                                         +annotation.getId()
                                         +": " + annotation.getParentId());
                      throw new StoreException(
                        "Error parsing word ID for "
                        +annotation.getId() +": " + annotation.getParentId(), exception);
                    }
                  } else { // other segment annotation
                    try
                    {
                      Object[] o = fmtAnnotationId.parse(
                        annotation.getParent().getParentId());
                      Long wordAnnotationId = ((Long)o[2]).longValue();
                      sqlSelectWordFields.setLong(1, wordAnnotationId); // word_annotation_id
                      ResultSet rs = sqlSelectWordFields.executeQuery();
                      rs.next();
                      sql.setLong(6, rs.getLong("turn_annotation_id"));
                      sql.setInt(7, rs.getInt("ordinal_in_turn"));
                      rs.close();
                      sql.setLong(8, wordAnnotationId); // word_annotation_id
                    } catch(ParseException exception) {
                      System.err.println("Error parsing word ID for parent "
                                         +annotation.getId()
                                         +": " + annotation.getParent().getParentId());
                      throw new StoreException(
                        "Error parsing word ID for parent "
                        +annotation.getId() +": " + annotation.getParent().getParentId(),
                        exception);
                    }
                    try {
                      Object[] o = fmtAnnotationId.parse(annotation.getParentId());
                      Long segmentAnnotationId = ((Long)o[2]).longValue();
                      sqlSelectSegmentFields.setLong(1, segmentAnnotationId); // segment_annotation_id
                      ResultSet rs = sqlSelectSegmentFields.executeQuery();
                      rs.next();
                      sql.setInt(9, rs.getInt("ordinal_in_word")); // ordinal_in_word
                      sql.setLong(10, segmentAnnotationId); // segment_annotation_id
                      rs.close();
                    } catch(ParseException exception) {
                      System.err.println("Error parsing segment ID for "
                                         +annotation.getId()
                                         +": " + annotation.getParentId());
                      throw new StoreException("Error parsing segment ID for "
                                               +annotation.getId()+": "
                                               + annotation.getParentId(), exception);
                    }
                    if (annotation.getParentId() == null) {
                      sql.setNull(11, java.sql.Types.INTEGER);
                    } else {
                      try
                      {
                        Object[] o = fmtAnnotationId.parse(annotation.getParentId());
                        sql.setLong(11, ((Long)o[2]).longValue());
                      }
                      catch(ParseException exception)
                      {
                        System.err.println("Error parsing parent id for "
                                           +annotation.getId()
                                           +": " + annotation.getParentId());
                        throw new StoreException("Error parsing parent id for "
                                                 +annotation.getId()+": "
                                                 + annotation.getParentId(), exception);
                      }
                    }
                    sql.setInt(12, annotation.getOrdinal());
                    if (annotation.getAnnotator() != null) {
                      sql.setString(13, annotation.getAnnotator());
                    } else {
                      sql.setString(13, getUser());
                    }
                    // if (annotation.getWhen() != null)
                    // {
                    //    sql.setTimestamp(14, new Timestamp(annotation.getWhen().getTime()));
                    // }
                    // else
                    {
                      sql.setTimestamp(14, new Timestamp(new java.util.Date().getTime()));
                    }
                    sql.setLong(15, annotationId); // annotation_id
                  } // other segment annotation
                } // segment annotation
              } // other word or segment annotation
            } // word or segment annotation
          } // meta, word, or segment annotation
          sql.executeUpdate();
	    
          break;
        } case Destroy: {
            try {
              Object[] o = fmtAnnotationId.parse(annotation.getId());
              Long layerId = (Long)o[1];
              Long annotationId = (Long)o[2];
              sqlDeleteAnnotation.setInt(1, layerId.intValue());
              sqlDeleteAnnotation.setLong(2, annotationId);
              sqlDeleteAnnotation.executeUpdate();
            } catch(ParseException exception) {
              // ignore the error - if it hasn't got a valid ID, it's not in the DB anyway
            }
            break;
          } // Destroy
    } // switch on change type

    if (layer.getType().indexOf('/') > 0) { // a MIME type, so it's a binary layer
      // there may be a file associated with the annotation
      try {
        // determine where the annotation's file would be
        File annotationFile = annotationDataFile(
          annotation, annotation.getGraph(), annotation.getLayer().getType());
        if (annotation.getChange() == Change.Operation.Destroy) { // deleting the annotation
          // delete the file too
          annotationFile.delete();
        } else { // saving annotation
          // have we been given a data URL?
          String dataUrl = (String)annotation.get("dataUrl");
          if (dataUrl != null && dataUrl.startsWith("file:")) {
            File dataFile = new File(new URI(dataUrl));
            if (dataFile.exists() // there is no file or it's not the one we already have
                || !dataFile.getAbsoluteFile().equals(annotationFile.getAbsoluteFile())) {
              // ensure the directory structure exists
              Files.createDirectories(annotationFile.getParentFile().toPath());
              // move the incoming file to the proper location
              IO.Rename(dataFile, annotationFile);
            } // it's a different file
          } // dataUrl is set
        } // saving annotation
      } catch (Exception x) {
        System.err.println("Error processing binary annotation "+annotation.getLayerId()
                           +":"+annotation.getId()+": " + x.getMessage());
        throw new StoreException(
          "Error processing binary annotation "+annotation.getLayerId()
          +":"+annotation.getId()+": " + x.getMessage(), x);
      }
    }

    annotation.put("@SqlUpdated", Boolean.TRUE); // flag the annotation as having been updated
  } // end of saveAnnotationChanges()
  
  /**
   * Get the location of the annotation's data file, if any.
   * <p> Layers that have a MIME type as their type (e.g. "image/png") allow each
   * annotation to represent binary data, which is implemented by storing it in the file
   * system as a file. This method returns the location of that file, if it were to be
   * present.
   * <p> The location is structured: 
   * <tt>files/{corpus}/{episode}/{ext}/{transcript}/{layer}/{annotation.id}.{ext}</tt>
   * @param annotation The annotation to determine the data file location of.
   * @param graph Annotation graph with at least corpus and episode layers loaded.
   * @param mimeType The layer's MIME type.
   * @return The location of the file containing the annotation's data, which may or may
   * not exist.
   * @throws StoreException, PermissionException, GraphNotFoundException
   */
  public File annotationDataFile(Annotation annotation, Graph graph, String mimeType)
    throws StoreException, PermissionException, GraphNotFoundException {
    if (mimeType.indexOf('/') < 0) return null; // not a MIME type
    String ext = MediaFile.MimeTypeToSuffix().get(mimeType);
    if (ext == null) { // not a known MIME type
      // just use whatever's after the slash
      ext = mimeType.substring(mimeType.indexOf('/')+1);
    }
    Annotation corpus = graph.first("corpus");
    Annotation episode = graph.first("episode");
    if (corpus == null || episode == null) {
      String[] layers = { "corpus", "episode" };
      graph = getTranscript(graph.getId(), layers);
      corpus = graph.first("corpus");
      episode = graph.first("episode");
    }
    File corpusDir = new File(getFiles(), corpus.getLabel());
    File episodeDir = new File(corpusDir, episode.getLabel());
    File extDir = new File(episodeDir, ext);
    File transcriptDir = new File(extDir, graph.getId());
    File layerDir = new File(transcriptDir, annotation.getLayerId());
    File annotationFile = new File(layerDir, annotation.getId()+"."+ext);
    return annotationFile;
  } // end of annotationDataFile()
  
  /**
   * Returns a URL for the given annotation. 
   * @param annotation
   * @param graph Annotation graph with at least corpus and episode layers loaded.
   * @param mimeType The layer's MIME type.
   * @return A URL to the file, which will be an http:// URL if {@link #baseUrl} is set,
   * and a file:// URL otherwise, but will be null if the annotation has no data file, or
   * the user doesn't have access to the corresponding media.
   */
  public String urlForAnnotationFile(Annotation annotation, Graph graph, String mimeType)
    throws StoreException, PermissionException, GraphNotFoundException {
    File annotationFile = annotationDataFile(annotation, graph, mimeType);
    if (annotationFile != null && annotationFile.exists()) {
      MediaFile mediaFile = new MediaFile(annotationFile, annotation.getLayerId());
      try {
        if (hasAccess(annotation.getGraph().getId(), mediaFile.getType().substring(0,1))) {
          if (getBaseUrl() == null) { // TODO check this isn't a security risk
            return annotationFile.toURI().toString();
          } else {
            return getBaseUrl()+"/api/annotation/data?id="+annotation.getId();
          }
        } // user has access
      } catch (SQLException x) {
        throw new StoreException(x);
      }
    }
    return null;
  } // end of urlForAnnotationFile()
   
  /**
   * Save changes to a 'special' annotation, e.g. corpus, series, etc.
   * @param annotation The annotation whose changes should be saved.
   * @param participantNameToNumber A map of participant names to speaker numbers.
   * @throws PermissionException If there's a permission problem.
   * @throws SQLException If a database error occurs.
   * @throws StoreException If some other error occurs.
   */
  protected void saveSpecialAnnotationChanges(
    Annotation annotation, HashMap<String,String> participantNameToNumber, Schema schema)
    throws PermissionException, StoreException, SQLException {
    try {
      if (annotation.getLayerId().equals("episode")) {
        switch (annotation.getChange()) {
          case Create: {
            int familyId = -1;
            int familySequence = 1;
            double familyOffset = 0.0;
            PreparedStatement sql = getConnection().prepareStatement(
              "SELECT transcript_family.family_id,"
              + " COALESCE(MAX(family_sequence) + 1, 1) AS family_sequence,"
              + " COALESCE(MAX(transcript.family_offset + anchor.offset), 0.0) AS family_offset"
              + " FROM transcript_family"
              + " LEFT OUTER JOIN transcript ON transcript.family_id = transcript_family.family_id"
              + " LEFT OUTER JOIN anchor ON anchor.ag_id = transcript.ag_id"
              + " WHERE name = ?"
              + " GROUP BY transcript_family.family_id");
            sql.setString(1, annotation.getLabel());
            ResultSet rs = sql.executeQuery();
            if (!rs.next()) { // doesn't exist, so create it		     
              rs.close();
              sql.close();
              sql = getConnection().prepareStatement(
                "SELECT MAX(family_id) + 1 AS family_id FROM transcript_family");
              rs = sql.executeQuery();
              rs.next();
              familyId = rs.getInt("family_id");
              rs.close();
              sql.close();
              sql = getConnection().prepareStatement(
                "INSERT INTO transcript_family (family_id, name) VALUES (?, ?)");
              sql.setString(2, annotation.getLabel());
              while (true) { // until we succeed in adding the family
                try {
                  sql.setInt(1, familyId);
                  sql.executeUpdate();
                  break;
                } catch(SQLException exception) {
                  // somebody else got the ID first, so try the next one
                  familyId++;
                }
              }
            } else {
              familyId = rs.getInt("family_id");
              familySequence = rs.getInt("family_sequence");
              familyOffset = rs.getDouble("family_offset");
            }
            try { rs.close(); } catch(Exception exception) {}
            sql.close();
            int agId = ((Integer)annotation.getGraph().get("@ag_id")).intValue();
            PreparedStatement sqlUpdate = getConnection().prepareStatement(
              "UPDATE transcript SET family_id = ?, family_sequence = ?, family_offset = ?"
              + " WHERE ag_id = ?");
            sqlUpdate.setInt(1, familyId);
            sqlUpdate.setInt(2, familySequence);
            sqlUpdate.setDouble(3, familyOffset);
            sqlUpdate.setInt(4, agId);
            sqlUpdate.executeUpdate();
            sqlUpdate.close();

            // update ID
            Object[] annotationIdParts = {
              schema.getLayer(annotation.getLayerId()).get("layer_id"), ""+familyId};
            annotation.setId(fmtMetaAnnotationId.format(annotationIdParts));

            // also update graph
            annotation.getGraph().put("@family_id", familyId);		  
            break;
          }
        } // switch on change type
      } else if (annotation.getLayerId().equals("corpus")) {
        switch (annotation.getChange()) {
          case Create:
          case Update: {
            int agId = ((Integer)annotation.getGraph().get("@ag_id")).intValue();
            PreparedStatement sqlUpdate = getConnection().prepareStatement(
              "UPDATE transcript SET corpus_name = ? WHERE ag_id = ?");
            sqlUpdate.setString(1, annotation.getLabel());
            sqlUpdate.setInt(2, agId);
            sqlUpdate.executeUpdate();
            sqlUpdate.close();
            break;
          }
        } // switch on change type
      } else if (annotation.getLayerId().equals("transcript_type")) {
        switch (annotation.getChange()) {
          case Create:
          case Update: {
            PreparedStatement sql = getConnection().prepareStatement(
              "SELECT type_id FROM transcript_type WHERE transcript_type = ?");
            sql.setString(1, annotation.getLabel());
            ResultSet rs = sql.executeQuery();
            if (rs.next()) {
              int typeId = rs.getInt("type_id");
              int agId = ((Integer)annotation.getGraph().get("@ag_id")).intValue();
              PreparedStatement sqlUpdate = getConnection().prepareStatement(
                "UPDATE transcript SET type_id = ? WHERE ag_id = ?");
              sqlUpdate.setInt(1, typeId);
              sqlUpdate.setInt(2, agId);
              sqlUpdate.executeUpdate();
              sqlUpdate.close();
            }
            rs.close();
            sql.close();
            break;
          }
        } // switch on change type
      } else if (annotation.getLayerId().equals("main_participant")) {
        int agId = ((Integer)annotation.getGraph().get("@ag_id")).intValue();
        Object[] o = fmtMetaAnnotationId.parse(annotation.getParentId());
        int speakerNumber = Integer.parseInt(o[1].toString().replace(",",""));
        PreparedStatement sqlUpdate = getConnection().prepareStatement(
          "UPDATE transcript_speaker SET main_speaker = ?"
          +" WHERE ag_id = ? AND speaker_number = ?");
        sqlUpdate.setInt(2, agId);
        sqlUpdate.setInt(3, speakerNumber);
        switch (annotation.getChange()) {
          case Create:
          case Update:
            sqlUpdate.setInt(1, 1); // is a main speaker
            break;
          case Destroy:
            sqlUpdate.setInt(1, 0); // isn't a main speaker
            break;
        } // switch on change type
        sqlUpdate.executeUpdate();
        sqlUpdate.close();
      } else if (annotation.getLayerId().equals("participant")) {
        int agId = ((Integer)annotation.getGraph().get("@ag_id")).intValue();
        switch (annotation.getChange()) {
          case Create: {
            // ensure speaker exists
            int speakerNumber = -1;
            PreparedStatement sql = getConnection().prepareStatement(
              "SELECT speaker_number FROM speaker WHERE name = ?");
            sql.setString(1, annotation.getLabel());
            ResultSet rs = sql.executeQuery();
            if (rs.next()) {
              speakerNumber = rs.getInt("speaker_number");
            } else {
              // create the speaker
              PreparedStatement sqlInsert = getConnection().prepareStatement(
                "INSERT INTO speaker (name) VALUES (?)");
              sqlInsert.setString(1, annotation.getLabel());
              try {
                sqlInsert.executeUpdate();
                sqlInsert.close();
                sqlInsert = getConnection().prepareStatement("SELECT LAST_INSERT_ID()");
                ResultSet rsInsert = sqlInsert.executeQuery();
                rsInsert.next();
                speakerNumber = rsInsert.getInt(1);
                rsInsert.close();
              } catch(SQLException exception) {
                // maybe some simultaneous upload already inserted this speaker...
                rs.close();
                rs = sql.executeQuery();
                if (rs.next()) { // yes, the speaker now exists, so get their identifier
                  speakerNumber = rs.getInt("speaker_number");
                } else {
                  // no speaker still doesn't exist, so something else went wrong...
                  rs.close();
                  sql.close();
                  throw exception;
                }
              }
              sqlInsert.close();
            }
            rs.close();
            sql.close();
            annotation.put("@speaker_number", Integer.valueOf(speakerNumber));
            participantNameToNumber.put(annotation.getLabel(), ""+speakerNumber);
		  
            // reset the annotation ID
            Object[] args = {
              schema.getLayer(annotation.getLayerId()).get("layer_id"), ""+speakerNumber };
            annotation.setId(fmtMetaAnnotationId.format(args));

            // add the speaker to transcript_speaker
            sql = getConnection().prepareStatement(
              "REPLACE INTO transcript_speaker (speaker_number, ag_id, name) VALUES (?,?,?)");
            sql.setInt(1, speakerNumber);
            sql.setInt(2, agId);
            sql.setString(3, annotation.getLabel());
            sql.executeUpdate();
            sql.close();
            break;
          } case Update: {
              Object[] o = fmtMetaAnnotationId.parse(annotation.getId());
              int speakerNumber = Integer.parseInt(o[1].toString().replace(",",""));
              // update the label (the only possible change)
              PreparedStatement sql = getConnection().prepareStatement(
                "UPDATE speaker SET name = ? WHERE speaker_number = ?");
              sql.setString(1, annotation.getLabel());
              sql.setInt(2, speakerNumber);
              sql.executeUpdate();
              sql.close();
              break;
            } case Destroy: {
                Object[] o = fmtMetaAnnotationId.parse(annotation.getId());
                int speakerNumber = Integer.parseInt(o[1].toString().replace(",",""));
                // delete from transcript_speaker only
                PreparedStatement sql = getConnection().prepareStatement(
                  "DELETE FROM transcript_speaker WHERE speaker_number = ? AND ag_id = ?");
                sql.setInt(1, speakerNumber);
                sql.setInt(2, agId);
                sql.executeUpdate();
                sql.close();
                break;
              } // Destroy
        } // switch on change type
      } else if (annotation.getLayerId().equals("audio_prompt")) {
        switch (annotation.getChange()) {
          case Create:
          case Update: {
            int agId = ((Integer)annotation.getGraph().get("@ag_id")).intValue();
            PreparedStatement sqlUpdate = getConnection().prepareStatement(
              "UPDATE transcript SET audio_prompt = ? WHERE ag_id = ?");
            sqlUpdate.setString(1, annotation.getLabel());
            sqlUpdate.setInt(2, agId);
            sqlUpdate.executeUpdate();
            sqlUpdate.close();
            break;
          }
        } // switch on change type
      }
      annotation.put("@SqlUpdated", Boolean.TRUE); // flag the annotation as having been updated
    } catch(ParseException exception) {
      System.err.println(
        "Error parsing ID for special attribute: "+annotation.getId()
        + " on layer " + annotation.getLayerId());
      throw new StoreException(
        "Error parsing ID for special attribute: "+annotation.getId()
        + " on layer " + annotation.getLayerId(), exception);
    }
  } // end of saveSpecialAnnotationChanges()

  /**
   * Save changes to an episode tag annotation.
   * @param annotation The annotation whose changes should be saved.
   * @param sqlLastId Prepared statement for retrieving the last database ID created.
   * @throws PermissionException If there's a permission problem.
   * @throws SQLException If a database error occurs.
   * @throws StoreException If some other error occurs.
   */
  protected void saveEpisodeAnnotationChanges(Annotation annotation, PreparedStatement sqlLastId, Schema schema)
    throws PermissionException, StoreException, SQLException
  {
    try {
      if (annotation.getConfidence() == null) {
        annotation.setConfidence(Integer.valueOf(Constants.CONFIDENCE_UNKNOWN));
      }
      switch (annotation.getChange()) {
        case Create: {
          Layer layer = schema.getLayer(annotation.getLayerId());
          Integer layerId = (Integer)layer.get("layer_id");
          PreparedStatement sql = getConnection().prepareStatement(
            "INSERT INTO `annotation_layer_"+layerId+"`"
            +" (family_id, label, label_status, parent_id, ordinal, annotated_by, annotated_when, data)"
            + " VALUES (?,?,?,?,?,?,?,?)");
          try {
            int familyId = (Integer)annotation.getGraph().get("@family_id");
            // delete any other annotations on this layer
            PreparedStatement sqlDelete = getConnection().prepareStatement(
              "DELETE FROM `annotation_layer_"+layerId+"` WHERE family_id = ?"
              // if peers are allowed, only delete peers that have the same label
              // (so as not to double-up)
              +(layer.getPeers()?" AND label = ?":""));
            try {
              sqlDelete.setInt(1, familyId);
              if (layer.getPeers()) sqlDelete.setString(2, annotation.getLabel());
              sqlDelete.executeUpdate();
            } finally {
              sqlDelete.close();
            }

            sql.setInt(1, familyId);
            sql.setString(2, annotation.getLabel());
            sql.setInt(3, annotation.getConfidence());
            if (annotation.getLayer().getParentId().equals("episode")) {
              // parent is episode, so parent_id is family_id
              sql.setInt(4, familyId);
            } else {
              Object[] o = fmtAnnotationId.parse(annotation.getParentId());
              sql.setLong(4, (Long)o[2]);
            }
            sql.setInt(5, annotation.getOrdinal());
            if (annotation.getAnnotator() != null) {
              sql.setString(6, annotation.getAnnotator());
            } else {
              sql.setString(6, getUser());
            }
            // if (annotation.getWhen() != null)
            // {
            //    sql.setTimestamp(7, new Timestamp(annotation.getWhen().getTime()));
            // }
            // else
            {
              sql.setTimestamp(7, new Timestamp(new java.util.Date().getTime()));
            }
            if (annotation.containsKey("data")) {
              sql.setBytes(8, (byte[])annotation.get("data"));
            } else {
              sql.setNull(8, java.sql.Types.BLOB);
            }
            sql.executeUpdate();

            ResultSet rs = sqlLastId.executeQuery();
            rs.next();
            String oldId = annotation.getId();
            long annotationId = rs.getLong(1);
            Object[] annotationIdParts = { "e", layerId, Long.valueOf(annotationId) };
            String newId = fmtAnnotationId.format(annotationIdParts);
            rs.close();
		  
            // change annotation ID
            annotation.setId(newId);
          } finally {
            sql.close();
          }
          break;
        } case Update: {
            Object[] o = fmtAnnotationId.parse(annotation.getId());
            Long layerId = (Long)o[1];
            Long annotationId = (Long)o[2];
            PreparedStatement sql = getConnection().prepareStatement(
              "UPDATE `annotation_layer_"+layerId+"`"
              + " SET label = ?, label_status = ?, "
              + " ordinal = ?, annotated_by = ?, annotated_when = ?,"
              + " data = ?"
              + " WHERE annotation_id = ?");
            try {
              sql.setString(1, annotation.getLabel());
              sql.setInt(2, annotation.getConfidence());
              sql.setInt(3, annotation.getOrdinal());
              if (annotation.getAnnotator() != null) {
                sql.setString(4, annotation.getAnnotator());
              } else {
                sql.setString(4, getUser());
              }
              // if (annotation.getWhen() != null)
              // {
              //    sql.setTimestamp(5, new Timestamp(annotation.getWhen().getTime()));
              // }
              // else
              {
                sql.setTimestamp(5, new Timestamp(new java.util.Date().getTime()));
              }
              if (annotation.containsKey("data")) {
                sql.setBytes(6, (byte[])annotation.get("data"));
              } else {
                sql.setNull(6, java.sql.Types.BLOB);
              }
              sql.setLong(7, annotationId);
              sql.executeUpdate();
            } finally {
              sql.close();
            }
            break;
          } case Destroy: {
              try {
                Object[] o = fmtAnnotationId.parse(annotation.getId());
                Long layerId = (Long)o[1];
                Long annotationId = (Long)o[2];
                PreparedStatement sql = getConnection().prepareStatement(
                  "DELETE FROM `annotation_layer_"+layerId+"` WHERE annotation_id = ?");
                try {
                  sql.setLong(1, annotationId);
                  sql.executeUpdate();
                } finally {
                  sql.close();
                }
              } catch(ParseException exception) {
                System.err.println("Error parsing ID for episode tag "+annotation.getId());
                throw new StoreException("Error parsing ID for episode tag "+annotation.getId(),
                                         exception);
              }
              break;
            } // switch on change type
      }
      annotation.put("@SqlUpdated", Boolean.TRUE); // flag the annotation as having been updated
    } catch(ParseException exception) {
      System.err.println("Error parsing ID for episode attribute: "+annotation.getId()
                         + " on layer " + annotation.getLayerId() + " : " + exception);
      throw new StoreException("Error parsing ID for episode attribute: "+annotation.getId()
                               + " on layer " + annotation.getLayerId(), exception);
    }
  } // end of saveEpisodeAnnotationChanges()
   
  /**
   * Saves changes to a transcript attribute annotation.
   * @param annotation The annotation whose changes should be saved.
   * @param sqlInsertTranscriptAttribute Prepared statement for inserting an attribute value.
   * @param sqlUpdateTranscriptAttribute Prepared statement for updating an attribute value.
   * @param sqlDeleteTranscriptAttribute Prepared statement for deleting an attribute value.
   * @throws StoreException If an ID cannot be parsed.
   * @throws SQLException If a database error occurs.
   */
  protected void saveTranscriptAttributeChanges(
    Annotation annotation, PreparedStatement sqlInsertTranscriptAttribute,
    PreparedStatement sqlUpdateTranscriptAttribute,
    PreparedStatement sqlDeleteTranscriptAttribute)
    throws SQLException, StoreException {
    try {
      switch (annotation.getChange()) {
        case Create: {
          String attribute = annotation.getLayerId().substring("transcript_".length());
          sqlInsertTranscriptAttribute.setString(2, attribute);
          sqlInsertTranscriptAttribute.setString(3, annotation.getLabel());
          if (annotation.getAnnotator() != null) {
            sqlInsertTranscriptAttribute.setString(4, annotation.getAnnotator());
          } else {
            sqlInsertTranscriptAttribute.setString(4, getUser());
          }
          // if (annotation.getWhen() != null)
          // {
          // 	  sqlInsertTranscriptAttribute.setTimestamp(5, new Timestamp(annotation.getWhen().getTime()));
          // }
          // else
          {
            sqlInsertTranscriptAttribute.setTimestamp(5, new Timestamp(new java.util.Date().getTime()));
          }
          sqlInsertTranscriptAttribute.executeUpdate();
          break;
        } case Update: {
            Object[] o = fmtTranscriptAttributeId.parse(annotation.getId());
            String attribute = o[0].toString();
            sqlUpdateTranscriptAttribute.setString(1, annotation.getLabel());
            if (annotation.getAnnotator() != null) {
              sqlUpdateTranscriptAttribute.setString(2, annotation.getAnnotator());
            } else {
              sqlUpdateTranscriptAttribute.setString(2, getUser());
            }
            // if (annotation.getWhen() != null)
            // {
            // 	  sqlUpdateTranscriptAttribute.setTimestamp(3, new Timestamp(annotation.getWhen().getTime()));
            // }
            // else
            {
              sqlUpdateTranscriptAttribute.setTimestamp(3, new Timestamp(new java.util.Date().getTime()));
            }
            sqlUpdateTranscriptAttribute.setString(5, attribute);
            sqlUpdateTranscriptAttribute.setInt(6, ((Long)o[1]).intValue());
            sqlUpdateTranscriptAttribute.executeUpdate();
            break;
          }
        case Destroy:
        {
          Object[] o = fmtTranscriptAttributeId.parse(annotation.getId());
          String attribute = o[0].toString();
          sqlDeleteTranscriptAttribute.setString(2, attribute);
          sqlDeleteTranscriptAttribute.setInt(3, ((Long)o[1]).intValue());
          sqlDeleteTranscriptAttribute.executeUpdate();
          break;
        } // Destroy
      } // switch on change type
	 
      annotation.put("@SqlUpdated", Boolean.TRUE); // flag the anchor as having been updated
    } catch(ParseException exception) {
      System.err.println("Error parsing ID for transcript attribute: "+annotation.getId());
      throw new StoreException(
        "Error parsing ID for transcript attribute: "+annotation.getId(), exception);
    }
  } // end of saveTranscriptAttributeChanges()

  /**
   * Saves changes to a transcript attribute annotation.
   * @param annotation The annotation whose changes should be saved.
   * @param sqlInsertParticipantAttribute Prepared statement for inserting an attribute value.
   * @param sqlUpdateParticipantAttribute Prepared statement for updating an attribute value.
   * @param sqlDeleteParticipantAttribute Prepared statement for deleting an attribute value.
   * @param sqlDeleteAllParticipantAttributesOnLayer Prepared statement for deleting all attribute values on a given layer.
   * @throws StoreException If an ID can't be parsed.
   * @throws SQLException If a database error occurs.
   */
  protected void saveParticipantAttributeChanges(
    Annotation annotation, PreparedStatement sqlInsertParticipantAttribute,
    PreparedStatement sqlUpdateParticipantAttribute,
    PreparedStatement sqlDeleteParticipantAttribute,
    PreparedStatement sqlDeleteAllParticipantAttributesOnLayer,
    PreparedStatement sqlUpdateParticipantPassword,
    PreparedStatement sqlInsertParticipantCorpus,
    PreparedStatement sqlDeleteParticipantCorpus)
    throws SQLException, StoreException, PermissionException {
    try {
      if ("_password".equals(annotation.getLayerId())) { // password update
        Object[] o = fmtMetaAnnotationId.parse(annotation.getParentId());
        int speakerNumber = Integer.parseInt(o[1].toString().replace(",",""));
        sqlUpdateParticipantPassword.setString(1, annotation.getLabel());
        sqlUpdateParticipantPassword.setInt(2, speakerNumber);
        int updated = sqlUpdateParticipantPassword.executeUpdate();
      } else if ("corpus".equals(annotation.getLayerId())) { // corpus update
        switch (annotation.getChange()) {
          case Create: {
            Object[] o = fmtMetaAnnotationId.parse(annotation.getParentId());
            int speakerNumber = Integer.parseInt(o[1].toString().replace(",",""));
            
            sqlInsertParticipantCorpus.setInt(1, speakerNumber);
            sqlInsertParticipantCorpus.setString(2, annotation.getLabel());
            int updated = sqlInsertParticipantCorpus.executeUpdate();
            break;
          } case Destroy: {
              Object[] o = fmtMetaAnnotationId.parse(annotation.getParentId());
              int speakerNumber = Integer.parseInt(o[1].toString().replace(",",""));
              
              sqlDeleteParticipantCorpus.setInt(1, speakerNumber);
              sqlDeleteParticipantCorpus.setString(2, annotation.getLabel());
              int updated = sqlDeleteParticipantCorpus.executeUpdate();
              break;
            } // Destroy
        } // switch on change type
      } else { // regular attribute update
        switch (annotation.getChange()) {
          case Create: {
            String attribute = annotation.getLayerId().substring("participant_".length());
            Object[] o = fmtMetaAnnotationId.parse(annotation.getParentId());
            int speakerNumber = Integer.parseInt(o[1].toString().replace(",",""));
            
            Layer layer = annotation.getLayer();
            if (layer == null) layer = getLayer(annotation.getLayerId());
            if (!layer.getPeers()) {
              // the attribute might be new in this graph, but already exist in the database
              // so delete first and then add
              sqlDeleteAllParticipantAttributesOnLayer.setInt(1, speakerNumber);
              sqlDeleteAllParticipantAttributesOnLayer.setString(2, attribute);
              sqlDeleteAllParticipantAttributesOnLayer.executeUpdate();
            }
            
            sqlInsertParticipantAttribute.setInt(1, speakerNumber);
            sqlInsertParticipantAttribute.setString(2, attribute);
            sqlInsertParticipantAttribute.setString(3, annotation.getLabel());
            if (annotation.getAnnotator() != null) {
              sqlInsertParticipantAttribute.setString(4, annotation.getAnnotator());
            } else {
              sqlInsertParticipantAttribute.setString(4, getUser());
            }
            // if (annotation.getWhen() != null)
            // {
            // 	  sqlInsertParticipantAttribute.setTimestamp(5, new Timestamp(annotation.getWhen().getTime()));
            // }
            // else
            {
              sqlInsertParticipantAttribute.setTimestamp(5, new Timestamp(new java.util.Date().getTime()));
            }
            int updated = sqlInsertParticipantAttribute.executeUpdate();
            break;
          } case Update: {
              Object[] o = fmtParticipantAttributeId.parse(annotation.getId());
              String attribute = o[0].toString();
              int annotationId = ((Long)o[1]).intValue();
              sqlUpdateParticipantAttribute.setString(1, annotation.getLabel());	    
              if (annotation.getAnnotator() != null) {
                sqlUpdateParticipantAttribute.setString(2, annotation.getAnnotator());
              } else {
                sqlUpdateParticipantAttribute.setString(2, getUser());
              }
              // if (annotation.getWhen() != null)
              // {
              // 	  sqlUpdateParticipantAttribute.setTimestamp(3, new Timestamp(annotation.getWhen().getTime()));
              // }
              // else
              {
                sqlUpdateParticipantAttribute.setTimestamp(3, new Timestamp(new java.util.Date().getTime()));
              }
              sqlUpdateParticipantAttribute.setString(4, attribute);
              sqlUpdateParticipantAttribute.setInt(5, annotationId);
              sqlUpdateParticipantAttribute.executeUpdate();
              break;
            } case Destroy: {
                Object[] o = fmtParticipantAttributeId.parse(annotation.getId());
                String attribute = o[0].toString();
                int annotationId = ((Long)o[1]).intValue();
                sqlDeleteParticipantAttribute.setString(1, attribute);
                sqlDeleteParticipantAttribute.setInt(2, annotationId);
                sqlDeleteParticipantAttribute.executeUpdate();
                break;
              } // Destroy
        } // switch on change type
      } // not password update
      annotation.put("@SqlUpdated", Boolean.TRUE); // flag the annotation as having been updated
    } catch(ParseException exception) {
      System.err.println("Error parsing ID for transcript attribute: "+annotation.getId());
      throw new StoreException(
        "Error parsing ID for transcript attribute: "+annotation.getId(), exception);
    }
  } // end of saveParticipantAttributeChanges()

  /**
   * Creates an annotation starting at <var>fromId</var> and ending at <var>toId</var>.
   * @param id The ID of the transcript.
   * @param fromId The start anchor's ID, which can be null if the layer is a tag layer.
   * @param toId The end anchor's ID, which can be null if the layer is a tag layer.
   * @param layerId The layer ID of the resulting annotation.
   * @param label The label of the resulting annotation.
   * @param confidence The confidence rating.
   * @param parentId The new annotation's parent's ID.
   * @return The ID of the new annotation.
   */
  public String createAnnotation(
    String id, String fromId, String toId, String layerId, String label, Integer confidence,
    String parentId)
    throws StoreException, PermissionException, GraphNotFoundException {
    requireEdit();
    Schema schema = getSchema();
    Layer layer = schema.getLayer(layerId);
    if (layer.get("scope") == null)
      throw new StoreException("Only temporal layers are supported: " + layerId);
    Annotation[] annotations = getMatchingAnnotations("id = '"+parentId+"'");
    if (annotations.length < 1)
      throw new StoreException("Invalid parent: " + parentId);
    Annotation parent = annotations[0];
    if (!layer.getParentId().equals(parent.getLayerId()))
      throw new StoreException("Layer "+layerId+" is not a child of " + parent.getLayerId());
    Layer parentLayer = schema.getLayer(parent.getLayerId());
    if (layer.getAlignment() == Constants.ALIGNMENT_NONE) {
      // tag layer - ignore given anchors, and use the parent's instead
      fromId = parent.getStartId();
      toId = parent.getEndId();
    }
    String[] anchorIds = {fromId, toId};
    Anchor[] anchors = getAnchors(id, anchorIds);
    if (anchors.length < 1) throw new StoreException("Invalid start anchor: " + fromId);
    Anchor from = anchors[0];
    if (anchors.length < 2) throw new StoreException("Invalid end anchor: " + toId);
    Anchor to = anchors[1];
    try {
      Object[] parentAttributes = fmtAnnotationId.parse(parent.getId());
      Long parentAnnotationId = (Long)parentAttributes[2];
      if (!layer.getPeers()) {
        // only one child allowed, so delete any children
        PreparedStatement sql = getConnection().prepareStatement(
          "DELETE FROM annotation_layer_"+layer.get("layer_id") +" WHERE parent_id = ?");
        sql.setLong(1, parentAnnotationId);
        sql.executeUpdate();
        sql.close();
      }
      String sql = "INSERT INTO annotation_layer_?"
        + " (ag_id, label, label_status, start_anchor_id, end_anchor_id,"
        + " parent_id, ordinal, annotated_by, annotated_when"
        + (!layer.get("scope").equals("F")?
           ", turn_annotation_id":"")
        + (layer.get("scope").equals("S")||layer.get("scope").equals("W")?
           ", ordinal_in_turn, word_annotation_id":"")
        + (layer.get("scope").equals("S")?", ordinal_in_word, segment_annotation_id":"")
        + ")"
        + " SELECT ag_id, ?, ?, "
        // non-aligned layers use their parent anchors, regardless of startId/endId
        +(layer.getAlignment()==Constants.ALIGNMENT_NONE?"start_anchor_id, end_anchor_id,":"?, ?,")
        + " annotation_id,"
        +(!layer.getPeers()?"1":
          // there may already be tags, so choose the next ordinal for each parent
          "(SELECT COUNT(*)+1 FROM annotation_layer_? WHERE parent_id = ?)")
        +", ?, Now()"
        + (!layer.get("scope").equals("F")?
           ", turn_annotation_id" :"")
        + (layer.get("scope").equals("S")||layer.get("scope").equals("W")?
           ", ordinal_in_turn, word_annotation_id":"")
        + (layer.get("scope").equals("S")?", ordinal_in_word, segment_annotation_id":"")
        + " FROM annotation_layer_?"
        +" WHERE annotation_id = ?";
      PreparedStatement sqlInsertAnnotation = getConnection().prepareStatement(sql);
      int p = 1;
      sqlInsertAnnotation.setInt(p++, ((Integer)layer.get("layer_id")).intValue());
      sqlInsertAnnotation.setString(p++, label);
      sqlInsertAnnotation.setInt(p++, confidence);

      if (layer.getAlignment() != Constants.ALIGNMENT_NONE) { // explicit anchors
        Object[] anchorAttributes = fmtAnchorId.parse(from.getId());
        sqlInsertAnnotation.setLong(p++, (Long)anchorAttributes[0]);
        
        anchorAttributes = fmtAnchorId.parse(to.getId());
        sqlInsertAnnotation.setLong(p++, (Long)anchorAttributes[0]);
      }
      
      if (layer.getPeers()) {
        sqlInsertAnnotation.setInt(p++, ((Integer)layer.get("layer_id")).intValue());
        sqlInsertAnnotation.setLong(p++, parentAnnotationId);
      }

      sqlInsertAnnotation.setString(p++, getUser());
      sqlInsertAnnotation.setInt(p++, ((Integer)parentLayer.get("layer_id")).intValue());
      sqlInsertAnnotation.setLong(p++, parentAnnotationId);
      sqlInsertAnnotation.executeUpdate();
      sqlInsertAnnotation.close();
      sqlInsertAnnotation = getConnection().prepareStatement("SELECT LAST_INSERT_ID()");
      ResultSet rsInsert = sqlInsertAnnotation.executeQuery();
      rsInsert.next();
      long annotation_id = rsInsert.getLong(1);
      rsInsert.close();
      sqlInsertAnnotation.close();
      Object[] annotationAttributes = {
        layer.get("scope").equals("F")?"":((String)layer.get("scope")).toLowerCase(),
        layer.get("layer_id"),
        Long.valueOf(annotation_id)};
      return fmtAnnotationId.format(annotationAttributes);
    } catch(ParseException exception) {
      System.err.println("Error parsing parent ID: "+parent.getId());
      throw new StoreException("Error parsing parent ID: "+parent.getId());
    } catch(SQLException exception) {
      System.err.println("SQL error: " + exception);
      exception.printStackTrace(System.err);
      throw new StoreException("Invalid query."); // TODO i18n
    }
  }

  /**
   * Destroys the annotation with the given ID.
   * @param id The ID of the transcript.
   * @param annotationId The annotation's ID.
   */
  public void destroyAnnotation(String id, String annotationId)
    throws StoreException, PermissionException, GraphNotFoundException {
    requireEdit();
    try {
      Object[] attributes = fmtAnnotationId.parse(annotationId);
      Long layer_id = (Long)attributes[1];
	 
      PreparedStatement sql = getConnection().prepareStatement(
        "SELECT COUNT(*) FROM layer WHERE parent_id = ?");
      sql.setLong(1, layer_id);
      ResultSet rs = sql.executeQuery();
      rs.next();
      try {
        if (rs.getInt(1) > 0) throw new StoreException("There are child layers: " + layer_id);
      } finally {
        rs.close();
        sql.close();
      }
	 
      Long annotation_id = (Long)attributes[2];
      // only one child allowed, so delete any children
      sql = getConnection().prepareStatement(
        "DELETE FROM annotation_layer_"+layer_id +" WHERE annotation_id = ?");
      sql.setLong(1, annotation_id);
      sql.executeUpdate();
      sql.close();
    } catch(ParseException exception) {
      System.err.println("Error parsing ID: "+annotationId);
      throw new StoreException("Error parsing ID: "+annotationId);
    } catch(SQLException exception) {
      System.err.println("SQL error: " + exception);
      exception.printStackTrace(System.err);
      throw new StoreException("Invalid query."); // TODO i18n
    }
  }

  /**
   * List the predefined media tracks available for transcripts.
   * @return An ordered list of media track definitions.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted. 
   */
  public MediaTrackDefinition[] getMediaTracks() throws StoreException, PermissionException {
    try {
      PreparedStatement sql = getConnection().prepareStatement(
        "SELECT * FROM media_track ORDER BY display_order, suffix");
      ResultSet rs = sql.executeQuery();
      Vector<MediaTrackDefinition> tracks = new Vector<MediaTrackDefinition>();
      while (rs.next()) {
        tracks.add(new MediaTrackDefinition(
                     rs.getString("suffix"), rs.getString("description")));
      } // next layer
      rs.close();
      sql.close();
      return tracks.toArray(new MediaTrackDefinition[0]);
    } catch(SQLException exception) {
      System.err.println("SQL error: " + exception);
      exception.printStackTrace(System.err);
      throw new StoreException("Invalid query."); // TODO i18n
    }
  }

  /**
   * Returns all the available conversions from one media type to another.
   * @return A map of MIME types to a set of MIME types that the key can be converted to.
   */
  protected Map<String,Set<String>> getMediaConversions() {
    Map<String,Set<String>> mConversionsFrom = new HashMap<String,Set<String>>();
    try {
      if (getSystemAttribute("ffmpegPath") != null) {
        PreparedStatement sqlSourceType = getConnection().prepareStatement(
          "SELECT DISTINCT from_mimetype FROM media_conversion WHERE method = 'ffmpeg'"
          +" ORDER BY from_mimetype");
        PreparedStatement sqlDestinationType = getConnection().prepareStatement(
          "SELECT to_mimetype FROM media_conversion"
          +" WHERE method = 'ffmpeg' AND from_mimetype = ?"
          +" ORDER BY to_mimetype");
        ResultSet rsSourceType = sqlSourceType.executeQuery();
        while (rsSourceType.next()) {
          String fromMimetype = rsSourceType.getString("from_mimetype");
          sqlDestinationType.setString(1, fromMimetype);
          ResultSet rsDestinationType = sqlDestinationType.executeQuery();
          HashSet<String> toMimeTypes = new HashSet<String>();
          while (rsDestinationType.next()) {
            toMimeTypes.add(rsDestinationType.getString("to_mimetype"));
          } // next destination type
          rsDestinationType.close();
          mConversionsFrom.put(fromMimetype, toMimeTypes);
        } // next source media type
        rsSourceType.close();
        sqlSourceType.close();
        sqlDestinationType.close();
      }
    } catch(SQLException exception) {
      System.err.println("getMediaConversions: " + exception);
      exception.printStackTrace(System.err);
    }
    return mConversionsFrom;
  } // end of mediaConversions()

  /**
   * List the media available for the given transcript.
   * @param id The transcript ID.
   * @return List of media files available for the given transcript.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   * @throws GraphNotFoundException If the transcript was not found in the store.
   */
  public MediaFile[] getAvailableMedia(String id) 
    throws StoreException, PermissionException, GraphNotFoundException {
    Map<String,MediaFile> files = new LinkedHashMap<String,MediaFile>(); // key=name
    String[] layers = { "corpus", "episode" };
    Graph graph = getTranscript(id, layers);
    if (graph.first("corpus") == null || graph.first("episode") == null) {
      // corpus/episode not correctly set
      return new MediaFile[0];
    }

    Annotation corpus = graph.first("corpus");
    Annotation episode = graph.first("episode");
    if (corpus != null && episode != null // (shouldn't happen, but let's be fault tolerant)
        && corpus.getLabel() != null && episode.getLabel() != null) {
      File corpusDir = new File(getFiles(), corpus.getLabel());
      File episodeDir = new File(corpusDir, episode.getLabel());
      MediaTrackDefinition[] tracks = getMediaTracks();
      String baseName = graph.getId().replaceAll("\\.[^.]*$","");
      File[] aDirs = episodeDir.listFiles(new FileFilter() {
          public boolean accept(File file) {
            return file.isDirectory() && !file.getName().equals("trs");
          }
        });
      if (aDirs != null) {
        for (File dir : aDirs) {
          // look for a file with the same name as the transcript, and 
          // an extension that matches the directory name
          String extension = dir.getName();
          for (MediaTrackDefinition track : tracks) {
            String fileName = baseName + track.getSuffix() + "." + extension;
            File f = new File(dir, fileName);
            if (f.exists()) {
              MediaFile mediaFile = new MediaFile(f, track.getSuffix());
              try {
                if (hasAccess(id, mediaFile.getType().substring(0,1))) {
                  if (getBaseUrl() == null) { // TODO check this isn't a security risk
                    mediaFile.setUrl(f.toURI().toString());
                  } else {
                    StringBuffer url = new StringBuffer(getBaseUrl());
                    url.append("/files/");
                    url.append(graph.first("corpus").getLabel());
                    url.append("/");
                    url.append(graph.first("episode").getLabel());
                    url.append("/");
                    url.append(mediaFile.getExtension());
                    url.append("/");
                    url.append(f.getName());
                    mediaFile.setUrl(url.toString());
                  }
                  files.put(mediaFile.getName(), mediaFile);
                } // user has access
              } catch (SQLException x) {
                throw new StoreException(x);
              }
            } // f.exists()
          } // next track
        } // next subdir
      } // there are directories
    } // has corpus/episode
    
    Map<String,Set<String>> mConversionsFrom = getMediaConversions();
    if (mConversionsFrom.size() > 0) { // conversions are possible
      // for each existing file (traverse a copy of values, to avoid concurrent modification)
      for (MediaFile file : new Vector<MediaFile>(files.values())) {
        if (mConversionsFrom.containsKey(file.getMimeType())) {
          // there's a conversion for this type of file
          // for each destination format
          for (String toMimeType : mConversionsFrom.get(file.getMimeType())) {
            String toExtension = MediaFile.MimeTypeToSuffix().get(toMimeType);
            // add a file
            File toDir = new File(
              file.getFile().getParentFile().getParentFile(), toExtension);
            File toFile = new File(
              toDir, file.getNameWithoutSuffix() + "." + toExtension);
                  
            if (!toFile.exists()) {
              MediaFile possibleFile = new MediaFile(toFile, file);
              possibleFile.setGenerateFrom(file);
              // only if we're not generating it some other way
              if (!files.containsKey(possibleFile.getName())) {
                try {
                  if (hasAccess(id, possibleFile.getType().substring(0,1))) {
                    if (getBaseUrl() == null) { // TODO check this isn't a security risk
                      possibleFile.setUrl(toFile.toURI().toString());
                    } else {
                      StringBuffer url = new StringBuffer(getBaseUrl());
                      url.append("/files/");
                      url.append(graph.first("corpus").getLabel());
                      url.append("/");
                      url.append(graph.first("episode").getLabel());
                      url.append("/");
                      url.append(possibleFile.getExtension());
                      url.append("/");
                      url.append(possibleFile.getName());
                      possibleFile.setUrl(url.toString());
                    }
                    files.put(possibleFile.getName(), possibleFile);
                  } // user has access
                } catch (SQLException x) {
                  throw new StoreException(x);
                }
              } // not already registered as available
            } // doesn't already exist
          } // next destination format
        } // conversion exists
      } // next file         
    } // conversions are possible
    return files.values().toArray(new MediaFile[0]);
  }
 
  /**
   * Gets a given media track for a given transcript.
   * @param id The transcript ID.
   * @param trackSuffix The track suffix of the media - see {@link MediaTrackDefinition#suffix}.
   * @param mimeType The MIME type of the media, which may include parameters for type
   * conversion, e.g. "audio/wav; samplerate=16000".
   * @return A URL to the given media for the given transcript, or null if the given media
   * doesn't exist.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   * @throws GraphNotFoundException If the transcript was not found in the store.
   */
  public String getMedia(String id, String trackSuffix, String mimeType) 
    throws StoreException, PermissionException, GraphNotFoundException {
    return getMedia(id, trackSuffix, mimeType, null, null);
  }
  /**
   * Gets a given media track for a given  was not found in the store..
   * @param id The transcript ID.
   * @param trackSuffix The track suffix of the media - see {@link MediaTrackDefinition#suffix}.
   * @param mimeType The MIME type of the media, which may include parameters for type
   * conversion, e.g. "audio/wav; samplerate=16000"
   * @param startOffset The start offset of the media sample, or null for the start of the whole
   * recording. 
   * @param endOffset The end offset of the media sample, or null for the end of the
   * whole recording. 
   * @return A URL to the given media for the given transcript, or null if the given
   * media doesn't exist. 
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   * @throws GraphNotFoundException If the transcript was not found in the store.
   */
  public String getMedia(
    String id, String trackSuffix, String mimeType, Double startOffset, Double endOffset) 
    throws StoreException, PermissionException, GraphNotFoundException {
    try {
      if (!hasAccess(id, mimeType.substring(0,1))) {
        throw new PermissionException(getUser(), "Access not permitted to " + mimeType);
      }
    } catch (SQLException x) {
      throw new StoreException(x);
    }

    String[] layers = { "corpus", "episode" };
    Graph graph = getTranscript(id, layers);
    File corpusDir = new File(getFiles(), graph.first("corpus").getLabel());
    File episodeDir = new File(corpusDir, graph.first("episode").getLabel());
    String[] mimeTypeParts = mimeType.split(";"); // e.g. "audio/wav; samplerate=16000"
    LinkedHashMap<String,String> mimeTypeParameters = new LinkedHashMap<String,String>();
    for (int p = 1; p < mimeTypeParts.length; p++) {
      int equals = mimeTypeParts[p].indexOf('=');
      if (equals >= 0) {
        mimeTypeParameters.put(mimeTypeParts[p].substring(0, equals).toLowerCase().trim(),
                               mimeTypeParts[p].substring(equals+1).trim());
      } // '=' is present
    } // next parameter
    mimeType = mimeTypeParts[0];
    String extension = MediaFile.MimeTypeToSuffix().get(mimeType);
    if (extension == null) {
      if (mimeType.startsWith("application/")) { // the extension is most likely the rest
        extension = mimeType.substring("application/".length());
      } else {
        throw new StoreException("Unknown MIME type: " + mimeType);
      }
    }
    File mediaDir = new File(episodeDir, extension);
    if (trackSuffix == null) trackSuffix = getMediaTracks()[0].getSuffix();
    String fileName = graph.getId().replaceAll("\\.[^.]*$","") + trackSuffix + "." + extension;
    File file = new File(mediaDir, fileName);
    if (!file.exists()) { // maybe the exension is upper case?
      extension = extension.toUpperCase();
      mediaDir = new File(episodeDir, extension);
      if (trackSuffix == null) trackSuffix = getMediaTracks()[0].getSuffix();
      fileName = graph.getId().replaceAll("\\.[^.]*$","") + trackSuffix + "." + extension;
      file = new File(mediaDir, fileName);
    }
    if (file.exists()) {
      if (startOffset == null && endOffset == null) {
        if (getBaseUrl() == null) // TODO check this isn't a security risk
        {
          return file.toURI().toString(); // TODO resampling?
        } else {
          try {
            StringBuffer url = new StringBuffer(getBaseUrl());
            url.append("/files/");
            url.append(URLEncoder.encode(graph.first("corpus").getLabel(), "UTF-8")
                       .replace("+", "%20"));
            url.append("/");
            url.append(URLEncoder.encode(graph.first("episode").getLabel(), "UTF-8")
                       .replace("+", "%20"));
            url.append("/");
            url.append(URLEncoder.encode(extension, "UTF-8")
                       .replace("+", "%20"));
            url.append("/");
            url.append(URLEncoder.encode(fileName, "UTF-8")
                       .replace("+", "%20"));
            return url.toString();
          } catch(UnsupportedEncodingException exception) {
            throw new StoreException(exception);
          }
        }
      }
      else // a fragment
      {
        if (getBaseUrl() == null) // TODO check this isn't a security risk
        {
          FragmentExtractor extractor = new FragmentExtractor();
          if (mimeTypeParameters.containsKey("samplerate")
              && mimeTypeParameters.get("samplerate") != null) {
            extractor.setSampleRate(
              Integer.parseInt(mimeTypeParameters.get("samplerate")));
          }
          extractor.setStart(startOffset);
          extractor.setEnd(endOffset);
          try {
            File fragment = File.createTempFile(
              "SqlGraphStore.getMedia_",
              Graph.FragmentId(id, startOffset, endOffset) + "." + IO.Extension(file));
            fragment.deleteOnExit();
            MediaThread thread = extractor.start(mimeType, file, mimeType, fragment);
            // wait for extractor to finish
            thread.join();
            if (thread.getLastError() == null) {
              return fragment.toURI().toString();
            } else {
              throw new StoreException(
                "Could not extract " + file.getName() + " to " + fragment.getName(),
                thread.getLastError());
            }
          } catch(Exception exception) {
            throw new StoreException(exception);
          }
        } else {
          StringBuffer url = new StringBuffer(getBaseUrl());
          url.append("/soundfragment");
          url.append("?id=");
          try {
            url.append(URLEncoder.encode(id, "UTF-8")
                       .replace("+", "%20"));
          } catch(UnsupportedEncodingException exception) {
            throw new StoreException(exception);
          }
          url.append("&start=");
          url.append(startOffset.toString());
          url.append("&end=");
          url.append(endOffset.toString());          
          for (String name : mimeTypeParameters.keySet()) {
            url.append("&");
            url.append(name);
            url.append("=");
            url.append(mimeTypeParameters.get(name));
          } // next parameter
          return url.toString();
        }
      }
    } else {
      return null;
    }
  }

  /**
   * Saves the given media for the given transcript.
   * @param id The transcript ID
   * @param mediaUrl A URL to the media content. If this is a <tt>file:</tt> URL, the file
   * will be moved rather than copied, if possible. Otherwise, the content will be downloaded.
   * @param trackSuffix The track suffix of the media - see {@link MediaTrackDefinition#suffix}.
   * @throws StoreException If an error prevents the media from being saved.
   * @throws PermissionException If saving the media is not permitted.
   * @throws GraphNotFoundException If the transcript doesn't exist.
   */
  public MediaFile saveMedia(String id, String mediaUrl, String trackSuffix)
    throws StoreException, PermissionException, GraphNotFoundException {
    requireEdit();
    if (trackSuffix == null) trackSuffix = "";
    Vector<File> toDelete = new Vector<File>();
    try {
      String[] layers = { "corpus", "episode" };
      Graph graph = getTranscript(id, layers);
      File corpusDir = new File(getFiles(), graph.first("corpus").getLabel());
      if (!corpusDir.exists()) corpusDir.mkdir();
      File episodeDir = new File(corpusDir, graph.first("episode").getLabel());
      if (!episodeDir.exists()) episodeDir.mkdir();
	 
      // get the content
      File source = null;
      if (!mediaUrl.startsWith("file:")) {
        // not file URL, but we need one, so download content to a temporary file
        URL url = new URL(mediaUrl);
        URLConnection connection = url.openConnection();
        String mimeType = connection.getContentType();
        String extension = MediaFile.MimeTypeToSuffix().get(mimeType);
        if (extension == null) throw new StoreException("Unknown MIME type: " + mimeType);
        String destinationName = graph.getId().replaceAll("\\.[^.]*$","") 
          + trackSuffix + "." + extension;
        source = File.createTempFile(destinationName, "."+extension);
        source.deleteOnExit();
        toDelete.add(source);
        try {
          IO.SaveUrlConnectionToFile(connection, source);
        } catch(IOException exception) {
          throw new StoreException(
            "Could not save " + url + " to " + source.getPath(), exception);
        }
      } else { // file URL
        // rename/take a copy of the file, so the caller can delete it with impunity
        File mediaFile = new File(new URI(mediaUrl));
        source = File.createTempFile("SqlGraphStore.saveMedia_", "_" + mediaFile.getName());
        source.deleteOnExit();
        toDelete.add(source);
        if (!mediaFile.renameTo(source)) { // can't rename, have to copy the data
          try {
            IO.Copy(mediaFile, source);
          } catch(IOException exception) {
            throw new StoreException(
              "Could not copy " + mediaFile.getPath() + " to " + source.getPath(), exception);
          }
        } // copy instead of rename
      }

      // determine the destination path
      MediaFile mediaFile = new MediaFile(source, trackSuffix);
      if (!hasAccess(id, mediaFile.getType().substring(0,1))) { // they have no access to this media
        throw new PermissionException("No access to: " + mediaFile.getType()); // TODO i18n
      }
      File mediaDir = new File(
        episodeDir, mediaFile.getExtension()
        // downcase media file extensions - i.e. .WAV -> .wav
        .toLowerCase());
      if (!mediaDir.exists()) mediaDir.mkdir();
      String destinationName = graph.getId().replaceAll("\\.[^.]*$","") 
        + trackSuffix + "." + mediaFile.getExtension()
        // downcase media file extensions - i.e. .WAV -> .wav
        .toLowerCase();
      File destination = new File(mediaDir, destinationName);
	 
      // backup old file if it exists
      IO.Backup(destination);

      String downsampleWav = getSystemAttribute("downsampleWav");
      if (downsampleWav.length() > 0 && mediaFile.getMimeType().equals("audio/wav")) {
        File downsampled = File.createTempFile(
          "SqlGraphStore.saveMedia_", "_"+downsampleWav+"_" + mediaFile.getName());
        downsampled.deleteOnExit();
        toDelete.add(downsampled);
	    
        ParameterSet configuration = new ParameterSet();
        configuration.addParameter(
          new Parameter("sampleRate", downsampleWav.equals("mono16kHz")?16000:22050));
        MediaConverter resampler = new Resampler();
        resampler.configure(configuration);

        // run the resampler
        MediaThread thread = resampler.start(
          mediaFile.getMimeType(), source, mediaFile.getMimeType(), downsampled);

        // wait for resampler to finish
        thread.join();
        if (thread.getLastError() == null) {
          source = downsampled;
        } else {
          throw new StoreException(
            "Could not resample " + source.getPath()
            + " to " + downsampleWav, thread.getLastError());
        }
      }

      // does the file need censoring?
      String censorshipRegexp = getSystemAttribute("censorshipRegexp");
      if (censorshipRegexp == null || censorshipRegexp.length() == 0) {
        // no censorship required, so just move the file
        if (!source.renameTo(destination)) { // can't rename, have to copy the data
          try {
            IO.Copy(source, destination);
          } catch(IOException exception) {
            throw new StoreException(
              "Could not copy " + source.getPath()
              + " to " + destination.getPath(), exception);
          }
        } // copy instead of rename
      } else { // censorship required

        // check ffmpeg configuration
        Pattern censorshipPattern = Pattern.compile(censorshipRegexp);
        String censorshipLayer = getSystemAttribute("censorshipLayer");
        String censorshipFfmpegAudioFilter = getSystemAttribute("censorshipFfmpegAudioFilter");
        String ffmpegPath = getSystemAttribute("ffmpegPath");
        if (ffmpegPath == null || ffmpegPath.length() == 0) {
          throw new StoreException("Censorship is configured, but ffmpeg path is not set");
        }
        File exe = new File(ffmpegPath, "ffmpeg");
        if (!exe.exists()) exe = new File(ffmpegPath, "ffmpeg.exe");
        if (!exe.exists()) {
          throw new StoreException(
            "Censorship is configured, but ffmpeg path is invalid: " + ffmpegPath);
        }

        // load the censorship layer
        Schema schema = getSchema();
        String[] censorshipLayers = {
          schema.getParticipantLayerId(),
          schema.getTurnLayerId(),
          schema.getUtteranceLayerId(),
          censorshipLayer };
        graph = getTranscript(id, censorshipLayers);
	    
        // get censorship boundaries
        Vector<Double> boundaries = new Vector<Double>();
        double lastEnd = 0.0;
        // get all censor annotations, sorted by anchor to ensure that if list returns
        // them out of order, they're processed in temporal order
        for (Annotation annotation : new AnnotationsByAnchor(graph.all(censorshipLayer))) {
          if (censorshipPattern.matcher(annotation.getLabel()).matches()) {
            // matching annotation
            try {
              // ensure points don't go backwards, and use offset Min/Max to widen interval
              // when alignment is uncertain
              double start = annotation.getStart().getOffset().doubleValue();
              if (annotation.getStart().getConfidence()
                  < Constants.CONFIDENCE_AUTOMATIC) { // uncertain start
                // find the containing utterance, and use its start
                Annotation turn = annotation.first(schema.getTurnLayerId());
                for (Annotation line : turn.all(schema.getUtteranceLayerId())) {
                  if (line.includesOffset(start)) { // found the containing utterance
                    start = line.getStart().getOffset();
                    if (line.getStart().getConfidence()
                        < Constants.CONFIDENCE_AUTOMATIC) { // still uncertain start
                      // use turn start
                      start = turn.getStart().getOffset();
                    }
                    break;
                  } // found the containing utterance
                } // next line
              } // uncertain start
              // ensure doesn't got backwards
              start = Math.max(start, lastEnd);
              double end = annotation.getEnd().getOffset().doubleValue();
              if (annotation.getEnd().getConfidence() < Constants.CONFIDENCE_AUTOMATIC) {
                // uncertain end
                        
                // find the containing utterance, and use its end
                Annotation turn = annotation.first(schema.getTurnLayerId());
                for (Annotation line : turn.all(schema.getUtteranceLayerId())) {
                  if (line.includesOffset(end)) { // found the containing utterance
                    end = line.getEnd().getOffset();
                    if (line.getEnd().getConfidence() < Constants.CONFIDENCE_AUTOMATIC)
                    { // still uncertain end
                      // use turn end
                      end = turn.getEnd().getOffset();
                    }
                    break;
                  } // found the containing utterance
                } // next line
              } // uncertain end
		     
              // add interval
              boundaries.add(start);
              boundaries.add(end);
		     
              lastEnd = end;
            } catch(NullPointerException exception) {
              // rollback possibl nullification of anchors, to give the most info possible
              annotation.getStart().rollback();
              annotation.getEnd().rollback();
              throw new StoreException("Uncertain boundaries for annotation: " + annotation
                                       + " ("+annotation.getStart()+"-"+annotation.getEnd()
                                       +")");
            }
          } // matching annotation
        } // next annotation

        // create a censor
        ParameterSet configuration = new ParameterSet();
        configuration.addParameter(new Parameter("ffmpegPath", exe.getParentFile()));
        configuration.addParameter(new Parameter("audioFilter", censorshipFfmpegAudioFilter));
        configuration.addParameter(new Parameter("deleteSource", Boolean.TRUE));
        MediaCensor censor = new FfmpegCensor();
        censor.configure(configuration);

        // run the censor
        MediaThread thread
          = censor.start(mediaFile.getMimeType(), source, boundaries, destination);
        thread.join();

      } // censorship required

      MediaFile resultingFile = new MediaFile(destination, trackSuffix);
      if (getBaseUrl() != null) {
        StringBuffer url = new StringBuffer(getBaseUrl());
        url.append("/files/");
        url.append(corpusDir.getName());
        url.append("/");
        url.append(episodeDir.getName());
        url.append("/");
        url.append(resultingFile.getExtension());
        url.append("/");
        url.append(resultingFile.getName());
        resultingFile.setUrl(url.toString());
      }
      return resultingFile;
    } catch (URISyntaxException urix) {
      throw new StoreException("Invalid URL: " + mediaUrl, urix);
    } catch (Throwable t) {
      throw new StoreException(t);
    } finally {
      // anything added to toDelete should be deleted, if it's still there
      for (File f : toDelete) f.delete();
    }
  }
   
  /**
   * Saves the given source file for the given transcript.
   * @param id The transcript ID
   * @param url A URL to the transcript.
   * @throws StoreException If an error prevents the media from being saved.
   * @throws PermissionException If saving the media is not permitted.
   * @throws GraphNotFoundException If the transcript doesn't exist.
   */
  public void saveSource(String id, String url)
    throws StoreException, PermissionException, GraphNotFoundException {
    requireEdit();
    try {
      String[] layers = { "corpus", "episode" };
      Graph graph = getTranscript(id, layers);
      File corpusDir = new File(getFiles(), graph.first("corpus").getLabel());
      if (!corpusDir.exists()) corpusDir.mkdir();
      File episodeDir = new File(corpusDir, graph.first("episode").getLabel());
      if (!episodeDir.exists()) episodeDir.mkdir();
	 
      // get the content
      if (url.startsWith("file:")) { // file URL
        File sourceDir = new File(episodeDir, "trs");
        if (!sourceDir.exists()) sourceDir.mkdir();
        File source = new File(new URI(url));
        String destinationName
          = graph.getId().replaceAll("\\.[^.]*$","") + "." + IO.Extension(source);
        File destination = new File(sourceDir, destinationName);

        // backup old file if it exists
        IO.Backup(destination);

        if (!source.renameTo(destination)) { // can't rename, have to copy the data TODO
          try {
            IO.Copy(source, destination);
          } catch(IOException exception) {
            throw new StoreException(
              "Could not copy " + source.getPath()
              + " to " + destination.getPath(), exception);
          }
        } // copy instead of rename
      } else { // not file URL
        String extension = url.substring(url.lastIndexOf('.') + 1);
        File sourceDir = new File(episodeDir, "trs");
        String destinationName = graph.getId().replaceAll("\\.[^.]*$","")  + "." + extension;
        File destination = new File(sourceDir, destinationName);
        try {
          IO.SaveUrlToFile(new URL(url), destination);
        } catch(IOException exception) {
          throw new StoreException(
            "Could not save " + url + " to " + destination.getPath(), exception);
        }	    
      } // not file URL
    } catch (URISyntaxException urix) {
      throw new StoreException("Invalid URL: " + url, urix);
    } catch (Throwable t) {
      throw new StoreException(t);
    }
  }

  /**
   * Gets the source file for the given transcript.
   * @param id The transcript ID
   * @return A URL to the transcript.
   * @throws StoreException If an error prevents the media from being saved.
   * @throws PermissionException If saving the media is not permitted.
   * @throws GraphNotFoundException If the transcript doesn't exist.
   */
  public String getSource(String id)
    throws StoreException, PermissionException, GraphNotFoundException {
    try {
      String[] layers = { "corpus", "episode" };
      Graph graph = getTranscript(id, layers);
      File corpusDir = new File(getFiles(), graph.first("corpus").getLabel());
      File episodeDir = new File(corpusDir, graph.first("episode").getLabel());
      File sourceDir = new File(episodeDir, "trs");
      File source = new File(sourceDir, graph.getId());
      if (!source.exists()) throw new GraphNotFoundException(source.getPath());

      if (baseUrl == null) {
        return source.toURI().toString();
      } else {
        return baseUrl + getFiles().getName() + "/" + corpusDir.getName() + "/" + episodeDir.getName() + "/" + sourceDir.getName() + "/" + source.getName();
      } // not file URL
    } catch (GraphNotFoundException x) {
      throw x;
    } catch (Throwable t) {
      throw new StoreException(t);
    }
  }

  /**
   * Saves the given document for the episode of the given transcript.
   * @param id The transcript ID
   * @param url A URL to the document.
   * @throws StoreException If an error prevents the media from being saved.
   * @throws PermissionException If saving the media is not permitted.
   * @throws GraphNotFoundException If the transcript doesn't exist.
   */
  public MediaFile saveEpisodeDocument(String id, String url)
    throws StoreException, PermissionException, GraphNotFoundException {
    requireEdit();
    try {
      String[] layers = { "corpus", "episode" };
      if (!hasAccess(id, "o")) { // they have no access to 'other' media
        throw new PermissionException("No access to documents."); // TODO i18n
      }
      Graph graph = getTranscript(id, layers);
      File corpusDir = new File(getFiles(), graph.first("corpus").getLabel());
      if (!corpusDir.exists()) corpusDir.mkdir();
      File episodeDir = new File(corpusDir, graph.first("episode").getLabel());	
      if (!episodeDir.exists()) episodeDir.mkdir();
      File destination = null;
 
      // get the content
      if (url.startsWith("file:")) { // file URL
        File docDir = new File(episodeDir, "doc");
        if (!docDir.exists()) docDir.mkdir();
        File source = new File(new URI(url));
        // docs saved with their own name
        destination = new File(docDir, source.getName());

        // backup old file if it exists
        IO.Backup(destination);

        if (!source.renameTo(destination)) { // can't rename, have to copy the data TODO
          try {
            IO.Copy(source, destination);
          } catch(IOException exception) {
            throw new StoreException(
              "Could not copy " + source.getPath()
              + " to " + destination.getPath(), exception);
          }
        } // copy instead of rename
      } else { // not file URL
        URL u = new URL(url);
        File docDir = new File(episodeDir, "doc");
        String destinationName = u.getPath();
        if (destinationName.indexOf('/') >= 0) {
          destinationName = destinationName.substring(destinationName.lastIndexOf('/') + 1);
          if (destinationName.length() == 0) destinationName = u.getPath();
        }
        destination = new File(docDir, destinationName);
        try {
          IO.SaveUrlToFile(u, destination);
        } catch(IOException exception) {
          throw new StoreException(
            "Could not save " + url + " to " + destination.getPath(), exception);
        }
      } // not file URL
      MediaFile resultingFile = new MediaFile(destination);
      if (getBaseUrl() != null) {
        StringBuffer docUrl = new StringBuffer(getBaseUrl());
        docUrl.append("/files/");
        docUrl.append(corpusDir.getName());
        docUrl.append("/");
        docUrl.append(episodeDir.getName());
        docUrl.append("/");
        docUrl.append(resultingFile.getExtension());
        docUrl.append("/");
        docUrl.append(resultingFile.getName());
        resultingFile.setUrl(docUrl.toString());
      }
      return resultingFile;
    } catch (URISyntaxException urix) {
      throw new StoreException("Invalid URL: " + url, urix);
    } catch (Throwable t) {
      throw new StoreException(t);
    }
  }

  /**
   * Get a list of documents associated with the episode of the given transcript.
   * @param id The transcript ID.
   * @return List of document files/URLs.
   * @throws StoreException If an error prevents the media from being saved.
   * @throws PermissionException If saving the media is not permitted.
   * @throws GraphNotFoundException If the transcript doesn't exist.
   */
  public MediaFile[] getEpisodeDocuments(String id)
    throws StoreException, PermissionException, GraphNotFoundException {
    Vector<MediaFile> files = new Vector<MediaFile>();
    String[] layers = { "corpus", "episode" };
    Graph graph = getTranscript(id, layers);
    File corpusDir = new File(getFiles(), graph.first("corpus").getLabel());
    File episodeDir = new File(corpusDir, graph.first("episode").getLabel());
    File docDir = new File(episodeDir, "doc");
    if (docDir.exists()) {
      for (File f : docDir.listFiles()) {
        MediaFile mediaFile = new MediaFile(f, "");
        try {
          if (hasAccess(id, mediaFile.getType().substring(0,1))) {
            if (getBaseUrl() == null) { // TODO check this isn't a security risk
              mediaFile.setUrl(f.toURI().toString());
            } else {
              StringBuffer url = new StringBuffer(getBaseUrl());
              url.append("/files/");
              url.append(graph.first("corpus").getLabel());
              url.append("/");
              url.append(graph.first("episode").getLabel());
              url.append("/doc/");
              url.append(f.getName());
              mediaFile.setUrl(url.toString());
            }
            files.add(mediaFile);
          }
        } catch (SQLException x) {
          throw new StoreException(x);
        }
      } // next doc file
    } // the doc dir exists
    return files.toArray(new MediaFile[0]);      
  }
   
  /**
   * Delete a given media or document file.
   * @param id The associated transcript ID.
   * @param fileName The media file name, e.g. {@link MediaFile#name}.
   * @throws StoreException, PermissionException, GraphNotFoundException
   */
  public void deleteMedia(String id, String fileName)
    throws StoreException, PermissionException, GraphNotFoundException {
    requireEdit();
    MediaFile file = null;
    for (MediaFile media : getAvailableMedia(id)) {
      if (media.getName().equals(fileName)) {
        file = media;
        break;
      }
    } // next available media file
    if (file == null) { // media file not found
      // might be an episode document
      for (MediaFile media : getEpisodeDocuments(id)) {
        if (media.getName().equals(fileName)) {
          file = media;
          break;
        }
      } // next episode document      
    } // media file not found

    if (file != null && file.getFile() != null && file.getFile().exists()) { // something to delete

      try {
        if (!hasAccess(id, file.getType().substring(0,1))) { // they have no access to this media
          throw new PermissionException("No access to: " + file.getType()); // TODO i18n
        }
        
        // delete the file
        if (!file.getFile().delete()) {
          throw new StoreException("Could not delete: " + file.getFile().getPath());
        }
      } catch (SQLException x) {
        throw new StoreException(x);
      }      
    } else {
      throw new StoreException("Not found: " + fileName);
    }
  }
   
  /**
   * Generates any media files that are not marked "on demand" and for which there are
   * available conversions.
   * <p>This implementation starts conversion processes, and returns immediately, so new
   * files may not exist immediately after the method returns.
   * @param id The transcript ID.
   * @throws StoreException If an error occurs.
   * @throws PermissionException If the operation is not permitted.
   * @throws GraphNotFoundException If the transcript was not found in the store.
   */
  public void generateMissingMedia(String id)
    throws StoreException, PermissionException, GraphNotFoundException {
    try {
      // for now only ffmpeg conversion is supported TODO
      String ffmpegPath = getSystemAttribute("ffmpegPath");
      if (ffmpegPath == null || ffmpegPath.length() == 0) {
        System.err.println("SqlGraphStore.generateMissingMedia: ffmpegPath not set");
        return;
      }
      File exe = new File(ffmpegPath, "ffmpeg");
      if (!exe.exists()) exe = new File(ffmpegPath, "ffmpeg.exe");
      if (!exe.exists()) {
        System.err.println(
          "SqlGraphStore.generateMissingMedia: ffmpegPath not found: " + exe.getPath());
        return;
      }
	 
      MediaFile[] availableMedia = getAvailableMedia(id);
      // first index them by track suffix, and then by MIME type
      HashMap<String,HashMap<String,MediaFile>> tracks
        = new HashMap<String,HashMap<String,MediaFile>>();
      for (MediaFile file : availableMedia) {
        if (file.getGenerateFrom() != null) continue; // we don't have it yet
        if (!tracks.containsKey(file.getTrackSuffix())) {
          tracks.put(file.getTrackSuffix(), new HashMap<String,MediaFile>());
        }
        tracks.get(file.getTrackSuffix()).put(file.getMimeType(), file);
      } // next file

      // what media types do we want? (key=MIME type, value=extension)
      LinkedHashMap<String,String> wantedTypes = new LinkedHashMap<String,String>();
      PreparedStatement sql = getConnection().prepareStatement(
        "SELECT mimetype, extension FROM media_type WHERE on_demand = 0 ORDER BY mimetype");
      ResultSet rs = sql.executeQuery();
      while (rs.next()) {
        wantedTypes.put(rs.getString("mimetype"), rs.getString("extension"));
      } // next layer
      rs.close();
      sql.close();

      Vector<MediaFile> wantedFiles = new Vector<MediaFile>();
      // check each track, and add any types that are missing
      for (String trackSuffix: tracks.keySet()) {
        HashMap<String,MediaFile> filesInTrack = tracks.get(trackSuffix);
        for (String mediaType : wantedTypes.keySet()) {
          if (!filesInTrack.containsKey(mediaType)) {
            MediaFile wantedFile = new MediaFile();
            wantedFile.setTrackSuffix(trackSuffix);
            wantedFile.setMimeType(mediaType);
            // we'll set the rest of the details later...
            wantedFiles.add(wantedFile);
          }
        } // next wanted type
      } // next track suffix

      if (wantedFiles.size() > 0) { // there are missing files
	    
        // need some info about the graph...
        String[] layers = { "corpus", "episode" };
        Graph graph = getTranscript(id, layers);
        File corpusDir = new File(getFiles(), graph.first("corpus").getLabel());
        File episodeDir = new File(corpusDir, graph.first("episode").getLabel());

        PreparedStatement sqlConversions = getConnection().prepareStatement(
          "SELECT from_mimetype, method, arguments FROM media_conversion"
          +" WHERE method = 'ffmpeg'" // TODO support other conversion methods
          +" AND to_mimetype = ? ORDER BY from_mimetype");

        // for each file we want:
        for (MediaFile wantedFile : wantedFiles) {
          // figure out file name
          String extension = MediaFile.MimeTypeToSuffix().get(wantedFile.getMimeType());
          if (extension == null) extension = wantedTypes.get(wantedFile.getMimeType());
          File mediaDir = new File(episodeDir, extension);
          String destinationName = graph.getId().replaceAll("\\.[^.]*$","") 
            + wantedFile.getTrackSuffix() + "." + extension;
          wantedFile.setFile(new File(mediaDir, destinationName));

          // look for a conversion
          sqlConversions.setString(1, wantedFile.getMimeType());
          ResultSet rsConversions = sqlConversions.executeQuery();
          while (rsConversions.next()) {
            String fromMimeType = rsConversions.getString("from_mimetype");
            // do we have a file with that MIME type in the same track?
            HashMap<String,MediaFile> track = tracks.get(wantedFile.getTrackSuffix());
            if (track.containsKey(fromMimeType)) {
              // the source MIME type exists - we have a winner
              MediaFile gotFile = track.get(fromMimeType);
              // ensure destination directory exists
              if (!wantedFile.getFile().getParentFile().exists()) {
                if (!wantedFile.getFile().getParentFile().mkdir()) {
                  System.err.println(
                    "SqlGraphStore.generateMissingMedia: mkdir "
                    + wantedFile.getFile().getParentFile().getPath() + " - failed");
                }
              }
              // convert
              ParameterSet configuration = new ParameterSet();
              configuration.addParameter(new Parameter("ffmpegPath", exe.getParentFile()));
              configuration.addParameter(
                new Parameter(
                  "conversionCommandLine", rsConversions.getString("arguments")));
              MediaConverter converter = new FfmpegConverter();
              converter.configure(configuration);
              MediaThread thread = converter.start(
                fromMimeType, gotFile.getFile(), wantedFile.getMimeType(),
                wantedFile.getFile());
              break;
            } // the source MIME type exists
          } // next conversion
          rsConversions.close();
        } // next wanted file
        sqlConversions.close();

      } // there are missing files
    } catch(SQLException exception) {
      System.err.println("SQL error: " + exception);
      exception.printStackTrace(System.err);
      throw new StoreException("Invalid query."); // TODO i18n
    } catch(MediaException exception) {
      throw new StoreException(exception);
    }
      
  } // end of generateMissingMedia()

  /**
   * Deletes the given transcript, and all associated files.
   * @param id The ID transcript to delete.
   * @throws StoreException If an error prevents the transcript from being saved.
   * @throws PermissionException If saving the transcript is not permitted.
   * @throws GraphNotFoundException If the transcript doesn't exist.
   */
  public void deleteTranscript(String id)
    throws StoreException, PermissionException, GraphNotFoundException {
    requireEdit();
    try {
      String[] layers = { "corpus", "episode" };
      Graph graph = getTranscript(id, layers);
      int iAgId = ((Integer)graph.get("@ag_id")).intValue();

      // media
      for (MediaFile media : getAvailableMedia(graph.getId())) {
        if (media.getFile().exists() // it might be 'available' because it can be generated
            && !media.getFile().delete()) {
          System.err.println("Could not delete " + media.getFile().getPath());
        }
      } // next media file

      if (graph.first("corpus") != null && graph.first("episode") != null) {
        // files to delete...
        File corpusDir = new File(getFiles(), graph.first("corpus").getLabel());
        File episodeDir = new File(corpusDir, graph.first("episode").getLabel());
         
        // transcript
        File trs = new File(episodeDir, "trs");
        File transcript = new File(trs, graph.getId());
        if (transcript.exists()) {
          if (!transcript.delete()) {
            System.err.println("Could not delete " + transcript.getPath());
          }
        }
      } // corpus/episode correctly set
	 
      // delete records from the database
	    
      // for each layer
      PreparedStatement selectLayers = getConnection().prepareStatement(
        "SELECT layer_id, scope, short_description, type FROM layer WHERE layer_id >= 0"
        +" ORDER BY layer_id DESC");
      ResultSet rsLayers = selectLayers.executeQuery();
      PreparedStatement deleteLayerData = getConnection().prepareStatement(
        "DELETE FROM annotation_layer_? WHERE ag_id = ?");
      deleteLayerData.setInt(2, iAgId);
      File annotationDataDir = null;
      while (rsLayers.next()) {
        
        // if it's a MIME type layer
        
        if (rsLayers.getString("type").indexOf('/') > 0) {
          // delete all data files before we delete the annotations
          Annotation[] annotations = getAnnotations(
            graph.getId(), rsLayers.getString("short_description"), null, null, null);
          File layerDir = null;
          for (Annotation annotation : annotations) {
            File file = annotationDataFile(annotation, graph, rsLayers.getString("type"));
            if (file != null && file.exists()) {
              file.delete();
              if (layerDir == null) {
                layerDir = file.getParentFile();
                if (annotationDataDir == null) {
                  annotationDataDir = layerDir.getParentFile();
                }
              }
            }
          } // next annotation
          if (layerDir != null) layerDir.delete();
        } // MIME type layer

        // delete annotation rows
        int iLayerId = rsLayers.getInt("layer_id");
        deleteLayerData.setInt(1, iLayerId);
        deleteLayerData.executeUpdate();
      } // next layer
      deleteLayerData.close();
      rsLayers.close();
      selectLayers.close();
      if (annotationDataDir != null) annotationDataDir.delete();
	 
      PreparedStatement deleteAnchors = getConnection().prepareStatement(
        "DELETE FROM anchor WHERE ag_id = ?");
      deleteAnchors.setInt(1, iAgId);
      deleteAnchors.executeUpdate();
      deleteAnchors.close();
	    
      PreparedStatement deleteSpeakers = getConnection().prepareStatement(
        "DELETE FROM transcript_speaker WHERE ag_id = ?");
      deleteSpeakers.setInt(1, iAgId);
      deleteSpeakers.executeUpdate();
      deleteSpeakers.close();
      PreparedStatement deleteAttributes = getConnection().prepareStatement(
        "DELETE FROM annotation_transcript WHERE ag_id = ?");
      deleteAttributes.setInt(1, iAgId);
      deleteAttributes.executeUpdate();
      deleteAttributes.close();
      PreparedStatement deleteTranscript = getConnection().prepareStatement(
        "DELETE FROM transcript WHERE ag_id = ?");
      deleteTranscript.setInt(1, iAgId);
      deleteTranscript.executeUpdate();
      deleteTranscript.close();
    } catch(SQLException exception) {
      System.err.println("SQL error: " + exception);
      exception.printStackTrace(System.err);
      throw new StoreException("Invalid query."); // TODO i18n
    }
  }
   
  /**
   * Deletes the given participant, and all associated meta-data.
   * @param id The ID participant to delete.
   * @throws StoreException If an error prevents the transcript from being saved.
   * @throws PermissionException If saving the transcript is not permitted.
   * @throws GraphNotFoundException If the transcript doesn't exist.
   */
  public void deleteParticipant(String id)
    throws StoreException, PermissionException, GraphNotFoundException {
    requireEdit();
    if (id == null) {
      throw new StoreException("No participant specified"); // TODO i18n
    } else {
      try {
        PreparedStatement sql = connection.prepareStatement(
          "SELECT speaker.speaker_number, COUNT(ag_id) AS transcript_count"
          +" FROM speaker"
          +" LEFT OUTER JOIN transcript_speaker"
          +" ON transcript_speaker.speaker_number = speaker.speaker_number"
          +" WHERE speaker.name = ?"
          +" GROUP BY speaker.speaker_number");
        sql.setString(1, id);
        if (id.startsWith("m_-2_")) { // we've been given an annotation ID
          String speaker_number = id.substring("m_-2_".length());
          sql.close();
          sql = connection.prepareStatement(
            "SELECT speaker.speaker_number, COUNT(ag_id) AS transcript_count"
            +" FROM speaker"
            +" LEFT OUTER JOIN transcript_speaker"
            +" ON transcript_speaker.speaker_number = speaker.speaker_number"
            +" WHERE speaker.speaker_number = ?"
            +" GROUP BY speaker.speaker_number");
          sql.setString(1, speaker_number);
        }
        ResultSet rs = sql.executeQuery();
        try {
          if (!rs.next()) {
            throw new StoreException("Invalid participant ID: " + id); // TODO i18n
          } else {
            int speakerNumber = rs.getInt("speaker_number");
            int transcriptCount = rs.getInt("transcript_count");
            if (transcriptCount > 0) {
              throw new StoreException( // TODO need a new exception so that Store cane return 200 instead of 400
                id + " is still in " + transcriptCount
                + " transcript" + (transcriptCount == 1?"":"s")); // TODO i18n
            } else {
              PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM annotation_participant WHERE speaker_number = ?");
              delete.setInt(1, speakerNumber);
              delete.executeUpdate();
              delete.close();
                     
              delete = connection.prepareStatement(
                "DELETE FROM speaker_corpus WHERE speaker_number = ?");
              delete.setInt(1, speakerNumber);
              delete.executeUpdate();
              delete.close();
                     
              delete = connection.prepareStatement(
                "DELETE FROM speaker WHERE speaker_number = ?");
              delete.setInt(1, speakerNumber);
              delete.executeUpdate();
              delete.close();
            }
          }
        } finally {
          rs.close();
          sql.close();
        }
      } catch(SQLException exception) {
      System.err.println("SQL error: " + exception);
      exception.printStackTrace(System.err);
      throw new StoreException("Invalid query."); // TODO i18n
      }
    }
  }
   
  /**
   * Gets the value of a system attribute.
   * @param name Attribute name.
   * @return The value of the system attribute, or null if there is no value.
   * @throws SQLException
   */
  public String getSystemAttribute(String name) throws SQLException {
    String value = null;
    PreparedStatement sql = getConnection().prepareStatement(
      "SELECT value FROM system_attribute WHERE name = ?");
    sql.setString(1, name);
    try {
      ResultSet rs = sql.executeQuery();
      try {
        if (rs.next()) value = rs.getString("value");
      } finally {
        rs.close();
      }
    } finally {
      sql.close();
    }
    return value;
  } // end of getSystemAttribute()

  /**
   * Root directory for serializers.
   * @see #getSerializersDirectory()
   * @see #setSerializersDirectory(File)
   */
  protected File serializersDirectory;
  /**
   * Getter for {@link #serializersDirectory}: Root directory for serializers.
   * @return Root directory for serializers.
   */
  public File getSerializersDirectory() { 
    if (serializersDirectory == null) {
      if (files != null) {
        try {
          setSerializersDirectory(new File(files.getParentFile(), "converters"));
        } catch(Exception exception) {}
      }
    }
    return serializersDirectory; 
  }
  /**
   * Setter for {@link #serializersDirectory}: Root directory for serializers.
   * @param newSerializersDirectory Root directory for serializers.
   */
  public SqlGraphStore setSerializersDirectory(File newSerializersDirectory) {
    serializersDirectory = newSerializersDirectory; return this;
  }
   
  /**
   * Registered deserializers, keyed by MIME type.
   * @see #getDeserializersByMimeType()
   * @see #setDeserializersByMimeType(HashMap)
   */
  protected HashMap<String,GraphDeserializer> deserializersByMimeType
  = new HashMap<String,GraphDeserializer>();
  /**
   * Getter for {@link #deserializersByMimeType}: Registered deserializers, keyed by MIME type.
   * @return Registered deserializers, keyed by MIME type.
   */
  public HashMap<String,GraphDeserializer> getDeserializersByMimeType() {
    return deserializersByMimeType;
  }
  /**
   * Setter for {@link #deserializersByMimeType}: Registered deserializers, keyed by MIME type.
   * @param newDeserializersByMimeType Registered deserializers, keyed by MIME type.
   */
  public SqlGraphStore setDeserializersByMimeType(
    HashMap<String,GraphDeserializer> newDeserializersByMimeType) {
    deserializersByMimeType = newDeserializersByMimeType; return this;
  }
      
  /**
   * Registered deserializers, keyed by file suffix (extension).
   * @see #getDeserializersBySuffix()
   * @see #setDeserializersBySuffix(HashMap)
   */
  protected HashMap<String,GraphDeserializer> deserializersBySuffix
  = new HashMap<String,GraphDeserializer>();
  /**
   * Getter for {@link #deserializersBySuffix}: Registered deserializers, keyed by file
   * suffix (extension).
   * @return Registered deserializers, keyed by file suffix (extension).
   */
  public HashMap<String,GraphDeserializer> getDeserializersBySuffix() {
    return deserializersBySuffix;
  }
  /**
   * Setter for {@link #deserializersBySuffix}: Registered deserializers, keyed by file
   * suffix (extension).
   * @param newDeserializersBySuffix Registered deserializers, keyed by file suffix (extension).
   */
  public SqlGraphStore setDeserializersBySuffix(
    HashMap<String,GraphDeserializer> newDeserializersBySuffix) {
    deserializersBySuffix = newDeserializersBySuffix; return this;
  }

  /**
   * Registered serializers, keyed by MIME type.
   * @see #getSerializersByMimeType()
   * @see #setSerializersByMimeType(HashMap)
   */
  protected HashMap<String,GraphSerializer> serializersByMimeType
  = new HashMap<String,GraphSerializer>();
  /**
   * Getter for {@link #serializersByMimeType}: Registered serializers, keyed by MIME type.
   * @return Registered serializers, keyed by MIME type.
   */
  public HashMap<String,GraphSerializer> getSerializersByMimeType() {
    return serializersByMimeType;
  }
  /**
   * Setter for {@link #serializersByMimeType}: Registered serializers, keyed by MIME type.
   * @param newSerializersByMimeType Registered serializers, keyed by MIME type.
   */
  public SqlGraphStore setSerializersByMimeType(
    HashMap<String,GraphSerializer> newSerializersByMimeType) {
    serializersByMimeType = newSerializersByMimeType; return this;
  }
   
  /**
   * Registered serializers, keyed by file suffix (extension).
   * @see #getSerializersBySuffix()
   * @see #setSerializersBySuffix(HashMap)
   */
  protected HashMap<String,GraphSerializer> serializersBySuffix
  = new HashMap<String,GraphSerializer>();
  /**
   * Getter for {@link #serializersBySuffix}: Registered serializers, keyed by file suffix
   * (extension).
   * @return Registered serializers, keyed by file suffix (extension).
   */
  public HashMap<String,GraphSerializer> getSerializersBySuffix() {
    return serializersBySuffix;
  }
  /**
   * Setter for {@link #serializersBySuffix}: Registered serializers, keyed by file suffix
   * (extension).
   * @param newSerializersBySuffix Registered serializers, keyed by file suffix (extension).
   */
  public SqlGraphStore setSerializersBySuffix(
    HashMap<String,GraphSerializer> newSerializersBySuffix) {
    serializersBySuffix = newSerializersBySuffix; return this;
  }

  /**
   * Loads the registered serializers/deserializers.
   * @throws SQLException On SQL error.
   */
  protected void loadSerializers()
    throws SQLException {
      
    PreparedStatement sqlRegisteredConverter = getConnection().prepareStatement(
      "SELECT DISTINCT class, jar, mimetype"
      +" FROM converter"
      +" WHERE type IN ('Serializer', 'Deserializer')"
      +" ORDER BY mimetype");
    ResultSet rs = sqlRegisteredConverter.executeQuery();
    while (rs.next()) {
      // get the jar file
      File file = new File(getSerializersDirectory(), rs.getString("jar"));
      try {
        JarFile jar = new JarFile(file);
	    
        // get an instance of the class
        URL[] url = new URL[] { file.toURI().toURL() };
        URLClassLoader classLoader = URLClassLoader.newInstance(
          url, getClass().getClassLoader());
        Object o = classLoader.loadClass(
          rs.getString("class")).getDeclaredConstructor().newInstance();
        if (o instanceof GraphDeserializer) {
          GraphDeserializer deserializer = (GraphDeserializer)o;
	       
          // register it in memory
          SerializationDescriptor descriptor = deserializer.getDescriptor();
          deserializersByMimeType.put(descriptor.getMimeType(), deserializer);
          for (String suffix : descriptor.getFileSuffixes()) {
            deserializersBySuffix.put(suffix, deserializer);
          } // next suffix
          if (getSerializersDirectory() != null) {
            File iconFile = IconHelper.EnsureIconFileExists(
              descriptor, getSerializersDirectory());
            if (getBaseUrl() != null && getBaseUrl().length() > 0) {
              descriptor.setIcon(
                new URL(getBaseUrl()+"/"+getSerializersDirectory().getName()
                        +"/"+iconFile.getName()));
            }
          }
        }
        if (o instanceof GraphSerializer) {
          GraphSerializer serializer = (GraphSerializer)o;
	       
          // register it in memory
          SerializationDescriptor descriptor = serializer.getDescriptor();
          serializersByMimeType.put(descriptor.getMimeType(), serializer);
          for (String suffix : descriptor.getFileSuffixes()) {
            serializersBySuffix.put(suffix, serializer);
          } // next suffix
          if (getSerializersDirectory() != null) {
            File iconFile = IconHelper.EnsureIconFileExists(
              descriptor, getSerializersDirectory());
            if (getBaseUrl() != null && getBaseUrl().length() > 0) {
              descriptor.setIcon(
                new URL(getBaseUrl()+"/"+getSerializersDirectory().getName()
                        +"/"+iconFile.getName()));
            }
          }
        }
      } catch(NoSuchMethodException x) {
        System.err.println(rs.getString("class") + ": " + x);
      } catch(InvocationTargetException x) {
        System.err.println(rs.getString("class") + ": " + x);
      } catch(ClassNotFoundException x) {
        System.err.println(rs.getString("class") + ": " + x);
      } catch(NoClassDefFoundError x) {
        System.err.println(rs.getString("class") + ": " + x);
      } catch(InstantiationException x) {
        System.err.println(rs.getString("class") + ": " + x);
      } catch(IllegalAccessException x) {
        System.err.println(rs.getString("class") + ": " + x);
      } catch(MalformedURLException x) {
        System.err.println(rs.getString("class") + ": " + x);
      } catch(IOException x) {
        System.err.println(rs.getString("class") + ": " + x);
      }
    }
    rs.close();
    sqlRegisteredConverter.close();
  } // end of loadSerializers()

  /**
   * Lists the descriptors of all registered serializers.
   * <p> Serializers are modules that export annotation structures as a specific file
   * format, e.g. Praat TextGrid, plain text, etc.
   * @return A list of the descriptors of all registered serializers.
   * @throws StoreException If an error prevents the operation from completing.
   * @throws PermissionException If the operation is not permitted.
   */
  public SerializationDescriptor[] getSerializerDescriptors()
    throws StoreException, PermissionException {
    SerializationDescriptor[] descriptors
      = new SerializationDescriptor[serializersByMimeType.size()];
    int i = 0;
    for (GraphSerializer serializer : serializersByMimeType.values()) {
      SerializationDescriptor descriptor = serializer.getDescriptor();
      try { // fix up the icon URL
        File iconFile = IconHelper.EnsureIconFileExists(descriptor, getSerializersDirectory());
        descriptor.setIcon(
          new URL(getBaseUrl()+"/"+getSerializersDirectory().getName()+"/"+iconFile.getName()));
      }
      catch(MalformedURLException exception) {}
      catch(IOException exception) {}
      descriptors[i++] = descriptor;
    }
    return descriptors;
  }
   
  /**
   * Lists the descriptors of all registered deserializers.
   * <p> Deserializers are modules that import annotation structures from a specific file
   * format, e.g. Praat TextGrid, plain text, etc.
   * @return A list of the descriptors of all registered deserializers.
   * @throws StoreException If an error prevents the descriptors from being listed.
   * @throws PermissionException If listing the deserializers is not permitted.
   */
  public SerializationDescriptor[] getDeserializerDescriptors()
    throws StoreException, PermissionException {
      
    SerializationDescriptor[] descriptors
      = new SerializationDescriptor[deserializersByMimeType.size()];
    int i = 0;
    for (GraphDeserializer deserializer : deserializersByMimeType.values()) {
      SerializationDescriptor descriptor = deserializer.getDescriptor();
      try { // fix up the icon URL
        File iconFile = IconHelper.EnsureIconFileExists(
          descriptor, getSerializersDirectory());
        descriptor.setIcon(
          new URL(
            getBaseUrl()+"/"+getSerializersDirectory().getName()+"/"+iconFile.getName()));
      }
      catch(MalformedURLException exception) {}
      catch(IOException exception) {}
      descriptors[i++] = descriptor;
    }
    return descriptors;
  }
      
  /**
   * Lists descriptors of all annotators that are installed.
   * @return A list of descriptors of all annotators that are installed.
   */
  public AnnotatorDescriptor[] getAnnotatorDescriptors() {
    TreeMap<String,AnnotatorDescriptor> descriptors = new TreeMap<String,AnnotatorDescriptor>();
    File dir = getAnnotatorDir();
    for (File jar : dir.listFiles(new FileFilter() {
        public boolean accept(File f) {
          return !f.isDirectory() && f.getName().endsWith(".jar");
        }})) {
      try {
        AnnotatorDescriptor descriptor = new AnnotatorDescriptor(jar);
        Annotator annotator = descriptor.getInstance();
            
        // give the annotator the resources it needs
        annotator.setSchema(getSchema());            
        if (annotator.getClass().isAnnotationPresent(UsesFileSystem.class)) {
          File annotatorDir = new File(dir, annotator.getAnnotatorId());
          if (!annotatorDir.exists()) annotatorDir.mkdir();
          annotator.setWorkingDirectory(annotatorDir);
        }            
        if (annotator.getClass().isAnnotationPresent(UsesRelationalDatabase.class)) {
          annotator.setRdbConnectionFactory(db);
        }
        if (!descriptors.containsKey(annotator.getAnnotatorId()) // no descriptor yet
            // or this one has a higher version than the one we already found
            || descriptor.compareTo(descriptors.get(annotator.getAnnotatorId())) > 0) {
          // add the descriptor to the list
          descriptors.put(annotator.getAnnotatorId(), descriptor);
        }
      } catch(Exception exception) {
        System.err.println("getAnnotatorDescriptors: " + exception);
        exception.printStackTrace(System.err);
      }
    } // next possible jar
    return descriptors.values().toArray(new AnnotatorDescriptor[0]);
  } // end of getAnnotatorDescriptors()
   
  /**
   * Gets an instance of the annotator with the given ID.
   * @param annotatorId
   * @return An instance of the given annotator, or null if there is no registered
   * annotator with the given ID. 
   */
  public Annotator getAnnotator(String annotatorId) {
    AnnotatorDescriptor descriptor = getAnnotatorDescriptor(annotatorId);
    if (descriptor != null) {
      return descriptor.getInstance();
    }
    return null;
  } // end of getAnnotator()

  /**
   * Gets a descriptor of the annotator with the given ID.
   * @param annotatorId The ID of the annotator.
   * @return A descriptor of the given annotator, or null if there is no registered
   * annotator with the given ID. 
   */
  public AnnotatorDescriptor getAnnotatorDescriptor(String annotatorId) {
    File dir = getAnnotatorDir();
    File[] possibleJars = dir.listFiles(new FileFilter() {
        public boolean accept(File f) { // select only files prefixed by the annotatorId
          return !f.isDirectory() && f.getName().startsWith(annotatorId + "-")
            && f.getName().endsWith(".jar");
        }});
    AnnotatorDescriptor finalDescriptor = null;
    for (File jar : possibleJars) {
      // System.out.println("jar " + jar.getName()); // TODO remove
      try {
        AnnotatorDescriptor descriptor = new AnnotatorDescriptor(jar);
        Annotator annotator = descriptor.getInstance();
        if (annotator.getAnnotatorId().equals(annotatorId)) {
          
          // give the annotator the resources it needs
          annotator.setSchema(getSchema());
               
          if (annotator.getClass().isAnnotationPresent(UsesFileSystem.class)) {
            File annotatorDir = new File(dir, annotator.getAnnotatorId());
            if (!annotatorDir.exists()) annotatorDir.mkdir();
            annotator.setWorkingDirectory(annotatorDir);
          }
               
          if (annotator.getClass().isAnnotationPresent(UsesRelationalDatabase.class)) {
            annotator.setRdbConnectionFactory(db);
          }
               
          if (annotator.getClass().isAnnotationPresent(UsesGraphStore.class)) {
            annotator.setStore(this);
          }
          
          if (finalDescriptor == null || finalDescriptor.compareTo(descriptor) < 0) {
            // System.out.println( // TODO remove
            //   (finalDescriptor==null?"Found":"Found newer")
            //   +" v. " + descriptor.getVersion() + " ("+jar.getName()+")");
            finalDescriptor = descriptor;
          // } else {
          //   System.out.println( // TODO remove
          //     "Older version ignored: " + descriptor.getVersion() + " ("+jar.getName()+")");
          }
        }
      } catch(Exception exception) {
        System.err.println("getAnnotatorDescriptor " + annotatorId + ": " + exception);
        exception.printStackTrace(System.err);
      }
    } // next possible jar
    return finalDescriptor;
  } // end of getAnnotatorDescriptor()

  /**
   * Supplies a list of automation tasks for the identified annotator.
   * @param annotatorId The ID of the annotator.
   * @return A map of task IDs to descriptions.
   * @throws StoreException If an error prevents the operation.
   * @throws PermissionException If the operation is not permitted.
   */
  public Map<String,String> getAnnotatorTasks(String annotatorId)
    throws StoreException, PermissionException {
    
    try {
      LinkedHashMap<String,String> tasks = new LinkedHashMap<String,String>();
      PreparedStatement sql = getConnection().prepareStatement(
        "SELECT task_id, description FROM automation"
        +" WHERE annotator_id = ? ORDER BY execution_order, automation_id");
      sql.setString(1, annotatorId);
      ResultSet rs = sql.executeQuery();
      try {
        while (rs.next()) {
          tasks.put(rs.getString("task_id"), rs.getString("description"));
        } // next task
      } finally {
        rs.close();
        sql.close();
      }
      return tasks;
    } catch (SQLException x) {
      throw new StoreException(x);
    }
  } // end of getAnnotatorTasks()   
   
  /**
   * Supplies the given task's parameter string.
   * @param taskId The ID of the automation task.
   * @return The task parameters, serialized as a string, or null if the
   * <var>taskId</var> does not exist. 
   * @throws StoreException If an error prevents the operation.
   * @throws PermissionException If the operation is not permitted.
   */
  public String getAnnotatorTaskParameters(String taskId)
    throws StoreException, PermissionException {
    
    try {
      PreparedStatement sql = getConnection().prepareStatement(
        "SELECT parameters FROM automation WHERE task_id = ?");
      sql.setString(1, taskId);
      ResultSet rs = sql.executeQuery();
      try {
        if (rs.next()) {
          return rs.getString("parameters");
        } else {
          throw new StoreException("No such task: " + taskId);
        }
      } finally {
        rs.close();
        sql.close();
      }
    } catch (SQLException x) {
      throw new StoreException(x);
    }
  } // end of getAnnotatorTaskParameters()
  
  /**
   * Lists descriptors of all transcribers that are installed.
   * @return A list of descriptors of all transcribers that are installed.
   */
  public AnnotatorDescriptor[] getTranscriberDescriptors() {
    // get all annotators
    return Arrays.stream(getAnnotatorDescriptors())
      // filter out the ones that aren't Transcribers
      .filter(a -> a.getInstance() instanceof Transcriber)
      .collect(Collectors.toList()).toArray(new AnnotatorDescriptor[0]);
  } // end of getTranscriberDescriptor()
   
  /**
   * Gets an instance of the transcriber with the given ID.
   * @param transcriberId
   * @return An instance of the given transcriber, or null if there is no registered
   * transcriber with the given ID. 
   */
  public Transcriber getTranscriber(String transcriberId) {
    AnnotatorDescriptor descriptor = getTranscriberDescriptor(transcriberId);
    if (descriptor != null) {
      return (Transcriber)descriptor.getInstance();
    }
    return null;
  } // end of getTranscriber()

  /**
   * Gets a descriptor of the transcriber with the given ID.
   * @param transcriberId
   * @return A descriptor of the given transcriber, or null if there is no registered
   * transcriber with the given ID. 
   */
  public AnnotatorDescriptor getTranscriberDescriptor(String transcriberId) {
    return getAnnotatorDescriptor(transcriberId);
  } // end of getTranscriberDescriptor()

  /**
   * Gets the deserializer for the given MIME type.
   * @param mimeType The MIME type.
   * @return The deserializer for the given MIME type, or null if none is registered.
   * @throws StoreException If an error prevents the operation from completing.
   * @throws PermissionException If the operation is not permitted.
   */
  public GraphDeserializer deserializerForMimeType(String mimeType)
    throws StoreException, PermissionException {
    try {
      return (GraphDeserializer)deserializersByMimeType.get(mimeType).getClass().getDeclaredConstructor().newInstance();
    } catch(NoSuchMethodException x) {
      return null;
    } catch(InvocationTargetException x) {
      return null;
    } catch(IllegalAccessException exception) {
      return null;
    } catch(InstantiationException exception) {
      return null;
    } catch(NullPointerException exception) {
      return null;
    }
  }

  /**
   * Gets the deserializer for the given file suffix (extension).
   * @param suffix The file extension.
   * @return The deserializer for the given suffix, or null if none is registered.
   * @throws StoreException If an error prevents the operation from completing.
   * @throws PermissionException If the operation is not permitted.
   */
  public GraphDeserializer deserializerForFilesSuffix(String suffix) throws StoreException, PermissionException {
    try {
      return (GraphDeserializer)deserializersBySuffix.get(suffix.toLowerCase()).getClass().getDeclaredConstructor().newInstance();
    } catch(InvocationTargetException exception) {
      return null;
    } catch(NoSuchMethodException exception) {
      return null;
    } catch(IllegalAccessException exception) {
      return null;
    } catch(InstantiationException exception) {
      return null;
    } catch(NullPointerException exception) {
      return null;
    }
  }

  /**
   * Gets the serializer for the given MIME type.
   * @param mimeType The MIME type.
   * @return The serializer for the given MIME type, or null if none is registered.
   * @throws StoreException If an error prevents the operation from completing.
   * @throws PermissionException If the operation is not permitted.
   */
  public GraphSerializer serializerForMimeType(String mimeType)
    throws StoreException, PermissionException {
    try {
      return (GraphSerializer)serializersByMimeType.get(mimeType).getClass().getDeclaredConstructor().newInstance();
    } catch(NoSuchMethodException exception) {
      return null;
    } catch(InvocationTargetException exception) {
      return null;
    } catch(IllegalAccessException exception) {
      return null;
    } catch(InstantiationException exception) {
      return null;
    } catch(NullPointerException exception) {
      return null;
    }
  }

  /**
   * Gets the serializer for the given file suffix (extension).
   * @param suffix The file extension.
   * @return The serializer for the given suffix, or null if none is registered.
   * @throws StoreException If an error prevents the operation from completing.
   * @throws PermissionException If the operation is not permitted.
   */
  public GraphSerializer serializerForFilesSuffix(String suffix)
    throws StoreException, PermissionException {      
    try {
      return (GraphSerializer)serializersBySuffix.get(suffix.toLowerCase()).getClass().getDeclaredConstructor().newInstance();
    } catch(InvocationTargetException exception) {
      return null; } catch(NoSuchMethodException exception) {
      return null;
    } catch(IllegalAccessException exception) {
      return null;
    } catch(InstantiationException exception) {
      return null;
    } catch(NullPointerException exception) {
      return null;
    }
  }

} // end of class SqlGraphStore
