export interface TaskResource {
    /** The database key for the elicitation task. */
    task_id: number;
    /** The database key for the resource (message). */
    resource_id: string;
    /** A description of the message to help administrators/translators
        understand the purpose of the resource. */
    help: string;
    /** The participant-facing message. */
    message: string;
    
    _changed: boolean;
    _deleting: boolean;
}
