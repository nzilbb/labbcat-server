import {OnInit, ElementRef, Input, Directive} from '@angular/core';

@Directive({
  selector: '[appAutofocus]'
})
export class AutofocusDirective implements OnInit {
    @Input() appAutofocus: boolean;
    private el: any;
    constructor(
        private elementRef:ElementRef,
    ) { 
        this.el = this.elementRef.nativeElement;   
    }
    ngOnInit(): void {
        if (this.appAutofocus) this.el.focus();
    }   
}
