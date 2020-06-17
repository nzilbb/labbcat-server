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


import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Types;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Vector;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.json.*;

/**
 * Base class that handles generic database table management.
 * <p> Subclasses specify the table name, key fields, and fields.
 * @author Robert Fromont robert@fromont.net.nz
 */
public class TableServletBase extends LabbcatServlet {

   /** The name of the data table */
   protected String table;

   /** An ordered list of key field names */
   protected List<String> keys;

   /** An ordered list of non-key fields */
   protected List<String> columns;

   /** An ordered list of non-key fields for full-listing requests */
   protected List<String> listColumns;

   /** WHERE condition */
   protected String whereClause;

   /** ORDER clause */
   protected String orderClause;

   /** Whether Create operations are allowed via POST */
   protected boolean create = false;

   /** Whether Read operations are allowed via GET */
   protected boolean read = false;

   /** Whether Update operations are allowed via PUT */
   protected boolean update = false;

   /** Whether Delete operations are allowed via DELETE */
   protected boolean delete = false;

   /** Key that is automatically generated when records are created */
   protected String autoKey = null;

   /** Query to generate {@link #autoKey} when records are created (null if the database
    * generates the new key itself) */
   protected String autoKeyQuery = null;

   /** A query used to check each row to determine if it can be deleted.
    * <var>deleteQuery</var> should be a query that
    * <ul>
    *  <li> Accepts all the specified fields as parameters </li>
    *  <li> Returns a single numeric column, which is 0 when the row can be deleted. </li>
    * </ul>
    * e.g. <code>
    * new DeleteCheck("SELECT COUNT(*) FROM transcript WHERE corpus_name = ?",
    *                 "Could not delete - transcripts exist for this corpus.")
    * </code>
    */
   class DeleteCheck {
      String query;
      List<String> fields;
      String error;
      public DeleteCheck(String query, List<String> fields, String error) {
         this.query = query;
         this.fields = fields;
         this.error = error;
      }
   }

   /** An ordered list of key field names */
   protected List<DeleteCheck> deleteChecks;

   /** 
    * Constructor from attributes.
    * @param table The name of the data table.
    * @param keys An ordered list of key field names.
    * @param columns An ordered list of non-key fields, for single-item and full-list requests.
    * @param whereClause WHERE condition.
    * @param orderClause ORDER clause.
    * @param create Whether Create operations are allowed via POST.
    * @param read Whether Read operations are allowed via GET.
    * @param update Whether Update operations are allowed via PUT.
    * @param delete Whether Delete operations are allowed via DELETE.
    */
   protected TableServletBase(
      String table, List<String> keys, List<String> columns,
      String whereClause, String orderClause,
      boolean create, boolean read, boolean update, boolean delete) {      
      this.table = table;
      this.keys = keys;
      this.columns = columns;
      this.listColumns = columns;
      this.whereClause = whereClause;
      this.orderClause = orderClause;
      this.create = create;
      this.read = read;
      this.update = update;
      this.delete = delete;
   }
   
   /** 
    * Constructor from attributes.
    * @param table The name of the data table.
    * @param keys An ordered list of key field names.
    * @param columns An ordered list of non-key fields.
    * @param whereClause WHERE condition.
    * @param orderClause ORDER clause.
    * @param create Whether Create operations are allowed via POST.
    * @param read Whether Read operations are allowed via GET.
    * @param update Whether Update operations are allowed via PUT.
    * @param delete Whether Delete operations are allowed via DELETE.
    */
   protected TableServletBase(
      String table, List<String> keys, List<String> columns, List<String> listColumns,
      String whereClause, String orderClause,
      boolean create, boolean read, boolean update, boolean delete) {      
      this.table = table;
      this.keys = keys;
      this.columns = columns;
      this.listColumns = listColumns;
      this.whereClause = whereClause;
      this.orderClause = orderClause;
      this.create = create;
      this.read = read;
      this.update = update;
      this.delete = delete;
   }
   
   /**
    * GET handler lists all rows. 
    * <p> The return is JSON encoded, unless the "Accept" request header, or the "Accept"
    * request parameter, is "text/csv", in which case CSV is returned.
    */
   @Override
   protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
      try {
         Connection connection = newConnection();
         try {
            if (!hasAccess(request, response, connection)) {
               return;
            } else if (!read) {
               response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            } else {
               // servlet returns JSON by default, but can be asked to return CSV
               boolean csv = request.getHeader("Accept").indexOf("text/csv") >= 0
                  || "text/csv".equals(request.getParameter("Accept"));
               if (csv) {
                  response.setContentType("text/csv");
               } else {
                  response.setContentType("application/json");
               }
               
               response.setCharacterEncoding("UTF-8");
               try {
                  String[] keyValues = null;
                  if (request.getPathInfo() != null && !request.getPathInfo().equals("/")) {
                     keyValues = request.getPathInfo().substring(1).split("/");
                  }
                  boolean partialKey = keyValues != null && keyValues.length < keys.size();
                  if (csv) {
                     String name = table;
                     if (keyValues != null) {
                        for (String value : keyValues) name += "-"+value;
                     }
                     response.setHeader("Content-Disposition", "attachment; filename=" + name + ".csv");
                  }
                  
                  // return a list of rows
                  StringBuilder query = new StringBuilder();
                  Vector<String> allColumns = new Vector<String>(keys);
                  if (keyValues != null) {
                     allColumns.addAll(columns);
                  } else { // full list
                     allColumns.addAll(listColumns);
                  }
                  for (String column : allColumns) {
                     if (query.length() == 0) {
                        query.append("SELECT ");
                     } else {
                        query.append(", ");
                     }
                     query.append(column);
                  } // next columsn
                  query.append(" FROM ");
                  query.append(table);
                  StringBuffer where = new StringBuffer(); 
                  if (whereClause != null && whereClause.length() > 0) {
                     where.append(whereClause);
                  }
                  // or only one row, if there's a path
                  if (keyValues != null) {
                     int k = 0;
                     for (String column : keys) {
                        
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
                  
                  if (request.getParameter("p") != null) { // page
                     try {
                        int page = Integer.parseInt(request.getParameter("p"));
                        int pageLength = 20;
                        if (request.getParameter("l") != null) {
                           pageLength = Integer.parseInt(request.getParameter("l"));
                        }
                        int offset = page * pageLength;
                        query.append(" LIMIT " + offset + ", " + pageLength);
                     } catch (Exception x) {
                        log("ERROR cannot paginate: " + x);
                     }
                  } // page
                  
                  log("GET " + request.getPathInfo() + " : " + query.toString()); // TODO remove
                  PreparedStatement sql = connection.prepareStatement(query.toString());
                  if (keyValues != null) {
                     int c = 1;
                     for (String value : keyValues) {
                        sql.setString(c++, value);
                     } // next key
                  } // key values specified in path
                  ResultSet rs = sql.executeQuery();
                  CSVPrinter csvOut = csv
                     ?new CSVPrinter(response.getWriter(), CSVFormat.EXCEL.withDelimiter(','))
                     :null;
                  JSONWriter jsonOut = csv
                     ?null:
                     new JSONWriter(response.getWriter());
                  if (jsonOut != null && (keyValues == null || partialKey)) {
                     jsonOut.array(); // all rows, start an array
                  }
                  int rowCount = 0;
                  try {
                     boolean headersWritten = false;
                     while (rs.next()) {
                        ResultSetMetaData meta = rs.getMetaData();
                        outputRow(rs, allColumns, meta, jsonOut, connection);
                        headersWritten = outputRow(rs, allColumns, meta, csvOut, headersWritten);
                        rowCount++;
                     } // next row
                     if (rowCount == 0 && keyValues != null && !partialKey) {
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        // JsonWriter hates to write nothing, so give it an empty object
                        if (jsonOut != null) jsonOut.object().endObject();
                     }
                  } finally {
                     if (jsonOut != null) {
                        if (keyValues == null || partialKey) {
                           jsonOut.endArray(); // all rows, finish array
                        }
                     }
                     if (csvOut != null) {
                        csvOut.close();
                     }
                     
                     rs.close();
                     sql.close();
                  }
               } catch(SQLException exception) {
                  response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                  log("TableServletBase GET: ERROR: " + exception);
                  response.setContentType("application/json");
                  failureResult("ERROR: " + exception.getMessage())
                     .write(response.getWriter());
               }
            }
         } finally {
            connection.close();
         }
      } catch(SQLException exception) {
         log("TableServletBase GET: Couldn't connect to database: " + exception);
         response.setContentType("application/json");
         response.setCharacterEncoding("UTF-8");
         failureResult("ERROR: " + exception.getMessage())
            .write(response.getWriter());
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
   protected boolean outputRow(ResultSet rs, List<String> allColumns, ResultSetMetaData meta, JSONWriter jsonOut, Connection connection) throws SQLException {
      if (jsonOut == null) return false;
      jsonOut.object();
      int c = 1;
      try {
         for (String column: allColumns) {
            jsonOut.key(column);
            String value = rs.getString(column);
            if (value == null) {
               jsonOut.value(null);
            } else {
               try {
                  switch(meta.getColumnType(c++)) { // get the type right
                     case Types.BIGINT:   jsonOut.value(rs.getLong(column)); break;
                     case Types.BIT:      jsonOut.value(rs.getInt(column) != 0); break;
                     case Types.BOOLEAN:  jsonOut.value(rs.getBoolean(column)); break;
                     case Types.DECIMAL:  jsonOut.value(rs.getDouble(column)); break;
                     case Types.DOUBLE:   jsonOut.value(rs.getDouble(column)); break;
                     case Types.FLOAT:    jsonOut.value(rs.getDouble(column)); break;
                     case Types.NUMERIC:  jsonOut.value(rs.getDouble(column)); break;
                     case Types.INTEGER:  jsonOut.value(rs.getInt(column)); break;
                     case Types.SMALLINT: jsonOut.value(rs.getInt(column)); break;
                     case Types.TINYINT:  jsonOut.value(rs.getInt(column)); break;
                     case Types.NULL:     jsonOut.value(null); break;
                     default:             jsonOut.value(value); break;
                  }
               } catch (SQLDataException x) { // can't determine the type?
                  jsonOut.value(value);
               }                           
            } // no null
         } // next column
         boolean canDelete = true;
         if (deleteChecks != null) { // need to run delete checks
            for (DeleteCheck check : deleteChecks) {
               // TODO prepare delete checks once instead of once for every row
               PreparedStatement sqlCheck = connection.prepareStatement(check.query);
               // set parameter values
               int p = 1;
               for (String field : check.fields) {
                  sqlCheck.setString(p++, rs.getString(field));
               } // next field
               ResultSet rsCheck = sqlCheck.executeQuery();
               try {
                  if (rsCheck.next() && rsCheck.getLong(1) != 0) {
                     canDelete = false;
                  }
               } finally {
                  rsCheck.close();
                  sqlCheck.close();
               }
            } // next check
         } // need to run delete checks
         jsonOut.key("_canDelete").value(canDelete);
      } finally {
         jsonOut.endObject();
      }
      return true;
   } // end of outputRow()
   
   /**
    * Outputs a single row as JSON.
    * @param rs
    * @param csvOut
    * @throws SQLException
    */
   protected boolean outputRow(ResultSet rs, List<String> allColumns, ResultSetMetaData meta, CSVPrinter csvOut, boolean headersWritten)
      throws SQLException, IOException {
      if (csvOut == null) return false;
      if (!headersWritten) { // write a line of header TODO
         for (String column: allColumns) {
            csvOut.print(column);
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
    * POST handler - add a new row.
    */
   @Override
   protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
      try {
         Connection connection = newConnection();
         try {
            if (!hasAccess(request, response, connection)) {
               return;
            } else if (!create) {
               response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            } else {
               response.setContentType("application/json");
               response.setCharacterEncoding("UTF-8");

               StringBuilder query = new StringBuilder();
               StringBuilder parameters = new StringBuilder();
               Vector<String> allColumns = new Vector<String>(keys);
               allColumns.addAll(columns);
               for (String column : allColumns) {
                  log("POST col " + column);
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
         
               // read the incoming object
               JSONTokener reader = new JSONTokener(request.getReader());
               JSONObject json = new JSONObject(reader);
               StringBuffer key = new StringBuffer();
               try {
            
                  // insert the row
                  log("POST " + query.toString()); // TODO remove
                  PreparedStatement sql = connection.prepareStatement(query.toString());
                  int c = 1;
                  for (String column : keys) {
               
                     // skip auto-generated key
                     if (column.equals(autoKey)) {
                        if (autoKeyQuery == null) {
                           // the database will generate the key
                           continue;
                        } else {
                           // run the query to get the key value
                           PreparedStatement sqlAutoKey
                              = connection.prepareStatement(autoKeyQuery);
                           ResultSet rsAutoKey = sqlAutoKey.executeQuery();
                           try {
                              if (rsAutoKey.next()) {
                                 // generate the value
                                 String value = rsAutoKey.getString(0);
                                 // put it into the INSERT statement
                                 sql.setString(c++, value);
                                 // add it to the key (for error reporting)
                                 key.append("/");
                                 key.append(value);
                                 // add it to the JSON object, for returning to the caller
                                 json.put(column, value);
                              }
                           } finally {
                              rsAutoKey.close();
                              sqlAutoKey.close();
                           }
                        }
                     } else {
                        String value = json.getString(column);
                        sql.setString(c++, value); 
                        key.append("/");
                        key.append(value);
                     }
                  } // next key
                  for (String column : columns) {
                     String value = json.getString(column);
                     sql.setString(c++, value);
                  } // next column
                  try {
                     int rows = sql.executeUpdate();
                     if (rows == 0) {
                        response.setStatus(HttpServletResponse.SC_CONFLICT);
                        failureResult("Record not added: " + key)
                           .write(response.getWriter());
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
                              json.put(autoKey, rsLastId.getLong(1));
                           } finally {
                              rsLastId.close();
                              sqlLastId.close();
                           }
                        }
                  
                        // record added, so return it
                        successResult(json, "Record added.")
                           .write(response.getWriter());
                     }
                  } finally {
                     sql.close();
                     connection.close();
                  }
               } catch(SQLIntegrityConstraintViolationException exception) {
                  // row is already there
                  response.setStatus(HttpServletResponse.SC_CONFLICT);
                  failureResult("Row already exists: " + key)
                     .write(response.getWriter());
               } catch(SQLException exception) {
                  response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                  log("TableServletBase POST: ERROR: " + exception);
                  failureResult("ERROR: " + exception.getMessage())
                     .write(response.getWriter());
               }
            } 
         } finally {
            connection.close();
         }
      } catch(SQLException exception) {
         log("TableServletBase GET: Couldn't connect to database: " + exception);
         response.setContentType("application/json");
         response.setCharacterEncoding("UTF-8");
         failureResult("ERROR: " + exception.getMessage())
            .write(response.getWriter());
      }      
   }

   /**
    * PUT handler - update an existing row.
    */
   @Override
   protected void doPut(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
      try {
         Connection connection = newConnection();
         try {
            if (!hasAccess(request, response, connection)) {
               return;
            } else if (!update) {
               response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            } else {
               response.setContentType("application/json");
               response.setCharacterEncoding("UTF-8");

               // prepare the UPDATE
               StringBuilder query = new StringBuilder();
               Vector<String> allColumns = new Vector<String>(keys);
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
               for (String column : keys) {
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
                  log("PUT " + query.toString()); // TODO remove
                  PreparedStatement sql = connection.prepareStatement(query.toString());

                  try {
                     // read the incoming object
                     JSONTokener reader = new JSONTokener(request.getReader());
                     JSONObject json = new JSONObject(reader);
                     int c = 1;
                     StringBuffer key = new StringBuffer();
                     for (String column : columns) {
                        String value = json.getString(column);
                        sql.setString(c++, value);
                     } // next column
                     for (String column : keys) {
                        String value = json.getString(column);
                        if (key.length() > 0) key.append("/");
                        key.append(value);
                        sql.setString(c++, value); 
                     } // next key
               
                     int rows = sql.executeUpdate();
                     if (rows == 0) {
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        failureResult("Record not found: " + key)
                           .write(response.getWriter());
                     } else {
                  
                        // record update, so return it
                        successResult(json, "Record updated.")
                           .write(response.getWriter());
                     }
                  } finally {
                     sql.close();
                     connection.close();
                  }
               } catch(SQLException exception) {
                  log("TableServletBase POST: ERROR: " + exception);
                  response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                  failureResult("ERROR: " + exception.getMessage())
                     .write(response.getWriter());
               }
            } 
         } finally {
            connection.close();
         }
      } catch(SQLException exception) {
         log("TableServletBase GET: Couldn't connect to database: " + exception);
         response.setContentType("application/json");
         response.setCharacterEncoding("UTF-8");
         failureResult("ERROR: " + exception.getMessage())
            .write(response.getWriter());
      }      
   }

   /**
    * DELETE handler - remove existing row.
    */
   @Override
   protected void doDelete(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
      try {
         Connection connection = newConnection();
         try {
            if (!hasAccess(request, response, connection)) {
               return;
            } else if (!delete) {
               response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            } else {
               response.setContentType("application/json");
               response.setCharacterEncoding("UTF-8");
               try {
                  String[] keyValues = null;
                  if (request.getPathInfo() != null && !request.getPathInfo().equals("/")) {
                     keyValues = request.getPathInfo().substring(1).split("/");
                     // only accept a path if all keys have a value
                     if (keyValues.length != keys.size()) keyValues = null;
                  }
                  if (keyValues == null) {
                     response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                     failureResult("Key values not found in path: " + request.getPathInfo())
                        .write(response.getWriter());
                     return;
                  }

                  StringBuilder query = new StringBuilder();
                  StringBuilder parameters = new StringBuilder();
                  query.append("DELETE FROM ");
                  query.append(table);
                  StringBuffer where = new StringBuffer(); 
                  if (whereClause != null && whereClause.length() > 0) {
                     where.append(whereClause);
                  }
                  for (String column : keys) {
                     if (where.length() > 0) {
                        where.append(" AND ");
                     }
                     where.append(column);
                     where.append(" = ?");
                  } // next key
                  query.append(" WHERE ");
                  query.append(where);

                  if (deleteChecks != null) {
                     // get all the field values we'll need
                     HashMap<String,String> checkFieldValues = new HashMap<String,String>();
                     HashSet<String> missingFields = new HashSet<String>();
                     boolean needNonKeyValues = false;
                     for (DeleteCheck check : deleteChecks) {
                        for (String field : check.fields) {
                           checkFieldValues.put(field, null);
                           if (!keys.contains(field)) needNonKeyValues = true;
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
                              response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                              failureResult("Could not query for delete-check values")
                                 .write(response.getWriter());
                              return;
                           } else {
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
                        for (String column : keys) {
                           checkFieldValues.put(column, keyValues[k++]);
                        } // next key
                     }
                     
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
                              response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                              failureResult(check.error)
                                 .write(response.getWriter());
                              return;
                           }
                        } finally {
                           rsCheck.close();
                           sqlCheck.close();
                        }
                     } // next check
                  } // need to check before deleting

                  log("DELETE " + request.getPathInfo() + " : " + query.toString()); // TODO remove
                  PreparedStatement sql = connection.prepareStatement(query.toString());
                  try {
                     int c = 1;
                     for (String value : keyValues) {
                        sql.setString(c++, value); 
                     } // next key

                     int rows = sql.executeUpdate();
                     if (rows == 0) {
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        failureResult("Record doesn't exist: " + request.getPathInfo())
                           .write(response.getWriter());
                     } 
               
                  } finally {
                     sql.close();
                  }
               } catch(SQLException exception) {
                  response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                  failureResult("ERROR: " + exception.getMessage())
                     .write(response.getWriter());
               }
            } 
         } finally {
            connection.close();
         }
      } catch(SQLException exception) {
         log("TableServletBase GET: Couldn't connect to database: " + exception);
         response.setContentType("application/json");
         response.setCharacterEncoding("UTF-8");
         failureResult("ERROR: " + exception.getMessage())
            .write(response.getWriter());
      }      
   }

   private static final long serialVersionUID = 1;
} // end of class TableServletBase
