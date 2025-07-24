import { Component, OnInit } from '@angular/core';
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

    id: string;
    start: number;
    end: number;
    layerId: string;

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
        this.route.queryParams.subscribe((params) => {
            this.id = params["id"];
            this.start = params["start"];
            this.end = params["end"];
            this.layerId = params["layerId"];
            this.loadFragment()
                .then(v=>this.visualizeTree());
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
/*
        const treeString = this.root.toString();
	 
        // for each character
        for (let index = 0; index < treeString.length; index++) {
	    switch(treeString[index]) {
	        case '(': // new peer
	            break;
                    
	        case ')': // no more children
	            // ensure next child is added to grandparent
	            // (return to the parent level - a comma will follow)
	            if (currentDepth > 0) {
		        currentNode = currentNode.parent;
		        currentDepth --;
	            }
	            break;
		    
	        default: // node name
	            // scan forward until we hit something that's not
	            //  part of a name
	            var name = "";
	            while (index + 1 < treeString.length) {
		        if (treeString[index] != '\\'
		            && (treeString[index + 1] == ')'
		                || treeString[index + 1] == '(')) {
		            break;
		        }
		        if (treeString[index] != '\\') {
		            name += treeString[index];
		        }
		        index++;
	            } // next character
	            name += treeString[index];
	            name = name.trim();
	            if (name != "") { // don't add empty nodes
		        if (!currentNode) {
		            currentNode = {
                                id: index,
                                label: name,
                                shape: "box",
                                color: { background : "white" },
                                borderWidth: 0,
                                level : currentDepth,
                                children: [] } as Node;
		        } else {
		            // Add a new child to the current node
		            currentDepth++;
		            maxDepth = Math.max(maxDepth, currentDepth);
		            const child: Node = {
                                id: index,
                                label: name,
                                parent: currentNode,
                                shape: "box",
                                color: { background : "white" },
                                borderWidth: 0,
                                level : currentDepth,
                                children: []
                            };
		            currentNode.children.push(child);
		            edgeArray.push({
                                to: child.id,
                                from: currentNode.id,
                                color: "black" } as Edge);
		            currentNode = child;
		        }
		        nodeArray.push(currentNode);
	            } // no more children
	    } // character switch
        } // next character
        const constituents = {}
        for (let i in nodeArray) {
	    constituents[nodeArray[i].id] = nodeArray[i];	
	    if (nodeArray[i].children.length == 0) nodeArray[i].level = maxDepth;
        }
*/
    
        // create vis
        // const nodes = new DataSet(nodeArray);
        // const edges = new DataSet(edgeArray);
        
        // create a network
        const container = document.getElementById("tree");
        
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
