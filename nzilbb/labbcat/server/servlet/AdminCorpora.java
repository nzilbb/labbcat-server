//
// Copyright 2020 New Zealand Institute of Language, Brain and Behaviour, 
// University of Canterbury
// Written by Robert Fromont - robert.fromont@canterbury.ac.nz
//
//    This file is part of LaBB-CAT.
//
//    LaBB-CAT is free software; you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation; either version 2 of the License, or
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
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;

/**
 * Servlet that allows administration of rows in the the <em> corpus </em> table.
 * <p> See <a href="package-summary.html#/api/admin/corpora">API summary</a> for more details.
 * @author Robert Fromont robert@fromont.net.nz
 */
@WebServlet(urlPatterns = "/api/admin/corpora/*", loadOnStartup = 20)
@RequiredRole("admin")
public class AdminCorpora extends TableServletBase {   

   public AdminCorpora() {
      super("corpus", // table
            new Vector<String>() {{ // primary keys
               add("corpus_id");
            }},
            new Vector<String>() {{ // URL keys
               add("corpus_name");
            }},
            new Vector<String>() {{ // columns
               add("corpus_language");
               add("corpus_description");
            }},
            "corpus_name"); // order
      
      create = true;
      read = true;
      update = true;
      delete = true;
      
      autoKey = "corpus_id";
      autoKeyQuery = "SELECT COALESCE(max(corpus_id) + 1, 1) FROM corpus";      
      
      deleteChecks = new Vector<DeleteCheck>() {{
            add(new DeleteCheck(
                   "SELECT COUNT(*), MIN(transcript_id) FROM transcript WHERE corpus_name = ?",
                   "corpus_name",
                   "{0,choice,1#There is still a transcript using this corpus: {1}"
                   +"|1<There are still {0} transcripts using this corpus, including {1}}"));
            add(new DeleteCheck(
                   "SELECT COUNT(*), MIN(speaker.name) FROM speaker_corpus"
                   +" INNER JOIN speaker ON speaker_corpus.speaker_number = speaker.speaker_number"
                   +" WHERE speaker_corpus.corpus_id = ?",
                   "corpus_id",
                   "{0,choice,1#There is still a participant using this corpus: {1}"
                   +"|1<There are still {0} participants using this corpus, including {1}}"));
         }};
   }

   /**
    * Validates a record before INSERTing it.
    * @param request The request.
    * @param record The incoming record to validate, to which attributes can be added.
    * @param connection A connection to th database.
    * @return A JSON representation of the valid record, which may or may not be the same
    * object as <var>record</var>.
    * @throws ValidationException If the record is invalid.
    */
   @Override
   protected JsonObject validateBeforeCreate(
      HttpServletRequest request, JsonObject record,
      Connection connection) throws ValidationException {
      
      Vector<String> errors = null;
      try {
         if (!record.containsKey("corpus_name") || record.isNull("corpus_name")) {
            errors = new Vector<String>() {{
                  add(localize(request, "No corpus name was provided.")); }};
         } else {
            // trim name
            if (!record.getString("corpus_name").equals(record.getString("corpus_name").trim())) {
               record = createMutableCopy(record, "corpus_name")
                  .add("corpus_name", record.getString("corpus_name").trim())
                  .build();
            }
            if (record.getString("corpus_name").length() == 0) {
               errors = new Vector<String>() {{
                     add(localize(request, "Corpus name cannot be blank.")); }};
            }
         }
      } catch (JsonException x) {
         if (errors == null) errors = new Vector<String>();
         errors.add(x.toString());
         // not expecting this, so log it:
         System.err.println("AdminCorpora.validateBeforeUpdate: ERROR " + x);
      }
      if (errors != null) throw new ValidationException(errors);
      return record;
   } // end of validateBeforeUpdate()
   
   private static final long serialVersionUID = 1;
} // end of class AdminCorpora
