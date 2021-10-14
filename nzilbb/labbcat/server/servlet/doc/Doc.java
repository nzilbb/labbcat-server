//
// Copyright 2021 New Zealand Institute of Language, Brain and Behaviour, 
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
package nzilbb.labbcat.server.servlet.doc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
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
    if (request.getPathInfo() == null) { // root directory with no slash
      // redirect to the slash-ending version
      response.sendRedirect(baseUrl(request) + "/doc/");
      return;
    }
    if (request.getPathInfo() != null && request.getPathInfo().endsWith("/")
        && !request.getPathInfo().equals("/")) { // non-root request ends with a slash
      // redirect to the non-slash-ending version so we can get the document if any
      response.sendRedirect(
        baseUrl(request) + "/doc"
        +request.getPathInfo().substring(0, request.getPathInfo().length()-1));
      return;
    }
    response.setContentType("text/html");
    if (!"/index".equals(request.getPathInfo())) {
      File html = file(request);
      if (!html.exists()) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        // return 404, but also a template for creating a new document
        html = new File(getServletContext().getRealPath("/doc/template.html"));
      }
      if (html.getName().equals("template.html")) { // template
        // stream out the contents, substituting "${base}" for a path to the root directory
        String context = request.getPathInfo();
        if ("/template.html".equals(request.getPathInfo())
            && request.getHeader("Referer") != null) {
          context = request.getHeader("Referer")
            .substring(baseUrl(request).length() + 4); // remove the full URL prefix
        }
        log("context " + context);        
        String[] parts = context.split("/");
        // stream out, line by line, substituting "${base}" for a path to the root directory
        String base = ".";
        if (parts.length > 2) {
          base = "..";
          for (int i = 0; i < parts.length - 3; i++) {
            base += "/..";
          } // next ancestor
        } // base is not the root directory
        BufferedReader reader = new BufferedReader(new FileReader(html));
        PrintWriter writer = response.getWriter();
        String line = reader.readLine();
        while (line != null) {
          writer.println(line.replace("${base}", base));
          line = reader.readLine();
        }
      } else { // existing document
        IO.Pump(new FileInputStream(html), response.getOutputStream());
      }
    } else { // request for index      
      File root = new File(getServletContext().getRealPath("/doc"));
      StringBuilder html = new StringBuilder();
      indexDir(root, root, baseUrl(request) + "/doc", "", response.getWriter());
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
  
  /**
   * Generate HTML for a recursive listing of the given file/directory.
   * @param root The root directory.
   * @param f The file or directory to list.
   * @param baseUrl Base URL for the servlet.
   * @param url relative URL for the parent.
   * @param writer The response writer.
   */
  protected void indexDir(File root, File f, String baseUrl, String url, PrintWriter writer) {
    String nameWithoutExension = IO.WithoutExtension(f);
    if (f.isFile()) {
      if (!f.getName().endsWith(".html")) { // only list HTML documents
        return;
      }
      if (f.getName().equals(".html") && f.getParentFile().equals(root)) {
        // do nothing here - we'll handle the .html file when we get to the dir
        return;
      }
      if (f.getName().equals("template.html") && f.getParentFile().equals(root)) {
        // We don't list the document template
        return;
      }
      // if there's a directory with the same name
      File dir = new File(f.getParentFile(), nameWithoutExension);
      if (dir.exists()) { // do nothing here - we'll handle the .html file when we get to the dir
        return;
      }
    }
    String urlSuffix = nameWithoutExension;
    String path = url+"/"+urlSuffix;
    File documentFile = f;
    if (f.isDirectory()) { // get the title from the file with the same name, if any
      documentFile = new File(f.getParentFile(), f.getName() + ".html");
      if (f == root) { // root directory has a different document file
        documentFile = new File(f, ".html");
        path = "";
      }
    }
    boolean listAsDirectory = false;
    if (f.isDirectory()) {
      // check it actually has files in it
      listAsDirectory = f.listFiles(
        child -> child.getName().endsWith(".html") || child.isDirectory()).length > 0;
    }
    String id = path.replaceAll("^/","") // no leading slash
      .replace("/","→") // no slashes
      .replace("\"", "$quot;"); // ensure it can be an HTML attribute
    if (!listAsDirectory) { // file
      writer.print("<div id=\""+id+"\">");
    } else if (f == root) {
      writer.print("<details open><summary id=\"→\">");
    } else {
      writer.print("<details><summary id=\""+id+"\">");
    }
    writer.print("<a href=\""+baseUrl+path+"\">");
    writer.print(title(documentFile));
    writer.print("</a>");
    if (!listAsDirectory) { // file
      writer.println("</div>");
    } else { // traverse the directory
      writer.println("</summary>");
      File[] children = f.listFiles();
      Arrays.sort(children);
      for (File child : children) {
        indexDir(root, child, baseUrl, path, writer);
      }
      writer.println("</details>");
    }
  } // end of indexDir()
  
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
