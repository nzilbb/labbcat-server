//
// (c) 2015, Robert Fromont - robert@fromont.net.nz
//
//
//    This module is free software; you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation; either version 2 of the License, or
//    (at your option) any later version.
//
//    This module is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this module; if not, write to the Free Software
//    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
package nzilbb.labbcat.server.servlet;

import java.util.ResourceBundle;
import nzilbb.sql.ConnectionFactory;

/**
 * An object that can answer questions about the context of a request, for example the
 * user, configuration parameters, etc.
 * @author Robert Fromont robert@fromont.net.nz
 */
public interface APIRequestContext {
  
  /**
   * Access the title of the request endpoint.
   * @return The title of the endpoint.
   */
  public String getTitle();
  
  /**
   * Determine the version of the server software.
   * @return The server version.
   */
  public String getVersion();
  
  /**
   * The ID of the logged-in user.
   * @return The ID of the logged-in user, on null if no user is logged in.
   */
  public String getUser();
  
  /**
   * Determines whether the logged-in user is in the given role.
   * @param role The desired role.
   * @return true if the user is in the given role, false otherwise.
   */
  public boolean isUserInRole(String role);
  
  /**
   * Access the value of an instance-wide named parameter.
   * @param name The name of the parameter.
   * @return The value of the named parameter.
   */
  public String getInitParameter(String name);
  
  /**
   * Access the localization resources for the correct locale.
   * @return The localization resources for the correct local.
   */
  public ResourceBundle getResourceBundle();
  
  /**
   * Accesses a database connection factory.
   * @return An object that can provide database connections.
   */
  public ConnectionFactory getConnectionFactory();
  
} // end of APIRequestContext
