import { Component, OnInit } from '@angular/core';

import { Corpus } from '../corpus';
import { Episode } from '../episode';
import { MessageService, LabbcatService, Response } from 'labbcat-common';
import { AdminComponent } from '../admin-component';

@Component({
  selector: 'app-admin-episode-organiser',
  templateUrl: './admin-episode-organiser.component.html',
  styleUrl: './admin-episode-organiser.component.css'
})
export class AdminEpisodeOrganiserComponent extends AdminComponent implements OnInit {
    corpora: Corpus[];
    episodes: { [corpus_name: string] : Episode[] }
    
    constructor(
        labbcatService: LabbcatService,
        messageService: MessageService
    ) {
        super(labbcatService, messageService);
    }
    
    ngOnInit(): void {
        this.episodes = {};
        this.readCorpora();
    }

    readCorpora(): void {
        this.labbcatService.labbcat.readCorpora((corpora, errors, messages) => {
            this.corpora = [];
            for (let corpus of corpora) {
                this.corpora.push(corpus as Corpus);
            }
        });
    }

    expandCorpus(event: any, corpus: Corpus): void {
        if (!event.target.parentElement.open) { // open == false means it's opening...
            if (!this.episodes[corpus.corpus_name]) { // episodes not loaded yet
                this.labbcatService.labbcat.readEpisodes(
                    corpus.corpus_name, (episodes, errors, messages) => {
                        if (errors) errors.forEach(m => this.messageService.error(m));
                        if (messages) messages.forEach(m => this.messageService.info(m));
                        if (episodes) {
                            this.episodes[corpus.corpus_name] = [];
                            for (let episode of episodes) {
                                this.episodes[corpus.corpus_name].push(episode as Episode);
                            } // next episode
                        }
                    }); // readEpisodes
            } // episodes not loaded yet
        }
    }

    saveEpisode(episode: Episode): void {
        if (!episode.name) {
            alert("Episode name cannot be blank.");
            return;
        }
        this.labbcatService.labbcat.updateEpisode(
            episode.episode_id, episode.name, episode.corpus, (updated, errors, messages) => {
                if (errors) errors.forEach(m => this.messageService.error(m));
                if (messages) messages.forEach(m => this.messageService.info(m));
                if (updated) {
                    episode.name = updated.name;
                    episode.corpus = updated.corpus;                    
                    episode._changed = episode._editing = false;
                    // has it moved to a new corpus?
                    if (!this.episodes[episode.corpus] // not loaded yet
                        || !this.episodes[episode.corpus].find( // doesn't contain the ep
                            e=>e.episode_id == episode.episode_id)) {
                        
                        // find the old corpus
                        for (let corpus of this.corpora) {
                            if (this.episodes[corpus.corpus_name]) { // is loaded
                                const ep = this.episodes[corpus.corpus_name].find(
                                    e=>e.episode_id == episode.episode_id);
                                if (ep) { // remove the episode from the old corpus
                                    console.log("Removing from "+corpus.corpus_name);
                                    this.episodes[corpus.corpus_name]
                                        = this.episodes[corpus.corpus_name].filter(
                                            e=>e.episode_id != episode.episode_id);
                                    break;
                                }
                            } // corpus episodes are loaded 
                        } // next corpus
                        
                        if (this.episodes[episode.corpus]) { // the new corpus is loaded
                            // add it to the new corpus
                            this.episodes[episode.corpus].push(episode);
                        }
                    }

                }
            }); // readEpisodes
    }

}
