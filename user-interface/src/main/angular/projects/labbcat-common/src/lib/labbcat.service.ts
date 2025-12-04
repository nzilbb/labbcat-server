import { Injectable, Inject, LOCALE_ID } from '@angular/core';
import { Observable, of } from 'rxjs';
import { Router } from '@angular/router';

import { MessageService } from './message.service';

declare var labbcat:  any; // nzilbb.labbcat.js
declare var ag:  any; // nzibb.ag.js

@Injectable({
  providedIn: 'root'
})
export class LabbcatService {
    _labbcat: any;
    ag: any;
    title: "";
    
    constructor(
        private router: Router,
        private messageService: MessageService,
        @Inject('environment') private environment,
        @Inject(LOCALE_ID) public locale: string
    ) {
        this.ag = ag;
        this._labbcat = new labbcat.LabbcatAdmin(this.environment.baseUrl); // TODO username/password
        this._labbcat.language = this.locale;
    }

    get labbcat() : any {
        if (!this.title) { // ensure we don't call this before login
            this._labbcat.getSystemAttribute("title", (attribute: any) => {
                this.title = attribute.value;
            });
        }
        return this._labbcat;
    }

    login(user: string, password: string): Promise<string> {
        return new Promise((resolve, reject) => {
            this._labbcat = new labbcat.LabbcatAdmin(this.environment.baseUrl);
            // do a request to ensure we're in
            this._labbcat.verbose = true;
            this._labbcat.login(user, password, (result, errors, messages, call)=>{
                console.log("errors " + JSON.stringify(errors));
                if (errors) {
                    if (errors[0].endsWith("401")) {
                        reject(["User ID or pass phrase incorrect"]); // TODO i18n
                    } else {
                        reject(errors.join("\n"));
                    }
                } else {
                    const url = result && result.url?result.url:null as string;
                    resolve(url);
                }
            });
        });
    }

    annotationGraph(jsonObject: any) {
        return ag.Graph.activateObject(jsonObject);
    }
}
