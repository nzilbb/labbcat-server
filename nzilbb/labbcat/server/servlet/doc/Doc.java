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
 * Provides CRUD operations for all files/directories under /doc/*.
 * <p> This allows for arbitrary documentation of a corpus, using wysiwiki, which allows
 * admin users to create, edit, and delete pages, providing read-only access to others.
 * <p> URLs are assumed to identify a file with a ".html" suffix. 
 * e.g. GETting <q>http://tld/doc/foo/bar</q> with return the contents of the 
 * <q>docs/foo/bar.html</q> file. PUTting to the same URL updates the contents of that file,
 * and DELETEing deletes the file.
 * <p> For files that don't exist, 404 is returned, but also the body of a <q>template.html</q> 
 * file if the user can edit. This way, the editing user can use the template as a starting point, 
 * and PUT the body, with their changes, to create the file.
 * <p> GETting <q>http://tld/doc/index</q> returns an HTML document that represents the whole
 * tree of documents and subdirectories, with corresponding &lt;a&gt; links.
 * <p> POSTting a file to any URL results in that file being written  
 * - i.e. POSTing to the file <q>http://tld/doc/foo/bar.png</q> will 
 * result in the creation of a file called <q>http://tld/doc/foo/bar.png</q>, and a relative 
 * URL to it is returned as part of the JSON-encoded response to the POST request.
 * @author Robert Fromont robert@fromont.net.nz
 */
@WebServlet({"/doc/*"})
public class Doc extends LabbcatServlet {

  DocumentBuilderFactory documentBuilderFactory;
  TransformerFactory transformerFactory;
  
  /** Constructor */
  public Doc() {
    documentBuilderFactory = DocumentBuilderFactory.newInstance();
    transformerFactory = TransformerFactory.newInstance();
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
      if (!html.exists()) { // not an HTML document or directory
        File asset = new File(getServletContext().getRealPath("/doc"+request.getPathInfo()));
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
          // return 404, but also a template for creating a new document
          html = new File(getServletContext().getRealPath("/doc/template.html"));
        }
      }
      if (html.getName().equals("template.html")) { // template
        // stream out the contents, substituting "${base}" for a path to the root directory
        String context = request.getPathInfo();
        if ("/template.html".equals(request.getPathInfo())
            && request.getHeader("Referer") != null) {
          context = request.getHeader("Referer")
            .substring(baseUrl(request).length() + 4); // remove the full URL prefix
        }
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
      try {
        File root = new File(getServletContext().getRealPath("/doc"));
        Document index = compileIndex(root, request);
        DOMSource source = new DOMSource(index);
        StreamResult result =  new StreamResult(response.getWriter());
        Transformer xmlTransformer = transformerFactory.newTransformer();
        xmlTransformer.transform(source, result);
      } catch (Exception x) {
        response.setContentType("application/json");
        x.printStackTrace(System.err);
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        JsonWriter writer = Json.createWriter(response.getWriter());
        writer.writeObject(failureResult(request, x.getMessage()));
        writer.close();      
      }
    } // request for index
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
      if ("/template".equals(request.getPathInfo()) // not allowed to edit template
          || "/template.html".equals(request.getPathInfo())
          || !canEdit(request)) { // only admin users allowed to edit anything
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return;
      }
      File html = file(request);
      String id = IO.WithoutExtension(html);
      if (id.length() == 0) id = "→";
      
      // get the file's item in the index
      File root = new File(getServletContext().getRealPath("/doc"));
      Document index = loadIndexXhtml(root, html.getParentFile());
      XPath xpath = XPathFactory.newInstance().newXPath();
      Element item = (Element)xpath.evaluate("//*[@id='"+id+"']", index, XPathConstants.NODE);
      
      String move = request.getParameter("move");
      if (move == null) { // PUT full content
                
        // back up the old version
        backup(html);
        
        // write the new version
        IO.Pump(request.getInputStream(), new FileOutputStream(html));
        
        // ensure it's indexed with the correct title
        if (item == null) { // not indexed yet
          addItemToIndex(index, index.getFirstChild(), html);
          saveIndexXhtml(index, html.getParentFile());
        } else { // file is in the index        
          Element a = (Element)item.getFirstChild();
          String currentTitle = a.getTextContent();
          String newTitle = title(html);
          if (!currentTitle.equals(newTitle)) {
            a.setTextContent(newTitle);
            saveIndexXhtml(index, html.getParentFile());
          }
        }
        
        // send JSON response
        JsonWriter writer = Json.createWriter(response.getWriter());
        writer.writeObject(successResult(request, null, "OK")); // TODO i18n?
        writer.close();
      } else { // move request - only edit the position in the index
        if (item != null) { // file is in the index
          if (item.getTagName().equals("summary")) { // it's a directory item
            // we will move the parent <detail> element
            item = (Element)item.getParentNode();
          }
          // edit the index
          Node previous = item;
          Node next = item.getNextSibling();
          Node parent = item.getParentNode();
          if (move.equals("up")) { // move up
            next = item;
            previous = item.getPreviousSibling();
          } // move up
          if (previous != null && next != null) { // swap them
            parent.removeChild(next);
            parent.insertBefore(next, previous);
            saveIndexXhtml(index, html.getParentFile());
          }
          // send JSON response
          JsonWriter writer = Json.createWriter(response.getWriter());
          writer.writeObject(successResult(request, null, "OK")); // TODO i18n?
          writer.close();
        } else { // not in index
          response.setStatus(HttpServletResponse.SC_NOT_FOUND);
          JsonWriter writer = Json.createWriter(response.getWriter());
          writer.writeObject(failureResult(request, "Record not found: {0}", id));
          writer.close();
        } // not in index
      } // move request
      
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

        // remove from index
        String id = IO.WithoutExtension(html);
        if (id.length() > 0) { // but only if it's not the root document
          File root = new File(getServletContext().getRealPath("/doc"));
          Document index = loadIndexXhtml(root, html.getParentFile());
          XPath xpath = XPathFactory.newInstance().newXPath();
          Element item = (Element)xpath.evaluate("//*[@id='"+id+"']", index, XPathConstants.NODE);
          if (item != null) { // it's in the index
            if (item.getTagName().equals("summary")) { // it's a directory item
              File dir = new File(html.getParentFile(), IO.WithoutExtension(html));
              if (!dir.exists()
                  || dir.listFiles(
                    file -> file.getName().endsWith(".html") || file.isDirectory()).length == 0) {
                // remove item (technically the parent of the item in this case)              
                item.getParentNode().getParentNode().removeChild(item.getParentNode());
                saveIndexXhtml(index, html.getParentFile());
              }
            } else { // it's an HTML document item
              item.getParentNode().removeChild(item);
              saveIndexXhtml(index, html.getParentFile());
            }
          } // it's in the index
        } // it's not the root document

        // send JSON response
        JsonWriter writer = Json.createWriter(response.getWriter());
        writer.writeObject(successResult(request, null, "OK")); // TODO i18n?
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
          
          File file = new File(getServletContext().getRealPath("/doc"+request.getPathInfo()));
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
          model.add("url", "."+request.getPathInfo().replaceAll("[^/]+$", file.getName()));
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
   * @param request The request.
   * @return An XML document representing the structure of the documents, where each
   * document is encosed in an HTML &lt;a&gt; tag, with directory links enclosed in 
   * &lt;details&gt;&lt;summary&gt; tags, and each file link enclosed in a &lt;div&gt;.
   */
  protected Document compileIndex(File root, HttpServletRequest request) throws Exception {
    XPath xpath = XPathFactory.newInstance().newXPath();
    // recursively construct the index document
    Document index = compileIndex(root, root, xpath);
    // update URLs
    String baseUrl = baseUrl(request) + "/doc/";
    NodeList ids = (NodeList)xpath.evaluate("//@id", index, XPathConstants.NODESET);
    for (int i = 0; i < ids.getLength(); i++) {
      Attr id = (Attr)ids.item(i);
      try {
        Node element = id.getOwnerElement();
        Node a = element.getFirstChild();
        Node href = a.getAttributes().getNamedItem("href");
        if (id.getNodeValue().equals("→")) { // root dir
          href.setNodeValue(baseUrl);
        } else {
          href.setNodeValue(baseUrl + id.getNodeValue().replace("→", "/"));
        }
      } catch(Exception x) {
        log("Couldn't set URL for node with id " + id.getNodeValue() + ": "+x);
      }
    } // next ID
    return index;
  }
  /**
   * Generate HTML for a recursive listing of the given file/directory.
   * @param root The root directory.
   * @param dir The directory to list the index of.
   * @return An XML document representing the structure of the documents, where each
   * document is encosed in an HTML &lt;a&gt; tag, with directory links enclosed in 
   * &lt;details&gt;&lt;summary&gt; tags, and each file link enclosed in a &lt;div&gt;.
   */
  protected Document compileIndex(File root, File dir, XPath xpath) throws Exception {
    DocumentBuilder xmlParser = documentBuilderFactory.newDocumentBuilder();
    // get the index file
    File xhtml = new File(dir, "index.xhtml");
    if (!xhtml.exists()) {
      indexDir(root, dir);
    } // generate it
    Document index = xmlParser.parse(xhtml);
    
    // for each <summary> entry, which represents a subdirectory
    NodeList summaries = (NodeList)xpath.evaluate("//summary", index, XPathConstants.NODESET);
    for (int s = 0; s < summaries.getLength(); s++) {
      Node summary = summaries.item(s);
      String id = summary.getAttributes().getNamedItem("id").getNodeValue();
      if (id.length() == 0) continue; // this (root) directory
      Node details = summary.getParentNode();
      
      // traverse the directory
      File subDir = new File(dir, id);
      
      if (subDir.exists()) {
        Document subIndex = compileIndex(root, subDir, xpath);
        
        // prefix all ids with the path
        NodeList subIds = (NodeList)xpath.evaluate("//@id", subIndex, XPathConstants.NODESET);
        for (int i = 0; i < subIds.getLength(); i++) {
          Node subId = subIds.item(i);
          subId.setNodeValue(id + "→" + subId.getNodeValue());
        } // next subdirectory entry

        // insert the entries into this summary
        Node subDetails = subIndex.getFirstChild();
        NodeList children = subDetails.getChildNodes();
        for (int c = 0; c < children.getLength(); c++) {
          Node child = children.item(c);
          child = index.importNode(child, true);
          details.appendChild(child);
        } // next child        
      } // subdir exists
    } // next summary/subdir    
        
    return index;
  }
  
  /**
   * Loads the index.xhtml file for the given directory, creating it if necessary.
   * @param root The root directory.
   * @param dir The directory whose index.xhtml file should be loaded.
   * @return The XHTML document.
   */
  protected Document loadIndexXhtml(File root, File dir) throws Exception {
    File xhtml = new File(dir, "index.xhtml");
    if (!xhtml.exists()) {
      // generate it
      indexDir(root, dir);
    }
    DocumentBuilder xmlParser = documentBuilderFactory.newDocumentBuilder();
    return xmlParser.parse(xhtml);
  } // end of loadIndexXhtml()
  
  /**
   * Saves the given document as the index.xhtml for the given directory.
   * @param index
   * @param dir
   */
  protected void saveIndexXhtml(Document index, File dir) throws Exception {
    File xhtml = new File(dir, "index.xhtml");
    DOMSource source = new DOMSource(index);
    StreamResult result =  new StreamResult(xhtml);
    Transformer xmlTransformer = transformerFactory.newTransformer();
    xmlTransformer.transform(source, result);    
  } // end of saveIndexXhtml()

  /**
   * Generate XHTML index file for the given directory.
   * @param root The root directory.
   * @param dir The file or directory to list.
   * @param baseUrl Base URL for the servlet.
   * @param url relative URL for the parent.
   * @param writer The response writer.
   */
  protected void indexDir(File root, File dir) throws Exception {
    if (!dir.exists()) { // file's directory doesn't exist
      XPath xpath = XPathFactory.newInstance().newXPath();
      // create all necessary directories
      Files.createDirectory(dir.toPath());
      // ensure the parent directory is in the grandparent directory's index
      File parentDir = dir.getParentFile();
      Document parentIndex = loadIndexXhtml(root, parentDir);
      Element parentItem = (Element)xpath.evaluate(
        "//*[@id='"+dir.getName()+"']", parentIndex, XPathConstants.NODE);
      if (parentItem == null) { // not indexed yet
        addItemToIndex(parentIndex, parentIndex.getFirstChild(), dir);
        saveIndexXhtml(parentIndex, parentDir);
      } else { // dir is in the parentIndex
        // check that it's a details/summary entry, not a div
        if (parentItem.getTagName().equals("div")) {
          // convert item from div to details/summary
          Element a = (Element)parentItem.getFirstChild();
          parentItem.removeChild(a);              
          Element summary = parentIndex.createElement("summary");
          summary.appendChild(a);
          summary.setAttribute("id", dir.getName());
          Element details = parentIndex.createElement("details");
          details.appendChild(summary);
          parentItem.getParentNode().insertBefore(details, parentItem);
          parentItem.getParentNode().removeChild(parentItem);
          saveIndexXhtml(parentIndex, parentDir);
        } // parentItem is a div (representing a file)
      } // dir is in the parentIndex
    } // file's directory doesn't exist
    DocumentBuilder docBuilder = documentBuilderFactory.newDocumentBuilder();
    Document index = docBuilder.newDocument();
    Element details = index.createElement("details");
    if (dir.equals(root)) { // details should be open at root
      details.setAttribute("open", "true");
    }
    index.appendChild(details);
    File[] children = dir.listFiles();
    Arrays.sort(children);
    for (File child : children) {
      // don't list the template
      if (dir.equals(root) && child.getName().equals("template.html")) continue;
      
      String id = IO.WithoutExtension(child);
      String title = id;
      if (id.length() == 0) { // root listing
        id = "→";
        title = "Documentation";
      }
      if (child.isDirectory()) { // subdirectory
        File directoryDoc = new File(dir, child.getName()+".html");
        if (directoryDoc.exists()) {
          title = title(directoryDoc);
        }
      } else if (child.getName().endsWith(".html")) { // document        
        // if there's a dirctory with the same name, skip the file here
        File correspondingSubdirectory = new File(dir, IO.WithoutExtension(child));
        if (correspondingSubdirectory.exists()
            && !child.getName().equals(".html")) { // (but list ".html" which is the root doc)
          continue;
        }
        // otherwise get the title
        title = title(child);
      } else { // skip any other files
        continue;
      }
      addItemToIndex(index, details, child);
    } // next child
    saveIndexXhtml(index, dir);
  } // end of indexDir()
  
  /**
   * Adds the given fild as an index item.
   * @param details
   * @param item The html file or the directory to add.
   * @param title
   */
  protected void addItemToIndex(Document index, Node details, File item) {
    File html = item;
    String id = IO.WithoutExtension(item);
    File dir = new File(item.getParentFile(), id);
    if (id.length() == 0) { // root listing
      id = "→";
    } else if (item.isDirectory()) {
      dir = item;
      html = new File(item.getParentFile(), dir.getName() + ".html");
    }
    boolean listAsDirectory = false;
    if (dir.exists()) {
      // check it actually has files in it
      listAsDirectory = dir.listFiles(
        file -> file.getName().endsWith(".html") || file.isDirectory()).length > 0;
    }
    Element a = index.createElement("a");
    a.setTextContent(title(html));
    a.setAttribute("href","");
    if (item.getName().equals(".html")) { // root document - just the summary
      Element summary = index.createElement("summary");
      summary.appendChild(a);
      summary.setAttribute("id", id);
      details.appendChild(summary);
    } else if (listAsDirectory) {
      // in details/summary
      Element summary = index.createElement("summary");
      summary.appendChild(a);
      summary.setAttribute("id", id);
      Element childDetails = index.createElement("details");
      childDetails.appendChild(summary);
      details.appendChild(childDetails);
    } else { // .html file
      // in div
      Element div = index.createElement("div");
      div.appendChild(a);
      div.setAttribute("id", id);
      details.appendChild(div);
    } // .html file
  } // end of addItemToIndex()
  
  static final Pattern titlePattern = Pattern.compile(".*<title>(.*)</title>.*");
  /**
   * Gets the title of the given document file.
   * @param doc A .html document.
   * @return The contents of the &lt;title&gt; tag in the file, or the file name without
   * the suffix, if there is none.
   */
  public static String title(File html) {
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
        //log("Doc.title("+html.getPath()+"): " + exception.toString());
      }
    } // file exists and is .html
    return IO.WithoutExtension(html);
  } // end of title()

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
          log("Doc.backup("+html.getPath()+"): " + exception.toString());
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
