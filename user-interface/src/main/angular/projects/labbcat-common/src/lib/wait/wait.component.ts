import { Component, OnInit, Input } from '@angular/core';
import { Inject } from '@angular/core';

@Component({
  selector: 'lib-wait',
  templateUrl: './wait.component.html',
  styleUrls: ['./wait.component.css']
})
export class WaitComponent implements OnInit {
    /** Display spinner inline instead of in a fixed position */
    @Input() inSitu: boolean;

    imagesLocation : string;
    constructor(@Inject('environment') private environment) {
        this.imagesLocation = this.environment.imagesLocation;
    }
    
    ngOnInit(): void {
    }
    
}
