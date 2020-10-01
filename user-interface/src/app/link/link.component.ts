import { Component, OnInit, Input } from '@angular/core';
import { environment } from '../../environments/environment';

@Component({
  selector: 'app-link',
  templateUrl: './link.component.html',
  styleUrls: ['./link.component.css']
})
export class LinkComponent implements OnInit {
    @Input() title: string;
    @Input() label: string;
    @Input() icon: string;
    @Input() img: string;
    @Input() routerLink: string[];
    processing: false;
    
    imagesLocation = environment.imagesLocation;
    classes = "lnk";
    
    constructor() { }
    
    ngOnInit(): void {
        this.title = this.title || this.label;
        if (!this.label) this.classes += " icon-only";
    }
    
}
