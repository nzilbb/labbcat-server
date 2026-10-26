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
 * <tt>/api/admin/dashboard/test</tt> : Allows dashboard item definitions to be tested.
 *   <p> The only method supported is:
 *   <dl>
 *    <dt> GET </dt><dd>
 *     <ul>
 *      <li><em> Parameter </em>
 *        <ul>
 *         <li><em> definition </em> The item defintion. </li>
 *        </ul>
 *      </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, returning
 *       as the 'model' the result of the definition.
 *     </ul></dd> 
 *   </dl>
 *  </p>
 * @author Robert Fromont
 */
@RequiredRole("admin")
public class DashboardTest extends APIRequestHandler {
  
  /**
   * Constructor
   */
  public DashboardTest() {
  } // end of constructor
  
  /**
   * Register admin page access.
   * @param parameters Request parameter map.
   * @param httpStatus Receives the response status code, in case of error.
   * @return A JSON object as the request response.
   */
  public JsonObject get(RequestParameters parameters, Consumer<Integer> httpStatus) {
    try {
      try (Connection connection = newConnection()) {
        if (!hasAccess(connection)) {
          httpStatus.accept(SC_FORBIDDEN);
          return null;
        }
        // get thread ID if any
        String definition = parameters.getString("definition");
        if (definition == null) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("No definition supplied"); // TODO i18n
        }
        // only SELECT
        if (!definition.startsWith("SELECT")) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("Invalid definition supplied"); // TODO i18n
        }
        // no passwords
        if (definition.toLowerCase().indexOf("password") >= 0) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("Invalid definition supplied"); // TODO i18n
        }
        // disallow multi-statement queries
        if (definition.indexOf(';') >=0 ) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("Invalid definition supplied"); // TODO i18n
        }
        // return query result
        try (PreparedStatement sql = connection.prepareStatement(definition)) {
          try (ResultSet rs = sql.executeQuery()) {
            String result = "";
            if (rs.next()) {
              result = rs.getString(1);
            }
            return successResult(result, null);
          } // close result set
        } // close sql
      } // close connection
    } catch (SQLException x) {
      System.err.println("api/activity: SQL ERROR: " + x.getMessage());
      x.printStackTrace(System.err);
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      return failureResult(x.getMessage());
    }
  }
} // end of class DashboardTest
