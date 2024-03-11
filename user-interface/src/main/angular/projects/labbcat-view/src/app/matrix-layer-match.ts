/**
 * One cell in a search matrix, containing a pattern to match on one layer.
 * General principles (which are not enforced) are:
 * - If pattern is set, then min and max should be null, and vice-versa.
 * - Only one MatrixLayerMatch in a Matrix has target == true.
 */
export interface MatrixLayerMatch {
    /** The Layer ID to match. */
    id: string;

    /** The regular expression to match the label against. */
    pattern: string;

    /** Whether the pattern is being negated (i.e. selecting tokens that don't match) or not. */
    not: boolean;

    /** The minimum value for the label, assuming it represents a number. */
    min: number;

    /** The maximum value for the label, assuming it represents a number. */
    max: number;

    /** Whether this condition is anchored to the start of the word token. */
    anchorStart: boolean;

    /** Whether this condition is anchored to the end of the word token. */
    anchorEnd: boolean;

    /** Whether this matrix cell is the target of the search. */
    target: boolean;    
}
