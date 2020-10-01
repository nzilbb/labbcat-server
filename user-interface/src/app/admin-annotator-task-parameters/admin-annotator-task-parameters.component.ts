import { Component, OnInit, OnDestroy, AfterViewInit, ViewChild, ElementRef } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { environment } from '../../environments/environment';

import { MessageService } from '../message.service';
import { LabbcatService } from '../labbcat.service';

@Component({
  selector: 'app-admin-annotator-task-parameters',
  templateUrl: './admin-annotator-task-parameters.component.html',
  styleUrls: ['./admin-annotator-task-parameters.component.css']
})
export class AdminAnnotatorTaskParametersComponent implements OnInit, AfterViewInit, OnDestroy {
    annotatorId: string;
    taskId: string;
    @ViewChild('taskWebapp', {static: false}) taskWebapp: ElementRef;
    
    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService,
        private route: ActivatedRoute,
        private router: Router
    ) {}

    boundOnSetTaskParameters: EventListener;
    ngOnInit(): void {
        this.boundOnSetTaskParameters = this.onSetTaskParameters.bind(this);
        // listen for 'parameters set' events
        addEventListener("message", this.boundOnSetTaskParameters, true);
        
        this.annotatorId = this.route.snapshot.paramMap.get('annotatorId');
        this.taskId = this.route.snapshot.paramMap.get('taskId');
    }
    ngOnDestroy(): void {
        console.log("admin-annotator-task-parameters destroy");
        // deregister our listener
        removeEventListener("message", this.boundOnSetTaskParameters, true);
    }
    
    onSetTaskParameters(event) {
        console.log("admin-annotator-task-paramaters message received: " + JSON.stringify(event.data))
        if (event.data.resource == "setTaskParameters") {
            if (event.data.error) {
                this.messageService.error(event.data.error);
            }
            if (event.data.message) {
                this.messageService.info(event.data.message);
            }
            this.messageService.info("Task parameters set"); // TODO i18n
            // return to previous page
            this.router.navigate([".."], { relativeTo: this.route });
        }
    }
    
    ngAfterViewInit() {
        // open the given config URL
        let url = environment.baseUrl + "admin/annotator/task/"+this.annotatorId+"/?" + this.taskId;
        this.taskWebapp.nativeElement.src = url;
    }
}
