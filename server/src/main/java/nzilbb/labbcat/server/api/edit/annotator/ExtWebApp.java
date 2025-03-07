//
// Copyright 2020-2025 New Zealand Institute of Language, Brain and Behaviour, 
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
package nzilbb.labbcat.server.api.edit.annotator;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.regex.*;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import nzilbb.ag.PermissionException;
import nzilbb.ag.automation.*;
import nzilbb.ag.automation.util.AnnotatorDescriptor;
import nzilbb.ag.automation.util.RequestRouter;
import nzilbb.ag.automation.util.RequestException;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.labbcat.server.api.APIRequestHandler;
import nzilbb.labbcat.server.api.RequiredRole;
import nzilbb.util.IO;
import nzilbb.webapp.StandAloneWebApp;

/**
 * Server-side implementation of 'ext' web-apps.
 * <p> This servlet provides access to an annotator's 'ext' web-app, if there is one.
 * <p> If there's an 'ext' web-app, the first request should be to a path structured: <br>
 * <tt>/api/admin/annotator/ext/<var>annotatorId</var>/</tt>
 * <p> The resulting HTML document will then implement the web-app by requesting resources
 * as required.
 * <p> 'Ext' web-apps provide a mechanism for supplying extra information or data to the
 * user (data visualizations, access to dictionary files, etc.) and are 'open-ended';
 * there's no final request that terminates the web-app, as there is with the 'config' and
 * 'task' web-apps.
 * @author Robert Fromont robert@fromont.net.nz
 */
@RequiredRole("edit")
public class ExtWebApp extends APIRequestHandler {
  
  HashMap<String,AnnotatorDescriptor> activeAnnotators
  = new HashMap<String,AnnotatorDescriptor>();
  
  /**
   * Default constructor.
   */
  public ExtWebApp() {
  } // end of constructor
  
  /**
   * GET handler.
   * @param method HTTP method of the request.
   * @param requestURI The URL of the request, excluding the query string.
   * @param pathInfo The URL path.
   * @param queryString The URL's query string.
   * @param requestHeaders Access to HTTP request headers.
   * @param requestBody Access to the body of the request.
   * @param out Response body output stream.
   * @param contentTypeConsumer Receives the content type for specification in the response headers.
   * @param contentEncoding Receives the character encoding of the recponse.
   * @param httpStatus Receives the response status code, in case or error.
   */
  public void get(
    String method, String requestURI, String pathInfo, String queryString,
    UnaryOperator<String> requestHeaders, InputStream requestBody,
    OutputStream out, Consumer<String> contentTypeConsumer, Consumer<String> contentEncoding,
    Consumer<Integer> httpStatus) {
    context.servletLog(pathInfo);
    try {
      SqlGraphStoreAdministration store = getStore();
      try {
        
        if (!hasAccess(store.getConnection())) {
          return;
        } 
            
        if (pathInfo == null || pathInfo.equals("/")) {
          httpStatus.accept(SC_NOT_FOUND);
          return;
        }
        Matcher path = Pattern.compile("/([^/]+)(/.*)$").matcher(pathInfo);
        if (!path.matches()) {
          httpStatus.accept(SC_NOT_FOUND);
          context.servletLog("No match");
          return;
        }            
        String annotatorId = path.group(1);
        String resource = path.group(2);
        String query = queryString;
        if (resource.equals("/")) resource = "/index.html";
        context.servletLog("annotatorId " + annotatorId + " resource " + resource + " URI " + requestURI);

        // get annotator descriptor - the same instance as last time if possible
        AnnotatorDescriptor newDescriptor = store.getAnnotatorDescriptor(annotatorId);
        AnnotatorDescriptor descriptor = activeAnnotators.get(annotatorId);
        if (descriptor == null // haven't got one of these yet
            || descriptor.getVersion() == null // this version has been uninstalled
            || (newDescriptor != null // or a new one has been installed
                && !descriptor.getVersion().equals(newDescriptor.getVersion()))) {
          // use the new one
          descriptor = newDescriptor;
          activeAnnotators.put(annotatorId, descriptor);
          context.servletLog("new descriptor " + descriptor);
        }
        if (descriptor == null) {
          httpStatus.accept(SC_NOT_FOUND);
          return;
        }

        // validate the webapp path
        if (!descriptor.hasExtWebapp()) {
          context.servletLog("no ext webapp " + annotatorId + " " + resource);
          httpStatus.accept(SC_NOT_FOUND);
          return;
        }

        final Annotator annotator = descriptor.getInstance();
        InputStream stream = null;

        String contentType = StandAloneWebApp.ContentTypeForName(resource);
        if (contentType.startsWith("text") || contentType.equals("application/json")) {
          contentType += ";charset=UTF-8";
        }          
        contentTypeConsumer.accept(contentType);
        int status = SC_OK;
            
        // check for getSchema call
        if (resource.equals("/getSchema")) {
          stream = new ByteArrayInputStream(
            annotator.getSchema().toJson().toString().getBytes());
          contentTypeConsumer.accept("application/json;charset=UTF-8");
          
        } else if (resource.equals("/util.js")) {
          URL url = descriptor.getClass().getResource("util.js");
          if (url != null) {
            try {
              stream = url.openConnection().getInputStream();
              contentTypeConsumer.accept("text/javascript;charset=UTF-8");
            } catch(IOException exception) {}
          }
               
        } else {            
          if (resource.indexOf('.') > 0) {
            // requests with a dot are taken to be resources for the webapp,
            // e.g. index.html
            try {
              //context.servletLog("about to get ext" + resource);
              stream = descriptor.getResource("ext"+resource);
              //context.servletLog("got ext" +resource);
              if (resource.indexOf(".html") > 0) {
                contentTypeConsumer.accept("text/html;charset=UTF-8");
              } else if (resource.indexOf(".js") > 0) {
                contentTypeConsumer.accept("application/json;charset=UTF-8");
              } else if (resource.indexOf(".css") > 0) {
                contentTypeConsumer.accept("text/css;charset=UTF-8");
              }
            } catch(Throwable exception) {
              context.servletLog(pathInfo + " - Could not getResource: "+exception);
            }
          } else {
            // everything else is routed to the annotator...
            try {
              String uri = requestURI;
              // the request's URI doesn't include the query string, so add it if necessary
              if (queryString != null) {
                uri += "?" + queryString;
              }
              stream = new RequestRouter(annotator).request(
                method, uri, requestHeaders.apply("Content-Type"), requestBody);
              echoContentType(requestHeaders, contentTypeConsumer, contentEncoding);
            } catch(RequestException exception) {
              context.servletLog(pathInfo + " - RequestException: " + exception);
              status = exception.getHttpStatus();
              stream = new ByteArrayInputStream(exception.getMessage().getBytes());
            } catch(URISyntaxException exception) {
              context.servletLog(pathInfo + " - URISyntaxException: " + exception);
              httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
              return;
            }
          }
        }
        if (stream == null)  {
          context.servletLog("no stream for ext" +resource);
          httpStatus.accept(SC_NOT_FOUND);
          return;
        }
        httpStatus.accept(status);
        if (stream instanceof ByteArrayInputStream) {
          contentEncoding.accept("UTF-8");
        }
        IO.Pump(stream, out);
            
      } finally {
        cacheStore(store);
      }
    } catch (PermissionException x) {
      httpStatus.accept(SC_FORBIDDEN);
      writeResponse(out, failureResult(x));
    } catch (SQLException x) {
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      writeResponse(out, failureResult("Cannot connect to database: {0}", x.getMessage()));
    } catch (IOException x) {
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      writeResponse(out, failureResult("Communication error: {0}", x.getMessage()));
    }     
  }

  /**
   * If the request specifies an expected content type, set the reponse Content-Type to that.
   */
  protected void echoContentType(UnaryOperator<String> requestHeaders, Consumer<String> contentTypeConsumer, Consumer<String> contentEncoding) {
    String accept = requestHeaders.apply("Accept");
    if (accept != null) {
      // Something like "*/*"
      // or "text/html, application/xhtml+xml, application/xml;q=0.9, */*;q=0.8" 
      // we'll take the first one
      String contentType = accept.split(",")[0].trim();
      // strip any q parameter
      contentType = contentType.split(";")[0].trim();
      // ignore */*
      if (!contentType.equals("*/*")) {
        contentTypeConsumer.accept(contentType);
        if (contentType.equals("application/json") || contentType.startsWith("text")) {
          contentEncoding.accept("UTF-8");
        }
      }
    }
  } // end of echoContentType()
} // end of class ExtWebApp
