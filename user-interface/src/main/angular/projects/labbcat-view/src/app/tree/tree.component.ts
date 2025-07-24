import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { MessageService, LabbcatService } from 'labbcat-common';
import { Layer, User, Annotation, Anchor } from 'labbcat-common';
import { TreeNode } from '../tree-node';

@Component({
  selector: 'app-tree',
  templateUrl: './tree.component.html',
  styleUrl: './tree.component.css'
})
export class TreeComponent implements OnInit {

    id: string;
    start: number;
    end: number;
    layerId: string;

    loading = true;

    fragment: any;
    nodes: Annotation[];
    root: TreeNode;

    constructor(
        private labbcatService : LabbcatService,
        private messageService : MessageService,
        private route : ActivatedRoute,
    ) {
    }

    ngOnInit() : void {        
        this.route.queryParams.subscribe((params) => {
            this.id = params["id"];
            this.start = params["start"];
            this.end = params["end"];
            this.layerId = params["layerId"];
            this.loadFragment();
        });
    }

    loadFragment(): Promise<void> {
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.getFragment(
                this.id, this.start, this.end, [this.layerId],
                (fragment, errors, messages) => {
                    this.loading = false;
                    if (errors) errors.forEach(m => this.messageService.error(m));
                    if (messages) messages.forEach(m => this.messageService.info(m));
                    if (!fragment) {
                        this.messageService.error("Invalid fragment"); // TODO i18n
                        reject();
                    } else { // valid transcript
                        fragment = this.labbcatService.annotationGraph(fragment);
                        this.fragment = fragment;
                        this.nodes = [];
                        // build a tree
                        let currentNode: TreeNode = null;
                        for (let annotation of fragment.all(this.layerId)) {
                            this.nodes.push(annotation);
                            if (!this.root) {
                                this.root = new TreeNode(null, annotation);
                                currentNode = this.root;
                            } else {
                                currentNode = currentNode
	                            .findFirstContainingNode(annotation)
	                            .addChild(annotation);
                            }
                        } // next annotation
                        resolve();
                    } // valid fragment
                });
        });
    }

}
