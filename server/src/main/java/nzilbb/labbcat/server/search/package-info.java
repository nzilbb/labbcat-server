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
 * Classes that manage definition, serializations, and deserialization of search matrices.
 *
 * <p> A search matrix is generally encoded as a JSON object, with the following structure:
 * <dl>
 *   <dt>columns</dt>
 *   <dd> An array of JSON objects, each representing a column (a word token) in the
 *        search matrix UI. Each object as the following structure:
 *        <dl>
 *          <dt>layers</dt>
 *          <dd> A JSON object where keys are layer IDs, and values are either a JSON
 *               object, or an array of JSON objects, with the following structure:
 *                <dl>
 *                 <dt>pattern</dt> <dd>A regular expression to match the label.</dd>
 *                 <dt>not</dt> <dd>Whether pattern matching is negated or not.</dd>
 *                 <dt>min</dt> <dd>Inclusive numeric minimum value for the label.</dd>
 *                 <dt>max</dt> <dd>Exclusive numeric maximum value for the label.</dd>
 *                 <dt>anchorStart</dt> <dd>Whether this annotation starts with the word.</dd>
 *                 <dt>anchorEnd</dt> <dd>Whether this annotation ends with the word.</dd>
 *                 <dt>target</dt> <dd>Whether this is the target annotation of the search.</dd>
 *                </dl>
 *          </dd>
 *          <dt>adj</dt>
 *          <dd> How many word tokens away from this token the token that matches the next
 *               column can be. </dd>
 *        </dl>
 *   </dd>
 *
 *   <dt>participantQuery</dt>
 *   <dd>An optional query to identify participants whose utterances should be searched.</dd>
 *
 *   <dt>transcriptQuery</dt>
 *   <dd>An optional to identify transcripts whose utterances should be searched.</dd>
 * </dl>
 *
 * <p> Any match condition in a column can contain no conditions - i.e. no pattern, min,
 * or max. In this case, the condition is ignored.
 *
 * <p> e.g. the following JSON-encoded search matrix identifies all tokens of the word
 * "the" at the beginning of a topix, followed withing three words by a token that starts
 * with a vowel in the pronunciation: 
 * <pre>{
 *   "columns":[
 *     {
 *       "layers":{
 *         "orthography":{
 *           "id":"orthography",
 *           "not":false,
 *           "pattern":"and",
 *           "target":true},
 *         "phonemes":{
 *           "id":"phonemes",
 *           "target":false},
 *         "topic":{
 *           "id":"topic",
 *           "target":false,
 *           "anchorStart":true}
 *       },
 *       "adj":3
 *     },
 *     {
 *       "layers":{
 *         "orthography":{
 *           "id":"orthography",
 *           "target":false},
 *         "phonemes":{
 *           "id":"phonemes",
 *           "target":false,
 *           "pattern":"[aeiou].*"},
 *         "topic":{
 *           "id":"topic",
 *           "target":false
 *         }
 *       }
 *     }]
 * }</pre>
 *
 * <p> If a layer in the 'layers' object contains an array of object with multiple
 * elements, then the layer expression is assumed to match multiple contiguous sub-word
 * annotations. This allows matching of segments within context.
 *
 * <p> e.g. the following matrix identifies tokens where the spelling starts with "k", and
 * segments start with /n/ followed by a vowel, which is the target:
 *
 * <pre>{
 *   "columns":[
 *     {
 *       "layers":{
 *         "orthography":{
 *           "id":"orthography",
 *           "pattern":"k.*"},
 *         "segment":[
 *           {
 *             "pattern":"n",
 *           },{
 *             "pattern":"[aeiou]",
 *             "target":true
 *           }]
 *       }
 *     }]
 * }</pre>
 *
 */
package nzilbb.labbcat.server.search;
