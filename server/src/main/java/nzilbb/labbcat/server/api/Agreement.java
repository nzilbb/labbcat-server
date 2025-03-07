//
// Copyright 2024-2025 New Zealand Institute of Language, Brain and Behaviour, 
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
package nzilbb.labbcat.server.api;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URLConnection;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.*;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.json.JsonWriter;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.labbcat.server.servlet.LabbcatServlet;
import nzilbb.util.IO;

/**
 * Provides CRUD operations for the agreement.html file, which is the license agreement
 * that users agree to the first time they log in.
 * @author Robert Fromont robert@fromont.net.nz
 */
public class Agreement extends APIRequestHandler {
  
  /** Constructor */
  public Agreement() {
  }
  
  /**
   * GET handler: Return the given HTML document, or a blank template if it doesn't exist
   * yet.
   * <p> If the Accept request header includes "application/json", then the response is a
   * JSON-encoded representation of the existing file/directory structure for this
   * document, including peers, ancestors, and ancestor peers. This allows the wysiwiki
   * page to present a navigation tree to the user.
   * @param pathInfo The URL path.
   * @param realPath Function for translating an absolute URL path into a File.
   * @param out Response body output stream.
   * @param contentType Receives the content type for specification in the
   * response headers. 
   * @param contentEncoding Receives content character encoding for specification in the
   * response headers. 
   * @param httpStatus Receives the response status code, in case or error.
   */
  public void get(
    String pathInfo, Function<String,File> realPath,
    OutputStream out, Consumer<Long> expiresHeader,
    Consumer<String> contentType, Consumer<String> contentEncoding,
    Consumer<Integer> httpStatus) {
    contentEncoding.accept("UTF-8");
    try {
      if (pathInfo == null
          || pathInfo.endsWith("agreement.html")) {
        contentType.accept("text/html");
        File html = realPath.apply("/agreement.html");
        if (!html.exists()) { // not an HTML document or directory
          httpStatus.accept(SC_NOT_FOUND);
        } else { // existing document
          IO.Pump(new FileInputStream(html), out);
        }
      } else { // an image or other asset
        File asset = realPath.apply("/agreement"+pathInfo);
        if (asset.exists() && !asset.isDirectory()) {
          // serve the content directly
          contentType.accept(
            URLConnection.guessContentTypeFromName(pathInfo));
          expiresHeader.accept( // expires in a week
            new Date().getTime() + (1000*60*60*24*7));
          IO.Pump(new FileInputStream(asset), out);
          return;
        } else {
          httpStatus.accept(SC_NOT_FOUND);
        }
      }
    } catch (Exception x) {
      x.printStackTrace(System.err);
      contentType.accept("application/json");
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      try {
        JsonWriter writer = Json.createWriter(new OutputStreamWriter(out, "UTF-8"));
        writer.writeObject(failureResult(x.getMessage()));
        writer.close();
      } catch (Exception ex) {
        context.servletLog("Agreement.get: " + x);
        x.printStackTrace(System.err);
      }
    }
  }
  
  /**
   * PUT handler: Adds or updates the HTML document
   * @param pathInfo The URL path.
   * @param realPath Function for translating an absolute URL path into a File.
   * @param requestBody Stream supplying the body of the request.
   * @param out Stream for writing the response.
   * @param contentType Consumer for receiving the output content type..
   * @param contentEncoding Receives content character encoding for specification in the
   * response headers. 
   * @param httpStatus Receives the response status code, in case or error.
   * @return A JSON object as the request response.
   */
  public void put(
    String pathInfo, Function<String,File> realPath,
    InputStream requestBody,
    OutputStream out,
    Consumer<String> contentType, Consumer<String> contentEncoding,
    Consumer<Integer> httpStatus) {
    contentType.accept("application/json");
    contentEncoding.accept("UTF-8");
    try {
      if (!canEdit()) { // only admin users allowed to edit anything
        httpStatus.accept(SC_FORBIDDEN);
        return;
      }
      File html = realPath.apply("/agreement.html");
      
      // back up the old version
      backup(html);
      
      // write the new version
      IO.Pump(requestBody, new FileOutputStream(html));
      
      // send JSON response
      JsonWriter writer = Json.createWriter(new OutputStreamWriter(out, "UTF-8"));
      writer.writeObject(successResult(null, "License agreement updated."));
      writer.close();
      
    } catch (Exception x) {
      x.printStackTrace(System.err);
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      try {
        JsonWriter writer = Json.createWriter(new OutputStreamWriter(out, "UTF-8"));
        writer.writeObject(failureResult(x.getMessage()));
        writer.close();
      } catch (Exception ex) {
        context.servletLog("Agreement.get: " + x);
        x.printStackTrace(System.err);
      }
    }
  }
  
  /**
   * DELETE handler: Delete the HTML document.
   * @param pathInfo The URL path.
   * @param realPath Function for translating an absolute URL path into a File.
   * @param requestHeaders Access to HTTP request headers.
   * @param out Response body output stream.
   * @param contentType Receives the content type for specification in the response headers.
   * @param contentEncoding Receives content character encoding for specification in the
   * @param httpStatus Receives the response status code, in case or error.
   */
  public void delete(
    String pathInfo, Function<String,File> realPath,
    OutputStream out,
    Consumer<String> contentType, Consumer<String> contentEncoding,
    Consumer<Integer> httpStatus) {
    try {
      if (!canEdit()) { // only admin users allowed to edit anything
        httpStatus.accept(SC_FORBIDDEN);
        return;
      }
      File html = realPath.apply("/agreement.html");
      if (!html.exists()) { // file's directory doesn't exist
        httpStatus.accept(SC_NOT_FOUND);
      } else {
        context.servletLog("Deleting " + html.getPath());
        
        // back up the old version
        backup(html);
        
        // delete the file
        html.delete();
        
        // send JSON response
        contentType.accept("application/json");
        contentEncoding.accept("UTF-8");
        JsonWriter writer = Json.createWriter(new OutputStreamWriter(out, "UTF-8"));
        writer.writeObject(successResult(null, "License agreement deleted."));
        writer.close();
        
      } // file exists
    } catch (Exception x) {
      contentType.accept("text/plain");
      contentEncoding.accept("UTF-8");
      x.printStackTrace(System.err);
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      try {
        out.write(x.getMessage().getBytes());
      } catch (Exception ex) {}
    }
  }
  
  /**
   * POST handler: for saving images and other assets.
   * @param pathInfo The URL path.
   * @param realPath Function for translating an absolute URL path into a File.
   * @param parameters Request parameter map.
   * @param out Stream for writing the response.
   * @param contentType Consumer for receiving the output content type..
   * @param contentEncoding Receives content character encoding for specification in the
   * response headers. 
   * @param httpStatus Receives the response status code, in case or error.
   * @return A JSON object as the request response.
   */
  @SuppressWarnings("rawtypes")
  public void post(
    String pathInfo, Function<String,File> realPath,
    RequestParameters parameters,
    OutputStream out,
    Consumer<String> contentType, Consumer<String> contentEncoding,
    Consumer<Integer> httpStatus) {
    
    try {
      if (!canEdit()) { // only admin users allowed to upload anything
        httpStatus.accept(SC_FORBIDDEN);
        return;
      }
      // take the first file we find
      Optional anyFileValue = parameters.keySet().stream()
        .map(key->parameters.get(key))
        .filter(value->value instanceof File)
        .findAny();
      if (!anyFileValue.isPresent()) { // file not being uploaded
        contentType.accept("application/json");
        contentEncoding.accept("UTF-8");
        writeResponse(out, failureResult("No file received."));
      } else { // file found
        File formFile = (File)anyFileValue.get();
        File file = realPath.apply("/agreement"+pathInfo);
        if (!file.getParentFile().exists()) {
          Files.createDirectories(file.getParentFile().toPath());
        }
        if (file.exists()) {
          // get a non-existent file name
          File dir = file.getParentFile();
          String name = IO.WithoutExtension(file);
          String ext = IO.Extension(file);
          int i = 0;
          do {
            file = new File(dir, name + "-" + (++i) + "." + ext);
          } while(file.exists());
        }
        if (!formFile.renameTo(file)) {
          IO.Copy(formFile, file);
          formFile.delete();
        }
        
        contentType.accept("application/json");
        contentEncoding.accept("UTF-8");
        JsonWriter writer = Json.createWriter(new OutputStreamWriter(out, "UTF-8"));
        JsonObjectBuilder model = Json.createObjectBuilder();
        model.add("url", "agreement"+pathInfo.replaceAll("[^/]+$", file.getName()));
        writer.writeObject(successResult(model.build(), null));
        writer.close();
        
      } // file found
    } catch (Exception x) {
      x.printStackTrace(System.err);
      contentType.accept("text/plain");
      contentEncoding.accept("UTF-8");
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      try {
        out.write(x.getMessage().getBytes());
      } catch(Exception exception) {}
    }
  }
  
  /**
   * OPTIONS handler: specifies what HTML methods are allowed, depending on the user access.
   */
  public String options() {
    String allow = "OPTIONS, GET";
    if (canEdit()) {
      allow += ", PUT, POST, DELETE";
    }
    return allow;
  }

  static final SimpleDateFormat versionFormat = new SimpleDateFormat("-yyyy-MM-dd-HH-mm-ss");
  /**
   * Copies the current version of the given file, if any.
   * @param html
   * @return true if the file exists, false otherwise
   */
  protected boolean backup(File html) {
    if (html.exists()) {
      // there's and older version of the file, take a backup
      File backup = new File(
        html.getParentFile(), html.getName()  + ".v"
        + versionFormat.format(new java.util.Date(html.lastModified())));
      if (!html.renameTo(backup)) {
        try {
          IO.Copy(html, backup);
        } catch(Exception exception) {
          context.servletLog("Agreement.backup("+html.getPath()+"): " + exception.toString());
        }
      }
      return true;
    }
    return false;
  } // end of backup()
  
  /**
   * Determines whether editing is allowed.
   * @return true if the current user is allowed to PUT documents, false otherwise.
   */
  protected boolean canEdit() {
    return context.isUserInRole("admin");
  } // end of canEdit()

}
