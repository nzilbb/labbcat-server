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
package nzilbb.labbcat.server.servlet.annotator;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
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
import java.util.regex.*;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import nzilbb.ag.PermissionException;
import nzilbb.ag.automation.*;
import nzilbb.ag.automation.util.AnnotatorDescriptor;
import nzilbb.ag.automation.util.RequestRouter;
import nzilbb.ag.automation.util.RequestException;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.labbcat.server.servlet.*;
import nzilbb.util.IO;
import nzilbb.webapp.StandAloneWebApp;
import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.disk.*;
import org.apache.commons.fileupload.servlet.*;

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
@WebServlet({"/admin/annotator/config/*", "/api/admin/annotator/config/*"})
@RequiredRole("admin")
public class AdminAnnotatorConfigWebApp extends LabbcatServlet {

  HashMap<String,AnnotatorDescriptor> activeAnnotators
  = new HashMap<String,AnnotatorDescriptor>();

  /**
   * Default constructor.
   */
  public AdminAnnotatorConfigWebApp() {
  } // end of constructor
   
  /** 
   * Initialise the servlet
   */
  public void init() {
    super.init();
  }
   
  /**
   * POST handler just calls {@link #doGet(HttpServletRequest,HttpServletResponse)}.
   */
  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    doGet(request, response);
  }
   
  /**
   * PUT handler just calls {@link #doGet(HttpServletRequest,HttpServletResponse)}.
   */
  @Override
  protected void doPut(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    doGet(request, response);
  }
   
  /**
   * DELETE handler just calls {@link #doGet(HttpServletRequest,HttpServletResponse)}.
   */
  @Override
  protected void doDelete(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    doGet(request, response);
  }
   
  /**
   * GET handler.
   */
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    log(request.getPathInfo());
    try {
      SqlGraphStoreAdministration store = getStore(request);
      try {
            
        if (!hasAccess(request, response, store.getConnection())) {
          return;
        } 
            
        if (request.getPathInfo() == null || request.getPathInfo().equals("/")) {
          response.setStatus(HttpServletResponse.SC_NOT_FOUND);
          return;
        }
        Matcher path = Pattern.compile("/([^/]+)(/.*)$")
          .matcher(request.getPathInfo());
        if (!path.matches()) {
          response.setStatus(HttpServletResponse.SC_NOT_FOUND);
          log("No match");
          return;
        }            
        String annotatorId = path.group(1);
        String resource = path.group(2);
        if (resource.equals("/")) resource = "/index.html";
        log("annotatorId " + annotatorId + " resource " + resource);

        // get annotator descriptor - the same instance as last time if possible
        AnnotatorDescriptor newDescriptor = store.getAnnotatorDescriptor(annotatorId);
        log("new descriptor " + (newDescriptor==null?"null":newDescriptor.getVersion()));
        AnnotatorDescriptor descriptor = activeAnnotators.get(annotatorId);
        log("descriptor " + (descriptor==null?"null":descriptor.getVersion()));
        if (descriptor == null // haven't got one of these yet
            || descriptor.getVersion() == null // this version has been uninstalled
            || (newDescriptor != null // or a new one has been installed
                && !descriptor.getVersion().equals(newDescriptor.getVersion()))) {
          // use the new one
          descriptor = newDescriptor;
          activeAnnotators.put(annotatorId, descriptor);
          log("new descriptor " + descriptor);
        }
        if (descriptor == null) {
          response.setStatus(HttpServletResponse.SC_NOT_FOUND);
          return;
        }

        // validate the webapp path
        if (!descriptor.hasConfigWebapp()
            && !resource.equals("/getStatus")          // except /getStatus OK
            && !resource.equals("/getPercentComplete") // except /getPercentComplete OK
            && !resource.equals("/setConfig")) {       // except /setConfig OK
          log("no config webapp " + annotatorId + " " + resource);
          response.setStatus(HttpServletResponse.SC_NOT_FOUND);
          return;
        }

        final Annotator annotator = descriptor.getInstance();
        InputStream stream = null;
            
        response.setContentType(StandAloneWebApp.ContentTypeForName(resource));
        int status = HttpServletResponse.SC_OK;
            
        // check for keyword resources
        if (resource.equals("/getSchema")) {
          stream = new ByteArrayInputStream(
            annotator.getSchema().toJson().toString().getBytes());
               
        } else if (resource.equals("/setConfig")) {
          // return something
          String finishedResponse
            ="<html><head><title>"+localize(request, "Installing...")+"</title></head><body>"
            +"<progress id='p' value='0' max='100' style='width: 100%'>"
            +localize(request, "Installing...")+"</progress>"
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
            +localize(request, "You can close this window.")+"';"
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
          response.setContentType("text/html");
               
          // set config in a thread we can get the status from later
          final String config = IO.InputStreamToString(request.getInputStream());
          new Thread(new Runnable() {
              public void run() {
                try {
                  annotator.setConfig(config);
                } catch(InvalidConfigurationException exception) {
                  log(request.getPathInfo() + " - invalid config: " + exception
                      + " : " + config);
                }
              }
            }).start();
        } else if (resource.equals("/util.js")) {
          URL url = descriptor.getClass().getResource("util.js");
          if (url != null) {
            try {
              stream = url.openConnection().getInputStream();
              response.setContentType("text/javascript");
            } catch(IOException exception) {}
          }
        } else {            
          if (resource.indexOf('.') > 0) {
            // requests with a dot are taken to be resources for the webapp,
            // e.g. index.html
            try {
              log("about to get " + resource);
              stream = descriptor.getResource("config"+resource);
              log("got " +resource);
            } catch(Throwable exception) {
              log(request.getPathInfo() + " - Could not getResource: "+exception);
            }
          } else {
            // everything else is routed to the annotator...
            try {
              String uri = request.getRequestURI();
              // the request's URI doesn't include the query string, so add it if necessary
              if (request.getQueryString() != null) {
                uri += "?" + request.getQueryString();
              }
              stream = new RequestRouter(annotator).request(
                request.getMethod(), uri, request.getHeader("Content-Type"),
                request.getInputStream());
              echoContentType(request, response);
            } catch(RequestException exception) {
              log(request.getPathInfo() + " - RequestException: " + exception);
              status = exception.getHttpStatus();
              stream = new ByteArrayInputStream(exception.getMessage().getBytes());
            } catch(URISyntaxException exception) {
              log(request.getPathInfo() + " - URISyntaxException: " + exception);
              response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
              return;
            }
          }
        }
        if (stream == null)  {
          log("no stream for config" +resource);
          response.setStatus(HttpServletResponse.SC_NOT_FOUND);
          return;
        }
        response.setStatus(status);
        IO.Pump(stream, response.getOutputStream());
            
      } finally {
        cacheStore(store);
      }
    } catch (PermissionException x) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      writeResponse(response, failureResult(x));
    } catch (SQLException x) {
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      writeResponse(response, failureResult(
                      request, "Cannot connect to database: {0}", x.getMessage()));
    }     
  }

  /**
   * If the request specifies an expected content type, set the reponse Content-Type to that.
   */
  protected void echoContentType(HttpServletRequest request, HttpServletResponse response) {
    String accept = request.getHeader("Accept");
    if (accept != null) {
      // Something like "*/*"
      // or "text/html, application/xhtml+xml, application/xml;q=0.9, */*;q=0.8" 
      // we'll take the first one
      String contentType = accept.split(",")[0].trim();
      // strip any q parameter
      contentType = contentType.split(";")[0].trim();
      // ignore */*
      if (!contentType.equals("*/*")) {
        response.setContentType(contentType);
      }
    }
  } // end of echoContentType()

  private static final long serialVersionUID = 1;
} // end of class AdminAnnotatorConfigWebApp
