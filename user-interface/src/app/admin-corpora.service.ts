import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { catchError, map, tap } from 'rxjs/operators';
import { environment } from '../environments/environment';

import { Response } from './response';
import { Corpus } from './corpus';
import { MessageService } from './message.service';
import { Service } from './service';

@Injectable({
  providedIn: 'root'
})
export class AdminCorporaService extends Service {
    
    constructor(
        private http: HttpClient,
        messageService: MessageService) {
        super(messageService, environment.baseUrl+'api/admin/corpora');
    }
    
    readCorpora(): Observable<Response> {
        return this.http.get<Response>(this.baseUrl)
            .pipe(
                tap(_ => this.log('fetched corpora')),
                catchError(this.handleError<Response>(
                    'access corpora', "Could not get corpus list."))
            );
    }

    createCorpus(corpus: Corpus): Observable<Response> {
        // TODO validation
        return this.http.post<Response>(this.baseUrl, corpus, this.httpOptions)
            .pipe(
                tap((response: Response) => this.info(
                    'createCorpus', `Added corpus: "${response.model.corpus_id}"`)),
                catchError(this.handleError<Response>(
                    'create corpus',`Could not add "${corpus.corpus_name}"` ))
            );
    }

    updateCorpus(corpus: Corpus): Observable<Response> {
        // TODO validation
        return this.http.put<Response>(this.baseUrl, corpus, this.httpOptions)
            .pipe(
                tap((response: Response) => this.info(
                    'updateCorpus', `Updated corpus: "${response.model.corpus_name}"`)),
                catchError(this.handleError<any>(
                    'update corpus',`Could not update "${corpus.corpus_name}"` ))
            );
    }
    
    deleteCorpus(corpus: Corpus): Observable<Response> {
        // TODO validation
        return this.http.delete<Response>(`${this.baseUrl}/${corpus.corpus_id}`)
            .pipe(
                tap(_ => this.info('deleteCorpus', `Removed corpus: ${corpus.corpus_name}`)),
                catchError(this.handleError<Response>(
                    'delete corpus',`Could not remove "${corpus.corpus_name}"`))
            );
    }    
}
