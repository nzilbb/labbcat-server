/**
 * @file nzilbb.labbcat module for communicating with <a href="https://labbcat.canterbury.ac.nz/">LaBB-CAT</a> web application servers.
 * 
 * <h2>What is LaBB-CAT?</h2>
 *
 * <p>LaBB-CAT is a web-based linguistic annotation store that stores audio or video
 * recordings, text transcripts, and other annotations.</p>
 *
 * <p>Annotations of various types can be automatically generated or manually added.</p>
 *
 * <p>LaBB-CAT servers are usually password-protected linguistic corpora, and can be
 * accessed manually via a web browser, or programmatically using a client library like
 * this one.</p>
 * 
 * <h2>What is this library?</h2>
 * 
 * <p>The library copies from  
 *   <a href="https://nzilbb.github.io/ag/javadoc/nzilbb/ag/IGraphStoreQuery.html">nzilbb.ag.IGraphStoreQuery</a>
 *   and related Java interfaces, for standardized API calls.</p>
 *
 * <p><em>nzilbb.labbcat</em> is available as an <em>npm</em> package
 *   <a href="https://www.npmjs.com/package/@nzilbb/labbcat">here.</a></p>
 * 
 * <p><em>nzilbb.labbcat.js</em> can also be used as a browser-importable script.</p>
 * 
 * <p>This API is has the following object model:
 * <dl>
 *  <dt>{@link LabbcatView}</dt><dd> implements read-only functions for a LaBB-CAT graph
 *   store, corresponding to <q>view</q> permissions in LaBB-CAT.</dd>
 *  <dt>{@link LabbcatEdit}</dt><dd> inherits all LabbcatView functions, and also
 *   implements some graph store editing functions, corresponding to <q>edit</q>
 *   permissions in LaBB-CAT.</dd>
 *  <dt>{@link LabbcatAdmin}</dt><dd> inherits all LabbcatEdit functions, and also
 *   implements some administration functions, corresponding to <q>admin</q>
 *   permissions in LaBB-CAT.</dd>
 * </dl> 
 *
 * @example
 * const corpus = new labbcat.LabbcatView("https://sometld.com", "your username", "your password");
 *
 * // optionally, we can set the language that messages are returned in 
 * labbcat.language = "es";
 * 
 * // get the first participant in the corpus
 * corpus.getParticipantIds((ids, errors, messages)=>{
 *     const participantId = ids[0];
 *     
 *     // all their instances of "the" followed by a word starting with a vowel
 *     const pattern = [
 *         {"orthography" : "i"},
 *         {"phonemes" : "[cCEFHiIPqQuUV0123456789~#\\$@].*"}];
 *     
 *     // start searching
 *     corpus.search(pattern, [ participantId ], false, (response, errors, messages)=>{
 *         const taskId = response.threadId
 *                 
 *         // wait for the search to finish
 *         corpus.waitForTask(taskId, 30, (task, errors, messages)=>{
 *             
 *             // get the matches
 *             corpus.getMatches(taskId, (result, errors, messages)=>{
 *                 const matches = result.matches;
 *                 console.log("There were " + matches.length + " matches for " + participantId);
 *                 
 *                 // get TextGrids of the utterances
 *                 corpus.getFragments(
 *                     matches, [ "orthography", "phonemes" ], "text/praat-textgrid",
 *                     (textgrids, errors, messages)=>{
 *                         
 *                         for (let textgrid of textgrids) {
 *                             console.log(textgrid);
 *                         }
 *                         
 *                         // get the utterance recordings
 *                         corpus.getSoundFragments(matches, (wavs, errors, messages)=>{
 *                             
 *                             for (let wav of wavs) {
 *                                 console.log(wav);
 *                             }
 *                         });
 *                     });
 *             });
 *         });
 *     });
 * });
 *
 * @author Robert Fromont robert.fromont@canterbury.ac.nz
 * @license magnet:?xt=urn:btih:1f739d935676111cfff4b4693e3816e664797050&dn=gpl-3.0.txt GPL v3.0
 * @copyright 2016-2020 New Zealand Institute of Language, Brain and Behaviour, University of Canterbury
 *
 *    This file is part of LaBB-CAT.
 *
 *    LaBB-CAT is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    LaBB-CAT is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with LaBB-CAT; if not, write to the Free Software
 *    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * @lic-end
 */

(function(exports){

  var runningOnNode = false;

  if (typeof(require) == "function") { // running on node.js
    XMLHttpRequest = require('xhr2');
    FormData = require('form-data');
    fs = require('fs');
    path = require('path');
    os = require('os');
    btoa = require('btoa');
    parseUrl = require('url').parse;
    runningOnNode = true;
  }

  /**
   * Callback invoked when the result of a request is available.
   *
   * @callback resultCallback
   * @param result The result of the function. This may be null, a string, number,
   * array, or complex object, depending on what function was called.
   * @param {string[]} errors A list of errors, or null if there were no errors.
   * @param {string[]} messages A list of messages from the server if any.
   * @param {string} call The name of the function that was called
   * @param {string} id The ID that was passed to the method, if any.
   */
  
  function callComplete(evt) {
    if (exports.verbose) console.log("callComplete: " + this.responseText);
    var result = null;
    var errors = null;
    var messages = null;
    try {
      if (evt.target.raw) {
        result = this.responseText;
      } else {
        var response = JSON.parse(this.responseText);
        if (response.model != null) {
          if (response.model.result) {
            result = response.model.result;
          }
	  if (!result && result != 0) result = response.model;
        }
        if (exports.verbose) console.log("result: " + JSON.stringify(result));
        var errors = response.errors;
        if (!errors || errors.length == 0) errors = null;
        var messages = response.messages;
        if (!messages || messages.length == 0) messages = null;
      }
    } catch(exception) {
      result = null;
      errors = ["" +exception+ ": " + this.responseText];
      messages = [];
    }
    if (evt.target.onResult) {
      if (exports.verbose) console.log("onResult: " + result);
      evt.target.onResult(result, errors, messages, evt.target.call, evt.target.id);
    }
  }
  function callFailed(evt) {
    if (exports.verbose) console.log("callFailed: "+this.responseText);
    if (evt.target.onResult) {
      evt.target.onResult(
        null, ["failed: " + this.responseText], [], evt.target.call, evt.target.id);
    }
  }
  function callCancelled(evt) {
    if (exports.verbose) console.log("callCancelled");
    if (evt.target.onResult) {
      evt.target.onResult(null, ["cancelled"], [], evt.target.call, evt.target.id);
    }
  }

  // LabbcatView class - read-only "view" access
  
  /**
   * Read-only querying of LaBB-CAT corpora, based on the  
   * <a href="https://nzilbb.github.io/ag/javadoc/nzilbb/ag/IGraphStoreQuery.html">nzilbb.ag.IGraphStoreQuery</a>
   * interface.
   * <p>This interface provides only <em>read-only</em> operations.
   * @example
   * // create annotation store client
   * const store = new LabbcatView("https://labbcat.canterbury.ac.nz", "demo", "demo");
   * // get some basic information
   * store.getId((result, errors, messages, call)=>{ 
   *     console.log("id: " + result); 
   *   });
   * store.getLayerIds((layers, errors, messages, call)=>{ 
   *     for (l in result) console.log("layer: " + layers[l]); 
   *   });
   * store.getCorpusIds((corpora, errors, messages, call)=>{ 
   *     store.getTranscriptIdsInCorpus(corpora[0], (ids, errors, messages, call, id)=>{ 
   *         console.log("transcripts in: " + id); 
   *         for (i in ids) console.log(ids[i]);
   *       });
   *   });
   * @author Robert Fromont robert@fromont.net.nz
   */
  class LabbcatView {
    /** 
     * Create a query client 
     * @param {string} baseUrl The LaBB-CAT base URL (i.e. the address of the 'home' link)
     * @param {string} username The LaBB-CAT user name.
     * @param {string} password The LaBB-CAT password.
     */
    constructor(baseUrl, username, password) {
      if (!/\/$/.test(baseUrl)) baseUrl += "/";
      this._baseUrl = baseUrl;
      this._storeUrl = baseUrl + "api/store/";
      
      this._username = username;
      this._password = password;
    }
    
    /**
     * The base URL - e.g. https://labbcat.canterbury.ac.nz/demo/api/store/
     */
    get baseUrl() {
      return this._baseUrl;
    }
    
    /**
     * The graph store URL - e.g. https://labbcat.canterbury.ac.nz/demo/api/store/
     */
    get storeUrl() {
      return this._storeUrl;
    }
    set storeUrl(url) {
      this._storeUrl = url;
    }
    
    /**
     * The LaBB-CAT user name.
     */
    get username() {
      return this._username;
    }

    parametersToQueryString(parameters) {
      var queryString = "";
      if (parameters) {
	for (var key in parameters) {
	  if (parameters[key] // parameter is not false-ish
              || parameters[key] === "" // or it's an empty string
              || parameters[key] == 0) { // or it's 0
  	    if (parameters[key].constructor === Array) {
	      for (var i in parameters[key]) {
		queryString += "&"+key+"="+encodeURIComponent(parameters[key][i])
	      }
	    } else {
	      queryString += "&"+key+"="+encodeURIComponent(parameters[key])
	    }
	  }
	} // next parameter
      }
      queryString = queryString.replace(/^&/,"");
      return queryString;
    }
    
    //
    // Creates an http request.
    // @param {string} call The name of the API function to call
    // @param {object} parameters The arguments of the function, if any
    // @param {resultCallback} onResult Invoked when the request has returned a result.
    // @param {string} [url=this.storeUrl] The URL
    // @param {string} [method=GET] The HTTP method e.g. "POST"
    // @param {string} [storeUrl=null] The URL for the graph store.
    // @param {string} [contentTypeHeader=null] The request content type e.g "application/x-www-form-urlencoded".
    // @param {boolean} [raw=false] Whether the result should be the un-parsed request response text.
    // @return {XMLHttpRequest} An open request.
    //
    createRequest(call, parameters, onResult, url, method, storeUrl, contentTypeHeader, raw) {
      if (exports.verbose)  {
        console.log("createRequest "+method+" "+url + " "
                    + call + " " + JSON.stringify(parameters));
      }
      method = method || "GET";
      
      var xhr = new XMLHttpRequest();
      xhr.call = call;
      if (parameters && parameters.id) xhr.id = parameters.id;
      xhr.onResult = onResult;
      xhr.addEventListener("load", callComplete, false);
      xhr.addEventListener("error", callFailed, false);
      xhr.addEventListener("abort", callCancelled, false);
      var queryString = this.parametersToQueryString(parameters);
      if (!url) {
        storeUrl = storeUrl || this.storeUrl;
        if (exports.verbose) {
          console.log(method + ": "+storeUrl + call + queryString + " as " + this.username);
        }
	xhr.open(method, storeUrl + call + (queryString?"?"+queryString:""), true);
      } else { // explicit URL, so don't append call
        if (exports.verbose) {
          console.log(method + ": "+url + queryString + " as " + this.username);
        }
	xhr.open(method, url + (queryString?"?"+queryString:""), true);
      }
      if (contentTypeHeader) xhr.setRequestHeader("Content-Type", contentTypeHeader);
      if (this.username) {
	xhr.setRequestHeader(
          "Authorization", "Basic " + btoa(this.username + ":" + this._password))
      }
      if (exports.language) {
	xhr.setRequestHeader("Accept-Language", exports.language);
      }
      if (raw) {
        xhr.raw = true;
        xhr.setRequestHeader("Accept", "text/plain");
      } else {
        xhr.setRequestHeader("Accept", "application/json");
      }
      return xhr;
    }
    
    /**
     * Gets version information of all components of LaBB-CAT.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be:  {object} An object containing section objects
     * "System", "Formats", "Layer Managers", etc. each containing version information
     * for sub-components. e.g.
     * <ul>
     *  <li>result["System"]["LaBB-CAT"] is the overall LaBB-CAT version,</li>
     *  <li>result["System"]["nzilbb.ag"] is the overall annotation graph package
     *      version,</li>
     *  <li>result["Formats"]["Praat TextGrid"] is version of the Praat TextGrid
     *      conversion module,</li>
     *  <li>result["Layer Managers"]["HTK"] is version of the HTK Layer Manager, etc.</li>
     * </ul>
     */
    versionInfo(onResult) {
      this.createRequest("version", null, onResult, this.baseUrl+"api/versions").send();
    }
    
    /**
     * Log in. This only works within a browser; in node, it's not possible to prevent
     * request redirection or capture cookies set before redirects, so form-based login
     * cannot be supported.
     * @param {string} username The user's login ID.
     * @param {string} password The user's password/phrase.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be an object with a "username" and "url"
     * attribute - the latter is where the browser should be redirected next, which
     * will be either the last requested page, or the LaBB-CAT's home page.
     */
    login(username, password, onResult) {
      console.log(`login ${username}`);
      var xhr = new XMLHttpRequest();
      var labbcat = this;
      xhr.onResult = onResult;
      xhr.addEventListener("load", function(e) {
        console.log(`load: ${this.status}: ${this.responseURL}`);
        if (this.status == 303 || this.status == 200 || this.status == 404) {
          onResult({
            username: username,
            url: this.responseURL
          }, null, [`Logged in as ${username}`], "login", username);
        } else if (this.status == 408) { // "Request Timeout"
          // this really means there was no previous URL request,
          // but Tomcat requires a previous URL, so let's give it one:
          var home = new XMLHttpRequest();
          home.open("GET", `${labbcat.baseUrl}`, false);
          home.send(null);
          labbcat.login(username, password, onResult);
        } else {
          onResult(null, [`${this.statusText}: ${this.status}`], [], "login", username);
        }
      }, false);
      xhr.addEventListener("error", function(e) {
        console.log(`error: ${this.status}: ${this.responseText}`);        
        onResult("", ["Username/password invalid"], [], "login", username);
      }, false);
      xhr.addEventListener("abort", function(e) {
        console.log(`abort: ${this.status}: ${this.responseText}`);
        onResult("", ["Request aborted"], [], "login", username);
      }, false);
      if (exports.verbose) {
        console.log("logging in: "+this.storeUrl + " as " + username);
      }
      xhr.open("POST", `${this.baseUrl}j_security_check`, true);
      if (exports.language) {
	xhr.setRequestHeader("Accept-Language", exports.language);
      }
      xhr.raw = true;
      xhr.setRequestHeader("Accept", "text/plain");
      xhr.setRequestHeader(
        "Content-Type", "application/x-www-form-urlencoded;charset=\"utf-8\"");
      console.log(`about to log in...`);
      xhr.send(this.parametersToQueryString({
        j_username: username,
        j_password: password
      }));
    }
    
    /**
     * Changes the current user's password.
     * @param {string} currentPassword The user's current password.
     * @param {string} newPassword The new password.
     * @param {resultCallback} onResult Invoked when the request has returned. 
     */
    changePassword(currentPassword, newPassword, onResult) {
      if (exports.verbose) console.log("changePassword(...)");
      this.createRequest(
        "users", null, onResult, this.baseUrl+"api/password", "PUT")
        .send(JSON.stringify({
          currentPassword : currentPassword,
          newPassword : newPassword}));
    }
    
    /**
     * Gets the store's ID.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be:  {string} The annotation store's ID.
     */
    getId(onResult) {
      this.createRequest("getId", null, onResult).send();
    }
    
    /**
     * Gets the store's information document.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be: {string} An HTML document providing
     * information about the corpus.
     */
    getInfo(onResult) {
      var xhr = new XMLHttpRequest();
      xhr.onResult = onResult;
      xhr.addEventListener("load", function(evt) {
        onResult(this.responseText, null, null, "getInfo")
      }, false);
      xhr.addEventListener("error", callFailed, false);
      xhr.addEventListener("abort", callCancelled, false);
      xhr.open("GET", this.baseUrl + "doc/");
      if (this.username) {
	xhr.setRequestHeader(
          "Authorization", "Basic " + btoa(this.username + ":" + this._password))
      }
      if (exports.language) {
	xhr.setRequestHeader("Accept-Language", exports.language);
      }
      xhr.setRequestHeader("Accept-Language", exports.language);
      xhr.setRequestHeader("Accept", "text/html");
      xhr.send();
    }
    
    /**
     * Gets a list of layer IDs (annotation 'types').
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be:  {string[]} A list of layer IDs.
     */
    getLayerIds(onResult) {
      this.createRequest("getLayerIds", null, onResult).send();
    }
    
    /**
     * Gets a list of layer definitions.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be:  A list of layer definitions.
     */
    getLayers(onResult) {
      this.createRequest("getLayers", null, onResult).send();
    }
    
    /**
     * Gets the layer schema.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be:  A schema defining the layers and how they
     * relate to each other. 
     */
    getSchema(onResult) {
      this.createRequest("getSchema", null, onResult).send();
    }
    
    /**
     * Gets a layer definition.
     * @param {string} id ID of the layer to get the definition for.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be: The definition of the given layer.
     */
    getLayer(id, onResult) {
      var xhr = this.createRequest("getLayer", { id : id }, onResult).send();
    }
    
    /**
     * Gets a list of corpus IDs.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be:  {string[]} A list of corpus IDs.
     */
    getCorpusIds(onResult) {
      this.createRequest("getCorpusIds", null, onResult).send();
    }
    
    /**
     * Gets statistics about a given corpus.
     * @param {string} id ID of the corpus.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be:  An object, where each key is the name of
     * a statistic and the value is the statistic's value.
     */
    getCorpusInfo(id, onResult) {
      this.createRequest("getCorpusInfo", null, onResult, `${this.baseUrl}api/corpus/${id}`)
        .send();
    }
    
    /**
     * Gets a list of participant IDs.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be: {string[]} A list of participant IDs.
     */
    getParticipantIds(onResult) {
      this.createRequest("getParticipantIds", null, onResult).send();
    }

    /**
     * Gets the participant record specified by the given identifier.
     * @param id The ID of the participant, which could be their name or their
     * database annotation ID. 
     * @param layerIds The IDs of the participant attribute layers to load, or null if only
     * participant data is required. 
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be:   An annotation representing the participant,
     * or null if the participant was not found.
     */
    getParticipant(id, layerIds, onResult) {
      if (typeof layerIds === "function") { // (id, onResult)
        onResult = layerIds;
        layerIds = null;
      }
      this.createRequest("getParticipant", {id : id, layerIds : layerIds}, onResult).send();
    }
    
    /**
     * Counts the number of participants that match a particular pattern.
     * @param {string} expression An expression that determines which participants match.
     * <p> The expression language is loosely based on JavaScript; expressions such as the
     * following can be used: 
     * <ul>
     *  <li><code>/Ada.+/.test(id)</code></li>
     *  <li><code>labels('corpus').includes('CC')</code></li>
     *  <li><code>labels('participant_languages').includes('en')</code></li>
     *  <li><code>labels('transcript_language').includes('en')</code></li>
     *  <li><code>!/Ada.+/.test(id) &amp;&amp; first('corpus').label == 'CC'</code></li>
     *  <li><code>all('transcript_rating').length &gt; 2</code></li>
     *  <li><code>all('participant_rating').length = 0</code></li>
     *  <li><code>!annotators('transcript_rating').includes('labbcat')</code></li>
     *  <li><code>first('participant_gender').label == 'NA'</code></li>
     * </ul>
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be: The number of matching participants.
     */
    countMatchingParticipantIds(expression, onResult) {
      this.createRequest("countMatchingParticipantIds", {
        expression : expression
      }, onResult).send();
    }
    
    /**
     * Gets a list of IDs of participants that match a particular pattern.
     * @param {string} expression An expression that determines which participants match.
     * <p> The expression language is loosely based on JavaScript; expressions such as the
     * following can be used: 
     * <ul>
     *  <li><code>/Ada.+/.test(id)</code></li>
     *  <li><code>labels('corpus').includes('CC')</code></li>
     *  <li><code>labels('participant_languages').includes('en')</code></li>
     *  <li><code>labels('transcript_language').includes('en')</code></li>
     *  <li><code>!/Ada.+/.test(id) &amp;&amp; first('corpus').label == 'CC'</code></li>
     *  <li><code>all('transcript_rating').length &gt; 2</code></li>
     *  <li><code>all('participant_rating').length = 0</code></li>
     *  <li><code>!annotators('transcript_rating').includes('labbcat')</code></li>
     *  <li><code>first('participant_gender').label == 'NA'</code></li>
     * </ul>
     * @param {int} [pageLength] The maximum number of IDs to return, or null to return all.
     * @param {int} [pageNumber] The zero-based page number to return, or null to return the
     * first page. 
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be: A list of participant IDs.
     */
    getMatchingParticipantIds(expression, pageLength, pageNumber, onResult) {
      if (typeof pageLength === "function") { // no pageLength, pageNumber
        onResult = pageLength;
        pageLength = null;
        pageNumber = null;
      }
      this.createRequest("getMatchingParticipantIds", {
        expression : expression,
        pageLength : pageLength,
        pageNumber : pageNumber
      }, onResult).send();
    }

    /**
     * Counts the number of transcripts that match a particular pattern.
     * @param {string} expression An expression that determines which transcripts match.
     * <p> The expression language is loosely based on JavaScript; expressions such as
     * the following can be used: 
     * <ul>
     *  <li><code>/Ada.+/.test(id)</code></li>
     *  <li><code>labels('participant').includes('Robert')</code></li>
     *  <li><code>('CC', 'IA', 'MU').includes(first('corpus').label)</code></li>
     *  <li><code>first('episode').label == 'Ada Aitcheson'</code></li>
     *  <li><code>first('transcript_scribe').label == 'Robert'</code></li>
     *  <li><code>first('participant_languages').label == 'en'</code></li>
     *  <li><code>first('noise').label == 'bell'</code></li>
     *  <li><code>labels('transcript_languages').includes('en')</code></li>
     *  <li><code>labels('participant_languages').includes('en')</code></li>
     *  <li><code>labels('noise').includes('bell')</code></li>
     *  <li><code>all('transcript_languages').length gt; 1</code></li>
     *  <li><code>all('participant_languages').length gt; 1</code></li>
     *  <li><code>all('transcript').length gt; 100</code></li>
     *  <li><code>annotators('transcript_rating').includes('Robert')</code></li>
     *  <li><code>!/Ada.+/.test(id) &amp;&amp; first('corpus').label == 'CC' &amp;&amp;
     * labels('participant').includes('Robert')</code></li> 
     * </ul>
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be: The number of matching transcripts.
     */
    countMatchingTranscriptIds(expression, onResult) {
      this.createRequest("countMatchingTranscriptIds", {
        expression : expression
      }, onResult).send();
    }    

    /**
     * <p>Gets a list of IDs of transcripts that match a particular pattern.
     * <p>The results can be exhaustive, by omitting pageLength and pageNumber, or they
     * can be a subset (a 'page') of results, by given pageLength and pageNumber values.</p>
     * <p>The order of the list can be specified.  If ommitted, the transcripts are
     * listed in ID order.</p> 
     * @param {string} expression An expression that determines which transcripts match.
     * <p> The expression language is loosely based on JavaScript; expressions such as
     * the following can be used:
     * <ul>
     *  <li><code>/Ada.+/.test(id)</code></li>
     *  <li><code>labels('participant').includes('Robert')</code></li>
     *  <li><code>('CC', 'IA', 'MU').includes(first('corpus').label)</code></li>
     *  <li><code>first('episode').label == 'Ada Aitcheson'</code></li>
     *  <li><code>first('transcript_scribe').label == 'Robert'</code></li>
     *  <li><code>first('participant_languages').label == 'en'</code></li>
     *  <li><code>first('noise').label == 'bell'</code></li>
     *  <li><code>labels('transcript_languages').includes('en')</code></li>
     *  <li><code>labels('participant_languages').includes('en')</code></li>
     *  <li><code>labels('noise').includes('bell')</code></li>
     *  <li><code>all('transcript_languages').length gt; 1</code></li>
     *  <li><code>all('participant_languages').length gt; 1</code></li>
     *  <li><code>all('transcript').length gt; 100</code></li>
     *  <li><code>annotators('transcript_rating').includes('Robert')</code></li>
     *  <li><code>!/Ada.+/.test(id) &amp;&amp; first('corpus').label == 'CC' &amp;&amp;
     * labels('participant').includes('Robert')</code></li> 
     * </ul>
     * @param {int} [pageLength] The maximum number of IDs to return, or null to return all.
     * @param {int} [pageNumber] The zero-based page number to return, or null to return
     * the first page. 
     * @param {string} [order] The ordering for the list of IDs, a string containing a
     * comma-separated list of 
     * expressions, which may be appended by " ASC" or " DESC", or null for transcript ID order. 
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be: A list of transcript IDs.
     */
    getMatchingTranscriptIds(expression, pageLength, pageNumber, order, onResult) {
      if (typeof pageLength === "function") { // (expression, onResult)
        onResult = pageLength;
        pageLength = null;
        pageNumber = null;
        order = null;
      } else if (typeof pageNumber === "function") { // (order, onResult)
        order = pageLength;
        onResult = pageNumber;
        pageLength = null;
        pageNumber = null;
      } else if (typeof order === "function") { // (pageLength, pageNumber, onResult)
        onResult = order;
        order = null;
      }
      this.createRequest("getMatchingTranscriptIds", {
        expression : expression,
        pageLength : pageLength,
        pageNumber : pageNumber,
        order : order
      }, onResult).send();
    }
    
    /**
     * Gets the number of annotations on the given layer of the given transcript.
     * @param {string} id The ID of the transcript.
     * @param {string} layerId The ID of the layer.
     * @param {int} [maxOrdinal] The maximum ordinal for the counted annotations.
     * e.g. a <var>maxOrdinal</var> of 1 will ensure that only the first annotation for each
     * parent is counted. If <var>maxOrdinal</var> is null, then all annotations are
     * counted, regardless of their ordinal.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be: A (possibly empty) array of annotations.
     */
    countAnnotations(id, layerId, maxOrdinal, onResult) {
      if (typeof maxOrdinal === "function") { // (id, layerId, onResult)
        onResult = maxOrdinal;
        maxOrdinal = null;
      }
      this.createRequest("countAnnotations", {
        id : id,
        layerId : layerId,
        maxOrdinal : maxOrdinal
      }, onResult).send();
    }
    
    /**
     * Gets the annotations on the given layer of the given transcript.
     * @param {string} id The ID of the transcript.
     * @param {string} layerId The ID of the layer.
     * @param {int} [maxOrdinal] The maximum ordinal for the returned annotations.
     * e.g. a <var>maxOrdinal</var> of 1 will ensure that only the first annotation for each
     * parent is returned. If <var>maxOrdinal</var> is null, then all annotations are
     * returned, regardless of their ordinal.
     * @param {int} [pageLength] The maximum number of IDs to return, or null to return all.
     * @param {int} [pageNumber] The zero-based page number to return, or null to return 
     * the first page. 
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be: A (possibly empty) array of annotations.
     */
    getAnnotations(id, layerId, maxOrdinal, pageLength, pageNumber, onResult) {
      if (typeof maxOrdinal === "function") { // (id, layerId, onResult)
        onResult = maxOrdinal;
        maxOrdinal = null;
        pageLength = null;
        pageNumber = null;
      } else if (typeof pageLength === "function") { // (id, layerId, maxOrdinal, onResult)
        onResult = pageLength;
        pageLength = null;
        pageNumber = null;
      } else if (typeof pageNumber === "function") { // (id, layerId, pageLength, pageNumber, onResult)
        onResult = pageNumber;
        pageNumber = pageLength;
        pageLength = maxOrdinal;
        maxOrdinal = null;
      }
      this.createRequest("getAnnotations", {
        id : id,
        layerId : layerId,
        maxOrdinal : maxOrdinal,
        pageLength : pageLength,
        pageNumber : pageNumber,
        includeAnchors : true
      }, onResult).send();
    }
    
    /**
     * Identifies a list of annotations that match a particular pattern, and aggregates
     * their labels. <p> This allows for counting, listing distinct labels, etc. 
     * @param {string} operation The aggregation operation(s) - e.g. 
     *  <dl>
     *   <dt> DISTINCT </dt><dd> List the distinct labels. </dd>
     *   <dt> MAX </dt><dd> Return the highest label. </dd>
     *   <dt> MIN </dt><dd> Return the lowest label. </dd>
     *   <dt> COUNT </dt><dd> Return the number of annotations. </dd>
     *   <dt> COUNT DISTINCT </dt><dd> Return the number of distinct labels. </dd>
     *  </dl>
     *  More than one operation can be specified, by using a comma delimiter. 
     *  e.g. "DISTINCT,COUNT" will return each distinct label, followed by its count
     *  (i.e. the array will have twice the number of elements as there are distinct words,
     *  even-indexed elements are the word labels, and odd-indexed elements are the counts).
     * @param {string} expression An expression that determines which participants match.
     * <p> The expression language is loosely based on JavaScript; expressions such as
     * the following can be used:
     * <ul>
     *  <li><code>layer.id == 'orthography'</code></li>
     *  <li><code>graph.id == 'AdaAicheson-01.trs' &amp;&amp; layer.id == 'orthography'</code></li>
     * </ul>
     * <p><em>NB</em> all expressions must match by either id or layer.id.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be: The number of matching annotations.
     */
    aggregateMatchingAnnotations(operation, expression, onResult) {
      this.createRequest("aggregateMatchingAnnotations", {
        operation : operation,
        expression : expression
      }, onResult).send();
    }
    
    /**
     * Counts the number of annotations that match a particular pattern.
     * @param {string} expression An expression that determines which participants match.
     * <p> The expression language is loosely based on JavaScript; expressions such as
     * the following can be used:
     * <ul>
     *  <li><code>id == 'ew_0_456'</code></li>
     *  <li><code>!/th[aeiou].&#47;/.test(label)</code></li>
     *  <li><code>first('participant').label == 'Robert' &amp;&amp; first('utterances').start.offset ==
     * 12.345</code></li> 
     *  <li><code>graph.id == 'AdaAicheson-01.trs' &amp;&amp; layer.id == 'orthography'
     * &amp;&amp; start.offset &gt; 10.5</code></li> 
     *  <li><code>previous.id == 'ew_0_456'</code></li>
     * </ul>
     * </ul>
     * <p><em>NB</em> all expressions must match by either id or layer.id.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be: The number of matching annotations.
     */
    countMatchingAnnotations(expression, onResult) {
      this.createRequest("countMatchingAnnotations", {
        expression : expression
      }, onResult).send();
    }
    
    /**
     * Gets a list of annotations that match a particular pattern.
     * @param {string} expression An expression that determines which transcripts match.
     * <p> The expression language is loosely based on JavaScript; expressions such as the
     * following can be used: 
     * <ul>
     *  <li><code>id == 'ew_0_456'</code></li>
     *  <li><code>!/th[aeiou].&#47;/.test(label)</code></li>
     *  <li><code>first('participant').label == 'Robert' &amp;&amp; first('utterances').start.offset ==
     * 12.345</code></li> 
     *  <li><code>graph.id == 'AdaAicheson-01.trs' &amp;&amp; layer.id == 'orthography'
     * &amp;&amp; start.offset &gt; 10.5</code></li> 
     *  <li><code>previous.id == 'ew_0_456'</code></li>
     * </ul>
     * <p><em>NB</em> all expressions must match by either id or layer.id.
     * @param {int} [pageLength] The maximum number of annotations to return, or null
     * to return all. 
     * @param {int} [pageNumber] The zero-based page number to return, or null to
     * return the first page. 
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be: A list of matching Annotations.
     */
    getMatchingAnnotations(expression, pageLength, pageNumber, onResult) {
      if (typeof pageLength === "function") { // (expression, onResult)
        onResult = pageLength;
        pageLength = null;
        pageNumber = null;
      }
      this.createRequest("getMatchingAnnotations", {
        expression : expression,
        pageLength : pageLength,
        pageNumber : pageNumber
      }, onResult).send();
    }
    
    /**
     * Gets a list of transcript IDs.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be:  {string[]} A list of transcript IDs.
     */
    getTranscriptIds(onResult) {
      this.createRequest("getTranscriptIds", null, onResult).send();
    }
    
    /**
     * Gets a list of transcript IDs in the given corpus.
     * @param {string} id A corpus ID.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be:  {string[]} A list of transcript IDs.
     */
    getTranscriptIdsInCorpus(id, onResult) {
      this.createRequest("getTranscriptIdsInCorpus", { id : id }, onResult).send();
    }
    
    /**
     * Gets a list of IDs of transcripts that include the given participant.
     * @param {string} id A participant ID.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be:  {string[]} A list of transcript IDs.
     */
    getTranscriptIdsWithParticipant(id, onResult) {
      this.createRequest("getTranscriptIdsWithParticipant", { id : id }, onResult).send();
    }
    
    /**
     * Gets a transcript given its ID, containing only the given layers.
     * @param {string} id The given transcript ID.
     * @param {string[]} layerIds The IDs of the layers to load, or null for all
     * layers. If only transcript data is required, set this to ["graph"]. 
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be:  The identified transcript.
     */
    getTranscript(id, layerIds, onResult) {
      this.createRequest("getTranscript", { id : id, layerIds : layerIds }, onResult).send();
    }
    
    /**
     * Gets a transcript given its ID, containing only the given layers.
     * @param {string} transcriptId The given transcript ID.
     * @param {string} annotationId The ID of an annotation that
     * defines the bounds of the fragment. 
     * @param {string[]} layerIds The IDs of the layers to load, or null for all
     * layers. If only transcript data is required, set this to ["graph"]. 
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be:  The identified transcript.
     */
    getFragment(id, annotationId, layerIds, onResult) {
      this.createRequest("getFragment", {
        id : id, annotationId: annotationId, layerIds : layerIds
      }, onResult).send();
    }
    
    /**
     * Gets the given anchors in the given transcript.
     * @param {string} id The given transcript ID.
     * @param {string[]} anchorIds The IDs of the anchors to load.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be:  The identified transcript.
     */
    getAnchors(id, anchorIds, onResult) {
      this.createRequest("getAnchors", { id : id, anchorIds : anchorIds }, onResult).send();
    }
    
    /**
     * List the predefined media tracks available for transcripts.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be:  An ordered list of media track definitions.
     */
    getMediaTracks(onResult) {
      this.createRequest("getMediaTracks", null, onResult).send();
    }
    
    /**
     * List the media available for the given transcript.
     * @param {string} id The transcript ID.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be:  List of media files available for the given transcript.
     */
    getAvailableMedia(id, onResult) {
      this.createRequest("getAvailableMedia", { id : id }, onResult).send();
    }
    
    /**
     * Get a list of documents associated with the episode of the given transcript.
     * @param {string} id The transcript ID.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be:  List of media files available for the given transcript.
     */
    getEpisodeDocuments(id, onResult) {
      this.createRequest("getEpisodeDocuments", { id : id }, onResult).send();
    }
    
    /**
     * Gets a given media track for a given transcript.
     * @param {string} id The transcript ID.
     * @param {string} trackSuffix The track suffix of the media.
     * @param {string} mimeType The MIME type of the media.
     * @param {float} [startOffset] The start offset of the media sample, or null for
     * the start of the whole recording. 
     * @param {float} [endOffset[ The end offset of the media sample, or null for the
     * end of the whole recording. 
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be: {string} A URL to the given media for the given
     * transcript, or null if the given media doesn't exist.
     */
    getMedia(id, trackSuffix, mimeType, startOffset, endOffset, onResult) {
      if (typeof startOffset === "function") { // (id, trackSuffix, mimeType, onResult)
        onResult = startOffset;
        startOffset = null;
        endOffset = null;
      }
      this.createRequest("getMedia", {
        id : id,
        trackSuffix : trackSuffix,
        mimeType : mimeType,
        startOffset : startOffset,
        endOffset : endOffset
      }, onResult).send();
    }

    /**
     * Gets list of tasks.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * result, which is an array of task IDs.
     */
    getTasks(onResult) {
      if (exports.verbose) console.log("getTasks()");
      this.createRequest("getTasks", null, onResult, `${this.baseUrl}api/task/`).send();
    }
    
    /**
     * Gets the status of a task.
     * @param {string} id ID of the task.
     * @param {object} options An object with boolean settings, e.g. {log:true}
     * Current supported options are:
     * <ul>
     *  <li>log: whether to return the tasks log (default is false)</li>
     *  <li>keepalive: whether to keep the thread alive (default is true)</li>
     * </ul>
     * "log": whether to return the tasks log,
     * @param {resultCallback} onResult Invoked when the request has returned a result.
     */
    taskStatus(id, options, onResult) {
      if (typeof options === "function") { // (id, onResult)
        onResult = options;
        options = null;
      }
      options = Object.assign({log:false, keepalive:true}, options);
      this.createRequest(
        "taskStatus", options, onResult, `${this.baseUrl}api/task/${id}`).send();
    }

    /**
     * Wait for the given task to finish.
     * @param {string} threadId The task ID.
     * @param {int} maxSeconds The maximum time to wait for the task, or 0 for forever.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be: The final task status. To determine whether
     * the task finished or waiting timed out, check <var>result.running</var>, which
     * will be false if the task finished.
     */
    waitForTask(threadId, maxSeconds, onResult) {
      if (exports.verbose) console.log("waitForTask("+threadId+", "+maxSeconds+")");
      const labbcat = this;
      this.taskStatus(threadId, (thread, errors, messages)=> {
        const waitTimeMS = thread && thread.refreshSeconds?
              thread.refreshSeconds*1000 : 2000;
        if (thread.running && maxSeconds > waitTimeMS/1000) {
          setTimeout(()=>labbcat.waitForTask(
            threadId, maxSeconds - waitTimeMS/1000, onResult), waitTimeMS);
        } else {
          if (onResult) {
            onResult(thread, errors, messages);
          }
        }
      });
    }

    /**
     * Releases a finished task so it no longer uses resources on the server.
     * @param {string} id ID of the task.
     * @param {resultCallback} onResult Invoked when the request has completed.
     */
    releaseTask(id, onResult) {
      if (exports.verbose) console.log("releaseTask("+id+")");
      this.createRequest("releaseTask", {
        release : true
      }, onResult, `${this.baseUrl}api/task/${id}`, "DELETE").send();
    }
    
    /**
     * Cancels a running task.
     * @param id The ID of the task.
     * @param {resultCallback} onResult Invoked when the request has completed.
     */
    cancelTask(id, onResult) {
      if (exports.verbose) console.log("cancelTask("+threadId+")");
      this.createRequest("cancelTask", {
        cancel : true
      }, onResult, `${this.baseUrl}api/task/${id}`, "DELETE").send();
    }
    
    /**
     * Searches for tokens that match the given pattern.
     * <p> Although <var>mainParticipantOnly</var>, <var>offsetThreshold</var> and
     * <var>matchesPerTranscript</var> are all optional, if one of them is specified,
     * then all must be specified.
     * <p> The <var>pattern</var> should match the structure of the search matrix in the
     * browser interface of LaBB-CAT. This is a JSON object with one attribute called
     * <q>columns</q>, which is an array of JSON objects.
     * <p>Each element in the <q>columns</q> array contains am JSON object named
     * <q>layers</q>, whose value is a JSON object for patterns to match on each layer, and
     * optionally an element named <q>adj</q>, whose value is a number representing the
     * maximum distance, in tokens, between this column and the next column - if <q>adj</q>
     * is not specified, the value defaults to 1, so tokens are contiguous.
     * Each element in the <q>layers</q> JSON object is named after the layer it matches, and
     * the value is a named list with the following possible attributes:
     * <dl>
     *  <dt>pattern</dt> <dd>A regular expression to match against the label</dd>
     *  <dt>min</dt> <dd>An inclusive minimum numeric value for the label</dd>
     *  <dt>max</dt> <dd>An exclusive maximum numeric value for the label</dd>
     *  <dt>not</dt> <dd>TRUE to negate the match</dd>
     *  <dt>anchorStart</dt> <dd>TRUE to anchor to the start of the annotation on this layer
     *     (i.e. the matching word token will be the first at/after the start of the matching
     *     annotation on this layer)</dd>
     *  <dt>anchorEnd</dt> <dd>TRUE to anchor to the end of the annotation on this layer
     *     (i.e. the matching word token will be the last before/at the end of the matching
     *     annotation on this layer)</dd>
     *  <dt>target</dt> <dd>TRUE to make this layer the target of the search; the results will
     *     contain one row for each match on the target layer</dd>
     * </dl>
     *
     * <p>Examples of valid pattern objects include:
     * <pre>// words starting with 'ps...'
     * const pattern1 = {
     *     "columns" : [
     *         {
     *             "layers" : {
     *                 "orthography" : {
     *                     "pattern" : "ps.*"
     *                 }
     *             }
     *         }
     *     ]};
     * 
     * // the word 'the' followed immediately or with one intervening word by
     * // a hapax legomenon (word with a frequency of 1) that doesn't start with a vowel
     * const pattern2 = {
     *     "columns" : [
     *         {
     *             "layers" : {
     *                 "orthography" : {
     *                     "pattern" : "the"
     *                 }
     *             }
     *             "adj" : 2 },
     *         {
     *             "layers" : {
     *                 "phonemes" : {
     *                     "not" : true,
     *                     "pattern" : "[cCEFHiIPqQuUV0123456789~#\\$@].*"}
     *                 "frequency" {
     *                     "max" : "2"
     *                 }
     *             }
     *         }
     *     ]};
     * </pre>
     *
     * For ease of use, the function will also accept the following abbreviated forms:
     * <pre>
     * // a single list representing a 'one column' search, 
     * // and string values, representing regular expression pattern matching
     * const pattern3 = { orthography : "ps.*" };
     *
     * // a list containing the columns (adj defaults to 1, so matching tokens are contiguous)
     * const pattrn4 = [{
     *     orthography : "the"
     * }, {
     *     phonemes : {
     *         not : true,
     *         pattern : "[cCEFHiIPqQuUV0123456789~#\\$@].*" },
     *     frequency : {
     *         max = "2" }
     * }];
     * </pre>
     * @param {object} pattern An object representing the pattern to search for, which
     * mirrors the Search Matrix in the browser interface.
     * @param {string[]} [participantQuery=null] An optional expression for
     * identifying participants to search the utterances of. This can be any
     * expression of the kind used with {@link LabbcatView#getMatchingParticipantIds}
     * e.g. "['AP2505_Nelson','AP2512_MattBlack','AP2515_ErrolHitt'].includes(id)"
     * @param {string[]} [transcriptQuery=null] An optional expression for
     * identifying transcripts to search the utterances of. This can be any
     * expression of the kind used with {@link LabbcatView#getMatchingTranscriptIds} 
     * e.g. "['CC','ID'].includes(first('corpus').label)
     *       && first('transcript_type').label == 'wordlist'"
     * @param {boolean} [mainParticipantOnly=true] true to search only main-participant
     * utterances, false to search all utterances. 
     * @param {int} [offsetThreshold=null] Optional minimum alignment confidence for
     * matching word or segment annotations. A value of 50 means that annotations that
     * were at least automatically aligned will be returned. Use 100 for
     * manually-aligned annotations only, and 0 or no value to return all matching
     * annotations regardless of alignment confidence.
     * @param {int} [matchesPerTranscript=null] Optional maximum number of matches per
     * transcript to return. <tt>null</tt> means all matches.
     * @param {int} [overlapThreshold=null] Optional percentage overlap with other
     * utterances before simultaneous speech is excluded. <tt>null</tt> means include
     * all overlapping utterances.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: An object with one attribute, "threadId",
     * which identifies the resulting task, which can be passed to 
     * {@link LabbcatView#getMatches}, {@link LabbcatView#taskStatus}, 
     * {@link LabbcatView#waitForTask}, etc.
     */
    search(pattern, participantQuery, transcriptQuery, mainParticipantOnly, offsetThreshold, matchesPerTranscript, overlapThreshold, onResult) {
      if (typeof participantQuery === "function") { // (pattern, onResult)
        onResult = participantQuery;
        participantQuery = null;
        transcriptQuery = null;
        mainParticipantOnly = true;
        offsetThreshold = null;
        matchesPerTranscript = null;
        overlapThreshold = null;
      } else if (typeof transcriptQuery === "function") {
        // (pattern, participantQuery, onResult)
        onResult = transcriptQuery;
        transcriptQuery = null;
        mainParticipantOnly = true;
        offsetThreshold = null;
        matchesPerTranscript = null;
        overlapThreshold = null;
      } else if (typeof transcriptQuery === "boolean") {
        // (pattern, participantQuery, mainParticipantOnly, offsetThreshold,
        // matchesPerTranscript, onResult) 
        onResult = overlapThreshold;
        overlapThreshold = matchesPerTranscript
        matchesPerTranscript = offsetThreshold;
        offsetThreshold = mainParticipantOnly;
        mainParticipantOnly = transcriptQuery;
        overlapThreshold = null;
      } else if (typeof mainParticipantOnly === "function") {
        // (pattern, participantQuery, transcriptQuery, onResult)
        onResult = mainParticipantOnly;
        mainParticipantOnly = true;
        offsetThreshold = null;
        matchesPerTranscript = null;
        overlapThreshold = null;
      }
      if (typeof offsetThreshold === "function") {
        // (pattern, participantIds, transcriptQuery, mainParticipantOnly, onResult)
        // i.e. the original signature of this function
        onResult = offsetThreshold;
        offsetThreshold = null;
        matchesPerTranscript = null;
        overlapThreshold = null;
      }
      if (typeof matchesPerTranscript === "function") {
        // (pattern, participantIds, mainParticipantOnly, offsetThreshold, onResult)
        // i.e. the original signature of this function
        onResult = matchesPerTranscript;
        matchesPerTranscript = null;
        overlapThreshold = null;
      }
      if (typeof overlapThreshold === "function") {
        // (pattern, participantIds, mainParticipantOnly, offsetThreshold, matchesPerTranscript, onResult)
        onResult = overlapThreshold;
        overlapThreshold = null;
      }
      if (exports.verbose) {
        console.log("search("+JSON.stringify(pattern)
                    +", "+participantQuery
                    +", "+transcriptQuery
                    +", "+mainParticipantOnly
                    +", "+offsetThreshold
                    +", "+matchesPerTranscript
                    +", "+overlapThreshold+")");
      }

      // for backwards compatibility, convert arrays of IDs to expressions
      if (Array.isArray(participantQuery)) {
        participantQuery = "["
          +participantQuery
          .map(s=>"'"+s.replace(/'/,"\\'")+"'")
          .join(",")
          +"].includes(id)";
      }
      if (Array.isArray(transcriptQuery)) {
        transcriptQuery = "["
          +transcriptQuery
          .map(s=>"'"+s.replace(/'/,"\\'")+"'")
          .join(",")
          +"].includes(first('transcript_type').label)";
      }

      // first normalize the pattern...

      // if pattern isn't a list with a "columns" element, wrap a list around it
      if (!pattern.columns) pattern = { columns : pattern };

      // if pattern.columns isn't an array wrap an array list around it
      if (!(pattern.columns instanceof Array)) pattern.columns = [ pattern.columns ];

      // columns contain lists with no "layers" element, wrap a list around them
      for (let c = 0; c < pattern.columns.length; c++) {
        if (!("layers" in pattern.columns[c])) {
          pattern.columns[c] = { layers : pattern.columns[c] };
        }
      } // next column

      // convert layer:string to layer : { pattern:string }
      for (let c = 0; c < pattern.columns.length; c++) { // for each column
        for (let l in pattern.columns[c].layers) { // for each layer in the column
          // if the layer value isn't an object
          if (typeof pattern.columns[c].layers[l] == "string") {
            // wrap a list(pattern=...) around it
            pattern.columns[c].layers[l] = { pattern : pattern.columns[c].layers[l] };
          } // value isn't a list
        } // next layer
      } // next column

      const parameters = {
        command : "search",
        searchJson : JSON.stringify(pattern),
        words_context : 0
      }
      if (mainParticipantOnly) parameters.mainParticipantOnly = true;
      if (offsetThreshold) parameters.offsetThreshold = offsetThreshold;
      if (matchesPerTranscript) parameters.matchesPerTranscript = matchesPerTranscript;
      if (participantQuery) parameters.participantQuery = participantQuery;
      if (transcriptQuery) parameters.transcriptQuery = transcriptQuery;
      if (overlapThreshold) parameters.overlapThreshold = overlapThreshold;

      this.createRequest(
        "search", null, onResult, this.baseUrl+"api/search",
        "POST", // not GET, because the number of parameters can make the URL too long
        null, "application/x-www-form-urlencoded;charset=\"utf-8\"")
        .send(this.parametersToQueryString(parameters));
    }
    
    /**
     * Identifies all utterances by the given participants.
     * @param {string[]} participantIds A list of participant IDs to identify
     * the utterances of.
     * @param {string[]} [transcriptTypes=null] An optional list of transcript types to limit
     * the results to. If null, all transcript types will be searched. 
     * @param {boolean} [mainParticipant=true] true to search only main-participant
     * utterances, false to search all utterances. 
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: An object with one attribute, "threadId",
     * which identifies the resulting task, which can be passed to 
     * {@link LabbcatView#getMatches}, {@link LabbcatView#taskStatus}, 
     * {@link LabbcatView#waitForTask}, etc.
     */
    allUtterances(participantIds, transcriptTypes, mainParticipant, onResult) {
      if (typeof transcriptTypes === "function") { // (participantIds, onResult)
        onResult = transcriptTypes;
        transcriptTypes = null;
        mainParticipant = true;
      } else if (typeof mainParticipant === "function") {
        // (participantIds, transcriptTypes, onResult)
        onResult = mainParticipant;
        mainParticipant = true;
      } else if (typeof transcriptTypes === "boolean") {
        // (participantIds, mainParticipant, onResult) 
        onResult = mainParticipant;
        mainParticipant = transcriptTypes;
        transcriptTypes = null;
      }
      if (exports.verbose) {
        console.log("allUtterances("+JSON.stringify(participantIds)
                    +", "+JSON.stringify(transcriptTypes)
                    +", "+mainParticipant+")");
      }

      // first normalize the pattern...

      const parameters = {
        list : "list",
        id : participantIds,
      }
      if (mainParticipant) parameters.only_main_speaker = true;
      if (transcriptTypes) parameters.transcript_type = transcriptTypes;
      if (exports.verbose) console.log(JSON.stringify(parameters));

      this.createRequest(
        "allUtterances", null, onResult, this.baseUrl+"api/utterances",
        "POST", // not GET, because the number of parameters can make the URL too long
        null, "application/x-www-form-urlencoded;charset=\"utf-8\"")
        .send(this.parametersToQueryString(parameters));
    }
    
    /**
     * Upload a CSV results file to parse, for then processing as any other results.
     * @param {file|string} results a CSV results file previously returned by
     * <tt>/api/results</tt>. 
     * @param targetColumn Optional column name that identifies each match.
     * The default is "MatchId".
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: An object with one attribute, "threadId",
     * which identifies the resulting task, which can be passed to 
     * {@link LabbcatView#getMatches}, {@link LabbcatView#taskStatus}, 
     * {@link LabbcatView#waitForTask}, etc.
     * @param onProgress Invoked on XMLHttpRequest progress.
     */
    resultsUpload(results, targetColumn, onResult, onProgress) {
      if (typeof targetColumn === "function") { // (results, onResult, onProgress)
        onProgress = onResult;
        onResult = targetColumn;
        targetColumn = null;
      }
      if (exports.verbose) {
        console.log("resultsUpload(" + results + ", " + targetColumn + ")");
      }
      // create form
      var fd = new FormData();
      if (targetColumn) fd.append("targetColumn", targetColumn);
      
      if (!runningOnNode) {
        
	fd.append("results", results);
        
	// create HTTP request
	var xhr = new XMLHttpRequest();
	xhr.call = "resultsUpload";
	xhr.onResult = onResult;
	xhr.addEventListener("load", callComplete, false);
	xhr.addEventListener("error", callFailed, false);
	xhr.addEventListener("abort", callCancelled, false);
	xhr.upload.addEventListener("progress", onProgress, false);
	
	xhr.open("POST", this.baseUrl + "api/results/upload");
	if (this.username) {
	  xhr.setRequestHeader("Authorization", "Basic " + btoa(this.username + ":" + this.password))
	}
	xhr.setRequestHeader("Accept", "application/json");
	xhr.send(fd);
      } else { // runningOnNode
	
	// on node.js, files are actually paths
	var resultsName = results.replace(/.*\//g, "");
        if (exports.verbose) console.log("resultsName: " + resultsName);

	fd.append(
          "results", 
	  fs.createReadStream(results).on('error', function(){
	    onResult(
              null, ["Invalid results: " + resultsName], [], "resultsUpload", resultsName);
	  }), resultsName);
        
	var urlParts = parseUrl(this.baseUrl + "api/results/upload");
	// for tomcat 8, we need to explicitly send the content-type and content-length headers...
        if (exports.verbose) console.log("urlParts " + JSON.stringify(urlParts));
	var labbcat = this;
        var password = this._password;
	fd.getLength(function(something, contentLength) {
	  var requestParameters = {
	    port: urlParts.port,
	    path: urlParts.pathname,
	    host: urlParts.hostname,
	    headers: {
	      "Accept" : "application/json",
	      "content-length" : contentLength,
	      "Content-Type" : "multipart/form-data; boundary=" + fd.getBoundary()
	    }
	  };
	  if (labbcat.username && password) {
	    requestParameters.auth = labbcat.username+':'+password;
	  }
	  if (/^https.*/.test(labbcat.baseUrl)) {
	    requestParameters.protocol = "https:";
	  }
          if (exports.verbose) {
            console.log("submit: " + labbcat.baseUrl + "api/edit/transcript/upload");
          }
          if (exports.verbose) console.log("fd.submit " + JSON.stringify(requestParameters));
	  fd.submit(requestParameters, function(err, res) {
	    var responseText = "";
	    if (!err) {
	      res.on('data',function(buffer) {
		responseText += buffer;
	      });
	      res.on('end',function(){
	        var result = null;
	        var errors = null;
	        var messages = null;
		try {
		  var response = JSON.parse(responseText);
		  result = response.model.result || response.model;
		  errors = response.errors;
		  if (errors && errors.length == 0) errors = null
		  messages = response.messages;
		  if (messages && messages.length == 0) messages = null
		} catch(exception) {
		  result = null
                  errors = ["" +exception+ ": " + labbcat.responseText];
                  messages = [];
		}
		onResult(result, errors, messages, "transcriptUpload", transcriptName);
	      });
	    } else {
	      onResult(null, ["" +err+ ": " + labbcat.responseText], [], "transcriptUpload", transcriptName);
	    }
	    
	    if (res) res.resume();
	  });
	}); // got length
      } // runningOnNode
    } // resultsUpload
    
    /**
     * Gets a list of tokens that were matched by {@link LabbcatView#search}.
     * <p>If the task is still running, then this function will wait for it to finish.
     * <p>This means calls can be stacked like this:
     *  <pre>const matches = labbcat.getMatches(
     *     labbcat.search(
     *        {"orthography", "and"},
     *        participantIds, true), 1);</pre>
     * @param {string} threadId A task ID returned by {@link LabbcatView#search}.
     * @param {int} [wordsContext=0] Number of words context to include in the <q>Before
     * Match</q> and <q>After Match</q> columns in the results.
     * @param {int} [pageLength] The maximum number of matches to return, or null to
     * return all. 
     * @param {int} [pageNumber] The zero-based page number to return, or null to
     * return the first page.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: An object with two attributes:
     * <dl>
     *  <dt>name</dt><dd>The name of the search results collection</dd>
     *  <dt>matches</dt>
     *   <dd>A list of match objects, with the following attributes
     *    <dl>
     *      <dt>Title</dt> <dd>The title of the LaBB-CAT instance</dd>
     *      <dt>Version</dt> <dd>The current version of the LaBB-CAT instance</dd>
     *      <dt>MatchId</dt> <dd>A string identifying the match, of the kind expected
     *        by {@link LabbcatView#getMatchAnnotations}</dd>
     *      <dt>Transcript</dt> <dd>The name of the transcript</dd>
     *      <dt>Participant</dt> <dd>The name of the participant</dd>
     *      <dt>Corpus</dt> <dd>The name of corpus the transcript belongs to</dd>
     *      <dt>Line</dt> <dd>The start offset of the utterance, usually in seconds</dd>
     *      <dt>LineEnd</dt> <dd>The end offset of the uttereance, usually in seconds</dd>
     *      <dt>BeforeMatch</dt> <dd>The context of the trascript text just before the
     *       match</dd> 
     *      <dt>Text</dt> <dd>The transcript text that matched</dd>
     *      <dt>AfterMatch</dt> <dd>The context of the transcript text just after
     *       the match</dd> 
     *    </dl>
     *   </dd> 
     * </dl>
     */
    getMatches(threadId, wordsContext, pageLength, pageNumber, onResult) {
      if (typeof wordsContext === "function") { // (threadId, onResult)
        onResult = wordsContext;
        wordsContext = null;
      }
      else if (typeof pageLength === "function") { // (threadId, wordsContext, onResult)
        onResult = pageLength;
        pageLength = null;
        pageNumber = null;
      }
      else if (typeof pageNumber === "function") {
        // (threadId, pageLength, pageNumber, onResult)
        onResult = pageNumber;
        pageNumber = pageLength;
        pageLength = wordsContext;
        wordsContext = null;
      }
      if (exports.verbose) {
        console.log("getMatches("+threadId+", "+wordsContext
                    +", "+pageLength+", "+pageNumber+")");
      }
      wordsContext = wordsContext || 0;
      
      this.createRequest("getMatches", {
        threadId : threadId,
        words_context : wordsContext,
        pageLength : pageLength,
        pageNumber : pageNumber
      }, onResult, this.baseUrl+"api/results").send();
    }
    
    /**
     * Gets annotations on selected layers related to search results returned by a previous
     * call to {@link LabbcatView#getMatches}.
     * @param {string[]|object[]} matchIds A list of MatchIds, or a list of match
     * objects returned by {@link LabbcatView#getMatches} 
     * @param {string[]} layerIds A list of layer IDs.
     * @param {int} [targetOffset=0] The distance from the original target of the match, e.g.
     * <ul>
     *  <li>0 - find annotations of the match target itself</li>
     *  <li>1 - find annotations of the token immediately <em>after</em> match target</li>
     *  <li>-1 - find annotations of the token immediately <em>before</em> match target</li>
     * </ul>
     * @param {int} [annotationsPerLayer=1] The number of annotations on the given layer to
     * retrieve. In most cases, there's only one annotation available. However, tokens may,
     * for example, be annotated with `all possible phonemic transcriptions', in which case
     * using a value of greater than 1 for this parameter provides other phonemic
     * transcriptions, for tokens that have more than one.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: An array of arrays of Annotations, of
     * dimensions <var>matchIds</var>.length &times; (<var>layerIds</var>.length *
     * <var>annotationsPerLayer</var>). The first index matches the corresponding
     * index in <var>matchIds</var>. 
     */
    getMatchAnnotations(matchIds, layerIds, targetOffset, annotationsPerLayer, onResult) {
      if (typeof targetOffset === "function") { // (matchIds, layerIds, onResult)
        onResult = targetOffset;
        targetOffset = null;
        annotationsPerLayer = null;
      } else if (typeof annotationsPerLayer === "function") {
        // (matchIds, layerIds, targetOffset, onResult)
        onResult = annotationsPerLayer;
        annotationsPerLayer = null;
      }

      // check that an array of matches hasn't been passed.
      if (typeof matchIds[0] != "string" && matchIds[0].MatchId) {
        // convert the array of matches into an array of MatchIds
        matchIds = matchIds.map(match => match.MatchId);
      }
      
      if (exports.verbose) {
        console.log("getMatchAnnotations("+JSON.stringify(matchIds)+", "
                    +JSON.stringify(layerIds)+", "+targetOffset+", "
                    +annotationsPerLayer+")");
      }
      targetOffset = targetOffset || 0;
      annotationsPerLayer = annotationsPerLayer || 1;

      // create forms
      var fdUpload = new FormData();
      fdUpload.append("csvFieldDelimiter", ",");
      fdUpload.append("targetColumn", "MatchId");
      // api/results/upload expects an uploaded CSV file for MatchIds, 
      const uploadfile = "MatchId\n"+matchIds.join("\n");
      fdUpload.append("results", uploadfile, {
        filename: 'uploadfile.csv',
        contentType: 'text/csv',
        knownLength: uploadfile.length
      });
      
      var downloadResults = (threadId) => {
        this.createRequest(
          "getMatchAnnotations", {
            threadId: threadId,
            targetOffset: targetOffset,
            annotationsPerLayer: annotationsPerLayer,
            offsetThreshold: 0, // return all anchors
            csvFieldDelimiter: ",",
            csv_layer: layerIds
          }, (result, errors, messages, call, id) => {
            this.releaseTask(threadId, ()=>{});
            if (onResult) onResult(result.matches, errors, messages, call, id);
          },
          this.baseUrl+"api/results")
          .send();
      };
      
      if (!runningOnNode) {	
	// create HTTP request
	var xhr = new XMLHttpRequest();
	xhr.call = "getMatchAnnotations";
	xhr.id = transcript.name;
	xhr.onResult = (result, errors, messages, call, id) => {
          if (result && result.threadId) {
            downloadResults(result.threadId);
          } else {
	    onResult(result, errors, messages, "getMatchAnnotations");
          }
        };
	xhr.addEventListener("load", callComplete, false);
	xhr.addEventListener("error", callFailed, false);
	xhr.addEventListener("abort", callCancelled, false);	        
	xhr.open("POST", this.baseUrl + "api/getMatchAnnotations");
	if (this.username) {
	  xhr.setRequestHeader("Authorization", "Basic " + btoa(this.username + ":" + this.password))
	}
	xhr.setRequestHeader("Accept", "application/json");
	xhr.send(fdUpload);
      } else { // runningOnNode
	var urlParts = parseUrl(this.baseUrl + "api/results/upload");
	// for tomcat 8, we need to explicitly send the content-type and content-length headers...
	var labbcat = this;
        var password = this._password;
	fdUpload.getLength(function(something, contentLength) {
	  var requestParameters = {
	    port: urlParts.port,
	    path: urlParts.pathname,
	    host: urlParts.hostname,
	    headers: {
	      "Accept" : "application/json",
	      "content-length" : contentLength,
	      "Content-Type" : "multipart/form-data; boundary=" + fdUpload.getBoundary()
	    }
	  };
	  if (labbcat.username && password) {
	    requestParameters.auth = labbcat.username+':'+password;
	  }
	  if (/^https.*/.test(labbcat.baseUrl)) {
	    requestParameters.protocol = "https:";
	  }
          if (exports.verbose) {
            console.log("submit: " + labbcat.baseUrl + "edit/transcript/new");
          }
	  fdUpload.submit(requestParameters, function(err, res) {
	    var responseText = "";
	    if (!err) {
	      res.on('data',function(buffer) {
		//console.log('data ' + buffer);
		responseText += buffer;
	      });
	      res.on('end',function(){
                if (exports.verbose) console.log("response: " + responseText);
	        var result = null;
	        var errors = null;
	        var messages = null;
		try {
		  var response = JSON.parse(responseText);
		  result = response.model.result || response.model;
		  errors = response.errors;
		  if (errors && errors.length == 0) errors = null
		  messages = response.messages;
		  if (messages && messages.length == 0) messages = null
		} catch(exception) {
		  result = null
                  errors = ["" +exception+ ": " + labbcat.responseText];
                  messages = [];
		}
                if (result && result.threadId) {
                  downloadResults(result.threadId);
                } else {
		  onResult(result, errors, messages, "getMatchAnnotations");
                }
	      });
	    } else {
	      onResult(null, ["" +err+ ": " + labbcat.responseText], [], "getMatchAnnotations");
	    }
	    
	    if (res) res.resume();
	  });
	}); // got length
      } // runningOnNode
      // old API
      // // create form
      // var fd = new FormData();
      // fd.append("targetOffset", targetOffset);
      // fd.append("annotationsPerLayer", annotationsPerLayer);
      // fd.append("csvFieldDelimiter", ",");
      // fd.append("targetColumn", "0");
      // fd.append("copyColumns", "false");
      // for (let layerId of layerIds ) fd.append("layer", layerId);

      // // getMatchAnnotations expects an uploaded CSV file for MatchIds, 
      // const uploadfile = "MatchId\n"+matchIds.join("\n");
      // fd.append("uploadfile", uploadfile, {
      //   filename: 'uploadfile.csv',
      //   contentType: 'text/csv',
      //   knownLength: uploadfile.length
      // });

      // if (!runningOnNode) {	
      //   // create HTTP request
      //   var xhr = new XMLHttpRequest();
      //   xhr.call = "getMatchAnnotations";
      //   xhr.id = transcript.name;
      //   xhr.onResult = onResult;
      //   xhr.addEventListener("load", callComplete, false);
      //   xhr.addEventListener("error", callFailed, false);
      //   xhr.addEventListener("abort", callCancelled, false);	        
      //   xhr.open("POST", this.baseUrl + "api/getMatchAnnotations");
      //   if (this.username) {
      //     xhr.setRequestHeader("Authorization", "Basic " + btoa(this.username + ":" + this.password))
      //   }
      //   xhr.setRequestHeader("Accept", "application/json");
      //   xhr.send(fd);
      // } else { // runningOnNode
      //   var urlParts = parseUrl(this.baseUrl + "api/getMatchAnnotations");
      //   // for tomcat 8, we need to explicitly send the content-type and content-length headers...
      //   var labbcat = this;
      //   var password = this._password;
      //   fd.getLength(function(something, contentLength) {
      //     var requestParameters = {
      //       port: urlParts.port,
      //       path: urlParts.pathname,
      //       host: urlParts.hostname,
      //       headers: {
      //         "Accept" : "application/json",
      //         "content-length" : contentLength,
      //         "Content-Type" : "multipart/form-data; boundary=" + fd.getBoundary()
      //       }
      //     };
      //     if (labbcat.username && password) {
      //       requestParameters.auth = labbcat.username+':'+password;
      //     }
      //     if (/^https.*/.test(labbcat.baseUrl)) {
      //       requestParameters.protocol = "https:";
      //     }
      //     if (exports.verbose) {
      //       console.log("submit: " + labbcat.baseUrl + "edit/transcript/new");
      //     }
      //     fd.submit(requestParameters, function(err, res) {
      //       var responseText = "";
      //       if (!err) {
      //         res.on('data',function(buffer) {
      // 	  //console.log('data ' + buffer);
      // 	  responseText += buffer;
      //         });
      //         res.on('end',function(){
      //           if (exports.verbose) console.log("response: " + responseText);
      //           var result = null;
      //           var errors = null;
      //           var messages = null;
      // 	  try {
      // 	    var response = JSON.parse(responseText);
      // 	    result = response.model.result || response.model;
      // 	    errors = response.errors;
      // 	    if (errors && errors.length == 0) errors = null
      // 	    messages = response.messages;
      // 	    if (messages && messages.length == 0) messages = null
      // 	  } catch(exception) {
      // 	    result = null
      //             errors = ["" +exception+ ": " + labbcat.responseText];
      //             messages = [];
      // 	  }
      // 	  onResult(result, errors, messages, "getMatchAnnotations");
      //         });
      //       } else {
      //         onResult(null, ["" +err+ ": " + labbcat.responseText], [], "getMatchAnnotations");
      //       }
      
      //       if (res) res.resume();
      //     });
      //   }); // got length
      // } // runningOnNode
    }
    
    /**
     * Downloads WAV sound fragments.
     * <p>For convenience, the first three arguments, <var>transcriptIds</var>, 
     * <var>startOffsets</var>, and <var>endOffsets</var>, can be replaced by a single
     * array of match objects of the kind returned by {@link LabbcatView#getMatches}, in
     * which case the start/end times are the utterance boundaries - e.g.
     * <pre>labbcat.getMatches(threadId, wordsContext (result, e, m) => {
     *   labbcat.getMatchAnnotations(result.matches, sampleRate, dir, (files, e, m) => {
     *       ...
     *   });
     * });</pre>
     * @param {string[]} transcriptIds A list of transcript IDs (transcript names).
     * @param {float[]} startOffsets A list of start offsets, with one element for each
     * element in <var>transcriptIds</var>. 
     * @param {float[]} endOffsets A list of end offsets, with one element for each element in
     * <var>transcriptIds</var>. 
     * @param {int} [sampleRate] The desired sample rate, or null for no preference.
     * @param {string} [dir] A directory in which the files should be stored, or null
     * for a temporary folder.  If specified, and the directory doesn't exist, it will
     * be created.  
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: A list of WAV files. If <var>dir</var> is
     * null, these files will be stored under the system's temporary directory, so
     * once processing is finished, they should be deleted by the caller, or moved to
     * a more permanent location.  
     */
    getSoundFragments(transcriptIds, startOffsets, endOffsets, sampleRate, dir, onResult) {
      if (!runningOnNode) {
        onResult && onResult(
          null, ["getSoundFragments is not yet implemented for browsers"], [], // TODO
          "getSoundFragments");
        return;
      }
      
      // ensure transcriptIds is a list of strings, not a list of matches
      if (typeof transcriptIds[0] != "string" && transcriptIds[0].Transcript) {
        // convert the array of matches into an arrays of transcriptIds, startOffset,
        // and endOffsets...

        // shift remaining arguments to the right
        onResult = sampleRate
        dir = endOffsets
        sampleRate = startOffsets

        // create arrays
        startOffsets = transcriptIds.map(match => match.Line);
        endOffsets = transcriptIds.map(match => match.LineEnd);
        transcriptIds = transcriptIds.map(match => match.Transcript);
      }            

      if (transcriptIds.length != startOffsets.length || transcriptIds.length != endOffsets.length) {
        onResult && onResult(null, [
          "transcriptIds ("+transcriptIds.length +"), startOffsets ("+startOffsets.length
            +"), and endOffsets ("+endOffsets.length+") must be arrays of equal size."],
                             [], "getSoundFragments");
        return;
      }

      if (typeof sampleRate === "function") {
        // (transcriptIds, startOffsets, endOffsets, onResult)
        onResult = sampleRate;
        sampleRate = null;
        dir = null;
      } else if (typeof dir === "function") {
        onResult = dir;
        if (typeof sampleRate === "string") {
          // (transcriptIds, startOffsets, endOffsets, dir, onResult)
          dir = sampleRate;
          sampleRate = null;
        } else {
          // (transcriptIds, startOffsets, endOffsets, sampleRate, onResult)
          dir = null;
        }
      }
      if (exports.verbose) {
        console.log("getSoundFragments("+transcriptIds.length+" transcriptIds, "
                    +startOffsets.length+" startOffsets, "
                    +endOffsets.length+" endOffsets, "
                    +sampleRate+", "+dir+")");
      }

      if (dir == null) {
        dir = os.tmpdir();
      } else {
        if (!fs.existsSync(dir)) fs.mkdirSync(dir);
      }
      
      let fragments = [];
      let errors = [];
      
      // get fragments individually to ensure elements in result map 1:1 to element
      // in transcriptIds
      const url = this.baseUrl + "api/media/fragments";
      const lc = this;
      const nextFragment = function(i) {
        if (i < transcriptIds.length) { // next file
	  const xhr = new XMLHttpRequest();
          
	  let queryString = "?id="+encodeURIComponent(transcriptIds[i])
            +"&start="+encodeURIComponent(startOffsets[i])
            +"&end="+encodeURIComponent(endOffsets[i]);
          if (sampleRate) queryString += "&sampleRate="+sampleRate;
          queryString += "&prefix=true"; // TODO add function parameter for this
          
          if (exports.verbose) {
            console.log("GET: "+url + "?" + queryString + " as " + lc.username);
          }
	  xhr.open("GET", url + queryString, true);
	  if (lc.username) {
	    xhr.setRequestHeader(
              "Authorization", "Basic " + btoa(lc.username + ":" + lc._password))
 	  }
          
	  xhr.setRequestHeader("Accept", "audio/wav");
          // we want binary data, not text
          xhr.responseType = "arraybuffer";
          
	  xhr.addEventListener("error", function(evt) {
            if (exports.verbose) {
              console.log("getSoundFragments "+i+" ERROR: "+this.responseText);
            }
            errors.push("Could not get fragment "+i+": "+this.responseText);
            fragments.push(null); // add a blank element
            nextFragment(i+1);
          }, false);
          
	  xhr.addEventListener("load", function(evt) {
            if (exports.verbose) {
              console.log("getSoundFragments "+i+" loaded.");
            }
            // save the result to a file
            let fileName = transcriptIds[i]+"__"+startOffsets[i]+"-"+endOffsets[i]+".wav";
            let contentDisposition = this.getResponseHeader("content-disposition");
            if (contentDisposition != null) {
              // something like attachment; filename=blah.wav
              const equals = contentDisposition.indexOf("=");
              if (equals > 0) {
                fileName = contentDisposition.substring(equals + 1);
              }
            }
            const filePath = path.join(dir, fileName);
            fs.writeFile(filePath, Buffer.from(this.response), function(err) {
              if (err) {
                if (exports.verbose) {
                  console.log("getSoundFragments "+i+" SAVE ERROR: "+err);
                }
                errors.push("Could not save fragment "+i+": "+err);
              }
              // add the file name to the result
              if (exports.verbose) console.log("wrote file " + filePath);
              fragments.push(filePath); // add a blank element
              nextFragment(i+1);
            });
          }, false);
          
          xhr.send();
        } else { // there are no more triples
          if (onResult) {
            onResult(fragments, errors.length?errors:null, [], "getSoundFragments");
          }
        }
      }
      nextFragment(0);
    }
    
    /**
     * Get transcript fragments in a specified format.
     * <p>For convenience, the first three arguments, <var>transcriptIds</var>, 
     * <var>startOffsets</var>, and <var>endOffsets</var>, can be replaced by a single
     * array of match objects of the kind returned by {@link LabbcatView#getMatches}, in
     * which case the start/end times are the utterance boundaries - e.g.
     * <pre>labbcat.getMatches(threadId, wordsContext (result, e, m) => {
     *   labbcat.getFragments(result.matches, layerIds, mimeType, dir, (files, e, m) => {
     *       ...
     *   });
     * });</pre>
     * @param {string[]} transcriptIds A list of transcript IDs (transcript names).
     * @param {float[]} startOffsets A list of start offsets, with one element for
     * each element in <var>transcriptIds</var>. 
     * @param {float[]} endOffsets A list of end offsets, with one element for each element in
     * <var>transcriptIds</var>. 
     * @param {string[]} layerIds A list of IDs of annotation layers to include in the
     * fragment. 
     * @param {string} mimeType The desired format, for example "text/praat-textgrid" for Praat
     * TextGrids, "text/plain" for plain text, etc.
     * @param {string} [dir] A directory in which the files should be stored, or null
     * for a temporary folder.   If specified, and the directory doesn't exist, it will
     * be created.  
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be:  A list of files. If <var>dir</var> is null,
     * these files will be stored under the system's temporary directory, so once
     * processing is finished, they should be deleted by the caller, or moved to a
     * more permanent location. 
     */
    getFragments(transcriptIds, startOffsets, endOffsets, layerIds, mimeType, dir, onResult) {
      if (!runningOnNode) {
        onResult && onResult(
          null, ["getFragments is not yet implemented for browsers"], [], // TODO
          "getFragments");
        return;
      }
      
      // ensure transcriptIds is a list of strings, not a list of matches
      if (typeof transcriptIds[0] != "string" && transcriptIds[0].Transcript) {
        // convert the array of matches into an arrays of transcriptIds, startOffset,
        // and endOffsets...

        // shift remaining arguments to the right
        onResult = mimeType
        dir = layerIds
        mimeType = endOffsets
        layerIds = startOffsets

        // create arrays
        startOffsets = transcriptIds.map(match => match.Line);
        endOffsets = transcriptIds.map(match => match.LineEnd);
        transcriptIds = transcriptIds.map(match => match.Transcript);
      }
      
      if (transcriptIds.length != startOffsets.length || transcriptIds.length != endOffsets.length) {
        onResult && onResult(
          null,
          ["transcriptIds ("+transcriptIds.length +"), startOffsets ("+startOffsets.length
           +"), and endOffsets ("+endOffsets.length+") must be arrays of equal size."],
          [], "getFragments");
        return;
      }

      if (typeof dir === "function") {
        // (transcriptIds, startOffsets, endOffsets, layerIds, mimeType, onResult)
        onResult = dir;
        dir = null;
      }
      if (exports.verbose) {
        console.log("getFragments("+transcriptIds.length+" transcriptIds, "
                    +startOffsets.length+" startOffsets, "
                    +endOffsets.length+" endOffsets, "
                    +JSON.stringify(layerIds)+", "+mimeType+", "+dir+")");
      }
      
      if (dir == null) {
        dir = os.tmpdir();
      } else {
        if (!fs.existsSync(dir)) fs.mkdirSync(dir);
      }
      
      let fragments = [];
      let errors = [];
      
      // get fragments individually to ensure elements in result map 1:1 to element
      // in transcriptIds
      let url = this.baseUrl + "api/serialize/fragment?mimeType="+encodeURIComponent(mimeType);
      for (let layerId of layerIds) url += "&layerId=" + layerId;
      const lc = this;
      const nextFragment = function(i) {
        if (i < transcriptIds.length) { // next file
	  const xhr = new XMLHttpRequest();
          
	  let queryString = "&id="+encodeURIComponent(transcriptIds[i])
            +"&start="+encodeURIComponent(startOffsets[i])
            +"&end="+encodeURIComponent(endOffsets[i]);
          queryString += "&prefix=true"; // TODO add a function parameter for this
          
          if (exports.verbose) {
            console.log("GET: "+url + queryString + " as " + lc.username);
          }
	  xhr.open("GET", url + queryString, true);
	  if (lc.username) {
	    xhr.setRequestHeader(
              "Authorization", "Basic " + btoa(lc.username + ":" + lc._password))
 	  }
          
	  xhr.setRequestHeader("Accept", mimeType);
          // we want binary data, not text
          xhr.responseType = "arraybuffer";
          
	  xhr.addEventListener("error", function(evt) {
            if (exports.verbose) {
              console.log("getFragments "+i+" ERROR: "+this.responseText);
            }
            errors.push("Could not get fragment "+i+": "+this.responseText);
            fragments.push(null); // add a blank element
            nextFragment(i+1);
          }, false);
          
	  xhr.addEventListener("load", function(evt) {
            if (exports.verbose) {
              console.log("getSoundFragments "+i+" loaded.");
            }
            // save the result to a file
            let fileName = transcriptIds[i]+"__"+startOffsets[i]+"-"+endOffsets[i];
            let contentDisposition = this.getResponseHeader("content-disposition");
            if (contentDisposition != null) {
              // something like attachment; filename=blah.wav
              const equals = contentDisposition.indexOf("=");
              if (equals > 0) {
                fileName = contentDisposition.substring(equals + 1);
              }
            }
            const filePath = path.join(dir, fileName);
            fs.writeFile(filePath, Buffer.from(this.response), function(err) {
              if (err) {
                if (exports.verbose) {
                  console.log("getFragments "+i+" SAVE ERROR: "+err);
                }
                errors.push("Could not save fragment "+i+": "+err);
              }
              // add the file name to the result
              fragments.push(filePath); // add a blank element
              nextFragment(i+1);
            });
          }, false);
          
          xhr.send();
        } else { // there are no more triples
          if (onResult) {
            onResult(fragments, errors.length?errors:null, [], "getSoundFragments");
          }
        }
      }
      nextFragment(0);
    }

    /**
     * Gets transcript attribute values for given transcript IDs.
     * @param {string[]} transcriptIds A list of transcript IDs (transcript names).
     * @param {string[]} layerIds A list of layer IDs corresponding to transcript
     * attributes. In general, these are layers whose ID is prefixed 'transcript_',
     * however formally it's any layer where layer.parentId == 'graph' &&
     * layer.alignment == 0, which includes 'corpus' as well as transcript attribute layers.
     * @param {string} fileName The full path for the file where the results CSV
     * should be saved. 
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: The CSV file path - i.e. <var>fileName</var>
     * or null if the request failed.  
     */
    getTranscriptAttributes(transcriptIds, layerIds, fileName, onResult) {
      if (!runningOnNode) {
        onResult && onResult(
          null, ["getTranscriptAttributes is not yet implemented for browsers"], [], // TODO
          "getTranscriptAttributes");
        return;
      }
      if (exports.verbose) {
        console.log("getTranscriptAttributes("+transcriptIds.length+" transcriptIds, "
                    +JSON.stringify(layerIds)+")");
      }
      const xhr = new XMLHttpRequest();            
      const url = this.baseUrl + "api/attributes";            
      let queryString = "?layer=transcript";
      for (let id of layerIds) queryString += "&layer="+encodeURIComponent(id);
      for (let id of transcriptIds) queryString += "&id="+encodeURIComponent(id);
      if (exports.verbose) {
        console.log("GET: "+url + queryString + " as " + this.username);
      }
      xhr.open("GET", url + queryString, true);
      if (this.username) {
	xhr.setRequestHeader(
          "Authorization", "Basic " + btoa(this.username + ":" + this._password))
      }
      xhr.setRequestHeader("Accept", "text/csv");

      xhr.addEventListener("error", function(evt) {
        if (exports.verbose) {
          console.log("getTranscriptAttributes "+i+" ERROR: "+this.responseText);
        }
        fragments.push(null); // add a blank element
        if (onResult) {
          onResult(null, ["Could not get transcript attributes: "
                          +this.responseText], [], "getTranscriptAttributes");
        }
      }, false);
      
      xhr.addEventListener("load", function(evt) {
        if (exports.verbose) {
          console.log("getTranscriptAttributes loaded. " + JSON.stringify(this.response));
        }
        fs.writeFile(fileName, Buffer.from(xhr.responseText), function(err) {
          if (exports.verbose) {
            console.log("getTranscriptAttributes wrote file " + fileName);
          }
          let errors = null;
          if (err) {
            if (exports.verbose) {
              console.log("getTranscriptAttributes SAVE ERROR: "+err);
            }
            errors = ["Could not get transcript attributes: "+err];
          }
          onResult(fileName, errors, [], "getTranscriptAttributes");
        });
      }, false);
      
      xhr.send();
    }
    
    /**
     * Gets participant attribute values for given participant IDs.
     * @param {string[]} participantIds A list of participant IDs.
     * @param {string[]} layerIds A list of layer IDs corresponding to participant
     * attributes. In general, these are layers whose ID is prefixed 'participant_',
     * however formally it's any layer where layer.parentId == 'participant' &&
     * layer.alignment == 0.
     * @param {string} fileName The full path for the file where the results CSV
     * should be saved. 
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: The CSV file path - i.e. <var>fileName</var>
     * or null if the request failed.  
     */
    getParticipantAttributes(participantIds, layerIds, fileName, onResult) {
      if (!runningOnNode) {
        onResult && onResult(
          null, ["getParticipantAttributes is not yet implemented for browsers"], [], // TODO
          "getParticipantAttributes");
        return;
      }
      if (exports.verbose) {
        console.log("getParticipantAttributes("+participantIds.length+" participantIds, "
                    +JSON.stringify(layerIds)+")");
      }
      const xhr = new XMLHttpRequest();            
      const url = this.baseUrl + "participantsExport";            
      let queryString = "?type=participant&content-type=text/csv&csvFieldDelimiter=,";
      for (let id of layerIds) queryString += "&layer="+encodeURIComponent(id);
      for (let id of participantIds) queryString += "&participantId="+encodeURIComponent(id);
      if (exports.verbose) {
        console.log("GET: "+url + queryString + " as " + this.username);
      }
      xhr.open("GET", url + queryString, true);
      if (this.username) {
	xhr.setRequestHeader(
          "Authorization", "Basic " + btoa(this.username + ":" + this._password))
      }
      xhr.setRequestHeader("Accept", "text/csv");

      xhr.addEventListener("error", function(evt) {
        if (exports.verbose) {
          console.log("getParticipantAttributes "+i+" ERROR: "+this.responseText);
        }
        fragments.push(null); // add a blank element
        if (onResult) {
          onResult(null, ["Could not get participant attributes: "
                          +this.responseText], [], "getParticipantAttributes");
        }
      }, false);
      
      xhr.addEventListener("load", function(evt) {
        if (exports.verbose) {
          console.log("getParticipantAttributes loaded. " + JSON.stringify(this.response));
        }
        fs.writeFile(fileName, Buffer.from(xhr.responseText), function(err) {
          if (exports.verbose) {
            console.log("getParticipantAttributes wrote file " + fileName);
          }
          let errors = null;
          if (err) {
            if (exports.verbose) {
              console.log("getParticipantAttributes SAVE ERROR: "+err);
            }
            errors = ["Could not get participant attributes: "+err];
          }
          onResult(fileName, errors, [], "getParticipantAttributes");
        });
      }, false);
      
      xhr.send();
    }

    /**
     * Lists the descriptors of all registered serializers.
     * <p> Serializers are modules that export annotation structures as a specific file
     * format, e.g. Praat TextGrid, plain text, etc., so the <var>mimeType</var> of descriptors
     * reflects what <var>mimeType</var>s can be specified for {@link LabbcatView#getFragments}.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be: A list of the descriptors of all registered
     * serializers. 
     */
    getSerializerDescriptors(onResult) {
      this.createRequest("getSerializerDescriptors", null, onResult).send();
    }

    /**
     * Lists the descriptors of all registered deserializers.
     * <p> Deserializers are modules that import annotation structures from a specific file
     * format, e.g. Praat TextGrid, plain text, etc.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be: A list of the descriptors of all registered
     * deserializers. 
     */
    getDeserializerDescriptors(onResult) {
      this.createRequest("getDeserializerDescriptors", null, onResult).send();
    }

    /**
     * Lists the descriptors of all registered annotators.
     * <p> Annotators are modules that perform automated annotation of transcripts.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be: A list of the descriptors of all registered
     * annotators. 
     */
    getAnnotatorDescriptors(onResult) {
      this.createRequest("getAnnotatorDescriptors", null, onResult).send();
    }

    /**
     * Lists the descriptors of all registered transcribers.
     * <p> Transcribers are modules that perform automated transcription of recordings
     * that have not alreadye been transcribed.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be: A list of the descriptors of all registered
     * transcribers. 
     */
    getTranscriberDescriptors(onResult) {
      this.createRequest("getTranscriberDescriptors", null, onResult).send();
    }
    
    /**
     * Gets the value of the given system attribute.
     * @param {string} attribute Name of the attribute.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be: The given attribute, with name and value properties. 
     */
    getSystemAttribute(attribute, onResult) {
      this.createRequest(
        "systemattributes", null, onResult, this.baseUrl+"api/systemattributes/" + attribute)
        .send();
    }
    
    /**
     * Gets a list of currently-installed layer managers.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be: an array of objects with the following
     * structure:
     * <ul>
     *  <li> <q> layer_manager_id </q> : ID of the layer manager. </li>
     *  <li> <q> version </q> : The version of the installed implementation. </li>
     *  <li> <q> name </q> : The name for the layer manager. </li>
     *  <li> <q> description </q> : A brief description of what the layer manager does. </li>
     *  <li> <q> layer_type </q> : What kinds of layers the layer manager can process - a
     *           string composed of any of the following:
     *            <ul>
     *             <li><b> S </b> - segment layers </li>
     *             <li><b> W </b> - word layers </li>
     *             <li><b> M </b> - phrase (meta) layers </li>
     *             <li><b> F </b> - span (freeform) layers </li>
     *            </ul>
     *  </li>
     * </ul>
     */
    getLayerManagers(onResult) {
      this.createRequest(
        "layermanagers", null, onResult, this.baseUrl+"api/layers/managers")
        .send();
    }
    
    /**
     * Gets information about the current user, including the roles or groups they are
     * in.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be: The user record with an attribute called
     * "roles" which is an array of string role names. 
     */
    getUserInfo(onResult) {
      this.createRequest(
        "user", null, onResult, this.baseUrl+"api/user")
        .send();
    }

    /**
     * Lists configured items for the given dashboard.
     * <p> These are generally single statistics about the corpus that are displayed
     *   on the home page or the 'statistics' page, which are individually configurable.
     *   However items can also be links, or the output of a command.
     * @param {string} dashboard Which dashboard to get items for;
     * "home", "statistics", or "express"
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be: an array of item objects, each with the
     * following attributes:
     * <ul>
     *  <li> <q> item_id </q> : ID of the item. </li>
     *  <li> <q> type </q> : The type of the item: "link", "sql", or "exec". </li>
     *  <li> <q> label </q> : The item's text label. </li>
     *  <li> <q> icon </q> : The item's icon. </li>
     * </ul>
     * The items are not evaluated by this function.
     * To get item values, call {@link LabbcatView#getDashboardItem}.
     */
    getDashboardItems(dashboard, onResult) {
      dashboard = dashboard||"home";
      this.createRequest(
        "dashboard", null, onResult,
        this.baseUrl+"api/dashboard"+(dashboard=="home"?"":"/"+dashboard))
        .send();
    }

    /**
     * Gets the value of one dashboard item.
     * @param {number} id The item_id of the item, as returned by
     * {@link LabbcatView#getDashboardItems}
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be a string representing the value of the item.
     */
    getDashboardItem(id, onResult) {
      this.createRequest(
        "dashboard/item", null, onResult,
        this.baseUrl+"api/dashboard/item/"+id)
        .send();
    }
    /**
     * For HTK dictionary-filling, this starts a task (returning it's threadId) that
     * traverses the given utterances, looking for missing word pronunciations
     * @param {string} seriesId search.search_id for identifying the utterances
     * @param {string} tokenLayerId token layer ("orthography")
     * @param {string} annotationLayerId tokens with missing annotations on this layer
     * should be returned ("phonemes") the "model" returned is the threadId of the task
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be: the threadId of the task.
     * {@link LabbcatView#taskStatus} can be used to follow the progress of the task
     * until it's finished. Once done, the resultUrl can be invoked, which returns a
     * map from token labels to token IDs (one per type) i.e. the word that's missing,
     * with an ID for finding its first occurance 
     */
    missingAnnotations(seriesId, tokenLayerId, annotationLayerId, fragmentIds, onResult) {
      if (typeof fragmentIds === "function") {
        // (seriesId, tokenLayerId, annotationLayerId, onResult)
        onProgress = fragmentIds;
        fragmentIds = null;
      }
      this.createRequest(
        "missingAnnotations", {
          seriesId : seriesId,
          utterance : fragmentIds,
          tokenLayerId : tokenLayerId,
          annotationLayerId : annotationLayerId
        }, onResult, this.baseUrl+"api/missingAnnotations").send();
    }

    /**
     * Lists generic dictionaries published by layer managers.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be an object whose keys are layer manager IDs, with
     * each value being an array of generic dictionary IDs for that layer manager.
     */
    getDictionaries(onResult) {
      this.createRequest(
        "getDictionaries", null, onResult, this.baseUrl+"api/dictionaries").send();
    }

    /**
     * For HTK dictionary-filling, this looks up some given words to get their entries.
     * @param {string} layerId The dictionary of this layer will be used.
     * @param {string} labels Space-separated list of words to look up.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be: an object with the following structure:
     * returned has the following structure:
     * <ul>
     *  <li><b> words </b> - object where each key is the word, and each value is an
     *                       array of entries for that word 
     *  <li><b> combined </b> - the first entry of each word, concatenated together
     *                          with a hyphen separator
     * </ul>
     */
    dictionaryLookup(layerId, labels, onResult) {
      this.createRequest(
        "lookup", {
          layerId : layerId,
          labels : labels
        }, onResult, this.baseUrl+"api/dictionary/lookup").send();
    }

    /**
     * For HTK dictionary-filling, this uses a layer dictionary to suggest missing entries.
     * @param {string} layerId The dictionary of this layer will be used.
     * @param {string} labels Space-separated list of words to suggest entries for.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which will be: an object with the following structure:
     * returned has the following structure:
     * <ul>
     *  <li><b> words </b> - object where each key is the word, and each value is a
     *                       array of suggestions for that word (with 0 or 1 elements)
     * </ul>
     */
    dictionarySuggest(layerId, labels, onResult) {
      this.createRequest(
        "suggest", {
          layerId : layerId,
          labels : labels
        }, onResult, this.baseUrl+"api/dictionary/suggest").send();
    }

    /**
     * Process with Praat.
     * @param {file|string} csv The results file to upload. In a browser, this
     * must be a file object, and in Node, it must be the full path to the file. 
     * @param {int} transcriptColumn CSV column index of the transcript name. 
     * @param {int} participantColumn CSV column index of the participant name. 
     * @param {int} startTimeColumn CSV column index of the start time. 
     * @param {int} endTimeColumn CSV column index of the end time name. 
     * @param {number} windowOffset How much surrounsing context to include, in seconds.
     * @param {boolean} passThroughData Whether to include all CSV columns from the
     * input file in the output file. 
     * @param {object} measurementParameters Parameters that define what to measure
     * and output. All parameters are optional, and include:
     * <dl>
     *
     * <dt> extractF1 (boolean) </dt><dd> Extract F1. (default: false) </dd>
     * <dt> extractF2 (boolean) </dt><dd> Extract F2. (default: false) </dd>
     * <dt> extractF3 (boolean) </dt><dd> Extract F3. (default: false) </dd>
     * <dt> samplePoints (string) </dt><dd> Space-delimited series of real
     *   numbers between 0 and 1, specifying the proportional time points to measure
     *   formant at. e.g. "0.5" will measure only the mid-point, "0 0.2 0.4 0.6 0.8 1"
     *   will measure six points evenly spread across the duration of the segment,
     *   etc. (default: "0.5")</dd>
     * <dt> formantCeilingDefault (int) </dt><dd> Maximum Formant by default. (default:
     *   550) </dd>
     * <dt> formantDifferentiationLayerId (string) </dt><dd> Participant attribute
     *   layer ID for differentiating formant settings; this will typically be
     *   "participant_gender" but can be any participant attribute layer. </dd>
     * <dt> formantOtherPattern (string[]) </dt><dd> Array of regular expression
     *   strings to match against the value of that attribute identified by
     *   <var>formantDifferentiationLayerId</var>. If the participant's attribute value
     *   matches the pattern for an element in this array, the corresponding element in
     *   <var>formantCeilingOther</var> will be used for that participant. </dd>
     * <dt> formantCeilingOther (int[]) </dt><dd> Values to use as the formant ceiling
     *   for participants who's attribute value matches the corresponding regular
     *   expression in <var>formantOtherPattern</var></dd>
     * <dt> scriptFormant (string) </dt><dd> Formant extraction script command.
     *   (default: "To Formant (burg)... 0.0025 5 formantCeiling 0.025 50") </dd>
     * 
     * <dt> useFastTrack (boolean) </dt><dd> Use the FastTrack plugin to generate
     *   optimum, smoothed formant tracks. (default: false)</dd>
     * <dt> fastTrackDifferentiationLayerId (string) </dt><dd> Participant attribute
     *   layer ID for differentiating fastTrack settings; this will typically be
     *   "participant_gender" but can be any participant attribute layer. </dd>
     * <dt> fastTrackOtherPattern (string[]) </dt><dd> Array of regular expression
     *   strings to match against the value of that attribute identified by
     *   <var>fastTrackDifferentiationLayerId</var>. If the participant's attribute value
     *   matches the pattern for an element in this array, the corresponding element in
     *   <var>fastTrackCeilingOther</var> will be used for that participant. </dd>
     * <dt> fastTrackLowestAnalysisFrequencyDefault (int) </dt><dd> Fast Track lowest
     *   analysis frequency by default. </dd>
     * <dt> fastTrackLowestAnalysisFrequencyOther (int[]) </dt><dd> Values to use as
     *   the Fast Track lowest analysis frequency for participants who's attribute
     *   value matches the corresponding regular expression in
     *   <var>fastTrackOtherPattern</var>.</dd> 
     * <dt> fastTrackHighestAnalysisFrequencyDefault (int) </dt><dd> Fast Track highest
     *   analysis frequency by default. </dd>
     * <dt> fastTrackHighestAnalysisFrequencyOther (int[]) </dt><dd> Values to use as
     *   the Fast Track highest analysis frequency for participants who's attribute
     *   value matches the corresponding regular expression in
     *   <var>fastTrackOtherPattern</var>.</dd> 
     * <dt> fastTrackTimeStep </dt>
     *       <dd> Fast Track time_step global setting. </dd>
     * <dt> fastTrackBasisFunctions </dt>
     *       <dd> Fast Track basis_functions global setting - "dct". </dd>
     * <dt> fastTrackErrorMethod </dt>
     *       <dd> Fast Track error_method global setting - "mae". </dd>
     * <dt> fastTrackTrackingMethod </dt>
     *       <dd> Fast Track tracking_method parameter for trackAutoselectProcedure; "burg" or
     *       "robust". </dd> 
     * <dt> fastTrackEnableF1FrequencyHeuristic ("true" or "false") </dt>
     *       <dd> Fast Track enable_F1_frequency_heuristic global setting. </dd>
     * <dt> fastTrackMaximumF1FrequencyValue </dt>
     *       <dd> Fast Track maximum_F1_frequency_value global setting. </dd>
     * <dt> fastTrackEnableF1BandwidthHeuristic </dt>
     *       <dd> Fast Track enable_F1_bandwidth_heuristic global setting. </dd>
     * <dt> fastTrackMaximumF1BandwidthValue </dt>
     *       <dd> Fast Track maximum_F1_bandwidth_value global setting. </dd>
     * <dt> fastTrackEnableF2BandwidthHeuristic ("true" or "false") </dt>
     *       <dd> Fast Track enable_F2_bandwidth_heuristic global setting. </dd>
     * <dt> fastTrackMaximumF2BandwidthValue </dt>
     *       <dd> Fast Track maximum_F2_bandwidth_value global setting. </dd>
     * <dt> fastTrackEnableF3BandwidthHeuristic ("true" or "false") </dt>
     *       <dd> Fast Track enable_F3_bandwidth_heuristic global setting.. </dd>
     * <dt> fastTrackMaximumF3BandwidthValue </dt>
     *       <dd> Fast Track maximum_F3_bandwidth_value global setting. </dd>
     * <dt> fastTrackEnableF4FrequencyHeuristic ("true" or "false") </dt>
     *       <dd> Fast Track enable_F4_frequency_heuristic global setting. </dd>
     * <dt> fastTrackMinimumF4FrequencyValue </dt>
     *       <dd> Fast Track minimum_F4_frequency_value global setting. </dd>
     * <dt> fastTrackEnableRhoticHeuristic ("true" of "false") </dt>
     *       <dd> Fast Track enable_rhotic_heuristic global setting. </dd>
     * <dt> fastTrackEnableF3F4ProximityHeuristic </dt>
     *       <dd> Fast Track enable_F3F4_proximity_heuristic global setting. </dd>
     * <dt> fastTrackNumberOfSteps </dt>
     *       <dd> Fast Track number of steps. </dd>
     * <dt> fastTrackNumberOfCoefficients </dt>
     *       <dd> Fast Track number of coefficients for the regression function. </dd>
     * <dt> fastTrackNumberOfFormants </dt>
     *       <dd> Fast Track number of formants. </dd>
     * <dt> fastTrackCoefficients ("true" or "false") </dt>
     *       <dd> Whether to return the regression coefficients from FastTrack. </dd>
     * 
     * <dt> extractMinimumPitch (boolean) </dt><dd> Extract minimum pitch. 
     *   (default: false) </dd>
     * <dt> extractMeanPitch (boolean) </dt><dd> Extract mean pitch. (default: false) </dd>
     * <dt> extractMaximumPitch (boolean) </dt><dd> Extract maximum pitch. 
     *   (default: false) </dd>
     * <dt> pitchFloorDefault (int) </dt><dd> Pitch Floor by default. (default: 60) </dd>
     * <dt> pitchCeilingDefault (int) </dt><dd> Pitch Ceiling by default. (default: 500) </dd>
     * <dt> voicingThresholdDefault (number) </dt><dd> Voicing Threshold by default. 
     *   (default: 0.5) </dd>
     * <dt> pitchDifferentiationLayerId (string) </dt><dd> Participant attribute
     *   layer ID for differentiating pitch settings; this will typically be
     *   "participant_gender" but can be any participant attribute layer. </dd>
     * <dt> pitchOtherPattern (string[]) </dt><dd> Array of regular expression
     *   strings to match against the value of that attribute identified by
     *   <var>pitchDifferentiationLayerId</var>. If the participant's attribute value
     *   matches the pattern for an element in this array, the corresponding element in
     *   <var>pitchFloorOther</var>, <var>pitchCeilingOther</var>, and
     *   <var>voicingThresholdOther</var> will be used for that participant. </dd> 
     * <dt> pitchFloorOther (int[]) </dt><dd> Values to use as the pitch floor
     *   for participants who's attribute value matches the corresponding regular
     *   expression in <var>pitchOtherPattern</var></dd>
     * <dt> pitchCeilingOther (int[]) </dt><dd> Values to use as the pitch ceiling
     *   for participants who's attribute value matches the corresponding regular
     *   expression in <var>pitchOtherPattern</var></dd>
     * <dt> voicingThresholdOther (int[]) </dt><dd> Values to use as the voicing threshold
     *   for participants who's attribute value matches the corresponding regular
     *   expression in <var>pitchOtherPattern</var></dd>
     * <dt> scriptPitch (string) </dt><dd> Pitch extraction script command.
     *   (default: 
     * "To Pitch (ac)...  0 pitchFloor 15 no 0.03 voicingThreshold 0.01 0.35 0.14 pitchCeiling") </dd>
     * 
     * <dt> extractMaximumIntensity (boolean) </dt><dd> Extract maximum intensity. 
     *   (default: false) </dd>
     * <dt> intensityPitchFloorDefault (int) </dt><dd> Pitch Floor by default. 
     *   (default: 60) </dd>
     * <dt> intensityDifferentiationLayerId (string) </dt><dd> Participant attribute
     *   layer ID for differentiating intensity settings; this will typically be
     *   "participant_gender" but can be any participant attribute layer. </dd>
     * <dt> intensityOtherPattern (string[]) </dt><dd> Array of regular expression
     *   strings to match against the value of that attribute identified by
     *   <var>intensityDifferentiationLayerId</var>. If the participant's attribute value
     *   matches the pattern for an element in this array, the corresponding element in
     *   <var>intensityPitchFloorOther</var> will be used for that participant. </dd> 
     * <dt> intensityPitchFloorOther (int[]) </dt><dd> Values to use as the pitch floor
     *   for participants who's attribute value matches the corresponding regular
     *   expression in <var>intensityPitchOtherPattern</var></dd>
     * <dt> scriptIntensity (string) </dt><dd> Pitch extraction script command.
     *   (default: "To Intensity... intensityPitchFloor 0 yes") </dd>
     * 
     * <dt> extractCOG1 (boolean) </dt><dd> Extract COG 1. (default: false) </dd>
     * <dt> extractCOG2 (boolean) </dt><dd> Extract COG 2. (default: false) </dd>
     * <dt> extractCOG23 (boolean) </dt><dd> Extract COG 2/3. (default: false) </dd>
     *
     * <dt> script (string) </dt><dd> A user-specified custom Praat script to execute
     *   on each segment. </dd> 
     * <dt> attributes (string[]) </dt><dd> A list of participant attribute layer IDs
     *   to include as variables for the custom Praat <var>script</var>. </dd> 
     * </dl>
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: An object with one attribute,
     * <var>threadId</var>. 
     * @param onProgress Invoked on XMLHttpRequest progress.
     */
    praat(
      csv, transcriptColumn, participantColumn, startTimeColumn, endTimeColumn,
      windowOffset, passThroughData, measurementParameters,
      onResult, onProgress) {
      
      if (exports.verbose) {
        console.log(
          "praat("
            +csv+", "+transcriptColumn+", "+participantColumn+", "
            +startTimeColumn+", "+endTimeColumn+", "+windowOffset+", "
            +passThroughData+", "+JSON.stringify(measurementParameters)+")");
      }

      // create form
      var fd = new FormData();
      fd.append("transcriptColumn", transcriptColumn);
      fd.append("participantColumn", participantColumn);
      fd.append("startTimeColumn", startTimeColumn);
      fd.append("endTimeColumn", endTimeColumn);
      fd.append("windowOffset", windowOffset);
      for (var parameter in measurementParameters) {
        var value = measurementParameters[parameter];
        if (Array.isArray(value)) {
          for (var element of value) {
            fd.append(parameter, element);
          } // next element
        } else { // simple value
          fd.append(parameter, value);
        }
      } // next parameter

      if (!runningOnNode) {	
	fd.append("csv", csv);
	// create HTTP request
	var xhr = new XMLHttpRequest();
	xhr.call = "praat";
	xhr.onResult = onResult;
	xhr.addEventListener("load", callComplete, false);
	xhr.addEventListener("error", callFailed, false);
	xhr.addEventListener("abort", callCancelled, false);	        
	xhr.upload.addEventListener("progress", onProgress, false);
	xhr.open("POST", this.baseUrl + "api/praat");
	if (this.username) {
	  xhr.setRequestHeader("Authorization", "Basic " + btoa(this.username + ":" + this.password))
	}
	xhr.setRequestHeader("Accept", "application/json");
	xhr.send(fd);
      } else { // runningOnNode
        var csvName = csv.replace(/.*\//g, "");
        if (exports.verbose) console.log("csvName: " + csvName);
	fd.append("csv", 
		  fs.createReadStream(csv).on('error', function(){
		    onResult(null, ["Invalid file: " + csvName], [], "praat", csvName);
		  }), csvName);

	var urlParts = parseUrl(this.baseUrl + "api/praat");
	// for tomcat 8, we need to explicitly send the content-type and content-length headers...
	var labbcat = this;
        var password = this._password;
	fd.getLength(function(something, contentLength) {
	  var requestParameters = {
	    port: urlParts.port,
	    path: urlParts.pathname,
	    host: urlParts.hostname,
	    headers: {
	      "Accept" : "application/json",
	      "content-length" : contentLength,
	      "Content-Type" : "multipart/form-data; boundary=" + fd.getBoundary()
	    }
	  };
	  if (labbcat.username && password) {
	    requestParameters.auth = labbcat.username+':'+password;
	  }
	  if (/^https.*/.test(labbcat.baseUrl)) {
	    requestParameters.protocol = "https:";
	  }
          if (exports.verbose) {
            console.log("submit: " + labbcat.baseUrl + "api/praat");
          }
	  fd.submit(requestParameters, function(err, res) {
	    var responseText = "";
	    if (!err) {
	      res.on('data',function(buffer) {
		//console.log('data ' + buffer);
		responseText += buffer;
	      });
	      res.on('end',function(){
                if (exports.verbose) console.log("response: " + responseText);
	        var result = null;
	        var errors = null;
	        var messages = null;
		try {
		  var response = JSON.parse(responseText);
		  result = response.model.result || response.model;
		  errors = response.errors;
		  if (errors && errors.length == 0) errors = null
		  messages = response.messages;
		  if (messages && messages.length == 0) messages = null
		} catch(exception) {
		  result = null
                  errors = ["" +exception+ ": " + labbcat.responseText];
                  messages = [];
		}
		onResult(result, errors, messages, "getMatchAnnotations");
	      });
	    } else {
	      onResult(null, ["" +err+ ": " + labbcat.responseText], [], "praat");
	    }
	    
	    if (res) res.resume();
	  });
	}); // got length
      } // runningOnNode
    }
    
    /**
     * Concatenates annotation labels for given labels contained in given time intervals.
     * @param {file|string} csv The results file to upload. In a browser, this
     * must be a file object, and in Node, it must be the full path to the file. 
     * @param {int} transcriptColumn CSV column index of the transcript name. 
     * @param {int} participantColumn CSV column index of the participant name. 
     * @param {int} startTimeColumn CSV column index of the start time. 
     * @param {int} endTimeColumn CSV column index of the end time name.
     * @param {string[]} layerId IDs of layers to extract.
     * @param {boolean} passThroughData Whether to include all CSV
     * columns from the input file in the output file. 
     * @param {string} labelDelimiter Delimiter to use between
     * labels. Defaults to a space " ". 
     * @param {string} containment "entire" if the annotations must be
     * entirely between the start and end times, "partial" if they can
     * extend before the start or after the end.  
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: An object with one attribute,
     * <var>threadId</var>. 
     * @param onProgress Invoked on XMLHttpRequest progress.
     */
    intervalAnnotations(
      csv, transcriptColumn, participantColumn, startTimeColumn, endTimeColumn, layerId,
      passThroughData, labelDelimiter, containment,
      onResult, onProgress) {
      if (typeof passThroughData === "function") {
        // (csv, transcriptColumn, participantColumn, startTimeColumn,
        // endTimeColumn, layerId, onResult, onProgress)
        onResult = passThroughData;
        onProgress = labelDelimiter;
        passThroughData = false;
        labelDelimiter = " ";
        containment = "entire";
      }
      
      if (exports.verbose) {
        console.log(
          "intervalAnnotations("
            +csv+", "+transcriptColumn+", "+participantColumn+", "
            +startTimeColumn+", "+endTimeColumn+", "+JSON.stringif(layerIds)+", "
            +passThroughData+", "+labelDelimiter+", "+containment+")");
      }

      // create form
      var fd = new FormData();
      fd.append("transcriptColumn", transcriptColumn);
      fd.append("participantColumn", participantColumn);
      fd.append("startTimeColumn", startTimeColumn);
      fd.append("endTimeColumn", endTimeColumn);
      for (let l of layerId) fd.append("layerId", l);
      fd.append("passThroughData", passThroughData);
      fd.append("labelDelimiter", labelDelimiter);
      fd.append("containment", containment);

      if (!runningOnNode) {	
	fd.append("csv", csv);
	// create HTTP request
	var xhr = new XMLHttpRequest();
	xhr.call = "intervalAnnotations";
	xhr.onResult = onResult;
	xhr.addEventListener("load", callComplete, false);
	xhr.addEventListener("error", callFailed, false);
	xhr.addEventListener("abort", callCancelled, false);	        
	xhr.upload.addEventListener("progress", onProgress, false);
	xhr.open("POST", this.baseUrl + "api/annotation/intervals");
	if (this.username) {
	  xhr.setRequestHeader("Authorization", "Basic " + btoa(this.username + ":" + this.password))
	}
	xhr.setRequestHeader("Accept", "application/json");
	xhr.send(fd);
      } else { // runningOnNode
        var csvName = csv.replace(/.*\//g, "");
        if (exports.verbose) console.log("csvName: " + csvName);
	fd.append("csv", 
		  fs.createReadStream(csv).on('error', function(){
		    onResult(null, ["Invalid file: " + csvName], [], "praat", csvName);
		  }), csvName);

	var urlParts = parseUrl(this.baseUrl + "api/praat");
	// for tomcat 8, we need to explicitly send the content-type and content-length headers...
	var labbcat = this;
        var password = this._password;
	fd.getLength(function(something, contentLength) {
	  var requestParameters = {
	    port: urlParts.port,
	    path: urlParts.pathname,
	    host: urlParts.hostname,
	    headers: {
	      "Accept" : "application/json",
	      "content-length" : contentLength,
	      "Content-Type" : "multipart/form-data; boundary=" + fd.getBoundary()
	    }
	  };
	  if (labbcat.username && password) {
	    requestParameters.auth = labbcat.username+':'+password;
	  }
	  if (/^https.*/.test(labbcat.baseUrl)) {
	    requestParameters.protocol = "https:";
	  }
          if (exports.verbose) {
            console.log("submit: " + labbcat.baseUrl + "api/annotation/intervals");
          }
	  fd.submit(requestParameters, function(err, res) {
	    var responseText = "";
	    if (!err) {
	      res.on('data',function(buffer) {
		//console.log('data ' + buffer);
		responseText += buffer;
	      });
	      res.on('end',function(){
                if (exports.verbose) console.log("response: " + responseText);
	        var result = null;
	        var errors = null;
	        var messages = null;
		try {
		  var response = JSON.parse(responseText);
		  result = response.model.result || response.model;
		  errors = response.errors;
		  if (errors && errors.length == 0) errors = null
		  messages = response.messages;
		  if (messages && messages.length == 0) messages = null
		} catch(exception) {
		  result = null
                  errors = ["" +exception+ ": " + labbcat.responseText];
                  messages = [];
		}
		onResult(result, errors, messages, "intervalAnnotations");
	      });
	    } else {
	      onResult(null, ["" +err+ ": " + labbcat.responseText], [], "praat");
	    }
	    
	    if (res) res.resume();
	  });
	}); // got length
      } // runningOnNode
    }
    
    /**
     * Supplies a list of automation tasks for the identified annotator.
     * @param {string} annotatorId The ID of the annotator that will perform the task.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var>, which is a map of task IDs to descriptions.
     */
    getAnnotatorTasks(annotatorId, onResult) {
      this.createRequest(
        "getAnnotatorTasks", {
          annotatorId: annotatorId
        }, onResult)
        .send();
    }
    
    /**
     * Supplies the given task's parameter string.
     * @param {string} taskId The ID of the task, which must not already exist.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var>, which is the task parameters, serialized as a string.
     */
    getAnnotatorTaskParameters(taskId, onResult) {
      this.createRequest(
        "getAnnotatorTaskParameters", {
          taskId: taskId
        }, onResult)
        .send();
    }
    
    /**
     * Reads a list of category records.
     * @param {string} class_id What attributes to read; "transcript" or "participant". 
     * @param {int} [pageNumber] The zero-based  page of records to return (if null, all
     * records will be returned). 
     * @param {int} [pageLength] The length of pages (if null, the default page length is 20).
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: A list of category records with the following
     * attributes:
     * <dl>
     *  <dt> class_id </dt> <dd> The class_id of the category. </dd>
     *  <dt> category </dt> <dd> The name/id of the category. </dd>
     *  <dt> description </dt> <dd> The description of the category. </dd>
     *  <dt> display_order </dt> <dd> Where the category appears among other categories. </dd>
     * </dl>
     */
    readOnlyCategories(class_id, pageNumber, pageLength, onResult) {
      if (typeof pageNumber === "function") { // (onResult)
        onResult = pageNumber;
        pageNumber = null;
        pageLength = null;
      } else if (typeof l === "function") { // (p, onResult)
        onResult = l;
        pageLength = null;
      }
      if (class_id == "participant") class_id = "speaker";
      this.createRequest(
        `categories/${class_id}`, {
          pageNumber:pageNumber,
          pageLength:pageLength
        }, onResult, `${this.baseUrl}api/categories/${class_id}`)
        .send();
    }

    /**
     * Reads the current license agreement (HTML) document.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be a string containing the HTML document.
     */
    readAgreement(onResult) {
      this.createRequest(
        "readAgreement", null, onResult, `${this.baseUrl}agreement.html`, "GET", null, null, true)
        .send();
    }
    
    /**
     * Gets the transcript of a given utterance, for possible correction suggestion using
     * {@link #utteranceSuggestion}.
     * @param {string} transcriptId The ID of the transcript.
     * @param {string} utteranceId The utterance's ID.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> with a "text" attribute, which is the plain-text
     * representation of the utterance, including word annotations and possibly
     * noise, comment, pronunciation etc. annotations using the configured transcript
     * conventions.
     */
    utteranceForSuggestion(transcriptId, utteranceId, onResult) {
      this.createRequest(
        "utteranceForSuggestion", {
          transcriptId : transcriptId,
          utteranceId : utteranceId
        }, onResult, this.baseUrl+"api/utterance/correction").send();
    }
    
    /**
     * Submits a suggestion for correction of the transcript of a given utterance.
     * @param {string} transcriptId The ID of the transcript.
     * @param {string} utteranceId The utterance's ID.
     * @param {string} text The corrected version of the transcript for the utterance.
     * This may include noise, comment, pronunciation etc. annotations using the
     * configured transcript conventions.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> with a "threadId" attribute, which is the ID of the task
     * regenerating layers based on the new transcript.
     */
    utteranceSuggestion(transcriptId, utteranceId, text, onResult) {
      this.createRequest(
        "utterance/correction", null, onResult, null, "POST", // TODO should be PUT
        this.baseUrl+"api/", "application/x-www-form-urlencoded;charset=\"utf-8\"")
        .send(this.parametersToQueryString({
          transcriptId : transcriptId,
          utteranceId : utteranceId,
          text : text
        }));
    }
    
  } // class LabbcatView

  // LabbcatEdit class - read/write "edit" access

  /**
   * Read/write interaction with LaBB-CAT corpora, based on the  
   * <a href="https://nzilbb.github.io/ag/javadoc/nzilbb/ag/IGraphStore.html">nzilbb.ag.IGraphStore</a>.
   * interface.
   * <p>This class inherits the <em>read-only</em> operations of LabbcatView
   * and adds some <em>write</em> operations for updating data.
   * @example
   * // create annotation store client
   * const store = new LabbcatEdit("https://labbcat.canterbury.ac.nz", "demo", "demo");
   * // get a corpus
   * store.getCorpusIds((corpora, errors, messages, call)=>{ 
   *     console.log("transcripts in: " + corpora[0]); 
   *     store.getTranscriptIdsInCorpus(corpora[0], (ids, errors, messages, call, id)=>{ 
   *         console.log("Deleting all transcripts in " + id));
   *         for (i in ids) {
   *           store.deleteTranscript(ids[i], (ids, errors, messages, call, id)=>{ 
   *               console.log("deleted " + id);
   *             });
   *         }
   *       });
   *   });
   * @extends LabbcatView
   * @author Robert Fromont robert@fromont.net.nz
   */
  class LabbcatEdit extends LabbcatView{
    
    /**
     * The graph store URL - e.g. https://labbcat.canterbury.ac.nz/demo/api/edit/store/
     */
    get storeEditUrl() {
      return this._storeEditUrl;
    }
    /** 
     * Create a store client 
     * @param {string} baseUrl The LaBB-CAT base URL (i.e. the address of the 'home' link)
     * @param {string} username The LaBB-CAT user name.
     * @param {string} password The LaBB-CAT password.
     */
    constructor(baseUrl, username, password) {
      super(baseUrl, username, password);
      this._storeEditUrl = this.baseUrl + "api/edit/store/";
    }

    /**
     * Saves changes to the given transcript annotation graph object, which was
     * previously returned from {@link #getTranscript}. 
     * <em>NB</em> this not be confused with the methods that upload a file:
     * {@link #newTranscript} and {@link #updateTranscript}.
     * <em>NB</em> Currently only transcript attributes can be updated.
     * <p> The graph can be partial e.g. include only some of the layers that the
     * stored version of the transcript contains. 
     * @param transcript The transcript to save.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: true if changes were saved, false if there
     * were no changes to save.
     * @see LabbcatView#getTranscript
     */
    saveTranscript(transcript, onResult) {
      this.createRequest(
        "saveTranscript", null, onResult, null, "POST",
        this.storeEditUrl, "application/json")
        .send(JSON.stringify(transcript));
    }
    
    /**
     * Saves the given media for the given transcript
     * @param {string} id The transcript ID
     * @param {file|string} media The media to upload. In a browser, this must be
     * a file object, and in Node, it must be the full path to the file.
     * @param {string} trackSuffix The track suffix of the media.
     * @param {resultCallback} onResult Invoked when the request has returned a result.
     * @param onProgress Invoked on XMLHttpRequest progress.
     */
    saveMedia(id, media, trackSuffix, onResult, onProgress) {
      if (typeof trackSuffix === "function") {
        // (id, mediaUrl, onResult, onProgress)
        onProgress = onResult;
        onResult = trackSuffix;
        trackSuffix = null;
      }
      if (exports.verbose) {
        console.log("saveMedia(" + id + ", " + media + ", " + trackSuffix + ")");
      }
      
      // create form
      var fd = new FormData();
      fd.append("id", id);
      if (trackSuffix) fd.append("trackSuffix", trackSuffix);
      
      if (!runningOnNode) {
	fd.append("media", media);
        
	// create HTTP request
	var xhr = new XMLHttpRequest();
	xhr.call = "saveMedia";
	xhr.id = id;
	xhr.onResult = onResult;
	xhr.addEventListener("load", callComplete, false);
	xhr.addEventListener("error", callFailed, false);
	xhr.addEventListener("abort", callCancelled, false);
	xhr.upload.addEventListener("progress", onProgress, false);
	xhr.upload.id = id; // for knowing what status to update during events
	
	xhr.open("POST", this._storeEditUrl + "saveMedia");
	if (this.username) {
	  xhr.setRequestHeader("Authorization", "Basic " + btoa(this.username + ":" + this.password))
	}
	xhr.setRequestHeader("Accept", "application/json");
	xhr.send(fd);
      } else { // runningOnNode
	
	// on node.js, files are actually paths
	var mediaName = media.replace(/.*\//g, "");
        if (exports.verbose) console.log("mediaName: " + mediaName);
        
	fd.append("media", 
		  fs.createReadStream(media).on('error', function(){
		    onResult(null, ["Invalid media: " + mediaName], [], "saveMedia", id);
		  }), mediaName);
        var urlParts = parseUrl(this._storeEditUrl + "saveMedia");
	// for tomcat 8, we need to explicitly send the content-type and content-length headers...
	var labbcat = this;
        var password = this._password;
	fd.getLength(function(something, contentLength) {
	  var requestParameters = {
	    port: urlParts.port,
	    path: urlParts.pathname,
	    host: urlParts.hostname,
	    headers: {
	      "Accept" : "application/json",
	      "content-length" : contentLength,
	      "Content-Type" : "multipart/form-data; boundary=" + fd.getBoundary()
	    }
	  };
	  if (labbcat.username && password) {
	    requestParameters.auth = labbcat.username+':'+password;
	  }
	  if (/^https.*/.test(labbcat.baseUrl)) {
	    requestParameters.protocol = "https:";
	  }
          if (exports.verbose) {
            console.log("submit: " + labbcat._storeEditUrl + "saveMedia");
          }
	  fd.submit(requestParameters, function(err, res) {
	    var responseText = "";
	    if (!err) {
	      res.on('data',function(buffer) {
		//console.log('data ' + buffer);
		responseText += buffer;
	      });
	      res.on('end',function(){
                if (exports.verbose) console.log("response: " + responseText);
	        var result = null;
	        var errors = null;
	        var messages = null;
		try {
		  var response = JSON.parse(responseText);
		  result = response.model.result || response.model;
		  errors = response.errors;
		  if (errors && errors.length == 0) errors = null
		  messages = response.messages;
		  if (messages && messages.length == 0) messages = null
		} catch(exception) {
		  result = null
                  errors = ["" +exception+ ": " + labbcat.responseText];
                  messages = [];
		}
		onResult(result, errors, messages, "saveMedia", id);
	      });
	    } else {
	      onResult(null, ["" +err+ ": " + labbcat.responseText], [], "saveMedia", id);
	    }
	    
	    if (res) res.resume();
	  });
	}); // got length
      } // runningOnNode
    }
    
    /**
     * Saves the given source file (transcript) for the given transcript.
     * @param {string} id The transcript ID
     * @param {string} url A URL to the transcript.
     * @param {resultCallback} onResult Invoked when the request has returned a result.
     */
    saveSource(id, url, onResult) { // TODO
    }

    /**
     * Saves the given document for the episode of the given transcript.
     * @param {string} id The transcript ID
     * @param {file|string} document The document to upload. In a browser, this must be
     * a file object, and in Node, it must be the full path to the file.
     * @param {resultCallback} onResult Invoked when the request has returned a result.
     * @param onProgress Invoked on XMLHttpRequest progress.
     */
    saveEpisodeDocument(id, document, onResult, onProgress) {
      if (exports.verbose) {
        console.log("saveEpisodeDocument(" + id + ", " + document + ")");
      }
      
      // create form
      var fd = new FormData();
      fd.append("id", id);
      
      if (!runningOnNode) {
	fd.append("document", document);
        
	// create HTTP request
	var xhr = new XMLHttpRequest();
	xhr.call = "saveEpisodeDocument";
	xhr.id = id;
	xhr.onResult = onResult;
	xhr.addEventListener("load", callComplete, false);
	xhr.addEventListener("error", callFailed, false);
	xhr.addEventListener("abort", callCancelled, false);
	xhr.upload.addEventListener("progress", onProgress, false);
	xhr.upload.id = id; // for knowing what status to update during events
	
	xhr.open("POST", this._storeEditUrl + "saveEpisodeDocument");
	if (this.username) {
	  xhr.setRequestHeader("Authorization", "Basic " + btoa(this.username + ":" + this.password))
	}
	xhr.setRequestHeader("Accept", "application/json");
	xhr.send(fd);
      } else { // runningOnNode
	
	// on node.js, files are actually paths
	var docName = document.replace(/.*\//g, "");
        if (exports.verbose) console.log("docName: " + docName);
        
	fd.append("document", 
		  fs.createReadStream(document).on('error', function(){
		    onResult(null, ["Invalid document: " + docName],[],"saveEpisodeDocument",id);
		  }), docName);
        var urlParts = parseUrl(this._storeEditUrl + "saveEpisodeDocument");
	// for tomcat 8, we need to explicitly send the content-type and content-length headers...
	var labbcat = this;
        var password = this._password;
	fd.getLength(function(something, contentLength) {
	  var requestParameters = {
	    port: urlParts.port,
	    path: urlParts.pathname,
	    host: urlParts.hostname,
	    headers: {
	      "Accept" : "application/json",
	      "content-length" : contentLength,
	      "Content-Type" : "multipart/form-data; boundary=" + fd.getBoundary()
	    }
	  };
	  if (labbcat.username && password) {
	    requestParameters.auth = labbcat.username+':'+password;
	  }
	  if (/^https.*/.test(labbcat.baseUrl)) {
	    requestParameters.protocol = "https:";
	  }
          if (exports.verbose) {
            console.log("submit: " + labbcat._storeEditUrl + "saveEpisodeDocument");
          }
	  fd.submit(requestParameters, function(err, res) {
	    var responseText = "";
	    if (!err) {
	      res.on('data',function(buffer) {
		//console.log('data ' + buffer);
		responseText += buffer;
	      });
	      res.on('end',function(){
                if (exports.verbose) console.log("response: " + responseText);
	        var result = null;
	        var errors = null;
	        var messages = null;
		try {
		  var response = JSON.parse(responseText);
		  result = response.model.result || response.model;
		  errors = response.errors;
		  if (errors && errors.length == 0) errors = null
		  messages = response.messages;
		  if (messages && messages.length == 0) messages = null
		} catch(exception) {
		  result = null
                  errors = ["" +exception+ ": " + labbcat.responseText];
                  messages = [];
		}
		onResult(result, errors, messages, "saveEpisodeDocument", id);
	      });
	    } else {
	      onResult(null, ["" +err+ ": " + labbcat.responseText],[],"saveEpisodeDocument",id);
	    }
	    
	    if (res) res.resume();
	  });
	}); // got length
      } // runningOnNode
    }
    
    /**
     * Delete a given media or episode document file.
     * @param {string} id The transcript ID
     * @param {string} fileName The media file name, e.g. mediaFile.name.
     * @param {resultCallback} onResult Invoked when the request has completed.
     */
    deleteMedia(id, fileName, onResult) {
      this.createRequest(
        "deleteMedia", null, onResult, null, "POST",
        this.storeEditUrl, "application/x-www-form-urlencoded;charset=\"utf-8\"")
        .send(this.parametersToQueryString({id : id, fileName : fileName}));
    }

    /**
     * Deletes the given transcript, and all associated media, from the graph store.
     * @param {string} id The transcript ID
     * @param {resultCallback} onResult Invoked when the request has completed.
     */
    deleteTranscript(id, onResult) {
      this.createRequest(
        "deleteTranscript", null, onResult, null, "POST",
        this.storeEditUrl, "application/x-www-form-urlencoded;charset=\"utf-8\"")
        .send(this.parametersToQueryString({id : id}));
    }

    /**
     * Saves a participant, and all its tags, to the graph store.
     * To change the ID of an existing participant, pass the old/current ID as the
     * <var>id</var>, and pass the new ID as the <var>label</var>.
     * If the participant ID does not already exist in the database, a new participant record
     * is created. 
     * @param {string} id The participant ID - either the unique internal database ID,
     * or their name. 
     * @param {string} label The new ID (name) for the participant
     * @param {object} attributes Participant attribute values - the names are the
     * participant attribute layer IDs, and the values are the corresponding new
     * attribute values. The pass phrase for participant access can also be set by
     * specifying a "_password" attribute.
     * @param {resultCallback} onResult Invoked when the request has completed.
     */
    saveParticipant(id, label, attributes, onResult) {
      attributes["id"] = id;
      attributes["label"] = label;
      this.createRequest(
        "saveParticipant", null, onResult, null, "POST",
        this.storeEditUrl, "application/x-www-form-urlencoded;charset=\"utf-8\"")
        .send(this.parametersToQueryString(attributes));
    }
    /**
     * Deletes the given participan, and all assciated meta-data, from the graph store.
     * @param {string} id The participant ID
     * @param {resultCallback} onResult Invoked when the request has completed.
     */
    deleteParticipant(id, onResult) {
      this.createRequest(
        "deleteParticipant", null, onResult, null, "POST",
        this.storeEditUrl, "application/x-www-form-urlencoded;charset=\"utf-8\"")
        .send(this.parametersToQueryString({id : id}));
    }

    /**
     * Upload a transcript file and associated media files, as the first stage in adding or
     * modifying a transcript to LaBB-CAT. The second stage is {@link #transcriptUploadParameters}
     * @param {file|string} transcript The transcript to upload. In a browser, this
     * must be a file object, and in Node, it must be the full path to the file. 
     * @param {string|object} media The media to upload, if any. This can be a single
     * file, or a map of  track suffixes to media files to upload for that track. In a
     * browser, attribute values must be arrays of file objects, and in Node, they must be
     * arrays of strings representing the full path to the file.
     * @param merge Whether the upload corresponds to updates to an existing transcript
     * (true) or a new transcript (false).
     * @param {resultCallback} onResult Invoked when the request has returned a
     * result, which is and object that has the following attributes:
     * <dl>
     *  <dt> id </dt> <dd> The unique identifier to use for this upload when subsequently
     *          calling {@link #transcriptUploadParameters}. </dd>
     *  <dt> parameters </dt> <dd> An array of objects representing the parameters that
     *          require values to be passed into {@link #transcriptUploadParameters}. 
     *          The <q>parameters</q> returned may include both information
     *          required by the format deserializer (e.g. mappings from tiers to LaBB-CAT
     *          layers) and also general information required by LaBB-CAT (e.g. the
     *          corpus, episode, and type of the transcript). 
     *                 </dd>
     * <p> Each parameter may contain the following attributes:
     *  <dl>
     *   <dt> name </dt>
     *       <dd> The name that should be used when specifying the value for the parameter
     *        when calling {@link #transcriptUploadParameters}. </dd> 
     *   <dt> label </dt>
     *       <dd> A label for the parameter intended for display to the user.</dd> 
     *   <dt> hint </dt>
     *       <dd> A description of the purpose of the parameter, for display to the user.</dd> 
     *   <dt> type </dt>
     *       <dd> The type of the parameter, e.g. <q>String</q>, <q>Double</q>, <q>Integer</q>,  
     *         <q>Boolean</q>.</dd> 
     *   <dt> required </dt>
     *       <dd> <tt>true</tt> if the value must be specified, <tt>false</tt> if it is optional.</dd> 
     *   <dt> value </dt>
     *       <dd> A default value for the parameter.</dd> 
     *   <dt> possibleValues </dt>
     *       <dd> A list of possible values, if the possibilities are limited to a finite set.</dd> 
     *  </dl>
     * <p> The required parameters may include both information
     * required by the format deserializer (e.g. mappings from tiers to LaBB-CAT layers) 
     * and also general information required by LaBB-CAT, such as:
     *  <dl>
     *   <dt> labbcat_corpus </dt>
     *       <dd> The corpus the new transcript(s) belong(s) to. </dd> 
     *   <dt> labbcat_episode </dt>
     *       <dd> The episode the new transcript(s) belong(s) to. </dd> 
     *   <dt> labbcat_transcript_type </dt>
     *       <dd> The transcript type for the new transcript(s). </dd> 
     *   <dt> labbcat_generate </dt>
     *       <dd> Whether to re-regenerate layers of automated annotations or not. </dd> 
     *  </dl> 
     * @param onProgress Invoked on XMLHttpRequest progress.
     */
    transcriptUpload(transcript, media, merge, onResult, onProgress) {
      if (typeof media === "boolean") {
        // (transcript, merge, onResult, onProgress)
        onProgress = onResult;
        onResult = merge;
        merge = media;
        media = null;
      }
      if (exports.verbose) {
        console.log("transcriptUpload(" + transcript + ", " + media + ", " + merge + ")");
      }
      // create form
      var fd = new FormData();
      fd.append("merge", ""+(merge?true:false));
      
      if (!runningOnNode) {	
        
	fd.append("transcript", transcript);
	if (media) {
          if (typeof media != "object") media = { "" : [media] }; // convert to track->array map
          for (var trackSuffix of Object.keys(media)) {
            var files = media[trackSuffix];
            if (files) {
	      if (files.constructor === Array) { // multiple files
	        for (var f in files) {
	          fd.append("media"+trackSuffix, files[f]);
	        } // next file
              } else { // a single file
	        fd.append("media"+trackSuffix, files);
	      }
            }
          } // next track suffix
	} // media to upload
        
	// create HTTP request
	var xhr = new XMLHttpRequest();
	xhr.call = "transcriptUpload";
	xhr.id = transcript.name;
	xhr.onResult = onResult;
	xhr.addEventListener("load", callComplete, false);
	xhr.addEventListener("error", callFailed, false);
	xhr.addEventListener("abort", callCancelled, false);
	xhr.upload.addEventListener("progress", onProgress, false);
	xhr.upload.id = transcript.name; // for knowing what status to update during events
	
	xhr.open("POST", this.baseUrl + "api/edit/transcript/upload");
	if (this.username) {
	  xhr.setRequestHeader("Authorization", "Basic " + btoa(this.username + ":" + this.password))
	}
	xhr.setRequestHeader("Accept", "application/json");
	xhr.send(fd);
      } else { // runningOnNode
	
	// on node.js, files are actually paths
	var transcriptName = transcript.replace(/.*\//g, "");
        if (exports.verbose) console.log("transcriptName: " + transcriptName);

	fd.append(
          "transcript", 
	  fs.createReadStream(transcript).on('error', function(){
	    onResult(
              null, ["Invalid transcript: " + transcriptName], [], "transcriptUpload", transcriptName);
	  }), transcriptName);
        
        if (media) {
          console.log("media " + JSON.stringify(media));
          if (typeof media == "string") media = { "" : [media] }; // convert to track->array map
          for (var trackSuffix in media) {
            var files = media[trackSuffix];
            if (files) {
	      if (files.constructor === Array) { // multiple files
	        for (var f in files) {
	          var mediaName = files[f].replace(/.*\//g, "");
	          try {
		    fd.append(
                      "media"+trackSuffix, 
		      fs.createReadStream(files[f]).on('error', function(x){
		        onResult(
                          null, ["Invalid media: " + mediaName], [], "transcriptUpload", transcriptName);
		      }), mediaName);
	          } catch(error) {
		    onResult(
                      null, ["Invalid media: " + mediaName, error.code], [], "transcriptUpload", 
                      transcriptName);
		    return;
	          }
	        } // next file
              } else { // a single file
	        var mediaName = files.replace(/.*\//g, "");
	        try {
	          fd.append(
                    "media"+trackSuffix, 
		    fs.createReadStream(files).on('error', function(){
		      onResult(null, ["Invalid media: " + mediaName], [], "transcriptUpload", transcriptName);
		    }), mediaName);
	        } catch(error) {
		  onResult(
                    null, ["Invalid media: " + mediaName, error.code], [], "transcriptUpload", 
                    transcriptName);
		  return;
	        }
	      } // single file
            } // there are files in this track
          } // next track suffix
        } // media is specified
	var urlParts = parseUrl(this.baseUrl + "api/edit/transcript/upload");
	// for tomcat 8, we need to explicitly send the content-type and content-length headers...
        if (exports.verbose) console.log("urlParts " + JSON.stringify(urlParts));
	var labbcat = this;
        var password = this._password;
	fd.getLength(function(something, contentLength) {
	  var requestParameters = {
	    port: urlParts.port,
	    path: urlParts.pathname,
	    host: urlParts.hostname,
	    headers: {
	      "Accept" : "application/json",
	      "content-length" : contentLength,
	      "Content-Type" : "multipart/form-data; boundary=" + fd.getBoundary()
	    }
	  };
	  if (labbcat.username && password) {
	    requestParameters.auth = labbcat.username+':'+password;
	  }
	  if (/^https.*/.test(labbcat.baseUrl)) {
	    requestParameters.protocol = "https:";
	  }
          if (exports.verbose) {
            console.log("submit: " + labbcat.baseUrl + "api/edit/transcript/upload");
          }
          if (exports.verbose) console.log("fd.submit " + JSON.stringify(requestParameters));
	  fd.submit(requestParameters, function(err, res) {
	    var responseText = "";
	    if (!err) {
	      res.on('data',function(buffer) {
		responseText += buffer;
	      });
	      res.on('end',function(){
	        var result = null;
	        var errors = null;
	        var messages = null;
		try {
		  var response = JSON.parse(responseText);
		  result = response.model.result || response.model;
		  errors = response.errors;
		  if (errors && errors.length == 0) errors = null
		  messages = response.messages;
		  if (messages && messages.length == 0) messages = null
		} catch(exception) {
		  result = null
                  errors = ["" +exception+ ": " + labbcat.responseText];
                  messages = [];
		}
		onResult(result, errors, messages, "transcriptUpload", transcriptName);
	      });
	    } else {
	      onResult(null, ["" +err+ ": " + labbcat.responseText], [], "transcriptUpload", transcriptName);
	    }
	    
	    if (res) res.resume();
	  });
	}); // got length
      } // runningOnNode
    } // transcriptUpload
    
    /**
     * The second part of a transcript upload process started by a call to
     * {@link #transcriptUpload}, which specifies values for the parameters
     * required to save the uploaded transcript to LaBB-CAT's database. 
     * <p> If the response includes more parameters, then this method should be called again
     * to supply their values.
     * @param {string} id Upload ID returned by the prior call to {@link #transcriptUpload}.
     * @param {object} parameters Object with an attribute and value for each parameter
     * returned by the prior call to {@link #transcriptUpload}.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * result, which is and object that has the following attributes:
     * <dl>
     *  <dt> transcripts </dt> <dd> an object for which each key is a transcript name, and its 
     *          value is the threadId of the server task processing the uploaded transcript, 
     *          which can be passed to {@link LabbcatView#taskStatus} to monitor progress. </dd>
     *  <dt> id </dt> <dd> The unique identifier to use for this upload when subsequently
     *          calling {@link #transcriptUploadParameters} (if parameters are returned). </dd>
     *  <dt> parameters </dt> <dd> An array of objects representing the parameters that
     *          still require values. </dd>
     * <p> If parameters are returned, they have the same structure as those returned by 
     * {@link #transcriptUpload}
     */
    transcriptUploadParameters(id, parameters, onResult) {
      if (parameters.constructor === Array) { // array of parameter objects, not keys/values
        var parameterValues = {}
        for (var p in parameters) {
          var parameter = parameters[p];
          if (parameter.name) {
            parameterValues[parameter.name] = parameter.value;
          }
        } // next parameter
        parameters = parameterValues;
      }
      this.createRequest(
        "transcriptUploadParameters", null, onResult,
        this.baseUrl+"api/edit/transcript/upload/"+encodeURIComponent(id)
          + "?"+this.parametersToQueryString(parameters),
        "PUT").send();
    } // transcriptUploadParameters

    /**
     * Cancel a transcript upload started by a call to {@link #transcriptUpload}, 
     * deleting any uploaded files from the server.
     * @param {string} id Upload ID returned by the prior call to {@link #transcriptUpload}.
     * @param {resultCallback} onResult Invoked when the request has returned.
     */
    transcriptUploadDelete(id, onResult) {
      this.createRequest(
        "transcriptUploadDelete", null, onResult,
        this.baseUrl+"api/edit/transcript/upload/"+encodeURIComponent(id),
        "DELETE").send();
    } // transcriptUploadDelete
    
    /**
     * Uploads a new transcript.
     * @param {file|string} transcript The transcript to upload. In a browser, this
     * must be a file object, and in Node, it must be the full path to the file. 
     * @param {file|file[]|string|string[]} media The media to upload, if any. In a
     * browser, these must be file objects, and in Node, they must be the full paths
     * to the files.
     * @param {string} [trackSuffix] The track suffix for the media.
     * @param {string} transcriptType The transcript type.
     * @param {string} corpus The corpus for the transcript.
     * @param {string} [episode] The episode the transcript belongs to.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * result, which is the task ID of the resulting annotation generation task. The
     * task status can be updated using {@link LabbcatView#taskStatus} 
     * @param onProgress Invoked on XMLHttpRequest progress.
     */
    newTranscript(transcript, media, trackSuffix, transcriptType, corpus, episode, onResult, onProgress) {
      if (typeof corpus === "function") {
        // (transcript, media, transcriptType, corpus, onResult, onProgress)
        onProgress = episode;
        onResult = corpus;
        episode = null;
        corpus = transcriptType;
        transcriptType = trackSuffix;
        trackSuffix = null;
      } else if (typeof episode === "function") {
        // (transcript, media, transcriptType, corpus, episode, onResult, onProgress)
        onProgress = onResult;
        onResult = episode;
        episode = corpus;
        corpus= transcriptType;
        transcriptType = trackSuffix;
        trackSuffix = null;
      }
      if (exports.verbose) {
        console.log("newTranscript(" + transcript + ", " + media + ", " + trackSuffix
                    + ", " + transcriptType + ", " + corpus + ", " + episode + ")");
      }

      // determine transcript name for onResult
      var transcriptName = transcript;
      if (!runningOnNode) {
        transcriptName = transcript.name;
      } else { // node
	transcriptName = transcript.replace(/.*\//g, "");
      }
      
      var legacyApi = () => { // for fallback if the new API isn't available:
        // create form
        var fd = new FormData();
        fd.append("todo", "new");
        fd.append("auto", "true");
        if (transcriptType) fd.append("transcript_type", transcriptType);
        if (corpus) fd.append("corpus", corpus);
        if (episode) fd.append("episode", episode);
        
        if (!runningOnNode) {	
          
	  fd.append("uploadfile1_0", transcript);
	  if (media) {
	    if (!trackSuffix) trackSuffix = "";
	    if (media.constructor === Array) { // multiple files
	      for (var f in media) {
	        fd.append("uploadmedia"+trackSuffix+"1", media[f]);
	      } // next file
            } else { // a single file
	      fd.append("uploadmedia"+trackSuffix+"1", media);
	    }
	  }
          
	  // create HTTP request
	  var xhr = new XMLHttpRequest();
	  xhr.call = "newTranscript";
	  xhr.id = transcript.name;
	  xhr.onResult = onResult;
	  xhr.addEventListener("load", callComplete, false);
	  xhr.addEventListener("error", callFailed, false);
	  xhr.addEventListener("abort", callCancelled, false);
	  xhr.upload.addEventListener("progress", onProgress, false);
	  xhr.upload.id = transcript.name; // for knowing what status to update during events
	  
	  xhr.open("POST", this.baseUrl + "edit/transcript/new");
	  if (this.username) {
	    xhr.setRequestHeader("Authorization", "Basic " + btoa(this.username + ":" + this.password))
	  }
	  xhr.setRequestHeader("Accept", "application/json");
	  xhr.send(fd);
        } else { // runningOnNode
	  
	  // on node.js, files are actually paths
          if (exports.verbose) console.log("transcriptName: " + transcriptName);

	  fd.append("uploadfile1_0", 
		    fs.createReadStream(transcript).on('error', function(){
		      onResult(null, ["Invalid transcript: " + transcriptName], [], "newTranscript", transcriptName);
		    }), transcriptName);
          
	  if (media) {
	    if (!trackSuffix) trackSuffix = "";
	    if (media.constructor === Array) { // multiple files
	      for (var f in media) {
	        var mediaName = media[f].replace(/.*\//g, "");
	        try {
		  fd.append("uploadmedia"+trackSuffix+(f+1), 
			    fs.createReadStream(media[f]).on('error', function(){
			      onResult(null, ["Invalid media: " + mediaName], [], "newTranscript", transcriptName);
			    }), mediaName);
	        } catch(error) {
		  onResult(null, ["Invalid media: " + mediaName], [], "newTranscript", transcriptName);
		  return;
	        }
	      } // next file
            } else { // a single file
	      var mediaName = media.replace(/.*\//g, "");
	      fd.append("uploadmedia"+trackSuffix+"1", 
		        fs.createReadStream(media).on('error', function(){
			  onResult(null, ["Invalid media: " + mediaName], [], "newTranscript", transcriptName);
		        }), mediaName);
	    }
	  }
	  
	  var urlParts = parseUrl(this.baseUrl + "edit/transcript/new");
	  // for tomcat 8, we need to explicitly send the content-type and content-length headers...
	  var labbcat = this;
          var password = this._password;
	  fd.getLength(function(something, contentLength) {
	    var requestParameters = {
	      port: urlParts.port,
	      path: urlParts.pathname,
	      host: urlParts.hostname,
	      headers: {
	        "Accept" : "application/json",
	        "content-length" : contentLength,
	        "Content-Type" : "multipart/form-data; boundary=" + fd.getBoundary()
	      }
	    };
	    if (labbcat.username && password) {
	      requestParameters.auth = labbcat.username+':'+password;
	    }
	    if (/^https.*/.test(labbcat.baseUrl)) {
	      requestParameters.protocol = "https:";
	    }
            if (exports.verbose) {
              console.log("submit: " + labbcat.baseUrl + "edit/transcript/new");
            }
	    fd.submit(requestParameters, function(err, res) {
	      var responseText = "";
	      if (!err) {
	        res.on('data',function(buffer) {
		  //console.log('data ' + buffer);
		  responseText += buffer;
	        });
	        res.on('end',function(){
                  if (exports.verbose) console.log("response: " + responseText);
	          var result = null;
	          var errors = null;
	          var messages = null;
		  try {
		    var response = JSON.parse(responseText);
		    result = response.model.result || response.model;
		    errors = response.errors;
		    if (errors && errors.length == 0) errors = null
		    messages = response.messages;
		    if (messages && messages.length == 0) messages = null
		  } catch(exception) {
		    result = null
                    errors = ["" +exception+ ": " + labbcat.responseText];
                    messages = [];
		  }
		  onResult(result, errors, messages, "newTranscript",
                           transcriptName);
	        });
	      } else {
	        onResult(null, ["" +err+ ": " + labbcat.responseText], [], "newTranscript", transcriptName);
	      }
	      
	      if (res) res.resume();
	    });
	  }); // got length
        } // runningOnNode
      }; // legacyApi

      // phase 1: upload files
      if (media) { // uploading media
        // convert to the track structure expected by transcriptUpload
        trackSuffix = trackSuffix || ""; // if not specified, trackSuffix is empty string
        media = { trackSuffix: media };
      }
      this.transcriptUpload(
        transcript, media, false, // merge=false: new transcript
        (result, errors, messages)=>{
          if (errors && errors.length > 0) {
            if (/.*404.*/.test(errors[0])) { // endpoint not found
              if (exports.verbose) {
                console.log("transcriptUpload: " + errors[0] + " - falling back to legacy API");
              }
              // fall back to lgacy API
              legacyApi();
            } else { // some other error 
	      onResult(result, errors, messages, "newTranscript", transcriptName);
            }
          } else { // no errors
            // set parameters to default values
            var parameters = {};
            for (var parameter of result.parameters) {
              parameters[parameter.name] = parameters[parameter.value];
            }
            // set the parameters we know the values of
            if (transcriptType) parameters["labbcat_transcript_type"] = transcriptType;
            if (corpus) parameters["labbcat_corpus"] = corpus;
            if (episode) parameters["labbcat_episode"] = episode;
            
            // phase 2: upload parameters
            this.transcriptUploadParameters(
              result.id, parameters, // merge=false : new transcript
              (result, errors, messages)=>{
                if (result && result.transcripts) {
                  // result is expected to be the transcriptId->threadId map
                  result = result.transcripts;
                }
	        onResult(result, errors, messages, "newTranscript", transcriptName);
              }); // transcriptUploadParameters
          } // no errors
      }, onProgress); // transcriptUpload
    }
    
    /**
     * Uploads a new version of an existing transcript.
     * <em>NB</em> this not be confused with the method that saves an annotation graph
     * object: {@link #saveTranscript}
     * @param {file|string} transcript The transcript to upload. In a browser, this
     * must be a file object, and in Node, it must be the full path to the file. 
     * @param {boolean} suppressGeneration (optional) false (the default) to run
     * automatic layer generation, true to suppress automatic layer generation.
     * @param {resultCallback} onResult Invoked when the request has returned a result, 
     * which is the task ID of the resulting annotation generation task. The 
     * task status can be updated using {@link LabbcatView#taskStatus}
     * @param onProgress Invoked on XMLHttpRequest progress.
     */
    updateTranscript(transcript, suppressGeneration, onResult, onProgress) {
      if (typeof suppressGeneration === "function") {
        onProgress = onResult;
        onResult = suppressGeneration;
        suppressGeneration = false
      }
      if (exports.verbose) {
        console.log("updateTranscript(" + transcript + ","+suppressGeneration+")");
      }
      
      // determine transcript name for onResult
      var transcriptName = transcript;
      if (!runningOnNode) {
        transcriptName = transcript.name;
      } else { // node
	transcriptName = transcript.replace(/.*\//g, "");
      }
      
      var legacyApi = () => { // for fallback if the new API isn't available:
        // create form
        var fd = new FormData();
        fd.append("todo", "update");
        fd.append("auto", "true");
        if (suppressGeneration) fd.append("suppressGeneration", "true");
        
        if (!runningOnNode) {	
          
	  fd.append("uploadfile1_0", transcript);
          
	  // create HTTP request
	  var xhr = new XMLHttpRequest();
	  xhr.call = "updateTranscript";
	  xhr.id = transcript.name;
	  xhr.onResult = onResult;
	  xhr.addEventListener("load", callComplete, false);
	  xhr.addEventListener("error", callFailed, false);
	  xhr.addEventListener("abort", callCancelled, false);
	  xhr.upload.addEventListener("progress", onProgress, false);
	  xhr.upload.id = transcript.name; // for knowing what status to update during events
	  
	  xhr.open("POST", this.baseUrl + "edit/transcript/new");
	  if (this.username) {
	    xhr.setRequestHeader("Authorization", "Basic " + btoa(this.username + ":" + this._password))
	  }
	  xhr.setRequestHeader("Accept", "application/json");
	  xhr.send(fd);
        } else { // runningOnNode
	  
	  // on node.js, files are actually paths
	  fd.append("uploadfile1_0", 
		    fs.createReadStream(transcript).on('error', function(){
		      onResult(null, ["Invalid transcript: " + transcriptName], [], "updateTranscript", transcriptName);
		    }), transcriptName);
	  
	  var urlParts = parseUrl(this.baseUrl + "edit/transcript/new");
	  var requestParameters = {
	    port: urlParts.port,
	    path: urlParts.pathname,
	    host: urlParts.hostname,
	    headers: { "Accept" : "application/json" }
	  };
	  if (this.username && this._password) {
	    requestParameters.auth = this.username+':'+this._password;
	  }
	  if (/^https.*/.test(this.baseUrl)) {
	    requestParameters.protocol = "https:";
	  }
	  fd.submit(requestParameters, function(err, res) {
	    var responseText = "";
	    if (!err) {
	      res.on('data',function(buffer) {
	        //console.log('data ' + buffer);
	        responseText += buffer;
	      });
	      res.on('end',function(){
                if (exports.verbose) console.log("response: " + responseText);
	        var result = null;
	        var errors = null;
	        var messages = null;
	        try {
		  var response = JSON.parse(responseText);
		  result = response.model.result || response.model;
		  errors = response.errors;
		  if (errors && errors.length == 0) errors = null
		  messages = response.messages;
		  if (messages && messages.length == 0) messages = null
                  ;
	        } catch(exception) {
		  result = null
                  errors = ["" +exception+ ": " + labbcat.responseText];
                  messages = [];
	        }
                // for this call, the result is an object with one key, whose
                // value is the threadId - so just return that
	        onResult(result, errors, messages, "updateTranscript", transcriptName);
              });
	    } else {
	      onResult(null, ["" +err+ ": " + this.responseText], [], "updateTranscript", transcriptName);
	    }
            
	    if (res) res.resume();
	  });
        }
      }; // legacyApi

      // phase 1: upload files
      this.transcriptUpload(
        transcript, null, true, // merge=true: existing transcript
        (result, errors, messages)=>{
          if (errors && errors.length > 0) {
            if (/.*404.*/.test(errors[0])) { // endpoint not found
              if (exports.verbose) {
                console.log("transcriptUpload: " + errors[0] + " - falling back to legacy API");
              }
              // fall back to lgacy API
              legacyApi();
            } else { // some other error 
	      onResult(result, errors, messages, "updateTranscript", transcriptName);
            }
          } else { // no errors
            // set parameters to default values
            var parameters = {};
            for (var parameter of result.parameters) {
              parameters[parameter.name] = parameters[parameter.value];
            }
            // set the parameters we know the values of
            if (suppressGeneration) {
              parameters["labbcat_generate"] = false;
            } else {
              parameters["labbcat_generate"] = true;
            }
            
            // phase 2: upload parameters
            this.transcriptUploadParameters(
              result.id, parameters, // merge=false : new transcript
              (result, errors, messages)=>{
                if (result && result.transcripts) {
                  // result is expected to be the transcriptId->threadId map
                  result = result.transcripts;
                }
	        onResult(result, errors, messages, "updateTranscript", transcriptName);
              }); // transcriptUploadParameters
          } // no errors
      }, onProgress); // transcriptUpload      
    }

    /**
     * For HTK dictionary-filling, this adds a new dictionary entry and updates all tokens.
     * @param {string} layerId The dictionary of this layer will be used.
     * @param {string} label The word label to add an entry for.
     * @param {string} entry The definition (pronunciation) of the word to add.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var>.
     */
    dictionaryAdd(layerId, label, entry, onResult) {
      this.createRequest(
        "add", {
          layerId : layerId,
          label : label,
          entry : entry
        }, onResult, this.baseUrl+"api/edit/dictionary/add").send();
    }

    /**
     * Creates an annotation starting at <var>fromId</var> and ending at <var>toId</var>.
     * @param {string} id The ID of the transcript.
     * @param {string} fromId The start anchor's ID, which can be null if the layer is a tag layer.
     * @param {string} toId The end anchor's ID, which can be null if the layer is a tag layer.
     * @param {string} layerId The layer ID of the resulting annotation.
     * @param {string} label The label of the resulting annotation.
     * @param {number} confidence The confidence rating.
     * @param {string} parentId The new annotation's parent's ID.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> which is the new annotation's ID.
     */
    createAnnotation(id, fromId, toId, layerId, label, confidence, parentId, onResult) {
      this.createRequest(
        "createAnnotation", null, onResult, null, "POST",
        this.storeEditUrl, "application/x-www-form-urlencoded;charset=\"utf-8\"")
        .send(this.parametersToQueryString({
          id : id,
          fromId : fromId,
          toId : toId,
          layerId : layerId,
          label : label,
          confidence : confidence,
          parentId : parentId
        }));
    }
    
    /**
     * Updates the label of the given annotation.
     * @param {string} id The ID of the transcript.
     * @param {string} annotationId The annotation's ID.
     * @param {string} label The new label.
     * @param {number} confidence The confidence rating.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * null <var>result</var> on success.
     */
    updateAnnotationLabel(id, annotationId, label, confidence, onResult) {
      this.createRequest(
        "updateAnnotationLabel", null, onResult, null, "POST",
        this.storeEditUrl, "application/x-www-form-urlencoded;charset=\"utf-8\"")
        .send(this.parametersToQueryString({
          id : id,
          annotationId : annotationId,
          label : label,
          confidence : confidence
        }));
    }

    /**
     * Destroys the annotation with the given ID.
     * @param {string} id The ID of the transcript.
     * @param {string} annotationId The annotation's ID.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * null <var>result</var> on success.
     */
    destroyAnnotation(id, annotationId, onResult) {
      this.createRequest(
        "destroyAnnotation", null, onResult, null, "POST",
        this.storeEditUrl, "application/x-www-form-urlencoded;charset=\"utf-8\"")
        .send(this.parametersToQueryString({
          id : id,
          annotationId : annotationId
        }));
    }
    
    /**
     * Gets the transcript of a given utterance, for possible correction using
     * {@link #utteranceCorrection}.
     * @param {string} transcriptId The ID of the transcript.
     * @param {string} utteranceId The utterance's ID.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> with a "text" attribute, which is the plain-text
     * representation of the utterance, including word annotations and possibly
     * noise, comment, pronunciation etc. annotations using the configured transcript
     * conventions.
     */
    utteranceForCorrection(transcriptId, utteranceId, onResult) {
      this.createRequest(
        "readUtteranceTranscript", {
          transcriptId : transcriptId,
          utteranceId : utteranceId
        }, onResult, this.baseUrl+"api/edit/utterance/correction").send();
    }
    
    /**
     * Corrects the transcript of a given utterance.
     * @param {string} transcriptId The ID of the transcript.
     * @param {string} utteranceId The utterance's ID.
     * @param {string} text The corrected version of the transcript for the utterance.
     * This may include noise, comment, pronunciation etc. annotations using the
     * configured transcript conventions.
     * @param {boolean} suppressGeneration (optional) false (the default) to run
     * automatic layer generation, true to suppress automatic layer generation.
     * @param {resultCallback} onResult Invoked when the request has returned a
     * <var>result</var> with a "threadId" attribute, which is the ID of the task
     * regenerating layers based on the new transcript.
     */
    utteranceCorrection(transcriptId, utteranceId, text, suppressGeneration, onResult) {
      if (typeof suppressGeneration === "function") {
        // utteranceCorrection(transcriptId, utteranceId, text, onResult)
        onResult = suppressGeneration;
        suppressGeneration = false
      }
      this.createRequest(
        "utterance/correction", null, onResult, null, "POST", // TODO should be PUT
        this.baseUrl+"api/edit/", "application/x-www-form-urlencoded;charset=\"utf-8\"")
        .send(this.parametersToQueryString({
          transcriptId : transcriptId,
          utteranceId : utteranceId,
          text : text,
          suppressGeneration : suppressGeneration
        }));
    }
  } // class LabbcatEdit
  
  // LabbcatAdmin class - read/write "admin" access

  /**
   * Read/write/administration interaction with LaBB-CAT corpora.
   * <p>This class inherits the <em>read/write</em> operations of LabbcatEdit
   * and adds some administration functions.
   * @example
   * // create annotation store client
   * const store = new labbcat.LabbcatAdmin("http://localhost:8080/labbcat", "labbcat", "labbcat");
   * // add a corpus
   * store.createCorpus("new-corpus", "en", "New English Corpus", (corpus, errors, messages, call)=>{ 
   *     console.log("new corpus ID is: " + corpus.corpus_id); 
   *     store.updateCorpus("new-corpus", "de", "New German Corpus", (corpus, errors, messages, call)=>{ 
   *         console.log("corpus updated, language is now: " + corpus.corpus_language); 
   *         store.deleteCorpus("new-corpus", (result, errors, messages, call)=>{ 
   *             console.log("corpus deleted"); 
   *         });
   *       });
   *   });
   * store.readCorpora((corpora, errors, messages, call)=>{ 
   *     for (let corpus of corpora) {
   *       console.log("corpus: " + corpus.corpus_name); 
   *     } // next corpus
   *   });
   * @extends LabbcatView
   * @author Robert Fromont robert@fromont.net.nz
   */
  class LabbcatAdmin extends LabbcatEdit {
    
    /**
     * The graph store URL - e.g. https://labbcat.canterbury.ac.nz/demo/api/edit/store/
     */
    get storeAdminUrl() {
      return this._storeAdminUrl;
    }
    /** 
     * Create a store client 
     * @param {string} baseUrl The LaBB-CAT base URL (i.e. the address of the 'home' link)
     * @param {string} username The LaBB-CAT user name.
     * @param {string} password The LaBB-CAT password.
     */
    constructor(baseUrl, username, password) {
      super(baseUrl, username, password);
      this._storeAdminUrl = this.baseUrl + "api/admin/store/";
    }
    
    /**
     * Adds a new layer.
     * @param {string|object} layer The layer ID, if all the other attribute
     * parameters are specified, or an object with all the layer attributes, in which case
     * only <var>onResult</var> need be specified.
     * @param {string} parentId The layer's parent layer id.
     * @param {string} description The description of the layer.
     * @param {number} alignment The layer's alignment 
     * - 0 for none, 1 for point alignment, 2 for interval alignment.
     * @param {boolean} peers Whether children on this layer have peers or not.
     * @param {boolean} peersOverlap Whether child peers on this layer can overlap or not.
     * @param {boolean} parentIncludes Whether the parent temporally includes the child.
     * @param {boolean} saturated Whether children must temporally fill the entire parent
     * duration (true) or not (false).
     * @param {string} type The type for labels on this layer, e.g. string, number,
     * boolean, ipa. 
     * @param {object} validLabels List of valid label values for this layer, or null 
     * if the layer values are not restricted. The 'key' is the possible label value, and 
     * each key is associated with a description of the value (e.g. for displaying to users). 
     * @param {string} category Category for the layer, if any.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: The resulting layer definition.
     */
    newLayer(layer, parentId, description, alignment,
             peers, peersOverlap, parentIncludes, saturated, type, validLabels, category,
             onResult) {
      var layerDefinition = {
        id: layer, parentId: parentId, description: description,
        alignment: Number(alignment), // ensure a number is passed, not a string
        peers: Boolean(peers), peersOverlap: Boolean(peersOverlap),
        parentIncludes: Boolean(parentIncludes), saturated: Boolean(saturated),
        type: type, validLabels: validLabels, category: category };
      if (typeof parentId === "function") { // (layerObject, onResult)
        onResult = parentId;
        layerDefinition = layer;
        // ensure important types are converted from strings
        layerDefinition.alignment = Number(layerDefinition.alignment);
        layerDefinition.peers = Boolean(layerDefinition.peers);
        layerDefinition.peers = Boolean(layerDefinition.peers);
        layerDefinition.peersOverlap = Boolean(layerDefinition.peersOverlap);
        layerDefinition.parentIncludes = Boolean(layerDefinition.parentIncludes);
        layerDefinition.saturated = Boolean(layerDefinition.saturated);
      }
      this.createRequest(
        "newLayer", null, onResult, this.storeAdminUrl + "newLayer", "POST",
        null, "application/json")
        .send(JSON.stringify(layerDefinition));
    }

    /**
     * Saves changes to a layer.
     * @param {string|object} layer The layer ID, if all the other attribute
     * parameters are specified, or an object with all the layer attributes, in which case
     * only <var>onResult</var> need be specified.
     * @param {string} parentId The layer's parent layer id.
     * @param {string} description The description of the layer.
     * @param {number} alignment The layer's alignment 
     * - 0 for none, 1 for point alignment, 2 for interval alignment.
     * @param {boolean} peers Whether children on this layer have peers or not.
     * @param {boolean} peersOverlap Whether child peers on this layer can overlap or not.
     * @param {boolean} parentIncludes Whether the parent temporally includes the child.
     * @param {boolean} saturated Whether children must temporally fill the entire parent
     * duration (true) or not (false).
     * @param {string} type The type for labels on this layer, e.g. string, number,
     * boolean, ipa. 
     * @param {object} validLabels List of valid label values for this layer, or null 
     * if the layer values are not restricted. The 'key' is the possible label value, and 
     * each key is associated with a description of the value (e.g. for displaying to users). 
     * @param {string} category Category for the layer, if any.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: The resulting layer definition.
     */
    saveLayer(layer, parentId, description, alignment,
              peers, peersOverlap, parentIncludes, saturated, type, validLabels, category,
              onResult) {
      var layerDefinition = {
        id: layer, parentId: parentId, description: description,
        alignment: Number(alignment), // ensure a number is passed, not a string
        peers: Boolean(peers), peersOverlap: Boolean(peersOverlap),
        parentIncludes: Boolean(parentIncludes), saturated: Boolean(saturated),
        type: type, validLabels: validLabels, category: category };
      if (typeof parentId === "function") { // (layerObject, onResult)
        onResult = parentId;
        layerDefinition = layer;
        // ensure important types are converted from strings
        layerDefinition.alignment = Number(layerDefinition.alignment);
        layerDefinition.peers = Boolean(layerDefinition.peers);
        layerDefinition.peers = Boolean(layerDefinition.peers);
        layerDefinition.peersOverlap = Boolean(layerDefinition.peersOverlap);
        layerDefinition.parentIncludes = Boolean(layerDefinition.parentIncludes);
        layerDefinition.saturated = Boolean(layerDefinition.saturated);
      }
      this.createRequest(
        "saveLayer", null, onResult, this.storeAdminUrl + "saveLayer", "POST",
        null, "application/json")
        .send(JSON.stringify(layerDefinition));
    }

    /**
     * Deletes the given layer, and all associated annotations.
     * @param {string|object} id The ID layer to delete.
     * @param {resultCallback} onResult Invoked when the request has completed.
     */
    deleteLayer(id, onResult) {
      this.createRequest(
        "deleteLayer", null, onResult, this.storeAdminUrl + "deleteLayer", "POST",
        null, "application/x-www-form-urlencoded;charset=\"utf-8\"")
        .send(this.parametersToQueryString({
          id : id
        }));
    }

    /**
     * Creates a new corpus record.
     * @see LabbcatAdmin#readCorpora
     * @see LabbcatAdmin#updateCorpus
     * @see LabbcatAdmin#deleteCorpus
     * @param {string} corpus_name The name/ID of the corpus.
     * @param {string} corpus_language The ISO 639-1 code for the default language.
     * @param {string} corpus_description The description of the corpus.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: A copy of the corpus record, 
     * including <em> corpus_id </em> - The database key for the record. 
     */
    createCorpus(corpus_name, corpus_language, corpus_description, onResult) {
      this.createRequest(
        "corpora", null, onResult, this.baseUrl+"api/admin/corpora", "POST",
        null, "application/json")
        .send(JSON.stringify({
          corpus_name : corpus_name,
          corpus_language : corpus_language,
          corpus_description : corpus_description}));
    }
    
    /**
     * Reads a list of corpus records.
     * @see LabbcatAdmin#createCorpus
     * @see LabbcatAdmin#updateCorpus
     * @see LabbcatAdmin#deleteCorpus
     * @param {int} [pageNumber] The zero-based  page of records to return (if null, all
     * records will be returned). 
     * @param {int} [pageLength] The length of pages (if null, the default page length is 20).
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: A list of corpus records with the following
     * attributes:
     * <dl>
     *  <dt> corpus_id </dt> <dd> The database key for the record. </dd>
     *  <dt> corpus_name </dt> <dd> The name/id of the corpus. </dd>
     *  <dt> corpus_language </dt> <dd> The ISO 639-1 code for the default language. </dd>
     *  <dt> corpus_description </dt> <dd> The description of the corpus. </dd>
     *  <dt> _cantDelete </dt> <dd> This is not a database field, but rather is present in
     *    records returned from the server that can not currently be deleted; 
     *    a string representing the reason the record can't be deleted. </dd>
     * </dl>
     */
    readCorpora(pageNumber, pageLength, onResult) {
      if (typeof pageNumber === "function") { // (onResult)
        onResult = pageNumber;
        pageNumber = null;
        pageLength = null;
      } else if (typeof l === "function") { // (p, onResult)
        onResult = l;
        pageLength = null;
      }
      this.createRequest(
        "corpora", {
          pageNumber:pageNumber,
          pageLength:pageLength
        }, onResult, this.baseUrl+"api/admin/corpora")
        .send();
    }
    
    /**
     * Updates an existing corpus record.
     * @see LabbcatAdmin#createCorpus
     * @see LabbcatAdmin#readCorpora
     * @see LabbcatAdmin#deleteCorpus
     * @param {string} corpus_id The database key for the record. // TODO eliminate corpus_id
     * @param {string} corpus_name The name/ID of the corpus.
     * @param {string} corpus_language The ISO 639-1 code for the default language.
     * @param {string} corpus_description The description of the corpus.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: A copy of the corpus record. 
     */
    updateCorpus(corpus_name, corpus_language, corpus_description, onResult) {
      this.createRequest(
        "corpora", null, onResult, this.baseUrl+"api/admin/corpora", "PUT")
        .send(JSON.stringify({
          corpus_name : corpus_name,
          corpus_language : corpus_language,
          corpus_description : corpus_description}));
    }
    
    /**
     * Deletes an existing corpus record.
     * @see LabbcatAdmin#createCorpus
     * @see LabbcatAdmin#readCorpora
     * @see LabbcatAdmin#updateCorpus
     * @param {string} corpus_name The name/ID of the corpus.
     * @param {resultCallback} onResult Invoked when the request has completed.
     */
    deleteCorpus(corpus_name, onResult) {
      this.createRequest(
        "corpora", null, onResult, `${this.baseUrl}api/admin/corpora/${corpus_name}`,
        "DELETE").send();
    }
    
    /**
     * Creates a new project record.
     * @deprecated since version 1.6.0: 'projects' are now categories with classId = 'layer'
     * - use createCategory instead. 
     * @see LabbcatAdmin#createCategory
     * @param {string} project The name/ID of the project.
     * @param {string} description The description of the project.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: A copy of the project record, 
     * including <em> project_id </em> - The database key for the record. 
     */
    createProject(project, description, onResult) {
      this.createCategory("layer", project, description, 0, onResult);
    }
    
    /**
     * Reads a list of project records.
     * @deprecated since version 1.6.0: 'projects' are now categories with classId = 'layer'
     * - use readCategories('layer') instead. 
     * @see LabbcatAdmin#readCategories
     * @param {int} [pageNumber] The zero-based  page of records to return (if null, all
     * records will be returned). 
     * @param {int} [pageLength] The length of pages (if null, the default page length is 20).
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: A list of project records with the following
     * attributes:
     * <dl>
     *  <dt> project_id </dt> <dd> The database key for the record. </dd>
     *  <dt> project </dt> <dd> The name/id of the project. </dd>
     *  <dt> description </dt> <dd> The description of the project. </dd>
     *  <dt> _cantDelete </dt> <dd> This is not a database field, but rather is present in
     *    records returned from the server that can not currently be deleted; 
     *    a string representing the reason the record can't be deleted. </dd>
     * </dl>
     */
    readProjects(pageNumber, pageLength, onResult) {
      this.readCategories("layer", pageNumber, pageLength, onResult);
    }
    
    /**
     * Updates an existing project record.
     * @deprecated since version 1.6.0: 'projects' are now categories with classId = 'layer'
     * - use updateCategory instead. 
     * @see LabbcatAdmin#updateCategory
     * @param {string} project_id The database key for the record. // TODO eliminate project_id
     * @param {string} project The name/ID of the project.
     * @param {string} description The description of the project.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: A copy of the project record. 
     */
    updateProject(project, description, onResult) {
      this.updateCategory("layer", project, description, 0, onResult);
    }
    
    /**
     * Deletes an existing project record.
     * @deprecated Deprecated as 'projects' are now categories with classId = 'layer' 
     * - use deleteCategory instead.
     * @see LabbcatAdmin#deleteCategory
     * @param {string} project The name/ID of the project.
     * @param {resultCallback} onResult Invoked when the request has completed.
     */
    deleteProject(project, onResult) {
      this.deleteCategory("layer", project, onResult);
    }
    
    /**
     * Reads a list of category records. This overrides the LabbcatView version, and includes
     * information about the possibility of deletion.
     * @see LabbcatAdmin#createCategory
     * @see LabbcatAdmin#updateCategory
     * @see LabbcatAdmin#deleteCategory
     * @param {string} class_id What attributes to read; "transcript" or "participant". 
     * @param {int} [pageNumber] The zero-based  page of records to return (if null, all
     * records will be returned). 
     * @param {int} [pageLength] The length of pages (if null, the default page length is 20).
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: A list of category records with the following
     * attributes:
     * <dl>
     *  <dt> class_id </dt> <dd> The class_id of the category. </dd>
     *  <dt> category </dt> <dd> The name/id of the category. </dd>
     *  <dt> description </dt> <dd> The description of the category. </dd>
     *  <dt> display_order </dt> <dd> Where the category appears among other categories. </dd>
     *  <dt> _cantDelete </dt> <dd> This is not a database field, but rather is present in
     *    records returned from the server that can not currently be deleted; 
     *    a string representing the reason the record can't be deleted. </dd>
     * </dl>
     */
    readCategories(class_id, pageNumber, pageLength, onResult) {
      if (typeof pageNumber === "function") { // (onResult)
        onResult = pageNumber;
        pageNumber = null;
        pageLength = null;
      } else if (typeof l === "function") { // (p, onResult)
        onResult = l;
        pageLength = null;
      }
      if (class_id == "participant") class_id = "speaker";
      this.createRequest(
        `categories/${class_id}`, {
          pageNumber:pageNumber,
          pageLength:pageLength
        }, onResult, `${this.baseUrl}api/admin/categories/${class_id}`)
        .send();
    }
    
    /**
     * Creates a new category record.
     * @see LabbcatView#readCategories
     * @see LabbcatAdmin#updateCategory
     * @see LabbcatAdmin#deleteCategory
     * @param {string} class_id What attributes the category applies to; "transcript" or
     * "participant". 
     * @param {string} category The name/ID of the category.
     * @param {string} description The description of the category.
     * @param {number} display_order Where the category appears among other categories.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: A copy of the category record, 
     * including <em> category_id </em> - The database key for the record. 
     */
    createCategory(class_id, category, description, display_order, onResult) {
      if (class_id == "participant") class_id = "speaker";
      this.createRequest(
        "categories", null, onResult, this.baseUrl+"api/admin/categories", "POST",
        null, "application/json")
        .send(JSON.stringify({
          class_id : class_id,
          category : category,
          description : description,
          display_order : display_order}));
    }
    
    /**
     * Updates an existing category record.
     * @see LabbcatAdmin#createCategory
     * @see LabbcatView#readCategories
     * @see LabbcatAdmin#deleteCategory
     * @param {string} class_id What attributes the category applies to; "transcript" or
     * "participant". 
     * @param {string} category The name/ID of the category.
     * @param {string} description The description of the category.
     * @param {number} display_order Where the category appears among other categories.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: A copy of the category record. 
     */
    updateCategory(class_id, category, description, display_order, onResult) {
      if (class_id == "participant") class_id = "speaker";
      this.createRequest(
        "categories", null, onResult, this.baseUrl+"api/admin/categories", "PUT")
        .send(JSON.stringify({
          class_id : class_id,
          category : category,
          description : description,
          display_order : display_order}));
    }
    
    /**
     * Deletes an existing category record.
     * @see LabbcatAdmin#createCategory
     * @see LabbcatView#readCategories
     * @see LabbcatAdmin#updateCategory
     * @param {string} class_id What attributes the category applies to; "transcript" or
     * "participant". 
     * @param {string} category The name/ID of the category.
     * @param {resultCallback} onResult Invoked when the request has completed.
     */
    deleteCategory(class_id, category, onResult) {
      if (class_id == "participant") class_id = "speaker";
      this.createRequest(
        "categories", null, onResult,
        `${this.baseUrl}api/admin/categories/${class_id}/${category}`,
        "DELETE").send();
    }
    
    /**
     * Creates a new media track record.
     * @see LabbcatAdmin#readMediaTracks
     * @see LabbcatAdmin#updateTask
     * @see LabbcatAdmin#deleteTask
     * @param {string} suffix The suffix of the mediaTrack.
     * @param {string} description The description of the mediaTrack.
     * @param {int} display_order The position of the mediaTrack relative to other mediaTracks.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: A copy of the mediaTrack record. 
     */
    createMediaTrack(suffix, description, display_order, onResult) {
      this.createRequest(
        "mediaTracks", null, onResult, this.baseUrl+"api/admin/mediatracks", "POST",
        null, "application/json")
        .send(JSON.stringify({
          suffix : suffix,
          description : description,
          display_order: display_order}));
    }
    
    /**
     * Reads a list of media track records.
     * @see LabbcatAdmin#createMediaTrack
     * @see LabbcatAdmin#updateTask
     * @see LabbcatAdmin#deleteTask
     * @param {int} [pageNumber] The zero-based  page of records to return (if null, all
     * records will be returned). 
     * @param {int} [pageLength] The length of pages (if null, the default page length is 20).
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: A list of mediaTrack records with the following
     * attributes:
     * <dl>
     *  <dt> suffix </dt> <dd> The suffix of the mediaTrack. </dd>
     *  <dt> description </dt> <dd> The description of the mediaTrack. </dd>
     *  <dt> display_order </dt> <dd> The position of the mediaTrack relative to other mediaTracks. </dd>
     *  <dt> _cantDelete </dt> <dd> This is not a database field, but rather is present in
     *    records returned from the server that can not currently be deleted; 
     *    a string representing the reason the record can't be deleted. </dd>
     * </dl>
     */
    readMediaTracks(pageNumber, pageLength, onResult) {
      if (typeof pageNumber === "function") { // (onResult)
        onResult = pageNumber;
        pageNumber = null;
        pageLength = null;
      } else if (typeof l === "function") { // (p, onResult)
        onResult = l;
        pageLength = null;
      }
      this.createRequest(
        "mediaTracks", {
          pageNumber:pageNumber,
          pageLength:pageLength
        }, onResult, this.baseUrl+"api/admin/mediatracks")
        .send();
    }
    
    /**
     * Updates an existing media track record.
     * @see LabbcatAdmin#createMediaTrack
     * @see LabbcatAdmin#readMediaTracks
     * @see LabbcatAdmin#deleteMediaTrack
     * @param {string} suffix The suffix of the mediaTrack.
     * @param {string} description The description of the mediaTrack.
     * @param {int} display_order The position of the mediaTrack relative to other mediaTracks.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: A copy of the mediaTrack record. 
     */
    updateMediaTrack(suffix, description, display_order, onResult) {
      this.createRequest(
        "mediaTracks", null, onResult, this.baseUrl+"api/admin/mediatracks", "PUT")
        .send(JSON.stringify({
          suffix : suffix,
          description : description,
          display_order: display_order}));
    }
    
    /**
     * Deletes an existing media track record.
     * @see LabbcatAdmin#createMediaTrack
     * @see LabbcatAdmin#readMediaTracks
     * @see LabbcatAdmin#updateMediaTrack
     * @param {string} suffix The suffix of the mediaTrack.
     * @param {resultCallback} onResult Invoked when the request has completed.
     */
    deleteMediaTrack(suffix, onResult) {
      this.createRequest(
        "mediaTracks", null, onResult, `${this.baseUrl}api/admin/mediatracks/${suffix}`,
        "DELETE").send();
    }
    
    /**
     * Creates a new role record.
     * @see LabbcatAdmin#readRoles
     * @see LabbcatAdmin#updateRole
     * @see LabbcatAdmin#deleteRole
     * @param {string} role_id The name/ID of the role.
     * @param {string} description The description of the role.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: A copy of the role record, 
     * including <em> role_id </em> - The database key for the record. 
     */
    createRole(role_id, description, onResult) {
      this.createRequest(
        "roles", null, onResult, this.baseUrl+"api/admin/roles", "POST",
        null, "application/json")
        .send(JSON.stringify({
          role_id : role_id,
          description : description}));
    }
    
    /**
     * Reads a list of role records.
     * @see LabbcatAdmin#createRole
     * @see LabbcatAdmin#updateRole
     * @see LabbcatAdmin#deleteRole
     * @param {int} [pageNumber] The zero-based  page of records to return (if null, all
     * records will be returned). 
     * @param {int} [pageLength] The length of pages (if null, the default page length is 20).
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: A list of role records with the following
     * attributes:
     * <dl>
     *  <dt> role_id </dt> <dd> The name/id of the role. </dd>
     *  <dt> description </dt> <dd> The description of the role. </dd>
     *  <dt> _cantDelete </dt> <dd> This is not a database field, but rather is present in
     *    records returned from the server that can not currently be deleted; 
     *    a string representing the reason the record can't be deleted. </dd>
     * </dl>
     */
    readRoles(pageNumber, pageLength, onResult) {
      if (typeof pageNumber === "function") { // (onResult)
        onResult = pageNumber;
        pageNumber = null;
        pageLength = null;
      } else if (typeof l === "function") { // (p, onResult)
        onResult = l;
        pageLength = null;
      }
      this.createRequest(
        "roles", {
          pageNumber:pageNumber,
          pageLength:pageLength
        }, onResult, this.baseUrl+"api/admin/roles")
        .send();
    }
    
    /**
     * Updates an existing role record.
     * @see LabbcatAdmin#createRole
     * @see LabbcatAdmin#readRoles
     * @see LabbcatAdmin#deleteRole
     * @param {string} role_id The name/ID of the role.
     * @param {string} description The description of the role.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: A copy of the role record. 
     */
    updateRole(role_id, description, onResult) {
      this.createRequest(
        "roles", null, onResult, this.baseUrl+"api/admin/roles", "PUT")
        .send(JSON.stringify({
          role_id : role_id,
          description : description}));
    }
    
    /**
     * Deletes an existing role record.
     * @see LabbcatAdmin#createRole
     * @see LabbcatAdmin#readRoles
     * @see LabbcatAdmin#updateRole
     * @param {string} role_id The name/ID of the role.
     * @param {resultCallback} onResult Invoked when the request has completed.
     */
    deleteRole(role_id, onResult) {
      this.createRequest(
        "roles", null, onResult, `${this.baseUrl}api/admin/roles/${role_id}`,
        "DELETE").send();
    }
    
    /**
     * Creates a new role permission record.
     * @see LabbcatAdmin#readRolePermissions
     * @see LabbcatAdmin#updateRolePermission
     * @see LabbcatAdmin#deleteRolePermission
     * @param {string} role_id The name/ID of the role.
     * @param {string} entity A string indentifying the entities the permission
     * applies to.
     * @param {string} layer The ID of a  a transcript attribute layer (or "corpus")
     * the label of which determines the access.
     * @param {string} value_pattern A regular expression; if the value of the
     * label identified by <var> layer </var> matches this pattern, then
     * access to the entities identfied by <var> entity </var> is granted.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: A copy of the role permission record, 
     * including <em> rolePermission_id </em> - The database key for the record. 
     */
    createRolePermission(role_id, entity, layer, value_pattern, onResult) {
      this.createRequest(
        "rolePermissions", null, (permission, errors, messages, call, id) => {
          if (permission) {
            // attribute_name -> layer
            if (permission.attribute_name == "corpus") {
              permission.layer = permission.attribute_name;
            } else {
              permission.layer = "transcript_" + permission.attribute_name;
            }
          }
          if (onResult) onResult(permission, errors, messages, call, id);
        }, this.baseUrl+"api/admin/roles/permissions", "POST",
        null, "application/json")
        .send(JSON.stringify({
          role_id : role_id,
          entity : entity,
          attribute_name : layer.replace(/^transcript_/,""), // layer -> attribute_name
          value_pattern : value_pattern}));
    }
    
    /**
     * Reads a list of role permission records for a given user role.
     * @see LabbcatAdmin#createRolePermission
     * @see LabbcatAdmin#updateRolePermission
     * @see LabbcatAdmin#deleteRolePermission
     * @param {string} role_id The name/ID of the role.
     * @param {int} [pageNumber] The zero-based  page of records to return (if null, all
     * records will be returned). 
     * @param {int} [pageLength] The length of pages (if null, the default page length is 20).
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: A list of role permission records with the following
     * attributes:
     * <dl>
     *  <dt> role_id </dt> <dd> The name/id of the rolePermission. </dd>
     *  <dt> entity </dt> <dd> A string indentifying the entities the permission
     *    applies to. </dd>
     *  <dt> layer </dt> <dd> The ID of a  a transcript attribute layer (or "corpus")
     *    the label of which determines the access. </dd>
     *  <dt> value_pattern </dt> <dd> A regular expression; if the value of the
     *    label identified by <var> layer </var> matches this pattern, then
     *    access to the entities identfied by <var> entity </var> is granted. </dd>
     *  <dt> _cantDelete </dt> <dd> This is not a database field, but rather is present in
     *    records returned from the server that can not currently be deleted; 
     *    a string representing the reason the record can't be deleted. </dd>
     * </dl>
     */
    readRolePermissions(role_id, pageNumber, pageLength, onResult) {
      if (typeof pageNumber === "function") { // (onResult)
        onResult = pageNumber;
        pageNumber = null;
        pageLength = null;
      } else if (typeof l === "function") { // (p, onResult)
        onResult = l;
        pageLength = null;
      }
      this.createRequest(
        "rolePermissions", {
          pageNumber:pageNumber,
          pageLength:pageLength
        }, (permissions, errors, messages, call, id) => {
          if (permissions) {
            // attribute_name -> layer
            for (var permission of permissions) {
              if (permission.attribute_name == "corpus") {
                permission.layer = permission.attribute_name;
              } else {
                permission.layer = "transcript_" + permission.attribute_name;
              }
            }
          }
          if (onResult) onResult(permissions, errors, messages, call, id);
        }, `${this.baseUrl}api/admin/roles/permissions/${role_id}`)
        .send();
    }
    
    /**
     * Updates an existing role permission record.
     * @see LabbcatAdmin#createRolePermission
     * @see LabbcatAdmin#readRolePermissions
     * @see LabbcatAdmin#deleteRolePermission
     * @param {string} role_id The name/ID of the role.
     * @param {string} entity A string indentifying the entities the permission
     * applies to.
     * @param {string} layer The ID of a  a transcript attribute layer (or "corpus")
     * the label of which determines the access.
     * @param {string} value_pattern A regular expression; if the value of the
     * label identified by <var> layer </var> matches this pattern, then
     * access to the entities identfied by <var> entity </var> is granted.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: A copy of the role permission record. 
     */
    updateRolePermission(role_id, entity, layer, value_pattern, onResult) {
      var permission = this.createRequest(
        "rolePermissions", null, (permission, errors, messages, call, id) => {
          if (permission) {
            // attribute_name -> layer
            if (permission.attribute_name == "corpus") {
              permission.layer = permission.attribute_name;
            } else {
              permission.layer = "transcript_" + permission.attribute_name;
            }
          }
          if (onResult) onResult(permission, errors, messages, call, id);
        }, this.baseUrl+"api/admin/roles/permissions", "PUT")
          .send(JSON.stringify({
            role_id : role_id,
            entity : entity,
            attribute_name : layer.replace(/^transcript_/,""), // layer -> attribute_name
            value_pattern : value_pattern}));
    }
    
    /**
     * Deletes an existing role permission record.
     * @see LabbcatAdmin#createRolePermission
     * @see LabbcatAdmin#readRolePermissions
     * @see LabbcatAdmin#updateRolePermission
     * @param {string} rolePermission_id The name/ID of the rolePermission.
     * @param {resultCallback} onResult Invoked when the request has completed.
     */
    deleteRolePermission(role_id, entity, onResult) {
      this.createRequest(
        "rolePermissions", null, onResult, `${this.baseUrl}api/admin/roles/permissions/${role_id}/${entity}`,
        "DELETE").send();
    }
    
    /**
     * Reads a list of system attribute records.
     * @see LabbcatAdmin#updateSystemAttribute
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: A list of system attribute records with the following
     * attributes:
     * <dl>
     *  <dt> attribute </dt> <dd> ID of the attribute. </dd>
     *  <dt> type </dt> <dd> The type of the attribute - "string", "integer",
     *                       "boolean", "select", etc. </dd>
     *  <dt> style </dt> <dd> Style definition which depends on <var> type </var> -
     *                        e.g. whether the "boolean" is shown as a checkbox or
     *                        radio buttons, etc. </dd>
     *  <dt> label </dt> <dd> User-facing label for the attribute. </dd>
     *  <dt> description </dt> <dd> User-facing (long) description for the attribute. </dd>
     *  <dt> options </dt> <dd> If <var> type </var> is "select", this is an object
     *                          defining the valid options for the attribute, where
     *                          the attribute key is the attribute value and the attribute
     *                          value is the user-facing label for the option. </dd>
     *  <dt> value </dt> <dd> The value of the attribute. </dd>
     * </dl>
     */
    readSystemAttributes(onResult) {
      this.createRequest(
        "systemattributes", null, onResult, this.baseUrl+"api/admin/systemattributes")
        .send();
    }
    
    /**
     * Updates an existing system attribute record.
     * @see LabbcatAdmin#readSystemAttributes
     * @param {string} attribute The ID of the attribute.
     * @param {string} value The value of the attribute.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: A copy of the system attribute record. 
     */
    updateSystemAttribute(attribute, value, onResult) {
      this.createRequest(
        "systemattributes", null, onResult, this.baseUrl+"api/admin/systemattributes", "PUT")
        .send(JSON.stringify({
          attribute : attribute,
          value : value}));
    }
    
    /**
     * Uploads an annotator module in preparation for installing it.
     * @param {string|file} jarFile Annotator .jar file.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: An object describing the attributes of the
     * annotator found in the jar file:
     * <dl>
     *  <dt> jar </dt><dd> The name of the file uploaded. </dd>
     *  <dt> annotatorId </dt><dd> The ID of the annotator. </dd>
     *  <dt> version </dt><dd> The version of the annotator implementation. </dd>
     *  <dt> installedVersion </dt><dd> The version of the annotator that this one will 
     *         replace, if the annotator ID has already been installed. </dd> 
     *  <dt> hasConfigWebapp </dt><dd> Whether the annotator has a
     *         installation/configuration web-app. </dd>
     *  <dt> hasTaskWebapp </dt><dd> Whether the annotator has a task definition
     *         web-app. </dd>
     *  <dt> hasExtWebapp </dt><dd> Whether the annotator has an 'extensions' web-app. </dd>
     *  <dt> info </dt><dd> HTML document describing the annotator. </dd>
     * </dl>
     * @param onProgress Invoked on XMLHttpRequest progress.
     */
    uploadAnnotator(jarFile, onResult, onProgress) {

      // create form
      var fd = new FormData();

      // TODO nzibb/labbcat-server/user-interface thinks it's running on Node when actually
      // it's running in a browser, so we need a better test for runningOnNode
      if (runningOnNode || true) {                
        
	fd.append("jarFile", jarFile);
	// create HTTP request
	var xhr = new XMLHttpRequest();
	xhr.call = "uploadAnnotator";
	xhr.onResult = onResult;
	xhr.addEventListener("load", callComplete, false);
	xhr.addEventListener("error", callFailed, false);
	xhr.addEventListener("abort", callCancelled, false);
	xhr.upload.addEventListener("progress", onProgress, false);
	
	xhr.open("POST", this.baseUrl + "admin/annotator");
	if (this.username) {
	  xhr.setRequestHeader("Authorization", "Basic " + btoa(this.username + ":" + this.password))
	}
	xhr.setRequestHeader("Accept", "application/json");
	xhr.send(fd);
      } else { // runningOnNode

	var jarName = jarFile.replace(/.*\//g, "");
        if (exports.verbose) console.log("jarName: " + jarName);
        fd.append(
          "jarFile", 
	  fs.createReadStream(jarFile).on('error', function(){
	    onResult(null, ["Invalid jar: " + jarFile], [], "uploadAnnotator", jarFile);
	  }), jarName);
        
	var urlParts = parseUrl(this.baseUrl + "admin/annotator");
	// for tomcat 8, we need to explicitly send the content-type and content-length headers...
	var labbcat = this;
        var password = this._password;
	fd.getLength(function(something, contentLength) {
	  var requestParameters = {
	    port: urlParts.port,
	    path: urlParts.pathname,
	    host: urlParts.hostname,
	    headers: {
	      "Accept" : "application/json",
	      "content-length" : contentLength,
	      "Content-Type" : "multipart/form-data; boundary=" + fd.getBoundary()
	    }
	  };
	  if (labbcat.username && password) {
	    requestParameters.auth = labbcat.username+':'+password;
	  }
	  if (/^https.*/.test(labbcat.baseUrl)) {
	    requestParameters.protocol = "https:";
	  }
          if (exports.verbose) {
            console.log("submit: " + labbcat.baseUrl + "edit/transcript/new");
          }
	  fd.submit(requestParameters, function(err, res) {
	    var responseText = "";
	    if (!err) {
	      res.on('data',function(buffer) {
		//console.log('data ' + buffer);
		responseText += buffer;
	      });
	      res.on('end',function(){
                if (exports.verbose) console.log("response: " + responseText);
	        var result = null;
	        var errors = null;
	        var messages = null;
		try {
		  var response = JSON.parse(responseText);
		  result = response.model.result || response.model;
		  errors = response.errors;
		  if (errors && errors.length == 0) errors = null
		  messages = response.messages;
		  if (messages && messages.length == 0) messages = null
		} catch(exception) {
		  result = null
                  errors = ["" +exception+ ": " + labbcat.responseText];
                  messages = [];
		}
                // for this call, the result is an object with one key, whose
                // value is the threadId - so just return that
		onResult(
                  result[jarName], errors, messages, "uploadAnnotator", jarName);
	      });
	    } else {
	      onResult(null, ["" +err+ ": " + labbcat.responseText], [], "uploadAnnotator", jarName);
	    }
	    
	    if (res) res.resume();
	  });
	}); // got length
      } // runningOnNode
    }
    
    /**
     * Uploads an annotator module in preparation for installing it.
     * @param {string} jar Name of the annotator .jar file already uploaded, as
     * specified by the "jar" attribute of the uploadAnnotator() response. 
     * @param {boolean} install true to install the annotator, false to cancel the
     * installation. 
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: the annotator ID if it was installed, and null 
     * otherwise.
     */
    installAnnotator(jar, install, onResult) {
      this.createRequest(
        "installAnnotator", null, onResult, this.baseUrl+"admin/annotator",
        "POST", // not GET, because the number of parameters can make the URL too long
        null, "application/x-www-form-urlencoded;charset=\"utf-8\"")
        .send(this.parametersToQueryString({
          jar : jar,
          action : install?"install":"cancel"
        }));
    }

    /**
     * Uploads an annotator module in preparation for installing it.
     * @param {string} annotatorId ID of the annotator to uninstall.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var>.
     */
    uninstallAnnotator(annotatorId, onResult) {
      this.createRequest(
        "uninstallAnnotator", null, onResult, this.baseUrl+"admin/annotator",
        "POST", // not GET, because the number of parameters can make the URL too long
        null, "application/x-www-form-urlencoded;charset=\"utf-8\"")
        .send(this.parametersToQueryString({
          annotatorId : annotatorId,
          action : "uninstall"
        }));
    }

    /**
     * Create a new annotator task with the given ID and description.
     * @param {string} annotatorId The ID of the annotator that will perform the task.
     * @param {string} taskId The ID of the task, which must not already exist.
     * @param {string} description The description of the task.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var>.
     */
    newAnnotatorTask(annotatorId, taskId, description, onResult) {
      this.createRequest(
        "newAnnotatorTask", null, onResult, null, "POST",
        this.storeAdminUrl+"newAnnotatorTask",
        "application/x-www-form-urlencoded;charset=\"utf-8\"")
        .send(this.parametersToQueryString({
          annotatorId: annotatorId,
          taskId: taskId,
          description: description
        }));
    }
    
    /**
     * Update the annotator task description.
     * @param {string} taskId The ID of the task, which must not already exist.
     * @param {string} description The description of the task.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var>.
     */
    saveAnnotatorTaskDescription(taskId, description, onResult) {
      this.createRequest(
        "saveAnnotatorTaskDescription", null, onResult, null, "POST",
        this.storeAdminUrl+"saveAnnotatorTaskDescription",
        "application/x-www-form-urlencoded;charset=\"utf-8\"")
        .send(this.parametersToQueryString({
          taskId: taskId,
          description: description
        }));
    }
    
    /**
     * Update the annotator task parameters.
     * @param {string} taskId The ID of the task, which must not already exist.
     * @param {string} parameters The task parameters, serialized as a string.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var>.
     */
    saveAnnotatorTaskParameters(taskId, parameters, onResult) {
      this.createRequest(
        "saveAnnotatorTaskParameters", null, onResult, null, "POST",
        this.storeAdminUrl+"saveAnnotatorTaskParameters",
        "application/x-www-form-urlencoded;charset=\"utf-8\"")
        .send(this.parametersToQueryString({
          taskId: taskId,
          parameters: parameters
        }));
    }
    
    /**
     * Delete the identified automation task.
     * @param {string} taskId The ID of the task, which must not already exist.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var>.
     */
    deleteAnnotatorTask(taskId, onResult) {
      this.createRequest(
        "deleteAnnotatorTask", null, onResult, null, "POST",
        this.storeAdminUrl+"deleteAnnotatorTask",
        "application/x-www-form-urlencoded;charset=\"utf-8\"")
        .send(this.parametersToQueryString({
          taskId: taskId
        }));
    }
    
    /**
     * Uploads an transcriber module in preparation for installing it.
     * @param {string|file} jarFile Transcriber .jar file.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: An object describing the attributes of the
     * transcriber found in the jar file:
     * <dl>
     *  <dt> jar </dt><dd> The name of the file uploaded. </dd>
     *  <dt> transcriberId </dt><dd> The ID of the transcriber. </dd>
     *  <dt> version </dt><dd> The version of the transcriber implementation. </dd>
     *  <dt> installedVersion </dt><dd> The version of the transcriber that this one will 
     *         replace, if the transcriber ID has already been installed. </dd> 
     *  <dt> hasConfigWebapp </dt><dd> Whether the transcriber has a
     *         installation/configuration web-app. </dd>
     *  <dt> info </dt><dd> HTML document describing the transcriber. </dd>
     * </dl>
     * @param onProgress Invoked on XMLHttpRequest progress.
     */
    uploadTranscriber(jarFile, onResult, onProgress) {

      // create form
      var fd = new FormData();

      // TODO nzibb/labbcat-server/user-interface thinks it's running on Node when actually
      // it's running in a browser, so we need a better test for runningOnNode
      if (runningOnNode || true) {                
        
	fd.append("jarFile", jarFile);
	// create HTTP request
	var xhr = new XMLHttpRequest();
	xhr.call = "uploadTranscriber";
	xhr.onResult = onResult;
	xhr.addEventListener("load", callComplete, false);
	xhr.addEventListener("error", callFailed, false);
	xhr.addEventListener("abort", callCancelled, false);
	xhr.upload.addEventListener("progress", onProgress, false);
	
	xhr.open("POST", this.baseUrl + "admin/transcriber");
	if (this.username) {
	  xhr.setRequestHeader("Authorization", "Basic " + btoa(this.username + ":" + this.password))
	}
	xhr.setRequestHeader("Accept", "application/json");
	xhr.send(fd);
      } else { // runningOnNode

	var jarName = jarFile.replace(/.*\//g, "");
        if (exports.verbose) console.log("jarName: " + jarName);
        fd.append(
          "jarFile", 
	  fs.createReadStream(jarFile).on('error', function(){
	    onResult(null, ["Invalid jar: " + jarFile], [], "uploadTranscriber", jarFile);
	  }), jarName);
        
	var urlParts = parseUrl(this.baseUrl + "admin/transcriber");
	// for tomcat 8, we need to explicitly send the content-type and content-length headers...
	var labbcat = this;
        var password = this._password;
	fd.getLength(function(something, contentLength) {
	  var requestParameters = {
	    port: urlParts.port,
	    path: urlParts.pathname,
	    host: urlParts.hostname,
	    headers: {
	      "Accept" : "application/json",
	      "content-length" : contentLength,
	      "Content-Type" : "multipart/form-data; boundary=" + fd.getBoundary()
	    }
	  };
	  if (labbcat.username && password) {
	    requestParameters.auth = labbcat.username+':'+password;
	  }
	  if (/^https.*/.test(labbcat.baseUrl)) {
	    requestParameters.protocol = "https:";
	  }
          if (exports.verbose) {
            console.log("submit: " + labbcat.baseUrl + "edit/transcript/new");
          }
	  fd.submit(requestParameters, function(err, res) {
	    var responseText = "";
	    if (!err) {
	      res.on('data',function(buffer) {
		//console.log('data ' + buffer);
		responseText += buffer;
	      });
	      res.on('end',function(){
                if (exports.verbose) console.log("response: " + responseText);
	        var result = null;
	        var errors = null;
	        var messages = null;
		try {
		  var response = JSON.parse(responseText);
		  result = response.model.result || response.model;
		  errors = response.errors;
		  if (errors && errors.length == 0) errors = null
		  messages = response.messages;
		  if (messages && messages.length == 0) messages = null
		} catch(exception) {
		  result = null
                  errors = ["" +exception+ ": " + labbcat.responseText];
                  messages = [];
		}
                // for this call, the result is an object with one key, whose
                // value is the threadId - so just return that
		onResult(
                  result[jarName], errors, messages, "uploadTranscriber", jarName);
	      });
	    } else {
	      onResult(null, ["" +err+ ": " + labbcat.responseText], [], "uploadTranscriber", jarName);
	    }
	    
	    if (res) res.resume();
	  });
	}); // got length
      } // runningOnNode
    }
    
    /**
     * Uploads an transcriber module in preparation for installing it.
     * @param {string} jar Name of the transcriber .jar file already uploaded, as
     * specified by the "jar" attribute of the uploadTranscriber() response. 
     * @param {boolean} install true to install the transcriber, false to cancel the
     * installation. 
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: the transcriber ID if it was installed, and null 
     * otherwise.
     */
    installTranscriber(jar, install, onResult) {
      this.createRequest(
        "installTranscriber", null, onResult, this.baseUrl+"admin/transcriber",
        "POST", // not GET, because the number of parameters can make the URL too long
        null, "application/x-www-form-urlencoded;charset=\"utf-8\"")
        .send(this.parametersToQueryString({
          jar : jar,
          action : install?"install":"cancel"
        }));
    }

    /**
     * Uploads an transcriber module in preparation for installing it.
     * @param {string} transcriberId ID of the transcriber to uninstall.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var>.
     */
    uninstallTranscriber(transcriberId, onResult) {
      this.createRequest(
        "uninstallTranscriber", null, onResult, this.baseUrl+"admin/transcriber",
        "POST", // not GET, because the number of parameters can make the URL too long
        null, "application/x-www-form-urlencoded;charset=\"utf-8\"")
        .send(this.parametersToQueryString({
          transcriberId : transcriberId,
          action : "uninstall"
        }));
    }

    /**
     * Creates a new user record.
     * @see LabbcatAdmin#readUsers
     * @see LabbcatAdmin#updateUser
     * @see LabbcatAdmin#deleteUser
     * @param {string} user The ID of the user.
     * @param {string} email The email address of the user.
     * @param {boolean} resetPassword Whether the user must reset their password when
     * they next log in. 
     * @param {string[]} roles Roles or groups the user belongs to.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: A copy of the user record, 
     * including <em> user </em> - The database key for the record. 
     */
    createUser(user, email, resetPassword, roles, onResult) {
      this.createRequest(
        "users", null, onResult, this.baseUrl+"api/admin/users", "POST",
        null, "application/json")
        .send(JSON.stringify({
          user : user,
          email : email,
          resetPassword : resetPassword?1:0,
          roles : roles}));
    }
    
    /**
     * Reads a list of user records.
     * @see LabbcatAdmin#createUser
     * @see LabbcatAdmin#updateUser
     * @see LabbcatAdmin#deleteUser
     * @param {int} [pageNumber] The zero-based  page of records to return (if null, all
     * records will be returned). 
     * @param {int} [pageLength] The length of pages (if null, the default page length is 20).
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: A list of user records with the following
     * attributes:
     * <dl>
     *  <dt> user </dt> <dd> The name/id of the user. </dd>
     *  <dt> email </dt> <dd> The email address of the user. </dd>
     *  <dt> resetPassword </dt> <dd> Whether the user must reset their password when
     * they next log in. </dd>
     *  <dt> roles </dt> <dd> Roles or groups the user belongs to. </dd>
     *  <dt> _cantDelete </dt> <dd> This is not a database field, but rather is present in
     *    records returned from the server that can not currently be deleted; 
     *    a string representing the reason the record can't be deleted. </dd>
     * </dl>
     */
    readUsers(pageNumber, pageLength, onResult) {
      if (typeof pageNumber === "function") { // (onResult)
        onResult = pageNumber;
        pageNumber = null;
        pageLength = null;
      } else if (typeof l === "function") { // (p, onResult)
        onResult = l;
        pageLength = null;
      }
      this.createRequest(
        "users", {
          pageNumber:pageNumber,
          pageLength:pageLength
        }, onResult, this.baseUrl+"api/admin/users")
        .send();
    }
    
    /**
     * Updates an existing user record.
     * @see LabbcatAdmin#createUser
     * @see LabbcatAdmin#readUsers
     * @see LabbcatAdmin#deleteUser
     * @param {string} user The ID of the user.
     * @param {string} email The email address of the user.
     * @param {boolean} resetPassword Whether the user must reset their password when
     * they next log in. 
     * @param {string[]} roles Roles or groups the user belongs to.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be: A copy of the user record. 
     */
    updateUser(user, email, resetPassword, roles, onResult) {
      if (exports.verbose) console.log("updateUser("+user+", "+email+", "+resetPassword+", "+JSON.stringify(roles));
      this.createRequest(
        "users", null, onResult, this.baseUrl+"api/admin/users", "PUT")
        .send(JSON.stringify({
          user : user,
          email : email,
          resetPassword : resetPassword?1:0,
          roles : roles}));
    }
    
    /**
     * Deletes an existing user record.
     * @see LabbcatAdmin#createUser
     * @see LabbcatAdmin#readUsers
     * @see LabbcatAdmin#updateUser
     * @param {string} user The name/ID of the user.
     * @param {resultCallback} onResult Invoked when the request has completed.
     */
    deleteUser(user, onResult) {
      this.createRequest(
        "users", null, onResult, `${this.baseUrl}api/admin/users/${user}`,
        "DELETE").send();
    }
    
    /**
     * Sets a given user's password.
     * @param {string} user The ID of the user.
     * @param {string} password The new password.
     * @param {boolean} resetPassword Whether the user must reset their password when
     * they next log in. 
     * @param {resultCallback} onResult Invoked when the request has returned. 
     */
    setPassword(user, password, resetPassword, onResult) {
      if (exports.verbose) console.log(`updateUsersetP(${user}, ****, ${resetPassword})`);
      this.createRequest(
        "users", null, onResult, this.baseUrl+"api/admin/password", "PUT")
        .send(JSON.stringify({
          user : user,
          password : password,
          resetPassword : resetPassword}));
    }

    /**
     * Updates the current license agreement (HTML) document (creating it if it 
     * doesn't already exist).
     * @param {string} agreementHtml The HTML content of the document. 
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be "OK" if the operation succeeded.
     */
    updateAgreement(agreementHtml, onResult) {
      this.createRequest(
        "updateAgreement", null, onResult, this.baseUrl+"agreement.html", "PUT")
        .send(agreementHtml);
    }

    /**
     * Deletes the current license agreement (HTML) document.
     * @param {resultCallback} onResult Invoked when the request has returned a 
     * <var>result</var> which will be "OK" if the operation succeeded.
     */
    deleteAgreement(onResult) {
      this.createRequest(
        "deleteAgreement", null, onResult, `${this.baseUrl}agreement.html`, "DELETE")
        .send();
    }

  }
  
  /**
   * Interpreter for match ID strings.
   * <p>The schema is:</p>
   * <ul>
   * 	<li>
   * 		when there's a defining annotation UID:<br>
   * 		g_<i>ag_id</i>;<em>uid</em><br>
   * 		e.g. <tt>g_243;em_12_20035</tt></li>
   * 	<li>
   * 		when there's anchor IDs:<br>
   * 		g_<i>ag_id</i>;<em>startuid</em>-<em>enduid</em><br>
   * 		e.g. <tt>g_243;n_72700-n_72709</tt></li>
   * 	<li>
   * 		when there's anchor offsets:<br>
   * 		g_<i>ag_id</i>;<em>startoffset</em>-<em>endoffset</em><br>
   * 		e.g. <tt>g_243;39.400-46.279</tt></li>
   * 	<li>
   * 		when there's a participant/speaker number, it will be appended:<br>
   * 		<em>...</em>;p_<em>speakernumber</em><br>
   * 		e.g.&nbsp;<tt>g_243;n_72700-n_72709;p_76</tt></li>
   * 	<li>
   * 		matching subparts can be identified by appending a list of annotation UIDs for insertion into {@link #mMatchAnnotationUids}, the keys being enclosed in square brackets:<br>
   * 		...;<em>[key]=uid;[key]=uid</em><br>
   * 		e.g. <samp>g_243;n_72700-n_72709;[0,0]=ew_0_123;[1,0]ew_0_234</samp></li>
   * 	<li>
   * 		a target annotation by appending a uid prefixed by <samp>#=</samp>:<br>
   * 		...;#=<em>uid</em><br>
   * 		e.g. <samp>g_243;n_72700-n_72709;#=ew_0_123</samp></li>
   * 	<li>
   * 		other items (search name or prefix) could then come after all that, and key=value pairs:<br>
   * 		...;<em>key</em>=<em>value</em><br>
   * 		e.g.&nbsp;<tt>g_243;n_72700-n_72709;ew_0_123-ew_0_234;prefix=024-;name=the_aeiou</tt></li>
   * <p>These can be something like:
   * <ul>
   * <li><q>g_3;em_11_23;n_19985-n_20003;p_4;#=ew_0_12611;prefix=001-;[0]=ew_0_12611</q></li>
   * <li><q>AgnesShacklock-01.trs;60.897-67.922;prefix=001-</q></li>
   * <li><q>AgnesShacklock-01.trs;60.897-67.922;m_-1_23</q></li>
   * </ul>
   */
  class MatchId {
    /**
     * String constructor.
     */
    constructor(matchId) {
      this._transcriptId = null;
      this._startAnchorId = null;
      this._endAnchorId = null;
      this._startOffset = null;
      this._endOffset = null;
      this._utteranceId = null;
      this._participantId = null;
      this._targetId = null;
      this._prefix = null;
      if (matchId) {
        const parts = matchId.split(";");
        this._transcriptId = parts[0];
        let intervalPart = null;
        for (let part of parts) {
          if (part == parts[0]) continue;
          if (part.indexOf("-") > 0) {
            intervalPart = part;
            break;
          }
        } // next part
        const interval = intervalPart.split("-");
        if (interval[0].startsWith("n_")) { // anchor IDs
          this._startAnchorId = interval[0];
          this._endAnchorId = interval[1];
        } else { // offsets
          this._startOffset = parseFloat(interval[0]);
          this._endOffset = parseFloat(interval[1]);
        }
        for (let part of parts) {
          if (part.startsWith("prefix=")) {
            this._prefix = part.substring("prefix=".length);
          } else if (part.startsWith("em_") || part.startsWith("m_")) {
            this._utteranceId = part;
          } else if (part.startsWith("p_")) {
            this._participantId = part;
          } else if (part.startsWith("#=")) {
            this._targetId = part.substring("#=".length);
          }
        } // next part
      } // string was given
    }
    /**
     * The transcript identifier.
     */
    get transcriptId() { return this._transcriptId; }
    /**
     * ID of the start anchor.
     */
    get startAnchorId() { return this._startAnchorId; }
    /**
     * ID of the end anchor.
     */
    get endAnchorId() { return this._endAnchorId; }
    /**
     * Offset of the start anchor.
     */
    get startOffset() { return this._startOffset; }
    /**
     * Offset of the end anchor.
     */
    get endOffset() { return this._endOffset; }
    /**
     * ID of the participant.
     */
    get participantId() { return this._participantId; }
    /**
     * ID of the match utterance.
     */
    get utteranceId() { return this._utteranceId; }
    /**
     * ID of the match target annotation.
     */
    get targetId() { return this._targetId; }
    /**
     * Match prefix for fragments.
     */
    get prefix() { return this._prefix; }
  }
  
  exports.LabbcatView = LabbcatView;
  exports.LabbcatEdit = LabbcatEdit;
  exports.LabbcatAdmin = LabbcatAdmin;
  exports.MatchId = MatchId;
  exports.verbose = false;
  exports.language = false;

}(typeof exports === 'undefined' ? this.labbcat = {} : exports));
