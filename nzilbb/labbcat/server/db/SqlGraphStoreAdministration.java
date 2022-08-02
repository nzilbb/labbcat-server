//
// Copyright 2016-2021 New Zealand Institute of Language, Brain and Behaviour, 
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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarFile;
import nzilbb.ag.*;
import nzilbb.ag.serialize.*;
import nzilbb.ag.serialize.util.IconHelper;
import nzilbb.sql.ConnectionFactory;
import nzilbb.util.IO;

/**
 * Graph store administration that uses a relational database as its back end.
 * @author Robert Fromont robert@fromont.net.nz
 */
public class SqlGraphStoreAdministration
  extends SqlGraphStore
  implements GraphStoreAdministration {
   
  // Methods:
   
  /**
   * Constructor with connection.
   * @param baseUrl URL prefix for file access.
   * @param connection An opened database connection.
   * @param user ID of the user
   * @throws SQLException If an error occurs during connection or loading of configuraion.
   * @throws PermissionException If the store user doesn't have administrator privileges
   */
  @Deprecated
  public SqlGraphStoreAdministration(String baseUrl, Connection connection, String user)
    throws SQLException, PermissionException {
      
    super(baseUrl, connection, user);
    // if (getUser() != null && !getUserRoles().contains("admin")) throw new PermissionException();
  } // end of constructor

  /**
   * Constructor with connection.
   * @param baseUrl URL prefix for file access.
   * @param files Root directory for file structure.
   * @param connection An opened database connection.
   * @param user ID of the user
   * @throws SQLException If an error occurs during connection or loading of configuraion.
   * @throws PermissionException If the store user doesn't have administrator privileges
   */
  @Deprecated
  public SqlGraphStoreAdministration(String baseUrl, File files, Connection connection, String user)
    throws SQLException, PermissionException {
      
    super(baseUrl, files, connection, user);
    // if (getUser() != null && !getUserRoles().contains("admin")) throw new PermissionException();
  } // end of constructor

  /**
   * Constructor with connection.
   * @param baseUrl URL prefix for file access.
   * @param db A database connection factory.
   * @param user ID of the user
   * @throws SQLException If an error occurs during connection or loading of configuraion.
   * @throws PermissionException If the store user doesn't have administrator privileges
   */
  public SqlGraphStoreAdministration(String baseUrl, ConnectionFactory db, String user)
    throws SQLException, PermissionException {
      
    super(baseUrl, db, user);
    // if (getUser() != null && !getUserRoles().contains("admin")) throw new PermissionException();
  } // end of constructor

  /**
   * Constructor with connection.
   * @param baseUrl URL prefix for file access.
   * @param files Root directory for file structure.
   * @param db A database connection factory.
   * @param user ID of the user
   * @throws SQLException If an error occurs during connection or loading of configuraion.
   * @throws PermissionException If the store user doesn't have administrator privileges
   */
  public SqlGraphStoreAdministration(String baseUrl, File files, ConnectionFactory db, String user)
    throws SQLException, PermissionException {
      
    super(baseUrl, files, db, user);
    // if (getUser() != null && !getUserRoles().contains("admin")) throw new PermissionException();
  } // end of constructor

  /**
   * Constructor with connection parameters.
   * @param baseUrl URL prefix for file access.
   * @param files Root directory for file structure.
   * @param connectString The database connection string.
   * @param databaseUser The database username.
   * @param password The databa password.
   * @param storeUser ID of the user
   * @throws SQLException If an error occurs during connection or loading of configuraion.
   * @throws PermissionException If the store user doesn't have administrator privileges
   */
  public SqlGraphStoreAdministration(String baseUrl, File files, String connectString, String databaseUser, String password, String storeUser)
    throws SQLException, PermissionException {
      
    super(baseUrl, files, connectString, databaseUser, password, storeUser);
    // if (getUser() != null && !getUserRoles().contains("admin")) throw new PermissionException();
  }

   
  /**
   * Checks the user has the 'admin' role, and throws PermissionException if not.
   * @throws PermissionException If {@link SqlGraphStore#getUserRoles()} doesn't contain "admin".
   */
  private void requireAdmin() throws PermissionException {
    if (!getUserRoles().contains("admin")) throw new PermissionException("Use does not have admin role"); // TODO i18n
  } // end of requireAdmin()


  /**
   * Registers a transcript deserializer.
   * @param deserializer The deserializer to register.
   * @throws StoreException If an error prevents the deserializer from being registered.
   * @throws PermissionException If registering the deserializer is not permitted.
   */
  public void registerDeserializer(GraphDeserializer deserializer)
    throws StoreException, PermissionException {
    requireAdmin();
         
    deregisterDeserializer(deserializer);

    try {
      SerializationDescriptor descriptor = deserializer.getDescriptor();
      PreparedStatement sqlRegister = connection.prepareStatement(
        "INSERT INTO converter"
        +" (mimetype, type, class, version, name, jar)"
        +" VALUES (?,'Deserializer',?,?,?,?)");
      sqlRegister.setString(1, descriptor.getMimeType());
      sqlRegister.setString(2, deserializer.getClass().getName());
      sqlRegister.setString(3, descriptor.getVersion());
      sqlRegister.setString(4, descriptor.getName());
      sqlRegister.setString(5, IO.JarFileOfClass(deserializer.getClass()).getName());
      sqlRegister.executeUpdate();
      sqlRegister.close();
	 
      deserializersByMimeType.put(descriptor.getMimeType(), deserializer);
      for (String suffix : descriptor.getFileSuffixes()) {
        deserializersBySuffix.put(suffix, deserializer);
      } // next suffix

      try {
        File iconFile = IconHelper.EnsureIconFileExists(descriptor, getSerializersDirectory());
        descriptor.setIcon(
          new URL(getBaseUrl()+"/"+getSerializersDirectory().getName()+"/"+iconFile.getName()));
      }
      catch(MalformedURLException exception) {}
      catch(IOException exception) {}
    } catch(SQLException exception) {
      throw new StoreException(exception);
    }
  }

  /**
   * De-registers a transcript deserializer.
   * @param deserializer The deserializer to de-register.
   * @throws StoreException If an error prevents the deserializer from being deregistered.
   * @throws PermissionException If deregistering the deserializer is not permitted.
   */
  public void deregisterDeserializer(GraphDeserializer deserializer)
    throws StoreException, PermissionException {
    requireAdmin();
      
    try {
      SerializationDescriptor descriptor = deserializer.getDescriptor();
      deserializersByMimeType.remove(descriptor.getMimeType());
      for (String suffix : descriptor.getFileSuffixes()) {
        deserializersBySuffix.remove(suffix);
      } // next suffix

      PreparedStatement sqlDeregister = connection.prepareStatement(
        "DELETE FROM converter WHERE mimetype = ? AND type = 'Deserializer'");
      sqlDeregister.setString(1, descriptor.getMimeType());
      sqlDeregister.executeUpdate();
      sqlDeregister.close();
    } catch(SQLException exception) {
      throw new StoreException(exception);
    }
  }

  /**
   * Registers a transcript serializer.
   * @param serializer The serializer to register.
   * @throws StoreException If an error prevents the operation from completing.
   * @throws PermissionException If the operation is not permitted.
   */
  public void registerSerializer(GraphSerializer serializer)
    throws StoreException, PermissionException {
    requireAdmin();
      
    deregisterSerializer(serializer);

    try {
      SerializationDescriptor descriptor = serializer.getDescriptor();
      PreparedStatement sqlRegister = connection.prepareStatement(
        "INSERT INTO converter"
        +" (mimetype, type, class, version, name, jar)"
        +" VALUES (?,'Serializer',?,?,?,?)");
      sqlRegister.setString(1, descriptor.getMimeType());
      sqlRegister.setString(2, serializer.getClass().getName());
      sqlRegister.setString(3, descriptor.getVersion());
      sqlRegister.setString(4, descriptor.getName());
      sqlRegister.setString(5, IO.JarFileOfClass(serializer.getClass()).getName());
      sqlRegister.executeUpdate();
      sqlRegister.close();
	 
      serializersByMimeType.put(descriptor.getMimeType(), serializer);
      for (String suffix : descriptor.getFileSuffixes()) {
        serializersBySuffix.put(suffix, serializer);
      } // next suffix

      try {
        File iconFile = IconHelper.EnsureIconFileExists(descriptor, getSerializersDirectory());
        descriptor.setIcon(
          new URL(getBaseUrl()+"/"+getSerializersDirectory().getName()+"/"+iconFile.getName()));
      }
      catch(MalformedURLException exception) {}
      catch(IOException exception) {}
    } catch(SQLException exception) {
      throw new StoreException(exception);
    }
  }

  /**
   * De-registers a transcript serializer.
   * @param serializer The serializer to de-register.
   * @throws StoreException If an error prevents the operation from completing.
   * @throws PermissionException If the operation is not permitted.
   */
  public void deregisterSerializer(GraphSerializer serializer)
    throws StoreException, PermissionException {
    requireAdmin();
      
    try {
      SerializationDescriptor descriptor = serializer.getDescriptor();
      serializersByMimeType.remove(descriptor.getMimeType());
      for (String suffix : descriptor.getFileSuffixes()) {
        serializersBySuffix.remove(suffix);
      } // next suffix

      PreparedStatement sqlDeregister = connection.prepareStatement(
        "DELETE FROM converter WHERE mimetype = ? AND type = 'Serializer'");
      sqlDeregister.setString(1, descriptor.getMimeType());
      sqlDeregister.executeUpdate();
      sqlDeregister.close();
    } catch(SQLException exception) {
      throw new StoreException(exception);
    }
  }

  /**
   * Saves changes to a layer.
   * @param layer A modified layer definition.
   * @return The resulting layer definition.
   * @throws StoreException If an error prevents the operation.
   * @throws PermissionException If the operation is not permitted.
   */
  public Layer saveLayer(Layer layer) throws StoreException, PermissionException {
    requireAdmin();
    try {
      Layer oldVersion = getLayer(layer.getId());
      if (layer.getId().equals("transcript_type")) {
        // can only update validLabels

        HashSet<String> toDelete = new HashSet<String>(oldVersion.getValidLabels().keySet());
        toDelete.removeAll(layer.getValidLabels().keySet());
        if (toDelete.size() > 0) {
          // check missing options can be deleted
          PreparedStatement sql = getConnection().prepareStatement(
            "SELECT COUNT(*) FROM transcript_type"
            +" INNER JOIN transcript ON transcript_type.type_id = transcript.type_id"
            +" WHERE transcript_type = ?");
          try {
            for (String option : toDelete) {
              sql.setString(1, option);
              ResultSet rs = sql.executeQuery();
              rs.next();
              try {
                if (rs.getInt(1) > 0) {
                  throw new StoreException("Option still in use: " + option);
                }
              } finally {
                rs.close();
              }
            }
          } finally {
            sql.close();
          }
               
          // delete missing options
          sql = getConnection().prepareStatement(
            "DELETE FROM transcript_type WHERE transcript_type = ?");
          for (String option : toDelete) {
            sql.setString(1, option);
            sql.executeUpdate();
          }
          sql.close();
        }
            
        HashSet<String> toCreate = new HashSet<String>(layer.getValidLabels().keySet());
        toCreate.removeAll(oldVersion.getValidLabels().keySet());            
        if (toCreate.size() > 0) {
          // insert new options
          PreparedStatement sql = getConnection().prepareStatement(
            "SELECT COALESCE(MAX(type_id + 1), 1) FROM transcript_type");
          ResultSet rs = sql.executeQuery();
          rs.next();
          int type_id = rs.getInt(1);
          rs.close();
          sql.close();
          sql = getConnection().prepareStatement(
            "INSERT INTO transcript_type (type_id, transcript_type) VALUES (?,?)");
          for (String option : toCreate) {
            sql.setInt(1, type_id++);
            sql.setString(2, option);
            sql.executeUpdate();
          }
          sql.close();
        }

        // ensure schema is reloaded with new layer
        this.schema = null;

      } else if (oldVersion.containsKey("layer_id")
                 && (Integer)oldVersion.get("layer_id") >= 0) { // temporal layer
        int layer_id = (Integer)oldVersion.get("layer_id");
            
        // attribute-wise updates...

        // description
        if (!oldVersion.getDescription().equals(layer.getDescription())) {
          PreparedStatement sql = getConnection().prepareStatement(
            "UPDATE layer SET notes = ? WHERE layer_id = ?");
          sql.setString(1, layer.getDescription());
          sql.setInt(2, layer_id);
          sql.executeUpdate();
          sql.close();
        }

        // some attributes cannot be updated for system layers
        if (layer_id != SqlConstants.LAYER_TRANSCRIPTION
            && layer_id != SqlConstants.LAYER_UTTERANCE
            && layer_id != SqlConstants.LAYER_TURN) {

          // type (only) can be changed for segment layer
          if (!oldVersion.getType().equals(layer.getType())) {
            String subtype = "T"; // Constants.TYPE_STRING
            if (Constants.TYPE_NUMBER.equals(layer.getType())) {
              subtype = "N";  // TODO handle type = number/integer
            } else if (Constants.TYPE_IPA.equals(layer.getType())) {
              subtype = "D";
            } else if (Constants.TYPE_BOOLEAN.equals(layer.getType())) {
              subtype = "boolean";
            }
            PreparedStatement sql = getConnection().prepareStatement(
              "UPDATE layer SET type = ? WHERE layer_id = ?");
            sql.setString(1, subtype);
            sql.setInt(2, layer_id);
            sql.executeUpdate();
            sql.close();
          }               

          // no other attributes can be updated for the segment layer
          if (layer_id != SqlConstants.LAYER_SEGMENT) {
                  
            // alignment
            if (oldVersion.getAlignment() != layer.getAlignment()) {
              PreparedStatement sql = getConnection().prepareStatement(
                "UPDATE layer SET alignment = ? WHERE layer_id = ?");
              sql.setInt(1, layer.getAlignment());
              sql.setInt(2, layer_id);
              sql.executeUpdate();
              sql.close();
            }
                  
            // relationship flag
            if (oldVersion.getPeers() != layer.getPeers()
                || oldVersion.getPeersOverlap() != layer.getPeersOverlap()
                || oldVersion.getParentIncludes() != layer.getParentIncludes()
                || oldVersion.getSaturated() != layer.getSaturated()) {
              PreparedStatement sql = getConnection().prepareStatement(
                "UPDATE layer"
                +" SET peers = ?, peers_overlap = ?, parent_includes = ?, saturated = ?"
                +" WHERE layer_id = ?");
              sql.setInt(1, layer.getPeers()?1:0);
              sql.setInt(2, layer.getPeersOverlap()?1:0);
              sql.setInt(3, layer.getParentIncludes()?1:0);
              sql.setInt(4, layer.getSaturated()?1:0);
              sql.setInt(5, layer_id);
              sql.executeUpdate();
              sql.close();
            }
                  
            // category
            if (!(""+oldVersion.getCategory()).equals(layer.getCategory())) {
              PreparedStatement sql = getConnection().prepareStatement(
                "UPDATE layer"
                +" SET project_id = (SELECT project_id FROM project WHERE project = ?)"
                +" WHERE layer_id = ?");
              sql.setString(1, layer.getCategory());
              sql.setInt(2, layer_id);
              sql.executeUpdate();
              sql.close();
            }
                  
            // LaBB-CAT extensions:
                  
            // enabled
            if (layer.containsKey("enabled")
                && !oldVersion.get("enabled").equals(layer.get("enabled"))) {
              PreparedStatement sql = getConnection().prepareStatement(
                "UPDATE layer SET enabled = ? WHERE layer_id = ?");
              sql.setString(1, layer.get("enabled").toString());
              sql.setInt(2, layer_id);
              sql.executeUpdate();
              sql.close();
            }
          } // non-segment layer
               
        } // non-system layer

        // validLabels
        HashSet<String> toDelete = new HashSet<String>(oldVersion.getValidLabels().keySet());
        toDelete.removeAll(layer.getValidLabels().keySet());
        if (toDelete.size() > 0) {
          // delete missing options
          PreparedStatement sql = getConnection().prepareStatement(
            "DELETE FROM label_option WHERE layer_id = ? AND value = ?");
          sql.setInt(1, layer_id);
          for (String option : toDelete) {
            sql.setString(2, option);
            sql.executeUpdate();
          }
          sql.close();
        }
            
        HashSet<String> toCreate = new HashSet<String>(layer.getValidLabels().keySet());
        toCreate.removeAll(oldVersion.getValidLabels().keySet());            
        if (toCreate.size() > 0) {
          // insert new options
          PreparedStatement sql = getConnection().prepareStatement(
            "SELECT COALESCE(MAX(display_order + 1), 1) FROM label_option WHERE layer_id = ?");
          sql.setInt(1, layer_id);
          ResultSet rs = sql.executeQuery();
          rs.next();
          int order = rs.getInt(1);
          rs.close();
          sql.close();
          sql = getConnection().prepareStatement(
            "INSERT INTO label_option (layer_id, value, description, display_order)"
            +" VALUES (?,?,?,?)");
          sql.setInt(1, layer_id);
          for (String option : toCreate) {
            sql.setString(2, option);
            sql.setString(3, layer.getValidLabels().get(option));
            sql.setInt(4, order++);
            sql.executeUpdate();
          }
          sql.close();
        }
      } else {
        throw new StoreException("Updating layer " + layer.getId() + " not yet implemented"); // TODO
      }
      // return new definition
      return getLayer(layer.getId());

    } catch (SQLException x) {
      throw new StoreException(x);
    }
  }
  
  /**
   * Adds a new layer.
   * @param layer A new layer definition.
   * @return The resulting layer definition.
   * @throws StoreException If an error prevents the operation.
   * @throws PermissionException If the operation is not permitted.
   */
  public Layer newLayer(Layer layer) throws StoreException, PermissionException {
    requireAdmin();

    // validate ID
    if (layer.getId() == null || layer.getId().trim().length() == 0) {
      throw new StoreException("No ID specified");
    }
    layer.setId(layer.getId().trim());
    Layer existingLayer = null;
    try {
      existingLayer = getLayer(layer.getId());
    } catch(StoreException exception) {
    }
    if (existingLayer != null) {
      throw new StoreException("Layer already exists: " + layer.getId());
    }
      
    String subtype = "T"; // Constants.TYPE_STRING
    if (Constants.TYPE_NUMBER.equals(layer.getType())) {
      subtype = "N";  // TODO handle type = number/integer
    } else if (Constants.TYPE_IPA.equals(layer.getType())) {
      subtype = "D";
    } else if (Constants.TYPE_BOOLEAN.equals(layer.getType())) {
      subtype = "boolean";
    }
         
    // check parent
    if (layer.getParentId() == null) {
      throw new StoreException("No parentId specified: " + layer.getId());
    }
    Schema schema = getSchema();
    Layer parent = schema.getLayer(layer.getParentId());
    if (parent == null) {
      throw new StoreException(
        "Invalid parent ("+layer.getParentId()+") for: " + layer.getId());
    }

    // TODO add support for participant/transcript attributes

    try {
      int project_id = -1;
      PreparedStatement sql = getConnection().prepareStatement(
        "SELECT project_id FROM project WHERE project = ?");
      if (layer.getCategory() != null) {
        sql.setString(1, layer.getCategory());
        ResultSet rs = sql.executeQuery();
        if (rs.next()) project_id = rs.getInt(1);
        rs.close();
      }
      sql.close();

      sql = getConnection().prepareStatement(
        "SELECT MAX(layer_id) + 1 FROM layer");
      ResultSet rs = sql.executeQuery();
      rs.next();
      int layer_id = rs.getInt(1);
      rs.close();
      sql.close();

      // We might create an automation task after adding the layer...
      String annotatorId = null;
      String taskParameters = null;

      sql = getConnection().prepareStatement(
        "INSERT INTO layer"
        +" (layer_id, short_description, description, notes, alignment," // TODO remove description
        +" peers, peers_overlap, parent_includes, saturated, type,"
        +" layer_manager_id, enabled, project_id, parent_id, scope, extra)"
        +" VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
      sql.setInt(1, layer_id);
      sql.setString(2, layer.getId());
      sql.setString(3, layer.getId()); // 'description', which is deprecated
      sql.setString(4, layer.getDescription()); // 'notes'
      sql.setInt(5, layer.getAlignment());
      sql.setInt(6, layer.getPeers()?1:0);
      sql.setInt(7, layer.getPeersOverlap()?1:0);
      sql.setInt(8, layer.getParentIncludes()?1:0);
      sql.setInt(9, layer.getSaturated()?1:0);
      sql.setString(10, subtype);
      if (layer.containsKey("layer_manager_id") && layer.get("layer_manager_id") != null
          && layer.get("layer_manager_id").toString().length() > 0) {
        sql.setString(11, layer.get("layer_manager_id").toString());
        if (layer.containsKey("extra") && layer.get("extra") != null
            && layer.get("extra").toString().length() > 0) {
          // if the layer manager is a known subclass of
          // nz.ac.canterbury.ling.layermanager.AnnotatorWrapperManager
          // then create an automation instead of saving to the 'extra' field
          String layerManagerId = layer.get("layer_manager_id").toString();
          if (layerManagerId.equals("CharacterMapper")) {
            annotatorId = "PhonemeTranscoder";
          } else if (layerManagerId.equals("CMUdict")) {
            annotatorId = "CMUDictionaryTagger";
          } else if (layerManagerId.equals("FlatFileDictionary")) {
            annotatorId = "FlatLexiconTagger";
          } else if (layerManagerId.equals("HTK")) {
            annotatorId = "HTKAligner";
          } else if (layerManagerId.equals("labelmapper")) {
            annotatorId = "LabelMapper";
          } else if (layerManagerId.equals("MFA")) {
            annotatorId = "MFA";
          } else if (layerManagerId.equals("BAS")) {
            annotatorId = "BASAnnotator";
          } else if (layerManagerId.equals("MorTagger")) {
            annotatorId = "MorTagger";
          } else if (layerManagerId.equals("PatternMatcher")) {
            annotatorId = "PatternTagger";
          } else if (layerManagerId.equals("PorterStemmer")) {
            annotatorId = "PorterStemmer";
          } else if (layerManagerId.equals("es-phon")) {
            annotatorId = "SpanishPhonologyTagger";
          } else if (layerManagerId.equals("StanfordPosTagger")) {
            annotatorId = "StanfordPosTagger";
          }
          if (annotatorId != null) { // annotator
            sql.setNull(16, java.sql.Types.VARCHAR); // extra
            // we will add a task with this configuration afterwards
            taskParameters = layer.get("extra").toString();
          } else {
            sql.setString(16, layer.get("extra").toString()); // extra
          }
        } else {
          sql.setNull(16, java.sql.Types.VARCHAR); // extra
        }
      } else {
        sql.setNull(11, java.sql.Types.VARCHAR);
        sql.setNull(16, java.sql.Types.VARCHAR); // extra
      }
      if (layer.containsKey("enabled")
          && layer.get("enabled").toString().length() > 0) {
        sql.setString(12, layer.get("enabled").toString());
      } else {
        sql.setNull(12, java.sql.Types.VARCHAR);
      }
      if (project_id >= 0) {
        sql.setInt(13, project_id);
      } else {
        sql.setNull(13, java.sql.Types.INTEGER);
      }
            
      try {
        if (parent.getId().equals(schema.getWordLayerId())) { // word layer
                  
          sql.setInt(14, (Integer)parent.get("layer_id"));
          sql.setString(15, SqlConstants.SCOPE_WORD.toUpperCase());
          sql.executeUpdate();
          sql.close();
                  
          // create annotation table
          sql = getConnection().prepareStatement(
            "CREATE TABLE annotation_layer_"+layer_id+" ("
            + " annotation_id INTEGER UNSIGNED NOT NULL AUTO_INCREMENT,"
            + " ag_id INTEGER UNSIGNED NOT NULL"
            + " COMMENT 'Annotation graph (transcript) ID',"
            + " label VARCHAR(247) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci"
            + " NOT NULL DEFAULT ''" 
            + " COMMENT 'Text annotation for the word'," 
            + " label_status TINYINT UNSIGNED DEFAULT 0"
            + " COMMENT 'How reliable the label is - 50: automated, 100: manually labelled', "
            + " data MEDIUMBLOB" 
            + " COMMENT 'Binary data attached to annotation'," 
            + " start_anchor_id INTEGER UNSIGNED NOT NULL"
            + " COMMENT 'Anchor for start time',"
            + " end_anchor_id INTEGER UNSIGNED NOT NULL"
            + " COMMENT 'Anchor for end time',"
            + " turn_annotation_id INTEGER UNSIGNED NOT NULL" 
            + " COMMENT 'References annotation_layer_11.annotation_id',"
            + " ordinal_in_turn INTEGER" 
            + " COMMENT 'Copy of annotation_layer_0.ordinal_in_turn',"  
            + " word_annotation_id INTEGER UNSIGNED" 
            + " COMMENT 'References annotation_layer_0.annotation_id'," 
            + " parent_id INTEGER UNSIGNED NULL"
            + " COMMENT 'Parent annotation_id',"
            + " ordinal INTEGER NULL"
            + " COMMENT 'The serial position of this annotation among its siblings on the same layer',"
            + " annotated_by VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL"
            + " COMMENT 'Name of person/system who created/changed the anchor',"
            + " annotated_when DATETIME NULL"
            + " COMMENT 'Date/time of the creation or last change of the anchor',"
            + " PRIMARY KEY (annotation_id),"
            + " INDEX IDX_LABEL(label, ag_id, turn_annotation_id), "
            + " INDEX IDX_ANCHOR(start_anchor_id, end_anchor_id), "
            + " INDEX IDX_END_ANCHOR(end_anchor_id),"
            + " INDEX IDX_WORD(word_annotation_id),"
            + " INDEX IDX_TURN(turn_annotation_id, ordinal_in_turn),"
            +"  INDEX IDX_AG(ag_id, annotation_id)"
            + " ) ENGINE=MyISAM;");
          sql.executeUpdate();
          sql.close();
                  
        } else if (parent.getId().equals("segment")) { // segment layer
                  
          sql.setInt(14, (Integer)parent.get("layer_id"));
          sql.setString(15, SqlConstants.SCOPE_SEGMENT.toUpperCase());
          sql.executeUpdate();
          sql.close();
                  
          // create annotation table
          sql = getConnection().prepareStatement(
            "CREATE TABLE annotation_layer_"+layer_id+" ("
            + " annotation_id INTEGER UNSIGNED NOT NULL AUTO_INCREMENT,"
            + " ag_id INTEGER UNSIGNED NOT NULL"
            + " COMMENT 'Annotation graph (transcript) ID',"
            + " label VARCHAR(247) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci"
            + " NOT NULL DEFAULT ''" 
            + " COMMENT 'Text annotation for the segment'," 
            + " label_status TINYINT UNSIGNED DEFAULT 0"
            + " COMMENT 'How reliable the label is - 50: automated, 100: manually labelled', "
            + " data MEDIUMBLOB" 
            + " COMMENT 'Binary data attached to annotation'," 
            + " start_anchor_id INTEGER UNSIGNED NOT NULL"
            + " COMMENT 'Anchor for start time',"
            + " end_anchor_id INTEGER UNSIGNED NOT NULL"
            + " COMMENT 'Anchor for end time',"
            + " turn_annotation_id INTEGER UNSIGNED NOT NULL" 
            + " COMMENT 'References annotation_layer_11.annotation_id',"
            + " ordinal_in_turn INTEGER" 
            + " COMMENT 'Copy of annotation_layer_0.ordinal_in_turn',"  
            + " word_annotation_id INTEGER UNSIGNED" 
            + " COMMENT 'References annotation_layer_0.annotation_id'," 
            + " ordinal_in_word INTEGER" 
            + " COMMENT 'Copy of annotation_layer_1.ordinal_in_word',"
            + " segment_annotation_id INTEGER UNSIGNED NOT NULL" 
            + " COMMENT 'References annotation_layer_1.annotation_id',"
            + " parent_id INTEGER UNSIGNED NULL"
            + " COMMENT 'Parent annotation_id',"
            + " ordinal INTEGER NULL"
            + " COMMENT 'The serial position of this annotation among its siblings on the same layer',"
            + " annotated_by VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL"
            + " COMMENT 'Name of person/system who created/changed the anchor',"
            + " annotated_when DATETIME NULL"
            + " COMMENT 'Date/time of the creation or last change of the anchor',"
            + " PRIMARY KEY (annotation_id),"
            + " INDEX IDX_LABEL(label, word_annotation_id, ag_id, turn_annotation_id), "
            + " INDEX IDX_ANCHOR(start_anchor_id, end_anchor_id), "
            + " INDEX IDX_END_ANCHOR(end_anchor_id),"
            + " INDEX IDX_WORD(word_annotation_id, ordinal_in_word),"
            + " INDEX IDX_SEGMENT(segment_annotation_id),"
            + " INDEX IDX_TURN(turn_annotation_id, ordinal_in_turn, word_annotation_id, ordinal_in_word),"
            +"  INDEX IDX_AG(ag_id, annotation_id)"
            + " ) ENGINE=MyISAM;");
          sql.executeUpdate();
          sql.close();
                  
        } else if (parent.getId().equals(schema.getTurnLayerId())) { // phrase layer
                  
          sql.setInt(14, (Integer)parent.get("layer_id"));
          sql.setString(15, SqlConstants.SCOPE_META.toUpperCase());
          sql.executeUpdate();
          sql.close();
                  
          // create annotation table
          sql = getConnection().prepareStatement(
            "CREATE TABLE annotation_layer_"+layer_id+" ("
            + " annotation_id INTEGER UNSIGNED NOT NULL AUTO_INCREMENT,"
            + " ag_id INTEGER UNSIGNED NOT NULL"
            + " COMMENT 'Annotation graph (transcript) ID',"
            + " label VARCHAR(247) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci"
            + " NOT NULL DEFAULT ''" 
            + " COMMENT 'Speaker number (from speaker.speaker_number)'," 
            + " label_status TINYINT UNSIGNED DEFAULT 0"
            + " COMMENT 'How reliable the label is - 50: automated, 100: manually labelled', "
            + " data MEDIUMBLOB" 
            + " COMMENT 'Binary data attached to annotation'," 
            + " start_anchor_id INTEGER UNSIGNED NOT NULL"
            + " COMMENT 'Anchor for start time'," 
            + " end_anchor_id INTEGER UNSIGNED NOT NULL"
            + " COMMENT 'Anchor for end time'," 
            + " turn_annotation_id INTEGER UNSIGNED" 
            + " COMMENT 'References annotation_layer_11.annotation_id'," 
            + " parent_id INTEGER UNSIGNED NULL"
            + " COMMENT 'Parent annotation_id',"
            + " ordinal INTEGER NULL"
            + " COMMENT 'The serial position of this annotation among its siblings on the same layer',"
            + " annotated_by VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL"
            + " COMMENT 'Name of person/system who created/changed the anchor',"
            + " annotated_when DATETIME NULL"
            + " COMMENT 'Date/time of the creation or last change of the anchor',"
            + " PRIMARY KEY (annotation_id),"
            + " INDEX IDX_LABEL(label, ag_id, start_anchor_id), "
            + " INDEX IDX_ANCHOR(start_anchor_id, end_anchor_id),"
            + " INDEX IDX_END_ANCHOR(end_anchor_id),"
            + " INDEX IDX_TURN(turn_annotation_id, annotation_id),"
            +"  INDEX IDX_AG(ag_id, annotation_id)"
            + " ) ENGINE=MyISAM;");
          sql.executeUpdate();
          sql.close();
                  
          // create transcript speaker table (some layer managers still need this)
          sql = getConnection().prepareStatement(
            "CREATE TABLE transcript_speaker_layer_"+layer_id+" ("
            +" speaker_number INT NOT NULL,"
            +" ag_id INT NOT NULL,"
            +" variant int(10) unsigned NOT NULL default 0,"
            +" representation varchar(100) NOT NULL default '''',"
            +" number DOUBLE unsigned NOT NULL default 0,"
            +" PRIMARY KEY  (speaker_number,ag_id,variant),"
            +" KEY IDX_REPRESENTATION (representation)"
            +" ) ENGINE=MyISAM;");
          sql.executeUpdate();
          sql.close();
                  
          // create corpus table (some layer managers still need this)
          sql = getConnection().prepareStatement(
            "CREATE TABLE corpus_layer_"+layer_id+" ("
            +" corpus_id int(11) NOT NULL default 0,"
            +" variant int(10) unsigned NOT NULL default 0,"
            +" representation varchar(100) NOT NULL default '''',"
            +" number DOUBLE unsigned NOT NULL default 0,"
            +" PRIMARY KEY  (corpus_id, variant),"
            +" KEY IDX_REPRESENTATION (representation)"
            +" ) ENGINE=MyISAM;");
          sql.executeUpdate();
          sql.close();
                  
        } else if (parent.getId().equals(schema.getRoot().getId())) { // span layer
          if (layer.getAlignment() != Constants.ALIGNMENT_NONE) {
                     
            sql.setInt(14, SqlConstants.LAYER_GRAPH);
            sql.setString(15, SqlConstants.SCOPE_FREEFORM.toUpperCase());
            sql.executeUpdate();
            sql.close();
                     
            // create annotation table
            sql = getConnection().prepareStatement(
              "CREATE TABLE annotation_layer_"+layer_id+" ("
              + " annotation_id INTEGER UNSIGNED NOT NULL AUTO_INCREMENT,"
              + " ag_id INTEGER UNSIGNED NOT NULL"
              + " COMMENT 'Annotation graph (transcript) ID',"
              + " label VARCHAR(247) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci"
              + " NOT NULL DEFAULT ''"
              + " COMMENT 'Text annotation'," 
              + " label_status TINYINT UNSIGNED DEFAULT 0"
              + " COMMENT 'How reliable the label is - 50: automated, 100: manually labelled', "
              + " data MEDIUMBLOB" 
              + " COMMENT 'Binary data attached to annotation'," 
              + " start_anchor_id INTEGER UNSIGNED NOT NULL"
              + " COMMENT 'Anchor for start time'," 
              + " end_anchor_id INTEGER UNSIGNED NOT NULL"
              + " COMMENT 'Anchor for end time'," 
              + " parent_id INTEGER UNSIGNED NULL"
              + " COMMENT 'Parent annotation_id',"
              + " ordinal INTEGER NULL"
              + " COMMENT 'The serial position of this annotation among its siblings on the same layer',"
              + " annotated_by VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL"
              + " COMMENT 'Name of person/system who created/changed the anchor',"
              + " annotated_when DATETIME NULL"
              + " COMMENT 'Date/time of the creation or last change of the anchor',"
              + " PRIMARY KEY (annotation_id),"
              + " INDEX IDX_LABEL(label, ag_id, start_anchor_id), "
              + " INDEX IDX_ANCHOR(start_anchor_id, end_anchor_id),"
              + " INDEX IDX_END_ANCHOR(end_anchor_id),"
              +"  INDEX IDX_AG(ag_id, annotation_id)"
              + " ) ENGINE=MyISAM;");
            sql.executeUpdate();
            sql.close();

          } else { // layer.getAlignment() == Constants.ALIGNMENT_NONE TODO transcript attributes
            throw new StoreException("Span layers must be aligned");
          }
        } else { // TODO support participant attributes
          // TODO support episode layers
          throw new StoreException(
            "Invalid parentId "+layer.getParentId()
            +" - must be one of: " + schema.getWordLayerId() + ", " + schema.getTurnLayerId()
            + ", " + schema.getRoot().getId());
        }

        if (annotatorId != null && taskParameters != null) {
          // create an automation task named after the layer
          try {
            newAnnotatorTask(annotatorId, layer.getId(), layer.getDescription());
            saveAnnotatorTaskParameters(layer.getId(), taskParameters);
          } catch(Exception exception) {
            System.err.println(
              "SqlGraphStoreAdministration.newLayer(" + layer + "): " + exception);
          }
        }
      } finally {
        sql.close();
      }

      // validLabels
      int order = 1;
      sql = getConnection().prepareStatement(
        "INSERT INTO label_option (layer_id, value, description, display_order)"
        +" VALUES (?,?,?,?)");
      try {
        sql.setInt(1, layer_id);
        for (String option : layer.getValidLabels().keySet()) {
          sql.setString(2, option);
          sql.setString(3, layer.getValidLabels().get(option));
          sql.setInt(4, order++);
          sql.executeUpdate();
        }
      } finally {
        sql.close();
      }
            
      // ensure schema is reloaded with new layer
      this.schema = null;      
            
      return getLayer(layer.getId());
    } catch (SQLException sqlX) {
      System.err.println("SqlGraphStoreAdministration.newLayer(" + layer + "): " + sqlX);
      sqlX.printStackTrace(System.err);
      throw new StoreException(sqlX);
    } catch (Throwable t) {
      System.err.println("SqlGraphStoreAdministration.newLayer(" + layer + "): " + t);
      t.printStackTrace(System.err);
      throw new StoreException(t);
    }
  }
   
  /**
   * Deletes the given layer, and all associated annotations.
   * @param id The ID layer to delete.
   * @throws StoreException If an error prevents the transcript from being saved.
   * @throws PermissionException If saving the transcript is not permitted.
   */
  public void deleteLayer(String id) throws StoreException, PermissionException {
    requireAdmin();
    if (id == null) throw new StoreException("Deleting layer: no ID specified");
    try {
      Schema schema = getSchema();
      if (id.equals(schema.getWordLayerId())
          || id.equals(schema.getUtteranceLayerId())
          || id.equals(schema.getTurnLayerId())
          || id.equals("orthography")
          || id.equals("segment")) {
        throw new StoreException("Cannot delete system layer: " + id);
      }
      Layer layer = getLayer(id);
      if (!layer.containsKey("layer_id")) {
        throw new StoreException("Deleting layer " + id + " not yet implemented");
      }
      int layer_id = (Integer)layer.get("layer_id");
      if (layer_id < 0) {
        throw new StoreException("Cannot delete system layer: " + id + " ("+layer_id+")");
      }

      // drop the annotation table
      PreparedStatement sql = getConnection().prepareStatement(
        "DROP TABLE IF EXISTS annotation_layer_" + layer_id);
      sql.executeUpdate();
      sql.close();

      // delete any annotation tasks named after the layer
      try {
        deleteAnnotatorTask(layer.getId());
      } catch(StoreException exception) {}
         
      // if there are auxiliary configurations...
      sql = getConnection().prepareStatement(
        "SELECT description FROM layer_auxiliary_manager WHERE layer_id = ?");
      sql.setInt(1, layer_id);
      ResultSet rs = sql.executeQuery();
      while (rs.next()) {
        // delete any annotation tasks for the auxiliary configuration
        try {
          deleteAnnotatorTask(layer.getId() + ":" + rs.getString(1));
        } catch(StoreException exception) {}
      } // next auxiliary configuration
      rs.close();
      sql.close();

      // delete any auxiliary configurations
      sql = getConnection().prepareStatement(
        "DELETE FROM layer_auxiliary_manager WHERE layer_id = ?");
      sql.setInt(1, layer_id);
      sql.executeUpdate();
      sql.close();

      // drop old 'meta' layer tables
      sql = getConnection().prepareStatement(
        "DROP TABLE IF EXISTS transcript_speaker_layer_" + layer_id);
      sql.executeUpdate();
      sql.close();         
      sql = getConnection().prepareStatement(
        "DROP TABLE IF EXISTS corpus_layer_" + layer_id);
      sql.executeUpdate();
      sql.close();

      // delete validLabels
      sql = getConnection().prepareStatement(
        "DELETE FROM label_option WHERE layer_id = ?");
      sql.setInt(1, layer_id);
      sql.executeUpdate();
      sql.close();

      // delete the layer row
      sql = getConnection().prepareStatement(
        "DELETE FROM layer WHERE layer_id = ?");
      sql.setInt(1, layer_id);
      sql.executeUpdate();
      sql.close();
         
      // ensure schema is reloaded with layer removed
      this.schema = null;
         
    } catch (SQLException x) {
      throw new StoreException(x);
    }
  }
   
  /**
   * Create a new annotator task with the given ID and description.
   * @param annotatorId The ID of the annotator that will perform the task.
   * @param taskId The ID of the task, which must not already exist.
   * @param description The description of the task.
   * @throws StoreException If an error prevents the operation.
   * @throws PermissionException If the operation is not permitted.
   */
  public void newAnnotatorTask(String annotatorId, String taskId, String description)
    throws StoreException, PermissionException {
    requireAdmin();

    if (getAnnotator(annotatorId) == null) {
      throw new InvalidIdException(annotatorId);
    }
      
    try {
      PreparedStatement sql = getConnection().prepareStatement(
        "INSERT INTO automation (annotator_id, task_id, description, user_id)"
        +" VALUES (?,?,?,?)");
      sql.setString(1, annotatorId);
      sql.setString(2, taskId);
      sql.setString(3, description);
      sql.setString(4, getUser()==null?"":getUser());
      try {
        sql.executeUpdate();
      } finally {
        sql.close();
      }
    } catch (SQLIntegrityConstraintViolationException x) {
      throw new ExistingIdException(taskId);
    } catch (SQLException x) {
      throw new StoreException(x);
    }
  } // end of newAnnotatorTask()
   
  /**
   * Update the annotator task description.
   * @param taskId The ID of the task, which must not already exist.
   * @param description The description of the task.
   * @throws StoreException If an error prevents the operation.
   * @throws PermissionException If the operation is not permitted.
   */
  public void saveAnnotatorTaskDescription(String taskId, String description)
    throws StoreException, PermissionException {
    requireAdmin();
      
    try {
      PreparedStatement sql = getConnection().prepareStatement(
        "UPDATE automation SET description = ? WHERE task_id = ?");
      sql.setString(1, description);
      sql.setString(2, taskId);
      try {
        if (sql.executeUpdate() <= 0) {
          throw new StoreException("Invalid task ID: " + taskId);
        }
      } finally {
        sql.close();
      }
    } catch (SQLException x) {
      throw new StoreException(x);
    }
  } // end of saveAnnotatorTaskDescription()

  /**
   * Update the annotator task parameters.
   * @param taskId The ID of the automation task.
   * @param parameters The task parameters, serialized as a string.
   * @throws StoreException If an error prevents the operation.
   * @throws PermissionException If the operation is not permitted.
   */
  public void saveAnnotatorTaskParameters(String taskId, String parameters)
    throws StoreException, PermissionException {
    requireAdmin();
      
    try {
      PreparedStatement sql = getConnection().prepareStatement(
        "UPDATE automation SET parameters = ? WHERE task_id = ?");
      sql.setString(1, parameters);
      sql.setString(2, taskId);
      try {
        if (sql.executeUpdate() <= 0) {
          throw new StoreException("Invalid task ID: " + taskId);
        }
      } finally {
        sql.close();
      }
    } catch (SQLException x) {
      throw new StoreException(x);
    }
  } // end of saveAnnotatorTaskParameters()

  /**
   * Delete the identified automation task.
   * @param taskId The ID of the automation task.
   * @throws StoreException If an error prevents the operation.
   * @throws PermissionException If the operation is not permitted.
   */
  public void deleteAnnotatorTask(String taskId)
    throws StoreException, PermissionException {
    requireAdmin();
      
    try {
      PreparedStatement sql = getConnection().prepareStatement(
        "DELETE FROM automation WHERE task_id = ?");
      sql.setString(1, taskId);
         
      try {
        if (sql.executeUpdate() <= 0) {
          throw new StoreException("No such task: " + taskId);
        }
      } finally {
        sql.close();
      }
    } catch (SQLException x) {
      throw new StoreException(x);
    }
  } // end of deleteAnnotatorTask()
   
} // end of class SqlGraphStoreAdministration
