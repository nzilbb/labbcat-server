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
import java.sql.SQLException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Consumer;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import nzilbb.labbcat.server.db.SqlSearchResults;
import nzilbb.labbcat.server.search.CsvResults;
import nzilbb.labbcat.server.search.SearchResults;
import nzilbb.labbcat.server.search.SearchTask;

/**
 * <tt>/api/task[/{ID}]</tt> : information about currently running tasks.
 *  <p> Provides information about tasks/threads that are currently
 *  running or recently finished.
 *   <p> Methods supported:
 *   <dl>
 *    <dt> GET </dt><dd>
 *     <ul>
 *      <li><em> Optional parameters </em>
 *        <ul>
 *         <li><em> log </em> "true" to include the task's  log in the response. </li>
 *         <li><em> keepalive </em> Requesting a task's status has the
 *             side-effect of keeping it from being released
 *             soon. Passing this keepalive=false suppresses this side-effect. </li>
 *        </ul>
 *      </li>
 *      <li><em> Response Body </em> - the standard JSON envelope. If
 *       no ID is specified, the model is an array of IDs of currently
 *       running threads. If an ID is appended to the URL, the model
 *       is an object with the following attributes:
 *       <dl>
 *         <dt>threadId</dt> <dd>The task's ID</dd>
 *         <dt>threadName</dt> <dd>The name of the task</dd>
 *         <dt>log</dt> <dd>The task's log, if requested</dd>
 *         <dt>resultUrl</dt> <dd>The URL of the task's result, if any</dd>
 *         <dt>resultText</dt> <dd>The label for the task's result, if any</dd>
 *         <dt>resultTarget</dt> <dd>The target HTML frame/window for
 *             the results to open in</dd>
 *         <dt>running</dt> <dd>Whether the task is currently running</dd>
 *         <dt>duration</dt> <dd>How long the task has run for</dd>
 *         <dt>percentComplete</dt> <dd>How far through the task is</dd>
 *         <dt>status</dt> <dd>The task's current status description</dd>
 *         <dt>refreshSeconds</dt> <dd> recommended delay before
 *             refreshing the task information</dd>
 *         <dt>lastException</dt> <dd>The last exception to occur, if any</dd>
 *         <dt>stackTrace</dt> <dd>If an exception has occurred, this
 *             is a stack trace identifying where the exception was thrown</dd>
 *       </dl>
 *       If the task produces searach results, the following optional
 *       attributes may also included:
 *       <dl>
 *         <dt>layers</dt> <dd>An array of IDs of layers that were matched
 *              during the search</dd>
 *         <dt>targetLayer</dt> <dd>The ID of the target layer</dd>
 *         <dt>size</dt> <dd>How many results are available</dd>
 *         <dt>resultsName</dt> <dd>The name of the result set, if any</dd>
 *         <dt>seriesId</dt> <dd>The unique database ID of the result set, if any</dd>
 *         <dt>totalUtteranceDuration</dt> <dd>The duration of all the
 *             utterances included in the results, if available</dd>
 *         <dt>csv</dt> <dd>The name of the reloaded CSV results file, if any</dd>
 *         <dt>csvColumns</dt> <dd>If the results were reloaded from a
 *             CSV file, this is an array of strings representing the
 *             CSV columns in the file.</dd>
 *       </dl>
 *      </li>
 *      <li><em> Response Status </em> <em> 200 </em> on success, or 404 if the
 *       task ID is invalid. </li>
 *     </ul></dd> 
 *    <dt> DELETE </dt><dd>
 *     An ID must be supplied for DELETE operations. If the "cancel"
 *     parameter is supplied, the running task is cancelled.
 *     Otherwise, the finished task is released.
 *     Only the user that created the task, or "admin" users can cancel/delete the task.
 *     <ul>
 *      <li><em> Optional parameters </em>
 *        <ul>
 *         <li><em> cancel </em> "true" to cancel the task but not  release it. </li>
 *        </ul>
 *      </li>
 *      <li><em> Response Status </em> <em> 200 </em> on success, 404 if the
 *       task ID is invalid, 403 if the user is not allowed to
 *       cancel/release the taks, or 400 if no ID was supplied. </li>
 *      <li><em> Response Body </em> - the standard JSON envelope,
 *       with a model representing the current state of the task, as
 *       with GET requests.
 *     </ul></dd> 
 *   </dl>
 *  </p>
 * @author Robert Fromont
 */
public class Task extends APIRequestHandler {

  SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSX");

  /**
   * Constructor
   */
  public Task() {
  } // end of constructor
  
  /**
   * Returns information about current tasks, or the given
   * task if an ID is specified.
   * @param pathInfo The URL path from which the upload ID can be inferred.
   * @param parameters Request parameter map.
   * @param httpStatus Receives the response status code, in case of error.
   * @return JSON-encoded object representing the response.
   */
  public JsonObject get(
    String pathInfo, RequestParameters parameters, Consumer<Integer> httpStatus) {
    return get(pathInfo, parameters, httpStatus, null);
  }
  /**
   * Returns information about current tasks, or the given
   * task if an ID is specified.
   * @param pathInfo The URL path from which the upload ID can be inferred.
   * @param parameters Request parameter map.
   * @param httpStatus Receives the response status code, in case of error.
   * @return JSON-encoded object representing the response.
   */
  protected JsonObject get(
    String pathInfo, RequestParameters parameters, Consumer<Integer> httpStatus,
    String message) {
    // get ID
    if (pathInfo == null || pathInfo.equals("/") || pathInfo.indexOf('/') < 0) {
      // list all thread IDs
      Thread[] threads = new Thread[
        nzilbb.labbcat.server.task.Task.getTaskThreadGroup().activeCount()];
      nzilbb.labbcat.server.task.Task.getTaskThreadGroup().enumerate(threads);
      JsonArrayBuilder tasks = Json.createArrayBuilder();
      for (Thread thread : threads) tasks.add(thread.getId());
      return successResult(tasks.build(), null);  
    }
    String id = pathInfo.substring(pathInfo.lastIndexOf('/') + 1);
    if (id.length() == 0) {
      httpStatus.accept(SC_BAD_REQUEST);
      return failureResult("No ID specified.");
    }
    long threadId = -1;
    try {
       threadId = Long.parseLong(id);
    } catch(NumberFormatException exception) {
      httpStatus.accept(SC_BAD_REQUEST);
      return failureResult("Invalid ID: {0}", id);
    }
    nzilbb.labbcat.server.task.Task task = nzilbb.labbcat.server.task.Task.findTask(
      threadId);
    if (task == null) {
      httpStatus.accept(SC_NOT_FOUND);
      return failureResult("Invalid ID: {0}", id);
    }
    if (!"false".equals(parameters.getString("keepalive"))) task.keepAlive();
    JsonObjectBuilder model = Json.createObjectBuilder();
    model = model.add("threadId", threadId);
    model = model.add("threadName", task.getName());
    model = model.add("who", task.getWho());
    model = model.add("creationTime", iso.format(task.getCreationTime()));
    if (task.getLastException() != null) {
      model = model.add("lastException", ""+task.getLastException());
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      task.getLastException().printStackTrace(pw);
      model = model.add("stackTrace", sw.toString());
      //TODO vErrors.add(task.getLastException().getMessage());
    }
    if (task.getResultUrl() != null) {
      model = model.add("resultUrl", task.getResultUrl());
      model = model.add(
        "resultTarget", Optional.ofNullable(task.getResultTarget()).orElse("task-"+id));
    }
    if (task.getResultText() != null)
      model = model.add("resultText", task.getResultText());
    model = model.add("running", task.getRunning());
    model = model.add("duration", task.getDuration());
    if (task.getPercentComplete() != null) {
      model = model.add("percentComplete", task.getPercentComplete());
    } else {
      model = model.add("percentComplete", 0);
    }
    if (task.getStatus() != null)
      model = model.add("status", task.getStatus());
    if ("true".equals(parameters.getString("log")))
      model = model.add("log", task.getLog());
    model = model.add(
      "refreshSeconds",
      !task.getRunning()?30 // if it's not running, check back after half a minute
      :task.getDuration() < 10000?2 // if it's just started, very frequent
      :5); // otherwise, 5 second intervals are fine
    if (task instanceof SearchTask) {
      SearchTask search = (SearchTask)task;
      if (search.getMatrix() != null) {
        Set<String> layerIds = search.getMatrix().layerMatchStream()
          .map(match -> match.getId())
          .collect(Collectors.toSet());
        JsonArrayBuilder layers = Json.createArrayBuilder();
        for (String layer : layerIds) layers = layers.add(layer);
        model = model.add("layers", layers.build());
        String targetLayer = search.getMatrix().getTargetLayerId();
        if (targetLayer != null) {
          model = model.add("targetLayer", search.getMatrix().getTargetLayerId());
        }
      }
      SearchResults results = search.getResults();
      if (results != null) {
        model = model.add("size", results.size());
        if (results.getName() != null)
          model = model.add("resultsName", results.getName());
        if (results instanceof SqlSearchResults) {
          SqlSearchResults sqlResults = (SqlSearchResults)results;
          model = model.add("seriesId", ""+sqlResults.getId());
          try {
            try (Connection connection = newConnection()) {
              model = model.add("totalUtteranceDuration",
                                sqlResults.totalUtteranceDuration(connection));
            }
          } catch(SQLException exception) {
          }
        } // SqlSearchResults
        if (results instanceof CsvResults) {
          CsvResults csvResults = (CsvResults)results;
          //model = model.add("seriesId", ""+csvResults.getName());
          if (csvResults.getCsvFile() != null) {
            model = model.add("csv", csvResults.getCsvFile().getName());
          }
          if (csvResults.getCsvColumns() != null) {
            JsonArrayBuilder columns = Json.createArrayBuilder();
            for (String c : csvResults.getCsvColumns()) columns = columns.add(c);
            model = model.add("csvColumns", columns);
          }
        } // CsvResults
      } // results
    } // SearchTask
    return successResult(model.build(), message);  
  }
  
  /**
   * Either cancel a currently running thread, or release a finished thread.
   * @param pathInfo The URL path from which the upload ID can be inferred.
   * @param parameters Request parameter map.
   * @param httpStatus Receives the response status code, in case of error.
   * @return JSON-encoded object representing the response.
   */
  public JsonObject delete(
    String pathInfo, RequestParameters parameters, Consumer<Integer> httpStatus) {
    // get ID
    if (pathInfo == null || pathInfo.equals("/") || pathInfo.indexOf('/') < 0) {
      // no path component
      httpStatus.accept(SC_BAD_REQUEST);
      return failureResult("No ID specified.");
    }
    String id = pathInfo.substring(pathInfo.lastIndexOf('/') + 1);
    if (id.length() == 0) {
      httpStatus.accept(SC_BAD_REQUEST);
      return failureResult("No ID specified.");
    }
    long threadId = -1;
    try {
       threadId = Long.parseLong(id);
    } catch(NumberFormatException exception) {
      httpStatus.accept(SC_BAD_REQUEST);
      return failureResult("Invalid ID: {0}", id);
    }
    nzilbb.labbcat.server.task.Task task = nzilbb.labbcat.server.task.Task.findTask(
      threadId);
    if (task == null) {
      httpStatus.accept(SC_NOT_FOUND);
      return failureResult("Invalid ID: {0}", id);
    }
    if (task.getWho() != null) {
      if (!context.isUserInRole("admin")) {
        String requestUser = context.getUser();
        // with no user auth, the users are represented by the host
        // they connect from
        if (requestUser == null) requestUser = context.getUserHost();
        if (!task.getWho().equals(requestUser)) {
          httpStatus.accept(SC_FORBIDDEN);
          return failureResult("Not allowed.");
        } // not the original user
      } // not admin user
    } // task belongs to someone
    context.servletLog("DELETE " + id + " " + parameters.getString("cancel"));
    if (parameters.getString("cancel") != null) {
      task.cancel();
      return get(pathInfo, parameters, httpStatus, localize("Cancelled {0}", id));
    } else {
      task.release();
      return successResult(null, "Released {0}", id);  
    }
    // return the thread info
  }
} // end of class Task
