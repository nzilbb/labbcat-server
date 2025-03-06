//
// Copyright 2019-2020 New Zealand Institute of Language, Brain and Behaviour, 
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

import java.io.*;
import java.net.*;
import javax.servlet.*; // d:/jakarta-tomcat-5.0.28/common/lib/servlet-api.jar
import javax.servlet.http.*;
import javax.servlet.annotation.WebServlet;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.xml.parsers.*;
import javax.xml.xpath.*;
import org.w3c.dom.*;
import org.xml.sax.*;
import nzilbb.configure.ParameterSet;
import nzilbb.ag.GraphStoreAdministration;
import nzilbb.ag.Schema;
import nzilbb.ag.Graph;
import nzilbb.ag.serialize.GraphSerializer;
import nzilbb.ag.serialize.SerializationException;
import nzilbb.ag.serialize.util.ConfigurationHelper;
import nzilbb.ag.serialize.util.NamedStream;
import nzilbb.ag.serialize.util.Utility;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.util.IO;

/**
 * <tt>/api/user</tt> : information about the current user.
 *  <p> Allows access to information about the current user, returning a
 *  JSON-encoded objects with the following attributes:
 *   <dl>
 *    <dt> user </dt><dd> ID of the user. </dd>
 *    <dt> roles </dt><dd> An array of strings, which are the roles or groups the user
 *                          belongs to. </dd> 
 *   </dl>
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
public class User extends APIRequestHandler {
  
  /**
   * Constructor
   */
  public User() {
  } // end of constructor
  
  /**
   * Generate the response to a request.
   * <p> This returns information about the current user - their ID and the roles they have.
   * @param jsonOut A JSON generator for writing the response to.
   */
  public void get(JsonGenerator jsonOut) {      
    startResult(jsonOut, false);
    String user = context.getUser();
    if (user != null) {
      jsonOut.write("user", user);
    }
    jsonOut.writeStartArray("roles");
    try {
      if (user == null) { // not using authentication
        jsonOut
          .write("view")
          .write("edit")
          .write("admin");
      } else {
        Connection db = newConnection();
        PreparedStatement sqlUserGroups = db.prepareStatement(
          "SELECT role_id FROM role WHERE user_id = ?");
        sqlUserGroups.setString(1, user);
        ResultSet rstUserGroups = sqlUserGroups.executeQuery();
        while (rstUserGroups.next()) {
          jsonOut.write(rstUserGroups.getString("role_id"));
        } // next group
        rstUserGroups.close();
        sqlUserGroups.close();
        db.close();
      }
      jsonOut.writeEnd(); // array
      endSuccessResult(jsonOut, null);
    } catch(SQLException exception) {
      jsonOut.writeEnd(); // array
      endFailureResult(jsonOut, exception.getMessage());
    }
  }
} // end of class User
