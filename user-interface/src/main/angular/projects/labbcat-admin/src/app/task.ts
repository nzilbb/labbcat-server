/** Elicitation task */
import { Step } from './step';

export interface Task {
    task_id: number;
    task_name: string;
    description: string;
    corpus: string;
    transcriptType: string;
    preamble: string;
    consent: string;
    endUrl: string;
    steps: any[]; // TODO
    resources: object;
    reminders: object[];
    participantFields: string[];
    transcriptFields: string[];
    version: string;
    uploadUrl: string;
    imageBaseUrl: string;
    consentUrl: string;
    newParticipantUrl: string;
    verifyUrl: string;
}
