import { Injectable, Inject } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { Observable } from 'rxjs';
import { map, filter } from 'rxjs/operators';
import { LabbcatService } from 'labbcat-common';

@Injectable({
  providedIn: 'root'
})
export class ActivityService {
    public otherUsersHere: string[]
    resource : string;
    viewport = Math.floor(Math.random() * 100); // identifies this tab/window
    urlChanges: Observable<string>;
    interval: any;
    
    constructor(
        private labbcatService: LabbcatService,
        private router: Router,
        @Inject('environment') private environment
    ) {
        this.interval = window.setInterval(()=>{this.poll()}, 5000);
        this.urlChanges = this.router.events.pipe(
            filter((event: any) => event instanceof NavigationEnd),
            map((event: NavigationEnd) => event.url)
        );
        this.urlChanges.subscribe((url)=>{
            console.log("urlChanges " + url);
            this.resource = url;
            this.poll();
        })
    }
    
    poll(): void {
        const activity = this.labbcatService.labbcat.createRequest(
            "activity", null, (model, errors, messages) => {
                this.otherUsersHere = [];
                if (model) {
                    this.otherUsersHere = model;
                } else {
                    this.otherUsersHere = [];
                }
            },
            `${this.environment.baseUrl}api/admin/activity?resource=${this.resource}&viewport=${this.viewport}`);
        try {
            console.log("activity " + this.resource);
            activity.send();
        } catch (x) {
            console.error(x);
        }
    }
}
