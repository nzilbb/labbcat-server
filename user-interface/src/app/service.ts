import { Observable, of } from 'rxjs';
import { HttpHeaders } from '@angular/common/http';
import { MessageService } from './message.service';

import { Response } from './response';

export class Service {

    httpOptions = {
        headers: new HttpHeaders({ 'Content-Type': 'application/json;charset=UTF-8' })
    };

    constructor(
        private messageService: MessageService,
        protected baseUrl: string
    ) {
        console.log(`baseUrl: ${this.baseUrl}`);
    }
    
    handleError<Response>(operation = 'do that', message = "ERROR") {
        return (error: any): Observable<Response> => {
            if (error.status == 403) {
                this.error(operation, `Sorry, you don't have permission to ${operation}.`);
            } else {
                console.error(error);
                if (error.error && error.error.message) {
                    message += (message?" - ":"") + error.error.message;
                }
                this.error(operation, message);
            }
            // Let the app keep running by returning an empty result.
            return of(error.error as Response);
        };
    }

    info(operation: string, message: string) {
        this.messageService.info(message);
        this.log(`${operation} - ${message}`);
    }
    
    error(operation: string, message: string) {
        this.messageService.error(message);
        this.log(`ERROR: ${operation} - ${message}`);
    }
    
    log(message: string) {
        console.log(message);
    }
}
