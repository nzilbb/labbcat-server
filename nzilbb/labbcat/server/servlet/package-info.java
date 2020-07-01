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
 * <p>Servlet providing an HTTP-based API.
 * <p>All LaBB-CAT requests for which the the <q>Accept</q> HTTP header is set to
 * <q>application/json</q> return a JSON response with the same standard envelope structure:
 * <dl>
 *  <dt>title</dt> <dd>(string) The title of the LaBB-CAT instance.</dd>
 *  <dt>version</dt> <dd>(string) The version of the LaBB-CAT instance</dd>
 *  <dt>code</dt> <dd>(int) 0 if the request was successful, 1 if there was a problem</dd>
 *  <dt>messages</dt> <dd>An array of message strings.</dd>
 *  <dt>errors</dt> <dd>An array of error message strings.</dd>
 *  <dt>model</dt> <dd>The result of the request, which may be a JSON object, JSON array,
 *   or a simple type.</dd>
 * </dl>
 * <p>e.g. the response to 
 * <tt>http://localhost:8080/labbcat/api/store/getLayer?id=transcript</tt>
 * might be:
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
 * <p> Graph store functions include:
 * <ul>
 *  <li><a href="#IGraphStoreQuery"> IGraphStoreQuery functions </a></li>
 *  <li><a href="#IGraphStore"> IGraphStore functions </a></li>
 *  <li><a href="#OtherFunctions"> and other LaBB-CAT-specific functions. </a></li>
 * </ul>
<!-- Based on IGraphStoreQuery javadoc -->
<section role="region">
  <ul class="blockList">
    <li class="blockList">
      <h3 id="IGraphStoreQuery">/api/store/&hellip; <a href="https://nzilbb.github.io/ag/javadoc/nzilbb/ag/IGraphStoreQuery.html">nzilbb.ag.IGraphStoreQuery</a> functions:</h3>
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
      <a id="getParticipant(java.lang.String)">
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
                  <li><code>id NOT MATCHES 'Ada.+' AND my('corpus').label = 'CC'</code></li>
                  <li><code>list('transcript_rating').length &gt; 2</code></li>
                  <li><code>list('participant_rating').length = 0</code></li>
                  <li><code>'labbcat' NOT IN annotators('transcript_rating')</code></li>
                  <li><code>my('participant_gender').label = 'NA'</code></li>
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
                  <li><code>id NOT MATCHES 'Ada.+' AND my('corpus').label = 'CC'</code></li>
                  <li><code>list('transcript_rating').length &gt; 2</code></li>
                  <li><code>list('participant_rating').length = 0</code></li>
                  <li><code>'labbcat' NOT IN annotators('transcript_rating')</code></li>
                  <li><code>my('participant_gender').label = 'NA'</code></li>
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
                  <li><code>'Robert' IN labels('who')</code></li>
                  <li><code>my('corpus').label IN ('CC', 'IA', 'MU')</code></li>
                  <li><code>my('episode').label = 'Ada Aitcheson'</code></li>
                  <li><code>my('transcript_scribe').label = 'Robert'</code></li>
                  <li><code>my('participant_languages').label = 'en'</code></li>
                  <li><code>my('noise').label = 'bell'</code></li>
                  <li><code>'en' IN labels('transcript_languages')</code></li>
                  <li><code>'en' IN labels('participant_languages')</code></li>
                  <li><code>'bell' IN labels('noise')</code></li>
                  <li><code>list('transcript_languages').length gt; 1</code></li>
                  <li><code>list('participant_languages').length gt; 1</code></li>
                  <li><code>list('transcript').length gt; 100</code></li>
                  <li><code>'Robert' IN annotators('transcript_rating')</code></li>
                  <li><code>id NOT MATCHES 'Ada.+' AND my('corpus').label = 'CC' AND 'Robert' IN labels('who')</code></li>
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
                  <li><code>'Robert' IN labels('who')</code></li>
                  <li><code>my('corpus').label IN ('CC', 'IA', 'MU')</code></li>
                  <li><code>my('episode').label = 'Ada Aitcheson'</code></li>
                  <li><code>my('transcript_scribe').label = 'Robert'</code></li>
                  <li><code>my('participant_languages').label = 'en'</code></li>
                  <li><code>my('noise').label = 'bell'</code></li>
                  <li><code>'en' IN labels('transcript_languages')</code></li>
                  <li><code>'en' IN labels('participant_languages')</code></li>
                  <li><code>'bell' IN labels('noise')</code></li>
                  <li><code>list('transcript_languages').length gt; 1</code></li>
                  <li><code>list('participant_languages').length gt; 1</code></li>
                  <li><code>list('transcript').length gt; 100</code></li>
                  <li><code>'Robert' IN annotators('transcript_rating')</code></li>
                  <li><code>id NOT MATCHES 'Ada.+' AND my('corpus').label = 'CC' AND 'Robert' IN labels('who')</code></li>
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
                  <li><code>layer.id = 'orthography' AND my('who').label = 'Robert' AND
                      my('utterances').start.offset = 12.345</code></li> 
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
                  <li><code>my('who').label = 'Robert' AND my('utterances').start.offset = 12.345</code></li>
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
            <dd>The identified transcript.</dd>
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
                  <li><code>layer.id = 'orthography' AND my('who').label = 'Robert' AND
                      my('utterances').start.offset = 12.345</code></li> 
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
                  <li><code>my('who').label = 'Robert' AND my('utterances').start.offset = 12.345</code></li>
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
    </li>
  </ul>
</section>

<!-- Based on IGraphStore javadoc -->

<section role="region">
  <ul class="blockList">
    <li class="blockList">
      <h3 id="IGraphStore">/api/edit/store/&hellip; <a href="https://nzilbb.github.io/ag/javadoc/nzilbb/ag/IGraphStore.html">nzilbb.ag.IGraphStore</a> functions:</h3>
      <a id="createAnnotation(java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.Integer,java.lang.String)">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/edit/store/createAnnotation</h4>
          <div class="block">Creates an annotation starting at <var>from</var> and ending at <var>to</var>.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>id</code> - The ID of the transcript.</dd>
            <dd><code>fromId</code> - The start anchor's ID.</dd>
            <dd><code>toId</code> - The end anchor's ID.</dd>
            <dd><code>layerId</code> - The layer ID of the resulting annotation.</dd>
            <dd><code>label</code> - The label of the resulting annotation.</dd>
            <dd><code>confidence</code> - The confidence rating.</dd>
            <dd><code>parentId</code> - The new annotation's parent's ID.</dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>The ID of the new annotation.</dd>
          </dl>
        </li>
      </ul>
      <a id="destroyAnnotation(java.lang.String,java.lang.String)">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/edit/store/destroyAnnotation</h4>
          <div class="block">Destroys the annotation with the given ID.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>id</code> - The ID of the transcript.</dd>
            <dd><code>annotationId</code> - The annotation's ID.</dd>
          </dl>
        </li>
      </ul>
      <a id="deleteTranscript(java.lang.String)">
        <!--   -->
      </a>
      <ul class="blockListLast">
        <li class="blockList">
          <h4>/api/edit/store/deleteTranscript</h4>
          <pre class="methodSignature">void&nbsp;deleteTranscript&#8203;(String&nbsp;id)
            throws <a href="StoreException.html" title="class in nzilbb.ag">StoreException</a>,
            <a href="PermissionException.html" title="class in nzilbb.ag">PermissionException</a>,
            <a href="GraphNotFoundException.html" title="class in nzilbb.ag">GraphNotFoundException</a></pre>
          <div class="block">Deletes the given transcript, and all associated files.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>id</code> - The ID transcript to delete.</dd>
          </dl>
        </li>
      </ul>
    </li>
  </ul>
</section>
 * 
 * <section role="region"><ul class="blockList"><li class="blockList">
 * <h3 id="OtherFunctions">Other functions include:</h3><ul class="blockList">
 * 
 * <li class="blockList"><h4 id="/api/serialize/graphs">/api/serialize/graphs</h4>
 *  <div> Converts transcript fragments. The request method can be <b> GET </b> or <b> POST </b>
 *  <p> This expects an array of graph <i>id</i>s, <i>start</i> times and <i>end</i> times, 
 *  a list of <i>layerId</i>s in include, and a <i>mimetype</i>.
 *   <dl>
 *     <dt><span class="paramLabel">Parameters:</span></dt>
 *     <dd><code>mimeType</code> - Content-type of the format to serialize to.</dd>
 *     <dd><code>layerId</code> - A list of layer IDs to include in the serialization.</dd>
 *     <dd><code>id</code> - One or more graph IDs.</dd>
 *     <dd><code>name</code> - Content-type of the format to serialize to.</dd>
 *     <dd><code>mimeType</code> - Optional name of the collection.</dd>
 *   </dl>
 *  <p><b>Output</b>: A each of the transcript fragments 
 *  specified by the input parameters converted to the given 
 *  format.  
 *  <p> This may be a single file or multiple files, depending on
 *  the converter behaviour and how many fragments are specified.
 *  If there is only one, the file in returned as the response to 
 *  the request.  If there are more than one, the response is a
 *  zipfile containing the output files.
 *  </div>
 * </li>
 * 
 * <li class="blockList"><h4 id="/api/admin/corpora">/api/admin/corpora[/<var>corpus_name</var>]</h4>
 *  <div> Allows administration (Create/Read/Update/Delete) of corpus records via
 *  JSON-encoded objects with the following attributes:
 *   <ul>
 *    <li> <q> corpus_id </q> : The database key for the record. </li>
 *    <li> <q> corpus_name </q> : The name of the corpus. </li>
 *    <li> <q> corpus_language </q> : The ISO 639-1 code for the default language. </li>
 *    <li> <q> corpus_description </q> : The description of the corpus. </li>
 *    <li> <q> _cantDelete </q> : This is not a database field, but rather is present in
 *         records returned from the server that can not currently be deleted; 
 *         a string representing the reason the record can't be deleted. </li>
 *   </ul>
 *  The following operations, specified by the HTTP method, are supported:
 *   <ul>
 *    <li><b> POST </b>
 *    - Create a new record.
 *     <ul>
 *      <li><em> Request Body </em> - a JSON-encoded object representing the new record
 *       (excluding <var>corpus_id</var>). </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object representing the new record (including <var>corpus_id</var>). </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was sucessfully created. </li>
 *         <li><em> 409 </em> : The record could not be added because it was already there. </li> 
 *        </ul>
 *      </li>
 *     </ul></li> 
 * 
 *    <li><b> GET </b>
 *    - Read the records. 
 *     <ul>
 *      <li><em> Parameters </em>
 *        <ul>
 *         <li><em> pageNumber </em> (integer) : The (zero-based) page to return. </li>
 *         <li><em> pageLength </em> (integer) : How many rows per page (default is 20). </li>
 *         <li><em> Accept </em> (string) : Equivalent of the "Accept" request header (see below). </li>
 *        </ul>
 *      </li>
 *      <li><em> "Accept" request header/parameter </em> "text/csv" to return records as
 *       Comma Separated Values. If not specified, records are returned as a JSON-encoded
 *       array of objects.</li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as a
 *       corresponding list of records.  </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The records could be listed. </li>
 *        </ul>
 *      </li>
 *     </ul></li> 
 *    
 *    <li><b> PUT </b>
 *    - Update an existing record, specified by the <var> corpus_name </var> given in the
 *    request body.
 *     <ul>
 *      <li><em> Request Body </em> - a JSON-encoded object representing the record. </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object representing the record. </li> 
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was sucessfully updated. </li>
 *         <li><em> 404 </em> : The record was not found. </li>
 *        </ul>
 *      </li>
 *     </ul></li> 
 *    
 *    <li><b> DELETE </b>
 *    - Delete an existing record.
 *     <ul>
 *      <li><em> Request Path </em> - /api/admin/corpora/<var>corpus_name</var> where 
 *          <var> corpus_name </var> is the name of the corpus to delete.</li>
 *      <li><em> Response Body </em> - the standard JSON envelope, including a message if
 *          the request succeeds or an error explaining the reason for failure. </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was sucessfully deleted. </li>
 *         <li><em> 400 </em> : No <var> corpus_id </var> was specified in the URL path,
 *             or the record exists but could not be deleted. </li> 
 *         <li><em> 404 </em> : The record was not found. </li>
 *        </ul>
 *      </li>
 *     </ul></li> 
 *   </ul>
 *  </div>
 * </li>
 * 
 * <li class="blockList"><h4 id="/api/admin/projects">/api/admin/projects[/<var>project</var>]</h4>
 *  <div> Allows administration (Create/Read/Update/Delete) of project records via
 *  JSON-encoded objects with the following attributes:
 *   <ul>
 *    <li> <q> project_id </q> : The database key for the record. </li>
 *    <li> <q> project </q> : The name of the project. </li>
 *    <li> <q> description </q> : The description of the project. </li>
 *    <li> <q> _cantDelete </q> : This is not a database field, but rather is present in
 *         records returned from the server that can not currently be deleted; 
 *         a string representing the reason the record can't be deleted. </li>
 *   </ul>
 *  The following operations, specified by the HTTP method, are supported:
 *   <ul>
 *    <li><b> POST </b>
 *    - Create a new record.
 *     <ul>
 *      <li><em> Request Body </em> - a JSON-encoded object representing the new record
 *       (excluding <var>project_id</var>). </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object representing the new record (including <var>project_id</var>). </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was sucessfully created. </li>
 *         <li><em> 409 </em> : The record could not be added because it was already there. </li> 
 *        </ul>
 *      </li>
 *     </ul></li> 
 * 
 *    <li><b> GET </b>
 *    - Read the records. 
 *     <ul>
 *      <li><em> Parameters </em>
 *        <ul>
 *         <li><em> pageNumber </em> (integer) : The (zero-based) page to return. </li>
 *         <li><em> pageLength </em> (integer) : How many rows per page (default is 20). </li>
 *         <li><em> Accept </em> (string) : Equivalent of the "Accept" request header (see below). </li>
 *        </ul>
 *      </li>
 *      <li><em> "Accept" request header/parameter </em> "text/csv" to return records as
 *       Comma Separated Values. If not specified, records are returned as a JSON-encoded
 *       array of objects.</li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as a
 *       corresponding list of records.  </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The records could be listed. </li>
 *        </ul>
 *      </li>
 *     </ul></li> 
 *    
 *    <li><b> PUT </b>
 *    - Update an existing record, specified by the <var> project </var> given in the
 *    request body.
 *     <ul>
 *      <li><em> Request Body </em> - a JSON-encoded object representing the record. </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object representing the record. </li> 
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was sucessfully updated. </li>
 *         <li><em> 404 </em> : The record was not found. </li>
 *        </ul>
 *      </li>
 *     </ul></li> 
 *    
 *    <li><b> DELETE </b>
 *    - Delete an existing record.
 *     <ul>
 *      <li><em> Request Path </em> - /api/admin/projects/<var>project</var> where 
 *          <var> project </var> is the database ID of the record to delete.</li>
 *      <li><em> Response Body </em> - the standard JSON envelope, including a message if
 *          the request succeeds or an error explaining the reason for failure. </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was sucessfully deleted. </li>
 *         <li><em> 400 </em> : No <var> project </var> was specified in the URL path,
 *             or the record exists but could not be deleted. </li> 
 *         <li><em> 404 </em> : The record was not found. </li>
 *        </ul>
 *      </li>
 *     </ul></li> 
 *   </ul>
 *  </div>
 * </li>
 * 
 * <li class="blockList"><h4 id="/api/admin/mediatracks">/api/admin/mediatracks[/<var>suffix</var>]</h4>
 *  <div> Allows administration (Create/Read/Update/Delete) of media track records via
 *  JSON-encoded objects with the following attributes:
 *   <ul>
 *    <li> <q> suffix </q> : The suffix associated with the media track. </li>
 *    <li> <q> description </q> : The description of the track. </li>
 *    <li> <q> display_order </q> : The position of the track amongst other tracks. </li>
 *    <li> <q> _cantDelete </q> : This is not a database field, but rather is present in
 *         records returned from the server that can not currently be deleted; 
 *         a string representing the reason the record can't be deleted. </li>
 *   </ul>
 *  The following operations, specified by the HTTP method, are supported:
 *   <ul>
 *    <li><b> POST </b>
 *    - Create a new record.
 *     <ul>
 *      <li><em> Request Body </em> - a JSON-encoded object representing the new record
 *       (excluding <var>mediaTrack_id</var>). </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object representing the new record (including <var>mediaTrack_id</var>). </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was sucessfully created. </li>
 *         <li><em> 409 </em> : The record could not be added because it was already there. </li> 
 *        </ul>
 *      </li>
 *     </ul></li> 
 * 
 *    <li><b> GET </b>
 *    - Read the records. 
 *     <ul>
 *      <li><em> Parameters </em>
 *        <ul>
 *         <li><em> pageNumber </em> (integer) : The (zero-based) page to return. </li>
 *         <li><em> pageLength </em> (integer) : How many rows per page (default is 20). </li>
 *         <li><em> Accept </em> (string) : Equivalent of the "Accept" request header (see below). </li>
 *        </ul>
 *      </li>
 *      <li><em> "Accept" request header/parameter </em> "text/csv" to return records as
 *       Comma Separated Values. If not specified, records are returned as a JSON-encoded
 *       array of objects.</li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as a
 *       corresponding list of records.  </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The records could be listed. </li>
 *        </ul>
 *      </li>
 *     </ul></li> 
 *    
 *    <li><b> PUT </b>
 *    - Update an existing record, specified by the <var> mediaTrack </var> given in the
 *    request body.
 *     <ul>
 *      <li><em> Request Body </em> - a JSON-encoded object representing the record. </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object representing the record. </li> 
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was sucessfully updated. </li>
 *         <li><em> 404 </em> : The record was not found. </li>
 *        </ul>
 *      </li>
 *     </ul></li> 
 *    
 *    <li><b> DELETE </b>
 *    - Delete an existing record.
 *     <ul>
 *      <li><em> Request Path </em> - /api/admin/mediatracks/<var>suffix</var> where 
 *          <var> suffix </var> is the suffix of the media track to delete.</li>
 *      <li><em> Response Body </em> - the standard JSON envelope, including a message if
 *          the request succeeds or an error explaining the reason for failure. </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was sucessfully deleted. </li>
 *         <li><em> 400 </em> : No <var> mediaTrack </var> was specified in the URL path,
 *             or the record exists but could not be deleted. </li> 
 *         <li><em> 404 </em> : The record was not found. </li>
 *        </ul>
 *      </li>
 *     </ul></li> 
 *   </ul>
 *  </div>
 * </li>
 *
 * <li class="blockList"><h4 id="/api/admin/roles">/api/admin/roles[/<var>role_id</var>]</h4>
 *  <div> Allows administration (Create/Read/Update/Delete) of user role records via
 *  JSON-encoded objects with the following attributes:
 *   <ul>
 *    <li> <q> role_id </q> : The name of the role. </li>
 *    <li> <q> description </q> : The description of the role. </li>
 *    <li> <q> _cantDelete </q> : This is not a database field, but rather is present in
 *         records returned from the server that can not currently be deleted; 
 *         a string representing the reason the record can't be deleted. </li>
 *   </ul>
 *  The following operations, specified by the HTTP method, are supported:
 *   <ul>
 *    <li><b> POST </b>
 *    - Create a new record.
 *     <ul>
 *      <li><em> Request Body </em> - a JSON-encoded object representing the new record
 *       (excluding <var>role_id</var>). </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object representing the new record (including <var>role_id</var>). </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was sucessfully created. </li>
 *         <li><em> 409 </em> : The record could not be added because it was already there. </li> 
 *        </ul>
 *      </li>
 *     </ul></li> 
 * 
 *    <li><b> GET </b>
 *    - Read the records. 
 *     <ul>
 *      <li><em> Parameters </em>
 *        <ul>
 *         <li><em> pageNumber </em> (integer) : The (zero-based) page to return. </li>
 *         <li><em> pageLength </em> (integer) : How many rows per page (default is 20). </li>
 *         <li><em> Accept </em> (string) : Equivalent of the "Accept" request header (see below). </li>
 *        </ul>
 *      </li>
 *      <li><em> "Accept" request header/parameter </em> "text/csv" to return records as
 *       Comma Separated Values. If not specified, records are returned as a JSON-encoded
 *       array of objects.</li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as a
 *       corresponding list of records.  </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The records could be listed. </li>
 *        </ul>
 *      </li>
 *     </ul></li> 
 *    
 *    <li><b> PUT </b>
 *    - Update an existing record, specified by the <var> role </var> given in the
 *    request body.
 *     <ul>
 *      <li><em> Request Body </em> - a JSON-encoded object representing the record. </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object representing the record. </li> 
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was sucessfully updated. </li>
 *         <li><em> 404 </em> : The record was not found. </li>
 *        </ul>
 *      </li>
 *     </ul></li> 
 *    
 *    <li><b> DELETE </b>
 *    - Delete an existing record.
 *     <ul>
 *      <li><em> Request Path </em> - /api/admin/roles/<var>role_id</var> where 
 *          <var> role_id </var> is the ID of the record to delete.</li>
 *      <li><em> Response Body </em> - the standard JSON envelope, including a message if
 *          the request succeeds or an error explaining the reason for failure. </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was sucessfully deleted. </li>
 *         <li><em> 400 </em> : No <var> role </var> was specified in the URL path,
 *             or the record exists but could not be deleted. </li> 
 *         <li><em> 404 </em> : The record was not found. </li>
 *        </ul>
 *      </li>
 *     </ul></li> 
 *   </ul>
 *  </div>
 * </li>
 * 
 * <li class="blockList"><h4 id="/api/admin/roles/permissions">/api/admin/roles/permissions[/<var>role_id</var>[/<var>entity</var>]]</h4>
 *  <div> Allows administration (Create/Read/Update/Delete) of user role permission records via
 *  JSON-encoded objects with the following attributes:
 *   <ul>
 *    <li> <q> role_id </q> : The ID of the role this permission applies to. </li>
 *    <li> <q> entity </q> : The media entity this permission applies to - a string made
 *         up of "t" (transcript), "a" (audio), "v" (video), or "i" (image). </li>
 *    <li> <q> attribute_name </q> : Name of a transcript attribute for which the value determines
 *         access. This is either a valid transcript attribute name (i.e. excluding the
 *         "transcript_" prefix in the layer ID), or "corpus". </li>
 *    <li> <q> value_pattern </q> : Regular expression for matching against the 
 *         <var> attribute_name </var> value. If the regular expression matches the value,
 *         access is allowed. </li>
 *    <li> <q> _cantDelete </q> : This is not a database field, but rather is present in
 *         records returned from the server that can not currently be deleted; 
 *         a string representing the reason the record can't be deleted. </li>
 *   </ul>
 *  The following operations, specified by the HTTP method, are supported:
 *   <ul>
 *    <li><b> POST </b>
 *    - Create a new record.
 *     <ul>
 *      <li><em> Request Body </em> - a JSON-encoded object representing the new record
 *       (excluding <var>role_id</var>). </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object representing the new record (including <var>role_id</var>). </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was sucessfully created. </li>
 *         <li><em> 409 </em> : The record could not be added because it was already there. </li> 
 *        </ul>
 *      </li>
 *     </ul></li> 
 * 
 *    <li><b> GET </b>
 *    - Read the records. 
 *     <ul>
 *      <li><em> Request Path </em> - /api/admin/roles/permissions/<var>role_id</var> where 
 *          <var> role_id </var> is the ID of the role the permissions belong to.</li>
 *      <li><em> Parameters </em>
 *        <ul>
 *         <li><em> pageNumber </em> (integer) : The (zero-based) page to return. </li>
 *         <li><em> pageLength </em> (integer) : How many rows per page (default is 20). </li>
 *         <li><em> Accept </em> (string) : Equivalent of the "Accept" request header (see below). </li>
 *        </ul>
 *      </li>
 *      <li><em> "Accept" request header/parameter </em> "text/csv" to return records as
 *       Comma Separated Values. If not specified, records are returned as a JSON-encoded
 *       array of objects.</li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as a
 *       corresponding list of records.  </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The records could be listed. </li>
 *        </ul>
 *      </li>
 *     </ul></li> 
 *    
 *    <li><b> PUT </b>
 *    - Update an existing record, specified by the <var> role </var> given in the
 *    request body.
 *     <ul>
 *      <li><em> Request Body </em> - a JSON-encoded object representing the record. </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object representing the record. </li> 
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was sucessfully updated. </li>
 *         <li><em> 404 </em> : The record was not found. </li>
 *        </ul>
 *      </li>
 *     </ul></li> 
 *    
 *    <li><b> DELETE </b>
 *    - Delete an existing record.
 *     <ul>
 *      <li><em> Request Path </em> - /api/admin/roles/permissions/<var>role_id</var>/<var>entity</var>  
 *          where <var> role_id </var> is the ID of the role the permissions belong to and
 *          <var> entity </var> is the entity to delete the permission for..</li>
 *      <li><em> Response Body </em> - the standard JSON envelope, including a message if
 *          the request succeeds or an error explaining the reason for failure. </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was sucessfully deleted. </li>
 *         <li><em> 400 </em> : No <var> role </var> was specified in the URL path,
 *             or the record exists but could not be deleted. </li> 
 *         <li><em> 404 </em> : The record was not found. </li>
 *        </ul>
 *      </li>
 *     </ul></li> 
 *   </ul>
 *  </div>
 * </li>
 * 
 * <li class="blockList"><h4 id="/api/admin/systemattributes">/api/admin/systemattributes</h4>
 *  <div> Allows administration (Read/Update) of system attribute records via
 *  JSON-encoded objects with the following attributes:
 *   <ul>
 *    <li> <q> attribute </q> : ID of the attribute. </li>
 *    <li> <q> type </q> : The type of the attribute - "string", "integer", "boolean",
 *                         "select", etc.  </li>
 *    <li> <q> style </q> : Style definition which depends on <q> type </q> - e.g. whether
 *                          the "boolean" is shown as a checkbox or radio buttons, etc.  </li>
 *    <li> <q> label </q> : User-facing label for the attribute. </li>
 *    <li> <q> description </q> : User-facing (long) description of the attribute. </li>
 *    <li> <q> options </q> : If <q> type </q> is "select", this is an object defining the
 *                            valid options for the attribute, where the object key is the
 *                            attribute value and the key's value is the user-facing label
 *                            for the option.  </li> 
 *    <li> <q> value </q> : The value of the attribute. </li>
 *   </ul>
 *  The following operations, specified by the HTTP method, are supported:
 *   <ul>
 *    <li><b> GET </b>
 *    - Read the records. 
 *     <ul>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as a
 *       corresponding list of records.  </li>
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The records could be listed. </li>
 *        </ul>
 *      </li>
 *     </ul></li> 
 *    
 *    <li><b> PUT </b>
 *    - Update an existing record, specified by the <var> systemAttribute </var> given in the
 *    request body.
 *     <ul>
 *      <li><em> Request Body </em> - a JSON-encoded object representing the record. </li>
 *      <li><em> Response Body </em> - the standard JSON envelope, with the model as an
 *       object representing the record. </li> 
 *      <li><em> Response Status </em>
 *        <ul>
 *         <li><em> 200 </em> : The record was sucessfully updated. </li>
 *         <li><em> 400 </em> : The record has type == "readonly" found. </li>
 *         <li><em> 404 </em> : The record was not found. </li>
 *        </ul>
 *      </li>
 *     </ul></li> 
 *   </ul>
 *  </div>
 * </li>
 * 
 * </ul></li></ul></section>
 *
 */
package nzilbb.labbcat.server.servlet;
