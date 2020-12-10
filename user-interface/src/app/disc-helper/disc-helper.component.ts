import { Component, OnInit, EventEmitter, Output } from '@angular/core';

@Component({
  selector: 'app-disc-helper',
  templateUrl: './disc-helper.component.html',
  styleUrls: ['./disc-helper.component.css']
})
export class DiscHelperComponent implements OnInit {
    @Output() symbolSelected = new EventEmitter<string>();
    
    constructor() { }
    
    ngOnInit(): void {
    }
    select(symbol: string) {
        this.symbolSelected.emit(symbol);
        return false;
    }
}
