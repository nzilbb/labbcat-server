//
// Copyright 2025-2026 New Zealand Institute of Language, Brain and Behaviour, 
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

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.ResourceBundle;
import javax.xml.parsers.*;
import javax.xml.xpath.*;
import nzilbb.sql.ConnectionFactory;
import nzilbb.util.IO;
import nzilbb.util.SemanticVersionComparator;
import org.w3c.dom.*;
import org.xml.sax.*;

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
   * Get the base URL for the server.
   * @return The base URL for the server, or null if it can't be determined.
   */
  public String getBaseUrl();
  
  /**
   * Get the base parth for the servlet.
   * @return Get the base parth for the servlet, or null if it can't be determined.
   */
  public String getServletPath();
  
  /**
   * The ID of the logged-in user.
   * @return The ID of the logged-in user, on null if no user is logged in.
   */
  public String getUser();
  
  /**
   * The IP/host name of the user's connection.
   * @return The IP/host name of the user's connection, or null if not available.
   */
  public String getUserHost();

  /**
   * Returns the path portion of the request URL.
   * @return The path portion of the request URL.
   */
  public String getPathInfo();

  /**
   * Provides access to a given header of the request.
   * @param name The name of the header.
   * @return The request header.
   */
  public String getRequestHeader(String name);
  
  /**
   * Add the given given header to the response.
   * @param name Header name.
   * @param value Header value.
   */
  public void addResponseHeader(String name, String value);
  
  /**
   * Provides access to a given attribute of the request.
   * @param name The name of the attribute.
   * @return The request attribute.
   */
  public Object getRequestAttribute(String name);
  
  /**
   * Sets the value of a given attribute of the request.
   * @param name The name of the attribute.
   * @param value The value for the attribute.
   */
  public void setRequestAttribute(String name, Object value);

  /**
   * Provides access to a given attribute of the user session.
   * @param name The name of the attribute.
   * @return The session attribute.
   */
  public Object getSessionAttribute(String name);
  
  /**
   * Sets the value of a given attribute of the user session.
   * @param name The name of the attribute.
   * @param value The value for the attribute.
   */
  public void setSessionAttribute(String name, Object value);
  
  /**
   * Provides access to a given attribute of the serlvet context.
   * @param name The name of the attribute.
   * @return The context attribute.
   */
  public Object getServletContextAttribute(String name);
  
  /**
   * Sets the value of a given attribute of the serlvet context.
   * @param name The name of the attribute.
   * @param value The new value for the attribute.
   */
  public void setServletContextAttribute(String name, Object value);
  
  /**
   * Provides the local file corresponding to the given path within the servlet context.
   * @param path The path within the servlet context.
   * @return The local path corresponding to the given path.
   */
  public String getRealPath(String path);
  
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
   * Generates an instance-wide notification that an underlying object has been updated,
   * and cached versions of that object should be flushed. 
   * @param name Name of the object that has been updated.
   */
  public void cacheNotification(String name);
  
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
  
  /**
   * Log a message.
   * @param message The message to add to the log.
   */
  public void servletLog(String message);
  
  /**
   * Log an exception.
   * @param exception The exception to add a stack-trace of to the log.
   */
  default public void servletLog(Throwable exception) {
    StringWriter sw = new StringWriter();
    exception.printStackTrace(new PrintWriter(sw));
    servletLog(sw.toString());
  }
  
  /**
   * Determines whether the logged-in user is in the given role.
   * <p> Side effects:
   * <ul>
   *  <li>The session has a possibly new attribute called "security" which indicates what
   *      type of security is being used; one of "none", "JDBCRealm", "JNDIRealm"</li>
   *  <li>The session has a possibly new set of attributes called "group_<i>role</i>",
   *      one for each role the user has.</li>
   *  <li>The request may have a new attribute called "reset_password", if they are
   *      marked for resetting their password.</li>
   * </ul>
   * @param role The desired role.
   * @param db A connected database connection.
   * @return true if the user is in the given role, false otherwise.
   */
  default public boolean isUserInRole(String role, Connection db)
    throws SQLException {    
    if ("/login".equals(getPathInfo())) { // if they haven'e logged in yet...
      return false; // ...they're not in any role
    }
    // load user groups
    if (getSessionAttribute("security") == null) {
      // check ser server context for already-loaded info
      try {
        if (getServletContextAttribute("auth") == null) {
          // look in web.xml
          File webXmlFile = new File(getRealPath("/WEB-INF/web.xml"));
          XPathFactory xpathFactory = XPathFactory.newInstance();
          XPath xpath = xpathFactory.newXPath();
          InputSource sbis = new InputSource(new FileInputStream(webXmlFile));
          Document document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(sbis);
          // is there a /web-app/security-constraint/auth-constraint/role-name == view
          String securityConstraint = (String)xpath.evaluate(
            "/web-app/security-constraint/auth-constraint/role-name[contains(text(),'view')]",
            document, XPathConstants.STRING);
          boolean viewRole = "view".equals(securityConstraint);
          if (!viewRole && getUser() == null) {
            setServletContextAttribute("auth", Boolean.FALSE);
          } else { // view role
            setServletContextAttribute("auth", Boolean.TRUE);
            // determine whether logout is possible
            // look in web.xml for /web-app/login-config/auth-method == BASIC
            Boolean canLogout = Boolean.TRUE;
            String authMethod = (String) xpath.evaluate(
              "/web-app/login-config/auth-method", document, XPathConstants.STRING);
            if ("BASIC".equals(authMethod)) {
              canLogout = Boolean.FALSE;
            }
            setServletContextAttribute("canLogout", canLogout);
          }
        }
        if (!(Boolean)getServletContextAttribute("auth")) {      
          setSessionAttribute("security", "none");
          setSessionAttribute("group_view", Boolean.TRUE);
          setSessionAttribute("group_edit", Boolean.TRUE);
          setSessionAttribute("group_admin", Boolean.TRUE);
        } else { // auth configured          
          setSessionAttribute(
            "canLogout",
            getServletContextAttribute("canLogout"));
          if (getUser() != null) {
            PreparedStatement sqlUserGroups = db.prepareStatement(
              "SELECT role_id FROM role WHERE user_id = ?");
            sqlUserGroups.setString(1, getUser());
            ResultSet rstUserGroups = sqlUserGroups.executeQuery();
            while (rstUserGroups.next()) {
              setSessionAttribute(
                "group_" + rstUserGroups.getString("role_id"), Boolean.TRUE);
            } // next group
            rstUserGroups.close();
            sqlUserGroups.close();
		     
            PreparedStatement sqlUser = db.prepareStatement(
              "SELECT reset_password FROM miner_user WHERE user_id = ?");
            sqlUser.setString(1, getUser());
            ResultSet rstUser = sqlUser.executeQuery();
            if (rstUser.next()) {
              // this user id is in the user table - this means we're
              // using JDBCRealm security to connect to our own DB
              setSessionAttribute("security", "JDBCRealm");
              if (rstUser.getInt("reset_password") == 1) {
                setRequestAttribute("reset_password", Boolean.TRUE);
              }
            } else {
              // this user id is not in the user table - this means
              // we're using some other security mechanism - probably
              // LDAP via JNDI
              setSessionAttribute("security", "JNDIRealm");
            } // user is in user table
            rstUser.close();
            sqlUser.close();
          } // user is set
        } // view role
      } catch (Throwable t) {
        System.err.println("IsUserInRole: Can't parse web.xml: " + t);
      }
    } // security not set yet, must be logging on
    
    return getSessionAttribute("group_" + role) != null;
  } // end of IsUserInRole()

  /**
   * Sets the Content-Disposition header of the given Response correctly for saving a file
   * to the given name. 
   * <p> This should handle special characters/spaces in the file name correctly.
   * @param fileName The file name to save the response body as.
   */
  default public void responseAttachmentName(String fileName) {
    if (fileName == null) return;
    fileName = IO.SafeFileNameUrl(fileName);
    String onlyASCIIFileName = IO.OnlyASCII(fileName)
      .replace(",","-"); // Chrome/Edge don't like commas
    String onlyASCIIFileNameNoQuotes = onlyASCIIFileName
      .replace(" ","_").replace(";","_");
    
    if (fileName.indexOf(' ') >= 0 // contains spaces
        || fileName.indexOf(';') >= 0 // contains semicolon
        || !fileName.equals(onlyASCIIFileName)) { // contains non-ascii
      try {
        fileName = "\""+URLEncoder.encode(fileName, "UTF-8").replace('+',' ')+"\"";
      } catch(UnsupportedEncodingException exception) {
        fileName = "\""+fileName+"\"";
      }
    } else {
      try {
        fileName = URLEncoder.encode(fileName, "UTF-8").replace('+',' ');
      } catch(UnsupportedEncodingException exception) {
      }
    }
    if (onlyASCIIFileName.indexOf(' ') >= 0 || onlyASCIIFileName.indexOf(';') >= 0){
      onlyASCIIFileName = "\""+onlyASCIIFileName+"\"";
    }
    
    // are we being called by the nzilbb.labbcat R package or by java (jsendpraat)?
    String userAgent = Optional.ofNullable(getRequestHeader("User-Agent")).orElse("");
    String labbcatRVersion = null;
    if (userAgent.startsWith("labbcat-R")) {
      String[] parts = userAgent.split("/");
      if (parts.length > 1) {
        labbcatRVersion = parts[1];
      }
    }
    if (userAgent != null
        && (userAgent.startsWith("Java/") // plain Java connection (probably jsendpraat)
            || userAgent.startsWith("labbcat-py/") // Python package
            || (labbcatRVersion != null // nzilbb.labbcat R package
                // and version <= 1.3-0
                && new SemanticVersionComparator().compare(labbcatRVersion, "1.3-0") <= 0))) {
      // specify only 'filename' without quotes
      addResponseHeader(
        "Content-Disposition", "attachment; filename="+onlyASCIIFileNameNoQuotes);
    } else if ("jsendpraat 20240927.1325".equals(userAgent)) {
      addResponseHeader( // this jsendpraat version prefers filename* second...
        "Content-Disposition", "attachment; filename="+onlyASCIIFileName+"; filename*="+fileName);
    } else {
      // MDN recommends not to use URLEncoder.encode
      // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Disposition#as_a_response_header_for_the_main_body
      // TODO we can use User-Agent to send un-URL-encoded responses to Safari only
      addResponseHeader(
        "Content-Disposition", "attachment; filename*="+fileName+"; filename="+onlyASCIIFileName);
    }
  } // end of ResponseAttachmentName()

} // end of APIRequestContext
