//
// Copyright 2021-2025 New Zealand Institute of Language, Brain and Behaviour, 
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

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.util.function.Consumer;
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
 *                              layers, or "transcript", "episode", or "corpus" .</dd>
 *     <dd><code>name</code> - Optional name of the file.</dd>
 *     <dd><code>csvFieldDelimiter</code> - Optional delimiter for CSV file (comma is used
 *                                          by default).</dd>
 *   </dl>
 * <p><b>Output</b>: A CSV file with a column for each attribute, and a row for each transcript. 
 * @author Robert Fromont
 */
public class Attributes extends APIRequestHandler { // TODO unit test
   
  /**
   * Constructor
   */
  public Attributes() {
  } // end of constructor
  
  // Servlet methods
  
  /**
   * The GET method for the servlet.
   * @param parameters Request parameter map.
   * @param out Response body stream.
   * @param fileName Receives the filename for specification in the response headers.
   * @param httpStatus Receives the response status code, in case or error.
   */
  public void get(RequestParameters parameters, OutputStream out, Consumer<String> fileName, Consumer<Integer> httpStatus) {
    
    // check parameters
    String[] nameOnly = { "transcript" };
    String[] selectedAttributes = parameters.getStrings("layer");
    if (selectedAttributes == null || selectedAttributes.length == 0) {
      String layers = parameters.getString("layers");
      if (layers != null) {
        // R can't send multiple parameters with the same value, so the workaround is
        // to send one parameter with the values delimited by newline
        selectedAttributes = layers.split("\n");
      }
    }
    if (selectedAttributes == null || selectedAttributes.length == 0) {
      selectedAttributes = nameOnly;
    }
    
    String name = parameters.getString("name");
    if (name == null || name.trim().length() == 0) name = "transcripts";
    name = name.trim();
    if (!name.endsWith(".csv")) {
      name += ".csv";
    }
    
    try {
      
      SqlGraphStoreAdministration store = getStore();
      // arrays of transcripts and delimiters
      String[] id = parameters.getStrings("id");
      if (id.length == 0) {
        String ids = parameters.getString("ids");
        if (ids != null) {
          // R can't send multiple parameters with the same value, so the workaround is
          // to send one parameter with the values delimited by newline
          id = ids.split("\n");
        } else {
          // have they specified a query?
          String query = parameters.getString("query");
          if (query != null) {
            id = store.getMatchingTranscriptIds(query);
          } // "query" parameter
        } // no "ids" parameter
      } // no "id" parameter values
      if (id == null || id.length == 0) {
        httpStatus.accept(SC_BAD_REQUEST);
        out.write("No graph IDs specified".getBytes());
        return;
      }
      
      fileName.accept(IO.SafeFileNameUrl(name));
      
      try {
        char delimiter = ',';
        if (parameters.containsKey("csvFieldDelimiter")
            && parameters.getString("csvFieldDelimiter").length() > 0) {
          delimiter = parameters.getString("csvFieldDelimiter").charAt(0);
        }
        
        CSVPrinter csvOut = new CSVPrinter(
          new OutputStreamWriter(out, "UTF-8"), CSVFormat.EXCEL.withDelimiter(delimiter));
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
            context.servletLog("Attributes: Cannot get transcript " + transcriptId + ": " + x);
          }
        } // next graph ID
        try {
          csvOut.close();
        } catch(Exception exception) {
          context.servletLog("Attributes: Cannot close CSV file: " + exception);
        }
      } catch(Exception exception) {
        context.servletLog("Attributes: open csv stream: " + exception);
      } finally {
        cacheStore(store);
      }
    } catch(Exception ex) {
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      try {
        out.write(("\n"+ex).getBytes());
      } catch(IOException exception) {}
    }
  }
  
  private static final long serialVersionUID = -1;
} // end of class Attributes
