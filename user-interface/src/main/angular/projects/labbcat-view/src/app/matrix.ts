import { MatrixColumn } from './matrix-column';

/** Complete search matrix.*/
export interface Matrix {
    /** The columns of the search matrix, each representing patterns matching one word token. */
    columns: MatrixColumn[];
    
    /** Query to identify participants whose utterances should be searched. */
    participantQuery: string;

    /** Query to identify transcripts whose utterances should be searched. */
    transcriptQuery: string;
    
}
