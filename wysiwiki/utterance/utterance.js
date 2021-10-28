import UtteranceEditing from './utteranceediting';
import UtteranceUI from './utteranceui';
import Plugin from '@ckeditor/ckeditor5-core/src/plugin';

export default class Utterance extends Plugin {
    static get requires() {
        return [ UtteranceEditing, UtteranceUI ];
    }
}
