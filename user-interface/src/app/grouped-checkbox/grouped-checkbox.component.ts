import { Component, OnInit, Input, EventEmitter, Output } from '@angular/core';

@Component({
  selector: 'app-grouped-checkbox',
  templateUrl: './grouped-checkbox.component.html',
  styleUrls: ['./grouped-checkbox.component.css']
})
export class GroupedCheckboxComponent implements OnInit {
    @Input() group: string;
    @Input() name: string;
    @Input() value: string;
    @Input() disabled: boolean;
    @Input() checked: boolean;
    @Output() checkedChange = new EventEmitter();

    static groups = {};
    
    constructor() { }
    
    ngOnInit(): void {
    }

    handleChange(): void {
        this.checkedChange.emit();
    }
    handleClick(event): void {
        let lastChk = event.target;
        if (event.shiftKey) {
            let firstChk = GroupedCheckboxComponent.groups[this.group];
            if (firstChk) {
                let wholeGroup = document.getElementsByClassName(this.group);
                let started = false;
                for (let c = 0; c < wholeGroup.length; c++) {
                    let chk = wholeGroup[c] as HTMLInputElement;
                    if (!started) {
                        if (chk === firstChk || chk === lastChk) { // found first checkbox
                            started = true;
                        } // found first checkbox
                    } else { // ticking checkboxes
                        if (chk === firstChk || chk === lastChk) { // found last checkbox
                            break;
                        } // found last checkbox
                        chk.click();
                    }
                } // next checkbox
            } // there is a first checkbox
        } // holding shift down
        GroupedCheckboxComponent.groups[this.group] = lastChk;
    }
}
