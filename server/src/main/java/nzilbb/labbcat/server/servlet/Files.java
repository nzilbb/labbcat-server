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

package nzilbb.labbcat.server.servlet;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.util.Vector;
import java.util.zip.*;
import javax.servlet.*; // d:/jakarta-tomcat-5.0.28/common/lib/servlet-api.jar
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import nzilbb.ag.Graph;
import nzilbb.ag.GraphNotFoundException;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.util.IO;

/**
 * <tt>/api/files</tt>
 * : Exports media/transcript files for specified transcripts.
 * <p> Batch access to transcript files, or media files, stored on the server. 
 * <p> The request method can be <b> GET </b> or <b> POST </b>
 *   <dl>
 *     <dt><span class="paramLabel">Parameters:</span></dt>
 *     <dd><code>id</code> - One or more graph IDs.</dd>
 *     <dd><code>query</code> - Graph QL expression to identify the graph IDs, if no
 *         <var>id</var> parameter is supplied.</dd>
 *     <dd><code>mimeType</code> - Optional content-type of media to export; if not
 *         specified, original transcript files are exported instead of media.</dd>
 *     <dd><code>trackSuffix</code> - Optional track suffix for the media to export.</dd>
 *     <dd><code>name</code> - Optional name of the collection.</dd>
 *   </dl>
 * <p><b>Output</b>: The media or transcript files for the given transcripts.  
 * <p> This may be a single file or multiple files, depending on how many transcripts are
 * specified. If there is only one, the file in returned as the response to  the request.
 * If there are more than one, the response is a zip file containing the output files. 
 * @author Robert Fromont
 */
@WebServlet({"/api/files"} )
public class Files extends LabbcatServlet { // TODO unit test
   
  /**
   * Constructor
   */
  public Files() {
  } // end of constructor

  // Servlet methods
   
  /**
   * The GET method for the servlet.
   * @param request HTTP request
   * @param response HTTP response
   */
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
      
    ServletContext context = getServletContext();
      
    // check parameters
    String mimeType = request.getParameter("mimeType");
    if (mimeType == null || mimeType.trim().length() == 0) mimeType = "";
    
    String trackSuffix = request.getParameter("trackSuffix");
    if (trackSuffix == null || trackSuffix.trim().length() == 0) trackSuffix = "";

    String name = request.getParameter("name");
    if (name == null || name.trim().length() == 0) {
      if (mimeType == null || mimeType.length() == 0) {
        name = "transcripts";
      } else {
        name = "media";
      }
    }
    name = IO.SafeFileNameUrl(name.trim());
      
    try {
         
      SqlGraphStoreAdministration store = getStore(request);
      // we want local, not remote, file URLs, so unset baseUrl temporarily
      String baseUrl = store.getBaseUrl();
      store.setBaseUrl(null);
      // arrays of transcripts and delimiters
      String[] id = request.getParameterValues("id");
      if (id == null) {
        // have they specified a query?
        String query = request.getParameter("query");
        id = store.getMatchingTranscriptIds(query);
        if (id.length == 0) {
          response.sendError(400, "No graph IDs specified");
          return;
        }
      } // no "id" parameter values

      File zipFile = null;
      Vector<File> files = new Vector<File>();
      try {

        for (String transcriptId : id) {
          try {
            String file = mimeType.length() > 0?
              store.getMedia(transcriptId, trackSuffix, mimeType) // media
              :store.getSource(transcriptId); // transcripts
            if (file != null) {
              files.add(new File(new URI(file)));
            } // file was returned
          } catch(GraphNotFoundException notFound) {
          } catch(Exception x) {
            response.sendError(500, "Could not get file for " + transcriptId + ": " + x);
              return;
          }
        } // gnext ID
        
        if (files.size() == 0) {
          response.sendError(HttpServletResponse.SC_NOT_FOUND, "No files were generated");
        }
        else if (files.size() == 1) { // one file only
          // don't zip a single file, just return the file
          response.setContentType(mimeType);
          File file = files.firstElement();
          ResponseAttachmentName(request, response, file.getName());               
          IO.Pump(new FileInputStream(file), response.getOutputStream());
        } else { // multiple files
          response.setContentType("application/zip");
          ResponseAttachmentName(request, response, IO.SafeFileNameUrl(name) + ".zip");
          
          // create a stream to pump from
          PipedInputStream inStream = new PipedInputStream();
          final PipedOutputStream outStream = new PipedOutputStream(inStream);
          
          // start a new thread to extract the data and stream it back
          new Thread(new Runnable() {
              public void run() {
                try {
                  ZipOutputStream zipOut = new ZipOutputStream(outStream);
                  
                  // for each file
                  for (File file : files) {
                    try {
                      // create the zip entry
                      zipOut.putNextEntry(
                        new ZipEntry(IO.SafeFileNameUrl(file.getName())));
                      
                      IO.Pump(new FileInputStream(file), zipOut, false);
                    } catch (ZipException zx) {
                    }
                  } // next file
                  try {
                    zipOut.close();
                  } catch(Exception exception) {
                    System.err.println("Files: Cannot close ZIP file: " + exception);
                  }
                } catch(Exception exception) {
                  System.err.println("Files: open zip stream: " + exception);
                }
              }
            }).start();
          
          // send headers immediately, so that the browser shows the 'save' prompt
          response.getOutputStream().flush();
          
          IO.Pump(inStream, response.getOutputStream());
        } // multiple files
      } finally {
        // restore the baseUrl setting
        store.setBaseUrl(baseUrl);
        // return the store to the cache
        cacheStore(store);
      }
    } catch(Exception ex) {
      throw new ServletException(ex);
    }
  }
  
  private static final long serialVersionUID = -1;
} // end of class Files
