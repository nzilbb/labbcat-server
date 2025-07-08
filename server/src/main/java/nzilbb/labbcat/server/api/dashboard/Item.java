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
package nzilbb.labbcat.server.api.dashboard;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Vector;
import java.text.DecimalFormat;
import java.util.function.Consumer;
import javax.json.JsonObject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import nzilbb.labbcat.server.api.Dashboard;

/**
 * <tt>/api/dashboard/item/<var>item_id</var></tt>
 * : The value of one dashboard item.
 *  <p> Evaluate the given dashboard item, which is an <q>item_id</q> returned by
 *  <tt>/api/dashboard</tt>, <tt>/api/express</tt>, or <tt>/api/statistics</tt>
 *   <dl>
 *    <li>Only the <b> GET </dt><dd> HTTP method is supported.
 *     <ul>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as a
 *       string containing the value of the given item.  </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The item was evaluated. </li>
 *         <li><em> 400 </em> : No item ID was specified. </li>
 *         <li><em> 404 </em> : The specified item ID was not valid. </li>
 *         <li><em> 500 </em> : The item could not be evaluated. </li>
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *   </dl>
 *  </p>
 * @author Robert Fromont robert@fromont.net.nz
 */
public class Item extends Dashboard {
  DecimalFormat num = new DecimalFormat();
  DecimalFormat num2 = new DecimalFormat("00");
  DecimalFormat num22 = new DecimalFormat("00.00");
  
  public Item() {
  }
  
  /**
   * Generate the response to a request.
   * <p> This returns information about the current user - their ID and the roles they have.
   * @param pathInfo The URL path.
   * @param httpStatus Receives the response status code, in case of error.
   * @return A JSON object as the request response.
   */
  public JsonObject get(String pathInfo, Consumer<Integer> httpStatus) {
    if (pathInfo == null || pathInfo.length() == 0 || pathInfo.length() == 1) {
      httpStatus.accept(SC_BAD_REQUEST);
      return failureResult("No ID specified.");
    }    
    try {
      int item_id = Integer.parseInt(pathInfo.substring(1)); // strip leading slash
      Connection db = newConnection();
      PreparedStatement sql = db.prepareStatement(
        "SELECT label, definition, type FROM dashboard_item WHERE item_id = ?");
      sql.setInt(1, item_id);
      ResultSet rs = sql.executeQuery();
      try {
        if (!rs.next()) {
          httpStatus.accept(SC_NOT_FOUND);
          return failureResult("Invalid ID: {0}", pathInfo);
        }
        String definition = rs.getString("definition");
        String type = rs.getString("type");
        if ("sql".equals(type)) {
          rs.close();
          sql.close();
          try {
            sql = db.prepareStatement(definition);
            rs = sql.executeQuery();
            if (!rs.next()) {
              return successResult("?", null);
            } else {
              // does it look like a duration result?
              if (definition.contains("offset")) {
                return successResult(hoursMinutesSeconds(rs.getDouble(1)), null);
              } else {
                return successResult(num.format(rs.getDouble(1)), null);
              }
            }
          } catch (Exception x) {
            httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
            return failureResult("Invalid definition: {0}", x.getMessage());
          }
        } else if (type.equals("exec")) {
          try {
            String cmd = "sh";
            if (System.getProperty("os.name").startsWith("Windows")) {
              // windows needs the command as command-line arguments
              cmd = "cmd.exe /C " + definition;
            }
            Process process = Runtime.getRuntime().exec(cmd);
            if (cmd.equals("sh")) {
              // to support pipes, bash prefers to read commands from stdin
              process.getOutputStream().write(definition.getBytes());
              process.getOutputStream().close();
            }
            InputStream inStream = process.getInputStream();
            InputStream errStream = process.getErrorStream();
            byte[] buffer = new byte[1024];
            StringBuilder output = new StringBuilder();
            StringBuilder error = new StringBuilder();
            
            // loop waiting for the process to exit, all the while reading from
            //  the input stream to stop it from hanging
            // there seems to be some overhead in querying the input streams,
            // so we need sleep while waiting to not barrage the process with
            // requests. However, we don't want to sleep too long for processes
            // that terminate quickly or we'll be needlessly waiting.
            // So we start with short sleeps, and exponentially increase the 
            // wait time, with a maximum sleep of 30 seconds
            int iMSSleep = 1;
            boolean running = true;
            while (running) {
              
              try {
                int iReturnValue = process.exitValue();		     
                // if exitValue returns, the process has finished
                running = false;
              }
              catch(IllegalThreadStateException exception) { // still executing
                // sleep for a while
                try {
		  Thread.sleep(iMSSleep);
                } catch(Exception sleepX) {
		  context.servletLog(
                    "Execution: " + definition + " Exception while sleeping: "
                    + sleepX.toString());	
                }
                iMSSleep *= 2; // backoff exponentially
                
                if (iMSSleep > 16000) { // already waited for half a minute
                  // which is too long
		  context.servletLog(
                    "Dashboard Item " + item_id + " takes too long and is being killed");
                  process.destroy();
                }
                if (iMSSleep > 32000) { // already waited for a minute
                  // which is way too long
                  process.destroyForcibly();
                }
              }
              
              try {
                // data ready?
                int bytesRead = inStream.available();
                String sMessages = "";
                while(bytesRead > 0) {
		  // if there's data coming, sleep a shorter time
		  iMSSleep = 1;		     
		  // write to the log file
		  bytesRead = inStream.read(buffer);
		  output.append(new String(buffer, 0, bytesRead));
		  // data ready?
		  bytesRead = inStream.available();
                } // next chunk of data	       
              } catch(IOException exception) {
                context.servletLog(
                  "Execution: ERROR reading conversion input stream: "
                  + definition + " - " + exception);
              }
              
              try {
                // data ready from error stream?
                int bytesRead = errStream.available();
                while(bytesRead > 0) {
		  // if there's data coming, sleep a shorter time
		  iMSSleep = 1;	    
		  bytesRead = errStream.read(buffer);
		  error.append(new String(buffer, 0, bytesRead));
		  context.servletLog("Execution: " + definition + ": " + new String(buffer, 0, bytesRead));
		  // data ready?
		  bytesRead = errStream.available();
                } // next chunk of data
              } catch(IOException exception) {
                context.servletLog(
                  "Execution: ERROR reading conversion error stream: "
                  + definition + " - " + exception);
              }
            } // running
            String result = output.toString().trim();
            if (error.length() > 0 && result.length() == 0) {
              httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
              return failureResult(error.toString());
            } else {
              return successResult(result, null);
            }
          } catch (Exception x) {
            httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
            return failureResult("Invalid definition: {0}", x.getMessage());
          }
        } else if (type.equals("link")) {
          return successResult(definition, null);
        } else {
          httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
          return failureResult("Invalid type: {0}", type);
        }
      } finally {
        try { rs.close();  } catch(Throwable exception) {}
        try { sql.close(); } catch(Throwable exception) {}
        try { db.close();  } catch(Throwable exception) {}
      }
    } catch(NumberFormatException exception) {
      httpStatus.accept(SC_BAD_REQUEST);
      return failureResult("Invalid ID: {0}", pathInfo);
    } catch(SQLException exception) {
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      return failureResult(exception.getMessage());
    }
  }
  
  /** Utility function to express a number of seconds as hours:minutes:seconds */
  private String hoursMinutesSeconds(double dSeconds) {
    int iHours = (int)(dSeconds / 3600);
    dSeconds -= (iHours * 3600);
    int iMinutes = (int)(dSeconds / 60);
    dSeconds -= (iMinutes * 60);
    return num.format(iHours) + ":" + num2.format(iMinutes) + ":" + num22.format(dSeconds);
  }
} // end of class Item
