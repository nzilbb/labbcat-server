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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Vector;
import java.util.function.Consumer;
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
 * <a href="https://nzilbb.github.io/ag/apidocs/nzilbb/ag/GraphStore.html">GraphStore</a> 
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
      <p> LaBB-CAT extends the {@link Layer#validLabels} funcionality by supporting an
      alternative layer attribute: <tt>validLabelsDefinition</tt>, which is an array of
      label definitions, each definition being a map of string to string or integer. Each
      label definition is expected to have the following attributes:
      <dl>
      <dt>label</dt> 
       <dd>what the underlying label is in LaBB-CAT (i.e. the DISC label, for a DISC layer)</dd> 
      <dt>legend</dt> 
       <dd>the symbol on the label helper or in the transcript, for the label (e.g. the IPA
           version of the label) - if there's no legend specified, then there's no option
           on the label helper (so that type-able consonants like p, b, t, d etc. don't
           take up space on the label helper)</dd> 
      <dt>description</dt> 
       <dd>tool-tip text that appears if you hover the mouse over the IPA symbol in the helper</dd>
      <dt>category</dt> 
       <dd>the broad category of the symbol, for organizing the layout of the helper</dd>
      <dt>subcategory</dt> 
       <dd>the narrower category of the symbol, for listing subgroups of symbols together</dd>
      <dt>display_order</dt> 
       <dd>the order to process/list the labels in</dd>
      </dl>
      <p> <tt>validLabelsDefinition</tt> is returned if there is a hierarchical set of
      options defined. Either way, <tt>validLabels</tt> (specifying the valid labels and
      their 'legend' values) is always returned.
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
                <li><code>/Ada.+/.test(id)</code></li>
                <li><code>labels('corpus').includes('CC')</code></li>
                <li><code>labels('participant_languages').includes('en')</code></li>
                <li><code>labels('transcript_language').includes('en')</code></li>
                <li><code>!/Ada.+/.test(id) &amp;&amp; first('corpus').label == 'CC'</code></li>
                <li><code>all('transcript_rating').length &gt; 2</code></li>
                <li><code>all('participant_rating').length = 0</code></li>
                <li><code>!annotators('transcript_rating').includes('labbcat')</code></li>
                <li><code>first('participant_gender').label == 'NA'</code></li>
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
                <li><code>/Ada.+/.test(id)</code></li>
                <li><code>labels('corpus').includes('CC')</code></li>
                <li><code>labels('participant_languages').includes('en')</code></li>
                <li><code>labels('transcript_language').includes('en')</code></li>
                <li><code>!/Ada.+/.test(id) &amp;&amp; first('corpus').label == 'CC'</code></li>
                <li><code>all('transcript_rating').length &gt; 2</code></li>
                <li><code>all('participant_rating').length = 0</code></li>
                <li><code>!annotators('transcript_rating').includes('labbcat')</code></li>
                <li><code>first('participant_gender').label == 'NA'</code></li>
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
                <li><code>/Ada.+/.test(id)</code></li>
                <li><code>labels('participant').includes('Robert')</code></li>
                <li><code>['CC', 'IA', 'MU'].includes(first('corpus').label)</code></li>
                <li><code>first('episode').label == 'Ada Aitcheson'</code></li>
                <li><code>first('transcript_scribe').label == 'Robert'</code></li>
                <li><code>first('participant_languages').label == 'en'</code></li>
                <li><code>first('noise').label == 'bell'</code></li>
                <li><code>labels('transcript_languages').includes('en')</code></li>
                <li><code>labels('participant_languages').includes('en')</code></li>
                <li><code>labels('noise').includes('bell')</code></li>
                <li><code>all('transcript_languages').length gt; 1</code></li>
                <li><code>all('participant_languages').length gt; 1</code></li>
                <li><code>all('word').length &gt; 100</code></li>
                <li><code>annotators('transcript_rating').includes('Robert')</code></li>
                <li><code>!/Ada.+/.test(id) &amp;&amp; first('corpus').label == 'CC' &amp;&amp;
                labels('participant').includes('Robert')</code></li> 
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
                <li><code>/Ada.+/.test(id)</code></li>
                <li><code>labels('participant').includes('Robert')</code></li>
                <li><code>['CC', 'IA', 'MU'].includes(first('corpus').label)</code></li>
                <li><code>first('episode').label == 'Ada Aitcheson'</code></li>
                <li><code>first('transcript_scribe').label == 'Robert'</code></li>
                <li><code>first('participant_languages').label == 'en'</code></li>
                <li><code>first('noise').label == 'bell'</code></li>
                <li><code>labels('transcript_languages').includes('en')</code></li>
                <li><code>labels('participant_languages').includes('en')</code></li>
                <li><code>labels('noise').includes('bell')</code></li>
                <li><code>all('transcript_languages').length gt; 1</code></li>
                <li><code>all('participant_languages').length gt; 1</code></li>
                <li><code>all('word').length &gt; 100</code></li>
                <li><code>annotators('transcript_rating').includes('Robert')</code></li>
                <li><code>!/Ada.+/.test(id) &amp;&amp; first('corpus').label == 'CC' &amp;&amp;
                labels('participant').includes('Robert')</code></li> 
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
                  <li><code>!/th[aeiou].+/.test(label)</code></li>
                  <li><code>layer.id == 'orthography' &amp;&amp; first('participant').label == 'Robert' &amp;&amp;
                      first('utterance').start.offset == 12.345</code></li> 
                  <li><code>graph.id == 'AdaAicheson-01.trs' &amp; layer.id == 'orthography' &amp; start.offset
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
                  <li><code>!/th[aeiou].+/.test(label)</code></li>
                  <li><code>layer.id == 'orthography' &amp;&amp; first('participant').label == 'Robert' &amp;&amp;
                      first('utterance').start.offset == 12.345</code></li> 
                  <li><code>graph.id == 'AdaAicheson-01.trs' &amp; layer.id == 'orthography' &amp; start.offset
                      &gt; 10.5</code></li> 
                  <li><code>previous.id == 'ew_0_456'</code></li>
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
                  <li><code>!/th[aeiou].+/.test(label)</code></li>
                  <li><code>layer.id == 'orthography' &amp;&amp; first('participant').label == 'Robert' &amp;&amp;
                      first('utterance').start.offset == 12.345</code></li> 
                  <li><code>graph.id == 'AdaAicheson-01.trs' &amp; layer.id == 'orthography' &amp; start.offset
                      &gt; 10.5</code></li> 
                  <li><code>previous.id == 'ew_0_456'</code></li>
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
                  <li><code>!/th[aeiou].+/.test(label)</code></li>
                  <li><code>layer.id == 'orthography' &amp;&amp; first('participant').label == 'Robert' &amp;&amp;
                      first('utterance').start.offset == 12.345</code></li> 
                  <li><code>graph.id == 'AdaAicheson-01.trs' &amp; layer.id == 'orthography' &amp; start.offset
                      &gt; 10.5</code></li> 
                  <li><code>previous.id == 'ew_0_456'</code></li>
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
public class Store extends APIRequestHandler {
   
  /**
   * Default constructor.
   */
  public Store() {
  } // end of constructor
  
  /**
   * GET handler lists all rows. 
   * <p> The return is JSON encoded, unless the "Accept" request header, or the "Accept"
   * request parameter, is "text/csv", in which case CSV is returned.
   * @param url The URI of the request. 
   * @param method The HTTP request method, e.g. "GET".
   * @param pathInfo The URL path.
   * @param queryString The URL's query string.
   * @param parameters Request parameter map.
   * @param requestBody For access to the request body.
   * @param httpStatus Receives the response status code, in case or error.
   * @param redirectUrl Receives a URL for the request to be redirected to.
   * @return JSON-encoded object representing the response
   */
  public JsonObject get(
    String url, String method, String pathInfo,
    String queryString, RequestParameters parameters, InputStream requestBody,
    Consumer<Integer> httpStatus, Consumer<String> redirectUrl) {
    
    try {
      SqlGraphStoreAdministration store = getStore();
      try {
        JsonObject json = invokeFunction(
          url, method, pathInfo, queryString, parameters, requestBody,
          httpStatus, redirectUrl, store);
        if (json == null) {
          httpStatus.accept(SC_BAD_REQUEST);
          return failureResult("Invalid path: {0}", pathInfo);
        } else {
          return json;
        }
      } finally {
        cacheStore(store);
      }
    } catch (GraphNotFoundException x) {         
      httpStatus.accept(SC_NOT_FOUND);
      return failureResult(x);
    } catch (PermissionException x) {
      httpStatus.accept(SC_FORBIDDEN);
      return failureResult(x);
    } catch (StoreException x) {
      httpStatus.accept(SC_BAD_REQUEST);
      return failureResult(x);
    } catch (SQLException x) {
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      return failureResult("Cannot connect to database: {0}", x.getMessage());
    } catch (IOException x) {
      httpStatus.accept(SC_INTERNAL_SERVER_ERROR);
      return failureResult("Communications error: {0}", x.getMessage());
    }
  }

  /**
   * Interprets the URL path, and executes the corresponding function on the store. This
   * method should be overridden by subclasses to interpret their own functions.
   * @param url The URI of the request. 
   * @param method The HTTP request method, e.g. "GET".
   * @param pathInfo The URL path.
   * @param queryString The URL's query string.
   * @param parameters Request parameter map.
   * @param requestBody For access to the request body.
   * @param httpStatus Receives the response status code, in case or error.
   * @param redirectUrl Receives a URL for the request to be redirected to.
   * @return JSON-encoded object representing the response
   */
  protected JsonObject invokeFunction(String url, String method, String pathInfo, String queryString, RequestParameters parameters, InputStream requestBody, Consumer<Integer> httpStatus, Consumer<String> redirectUrl, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException, GraphNotFoundException {
    if (pathInfo == null || pathInfo.equals("/")) { // no path component
      // redirect /store?call=getXXX to /store/getXXX
      if ("GET".equals(method)
          && parameters.getString("call") != null
          && parameters.getString("call").length() > 0) {
        redirectUrl.accept(
          url
          + "/" + parameters.getString("call")
          + "?" + queryString);
        return null;
      } else {
        return successResult(
          // send the version in the model for backwards compatibility with labbcat-R <= 0.4-2
          Json.createObjectBuilder().add("version", context.getVersion()).build(), null);
      }
    } else {
      pathInfo = pathInfo.toLowerCase(); // case-insensitive
      if (pathInfo.endsWith("getid")) {
        return getId(parameters, store);
      } else if (pathInfo.endsWith("getschema")) {
        return getSchema(parameters, store);
      } else if (pathInfo.endsWith("getlayerids")) {
        return getLayerIds(parameters, store);
      } else if (pathInfo.endsWith("getlayers")) {
        return getLayers(parameters, store);
      } else if (pathInfo.endsWith("getlayer")) {
        return getLayer(parameters, store);
      } else if (pathInfo.endsWith("getcorpusids")) {
        return getCorpusIds(parameters, store);
      } else if (pathInfo.endsWith("getparticipantids")) {
        return getParticipantIds(parameters, store);
      } else if (pathInfo.endsWith("getparticipant")) {
        return getParticipant(parameters, store);
      } else if (pathInfo.endsWith("countmatchingparticipantids")) {
        return countMatchingParticipantIds(parameters, store);
      } else if (pathInfo.endsWith("getmatchingparticipantids")) {
        return getMatchingParticipantIds(parameters, store);
      } else if (pathInfo.endsWith("gettranscriptids")
                 // support deprecated name
                 || pathInfo.endsWith("getgraphids")) {
        return getTranscriptIds(parameters, store);
      } else if (pathInfo.endsWith("gettranscriptidsincorpus")
                 // support deprecated name
                 || pathInfo.endsWith("getgraphidsincorpus")) {
        return getTranscriptIdsInCorpus(parameters, store);
      } else if (pathInfo.endsWith("gettranscriptidswithparticipant")
                 // support deprecated name
                 || pathInfo.endsWith("getgraphidswithparticipant")) {
        return getTranscriptIdsWithParticipant(parameters, store);
      } else if (pathInfo.endsWith("countmatchingtranscriptids")
                 // support deprecated name
                 || pathInfo.endsWith("countmatchinggraphids")) {
        return countMatchingTranscriptIds(parameters, store);
      } else if (pathInfo.endsWith("getmatchingtranscriptids")
                 // support deprecated name
                 || pathInfo.endsWith("getmatchinggraphids")) {
        return getMatchingTranscriptIds(parameters, store);
      } else if (pathInfo.endsWith("countmatchingannotations")) {
        return countMatchingAnnotations(parameters, store);
      } else if (pathInfo.endsWith("getmatchingannotations")) {
        return getMatchingAnnotations(parameters, store);
      } else if (pathInfo.endsWith("aggregatematchingannotations")) {
        return aggregateMatchingAnnotations(parameters, store);
      } else if (pathInfo.endsWith("countannotations")) {
        return countAnnotations(parameters, store);
      } else if (pathInfo.endsWith("getannotations")) {
        return getAnnotations(parameters, store);
      } else if (pathInfo.endsWith("getanchors")) {
        return getAnchors(parameters, store);
      } else if (pathInfo.endsWith("getmediatracks")) {
        return getMediaTracks(parameters, store);
      } else if (pathInfo.endsWith("getavailablemedia")) {
        return getAvailableMedia(parameters, store);
      } else if (pathInfo.endsWith("gettranscript")
                 // support deprecated name
                 || pathInfo.endsWith("getgraph")) {
        return getTranscript(parameters, store);
      } else if (pathInfo.endsWith("getfragment")) {
        return getFragment(parameters, store);
      } else if (pathInfo.endsWith("getmedia")) {
        return getMedia(parameters, store);
      } else if (pathInfo.endsWith("getepisodedocuments")) {
        return getEpisodeDocuments(parameters, store);
      } else if (pathInfo.endsWith("getserializerdescriptors")) {
        return getSerializerDescriptors(parameters, store);
      } else if (pathInfo.endsWith("getdeserializerdescriptors")) {
        return getDeserializerDescriptors(parameters, store);
      } else if (pathInfo.endsWith("getannotatordescriptors")) {
        return getAnnotatorDescriptors(parameters, store);
      } else if (pathInfo.endsWith("getannotatordescriptor")) {
        return getAnnotatorDescriptor(parameters, store);
      } else if (pathInfo.endsWith("getannotatortasks")) {
         return getAnnotatorTasks(parameters, store);
      } else if (pathInfo.endsWith("getannotatortaskparameters")) {
         return getAnnotatorTaskParameters(parameters, store);
      } else if (pathInfo.endsWith("gettranscriberdescriptors")) {
        return getTranscriberDescriptors(parameters, store);
      }
    }
    return null;
  } // end of invokeFunction()
   
  // IGraphStore method handlers

  /**
   * Implementation of {@link nzilbb.ag.IGraphStore#getId()}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getId(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException {
    return successResult(store.getId(), null);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStore#getLayerIds()}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getLayerIds(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException {
      
    return successResult(store.getLayerIds(), null);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStore#getLayers()}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getLayers(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException {
      
    Layer[] layers = store.getLayers();
    // unset children so that JSON serialization doesn't double-up layers
    for (Layer layer : layers) layer.setChildren(null);
    return successResult(layers, null);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStore#getSchema()}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getSchema(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException {
      
    return successResult(store.getSchema(), null);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStore#getLayer(String)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getLayer(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException {
      
    String id = parameters.getString("id");
    if (id == null) return failureResult("No ID specified.");
    return successResult(store.getLayer(id), null);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStore#getCorpusIds()}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getCorpusIds(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException {
      
    String[] ids = store.getCorpusIds();
    return successResult(ids, ids.length == 0?"There are no corpora.":null);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStore#getParticipantIds()}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getParticipantIds(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException {
      
    String[] ids = store.getParticipantIds();
    return successResult(ids, ids.length == 0?"There are no participants.":null);
  }
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStore#getParticipant(String)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getParticipant(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException {
      
    String id = parameters.getString("id");
    if (id == null) return failureResult("No ID specified.");
    String[] layerIds = parameters.getStrings("layerIds");
    // The httr R package can't handle multiple parameters with the same name,
    // so we may have received a single newline-delimited string
    if (layerIds.length == 1) {
      layerIds = layerIds[0].split("\n");
    }
    Annotation participant = store.getParticipant(id, layerIds);
    return successResult(participant, null);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStore#countMatchingParticipantIds(String)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject countMatchingParticipantIds(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException {
      
    String expression = parameters.getString("expression");
    if (expression == null) return failureResult("No expression specified.");
    return successResult(store.countMatchingParticipantIds(expression), null);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStore#getMatchingParticipantIds(String)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getMatchingParticipantIds(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException {
      
    Vector<String> errors = new Vector<String>();
    String expression = parameters.getString("expression");
    if (expression == null) errors.add("No expression specified.");
    Integer pageLength = null;
    if (parameters.getString("pageLength") != null) {
      try {
        pageLength = Integer.valueOf(parameters.getString("pageLength"));
      } catch(NumberFormatException x) {
        errors.add("Invalid page length: " + x.getMessage());
      }
    }
    Integer pageNumber = null;
    if (parameters.getString("pageNumber") != null) {
      try {
        pageNumber = Integer.valueOf(parameters.getString("pageNumber"));
      } catch(NumberFormatException x) {
        errors.add("Invalid page number: " + x.getMessage());
      }
    }
    if (errors.size() > 0) return failureResult(errors);
    String[] ids = store.getMatchingParticipantIds(expression, pageLength, pageNumber);
    return successResult(ids, ids.length == 0?"There are no matching IDs.":null);
  }         
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStore#getTranscriptIds()}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getTranscriptIds(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException {
      
    return successResult(store.getTranscriptIds(), null);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStore#getTranscriptIdsInCorpus(String)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getTranscriptIdsInCorpus(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException {
      
    String id = parameters.getString("id");
    if (id == null) return failureResult("No ID specified.");
    String[] ids = store.getTranscriptIdsInCorpus(id);
    return successResult(ids, ids.length == 0?"There are no matching IDs.":null);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStore#getTranscriptIdsWithParticipant(String)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getTranscriptIdsWithParticipant(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException {
      
    String id = parameters.getString("id");
    if (id == null) return failureResult("No ID specified.");
    String[] ids = store.getTranscriptIdsWithParticipant(id);
    return successResult(ids, ids.length == 0?"There are no matching IDs.":null);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStore#countMatchingTranscriptIds(String)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject countMatchingTranscriptIds(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException {
      
    String expression = parameters.getString("expression");
    if (expression == null) return failureResult("No expression specified.");
    return successResult(store.countMatchingTranscriptIds(expression), null);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStore#getMatchingTranscriptIds(String)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getMatchingTranscriptIds(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException {

    Vector<String> errors = new Vector<String>();
    String expression = parameters.getString("expression");
    if (expression == null) errors.add(localize("No expression specified."));
    Integer pageLength = null;
    if (parameters.getString("pageLength") != null) {
      try {
        pageLength = Integer.valueOf(parameters.getString("pageLength"));
      } catch(NumberFormatException x) {
        errors.add(localize("Invalid page length: " + x.getMessage()));
      }
    }
    Integer pageNumber = null;
    if (parameters.getString("pageNumber") != null) {
      try {
        pageNumber = Integer.valueOf(parameters.getString("pageNumber"));
      } catch(NumberFormatException x) {
        errors.add(localize("Invalid page number: " + x.getMessage()));
      }
    }
    if (errors.size() > 0) return failureResult(errors);
    String[] ids = store.getMatchingTranscriptIds(expression, pageLength, pageNumber);
    return successResult(ids, ids.length == 0?"There are no matching IDs.":null);
  }         
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStore#countMatchingAnnotations(String)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject countMatchingAnnotations(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException {
      
    String expression = parameters.getString("expression");
    if (expression == null) return failureResult("No expression specified.");
    return successResult(store.countMatchingAnnotations(expression), null);
  }      
   
  /**
   * Implementation of
   * {@link nzilbb.ag.IGraphStore#getMatchingAnnotations(String,Integer,Integer)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getMatchingAnnotations(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException {
      
    Vector<String> errors = new Vector<String>();
    String expression = parameters.getString("expression");
    if (expression == null) errors.add(localize("No expression specified."));
    Integer pageLength = null;
    if (parameters.getString("pageLength") != null) {
      try {
        pageLength = Integer.valueOf(parameters.getString("pageLength"));
      } catch(NumberFormatException x) {
        errors.add(localize("Invalid page length: {0}", x.getMessage()));
      }
    }
    Integer pageNumber = null;
    if (parameters.getString("pageNumber") != null) {
      try {
        pageNumber = Integer.valueOf(parameters.getString("pageNumber"));
      } catch(NumberFormatException x) {
        errors.add(localize("Invalid page number: {0}", x.getMessage()));
      }
    }
    if (errors.size() > 0) return failureResult(errors);
    Annotation[] annotations = store.getMatchingAnnotations(expression, pageLength, pageNumber);
    return successResult(annotations, annotations.length == 0?"There are no annotations.":null);
  }         

  /**
   * Implementation of
   * {@link nzilbb.ag.GraphStore#aggregateMatchingAnnotations(String,String)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject aggregateMatchingAnnotations(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException {
      
    Vector<String> errors = new Vector<String>();
    String operation = parameters.getString("operation");
    if (operation == null) errors.add(localize("No operation specified."));
    String expression = parameters.getString("expression");
    if (expression == null) errors.add(localize("No expression specified."));
    if (errors.size() > 0) return failureResult(errors);
    String[] values = store.aggregateMatchingAnnotations(operation, expression);
    return successResult(values, values.length == 0?"There are no annotations.":null);
  }         

  /**
   * Implementation of {@link nzilbb.ag.IGraphStore#countAnnotations(String,String,Integer)}
   * @param parameters Request parameter map.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject countAnnotations(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException, GraphNotFoundException {
      
    Vector<String> errors = new Vector<String>();
    String id = parameters.getString("id");
    if (id == null) errors.add(localize("No ID specified."));
    String layerId = parameters.getString("layerId");
    if (layerId == null) errors.add(localize("No layer ID specified."));
    Integer maxOrdinal = null;
    if (parameters.getString("maxOrdinal") != null) {
      try {
        maxOrdinal = Integer.valueOf(parameters.getString("maxOrdinal"));
      } catch(NumberFormatException x) {
        errors.add(localize("Invalid maximum ordinal: {0}", x.getMessage()));
      }
    }
    if (errors.size() > 0) return failureResult(errors);
    return successResult(store.countAnnotations(id, layerId, maxOrdinal), null);
  }
   
  /**
   * Implementation of
   * {@link nzilbb.ag.IGraphStore#getAnnotations(String,String,Integer,Integer,Integer)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getAnnotations(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException, GraphNotFoundException {
      
    Vector<String> errors = new Vector<String>();
    String id = parameters.getString("id");
    if (id == null) errors.add("No ID specified.");
    String layerId = parameters.getString("layerId");
    if (layerId == null) errors.add(localize("No layer ID specified."));
    Integer pageLength = null;
    if (parameters.getString("pageLength") != null) {
      try {
        pageLength = Integer.valueOf(parameters.getString("pageLength"));
      } catch(NumberFormatException x) {
        errors.add(localize("Invalid page length: {0}", x.getMessage()));
      }
    }
    Integer pageNumber = null;
    if (parameters.getString("pageNumber") != null) {
      try {
        pageNumber = Integer.valueOf(parameters.getString("pageNumber"));
      } catch(NumberFormatException x) {
        errors.add(localize("Invalid page number: {0}", x.getMessage()));
      }
    }
    Integer maxOrdinal = null;
    if (parameters.getString("maxOrdinal") != null) {
      try {
        maxOrdinal = Integer.valueOf(parameters.getString("maxOrdinal"));
      } catch(NumberFormatException x) {
        errors.add(localize("Invalid maximum ordinal: {0}", x.getMessage()));
      }
    }
    if (errors.size() > 0) return failureResult(errors);
    Annotation[] annotations = store.getAnnotations(id, layerId, maxOrdinal, pageLength, pageNumber);
    return successResult(annotations, annotations.length == 0?"There are no annotations.":null);
  }

  // TODO getMatchAnnotations
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStore#getAnchors(String,String[])}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getAnchors(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException, GraphNotFoundException {
      
    Vector<String> errors = new Vector<String>();
    String id = parameters.getString("id");
    if (id == null) errors.add(localize("No ID specified."));
    String[] anchorIds = parameters.getStrings("anchorIds");
    if (anchorIds.length == 0) errors.add(localize("No anchor IDs specified."));
    if (errors.size() > 0) return failureResult(errors);
    return successResult(store.getAnchors(id, anchorIds), null);
  }
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStore#getTranscript(String,String[])}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getTranscript(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException, GraphNotFoundException {
      
    Vector<String> errors = new Vector<String>();
    String id = parameters.getString("id");
    if (id == null) errors.add(localize("No ID specified."));
    String[] layerIds = parameters.getStrings("layerIds");
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
                  (warning) -> context.servletLog(warning),
                  (exception) -> exceptions.add(exception));
      JsonReader reader = Json.createReader(
        new InputStreamReader(streams.elementAt(0).getStream(), "UTF-8"));
      JsonObject json = reader.readObject();    
      return successResult(json, null);
    } catch(SerializerNotConfiguredException exception) { // shouldn't happen
      throw new StoreException(exception);
    }
  }

  /**
   * Implementation of {@link nzilbb.ag.GraphStore#getFragment(String,String,String[])}
   * and {@link nzilbb.ag.GraphStore#getFragment(String,double,double,String[])}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getFragment(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException, GraphNotFoundException {
    
    Vector<String> errors = new Vector<String>();
    String id = parameters.getString("id");
    if (id == null) errors.add(localize("No ID specified."));
    String[] layerIds = parameters.getStrings("layerIds");
    String annotationId = parameters.getString("annotationId");
    String startParameter = parameters.getString("start");
    Double start = null;
    if (startParameter != null) {
      try {
        start = Double.valueOf(startParameter);
      } catch(NumberFormatException x) {
        errors.add(localize("Invalid start offset: {0}", x.getMessage()));
      }
    }
    String endParameter = parameters.getString("end");
    Double end = null;
    if (endParameter != null) {
      try {
        end = Double.valueOf(endParameter);
      } catch(NumberFormatException x) {
        errors.add(localize("Invalid end offset: {0}", x.getMessage()));
      }
    }
    if (annotationId == null && (start == null || end == null)) {
      errors.add(localize("Annotation ID not specified.")); // TODO i18n
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
                  (warning) -> context.servletLog(warning),
                  (exception) -> exceptions.add(exception));
      JsonReader reader = Json.createReader(
        new InputStreamReader(streams.elementAt(0).getStream(), "UTF-8"));
      JsonObject json = reader.readObject();    
      return successResult(json, null);
    } catch(SerializerNotConfiguredException exception) { // shouldn't happen
      throw new StoreException(exception);
    }

  }
  // TODO getFragmentSeries
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStore#getMediaTracks()}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getMediaTracks(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException {
      
    return successResult(store.getMediaTracks(), null);
  }      
   
  /**
   * Implementation of {@link nzilbb.ag.IGraphStore#getAvailableMedia(String)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getAvailableMedia(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException, GraphNotFoundException {
      
    String id = parameters.getString("id");
    if (id == null) return failureResult("No ID specified.");

    MediaFile[] media = store.getAvailableMedia(id);
      
    // strip out local file paths
    for (MediaFile file : media) file.setFile(null);
      
    return successResult(media, media.length == 0?"There is no media.":null);
  }

  /**
   * Implementation of {@link nzilbb.ag.IGraphStore#getAvailableMedia(String)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getMedia(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException, GraphNotFoundException {
      
    Vector<String> errors = new Vector<String>();
    String id = parameters.getString("id");
    if (id == null) errors.add(localize("No ID specified."));
    String trackSuffix = parameters.getString("trackSuffix"); // optional
    String mimeType = parameters.getString("mimeType");
    if (mimeType == null) errors.add(localize("No Content Type specified."));
    Double startOffset = null;
    if (parameters.getString("startOffset") != null) {
      try {
        startOffset = Double.valueOf(parameters.getString("startOffset"));
      } catch(NumberFormatException x) {
        errors.add(localize("Invalid start offset: {0}", x.getMessage()));
      }
    }
    Double endOffset = null;
    if (parameters.getString("endOffset") != null) {
      try {
        endOffset = Double.valueOf(parameters.getString("endOffset"));
      } catch(NumberFormatException x) {
        errors.add(localize("Invalid end offset: {0}", x.getMessage()));
      }
    }
    if (startOffset == null && endOffset == null) {
      if (errors.size() > 0) return failureResult(errors);
      return successResult(store.getMedia(id, trackSuffix, mimeType), null);
    } else {
      if (startOffset == null) errors.add(localize("Start offset not specified."));
      if (endOffset == null) errors.add(localize("End offset not specified."));
      if (endOffset <= startOffset)
        errors.add(localize("Start offset ({0,number,#.###}) must be before end offset ({1,number,#.###})", startOffset, endOffset));
      if (errors.size() > 0) return failureResult(errors);
      return successResult(store.getMedia(id, trackSuffix, mimeType, startOffset, endOffset), null);
    }
  }

  /**
   * Implementation of {@link nzilbb.ag.IGraphStore#getEpisodeDocuments(String)}
   * @param request The HTTP request.
   * @param request The HTTP response.
   * @param store A graph store object.
   * @return A JSON response for returning to the caller.
   */
  protected JsonObject getEpisodeDocuments(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException, GraphNotFoundException {
      
    String id = parameters.getString("id");
    if (id == null) return failureResult("No ID specified.");

    MediaFile[] media = store.getEpisodeDocuments(id);
      
    // strip out local file paths
    for (MediaFile file : media) file.setFile(null);
      
    return successResult(media, media.length == 0?"There are no documents.":null);
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
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException, GraphNotFoundException {
    SerializationDescriptor[] descriptors = store.getSerializerDescriptors();
    return successResult(
      descriptors, descriptors.length == 0?"There are no serializers.":null);
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
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException, GraphNotFoundException {
    SerializationDescriptor[] descriptors = store.getDeserializerDescriptors();
    return successResult(
      descriptors, descriptors.length == 0?"There are no deserializers.":null);
  }

  /**
   * Lists descriptors of all annotators that are installed.
   * <p> Annotators are modules that perform automated annations of existing transcripts.
   * @return A list of the descriptors of all registered annotators.
   * @throws StoreException If an error prevents the descriptors from being listed.
   * @throws PermissionException If listing the deserializers is not permitted.
   */
  protected JsonObject getAnnotatorDescriptors(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException, GraphNotFoundException {
    AnnotatorDescriptor[] descriptors = store.getAnnotatorDescriptors();
    return successResult(
      descriptors, descriptors.length == 0?"There are no annotators.":null);
  }
  
  /**
   * Gets a descriptor of the annotator with the given ID (annotatorId).
   * <p> Annotators are modules that perform automated annations of existing transcripts.
   * @return A list of the descriptors of all registered annotators.
   * @throws StoreException If an error prevents the descriptors from being listed.
   * @throws PermissionException If listing the deserializers is not permitted.
   */
  protected JsonObject getAnnotatorDescriptor(
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException, GraphNotFoundException {
    String annotatorId = parameters.getString("annotatorId");
    if (annotatorId == null) return failureResult("No ID specified.");
    AnnotatorDescriptor descriptor = store.getAnnotatorDescriptor(annotatorId);
    if (descriptor == null) return failureResult("Invalid ID: " + annotatorId);
    return successResult(
      descriptor, null);
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
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException, GraphNotFoundException {
    Vector<String> errors = new Vector<String>();
    // get/validate the parameters
    String annotatorId = parameters.getString("annotatorId");
    if (annotatorId == null) errors.add(localize("No Annotator ID specified."));
    if (errors.size() > 0) return failureResult(errors);
    
    return successResult(
      store.getAnnotatorTasks(annotatorId), null);
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
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException, GraphNotFoundException {
    Vector<String> errors = new Vector<String>();
    // get/validate the parameters
    String taskId = parameters.getString("taskId");
    if (taskId == null) errors.add(localize("No ID specified."));
    if (errors.size() > 0) return failureResult(errors);
    
    return successResult(
      store.getAnnotatorTaskParameters(taskId), null);
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
    RequestParameters parameters, SqlGraphStoreAdministration store)
    throws IOException, StoreException, PermissionException, GraphNotFoundException {
    AnnotatorDescriptor[] descriptors = store.getTranscriberDescriptors();
    return successResult(
      descriptors, descriptors.length == 0?"There are no transcribers.":null);
  }

  private static final long serialVersionUID = 1;
} // end of class Store
