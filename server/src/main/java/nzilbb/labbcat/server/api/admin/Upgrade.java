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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import nzilbb.labbcat.server.api.APIRequestHandler;
import nzilbb.labbcat.server.api.RequestParameters;
import nzilbb.labbcat.server.api.RequiredRole;
import nzilbb.labbcat.server.db.SqlGraphStore;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.labbcat.server.db.StoreCache;
import nzilbb.labbcat.server.task.Upgrader;
import nzilbb.util.IO;

/**
 * <tt>/api/admin/upgrade[/*]</tt>
 * : Handler for receiving, analysing, and processing a LaBB-CAT upgrader.
 * <h3 id="POST"> <tt>/api/admin/upgrade</tt> </h3>
 * <p> <b> POST </b> method requests start the process by uploading an upload .war file,
 * which is the same file as can be used to install a new version of LaBB-CAT. 
 * <p> The multipart-encoded parameter is:
 *  <dl>
 *   <dt> war </dt> <dd> Upgrade file to upload. </dd>
 *  </dl>
 * <p><b>Output</b>: A JSON-encoded response containing a <q>model</q> with the following
 * attributes:
 *  <dl>
 *   <dt> id </dt>
 *       <dd> A unique identifier for the upload which can be passed into subsequent PUT calls
 *        to {@link #put(String,RequestParameters,Consumer,Function) /api/admin/upgrade/...} 
 *        for finalizing the upgrade. </dd> 
 *   <dt> oldVersion </dt>
 *       <dd> The version of LaBB-CAT currently installed. </dd> 
 *   <dt> newVersion </dt>
 *       <dd> The version of LaBB-CAT that would be upgraded to if it's confirmed. </dd> 
 *   <dt> migration </dt>
 *       <dd> <code>true</code> if the file is a migration package - i.e. a complete
 *       LaBB-CAT database include data which has been exported from elsewhere. </dd> 
 *  </dl>
 *
 * <h2 id="PUT"> <tt>/api/admin/upgrade/...</tt> </h2>
 * <p> <b> PUT </b> method confirms that the upgrade should proceed.
 * <p> The request method must be <b> PUT </b> and the URL path following
 * <tt>.../upgrade/</tt> must be the <var>id</var> that was returned by the earlier 
 * <a href="POST">POST</a>. 
 * <p><b>Output</b>: A JSON-encoded response containing a <q>model</q> with the following
 * attributes:
 *  <dl>
 *   <dt> id </dt>
 *       <dd> The upload ID passed in, which can be passed into subsequent calls if necessary. </dd> 
 *   <dt> threadId </dt>
 *       <dd> The ID of the task unpacking the upgrade package. </dd> 
 *  </dl>
 * <p> The response will also contain any messages or errors associated with the
 * confirmation of the upgrade.
 * <p> Confirming the upgrade upacks the contents of the upgrader, triggering the
 * start of the upgrade. Upgrade progress can be followed using <a href="#GET">GET</a>
 * requests.
 *
 * <h2 id="DELETE"> <tt>/api/admin/upgrade/...</tt> </h2>
 * <p> <b> DELETE </b> method requests cancel a previously POSTed upload of an upgrader. 
 * <p> The request method must be <b> DELETE </b> and the URL path following
 * <tt>.../upload/</tt> must be the <var>id</var> that was returned by the earlier 
 * <a href="#POST">POST</a>. 
 *
 * <h2 id="GET"> <tt>/api/admin/upgrade</tt> </h2>
 * <p> <b> GET </b> method provides the status of the current upgrade, if any. 
 * <p><b>Output</b>: A JSON-encoded response containing a <q>model</q> with the following
 * attributes: </p>
 *       <dl>
 *         <dt>version</dt> <dd>The current LaBB-CAT version</dd>
 *         <dt>threadId</dt> <dd>The task's ID</dd>
 *         <dt>threadName</dt> <dd>The name of the task</dd>
 *         <dt>running</dt> <dd>Whether the task is currently running</dd>
 *         <dt>duration</dt> <dd>How long the task has run for</dd>
 *         <dt>percentComplete</dt> <dd>How far through the task is</dd>
 *         <dt>status</dt> <dd>The task's current status description</dd>
 *         <dt>lastException</dt> <dd>The last exception to occur, if any</dd>
 *         <dt>stackTrace</dt> <dd>If an exception has occurred, this
 *             is a stack trace identifying where the exception was thrown</dd>
 *         <dt>messages</dt> <dd>An array of upgrade messages</dd>
 *       </dl>
 * <p> If only the <i>version</i> attribute is present in the model, no upgrader is currently running.</p>
 * @author Robert Fromont robert@fromont.net.nz
 */
@RequiredRole("admin")
public class Upgrade extends APIRequestHandler {

  File uploadsDir;
  File root;
  String driverName;
  String connectionURL;
  String connectionName;
  String connectionPassword;
  
  /**
   * Constructor.
   */
  public Upgrade(
    File root, String driverName, String connectionURL, String connectionName,
    String connectionPassword) {
    uploadsDir = new File(new File(System.getProperty("java.io.tmpdir")), "LaBB-CAT.Upgrade");
    if (!uploadsDir.exists()) uploadsDir.mkdir();
    this.root = root;
    this.driverName = driverName;
    this.connectionURL = connectionURL;
    this.connectionName = connectionName;
    this.connectionPassword = connectionPassword;
  } // end of constructor

  /**
   * The POST method for the servlet.
   * @param requestParameters Request parameter map.
   * @param httpStatus Receives the response status code, in case of error.
   * @return JSON-encoded object representing the response
   */
  public JsonObject post(RequestParameters requestParameters, Consumer<Integer> httpStatus) {
    context.servletLog(
      "POST post " + requestParameters
      + (requestParameters.getFile("war") != null?
         requestParameters.getFile("war").getPath():"(no package)"));
    File dir = null;
    try {
      SqlGraphStoreAdministration store = getStore();
      try {
        if (!hasAccess(store.getConnection())) {
          httpStatus.accept(SC_FORBIDDEN);
          return null;
        }

        File war = requestParameters.getFile("war");
        if (war == null) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No file received.");  
        }
        
        // generate an ID/directory to save files
        dir = Files.createTempDirectory(uploadsDir.toPath(), "_Upgrade_").toFile();
        dir.deleteOnExit();
        String id = dir.getName();
        // context.servletLog("POST id " + id);

        if (war.getName().toLowerCase().endsWith(".zip")) {
          // unzip file and see if there's a war in it
          File zip = war;
          IO.Unzip(zip, dir);
          war = null;
          for (File f : dir.listFiles()) {
            if (f.getName().toLowerCase().endsWith(".war") && f.isFile()) {
              war = f;
              break;
            }
          }
          if (war == null) {
            zip.delete();
            if (dir != null) IO.RecursivelyDelete​(dir);
            
            httpStatus.accept(SC_BAD_REQUEST);
            return failureResult("No upgrade found in {0}", zip.getName());
          }
        }
        if (!war.getName().toLowerCase().endsWith(".war")) {
          war.delete();
          if (dir != null) IO.RecursivelyDelete​(dir);
          
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("Invalid upgrader");
        }

        JsonObjectBuilder model = Json.createObjectBuilder()
          .add("id", id)
          .add("oldVersion", context.getVersion());

        File uploadedWar = new File(dir, war.getName());
        IO.Rename(war, uploadedWar);
        JarFile jar = new JarFile(uploadedWar);
        
        // look for the version number in the file...
        // get the contents of version.txt, if it's there
        try {
          String newVersion = IO.InputStreamToString(
            jar.getInputStream(jar.getJarEntry("version.txt")));
          model.add("newVersion", newVersion);
          if (jar.getJarEntry("WEB-INF/migrate.sql") != null) {
            model.add("migration", Boolean.TRUE);
          }
          context.servletLog(
            "Upgrade to version "+newVersion+" uploaded: " + war.getPath());
        } catch(Throwable exception) {
          war.delete();
          if (dir != null) IO.RecursivelyDelete​(dir);
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult(
            "Could not determine the version in the file: {0}", exception.toString());
        }

        // context.servletLog("POST success " + localize("Uploaded: {0}", transcript.getName()));
        return successResult(model.build(), "Uploaded: {0}", war.getName());
      } finally {
        cacheStore(store);
      }
    } catch(Exception ex) {
      if (dir != null) IO.RecursivelyDelete​(dir);
      try {
        httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      } catch(Exception exception) {}
      context.servletLog("POST Upgrade.post: unhandled exception: " + ex);
      ex.printStackTrace(System.err);
      return failureResult(ex);
    }
  }

  /**
   * The PUT method for the servlet.
   * @param pathInfo The URL path from which the upload ID can be inferred.
   * @param requestParameters Request parameter map.
   * @param httpStatus Receives the response status code, in case of error.
   * @return JSON-encoded object representing the response
   */
  public JsonObject put(
    String pathInfo, RequestParameters requestParameters,Consumer<Integer> httpStatus) {
    context.servletLog("PUT " + pathInfo);
    
    // get ID/directory of saved files
    if (pathInfo == null || pathInfo.equals("/") || pathInfo.indexOf('/') < 0) {
      // no path component
      httpStatus.accept(SC_BAD_REQUEST);
      return failureResult("No ID specified.");
    }
    String id = pathInfo.substring(pathInfo.lastIndexOf('/') + 1);
    if (id.length() == 0) {
      httpStatus.accept(SC_BAD_REQUEST);
      return failureResult("No ID specified.");
    }        
    // context.servletLog("PUT id " + id);
    
    File dir = null;
    try {
      final SqlGraphStoreAdministration store = getStore();
      if (!hasAccess(store.getConnection())) {
        httpStatus.accept(SC_FORBIDDEN);
        return null;
      }
      dir = new File(uploadsDir, id);
      if (!dir.exists()) {
        // close database etc.
        cacheStore(store);
        httpStatus.accept(SC_NOT_FOUND);
        return failureResult("Invalid ID: {0}", id);
      }
      
      File war = null;
      for (File f : dir.listFiles()) {
        if (f.isFile() && f.getName().toLowerCase().endsWith(".war")) {
            war = f;
            break;
          }
        }
        if (war == null) {
          cacheStore(store);
          IO.RecursivelyDelete​(dir);
          httpStatus.accept(SC_NOT_FOUND);
          return failureResult("Invalid ID: {0}", id+"/*.war");
        }
        
        // start upgrader task
        Upgrader task = new Upgrader(
          war, root, driverName, connectionURL, connectionName, connectionPassword);
        task.setStoreCache(new StoreCache() {
            public SqlGraphStore get() {
              return store;
            }
            public void accept(SqlGraphStore store) {
              cacheStore((SqlGraphStoreAdministration)store);
            }
          });
        
        task.setName(id);
        if (context.getUser() != null) {	
          task.setWho(context.getUser());
        } else {
          task.setWho(context.getUserHost());
        }
        context.servletLog("Unpacking upgrade from: " + war.getPath());
        task.start();
        
        // return its ID
        JsonObjectBuilder jsonResult = Json.createObjectBuilder()
          .add("id", id)
          .add("threadId", task.getId());
        return successResult(jsonResult.build(), null);
        
    } catch(Exception ex) {
      context.servletLog("PUT Exception " + ex);
      if (dir != null) IO.RecursivelyDelete​(dir);
      try {
        httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      } catch(Exception exception) {}
      context.servletLog("PUT Upload.post: unhandled exception: " + ex);
      ex.printStackTrace(System.err);
      return failureResult(ex);
    }
  }

  /**
   * The DELETE method for the servlet.
   * @param pathInfo The URL path from which the upload ID can be inferred.
   * @param httpStatus Receives the response status code, in case of error.
   * @return JSON-encoded object representing the response
   */
  public JsonObject delete(
    String pathInfo, Consumer<Integer> httpStatus) {
    context.servletLog("delete " + pathInfo);
    
    // get ID/directory of saved files
    if (pathInfo == null || pathInfo.equals("/") || pathInfo.indexOf('/') < 0) {
      // no path component
      httpStatus.accept(SC_BAD_REQUEST);
      return failureResult("No ID specified.");
    }
    String id = pathInfo.substring(pathInfo.lastIndexOf('/') + 1);
    if (id.length() == 0) {
      httpStatus.accept(SC_BAD_REQUEST);
      return failureResult("No ID specified.");
    }        
    // context.servletLog("id " + id);
    File dir = null;
    try {
      SqlGraphStoreAdministration store = getStore();
      // context.servletLog("store " + store.getId());
      try {
        if (!hasAccess(store.getConnection())) {
          httpStatus.accept(SC_FORBIDDEN);
          return null;
        }
        dir = new File(uploadsDir, id);
        if (!dir.exists()) {
          httpStatus.accept(SC_NOT_FOUND);
          return failureResult("Invalid ID: {0}", id);
        }

        // TODO check the upgrader hasn't already been confirmed

        IO.RecursivelyDelete​(dir);
        return successResult(null, "Upgrade deleted: {0}", id);
      } finally {
        cacheStore(store);
      }
    } catch(Exception ex) {
      context.servletLog("Exception " + ex);
      if (dir != null) IO.RecursivelyDelete​(dir);
      try {
        httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      } catch(Exception exception) {}
      context.servletLog("Upgrade.post: unhandled exception: " + ex);
      ex.printStackTrace(System.err);
      return failureResult(ex);
    }
  } // end of delete    

  /**
   * The GET method for the servlet.
   * @param httpStatus Receives the response status code, in case of error.
   * @return JSON-encoded object representing the response
   */
  public JsonObject get(Consumer<Integer> httpStatus) {
    context.servletLog("get");
    try {
      SqlGraphStoreAdministration store = getStore();
      // context.servletLog("store " + store.getId());
      try {
        if (!hasAccess(store.getConnection())) {
          httpStatus.accept(SC_FORBIDDEN);
          return null;
        }
        
        JsonObjectBuilder model = Json.createObjectBuilder();
        model = model.add("version", context.getVersion());
        
        // return log/status of upgrade
        Upgrader task = Upgrader.getUpgrader();
        if (task == null) {
          return successResult(model.build(), null);
        }

        model = model.add("threadId", task.getId());
        model = model.add("threadName", task.getName());
        model = model.add("who", task.getWho());
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSX");
        model = model.add("creationTime", iso.format(task.getCreationTime()));
        if (task.getLastException() != null) {
          model = model.add("lastException", task.getLastException().toString()
                            // if it's a generic exception, no need to include the class name
                            .replaceAll("^java.lang.Exception: ",""));
          StringWriter sw = new StringWriter();
          PrintWriter pw = new PrintWriter(sw);
          task.getLastException().printStackTrace(pw);
          model = model.add("stackTrace", sw.toString());
        }
        if (task.getResultText() != null)
          model = model.add("resultText", task.getResultText());
        model = model.add("running", task.getRunning());
        model = model.add("duration", task.getDuration());
        if (task.getPercentComplete() != null) {
          model = model.add("percentComplete", task.getPercentComplete());
        } else {
          model = model.add("percentComplete", 0);
        }
        if (task.getStatus() != null)
          model = model.add("status", task.getStatus());
        JsonArrayBuilder messages = Json.createArrayBuilder();
        for (String message : new Vector<String>(task.getMessages())) {
          messages.add(message);
        }
        model = model.add("messages", messages);
        return successResult(model.build(), null);        
      } finally {
        try {
          cacheStore(store);
       } catch(Throwable exception) {}
      }
    } catch(Exception ex) {
      context.servletLog("Exception " + ex);
      try {
        httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      } catch(Exception exception) {}
      context.servletLog("Upgrade.post: unhandled exception: " + ex);
      ex.printStackTrace(System.err);
      return failureResult(ex);
    }
  }

} // end of class Upgrade
