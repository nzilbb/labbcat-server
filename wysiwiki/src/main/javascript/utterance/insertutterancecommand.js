import Command from '@ckeditor/ckeditor5-core/src/command';

const baseUrl = document.URL.replace(/\/doc\/.*/,"");

let layers = ["word"];

export default class InsertUtteranceCommand extends Command {
    execute() {
        
        // ask for a URL
        const utteranceUrl = prompt( 'Utterance URL' );
        if (!utteranceUrl) return;
        
        // parse out the bits we're interested in
        const urlPattern = /.*?transcript=([^&?]*)(&.*)?#(.*)$/
        if (!urlPattern.test(utteranceUrl)) {
            alert(`Invalid utterance URL:\n${utteranceUrl}`);
            return;
        }
        const transcriptId = utteranceUrl.replace(urlPattern,"$1");
        const id = utteranceUrl.replace(urlPattern,"$3");
        if (id.startsWith("ew_0_")) { 
            this.getUtteranceId(transcriptId, id);
        } else if (id.startsWith("em_12_")) { 
            this.getFragment(transcriptId, id);
        } else {
            alert(`Could not identify target ID from URL:\n${utteranceUrl}`);
            return;
        }
    }
    
    refresh() {
        const model = this.editor.model;
        const selection = model.document.selection;
        const allowedIn = model.schema.findAllowedParent( selection.getFirstPosition(), 'utterance' );
        
        this.isEnabled = allowedIn !== null;
    }
    
    getUtteranceId(transcriptId, wordId) {
        // query for the utterance ID
        const query = encodeURIComponent(
            `layer.id == 'utterance' && all('word').includes('${wordId}')`);
        const queryUrl
              = `${baseUrl}/api/store/getMatchingAnnotations?expression=${query}`;
        $.getJSON(queryUrl, (response, status)=>{
            if (response.errors.length > 0) {
                alert(response.errors.join("\n"));
                return;
            }
            const utterances = response.model;
            if (!utterances.length) {
                alert("Utterance not found.");
                return;
            }
            const utterance = utterances[0];
            this.getFragment(transcriptId, utterance.id);
        });        
    }
    
    getFragment(transcriptId, utteranceId) {
        // what layers are currently selected?
        $.getJSON(`${baseUrl}/selectedLayers`, (response, status)=>{
            layers = response.model;
            // ensure that at least "word" is selected
            if (!layers || layers.length == 0) layers = [ "word" ];
            if (!layers.includes("word")) layers.push("word");
            // don't want "orthography"
            if (layers.includes("orthography")) layers = layers.filter(l=>l != "orthography");
            const layerIds = layers.join("&layerIds=");
            
            // load fragment including selected layers
            const fragmentUrl = `${baseUrl}/api/store/getFragment?id=${transcriptId}&annotationId=${utteranceId}&layerIds=${layerIds}`;
            $.getJSON(fragmentUrl, (response, status)=>{
                if (response.errors.length > 0) {
                    alert(response.errors.join("\n"));
                    return;
                }
                
                // extract out the bits we want
                const fragment = response.model;
                const idPattern = /.*__([0-9]+\.[0-9]+)-([0-9]+\.[0-9]+)$/;
                const startOffset = fragment.id.replace(idPattern,"$1")
                const endOffset = fragment.id.replace(idPattern,"$2")
                
                // compile URLs we need
                const audioUrl = `${baseUrl}/soundfragment?id=${transcriptId}&start=${startOffset}&end=${endOffset}`;
                const transcriptUrl = `${baseUrl}/transcript?transcript=${transcriptId}#${utteranceId}`;
                
                // create the utterance element
                this.editor.model.change( writer => {
                    this.editor.model.insertContent(
                        this.createUtterance(writer, transcriptUrl, audioUrl,
                                             fragment.participant[0].turn[0]));
                });
            });
        });
    }
    
    createUtterance(writer, transcriptUrl, audioUrl, turn) {
        const utterance = writer.createElement('utterance');
        const utteranceAudio = writer.createElement('utteranceAudio', {
            source: audioUrl,
            controls: true
        });
        const utteranceDescription = writer.createElement( 'utteranceDescription' );
        
        const words = turn.word;
        const phraseLayers = [];
        const wordLayers = [];
        for (let l of layers) {
            if (l != "word") {
                if (turn[l]) {
                    phraseLayers.push(l);
                } else {
                    wordLayers.push(l);
                }
            }
        } // next layer
        
        // add words
        const wordsElement = writer.createElement( 'words' );
        writer.append(wordsElement, utteranceDescription);
        if (wordLayers.length > 0) {
            // add 'title' labels
            const token = writer.createElement( 'token' );
            for (let l of wordLayers) {
                const tag = writer.createElement( 'tag' );
                const text = writer.createText( `${l} :` );
                writer.append(text, tag);
                writer.append(tag, token);
            } // next word layer
            const word = writer.createElement( 'word' );
            const text = writer.createText( 'word :' );
            writer.append(text, word);
            writer.append(word, token);
            writer.append(token, wordsElement);
        } // there are layers selected
        
        // add word tokens
        for (let w of words) {
            const token = writer.createElement( 'token' );
            for (let l of wordLayers) {
                const tag = writer.createElement( 'tag', { layer: l } );
                let tags = " ";
                if (w[l]) { // if this word has tags on this layer
                    tags = w[l].map(t=>t.label).join(" ");
                }
                const text = writer.createText(tags);
                writer.append(text, tag);
                writer.append(tag, token);
            } // next word layer
            const word = writer.createElement( 'word' );
            const text = writer.createText( w.label, {
                linkHref: transcriptUrl,
                linkIsExternal: true
            } );
            writer.append(text, word);
            writer.append(word, token);
            writer.append(token, wordsElement);
        } // next word token
        
        // add phrases
        for (let l of phraseLayers) {
            const phrase = writer.createElement( 'phrase', { layer: l } );
            let tags = " ";
            if (turn[l]) { // if this word has tags on this layer
                tags = turn[l].map(t=>t.label).join(" ");
            }
            const text = writer.createText(tags);
            writer.append(text, phrase);
            writer.append(phrase, utteranceDescription);
        } // next phrase layer
        
        writer.append(utteranceAudio, utterance);
        writer.append(utteranceDescription, utterance);
        
        return utterance;
    }

}
