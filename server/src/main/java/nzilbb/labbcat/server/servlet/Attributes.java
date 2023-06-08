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

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import javax.servlet.*; // d:/jakarta-tomcat-5.0.28/common/lib/servlet-api.jar
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import nzilbb.ag.Graph;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.util.IO;
import org.apache.commons.csv.*;

/**
 * <tt>/api/attributes</tt>
 * : Exports selected attributes for specified transcripts to CSV.
 * <p> The request method can be <b> GET </b> or <b> POST </b>
 *   <dl>
 *     <dt><span class="paramLabel">Parameters:</span></dt>
 *     <dd><code>id</code> - One or more graph IDs.</dd>
 *     <dd><code>query</code> - AGQL expression to identify the graph IDs, if no
 *         <var>id</var> parameter is supplied.</dd>
 *     <dd><code>layer</code> - One or more layer IDs, representing transcript attribute
 *                              layers, or "transcript", "epsiode", or "corpus" .</dd>
 *     <dd><code>name</code> - Optional name of the file.</dd>
 *     <dd><code>csvFieldDelimiter</code> - Optional delimiter for CSV file (comma is used
 *                                          by default).</dd>
 *   </dl>
 * <p><b>Output</b>: A CSV file with a column for each attribute, and a row for each transcript. 
 * @author Robert Fromont
 */
@WebServlet({"/api/attributes"} )
public class Attributes extends LabbcatServlet { // TODO unit test
   
  /**
   * Constructor
   */
  public Attributes() {
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
    String[] nameOnly = { "transcript" };
    String[] selectedAttributes = request.getParameterValues("layer");
    if (selectedAttributes == null) {
      String layers = request.getParameter("layers");
      if (layers != null) {
        // R can't send multiple parameters with the same value, so the workaround is
        // to send one parameter with the values delimited by newline
        selectedAttributes = layers.split("\n");
      }
    }
    if (selectedAttributes == null) {
      selectedAttributes = nameOnly;
    }

    String name = request.getParameter("name");
    if (name == null || name.trim().length() == 0) name = "list";
    name = IO.SafeFileNameUrl(name.trim());
      
    try {
      
      SqlGraphStoreAdministration store = getStore(request);
      // arrays of transcripts and delimiters
      String[] id = request.getParameterValues("id");
      if (id == null) {
        String ids = request.getParameter("ids");
        if (ids != null) {
          // R can't send multiple parameters with the same value, so the workaround is
          // to send one parameter with the values delimited by newline
          id = ids.split("\n");
        } else {
          // have they specified a query?
          String query = request.getParameter("query");
          if (query != null) {
            id = store.getMatchingTranscriptIds(query);
          } // "query" parameter
        } // no "ids" parameter
      } // no "id" parameter values
      if (id == null || id.length == 0) {
        response.sendError(400, "No graph IDs specified");
        return;
      }

      response.setContentType("text/csv");
      response.setHeader("Content-Disposition", "attachment; filename="+name+".csv");
      // send headers immediately, so that the browser shows the 'save' prompt
      response.getOutputStream().flush();
      
      try {
        char delimiter = ',';
        if (request.getParameter("csvFieldDelimiter") != null
            && request.getParameter("csvFieldDelimiter").length() > 0) {
          delimiter = request.getParameter("csvFieldDelimiter").charAt(0);
        }
        
        CSVPrinter csvOut = new CSVPrinter(
          new OutputStreamWriter(
            response.getOutputStream(), "UTF-8"), CSVFormat.EXCEL.withDelimiter(delimiter));
        // write headers
        for (String layer : selectedAttributes) csvOut.print(layer);
        csvOut.println();
        for (String transcriptId : id) {
          try {
            Graph graph = store.getTranscript(transcriptId, selectedAttributes);
            for (String layer : selectedAttributes) {
              try {
                StringBuffer value = new StringBuffer();
                for (String label : graph.labels(layer)) {
                  if (value.length() > 0) value.append("\n");
                  value.append(label);
                }
                csvOut.print(value);
              } catch(NullPointerException exception) {
                csvOut.print("");
              }
            } // next layer
            csvOut.println();
          } catch(Exception x) {
            System.err.println("Attributes: Cannot get transcript " + transcriptId + ": " + x);
          }
        } // next graph ID
        try {
          csvOut.close();
        } catch(Exception exception) {
          System.err.println("Attributes: Cannot close CSV file: " + exception);
        }
      } catch(Exception exception) {
        System.err.println("Attributes: open csv stream: " + exception);
      } finally {
        cacheStore(store);
      }
    } catch(Exception ex) {
      throw new ServletException(ex);
    }
  }
  
  private static final long serialVersionUID = -1;
} // end of class Attributes
