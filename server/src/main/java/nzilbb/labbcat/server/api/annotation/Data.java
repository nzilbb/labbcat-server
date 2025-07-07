//
// Copyright 2025 New Zealand Institute of Language, Brain and Behaviour, 
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

package nzilbb.labbcat.server.api.annotation;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import nzilbb.ag.Anchor;
import nzilbb.ag.Annotation;
import nzilbb.ag.Graph;
import nzilbb.ag.GraphStoreAdministration;
import nzilbb.ag.Layer;
import nzilbb.labbcat.server.api.APIRequestHandler;
import nzilbb.labbcat.server.api.RequestParameters;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.util.IO;

/**
 * <tt>/api/annotation/data</tt>
 * : Accesses binary data for annotations.
 * <p> Some layers have have their type set to a MIME type, e.g. "image/png". On those
 * layers, each annotation is associated with binary data of the given MIME type, e.g. an
 * PNG image. This endpoint provides access to that data.
 * <p> The request method can be <b> GET </b> or <b> POST </b>
 *   <dl>
 *     <dt><span class="paramLabel">Parameters:</span></dt>
 *     <dd><code>id</code> - One or more annotations IDs.</dd>
 *     <dd><code>expression</code> - An expression to determine the annotations IDs.
 *                             e.g. "['e_144_17346', 'e_144_17347'].includes(id)"</dd>
 *     <dd><code>name</code> - Optional name of the collection.</dd>
 *     <dd><code>show</code> - Optional parameter for suppressing disposition/filename headers,
 *                             intended to allow easy display of the image in a browser,
 *                             without the browser offering to download the file.</dd>
 *   </dl>
 * <p> One of <code>id</code> or <code>expression</code> must be specified.
 * <p><b>Output</b>: If one annotation ID is specified, the associated data file is
 * returned. If multiple IDs are specified, a ZIP file is returned, which contains all the
 * data files.
 * @author Robert Fromont
 */
public class Data extends APIRequestHandler { // TODO unit test
   
  private static DecimalFormat offsetFormat = new DecimalFormat(
    // force the locale to something with . as the decimal separator
    "0.000", new DecimalFormatSymbols(Locale.UK));
  
  /**
   * Constructor
   */
  public Data() {
  } // end of constructor

  // Servlet methods
   
  /**
   * The GET or POST method for the servlet.
   * @param parameters Request parameter map.
   * @param out Response body stream.
   * @param contentType Receives the content type for specification in the response headers.
   * @param fileName Receives the filename for specification in the response headers.
   * @param httpStatus Receives the response status code, in case of error.
   */
  public void post(RequestParameters parameters, OutputStream out, Consumer<String> contentType,
                   Consumer<String> fileName, Consumer<Integer> httpStatus) {
    try {
         
      final SqlGraphStoreAdministration store = getStore();
      ZipOutputStream zipOut = null;
      try {
        // arrays of transcripts and delimiters
        String[] ids = parameters.getStrings("id");
        if (ids.length == 0) {
          String expression = parameters.getString("expression");
          if (expression == null) {
            contentType.accept("text/plain;charset=UTF-8");
            httpStatus.accept(SC_BAD_REQUEST);
            try {
              out.write(localize("No ID specified.").getBytes());
            } catch(IOException exception) {}
            return;
          }
          Annotation[] annotations = store.getMatchingAnnotations(expression);
          if (annotations.length == 0) {
            contentType.accept("text/plain;charset=UTF-8");
            httpStatus.accept(SC_NOT_FOUND);
            try {
              out.write(localize("There are no matching IDs.").getBytes());
            } catch(IOException exception) {}
            return;
          }
          ids = Arrays.stream(annotations).map(a->a.getId()).toArray(String[]::new); 
        }
        Layer layer = null; // they're probably all from the same layer, but if not, ok
        int validIdCount = 0;
        for (String id : ids ) {
          Annotation[] matches = store.getMatchingAnnotations("id == '"+esc(id)+"'", 1, 0, true);
          if (matches.length > 0) {
            validIdCount++;
            Annotation annotation = matches[0];
            Graph graph = annotation.getGraph();
            if (layer == null || !annotation.getLayerId().equals(layer.getId())) {
              layer = store.getLayer(annotation.getLayerId());
            }
            String[] oneAnchorId = { annotation.getStartId() };
            String[] anchorIds = { annotation.getStartId(), annotation.getEndId() };
            if (anchorIds[0].equals(anchorIds[1])) {
              anchorIds = oneAnchorId;
            }
            File file = store.annotationDataFile(annotation, graph, layer.getType());
            if (file.exists()) {
              // we need the anchors to determine the name of the file
              Anchor[] anchors = store.getAnchors(graph.getId(), anchorIds);
              StringBuilder name = new StringBuilder()
                .append(graph.getId())
                .append("_[")
                .append(IO.SafeFileNameUrl(layer.getId()))
                .append("]__")
                .append(offsetFormat.format(anchors[0].getOffset()));
              if (anchors.length > 1) {
                name.append("-")
                  .append(offsetFormat.format(anchors[1].getOffset()));
              }
              name.append(".").append(IO.Extension(file));
              
              if (ids.length == 1) { // this is the one and only file
                // stream out the file
                contentType.accept(layer.getType());
                if (parameters.get("show") == null) { // not showing in the browser
                  fileName.accept(name.toString());
                }
                IO.Pump​(new FileInputStream(file), out);
                return;
              } else { // multiple files
                if (zipOut == null) { // no ZIP stream yet
                  contentType.accept("application/zip");
                  String zipName = Optional.ofNullable(parameters.getString("name"))
                    .orElse(layer.getId());
                  fileName.accept(zipName + ".zip");
                  zipOut = new ZipOutputStream(out);
                } // start ZIP stream
                zipOut.putNextEntry(
                  new ZipEntry(name.toString()));
                // pump the data into it
                IO.Pump(new FileInputStream(file), zipOut, false);
              } // multiple files
            }
          }  // annotation exists
        } // next anntation ID

        if (validIdCount == 0) {
          // no IDs were valid
          contentType.accept("text/plain;charset=UTF-8");
          httpStatus.accept(SC_NOT_FOUND);
          try {
            out.write(localize("Invalid ID: {0}", ids[0] + (ids.length == 1?"":"...")).getBytes());
          } catch(IOException exception) {}
          return;
        }
        
      } finally {
        cacheStore(store);
        if (zipOut != null) zipOut.close();
      }
    } catch(Exception ex) {
      contentType.accept("text/plain;charset=UTF-8");
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      try {
        out.write(ex.toString().getBytes());
      } catch(IOException exception) {
        context.servletLog("Files.get: could not report unhandled exception: " + ex);
        ex.printStackTrace(System.err);
      }
    }
  }
} // end of class Data
