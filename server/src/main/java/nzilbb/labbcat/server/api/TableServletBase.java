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
package nzilbb.labbcat.server.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Types;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.stream.JsonGenerator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
/**
 * Base class that handles generic database table management.
 * <p> Subclasses specify the table name, key fields, and fields.
 * @author Robert Fromont robert@fromont.net.nz
 */
public class TableServletBase extends APIRequestHandler {
  
  @SuppressWarnings("serial")
  protected class ValidationException extends Exception {
    List<String> errors;
    public ValidationException(List<String> errors) {
      super(errors.toString());
      this.errors = errors;
    }
  }
  
  /** The name of the data table */
  protected String table;
  
  /** An ordered list of database key field names */
  protected List<String> dbKeys;
  
  /** An ordered list of URL path field names */
  protected List<String> urlKeys;
  
  /** An ordered list of non-key fields */
  protected List<String> columns;
  
  /** An ordered list of non-key fields for full-listing requests */
  protected List<String> listColumns;
  
  /** A map from table column names to object attribute names */
  protected HashMap<String,String> columnToAttribute = new HashMap<String,String>();
  
  /** A map from object attribute names to table column names */
  protected HashMap<String,String> attributeToColumn = new HashMap<String,String>();
  
  /** WHERE condition */
  protected String whereClause;
  
  /** ORDER clause */
  protected String orderClause;
  
  /** Whether Create operations are allowed via POST - default = false */
  protected boolean create = false;
  
  /** Whether Read operations are allowed via GET - default = true */
  protected boolean read = true;
  
  /** Whether Update operations are allowed via PUT - default = false */
  protected boolean update = false;
  
  /** Whether Delete operations are allowed via DELETE - default = false */
  protected boolean delete = false;
  
  /** Key that is automatically generated when records are created */
  protected String autoKey = null;
  
  /** Query to generate {@link #autoKey} when records are created (null if the database
   * generates the new key itself) */
  protected String autoKeyQuery = null;
  
  /** Are empty key values allowed? */
  protected boolean emptyKeyAllowed = false;
  
  /** 
   * A query used to check each row to determine if there's a reason it can't be deleted.
   * <var>deleteQuery</var> should be a query that
   * <ul>
   *  <li> Accepts all the specified fields as parameters </li>
   *  <li> Returns a numeric column, which is 0 when the row can be deleted. </li>
   *  <li> Optionally returns a second column for informational purposes. </li>
   * </ul>
   * e.g. <code>
   * new DeleteCheck("SELECT COUNT(*), MIN(transcript_id) FROM transcript WHERE corpus_name = ?",
   *                 "corpus_name",
   *                 "{0} {0,choice,1#transcript|1&lt;transcripts} use this corpus, e.g. {1}")
   * </code>
   */
  public class DeleteCheck {
    String query;
    List<String> fields;
    String reason;
    public DeleteCheck(String query, String field, String reason) {
      this(query, new Vector<String>(){{ add(field); }}, reason);
    }
    public DeleteCheck(String query, List<String> fields, String reason) {
      this.query = query;
      this.fields = fields;
      this.reason = reason;
    }
    
    /**
     * Formats the reason by including the returned count if included in the error.
     * @param rs
     * @return The formatted message.
     */
    public String formatReason(ResultSet rs, TableServletBase servlet) {
      if (!reason.contains("{")) return servlet.localize(reason);
      try {
        return servlet.localize(
          reason, rs.getLong(1), // the number returned
          reason.contains("{1")?rs.getString(2):null); // the optional second column
      } catch (Throwable anything) {
        return reason;
      }
    } // end of formatMessage()
  }
  
  /** An ordered list of checks to discover whether a record can be deleted */
  protected List<DeleteCheck> deleteChecks;
  
  /** An ordered list of updates to run before deleting a record */
  protected List<DeleteCheck> beforeDelete;
  
  /** 
   * Constructor from attributes.
   * @param table The name of the data table.
   * @param dbKeys An ordered list of key field names.
   * @param columns An ordered list of non-key fields, for single-item and full-list requests.
   * @param orderClause ORDER clause.
   */
  protected TableServletBase(
    String table, List<String> dbKeys, List<String> columns, String orderClause) {      
    this(table, dbKeys, dbKeys, columns, columns, orderClause);
  }
  
  /** 
   * Constructor from attributes.
   * @param table The name of the data table.
   * @param dbKeys An ordered list of primary key field names.
   * @param urlKeys An ordered list of key field names used to identify a record on the URL path.
   * @param columns An ordered list of non-key fields, for single-item and full-list requests.
   * @param orderClause ORDER clause.
   */
  protected TableServletBase(
    String table, List<String> dbKeys, List<String> urlKeys, List<String> columns,
    String orderClause) {
    this(table, dbKeys, urlKeys, columns, columns, orderClause);
  }   
  
  /** 
   * Constructor from attributes.
   * @param table The name of the data table.
   * @param dbKeys An ordered list of database key field names.
   * @param urlKeys An ordered list of key field names used to identify a record on the URL path.
   * @param columns An ordered list of non-key fields.
   * @param listColumns An ordered list of non-key fields for full-listing requests
   * @param orderClause ORDER clause.
   */
  protected TableServletBase(
    String table, List<String> dbKeys, List<String> urlKeys, List<String> columns,
    List<String> listColumns, String orderClause) {      
    this.table = table;
    this.dbKeys = dbKeys;
    this.urlKeys = urlKeys;
    this.columns = columns;
    this.listColumns = listColumns;
    this.orderClause = orderClause;
    
    mapAliases();
  }
  
  /**
   * Defines aliases for table columns.
   * @param columnToAttribute
   */
  protected void setAliases(HashMap<String,String> columnToAttribute) {
    this.columnToAttribute = columnToAttribute;
    mapAliases();
  } // end of setAliases()
  
  /**
   * Populate maps between table column names and object attribute names.
   */
  private void mapAliases() {
    // any fields that have no alias in the object are mapped to themselves
    for (String field : new Vector<String>() {{
      addAll(dbKeys);
      addAll(urlKeys);
      addAll(columns);
    }}) {
      if (!columnToAttribute.containsKey(field)) {
        columnToAttribute.put(field, field);
      }
    } // next field
      // reverse mapping
    for (String field : columnToAttribute.keySet()) {
      attributeToColumn.put(columnToAttribute.get(field), field);
    }
  } // end of mapAliases()
  
  /**
   * GET handler lists all rows. 
   * <p> The return is JSON encoded, unless the "Accept" request header, or the "Accept"
   * request parameter, is "text/csv", in which case CSV is returned.
   * @param pathInfo The URL path.
   * @param parameters Request parameter map.
   * @param requestHeaders Access to HTTP request headers.
   * @param out Response body output stream.
   * @param contentTypeConsumer Receives the content type for specification in the response headers.
   * @param fileName Receives the filename for specification in the response headers.
   * @param httpStatus Receives the response status code, in case of error.
   */
  public void get(String pathInfo, RequestParameters parameters, UnaryOperator<String> requestHeaders, OutputStream out, Consumer<String> contentTypeConsumer, Consumer<String> fileName, Consumer<Integer> httpStatus) {
    try {
      Connection connection = newConnection();
      try {
        if (!hasAccess(connection)) {
          httpStatus.accept(SC_FORBIDDEN);
          return;
        } else if (!read) {
          httpStatus.accept(SC_METHOD_NOT_ALLOWED);
          return;
        } else {
          // servlet returns JSON by default, but can be asked to return CSV
          boolean csv = requestHeaders.apply("Accept").indexOf("text/csv") >= 0
            || "text/csv".equals(parameters.getString("Accept"));
          if (csv) {
            contentTypeConsumer.accept("text/csv;charset=UTF-8");
          } else {
            contentTypeConsumer.accept("application/json;charset=UTF-8");
          }
          try {
            String[] keyValues = null;
            if (pathInfo != null && !pathInfo.equals("/")) {
              keyValues = pathInfo.substring(1).split("/");
            }
            boolean partialKey = keyValues != null && keyValues.length < urlKeys.size();
            if (csv) {
              String name = table;
              if (keyValues != null) {
                for (String value : keyValues) name += "-"+value;
              }
              fileName.accept(name+".csv");
            }
                  
            // return a list of rows
            StringBuilder query = new StringBuilder();
            Vector<String> allColumns = new Vector<String>(dbKeys);
            if (dbKeys != urlKeys) allColumns.addAll(urlKeys);
            if (keyValues != null) {
              allColumns.addAll(columns);
            } else { // full list
              allColumns.addAll(listColumns);
            }
            for (String column : allColumns) {
              query.append(query.length() == 0?"SELECT ":", ");
              query.append(column);
            } // next column
            query.append(" FROM ");
            query.append(table);
            StringBuffer where = new StringBuffer(); 
            if (whereClause != null && whereClause.length() > 0) {
              where.append(whereClause);
            }
            // or only one row, if there's a path
            if (keyValues != null) {
              int k = 0;
              for (String column : urlKeys) {
                
                // only add as many parameters as values
                if (++k > keyValues.length) break;
                
                if (where.length() > 0) {
                  where.append(" AND ");
                }
                where.append(column);
                where.append(" = ?");
              } // next key
            } // key values specified in path
            if (where.length() > 0) {
              query.append(" WHERE ");
              query.append(where);
            }
            if (orderClause != null && orderClause.length() > 0) {
              query.append(" ORDER BY ");
              query.append(orderClause);
            }
            
            if (parameters.getString("pageNumber") != null) { // page
              try {
                int page = Integer.parseInt(parameters.getString("pageNumber"));
                int pageLength = 20;
                if (parameters.getString("pageLength") != null) {
                  pageLength = Integer.parseInt(parameters.getString("pageLength"));
                }
                int offset = page * pageLength;
                query.append(" LIMIT " + offset + ", " + pageLength);
              } catch (Exception x) {
                context.servletLog("ERROR cannot paginate: " + x);
              }
            } // page
            
            context.servletLog("GET " + pathInfo + " : " + query.toString()); // TODO remove
            PreparedStatement sql = connection.prepareStatement(query.toString());
            if (keyValues != null) {
              int c = 1;
              for (String value : keyValues) {
                sql.setString(c++, value);
              } // next key
            } // key values specified in path
            ResultSet rs = sql.executeQuery();
            try {
              CSVPrinter csvOut = csv
                ?new CSVPrinter(new OutputStreamWriter(out, "UTF-8"),
                                CSVFormat.EXCEL.withDelimiter(','))
                :null;
              JsonGenerator jsonOut = csv
                ?null:
                Json.createGenerator(new OutputStreamWriter(out, "UTF-8"));
              boolean multipleRows = keyValues == null || partialKey;
              if (jsonOut != null) {
                startResult(jsonOut, multipleRows); 
              }
              int rowCount = 0;
              try {
                boolean headersWritten = false;
                while (rs.next()) {
                  ResultSetMetaData meta = rs.getMetaData();
                  outputRow(rs, allColumns, meta, jsonOut, connection, multipleRows);
                  headersWritten = outputRow(rs, allColumns, meta, csvOut, headersWritten);
                  rowCount++;
                } // next row
                if (rowCount == 0 && keyValues != null && !partialKey) {
                  httpStatus.accept(SC_NOT_FOUND);
                  // JsonWriter hates to write nothing, so give it an empty object
                  // if (jsonOut != null) jsonOut.writeStartObject().writeEnd(); // object
                }
              } finally {
                // if (jsonOut != null) {
                //    if (keyValues == null || partialKey) {
                //       jsonOut.writeEnd(); // all rows, finish array
                //    }
                // }
                if (csvOut != null) {
                  try {
                    csvOut.close();
                  } catch(IOException exception) {}
                }
              }
              if (jsonOut != null) {
                endSuccessResult(jsonOut, null);
              }
            } catch (Exception x) {
              contentTypeConsumer.accept("application/json");
              httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
              context.servletLog("TableServletBase GET: ERROR: " + x);
              writeResponse(out, failureResult(x));
            } finally {
                rs.close();
                sql.close();
            }
          } catch(SQLException exception) {
            contentTypeConsumer.accept("application/json");
            httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
            context.servletLog("TableServletBase GET: ERROR: " + exception);
            writeResponse(out, failureResult(exception));
          }
        }
      } finally {
        connection.close();
      }
    } catch(SQLException exception) {
      context.servletLog("TableServletBase GET: Couldn't connect to database: " + exception);
      contentTypeConsumer.accept("application/json;charset=UTF-8");
      writeResponse(out, failureResult(exception));
    }      
  }

  /**
   * Outputs a single row as JSON.
   * @param rs The resultset with the row to return.
   * @param allColumns A list of all columns to return.
   * @param meta Column meta-data.
   * @param jsonOut Writer for serializing to JSON.
   * @param connection A database connection in case delete checks must be done.
   * @throws SQLException
   */
  protected boolean outputRow(ResultSet rs, List<String> allColumns, ResultSetMetaData meta, JsonGenerator jsonOut, Connection connection, boolean modelIsArray) throws SQLException {
    if (jsonOut == null) return false;
    if (modelIsArray) jsonOut.writeStartObject();
    int c = 1;
    try {
      for (String column: allColumns) {
        String value = rs.getString(column);
        String attribute = columnToAttribute.get(column);
        if (value == null) {
          jsonOut.writeNull(attribute);
        } else {
          try {
            switch(meta.getColumnType(c++)) { // get the type right
              case Types.BIGINT:   jsonOut.write(attribute, rs.getLong(column)); break;
              case Types.BIT:      jsonOut.write(attribute, rs.getInt(column) != 0); break;
              case Types.BOOLEAN:  jsonOut.write(attribute, rs.getBoolean(column)); break;
              case Types.DECIMAL:  jsonOut.write(attribute, rs.getDouble(column)); break;
              case Types.DOUBLE:   jsonOut.write(attribute, rs.getDouble(column)); break;
              case Types.FLOAT:    jsonOut.write(attribute, rs.getDouble(column)); break;
              case Types.NUMERIC:  jsonOut.write(attribute, rs.getDouble(column)); break;
              case Types.INTEGER:  jsonOut.write(attribute, rs.getInt(column)); break;
              case Types.SMALLINT: jsonOut.write(attribute, rs.getInt(column)); break;
              case Types.TINYINT:  jsonOut.write(attribute, rs.getInt(column)); break;
              case Types.NULL:     jsonOut.writeNull(attribute); break;
              default:             jsonOut.write(attribute, value); break;
            }
          } catch (SQLDataException x) { // can't determine the type?
            jsonOut.write(attribute, value.toString());
          }                           
        } // no null
      } // next column
      
      // allow subclasses to add data
      editOutputRecord(rs, jsonOut, connection);
      
      String cantDelete = checkCanDelete(rs, null, null, connection);
      if (cantDelete != null) jsonOut.write("_cantDelete", cantDelete);
    } finally {
      if (modelIsArray) jsonOut.writeEnd(); // object
    }
    return true;
  } // end of outputRow()
  
  /**
   * Checks whether the given row can be deleted, and returns a reason if not.
   * @param rs The ResultSet with the row values, or null if <var>json</var> should be
   * used to determine fieldd values.
   * @param json An object with row values, or null if <var>rs</var> should be used to
   * determine field values.
   * @param jsonResult A mutable copy of <var>json</var>. If a reason is found, this object has
   * a new attribute named <q>_cantDelete</q> with the reason as the value.
   * @param connection A connection to the database for running any necessary check queries.
   * @return The reason, if the row can't be deleted, or null if it is deletable.
   */
  protected String checkCanDelete(ResultSet rs, JsonObject json, JsonObjectBuilder jsonResult, Connection connection)
    throws SQLException {
    if (deleteChecks != null) { // need to run delete checks
      for (DeleteCheck check : deleteChecks) {
        PreparedStatement sqlCheck = connection.prepareStatement(check.query);
        // set parameter values
        int p = 1;
        if (rs != null) {
          for (String field : check.fields) {
            sqlCheck.setString(p++, rs.getString(field));
          } // next field
        } else { // TODO
          for (String field : check.fields) {
            sqlCheck.setString(p++, ""+json.get(columnToAttribute.get(field)));
          } // next field
        }
        ResultSet rsCheck = sqlCheck.executeQuery();
        try {
          if (rsCheck.next() && rsCheck.getLong(1) != 0) {
            String reason = check.formatReason(rsCheck, this);
            if (jsonResult != null) jsonResult.add("_cantDelete", reason);
            return reason;
          }
        } finally {
          rsCheck.close();
          sqlCheck.close();
        }
      } // next check
    } // need to run delete checks
    // got this far, no reason not to delete
    return null;
  } // end of checkCanDelete()
  
  /**
   * Outputs a single row as CSV.
   * @param rs
   * @param csvOut
   * @throws SQLException
   */
  protected boolean outputRow(ResultSet rs, List<String> allColumns, ResultSetMetaData meta, CSVPrinter csvOut, boolean headersWritten)
    throws SQLException, IOException {
    if (csvOut == null) return false;
    if (!headersWritten) { // write a line of header
      for (String column: allColumns) {
        csvOut.print(columnToAttribute.get(column));
      }
      csvOut.println();
    }
    int c = 1;
    try {
      for (String column: allColumns) {
        String value = rs.getString(column);
        if (value == null) {
          csvOut.print("");
        } else {
          try {
            switch(meta.getColumnType(c++)) { // get the type right
              case Types.BIGINT:   csvOut.print(rs.getLong(column)); break;
              case Types.BIT:      csvOut.print(rs.getInt(column) != 0); break;
              case Types.BOOLEAN:  csvOut.print(rs.getBoolean(column)); break;
              case Types.DECIMAL:  csvOut.print(rs.getDouble(column)); break;
              case Types.DOUBLE:   csvOut.print(rs.getDouble(column)); break;
              case Types.FLOAT:    csvOut.print(rs.getDouble(column)); break;
              case Types.NUMERIC:  csvOut.print(rs.getDouble(column)); break;
              case Types.INTEGER:  csvOut.print(rs.getInt(column)); break;
              case Types.SMALLINT: csvOut.print(rs.getInt(column)); break;
              case Types.TINYINT:  csvOut.print(rs.getInt(column)); break;
              case Types.NULL:     csvOut.print(""); break;
              default:             csvOut.print(value); break;
            }
          } catch (SQLDataException x) { // can't determine the type?
            csvOut.print(value);
          }
        } // no null                           
      } // next column
    } finally {
      csvOut.println();
    }
    return true;
  } // end of outputRow()
  
  /**
   * Creates a 'mutable' copy of the JSON object, to which attributes can be added.
   * @param json The object to copy.
   * @param excludeAttributen The name of an attribute to exclude from the copy, which
   * can be null
   * @return A builder that already has the given object's attributes added.
   */
  protected JsonObjectBuilder createMutableCopy(JsonObject json, String excludeAttribute) {
    JsonObjectBuilder copy = Json.createObjectBuilder();
    for (String key : json.keySet()) {
      if (!key.equals(excludeAttribute)) {
        JsonValue value = json.get(key);
        copy.add(key, value);
      }
    }
    return copy;
  } // end of createMutableCopy()
  
  /**
   * POST handler - add a new row.
   * @param requestBody Stream containing the request body.
   * @param requestHeaders Access to HTTP request headers.
   * @param out Response body output stream.
   * @param contentTypeConsumer Receives the content type for specification in the response headers.
   * @param httpStatus Receives the response status code, in case of error.
   */
  public void post(InputStream requestBody, UnaryOperator<String> requestHeaders, OutputStream out, Consumer<String> contentTypeConsumer, Consumer<Integer> httpStatus) {
    try {
      Connection connection = newConnection();
      try {
        if (!hasAccess(connection)) {
          httpStatus.accept(SC_FORBIDDEN);
          return;
        } else if (!create) {
          httpStatus.accept(SC_METHOD_NOT_ALLOWED);
          return;
        } else {
          contentTypeConsumer.accept("application/json;charset=UTF-8");
          
          StringBuilder query = new StringBuilder();
          StringBuilder parameters = new StringBuilder();
          Vector<String> allColumns = new Vector<String>(dbKeys);
          if (dbKeys != urlKeys) allColumns.addAll(urlKeys);
          allColumns.addAll(columns);
          for (String column : allColumns) {
            // skip auto-generated key
            if (autoKeyQuery == null && column.equals(autoKey)) continue;
            
            if (query.length() == 0) {
              query.append("INSERT INTO ");
              query.append(table);
              query.append(" (");
            } else {
              query.append(", ");
              parameters.append(", ");
            }
            query.append(column);
            parameters.append("?");
          } // next column
          query.append(") VALUES (");
          query.append(parameters);
          query.append(")");

          try {
            // read the incoming object
            JsonReader reader = Json.createReader( // ensure we read as UTF-8
              new InputStreamReader(requestBody, "UTF-8"));
            // incoming object:
            JsonObject json = reader.readObject();
          
            json = validateBeforeCreate(json, connection);
               
            // return a copy of it, which we might add stuff to:
            JsonObjectBuilder jsonResult = createMutableCopy(
              json, "_changed"); // exclude _changed flag added by client
          
            StringBuffer key = new StringBuffer();
            try {
            
              // insert the row
              context.servletLog("POST " + query.toString()); // TODO remove
              PreparedStatement sql = connection.prepareStatement(query.toString());
              int c = 1;
              for (String column : dbKeys) {
                String attribute = columnToAttribute.get(column);
                // skip auto-generated key
                if (column.equals(autoKey)) {
                  if (autoKeyQuery == null) {
                    // the database will generate the key
                    continue;
                  } else {
                    // run the query to get the key value
                    PreparedStatement sqlAutoKey = connection.prepareStatement(autoKeyQuery);
                    ResultSet rsAutoKey = sqlAutoKey.executeQuery();
                    try {
                      if (rsAutoKey.next()) {
                        // generate the value
                        String value = rsAutoKey.getString(1);
                        // put it into the INSERT statement
                        sql.setString(c++, value);
                        if (dbKeys == urlKeys) {
                          // add it to the key (for error reporting)
                          key.append("/");
                          key.append(value);
                        }
                        // add it to the JSON object, for returning to the caller
                        jsonResult.add(attribute, value);
                      }
                    } finally {
                      rsAutoKey.close();
                      sqlAutoKey.close();
                    }
                  }
                } else {
                  String value = ""+json.get(attribute);
                  if (!json.containsKey(attribute) || json.isNull(attribute)) value = null;
                  sql.setString(c++, value); 
                  if (dbKeys == urlKeys) {
                    // add it to the key (for error reporting)
                    key.append("/");
                    key.append(value);
                  }
                }
              } // next key
              if (dbKeys != urlKeys) {
                for (String column : urlKeys) {
                  String attribute = columnToAttribute.get(column);
                  String value = ""+json.get(attribute);
                  if (!json.containsKey(attribute) || json.isNull(attribute)) value = null;
                  sql.setString(c++, value);
                  // add it to the key (for error reporting)
                  key.append("/");
                  key.append(value);
                } // next column
              }
              for (String column : columns) {
                String attribute = columnToAttribute.get(column);
                String value = ""+json.get(attribute);
                if (!json.containsKey(attribute) || json.isNull(attribute)) value = null;
                sql.setString(c++, value);
              } // next column
              try {
                int rows = sql.executeUpdate();
                if (rows == 0) {
                  httpStatus.accept(SC_CONFLICT);
                  writeResponse(out, failureResult("Record not added: {0}", key.substring(1)));
                } else {
                
                  // if the key was auto-generated
                  if (autoKey != null && autoKeyQuery == null) {
                    // get it's value
                    PreparedStatement sqlLastId = connection.prepareStatement(
                      "SELECT LAST_INSERT_ID()");
                    ResultSet rsLastId = sqlLastId.executeQuery();
                    try {                        
                      // add the key attribute
                      rsLastId.next();
                      jsonResult.add(columnToAttribute.get(autoKey), rsLastId.getLong(1));
                    } finally {
                      rsLastId.close();
                      sqlLastId.close();
                    }
                  }
                
                  // give subclasses an opportunity to process the new record
                  editNewRecord(json, jsonResult, connection);
                
                  checkCanDelete(null, json, jsonResult, connection);
                
                  // record added, so return it
                  writeResponse(
                    out, successResult(jsonResult.build(), "Record created."));
                }
              } finally {
                sql.close();
                connection.close();
              }
            } catch(SQLIntegrityConstraintViolationException exception) {
              // row is already there
              httpStatus.accept(SC_CONFLICT);
              writeResponse(out, failureResult("Record already exists: {0}", key.substring(1)));
            } catch(SQLException exception) {
              httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
              context.servletLog("TableServletBase POST: ERROR: " + exception);
              writeResponse(out, failureResult(exception));
            }
          } catch(java.io.UnsupportedEncodingException x) {
            context.servletLog("TableServletBase POST: Encoding error: " + x);
            contentTypeConsumer.accept("application/json;charset=UTF-8");
            writeResponse(out, failureResult(x));
          }
        } 
      } catch(ValidationException exception) {
        httpStatus.accept(SC_BAD_REQUEST);
        writeResponse(out, failureResult(exception.errors));
      } finally {
        connection.close();
      }
    } catch(SQLException exception) {
      context.servletLog("TableServletBase POST: Couldn't connect to database: " + exception);
      contentTypeConsumer.accept("application/json;charset=UTF-8");
      writeResponse(out, failureResult(exception));
    }      
  }

   /**
    * PUT handler - update an existing row.
    * @param requestBody Stream supplying the body of the request.
    * @param out Stream for writing the response.
    * @param contentTypeConsumer Consumer for receiving the output content type..
    * @param httpStatus Receives the response status code, in case of error.
    */
  public void put(InputStream requestBody, OutputStream out, Consumer<String> contentTypeConsumer, Consumer<Integer> httpStatus) {
    try {
      Connection connection = newConnection();
         try {
           if (!hasAccess(connection)) {
             httpStatus.accept(SC_FORBIDDEN);
             return;
           } else if (!update) {
             httpStatus.accept(SC_METHOD_NOT_ALLOWED);
             return;
           } else {
             contentTypeConsumer.accept("application/json;charset=UTF-8");
             
             // prepare the UPDATE
             StringBuilder query = new StringBuilder();
             Vector<String> allColumns = new Vector<String>(urlKeys);
             allColumns.addAll(columns);
             for (String column : columns) {
               if (query.length() == 0) {
                 query.append("UPDATE ");
                 query.append(table);
                 query.append(" SET ");
               } else {
                 query.append(", ");
               }
               query.append(column);
               query.append(" = ?");
             } // next column
             StringBuilder where = new StringBuilder();
             for (String column : urlKeys) {
               if (where.length() == 0) {
                 where.append(" WHERE ");
               } else {
                 where.append(" AND ");
               }
               where.append(column);
               where.append(" = ?");
             } // next column
             query.append(where);
             
             try {
               context.servletLog("PUT " + query.toString()); // TODO remove
               PreparedStatement sql = connection.prepareStatement(query.toString());
               
               try {
                 // read the incoming object
                 JsonReader reader = Json.createReader( // ensure we read as UTF-8
                   new InputStreamReader(requestBody, "UTF-8"));
                 // incoming object:
                 JsonObject json = reader.readObject();
                 
                 json = validateBeforeUpdate(json, connection);
                 
                 // return a copy of it, which we might add stuff to:
                 JsonObjectBuilder jsonResult = createMutableCopy(
                   json, "_changed"); // exclude _changed flag added by client
                 
                 int c = 1;
                 StringBuffer key = new StringBuffer();
                 for (String column : columns) {
                   String attribute = columnToAttribute.get(column);
                   String value = ""+json.get(attribute);
                   if (!json.containsKey(attribute) || json.isNull(attribute)) value = null;
                   sql.setString(c++, value);
                 } // next column
                 for (String column : urlKeys) {
                   String attribute = columnToAttribute.get(column);
                   String value = ""+json.get(attribute);
                   if (key.length() > 0) key.append("/");
                   key.append(value);
                   sql.setString(c++, value); 
                 } // next key
                 
                 int rows = sql.executeUpdate();
                 if (rows == 0) {
                   httpStatus.accept(SC_NOT_FOUND);
                   writeResponse(out, failureResult("Record not found: {0}", key));
                 } else {
                   
                   if (dbKeys != urlKeys) {
                     // fill in key values
                     StringBuilder dbKeyQuery = new StringBuilder();
                     for (String column : dbKeys) {
                       dbKeyQuery.append(dbKeyQuery.length() == 0?"SELECT ":", ");
                       dbKeyQuery.append(column);
                     } // next column
                     dbKeyQuery.append(" FROM ");
                     dbKeyQuery.append(table);
                     StringBuffer dbKeyWhere = new StringBuffer(); 
                     for (String column : urlKeys) {
                       if (dbKeyWhere.length() > 0) dbKeyWhere.append(" AND ");
                       dbKeyWhere.append(column);
                       dbKeyWhere.append(" = ?");
                     } // next key
                     dbKeyQuery.append(" WHERE ");
                     dbKeyQuery.append(dbKeyWhere);
                     context.servletLog("PUT key query: " + dbKeyQuery);
                     PreparedStatement sqlKeys = connection.prepareStatement(dbKeyQuery.toString());
                     int k = 1;
                     for (String column : urlKeys) {
                       sqlKeys.setString(k++, ""+json.get(column));
                     } // next key
                     ResultSet rsKeys = sqlKeys.executeQuery();
                     if (rsKeys.next()) {
                       for (String column : dbKeys) {
                         String attribute = columnToAttribute.get(column);
                         jsonResult.add(attribute, rsKeys.getString(column));
                       } // next key value
                     }
                   } // dbKeys != urlKeys
                   
                   // give subclasses an opportunity to process the updated record
                   editUpdatedRecord(json, jsonResult, connection);
                   
                   // set _cantDelete flag?
                   checkCanDelete(null, json, jsonResult, connection);
                   
                   // record update, so return it
                   writeResponse(out, successResult(jsonResult.build(), "Record updated."));
                 }
               } catch(java.io.UnsupportedEncodingException x) {
                 context.servletLog("TableServletBase POST: Encoding error: " + x);
                 contentTypeConsumer.accept("application/json;charset=UTF-8");
                 writeResponse(out, failureResult(x));
               } finally {
                 sql.close();
                 connection.close();
               }
             } catch(ValidationException exception) {
               httpStatus.accept(SC_BAD_REQUEST);
               writeResponse(out, failureResult(exception.errors));
             } catch(SQLException exception) {
               context.servletLog("TableServletBase POST: ERROR: " + exception);
               httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
               writeResponse(out, failureResult(exception));
             }
           } 
         } finally {
           connection.close();
         }
    } catch(SQLException exception) {
      context.servletLog("TableServletBase PUT: Couldn't connect to database: " + exception);
      contentTypeConsumer.accept("application/json;charset=UTF-8");
      writeResponse(out, failureResult(exception));
    }
  }
  
  /**
   * DELETE handler - remove existing row.
   * @param pathInfo The URL path.
   * @param out Response body output stream.
   * @param contentTypeConsumer Receives the content type for specification in the response headers.
   * @param httpStatus Receives the response status code, in case of error.
   */
  public void delete(String pathInfo, OutputStream out, Consumer<String> contentTypeConsumer, Consumer<Integer> httpStatus) {
    try {
      Connection connection = newConnection();
      try {
        if (!hasAccess(connection)) {
          httpStatus.accept(SC_FORBIDDEN);
          return;
        } else if (!delete) {
          httpStatus.accept(SC_METHOD_NOT_ALLOWED);
          return;
        } else {
          contentTypeConsumer.accept("application/json;charset=UTF-8");
          try {
            String[] keyValues = null;
            if (pathInfo != null && !pathInfo.equals("/")) {
              keyValues = pathInfo.substring(1).split("/");
              // only accept a path if all urlKeys have a value
              if (keyValues.length != urlKeys.size()) keyValues = null;
            }
            if (keyValues == null) {
              if (emptyKeyAllowed) {
                String[] emptyKey = {""};
                keyValues = emptyKey;
              } else {
                httpStatus.accept(SC_BAD_REQUEST);
                writeResponse(out, failureResult("Key values not found in path: {0}" + pathInfo));
                return;
              }
            }
            
            StringBuilder query = new StringBuilder();
            StringBuilder parameters = new StringBuilder();
            query.append("DELETE FROM ");
            query.append(table);
            StringBuffer where = new StringBuffer(); 
            if (whereClause != null && whereClause.length() > 0) {
              where.append(whereClause);
            }
            for (String column : urlKeys) {
              if (where.length() > 0) {
                where.append(" AND ");
              }
              where.append(column);
              where.append(" = ?");
            } // next key
            query.append(" WHERE ");
            query.append(where);
            
            if (deleteChecks != null || beforeDelete != null) {
              // get all the field values we'll need
              Vector<DeleteCheck> allQueries = new Vector<DeleteCheck>();
              if (deleteChecks != null) allQueries.addAll(deleteChecks);
              if (beforeDelete != null) allQueries.addAll(beforeDelete);
              HashMap<String,String> checkFieldValues = new HashMap<String,String>();
              HashSet<String> missingFields = new HashSet<String>();
              boolean needNonKeyValues = false;
              for (DeleteCheck check : allQueries) {
                for (String field : check.fields) {
                  checkFieldValues.put(field, null);
                  if (!urlKeys.contains(field)) needNonKeyValues = true;
                }
              } // next check
              
              if (needNonKeyValues) { // get check field values by querying
                StringBuilder checkFieldValuesQuery = new StringBuilder();
                for (String field : checkFieldValues.keySet()) {
                  if (checkFieldValuesQuery.length() == 0) {
                    checkFieldValuesQuery.append("SELECT ");
                  } else {
                    checkFieldValuesQuery.append(", ");
                  }
                  checkFieldValuesQuery.append(field);                           
                } // field
                checkFieldValuesQuery.append(" FROM ");
                checkFieldValuesQuery.append(table);
                checkFieldValuesQuery.append(" WHERE ");
                checkFieldValuesQuery.append(where);
                PreparedStatement sql = connection.prepareStatement(
                  checkFieldValuesQuery.toString());
                int c = 1;
                for (String value : keyValues) {
                  sql.setString(c++, value); 
                } // next key
                ResultSet rs = sql.executeQuery();
                try {
                  if (rs.next()) {
                    for (String field : checkFieldValues.keySet()) {
                      checkFieldValues.put(field, rs.getString(field));
                    } // next field
                  }
                } finally {
                  rs.close();
                  sql.close();
                }
              } else { // get check field values from key
                int k = 0;
                for (String column : urlKeys) {
                  checkFieldValues.put(column, keyValues[k++]);
                } // next key
              }
              
              if (deleteChecks != null) {
                // now that we've got the field values, run each check
                for (DeleteCheck check : deleteChecks) {
                  PreparedStatement sqlCheck = connection.prepareStatement(check.query);
                  // set parameter values
                  int p = 1;
                  for (String field : check.fields) {
                    sqlCheck.setString(p++, checkFieldValues.get(field));
                  } // next field
                  ResultSet rsCheck = sqlCheck.executeQuery();
                  try {
                    if (rsCheck.next() && rsCheck.getLong(1) != 0) {
                      httpStatus.accept(SC_BAD_REQUEST);
                      writeResponse(
                        out, failureResult(check.formatReason(rsCheck, this)));
                      return;
                    }
                  } finally {
                    rsCheck.close();
                    sqlCheck.close();
                  }
                } // next check
              } // deleteChecks
              
              // if we got this far, the delete will go ahead, so run beforeDeletes now
              
              if (beforeDelete != null) {
                // now that we've got the field values, run each check
                for (DeleteCheck update : beforeDelete) {
                  PreparedStatement sqlUpdate = connection.prepareStatement(update.query);
                  // set parameter values
                  int p = 1;
                  for (String field : update.fields) {
                    sqlUpdate.setString(p++, checkFieldValues.get(field));
                  } // next field
                  try {
                    sqlUpdate.executeUpdate();
                  } catch (SQLException sqlX) {
                    context.servletLog("beforeDelete failed - " + update.query + " : " + sqlX);
                  } finally {
                    sqlUpdate.close();
                  }
                } // next check
              } // deleteChecks
            } // need to check or update before deleting
            
            context.servletLog("DELETE " + pathInfo + " : " + query.toString()); // TODO remove
            PreparedStatement sql = connection.prepareStatement(query.toString());
            try {
              int c = 1;
              for (String value : keyValues) {
                sql.setString(c++, value); 
              } // next key
              
              int rows = sql.executeUpdate();
              if (rows == 0) {
                httpStatus.accept(SC_NOT_FOUND);
                writeResponse(out, failureResult("Record not found: {0}", pathInfo));
              } else {
                writeResponse(out, successResult(null, "Record deleted."));
              }
              
            } finally {
              sql.close();
            }
          } catch(SQLException exception) {
            httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
            writeResponse(out, failureResult(exception));
          }
        } 
      } finally {
        connection.close();
      }
    } catch(SQLException exception) {
      context.servletLog("TableServletBase DELETE: Couldn't connect to database: " + exception);
      contentTypeConsumer.accept("application/json;charset=UTF-8");
      writeResponse(out, failureResult(exception));
    }      
  }
  
  /**
   * Validates a record before INSERTing it.
   * <p> This can be overridden by subclasses. The default implementation returns the
   * result of {@link #validateBeforeUpdate(JsonObjectBuilder,Connection)}.
   * <p> This method may change the record, to standardize or provide default values.
   * @param request The request.
   * @param record The incoming record to validate, to which attributes can be added.
   * @param connection A connection to the database.
   * @return A JSON representation of the valid record, which may or may not be the same
   * object as <var>record</var>.
   * @throws ValidationException If the record is invalid.
   * @see #createMutableCopy(JsonObject)
   */
  protected JsonObject validateBeforeCreate(
    JsonObject record, Connection connection) throws ValidationException {
    return validateBeforeUpdate(record, connection);
  } // end of validateBeforeCreate()
  
  /**
   * Validates a record before UPDATEing it.
   * <p> This can be overridden by subclasses. The default implementation returns null.
   * <p> This method may change the record to standardize or provide default values, by
   * adding attributes to <var>jsonRecord</var>.
   * @param request The request.
   * @param record The incoming record to validate, to which attributes can be added.
   * @param connection A connection to the database.
   * @return A JSON representation of the valid record, which may or may not be the same
   * object as <var>record</var>.
   * @throws ValidationException If the record is invalid.
   * @see #createMutableCopy(JsonObject)
   */
  protected JsonObject validateBeforeUpdate(
    JsonObject record, Connection connection) throws ValidationException {
    return record;
  } // end of validateBeforeUpdate()
  
  /**
   * Allows a JSON record to be added to before it's returned.
   * <p> This method can be overridden by subclasses in order to add further information
   * (e.g. data from dependent tables) to the JSON object returned by the request.
   * @param rs The record.
   * @param jsonOut The object to return in the response, after main columns have been
   * written, and before <var> _canDelete </var> and the object end has been written.
   * @param connection Database connection
   * @throws SQLException If there's an error operating with the database.
   */
  protected void editOutputRecord(ResultSet rs, JsonGenerator jsonOut, Connection connection)
    throws SQLException {
  } // end of editOutputRecord()
  
  /**
   * Allows a new record to be further processed after it's been added to the table.
   * <p> This method can be overridden by subclasses in order to further process a new
   * record (e.g. data can be inserted into dependent tables).
   * <p> The default implementation calls 
   * {@link #editUpdatedRecord(JsonObject,JsonObjectBuilder,Connection)}
   * @param jsonIn The JSON representation of the object received with the request.
   * @param jsonOut The object to return in the response, after main columns have been
   * written, and before <var> _canDelete </var> and the object end has been written.
   * @param connection Database connection
   */
  protected void editNewRecord(
    JsonObject jsonIn, JsonObjectBuilder jsonOut, Connection connection) {
    editUpdatedRecord(jsonIn, jsonOut, connection);
  } // end of editNewRecord()
  
  /**
   * Allows an edited record to be further processed after it's been updated in the table.
   * <p> This method can be overridden by subclasses in order to further process an updated
   * record (e.g. data can be merged into dependent tables).
   * @param jsonIn The JSON representation of the object received with the request.
   * @param jsonOut The object to return in the response, after main columns have been
   * written, and before <var> _canDelete </var> and the object end has been written.
   * @param connection Database connection
   */
  protected void editUpdatedRecord(
    JsonObject jsonIn, JsonObjectBuilder jsonOut, Connection connection) {
  } // end of editUpdatedRecord()
  
} // end of class TableServletBase
