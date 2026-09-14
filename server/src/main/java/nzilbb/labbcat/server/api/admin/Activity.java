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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import nzilbb.labbcat.server.api.APIRequestHandler;
import nzilbb.labbcat.server.api.RequestParameters;
import nzilbb.labbcat.server.api.RequiredRole;

/**
 * <tt>/api/admin/activity</tt> : Tracks current user activity on certain admin pages.
 *  <p> Pages that involve editing administration settings may be open for long periods
 *   while the user is editing configuration. If multiple users are editing the same
 *   settings simulataneously, saving can overwrite the changes recently made by another
 *   user. In order to prevent this, current access to certain pages is tracked, so that
 *   users can see whether another user is currently changing settings.
 *  <p> The user interface of such pages regularly polls this endpoint to both register
 *   the user's access, and also monitor the access of other users.
 *   The polling request's user ID is inferred from their login session, and the
 *   <tt>Referer</tt> request header is used to infer which page they have open.
 *   <p> The only method supported is:
 *   <dl>
 *    <dt> GET </dt><dd>
 *     <ul>
 *      <li><em> Optional parameters </em>
 *        <ul>
 *         <li><em> resource </em> Resource identifier, which defaults to the referer. </li>
 *         <li><em> viewport </em> A unique identifier for the browser window/tab,
 *             allowing users with multiple tabs open to see other tabs in the list
 *             of other users. </li>
 *        </ul>
 *      </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, returning
 *       as the 'model' an array of strings representing IDs of other users that
 *       currently have the page open. If the user's own ID is in this list, it's
 *       because they have the page open in more than one viewport (browser tab).
 *      <li><em> Response Status </em> - <em> 200 </em>
 *        unless the page being viewed cannot be inferred, in which case <em> 400 </em>
 *        is returned. </li>
 *     </ul></dd> 
 *   </dl>
 *  </p>
 * @author Robert Fromont
 */
@RequiredRole("admin")
public class Activity extends APIRequestHandler {
  
  /**
   * Constructor
   */
  public Activity() {
  } // end of constructor
  
  /**
   * Register admin page access.
   * @param parameters Request parameter map.
   * @param requestHeaders Access to HTTP request headers.
   * @param httpStatus Receives the response status code, in case of error.
   * @return A JSON object as the request response.
   */
  public JsonObject get(
    RequestParameters parameters, UnaryOperator<String> requestHeaders,
    Consumer<Integer> httpStatus) {
    try {
      try (Connection connection = newConnection()) {
        if (!hasAccess(connection)) {
          httpStatus.accept(SC_FORBIDDEN);
          return null;
        }
        // get thread ID if any
        String resource = Optional.ofNullable(parameters.getString("resource"))
          .orElse(requestHeaders.apply("Referer"));
        if (resource == null) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("Could not determine resource."); // TODO i18n
        }
        String user = Optional.ofNullable(context.getUser())
          .orElse(context.getUserHost());
        String viewport = Optional.ofNullable(parameters.getString("viewport")).orElse("");
        
        JsonArrayBuilder otherUserIds = Json.createArrayBuilder();
        
        // delete all activity older that 10 seconds
        try (PreparedStatement delete = connection.prepareStatement(
               "DELETE FROM activity WHERE last_seen < ADDTIME(Now(), '-00:00:10')")) {
          delete.executeUpdate();
        } // close delete
        
        // register this user's activity for this resource
        try (PreparedStatement replace = connection.prepareStatement(
               "REPLACE INTO activity (resource, user_id, viewport) VALUES (?,?,?)")) {
          replace.setString(1, resource);
          replace.setString(2, user);
          replace.setString(3, viewport);
          replace.executeUpdate();
        } // close insert
      
        // return all other users' (or other viewports for this user) activity
        try (PreparedStatement sql = connection.prepareStatement(
               "SELECT DISTINCT user_id FROM activity"
               +" WHERE resource = ? AND (user_id != ? OR viewport != ?)"
               +" ORDER BY user_id")) {
          sql.setString(1, resource);
          sql.setString(2, user);
          sql.setString(3, viewport);
          try (ResultSet otherUsers = sql.executeQuery()) {
            while (otherUsers.next()) {
              otherUserIds.add(otherUsers.getString(1));
            }
          } // close result set
        } // close sql
        return successResult(otherUserIds.build(), null);
      } // close connection
    } catch (SQLException x) {
      System.err.println("api/activity: SQL ERROR: " + x.getMessage());
      x.printStackTrace(System.err);
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      return failureResult("Unexpected error.");
    }
  }
} // end of class Activity
