import { Component, OnInit, EventEmitter, Output } from '@angular/core';

@Component({
  selector: 'lib-disc-helper',
  templateUrl: './disc-helper.component.html',
  styleUrls: ['./disc-helper.component.css']
})
export class DiscHelperComponent implements OnInit {
    @Output() symbolSelected = new EventEmitter<string>();
    
    constructor() { }
    
    ngOnInit(): void {
    }
    select(event: Event, symbol: string): boolean {
        this.symbolSelected.emit(symbol);
        if (event) event.stopPropagation();
        return false;
    }
    /* Block SearchMatrixComponent.hideHelper() when clicking inside disc-helper */
    dontHide(event: Event): boolean {
        if (event) event.stopPropagation();
        return false;
    }
}
