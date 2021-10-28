import Command from '@ckeditor/ckeditor5-core/src/command';

const baseUrl = document.URL.replace(/\/doc\/.*/,"");

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
            const fragmentUrl = `${baseUrl}/api/store/getFragment?id=${transcriptId}&annotationId=${utterance.id}&layerIds=word`
            $.getJSON(fragmentUrl, (response, status)=>{
                if (response.errors.length > 0) {
                    alert(response.errors.join("\n"));
                    return;
                }
                const fragment = response.model;
                const words = fragment.participant[0].turn[0].word
                      .map(annotation=>annotation.label)
                      .join(" ");
                const utteranceId = fragment.participant[0].turn[0].utterance[0].id;
                const idPattern = /.*__([0-9]+\.[0-9]+)-([0-9]+\.[0-9]+)$/;
                const startOffset = fragment.id.replace(idPattern,"$1")
                const endOffset = fragment.id.replace(idPattern,"$2")
                const audioUrl = `${baseUrl}/soundfragment?id=${transcriptId}&start=${startOffset}&end=${endOffset}`;
                const transcriptUrl = `${baseUrl}/transcript?transcript=${transcriptId}#${utteranceId}`;
                this.editor.model.change( writer => {
                    this.editor.model.insertContent(
                        createUtterance(writer, transcriptUrl, audioUrl, words));
                } );
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
    writer.append(utteranceAudio, utterance);
    writer.append(utteranceDescription, utterance);
    
    //writer.appendElement( 'paragraph', utteranceDescription );
    const transcript = writer.createText( words, {
        linkHref: transcriptUrl,
        linkIsExternal: true
    });
    writer.append(transcript, utteranceDescription);    
    
    return utterance;
}
