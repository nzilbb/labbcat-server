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
package nzilbb.labbcat.server.db;

import nzilbb.ag.StoreException;

/**
 * Exception thrown when trying to create a record that already exists.
 * @author Robert Fromont robert@fromont.net.nz
 */
@SuppressWarnings("serial")
public class ExistingIdException extends StoreException {
   
   /**
    * The ID that cause the exception.
    * @see #getId()
    * @see #setId(String)
    */
   protected String id;
   /**
    * Getter for {@link #id}: The ID that cause the exception.
    * @return The ID that cause the exception.
    */
   public String getId() { return id; }
   /**
    * Setter for {@link #id}: The ID that cause the exception.
    * @param newId The ID that cause the exception.
    */
   public ExistingIdException setId(String newId) { id = newId; return this; }

   public ExistingIdException(String id) {
      super("Already exists: " + id);
      this.id = id;
   }
}
