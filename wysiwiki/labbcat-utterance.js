import Plugin from '@ckeditor/ckeditor5-core/src/plugin';
import ButtonView from '@ckeditor/ckeditor5-ui/src/button/buttonview';
import icon from './labbcat-utterance.svg';

export default class LabbcatUtterance extends Plugin {
    init() {
        this._defineSchema();
        this._defineConverters();
        const editor = this.editor;
        this._baseUrl = document.URL.replace(/\/doc\/.*/,"");
        console.log(`this._baseUrl ${this._baseUrl}`)
        
        editor.ui.componentFactory.add( 'labbcatUtterance', locale => {
            const view = new ButtonView( locale );
            
            view.set( {
                label: 'Insert utterance',
                icon: icon,
                tooltip: true
            } );

            // Callback executed once the image is clicked.
            view.on( 'execute', () => {
                const utteranceUrl = prompt( 'Utterance URL' );
                // layer.id=='utterance' %26%26 'ew_0_705455' IN all('word')
                const urlPattern = /.*?transcript=(.*)(&.*)?#(.*)$/
                if (!urlPattern.test(utteranceUrl)) {
                    alert(`Invalid utterance URL:\n${utteranceUrl}`);
                    return;
                }
                const transcriptId = utteranceUrl.replace(urlPattern,"$1");
                console.log(`transcriptId ${transcriptId}`);
                const wordId = utteranceUrl.replace(urlPattern,"$3");
                console.log(`wordId ${wordId}`);
                const query = encodeURIComponent(
                    `layer.id == 'utterance' && all('word').includes('${wordId}')`);
                const queryUrl
                      = `${this._baseUrl}/api/store/getMatchingAnnotations?expression=${query}`;
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
                    const fragmentUrl = `${this._baseUrl}/api/store/getFragment?id=${transcriptId}&annotationId=${utterance.id}&layerIds=word`
                    $.getJSON(fragmentUrl, (response, status)=>{
                        if (response.errors.length > 0) {
                            alert(response.errors.join("\n"));
                            return;
                        }
                        const fragment = response.model;
                        const words = fragment.participant[0].turn[0].word
                              .map(annotation=>annotation.label)
                              .join(" ");
                        const idPattern = /.*__([0-9]+\.[0-9]+)-([0-9]+\.[0-9]+)$/;
                        const startOffset = fragment.id.replace(idPattern,"$1")
                        const endOffset = fragment.id.replace(idPattern,"$2")
                        const audioUrl = `${this._baseUrl}/soundfragment?id=${transcriptId}&start=${startOffset}&end=${endOffset}`
                        console.log(`audioUrl ${audioUrl}`);
                        editor.model.change( writer => {
                            const utterance = writer.createElement( 'utterance' );
                            const audio = writer.createElement( 'utteranceAudio', {
                                source: audioUrl,
                                controls: true
                            } );
                            const text = writer.createElement( 'utteranceText' );
                            const transcript = writer.appendText( words, text );
                            writer.append( audio, utterance );
                            writer.append( text, utterance );
                            
                            // Insert the utterance in the current selection location.
                            editor.model.insertContent(
                                utterance, editor.model.document.selection );
                        } ); // editor.model.change
                    }) // getJSON(fragmentUrl)
                }); // getJSON(queryUrl)
            } ); // on execute
            
            return view;
        } ); 
    }

    _defineSchema() {
        const schema = this.editor.model.schema;
        
        schema.register( 'utterance', {
            isObject: true,            
            allowWhere: '$block'
        } );
        
        schema.register( 'utteranceAudio', {
            isObject: true,
            allowIn: 'utterance',
            allowAttributes: ['source','controls']
        } );

        schema.register( 'utteranceText', {
            isObject: true,            
            allowIn: 'utterance',
        } );
    }

    _defineConverters() {
        const conversion = this.editor.conversion;
        
        conversion.elementToElement( {
            model: 'utterance',
            view: {
                name: 'div',
                classes: 'utterance'
            }
        } );

        conversion.elementToElement( {
            model: 'utteranceAudio',
            view: {
                name: 'audio',
                classes: 'utterance-audio'
            }
        } );
        conversion.attributeToAttribute( { model: 'source', view: 'src' } );
        conversion.attributeToAttribute( { model: 'controls', view: 'controls' } );

        conversion.elementToElement( {
            model: 'utteranceText',
            view: {
                name: 'div',
                classes: 'utterance-text'
            }
        } );
    }
}
