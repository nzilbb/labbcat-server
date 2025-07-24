import { Annotation, Anchor } from 'labbcat-common';

/** Simple implementation of an annotation tree */
export class TreeNode {
    annotation: Annotation;
    parent?: TreeNode;
    children: TreeNode[];
    depth?: number;
    
    constructor(
        parent: TreeNode,
        annotation: Annotation
    ) {
        this.annotation = annotation;
        this.parent = parent;
        this.children = [];
    }

    findFirstContainingNode(annotation: Annotation): TreeNode {
	let node: TreeNode = this;
	// end time is sufficient, as the annotations always go forward
	while (node.annotation.end.offset < annotation.end.offset) {
	    // (if we're arrived at the root, give up)
	    if (node.parent == null) return node;
	    else node = node.parent;
	} // next ancestor
	return node;
    }
    
    addChild(annotation: Annotation): TreeNode {
	let child = new TreeNode(this, annotation);
	this.children.push(child);
	return child;
    }

    setDepths(depth: number) : number {
        this.depth = depth;
        let maxDepth = depth;
	for (let child of this.children) {
	    maxDepth = Math.max(
                maxDepth,
                child.setDepths(depth + 1));                                
	}  // next child
        return maxDepth;
    }

    traverse(): TreeNode[] {
	let nodes: TreeNode[] = [ this ];
	for (let child of this.children) {
	    nodes = nodes.concat(child.traverse());
	}  // next child
	return nodes;
    }
    
    toString(): string {
	let s = "";
	if (this.annotation != null) {
	    s += this.annotation.label
	        .replaceAll("\\(","\\(").replaceAll("\\)","\\)");
	} else {
	    s += "?";
	}
	for (let child of this.children) s += "(" + child + ")";
	return s;
    }

}
