import { HostListener } from '@angular/core';

import { MessageService, LabbcatService } from 'labbcat-common';
import { ComponentCanDeactivate } from './pending-changes.guard';

// Base class for components that implement any kind of CRUD administration operations
export class AdminComponent implements ComponentCanDeactivate {
    changed = false;
    
    constructor(
        protected labbcatService: LabbcatService,
        protected messageService: MessageService
    ) { }

    // guard against browser refresh, close, etc.
    @HostListener('window:beforeunload')
    canDeactivate(): boolean {
        return !this.changed;
    }
}
