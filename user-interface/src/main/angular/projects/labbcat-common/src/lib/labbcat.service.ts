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
    labbcat: any;
    ag: any;
    title: "";
    
    constructor(
        private router: Router,
        private messageService: MessageService,
        @Inject('environment') private environment,
        @Inject(LOCALE_ID) public locale: string
    ) {
        this.ag = ag;
        this.labbcat = new labbcat.LabbcatAdmin(this.environment.baseUrl); // TODO username/password
        labbcat.language = this.locale;
        this.labbcat.getSystemAttribute("title", (attribute: any) => {
            console.log("attribute " + JSON.stringify(attribute));
            this.title = attribute.value;
        })
    }

    login(user: string, password: string, url = "/") {
        this.labbcat = new labbcat.LabbcatAdmin(this.environment.baseUrl);
        // do a request to ensure we're in
        this.labbcat.getId((result, errors, messages, call)=>{
            console.log("errors " + JSON.stringify(errors));
            if (errors) {
                if (errors[0].endsWith("401")) {
                    this.messageService.error("User ID or pass phrase incorrect");
                } else {
                    for (let message of errors) this.messageService.error(message);
                }
            } else {
                this.router.navigateByUrl(url);
            }
        });
    }

    annotationGraph(jsonObject: any) {
        return ag.Graph.activateObject(jsonObject);
    }
}
