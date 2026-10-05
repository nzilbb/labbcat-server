//
// Copyright 2019-2025 New Zealand Institute of Language, Brain and Behaviour, 
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

import java.io.File;
import java.io.Closeable;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

/**
 * A map of request parameter names to their values. Each name can correspond to multiple
 * values, and values can be either Strings or Files. 
 * @author Robert Fromont robert@fromont.net.nz
 */
public class RequestParameters extends HashMap<String,Object> implements Closeable {
  /**
   * Default constructor.
   */
  public RequestParameters() {
  } // end of constructor
  
  /**
   * Gets the named item as a string.
   * @param key The parameter name.
   * @return The item as a String.
   */
  public String getString(String key) {
    Object value = get(key);
    if (value != null) {
      if (value instanceof String[]) {
        String[] values = (String[])value;
        return values[0];
      } else {
        return value.toString();
      }
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
      } else if (value instanceof String[]) {
        return (String[])value;
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
  public File getFile(String key) {
    Object value = get(key);
    if (value != null) {
      if (value instanceof Vector) {
        value = ((Vector)value).elementAt(0);
      }
      if (value instanceof File) {
        return (File)value;
      }
    }
    return null;
  } // end of getFiles()
  
  /**
   * Gets the named item as a list of files.
   * @param key The parameter name.
   * @return A list of FileItems, which may be empty.
   */
  @SuppressWarnings("unchecked")
  public Vector<File> getFiles(String key) {
    Object value = get(key);
    if (value != null) {
      if (value instanceof Vector) {
        if (((Vector)value).size() > 0 && ((Vector)value).elementAt(0) instanceof File) {
          return (Vector<File>)value;
        }
      } else {
        Vector<File> files = new Vector<File>();
        if (value instanceof File) {
          files.add((File)value);
        }
        return files;
      }
    }
    return new Vector<File>();
  } // end of getFiles()
  
  /**
   * Obtains all files in the parameters.
   * @return All the files uploaded with the parameters.
   */
  @SuppressWarnings("unchecked")
  public List<File> getAllFiles() {
    Vector<File> allFiles = new Vector<File>();
    for (Object value : values()) {
      if (value instanceof File) {
        allFiles.add((File)value);
      } else if (value instanceof Vector) {
        Vector values = (Vector)value;
        if (values.size() > 0) {
          if (values.elementAt(0) instanceof File) {
            for (Object file : values) {
              allFiles.add((File)file);
            }
          }
        }
      }
    } // next value
    return allFiles;
  } // end of getAllFiles()
  
  /**
   * Deletes all temporary files that were created while reading file parameters.
   */
  public void close() {
    for (File file : getAllFiles()) {      
      if (file.exists()) {
        if (!file.delete()) {
          System.err.println("Could not delete file from request: " + file.getPath());
        }
      } // file still exists
    } // next file
  } // end of close()
  
} // end of class RequestParameters
