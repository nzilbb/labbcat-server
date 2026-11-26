import { HostListener, Directive } from '@angular/core';
import { ActivatedRoute, UrlSegment } from '@angular/router';
import { Observable } from 'rxjs';

import { MessageService, LabbcatService, ComponentCanDeactivate } from 'labbcat-common';

// Base class for components that implement any kind of CRUD administration operations
@Directive()
export class AdminComponent implements ComponentCanDeactivate {
    changed = false;
    
    constructor(
        protected labbcatService: LabbcatService,
        protected messageService: MessageService,
        route: ActivatedRoute,
        protected help = null as string // help page URL if any
    ) {
        if (route) {
            route.url.subscribe((segments: UrlSegment[]) => {
                const url = segments.map(seg=>seg.path).join("/");
                if (this.help) {
                    window.postMessage({
                        app: "labbcat-admin",
                        url: `/${url}`,
                        help: this.help
                    });
                } else {
                    window.postMessage({
                        app: "labbcat-admin",
                        url: `/${url}`
                    });
                }
            });
        } // there is a help link
    }
    
    // guard against browser refresh, close, etc.
    @HostListener('window:beforeunload')
    canDeactivate(): boolean {
        return !this.changed;
    }
}
