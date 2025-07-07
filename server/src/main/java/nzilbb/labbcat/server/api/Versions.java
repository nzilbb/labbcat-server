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

package nzilbb.labbcat.server.api;

import java.io.File;
import java.io.FileInputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.TreeMap;
import java.util.function.Consumer;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import nzilbb.ag.automation.util.AnnotatorDescriptor;
import nzilbb.ag.serialize.SerializationDescriptor;
import nzilbb.labbcat.server.db.SqlGraphStore;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.util.Execution;
import nzilbb.util.IO;

/**
 * <tt>/api/versions</tt> : version information about LaBB-CAT, its modules, and 3rd-party integrations.
 *  <p> Allows access to information about the current user, returning a
 *  JSON-encoded object with the following top level attributes:
 *   <dl>
 *    <dt> System </dt><dd> Versions of different components of LaBB-CAT as a whole. </dd>
 *    <dt> Formats </dt><dd> Versions of format conversion modules. </dd>
 *    <dt> Layer Managers </dt><dd> Versions of legacy layer manager modules. </dd>
 *    <dt> Annotator modules </dt><dd> Versions of annotator modules. </dd>
 *    <dt> 3rd Party Software </dt><dd> Versions of Praat, ffmpeg, etc. </dd>
 *    <dt> RDBMS </dt><dd> Version information for the SQL database. </dd>
 *   </dl>
 *  <p> Each attribute value is a object where each attribute name is a sub-component name,
 *  and its value is a string representing the version of that sub-component.
 *   <p> Only the GET HTTP method is supported:
 *   <dl>
 *    <dt> GET </dt><dd>
 *     <ul>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object with the above structure.  </li>
 *      <li><em> Response Status </em> n- <em> 200 </em> : Success. </li>
 *     </ul></dd> 
 *   </dl>
 *  </p>
 * @author Robert Fromont
 */
public class Versions extends APIRequestHandler {
  
  /** Constructor */
  public Versions() {}
  
  /**
   * Generate the response to a request.
   * <p> This returns information about the current user - their ID and the roles they have.
   * @param jsonOut A JSON generator for writing the response to.
   * @param httpStatus Receives the response status code, in case of error.
   */
  public void get(JsonGenerator jsonOut, Consumer<Integer> httpStatus) {
    try {
      SqlGraphStore store = getStore();
      try {
        startResult(jsonOut, false);
        
        jsonOut.writeStartObject("System");
        jsonOut.write("LaBB-CAT", context.getVersion());
        jsonOut.write("Latest Upgrader", store.getSystemAttribute("configVersion"));
        jsonOut.write("nzilbb.ag API", nzilbb.ag.Constants.VERSION);
        jsonOut.write("nzilbb.labbcat.server",
                      nzilbb.labbcat.server.api.Store.class.getPackage()
                      .getImplementationVersion());
        jsonOut.writeEnd(); // System

        // formats
        TreeMap<String,String> formatterVersions = new TreeMap<String,String>();
        for (SerializationDescriptor descriptor : store.getSerializerDescriptors()) {
          formatterVersions.put(descriptor.getName(), descriptor.getVersion());
        }
        for (SerializationDescriptor descriptor : store.getDeserializerDescriptors()) {
          formatterVersions.put(descriptor.getName(), descriptor.getVersion());
        }
        jsonOut.writeStartObject("Formats");
        for (String format : formatterVersions.keySet()) {
          jsonOut.write(format, formatterVersions.get(format));
        }
        jsonOut.writeEnd(); // Formats

        jsonOut.writeStartObject("Annotators");
        for (AnnotatorDescriptor descriptor : store.getAnnotatorDescriptors()) {
          jsonOut.write(descriptor.getAnnotatorId(), descriptor.getVersion());
        }
        jsonOut.writeEnd(); // Annotator modules

        jsonOut.writeStartObject("ThirdPartySoftware");
        try { // praat
          File praatExe = new File("praat");
          String praatPath = store.getSystemAttribute("praatPath");
          if (praatPath != null && praatPath.length() > 0) {
            praatExe = new File(new File(praatPath), "praat");
            String osName = java.lang.System.getProperty("os.name");
            if (osName.startsWith("Windows")) {
              praatExe = new File(praatPath, "Praat.exe");
            } else if (osName.startsWith("Mac")) {
              praatExe = new File(
                new File(new File(new File(praatPath, "Praat.app"), "Contents"), "MacOS"), "Praat");
            }       
            if (praatExe.exists()) {
              Execution exe = new Execution().setExe(praatExe).arg("--version");
              exe.run();
              jsonOut.write("Praat", exe.stdout().trim());
            } else {
              jsonOut.write(
                "Praat", "Praat executable does not exist: " + praatExe.getPath());
            }
          }
        } catch(Throwable exception) {
          context.servletLog("Versions: Could not determine Praat version: " + exception);
          context.servletLog(exception);
        }      
        try { // ffmpeg
          File ffmpegExe = new File("ffmpeg");
          String ffmpegPath = store.getSystemAttribute("ffmpegPath");
          if (ffmpegPath != null && ffmpegPath.length() > 0) {
            ffmpegExe = new File(new File(ffmpegPath), "ffmpeg");
            String osName = java.lang.System.getProperty("os.name");
            if (osName.startsWith("Windows")) {
              ffmpegExe = new File(ffmpegPath, "ffmpeg.exe");
            }
            if (ffmpegExe.exists()) {
              Execution exe = new Execution().setExe(ffmpegExe).arg("-version");
              exe.run();
              String firstLine = exe.stdout().split("\n")[0];
              String version = firstLine.replaceAll(".*version (\\S+).*", "$1");
              jsonOut.write("Ffmpeg", version.trim());
            } else {
              jsonOut.write(
                "Ffmpeg", "ffmpeg executable does not exist: " + ffmpegExe.getPath());
            }
          }
        } catch(Throwable exception) {
          context.servletLog("Versions: Could not determine Ffmpeg version: " + exception);
          context.servletLog(exception);
        }
        try { // fast track
          File fastTrackVersion
            = new File(new File(store.getFiles(), "FastTrack-master"), "version.txt");
          if (fastTrackVersion.exists()) {
            jsonOut.write("FastTrack", IO.InputStreamToString(
                            new FileInputStream(fastTrackVersion)));
          }
        } catch(Exception exception) {
        }
        jsonOut.writeEnd(); // 3rd Party Software

        jsonOut.writeStartObject("RDBMS");
        PreparedStatement sql = store.getConnection()
          .prepareStatement("SELECT @@version, @@version_comment");
        ResultSet rs = sql.executeQuery();
        try {
          rs.next();
          jsonOut.write("version", rs.getString(1));
          jsonOut.write("version_comment", rs.getString(2));
        } finally {
          rs.close();
          sql.close();
        }
        jsonOut.writeEnd(); // RDBMS
        
        jsonOut.writeStartObject("LayerManagers");
        sql = store.getConnection().prepareStatement(
          "SELECT layer_manager_id, version FROM layer_manager ORDER BY layer_manager_id");
        rs = sql.executeQuery();
        try {
          while (rs.next()) {
            jsonOut.write(rs.getString(1), rs.getString(2));
          } // next row
        } finally {
          rs.close();
          sql.close();
        }
        jsonOut.writeEnd(); // Layer Managers
        
        endSuccessResult(jsonOut, null);
      } finally {
        cacheStore((SqlGraphStoreAdministration)store);
      }
    } catch(Exception ex) {
      context.servletLog("Version.get: failed: " + ex);
      context.servletLog(ex);
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      // for security, don't return the specific error to the client
      endFailureResult(jsonOut, "Unexpected error.");
    } 
  }
} // end of class Versions
