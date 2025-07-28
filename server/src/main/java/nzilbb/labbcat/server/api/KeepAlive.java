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

import java.util.function.Consumer;
import nzilbb.labbcat.server.task.Task;

/**
 * <tt>/keepalive</tt> or <tt>/api/keepalive</tt> : Keeps the current session/task alive.
 *  <p> This enpoint is intended to that the user's session, and a given task, is not
 *  disposed of even if they don't browse to a new page for a long time.
 *   <p> The only method supported is:
 *   <dl>
 *    <dt> GET </dt><dd>
 *     <ul>
 *      <li><em> Optional parameter </em>
 *        <ul>
 *         <li><em> threadId </em> A thread to keep from being cleaned up. </li>
 *        </ul>
 *      </li>
 *      <li><em> Response Body </em> - no body is returned.
 *      <li><em> Response Status </em> - always <em> 200 </em>. </li>
 *     </ul></dd> 
 *   </dl>
 *  </p>
 * @author Robert Fromont
 */
public class KeepAlive extends APIRequestHandler {
  
  /**
   * Constructor
   */
  public KeepAlive() {
  } // end of constructor
  
  /**
   * Keeps session/task alive.
   * @param parameters Request parameter map.
   * @param refreshHeader Receives the value for the Refresh header.
   */
  public void get(RequestParameters parameters, Consumer<Integer> refreshHeader) {
    int refreshSeconds = 300; // refresh every 50 minutes
    // get thread ID if any
    String id = parameters.getString("threadId");
    if (id != null) {
      try {
        long threadId = Long.parseLong(id);
        Task task = Task.findTask(threadId);
        if (task != null) {
          task.keepAlive();
          // refresh more often to stop the thread from dying
          refreshSeconds = 30;
        }
      } catch(NumberFormatException exception) {
      }
    }
    refreshHeader.accept(refreshSeconds);
  }
} // end of class KeepAlive
