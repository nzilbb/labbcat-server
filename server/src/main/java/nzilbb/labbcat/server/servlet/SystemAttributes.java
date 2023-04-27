//
// Copyright 2020 New Zealand Institute of Language, Brain and Behaviour, 
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

import java.sql.Connection;
import java.util.List;
import java.util.Vector;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;

/**
 * <tt>/api/systemattributes/<var>name</var></tt>
 * : Access to system attributes.
 *  <p> Allows access to the value of a given system attribute, returning a
 *  JSON-encoded objects with the following attributes:
 *   <ul>
 *    <li> <q> name </q> : ID of the attribute. </li>
 *    <li> <q> value </q> : The value of the attribute. </li>
 *   </ul>
 *   GET HTTP method is supported,
 *   <dl>
 *    <li>Only the <b> GET </dt><dd> HTTP method is supported.
 *     <ul>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object with the above structure.  </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The attribute was found. </li>
 *         <li><em> 404 </em> : The attribute was not found. </li>
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *   </dl>
 *  </p>
 * @author Robert Fromont robert@fromont.net.nz
 */
@WebServlet(urlPatterns = "/api/systemattributes/*", loadOnStartup = 20)
public class SystemAttributes extends TableServletBase {   
   
   public SystemAttributes() {
      super("system_attribute", // table
            new Vector<String>() {{ // primary keys
               add("name");
            }},
            new Vector<String>() {{ // columns
               add("value");
            }},
            "name"); // order
      // keep sensitive information out of view-only user's eyes
      whereClause = "name NOT LIKE '%SMTP%'" // no mail server details
         +" AND name NOT LIKE '%password%'" // no passwords
         +" AND name NOT LIKE '%email%'"; // no email addresses
   }
   
   private static final long serialVersionUID = 1;
} // end of class SystemAttributes
