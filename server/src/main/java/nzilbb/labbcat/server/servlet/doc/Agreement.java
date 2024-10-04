//
// Copyright 2024 New Zealand Institute of Language, Brain and Behaviour, 
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLConnection;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.*;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.json.JsonWriter;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.labbcat.server.servlet.LabbcatServlet;
import nzilbb.util.IO;
import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.disk.*;
import org.apache.commons.fileupload.servlet.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Provides CRUD operations for the agreement.html file, which is the license agreement
 * that users agree to the first time they log in.
 * @author Robert Fromont robert@fromont.net.nz
 */
@WebServlet({"/agreement.html", "/agreement/*"})
public class Agreement extends LabbcatServlet {
  
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
   */
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    response.setCharacterEncoding("UTF-8");
    if (request.getPathInfo() == null
        || request.getPathInfo().endsWith("agreement.html")) {
      response.setContentType("text/html");
      File html = new File(getServletContext().getRealPath("/agreement.html"));
      if (!html.exists()) { // not an HTML document or directory
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      } else { // existing document
        IO.Pump(new FileInputStream(html), response.getOutputStream());
      }
    } else { // an image or other asset
      File asset = new File(getServletContext().getRealPath("/agreement"+request.getPathInfo()));
      if (asset.exists() && !asset.isDirectory()) {
        // serve the content directly
        response.setContentType(
          URLConnection.guessContentTypeFromName(request.getPathInfo()));
        response.setDateHeader( // expires in a week
          "Expires", new java.util.Date().getTime() + (1000*60*60*24*7));
        IO.Pump(new FileInputStream(asset), response.getOutputStream());
        return;
      } else {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      }
    }
  }
  
  /**
   * PUT handler: Adds or updates an HTML document, or if the "move" parameter is specified,
   * the document's entry is moved in the index (in which case the HTML document itself is
   * not updated).
   */
  @Override
  protected void doPut(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    try {
      if (!canEdit(request)) { // only admin users allowed to edit anything
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return;
      }
      File html = new File(getServletContext().getRealPath("/agreement.html"));
      
      // back up the old version
      backup(html);
      
      // write the new version
      IO.Pump(request.getInputStream(), new FileOutputStream(html));
      
      // send JSON response
      JsonWriter writer = Json.createWriter(response.getWriter());
      writer.writeObject(successResult(request, null, "License agreement updated.")); // TODO i18n?
      writer.close();
      
    } catch (Exception x) {
      x.printStackTrace(System.err);
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      JsonWriter writer = Json.createWriter(response.getWriter());
      writer.writeObject(failureResult(request, x.getMessage()));
      writer.close();      
    }
  }
  
  /**
   * DELETE handler: Delete the given HTML document.
   */
  @Override
  protected void doDelete(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    try {
      if (!canEdit(request)) { // only admin users allowed to edit anything
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return;
      }
      File html = new File(getServletContext().getRealPath("/agreement.html"));
      if (!html.exists()) { // file's directory doesn't exist
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      } else {
        log("Deleting " + html.getPath());
        
        // back up the old version
        backup(html);
        
        // delete the file
        html.delete();
        
        // send JSON response
        JsonWriter writer = Json.createWriter(response.getWriter());
        writer.writeObject(successResult(request, null, "License agreement deleted.")); // TODO i18n?
        writer.close();
        
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
          
          File file = new File(getServletContext().getRealPath("/agreement"+request.getPathInfo()));
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
          fileItem.write(file);

          JsonWriter writer = Json.createWriter(response.getWriter());
          JsonObjectBuilder model = Json.createObjectBuilder();
          model.add("url", "agreement"+request.getPathInfo().replaceAll("[^/]+$", file.getName()));
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
          log("Agreement.backup("+html.getPath()+"): " + exception.toString());
        }
      }
      return true;
    }
    return false;
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
        return IsUserInRole("admin", request, store.getConnection());
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
