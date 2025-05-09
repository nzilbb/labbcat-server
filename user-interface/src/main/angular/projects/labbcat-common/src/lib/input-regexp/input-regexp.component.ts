import { Component, OnInit, Input, Output, EventEmitter } from '@angular/core';

@Component({
  selector: 'lib-input-regexp',
  templateUrl: './input-regexp.component.html',
  styleUrls: ['./input-regexp.component.css']
})
export class InputRegexpComponent implements OnInit {
    @Input() id: string;
    @Input() name: string;
    @Input() className: string;
    @Input() title = "Regular expression"; // TODO i18n
    @Input() placeholder = "Regular expression"; // TODO i18n
    @Input() value: string;
    @Output() valueChange = new EventEmitter<string>();
    @Input() autofocus: boolean;
    temporaryTitle: string;
    regexpError: string;
    
    constructor() { }
    
    ngOnInit(): void {
    }

    checkRegularExpression(event: any): void {
        const value = event.target.value;
        try {
            new RegExp(value, "u");
            this.regexpError = "";
            // if it's a long regular expression...
            this.temporaryTitle = value.length>10
            // show the regular expression as the tool-tip
                ?value
            // but otherwise, use the given title
                :null;
        } catch(error) {
            this.temporaryTitle = error.message.replace(': ', ' ').replace('/u:', '/:');
            this.regexpError = error.message.match("(?<=/u: ).+");
        }
    }
}
