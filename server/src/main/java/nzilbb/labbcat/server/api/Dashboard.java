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

import java.sql.Connection;
import java.util.List;
import java.util.Vector;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;

/**
 * <tt>/api/dashboard</tt>
 * : Access to dashboard items.
 *  <p> Lists items for the home page dashboard, with the following attributes:
 *   <ul>
 *    <li> <q> item_id </q> : ID of the item. </li>
 *    <li> <q> type </q> : The type of the item: "link", "sql", or "exec". </li>
 *    <li> <q> label </q> : The item's text label. </li>
 *    <li> <q> icon </q> : The item's icon. </li>
 *   </ul>
 *   <dl>
 *    <li>Only the <b> GET </dt><dd> HTTP method is supported.
 *     <ul>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object with the above structure.  </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The attributes were listed. </li>
 *        </ul>
 *      </li>
 *     </ul></dd> 
 *   </dl>
  * <p> These are generally single statistics about the corpus that are displayed
 *   on the home page, which are individually configurable.
 *   However items can also be links, or the output of a command. 
* <p> <tt>/api/dashboard/item/<var>item_id</var></tt> can be used to evaluate each item.
 * @author Robert Fromont robert@fromont.net.nz
 */
public class Dashboard extends TableServletBase {
  public Dashboard() {
    super("dashboard_item", // table
          new Vector<String>() {{ // primary keys
            add("type");
            add("item_id");
          }},
          new Vector<String>() {{ // columns
            add("label");
            add("icon");
          }},
          "display_order, label"); // order
    whereClause = "dashboard = 1";
  }
} // end of class Dashboard
