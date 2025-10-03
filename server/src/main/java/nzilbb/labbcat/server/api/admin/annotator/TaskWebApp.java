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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Timer;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.regex.*;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import nzilbb.ag.Layer;
import nzilbb.ag.PermissionException;
import nzilbb.ag.StoreException;
import nzilbb.ag.automation.*;
import nzilbb.ag.automation.util.AnnotatorDescriptor;
import nzilbb.ag.automation.util.RequestException;
import nzilbb.ag.automation.util.RequestRouter;
import nzilbb.ag.util.LayerHierarchyTraversal;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.labbcat.server.api.APIRequestHandler;
import nzilbb.labbcat.server.api.RequiredRole;
import nzilbb.util.IO;
import nzilbb.webapp.StandAloneWebApp;

/**
 * Server-side implementation of 'task' web-apps.
 * <p> This servlet manages configuration of annotator tasks, by presenting the
 * given annotator's 'task' web-app, if there is one, and accepting a <q>setTaskParameters</q>
 * request to finalize task configuration (whether there's a 'task' web-app or not).
 * <p> If there's a 'task' web-app, the first request should be to a path structured: <br>
 * <tt>/api/admin/annotator/task/<var>annotatorId</var>/?<var>taskId</var></tt>
 * <p> The resulting HTML document will then implement the web-app by requesting resources
 * as required.
 * <p> Once ready (or if the annotator implements no 'task' web-app) a request to <br>
 * <tt>/api/admin/annotator/task/<var>annotatorId</var>/setTaskParameters</tt>
 * <br>... with the body of the request representing the task parameters, if any.
 * <p> The response to the <tt>setTaskParameters</tt> request will be an HTML document for
 * display to the user. However, no further interaction with the user is expected.
 * @author Robert Fromont robert@fromont.net.nz
 */
@RequiredRole("admin")
public class TaskWebApp extends APIRequestHandler {
   
  HashMap<String,HashMap<String,AnnotatorDescriptor>> activeAnnotators;
  Timer annotatorDeactivator;
   
  /**
   * Constructor.
   */
  public TaskWebApp(HashMap<String,HashMap<String,AnnotatorDescriptor>> activeAnnotators,
                    Timer annotatorDeactivator) {
    this.activeAnnotators = activeAnnotators;
    this.annotatorDeactivator = annotatorDeactivator;
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
   * @param httpStatus Receives the response status code, in case of error.
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
        String taskId = queryString;
        if (taskId == null || taskId.length() == 0) {            
          String referer = requestHeaders.apply("Referer");
          if (referer != null) {
            taskId = java.net.URLDecoder.decode(new URL(referer).getQuery(), "UTF-8");
          }
        }
        if (taskId == null || taskId.length() == 0) {
          httpStatus.accept(SC_BAD_REQUEST);
          context.servletLog("No task specified by query string or referrer");
          return;
        }
        if (resource.equals("/")) resource = "/index.html";
        context.servletLog("annotatorId " + annotatorId + " resource " + resource);

        // get annotator descriptor - the same instance as last time if possible
        if (!activeAnnotators.containsKey(annotatorId)) {
          activeAnnotators.put(annotatorId, new HashMap<String,AnnotatorDescriptor>());
        }
        final AnnotatorDescriptor newDescriptor = store.getAnnotatorDescriptor(annotatorId);
        AnnotatorDescriptor descriptor = activeAnnotators.get(annotatorId).get(taskId);
        if (descriptor == null // haven't got one of these yet
            || descriptor.getVersion() == null // this version has been uninstalled
            || (newDescriptor != null // or a new one has been installed
                && !descriptor.getVersion().equals(newDescriptor.getVersion()))) {
          // use the new one
          descriptor = newDescriptor;
          // get task parameters from database
          try {
            descriptor.getInstance().setTaskParameters(
              store.getAnnotatorTaskParameters(taskId));
          } catch(Exception exception) {}
          // persist the descriptor between requests
          activeAnnotators.get(annotatorId).put(taskId, descriptor);
          context.servletLog("new descriptor " + descriptor);
          descriptor.getInstance().getStatusObservers().add(s->context.servletLog(s));
          // these objects shouldn't hang around forever in memory
          // delete them after an hour, which should be long enough to configure a task
          final String finalTaskId = taskId;
          annotatorDeactivator.schedule(new java.util.TimerTask() { public void run() {
            // if we haven't gotten rid of this annotator for this task yet
            if (newDescriptor == activeAnnotators.get(annotatorId).get(finalTaskId)) {
              // get rid of it now
              activeAnnotators.get(annotatorId).remove(finalTaskId);
            }
          }}, 60*60*1000); // after an hour

        }
        if (descriptor == null) {
          httpStatus.accept(SC_NOT_FOUND);
          return;
        }

        // validate the webapp path
        if (!descriptor.hasTaskWebapp()
            && !resource.equals("/getStatus")          // except /getStatus OK
            && !resource.equals("/getPercentComplete") // except /getPercentComplete OK
            && !resource.equals("/setTask")) {       // except /setTask OK
          context.servletLog("no task webapp " + annotatorId + " " + resource);
          httpStatus.accept(SC_NOT_FOUND);
          return;
        }

        final Annotator annotator = descriptor.getInstance();
        if (annotator.getClass().isAnnotationPresent(UsesGraphStore.class)) {
          annotator.setStore(store);
        }
        InputStream stream = null;
            
        contentTypeConsumer.accept(StandAloneWebApp.ContentTypeForName(resource));
        int status = SC_OK;
            
        // check for keyword resources
        if (resource.equals("/getSchema")) {
          stream = new ByteArrayInputStream(
            annotator.getSchema().toJson().toString().getBytes());               
          contentTypeConsumer.accept("application/json;charset=UTF-8");
          
        } else if (resource.equals("/getTaskParameters")) {
          // get task parameters from database
          String parameters = store.getAnnotatorTaskParameters(taskId);
          if (parameters != null) {
            stream = new ByteArrayInputStream(parameters.getBytes());
            if (parameters.startsWith("{") || parameters.startsWith("[")) {
              contentTypeConsumer.accept("application/json;charset=UTF-8");
            } else {
              contentTypeConsumer.accept("text/plain;charset=UTF-8");
            }
          }
          
        } else if (resource.equals("/setTaskParameters")) {
          context.servletLog("setTaskParameters: " + taskId);

          // check the parameters don't generate an error
          String parameters = IO.InputStreamToString(requestBody);
          try {
            annotator.setTaskParameters(parameters);
            
            String finishedResponse
              ="<html><head><title>"
              +localize("Task Parameters")+"</title></head><body>"
              +"<p style='text-align: center;'><big>"+localize("Thanks.")+"</big></p>"
              +"<script type='text/javascript'>"
              +"window.parent.postMessage({ resource: 'setTaskParameters' }, '*');"
              +"</script>"
              +"</body></html>";
            stream = new ByteArrayInputStream(finishedResponse.getBytes());
            contentTypeConsumer.accept("text/html;charset=UTF-8");
            
            // store task parameters in DB
            store.saveAnnotatorTaskParameters(taskId, parameters);
            
            boolean editableOutputs
              = annotator.getClass().isAnnotationPresent(AllowsManualAnnotations.class);
            // create/update any layers that are required
            for (String layerId : annotator.getOutputLayers()) {
              Layer newOutput = annotator.getSchema().getLayer(layerId);
              if (editableOutputs) newOutput.put("extra", "editable");
              Layer existingOutput = store.getLayer(layerId);
              if (newOutput != null) {
                if (existingOutput == null) { // output doesn't exist yet
                  // create new layer
                  store.newLayer(newOutput);
                  context.servletLog("Output layer " + newOutput + " created");
                } else { // existing layer
                  
                  if (!newOutput.getType().equals(existingOutput.getType())
                      || newOutput.getAlignment() != existingOutput.getAlignment()
                      || newOutput.getPeers() != existingOutput.getPeers()
                      || newOutput.getPeersOverlap() != existingOutput.getPeersOverlap()
                      || newOutput.getParentIncludes() != existingOutput.getParentIncludes()
                      || newOutput.getSaturated() != existingOutput.getSaturated()
                      || !newOutput.getValidLabels().keySet().equals(
                        existingOutput.getValidLabels().keySet())
                      // annotator creates editable layers but the layer wasn't editable
                      || (newOutput.get("extra") != null // TODO use a formal mechanism 
                          && !newOutput.get("extra").equals(existingOutput.get("extra")))
                    ) {
                    store.saveLayer(newOutput);
                    context.servletLog("Output layer " + newOutput + " updated");
                  } // output layer definition has changed
                } // output layer exists in store
              } // output layer exists for annotator
            } // next output layer
          } catch (InvalidConfigurationException x) {
            httpStatus.accept(SC_BAD_REQUEST);
            String finishedResponse
              ="<html><head><title>"
              +localize("Task Parameters")+"</title></head><body>"
              +"<p style='text-align: center; color: red;'><big>"+x.getMessage()+"</big></p>"
              +"</body></html>";
            stream = new ByteArrayInputStream(finishedResponse.getBytes());
            contentTypeConsumer.accept("text/html;charset=UTF-8");
          }
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
              //context.servletLog("about to get task" +resource);
              stream = descriptor.getResource("task"+resource);
              //context.servletLog("got task" +resource);
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
              }
            } catch(URISyntaxException exception) {
              context.servletLog(pathInfo + " - URISyntaxException: " + exception);
              httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
              return;
            }
          }
        }
        if (stream == null)  {
          context.servletLog("no stream for task" +resource);
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
    } catch (StoreException x) { // getAnnotatorTaskParameters or saveAnnotatorTaskParameters
      httpStatus.accept(SC_BAD_REQUEST);
      writeResponse(out, failureResult(x));
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
} // end of class TaskWebApp
