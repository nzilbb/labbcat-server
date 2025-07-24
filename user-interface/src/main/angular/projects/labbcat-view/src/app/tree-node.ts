import { Annotation, Anchor } from 'labbcat-common';

/** Simple implementation of an annotation tree */
export class TreeNode {
    annotation: Annotation;
    label: string;
    parent?: TreeNode;
    children: TreeNode[];
    
    constructor(
        parent: TreeNode,
        annotation: Annotation
    ) {
        this.annotation = annotation;
        this.label = annotation.label;
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

    toString(): string {
	let s = "";
	if (this.annotation != null) {
	    s += this.annotation.label
	        .replaceAll("\\(","\\(").replaceAll("\\)","\\)");
	} else if (this.label != null) {
	    s += this.label.replaceAll("\\(","\\(").replaceAll("\\)","\\)");
	} else {
	    s += "?";
	}
	for (let child of this.children) s += "(" + child + ")";
	return s;
    }

}
