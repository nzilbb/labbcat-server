import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { environment } from '../environments/environment';

declare var labbcat:  any;

@Injectable({
  providedIn: 'root'
})
export class LabbcatService {
    labbcat: any;
    
    constructor() {
        this.labbcat = new labbcat.LabbcatAdmin(environment.baseUrl); // TODO username/password
    }
}
