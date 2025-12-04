import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class MessageService {
    messages: string[] = [];
    errors: string[] = [];

    constructor() { }
    
    info(message: string): void {
        this.messages.push(message);
        setTimeout(function() { this.messages.shift(); }.bind(this), 10000); // TODO find a better way
    }
    
    error(message: string): void {
        this.errors.push(message);
        setTimeout(function() { this.errors.shift(); }.bind(this), 30000); // TODO find a better way
    }

}
