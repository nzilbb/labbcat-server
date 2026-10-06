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
package nzilbb.labbcat.server.api.admin.elicit;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Optional;
import java.util.Vector;
import java.util.function.Consumer;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import nzilbb.ag.PermissionException;
import nzilbb.ag.StoreException;
import nzilbb.labbcat.server.api.APIRequestHandler;
import nzilbb.labbcat.server.api.RequestParameters;
import nzilbb.labbcat.server.api.RequiredRole;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.util.IO;

/**
 * Servlet that manages speech elicitation task stimuli files.
 * <h4>/api/admin/elicit/stimulus[/file-name]</h4>
 *  <p> The following operations, specified by the HTTP method, are supported:
 *   <dl>
 *    <dt> POST </dt><dd> Upload a stimulus image/video. using a multipart POST request.
 *       (the first file parameter encountered is taken, regardless of its name).
 *     <ul>
 *      <li><em> Request Body </em> - A multi-part encoded request with one file parameter
 *          (the first file parameter encountered is taken, regardless of its name).
 *        Accepted file types are: png, gif, jpeg, mp4.
 *      </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object with the following attributes:
 *      <dl>
 *        <dt> file_size </dt><dd> A string representing size of the file in bytes. </dd>
 *        <dt> file_content_type </dt><dd> The file content type. </dd>
 *        <dt> file_name </dt><dd> The name of the file on the server. </dd>
 *      </dl>
 *      </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 201 </em> : The stimulus file was successfully created or replaced.</li>
 *         <li><em> 415 </em> : The file was not of a supported type. </li> 
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *    <dt> GET </dt><dd> List stimulus files. 
 *     <ul>
 *      <li><em> No parameters are required </em></li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as a
 *       corresponding array of file names.  </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The records could be listed. </li>
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *    <dt> DELETE </dt><dd> Delete an existing file.
 *     <ul>
 *      <li><em> Request Path </em> - /api/admin/elicit/stimulus/<var>file-name</var>.</li>
 *      <li><em> Response Body </em> - the standard JSON envelope, including a message if
 *          the request succeeds or an error explaining the reason for failure. </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The stimulus file was successfully deleted. </li>
 *         <li><em> 415 </em> : The file was not of a supported type. </li> 
 *         <li><em> 404 </em> : The file was not found. </li>
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *   </dl>
 *  </li>
 * @author Robert Fromont robert@fromont.net.nz
 */
@RequiredRole("admin")
public class Stimulus extends APIRequestHandler {

  private static HashMap<String,String> extensionToMimeType = new HashMap<String,String>(){{
      put("png", "image/png");
      put("gif", "image/gif");
      put("jpg", "image/jpeg");
      put("jpeg", "image/jpeg");
      put("mp4", "video/mp4");
      put("svg", "image/svg+xml");
    }};
  private File stimulusDir;
  
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
  public Stimulus(File stimulusDir) {
    this.stimulusDir = stimulusDir;
  } // end of constructor
  
  /**
   * POST handler - receive an uploaded stimulus file.
   * @param parameters Request parameter map.
   * @param httpStatus Receives the response status code, in case of error.
   * @param annotatorDir The directory in which annotator jars and their files are stored.
   * @return JSON-encoded object representing the response
   */
  @SuppressWarnings("rawtypes")
  public JsonObject post(RequestParameters parameters, Consumer<Integer> httpStatus) {

    try {
      SqlGraphStoreAdministration store = getStore();
      try {
        if (!hasAccess(store.getConnection())) {
          return null;
        }
        if (parameters == null) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No file received.");
        }
        Optional anyFileValue = parameters.keySet().stream()
          .map(key->parameters.getFile(key))
          .filter(file->file != null)
          .findAny();
        if (!anyFileValue.isPresent()) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No file received.");
        } else { // file being uploaded
          // take the first file we find
          File formFile = (File)anyFileValue.get();
          try {
            String mimeType = extensionToMimeType.get(IO.Extension(formFile));
            if (mimeType == null) {
              httpStatus.accept(SC_UNSUPPORTED_MEDIA_TYPE); // 415
              return failureResult("Invalid file: {0}", formFile.getName()); // TODO i18n
            }
            File stimulusFile = new File(stimulusDir, formFile.getName());
            boolean preexisting = stimulusFile.exists();
            IO.Rename(formFile, stimulusFile);
            // return information
            JsonObjectBuilder jsonResult = Json.createObjectBuilder()
              .add("file_name", stimulusFile.getName())
              .add("url", getContext().getBaseUrl()+"/elicit/"+stimulusFile.getName())
              .add("file_size", ""+stimulusFile.length())
              .add("file_content_type", mimeType);
            
            httpStatus.accept(SC_CREATED);
            return successResult(
              jsonResult.build(),
              preexisting?"Replaced {0}":"Saved {0}", // TODO i18n
              stimulusFile.getName());
          } finally {
            formFile.delete();
          }
        } // uploading a file
      } finally {
        cacheStore(store);
      }
    } catch (PermissionException x) {
      httpStatus.accept(SC_FORBIDDEN);
      return failureResult(x);
    } catch (SQLException x) {
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      return failureResult("Cannot connect to database: {0}", x.getMessage());
    } catch (IOException x) {
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      return failureResult("Communcation error: {0}", x.getMessage());
    }
  }
  
  /**
   * GET handler - list stimulus files.
   * @param httpStatus Receives the response status code, in case of error.
   * @param annotatorDir The directory in which annotator jars and their files are stored.
   * @return JSON-encoded object representing the response
   */
  public JsonObject get(Consumer<Integer> httpStatus) {
    try {
      SqlGraphStoreAdministration store = getStore();
      try {        
        if (!hasAccess(store.getConnection())) {
          return null;
        }
        String[] stimulusFiles = stimulusDir.list(new FilenameFilter() {
            public boolean accept(File dir, String name) {
              return extensionToMimeType.keySet().contains(IO.Extension(name));
            }
          });
        JsonArrayBuilder jsonResult = Json.createArrayBuilder();
        for (String stimulus : stimulusFiles) {
          jsonResult.add(stimulus);
        } // next file
        return successResult(jsonResult.build(), null);
      } finally {
        cacheStore(store);
      }
    } catch (PermissionException x) {
      httpStatus.accept(SC_FORBIDDEN);
      return failureResult(x);
    } catch (SQLException x) {
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      return failureResult("Cannot connect to database: {0}", x.getMessage());
    }
  }

  /**
   * DELETE handler: Delete the given stimulus file.
   * @param pathInfo The URL path.
   * @param out Response body output stream.
   * @param contentType Receives the content type for specification in the response headers.
   * @param contentEncoding Receives content character encoding for specification in the
   * @param httpStatus Receives the response status code, in case of error.
   * @return JSON-encoded object representing the response
   */
  public JsonObject delete(String pathInfo, Consumer<Integer> httpStatus) {
    try {
      SqlGraphStoreAdministration store = getStore();
      try {        
        if (!hasAccess(store.getConnection())) {
          return null;
        }
        String fileName = Optional.ofNullable(pathInfo).orElse("");
        int lastSlash = pathInfo.lastIndexOf('/');
        if (lastSlash >= 0) fileName.substring(lastSlash+1);
        if (fileName.length() == 0) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No file name specified.");
        }
        String mimeType = extensionToMimeType.get(IO.Extension(fileName));
        if (mimeType == null) {
          httpStatus.accept(SC_UNSUPPORTED_MEDIA_TYPE); // 415
          return failureResult("Invalid file: {0}", fileName); // TODO i18n
        }
        File stimulus = new File(stimulusDir, fileName);
        if (!stimulus.exists()) {
          httpStatus.accept(SC_NOT_FOUND);
          return failureResult("File not found: {0}", stimulus.getName());
        }
        if (!stimulus.delete()) {
          httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
          return failureResult("Could not delete: {0}", stimulus.getName());
        }
        return successResult(null, "File deleted: {0}", stimulus.getName());
      } finally {
        cacheStore(store);
      }
    } catch (PermissionException x) {
      httpStatus.accept(SC_FORBIDDEN);
      return failureResult(x);
    } catch (SQLException x) {
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      return failureResult("Cannot connect to database: {0}", x.getMessage());
    }
  }
} // end of class Stimulus
