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
package nzilbb.labbcat.server.api.admin.annotator;

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
 * Server-side implementation of 'config' web-apps.
 * <p> This servlet manages post-installation configuration of annotators, by presenting the
 * given annotator's 'config' web-app, if there is one, and accepting a <q>setConfig</q>
 * request to finalize installation (whether there's a 'config' web-app or not).
 * <p> If there's a 'config' web-app, the first request should be to a path structured: <br>
 * <tt>/api/admin/annotator/config/<var>annotatorId</var>/</tt>
 * <p> The resulting HTML document will then implement the web-app by requesting resources
 * as required.
 * <p> Once ready (or if the annotator implements no 'config' web-app) a request to <br>
 * <tt>/api/admin/annotator/config/<var>annotatorId</var>/setConfig</tt>
 * <br>... with the body of the request representing the annotator configuration, if any.
 * <p> The response to the <tt>setConfig</tt> request will be an HTML document that
 * renders a progress bar, and manages updating progress as the annotator completes its
 * configuration.
 * @author Robert Fromont robert@fromont.net.nz
 */
@RequiredRole("admin")
public class ConfigWebApp extends APIRequestHandler {
  
  HashMap<String,AnnotatorDescriptor> activeAnnotators = new HashMap<String,AnnotatorDescriptor>();
  
  /**
   * Default constructor.
   */
  public ConfigWebApp() {
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
        if (resource.equals("/")) resource = "/index.html";
        context.servletLog("annotatorId " + annotatorId + " resource " + resource);
        
        // get annotator descriptor - the same instance as last time if possible
        AnnotatorDescriptor newDescriptor = store.getAnnotatorDescriptor(annotatorId);
        AnnotatorDescriptor descriptor = activeAnnotators.get(annotatorId);
        context.servletLog("descriptor " + (descriptor==null?"null":descriptor.getVersion()));
        if (descriptor == null // haven't got one of these yet
            || descriptor.getVersion() == null // this version has been uninstalled
            || (newDescriptor != null // or a new one has been installed
                && !descriptor.getVersion().equals(newDescriptor.getVersion()))) {
          // use the new one
          descriptor = newDescriptor;
          activeAnnotators.put(annotatorId, descriptor);
          context.servletLog("new descriptor " + descriptor);
          descriptor.getInstance().getStatusObservers().add(
            status -> context.servletLog(annotatorId + ": " + status));
        }
        if (descriptor == null) {
          httpStatus.accept(SC_NOT_FOUND);
          return;
        }
        
        // validate the webapp path
        if (!descriptor.hasConfigWebapp()
            && !resource.equals("/getStatus")          // except /getStatus OK
            && !resource.equals("/getPercentComplete") // except /getPercentComplete OK
            && !resource.equals("/setConfig")) {       // except /setConfig OK
          context.servletLog("no config webapp " + annotatorId + " " + resource);
          httpStatus.accept(SC_NOT_FOUND);
          return;
        }
        
        final Annotator annotator = descriptor.getInstance();
        InputStream stream = null;
            
        contentTypeConsumer.accept(StandAloneWebApp.ContentTypeForName(resource));
        int status = SC_OK;
        
        // check for keyword resources
        if (resource.equals("/getSchema")) {
          stream = new ByteArrayInputStream(
            annotator.getSchema().toJson().toString().getBytes());
          contentTypeConsumer.accept("application/json;charset=UTF-8");
          
        } else if (resource.equals("/setConfig")) {
          // return something
          String finishedResponse
            ="<html><head><title>"+localize("Installing...")+"</title></head><body>"
            +"<progress id='p' value='0' max='100' style='width: 100%'>"
            +localize("Installing...")+"</progress>"
            +"<p id='m' style='text-align:center;'></p>"
            +"<script type='text/javascript'>"
            +"\nfunction p() {"
            +"\n  var request = new XMLHttpRequest();"
            +"\n  request.open('GET', 'getStatus');"
            +"\n  request.setRequestHeader('Accept', 'text/plain');"
            +"\n  request.addEventListener('load', function(e) {"
            +"\n    var message = document.getElementById('m');"
            +"\n    message.innerHTML = this.responseText;"
            +"\n    var request = new XMLHttpRequest();"
            +"\n    request.open('GET', 'getPercentComplete');"
            +"\n    request.setRequestHeader('Accept', 'text/plain');"
            +"\n    request.addEventListener('load', function(e) {"
            +"\n      var progress = document.getElementById('p');"
            +"\n      progress.value = this.responseText;"
            +"\n      progress.title = this.responseText+'%';"
            +"\n      if (progress.value < 100) window.setTimeout(p, 500);"
            +"\n      else {"
            +"\n        document.getElementById('m').innerHTML = '"
            +localize("You can close this window.")+"';"
            +"\n        window.parent.postMessage({ resource: 'setConfig' }, '*');"
            +"\n      }"
            +"\n    }, false);"
            +"\n    request.send();"
            +"\n  }, false);"
            +"\n  request.send();"
            +"\n}"
            +"\np();"
            +"</script>"
            +"</body></html>";
          stream = new ByteArrayInputStream(finishedResponse.getBytes());
          contentTypeConsumer.accept("text/html;charset=UTF-8");
               
          // set config in a thread we can get the status from later
          final String config = IO.InputStreamToString(requestBody);
          new Thread(new Runnable() {
              public void run() {
                try {
                  annotator.setConfig(config);
                } catch(InvalidConfigurationException exception) {
                  context.servletLog(pathInfo + " - invalid config: " + exception
                      + " : " + config);
                }
              }
            }).start();
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
              //context.servletLog("about to get " + resource);
              stream = descriptor.getResource("config"+resource);
              //context.servletLog("got " +resource);
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
              if (exception.getMessage() != null) {
                stream = new ByteArrayInputStream(exception.getMessage().getBytes());
              } else {
                stream = new ByteArrayInputStream(exception.getClass().getName().getBytes());
              }
            } catch(URISyntaxException exception) {
              context.servletLog(pathInfo + " - URISyntaxException: " + exception);
              httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
              return;
            }
          }
        }
        if (stream == null)  {
          context.servletLog("no stream for config" +resource);
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
} // end of class ConfigWebApp
