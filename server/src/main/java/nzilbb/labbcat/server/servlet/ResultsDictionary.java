//
// Copyright 2023 New Zealand Institute of Language, Brain and Behaviour, 
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

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Vector;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.stream.JsonGenerator;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import nzilbb.ag.Anchor;
import nzilbb.ag.Annotation;
import nzilbb.ag.Constants;
import nzilbb.ag.Graph;
import nzilbb.ag.Layer;
import nzilbb.ag.Schema;
import nzilbb.labbcat.server.db.IdMatch;
import nzilbb.labbcat.server.db.SqlConstants;
import nzilbb.labbcat.server.db.SqlGraphStore;
import nzilbb.labbcat.server.db.SqlGraphStoreAdministration;
import nzilbb.labbcat.server.db.SqlSearchResults;
import nzilbb.labbcat.server.db.StoreCache;
import nzilbb.labbcat.server.search.ArraySearchResults;
import nzilbb.labbcat.server.search.Column;
import nzilbb.labbcat.server.search.Matrix;
import nzilbb.labbcat.server.search.SearchResults;
import nzilbb.labbcat.server.search.SearchTask;
import nzilbb.labbcat.server.task.Task;
import nzilbb.util.IO;

/**
 * <tt>/api/results/dictionary</tt>
 * : Generates a dictionary from search results.
 * <p><b>Input HTTP parameters</b>:
 * <ul>
 *  <li><i>threadId</i> - The search task ID returned by a previous call to
 *      <tt>/api/search</tt>. </li>
 *  <li><i>definitionLayerId</i> - (Optional) ID of the layer that provides the 'definition' of
 *      each entry. If not specified, a word-list is returned.</li>
 * </ul>
 * <br><b>Output</b>: A plain text file containing a dictionary generated from the given
 * search results, with repeat words numbered in parentheses, e.g.
 * <pre>
 * tea	ti
 * teach	tiJ
 * teacher	tiJ@
 * teacher(2)	tiJ@r
 * teachers	tiJ@z
 * teaching	tiJIN
 * team	tim
 * teams	timz
 * tears	t7z
 * tears(2)	t8z
 *</pre>
 * @author Robert Fromont
 */
@WebServlet({"/api/results/dictionary"})
public class ResultsDictionary extends LabbcatServlet { // TODO unit test
  
  /**
   * Constructor
   */
  public ResultsDictionary() {
  } // end of constructor
  
  // Servlet methods
  
  /**
   * The GET method for the servlet.
   * <p> This expects a multipart request body with parameters as defined above.
   * @param request HTTP request
   * @param response HTTP response
   */
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    response.setCharacterEncoding("UTF-8");
    response.setContentType("text/plain");
    PrintWriter writer = response.getWriter();

    try {
      SqlGraphStoreAdministration store = getStore(request);
      Schema schema = store.getSchema();
      Connection connection = store.getConnection();
      
      try {
        // parameters
        String threadId = request.getParameter("threadId");
        if (threadId == null) {
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          writer.println("No task ID specified.");
          return;
        }
        Task task = Task.findTask(Long.valueOf(threadId));
        if (task == null) {
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          writer.println(localize(request, "Invalid task ID: {0}", "\""+threadId+"\""));
          return;
        } else if (!(task instanceof SearchTask)) {
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          writer.println(localize(request, "Invalid task ID: {0}", task.getClass().getName()));
          return;
        }
        SearchTask search = (SearchTask)task;
        if (!(search.getResults() instanceof SqlSearchResults)) {
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          writer.println(
            localize(request, "Invalid task ID: {0}", search.getResults().getClass().getName()));
          return;
        }
        String definitionLayerId = request.getParameter("definitionLayerId");
        if (definitionLayerId == null) definitionLayerId = "orthography";
        Layer definitionLayer = schema.getLayer(definitionLayerId);
        if (definitionLayerId == null) schema.getWordLayer();

        // get search results
        search.keepAlive(); // prevent the task from dying while we're still interested
        SqlSearchResults results = (SqlSearchResults)search.getResults();

        String searchName = results.getName();
        if (searchName == null || searchName.trim().length() == 0
            && search.getMatrix() != null) {
          searchName = search.getMatrix().getDescription();
        }
        if (searchName == null || searchName.trim().length() == 0
            && search.getDescription() != null) {
          searchName = search.getDescription();
        }
        String fileName = IO.SafeFileNameUrl(searchName);
        if (fileName.length() > 150) fileName = fileName.substring(0, 150);
        fileName = "dictionary_" + fileName + ".txt";
        response.setHeader(
          "Content-Disposition",
          "attachment; filename*=\"" + URLEncoder.encode(fileName.toString(), "UTF-8")+"\"");

        // create SQL
        PreparedStatement sql = connection.prepareStatement(
          "SELECT DISTINCT token.label AS word, pron.label AS definition"
          +" FROM result"
          +" INNER JOIN annotation_layer_"+SqlConstants.LAYER_ORTHOGRAPHY+" first_word"
          +" ON result.first_matched_word_annotation_id = first_word.word_annotation_id"
          +" INNER JOIN annotation_layer_"+SqlConstants.LAYER_ORTHOGRAPHY+" last_word"
          +" ON result.last_matched_word_annotation_id = last_word.word_annotation_id"
          +" INNER JOIN annotation_layer_"+SqlConstants.LAYER_ORTHOGRAPHY+" token"
          +" ON token.turn_annotation_id = first_word.turn_annotation_id"
          +" AND token.ordinal_in_turn"
          +" BETWEEN first_word.ordinal_in_turn AND last_word.ordinal_in_turn"
          +" INNER JOIN annotation_layer_"+definitionLayer.get("layer_id")+" pron"
          +" ON token.word_annotation_id = pron.word_annotation_id"
          +" WHERE search_id = ?"
          +" ORDER BY token.label");
        boolean wordListOnly = definitionLayer.get("layer_id")
          .equals(SqlConstants.LAYER_ORTHOGRAPHY);
        sql.setLong(1, results.getId());
        ResultSet rs = sql.executeQuery();
        search.keepAlive(); // prevent the task from dying while we're still interested
        boolean trackRepetition = true;
        try {
          String lastWord = "";
          int repetition = 0;
          while (rs.next()) {
            String word = rs.getString(1);
            if (trackRepetition) {
              if (!word.equals(lastWord)) {
                lastWord = word;
                repetition = 0;
              }
              if (++repetition > 1) {
                word += "("+repetition+")";
              }
            }
            writer.print(word);
            if (!wordListOnly) {
              String definition = rs.getString(2);
              writer.print("\t");
              writer.print(definition);
            }
            writer.println();
            search.keepAlive();
          } // next entry
        } finally {
          rs.close();
          sql.close();
        }
        
      } finally {
        cacheStore(store);
      }
      
    } catch(Exception ex) {
      throw new ServletException(ex);
    }
  }
  
  private static final long serialVersionUID = -1;
} // end of class ResultsDictionary
