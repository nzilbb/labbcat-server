import { Component, OnInit, Inject, ViewChild, ElementRef } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { MessageService, LabbcatService } from 'labbcat-common';

@Component({
  selector: 'app-dictionary',
  templateUrl: './dictionary.component.html',
  styleUrl: './dictionary.component.css'
})
export class DictionaryComponent implements OnInit {
    baseUrl: string;
    managerId: string;
    dictionaryId: string;
    
    csv: File;
    rowCount: number;
    headers: string[];
    wordColumn: number;

    @ViewChild('form', {static: false}) form: ElementRef;
    
    constructor(
        private labbcatService: LabbcatService,
        private messageService: MessageService,
        private route: ActivatedRoute,
        private router: Router,
        @Inject('environment') private environment
    ) {
        this.baseUrl = this.environment.baseUrl;
    }

    ngOnInit(): void {
        this.route.queryParams.subscribe((params) => {
            this.managerId = params["managerId"];
            this.dictionaryId = params["dictionaryId"];
        });
    }

    /** Called when a CSV file is selected; parses the file to determine CSV fields. */
    selectFile(files: File[]): void {
        if (files.length == 0) return;
        this.csv = files[0]
        if (!this.csv.name.endsWith(".csv") && !this.csv.name.endsWith(".tsv")) {
            this.messageService.error("File must be a CSV file.")
            this.csv = null;
            return;
        }
        
        const reader = new FileReader();
        const component = this;
        reader.onload = () => {  
            const csvData = reader.result;  
            let csvRecordsArray = (<string>csvData).split(/\r\n|\n/);
            // remove blank lines
            csvRecordsArray = csvRecordsArray.filter(l=>l.length>0);
            if (csvRecordsArray.length == 0) {
                component.messageService.error("File is empty: " + component.csv.name);
            } else {
                this.rowCount = csvRecordsArray.length - 1; // (don't count header line)
                
                // get headers...
                const firstLine = csvRecordsArray[0];
                // split the line into fields
                let delimiter = ",";
                if (firstLine.match(/.*\t.*/)) delimiter = "\t";
                else if (firstLine.match(/.;.*/)) delimiter = ";";
                const fields = firstLine.split(delimiter);
                // the fields might be quoted, so remove quotes
                component.headers = fields.map(f=>f.replace(/^"(.*)"$/g, "$1"))
                
                // if there's a column called "word", select it
                this.wordColumn = this.headers.findIndex(h=>/^word$/i.test(h));
                if (this.wordColumn < 0) { // if not
                    // select the first column containing "word"
                    this.wordColumn = this.headers.findIndex(h=>/^.*word.*$/i.test(h));
                }
                if (this.wordColumn < 0) this.wordColumn = 0; // first column by default
            }
        };
        reader.onerror = function () {  
            component.messageService.error("Error reading " + component.csv.name);
        };
        reader.readAsText(this.csv);        
    }

    lookup() : void {
        this.form.nativeElement.submit();
    }
}
