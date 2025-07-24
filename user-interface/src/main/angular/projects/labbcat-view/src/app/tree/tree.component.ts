import { Component, OnInit, Input } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { Network, Node, Edge, Data, Options } from 'vis';

import { MessageService, LabbcatService } from 'labbcat-common';
import { Layer, User, Annotation, Anchor } from 'labbcat-common';
import { TreeNode } from '../tree-node';

@Component({
  selector: 'app-tree',
  templateUrl: './tree.component.html',
  styleUrl: './tree.component.css'
})
export class TreeComponent implements OnInit {

    @Input() id: string;
    @Input() annotationId: string;
    @Input() layerId: string;

    loading = true;

    fragment: any;
    nodes: Annotation[];
    root: TreeNode;
    viz: Network;

    constructor(
        private labbcatService : LabbcatService,
        private messageService : MessageService,
        private route : ActivatedRoute,
    ) {
    }

    ngOnInit() : void {
        if (!this.id || !this.annotationId || !this.layerId) {
            this.route.queryParams.subscribe((params) => {
                this.id = this.id || params["id"];
                this.annotationId = this.annotationId || params["annotationId"];
                this.layerId = this.layerId || params["layerId"];
                this.loadFragment()
                    .then(v=>this.visualizeTree());
            });
        } else {
            this.loadFragment()
                .then(v=>this.visualizeTree());
        }
    }

    loadFragment(): Promise<void> {
        return new Promise((resolve, reject) => {
            this.labbcatService.labbcat.getFragment(
                this.id, this.annotationId, [this.layerId],
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

    visualizeTree(): void {
        console.log("viz...");
        const nodeArray: Node[] = [];
        const edgeArray: Edge[] = [];

        let index = 0; // character in the string
        let currentNode: Node = null;
        let currentDepth = 0; // track the depth of the tree
        let maxDepth = 0;

        // construct a vis graph from the parse tree
        this.root.setDepths(0);
        for (let treeNode of this.root.traverse()) {
	    const node: Node = {
                id: treeNode.annotation.id,
                label: treeNode.annotation.label,
                shape: "box",
                color: { background : "white" },
                borderWidth: 0,
                level : treeNode.depth,
            };
	    nodeArray.push(node);
            if (treeNode.parent) {
		edgeArray.push({
                    to: node.id,
                    from: treeNode.parent.annotation.id,
                    color: "black" } as Edge);
            }
        } // next tree node
    
        // create a network
        const container = document.getElementById(`tree-${this.id}`);
        
        // provide the data in the vis format
        const data: Data = {
	    nodes: nodeArray,
	    edges: edgeArray
        };
        var options: Options = {
	    physics: false,
	    layout: {
	        hierarchical: {
		    levelSeparation : 50,
		    parentCentralization : true,
		    blockShifting : true,
		    edgeMinimization : true,
		    sortMethod : "directed"
	        }
	    }
        };
        
        // initialize your network!
        this.viz = new Network(container, data, options);
    }

}
