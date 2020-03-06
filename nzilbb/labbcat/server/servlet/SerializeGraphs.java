//
// Copyright 2019-2020 New Zealand Institute of Language, Brain and Behaviour, 
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

package nzilbb.labbcat.server.servlet;

import java.io.*;
import java.net.*;
import javax.servlet.*; // d:/jakarta-tomcat-5.0.28/common/lib/servlet-api.jar
import javax.servlet.http.*;
import javax.servlet.annotation.WebServlet;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;
import javax.xml.parsers.*;
import javax.xml.xpath.*;
import org.json.*;
import org.w3c.dom.*;
import org.xml.sax.*;
import nzilbb.configure.ParameterSet;
import nzilbb.ag.IGraphStoreAdministration;
import nzilbb.ag.Schema;
import nzilbb.ag.Graph;
import nzilbb.ag.serialize.ISerializer;
import nzilbb.ag.serialize.SerializationException;
import nzilbb.ag.serialize.util.ConfigurationHelper;
import nzilbb.ag.serialize.util.NamedStream;
import nzilbb.ag.serialize.util.Utility;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.util.IO;

/**
 * Servlet that converts transcript fragments.
 * @author Robert Fromont
 */
@WebServlet({"/api/serialize/graphs", "/serialize/graphs"} )
public class SerializeGraphs extends LabbcatServlet {
   
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
   public SerializeGraphs setPercentComplete(int iNewPercentComplete) { iPercentComplete = iNewPercentComplete; return this; }
   
   /**
    * Constructor
    */
   public SerializeGraphs() {
   } // end of constructor

   // Servlet methods
   
   /**
    * The GET method for the servlet - this expects an array of 
    * graph <i>id</i>s, <i>start</i> times and <i>end</i> times, 
    * a list of <i>layerId</i>s in include, and a <i>mimetype</i>.
    * <p><b>Input HTTP parameters</b>:
    * <ul>
    *  <li><i>mimeType</i> - content-type of the format to serialize to. </li>
    *  <li><i>layerId</i> - a list of layer IDs to include in the serialization. </li>
    *  <li><i>id</i> - one or more graph IDs. </li>
    *  <li><i>name</i> - (optional) name of the collection.</li>
    * </ul>
    * <br><b>Output</b>: A each of the transcript fragments 
    * specified by the input parameters converted to the given 
    * format.  
    * This may be a single file or multiple files, depending on
    * the converter behaviour and how many fragments are specified.
    * If there is only one, the file in returned as the response to 
    * the request.  If there are more than one, the response is a
    * zipfile containing the output files. 
    * @param req HTTP request
    * @param res HTTP response
    */
   @Override
   public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
      
      ServletContext context = request.getSession().getServletContext();
      
      // check parameters
      String name = request.getParameter("name");
      if (name == null || name.trim().length() == 0) name = "fragments";
      name = name.trim();
      
      String mimeType = request.getParameter("mimeType");
      if (mimeType == null) {
	 response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No MIME type specified");
	 return;
      }
      
      // an array of layer names
      String[] layerId = request.getParameterValues("layerId");
      if (layerId == null) {
	 response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No layers specified");
	 return;
      }
      
      // arrays of transcripts and delimiters
      String[] id = request.getParameterValues("id");
      if (id == null) {
	 response.sendError(400, "No graph IDs specified");
	 return;
      }
      
      File zipFile = null;
      Connection connection = null;
      try {
         connection = newConnection();

         SqlGraphStoreAdministration store = (SqlGraphStoreAdministration)
            request.getSession().getAttribute("store");
         if (store != null) { // use this request's connection
            store.setConnection(connection);
            
            // stop other requests from using this store at the same time
            request.getSession().setAttribute("store", null);
         } else { // no store yet, so create one
            store = new SqlGraphStoreAdministration(
               baseUrl(request), connection, request.getRemoteUser());
         }
         
         LinkedHashSet<String> layers = new LinkedHashSet<String>();
         for (String l : layerId) layers.add(l);
         
         Vector<NamedStream> files = serializeGraphs(name, id, layers, mimeType, store);
         
         // did we actually find any files?
         if (files.size() == 0) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "No files were generated");
         }
         else if (files.size() == 1) { // one file only
            // don't zip a single file, just return the file
            response.setContentType(mimeType);
            NamedStream stream = files.firstElement();
            response.addHeader("Content-Disposition", "attachment; filename=" + stream.getName());

            IO.Pump(stream.getStream(), response.getOutputStream());
         } else { /// multiple files
            response.addHeader(
               "Content-Disposition", "attachment; filename=" + IO.SafeFileNameUrl(name) + ".zip");
            
            // create a stream to pump from
            PipedInputStream inStream = new PipedInputStream();
            final PipedOutputStream outStream = new PipedOutputStream(inStream);
	    
            // start a new thread to extract the data and stream it back
            new Thread(new Runnable() {
                  public void run() {
                     try {
                        ZipOutputStream zipOut = new ZipOutputStream(outStream);
			
                        // for each file
                        for (NamedStream stream : files) {
                           try {
                              // create the zip entry
                              zipOut.putNextEntry(
                                 new ZipEntry(IO.SafeFileNameUrl(stream.getName())));
                              
                              IO.Pump(stream.getStream(), zipOut, false);
                           } catch (ZipException zx) {
                           } finally {
                              stream.getStream().close();
                           }
                        } // next file
                        try {
                           zipOut.close();
                        } catch(Exception exception) {
                           System.err.println(
                              "SerializeGraphs: Cannot close ZIP file: " + exception);
                        }
                     } catch(Exception exception) {
                        System.err.println("SerializeGraphs: open zip stream: " + exception);
                     }
                  }
               }).start();
	    
            // send headers immediately, so that the browser shows the 'save' prompt
            response.getOutputStream().flush();
            
            IO.Pump(inStream, response.getOutputStream());
         } // multiple files
      } catch(Exception ex) {
         throw new ServletException(ex);
      } finally {
         try { connection.close(); } catch(Exception eClose) {}
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
    * @param id IDs of graphs to convert.
    * @param layers A list of layer names.
    * @param sMimeType
    * @param store
    * @return A Stream containing the fragments - could be a single stream with the fragments, of the given MIME type, or a ZIP file containing individual files.
    * @throws Exception
    */
   public Vector<NamedStream> serializeGraphs(String name, String[] ids, Collection<String> layers, String sMimeType, SqlGraphStoreAdministration store)
      throws Exception {
      
      bCancel = false;
      // ensure the collection is mutable
      layers = new Vector<String>(layers);
      
      String[] selectedLayerIds = layers.toArray(new String[0]);
      
      int iGraphCount = ids.length;
      if (iGraphCount == 0) throw new Exception("No IDs specified");
      int iGraph = 0;
      
      File fTempDir = new File(System.getProperty("java.io.tmpdir"));
      
      ISerializer serializer = store.serializerForMimeType(sMimeType);
      if (serializer == null) {
	 throw new Exception("Invalid MIME type: " + sMimeType);
      }
      Schema schema = store.getSchema();
      // configure deserializer
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
	    System.err.println("SerializeGraphs error processing: " + id + " - " + exception);
	 }	    
      } // next graph
      iPercentComplete = 50;
      
      final Vector<NamedStream> files = new Vector<NamedStream>();
      if (!bCancel) {
         // serialize them
         serializer.serialize(
            graphs.spliterator(), selectedLayerIds,
            new Consumer<NamedStream>() {
               public void accept(NamedStream stream) {
                  if (bCancel) return;
                  files.add(stream);
                  iPercentComplete = 50 + Optional.of(serializer.getPercentComplete()).orElse(0)/2;
               }},
            new Consumer<String>() {
               public void accept(String warning) {
                  System.out.println("WARNING: " + warning);
               }},
            new Consumer<SerializationException>() {
               public void accept(SerializationException exception) {
                  System.err.println("SerializeFragment error: " + exception);
               }       
            });
         iPercentComplete = 100;
      }
      return files;
   } // end of convertFragments()
   
   private static final long serialVersionUID = -1;
} // end of class SerializeGraphs
