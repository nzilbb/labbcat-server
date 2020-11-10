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
    * Saves changes to a layer, or adds a new layer.
    * @param layer A new or modified layer definition.
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
    * Supplies a list of automation tasks for the identified annotator.
    * @param annotatorId The ID of the annotator.
    * @return A map of task IDs to descriptions.
    * @throws StoreException If an error prevents the operation.
    * @throws PermissionException If the operation is not permitted.
    */
   public Map<String,String> getAnnotatorTasks(String annotatorId)
      throws StoreException, PermissionException {
      requireAdmin();

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
      requireAdmin();
      
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
