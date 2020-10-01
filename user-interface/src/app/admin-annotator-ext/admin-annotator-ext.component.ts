import { Component, OnInit, AfterViewInit, ViewChild, ElementRef } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { environment } from '../../environments/environment';

import { MessageService } from '../message.service';
import { LabbcatService } from '../labbcat.service';

@Component({
  selector: 'app-admin-annotator-ext',
  templateUrl: './admin-annotator-ext.component.html',
  styleUrls: ['./admin-annotator-ext.component.css']
})
export class AdminAnnotatorExtComponent implements OnInit, AfterViewInit {
    annotatorId: string;
    @ViewChild('extWebapp', {static: false}) extWebapp: ElementRef;

    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService,
        private route: ActivatedRoute,
        private router: Router
    ) { }
    
    ngOnInit(): void {
        this.annotatorId = this.route.snapshot.paramMap.get('annotatorId');
    }

    ngAfterViewInit() {
        // open the given config URL
        let url = environment.baseUrl + "admin/annotator/ext/"+this.annotatorId+"/";
        this.extWebapp.nativeElement.src = url;
    }
}
