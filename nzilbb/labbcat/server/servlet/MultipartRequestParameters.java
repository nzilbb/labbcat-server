//
// Copyright 2004-2021 New Zealand Institute of Language, Brain and Behaviour, 
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

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import javax.servlet.*;
import javax.servlet.http.*;
import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.disk.*;
import org.apache.commons.fileupload.servlet.*;

/**
 * Utility class for loading details from a multipart (file) upload request.
 * <p> If the request is not multipart, the parameters are still loaded into the
 * Hashtable, but there will be no FileItem values.
 * @author Robert Fromont robert@fromont.net.nz
 */
@SuppressWarnings({"rawtypes","serial"})
public class MultipartRequestParameters extends Hashtable {
  
  /**
   * Constructor from request.
   */
  @SuppressWarnings("unchecked")
  public MultipartRequestParameters(HttpServletRequest request) {
    try {
      ServletFileUpload fileupload = new ServletFileUpload(new DiskFileItemFactory());
      // this ensures that umlauts, etc. don't get ?ified
      fileupload.setHeaderEncoding("UTF-8");
      List items = fileupload.parseRequest(request);
      Iterator it = items.iterator();
      while (it.hasNext()) {
        FileItem item = (FileItem) it.next();
        if (item.isFormField()) {
          // interpret common form fields
          if (!containsKey(item.getFieldName())) {
            put(item.getFieldName(), item.getString());
          } else { // multiple values for same parameter
            // save a vector of strings
            if (get(item.getFieldName()) instanceof String) { // only one value so far
              Vector<String> values = new Vector<String>();
              values.add(getString(item.getFieldName()));
              values.add(item.getString());
              put(item.getFieldName(), values);
            } else { // already have multiple values
              Vector<String> values = (Vector<String>)get(item.getFieldName());
              values.add(item.getString());
            }
          } // multiple values for same parameter
        } else { // it's a file
          if (!containsKey(item.getFieldName())) {
            put(item.getFieldName(), item);
          } else {
            // already got a file with this name - must be multiple files with the name name
            Vector<FileItem> files = null;
            if (get(item.getFieldName()) instanceof Vector) {
              files = (Vector<FileItem>)get(item.getFieldName());
            } else {
              files = new Vector<FileItem>();
              files.add((FileItem)get(item.getFieldName()));
              put(item.getFieldName(), files);
            }
            files.add(item);
          }
        }
      }
    } catch(FileUploadException exception) {
      // not a multipart request, just load regular parameters
      Enumeration enNames = request.getParameterNames();
      while (enNames.hasMoreElements()) {
        String name = (String)enNames.nextElement();
        String[] values = request.getParameterValues(name);
        if (values.length == 1) {
          put(name, values[0]);
        } else {
          put(name, values);
        }
      } // next element 
    }
  } // end of constructor
  
  /**
   * Gets the named item as a string.
   * @param key The parameter name.
   * @return The item as a String.
   */
  public String getString(String key) {
    Object value = get(key);
    if (value != null) {
	 return value.toString();
    } else {
      return null;
    }
  } // end of getString()

  /**
   * Gets all parameters with the given name.
   * @param key The parameter name.
   * @return All parameter String values, or an empty array if there are none.
   */
  @SuppressWarnings("unchecked")
  public String[] getStrings(String key) {
    Object value = get(key);
    if (value != null) {
      if (value instanceof String) {
        String[] values = { value.toString() };
        return values;
      } else {
        return ((Vector<String>)value).toArray(new String[0]);
      }
    } else {
      return new String[0];
    }
  } // end of getString()
  
  /**
   * Gets the named item as a list of files.
   * @param key The parameter name.
   * @return A list of FileItems.
   */
  @SuppressWarnings("unchecked")
  public Vector<FileItem> getFiles(String key) {
    Object value = get(key);
    if (value != null) {
      if (value instanceof Vector) {
        return (Vector<FileItem>)value;
      } else {
        Vector<FileItem> files = new Vector<FileItem>();
        files.add((FileItem)value);
        return files;
      }
    } else {
      return new Vector<FileItem>();
    } 
  } // end of getFiles()
  
} // end of class MultipartRequestParameters
