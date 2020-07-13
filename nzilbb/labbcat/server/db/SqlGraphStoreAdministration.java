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
   public SqlGraphStoreAdministration setSerializersDirectory(File newSerializersDirectory) { serializersDirectory = newSerializersDirectory; return this; }
   
   /**
    * Registered deserializers, keyed by MIME type.
    * @see #getDeserializersByMimeType()
    * @see #setDeserializersByMimeType(HashMap)
    */
   protected HashMap<String,GraphDeserializer> deserializersByMimeType = new HashMap<String,GraphDeserializer>();
   /**
    * Getter for {@link #deserializersByMimeType}: Registered deserializers, keyed by MIME type.
    * @return Registered deserializers, keyed by MIME type.
    */
   public HashMap<String,GraphDeserializer> getDeserializersByMimeType() { return deserializersByMimeType; }
   /**
    * Setter for {@link #deserializersByMimeType}: Registered deserializers, keyed by MIME type.
    * @param newDeserializersByMimeType Registered deserializers, keyed by MIME type.
    */
   public SqlGraphStoreAdministration setDeserializersByMimeType(HashMap<String,GraphDeserializer> newDeserializersByMimeType) { deserializersByMimeType = newDeserializersByMimeType; return this; }

   
   /**
    * Registered deserializers, keyed by file suffix (extension).
    * @see #getDeserializersBySuffix()
    * @see #setDeserializersBySuffix(HashMap)
    */
   protected HashMap<String,GraphDeserializer> deserializersBySuffix = new HashMap<String,GraphDeserializer>();
   /**
    * Getter for {@link #deserializersBySuffix}: Registered deserializers, keyed by file
    * suffix (extension).
    * @return Registered deserializers, keyed by file suffix (extension).
    */
   public HashMap<String,GraphDeserializer> getDeserializersBySuffix() { return deserializersBySuffix; }
   /**
    * Setter for {@link #deserializersBySuffix}: Registered deserializers, keyed by file
    * suffix (extension).
    * @param newDeserializersBySuffix Registered deserializers, keyed by file suffix (extension).
    */
   public SqlGraphStoreAdministration setDeserializersBySuffix(HashMap<String,GraphDeserializer> newDeserializersBySuffix) { deserializersBySuffix = newDeserializersBySuffix; return this; }

   /**
    * Registered serializers, keyed by MIME type.
    * @see #getSerializersByMimeType()
    * @see #setSerializersByMimeType(HashMap)
    */
   protected HashMap<String,GraphSerializer> serializersByMimeType = new HashMap<String,GraphSerializer>();
   /**
    * Getter for {@link #serializersByMimeType}: Registered serializers, keyed by MIME type.
    * @return Registered serializers, keyed by MIME type.
    */
   public HashMap<String,GraphSerializer> getSerializersByMimeType() { return serializersByMimeType; }
   /**
    * Setter for {@link #serializersByMimeType}: Registered serializers, keyed by MIME type.
    * @param newSerializersByMimeType Registered serializers, keyed by MIME type.
    */
   public SqlGraphStoreAdministration setSerializersByMimeType(HashMap<String,GraphSerializer> newSerializersByMimeType) { serializersByMimeType = newSerializersByMimeType; return this; }
   
   /**
    * Registered serializers, keyed by file suffix (extension).
    * @see #getSerializersBySuffix()
    * @see #setSerializersBySuffix(HashMap)
    */
   protected HashMap<String,GraphSerializer> serializersBySuffix = new HashMap<String,GraphSerializer>();
   /**
    * Getter for {@link #serializersBySuffix}: Registered serializers, keyed by file suffix
    * (extension).
    * @return Registered serializers, keyed by file suffix (extension).
    */
   public HashMap<String,GraphSerializer> getSerializersBySuffix() { return serializersBySuffix; }
   /**
    * Setter for {@link #serializersBySuffix}: Registered serializers, keyed by file suffix
    * (extension).
    * @param newSerializersBySuffix Registered serializers, keyed by file suffix (extension).
    */
   public SqlGraphStoreAdministration setSerializersBySuffix(HashMap<String,GraphSerializer> newSerializersBySuffix) { serializersBySuffix = newSerializersBySuffix; return this; }

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
      loadSerializers();
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
    * @throws SQLException If an error occurs during connection or loading of configuraion.
    * @throws PermissionException If the store user doesn't have administrator privileges
    */
   public SqlGraphStoreAdministration(String baseUrl, File files, String connectString, String databaseUser, String password, String storeUser)
      throws SQLException, PermissionException {
      
      super(baseUrl, files, connectString, databaseUser, password, storeUser);
      // if (getUser() != null && !getUserRoles().contains("admin")) throw new PermissionException();
      loadSerializers();
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
            URLClassLoader classLoader = URLClassLoader.newInstance(url, getClass().getClassLoader());
            Object o = classLoader.loadClass(rs.getString("class")).getDeclaredConstructor().newInstance();
            if (o instanceof GraphDeserializer) {
               GraphDeserializer deserializer = (GraphDeserializer)o;
	       
               // register it in memory
               SerializationDescriptor descriptor = deserializer.getDescriptor();
               deserializersByMimeType.put(descriptor.getMimeType(), deserializer);
               for (String suffix : descriptor.getFileSuffixes()) {
                  deserializersBySuffix.put(suffix, deserializer);
               } // next suffix
               if (getSerializersDirectory() != null) {
                  File iconFile = IconHelper.EnsureIconFileExists(descriptor, getSerializersDirectory());
                  if (getBaseUrl() != null && getBaseUrl().length() > 0)
                  {
                     descriptor.setIcon(
                        new URL(getBaseUrl()+"/"+getSerializersDirectory().getName()+"/"+iconFile.getName()));
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
                  File iconFile = IconHelper.EnsureIconFileExists(descriptor, getSerializersDirectory());
                  if (getBaseUrl() != null && getBaseUrl().length() > 0) {
                     descriptor.setIcon(
                        new URL(getBaseUrl()+"/"+getSerializersDirectory().getName()+"/"+iconFile.getName()));
                  }
               }
            }
         }
         catch(NoSuchMethodException x) { System.err.println(rs.getString("class") + ": " + x); }
         catch(InvocationTargetException x) { System.err.println(rs.getString("class") + ": " + x); }
         catch(ClassNotFoundException x) { System.err.println(rs.getString("class") + ": " + x); }
         catch(InstantiationException x) { System.err.println(rs.getString("class") + ": " + x); }
         catch(IllegalAccessException x) { System.err.println(rs.getString("class") + ": " + x); }
         catch(MalformedURLException x) { System.err.println(rs.getString("class") + ": " + x); }
         catch(IOException x) { System.err.println(rs.getString("class") + ": " + x); }
      }
      rs.close();
      sqlRegisteredConverter.close();
   } // end of loadSerializers()

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
    * Lists the descriptors of all registered deserializers.
    * @return A list of the descriptors of all registered deserializers.
    * @throws StoreException If an error prevents the descriptors from being listed.
    * @throws PermissionException If listing the deserializers is not permitted.
    */
   public SerializationDescriptor[] getDeserializerDescriptors()
      throws StoreException, PermissionException {
      
      SerializationDescriptor[] descriptors = new SerializationDescriptor[deserializersByMimeType.size()];
      int i = 0;
      for (GraphDeserializer deserializer : deserializersByMimeType.values()) {
         SerializationDescriptor descriptor = deserializer.getDescriptor();
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
    * Lists the descriptors of all registered serializers.
    * @return A list of the descriptors of all registered serializers.
    * @throws StoreException If an error prevents the operation from completing.
    * @throws PermissionException If the operation is not permitted.
    */
   public SerializationDescriptor[] getSerializerDescriptors()
      throws StoreException, PermissionException {
      SerializationDescriptor[] descriptors = new SerializationDescriptor[serializersByMimeType.size()];
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

} // end of class SqlGraphStoreAdministration
