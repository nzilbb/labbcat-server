//
// Copyright 2020 New Zealand Institute of Language, Brain and Behaviour, 
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
package nzilbb.labbcat.server.servlet.doc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.*;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.json.JsonWriter;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.labbcat.server.servlet.LabbcatServlet;
import nzilbb.util.IO;
import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.disk.*;
import org.apache.commons.fileupload.servlet.*;

/**
 * Provides CRUD operations for all files/directories under /doc/*.
 * <p> This allows for arbitrary documentation of a corpus, using wysiwiki, which allows
 * admin users to create, edit, and delete pages, providing read-only access to others.
 * @author Robert Fromont robert@fromont.net.nz
 */
@WebServlet({"/doc/*"})
public class Doc extends LabbcatServlet {
  
  /**
   * GET handler: Return the given HTML document, or a blank template if it doesn't exist
   * yet.
   * <p> If the Accept request header includes "application/json", then the response is a
   * JSON-encoded representation of the existing file/directory structure for this
   * document, including peers, ancestors, and ancestor peers. This allows the wysiwiki
   * page to present a navigation tree to the user.
   */
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    response.setCharacterEncoding("UTF-8");
    if (request.getPathInfo() != null && request.getPathInfo().endsWith("/")
        && !request.getPathInfo().equals("/")) {
      // redirect to the non-slash-ending version
      response.sendRedirect(
        baseUrl(request) + "/doc"
        +request.getPathInfo().substring(0, request.getPathInfo().length()-1));
      return;
    }
    File html = file(request);
    if (request.getHeader("Accept").indexOf("application/json") < 0) { // page request
      response.setContentType("text/html");               
      if (!html.exists()) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        // return 404, but also a template for creating a new document
        html = new File(getServletContext().getRealPath("/doc/template.html"));        
      }
      // stream out, line by line, substituting "${baseUrl}"
      String baseUrl = baseUrl(request);
      BufferedReader reader = new BufferedReader(new FileReader(html));
      PrintWriter writer = response.getWriter();
      String line = reader.readLine();
      while (line != null) {
        writer.println(line.replace("${baseUrl}", baseUrl));
        line = reader.readLine();
      }
    } else { // application/json request for directory structure
      response.setContentType("application/json");

      File here = html;
      JsonObjectBuilder hereJson = Json.createObjectBuilder();
      JsonObjectBuilder hereChildren = Json.createObjectBuilder();
      File dir = here.getParentFile();
      JsonObjectBuilder dirJson = null;
      do {
        dirJson = Json.createObjectBuilder();
        File[] files = dir.listFiles(new FileFilter() {
            public boolean accept(File p) {
              return !p.getName().equals("template.html")
                && (p.getName().endsWith(".html") || p.isDirectory());
            }
          });
        // build a map of entries, so that dirs and files with the same name are merged
        // and entries are sorted
        TreeMap<String, JsonObjectBuilder> entries = new TreeMap<String, JsonObjectBuilder>();
        for (File f : files) {
          String url = f.getName().replaceAll("\\.html$", "");
          if (!entries.containsKey(url)) entries.put(url, Json.createObjectBuilder());
          JsonObjectBuilder fileJson = entries.get(url);
          if (f.getName().endsWith(".html")) { // document
            // get title from content
            fileJson.add("title", title(f));
            if (f.equals(html)) { // this is the current file
              // add version history
              JsonArrayBuilder versionsJson = Json.createArrayBuilder();
              String versionFilePrefix = html.getName() + ".v-";
              File[] versions = dir.listFiles(new FileFilter() {
                  public boolean accept(File p) {
                    return p.getName().startsWith(versionFilePrefix);
                  }});
              Arrays.sort(versions);
              for (File version : versions) {
                versionsJson.add(version.getName());
              } // next version file
              fileJson.add("versions", versionsJson);
            }
          } else { // subdirectory
            if (f.equals(here)) {              
              fileJson.add("children", hereJson);
            }
          }
          if (f.equals(here)) {              
            fileJson.add("current", Boolean.TRUE);
          }
        } // next file
        for (String url: entries.keySet()) {
          dirJson.add(url, entries.get(url));
        }
        here = dir;
        hereJson = dirJson;
        dir = here.getParentFile();
      } while (!here.getName().equals("doc")); // TODO detect root better than by name
      
      JsonWriter writer = Json.createWriter(response.getWriter());
      writer.writeObject(successResult(request, dirJson.build(), null));
      writer.close();
    }
  }
  
  /**
   * PUT handler: Adds or updates an HTML document.
   */
  @Override
  protected void doPut(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    try {
      if ("/template".equals(request.getPathInfo()) // not allowed to edit template
          || "/template.html".equals(request.getPathInfo())
          || !canEdit(request)) { // only admin users allowed to edit anything
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return;
      }
      File html = file(request);
      if (!html.getParentFile().exists()) { // file's directory doesn't exist
        // create all necessary directories
        Files.createDirectory(html.getParentFile().toPath());
      }
      // back up the old version
      backup(html);
      // write the new version
      IO.Pump(request.getInputStream(), new FileOutputStream(html));
    } catch (Exception x) {
      x.printStackTrace(System.err);
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      response.getOutputStream().write(x.getMessage().getBytes());
    }
  }
  
  /**
   * DELETE handler: Delete the given HTML document.
   */
  @Override
  protected void doDelete(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    try {
      if ("/template".equals(request.getPathInfo()) // not allowed to edit template
          || "/template.html".equals(request.getPathInfo())
          || !canEdit(request)) { // only admin users allowed to edit anything
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return;
      }
      File html = file(request);
      if (!html.exists()) { // file's directory doesn't exist
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      } else {
        log("Deleting " + html.getPath());
        // back up the old version
        backup(html);
        // delete the file
        html.delete();
      } // file exists
    } catch (Exception x) {
      x.printStackTrace(System.err);
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      response.getOutputStream().write(x.getMessage().getBytes());
    }
  }
  
  /**
   * POST handler: for saving images and other assets.
   */
  @Override
  @SuppressWarnings("rawtypes")
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    try {
      if (!canEdit(request)) { // only admin users allowed to upload anything
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return;
      }
      if (ServletFileUpload.isMultipartContent(request)) { // file being uploaded

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
        } else { // file found
          
          File file = new File(getServletContext().getRealPath("/doc-assets"+request.getPathInfo()));
          if (!file.getParentFile().exists()) {
            Files.createDirectories(file.getParentFile().toPath());
          }
          String url = baseUrl(request) + "/doc-assets" + request.getPathInfo();
          if (file.exists()) {
            // get a non-existent file name
            File dir = file.getParentFile();
            String name = IO.WithoutExtension(file);
            String ext = IO.Extension(file);
            int i = 0;
            do {
              file = new File(dir, name + "-" + (++i) + "." + ext);
            } while(file.exists());
            url = url.replaceAll("/[^/]+\\.[^.]+$", "/"+file.getName());
          }
          fileItem.write(file);

          JsonWriter writer = Json.createWriter(response.getWriter());
          JsonObjectBuilder model = Json.createObjectBuilder();
          model.add("url", url);
          writer.writeObject(successResult(request, model.build(), null));
          writer.close();
          
        } // file found
      } // file being uploaded
    } catch (Exception x) {
      x.printStackTrace(System.err);
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      response.getOutputStream().write(x.getMessage().getBytes());
    }
  }
  
  @Override
  /**
   * OPTIONS handler: specifies what HTML methods are allowed, depending on the user access.
   */
  protected void doOptions(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    String allow = "OPTIONS, GET";
    if (canEdit(request)) {
      allow += ", PUT, POST, DELETE";
    }
    response.addHeader("Allow", allow);
  }

  // TODO doDelete
  
  /**
   * Translates the request path into a file.
   * @param request
   * @return The real path of the request.
   */
  protected File file(HttpServletRequest request) {
    String path = request.getPathInfo();
    if (!path.endsWith(".html")) path += ".html";
    return new File(getServletContext().getRealPath("/doc"+path));
  } // end of file()
  
  /**
   * Translates the request path into a local path and determines if it's an existing directory.
   * @param request
   * @return An existing directory if the request corresponds to one, or null otherwise.
   */
  protected File directory(HttpServletRequest request) {
    File dir = new File(getServletContext().getRealPath("/doc"+request.getPathInfo()));
    if (dir.exists() && dir.isDirectory()) {
      return dir;
    } else {
      return null;
    }
  } // end of directory()

  static final Pattern titlePattern = Pattern.compile(".*<title>(.*)</title>.*");
  /**
   * Gets the title of the given document file.
   * @param doc A .html document.
   * @return The contents of the &lt;title&gt; tag in the file, or the file name without
   * the suffix, if there is none.
   */
  protected String title(File html) {
    if (html.exists() && html.getName().endsWith(".html")) {
      try {
        BufferedReader reader = new BufferedReader(new FileReader(html));
        int l = 0;
        String title = null;
        String line = reader.readLine();
        while (line != null && ++l < 10 && title == null) { // read up to ten lines
          Matcher matcher = titlePattern.matcher(line);
          if (matcher.find()) {
            title = matcher.group(1);
            break;
          }
          line = reader.readLine();
        } // next line
        if (title != null && title.trim().length() > 0) {
          return title;
        }
      } catch(Exception exception) {
        log("Doc.title("+html.getPath()+"): " + exception.toString());
      }
    } // file exists and is .html
    return IO.WithoutExtension(html);
  } // end of title()

  static final SimpleDateFormat versionFormat = new SimpleDateFormat("-yyyy-MM-dd-HH-mm-ss");
  /**
   * Copies the current version of the given file, if any.
   * @param html
   */
  protected void backup(File html) {
    if (html.exists()) {
      // there's and older version of the file, take a backup
      File backup = new File(
        html.getParentFile(), html.getName()  + ".v"
        + versionFormat.format(new java.util.Date(html.lastModified())));
      if (!html.renameTo(backup)) {
        try {
          IO.Copy(html, backup);
        } catch(Exception exception) {
          log("Doc.backup("+html.getPath()+"): " + exception.toString());
        }
      }
    }
  } // end of backup()

  /**
   * Determines whether editing is allowed.
   * @param request
   * @return true if the current user is allowed to PUT documents, false otherwise.
   */
  protected boolean canEdit(HttpServletRequest request) {
    try {
      SqlGraphStoreAdministration store = getStore(request);
      try {
        return isUserInRole("admin", request, store.getConnection());
      } finally {
        cacheStore(store);
      }
    } catch (Exception x) {
      x.printStackTrace(System.err);
      return false;
    }
  } // end of canEdit()
  
  private static final long serialVersionUID = 1;

}
