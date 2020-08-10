import { Component, Input, OnInit } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

import { LabbcatService } from '../labbcat.service';

@Component({
  selector: 'app-keep-alive',
  templateUrl: './keep-alive.component.html',
  styleUrls: ['./keep-alive.component.css']
})
export class KeepAliveComponent implements OnInit {
    @Input() threadId: string;
    url: SafeResourceUrl;
    
    constructor(
        private labbcatService: LabbcatService,
        private sanitizer: DomSanitizer
    ) { }
    
    ngOnInit(): void {
        this.url = this.sanitizer.bypassSecurityTrustResourceUrl(
            this.labbcatService.labbcat.baseUrl + "keepalive?threadId="+this.threadId);
    }

}
