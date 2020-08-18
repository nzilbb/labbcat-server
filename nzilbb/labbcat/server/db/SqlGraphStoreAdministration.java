//
// Copyright 2016-2020 New Zealand Institute of Language, Brain and Behaviour, 
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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.jar.JarFile;
import nzilbb.ag.*;
import nzilbb.ag.serialize.*;
import nzilbb.ag.serialize.util.IconHelper;
import nzilbb.util.IO;

/**
 * Graph store administration that uses a relational database as its back end.
 * @author Robert Fromont robert@fromont.net.nz
 */
public class SqlGraphStoreAdministration
   extends SqlGraphStore
   implements GraphStoreAdministration {
   
   // Attributes:
   // Methods:
   
   /**
    * Constructor with connection.
    * @param baseUrl URL prefix for file access.
    * @param connection An opened database connection.
    * @param user ID of the user
    * @throws SQLException If an error occurs during connection or loading of configuraion.
    * @throws PermissionException If the store user doesn't have administrator privileges
    */
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
   public SqlGraphStoreAdministration(String baseUrl, File files, Connection connection, String user)
      throws SQLException, PermissionException {
      
      super(baseUrl, files, connection, user);
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
    * Registers a transcript deserializer.
    * @param deserializer The deserializer to register.
    * @throws StoreException If an error prevents the deserializer from being registered.
    * @throws PermissionException If registering the deserializer is not permitted.
    */
   public void registerDeserializer(GraphDeserializer deserializer)
      throws StoreException, PermissionException {
      
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
      }
      catch(NoSuchMethodException x) { return null; }
      catch(InvocationTargetException x) { return null; }
      catch(IllegalAccessException exception) { return null; }
      catch(InstantiationException exception) { return null; }
      catch(NullPointerException exception) { return null; }
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
      }
      catch(InvocationTargetException exception) { return null; }
      catch(NoSuchMethodException exception) { return null; }
      catch(IllegalAccessException exception) { return null; }
      catch(InstantiationException exception) { return null; }
      catch(NullPointerException exception) { return null; }
   }

   /**
    * Registers a transcript serializer.
    * @param serializer The serializer to register.
    * @throws StoreException If an error prevents the operation from completing.
    * @throws PermissionException If the operation is not permitted.
    */
   public void registerSerializer(GraphSerializer serializer)
      throws StoreException, PermissionException {
      
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
      }
      catch(NoSuchMethodException exception) { return null; }
      catch(InvocationTargetException exception) { return null; }
      catch(IllegalAccessException exception) { return null; }
      catch(InstantiationException exception) { return null; }
      catch(NullPointerException exception) { return null; }
   }

   /**
    * Gets the serializer for the given file suffix (extension).
    * @param suffix The file extension.
    * @return The serializer for the given suffix, or null if none is registered.
    * @throws StoreException If an error prevents the operation from completing.
    * @throws PermissionException If the operation is not permitted.
    */
   public GraphSerializer serializerForFilesSuffix(String suffix) throws StoreException, PermissionException {
      
      try {
         return (GraphSerializer)serializersBySuffix.get(suffix.toLowerCase()).getClass().getDeclaredConstructor().newInstance();
      }
      catch(InvocationTargetException exception) { return null; }
      catch(NoSuchMethodException exception) { return null; }
      catch(IllegalAccessException exception) { return null; }
      catch(InstantiationException exception) { return null; }
      catch(NullPointerException exception) { return null; }
   }

   /**
    * Saves changes to a layer, or adds a new layer.
    * @param layer A new or modified layer definition.
    * @return The resulting layer definition.
    * @throws StoreException If an error prevents the operation.
    * @throws PermissionException If the operation is not permitted.
    */
   public Layer saveLayer(Layer layer) throws StoreException, PermissionException {
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
            schema = null;

            // return new definition
            return getLayer(layer.getId());

         } else {
            throw new StoreException("Updating layer " + layer.getId() + " not yet implemented"); // TODO
         }
      } catch (SQLException x) {
         throw new StoreException(x);
      } catch (StoreException x) { // not found
         // TODO add new layer
         throw x;
      }
   }

} // end of class SqlGraphStoreAdministration
