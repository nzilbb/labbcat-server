import { ClassicEditor } from '@ckeditor/ckeditor5-editor-classic';

import {
  FileLoader,
  UploadResponse,
} from '@ckeditor/ckeditor5-upload/src/filerepository';

// Image Upload
export default class LabbcatUploadAdapter {
    loader: FileLoader;
    xhr: any;
    // ...
    constructor(loader: FileLoader) {
        // The file loader instance to use during the upload. It sounds scary but do not
        // worry — the loader will be passed into the adapter later on in this guide.
        this.loader = loader;
    }
    // Starts the upload process.
    upload(): Promise<UploadResponse> {
        console.log('Upload was at least hit. Therefore upload adapter works');
        return this.loader?.file.then(
            (file: any) =>
                new Promise((resolve, reject) => {
                    this._initRequest(file.name);
                    this._initListeners(resolve, reject, file);
                    this._sendRequest(file);
                })
        );
    }
    // Aborts the upload process.
    abort() {
        if (this.xhr) {
            this.xhr.abort();
        }
    }
    _initRequest(name) {
        console.log('Request was initiated');
        const xhr = (this.xhr = new XMLHttpRequest());
        
        // Note that your request may look different. It is up to you and your editor
        // integration to choose the right communication channel. This example uses
        // a POST request with JSON as a data structure but your configuration
        // could be different.
        xhr.open('POST', 'agreement/'+name, true);
        xhr.responseType = 'json';
    }
    // Initializes XMLHttpRequest listeners.
    _initListeners(resolve: any, reject: any, file: File) {
        const xhr = this.xhr;
        const loader = this.loader;
        const genericErrorText = `Couldn't upload file: ${file.name}.`;
        
        xhr.addEventListener('error', () => reject(genericErrorText));
        xhr.addEventListener('abort', () => reject());
        xhr.addEventListener('load', () => {
            const response = xhr.response;
            console.log('response: ' + JSON.stringify(response));
            // This example assumes the XHR server's "response" object will come with
            // an "error" which has its own "message" that can be passed to reject()
            // in the upload promise.
            //
            // Your integration may handle upload errors in a different way so make sure
            // it is done properly. The reject() function must be called when the upload fails.
            if (!response || response.error) {
                return reject(
                    response && response.error ? response.error.message : genericErrorText
                );
            }
            
            // If the upload is successful, resolve the upload promise with an object containing
            // at least the "default" URL, pointing to the image on the server.
            // This URL will be used to display the image in the content. Learn more in the
            // UploadAdapter#upload documentation.
            resolve({
                default: response.model.url,
            });
        });
        // Upload progress when it is supported. The file loader has the #uploadTotal and #uploaded
        // properties which are used e.g. to display the upload progress bar in the editor
        // user interface.
        if (xhr.upload) {
            console.log('Progress event', xhr);
            xhr.upload.addEventListener('progress', (evt: ProgressEvent) => {
                if (evt.lengthComputable) {
                    loader.uploadTotal = evt.total;
                    loader.uploaded = evt.loaded;
                }
            });
        }
    }
    // Prepares the data and sends the request.
    _sendRequest(file: File) {
        console.log('Send file request', file);
        // Prepare the form data.
        const data = new FormData();
        
        data.append('upload', file);
        console.log(data);
        
        // Important note: This is the right place to implement security mechanisms
        // like authentication and CSRF protection. For instance, you can use
        // XMLHttpRequest.setRequestHeader() to set the request headers containing
        // the CSRF token generated earlier by your application.
        
        // Send the request.
        this.xhr.send(data);
    }
    
    // ...
    
}

// Upload ADAPTOR
export function LabbcatUploadAdapterPlugin(editor: any) {
    editor.plugins.get('FileRepository').createUploadAdapter = (
        loader: FileLoader
    ) => {
        // Configure the URL to the upload script in your back-end here!
        return new LabbcatUploadAdapter(loader);
    };
}
