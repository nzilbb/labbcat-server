import { Component, OnInit, Input } from '@angular/core';
import { Inject } from '@angular/core';

@Component({
  selector: 'lib-link',
  templateUrl: './link.component.html',
  styleUrls: ['./link.component.css']
})
export class LinkComponent implements OnInit {
    @Input() title: string;
    @Input() label: string;
    @Input() icon: string;
    @Input() img: string;
    @Input() routerLink: string[];
    @Input() queryParams: object;
    @Input() href: string;
    processing: false;
    
    imagesLocation: string;
    classes = "lnk";
    
    constructor(@Inject('environment') private environment) {
        this.imagesLocation = this.environment.imagesLocation;
    }
    
    ngOnInit(): void {
        this.title = this.title || this.label;
        if (!this.label) this.classes += " icon-only";
    }
    
}
