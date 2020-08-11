import { Component, OnInit } from '@angular/core';
import { environment } from '../../environments/environment';

@Component({
  selector: 'app-wait',
  templateUrl: './wait.component.html',
  styleUrls: ['./wait.component.css']
})
export class WaitComponent implements OnInit {

    imagesLocation = environment.imagesLocation;
    constructor() { }
    
    ngOnInit(): void {
    }
    
}
