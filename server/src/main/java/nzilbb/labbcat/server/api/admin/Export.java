//
// Copyright 2026 New Zealand Institute of Language, Brain and Behaviour, 
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

package nzilbb.labbcat.server.api.admin;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;
import java.util.regex.Pattern;
import nzilbb.labbcat.server.api.APIRequestHandler;
import nzilbb.labbcat.server.api.RequestParameters;
import nzilbb.labbcat.server.api.RequiredRole;
import nzilbb.labbcat.server.db.SqlGraphStore;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.labbcat.server.db.StoreCache;
import nzilbb.labbcat.server.task.Task;
import nzilbb.sql.DatabaseDump;
import nzilbb.util.Execution;
import nzilbb.util.IO;

/**
 * <tt>/api/admin/export</tt>
 * : Endpoint for exporting the LaBB-CAT webapp, including all data and media.
 * <p> This can be used for backup purposes, or during migration from one server to another.
 * <h3 id="GET"> <tt>/api/admin/export</tt> </h3>
 * <p> <b> GET </b> method requests start the export process, which involves packing.
 * the following elements into a .war file, which is returned as the response:
 * <ol>
 *  <li> a complete backup of LaBB-CAT's relational database, which includes all
 *       transcript and annotation data, </li>
 *  <li> the transcript files and other associateed documents, </li>
 *  <li> all audio/video recordings (if media export is not suppressed), and </li>
 *  <li> the LaBB-CAT web application itself, including all installed formatter and
 *       annotator modules, with their settings.</li>
 * </ol>
 * <p> Possible parameters (all of which are optional) are:
 *  <dl>
 *   <dt> nomedia </dt> <dd> Specify any value to exclude media files
 *        (sound/video recordings, images) from the export. </dd>
 *   <dt> test </dt> <dd> Do not export any files, return an empty .war file
 *        (for testing purposes). </dd>
 *  </dl>
 * <p><b>Output</b>: a .war file that can be installed in the same way as
 *  the <i>labbcat.war</i> file downloaded for new installation.
 *
 * @author Robert Fromont robert@fromont.net.nz
 */
@RequiredRole("admin")
public class Export extends APIRequestHandler { // TODO test

  File root;
  String driverName;
  String connectionURL;
  String connectionName;
  String connectionPassword;

  Execution mysqldump; 
  DatabaseDump dump;
  
  /**
   * Constructor.
   */
  public Export(
    File root, String driverName, String connectionURL, String connectionName,
    String connectionPassword) {
    this.root = root;
    this.driverName = driverName;
    this.connectionURL = connectionURL;
    this.connectionName = connectionName;
    this.connectionPassword = connectionPassword;
  } // end of constructor

  /**
   * The GET method for the servlet.
   * @param root The directory that contains LaBB-CAT.
   * @param parameters Request parameter map.
   * @param out Response body stream.
   * @param contentType Receives the content type for specification in the response headers.
   * @param fileName Receives the filename for specification in the response headers.
   * @param httpStatus Receives the response status code, in case of error.
   */
  public void get(
    RequestParameters requestParameters, OutputStream out, Consumer<String> contentType,
    Consumer<String> fileName, Consumer<Integer> httpStatus) {
    context.servletLog("GET " + requestParameters.keySet());
    boolean noMedia = requestParameters.getString("nomedia") != null;
    boolean testOnly = requestParameters.getString("test") != null;
    final File dirFiles = new File(root, "files");
    try {
      SqlGraphStoreAdministration store = getStore();
      try {
        if (!hasAccess(store.getConnection())) {
          httpStatus.accept(SC_FORBIDDEN);
          return;
        }

        Task exporter = new Task() {
            public void setStatus(String message) {
              super.setStatus(message);
              context.servletLog(message);
            }
            public void cancel() {
              super.cancel();
              if (mysqldump != null) { // we're running mysqldump
                if (mysqldump.getProcess() != null) {
                  // kill the process
                  mysqldump.getProcess().destroy();
                }
              }
              // ensure the alternative (dump) doesn't start up instead:
              mysqldump = null;
              if (dump != null) dump.cancel();
            }
            int packedFiles = 0;
            public void run() {
              try {
                runStart();
                try {
                  context.servletLog("Starting thread...");
                  
                  // open the war stream
                  contentType.accept("application/zip");
                  fileName.accept(root.getName()+".war");
                  setStatus("Composing "+root.getName()+".war ...");
                  iPercentComplete = 1;
                  try (ZipOutputStream war = new ZipOutputStream(out)) {
                    SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssX");
                    war.setComment(
                      store.getSystemAttribute("title")            
                      +"\nExported: "+ iso.format(new Date())
                      +"\nversion " + context.getVersion());
                    war.flush(); // ... so the browser gets an immediate response
                    if (!testOnly) {
                      
                      // backup database to admin/migrate.sql
                      if (!bCancelling) {
                        setStatus("Exporting relational database...");
                        File migrateSql = backupDatabase((status)->setStatus(status));
                        try {
                          if (!bCancelling) {
                            iPercentComplete = 10;
                            
                            // add admin/migrate.sql to the stream as /WEB-INF/migrate.sql
                            setStatus("Packing database backup...");
                            war.putNextEntry(new ZipEntry("WEB-INF/migrate.sql"));
                            try (FileInputStream in = new FileInputStream(migrateSql)) {
                              IO.Pump(in, war, false);
                            } // close input stream
                            packedFiles++;
                            iPercentComplete = 20;
                          } // not cancelling                          
                        } finally { // delete migrate.sql
                          migrateSql.delete();
                        }
                      } // not cancelling
                      
                      // add WEB-INF/web_blank.xml to the stream as /WEB-INF/web.xml
                      File webBlankXml = new File(new File(root, "WEB-INF"), "web_blank.xml");
                      war.putNextEntry(new ZipEntry("WEB-INF/web.xml"));
                      try (FileInputStream in = new FileInputStream(webBlankXml)) {
                        IO.Pump(in, war, false);
                      } // close input stream
                      packedFiles++;
                      
                      // pack (almost) everything into the war stream
                      final Set<String> corpora = new HashSet<String>(
                        Arrays.asList(store.getCorpusIds()));
                      final Vector<Pattern> forbiddenPatterns = new Vector<Pattern>();
                      // no upload backup files
                      forbiddenPatterns.add( // transcript/media backups
                        Pattern.compile(
                          ".+\\.bak-[0-9]{4}-[0-9]{2}-[0-9]{2}-[0-9]{2}-[0-9]{2}-[0-9]{2}\\..+"));
                      forbiddenPatterns.add(Pattern.compile("web\\.xml"));
                      forbiddenPatterns.add( // web.xml backups
                        Pattern.compile(
                          "web\\.xml-[0-9]{4}-[0-9]{2}-[0-9]{2}-[0-9]{2}-[0-9]{2}\\.bak"));
                      forbiddenPatterns.add(Pattern.compile("labbcat.*\\.war"));
                      forbiddenPatterns.add(Pattern.compile("labbcat.*\\.zip"));
                      forbiddenPatterns.add(Pattern.compile("labbcat.*\\.sig"));
                      forbiddenPatterns.add(Pattern.compile("war-tracker"));
                      if (noMedia) {
                        forbiddenPatterns.add(Pattern.compile(".+\\.wav$"));
                        forbiddenPatterns.add(Pattern.compile(".+\\.mp3$"));
                        forbiddenPatterns.add(Pattern.compile(".+\\.au$"));
                        forbiddenPatterns.add(Pattern.compile(".+\\.avi$"));
                        forbiddenPatterns.add(Pattern.compile(".+\\.mpg$"));
                        forbiddenPatterns.add(Pattern.compile(".+\\.mpeg$"));
                        forbiddenPatterns.add(Pattern.compile(".+\\.mp4"));
                        forbiddenPatterns.add(Pattern.compile(".+\\.f0"));
                        forbiddenPatterns.add(Pattern.compile(".+\\.pm"));
                      }
                      // include or exclude media files?
                      FileFilter filter = new FileFilter() {
                          public boolean accept(File f) {
                            if (!f.canRead()) return false; // skip files we don't have access to
                            if (f.isHidden() // don't export hidden files
                                // except '.html', the doc root!
                                && !f.getName().equals(".html")) { 
                              return false;
                            }
                            if (f.getName().equals("context.xml")
                                && f.getParentFile().getName().equals("META-INF")) {
                              return false;
                            }
                            if (f.getName().equals("standalone")
                                && f.getParentFile().getName().equals("ling")) {
                              return false;
                            }
                            // if it's in the 'files' directory,
                            // only accept corpus subdirectories
                            if (f.getParentFile().equals(dirFiles)) {
                              // and annotators
                              return f.getName().equals("annotators")
                                // and layermanagers
                                || f.getName().equals("layermanagers") 
                                || (f.isDirectory() && corpora.contains(f.getName()));
                              // otherwise accept everything except backup files
                            } else if (f.isDirectory()) {
                              return true;
                            } else {
                              for (Pattern pattern : forbiddenPatterns) {
                                if (pattern.matcher(f.getName().toLowerCase()).matches()) {
                                  return false;
                                }
                              }
                              return true;
                            }
                          }
                        };
                      if (!bCancelling) {
                        final int fileCount = IO.RecursivelyCountFiles(root, filter);
                        if (!bCancelling) {
                          setStatus("Packing "+(fileCount+2)+" files...");
                          IO.RecursivelyZip(root, war, filter, (name)->{
                              setStatus(name);
                              packedFiles++;
                              iPercentComplete = 20 + ((80 * packedFiles) / fileCount);
                            }, ()->bCancelling);
                          setStatus("Packed "+packedFiles+" files.");
                        } // not cancelling
                      } // not cancelling
                    } // !testOnly
                  } // close war
                  if (!bCancelling) {
                    setStatus("Export complete.");
                  } else {
                    setStatus("Cancelled.");
                  }
                  
                } catch (Throwable x) {
                  setLastException(x);
                }
                synchronized (this) {
                  this.notifyAll();
                }
              } finally {
                runEnd();
              }
              waitToDie();
            } // run
          };
        exporter.setName("Export LaBB-CAT"); // parent dir is a unique version of the name
        if (context.getUser() != null) {
          exporter.setWho(context.getUser());
        } else {
          exporter.setWho(context.getUserHost());
        }
        exporter.start();
        synchronized (exporter) {
          exporter.wait();
        }
        
        if (exporter.getLastException() != null) {
          try {
            httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
          } catch(Exception exception) {}
          context.servletLog(
            "GET Export exporter: unhandled exception: " + exporter.getLastException());
          exporter.getLastException().printStackTrace(System.err);
        }

      } finally {
        cacheStore(store);
      }
    } catch(Throwable ex) {
      try {
        httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      } catch(Exception exception) {}
      context.servletLog("GET Export.get: unhandled exception: " + ex);
      ex.printStackTrace(System.err);
    }
  }

  /**
   * Creates a backup of the relational database, and returns the file containing the
   * backup.
   * @return Backup file.
   */
  public File backupDatabase(Consumer<String> status) throws Exception {
    File migrateSql = File.createTempFile("migrate-", ".sql");
    migrateSql.deleteOnExit();
    status.accept("Backup file: "+migrateSql.getPath());
    int lastSlash = connectionURL.lastIndexOf("/");
    int queryMarker = connectionURL.indexOf('?');
    if (queryMarker <= 0) queryMarker = connectionURL.length();
    String hostName = connectionURL.substring("jdbc:mysql://".length(), lastSlash);
    String dbName = connectionURL.substring(lastSlash + 1, queryMarker);
    try {
      mysqldump = new Execution().setExe("mysqldump")
        .arg("-h").arg(hostName)
        .arg("-u").arg(connectionName)
        .arg("-p"+connectionPassword)
        .arg("-f")
        .arg("--skip-triggers")
        .arg("--default-character-set=utf8")
        .arg("--result-file="+migrateSql.getPath())
        .arg(dbName);
      
      status.accept("Running mysqldump on " + dbName + " (" + hostName + ") ...");
      mysqldump.run();
      if (mysqldump == null) { // cancelled
        throw new NullPointerException("Cancelled.");
      }
      String error = mysqldump.getError().toString();
      if (error.length() == 0) {
        status.accept("mysqldump complete.");
      } else { // errors means something went wrong, maybe mysqldump is not even available
        if (mysqldump != null) { // if it's null, we're cancelling...
          status.accept(error);
          status.accept("Using SQL queries for database export instead of mysqldump...");
          
          dump = new DatabaseDump(
            driverName, connectionURL, connectionName, connectionPassword);
          dump.getCurrentTableObservers().add((t)->{
              if (t != null) status.accept("Table: " + t);
            });          
          dump.dump(migrateSql);
        } // use DatabaseDump instead
      }
      return migrateSql;
    } finally {
      mysqldump = null;
      dump = null;
    }
  } // end of backupDatabase()

} // end of class Export
