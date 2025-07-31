import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';

import { MessageService } from 'labbcat-common';

import { ProgressUpdate } from './progress-update';

declare var nzilbb:  any; // nzilbb.jsendpraat.js

/** Service that managed communication with the JSendPraat browser extension */
@Injectable({
  providedIn: 'root'
})
export class PraatService {
    extensionVersion: string;
    nativeMessagingVersion: string;
    progress: Subject<ProgressUpdate>;
    
    sendPraatResolve: (message: string) => void;
    sendPraatReject: (error: string) => void;
    uploadResolve: (message: string) => void;
    uploadReject: (error: string) => void;

    constructor(private messageService: MessageService) {
        this.progress = new Subject<ProgressUpdate>();
    }

    /** 
     * Initializes communication with the JSendPraat extension, if it's installed.
     * The string returned by the promise is the version of the browser extension installed.
     * The promise is rejected if the extension is not installed; the argument of the reject
     * call is true if the browser is compatible with user installation, and false if the 
     * browser is incompatible.
     * This must be called before any other methods.
     */
    initialize(): Promise<string> {
        return new Promise((resolve, reject) => {
            if (nzilbb.jsendpraat.detectExtension(
                (version) => { // onExtensionDetected
                    this.extensionVersion = version;
                    resolve(version);
                }, // onExtensionDetected

                // the extension requires handlers set up at the outset
                // so the handlers below accept/reject subsequent promises made as required
                
                (code, error) => { // onSendPraatResponse
                    if (error) {
                        this.messageService.error(error);
                        if (this.sendPraatReject) {
                            this.sendPraatReject(code+": "+error);
                        } else {
                            this.progress.next({
                                message: "",
                                value: 100,
                                maximum: 100,
                                error: error,
                                code: code
                            });
                        }
                    } else if (this.sendPraatResolve) {
                        this.sendPraatResolve(code);
                    }
                    this.sendPraatResolve = null;
                    this.sendPraatReject = null;
                }, // onSendPraatResponse
                
                (message, value, maximum, error, code) => { // onProgress
                    this.progress.next({
                        message: message,
                        value: value || 0,
                        maximum: maximum || 100,
                        error: error,
                        code: code
                    });
                }, // onProgress
                
                (code, summary, error) => { // onUploadResponse
                    if (summary) this.messageService.info(summary);
                    if (error) {
                        this.messageService.error(error);
                        if (this.uploadReject) {
                            this.uploadReject(code+": "+error);
                        } else {
                            this.progress.next({
                                message: "",
                                value: 100,
                                maximum: 100,
                                error: error,
                                code: code
                            });
                        }
                    } else if (this.uploadResolve) {
                        this.uploadResolve(summary);
                    }
                    this.uploadResolve = null;
                    this.uploadReject = null;
                },  // onUploadResponse
                
                (version) => { // onNativeMessagingHostDetected
                    this.nativeMessagingVersion = version;
                }, // onExtensionDetected
            )) {
                
                // we've called nzilbb.jsendpraat.detectExtension, which send's a message to
                // the extension. If we never hear back, that means it's not installed,
                // but the browser is compatible

                setTimeout(()=>{ // wait a short while
                    // if we haven't heard back yet, reject
                    if (nzilbb.jsendpraat.isInstalled == null) {
                        reject(true); // true: browser is compatible
                    }
                }, 2000);
                
            } else { // incompatible browser
                reject(false); // false: browser is incompatible
            }            
        });
    }
    
    /**
     * Send a script for Praat to execute.
     * @param {string[]} script The script to send to Praat. 
     *  (e.g. ["Read from file... http://myserver/myfile.wav, "Edit"]). 
     * @param {string} authorization The Authorization header to be sent with any HTTP requests. 
     * @returns A promise of the code returned by the request.
     */
    sendPraat(script: string[], authorization: string): Promise<string> {
        return new Promise((resolve, reject) => {
            this.sendPraatResolve = resolve;
            this.sendPraatReject = reject;
            nzilbb.jsendpraat.sendpraat(script, authorization);
        });
    }

    /**
     * Send a script for Praat to execute, and then upload a file to the server. 
     * This can be used to upload to a the server a previously downloaded and then edited TextGrid.
     * @param {string[]} script The script to send to Praat. 
     * e.g. ["Read from file... http://myserver/myfile.wav, "Edit"]). 
     * @param {string} uploadUrl URL to upload to.
     * @param {string} fileParameter name of file HTTP parameter.
     * @param {string} fileUrl original URL for the file to upload.
     * @param {Object} otherParameters extra HTTP request parameters.
     * @param {string} authorization The Authorization header to be sent with any HTTP requests. 
     * @returns A promise of the code returned by the request.
     */
    upload(
        script: string[], uploadUrl: string, fileParameter: string, fileUrl: string,
        otherParameters: any, authorization: string): Promise<string> {
        return new Promise((resolve, reject) => {
            this.uploadResolve = resolve;
            this.uploadReject = reject;
            nzilbb.jsendpraat.upload(
                script, uploadUrl, fileParameter, fileUrl, otherParameters, authorization);
        });
    }

    /** Provides a subscription to progress updates when interacting with Praat. */
    progressUpdates() : Observable<ProgressUpdate> {
        return this.progress.asObservable();
    }

}
