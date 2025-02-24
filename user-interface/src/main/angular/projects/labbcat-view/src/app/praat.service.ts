import { Injectable } from '@angular/core';

import { MessageService } from 'labbcat-common';

declare var nzilbb:  any; // nzilbb.jsendpraat.js

@Injectable({
  providedIn: 'root'
})
export class PraatService {
    extensionVersion: string;
    nativeMessagingVersion: string;
    
    sendPraatResolve: (code: string) => void;
    sendPraatReject: (code: string) => void;
    uploadResolve: (code: string) => void;
    uploadReject: (code: string) => void;

    constructor(private messageService: MessageService) {
    }

    /** 
     * Initializes communication with the JSendPraat extension, if it's intalled.
     * The string returned by the promise is the version of the browser extension installed.
     * The promise is rejected if the extension is not installed; the argument of the reject
     * call is true if the browser is compatible with user installation, and false if the 
     * browser is incompatible.
     * This must be called before any other methods.
     */
    initialize(): Promise<string> {
        return new Promise((resolve, reject) => {
            console.log("nzilbb.jsendpraat.detectExtension...");
            if (nzilbb.jsendpraat.detectExtension(
                (version) => { // onExtensionDetected
                    console.log(`onExtensionDetected ${version}`);
                    this.extensionVersion = version;
                    resolve(version);
                }, // onExtensionDetected

                // the extension requires handlers set up at the outset
                // so the handlers below accept/reject subsequent promises made as required
                
                (code, error) => { // onSendPraatResponse
                    if (error) {
                        this.messageService.error(error);
                        if (this.sendPraatReject) {
                            this.sendPraatReject(code);
                        }
                    } else if (this.sendPraatResolve) {
                        this.sendPraatResolve(code);
                    }
                    this.sendPraatResolve = null;
                    this.sendPraatReject = null;
                }, // onSendPraatResponse
                
                (string, value, maximum, error, code) => { // onProgress
                    console.log(`praat progress ${string} ${value} ${maximum} ${error} ${code}`);
                    // TODO make this a subscription thing
                }, // onProgress
                
                (code, summary, error) => { // onUploadResponse
                    if (summary) this.messageService.info(summary);
                    if (error) {
                        this.messageService.error(error);
                        if (this.uploadReject) {
                            this.uploadReject(code);
                        }
                    } else if (this.uploadResolve) {
                        this.uploadResolve(code);
                    }
                    this.uploadResolve = null;
                    this.uploadReject = null;
                },
                
                (version) => { // onNativeMessagingHostDetected
                    this.nativeMessagingVersion = version;
                }, // onExtensionDetected
            )) { // onUploadResponse
                
                // we've called nzilbb.jsendpraat.detectExtension, which send's a message to
                // the extension. If we never hear back, that means it's not installed,
                // but the browser is compatible

                setTimeout(()=>{ // wait a short while
                    console.log(`Timeout`);
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

    sendPraat(script: string[], authorization: string): Promise<string> {
        return new Promise((resolve, reject) => {
            this.sendPraatResolve = resolve;
            this.sendPraatReject = reject;
            nzilbb.jsendpraat.sendpraat(script, authorization);
        });
    }

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
}
