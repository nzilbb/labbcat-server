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
package nzilbb.labbcat.server.task;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.jar.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import nzilbb.util.Execution;
import nzilbb.util.IO;

/**
 * Task that upgrades LaBB-CAT by selectively unpacking a given .war file.
 * @author Robert Fromont robert@fromont.net.nz
 */
public class Upgrader extends Task {
  
  protected String driverName;
  protected String connectionURL;
  protected String connectionName;
  protected String connectionPassword;
  
  protected static Upgrader upgrader = null;  
  /**
   * Provides the currently-running upgrader task, if any.
   * @return The currently-running upgrader task, if any.
   */
  public static Upgrader getUpgrader() {
    return upgrader;
  } // end of getUpgrader()
  
  /**
   * The .war package to upgrade from.
   * @see #getWar()
   * @see #setWar(File)
   */
  protected File war;
  /**
   * Getter for {@link #war}: The .war package to upgrade from.
   * @return The .war package to upgrade from.
   */
  public File getWar() { return war; }
  /**
   * Setter for {@link #war}: The .war package to upgrade from.
   * @param newWar The .war package to upgrade from.
   */
  public Upgrader setWar(File newWar) { war = newWar; return this; }
  
  /**
   * LaBB-CAT root directory, to where the contents of the uploader should be unpacked.
   * @see #getDir()
   * @see #setDir(File)
   */
  protected File dir;
  /**
   * Getter for {@link #dir}: LaBB-CAT root directory, to where the
   * contents of the uploader should be unpacked. 
   * @return LaBB-CAT root directory, to where the contents of the
   * uploader should be unpacked. 
   */
  public File getDir() { return dir; }
  /**
   * Setter for {@link #dir}: LaBB-CAT root directory, to where the
   * contents of the uploader should be unpacked. 
   * @param newDir LaBB-CAT root directory, to where the contents of
   * the uploader should be unpacked. 
   */
  public Upgrader setDir(File newDir) { dir = newDir; return this; }
  
  /**
   * Messages logged by the upgrader.
   * @see #getMessages()
   * @see #setMessages(Vector<String>)
   */
  protected Vector<String> messages = new Vector<String>();
  /**
   * Getter for {@link #messages}: Messages logged by the upgrader.
   * @return Messages logged by the upgrader.
   */
  public Vector<String> getMessages() { return messages; }
  
  /**
   * Subclass constructor.
   */
  public Upgrader() {
  }
  
  /**
   * Constructor.
   * @param war The .war package file to upgrade from.
   * @param dir The directory into which files should be unpacked.
   */
  public Upgrader(
    File war, File dir, String driverName, String connectionURL, String connectionName,
    String connectionPassword) {
    this.war = war;
    this.dir = dir;
    this.driverName = driverName;
    this.connectionURL = connectionURL;
    this.connectionName = connectionName;
    this.connectionPassword = connectionPassword;
  }

  /**
   * Sets the thread status.
   * @param sMessage a status message to display to anyone who's watching the thread.
   */
  @Override public void setStatus(String message) {
    System.err.println(message);
    messages.add(message);
    super.setStatus(message);
  }
  
  /**
   * Runs the upgrade.
   */
  public void run() {
    runStart();
    upgrader = this;
    try {

      setStatus("Upgrading from package " + war.getName() + " ...");
      
      File oldVersionFile = new File(dir, "version.txt");
      String oldVersion = IO.InputStreamToString(new FileInputStream(oldVersionFile));
      JarFile jar = new JarFile(war);
      String newVersion = IO.InputStreamToString(
        jar.getInputStream(jar.getJarEntry("version.txt")));
      setStatus(oldVersion + " → " + newVersion);
      
      // handle migration?
      JarEntry migrateEntry = jar.getJarEntry("WEB-INF/migrate.sql");
      setStatus("WEB-INF/migrate.sql " + migrateEntry);
      if (migrateEntry != null) {
        setStatus("Package includes data for migration...");

        // extract migrate.sql
        File migrateSql = new File(dir, "migrate.sql");
        InputStream in = jar.getInputStream(migrateEntry);
        IO.SaveInputStreamToFile(in, migrateSql);

        setStatus("Importing data from migrate.sql...");
        executeSql(migrateSql);
        setStatus("Data imported from migrate.sql.");
      }
      
      // unpack contents
      String lastFile = null;      
      try {
        Enumeration<JarEntry> enEntries = jar.entries();
        while (enEntries.hasMoreElements()) {
          JarEntry entry = enEntries.nextElement();
          if (!entry.isDirectory()
              && !entry.getName().equals("UnJar.class")
              && !entry.getName().endsWith("web.xml") // just in case
              && !entry.getName().startsWith("META-INF") // just in case
              && !entry.getName().equals("install.jsp") // just in case
              && !entry.getName().equals("local.css") // don't overwrite
              && !entry.getName().equals("custom.css") // don't overwrite
              && !entry.getName().equals("favicon.ico") // don't overwrite
              && !entry.getName().equals("favicon.svg") // don't overwrite
              && !entry.getName().endsWith("migrate.sql") // processed above
            ) {
            File parent = dir;
            String sFileName = entry.getName();
            setStatus("Extracting " + entry.getName());
            lastFile = entry.getName();
            StringTokenizer stPathParts = new StringTokenizer(entry.getName(), "/");
            if (stPathParts.countTokens() > 1) { // complex path
              // ensure that the required directories exist
              sFileName = stPathParts.nextToken();
              
              while(stPathParts.hasMoreTokens()) {
                // previous token was not the last, so it
                // must be a directory
                parent = new File(parent, sFileName);
                if (!parent.exists()) {
                  parent.mkdir();
                }
		
                sFileName = stPathParts.nextToken();
              } // next token
            }
            File file = new File(parent, sFileName);
            
            // get input stream
            InputStream in = jar.getInputStream(entry);

            // unpack file
            IO.SaveInputStreamToFile(in, file);
            
          } else {
            setStatus("Skipping " + entry.getName());
          }
        } // next entry
        war.delete();
        // setStatus("Deleted " + war.getPath());
        
        // trigger restart
        File webXmlFile = new File(new File(dir, "WEB-INF"), "web.xml");
        setStatus("Triggering restart...");
        webXmlFile.setLastModified(new java.util.Date().getTime());
        
      } catch(Throwable exception) {
        setStatus("Error extracting file \"" + lastFile + "\": " + exception);
        setLastException(exception);
      }      
      
    } catch(FileNotFoundException exception) {
      setStatus("Error getting current version: " + exception);
      setLastException(exception);
    } catch(IOException exception) {
      setStatus("Error processing jar: " + exception);
      setLastException(exception);
    } catch(SQLException exception) {
      setStatus("Error extracting migration data: " + exception);
      setLastException(exception);
    } catch(Exception exception) {
      setStatus("Error extracting migration data: " + exception);
      setLastException(exception);
    } finally {
      runEnd();
      bRunning = true; // leaving it 'running' so that the UI waits for restart
      try {
        Thread.sleep(30000); // wait for a while, LaBB-CAT should restart in this time.
      } catch(Exception exception) {
      }
      // If we're still running, it's because Tomcat needs to be manually restarted
      setStatus("LABB-CAT must be restarted. Please restart Tomcat to continue with the upgrade.");

      waitToDie();
      upgrader = null;
    }
  } // end of run()

  
  /**
   * Executes all SQL statements in the given file.
   * @param sqlFile The file to execute.
   * @throws SQLException If there was an SQL error.
   * @throws Exception If there was a general error.
   */
  public void executeSql(File sqlFile) throws SQLException, Exception {
    if (connectionURL.indexOf("mysql") < 0) throw new NullPointerException("Not a mysql connect string");
    Matcher matcher = Pattern.compile("jdbc:[^:]+://([^/]+)/([^/]+)\\?.*")
      .matcher(connectionURL);
    if (!matcher.matches()) {
      throw new Exception(
        "Cannot infer dbHost and dbName from connect string: " + connectionURL);
    }
    String host = matcher.group(1);
    String databaseName = matcher.group(2);
    File mysqlExe = Execution.Which("mysql");
    if (mysqlExe != null) {
      Execution mysql = new Execution()
        .setExe(mysqlExe)
        .arg("--default-character-set=utf8")
        .arg("--force") // ignore errors - some migrations files are sometimes slightly invalid (?!)
        .arg("-h").arg(host)
        .arg("-u").arg(connectionName)
        .arg("-p"+connectionPassword)
        .arg(databaseName)
        .stdin(sqlFile)
        .addStdoutObserver(m->{
            System.out.println(m);
            setStatus(m);
        })
        .addStderrObserver(m->{
            System.err.println(m);
            setStatus(m);
        });
      System.out.println("mysql: " + sqlFile.getPath());
      setStatus("Running mysql...");
      mysql.run();
      
    } else { // no mysql command available      
      setStatus(
        "mysql command not available, falling back to statement-based execution...");
      
      // connect to DB
      //Class.forName(driverName).newInstance();
      Connection connection = DriverManager.getConnection (
        connectionURL, connectionName, connectionPassword);
      
      // read file into a string
      String sql = "";
      BufferedReader br = new BufferedReader(
        new InputStreamReader(new FileInputStream(sqlFile), "UTF-8"));
      String line = br.readLine();
      while (line != null) {
        sql += line + "\n";
        
        // execute statements as we find them...
        if (sql.trim().endsWith(";")) {
          // Ignore DELIMITER blocks created by sqldump TODO check this...
          if (sql.trim().startsWith("DELIMITER")
             // ignore empty statements
             || sql.trim().equals(";")) {
            sql = "";
          } else {
            try {
              connection.prepareStatement(sql).execute();
            } catch (SQLException x) {
              setStatus(sql + " - " + x.getMessage() + "\n");
            }
            sql = "";
          }
        } // semicolon encountered
        line = br.readLine();
      } // next line
        // execute the last statement
      if (sql.trim().length() > 0) {
        connection.prepareStatement(sql).execute();
      }
      setStatus("Finished SQL execution");
    }
  } // end of executeSql()
    
  /**
   * Release resources.
   */
  @Override public void release() {
    if (war != null) war.delete();
    super.release();
  }
  
}
