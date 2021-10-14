//
// Copyright 2020 New Zealand Institute of Language, Brain and Behaviour, 
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
package nzilbb.labbcat.server.servlet.annotator;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import nzilbb.ag.PermissionException;
import nzilbb.ag.automation.*;
import nzilbb.ag.automation.util.AnnotatorDescriptor;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.labbcat.server.servlet.*;
import nzilbb.util.IO;
import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.disk.*;
import org.apache.commons.fileupload.servlet.*;

/**
 * Servlet that manages installation/upgrade/uninstallation of annotators.
 * <h4>/api/admin/annotator</h4>
 * <p> Only the POST method is accepted. The protocol for installation of an annotator
 * module is:
 * <ol>
 *
 *  <li> Upload the annotator .jar file with a multipart POST request (the first file
 *       parameter encountered is taken, regardless of its name). The response is a
 *       JSON-encoded envelope with a "model" object with the following attributes:
 *   <dl>
 *     <dt> jar </dt><dd> The name of the .jar file uploaded (this must be used in the
 *                        subsequent request). </dd>
 *     <dt> annotatorId </dt><dd> The ID of the annotator found in the .jar file. </dd>
 *     <dt> version </dt><dd> The version of the annotator implementation. </dd>
 *     <dt> installedVersion </dt><dd> The version of the already-installed annotator
 *                        implementation, if any. </dd>
 *     <dt> hasConfigWebapp </dt><dd> Whether or not the annotator implements a 'config'
 *                        webapp </dd> 
 *     <dt> hasTaskWebapp </dt><dd> Whether or not the annotator implements a 'task' webapp </dd>
 *     <dt> hasExtWebapp </dt><dd> Whether or not the annotator implements an 'ext' webapp </dd>
 *     <dt> info </dt><dd> A complete HTML document containing a description of the
 *                        annotator. </dd>
 *   </dl>
 *  </li>
 *
 *  <li> Make a POST request with the following application/x-www-form-urlencoded
 *       parameters:
 *   <dl>
 *     <dt> action </dt><dd> Either <q>install</q> or <q>cancel</q> </dd>
 *     <dt> jar </dt><dd> The name of the .jar file, as returned in the response to the
 *                        preovious request. </dd>
 *   </dl>
 *       If action was <q>instsall</q>, then the response is a JSON-encoded envelope with
 *       a "model" object with the following attributes: 
 *   <dl>
 *     <dt> jar </dt><dd> The name of the .jar file, as returned in the response to the
 *                        preovious request. </dd>
 *     <dt> annotatorId </dt><dd> The ID of the annotator found in the .jar file. </dd>
 *     <dt> url </dt><dd> The URL that must be visited next in order to complete the
 *                        installation by configuring the annotator. This may be the URL
 *                        of the 'config' webapp if there is one, or the 'setConfig'
 *                        request if not.</dd>
 *   </dl>
 *  </li>
 *
 *  <li> Visit the <q>url</q> returned in the reponse to the previous request. The
 *       resulting response will be an HTML document.</li>
 *  
 * </ol>
 * 
 * <p> Annotators can be uninstalled by making a POST request with the following
 * application/x-www-form-urlencoded parameters:
 *   <dl>
 *     <dt> action </dt><dd> <q>uninstall</q> </dd>
 *     <dt> annotatorId </dt><dd> The ID of the annotator. </dd>
 *   </dl>
 * @author Robert Fromont robert@fromont.net.nz
 */
@WebServlet({"/admin/annotator", "/api/admin/annotator"})
@RequiredRole("admin")
public class AdminAnnotators extends LabbcatServlet {

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
  public AdminAnnotators() {
  } // end of constructor
   
  /** 
   * Initialise the servlet
   */
  public void init() {
    super.init();
    try {
      tempDir = Files.createTempDirectory(getClass().getSimpleName()).toFile();
    } catch (IOException x) {
      System.err.println("AdminAnnotators.init: " + x);
    }
  }
   
  /**
   * POST handler - receive an uploaded file or an installation confirmation.
   */
  @Override
  @SuppressWarnings("rawtypes")
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {

    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    try {
      SqlGraphStoreAdministration store = getStore(request);
      try {
            
        if (!hasAccess(request, response, store.getConnection())) {
          return;
        } 
        if (ServletFileUpload.isMultipartContent(request)) { // file being uploaded
               
          try {
            // take the first file we find
            ServletFileUpload upload = new ServletFileUpload(new DiskFileItemFactory());
            List items = upload.parseRequest(request);
            Iterator it = items.iterator();
            FileItem fileItem = null;
            while (it.hasNext()) {
              FileItem item = (FileItem) it.next();
              if (!item.isFormField()) {
                fileItem = item;
                break;
              }            
            } // next part
            if (fileItem == null) {
              writeResponse(response, failureResult(request, "No file received."));
            } else {
              String fileName = fileItem.getName();
              // some browsers provide a full path, which must be truncated
              int lastSlash = fileName.lastIndexOf('/');
              if (lastSlash < 0) lastSlash = fileName.lastIndexOf('\\');
              if (lastSlash >= 0) {
                fileName = fileName.substring(lastSlash + 1);
              }
              // save the file
              File uploadedJarFile = new File(tempDir, fileName);
              uploadedJarFile.deleteOnExit();
              fileItem.write(uploadedJarFile);
                     
              // find the annotator
              try {
                AnnotatorDescriptor descriptor = new AnnotatorDescriptor(uploadedJarFile);
                Annotator annotator = descriptor.getInstance();

                // return information
                JsonObjectBuilder jsonResult = Json.createObjectBuilder()
                  .add("jar", uploadedJarFile.getName())
                  .add("annotatorId", annotator.getAnnotatorId())
                  .add("version", annotator.getVersion());

                Annotator installed = store.getAnnotator(annotator.getAnnotatorId());
                if (installed != null) {
                  jsonResult.add("installedVersion", installed.getVersion());
                }
                        
                jsonResult
                  .add("hasConfigWebapp", descriptor.hasConfigWebapp())
                  .add("hasTaskWebapp", descriptor.hasTaskWebapp())
                  .add("hasExtWebapp", descriptor.hasExtWebapp())
                  .add("info", descriptor.getInfo());
                writeResponse(
                  response, successResult(request, jsonResult.build(), "Annotator received."));
              } catch (ClassNotFoundException noAnnotator) {
                writeResponse(response, failureResult(
                                request, "No annotator found in {0}", fileName));
              }
            }
          } catch(Exception exception) {
            writeResponse(response, failureResult(exception));
            return;
          }
        } else { // another action: install/cancel/uninstall
               
          String action = request.getParameter("action");
          if (action == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeResponse(response, failureResult(
                            request, "Missing parameter: {0}", "action"));
            return;
          }
               
          if (action.equals("uninstall")) { // uninstall
            String annotatorId = request.getParameter("annotatorId");
            if (annotatorId == null) {
              response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
              writeResponse(response, failureResult(
                              request, "Missing parameter: {0}", "annotatorId"));
            }

            // execute uninstall()
            AnnotatorDescriptor descriptor = store.getAnnotatorDescriptor(annotatorId);
            if (descriptor == null) {
              response.setStatus(HttpServletResponse.SC_NOT_FOUND);
              writeResponse(response, failureResult(
                              request, "Invalid ID: {0}", annotatorId));
              return;
            }
            descriptor.getInstance().uninstall();

            // delete jar file
            try {
              deleteAllJars(annotatorId);
              writeResponse(
                response, successResult(
                  request, null, "Annotator uninstalled."));
            } catch(CouldNotDeleteFileException x) {
              writeResponse(
                response, failureResult(
                  request, "Could not uninstall annotator {0}",
                  x.file.getName()));
              return;
            }
                  
          } else { // install/cancel
            String fileName = request.getParameter("jar");
            File uploadedJarFile = new File(tempDir, fileName);
            if (!uploadedJarFile.exists()) {
              writeResponse(response, failureResult(
                              request, "File not found: {0}", fileName));
            } else { // uploadedJarFile exists
                     
              if (action.equals("install")) { // install
                // find the annotator
                try {
                  AnnotatorDescriptor descriptor = new AnnotatorDescriptor(uploadedJarFile);
                  Annotator annotator = descriptor.getInstance();
                           
                  File installedFile = new File(
                    getAnnotatorDir(), annotator.getAnnotatorId()
                    + "-" + annotator.getVersion() + ".jar");
                           
                  // delete all other versions of the annotator
                  try {
                    deleteAllJars(annotator.getAnnotatorId());
                  }
                  catch(CouldNotDeleteFileException x) {
                    writeResponse(
                      response, failureResult(
                        request, "Could not delete previous version: {0}",
                        x.file.getName()));
                    return;
                  }

                  IO.Copy(uploadedJarFile, installedFile);
                           
                  // return information
                  JsonObjectBuilder jsonResult = Json.createObjectBuilder()
                    .add("jar", installedFile.getName())
                    .add("annotatorId", annotator.getAnnotatorId());
                  // if there's a configuration webapp...
                  if (descriptor.hasConfigWebapp()) {
                    // the next step is to configure the annotator
                    jsonResult.add(
                      "url", baseUrl(request) + request.getServletPath()
                      + "/config/" + annotator.getAnnotatorId() + "/");
                  } else {
                    // no configuration is required, so set the configuration directly
                    jsonResult.add(
                      "url", baseUrl(request) + request.getServletPath()
                      + "/config/" + annotator.getAnnotatorId() + "/setConfig");
                  }                        
                           
                  writeResponse(
                    response, successResult(
                      request, jsonResult.build(), "Annotator installed."));
                } catch (ClassNotFoundException noAnnotator) {
                  writeResponse(response, failureResult(
                                  request, "No annotator found in {0}", fileName));
                           
                }
              } else { // cancel
                writeResponse(
                  response, successResult(request, null, "Installation cancelled."));
              }

              // delete uploaded file whether installing or cancelling
              uploadedJarFile.delete();
                     
            } // uploadedJarFile exists
          } // install/cancel
        } // not uploading a file, so another action: install/cancel/uninstall
      } finally {
        cacheStore(store);
      }
    } catch (PermissionException x) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      writeResponse(response, failureResult(x));
    } catch (SQLException x) {
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      writeResponse(response, failureResult(
                      request, "Cannot connect to database: {0}", x.getMessage()));
    }
  }
   
  /**
   * Deletes all .jar files for the given annotatorId (there should be only one).
   * @param annotatorId
   * @return The number of files deleted.
   * @throws Exception
   */
  private int deleteAllJars(String annotatorId) throws CouldNotDeleteFileException {
    int fileCount = 0;
    for (File jar : getAnnotatorDir().listFiles(new FileFilter() {
        public boolean accept(File f) {
          return !f.isDirectory()
            && f.getName().startsWith(annotatorId + "-")
            && f.getName().endsWith(".jar");
        }})) {
      if (!jar.delete()) {
        throw new CouldNotDeleteFileException(jar);
      }
      fileCount++;
    } // next file
    return fileCount;
  } // end of deleteAllJars()

  /**
   * GET handler: lists currently installed annotators.
   */
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    try { // check access
      Connection connection = newConnection();
      try {
        if (!hasAccess(request, response, connection)) {
          return;
        }
      } finally {
        connection.close();
      }
    } catch(SQLException exception) {
      log("AdminAnnotators: Couldn't connect to database: " + exception);
      writeResponse(response, failureResult(exception));
      return;
    }

    // send upload form
    response.setContentType("text/html");
    response.setCharacterEncoding("UTF-8");
      
    PrintWriter writer = response.getWriter();
    writer.println("<!DOCTYPE html>");
    writer.println("<html>");
    writer.println(" <head>");
    writer.println("  <title>LaBB-CAT Annotator installer</title>");
    writer.println(" </head>");
    writer.println(" <body>");
    writer.println("  <h1>LaBB-CAT Annotator installer</h1>");
    writer.println("  <h2>Upload Annotator</h2>");
    writer.println("  <form method=\"POST\" enctype=\"multipart/form-data\"><table>");
      
    // jar file
    writer.println("   <tr title=\"The annotator module (.jar file)\">");
    writer.println("    <td><label for=\"jar\">Annotator .jar file</label></td>");
    writer.println("    <td><input id=\"jar\" name=\"jar\" type=\"file\""
                   +" onchange=\"if (!this.files[0].name.match('\\.jar$'))"
                   +" { alert('Please choose a .jar file'); this.value = null; }"
                   +" else { document.getElementById('jarName').value = this.files[0].name; }\""
                   +"/></td></tr>");      
    writer.println("    <tr><td><input type=\"submit\" value=\"Upload\"></td></tr>");
      
    writer.println("  </table></form>");
    writer.println("  <h2>Confirm Installation</h2>");
    writer.println("  <form method=\"POST\"><table>");
      
    // confirmation
    writer.println("   <tr title=\"The annotator jar file name just uploaded\">");
    writer.println("    <td><label for=\"jarName\">.jar file name</label></td>");
    writer.println("    <td><input id=\"jarName\" name=\"jar\" type=\"text\""
                   +" onchange=\"if (!this.value.match('\\.jar$'))"
                   +" { alert('Please enter a .jar file name'); this.value = null; }\""
                   +"/></td></tr>");
    writer.println("   <tr title=\"Confirm installation of the annotator\">");
    writer.println("    <td><label for=\"install\">"
                   +"<input type=\"radio\" name=\"action\" id=\"install\" value=\"install\""
                   +" checked>Install</label></td></tr>");
    writer.println("   <tr title=\"Cancel installation of the annotator\">");
    writer.println("    <td><label for=\"cancel\">"
                   +"<input type=\"radio\" name=\"action\" id=\"cancel\" value=\"cancel\">"
                   + "Cancel</label></td></tr>");
    writer.println("   <tr><td><input type=\"submit\" value=\"Confirm\"></td></tr>");
      
    writer.println("  </table></form>");
    writer.println(" </body>");
    writer.println("</html>");
    writer.flush();

  }

  private static final long serialVersionUID = 1;
} // end of class AdminAnnotators
