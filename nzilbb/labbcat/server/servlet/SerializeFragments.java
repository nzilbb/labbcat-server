//
// Copyright 2017-2020 New Zealand Institute of Language, Brain and Behaviour, 
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

import java.io.*;
import java.net.*;
import java.sql.*;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.servlet.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import nzilbb.ag.Graph;
import nzilbb.ag.GraphStoreAdministration;
import nzilbb.ag.Schema;
import nzilbb.ag.serialize.GraphSerializer;
import nzilbb.ag.serialize.SerializationException;
import nzilbb.ag.serialize.util.ConfigurationHelper;
import nzilbb.ag.serialize.util.NamedStream;
import nzilbb.ag.serialize.util.Utility;
import nzilbb.configure.ParameterSet;
import nzilbb.labbcat.server.db.ConsolidatedGraphSeries;
import nzilbb.labbcat.server.db.FragmentSeries;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.labbcat.server.task.SerializeFragmentsTask;
import nzilbb.util.IO;
import nzilbb.util.MonitorableSeries;

/**
 * <tt>/api/serialize/fragment</tt>
 * : Converts transcript fragments to specific formats.
 *  <p> Converts parts of transcripts to annotation file formats. 
 *  <p> The request method can be <b> GET </b> or <b> POST </b>
 * The servlet expects an array of graph <i>id</i>s, <i>start</i> times and <i>end</i> times, 
 * a list of <i>layerId</i>s in include, and a <i>mimetype</i>.
 * <p><b>Input HTTP parameters</b>:
 * <ul>
 *  <li><i>mimeType</i> - content-type of the format to serialize to. </li>
 *  <li><i>layerId</i> - a list of layer IDs to include in the serialization. </li>
 *  <li><i>id</i> - one or more graph IDs. </li>
 *  <li><i>start</i> - one or more start times (in seconds).</li>
 *  <li><i>end</i> - one or more end times (in seconds).</li>
 *  <li><i>filter</i> - (optional) one or more annotation IDs to filter by. e.g. a turn
 * annotation ID, which would ensure that only words within the specified turn are
 * included, not words from other turns.</li> 
 *  <li><i>name</i> - (optional) name of the collection.</li>
 *  <li><i>prefix</i> - (optional) prefix fragment names with a numeric serial number.</li>
 *  <li><i>tag</i> - (optional) add a tag identifying the target annotation.</li>
 *  <li><i>async</i> - (optional) "true" to start a serialization server task and
 *      immediately it's <var>threadId</var> rather than return the actual serialization
 *      results. </li>
 * </ul>
 * <br><b>Output</b>: if <i>async</i> is ommited, the result is each of the transcript
 * fragments  specified by the input parameters converted to the given  format. This may
 * be a single file or multiple files, depending on the converter behaviour and how many
 * fragments are specified. If there is only one, the file in returned as the response to
 * the request.  If there are more than one, the response is a zip file containing the
 * output files.  
 * <br> If <i>async</i> == true, the result is a JSON-encoded response object of the usual
 * structure for which the "model" is an object with a "threadId" attribute, which is the
 * ID of the server task to monitor for results. e.g.
 * <pre>{
 *    "title":"SerializeFragments",
 *    "version" : "20220303.1143",
 *    "code" : 0,
 *    "errors" : [],
 *    "messages" : [],
 *    "model" : {
 *        "threadId" : 80
 *    }
 * }</pre>
 * <br> The task, when finished, will output the same as above (a zip or other file with
 * the fragments in the given format).
 * <p> In general, <i>async</i> should not be specified, as it uses more resources
 * (results files must be stored on the server until the whole serialization is
 * finished). </p>
 * <p> However, for some serializers (e.g. <q>"text/x-kaldi-text"</q>), it's not
 * possible to return any content until all fragments are processed anyway. In these
 * cases, if the set of fragments is very large, the delay between making the request and
 * receiving the response can be so long that client libraries time out. In such cases,
 * using <i>async</i>=true is preferable, as the serialization can be monitored with a
 * long series of short requests, and then the data tranferred when finally ready.</p>
 * @author Robert Fromont
 */
@WebServlet({"/api/serialize/fragment", "/serialize/fragment"} )
public class SerializeFragments extends LabbcatServlet { // TODO unit test
   
   // Attributes:
   private boolean bCancel = false;
   private MonitorableSeries<Graph> utterances;
   private GraphSerializer serializer;
   
   /**
    * How far through the speakers.
    */
   private int iPercentComplete = 0;
   /**
    * PercentComplete accessor 
    * @return How far through the speakers.
    */
   public int getPercentComplete() {
      if (serializer != null && utterances != null) {
         iPercentComplete =
            Optional.of(utterances.getPercentComplete()).orElse(0)/2
            + Optional.of(serializer.getPercentComplete()).orElse(0)/2;
      }
      return iPercentComplete;
   }
   /**
    * PercentComplete mutator
    * @param iNewPercentComplete How far through the speakers.
    */
   public void setPercentComplete(int iNewPercentComplete) { iPercentComplete = iNewPercentComplete; }
   
   /**
    * Constructor
    */
   public SerializeFragments() {
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
    *  <li><i>start</i> - one or more start times (in seconds).</li>
    *  <li><i>end</i> - one or more end times (in seconds).</li>
    *  <li><i>filter</i> - (optional) one or more annotation IDs to filter by. e.g. a turn
    * annotation ID, which would ensure that only words within the specified turn are
    * included, not words from other turns.</li>
    *  <li><i>name</i> - (optional) name of the collection.</li>
    *  <li><i>prefix</i> - (optional) prefix fragment names with a numeric serial number.</li>
    *  <li><i>tag</i> - (optional) add a tag identifying the target annotation.</li>
    * </ul>
    * <br><b>Output</b>: A each of the transcript fragments 
    * specified by the input parameters converted to the given 
    * format.  
    * This may be a single file or multiple files, depending on
    * the converter behaviour and how many fragments are specified.
    * If there is only one, the file in returned as the response to 
    * the request.  If there are more than one, the response is a
    * zipfile containing the output files. 
    * @param request HTTP request
    * @param response HTTP response
    */
   @Override
   public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
      
      ServletContext context = getServletContext();
      
      // check parameters
      String name = request.getParameter("name");
      if (name == null || name.trim().length() == 0) name = "fragments";
      name = IO.SafeFileNameUrl(name.trim());
      
      String mimeType = request.getParameter("mimeType");
      if (mimeType == null) mimeType = request.getParameter("content-type");
      if (mimeType == null) {
	 response.sendError(500, "no MIME type specified");
	 return;
      }
      
      // an array of layer names
      String[] layerId = request.getParameterValues("layerId");
      if (layerId == null) {
	 response.sendError(500, "no layers specified");
	 return;
      }
      
      // arrays of transcripts and delimiters
      String[] id = request.getParameterValues("id");
      if (id == null) {
	 response.sendError(500, "no graph IDs specified");
	 return;
      }
      String[] start = request.getParameterValues("start");
      if (start == null) {
	 response.sendError(500, "no start offsets specified");
	 return;
      }
      String[] end = request.getParameterValues("end");
      if (end == null) {
	 response.sendError(500, "no end offsets specified");
	 return;
      }
      String[] filter = request.getParameterValues("filter");
      if (id.length != start.length || id.length != end.length
          || (filter != null && id.length != filter.length)) {
	 response.sendError(500, "mismatched number of id, start, end, and filter parameters");
	 return;
      }

      boolean prefixNames = request.getParameter("prefix") != null;
      boolean tagTarget = request.getParameter("tag") != null;
      NumberFormat resultNumberFormatter = NumberFormat.getInstance();
      resultNumberFormatter.setGroupingUsed(false);
      resultNumberFormatter.setMinimumIntegerDigits((int)(Math.log10(id.length)) + 1);
      
      try {
        if ("true".equalsIgnoreCase(request.getParameter("async"))) {
          // start a task and return its ID
          
          // layers
          HashSet<String> layers = new HashSet<String>();
          for (String l : layerId) layers.add(l);
          
          // utterances
          Vector<String> vUtterances = new Vector<String>();
          if (filter == null) { // not filtering by turn etc.
            for (int f = 0; f < id.length; f++) {
              vUtterances.add(
                id[f]+";"+start[f]+"-"+end[f]
                +(prefixNames?";prefix="+resultNumberFormatter.format(f+1)+"-":""));
            }
          } else { // filtering by turn etc.
            for (int f = 0; f < id.length; f++) {
              vUtterances.add(
                id[f]+";"+start[f]+"-"+end[f]+";"+filter[f]
                +(prefixNames?";prefix="+resultNumberFormatter.format(f+1)+"-":""));
            }
          }
          
          SerializeFragmentsTask task = new SerializeFragmentsTask(
            name, -1, layers,
            mimeType, getStore(request))
            .setIncludeRequiredLayers(true)
            .setPrefixNames(prefixNames)
            .setTagTarget(tagTarget);
          task.setUtterances(vUtterances);
          if (request.getRemoteUser() != null) {	
            task.setWho(request.getRemoteUser());
          } else {
            task.setWho(request.getRemoteHost());
          }
          task.start();
          // return its ID
          JsonObjectBuilder jsonResult = Json.createObjectBuilder()
            .add("threadId", task.getId());
          writeResponse(
            response, successResult(request, jsonResult.build(), null));
          return;
        } // async
        
        File zipFile = null;
        SqlGraphStoreAdministration store = getStore(request);
        try {
            
          LinkedHashSet<String> layersToLoad = new LinkedHashSet<String>();
          for (String l : layerId) layersToLoad.add(l);
            
          Vector<String> vUtterances = new Vector<String>();
          if (filter == null) { // not filtering by turn etc.
            for (int f = 0; f < id.length; f++) {
              vUtterances.add(
                id[f]+";"+start[f]+"-"+end[f]
                +(prefixNames?";prefix="+resultNumberFormatter.format(f+1)+"-":""));
            }
          } else { // filtering by turn etc.
            for (int f = 0; f < id.length; f++) {
              vUtterances.add(
                id[f]+";"+start[f]+"-"+end[f]+";"+filter[f]
                +(prefixNames?";prefix="+resultNumberFormatter.format(f+1)+"-":""));
            }
          }
            
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
          for (String l : serializer.getRequiredLayers()) layersToLoad.add(l);
          MonitorableSeries<Graph> fragmentSource = new FragmentSeries(
            vUtterances, store, layersToLoad.toArray(new String[0]))
            .setPrefixNames(prefixNames)
            .setTagTarget(tagTarget);
          // if we're not prefixing names, and we're tagging targets
          if (!prefixNames && tagTarget) {
            // then we need to consolidate graphs - i.e. catch consecutive fragments that
            // are the same ID, and copy the target tags into the winning version of the graph
            fragmentSource = new ConsolidatedGraphSeries(fragmentSource)
              .copyLayer("target");
          }
            
          final Vector<NamedStream> files = new Vector<NamedStream>();
          serializeFragments(
            name, fragmentSource,
            serializer,
            new Consumer<NamedStream>() {
              public void accept(NamedStream stream) {
                files.add(stream);
              }},
            new Consumer<SerializationException>() {
              public void accept(SerializationException exception) {
                System.err.println("SerializeFragments: " + exception);
              }},
            layerId, mimeType, store);
            
          // did we actually find any files?
          if (files.size() == 0) {
            response.sendError(404, "no files were generated");
          } else if (files.size() == 1) { // one file only
            // don't zip a single file, just return the file
            response.setContentType(mimeType);
            NamedStream stream = files.firstElement();
            response.addHeader(
              "Content-Disposition", "attachment; filename=" + stream.getName());
               
            IO.Pump(stream.getStream(), response.getOutputStream());
          } else { /// multiple files
            response.setContentType("application/zip");
            response.addHeader("Content-Disposition", "attachment; filename=" 
                               + IO.SafeFileNameUrl(name) + ".zip");
               
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
                      }
                      catch (ZipException zx) {
                      } finally {
                        stream.getStream().close();
                      }
                    } // next file
                    try {
                      zipOut.close();
                    } catch(Exception exception) {
                      System.err.println("ConvertFragment: Cannot close ZIP file: " + exception);
                    }
                  } catch(Exception exception) {
                    System.err.println("ConvertFragment: open zip stream: " + exception);
                  }
                }
              }).start();
               
            // send headers immediately, so that the browser shows the 'save' prompt
            response.getOutputStream().flush();
               
            IO.Pump(inStream, response.getOutputStream());
          } // multiple files
        } finally {
          cacheStore(store);
        }
      } catch(Exception ex) {
         throw new ServletException(ex);
      }
   }
   
   /**
    * The post method for this servlet - simply calls doGet.
    * @param req HTTP request
    * @param res HTTP response
    */
   protected void doPost(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, java.io.IOException {
      doGet(req,res);
   }

   /**
    * Cancels the currently running exportTextGrids() method.
    */
   public void cancel() {
      bCancel = true;
      if (utterances != null) utterances.cancel();
      if (serializer != null) serializer.cancel();
   } // end of cancel()
   
   /**
    * Serializes the given series of utterances using the given serializer.
    * @param name The name of the collection.
    * @param utterances Utterances to serialize.
    * @param serializer The serialization module.
    * @param streamConsumer Consumer for receiving the serialized streams.
    * @param errorConsumer Consumer for handling serialization errors.
    * @param layerIds A list of layer names.
    * @param mimeType
    * @param store Graph store.
    * @throws Exception
    */
   public void serializeFragments(
      String name, MonitorableSeries<Graph> utterances, GraphSerializer serializer,
      Consumer<NamedStream> streamConsumer, Consumer<SerializationException> errorConsumer,
      String[] layerIds, String mimeType, GraphStoreAdministration store)
      throws Exception {
      
      bCancel = false;
      this.utterances = utterances;
      this.serializer = serializer;
      
      if (utterances.getExactSizeIfKnown() <= 0) throw new Exception("No utterances specified");
      
      File fTempDir = new File(System.getProperty("java.io.tmpdir"));
	 
      // serialize the fragments
      final Vector<NamedStream> files = new Vector<NamedStream>();
      serializer.serialize(
         utterances, layerIds,
         streamConsumer,
         new Consumer<String>() {
            public void accept(String warning) {
               System.out.println("WARNING: " + warning);
            }},
         errorConsumer);
      iPercentComplete = 100;
   } // end of convertFragments()
   
   private static final long serialVersionUID = -1;
} // end of class SerializeFragments
