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
 * Graph store implementations include:
<!-- Based on IGraphStoreQuery javadoc -->
<section role="region">
  <ul class="blockList">
    <li class="blockList"><a id="method.detail">
        <!--   -->
      </a>
      <h3>/api/store/&hellip; <a href="https://nzilbb.github.io/ag/javadoc/nzilbb/ag/IGraphStoreQuery.html">nzilbb.ag.IGraphStoreQuery</a> functions:</h3>
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
      <a id="getGraphIds()">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/getGraphIds</h4>
          <div class="block">Gets a list of graph IDs.</div>
          <dl>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>A list of graph IDs.</dd>
          </dl>
        </li>
      </ul>
      <a id="getGraphIdsInCorpus(java.lang.String)">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/getGraphIdsInCorpus</h4>
          <div class="block">Gets a list of graph IDs in the given corpus.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>id</code> - A corpus ID.</dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>A list of graph IDs.</dd>
          </dl>
        </li>
      </ul>
      <a id="getGraphIdsWithParticipant(java.lang.String)">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/getGraphIdsWithParticipant</h4>
          <div class="block">Gets a list of IDs of graphs that include the given participant.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>id</code> - A participant ID.</dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>A list of graph IDs.</dd>
          </dl>
        </li>
      </ul>
      <a id="countMatchingGraphIds(java.lang.String)">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/countMatchingGraphIds</h4>
          <div class="block">Counts the number of graphs that match a particular pattern.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>expression</code> - An expression that determines which graphs match.
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
            <dd>The number of matching graphs.</dd>
          </dl>
        </li>
      </ul>
      <a id="getMatchingGraphIds(java.lang.String,java.lang.Integer,java.lang.Integer,java.lang.String)">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/getMatchingGraphIds</h4>
          <div class="block"><p>Gets a list of IDs of graphs that match a particular pattern.</p>
            <p>The results can be exhaustive, by omitting pageLength and pageNumber, or they
              can be a subset (a 'page') of results, by given pageLength and pageNumber values.</p>
            <p>The order of the list can be specified.  If ommitted, the graphs are listed in ID
              order.</p></div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>expression</code> - An expression that determines which graphs match.
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
              expressions, which may be appended by " ASC" or " DESC", or absent for graph ID order.</dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>A list of graph IDs.</dd>
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
            <dd><code>expression</code> - An expression that determines which graphs match.
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
          <div class="block">Gets the number of annotations on the given layer of the given graph.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>id</code> - The ID of the graph.</dd>
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
          <div class="block">Gets the annotations on the given layer of the given graph.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>id</code> - The ID of the graph.</dd>
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
          <div class="block">Gets the given anchors in the given graph.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>id</code> - The ID of the graph.</dd>
            <dd><code>anchorIds</code> - A list of anchor IDs, passed by specifying the <code>anchorIds</code> parameter multiple times, once for each anchor.</dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>A (possibly empty) array of anchors.</dd>
          </dl>
        </li>
      </ul>
      <a id="getGraph(java.lang.String,java.lang.String[])">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/store/getGraph</h4>
          <div class="block">Gets a graph given its ID, containing only the given layers.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>id</code> - The given graph ID.</dd>
            <dd><code>layerIds</code> - The IDs of the layers to load, passed by specifying the <code>layerIds</code> multiple times, once for each layer, or absent if only graph data is required.</dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>The identified graph.</dd>
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
          <div class="block">List the media available for the given graph.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>id</code> - The graph ID.</dd>
            <dt><span class="returnLabel">Returns:</span></dt>
            <dd>List of media files available for the given graph.</dd>
          </dl>
        </li>
      </ul>
    </li>
  </ul>
</section>

<!-- Based on IGraphStore javadoc -->

<section role="region">
  <ul class="blockList">
    <li class="blockList"><a id="method.detail">
        <!--   -->
      </a>
      <h3>/api/edit/store/&hellip; <a href="https://nzilbb.github.io/ag/javadoc/nzilbb/ag/IGraphStore.html">nzilbb.ag.IGraphStore</a> functions:</h3>
      <a id="createAnnotation(java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.Integer,java.lang.String)">
        <!--   -->
      </a>
      <ul class="blockList">
        <li class="blockList">
          <h4>/api/edit/store/createAnnotation</h4>
          <div class="block">Creates an annotation starting at <var>from</var> and ending at <var>to</var>.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>id</code> - The ID of the graph.</dd>
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
          <h4>destroyAnnotation</h4>
          <div class="block">Destroys the annotation with the given ID.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>id</code> - The ID of the graph.</dd>
            <dd><code>annotationId</code> - The annotation's ID.</dd>
          </dl>
        </li>
      </ul>
      <a id="deleteGraph(java.lang.String)">
        <!--   -->
      </a>
      <ul class="blockListLast">
        <li class="blockList">
          <h4>deleteGraph</h4>
          <pre class="methodSignature">void&nbsp;deleteGraph&#8203;(String&nbsp;id)
            throws <a href="StoreException.html" title="class in nzilbb.ag">StoreException</a>,
            <a href="PermissionException.html" title="class in nzilbb.ag">PermissionException</a>,
            <a href="GraphNotFoundException.html" title="class in nzilbb.ag">GraphNotFoundException</a></pre>
          <div class="block">Deletes the given graph, and all associated files.</div>
          <dl>
            <dt><span class="paramLabel">Parameters:</span></dt>
            <dd><code>id</code> - The ID graph to save.</dd>
          </dl>
        </li>
      </ul>
    </li>
  </ul>
</section>
 *
 */
package nzilbb.labbcat.server.servlet;
