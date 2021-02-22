import { Component, Input, OnInit } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

import { LabbcatService } from '../labbcat.service';

@Component({
  selector: 'lib-keep-alive',
  templateUrl: './keep-alive.component.html',
  styleUrls: ['./keep-alive.component.css']
})
export class KeepAliveComponent implements OnInit {
    @Input() threadId: string;
    url: SafeResourceUrl;
    interval: number;
    
    constructor(
        private labbcatService: LabbcatService,
        private sanitizer: DomSanitizer
    ) { }
    
    ngOnInit(): void {
        if (this.threadId) {
            // if we have a task to keep alive, proactively ping it instead of relying
            // on the the Refresh http-equiv meta tag of the keepalive HTML document
            this.interval = setInterval(()=>{
                this.labbcatService.labbcat.taskStatus(this.threadId, () => {});
            }, 30000); // every 30 seconds
        } else {
            this.url = this.sanitizer.bypassSecurityTrustResourceUrl(
                this.labbcatService.labbcat.baseUrl + "keepalive?threadId="+this.threadId);
        }
    }

}
