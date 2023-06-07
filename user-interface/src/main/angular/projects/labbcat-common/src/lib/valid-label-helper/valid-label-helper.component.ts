import { Component, OnInit, EventEmitter, Output, Input } from '@angular/core';

import { Layer } from '../layer';

@Component({
  selector: 'lib-valid-label-helper',
  templateUrl: './valid-label-helper.component.html',
  styleUrls: ['./valid-label-helper.component.css']
})
export class ValidLabelHelperComponent implements OnInit {
    @Input() layer: Layer;
    @Output() symbolSelected = new EventEmitter<string>();
    
    Object = Object; // so we can call Object.keys in the template

    constructor() { }
    
    ngOnInit(): void {
    }
    
    select(symbol: string) {
        this.symbolSelected.emit(symbol);
        return false;
    }

}
