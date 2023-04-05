//
// Copyright 2020-2023 New Zealand Institute of Language, Brain and Behaviour, 
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Vector;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.*;
import javax.xml.xpath.*;
import nzilbb.ag.*;
import nzilbb.ag.automation.util.AnnotatorDescriptor;
import nzilbb.ag.serialize.SerializationDescriptor;
import nzilbb.ag.serialize.SerializationException;
import nzilbb.ag.serialize.SerializerNotConfiguredException;
import nzilbb.ag.serialize.json.JSONSerialization;
import nzilbb.ag.serialize.util.NamedStream;
import nzilbb.ag.serialize.util.Utility;
import nzilbb.configure.ParameterSet;
import nzilbb.labbcat.server.db.*;
import nzilbb.util.IO;
import org.w3c.dom.*;
import org.xml.sax.*;

/**
 * Endpoints starting <tt>/api/store/&hellip;</tt> provide an HTTP-based API for access to
 * <a href="https://nzilbb.github.io/ag/apidocs/nzilbb/ag/GraphStoreQuery.html">GraphStoreQuery</a> 
 * functions.
 * <p> These endpoints all work for <b>GET</b> HTTP requests and return a JSON response with the same standard envelope structure:
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
 * <tt>http://localhost:8080/labbcat/api/store/getLayer?id=word</tt>
 *  might be:
 * <pre>{
 *    "title":"LaBB-CAT",
 *    "version":"20200129.1901",
 *    "code":0,
 *    "errors":[],
 *    "messages":[],
 *    "model":{
 *        "id":"word",
 *        "parentId":"turns",
 *        "description":"Word tokens",
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
 *
 * <p> User authorization for password-protected instances of LaBB-CAT uses the 'Basic'
 * HTTP authentication scheme. This involves sending an <q>Authorization</q> request
 * header of the form <tt>Basic <var>TTTTTTTT</var></tt>, where <var>TTTTTTTT</var> is an
 * authentication token formed by base64-encoding a string of the form
 * <tt><var>username</var>:<var>password</var></tt>
 *
      <a id="getId()">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/getId</h4>
          <div class="block">Gets the store's ID.</div>
          <dl>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>The annotation store's ID.</dd>
          </dl>
        </li>
      </ul>
      <a id="getLayerIds()">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/getLayerIds</h4>
          <div class="block">Gets a list of layer IDs (annotation 'types').</div>
          <dl>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>A list of layer IDs.</dd>
          </dl>
        </li>
      </ul>
      <a id="getLayers()">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/getLayers</h4>
          <div class="block">Gets a list of layer definitions.</div>
          <dl>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>A list of layer definitions.</dd>
          </dl>
        </li>
      </ul>
      <a id="getSchema()">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/getSchema</h4>
          <div class="block">Gets the layer schema.</div>
          <dl>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>A schema defining the layers and how they relate to each other.</dd>
          </dl>
        </li>
      </ul>
      <a id="getLayer(java.lang.String)">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/getLayer</h4>
          <div class="block">Gets a layer definition.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>id</code> - ID of the layer to get the definition for.</dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>The definition of the given layer.</dd>
          </dl>
        </li>
      </ul>
      <a id="getCorpusIds()">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/getCorpusIds</h4>
          <div class="block">Gets a list of corpus IDs.</div>
          <dl>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>A list of corpus IDs.</dd>
          </dl>
        </li>
      </ul>
      <a id="getParticipantIds()">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/getParticipantIds</h4>
          <div class="block">Gets a list of participant IDs.</div>
          <dl>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>A list of participant IDs.</dd>
          </dl>
        </li>
      </ul>
      <a id="getParticipant(java.lang.String,java.lang.String[])">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/getParticipant</h4>
          <div class="block">Gets the participant record specified by the given identifier.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>id</code> - The ID of the participant, which could be their name or their database annotation
              ID.</dd>
            <dd><code>layerIds</code> - The IDs of the participant attribute layers to load, passed by specifying the <code>layerIds</code> multiple times, once for each layer, or absent if only participant data is required.</dd></dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>An annotation representing the participant, or null if the participant was not found.</dd>
          </dl>
        </li>
      </ul>
      <a id="countMatchingParticipantIds(java.lang.String)">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/countMatchingParticipantIds</h4>
          <div class="block">Counts the number of participants that match a particular pattern.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>expression</code> - An expression that determines which participants match.
              <p> The expression language is currently not well defined, but expressions such as the
                following can be used: 
                <ul>
                  <li><code>id MATCHES 'Ada.+'</code></li>
                  <li><code>'CC' IN labels('corpus')</code></li>
                  <li><code>'en' IN labels('participant_languages')</code></li>
                  <li><code>'en' IN labels('transcript_language')</code></li>
                  <li><code>id NOT MATCHES 'Ada.+' AND first('corpus').label = 'CC'</code></li>
                  <li><code>all('transcript_rating').length &gt; 2</code></li>
                  <li><code>all('participant_rating').length = 0</code></li>
                  <li><code>'labbcat' NOT IN annotators('transcript_rating')</code></li>
                  <li><code>first('participant_gender').label = 'NA'</code></li>
            </ul></dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>The number of matching participants.</dd>
          </dl>
        </li>
      </ul>
      <a id="getMatchingParticipantIds(java.lang.String,java.lang.Integer,java.lang.Integer)">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/getMatchingParticipantIds</h4>
          <div class="block">Gets a list of IDs of participants that match a particular pattern.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>expression</code> - An expression that determines which participants match.
              <p> The expression language is currently not well defined, but expressions such as the
                following can be used: 
                <ul>
                  <li><code>id MATCHES 'Ada.+'</code></li>
                  <li><code>'CC' IN labels('corpus')</code></li>
                  <li><code>'en' IN labels('participant_languages')</code></li>
                  <li><code>'en' IN labels('transcript_language')</code></li>
                  <li><code>id NOT MATCHES 'Ada.+' AND first('corpus').label = 'CC'</code></li>
                  <li><code>all('transcript_rating').length &gt; 2</code></li>
                  <li><code>all('participant_rating').length = 0</code></li>
                  <li><code>'labbcat' NOT IN annotators('transcript_rating')</code></li>
                  <li><code>first('participant_gender').label = 'NA'</code></li>
            </ul></dd>
            <dd><code>pageLength</code> (Optional) - The maximum number of IDs to return, or absent to return all.</dd>
            <dd><code>pageNumber</code> (Optional) - The zero-based page number to return, or absent to return the first page.</dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>A list of participant IDs.</dd>
          </dl>
        </li>
      </ul>
      <a id="getTranscriptIds()">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/getTranscriptIds</h4>
          <div class="block">Gets a list of transcript IDs.</div>
          <dl>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>A list of transcript IDs.</dd>
          </dl>
        </li>
      </ul>
      <a id="getTranscriptIdsInCorpus(java.lang.String)">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/getTranscriptIdsInCorpus</h4>
          <div class="block">Gets a list of transcript IDs in the given corpus.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>id</code> - A corpus ID.</dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>A list of transcript IDs.</dd>
          </dl>
        </li>
      </ul>
      <a id="getTranscriptIdsWithParticipant(java.lang.String)">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/getTranscriptIdsWithParticipant</h4>
          <div class="block">Gets a list of IDs of transcripts that include the given participant.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>id</code> - A participant ID.</dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>A list of transcript IDs.</dd>
          </dl>
        </li>
      </ul>
      <a id="countMatchingTranscriptIds(java.lang.String)">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/countMatchingTranscriptIds</h4>
          <div class="block">Counts the number of transcripts that match a particular pattern.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>expression</code> - An expression that determines which transcripts match.
              <p> The expression language is currently not well defined, but expressions such as the following can be used:
                <ul>
                  <li><code>id MATCHES 'Ada.+'</code></li>
                  <li><code>'Robert' IN labels('participant')</code></li>
                  <li><code>first('corpus').label IN ('CC', 'IA', 'MU')</code></li>
                  <li><code>first('episode').label = 'Ada Aitcheson'</code></li>
                  <li><code>first('transcript_scribe').label = 'Robert'</code></li>
                  <li><code>first('participant_languages').label = 'en'</code></li>
                  <li><code>first('noise').label = 'bell'</code></li>
                  <li><code>'en' IN labels('transcript_languages')</code></li>
                  <li><code>'en' IN labels('participant_languages')</code></li>
                  <li><code>'bell' IN labels('noise')</code></li>
                  <li><code>all('transcript_languages').length gt; 1</code></li>
                  <li><code>all('participant_languages').length gt; 1</code></li>
                  <li><code>all('word').length gt; 100</code></li>
                  <li><code>'Robert' IN annotators('transcript_rating')</code></li>
                  <li><code>id NOT MATCHES 'Ada.+' AND first('corpus').label = 'CC' AND 'Robert' IN labels('participant')</code></li>
            </ul></dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>The number of matching transcripts.</dd>
          </dl>
        </li>
      </ul>
      <a id="getMatchingTranscriptIds(java.lang.String,java.lang.Integer,java.lang.Integer,java.lang.String)">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/getMatchingTranscriptIds</h4>
          <div class="block"><p>Gets a list of IDs of transcripts that match a particular pattern.</p>
            <p>The results can be exhaustive, by omitting pageLength and pageNumber, or they
              can be a subset (a 'page') of results, by given pageLength and pageNumber values.</p>
            <p>The order of the list can be specified.  If ommitted, the transcripts are listed in ID
              order.</p></div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>expression</code> - An expression that determines which transcripts match.
              <p> The expression language is currently not well defined, but expressions such as the following can be used:
                <ul>
                  <li><code>id MATCHES 'Ada.+'</code></li>
                  <li><code>'Robert' IN labels('participant')</code></li>
                  <li><code>first('corpus').label IN ('CC', 'IA', 'MU')</code></li>
                  <li><code>first('episode').label = 'Ada Aitcheson'</code></li>
                  <li><code>first('transcript_scribe').label = 'Robert'</code></li>
                  <li><code>first('participant_languages').label = 'en'</code></li>
                  <li><code>first('noise').label = 'bell'</code></li>
                  <li><code>'en' IN labels('transcript_languages')</code></li>
                  <li><code>'en' IN labels('participant_languages')</code></li>
                  <li><code>'bell' IN labels('noise')</code></li>
                  <li><code>all('transcript_languages').length gt; 1</code></li>
                  <li><code>all('participant_languages').length gt; 1</code></li>
                  <li><code>all('word').length gt; 100</code></li>
                  <li><code>'Robert' IN annotators('transcript_rating')</code></li>
                  <li><code>id NOT MATCHES 'Ada.+' AND first('corpus').label = 'CC' AND 'Robert' IN labels('participant')</code></li>
            </ul></dd>
            <dd><code>pageLength</code> (Optional) - The maximum number of IDs to return, or absent to return all.</dd>
            <dd><code>pageNumber</code> (Optional) - The zero-based page number to return, or absent to return the first page.</dd>
            <dd><code>order</code> (Optional) - The ordering for the list of IDs, a string containing a comma-separated list of
              expressions, which may be appended by " ASC" or " DESC", or absent for transcript ID order.</dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>A list of transcript IDs.</dd>
          </dl>
        </li>
      </ul>
      <a id="countMatchingAnnotations(java.lang.String)">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/countMatchingAnnotations</h4>
          <div class="block">Counts the number of annotations that match a particular pattern.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>expression</code> - An expression that determines which participants match.
              <p> The expression language is currently not well defined, but expressions such as the following can be used:
                <ul>
                  <li><code>id = 'ew_0_456'</code></li>
                  <li><code>label NOT MATCHES 'th[aeiou].*'</code></li>
                  <li><code>layer.id = 'orthography' AND first('participant').label = 'Robert' AND
                      first('utterance').start.offset = 12.345</code></li> 
                  <li><code>graph.id = 'AdaAicheson-01.trs' AND layer.id = 'orthography' AND start.offset
                      &gt; 10.5</code></li> 
                </ul>
              <p><em>NB</em> all expressions must match by either id or layer.id.</dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>The number of matching annotations.</dd>
          </dl>
        </li>
      </ul>
      <a id="getMatchingAnnotations(java.lang.String,java.lang.Integer,java.lang.Integer)">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/getMatchingAnnotations</h4>
          <div class="block">Gets a list of annotations that match a particular pattern.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>expression</code> - An expression that determines which transcripts match.
              <p> The expression language is currently not well defined, but expressions such as the
                following can be used: 
                <ul>
                  <li><code>id = 'ew_0_456'</code></li>
                  <li><code>label NOT MATCHES 'th[aeiou].*'</code></li>
                  <li><code>first('participant').label = 'Robert' AND first('utterance').start.offset = 12.345</code></li>
                  <li><code>graph.id = 'AdaAicheson-01.trs' AND layer.id = 'orthography' AND start.offset
                      &gt; 10.5</code></li> 
                  <li><code>previous.id = 'ew_0_456'</code></li>
                </ul>
              <p><em>NB</em> all expressions must match by either id or layer.id.</dd>
            <dd><code>pageLength</code> (Optional) - The maximum number of annotations to return, or absent to return all.</dd>
            <dd><code>pageNumber</code> (Optional) - The zero-based page number to return, or absent to return the first page.</dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>A list of matching <a href="Annotation.html" title="class in nzilbb.ag"><code>Annotation</code></a>s.</dd>
          </dl>
        </li>
      </ul>
      <a id="aggregateMatchingAnnotations(java.lang.String,java.lang.String)">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/aggregateMatchingAnnotations</h4>
          <div class="block"> Identifies a list of annotations that match a particular pattern, and aggregates their labels. <p> This allows for counting, listing distinct labels, etc.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>operation</code> - The aggregation operation(s) - e.g. 
             <dl>
              <dt> DISTINCT </dt><dd> List the distinct labels. </dd>
              <dt> MAX </dt><dd> Return the highest label. </dd>
              <dt> MIN </dt><dd> Return the lowest label. </dd>
              <dt> COUNT </dt><dd> Return the number of annotations. </dd>
              <dt> COUNT DISTINCT </dt><dd> Return the number of distinct labels. </dd>
             </dl>
             More than one operation can be specified, by using a comma delimiter. 
             e.g. "DISTINCT,COUNT" will return each distinct label, followed by its count
             (i.e. the array will have twice the number of elements as there are distinct words,
             even-indexed elements are the word labels, and odd-indexed elements are the counts).</dd>
            <dd><code>expression</code> - An expression that determines which transcripts match.
              <p> The expression language is currently not well defined, but expressions such as the
                following can be used: 
             <ul>
              <li><code>layer.id == 'orthography'</code></li>
              <li><code>graph.id == 'AdaAicheson-01.trs' &amp;&amp; layer.id == 'orthography'</code></li> 
             </ul>
              <p><em>NB</em> all expressions must match by either id or layer.id.</dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>A list of results. This may have a single element (e.g. when
             <var>operation</var> == <q>COUNT</q>), or may be a (long) list of labels (e.g. when
             <var>operation</var> == <q>DISTINCT</q>. If there are multiple operations then the
             array will contain a multiple of the number of matching annotations. 
             (e.g. if <var>operation</var> == <q>DISTINCT,COUNT</q> then the array will have
             twice the number of elements as there are distinct words, even-indexed elements are
             the word labels, and odd-indexed elements are the counts.) </dd>
          </dl>
        </li>
      </ul>
      <a id="countAnnotations(java.lang.String,java.lang.String)">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/countAnnotations</h4>
          <div class="block">Gets the number of annotations on the given layer of the given transcript.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>id</code> - The ID of the transcript.</dd>
            <dd><code>layerId</code> - The ID of the layer.</dd>
            <dd><code>maxOrdinal</code> - (Optional) The maximum ordinal for the counted annotations.
             e.g. a <var>maxOrdinal</var> of 1 will ensure that only the first annotation for each
             parent is counted. If <var>maxOrdinal</var> is null, then all annotations are
             counted, regardless of their ordinal.</dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>A (possibly empty) array of annotations.</dd>
          </dl>
        </li>
      </ul>
      <a id="getAnnotations(java.lang.String,java.lang.String,java.lang.Integer,java.lang.Integer)">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/getAnnotations</h4>
          <div class="block">Gets the annotations on the given layer of the given transcript.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>id</code> - The ID of the transcript.</dd>
            <dd><code>layerId</code> - The ID of the layer.</dd>
            <dd><code>maxOrdinal</code> - (Optional) The maximum ordinal for the returned annotations.
             e.g. a <var>maxOrdinal</var> of 1 will ensure that only the first annotation for each
             parent is returned. If <var>maxOrdinal</var> is null, then all annotations are
             counted, regardless of their ordinal.</dd>
            <dd><code>pageLength</code> (Optional) - The maximum number of IDs to return, or absent to return all.</dd>
            <dd><code>pageNumber</code> (Optional) - The zero-based page number to return, or absent to return the first page.</dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>A (possibly empty) array of annotations.</dd>
          </dl>
        </li>
      </ul>
      <a id="getAnchors(java.lang.String,java.lang.String[])">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/getAnchors</h4>
          <div class="block">Gets the given anchors in the given transcript.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>id</code> - The ID of the transcript.</dd>
            <dd><code>anchorIds</code> - A list of anchor IDs, passed by specifying the <code>anchorIds</code> parameter multiple times, once for each anchor.</dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>A (possibly empty) array of anchors.</dd>
          </dl>
        </li>
      </ul>
      <a id="getTranscript(java.lang.String,java.lang.String[])">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/getTranscript</h4>
          <div class="block">Gets a transcript given its ID, containing only the given layers.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>id</code> - The given transcript ID.</dd>
            <dd><code>layerIds</code> - The IDs of the layers to load, passed by specifying the <code>layerIds</code> multiple times, once for each layer, or absent if only transcript data is required.</dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>The identified transcript, encoded as JSON.</dd>
          </dl>
        </li>
      </ul>
      <a id="getFragment(java.lang.String,java.lang.String,java.lang.String[])">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/getFragment</h4>
          <div class="block">Gets a fragment of a transcript, given its ID and the ID of an annotation in it that defines the desired fragment, and containing only the given layers.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>id</code> - The ID of the transcript.</dd>
            <dd><code>annotationId</code> - The ID of an annotation that defines the bounds of the fragment.</dd>
            <dd><code>layerIds</code> - The IDs of the layers to load, passed by specifying the <code>layerIds</code> multiple times, once for each layer, or absent if only transcript data is required.</dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>The identified transcript fragment, encoded as JSON.</dd>
          </dl>
        </li>
      </ul>
      <a id="getFragment(java.lang.String,double,double,java.lang.String[])">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/getFragment</h4>
          <div class="block">Gets a fragment of a transcript, given its ID and the start/end offsets that define the desired fragment, and containing only the given layers.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>id</code> - The ID of the transcript.</dd>
            <dd><code>start</code> - The start offset of the fragment.</dd>
            <dd><code>end</code> - The end offset of the fragment.</dd>
            <dd><code>layerIds</code> - The IDs of the layers to load, passed by specifying the <code>layerIds</code> multiple times, once for each layer, or absent if only transcript data is required.</dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>The identified transcript fragment, encoded as JSON.</dd>
          </dl>
        </li>
      </ul>
      <a id="getMediaTracks()">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/getMediaTracks</h4>
          <div class="block">List the predefined media tracks available for transcripts.</div>
          <dl>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>An ordered list of media track definitions.</dd>
          </dl>
        </li>
      </ul>
      <a id="getAvailableMedia(java.lang.String)">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/getAvailableMedia</h4>
          <div class="block">List the media available for the given transcript.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>id</code> - The transcript ID.</dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>List of media files available for the given transcript.</dd>
          </dl>
        </li>
      </ul>
      <a id="getMedia(java.lang.String,java.lang.String,java.lang.String,java.lang.Double,java.lang.Double)">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/getMedia</h4>
          <div class="block">Gets a given media track for a given transcript.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>id</code> - The transcript ID.</dd>
            <dd><code>trackSuffix</code> (Optional) - The track suffix of the media - see <a href="MediaTrackDefinition.html#suffix"><code>MediaTrackDefinition.suffix</code></a>.</dd>
            <dd><code>mimeType</code> - The MIME type of the media, which may include parameters for type
              conversion, e.g. "text/wav; samplerate=16000"</dd>
            <dd><code>startOffset</code> (Optional) - The start offset of the media sample, or null for the start of the whole
              recording.</dd>
            <dd><code>endOffset</code> (Optional) - The end offset of the media sample, or null for the end of the whole
              recording.</dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>A URL to the given media for the given transcript, or null if the given media doesn't
              exist.</dd>
          </dl>
        </li>
      </ul>
      <a id="countMatchingAnnotations(java.lang.String)">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/countMatchingAnnotations</h4>
          <div class="block">Counts the number of annotations that match a particular pattern.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>expression</code> - An expression that determines which participants match.
              <p> The expression language is currently not well defined, but expressions such as the following can be used:
                <ul>
                  <li><code>id = 'ew_0_456'</code></li>
                  <li><code>label NOT MATCHES 'th[aeiou].*'</code></li>
                  <li><code>layer.id = 'orthography' AND first('participant').label = 'Robert' AND
                      first('utterance').start.offset = 12.345</code></li> 
                  <li><code>graph.id = 'AdaAicheson-01.trs' AND layer.id = 'orthography' AND start.offset
                      &gt; 10.5</code></li> 
                </ul>
              <p><em>NB</em> all expressions must match by either id or layer.id.</dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>The number of matching annotations.</dd>
          </dl>
        </li>
      </ul>
      <a id="getMatchingAnnotations(java.lang.String,java.lang.Integer,java.lang.Integer)">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/getMatchingAnnotations</h4>
          <div class="block">Gets a list of annotations that match a particular pattern.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>expression</code> - An expression that determines which transcripts match.
              <p> The expression language is currently not well defined, but expressions such as the
                following can be used: 
                <ul>
                  <li><code>id = 'ew_0_456'</code></li>
                  <li><code>label NOT MATCHES 'th[aeiou].*'</code></li>
                  <li><code>first('participant').label = 'Robert' AND first('utterance').start.offset = 12.345</code></li>
                  <li><code>graph.id = 'AdaAicheson-01.trs' AND layer.id = 'orthography' AND start.offset
                      &gt; 10.5</code></li> 
                  <li><code>previous.id = 'ew_0_456'</code></li>
                </ul>
              <p><em>NB</em> all expressions must match by either id or layer.id.</dd>
            <dd><code>pageLength</code> - The maximum number of annotations to return, or null to return all.</dd>
            <dd><code>pageNumber</code> - The zero-based page number to return, or null to return the first page.</dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>A list of matching <a href="Annotation.html" title="class in nzilbb.ag"><code>Annotation</code></a>s.</dd>
          </dl>
        </li>
      </ul>
      <a id="getEpisodeDocuments(java.lang.String)">
        <!--   -->
      </a>
      <ul class="blockListLast">
        <li class="blockList">
          <h4>/api/store/getEpisodeDocuments</h4>
          <div class="block">Get a list of documents associated with the episode of the given transcript.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>id</code> - The transcript ID.</dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>List of URLs to documents.</dd>
          </dl>
        </li>
      </ul>

      <a id="getSerializerDescriptors()">
      <!--   -->
      </a>
      <ul class="blockList">
      <li class="blockList">
      <h4>/api/store/getSerializerDescriptors</h4>
      <div class="block">Lists the descriptors of all registered serializers.
      <p> Serializers are modules that export annotation structures as a specific file
      format, e.g. Praat TextGrid, plain text, etc., so the
      <code>mimeType</code> of descriptors reflects what 
      <var>mimeType</var>s can be specified for exporting annotation data.</div>
      <dl>
      <dt><span class="returnLabel">Returns:</span></dt>
      <dd>A list of the descriptors of all registered serializers.</dd>
      </dl>
      </li>
      </ul>
      
      <a id="getDeserializerDescriptors()">
      <!--   -->
      </a>
      <ul class="blockListLast">
      <li class="blockList">
      <h4>/api/store/getDeserializerDescriptors</h4>
      <div class="block">Lists the descriptors of all registered deserializers.
      <p> Deserializers are modules that import annotation structures from a specific file
      format, e.g. Praat TextGrid, plain text, etc.</div>
      <dl>
      <dt><span class="returnLabel">Returns:</span></dt>
      <dd>A list of the descriptors of all registered deserializers.</dd>
      </dl>
      </li>
      </ul>

      <a id="getAnnotatorDescriptors()">
      <!--   -->
      </a>
      <ul class="blockListLast">
      <li class="blockList">
      <h4>/api/store/getAnnotatorDescriptors</h4>
      <div class="block">Lists descriptors of all annotators that are installed.
      <p> Annotators are modules that perform automated annations of existing transcripts.</div>
      <dl>
      <dt><span class="returnLabel">Returns:</span></dt>
      <dd>A list of the descriptors of all registered annotators.</dd>
      </dl>
      </li>
      </ul>

      <a id="getAnnotatorDescriptor()">
      <!--   -->
      </a>
      <ul class="blockListLast">
      <li class="blockList">
      <h4>/api/store/getAnnotatorDescriptors</h4>
      <div class="block">Gets a descriptor of the annotator with the given ID.
      <p> Annotators are modules that perform automated annations of existing transcripts.</div>
      <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>annotatorId</code> - The ID of the annotator.</dd>
      <dt><span class="returnLabel">Returns:</span></dt>
      <dd>A list of the descriptors of all registered annotators.</dd>
      </dl>
      </li>
      </ul>

      <a id="getAnnotatorTasks(java.lang.String)">
        <!--   -->
      </a>
      <ul class="blockListLast">
        <li class="blockList">
          <h4>/api/admin/store/getAnnotatorTasks</h4>
          <div class="block">Supplies a list of automation tasks for the identified annotator.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dt> annotatorId </dt><dd> The ID of the annotator that performs the tasks. </dd>
          </dl>
          <div>The response contains a model which represents a map of <var>taskId</var>s to <var>description</var>s.</div> 
        </li>
      </ul>

      <a id="getAnnotatorTaskParameters(java.lang.String)">
        <!--   -->
      </a>
      <ul class="blockListLast">
        <li class="blockList">
          <h4>/api/admin/store/getAnnotatorTaskParameters</h4>
          <div class="block">Supplies the given task's parameter string.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dt> taskId </dt>     <dd> The ID of the task. </dd>
          </dl>
        </li>
      </ul>

      <a id="getTranscriberDescriptors()">
      <!--   -->
      </a>
      <ul class="blockListLast">
      <li class="blockList">
      <h4>/api/store/getTranscriberDescriptors</h4>
      <div class="block">Lists descriptors of all transcribers that are installed.
      <p> Transcribers are modules that perform perform automated transcription of recordings
          that have not alreadye been transcribed.</div>
      <dl>
      <dt><span class="returnLabel">Returns:</span></dt>
      <dd>A list of the descriptors of all registered transcribers.</dd>
      </dl>
      </li>
      </ul>

 * @author Robert Fromont robert@fromont.net.nz
 */
@WebServlet({"/api/store/*"} )
public class StoreQuery extends LabbcatServlet {
   
  // Attributes:

  // Methods:
   
  /**
   * Default constructor.
   */
  public StoreQuery() {
  } // end of constructor

  /**
   * GET handler
   */
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
      
    JsonObject json = null;
    try {
      SqlGraphStoreAdministration store = getStore(request);
      try {
        if (title == null) {
          title = store.getSystemAttribute("title");
        }
        json = invokeFunction(request, response, store);
        if (json == null) {
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          json = failureResult(request, "Invalid path: {0}", request.getPathInfo());
        }
      } finally {
        cacheStore(store);
      }
    } catch (GraphNotFoundException x) {         
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      json = failureResult(x);
    } catch (PermissionException x) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      json = failureResult(x);
    } catch (StoreException x) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      json = failureResult(x);
    } catch (SQLException x) {
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      json = failureResult(request, "Cannot connect to database: {0}", x.getMessage());
    }
    if (json != null) {
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");
      JsonWriter writer = Json.createWriter(response.getWriter());
      writer.writeObject(json);
      writer.close();
    }
  }

  /**
   * Interprets the URL path, and executes the corresponding function on the store. This
   * method should be overridden by subclasses to interpret their own functions.
   * @param request The request.
   * @param response The response.
   * @param store The connected graph store.
   * @return The response to send to the caller, or null if the request could not be interpreted.
   */
  protected JsonObject invokeFunction(HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
    JsonObject json = null;
    if (request.getPathInfo() == null || request.getPathInfo().equals("/")) {
      // no path component
      json = successResult(
        // send the version in the model for backwards compatibility with labbcat-R <= 0.4-2
        request, Json.createObjectBuilder().add("version", version).build(), null);
      // redirect /store?call=getXXX to /store/getXXX
      if (request.getMethod().equals("GET")
          && request.getParameter("call") != null
          && request.getParameter("call").length() > 0) {
        response.sendRedirect(
          request.getRequestURI()
          + "/" + request.getParameter("call")
          + "?" + request.getQueryString());
      }
    } else {
      String pathInfo = request.getPathInfo().toLowerCase(); // case-insensitive
      if (pathInfo.endsWith("getid")) {
        json = getId(request, response, store);
      } else if (pathInfo.endsWith("getschema")) {
        json = getSchema(request, response, store);
      } else if (pathInfo.endsWith("getlayerids")) {
        json = getLayerIds(request, response, store);
      } else if (pathInfo.endsWith("getlayers")) {
        json = getLayers(request, response, store);
      } else if (pathInfo.endsWith("getlayer")) {
        json = getLayer(request, response, store);
      } else if (pathInfo.endsWith("getcorpusids")) {
        json = getCorpusIds(request, response, store);
      } else if (pathInfo.endsWith("getparticipantids")) {
        json = getParticipantIds(request, response, store);
      } else if (pathInfo.endsWith("getparticipant")) {
        json = getParticipant(request, response, store);
      } else if (pathInfo.endsWith("countmatchingparticipantids")) {
        json = countMatchingParticipantIds(request, response, store);
      } else if (pathInfo.endsWith("getmatchingparticipantids")) {
        json = getMatchingParticipantIds(request, response, store);
      } else if (pathInfo.endsWith("gettranscriptids")
                 // support deprecated name
                 || pathInfo.endsWith("getgraphids")) {
        json = getTranscriptIds(request, response, store);
      } else if (pathInfo.endsWith("gettranscriptidsincorpus")
                 // support deprecated name
                 || pathInfo.endsWith("getgraphidsincorpus")) {
        json = getTranscriptIdsInCorpus(request, response, store);
      } else if (pathInfo.endsWith("gettranscriptidswithparticipant")
                 // support deprecated name
                 || pathInfo.endsWith("getgraphidswithparticipant")) {
        json = getTranscriptIdsWithParticipant(request, response, store);
      } else if (pathInfo.endsWith("countmatchingtranscriptids")
                 // support deprecated name
                 || pathInfo.endsWith("countmatchinggraphids")) {
        json = countMatchingTranscriptIds(request, response, store);
      } else if (pathInfo.endsWith("getmatchingtranscriptids")
                 // support deprecated name
                 || pathInfo.endsWith("getmatchinggraphids")) {
        json = getMatchingTranscriptIds(request, response, store);
      } else if (pathInfo.endsWith("countmatchingannotations")) {
        json = countMatchingAnnotations(request, response, store);
      } else if (pathInfo.endsWith("getmatchingannotations")) {
        json = getMatchingAnnotations(request, response, store);
      } else if (pathInfo.endsWith("aggregatematchingannotations")) {
        json = aggregateMatchingAnnotations(request, response, store);
      } else if (pathInfo.endsWith("countannotations")) {
        json = countAnnotations(request, response, store);
      } else if (pathInfo.endsWith("getannotations")) {
        json = getAnnotations(request, response, store);
      } else if (pathInfo.endsWith("getanchors")) {
        json = getAnchors(request, response, store);
      } else if (pathInfo.endsWith("getmediatracks")) {
        json = getMediaTracks(request, response, store);
      } else if (pathInfo.endsWith("getavailablemedia")) {
        json = getAvailableMedia(request, response, store);
      } else if (pathInfo.endsWith("gettranscript")
                 // support deprecated name
                 || pathInfo.endsWith("getgraph")) {
        json = getTranscript(request, response, store);
      } else if (pathInfo.endsWith("getfragment")) {
        json = getFragment(request, response, store);
      } else if (pathInfo.endsWith("getmedia")) {
        json = getMedia(request, response, store);
      } else if (pathInfo.endsWith("getepisodedocuments")) {
        json = getEpisodeDocuments(request, response, store);
      } else if (pathInfo.endsWith("getserializerdescriptors")) {
        json = getSerializerDescriptors(request, response, store);
      } else if (pathInfo.endsWith("getdeserializerdescriptors")) {
        json = getDeserializerDescriptors(request, response, store);
      } else if (pathInfo.endsWith("getannotatordescriptors")) {
        json = getAnnotatorDescriptors(request, response, store);
      } else if (pathInfo.endsWith("getannotatordescriptor")) {
        json = getAnnotatorDescriptor(request, response, store);
      } else if (pathInfo.endsWith("getannotatortasks")) {
         json = getAnnotatorTasks(request, response, store);
      } else if (pathInfo.endsWith("getannotatortaskparameters")) {
         json = getAnnotatorTaskParameters(request, response, store);
      } else if (pathInfo.endsWith("gettranscriberdescriptors")) {
        json = getTranscriberDescriptors(request, response, store);
      }
    }
    return json;
  } // end of invokeFunction()
   
  // IGraphStoreQuery method handlers

  /**
   * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getId()}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getId(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException {
    return successResult(request, store.getId(), null);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getLayerIds()}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getLayerIds(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException {
      
    return successResult(request, store.getLayerIds(), null);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getLayers()}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getLayers(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException {
      
    Layer[] layers = store.getLayers();
    // unset children so that JSON serialization doesn't double-up layers
    for (Layer layer : layers) layer.setChildren(null);
    return successResult(request, layers, null);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getSchema()}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getSchema(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException {
      
    return successResult(request, store.getSchema(), null);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getLayer(String)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getLayer(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException {
      
    String id = request.getParameter("id");
    if (id == null) return failureResult(request, "No ID specified.");
    return successResult(request, store.getLayer(id), null);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getCorpusIds()}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getCorpusIds(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException {
      
    String[] ids = store.getCorpusIds();
    return successResult(request, ids, ids.length == 0?"There are no corpora.":null);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getParticipantIds()}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getParticipantIds(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException {
      
    String[] ids = store.getParticipantIds();
    return successResult(request, ids, ids.length == 0?"There are no participants.":null);
  }
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getParticipant(String)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getParticipant(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException {
      
    String id = request.getParameter("id");
    if (id == null) return failureResult(request, "No ID specified.");
    String[] layerIds = request.getParameterValues("layerIds");
    // The httr R package can't handle multiple parameters with the same name,
    // so we may have received a single newline-delimited string
    if (layerIds != null && layerIds.length == 1) {
      layerIds = layerIds[0].split("\n");
    }
    Annotation participant = store.getParticipant(id, layerIds);
    return successResult(request, participant, null);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStoreQuery#countMatchingParticipantIds(String)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject countMatchingParticipantIds(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException {
      
    String expression = request.getParameter("expression");
    if (expression == null) return failureResult(request, "No expression specified.");
    return successResult(request, store.countMatchingParticipantIds(expression), null);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getMatchingParticipantIds(String)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getMatchingParticipantIds(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException {
      
    Vector<String> errors = new Vector<String>();
    String expression = request.getParameter("expression");
    if (expression == null) errors.add("No expression specified.");
    Integer pageLength = null;
    if (request.getParameter("pageLength") != null) {
      try {
        pageLength = Integer.valueOf(request.getParameter("pageLength"));
      } catch(NumberFormatException x) {
        errors.add("Invalid page length: " + x.getMessage());
      }
    }
    Integer pageNumber = null;
    if (request.getParameter("pageNumber") != null) {
      try {
        pageNumber = Integer.valueOf(request.getParameter("pageNumber"));
      } catch(NumberFormatException x) {
        errors.add("Invalid page number: " + x.getMessage());
      }
    }
    if (errors.size() > 0) return failureResult(errors);
    String[] ids = store.getMatchingParticipantIds(expression, pageLength, pageNumber);
    return successResult(request, ids, ids.length == 0?"There are no matching IDs.":null);
  }         
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getTranscriptIds()}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getTranscriptIds(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException {
      
    return successResult(request, store.getTranscriptIds(), null);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getTranscriptIdsInCorpus(String)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getTranscriptIdsInCorpus(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException {
      
    String id = request.getParameter("id");
    if (id == null) return failureResult(request, "No ID specified.");
    String[] ids = store.getTranscriptIdsInCorpus(id);
    return successResult(request, ids, ids.length == 0?"There are no matching IDs.":null);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getTranscriptIdsWithParticipant(String)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getTranscriptIdsWithParticipant(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException {
      
    String id = request.getParameter("id");
    if (id == null) return failureResult(request, "No ID specified.");
    String[] ids = store.getTranscriptIdsWithParticipant(id);
    return successResult(request, ids, ids.length == 0?"There are no matching IDs.":null);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStoreQuery#countMatchingTranscriptIds(String)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject countMatchingTranscriptIds(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException {
      
    String expression = request.getParameter("expression");
    if (expression == null) return failureResult(request, "No expression specified.");
    return successResult(request, store.countMatchingTranscriptIds(expression), null);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getMatchingTranscriptIds(String)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getMatchingTranscriptIds(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException {

    Vector<String> errors = new Vector<String>();
    String expression = request.getParameter("expression");
    if (expression == null) errors.add(localize(request, "No expression specified."));
    Integer pageLength = null;
    if (request.getParameter("pageLength") != null) {
      try {
        pageLength = Integer.valueOf(request.getParameter("pageLength"));
      } catch(NumberFormatException x) {
        errors.add(localize(request, "Invalid page length: " + x.getMessage()));
      }
    }
    Integer pageNumber = null;
    if (request.getParameter("pageNumber") != null) {
      try {
        pageNumber = Integer.valueOf(request.getParameter("pageNumber"));
      } catch(NumberFormatException x) {
        errors.add(localize(request, "Invalid page number: " + x.getMessage()));
      }
    }
    if (errors.size() > 0) return failureResult(errors);
    String[] ids = store.getMatchingTranscriptIds(expression, pageLength, pageNumber);
    return successResult(request, ids, ids.length == 0?"There are no matching IDs.":null);
  }         
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStoreQuery#countMatchingAnnotations(String)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject countMatchingAnnotations(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException {
      
    String expression = request.getParameter("expression");
    if (expression == null) return failureResult(request, "No expression specified.");
    return successResult(request, store.countMatchingAnnotations(expression), null);
  }      
   
  /**
   * Implementation of
   * {@link nzilbb.ag.IGraphStoreQuery#getMatchingAnnotations(String,Integer,Integer)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getMatchingAnnotations(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException {
      
    Vector<String> errors = new Vector<String>();
    String expression = request.getParameter("expression");
    if (expression == null) errors.add(localize(request, "No expression specified."));
    Integer pageLength = null;
    if (request.getParameter("pageLength") != null) {
      try {
        pageLength = Integer.valueOf(request.getParameter("pageLength"));
      } catch(NumberFormatException x) {
        errors.add(localize(request, "Invalid page length: {0}", x.getMessage()));
      }
    }
    Integer pageNumber = null;
    if (request.getParameter("pageNumber") != null) {
      try {
        pageNumber = Integer.valueOf(request.getParameter("pageNumber"));
      } catch(NumberFormatException x) {
        errors.add(localize(request, "Invalid page number: {0}", x.getMessage()));
      }
    }
    if (errors.size() > 0) return failureResult(errors);
    Annotation[] annotations = store.getMatchingAnnotations(expression, pageLength, pageNumber);
    return successResult(request, annotations, annotations.length == 0?"There are no annotations.":null);
  }         

  /**
   * Implementation of
   * {@link nzilbb.ag.GraphStoreQuery#aggregateMatchingAnnotations(String,String)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject aggregateMatchingAnnotations(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException {
      
    Vector<String> errors = new Vector<String>();
    String operation = request.getParameter("operation");
    if (operation == null) errors.add(localize(request, "No operation specified."));
    String expression = request.getParameter("expression");
    if (expression == null) errors.add(localize(request, "No expression specified."));
    if (errors.size() > 0) return failureResult(errors);
    String[] values = store.aggregateMatchingAnnotations(operation, expression);
    return successResult(request, values, values.length == 0?"There are no annotations.":null);
  }         

  /**
   * Implementation of {@link nzilbb.ag.IGraphStoreQuery#countAnnotations(String,String,Integer)}
   * @param request The HTTP request.
   * @param response The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject countAnnotations(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
      
    Vector<String> errors = new Vector<String>();
    String id = request.getParameter("id");
    if (id == null) errors.add(localize(request, "No ID specified."));
    String layerId = request.getParameter("layerId");
    if (layerId == null) errors.add(localize(request, "No layer ID specified."));
    Integer maxOrdinal = null;
    if (request.getParameter("maxOrdinal") != null) {
      try {
        maxOrdinal = Integer.valueOf(request.getParameter("maxOrdinal"));
      } catch(NumberFormatException x) {
        errors.add(localize(request, "Invalid maximum ordinal: {0}", x.getMessage()));
      }
    }
    if (errors.size() > 0) return failureResult(errors);
    return successResult(request, store.countAnnotations(id, layerId, maxOrdinal), null);
  }
   
  /**
   * Implementation of
   * {@link nzilbb.ag.IGraphStoreQuery#getAnnotations(String,String,Integer,Integer,Integer)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getAnnotations(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
      
    Vector<String> errors = new Vector<String>();
    String id = request.getParameter("id");
    if (id == null) errors.add("No ID specified.");
    String layerId = request.getParameter("layerId");
    if (layerId == null) errors.add(localize(request, "No layer ID specified."));
    Integer pageLength = null;
    if (request.getParameter("pageLength") != null) {
      try {
        pageLength = Integer.valueOf(request.getParameter("pageLength"));
      } catch(NumberFormatException x) {
        errors.add(localize(request, "Invalid page length: {0}", x.getMessage()));
      }
    }
    Integer pageNumber = null;
    if (request.getParameter("pageNumber") != null) {
      try {
        pageNumber = Integer.valueOf(request.getParameter("pageNumber"));
      } catch(NumberFormatException x) {
        errors.add(localize(request, "Invalid page number: {0}", x.getMessage()));
      }
    }
    Integer maxOrdinal = null;
    if (request.getParameter("maxOrdinal") != null) {
      try {
        maxOrdinal = Integer.valueOf(request.getParameter("maxOrdinal"));
      } catch(NumberFormatException x) {
        errors.add(localize(request, "Invalid maximum ordinal: {0}", x.getMessage()));
      }
    }
    if (errors.size() > 0) return failureResult(errors);
    Annotation[] annotations = store.getAnnotations(id, layerId, maxOrdinal, pageLength, pageNumber);
    return successResult(request, annotations, annotations.length == 0?"There are no annotations.":null);
  }

  // TODO getMatchAnnotations
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getAnchors(String,String[])}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getAnchors(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
      
    Vector<String> errors = new Vector<String>();
    String id = request.getParameter("id");
    if (id == null) errors.add(localize(request, "No ID specified."));
    String[] anchorIds = request.getParameterValues("anchorIds");
    if (anchorIds == null) errors.add(localize(request, "No anchor IDs specified."));
    if (errors.size() > 0) return failureResult(errors);
    return successResult(request, store.getAnchors(id, anchorIds), null);
  }
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getTranscript(String,String[])}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getTranscript(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
      
    Vector<String> errors = new Vector<String>();
    String id = request.getParameter("id");
    if (id == null) errors.add(localize(request, "No ID specified."));
    String[] layerIds = request.getParameterValues("layerIds");
    if (layerIds == null) layerIds = new String[0];
    if (errors.size() > 0) return failureResult(errors);

    Graph transcript = store.getTranscript(id, layerIds);
    // serialize with JSON serialization
    JSONSerialization s = new JSONSerialization();
    s.configure(s.configure(new ParameterSet(), transcript.getSchema()), transcript.getSchema());
    final Vector<SerializationException> exceptions = new Vector<SerializationException>();
    final Vector<NamedStream> streams = new Vector<NamedStream>();
    try {
      s.serialize(Utility.OneGraphSpliterator(transcript), null,
                  (stream) -> streams.add(stream),
                  (warning) -> System.out.println(warning),
                  (exception) -> exceptions.add(exception));
      JsonReader reader = Json.createReader(
        new InputStreamReader(streams.elementAt(0).getStream(), "UTF-8"));
      JsonObject json = reader.readObject();    
      return successResult(request, json, null);
    } catch(SerializerNotConfiguredException exception) { // shouldn't happen
      throw new StoreException(exception);
    }
  }

  /**
   * Implementation of {@link nzilbb.ag.GraphStoreQuery#getFragment(String,String,String[])}
   * and {@link nzilbb.ag.GraphStoreQuery#getFragment(String,double,double,String[])}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getFragment(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
    
    Vector<String> errors = new Vector<String>();
    String id = request.getParameter("id");
    if (id == null) errors.add(localize(request, "No ID specified."));
    String[] layerIds = request.getParameterValues("layerIds");
    if (layerIds == null) layerIds = new String[0];
    String annotationId = request.getParameter("annotationId");
    String startParameter = request.getParameter("start");
    Double start = null;
    if (startParameter != null) {
      try {
        start = Double.valueOf(startParameter);
      } catch(NumberFormatException x) {
        errors.add(localize(request, "Invalid start offset: {0}", x.getMessage()));
      }
    }
    String endParameter = request.getParameter("end");
    Double end = null;
    if (endParameter != null) {
      try {
        end = Double.valueOf(endParameter);
      } catch(NumberFormatException x) {
        errors.add(localize(request, "Invalid end offset: {0}", x.getMessage()));
      }
    }
    if (annotationId == null && (start == null || end == null)) {
      errors.add(localize(request, "Annotation ID not specified.")); // TODO i18n
    }
    
    if (errors.size() > 0) return failureResult(errors);
    Graph fragment = annotationId != null?
      store.getFragment(id, annotationId, layerIds):
      store.getFragment(id, start, end, layerIds);
    // serialize with JSON serialization
    JSONSerialization s = new JSONSerialization();
    s.configure(s.configure(new ParameterSet(), fragment.getSchema()), fragment.getSchema());
    final Vector<SerializationException> exceptions = new Vector<SerializationException>();
    final Vector<NamedStream> streams = new Vector<NamedStream>();
    try {
      s.serialize(Utility.OneGraphSpliterator(fragment), null,
                  (stream) -> streams.add(stream),
                  (warning) -> System.out.println(warning),
                  (exception) -> exceptions.add(exception));
      JsonReader reader = Json.createReader(
        new InputStreamReader(streams.elementAt(0).getStream(), "UTF-8"));
      JsonObject json = reader.readObject();    
      return successResult(request, json, null);
    } catch(SerializerNotConfiguredException exception) { // shouldn't happen
      throw new StoreException(exception);
    }

  }
  // TODO getFragmentSeries
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getMediaTracks()}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getMediaTracks(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException {
      
    return successResult(request, store.getMediaTracks(), null);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getAvailableMedia(String)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getAvailableMedia(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
      
    String id = request.getParameter("id");
    if (id == null) return failureResult(request, "No ID specified.");

    MediaFile[] media = store.getAvailableMedia(id);
      
    // strip out local file paths
    for (MediaFile file : media) file.setFile(null);
      
    return successResult(request, media, media.length == 0?"There is no media.":null);
  }

  /**
   * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getAvailableMedia(String)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getMedia(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
      
    Vector<String> errors = new Vector<String>();
    String id = request.getParameter("id");
    if (id == null) errors.add(localize(request, "No ID specified."));
    String trackSuffix = request.getParameter("trackSuffix"); // optional
    String mimeType = request.getParameter("mimeType");
    if (mimeType == null) errors.add(localize(request, "No Content Type specified."));
    Double startOffset = null;
    if (request.getParameter("startOffset") != null) {
      try {
        startOffset = Double.valueOf(request.getParameter("startOffset"));
      } catch(NumberFormatException x) {
        errors.add(localize(request, "Invalid start offset: {0}", x.getMessage()));
      }
    }
    Double endOffset = null;
    if (request.getParameter("endOffset") != null) {
      try {
        endOffset = Double.valueOf(request.getParameter("endOffset"));
      } catch(NumberFormatException x) {
        errors.add(localize(request, "Invalid end offset: {0}", x.getMessage()));
      }
    }
    if (startOffset == null && endOffset == null) {
      if (errors.size() > 0) return failureResult(errors);
      return successResult(request, store.getMedia(id, trackSuffix, mimeType), null);
    } else {
      if (startOffset == null) errors.add(localize(request, "Start offset not specified."));
      if (endOffset == null) errors.add(localize(request, "End offset not specified."));
      if (endOffset <= startOffset)
        errors.add(localize(request, "Start offset ({0,number,#.###}) must be before end offset ({1,number,#.###})", startOffset, endOffset));
      if (errors.size() > 0) return failureResult(errors);
      return successResult(request, store.getMedia(id, trackSuffix, mimeType, startOffset, endOffset), null);
    }
  }

  /**
   * Implementation of {@link nzilbb.ag.IGraphStoreQuery#getEpisodeDocuments(String)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getEpisodeDocuments(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
      
    String id = request.getParameter("id");
    if (id == null) return failureResult(request, "No ID specified.");

    MediaFile[] media = store.getEpisodeDocuments(id);
      
    // strip out local file paths
    for (MediaFile file : media) file.setFile(null);
      
    return successResult(request, media, media.length == 0?"There are no documents.":null);
  }

  /**
   * Lists the descriptors of all registered serializers.
   * <p> Serializers are modules that export annotation structures as a specific file
   * format, e.g. Praat TextGrid, plain text, etc., so the mimeType of descriptors reflects what 
   * <var>mimeType</var>s can be specified for {@link SerializeGraphs}.
   * @return A list of the descriptors of all registered serializers.
   * @throws StoreException If an error prevents the operation from completing.
   * @throws PermissionException If the operation is not permitted.
   */
  protected JsonObject getSerializerDescriptors(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
    SerializationDescriptor[] descriptors = store.getSerializerDescriptors();
    return successResult(
      request, descriptors, descriptors.length == 0?"There are no serializers.":null);
  }
   
  /**
   * Lists the descriptors of all registered deserializers.
   * <p> Deserializers are modules that import annotation structures from a specific file
   * format, e.g. Praat TextGrid, plain text, etc.
   * @return A list of the descriptors of all registered deserializers.
   * @throws StoreException If an error prevents the descriptors from being listed.
   * @throws PermissionException If listing the deserializers is not permitted.
   */
  protected JsonObject getDeserializerDescriptors(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
    SerializationDescriptor[] descriptors = store.getDeserializerDescriptors();
    return successResult(
      request, descriptors, descriptors.length == 0?"There are no deserializers.":null);
  }

  /**
   * Lists descriptors of all annotators that are installed.
   * <p> Annotators are modules that perform automated annations of existing transcripts.
   * @return A list of the descriptors of all registered annotators.
   * @throws StoreException If an error prevents the descriptors from being listed.
   * @throws PermissionException If listing the deserializers is not permitted.
   */
  protected JsonObject getAnnotatorDescriptors(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
    AnnotatorDescriptor[] descriptors = store.getAnnotatorDescriptors();
    return successResult(
      request, descriptors, descriptors.length == 0?"There are no annotators.":null);
  }
  
  /**
   * Gets a descriptor of the annotator with the given ID (annotatorId).
   * <p> Annotators are modules that perform automated annations of existing transcripts.
   * @return A list of the descriptors of all registered annotators.
   * @throws StoreException If an error prevents the descriptors from being listed.
   * @throws PermissionException If listing the deserializers is not permitted.
   */
  protected JsonObject getAnnotatorDescriptor(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
    String annotatorId = request.getParameter("annotatorId");
    if (annotatorId == null) return failureResult(request, "No ID specified.");
    AnnotatorDescriptor descriptor = store.getAnnotatorDescriptor(annotatorId);
    if (descriptor == null) return failureResult(request, "Invalid ID: " + annotatorId);
    return successResult(
      request, descriptor, null);
  }

  /**
   * Supplies a list of automation tasks for the identified annotator.
   * @param request The HTTP request with parameter:
   * <dl>
   *  <dt> annotatorId </dt><dd> The ID of the annotator that performs the tasks. </dd>
   * </dl>
   * @param response The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getAnnotatorTasks(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
    Vector<String> errors = new Vector<String>();
    // get/validate the parameters
    String annotatorId = request.getParameter("annotatorId");
    if (annotatorId == null) errors.add(localize(request, "No Annotator ID specified."));
    if (errors.size() > 0) return failureResult(errors);
    
    return successResult(
      request, store.getAnnotatorTasks(annotatorId), null);
  }      
  
  /**
   * Supplies the given task's parameter string.
   * @param request The HTTP request with parameter:
   * <dl>
   *  <dt> taskId </dt><dd> The ID of the automation task. </dd>
   * </dl>
   * @param response The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getAnnotatorTaskParameters(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
    Vector<String> errors = new Vector<String>();
    // get/validate the parameters
    String taskId = request.getParameter("taskId");
    if (taskId == null) errors.add(localize(request, "No ID specified."));
    if (errors.size() > 0) return failureResult(errors);
    
    return successResult(
      request, store.getAnnotatorTaskParameters(taskId), null);
  }
  
  /**
   * Lists descriptors of all transcribers that are installed.
   * <p> Transcribers are modules that perform automated transcription of recordings
   * that have not alreadye been transcribed.
   * @return A list of the descriptors of all registered transcribers.
   * @throws StoreException If an error prevents the descriptors from being listed.
   * @throws PermissionException If listing the deserializers is not permitted.
   */
  protected JsonObject getTranscriberDescriptors(
    HttpServletRequest request, HttpServletResponse response, SqlGraphStoreAdministration store)
    throws ServletException, IOException, StoreException, PermissionException, GraphNotFoundException {
    AnnotatorDescriptor[] descriptors = store.getTranscriberDescriptors();
    return successResult(
      request, descriptors, descriptors.length == 0?"There are no transcribers.":null);
  }

  private static final long serialVersionUID = 1;
} // end of class StoreQuery
