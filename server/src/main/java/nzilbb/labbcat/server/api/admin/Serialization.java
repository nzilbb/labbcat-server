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
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import nzilbb.ag.PermissionException;
import nzilbb.ag.StoreException;
import nzilbb.ag.serialize.*;
import nzilbb.ag.serialize.util.IconHelper;
import nzilbb.labbcat.server.api.APIRequestHandler;
import nzilbb.labbcat.server.api.RequestParameters;
import nzilbb.labbcat.server.api.RequiredRole;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.util.IO;
import nzilbb.util.SemanticVersionComparator;

/**
 * Servlet that manages installation/upgrade/uninstallation of formatters (serializers/deserializers).
 * <h4>/api/admin/serialization</h4>
 * <p> Only the POST method is accepted. The protocol for installation of a serialization
 * module is:
 * <ol>
 *
 *  <li> Upload the formatter .jar file with a multipart POST request (the first file
 *       parameter encountered is taken, regardless of its name). The response is a
 *       JSON-encoded envelope with a "model" object with the following attributes:
 *   <dl>
 *     <dt> jar </dt><dd> The name of the .jar file uploaded (this must be used in the
 *                        subsequent request). </dd>
 *     <dt> name </dt><dd> The human-readable name of the file format handled by the 
 *                        serialization module found in the .jar file. </dd>
 *     <dt> mimeType </dt><dd> The MIME (Content) Type handled by the serialization module
 *                        found in the .jar file. </dd>
 *     <dt> version </dt><dd> The version of the serialization implementation. </dd>
 *     <dt> deserializer </dt><dd> Whether or not the module can read files of the
 *                        given format </dd> 
 *     <dt> serializer </dt><dd> Whether or not the module can produce files of the
 *                        given format </dd> 
 *     <dt> icon </dt><dd> URL for the module's icon. </dd>
 *     <dt> installedVersion </dt><dd> The version of the already-installed serialization
 *                        implementation, if any. </dd>
 *   </dl>
 *  </li>
 *
 *  <li> Make a POST request with the following application/x-www-form-urlencoded
 *       parameters:
 *   <dl>
 *     <dt> action </dt><dd> Either <q>install</q> or <q>cancel</q> </dd>
 *     <dt> jar </dt><dd> The name of the .jar file, as returned in the response to the
 *                        previous request. </dd>
 *   </dl>
 *       If action was <q>intsall</q>, then the response is a JSON-encoded envelope with
 *       a "model" object with the following attributes: 
 *   <dl>
 *     <dt> jar </dt><dd> The name of the .jar file, as returned in the response to the
 *                        previous request. </dd>
 *     <dt> name </dt><dd> The human-readable name of the file format handled by the 
 *                        serialization module found in the .jar file. </dd>
 *     <dt> mimeType </dt><dd> The MIME (Content) Type handled by the serialization module
 *                        found in the .jar file. </dd>
 *     <dt> version </dt><dd> The version of the serialization implementation. </dd>
 *   </dl>
 *  </li>
 * </ol>
 * 
 * <p> Serialization modules can be uninstalled by making a POST request with the following
 * application/x-www-form-urlencoded parameters:
 *   <dl>
 *     <dt> action </dt><dd> <q>uninstall</q> </dd>
 *     <dt> mimeType </dt><dd> The MIME (Content) Type handled by the serialization. </dd>
 *   </dl>
 * @author Robert Fromont robert@fromont.net.nz
 */
@RequiredRole("admin")
public class Serialization extends APIRequestHandler {

  private File tempDir;
  
  @SuppressWarnings("serial")
  class CouldNotDeleteFileException extends Exception {
    File file;
    public CouldNotDeleteFileException(File f) {
      file = f;
    }
  }
  
  /**
   * Default constructor.
   */
  public Serialization(File tempDir) {
    this.tempDir = tempDir;
  } // end of constructor
  
  /**
   * POST handler - receive an uploaded file or an installation confirmation.
   * @param parameters Request parameter map.
   * @param httpStatus Receives the response status code, in case of error.
   * @param annotatorDir The directory in which annotator jars and their files are stored.
   * @return JSON-encoded object representing the response
   */
  @SuppressWarnings("rawtypes")
  public JsonObject post(RequestParameters parameters, Consumer<Integer> httpStatus, File annotatorDir) {

    try {
      SqlGraphStoreAdministration store = getStore();
      try {
        
        if (!hasAccess(store.getConnection())) {
          return null;
        }
        Optional anyFileValue = parameters.keySet().stream()
          .map(key->parameters.get(key))
          .filter(value->value instanceof Vector)
          .filter(value->((Vector)value).size() > 0)
          .map(value->((Vector)value).firstElement())
          .filter(firstValue->firstValue instanceof File)
          .findAny();
        if (anyFileValue.isPresent()) { // file being uploaded
               
          // take the first file we find
          File formFile = (File)anyFileValue.get();
          if (!formFile.getName().endsWith(".jar")) {
            httpStatus.accept(SC_BAD_REQUEST);
            return failureResult("Invalid file: {0}", formFile.getName());
          }
          File uploadedJarFile = new File(tempDir, formFile.getName());
          uploadedJarFile.deleteOnExit();
          if (!formFile.renameTo(uploadedJarFile)) {
            IO.Copy(formFile, uploadedJarFile);
            formFile.delete();
          }
          
          // find the serialization
          try {
            GraphDeserializer deserializer = null;
            GraphSerializer serializer = null;
            try {
              Vector deserializers = nzilbb.util.IO.FindImplementorsInJar(
                uploadedJarFile, getClass().getClassLoader(), 
                Class.forName("nzilbb.ag.serialize.GraphDeserializer"));
              if (deserializers.size() > 0) {
                deserializer = (GraphDeserializer)deserializers.firstElement();
              }
            } catch (ClassNotFoundException noAnnotator) {
            } catch (ClassCastException wrongType) {
            }
            try {
              Vector serializers = nzilbb.util.IO.FindImplementorsInJar(
                uploadedJarFile, getClass().getClassLoader(), 
                Class.forName("nzilbb.ag.serialize.GraphSerializer"));
              if (serializers.size() > 0) {
                serializer = (GraphSerializer)serializers.firstElement();
              }
            } catch (ClassNotFoundException noAnnotator) {
            } catch (ClassCastException wrongType) {
            }
            SerializationDescriptor descriptor 
              = deserializer != null?deserializer.getDescriptor()
              : serializer != null?serializer.getDescriptor()
              : null;
            if (descriptor == null) {
              httpStatus.accept(SC_BAD_REQUEST);
              return failureResult("No formatter found in {0}", uploadedJarFile);
            }
            SemanticVersionComparator versionComparator = new SemanticVersionComparator();
            if (versionComparator.compare(
                  descriptor.getMinimumApiVersion(), nzilbb.ag.Constants.VERSION) > 0) {
              return failureResult(
                "Formatter {0} ({1}) version {2} requires API version {3} but you are currently running version {4}",
                descriptor.getName(),
                descriptor.getMimeType(),
                descriptor.getVersion(),
                descriptor.getMinimumApiVersion(),
                nzilbb.ag.Constants.VERSION);
            }
            GraphDeserializer previousDeserializer = store.deserializerForMimeType(
              descriptor.getMimeType());
            GraphSerializer previousSerializer = store.serializerForMimeType(
              descriptor.getMimeType());
            SerializationDescriptor previous
              = previousDeserializer != null?previousDeserializer.getDescriptor()
              : previousSerializer != null?previousSerializer.getDescriptor()
              : null;
            
            // return information
            JsonObjectBuilder jsonResult = Json.createObjectBuilder()
              .add("jar", uploadedJarFile.getName())
              .add("name", descriptor.getName())
              .add("mimeType", descriptor.getMimeType())
              .add("version", descriptor.getVersion())
              .add("deserializer", deserializer != null)
              .add("serializer", serializer != null);
            // try to unpack the icon and make it temporarily available
            try {
              File iconFile = IconHelper.EnsureIconFileExists(descriptor, tempDir);
              File tempIconFile = new File(
                store.getSerializersDirectory(), 
                IO.SafeFileNameUrl(descriptor.getMimeType().replace('/','_'))
                + "_temp_icon." + IO.Extension(iconFile));
              IO.Rename(iconFile, tempIconFile);
              jsonResult = jsonResult.add(
                "icon", new URL(store.getBaseUrl()
                                +"/"+store.getSerializersDirectory().getName()
                                +"/"+tempIconFile.getName()).toString());
            } catch(MalformedURLException exception) {
            } catch(IOException exception) {
            }
            if (previous != null) {
              jsonResult = jsonResult.add("installedVersion", previous.getVersion());
            }
            return successResult(jsonResult.build(), "Formatter received.");
          } catch(Exception exception) {
            httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
            return failureResult(exception);
          }
        } else { // another action: install/cancel/uninstall
          
          String action = parameters.getString("action");
          if (action == null) {
            httpStatus.accept(SC_BAD_REQUEST);
            return failureResult("Missing parameter: {0}", "action");
          }
          
          if (action.equals("uninstall")) { // uninstall
            String mimeType = parameters.getString("mimeType");
            if (mimeType == null) {
              httpStatus.accept(SC_BAD_REQUEST);
              return failureResult("Missing parameter: {0}", "mimeType");
            }

            File jar = null;

            // deregister the serializer/deserializer
            GraphDeserializer deserializer = store.deserializerForMimeType(mimeType);
            if (deserializer != null) {
              jar = IO.JarFileOfClass(deserializer.getClass());
              store.deregisterDeserializer(deserializer);
            }
            GraphSerializer serializer = store.serializerForMimeType(mimeType);
            if (serializer != null) {
              jar = IO.JarFileOfClass(serializer.getClass());
              store.deregisterSerializer(serializer);
            }
            
            if (deserializer == null && serializer == null) {
              httpStatus.accept(SC_NOT_FOUND);
              return failureResult("Formatter not found: {0}", mimeType);
            }
            
            // delete jar file
            if (jar != null) jar.delete();
            return successResult(null, "Formatter uninstalled"); 
            
          } else { // install/cancel
            String fileName = parameters.getString("jar");
            if (fileName == null) {
              httpStatus.accept(SC_BAD_REQUEST);
              return failureResult("Missing parameter: {0}", "jar");
            }
            File uploadedJarFile = new File(tempDir, fileName);
            if (!uploadedJarFile.exists()) {
              httpStatus.accept(SC_NOT_FOUND);
              return failureResult("File not found: {0}", fileName);
            } else { // uploadedJarFile exists
              try {
                // find the annotator
                GraphDeserializer deserializer = null;
                GraphSerializer serializer = null;
                try {
                  Vector deserializers = nzilbb.util.IO.FindImplementorsInJar(
                    uploadedJarFile, getClass().getClassLoader(), 
                    Class.forName("nzilbb.ag.serialize.GraphDeserializer"));
                  if (deserializers.size() > 0) {
                    deserializer = (GraphDeserializer)deserializers.firstElement();
                  }
                } catch (ClassNotFoundException noAnnotator) {
                } catch (ClassCastException wrongType) {
                }
                try {
                  Vector serializers = nzilbb.util.IO.FindImplementorsInJar(
                    uploadedJarFile, getClass().getClassLoader(), 
                    Class.forName("nzilbb.ag.serialize.GraphSerializer"));
                  if (serializers.size() > 0) {
                    serializer = (GraphSerializer)serializers.firstElement();
                  }
                } catch (ClassNotFoundException noAnnotator) {
                } catch (ClassCastException wrongType) {
                }
                SerializationDescriptor descriptor 
                  = deserializer != null?deserializer.getDescriptor()
                  : serializer != null?serializer.getDescriptor()
                  : null;
                if (descriptor == null) {
                  httpStatus.accept(SC_BAD_REQUEST);
                  return failureResult("No formatter found in {0}", fileName);
                }
                File installedFile = new File(
                  store.getSerializersDirectory(), 
                  IO.SafeFileNameUrl(descriptor.getMimeType().replace('/','_'))
                  + "_" + descriptor.getVersion() + ".jar");
                
                // track temporary icon file
                String extension = IO.Extension(descriptor.getIcon().getPath());
                File tempIconFile = new File(
                  store.getSerializersDirectory(), 
                  IO.SafeFileNameUrl(descriptor.getMimeType().replace('/','_'))
                  + "_temp_icon." + extension);

                try {
                  if (action.equals("install")) { // install
                    GraphDeserializer previousDeserializer = store.deserializerForMimeType(
                      descriptor.getMimeType());
                    if (previousDeserializer != null) {
                      File jar = IO.JarFileOfClass(previousDeserializer.getClass());
                      if (jar.exists() && !jar.delete()) {
                        httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
                        return failureResult(
                          "Could not delete previous version: {0}", jar.getName());
                      }
                    }
                    GraphSerializer previousSerializer = store.serializerForMimeType(
                      descriptor.getMimeType());
                    if (previousSerializer != null) {
                      File jar = IO.JarFileOfClass(previousSerializer.getClass());
                      if (jar.exists() && !jar.delete()) {
                        httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
                        return failureResult(
                          "Could not delete previous version: {0}", jar.getName());
                      }
                    }
                    
                    // install file
                    IO.Copy(uploadedJarFile, installedFile);
                    
                    // register serialization(s) from the installed file
                    try {
                      Vector deserializers = nzilbb.util.IO.FindImplementorsInJar(
                        installedFile, getClass().getClassLoader(), 
                        Class.forName("nzilbb.ag.serialize.GraphDeserializer"));
                      if (deserializers.size() > 0) {
                        store.registerDeserializer((GraphDeserializer)deserializers.firstElement());
                      }
                    } catch (ClassNotFoundException noAnnotator) {
                    } catch (ClassCastException wrongType) {
                    }
                    try {
                      Vector serializers = nzilbb.util.IO.FindImplementorsInJar(
                        installedFile, getClass().getClassLoader(), 
                        Class.forName("nzilbb.ag.serialize.GraphSerializer"));
                      if (serializers.size() > 0) {
                        store.registerSerializer((GraphSerializer)serializers.firstElement());
                      }
                    } catch (ClassNotFoundException noAnnotator) {
                    } catch (ClassCastException wrongType) {
                    }
                    
                    // return information
                    JsonObjectBuilder jsonResult = Json.createObjectBuilder()
                      .add("jar", uploadedJarFile.getName())
                      .add("name", descriptor.getName())
                      .add("mimeType", descriptor.getMimeType())
                      .add("version", descriptor.getVersion());
                    
                    return successResult(jsonResult.build(), "Formatter installed");
                  } else { // cancel
                    return successResult(null, "Installation cancelled.");
                  }
                } finally {
                  // delete temporary icon whether installing or cancelling
                  tempIconFile.delete();
                }
              } finally {
                // delete uploaded file whether installing or cancelling
                uploadedJarFile.delete();
              }              
            } // uploadedJarFile exists
          } // install/cancel
        } // not uploading a file, so another action: install/cancel/uninstall
      } finally {
        cacheStore(store);
      }
    } catch (PermissionException x) {
      httpStatus.accept(SC_FORBIDDEN);
      return failureResult(x);
    } catch (StoreException x) {
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      return failureResult(x.getMessage());
    } catch (SQLException x) {
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      return failureResult("Cannot connect to database: {0}", x.getMessage());
    } catch (IOException x) {
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      return failureResult("Communcation error: {0}", x.getMessage());
    }
  }

} // end of class Serialization
