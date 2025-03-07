//
// Copyright 2019-2025 New Zealand Institute of Language, Brain and Behaviour, 
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

package nzilbb.labbcat.server.api.serialize;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;
import javax.servlet.*; // d:/jakarta-tomcat-5.0.28/common/lib/servlet-api.jar
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import javax.xml.parsers.*;
import javax.xml.xpath.*;
import nzilbb.ag.Graph;
import nzilbb.ag.GraphStoreAdministration;
import nzilbb.ag.Schema;
import nzilbb.ag.StoreException;
import nzilbb.ag.PermissionException;
import nzilbb.ag.serialize.GraphSerializer;
import nzilbb.ag.serialize.SerializationException;
import nzilbb.ag.serialize.SerializerNotConfiguredException;
import nzilbb.ag.serialize.util.ConfigurationHelper;
import nzilbb.ag.serialize.util.NamedStream;
import nzilbb.ag.serialize.util.Utility;
import nzilbb.configure.ParameterSet;
import nzilbb.labbcat.server.api.APIRequestHandler;
import nzilbb.labbcat.server.api.RequestParameters;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.labbcat.server.task.Task;
import nzilbb.util.IO;
import org.w3c.dom.*;
import org.xml.sax.*;

/**
 * <tt>/api/serialize/graphs</tt>
 * : Converts transcripts to specific formats.
 * <p> Converts transcripts to annotation file formats. 
 * <p> The request method can be <b> GET </b> or <b> POST </b>
 *   <dl>
 *     <dt><span class="paramLabel">Parameters:</span></dt>
 *     <dd><code>mimeType</code> - Content-type of the format to serialize to.</dd>
 *     <dd><code>layerId</code> - A list of layer IDs to include in the serialization.</dd>
 *     <dd><code>id</code> - One or more graph IDs.</dd>
 *     <dd><code>query</code> - Graph QL expression to identify the graph IDs, if no
 *         <var>id</var> parameter is supplied.</dd>
 *     <dd><code>name</code> - Optional name of the collection.</dd>
 *   </dl>
 * <p><b>Output</b>: Each of the transcripts specified by the input parameters converted
 * to the given format.
 * <p> This may be a single file or multiple files, depending on the converter behaviour
 * and how many graphs are specified. If there is only one, the file in returned as the
 * response to  the request.  If there are more than one, the response is a zip file
 * containing the output files. 
 * @author Robert Fromont
 */
public class Graphs extends APIRequestHandler { // TODO unit test
   
  // Attributes:
  
  private boolean bCancel = false;
  
  /**
   * How far through the speakers.
   */
  private int iPercentComplete = 0;
  /**
   * PercentComplete accessor 
   * @return How far through the speakers.
   */
  public int getPercentComplete() { return iPercentComplete; }
  /**
   * PercentComplete mutator
   * @param iNewPercentComplete How far through the speakers.
   */
  public Graphs setPercentComplete(int iNewPercentComplete) { iPercentComplete = iNewPercentComplete; return this; }
   
  /**
   * Constructor
   */
  public Graphs() {
  } // end of constructor

  // Servlet methods
   
  /**
   * The GET method for the servlet.
   * <p> This expects an array of graph <i>id</i>s, <i>start</i> times and <i>end</i> times, 
   * a list of <i>layerId</i>s in include, and a <i>mimetype</i>.
   * <p><b>Input HTTP parameters</b>:
   * <ul>
   *  <li><i>mimeType</i> - content-type of the format to serialize to. </li>
   *  <li><i>layerId</i> - a list of layer IDs to include in the serialization. </li>
   *  <li><i>id</i> - one or more graph IDs. </li>
   *  <li><i>query</i> - Graph QL expression to identify the graph IDs, if no <i>id</i>
   *                     parameter is supplied.</li> 
   *  <li><i>name</i> - (optional) name of the collection.</li>
   * </ul>
   * <br><b>Output</b>: A each of the transcript fragments 
   * specified by the input parameters converted to the given 
   * format.  
   * <p> This may be a single file or multiple files, depending on
   * the converter behaviour and how many fragments are specified.
   * If there is only one, the file in returned as the response to 
   * the request.  If there are more than one, the response is a
   * zipfile containing the output files. 
   * @param parameters Request parameter map.
   * @param out Response body stream.
   * @param contentType Receives the content type for specification in the response headers.
   * @param fileName Receives the filename for specification in the response headers.
   * @param httpStatus Receives the response status code, in case or error.
   */
  public void get(RequestParameters parameters, OutputStream out, Consumer<String> contentType, Consumer<String> fileName, Consumer<Integer> httpStatus) {
      
    bCancel = false;
      
    // check parameters
    String name = parameters.getString("name");
    if (name == null || name.trim().length() == 0) name = "transcripts";
    name = IO.SafeFileNameUrl(name.trim());
      
    String mimeType = parameters.getString("mimeType");
    if (mimeType == null) {
      contentType.accept("text/plain;charset=UTF-8");
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      try {
        out.write(localize("No MIME type specified").getBytes()); // TODO i18n
      } catch(IOException exception) {}
      return;
    }
      
    // an array of layer names
    String[] layersToSerialize = parameters.getStrings("layerId");
    if (layersToSerialize.length == 0) {
      contentType.accept("text/plain;charset=UTF-8");
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      try {
        out.write(localize("No layers specified").getBytes()); // TODO i18n
      } catch(IOException exception) {}
      return;
    }
    
    try {
         
      final SqlGraphStoreAdministration store = getStore();
      try {
        // arrays of transcripts and delimiters
        String[] ids = parameters.getStrings("id");
        if (ids.length == 0) {
          // have they specified a query?
          String query = parameters.getString("query");
          ids = store.getMatchingTranscriptIds(query);
          if (ids.length == 0) {
            contentType.accept("text/plain;charset=UTF-8");
            httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
            out.write(localize("No IDs were specified").getBytes()); // TODO i18n
            return;
          }
        } // no "id" parameter values
        
        // gather layer IDs in a mutable collection
        LinkedHashSet<String> layers = new LinkedHashSet<String>();
        for (String l : layersToSerialize) layers.add(l);
        
        // configure serializer
        final GraphSerializer serializer = store.serializerForMimeType(mimeType);
        if (serializer == null) {
          contentType.accept("text/plain;charset=UTF-8");
          httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
          out.write(localize("Invalid MIME type: {0}", mimeType).getBytes()); // TODO i18n
          return;
        }
        Schema schema = store.getSchema();
        ParameterSet configuration = new ParameterSet();
        // default values
        serializer.configure(configuration, schema);
        // load saved ones
        ConfigurationHelper.LoadConfiguration(
          serializer.getDescriptor(), configuration, store.getSerializersDirectory(), schema);
        serializer.configure(configuration, schema);
        
        // add any layers required by the serializer
        for (String l : serializer.getRequiredLayers()) layers.add(l);
        final String[] layersToLoad = layers.toArray(new String[0]);
        
        // make the serialization a monitorable, cancelable task 
        // create a stream to pump from
        final String finalName = name;
        PipedInputStream inStream = new PipedInputStream();
        final PipedOutputStream outStream = new PipedOutputStream(inStream);
        final String[] finalIds = ids;
        Task task = new Task() {
            int gotGraphCount = 0;
            public Integer getPercentComplete() {
              if (serializer.getPercentComplete() != null) {
                iPercentComplete = serializer.getPercentComplete();
              } else {
                iPercentComplete = iPercentComplete = gotGraphCount * 100 / finalIds.length;
              }
              return super.getPercentComplete();
            }
            public void run() {
              runStart();
              try {
                
                // if we know we'll only produce one file, don't create a zip stream
                if (serializer.getCardinality() == GraphSerializer.Cardinality.NToOne
                    || (serializer.getCardinality() == GraphSerializer.Cardinality.NToN
                        && finalIds.length == 1)) { // single output stream, return that file
                  
                  contentType.accept(mimeType);
                  serializer.serialize(
                    // a source of graphs to serialize (for NToOne there might be more than one)
                    Arrays.stream(finalIds)
                    .map((id)->{
                        if (bCancelling) return null;
                        setStatus(id);
                        try {
                          return store.getTranscript(id, layersToLoad);
                        } catch (Exception x) {
                          setLastException(x);
                          setStatus("Graphs: getTranscript("+id+"): " + x);
                          return null;
                        } finally {
                          gotGraphCount += 1;
                        }
                      })
                    .filter(graph->graph != null)
                    .spliterator(),
                    // the selected layers to include (not necessarily all the layers loaded)
                    layersToSerialize,
                    // what to do with the resulting stream
                    stream -> {
                      if (bCancelling) return;
                      try {
                        setStatus(stream.getName());
                        fileName.accept(stream.getName());
                        IO.Pump(stream.getStream(), outStream);
                        outStream.flush();
                        outStream.close();
                      } catch (IOException iox) {
                        setLastException(iox);
                        setStatus("Could no read single graph stream: " + iox);
                      } finally {
                        try {
                          stream.getStream().close();
                        } catch(Exception exception) {
                          setStatus(
                            "Graphs: Cannot close single graph stream: " + exception);
                        }
                      }
                      iPercentComplete = 100;
                    },
                    warning -> setStatus("WARNING: " + warning),
                    exception -> {
                      setLastException(exception);
                      setStatus("Serialization error: " + exception);
                    });
                  
                  
                } else { // multiple output streams, return a zip file
                  
                  // send headers
                  contentType.accept("application/zip");
                  fileName.accept(finalName + ".zip");
                  final ZipOutputStream zipOut = new ZipOutputStream(outStream);
                  try {
                    
                    // serialize the stream
                    serializer.serialize(
                      // a source of graphs to serialize
                      Arrays.stream(finalIds)
                      .map((id)->{
                          if (bCancelling) return null;
                          setStatus(id);
                          try {
                            return store.getTranscript(id, layersToLoad);
                          } catch (Exception x) {
                            setLastException(x);
                            setStatus("Graphs: getTranscript("+id+"): " + x);
                            return null;
                          } finally {
                            gotGraphCount += 1;
                          }
                        })
                      .filter(graph->graph != null)
                      .spliterator(),
                      // the selected layers to include (not necessarily all the layers loaded)
                      layersToSerialize,
                      // what to do with the resulting streams
                      stream -> {
                        if (bCancelling) return;
                        setStatus(stream.getName());
                        try {
                          // create the zip entry
                          zipOut.putNextEntry(
                            new ZipEntry(IO.SafeFileNameUrl(stream.getName())));
                          // pump the data into it
                          IO.Pump(stream.getStream(), zipOut, false);
                        } catch (ZipException zx) {
                          setLastException(zx);
                          setStatus("Graphs: can't zip stream "+stream.getName()+": " + zx);
                        } catch (IOException iox) {
                          setLastException(iox);
                          setStatus("Graphs: can't process stream "+stream.getName()+": " + iox);
                        } finally {
                          try {
                            stream.getStream().close();
                          } catch(Exception exception) {
                            setStatus("Graphs: Cannot close graph stream: " + exception);
                          }
                        }
                      },
                      warning -> setStatus("WARNING: " + warning),
                      exception -> {
                        setLastException(exception);
                        setStatus("Serialization error: " + exception);
                      });

                  } finally { // finish up
                    try {
                      zipOut.close();
                    } catch(Exception exception) {
                      setStatus("Graphs: Cannot close ZIP file: " + exception);
                    }
                  }
                } // multiple output streams, return a zip file
                setStatus(finalName + (bCancelling?" cancelled.":" complete."));
              } catch (SerializerNotConfiguredException x) { // shouldn't happen
                setLastException(x);
                setStatus("Could not serialize " + finalName + ": " + x);
              } finally {
                runEnd();
                if (getLastException() != null) {
                  
                  // somebody might want more info
                  StringWriter sw = new StringWriter();
                  PrintWriter pw = new PrintWriter(sw);
                  getLastException().printStackTrace(pw);
                  setStatus(getLastException() + ": " + sw.toString());
                  pw.close();
                            
                  // wait a while before disappearing
                  waitToDie();
                }
              }
            } // run
          };
        task.setStore(store);
        task.start();
        IO.Pump(inStream, out);
        
      } finally {
        cacheStore(store);
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

  /**
   * Cancels the currently running exportTextGrids() method.
   */
  public void cancel() {
    bCancel = true;
  } // end of cancel()
   
  /**
   * Converts the given utterances to the given format.
   * @param ids IDs of graphs to convert.
   * @param layers A list of layer names.
   * @param mimeType The target content type.
   * @param store The graph store for retrieving serializers and graphs from.
   * @return A Stream containing the fragments - could be a single stream with the
   * fragments, of the given MIME type, or a ZIP file containing individual files. 
   * @throws Exception
   */
  @Deprecated // TODO remove this method once legacy BatchExportTask is no longer referenced
  public Vector<NamedStream> serializeGraphs(
    String name, String[] ids, Collection<String> layers, String mimeType,
    SqlGraphStoreAdministration store)
    throws Exception {
      
    bCancel = false;
    // ensure the collection is mutable
    layers = new Vector<String>(layers);
      
    String[] selectedLayerIds = layers.toArray(new String[0]);
      
    int iGraphCount = ids.length;
    if (iGraphCount == 0) throw new Exception("No IDs specified");
    int iGraph = 0;
      
    File fTempDir = new File(System.getProperty("java.io.tmpdir"));
      
    GraphSerializer serializer = store.serializerForMimeType(mimeType);
    if (serializer == null) {
      throw new Exception("Invalid MIME type: " + mimeType);
    }
    Schema schema = store.getSchema();
    // configure serializer
    ParameterSet configuration = new ParameterSet();
    // default values
    serializer.configure(configuration, schema);
    // load saved ones
    ConfigurationHelper.LoadConfiguration(
      serializer.getDescriptor(), configuration, store.getSerializersDirectory(), schema);
    serializer.configure(configuration, schema);
    for (String l : serializer.getRequiredLayers()) layers.add(l);
    String[] layerIds = layers.toArray(new String[0]);
      
    // for each transcript specified
    Vector<Graph> graphs = new Vector<Graph>();
    for (String id : ids) {
      if (bCancel) break;

      try {
        graphs.add(store.getTranscript(id, layerIds));
      } catch(Exception exception) {
        context.servletLog("Graphs error processing: " + id + " - " + exception);
      }	    
    } // next graph
    iPercentComplete = 50;
      
    final Vector<NamedStream> files = new Vector<NamedStream>();
    if (!bCancel) {
      // serialize them
      serializer.serialize(
        graphs.spliterator(), selectedLayerIds,
        stream -> {
          if (bCancel) return;
          files.add(stream);
          iPercentComplete = 50 + Optional.of(serializer.getPercentComplete()).orElse(0)/2;
        },
        warning -> context.servletLog("WARNING: " + warning),
        exception -> context.servletLog("SerializeFragment error: " + exception));
      iPercentComplete = 100;
    }
    return files;
  } // end of serializeGraphs()
   
  private static final long serialVersionUID = -1;
} // end of class Graphs
