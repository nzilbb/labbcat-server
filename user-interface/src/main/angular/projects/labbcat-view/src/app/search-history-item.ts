import { Task } from 'labbcat-common';
import { Matrix } from './matrix';

/** One item in a history of searches. */
export interface SearchHistoryItem {
    task: Task;
    cancelled: boolean; // Task.status.includes("cancelled"), not exported
    matrix: Matrix;

    /** Participant/transcript filters (from Search) */
    filters: {
        participantDescription: string,
        participantCount: number,
        transcriptDescription: string,
        transcriptCount: number
    };
    
    /** Match options (from Search) */
    matchOptions: {
        mainParticipantOnly: boolean,
        onlyAligned: boolean,
        firstMatchOnly: boolean,
        excludeSimultaneousSpeech: boolean,
        overlapThreshold: number
    };
}
