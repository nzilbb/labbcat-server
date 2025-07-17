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

package nzilbb.labbcat.server.api.results;

import java.io.BufferedReader;
import java.io.File;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.Optional;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.json.Json;
import javax.json.JsonObject;
import nzilbb.ag.*;
import nzilbb.labbcat.server.api.APIRequestHandler;
import nzilbb.labbcat.server.api.RequestParameters;
import nzilbb.labbcat.server.db.SqlGraphStore;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.labbcat.server.db.StoreCache;
import nzilbb.labbcat.server.task.ParseResultsFile;
import nzilbb.util.IO;
import org.apache.commons.csv.*;

/**
 * <tt>/api/results/upload</tt>
 * : Handler for receiving a CSV results file previously returned by <tt>/api/results</tt>.
 * <p> <b> POST </b> method receives the data file and begins the process of parsing the
 * results. 
 * <p> The multipart-encoded parameters are:
 *  <dl>
 *   <dt> results </dt>
 *       <dd> Results file to upload. </dd>
 *   <dt> csvFieldDelimiter </dt>
 *       <dd> Optional character for delimiting the CSV field. If not specified, the
 *            delimiter will be inferred from the first line of the file. </dd> 
 *   <dt> targetColumn </dt>
 *       <dd> Optional column name that identifies each match. The default is <q>MatchId</q> </dd> 
 *  </dl>
 * <p><b>Output</b>: a JSON-encoded response object of the usual structure for which the
 * <q>model</q> is an object with a <q>threadId</q> attribute, which is the ID of the
 * server task to monitor file parsing progress. e.g. 
 * <pre>{
 *    "title":"upload",
 *    "version" : "20250711.1119",
 *    "code" : 0,
 *    "errors" : [],
 *    "messages" : [],
 *    "model" : {
 *        "threadId" : "80"
 *    }
 * }</pre>
 * <p> The task, when finished, will output a URL for accessing the matches of the search.
 * @author Robert Fromont robert@fromont.net.nz
 */
public class Upload extends APIRequestHandler {

  File uploadsDir;
  
  /**
   * Default constructor.
   */
  public Upload() {
    uploadsDir = new File(new File(System.getProperty("java.io.tmpdir")), "LaBB-CAT.Upload");
    if (!uploadsDir.exists()) uploadsDir.mkdir();
  } // end of constructor
  
  /**
   * The POST method for the servlet.
   * @param requestParameters Request parameter map.
   * @param httpStatus Receives the response status code, in case of error.
   * @return JSON-encoded object representing the response
   */
  public JsonObject post(RequestParameters requestParameters, Consumer<Integer> httpStatus) {
    File dir = null;
    try {
      final SqlGraphStoreAdministration store = getStore();
      String targetColumn = Optional.ofNullable(
        requestParameters.getString("targetColumn")).orElse("MatchId");
      
      // generate an ID/directory to save files
      dir = Files.createTempDirectory(uploadsDir.toPath(), "results-").toFile();
      dir.deleteOnExit();
      
      // get results file
      File csvFile = requestParameters.getFile("results");
      if (csvFile == null) {
        httpStatus.accept(SC_BAD_REQUEST);
        return failureResult("No file received.");
      }
      
      // determine field delimiter
      String csvFieldDelimiter = requestParameters.getString("csvFieldDelimiter");
      if (csvFieldDelimiter == null || csvFieldDelimiter.length() == 0) {
        // figure out the field delimiter          
        try (BufferedReader r =  new BufferedReader(new FileReader(csvFile))) {
          String firstLine = r.readLine();
          while (firstLine != null && firstLine.trim().length() == 0) {
            firstLine = r.readLine();
          } // next blank line
          if (firstLine != null) {
            if (firstLine.contains("\t")) csvFieldDelimiter = "\t";
            else if (firstLine.contains(";")) csvFieldDelimiter = ";";
            else if (firstLine.contains(",")) csvFieldDelimiter = ",";
          }
        } // reader
      }
      
      // check the target column is valid
      boolean targetFound = false;
      CSVFormat format = CSVFormat.EXCEL
        .withDelimiter(csvFieldDelimiter.charAt(0));
      try (CSVParser parser = new CSVParser(new FileReader(csvFile), format)) {
        Iterator<CSVRecord> records = parser.iterator();          
        if (!records.hasNext()) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("Empty received."); // TODO i18n
        }
        // list the columns
        CSVRecord headers = records.next();
        Vector vColumns = new Vector();
        for (int c = 0; c < headers.size(); c++) {
          if (headers.get(c).equals(targetColumn)) {
            targetFound = true;
            break;
          }
        } // next field
      } // parser
      if (!targetFound) {
        httpStatus.accept(SC_BAD_REQUEST);
        return failureResult("Target column is missing: {0}", targetColumn); // TODO i18n
      }
      
      // start parsing the file
      ParseResultsFile task = new ParseResultsFile(csvFile)
        .setCsvFieldDelimiter(csvFieldDelimiter.charAt(0))
        .setTargetColumn(targetColumn);
      task.setStoreCache(new StoreCache() {
          public SqlGraphStore get() {
            return store;
          }
          public void accept(SqlGraphStore store) {
            cacheStore((SqlGraphStoreAdministration)store);
          }
        });
      if (context.getUser() != null) {	
        task.setWho(context.getUser());
        // admin users have access to everything
        if (!context.isUserInRole("admin")
            // if they're using using access permissions in general
            && task.getStore().getPermissionsSpecified()) {
          // other users may have restricted access to some things
          task.setRestrictByUser(context.getUser());
        } // not an admin user
      } else {
        task.setWho(context.getUserHost());
      }        
      task.start();
      
      // return the threadId        
      return successResult(
        Json.createObjectBuilder().add("threadId", ""+task.getId()).build(),
        "Uploaded: {0}", csvFile.getName());
    } catch(Exception ex) {
      if (dir != null) IO.RecursivelyDelete​(dir);
      try {
        httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      } catch(Exception exception) {}
      context.servletLog("POST Upload.post: unhandled exception: " + ex);
      ex.printStackTrace(System.err);
      return failureResult(ex);
    }
  }

} // end of class Upload
