import Command from '@ckeditor/ckeditor5-core/src/command';

const baseUrl = document.URL.replace(/\/doc\/.*/,"");

let layers = ["word"];

export default class InsertUtteranceCommand extends Command {
    execute() {
        const utteranceUrl = prompt( 'Utterance URL' );
        if (!utteranceUrl) return;
        // layer.id=='utterance' %26%26 'ew_0_705455' IN all('word')
        const urlPattern = /.*?transcript=([^&?]*)(&.*)?#(.*)$/
        if (!urlPattern.test(utteranceUrl)) {
            alert(`Invalid utterance URL:\n${utteranceUrl}`);
            return;
        }
        const transcriptId = utteranceUrl.replace(urlPattern,"$1");
        const wordId = utteranceUrl.replace(urlPattern,"$3");
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
            $.getJSON(`${baseUrl}/selectedLayers`, (response, status)=>{
                layers = response.model;
                // ensure that at least "word" is selected
                if (!layers || layers.length == 0) layers = [ "word" ];
                if (!layers.includes("word")) layers.push("word");
                // don't want "orthography"
                if (layers.includes("orthography")) layers = layers.filter(l=>l != "orthography");
                const layerIds = layers.join("&layerIds=");
                // load fragment including selected layers
                const fragmentUrl = `${baseUrl}/api/store/getFragment?id=${transcriptId}&annotationId=${utterance.id}&layerIds=${layerIds}`;
                $.getJSON(fragmentUrl, (response, status)=>{
                    if (response.errors.length > 0) {
                        alert(response.errors.join("\n"));
                        return;
                    }
                    const fragment = response.model;
                    const utteranceId = fragment.participant[0].turn[0].utterance[0].id;
                    const idPattern = /.*__([0-9]+\.[0-9]+)-([0-9]+\.[0-9]+)$/;
                    const startOffset = fragment.id.replace(idPattern,"$1")
                    const endOffset = fragment.id.replace(idPattern,"$2")
                    const audioUrl = `${baseUrl}/soundfragment?id=${transcriptId}&start=${startOffset}&end=${endOffset}`;
                    const transcriptUrl = `${baseUrl}/transcript?transcript=${transcriptId}#${utteranceId}`;
                    this.editor.model.change( writer => {
                        this.editor.model.insertContent(
                            createUtterance(writer, transcriptUrl, audioUrl,
                                            fragment.participant[0].turn[0].word));
                    });
                });
            })
        });
    }
    
    refresh() {
        const model = this.editor.model;
        const selection = model.document.selection;
        const allowedIn = model.schema.findAllowedParent( selection.getFirstPosition(), 'utterance' );
        
        this.isEnabled = allowedIn !== null;
    }
}

function createUtterance(writer, transcriptUrl, audioUrl, words ) {
    const utterance = writer.createElement('utterance');
    const utteranceAudio = writer.createElement('utteranceAudio', {
        source: audioUrl,
        controls: true
    });
    const utteranceDescription = writer.createElement( 'utteranceDescription' );

    if (layers.length > 1) {
        // add 'title' labels
        const token = writer.createElement( 'token' );
        for (let l of layers) {
            if (l != "word") {
                const tag = writer.createElement( 'tag' );
                const text = writer.createText( `${l} :` );
                writer.append(text, tag);
                writer.append(tag, token);
            }
        } // next layer
        const word = writer.createElement( 'word' );
        const text = writer.createText( 'word :' );
        writer.append(text, word);
        writer.append(word, token);
        writer.append(token, utteranceDescription);
    } // there are layers selected

    // add word tokens
    for (let w of words) {
        const token = writer.createElement( 'token' );
        for (let l of layers) {
            if (l != "word") {
                const tag = writer.createElement( 'tag', { layer: l } );
                let tags = " ";
                if (w[l]) { // if this word has tags on this layer
                    tags = w[l].map(t=>t.label).join(" ");
                }
                const text = writer.createText(tags);
                writer.append(text, tag);
                writer.append(tag, token);
            }
        } // next layer
        const word = writer.createElement( 'word' );
        const text = writer.createText( w.label, {
            linkHref: transcriptUrl,
            linkIsExternal: true
        } );
        writer.append(text, word);
        writer.append(word, token);
        writer.append(token, utteranceDescription);
    } // next word token
    writer.append(utteranceAudio, utterance);
    writer.append(utteranceDescription, utterance);
    
    return utterance;
}
