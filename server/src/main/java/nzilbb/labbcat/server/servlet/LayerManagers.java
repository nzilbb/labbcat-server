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
 * <tt>/api/layers/managers/</tt>
 * : Access to layer managers.
 *  <p> Allows access a list of currently-installed layer managers and their attributes,
 *   returning a JSON-encoded array of objects with the following attributes:
 *   <ul>
 *    <li> <q> layer_manager_id </q> : ID of the layer manager. </li>
 *    <li> <q> version </q> : The version of the installed implementation. </li>
 *    <li> <q> name </q> : The name for the layer manager. </li>
 *    <li> <q> description </q> : A brief description of what the layer manager does. </li>
 *    <li> <q> layer_type </q> : What kinds of layers the layer manager can process - a
 *             string composed of any of the following:
 *              <ul>
 *               <li><b> S </b> - segment layers </li>
 *               <li><b> W </b> - word layers </li>
 *               <li><b> M </b> - phrase (meta) layers </li>
 *               <li><b> F </b> - span (freeform) layers </li>
 *              </ul>
 *    </li>
 *   </ul>
 *   GET HTTP method is supported,
 *   <dl>
 *    <li>Only the <b> GET </dt><dd> HTTP method is supported.
 *     <ul>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       array of objects with the above structure.  </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : Success. </li>
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *   </dl>
 *  </p>
 * @author Robert Fromont robert@fromont.net.nz
 */
public class LayerManagers extends TableServletBase {   
  // TODO deprecate this servlet when new automation API is implemented
  public LayerManagers() {
    super("layer_manager", // table
          new Vector<String>() {{ // primary keys
            add("layer_manager_id");
          }},
          new Vector<String>() {{ // columns
            add("version");
            add("name");
            add("description");
            add("layer_type");
          }},
          "layer_manager_id"); // order
  }  
} // end of class LayerManagers
