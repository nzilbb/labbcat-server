import { Component, OnInit, EventEmitter, Output } from '@angular/core';

@Component({
  selector: 'lib-ipa-helper',
  templateUrl: './ipa-helper.component.html',
  styleUrls: ['./ipa-helper.component.css']
})
export class IpaHelperComponent implements OnInit {
    @Output() symbolSelected = new EventEmitter<string>();
    
    constructor() { }
    
    ngOnInit(): void {
    }
    select(symbol: string) {
        this.symbolSelected.emit(symbol);
        return false;
    }
}
