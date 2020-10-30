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
/**
 * <p> Endpoints providing an HTTP-based API for access to transcripts, annotations,
 * media, and functions that manipulate them.
 * <p> All LaBB-CAT requests for which the the <q>Accept</q> HTTP header is set to
 *  <q>application/json</q> return a JSON response with the same standard envelope structure:
 * <dl>
 *  <dt>title</dt> <dd>(string) The title of the LaBB-CAT instance.</dd>
 *  <dt>version</dt> <dd>(string) The version of the LaBB-CAT instance</dd>
 *  <dt>code</dt> <dd>(int) 0 if the request was successful, 1 if there was a problem</dd>
 *  <dt>messages</dt> <dd>An array of message strings.</dd>
 *  <dt>errors</dt> <dd>An array of error message strings.</dd>
 *  <dt>model</dt> <dd>The result of the request, which may be a JSON object, JSON array,
 *   or a simple type.</dd>
 * </dl>
 * <p> e.g. the response to 
 * <tt>http://localhost:8080/labbcat/api/store/getLayer?id=transcript</tt>
 *  might be:
 * <pre>{
 *    "title":"LaBB-CAT",
 *    "version":"20200129.1901",
 *    "code":0,
 *    "errors":[],
 *    "messages":[],
 *    "model":{
 *        "id":"transcript",
 *        "parentId":"turns",
 *        "description":"Original transcription",
 *        "alignment":2,
 *        "peers":true,
 *        "peersOverlap":false,
 *        "parentIncludes":true,
 *        "saturated":false,
 *        "type":"string",
 *        "validLabels":{},
 *        "category":null
 *    }
 *}</pre>
 * <p> If the <q>Accept-Language</q> request header is set, the server will endeavor to
 * localize messages to the specified language.
 * <p> Annotation Graph store functions include:
 * <ul>
 *  <li> {@link StoreQuery GraphStoreQuery} functions for querying transcripts/annotations. </li>
 *  <li> {@link Store GraphStore} functions for editing transcripts/annotations </li>
 *  <li> {@link StoreAdministration GraphStoreAdministration} functions for defining the
 * data schema, annotation automation, etc. </li>
 *  <li> and other LaBB-CAT-specific functions which listed below &hellip;</li>
 * </ul>
 */
package nzilbb.labbcat.server.servlet;
