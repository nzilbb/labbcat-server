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
    @Input() disabled: boolean;
    temporaryTitle: string;
    regexpError: string;
    
    constructor() { }
    
    ngOnInit(): void {
    }

    checkRegularExpression(event: any): void {
        const value = event.target.value;
        // prevent errors thrown by [[a][b]], since this pattern isn't problematic for MySQL
        const checkValue = value.replace(/\[([^[]*)\[([^[\]]+)\]([^[\]]*)\[([^[\]]+)\]([^[\]]*)\]/,'[$1$2$3$4$5]');
        // catch patterns that will error for java.sql but don't in new Regexp(): unmatched [, empty []
        if ((checkValue.match(/\[/g) || []).length > (checkValue.match(/\]/g) || []).length) {
            this.regexpError = 'Unterminated character class';
            this.temporaryTitle = 'Invalid regular expression /' + value + '/: ' + this.regexpError;
        } else if (checkValue.match(/\[\]/)) {
            this.regexpError = 'Empty character class';
            this.temporaryTitle = 'Invalid regular expression /' + value + '/: ' + this.regexpError;
        } else {
        // check regexp
        try {
            new RegExp(checkValue, "u");
            this.validRegexp(value);
        } catch(error) {
            if (error.message.endsWith("Invalid escape") || error.message.endsWith("invalid identity escape in regular expression") || error.message.endsWith("invalid escaped character for Unicode pattern")) {
                // ignore errors thrown by \- outside [], since this pattern isn't problematic for MySQL
                this.validRegexp(checkValue);
            } else {
                this.regexpError = error.message.replace(/Invalid regular expression: (\/.+\/u: )?/, '');
                this.temporaryTitle = 'Invalid regular expression /' + value + '/: ' + this.regexpError;
            }
        }
        }
    }
    
    validRegexp(regexp: string): void {
        this.regexpError = "";
        // if it's a long regular expression...
        this.temporaryTitle = regexp.length>10
        // show the regular expression as the tool-tip
            ?regexp
        // but otherwise, use the given title
            :null;
    }
}
