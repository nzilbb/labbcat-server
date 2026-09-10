export interface ElicitationTask {
    /** The database key for the record. */
    task_id: number;
    /** The name of the task. */
    task_name: string;
    /** Description of the task. */
    description: string;
    /** The corpus for elicited transcripts/recordings. */
    corpus_name: string;
    /** The transcript type for elicited transcripts/recordings. */
    transcript_type: string;
    /** HTML-encoded text the participant sees when they are about to start the task. */
    preamble: string;
    /** Optional HTML-encoded consent form to be 'signed' by the participant. */
    consent: string;
    /** Optional URL to send participants to after they finish the task.
        ({participant}, if present in the URL, is replaced by the participant's ID). */
    endUrl: string;

    _changed: boolean;
    _cantDelete: string;
    _deleting: boolean;
}
