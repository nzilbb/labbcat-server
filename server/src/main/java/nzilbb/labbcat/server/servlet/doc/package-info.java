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
/**
 * Provides CRUD operations for all files/directories under /doc/*.
 * <p> This allows for arbitrary documentation of a corpus, using wysiwiki, which allows
 * admin users to create, edit, and delete pages, providing read-only access to others.
 * <p> URLs are assumed to identify a file with a ".html" suffix. 
 * e.g. GETting <q>http://tld/doc/foo/bar</q> with return the contents of the 
 * <q>docs/foo/bar.html</q> file. PUTting to the same URL updates the contents of that file,
 * and DELETEing deletes the file.
 * <p> For files that don't exist, 404 is returned, but also the body of a <q>template.html</q> 
 * file if the user can edit. This way, the editing user can use the template as a starting point, 
 * and PUT the body, with their changes, to create the file.
 * <p> GETting <q>http://tld/doc/index</q> returns an HTML document that represents the whole
 * tree of documents and subdirectories, with corresponding &lt;a&gt; links.
 * <p> POSTting a file to any URL results in that file being written  
 * - i.e. POSTing to the file <q>http://tld/doc/foo/bar.png</q> will 
 * result in the creation of a file called <q>http://tld/doc/foo/bar.png</q>, and a relative 
 * URL to it is returned as part of the JSON-encoded response to the POST request.
 * @author Robert Fromont robert@fromont.net.nz
 */
package nzilbb.labbcat.server.servlet.doc;
